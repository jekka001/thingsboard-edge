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
package org.thingsboard.server.transport.coap.provision;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.elements.exception.ConnectorException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.server.transport.coap.AbstractCoapIntegrationTest;
import org.thingsboard.server.common.data.CoapDeviceType;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.TransportPayloadType;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.msg.EncryptionUtil;
import org.thingsboard.server.common.msg.session.FeatureType;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.device.provision.ProvisionResponseStatus;
import org.thingsboard.server.gen.transport.TransportProtos.CredentialsDataProto;
import org.thingsboard.server.gen.transport.TransportProtos.CredentialsType;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceCredentialsMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ValidateDeviceTokenRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ValidateDeviceX509CertRequestMsg;

import java.io.IOException;

@Slf4j
public abstract class AbstractCoapProvisionProtoDeviceTest extends AbstractCoapIntegrationTest {

    @Autowired
    DeviceCredentialsService deviceCredentialsService;

    @Autowired
    DeviceService deviceService;

    @After
    public void afterTest() throws Exception {
        super.processAfterTest();
    }

    @Test
    public void testProvisioningDisabledDevice() throws Exception {
        processTestProvisioningDisabledDevice();
    }

    @Test
    public void testProvisioningCheckPreProvisionedDevice() throws Exception {
        processTestProvisioningCheckPreProvisionedDevice();
    }

    @Test
    public void testProvisioningCreateNewDeviceWithoutCredentials() throws Exception {
        processTestProvisioningCreateNewDeviceWithoutCredentials();
    }

    @Test
    public void testProvisioningCreateNewDeviceWithAccessToken() throws Exception {
        processTestProvisioningCreateNewDeviceWithAccessToken();
    }

    @Test
    public void testProvisioningCreateNewDeviceWithCert() throws Exception {
        processTestProvisioningCreateNewDeviceWithCert();
    }

    @Test
    public void testProvisioningWithBadKeyDevice() throws Exception {
        processTestProvisioningWithBadKeyDevice();
    }


    private void processTestProvisioningDisabledDevice() throws Exception {
        super.processBeforeTest("Test Provision device", CoapDeviceType.DEFAULT, TransportPayloadType.PROTOBUF, null, null, DeviceProfileProvisionType.DISABLED, null, null);
        ProvisionDeviceResponseMsg result = ProvisionDeviceResponseMsg.parseFrom(createCoapClientAndPublish().getPayload());
        Assert.assertNotNull(result);
        Assert.assertEquals(ProvisionResponseStatus.NOT_FOUND.name(), result.getStatus().toString());
    }

    private void processTestProvisioningCreateNewDeviceWithoutCredentials() throws Exception {
        super.processBeforeTest("Test Provision device3", CoapDeviceType.DEFAULT, TransportPayloadType.PROTOBUF, null, null, DeviceProfileProvisionType.ALLOW_CREATE_NEW_DEVICES, "testProvisionKey", "testProvisionSecret");
        ProvisionDeviceResponseMsg response = ProvisionDeviceResponseMsg.parseFrom(createCoapClientAndPublish().getPayload());

        Device createdDevice = deviceService.findDeviceByTenantIdAndName(savedTenant.getTenantId(), "Test Provision device");

        Assert.assertNotNull(createdDevice);

        DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(savedTenant.getTenantId(), createdDevice.getId());

        Assert.assertEquals(deviceCredentials.getCredentialsType().name(), response.getCredentialsType().toString());
        Assert.assertEquals(ProvisionResponseStatus.SUCCESS.name(), response.getStatus().toString());
    }

    private void processTestProvisioningCreateNewDeviceWithAccessToken() throws Exception {
        super.processBeforeTest("Test Provision device3", CoapDeviceType.DEFAULT, TransportPayloadType.PROTOBUF, null, null, DeviceProfileProvisionType.ALLOW_CREATE_NEW_DEVICES, "testProvisionKey", "testProvisionSecret");
        CredentialsDataProto requestCredentials = CredentialsDataProto.newBuilder().setValidateDeviceTokenRequestMsg(ValidateDeviceTokenRequestMsg.newBuilder().setToken("test_token").build()).build();

        ProvisionDeviceResponseMsg response = ProvisionDeviceResponseMsg.parseFrom(createCoapClientAndPublish(createTestsProvisionMessage(CredentialsType.ACCESS_TOKEN, requestCredentials)).getPayload());

        Device createdDevice = deviceService.findDeviceByTenantIdAndName(savedTenant.getTenantId(), "Test Provision device");

        Assert.assertNotNull(createdDevice);

        DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(savedTenant.getTenantId(), createdDevice.getId());

        Assert.assertEquals(deviceCredentials.getCredentialsType().name(), response.getCredentialsType().toString());
        Assert.assertEquals(deviceCredentials.getCredentialsType(), DeviceCredentialsType.ACCESS_TOKEN);
        Assert.assertEquals(deviceCredentials.getCredentialsId(), "test_token");
        Assert.assertEquals(ProvisionResponseStatus.SUCCESS.name(), response.getStatus().toString());
    }

