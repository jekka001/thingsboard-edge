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
package org.thingsboard.server.dao.service;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileInfo;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.exception.DataValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BaseDeviceProfileServiceTest extends AbstractServiceTest {

    private IdComparator<DeviceProfile> idComparator = new IdComparator<>();
    private IdComparator<DeviceProfileInfo> deviceProfileInfoIdComparator = new IdComparator<>();

    private TenantId tenantId;

    @Before
    public void before() {
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        Tenant savedTenant = tenantService.saveTenant(tenant);
        Assert.assertNotNull(savedTenant);
        tenantId = savedTenant.getId();
    }

    @After
    public void after() {
        tenantService.deleteTenant(tenantId);
    }

    @Test
    public void testSaveDeviceProfile() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId, "Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        Assert.assertNotNull(savedDeviceProfile);
        Assert.assertNotNull(savedDeviceProfile.getId());
        Assert.assertTrue(savedDeviceProfile.getCreatedTime() > 0);
        Assert.assertEquals(deviceProfile.getName(), savedDeviceProfile.getName());
        Assert.assertEquals(deviceProfile.getDescription(), savedDeviceProfile.getDescription());
        Assert.assertEquals(deviceProfile.getProfileData(), savedDeviceProfile.getProfileData());
        Assert.assertEquals(deviceProfile.isDefault(), savedDeviceProfile.isDefault());
        Assert.assertEquals(deviceProfile.getDefaultRuleChainId(), savedDeviceProfile.getDefaultRuleChainId());
        savedDeviceProfile.setName("New device profile");
        deviceProfileService.saveDeviceProfile(savedDeviceProfile);
        DeviceProfile foundDeviceProfile = deviceProfileService.findDeviceProfileById(tenantId, savedDeviceProfile.getId());
        Assert.assertEquals(savedDeviceProfile.getName(), foundDeviceProfile.getName());
    }

    @Test
    public void testFindDeviceProfileById() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        DeviceProfile foundDeviceProfile = deviceProfileService.findDeviceProfileById(tenantId, savedDeviceProfile.getId());
        Assert.assertNotNull(foundDeviceProfile);
        Assert.assertEquals(savedDeviceProfile, foundDeviceProfile);
   }

    @Test
    public void testFindDeviceProfileInfoById() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        DeviceProfileInfo foundDeviceProfileInfo = deviceProfileService.findDeviceProfileInfoById(tenantId, savedDeviceProfile.getId());
        Assert.assertNotNull(foundDeviceProfileInfo);
        Assert.assertEquals(savedDeviceProfile.getId(), foundDeviceProfileInfo.getId());
        Assert.assertEquals(savedDeviceProfile.getName(), foundDeviceProfileInfo.getName());
        Assert.assertEquals(savedDeviceProfile.getType(), foundDeviceProfileInfo.getType());
    }

    @Test
    public void testFindDefaultDeviceProfile() {
        DeviceProfile foundDefaultDeviceProfile = deviceProfileService.findDefaultDeviceProfile(tenantId);
        Assert.assertNotNull(foundDefaultDeviceProfile);
        Assert.assertNotNull(foundDefaultDeviceProfile.getId());
        Assert.assertNotNull(foundDefaultDeviceProfile.getName());
    }

    @Test
    public void testFindDefaultDeviceProfileInfo() {
        DeviceProfileInfo foundDefaultDeviceProfileInfo = deviceProfileService.findDefaultDeviceProfileInfo(tenantId);
        Assert.assertNotNull(foundDefaultDeviceProfileInfo);
        Assert.assertNotNull(foundDefaultDeviceProfileInfo.getId());
        Assert.assertNotNull(foundDefaultDeviceProfileInfo.getName());
        Assert.assertNotNull(foundDefaultDeviceProfileInfo.getType());
    }

    @Test
    public void testFindOrCreateDeviceProfile() throws ExecutionException, InterruptedException {
        ListeningExecutorService testExecutor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(100));
        try {
            List<ListenableFuture<DeviceProfile>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i ++) {
                futures.add(testExecutor.submit(() -> deviceProfileService.findOrCreateDeviceProfile(tenantId, "Device Profile 1")));
                futures.add(testExecutor.submit(() -> deviceProfileService.findOrCreateDeviceProfile(tenantId, "Device Profile 2")));
            }

            List<DeviceProfile> deviceProfiles = Futures.allAsList(futures).get();
            deviceProfiles.forEach(Assert::assertNotNull);
        } finally {
            testExecutor.shutdownNow();
        }
    }

    @Test
    public void testSetDefaultDeviceProfile() {
        DeviceProfile deviceProfile1 = this.createDeviceProfile(tenantId,"Device Profile 1");
        DeviceProfile deviceProfile2 = this.createDeviceProfile(tenantId,"Device Profile 2");

        DeviceProfile savedDeviceProfile1 = deviceProfileService.saveDeviceProfile(deviceProfile1);
        DeviceProfile savedDeviceProfile2 = deviceProfileService.saveDeviceProfile(deviceProfile2);

        boolean result = deviceProfileService.setDefaultDeviceProfile(tenantId, savedDeviceProfile1.getId());
        Assert.assertTrue(result);
        DeviceProfile defaultDeviceProfile = deviceProfileService.findDefaultDeviceProfile(tenantId);
        Assert.assertNotNull(defaultDeviceProfile);
        Assert.assertEquals(savedDeviceProfile1.getId(), defaultDeviceProfile.getId());
        result = deviceProfileService.setDefaultDeviceProfile(tenantId, savedDeviceProfile2.getId());
        Assert.assertTrue(result);
        defaultDeviceProfile = deviceProfileService.findDefaultDeviceProfile(tenantId);
        Assert.assertNotNull(defaultDeviceProfile);
        Assert.assertEquals(savedDeviceProfile2.getId(), defaultDeviceProfile.getId());
    }

    @Test(expected = DataValidationException.class)
    public void testSaveDeviceProfileWithEmptyName() {
        DeviceProfile deviceProfile = new DeviceProfile();
        deviceProfile.setTenantId(tenantId);
        deviceProfileService.saveDeviceProfile(deviceProfile);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveDeviceProfileWithSameName() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        deviceProfileService.saveDeviceProfile(deviceProfile);
        DeviceProfile deviceProfile2 = this.createDeviceProfile(tenantId,"Device Profile");
        deviceProfileService.saveDeviceProfile(deviceProfile2);
    }

    @Ignore
    @Test(expected = DataValidationException.class)
    public void testChangeDeviceProfileTypeWithExistingDevices() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        Device device = new Device();
        device.setTenantId(tenantId);
        device.setName("Test device");
        device.setType("default");
        device.setDeviceProfileId(savedDeviceProfile.getId());
        deviceService.saveDevice(device);
        //TODO: once we have more profile types, we should test that we can not change profile type in runtime and uncomment the @Ignore.
