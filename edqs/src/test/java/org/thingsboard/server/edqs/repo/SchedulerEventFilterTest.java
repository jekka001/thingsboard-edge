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
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.SchedulerEventFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.edqs.util.RepositoryUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class SchedulerEventFilterTest extends AbstractEDQTest {

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testFindTenantSchedulerEvents() {
        UUID dashboardId = createDashboard("test dashboard");
        UUID deviceId = createDevice("test device");

        UUID eventId1 = createSchedulerEvent("Update attributes", new DeviceId(deviceId), "Turn off device");
        UUID eventId2 = createSchedulerEvent("Generate report", new DashboardId(dashboardId), "Generate morning report");
        UUID eventId3 = createSchedulerEvent("Generate report", new DashboardId(dashboardId), "Generate evening report");

        // find all scheduler events with type "Generate report"
        var result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getSchedulerEventQuery("Generate report", null, null), false);
        Assert.assertEquals(2, result.getTotalElements());
        Assert.assertTrue(checkContains(result, eventId2));
        Assert.assertTrue(checkContains(result, eventId3));

        // find all scheduler events for device originator
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getSchedulerEventQuery(null, new DeviceId(deviceId), null), false);
        Assert.assertEquals(1, result.getTotalElements());
        Assert.assertTrue(checkContains(result, eventId1));

        // find  all scheduler events with name "%morning%"
        KeyFilter containsNameFilter = getSchedulerEventNameKeyFilter(StringFilterPredicate.StringOperation.CONTAINS, "morning", true);
        result = repository.findEntityDataByQuery(tenantId, null, RepositoryUtils.ALL_READ_PERMISSIONS, getSchedulerEventQuery(null, null, List.of(containsNameFilter)), false);
        Assert.assertEquals(1, result.getTotalElements());
        Assert.assertTrue(checkContains(result, eventId2));
    }

    @Test
    public void testFindCustomerEdges() {
        UUID dashboardId = createDashboard( "test dashboard");
        UUID deviceId = createDevice("test device");

        UUID eventId1 = createSchedulerEvent(customerId.getId(), "Update attributes", new DeviceId(deviceId), "Turn off device");
        UUID eventId2 = createSchedulerEvent(customerId.getId(), "Generate report", new DashboardId(dashboardId), "Generate morning report");
        UUID eventId3 = createSchedulerEvent(customerId.getId(), "Generate report", new DashboardId(dashboardId), "Generate evening report");

        // find all scheduler events with type "Generate report"
        var result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getSchedulerEventQuery("Generate report", null, null), false);
        Assert.assertEquals(2, result.getTotalElements());
        Assert.assertTrue(checkContains(result, eventId2));
        Assert.assertTrue(checkContains(result, eventId3));

        // find all scheduler events for device originator
        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getSchedulerEventQuery(null, new DeviceId(deviceId), null), false);
        Assert.assertEquals(1, result.getTotalElements());
        Assert.assertTrue(checkContains(result, eventId1));

        // find  all scheduler events with name "%morning%"
        KeyFilter containsNameFilter = getSchedulerEventNameKeyFilter(StringFilterPredicate.StringOperation.CONTAINS, "morning", true);
        result = repository.findEntityDataByQuery(tenantId, customerId, RepositoryUtils.ALL_READ_PERMISSIONS, getSchedulerEventQuery(null, null, List.of(containsNameFilter)), false);
        Assert.assertEquals(1, result.getTotalElements());
        Assert.assertTrue(checkContains(result, eventId2));
    }

    private static EntityDataQuery getSchedulerEventQuery(String eventType, EntityId entityId, List<KeyFilter> keyFilters) {
        SchedulerEventFilter filter = new SchedulerEventFilter();
        filter.setEventType(eventType);
        filter.setOriginator(entityId);
        var pageLink = new EntityDataPageLink(20, 0, null, new EntityDataSortOrder(new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.DESC), false);

        var entityFields = Arrays.asList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"));
        var latestValues = Arrays.asList(new EntityKey(EntityKeyType.TIME_SERIES, "state"));

        return new EntityDataQuery(filter, pageLink, entityFields, latestValues, keyFilters);
    }

    private static KeyFilter getSchedulerEventNameKeyFilter(StringFilterPredicate.StringOperation operation, String predicateValue, boolean ignoreCase) {
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
