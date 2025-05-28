/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.AbstractMessage;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EdgeUpgradeInfo;
import org.thingsboard.server.common.data.EntitySubtype;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.domain.Domain;
import org.thingsboard.server.common.data.domain.DomainInfo;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.menu.CustomMenuConfig;
import org.thingsboard.server.common.data.menu.CustomMenuItem;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.common.data.security.model.JwtSettings;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.common.data.wl.WhiteLabelingType;
import org.thingsboard.server.dao.edge.EdgeDao;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.edge.imitator.EdgeImitator;
import org.thingsboard.server.exception.DataValidationException;
import org.thingsboard.server.gen.edge.v1.AdminSettingsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CustomTranslationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeConfiguration;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.EntityGroupUpdateMsg;
import org.thingsboard.server.gen.edge.v1.GroupPermissionProto;
import org.thingsboard.server.gen.edge.v1.OAuth2ClientUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OAuth2DomainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.QueueUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RoleProto;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.SyncCompletedMsg;
import org.thingsboard.server.gen.edge.v1.TenantProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.TenantUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UserUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WhiteLabelingProto;
import org.thingsboard.server.service.edge.instructions.EdgeUpgradeInstructionsService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.server.dao.customer.CustomerServiceImpl.PUBLIC_CUSTOMER_SUFFIX;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.edge.AbstractEdgeTest.CONNECT_MESSAGE_COUNT;

@TestPropertySource(properties = {
        "edges.enabled=true",
        "queue.rule-engine.stats.enabled=false"
})
@ContextConfiguration(classes = {EdgeControllerTest.Config.class})
@DaoSqlTest
@Slf4j
public class EdgeControllerTest extends AbstractControllerTest {

    public static final String EDGE_HOST = "localhost";
    public static final int EDGE_PORT = 7070;
    private static final String SYSADMIN_EDGE_DOMAIN = "sysadmin.edge.domain";
    private static final String TENANT_EDGE_DOMAIN = "tenant.edge.domain";
    private static final String CUSTOMER_EDGE_DOMAIN = "customer.edge.domain";

    private Domain sysAdminDomain;
    private Domain tenantDomain;

    private final IdComparator<Edge> idComparator = new IdComparator<>();

    ListeningExecutorService executor;

    List<ListenableFuture<Edge>> futures;

    @Autowired
    private EdgeDao edgeDao;

    @Autowired
    private EdgeUpgradeInstructionsService edgeUpgradeInstructionsService;

    static class Config {
        @Bean
        @Primary
        public EdgeDao edgeDao(EdgeDao edgeDao) {
            return Mockito.mock(EdgeDao.class, AdditionalAnswers.delegatesTo(edgeDao));
        }

    }

    @Before
    public void setupEdgeTest() throws Exception {
        executor = MoreExecutors.listeningDecorator(ThingsBoardExecutors.newWorkStealingPool(8, getClass()));
        loginTenantAdmin();
    }

    @After
    public void teardownEdgeTest() {
        executor.shutdownNow();
    }

    @Test
    public void testSaveEdge() throws Exception {
        Edge edge = constructEdge("My edge", "default");

        Mockito.reset(tbClusterService, auditLogService);

        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Assert.assertNotNull(savedEdge);
        Assert.assertNotNull(savedEdge.getId());
        Assert.assertTrue(savedEdge.getCreatedTime() > 0);
        Assert.assertEquals(tenantId, savedEdge.getTenantId());
        Assert.assertNotNull(savedEdge.getCustomerId());
        Assert.assertEquals(NULL_UUID, savedEdge.getCustomerId().getId());
        Assert.assertEquals(edge.getName(), savedEdge.getName());
        Assert.assertTrue(StringUtils.isNoneBlank(savedEdge.getEdgeLicenseKey()));
        Assert.assertTrue(StringUtils.isNoneBlank(savedEdge.getCloudEndpoint()));

        testNotifyEdgeStateChangeEventManyTimeMsgToEdgeServiceNever(savedEdge, savedEdge.getId(), savedEdge.getId(),
                tenantId, tenantAdminUser.getCustomerId(), tenantAdminUser.getId(), tenantAdminUser.getEmail(),
                ActionType.ADDED, 2);

        savedEdge.setName("My new edge");
        doPost("/api/edge", savedEdge, Edge.class);

        Edge foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(foundEdge.getName(), savedEdge.getName());

        testNotifyEdgeStateChangeEventManyTimeMsgToEdgeServiceNever(foundEdge, foundEdge.getId(), foundEdge.getId(),
                tenantId, tenantAdminUser.getCustomerId(), tenantAdminUser.getId(), tenantAdminUser.getEmail(),
                ActionType.UPDATED, 1);
    }

