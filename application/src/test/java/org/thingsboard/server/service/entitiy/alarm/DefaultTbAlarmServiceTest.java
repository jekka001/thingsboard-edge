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
package org.thingsboard.server.service.entitiy.alarm;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmApiCallResult;
import org.thingsboard.server.common.data.alarm.AlarmComment;
import org.thingsboard.server.common.data.alarm.AlarmCommentType;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.asset.AssetProfileService;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.service.entitiy.TbLogEntityActionService;
import org.thingsboard.server.service.security.permission.AccessControlService;
import org.thingsboard.server.service.sync.vc.EntitiesVersionControlService;
import org.thingsboard.server.service.telemetry.AlarmSubscriptionService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DefaultTbAlarmService.class)
class DefaultTbAlarmServiceTest {

    @MockBean
    TbLogEntityActionService logEntityActionService;
    @MockBean
    EdgeService edgeService;
    @MockBean
    AlarmService alarmService;
    @MockBean
    TbAlarmCommentService alarmCommentService;
    @MockBean
    AlarmSubscriptionService alarmSubscriptionService;
    @MockBean
    CustomerService customerService;
    @MockBean
    TbClusterService tbClusterService;
    @MockBean
    EntitiesVersionControlService vcService;
    @MockBean
    AccessControlService accessControlService;
    @MockBean
    EntityGroupService entityGroupService;
    @MockBean
    TenantService tenantService;
    @MockBean
    AssetService assetService;
    @MockBean
    DeviceService deviceService;
    @MockBean
    AssetProfileService assetProfileService;
    @MockBean
    DeviceProfileService deviceProfileService;
    @MockBean
    EntityService entityService;

    @Autowired
    DefaultTbAlarmService service;

    TenantId tenantId = TenantId.fromUUID(Uuids.timeBased());
    CustomerId customerId = new CustomerId(Uuids.timeBased());

    @Test
    void testSave() throws ThingsboardException {
        var alarm = new AlarmInfo();
        when(alarmSubscriptionService.createAlarm(any())).thenReturn(AlarmApiCallResult.builder()
                .successful(true)
                .modified(true)
                .alarm(alarm)
                .build());
        service.save(alarm, new User());

        verify(logEntityActionService).logEntityAction(any(), any(), any(), any(), eq(ActionType.ADDED), any());
        verify(alarmSubscriptionService).createAlarm(any());
    }

    @Test
    void testAck() throws ThingsboardException {
        var alarm = new Alarm();
        when(alarmSubscriptionService.acknowledgeAlarm(any(), any(), anyLong()))
                .thenReturn(AlarmApiCallResult.builder().successful(true).modified(true).alarm(new AlarmInfo()).build());
        service.ack(alarm, new User(new UserId(UUID.randomUUID())));

        verify(alarmCommentService).saveAlarmComment(any(), any(), any());
        verify(logEntityActionService).logEntityAction(any(), any(), any(), any(), eq(ActionType.ALARM_ACK), any());
        verify(alarmSubscriptionService).acknowledgeAlarm(any(), any(), anyLong());
    }

    @Test
    void testClear() throws ThingsboardException {
        var alarm = new Alarm();
        alarm.setAcknowledged(true);
        when(alarmSubscriptionService.clearAlarm(any(), any(), anyLong(), any()))
                .thenReturn(AlarmApiCallResult.builder().successful(true).cleared(true).alarm(new AlarmInfo()).build());
        service.clear(alarm, new User(new UserId(UUID.randomUUID())));

        verify(alarmCommentService).saveAlarmComment(any(), any(), any());
        verify(logEntityActionService).logEntityAction(any(), any(), any(), any(), eq(ActionType.ALARM_CLEAR), any());
        verify(alarmSubscriptionService).clearAlarm(any(), any(), anyLong(), any());
    }

    @Test
    void testDelete_deleteApiReturnsTrue_shouldLogActionAndReturnTrue() {
        // GIVEN
        var alarmOriginator = new DeviceId(Uuids.timeBased());

        var alarm = new Alarm(new AlarmId(Uuids.timeBased()));
        alarm.setTenantId(tenantId);
        alarm.setCustomerId(customerId);
        alarm.setOriginator(alarmOriginator);

        var user = new User();

        when(alarmSubscriptionService.deleteAlarm(tenantId, alarm.getId())).thenReturn(true);

        // WHEN
        boolean actual = service.delete(alarm, user);

        assertThat(actual).isTrue();
        verify(logEntityActionService).logEntityAction(tenantId, alarmOriginator, alarm, alarm.getCustomerId(), ActionType.ALARM_DELETE, user, alarm.getId());
        verify(alarmSubscriptionService).deleteAlarm(tenantId, alarm.getId());
    }

