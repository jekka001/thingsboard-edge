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
package org.thingsboard.server.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.scheduler.MonthlyRepeat;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.scheduler.SchedulerEventInfo;
import org.thingsboard.server.common.data.scheduler.SchedulerRepeat;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.SchedulerEventUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class SchedulerEventEdgeTest extends AbstractEdgeTest {

    @Test
    @Ignore
    public void testSchedulerEvent_tenantLevel() throws Exception {
        // create scheduler event
        ObjectNode schedule = JacksonUtil.newObjectNode();
        schedule.put("startTime", System.currentTimeMillis());
        schedule.put("timezone", "UTC");
        SchedulerRepeat schedulerRepeat = new MonthlyRepeat();
        schedule.set("repeat", JacksonUtil.valueToTree(schedulerRepeat));

        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.put("msgType", TbMsgType.POST_ATTRIBUTES_REQUEST.name());

        SchedulerEvent schedulerEvent = createSchedulerEvent("Edge Scheduler Event", tenantId, schedule, configuration);
        SchedulerEvent savedSchedulerEvent = doPost("/api/schedulerEvent", schedulerEvent, SchedulerEvent.class);
        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getUuidId()
                + "/schedulerEvent/" + savedSchedulerEvent.getUuidId(), SchedulerEventInfo.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof SchedulerEventUpdateMsg);
        SchedulerEventUpdateMsg schedulerEventUpdateMsg = (SchedulerEventUpdateMsg) latestMessage;
        SchedulerEvent event = JacksonUtil.fromString(schedulerEventUpdateMsg.getEntity(), SchedulerEvent.class, true);
        Assert.assertNotNull(event);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, schedulerEventUpdateMsg.getMsgType());
        Assert.assertEquals(savedSchedulerEvent.getUuidId().getMostSignificantBits(), schedulerEventUpdateMsg.getIdMSB());
        Assert.assertEquals(savedSchedulerEvent.getUuidId().getLeastSignificantBits(), schedulerEventUpdateMsg.getIdLSB());
        Assert.assertEquals("Edge Scheduler Event", event.getName());
        Assert.assertEquals("irrigation", event.getType());
        Assert.assertEquals(schedule.toString(), event.getSchedule().toString());
        Assert.assertEquals(configuration, event.getConfiguration());

        Assert.assertEquals(tenantId, event.getOriginatorId());

        // update scheduler event
        edgeImitator.expectMessageAmount(1);
        savedSchedulerEvent.setName("Edge Scheduler Event Updated");
        savedSchedulerEvent = doPost("/api/schedulerEvent", savedSchedulerEvent, SchedulerEvent.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof SchedulerEventUpdateMsg);
        schedulerEventUpdateMsg = (SchedulerEventUpdateMsg) latestMessage;
        event = JacksonUtil.fromString(schedulerEventUpdateMsg.getEntity(), SchedulerEvent.class, true);
        Assert.assertNotNull(event);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, schedulerEventUpdateMsg.getMsgType());
        Assert.assertEquals("Edge Scheduler Event Updated", event.getName());

        // unassign scheduler event
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getUuidId()
                + "/schedulerEvent/" + savedSchedulerEvent.getUuidId(), SchedulerEventInfo.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof SchedulerEventUpdateMsg);
        schedulerEventUpdateMsg = (SchedulerEventUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, schedulerEventUpdateMsg.getMsgType());
        Assert.assertEquals(savedSchedulerEvent.getUuidId().getMostSignificantBits(), schedulerEventUpdateMsg.getIdMSB());
        Assert.assertEquals(savedSchedulerEvent.getUuidId().getLeastSignificantBits(), schedulerEventUpdateMsg.getIdLSB());

        // delete scheduler event
        doDelete("/api/schedulerEvent/" + savedSchedulerEvent.getUuidId())
                .andExpect(status().isOk());
    }

    @Test
    @Ignore
    public void testSchedulerEvent_customerLevel() throws Exception {
        // create customer
        Customer savedCustomer = saveCustomer("Edge Customer", null);
        // create sub customer
        saveCustomer("Edge Sub Customer", savedCustomer.getId());

        // must sleep to make sure that role creation events are processed by edge consumer before edge owner changed
        TimeUnit.SECONDS.sleep(1);

        // change edge owner from tenant to customer
        changeEdgeOwnerToCustomer(savedCustomer);

        // create scheduler event
        ObjectNode schedule = JacksonUtil.newObjectNode();
        schedule.put("startTime", System.currentTimeMillis());
        schedule.put("timezone", "UTC");

        SchedulerEvent schedulerEvent =
                createSchedulerEvent("Edge Customer Scheduler Event", savedCustomer.getId(), schedule, JacksonUtil.newObjectNode());
        SchedulerEvent savedSchedulerEvent = doPost("/api/schedulerEvent", schedulerEvent, SchedulerEvent.class);
        edgeImitator.expectMessageAmount(1);
        doPost("/api/edge/" + edge.getUuidId()
                + "/schedulerEvent/" + savedSchedulerEvent.getUuidId(), SchedulerEventInfo.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof SchedulerEventUpdateMsg);
        SchedulerEventUpdateMsg schedulerEventUpdateMsg = (SchedulerEventUpdateMsg) latestMessage;
        SchedulerEvent event = JacksonUtil.fromString(schedulerEventUpdateMsg.getEntity(), SchedulerEvent.class, true);
        Assert.assertNotNull(event);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, schedulerEventUpdateMsg.getMsgType());
        Assert.assertEquals(savedSchedulerEvent.getId(), event.getId());
        Assert.assertEquals(savedCustomer.getId(), event.getCustomerId());

        // update scheduler event
        edgeImitator.expectMessageAmount(1);
        savedSchedulerEvent.setName("Edge Customer Scheduler Event Updated");
        savedSchedulerEvent = doPost("/api/schedulerEvent", savedSchedulerEvent, SchedulerEvent.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof SchedulerEventUpdateMsg);
        schedulerEventUpdateMsg = (SchedulerEventUpdateMsg) latestMessage;
        event = JacksonUtil.fromString(schedulerEventUpdateMsg.getEntity(), SchedulerEvent.class, true);
        Assert.assertNotNull(event);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, schedulerEventUpdateMsg.getMsgType());
        Assert.assertEquals("Edge Customer Scheduler Event Updated", event.getName());

        // unassign scheduler event
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getUuidId()
                + "/schedulerEvent/" + savedSchedulerEvent.getUuidId(), SchedulerEventInfo.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof SchedulerEventUpdateMsg);
        schedulerEventUpdateMsg = (SchedulerEventUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, schedulerEventUpdateMsg.getMsgType());
        Assert.assertEquals(savedSchedulerEvent.getUuidId().getMostSignificantBits(), schedulerEventUpdateMsg.getIdMSB());
        Assert.assertEquals(savedSchedulerEvent.getUuidId().getLeastSignificantBits(), schedulerEventUpdateMsg.getIdLSB());

        // delete scheduler event
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/schedulerEvent/" + savedSchedulerEvent.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());

        // change owner to tenant
        changeEdgeOwnerFromCustomerToTenant(savedCustomer, 2);

        // delete customers
        doDelete("/api/customer/" + savedCustomer.getUuidId())
                .andExpect(status().isOk());
    }

    private SchedulerEvent createSchedulerEvent(String name, EntityId ownerId,
                                                JsonNode schedule, JsonNode configuration) {
        SchedulerEvent schedulerEvent = new SchedulerEvent();
        schedulerEvent.setName(name);
        schedulerEvent.setType("irrigation");
        schedulerEvent.setSchedule(schedule);
        schedulerEvent.setConfiguration(configuration);
        schedulerEvent.setOriginatorId(tenantId);
        schedulerEvent.setOwnerId(ownerId);
        return schedulerEvent;
    }

}
