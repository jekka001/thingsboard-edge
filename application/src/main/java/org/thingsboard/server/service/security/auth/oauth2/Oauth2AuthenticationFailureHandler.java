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
package org.thingsboard.server.service.security.auth.oauth2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.service.security.system.SystemSecurityService;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component(value = "oauth2AuthenticationFailureHandler")
@ConditionalOnProperty(prefix = "security.oauth2", value = "enabled", havingValue = "true")
public class Oauth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final SystemSecurityService systemSecurityService;

    @Autowired
    public Oauth2AuthenticationFailureHandler(final SystemSecurityService systemSecurityService) {
        this.systemSecurityService = systemSecurityService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        String baseUrl = this.systemSecurityService.getBaseUrl(Authority.SYS_ADMIN, TenantId.SYS_TENANT_ID, new CustomerId(EntityId.NULL_UUID), request);
        getRedirectStrategy().sendRedirect(request, response, baseUrl + "/login?loginError=" +
                URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8.toString()));
    }
}
