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
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class BaseCustomerControllerTest extends AbstractControllerTest {
    static final TypeReference<PageData<Customer>> PAGE_DATA_CUSTOMER_TYPE_REFERENCE = new TypeReference<>() {
    };

    ListeningExecutorService executor;

    private Tenant savedTenant;
    private User tenantAdmin;

    @Before
    public void beforeTest() throws Exception {
        executor = MoreExecutors.listeningDecorator(ThingsBoardExecutors.newWorkStealingPool(8, getClass()));

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
        executor.shutdownNow();

        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");

        Mockito.reset(tbClusterService, auditLogService);

        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        testNotifyEntityOneTimeMsgToEdgeServiceNever(savedCustomer, savedCustomer.getId(), savedCustomer.getId(),
                savedCustomer.getTenantId(), tenantAdmin.getCustomerId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED);

        Assert.assertNotNull(savedCustomer);
        Assert.assertNotNull(savedCustomer.getId());
        Assert.assertTrue(savedCustomer.getCreatedTime() > 0);
        Assert.assertEquals(customer.getTitle(), savedCustomer.getTitle());

        savedCustomer.setTitle("My new customer");

        doPost("/api/customer", savedCustomer, Customer.class);

        testNotifyEntityEntityGroupNullAllOneTime(savedCustomer, savedCustomer.getId(), savedCustomer.getId(), savedCustomer.getTenantId(),
                new CustomerId(CustomerId.NULL_UUID), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.UPDATED);

        Customer foundCustomer = doGet("/api/customer/" + savedCustomer.getId().getId().toString(), Customer.class);
        Assert.assertEquals(foundCustomer.getTitle(), savedCustomer.getTitle());

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveCustomerWithViolationOfValidation() throws Exception {
        Customer customer = new Customer();
        customer.setTitle(RandomStringUtils.randomAlphabetic(300));

        Mockito.reset(tbClusterService, auditLogService);

        String msgError = "length of title must be equal or less than 255";
        doPost("/api/customer", customer).andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        customer.setTitle("Normal title");
        customer.setCity(RandomStringUtils.randomAlphabetic(300));
        msgError = "length of city must be equal or less than 255";

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", customer).andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        customer.setCity("Normal city");
        customer.setCountry(RandomStringUtils.randomAlphabetic(300));
        msgError = "length of country must be equal or less than 255";
        doPost("/api/customer", customer).andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        customer.setCountry("Ukraine");
        customer.setPhone(RandomStringUtils.randomAlphabetic(300));
        msgError = "length of phone must be equal or less than 255";
        doPost("/api/customer", customer).andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        customer.setPhone("+3892555554512");
        customer.setState(RandomStringUtils.randomAlphabetic(300));
        msgError = "length of state must be equal or less than 255";
        doPost("/api/customer", customer).andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        customer.setState("Normal state");
        customer.setZip(RandomStringUtils.randomAlphabetic(300));
        msgError = "length of zip or postal code must be equal or less than 255";
        doPost("/api/customer", customer).andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
     }

    @Test
    public void testUpdateCustomerFromDifferentTenant() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        doPost("/api/customer", savedCustomer, Customer.class);

        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        String msgError = "You don't have permission to perform 'WRITE' operation with CUSTOMER 'My customer'";
        doPost("/api/customer", savedCustomer, Customer.class, status().isForbidden());

        testNotifyEntityEqualsOneTimeError(savedCustomer,  differentTenantId, differentTenantAdminUserId, DIFFERENT_TENANT_ADMIN_EMAIL,
                ActionType.UPDATED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));

        deleteDifferentTenant();
        login(tenantAdmin.getName(), "testPassword1");

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());

    }

    @Test
    public void testFindCustomerById() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Customer foundCustomer = doGet("/api/customer/" + savedCustomer.getId().getId().toString(), Customer.class);
        Assert.assertNotNull(foundCustomer);
        Assert.assertEquals(savedCustomer, foundCustomer);

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testDeleteCustomer() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());

        testNotifyEntityOneBroadcastEntityStateChangeEventTimeMsgToEdgeServiceNever(savedCustomer, savedCustomer.getId(),
                savedCustomer.getId(), savedCustomer.getTenantId(), savedCustomer.getId(), tenantAdmin.getId(),
                tenantAdmin.getEmail(), ActionType.DELETED, savedCustomer.getId().getId().toString());

        doGet("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testSaveCustomerWithEmptyTitle() throws Exception {
        Customer customer = new Customer();
        String msgError = "Customer title should be specified";

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", customer)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testSaveCustomerWithInvalidEmail() throws Exception {
        Customer customer = new Customer();
        String msgError = "Invalid email address format 'invalid@mail'";
        customer.setTitle("My customer");
        customer.setEmail("invalid@mail");

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer", customer)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new ThingsboardException(msgError, ThingsboardErrorCode.PERMISSION_DENIED));
        testNotifyEntityEqualsOneTimeError(customer, savedTenant.getId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testFindCustomers() throws Exception {
        TenantId tenantId = savedTenant.getId();

        int cntEntity = 135;

        Mockito.reset(tbClusterService, auditLogService);

        List<ListenableFuture<Customer>> futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            Customer customer = new Customer();
            customer.setTenantId(tenantId);
            customer.setTitle("Customer" + i);
            futures.add(executor.submit(() ->
                    doPost("/api/customer", customer, Customer.class)));
        }
        List<Customer> customers = Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS);

        testNotifyManyEntityManyTimeMsgToEdgeServiceNever(new Customer(), new Customer(),
                tenantId, tenantAdmin.getCustomerId(), tenantAdmin.getId(), tenantAdmin.getEmail(),
                ActionType.ADDED, cntEntity);

        List<Customer> loadedCustomers = new ArrayList<>(135);
        PageLink pageLink = new PageLink(23);
        PageData<Customer> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
            loadedCustomers.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(customers).containsExactlyInAnyOrderElementsOf(loadedCustomers);

        deleteEntitiesAsync("/api/customer/", loadedCustomers, executor).get(TIMEOUT, TimeUnit.SECONDS);
    }

    @Test
    public void testFindCustomersByTitle() throws Exception {
        TenantId tenantId = savedTenant.getId();

        String title1 = "Customer title 1";
        List<ListenableFuture<Customer>> futures = new ArrayList<>(143);
        for (int i = 0; i < 143; i++) {
            Customer customer = new Customer();
            customer.setTenantId(tenantId);
            String suffix = RandomStringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String title = title1 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            customer.setTitle(title);
            futures.add(executor.submit(() ->
                    doPost("/api/customer", customer, Customer.class)));
        }
        List<Customer> customersTitle1 = Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS);

        String title2 = "Customer title 2";
        futures = new ArrayList<>(175);
        for (int i = 0; i < 175; i++) {
            Customer customer = new Customer();
            customer.setTenantId(tenantId);
            String suffix = RandomStringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String title = title2 + suffix;
            title = i % 2 == 0 ? title.toLowerCase() : title.toUpperCase();
            customer.setTitle(title);
            futures.add(executor.submit(() ->
                    doPost("/api/customer", customer, Customer.class)));
        }

        List<Customer> customersTitle2 = Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS);

        List<Customer> loadedCustomersTitle1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Customer> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
            loadedCustomersTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(customersTitle1).as(title1).containsExactlyInAnyOrderElementsOf(loadedCustomersTitle1);

        List<Customer> loadedCustomersTitle2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
            loadedCustomersTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        assertThat(customersTitle2).as(title2).containsExactlyInAnyOrderElementsOf(loadedCustomersTitle2);

        deleteEntitiesAsync("/api/customer/", loadedCustomersTitle1, executor).get(TIMEOUT, TimeUnit.SECONDS);

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        deleteEntitiesAsync("/api/customer/", loadedCustomersTitle2, executor).get(TIMEOUT, TimeUnit.SECONDS);

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/customers?", PAGE_DATA_CUSTOMER_TYPE_REFERENCE, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }
}
