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
package org.thingsboard.server.edqs.state;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.ObjectType;
import org.thingsboard.server.common.data.edqs.EdqsEventType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.queue.QueueConfig;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.edqs.processor.EdqsProcessor;
import org.thingsboard.server.edqs.processor.EdqsProducer;
import org.thingsboard.server.edqs.util.EdqsPartitionService;
import org.thingsboard.server.edqs.util.VersionsStore;
import org.thingsboard.server.gen.transport.TransportProtos.EdqsEventMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToEdqsMsg;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.common.consumer.MainQueueConsumerManager;
import org.thingsboard.server.queue.common.consumer.QueueConsumerManager;
import org.thingsboard.server.queue.discovery.QueueKey;
import org.thingsboard.server.queue.edqs.EdqsConfig;
import org.thingsboard.server.queue.edqs.EdqsQueue;
import org.thingsboard.server.queue.edqs.EdqsQueueFactory;
import org.thingsboard.server.queue.edqs.KafkaEdqsComponent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@KafkaEdqsComponent
@Slf4j
public class KafkaEdqsStateService implements EdqsStateService {

    private final EdqsConfig config;
    private final EdqsPartitionService partitionService;
    private final EdqsQueueFactory queueFactory;
    private final EdqsProcessor edqsProcessor;

    private MainQueueConsumerManager<TbProtoQueueMsg<ToEdqsMsg>, QueueConfig> stateConsumer;
    private QueueConsumerManager<TbProtoQueueMsg<ToEdqsMsg>> eventsConsumer;
    private EdqsProducer stateProducer;

    private boolean initialRestoreDone;

    private ExecutorService consumersExecutor;
    private ExecutorService mgmtExecutor;
    private ScheduledExecutorService scheduler;

    private final VersionsStore versionsStore = new VersionsStore();
    private final AtomicInteger stateReadCount = new AtomicInteger();
    private final AtomicInteger eventsReadCount = new AtomicInteger();

    @PostConstruct
    private void init() {
        consumersExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("edqs-consumer"));
        mgmtExecutor = ThingsBoardExecutors.newWorkStealingPool(4, "edqs-backup-consumer-mgmt");
        scheduler = ThingsBoardExecutors.newSingleThreadScheduledExecutor("edqs-backup-scheduler");

        stateConsumer = MainQueueConsumerManager.<TbProtoQueueMsg<ToEdqsMsg>, QueueConfig>builder() // FIXME Slavik: if topic is empty
                .queueKey(new QueueKey(ServiceType.EDQS, EdqsQueue.STATE.getTopic()))
                .config(QueueConfig.of(true, config.getPollInterval()))
                .msgPackProcessor((msgs, consumer, config) -> {
                    for (TbProtoQueueMsg<ToEdqsMsg> queueMsg : msgs) {
                        try {
                            ToEdqsMsg msg = queueMsg.getValue();
                            log.trace("Processing message: {}", msg);
                            edqsProcessor.process(msg, EdqsQueue.STATE);
                            if (stateReadCount.incrementAndGet() % 100000 == 0) {
                                log.info("[state] Processed {} msgs", stateReadCount.get());
                            }
                        } catch (Exception e) {
                            log.error("Failed to process message: {}", queueMsg, e); // TODO: do something about the error - e.g. reprocess
                        }
                    }
                    consumer.commit();
                })
                .consumerCreator((config, partitionId) -> queueFactory.createEdqsMsgConsumer(EdqsQueue.STATE))
                .consumerExecutor(consumersExecutor)
                .taskExecutor(mgmtExecutor)
                .scheduler(scheduler)
                .uncaughtErrorHandler(edqsProcessor.getErrorHandler())
                .build();

