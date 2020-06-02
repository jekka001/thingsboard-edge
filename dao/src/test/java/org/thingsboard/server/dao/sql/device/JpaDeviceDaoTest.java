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
package org.thingsboard.server.dao.sql.device;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.AbstractJpaDaoTest;
import org.thingsboard.server.dao.device.DeviceDao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by Valerii Sosliuk on 5/6/2017.
 */
public class JpaDeviceDaoTest extends AbstractJpaDaoTest {

    @Autowired
    private DeviceDao deviceDao;

    @Test
    public void testFindDevicesByTenantId() {
        UUID tenantId1 = Uuids.timeBased();
        UUID tenantId2 = Uuids.timeBased();
        UUID customerId1 = Uuids.timeBased();
        UUID customerId2 = Uuids.timeBased();
        createDevices(tenantId1, tenantId2, customerId1, customerId2, 40);

        PageLink pageLink = new PageLink(15, 0,  "SEARCH_TEXT");
        PageData<Device> devices1 = deviceDao.findDevicesByTenantId(tenantId1, pageLink);
        assertEquals(15, devices1.getData().size());

        pageLink = pageLink.nextPageLink();

        PageData<Device> devices2 = deviceDao.findDevicesByTenantId(tenantId1, pageLink);
        assertEquals(5, devices2.getData().size());
    }

    @Test
    public void testFindAsync() throws ExecutionException, InterruptedException {
        UUID tenantId = Uuids.timeBased();
        UUID customerId = Uuids.timeBased();
        Device device = getDevice(tenantId, customerId);
        deviceDao.save(new TenantId(tenantId), device);

        UUID uuid = device.getId().getId();
        Device entity = deviceDao.findById(new TenantId(tenantId), uuid);
        assertNotNull(entity);
        assertEquals(uuid, entity.getId().getId());

        ListeningExecutorService service = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(10));
        ListenableFuture<Device> future = service.submit(() -> deviceDao.findById(new TenantId(tenantId), uuid));
        Device asyncDevice = future.get();
        assertNotNull("Async device expected to be not null", asyncDevice);
    }

    @Test
    public void testFindDevicesByTenantIdAndIdsAsync() throws ExecutionException, InterruptedException {
        UUID tenantId1 = Uuids.timeBased();
        UUID customerId1 = Uuids.timeBased();
        UUID tenantId2 = Uuids.timeBased();
        UUID customerId2 = Uuids.timeBased();

        List<UUID> deviceIds = new ArrayList<>();

        for(int i = 0; i < 5; i++) {
            UUID deviceId1 = Uuids.timeBased();
            UUID deviceId2 = Uuids.timeBased();
            deviceDao.save(new TenantId(tenantId1), getDevice(tenantId1, customerId1, deviceId1));
            deviceDao.save(new TenantId(tenantId2), getDevice(tenantId2, customerId2, deviceId2));
            deviceIds.add(deviceId1);
            deviceIds.add(deviceId2);
        }

        ListenableFuture<List<Device>> devicesFuture = deviceDao.findDevicesByTenantIdAndIdsAsync(tenantId1, deviceIds);
        List<Device> devices = devicesFuture.get();
        assertEquals(5, devices.size());
    }

    @Test
    public void testFindDevicesByTenantIdAndCustomerIdAndIdsAsync() throws ExecutionException, InterruptedException {
        UUID tenantId1 = Uuids.timeBased();
        UUID customerId1 = Uuids.timeBased();
        UUID tenantId2 = Uuids.timeBased();
        UUID customerId2 = Uuids.timeBased();

        List<UUID> deviceIds = new ArrayList<>();

        for(int i = 0; i < 20; i++) {
            UUID deviceId1 = Uuids.timeBased();
            UUID deviceId2 = Uuids.timeBased();
            deviceDao.save(new TenantId(tenantId1), getDevice(tenantId1, customerId1, deviceId1));
            deviceDao.save(new TenantId(tenantId2), getDevice(tenantId2, customerId2, deviceId2));
            deviceIds.add(deviceId1);
            deviceIds.add(deviceId2);
        }

        ListenableFuture<List<Device>> devicesFuture = deviceDao.findDevicesByTenantIdCustomerIdAndIdsAsync(tenantId1, customerId1, deviceIds);
        List<Device> devices = devicesFuture.get();
        assertEquals(20, devices.size());
    }

    private void createDevices(UUID tenantId1, UUID tenantId2, UUID customerId1, UUID customerId2, int count) {
        for (int i = 0; i < count / 2; i++) {
            deviceDao.save(new TenantId(tenantId1), getDevice(tenantId1, customerId1));
            deviceDao.save(new TenantId(tenantId2), getDevice(tenantId2, customerId2));
        }
    }

    private Device getDevice(UUID tenantId, UUID customerID) {
        return getDevice(tenantId, customerID, Uuids.timeBased());
    }

    private Device getDevice(UUID tenantId, UUID customerID, UUID deviceId) {
        Device device = new Device();
        device.setId(new DeviceId(deviceId));
        device.setTenantId(new TenantId(tenantId));
        device.setCustomerId(new CustomerId(customerID));
        device.setName("SEARCH_TEXT");
        return device;
    }
}
