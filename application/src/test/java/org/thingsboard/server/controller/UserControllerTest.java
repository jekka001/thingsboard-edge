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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.ResultActions;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.UserEmailInfo;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.settings.StarredDashboardInfo;
import org.thingsboard.server.common.data.settings.UserDashboardsInfo;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.dao.user.UserDao;
import org.thingsboard.server.exception.DataValidationException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.server.dao.model.ModelConstants.SYSTEM_TENANT;

@ContextConfiguration(classes = {UserControllerTest.Config.class})
@DaoSqlTest
public class UserControllerTest extends AbstractControllerTest {

    private IdComparator<User> idComparator = new IdComparator<>();
    private IdComparator<UserEmailInfo> userDataIdComparator = new IdComparator<>();

    private EntityIdComparator<UserId> userIdComparator = new EntityIdComparator<>();

    private CustomerId customerNUULId = (CustomerId) createEntityId_NULL_UUID(new Customer());

    @Autowired
    private UserDao userDao;

    @Autowired
    private DeviceService deviceService;

    static class Config {
        @Bean
        @Primary
        public UserDao userDao(UserDao userDao) {
            return Mockito.mock(UserDao.class, AdditionalAnswers.delegatesTo(userDao));
        }
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();
    }

    @Test
    public void testSaveUser() throws Exception {
        loginSysAdmin();

        User user = createTenantAdminUser();
        String email = user.getEmail();
        Mockito.reset(tbClusterService, auditLogService);

        User savedUser = doPost("/api/user", user, User.class);
        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());
        Assert.assertTrue(savedUser.getCreatedTime() > 0);
        Assert.assertEquals(email, savedUser.getEmail());

        User foundUser = doGet("/api/user/" + savedUser.getId().getId().toString(), User.class);
        foundUser.setAdditionalInfo(savedUser.getAdditionalInfo());
        Assert.assertEquals(foundUser, savedUser);

        testNotifyManyEntityManyTimeMsgToEdgeServiceEntityEqAny(foundUser, foundUser,
                SYSTEM_TENANT, customerNUULId, null, SYS_ADMIN_EMAIL, ActionType.ADDED_TO_ENTITY_GROUP, 1, 0, 2);
        testNotifyManyEntityManyTimeMsgToEdgeServiceEntityEqAny(foundUser, foundUser,
                SYSTEM_TENANT, customerNUULId, null, SYS_ADMIN_EMAIL, ActionType.ADDED, 1, 1, 2);
        Mockito.reset(tbClusterService, auditLogService);

