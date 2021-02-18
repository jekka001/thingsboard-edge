/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.AbstractJpaDaoTest;
import org.thingsboard.server.dao.service.AbstractServiceTest;
import org.thingsboard.server.dao.user.UserDao;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;

/**
 * Created by Valerii Sosliuk on 4/18/2017.
 */
public class JpaUserDaoTest extends AbstractJpaDaoTest {

    @Autowired
    private UserDao userDao;

    @Test
    @DatabaseSetup("classpath:dbunit/user.xml")
    public void testFindAll() {
        List<User> users = userDao.find(AbstractServiceTest.SYSTEM_TENANT_ID);
        assertEquals(users.size(), 5);
    }

    @Test
    @DatabaseSetup("classpath:dbunit/user.xml")
    public void testFindByEmail() {
        User user = userDao.findByEmail(AbstractServiceTest.SYSTEM_TENANT_ID,"sysadm@thingsboard.org");
        assertNotNull("User is expected to be not null", user);
        assertEquals("9cb58ba0-27c1-11e7-93ae-92361f002671", user.getId().toString());
        assertEquals("c97ea14e-27c1-11e7-93ae-92361f002671", user.getTenantId().toString());
        assertEquals("cdf9c79e-27c1-11e7-93ae-92361f002671", user.getCustomerId().toString());
        assertEquals(Authority.SYS_ADMIN, user.getAuthority());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
    }

    @Test
    @DatabaseSetup("classpath:dbunit/empty_dataset.xml")
    public void testFindTenantAdmins() {
        UUID tenantId = Uuids.timeBased();
        UUID customerId = Uuids.timeBased();
        create30Adminsand60Users(tenantId, customerId);
        PageLink pageLink = new PageLink(20);
        PageData<User> tenantAdmins1 = userDao.findTenantAdmins(tenantId, pageLink);
        assertEquals(20, tenantAdmins1.getData().size());
        pageLink = pageLink.nextPageLink();
        PageData<User> tenantAdmins2 = userDao.findTenantAdmins(tenantId,
                pageLink);
        assertEquals(10, tenantAdmins2.getData().size());
        pageLink = pageLink.nextPageLink();
        PageData<User> tenantAdmins3 = userDao.findTenantAdmins(tenantId,
                pageLink);
        assertEquals(0, tenantAdmins3.getData().size());
    }

    @Test
    @DatabaseSetup("classpath:dbunit/empty_dataset.xml")
    public void testFindCustomerUsers() {
        UUID tenantId = Uuids.timeBased();
        UUID customerId = Uuids.timeBased();
        create30Adminsand60Users(tenantId, customerId);
        PageLink pageLink = new PageLink(40);
        PageData<User> customerUsers1 = userDao.findCustomerUsers(tenantId, customerId, pageLink);
        assertEquals(40, customerUsers1.getData().size());
        pageLink = pageLink.nextPageLink();
        PageData<User> customerUsers2 = userDao.findCustomerUsers(tenantId, customerId,
                pageLink);
        assertEquals(20, customerUsers2.getData().size());
        pageLink = pageLink.nextPageLink();
        PageData<User> customerUsers3 = userDao.findCustomerUsers(tenantId, customerId,
                pageLink);
        assertEquals(0, customerUsers3.getData().size());
    }

    @Test
    @DatabaseSetup("classpath:dbunit/user.xml")
    public void testSave() throws IOException {
        User user = new User();
        user.setId(new UserId(UUID.fromString("cd481534-27cc-11e7-93ae-92361f002671")));
        user.setTenantId(new TenantId(UUID.fromString("1edcb2c6-27cb-11e7-93ae-92361f002671")));
        user.setCustomerId(new CustomerId(UUID.fromString("51477cb4-27cb-11e7-93ae-92361f002671")));
        user.setEmail("user@thingsboard.org");
        user.setFirstName("Jackson");
        user.setLastName("Roberts");
        ObjectMapper mapper = new ObjectMapper();
        String additionalInfo = "{\"key\":\"value-100\"}";
        JsonNode jsonNode = mapper.readTree(additionalInfo);
        user.setAdditionalInfo(jsonNode);
        userDao.save(AbstractServiceTest.SYSTEM_TENANT_ID,user);
        assertEquals(6, userDao.find(AbstractServiceTest.SYSTEM_TENANT_ID).size());
        User savedUser = userDao.findByEmail(AbstractServiceTest.SYSTEM_TENANT_ID,"user@thingsboard.org");
        assertNotNull(savedUser);
        assertEquals(additionalInfo, savedUser.getAdditionalInfo().toString());
    }

    private void create30Adminsand60Users(UUID tenantId, UUID customerId) {
        // Create 30 tenant admins and 60 customer users
        for (int i = 0; i < 30; i++) {
            saveUser(tenantId, NULL_UUID);
            saveUser(tenantId, customerId);
            saveUser(tenantId, customerId);
        }
    }

    private void saveUser(UUID tenantId, UUID customerId) {
        User user = new User();
        UUID id = Uuids.timeBased();
        user.setId(new UserId(id));
        user.setTenantId(new TenantId(tenantId));
        user.setCustomerId(new CustomerId(customerId));
        if (customerId == NULL_UUID) {
            user.setAuthority(Authority.TENANT_ADMIN);
        } else {
            user.setAuthority(Authority.CUSTOMER_USER);
        }
        String idString = id.toString();
        String email = idString.substring(0, idString.indexOf('-')) + "@thingsboard.org";
        user.setEmail(email);
        userDao.save(AbstractServiceTest.SYSTEM_TENANT_ID,user);
    }
}
