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
package org.thingsboard.server.transport.mqtt.sparkplug.attributes;

import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.msg.session.FeatureType;
import org.thingsboard.server.transport.mqtt.sparkplug.AbstractMqttV5ClientSparkplugTest;
import org.thingsboard.server.transport.mqtt.util.sparkplug.MetricDataType;
import org.thingsboard.server.transport.mqtt.util.sparkplug.SparkplugMessageType;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.server.common.data.DataConstants.SHARED_SCOPE;
import static org.thingsboard.server.transport.mqtt.util.sparkplug.MetricDataType.UInt32;
import static org.thingsboard.server.transport.mqtt.util.sparkplug.SparkplugMessageType.NCMD;
import static org.thingsboard.server.transport.mqtt.util.sparkplug.SparkplugTopicUtil.NAMESPACE;

/**
 * Created by nickAS21 on 12.01.23
 */
@Slf4j
public abstract class AbstractMqttV5ClientSparkplugAttributesTest extends AbstractMqttV5ClientSparkplugTest {

    protected void processClientWithCorrectAccessTokenPublishNCMDReBirth() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        List<String> listKeys = connectionWithNBirth(metricBirthDataType_Int32, metricBirthName_Int32, nextInt32());
        // Shared attribute "Node Control/Rebirth" = true. type = NCMD.
        boolean value = true;
        Assert.assertTrue(listKeys.contains(keyNodeRebirth));
        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + keyNodeRebirth + "\":" + value + "}";
        Assert.assertTrue("Connection node is failed", client.isConnected());
        client.subscribeAndWait(NAMESPACE + "/" + groupId + "/" + NCMD.name() + "/" + edgeNode + "/#", MqttQoS.AT_MOST_ONCE);
        awaitForDeviceActorToReceiveSubscription(savedGateway.getId(), FeatureType.ATTRIBUTES, 1);
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(keyNodeRebirth, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(mqttCallback.getMessageArrivedMetrics().get(0).getBooleanValue());
    }

    /**
     * If boolean - send long 0 or 1
     * If String - try to parse long
     * If double - cast long
     * If we can't parse, cast, or JSON there - debug the message with the id of the devise/node, tenant,
     * the name and type of the attribute into an error and don't send anything.
     */
    protected void processClientWithCorrectAccessTokenPublishNCMD_BooleanType_IfMetricFailedTypeCheck_SendValueOk() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        MetricDataType metricDataType = MetricDataType.Boolean;
        String metricKey = "MyBoolean";
        Object metricValue = nextBoolean();
        connectionWithNBirth(metricDataType, metricKey, metricValue);
        Assert.assertTrue("Connection node is failed", client.isConnected());
        client.subscribeAndWait(NAMESPACE + "/" + groupId + "/" + NCMD.name() + "/" + edgeNode + "/#", MqttQoS.AT_MOST_ONCE);
        awaitForDeviceActorToReceiveSubscription(savedGateway.getId(), FeatureType.ATTRIBUTES, 1);

