/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.ResultActions;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.permission.ShareGroupRequest;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.security.Authority;

import java.util.LinkedList;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class BaseAlarmControllerTest extends AbstractControllerTest {

    public static final String TEST_ALARM_TYPE = "Test";

    protected final String CUSTOMER_ADMIN_EMAIL = "testadmincustomer@thingsboard.org";
    protected final String CUSTOMER_ADMIN_PASSWORD = "admincustomer";

    protected final String DIFFERENT_CUSTOMER_ADMIN_EMAIL = "testdiffadmincustomer@thingsboard.org";
    protected final String DIFFERENT_CUSTOMER_ADMIN_PASSWORD = "diffadmincustomer";

    protected final String SUB_CUSTOMER_ADMIN_EMAIL = "subcustomer@thingsboard.org";
    protected final String SUB_CUSTOMER_ADMIN_PASSWORD = "subcustomer";

    protected final String SUB_SUB_CUSTOMER_ADMIN_EMAIL = "subsubcustomer@thingsboard.org";
    protected final String SUB_SUB_CUSTOMER_ADMIN_PASSWORD = "subsubcustomer";

    protected Device customerDevice;

    private Role role;
    private EntityGroup entityGroup;
    private GroupPermission groupPermission;

    @Before
    public void setup() throws Exception {
        loginTenantAdmin();

        Device device = new Device();
        device.setTenantId(tenantId);
        device.setName("Test device");
        device.setLabel("Label");
        device.setType("Type");
        device.setCustomerId(customerId);
        customerDevice = doPost("/api/device", device, Device.class);

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

    @After
    public void teardown() throws Exception {
        loginSysAdmin();
        deleteDifferentTenant();
        clearCustomerAdminPermissionGroup();
    }

    @Test
    public void testCreateAlarmViaCustomerWithPermission() throws Exception {
        loginCustomerAdministrator();

        Mockito.reset(tbClusterService, auditLogService);

        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        testNotifyEntityEntityGroupNullAllOneTime(alarm, alarm.getId(), alarm.getOriginator(),
                tenantId, customerId, customerAdminUserId, CUSTOMER_ADMIN_EMAIL, ActionType.ADDED);

    }

    @Test
    public void testCreateAlarmViaCustomerWithoutPermission() throws Exception {
        loginCustomerUser();
        createAlarmAndReturnAction(TEST_ALARM_TYPE).andExpect(status().isForbidden());
    }

    @Test
    public void testCreateAlarmViaTenant() throws Exception {
        loginTenantAdmin();
        createAlarm(TEST_ALARM_TYPE);

        Mockito.reset(tbClusterService, auditLogService);

        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        testNotifyEntityEntityGroupNullAllOneTime(alarm, alarm.getId(), alarm.getOriginator(),
                tenantId, customerId, tenantAdminUserId, TENANT_ADMIN_EMAIL, ActionType.ADDED);
    }

    @Test
    public void testUpdateAlarmViaCustomerWithPermission() throws Exception {
        loginCustomerAdministrator();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        Mockito.reset(tbClusterService, auditLogService);

        alarm.setSeverity(AlarmSeverity.MAJOR);
        Alarm updatedAlarm = doPost("/api/alarm", alarm, Alarm.class);
        Assert.assertNotNull(updatedAlarm);
        Assert.assertEquals(AlarmSeverity.MAJOR, updatedAlarm.getSeverity());

        testNotifyEntityEntityGroupNullAllOneTime(updatedAlarm, updatedAlarm.getId(), updatedAlarm.getOriginator(),
                tenantId, customerId, customerAdminUserId, CUSTOMER_ADMIN_EMAIL, ActionType.UPDATED);

    }

    @Test
    public void testUpdateAlarmViaCustomerWithoutPermission() throws Exception {
        loginCustomerUser();
        createAlarmAndReturnAction(TEST_ALARM_TYPE).andExpect(status().isForbidden());
    }

    @Test
    public void testUpdateAlarmViaTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        Mockito.reset(tbClusterService, auditLogService);

        alarm.setSeverity(AlarmSeverity.MAJOR);
        Alarm updatedAlarm = doPost("/api/alarm", alarm, Alarm.class);
        Assert.assertNotNull(updatedAlarm);
        Assert.assertEquals(AlarmSeverity.MAJOR, updatedAlarm.getSeverity());

        testNotifyEntityEntityGroupNullAllOneTime(updatedAlarm, updatedAlarm.getId(), updatedAlarm.getOriginator(),
                tenantId, customerId, tenantAdminUserId, TENANT_ADMIN_EMAIL, ActionType.UPDATED);
    }

    @Test
    public void testUpdateAlarmViaDifferentTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        alarm.setSeverity(AlarmSeverity.MAJOR);
        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm", alarm).andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testUpdateAlarmViaDifferentCustomer() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentCustomer();
        alarm.setSeverity(AlarmSeverity.MAJOR);

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm", alarm).andExpect(status().isForbidden());
        loginDifferentCustomerAdministrator();
        doPost("/api/alarm", alarm).andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testDeleteAlarmViaCustomerWithPermission() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);
        loginCustomerAdministrator();

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/alarm/" + alarm.getId()).andExpect(status().isOk());

        testNotifyEntityOneTimeMsgToEdgeServiceNever(alarm, alarm.getId(), alarm.getOriginator(),
                tenantId, customerId, customerAdminUserId, CUSTOMER_ADMIN_EMAIL, ActionType.DELETED);

    }

    @Test
    public void testDeleteAlarmViaCustomerWithoutPermission() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);
        loginCustomerUser();
        doDelete("/api/alarm/" + alarm.getId()).andExpect(status().isForbidden());
    }

    @Test
    public void testDeleteAlarmViaTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/alarm/" + alarm.getId()).andExpect(status().isOk());

        testNotifyEntityOneTimeMsgToEdgeServiceNever(alarm, alarm.getId(), alarm.getOriginator(),
                tenantId, tenantAdminCustomerId, tenantAdminUserId, TENANT_ADMIN_EMAIL, ActionType.DELETED);
    }

    @Test
    public void testDeleteAlarmViaDifferentTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/alarm/" + alarm.getId()).andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testDeleteAlarmViaDifferentCustomer() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentCustomer();

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/alarm/" + alarm.getId()).andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);

        loginDifferentCustomerAdministrator();
        doDelete("/api/alarm/" + alarm.getId()).andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testClearAlarmViaCustomer() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/clear").andExpect(status().isOk());

        Alarm foundAlarm = doGet("/api/alarm/" + alarm.getId(), Alarm.class);
        Assert.assertNotNull(foundAlarm);
        Assert.assertEquals(AlarmStatus.CLEARED_UNACK, foundAlarm.getStatus());

        testNotifyEntityEntityGroupNullAllOneTime(foundAlarm, foundAlarm.getId(), foundAlarm.getOriginator(),
                tenantId, customerId, tenantAdminUserId, TENANT_ADMIN_EMAIL, ActionType.ALARM_CLEAR);
    }

    @Test
    public void testClearAlarmViaTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/clear").andExpect(status().isOk());
        Alarm foundAlarm = doGet("/api/alarm/" + alarm.getId(), Alarm.class);
        Assert.assertNotNull(foundAlarm);
        Assert.assertEquals(AlarmStatus.CLEARED_UNACK, foundAlarm.getStatus());

        testNotifyEntityEntityGroupNullAllOneTime(foundAlarm, foundAlarm.getId(), foundAlarm.getOriginator(),
                tenantId, customerId, tenantAdminUserId, TENANT_ADMIN_EMAIL, ActionType.ALARM_CLEAR);
    }

    @Test
    public void testAcknowledgeAlarmViaCustomerWithoutPermission() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);
        loginCustomerUser();
        doPost("/api/alarm/" + alarm.getId() + "/ack").andExpect(status().isForbidden());
    }

    @Test
    public void testAcknowledgeAlarmViaCustomerWithPermission() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);
        loginCustomerAdministrator();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/ack").andExpect(status().isOk());

        Alarm foundAlarm = doGet("/api/alarm/" + alarm.getId(), Alarm.class);
        Assert.assertNotNull(foundAlarm);
        Assert.assertEquals(AlarmStatus.ACTIVE_ACK, foundAlarm.getStatus());

        testNotifyEntityEntityGroupNullAllOneTime(foundAlarm, foundAlarm.getId(), foundAlarm.getOriginator(),
                tenantId, customerId, customerAdminUserId, CUSTOMER_ADMIN_EMAIL, ActionType.ALARM_ACK);
    }

    @Test
    public void testClearAlarmViaDifferentCustomer() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentCustomer();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/clear").andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);

        loginDifferentCustomerAdministrator();
        doPost("/api/alarm/" + alarm.getId() + "/clear").andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testClearAlarmViaDifferentTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/clear").andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testAcknowledgeAlarmViaDifferentCustomer() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentCustomer();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/ack").andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);

        loginDifferentCustomerAdministrator();
        doPost("/api/alarm/" + alarm.getId() + "/ack").andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testAcknowledgeAlarmViaDifferentTenant() throws Exception {
        loginTenantAdmin();
        Alarm alarm = createAlarm(TEST_ALARM_TYPE);

        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/alarm/" + alarm.getId() + "/ack").andExpect(status().isForbidden());

        testNotifyEntityNever(alarm.getId(), alarm);
    }

    @Test
    public void testFindAlarmsViaCustomerUser() throws Exception {
        loginCustomerAdministrator();

        List<Alarm> createdAlarms = new LinkedList<>();

        final int size = 10;
        for (int i = 0; i < size; i++) {
            createdAlarms.add(
                    createAlarm(TEST_ALARM_TYPE + i)
            );
        }

        var response = doGetTyped(
                "/api/alarm/" + customerDevice.getEntityType() + "/"
                        + customerDevice.getUuidId() + "?page=0&pageSize=" + size,
                new TypeReference<PageData<AlarmInfo>>() {}
        );
        var foundAlarmInfos = response.getData();
        Assert.assertNotNull("Found pageData is null", foundAlarmInfos);
        Assert.assertNotEquals(
                "Expected alarms are not found!",
                0, foundAlarmInfos.size()
        );

        boolean allMatch = createdAlarms.stream()
                .allMatch(alarm -> foundAlarmInfos.stream()
                        .map(Alarm::getType)
                        .anyMatch(type -> alarm.getType().equals(type))
                );
        Assert.assertTrue("Created alarm doesn't match any found!", allMatch);
    }

    @Test
    public void testFindAlarmsViaDifferentCustomerUser() throws Exception {
        loginCustomerAdministrator();

        final int size = 10;
        for (int i = 0; i < size; i++) {
            createAlarm(TEST_ALARM_TYPE + i);
        }

        loginDifferentCustomer();
        doGet("/api/alarm/" + customerDevice.getEntityType() + "/"
                + customerDevice.getUuidId() + "?page=0&pageSize=" + size)
                .andExpect(status().isForbidden());

        loginDifferentCustomerAdministrator();
        doGet("/api/alarm/" + customerDevice.getEntityType() + "/"
                + customerDevice.getUuidId() + "?page=0&pageSize=" + size)
                .andExpect(status().isForbidden());
    }

    @Test
    public void testSubCustomersAlarmsCanBeFoundByParentCustomer() throws Exception {
        loginCustomerAdministrator();

        Customer subCustomer = new Customer();
        subCustomer.setParentCustomerId(customerId);
        subCustomer.setTitle("Sub Customer");

        Customer savedSubCustomer = doPost("/api/customer", subCustomer, Customer.class);
        createCustomerAdministrator(
                savedCustomerAdministrator.getTenantId(),
                savedSubCustomer.getId(),
                SUB_CUSTOMER_ADMIN_EMAIL,
                SUB_CUSTOMER_ADMIN_PASSWORD
        );

        login(SUB_CUSTOMER_ADMIN_EMAIL, SUB_CUSTOMER_ADMIN_PASSWORD);

        Device device = new Device();
        device.setName("sub customer device");
        device.setLabel("Label");
        device.setType("Type");
        Device savedDevice = doPost("/api/device", device, Device.class);

        Alarm alarm = createAlarm(
                savedSubCustomer,
                savedDevice.getId(),
                TEST_ALARM_TYPE
        );

        loginCustomerAdministrator();

        var response = doGetTyped(
                "/api/alarm/" + savedDevice.getEntityType() + "/" + savedDevice.getUuidId() + "?page=0&pageSize=1",
                new TypeReference<PageData<AlarmInfo>>() {}
        );
        var pageData = response.getData();
        Assert.assertNotNull("Found pageData is null", pageData);
        Assert.assertNotEquals("Expected alarms are not found!", 0, pageData.size());

        AlarmInfo alarmInfo = pageData.get(0);
        boolean equals = alarm.getId().equals(alarmInfo.getId()) && alarm.getType().equals(alarmInfo.getType());
        Assert.assertTrue("Created alarm doesn't match the found one!", equals);


        loginTenantAdmin();

        response = doGetTyped(
                "/api/alarm/" + savedDevice.getEntityType() + "/" + savedDevice.getUuidId() + "?page=0&pageSize=1",
                new TypeReference<PageData<AlarmInfo>>() {}
        );
        pageData = response.getData();
        Assert.assertNotNull("Found pageData is null", pageData);
        Assert.assertNotEquals("Expected alarms are not found!", 0, pageData.size());

        alarmInfo = pageData.get(0);
        equals = alarm.getId().equals(alarmInfo.getId()) && alarm.getType().equals(alarmInfo.getType());
        Assert.assertTrue("Created alarm doesn't match the found one!", equals);
    }

    @Test
    public void testSubCustomersSubCustomerAlarmsCanBeFoundByParentCustomer() throws Exception{
        loginCustomerAdministrator();

        Customer subCustomer = new Customer();
        subCustomer.setParentCustomerId(customerId);
        subCustomer.setTitle("Sub Customer");

        Customer savedSubCustomer = doPost("/api/customer", subCustomer, Customer.class);
        createCustomerAdministrator(
                savedCustomerAdministrator.getTenantId(),
                savedSubCustomer.getId(),
                SUB_CUSTOMER_ADMIN_EMAIL,
                SUB_CUSTOMER_ADMIN_PASSWORD
        );

        login(SUB_CUSTOMER_ADMIN_EMAIL, SUB_CUSTOMER_ADMIN_PASSWORD);

        Customer subSubCustomer = new Customer();
        subSubCustomer.setParentCustomerId(savedSubCustomer.getId());
        subSubCustomer.setTitle("Sub sub Customer");

        Customer savedSubSubCustomer = doPost("/api/customer", subSubCustomer, Customer.class);
        createCustomerAdministrator(
                savedCustomerAdministrator.getTenantId(),
                savedSubSubCustomer.getId(),
                SUB_SUB_CUSTOMER_ADMIN_EMAIL,
                SUB_SUB_CUSTOMER_ADMIN_PASSWORD
        );

        login(SUB_SUB_CUSTOMER_ADMIN_EMAIL, SUB_SUB_CUSTOMER_ADMIN_PASSWORD);

        Device device = new Device();
        device.setName("sub sub customer device");
        device.setLabel("Label");
        device.setType("Type");
        Device savedDevice = doPost("/api/device", device, Device.class);

        Alarm alarm = createAlarm(
                savedSubSubCustomer,
                savedDevice.getId(),
                TEST_ALARM_TYPE
        );

        login(SUB_CUSTOMER_ADMIN_EMAIL, SUB_CUSTOMER_ADMIN_PASSWORD);

        var response = doGetTyped(
                "/api/alarm/" + savedDevice.getEntityType() + "/" + savedDevice.getUuidId() + "?page=0&pageSize=1",
                new TypeReference<PageData<AlarmInfo>>() {}
        );
        var pageData = response.getData();
        Assert.assertNotNull("Found pageData is null", pageData);
        Assert.assertNotEquals("Expected alarms are not found!", 0, pageData.size());

        AlarmInfo alarmInfo = pageData.get(0);
        boolean equals = alarm.getId().equals(alarmInfo.getId()) && alarm.getType().equals(alarmInfo.getType());
        Assert.assertTrue("Created alarm doesn't match the found one!", equals);

        loginCustomerAdministrator();

        response = doGetTyped(
                "/api/alarm/" + savedDevice.getEntityType() + "/" + savedDevice.getUuidId() + "?page=0&pageSize=1",
                new TypeReference<PageData<AlarmInfo>>() {}
        );
        pageData = response.getData();
        Assert.assertNotNull("Found pageData is null", pageData);
        Assert.assertNotEquals("Expected alarms are not found!", 0, pageData.size());

        alarmInfo = pageData.get(0);
        equals = alarm.getId().equals(alarmInfo.getId()) && alarm.getType().equals(alarmInfo.getType());
        Assert.assertTrue("Created alarm doesn't match the found one!", equals);


        loginTenantAdmin();

        response = doGetTyped(
                "/api/alarm/" + savedDevice.getEntityType() + "/" + savedDevice.getUuidId() + "?page=0&pageSize=1",
                new TypeReference<PageData<AlarmInfo>>() {}
        );
        pageData = response.getData();
        Assert.assertNotNull("Found pageData is null", pageData);
        Assert.assertNotEquals("Expected alarms are not found!", 0, pageData.size());

        alarmInfo = pageData.get(0);
        equals = alarm.getId().equals(alarmInfo.getId()) && alarm.getType().equals(alarmInfo.getType());
        Assert.assertTrue("Created alarm doesn't match the found one!", equals);
    }

    @Test
    public void testFindAlarmsViaPublicCustomer() throws Exception {
        loginCustomerAdministrator();

        EntityGroupInfo deviceGroup = createSharedPublicEntityGroup(
                "Device Test Entity Group",
                EntityType.DEVICE,
                customerId
        );
        String publicId = deviceGroup.getAdditionalInfo().get("publicCustomerId").asText();

        Device device = new Device();
        device.setName("Test Public Device");
        device.setLabel("Label");
        device.setCustomerId(customerId);
        device = doPost("/api/device?entityGroupId=" + deviceGroup.getUuidId(), device, Device.class);


        Alarm alarm = Alarm.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .originator(device.getId())
                .status(AlarmStatus.ACTIVE_UNACK)
                .severity(AlarmSeverity.CRITICAL)
                .type("Test")
                .build();
        alarm = doPost("/api/alarm", alarm, Alarm.class);
        Assert.assertNotNull("Saved alarm is null!", alarm);

        logout();

        JsonNode publicLoginRequest = JacksonUtil.toJsonNode("{\"publicId\": \"" + publicId + "\"}");
        JsonNode tokens = doPost("/api/auth/login/public", publicLoginRequest, JsonNode.class);
        this.token = tokens.get("token").asText();

        PageData<AlarmInfo> pageData = doGetTyped(
                "/api/alarm/DEVICE/" + device.getUuidId() + "?page=0&pageSize=1", new TypeReference<PageData<AlarmInfo>>() {}
        );

        Assert.assertNotNull("Found pageData is null", pageData);
        Assert.assertNotEquals("Expected alarms are not found!", 0, pageData.getTotalElements());

        AlarmInfo alarmInfo = pageData.getData().get(0);
        boolean equals = alarm.getId().equals(alarmInfo.getId()) && alarm.getType().equals(alarmInfo.getType());
        Assert.assertTrue("Created alarm doesn't match the found one!", equals);
    }

    @NotNull
    private EntityGroupInfo createSharedPublicEntityGroup(String name, EntityType entityType, EntityId ownerId) throws Exception {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName(name);
        entityGroup.setType(entityType);
        EntityGroupInfo groupInfo =
                doPostWithResponse("/api/entityGroup", entityGroup, EntityGroupInfo.class);

        ShareGroupRequest groupRequest = new ShareGroupRequest(
                ownerId,
                true,
                null,
                true,
                null
        );

        doPost("/api/entityGroup/" + groupInfo.getId() + "/share", groupRequest)
                .andExpect(status().isOk());

        doPost("/api/entityGroup/" + groupInfo.getId() + "/makePublic")
                .andExpect(status().isOk());
        return doGet("/api/entityGroup/" + groupInfo.getUuidId(), EntityGroupInfo.class);
    }

    private Alarm createAlarm(String type) throws Exception {
        Alarm alarm = Alarm.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .originator(customerDevice.getId())
                .status(AlarmStatus.ACTIVE_UNACK)
                .severity(AlarmSeverity.CRITICAL)
                .type(type)
                .build();

        alarm = doPost("/api/alarm", alarm, Alarm.class);
        Assert.assertNotNull(alarm);

        return alarm;
    }

    private Alarm createAlarm(Customer customer, EntityId originatorId, String type) throws Exception {
        Alarm alarm = Alarm.builder()
                .tenantId(customer.getTenantId())
                .customerId(customer.getId())
                .originator(originatorId)
                .status(AlarmStatus.ACTIVE_UNACK)
                .severity(AlarmSeverity.CRITICAL)
                .type(type)
                .build();

        alarm = doPost("/api/alarm", alarm, Alarm.class);
        Assert.assertNotNull(alarm);

        return alarm;
    }

    private ResultActions createAlarmAndReturnAction(String type) throws Exception {
        Alarm alarm = Alarm.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .originator(customerDevice.getId())
                .status(AlarmStatus.ACTIVE_UNACK)
                .severity(AlarmSeverity.CRITICAL)
                .type(type)
                .build();

        return doPost("/api/alarm", alarm);
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

    private User savedCustomerAdministrator;
    private User savedDifferentCustomerAdministrator;

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

    private void loginDifferentCustomerAdministrator() throws Exception {
        if (savedDifferentCustomerAdministrator == null) {
            if (differentCustomerId == null) {
                createDifferentCustomer();
            }

            savedDifferentCustomerAdministrator = createCustomerAdministrator(
                    tenantId,
                    differentCustomerId,
                    DIFFERENT_CUSTOMER_ADMIN_EMAIL,
                    DIFFERENT_CUSTOMER_ADMIN_PASSWORD
            );
        }
        login(savedDifferentCustomerAdministrator.getEmail(), DIFFERENT_CUSTOMER_ADMIN_PASSWORD);
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
        logout();

        return user;
    }
}
