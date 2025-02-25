/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.cloud;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.CloudUtils;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmComment;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.cloud.CloudEventService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbCoreComponent;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@TbCoreComponent
@Slf4j
public class DefaultCloudNotificationService implements CloudNotificationService {

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private CloudEventService cloudEventService;

    private ExecutorService dbCallBackExecutor;

    @PostConstruct
    public void initExecutor() {
        dbCallBackExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("cloud-notifications"));
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (dbCallBackExecutor != null) {
            dbCallBackExecutor.shutdownNow();
        }
    }

    @Override
    public void pushNotificationToCloud(TransportProtos.CloudNotificationMsgProto cloudNotificationMsg, TbCallback callback) {
        try {
            TenantId tenantId = TenantId.fromUUID(new UUID(cloudNotificationMsg.getTenantIdMSB(), cloudNotificationMsg.getTenantIdLSB()));
            CloudEventType cloudEventType = CloudEventType.valueOf(cloudNotificationMsg.getCloudEventType());
            ListenableFuture<Void> future;
            switch (cloudEventType) {
                // TODO: voba - handle cloud updates
                case EDGE, ASSET, DEVICE, ASSET_PROFILE, DEVICE_PROFILE, ENTITY_VIEW, DASHBOARD, RULE_CHAIN, TB_RESOURCE ->
                        future = processEntity(tenantId, cloudNotificationMsg);
                case ALARM -> future = processAlarm(tenantId, cloudNotificationMsg);
                case RELATION -> future = processRelation(tenantId, cloudNotificationMsg);
                case ALARM_COMMENT -> future = processAlarmComment(tenantId, cloudNotificationMsg);
                default -> {
                    log.warn("Cloud event type [{}] is not designed to be pushed to cloud", cloudEventType);
                    future = Futures.immediateFuture(null);
                }
            }
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Void unused) {
                    callback.onSuccess();
                }

                @Override
                public void onFailure(Throwable throwable) {
                    callBackFailure(cloudNotificationMsg, callback, throwable);
                }
            }, dbCallBackExecutor);
        } catch (Exception e) {
            callBackFailure(cloudNotificationMsg, callback, e);
        }
    }

    private void callBackFailure(TransportProtos.CloudNotificationMsgProto cloudNotificationMsg, TbCallback callback, Throwable throwable) {
        log.error("Can't push to cloud updates, cloudNotificationMsg [{}]", cloudNotificationMsg, throwable);
        callback.onFailure(throwable);
    }


    private ListenableFuture<Void> processEntity(TenantId tenantId, TransportProtos.CloudNotificationMsgProto cloudNotificationMsg) {
        EdgeEventActionType cloudEventActionType = EdgeEventActionType.valueOf(cloudNotificationMsg.getCloudEventAction());
        CloudEventType cloudEventType = CloudEventType.valueOf(cloudNotificationMsg.getCloudEventType());
        EntityId entityId = EntityIdFactory.getByCloudEventTypeAndUuid(cloudEventType, new UUID(cloudNotificationMsg.getEntityIdMSB(), cloudNotificationMsg.getEntityIdLSB()));
        return switch (cloudEventActionType) {
            case ADDED, UPDATED, CREDENTIALS_UPDATED, ADDED_TO_ENTITY_GROUP, DELETED, REMOVED_FROM_ENTITY_GROUP -> {
                EntityGroupId entityGroupId = null;
                if (cloudNotificationMsg.getEntityGroupIdMSB() != 0 && cloudNotificationMsg.getEntityGroupIdLSB() != 0) {
                    entityGroupId = new EntityGroupId(
                            new UUID(cloudNotificationMsg.getEntityGroupIdMSB(), cloudNotificationMsg.getEntityGroupIdLSB()));
                }
                yield cloudEventService.saveCloudEventAsync(tenantId, cloudEventType, cloudEventActionType, entityId, null, entityGroupId);
            }
            default -> Futures.immediateFuture(null);
        };
    }

    private ListenableFuture<Void> processAlarm(TenantId tenantId, TransportProtos.CloudNotificationMsgProto cloudNotificationMsg) {
        EdgeEventActionType actionType = EdgeEventActionType.valueOf(cloudNotificationMsg.getCloudEventAction());
        AlarmId alarmId = new AlarmId(new UUID(cloudNotificationMsg.getEntityIdMSB(), cloudNotificationMsg.getEntityIdLSB()));
        if (EdgeEventActionType.DELETED.equals(actionType) || EdgeEventActionType.ALARM_DELETE.equals(actionType)) {
            Alarm deletedAlarm = JacksonUtil.fromString(cloudNotificationMsg.getEntityBody(), Alarm.class);
            return cloudEventService.saveCloudEventAsync(tenantId, CloudEventType.ALARM, actionType, alarmId, JacksonUtil.valueToTree(deletedAlarm), null);
        }
        ListenableFuture<Alarm> future = alarmService.findAlarmByIdAsync(tenantId, alarmId);
        return Futures.transformAsync(future, alarm -> {
            if (alarm != null) {
                CloudEventType cloudEventType = CloudUtils.getCloudEventTypeByEntityType(alarm.getOriginator().getEntityType());
                if (cloudEventType != null) {
                    return cloudEventService.saveCloudEventAsync(tenantId, CloudEventType.ALARM, EdgeEventActionType.valueOf(cloudNotificationMsg.getCloudEventAction()), alarmId, null, null);
                }
            }
            return Futures.immediateFuture(null);
        }, dbCallBackExecutor);
    }

    public ListenableFuture<Void> processAlarmComment(TenantId tenantId, TransportProtos.CloudNotificationMsgProto cloudNotificationMsg) {
        EdgeEventActionType actionType = EdgeEventActionType.valueOf(cloudNotificationMsg.getCloudEventAction());
        AlarmId alarmId = new AlarmId(new UUID(cloudNotificationMsg.getEntityIdMSB(), cloudNotificationMsg.getEntityIdLSB()));
        AlarmComment alarmComment = JacksonUtil.fromString(cloudNotificationMsg.getEntityBody(), AlarmComment.class);
        if (alarmComment == null) {
            return Futures.immediateFuture(null);
        }
        return cloudEventService.saveCloudEventAsync(tenantId, CloudEventType.ALARM_COMMENT, actionType, alarmId, JacksonUtil.valueToTree(alarmComment), null);
    }

    private ListenableFuture<Void> processRelation(TenantId tenantId, TransportProtos.CloudNotificationMsgProto cloudNotificationMsg) {
        EntityRelation relation = JacksonUtil.fromString(cloudNotificationMsg.getEntityBody(), EntityRelation.class);
        return cloudEventService.saveCloudEventAsync(tenantId, CloudEventType.RELATION, EdgeEventActionType.valueOf(cloudNotificationMsg.getCloudEventAction()), null, JacksonUtil.valueToTree(relation), null);
    }

}
