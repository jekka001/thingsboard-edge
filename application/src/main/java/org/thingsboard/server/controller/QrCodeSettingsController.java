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
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.mobile.app.MobileApp;
import org.thingsboard.server.common.data.mobile.qrCodeSettings.QrCodeSettings;
import org.thingsboard.server.common.data.mobile.app.StoreInfo;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.security.model.JwtPair;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.mobile.QrCodeSettingService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.mobile.secret.MobileAppSecretService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.system.SystemSecurityService;

import java.net.URI;
import java.net.URISyntaxException;

import static org.thingsboard.server.common.data.oauth2.PlatformType.ANDROID;
import static org.thingsboard.server.common.data.oauth2.PlatformType.IOS;
import static org.thingsboard.server.common.data.wl.WhiteLabelingType.LOGIN;
import static org.thingsboard.server.controller.ControllerConstants.AVAILABLE_FOR_ANY_AUTHORIZED_USER;
import static org.thingsboard.server.controller.ControllerConstants.SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH;

@RequiredArgsConstructor
@RestController
@TbCoreComponent
@Slf4j
public class QrCodeSettingsController extends BaseController {

    @Value("${cache.specs.mobileSecretKey.timeToLiveInMinutes:2}")
    private int mobileSecretKeyTtl;
    @Value("${mobileApp.domain:thingsboard.cloud}")
    private String defaultAppDomain;

    public static final String ASSET_LINKS_PATTERN = "[{\n" +
            "  \"relation\": [\"delegate_permission/common.handle_all_urls\"],\n" +
            "  \"target\": {\n" +
            "    \"namespace\": \"android_app\",\n" +
            "    \"package_name\": \"%s\",\n" +
            "    \"sha256_cert_fingerprints\":\n" +
            "    [\"%s\"]\n" +
            "  }\n" +
            "}]";

    public static final String APPLE_APP_SITE_ASSOCIATION_PATTERN = "{\n" +
            "    \"applinks\": {\n" +
            "        \"apps\": [],\n" +
            "        \"details\": [\n" +
            "            {\n" +
            "                \"appID\": \"%s\",\n" +
            "                \"paths\": [ \"/api/noauth/qr\" ]\n" +
            "            }\n" +
            "        ]\n" +
            "    }\n" +
            "}";

    public static final String SECRET = "secret";
    public static final String SECRET_PARAM_DESCRIPTION = "A string value representing short-lived secret key";
    public static final String DEEP_LINK_PATTERN = "https://%s/api/noauth/qr?secret=%s&ttl=%s";

    private final SystemSecurityService systemSecurityService;
    private final MobileAppSecretService mobileAppSecretService;
    private final QrCodeSettingService qrCodeSettingService;
    private final WhiteLabelingService whiteLabelingService;

