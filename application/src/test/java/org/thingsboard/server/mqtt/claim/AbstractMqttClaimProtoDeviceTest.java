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
package org.thingsboard.server.mqtt.claim;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.Disabled;
import org.thingsboard.server.common.data.TransportPayloadType;
import org.thingsboard.server.gen.transport.TransportApiProtos;

@Slf4j
public abstract class AbstractMqttClaimProtoDeviceTest extends AbstractMqttClaimDeviceTest {

    @Before
    public void beforeTest() throws Exception {
        processBeforeTest("Test Claim device", "Test Claim gateway", TransportPayloadType.PROTOBUF, null, null);
        createCustomerAndUser();
    }

    @After
    public void afterTest() throws Exception { super.afterTest(); }

    @Test
    @Disabled
    // TODO: voba - disabled - can't assing to customer groups. Enable once this feature will be required
    public void testClaimingDevice() throws Exception {
        processTestClaimingDevice(false);
    }

    @Test
    @Disabled
    // TODO: voba - disabled - can't assing to customer groups. Enable once this feature will be required
    public void testClaimingDeviceWithoutSecretAndDuration() throws Exception {
        processTestClaimingDevice(true);
    }

    @Test
    @Disabled
    // TODO: voba - disabled - can't assing to customer groups. Enable once this feature will be required
    public void testGatewayClaimingDevice() throws Exception {
        processTestGatewayClaimingDevice("Test claiming gateway device Proto", false);
    }

    @Test
    @Disabled
    // TODO: voba - disabled - can't assing to customer groups. Enable once this feature will be required
    public void testGatewayClaimingDeviceWithoutSecretAndDuration() throws Exception {
        processTestGatewayClaimingDevice("Test claiming gateway device empty payload Proto", true);
    }

    protected void processTestClaimingDevice(boolean emptyPayload) throws Exception {
        MqttAsyncClient client = getMqttAsyncClient(accessToken);
        byte[] payloadBytes;
        if (emptyPayload) {
            payloadBytes = getClaimDevice(0, emptyPayload).toByteArray();
        } else {
            payloadBytes = getClaimDevice(60000, emptyPayload).toByteArray();
        }
        byte[] failurePayloadBytes = getClaimDevice(1, emptyPayload).toByteArray();
        validateClaimResponse(emptyPayload, client, payloadBytes, failurePayloadBytes);
    }

    protected void processTestGatewayClaimingDevice(String deviceName, boolean emptyPayload) throws Exception {
        MqttAsyncClient client = getMqttAsyncClient(gatewayAccessToken);
        byte[] failurePayloadBytes;
        byte[] payloadBytes;
        if (emptyPayload) {
            payloadBytes = getGatewayClaimMsg(deviceName, 0, emptyPayload).toByteArray();
        } else {
            payloadBytes = getGatewayClaimMsg(deviceName, 60000, emptyPayload).toByteArray();
        }
        failurePayloadBytes = getGatewayClaimMsg(deviceName, 1, emptyPayload).toByteArray();

        validateGatewayClaimResponse(deviceName, emptyPayload, client, failurePayloadBytes, payloadBytes);
    }

    private TransportApiProtos.GatewayClaimMsg getGatewayClaimMsg(String deviceName, long duration, boolean emptyPayload) {
        TransportApiProtos.GatewayClaimMsg.Builder gatewayClaimMsgBuilder = TransportApiProtos.GatewayClaimMsg.newBuilder();
        TransportApiProtos.ClaimDeviceMsg.Builder claimDeviceMsgBuilder = TransportApiProtos.ClaimDeviceMsg.newBuilder();
        TransportApiProtos.ClaimDevice.Builder claimDeviceBuilder = TransportApiProtos.ClaimDevice.newBuilder();
        if (!emptyPayload) {
            claimDeviceBuilder.setSecretKey("value");
        }
        if (duration > 0) {
            claimDeviceBuilder.setDurationMs(duration);
        }
        TransportApiProtos.ClaimDevice claimDevice = claimDeviceBuilder.build();
        claimDeviceMsgBuilder.setClaimRequest(claimDevice);
        claimDeviceMsgBuilder.setDeviceName(deviceName);
        TransportApiProtos.ClaimDeviceMsg claimDeviceMsg = claimDeviceMsgBuilder.build();
        gatewayClaimMsgBuilder.addMsg(claimDeviceMsg);
        return gatewayClaimMsgBuilder.build();
    }

    private TransportApiProtos.ClaimDevice getClaimDevice(long duration, boolean emptyPayload) {
        TransportApiProtos.ClaimDevice.Builder claimDeviceBuilder = TransportApiProtos.ClaimDevice.newBuilder();
        if (!emptyPayload) {
            claimDeviceBuilder.setSecretKey("value");
        }
        if (duration > 0) {
            claimDeviceBuilder.setSecretKey("value");
            claimDeviceBuilder.setDurationMs(duration);
        }
        return claimDeviceBuilder.build();
    }


}
