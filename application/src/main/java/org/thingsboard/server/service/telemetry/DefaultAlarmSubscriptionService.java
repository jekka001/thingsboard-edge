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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmFilter;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQuery;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.query.AlarmData;
import org.thingsboard.server.common.data.query.AlarmDataPageLink;
import org.thingsboard.server.common.data.query.AlarmDataQuery;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.dao.alarm.AlarmOperationResult;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.discovery.PartitionChangeEvent;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.service.queue.TbClusterService;
import org.thingsboard.server.service.subscription.SubscriptionManagerService;
import org.thingsboard.server.service.subscription.TbSubscriptionUtils;
import org.thingsboard.server.service.telemetry.sub.AlarmSubscriptionUpdate;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Created by ashvayka on 27.03.18.
 */
@Service
@Slf4j
public class DefaultAlarmSubscriptionService extends AbstractSubscriptionService implements AlarmSubscriptionService {

    private final AlarmService alarmService;

    public DefaultAlarmSubscriptionService(TbClusterService clusterService,
                                           PartitionService partitionService,
                                           AlarmService alarmService) {
        super(clusterService, partitionService);
        this.alarmService = alarmService;
    }

    @Autowired(required = false)
    public void setSubscriptionManagerService(Optional<SubscriptionManagerService> subscriptionManagerService) {
        this.subscriptionManagerService = subscriptionManagerService;
    }

    @Override
    String getExecutorPrefix() {
        return "alarm";
    }

    @Override
    public Alarm createOrUpdateAlarm(Alarm alarm) {
        AlarmOperationResult result = alarmService.createOrUpdateAlarm(alarm);
        if (result.isSuccessful()) {
            onAlarmUpdated(result);
        }
        return result.getAlarm();
    }

    @Override
    public Boolean deleteAlarm(TenantId tenantId, AlarmId alarmId) {
        AlarmOperationResult result = alarmService.deleteAlarm(tenantId, alarmId);
        onAlarmDeleted(result);
        return result.isSuccessful();
    }

    @Override
    public ListenableFuture<Boolean> ackAlarm(TenantId tenantId, AlarmId alarmId, long ackTs) {
        ListenableFuture<AlarmOperationResult> result = alarmService.ackAlarm(tenantId, alarmId, ackTs);
        Futures.addCallback(result, new AlarmUpdateCallback(), wsCallBackExecutor);
        return Futures.transform(result, AlarmOperationResult::isSuccessful, wsCallBackExecutor);
    }

    @Override
    public ListenableFuture<Boolean> clearAlarm(TenantId tenantId, AlarmId alarmId, JsonNode details, long clearTs) {
        ListenableFuture<AlarmOperationResult> result = alarmService.clearAlarm(tenantId, alarmId, details, clearTs);
        Futures.addCallback(result, new AlarmUpdateCallback(), wsCallBackExecutor);
        return Futures.transform(result, AlarmOperationResult::isSuccessful, wsCallBackExecutor);
    }

    @Override
    public ListenableFuture<Alarm> findAlarmByIdAsync(TenantId tenantId, AlarmId alarmId) {
        return alarmService.findAlarmByIdAsync(tenantId, alarmId);
    }

    @Override
    public ListenableFuture<AlarmInfo> findAlarmInfoByIdAsync(TenantId tenantId, AlarmId alarmId) {
        return alarmService.findAlarmInfoByIdAsync(tenantId, alarmId);
    }

    @Override
    public ListenableFuture<PageData<AlarmInfo>> findAlarms(TenantId tenantId, AlarmQuery query) {
        return alarmService.findAlarms(tenantId, query);
    }

    @Override
    public AlarmSeverity findHighestAlarmSeverity(TenantId tenantId, EntityId entityId, AlarmSearchStatus alarmSearchStatus, AlarmStatus alarmStatus) {
        return alarmService.findHighestAlarmSeverity(tenantId, entityId, alarmSearchStatus, alarmStatus);
    }

    @Override
    public PageData<AlarmData> findAlarmDataByQueryForEntities(TenantId tenantId, CustomerId customerId, MergedUserPermissions mergedUserPermissions,
                                                               AlarmDataQuery query, Collection<EntityId> orderedEntityIds) {
        return alarmService.findAlarmDataByQueryForEntities(tenantId, customerId, mergedUserPermissions, query, orderedEntityIds);
    }

    @Override
    public ListenableFuture<Alarm> findLatestByOriginatorAndType(TenantId tenantId, EntityId originator, String type) {
        return alarmService.findLatestByOriginatorAndType(tenantId, originator, type);
    }

    @Override
    public List<Long> findAlarmCounts(TenantId tenantId, AlarmQuery query, List<AlarmFilter> filters) {
        return alarmService.findAlarmCounts(tenantId, query, filters);
    }

    private void onAlarmUpdated(AlarmOperationResult result) {
        wsCallBackExecutor.submit(() -> {
            Alarm alarm = result.getAlarm();
            TenantId tenantId = result.getAlarm().getTenantId();
            for (EntityId entityId : result.getPropagatedEntitiesList()) {
                TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, tenantId, entityId);
                if (currentPartitions.contains(tpi)) {
                    if (subscriptionManagerService.isPresent()) {
                        subscriptionManagerService.get().onAlarmUpdate(tenantId, entityId, alarm, TbCallback.EMPTY);
                    } else {
                        log.warn("Possible misconfiguration because subscriptionManagerService is null!");
                    }
                } else {
                    TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toAlarmUpdateProto(tenantId, entityId, alarm);
                    clusterService.pushMsgToCore(tpi, entityId.getId(), toCoreMsg, null);
                }
            }
        });
    }

    private void onAlarmDeleted(AlarmOperationResult result) {
        wsCallBackExecutor.submit(() -> {
            Alarm alarm = result.getAlarm();
            TenantId tenantId = result.getAlarm().getTenantId();
            for (EntityId entityId : result.getPropagatedEntitiesList()) {
                TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, tenantId, entityId);
                if (currentPartitions.contains(tpi)) {
                    if (subscriptionManagerService.isPresent()) {
                        subscriptionManagerService.get().onAlarmDeleted(tenantId, entityId, alarm, TbCallback.EMPTY);
                    } else {
                        log.warn("Possible misconfiguration because subscriptionManagerService is null!");
                    }
                } else {
                    TransportProtos.ToCoreMsg toCoreMsg = TbSubscriptionUtils.toAlarmDeletedProto(tenantId, entityId, alarm);
                    clusterService.pushMsgToCore(tpi, entityId.getId(), toCoreMsg, null);
                }
            }
        });
    }

    private class AlarmUpdateCallback implements FutureCallback<AlarmOperationResult> {
        @Override
        public void onSuccess(@Nullable AlarmOperationResult result) {
            onAlarmUpdated(result);
        }

        @Override
        public void onFailure(Throwable t) {
            log.warn("Failed to update alarm", t);
        }
    }

}
