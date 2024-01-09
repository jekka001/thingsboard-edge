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
package org.thingsboard.server.service.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.NotificationType;
import org.thingsboard.server.common.data.notification.rule.trigger.config.EntityActionNotificationRuleTriggerConfig;
import org.thingsboard.server.common.data.notification.targets.NotificationTarget;
import org.thingsboard.server.common.data.notification.targets.platform.AllUsersFilter;
import org.thingsboard.server.common.data.notification.targets.platform.CustomerUsersFilter;
import org.thingsboard.server.common.data.notification.targets.platform.PlatformUsersNotificationTargetConfig;
import org.thingsboard.server.common.data.notification.targets.platform.TenantAdministratorsFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UserListFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UsersFilter;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.notification.NotificationTargetDao;
import org.thingsboard.server.dao.service.DaoSqlTest;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class NotificationTargetApiTest extends AbstractNotificationApiTest {

    @Autowired
    private NotificationTargetDao notificationTargetDao;

    @Before
    public void beforeEach() throws Exception {
        loginTenantAdmin();
    }

    @Test
    public void givenInvalidNotificationTarget_whenSaving_returnValidationError() throws Exception {
        NotificationTarget target = new NotificationTarget();
        target.setTenantId(null);
        target.setName(null);
        target.setConfiguration(null);

        String validationError = saveAndGetError(target, status().isBadRequest());
        assertThat(validationError)
                .contains("name must not be")
                .contains("configuration must not be");

        PlatformUsersNotificationTargetConfig targetConfig = new PlatformUsersNotificationTargetConfig();
        UserListFilter userListFilter = new UserListFilter();
        userListFilter.setUsersIds(Collections.emptyList());
        targetConfig.setUsersFilter(userListFilter);
        target.setConfiguration(targetConfig);

        validationError = saveAndGetError(target, status().isBadRequest());
        assertThat(validationError)
                .contains("usersIds must not be");
    }

    @Test
    public void givenNotificationTargetWithUsersFromDifferentTenant_whenSaving_returnAccessDeniedError() throws Exception {
        loginDifferentTenant();
        NotificationTarget notificationTarget = new NotificationTarget();
        notificationTarget.setTenantId(savedDifferentTenant.getId());
        notificationTarget.setName("Target 1");

        PlatformUsersNotificationTargetConfig targetConfig = new PlatformUsersNotificationTargetConfig();
        UserListFilter userListFilter = new UserListFilter();
        userListFilter.setUsersIds(List.of(customerUserId.getId(), tenantAdminUserId.getId()));
        targetConfig.setUsersFilter(userListFilter);
        notificationTarget.setConfiguration(targetConfig);

        saveAndGetError(notificationTarget, status().isForbidden());

        loginSysAdmin();
        notificationTarget.setTenantId(TenantId.SYS_TENANT_ID);
        save(notificationTarget, status().isOk());
    }

    @Test
    public void givenCustomerUsersTargetFilter_testGetRecipients() throws Exception {
        CustomerUsersFilter filter = new CustomerUsersFilter();
        filter.setCustomerId(customerId.getId());

        List<User> recipients = getRecipients(filter);
        assertThat(recipients).size().isNotZero();
        assertThat(recipients).allSatisfy(recipient -> {
            assertThat(recipient.getCustomerId()).isEqualTo(customerId);
        });
    }

    @Test
    public void givenAllUsersTargetFilter_testGetRecipients() throws Exception {
        AllUsersFilter filter = new AllUsersFilter();

        List<User> recipients = getRecipients(filter);
        assertThat(recipients).size().isGreaterThanOrEqualTo(2);
        assertThat(recipients).allSatisfy(recipient -> {
            assertThat(recipient.getTenantId()).isEqualTo(tenantId);
        });
    }

    @Test
    public void givenAllUsersTargetFilter_sysAdmin_testGetRecipients() throws Exception {
        loginSysAdmin();
        createDifferentTenant();
        loginSysAdmin();
        AllUsersFilter filter = new AllUsersFilter();

        List<User> recipients = getRecipients(filter);
        assertThat(recipients).size().isGreaterThanOrEqualTo(3);
        assertThat(recipients).anySatisfy(recipient -> {
            assertThat(recipient.getTenantId()).isEqualTo(tenantId);
        });
        assertThat(recipients).anySatisfy(recipient -> {
            assertThat(recipient.getTenantId()).isEqualTo(savedDifferentTenant.getId());
        });
    }

    @Test
    public void givenTenantAdminsTargetFilter_onSysAdminLevel_testGetRecipients() throws Exception {
        loginSysAdmin();
        User tenantAdmin1 = new User();
        tenantAdmin1.setTenantId(tenantId);
        tenantAdmin1.setEmail("tenant-admin1@tb.org");
        tenantAdmin1.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin1 = createUser(tenantAdmin1, tenantAdmin1.getEmail());

        createDifferentTenant();
        loginSysAdmin();
        User tenantAdmin2 = new User();
        tenantAdmin2.setTenantId(differentTenantId);
        tenantAdmin2.setEmail("tenant-admin2@tb.org");
        tenantAdmin2.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin2 = createUser(tenantAdmin2, tenantAdmin2.getEmail());

        loginTenantAdmin();
        EntityGroup tenantUsers = entityGroupService.findOrCreateTenantUsersGroup(tenantId);
        User tenantUser1 = new User();
        tenantUser1.setEmail("tenant-user1@tb.org");
        tenantUser1.setAuthority(Authority.TENANT_ADMIN);
        tenantUser1 = createUser(tenantUser1, tenantUser1.getEmail(), tenantUsers.getId());

        loginDifferentTenant();
        tenantUsers = entityGroupService.findOrCreateTenantUsersGroup(differentTenantId);
        User tenantUser2 = new User();
        tenantUser2.setEmail("tenant-user2@tb.org");
        tenantUser2.setAuthority(Authority.TENANT_ADMIN);
        tenantUser2 = createUser(tenantUser2, tenantUser2.getEmail(), tenantUsers.getId());

        loginSysAdmin();
        TenantAdministratorsFilter tenantAdminsFilter = new TenantAdministratorsFilter();
        tenantAdminsFilter.setTenantsIds(Set.of(tenantId.getId()));
        List<User> recipients = getRecipients(tenantAdminsFilter);
        assertThat(recipients).extracting(User::getId)
                .contains(tenantAdmin1.getId())
                .doesNotContain(tenantUser1.getId())
                .doesNotContain(tenantUser2.getId(), tenantAdmin2.getId());

        tenantAdminsFilter.setTenantsIds(Set.of(differentTenantId.getId()));
        recipients = getRecipients(tenantAdminsFilter);
        assertThat(recipients).extracting(User::getId)
                .contains(tenantAdmin2.getId())
                .doesNotContain(tenantUser2.getId())
                .doesNotContain(tenantUser1.getId(), tenantAdmin1.getId());

        tenantAdminsFilter.setTenantsIds(Set.of(tenantId.getId(), differentTenantId.getId()));
        recipients = getRecipients(tenantAdminsFilter);
        assertThat(recipients).extracting(User::getId)
                .contains(tenantAdmin1.getId(), tenantAdmin2.getId())
                .doesNotContain(tenantUser1.getId(), tenantUser2.getId());

        tenantAdminsFilter.setTenantsIds(Collections.emptySet());
        recipients = getRecipients(tenantAdminsFilter);
        assertThat(recipients).extracting(User::getId)
                .contains(tenantAdmin1.getId(), tenantAdmin2.getId())
                .doesNotContain(tenantUser1.getId(), tenantUser2.getId());

        tenantAdminsFilter.setTenantsIds(null);
        tenantAdminsFilter.setTenantProfilesIds(Set.of(tenantProfileId.getId()));
        recipients = getRecipients(tenantAdminsFilter);
        assertThat(recipients).extracting(User::getId)
                .contains(tenantAdmin1.getId(), tenantAdmin2.getId())
                .doesNotContain(tenantUser1.getId(), tenantUser2.getId());
    }

    @Test
    public void givenTenantAdminsTargetFilter_onTenantLevel_testGetRecipients() throws Exception {
        loginSysAdmin();
        User tenantAdmin1 = new User();
        tenantAdmin1.setTenantId(tenantId);
        tenantAdmin1.setEmail("tenant-admin1@tb.org");
        tenantAdmin1.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin1 = createUser(tenantAdmin1, tenantAdmin1.getEmail());

        loginTenantAdmin();
        EntityGroup tenantUsers = entityGroupService.findOrCreateTenantUsersGroup(tenantId);
        User tenantUser1 = new User();
        tenantUser1.setEmail("tenant-user1@tb.org");
        tenantUser1.setAuthority(Authority.TENANT_ADMIN);
        tenantUser1 = createUser(tenantUser1, tenantUser1.getEmail(), tenantUsers.getId());

        TenantAdministratorsFilter tenantAdminsFilter = new TenantAdministratorsFilter();
        List<User> recipients = getRecipients(tenantAdminsFilter);
        assertThat(recipients).extracting(User::getId)
                .contains(tenantAdmin1.getId())
                .doesNotContain(tenantUser1.getId());
    }

    @Test
    public void whenDeletingTenant_thenDeleteNotificationTarget() throws Exception {
        createDifferentTenant();
        NotificationTarget notificationTarget = new NotificationTarget();
        notificationTarget.setName("Test 1");
        TenantId tenantId = savedDifferentTenant.getId();
        notificationTarget.setTenantId(tenantId);
        PlatformUsersNotificationTargetConfig targetConfig = new PlatformUsersNotificationTargetConfig();
        targetConfig.setUsersFilter(new AllUsersFilter());
        notificationTarget.setConfiguration(targetConfig);
        save(notificationTarget, status().isOk());
        assertThat(notificationTargetDao.findByTenantIdAndPageLink(tenantId, new PageLink(10)).getData()).isNotEmpty();
        assertThat(notificationTargetDao.findByTenantIdAndSupportedNotificationTypeAndPageLink(tenantId, NotificationType.GENERAL, new PageLink(10)).getData()).isNotEmpty();

        deleteDifferentTenant();
        assertThat(notificationTargetDao.findByTenantIdAndPageLink(tenantId, new PageLink(10)).getData()).isEmpty();
    }

    @Test
    public void whenDeletingTargetUsedByRule_thenReturnError() throws Exception {
        NotificationTarget target = createNotificationTarget(tenantAdminUserId);
        createNotificationRule(new EntityActionNotificationRuleTriggerConfig(), "Test", "Test", target.getId());

        String error = getErrorMessage(doDelete("/api/notification/target/" + target.getId())
                .andExpect(status().isBadRequest()));
        assertThat(error).containsIgnoringCase("used in notification rule");
    }

    @Test
    public void whenDeletingTargetUsedByScheduledNotificationRequest_thenReturnError() throws Exception {
        NotificationTarget target = createNotificationTarget(tenantAdminUserId);
        submitNotificationRequest(target.getId(), "Test", 100, NotificationDeliveryMethod.WEB);

        String error = getErrorMessage(doDelete("/api/notification/target/" + target.getId())
                .andExpect(status().isBadRequest()));
        assertThat(error).containsIgnoringCase("referenced by scheduled notification request");
    }

    private String saveAndGetError(NotificationTarget notificationTarget, ResultMatcher statusMatcher) throws Exception {
        return getErrorMessage(save(notificationTarget, statusMatcher));
    }

    private ResultActions save(NotificationTarget notificationTarget, ResultMatcher statusMatcher) throws Exception {
        return doPost("/api/notification/target", notificationTarget)
                .andExpect(statusMatcher);
    }

    private List<User> getRecipients(UsersFilter usersFilter) throws Exception {
        NotificationTarget notificationTarget = new NotificationTarget();
        notificationTarget.setName(usersFilter.toString());
        PlatformUsersNotificationTargetConfig targetConfig = new PlatformUsersNotificationTargetConfig();
        targetConfig.setUsersFilter(usersFilter);
        notificationTarget.setConfiguration(targetConfig);
        return doPostWithTypedResponse("/api/notification/target/recipients?page=0&pageSize=100", notificationTarget, new TypeReference<PageData<User>>() {}).getData();
    }

}
