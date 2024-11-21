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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceClient;
import com.google.cloud.recaptchaenterprise.v1.RecaptchaEnterpriseServiceSettings;
import com.google.recaptchaenterprise.v1.Assessment;
import com.google.recaptchaenterprise.v1.CreateAssessmentRequest;
import com.google.recaptchaenterprise.v1.Event;
import com.google.recaptchaenterprise.v1.ProjectName;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.MailService;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.mobile.app.MobileApp;
import org.thingsboard.server.common.data.mobile.bundle.MobileAppBundle;
import org.thingsboard.server.common.data.oauth2.PlatformType;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.common.data.security.model.JwtPair;
import org.thingsboard.server.common.data.selfregistration.EnterpriseCaptchaParams;
import org.thingsboard.server.common.data.selfregistration.V2CaptchaParams;
import org.thingsboard.server.common.data.selfregistration.MobileRedirectParams;
import org.thingsboard.server.common.data.selfregistration.SelfRegistrationParams;
import org.thingsboard.server.common.data.selfregistration.SignUpField;
import org.thingsboard.server.common.data.selfregistration.SignUpFieldId;
import org.thingsboard.server.common.data.selfregistration.V3CaptchaParams;
import org.thingsboard.server.common.data.selfregistration.WebSelfRegistrationParams;
import org.thingsboard.server.common.data.signup.SignUpRequest;
import org.thingsboard.server.common.data.signup.SignUpResult;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.config.SignUpConfig;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.data.RecaptchaValidationResult;
import org.thingsboard.server.exception.DataValidationException;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.auth.rest.RestAuthenticationDetails;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.security.model.token.JwtTokenFactory;
import org.thingsboard.server.service.security.system.SystemSecurityService;
import org.thingsboard.server.utils.MiscUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.ADDRESS;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.ADDRESS2;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.CITY;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.COUNTRY;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.EMAIL;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.FIRST_NAME;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.LAST_NAME;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.PASSWORD;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.PHONE;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.STATE;
import static org.thingsboard.server.common.data.selfregistration.SignUpFieldId.ZIP;
import static org.thingsboard.server.common.data.wl.WhiteLabelingType.SELF_REGISTRATION;

@RestController
@TbCoreComponent
@RequestMapping("/api")
@Slf4j
public class SignUpController extends BaseController {

    private static final String SELF_REGISTRATION_SETTINGS_WAS_NOT_FOUND = "Self registration settings was not found";
    private static final String SELF_REGISTRATION_SETTINGS_IS_NOT_ALLOWED = "Self registration is not allowed";

    private static final String WL_SETTINGS_WAS_NOT_FOUND = "White labeling settings was not found";

    private static final String INVALID_APP_SECRET = "Invalid Application Secret!";

    private static final String MOBILE_APP_BUNDLE_WAS_NOT_FOUND = "Mobile app bundle was not found";

    private static final String PRIVACY_POLICY_ACCEPTED = "privacyPolicyAccepted";

    private static final String TERMS_OF_USE_ACCEPTED = "termsOfUseAccepted";

    private RestTemplate restTemplate;

    @Autowired
    private SignUpConfig signUpConfig;

    @Autowired
    private MailService mailService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenFactory tokenFactory;

    @Autowired
    private SystemSecurityService systemSecurityService;

    @PostConstruct
    public void init() throws Exception {
        restTemplate = new RestTemplate();
    }

