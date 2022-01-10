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
package org.thingsboard.server.transport.lwm2m.ota.sql;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.device.credentials.lwm2m.NoSecClientCredential;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus;
import org.thingsboard.server.transport.lwm2m.ota.AbstractOtaLwM2MIntegrationTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.thingsboard.rest.client.utils.RestJsonConverter.toTimeseries;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.DOWNLOADED;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.DOWNLOADING;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.FAILED;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.INITIATED;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.QUEUED;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.UPDATED;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.UPDATING;
import static org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus.VERIFIED;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.COAP_CONFIG;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.SECURITY;

@Slf4j
public class OtaLwM2MIntegrationTest extends AbstractOtaLwM2MIntegrationTest {

    public static final int TIMEOUT = 30;
    private final String OTA_TRANSPORT_CONFIGURATION = "{\n" +
            "  \"type\": \"LWM2M\",\n" +
            "  \"observeAttr\": {\n" +
            "    \"keyName\": {\n" +
            "      \"/5_1.0/0/3\": \"state\",\n" +
            "      \"/5_1.0/0/5\": \"updateResult\",\n" +
            "      \"/5_1.0/0/6\": \"pkgname\",\n" +
            "      \"/5_1.0/0/7\": \"pkgversion\",\n" +
            "      \"/5_1.0/0/9\": \"firmwareUpdateDeliveryMethod\",\n" +
            "      \"/9_1.0/0/0\": \"pkgname\",\n" +
            "      \"/9_1.0/0/1\": \"pkgversion\",\n" +
            "      \"/9_1.0/0/7\": \"updateState\",\n" +
            "      \"/9_1.0/0/9\": \"updateResult\"\n" +
            "    },\n" +
            "    \"observe\": [\n" +
            "      \"/5_1.0/0/3\",\n" +
            "      \"/5_1.0/0/5\",\n" +
            "      \"/5_1.0/0/6\",\n" +
            "      \"/5_1.0/0/7\",\n" +
            "      \"/5_1.0/0/9\",\n" +
            "      \"/9_1.0/0/0\",\n" +
            "      \"/9_1.0/0/1\",\n" +
            "      \"/9_1.0/0/7\",\n" +
            "      \"/9_1.0/0/9\"\n" +
            "    ],\n" +
            "    \"attribute\": [],\n" +
            "    \"telemetry\": [\n" +
            "      \"/5_1.0/0/3\",\n" +
            "      \"/5_1.0/0/5\",\n" +
            "      \"/5_1.0/0/6\",\n" +
            "      \"/5_1.0/0/7\",\n" +
            "      \"/5_1.0/0/9\",\n" +
            "      \"/9_1.0/0/0\",\n" +
            "      \"/9_1.0/0/1\",\n" +
            "      \"/9_1.0/0/7\",\n" +
            "      \"/9_1.0/0/9\"\n" +
            "    ],\n" +
            "    \"attributeLwm2m\": {}\n" +
            "  },\n" +
            "  \"bootstrapServerUpdateEnable\": true,\n" +
            "  \"bootstrap\": [\n" +
            "    {\n" +
            "       \"host\": \"0.0.0.0\",\n" +
            "       \"port\": 5687,\n" +
            "       \"binding\": \"U\",\n" +
            "       \"lifetime\": 300,\n" +
            "       \"securityMode\": \"NO_SEC\",\n" +
            "       \"shortServerId\": 111,\n" +
            "       \"notifIfDisabled\": true,\n" +
            "       \"serverPublicKey\": \"\",\n" +
            "       \"defaultMinPeriod\": 1,\n" +
            "       \"bootstrapServerIs\": true,\n" +
            "       \"clientHoldOffTime\": 1,\n" +
            "       \"bootstrapServerAccountTimeout\": 0\n" +
            "    },\n" +
            "    {\n" +
            "       \"host\": \"0.0.0.0\",\n" +
            "       \"port\": 5685,\n" +
            "       \"binding\": \"U\",\n" +
            "       \"lifetime\": 300,\n" +
            "       \"securityMode\": \"NO_SEC\",\n" +
            "       \"shortServerId\": 123,\n" +
            "       \"notifIfDisabled\": true,\n" +
            "       \"serverPublicKey\": \"\",\n" +
            "       \"defaultMinPeriod\": 1,\n" +
            "       \"bootstrapServerIs\": false,\n" +
            "       \"clientHoldOffTime\": 1,\n" +
            "       \"bootstrapServerAccountTimeout\": 0\n" +
            "    }\n" +
            "  ],\n" +
            "  \"clientLwM2mSettings\": {\n" +
            "    \"edrxCycle\": null,\n" +
            "    \"powerMode\": \"DRX\",\n" +
            "    \"fwUpdateResource\": null,\n" +
            "    \"fwUpdateStrategy\": 1,\n" +
            "    \"psmActivityTimer\": null,\n" +
            "    \"swUpdateResource\": null,\n" +
            "    \"swUpdateStrategy\": 1,\n" +
            "    \"pagingTransmissionWindow\": null,\n" +
            "    \"clientOnlyObserveAfterConnect\": 1\n" +
            "  }\n" +
            "}";