    @Test
    public void testSaveEdgeWithViolationOfLengthValidation() throws Exception {
        Edge edge = constructEdge(StringUtils.randomAlphabetic(300), "default");
        String msgError = msgErrorFieldLength("name");

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(edge, tenantId,
                tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        msgError = msgErrorFieldLength("type");
        edge.setName("normal name");
        edge.setType(StringUtils.randomAlphabetic(300));
        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(edge, tenantId,
                tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.ADDED, new DataValidationException(msgError));
        Mockito.reset(tbClusterService, auditLogService);

        msgError = msgErrorFieldLength("label");
        edge.setType("normal type");
        edge.setLabel(StringUtils.randomAlphabetic(300));
        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(edge, tenantId,
                tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testFindEdgeById() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);
        Edge foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertNotNull(foundEdge);
        Assert.assertEquals(savedEdge, foundEdge);
    }

    @Test
    public void testFindEdgeTypesByTenantId() throws Exception {
        int cntEntity = 3;

        Mockito.reset(tbClusterService, auditLogService);

        for (int i = 0; i < cntEntity; i++) {
            Edge edge = constructEdge("My edge B" + i, "typeB");
            doPost("/api/edge", edge, Edge.class);
        }

        testNotifyManyEntityManyTimeMsgToEdgeServiceNeverAdditionalInfoAny(new Edge(), new Edge(),
                tenantId, tenantAdminUser.getCustomerId(), tenantAdminUser.getId(), tenantAdminUser.getEmail(),
                ActionType.ADDED, cntEntity, 0);

        for (int i = 0; i < 7; i++) {
            Edge edge = constructEdge("My edge C" + i, "typeC");
            doPost("/api/edge", edge, Edge.class);
        }
        for (int i = 0; i < 9; i++) {
            Edge edge = constructEdge("My edge A" + i, "typeA");
            doPost("/api/edge", edge, Edge.class);
        }
        List<EntitySubtype> edgeTypes = doGetTyped("/api/edge/types",
                new TypeReference<>() {
                });

        Assert.assertNotNull(edgeTypes);
        Assert.assertEquals(3, edgeTypes.size());
        Assert.assertEquals("typeA", edgeTypes.get(0).getType());
        Assert.assertEquals("typeB", edgeTypes.get(1).getType());
        Assert.assertEquals("typeC", edgeTypes.get(2).getType());
    }

    @Test
    public void testDeleteEdge() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Mockito.reset(tbClusterService, auditLogService);

        doDelete("/api/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isOk());

        testNotifyEntityBroadcastEntityStateChangeEventManyTimeMsgToEdgeServiceNever(savedEdge, savedEdge.getId(), savedEdge.getId(),
                tenantId, tenantAdminUser.getCustomerId(), tenantAdminUser.getId(), tenantAdminUser.getEmail(),
                ActionType.DELETED, 1, savedEdge.getId().getId().toString());

        doGet("/api/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(statusReason(containsString(msgErrorNoFound("Edge", savedEdge.getId().getId().toString()))));
    }

    @Test
    public void testSaveEdgeWithEmptyType() throws Exception {
        Edge edge = constructEdge("My edge", null);

        Mockito.reset(tbClusterService, auditLogService);

        String msgError = "Edge type " + msgErrorShouldBeSpecified;
        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(edge, tenantId,
                tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.ADDED, new DataValidationException(msgError));
    }

    @Test
    public void testSaveEdgeWithEmptyName() throws Exception {
        Edge edge = constructEdge(null, "default");

        Mockito.reset(tbClusterService, auditLogService);

        String msgError = "Edge name " + msgErrorShouldBeSpecified;
        doPost("/api/edge", edge)
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityEqualsOneTimeServiceNeverError(edge, tenantId,
                tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.ADDED, new DataValidationException(msgError));
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testAssignUnassignEdgeToCustomer() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Customer customer = new Customer();
        customer.setTitle("My customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        Mockito.reset(tbClusterService, auditLogService);

        Edge assignedEdge = doPost("/api/customer/" + savedCustomer.getId().getId().toString()
                + "/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(savedCustomer.getId(), assignedEdge.getCustomerId());

        testNotifyEntityAllOneTimeLogEntityActionEntityEqClass(assignedEdge, assignedEdge.getId(), assignedEdge.getId(),
                tenantId, savedCustomer.getId(), tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.ASSIGNED_TO_CUSTOMER,
                ActionType.ASSIGNED_TO_CUSTOMER, assignedEdge.getId().getId().toString(), savedCustomer.getId().getId().toString(), savedCustomer.getTitle());

        Edge foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(savedCustomer.getId(), foundEdge.getCustomerId());

        Edge unassignedEdge =
                doDelete("/api/customer/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(ModelConstants.NULL_UUID, unassignedEdge.getCustomerId().getId());

        testNotifyEntityAllOneTimeLogEntityActionEntityEqClass(unassignedEdge, unassignedEdge.getId(), unassignedEdge.getId(),
                tenantId, savedCustomer.getId(), tenantAdminUser.getId(), tenantAdminUser.getEmail(), ActionType.UNASSIGNED_FROM_CUSTOMER,
                ActionType.UNASSIGNED_FROM_CUSTOMER, unassignedEdge.getId().getId().toString(), savedCustomer.getId().getId().toString(), savedCustomer.getTitle());

        foundEdge = doGet("/api/edge/" + savedEdge.getId().getId().toString(), Edge.class);
        Assert.assertEquals(ModelConstants.NULL_UUID, foundEdge.getCustomerId().getId());
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testAssignEdgeToNonExistentCustomer() throws Exception {
        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Mockito.reset(tbClusterService, auditLogService);

        CustomerId customerId = new CustomerId(Uuids.timeBased());
        String customerIdStr = customerId.getId().toString();

        String msgError = msgErrorNoFound("Customer", customerIdStr);
        doPost("/api/customer/" + customerIdStr + "/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(statusReason(containsString(msgError)));

        testNotifyEntityNever(savedEdge.getId(), savedEdge);
        testNotifyEntityNever(customerId, new Customer());
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testAssignEdgeToCustomerFromDifferentTenant() throws Exception {
        loginSysAdmin();

        Tenant tenant2 = new Tenant();
        tenant2.setTitle("Different tenant");
        Tenant savedTenant2 = saveTenant(tenant2);
        Assert.assertNotNull(savedTenant2);

        User tenantAdmin2 = new User();
        tenantAdmin2.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin2.setTenantId(savedTenant2.getId());
        tenantAdmin2.setEmail("tenant3@thingsboard.org");
        tenantAdmin2.setFirstName("Joe");
        tenantAdmin2.setLastName("Downs");

        createUserAndLogin(tenantAdmin2, "testPassword1");

        Customer customer = new Customer();
        customer.setTitle("Different customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        loginTenantAdmin();

        Edge edge = constructEdge("My edge", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        Mockito.reset(tbClusterService, auditLogService);

        doPost("/api/customer/" + savedCustomer.getId().getId().toString()
                + "/edge/" + savedEdge.getId().getId().toString())
                .andExpect(status().isForbidden())
                .andExpect(statusReason(containsString("msgErrorPermission")));

        testNotifyEntityNever(savedEdge.getId(), savedEdge);
        testNotifyEntityNever(savedCustomer.getId(), savedCustomer);

        loginSysAdmin();

        deleteTenant(savedTenant2.getId());
    }

    @Test
    public void testFindTenantEdges() throws Exception {
        int cntEntity = 178;
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            Edge edge = constructEdge("Edge" + i, "default");
            futures.add(executor.submit(() ->
                    doPost("/api/edge", edge, Edge.class)));
        }
        List<Edge> edges = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));
        List<Edge> loadedEdges = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<Edge> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedEdges.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edges.sort(idComparator);
        loadedEdges.sort(idComparator);

        Assert.assertEquals(edges, loadedEdges);
    }

    @Test
    public void testFindTenantEdgesByName() throws Exception {
        String title1 = "Edge title 1";
        int cntEntity = 143;
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            futures.add(executor.submit(() ->
                    doPost("/api/edge", edge, Edge.class)));
        }
        List<Edge> edgesTitle1 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        String title2 = "Edge title 2";
        cntEntity = 75;
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            futures.add(executor.submit(() ->
                    doPost("/api/edge", edge, Edge.class)));
        }
        List<Edge> edgesTitle2 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        List<Edge> loadedEdgesTitle1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Edge> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedEdgesTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesTitle1.sort(idComparator);
        loadedEdgesTitle1.sort(idComparator);

        Assert.assertEquals(edgesTitle1, loadedEdgesTitle1);

        List<Edge> loadedEdgesTitle2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedEdgesTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesTitle2.sort(idComparator);
        loadedEdgesTitle2.sort(idComparator);

        Assert.assertEquals(edgesTitle2, loadedEdgesTitle2);

        for (Edge edge : loadedEdgesTitle1) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesTitle2) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    @Test
    public void testFindTenantEdgesByType() throws Exception {
        String title1 = "Edge title 1";
        String type1 = "typeA";
        int cntEntity = 143;
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type1);
            futures.add(executor.submit(() ->
                    doPost("/api/edge", edge, Edge.class)));
        }
        List<Edge> edgesType1 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        String title2 = "Edge title 2";
        String type2 = "typeB";
        cntEntity = 75;
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type2);
            futures.add(executor.submit(() ->
                    doPost("/api/edge", edge, Edge.class)));
        }
        List<Edge> edgesType2 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        List<Edge> loadedEdgesType1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15);
        PageData<Edge> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                    new TypeReference<>() {
                    }, pageLink, type1);
            loadedEdgesType1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesType1.sort(idComparator);
        loadedEdgesType1.sort(idComparator);

        Assert.assertEquals(edgesType1, loadedEdgesType1);

        List<Edge> loadedEdgesType2 = new ArrayList<>();
        pageLink = new PageLink(4);
        do {
            pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                    new TypeReference<>() {
                    }, pageLink, type2);
            loadedEdgesType2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesType2.sort(idComparator);
        loadedEdgesType2.sort(idComparator);

        Assert.assertEquals(edgesType2, loadedEdgesType2);