    @ApiOperation(value = "User Sign Up (signUp)",
            notes = "Process user sign up request. Creates the Customer and corresponding User based on self Registration parameters for the domain. " +
                    "See [Self Registration Controller](/swagger-ui.html#/self-registration-controller) for more details.  " +
                    "The result is either 'SUCCESS' or 'INACTIVE_USER_EXISTS'. " +
                    "If Success, the user will receive an email with instruction to activate the account. " +
                    "The content of the email is customizable via the mail templates.")
    @PostMapping(value = "/noauth/signup")
    public SignUpResult signUp(
            @Parameter(description = "A JSON value representing the signup request.", required = true)
            @RequestBody SignUpRequest signUpRequest, HttpServletRequest request) throws ThingsboardException, IOException {
        SelfRegistrationParams selfRegistrationParams;
        TenantId tenantId;
        if (!StringUtils.isEmpty(signUpRequest.getPkgName())) {
            validateAppSecret(signUpRequest);
            MobileAppBundle mobileAppBundle = checkMobileSRSettings(signUpRequest.getPkgName(), signUpRequest.getPlatform());
            selfRegistrationParams = mobileAppBundle.getSelfRegistrationParams();
            tenantId = mobileAppBundle.getTenantId();
        } else {
            WhiteLabeling whiteLabeling = checkWebSRSettings(request.getServerName());
            selfRegistrationParams = JacksonUtil.treeToValue(whiteLabeling.getSettings(), WebSelfRegistrationParams.class);
            tenantId = whiteLabeling.getTenantId();
        }

        //validate self-registration is allowed
        validateSelfRegistrationAllowed(selfRegistrationParams);

        //validate required fields
        validateRequiredFields(signUpRequest, selfRegistrationParams);
        String email = signUpRequest.getFields().get(EMAIL);
        String firstName = signUpRequest.getFields().get(FIRST_NAME);
        String lastName = signUpRequest.getFields().get(LAST_NAME);
        String password = signUpRequest.getFields().get(PASSWORD);

        //Verify recaptcha response
        if (selfRegistrationParams.getCaptcha() instanceof EnterpriseCaptchaParams) {
            validateEnterpriseReCaptcha(signUpRequest, request, selfRegistrationParams);
        } else if (selfRegistrationParams.getCaptcha() instanceof V2CaptchaParams captcha) {
            validateReCaptcha(signUpRequest.getRecaptchaResponse(), request.getRemoteAddr(), captcha.getSecretKey());
        } else if (selfRegistrationParams.getCaptcha() instanceof V3CaptchaParams captcha) {
            validateReCaptcha(signUpRequest.getRecaptchaResponse(), request.getRemoteAddr(), captcha.getSecretKey());
        } else {
            throw new DataValidationException("Error validating captcha: wrong captcha version");
        }

        //Verify email
        DataValidator.validateEmail(email);
        User existingUser = userService.findUserByEmail(tenantId, email);
        if (existingUser != null) {
            UserCredentials credentials = userService.findUserCredentialsByUserId(tenantId, existingUser.getId());
            if (credentials.isEnabled()) {
                throw new DataValidationException("User with email '" + existingUser.getEmail() + "' "
                        + " is already registered!");
            } else {
                return SignUpResult.INACTIVE_USER_EXISTS;
            }
        }

        systemSecurityService.validatePassword(password, null);

        Customer savedCustomer = createCustomer(signUpRequest, selfRegistrationParams, tenantId);
        EntityGroup savedUsersGroup = createCustomerUserGroup(selfRegistrationParams, savedCustomer);

        User savedUser = createUser(signUpRequest, selfRegistrationParams, savedCustomer);
        entityGroupService.addEntityToEntityGroup(tenantId, savedUsersGroup.getId(), savedUser.getId());
        UserCredentials savedUserCredentials = saveUserCredentials(savedUser, signUpRequest);

        try {
            sendEmailVerification(tenantId, request, savedUserCredentials, email, null, signUpRequest.getPkgName(), signUpRequest.getPlatform());
        } catch (ThingsboardException e) {
            customerService.deleteCustomer(tenantId, savedCustomer.getId());
            throw e;
        }
        sendUserActivityNotification(tenantId, Optional.ofNullable(firstName).orElse("") + " " + Optional.ofNullable(lastName).orElse(""),
                email, false, selfRegistrationParams.getNotificationEmail());

        logEntityActionService.logEntityAction(tenantId, savedCustomer.getId(), savedCustomer, savedCustomer.getId(),
                ActionType.ADDED, null);
        logEntityActionService.logEntityAction(tenantId, savedUser.getId(), savedUser, savedUser.getCustomerId(),
                ActionType.ADDED, null);
        logEntityActionService.logEntityAction(tenantId, savedUser.getId(), savedUser, savedCustomer.getId(),
                ActionType.ADDED_TO_ENTITY_GROUP, null, savedUsersGroup.toString(), savedUsersGroup.getName());

        return SignUpResult.SUCCESS;
    }

