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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.MergedGroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityViewSearchQueryFilter;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.edqs.util.RepositoryUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EntityViewSearchQueryFilterTest extends AbstractEDQTest {

    @Before
    public void setUp() {
    }

    @Test
    public void testFindTenantEntityViews() {
        UUID asset1 = createAsset( "A1");
        UUID device1 = createDevice("D1");
        UUID device2 = createDevice("D2");
        UUID deviceView1 = createView("V1");
        UUID deviceView2 = createView("V2");

        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device1, "Contains");
        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device2, "Contains");
        createRelation(EntityType.DEVICE, device1, EntityType.ENTITY_VIEW, deviceView1, "Contains");
        createRelation(EntityType.DEVICE, device2, EntityType.ENTITY_VIEW, deviceView2, "Contains");

        // find all entity views of asset A1
        PageData<QueryResult> relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("default"));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, deviceView1));
        Assert.assertTrue(checkContains(relationsResult, deviceView2));

        // find all entity views with max level = 1
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 1, false, Arrays.asList("default"));
        Assert.assertEquals(0, relationsResult.getData().size());

        // find all entity views with type "day 1"
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 1, false, Arrays.asList("day 1"));
        Assert.assertEquals(0, relationsResult.getData().size());

        // find all entity views last level only, level = 2
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, true, Arrays.asList("default"));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, deviceView1));
        Assert.assertTrue(checkContains(relationsResult, deviceView2));
    }

    @Test
    public void testFindTenantDevicesWithGroupPermissionOnly() {
        UUID eg1 = createGroup(EntityType.ENTITY_VIEW, "Group A");

        UUID asset1 = createAsset( "A1");
        UUID device1 = createDevice("D1");
        UUID device2 = createDevice("D2");
        UUID deviceView1 = createView("V1");
        UUID deviceView2 = createView("V2");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.ENTITY_VIEW, deviceView1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device1, "Contains");
        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device2, "Contains");
        createRelation(EntityType.DEVICE, device1, EntityType.ENTITY_VIEW, deviceView1, "Contains");
        createRelation(EntityType.DEVICE, device2, EntityType.ENTITY_VIEW, deviceView2, "Contains");

        // find all devices with group permission only
        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Collections.emptyMap(), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.ENTITY_VIEW, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));
        PageData<QueryResult> relationsResult = findData(readGroupPermissions, null, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("default"));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, deviceView1));
    }

    @Test
    public void testFindCustomerDevices() {
        UUID asset1 = createAsset(customerId.getId(), defaultAssetProfileId, "A1");
        UUID device1 = createDevice(customerId.getId(), defaultDeviceProfileId, "D1");
        UUID device2 = createDevice(customerId.getId(), defaultDeviceProfileId,"D2");
        UUID deviceView1 = createView(customerId.getId(),"day 1", "V1");
        UUID deviceView2 = createView(customerId.getId(), "day 1", "V2");

        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device1, "Contains");
        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device2, "Contains");
        createRelation(EntityType.DEVICE, device1, EntityType.ENTITY_VIEW, deviceView1, "Contains");
        createRelation(EntityType.DEVICE, device2, EntityType.ENTITY_VIEW, deviceView2, "Contains");

        // find all entity views of type "day 1"
        PageData<QueryResult> relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, customerId, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("day 1"));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, deviceView1));
        Assert.assertTrue(checkContains(relationsResult, deviceView2));

        // find all entity views of type "day 2"
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, customerId, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("day 2"));
        Assert.assertEquals(0, relationsResult.getData().size());

        // find all entity views with other customer
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, new CustomerId(UUID.randomUUID()), new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("thermostat"));
        Assert.assertEquals(0, relationsResult.getData().size());
    }

    @Test
    public void testFindCustomerEntityViewsWithGroupPermission() {
        UUID eg1 = createGroup(EntityType.ENTITY_VIEW, "Group A");

        UUID asset1 = createAsset(customerId.getId(), defaultAssetProfileId, "A1");
        UUID device1 = createDevice(customerId.getId(), defaultDeviceProfileId, "D1");
        UUID device2 = createDevice(customerId.getId(), defaultDeviceProfileId,"D2");
        UUID deviceView1 = createView(customerId.getId(),"day 1", "V1");
        UUID deviceView2 = createView(customerId.getId(), "day 1", "V2");

        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.ENTITY_VIEW, deviceView1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device1, "Contains");
        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device2, "Contains");
        createRelation(EntityType.DEVICE, device1, EntityType.ENTITY_VIEW, deviceView1, "Contains");
        createRelation(EntityType.DEVICE, device2, EntityType.ENTITY_VIEW, deviceView2, "Contains");

        // find all entity views with group permission only
        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Collections.emptyMap(), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.ENTITY_VIEW, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));
        PageData<QueryResult> relationsResult = findData(readGroupPermissions, customerId, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("day 1"));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, deviceView1));
    }

    @Test
    public void testFindCustomerAssetsWithGenericAndGroupPermission() {
        CustomerId customerB = new CustomerId(UUID.randomUUID());
        createCustomer(customerB.getId(), customerId.getId(), "Customer B");

        UUID eg1 = createGroup(customerB.getId(), EntityType.ENTITY_VIEW, "Group A");

        UUID asset1 = createAsset(customerId.getId(), defaultAssetProfileId, "A1");
        UUID device1 = createDevice(customerId.getId(), defaultDeviceProfileId, "D1");
        UUID device2 = createDevice(customerB.getId(), defaultDeviceProfileId,"D2");
        UUID deviceView1 = createView(customerId.getId(),"day 1", "V1");
        UUID deviceView2 = createView(customerB.getId(), "day 1", "V2");

        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.ENTITY_VIEW, deviceView1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device1, "Contains");
        createRelation(EntityType.ASSET, asset1, EntityType.DEVICE, device2, "Contains");
        createRelation(EntityType.DEVICE, device1, EntityType.ENTITY_VIEW, deviceView1, "Contains");
        createRelation(EntityType.DEVICE, device2, EntityType.ENTITY_VIEW, deviceView2, "Contains");

        // find all entity views with generic and group permission
        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Map.of(Resource.ALL, Set.of(Operation.ALL)), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.ENTITY_VIEW, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));
        PageData<QueryResult> relationsResult = findData(readGroupPermissions, customerId, new AssetId(asset1),
                EntitySearchDirection.FROM,  "Contains", 2, false, Arrays.asList("day 1"));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, deviceView1));
        Assert.assertTrue(checkContains(relationsResult, deviceView2));
    }

    private PageData<QueryResult> findData(MergedUserPermissions permissions, CustomerId customerId, EntityId rootId,
                                           EntitySearchDirection direction, String relationType, int maxLevel, boolean lastLevelOnly, List<String> entityViewTypes) {
        EntityViewSearchQueryFilter filter = new EntityViewSearchQueryFilter();
        filter.setRootEntity(rootId);
        filter.setDirection(direction);
        filter.setRelationType(relationType);
        filter.setEntityViewTypes(entityViewTypes);
        filter.setFetchLastLevelOnly(lastLevelOnly);
        filter.setMaxLevel(maxLevel);
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, null);
        List<KeyFilter> keyFiltersEqualString = createStringKeyFilters("name", EntityKeyType.ENTITY_FIELD, StringFilterPredicate.StringOperation.STARTS_WITH, "V");
        EntityDataQuery query = new EntityDataQuery(filter, pageLink, Collections.emptyList(), Collections.emptyList(), keyFiltersEqualString);
        return repository.findEntityDataByQuery(tenantId, customerId, permissions, query, false);
    }

}
