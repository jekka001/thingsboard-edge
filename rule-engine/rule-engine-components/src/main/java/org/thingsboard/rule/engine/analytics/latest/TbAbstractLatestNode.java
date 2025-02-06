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
package org.thingsboard.rule.engine.analytics.latest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.thingsboard.common.util.DonAsynchron.withCallback;
import static org.thingsboard.server.common.data.DataConstants.QUEUE_NAME;
import static org.thingsboard.server.common.data.msg.TbNodeConnectionType.SUCCESS;

@Slf4j
public abstract class TbAbstractLatestNode<C extends TbAbstractLatestNodeConfiguration> implements TbNode {

    private final Gson gson = new Gson();

    protected C config;
    private long delay;
    private long lastScheduledTs;
    private UUID nextTickId;
    protected String queueName;
    protected String outMsgType;
    private ParentEntitiesQuery parentEntitiesQuery;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = loadMapperNodeConfig(configuration);
        this.queueName = ctx.getQueueName();
        this.delay = config.getPeriodTimeUnit().toMillis(config.getPeriodValue());
        this.outMsgType = StringUtils.isNotBlank(config.getOutMsgType()) ? config.getOutMsgType() : TbMsgType.POST_TELEMETRY_REQUEST.name();
        this.parentEntitiesQuery = config.getParentEntitiesQuery();
        validateConfig(ctx);
        scheduleTickMsg(ctx);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (msg.isTypeOf(tickMessageType()) && msg.getId().equals(nextTickId)) {
            withCallback(aggregate(ctx),
                    m -> scheduleTickMsg(ctx),
                    t -> {
                        ctx.tellFailure(msg, t);
                        scheduleTickMsg(ctx);
                    });
        }
    }

    private void scheduleTickMsg(TbContext ctx) {
        long curTs = System.currentTimeMillis();
        if (lastScheduledTs == 0L) {
            lastScheduledTs = curTs;
        }
        lastScheduledTs = lastScheduledTs + delay;
        long curDelay = Math.max(0L, (lastScheduledTs - curTs));
        TbMsg tickMsg = ctx.newMsg(queueName, tickMessageType(), ctx.getSelfId(), TbMsgMetaData.EMPTY, TbMsg.EMPTY_STRING);
        nextTickId = tickMsg.getId();
        ctx.tellSelf(tickMsg, curDelay);
    }

    private ListenableFuture<List<TbMsg>> aggregate(TbContext ctx) {
        ListenableFuture<List<EntityId>> parentEntityIdsFuture = parentEntitiesQuery.getParentEntitiesAsync(ctx);
        return Futures.transformAsync(parentEntityIdsFuture, parentEntityIds -> {
            List<ListenableFuture<TbMsg>> msgFutures = new ArrayList<>();
            String dataTs = Long.toString(System.currentTimeMillis());
            parentEntityIds.forEach(parentEntityId -> {
                Map<EntityId, List<ListenableFuture<Optional<JsonObject>>>> aggregateFuturesMap = doParentAggregations(ctx, parentEntityId);
                aggregateFuturesMap.forEach((originatorId, aggregateFutures) -> aggregateFutures.forEach(aggregateFuture -> {
                    ListenableFuture<Optional<JsonObject>>
                            aggregateFutureWithFallback = Futures.catching(aggregateFuture, Throwable.class, e -> {
                        TbMsg msg = TbMsg.newMsg()
                                .queueName(queueName)
                                .type(outMsgType)
                                .originator(originatorId)
                                .copyMetaData(TbMsgMetaData.EMPTY)
                                .data(TbMsg.EMPTY_STRING)
                                .build();
                        ctx.enqueueForTellFailure(msg, e.getMessage());
                        return Optional.empty();
                    }, MoreExecutors.directExecutor());
                    ListenableFuture<TbMsg> msgFuture = Futures.transform(aggregateFutureWithFallback, element -> {
                        if (element.isPresent()) {
                            TbMsgMetaData metaData = new TbMsgMetaData();
                            metaData.putValue("ts", dataTs);
                            JsonObject messageData = element.get();
                            TbMsg msg = TbMsg.newMsg()
                                    .queueName(queueName)
                                    .type(outMsgType)
                                    .originator(originatorId)
                                    .copyMetaData(metaData)
                                    .data(gson.toJson(messageData))
                                    .build();
                            ctx.enqueueForTellNext(msg, SUCCESS);
                            return msg;
                        } else {
                            return null;
                        }
                    }, MoreExecutors.directExecutor());
                    msgFutures.add(msgFuture);
                }));
            });
            return Futures.allAsList(msgFutures);
        }, ctx.getDbCallbackExecutor());
    }

    protected abstract C loadMapperNodeConfig(TbNodeConfiguration configuration) throws TbNodeException;

    protected abstract TbMsgType tickMessageType();

    protected abstract Map<EntityId, List<ListenableFuture<Optional<JsonObject>>>> doParentAggregations(TbContext ctx, EntityId parentEntityId);

    private void validateConfig(TbContext ctx) throws TbNodeException {
        if (parentEntitiesQuery instanceof ParentEntitiesSingleEntity) {
            ctx.checkTenantEntity(((ParentEntitiesSingleEntity) parentEntitiesQuery).getEntityId());
        } else if (parentEntitiesQuery instanceof  ParentEntitiesGroup) {
            ctx.checkTenantEntity(((ParentEntitiesGroup) parentEntitiesQuery).getEntityGroupId());
        } else if (parentEntitiesQuery instanceof  ParentEntitiesRelationsQuery) {
            ctx.checkTenantEntity(((ParentEntitiesRelationsQuery) parentEntitiesQuery).getRootEntityId());
        }
    }

    @Override
    public TbPair<Boolean, JsonNode> upgrade(int fromVersion, JsonNode oldConfiguration) throws TbNodeException {
        boolean hasChanges = false;
        switch (fromVersion) {
            case 0:
                if (!oldConfiguration.hasNonNull("outMsgType")) {
                    ((ObjectNode) oldConfiguration).put("outMsgType", TbMsgType.POST_TELEMETRY_REQUEST.name());
                    hasChanges = true;
                }
            case 1:
                if (oldConfiguration.has(QUEUE_NAME)) {
                    hasChanges = true;
                    ((ObjectNode) oldConfiguration).remove(QUEUE_NAME);
                }
                break;
        }
        return new TbPair<>(hasChanges, oldConfiguration);
    }

}