        ExecutorService backupExecutor = ThingsBoardExecutors.newLimitedTasksExecutor(12, 1000, "events-to-backup-executor");
        eventsConsumer = QueueConsumerManager.<TbProtoQueueMsg<ToEdqsMsg>>builder() // FIXME Slavik writes to the state while we read it, slows down the start. maybe start backup consumer after restore is finished
                .name("edqs-events-to-backup-consumer")
                .pollInterval(config.getPollInterval())
                .msgPackProcessor((msgs, consumer) -> {
                    CountDownLatch resultLatch = new CountDownLatch(msgs.size());
                    for (TbProtoQueueMsg<ToEdqsMsg> queueMsg : msgs) {
                        backupExecutor.submit(() -> {
                            try {
                                ToEdqsMsg msg = queueMsg.getValue();
                                log.trace("Processing message: {}", msg);

                                if (msg.hasEventMsg()) {
                                    EdqsEventMsg eventMsg = msg.getEventMsg();
                                    String key = eventMsg.getKey();
                                    int count = eventsReadCount.incrementAndGet();
                                    if (count % 100000 == 0) {
                                        log.info("[events-to-backup] Processed {} msgs", count);
                                    }
                                    if (eventMsg.hasVersion()) {
                                        if (!versionsStore.isNew(key, eventMsg.getVersion())) {
                                            return;
                                        }
                                    }

                                    TenantId tenantId = getTenantId(msg);
                                    ObjectType objectType = ObjectType.valueOf(eventMsg.getObjectType());
                                    EdqsEventType eventType = EdqsEventType.valueOf(eventMsg.getEventType());
                                    log.debug("[{}] Saving to backup [{}] [{}] [{}]", tenantId, objectType, eventType, key);
                                    stateProducer.send(tenantId, objectType, key, msg);
                                }
                            } catch (Throwable t) {
                                log.error("Failed to process message: {}", queueMsg, t);
                            } finally {
                                resultLatch.countDown();
                            }
                        });
                    }
                    resultLatch.await();
                    consumer.commit();
                })
                .consumerCreator(() -> queueFactory.createEdqsMsgConsumer(EdqsQueue.EVENTS, "events-to-backup-consumer-group")) // shared by all instances consumer group
                .consumerExecutor(consumersExecutor)
                .threadPrefix("edqs-events-to-backup")
                .build();

        stateProducer = EdqsProducer.builder()
                .queue(EdqsQueue.STATE)
                .partitionService(partitionService)
                .producer(queueFactory.createEdqsMsgProducer(EdqsQueue.STATE))
                .build();
    }

    @Override
    public void restore(Set<TopicPartitionInfo> partitions) {
        stateReadCount.set(0); //TODO Slavik: do not support remote mode in monolith setup
        long startTs = System.currentTimeMillis();
        log.info("Restore started for partitions {}", partitions.stream().map(tpi -> tpi.getPartition().orElse(-1)).sorted().toList());
        stateConsumer.doUpdate(partitions); // calling blocking doUpdate instead of update
        stateConsumer.awaitStop(0); // consumers should stop on their own because EdqsQueue.STATE.stopWhenRead is true, we just need to wait
        log.info("Restore finished in {} ms. Processed {} msgs", (System.currentTimeMillis() - startTs), stateReadCount.get());

        if (!initialRestoreDone) {
            initialRestoreDone = true;

            eventsConsumer.subscribe();
            eventsConsumer.launch();
        }
    }

    @Override
    public void save(TenantId tenantId, ObjectType type, String key, EdqsEventType eventType, ToEdqsMsg msg) {
        // do nothing here, backup is done by events consumer
    }

    @Override
    public boolean isReady() {
        return initialRestoreDone;
    }

    private TenantId getTenantId(ToEdqsMsg edqsMsg) {
        return TenantId.fromUUID(new UUID(edqsMsg.getTenantIdMSB(), edqsMsg.getTenantIdLSB()));
    }

    @PreDestroy
    private void preDestroy() {
        stateConsumer.stop();
        stateConsumer.awaitStop();
        eventsConsumer.stop();
        stateProducer.stop();

        consumersExecutor.shutdownNow();
        mgmtExecutor.shutdownNow();
        scheduler.shutdownNow();
    }

}
