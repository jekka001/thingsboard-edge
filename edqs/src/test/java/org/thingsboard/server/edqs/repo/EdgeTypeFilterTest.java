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
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edqs.LatestTsKv;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.query.EdgeTypeFilter;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class EdgeTypeFilterTest extends AbstractEDQTest {

    private Edge edge;
    private Edge edge2;
    private Edge edge3;


    @Before
    public void setUp() {
        edge = buildEdge("default", "Edge 1");
        edge2 = buildEdge("default", "Edge 2");
        edge3 = buildEdge("edge v2", "Edge 3");
        addOrUpdate(EntityType.EDGE, edge);
        addOrUpdate(EntityType.EDGE, edge2);
        addOrUpdate(EntityType.EDGE, edge3);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFindTenantEdges() {
        // find edges with type "default"
        var result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(Collections.singletonList("default"), null, null), false);

        Assert.assertEquals(2, result.getTotalElements());
        Optional<QueryResult> firstView = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals("Edge 1")).findFirst();
        assertThat(firstView).isPresent();
        assertThat(firstView.get().getEntityId()).isEqualTo(edge.getId());
        assertThat(firstView.get().getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue()).isEqualTo(String.valueOf(edge.getCreatedTime()));

        // find edges with types "default" and "edge v2"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(Arrays.asList("default", "edge v2"), null, null), false);

        Assert.assertEquals(3, result.getTotalElements());
        Optional<QueryResult> thirdView = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals("Edge 3")).findFirst();
        assertThat(thirdView).isPresent();
        assertThat(thirdView.get().getEntityId()).isEqualTo(edge3.getId());
        assertThat(thirdView.get().getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue()).isEqualTo(String.valueOf(edge.getCreatedTime()));

        // find entity view with type "day 3"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("edge v3"), null, null), false);
        Assert.assertEquals(0, result.getTotalElements());

        // find entity view with name "%Edge%"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), "%Edge%", null), false);
        Assert.assertEquals(2, result.getTotalElements());

        // find entity view with name "Edge 1"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), "Edge 1", null), false);
        Assert.assertEquals(1, result.getTotalElements());

        // find entity view with name "%Edge 4%"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), "%Edge 4%", null), false);
        Assert.assertEquals(0, result.getTotalElements());

        // find entity view with key filter: name contains "Edge"
        KeyFilter containsNameFilter = getEntityViewNameKeyFilter(StringFilterPredicate.StringOperation.CONTAINS, "Edge", true);
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), null, List.of(containsNameFilter)), false);
        Assert.assertEquals(2, result.getTotalElements());

        // find entity view with key filter: name starts with "edge" and matches case
        KeyFilter startsWithNameFilter = getEntityViewNameKeyFilter(StringFilterPredicate.StringOperation.STARTS_WITH, "edge", false);
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), null, List.of(startsWithNameFilter)), false);
        Assert.assertEquals(0, result.getTotalElements());
    }

    @Test
    public void testFindCustomerEdges() {
        addOrUpdate(new LatestTsKv(edge.getId(), new BasicTsKvEntry(43, new StringDataEntry("state", "TEST")), 0L));

        var result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), null, null), false);
        Assert.assertEquals(0, result.getTotalElements());

        edge.setCustomerId(customerId);
        edge2.setCustomerId(customerId);
        edge3.setCustomerId(customerId);
        addOrUpdate(EntityType.EDGE, edge);
        addOrUpdate(EntityType.EDGE, edge2);
        addOrUpdate(EntityType.EDGE, edge3);

        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("default"), null, null), false);

        Assert.assertEquals(2, result.getTotalElements());
        Optional<QueryResult> firstView = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals("Edge 1")).findFirst();
        assertThat(firstView).isPresent();
        assertThat(firstView.get().getEntityId()).isEqualTo(edge.getId());
        assertThat(firstView.get().getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue()).isEqualTo(String.valueOf(edge.getCreatedTime()));

        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEdgeTypeQuery(List.of("edge v3"), null, null), false);
        Assert.assertEquals(0, result.getTotalElements());
    }

    private Edge buildEdge(String type, String name) {
        Edge edge = new Edge();
        edge.setId(new EdgeId(UUID.randomUUID()));
        edge.setTenantId(tenantId);
        edge.setType(type);
        edge.setName(name);
        edge.setCreatedTime(42L);
        return edge;
    }

    private static EntityDataQuery getEdgeTypeQuery(List<String> edgeTypes, String edgeNameFilter, List<KeyFilter> keyFilters) {
        EdgeTypeFilter filter = new EdgeTypeFilter();
        filter.setEdgeTypes(edgeTypes);
        filter.setEdgeNameFilter(edgeNameFilter);
        var pageLink = new EntityDataPageLink(20, 0, null, new EntityDataSortOrder(new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.DESC), false);

        var entityFields = Arrays.asList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"));
        var latestValues = Arrays.asList(new EntityKey(EntityKeyType.TIME_SERIES, "state"));

        return new EntityDataQuery(filter, pageLink, entityFields, latestValues, keyFilters);
    }

    private static KeyFilter getEntityViewNameKeyFilter(StringFilterPredicate.StringOperation operation, String predicateValue, boolean ignoreCase) {
        KeyFilter nameFilter = new KeyFilter();
        nameFilter.setKey(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        var predicate = new StringFilterPredicate();
        predicate.setIgnoreCase(ignoreCase);
        predicate.setOperation(operation);
        predicate.setValue(new FilterPredicateValue<>(predicateValue));
        nameFilter.setPredicate(predicate);
        nameFilter.setValueType(EntityKeyValueType.STRING);
        return nameFilter;
    }

}
