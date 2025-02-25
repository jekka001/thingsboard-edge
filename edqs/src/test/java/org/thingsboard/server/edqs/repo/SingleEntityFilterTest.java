/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.edqs.repo;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edqs.LatestTsKv;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.permission.MergedGroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.SingleEntityFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.edqs.util.RepositoryUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SingleEntityFilterTest extends AbstractEDQTest {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFindTenantDevice() {
        DeviceId deviceId = new DeviceId(UUID.randomUUID());
        Device device = new Device();
        device.setId(deviceId);
        device.setTenantId(tenantId);
        device.setName("LoRa-1");
        device.setCreatedTime(42L);
        device.setDeviceProfileId(new DeviceProfileId(defaultDeviceProfileId));
        addOrUpdate(EntityType.DEVICE, device);
        addOrUpdate(new LatestTsKv(deviceId, new BasicTsKvEntry(43, new StringDataEntry("state", "TEST")), 0L));

        var result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityDataQuery(device.getId()), false);

        Assert.assertEquals(1, result.getTotalElements());
        var first = result.getData().get(0);
        Assert.assertEquals(deviceId, first.getEntityId());
        Assert.assertEquals("LoRa-1", first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
        Assert.assertEquals("42", first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue());

        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityDataQuery(new DeviceId(UUID.randomUUID())), false);
        Assert.assertEquals(0, result.getTotalElements());

        device.setCustomerId(customerId);
        addOrUpdate(EntityType.DEVICE, device);

        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityDataQuery(device.getId()), false);
        Assert.assertEquals(1, result.getTotalElements());
        first = result.getData().get(0);
        Assert.assertEquals(deviceId, first.getEntityId());
        Assert.assertEquals("LoRa-1", first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
        Assert.assertEquals("42", first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue());
    }

    @Test
    public void testFindTenantDeviceWithGenericAndGroupPermission() {
        UUID deviceId = createDevice(customerId, "LoRa-customer-1");
        UUID deviceId2 = createDevice(customerId, "LoRa-customer-2");
        UUID deviceId3 = createDevice(customerId, "LoRa-customer-3");

        // add device and device 2 to Group A
        UUID groupAId = createGroup(customerId.getId(), EntityType.DEVICE, "Group A");
        createRelation(EntityType.ENTITY_GROUP, groupAId, EntityType.DEVICE, deviceId, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");
        createRelation(EntityType.ENTITY_GROUP, groupAId, EntityType.DEVICE, deviceId2, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        // add device and device 2 to Group A
        UUID groupBId = createGroup(customerId.getId(), EntityType.DEVICE, "Group B");
        createRelation(EntityType.ENTITY_GROUP, groupAId, EntityType.DEVICE, deviceId3, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        MergedUserPermissions genericAndGroupAPermission = new MergedUserPermissions(
                Map.of(Resource.ALL, Set.of(Operation.ALL)), Map.of(new EntityGroupId(groupAId), new MergedGroupPermissionInfo(EntityType.DEVICE, Set.of(Operation.ALL))));
        var result = repository.findEntityDataByQuery(tenantId, null, genericAndGroupAPermission, getEntityDataQuery(new DeviceId(deviceId2)), false);
        Assert.assertEquals(1, result.getTotalElements());
        QueryResult queryResult = result.getData().get(0);
        Assert.assertEquals(deviceId2, queryResult.getEntityId().getId());
        Assert.assertEquals("LoRa-customer-2", queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());

        // find device without permission
        MergedUserPermissions genericAndGroupBPermission = new MergedUserPermissions(
                Map.of(Resource.ALL, Set.of(Operation.ALL)), Map.of(new EntityGroupId(groupAId), new MergedGroupPermissionInfo(EntityType.DEVICE, Set.of(Operation.ALL))));
        result = repository.findEntityDataByQuery(tenantId, null, genericAndGroupBPermission, getEntityDataQuery(new DeviceId(deviceId3)), false);
        Assert.assertEquals(1, result.getTotalElements());
        queryResult = result.getData().get(0);
        Assert.assertEquals(deviceId3, queryResult.getEntityId().getId());
        Assert.assertEquals("LoRa-customer-3", queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
    }

    @Test
    public void testFindCustomerDevice() {
        DeviceId deviceId = new DeviceId(UUID.randomUUID());
        Device device = new Device();
        device.setId(deviceId);
        device.setTenantId(tenantId);
        device.setName("LoRa-1");
        device.setCreatedTime(42L);
        device.setDeviceProfileId(new DeviceProfileId(defaultDeviceProfileId));
        addOrUpdate(EntityType.DEVICE, device);
        addOrUpdate(new LatestTsKv(deviceId, new BasicTsKvEntry(43, new StringDataEntry("state", "TEST")), 0L));

        var result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityDataQuery(device.getId()), false);
        Assert.assertEquals(0, result.getTotalElements());

        device.setCustomerId(customerId);
        addOrUpdate(EntityType.DEVICE, device);

        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityDataQuery(device.getId()), false);

        Assert.assertEquals(1, result.getTotalElements());
        var first = result.getData().get(0);
        Assert.assertEquals(deviceId, first.getEntityId());
        Assert.assertEquals("LoRa-1", first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
        Assert.assertEquals("42", first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue());
    }

    private static EntityDataQuery getEntityDataQuery(DeviceId deviceId) {
        SingleEntityFilter filter = new SingleEntityFilter();
        filter.setSingleEntity(deviceId);
        var pageLink = new EntityDataPageLink(20, 0, null, new EntityDataSortOrder(new EntityKey(EntityKeyType.TIME_SERIES, "state"), EntityDataSortOrder.Direction.DESC), false);

        var entityFields = Arrays.asList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"));
        var latestValues = Arrays.asList(new EntityKey(EntityKeyType.TIME_SERIES, "state"));
        KeyFilter nameFilter = new KeyFilter();
        nameFilter.setKey(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        var predicate = new StringFilterPredicate();
        predicate.setIgnoreCase(false);
        predicate.setOperation(StringFilterPredicate.StringOperation.CONTAINS);
        predicate.setValue(new FilterPredicateValue<>("LoRa-"));
        nameFilter.setPredicate(predicate);
        nameFilter.setValueType(EntityKeyValueType.STRING);

        return new EntityDataQuery(filter, pageLink, entityFields, latestValues, Arrays.asList(nameFilter));
    }

}
