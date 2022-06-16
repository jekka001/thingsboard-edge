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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileInfo;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.device.profile.DeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.JsonTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.MqttDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.ProtoTransportPayloadConfiguration;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.security.Authority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class BaseDeviceProfileControllerTest extends AbstractControllerTest {

    private IdComparator<DeviceProfile> idComparator = new IdComparator<>();
    private IdComparator<DeviceProfileInfo> deviceProfileInfoIdComparator = new IdComparator<>();

    private Tenant savedTenant;
    private User tenantAdmin;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        Assert.assertNotNull(savedTenant);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");
    }

    @After
    public void afterTest() throws Exception {
        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void testSaveDeviceProfile() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        Assert.assertNotNull(savedDeviceProfile);
        Assert.assertNotNull(savedDeviceProfile.getId());
        Assert.assertTrue(savedDeviceProfile.getCreatedTime() > 0);
        Assert.assertEquals(deviceProfile.getName(), savedDeviceProfile.getName());
        Assert.assertEquals(deviceProfile.getDescription(), savedDeviceProfile.getDescription());
        Assert.assertEquals(deviceProfile.getProfileData(), savedDeviceProfile.getProfileData());
        Assert.assertEquals(deviceProfile.isDefault(), savedDeviceProfile.isDefault());
        Assert.assertEquals(deviceProfile.getDefaultRuleChainId(), savedDeviceProfile.getDefaultRuleChainId());
        Assert.assertEquals(DeviceProfileProvisionType.DISABLED, savedDeviceProfile.getProvisionType());
        savedDeviceProfile.setName("New device profile");
        doPost("/api/deviceProfile", savedDeviceProfile, DeviceProfile.class);
        DeviceProfile foundDeviceProfile = doGet("/api/deviceProfile/"+savedDeviceProfile.getId().getId().toString(), DeviceProfile.class);
        Assert.assertEquals(savedDeviceProfile.getName(), foundDeviceProfile.getName());
    }

    @Test
    public void saveDeviceProfileWithViolationOfValidation() throws Exception {
        doPost("/api/deviceProfile", this.createDeviceProfile(RandomStringUtils.randomAlphabetic(300)))
                .andExpect(statusReason(containsString("length of name must be equal or less than 255")));
    }

    @Test
    public void testFindDeviceProfileById() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        DeviceProfile foundDeviceProfile = doGet("/api/deviceProfile/"+savedDeviceProfile.getId().getId().toString(), DeviceProfile.class);
        Assert.assertNotNull(foundDeviceProfile);
        Assert.assertEquals(savedDeviceProfile, foundDeviceProfile);
    }

    @Test
    public void testFindDeviceProfileInfoById() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        DeviceProfileInfo foundDeviceProfileInfo = doGet("/api/deviceProfileInfo/"+savedDeviceProfile.getId().getId().toString(), DeviceProfileInfo.class);
        Assert.assertNotNull(foundDeviceProfileInfo);
        Assert.assertEquals(savedDeviceProfile.getId(), foundDeviceProfileInfo.getId());
        Assert.assertEquals(savedDeviceProfile.getName(), foundDeviceProfileInfo.getName());
        Assert.assertEquals(savedDeviceProfile.getType(), foundDeviceProfileInfo.getType());
    }

    @Test
    public void testFindDefaultDeviceProfileInfo() throws Exception {
        DeviceProfileInfo foundDefaultDeviceProfileInfo = doGet("/api/deviceProfileInfo/default", DeviceProfileInfo.class);
        Assert.assertNotNull(foundDefaultDeviceProfileInfo);
        Assert.assertNotNull(foundDefaultDeviceProfileInfo.getId());
        Assert.assertNotNull(foundDefaultDeviceProfileInfo.getName());
        Assert.assertNotNull(foundDefaultDeviceProfileInfo.getType());
        Assert.assertEquals(DeviceProfileType.DEFAULT, foundDefaultDeviceProfileInfo.getType());
        Assert.assertEquals("default", foundDefaultDeviceProfileInfo.getName());
    }

    @Test
    public void testSetDefaultDeviceProfile() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile 1");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        DeviceProfile defaultDeviceProfile = doPost("/api/deviceProfile/"+savedDeviceProfile.getId().getId().toString()+"/default", DeviceProfile.class);
        Assert.assertNotNull(defaultDeviceProfile);
        DeviceProfileInfo foundDefaultDeviceProfile = doGet("/api/deviceProfileInfo/default", DeviceProfileInfo.class);
        Assert.assertNotNull(foundDefaultDeviceProfile);
        Assert.assertEquals(savedDeviceProfile.getName(), foundDefaultDeviceProfile.getName());
        Assert.assertEquals(savedDeviceProfile.getId(), foundDefaultDeviceProfile.getId());
        Assert.assertEquals(savedDeviceProfile.getType(), foundDefaultDeviceProfile.getType());
    }

    @Test
    public void testSaveDeviceProfileWithEmptyName() throws Exception {
        DeviceProfile deviceProfile = new DeviceProfile();
        doPost("/api/deviceProfile", deviceProfile).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Device profile name should be specified")));
    }

    @Test
    public void testSaveDeviceProfileWithSameName() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        doPost("/api/deviceProfile", deviceProfile).andExpect(status().isOk());
        DeviceProfile deviceProfile2 = this.createDeviceProfile("Device Profile");
        doPost("/api/deviceProfile", deviceProfile2).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Device profile with such name already exists")));
    }

    @Test
    public void testSaveDeviceProfileWithSameProvisionDeviceKey() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        deviceProfile.setProvisionDeviceKey("testProvisionDeviceKey");
        doPost("/api/deviceProfile", deviceProfile).andExpect(status().isOk());
        DeviceProfile deviceProfile2 = this.createDeviceProfile("Device Profile 2");
        deviceProfile2.setProvisionDeviceKey("testProvisionDeviceKey");
        doPost("/api/deviceProfile", deviceProfile2).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Device profile with such provision device key already exists")));
    }

    @Ignore
    @Test
    public void testChangeDeviceProfileTypeWithExistingDevices() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        Device device = new Device();
        device.setName("Test device");
        device.setType("default");
        device.setDeviceProfileId(savedDeviceProfile.getId());
        doPost("/api/device", device, Device.class);
        //TODO uncomment once we have other device types;
        //savedDeviceProfile.setType(DeviceProfileType.LWM2M);
        doPost("/api/deviceProfile", savedDeviceProfile).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Can't change device profile type because devices referenced it")));
    }

    @Test
    public void testChangeDeviceProfileTransportTypeWithExistingDevices() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        Device device = new Device();
        device.setName("Test device");
        device.setType("default");
        device.setDeviceProfileId(savedDeviceProfile.getId());
        doPost("/api/device", device, Device.class);
        savedDeviceProfile.setTransportType(DeviceTransportType.MQTT);
        doPost("/api/deviceProfile", savedDeviceProfile).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("Can't change device profile transport type because devices referenced it")));
    }

    @Test
    public void testDeleteDeviceProfileWithExistingDevice() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);

        Device device = new Device();
        device.setName("Test device");
        device.setType("default");
        device.setDeviceProfileId(savedDeviceProfile.getId());

        Device savedDevice = doPost("/api/device", device, Device.class);

        doDelete("/api/deviceProfile/" + savedDeviceProfile.getId().getId().toString())
                .andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString("The device profile referenced by the devices cannot be deleted")));
    }

    @Test
    public void testDeleteDeviceProfile() throws Exception {
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile");
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);

        doDelete("/api/deviceProfile/" + savedDeviceProfile.getId().getId().toString())
                .andExpect(status().isOk());

        doGet("/api/deviceProfile/" + savedDeviceProfile.getId().getId().toString())
                .andExpect(status().isNotFound());
    }

    @Test
    public void testFindDeviceProfiles() throws Exception {
        List<DeviceProfile> deviceProfiles = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<DeviceProfile> pageData = doGetTypedWithPageLink("/api/deviceProfiles?",
                new TypeReference<PageData<DeviceProfile>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getTotalElements());
        deviceProfiles.addAll(pageData.getData());

        for (int i=0;i<28;i++) {
            DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile"+i);
            deviceProfiles.add(doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class));
        }

        List<DeviceProfile> loadedDeviceProfiles = new ArrayList<>();
        pageLink = new PageLink(17);
        do {
            pageData = doGetTypedWithPageLink("/api/deviceProfiles?",
                    new TypeReference<PageData<DeviceProfile>>(){}, pageLink);
            loadedDeviceProfiles.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(deviceProfiles, idComparator);
        Collections.sort(loadedDeviceProfiles, idComparator);

        Assert.assertEquals(deviceProfiles, loadedDeviceProfiles);

        for (DeviceProfile deviceProfile : loadedDeviceProfiles) {
            if (!deviceProfile.isDefault()) {
                doDelete("/api/deviceProfile/" + deviceProfile.getId().getId().toString())
                        .andExpect(status().isOk());
            }
        }

        pageLink = new PageLink(17);
        pageData = doGetTypedWithPageLink("/api/deviceProfiles?",
                new TypeReference<PageData<DeviceProfile>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getTotalElements());
    }

    @Test
    public void testFindDeviceProfileInfos() throws Exception {
        List<DeviceProfile> deviceProfiles = new ArrayList<>();
        PageLink pageLink = new PageLink(17);
        PageData<DeviceProfile> deviceProfilePageData = doGetTypedWithPageLink("/api/deviceProfiles?",
                new TypeReference<PageData<DeviceProfile>>(){}, pageLink);
        Assert.assertFalse(deviceProfilePageData.hasNext());
        Assert.assertEquals(1, deviceProfilePageData.getTotalElements());
        deviceProfiles.addAll(deviceProfilePageData.getData());

        for (int i=0;i<28;i++) {
            DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile"+i);
            deviceProfiles.add(doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class));
        }

        List<DeviceProfileInfo> loadedDeviceProfileInfos = new ArrayList<>();
        pageLink = new PageLink(17);
        PageData<DeviceProfileInfo> pageData;
        do {
            pageData = doGetTypedWithPageLink("/api/deviceProfileInfos?",
                    new TypeReference<PageData<DeviceProfileInfo>>(){}, pageLink);
            loadedDeviceProfileInfos.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());

        Collections.sort(deviceProfiles, idComparator);
        Collections.sort(loadedDeviceProfileInfos, deviceProfileInfoIdComparator);

        List<DeviceProfileInfo> deviceProfileInfos = deviceProfiles.stream().map(deviceProfile -> new DeviceProfileInfo(deviceProfile.getId(),
                deviceProfile.getName(), deviceProfile.getImage(), deviceProfile.getDefaultDashboardId(),
                deviceProfile.getType(), deviceProfile.getTransportType())).collect(Collectors.toList());

        Assert.assertEquals(deviceProfileInfos, loadedDeviceProfileInfos);

        for (DeviceProfile deviceProfile : deviceProfiles) {
            if (!deviceProfile.isDefault()) {
                doDelete("/api/deviceProfile/" + deviceProfile.getId().getId().toString())
                        .andExpect(status().isOk());
            }
        }

        pageLink = new PageLink(17);
        pageData = doGetTypedWithPageLink("/api/deviceProfileInfos?",
                new TypeReference<PageData<DeviceProfileInfo>>(){}, pageLink);
        Assert.assertFalse(pageData.hasNext());
        Assert.assertEquals(1, pageData.getTotalElements());
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidProtoFile() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message SchemaValidationTest {\n" +
                "   required int32 parameter = 1;\n" +
                "}", "[Transport Configuration] failed to parse attributes proto schema due to: Syntax error in :6:4: 'required' label forbidden in proto3 field declarations");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidProtoSyntax() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto2\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message SchemaValidationTest {\n" +
                "   required int32 parameter = 1;\n" +
                "}", "[Transport Configuration] invalid schema syntax: proto2 for attributes proto schema provided! Only proto3 allowed!");
    }

    @Test
    public void testSaveProtoDeviceProfileOptionsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "option java_package = \"com.test.schemavalidation\";\n" +
                "option java_multiple_files = true;\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message SchemaValidationTest {\n" +
                "   optional int32 parameter = 1;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Schema options don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfilePublicImportsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "import public \"oldschema.proto\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message SchemaValidationTest {\n" +
                "   optional int32 parameter = 1;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Schema public imports don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileImportsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "import \"oldschema.proto\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message SchemaValidationTest {\n" +
                "   optional int32 parameter = 1;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Schema imports don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileExtendDeclarationsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "extend google.protobuf.MethodOptions {\n" +
                "  MyMessage my_method_option = 50007;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Schema extend declarations don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileEnumOptionsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "enum testEnum {\n" +
                "   option allow_alias = true;\n" +
                "   DEFAULT = 0;\n" +
                "   STARTED = 1;\n" +
                "   RUNNING = 2;\n" +
                "}\n" +
                "\n" +
                "message testMessage {\n" +
                "   optional int32 parameter = 1;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Enum definitions options are not supported!");
    }

    @Test
    public void testSaveProtoDeviceProfileNoOneMessageTypeExists() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "enum testEnum {\n" +
                "   DEFAULT = 0;\n" +
                "   STARTED = 1;\n" +
                "   RUNNING = 2;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! At least one Message definition should exists!");
    }

    @Test
    public void testSaveProtoDeviceProfileMessageTypeOptionsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message testMessage {\n" +
                "   option allow_alias = true;\n" +
                "   optional int32 parameter = 1;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Message definition options don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileMessageTypeExtensionsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message TestMessage {\n" +
                "   extensions 100 to 199;\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Message definition extensions don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileMessageTypeReservedElementsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message Foo {\n" +
                "  reserved 2, 15, 9 to 11;\n" +
                "  reserved \"foo\", \"bar\";\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Message definition reserved elements don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileMessageTypeGroupsElementsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message TestMessage {\n" +
                "  repeated group Result = 1 {\n" +
                "    optional string url = 2;\n" +
                "    optional string title = 3;\n" +
                "    repeated string snippets = 4;\n" +
                "  }\n" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! Message definition groups don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileOneOfsGroupsElementsNotSupported() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax = \"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message SampleMessage {\n" +
                "  oneof test_oneof {\n" +
                "     string name = 1;\n" +
                "     group Result = 2 {\n" +
                "    \tstring url = 3;\n" +
                "    \tstring title = 4;\n" +
                "    \trepeated string snippets = 5;\n" +
                "     }\n" +
                "  }" +
                "}", "[Transport Configuration] invalid attributes proto schema provided! OneOf definition groups don't support!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidTelemetrySchemaTsField() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message PostTelemetry {\n" +
                "  int64 ts = 1;\n" +
                "  Values values = 2;\n" +
                "  \n" +
                "  message Values {\n" +
                "    string key1 = 3;\n" +
                "    bool key2 = 4;\n" +
                "    double key3 = 5;\n" +
                "    int32 key4 = 6;\n" +
                "    JsonObject key5 = 7;\n" +
                "  }\n" +
                "  \n" +
                "  message JsonObject {\n" +
                "    optional int32 someNumber = 8;\n" +
                "    repeated int32 someArray = 9;\n" +
                "    NestedJsonObject someNestedObject = 10;\n" +
                "    message NestedJsonObject {\n" +
                "       optional string key = 11;\n" +
                "    }\n" +
                "  }\n" +
                "}", "[Transport Configuration] invalid telemetry proto schema provided! Field 'ts' has invalid label. Field 'ts' should have optional keyword!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidTelemetrySchemaTsDateType() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message PostTelemetry {\n" +
                "  optional int32 ts = 1;\n" +
                "  Values values = 2;\n" +
                "  \n" +
                "  message Values {\n" +
                "    string key1 = 3;\n" +
                "    bool key2 = 4;\n" +
                "    double key3 = 5;\n" +
                "    int32 key4 = 6;\n" +
                "    JsonObject key5 = 7;\n" +
                "  }\n" +
                "  \n" +
                "  message JsonObject {\n" +
                "    optional int32 someNumber = 8;\n" +
                "  }\n" +
                "}", "[Transport Configuration] invalid telemetry proto schema provided! Field 'ts' has invalid data type. Only int64 type is supported!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidTelemetrySchemaValuesDateType() throws Exception {
        testSaveDeviceProfileWithInvalidProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message PostTelemetry {\n" +
                "  optional int64 ts = 1;\n" +
                "  string values = 2;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid telemetry proto schema provided! Field 'values' has invalid data type. Only message type is supported!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaMethodDateType() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional int32 method = 1;\n" +
                "  optional int32 requestId = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Field 'method' has invalid data type. Only string type is supported!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaRequestIdDateType() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional string method = 1;\n" +
                "  optional int64 requestId = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Field 'requestId' has invalid data type. Only int32 type is supported!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaMethodLabel() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  repeated string method = 1;\n" +
                "  optional int32 requestId = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Field 'method' has invalid label!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaRequestIdLabel() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional string method = 1;\n" +
                "  repeated int32 requestId = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Field 'requestId' has invalid label!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaParamsLabel() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional string method = 1;\n" +
                "  optional int32 requestId = 2;\n" +
                "  repeated string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Field 'params' has invalid label!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaFieldsCount() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional int32 requestId = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! RpcRequestMsg message should always contains 3 fields: method, requestId and params!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaFieldMethodIsNoSet() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional string methodName = 1;\n" +
                "  optional int32 requestId = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Failed to get field descriptor for field: method!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaFieldRequestIdIsNotSet() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional string method = 1;\n" +
                "  optional int32 requestIdentifier = 2;\n" +
                "  optional string params = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Failed to get field descriptor for field: requestId!");
    }

    @Test
    public void testSaveProtoDeviceProfileWithInvalidRpcRequestSchemaFieldParamsIsNotSet() throws Exception {
        testSaveDeviceProfileWithInvalidRpcRequestProtoSchema("syntax =\"proto3\";\n" +
                "\n" +
                "package schemavalidation;\n" +
                "\n" +
                "message RpcRequestMsg {\n" +
                "  optional string method = 1;\n" +
                "  optional int32 requestId = 2;\n" +
                "  optional string parameters = 3;\n" +
                "  \n" +
                "}", "[Transport Configuration] invalid rpc request proto schema provided! Failed to get field descriptor for field: params!");
    }

    @Test
    public void testSaveDeviceProfileWithSendAckOnValidationException() throws Exception {
        JsonTransportPayloadConfiguration jsonTransportPayloadConfiguration = new JsonTransportPayloadConfiguration();
        MqttDeviceProfileTransportConfiguration mqttDeviceProfileTransportConfiguration = this.createMqttDeviceProfileTransportConfiguration(jsonTransportPayloadConfiguration, true);
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile", mqttDeviceProfileTransportConfiguration);
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        Assert.assertNotNull(savedDeviceProfile);
        Assert.assertEquals(savedDeviceProfile.getTransportType(), DeviceTransportType.MQTT);
        Assert.assertTrue(savedDeviceProfile.getProfileData().getTransportConfiguration() instanceof MqttDeviceProfileTransportConfiguration);
        MqttDeviceProfileTransportConfiguration transportConfiguration = (MqttDeviceProfileTransportConfiguration) savedDeviceProfile.getProfileData().getTransportConfiguration();
        Assert.assertTrue(transportConfiguration.isSendAckOnValidationException());
        DeviceProfile foundDeviceProfile = doGet("/api/deviceProfile/"+ savedDeviceProfile.getId().getId().toString(), DeviceProfile.class);
        Assert.assertEquals(savedDeviceProfile, foundDeviceProfile);
    }

    private DeviceProfile testSaveDeviceProfileWithProtoPayloadType(String schema) throws Exception {
        ProtoTransportPayloadConfiguration protoTransportPayloadConfiguration = this.createProtoTransportPayloadConfiguration(schema, schema, null, null);
        MqttDeviceProfileTransportConfiguration mqttDeviceProfileTransportConfiguration = this.createMqttDeviceProfileTransportConfiguration(protoTransportPayloadConfiguration, false);
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile", mqttDeviceProfileTransportConfiguration);
        DeviceProfile savedDeviceProfile = doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
        Assert.assertNotNull(savedDeviceProfile);
        DeviceProfile foundDeviceProfile = doGet("/api/deviceProfile/"+ savedDeviceProfile.getId().getId().toString(), DeviceProfile.class);
        Assert.assertEquals(savedDeviceProfile, foundDeviceProfile);
        return savedDeviceProfile;
    }

    private void testSaveDeviceProfileWithInvalidProtoSchema(String schema, String errorMsg) throws Exception {
        ProtoTransportPayloadConfiguration protoTransportPayloadConfiguration = this.createProtoTransportPayloadConfiguration(schema, schema, null, null);
        MqttDeviceProfileTransportConfiguration mqttDeviceProfileTransportConfiguration = this.createMqttDeviceProfileTransportConfiguration(protoTransportPayloadConfiguration, false);
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile", mqttDeviceProfileTransportConfiguration);
        doPost("/api/deviceProfile", deviceProfile).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(errorMsg)));
    }

    private void testSaveDeviceProfileWithInvalidRpcRequestProtoSchema(String schema, String errorMsg) throws Exception {
        ProtoTransportPayloadConfiguration protoTransportPayloadConfiguration = this.createProtoTransportPayloadConfiguration(schema, schema, schema, null);
        MqttDeviceProfileTransportConfiguration mqttDeviceProfileTransportConfiguration = this.createMqttDeviceProfileTransportConfiguration(protoTransportPayloadConfiguration, false);
        DeviceProfile deviceProfile = this.createDeviceProfile("Device Profile", mqttDeviceProfileTransportConfiguration);
        doPost("/api/deviceProfile", deviceProfile).andExpect(status().isBadRequest())
                .andExpect(statusReason(containsString(errorMsg)));
    }

}
