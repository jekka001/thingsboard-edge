/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.security.Authority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class BaseDashboardControllerTest extends AbstractControllerTest {

    private IdComparator<DashboardInfo> idComparator = new IdComparator<>();

    private Tenant savedTenant;
    private User tenantAdmin;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
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

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveDashboard() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);

        Assert.assertNotNull(savedDashboard);
        Assert.assertNotNull(savedDashboard.getId());
        Assert.assertTrue(savedDashboard.getCreatedTime() > 0);
        Assert.assertEquals(savedTenant.getId(), savedDashboard.getTenantId());
        Assert.assertEquals(dashboard.getTitle(), savedDashboard.getTitle());

        savedDashboard.setTitle("My new dashboard");
        doPost("/api/dashboard", savedDashboard, Dashboard.class);

        Dashboard foundDashboard = doGet("/api/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertEquals(foundDashboard.getTitle(), savedDashboard.getTitle());
    }

    @Test
    public void testUpdateDashboardFromDifferentTenant() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);

        loginDifferentTenant();
        doPost("/api/dashboard", savedDashboard, Dashboard.class, status().isForbidden());
        deleteDifferentTenant();
    }
    
    @Test
    public void testFindDashboardById() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);
        Dashboard foundDashboard = doGet("/api/dashboard/" + savedDashboard.getId().getId().toString(), Dashboard.class);
        Assert.assertNotNull(foundDashboard);
        Assert.assertEquals(savedDashboard, foundDashboard);
    }

    @Test
    public void testDeleteDashboard() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("My dashboard");
        Dashboard savedDashboard = doPost("/api/dashboard", dashboard, Dashboard.class);

        doDelete("/api/dashboard/" + savedDashboard.getId().getId().toString())
                .andExpect(status().isOk());

        doGet("/api/dashboard/" + savedDashboard.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testSaveDashboardWithEmptyTitle() throws Exception {
        Dashboard dashboard = new Dashboard();
        doPost("/api/dashboard", dashboard)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Dashboard title should be specified")));
    }

    @Test
    public void testFindTenantDashboards() throws Exception {
        List<DashboardInfo> dashboards = new ArrayList<>();
        for (int i = 0; i < 173; i++) {
            Dashboard dashboard = new Dashboard();
            dashboard.setTitle("Dashboard" + i);
            dashboards.add(new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class)));
        }
        List<DashboardInfo> loadedDashboards = new ArrayList<>();
        PageLink pageLink = new PageLink(24);
        PageData<DashboardInfo> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?",
                    new TypeReference<PageData<DashboardInfo>>() {
                    }, pageLink);
            loadedDashboards.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(dashboards, idComparator);
        Collections.sort(loadedDashboards, idComparator);

        Assert.assertEquals(dashboards, loadedDashboards);
    }

    @Test
    public void testFindTenantDashboardsByTitle() throws Exception {
        String title1 = "Dashboard title 1";
        List<DashboardInfo> dashboardsTitle1 = new ArrayList<>();
        for (int i = 0; i < 134; i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int) (Math.random() * 15));
            String title = title1 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboardsTitle1.add(new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class)));
        }
        String title2 = "Dashboard title 2";
        List<DashboardInfo> dashboardsTitle2 = new ArrayList<>();
        for (int i = 0; i < 112; i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int) (Math.random() * 15));
            String title = title2 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboardsTitle2.add(new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class)));
        }

        List<DashboardInfo> loadedDashboardsTitle1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<DashboardInfo> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?",
                    new TypeReference<PageData<DashboardInfo>>() {
                    }, pageLink);
            loadedDashboardsTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(dashboardsTitle1, idComparator);
        Collections.sort(loadedDashboardsTitle1, idComparator);

        Assert.assertEquals(dashboardsTitle1, loadedDashboardsTitle1);

        List<DashboardInfo> loadedDashboardsTitle2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?",
                    new TypeReference<PageData<DashboardInfo>>() {
                    }, pageLink);
            loadedDashboardsTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(dashboardsTitle2, idComparator);
        Collections.sort(loadedDashboardsTitle2, idComparator);

        Assert.assertEquals(dashboardsTitle2, loadedDashboardsTitle2);

        for (DashboardInfo dashboard : loadedDashboardsTitle1) {
            doDelete("/api/dashboard/" + dashboard.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/tenant/dashboards?",
                new TypeReference<PageData<DashboardInfo>>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (DashboardInfo dashboard : loadedDashboardsTitle2) {
            doDelete("/api/dashboard/" + dashboard.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/tenant/dashboards?",
                new TypeReference<PageData<DashboardInfo>>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void testFindCustomerUserDashboards() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        customer.setTenantId(savedTenant.getTenantId());
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        EntityGroup customerUserGroup = new EntityGroup();
        customerUserGroup.setType(EntityType.USER);
        customerUserGroup.setName("Customer User Group");
        customerUserGroup.setOwnerId(savedCustomer.getOwnerId());
        customerUserGroup = doPost("/api/entityGroup", customerUserGroup, EntityGroup.class);

        EntityGroup tenantDashboardGroup = new EntityGroup();
        tenantDashboardGroup.setType(EntityType.DASHBOARD);
        tenantDashboardGroup.setName("Tenant Dashboard Group");
        tenantDashboardGroup = doPost("/api/entityGroup", tenantDashboardGroup, EntityGroup.class);

        Role groupRole = new Role();
        groupRole.setTenantId(savedTenant.getId());
        groupRole.setName("Read Group Role");
        groupRole.setType(RoleType.GROUP);
        ArrayNode readPermissions = mapper.createArrayNode();
        readPermissions.add("READ");
        groupRole.setPermissions(readPermissions);
        groupRole = doPost("/api/role", groupRole, Role.class);

        GroupPermission readTenantDashboardGroupPermission = new GroupPermission();
        readTenantDashboardGroupPermission.setRoleId(groupRole.getId());
        readTenantDashboardGroupPermission.setUserGroupId(customerUserGroup.getId());
        readTenantDashboardGroupPermission.setEntityGroupId(tenantDashboardGroup.getId());
        readTenantDashboardGroupPermission.setEntityGroupType(tenantDashboardGroup.getType());
        doPost("/api/groupPermission", readTenantDashboardGroupPermission, GroupPermission.class);

        Role genericRole = new Role();
        genericRole.setTenantId(savedTenant.getId());
        genericRole.setName("Read Generic Role");
        genericRole.setType(RoleType.GENERIC);
        ObjectNode genericPermissions = mapper.createObjectNode();
        genericPermissions.set("ALL", readPermissions);
        genericRole.setPermissions(genericPermissions);
        genericRole = doPost("/api/role", genericRole, Role.class);

        GroupPermission genericPermission = new GroupPermission();
        genericPermission.setRoleId(genericRole.getId());
        genericPermission.setUserGroupId(customerUserGroup.getId());
        doPost("/api/groupPermission", genericPermission, GroupPermission.class);

        Dashboard dashboard = new Dashboard();
        dashboard.setTitle("Tenant Dashboard");
        new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class));

        Dashboard sharedDashboard = new Dashboard();
        sharedDashboard.setTitle("Shared Dashboard");
        new DashboardInfo(doPost("/api/dashboard?entityGroupId={entityGroupId}", sharedDashboard, Dashboard.class, tenantDashboardGroup.getId().getId().toString()));

        List<DashboardInfo> tenantAdminDashboards = new ArrayList<>();
        PageLink pageLink = new PageLink(100);
        PageData<DashboardInfo> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/user/dashboards?",
                    new TypeReference<PageData<DashboardInfo>>() {
                    }, pageLink);
            tenantAdminDashboards.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        // Tenant admin user must have access to both dashboards
        Assert.assertEquals(2, tenantAdminDashboards.size());

        User customerUser = new User();
        customerUser.setAuthority(Authority.CUSTOMER_USER);
        customerUser.setTenantId(savedTenant.getId());
        customerUser.setCustomerId(savedCustomer.getId());
        customerUser.setOwnerId(savedCustomer.getId());
        customerUser.setEmail("customerUser@thingsboard.org");
        User savedUser = doPost("/api/user?entityGroupId={entityGroupId}", customerUser, User.class, customerUserGroup.getId().getId().toString());

        List<DashboardInfo> customerUserDashboards = new ArrayList<>();
        do {
            pageData = doGetTypedWithPageLink("/api/user/dashboards?userId={userId}&",
                    new TypeReference<PageData<DashboardInfo>>() {
                    }, pageLink, savedUser.getId().getId().toString());
            customerUserDashboards.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        // Customer user must have access only to a shared dashboard
        Assert.assertEquals(1, customerUserDashboards.size());
    }
}
