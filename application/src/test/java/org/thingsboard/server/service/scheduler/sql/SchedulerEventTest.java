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
package org.thingsboard.server.service.scheduler.sql;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.service.scheduler.DefaultSchedulerService;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.server.common.data.msg.TbMsgType.RPC_CALL_FROM_SERVER_TO_DEVICE;
import static org.thingsboard.server.dao.scheduler.BaseSchedulerEventService.getOriginatorId;

@DaoSqlTest
public class SchedulerEventTest extends AbstractControllerTest {

    @Autowired
    DefaultSchedulerService schedulerService;

    @Autowired
    TbServiceInfoProvider serviceInfoProvider;

    @Before
    public void before() throws Exception {
        loginTenantAdmin();
        Mockito.reset(tbClusterService);
    }

    private static final String testRpc = "{\"method\":\"test\",\"params\":{\"p1\":1,\"p2\":\"2\"}}";
    private static final Boolean PERSISTENT_TEST_VALUE = true;
    private static final Long TIMEOUT_TEST_VALUE = 7000L;

    @Test
    public void sendRpcRequestToDevice() throws Exception {
        Device device = new Device();
        device.setName("My device");
        device.setType("default");
        Device savedDevice = doPost("/api/device", device, Device.class);

        ListeningScheduledExecutorService mockExecutor = Mockito.mock(ListeningScheduledExecutorService.class);

        AtomicBoolean isScheduled = new AtomicBoolean(false);

        when(mockExecutor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            if (!isScheduled.get()) {
                isScheduled.set(true);
                Runnable task = (Runnable) invocation.getArguments()[0];
                task.run();
            }

            return any();
        });

        ReflectionTestUtils.setField(schedulerService, "scheduledExecutor", mockExecutor);

        SchedulerEvent schedulerEvent = createSchedulerEvent(savedDevice.getId(), true);
        SchedulerEvent savedSchedulerEvent = doPost("/api/schedulerEvent", schedulerEvent, SchedulerEvent.class);

        verify(tbClusterService, timeout(10000)).pushMsgToRuleEngine(eq(tenantId), eq(getOriginatorId(savedSchedulerEvent)), argThat(tbMsg -> {
                    if (tbMsg.isTypeOf(RPC_CALL_FROM_SERVER_TO_DEVICE)) {
                        assertEquals(tbMsg.getOriginator(), savedDevice.getId());
                        assertEquals(testRpc, tbMsg.getData());
                        assertEquals(serviceInfoProvider.getServiceId(), tbMsg.getMetaData().getValue("originServiceId"));
                        assertThat(Long.parseLong(tbMsg.getMetaData().getValue("expirationTime"))).isCloseTo(System.currentTimeMillis() + TIMEOUT_TEST_VALUE, byLessThan(10000L));
                        assertThat(tbMsg.getMetaData().getValue("persistent")).isEqualTo(PERSISTENT_TEST_VALUE.toString());
                        assertEquals(serviceInfoProvider.getServiceId(), tbMsg.getMetaData().getValue("originServiceId"));
                        return true;
                    }
                    return false;
                }
        ), any());
    }

    @Test
    public void shouldNotSendRpcRequestToDeviceIfSchedulerEventIsDisabled() {
        Device device = new Device();
        device.setName("My device");
        device.setType("default");
        Device savedDevice = doPost("/api/device", device, Device.class);

        ListeningScheduledExecutorService mockExecutor = Mockito.mock(ListeningScheduledExecutorService.class);

        AtomicBoolean isScheduled = new AtomicBoolean(false);

        when(mockExecutor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(invocation -> {
            if (!isScheduled.get()) {
                isScheduled.set(true);
                Runnable task = (Runnable) invocation.getArguments()[0];
                task.run();
            }

            return any();
        });

        ReflectionTestUtils.setField(schedulerService, "scheduledExecutor", mockExecutor);

        SchedulerEvent schedulerEvent = createSchedulerEvent(savedDevice.getId(), false);
        SchedulerEvent savedSchedulerEvent = doPost("/api/schedulerEvent", schedulerEvent, SchedulerEvent.class);

        verify(tbClusterService, Mockito.never()).pushMsgToRuleEngine(eq(tenantId), eq(getOriginatorId(savedSchedulerEvent)), argThat(tbMsg -> {
                    return tbMsg.isTypeOf(RPC_CALL_FROM_SERVER_TO_DEVICE);
                }
        ), any());
    }

    private SchedulerEvent createSchedulerEvent(EntityId originatorId, boolean enabled) {
        SchedulerEvent schedulerEvent = new SchedulerEvent();
        schedulerEvent.setName("TestRpc");
        schedulerEvent.setType("sendRpcRequest");
        schedulerEvent.setEnabled(enabled);
        ObjectNode schedule = JacksonUtil.newObjectNode();
        schedule.put("startTime", Long.MAX_VALUE);
        schedule.put("timezone", "UTC");
        schedulerEvent.setSchedule(schedule);
        schedulerEvent.setOriginatorId(originatorId);

        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.put("msgType", RPC_CALL_FROM_SERVER_TO_DEVICE.name());
        configuration.set("msgBody", JacksonUtil.toJsonNode(testRpc));
        ObjectNode metadata = JacksonUtil.newObjectNode();
        metadata.put("persistent", PERSISTENT_TEST_VALUE);
        metadata.put("timeout", TIMEOUT_TEST_VALUE);
        configuration.set("metadata", metadata);
        schedulerEvent.setConfiguration(configuration);

        return schedulerEvent;
    }

}
