/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientRegistrationInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2CustomMapperConfig;
import org.thingsboard.server.common.data.oauth2.OAuth2MapperConfig;
import org.thingsboard.server.dao.oauth2.OAuth2User;
import org.thingsboard.server.service.security.model.SecurityUser;

@Service(value = "customOAuth2ClientMapper")
@Slf4j
public class CustomOAuth2ClientMapper extends AbstractOAuth2ClientMapper implements OAuth2ClientMapper {
    private static final String PROVIDER_ACCESS_TOKEN = "provider-access-token";

    private static final ObjectMapper json = new ObjectMapper();

    private RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder();

    @Override
    public SecurityUser getOrCreateUserByClientPrincipal(OAuth2AuthenticationToken token, String providerAccessToken, OAuth2ClientRegistrationInfo clientRegistration) {
        OAuth2MapperConfig config = clientRegistration.getMapperConfig();
        OAuth2User oauth2User = getOAuth2User(token, providerAccessToken, config.getCustom());
        return getOrCreateSecurityUserFromOAuth2User(oauth2User, clientRegistration);
    }

    private synchronized OAuth2User getOAuth2User(OAuth2AuthenticationToken token, String providerAccessToken, OAuth2CustomMapperConfig custom) {
        if (!StringUtils.isEmpty(custom.getUsername()) && !StringUtils.isEmpty(custom.getPassword())) {
            restTemplateBuilder = restTemplateBuilder.basicAuthentication(custom.getUsername(), custom.getPassword());
        }
        if (custom.isSendToken() && !StringUtils.isEmpty(providerAccessToken)) {
            restTemplateBuilder = restTemplateBuilder.defaultHeader(PROVIDER_ACCESS_TOKEN, providerAccessToken);
        }

        RestTemplate restTemplate = restTemplateBuilder.build();
        String request;
        try {
            request = json.writeValueAsString(token.getPrincipal());
        } catch (JsonProcessingException e) {
            log.error("Can't convert principal to JSON string", e);
            throw new RuntimeException("Can't convert principal to JSON string", e);
        }
        try {
            return restTemplate.postForEntity(custom.getUrl(), request, OAuth2User.class).getBody();
        } catch (Exception e) {
            log.error("There was an error during connection to custom mapper endpoint", e);
            throw new RuntimeException("Unable to login. Please contact your Administrator!");
        }
    }
}
