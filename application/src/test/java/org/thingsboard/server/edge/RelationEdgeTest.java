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

import com.fasterxml.jackson.databind.node.TextNode;
import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.RelationRequestMsg;
import org.thingsboard.server.gen.edge.v1.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;

import java.util.UUID;

@DaoSqlTest
public class RelationEdgeTest extends AbstractEdgeTest {

    @Test
    public void testRelations() throws Exception {
        // create relation
        Device device = saveDeviceOnCloudAndVerifyDeliveryToEdge();
        Asset asset = saveAssetOnCloudAndVerifyDeliveryToEdge();
        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(device.getId());
        relation.setTo(asset.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        edgeImitator.expectMessageAmount(1);
        relation = doPost("/api/v2/relation", relation, EntityRelation.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        RelationUpdateMsg relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        EntityRelation entityRelation = JacksonUtil.fromString(relationUpdateMsg.getEntity(), EntityRelation.class, true);
        Assert.assertNotNull(entityRelation);
        Assert.assertEquals(relation, entityRelation);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, relationUpdateMsg.getMsgType());

        // delete relation
        edgeImitator.expectMessageAmount(1);

        String deleteUrl = String.format("/api/v2/relation?fromId=%s&fromType=%s&relationType=%s&relationTypeGroup=%s&toId=%s&toType=%s",
                device.getId().toString(), EntityType.DEVICE.name(), "test",
                RelationTypeGroup.COMMON.name(), asset.getId().toString(), EntityType.ASSET.name()
        );

        var deletedRelation = doDelete(deleteUrl, EntityRelation.class);

        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        entityRelation = JacksonUtil.fromString(relationUpdateMsg.getEntity(), EntityRelation.class, true);
        Assert.assertNotNull(entityRelation);
        Assert.assertEquals(deletedRelation, entityRelation);
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
    }

    @Test
    public void testSendRelationToCloud() throws Exception {
        Device device = saveDeviceOnCloudAndVerifyDeliveryToEdge();
        Asset asset = saveAssetOnCloudAndVerifyDeliveryToEdge();

        EntityRelation entityRelation = buildEntityRelationForUplinkMsg(device.getId(), asset.getId());

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        RelationUpdateMsg.Builder relationUpdateMsgBuilder = RelationUpdateMsg.newBuilder();
        relationUpdateMsgBuilder.setEntity(JacksonUtil.toString(entityRelation));
        testAutoGeneratedCodeByProtobuf(relationUpdateMsgBuilder);
        uplinkMsgBuilder.addRelationUpdateMsg(relationUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());

        String getUrl = String.format("/api/relation?fromId=%s&fromType=%s&relationType=%s&relationTypeGroup=%s&toId=%s&toType=%s",
                asset.getUuidId(), EntityType.ASSET.name(), "test",
                RelationTypeGroup.COMMON.name(), device.getUuidId(), EntityType.DEVICE.name()
        );

        var relation = doGet(getUrl, EntityRelation.class);

        Assert.assertNotNull(relation);
    }

    @Test
    public void testSendRelationRequestToCloud() throws Exception {
        Device device = saveDeviceOnCloudAndVerifyDeliveryToEdge();
        Asset asset = saveAssetOnCloudAndVerifyDeliveryToEdge();

        EntityRelation deviceToAssetRelation = new EntityRelation();
        deviceToAssetRelation.setType("test");
        deviceToAssetRelation.setFrom(device.getId());
        deviceToAssetRelation.setTo(asset.getId());
        deviceToAssetRelation.setTypeGroup(RelationTypeGroup.COMMON);

        edgeImitator.expectMessageAmount(1);
        deviceToAssetRelation = doPost("/api/v2/relation", deviceToAssetRelation, EntityRelation.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        EntityRelation assetToTenantRelation = new EntityRelation();
        assetToTenantRelation.setType("test");
        assetToTenantRelation.setFrom(asset.getId());
        assetToTenantRelation.setTo(tenantId);
        assetToTenantRelation.setTypeGroup(RelationTypeGroup.COMMON);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/relation", assetToTenantRelation);
        Assert.assertTrue(edgeImitator.waitForMessages());

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        RelationRequestMsg.Builder relationRequestMsgBuilder = RelationRequestMsg.newBuilder();
        relationRequestMsgBuilder.setEntityIdMSB(device.getId().getId().getMostSignificantBits());
        relationRequestMsgBuilder.setEntityIdLSB(device.getId().getId().getLeastSignificantBits());
        relationRequestMsgBuilder.setEntityType(device.getId().getEntityType().name());
        testAutoGeneratedCodeByProtobuf(relationRequestMsgBuilder);

        uplinkMsgBuilder.addRelationRequestMsg(relationRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        RelationUpdateMsg relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        EntityRelation entityRelation = JacksonUtil.fromString(relationUpdateMsg.getEntity(), EntityRelation.class, true);
        Assert.assertNotNull(entityRelation);
        Assert.assertEquals(deviceToAssetRelation, entityRelation);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
    }

    @Test
    public void testRelationFromEdgeToDevice() throws Exception {
        // create relation
        Device device = saveDeviceOnCloudAndVerifyDeliveryToEdge();
        EdgeId edgeId = new EdgeId(new UUID(edgeImitator.getConfiguration().getEdgeIdMSB(), edgeImitator.getConfiguration().getEdgeIdLSB()));
        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(edgeId);
        relation.setTo(device.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        edgeImitator.expectMessageAmount(1);
        relation = doPost("/api/v2/relation", relation, EntityRelation.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        RelationUpdateMsg relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        EntityRelation entityRelation = JacksonUtil.fromString(relationUpdateMsg.getEntity(), EntityRelation.class, true);
        Assert.assertNotNull(entityRelation);
        Assert.assertEquals(relation, entityRelation);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, relationUpdateMsg.getMsgType());

        // delete relation
        edgeImitator.expectMessageAmount(1);

        String deleteUrl = String.format("/api/v2/relation?fromId=%s&fromType=%s&relationType=%s&relationTypeGroup=%s&toId=%s&toType=%s",
                edgeId, EntityType.EDGE.name(), "test",
                RelationTypeGroup.COMMON.name(), device.getId().toString(), EntityType.DEVICE.name()
        );

        var deletedRelation = doDelete(deleteUrl, EntityRelation.class);

        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        entityRelation = JacksonUtil.fromString(relationUpdateMsg.getEntity(), EntityRelation.class, true);
        Assert.assertNotNull(entityRelation);
        Assert.assertEquals(deletedRelation, entityRelation);
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
    }

    private EntityRelation buildEntityRelationForUplinkMsg(DeviceId deviceId, AssetId assetId) {
        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        relation.setTo(deviceId);
        relation.setFrom(assetId);
        relation.setAdditionalInfo(TextNode.valueOf("{}"));
        return relation;
    }

}
