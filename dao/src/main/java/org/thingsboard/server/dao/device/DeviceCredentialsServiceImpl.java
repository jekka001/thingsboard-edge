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
package org.thingsboard.server.dao.device;


import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.device.credentials.BasicMqttCredentials;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.msg.EncryptionUtil;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;

import static org.thingsboard.server.common.data.CacheConstants.DEVICE_CREDENTIALS_CACHE;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service
@Slf4j
public class DeviceCredentialsServiceImpl extends AbstractEntityService implements DeviceCredentialsService {

    @Autowired
    private DeviceCredentialsDao deviceCredentialsDao;

    @Autowired
    private DeviceService deviceService;

    @Override
    public DeviceCredentials findDeviceCredentialsByDeviceId(TenantId tenantId, DeviceId deviceId) {
        log.trace("Executing findDeviceCredentialsByDeviceId [{}]", deviceId);
        validateId(deviceId, "Incorrect deviceId " + deviceId);
        return deviceCredentialsDao.findByDeviceId(tenantId, deviceId.getId());
    }

    @Override
    @Cacheable(cacheNames = DEVICE_CREDENTIALS_CACHE, key = "'deviceCredentials_' + #credentialsId", unless = "#result == null")
    public DeviceCredentials findDeviceCredentialsByCredentialsId(String credentialsId) {
        log.trace("Executing findDeviceCredentialsByCredentialsId [{}]", credentialsId);
        validateString(credentialsId, "Incorrect credentialsId " + credentialsId);
        return deviceCredentialsDao.findByCredentialsId(new TenantId(EntityId.NULL_UUID), credentialsId);
    }

    @Override
    @CacheEvict(cacheNames = DEVICE_CREDENTIALS_CACHE, keyGenerator = "previousDeviceCredentialsId", beforeInvocation = true)
    public DeviceCredentials updateDeviceCredentials(TenantId tenantId, DeviceCredentials deviceCredentials) {
        return saveOrUpdate(tenantId, deviceCredentials);
    }

    @Override
    public DeviceCredentials createDeviceCredentials(TenantId tenantId, DeviceCredentials deviceCredentials) {
        return saveOrUpdate(tenantId, deviceCredentials);
    }

    private DeviceCredentials saveOrUpdate(TenantId tenantId, DeviceCredentials deviceCredentials) {
        if(deviceCredentials.getCredentialsType() == null){
            throw new DataValidationException("Device credentials type should be specified");
        }
        switch (deviceCredentials.getCredentialsType()) {
            case X509_CERTIFICATE:
                formatCertData(deviceCredentials);
                break;
            case MQTT_BASIC:
                formatSimpleMqttCredentials(deviceCredentials);
                break;
        }
        log.trace("Executing updateDeviceCredentials [{}]", deviceCredentials);
        credentialsValidator.validate(deviceCredentials, id -> tenantId);
        try {
            return deviceCredentialsDao.save(tenantId, deviceCredentials);
        } catch (Exception t) {
            ConstraintViolationException e = extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null
                    && (e.getConstraintName().equalsIgnoreCase("device_credentials_id_unq_key") || e.getConstraintName().equalsIgnoreCase("device_credentials_device_id_unq_key"))) {
                throw new DataValidationException("Specified credentials are already registered!");
            } else {
                throw t;
            }
        }
    }

    private void formatSimpleMqttCredentials(DeviceCredentials deviceCredentials) {
        BasicMqttCredentials mqttCredentials;
        try {
            mqttCredentials = JacksonUtil.fromString(deviceCredentials.getCredentialsValue(), BasicMqttCredentials.class);
            if (mqttCredentials == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new DataValidationException("Invalid credentials body for simple mqtt credentials!");
        }
        if (StringUtils.isEmpty(mqttCredentials.getClientId()) && StringUtils.isEmpty(mqttCredentials.getUserName())) {
            throw new DataValidationException("Both mqtt client id and user name are empty!");
        }
        if (StringUtils.isEmpty(mqttCredentials.getClientId())) {
            deviceCredentials.setCredentialsId(mqttCredentials.getUserName());
        } else if (StringUtils.isEmpty(mqttCredentials.getUserName())) {
            deviceCredentials.setCredentialsId(EncryptionUtil.getSha3Hash(mqttCredentials.getClientId()));
        } else {
            deviceCredentials.setCredentialsId(EncryptionUtil.getSha3Hash("|", mqttCredentials.getClientId(), mqttCredentials.getUserName()));
        }
        if (!StringUtils.isEmpty(mqttCredentials.getPassword())) {
            mqttCredentials.setPassword(mqttCredentials.getPassword());
        }
        deviceCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
    }

    private void formatCertData(DeviceCredentials deviceCredentials) {
        String cert = EncryptionUtil.trimNewLines(deviceCredentials.getCredentialsValue());
        String sha3Hash = EncryptionUtil.getSha3Hash(cert);
        deviceCredentials.setCredentialsId(sha3Hash);
        deviceCredentials.setCredentialsValue(cert);
    }

    @Override
    @CacheEvict(cacheNames = DEVICE_CREDENTIALS_CACHE, key = "'deviceCredentials_' + #deviceCredentials.credentialsId")
    public void deleteDeviceCredentials(TenantId tenantId, DeviceCredentials deviceCredentials) {
        log.trace("Executing deleteDeviceCredentials [{}]", deviceCredentials);
        deviceCredentialsDao.removeById(tenantId, deviceCredentials.getUuidId());
    }

    private DataValidator<DeviceCredentials> credentialsValidator =
            new DataValidator<DeviceCredentials>() {

                @Override
                protected void validateCreate(TenantId tenantId, DeviceCredentials deviceCredentials) {
                    if (deviceCredentialsDao.findByDeviceId(tenantId, deviceCredentials.getDeviceId().getId()) != null) {
                        throw new DataValidationException("Credentials for this device are already specified!");
                    }
                    if (deviceCredentialsDao.findByCredentialsId(tenantId, deviceCredentials.getCredentialsId()) != null) {
                        throw new DataValidationException("Device credentials are already assigned to another device!");
                    }
                }

                @Override
                protected void validateUpdate(TenantId tenantId, DeviceCredentials deviceCredentials) {
                    if (deviceCredentialsDao.findById(tenantId, deviceCredentials.getUuidId()) == null) {
                        throw new DataValidationException("Unable to update non-existent device credentials!");
                    }
                    DeviceCredentials existingCredentials = deviceCredentialsDao.findByCredentialsId(tenantId, deviceCredentials.getCredentialsId());
                    if (existingCredentials != null && !existingCredentials.getId().equals(deviceCredentials.getId())) {
                        throw new DataValidationException("Device credentials are already assigned to another device!");
                    }
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, DeviceCredentials deviceCredentials) {
                    if (deviceCredentials.getDeviceId() == null) {
                        throw new DataValidationException("Device credentials should be assigned to device!");
                    }
                    if (deviceCredentials.getCredentialsType() == null) {
                        throw new DataValidationException("Device credentials type should be specified!");
                    }
                    if (StringUtils.isEmpty(deviceCredentials.getCredentialsId())) {
                        throw new DataValidationException("Device credentials id should be specified!");
                    }
                    Device device = deviceService.findDeviceById(tenantId, deviceCredentials.getDeviceId());
                    if (device == null) {
                        throw new DataValidationException("Can't assign device credentials to non-existent device!");
                    }
                }
            };

}