        resetTokens();
        doGet("/api/noauth/activate?activateToken={activateToken}", this.currentActivateToken)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "/login/createPassword?activateToken=" + this.currentActivateToken));

        JsonNode activateRequest = JacksonUtil.newObjectNode()
                .put("activateToken", this.currentActivateToken)
                .put("password", "testPassword");

        JsonNode tokenInfo = readResponse(doPost("/api/noauth/activate", activateRequest).andExpect(status().isOk()), JsonNode.class);
        validateAndSetJwtToken(tokenInfo, email);

        doGet("/api/auth/user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authority", is(Authority.TENANT_ADMIN.name())))
                .andExpect(jsonPath("$.email", is(email)));

        resetTokens();

        login(email, "testPassword");

        doGet("/api/auth/user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authority", is(Authority.TENANT_ADMIN.name())))
                .andExpect(jsonPath("$.email", is(email)));

        loginSysAdmin();
        foundUser = doGet("/api/user/" + savedUser.getId().getId().toString(), User.class);

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/user/" + savedUser.getId().getId().toString())
                .andExpect(status().isOk());

        testNotifyEntityAllNTimeLogEntityActionEntityEqClass(foundUser, foundUser.getId(), foundUser.getId(),
                SYSTEM_TENANT, customerNUULId, null, SYS_ADMIN_EMAIL,
                ActionType.DELETED, ActionType.DELETED, 1, 1, SYSTEM_TENANT.getId().toString());
    }

    @Test
    public void testSaveUserWithViolationOfFiledValidation() throws Exception {
        loginSysAdmin();

        Mockito.reset(tbClusterService, auditLogService);

        User user = createTenantAdminUser(StringUtils.randomAlphabetic(300), "Brown");
        String msgError = msgErrorFieldLength("first name");
        doPost("/api/user", user)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(user,
                SYSTEM_TENANT, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        user.setFirstName("Normal name");
        msgError = msgErrorFieldLength("last name");
        user.setLastName(StringUtils.randomAlphabetic(300));
        doPost("/api/user", user)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(user,
                SYSTEM_TENANT, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testCreateSysAdmin() throws Exception {
        loginSysAdmin();

        String email = "sysadmin2@thingsboard.org";
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setTenantId(TenantId.SYS_TENANT_ID);
        user.setEmail(email);
        user.setFirstName("Joe");
        user.setLastName("Downs");
        User savedUser = doPost("/api/user", user, User.class);
        Assert.assertNotNull(savedUser);
        Assert.assertNotNull(savedUser.getId());
        Assert.assertTrue(savedUser.getCreatedTime() > 0);
        Assert.assertEquals(user.getEmail(), savedUser.getEmail());

        User foundUser = doGet("/api/user/" + savedUser.getId().getId().toString(), User.class);
        foundUser.setAdditionalInfo(savedUser.getAdditionalInfo());
        Assert.assertEquals(foundUser, savedUser);

        resetTokens();

        loginSysAdmin();
        doDelete("/api/user/" + savedUser.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testTryCreateSysAdminByUserWithNoAuthority() throws Exception {
        loginTenantAdmin();

        String email = "sysadmin2@thingsboard.org";
        User user = new User();
        user.setAuthority(Authority.SYS_ADMIN);
        user.setTenantId(TenantId.SYS_TENANT_ID);
        user.setEmail(email);
        user.setFirstName("Joe");
        user.setLastName("Downs");
        doPost("/api/user", user).andExpect(status().is4xxClientError());
    }

    @Test
    public void testUpdateUserFromDifferentTenant() throws Exception {
        loginSysAdmin();

        User tenantAdmin = createTenantAdminUser();
        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");

        loginDifferentTenant();

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/user", tenantAdmin)
                .andExpect(status().isForbidden())
                .andExpect(statusReason(containsString(msgErrorPermissionWrite + "USER" + " '" + tenantAdmin.getEmail() + "'!")));

        testNotifyEntityNever(tenantAdmin.getId(), tenantAdmin);

        deleteDifferentTenant();
    }

    @Test
    public void testResetPassword() throws Exception {
        loginSysAdmin();

        User user = createTenantAdminUser();
        String email = user.getEmail();
        User savedUser = createUserAndLogin(user, "testPassword1");
        resetTokens();

        JsonNode resetPasswordByEmailRequest = JacksonUtil.newObjectNode()
                .put("email", email);

        doPost("/api/noauth/resetPasswordByEmail", resetPasswordByEmailRequest)
                .andExpect(status().isOk());
        Thread.sleep(1000);
        doGet("/api/noauth/resetPassword?resetToken={resetToken}", this.currentResetPasswordToken)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "/login/resetPassword?resetToken=" + this.currentResetPasswordToken));

        JsonNode resetPasswordRequest = JacksonUtil.newObjectNode()
                .put("resetToken", this.currentResetPasswordToken)
                .put("password", "testPassword2");

        Mockito.doNothing().when(mailService).sendPasswordWasResetEmail(any(), anyString(), anyString());
        JsonNode tokenInfo = readResponse(
                doPost("/api/noauth/resetPassword", resetPasswordRequest)
                        .andExpect(status().isOk()), JsonNode.class);
        Mockito.verify(mailService).sendPasswordWasResetEmail(any(), anyString(), anyString());
        validateAndSetJwtToken(tokenInfo, email);

        doGet("/api/auth/user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authority", is(Authority.TENANT_ADMIN.name())))
                .andExpect(jsonPath("$.email", is(email)));

        resetTokens();

        login(email, "testPassword2");
        doGet("/api/auth/user")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authority", is(Authority.TENANT_ADMIN.name())))
                .andExpect(jsonPath("$.email", is(email)));

        loginSysAdmin();
        doDelete("/api/user/" + savedUser.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testFindUserById() throws Exception {
        loginSysAdmin();

        User user = createTenantAdminUser();

        User savedUser = doPost("/api/user", user, User.class);
        User foundUser = doGet("/api/user/" + savedUser.getId().getId().toString(), User.class);
        Assert.assertNotNull(foundUser);
        foundUser.setAdditionalInfo(savedUser.getAdditionalInfo());
        Assert.assertEquals(savedUser, foundUser);
    }

    @Test
    public void testSaveUserWithSameEmail() throws Exception {
        loginSysAdmin();

        Mockito.reset(tbClusterService, auditLogService);

        User user = new User();
        user.setAuthority(Authority.TENANT_ADMIN);
        user.setTenantId(tenantId);
        user.setEmail(TENANT_ADMIN_EMAIL);

        String msgError = "User with email '" + TENANT_ADMIN_EMAIL + "' already present in database!";
        doPost("/api/user", user)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(user,
                SYSTEM_TENANT, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testSaveUserWithInvalidEmail() throws Exception {
        loginSysAdmin();

        Mockito.reset(tbClusterService, auditLogService);

        String email = "tenant_thingsboard.org";
        User user = createTenantAdminUser();
        user.setEmail(email);

        String msgError = "Invalid email address format '" + email + "'";
        doPost("/api/user", user)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(user,
                SYSTEM_TENANT, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testSaveUserWithEmptyEmail() throws Exception {
        loginSysAdmin();

        Mockito.reset(tbClusterService, auditLogService);

        User user = new User();
        user.setAuthority(Authority.TENANT_ADMIN);
        user.setTenantId(tenantId);
        user.setFirstName("Joe");
        user.setLastName("Downs");

        String msgError = "User email " + msgErrorShouldBeSpecified;
        doPost("/api/user", user)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("User email " + msgErrorShouldBeSpecified)));

        testNotifyEntityEqualsOneTimeServiceNeverError(user,
                SYSTEM_TENANT, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testSaveUserWithoutTenant() throws Exception {
        loginSysAdmin();

        Mockito.reset(tbClusterService, auditLogService);

        User user = new User();
        user.setAuthority(Authority.TENANT_ADMIN);
        user.setEmail("tenant2@thingsboard.org");
        user.setFirstName("Joe");
        user.setLastName("Downs");

        String msgError = "Tenant administrator should be assigned to tenant";
        doPost("/api/user", user)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(user,
                SYSTEM_TENANT, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, new DataValidationException(msgError));

    }

    @Test
    public void testDeleteUser() throws Exception {
        loginSysAdmin();

        User user = createTenantAdminUser();

        User savedUser = doPost("/api/user", user, User.class);
        User foundUser = doGet("/api/user/" + savedUser.getId().getId().toString(), User.class);
        Assert.assertNotNull(foundUser);

        doDelete("/api/user/" + savedUser.getId().getId().toString())
                .andExpect(status().isOk());

        String userIdStr = savedUser.getId().getId().toString();
        doGet("/api/user/" + userIdStr)
                .andExpect(status().isNotFound())
                .andExpect(statusReason(containsString(msgErrorNoFound("User", userIdStr))));
    }

    @Test
    public void testFindTenantAdmins() throws Exception {
        loginSysAdmin();

        //here created a new tenant despite already created on AbstractWebTest and then delete the tenant properly on the last line
        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant with many admins");
        Tenant savedTenant = saveTenant(tenant);
        Assert.assertNotNull(savedTenant);

        TenantId tenantId = savedTenant.getId();

        Mockito.reset(tbClusterService, auditLogService);

        int cntEntity = 64;
        List<User> tenantAdmins = new ArrayList<>();
        for (int i = 0; i < cntEntity; i++) {
            User user = new User();
            user.setAuthority(Authority.TENANT_ADMIN);
            user.setTenantId(tenantId);
            user.setEmail("testTenant" + i + "@thingsboard.org");
            tenantAdmins.add(doPost("/api/user", user, User.class));
        }

        User testManyUser = new User();
        testManyUser.setTenantId(tenantId);
        testNotifyManyEntityManyTimeMsgToEdgeServiceEntityEqAny(testManyUser, testManyUser,
                SYSTEM_TENANT, customerNUULId, null, SYS_ADMIN_EMAIL,
                ActionType.ADDED, cntEntity, cntEntity, cntEntity * 2);

        List<User> loadedTenantAdmins = new ArrayList<>();
        PageLink pageLink = new PageLink(33);
        PageData<User> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/" + tenantId.getId().toString() + "/users?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedTenantAdmins.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        tenantAdmins.sort(idComparator);
        loadedTenantAdmins.sort(idComparator);

        assertThat(tenantAdmins).as("admins list size").hasSameSizeAs(loadedTenantAdmins);
        assertThat(tenantAdmins).as("admins list content").isEqualTo(loadedTenantAdmins);

        deleteTenant(tenantId);

        pageLink = new PageLink(33);
        pageData = doGetTypedWithPageLink("/api/tenant/" + tenantId.getId().toString() + "/users?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
    }

    @Test
    public void testFindTenantAdminsByEmail() throws Exception {

        loginSysAdmin();

        String email1 = "testEmail1";
        List<User> tenantAdminsEmail1 = new ArrayList<>();

        final int NUMBER_OF_USERS = 124;

        for (int i = 0; i < NUMBER_OF_USERS; i++) {
            User user = new User();
            user.setAuthority(Authority.TENANT_ADMIN);
            user.setTenantId(tenantId);
            String suffix = StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String email = email1 + suffix + "@thingsboard.org";
            email = i % 2 == 0 ? email.toLowerCase() : email.toUpperCase();
            user.setEmail(email);
            tenantAdminsEmail1.add(doPost("/api/user", user, User.class));
        }

        String email2 = "testEmail2";
        List<User> tenantAdminsEmail2 = new ArrayList<>();

        for (int i = 0; i < 112; i++) {
            User user = new User();
            user.setAuthority(Authority.TENANT_ADMIN);
            user.setTenantId(tenantId);
            String suffix = StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
            String email = email2 + suffix + "@thingsboard.org";
            email = i % 2 == 0 ? email.toLowerCase() : email.toUpperCase();
            user.setEmail(email);
            tenantAdminsEmail2.add(doPost("/api/user", user, User.class));
        }

        List<User> loadedTenantAdminsEmail1 = new ArrayList<>();
        PageLink pageLink = new PageLink(33, 0, email1);
        PageData<User> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/" + tenantId.getId().toString() + "/users?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedTenantAdminsEmail1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        tenantAdminsEmail1.sort(idComparator);
        loadedTenantAdminsEmail1.sort(idComparator);

        Assert.assertEquals(tenantAdminsEmail1, loadedTenantAdminsEmail1);

        List<User> loadedTenantAdminsEmail2 = new ArrayList<>();
        pageLink = new PageLink(16, 0, email2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/" + tenantId.getId().toString() + "/users?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedTenantAdminsEmail2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        tenantAdminsEmail2.sort(idComparator);
        loadedTenantAdminsEmail2.sort(idComparator);

        Assert.assertEquals(tenantAdminsEmail2, loadedTenantAdminsEmail2);

        Mockito.reset(tbClusterService, auditLogService);

        int cntEntity = loadedTenantAdminsEmail1.size();
        for (User user : loadedTenantAdminsEmail1) {
            doDelete("/api/user/" + user.getId().getId().toString())
                    .andExpect(status().isOk());
        }
        User testManyUser = new User();
        testManyUser.setTenantId(tenantId);
        testNotifyManyEntityManyTimeMsgToEdgeServiceEntityEqAny(testManyUser, testManyUser,
                SYSTEM_TENANT, customerNUULId, null, SYS_ADMIN_EMAIL,
                ActionType.DELETED, cntEntity, NUMBER_OF_USERS, cntEntity, "");

        pageLink = new PageLink(4, 0, email1);
        pageData = doGetTypedWithPageLink("/api/tenant/" + tenantId.getId().toString() + "/users?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (User user : loadedTenantAdminsEmail2) {
            doDelete("/api/user/" + user.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, email2);
        pageData = doGetTypedWithPageLink("/api/tenant/" + tenantId.getId().toString() + "/users?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void testFindCustomerUsers() throws Exception {
        loginSysAdmin();

        User tenantAdmin = createTenantAdminUser();
        createUserAndLogin(tenantAdmin, "testPassword1");

        CustomerId customerId = postCustomer();

        List<User> customerUsers = new ArrayList<>();
        for (int i = 0; i < 56; i++) {
            User user = new User();
            user.setAuthority(Authority.CUSTOMER_USER);
            user.setCustomerId(customerId);
            user.setEmail("testCustomer" + i + "@thingsboard.org");
            customerUsers.add(doPost("/api/user", user, User.class));
        }

        List<User> loadedCustomerUsers = new ArrayList<>();
        PageLink pageLink = new PageLink(33);
        PageData<User> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/users?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedCustomerUsers.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        customerUsers.sort(idComparator);
        loadedCustomerUsers.sort(idComparator);

        Assert.assertEquals(customerUsers, loadedCustomerUsers);

        doDelete("/api/customer/" + customerId.getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testFindCustomerUsersByEmail() throws Exception {
        loginSysAdmin();

        User tenantAdmin = createTenantAdminUser();
        createUserAndLogin(tenantAdmin, "testPassword1");

        CustomerId customerId = postCustomer();

        String email1 = "testEmail1";
        String email2 = "testEmail2";
        List<User> customerUsersEmail1 = new ArrayList<>();
        List<User> customerUsersEmail2 = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            User customerUser = createCustomerUser(customerId);
            customerUser.setEmail(email1 + StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10)) + "@thingsboard.org");
            customerUsersEmail1.add(doPost("/api/user", customerUser, User.class));

            customerUser.setEmail(email2 + StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10)) + "@thingsboard.org");
            customerUsersEmail2.add(doPost("/api/user", customerUser, User.class));
        }

        List<User> loadedCustomerUsersEmail1 = new ArrayList<>();
        PageLink pageLink = new PageLink(33, 0, email1);
        PageData<User> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/users?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedCustomerUsersEmail1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        customerUsersEmail1.sort(idComparator);
        loadedCustomerUsersEmail1.sort(idComparator);

        Assert.assertEquals(customerUsersEmail1, loadedCustomerUsersEmail1);

        List<User> loadedCustomerUsersEmail2 = new ArrayList<>();
        pageLink = new PageLink(16, 0, email2);
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/users?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedCustomerUsersEmail2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        customerUsersEmail2.sort(idComparator);
        loadedCustomerUsersEmail2.sort(idComparator);

        Assert.assertEquals(customerUsersEmail2, loadedCustomerUsersEmail2);

        for (User user : loadedCustomerUsersEmail1) {
            doDelete("/api/user/" + user.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, email1);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/users?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (User user : loadedCustomerUsersEmail2) {
            doDelete("/api/user/" + user.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, email2);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/users?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        doDelete("/api/customer/" + customerId.getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testGetUsersForAssign() throws Exception {
        loginTenantAdmin();

        String email = "testEmail1";
        String password = "testPassword";
        User customerAdminUser = createCustomerAdminWithAllPermission(customerId, password);
        List<UserId> expectedCustomerUserIds = new ArrayList<>();
        expectedCustomerUserIds.add(customerAdminUser.getId());
        expectedCustomerUserIds.add(customerUserId);
        expectedCustomerUserIds.add(customerAdminUserId);
        for (int i = 0; i < 45; i++) {
            User customerUser = createCustomerUser(customerId);
            customerUser.setEmail(email + StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10)) + "@thingsboard.org");
            User user = doPost("/api/user", customerUser, User.class);
            expectedCustomerUserIds.add(user.getId());
        }
        List<UserId> expectedTenantUserIds = new ArrayList<>(List.copyOf(expectedCustomerUserIds));
        expectedTenantUserIds.add(tenantAdminUserId);

        Device device = new Device();
        device.setName("testDevice");
        Device savedDevice = doPost("/api/device", device, Device.class);

        Alarm alarm = createTestAlarm(savedDevice);

        List<UserId> loadedTenantUserIds = new ArrayList<>();
        PageLink pageLink = new PageLink(33, 0);
        PageData<UserEmailInfo> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/users/assign/" + alarm.getId().getId().toString() + "?",
                    new TypeReference<>() {}, pageLink);
            loadedTenantUserIds.addAll(pageData.getData().stream().map(UserEmailInfo::getId)
                    .collect(Collectors.toList()));
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Assert.assertEquals(1, loadedTenantUserIds.size());
        Assert.assertEquals(tenantAdminUserId, loadedTenantUserIds.get(0));

        doDelete("/api/alarm/" + alarm.getId().getId().toString());

        //change device owner
        doPost("/api/owner/CUSTOMER/" + customerId.getId() + "/DEVICE/" + savedDevice.getId().getId());
        savedDevice = doGet("/api/device/" + savedDevice.getId().getId(), Device.class);

        alarm = createTestAlarm(savedDevice);
        List<UserId> loadedUserIds = new ArrayList<>();
        pageLink = new PageLink(16, 0);
        do {
            pageData = doGetTypedWithPageLink("/api/users/assign/" + alarm.getId().getId().toString() + "?",
                    new TypeReference<>() {}, pageLink);
            loadedUserIds.addAll(pageData.getData().stream().map(UserEmailInfo::getId)
                    .collect(Collectors.toList()));
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        expectedTenantUserIds.sort(userIdComparator);
        loadedUserIds.sort(userIdComparator);

        Assert.assertEquals(expectedTenantUserIds, loadedUserIds);

        login(customerAdminUser.getEmail(), password);

        loadedUserIds = new ArrayList<>();
        pageLink = new PageLink(16, 0);
        do {
            pageData = doGetTypedWithPageLink("/api/users/assign/" + alarm.getId().getId().toString() + "?",
                    new TypeReference<>() {}, pageLink);
            loadedUserIds.addAll(pageData.getData().stream().map(UserEmailInfo::getId)
                    .collect(Collectors.toList()));
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        expectedCustomerUserIds.sort(userIdComparator);
        loadedUserIds.sort(userIdComparator);

        Assert.assertEquals(expectedCustomerUserIds, loadedUserIds);
    }

    @Test
    public void testGetUsersForDeletedAlarmOriginator() throws Exception {
        loginTenantAdmin();

        String email = "testEmail1";
        for (int i = 0; i < 45; i++) {
            User customerUser = createCustomerUser(customerId);
            customerUser.setEmail(email + StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10)) + "@thingsboard.org");
            doPost("/api/user", customerUser, User.class);
        }

        Device device = new Device();
        device.setName("testDevice");
        device.setCustomerId(customerId);
        Device savedDevice = doPost("/api/device", device, Device.class);

        Alarm alarm = createTestAlarm(savedDevice);

        deviceService.deleteDevice(tenantId, savedDevice.getId());

        List<UserId> loadedUserIds = new ArrayList<>();
        PageLink pageLink = new PageLink(33, 0);
        PageData<UserEmailInfo> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/users/assign/" + alarm.getId().getId().toString() + "?",
                    new TypeReference<>() {}, pageLink);
            loadedUserIds.addAll(pageData.getData().stream().map(UserEmailInfo::getId)
                    .collect(Collectors.toList()));
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Assert.assertEquals(1, loadedUserIds.size());
        Assert.assertEquals(tenantAdminUserId, loadedUserIds.get(0));
    }

    @Test
    public void testDeleteUserWithDeleteRelationsOk() throws Exception {
        loginSysAdmin();
        User tenantAdminUser = createTenantAdminUser();
        UserId userId = doPost("/api/user", tenantAdminUser, User.class).getId();
        testEntityDaoWithRelationsOk(tenantId, userId, "/api/user/" + userId);
    }

    @Ignore
    @Test
    public void testDeleteUserExceptionWithRelationsTransactional() throws Exception {
        loginSysAdmin();
        User tenantAdminUser = createTenantAdminUser("Joe", "Downs");
        UserId userId = doPost("/api/user", tenantAdminUser, User.class).getId();
        testEntityDaoWithRelationsTransactionalException(userDao, tenantId, userId, "/api/user/" + userId);
    }

    @Test
    public void testEnableDisableUser() throws Exception {
        loginSysAdmin();

        User tenantUser = new User();
        tenantUser.setAuthority(Authority.TENANT_ADMIN);
        tenantUser.setTenantId(tenantId);
        tenantUser.setEmail("tenant2@thingsboard.org");
        tenantUser.setFirstName("Joe");
        tenantUser.setLastName("Downs");

        tenantUser = createUser(tenantUser, "testPassword1");

        doPost("/api/user/" + tenantUser.getUuidId() + "/userCredentialsEnabled?userCredentialsEnabled=true")
                .andExpect(status().isOk());

        loginTenantAdmin();
        doPost("/api/user/" + tenantUser.getUuidId() + "/userCredentialsEnabled?userCredentialsEnabled=true")
                .andExpect(status().isOk());

        User customerAdmin = createCustomerAdminWithAllPermission(customerId, "testPassword1");

        User customerUser = new User();
        customerUser.setAuthority(Authority.CUSTOMER_USER);
        customerUser.setTenantId(tenantId);
        customerUser.setCustomerId(customerId);
        customerUser.setEmail("customer2@thingsboard.org");
        customerUser = createUser(customerUser, "testPassword1");

        login(customerAdmin.getEmail(), "testPassword1");
        doPost("/api/user/" + customerUser.getUuidId() + "/userCredentialsEnabled?userCredentialsEnabled=true")
                .andExpect(status().isOk());

        login(customerUser.getEmail(), "testPassword1");
        doPost("/api/user/" + customerAdmin.getUuidId() + "/userCredentialsEnabled?userCredentialsEnabled=true")
                .andExpect(status().isForbidden());
    }

    @Test
    public void givenInvalidPageLink_thenReturnError() throws Exception {
        loginTenantAdmin();

        String invalidSortProperty = "abc(abc)";

        ResultActions result = doGet("/api/user/users?page={page}&pageSize={pageSize}&sortProperty={sortProperty}", 0, 100, invalidSortProperty)
                .andExpect(status().isBadRequest());
        assertThat(getErrorMessage(result)).containsIgnoringCase("invalid sort property");
    }

    @Test
    public void testSaveUserSettings() throws Exception {
        loginCustomerUser();

        JsonNode userSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":10, \"E\":18}");
        JsonNode savedSettings = doPost("/api/user/settings", userSettings, JsonNode.class);
        Assert.assertEquals(userSettings, savedSettings);

        JsonNode retrievedSettings = doGet("/api/user/settings", JsonNode.class);
        Assert.assertEquals(retrievedSettings, userSettings);
    }

    @Test
    public void testShouldNotSaveJsonWithRestrictedSymbols() throws Exception {
        loginCustomerUser();

        JsonNode userSettings = JacksonUtil.toJsonNode("{\"A.B\":5, \"E\":18}");
        doPost("/api/user/settings", userSettings).andExpect(status().isBadRequest());

        userSettings = JacksonUtil.toJsonNode("{\"A,B\":5, \"E\":18}");
        doPost("/api/user/settings", userSettings).andExpect(status().isBadRequest());
    }

    @Test
    public void testUpdateUserSettings() throws Exception {
        loginCustomerUser();

        JsonNode userSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":{\"C\":true, \"D\":\"stringValue\"}}");
        JsonNode savedSettings = doPost("/api/user/settings", userSettings, JsonNode.class);
        Assert.assertEquals(userSettings, savedSettings);

        JsonNode newSettings = JacksonUtil.toJsonNode("{\"A\":10}");
        doPut("/api/user/settings", newSettings);
        JsonNode updatedSettings = doGet("/api/user/settings", JsonNode.class);
        JsonNode expectedSettings = JacksonUtil.toJsonNode("{\"A\":10, \"B\":{\"C\":true, \"D\":\"stringValue\"}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        JsonNode patchedSettings = JacksonUtil.toJsonNode("{\"A\":11, \"B\":{\"C\":false, \"D\":\"stringValue2\"}}");
        doPut("/api/user/settings", patchedSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":11, \"B\":{\"C\":false, \"D\":\"stringValue2\"}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        patchedSettings = JacksonUtil.toJsonNode("{\"B.D\": \"stringValue3\"}");
        doPut("/api/user/settings", patchedSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":11, \"B\":{\"C\":false, \"D\": \"stringValue3\"}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        patchedSettings = JacksonUtil.toJsonNode("{\"B.D\": {\"E\": 76, \"F\": 92}}");
        doPut("/api/user/settings", patchedSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":11, \"B\":{\"C\":false, \"D\": {\"E\":76, \"F\": 92}}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        patchedSettings = JacksonUtil.toJsonNode("{\"B.D.E\": 100}");
        doPut("/api/user/settings", patchedSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":11, \"B\":{\"C\":false, \"D\": {\"E\":100, \"F\": 92}}}");
        Assert.assertEquals(expectedSettings, updatedSettings);
    }

    @Test
    public void testShouldCreatePathIfNotExists() throws Exception {
        loginCustomerUser();

        JsonNode userSettings = JacksonUtil.toJsonNode("{\"A\":5}");
        JsonNode savedSettings = doPost("/api/user/settings", userSettings, JsonNode.class);
        Assert.assertEquals(userSettings, savedSettings);

        JsonNode newSettings = JacksonUtil.toJsonNode("{\"B\":{\"C\": 10}}");
        doPut("/api/user/settings", newSettings);
        JsonNode updatedSettings = doGet("/api/user/settings", JsonNode.class);
        JsonNode expectedSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":{\"C\": 10}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        newSettings = JacksonUtil.toJsonNode("{\"B.K\":true}");
        doPut("/api/user/settings", newSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":{\"C\": 10, \"K\": true}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        newSettings = JacksonUtil.toJsonNode("{\"B\":{}}");
        doPut("/api/user/settings", newSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":{}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        newSettings = JacksonUtil.toJsonNode("{\"F.G\":\"string\"}");
        doPut("/api/user/settings", newSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":{}, \"F\":{\"G\": \"string\"}}");
        Assert.assertEquals(expectedSettings, updatedSettings);

        newSettings = JacksonUtil.toJsonNode("{\"F\":{\"G\":\"string2\"}}");
        doPut("/api/user/settings", newSettings);
        updatedSettings = doGet("/api/user/settings", JsonNode.class);
        expectedSettings = JacksonUtil.toJsonNode("{\"A\":5, \"B\":{}, \"F\":{\"G\": \"string2\"}}");
        Assert.assertEquals(expectedSettings, updatedSettings);
    }

    @Test
    public void testDeleteUserSettings() throws Exception {
        loginCustomerUser();

        JsonNode userSettings = JacksonUtil.toJsonNode("{\"A\":10, \"B\":10, \"C\":{\"D\": 16}}");
        JsonNode savedSettings = doPost("/api/user/settings", userSettings, JsonNode.class);
        Assert.assertEquals(userSettings, savedSettings);

        doDelete("/api/user/settings/C.D,B");

        JsonNode retrievedSettings = doGet("/api/user/settings", JsonNode.class);
        JsonNode expectedSettings = JacksonUtil.toJsonNode("{\"A\":10, \"C\":{}}");
        Assert.assertEquals(expectedSettings, retrievedSettings);
    }

    @Test
    public void checkCustomerUserDoNotSeeTenantUsersOtherTenantUsersOtherCustomerUsers() throws Exception {
        loginSysAdmin();
        String searchText = "Joe";

        loginDifferentTenant();
        CustomerId customerId1 = postCustomer();
        doPost("/api/user", createCustomerUser(searchText, "Ress", customerId1), User.class);

        loginSysAdmin();
        User tenantAdmin = createTenantAdminUser(searchText, "Brown");
        createUserAndLogin(tenantAdmin, "testPassword1");

        CustomerId customerId2 = postCustomer();
        User user = createCustomerUser(searchText, "Downs", customerId2);
        doPost("/api/user", user, User.class);

        CustomerId customerId3 = postCustomer();
        User customerAdmin = createCustomerAdminWithAllPermission(customerId, "testPassword2");
        login(customerAdmin.getEmail(), "testPassword2");

        PageLink pageLink = new PageLink(10, 0, searchText);
        List<UserEmailInfo> usersInfo = getUsersInfo(pageLink);

        Assert.assertEquals(usersInfo.size(), 0);

        //clear users
        loginDifferentTenant();
        doDelete("/api/customer/" + customerId1.getId().toString())
                .andExpect(status().isOk());
        loginUser(tenantAdmin.getEmail(), "testPassword1");
        doDelete("/api/customer/" + customerId2.getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/customer/" + customerId3.getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void shouldFindCustomerUsersBySearchText() throws Exception {
        loginSysAdmin();
        User tenantAdmin = createTenantAdminUser();
        createUserAndLogin(tenantAdmin, "testPassword1");

        String searchText = "Philip";

        CustomerId customerId = postCustomer();
        CustomerId customerId2 = postCustomer();

        List<User> customerUsersContainingText = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String suffix = StringUtils.randomAlphabetic((int) (5 + Math.random() * 10));

            customerUsersContainingText.add(doPost("/api/user", createCustomerUser(searchText + i, "Last" + i, customerId), User.class));
            customerUsersContainingText.add(doPost("/api/user", createCustomerUser(null, null, searchText + suffix + "@thingsboard.org", customerId), User.class));
            doPost("/api/user", createCustomerUser(null, null, customerId), User.class);

            suffix = StringUtils.randomAlphabetic((int) (5 + Math.random() * 10));
            doPost("/api/user", createCustomerUser(searchText + i, "Last" + i, customerId2), User.class);
            doPost("/api/user", createCustomerUser(null, null, searchText + suffix + "@thingsboard.org", customerId2), User.class);
        }

        User customerAdmin = createCustomerAdminWithAllPermission(customerId, "testPassword2");
        login(customerAdmin.getEmail(), "testPassword2");

        // find users by search text
        PageLink pageLink = new PageLink(10, 0, searchText);
        List<UserEmailInfo> usersInfo = getUsersInfo(pageLink);

        List<UserEmailInfo> expectedUserInfos = customerUsersContainingText.stream().map(customerUser -> new UserEmailInfo(customerUser.getId(),
                        customerUser.getEmail(), customerUser.getFirstName() == null ? "" : customerUser.getFirstName(),
                        customerUser.getLastName() == null ? "" : customerUser.getLastName()))
                .sorted(userDataIdComparator).collect(Collectors.toList());
        usersInfo.sort(userDataIdComparator);

        Assert.assertEquals(expectedUserInfos, usersInfo);

        // find user by full first name
        pageLink = new PageLink(10, 0, searchText + "5");
        usersInfo = getUsersInfo(pageLink);
        Assert.assertEquals(1, usersInfo.size());

        //clear users
        loginUser(tenantAdmin.getEmail(), "testPassword1");
        doDelete("/api/customer/" + customerId.getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/customer/" + customerId2.getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void shouldFindTenantUsersBySearchText() throws Exception {
        loginSysAdmin();

        User tenantAdmin = createTenantAdminUser();
        createUserAndLogin(tenantAdmin, "testPassword1");
        CustomerId customerId = postCustomer();
        CustomerId customerId2 = postCustomer();

        String searchText = "Brown";

        List<User> usersContainingText = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String suffix = StringUtils.randomAlphabetic((int) (5 + Math.random() * 10));
            usersContainingText.add(doPost("/api/user", createCustomerUser("First" + i, searchText + i, customerId), User.class));
            usersContainingText.add(doPost("/api/user", createCustomerUser(null, null, searchText + suffix + "@thingsboard.org", customerId), User.class));
            doPost("/api/user", createCustomerUser(null, null, customerId), User.class);

            suffix = StringUtils.randomAlphabetic((int) (5 + Math.random() * 10));
            usersContainingText.add(doPost("/api/user", createCustomerUser("First" + i, searchText + i, customerId2), User.class));
            usersContainingText.add(doPost("/api/user", createCustomerUser(null, null, searchText + suffix + "@thingsboard.org", customerId2), User.class));
        }

        loginDifferentTenant();
        CustomerId customerId3 = postCustomer();
        doPost("/api/user", createCustomerUser("Jane", searchText, customerId3), User.class);

        // find users by search text
        loginUser(tenantAdmin.getEmail(), "testPassword1");
        PageLink pageLink = new PageLink(10, 0, searchText);
        List<UserEmailInfo> usersInfo = getUsersInfo(pageLink);

        List<UserEmailInfo> expectedUserInfos = usersContainingText.stream().map(customerUser -> new UserEmailInfo(customerUser.getId(),
                        customerUser.getEmail(), customerUser.getFirstName() == null ? "" : customerUser.getFirstName(),
                        customerUser.getLastName() == null ? "" : customerUser.getLastName()))
                .sorted(userDataIdComparator).collect(Collectors.toList());
        usersInfo.sort(userDataIdComparator);

        Assert.assertEquals(expectedUserInfos, usersInfo);

        // find user by full last name
        pageLink = new PageLink(10, 0, searchText + "3");
        usersInfo = getUsersInfo(pageLink);
        Assert.assertEquals(2, usersInfo.size());

        //clear users
        doDelete("/api/customer/" + customerId.getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/customer/" + customerId2.getId().toString())
                .andExpect(status().isOk());
    }

    private CustomerId postCustomer() {
        Customer customer = new Customer();
        customer.setTitle(StringUtils.randomAlphabetic(9));
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        return savedCustomer.getId();
    }

    private static User createCustomerUser(CustomerId customerId) {
        return createCustomerUser(null, null, customerId);
    }

    private static User createCustomerUser(String firstName, String lastName, CustomerId customerId) {
        String suffix = StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));
        return createCustomerUser(firstName, lastName, "testMail" + suffix + "@thingsboard.org", customerId);
    }

    private static User createCustomerUser(String firstName, String lastName, String email, CustomerId customerId) {
        User user = new User();
        user.setAuthority(Authority.CUSTOMER_USER);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setCustomerId(customerId);
        user.setEmail(email);
        return user;
    }

    private User createTenantAdminUser() {
        return createTenantAdminUser(null, null);
    }

    private User createTenantAdminUser(String firstName, String lastName) {
        String suffix = StringUtils.randomAlphanumeric((int) (5 + Math.random() * 10));

        User tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(tenantId);
        tenantAdmin.setEmail("testEmail" + suffix + "@thingsbord.org");
        tenantAdmin.setFirstName(firstName);
        tenantAdmin.setLastName(lastName);
        return tenantAdmin;
    }

    private List<UserEmailInfo> getUsersInfo(PageLink pageLink) throws Exception {
        List<UserEmailInfo> loadedCustomerUsers = new ArrayList<>();
        PageData<UserEmailInfo> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/users/info?", new TypeReference<>() {
            }, pageLink);
            loadedCustomerUsers.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
                Assert.assertEquals(pageLink.getPageSize(), pageData.getData().size());
            }
        } while (pageData.hasNext());
        return loadedCustomerUsers;
    }

    private User createCustomerAdminWithAllPermission(CustomerId customerId, String testPassword2) throws Exception {
        Role role = new Role();
        role.setTenantId(tenantId);
        role.setCustomerId(customerId);
        role.setType(RoleType.GENERIC);
        role.setName("Test customer administrator");
        role.setPermissions(JacksonUtil.toJsonNode("{\"ALL\":[\"ALL\"]}"));
        role = doPost("/api/role", role, Role.class);

        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName("Test customer administrators");
        entityGroup.setType(EntityType.USER);
        entityGroup.setOwnerId(customerId);
        entityGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class);

        GroupPermission groupPermission = new GroupPermission(tenantId, entityGroup.getId(), role.getId(),
                null, null, false
        );
        doPost("/api/groupPermission", groupPermission, GroupPermission.class);

        User customerAdmin = new User();
        customerAdmin.setEmail("customer1@thingsboard.org");
        customerAdmin.setTenantId(tenantId);
        customerAdmin.setCustomerId(customerId);
        customerAdmin.setFirstName("customer");
        customerAdmin.setLastName("admin");
        customerAdmin.setAuthority(Authority.CUSTOMER_USER);
        customerAdmin = createUser(customerAdmin, testPassword2, entityGroup.getId());
        return customerAdmin;
    }

    private Alarm createTestAlarm(Device device) {
        Alarm alarm = new Alarm();
        alarm.setOriginator(device.getId());
        alarm.setCustomerId(device.getCustomerId());
        alarm.setSeverity(AlarmSeverity.MAJOR);
        alarm.setType("testAlarm");
        alarm.setStartTs(System.currentTimeMillis());
        return doPost("/api/alarm", alarm, Alarm.class);
    }

    @Test
    public void testEmptyDashboardSettings() throws Exception {
        loginCustomerUser();

        UserDashboardsInfo retrievedSettings = doGet("/api/user/dashboards", UserDashboardsInfo.class);
        Assert.assertNotNull(retrievedSettings);
        Assert.assertNotNull(retrievedSettings.getLast());
        Assert.assertTrue(retrievedSettings.getLast().isEmpty());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertTrue(retrievedSettings.getStarred().isEmpty());
    }

    @Test
    public void testDashboardSettingsFlow() throws Exception {
        loginTenantAdmin();

        Dashboard dashboard1 = new Dashboard();
        dashboard1.setTitle("My dashboard 1");
        Dashboard savedDashboard1 = doPost("/api/dashboard", dashboard1, Dashboard.class);
        Dashboard dashboard2 = new Dashboard();
        dashboard2.setTitle("My dashboard 2");
        Dashboard savedDashboard2 = doPost("/api/dashboard", dashboard2, Dashboard.class);

        UserDashboardsInfo retrievedSettings = doGet("/api/user/dashboards", UserDashboardsInfo.class);
        Assert.assertNotNull(retrievedSettings);
        Assert.assertNotNull(retrievedSettings.getLast());
        Assert.assertTrue(retrievedSettings.getLast().isEmpty());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertTrue(retrievedSettings.getStarred().isEmpty());

        UserDashboardsInfo newSettings = doGet("/api/user/dashboards/" + savedDashboard1.getId().getId() + "/visit", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(1, newSettings.getLast().size());
        var lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard1.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard1.getTitle(), lastVisited.getTitle());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertTrue(retrievedSettings.getStarred().isEmpty());

        newSettings = doGet("/api/user/dashboards/" + savedDashboard2.getId().getId() + "/visit", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(2, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertTrue(retrievedSettings.getStarred().isEmpty());

        newSettings = doGet("/api/user/dashboards", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(2, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertTrue(retrievedSettings.getStarred().isEmpty());

        newSettings = doGet("/api/user/dashboards/" + savedDashboard1.getId().getId() + "/star", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(2, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertFalse(lastVisited.isStarred());
        lastVisited = newSettings.getLast().get(1);
        Assert.assertEquals(savedDashboard1.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard1.getTitle(), lastVisited.getTitle());
        Assert.assertTrue(lastVisited.isStarred());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertEquals(1, newSettings.getStarred().size());
        StarredDashboardInfo starred = newSettings.getStarred().get(0);
        Assert.assertEquals(savedDashboard1.getId().getId(), starred.getId());
        Assert.assertEquals(savedDashboard1.getTitle(), starred.getTitle());

        newSettings = doGet("/api/user/dashboards/" + savedDashboard2.getId().getId() + "/star", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(2, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertTrue(lastVisited.isStarred());
        lastVisited = newSettings.getLast().get(1);
        Assert.assertEquals(savedDashboard1.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard1.getTitle(), lastVisited.getTitle());
        Assert.assertTrue(lastVisited.isStarred());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertEquals(2, newSettings.getStarred().size());
        starred = newSettings.getStarred().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), starred.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), starred.getTitle());

        newSettings = doGet("/api/user/dashboards/" + savedDashboard1.getId().getId() + "/unstar", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(2, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertTrue(lastVisited.isStarred());
        lastVisited = newSettings.getLast().get(1);
        Assert.assertEquals(savedDashboard1.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard1.getTitle(), lastVisited.getTitle());
        Assert.assertFalse(lastVisited.isStarred());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertEquals(1, newSettings.getStarred().size());
        starred = newSettings.getStarred().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), starred.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), starred.getTitle());

        //TEST renaming in the cache.
        savedDashboard1.setTitle(RandomStringUtils.randomAlphanumeric(10));
        savedDashboard1 = doPost("/api/dashboard", savedDashboard1, Dashboard.class);
        savedDashboard2.setTitle(RandomStringUtils.randomAlphanumeric(10));
        savedDashboard2 = doPost("/api/dashboard", savedDashboard2, Dashboard.class);

        newSettings = doGet("/api/user/dashboards/" + savedDashboard1.getId().getId() + "/unstar", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(2, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertTrue(lastVisited.isStarred());
        lastVisited = newSettings.getLast().get(1);
        Assert.assertEquals(savedDashboard1.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard1.getTitle(), lastVisited.getTitle());
        Assert.assertFalse(lastVisited.isStarred());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertEquals(1, newSettings.getStarred().size());
        starred = newSettings.getStarred().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), starred.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), starred.getTitle());

        doDelete("/api/dashboard/" + savedDashboard1.getId().getId().toString()).andExpect(status().isOk());

        newSettings = doGet("/api/user/dashboards", UserDashboardsInfo.class);
        Assert.assertNotNull(newSettings);
        Assert.assertNotNull(newSettings.getLast());
        Assert.assertEquals(1, newSettings.getLast().size());
        lastVisited = newSettings.getLast().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), lastVisited.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), lastVisited.getTitle());
        Assert.assertTrue(lastVisited.isStarred());
        Assert.assertEquals(1, newSettings.getStarred().size());
        starred = newSettings.getStarred().get(0);
        Assert.assertEquals(savedDashboard2.getId().getId(), starred.getId());
        Assert.assertEquals(savedDashboard2.getTitle(), starred.getTitle());

        doDelete("/api/dashboard/" + savedDashboard2.getId().getId().toString()).andExpect(status().isOk());

        retrievedSettings = doGet("/api/user/dashboards", UserDashboardsInfo.class);
        Assert.assertNotNull(retrievedSettings);
        Assert.assertNotNull(retrievedSettings.getLast());
        Assert.assertTrue(retrievedSettings.getLast().isEmpty());
        Assert.assertNotNull(retrievedSettings.getStarred());
        Assert.assertTrue(retrievedSettings.getStarred().isEmpty());
    }

}