    @ApiOperation(value = "Get associated android applications (getAssetLinks)")
    @GetMapping(value = "/.well-known/assetlinks.json")
    public ResponseEntity<JsonNode> getAssetLinks(HttpServletRequest request) {
        String domainName = request.getServerName();
        WhiteLabeling loginWL = whiteLabelingService.findWhiteLabelingByDomainAndType(domainName, LOGIN);
        MobileApp mobileApp = qrCodeSettingService.findAppFromQrCodeSettings(loginWL != null ? loginWL.getTenantId() : TenantId.SYS_TENANT_ID, ANDROID);
        StoreInfo storeInfo = mobileApp != null ? mobileApp.getStoreInfo() : null;
        if (storeInfo != null && storeInfo.getSha256CertFingerprints() != null) {
            return ResponseEntity.ok(JacksonUtil.toJsonNode(String.format(ASSET_LINKS_PATTERN, mobileApp.getPkgName(), storeInfo.getSha256CertFingerprints())));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @ApiOperation(value = "Get associated ios applications (getAppleAppSiteAssociation)")
    @GetMapping(value = "/.well-known/apple-app-site-association")
    public ResponseEntity<JsonNode> getAppleAppSiteAssociation(HttpServletRequest request) {
        String domainName = request.getServerName();
        WhiteLabeling loginWL = whiteLabelingService.findWhiteLabelingByDomainAndType(domainName, LOGIN);
        MobileApp mobileApp = qrCodeSettingService.findAppFromQrCodeSettings(loginWL != null ? loginWL.getTenantId() : TenantId.SYS_TENANT_ID, IOS);
        StoreInfo storeInfo = mobileApp != null ? mobileApp.getStoreInfo() : null;
        if (storeInfo != null && storeInfo.getAppId() != null) {
            return ResponseEntity.ok(JacksonUtil.toJsonNode(String.format(APPLE_APP_SITE_ASSOCIATION_PATTERN, storeInfo.getAppId())));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @ApiOperation(value = "Create Or Update the Mobile application settings (saveMobileAppSettings)",
            notes = "The request payload contains configuration for android/iOS applications and platform qr code widget settings." + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @PostMapping(value = "/api/mobile/qr/settings")
    public QrCodeSettings saveQrCodeSettings(@Parameter(description = "A JSON value representing the mobile apps configuration")
                                             @RequestBody QrCodeSettings qrCodeSettings) throws ThingsboardException {
        SecurityUser currentUser = getCurrentUser();
        accessControlService.checkPermission(currentUser, Resource.MOBILE_APP_SETTINGS, Operation.WRITE);
        qrCodeSettings.setTenantId(getTenantId());
        if (qrCodeSettings.getMobileAppBundleId() != null) {
            checkEntityId(qrCodeSettings.getMobileAppBundleId(), Operation.READ);
        }
        return qrCodeSettingService.saveQrCodeSettings(currentUser.getTenantId(), qrCodeSettings);
    }

    @ApiOperation(value = "Get Mobile application settings (getMobileAppSettings)",
            notes = "The response payload contains configuration for android/iOS applications and platform qr code widget settings." + AVAILABLE_FOR_ANY_AUTHORIZED_USER)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/api/mobile/qr/settings")
    public QrCodeSettings getQrCodeSettings() throws ThingsboardException {
        SecurityUser currentUser = getCurrentUser();
        accessControlService.checkPermission(currentUser, Resource.MOBILE_APP_SETTINGS, Operation.READ);
        return qrCodeSettingService.findQrCodeSettings(currentUser.getTenantId());
    }

    @ApiOperation(value = "Get QR code configuration for home page (getMobileAppQrCodeConfig)",
            notes = "The response payload contains ui configuration of qr code" + AVAILABLE_FOR_ANY_AUTHORIZED_USER)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/api/mobile/qr/merged")
    public QrCodeSettings getMergedMobileAppSettings() throws ThingsboardException {
        SecurityUser currentUser = getCurrentUser();
        accessControlService.checkPermission(getCurrentUser(), Resource.MOBILE_APP_SETTINGS, Operation.READ);
        return qrCodeSettingService.getMergedQrCodeSettings(currentUser.getTenantId());
    }

    @ApiOperation(value = "Get the deep link to the associated mobile application (getMobileAppDeepLink)",
            notes = "Fetch the url that takes user to linked mobile application " + AVAILABLE_FOR_ANY_AUTHORIZED_USER)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/api/mobile/qr/deepLink", produces = "text/plain")
    public String getMobileAppDeepLink(HttpServletRequest request) throws ThingsboardException, URISyntaxException {
        SecurityUser currentUser = getCurrentUser();
        String secret = mobileAppSecretService.generateMobileAppSecret(getCurrentUser());
        String baseUrl = systemSecurityService.getBaseUrl(currentUser.getAuthority(), currentUser.getTenantId(), currentUser.getCustomerId(), request);
        String platformDomain;
        try {
            platformDomain = new URI(baseUrl).getHost();
        } catch (URISyntaxException e) {
            log.debug("Failed to get host from base url: {}", baseUrl, e);
            platformDomain = defaultAppDomain;
        }
        QrCodeSettings qrCodeSettings = qrCodeSettingService.getMergedQrCodeSettings(currentUser.getTenantId());
        String appDomain = qrCodeSettings.isUseDefaultApp() ? defaultAppDomain : platformDomain;
        String deepLink = String.format(DEEP_LINK_PATTERN, appDomain, secret, mobileSecretKeyTtl);
        if (!appDomain.equals(platformDomain)) {
            deepLink = deepLink + "&host=" + baseUrl;
        }
        return "\"" + deepLink + "\"";
    }

    @ApiOperation(value = "Get User Token (getUserTokenByMobileSecret)",
            notes = "Returns the token of the User based on the provided secret key.")
    @GetMapping(value = "/api/noauth/qr/{secret}")
    public JwtPair getUserTokenByMobileSecret(@Parameter(description = SECRET_PARAM_DESCRIPTION)
                                              @PathVariable(SECRET) String secret) throws ThingsboardException {
        checkParameter(SECRET, secret);
        return mobileAppSecretService.getJwtPair(secret);
    }

    @GetMapping(value = "/api/noauth/qr")
    public ResponseEntity<?> getApplicationRedirect(@RequestHeader(value = "User-Agent") String userAgent, HttpServletRequest request) {
        WhiteLabeling loginWL = whiteLabelingService.findWhiteLabelingByDomainAndType(request.getServerName(), LOGIN);
        QrCodeSettings qrCodeSettings;
        if (loginWL != null) {
            qrCodeSettings = qrCodeSettingService.getMergedQrCodeSettings(loginWL.getTenantId());
        } else {
            qrCodeSettings = qrCodeSettingService.findQrCodeSettings(TenantId.SYS_TENANT_ID);
        }
        if (userAgent.contains("Android")) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", qrCodeSettings.getGooglePlayLink())
                    .build();
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad") && qrCodeSettings.isIosEnabled()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", qrCodeSettings.getAppStoreLink())
                    .build();
        } else {
            return response(HttpStatus.NOT_FOUND);
        }
    }

}
