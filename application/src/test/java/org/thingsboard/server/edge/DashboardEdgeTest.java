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
package org.thingsboard.server.edge;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityGroupRequestMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class DashboardEdgeTest extends AbstractEdgeTest {

    private static final int MOBILE_ORDER = 5;
    private static final String IMAGE = "data:image/png;base64,iVBORw0KGgoA";

    @Test
    public void testDashboards() throws Exception {
        // create dashboard entity group and assign to edge
        EntityGroup dashboardEntityGroup1 = createEntityGroupAndAssignToEdge(EntityType.DASHBOARD, "DashboardGroup1", tenantId);

        // create dashboard and add to entity group 1
        edgeImitator.expectMessageAmount(1);
        Dashboard savedDashboard = saveDashboard("Edge Dashboard 1", dashboardEntityGroup1.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        DashboardUpdateMsg dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(savedDashboard.getTitle(), dashboardUpdateMsg.getTitle());
        Assert.assertEquals(savedDashboard.getUuidId().getMostSignificantBits(), dashboardUpdateMsg.getIdMSB());
        Assert.assertEquals(savedDashboard.getUuidId().getLeastSignificantBits(), dashboardUpdateMsg.getIdLSB());
        Assert.assertEquals(dashboardEntityGroup1.getUuidId().getMostSignificantBits(), dashboardUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(dashboardEntityGroup1.getUuidId().getLeastSignificantBits(), dashboardUpdateMsg.getEntityGroupIdLSB());

        // request dashboards by entity group id
        testDashboardEntityGroupRequestMsg(dashboardEntityGroup1.getUuidId().getMostSignificantBits(),
                dashboardEntityGroup1.getUuidId().getLeastSignificantBits(),
                savedDashboard.getId());

        edgeImitator.expectMessageAmount(1);
        savedDashboard.setMobileHide(true);
        savedDashboard.setImage(IMAGE);
        savedDashboard.setMobileOrder(MOBILE_ORDER);
        savedDashboard = doPost("/api/dashboard", savedDashboard, Dashboard.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        // add dashboard to entity group 2
        EntityGroup dashboardEntityGroup2 = createEntityGroupAndAssignToEdge(EntityType.DASHBOARD, "DashboardGroup2", tenantId);
        edgeImitator.expectMessageAmount(1);
        addEntitiesToEntityGroup(Collections.singletonList(savedDashboard.getId()), dashboardEntityGroup2.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardEntityGroup2.getUuidId().getMostSignificantBits(), dashboardUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(dashboardEntityGroup2.getUuidId().getLeastSignificantBits(), dashboardUpdateMsg.getEntityGroupIdLSB());
        Assert.assertEquals(savedDashboard.getTitle(), dashboardUpdateMsg.getTitle());
        Assert.assertTrue(dashboardUpdateMsg.getMobileHide());
        Assert.assertEquals(IMAGE, dashboardUpdateMsg.getImage());
        Assert.assertEquals(MOBILE_ORDER, dashboardUpdateMsg.getMobileOrder());

        // update dashboard
        edgeImitator.expectMessageAmount(1);
        savedDashboard.setTitle("Edge Dashboard 1 Updated");
        savedDashboard = doPost("/api/dashboard", savedDashboard, Dashboard.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals("Edge Dashboard 1 Updated", dashboardUpdateMsg.getTitle());

        // remove dashboard from entity group 2
        edgeImitator.expectMessageAmount(1);
        deleteEntitiesFromEntityGroup(Collections.singletonList(savedDashboard.getId()), dashboardEntityGroup2.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardEntityGroup2.getUuidId().getMostSignificantBits(), dashboardUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(dashboardEntityGroup2.getUuidId().getLeastSignificantBits(), dashboardUpdateMsg.getEntityGroupIdLSB());

        unAssignEntityGroupFromEdge(dashboardEntityGroup2);

        // delete dashboard
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/dashboard/" + savedDashboard.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(savedDashboard.getUuidId().getMostSignificantBits(), dashboardUpdateMsg.getIdMSB());
        Assert.assertEquals(savedDashboard.getUuidId().getLeastSignificantBits(), dashboardUpdateMsg.getIdLSB());
    }

    private void testDashboardEntityGroupRequestMsg(long msbId, long lsbId, DashboardId expectedDashboardId) throws Exception {
        EntityGroupRequestMsg.Builder entitiesGroupRequestMsgBuilder = EntityGroupRequestMsg.newBuilder()
                .setEntityGroupIdMSB(msbId)
                .setEntityGroupIdLSB(lsbId)
                .setType(EntityType.DASHBOARD.name());
        testAutoGeneratedCodeByProtobuf(entitiesGroupRequestMsgBuilder);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addEntityGroupEntitiesRequestMsg(entitiesGroupRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        DashboardUpdateMsg dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        DashboardId receivedDashboardId =
                new DashboardId(new UUID(dashboardUpdateMsg.getIdMSB(), dashboardUpdateMsg.getIdLSB()));
        Assert.assertEquals(expectedDashboardId, receivedDashboardId);

        testAutoGeneratedCodeByProtobuf(dashboardUpdateMsg);
    }

    @Test
    public void testSendDashboardToCloud() throws Exception {
        UUID uuid = Uuids.timeBased();

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        DashboardUpdateMsg.Builder dashboardUpdateMsgBuilder = DashboardUpdateMsg.newBuilder();
        dashboardUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        dashboardUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        dashboardUpdateMsgBuilder.setTitle("Edge Test Dashboard");
        dashboardUpdateMsgBuilder.setConfiguration("");
        dashboardUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        testAutoGeneratedCodeByProtobuf(dashboardUpdateMsgBuilder);
        uplinkMsgBuilder.addDashboardUpdateMsg(dashboardUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());

        Dashboard dashboard = doGet("/api/dashboard/" + uuid, Dashboard.class);
        Assert.assertNotNull(dashboard);
        Assert.assertEquals("Edge Test Dashboard", dashboard.getName());
    }

    @Test
    public void testSendDeleteEntityViewOnEdgeToCloud() throws Exception {
        // create dashboard entity group and assign to edge
        EntityGroup dashboardEntityGroup = createEntityGroupAndAssignToEdge(EntityType.DASHBOARD, "DashboardToDeleteGroup", tenantId);

        Dashboard savedDashboard = saveDashboardOnCloudAndVerifyDeliveryToEdge(dashboardEntityGroup.getId());

        UplinkMsg.Builder upLinkMsgBuilder = UplinkMsg.newBuilder();
        DashboardUpdateMsg.Builder dashboardDeleteMsgBuilder = DashboardUpdateMsg.newBuilder();
        dashboardDeleteMsgBuilder.setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE);
        dashboardDeleteMsgBuilder.setIdMSB(savedDashboard.getUuidId().getMostSignificantBits());
        dashboardDeleteMsgBuilder.setIdLSB(savedDashboard.getUuidId().getLeastSignificantBits());
        dashboardDeleteMsgBuilder.setEntityGroupIdMSB(dashboardEntityGroup.getUuidId().getMostSignificantBits());
        dashboardDeleteMsgBuilder.setEntityGroupIdLSB(dashboardEntityGroup.getUuidId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(dashboardDeleteMsgBuilder);

        upLinkMsgBuilder.addDashboardUpdateMsg(dashboardDeleteMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(upLinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(upLinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        DashboardInfo dashboardInfo = doGet("/api/dashboard/info/" + savedDashboard.getUuidId(), DashboardInfo.class);
        Assert.assertNotNull(dashboardInfo);
        List<DashboardInfo> edgeDashboards = doGetTypedWithPageLink("/api/entityGroup/" + dashboardEntityGroup.getUuidId() + "/dashboards?",
                new TypeReference<PageData<DashboardInfo>>() {
                }, new PageLink(100)).getData();
        Assert.assertFalse(edgeDashboards.contains(dashboardInfo));
    }

    private Dashboard saveDashboardOnCloudAndVerifyDeliveryToEdge(EntityGroupId entityGroupId) throws Exception {
        // create dashboard and assign to edge
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("Dashboard to delete");
        if (entityGroupId != null) {
            dashboard = doPost("/api/dashboard?entityGroupId={entityGroupId}", dashboard, Dashboard.class, entityGroupId.getId().toString());
        } else {
            dashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        }
        edgeImitator.expectMessageAmount(1); // dashboard message
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<DashboardUpdateMsg> dashboardUpdateMsgOpt = edgeImitator.findMessageByType(DashboardUpdateMsg.class);
        Assert.assertTrue(dashboardUpdateMsgOpt.isPresent());
        DashboardUpdateMsg entityViewUpdateMsg = dashboardUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(dashboard.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getIdMSB());
        Assert.assertEquals(dashboard.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getIdLSB());
        return dashboard;
    }

}
