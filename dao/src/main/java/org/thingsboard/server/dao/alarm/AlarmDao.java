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
package org.thingsboard.server.dao.alarm;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmFilter;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQuery;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.query.AlarmData;
import org.thingsboard.server.common.data.query.AlarmDataQuery;
import org.thingsboard.server.dao.Dao;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Created by ashvayka on 11.05.17.
 */
public interface AlarmDao extends Dao<Alarm> {

    Boolean deleteAlarm(TenantId tenantId, Alarm alarm);

    ListenableFuture<Alarm> findLatestByOriginatorAndType(TenantId tenantId, EntityId originator, String type);

    ListenableFuture<Alarm> findAlarmByIdAsync(TenantId tenantId, UUID key);

    Alarm save(TenantId tenantId, Alarm alarm);

    PageData<AlarmInfo> findAlarms(TenantId tenantId, AlarmQuery query);

    long findAlarmCount(TenantId tenantId, AlarmQuery query, AlarmFilter filter);

    PageData<AlarmData> findAlarmDataByQueryForEntities(TenantId tenantId, CustomerId customerId, MergedUserPermissions mergedUserPermissions,
                                                        AlarmDataQuery query, Collection<EntityId> orderedEntityIds);

    Set<AlarmSeverity> findAlarmSeverities(TenantId tenantId, EntityId entityId, Set<AlarmStatus> status);
}
