/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.security.auth;

import io.jsonwebtoken.Claims;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.NotificationCenter;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.targets.platform.SystemAdministratorsFilter;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.model.JwtSettings;
import org.thingsboard.server.common.data.security.model.JwtToken;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.service.security.auth.jwt.settings.DefaultJwtSettingsService;
import org.thingsboard.server.service.security.auth.jwt.settings.DefaultJwtSettingsValidator;
import org.thingsboard.server.service.security.auth.jwt.settings.JwtSettingsService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.security.model.token.AccessJwtToken;
import org.thingsboard.server.service.security.model.token.JwtTokenFactory;
import org.thingsboard.server.service.security.model.token.RawAccessJwtToken;
import org.thingsboard.server.service.security.permission.UserPermissionsService;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JwtTokenFactoryTest {

    private JwtTokenFactory tokenFactory;
    private AdminSettingsService adminSettingsService;
    private NotificationCenter notificationCenter;
    private JwtSettingsService jwtSettingsService;

    private JwtSettings jwtSettings;

    @Before
    public void beforeEach() {
        jwtSettings = new JwtSettings();
        jwtSettings.setTokenIssuer("tb");
        jwtSettings.setTokenSigningKey("abewafaf");
        jwtSettings.setTokenExpirationTime((int) TimeUnit.HOURS.toSeconds(2));
        jwtSettings.setRefreshTokenExpTime((int) TimeUnit.DAYS.toSeconds(7));

        adminSettingsService = mock(AdminSettingsService.class);
        notificationCenter = mock(NotificationCenter.class);
        jwtSettingsService = mockJwtSettingsService();
        mockJwtSettings(jwtSettings);

        tokenFactory = new JwtTokenFactory(jwtSettingsService, Mockito.mock(UserPermissionsService.class));
    }

    @Test
    public void testCreateAndParseAccessJwtToken() {
        SecurityUser securityUser = new SecurityUser();
        securityUser.setId(new UserId(UUID.randomUUID()));
        securityUser.setEmail("tenant@thingsboard.org");
        securityUser.setAuthority(Authority.TENANT_ADMIN);
        securityUser.setTenantId(new TenantId(UUID.randomUUID()));
        securityUser.setEnabled(true);
        securityUser.setFirstName("A");
        securityUser.setLastName("B");
        securityUser.setUserPrincipal(new UserPrincipal(UserPrincipal.Type.USER_NAME, securityUser.getEmail()));
        securityUser.setCustomerId(new CustomerId(UUID.randomUUID()));

        testCreateAndParseAccessJwtToken(securityUser);

        securityUser = new SecurityUser(securityUser, true, new UserPrincipal(UserPrincipal.Type.PUBLIC_ID, securityUser.getEmail()), null);
        securityUser.setFirstName(null);
        securityUser.setLastName(null);
        securityUser.setCustomerId(null);

        testCreateAndParseAccessJwtToken(securityUser);
    }

    public void testCreateAndParseAccessJwtToken(SecurityUser securityUser) {
        AccessJwtToken accessToken = tokenFactory.createAccessJwtToken(securityUser);
        checkExpirationTime(accessToken, jwtSettings.getTokenExpirationTime());

        SecurityUser parsedSecurityUser = tokenFactory.parseAccessJwtToken(new RawAccessJwtToken(accessToken.getToken()));
        assertThat(parsedSecurityUser.getId()).isEqualTo(securityUser.getId());
        assertThat(parsedSecurityUser.getEmail()).isEqualTo(securityUser.getEmail());
        assertThat(parsedSecurityUser.getUserPrincipal()).matches(userPrincipal -> {
            return userPrincipal.getType().equals(securityUser.getUserPrincipal().getType())
                    && userPrincipal.getValue().equals(securityUser.getUserPrincipal().getValue());
        });
        assertThat(parsedSecurityUser.getAuthorities()).isEqualTo(securityUser.getAuthorities());
        assertThat(parsedSecurityUser.isEnabled()).isEqualTo(securityUser.isEnabled());
        assertThat(parsedSecurityUser.getTenantId()).isEqualTo(securityUser.getTenantId());
        assertThat(parsedSecurityUser.getCustomerId()).isEqualTo(securityUser.getCustomerId());
        assertThat(parsedSecurityUser.getFirstName()).isEqualTo(securityUser.getFirstName());
        assertThat(parsedSecurityUser.getLastName()).isEqualTo(securityUser.getLastName());
    }

    @Test
    public void testCreateAndParseRefreshJwtToken() {
        SecurityUser securityUser = new SecurityUser();
        securityUser.setId(new UserId(UUID.randomUUID()));
        securityUser.setEmail("tenant@thingsboard.org");
        securityUser.setAuthority(Authority.TENANT_ADMIN);
        securityUser.setUserPrincipal(new UserPrincipal(UserPrincipal.Type.USER_NAME, securityUser.getEmail()));
        securityUser.setEnabled(true);
        securityUser.setTenantId(new TenantId(UUID.randomUUID()));
        securityUser.setCustomerId(new CustomerId(UUID.randomUUID()));

        JwtToken refreshToken = tokenFactory.createRefreshToken(securityUser);
        checkExpirationTime(refreshToken, jwtSettings.getRefreshTokenExpTime());

        SecurityUser parsedSecurityUser = tokenFactory.parseRefreshToken(new RawAccessJwtToken(refreshToken.getToken()));
        assertThat(parsedSecurityUser.getId()).isEqualTo(securityUser.getId());
        assertThat(parsedSecurityUser.getUserPrincipal()).matches(userPrincipal -> {
            return userPrincipal.getType().equals(securityUser.getUserPrincipal().getType())
                    && userPrincipal.getValue().equals(securityUser.getUserPrincipal().getValue());
        });
        assertThat(parsedSecurityUser.getAuthority()).isNull();
    }

    @Test
    public void testCreateAndParsePreVerificationJwtToken() {
        SecurityUser securityUser = new SecurityUser();
        securityUser.setId(new UserId(UUID.randomUUID()));
        securityUser.setEmail("tenant@thingsboard.org");
        securityUser.setAuthority(Authority.TENANT_ADMIN);
        securityUser.setUserPrincipal(new UserPrincipal(UserPrincipal.Type.USER_NAME, securityUser.getEmail()));
        securityUser.setEnabled(true);
        securityUser.setTenantId(new TenantId(UUID.randomUUID()));
        securityUser.setCustomerId(new CustomerId(UUID.randomUUID()));

        int tokenLifetime = (int) TimeUnit.MINUTES.toSeconds(30);
        JwtToken preVerificationToken = tokenFactory.createPreVerificationToken(securityUser, tokenLifetime);
        checkExpirationTime(preVerificationToken, tokenLifetime);

        SecurityUser parsedSecurityUser = tokenFactory.parseAccessJwtToken(new RawAccessJwtToken(preVerificationToken.getToken()));
        assertThat(parsedSecurityUser.getId()).isEqualTo(securityUser.getId());
        assertThat(parsedSecurityUser.getAuthority()).isEqualTo(Authority.PRE_VERIFICATION_TOKEN);
        assertThat(parsedSecurityUser.getTenantId()).isEqualTo(securityUser.getTenantId());
        assertThat(parsedSecurityUser.getCustomerId()).isEqualTo(securityUser.getCustomerId());
        assertThat(parsedSecurityUser.getUserPrincipal()).matches(userPrincipal -> {
            return userPrincipal.getType() == UserPrincipal.Type.USER_NAME
                    && userPrincipal.getValue().equals(securityUser.getUserPrincipal().getValue());
        });
    }

    @Test
    public void testJwtSigningKeyIssueNotification() {
        JwtSettings badJwtSettings = jwtSettings;
        badJwtSettings.setTokenSigningKey(JwtSettingsService.TOKEN_SIGNING_KEY_DEFAULT);
        mockJwtSettings(badJwtSettings);
        jwtSettingsService = mockJwtSettingsService();

        for (int i = 0; i < 5; i++) { // to check if notification is not sent twice
            jwtSettingsService.getJwtSettings();
        }
        verify(notificationCenter, times(1)).sendGeneralWebNotification(eq(TenantId.SYS_TENANT_ID),
                isA(SystemAdministratorsFilter.class), argThat(template -> template.getConfiguration().getDeliveryMethodsTemplates().get(NotificationDeliveryMethod.WEB)
                        .getBody().contains("The platform is configured to use default JWT Signing Key")));
    }

    private void mockJwtSettings(JwtSettings settings) {
        AdminSettings adminJwtSettings = new AdminSettings();
        adminJwtSettings.setJsonValue(JacksonUtil.valueToTree(settings));
        when(adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, JwtSettingsService.ADMIN_SETTINGS_JWT_KEY))
                .thenReturn(adminJwtSettings);
    }

    private DefaultJwtSettingsService mockJwtSettingsService() {
        return new DefaultJwtSettingsService(adminSettingsService, Optional.empty(),
                Optional.of(notificationCenter), new DefaultJwtSettingsValidator());
    }

    private void checkExpirationTime(JwtToken jwtToken, int tokenLifetime) {
        Claims claims = tokenFactory.parseTokenClaims(jwtToken).getBody();
        assertThat(claims.getExpiration()).matches(actualExpirationTime -> {
            Calendar expirationTime = Calendar.getInstance();
            expirationTime.setTime(new Date());
            expirationTime.add(Calendar.SECOND, tokenLifetime);
            if (actualExpirationTime.equals(expirationTime.getTime())) {
                return true;
            } else if (actualExpirationTime.before(expirationTime.getTime())) {
                int gap = 2;
                expirationTime.add(Calendar.SECOND, -gap);
                return actualExpirationTime.after(expirationTime.getTime());
            } else {
                return false;
            }
        });
    }

}