    @Test
    public void testFirmwareUpdateWithClientWithoutFirmwareOtaInfoFromProfile() throws Exception {
        String endpoint = "WithoutFirmwareInfoDevice";
        setEndpoint(endpoint);
        createDeviceProfile(transportConfiguration);
        NoSecClientCredential credentials = createNoSecClientCredentials(endpoint);
        final Device device = createDevice(credentials);
        createNewClient(SECURITY, COAP_CONFIG, false);

        Thread.sleep(1000);

        device.setFirmwareId(createFirmware().getId());
        final Device savedDevice = doPost("/api/device", device, Device.class);

        Thread.sleep(1000);

        assertThat(savedDevice).as("saved device").isNotNull();
        assertThat(getDeviceFromAPI(device.getId().getId())).as("fetched device").isEqualTo(savedDevice);

        List<TsKvEntry> ts = toTimeseries(doGetAsyncTyped("/api/plugins/telemetry/DEVICE/" +
                savedDevice.getId().getId() + "/values/timeseries?keys=fw_state", new TypeReference<>() {}));
        List<OtaPackageUpdateStatus> statuses = ts.stream().map(KvEntry::getValueAsString).map(OtaPackageUpdateStatus::valueOf).collect(Collectors.toList());
        List<OtaPackageUpdateStatus> expectedStatuses = Collections.singletonList(FAILED);

        Assert.assertEquals(expectedStatuses, statuses);
    }

    @Test
    public void testFirmwareUpdateByObject5() throws Exception {
        String endpoint = "Ota5_Device";
        setEndpoint(endpoint);
        createDeviceProfile(OTA_TRANSPORT_CONFIGURATION);
        NoSecClientCredential credentials = createNoSecClientCredentials(endpoint);
        final Device device = createDevice(credentials);
        createNewClient(SECURITY, COAP_CONFIG, false);

        Thread.sleep(1000);

        device.setFirmwareId(createFirmware().getId());
        final Device savedDevice = doPost("/api/device", device, Device.class);

        Thread.sleep(1000);

        assertThat(savedDevice).as("saved device").isNotNull();
        assertThat(getDeviceFromAPI(device.getId().getId())).as("fetched device").isEqualTo(savedDevice);

        final List<OtaPackageUpdateStatus> expectedStatuses = Arrays.asList(QUEUED, INITIATED, DOWNLOADING, DOWNLOADED, UPDATING, UPDATED);
        List<TsKvEntry> ts = await("await on timeseries")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> toTimeseries(doGetAsyncTyped("/api/plugins/telemetry/DEVICE/" +
                        savedDevice.getId().getId() + "/values/timeseries?orderBy=ASC&keys=fw_state&startTs=0&endTs=" +
                        System.currentTimeMillis(), new TypeReference<>() {
                })), hasSize(expectedStatuses.size()));
        List<OtaPackageUpdateStatus> statuses = ts.stream().sorted(Comparator
                        .comparingLong(TsKvEntry::getTs)).map(KvEntry::getValueAsString)
                .map(OtaPackageUpdateStatus::valueOf)
                .collect(Collectors.toList());

        Assert.assertEquals(expectedStatuses, statuses);
    }

    /**
     * This is the example how to use the AWAITILITY instead Thread.sleep()
     * Test will finish as fast as possible, but will await until TIMEOUT if a build machine is busy or slow
     * Check the detailed log output to learn how Awaitility polling the API and when exactly expected result appears
     * */
    @Test
    public void testSoftwareUpdateByObject9() throws Exception {
        String endpoint = "Ota9_Device";
        setEndpoint(endpoint);
        createDeviceProfile(OTA_TRANSPORT_CONFIGURATION);
        NoSecClientCredential credentials = createNoSecClientCredentials(endpoint);
        final Device device = createDevice(credentials);
        createNewClient(SECURITY, COAP_CONFIG, false);

        Thread.sleep(1000);

        device.setSoftwareId(createSoftware().getId());
        final Device savedDevice = doPost("/api/device", device, Device.class); //sync call

        Thread.sleep(1000);

        assertThat(savedDevice).as("saved device").isNotNull();
        assertThat(getDeviceFromAPI(device.getId().getId())).as("fetched device").isEqualTo(savedDevice);

        final List<OtaPackageUpdateStatus> expectedStatuses = List.of(
                QUEUED, INITIATED, DOWNLOADING, DOWNLOADING, DOWNLOADING, DOWNLOADED, VERIFIED, UPDATED);
        log.warn("AWAIT atMost {} SECONDS on timeseries List<TsKvEntry> by API with list size {}...", TIMEOUT, expectedStatuses.size());
        List<TsKvEntry> ts = await("await on timeseries")
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> getSwStateTelemetryFromAPI(device.getId().getId()), hasSize(expectedStatuses.size()));
        log.warn("Got the ts: {}", ts);

        ts.sort(Comparator.comparingLong(TsKvEntry::getTs));
        log.warn("Ts ordered: {}", ts);
        ts.forEach((x) -> log.warn("ts: {        Thread.sleep(1000);} ", x));
        List<OtaPackageUpdateStatus> statuses = ts.stream().map(KvEntry::getValueAsString)
                .map(OtaPackageUpdateStatus::valueOf)
                .collect(Collectors.toList());
        log.warn("Converted ts to statuses: {}", statuses);

        assertThat(statuses).isEqualTo(expectedStatuses);
    }

    private Device getDeviceFromAPI(UUID deviceId) throws Exception {
        final Device device = doGet("/api/device/" + deviceId, Device.class);
        log.trace("Fetched device by API for deviceId {}, device is {}", deviceId, device);
        return device;
    }

    private List<TsKvEntry> getSwStateTelemetryFromAPI(UUID deviceId) throws Exception {
        final List<TsKvEntry> tsKvEntries = toTimeseries(doGetAsyncTyped("/api/plugins/telemetry/DEVICE/" + deviceId + "/values/timeseries?orderBy=ASC&keys=sw_state&startTs=0&endTs=" + System.currentTimeMillis(), new TypeReference<>() {
        }));
        log.warn("Fetched telemetry by API for deviceId {}, list size {}, tsKvEntries {}", deviceId, tsKvEntries.size(), tsKvEntries);
        return tsKvEntries;
    }
}