    private void sendEmailVerification(TenantId tenantId, HttpServletRequest request, UserCredentials userCredentials,
                                       String targetEmail, String baseUrl, String pkgName, PlatformType platformType) throws ThingsboardException {
        if (baseUrl == null) {
            baseUrl = MiscUtils.constructBaseUrl(request);
        }
        String activationLink = String.format("%s/api/noauth/activateEmail?emailCode=%s", baseUrl, userCredentials.getActivateToken());
        if (!StringUtils.isEmpty(pkgName)) {
            activationLink = String.format("%s&pkgName=%s&platform=%s", activationLink, pkgName, platformType);
        }
        try {
            mailService.sendActivationEmail(tenantId, activationLink, userCredentials.getActivationTokenTtl(), targetEmail);
        } catch (Exception e) {
            throw new ThingsboardException("Temporarily unable to send activation email", ThingsboardErrorCode.GENERAL);
        }
    }

    private void sendUserActivityNotification(TenantId tenantId, String userFullName, String userEmail, boolean activated, String infoMail) {
        try {
            if (activated) {
                mailService.sendUserActivatedEmail(tenantId, userFullName, userEmail, infoMail);
            } else {
                mailService.sendUserRegisteredEmail(tenantId, userFullName, userEmail, infoMail);
            }
        } catch (ThingsboardException e) {
            String action = activated ? "activation" : "registration";
            log.error("Failed to send notification email about user {}", action, e);
        }
    }

    @ApiOperation(value = "Resend Activation Email (resendEmailActivation)",
            notes = "Request to resend the activation email for the user. Checks that user was not activated yet.")
    @PostMapping(value = "/noauth/resendEmailActivation")
    public void resendEmailActivation(
            @Parameter(description = "Email of the user.", required = true, example = "john.doe@company.com")
            @RequestParam(value = "email") String email,
            @Parameter(description = "Optional package name of the mobile application.")
            @RequestParam(required = false) String pkgName,
            @Parameter(description = "Platform type", schema = @Schema(allowableValues = {"ANDROID", "IOS"}))
            @RequestParam(required = false) PlatformType platform,
            HttpServletRequest request) throws ThingsboardException, IOException {
        TenantId tenantId;
        if (!StringUtils.isEmpty(pkgName)) {
            MobileAppBundle mobileAppBundle = checkMobileSRSettings(pkgName, platform);
            tenantId = mobileAppBundle.getTenantId();
        } else {
            WhiteLabeling whiteLabeling = checkWebSRSettings(request.getServerName());
            tenantId = whiteLabeling.getTenantId();
        }

        User existingUser = userService.findUserByEmail(TenantId.SYS_TENANT_ID, email);
        if (existingUser != null) {
            UserCredentials credentials = userService.findUserCredentialsByUserId(existingUser.getTenantId(), existingUser.getId());
            if (credentials.isEnabled()) {
                throw new DataValidationException("User with email '" + existingUser.getEmail() + "' "
                        + " is already active!");
            } else {
                sendEmailVerification(tenantId, request, credentials, email, null, pkgName, platform);
            }
        } else {
            throw new DataValidationException("User with email '" + email + "' "
                    + " is not registered!");
        }
    }

