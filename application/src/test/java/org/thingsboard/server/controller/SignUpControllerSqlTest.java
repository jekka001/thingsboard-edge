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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.testcontainers.shaded.org.apache.commons.lang3.RandomStringUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.CustomerInfo;
import org.thingsboard.server.common.data.EntityInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.menu.CMAssigneeType;
import org.thingsboard.server.common.data.menu.CMScope;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.mobile.app.MobileApp;
import org.thingsboard.server.common.data.mobile.app.MobileAppStatus;
import org.thingsboard.server.common.data.mobile.bundle.MobileAppBundle;
import org.thingsboard.server.common.data.oauth2.PlatformType;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.selfregistration.CaptchaParams;
import org.thingsboard.server.common.data.selfregistration.MobileRedirectParams;
import org.thingsboard.server.common.data.selfregistration.MobileSelfRegistrationParams;
import org.thingsboard.server.common.data.selfregistration.SignUpField;
import org.thingsboard.server.common.data.selfregistration.SignUpFieldId;
import org.thingsboard.server.common.data.selfregistration.WebSelfRegistrationParams;
import org.thingsboard.server.common.data.signup.SignUpRequest;
import org.thingsboard.server.common.data.signup.SignUpResult;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.menu.CustomMenuDao;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.service.entitiy.TbLogEntityActionService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DaoSqlTest
public class SignUpControllerSqlTest extends AbstractControllerTest {

    public static final String RECAPTCHA_DUMMY_RESPONSE =
            "03AGdBq25eKTPJzWYaCnIx7JLlcQIEIZHX8IMiXbWb39HnZPMb8fE61JxA_xA-UAcPZhFvWJo00-VukIJXky" +
                    "_Fp6vPUseVA0564BBUKJuRU-jA591Wx46ZDUXqvWtsXpzgSSv5cOXFgaOpkM0pGa9Azw8CaPjVZN0Z13PKsvKogtSpess" +
                    "-n_-OMbVo0tkjGfiy34ih5Uf1l6LhbxVR7NYfzKqZPD2qRM25jcRNugQbxmFpemAaREcjkOmL3tr23EyRVWgDsv032DqiaI" +
                    "IcCoNT3zUoqjfDo1m-yL3kwtO4-WqEOoP2oO353-pqjMYMPaYpjjQJXxHL4qJC1xky4ANAI_th6GtsHwnfLw_sWDHlPgb" +
                    "-IEW8wcD-zZW5TBpagv7p0V08ebdqxCkvb-7p4QrgNXQA_psw4SEHIg";

    public static final String CUSTOMER_TEST_EMAIL = "force_push@junior.com";
    public static final String CUSTOMER_TITLE_PREFIX = "Prefix-";
    private static List<UUID> idsToRemove = new ArrayList<>();

    private EntityGroup customerGroup;
    private CustomMenu customMenu;

    @SpyBean
    protected TbLogEntityActionService logEntityActionService;

    @Autowired
    protected UserService userService;

    @Autowired
    private CustomMenuDao customMenuDao;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected TenantService tenantService;

    @Autowired
    protected EntityGroupService entityGroupService;

    @Autowired
    protected AdminSettingsService adminSettingsService;

    @Autowired
    protected WhiteLabelingService whiteLabelingService;

    @Before
    public void beforeTest() throws Exception {
        loginTenantAdmin();

        customMenu = new CustomMenu();
        customMenu.setName(RandomStringUtils.randomAlphabetic(10));
        customMenu.setScope(CMScope.CUSTOMER);
        customMenu.setAssigneeType(CMAssigneeType.CUSTOMERS);

        customMenu = doPost("/api/customMenu", customMenu, CustomMenu.class);
        idsToRemove.add(customMenu.getUuidId());

        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName("Test customer group");
        entityGroup.setType(EntityType.CUSTOMER);
        customerGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class);

