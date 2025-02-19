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
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edqs.EdqsObject;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.edqs.util.EdqsConverter;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RunWith(SpringRunner.class)
@Configuration
@ComponentScan({"org.thingsboard.server.edqs.repo", "org.thingsboard.server.edqs.util"})
@EntityScan("org.thingsboard.server.edqs")
@TestPropertySource(locations = {"classpath:edq-test.properties"})
@TestExecutionListeners({
        DependencyInjectionTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class})
public abstract class AbstractEDQTest {

    @Autowired
    protected InMemoryEdqRepository repository;
    @Autowired
    protected EdqsConverter edqsConverter;

    protected final TenantId tenantId = TenantId.fromUUID(UUID.randomUUID());
    protected final CustomerId customerId = new CustomerId(UUID.randomUUID());

    protected final UUID defaultAssetProfileId = UUID.randomUUID();
    protected final UUID defaultDeviceProfileId = UUID.randomUUID();

    @Before
    public final void before() {
        AssetProfile ap = new AssetProfile(new AssetProfileId(defaultAssetProfileId));
        ap.setName("default");
        ap.setDefault(true);
        addOrUpdate(EntityType.ASSET_PROFILE, ap);

        DeviceProfile dp = new DeviceProfile(new DeviceProfileId(defaultDeviceProfileId));
        dp.setName("default");
        dp.setDefault(true);
        dp.setType(DeviceProfileType.DEFAULT);
        addOrUpdate(EntityType.DEVICE_PROFILE, dp);

        createCustomer(customerId.getId(), null, "Customer A");
    }

    @After
    public final void after() {
        repository.clear();
    }

    protected void createCustomer(UUID id, UUID parentCustomerId, String title) {
        Customer entity = new Customer();
        entity.setId(new CustomerId(id));
        entity.setTitle(title);
        entity.setOwnerId(parentCustomerId != null ? new CustomerId(parentCustomerId) : tenantId);
        addOrUpdate(EntityType.CUSTOMER, entity);
    }

    protected UUID createGroup(EntityType entityType, String groupName) {
        return createGroup(null, entityType, groupName);
    }

    protected UUID createGroup(UUID customerId, EntityType entityType, String groupName) {
        EntityGroup eg = new EntityGroup();
        eg.setId(new EntityGroupId(UUID.randomUUID()));
        eg.setType(entityType);
        eg.setName(groupName);
        eg.setOwnerId(customerId != null ? new CustomerId(customerId) : tenantId);
        addOrUpdate(EntityType.ENTITY_GROUP, eg);
        return eg.getId().getId();
    }

    protected UUID createDevice(String name) {
        return createDevice(null, defaultDeviceProfileId, name);
    }

    protected UUID createDevice(CustomerId customerId, String name) {
        return createDevice(customerId.getId(), defaultDeviceProfileId, name);
    }

    protected UUID createDevice(UUID customerId, UUID profileId, String name) {
        UUID entityId = UUID.randomUUID();
        Device entity = new Device();
        entity.setId(new DeviceId(entityId));
        if (profileId != null) {
            entity.setDeviceProfileId(new DeviceProfileId(profileId));
        }
        if (customerId != null) {
            entity.setCustomerId(new CustomerId(customerId));
        }
        entity.setName(name);
        addOrUpdate(EntityType.DEVICE, entity);
        return entityId;
    }

    protected UUID createDashboard(String name) {
        return createDashboard(null, name);
    }

    protected UUID createDashboard(UUID customerId, String name) {
        UUID entityId = UUID.randomUUID();
        Dashboard entity = new Dashboard();
        entity.setId(new DashboardId(entityId));
        if (customerId != null) {
            entity.setCustomerId(new CustomerId(customerId));
        }
        entity.setTitle(name);
        addOrUpdate(EntityType.DEVICE, entity);
        return entityId;
    }

    protected UUID createView(String name) {
        return createView(null, "default", name);
    }

    protected UUID createView(CustomerId customerId, String name) {
        return createView(customerId.getId(), "default", name);
    }

    protected UUID createView(UUID customerId, String type, String name) {
        UUID entityId = UUID.randomUUID();
        EntityView entity = new EntityView();
        entity.setId(new EntityViewId(entityId));
        entity.setType(type);
        if (customerId != null) {
            entity.setCustomerId(new CustomerId(customerId));
        }
        entity.setName(name);
        addOrUpdate(EntityType.ENTITY_VIEW, entity);
        return entityId;
    }

