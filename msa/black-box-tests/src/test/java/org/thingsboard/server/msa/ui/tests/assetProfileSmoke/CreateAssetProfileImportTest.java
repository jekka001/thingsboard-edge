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
package org.thingsboard.server.msa.ui.tests.assetProfileSmoke;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.thingsboard.server.msa.ui.base.AbstractDriverBaseTest;
import org.thingsboard.server.msa.ui.pages.LoginPageHelper;
import org.thingsboard.server.msa.ui.pages.ProfilesPageHelper;
import org.thingsboard.server.msa.ui.pages.SideBarMenuViewHelper;
import org.thingsboard.server.msa.ui.utils.EntityPrototypes;

import static org.thingsboard.server.msa.ui.utils.Const.EMPTY_IMPORT_MESSAGE;
import static org.thingsboard.server.msa.ui.utils.Const.IMPORT_ASSET_PROFILE_FILE_NAME;
import static org.thingsboard.server.msa.ui.utils.Const.IMPORT_ASSET_PROFILE_NAME;
import static org.thingsboard.server.msa.ui.utils.Const.IMPORT_TXT_FILE_NAME;
import static org.thingsboard.server.msa.ui.utils.Const.SAME_NAME_WARNING_ASSET_PROFILE_MESSAGE;

public class CreateAssetProfileImportTest extends AbstractDriverBaseTest {

    private SideBarMenuViewHelper sideBarMenuView;
    private ProfilesPageHelper profilesPage;
    private final String absolutePathToFileImportAssetProfile = getClass().getClassLoader().getResource(IMPORT_ASSET_PROFILE_FILE_NAME).getPath();
    private final String absolutePathToFileImportTxt = getClass().getClassLoader().getResource(IMPORT_TXT_FILE_NAME).getPath();
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
            testRestClient.deleteAssetProfile(getAssetProfileByName(name).getId());
            name = null;
        }
    }

    @Epic("Asset profiles smoke")
    @Feature("Import asset profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Import asset profile")
    public void importAssetProfile() {
        sideBarMenuView.openAssetProfiles();
        profilesPage.openImportAssetProfileView();
        profilesPage.browseFile().sendKeys(absolutePathToFileImportAssetProfile);
        profilesPage.importBrowseFileBtn().click();
        name = IMPORT_ASSET_PROFILE_NAME;
        profilesPage.refreshBtn().click();

        Assert.assertNotNull(profilesPage.entity(IMPORT_ASSET_PROFILE_NAME));
        Assert.assertTrue(profilesPage.entity(IMPORT_ASSET_PROFILE_NAME).isDisplayed());
    }

    @Epic("Asset profiles smoke")
    @Feature("Import asset profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Import txt file")
    public void importTxtFile() {
        sideBarMenuView.openDeviceProfiles();
        profilesPage.openImportDeviceProfileView();
        profilesPage.browseFile().sendKeys(absolutePathToFileImportTxt);

        Assert.assertNotNull(profilesPage.importingFile(EMPTY_IMPORT_MESSAGE));
        Assert.assertTrue(profilesPage.importingFile(EMPTY_IMPORT_MESSAGE).isDisplayed());
    }

    @Epic("Asset profiles smoke")
    @Feature("Import asset profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Drop json file and delete it")
    public void addFileToImportAndRemove() {
        sideBarMenuView.openAssetProfiles();
        profilesPage.openImportAssetProfileView();
        profilesPage.browseFile().sendKeys(absolutePathToFileImportAssetProfile);
        profilesPage.clearImportFileBtn().click();

        Assert.assertNotNull(profilesPage.importingFile(EMPTY_IMPORT_MESSAGE));
        Assert.assertTrue(profilesPage.importingFile(EMPTY_IMPORT_MESSAGE).isDisplayed());
        Assert.assertTrue(profilesPage.assertEntityIsNotPresent(IMPORT_ASSET_PROFILE_NAME));
    }

    @Epic("Asset profiles smoke")
    @Feature("Import asset profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Import asset profile with same name")
    public void importAssetProfileWithSameName() {
        String name = IMPORT_ASSET_PROFILE_NAME;
        testRestClient.postAssetProfile(EntityPrototypes.defaultAssetProfile(name));
        this.name = name;

        sideBarMenuView.openAssetProfiles();
        profilesPage.openImportAssetProfileView();
        profilesPage.browseFile().sendKeys(absolutePathToFileImportAssetProfile);
        profilesPage.importBrowseFileBtn().click();
        profilesPage.refreshBtn().click();

        Assert.assertNotNull(profilesPage.warningMessage());
        Assert.assertTrue(profilesPage.warningMessage().isDisplayed());
        Assert.assertEquals(profilesPage.warningMessage().getText(), SAME_NAME_WARNING_ASSET_PROFILE_MESSAGE);
    }

    @Epic("Asset profiles smoke")
    @Feature("Import asset profile")
    @Test(priority = 20, groups = "smoke")
    @Description("Import asset profile without refresh")
    public void importAssetProfileWithoutRefresh() {
        sideBarMenuView.openAssetProfiles();
        profilesPage.openImportAssetProfileView();
        profilesPage.browseFile().sendKeys(absolutePathToFileImportAssetProfile);
        profilesPage.importBrowseFileBtn().click();
        name = IMPORT_ASSET_PROFILE_NAME;

        Assert.assertNotNull(profilesPage.entity(IMPORT_ASSET_PROFILE_NAME));
        Assert.assertTrue(profilesPage.entity(IMPORT_ASSET_PROFILE_NAME).isDisplayed());
    }
}