    @Test
    void testDelete_deleteApiReturnsFalse_shouldNotLogActionAndReturnFalse() {
        // GIVEN
        var alarm = new Alarm(new AlarmId(Uuids.timeBased()));
        alarm.setTenantId(tenantId);

        var user = new User();

        // WHEN
        boolean actual = service.delete(alarm, user);

        assertThat(actual).isFalse();
        verifyNoInteractions(logEntityActionService);
        verify(alarmSubscriptionService).deleteAlarm(tenantId, alarm.getId());
    }

    @Test
    void testDelete_deleteApiThrowsException_shouldLogFailedActionAndRethrow() {
        // GIVEN
        var alarmOriginator = new DeviceId(Uuids.timeBased());

        var alarm = new Alarm(new AlarmId(Uuids.timeBased()));
        alarm.setTenantId(tenantId);
        alarm.setOriginator(alarmOriginator);

        var user = new User();

        var exception = new RuntimeException("failed to delete alarm");

        when(alarmSubscriptionService.deleteAlarm(tenantId, alarm.getId())).thenThrow(exception);

        // WHEN-THEN
        assertThatThrownBy(() -> service.delete(alarm, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("failed to delete alarm");

        verify(logEntityActionService).logEntityAction(tenantId, new DeviceId(EntityId.NULL_UUID), ActionType.ALARM_DELETE, user, exception, alarm.getId());
        verify(alarmSubscriptionService).deleteAlarm(tenantId, alarm.getId());
    }

    @Test
    void testUnassignAlarm() throws ThingsboardException {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setId(new AlarmId(UUID.randomUUID()));
        when(alarmSubscriptionService.unassignAlarm(any(), any(), anyLong()))
                .thenReturn(AlarmApiCallResult.builder().successful(true).modified(true).alarm(alarm).build());

        User user = new User();
        user.setEmail("testEmail@gmail.com");
        user.setId(new UserId(UUID.randomUUID()));
        service.unassign(new Alarm(), 0L, user);

        ObjectNode commentNode = JacksonUtil.newObjectNode();
        commentNode.put("subtype", "ASSIGN");
        commentNode.put("text", "Alarm was unassigned by user " + user.getTitle());
        commentNode.put("userId", user.getId().getId().toString());
        AlarmComment expectedAlarmComment = AlarmComment.builder()
                .alarmId(alarm.getId())
                .type(AlarmCommentType.SYSTEM)
                .comment(commentNode)
                .build();

        verify(alarmCommentService).saveAlarmComment(eq(alarm), eq(expectedAlarmComment), eq(user));
    }

    @Test
    void testUnassignDeletedUserAlarms() throws ThingsboardException {
        AlarmInfo alarm = new AlarmInfo();
        alarm.setId(new AlarmId(UUID.randomUUID()));

        when(alarmSubscriptionService.unassignAlarm(any(), any(), anyLong()))
                .thenReturn(AlarmApiCallResult.builder().successful(true).modified(true).alarm(alarm).build());

        User user = new User();
        user.setEmail("testEmail@gmail.com");
        user.setId(new UserId(UUID.randomUUID()));
        service.unassignDeletedUserAlarms(tenantId, user.getId(), user.getTitle(), List.of(alarm.getUuidId()), System.currentTimeMillis());

        ObjectNode commentNode = JacksonUtil.newObjectNode();
        commentNode.put("subtype", "ASSIGN");
        commentNode.put("text", String.format("Alarm was unassigned because user %s - was deleted", user.getTitle()));
        AlarmComment expectedAlarmComment = AlarmComment.builder()
                .alarmId(alarm.getId())
                .type(AlarmCommentType.SYSTEM)
                .comment(commentNode)
                .build();

        verify(alarmCommentService).saveAlarmComment(eq(alarm), eq(expectedAlarmComment), eq(null));
    }

}
