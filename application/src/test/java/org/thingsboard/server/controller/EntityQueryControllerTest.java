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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.permission.GroupPermissionInfo;
import org.thingsboard.server.common.data.permission.ShareGroupRequest;
import org.thingsboard.server.common.data.query.AlarmCountQuery;
import org.thingsboard.server.common.data.query.DeviceTypeFilter;
import org.thingsboard.server.common.data.query.DynamicValue;
import org.thingsboard.server.common.data.query.DynamicValueSourceType;
import org.thingsboard.server.common.data.query.EntityCountQuery;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityGroupListFilter;
import org.thingsboard.server.common.data.query.EntityGroupNameFilter;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.EntityListFilter;
import org.thingsboard.server.common.data.query.EntityTypeFilter;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.NumericFilterPredicate;
import org.thingsboard.server.common.data.query.RelationsQueryFilter;
import org.thingsboard.server.common.data.query.SchedulerEventFilter;
import org.thingsboard.server.common.data.query.StringFilterPredicate;
import org.thingsboard.server.common.data.query.TsValue;
import org.thingsboard.server.common.data.queue.QueueStats;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationEntityTypeFilter;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.scheduler.MonthlyRepeat;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.scheduler.SchedulerRepeat;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.queue.QueueStatsService;
import org.thingsboard.server.dao.service.DaoSqlTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class EntityQueryControllerTest extends AbstractControllerTest {

    protected final String CUSTOMER_ADMIN_EMAIL = "testadmincustomer@thingsboard.org";
    protected final String CUSTOMER_ADMIN_PASSWORD = "admincustomer";

    private Tenant savedTenant;
    private User tenantAdmin;
    private User savedCustomerAdministrator;

    private Role role;
    private EntityGroup entityGroup;
    private GroupPermission groupPermission;
    private final String classNameAlarm = "ALARM";

    @Autowired
    private QueueStatsService queueStatsService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = saveTenant(tenant);
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();
        deleteTenant(savedTenant.getId());
    }

    @Before
    public void setup() throws Exception {
        loginTenantAdmin();

        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCustomerId(customerId);
        role.setType(RoleType.GENERIC);
        role.setName("Test customer administrator");
        role.setPermissions(JacksonUtil.toJsonNode("{\"ALL\":[\"ALL\"]}"));

        this.role = doPost("/api/role", role, Role.class);

        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName("Test customer administrators");
        entityGroup.setType(EntityType.USER);
        entityGroup.setOwnerId(customerId);
        this.entityGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class);

        GroupPermission groupPermission = new GroupPermission(
                tenantId,
                this.entityGroup.getId(),
                this.role.getId(),
                null,
                null,
                false
        );
        this.groupPermission =
                doPost("/api/groupPermission", groupPermission, GroupPermission.class);
    }

    @Test
    public void testTenantCountEntitiesByQuery() throws Exception {
        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            Device device = new Device();
            device.setName("Device" + i);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device", device, Device.class));
            Thread.sleep(1);
        }
        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        EntityCountQuery countQuery = new EntityCountQuery(filter);

        Long count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        filter.setDeviceTypes(List.of("unknown"));
        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());

        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("Device1");

        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(11, count.longValue());

        EntityListFilter entityListFilter = new EntityListFilter();
        entityListFilter.setEntityType(EntityType.DEVICE);
        entityListFilter.setEntityList(devices.stream().map(Device::getId).map(DeviceId::toString).collect(Collectors.toList()));

        countQuery = new EntityCountQuery(entityListFilter);

        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        EntityTypeFilter filter2 = new EntityTypeFilter();
        filter2.setEntityType(EntityType.DEVICE);

        EntityCountQuery countQuery2 = new EntityCountQuery(filter2);

        Long count2 = doPostWithResponse("/api/entitiesQuery/count", countQuery2, Long.class);
        Assert.assertEquals(97, count2.longValue());
    }

    @Test
    public void testSysAdminCountEntitiesByQuery() throws Exception {
        loginSysAdmin();

        EntityTypeFilter allDeviceFilter = new EntityTypeFilter();
        allDeviceFilter.setEntityType(EntityType.DEVICE);
        EntityCountQuery query = new EntityCountQuery(allDeviceFilter);
        Long initialCount = doPostWithResponse("/api/entitiesQuery/count", query, Long.class);

        loginTenantAdmin();

        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            Device device = new Device();
            device.setName("Device" + i);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device", device, Device.class));
            Thread.sleep(1);
        }
        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceType("default");
        filter.setDeviceNameFilter("");

        loginSysAdmin();

        EntityCountQuery countQuery = new EntityCountQuery(filter);

        Long count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        filter.setDeviceType("unknown");
        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());

        filter.setDeviceType("default");
        filter.setDeviceNameFilter("Device1");

        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(11, count.longValue());

        EntityListFilter entityListFilter = new EntityListFilter();
        entityListFilter.setEntityType(EntityType.DEVICE);
        entityListFilter.setEntityList(devices.stream().map(Device::getId).map(DeviceId::toString).collect(Collectors.toList()));

        countQuery = new EntityCountQuery(entityListFilter);

        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        Long count2 = doPostWithResponse("/api/entitiesQuery/count", query, Long.class);
        Assert.assertEquals(initialCount + 97, count2.longValue());
    }

    @Test
    public void testTenantCountAlarmsByQuery() throws Exception {
        loginTenantAdmin();
        List<Device> devices = new ArrayList<>();
        List<Alarm> alarms = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            Device device = new Device();
            device.setName("Device" + i);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device", device, Device.class));
            Thread.sleep(1);
        }

        for (int i = 0; i < devices.size(); i++) {
            Alarm alarm = new Alarm();
            alarm.setOriginator(devices.get(i).getId());
            alarm.setType("alarm" + i);
            alarm.setSeverity(AlarmSeverity.WARNING);
            alarms.add(doPost("/api/alarm", alarm, Alarm.class));
            Thread.sleep(1);
        }
        testCountAlarmsByQuery(alarms);
    }

    @Test
    public void testCustomerCountAlarmsByQuery() throws Exception {
        loginTenantAdmin();
        List<Device> devices = new ArrayList<>();
        List<Alarm> alarms = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            Device device = new Device();
            device.setCustomerId(customerId);
            device.setName("Device" + i);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device", device, Device.class));
            Thread.sleep(1);
        }

        loginCustomerAdministrator();

        for (int i = 0; i < devices.size(); i++) {
            Alarm alarm = new Alarm();
            alarm.setCustomerId(customerId);
            alarm.setOriginator(devices.get(i).getId());
            alarm.setType("alarm" + i);
            alarm.setSeverity(AlarmSeverity.WARNING);
            alarms.add(doPost("/api/alarm", alarm, Alarm.class));
            Thread.sleep(1);
        }
        testCountAlarmsByQuery(alarms);
    }

    private void testCountAlarmsByQuery(List<Alarm> alarms) throws Exception {
        AlarmCountQuery countQuery = new AlarmCountQuery();

        Long count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .typeList(List.of("unknown"))
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .typeList(List.of("alarm1", "alarm2", "alarm3"))
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(3, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .typeList(alarms.stream().map(Alarm::getType).collect(Collectors.toList()))
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .severityList(List.of(AlarmSeverity.CRITICAL))
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .severityList(List.of(AlarmSeverity.WARNING))
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        long startTs = alarms.stream().map(Alarm::getCreatedTime).min(Long::compareTo).get();
        long endTs = alarms.stream().map(Alarm::getCreatedTime).max(Long::compareTo).get();

        countQuery = AlarmCountQuery.builder()
                .startTs(startTs - 1)
                .endTs(endTs + 1)
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .startTs(0)
                .endTs(endTs + 1)
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .startTs(0)
                .endTs(System.currentTimeMillis())
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        countQuery = AlarmCountQuery.builder()
                .startTs(endTs + 1)
                .endTs(System.currentTimeMillis())
                .build();

        count = doPostWithResponse("/api/alarmsQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());
    }

    @Test
    public void testSimpleFindEntityDataByQuery() throws Exception {
        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            Device device = new Device();
            device.setName("Device" + i);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device", device, Device.class));
            Thread.sleep(1);
        }

        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, null, null);

        PageData<EntityData> data =
                doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
                });

        Assert.assertEquals(97, data.getTotalElements());
        Assert.assertEquals(10, data.getTotalPages());
        Assert.assertTrue(data.hasNext());
        Assert.assertEquals(10, data.getData().size());

        List<EntityData> loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(97, loadedEntities.size());

        List<EntityId> loadedIds = loadedEntities.stream().map(EntityData::getEntityId).collect(Collectors.toList());
        List<EntityId> deviceIds = devices.stream().map(Device::getId).collect(Collectors.toList());

        Assert.assertEquals(deviceIds, loadedIds);

        List<String> loadedNames = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).collect(Collectors.toList());
        List<String> deviceNames = devices.stream().map(Device::getName).collect(Collectors.toList());

        Assert.assertEquals(deviceNames, loadedNames);

        sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), EntityDataSortOrder.Direction.DESC
        );

        pageLink = new EntityDataPageLink(10, 0, "device1", sortOrder);
        query = new EntityDataQuery(filter, pageLink, entityFields, null, null);
        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });
        Assert.assertEquals(11, data.getTotalElements());
        Assert.assertEquals("Device19", data.getData().get(0).getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());


        EntityTypeFilter filter2 = new EntityTypeFilter();
        filter2.setEntityType(EntityType.DEVICE);

        EntityDataSortOrder sortOrder2 = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink2 = new EntityDataPageLink(10, 0, null, sortOrder2);
        List<EntityKey> entityFields2 = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query2 = new EntityDataQuery(filter2, pageLink2, entityFields2, null, null);

        PageData<EntityData> data2 =
                doPostWithTypedResponse("/api/entitiesQuery/find", query2, new TypeReference<PageData<EntityData>>() {
                });

        Assert.assertEquals(97, data2.getTotalElements());
        Assert.assertEquals(10, data2.getTotalPages());
        Assert.assertTrue(data2.hasNext());
        Assert.assertEquals(10, data2.getData().size());
    }

    @Test
    public void testFindEntityDataByQueryWithNegateParam() throws Exception {
        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            Device device = new Device();
            device.setName("Device" + i);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device", device, Device.class));
            Thread.sleep(1);
        }

        Device mainDevice = new Device();
        mainDevice.setName("Main device");
        mainDevice = doPost("/api/device", mainDevice, Device.class);

        for (int i = 0; i < 10; i++) {
            EntityRelation relation = createFromRelation(mainDevice, devices.get(i), "CONTAINS");
            doPost("/api/relation", relation).andExpect(status().isOk());
        }

        for (int i = 10; i < 97; i++) {
            EntityRelation relation = createFromRelation(mainDevice, devices.get(i), "NOT_CONTAINS");
            doPost("/api/relation", relation).andExpect(status().isOk());
        }

        RelationsQueryFilter filter = new RelationsQueryFilter();
        filter.setRootEntity(mainDevice.getId());
        filter.setDirection(EntitySearchDirection.FROM);
        filter.setNegate(true);
        filter.setFilters(List.of(new RelationEntityTypeFilter("CONTAINS", List.of(EntityType.DEVICE), false)));

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, null, null);

        PageData<EntityData> data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {});

        Assert.assertEquals(87, data.getTotalElements());

        filter.setFilters(List.of(new RelationEntityTypeFilter("NOT_CONTAINS", List.of(EntityType.DEVICE), false)));
        query = new EntityDataQuery(filter, pageLink, entityFields, null, null);
        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {});
        Assert.assertEquals(10, data.getTotalElements());

        filter.setFilters(List.of(new RelationEntityTypeFilter("NOT_CONTAINS", List.of(EntityType.DEVICE), true)));
        query = new EntityDataQuery(filter, pageLink, entityFields, null, null);
        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {});
        Assert.assertEquals(87, data.getTotalElements());
    }

    private EntityRelation createFromRelation(Device mainDevice, Device device, String relationType) {
        return new EntityRelation(mainDevice.getId(), device.getId(), relationType);
    }

    @Test
    public void testFindEntityDataByQueryWithAttributes() throws Exception {
        List<Device> devices = new ArrayList<>();
        List<Long> temperatures = new ArrayList<>();
        List<Long> highTemperatures = new ArrayList<>();
        for (int i = 0; i < 67; i++) {
            Device device = new Device();
            String name = "Device" + i;
            device.setName(name);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            devices.add(doPost("/api/device?accessToken=" + name, device, Device.class));
            Thread.sleep(1);
            long temperature = (long) (Math.random() * 100);
            temperatures.add(temperature);
            if (temperature > 45) {
                highTemperatures.add(temperature);
            }
        }
        for (int i = 0; i < devices.size(); i++) {
            Device device = devices.get(i);
            String payload = "{\"temperature\":" + temperatures.get(i) + "}";
            doPost("/api/plugins/telemetry/" + device.getId() + "/" + DataConstants.SHARED_SCOPE, payload, String.class, status().isOk());
        }
        Thread.sleep(1000);

        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        List<EntityKey> latestValues = Collections.singletonList(new EntityKey(EntityKeyType.ATTRIBUTE, "temperature"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, latestValues, null);
        PageData<EntityData> data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });

        List<EntityData> loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(67, loadedEntities.size());

        List<String> loadedTemperatures = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ATTRIBUTE).get("temperature").getValue()).collect(Collectors.toList());
        List<String> deviceTemperatures = temperatures.stream().map(aLong -> Long.toString(aLong)).collect(Collectors.toList());
        Assert.assertEquals(deviceTemperatures, loadedTemperatures);

        pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        KeyFilter highTemperatureFilter = new KeyFilter();
        highTemperatureFilter.setKey(new EntityKey(EntityKeyType.ATTRIBUTE, "temperature"));
        NumericFilterPredicate predicate = new NumericFilterPredicate();
        predicate.setValue(FilterPredicateValue.fromDouble(45));
        predicate.setOperation(NumericFilterPredicate.NumericOperation.GREATER);
        highTemperatureFilter.setPredicate(predicate);
        List<KeyFilter> keyFilters = Collections.singletonList(highTemperatureFilter);

        query = new EntityDataQuery(filter, pageLink, entityFields, latestValues, keyFilters);

        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });
        loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(highTemperatures.size(), loadedEntities.size());

        List<String> loadedHighTemperatures = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ATTRIBUTE).get("temperature").getValue()).collect(Collectors.toList());
        List<String> deviceHighTemperatures = highTemperatures.stream().map(aLong -> Long.toString(aLong)).collect(Collectors.toList());

        Assert.assertEquals(deviceHighTemperatures, loadedHighTemperatures);
    }

    @Test
    public void testFindEntityDataByEntityGroupNameFilterQuery() throws Exception {
        List<EntityGroup> groups = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            EntityGroup entityGroup = new EntityGroup();
            entityGroup.setName("TestGroup" + i);
            entityGroup.setType(EntityType.DEVICE);
            groups.add(doPost("/api/entityGroup", entityGroup, EntityGroup.class));
            Thread.sleep(1);
        }

        EntityGroupNameFilter filter = new EntityGroupNameFilter();
        filter.setGroupType(EntityType.DEVICE);
        filter.setEntityGroupNameFilter("TEST");

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, null, null);

        PageData<EntityData> data =
                doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
                });

        Assert.assertEquals(97, data.getTotalElements());
        Assert.assertEquals(10, data.getTotalPages());
        Assert.assertTrue(data.hasNext());
        Assert.assertEquals(10, data.getData().size());

        List<EntityData> loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(97, loadedEntities.size());

        List<EntityId> loadedIds = loadedEntities.stream().map(EntityData::getEntityId).collect(Collectors.toList());
        List<EntityId> deviceIds = groups.stream().map(EntityGroup::getId).collect(Collectors.toList());

        Assert.assertEquals(deviceIds, loadedIds);

        List<String> loadedNames = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).collect(Collectors.toList());
        List<String> deviceNames = groups.stream().map(EntityGroup::getName).collect(Collectors.toList());

        Assert.assertEquals(deviceNames, loadedNames);

        sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), EntityDataSortOrder.Direction.DESC
        );

        pageLink = new EntityDataPageLink(10, 0, "testGroup1", sortOrder);
        query = new EntityDataQuery(filter, pageLink, entityFields, null, null);
        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
        });
        Assert.assertEquals(11, data.getTotalElements());
        Assert.assertEquals("TestGroup19",
                data.getData().get(0).getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());

        EntityGroupNameFilter filter2 = new EntityGroupNameFilter();
        filter2.setGroupType(EntityType.DEVICE);
        filter2.setEntityGroupNameFilter("test");

        EntityDataSortOrder sortOrder2 = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC);
        EntityDataPageLink pageLink2 = new EntityDataPageLink(10, 0, null, sortOrder2);
        List<EntityKey> entityFields2 = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query2 = new EntityDataQuery(filter2, pageLink2, entityFields2, null, null);

        PageData<EntityData> data2 =
                doPostWithTypedResponse("/api/entitiesQuery/find", query2, new TypeReference<>() {
                });

        Assert.assertEquals(97, data2.getTotalElements());
        Assert.assertEquals(10, data2.getTotalPages());
        Assert.assertTrue(data2.hasNext());
        Assert.assertEquals(10, data2.getData().size());
    }

    @Test
    public void testFindEntityDataByEntityGroupListFilterQueryByTenant() throws Exception {
        List<EntityGroup> groups = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            EntityGroup entityGroup = new EntityGroup();
            entityGroup.setName("TestGroup" + i);
            entityGroup.setType(EntityType.DEVICE);
            groups.add(doPost("/api/entityGroup", entityGroup, EntityGroup.class));
            Thread.sleep(1);
        }

        EntityGroupListFilter filter = new EntityGroupListFilter();
        filter.setGroupType(EntityType.DEVICE);
        filter.setEntityGroupList(groups.stream().map(EntityGroup::getId).map(Objects::toString).collect(Collectors.toList()));

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, null, null);

        PageData<EntityData> data =
                doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
                });

        Assert.assertEquals(97, data.getTotalElements());
        Assert.assertEquals(10, data.getTotalPages());
        Assert.assertTrue(data.hasNext());
        Assert.assertEquals(10, data.getData().size());

        List<EntityData> loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(97, loadedEntities.size());

        List<EntityId> loadedIds = loadedEntities.stream().map(EntityData::getEntityId).collect(Collectors.toList());
        List<EntityId> deviceIds = groups.stream().map(EntityGroup::getId).collect(Collectors.toList());

        Assert.assertEquals(deviceIds, loadedIds);

        List<String> loadedNames = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).collect(Collectors.toList());
        List<String> deviceNames = groups.stream().map(EntityGroup::getName).collect(Collectors.toList());

        Assert.assertEquals(deviceNames, loadedNames);

        sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), EntityDataSortOrder.Direction.DESC
        );

        pageLink = new EntityDataPageLink(10, 0, "testGroup1", sortOrder);
        query = new EntityDataQuery(filter, pageLink, entityFields, null, null);
        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
        });
        Assert.assertEquals(11, data.getTotalElements());
        Assert.assertEquals("TestGroup19",
                data.getData().get(0).getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());
    }

    @Test
    public void testFindEntityDataByEntityGroupListFilterQueryByCustomerWithSharedGroups() throws Exception {
        List<EntityGroup> groups = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            EntityGroup entityGroup = new EntityGroup();
            entityGroup.setName("TestGroup" + i);
            entityGroup.setType(EntityType.DEVICE);
            groups.add(entityGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class));
            Thread.sleep(1);
            var shareGroupRequest = new ShareGroupRequest(customerId, true, null, false, Collections.emptyList());
            doPost("/api/entityGroup/{entityGroupId}/share", shareGroupRequest, entityGroup.getId().toString());
        }

        loginCustomerAdministrator();

        for (int i = 0; i < 97; i++) {
            EntityGroup entityGroup = new EntityGroup();
            entityGroup.setName("TestCustomerGroup" + i);
            entityGroup.setType(EntityType.DEVICE);
            groups.add(doPost("/api/entityGroup", entityGroup, EntityGroup.class));
            Thread.sleep(1);
        }

        EntityGroupListFilter filter = new EntityGroupListFilter();
        filter.setGroupType(EntityType.DEVICE);
        filter.setEntityGroupList(groups.stream().map(EntityGroup::getId).map(Objects::toString).collect(Collectors.toList()));

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, null, null);

        PageData<EntityData> data =
                doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
                });

        Assert.assertEquals(groups.size(), data.getTotalElements());
        Assert.assertTrue(data.hasNext());

        List<EntityData> loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(groups.size(), loadedEntities.size());

        List<EntityId> loadedIds = loadedEntities.stream().map(EntityData::getEntityId).collect(Collectors.toList());
        List<EntityId> deviceIds = groups.stream().map(EntityGroup::getId).collect(Collectors.toList());

        Assert.assertEquals(deviceIds, loadedIds);

        List<String> loadedNames = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).collect(Collectors.toList());
        List<String> deviceNames = groups.stream().map(EntityGroup::getName).collect(Collectors.toList());

        Assert.assertEquals(deviceNames, loadedNames);
    }

    @Test
    public void testFindEntityDataAfterEntityGroupIsUnshared() throws Exception {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName("TestGroup");
        entityGroup.setType(EntityType.DEVICE);
        entityGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class);

        List<Device> devices = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Device device = new Device();
            String name = "Device" + i;
            device.setName(name);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));
            Device savedDevice = doPost("/api/device?accessToken=" + name, device, Device.class);
            devices.add(savedDevice);
            Thread.sleep(1);
            long temperature = (long) (Math.random() * 100);
            doPost("/api/entityGroup/" + entityGroup.getId() + "/addEntities", Collections.singletonList(savedDevice.getId().getId()))
                    .andExpect(status().isOk());
        }

        //share group for customer
        var shareGroupRequest = new ShareGroupRequest(customerId, true, null, false, Collections.emptyList());
        doPost("/api/entityGroup/{entityGroupId}/share", shareGroupRequest, entityGroup.getId().toString());

        loginCustomerAdministrator();

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(20, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        List<EntityKey> latestValues = Collections.singletonList(new EntityKey(EntityKeyType.ATTRIBUTE, "temperature"));
        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, latestValues, null);
        PageData<EntityData> data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });
        List<String> loadedNames = data.getData().stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).collect(Collectors.toList());
        assertThat(loadedNames).containsExactlyInAnyOrderElementsOf(devices.stream().map(Device::getName).collect(Collectors.toList()));

        //unshare group
        loginTenantAdmin();
        List<GroupPermissionInfo> loadedGroupPermissionsInfo = doGetTyped("/api/entityGroup/" + entityGroup.getId().getId() + "/groupPermissions",
                new TypeReference<>() {});
        doDelete("/api/groupPermission/" + loadedGroupPermissionsInfo.get(0).getUuidId())
                .andExpect(status().isOk());

        //check no more devices are visible
        loginCustomerAdministrator();
        PageData<EntityData> dataAfterGroupIsUnshared = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });
        assertThat(dataAfterGroupIsUnshared.getData()).isEmpty();
    }

    @Test
    public void testFindEntityDataByQueryWithDynamicValue() throws Exception {
        int numOfDevices = 2;

        for (int i = 0; i < numOfDevices; i++) {
            Device device = new Device();
            String name = "Device" + i;
            device.setName(name);
            device.setType("default");
            device.setLabel("testLabel" + (int) (Math.random() * 1000));

            Device savedDevice1 = doPost("/api/device?accessToken=" + name, device, Device.class);
            JsonNode content = JacksonUtil.toJsonNode("{\"alarmActiveTime\": 1" + i + "}");
            doPost("/api/plugins/telemetry/" + EntityType.DEVICE.name() + "/" + savedDevice1.getUuidId() + "/SERVER_SCOPE", content)
                    .andExpect(status().isOk());
        }
        JsonNode content = JacksonUtil.toJsonNode("{\"dynamicValue\": 0}");
        doPost("/api/plugins/telemetry/" + EntityType.TENANT.name() + "/" + tenantId.getId() + "/SERVER_SCOPE", content)
                .andExpect(status().isOk());


        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        KeyFilter highTemperatureFilter = new KeyFilter();
        highTemperatureFilter.setKey(new EntityKey(EntityKeyType.SERVER_ATTRIBUTE, "alarmActiveTime"));
        NumericFilterPredicate predicate = new NumericFilterPredicate();

        DynamicValue<Double> dynamicValue =
                new DynamicValue<>(DynamicValueSourceType.CURRENT_TENANT, "dynamicValue");
        FilterPredicateValue<Double> predicateValue = new FilterPredicateValue<>(0.0, null, dynamicValue);

        predicate.setValue(predicateValue);
        predicate.setOperation(NumericFilterPredicate.NumericOperation.GREATER);
        highTemperatureFilter.setPredicate(predicate);

        List<KeyFilter> keyFilters = Collections.singletonList(highTemperatureFilter);


        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);

        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        List<EntityKey> latestValues = Collections.singletonList(new EntityKey(EntityKeyType.ATTRIBUTE, "alarmActiveTime"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, latestValues, keyFilters);

        Awaitility.await()
                .alias("data by query")
                .atMost(TIMEOUT, TimeUnit.SECONDS)
                .until(() -> {
                    var data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
                    });
                    var loadedEntities = new ArrayList<>(data.getData());
                    return loadedEntities.size() == numOfDevices;
                });

        var data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });
        var loadedEntities = new ArrayList<>(data.getData());

        Assert.assertEquals(numOfDevices, loadedEntities.size());

        for (int i = 0; i < numOfDevices; i++) {
            var entity = loadedEntities.get(i);
            String name = entity.getLatest().get(EntityKeyType.ENTITY_FIELD).getOrDefault("name", new TsValue(0, "Invalid")).getValue();
            String alarmActiveTime = entity.getLatest().get(EntityKeyType.ATTRIBUTE).getOrDefault("alarmActiveTime", new TsValue(0, "-1")).getValue();

            Assert.assertEquals("Device" + i, name);
            Assert.assertEquals("1" + i, alarmActiveTime);
        }
    }

    @Test
    public void testCountSchedulerEventsByQuery() throws Exception {
        List<SchedulerEvent> schedulerEvents = new ArrayList<>();
        EntityId originator = new DeviceId(UUID.randomUUID());
        for (int i = 0; i < 97; i++) {
            schedulerEvents.add(doPost("/api/schedulerEvent", createSchedulerEvent(originator, "CUSTOM", "Name " + i), SchedulerEvent.class));
            Thread.sleep(1);
        }
        SchedulerEventFilter filter = new SchedulerEventFilter();

        EntityCountQuery countQuery = new EntityCountQuery(filter);

        Long count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        filter.setOriginator(originator);
        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        filter.setEventType("CUSTOM");
        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());

        filter.setEventType("NOT_CUSTOM");
        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());

        filter.setEventType("CUSTOM");
        filter.setOriginator(new DeviceId(UUID.randomUUID()));
        count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(0, count.longValue());
    }

    @Test
    public void testFindSchedulerEventsByQuery() throws Exception {
        List<SchedulerEvent> schedulerEvents = new ArrayList<>();
        EntityId originator = new DeviceId(UUID.randomUUID());
        for (int i = 0; i < 97; i++) {
            schedulerEvents.add(doPost("/api/schedulerEvent", createSchedulerEvent(originator, "CUSTOM", "Name " + i), SchedulerEvent.class));
            Thread.sleep(1);
        }

        SchedulerEventFilter filter = new SchedulerEventFilter();

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, null, null);

        PageData<EntityData> data =
                doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
                });

        Assert.assertEquals(97, data.getTotalElements());
        Assert.assertEquals(10, data.getTotalPages());
        Assert.assertTrue(data.hasNext());
        Assert.assertEquals(10, data.getData().size());

        List<EntityData> loadedEntities = new ArrayList<>(data.getData());
        while (data.hasNext()) {
            query = query.next();
            data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
            });
            loadedEntities.addAll(data.getData());
        }
        Assert.assertEquals(97, loadedEntities.size());

        List<EntityId> loadedIds = loadedEntities.stream().map(EntityData::getEntityId).collect(Collectors.toList());
        List<EntityId> originalIds = schedulerEvents.stream().map(SchedulerEvent::getId).collect(Collectors.toList());

        Assert.assertEquals(originalIds, loadedIds);
