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
package org.thingsboard.server.transport.mqtt.telemetry.attributes;

import com.github.os72.protobuf.dynamic.DynamicSchema;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.common.data.TransportPayloadType;
import org.thingsboard.server.common.data.device.profile.DeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.MqttDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.MqttTopics;
import org.thingsboard.server.common.data.device.profile.ProtoTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.TransportPayloadTypeConfiguration;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.transport.TransportApiProtos;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Slf4j
@DaoSqlTest
public class MqttAttributesProtoIntegrationTest extends MqttAttributesIntegrationTest {

    private static final String POST_DATA_ATTRIBUTES_TOPIC = "proto/attributes";

    @Before
    @Override
    public void beforeTest() throws Exception {
        //do nothing, processBeforeTest will be invoked in particular test methods with different parameters
    }

    @Test
    public void testPushAttributes() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, POST_DATA_ATTRIBUTES_TOPIC);
        DynamicMessage postAttributesMsg = getDefaultDynamicMessage();
        processAttributesTest(POST_DATA_ATTRIBUTES_TOPIC, Arrays.asList("key1", "key2", "key3", "key4", "key5"), postAttributesMsg.toByteArray(), false);
    }

    @Test
    public void testPushAttributesWithEnabledJsonBackwardCompatibility() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, POST_DATA_ATTRIBUTES_TOPIC, true, false);
        processJsonPayloadAttributesTest(POST_DATA_ATTRIBUTES_TOPIC, Arrays.asList("key1", "key2", "key3", "key4", "key5"), PAYLOAD_VALUES_STR.getBytes());
    }

    @Test
    public void testPushAttributesWithExplicitPresenceProtoKeys() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, POST_DATA_ATTRIBUTES_TOPIC);
        DynamicSchema attributesSchema = getDynamicSchema();

        DynamicMessage.Builder nestedJsonObjectBuilder = attributesSchema.newMessageBuilder("PostAttributes.JsonObject.NestedJsonObject");
        Descriptors.Descriptor nestedJsonObjectBuilderDescriptor = nestedJsonObjectBuilder.getDescriptorForType();
        assertNotNull(nestedJsonObjectBuilderDescriptor);
        DynamicMessage nestedJsonObject = nestedJsonObjectBuilder.setField(nestedJsonObjectBuilderDescriptor.findFieldByName("key"), "value").build();

        DynamicMessage.Builder jsonObjectBuilder = attributesSchema.newMessageBuilder("PostAttributes.JsonObject");
        Descriptors.Descriptor jsonObjectBuilderDescriptor = jsonObjectBuilder.getDescriptorForType();
        assertNotNull(jsonObjectBuilderDescriptor);
        DynamicMessage jsonObject = jsonObjectBuilder
                .addRepeatedField(jsonObjectBuilderDescriptor.findFieldByName("someArray"), 1)
                .addRepeatedField(jsonObjectBuilderDescriptor.findFieldByName("someArray"), 2)
                .addRepeatedField(jsonObjectBuilderDescriptor.findFieldByName("someArray"), 3)
                .setField(jsonObjectBuilderDescriptor.findFieldByName("someNestedObject"), nestedJsonObject)
                .build();

        DynamicMessage.Builder postAttributesBuilder = attributesSchema.newMessageBuilder("PostAttributes");
        Descriptors.Descriptor postAttributesMsgDescriptor = postAttributesBuilder.getDescriptorForType();
        assertNotNull(postAttributesMsgDescriptor);
        DynamicMessage postAttributesMsg = postAttributesBuilder
                .setField(postAttributesMsgDescriptor.findFieldByName("key1"), "")
                .setField(postAttributesMsgDescriptor.findFieldByName("key2"), false)
                .setField(postAttributesMsgDescriptor.findFieldByName("key3"), 0.0)
                .setField(postAttributesMsgDescriptor.findFieldByName("key4"), 0)
                .setField(postAttributesMsgDescriptor.findFieldByName("key5"), jsonObject)
                .build();
        processAttributesTest(POST_DATA_ATTRIBUTES_TOPIC, Arrays.asList("key1", "key2", "key3", "key4", "key5"), postAttributesMsg.toByteArray(), true);
    }

    @Test
    public void testPushAttributesOnShortTopic() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, POST_DATA_ATTRIBUTES_TOPIC);
        DynamicMessage postAttributesMsg = getDefaultDynamicMessage();
        processAttributesTest(MqttTopics.DEVICE_ATTRIBUTES_SHORT_TOPIC, Arrays.asList("key1", "key2", "key3", "key4", "key5"), postAttributesMsg.toByteArray(), false);
    }

    @Test
    public void testPushAttributesOnShortJsonTopic() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, POST_DATA_ATTRIBUTES_TOPIC);
        processJsonPayloadAttributesTest(MqttTopics.DEVICE_ATTRIBUTES_SHORT_JSON_TOPIC, Arrays.asList("key1", "key2", "key3", "key4", "key5"), PAYLOAD_VALUES_STR.getBytes());
    }

    @Test
    public void testPushAttributesOnShortProtoTopic() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, POST_DATA_ATTRIBUTES_TOPIC);
        DynamicMessage postAttributesMsg = getDefaultDynamicMessage();
        processAttributesTest(MqttTopics.DEVICE_ATTRIBUTES_SHORT_PROTO_TOPIC, Arrays.asList("key1", "key2", "key3", "key4", "key5"), postAttributesMsg.toByteArray(), false);
    }

    @Test
    public void testPushAttributesGateway() throws Exception {
        processBeforeTest("Test Post Attributes device", "Test Post Attributes gateway", TransportPayloadType.PROTOBUF, null, null);
        TransportApiProtos.GatewayAttributesMsg.Builder gatewayAttributesMsgProtoBuilder = TransportApiProtos.GatewayAttributesMsg.newBuilder();
        List<String> expectedKeys = Arrays.asList("key1", "key2", "key3", "key4", "key5");
        String deviceName1 = "Device A";
        String deviceName2 = "Device B";
        TransportApiProtos.AttributesMsg firstDeviceAttributesMsgProto = getDeviceAttributesMsgProto(deviceName1, expectedKeys);
        TransportApiProtos.AttributesMsg secondDeviceAttributesMsgProto = getDeviceAttributesMsgProto(deviceName2, expectedKeys);
        gatewayAttributesMsgProtoBuilder.addAllMsg(Arrays.asList(firstDeviceAttributesMsgProto, secondDeviceAttributesMsgProto));
        TransportApiProtos.GatewayAttributesMsg gatewayAttributesMsg = gatewayAttributesMsgProtoBuilder.build();
        processGatewayAttributesTest(expectedKeys, gatewayAttributesMsg.toByteArray(), deviceName1, deviceName2);
    }

    private DynamicSchema getDynamicSchema() {
        DeviceProfileTransportConfiguration transportConfiguration = deviceProfile.getProfileData().getTransportConfiguration();
        assertTrue(transportConfiguration instanceof MqttDeviceProfileTransportConfiguration);
        MqttDeviceProfileTransportConfiguration mqttTransportConfiguration = (MqttDeviceProfileTransportConfiguration) transportConfiguration;
        TransportPayloadTypeConfiguration transportPayloadTypeConfiguration = mqttTransportConfiguration.getTransportPayloadTypeConfiguration();
        assertTrue(transportPayloadTypeConfiguration instanceof ProtoTransportPayloadConfiguration);
        ProtoTransportPayloadConfiguration protoTransportPayloadConfiguration = (ProtoTransportPayloadConfiguration) transportPayloadTypeConfiguration;
        ProtoFileElement transportProtoSchemaFile = protoTransportPayloadConfiguration.getTransportProtoSchema(DEVICE_ATTRIBUTES_PROTO_SCHEMA);
        return protoTransportPayloadConfiguration.getDynamicSchema(transportProtoSchemaFile, ProtoTransportPayloadConfiguration.ATTRIBUTES_PROTO_SCHEMA);
    }

    private DynamicMessage getDefaultDynamicMessage() {
        DynamicSchema attributesSchema = getDynamicSchema();

        DynamicMessage.Builder nestedJsonObjectBuilder = attributesSchema.newMessageBuilder("PostAttributes.JsonObject.NestedJsonObject");
        Descriptors.Descriptor nestedJsonObjectBuilderDescriptor = nestedJsonObjectBuilder.getDescriptorForType();
        assertNotNull(nestedJsonObjectBuilderDescriptor);
        DynamicMessage nestedJsonObject = nestedJsonObjectBuilder.setField(nestedJsonObjectBuilderDescriptor.findFieldByName("key"), "value").build();

        DynamicMessage.Builder jsonObjectBuilder = attributesSchema.newMessageBuilder("PostAttributes.JsonObject");
        Descriptors.Descriptor jsonObjectBuilderDescriptor = jsonObjectBuilder.getDescriptorForType();
        assertNotNull(jsonObjectBuilderDescriptor);
        DynamicMessage jsonObject = jsonObjectBuilder
                .setField(jsonObjectBuilderDescriptor.findFieldByName("someNumber"), 42)
                .addRepeatedField(jsonObjectBuilderDescriptor.findFieldByName("someArray"), 1)
                .addRepeatedField(jsonObjectBuilderDescriptor.findFieldByName("someArray"), 2)
                .addRepeatedField(jsonObjectBuilderDescriptor.findFieldByName("someArray"), 3)
                .setField(jsonObjectBuilderDescriptor.findFieldByName("someNestedObject"), nestedJsonObject)
                .build();

        DynamicMessage.Builder postAttributesBuilder = attributesSchema.newMessageBuilder("PostAttributes");
        Descriptors.Descriptor postAttributesMsgDescriptor = postAttributesBuilder.getDescriptorForType();
        assertNotNull(postAttributesMsgDescriptor);
        return postAttributesBuilder
                .setField(postAttributesMsgDescriptor.findFieldByName("key1"), "value1")
                .setField(postAttributesMsgDescriptor.findFieldByName("key2"), true)
                .setField(postAttributesMsgDescriptor.findFieldByName("key3"), 3.0)
                .setField(postAttributesMsgDescriptor.findFieldByName("key4"), 4)
                .setField(postAttributesMsgDescriptor.findFieldByName("key5"), jsonObject)
                .build();
    }

    private TransportApiProtos.AttributesMsg getDeviceAttributesMsgProto(String deviceName, List<String> expectedKeys) {
        TransportApiProtos.AttributesMsg.Builder deviceAttributesMsgBuilder = TransportApiProtos.AttributesMsg.newBuilder();
        TransportProtos.PostAttributeMsg msg = getPostAttributeMsg(expectedKeys);
        deviceAttributesMsgBuilder.setDeviceName(deviceName);
        deviceAttributesMsgBuilder.setMsg(msg);
        return deviceAttributesMsgBuilder.build();
    }
}
