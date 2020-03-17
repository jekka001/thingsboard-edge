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
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.converter.ConverterType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.dao.exception.DataValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class BaseConverterServiceTest extends AbstractBeforeTest {

    private IdComparator<Converter> idComparator = new IdComparator<>();

    private TenantId tenantId;

    private static final JsonNode CUSTOM_CONVERTER_CONFIGURATION = new ObjectMapper()
            .createObjectNode().put("decoder", "return {deviceName: 'Device A', deviceType: 'thermostat'};");


    @Before
    public void beforeRun() {
        tenantId = before();
    }

    @After
    public void after() {
        tenantService.deleteTenant(tenantId);
    }

    @Test
    public void testSaveConverter() {
        Converter converter = new Converter();
        converter.setTenantId(tenantId);
        converter.setName("My converter");
        converter.setType(ConverterType.UPLINK);
        converter.setConfiguration(CUSTOM_CONVERTER_CONFIGURATION);
        Converter savedConverter = converterService.saveConverter(converter);

        Assert.assertNotNull(savedConverter);
        Assert.assertNotNull(savedConverter.getId());
        Assert.assertTrue(savedConverter.getCreatedTime() > 0);
        Assert.assertEquals(converter.getTenantId(), savedConverter.getTenantId());
        Assert.assertEquals(converter.getName(), savedConverter.getName());

        savedConverter.setName("My new converter");

        converterService.saveConverter(savedConverter);
        Converter foundConverter = converterService.findConverterById(savedConverter.getTenantId(), savedConverter.getId());
        Assert.assertEquals(foundConverter.getName(), savedConverter.getName());

        converterService.deleteConverter(savedConverter.getTenantId(), savedConverter.getId());
    }

    @Test(expected = DataValidationException.class)
    public void testSaveConverterWithEmptyName() {
        Converter converter = new Converter();
        converter.setTenantId(tenantId);
        converter.setType(ConverterType.UPLINK);
        converterService.saveConverter(converter);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveConverterWithEmptyTenant() {
        Converter converter = new Converter();
        converter.setName("My converter");
        converter.setType(ConverterType.UPLINK);
        converterService.saveConverter(converter);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveConverterWithInvalidTenant() {
        Converter converter = new Converter();
        converter.setName("My converter");
        converter.setType(ConverterType.UPLINK);
        converter.setTenantId(new TenantId(UUIDs.timeBased()));
        converterService.saveConverter(converter);
    }

    @Test
    public void testFindConverterById() {
        Converter converter = new Converter();
        converter.setTenantId(tenantId);
        converter.setName("My converter");
        converter.setType(ConverterType.UPLINK);
        converter.setConfiguration(CUSTOM_CONVERTER_CONFIGURATION);
        Converter savedConverter = converterService.saveConverter(converter);
        Converter foundConverter = converterService.findConverterById(savedConverter.getTenantId(), savedConverter.getId());
        Assert.assertNotNull(foundConverter);
        Assert.assertEquals(savedConverter, foundConverter);
        converterService.deleteConverter(savedConverter.getTenantId(), savedConverter.getId());
    }

    @Test
    public void testDeleteConverter() {
        Converter converter = new Converter();
        converter.setTenantId(tenantId);
        converter.setName("My converter");
        converter.setType(ConverterType.UPLINK);
        converter.setConfiguration(CUSTOM_CONVERTER_CONFIGURATION);
        Converter savedConverter = converterService.saveConverter(converter);
        Converter foundConverter = converterService.findConverterById(savedConverter.getTenantId(), savedConverter.getId());
        Assert.assertNotNull(foundConverter);
        converterService.deleteConverter(savedConverter.getTenantId(), savedConverter.getId());
        foundConverter = converterService.findConverterById(savedConverter.getTenantId(), savedConverter.getId());
        Assert.assertNull(foundConverter);
    }

    @Test
    public void testFindTenantConverters() {
        Tenant tenant = new Tenant();
        tenant.setTitle("Test tenant");
        tenant = tenantService.saveTenant(tenant);

        TenantId tenantId = tenant.getId();

        List<Converter> converters = new ArrayList<>();
        for (int i = 0; i < 178; i++) {
            Converter converter = new Converter();
            converter.setTenantId(tenantId);
            converter.setName("Converter" + i);
            converter.setType(ConverterType.UPLINK);
            converter.setConfiguration(CUSTOM_CONVERTER_CONFIGURATION);
            converters.add(converterService.saveConverter(converter));
        }

        List<Converter> loadedConverters = new ArrayList<>();
        TextPageLink pageLink = new TextPageLink(23);
        TextPageData<Converter> pageData;
        do {
            pageData = converterService.findTenantConverters(tenantId, pageLink);
            loadedConverters.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageData.getNextPageLink();
            }
        } while (pageData.hasNext());


        Collections.sort(converters, idComparator);
        Collections.sort(loadedConverters, idComparator);

        Assert.assertEquals(converters, loadedConverters);

        converterService.deleteConvertersByTenantId(tenantId);

        pageLink = new TextPageLink(33);
        pageData = converterService.findTenantConverters(tenantId, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());

        tenantService.deleteTenant(tenantId);
    }

}
