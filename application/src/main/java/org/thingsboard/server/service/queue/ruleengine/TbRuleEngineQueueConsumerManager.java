/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.service.queue.ruleengine;

import com.google.protobuf.ProtocolStringList;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.gen.MsgProtos;
import org.thingsboard.server.common.msg.queue.QueueToRuleEngineMsg;
import org.thingsboard.server.common.msg.queue.RuleEngineException;
import org.thingsboard.server.common.msg.queue.RuleNodeInfo;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TbMsgCallback;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.gen.transport.TransportProtos.ToRuleEngineMsg;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.QueueKey;
import org.thingsboard.server.service.queue.TbMsgPackCallback;
import org.thingsboard.server.service.queue.TbMsgPackProcessingContext;
import org.thingsboard.server.service.queue.TbRuleEngineConsumerStats;
import org.thingsboard.server.service.queue.consumer.MainQueueConsumerManager;
import org.thingsboard.server.service.queue.processing.TbRuleEngineProcessingDecision;
import org.thingsboard.server.service.queue.processing.TbRuleEngineProcessingResult;
import org.thingsboard.server.service.queue.processing.TbRuleEngineProcessingStrategy;
import org.thingsboard.server.service.queue.processing.TbRuleEngineSubmitStrategy;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class TbRuleEngineQueueConsumerManager extends MainQueueConsumerManager<TbProtoQueueMsg<ToRuleEngineMsg>, Queue> {

    public static final String SUCCESSFUL_STATUS = "successful";
    public static final String FAILED_STATUS = "failed";

    private final TbRuleEngineConsumerContext ctx;
    private final TbRuleEngineConsumerStats stats;

    @Builder(builderMethodName = "create") // not to conflict with super.builder()
    public TbRuleEngineQueueConsumerManager(TbRuleEngineConsumerContext ctx,
                                            QueueKey queueKey,
                                            ExecutorService consumerExecutor,
                                            ScheduledExecutorService scheduler,
                                            ExecutorService taskExecutor) {
        super(queueKey, null, null, ctx.getQueueFactory()::createToRuleEngineMsgConsumer, consumerExecutor, scheduler, taskExecutor);
        this.ctx = ctx;
        this.stats = new TbRuleEngineConsumerStats(queueKey, ctx.getStatsFactory());
    }

    public void delete(boolean drainQueue) {
        addTask(TbQueueConsumerManagerTask.delete(drainQueue));
    }

    @Override
    protected void processTask(TbQueueConsumerManagerTask task) {
        if (task.getEvent() == QueueEvent.DELETE) {
            doDelete(task.isDrainQueue());
        }
    }

    private void doDelete(boolean drainQueue) {
        stopped = true;
        log.info("[{}] Handling queue deletion", queueKey);
        consumerWrapper.getConsumers().forEach(TbQueueConsumerTask::awaitCompletion);

        List<TbQueueConsumer<TbProtoQueueMsg<ToRuleEngineMsg>>> queueConsumers = consumerWrapper.getConsumers().stream()
                .map(TbQueueConsumerTask::getConsumer).collect(Collectors.toList());
        consumerExecutor.submit(() -> {
            if (drainQueue) {
                drainQueue(queueConsumers);
            }

            queueConsumers.forEach(consumer -> {
                for (String topic : consumer.getFullTopicNames()) {
                    try {
                        ctx.getQueueAdmin().deleteTopic(topic);
                        log.info("Deleted topic {}", topic);
                    } catch (Exception e) {
                        log.error("Failed to delete topic {}", topic, e);
                    }
                }
                try {
                    consumer.unsubscribe();
                } catch (Exception e) {
                    log.error("[{}] Failed to unsubscribe consumer", queueKey, e);
                }
            });
        });
    }

    @Override
    protected void processMsgs(List<TbProtoQueueMsg<ToRuleEngineMsg>> msgs,
                               TbQueueConsumer<TbProtoQueueMsg<ToRuleEngineMsg>> consumer,
                               Queue queue) throws Exception {
        TbRuleEngineSubmitStrategy submitStrategy = getSubmitStrategy(queue);
        TbRuleEngineProcessingStrategy ackStrategy = getProcessingStrategy(queue);
        submitStrategy.init(msgs);
        while (!stopped && !consumer.isStopped()) {
            TbMsgPackProcessingContext packCtx = new TbMsgPackProcessingContext(queue.getName(), submitStrategy, ackStrategy.isSkipTimeoutMsgs());
            submitStrategy.submitAttempt((id, msg) -> submitMessage(packCtx, id, msg));

            final boolean timeout = !packCtx.await(queue.getPackProcessingTimeout(), TimeUnit.MILLISECONDS);

            TbRuleEngineProcessingResult result = new TbRuleEngineProcessingResult(queue.getName(), timeout, packCtx);
            if (timeout) {
                printFirstOrAll(packCtx, packCtx.getPendingMap(), "Timeout");
            }
            if (!packCtx.getFailedMap().isEmpty()) {
                printFirstOrAll(packCtx, packCtx.getFailedMap(), "Failed");
            }
            packCtx.printProfilerStats();

            TbRuleEngineProcessingDecision decision = ackStrategy.analyze(result);
            if (ctx.isStatsEnabled()) {
                stats.log(result, decision.isCommit());
            }

            packCtx.cleanup();

            if (decision.isCommit()) {
                submitStrategy.stop();
                consumer.commit();
                break;
            } else {
                submitStrategy.update(decision.getReprocessMap());
            }
        }
    }

    private TbRuleEngineSubmitStrategy getSubmitStrategy(Queue queue) {
        return ctx.getSubmitStrategyFactory().newInstance(queue.getName(), queue.getSubmitStrategy());
    }

    private TbRuleEngineProcessingStrategy getProcessingStrategy(Queue queue) {
        return ctx.getProcessingStrategyFactory().newInstance(queue.getName(), queue.getProcessingStrategy());
    }

    private void submitMessage(TbMsgPackProcessingContext packCtx, UUID id, TbProtoQueueMsg<ToRuleEngineMsg> msg) {
        log.trace("[{}] Creating callback for topic {} message: {}", id, config.getName(), msg.getValue());
        ToRuleEngineMsg toRuleEngineMsg = msg.getValue();
        TenantId tenantId = TenantId.fromUUID(new UUID(toRuleEngineMsg.getTenantIdMSB(), toRuleEngineMsg.getTenantIdLSB()));
        TbMsgCallback callback = ctx.isPrometheusStatsEnabled() ?
                new TbMsgPackCallback(id, tenantId, packCtx, stats.getTimer(tenantId, SUCCESSFUL_STATUS), stats.getTimer(tenantId, FAILED_STATUS)) :
                new TbMsgPackCallback(id, tenantId, packCtx);
        try {
            if (!toRuleEngineMsg.getTbMsg().isEmpty() || toRuleEngineMsg.getTbMsgProto().isInitialized()) {
                forwardToRuleEngineActor(config.getName(), tenantId, toRuleEngineMsg, callback);
            } else {
                callback.onSuccess();
            }
        } catch (Exception e) {
            callback.onFailure(new RuleEngineException(e.getMessage(), e));
        }
    }

    private void forwardToRuleEngineActor(String queueName, TenantId tenantId, ToRuleEngineMsg toRuleEngineMsg, TbMsgCallback callback) {
        TbMsg tbMsg = TbMsg.fromProto(queueName, toRuleEngineMsg.getTbMsgProto(), toRuleEngineMsg.getTbMsg(), callback);
        QueueToRuleEngineMsg msg;
        ProtocolStringList relationTypesList = toRuleEngineMsg.getRelationTypesList();
        Set<String> relationTypes;
        if (relationTypesList.size() == 1) {
            relationTypes = Collections.singleton(relationTypesList.get(0));
        } else {
            relationTypes = new HashSet<>(relationTypesList);
        }
        msg = new QueueToRuleEngineMsg(tenantId, tbMsg, relationTypes, toRuleEngineMsg.getFailureMessage());
        ctx.getActorContext().tell(msg);
    }

    private void printFirstOrAll(TbMsgPackProcessingContext ctx, Map<UUID, TbProtoQueueMsg<ToRuleEngineMsg>> map, String prefix) {
        boolean printAll = log.isTraceEnabled();
        log.info("[{}] {} to process [{}] messages", queueKey, prefix, map.size());
        for (Map.Entry<UUID, TbProtoQueueMsg<ToRuleEngineMsg>> pending : map.entrySet()) {
            ToRuleEngineMsg tmp = pending.getValue().getValue();
            TbMsg tmpMsg = TbMsg.fromProto(config.getName(), tmp.getTbMsgProto(), tmp.getTbMsg(), TbMsgCallback.EMPTY);
            RuleNodeInfo ruleNodeInfo = ctx.getLastVisitedRuleNode(pending.getKey());
            if (printAll) {
                log.trace("[{}][{}] {} to process message: {}, Last Rule Node: {}", queueKey, TenantId.fromUUID(new UUID(tmp.getTenantIdMSB(), tmp.getTenantIdLSB())), prefix, tmpMsg, ruleNodeInfo);
            } else {
                log.info("[{}] {} to process message: {}, Last Rule Node: {}", TenantId.fromUUID(new UUID(tmp.getTenantIdMSB(), tmp.getTenantIdLSB())), prefix, tmpMsg, ruleNodeInfo);
                break;
            }
        }
    }

    public void printStats(long ts) {
        stats.printStats();
        ctx.getStatisticsService().reportQueueStats(ts, stats);
        stats.reset();
    }

    private void drainQueue(List<TbQueueConsumer<TbProtoQueueMsg<ToRuleEngineMsg>>> consumers) {
        long finishTs = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ctx.getTopicDeletionDelayInSec());
        try {
            int n = 0;
            while (System.currentTimeMillis() <= finishTs) {
                for (TbQueueConsumer<TbProtoQueueMsg<ToRuleEngineMsg>> consumer : consumers) {
                    List<TbProtoQueueMsg<ToRuleEngineMsg>> msgs = consumer.poll(config.getPollInterval());
                    if (msgs.isEmpty()) {
                        continue;
                    }
                    for (TbProtoQueueMsg<ToRuleEngineMsg> msg : msgs) {
                        try {
                            MsgProtos.TbMsgProto tbMsgProto;
                            if (msg.getValue().getTbMsg().isEmpty()) {
                                tbMsgProto = msg.getValue().getTbMsgProto();
                            } else {
                                tbMsgProto = MsgProtos.TbMsgProto.parseFrom(msg.getValue().getTbMsg());
                            }

                            EntityId originator = EntityIdFactory.getByTypeAndUuid(tbMsgProto.getEntityType(), new UUID(tbMsgProto.getEntityIdMSB(), tbMsgProto.getEntityIdLSB()));

                            TopicPartitionInfo tpi = ctx.getPartitionService().resolve(ServiceType.TB_RULE_ENGINE, config.getName(), TenantId.SYS_TENANT_ID, originator);
                            ctx.getProducerProvider().getRuleEngineMsgProducer().send(tpi, msg, null);
                            n++;
                        } catch (Throwable e) {
                            log.warn("Failed to move message to system {}: {}", consumer.getTopic(), msg, e);
                        }
                    }
                    consumer.commit();
                }
            }
            if (n > 0) {
                log.info("Moved {} messages from {} to system {}", n, queueKey, config.getName());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to drain queue", queueKey, e);
        }
    }

}
