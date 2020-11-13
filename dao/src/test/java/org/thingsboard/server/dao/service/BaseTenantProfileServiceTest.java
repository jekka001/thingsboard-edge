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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.server.common.data.EntityInfo;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.TenantProfileData;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.util.mapping.JacksonUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BaseTenantProfileServiceTest extends AbstractServiceTest {

    private IdComparator<TenantProfile> idComparator = new IdComparator<>();
    private IdComparator<EntityInfo> tenantProfileInfoIdComparator = new IdComparator<>();

    @After
    public void after() {
        tenantProfileService.deleteTenantProfiles(TenantId.SYS_TENANT_ID);
    }

    @Test
    public void testSaveTenantProfile() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        Assert.assertNotNull(savedTenantProfile);
        Assert.assertNotNull(savedTenantProfile.getId());
        Assert.assertTrue(savedTenantProfile.getCreatedTime() > 0);
        Assert.assertEquals(tenantProfile.getName(), savedTenantProfile.getName());
        Assert.assertEquals(tenantProfile.getDescription(), savedTenantProfile.getDescription());
        Assert.assertEquals(tenantProfile.getProfileData(), savedTenantProfile.getProfileData());
        Assert.assertEquals(tenantProfile.isDefault(), savedTenantProfile.isDefault());
        Assert.assertEquals(tenantProfile.isIsolatedTbCore(), savedTenantProfile.isIsolatedTbCore());
        Assert.assertEquals(tenantProfile.isIsolatedTbRuleEngine(), savedTenantProfile.isIsolatedTbRuleEngine());

        savedTenantProfile.setName("New tenant profile");
        tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile);
        TenantProfile foundTenantProfile = tenantProfileService.findTenantProfileById(TenantId.SYS_TENANT_ID, savedTenantProfile.getId());
        Assert.assertEquals(foundTenantProfile.getName(), savedTenantProfile.getName());
    }

    @Test
    public void testFindTenantProfileById() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        TenantProfile foundTenantProfile = tenantProfileService.findTenantProfileById(TenantId.SYS_TENANT_ID, savedTenantProfile.getId());
        Assert.assertNotNull(foundTenantProfile);
        Assert.assertEquals(savedTenantProfile, foundTenantProfile);
    }

    @Test
    public void testFindTenantProfileInfoById() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        EntityInfo foundTenantProfileInfo = tenantProfileService.findTenantProfileInfoById(TenantId.SYS_TENANT_ID, savedTenantProfile.getId());
        Assert.assertNotNull(foundTenantProfileInfo);
        Assert.assertEquals(savedTenantProfile.getId(), foundTenantProfileInfo.getId());
        Assert.assertEquals(savedTenantProfile.getName(), foundTenantProfileInfo.getName());
    }

    @Test
    public void testFindDefaultTenantProfile() {
        TenantProfile tenantProfile = this.createTenantProfile("Default Tenant Profile");
        tenantProfile.setDefault(true);
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        TenantProfile foundDefaultTenantProfile = tenantProfileService.findDefaultTenantProfile(TenantId.SYS_TENANT_ID);
        Assert.assertNotNull(foundDefaultTenantProfile);
        Assert.assertEquals(savedTenantProfile, foundDefaultTenantProfile);
    }

    @Test
    public void testFindDefaultTenantProfileInfo() {
        TenantProfile tenantProfile = this.createTenantProfile("Default Tenant Profile");
        tenantProfile.setDefault(true);
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        EntityInfo foundDefaultTenantProfileInfo = tenantProfileService.findDefaultTenantProfileInfo(TenantId.SYS_TENANT_ID);
        Assert.assertNotNull(foundDefaultTenantProfileInfo);
        Assert.assertEquals(savedTenantProfile.getId(), foundDefaultTenantProfileInfo.getId());
        Assert.assertEquals(savedTenantProfile.getName(), foundDefaultTenantProfileInfo.getName());
    }

    @Test
    public void testSetDefaultTenantProfile() {
        TenantProfile tenantProfile1 = this.createTenantProfile("Tenant Profile 1");
        TenantProfile tenantProfile2 = this.createTenantProfile("Tenant Profile 2");

        TenantProfile savedTenantProfile1 = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile1);
        TenantProfile savedTenantProfile2 = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile2);

        boolean result = tenantProfileService.setDefaultTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile1.getId());
        Assert.assertTrue(result);
        TenantProfile defaultTenantProfile = tenantProfileService.findDefaultTenantProfile(TenantId.SYS_TENANT_ID);
        Assert.assertNotNull(defaultTenantProfile);
        Assert.assertEquals(savedTenantProfile1.getId(), defaultTenantProfile.getId());
        result = tenantProfileService.setDefaultTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile2.getId());
        Assert.assertTrue(result);
        defaultTenantProfile = tenantProfileService.findDefaultTenantProfile(TenantId.SYS_TENANT_ID);
        Assert.assertNotNull(defaultTenantProfile);
        Assert.assertEquals(savedTenantProfile2.getId(), defaultTenantProfile.getId());
    }

    @Test(expected = DataValidationException.class)
    public void testSaveTenantProfileWithEmptyName() {
        TenantProfile tenantProfile = new TenantProfile();
        tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveTenantProfileWithSameName() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        TenantProfile tenantProfile2 = this.createTenantProfile("Tenant Profile");
        tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile2);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveSameTenantProfileWithDifferentIsolatedTbRuleEngine() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        savedTenantProfile.setIsolatedTbRuleEngine(true);
        tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile);
    }

    @Test(expected = DataValidationException.class)
    public void testSaveSameTenantProfileWithDifferentIsolatedTbCore() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        savedTenantProfile.setIsolatedTbCore(true);
        tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile);
    }

    @Test(expected = DataValidationException.class)
    public void testDeleteTenantProfileWithExistingTenant() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        Tenant tenant = new Tenant();
        tenant.setTitle("Test tenant");
        tenant.setTenantProfileId(savedTenantProfile.getId());
        tenant = tenantService.saveTenant(tenant);
        try {
            tenantProfileService.deleteTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile.getId());
        } finally {
            tenantService.deleteTenant(tenant.getId());
        }
    }

    @Test
    public void testDeleteTenantProfile() {
        TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile");
        TenantProfile savedTenantProfile = tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile);
        tenantProfileService.deleteTenantProfile(TenantId.SYS_TENANT_ID, savedTenantProfile.getId());
        TenantProfile foundTenantProfile = tenantProfileService.findTenantProfileById(TenantId.SYS_TENANT_ID, savedTenantProfile.getId());
        Assert.assertNull(foundTenantProfile);
    }

    @Test
    public void testFindTenantProfiles() {

        List<TenantProfile> tenantProfiles = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<TenantProfile> pageData = tenantProfileService.findTenantProfiles(TenantId.SYS_TENANT_ID, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());
        tenantProfiles.addAll(pageData.getData());

        for (int i=0;i<28;i++) {
            TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile"+i);
            tenantProfiles.add(tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile));
        }

        List<TenantProfile> loadedTenantProfiles = new ArrayList<>();
        pageLink = new PageLink(17);
        do {
            pageData = tenantProfileService.findTenantProfiles(TenantId.SYS_TENANT_ID, pageLink);
            loadedTenantProfiles.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(tenantProfiles, idComparator);
        Collections.sort(loadedTenantProfiles, idComparator);

        Assert.assertEquals(tenantProfiles, loadedTenantProfiles);

        for (TenantProfile tenantProfile : loadedTenantProfiles) {
            tenantProfileService.deleteTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile.getId());
        }

        pageLink = new PageLink(17);
        pageData = tenantProfileService.findTenantProfiles(TenantId.SYS_TENANT_ID, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());

    }

    @Test
    public void testFindTenantProfileInfos() {

        List<TenantProfile> tenantProfiles = new ArrayList<>();

        for (int i=0;i<28;i++) {
            TenantProfile tenantProfile = this.createTenantProfile("Tenant Profile"+i);
            tenantProfiles.add(tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, tenantProfile));
        }

        List<EntityInfo> loadedTenantProfileInfos = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<EntityInfo> pageData;
        do {
            pageData = tenantProfileService.findTenantProfileInfos(TenantId.SYS_TENANT_ID, pageLink);
            loadedTenantProfileInfos.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(tenantProfiles, idComparator);
        Collections.sort(loadedTenantProfileInfos, tenantProfileInfoIdComparator);

        List<EntityInfo> tenantProfileInfos = tenantProfiles.stream().map(tenantProfile -> new EntityInfo(tenantProfile.getId(),
                tenantProfile.getName())).collect(Collectors.toList());

        Assert.assertEquals(tenantProfileInfos, loadedTenantProfileInfos);

        for (EntityInfo tenantProfile : loadedTenantProfileInfos) {
            tenantProfileService.deleteTenantProfile(TenantId.SYS_TENANT_ID, new TenantProfileId(tenantProfile.getId().getId()));
        }

        pageLink = new PageLink(17);
        pageData = tenantProfileService.findTenantProfileInfos(TenantId.SYS_TENANT_ID, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertTrue(pageData.getData().isEmpty());

    }

    private TenantProfile createTenantProfile(String name) {
        TenantProfile tenantProfile = new TenantProfile();
        tenantProfile.setName(name);
        tenantProfile.setDescription(name + " Test");
        tenantProfile.setProfileData(new TenantProfileData());
        tenantProfile.setDefault(false);
        tenantProfile.setIsolatedTbCore(false);
        tenantProfile.setIsolatedTbRuleEngine(false);
        return tenantProfile;
    }

}
