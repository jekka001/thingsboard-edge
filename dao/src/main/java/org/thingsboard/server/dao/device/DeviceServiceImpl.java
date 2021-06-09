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
package org.thingsboard.server.dao.device;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.device.DeviceSearchQuery;
import org.thingsboard.server.common.data.device.credentials.BasicMqttCredentials;
import org.thingsboard.server.common.data.device.data.CoapDeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.data.DefaultDeviceConfiguration;
import org.thingsboard.server.common.data.device.data.DefaultDeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.data.DeviceData;
import org.thingsboard.server.common.data.device.data.DeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.data.Lwm2mDeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.data.MqttDeviceTransportConfiguration;
import org.thingsboard.server.common.data.device.data.SnmpDeviceTransportConfiguration;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.dao.customer.CustomerDao;
import org.thingsboard.server.dao.device.provision.ProvisionFailedException;
import org.thingsboard.server.dao.device.provision.ProvisionRequest;
import org.thingsboard.server.dao.device.provision.ProvisionResponseStatus;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.event.EventService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.ota.OtaPackageService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;
import org.thingsboard.server.dao.tenant.TenantDao;

import javax.annotation.Nullable;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.CacheConstants.DEVICE_CACHE;
import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service
@Slf4j
public class DeviceServiceImpl extends AbstractEntityService implements DeviceService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_DEVICE_PROFILE_ID = "Incorrect deviceProfileId ";
    public static final String INCORRECT_PAGE_LINK = "Incorrect page link ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_DEVICE_ID = "Incorrect deviceId ";

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private DeviceCredentialsService deviceCredentialsService;

    @Autowired
    private EntityService entityService;

    @Autowired
    private DeviceProfileService deviceProfileService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EventService eventService;

    @Autowired
    @Lazy
    private TbTenantProfileCache tenantProfileCache;

    @Autowired
    private OtaPackageService otaPackageService;

    @Override
    public Device findDeviceById(TenantId tenantId, DeviceId deviceId) {
        log.trace("Executing findDeviceById [{}]", deviceId);
        validateId(deviceId, INCORRECT_DEVICE_ID + deviceId);
        if (TenantId.SYS_TENANT_ID.equals(tenantId)) {
            return deviceDao.findById(tenantId, deviceId.getId());
        } else {
            return deviceDao.findDeviceByTenantIdAndId(tenantId, deviceId.getId());
        }
    }

    @Override
    public ListenableFuture<Device> findDeviceByIdAsync(TenantId tenantId, DeviceId deviceId) {
        log.trace("Executing findDeviceById [{}]", deviceId);
        validateId(deviceId, INCORRECT_DEVICE_ID + deviceId);
        if (TenantId.SYS_TENANT_ID.equals(tenantId)) {
            return deviceDao.findByIdAsync(tenantId, deviceId.getId());
        } else {
            return deviceDao.findDeviceByTenantIdAndIdAsync(tenantId, deviceId.getId());
        }
    }

    @Cacheable(cacheNames = DEVICE_CACHE, key = "{#tenantId, #name}")
    @Override
    public Device findDeviceByTenantIdAndName(TenantId tenantId, String name) {
        log.trace("Executing findDeviceByTenantIdAndName [{}][{}]", tenantId, name);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Optional<Device> deviceOpt = deviceDao.findDeviceByTenantIdAndName(tenantId.getId(), name);
        return deviceOpt.orElse(null);
    }

    @CacheEvict(cacheNames = DEVICE_CACHE, key = "{#device.tenantId, #device.name}")
    @Override
    public Device saveDeviceWithAccessToken(Device device, String accessToken) {
        return doSaveDevice(device, accessToken);
    }

    @CacheEvict(cacheNames = DEVICE_CACHE, key = "{#device.tenantId, #device.name}")
    @Override
    public Device saveDevice(Device device) {
        return doSaveDevice(device, null);
    }

    @CacheEvict(cacheNames = DEVICE_CACHE, key = "{#device.tenantId, #device.name}")
    @Override
    public Device saveDeviceWithCredentials(Device device, DeviceCredentials deviceCredentials) {
        if (device.getId() == null) {
            Device deviceWithName = this.findDeviceByTenantIdAndName(device.getTenantId(), device.getName());
            device = deviceWithName == null ? device : deviceWithName.updateDevice(device);
        }
        Device savedDevice = this.saveDeviceWithoutCredentials(device);
        deviceCredentials.setDeviceId(savedDevice.getId());
        if (device.getId() == null) {
            deviceCredentialsService.createDeviceCredentials(savedDevice.getTenantId(), deviceCredentials);
        } else {
            DeviceCredentials foundDeviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(device.getTenantId(), savedDevice.getId());
            if (foundDeviceCredentials == null) {
                deviceCredentialsService.createDeviceCredentials(savedDevice.getTenantId(), deviceCredentials);
            } else {
                deviceCredentialsService.updateDeviceCredentials(device.getTenantId(), deviceCredentials);
            }
        }
        return savedDevice;
    }

    private Device doSaveDevice(Device device, String accessToken) {
        Device savedDevice = this.saveDeviceWithoutCredentials(device);
        if (device.getId() == null) {
            DeviceCredentials deviceCredentials = new DeviceCredentials();
            deviceCredentials.setDeviceId(new DeviceId(savedDevice.getUuidId()));
            deviceCredentials.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN);
            deviceCredentials.setCredentialsId(!StringUtils.isEmpty(accessToken) ? accessToken : RandomStringUtils.randomAlphanumeric(20));
            deviceCredentialsService.createDeviceCredentials(device.getTenantId(), deviceCredentials);
        }
        return savedDevice;
    }

    private Device saveDeviceWithoutCredentials(Device device) {
        log.trace("Executing saveDevice [{}]", device);
        deviceValidator.validate(device, Device::getTenantId);
        Device savedDevice;
        try {
            DeviceProfile deviceProfile;
            if (device.getDeviceProfileId() == null) {
                if (!StringUtils.isEmpty(device.getType())) {
                    deviceProfile = this.deviceProfileService.findOrCreateDeviceProfile(device.getTenantId(), device.getType());
                } else {
                    deviceProfile = this.deviceProfileService.findDefaultDeviceProfile(device.getTenantId());
                }
                device.setDeviceProfileId(new DeviceProfileId(deviceProfile.getId().getId()));
            } else {
                deviceProfile = this.deviceProfileService.findDeviceProfileById(device.getTenantId(), device.getDeviceProfileId());
                if (deviceProfile == null) {
                    throw new DataValidationException("Device is referencing non existing device profile!");
                }
            }
            device.setType(deviceProfile.getName());
            device.setDeviceData(syncDeviceData(deviceProfile, device.getDeviceData()));
            savedDevice = deviceDao.save(device.getTenantId(), device);
        } catch (Exception t) {
            ConstraintViolationException e = extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("device_name_unq_key")) {
                // remove device from cache in case null value cached in the distributed redis.
                removeDeviceFromCacheByName(device.getTenantId(), device.getName());
                throw new DataValidationException("Device with such name already exists!");
            } else {
                throw t;
            }
        }
        if (device.getId() == null) {
            entityGroupService.addEntityToEntityGroupAll(savedDevice.getTenantId(), savedDevice.getOwnerId(), savedDevice.getId());
        }
        return savedDevice;
    }

    private DeviceData syncDeviceData(DeviceProfile deviceProfile, DeviceData deviceData) {
        if (deviceData == null) {
            deviceData = new DeviceData();
        }
        if (deviceData.getConfiguration() == null || !deviceProfile.getType().equals(deviceData.getConfiguration().getType())) {
            switch (deviceProfile.getType()) {
                case DEFAULT:
                    deviceData.setConfiguration(new DefaultDeviceConfiguration());
                    break;
            }
        }
        if (deviceData.getTransportConfiguration() == null || !deviceProfile.getTransportType().equals(deviceData.getTransportConfiguration().getType())) {
            switch (deviceProfile.getTransportType()) {
                case DEFAULT:
                    deviceData.setTransportConfiguration(new DefaultDeviceTransportConfiguration());
                    break;
                case MQTT:
                    deviceData.setTransportConfiguration(new MqttDeviceTransportConfiguration());
                    break;
                case COAP:
                    deviceData.setTransportConfiguration(new CoapDeviceTransportConfiguration());
                    break;
                case LWM2M:
                    deviceData.setTransportConfiguration(new Lwm2mDeviceTransportConfiguration());
                    break;
                case SNMP:
                    deviceData.setTransportConfiguration(new SnmpDeviceTransportConfiguration());
                    break;
            }
        }
        return deviceData;
    }

    @Override
    public void deleteDevice(TenantId tenantId, DeviceId deviceId) {
        log.trace("Executing deleteDevice [{}]", deviceId);
        validateId(deviceId, INCORRECT_DEVICE_ID + deviceId);

        Device device = deviceDao.findById(tenantId, deviceId.getId());
        try {
            List<EntityView> entityViews = entityViewService.findEntityViewsByTenantIdAndEntityIdAsync(device.getTenantId(), deviceId).get();
            if (entityViews != null && !entityViews.isEmpty()) {
                throw new DataValidationException("Can't delete device that has entity views!");
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Exception while finding entity views for deviceId [{}]", deviceId, e);
            throw new RuntimeException("Exception while finding entity views for deviceId [" + deviceId + "]", e);
        }

        DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(tenantId, deviceId);
        if (deviceCredentials != null) {
            deviceCredentialsService.deleteDeviceCredentials(tenantId, deviceCredentials);
        }
        deleteEntityRelations(tenantId, deviceId);

        removeDeviceFromCacheByName(tenantId, device.getName());

        deviceDao.removeById(tenantId, deviceId.getId());
    }

    private void removeDeviceFromCacheByName(TenantId tenantId, String name) {
        Cache cache = cacheManager.getCache(DEVICE_CACHE);
        cache.evict(Arrays.asList(tenantId, name));
    }

    @Override
    public PageData<Device> findDevicesByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findDevicesByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<Device> findDevicesByTenantIdAndType(TenantId tenantId, String type, PageLink pageLink) {
        log.trace("Executing findDevicesByTenantIdAndType, tenantId [{}], type [{}], pageLink [{}]", tenantId, type, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByTenantIdAndType(tenantId.getId(), type, pageLink);
    }

    @Override
    public ListenableFuture<List<Device>> findDevicesByTenantIdAndIdsAsync(TenantId tenantId, List<DeviceId> deviceIds) {
        log.trace("Executing findDevicesByTenantIdAndIdsAsync, tenantId [{}], deviceIds [{}]", tenantId, deviceIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateIds(deviceIds, "Incorrect deviceIds " + deviceIds);
        return deviceDao.findDevicesByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(deviceIds));
    }

    @Override
    public void deleteDevicesByTenantId(TenantId tenantId) {
        log.trace("Executing deleteDevicesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        tenantDevicesRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public PageData<Device> findDevicesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, PageLink pageLink) {
        log.trace("Executing findDevicesByTenantIdAndCustomerId, tenantId [{}], customerId [{}], pageLink [{}]", tenantId, customerId, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByTenantIdAndCustomerId(tenantId.getId(), customerId.getId(), pageLink);
    }

    @Override
    public void deleteDevicesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing deleteDevicesByTenantIdAndCustomerId, tenantId [{}], customerId [{}]", tenantId, customerId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        customerDevicesRemover.removeEntities(tenantId, customerId);
    }

    @Override
    public PageData<Device> findDevicesByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, String type, PageLink pageLink) {
        log.trace("Executing findDevicesByTenantIdAndCustomerIdAndType, tenantId [{}], customerId [{}], type [{}], pageLink [{}]", tenantId, customerId, type, pageLink);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByTenantIdAndCustomerIdAndType(tenantId.getId(), customerId.getId(), type, pageLink);
    }

    @Override
    public ListenableFuture<List<Device>> findDevicesByTenantIdCustomerIdAndIdsAsync(TenantId tenantId, CustomerId customerId, List<DeviceId> deviceIds) {
        log.trace("Executing findDevicesByTenantIdCustomerIdAndIdsAsync, tenantId [{}], customerId [{}], deviceIds [{}]", tenantId, customerId, deviceIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        validateIds(deviceIds, "Incorrect deviceIds " + deviceIds);
        return deviceDao.findDevicesByTenantIdCustomerIdAndIdsAsync(tenantId.getId(),
                customerId.getId(), toUUIDs(deviceIds));
    }

    @Override
    public ListenableFuture<List<Device>> findDevicesByQuery(TenantId tenantId, DeviceSearchQuery query) {
        ListenableFuture<List<EntityRelation>> relations = relationService.findByQuery(tenantId, query.toEntitySearchQuery());
        ListenableFuture<List<Device>> devices = Futures.transformAsync(relations, r -> {
            EntitySearchDirection direction = query.toEntitySearchQuery().getParameters().getDirection();
            List<ListenableFuture<Device>> futures = new ArrayList<>();
            for (EntityRelation relation : r) {
                EntityId entityId = direction == EntitySearchDirection.FROM ? relation.getTo() : relation.getFrom();
                if (entityId.getEntityType() == EntityType.DEVICE) {
                    futures.add(findDeviceByIdAsync(tenantId, new DeviceId(entityId.getId())));
                }
            }
            return Futures.successfulAsList(futures);
        }, MoreExecutors.directExecutor());

        devices = Futures.transform(devices, new Function<List<Device>, List<Device>>() {
            @Nullable
            @Override
            public List<Device> apply(@Nullable List<Device> deviceList) {
                return deviceList == null ? Collections.emptyList() : deviceList.stream().filter(device -> query.getDeviceTypes().contains(device.getType())).collect(Collectors.toList());
            }
        }, MoreExecutors.directExecutor());

        return devices;
    }

    @Override
    public ListenableFuture<List<EntitySubtype>> findDeviceTypesByTenantId(TenantId tenantId) {
        log.trace("Executing findDeviceTypesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        ListenableFuture<List<EntitySubtype>> tenantDeviceTypes = deviceDao.findTenantDeviceTypesAsync(tenantId.getId());
        return Futures.transform(tenantDeviceTypes,
                deviceTypes -> {
                    deviceTypes.sort(Comparator.comparing(EntitySubtype::getType));
                    return deviceTypes;
                }, MoreExecutors.directExecutor());
    }

    @Override
    public PageData<Device> findDevicesByEntityGroupId(EntityGroupId groupId, PageLink pageLink) {
        log.trace("Executing findDevicesByEntityGroupId, groupId [{}], pageLink [{}]", groupId, pageLink);
        validateId(groupId, "Incorrect entityGroupId " + groupId);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByEntityGroupId(groupId.getId(), pageLink);
    }

    @Override
    public PageData<Device> findDevicesByEntityGroupIds(List<EntityGroupId> groupIds, PageLink pageLink) {
        log.trace("Executing findDevicesByEntityGroupIds, groupIds [{}], pageLink [{}]", groupIds, pageLink);
        validateIds(groupIds, "Incorrect groupIds " + groupIds);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByEntityGroupIds(toUUIDs(groupIds), pageLink);
    }

    @Override
    public PageData<Device> findDevicesByEntityGroupIdsAndType(List<EntityGroupId> groupIds, String type, PageLink pageLink) {
        log.trace("Executing findDevicesByEntityGroupIdsAndType, groupIds [{}], type [{}], pageLink [{}]", groupIds, type, pageLink);
        validateIds(groupIds, "Incorrect groupIds " + groupIds);
        validateString(type, "Incorrect type " + type);
        validatePageLink(pageLink);
        return deviceDao.findDevicesByEntityGroupIdsAndType(toUUIDs(groupIds), type, pageLink);
    }

    @CacheEvict(cacheNames = DEVICE_CACHE, key = "{#device.tenantId, #device.name}")
    @Override
    public Device assignDeviceToTenant(TenantId tenantId, Device device) {
        log.trace("Executing assignDeviceToTenant [{}][{}]", tenantId, device);

        try {
            List<EntityView> entityViews = entityViewService.findEntityViewsByTenantIdAndEntityIdAsync(device.getTenantId(), device.getId()).get();
            if (!CollectionUtils.isEmpty(entityViews)) {
                throw new DataValidationException("Can't assign device that has entity views to another tenant!");
            }
        } catch (ExecutionException | InterruptedException e) {
            log.error("Exception while finding entity views for deviceId [{}]", device.getId(), e);
            throw new RuntimeException("Exception while finding entity views for deviceId [" + device.getId() + "]", e);
        }

        eventService.removeEvents(device.getTenantId(), device.getId());

        relationService.removeRelations(device.getTenantId(), device.getId());

        device.setTenantId(tenantId);
        device.setCustomerId(null);
        Device assignedDevice = doSaveDevice(device, null);
        entityGroupService.addEntityToEntityGroupAll(assignedDevice.getTenantId(), assignedDevice.getOwnerId(), assignedDevice.getId());
        return assignedDevice;
    }

    @Override
    @CacheEvict(cacheNames = DEVICE_CACHE, key = "{#profile.tenantId, #provisionRequest.deviceName}")
    @Transactional
    public Device saveDevice(ProvisionRequest provisionRequest, DeviceProfile profile) {
        Device device = new Device();
        device.setName(provisionRequest.getDeviceName());
        device.setType(profile.getName());
        device.setTenantId(profile.getTenantId());
        Device savedDevice = saveDevice(device);
        if (!StringUtils.isEmpty(provisionRequest.getCredentialsData().getToken()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getX509CertHash()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getUsername()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getPassword()) ||
                !StringUtils.isEmpty(provisionRequest.getCredentialsData().getClientId())) {
            DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(savedDevice.getTenantId(), savedDevice.getId());
            if (deviceCredentials == null) {
                deviceCredentials = new DeviceCredentials();
            }
            deviceCredentials.setDeviceId(savedDevice.getId());
            deviceCredentials.setCredentialsType(provisionRequest.getCredentialsType());
            switch (provisionRequest.getCredentialsType()) {
                case ACCESS_TOKEN:
                    deviceCredentials.setCredentialsId(provisionRequest.getCredentialsData().getToken());
                    break;
                case MQTT_BASIC:
                    BasicMqttCredentials mqttCredentials = new BasicMqttCredentials();
                    mqttCredentials.setClientId(provisionRequest.getCredentialsData().getClientId());
                    mqttCredentials.setUserName(provisionRequest.getCredentialsData().getUsername());
                    mqttCredentials.setPassword(provisionRequest.getCredentialsData().getPassword());
                    deviceCredentials.setCredentialsValue(JacksonUtil.toString(mqttCredentials));
                    break;
                case X509_CERTIFICATE:
                    deviceCredentials.setCredentialsValue(provisionRequest.getCredentialsData().getX509CertHash());
                    break;
                case LWM2M_CREDENTIALS:
                    break;
            }
            try {
                deviceCredentialsService.updateDeviceCredentials(savedDevice.getTenantId(), deviceCredentials);
            } catch (Exception e) {
                throw new ProvisionFailedException(ProvisionResponseStatus.FAILURE.name());
            }
        }
        return savedDevice;
    }

    @Override
    public PageData<UUID> findDevicesIdsByDeviceProfileTransportType(DeviceTransportType transportType, PageLink pageLink) {
        return deviceDao.findDevicesIdsByDeviceProfileTransportType(transportType, pageLink);
    }

    @Override
    public long countByTenantId(TenantId tenantId) {
        return deviceDao.countByTenantId(tenantId);
    }

    private DataValidator<Device> deviceValidator =
            new DataValidator<Device>() {

                @Override
                protected void validateCreate(TenantId tenantId, Device device) {
                    DefaultTenantProfileConfiguration profileConfiguration =
                            (DefaultTenantProfileConfiguration) tenantProfileCache.get(tenantId).getProfileData().getConfiguration();
                    long maxDevices = profileConfiguration.getMaxDevices();
                    validateNumberOfEntitiesPerTenant(tenantId, deviceDao, maxDevices, EntityType.DEVICE);
                }

                @Override
                protected void validateUpdate(TenantId tenantId, Device device) {
                    Device old = deviceDao.findById(device.getTenantId(), device.getId().getId());
                    if (old == null) {
                        throw new DataValidationException("Can't update non existing device!");
                    }
                    if (!old.getName().equals(device.getName())) {
                        removeDeviceFromCacheByName(tenantId, old.getName());
                    }
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, Device device) {
                    if (StringUtils.isEmpty(device.getName()) || device.getName().trim().length() == 0) {
                        throw new DataValidationException("Device name should be specified!");
                    }
                    if (device.getTenantId() == null) {
                        throw new DataValidationException("Device should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(device.getTenantId(), device.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Device is referencing to non-existent tenant!");
                        }
                    }
                    if (device.getCustomerId() == null) {
                        device.setCustomerId(new CustomerId(NULL_UUID));
                    } else if (!device.getCustomerId().getId().equals(NULL_UUID)) {
                        Customer customer = customerDao.findById(device.getTenantId(), device.getCustomerId().getId());
                        if (customer == null) {
                            throw new DataValidationException("Can't assign device to non-existent customer!");
                        }
                        if (!customer.getTenantId().getId().equals(device.getTenantId().getId())) {
                            throw new DataValidationException("Can't assign device to customer from different tenant!");
                        }
                    }
                    Optional.ofNullable(device.getDeviceData())
                            .flatMap(deviceData -> Optional.ofNullable(deviceData.getTransportConfiguration()))
                            .ifPresent(DeviceTransportConfiguration::validate);

                    if (device.getFirmwareId() != null) {
                        OtaPackage firmware = otaPackageService.findOtaPackageById(tenantId, device.getFirmwareId());
                        if (firmware == null) {
                            throw new DataValidationException("Can't assign non-existent firmware!");
                        }
                        if (!firmware.getType().equals(OtaPackageType.FIRMWARE)) {
                            throw new DataValidationException("Can't assign firmware with type: " + firmware.getType());
                        }
                        if (firmware.getData() == null && !firmware.hasUrl()) {
                            throw new DataValidationException("Can't assign firmware with empty data!");
                        }
                        if (!firmware.getDeviceProfileId().equals(device.getDeviceProfileId())) {
                            throw new DataValidationException("Can't assign firmware with different deviceProfile!");
                        }
                    }

                    if (device.getSoftwareId() != null) {
                        OtaPackage software = otaPackageService.findOtaPackageById(tenantId, device.getSoftwareId());
                        if (software == null) {
                            throw new DataValidationException("Can't assign non-existent software!");
                        }
                        if (!software.getType().equals(OtaPackageType.SOFTWARE)) {
                            throw new DataValidationException("Can't assign software with type: " + software.getType());
                        }
                        if (software.getData() == null) {
                            throw new DataValidationException("Can't assign software with empty data!");
                        }
                        if (!software.getDeviceProfileId().equals(device.getDeviceProfileId())) {
                            throw new DataValidationException("Can't assign firmware with different deviceProfile!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, Device> tenantDevicesRemover =
            new PaginatedRemover<TenantId, Device>() {

                @Override
                protected PageData<Device> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
                    return deviceDao.findDevicesByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Device entity) {
                    deleteDevice(tenantId, new DeviceId(entity.getUuidId()));
                }
            };

    private PaginatedRemover<CustomerId, Device> customerDevicesRemover = new PaginatedRemover<CustomerId, Device>() {

        @Override
        protected PageData<Device> findEntities(TenantId tenantId, CustomerId id, PageLink pageLink) {
            return deviceDao.findDevicesByTenantIdAndCustomerId(tenantId.getId(), id.getId(), pageLink);
        }

        @Override
        protected void removeEntity(TenantId tenantId, Device entity) {
            deleteDevice(tenantId, new DeviceId(entity.getUuidId()));
        }
    };

    @Override
    public PageData<Device> findByEntityGroupAndDeviceProfileAndEmptyOtaPackage(EntityGroupId groupId,
                                                                                DeviceProfileId deviceProfileId,
                                                                                OtaPackageType type,
                                                                                PageLink pageLink) {
        log.trace("Executing findByEntityGroupIdAndDeviceProfileAndFirmwareIsNull, groupId [{}], deviceProfileId [{}], " +
                " firmwareType [{}]", groupId, deviceProfileId, type);
        validateId(groupId, "Incorrect groupId" + groupId);
        validateId(deviceProfileId, "Incorrect deviceProfileId" + deviceProfileId);
        validatePageLink(pageLink);
        return deviceDao.findByEntityGroupAndDeviceProfileAndEmptyOtaPackage(groupId.getId(), deviceProfileId.getId(), type, pageLink);
    }

    @Override
    public PageData<Device> findByDeviceProfileAndEmptyOtaPackage(TenantId tenantId, DeviceProfileId deviceProfileId,
                                                                  OtaPackageType type,
                                                                  PageLink pageLink) {
        log.trace("Executing findByDeviceProfileAndEmptyFirmware,  deviceProfileId [{}], firmwareType [{}]",
                deviceProfileId, type);
        validateId(deviceProfileId, "Incorrect deviceProfileId" + deviceProfileId);
        validatePageLink(pageLink);
        return deviceDao.findByDeviceProfileAndEmptyOtaPackage(tenantId.getId(), deviceProfileId.getId(), type, pageLink);
    }

    @Override
    public Long countByEntityGroupAndEmptyOtaPackage(EntityGroupId groupId, OtaPackageId otaPackageId, OtaPackageType type) {
        log.trace("Executing countByEntityGroupAndDeviceProfileAndEmptyOtaPackage, groupId [{}], otaPackageId [{}], firmwareType [{}]", groupId, otaPackageId, type);
        validateId(groupId, "Incorrect groupId" + groupId);
        validateId(otaPackageId, "Incorrect deviceProfileId" + otaPackageId);

        return deviceDao.countByEntityGroupAndEmptyOtaPackage(groupId.getId(), otaPackageId.getId(), type);
    }

    @Override
    public Long countByDeviceProfileAndEmptyOtaPackage(TenantId tenantId, DeviceProfileId deviceProfileId, OtaPackageType type) {
        log.trace("Executing countByDeviceProfileAndEmptyOtaPackage,  deviceProfileId [{}], firmwareType [{}]", deviceProfileId, type);
        validateId(deviceProfileId, "Incorrect deviceProfileId" + deviceProfileId);

        return deviceDao.countByDeviceProfileAndEmptyOtaPackage(tenantId.getId(), deviceProfileId.getId(), type);
    }
}
