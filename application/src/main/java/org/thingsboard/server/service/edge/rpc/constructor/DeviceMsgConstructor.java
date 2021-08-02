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
package org.thingsboard.server.service.edge.rpc.constructor;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RpcRequestMsg;
import org.thingsboard.server.gen.edge.v1.RpcResponseMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.UUID;

import static org.thingsboard.server.service.edge.rpc.EdgeProtoUtils.getInt64Value;
import static org.thingsboard.server.service.edge.rpc.EdgeProtoUtils.getStringValue;

@Component
@TbCoreComponent
public class DeviceMsgConstructor {

    public DeviceUpdateMsg constructDeviceUpdatedMsg(UpdateMsgType msgType, Device device) {
        return constructDeviceUpdatedMsg(msgType, device, null, null);
    }

    public DeviceUpdateMsg constructDeviceUpdatedMsg(UpdateMsgType msgType, Device device, CustomerId customerId,
                                                     String conflictName) {
        return constructDeviceUpdatedMsg(msgType, device, customerId, conflictName, null);
    }

    public DeviceUpdateMsg constructDeviceUpdatedMsg(UpdateMsgType msgType, Device device, CustomerId customerId,
                                                     String conflictName, EntityGroupId entityGroupId) {
        DeviceUpdateMsg.Builder builder = DeviceUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setIdMSB(device.getId().getId().getMostSignificantBits())
                .setIdLSB(device.getId().getId().getLeastSignificantBits())
                .setName(device.getName())
                .setType(device.getType());
        if (device.getLabel() != null) {
            builder.setLabel(getStringValue(device.getLabel()));
        }
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(getInt64Value(entityGroupId.getId().getMostSignificantBits()))
                    .setEntityGroupIdLSB(getInt64Value(entityGroupId.getId().getLeastSignificantBits()));
        }
        if (device.getCustomerId() != null && !device.getCustomerId().isNullUid()) {
            builder.setCustomerIdMSB(getInt64Value(device.getCustomerId().getId().getMostSignificantBits()))
                    .setCustomerIdLSB(getInt64Value(device.getCustomerId().getId().getLeastSignificantBits()));
        }
        if (device.getDeviceProfileId() != null) {
            builder.setDeviceProfileIdMSB(getInt64Value(device.getDeviceProfileId().getId().getMostSignificantBits()));
            builder.setDeviceProfileIdLSB(getInt64Value(device.getDeviceProfileId().getId().getLeastSignificantBits()));
        }
        if (device.getAdditionalInfo() != null) {
            builder.setAdditionalInfo(getStringValue(JacksonUtil.toString(device.getAdditionalInfo())));
        }
        if (conflictName != null) {
            builder.setConflictName(getStringValue(conflictName));
        }
        return builder.build();
    }

    public DeviceCredentialsUpdateMsg constructDeviceCredentialsUpdatedMsg(DeviceCredentials deviceCredentials) {
        DeviceCredentialsUpdateMsg.Builder builder = DeviceCredentialsUpdateMsg.newBuilder()
                .setDeviceIdMSB(deviceCredentials.getDeviceId().getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceCredentials.getDeviceId().getId().getLeastSignificantBits());
        if (deviceCredentials.getCredentialsType() != null) {
            builder.setCredentialsType(deviceCredentials.getCredentialsType().name())
                    .setCredentialsId(deviceCredentials.getCredentialsId());
        }
        if (deviceCredentials.getCredentialsValue() != null) {
            builder.setCredentialsValue(deviceCredentials.getCredentialsValue());
        }
        return builder.build();
    }

    public DeviceUpdateMsg constructDeviceDeleteMsg(DeviceId deviceId, EntityGroupId entityGroupId) {
        DeviceUpdateMsg.Builder builder = DeviceUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(deviceId.getId().getMostSignificantBits())
                .setIdLSB(deviceId.getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(getInt64Value(entityGroupId.getId().getMostSignificantBits()))
                    .setEntityGroupIdLSB(getInt64Value(entityGroupId.getId().getLeastSignificantBits()));
        }
        return builder.build();
    }

    public DeviceRpcCallMsg constructDeviceRpcCallMsg(UUID deviceId, JsonNode body) {
        int requestId = body.get("requestId").asInt();
        boolean oneway = body.get("oneway").asBoolean();
        UUID requestUUID = UUID.fromString(body.get("requestUUID").asText());
        long expirationTime = body.get("expirationTime").asLong();
        String method = body.get("method").asText();
        String params = body.get("params").asText();

        RpcRequestMsg.Builder requestBuilder = RpcRequestMsg.newBuilder();
        requestBuilder.setMethod(method);
        requestBuilder.setParams(params);
        DeviceRpcCallMsg.Builder builder = DeviceRpcCallMsg.newBuilder()
                .setDeviceIdMSB(deviceId.getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getLeastSignificantBits())
                .setRequestUuidMSB(requestUUID.getMostSignificantBits())
                .setRequestUuidLSB(requestUUID.getLeastSignificantBits())
                .setRequestId(requestId)
                .setExpirationTime(expirationTime)
                .setOneway(oneway)
                .setRequestMsg(requestBuilder.build());
        return builder.build();
    }

    public DeviceRpcCallMsg constructDeviceRpcResponseMsg(DeviceId deviceId, JsonNode body) {
        RpcResponseMsg.Builder responseBuilder = RpcResponseMsg.newBuilder();
        if (body.has("error")) {
            responseBuilder.setError(body.get("error").asText());
        } else {
            responseBuilder.setResponse(body.get("response").asText());
        }
        UUID requestUUID = UUID.fromString(body.get("requestUUID").asText());
        DeviceRpcCallMsg.Builder builder = DeviceRpcCallMsg.newBuilder()
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setRequestUuidMSB(requestUUID.getMostSignificantBits())
                .setRequestUuidLSB(requestUUID.getLeastSignificantBits())
                .setExpirationTime(body.get("expirationTime").asLong())
                .setRequestId(body.get("requestId").asInt())
                .setOneway(body.get("oneway").asBoolean())
                .setResponseMsg(responseBuilder.build());
        return builder.build();
    }
}