        for (Edge edge : loadedEdgesType1) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                new TypeReference<>() {
                }, pageLink, type1);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesType2) {
            doDelete("/api/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4);
        pageData = doGetTypedWithPageLink("/api/tenant/edges?type={type}&",
                new TypeReference<>() {
                }, pageLink, type2);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testFindCustomerEdges() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        Mockito.reset(tbClusterService, auditLogService);

        int cntEntity = 128;
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            Edge edge = constructEdge("Edge" + i, "default");
            futures.add(executor.submit(() -> {
                Edge edge1 = doPost("/api/edge", edge, Edge.class);
                return doPost("/api/customer/" + customerId.getId().toString()
                        + "/edge/" + edge1.getId().getId().toString(), Edge.class);
            }));
        }
        List<Edge> edges = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        testNotifyManyEntityManyTimeMsgToEdgeServiceEntityEqAny(new Edge(), new Edge(),
                tenantId, customerId, tenantAdminUser.getId(), tenantAdminUser.getEmail(),
                ActionType.ASSIGNED_TO_CUSTOMER, cntEntity, cntEntity, cntEntity * 2, "", "", "");

        List<Edge> loadedEdges = new ArrayList<>();
        PageLink pageLink = new PageLink(23);
        PageData<Edge> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedEdges.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edges.sort(idComparator);
        loadedEdges.sort(idComparator);

        Assert.assertEquals(edges, loadedEdges);
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testFindCustomerEdgesByName() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        int cntEntity = 125;
        String title1 = "Edge title 1";
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            futures.add(executor.submit(() -> {
                Edge edge1 = doPost("/api/edge", edge, Edge.class);
                return doPost("/api/customer/" + customerId.getId().toString()
                        + "/edge/" + edge1.getId().getId().toString(), Edge.class);
            }));
        }
        List<Edge> edgesTitle1 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        cntEntity = 143;
        String title2 = "Edge title 2";
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, "default");
            futures.add(executor.submit(() -> {
                Edge edge1 = doPost("/api/edge", edge, Edge.class);
                return doPost("/api/customer/" + customerId.getId().toString()
                        + "/edge/" + edge1.getId().getId().toString(), Edge.class);
            }));
        }
        List<Edge> edgesTitle2 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        List<Edge> loadedEdgesTitle1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Edge> pageData = null;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedEdgesTitle1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesTitle1.sort(idComparator);
        loadedEdgesTitle1.sort(idComparator);

        Assert.assertEquals(edgesTitle1, loadedEdgesTitle1);

        List<Edge> loadedEdgesTitle2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                    new TypeReference<>() {
                    }, pageLink);
            loadedEdgesTitle2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(edgesTitle2, idComparator);
        Collections.sort(loadedEdgesTitle2, idComparator);

        Assert.assertEquals(edgesTitle2, loadedEdgesTitle2);

        Mockito.reset(tbClusterService, auditLogService);

        for (Edge edge : loadedEdgesTitle1) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        cntEntity = loadedEdgesTitle1.size();
        testNotifyManyEntityManyTimeMsgToEdgeServiceEntityEqAnyAdditionalInfoAny(new Edge(), new Edge(),
                tenantId, customerId, tenantAdminUser.getId(), tenantAdminUser.getEmail(),
                ActionType.UNASSIGNED_FROM_CUSTOMER, ActionType.UNASSIGNED_FROM_CUSTOMER, cntEntity, cntEntity, 3);

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesTitle2) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?",
                new TypeReference<>() {
                }, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testFindCustomerEdgesByType() throws Exception {
        Customer customer = new Customer();
        customer.setTitle("Test customer");
        customer = doPost("/api/customer", customer, Customer.class);
        CustomerId customerId = customer.getId();

        int cntEntity = 125;
        String title1 = "Edge title 1";
        String type1 = "typeC";
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title1 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type1);
            futures.add(executor.submit(() -> {
                Edge edge1 = doPost("/api/edge", edge, Edge.class);
                return doPost("/api/customer/" + customerId.getId().toString()
                        + "/edge/" + edge1.getId().getId().toString(), Edge.class);
            }));
        }
        List<Edge> edgesType1 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        cntEntity = 143;
        String title2 = "Edge title 2";
        String type2 = "typeD";
        futures = new ArrayList<>(cntEntity);
        for (int i = 0; i < cntEntity; i++) {
            String suffix = StringUtils.randomAlphanumeric(15);
            String name = title2 + suffix;
            name = i % 2 == 0 ? name.toLowerCase() : name.toUpperCase();
            Edge edge = constructEdge(name, type2);
            futures.add(executor.submit(() -> {
                Edge edge1 = doPost("/api/edge", edge, Edge.class);
                return doPost("/api/customer/" + customerId.getId().toString()
                        + "/edge/" + edge1.getId().getId().toString(), Edge.class);
            }));
        }
        List<Edge> edgesType2 = new ArrayList<>(Futures.allAsList(futures).get(TIMEOUT, TimeUnit.SECONDS));

        List<Edge> loadedEdgesType1 = new ArrayList<>();
        PageLink pageLink = new PageLink(15, 0, title1);
        PageData<Edge> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                    new TypeReference<>() {
                    }, pageLink, type1);
            loadedEdgesType1.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesType1.sort(idComparator);
        loadedEdgesType1.sort(idComparator);

        Assert.assertEquals(edgesType1, loadedEdgesType1);

        List<Edge> loadedEdgesType2 = new ArrayList<>();
        pageLink = new PageLink(4, 0, title2);
        do {
            pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                    new TypeReference<>() {
                    }, pageLink, type2);
            loadedEdgesType2.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        edgesType2.sort(idComparator);
        loadedEdgesType2.sort(idComparator);

        Assert.assertEquals(edgesType2, loadedEdgesType2);

        for (Edge edge : loadedEdgesType1) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title1);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                new TypeReference<>() {
                }, pageLink, type1);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());

        for (Edge edge : loadedEdgesType2) {
            doDelete("/api/customer/edge/" + edge.getId().getId().toString())
                    .andExpect(status().isOk());
        }

        pageLink = new PageLink(4, 0, title2);
        pageData = doGetTypedWithPageLink("/api/customer/" + customerId.getId().toString() + "/edges?type={type}&",
                new TypeReference<>() {
                }, pageLink, type2);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(0, pageData.getData().size());
    }

    // keeping CE test for merge compatibility
    // @Test
    public void testSyncEdge() throws Exception {
        loginSysAdmin();
        // get jwt settings from yaml config
        JwtSettings settings = doGet("/api/admin/jwtSettings", JwtSettings.class);
        // save jwt settings into db
        doPost("/api/admin/jwtSettings", settings).andExpect(status().isOk());
        loginTenantAdmin();

        Edge edge = doPost("/api/edge", constructEdge("Test Sync Edge", "test"), Edge.class);

        Asset asset = new Asset();
        asset.setName("Test Sync Edge Asset 1");
        asset.setType("default");
        Asset savedAsset = doPost("/api/asset", asset, Asset.class);

        Device device = new Device();
        device.setName("Test Sync Edge Device 1");
        device.setType("default");
        Device savedDevice = doPost("/api/device", device, Device.class);

        // create public customer
        //1 message
        // Customer
        doPost("/api/customer/public/device/" + savedDevice.getId().getId(), Device.class);
        doDelete("/api/customer/device/" + savedDevice.getId().getId(), Device.class);

        simulateEdgeActivation(edge);

        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/device/" + savedDevice.getId().getId().toString(), Device.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/asset/" + savedAsset.getId().getId().toString(), Asset.class);

        EdgeImitator edgeImitator = new EdgeImitator(EDGE_HOST, EDGE_PORT, edge.getRoutingKey(), edge.getSecret());
        edgeImitator.ignoreType(OAuth2ClientUpdateMsg.class);
        edgeImitator.ignoreType(OAuth2DomainUpdateMsg.class);

        // 27 connect message
        // + 1 Customer
        // + 5 fetchers messages (DeviceProfile, Device, DeviceCredentials, AssetProfile, Asset) in sync process
        // + 5 queue messages the same
        edgeImitator.expectMessageAmount(CONNECT_MESSAGE_COUNT + 11);
        edgeImitator.connect();
        edgeImitator.waitForMessages();

        verifyFetchersMsgs(edgeImitator, savedDevice);
        // verify queue msgs
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Test Sync Edge Device 1"));
        Assert.assertTrue(popDeviceCredentialsMsg(edgeImitator.getDownlinkMsgs(), savedDevice.getId()));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Test Sync Edge Asset 1"));
        printQueueMsgsIfNotEmpty(edgeImitator);

        // 27 connect messages
        // +1 Customer
        // + 5 fetchers messages (DeviceProfile, Device, DeviceCredentials, AssetProfile, Asset) in sync process
        edgeImitator.expectMessageAmount(CONNECT_MESSAGE_COUNT + 6);
        doPost("/api/edge/sync/" + edge.getId()).andExpect(status().isOk());
        edgeImitator.waitForMessages();

        verifyFetchersMsgs(edgeImitator, savedDevice);
        printQueueMsgsIfNotEmpty(edgeImitator);

        edgeImitator.allowIgnoredTypes();
        try {
            edgeImitator.disconnect();
        } catch (Exception ignored) {
        }

        doDelete("/api/device/" + savedDevice.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/asset/" + savedAsset.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/edge/" + edge.getId().getId().toString())
                .andExpect(status().isOk());
    }

    private static void printQueueMsgsIfNotEmpty(EdgeImitator edgeImitator) {
        if (!edgeImitator.getDownlinkMsgs().isEmpty()) {
            for (AbstractMessage downlinkMsg : edgeImitator.getDownlinkMsgs()) {
                log.warn("Unexpected message in the queue: {}", downlinkMsg);
            }
        }
        Assert.assertTrue(edgeImitator.getDownlinkMsgs().isEmpty());
    }

    private RuleChainId getEdgeRootRuleChainId(EdgeImitator edgeImitator) {
        try {
            EdgeId edgeId = new EdgeId(new UUID(edgeImitator.getConfiguration().getEdgeIdMSB(), edgeImitator.getConfiguration().getEdgeIdLSB()));
            List<RuleChain> edgeRuleChains = doGetTypedWithPageLink("/api/edge/" + edgeId.getId() + "/ruleChains?",
                    new TypeReference<PageData<RuleChain>>() {
                    }, new PageLink(100)).getData();
            for (RuleChain edgeRuleChain : edgeRuleChains) {
                if (edgeRuleChain.isRoot()) {
                    return edgeRuleChain.getId();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("Root rule chain not found");
    }

    private void simulateEdgeActivation(Edge edge) throws Exception {
        Awaitility.await()
                .atMost(TIMEOUT, TimeUnit.SECONDS)
                .until(() -> {
                    List<RuleChain> ruleChains = getEdgeRuleChains(edge.getId());
                    return ruleChains.size() == 1 && ruleChains.get(0).getId().equals(edge.getRootRuleChainId());
                });

        ObjectNode attributes = JacksonUtil.newObjectNode();
        attributes.put("active", true);
        doPost("/api/plugins/telemetry/EDGE/" + edge.getId() + "/attributes/" + AttributeScope.SERVER_SCOPE, attributes);
        Awaitility.await()
                .atMost(TIMEOUT, TimeUnit.SECONDS)
                .until(() -> {
                    List<Map<String, Object>> values = doGetAsyncTyped("/api/plugins/telemetry/EDGE/" + edge.getId() +
                            "/values/attributes/SERVER_SCOPE", new TypeReference<>() {});
                    Optional<Map<String, Object>> activeAttrOpt = values.stream().filter(att -> att.get("key").equals("active")).findFirst();
                    if (activeAttrOpt.isEmpty()) {
                        return false;
                    }
                    List<RuleChain> ruleChains = getEdgeRuleChains(edge.getId());
                    Map<String, Object> activeAttr = activeAttrOpt.get();
                    return "true".equals(activeAttr.get("value").toString()) && ruleChains.size() == 1;
                });
    }

    private void verifyFetchersMsgs(EdgeImitator edgeImitator, Device savedDevice) {
        Assert.assertTrue(popQueueMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Main"));
        Assert.assertTrue(popRuleChainMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Edge Root Rule Chain"));
        Assert.assertTrue(popRuleChainMetadataMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, getEdgeRootRuleChainId(edgeImitator)));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "general"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "mail"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "connectivity"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "jwt"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popUserCredentialsMsg(edgeImitator.getDownlinkMsgs(), currentUserId));
        Assert.assertTrue(popUserMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, TENANT_ADMIN_EMAIL, Authority.TENANT_ADMIN));
        Assert.assertTrue(popCustomerMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Public"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Test Sync Edge Device 1"));
        Assert.assertTrue(popDeviceCredentialsMsg(edgeImitator.getDownlinkMsgs(), savedDevice.getId()));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Test Sync Edge Asset 1"));
        Assert.assertTrue(popTenantMsg(edgeImitator.getDownlinkMsgs(), tenantId));
        Assert.assertTrue(popTenantProfileMsg(edgeImitator.getDownlinkMsgs(), tenantProfileId));
        Assert.assertTrue(popSyncCompletedMsg(edgeImitator.getDownlinkMsgs()));
    }

    private boolean popQueueMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof QueueUpdateMsg queueUpdateMsg) {
                Queue queue = JacksonUtil.fromString(queueUpdateMsg.getEntity(), Queue.class, true);
                Assert.assertNotNull(queue);
                if (msgType.equals(queueUpdateMsg.getMsgType()) && name.equals(queue.getName())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popRuleChainMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof RuleChainUpdateMsg ruleChainUpdateMsg) {
                RuleChain ruleChain = JacksonUtil.fromString(ruleChainUpdateMsg.getEntity(), RuleChain.class, true);
                Assert.assertNotNull(ruleChain);
                if (msgType.equals(ruleChainUpdateMsg.getMsgType())
                        && name.equals(ruleChain.getName())
                        && ruleChain.isRoot()) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popRuleChainMetadataMsg(List<AbstractMessage> messages, UpdateMsgType msgType, RuleChainId ruleChainId) {
        for (AbstractMessage message : messages) {
            if (message instanceof RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg) {
                RuleChainMetaData ruleChainMetaData = JacksonUtil.fromString(ruleChainMetadataUpdateMsg.getEntity(), RuleChainMetaData.class, true);
                Assert.assertNotNull(ruleChainMetaData);
                if (msgType.equals(ruleChainMetadataUpdateMsg.getMsgType())
                        && ruleChainId.equals(ruleChainMetaData.getRuleChainId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popAdminSettingsMsg(List<AbstractMessage> messages, String key) {
        for (AbstractMessage message : messages) {
            if (message instanceof AdminSettingsUpdateMsg adminSettingsUpdateMsg) {
                AdminSettings adminSettings = JacksonUtil.fromString(adminSettingsUpdateMsg.getEntity(), AdminSettings.class, true);
                Assert.assertNotNull(adminSettings);
                if (key.equals(adminSettings.getKey())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popDeviceProfileMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof DeviceProfileUpdateMsg deviceProfileUpdateMsg) {
                DeviceProfile deviceProfile = JacksonUtil.fromString(deviceProfileUpdateMsg.getEntity(), DeviceProfile.class, true);
                Assert.assertNotNull(deviceProfile);
                if (msgType.equals(deviceProfileUpdateMsg.getMsgType())
                        && name.equals(deviceProfile.getName())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popDeviceMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof DeviceUpdateMsg deviceUpdateMsg) {
                Device device = JacksonUtil.fromString(deviceUpdateMsg.getEntity(), Device.class, true);
                Assert.assertNotNull(device);
                if (msgType.equals(deviceUpdateMsg.getMsgType())
                        && name.equals(device.getName())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popDeviceCredentialsMsg(List<AbstractMessage> messages, DeviceId deviceId) {
        for (AbstractMessage message : messages) {
            if (message instanceof DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg) {
                DeviceCredentials deviceCredentials = JacksonUtil.fromString(deviceCredentialsUpdateMsg.getEntity(), DeviceCredentials.class, true);
                Assert.assertNotNull(deviceCredentials);
                if (deviceId.equals(deviceCredentials.getDeviceId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popAssetProfileMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof AssetProfileUpdateMsg assetProfileUpdateMsg) {
                AssetProfile assetProfile = JacksonUtil.fromString(assetProfileUpdateMsg.getEntity(), AssetProfile.class, true);
                Assert.assertNotNull(assetProfile);
                if (msgType.equals(assetProfileUpdateMsg.getMsgType())
                        && name.equals(assetProfile.getName())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popAssetMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof AssetUpdateMsg assetUpdateMsg) {
                Asset asset = JacksonUtil.fromString(assetUpdateMsg.getEntity(), Asset.class, true);
                Assert.assertNotNull(asset);
                if (msgType.equals(assetUpdateMsg.getMsgType())
                        && name.equals(asset.getName())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popUserCredentialsMsg(List<AbstractMessage> messages, UserId userId) {
        for (AbstractMessage message : messages) {
            if (message instanceof UserCredentialsUpdateMsg userCredentialsUpdateMsg) {
                UserCredentials userCredentials = JacksonUtil.fromString(userCredentialsUpdateMsg.getEntity(), UserCredentials.class, true);
                Assert.assertNotNull(userCredentials);
                if (userId.equals(userCredentials.getUserId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popUserMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String email, Authority authority) {
        for (AbstractMessage message : messages) {
            if (message instanceof UserUpdateMsg userUpdateMsg) {
                User user = JacksonUtil.fromString(userUpdateMsg.getEntity(), User.class, true);
                Assert.assertNotNull(user);
                if (msgType.equals(userUpdateMsg.getMsgType())
                        && email.equals(user.getEmail())
                        && authority.equals(user.getAuthority())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popCustomerMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String title) {
        for (AbstractMessage message : messages) {
            if (message instanceof CustomerUpdateMsg customerUpdateMsg) {
                Customer customer = JacksonUtil.fromString(customerUpdateMsg.getEntity(), Customer.class, true);
                Assert.assertNotNull(customer);
                if (msgType.equals(customerUpdateMsg.getMsgType())
                        && title.equals(customer.getTitle())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popCustomerMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String title, String ownerType, UUID ownerUUID) {
        for (AbstractMessage message : messages) {
            if (message instanceof CustomerUpdateMsg customerUpdateMsg) {
                Customer customer = JacksonUtil.fromString(customerUpdateMsg.getEntity(), Customer.class, true);
                Assert.assertNotNull(customer);
                if (msgType.equals(customerUpdateMsg.getMsgType())
                        && title.equals(customer.getTitle())
                        && ownerType.equals(customer.getOwnerId().getEntityType().name())
                        && ownerUUID.getMostSignificantBits() == customer.getOwnerId().getId().getMostSignificantBits()
                        && ownerUUID.getLeastSignificantBits() == customer.getOwnerId().getId().getLeastSignificantBits()) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;

    }

    private boolean popRoleMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name, RoleType type) {
        for (AbstractMessage message : messages) {
            if (message instanceof RoleProto roleProto) {
                Role role = JacksonUtil.fromString(roleProto.getEntity(), Role.class, true);
                Assert.assertNotNull(role);
                if (msgType.equals(roleProto.getMsgType())
                        && name.equals(role.getName())
                        && type.equals(role.getType())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private RoleId findRoleId(List<AbstractMessage> messages, UpdateMsgType msgType, String name, RoleType type) {
        for (AbstractMessage message : messages) {
            if (message instanceof RoleProto roleProto) {
                Role role = JacksonUtil.fromString(roleProto.getEntity(), Role.class, true);
                Assert.assertNotNull(role);
                if (msgType.equals(roleProto.getMsgType())
                        && name.equals(role.getName())
                        && type.equals(role.getType())) {
                    return role.getId();
                }
            }
        }
        return null;
    }

    private boolean popDomainMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name, TenantId tenantId, CustomerId customerId) {
        for (AbstractMessage message : messages) {
            if (message instanceof OAuth2DomainUpdateMsg oAuth2DomainUpdateMsg) {
                DomainInfo domainInfo = JacksonUtil.fromString(oAuth2DomainUpdateMsg.getEntity(), DomainInfo.class, true);
                Assert.assertNotNull(domainInfo);
                if (msgType.equals(oAuth2DomainUpdateMsg.getMsgType())
                        && name.equals(domainInfo.getName())
                        && tenantId.equals(domainInfo.getTenantId())
                        && customerId.equals(domainInfo.getCustomerId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popSyncCompletedMsg(List<AbstractMessage> messages) {
        for (AbstractMessage message : messages) {
            if (message instanceof SyncCompletedMsg) {
                messages.remove(message);
                return true;
            }
        }
        return false;
    }

    private boolean popEntityGroupMsg(List<AbstractMessage> messages, UpdateMsgType msgType, String name, EntityType type, EntityType ownerType) {
        for (AbstractMessage message : messages) {
            if (message instanceof EntityGroupUpdateMsg entityGroupUpdateMsg) {
                EntityGroup entityGroup = JacksonUtil.fromString(entityGroupUpdateMsg.getEntity(), EntityGroup.class, true);
                Assert.assertNotNull(entityGroup);
                if (msgType.equals(entityGroupUpdateMsg.getMsgType())
                        && name.equals(entityGroup.getName())
                        && type.equals(entityGroup.getType())
                        && ownerType.equals(entityGroup.getOwnerId().getEntityType())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private EntityGroupId findEntityGroupId(List<AbstractMessage> messages, UpdateMsgType msgType, String name, EntityType type, EntityType ownerType) {
        for (AbstractMessage message : messages) {
            if (message instanceof EntityGroupUpdateMsg entityGroupUpdateMsg) {
                EntityGroup entityGroup = JacksonUtil.fromString(entityGroupUpdateMsg.getEntity(), EntityGroup.class, true);
                Assert.assertNotNull(entityGroup);
                if (msgType.equals(entityGroupUpdateMsg.getMsgType())
                        && name.equals(entityGroup.getName())
                        && type.equals(entityGroup.getType())
                        && ownerType.equals(entityGroup.getOwnerId().getEntityType())) {
                    return entityGroup.getId();
                }
            }
        }
        return null;
    }

    private boolean popGroupPermissionMsg(List<AbstractMessage> messages, UpdateMsgType msgType, EntityGroupId userGroupId, RoleId roleId) {
        for (AbstractMessage message : messages) {
            if (message instanceof GroupPermissionProto groupPermissionProto) {
                GroupPermission groupPermission = JacksonUtil.fromString(groupPermissionProto.getEntity(), GroupPermission.class, true);
                Assert.assertNotNull(groupPermission);
                if (msgType.equals(groupPermissionProto.getMsgType())
                        && userGroupId.equals(groupPermission.getUserGroupId())
                        && roleId.equals(groupPermission.getRoleId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popEdgeConfigurationMsg(List<AbstractMessage> messages, String name) {
        for (AbstractMessage message : messages) {
            if (message instanceof EdgeConfiguration edgeConfiguration) {
                if (name.equals(edgeConfiguration.getName())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popTenantProfileMsg(List<AbstractMessage> messages, TenantProfileId tenantProfileId) {
        for (AbstractMessage message : messages) {
            if (message instanceof TenantProfileUpdateMsg tenantProfileUpdateMsg) {
                TenantProfile tenantProfile = JacksonUtil.fromString(tenantProfileUpdateMsg.getEntity(), TenantProfile.class, true);
                Assert.assertNotNull(tenantProfile);
                if (UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE.equals(tenantProfileUpdateMsg.getMsgType())
                        && tenantProfileId.equals(tenantProfile.getId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popTenantMsg(List<AbstractMessage> messages, TenantId tenantId1) {
        for (AbstractMessage message : messages) {
            if (message instanceof TenantUpdateMsg tenantUpdateMsg) {
                Tenant tenant = JacksonUtil.fromString(tenantUpdateMsg.getEntity(), Tenant.class, true);
                Assert.assertNotNull(tenant);
                if (UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE.equals(tenantUpdateMsg.getMsgType())
                        && tenantId1.equals(tenant.getId())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popWhiteLabeling(List<AbstractMessage> messages, WhiteLabelingType type) {
        for (AbstractMessage message : messages) {
            if (message instanceof WhiteLabelingProto whiteLabelingProto) {
                WhiteLabeling whiteLabeling = JacksonUtil.fromString(whiteLabelingProto.getEntity(), WhiteLabeling.class, true);
                Assert.assertNotNull(whiteLabeling);
                if (type.equals(whiteLabeling.getType())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean popCustomTranslation(List<AbstractMessage> messages, String locale) {
        for (AbstractMessage message : messages) {
            if (message instanceof CustomTranslationUpdateMsg customTranslationUpdateMsg) {
                CustomTranslation customTranslation = JacksonUtil.fromString(customTranslationUpdateMsg.getEntity(), CustomTranslation.class, true);
                Assert.assertNotNull(customTranslation);
                if (locale.equals(customTranslation.getLocaleCode())) {
                    messages.remove(message);
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    public void testSyncEdge_tenantLevel() throws Exception {
        createAdminSettings();
        createSysAdminAndTenantDomains();
        resetSysAdminWhiteLabelingSettings();
        loginTenantAdmin();
        createPublicCustomerOnTenantLevel();

        EntityGroup savedDeviceGroup = new EntityGroup();
        savedDeviceGroup.setType(EntityType.DEVICE);
        savedDeviceGroup.setName("DeviceGroup");
        savedDeviceGroup = doPost("/api/entityGroup", savedDeviceGroup, EntityGroup.class);

        Device device = new Device();
        device.setName("Sync Test EG Edge Device 1");
        device.setType("default");
        Device savedDevice = doPost("/api/device?entityGroupId={entityGroupId}", device, Device.class, savedDeviceGroup.getId().getId().toString());

        EntityGroup savedAssetGroup = new EntityGroup();
        savedAssetGroup.setType(EntityType.ASSET);
        savedAssetGroup.setName("AssetGroup");
        savedAssetGroup = doPost("/api/entityGroup", savedAssetGroup, EntityGroup.class);

        Asset asset = new Asset();
        asset.setName("Sync Test EG Edge Asset 1");
        asset.setType("test");
        Asset savedAsset = doPost("/api/asset?entityGroupId={entityGroupId}", asset, Asset.class, savedAssetGroup.getId().getId().toString());

        Edge edge = doPost("/api/edge", constructEdge("Sync Test EG Edge", "test"), Edge.class);

        verifyEdgeUserGroups(edge, 2);

        simulateEdgeActivation(edge);

        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + savedDeviceGroup.getId().getId().toString() + "/DEVICE", EntityGroup.class);

        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + savedAssetGroup.getId().getId().toString() + "/ASSET", EntityGroup.class);

        EdgeImitator edgeImitator = new EdgeImitator(EDGE_HOST, EDGE_PORT, edge.getRoutingKey(), edge.getSecret());

        // 30 connect message
        // + 13 fetchers messages in sync process
        // + 2 queue messages (DeviceGroup, AssetGroup)
        edgeImitator.expectMessageAmount(CONNECT_MESSAGE_COUNT + 15);
        edgeImitator.connect();
        edgeImitator.waitForMessages();

        verifyFetchersMsgs_tenantLevel(edgeImitator, savedDevice);
        // verify queue msgs
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "DeviceGroup", EntityType.DEVICE, EntityType.TENANT));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "AssetGroup", EntityType.ASSET, EntityType.TENANT));
        Assert.assertTrue("There are some messages: " + edgeImitator.getDownlinkMsgs(), edgeImitator.getDownlinkMsgs().isEmpty());

        // 30 connect messages
        // + 13 fetchers messages in sync process
        edgeImitator.expectMessageAmount(CONNECT_MESSAGE_COUNT + 13);
        doPost("/api/edge/sync/" + edge.getId());
        edgeImitator.waitForMessages();

        verifyFetchersMsgs_tenantLevel(edgeImitator, savedDevice);
        Assert.assertTrue(edgeImitator.getDownlinkMsgs().isEmpty());

        edgeImitator.allowIgnoredTypes();
        try {
            edgeImitator.disconnect();
        } catch (Exception ignored) {
        }

        doDelete("/api/device/" + savedDevice.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/asset/" + savedAsset.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/edge/" + edge.getId().getId().toString())
                .andExpect(status().isOk());

        cleanupDomains();
    }

    private void createAdminSettings() throws Exception {
        loginSysAdmin();
        // get jwt settings from yaml config
        JwtSettings settings = doGet("/api/admin/jwtSettings", JwtSettings.class);
        // save jwt settings into db
        doPost("/api/admin/jwtSettings", settings).andExpect(status().isOk());

        CustomMenu sysMenu = new CustomMenu();

        CustomMenuItem sysItem = new CustomMenuItem();
        sysItem.setName("System Menu");
        sysMenu.setConfig(new CustomMenuConfig(new ArrayList<>(List.of(sysItem))));

        doPost("/api/customMenu/customMenu", sysMenu);

        // create sysadmin custom translation
        String localeCode = "en_US";
        createCustomTranslation(localeCode);

        // create tenant custom translation
        loginTenantAdmin();
        localeCode = "es_ES";
        createCustomTranslation(localeCode);
    }

    private void createSysAdminAndTenantDomains() throws Exception {
        loginSysAdmin();
        sysAdminDomain = doPost("/api/domain", constructDomain(TenantId.SYS_TENANT_ID, new CustomerId(CustomerId.NULL_UUID), SYSADMIN_EDGE_DOMAIN), Domain.class);

        loginTenantAdmin();
        tenantDomain = doPost("/api/domain", constructDomain(tenantId, new CustomerId(CustomerId.NULL_UUID), TENANT_EDGE_DOMAIN), Domain.class);
    }

    private void createCustomerDomain(Customer customer) throws Exception {
        User customerAUser = new User();
        customerAUser.setAuthority(Authority.CUSTOMER_USER);
        customerAUser.setTenantId(tenantId);
        customerAUser.setCustomerId(customer.getId());
        customerAUser.setEmail("edgetestcustomeradmin@thingsboard.org");
        EntityGroupInfo customerAdminsGroup = findCustomerAdminsGroup(customerId);
        createUser(customerAUser, "customer", customerAdminsGroup.getId()).getId();

        login("edgetestcustomeradmin@thingsboard.org", "customer");

        doPost("/api/domain", constructDomain(tenantId, customer.getId(), CUSTOMER_EDGE_DOMAIN), Domain.class);

        loginTenantAdmin();
    }

    private void cleanupDomains() throws Exception {
        loginSysAdmin();
        doDelete("/api/domain/" + sysAdminDomain.getId().getId());

        loginTenantAdmin();
        doDelete("/api/domain/" + tenantDomain.getId().getId());
    }

    private Domain constructDomain(TenantId tenantId, CustomerId customerId, String name) {
        Domain domain = new Domain();
        domain.setTenantId(tenantId);
        domain.setCustomerId(customerId);
        domain.setName(name);
        domain.setOauth2Enabled(true);
        domain.setPropagateToEdge(true);
        return domain;
    }

    @Test
    public void testSyncEdge_customerLevel() throws Exception {
        createAdminSettings();
        createSysAdminAndTenantDomains();
        resetSysAdminWhiteLabelingSettings();
        loginTenantAdmin();
        createPublicCustomerOnTenantLevel();

        // create customer
        Customer customer = new Customer();
        customer.setTitle("Edge Customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);

        createPublicCustomerOnCustomerLevel(savedCustomer);

        createCustomerDomain(savedCustomer);

        EntityGroup savedCustomerDeviceGroup = new EntityGroup();
        savedCustomerDeviceGroup.setType(EntityType.DEVICE);
        savedCustomerDeviceGroup.setName("CustomerDeviceGroup");
        savedCustomerDeviceGroup.setOwnerId(savedCustomer.getId());
        savedCustomerDeviceGroup = doPost("/api/entityGroup", savedCustomerDeviceGroup, EntityGroup.class);

        Device customerDevice = new Device();
        customerDevice.setName("Sync Test EG Edge Customer Device 1");
        customerDevice.setType("default");
        customerDevice.setOwnerId(savedCustomer.getId());
        Device savedDevice = doPost("/api/device?entityGroupId={entityGroupId}", customerDevice, Device.class, savedCustomerDeviceGroup.getId().getId().toString());

        EntityGroup savedCustomerAssetGroup = new EntityGroup();
        savedCustomerAssetGroup.setType(EntityType.ASSET);
        savedCustomerAssetGroup.setName("CustomerAssetGroup");
        savedCustomerAssetGroup.setOwnerId(savedCustomer.getId());
        savedCustomerAssetGroup = doPost("/api/entityGroup", savedCustomerAssetGroup, EntityGroup.class);

        Asset customerAsset = new Asset();
        customerAsset.setName("Sync Test EG Edge Customer Asset 1");
        customerAsset.setType("test");
        customerAsset.setOwnerId(savedCustomer.getId());
        Asset savedAsset = doPost("/api/asset?entityGroupId={entityGroupId}", customerAsset, Asset.class, savedCustomerAssetGroup.getId().getId().toString());

        Edge edge = doPost("/api/edge", constructEdge("Sync Test EG Edge", "test"), Edge.class);

        verifyEdgeUserGroups(edge, 2);

        simulateEdgeActivation(edge);

        doPost("/api/owner/CUSTOMER/" + savedCustomer.getId().getId() + "/EDGE/" + edge.getId().getId());

        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + savedCustomerDeviceGroup.getId().getId().toString() + "/DEVICE", EntityGroup.class);

        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + savedCustomerAssetGroup.getId().getId().toString() + "/ASSET", EntityGroup.class);

        verifyEdgeUserGroups(edge, 4);

        EdgeImitator edgeImitator = new EdgeImitator(EDGE_HOST, EDGE_PORT, edge.getRoutingKey(), edge.getSecret());

        // 30 connect message
        // + 23 fetchers messages in sync process
        // + 6 queue messages
        edgeImitator.expectMessageAmount(CONNECT_MESSAGE_COUNT + 29);
        edgeImitator.connect();
        edgeImitator.waitForMessages();

        verifyFetchersMsgs_customerLevel(edgeImitator, savedCustomer.getId(), savedDevice);
        // verify queue msgs
        Assert.assertTrue(popCustomerMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Edge Customer", "TENANT", tenantId.getId()));
        Assert.assertTrue(popEdgeConfigurationMsg(edgeImitator.getDownlinkMsgs(), edge.getName()));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Users", EntityType.USER, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Administrators", EntityType.USER, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "CustomerDeviceGroup", EntityType.DEVICE, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "CustomerAssetGroup", EntityType.ASSET, EntityType.CUSTOMER));
        Assert.assertTrue("There are some messages: " + edgeImitator.getDownlinkMsgs(), edgeImitator.getDownlinkMsgs().isEmpty());

        // 30 connect messages
        // + 23 fetchers messages in sync process
        edgeImitator.expectMessageAmount(CONNECT_MESSAGE_COUNT + 23);
        doPost("/api/edge/sync/" + edge.getId());
        edgeImitator.waitForMessages();

        verifyFetchersMsgs_customerLevel(edgeImitator, savedCustomer.getId(), savedDevice);
        Assert.assertTrue("There are some messages: " + edgeImitator.getDownlinkMsgs(), edgeImitator.getDownlinkMsgs().isEmpty());

        edgeImitator.allowIgnoredTypes();
        try {
            edgeImitator.disconnect();
        } catch (Exception ignored) {
        }

        doDelete("/api/device/" + savedDevice.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/asset/" + savedAsset.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/edge/" + edge.getId().getId().toString())
                .andExpect(status().isOk());
        doDelete("/api/customer/" + savedCustomer.getId().getId().toString())
                .andExpect(status().isOk());

        cleanupDomains();
    }

    private void verifyEdgeUserGroups(Edge edge, int expectedGroupNumber) {
        Awaitility.await()
                .atMost(TIMEOUT, TimeUnit.SECONDS)
                .until(() -> {
                    PageData<EntityGroupInfo> pageData = doGetTypedWithPageLink("/api/entityGroups/edge/" + edge.getId().getId() + "/USER?",
                            new TypeReference<>() {}, new PageLink(1024));
                    if (pageData.getData().isEmpty()) {
                        return false;
                    }
                    return pageData.getTotalElements() == expectedGroupNumber;
                });
    }

    private void verifyFetchersMsgs_tenantLevel(EdgeImitator edgeImitator, Device savedDevice) {
        verifyFetchersMsgs_bothLevels(edgeImitator);
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "DeviceGroup", EntityType.DEVICE, EntityType.TENANT));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "AssetGroup", EntityType.ASSET, EntityType.TENANT));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "test"));
        Assert.assertTrue(popAssetMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Sync Test EG Edge Asset 1"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Sync Test EG Edge Device 1"));
        Assert.assertTrue(popDeviceCredentialsMsg(edgeImitator.getDownlinkMsgs(), savedDevice.getId()));
    }

    private void verifyFetchersMsgs_customerLevel(EdgeImitator edgeImitator, CustomerId edgeCustomerId, Device savedDevice) {
        RoleId customerUserRoleId = findRoleId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer User", RoleType.GENERIC);
        RoleId customerAdministratorRoleId = findRoleId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Administrator", RoleType.GENERIC);
        EntityGroupId customerUsersGroupId = findEntityGroupId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Users", EntityType.USER, EntityType.CUSTOMER);
        EntityGroupId customerAdministratorsGroupId = findEntityGroupId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Administrators", EntityType.USER, EntityType.CUSTOMER);

        verifyFetchersMsgs_bothLevels(edgeImitator);
        Assert.assertTrue(popDomainMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, CUSTOMER_EDGE_DOMAIN, tenantId, edgeCustomerId));
        Assert.assertTrue(popCustomerMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Edge Customer", "TENANT", tenantId.getId()));
        Assert.assertTrue(popCustomerMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "[Edge Customer] " + PUBLIC_CUSTOMER_SUFFIX, "CUSTOMER", edgeCustomerId.getId()));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Entity Group Public User", RoleType.GROUP));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Public User", RoleType.GENERIC));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Public Users", EntityType.USER, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "CustomerDeviceGroup", EntityType.DEVICE, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "CustomerAssetGroup", EntityType.ASSET, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Users", EntityType.USER, EntityType.CUSTOMER));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Administrators", EntityType.USER, EntityType.CUSTOMER));
        Assert.assertTrue(popGroupPermissionMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerUsersGroupId, customerUserRoleId));
        Assert.assertTrue(popGroupPermissionMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerAdministratorsGroupId, customerAdministratorRoleId));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "test"));
        Assert.assertTrue(popAssetMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Sync Test EG Edge Customer Asset 1"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Sync Test EG Edge Customer Device 1"));
        Assert.assertTrue(popDeviceCredentialsMsg(edgeImitator.getDownlinkMsgs(), savedDevice.getId()));
    }

    private void verifyFetchersMsgs_bothLevels(EdgeImitator edgeImitator) {
        RoleId tenantUserRoleId = findRoleId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant User", RoleType.GENERIC);
        RoleId tenantAdministratorRoleId = findRoleId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant Administrator", RoleType.GENERIC);
        EntityGroupId tenantUsersGroupId = findEntityGroupId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant Users", EntityType.USER, EntityType.TENANT);
        EntityGroupId tenantAdministratorsGroupId = findEntityGroupId(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant Administrators", EntityType.USER, EntityType.TENANT);

        Assert.assertTrue(popTenantMsg(edgeImitator.getDownlinkMsgs(), tenantId));
        Assert.assertTrue(popTenantProfileMsg(edgeImitator.getDownlinkMsgs(), tenantProfileId));
        Assert.assertTrue(popQueueMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Main"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "general"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "mail"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "connectivity"));
        Assert.assertTrue(popAdminSettingsMsg(edgeImitator.getDownlinkMsgs(), "jwt"));
        Assert.assertTrue(popRuleChainMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Edge Root Rule Chain"));
        Assert.assertTrue(popRuleChainMetadataMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, getEdgeRootRuleChainId(edgeImitator)));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant User", RoleType.GENERIC));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant Administrator", RoleType.GENERIC));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer User", RoleType.GENERIC));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Customer Administrator", RoleType.GENERIC));
        Assert.assertTrue(popDomainMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, SYSADMIN_EDGE_DOMAIN, TenantId.SYS_TENANT_ID, new CustomerId(CustomerId.NULL_UUID)));
        Assert.assertTrue(popDomainMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, TENANT_EDGE_DOMAIN, tenantId, new CustomerId(CustomerId.NULL_UUID)));
        Assert.assertTrue(popWhiteLabeling(edgeImitator.getDownlinkMsgs(), WhiteLabelingType.GENERAL));
        Assert.assertTrue(popWhiteLabeling(edgeImitator.getDownlinkMsgs(), WhiteLabelingType.LOGIN));
        Assert.assertTrue(popCustomerMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Public", "TENANT", tenantId.getId()));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Entity Group Public User", RoleType.GROUP));
        Assert.assertTrue(popRoleMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Public User", RoleType.GENERIC));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Public Users", EntityType.USER, EntityType.CUSTOMER));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popDeviceProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "default"));
        Assert.assertTrue(popAssetProfileMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "test"));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant Users", EntityType.USER, EntityType.TENANT));
        Assert.assertTrue(popEntityGroupMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, "Tenant Administrators", EntityType.USER, EntityType.TENANT));
        Assert.assertTrue(popGroupPermissionMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, tenantUsersGroupId, tenantUserRoleId));
        Assert.assertTrue(popGroupPermissionMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, tenantAdministratorsGroupId, tenantAdministratorRoleId));
        Assert.assertTrue(popUserCredentialsMsg(edgeImitator.getDownlinkMsgs(), currentUserId));
        Assert.assertTrue(popUserMsg(edgeImitator.getDownlinkMsgs(), UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, TENANT_ADMIN_EMAIL, Authority.TENANT_ADMIN));
        Assert.assertTrue(popCustomTranslation(edgeImitator.getDownlinkMsgs(), "en_US")); // sysadmin custom translation
        Assert.assertTrue(popCustomTranslation(edgeImitator.getDownlinkMsgs(), "es_ES")); // tenant custom translation
        Assert.assertTrue(popSyncCompletedMsg(edgeImitator.getDownlinkMsgs()));
    }

    @Test
    public void testDeleteEdgeWithDeleteRelationsOk() throws Exception {
        EdgeId edgeId = savedEdge("Edge for Test WithRelationsOk").getId();
        testEntityDaoWithRelationsOk(tenantId, edgeId, "/api/edge/" + edgeId);
    }

    @Ignore
    @Test
    public void testDeleteEdgeExceptionWithRelationsTransactional() throws Exception {
        EdgeId edgeId = savedEdge("Edge for Test WithRelations Transactional Exception").getId();
        testEntityDaoWithRelationsTransactionalException(edgeDao, tenantId, edgeId, "/api/edge/" + edgeId);
    }

    private Edge savedEdge(String name) {
        Edge edge = constructEdge(name, "default");
        return doPost("/api/edge", edge, Edge.class);
    }

    private List<RuleChain> getEdgeRuleChains(EdgeId edgeId) throws Exception {
        return doGetTypedWithTimePageLink("/api/edge/" + edgeId + "/ruleChains?",
                new TypeReference<PageData<RuleChain>>() {
                }, new TimePageLink(10)).getData();
    }

    @Test
    public void testGetEdgeInstallInstructions() throws Exception {
        Edge edge = constructEdge(tenantId, "Edge for Test Docker Install Instructions", "default", "7390c3a6-69b0-9910-d155-b90aca4b772e", "l7q4zsjplzwhk16geqxy");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);
        String installInstructions = doGet("/api/edge/instructions/install/" + savedEdge.getId().getId().toString() + "/docker", String.class);
        Assert.assertTrue(installInstructions.contains("l7q4zsjplzwhk16geqxy"));
        Assert.assertTrue(installInstructions.contains("7390c3a6-69b0-9910-d155-b90aca4b772e"));
    }

    @Test
    public void testGetEdgeUpgradeInstructions() throws Exception {
        // UpdateInfo config is updating from Thingsboard Update server
        HashMap<String, EdgeUpgradeInfo> upgradeInfoHashMap = new HashMap<>();
        upgradeInfoHashMap.put("3.6.0", new EdgeUpgradeInfo(true, "3.6.1"));
        upgradeInfoHashMap.put("3.6.1", new EdgeUpgradeInfo(true, "3.6.2"));
        upgradeInfoHashMap.put("3.6.2", new EdgeUpgradeInfo(true, null));
        edgeUpgradeInstructionsService.updateInstructionMap(upgradeInfoHashMap);
        Edge edge = constructEdge("Edge for Test Docker Upgrade Instructions", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);
        String body = "{\"edgeVersion\": \"V_3_6_0\"}";
        doPostAsync("/api/plugins/telemetry/EDGE/" + savedEdge.getId().getId() + "/attributes/SERVER_SCOPE", body, String.class, status().isOk());
        String upgradeInstructions = doGet("/api/edge/instructions/upgrade/" + EdgeVersion.V_3_6_0.name() + "/docker", String.class);
        Assert.assertTrue(upgradeInstructions.contains("Upgrading to 3.6.1EDGE"));
        Assert.assertTrue(upgradeInstructions.contains("Upgrading to 3.6.2EDGE"));
    }

    @Test
    public void testIsEdgeUpgradeAvailable() throws Exception {
        Edge edge = constructEdge("Edge Upgrade Available", "default");
        Edge savedEdge = doPost("/api/edge", edge, Edge.class);

        // Test 3.5.0 Edge - upgrade not available
        String body = "{\"edgeVersion\": \"V_3_5_0\"}";
        doPostAsync("/api/plugins/telemetry/EDGE/" + savedEdge.getId().getId() + "/attributes/SERVER_SCOPE", body, String.class, status().isOk());
        edgeUpgradeInstructionsService.setAppVersion("3.6.0PE");
        Assert.assertFalse(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.2PE");
        Assert.assertFalse(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.2.7PE");
        Assert.assertFalse(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));

        // Test 3.6.0 Edge - upgrade available
        body = "{\"edgeVersion\": \"V_3_6_0\"}";
        doPostAsync("/api/plugins/telemetry/EDGE/" + savedEdge.getId().getId() + "/attributes/SERVER_SCOPE", body, String.class, status().isOk());
        edgeUpgradeInstructionsService.setAppVersion("3.6.0PE");
        Assert.assertFalse(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.1.5PE");
        Assert.assertTrue(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.2PE");
        Assert.assertTrue(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.3PE-SNAPSHOT");
        Assert.assertTrue(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));

        // Test 3.6.1 Edge - upgrade available
        body = "{\"edgeVersion\": \"V_3_6_1\"}";
        doPostAsync("/api/plugins/telemetry/EDGE/" + savedEdge.getId().getId() + "/attributes/SERVER_SCOPE", body, String.class, status().isOk());
        edgeUpgradeInstructionsService.setAppVersion("3.6.1PE");
        Assert.assertFalse(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.1PE-SNAPSHOT");
        Assert.assertFalse(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.2PE");
        Assert.assertTrue(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.2.6PE");
        Assert.assertTrue(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
        edgeUpgradeInstructionsService.setAppVersion("3.6.2.6PE-SNAPSHOT");
        Assert.assertTrue(edgeUpgradeInstructionsService.isUpgradeAvailable(savedEdge.getTenantId(), savedEdge.getId()));
    }

    private void createCustomTranslation(String localeCode) throws Exception {
        JsonNode esCustomTranslation = JacksonUtil.toJsonNode("{\"save\":\"" + StringUtils.randomAlphabetic(10) + "\"}");
        doPost("/api/translation/custom/" + localeCode, esCustomTranslation);

        JsonNode savedCT =  doGet("/api/translation/custom/" + localeCode, JsonNode.class);
        assertThat(savedCT).isEqualTo(esCustomTranslation);
    }

    @Test
    public void testSaveEntityGroup_noNotificationOnAdded_notificationOnlyOnUpdated() {
        Mockito.reset(tbClusterService);
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName("Edge - No Notification On Added");
        entityGroup.setType(EntityType.DEVICE);
        EntityGroup savedEntityGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class);
        Mockito.verify(tbClusterService, never()).sendNotificationMsgToEdge(Mockito.eq(tenantId),
                Mockito.isNull(), Mockito.eq(savedEntityGroup.getId()), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq(EdgeEventActionType.ADDED), Mockito.any());

        Mockito.reset(tbClusterService);
        savedEntityGroup.setName("Edge - Notification On Updated");
        doPost("/api/entityGroup", savedEntityGroup, EntityGroup.class);
        Mockito.verify(tbClusterService, times(1)).sendNotificationMsgToEdge(Mockito.eq(tenantId),
                Mockito.isNull(), Mockito.eq(savedEntityGroup.getId()), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq(EdgeEventActionType.UPDATED), Mockito.any());
    }
}
