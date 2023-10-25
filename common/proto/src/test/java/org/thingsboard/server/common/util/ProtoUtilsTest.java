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
package org.thingsboard.server.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.rpc.RpcError;
import org.thingsboard.server.common.data.rpc.ToDeviceRpcRequestBody;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.msg.FromDeviceRpcResponseActorMsg;
import org.thingsboard.server.common.msg.ToDeviceActorNotificationMsg;
import org.thingsboard.server.common.msg.ToDeviceRpcRequestActorMsg;
import org.thingsboard.server.common.msg.edge.EdgeEventUpdateMsg;
import org.thingsboard.server.common.msg.edge.FromEdgeSyncResponse;
import org.thingsboard.server.common.msg.edge.ToEdgeSyncRequest;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.rpc.FromDeviceRpcResponse;
import org.thingsboard.server.common.msg.rpc.RemoveRpcActorMsg;
import org.thingsboard.server.common.msg.rpc.ToDeviceRpcRequest;
import org.thingsboard.server.common.msg.ruleengine.DeviceAttributesEventNotificationMsg;
import org.thingsboard.server.common.msg.ruleengine.DeviceCredentialsUpdateNotificationMsg;
import org.thingsboard.server.common.msg.ruleengine.DeviceEdgeUpdateMsg;
import org.thingsboard.server.common.msg.ruleengine.DeviceNameOrTypeUpdateMsg;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProtoUtilsTest {

    TenantId tenantId = TenantId.fromUUID(UUID.fromString("35e10f77-16e7-424d-ae46-ee780f87ac4f"));
    EntityId entityId = new RuleChainId(UUID.fromString("c640b635-4f0f-41e6-b10b-25a86003094e"));
    DeviceId deviceId = new DeviceId(UUID.fromString("ceebb9e5-4239-437c-a507-dc5f71f1232d"));
    EdgeId edgeId = new EdgeId(UUID.fromString("364be452-2183-459b-af93-1ddb325feac1"));
    UUID id = UUID.fromString("31a07d85-6ed5-46f8-83c0-6715cb0a8782");

    @Test
    void protoComponentLifecycleSerialization() {
        ComponentLifecycleMsg msg = new ComponentLifecycleMsg(tenantId, entityId, ComponentLifecycleEvent.UPDATED);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
        msg = new ComponentLifecycleMsg(tenantId, entityId, ComponentLifecycleEvent.STARTED);
        assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(msg))).as("deserialized").isEqualTo(msg);
    }

    @Test
    void protoEntityTypeSerialization() {
        for(EntityType entityType : EntityType.values()){
            assertThat(ProtoUtils.fromProto(ProtoUtils.toProto(entityType))).as(entityType.getNormalName()).isEqualTo(entityType);
        }
    }

    @Test
    void toProtoEdgeEventUpdateMsg() {
        EdgeEventUpdateMsg msg = new EdgeEventUpdateMsg(tenantId, edgeId);

        TransportProtos.EdgeEventUpdateMsgProto proto = ProtoUtils.toProto(msg);

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.EdgeEventUpdateMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .build()
        );

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoEdgeEventUpdateMsg() {
        TransportProtos.EdgeEventUpdateMsgProto proto = TransportProtos.EdgeEventUpdateMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .build();

        EdgeEventUpdateMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new EdgeEventUpdateMsg(tenantId, edgeId));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoEdgeSyncRequestMsg() {
        ToEdgeSyncRequest msg = new ToEdgeSyncRequest(id, tenantId, edgeId);

        TransportProtos.ToEdgeSyncRequestMsgProto proto = ProtoUtils.toProto(msg);

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToEdgeSyncRequestMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setRequestIdMSB(id.getMostSignificantBits())
                .setRequestIdLSB(id.getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .build()
        );

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoEdgeSyncRequestMsg() {
        TransportProtos.ToEdgeSyncRequestMsgProto proto = TransportProtos.ToEdgeSyncRequestMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setRequestIdMSB(id.getMostSignificantBits())
                .setRequestIdLSB(id.getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .build();

        ToEdgeSyncRequest msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new ToEdgeSyncRequest(id, tenantId, edgeId));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoEdgeSyncResponseMsg() {
        FromEdgeSyncResponse msg = new FromEdgeSyncResponse(id, tenantId, edgeId, true);

        TransportProtos.FromEdgeSyncResponseMsgProto proto = ProtoUtils.toProto(msg);

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.FromEdgeSyncResponseMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setResponseIdMSB(id.getMostSignificantBits())
                .setResponseIdLSB(id.getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .setSuccess(true)
                .build()
        );

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoEdgeSyncResponseMsg() {
        TransportProtos.FromEdgeSyncResponseMsgProto proto = TransportProtos.FromEdgeSyncResponseMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setResponseIdMSB(id.getMostSignificantBits())
                .setResponseIdLSB(id.getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .setSuccess(true)
                .build();

        FromEdgeSyncResponse msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new FromEdgeSyncResponse(id, tenantId, edgeId, true));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoDeviceEdgeUpdateMsg() {
        DeviceEdgeUpdateMsg msg = new DeviceEdgeUpdateMsg(tenantId, deviceId, edgeId);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.DeviceEdgeUpdateMsgProto deviceProto = TransportProtos.DeviceEdgeUpdateMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceEdgeUpdateMsg(deviceProto).build());

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoDeviceEdgeUpdateMsg() {
        TransportProtos.DeviceEdgeUpdateMsgProto deviceProto = TransportProtos.DeviceEdgeUpdateMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setEdgeIdMSB(edgeId.getId().getMostSignificantBits())
                .setEdgeIdLSB(edgeId.getId().getLeastSignificantBits())
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceEdgeUpdateMsg(deviceProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new DeviceEdgeUpdateMsg(tenantId, deviceId, edgeId));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoDeviceNameOrTypeUpdateMsg() {
        String deviceName = "test", deviceType = "test";
        DeviceNameOrTypeUpdateMsg msg = new DeviceNameOrTypeUpdateMsg(tenantId, deviceId, deviceName, deviceType);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.DeviceNameOrTypeUpdateMsgProto deviceProto = TransportProtos.DeviceNameOrTypeUpdateMsgProto.newBuilder()
                .setTenantIdMSB(msg.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(msg.getTenantId().getId().getLeastSignificantBits())
                .setDeviceIdMSB(msg.getDeviceId().getId().getMostSignificantBits())
                .setDeviceIdLSB(msg.getDeviceId().getId().getLeastSignificantBits())
                .setDeviceName(msg.getDeviceName())
                .setDeviceType(msg.getDeviceType())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceNameOrTypeMsg(deviceProto).build());


        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoDeviceNameOrTypeUpdateMsg() {
        String deviceName = "test", deviceType = "test";
        TransportProtos.DeviceNameOrTypeUpdateMsgProto deviceProto = TransportProtos.DeviceNameOrTypeUpdateMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setDeviceName(deviceName)
                .setDeviceType(deviceType)
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceNameOrTypeMsg(deviceProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new DeviceNameOrTypeUpdateMsg(tenantId, deviceId, deviceName, deviceType));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoDeviceAttributesEventMsg() {
        long ts = System.currentTimeMillis();
        List<AttributeKvEntry> list = List.of(new BaseAttributeKvEntry(ts, new StringDataEntry("key", "value")));
        DeviceAttributesEventNotificationMsg msg = new DeviceAttributesEventNotificationMsg(tenantId, deviceId, null, "CLIENT_SCOPE", list, false);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.DeviceAttributesEventMsgProto deviceProto = TransportProtos.DeviceAttributesEventMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setScope(TransportProtos.AttributeScopeProto.CLIENT_SCOPE)
                .setDeleted(false)
                .addValues(TransportProtos.AttributeValueProto.newBuilder()
                        .setLastUpdateTs(ts)
                        .setHasV(true)
                        .setKey("key")
                        .setStringV("value")
                        .setType(TransportProtos.KeyValueType.STRING_V).build())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceAttributesEventMsg(deviceProto).build());


        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoDeviceAttributesEventMsg() {
        long ts = System.currentTimeMillis();
        TransportProtos.DeviceAttributesEventMsgProto deviceProto = TransportProtos.DeviceAttributesEventMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setScope(TransportProtos.AttributeScopeProto.CLIENT_SCOPE)
                .setDeleted(false)
                .addValues(TransportProtos.AttributeValueProto.newBuilder()
                        .setLastUpdateTs(ts)
                        .setHasV(true)
                        .setKey("key")
                        .setStringV("value")
                        .setType(TransportProtos.KeyValueType.STRING_V).build())
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceAttributesEventMsg(deviceProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new DeviceAttributesEventNotificationMsg(tenantId, deviceId, null, "CLIENT_SCOPE",
                        List.of(new BaseAttributeKvEntry(ts, new StringDataEntry("key", "value"))), false));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoDeviceCredentialsUpdateMsg() {
        DeviceCredentials deviceCredentials = new DeviceCredentials();
        deviceCredentials.setDeviceId(deviceId);
        deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
        deviceCredentials.setCredentialsValue("test");
        deviceCredentials.setCredentialsId("test");
        DeviceCredentialsUpdateNotificationMsg msg = new DeviceCredentialsUpdateNotificationMsg(tenantId, deviceId, deviceCredentials);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.DeviceCredentialsUpdateMsgProto deviceCredentialsProto = TransportProtos.DeviceCredentialsUpdateMsgProto.newBuilder()
                .setTenantIdMSB(msg.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(msg.getTenantId().getId().getLeastSignificantBits())
                .setDeviceIdMSB(msg.getDeviceId().getId().getMostSignificantBits())
                .setDeviceIdLSB(msg.getDeviceId().getId().getLeastSignificantBits())
                .setDeviceCredentials(TransportProtos.DeviceCredentialsProto.newBuilder()
                        .setDeviceIdMSB(msg.getDeviceCredentials().getDeviceId().getId().getMostSignificantBits())
                        .setDeviceIdLSB(msg.getDeviceCredentials().getDeviceId().getId().getLeastSignificantBits())
                        .setCredentialsId(msg.getDeviceCredentials().getCredentialsId())
                        .setCredentialsValue(msg.getDeviceCredentials().getCredentialsValue())
                        .setCredentialsType(TransportProtos.CredentialsType.valueOf(msg.getDeviceCredentials().getCredentialsType().name()))
                        .build())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceCredentialsUpdateMsg(deviceCredentialsProto).build());

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoDeviceCredentialsUpdateMsg() {
        DeviceCredentials deviceCredentials = new DeviceCredentials();
        deviceCredentials.setDeviceId(deviceId);
        deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
        deviceCredentials.setCredentialsValue("test");
        deviceCredentials.setCredentialsId("test");

        TransportProtos.DeviceCredentialsUpdateMsgProto deviceCredentialsProto = TransportProtos.DeviceCredentialsUpdateMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setDeviceCredentials(TransportProtos.DeviceCredentialsProto.newBuilder()
                        .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                        .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                        .setCredentialsId(deviceCredentials.getCredentialsId())
                        .setCredentialsValue(deviceCredentials.getCredentialsValue())
                        .setCredentialsType(TransportProtos.CredentialsType.valueOf(deviceCredentials.getCredentialsType().name()))
                        .build())
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setDeviceCredentialsUpdateMsg(deviceCredentialsProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new DeviceCredentialsUpdateNotificationMsg(tenantId, deviceId, deviceCredentials));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoDeviceRpcRequestActorMsg() {
        String serviceId = "cadcaac6-85c3-4211-9756-f074dcd1e7f7";
        ToDeviceRpcRequest request = new ToDeviceRpcRequest(id, tenantId, deviceId, true, 0, new ToDeviceRpcRequestBody("method", "params"), false, 0, "");
        ToDeviceRpcRequestActorMsg msg = new ToDeviceRpcRequestActorMsg(serviceId, request);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.ToDeviceRpcRequestActorMsgProto deviceProto = TransportProtos.ToDeviceRpcRequestActorMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setServiceId(serviceId)
                .setToDeviceRpcRequestMsg(TransportProtos.ToDeviceRpcRequestMsg.newBuilder()
                        .setRequestId(0)
                        .setMethodName("method")
                        .setParams("params")
                        .setExpirationTime(0)
                        .setRequestIdMSB(id.getMostSignificantBits())
                        .setRequestIdLSB(id.getLeastSignificantBits())
                        .setOneway(true)
                        .build())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setToDeviceRpcRequestMsg(deviceProto).build());

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoDeviceRpcRequestActorMsg() {
        String serviceId = "cadcaac6-85c3-4211-9756-f074dcd1e7f7";
        ToDeviceRpcRequest request = new ToDeviceRpcRequest(id, tenantId, deviceId, true, 0, new ToDeviceRpcRequestBody("method", "params"), false, 0, "");

        TransportProtos.ToDeviceRpcRequestActorMsgProto deviceProto = TransportProtos.ToDeviceRpcRequestActorMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setServiceId(serviceId)
                .setToDeviceRpcRequestMsg(TransportProtos.ToDeviceRpcRequestMsg.newBuilder()
                        .setRequestId(0)
                        .setMethodName("method")
                        .setParams("params")
                        .setExpirationTime(0)
                        .setRequestIdMSB(id.getMostSignificantBits())
                        .setRequestIdLSB(id.getLeastSignificantBits())
                        .setOneway(true)
                        .build())
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setToDeviceRpcRequestMsg(deviceProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new ToDeviceRpcRequestActorMsg(serviceId, request));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoDeviceRpcResponseActorMsg() {
        FromDeviceRpcResponse response = new FromDeviceRpcResponse(id, "response", RpcError.NOT_FOUND);
        FromDeviceRpcResponseActorMsg msg = new FromDeviceRpcResponseActorMsg(23, tenantId, deviceId, response);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.FromDeviceRpcResponseActorMsgProto deviceProto = TransportProtos.FromDeviceRpcResponseActorMsgProto.newBuilder()
                .setRequestId(23)
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setRpcResponse(TransportProtos.FromDeviceRPCResponseProto.newBuilder()
                        .setRequestIdMSB(id.getMostSignificantBits())
                        .setRequestIdLSB(id.getLeastSignificantBits())
                        .setError(RpcError.NOT_FOUND.ordinal())
                        .setResponse("response").build())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setFromDeviceRpcResponseMsg(deviceProto).build());

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoDeviceRpcResponseActorMsg() {
        FromDeviceRpcResponse response = new FromDeviceRpcResponse(id, "response", RpcError.NOT_FOUND);

        TransportProtos.FromDeviceRpcResponseActorMsgProto deviceProto = TransportProtos.FromDeviceRpcResponseActorMsgProto.newBuilder()
                .setRequestId(23)
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setRpcResponse(TransportProtos.FromDeviceRPCResponseProto.newBuilder()
                        .setRequestIdMSB(id.getMostSignificantBits())
                        .setRequestIdLSB(id.getLeastSignificantBits())
                        .setError(RpcError.NOT_FOUND.ordinal())
                        .setResponse("response").build())
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setFromDeviceRpcResponseMsg(deviceProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new FromDeviceRpcResponseActorMsg(23, tenantId, deviceId, response));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

    @Test
    void toProtoRemoveRpcActorMsg() {
        RemoveRpcActorMsg msg = new RemoveRpcActorMsg(tenantId, deviceId, id);

        TransportProtos.ToDeviceActorNotificationMsgProto proto = ProtoUtils.toProto(msg);
        Assertions.assertNotNull(proto);

        TransportProtos.RemoveRpcActorMsgProto rpcProto = TransportProtos.RemoveRpcActorMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setRequestIdMSB(id.getMostSignificantBits())
                .setRequestIdLSB(id.getLeastSignificantBits())
                .build();

        assertThat(proto).as("to proto").isEqualTo(TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setRemoveRpcActorMsg(rpcProto).build());

        assertThat(ProtoUtils.fromProto(proto)).as("from proto").isEqualTo(msg);
    }

    @Test
    void fromProtoRemoveRpcActorMsg() {
        TransportProtos.RemoveRpcActorMsgProto rpcProto = TransportProtos.RemoveRpcActorMsgProto.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setRequestIdMSB(id.getMostSignificantBits())
                .setRequestIdLSB(id.getLeastSignificantBits())
                .build();

        TransportProtos.ToDeviceActorNotificationMsgProto proto = TransportProtos.ToDeviceActorNotificationMsgProto.newBuilder().setRemoveRpcActorMsg(rpcProto).build();

        ToDeviceActorNotificationMsg msg = ProtoUtils.fromProto(proto);

        assertThat(msg).as("from proto").isEqualTo(
                new RemoveRpcActorMsg(tenantId, deviceId, id));

        assertThat(ProtoUtils.toProto(msg)).as("to proto").isEqualTo(proto);
    }

}
