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
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationInfo;
import org.thingsboard.server.common.data.relation.EntityRelationsQuery;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationEntityTypeFilter;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.relation.RelationsSearchParameters;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.relation.RelationService;

import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public abstract class BaseEntityRelationControllerTest extends AbstractControllerTest {

    public static final String BASE_DEVICE_NAME = "Test dummy device";

    @Autowired
    RelationService relationService;

    private IdComparator<EntityView> idComparator;
    private Tenant savedTenant;
    private User tenantAdmin;
    private Device mainDevice;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();
        idComparator = new IdComparator<>();

        Tenant tenant = new Tenant();
        tenant.setTitle("Test tenant");

        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");
        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");

        Device device = new Device();
        device.setName("Main test device");
        device.setType("default");
        mainDevice = doPost("/api/device", device, Device.class);
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveAndFindRelation() throws Exception {
        Device device = buildSimpleDevice("Test device 1");

        EntityRelation relation = createFromRelation(mainDevice, device, "CONTAINS");
        doPost("/api/relation", relation).andExpect(status().isOk());

        String url = String.format("/api/relation?fromId=%s&fromType=%s&relationType=%s&toId=%s&toType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE,
                "CONTAINS", device.getUuidId(), EntityType.DEVICE
        );

        EntityRelation foundRelation = doGet(url, EntityRelation.class);

        Assert.assertNotNull("Relation is not found!", foundRelation);
        Assert.assertEquals("Found relation is not equals origin!", relation, foundRelation);
    }

    @Test
    public void testSaveAndFindRelationsByFrom() throws Exception {
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME);
        String url = String.format("/api/relations?fromId=%s&fromType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );

        assertFoundList(url, numOfDevices);
    }

    @Test
    public void testSaveAndFindRelationsByTo() throws Exception {
        final int numOfDevices = 30;
        createDevicesByTo(numOfDevices, BASE_DEVICE_NAME);
        String url = String.format("/api/relations?toId=%s&toType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );

        assertFoundList(url, numOfDevices);
    }

    @Test
    public void testSaveAndFindRelationsByFromWithRelationType() throws Exception {
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME);

        Device device = buildSimpleDevice("Unique dummy test device ");
        EntityRelation relation = createFromRelation(mainDevice, device, "TEST");

        doPost("/api/relation", relation).andExpect(status().isOk());
        String url = String.format("/api/relations?fromId=%s&fromType=%s&relationType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE, "TEST"
        );

        assertFoundList(url, 1);
    }

    @Test
    public void testSaveAndFindRelationsByToWithRelationType() throws Exception {
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME);

        Device device = buildSimpleDevice("Unique dummy test device ");
        EntityRelation relation = createFromRelation(device, mainDevice, "TEST");

        doPost("/api/relation", relation).andExpect(status().isOk());
        String url = String.format("/api/relations?toId=%s&toType=%s&relationType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE, "TEST"
        );

        assertFoundList(url, 1);
    }

    @Test
    public void testFindRelationsInfoByFrom() throws Exception {
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME);
        String url = String.format("/api/relations/info?fromId=%s&fromType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );

        List<EntityRelationInfo> relationsInfos =
                JacksonUtil.convertValue(doGet(url, JsonNode.class), new TypeReference<List<EntityRelationInfo>>() {
                });

        Assert.assertNotNull("Relations is not found!", relationsInfos);
        Assert.assertEquals("List of found relationsInfos is not equal to number of created relations!",
                numOfDevices, relationsInfos.size());

        assertRelationsInfosByFrom(relationsInfos);
    }

    @Test
    public void testFindRelationsInfoByTo() throws Exception {
        final int numOfDevices = 30;
        createDevicesByTo(numOfDevices, BASE_DEVICE_NAME);
        String url = String.format("/api/relations/info?toId=%s&toType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );

        List<EntityRelationInfo> relationsInfos =
                JacksonUtil.convertValue(doGet(url, JsonNode.class), new TypeReference<List<EntityRelationInfo>>() {
                });

        Assert.assertNotNull("Relations is not found!", relationsInfos);
        Assert.assertEquals("List of found relationsInfos is not equal to number of created relations!",
                numOfDevices, relationsInfos.size());

        assertRelationsInfosByTo(relationsInfos);
    }

    @Test
    public void testDeleteRelation() throws Exception {
        Device device = buildSimpleDevice("Test device 1");

        EntityRelation relation = createFromRelation(mainDevice, device, "CONTAINS");
        doPost("/api/relation", relation).andExpect(status().isOk());

        String url = String.format("/api/relation?fromId=%s&fromType=%s&relationType=%s&toId=%s&toType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE,
                "CONTAINS", device.getUuidId(), EntityType.DEVICE
        );

        EntityRelation foundRelation = doGet(url, EntityRelation.class);

        Assert.assertNotNull("Relation is not found!", foundRelation);
        Assert.assertEquals("Found relation is not equals origin!", relation, foundRelation);

        doDelete(url).andExpect(status().isOk());
        doGet(url).andExpect(status().is4xxClientError());
    }

    @Test
    public void testDeleteRelations() throws Exception {
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME + " from");
        createDevicesByTo(numOfDevices, BASE_DEVICE_NAME + " to");

        String urlTo = String.format("/api/relations?toId=%s&toType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );
        String urlFrom = String.format("/api/relations?fromId=%s&fromType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );

        assertFoundList(urlTo, numOfDevices);
        assertFoundList(urlFrom, numOfDevices);

        String url = String.format("/api/relations?entityId=%s&entityType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE
        );
        doDelete(url).andExpect(status().isOk());

        Assert.assertTrue(
                "Performed deletion of all relations but some relations were found!",
                doGet(urlTo, List.class).isEmpty()
        );
        Assert.assertTrue(
                "Performed deletion of all relations but some relations were found!",
                doGet(urlFrom, List.class).isEmpty()
        );
    }

    @Test
    public void testFindRelationsByFromQuery() throws Exception {
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME);

        EntityRelationsQuery query = new EntityRelationsQuery();
        query.setParameters(new RelationsSearchParameters(
                mainDevice.getUuidId(), EntityType.DEVICE,
                EntitySearchDirection.FROM,
                RelationTypeGroup.COMMON,
                1, true
                ));
        query.setFilters(Collections.singletonList(
                new RelationEntityTypeFilter("CONTAINS", List.of(EntityType.DEVICE))
        ));

        List<EntityRelation> relations = readResponse(
                doPost("/api/relations", query).andExpect(status().isOk()),
                new TypeReference<List<EntityRelation>>() {}
        );

        assertFoundRelations(relations, numOfDevices);
    }

    @Test
    public void testFindRelationsByToQuery() throws Exception {
        final int numOfDevices = 30;
        createDevicesByTo(numOfDevices, BASE_DEVICE_NAME);

        EntityRelationsQuery query = new EntityRelationsQuery();
        query.setParameters(new RelationsSearchParameters(
                mainDevice.getUuidId(), EntityType.DEVICE,
                EntitySearchDirection.TO,
                RelationTypeGroup.COMMON,
                1, true
        ));
        query.setFilters(Collections.singletonList(
                new RelationEntityTypeFilter("CONTAINS", List.of(EntityType.DEVICE))
        ));

        List<EntityRelation> relations = readResponse(
                doPost("/api/relations", query).andExpect(status().isOk()),
                new TypeReference<List<EntityRelation>>() {}
        );

        assertFoundRelations(relations, numOfDevices);
    }

    @Test
    public void testFindRelationsInfoByFromQuery() throws Exception{
        final int numOfDevices = 30;
        createDevicesByFrom(numOfDevices, BASE_DEVICE_NAME);

        EntityRelationsQuery query = new EntityRelationsQuery();
        query.setParameters(new RelationsSearchParameters(
                mainDevice.getUuidId(), EntityType.DEVICE,
                EntitySearchDirection.FROM,
                RelationTypeGroup.COMMON,
                1, true
        ));
        query.setFilters(Collections.singletonList(
                new RelationEntityTypeFilter("CONTAINS", List.of(EntityType.DEVICE))
        ));

        List<EntityRelationInfo> relationsInfo = readResponse(
                doPost("/api/relations/info", query).andExpect(status().isOk()),
                new TypeReference<List<EntityRelationInfo>>() {}
        );

        assertRelationsInfosByFrom(relationsInfo);
    }

    @Test
    public void testFindRelationsInfoByToQuery() throws Exception{
        final int numOfDevices = 30;
        createDevicesByTo(numOfDevices, BASE_DEVICE_NAME);

        EntityRelationsQuery query = new EntityRelationsQuery();
        query.setParameters(new RelationsSearchParameters(
                mainDevice.getUuidId(), EntityType.DEVICE,
                EntitySearchDirection.TO,
                RelationTypeGroup.COMMON,
                1, true
        ));
        query.setFilters(Collections.singletonList(
                new RelationEntityTypeFilter("CONTAINS", List.of(EntityType.DEVICE))
        ));

        List<EntityRelationInfo> relationsInfo = readResponse(
                doPost("/api/relations/info", query).andExpect(status().isOk()),
                new TypeReference<List<EntityRelationInfo>>() {}
        );

        assertRelationsInfosByTo(relationsInfo);
    }

    @Test
    public void testCreateRelationFromTenantToDevice() throws Exception{
        EntityRelation relation = new EntityRelation(tenantAdmin.getTenantId(), mainDevice.getId(), "CONTAINS");
        doPost("/api/relation", relation).andExpect(status().isOk());

        String url = String.format("/api/relation?fromId=%s&fromType=%s&relationType=%s&toId=%s&toType=%s",
                tenantAdmin.getTenantId(), EntityType.TENANT,
                "CONTAINS", mainDevice.getUuidId(), EntityType.DEVICE
        );

        EntityRelation foundRelation = doGet(url, EntityRelation.class);

        Assert.assertNotNull("Relation is not found!", foundRelation);
        Assert.assertEquals("Found relation is not equals origin!", relation, foundRelation);
    }

    @Test
    public void testCreateRelationFromDeviceToTenant() throws Exception{
        EntityRelation relation = new EntityRelation(mainDevice.getId(), tenantAdmin.getTenantId(), "CONTAINS");
        doPost("/api/relation", relation).andExpect(status().isOk());

        String url = String.format("/api/relation?fromId=%s&fromType=%s&relationType=%s&toId=%s&toType=%s",
                mainDevice.getUuidId(), EntityType.DEVICE,
                "CONTAINS", tenantAdmin.getTenantId(), EntityType.TENANT
        );

        EntityRelation foundRelation = doGet(url, EntityRelation.class);

        Assert.assertNotNull("Relation is not found!", foundRelation);
        Assert.assertEquals("Found relation is not equals origin!", relation, foundRelation);
    }

    private Device buildSimpleDevice(String name) throws Exception {
        Device device = new Device();
        device.setName(name);
        device.setType("default");
        device = doPost("/api/device", device, Device.class);
        return device;
    }

    private EntityRelation createFromRelation(Device mainDevice, Device device, String relationType) {
        return new EntityRelation(mainDevice.getId(), device.getId(), relationType);
    }

    private void createDevicesByFrom(int numOfDevices, String baseName) throws Exception {
        for (int i = 0; i < numOfDevices; i++) {
            Device device = buildSimpleDevice(baseName + i);

            EntityRelation relation = createFromRelation(mainDevice, device, "CONTAINS");
            doPost("/api/relation", relation).andExpect(status().isOk());
        }
    }

    private void createDevicesByTo(int numOfDevices, String baseName) throws Exception {
        for (int i = 0; i < numOfDevices; i++) {
            Device device = buildSimpleDevice(baseName + i);
            EntityRelation relation = createFromRelation(device, mainDevice, "CONTAINS");
            doPost("/api/relation", relation).andExpect(status().isOk());
        }
    }

    private void assertFoundRelations(List<EntityRelation> relations, int numOfDevices) {
        Assert.assertNotNull("Relations is not found!", relations);
        Assert.assertEquals("List of found relations is not equal to number of created relations!",
                numOfDevices, relations.size());
    }

    private void assertFoundList(String url, int numOfDevices) throws Exception {
        @SuppressWarnings("unchecked")
        List<EntityRelation> relations = doGet(url, List.class);
        assertFoundRelations(relations, numOfDevices);
    }

    private void assertRelationsInfosByFrom(List<EntityRelationInfo> relationsInfos) {
        for (EntityRelationInfo info : relationsInfos) {
            Assert.assertEquals("Wrong FROM entityId!", mainDevice.getId(), info.getFrom());
            Assert.assertTrue("Wrong FROM name!", info.getToName().contains(BASE_DEVICE_NAME));
            Assert.assertEquals("Wrong relationType!", "CONTAINS", info.getType());
        }
    }

    private void assertRelationsInfosByTo(List<EntityRelationInfo> relationsInfos) {
        for (EntityRelationInfo info : relationsInfos) {
            Assert.assertEquals("Wrong TO entityId!", mainDevice.getId(), info.getTo());
            Assert.assertTrue("Wrong TO name!", info.getFromName().contains(BASE_DEVICE_NAME));
            Assert.assertEquals("Wrong relationType!", "CONTAINS", info.getType());
        }
    }
}
