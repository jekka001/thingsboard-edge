/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.msa.connectivity;

import com.fasterxml.jackson.databind.JsonNode;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.msa.AbstractContainerTest;
import org.thingsboard.server.msa.WsClient;
import org.thingsboard.server.msa.mapper.WsTelemetryResponse;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.thingsboard.server.common.data.DataConstants.DEVICE;
import static org.thingsboard.server.common.data.DataConstants.SHARED_SCOPE;
import static org.thingsboard.server.msa.prototypes.DevicePrototypes.defaultDevicePrototype;

public class HttpClientTest extends AbstractContainerTest {
    private Device device;
    @BeforeMethod
    public void setUp() throws Exception {
        testRestClient.login("tenant@thingsboard.org", "tenant");
        device = testRestClient.postDevice("", defaultDevicePrototype("http_"));
    }

    @AfterMethod
    public void tearDown() {
        testRestClient.deleteDeviceIfExists(device.getId());
    }

    @Test
    public void telemetryUpload() throws Exception {
        DeviceCredentials deviceCredentials = testRestClient.getDeviceCredentialsByDeviceId(device.getId());

        WsClient wsClient = subscribeToWebSocket(device.getId(), "LATEST_TELEMETRY", CmdsType.TS_SUB_CMDS);
        testRestClient.postTelemetry(deviceCredentials.getCredentialsId(), mapper.readTree(createPayload().toString()));

        WsTelemetryResponse actualLatestTelemetry = wsClient.getLastMessage();
        wsClient.closeBlocking();

        assertThat(actualLatestTelemetry.getLatestValues().keySet()).containsOnlyOnceElementsOf(Arrays.asList("booleanKey", "stringKey", "doubleKey", "longKey"));

        assertThat(actualLatestTelemetry.getDataValuesByKey("booleanKey").get(1)).isEqualTo(Boolean.TRUE.toString());
        assertThat(actualLatestTelemetry.getDataValuesByKey("stringKey").get(1)).isEqualTo("value1");
        assertThat(actualLatestTelemetry.getDataValuesByKey("doubleKey").get(1)).isEqualTo(Double.toString(42.6));
        assertThat(actualLatestTelemetry.getDataValuesByKey("longKey").get(1)).isEqualTo(Long.toString(73));
    }

    @Test
    public void getAttributes() throws Exception {
        String accessToken = testRestClient.getDeviceCredentialsByDeviceId(device.getId()).getCredentialsId();
        assertThat(accessToken).isNotNull();

        JsonNode sharedAattribute = mapper.readTree(createPayload().toString());
        testRestClient.postTelemetryAttribute(DEVICE, device.getId(), SHARED_SCOPE, sharedAattribute);

        JsonNode clientAttribute = mapper.readTree(createPayload().toString());
        testRestClient.postAttribute(accessToken, clientAttribute);

        TimeUnit.SECONDS.sleep(3 * timeoutMultiplier);

        JsonNode attributes = testRestClient.getAttributes(accessToken, null, null);
        assertThat(attributes.get("shared")).isEqualTo(sharedAattribute);
        assertThat(attributes.get("client")).isEqualTo(clientAttribute);

        JsonNode attributes2 = testRestClient.getAttributes(accessToken, null, "stringKey");
        assertThat(attributes2.get("shared").get("stringKey")).isEqualTo(sharedAattribute.get("stringKey"));
        assertThat(attributes2.has("client")).isFalse();

        JsonNode attributes3 =  testRestClient.getAttributes(accessToken, "longKey,stringKey", null);

        assertThat(attributes3.has("shared")).isFalse();
        assertThat(attributes3.get("client").get("longKey")).isEqualTo(clientAttribute.get("longKey"));
        assertThat(attributes3.get("client").get("stringKey")).isEqualTo(clientAttribute.get("stringKey"));
    }
}
