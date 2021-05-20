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
package org.thingsboard.server.service.cloud.processor;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.RpcError;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.edge.CloudType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rpc.ToDeviceRpcRequestBody;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.msg.rpc.ToDeviceRpcRequest;
import org.thingsboard.server.gen.edge.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.UpdateMsgType;
import org.thingsboard.server.service.rpc.FromDeviceRpcResponse;
import org.thingsboard.server.service.state.DeviceStateService;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class DeviceCloudProcessor extends BaseCloudProcessor {

    @Autowired
    private DeviceStateService deviceStateService;

    public ListenableFuture<Void> onDeviceUpdate(TenantId tenantId, CustomerId customerId, DeviceUpdateMsg deviceUpdateMsg, CloudType cloudType) {
        DeviceId deviceId = new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
        switch (deviceUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                saveOrUpdateDevice(tenantId, customerId, deviceUpdateMsg, cloudType);
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                UUID entityGroupUUID = safeGetUUID(deviceUpdateMsg.getEntityGroupIdMSB(), deviceUpdateMsg.getEntityGroupIdLSB());
                if (entityGroupUUID != null) {
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
                try {
                    deviceCreationLock.lock();
                    String deviceName = deviceUpdateMsg.getName();
                    if (StringUtils.isNoneBlank(deviceUpdateMsg.getConflictName())) {
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
                log.error("Unsupported msg type");
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type" + deviceUpdateMsg.getMsgType()));
        }

        SettableFuture<Void> futureToSet = SettableFuture.create();
        Futures.addCallback(requestForAdditionalData(tenantId, deviceUpdateMsg.getMsgType(), deviceId), new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Void unused) {
                FutureCallback<CloudEvent> cloudEventFutureCallback = new FutureCallback<>() {
                    @Override
                    public void onSuccess(@Nullable CloudEvent cloudEvent) {
                        futureToSet.set(null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        futureToSet.setException(t);
                    }
                };
                if (UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE.equals(deviceUpdateMsg.getMsgType()) ||
                        UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE.equals(deviceUpdateMsg.getMsgType())) {
                    Futures.addCallback(saveCloudEvent(tenantId, CloudEventType.DEVICE, ActionType.CREDENTIALS_REQUEST, deviceId, null),
                            cloudEventFutureCallback,
                            dbCallbackExecutor);
                } else if (UpdateMsgType.ENTITY_MERGE_RPC_MESSAGE.equals(deviceUpdateMsg.getMsgType())) {
                    Futures.addCallback(saveCloudEvent(tenantId, CloudEventType.DEVICE, ActionType.CREDENTIALS_UPDATED, deviceId, null),
                            cloudEventFutureCallback,
                            dbCallbackExecutor);
                } else {
                    futureToSet.set(null);
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

    public ListenableFuture<Void> onDeviceCredentialsUpdate(TenantId tenantId, DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg) {
        DeviceId deviceId = new DeviceId(new UUID(deviceCredentialsUpdateMsg.getDeviceIdMSB(), deviceCredentialsUpdateMsg.getDeviceIdLSB()));
        Device device = deviceService.findDeviceById(tenantId, deviceId);

        if (device != null) {
            log.debug("Updating device credentials for device [{}]. New device credentials Id [{}], value [{}]",
                    device.getName(), deviceCredentialsUpdateMsg.getCredentialsId(), deviceCredentialsUpdateMsg.getCredentialsValue());
            try {
                DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId, device.getId());
                deviceCredentials.setCredentialsType(DeviceCredentialsType.valueOf(deviceCredentialsUpdateMsg.getCredentialsType()));
                deviceCredentials.setCredentialsId(deviceCredentialsUpdateMsg.getCredentialsId());
                deviceCredentials.setCredentialsValue(deviceCredentialsUpdateMsg.getCredentialsValue());
                deviceCredentialsService.updateDeviceCredentials(tenantId, deviceCredentials);
            } catch (Exception e) {
                log.error("Can't update device credentials for device [{}], deviceCredentialsUpdateMsg [{}]",
                        device.getName(), deviceCredentialsUpdateMsg, e);
                return Futures.immediateFailedFuture(
                        new RuntimeException("Can't update device credentials for device " +
                                device.getName() + ", deviceCredentialsUpdateMsg " + deviceCredentialsUpdateMsg,
                                e));
            }
        }
        return Futures.immediateFuture(null);
    }

    private Device saveOrUpdateDevice(TenantId tenantId, CustomerId customerId, DeviceUpdateMsg deviceUpdateMsg, CloudType cloudType) {
        Device device;
        try {
            deviceCreationLock.lock();
            DeviceId deviceId = new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
            device = deviceService.findDeviceById(tenantId, deviceId);
            boolean created = false;
            if (device == null) {
                device = new Device();
                device.setTenantId(tenantId);
                device.setId(deviceId);
                device.setCreatedTime(Uuids.unixTimestamp(deviceId.getId()));
                created = true;
            }
            device.setName(deviceUpdateMsg.getName());
            device.setType(deviceUpdateMsg.getType());
            device.setLabel(deviceUpdateMsg.getLabel());
            if (deviceUpdateMsg.getDeviceProfileIdMSB() != 0 && deviceUpdateMsg.getDeviceProfileIdLSB() != 0) {
                DeviceProfileId deviceProfileId = new DeviceProfileId(
                        new UUID(deviceUpdateMsg.getDeviceProfileIdMSB(), deviceUpdateMsg.getDeviceProfileIdLSB()));
                device.setDeviceProfileId(deviceProfileId);
            }
            device.setAdditionalInfo(JacksonUtil.toJsonNode(deviceUpdateMsg.getAdditionalInfo()));
            CustomerId deviceCustomerId = safeSetCustomerId(deviceUpdateMsg, cloudType, device);
            Device savedDevice = deviceService.saveDevice(device);
            if (created) {
                DeviceCredentials deviceCredentials = new DeviceCredentials();
                deviceCredentials.setDeviceId(new DeviceId(savedDevice.getUuidId()));
                deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
                deviceCredentials.setCredentialsId(RandomStringUtils.randomAlphanumeric(20));
                deviceCredentialsService.createDeviceCredentials(device.getTenantId(), deviceCredentials);
                entityGroupService.addEntityToEntityGroupAll(savedDevice.getTenantId(), savedDevice.getOwnerId(), savedDevice.getId());

                deviceStateService.onDeviceAdded(savedDevice);
            }
            addToEntityGroup(tenantId, customerId, deviceUpdateMsg, cloudType, deviceId, deviceCustomerId);
        } finally {
            deviceCreationLock.unlock();
        }
        return device;
    }

    private CustomerId safeSetCustomerId(DeviceUpdateMsg deviceUpdateMsg, CloudType cloudType, Device device) {
        CustomerId deviceCustomerId = safeGetCustomerId(deviceUpdateMsg.getCustomerIdMSB(), deviceUpdateMsg.getCustomerIdLSB());
        if (CloudType.PE.equals(cloudType)) {
            device.setCustomerId(deviceCustomerId);
        }
        return deviceCustomerId;
    }

    private void addToEntityGroup(TenantId tenantId, CustomerId customerId, DeviceUpdateMsg deviceUpdateMsg, CloudType cloudType, DeviceId deviceId, CustomerId deviceCustomerId) {
        if (CloudType.CE.equals(cloudType)) {
            if (deviceCustomerId != null && deviceCustomerId.equals(customerId)) {
                EntityGroup customerDevicesEntityGroup =
                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.DEVICE);
                entityGroupService.addEntityToEntityGroup(tenantId, customerDevicesEntityGroup.getId(), deviceId);
            }
            if ((deviceCustomerId == null || deviceCustomerId.isNullUid()) &&
                    (customerId != null && !customerId.isNullUid())) {
                EntityGroup customerDevicesEntityGroup =
                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.DEVICE);
                entityGroupService.removeEntityFromEntityGroup(tenantId, customerDevicesEntityGroup.getId(), deviceId);
            }
        } else {
            UUID entityGroupUUID = safeGetUUID(deviceUpdateMsg.getEntityGroupIdMSB(), deviceUpdateMsg.getEntityGroupIdLSB());
            if (entityGroupUUID != null) {
                EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
                addEntityToGroup(tenantId, entityGroupId, deviceId);
            }
        }
    }

    public ListenableFuture<Void> onDeviceRpcRequest(TenantId tenantId, DeviceRpcCallMsg deviceRpcRequestMsg) {
        DeviceId deviceId = new DeviceId(new UUID(deviceRpcRequestMsg.getDeviceIdMSB(), deviceRpcRequestMsg.getDeviceIdLSB()));
        boolean oneWay = deviceRpcRequestMsg.getOneway();
        long expTime = deviceRpcRequestMsg.getExpirationTime();

        ToDeviceRpcRequestBody body = new ToDeviceRpcRequestBody(deviceRpcRequestMsg.getRequestMsg().getMethod(),
                deviceRpcRequestMsg.getRequestMsg().getParams());

        UUID requestUUID = new UUID(deviceRpcRequestMsg.getRequestUuidMSB(), deviceRpcRequestMsg.getRequestUuidLSB());
        ToDeviceRpcRequest rpcRequest = new ToDeviceRpcRequest(requestUUID,
                tenantId,
                deviceId,
                oneWay,
                expTime,
                body
        );

        tbCoreDeviceRpcService.processRestApiRpcRequest(rpcRequest,
                fromDeviceRpcResponse -> reply(rpcRequest, deviceRpcRequestMsg.getRequestId(), fromDeviceRpcResponse));
        return Futures.immediateFuture(null);
    }

    public void reply(ToDeviceRpcRequest rpcRequest, int requestId, FromDeviceRpcResponse response) {
        try {
            Optional<RpcError> rpcError = response.getError();
            ObjectNode body = mapper.createObjectNode();
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
            saveCloudEvent(rpcRequest.getTenantId(), CloudEventType.DEVICE, ActionType.RPC_CALL, rpcRequest.getDeviceId(), body);
        } catch (Exception e) {
            log.debug("Can't process RPC response [{}] [{}]", rpcRequest, response);
        }
    }

}
