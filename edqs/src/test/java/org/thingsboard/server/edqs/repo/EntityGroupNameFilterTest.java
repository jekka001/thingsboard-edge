/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.permission.MergedGroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityGroupNameFilter;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.edqs.util.RepositoryUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityGroupNameFilterTest extends AbstractEDQTest {

    private EntityGroup deviceGroup;
    private EntityGroup deviceGroup2;
    private EntityGroup dashboardGroup;

    @Before
    public void setUp() {
        deviceGroup = buildEntityGroup(EntityType.DEVICE, "thermostats");
        deviceGroup2 = buildEntityGroup(EntityType.DEVICE, "thermostats 2");
        dashboardGroup = buildEntityGroup(EntityType.DASHBOARD, "device dashboards");
        addOrUpdate(EntityType.ENTITY_GROUP, deviceGroup);
        addOrUpdate(EntityType.ENTITY_GROUP, deviceGroup2);
        addOrUpdate(EntityType.ENTITY_GROUP, dashboardGroup);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFindTenantEntityGroups() {
        // get entity list
        var result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityGroupNameDataQuery(EntityType.DEVICE, "thermo", null), false);

        Assert.assertEquals(2, result.getTotalElements());
        Optional<QueryResult> group = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals(deviceGroup.getName())).findFirst();
        assertThat(group).isPresent();
        var first = group.get();
        Assert.assertEquals(deviceGroup.getId(), first.getEntityId());
        Assert.assertEquals(deviceGroup.getName(), first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
        Assert.assertEquals(String.valueOf(deviceGroup.getCreatedTime()), first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue());

        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityGroupNameDataQuery(EntityType.DEVICE, "thermostats 2", null), false);
        Assert.assertEquals(1, result.getTotalElements());

        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityGroupNameDataQuery(EntityType.DEVICE, "humidity", null), false);
        Assert.assertEquals(0, result.getTotalElements());

        //add name filter
        KeyFilter nameFilter = getNameKeyFilter(StringFilterPredicate.StringOperation.CONTAINS,  "humidity");
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityGroupNameDataQuery(EntityType.DEVICE, "thermo", List.of(nameFilter)),  false);
        Assert.assertEquals(0, result.getTotalElements());
    }

    @Test
    public void testFindCustomerEntityGroups() {
        var result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityGroupNameDataQuery(EntityType.DEVICE, "thermo", null), false);
        Assert.assertEquals(0, result.getTotalElements());

        deviceGroup.setOwnerId(customerId);
        addOrUpdate(EntityType.ENTITY_GROUP, deviceGroup);

        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityGroupNameDataQuery(EntityType.DEVICE, "thermo", null), false);

        Assert.assertEquals(1, result.getTotalElements());
        var first = result.getData().get(0);
        Assert.assertEquals(deviceGroup.getId(), first.getEntityId());
        Assert.assertEquals(deviceGroup.getName(), first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
        Assert.assertEquals(String.valueOf(deviceGroup.getCreatedTime()), first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue());
    }

    @Test
    public void testFindCustomerDeviceGroupWithGroupPermission() {
        CustomerId subCustomer = new CustomerId(UUID.randomUUID());
        createCustomer(subCustomer.getId(), customerId.getId(), "Sub Customer A");

        EntityGroup deviceGroup3 = buildEntityGroup(EntityType.DEVICE, "sensors A");
        deviceGroup3.setOwnerId(subCustomer);
        addOrUpdate(EntityType.ENTITY_GROUP, deviceGroup3);

        MergedUserPermissions groupPermission = new MergedUserPermissions(
                Collections.emptyMap(), Map.of(deviceGroup3.getId(), new MergedGroupPermissionInfo(EntityType.DEVICE, Set.of(Operation.ALL))));
        var result = repository.findEntityDataByQuery(tenantId, customerId, groupPermission, getEntityGroupNameDataQuery(EntityType.DEVICE,"sensors", null), false);

        Assert.assertEquals(1, result.getTotalElements());
        var first = result.getData().get(0);
        Assert.assertEquals(deviceGroup3.getId(), first.getEntityId());
        Assert.assertEquals(deviceGroup3.getName(), first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
        Assert.assertEquals(String.valueOf(deviceGroup3.getCreatedTime()), first.getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue());
    }

    @Test
    public void testFindGroupWithGenericAndGroupPermission() {
        CustomerId subCustomer = new CustomerId(UUID.randomUUID());
        createCustomer(subCustomer.getId(), customerId.getId(), "Sub Customer A");

        UUID customerGroupId = createGroup(customerId.getId(), EntityType.DEVICE, "sensors A");
        UUID subCustomerGroupId = createGroup(subCustomer.getId(), EntityType.DEVICE, "sensors A");

        MergedUserPermissions groupPermission = new MergedUserPermissions(
                Map.of(Resource.ALL, Set.of(Operation.ALL)), Map.of(new EntityGroupId(customerGroupId), new MergedGroupPermissionInfo(EntityType.DEVICE, Set.of(Operation.ALL))));
        var result = repository.findEntityDataByQuery(tenantId, subCustomer, groupPermission,
                getEntityGroupNameDataQuery(EntityType.DEVICE, "sensors A", null), false);

        Assert.assertEquals(2, result.getTotalElements());
        Assert.assertTrue(checkContains(result, customerGroupId));
        Assert.assertTrue(checkContains(result, subCustomerGroupId));
    }

    private EntityGroup buildEntityGroup(EntityType entityType, String name) {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setId(new EntityGroupId(UUID.randomUUID()));
        entityGroup.setTenantId(tenantId);
        entityGroup.setOwnerId(tenantId);
        entityGroup.setName(name);
        entityGroup.setType(entityType);
        entityGroup.setCreatedTime(42L);
        return entityGroup;
    }

    private static EntityDataQuery getEntityGroupNameDataQuery(EntityType entityType, String groupName, List<KeyFilter> keyFilters) {
        EntityGroupNameFilter filter = new EntityGroupNameFilter();
        filter.setGroupType(entityType);
        filter.setEntityGroupNameFilter(groupName);
        var pageLink = new EntityDataPageLink(20, 0, null, new EntityDataSortOrder(new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.DESC), false);

        var entityFields = Arrays.asList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"));
        return new EntityDataQuery(filter, pageLink, entityFields, null, keyFilters);
    }

    private static KeyFilter getNameKeyFilter(StringFilterPredicate.StringOperation operation, String value) {
        KeyFilter nameFilter = new KeyFilter();
        nameFilter.setKey(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        var predicate = new StringFilterPredicate();
        predicate.setIgnoreCase(false);
        predicate.setOperation(operation);
        predicate.setValue(new FilterPredicateValue<>(value));
        nameFilter.setPredicate(predicate);
        nameFilter.setValueType(EntityKeyValueType.STRING);
        return nameFilter;
    }

}
