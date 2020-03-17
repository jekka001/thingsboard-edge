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

import static org.hamcrest.Matchers.containsString;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.datastax.driver.core.utils.UUIDs;
import org.apache.commons.lang3.RandomStringUtils;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.model.ModelConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;

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
        
        doDelete("/api/tenant/"+savedTenant.getId().getId().toString())
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
        
        doDelete("/api/dashboard/"+savedDashboard.getId().getId().toString())
        .andExpect(status().isOk());

        doGet("/api/dashboard/"+savedDashboard.getId().getId().toString())
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
        for (int i=0;i<173;i++) {
            Dashboard dashboard = new Dashboard();
            dashboard.setTitle("Dashboard"+i);
            dashboards.add(new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class)));
        }
        List<DashboardInfo> loadedDashboards = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(24);
        TextPageData<DashboardInfo> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                    new TypeReference<TextPageData<DashboardInfo>>(){}, pageLink);
            loadedDashboards.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
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
        for (int i=0;i<134;i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int)(Math.random()*15));
            String title = title1+suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboardsTitle1.add(new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class)));
        }
        String title2 = "Dashboard title 2";
        List<DashboardInfo> dashboardsTitle2 = new ArrayList<>();
        for (int i=0;i<112;i++) {
            Dashboard dashboard = new Dashboard();
            String suffix = RandomStringUtils.randomAlphanumeric((int)(Math.random()*15));
            String title = title2+suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            dashboard.setTitle(title);
            dashboardsTitle2.add(new DashboardInfo(doPost("/api/dashboard", dashboard, Dashboard.class)));
        }
        
        List<DashboardInfo> loadedDashboardsTitle1 = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(15, title1);
        TextPageData<DashboardInfo> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                    new TypeReference<TextPageData<DashboardInfo>>(){}, pageLink);
            loadedDashboardsTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());
        
        Collections.sort(dashboardsTitle1, idComparator);
        Collections.sort(loadedDashboardsTitle1, idComparator);
        
        Assert.assertEquals(dashboardsTitle1, loadedDashboardsTitle1);
        
        List<DashboardInfo> loadedDashboardsTitle2 = new ArrayList<>();
        pageLink = new TextPageLink(4, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                    new TypeReference<TextPageData<DashboardInfo>>(){}, pageLink);
            loadedDashboardsTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(dashboardsTitle2, idComparator);
        Collections.sort(loadedDashboardsTitle2, idComparator);
        
        Assert.assertEquals(dashboardsTitle2, loadedDashboardsTitle2);
        
        for (DashboardInfo dashboard : loadedDashboardsTitle1) {
            doDelete("/api/dashboard/"+dashboard.getId().getId().toString())
            .andExpect(status().isOk());
        }
        
        pageLink = new TextPageLink(4, title1);
        pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                new TypeReference<TextPageData<DashboardInfo>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
        
        for (DashboardInfo dashboard : loadedDashboardsTitle2) {
            doDelete("/api/dashboard/"+dashboard.getId().getId().toString())
            .andExpect(status().isOk());
        }
        
        pageLink = new TextPageLink(4, title2);
        pageData = doGetTypedWithPageLink("/api/tenant/dashboards?", 
                new TypeReference<TextPageData<DashboardInfo>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

}
