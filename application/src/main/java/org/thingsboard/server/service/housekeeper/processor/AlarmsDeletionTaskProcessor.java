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
package org.thingsboard.server.service.housekeeper.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.housekeeper.AlarmsDeletionHousekeeperTask;
import org.thingsboard.server.common.data.housekeeper.HousekeeperTaskType;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.dao.alarm.AlarmService;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlarmsDeletionTaskProcessor extends HousekeeperTaskProcessor<AlarmsDeletionHousekeeperTask> {

    private final AlarmService alarmService;

    @Override
    public void process(AlarmsDeletionHousekeeperTask task) throws Exception {
        EntityId entityId = task.getEntityId();
        EntityType entityType = entityId.getEntityType();
        TenantId tenantId = task.getTenantId();

        if (entityType == EntityType.DEVICE || entityType == EntityType.ASSET) {
            if (task.getAlarms() == null) {
                AlarmId lastId = null;
                long lastCreatedTime = 0;
                while (true) {
                    List<TbPair<UUID, Long>> alarms = alarmService.findAlarmIdsByOriginatorId(tenantId, entityId, lastCreatedTime, lastId, 128);
                    if (alarms.isEmpty()) {
                        break;
                    }

                    housekeeperClient.submitTask(new AlarmsDeletionHousekeeperTask(tenantId, entityId, alarms.stream().map(TbPair::getFirst).toList()));

                    TbPair<UUID, Long> last = alarms.get(alarms.size() - 1);
                    lastId = new AlarmId(last.getFirst());
                    lastCreatedTime = last.getSecond();
                    log.debug("[{}][{}][{}] Submitted task for deleting {} alarms", tenantId, entityType, entityId, alarms.size());
                }
            } else {
                for (UUID alarmId : task.getAlarms()) {
                    alarmService.delAlarm(tenantId, new AlarmId(alarmId));
                }
                log.debug("[{}][{}][{}] Deleted {} alarms", tenantId, entityType, entityId, task.getAlarms().size());
            }
        }

        int count = alarmService.deleteEntityAlarmRecords(tenantId, entityId);
        log.debug("[{}][{}][{}] Deleted {} entity alarms", tenantId, entityType, entityId, count);
    }

    @Override
    public HousekeeperTaskType getTaskType() {
        return HousekeeperTaskType.DELETE_ALARMS;
    }

}
