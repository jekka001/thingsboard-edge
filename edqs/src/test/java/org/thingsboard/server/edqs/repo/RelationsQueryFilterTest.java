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
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.RelationsQueryFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationEntityTypeFilter;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.edqs.util.RepositoryUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class RelationsQueryFilterTest extends AbstractEDQTest {

    @Before
    public void setUp() {
    }

    @Test
    public void testFindTenantDevices() {
        UUID ta1 = createAsset("T A1");
        UUID ta2 = createAsset("T A2");
        UUID da1 = createDevice("T D1");
        UUID da2 = createDevice(customerId, "T D2");
        UUID da3 = createDevice("NOT MATCHING D3");

        // A1 --Contains--> A2, A1 --Contains--> D1. A1 --Manages--> D2.
        createRelation(EntityType.ASSET, ta1, EntityType.ASSET, ta2, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da1, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da2, "Manages");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da3, "Contains");

        PageData<QueryResult> relationsResult = filter(new AssetId(ta1), new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, ta2));
        Assert.assertTrue(checkContains(relationsResult, da1));

        relationsResult = filter(new AssetId(ta1), new RelationEntityTypeFilter("Manages", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, da2));
    }

    @Test
    public void testFindTenantDevicesLastLevelOnly() {
        UUID root = createAsset("T ROOT");

        UUID ta1 = createAsset("T A1 NO MORE RELATIONS");
        UUID ta2 = createAsset("T A2");
        UUID da1 = createDevice("T D1");
        UUID da2 = createDevice(customerId, "T D2");
        UUID da3 = createDevice(customerId, "T D3");
        UUID da4 = createDevice(customerId, "T D4"); // Lvl 4

        // ROOT --Contains--> A1, A2; A2 --Contains--> D1, D2; D2 --Contains--> D3.
        createRelation(EntityType.ASSET, root, EntityType.ASSET, ta1, "Contains");
        createRelation(EntityType.ASSET, root, EntityType.ASSET, ta2, "Contains");
        createRelation(EntityType.ASSET, ta2, EntityType.DEVICE, da1, "Contains");
        createRelation(EntityType.ASSET, ta2, EntityType.DEVICE, da2, "Contains");
        createRelation(EntityType.ASSET, da2, EntityType.DEVICE, da3, "Contains");
        createRelation(EntityType.ASSET, da3, EntityType.DEVICE, da4, "Contains");

        PageData<QueryResult> relationsResult = filter(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(root), 1, true,
                new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, ta1));
        Assert.assertTrue(checkContains(relationsResult, ta2));

        relationsResult = filter(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(root), 2, true,
                new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(3, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, ta1));
        Assert.assertTrue(checkContains(relationsResult, da1));
        Assert.assertTrue(checkContains(relationsResult, da2));

        relationsResult = filter(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(root), 3, true,
                new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(3, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, ta1));
        Assert.assertTrue(checkContains(relationsResult, da1));
        Assert.assertTrue(checkContains(relationsResult, da3));

        relationsResult = filter(RepositoryUtils.ALL_READ_PERMISSIONS, null, new AssetId(root), 4, true,
                new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(3, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, ta1));
        Assert.assertTrue(checkContains(relationsResult, da1));
        Assert.assertTrue(checkContains(relationsResult, da4));

    }

    @Test
    public void testFindTenantDevicesGroupsOnly() {
        UUID ta1 = createAsset("T A1");
        UUID ta2 = createAsset("T A2");
        UUID da1 = createDevice("T D1");
        UUID da2 = createDevice(customerId, "T D2");

        UUID eg1 = createGroup(EntityType.DEVICE, "Group A");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.DEVICE, da1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        // A1 --Contains--> A2, A1 --Contains--> D1. A1 --Manages--> D2.
        createRelation(EntityType.ASSET, ta1, EntityType.ASSET, ta2, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da1, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da2, "Manages");

        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Collections.emptyMap(), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.DEVICE, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));

        PageData<QueryResult> relationsResult = filter(readGroupPermissions, new AssetId(ta1), new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, da1));

        relationsResult = filter(readGroupPermissions, new AssetId(ta1), new RelationEntityTypeFilter("Manages", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(0, relationsResult.getData().size());
    }

    @Test
    public void testFindCustomerDevices() {
        UUID ta1 = createAsset("T A1");
        UUID ta2 = createAsset("T A2");
        UUID da1 = createDevice(customerId, "T D1");
        UUID da2 = createDevice("T D2");

        // A1 --Contains--> A2, A1 --Contains--> D1. A1 --Manages--> D2.
        createRelation(EntityType.ASSET, ta1, EntityType.ASSET, ta2, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da1, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da2, "Manages");

        PageData<QueryResult> relationsResult = filter(customerId, new AssetId(ta1), new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, da1));

        relationsResult = filter(customerId, new AssetId(ta1), new RelationEntityTypeFilter("Manages", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(0, relationsResult.getData().size());
    }

    @Test
    public void testFindCustomerDevicesGroupsOnly() {
        UUID ta1 = createAsset("T A1");
        UUID ta2 = createAsset("T A2");
        UUID da1 = createDevice(customerId, "T D1");
        UUID da2 = createDevice(customerId, "T D2");
        UUID da3 = createDevice(customerId, "T D3");

        UUID eg1 = createGroup(EntityType.DEVICE, "Group A");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.DEVICE, da1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.DEVICE, da2, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.DEVICE, da3, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        // A1 --Contains--> A2, A1 --Contains--> D1. A1 --Manages--> D2.
        createRelation(EntityType.ASSET, ta1, EntityType.ASSET, ta2, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da1, "Contains");
        createRelation(EntityType.ASSET, ta1, EntityType.DEVICE, da2, "Manages");
        createRelation(EntityType.DEVICE, da2, EntityType.DEVICE, da3, "Contains");

        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Collections.emptyMap(), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.DEVICE, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));

        PageData<QueryResult> relationsResult = filter(readGroupPermissions, customerId, new AssetId(ta1), new RelationEntityTypeFilter("Contains", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, da1));
        Assert.assertTrue(checkContains(relationsResult, da3));

        relationsResult = filter(readGroupPermissions, customerId, new AssetId(ta1), new RelationEntityTypeFilter("Manages", Arrays.asList(EntityType.DEVICE, EntityType.ASSET)));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, da2));
    }

    private PageData<QueryResult> filter(EntityId rootId, RelationEntityTypeFilter... relationEntityTypeFilters) {
        return filter(RepositoryUtils.ALL_READ_PERMISSIONS, rootId, relationEntityTypeFilters);
    }

    private PageData<QueryResult> filter(MergedUserPermissions permissions, EntityId rootId, RelationEntityTypeFilter... relationEntityTypeFilters) {
        return filter(permissions, null, rootId, relationEntityTypeFilters);
    }

    private PageData<QueryResult> filter(CustomerId customerId, EntityId rootId, RelationEntityTypeFilter... relationEntityTypeFilters) {
        return filter(RepositoryUtils.ALL_READ_PERMISSIONS, customerId, rootId, relationEntityTypeFilters);
    }

    private PageData<QueryResult> filter(MergedUserPermissions permissions, CustomerId customerId, EntityId rootId, RelationEntityTypeFilter... relationEntityTypeFilters) {
        return filter(permissions, customerId, rootId, 3, false, relationEntityTypeFilters);
    }

    private PageData<QueryResult> filter(MergedUserPermissions permissions, CustomerId customerId, EntityId rootId, int maxLevel, boolean lastLevelOnly, RelationEntityTypeFilter... relationEntityTypeFilters) {
        RelationsQueryFilter filter = new RelationsQueryFilter();
        filter.setRootEntity(rootId);
        filter.setFilters(Arrays.asList(relationEntityTypeFilters));
        filter.setDirection(EntitySearchDirection.FROM);
        filter.setFetchLastLevelOnly(lastLevelOnly);
        filter.setMaxLevel(maxLevel);
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, null);
        List<KeyFilter> keyFiltersEqualString = createStringKeyFilters("name", EntityKeyType.ENTITY_FIELD, StringFilterPredicate.StringOperation.STARTS_WITH, "T");
        EntityDataQuery query = new EntityDataQuery(filter, pageLink, Collections.emptyList(), Collections.emptyList(), keyFiltersEqualString);
        return repository.findEntityDataByQuery(tenantId, customerId, permissions, query, false);
    }

}