    @ApiOperation(value = "Activate User using code from Email (activateEmail)",
            notes = "Activate the user using code(link) from the activation email. " +
                    "Validates the code an redirects according to the signup flow. " +
                    "Checks that user was not activated yet.")
    @GetMapping(value = "/noauth/activateEmail", params = {"emailCode"})
    public ResponseEntity<String> activateEmail(
            @Parameter(description = "Activation token.", required = true)
            @RequestParam(value = "emailCode") String emailCode,
            @Parameter(description = "Optional package name of the mobile application.")
            @RequestParam(required = false) String pkgName,
            @Parameter(description = "Platform type", schema = @Schema(allowableValues = {"ANDROID", "IOS"}))
            @RequestParam(required = false) PlatformType platform,
            HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        HttpStatus responseStatus;
        UserCredentials userCredentials = userService.findUserCredentialsByActivateToken(TenantId.SYS_TENANT_ID, emailCode);
        if (userCredentials != null) {
            String emailVerifiedURI = null;
            try {
                if (!StringUtils.isEmpty(pkgName)) {
                    MobileAppBundle mobileAppBundle = checkMobileSRSettings(pkgName, platform);
                    MobileRedirectParams redirect = mobileAppBundle.getSelfRegistrationParams().getRedirect();
                    emailVerifiedURI = redirect.getScheme() + "://" + redirect.getHost() + "/signup/emailVerified";
                } else {
                    emailVerifiedURI = "/signup/emailVerified";
                }
                URI location = new URI(emailVerifiedURI + "?emailCode=" + emailCode);
                headers.setLocation(location);
                responseStatus = HttpStatus.PERMANENT_REDIRECT;
            } catch (URISyntaxException | ThingsboardException e) {
                log.error("Unable to create URI with address [{}]", emailVerifiedURI);
                responseStatus = HttpStatus.BAD_REQUEST;
            }
        } else {
            responseStatus = HttpStatus.CONFLICT;
        }
        return new ResponseEntity<>(headers, responseStatus);
    }

    @ApiOperation(value = "Mobile Login redirect (mobileLogin)",
            notes = "This method generates redirect to the special link that is handled by mobile application. Useful for email verification flow on mobile app.")
    @GetMapping(value = "/noauth/login", params = {"pkgName, platform"})
    public ResponseEntity<String> mobileLogin(
            @Parameter(description = "Mobile app package name. Used to identify the application and build the redirect link.", required = true)
            @RequestParam(value = "pkgName") String pkgName,
            @Parameter(description = "Platform type", schema = @Schema(allowableValues = {"ANDROID", "IOS"}), required = true)
            @RequestParam PlatformType platform) throws ThingsboardException {
        HttpHeaders headers = new HttpHeaders();
        HttpStatus responseStatus;
        MobileAppBundle mobileAppBundle = checkMobileSRSettings(pkgName, platform);
        MobileRedirectParams redirect = mobileAppBundle.getSelfRegistrationParams().getRedirect();
        String redirectURI = redirect.getScheme() + "://" + redirect.getHost() + "/login";
        try {
            URI location = new URI(redirectURI);
            headers.setLocation(location);
            responseStatus = HttpStatus.PERMANENT_REDIRECT;
        } catch (URISyntaxException e) {
            log.error("Unable to create URI with address [{}]", redirectURI);
            responseStatus = HttpStatus.BAD_REQUEST;
        }
        return new ResponseEntity<>(headers, responseStatus);
    }

