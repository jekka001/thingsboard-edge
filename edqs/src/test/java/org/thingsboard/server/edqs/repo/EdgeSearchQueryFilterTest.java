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
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.MergedGroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.query.EdgeSearchQueryFilter;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.edqs.util.RepositoryUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class EdgeSearchQueryFilterTest extends AbstractEDQTest {

    @Before
    public void setUp() {
    }

    @Test
    public void testFindDevicesManagesByTenant() {
        UUID edge1 = createEdge("E1");
        UUID edge2 = createEdge("E2");
        UUID device1 = createDevice("D1");
        UUID device2 = createDevice("D2");
        UUID device3 = createDevice("D3");

        createRelation(EntityType.EDGE, edge1, EntityType.DEVICE, device1, "Manages");
        createRelation(EntityType.EDGE, edge2, EntityType.DEVICE, device2, "Manages");
        createRelation(EntityType.EDGE, edge2, EntityType.DEVICE, device3, "Manages");

        // find devices managed by edge
        PageData<QueryResult> relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new DeviceId(device1),
                EntitySearchDirection.TO,  "Manages", 2, false, Arrays.asList("default"));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, edge1));

        // find devices managed by edge with non-existing type
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new DeviceId(device1),
                EntitySearchDirection.TO,  "Manages", 1, false, Arrays.asList("non-existing type"));
        Assert.assertEquals(0, relationsResult.getData().size());

        // find all entity views last level only, level = 2
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, null, new DeviceId(device1),
                EntitySearchDirection.TO,  "Manages", 2, true, Arrays.asList("default"));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, edge1));
    }

    @Test
    public void testFindTenantEdgesWithGroupPermissionOnly() {
        UUID eg1 = createGroup(EntityType.EDGE, "Group A");

        UUID edge1 = createEdge("E1");
        UUID edge2 = createEdge("E2");
        createRelation(EntityType.TENANT, tenantId.getId(), EntityType.EDGE, edge1, "Manages");
        createRelation(EntityType.TENANT, tenantId.getId(), EntityType.EDGE, edge2, "Manages");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.ENTITY_VIEW, edge1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        // find all devices with group permission only
        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Collections.emptyMap(), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.EDGE, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));
        PageData<QueryResult> relationsResult = findData(readGroupPermissions, null, tenantId,
                EntitySearchDirection.FROM,  "Manages", 2, false, Arrays.asList("default"));
        Assert.assertEquals(1, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, edge1));
    }

    @Test
    public void testFindCustomerEdges() {
        UUID edge1 = createEdge(customerId, "E1");
        UUID edge2 = createEdge(customerId, "E2");
        createRelation(EntityType.CUSTOMER, customerId.getId(), EntityType.EDGE, edge1, "Manages");
        createRelation(EntityType.CUSTOMER, customerId.getId(), EntityType.EDGE, edge2, "Manages");

        // find all edges managed by customer
        PageData<QueryResult> relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, customerId, customerId,
                EntitySearchDirection.FROM,  "Manages", 2, false, Arrays.asList("default"));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, edge1));
        Assert.assertTrue(checkContains(relationsResult, edge2));

        // find all edges managed by customer with non-existing type
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, customerId, customerId,
                EntitySearchDirection.FROM,  "Manages", 2, false, Arrays.asList("non existing"));
        Assert.assertEquals(0, relationsResult.getData().size());

        // find all entity views with other customer
        relationsResult = findData(RepositoryUtils.ALL_READ_PERMISSIONS, new CustomerId(UUID.randomUUID()), customerId,
                EntitySearchDirection.FROM,  "Manages", 2, false, Arrays.asList("default"));
        Assert.assertEquals(0, relationsResult.getData().size());
    }

    @Test
    public void testFindCustomerEdgesWithGroupPermission() {
        UUID eg1 = createGroup(EntityType.EDGE, "Group A");

        UUID edge1 = createEdge(customerId, "E1");
        UUID edge2 = createEdge(customerId, "E2");
        UUID edge3 = createEdge(customerId, "E3");

        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.ENTITY_VIEW, edge1, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");
        createRelation(EntityType.ENTITY_GROUP, eg1, EntityType.ENTITY_VIEW, edge2, RelationTypeGroup.FROM_ENTITY_GROUP, "Contains");

        createRelation(EntityType.CUSTOMER, customerId.getId(), EntityType.EDGE, edge1, "Manages");
        createRelation(EntityType.CUSTOMER, customerId.getId(), EntityType.EDGE, edge2, "Manages");
        createRelation(EntityType.CUSTOMER, customerId.getId(), EntityType.EDGE, edge3, "Manages");

        // find all entity views with group permission only
        MergedUserPermissions readGroupPermissions = new MergedUserPermissions(Collections.emptyMap(), Collections.singletonMap(new EntityGroupId(eg1),
                new MergedGroupPermissionInfo(EntityType.EDGE, new HashSet<>(Arrays.asList(Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY)))));
        PageData<QueryResult> relationsResult = findData(readGroupPermissions, customerId, customerId,
                EntitySearchDirection.FROM,  "Manages", 2, false, Arrays.asList("default"));
        Assert.assertEquals(2, relationsResult.getData().size());
        Assert.assertTrue(checkContains(relationsResult, edge1));
        Assert.assertTrue(checkContains(relationsResult, edge2));
    }

     private PageData<QueryResult> findData(MergedUserPermissions permissions, CustomerId customerId, EntityId rootId,
                                           EntitySearchDirection direction, String relationType, int maxLevel, boolean lastLevelOnly, List<String> edgeTypes) {
        EdgeSearchQueryFilter filter = new EdgeSearchQueryFilter();
        filter.setRootEntity(rootId);
        filter.setDirection(direction);
        filter.setRelationType(relationType);
        filter.setEdgeTypes(edgeTypes);
        filter.setFetchLastLevelOnly(lastLevelOnly);
        filter.setMaxLevel(maxLevel);
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, null);
        List<KeyFilter> keyFiltersEqualString = createStringKeyFilters("name", EntityKeyType.ENTITY_FIELD, StringFilterPredicate.StringOperation.STARTS_WITH, "E");
        EntityDataQuery query = new EntityDataQuery(filter, pageLink, Collections.emptyList(), Collections.emptyList(), keyFiltersEqualString);
        return repository.findEntityDataByQuery(tenantId, customerId, permissions, query, false);
    }

}
