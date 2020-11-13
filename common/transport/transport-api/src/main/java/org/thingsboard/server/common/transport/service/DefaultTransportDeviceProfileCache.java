/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.common.transport.service;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.transport.TransportDeviceProfileCache;
import org.thingsboard.server.common.transport.util.DataDecodingEncodingService;
import org.thingsboard.server.queue.util.TbTransportComponent;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@TbTransportComponent
public class DefaultTransportDeviceProfileCache implements TransportDeviceProfileCache {

    private final ConcurrentMap<DeviceProfileId, DeviceProfile> deviceProfiles = new ConcurrentHashMap<>();

    private final DataDecodingEncodingService dataDecodingEncodingService;

    public DefaultTransportDeviceProfileCache(DataDecodingEncodingService dataDecodingEncodingService) {
        this.dataDecodingEncodingService = dataDecodingEncodingService;
    }

    @Override
    public DeviceProfile getOrCreate(DeviceProfileId id, ByteString profileBody) {
        DeviceProfile profile = deviceProfiles.get(id);
        if (profile == null) {
            Optional<DeviceProfile> deviceProfile = dataDecodingEncodingService.decode(profileBody.toByteArray());
            if (deviceProfile.isPresent()) {
                profile = deviceProfile.get();
                deviceProfiles.put(id, profile);
            }
        }
        return profile;
    }

    @Override
    public DeviceProfile get(DeviceProfileId id) {
        return deviceProfiles.get(id);
    }

    @Override
    public void put(DeviceProfile profile) {
        deviceProfiles.put(profile.getId(), profile);
    }

    @Override
    public DeviceProfile put(ByteString profileBody) {
        Optional<DeviceProfile> deviceProfile = dataDecodingEncodingService.decode(profileBody.toByteArray());
        if (deviceProfile.isPresent()) {
            put(deviceProfile.get());
            return deviceProfile.get();
        } else {
            return null;
        }
    }

    @Override
    public void evict(DeviceProfileId id) {
        deviceProfiles.remove(id);
    }
}
