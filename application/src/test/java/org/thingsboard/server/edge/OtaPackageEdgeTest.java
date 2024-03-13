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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.SaveOtaPackageInfoRequest;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.ota.ChecksumAlgorithm;
import org.thingsboard.server.common.data.ota.DeviceGroupOtaPackage;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.DeviceGroupOtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.server.common.data.ota.OtaPackageType.FIRMWARE;

@DaoSqlTest
public class OtaPackageEdgeTest extends AbstractEdgeTest {

    @Test
    @Ignore
    public void testOtaPackages_usesUrl() throws Exception {
        // create ota package
        SaveOtaPackageInfoRequest firmwareInfo = new SaveOtaPackageInfoRequest();
        firmwareInfo.setDeviceProfileId(thermostatDeviceProfile.getId());
        firmwareInfo.setType(FIRMWARE);
        firmwareInfo.setTitle("My firmware #1");
        firmwareInfo.setVersion("v1.0");
        firmwareInfo.setTag("My firmware #1 v1.0");
        firmwareInfo.setUsesUrl(true);
        firmwareInfo.setUrl("http://localhost:8080/v1/package");
        firmwareInfo.setAdditionalInfo(JacksonUtil.newObjectNode());

        edgeImitator.expectMessageAmount(1);
        OtaPackageInfo savedFirmwareInfo = doPost("/api/otaPackage", firmwareInfo, OtaPackageInfo.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof OtaPackageUpdateMsg);
        OtaPackageUpdateMsg otaPackageUpdateMsg = (OtaPackageUpdateMsg) latestMessage;
        OtaPackage otaPackage = JacksonUtil.fromString(otaPackageUpdateMsg.getEntity(), OtaPackage.class, true);
        Assert.assertNotNull(otaPackage);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, otaPackageUpdateMsg.getMsgType());
        Assert.assertEquals(savedFirmwareInfo.getId(), otaPackage.getId());
        Assert.assertEquals(thermostatDeviceProfile.getId(), otaPackage.getDeviceProfileId());
        Assert.assertEquals(FIRMWARE, otaPackage.getType());
        Assert.assertEquals("My firmware #1", otaPackage.getTitle());
        Assert.assertEquals("v1.0", otaPackage.getVersion());
        Assert.assertEquals("My firmware #1 v1.0", otaPackage.getTag());
        Assert.assertEquals("http://localhost:8080/v1/package", otaPackage.getUrl());
        Assert.assertNull(otaPackage.getData());
        Assert.assertNull(otaPackage.getFileName());
        Assert.assertNull(otaPackage.getContentType());
        Assert.assertNull(otaPackage.getChecksumAlgorithm());
        Assert.assertNull(otaPackage.getChecksum());
        Assert.assertNull(otaPackage.getDataSize());

