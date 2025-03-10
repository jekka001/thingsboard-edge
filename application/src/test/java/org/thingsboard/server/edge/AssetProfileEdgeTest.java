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
package org.thingsboard.server.edge;

import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.gen.edge.v1.UplinkResponseMsg;

import java.util.Optional;
import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class AssetProfileEdgeTest extends AbstractEdgeTest {

    @Test
    public void testAssetProfiles() throws Exception {
        RuleChainId buildingsRuleChainId = createEdgeRuleChainAndAssignToEdge("Buildings Rule Chain");

        // create asset profile
        AssetProfile assetProfile = this.createAssetProfile("Building");
        assetProfile.setDefaultEdgeRuleChainId(buildingsRuleChainId);
        edgeImitator.expectMessageAmount(1);
        assetProfile = doPost("/api/assetProfile", assetProfile, AssetProfile.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetProfileUpdateMsg);
        AssetProfileUpdateMsg assetProfileUpdateMsg = (AssetProfileUpdateMsg) latestMessage;
        AssetProfile assetProfileMsg = JacksonUtil.fromString(assetProfileUpdateMsg.getEntity(), AssetProfile.class, true);
        Assert.assertNotNull(assetProfileMsg);
        Assert.assertEquals(assetProfile, assetProfileMsg);
        Assert.assertEquals("Building", assetProfileMsg.getName());
        Assert.assertEquals(buildingsRuleChainId, assetProfileMsg.getDefaultEdgeRuleChainId());
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, assetProfileUpdateMsg.getMsgType());

        // update asset profile
        assetProfile.setImage("IMAGE");
        edgeImitator.expectMessageAmount(1);
        assetProfile = doPost("/api/assetProfile", assetProfile, AssetProfile.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetProfileUpdateMsg);
        assetProfileUpdateMsg = (AssetProfileUpdateMsg) latestMessage;
        assetProfileMsg = JacksonUtil.fromString(assetProfileUpdateMsg.getEntity(), AssetProfile.class, true);
        Assert.assertNotNull(assetProfileMsg);
        Assert.assertEquals("IMAGE", assetProfileMsg.getImage());
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, assetProfileUpdateMsg.getMsgType());

        // delete profile
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/assetProfile/" + assetProfile.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetProfileUpdateMsg);
        assetProfileUpdateMsg = (AssetProfileUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, assetProfileUpdateMsg.getMsgType());
        Assert.assertEquals(assetProfile.getUuidId().getMostSignificantBits(), assetProfileUpdateMsg.getIdMSB());
        Assert.assertEquals(assetProfile.getUuidId().getLeastSignificantBits(), assetProfileUpdateMsg.getIdLSB());

        unAssignFromEdgeAndDeleteRuleChain(buildingsRuleChainId);
    }

    @Test
    public void testSendAssetProfileToCloud() throws Exception {
        RuleChainId edgeRuleChainId = createEdgeRuleChainAndAssignToEdge("Asset Profile Rule Chain");

        EntityGroup dashboardEntityGroup = createEntityGroupAndAssignToEdge(EntityType.DASHBOARD, "DashboardGroup", tenantId);
        edgeImitator.expectMessageAmount(1);
        DashboardId dashboardId = saveDashboard("Edge Dashboard", dashboardEntityGroup.getId()).getId();
        Assert.assertTrue(edgeImitator.waitForMessages());

        AssetProfile assetProfileOnEdge = buildAssetProfileForUplinkMsg("Asset Profile On Edge");
        assetProfileOnEdge.setDefaultRuleChainId(edgeRuleChainId);
        assetProfileOnEdge.setDefaultDashboardId(dashboardId);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        AssetProfileUpdateMsg.Builder assetProfileUpdateMsgBuilder = AssetProfileUpdateMsg.newBuilder();
        assetProfileUpdateMsgBuilder.setIdMSB(assetProfileOnEdge.getUuidId().getMostSignificantBits());
        assetProfileUpdateMsgBuilder.setIdLSB(assetProfileOnEdge.getUuidId().getLeastSignificantBits());
        assetProfileUpdateMsgBuilder.setEntity(JacksonUtil.toString(assetProfileOnEdge));
        assetProfileUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        testAutoGeneratedCodeByProtobuf(assetProfileUpdateMsgBuilder);
        uplinkMsgBuilder.addAssetProfileUpdateMsg(assetProfileUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());

        UplinkResponseMsg latestResponseMsg = edgeImitator.getLatestResponseMsg();
        Assert.assertTrue(latestResponseMsg.getSuccess());

        AssetProfile assetProfile = doGet("/api/assetProfile/" + assetProfileOnEdge.getUuidId(), AssetProfile.class);
        Assert.assertNotNull(assetProfile);
        Assert.assertEquals("Asset Profile On Edge", assetProfile.getName());
        Assert.assertEquals(dashboardId, assetProfile.getDefaultDashboardId());
        Assert.assertNull(assetProfile.getDefaultRuleChainId());
        Assert.assertEquals(edgeRuleChainId, assetProfile.getDefaultEdgeRuleChainId());

        // delete profile
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/assetProfile/" + assetProfile.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetProfileUpdateMsg);
        AssetProfileUpdateMsg assetProfileUpdateMsg = (AssetProfileUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, assetProfileUpdateMsg.getMsgType());
        Assert.assertEquals(assetProfile.getUuidId().getMostSignificantBits(), assetProfileUpdateMsg.getIdMSB());
        Assert.assertEquals(assetProfile.getUuidId().getLeastSignificantBits(), assetProfileUpdateMsg.getIdLSB());

        // cleanup
        unAssignEntityGroupFromEdge(dashboardEntityGroup);
        unAssignFromEdgeAndDeleteRuleChain(edgeRuleChainId);
    }

    @Test
    public void testSendAssetProfileToCloudWithNameThatAlreadyExistsOnCloud() throws Exception {
        String assetProfileOnCloudName = StringUtils.randomAlphanumeric(15);

        edgeImitator.expectMessageAmount(1);
        AssetProfile assetProfileOnCloud = this.createAssetProfile(assetProfileOnCloudName);
        assetProfileOnCloud = doPost("/api/assetProfile", assetProfileOnCloud, AssetProfile.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        AssetProfile assetProfileOnEdge = buildAssetProfileForUplinkMsg(assetProfileOnCloudName);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        AssetProfileUpdateMsg.Builder assetProfileUpdateMsgBuilder = AssetProfileUpdateMsg.newBuilder();
        assetProfileUpdateMsgBuilder.setEntity(JacksonUtil.toString(assetProfileOnEdge));
        assetProfileUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        uplinkMsgBuilder.addAssetProfileUpdateMsg(assetProfileUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);

        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        Optional<AssetProfileUpdateMsg> assetProfileUpdateMsgOpt = edgeImitator.findMessageByType(AssetProfileUpdateMsg.class);
        Assert.assertTrue(assetProfileUpdateMsgOpt.isPresent());
        AssetProfileUpdateMsg latestAssetProfileUpdateMsg = assetProfileUpdateMsgOpt.get();
        AssetProfile assetProfileMsg = JacksonUtil.fromString(latestAssetProfileUpdateMsg.getEntity(), AssetProfile.class, true);
        Assert.assertNotNull(assetProfileMsg);
        Assert.assertNotEquals(assetProfileOnCloudName, assetProfileMsg.getName());

        Assert.assertNotEquals(assetProfileOnCloud.getUuidId(), assetProfileOnEdge.getUuidId());

        AssetProfile assetProfile = doGet("/api/assetProfile/" + assetProfileMsg.getUuidId(), AssetProfile.class);
        Assert.assertNotNull(assetProfile);
        Assert.assertNotEquals(assetProfileOnCloudName, assetProfile.getName());
    }

    private AssetProfile buildAssetProfileForUplinkMsg(String name) {
        AssetProfile assetProfile = new AssetProfile();
        assetProfile.setId(new AssetProfileId(UUID.randomUUID()));
        assetProfile.setTenantId(tenantId);
        assetProfile.setName(name);
        assetProfile.setDefault(false);
        return assetProfile;
    }
}