        // Boolean <-> String
        boolean expectedValue = true;
        String valueStr = "123";
        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueStr + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getBooleanValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        expectedValue = false;
        valueStr = "0";
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueStr + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getBooleanValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        // Boolean <-> Integer
        expectedValue = true;
        Integer valueInt = nextInt32();
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueInt + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getBooleanValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        expectedValue = false;
        valueInt = 0;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueInt + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getBooleanValue());
    }

    protected void processClientWithCorrectAccessTokenPublishNCMD_LongType_IfMetricFailedTypeCheck_SendValueOk() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        MetricDataType metricDataType = UInt32;
        String metricKey = "MyLong";
        Object metricValue = nextUInt32();
        connectionWithNBirth(metricDataType, metricKey, metricValue);
        Assert.assertTrue("Connection node is failed", client.isConnected());
        client.subscribeAndWait(NAMESPACE + "/" + groupId + "/" + NCMD.name() + "/" + edgeNode + "/#", MqttQoS.AT_MOST_ONCE);
        awaitForDeviceActorToReceiveSubscription(savedGateway.getId(), FeatureType.ATTRIBUTES, 1);

        // Long <-> String
        String valueStr = "123";
        long expectedValue = Long.valueOf(valueStr);

        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueStr + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getLongValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        // Long <-> Boolean
        Boolean valueBoolean = true;
        expectedValue = 1L;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getLongValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        valueBoolean = false;
        expectedValue = 0L;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getLongValue());
    }

    protected void processClientWithCorrectAccessTokenPublishNCMD_FloatType_IfMetricFailedTypeCheck_SendValueOk() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        MetricDataType metricDataType = MetricDataType.Float;
        String metricKey = "MyFloat";
        Object metricValue = nextFloat(30, 400);
        connectionWithNBirth(metricDataType, metricKey, metricValue);
        Assert.assertTrue("Connection node is failed", client.isConnected());
        client.subscribeAndWait(NAMESPACE + "/" + groupId + "/" + NCMD.name() + "/" + edgeNode + "/#", MqttQoS.AT_MOST_ONCE);
        awaitForDeviceActorToReceiveSubscription(savedGateway.getId(), FeatureType.ATTRIBUTES, 1);

        // Float <-> String
        String valueStr = "123.345";
        float expectedValue = Float.valueOf(valueStr);

        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueStr + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(expectedValue == mqttCallback.getMessageArrivedMetrics().get(0).getFloatValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        // Float <-> Boolean
        Boolean valueBoolean = true;
        expectedValue = 1f;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(expectedValue == mqttCallback.getMessageArrivedMetrics().get(0).getFloatValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        valueBoolean = false;
        expectedValue = 0f;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(expectedValue == mqttCallback.getMessageArrivedMetrics().get(0).getFloatValue());
    }

    protected void processClientWithCorrectAccessTokenPublishNCMD_DoubleType_IfMetricFailedTypeCheck_SendValueOk() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        MetricDataType metricDataType = MetricDataType.Double;
        String metricKey = "MyDouble";
        Object metricValue = nextDouble();
        connectionWithNBirth(metricDataType, metricKey, metricValue);
        Assert.assertTrue("Connection node is failed", client.isConnected());
        client.subscribeAndWait(NAMESPACE + "/" + groupId + "/" + NCMD.name() + "/" + edgeNode + "/#", MqttQoS.AT_MOST_ONCE);
        awaitForDeviceActorToReceiveSubscription(savedGateway.getId(), FeatureType.ATTRIBUTES, 1);

        // Double <-> String
        String valueStr = "123345456";
        double expectedValue = Double.valueOf(valueStr);

        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueStr + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(expectedValue == mqttCallback.getMessageArrivedMetrics().get(0).getDoubleValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        // Double <-> Boolean
        Boolean valueBoolean = true;
        expectedValue = 1d;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(expectedValue == mqttCallback.getMessageArrivedMetrics().get(0).getDoubleValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        valueBoolean = false;
        expectedValue = 0d;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertTrue(expectedValue == mqttCallback.getMessageArrivedMetrics().get(0).getDoubleValue());
    }

    protected void processClientWithCorrectAccessTokenPublishNCMD_StringType_IfMetricFailedTypeCheck_SendValueOk() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        MetricDataType metricDataType = MetricDataType.String;
        String metricKey = "MyString";
        Object metricValue = nextString();
        connectionWithNBirth(metricDataType, metricKey, metricValue);
        Assert.assertTrue("Connection node is failed", client.isConnected());
        client.subscribeAndWait(NAMESPACE + "/" + groupId + "/" + NCMD.name() + "/" + edgeNode + "/#", MqttQoS.AT_MOST_ONCE);
        awaitForDeviceActorToReceiveSubscription(savedGateway.getId(), FeatureType.ATTRIBUTES, 1);

        // String <-> Long
        long valueLong = 123345456L;
        String expectedValue = String.valueOf(valueLong);

        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueLong + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getStringValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        // String <-> Boolean
        Boolean valueBoolean = true;
        expectedValue = "true";
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getStringValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        valueBoolean = false;
        expectedValue = "false";
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricKey + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricKey, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getStringValue());
    }

    protected void processClientDeviceWithCorrectAccessTokenPublishWithBirth_SharedAttribute() throws Exception {
        long ts = calendar.getTimeInMillis();
        List<Device> devices = connectClientWithCorrectAccessTokenWithNDEATHCreatedDevices(1, ts);

        // Integer <-> Integer
        int expectedValueInt = 123456;

        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricBirthName_Int32 + "\":" + expectedValueInt + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + devices.get(0).getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.DBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricBirthName_Int32, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(metricBirthName_Int32, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValueInt, mqttCallback.getMessageArrivedMetrics().get(0).getIntValue());
    }

    protected void processClientDeviceWithCorrectAccessTokenPublishWithBirth_SharedAttributes_LongType_IfMetricFailedTypeCheck_SendValueOk() throws Exception {
        long ts = calendar.getTimeInMillis();
        List<Device> devices = connectClientWithCorrectAccessTokenWithNDEATHCreatedDevices(1, ts);

        // Int <-> String
        String valueStr = "123";
        long expectedValue = Long.valueOf(valueStr);

        String SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricBirthName_Int32 + "\":" + valueStr + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + devices.get(0).getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.DBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricBirthName_Int32, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getIntValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        // Int <-> Boolean
        Boolean valueBoolean = true;
        expectedValue = 1;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricBirthName_Int32 + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + devices.get(0).getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricBirthName_Int32, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getIntValue());
        mqttCallback.deleteMessageArrivedMetrics(0);

        valueBoolean = false;
        expectedValue = 0;
        SHARED_ATTRIBUTES_PAYLOAD = "{\"" + metricBirthName_Int32 + "\":" + valueBoolean + "}";
        doPostAsync("/api/plugins/telemetry/DEVICE/" + devices.get(0).getId().getId() + "/attributes/SHARED_SCOPE", SHARED_ATTRIBUTES_PAYLOAD, String.class, status().isOk());
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    return mqttCallback.getMessageArrivedMetrics().size() == 1;
                });
        Assert.assertEquals(metricBirthName_Int32, mqttCallback.getMessageArrivedMetrics().get(0).getName());
        Assert.assertEquals(expectedValue, mqttCallback.getMessageArrivedMetrics().get(0).getIntValue());
    }

    protected void processClientNodeWithCorrectAccessTokenPublish_AttributesInProfileContainsKeyAttributes() throws Exception {
        clientWithCorrectNodeAccessTokenWithNDEATH();
        connectionWithNBirth(metricBirthDataType_Int32, metricBirthName_Int32, nextInt32());
        String urlTemplate = "/api/plugins/telemetry/DEVICE/" + savedGateway.getId().getId() + "/keys/attributes/" + SHARED_SCOPE;
        AtomicReference<List<String>> actualKeys = new AtomicReference<>();
        await(alias + SparkplugMessageType.NBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    actualKeys.set(doGetAsyncTyped(urlTemplate, new TypeReference<>() {
                    }));
                    return actualKeys.get().size() == 1;
                });
        Assert.assertEquals(metricBirthName_Int32, actualKeys.get().get(0));
    }

    protected void processClientDeviceWithCorrectAccessTokenPublish_AttributesInProfileContainsKeyAttributes() throws Exception {
        long ts = calendar.getTimeInMillis();
        List<Device> devices = connectClientWithCorrectAccessTokenWithNDEATHCreatedDevices(1, ts);
        String urlTemplate = "/api/plugins/telemetry/DEVICE/" + devices.get(0).getId().getId() + "/keys/attributes/" + SHARED_SCOPE;
        AtomicReference<List<String>> actualKeys = new AtomicReference<>();
        await(alias + SparkplugMessageType.DBIRTH.name())
                .atMost(40, TimeUnit.SECONDS)
                .until(() -> {
                    actualKeys.set(doGetAsyncTyped(urlTemplate, new TypeReference<>() {
                    }));
                    return actualKeys.get().size() == 1;
                });
        Assert.assertEquals(metricBirthName_Int32, actualKeys.get().get(0));
    }

}