        // delete ota package
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/otaPackage/" + savedFirmwareInfo.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof OtaPackageUpdateMsg);
        otaPackageUpdateMsg = (OtaPackageUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, otaPackageUpdateMsg.getMsgType());
        Assert.assertEquals(savedFirmwareInfo.getUuidId().getMostSignificantBits(), otaPackageUpdateMsg.getIdMSB());
        Assert.assertEquals(savedFirmwareInfo.getUuidId().getLeastSignificantBits(), otaPackageUpdateMsg.getIdLSB());
    }

    @Test
    @Ignore
    public void testOtaPackages_hasData() throws Exception {
        // create ota package
        SaveOtaPackageInfoRequest firmwareInfo = new SaveOtaPackageInfoRequest();
        firmwareInfo.setDeviceProfileId(thermostatDeviceProfile.getId());
        firmwareInfo.setType(FIRMWARE);
        firmwareInfo.setTitle("My firmware #2");
        firmwareInfo.setVersion("v2.0");
        firmwareInfo.setTag("My firmware #2 v2.0");
        firmwareInfo.setUsesUrl(false);
        firmwareInfo.setHasData(false);
        firmwareInfo.setAdditionalInfo(JacksonUtil.newObjectNode());

        edgeImitator.expectMessageAmount(1);

        OtaPackageInfo savedFirmwareInfo = doPost("/api/otaPackage", firmwareInfo, OtaPackageInfo.class);
        MockMultipartFile testData = new MockMultipartFile("file", "firmware.bin", "image/png", ByteBuffer.wrap(new byte[]{1, 3, 5}).array());
        savedFirmwareInfo = saveData("/api/otaPackage/" + savedFirmwareInfo.getId().getId().toString() + "?checksumAlgorithm={checksumAlgorithm}", testData, ChecksumAlgorithm.SHA256.name());

        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof OtaPackageUpdateMsg);
        OtaPackageUpdateMsg otaPackageUpdateMsg = (OtaPackageUpdateMsg) latestMessage;
        OtaPackage otaPackage = JacksonUtil.fromString(otaPackageUpdateMsg.getEntity(), OtaPackage.class, true);
        Assert.assertNotNull(otaPackage);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, otaPackageUpdateMsg.getMsgType());
        Assert.assertEquals(savedFirmwareInfo.getId(), otaPackage.getId());
        Assert.assertEquals(thermostatDeviceProfile.getId(), otaPackage.getDeviceProfileId());
        Assert.assertEquals(FIRMWARE, otaPackage.getType());
        Assert.assertEquals("My firmware #2", otaPackage.getTitle());
        Assert.assertEquals("v2.0", otaPackage.getVersion());
        Assert.assertEquals("My firmware #2 v2.0", otaPackage.getTag());
        Assert.assertFalse(otaPackage.hasUrl());
        Assert.assertEquals("firmware.bin", otaPackage.getFileName());
        Assert.assertEquals("image/png", otaPackage.getContentType());
        Assert.assertEquals(ChecksumAlgorithm.SHA256, otaPackage.getChecksumAlgorithm());
        Assert.assertEquals("62467691cf583d4fa78b18fafaf9801f505e0ef03baf0603fd4b0cd004cd1e75", otaPackage.getChecksum());
        Assert.assertEquals(3L, otaPackage.getDataSize().longValue());
        Assert.assertEquals(ByteBuffer.wrap(new byte[]{1, 3, 5}), otaPackage.getData());

        // delete ota package
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/otaPackage/" + savedFirmwareInfo.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof OtaPackageUpdateMsg);
        otaPackageUpdateMsg = (OtaPackageUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, otaPackageUpdateMsg.getMsgType());
        Assert.assertEquals(savedFirmwareInfo.getUuidId().getMostSignificantBits(), otaPackageUpdateMsg.getIdMSB());
        Assert.assertEquals(savedFirmwareInfo.getUuidId().getLeastSignificantBits(), otaPackageUpdateMsg.getIdLSB());
    }

    @Test
    @Ignore
    public void testDeviceGroupOtaPackage() throws Exception {
        // create device entity group and do not assign to edge
        EntityGroup deviceEntityGroup1 = new EntityGroup();
        deviceEntityGroup1.setType(EntityType.DEVICE);
        deviceEntityGroup1.setName("DeviceGroup1");
        deviceEntityGroup1.setOwnerId(tenantId);
        deviceEntityGroup1 = doPost("/api/entityGroup", deviceEntityGroup1, EntityGroup.class);

        // create ota package
        SaveOtaPackageInfoRequest firmwareInfo = new SaveOtaPackageInfoRequest();
        firmwareInfo.setDeviceProfileId(thermostatDeviceProfile.getId());
        firmwareInfo.setType(FIRMWARE);
        firmwareInfo.setTitle("My firmware #2");
        firmwareInfo.setVersion("v2.0");
        firmwareInfo.setTag("My firmware #2 v2.0");
        firmwareInfo.setUsesUrl(false);
        firmwareInfo.setHasData(false);
        firmwareInfo.setAdditionalInfo(JacksonUtil.newObjectNode());

        edgeImitator.expectMessageAmount(1);

        OtaPackageInfo savedFirmwareInfo = doPost("/api/otaPackage", firmwareInfo, OtaPackageInfo.class);
        MockMultipartFile testData = new MockMultipartFile("file", "firmware.bin", "image/png", ByteBuffer.wrap(new byte[]{1, 3, 5}).array());
        savedFirmwareInfo = saveData("/api/otaPackage/" + savedFirmwareInfo.getId().getId().toString() + "?checksumAlgorithm={checksumAlgorithm}", testData, ChecksumAlgorithm.SHA256.name());

        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof OtaPackageUpdateMsg);
        OtaPackageUpdateMsg otaPackageUpdateMsg = (OtaPackageUpdateMsg) latestMessage;
        OtaPackage otaPackage = JacksonUtil.fromString(otaPackageUpdateMsg.getEntity(), OtaPackage.class, true);
        Assert.assertNotNull(otaPackage);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, otaPackageUpdateMsg.getMsgType());
        Assert.assertEquals(savedFirmwareInfo.getId(), otaPackage.getId());
        Assert.assertEquals(thermostatDeviceProfile.getId(), otaPackage.getDeviceProfileId());
        Assert.assertEquals(FIRMWARE, otaPackage.getType());
        Assert.assertEquals("My firmware #2", otaPackage.getTitle());
        Assert.assertEquals("v2.0", otaPackage.getVersion());
        Assert.assertEquals("My firmware #2 v2.0", otaPackage.getTag());

        // create device group ota package and do not send to edge
        DeviceGroupOtaPackage deviceGroupOtaPackage1 = new DeviceGroupOtaPackage();
        deviceGroupOtaPackage1.setGroupId(deviceEntityGroup1.getId());
        deviceGroupOtaPackage1.setOtaPackageId(savedFirmwareInfo.getId());
        deviceGroupOtaPackage1.setOtaPackageType(savedFirmwareInfo.getType());

        doPost("/api/deviceGroupOtaPackage", deviceGroupOtaPackage1, DeviceGroupOtaPackage.class);

        // no edge event saved - entity group is not assigned to edge
        Awaitility.await()
                .atMost(TIMEOUT, TimeUnit.SECONDS)
                .until(() -> {
                    PageData<EdgeEvent> result = edgeEventService.findEdgeEvents(tenantId, edge.getId(), 0L, null, new TimePageLink(1));
                    return result.getData().stream().noneMatch(ee -> EdgeEventType.DEVICE_GROUP_OTA.equals(ee.getType()));
                });

        // create device entity group and assign to edge
        EntityGroup deviceEntityGroup2 = createEntityGroupAndAssignToEdge(EntityType.DEVICE, "DeviceGroup2", tenantId);

        // create device and add to entity group 2
        edgeImitator.expectMessageAmount(2);
        Device savedDevice2 = saveDevice("Edge Device 2", THERMOSTAT_DEVICE_PROFILE_NAME, deviceEntityGroup2.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        Optional<DeviceUpdateMsg> deviceUpdateMsgOpt = edgeImitator.findMessageByType(DeviceUpdateMsg.class);
        Assert.assertTrue(deviceUpdateMsgOpt.isPresent());
        DeviceUpdateMsg deviceUpdateMsg = deviceUpdateMsgOpt.get();
        Device deviceFromMsg = JacksonUtil.fromString(deviceUpdateMsg.getEntity(), Device.class, true);
        Assert.assertNotNull(deviceFromMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        Assert.assertEquals(savedDevice2, deviceFromMsg);

        DeviceGroupOtaPackage deviceGroupOtaPackage2 = new DeviceGroupOtaPackage();
        deviceGroupOtaPackage2.setGroupId(deviceEntityGroup2.getId());
        deviceGroupOtaPackage2.setOtaPackageId(savedFirmwareInfo.getId());
        deviceGroupOtaPackage2.setOtaPackageType(savedFirmwareInfo.getType());

        edgeImitator.expectMessageAmount(1);

        deviceGroupOtaPackage2 = doPost("/api/deviceGroupOtaPackage", deviceGroupOtaPackage2, DeviceGroupOtaPackage.class);

        Assert.assertTrue(edgeImitator.waitForMessages());

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceGroupOtaPackageUpdateMsg);
        DeviceGroupOtaPackageUpdateMsg deviceGroupOtaPackageUpdateMsg = (DeviceGroupOtaPackageUpdateMsg) latestMessage;
        DeviceGroupOtaPackage savedDeviceGroupOtaPackage = JacksonUtil.fromString(deviceGroupOtaPackageUpdateMsg.getEntity(), DeviceGroupOtaPackage.class, true);
        Assert.assertNotNull(savedDeviceGroupOtaPackage);
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, otaPackageUpdateMsg.getMsgType());
        Assert.assertEquals(deviceGroupOtaPackage2, savedDeviceGroupOtaPackage);
    }

    private OtaPackageInfo saveData(String urlTemplate, MockMultipartFile content, String... params) throws Exception {
        MockMultipartHttpServletRequestBuilder postRequest = MockMvcRequestBuilders.multipart(urlTemplate, params);
        postRequest.file(content);
        setJwtToken(postRequest);
        return readResponse(mockMvc.perform(postRequest).andExpect(status().isOk()), OtaPackageInfo.class);
    }

}
