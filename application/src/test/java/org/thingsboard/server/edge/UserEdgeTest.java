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
package org.thingsboard.server.edge;

import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.gen.edge.v1.UserCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.v1.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UserUpdateMsg;
import org.thingsboard.server.service.security.model.ChangePasswordRequest;

import java.util.Collections;
import java.util.Optional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class UserEdgeTest extends AbstractEdgeTest {

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    public void testCreateUpdateDeleteTenantUser() throws Exception {
        // create user, activate user and add to tenant admin group
        edgeImitator.expectMessageAmount(3);
        User newTenantAdmin = new User();
        newTenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        newTenantAdmin.setTenantId(tenantId);
        newTenantAdmin.setEmail("tenantAdmin@thingsboard.org");
        newTenantAdmin.setFirstName("Boris");
        newTenantAdmin.setLastName("Johnson");
        EntityGroupInfo tenantAdminsGroup = findTenantAdminsGroup();
        User savedTenantAdmin = createUser(newTenantAdmin, "tenant", tenantAdminsGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages()); // user update msg with entityGroupId and user credentials update msg
        Optional<UserUpdateMsg> latestMessageOpt = edgeImitator.findMessageByType(UserUpdateMsg.class);
        Assert.assertTrue(latestMessageOpt.isPresent());
        UserUpdateMsg userUpdateMsg = latestMessageOpt.get();
        User userMsg = JacksonUtil.fromString(userUpdateMsg.getEntity(), User.class, true);
        Assert.assertNotNull(userMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(savedTenantAdmin.getId(), userMsg.getId());
        Assert.assertEquals(savedTenantAdmin.getAuthority(), userMsg.getAuthority());
        Assert.assertEquals(savedTenantAdmin.getEmail(), userMsg.getEmail());
        Assert.assertEquals(savedTenantAdmin.getFirstName(), userMsg.getFirstName());
        Assert.assertEquals(savedTenantAdmin.getLastName(), userMsg.getLastName());
        Optional<UserCredentialsUpdateMsg> userCredentialsUpdateMsgOpt = edgeImitator.findMessageByType(UserCredentialsUpdateMsg.class);
        Assert.assertTrue(userCredentialsUpdateMsgOpt.isPresent());

        // add custom user group and add user to this group
        EntityGroup edgeUserGroup = createEntityGroupAndAssignToEdge(EntityType.USER, "EdgeUserGroup", tenantId);
        edgeImitator.expectMessageAmount(1);
        addEntitiesToEntityGroup(Collections.singletonList(savedTenantAdmin.getId()), edgeUserGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessageOpt = edgeImitator.findMessageByType(UserUpdateMsg.class);
        Assert.assertTrue(latestMessageOpt.isPresent());
        userUpdateMsg = latestMessageOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(edgeUserGroup.getUuidId().getMostSignificantBits(), userUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(edgeUserGroup.getUuidId().getLeastSignificantBits(), userUpdateMsg.getEntityGroupIdLSB());

        // update user
        edgeImitator.expectMessageAmount(1);
        savedTenantAdmin.setLastName("Borisov");
        savedTenantAdmin = doPost("/api/user", savedTenantAdmin, User.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserUpdateMsg);
        userUpdateMsg = (UserUpdateMsg) latestMessage;
        userMsg = JacksonUtil.fromString(userUpdateMsg.getEntity(), User.class, true);
        Assert.assertNotNull(userMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(savedTenantAdmin.getLastName(), userMsg.getLastName());

        // remove user from custom user group
        edgeImitator.expectMessageAmount(1);
        deleteEntitiesFromEntityGroup(Collections.singletonList(savedTenantAdmin.getId()), edgeUserGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessageOpt = edgeImitator.findMessageByType(UserUpdateMsg.class);
        Assert.assertTrue(latestMessageOpt.isPresent());
        userUpdateMsg = latestMessageOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(edgeUserGroup.getUuidId().getMostSignificantBits(), userUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(edgeUserGroup.getUuidId().getLeastSignificantBits(), userUpdateMsg.getEntityGroupIdLSB());

        // update user credentials
        login(savedTenantAdmin.getEmail(), "tenant");

        edgeImitator.expectMessageAmount(1);
        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        changePasswordRequest.setCurrentPassword("tenant");
        changePasswordRequest.setNewPassword("newTenant");
        doPost("/api/auth/changePassword", changePasswordRequest);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserCredentialsUpdateMsg);
        UserCredentialsUpdateMsg userCredentialsUpdateMsg = (UserCredentialsUpdateMsg) latestMessage;
        UserCredentials userCredentialsMsg = JacksonUtil.fromString(userCredentialsUpdateMsg.getEntity(), UserCredentials.class, true);
        Assert.assertNotNull(userCredentialsMsg);
        Assert.assertEquals(savedTenantAdmin.getId(), userCredentialsMsg.getUserId());
        Assert.assertTrue(passwordEncoder.matches(changePasswordRequest.getNewPassword(), userCredentialsMsg.getPassword()));

        loginTenantAdmin();

        // delete user
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/user/" + savedTenantAdmin.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserUpdateMsg);
        userUpdateMsg = (UserUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(savedTenantAdmin.getUuidId().getMostSignificantBits(), userUpdateMsg.getIdMSB());
        Assert.assertEquals(savedTenantAdmin.getUuidId().getLeastSignificantBits(), userUpdateMsg.getIdLSB());
    }

    @Test
    public void testCreateUpdateDeleteCustomerUser() throws Exception {
        // create customer
        Customer savedCustomer = saveCustomer("Edge Customer", null);
        // create sub customer
        saveCustomer("Edge Sub Customer", savedCustomer.getId());

        // change owner from tenant to parent customer
        changeEdgeOwnerToCustomer(savedCustomer);

        // create user, activate user and add to customer admin group
        edgeImitator.expectMessageAmount(3);
        User customerUser = new User();
        customerUser.setAuthority(Authority.CUSTOMER_USER);
        customerUser.setTenantId(tenantId);
        customerUser.setCustomerId(savedCustomer.getId());
        customerUser.setEmail("customerUser@thingsboard.org");
        customerUser.setFirstName("John");
        customerUser.setLastName("Edwards");
        EntityGroupInfo customerAdminsGroup = findCustomerAdminsGroup(savedCustomer);
        User savedCustomerUser = createUser(customerUser, "customer", customerAdminsGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());  // user update msg with entityGroupId and user credentials update msg
        Optional<UserUpdateMsg> latestMessageOpt = edgeImitator.findMessageByType(UserUpdateMsg.class);
        Assert.assertTrue(latestMessageOpt.isPresent());
        UserUpdateMsg userUpdateMsg = latestMessageOpt.get();
        User userMsg = JacksonUtil.fromString(userUpdateMsg.getEntity(), User.class, true);
        Assert.assertNotNull(userMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomerUser.getId(), userMsg.getId());
        Assert.assertEquals(savedCustomerUser.getCustomerId(), userMsg.getCustomerId());
        Assert.assertEquals(savedCustomerUser.getAuthority(), userMsg.getAuthority());
        Assert.assertEquals(savedCustomerUser.getEmail(), userMsg.getEmail());
        Assert.assertEquals(savedCustomerUser.getFirstName(), userMsg.getFirstName());
        Assert.assertEquals(savedCustomerUser.getLastName(), userMsg.getLastName());
        Optional<UserCredentialsUpdateMsg> userCredentialsUpdateMsgOpt = edgeImitator.findMessageByType(UserCredentialsUpdateMsg.class);
        Assert.assertTrue(userCredentialsUpdateMsgOpt.isPresent());

        // add custom user group and add user to this group
        EntityGroup customUserGroup = createEntityGroupAndAssignToEdge(EntityType.USER, "CustomerEdgeUserGroup", savedCustomer.getId());
        edgeImitator.expectMessageAmount(1);
        addEntitiesToEntityGroup(Collections.singletonList(savedCustomerUser.getId()), customUserGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessageOpt = edgeImitator.findMessageByType(UserUpdateMsg.class);
        Assert.assertTrue(latestMessageOpt.isPresent());
        userUpdateMsg = latestMessageOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(customUserGroup.getUuidId().getMostSignificantBits(), userUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(customUserGroup.getUuidId().getLeastSignificantBits(), userUpdateMsg.getEntityGroupIdLSB());

        // update user
        edgeImitator.expectMessageAmount(1);
        savedCustomerUser.setLastName("Addams");
        savedCustomerUser = doPost("/api/user", savedCustomerUser, User.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserUpdateMsg);
        userUpdateMsg = (UserUpdateMsg) latestMessage;
        userMsg = JacksonUtil.fromString(userUpdateMsg.getEntity(), User.class, true);
        Assert.assertNotNull(userMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomerUser.getLastName(), userMsg.getLastName());

        // remove user from custom user group
        edgeImitator.expectMessageAmount(1);
        deleteEntitiesFromEntityGroup(Collections.singletonList(savedCustomerUser.getId()), customUserGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessageOpt = edgeImitator.findMessageByType(UserUpdateMsg.class);
        Assert.assertTrue(latestMessageOpt.isPresent());
        userUpdateMsg = latestMessageOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(customUserGroup.getUuidId().getMostSignificantBits(), userUpdateMsg.getEntityGroupIdMSB());
        Assert.assertEquals(customUserGroup.getUuidId().getLeastSignificantBits(), userUpdateMsg.getEntityGroupIdLSB());

        unAssignEntityGroupFromEdge(customUserGroup);

        // update user credentials
        login(savedCustomerUser.getEmail(), "customer");

        edgeImitator.expectMessageAmount(1);
        ChangePasswordRequest changePasswordRequest = new ChangePasswordRequest();
        changePasswordRequest.setCurrentPassword("customer");
        changePasswordRequest.setNewPassword("newCustomer");
        doPost("/api/auth/changePassword", changePasswordRequest);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserCredentialsUpdateMsg);
        UserCredentialsUpdateMsg userCredentialsUpdateMsg = (UserCredentialsUpdateMsg) latestMessage;
        UserCredentials userCredentialsMsg = JacksonUtil.fromString(userCredentialsUpdateMsg.getEntity(), UserCredentials.class, true);
        Assert.assertNotNull(userCredentialsMsg);
        Assert.assertEquals(savedCustomerUser.getId(), userCredentialsMsg.getUserId());
        Assert.assertTrue(passwordEncoder.matches(changePasswordRequest.getNewPassword(), userCredentialsMsg.getPassword()));

        loginTenantAdmin();

        // delete user
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/user/" + savedCustomerUser.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserUpdateMsg);
        userUpdateMsg = (UserUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, userUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomerUser.getUuidId().getMostSignificantBits(), userUpdateMsg.getIdMSB());
        Assert.assertEquals(savedCustomerUser.getUuidId().getLeastSignificantBits(), userUpdateMsg.getIdLSB());

        // change owner to tenant
        changeEdgeOwnerFromCustomerToTenant(savedCustomer, 2);

        // delete customers
        doDelete("/api/customer/" + savedCustomer.getUuidId())
                .andExpect(status().isOk());

    }

    @Test
    public void testSendUserCredentialsRequestToCloud() throws Exception {
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        UserCredentialsRequestMsg.Builder userCredentialsRequestMsgBuilder = UserCredentialsRequestMsg.newBuilder();
        userCredentialsRequestMsgBuilder.setUserIdMSB(tenantAdminUserId.getId().getMostSignificantBits());
        userCredentialsRequestMsgBuilder.setUserIdLSB(tenantAdminUserId.getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(userCredentialsRequestMsgBuilder);
        uplinkMsgBuilder.addUserCredentialsRequestMsg(userCredentialsRequestMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserCredentialsUpdateMsg);
        UserCredentialsUpdateMsg userCredentialsUpdateMsg = (UserCredentialsUpdateMsg) latestMessage;
        UserCredentials userCredentialsMsg = JacksonUtil.fromString(userCredentialsUpdateMsg.getEntity(), UserCredentials.class, true);
        Assert.assertNotNull(userCredentialsMsg);
        Assert.assertEquals(tenantAdminUserId, userCredentialsMsg.getUserId());
    }

    @Test
    public void sendUserCredentialsRequest() throws Exception {
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        UserCredentialsRequestMsg.Builder userCredentialsRequestMsgBuilder = UserCredentialsRequestMsg.newBuilder();
        userCredentialsRequestMsgBuilder.setUserIdMSB(tenantAdminUserId.getId().getMostSignificantBits());
        userCredentialsRequestMsgBuilder.setUserIdLSB(tenantAdminUserId.getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(userCredentialsRequestMsgBuilder);
        uplinkMsgBuilder.addUserCredentialsRequestMsg(userCredentialsRequestMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserCredentialsUpdateMsg);
        UserCredentialsUpdateMsg userCredentialsUpdateMsg = (UserCredentialsUpdateMsg) latestMessage;
        UserCredentials userCredentialsMsg = JacksonUtil.fromString(userCredentialsUpdateMsg.getEntity(), UserCredentials.class, true);
        Assert.assertNotNull(userCredentialsMsg);
        Assert.assertEquals(tenantAdminUserId, userCredentialsMsg.getUserId());

        testAutoGeneratedCodeByProtobuf(userCredentialsUpdateMsg);
    }
}