    private void processTestProvisioningCreateNewDeviceWithCert() throws Exception {
        super.processBeforeTest("Test Provision device3", CoapDeviceType.DEFAULT, TransportPayloadType.PROTOBUF, null, null, DeviceProfileProvisionType.ALLOW_CREATE_NEW_DEVICES, "testProvisionKey", "testProvisionSecret");
        CredentialsDataProto requestCredentials = CredentialsDataProto.newBuilder().setValidateDeviceX509CertRequestMsg(ValidateDeviceX509CertRequestMsg.newBuilder().setHash("testHash").build()).build();

        ProvisionDeviceResponseMsg response = ProvisionDeviceResponseMsg.parseFrom(createCoapClientAndPublish(createTestsProvisionMessage(CredentialsType.X509_CERTIFICATE, requestCredentials)).getPayload());

        Device createdDevice = deviceService.findDeviceByTenantIdAndName(savedTenant.getTenantId(), "Test Provision device");

        Assert.assertNotNull(createdDevice);

        DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(savedTenant.getTenantId(), createdDevice.getId());

        Assert.assertEquals(deviceCredentials.getCredentialsType().name(), response.getCredentialsType().toString());
        Assert.assertEquals(deviceCredentials.getCredentialsType(), DeviceCredentialsType.X509_CERTIFICATE);

        String cert = EncryptionUtil.trimNewLines(deviceCredentials.getCredentialsValue());
        String sha3Hash = EncryptionUtil.getSha3Hash(cert);

        Assert.assertEquals(deviceCredentials.getCredentialsId(), sha3Hash);

        Assert.assertEquals(deviceCredentials.getCredentialsValue(), "testHash");
        Assert.assertEquals(ProvisionResponseStatus.SUCCESS.name(), response.getStatus().toString());
    }

    private void processTestProvisioningCheckPreProvisionedDevice() throws Exception {
        super.processBeforeTest("Test Provision device", CoapDeviceType.DEFAULT, TransportPayloadType.PROTOBUF, null, null, DeviceProfileProvisionType.CHECK_PRE_PROVISIONED_DEVICES, "testProvisionKey", "testProvisionSecret");
        ProvisionDeviceResponseMsg response = ProvisionDeviceResponseMsg.parseFrom(createCoapClientAndPublish().getPayload());

        DeviceCredentials deviceCredentials = deviceCredentialsService.findDeviceCredentialsByDeviceId(savedTenant.getTenantId(), savedDevice.getId());

        Assert.assertEquals(deviceCredentials.getCredentialsType().name(), response.getCredentialsType().toString());
        Assert.assertEquals(ProvisionResponseStatus.SUCCESS.name(), response.getStatus().toString());
    }

    private void processTestProvisioningWithBadKeyDevice() throws Exception {
        super.processBeforeTest("Test Provision device", CoapDeviceType.DEFAULT, TransportPayloadType.PROTOBUF, null, null, DeviceProfileProvisionType.CHECK_PRE_PROVISIONED_DEVICES, "testProvisionKeyOrig", "testProvisionSecret");
        ProvisionDeviceResponseMsg response = ProvisionDeviceResponseMsg.parseFrom(createCoapClientAndPublish().getPayload());
        Assert.assertEquals(ProvisionResponseStatus.NOT_FOUND.name(), response.getStatus().toString());
    }

    private CoapResponse createCoapClientAndPublish() throws Exception {
        byte[] provisionRequestMsg = createTestProvisionMessage();
        return createCoapClientAndPublish(provisionRequestMsg);
    }

    private CoapResponse createCoapClientAndPublish(byte[] provisionRequestMsg) throws Exception {
        CoapClient client = getCoapClient(FeatureType.PROVISION);
        return postProvision(client, provisionRequestMsg);
    }

    private CoapResponse postProvision(CoapClient client, byte[] payload) throws IOException, ConnectorException {
        return client.setTimeout((long) 60000).post(payload, MediaTypeRegistry.APPLICATION_JSON);
    }

    private byte[] createTestsProvisionMessage(CredentialsType credentialsType, CredentialsDataProto credentialsData) throws Exception {
        return ProvisionDeviceRequestMsg.newBuilder()
                .setDeviceName("Test Provision device")
                .setCredentialsType(credentialsType != null ? credentialsType : CredentialsType.ACCESS_TOKEN)
                .setCredentialsDataProto(credentialsData != null ? credentialsData: CredentialsDataProto.newBuilder().build())
                .setProvisionDeviceCredentialsMsg(
                        ProvisionDeviceCredentialsMsg.newBuilder()
                                .setProvisionDeviceKey("testProvisionKey")
                                .setProvisionDeviceSecret("testProvisionSecret")
                ).build()
                .toByteArray();
    }


    private byte[] createTestProvisionMessage() throws Exception {
        return createTestsProvisionMessage(null, null);
    }

}
