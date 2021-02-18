/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.telemetry;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.common.stats.TbApiUsageReportClient;
import org.thingsboard.server.service.apiusage.TbApiUsageStateService;
import org.thingsboard.server.service.queue.TbClusterService;
import org.thingsboard.server.service.subscription.TbSubscriptionUtils;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ashvayka on 27.03.18.
 */
@Service
@Slf4j
public class DefaultTelemetrySubscriptionService extends AbstractSubscriptionService implements TelemetrySubscriptionService {

    private final AttributesService attrService;
    private final TimeseriesService tsService;
    private final EntityViewService entityViewService;
    private final TbApiUsageReportClient apiUsageClient;
    private final TbApiUsageStateService apiUsageStateService;

    private ExecutorService tsCallBackExecutor;

    public DefaultTelemetrySubscriptionService(AttributesService attrService,
                                               TimeseriesService tsService,
                                               EntityViewService entityViewService,
                                               TbClusterService clusterService,
                                               PartitionService partitionService,
                                               TbApiUsageReportClient apiUsageClient,
                                               TbApiUsageStateService apiUsageStateService) {
        super(clusterService, partitionService);
        this.attrService = attrService;
        this.tsService = tsService;
        this.entityViewService = entityViewService;
        this.apiUsageClient = apiUsageClient;
        this.apiUsageStateService = apiUsageStateService;
    }

