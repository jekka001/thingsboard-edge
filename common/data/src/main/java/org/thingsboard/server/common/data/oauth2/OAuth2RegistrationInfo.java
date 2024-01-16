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
package org.thingsboard.server.common.data.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@EqualsAndHashCode
@Data
@ToString(exclude = {"clientSecret"})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema
public class OAuth2RegistrationInfo {
    @Schema(description = "Config for mapping OAuth2 log in response to platform entities", required = true)
    private OAuth2MapperConfig mapperConfig;
    @Schema(description = "OAuth2 client ID. Cannot be empty", required = true)
    private String clientId;
    @Schema(description = "OAuth2 client secret. Cannot be empty", required = true)
    private String clientSecret;
    @Schema(description = "Authorization URI of the OAuth2 provider. Cannot be empty", required = true)
    private String authorizationUri;
    @Schema(description = "Access token URI of the OAuth2 provider. Cannot be empty", required = true)
    private String accessTokenUri;
    @Schema(description = "OAuth scopes that will be requested from OAuth2 platform. Cannot be empty", required = true)
    private List<String> scope;
    @Schema(description = "User info URI of the OAuth2 provider")
    private String userInfoUri;
    @Schema(description = "Name of the username attribute in OAuth2 provider response. Cannot be empty")
    private String userNameAttributeName;
    @Schema(description = "JSON Web Key URI of the OAuth2 provider")
    private String jwkSetUri;
    @Schema(description = "Client authentication method to use: 'BASIC' or 'POST'. Cannot be empty", required = true)
    private String clientAuthenticationMethod;
    @Schema(description = "OAuth2 provider label. Cannot be empty", required = true)
    private String loginButtonLabel;
    @Schema(description = "Log in button icon for OAuth2 provider")
    private String loginButtonIcon;
    @Schema(description = "List of platforms for which usage of the OAuth2 client is allowed (empty for all allowed)")
    private List<PlatformType> platforms;
    @Schema(description = "Additional info of OAuth2 client (e.g. providerName)", required = true)
    private JsonNode additionalInfo;
}