//        savedDeviceProfile.setType(DeviceProfileType.LWM2M);
        deviceProfileService.saveDeviceProfile(savedDeviceProfile);
    }

    @Test(expected = DataValidationException.class)
    public void testChangeDeviceProfileTransportTypeWithExistingDevices() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        Device device = new Device();
        device.setTenantId(tenantId);
        device.setName("Test device");
        device.setType("default");
        device.setDeviceProfileId(savedDeviceProfile.getId());
        deviceService.saveDevice(device);
        savedDeviceProfile.setTransportType(DeviceTransportType.MQTT);
        deviceProfileService.saveDeviceProfile(savedDeviceProfile);
    }

    @Test(expected = DataValidationException.class)
    public void testDeleteDeviceProfileWithExistingDevice() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        Device device = new Device();
        device.setTenantId(tenantId);
        device.setName("Test device");
        device.setType("default");
        device.setDeviceProfileId(savedDeviceProfile.getId());
        deviceService.saveDevice(device);
        deviceProfileService.deleteDeviceProfile(tenantId, savedDeviceProfile.getId());
    }

    @Test
    public void testDeleteDeviceProfile() {
        DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile");
        DeviceProfile savedDeviceProfile = deviceProfileService.saveDeviceProfile(deviceProfile);
        deviceProfileService.deleteDeviceProfile(tenantId, savedDeviceProfile.getId());
        DeviceProfile foundDeviceProfile = deviceProfileService.findDeviceProfileById(tenantId, savedDeviceProfile.getId());
        Assert.assertNull(foundDeviceProfile);
    }

    @Test
    public void testFindDeviceProfiles() {

        List<DeviceProfile> deviceProfiles = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<DeviceProfile> pageData = deviceProfileService.findDeviceProfiles(tenantId, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getTotalElements());
        deviceProfiles.addAll(pageData.getData());

        for (int i=0;i<28;i++) {
            DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile"+i);
            deviceProfiles.add(deviceProfileService.saveDeviceProfile(deviceProfile));
        }

        List<DeviceProfile> loadedDeviceProfiles = new ArrayList<>();
        pageLink = new PageLink(17);
        do {
            pageData = deviceProfileService.findDeviceProfiles(tenantId, pageLink);
            loadedDeviceProfiles.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(deviceProfiles, idComparator);
        Collections.sort(loadedDeviceProfiles, idComparator);

        Assert.assertEquals(deviceProfiles, loadedDeviceProfiles);

        for (DeviceProfile deviceProfile : loadedDeviceProfiles) {
            if (!deviceProfile.isDefault()) {
                deviceProfileService.deleteDeviceProfile(tenantId, deviceProfile.getId());
            }
        }

        pageLink = new PageLink(17);
        pageData = deviceProfileService.findDeviceProfiles(tenantId, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getTotalElements());
    }

    @Test
    public void testFindDeviceProfileInfos() {

        List<DeviceProfile> deviceProfiles = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<DeviceProfile> deviceProfilePageData = deviceProfileService.findDeviceProfiles(tenantId, pageLink);
        Assert.assertFalse(deviceProfilePageData.hasNext());
        Assert.assertEquals(1, deviceProfilePageData.getTotalElements());
        deviceProfiles.addAll(deviceProfilePageData.getData());

        for (int i=0;i<28;i++) {
            DeviceProfile deviceProfile = this.createDeviceProfile(tenantId,"Device Profile"+i);
            deviceProfiles.add(deviceProfileService.saveDeviceProfile(deviceProfile));
        }

        List<DeviceProfileInfo> loadedDeviceProfileInfos = new ArrayList<>();
        pageLink = new PageLink(17);
        PageData<DeviceProfileInfo> pageData;
        do {
            pageData = deviceProfileService.findDeviceProfileInfos(tenantId, pageLink, null);
            loadedDeviceProfileInfos.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());


        Collections.sort(deviceProfiles, idComparator);
        Collections.sort(loadedDeviceProfileInfos, deviceProfileInfoIdComparator);

        List<DeviceProfileInfo> deviceProfileInfos = deviceProfiles.stream()
                .map(deviceProfile -> new DeviceProfileInfo(deviceProfile.getId(),
                deviceProfile.getName(), deviceProfile.getType(), deviceProfile.getTransportType())).collect(Collectors.toList());

        Assert.assertEquals(deviceProfileInfos, loadedDeviceProfileInfos);

        for (DeviceProfile deviceProfile : deviceProfiles) {
            if (!deviceProfile.isDefault()) {
                deviceProfileService.deleteDeviceProfile(tenantId, deviceProfile.getId());
            }
        }

        pageLink = new PageLink(17);
        pageData = deviceProfileService.findDeviceProfileInfos(tenantId, pageLink, null);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getTotalElements());
    }
}
