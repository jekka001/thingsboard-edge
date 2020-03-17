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
package org.thingsboard.server.dao.service;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.model.ModelConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseWidgetTypeServiceTest extends AbstractBeforeTest {

    private IdComparator<WidgetType> idComparator = new IdComparator<>();

    private TenantId tenantId;

    @Before
    public void beforeRun() {
        tenantId = before();
    }

    @After
    public void after() {
        tenantService.deleteTenant(tenantId);
    }

    @Test
    public void testSaveWidgetType() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);


        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);

        Assert.assertNotNull(savedWidgetType);
        Assert.assertNotNull(savedWidgetType.getId());
        Assert.assertNotNull(savedWidgetType.getAlias());
        Assert.assertTrue(savedWidgetType.getCreatedTime() > 0);
        Assert.assertEquals(widgetType.getTenantId(), savedWidgetType.getTenantId());
        Assert.assertEquals(widgetType.getName(), savedWidgetType.getName());
        Assert.assertEquals(widgetType.getDescriptor(), savedWidgetType.getDescriptor());
        Assert.assertEquals(savedWidgetsBundle.getAlias(), savedWidgetType.getBundleAlias());

        savedWidgetType.setName("New Widget Type");

        widgetTypeService.saveWidgetType(savedWidgetType);
        WidgetType foundWidgetType = widgetTypeService.findWidgetTypeById(tenantId, savedWidgetType.getId());
        Assert.assertEquals(foundWidgetType.getName(), savedWidgetType.getName());

        widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
    }

    @Test(expected = DataValidationException.class)
    public void testSaveWidgetTypeWithEmptyName() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        try {
            widgetTypeService.saveWidgetType(widgetType);
        } finally {
            widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
        }
    }

    @Test(expected = DataValidationException.class)
    public void testSaveWidgetTypeWithEmptyBundleAlias() throws IOException {
        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        widgetTypeService.saveWidgetType(widgetType);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveWidgetTypeWithEmptyDescriptor() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setName("Widget Type");
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setDescriptor(new ObjectMapper().readValue("{}", JsonNode.class));
        try {
            widgetTypeService.saveWidgetType(widgetType);
        } finally {
            widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
        }
    }

    @Test(expected = DataValidationException.class)
    public void testSaveWidgetTypeWithInvalidTenant() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(new TenantId(UUIDs.timeBased()));
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        try {
            widgetTypeService.saveWidgetType(widgetType);
        } finally {
            widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
        }
    }

    @Test(expected = DataValidationException.class)
    public void testSaveWidgetTypeWithInvalidBundleAlias() throws IOException {
        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias("some_alias");
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        widgetTypeService.saveWidgetType(widgetType);
    }

    @Test(expected = DataValidationException.class)
    public void testUpdateWidgetTypeTenant() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);
        savedWidgetType.setTenantId(new TenantId(ModelConstants.NULL_UUID));
        try {
            widgetTypeService.saveWidgetType(savedWidgetType);
        } finally {
            widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
        }
    }

    @Test(expected = DataValidationException.class)
    public void testUpdateWidgetTypeBundleAlias() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);
        savedWidgetType.setBundleAlias("some_alias");
        try {
            widgetTypeService.saveWidgetType(savedWidgetType);
        } finally {
            widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
        }
    }

    @Test(expected = DataValidationException.class)
    public void testUpdateWidgetTypeAlias() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);
        savedWidgetType.setAlias("some_alias");
        try {
            widgetTypeService.saveWidgetType(savedWidgetType);
        } finally {
            widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
        }
    }

    @Test
    public void testFindWidgetTypeById() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);
        WidgetType foundWidgetType = widgetTypeService.findWidgetTypeById(tenantId, savedWidgetType.getId());
        Assert.assertNotNull(foundWidgetType);
        Assert.assertEquals(savedWidgetType, foundWidgetType);

        widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
    }

    @Test
    public void testFindWidgetTypeByTenantIdBundleAliasAndAlias() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);
        WidgetType foundWidgetType = widgetTypeService.findWidgetTypeByTenantIdBundleAliasAndAlias(tenantId, savedWidgetsBundle.getAlias(), savedWidgetType.getAlias());
        Assert.assertNotNull(foundWidgetType);
        Assert.assertEquals(savedWidgetType, foundWidgetType);

        widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
    }

    @Test
    public void testDeleteWidgetType() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        WidgetType widgetType = new WidgetType();
        widgetType.setTenantId(tenantId);
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        widgetType.setName("Widget Type");
        widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
        WidgetType savedWidgetType = widgetTypeService.saveWidgetType(widgetType);
        WidgetType foundWidgetType = widgetTypeService.findWidgetTypeById(tenantId, savedWidgetType.getId());
        Assert.assertNotNull(foundWidgetType);
        widgetTypeService.deleteWidgetType(tenantId, savedWidgetType.getId());
        foundWidgetType = widgetTypeService.findWidgetTypeById(tenantId, savedWidgetType.getId());
        Assert.assertNull(foundWidgetType);

        widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
    }

    @Test
    public void testFindWidgetTypesByTenantIdAndBundleAlias() throws IOException {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTenantId(tenantId);
        widgetsBundle.setTitle("Widgets bundle");
        WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);

        List<WidgetType> widgetTypes = new ArrayList<>();
        for (int i=0;i<121;i++) {
            WidgetType widgetType = new WidgetType();
            widgetType.setTenantId(tenantId);
            widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
            widgetType.setName("Widget Type " + i);
            widgetType.setDescriptor(new ObjectMapper().readValue("{ \"someKey\": \"someValue\" }", JsonNode.class));
            widgetTypes.add(widgetTypeService.saveWidgetType(widgetType));
        }

        List<WidgetType> loadedWidgetTypes = widgetTypeService.findWidgetTypesByTenantIdAndBundleAlias(tenantId, savedWidgetsBundle.getAlias());

        Collections.sort(widgetTypes, idComparator);
        Collections.sort(loadedWidgetTypes, idComparator);

        Assert.assertEquals(widgetTypes, loadedWidgetTypes);

        widgetTypeService.deleteWidgetTypesByTenantIdAndBundleAlias(tenantId, savedWidgetsBundle.getAlias());

        loadedWidgetTypes = widgetTypeService.findWidgetTypesByTenantIdAndBundleAlias(tenantId, savedWidgetsBundle.getAlias());

        Assert.assertTrue(loadedWidgetTypes.isEmpty());

        widgetsBundleService.deleteWidgetsBundle(tenantId, savedWidgetsBundle.getId());
    }

}
