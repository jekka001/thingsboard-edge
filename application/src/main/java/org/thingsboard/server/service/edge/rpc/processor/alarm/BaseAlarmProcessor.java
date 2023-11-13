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
package org.thingsboard.server.service.edge.rpc.processor.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmCreateOrUpdateActiveRequest;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.alarm.AlarmUpdateRequest;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.utils.EdgeVersionUtils;

import java.util.UUID;

@Slf4j
public abstract class BaseAlarmProcessor extends BaseEdgeProcessor {

    public ListenableFuture<Void> processAlarmMsg(TenantId tenantId, AlarmUpdateMsg alarmUpdateMsg, EdgeVersion edgeVersion) {
        log.trace("[{}] processAlarmMsg [{}]", tenantId, alarmUpdateMsg);
        AlarmId alarmId = new AlarmId(new UUID(alarmUpdateMsg.getIdMSB(), alarmUpdateMsg.getIdLSB()));
        boolean isEdgeVersionOlderThan_3_6_2 = EdgeVersionUtils.isEdgeVersionOlderThan_3_6_2(edgeVersion);
        Alarm alarm = isEdgeVersionOlderThan_3_6_2 ? createDeprecatedAlarm(tenantId, alarmUpdateMsg)
                : JacksonUtil.fromStringIgnoreUnknownProperties(alarmUpdateMsg.getEntity(), Alarm.class);
        if (alarm == null) {
            throw new RuntimeException("[{" + tenantId + "}] alarmUpdateMsg {" + alarmUpdateMsg + "} cannot be converted to alarm");
        }
        EntityId originatorId = isEdgeVersionOlderThan_3_6_2
                ? getAlarmOriginator(tenantId, alarmUpdateMsg.getOriginatorName(), EntityType.valueOf(alarmUpdateMsg.getOriginatorType()))
                : alarm.getOriginator();
        if (originatorId == null) {
            log.warn("[{}] Originator not found for the alarm msg {}", tenantId, alarmUpdateMsg);
            return Futures.immediateFuture(null);
        }
        try {

            switch (alarmUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    alarm.setId(alarmId);
                    alarm.setOriginator(originatorId);
                    if (UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE.equals(alarmUpdateMsg.getMsgType())) {
                        alarmService.createAlarm(AlarmCreateOrUpdateActiveRequest.fromAlarm(alarm, null, alarmId));
                    } else {
                        alarmService.updateAlarm(AlarmUpdateRequest.fromAlarm(alarm));
                    }
                    break;
                case ALARM_ACK_RPC_MESSAGE:
                    Alarm alarmToAck = alarmService.findAlarmById(tenantId, alarmId);
                    if (alarmToAck != null) {
                        alarmService.acknowledgeAlarm(tenantId, alarmId, alarm.getAckTs());
                    }
                    break;
                case ALARM_CLEAR_RPC_MESSAGE:
                    Alarm alarmToClear = alarmService.findAlarmById(tenantId, alarmId);
                    if (alarmToClear != null) {
                        alarmService.clearAlarm(tenantId, alarmId, alarm.getClearTs(), alarm.getDetails());
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    Alarm alarmToDelete = alarmService.findAlarmById(tenantId, alarmId);
                    if (alarmToDelete != null) {
                        alarmService.delAlarm(tenantId, alarmId);
                    }
                    break;
                case UNRECOGNIZED:
                default:
                    return handleUnsupportedMsgType(alarmUpdateMsg.getMsgType());
            }
        } catch (Exception e) {
            log.error("[{}] Failed to process alarm update msg [{}]", tenantId, alarmUpdateMsg, e);
            return Futures.immediateFailedFuture(e);
        }
        return Futures.immediateFuture(null);
    }

    private Alarm createDeprecatedAlarm(TenantId tenantId, AlarmUpdateMsg alarmUpdateMsg) {
        Alarm alarm = new Alarm();
        alarm.setTenantId(tenantId);
        alarm.setType(alarmUpdateMsg.getName());
        alarm.setSeverity(AlarmSeverity.valueOf(alarmUpdateMsg.getSeverity()));
        alarm.setStartTs(alarmUpdateMsg.getStartTs());
        AlarmStatus alarmStatus = AlarmStatus.valueOf(alarmUpdateMsg.getStatus());
        alarm.setClearTs(alarmUpdateMsg.getClearTs());
        alarm.setPropagate(alarmUpdateMsg.getPropagate());
        alarm.setCleared(alarmStatus.isCleared());
        alarm.setAcknowledged(alarmStatus.isAck());
        alarm.setAckTs(alarmUpdateMsg.getAckTs());
        alarm.setEndTs(alarmUpdateMsg.getEndTs());
        alarm.setDetails(JacksonUtil.toJsonNode(alarmUpdateMsg.getDetails()));
        return alarm;
    }

    public AlarmUpdateMsg convertAlarmEventToAlarmMsg(TenantId tenantId, UUID entityId, EdgeEventActionType actionType, JsonNode body, EdgeVersion edgeVersion) {
        AlarmId alarmId = new AlarmId(entityId);
        UpdateMsgType msgType = getUpdateMsgType(actionType);
        switch (actionType) {
            case ADDED:
            case UPDATED:
            case ALARM_ACK:
            case ALARM_CLEAR:
                Alarm alarm = alarmService.findAlarmById(tenantId, alarmId);
                if (alarm != null) {
                    return alarmMsgConstructor.constructAlarmUpdatedMsg(msgType, alarm, findOriginatorEntityName(tenantId, alarm), edgeVersion);
                }
                break;
            case DELETED:
                Alarm deletedAlarm = JacksonUtil.convertValue(body, Alarm.class);
                if (deletedAlarm != null) {
                    return alarmMsgConstructor.constructAlarmUpdatedMsg(msgType, deletedAlarm, findOriginatorEntityName(tenantId, deletedAlarm), edgeVersion);
                }
        }
        return null;
    }

    private EntityId getAlarmOriginator(TenantId tenantId, String entityName, EntityType entityType) {
        switch (entityType) {
            case DEVICE:
                return deviceService.findDeviceByTenantIdAndName(tenantId, entityName).getId();
            case ASSET:
                return assetService.findAssetByTenantIdAndName(tenantId, entityName).getId();
            case ENTITY_VIEW:
                return entityViewService.findEntityViewByTenantIdAndName(tenantId, entityName).getId();
            default:
                return null;
        }
    }

    private String findOriginatorEntityName(TenantId tenantId, Alarm alarm) {
        String entityName = null;
        switch (alarm.getOriginator().getEntityType()) {
            case DEVICE:
                Device deviceById = deviceService.findDeviceById(tenantId, new DeviceId(alarm.getOriginator().getId()));
                if (deviceById != null) {
                    entityName = deviceById.getName();
                }
                break;
            case ASSET:
                Asset assetById = assetService.findAssetById(tenantId, new AssetId(alarm.getOriginator().getId()));
                if (assetById != null) {
                    entityName = assetById.getName();
                }
                break;
            case ENTITY_VIEW:
                EntityView entityViewById = entityViewService.findEntityViewById(tenantId, new EntityViewId(alarm.getOriginator().getId()));
                if (entityViewById != null) {
                    entityName = entityViewById.getName();
                }
                break;
        }
        return entityName;
    }
}