    protected UUID createEdge(String name) {
        return createEdge(null, "default", name);
    }

    protected UUID createEdge(CustomerId customerId, String name) {
        return createEdge(customerId.getId(), "default", name);
    }

    protected UUID createEdge(UUID customerId, String type, String name) {
        UUID id = UUID.randomUUID();
        Edge edge = new Edge();
        edge.setId(new EdgeId(id));
        edge.setTenantId(tenantId);
        if (customerId != null) {
            edge.setCustomerId(new CustomerId(customerId));
        }
        edge.setType(type);
        edge.setName(name);
        edge.setCreatedTime(42L);
        addOrUpdate(EntityType.EDGE, edge);
        return id;
    }

    protected UUID createSchedulerEvent(String type, EntityId originatorId, String name) {
        return createSchedulerEvent(null, type, originatorId, name);
    }

    protected UUID createSchedulerEvent(UUID customerId, String type, EntityId originatorId, String name) {
        UUID id = UUID.randomUUID();
        SchedulerEvent schedulerEvent = new SchedulerEvent();
        schedulerEvent.setId(new SchedulerEventId(id));
        schedulerEvent.setTenantId(tenantId);
        if (customerId != null) {
            schedulerEvent.setCustomerId(new CustomerId(customerId));
        }
        schedulerEvent.setType(type);
        schedulerEvent.setName(name);
        schedulerEvent.setConfiguration(JacksonUtil.newObjectNode());
        schedulerEvent.setSchedule(JacksonUtil.newObjectNode());
        schedulerEvent.setOriginatorId(originatorId);
        schedulerEvent.setCreatedTime(42L);
        addOrUpdate(EntityType.SCHEDULER_EVENT, schedulerEvent);
        return id;
    }

    protected UUID createAsset(String name) {
        return createAsset(null, defaultAssetProfileId, name);
    }

    protected UUID createAsset(UUID customerId, String name) {
        return createAsset(customerId, defaultAssetProfileId, name);
    }

    protected UUID createAsset(UUID customerId, UUID profileId, String name) {
        UUID entityId = UUID.randomUUID();
        Asset entity = new Asset();
        entity.setId(new AssetId(entityId));
        if (profileId != null) {
            entity.setAssetProfileId(new AssetProfileId(profileId));
        }
        if (customerId != null) {
            entity.setCustomerId(new CustomerId(customerId));
        }
        entity.setName(name);
        addOrUpdate(EntityType.ASSET, entity);
        return entityId;
    }

    protected void createRelation(EntityType fromType, UUID fromId, EntityType toType, UUID toId, String type) {
        createRelation(fromType, fromId, toType, toId, RelationTypeGroup.COMMON, type);
    }

    protected void createRelation(EntityType fromType, UUID fromId, EntityType toType, UUID toId, RelationTypeGroup group, String type) {
        addOrUpdate(new EntityRelation(EntityIdFactory.getByTypeAndUuid(fromType, fromId), EntityIdFactory.getByTypeAndUuid(toType, toId), type, group));
    }


    protected boolean checkContains(PageData<QueryResult> data, UUID entityId) {
        return data.getData().stream().anyMatch(r -> r.getEntityId().getId().equals(entityId));
    }

    protected List<KeyFilter> createStringKeyFilters(String key, EntityKeyType keyType, StringFilterPredicate.StringOperation operation, String value) {
        KeyFilter filter = new KeyFilter();
        filter.setKey(new EntityKey(keyType, key));
        filter.setValueType(EntityKeyValueType.STRING);
        StringFilterPredicate predicate = new StringFilterPredicate();
        predicate.setValue(FilterPredicateValue.fromString(value));
        predicate.setOperation(operation);
        predicate.setIgnoreCase(true);
        filter.setPredicate(predicate);
        return Collections.singletonList(filter);
    }

    protected void addOrUpdate(EntityType entityType, Object entity) {
        addOrUpdate(EdqsConverter.toEntity(entityType, entity));
    }

    protected void addOrUpdate(EdqsObject edqsObject) {
        byte[] serialized = edqsConverter.serialize(edqsObject.type(), edqsObject);
        edqsObject = edqsConverter.deserialize(edqsObject.type(), serialized);
        repository.get(tenantId).addOrUpdate(edqsObject);
    }

}
