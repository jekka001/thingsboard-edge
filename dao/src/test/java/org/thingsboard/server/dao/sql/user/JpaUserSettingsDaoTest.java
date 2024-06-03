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
package org.thingsboard.server.dao.sql.user;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.settings.UserSettings;
import org.thingsboard.server.common.data.settings.UserSettingsCompositeKey;
import org.thingsboard.server.common.data.settings.UserSettingsType;
import org.thingsboard.server.dao.AbstractJpaDaoTest;
import org.thingsboard.server.dao.service.AbstractServiceTest;
import org.thingsboard.server.dao.user.UserDao;
import org.thingsboard.server.dao.user.UserSettingsDao;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Index.atIndex;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.dao.service.AbstractServiceTest.SYSTEM_TENANT_ID;

@Slf4j
public class JpaUserSettingsDaoTest extends AbstractJpaDaoTest {

    private UUID tenantId;
    private User user;

    @Autowired
    private UserSettingsDao userSettingsDao;

    @Autowired
    private UserDao userDao;

    @Before
    public void setUp() {
        tenantId = Uuids.timeBased();
        user = saveUser(tenantId, Uuids.timeBased());
    }

    @After
    public void tearDown() {
        userDao.removeById(user.getTenantId(), user.getUuidId());
    }

    @Test
    public void testFindSettingsByUserId() {
        UserSettings userSettings = createUserSettings(user.getId());

        UserSettings retrievedUserSettings = userSettingsDao.findById(SYSTEM_TENANT_ID, new UserSettingsCompositeKey(user.getId().getId(), UserSettingsType.GENERAL.name()));
        assertEquals(retrievedUserSettings.getSettings(), userSettings.getSettings());

        userSettingsDao.removeById(SYSTEM_TENANT_ID, new UserSettingsCompositeKey(user.getId().getId(), UserSettingsType.GENERAL.name()));

        UserSettings retrievedUserSettings2 = userSettingsDao.findById(SYSTEM_TENANT_ID, new UserSettingsCompositeKey(user.getId().getId(), UserSettingsType.GENERAL.name()));
        assertNull(retrievedUserSettings2);
    }

    // If Hibernate fail to bind JSON path please check the hypersistence-utils-hibernate-XX artifact name and version in the dependency management
    // Example: java.lang.ClassCastException: class [Ljava.lang.String; cannot be cast to class [B ([Ljava.lang.String; and [B are in module java.base of loader 'bootstrap')
    @Test
    public void testFindByTypeAndJsonPath() {
        UserSettings userSettings = createUserSettings(user.getId());
        log.warn("userSettings {}", userSettings);

        userSettings.setSettings(JacksonUtil.toJsonNode("{\"text\":\"bla1\",\"sessions\":{\"tenantFcmToken\":{\"fcmTokenTimestamp\":0}}}"));

        userSettingsDao.save(SYSTEM_TENANT_ID, userSettings);

        assertThat(userSettingsDao.findByTypeAndPath(SYSTEM_TENANT_ID, UserSettingsType.GENERAL, "text"))
                .isNotEmpty().hasSize(1).contains(userSettings, atIndex(0));

        assertThat(userSettingsDao.findByTypeAndPath(SYSTEM_TENANT_ID, UserSettingsType.GENERAL, "sessions", "tenantFcmToken"))
                .isNotEmpty().hasSize(1).contains(userSettings, atIndex(0));

        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.GENERAL, "mistery")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.GENERAL, "text", "text")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.GENERAL, "text", "lvl2")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.MOBILE, "text")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.MOBILE, "sessions", "1")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.MOBILE, "text", "text")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.MOBILE, "")).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.MOBILE, null)).isEmpty();
        assertThat(userSettingsDao.findByTypeAndPath(user.getTenantId(), UserSettingsType.MOBILE)).isEmpty();
    }

    private UserSettings createUserSettings(UserId userId) {
        UserSettings userSettings = new UserSettings();
        userSettings.setType(UserSettingsType.GENERAL);
        userSettings.setSettings(JacksonUtil.newObjectNode().put("text", RandomStringUtils.randomAlphanumeric(10)));
        userSettings.setUserId(userId);
        return userSettingsDao.save(SYSTEM_TENANT_ID, userSettings);
    }

    private User saveUser(UUID tenantId, UUID customerId) {
        User user = new User();
        UUID id = Uuids.timeBased();
        user.setId(new UserId(id));
        user.setTenantId(TenantId.fromUUID(tenantId));
        user.setCustomerId(new CustomerId(customerId));
        if (customerId == NULL_UUID) {
            user.setAuthority(Authority.TENANT_ADMIN);
        } else {
            user.setAuthority(Authority.CUSTOMER_USER);
        }
        String idString = id.toString();
        String email = idString.substring(0, idString.indexOf('-')) + "@thingsboard.org";
        user.setEmail(email);
        return  userDao.save(AbstractServiceTest.SYSTEM_TENANT_ID, user);
    }
}
