/**
 * Copyright © 2016-2023 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.edge.rpc.processor.device;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.queue.util.DataDecodingEncodingService;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class BaseDeviceProfileProcessor extends BaseEdgeProcessor {

    @Autowired
    private DataDecodingEncodingService dataDecodingEncodingService;

    protected boolean saveOrUpdateDeviceProfile(TenantId tenantId, DeviceProfileId deviceProfileId, DeviceProfileUpdateMsg deviceProfileUpdateMsg) {
        boolean created = false;
        deviceProfileCreationLock.lock();
        try {
            DeviceProfile deviceProfile = deviceProfileService.findDeviceProfileById(tenantId, deviceProfileId);
            if (deviceProfile == null) {
                created = true;
                deviceProfile = new DeviceProfile();
                deviceProfile.setTenantId(tenantId);
                deviceProfile.setCreatedTime(Uuids.unixTimestamp(deviceProfileId.getId()));
            }
            deviceProfile.setName(deviceProfileUpdateMsg.getName());
            deviceProfile.setDescription(deviceProfileUpdateMsg.hasDescription() ? deviceProfileUpdateMsg.getDescription() : null);
            deviceProfile.setDefault(deviceProfileUpdateMsg.getDefault());
            deviceProfile.setType(DeviceProfileType.valueOf(deviceProfileUpdateMsg.getType()));
            deviceProfile.setTransportType(deviceProfileUpdateMsg.hasTransportType()
                    ? DeviceTransportType.valueOf(deviceProfileUpdateMsg.getTransportType()) : DeviceTransportType.DEFAULT);
            deviceProfile.setImage(deviceProfileUpdateMsg.hasImage()
                    ? new String(deviceProfileUpdateMsg.getImage().toByteArray(), StandardCharsets.UTF_8) : null);
            deviceProfile.setProvisionType(deviceProfileUpdateMsg.hasProvisionType()
                    ? DeviceProfileProvisionType.valueOf(deviceProfileUpdateMsg.getProvisionType()) : DeviceProfileProvisionType.DISABLED);
            deviceProfile.setProvisionDeviceKey(deviceProfileUpdateMsg.hasProvisionDeviceKey()
                    ? deviceProfileUpdateMsg.getProvisionDeviceKey() : null);
            deviceProfile.setDefaultQueueName(deviceProfileUpdateMsg.getDefaultQueueName());

            Optional<DeviceProfileData> profileDataOpt =
                    dataDecodingEncodingService.decode(deviceProfileUpdateMsg.getProfileDataBytes().toByteArray());
            deviceProfile.setProfileData(profileDataOpt.orElse(null));

            UUID defaultRuleChainUUID = safeGetUUID(deviceProfileUpdateMsg.getDefaultRuleChainIdMSB(), deviceProfileUpdateMsg.getDefaultRuleChainIdLSB());
            deviceProfile.setDefaultRuleChainId(defaultRuleChainUUID != null ? new RuleChainId(defaultRuleChainUUID) : null);

            UUID defaultDashboardUUID = safeGetUUID(deviceProfileUpdateMsg.getDefaultDashboardIdMSB(), deviceProfileUpdateMsg.getDefaultDashboardIdLSB());
            deviceProfile.setDefaultDashboardId(defaultDashboardUUID != null ? new DashboardId(defaultDashboardUUID) : null);

            String defaultQueueName = StringUtils.isNotBlank(deviceProfileUpdateMsg.getDefaultQueueName())
                    ? deviceProfileUpdateMsg.getDefaultQueueName() : null;
            deviceProfile.setDefaultQueueName(defaultQueueName);

            UUID firmwareUUID = safeGetUUID(deviceProfileUpdateMsg.getFirmwareIdMSB(), deviceProfileUpdateMsg.getFirmwareIdLSB());
            deviceProfile.setFirmwareId(firmwareUUID != null ? new OtaPackageId(firmwareUUID) : null);

            UUID softwareUUID = safeGetUUID(deviceProfileUpdateMsg.getSoftwareIdMSB(), deviceProfileUpdateMsg.getSoftwareIdLSB());
            deviceProfile.setSoftwareId(softwareUUID != null ? new OtaPackageId(softwareUUID) : null);


            deviceProfileValidator.validate(deviceProfile, DeviceProfile::getTenantId);
            if (created) {
                deviceProfile.setId(deviceProfileId);
            }
            deviceProfileService.saveDeviceProfile(deviceProfile, false);
        } finally {
            deviceProfileCreationLock.unlock();
        }
        return created;
    }
}