        WebSelfRegistrationParams selfRegistrationParams = new WebSelfRegistrationParams();
        selfRegistrationParams.setTitle("Please sign up");
        CaptchaParams captcha = new CaptchaParams();
        captcha.setSiteKey("6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI");
        captcha.setSecretKey("6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe");
        selfRegistrationParams.setCaptcha(captcha);
        selfRegistrationParams.setShowTermsOfUse(true);
        selfRegistrationParams.setShowPrivacyPolicy(true);
        selfRegistrationParams.setDomainName("localhost");
        selfRegistrationParams.setCustomerTitlePrefix(CUSTOMER_TITLE_PREFIX);
        selfRegistrationParams.setCustomerGroupId(customerGroup.getId());
        selfRegistrationParams.setCustomMenuId(customMenu.getId());
        selfRegistrationParams.setPermissions(Collections.emptyList());
        selfRegistrationParams.setSignUpFields(List.of(new SignUpField(SignUpFieldId.EMAIL, "email", true),
                new SignUpField(SignUpFieldId.PASSWORD, "password", true)));

        doPost("/api/selfRegistration/selfRegistrationParams",
                selfRegistrationParams, JsonNode.class);
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();
        whiteLabelingService.deleteTenantWhiteLabeling(tenantId);

        customMenuDao.removeAllByIds(idsToRemove);
        idsToRemove = new ArrayList<>();
    }

    @Test
    public void testSelfRegisterUser() throws Exception {
        createWebSelfRegistrationSettings();
        List<Customer> customers = customerService.findCustomersByTenantId(tenantId, new PageLink(10)).getData();

        SignUpRequest signUpRequest = createWebSignUpRequest(CUSTOMER_TEST_EMAIL);
        doSignUp(signUpRequest);

        List<Customer> customersAfterSignUp = customerService.findCustomersByTenantId(tenantId, new PageLink(10)).getData();
        assertThat(customersAfterSignUp.size()).isEqualTo(customers.size() + 1);
        Customer createdCustomer = customersAfterSignUp.stream().filter(customer -> customer.getName().equals(CUSTOMER_TEST_EMAIL)).findAny()
                .orElseThrow(() -> new RuntimeException("Customer was not found"));

        assertThat(createdCustomer.getEmail()).isEqualTo(CUSTOMER_TEST_EMAIL);
        assertThat(createdCustomer.getName()).isEqualTo(CUSTOMER_TEST_EMAIL);

        User user = userService.findUserByEmail(tenantId, CUSTOMER_TEST_EMAIL);
        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo(CUSTOMER_TEST_EMAIL);

        removeCreatedUser(user);
    }

    @Test
    public void testSelfRegisterUserFromMobileApp() throws Exception {
        String appSecret = StringUtils.randomAlphanumeric(24);
        String mobilePkgName = "my.test.android.app";
        createMobileSelfRegistrationSettings(mobilePkgName, appSecret, PlatformType.ANDROID);

        List<Customer> customers = customerService.findCustomersByTenantId(tenantId, new PageLink(10)).getData();

        SignUpRequest signUpRequest = createMobileSignUpRequest(CUSTOMER_TEST_EMAIL, mobilePkgName, appSecret, PlatformType.ANDROID);
        doSignUp(signUpRequest);

        List<Customer> customersAfterSignUp = customerService.findCustomersByTenantId(tenantId, new PageLink(10)).getData();
        assertThat(customersAfterSignUp.size()).isEqualTo(customers.size() + 1);
        Customer createdCustomer = customersAfterSignUp.stream().filter(customer -> customer.getName().equals(CUSTOMER_TITLE_PREFIX + CUSTOMER_TEST_EMAIL)).findAny()
                .orElseThrow(() -> new RuntimeException("Customer was not found"));

        assertThat(createdCustomer.getEmail()).isEqualTo(CUSTOMER_TEST_EMAIL);
        assertThat(createdCustomer.getName()).isEqualTo(CUSTOMER_TITLE_PREFIX + CUSTOMER_TEST_EMAIL);
        assertThat(createdCustomer.getAddress()).isEqualTo(signUpRequest.getFields().get(SignUpFieldId.ADDRESS));
        assertThat(createdCustomer.getAddress2()).isEqualTo(signUpRequest.getFields().get(SignUpFieldId.ADDRESS2));
        assertThat(createdCustomer.getCountry()).isEqualTo(signUpRequest.getFields().get(SignUpFieldId.COUNTRY));
        assertThat(createdCustomer.getCity()).isEqualTo(signUpRequest.getFields().get(SignUpFieldId.CITY));
        assertThat(createdCustomer.getState()).isEqualTo(signUpRequest.getFields().get(SignUpFieldId.STATE));
        assertThat(createdCustomer.getPhone()).isEqualTo(signUpRequest.getFields().get(SignUpFieldId.PHONE));
        assertThat(createdCustomer.getCustomMenuId()).isEqualTo(customMenu.getId());

        CustomerInfo customerInfoById = customerService.findCustomerInfoById(tenantId, createdCustomer.getId());
        assertThat(customerInfoById.getGroups()).containsExactly(new EntityInfo(customerGroup.getId(), customerGroup.getName()));

        User user = userService.findUserByEmail(tenantId, CUSTOMER_TEST_EMAIL);
        assertThat(user).isNotNull();
        assertThat(user.getEmail()).isEqualTo(CUSTOMER_TEST_EMAIL);

        removeCreatedUser(user);
    }

    @Test
    public void testSelfRegistrationCreateMessageInRuleChain() throws Exception {
        var signUpRequest = createWebSignUpRequest(CUSTOMER_TEST_EMAIL);
        doSignUp(signUpRequest);

        var user = userService.findUserByEmail(tenantId, CUSTOMER_TEST_EMAIL);
        var customer = customerService.findCustomerById(tenantId, user.getCustomerId());
        var entityGroup = entityGroupService.findOrCreateUserGroup(
                tenantId, customer.getId(), "Self Registration Users", "Autogenerated Self Registration group"
        );

        Assert.assertNotNull(user);
        Assert.assertEquals(CUSTOMER_TEST_EMAIL, user.getEmail());

        verify(logEntityActionService, times(1)).logEntityAction(
                eq(tenantId),
                eq(customer.getId()),
                eq(customer),
                eq(customer.getId()),
                eq(ActionType.ADDED),
                eq(null)
        );
        verify(logEntityActionService, times(1)).logEntityAction(
                eq(tenantId),
                eq(user.getId()),
                argThat(o -> {
                    if (!(o instanceof User)) return false;
                    User u = (User) o;
                    return u.getId().equals(user.getId()) && u.getEmail().equals(user.getEmail());
                }),
                eq(customer.getId()),
                eq(ActionType.ADDED),
                eq(null)
        );
        verify(logEntityActionService, times(1)).logEntityAction(
                eq(tenantId),
                eq(user.getId()),
                argThat(o -> {
                    if (!(o instanceof User)) return false;
                    User u = (User) o;
                    return u.getId().equals(user.getId()) && u.getEmail().equals(user.getEmail());
                }),
                eq(customer.getId()),
                eq(ActionType.ADDED_TO_ENTITY_GROUP),
                eq(null),
                eq(entityGroup.toString()),
                eq(entityGroup.getName())
        );

        removeCreatedUser(user);
    }

    protected SignUpRequest createWebSignUpRequest(String email) {
        var signUpRequest = new SignUpRequest();
        signUpRequest.setFields(Map.of(SignUpFieldId.EMAIL, email,
                SignUpFieldId.FIRST_NAME,"John",
                SignUpFieldId.LAST_NAME,"Gilmore",
                SignUpFieldId.PASSWORD,"abcdef123"));
        signUpRequest.setRecaptchaResponse(RECAPTCHA_DUMMY_RESPONSE);
        return signUpRequest;
    }

    protected SignUpRequest createMobileSignUpRequest(String email, String pkgName, String appSecret, PlatformType platformType) {
        var signUpRequest = new SignUpRequest();
        signUpRequest.setFields(Map.of(SignUpFieldId.EMAIL, email,
                SignUpFieldId.FIRST_NAME,"John",
                SignUpFieldId.LAST_NAME,"Gilmore",
                SignUpFieldId.PASSWORD,"abcdef123",
                SignUpFieldId.COUNTRY, "Ukraine",
                SignUpFieldId.STATE, "Kyiv",
                SignUpFieldId.PHONE, "5555555555",
                SignUpFieldId.ADDRESS, "test address",
                SignUpFieldId.ADDRESS2, "test address2",
                SignUpFieldId.CITY, "Kyiv"));
        signUpRequest.setRecaptchaResponse(RECAPTCHA_DUMMY_RESPONSE);
        signUpRequest.setPkgName(pkgName);
        signUpRequest.setAppSecret(appSecret);
        signUpRequest.setPlatform(platformType);
        return signUpRequest;
    }

    protected void doSignUp(SignUpRequest signUpRequest) throws Exception {
        var result = doPostWithTypedResponse(
                "/api/noauth/signup", signUpRequest,
                new TypeReference<SignUpResult>() {
                }
        );
        Assert.assertEquals("Error while doing SignUp", SignUpResult.SUCCESS, result);
    }

    private void removeCreatedUser(User user) {
        userService.deleteUser(tenantId, user);
        var found = userService.findUserByEmail(tenantId, user.getEmail());
        Assert.assertNull("Expected that created user is deleted but one found!", found);
    }

    private void createWebSelfRegistrationSettings() {
        WebSelfRegistrationParams selfRegistrationParams = new WebSelfRegistrationParams();
        selfRegistrationParams.setTitle("Please sign up");
        CaptchaParams captcha = new CaptchaParams();
        captcha.setSiteKey("6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI");
        captcha.setSecretKey("6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe");
        selfRegistrationParams.setCaptcha(captcha);
        selfRegistrationParams.setShowTermsOfUse(true);
        selfRegistrationParams.setShowPrivacyPolicy(true);
        selfRegistrationParams.setDomainName("localhost");
        selfRegistrationParams.setPermissions(Collections.emptyList());

        doPost("/api/selfRegistration/selfRegistrationParams",
                selfRegistrationParams, JsonNode.class);
    }

    private void createMobileSelfRegistrationSettings(String mobilePkgName, String appSecret, PlatformType platformType) {
        MobileApp mobileApp = validMobileApp(mobilePkgName, appSecret, platformType);
        mobileApp = doPost("/api/mobile/app", mobileApp, MobileApp.class);

        MobileAppBundle mobileAppBundle = new MobileAppBundle();
        mobileAppBundle.setTitle("Test bundle");
        if (platformType.equals(PlatformType.ANDROID)) {
            mobileAppBundle.setAndroidAppId(mobileApp.getId());
        } else {
            mobileAppBundle.setIosAppId(mobileApp.getId());
        }
        MobileSelfRegistrationParams selfRegistrationParams = createMobileSelfRegistrationParams();
        mobileAppBundle.setSelfRegistrationParams(selfRegistrationParams);
        MobileAppBundle createdMobileAppBundle = doPost("/api/mobile/bundle", mobileAppBundle, MobileAppBundle.class);
    }

    private MobileSelfRegistrationParams createMobileSelfRegistrationParams() {
        MobileSelfRegistrationParams selfRegistrationParams = new MobileSelfRegistrationParams();
        selfRegistrationParams.setTitle("Please sign up");
        CaptchaParams captcha = new CaptchaParams();
        captcha.setSecretKey("6LeIxAcTAAAAAGG-vFI1TnRWxMZNFuojJ4WifJWe");
        captcha.setSiteKey("6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI");
        captcha.setLogActionName("sign_up");
        selfRegistrationParams.setCaptcha(captcha);
        selfRegistrationParams.setShowPrivacyPolicy(true);
        selfRegistrationParams.setShowTermsOfUse(true);
        selfRegistrationParams.setEnabled(true);
        selfRegistrationParams.setNotificationEmail("testEmail@gmail.com");
        selfRegistrationParams.setTermsOfUse("My terms of use");
        selfRegistrationParams.setPrivacyPolicy("My privacy policy");
        selfRegistrationParams.setPermissions(Collections.emptyList());
        selfRegistrationParams.setCustomerTitlePrefix(CUSTOMER_TITLE_PREFIX);
        selfRegistrationParams.setCustomerGroupId(customerGroup.getId());
        selfRegistrationParams.setCustomMenuId(customMenu.getId());
        selfRegistrationParams.setSignUpFields(List.of(new SignUpField(SignUpFieldId.EMAIL, "email", true),
                new SignUpField(SignUpFieldId.PASSWORD, "password", true)));
        MobileRedirectParams redirect = new MobileRedirectParams();
        redirect.setHost("test");
        redirect.setScheme("scheme");
        selfRegistrationParams.setRedirect(redirect);
        selfRegistrationParams.setSignUpFields(List.of(new SignUpField(SignUpFieldId.EMAIL, "email",true)));
        return selfRegistrationParams;
    }

    private MobileApp validMobileApp(String mobileAppName, String secret, PlatformType platformType) {
        MobileApp mobileApp = new MobileApp();
        mobileApp.setStatus(MobileAppStatus.DRAFT);
        mobileApp.setPkgName(mobileAppName);
        mobileApp.setPlatformType(platformType);
        mobileApp.setAppSecret(secret);
        return mobileApp;
    }
}
