/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.cloud.rpc.processor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;

import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class AlarmCloudProcessor extends BaseCloudProcessor {

    public ListenableFuture<Void> processAlarmMsgFromCloud(TenantId tenantId, AlarmUpdateMsg alarmUpdateMsg) {
        log.trace("[{}] onAlarmUpdate [{}]", tenantId, alarmUpdateMsg);
        EntityId originatorId = getAlarmOriginator(tenantId, alarmUpdateMsg.getOriginatorName(),
                EntityType.valueOf(alarmUpdateMsg.getOriginatorType()));
        if (originatorId == null) {
            log.warn("Originator not found for the alarm msg {}", alarmUpdateMsg);
            return Futures.immediateFuture(null);
        }
        try {
            Alarm existentAlarm = alarmService.findLatestByOriginatorAndType(tenantId, originatorId, alarmUpdateMsg.getType()).get();
            switch (alarmUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    if (existentAlarm == null || existentAlarm.getStatus().isCleared()) {
                        existentAlarm = new Alarm();
                        existentAlarm.setTenantId(tenantId);
                        existentAlarm.setType(alarmUpdateMsg.getName());
                        existentAlarm.setOriginator(originatorId);
                        existentAlarm.setSeverity(AlarmSeverity.valueOf(alarmUpdateMsg.getSeverity()));
                        existentAlarm.setStartTs(alarmUpdateMsg.getStartTs());
                        existentAlarm.setClearTs(alarmUpdateMsg.getClearTs());
                        existentAlarm.setPropagate(alarmUpdateMsg.getPropagate());
                    }
                    existentAlarm.setStatus(AlarmStatus.valueOf(alarmUpdateMsg.getStatus()));
                    existentAlarm.setAckTs(alarmUpdateMsg.getAckTs());
                    existentAlarm.setEndTs(alarmUpdateMsg.getEndTs());
                    existentAlarm.setDetails(mapper.readTree(alarmUpdateMsg.getDetails()));
                    alarmService.createOrUpdateAlarm(existentAlarm);
                    break;
                case ALARM_ACK_RPC_MESSAGE:
                    if (existentAlarm != null) {
                        alarmService.ackAlarm(tenantId, existentAlarm.getId(), alarmUpdateMsg.getAckTs());
                    }
                    break;
                case ALARM_CLEAR_RPC_MESSAGE:
                    if (existentAlarm != null) {
                        alarmService.clearAlarm(tenantId, existentAlarm.getId(), mapper.readTree(alarmUpdateMsg.getDetails()), alarmUpdateMsg.getAckTs());
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    if (existentAlarm != null) {
                        alarmService.deleteAlarm(tenantId, existentAlarm.getId());
                    }
                    break;
            }
            return Futures.immediateFuture(null);
        } catch (Exception e) {
            log.error("Failed to process alarm update msg [{}]", alarmUpdateMsg, e);
            return Futures.immediateFailedFuture(new RuntimeException("Failed to process alarm update msg", e));
        }
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

    public UplinkMsg processAlarmMsgToCloud(TenantId tenantId, CloudEvent cloudEvent, UpdateMsgType msgType, ActionType edgeEventAction)
            throws ExecutionException, InterruptedException {
        AlarmId alarmId = new AlarmId(cloudEvent.getEntityId());
        UplinkMsg msg = null;
        switch (edgeEventAction) {
            case ADDED:
            case UPDATED:
            case ALARM_ACK:
            case ALARM_CLEAR:
                Alarm alarm = alarmService.findAlarmByIdAsync(cloudEvent.getTenantId(), alarmId).get();
                if (alarm != null) {
                    AlarmUpdateMsg alarmUpdateMsg = alarmMsgConstructor.constructAlarmUpdatedMsg(tenantId, msgType, alarm);
                    msg = UplinkMsg.newBuilder()
                            .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                            .addAlarmUpdateMsg(alarmUpdateMsg).build();
                } else {
                    log.info("Skipping event as alarm was not found [{}]", cloudEvent);
                }
                break;
            case DELETED:
                Alarm deletedAlarm = mapper.convertValue(cloudEvent.getEntityBody(), Alarm.class);
                AlarmUpdateMsg alarmUpdateMsg =
                        alarmMsgConstructor.constructAlarmUpdatedMsg(tenantId, msgType, deletedAlarm);
                msg = UplinkMsg.newBuilder()
                        .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                        .addAlarmUpdateMsg(alarmUpdateMsg)
                        .build();
                break;
        }
        return msg;
    }
}
