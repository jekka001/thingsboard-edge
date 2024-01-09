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
package org.thingsboard.server.msa.ui.tests.deviceProfileSmoke;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.openqa.selenium.Keys;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.thingsboard.server.msa.ui.base.AbstractDriverBaseTest;
import org.thingsboard.server.msa.ui.pages.LoginPageHelper;
import org.thingsboard.server.msa.ui.pages.ProfilesPageHelper;
import org.thingsboard.server.msa.ui.pages.SideBarMenuViewHelper;
import org.thingsboard.server.msa.ui.utils.EntityPrototypes;

import static org.thingsboard.server.msa.ui.base.AbstractBasePage.random;
import static org.thingsboard.server.msa.ui.utils.Const.EMPTY_DEVICE_PROFILE_MESSAGE;
import static org.thingsboard.server.msa.ui.utils.Const.ENTITY_NAME;
import static org.thingsboard.server.msa.ui.utils.Const.NAME_IS_REQUIRED_MESSAGE;
import static org.thingsboard.server.msa.ui.utils.Const.SAME_NAME_WARNING_DEVICE_PROFILE_MESSAGE;

public class CreateDeviceProfileTest extends AbstractDriverBaseTest {

    private SideBarMenuViewHelper sideBarMenuView;
    private ProfilesPageHelper profilesPage;
    private String name;

    @BeforeClass
    public void login() {
        new LoginPageHelper(driver).authorizationTenant();
        sideBarMenuView = new SideBarMenuViewHelper(driver);
        profilesPage = new ProfilesPageHelper(driver);
    }

    @AfterMethod
    public void delete() {
        if (name != null) {
            testRestClient.deleteDeviseProfile(getDeviceProfileByName(name).getId());
            name = null;
        }
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 10, groups = "smoke")
    @Description("Add device profile after specifying the name (text/numbers /special characters)")
    public void createDeviceProfile() {
        String name = ENTITY_NAME + random();

        sideBarMenuView.openDeviceProfiles();
        profilesPage.openCreateDeviceProfileView();
        profilesPage.createDeviceProfileEnterName(name);
        profilesPage.addDeviceProfileAddBtn().click();
        profilesPage.refreshBtn().click();
        this.name = name;

        Assert.assertNotNull(profilesPage.entity(name));
        Assert.assertTrue(profilesPage.entity(name).isDisplayed());
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Add device profile after specifying the name with details")
    public void createDeviceProfileWithDetails() {
        String name = ENTITY_NAME + random();
        String ruleChain = "Root Rule Chain";
        String mobileDashboard = "Firmware";
        String queue = "Main";
        String description = "Description";

        sideBarMenuView.openDeviceProfiles();
        profilesPage.openCreateDeviceProfileView();
        profilesPage.createDeviceProfileEnterName(name);
        profilesPage.addDeviceProfileViewChooseRuleChain(ruleChain);
        profilesPage.addDeviceProfileViewChooseMobileDashboard(mobileDashboard);
        profilesPage.addDeviceProfileViewChooseQueue(queue);
        profilesPage.addDeviceProfileViewEnterDescription(description);
        profilesPage.addDeviceProfileAddBtn().click();
        profilesPage.refreshBtn().click();
        this.name = name;
        profilesPage.entity(name).click();
        profilesPage.setName();
        profilesPage.setRuleChain();
        profilesPage.setMobileDashboard();
        profilesPage.setQueue();
        profilesPage.setDescription();

        Assert.assertNotNull(profilesPage.entity(name));
        Assert.assertTrue(profilesPage.entity(name).isDisplayed());
        Assert.assertEquals(name, profilesPage.getName());
        Assert.assertEquals(ruleChain, profilesPage.getRuleChain());
        Assert.assertEquals(mobileDashboard, profilesPage.getMobileDashboard());
        Assert.assertEquals(queue, profilesPage.getQueue());
        Assert.assertEquals(description, profilesPage.getDescription());
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Create a device profile with the same name")
    public void createDeviseProfileWithSameName() {
        String name = ENTITY_NAME + random();
        testRestClient.postDeviceProfile(EntityPrototypes.defaultDeviceProfile(name));
        this.name = name;

        sideBarMenuView.openDeviceProfiles();
        profilesPage.openCreateDeviceProfileView();
        profilesPage.createDeviceProfileEnterName(name);
        profilesPage.addDeviceProfileAddBtn().click();

        Assert.assertNotNull(profilesPage.warningMessage());
        Assert.assertTrue(profilesPage.warningMessage().isDisplayed());
        Assert.assertEquals(profilesPage.warningMessage().getText(), SAME_NAME_WARNING_DEVICE_PROFILE_MESSAGE);
        Assert.assertNotNull(profilesPage.addDeviceProfileView());
        Assert.assertTrue(profilesPage.addDeviceProfileView().isDisplayed());
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Add device profile without the name")
    public void createDeviceProfileWithoutName() {
        sideBarMenuView.openDeviceProfiles();
        profilesPage.openCreateDeviceProfileView();
        profilesPage.addDeviceProfileAddBtn().click();

        Assert.assertNotNull(profilesPage.addDeviceProfileView());
        Assert.assertTrue(profilesPage.addDeviceProfileView().isDisplayed());
        Assert.assertNotNull(profilesPage.errorMessage());
        Assert.assertEquals(profilesPage.errorMessage().getText(), NAME_IS_REQUIRED_MESSAGE);
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Create device profile only with spase in name")
    public void createDeviseProfileWithOnlySpaceInName() {
        sideBarMenuView.openDeviceProfiles();
        profilesPage.openCreateDeviceProfileView();
        profilesPage.createDeviceProfileEnterName(Keys.SPACE);
        profilesPage.addDeviceProfileAddBtn().click();

        Assert.assertNotNull(profilesPage.warningMessage());
        Assert.assertTrue(profilesPage.warningMessage().isDisplayed());
        Assert.assertEquals(profilesPage.warningMessage().getText(), EMPTY_DEVICE_PROFILE_MESSAGE);
        Assert.assertNotNull(profilesPage.addDeviceProfileView());
        Assert.assertTrue(profilesPage.addDeviceProfileView().isDisplayed());
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Add device profile after specifying the name (text/numbers /special characters) without refresh")
    public void createDeviceProfileWithoutRefresh() {
        String name = ENTITY_NAME + random();

        sideBarMenuView.openDeviceProfiles();
        profilesPage.openCreateDeviceProfileView();
        profilesPage.createDeviceProfileEnterName(name);
        profilesPage.addDeviceProfileAddBtn().click();
        this.name = name;

        Assert.assertNotNull(profilesPage.entity(name));
        Assert.assertTrue(profilesPage.entity(name).isDisplayed());
    }

    @Epic("Device profile smoke tests")
    @Feature("Create device profile")
    @Test(priority = 30, groups = "smoke")
    @Description("Go to device profile documentation page")
    public void documentation() {
        String urlPath = "docs/pe/user-guide/device-profiles/";

        sideBarMenuView.openDeviceProfiles();
        profilesPage.profileNames().get(0).click();
        profilesPage.goToProfileHelpPage();

        Assert.assertTrue(urlContains(urlPath), "URL contains " + urlPath);
    }
}

