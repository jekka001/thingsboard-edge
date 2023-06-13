/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.rule.engine.metadata;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.thingsboard.common.util.DonAsynchron.withCallback;
import static org.thingsboard.server.common.data.DataConstants.CLIENT_SCOPE;
import static org.thingsboard.server.common.data.DataConstants.LATEST_TS;
import static org.thingsboard.server.common.data.DataConstants.SERVER_SCOPE;
import static org.thingsboard.server.common.data.DataConstants.SHARED_SCOPE;

@Slf4j
public abstract class TbAbstractGetAttributesNode<C extends TbGetAttributesNodeConfiguration, T extends EntityId> extends TbAbstractNodeWithFetchTo<C> {

    private static final String VALUE = "value";
    private static final String TS = "ts";
    private boolean isTellFailureIfAbsent;
    private boolean getLatestValueWithTs;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        super.init(ctx, configuration);
        getLatestValueWithTs = config.isGetLatestValueWithTs();
        isTellFailureIfAbsent = BooleanUtils.toBooleanDefaultIfNull(config.isTellFailureIfAbsent(), true);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) throws TbNodeException {
        var msgDataAsObjectNode = FetchTo.DATA.equals(fetchTo) ? getMsgDataAsObjectNode(msg) : null;
        withCallback(
                findEntityIdAsync(ctx, msg),
                entityId -> safePutAttributes(ctx, msg, msgDataAsObjectNode, entityId),
                t -> ctx.tellFailure(msg, t), ctx.getDbCallbackExecutor());
    }

    protected abstract ListenableFuture<T> findEntityIdAsync(TbContext ctx, TbMsg msg);

    private void safePutAttributes(TbContext ctx, TbMsg msg, ObjectNode msgDataNode, T entityId) {
        Set<TbPair<String, List<String>>> failuresPairSet = ConcurrentHashMap.newKeySet();
        var getKvEntryPairFutures = Futures.allAsList(
                getLatestTelemetry(ctx, entityId, TbNodeUtils.processPatterns(config.getLatestTsKeyNames(), msg), failuresPairSet),
                getAttrAsync(ctx, entityId, CLIENT_SCOPE, TbNodeUtils.processPatterns(config.getClientAttributeNames(), msg), failuresPairSet),
                getAttrAsync(ctx, entityId, SHARED_SCOPE, TbNodeUtils.processPatterns(config.getSharedAttributeNames(), msg), failuresPairSet),
                getAttrAsync(ctx, entityId, SERVER_SCOPE, TbNodeUtils.processPatterns(config.getServerAttributeNames(), msg), failuresPairSet)
        );
        withCallback(getKvEntryPairFutures, futuresList -> {
            var msgMetaData = msg.getMetaData().copy();
            futuresList.stream().filter(Objects::nonNull).forEach(kvEntriesPair -> {
                var keyScope = kvEntriesPair.getFirst();
                var kvEntryList = kvEntriesPair.getSecond();
                var prefix = getPrefix(keyScope);
                kvEntryList.forEach(kvEntry -> {
                    String targetKey = prefix + kvEntry.getKey();
                    enrichMessage(msgDataNode, msgMetaData, kvEntry, targetKey);
                });
            });
            TbMsg outMsg = transformMessage(msg, msgDataNode, msgMetaData);
            if (failuresPairSet.isEmpty()) {
                ctx.tellSuccess(outMsg);
            } else {
                ctx.tellFailure(outMsg, reportFailures(failuresPairSet));
            }
        }, t -> ctx.tellFailure(msg, t), MoreExecutors.directExecutor());
    }

    private ListenableFuture<TbPair<String, List<AttributeKvEntry>>> getAttrAsync(
            TbContext ctx,
            EntityId entityId,
            String scope,
            List<String> keys,
            Set<TbPair<String, List<String>>> failuresPairSet
    ) {
        if (CollectionUtils.isEmpty(keys)) {
            return Futures.immediateFuture(null);
        }
        var attributeKvEntryListFuture = ctx.getAttributesService().find(ctx.getTenantId(), entityId, scope, keys);
        return Futures.transform(attributeKvEntryListFuture, attributeKvEntryList -> {
            if (isTellFailureIfAbsent && attributeKvEntryList.size() != keys.size()) {
                List<String> nonExistentKeys = getNonExistentKeys(attributeKvEntryList, keys);
                failuresPairSet.add(new TbPair<>(scope, nonExistentKeys));
            }
            return new TbPair<>(scope, attributeKvEntryList);
        }, ctx.getDbCallbackExecutor());
    }

    private ListenableFuture<TbPair<String, List<TsKvEntry>>> getLatestTelemetry(TbContext ctx, EntityId entityId, List<String> keys, Set<TbPair<String, List<String>>> failuresPairSet) {
        if (CollectionUtils.isEmpty(keys)) {
            return Futures.immediateFuture(null);
        }
        ListenableFuture<List<TsKvEntry>> latestTelemetryFutures = ctx.getTimeseriesService().findLatest(ctx.getTenantId(), entityId, keys);
        return Futures.transform(latestTelemetryFutures, tsKvEntries -> {
            var listTsKvEntry = new ArrayList<TsKvEntry>();
            var nonExistentKeys = new ArrayList<String>();
            tsKvEntries.forEach(tsKvEntry -> {
                if (tsKvEntry.getValue() == null) {
                    if (isTellFailureIfAbsent) {
                        nonExistentKeys.add(tsKvEntry.getKey());
                    }
                } else if (getLatestValueWithTs) {
                    listTsKvEntry.add(getValueWithTs(tsKvEntry));
                } else {
                    listTsKvEntry.add(new BasicTsKvEntry(tsKvEntry.getTs(), tsKvEntry));
                }
            });
            if (isTellFailureIfAbsent && !nonExistentKeys.isEmpty()) {
                failuresPairSet.add(new TbPair<>(LATEST_TS, nonExistentKeys));
            }
            return new TbPair<>(LATEST_TS, listTsKvEntry);
        }, ctx.getDbCallbackExecutor());
    }

    private TsKvEntry getValueWithTs(TsKvEntry tsKvEntry) {
        var mapper = FetchTo.DATA.equals(fetchTo) ? JacksonUtil.OBJECT_MAPPER : JacksonUtil.ALLOW_UNQUOTED_FIELD_NAMES_MAPPER;
        var value = JacksonUtil.newObjectNode(mapper);
        value.put(TS, tsKvEntry.getTs());
        JacksonUtil.addKvEntry(value, tsKvEntry, VALUE, mapper);
        return new BasicTsKvEntry(tsKvEntry.getTs(), new JsonDataEntry(tsKvEntry.getKey(), value.toString()));
    }

    private String getPrefix(String scope) {
        var prefix = "";
        switch (scope) {
            case CLIENT_SCOPE:
                prefix = "cs_";
                break;
            case SHARED_SCOPE:
                prefix = "shared_";
                break;
            case SERVER_SCOPE:
                prefix = "ss_";
                break;
        }
        return prefix;
    }

    private List<String> getNonExistentKeys(List<AttributeKvEntry> existingAttributesKvEntry, List<String> allKeys) {
        List<String> existingKeys = existingAttributesKvEntry.stream().map(KvEntry::getKey).collect(Collectors.toList());
        return allKeys.stream().filter(key -> !existingKeys.contains(key)).collect(Collectors.toList());
    }

    private RuntimeException reportFailures(Set<TbPair<String, List<String>>> failuresPairSet) {
        var errorMessage = new StringBuilder("The following attribute/telemetry keys is not present in the DB: ").append("\n");
        failuresPairSet.forEach(failurePair -> {
            String scope = failurePair.getFirst();
            List<String> nonExistentKeys = failurePair.getSecond();
            errorMessage.append("\t").append("[").append(scope).append("]:").append(nonExistentKeys.toString()).append("\n");
        });
        failuresPairSet.clear();
        return new RuntimeException(errorMessage.toString());
    }

}
