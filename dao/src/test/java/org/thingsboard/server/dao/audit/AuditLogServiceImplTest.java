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
package org.thingsboard.server.dao.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmComment;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.audit.AuditLog;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.dao.audit.sink.AuditLogSink;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.service.validator.AuditLogDataValidator;
import org.thingsboard.server.dao.sql.JpaExecutorService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
public class AuditLogServiceImplTest {

    private final TenantId TENANT_ID = TenantId.fromUUID(UUID.fromString("9114e9ac-6c28-4019-a2a7-b948cb9500d5"));
    private final CustomerId CUSTOMER_ID = new CustomerId(UUID.fromString("d15822ef-09eb-49a6-9068-21b9c8ae3356"));
    private final UserId USER_ID = new UserId(UUID.fromString("47a2c904-3a47-4530-91bb-51068a4610a7"));
    private final DeviceId DEVICE_ID = new DeviceId(UUID.fromString("b913c12a-9942-4cbd-9481-c42dad4831b0"));

    private final String USER_NAME = "Test User";

    @InjectMocks
    private AuditLogServiceImpl auditLogService;
    @Mock
    private EntityService entityService;
    @Mock
    private AuditLogLevelFilter auditLogLevelFilter;
    @Mock
    private AuditLogDataValidator auditLogDataValidator;
    @Mock
    private JpaExecutorService executor;
    @Mock
    private AuditLogDao auditLogDao;
    @Mock
    private AuditLogSink auditLogSink;

    @Test
    public void givenEntityIsNull_whenLogEntityAction_thenShouldFetchEntityName() throws Exception {
        // GIVEN
        given(auditLogLevelFilter.logEnabled(any(), any())).willReturn(true);
        given(entityService.fetchEntityName(any(), any())).willReturn(Optional.of("Test device"));

        // WHEN
        auditLogService.logEntityAction(TENANT_ID, CUSTOMER_ID, USER_ID, USER_NAME, DEVICE_ID, null, ActionType.ADDED, null);

        // THEN
        then(entityService).should().fetchEntityName(TENANT_ID, DEVICE_ID);
        verifyEntityName("Test device");
    }

    @Test
    public void givenActionTypeIsAlarmActionAndEntityIsAlarm_whenLogEntityAction_thenShouldGetEntityName() throws Exception {
        // GIVEN
        given(auditLogLevelFilter.logEnabled(any(), any())).willReturn(true);
        Alarm alarm = new Alarm(new AlarmId(UUID.fromString("55f577b3-6ef5-4b99-92dc-70eb78b2a970")));
        alarm.setType("Test alarm");
        AlarmComment comment = new AlarmComment();
        comment.setComment(JacksonUtil.toJsonNode("{\"comment\": \"test\"}"));

        // WHEN
        auditLogService.logEntityAction(TENANT_ID, CUSTOMER_ID, USER_ID, USER_NAME, alarm.getId(), alarm, ActionType.ADDED_COMMENT, null, comment);

        // THEN
        verifyEntityName("Test alarm");
    }

    @Test
    public void givenActionTypeIsAlarmActionAndEntityIsAlarmInfo_whenLogEntityAction_thenShouldGetEntityOriginatorName() throws Exception {
        // GIVEN
        given(auditLogLevelFilter.logEnabled(any(), any())).willReturn(true);
        AlarmInfo alarmInfo = new AlarmInfo();
        alarmInfo.setOriginatorName("Test device");

        // WHEN
        auditLogService.logEntityAction(TENANT_ID, CUSTOMER_ID, USER_ID, USER_NAME, DEVICE_ID, alarmInfo, ActionType.ALARM_ASSIGNED, null);

        // THEN
        verifyEntityName("Test device");
    }

    @Test
    public void givenActionTypeIsAlarmActionAndEntityIsAlarm_whenLogEntityAction_thenShouldFetchEntityName() throws Exception {
        // GIVEN
        given(auditLogLevelFilter.logEnabled(any(), any())).willReturn(true);
        given(entityService.fetchEntityName(any(), any())).willReturn(Optional.of("Test alarm"));

        // WHEN
        auditLogService.logEntityAction(TENANT_ID, CUSTOMER_ID, USER_ID, USER_NAME, DEVICE_ID, new Alarm(), ActionType.ALARM_DELETE, null);

        // THEN
        then(entityService).should().fetchEntityName(TENANT_ID, DEVICE_ID);
        verifyEntityName("Test alarm");
    }

    private void verifyEntityName(String entityName) throws Exception {
        then(auditLogDataValidator).should().validate(any(AuditLog.class), any());
        ArgumentCaptor<Callable> submitTask = ArgumentCaptor.forClass(Callable.class);
        then(executor).should().submit(submitTask.capture());
        submitTask.getValue().call();
        ArgumentCaptor<AuditLog> auditLogEntry = ArgumentCaptor.forClass(AuditLog.class);
        then(auditLogDao).should().save(eq(TENANT_ID), auditLogEntry.capture());
        assertThat(auditLogEntry.getValue().getEntityName()).isEqualTo(entityName);
        then(auditLogSink).should().logAction(any());
    }

}
