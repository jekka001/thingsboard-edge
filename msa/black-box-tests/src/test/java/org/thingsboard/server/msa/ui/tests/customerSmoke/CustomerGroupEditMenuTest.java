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
package org.thingsboard.server.msa.ui.tests.customerSmoke;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.msa.ui.base.AbstractDriverBaseTest;
import org.thingsboard.server.msa.ui.pages.CustomerPageHelper;
import org.thingsboard.server.msa.ui.pages.LoginPageHelper;
import org.thingsboard.server.msa.ui.pages.SideBarMenuViewHelper;
import org.thingsboard.server.msa.ui.utils.DataProviderCredential;
import org.thingsboard.server.msa.ui.utils.EntityPrototypes;

import static org.thingsboard.server.msa.ui.base.AbstractBasePage.getRandomNumber;
import static org.thingsboard.server.msa.ui.base.AbstractBasePage.random;
import static org.thingsboard.server.msa.ui.utils.Const.EMPTY_GROUP_NAME_MESSAGE;
import static org.thingsboard.server.msa.ui.utils.Const.ENTITY_NAME;

public class CustomerGroupEditMenuTest extends AbstractDriverBaseTest {
    private SideBarMenuViewHelper sideBarMenuView;
    private CustomerPageHelper customerPage;
    private String customerGroupName;

    @BeforeClass
    public void login() {
        new LoginPageHelper(driver).authorizationTenant();
        sideBarMenuView = new SideBarMenuViewHelper(driver);
        customerPage = new CustomerPageHelper(driver);
    }

    @AfterMethod
    public void delete() {
        if (customerGroupName != null) {
            testRestClient.deleteEntityGroup(getEntityGroupByName(EntityType.CUSTOMER, customerGroupName).getId());
            customerGroupName = null;
        }
    }

    @Epic("Customers smoke tests")
    @Feature("Edit customer group")
    @Test(priority = 10, groups = "smoke")
    @Description("Change name by edit menu")
    public void changeTitle() {
        String customerGroupName = ENTITY_NAME + random();
        testRestClient.postEntityGroup(EntityPrototypes.defaultEntityGroupPrototype(customerGroupName, EntityType.CUSTOMER));
        this.customerGroupName = customerGroupName;
        String changedName = "Changed" + getRandomNumber();

        sideBarMenuView.goToCustomerGroups();
        customerPage.detailsBtn(customerGroupName).click();
        customerPage.setHeaderName();
        String nameBefore = customerPage.getHeaderName();
        customerPage.entityGroupEditPencilBtn().click();
        customerPage.changeNameEditMenu(changedName);
        customerPage.entityGroupDoneBtnEditView().click();
        this.customerGroupName = changedName;
        customerPage.setHeaderName();
        String nameAfter = customerPage.getHeaderName();

        Assert.assertNotEquals(nameBefore, nameAfter);
        Assert.assertEquals(changedName, nameAfter);
    }

    @Epic("Customers smoke tests")
    @Feature("Edit customer group")
    @Test(priority = 20, groups = "smoke")
    @Description("Delete name and save")
    public void deleteName() {
        String customerGroupName = ENTITY_NAME + random();
        testRestClient.postEntityGroup(EntityPrototypes.defaultEntityGroupPrototype(customerGroupName, EntityType.CUSTOMER));
        this.customerGroupName = customerGroupName;

        sideBarMenuView.goToCustomerGroups();
        customerPage.detailsBtn(customerGroupName).click();
        customerPage.entityGroupEditPencilBtn().click();
        customerPage.nameFieldEditMenu().clear();

        Assert.assertFalse(customerPage.entityGroupDoneBtnVisibleEditView().isEnabled());
    }

    @Epic("Customers smoke tests")
    @Feature("Edit customer group")
    @Test(priority = 20, groups = "smoke")
    @Description("Save only with space")
    public void saveOnlyWithSpace() {
        String customerGroupName = ENTITY_NAME + random();
        testRestClient.postEntityGroup(EntityPrototypes.defaultEntityGroupPrototype(customerGroupName, EntityType.CUSTOMER));
        this.customerGroupName = customerGroupName;

        sideBarMenuView.goToCustomerGroups();
        customerPage.detailsBtn(customerGroupName).click();
        customerPage.entityGroupEditPencilBtn().click();
        customerPage.changeNameEditMenu(" ");
        customerPage.entityGroupDoneBtnEditView().click();
        customerPage.setHeaderName();

        Assert.assertNotNull(customerPage.warningMessage());
        Assert.assertTrue(customerPage.warningMessage().isDisplayed());
        Assert.assertEquals(customerPage.warningMessage().getText(), EMPTY_GROUP_NAME_MESSAGE);
        Assert.assertEquals(customerGroupName, customerPage.getHeaderName());
    }

    @Epic("Customers smoke tests")
    @Feature("Edit customer group")
    @Test(priority = 20, groups = "smoke", dataProviderClass = DataProviderCredential.class, dataProvider = "editMenuDescription")
    @Description("Write the description and save the changes/Change the description and save the changes/Delete the description and save the changes")
    public void editDescription(String description, String newDescription, String finalDescription) {
        String customerGroupName = ENTITY_NAME + random();
        testRestClient.postEntityGroup(EntityPrototypes.defaultEntityGroupPrototype(customerGroupName, EntityType.CUSTOMER, description));
        this.customerGroupName = customerGroupName;

        sideBarMenuView.goToCustomerGroups();
        customerPage.detailsBtn(customerGroupName).click();
        customerPage.entityGroupEditPencilBtn().click();
        customerPage.descriptionEntityView().sendKeys(newDescription);
        customerPage.entityGroupDoneBtnEditView().click();
        customerPage.setDescription();

        Assert.assertEquals(customerPage.getDescription(), finalDescription);
    }
}
