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
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.edqs.LatestTsKv;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.EntityViewTypeFilter;
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

public class EntityViewTypeFilterTest extends AbstractEDQTest {

    private EntityView entityView;
    private EntityView entityView2;
    private EntityView entityView3;


    @Before
    public void setUp() {
        entityView = buildEntityView("day 1", "day 1 lora 1 view");
        entityView2 = buildEntityView("day 1", "day 1 lora 2 view");
        entityView3 = buildEntityView("day 2", "day 2 lora 1 view");
        addOrUpdate(EntityType.ENTITY_VIEW, entityView);
        addOrUpdate(EntityType.ENTITY_VIEW, entityView2);
        addOrUpdate(EntityType.ENTITY_VIEW, entityView3);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFindTenantEntityView() {
        // find entity view with type "day 1"
        var result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), null, null), false);

        Assert.assertEquals(2, result.getTotalElements());
        Optional<QueryResult> firstView = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals("day 1 lora 1 view")).findFirst();
        assertThat(firstView).isPresent();
        assertThat(firstView.get().getEntityId()).isEqualTo(entityView.getId());
        assertThat(firstView.get().getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue()).isEqualTo(String.valueOf(entityView.getCreatedTime()));

        // find entity view with types "day 1" and "day 2"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Arrays.asList("day 1", "day 2"), null, null), false);

        Assert.assertEquals(3, result.getTotalElements());
        Optional<QueryResult> thirdView = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals("day 2 lora 1 view")).findFirst();
        assertThat(thirdView).isPresent();
        assertThat(thirdView.get().getEntityId()).isEqualTo(entityView3.getId());
        assertThat(thirdView.get().getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue()).isEqualTo(String.valueOf(entityView.getCreatedTime()));

        // find entity view with type "day 3"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 3"), null, null), false);
        Assert.assertEquals(0, result.getTotalElements());

        // find entity view with name "%Lora%"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), "%day 1 lora%", null), false);
        Assert.assertEquals(2, result.getTotalElements());

        // find entity view with name "Lora 1 device view"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), "day 1 lora 1 view", null), false);
        Assert.assertEquals(1, result.getTotalElements());

        // find entity view with name "%Parking sensor%"
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), "%day 3 lora%", null), false);
        Assert.assertEquals(0, result.getTotalElements());

        // find entity view with key filter: name contains "Lora"
        KeyFilter containsNameFilter = getEntityViewNameKeyFilter(StringFilterPredicate.StringOperation.CONTAINS, "Lora", true);
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), null, Arrays.asList(containsNameFilter)), false);
        Assert.assertEquals(2, result.getTotalElements());

        // find entity view with key filter: name starts with "lora" and matches case
        KeyFilter startsWithNameFilter = getEntityViewNameKeyFilter(StringFilterPredicate.StringOperation.STARTS_WITH, "lora", false);
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), null, Arrays.asList(startsWithNameFilter)), false);
        Assert.assertEquals(0, result.getTotalElements());
    }

    @Test
    public void testFindCustomerEntityView() {
        addOrUpdate(new LatestTsKv(entityView.getId(), new BasicTsKvEntry(43, new StringDataEntry("state", "TEST")), 0L));

        var result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), null, null), false);
        Assert.assertEquals(0, result.getTotalElements());

        entityView.setCustomerId(customerId);
        entityView2.setCustomerId(customerId);
        entityView3.setCustomerId(customerId);
        addOrUpdate(EntityType.ENTITY_VIEW, entityView);
        addOrUpdate(EntityType.ENTITY_VIEW, entityView2);
        addOrUpdate(EntityType.ENTITY_VIEW, entityView3);

        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 1"), null, null), false);

        Assert.assertEquals(2, result.getTotalElements());
        Optional<QueryResult> firstView = result.getData().stream().filter(queryResult -> queryResult.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue().equals("day 1 lora 1 view")).findFirst();
        assertThat(firstView).isPresent();
        assertThat(firstView.get().getEntityId()).isEqualTo(entityView.getId());
        assertThat(firstView.get().getLatest().get(EntityKeyType.ENTITY_FIELD).get("createdTime").getValue()).isEqualTo(String.valueOf(entityView.getCreatedTime()));

        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getEntityViewTypeQuery(Collections.singletonList("day 3"), null, null), false);
        Assert.assertEquals(0, result.getTotalElements());
    }

    private EntityView buildEntityView(String type, String name) {
        EntityView entityView = new EntityView();
        entityView.setId(new EntityViewId(UUID.randomUUID()));
        entityView.setTenantId(tenantId);
        entityView.setType(type);
        entityView.setName(name);
        entityView.setCreatedTime(42L);
        return entityView;
    }

    private static EntityDataQuery getEntityViewTypeQuery(List<String> assetTypes, String entityViewNameFilter, List<KeyFilter> keyFilters) {
        EntityViewTypeFilter filter = new EntityViewTypeFilter();
        filter.setEntityViewTypes(assetTypes);
        filter.setEntityViewNameFilter(entityViewNameFilter);
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