    @ApiOperation(value = "Activate and login using code from Email (activateUserByEmailCode)",
            notes = "Activate the user using code(link) from the activation email and return the JWT Token. " +
                    "Sends the notification and email about user activation. " +
                    "Checks that user was not activated yet.")
    @PostMapping(value = "/noauth/activateByEmailCode")
    public JwtPair activateUserByEmailCode(
            @Parameter(description = "Activation token.", required = true)
            @RequestParam(value = "emailCode") String emailCode,
            @Parameter(description = "Optional package name of the mobile application.")
            @RequestParam(required = false) String pkgName,
            @Parameter(description = "Platform type", schema = @Schema(allowableValues = {"ANDROID", "IOS"}))
            @RequestParam(required = false) PlatformType platform,
            HttpServletRequest request) throws ThingsboardException {
        SelfRegistrationParams selfRegistrationParams;
        TenantId tenantId;
        if (!StringUtils.isEmpty(pkgName)) {
            MobileAppBundle mobileAppBundle = checkMobileSRSettings(pkgName, platform);
            selfRegistrationParams = mobileAppBundle.getSelfRegistrationParams();
            tenantId = mobileAppBundle.getTenantId();
        } else {
            WhiteLabeling whiteLabeling = checkWebSRSettings(request.getServerName());
            selfRegistrationParams = JacksonUtil.treeToValue(whiteLabeling.getSettings(), WebSelfRegistrationParams.class);
            tenantId = whiteLabeling.getTenantId();
        }

        UserCredentials userCredentials = userService.findUserCredentialsByActivateToken(TenantId.SYS_TENANT_ID, emailCode);
        if (userCredentials == null) {
            throw new ThingsboardException("Invalid email code!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
        if (userCredentials.getPassword() == null) {
            throw new ThingsboardException("Unable to activate user!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        String encodedPassword = userCredentials.getPassword();
        UserCredentials credentials = userService.activateUserCredentials(TenantId.SYS_TENANT_ID, emailCode, encodedPassword);
        User user = userService.findUserById(TenantId.SYS_TENANT_ID, credentials.getUserId());
        setPrivacyPolicyAccepted(user);
        setTermsOfUseAccepted(user);
        user = userService.saveUser(tenantId, user);
        UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
        SecurityUser securityUser = new SecurityUser(user, credentials.isEnabled(), principal, getMergedUserPermissions(user, false));
        String baseUrl = MiscUtils.constructBaseUrl(request);
        String loginUrl;
        if (!StringUtils.isEmpty(pkgName)) {
            loginUrl = String.format("%s/api/noauth/login?pkgName=%s", baseUrl, pkgName);
        } else {
            loginUrl = String.format("%s/login", baseUrl);
        }
        String email = user.getEmail();

        try {
            mailService.sendAccountActivatedEmail(tenantId, loginUrl, email);
        } catch (Exception e) {
            log.warn("Unable to send account activated email for {}: {}", email, e.getMessage());
        }

        sendUserActivityNotification(tenantId, user.getFirstName() + " " + user.getLastName(), email, true, selfRegistrationParams.getNotificationEmail());

        systemSecurityService.logLoginAction(user, new RestAuthenticationDetails(request), ActionType.LOGIN, null);
        return tokenFactory.createTokenPair(securityUser);
    }

    private void setPrivacyPolicyAccepted(User user) {
        JsonNode additionalInfo = user.getAdditionalInfo();
        if (!(additionalInfo instanceof ObjectNode)) {
            additionalInfo = JacksonUtil.newObjectNode();
        }
        ((ObjectNode) additionalInfo).put(PRIVACY_POLICY_ACCEPTED, true);
        user.setAdditionalInfo(additionalInfo);
    }

    private boolean isPrivacyPolicyAccepted(User user) {
        JsonNode additionalInfo = user.getAdditionalInfo();
        if (additionalInfo != null && additionalInfo.has(PRIVACY_POLICY_ACCEPTED)) {
            return additionalInfo.get(PRIVACY_POLICY_ACCEPTED).asBoolean();
        }
        return false;
    }

    @ApiOperation(value = "Check privacy policy (privacyPolicyAccepted)",
            notes = "Checks that current user accepted the privacy policy.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/signup/privacyPolicyAccepted")
    public @ResponseBody
    Boolean privacyPolicyAccepted() throws ThingsboardException {
        SecurityUser securityUser = getCurrentUser();
        User user = userService.findUserById(securityUser.getTenantId(), securityUser.getId());
        return isPrivacyPolicyAccepted(user);
    }

    @ApiOperation(value = "Accept privacy policy (acceptPrivacyPolicy)",
            notes = "Accept privacy policy by the current user.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping(value = "/signup/acceptPrivacyPolicy")
    public JsonNode acceptPrivacyPolicy() throws ThingsboardException {
        SecurityUser securityUser = getCurrentUser();
        User user = userService.findUserById(securityUser.getTenantId(), securityUser.getId());
        setPrivacyPolicyAccepted(user);
        user = userService.saveUser(securityUser.getTenantId(), user);
        UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
        securityUser = new SecurityUser(user, true, principal, getMergedUserPermissions(user, false));
        JwtPair tokenPair = tokenFactory.createTokenPair(securityUser);

        ObjectNode tokenObject = JacksonUtil.newObjectNode();
        tokenObject.put("token", tokenPair.getToken());
        tokenObject.put("refreshToken", tokenPair.getRefreshToken());
        return tokenObject;
    }

    private void setTermsOfUseAccepted(User user) {
        JsonNode additionalInfo = user.getAdditionalInfo();
        if (additionalInfo == null || !(additionalInfo instanceof ObjectNode)) {
            additionalInfo = JacksonUtil.newObjectNode();
        }
        ((ObjectNode) additionalInfo).put(TERMS_OF_USE_ACCEPTED, true);
        user.setAdditionalInfo(additionalInfo);
    }

    private boolean isTermsOfUseAccepted(User user) {
        JsonNode additionalInfo = user.getAdditionalInfo();
        if (additionalInfo != null && additionalInfo.has(TERMS_OF_USE_ACCEPTED)) {
            return additionalInfo.get(TERMS_OF_USE_ACCEPTED).asBoolean();
        }
        return false;
    }

    @ApiOperation(value = "Check Terms Of User (termsOfUseAccepted)",
            notes = "Checks that current user accepted the privacy policy.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/signup/termsOfUseAccepted")
    public @ResponseBody
    Boolean termsOfUseAccepted() throws ThingsboardException {
        SecurityUser securityUser = getCurrentUser();
        User user = userService.findUserById(securityUser.getTenantId(), securityUser.getId());
        return isTermsOfUseAccepted(user);
    }

    @ApiOperation(value = "Accept Terms of Use (acceptTermsOfUse)",
            notes = "Accept Terms of Use by the current user.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping(value = "/signup/acceptTermsOfUse")
    public JsonNode acceptTermsOfUse() throws ThingsboardException {
        SecurityUser securityUser = getCurrentUser();
        User user = userService.findUserById(securityUser.getTenantId(), securityUser.getId());
        setTermsOfUseAccepted(user);
        user = userService.saveUser(securityUser.getTenantId(), user);
        UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
        securityUser = new SecurityUser(user, true, principal, getMergedUserPermissions(user, false));
        JwtPair tokenPair = tokenFactory.createTokenPair(securityUser);

        ObjectNode tokenObject = JacksonUtil.newObjectNode();
        tokenObject.put("token", tokenPair.getToken());
        tokenObject.put("refreshToken", tokenPair.getRefreshToken());
        return tokenObject;
    }

    private void validateEnterpriseReCaptcha(SignUpRequest signUpRequest, HttpServletRequest request, SelfRegistrationParams selfRegistrationParams)
            throws IOException {
        EnterpriseCaptchaParams captcha = (EnterpriseCaptchaParams) selfRegistrationParams.getCaptcha();
        try (RecaptchaEnterpriseServiceClient client = RecaptchaEnterpriseServiceClient.create(
                RecaptchaEnterpriseServiceSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(
                                ServiceAccountCredentials.fromStream(new ByteArrayInputStream(captcha.getServiceAccountCredentials().getBytes()))))
                        .build())) {
            String userAgent = request.getHeader("User-Agent");
            String siteKey;
            if (userAgent != null && userAgent.contains("Android")) {
                siteKey = captcha.getAndroidKey();
            } else if (userAgent != null && (userAgent.contains("iPhone") || userAgent.contains("iPad"))) {
                siteKey = captcha.getIosKey();
            } else {
                throw new DataValidationException("Error validating reCAPTCHA: platform could not be detected");
            }
            Event event = Event.newBuilder()
                    .setSiteKey(siteKey)
                    .setToken(signUpRequest.getRecaptchaResponse())
                    .setUserIpAddress(request.getRemoteAddr())
                    .setUserAgent(userAgent)
                    .build();

            CreateAssessmentRequest createAssessmentRequest =
                    CreateAssessmentRequest.newBuilder()
                            .setParent(ProjectName.of(captcha.getProjectId()).toString())
                            .setAssessment(Assessment.newBuilder().setEvent(event).build())
                            .build();

            Assessment response = client.createAssessment(createAssessmentRequest);

            if (!response.getTokenProperties().getValid()) {
                throw new DataValidationException("Invalid reCaptcha response!");
            }

            if (!response.getTokenProperties().getAction().equals(captcha.getLogActionName())) {
                throw new DataValidationException("Wrong recaptcha version!");
            }

            float recaptchaScore = response.getRiskAnalysis().getScore();
            if (recaptchaScore < 0.95) {
                throw new DataValidationException("The reCAPTCHA score is low: " + recaptchaScore);
            }
        }
    }

    private void validateReCaptcha(String userResponse, String ipAddress, String recaptchaSecretKey) throws ThingsboardException {
        checkParameter("Recaptcha response", userResponse);
        MultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
        parameters.add("secret", recaptchaSecretKey);
        parameters.add("response", userResponse);
        parameters.add("remoteip", ipAddress);

        log.debug("Validating reCAPTCHA:\n    verification url: {}\n    verification parameters: {}", signUpConfig.getRecaptchaVerificationUrl(), parameters);

        try {
            RecaptchaValidationResult result = restTemplate.postForEntity(signUpConfig.getRecaptchaVerificationUrl(), parameters, RecaptchaValidationResult.class).getBody();
            log.debug("reCAPTCHA validation finished: {}", result);
            if (result.isFailure()) {
                log.error("reCAPTCHA validation failed: {}", result);
                throw new DataValidationException("Invalid reCaptcha response!");
            }
        } catch (RestClientException ex) {
            log.error("Error validating reCAPTCHA. User response: [{}], verification URL: [{}]", userResponse, signUpConfig.getRecaptchaVerificationUrl());
            throw new ThingsboardException("Error validating reCAPTCHA", ex, ThingsboardErrorCode.GENERAL);
        }
    }

    private void validateRequiredFields(SignUpRequest signUpRequest, SelfRegistrationParams selfRegistrationParams) throws ThingsboardException {
        Map<SignUpFieldId, String> signUpRequestFields = signUpRequest.getFields();
        List<SignUpField> selfRegisterSignUpFields = selfRegistrationParams.getSignUpFields();
        if (selfRegisterSignUpFields != null) {
            for (SignUpField field : selfRegisterSignUpFields) {
                if (field.isRequired()) {
                    checkNotNull(signUpRequestFields.get(field.getId()));
                }
            }
        } else {
            checkNotNull(signUpRequestFields.get(EMAIL));
            checkNotNull(signUpRequestFields.get(PASSWORD));
       }
    }

    private EntityGroup createCustomerUserGroup(SelfRegistrationParams selfRegistrationParams, Customer savedCustomer) {
        TenantId tenantId = savedCustomer.getTenantId();
        EntityGroup usersEntityGroup = entityGroupService.findOrCreateUserGroup(tenantId, savedCustomer.getId(), "Self Registration Users",
                "Autogenerated Self Registration group");

        List<GroupPermission> permissions = selfRegistrationParams.getPermissions();
        for (GroupPermission permission : permissions) {
            permission.setTenantId(tenantId);
            permission.setUserGroupId(usersEntityGroup.getId());
            if (permission.getEntityGroupId() != null) {
                EntityGroup entityGroup = entityGroupService.findEntityGroupById(tenantId, permission.getEntityGroupId());
                if (entityGroup != null) {
                    permission.setEntityGroupType(entityGroup.getType());
                    groupPermissionService.saveGroupPermission(tenantId, permission);
                }
            } else {
                groupPermissionService.saveGroupPermission(tenantId, permission);
            }
        }
        return usersEntityGroup;
    }

    private User createUser(SignUpRequest signUpRequest, SelfRegistrationParams selfRegistrationParams, Customer savedCustomer) throws ThingsboardException {
        User user = new User();
        Map<SignUpFieldId, String> signUpRequestFields = signUpRequest.getFields();
        user.setFirstName(signUpRequestFields.get(FIRST_NAME));
        user.setLastName(signUpRequestFields.get(LAST_NAME));
        user.setEmail(signUpRequestFields.get(EMAIL));
        user.setPhone(signUpRequestFields.get(PHONE));
        user.setAuthority(Authority.CUSTOMER_USER);
        user.setTenantId(savedCustomer.getTenantId());
        user.setCustomerId(savedCustomer.getId());
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        objectNode.put("lang", "en_US");

        Optional.ofNullable(selfRegistrationParams.getDefaultDashboard())
                .ifPresent(dashboard -> {
                    objectNode.put("defaultDashboardId", dashboard.getId());
                    objectNode.put("defaultDashboardFullscreen", dashboard.isFullscreen());
                });
        user.setAdditionalInfo(objectNode);

        return checkNotNull(userService.saveUser(savedCustomer.getTenantId(), user));
    }

    private Customer createCustomer(SignUpRequest signUpRequest, SelfRegistrationParams selfRegistrationParams, TenantId tenantId) throws ThingsboardException {
        Customer customer = new Customer();

        customer.setTenantId(tenantId);
        String customerTitlePrefix = Optional.ofNullable(selfRegistrationParams.getCustomerTitlePrefix()).orElse("");
        Map<SignUpFieldId, String> signUpRequestFields = signUpRequest.getFields();
        customer.setTitle(customerTitlePrefix + signUpRequestFields.get(EMAIL));
        customer.setOwnerId(tenantId);
        customer.setEmail(signUpRequestFields.get(EMAIL));
        customer.setCountry(signUpRequestFields.get(COUNTRY));
        customer.setState(signUpRequestFields.get(STATE));
        customer.setCity(signUpRequestFields.get(CITY));
        customer.setAddress(signUpRequestFields.get(ADDRESS));
        customer.setAddress2(signUpRequestFields.get(ADDRESS2));
        customer.setZip(signUpRequestFields.get(ZIP));
        customer.setPhone(signUpRequestFields.get(PHONE));
        customer.setCustomMenuId(selfRegistrationParams.getCustomMenuId());

        Customer savedCustomer = checkNotNull(customerService.saveCustomer(customer));
        if (selfRegistrationParams.getCustomerGroupId() != null) {
            entityGroupService.addEntityToEntityGroup(tenantId, selfRegistrationParams.getCustomerGroupId(), savedCustomer.getId());
        }
        return savedCustomer;
    }

    private UserCredentials saveUserCredentials(User savedUser, SignUpRequest signUpRequest) {
        UserCredentials userCredentials = userService.findUserCredentialsByUserId(savedUser.getTenantId(), savedUser.getId());
        userCredentials.setPassword(passwordEncoder.encode(signUpRequest.getFields().get(PASSWORD)));

        userService.saveUserCredentials(savedUser.getTenantId(), userCredentials);
        return userCredentials;
    }

    private void validateAppSecret(SignUpRequest signUpRequest) {
        MobileApp mobileApp = mobileAppService.findMobileAppByPkgNameAndPlatformType(signUpRequest.getPkgName(), signUpRequest.getPlatform());
        if (StringUtils.isEmpty(signUpRequest.getAppSecret()) || !signUpRequest.getAppSecret().equals(mobileApp.getAppSecret())) {
            throw new DataValidationException(INVALID_APP_SECRET);
        }
    }

    private MobileAppBundle checkMobileSRSettings(String pkgName, PlatformType platform) throws ThingsboardException {
        MobileAppBundle mobileAppBundle = mobileAppBundleService.findMobileAppBundleByPkgNameAndPlatform(TenantId.SYS_TENANT_ID, pkgName, platform, false);
        checkNotNull(mobileAppBundle, MOBILE_APP_BUNDLE_WAS_NOT_FOUND);
        checkNotNull(mobileAppBundle.getSelfRegistrationParams(), SELF_REGISTRATION_SETTINGS_WAS_NOT_FOUND);
        return mobileAppBundle;
    }

    private WhiteLabeling checkWebSRSettings(String domain) throws ThingsboardException {
        WhiteLabeling whiteLabeling = whiteLabelingService.findWhiteLabelingByDomainAndType(domain, SELF_REGISTRATION);
        checkNotNull(whiteLabeling, WL_SETTINGS_WAS_NOT_FOUND);
        checkNotNull(whiteLabeling.getSettings(), SELF_REGISTRATION_SETTINGS_WAS_NOT_FOUND);
        return whiteLabeling;
    }

    private void validateSelfRegistrationAllowed(SelfRegistrationParams selfRegistrationParams) {
        if (selfRegistrationParams.getEnabled() != null && !selfRegistrationParams.getEnabled()) {
            throw new DataValidationException(SELF_REGISTRATION_SETTINGS_IS_NOT_ALLOWED);
        }
    }

}
