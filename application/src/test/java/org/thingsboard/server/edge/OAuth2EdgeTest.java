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
package org.thingsboard.server.edge;

import com.google.protobuf.AbstractMessage;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.oauth2.MapperType;
import org.thingsboard.server.common.data.oauth2.OAuth2CustomMapperConfig;
import org.thingsboard.server.common.data.oauth2.OAuth2MapperConfig;
import org.thingsboard.server.common.data.oauth2.OAuth2Client;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.OAuth2UpdateMsg;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

@DaoSqlTest
@Ignore("Ignored till fixed")
public class OAuth2EdgeTest extends AbstractEdgeTest {

    @Test
    public void testOAuth2Support() throws Exception {
        loginSysAdmin();

        // enable oauth
        edgeImitator.allowIgnoredTypes();
        edgeImitator.expectMessageAmount(1);
        OAuth2Client oAuth2Client = createDefaultOAuth2Info();
        oAuth2Client = doPost("/api/oauth2/client", oAuth2Client, OAuth2Client.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof OAuth2UpdateMsg);
        OAuth2UpdateMsg oAuth2ProviderUpdateMsg = (OAuth2UpdateMsg) latestMessage;
        OAuth2Client result = JacksonUtil.fromString(oAuth2ProviderUpdateMsg.getEntity(), OAuth2Client.class, true);
        Assert.assertEquals(oAuth2Client, result);

        // disable oauth support
        edgeImitator.expectMessageAmount(1);
//        oAuth2Info.setEnabled(false);
//        oAuth2Info.setEdgeEnabled(false);
//        doPost("/api/oauth2/config", oAuth2Info, OAuth2Info.class);
//        Assert.assertFalse(edgeImitator.waitForMessages(5));

        edgeImitator.ignoreType(OAuth2UpdateMsg.class);
        loginTenantAdmin();
    }

    private OAuth2Client createDefaultOAuth2Info() {
        return validRegistrationInfo();
    }

    private OAuth2Client validRegistrationInfo() {
        OAuth2Client oAuth2Client = new OAuth2Client();
        oAuth2Client.setTitle(UUID.randomUUID().toString());
        oAuth2Client.setClientId(UUID.randomUUID().toString());
        oAuth2Client.setClientSecret(UUID.randomUUID().toString());
        oAuth2Client.setAuthorizationUri(UUID.randomUUID().toString());
        oAuth2Client.setAccessTokenUri(UUID.randomUUID().toString());
        oAuth2Client.setScope(Arrays.asList(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        oAuth2Client.setPlatforms(Collections.emptyList());
        oAuth2Client.setUserInfoUri(UUID.randomUUID().toString());
        oAuth2Client.setUserNameAttributeName(UUID.randomUUID().toString());
        oAuth2Client.setJwkSetUri(UUID.randomUUID().toString());
        oAuth2Client.setClientAuthenticationMethod(UUID.randomUUID().toString());
        oAuth2Client.setLoginButtonLabel(UUID.randomUUID().toString());
        oAuth2Client.setMapperConfig(
                        OAuth2MapperConfig.builder()
                                .type(MapperType.CUSTOM)
                                .custom(
                                        OAuth2CustomMapperConfig.builder()
                                                .url(UUID.randomUUID().toString())
                                                .build()
                                )
                                .build());
        return oAuth2Client;
    }

}
