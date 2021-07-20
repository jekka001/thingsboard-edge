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
package org.thingsboard.server.service.edge.rpc.processor;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.transport.util.DataDecodingEncodingService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.gen.edge.v1.DeviceProfileDevicesRequestMsg;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class DeviceProfileCloudProcessor extends BaseCloudProcessor {

    @Autowired
    private DeviceProfileService deviceProfileService;

    @Autowired
    private DataDecodingEncodingService dataDecodingEncodingService;

    public ListenableFuture<Void> processDeviceProfileMsgFromCloud(TenantId tenantId, DeviceProfileUpdateMsg deviceProfileUpdateMsg) {
        DeviceProfileId deviceProfileId = new DeviceProfileId(new UUID(deviceProfileUpdateMsg.getIdMSB(), deviceProfileUpdateMsg.getIdLSB()));
        switch (deviceProfileUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                deviceCreationLock.lock();
                try {
                    DeviceProfile deviceProfile = deviceProfileService.findDeviceProfileById(tenantId, deviceProfileId);
                    boolean created = false;
                    if (deviceProfile == null) {
                        deviceProfile = new DeviceProfile();
                        deviceProfile.setId(deviceProfileId);
                        deviceProfile.setCreatedTime(Uuids.unixTimestamp(deviceProfileId.getId()));
                        deviceProfile.setTenantId(tenantId);
                        created = true;
                    }
                    deviceProfile.setName(deviceProfileUpdateMsg.getName());
                    deviceProfile.setDescription(deviceProfileUpdateMsg.getDescription());
                    deviceProfile.setDefault(deviceProfileUpdateMsg.getDefault());
                    deviceProfile.setType(DeviceProfileType.valueOf(deviceProfileUpdateMsg.getType()));
                    deviceProfile.setTransportType(DeviceTransportType.valueOf(deviceProfileUpdateMsg.getTransportType()));
                    if (deviceProfileUpdateMsg.getImage() != null
                            && deviceProfileUpdateMsg.getImage().toByteArray() != null
                            && deviceProfileUpdateMsg.getImage().toByteArray().length > 0) {
                        deviceProfile.setImage(new String(deviceProfileUpdateMsg.getImage().toByteArray(), StandardCharsets.UTF_8));
                    }
                    if (!StringUtils.isBlank(deviceProfileUpdateMsg.getProvisionType())) {
                        deviceProfile.setProvisionType(DeviceProfileProvisionType.valueOf(deviceProfileUpdateMsg.getProvisionType()));
                    }
                    String defaultQueueName = StringUtils.isBlank(deviceProfileUpdateMsg.getDefaultQueueName())
                            ? null : deviceProfileUpdateMsg.getDefaultQueueName();
                    deviceProfile.setDefaultQueueName(defaultQueueName);
                    String provisionDeviceKey = StringUtils.isBlank(deviceProfileUpdateMsg.getProvisionDeviceKey())
                            ? null : deviceProfileUpdateMsg.getProvisionDeviceKey();
                    deviceProfile.setProvisionDeviceKey(provisionDeviceKey);
                    Optional<DeviceProfileData> profileDataOpt =
                            dataDecodingEncodingService.decode(deviceProfileUpdateMsg.getProfileDataBytes().toByteArray());
                    if (profileDataOpt.isPresent()) {
                        deviceProfile.setProfileData(profileDataOpt.get());
                    }
                    if (deviceProfileUpdateMsg.getDefaultRuleChainIdMSB() != 0 &&
                            deviceProfileUpdateMsg.getDefaultRuleChainIdLSB() != 0) {
                        RuleChainId defaultRuleChainId = new RuleChainId(
                                new UUID(deviceProfileUpdateMsg.getDefaultRuleChainIdMSB(), deviceProfileUpdateMsg.getDefaultRuleChainIdLSB()));
                        deviceProfile.setDefaultRuleChainId(defaultRuleChainId);
                    }
                    deviceProfileService.saveDeviceProfile(deviceProfile, false);

                    saveCloudEvent(tenantId, CloudEventType.DEVICE_PROFILE, ActionType.DEVICE_PROFILE_DEVICES_REQUEST, deviceProfileId, null);
                } finally {
                    deviceCreationLock.unlock();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                DeviceProfile deviceProfile = deviceProfileService.findDeviceProfileById(tenantId, deviceProfileId);
                if (deviceProfile != null) {
                    deviceProfileService.deleteDeviceProfile(tenantId, deviceProfileId);
                }
                break;
            case UNRECOGNIZED:
                log.error("Unsupported msg type");
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type " + deviceProfileUpdateMsg.getMsgType()));
        }
        return Futures.immediateFuture(null);
    }

    public UplinkMsg processDeviceProfileDevicesRequestMsgToCloud(CloudEvent cloudEvent) {
        EntityId deviceProfileId = EntityIdFactory.getByCloudEventTypeAndUuid(cloudEvent.getCloudEventType(), cloudEvent.getEntityId());
        DeviceProfileDevicesRequestMsg deviceProfileDevicesRequestMsg = DeviceProfileDevicesRequestMsg.newBuilder()
                .setDeviceProfileIdMSB(deviceProfileId.getId().getMostSignificantBits())
                .setDeviceProfileIdLSB(deviceProfileId.getId().getLeastSignificantBits())
                .build();
        UplinkMsg.Builder builder = UplinkMsg.newBuilder()
                .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                .addDeviceProfileDevicesRequestMsg(deviceProfileDevicesRequestMsg);
        return builder.build();
    }
}
