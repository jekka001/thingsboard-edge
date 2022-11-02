/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.cloud.rpc.processor;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.device.data.DeviceData;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rpc.RpcError;
import org.thingsboard.server.common.data.rpc.ToDeviceRpcRequestBody;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.msg.rpc.FromDeviceRpcResponse;
import org.thingsboard.server.common.msg.rpc.ToDeviceRpcRequest;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.queue.util.DataDecodingEncodingService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class DeviceCloudProcessor extends BaseCloudProcessor {

    @Autowired
    private DataDecodingEncodingService dataDecodingEncodingService;

    public ListenableFuture<Void> processDeviceMsgFromCloud(TenantId tenantId,
                                                            DeviceUpdateMsg deviceUpdateMsg,
                                                            Long queueStartTs) {
        DeviceId deviceId = new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
        switch (deviceUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                createDevice(tenantId, deviceUpdateMsg);
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                if (deviceUpdateMsg.hasEntityGroupIdMSB() && deviceUpdateMsg.hasEntityGroupIdLSB()) {
                    UUID entityGroupUUID = safeGetUUID(deviceUpdateMsg.getEntityGroupIdMSB(),
                            deviceUpdateMsg.getEntityGroupIdLSB());
                    EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
                    entityGroupService.removeEntityFromEntityGroup(tenantId, entityGroupId, deviceId);
                } else {
                    Device deviceById = deviceService.findDeviceById(tenantId, deviceId);
                    if (deviceById != null) {
                        deviceService.deleteDevice(tenantId, deviceId);
                    }
                }
                break;
            case ENTITY_MERGE_RPC_MESSAGE:
                deviceCreationLock.lock();
                try {
                    String deviceName = deviceUpdateMsg.getName();
                    if (deviceUpdateMsg.hasConflictName()) {
                        deviceName = deviceUpdateMsg.getConflictName();
                    }
                    Device deviceByName = deviceService.findDeviceByTenantIdAndName(tenantId, deviceName);
                    if (deviceByName != null) {
                        deviceByName.setName(deviceUpdateMsg.getName());
                        deviceService.saveDevice(deviceByName);
                    }
                } finally {
                    deviceCreationLock.unlock();
                }
                break;
            case UNRECOGNIZED:
                return handleUnsupportedMsgType(deviceUpdateMsg.getMsgType());
        }

        SettableFuture<Void> futureToSet = SettableFuture.create();
        Futures.addCallback(requestForAdditionalData(tenantId, deviceUpdateMsg.getMsgType(), deviceId, queueStartTs), new FutureCallback<>() {
            @Override
            public void onSuccess(Void ignored) {
                try {
                    if (UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE.equals(deviceUpdateMsg.getMsgType()) ||
                            UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE.equals(deviceUpdateMsg.getMsgType())) {
                        cloudEventService.saveCloudEvent(tenantId, CloudEventType.DEVICE, EdgeEventActionType.CREDENTIALS_REQUEST,
                                deviceId, null, null, queueStartTs);
                    } else if (UpdateMsgType.ENTITY_MERGE_RPC_MESSAGE.equals(deviceUpdateMsg.getMsgType())) {
                        cloudEventService.saveCloudEvent(tenantId, CloudEventType.DEVICE, EdgeEventActionType.CREDENTIALS_UPDATED,
                                deviceId, null, null, 0L);
                    }
                    futureToSet.set(null);
                } catch (Exception e) {
                    log.error("Failed to save credential updated cloud event, deviceUpdateMsg [{}]", deviceUpdateMsg, e);
                    futureToSet.setException(e);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("Failed to request for additional data, deviceUpdateMsg [{}]", deviceUpdateMsg, t);
                futureToSet.setException(t);
            }
        }, dbCallbackExecutor);
        return futureToSet;
    }

    public ListenableFuture<Void> processDeviceCredentialsMsgFromCloud(TenantId tenantId, DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg) {
        DeviceId deviceId = new DeviceId(new UUID(deviceCredentialsUpdateMsg.getDeviceIdMSB(), deviceCredentialsUpdateMsg.getDeviceIdLSB()));
        Device device = deviceService.findDeviceById(tenantId, deviceId);

        if (device != null) {
            log.debug("Updating device credentials for device [{}]. New device credentials Id [{}], value [{}]",
                    device.getName(), deviceCredentialsUpdateMsg.getCredentialsId(), deviceCredentialsUpdateMsg.getCredentialsValue());
            try {
                DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId, device.getId());
                deviceCredentials.setCredentialsType(DeviceCredentialsType.valueOf(deviceCredentialsUpdateMsg.getCredentialsType()));
                deviceCredentials.setCredentialsId(deviceCredentialsUpdateMsg.getCredentialsId());
                deviceCredentials.setCredentialsValue(deviceCredentialsUpdateMsg.hasCredentialsValue()
                        ? deviceCredentialsUpdateMsg.getCredentialsValue() : null);
                deviceCredentialsService.updateDeviceCredentials(tenantId, deviceCredentials);
            } catch (Exception e) {
                String errMsg = String.format("Can't update device credentials for device [%s], deviceCredentialsUpdateMsg [%s]",
                        device.getName(), deviceCredentialsUpdateMsg);
                log.error(errMsg, e);
                return Futures.immediateFailedFuture(new RuntimeException(errMsg, e));
            }
        }
        return Futures.immediateFuture(null);
    }

    private Device createDevice(TenantId tenantId, DeviceUpdateMsg deviceUpdateMsg) {
        Device device;
        deviceCreationLock.lock();
        try {
            DeviceId deviceId = new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
            device = deviceService.findDeviceById(tenantId, deviceId);
            boolean created = false;
            String deviceName = deviceUpdateMsg.getName();
            if (device == null) {
                created = true;
                device = new Device();
                device.setTenantId(tenantId);
                device.setCreatedTime(Uuids.unixTimestamp(deviceId.getId()));
                Device deviceByName = deviceService.findDeviceByTenantIdAndName(tenantId, deviceName);
                if (deviceByName != null) {
                    deviceName = deviceName + "_" + RandomStringUtils.randomAlphabetic(15);
                    log.warn("Device with name {} already exists on the edge. Renaming device name to {}",
                            deviceUpdateMsg.getName(), deviceName);
                }
            }
            device.setName(deviceName);
            device.setType(deviceUpdateMsg.getType());
            device.setLabel(deviceUpdateMsg.hasLabel() ? deviceUpdateMsg.getLabel() : null);
            device.setAdditionalInfo(deviceUpdateMsg.hasAdditionalInfo()
                    ? JacksonUtil.toJsonNode(deviceUpdateMsg.getAdditionalInfo()) : null);
            if (deviceUpdateMsg.hasDeviceProfileIdMSB() && deviceUpdateMsg.hasDeviceProfileIdLSB()) {
                DeviceProfileId deviceProfileId = new DeviceProfileId(
                        new UUID(deviceUpdateMsg.getDeviceProfileIdMSB(),
                                deviceUpdateMsg.getDeviceProfileIdLSB()));
                device.setDeviceProfileId(deviceProfileId);
            } else {
                device.setDeviceProfileId(null);
            }
            device.setCustomerId(safeGetCustomerId(deviceUpdateMsg.getCustomerIdMSB(), deviceUpdateMsg.getCustomerIdLSB()));
            Optional<DeviceData> deviceDataOpt =
                    dataDecodingEncodingService.decode(deviceUpdateMsg.getDeviceDataBytes().toByteArray());
            if (deviceDataOpt.isPresent()) {
                device.setDeviceData(deviceDataOpt.get());
            } else {
                device.setDeviceData(null);
            }
            if (created) {
                deviceValidator.validate(device, Device::getTenantId);
                device.setId(deviceId);
            } else {
                deviceValidator.validate(device, Device::getTenantId);
            }
            CustomerId customerId = safeGetCustomerId(deviceUpdateMsg.getCustomerIdMSB(),
                    deviceUpdateMsg.getCustomerIdLSB());
            device.setCustomerId(customerId);
            Device savedDevice = deviceService.saveDevice(device, false);
            if (created) {
                DeviceCredentials deviceCredentials = new DeviceCredentials();
                deviceCredentials.setDeviceId(new DeviceId(savedDevice.getUuidId()));
                deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
                deviceCredentials.setCredentialsId(RandomStringUtils.randomAlphanumeric(20));
                deviceCredentialsService.createDeviceCredentials(device.getTenantId(), deviceCredentials);
                entityGroupService.addEntityToEntityGroupAll(savedDevice.getTenantId(), savedDevice.getOwnerId(), savedDevice.getId());
            }
            addToEntityGroup(tenantId, deviceUpdateMsg, deviceId);
            tbClusterService.onDeviceUpdated(savedDevice, created ? null : device, false, false);
        } finally {
            deviceCreationLock.unlock();
        }
        return device;
    }

    private void addToEntityGroup(TenantId tenantId, DeviceUpdateMsg deviceUpdateMsg, DeviceId deviceId) {
        if (deviceUpdateMsg.hasEntityGroupIdMSB() && deviceUpdateMsg.hasEntityGroupIdLSB()) {
            UUID entityGroupUUID = safeGetUUID(deviceUpdateMsg.getEntityGroupIdMSB(),
                    deviceUpdateMsg.getEntityGroupIdLSB());
            EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
            addEntityToGroup(tenantId, entityGroupId, deviceId);
        }
    }

    public ListenableFuture<Void> processDeviceRpcRequestFromCloud(TenantId tenantId, DeviceRpcCallMsg deviceRpcRequestMsg) {
        DeviceId deviceId = new DeviceId(new UUID(deviceRpcRequestMsg.getDeviceIdMSB(), deviceRpcRequestMsg.getDeviceIdLSB()));
        boolean oneWay = deviceRpcRequestMsg.getOneway();
        long expTime = deviceRpcRequestMsg.getExpirationTime();

        ToDeviceRpcRequestBody body = new ToDeviceRpcRequestBody(deviceRpcRequestMsg.getRequestMsg().getMethod(),
                deviceRpcRequestMsg.getRequestMsg().getParams());

        UUID requestUUID = new UUID(deviceRpcRequestMsg.getRequestUuidMSB(), deviceRpcRequestMsg.getRequestUuidLSB());
        // TODO: voba - add retries from the cloud
        ToDeviceRpcRequest rpcRequest = new ToDeviceRpcRequest(requestUUID,
                tenantId,
                deviceId,
                oneWay,
                expTime,
                body,
                false,
                1,
                null
        );

        // @voba - changes to be in sync with cloud version
        SecurityUser dummySecurityUser = new SecurityUser();
        tbCoreDeviceRpcService.processRestApiRpcRequest(rpcRequest,
                fromDeviceRpcResponse -> reply(rpcRequest, deviceRpcRequestMsg.getRequestId(), fromDeviceRpcResponse),
                dummySecurityUser);
        return Futures.immediateFuture(null);
    }

    private void reply(ToDeviceRpcRequest rpcRequest, int requestId, FromDeviceRpcResponse response) {
        try {
            Optional<RpcError> rpcError = response.getError();
            ObjectNode body = JacksonUtil.OBJECT_MAPPER.createObjectNode();
            body.put("requestUUID", rpcRequest.getId().toString());
            body.put("expirationTime", rpcRequest.getExpirationTime());
            body.put("oneway", rpcRequest.isOneway());
            body.put("requestId", requestId);
            if (rpcError.isPresent()) {
                RpcError error = rpcError.get();
                body.put("error", error.name());
            } else {
                body.put("response", response.getResponse().orElse("{}"));
            }
            cloudEventService.saveCloudEvent(rpcRequest.getTenantId(), CloudEventType.DEVICE, EdgeEventActionType.RPC_CALL,
                    rpcRequest.getDeviceId(), body, null, 0L);
        } catch (Exception e) {
            log.debug("Can't process RPC response [{}] [{}]", rpcRequest, response, e);
        }
    }

    public UplinkMsg processRpcCallResponseMsgToCloud(CloudEvent cloudEvent) {
        DeviceId deviceId = new DeviceId(cloudEvent.getEntityId());
        DeviceRpcCallMsg rpcResponseMsg = deviceMsgConstructor.constructDeviceRpcResponseMsg(deviceId, cloudEvent.getEntityBody());
        return UplinkMsg.newBuilder()
                .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                .addDeviceRpcCallMsg(rpcResponseMsg).build();
    }

    public UplinkMsg processDeviceMsgToCloud(TenantId tenantId, CloudEvent cloudEvent) {
        DeviceId deviceId = new DeviceId(cloudEvent.getEntityId());
        UplinkMsg msg = null;
        switch (cloudEvent.getAction()) {
            case ADDED:
            case UPDATED:
            case ADDED_TO_ENTITY_GROUP:
                Device device = deviceService.findDeviceById(cloudEvent.getTenantId(), deviceId);
                if (device != null) {
                    UpdateMsgType msgType = getUpdateMsgType(cloudEvent.getAction());
                    EntityGroupId entityGroupId = cloudEvent.getEntityGroupId() != null ? new EntityGroupId(cloudEvent.getEntityGroupId()) : null;
                    DeviceUpdateMsg deviceUpdateMsg =
                            deviceMsgConstructor.constructDeviceUpdatedMsg(msgType, device, null, entityGroupId);
                    msg = UplinkMsg.newBuilder()
                            .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                            .addDeviceUpdateMsg(deviceUpdateMsg).build();
                } else {
                    log.info("Skipping event as device was not found [{}]", cloudEvent);
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
                EntityGroupId entityGroupId = cloudEvent.getEntityGroupId() != null ? new EntityGroupId(cloudEvent.getEntityGroupId()) : null;
                DeviceUpdateMsg deviceUpdateMsg =
                        deviceMsgConstructor.constructDeviceDeleteMsg(deviceId, entityGroupId);
                msg = UplinkMsg.newBuilder()
                        .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                        .addDeviceUpdateMsg(deviceUpdateMsg).build();
                break;
            case CREDENTIALS_UPDATED:
                DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId, deviceId);
                if (deviceCredentials != null) {
                    DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg =
                            deviceMsgConstructor.constructDeviceCredentialsUpdatedMsg(deviceCredentials);
                    msg = UplinkMsg.newBuilder()
                            .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                            .addDeviceCredentialsUpdateMsg(deviceCredentialsUpdateMsg).build();
                } else {
                    log.info("Skipping event as device credentials was not found [{}]", cloudEvent);
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported edge action type [" + cloudEvent.getAction() + "]");
        }
        return msg;
    }

}
