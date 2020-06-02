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
package org.thingsboard.server.mqtt.telemetry;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.web.util.UriComponentsBuilder;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.service.DaoNoSqlTest;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Valerii Sosliuk
 */
@Slf4j
public abstract class AbstractMqttTelemetryIntegrationTest extends AbstractControllerTest {

    private static final String MQTT_URL = "tcp://localhost:1883";

    private Device savedDevice;
    private String accessToken;

    @Before
    public void beforeTest() throws Exception {
        loginTenantAdmin();

        Device device = new Device();
        device.setName("Test device");
        device.setType("default");
        savedDevice = doPost("/api/device", device, Device.class);

        DeviceCredentials deviceCredentials =
                doGet("/api/device/" + savedDevice.getId().getId().toString() + "/credentials", DeviceCredentials.class);

        assertEquals(savedDevice.getId(), deviceCredentials.getDeviceId());
        accessToken = deviceCredentials.getCredentialsId();
        assertNotNull(accessToken);
    }

    @Test
    public void testPushMqttRpcData() throws Exception {
        String clientId = MqttAsyncClient.generateClientId();
        MqttAsyncClient client = new MqttAsyncClient(MQTT_URL, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(accessToken);
        client.connect(options);
        Thread.sleep(3000);
        MqttMessage message = new MqttMessage();
        message.setPayload("{\"key1\":\"value1\", \"key2\":true, \"key3\": 3.0, \"key4\": 4}".getBytes());
        client.publish("v1/devices/me/telemetry", message);

        String deviceId = savedDevice.getId().getId().toString();

        Thread.sleep(2000);
        List<String> actualKeys = doGetAsync("/api/plugins/telemetry/DEVICE/" + deviceId + "/keys/timeseries", List.class);
        Set<String> actualKeySet = new HashSet<>(actualKeys);

        List<String> expectedKeys = Arrays.asList("key1", "key2", "key3", "key4");
        Set<String> expectedKeySet = new HashSet<>(expectedKeys);

        assertEquals(expectedKeySet, actualKeySet);

        String getTelemetryValuesUrl = "/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries?keys=" + String.join(",", actualKeySet);
        Map<String, List<Map<String, String>>> values = doGetAsync(getTelemetryValuesUrl, Map.class);

        assertEquals("value1", values.get("key1").get(0).get("value"));
        assertEquals("true", values.get("key2").get(0).get("value"));
        assertEquals("3.0", values.get("key3").get(0).get("value"));
        assertEquals("4", values.get("key4").get(0).get("value"));
        client.disconnect();
    }


//    @Test - Unstable
    public void testMqttQoSLevel() throws Exception {
        String clientId = MqttAsyncClient.generateClientId();
        MqttAsyncClient client = new MqttAsyncClient(MQTT_URL, clientId);

        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName(accessToken);
        CountDownLatch latch = new CountDownLatch(1);
        TestMqttCallback callback = new TestMqttCallback(client, latch);
        client.setCallback(callback);
        client.connect(options).waitForCompletion(5000);
        client.subscribe("v1/devices/me/attributes", MqttQoS.AT_MOST_ONCE.value());
        String payload = "{\"key\":\"uniqueValue\"}";
//        TODO 3.1: we need to acknowledge subscription only after it is processed by device actor and not when the message is pushed to queue.
//        MqttClient -> SUB REQUEST -> Transport -> Kafka -> Device Actor (subscribed)
//        MqttClient <- SUB_ACK <- Transport
        Thread.sleep(5000);
        doPostAsync("/api/plugins/telemetry/" + savedDevice.getId() + "/SHARED_SCOPE", payload, String.class, status().isOk());
        latch.await(10, TimeUnit.SECONDS);
        assertEquals(payload, callback.getPayload());
        assertEquals(MqttQoS.AT_MOST_ONCE.value(), callback.getQoS());
        client.disconnect();
    }

    private static class TestMqttCallback implements MqttCallback {

        private final MqttAsyncClient client;
        private final CountDownLatch latch;
        private volatile Integer qoS;
        private volatile String payload;

        String getPayload() {
            return payload;
        }

        TestMqttCallback(MqttAsyncClient client, CountDownLatch latch) {
            this.client = client;
            this.latch = latch;
        }

        int getQoS() {
            return qoS;
        }

        @Override
        public void connectionLost(Throwable throwable) {
            log.error("Client connection lost", throwable);
        }

        @Override
        public void messageArrived(String requestTopic, MqttMessage mqttMessage) {
            payload = new String(mqttMessage.getPayload());
            qoS = mqttMessage.getQos();
            latch.countDown();
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

        }
    }


}