//
        List<String> loadedNames = loadedEntities.stream().map(entityData ->
                entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).collect(Collectors.toList());
        List<String> originalNames = schedulerEvents.stream().map(SchedulerEvent::getName).collect(Collectors.toList());

        Assert.assertEquals(originalNames, loadedNames);

        sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), EntityDataSortOrder.Direction.DESC
        );

        pageLink = new EntityDataPageLink(10, 0, "Name 1", sortOrder);
        query = new EntityDataQuery(filter, pageLink, entityFields, null, null);
        data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
        });
        Assert.assertEquals(11, data.getTotalElements());
        Assert.assertEquals("Name 19", data.getData().get(0).getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue());


        SchedulerEventFilter filter2 = new SchedulerEventFilter();
        filter2.setOriginator(originator);

        EntityDataSortOrder sortOrder2 = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink2 = new EntityDataPageLink(10, 0, null, sortOrder2);
        List<EntityKey> entityFields2 = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));

        EntityDataQuery query2 = new EntityDataQuery(filter2, pageLink2, entityFields2, null, null);

        PageData<EntityData> data2 =
                doPostWithTypedResponse("/api/entitiesQuery/find", query2, new TypeReference<PageData<EntityData>>() {
                });

        Assert.assertEquals(97, data2.getTotalElements());
        Assert.assertEquals(10, data2.getTotalPages());
        Assert.assertTrue(data2.hasNext());
        Assert.assertEquals(10, data2.getData().size());
    }


    private SchedulerEvent createSchedulerEvent(EntityId originator, String type, String name) {
        SchedulerEvent schedulerEvent = new SchedulerEvent();
        schedulerEvent.setTenantId(tenantId);
        schedulerEvent.setOriginatorId(originator);
        schedulerEvent.setName(name);
        schedulerEvent.setType(type);
        ObjectNode schedule = JacksonUtil.newObjectNode();
        schedule.put("startTime", System.currentTimeMillis());
        schedule.put("timezone", "UTC");
        SchedulerRepeat schedulerRepeat = new MonthlyRepeat();
        schedule.set("repeat", JacksonUtil.valueToTree(schedulerRepeat));
        schedulerEvent.setSchedule(schedule);
        return schedulerEvent;
    }

    @Test
    public void givenInvalidEntityDataPageLink_thenReturnError() throws Exception {
        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        String invalidSortProperty = "created(Time)";
        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, invalidSortProperty), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Collections.singletonList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"));
        List<EntityKey> latestValues = Collections.singletonList(new EntityKey(EntityKeyType.ATTRIBUTE, "temperature"));
        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, latestValues, null);

        ResultActions result = doPost("/api/entitiesQuery/find", query).andExpect(status().isBadRequest());
        assertThat(getErrorMessage(result)).contains("Invalid").contains("sort property");
    }

    @Test
    public void testFindQueueStatsEntitiesByQuery() throws Exception {
        List<QueueStats> queueStatsList = new ArrayList<>();
        for (int i = 0; i < 97; i++) {
            QueueStats queueStats = new QueueStats();
            queueStats.setQueueName(StringUtils.randomAlphabetic(5));
            queueStats.setServiceId(StringUtils.randomAlphabetic(5));
            queueStats.setTenantId(tenantId);
            queueStatsList.add(queueStatsService.save(tenantId, queueStats));
            Thread.sleep(1);
        }

        EntityTypeFilter entityTypeFilter = new EntityTypeFilter();
        entityTypeFilter.setEntityType(EntityType.QUEUE_STATS);

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "queueName"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = Arrays.asList(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), new EntityKey(EntityKeyType.ENTITY_FIELD, "queueName"),
                new EntityKey(EntityKeyType.ENTITY_FIELD, "serviceId"));

        EntityDataQuery query = new EntityDataQuery(entityTypeFilter, pageLink, entityFields, null, null);

        PageData<EntityData> data =
                doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {
                });

        Assert.assertEquals(97, data.getTotalElements());
        Assert.assertEquals(10, data.getTotalPages());
        Assert.assertTrue(data.hasNext());
        Assert.assertEquals(10, data.getData().size());
        data.getData().forEach(entityData -> {
            String queueName = entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("queueName").getValue();
            String serviceId = entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("serviceId").getValue();
            assertThat(queueName).isNotBlank();
            assertThat(serviceId).isNotBlank();
            assertThat(entityData.getLatest().get(EntityKeyType.ENTITY_FIELD).get("name").getValue()).isEqualTo(queueName + "_" + serviceId);

        });

        EntityCountQuery countQuery = new EntityCountQuery(entityTypeFilter);

        Long count = doPostWithResponse("/api/entitiesQuery/count", countQuery, Long.class);
        Assert.assertEquals(97, count.longValue());
    }

    @Test
    public void testFindDevicesCountByOwnerNameAndOwnerType() throws Exception {
        loginTenantAdmin();
        int numOfDevices = 8;

        for (int i = 0; i < numOfDevices; i++) {
            Device device = new Device();
            String name = "Device" + i;
            device.setName(name);
            device.setType("default");

            Device savedDevice = doPost("/api/device?accessToken=" + name, device, Device.class);
            JsonNode content = JacksonUtil.toJsonNode("{\"alarmActiveTime\": 1" + i + "}");
            doPost("/api/plugins/telemetry/" + EntityType.DEVICE.name() + "/" + savedDevice.getUuidId() + "/SERVER_SCOPE", content)
                    .andExpect(status().isOk());
        }

        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        KeyFilter activeAlarmTimeFilter = getServerAttributeNumericGreaterThanKeyFilter("alarmActiveTime", 5);
        KeyFilter activeAlarmTimeToLongFilter = getServerAttributeNumericGreaterThanKeyFilter("alarmActiveTime", 30);
        KeyFilter tenantOwnerNameFilter = getEntityFieldStringEqualToKeyFilter("ownerName", TEST_TENANT_NAME);
        KeyFilter wrongOwnerNameFilter = getEntityFieldStringEqualToKeyFilter("ownerName", "wrongName");
        KeyFilter tenantOwnerTypeFilter =  getEntityFieldStringEqualToKeyFilter("ownerType", "TENANT");
        KeyFilter customerOwnerTypeFilter = getEntityFieldStringEqualToKeyFilter("ownerType", "CUSTOMER");

        // all devices with ownerName = TEST TENANT
        EntityCountQuery query = new EntityCountQuery(filter,  List.of(activeAlarmTimeFilter, tenantOwnerNameFilter));
        checkEntitiesCount(query, numOfDevices);

        // all devices with ownerName = TEST TENANT
        EntityCountQuery activeAlarmTimeToLongQuery = new EntityCountQuery(filter,  List.of(activeAlarmTimeToLongFilter, tenantOwnerNameFilter));
        checkEntitiesCount(activeAlarmTimeToLongQuery, 0);

        // all devices with wrong ownerName
        EntityCountQuery wrongTenantNameQuery = new EntityCountQuery(filter, List.of(activeAlarmTimeFilter, wrongOwnerNameFilter));
        checkEntitiesCount(wrongTenantNameQuery, 0);

        // all devices with owner type = TENANT
        EntityCountQuery tenantEntitiesQuery = new EntityCountQuery(filter, List.of(activeAlarmTimeFilter, tenantOwnerTypeFilter));
        checkEntitiesCount(tenantEntitiesQuery, numOfDevices);

        // all devices with owner type = CUSTOMER
        EntityCountQuery customerEntitiesQuery = new EntityCountQuery(filter, List.of(activeAlarmTimeFilter, customerOwnerTypeFilter));
        checkEntitiesCount(customerEntitiesQuery, 0);
    }

    @Test
    public void testFindDevicesByOwnerNameAndOwnerType() throws Exception {
        loginTenantAdmin();
        int numOfDevices = 3;

        for (int i = 0; i < numOfDevices; i++) {
            Device device = new Device();
            String name = "Device" + i;
            device.setName(name);
            device.setType("default");

            Device savedDevice = doPost("/api/device?accessToken=" + name, device, Device.class);
            JsonNode content = JacksonUtil.toJsonNode("{\"alarmActiveTime\": 1" + i + "}");
            doPost("/api/plugins/telemetry/" + EntityType.DEVICE.name() + "/" + savedDevice.getUuidId() + "/SERVER_SCOPE", content)
                    .andExpect(status().isOk());
        }

        DeviceTypeFilter filter = new DeviceTypeFilter();
        filter.setDeviceTypes(List.of("default"));
        filter.setDeviceNameFilter("");

        KeyFilter activeAlarmTimeFilter = getServerAttributeNumericGreaterThanKeyFilter("alarmActiveTime", 5);
        KeyFilter tenantOwnerNameFilter = getEntityFieldStringEqualToKeyFilter("ownerName", TEST_TENANT_NAME);
        KeyFilter wrongOwnerNameFilter = getEntityFieldStringEqualToKeyFilter("ownerName", "wrongName");
        KeyFilter tenantOwnerTypeFilter =  getEntityFieldStringEqualToKeyFilter("ownerType", "TENANT");
        KeyFilter customerOwnerTypeFilter = getEntityFieldStringEqualToKeyFilter("ownerType", "CUSTOMER");

        EntityDataSortOrder sortOrder = new EntityDataSortOrder(
                new EntityKey(EntityKeyType.ENTITY_FIELD, "createdTime"), EntityDataSortOrder.Direction.ASC
        );
        EntityDataPageLink pageLink = new EntityDataPageLink(10, 0, null, sortOrder);
        List<EntityKey> entityFields = List.of(new EntityKey(EntityKeyType.ENTITY_FIELD, "name"), new EntityKey(EntityKeyType.ENTITY_FIELD, "ownerName"),
                new EntityKey(EntityKeyType.ENTITY_FIELD, "ownerType"));
        List<EntityKey> latestValues = Collections.singletonList(new EntityKey(EntityKeyType.ATTRIBUTE, "alarmActiveTime"));

        // all devices with ownerName = TEST TENANT
        EntityDataQuery query = new EntityDataQuery(filter, pageLink, entityFields, latestValues, List.of(activeAlarmTimeFilter, tenantOwnerNameFilter));
        checkEntitiesByQuery(query, numOfDevices, TEST_TENANT_NAME, "TENANT");

        // all devices with wrong ownerName
        EntityDataQuery wrongTenantNameQuery = new EntityDataQuery(filter, pageLink, entityFields, latestValues, List.of(activeAlarmTimeFilter, wrongOwnerNameFilter));
        checkEntitiesByQuery(wrongTenantNameQuery, 0, null, null);

        // all devices with owner type = TENANT
        EntityDataQuery tenantEntitiesQuery = new EntityDataQuery(filter, pageLink, entityFields, latestValues, List.of(activeAlarmTimeFilter, tenantOwnerTypeFilter));
        checkEntitiesByQuery(tenantEntitiesQuery, numOfDevices, TEST_TENANT_NAME, "TENANT");

        // all devices with owner type = CUSTOMER
        EntityDataQuery customerEntitiesQuery = new EntityDataQuery(filter, pageLink, entityFields, latestValues, List.of(activeAlarmTimeFilter, customerOwnerTypeFilter));
        checkEntitiesByQuery(customerEntitiesQuery, 0, null, null);
    }

    @Test
    public void testCountByQueryWithUnsuitableEntityType() {
        EntityTypeFilter entityTypeFilter = new EntityTypeFilter();
        EntityCountQuery entityTypeQuery = new EntityCountQuery(entityTypeFilter);
        long count = doPost("/api/entitiesQuery/count", entityTypeQuery, Long.class);
        Assert.assertEquals(0, count);

        entityTypeFilter.setEntityType(EntityType.NOTIFICATION);
        count = doPost("/api/entitiesQuery/count", entityTypeQuery, Long.class);
        Assert.assertEquals(0, count);

        EntityGroupNameFilter groupNameFilter = new EntityGroupNameFilter();
        EntityCountQuery groupNameQuery = new EntityCountQuery(groupNameFilter);
        count = doPost("/api/entitiesQuery/count", groupNameQuery, Long.class);
        Assert.assertEquals(0, count);

        groupNameFilter.setGroupType(EntityType.ALARM);
        count = doPost("/api/entitiesQuery/count", groupNameQuery, Long.class);
        Assert.assertEquals(0, count);
    }

    private void clearCustomerAdminPermissionGroup() throws Exception {
        loginTenantAdmin();
        doDelete("/api/groupPermission/" + groupPermission.getUuidId())
                .andExpect(status().isOk());
        doDelete("/api/entityGroup/" + entityGroup.getUuidId())
                .andExpect(status().isOk());
        doDelete("/api/role/" + role.getUuidId())
                .andExpect(status().isOk());
    }

    private void loginCustomerAdministrator() throws Exception {
        if (savedCustomerAdministrator == null) {
            savedCustomerAdministrator = createCustomerAdministrator(
                    tenantId,
                    customerId,
                    CUSTOMER_ADMIN_EMAIL,
                    CUSTOMER_ADMIN_PASSWORD
            );
        }
        login(savedCustomerAdministrator.getEmail(), CUSTOMER_ADMIN_PASSWORD);
    }

    private User createCustomerAdministrator(TenantId tenantId, CustomerId customerId, String email, String pass) throws Exception {
        loginTenantAdmin();

        User user = new User();
        user.setEmail(email);
        user.setTenantId(tenantId);
        user.setCustomerId(customerId);
        user.setFirstName("customer");
        user.setLastName("admin");
        user.setAuthority(Authority.CUSTOMER_USER);

        user = createUser(user, pass, entityGroup.getId());
        customerAdminUserId = user.getId();
        resetTokens();

        return user;
    }

    private void checkEntitiesByQuery(EntityDataQuery query, int expectedNumOfDevices, String expectedOwnerName, String expectedOwnerType) throws Exception {
        Awaitility.await()
                .alias("data by query")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    var data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {});
                    var loadedEntities = new ArrayList<>(data.getData());
                    return loadedEntities.size() == expectedNumOfDevices;
                });
        if (expectedNumOfDevices == 0) {
            return;
        }
        var data = doPostWithTypedResponse("/api/entitiesQuery/find", query, new TypeReference<PageData<EntityData>>() {});
        var loadedEntities = new ArrayList<>(data.getData());

        Assert.assertEquals(expectedNumOfDevices, loadedEntities.size());

        for (int i = 0; i < expectedNumOfDevices; i++) {
            var entity = loadedEntities.get(i);
            String name = entity.getLatest().get(EntityKeyType.ENTITY_FIELD).getOrDefault("name", new TsValue(0, "Invalid")).getValue();
            String ownerName = entity.getLatest().get(EntityKeyType.ENTITY_FIELD).getOrDefault("ownerName", new TsValue(0, "Invalid")).getValue();
            String ownerType = entity.getLatest().get(EntityKeyType.ENTITY_FIELD).getOrDefault("ownerType", new TsValue(0, "Invalid")).getValue();
            String alarmActiveTime = entity.getLatest().get(EntityKeyType.ATTRIBUTE).getOrDefault("alarmActiveTime", new TsValue(0, "-1")).getValue();

            Assert.assertEquals("Device" + i, name);
            Assert.assertEquals( expectedOwnerName, ownerName);
            Assert.assertEquals( expectedOwnerType, ownerType);
            Assert.assertEquals("1" + i, alarmActiveTime);
        }
    }

    private void checkEntitiesCount(EntityCountQuery query, int expectedNumOfDevices) {
        Awaitility.await()
                .alias("count by query")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    var count = doPost("/api/entitiesQuery/count", query, Integer.class);
                    return count == expectedNumOfDevices;
                });
   }

    private KeyFilter getEntityFieldStringEqualToKeyFilter(String keyName, String value) {
        KeyFilter tenantOwnerNameFilter = new KeyFilter();
        tenantOwnerNameFilter.setKey(new EntityKey(EntityKeyType.ENTITY_FIELD, keyName));
        StringFilterPredicate ownerNamePredicate = new StringFilterPredicate();
        ownerNamePredicate.setValue(FilterPredicateValue.fromString(value));
        ownerNamePredicate.setOperation(StringFilterPredicate.StringOperation.EQUAL);
        tenantOwnerNameFilter.setPredicate(ownerNamePredicate);
        return tenantOwnerNameFilter;
    }

    private KeyFilter getServerAttributeNumericGreaterThanKeyFilter(String attribute, int value) {
        KeyFilter numericFilter = new KeyFilter();
        numericFilter.setKey(new EntityKey(EntityKeyType.SERVER_ATTRIBUTE, attribute));
        NumericFilterPredicate predicate = new NumericFilterPredicate();
        predicate.setValue(FilterPredicateValue.fromDouble(value));
        predicate.setOperation(NumericFilterPredicate.NumericOperation.GREATER);
        numericFilter.setPredicate(predicate);
        return numericFilter;
    }

}
