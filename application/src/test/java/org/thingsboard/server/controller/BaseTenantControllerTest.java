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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantInfo;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public abstract class BaseTenantControllerTest extends AbstractControllerTest {

    static final TypeReference<PageData<Tenant>> PAGE_DATA_TENANT_TYPE_REF = new TypeReference<>(){};
    static final TypeReference<PageData<TenantInfo>> PAGE_DATA_TENANT_INFO_TYPE_REF = new TypeReference<>(){};
    static final int TIMEOUT = 30;

    List<ListenableFuture<ResultActions>> deleteFutures = new ArrayList<>();
    ListeningExecutorService executor;

    @Before
    public void setUp() throws Exception {
        executor = MoreExecutors.listeningDecorator(ThingsBoardExecutors.newWorkStealingPool(8, getClass()));
    }

    @After
    public void tearDown() throws Exception {
        executor.shutdownNow();
    }

    @Test
    public void testSaveTenant() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        Tenant savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        Assert.assertNotNull(savedTenant);
        Assert.assertNotNull(savedTenant.getId());
        Assert.assertTrue(savedTenant.getCreatedTime() > 0);
        Assert.assertEquals(tenant.getTitle(), savedTenant.getTitle());
        savedTenant.setTitle("My new tenant");
        doPost("/api/tenant", savedTenant, Tenant.class);
        Tenant foundTenant = doGet("/api/tenant/" + savedTenant.getId().getId().toString(), Tenant.class);
        Assert.assertEquals(foundTenant.getTitle(), savedTenant.getTitle());
        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveTenantWithViolationOfValidation() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        tenant.setTitle(RandomStringUtils.randomAlphanumeric(300));
        doPost("/api/tenant", tenant).andExpect(statusReason(containsString("length of title must be equal or less than 255")));
    }

    @Test
    public void testFindTenantById() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        Tenant savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        Tenant foundTenant = doGet("/api/tenant/" + savedTenant.getId().getId().toString(), Tenant.class);
        Assert.assertNotNull(foundTenant);
        Assert.assertEquals(savedTenant, foundTenant);
        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testFindTenantInfoById() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        Tenant savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        TenantInfo foundTenant = doGet("/api/tenant/info/" + savedTenant.getId().getId().toString(), TenantInfo.class);
        Assert.assertNotNull(foundTenant);
        Assert.assertEquals(new TenantInfo(savedTenant, "Default"), foundTenant);
        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveTenantWithEmptyTitle() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        doPost("/api/tenant", tenant)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Tenant title should be specified")));
    }

    @Test
    public void testSaveTenantWithInvalidEmail() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        tenant.setEmail("invalid@mail");
        doPost("/api/tenant", tenant)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Invalid email address format")));
    }

    @Test
    public void testDeleteTenant() throws Exception {
        loginSysAdmin();
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        Tenant savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
        doGet("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testFindTenants() throws Exception {
        loginSysAdmin();
        List<Tenant> tenants = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<Tenant> pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getData().size());
        tenants.addAll(pageData.getData());

        List<ListenableFuture<Tenant>> createFutures = new ArrayList<>(56);
        for (int i = 0; i < 56; i++) {
            Tenant tenant = new Tenant();
            tenant.setTitle("Tenant" + i);
            createFutures.add(executor.submit(() ->
                    doPost("/api/tenant", tenant, Tenant.class)));
        }
        tenants.addAll(Futures.allAsList(createFutures).get(TIMEOUT, TimeUnit.SECONDS));

        List<Tenant> loadedTenants = new ArrayList<>();
        pageLink = new PageLink(17);
        do {
            pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
            loadedTenants.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(tenants).containsExactlyInAnyOrderElementsOf(loadedTenants);

        for (Tenant tenant : loadedTenants) {
            if (!tenant.getTitle().equals(TEST_TENANT_NAME)) {
                deleteFutures.add(executor.submit(() ->
                        doDelete("/api/tenant/" + tenant.getId().getId().toString())
                                .andExpect(status().isOk())));
            }
        }
        Futures.allAsList(deleteFutures).get(TIMEOUT, TimeUnit.SECONDS);

        pageLink = new PageLink(17);
        pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getData().size());
    }

    @Test
    public void testFindTenantsByTitle() throws Exception {
        log.debug("login sys admin");
        loginSysAdmin();
        log.debug("test started");
        String title1 = "Tenant title 1";
        List<ListenableFuture<Tenant>> createFutures = new ArrayList<>(134);
        for (int i = 0; i < 134; i++) {
            Tenant tenant = new Tenant();
            String suffix = RandomStringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String title = title1 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            tenant.setTitle(title);
            createFutures.add(executor.submit(() ->
                    doPost("/api/tenant", tenant, Tenant.class)));
        }

        List<Tenant> tenantsTitle1 = Futures.allAsList(createFutures).get(TIMEOUT, TimeUnit.SECONDS);
        log.debug("saved '{}', qty {}", title1, tenantsTitle1.size());

        String title2 = "Tenant title 2";
        createFutures = new ArrayList<>(127);
        for (int i = 0; i < 127; i++) {
            Tenant tenant = new Tenant();
            String suffix = RandomStringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String title = title2 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            tenant.setTitle(title);
            createFutures.add(executor.submit(() ->
                    doPost("/api/tenant", tenant, Tenant.class)));
        }

        List<Tenant> tenantsTitle2 = Futures.allAsList(createFutures).get(TIMEOUT, TimeUnit.SECONDS);
        log.debug("saved '{}', qty {}", title2, tenantsTitle2.size());

        List<Tenant> loadedTenantsTitle1 = new ArrayList<>(134);
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Tenant> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
            loadedTenantsTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        log.debug("found by name '{}', step 15 {}", title1, loadedTenantsTitle1.size());

        assertThat(tenantsTitle1).as(title1).containsExactlyInAnyOrderElementsOf(loadedTenantsTitle1);
        log.debug("asserted");

        List<Tenant> loadedTenantsTitle2 = new ArrayList<>(127);
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
            loadedTenantsTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        log.debug("found by name '{}', step 4 {}", title1, loadedTenantsTitle2.size());
        assertThat(tenantsTitle2).as(title2).containsExactlyInAnyOrderElementsOf(loadedTenantsTitle2);
        log.debug("asserted");

        deleteFutures.clear();
        for (Tenant tenant : loadedTenantsTitle1) {
            deleteFutures.add(executor.submit(() ->
                    doDelete("/api/tenant/" + tenant.getId().getId().toString())
                            .andExpect(status().isOk())));
        }

        Futures.allAsList(deleteFutures).get(TIMEOUT, TimeUnit.SECONDS);
        log.debug("deleted '{}', size {}", title1, loadedTenantsTitle1.size());

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        log.debug("tried to search another '{}', step 4", title1);

        deleteFutures.clear();
        for (Tenant tenant : loadedTenantsTitle2) {
            deleteFutures.add(executor.submit(() ->
                    doDelete("/api/tenant/" + tenant.getId().getId().toString())
                            .andExpect(status().isOk())));
        }

        Futures.allAsList(deleteFutures).get(TIMEOUT, TimeUnit.SECONDS);
        log.debug("deleted '{}', size {}", title2, loadedTenantsTitle2.size());

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/tenants?", PAGE_DATA_TENANT_TYPE_REF, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
        log.debug("tried to search another '{}', step 4", title2);
    }

    @Test
    public void testFindTenantInfos() throws Exception {
        loginSysAdmin();
        List<TenantInfo> tenants = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<TenantInfo> pageData = doGetTypedWithPageLink("/api/tenantInfos?", PAGE_DATA_TENANT_INFO_TYPE_REF, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getData().size());
        tenants.addAll(pageData.getData());

        List<ListenableFuture<TenantInfo>> createFutures = new ArrayList<>(56);
        for (int i = 0; i < 56; i++) {
            Tenant tenant = new Tenant();
            tenant.setTitle("Tenant" + i);
            createFutures.add(executor.submit(() ->
                    new TenantInfo(doPost("/api/tenant", tenant, Tenant.class), "Default")));
        }
        tenants.addAll(Futures.allAsList(createFutures).get(TIMEOUT, TimeUnit.SECONDS));

        List<TenantInfo> loadedTenants = new ArrayList<>();
        pageLink = new PageLink(17);
        do {
            pageData = doGetTypedWithPageLink("/api/tenantInfos?", PAGE_DATA_TENANT_INFO_TYPE_REF, pageLink);
            loadedTenants.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(tenants).containsExactlyInAnyOrderElementsOf(loadedTenants);

        for (TenantInfo tenant : loadedTenants) {
            if (!tenant.getTitle().equals(TEST_TENANT_NAME)) {
                deleteFutures.add(executor.submit(() ->
                        doDelete("/api/tenant/" + tenant.getId().getId().toString())
                                .andExpect(status().isOk())));
            }
        }
        Futures.allAsList(deleteFutures).get(TIMEOUT, TimeUnit.SECONDS);

        pageLink = new PageLink(17);
        pageData = doGetTypedWithPageLink("/api/tenantInfos?", PAGE_DATA_TENANT_INFO_TYPE_REF, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getData().size());
    }
}