    @PostConstruct
    public void initExecutor() {
        super.initExecutor();
        tsCallBackExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("ts-service-ts-callback"));
    }

    @Override
    protected String getExecutorPrefix() {
        return "ts";
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (tsCallBackExecutor != null) {
            tsCallBackExecutor.shutdownNow();
        }
        super.shutdownExecutor();
    }

    @Override
    public void saveAndNotify(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts, FutureCallback<Void> callback) {
        saveAndNotify(tenantId, entityId, ts, 0L, callback);
    }

    @Override
    public void saveAndNotify(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts, long ttl, FutureCallback<Void> callback) {
        checkInternalEntity(entityId);
        boolean sysTenant = TenantId.SYS_TENANT_ID.equals(tenantId) || tenantId == null;
        if (sysTenant || apiUsageStateService.getApiUsageState(tenantId).isDbStorageEnabled()) {
            saveAndNotifyInternal(tenantId, entityId, ts, ttl, new FutureCallback<Integer>() {
                @Override
                public void onSuccess(Integer result) {
                    if (!sysTenant && result != null && result > 0) {
                        apiUsageClient.report(tenantId, ApiUsageRecordKey.STORAGE_DP_COUNT, result);
                    }
                    callback.onSuccess(null);
                }

                @Override
                public void onFailure(Throwable t) {
                    callback.onFailure(t);
                }
            });
        } else {
            callback.onFailure(new RuntimeException("DB storage writes are disabled due to API limits!"));
        }
    }

    @Override
    public void saveAndNotifyInternal(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts, FutureCallback<Integer> callback) {
        saveAndNotifyInternal(tenantId, entityId, ts, 0L, callback);
    }

    @Override
    public void saveAndNotifyInternal(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts, long ttl, FutureCallback<Integer> callback) {
        ListenableFuture<Integer> saveFuture = tsService.save(tenantId, entityId, ts, ttl);
        addMainCallback(saveFuture, callback);
        addWsCallback(saveFuture, success -> onTimeSeriesUpdate(tenantId, entityId, ts));
        if (EntityType.DEVICE.equals(entityId.getEntityType()) || EntityType.ASSET.equals(entityId.getEntityType())) {
            Futures.addCallback(this.entityViewService.findEntityViewsByTenantIdAndEntityIdAsync(tenantId, entityId),
                    new FutureCallback<List<EntityView>>() {
                        @Override
                        public void onSuccess(@Nullable List<EntityView> result) {
                            if (result != null) {
                                Map<String, List<TsKvEntry>> tsMap = new HashMap<>();
                                for (TsKvEntry entry : ts) {
                                    tsMap.computeIfAbsent(entry.getKey(), s -> new ArrayList<>()).add(entry);
                                }
                                for (EntityView entityView : result) {
                                    List<String> keys = entityView.getKeys() != null && entityView.getKeys().getTimeseries() != null ?
                                            entityView.getKeys().getTimeseries() : new ArrayList<>(tsMap.keySet());
                                    List<TsKvEntry> entityViewLatest = new ArrayList<>();
                                    long startTs = entityView.getStartTimeMs();
                                    long endTs = entityView.getEndTimeMs() == 0 ? Long.MAX_VALUE : entityView.getEndTimeMs();
                                    for (String key : keys) {
                                        List<TsKvEntry> entries = tsMap.get(key);
                                        if (entries != null) {
                                            Optional<TsKvEntry> tsKvEntry = entries.stream()
                                                    .filter(entry -> entry.getTs() > startTs && entry.getTs() <= endTs)
                                                    .max(Comparator.comparingLong(TsKvEntry::getTs));
                                            tsKvEntry.ifPresent(entityViewLatest::add);
                                        }
                                    }
                                    if (!entityViewLatest.isEmpty()) {
                                        saveLatestAndNotify(tenantId, entityView.getId(), entityViewLatest, new FutureCallback<Void>() {
                                            @Override
                                            public void onSuccess(@Nullable Void tmp) {
                                            }

                                            @Override
                                            public void onFailure(Throwable t) {
                                            }
                                        });
                                    }
                                }
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            log.error("Error while finding entity views by tenantId and entityId", t);
                        }
                    }, MoreExecutors.directExecutor());
        }
    }

    @Override
    public void saveAndNotify(TenantId tenantId, EntityId entityId, String scope, List<AttributeKvEntry> attributes, FutureCallback<Void> callback) {
        saveAndNotify(tenantId, entityId, scope, attributes, true, callback);
    }

    @Override
    public void saveAndNotify(TenantId tenantId, EntityId entityId, String scope, List<AttributeKvEntry> attributes, boolean notifyDevice, FutureCallback<Void> callback) {
        checkInternalEntity(entityId);
        saveAndNotifyInternal(tenantId, entityId, scope, attributes, notifyDevice, callback);
    }

    @Override
    public void saveAndNotifyInternal(TenantId tenantId, EntityId entityId, String scope, List<AttributeKvEntry> attributes, boolean notifyDevice, FutureCallback<Void> callback) {
        ListenableFuture<List<Void>> saveFuture = attrService.save(tenantId, entityId, scope, attributes);
        addVoidCallback(saveFuture, callback);
        addWsCallback(saveFuture, success -> onAttributesUpdate(tenantId, entityId, scope, attributes, notifyDevice));
    }

    @Override
    public void saveLatestAndNotify(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts, FutureCallback<Void> callback) {
        checkInternalEntity(entityId);
        saveLatestAndNotifyInternal(tenantId, entityId, ts, callback);
    }

    @Override
    public void saveLatestAndNotifyInternal(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts, FutureCallback<Void> callback) {
        ListenableFuture<List<Void>> saveFuture = tsService.saveLatest(tenantId, entityId, ts);
        addVoidCallback(saveFuture, callback);
        addWsCallback(saveFuture, success -> onTimeSeriesUpdate(tenantId, entityId, ts));
    }

    @Override
    public void deleteAndNotify(TenantId tenantId, EntityId entityId, String scope, List<String> keys, FutureCallback<Void> callback) {
        checkInternalEntity(entityId);
        deleteAndNotifyInternal(tenantId, entityId, scope, keys, callback);
    }

    @Override
    public void deleteAndNotifyInternal(TenantId tenantId, EntityId entityId, String scope, List<String> keys, FutureCallback<Void> callback) {
        ListenableFuture<List<Void>> deleteFuture = attrService.removeAll(tenantId, entityId, scope, keys);
        addVoidCallback(deleteFuture, callback);
        addWsCallback(deleteFuture, success -> onAttributesDelete(tenantId, entityId, scope, keys));
    }

    @Override
    public void deleteLatest(TenantId tenantId, EntityId entityId, List<String> keys, FutureCallback<Void> callback) {
        checkInternalEntity(entityId);
        deleteLatestInternal(tenantId, entityId, keys, callback);
    }

    @Override
    public void deleteLatestInternal(TenantId tenantId, EntityId entityId, List<String> keys, FutureCallback<Void> callback) {
        ListenableFuture<List<Void>> deleteFuture = tsService.removeLatest(tenantId, entityId, keys);
        addVoidCallback(deleteFuture, callback);
    }

    @Override
    public void deleteAllLatest(TenantId tenantId, EntityId entityId, FutureCallback<Collection<String>> callback) {
        ListenableFuture<Collection<String>> deleteFuture = tsService.removeAllLatest(tenantId, entityId);
        Futures.addCallback(deleteFuture, new FutureCallback<Collection<String>>() {
            @Override
            public void onSuccess(@Nullable Collection<String> result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onFailure(t);
            }
        }, tsCallBackExecutor);
    }

    @Override
    public void saveAttrAndNotify(TenantId tenantId, EntityId entityId, String scope, String key, long value, FutureCallback<Void> callback) {
        saveAndNotify(tenantId, entityId, scope, Collections.singletonList(new BaseAttributeKvEntry(new LongDataEntry(key, value)
                , System.currentTimeMillis())), callback);
    }

    @Override
    public void saveAttrAndNotify(TenantId tenantId, EntityId entityId, String scope, String key, String value, FutureCallback<Void> callback) {
        saveAndNotify(tenantId, entityId, scope, Collections.singletonList(new BaseAttributeKvEntry(new StringDataEntry(key, value)
                , System.currentTimeMillis())), callback);
    }

    @Override
    public void saveAttrAndNotify(TenantId tenantId, EntityId entityId, String scope, String key, double value, FutureCallback<Void> callback) {
        saveAndNotify(tenantId, entityId, scope, Collections.singletonList(new BaseAttributeKvEntry(new DoubleDataEntry(key, value)
                , System.currentTimeMillis())), callback);
    }

    @Override
    public void saveAttrAndNotify(TenantId tenantId, EntityId entityId, String scope, String key, boolean value, FutureCallback<Void> callback) {
        saveAndNotify(tenantId, entityId, scope, Collections.singletonList(new BaseAttributeKvEntry(new BooleanDataEntry(key, value)
                , System.currentTimeMillis())), callback);
    }

    private void onAttributesUpdate(TenantId tenantId, EntityId entityId, String scope, List<AttributeKvEntry> attributes, boolean notifyDevice) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, tenantId, entityId);
        if (currentPartitions.contains(tpi)) {
            if (subscriptionManagerService.isPresent()) {
                subscriptionManagerService.get().onAttributesUpdate(tenantId, entityId, scope, attributes, notifyDevice, TbCallback.EMPTY);
            } else {
                log.warn("Possible misconfiguration because subscriptionManagerService is null!");
            }
        } else {
            TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toAttributesUpdateProto(tenantId, entityId, scope, attributes);
            clusterService.pushMsgToCore(tpi, entityId.getId(), toCoreMsg, null);
        }
    }

    private void onAttributesDelete(TenantId tenantId, EntityId entityId, String scope, List<String> keys) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, tenantId, entityId);
        if (currentPartitions.contains(tpi)) {
            if (subscriptionManagerService.isPresent()) {
                subscriptionManagerService.get().onAttributesDelete(tenantId, entityId, scope, keys, TbCallback.EMPTY);
            } else {
                log.warn("Possible misconfiguration because subscriptionManagerService is null!");
            }
        } else {
            TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toAttributesDeleteProto(tenantId, entityId, scope, keys);
            clusterService.pushMsgToCore(tpi, entityId.getId(), toCoreMsg, null);
        }
    }

    private void onTimeSeriesUpdate(TenantId tenantId, EntityId entityId, List<TsKvEntry> ts) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, tenantId, entityId);
        if (currentPartitions.contains(tpi)) {
            if (subscriptionManagerService.isPresent()) {
                subscriptionManagerService.get().onTimeSeriesUpdate(tenantId, entityId, ts, TbCallback.EMPTY);
            } else {
                log.warn("Possible misconfiguration because subscriptionManagerService is null!");
            }
        } else {
            TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toTimeseriesUpdateProto(tenantId, entityId, ts);
            clusterService.pushMsgToCore(tpi, entityId.getId(), toCoreMsg, null);
        }
    }

    private <S> void addVoidCallback(ListenableFuture<S> saveFuture, final FutureCallback<Void> callback) {
        Futures.addCallback(saveFuture, new FutureCallback<S>() {
            @Override
            public void onSuccess(@Nullable S result) {
                callback.onSuccess(null);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onFailure(t);
            }
        }, tsCallBackExecutor);
    }

    private <S> void addMainCallback(ListenableFuture<S> saveFuture, final FutureCallback<S> callback) {
        Futures.addCallback(saveFuture, new FutureCallback<S>() {
            @Override
            public void onSuccess(@Nullable S result) {
                callback.onSuccess(result);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onFailure(t);
            }
        }, tsCallBackExecutor);
    }

    private void checkInternalEntity(EntityId entityId) {
        if (EntityType.API_USAGE_STATE.equals(entityId.getEntityType())) {
            throw new RuntimeException("Can't update API Usage State!");
        }
    }

}
