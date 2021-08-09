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
package org.thingsboard.server.edge;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.device.profile.AlarmCondition;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilter;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilterKey;
import org.thingsboard.server.common.data.device.profile.AlarmConditionKeyType;
import org.thingsboard.server.common.data.device.profile.AlarmRule;
import org.thingsboard.server.common.data.device.profile.AllowCreateNewDevicesDeviceProfileProvisionConfiguration;
import org.thingsboard.server.common.data.device.profile.DeviceProfileAlarm;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.device.profile.SimpleAlarmConditionSpec;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.NumericFilterPredicate;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.edge.EdgeEventService;
import org.thingsboard.server.edge.imitator.EdgeImitator;
import org.thingsboard.server.gen.edge.v1.AdminSettingsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AttributeDeleteMsg;
import org.thingsboard.server.gen.edge.v1.AttributesRequestMsg;
import org.thingsboard.server.gen.edge.v1.CustomTranslationProto;
import org.thingsboard.server.gen.edge.v1.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeConfiguration;
import org.thingsboard.server.gen.edge.v1.EntityDataProto;
import org.thingsboard.server.gen.edge.v1.EntityGroupRequestMsg;
import org.thingsboard.server.gen.edge.v1.EntityGroupUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.v1.LoginWhiteLabelingParamsProto;
import org.thingsboard.server.gen.edge.v1.RelationRequestMsg;
import org.thingsboard.server.gen.edge.v1.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RoleProto;
import org.thingsboard.server.gen.edge.v1.RpcResponseMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataRequestMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.gen.edge.v1.UserCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.v1.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WhiteLabelingParamsProto;
import org.thingsboard.server.gen.edge.v1.WidgetTypeUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WidgetsBundleUpdateMsg;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.cluster.TbClusterService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
abstract public class BaseEdgeTest extends AbstractControllerTest {

    private static final String CUSTOM_DEVICE_PROFILE_NAME = "Thermostat";

    private Tenant savedTenant;
    private TenantId tenantId;
    private User tenantAdmin;

    private Device globalTestDevice;
    private Asset globalTestAsset;

    private EdgeImitator edgeImitator;
    private Edge edge;

    @Autowired
    private EdgeEventService edgeEventService;

    @Autowired
    private TbClusterService clusterService;

    @Before
    public void beforeTest() throws Exception {
        loginSysAdmin();

        Tenant tenant = new Tenant();
        tenant.setTitle("My tenant");
        savedTenant = doPost("/api/tenant", tenant, Tenant.class);
        tenantId = savedTenant.getId();
        Assert.assertNotNull(savedTenant);

        CustomTranslation content = new CustomTranslation();
        content.getTranslationMap().put("key", "sys_admin_value");
        doPost("/api/customTranslation/customTranslation", content, CustomTranslation.class);
        WhiteLabelingParams whiteLabelingParams = new WhiteLabelingParams();
        whiteLabelingParams.setAppTitle("Sys Admin TB");
        doPost("/api/whiteLabel/whiteLabelParams", whiteLabelingParams, WhiteLabelingParams.class);
        LoginWhiteLabelingParams loginWhiteLabelingParams = new LoginWhiteLabelingParams();
        loginWhiteLabelingParams.setDomainName("sysadmin.org");
        doPost("/api/whiteLabel/loginWhiteLabelParams", loginWhiteLabelingParams, LoginWhiteLabelingParams.class);

        tenantAdmin = new User();
        tenantAdmin.setAuthority(Authority.TENANT_ADMIN);
        tenantAdmin.setTenantId(savedTenant.getId());
        tenantAdmin.setEmail("tenant2@thingsboard.org");
        tenantAdmin.setFirstName("Joe");
        tenantAdmin.setLastName("Downs");

        tenantAdmin = createUserAndLogin(tenantAdmin, "testPassword1");

        content = new CustomTranslation();
        content.getTranslationMap().put("key", "tenant_value");
        doPost("/api/customTranslation/customTranslation", content, CustomTranslation.class);
        whiteLabelingParams = new WhiteLabelingParams();
        whiteLabelingParams.setAppTitle("Tenant TB");
        doPost("/api/whiteLabel/whiteLabelParams", whiteLabelingParams, WhiteLabelingParams.class);
        loginWhiteLabelingParams = new LoginWhiteLabelingParams();
        loginWhiteLabelingParams.setDomainName("tenant.org");
        doPost("/api/whiteLabel/loginWhiteLabelParams", loginWhiteLabelingParams, LoginWhiteLabelingParams.class);

        // sleep 1 seconds to avoid CREDENTIALS updated message for the user
        // user credentials is going to be stored and updated event pushed to edge notification service
        // while service will be processing this event edge could be already added and additional message will be pushed
        Thread.sleep(500);

        installation();

        edgeImitator = new EdgeImitator("localhost", 7070, edge.getRoutingKey(), edge.getSecret());
        edgeImitator.expectMessageAmount(17);
        edgeImitator.connect();

        testReceivedInitialData();
    }

    @After
    public void afterTest() throws Exception {
        try {
            edgeImitator.disconnect();
        } catch (Exception ignored) {}

        loginSysAdmin();

        doDelete("/api/tenant/" + savedTenant.getId().getId().toString())
                .andExpect(status().isOk());
    }

    @Test
    public void test() throws Exception {
        testDevices();

        testAssets();

        testRuleChains();

        testDashboards();

        testRelations();

        testAlarms();

        testEntityView();

        // TODO
//        changeOwnerToCustomer();

        testWidgetsBundleAndWidgetType();

        testTimeseries();

        testAttributes();

        testRpcCall();

        testTimeseriesWithFailures();

        testSendMessagesToCloud();
    }

    private void installation() throws Exception {
        edge = doPost("/api/edge", constructEdge("Test Edge", "test"), Edge.class);

        DeviceProfile deviceProfile = this.createDeviceProfile(CUSTOM_DEVICE_PROFILE_NAME, null);
        extendDeviceProfileData(deviceProfile);
        doPost("/api/deviceProfile", deviceProfile, DeviceProfile.class);
    }

    private void extendDeviceProfileData(DeviceProfile deviceProfile) {
        DeviceProfileData profileData = deviceProfile.getProfileData();
        List<DeviceProfileAlarm> alarms = new ArrayList<>();
        DeviceProfileAlarm deviceProfileAlarm = new DeviceProfileAlarm();
        deviceProfileAlarm.setAlarmType("High Temperature");
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmDetails("Alarm Details");
        AlarmCondition alarmCondition = new AlarmCondition();
        alarmCondition.setSpec(new SimpleAlarmConditionSpec());
        List<AlarmConditionFilter> condition = new ArrayList<>();
        AlarmConditionFilter alarmConditionFilter = new AlarmConditionFilter();
        alarmConditionFilter.setKey(new AlarmConditionFilterKey(AlarmConditionKeyType.ATTRIBUTE, "temperature"));
        NumericFilterPredicate predicate = new NumericFilterPredicate();
        predicate.setOperation(NumericFilterPredicate.NumericOperation.GREATER);
        predicate.setValue(new FilterPredicateValue<>(55.0));
        alarmConditionFilter.setPredicate(predicate);
        alarmConditionFilter.setValueType(EntityKeyValueType.NUMERIC);
        condition.add(alarmConditionFilter);
        alarmCondition.setCondition(condition);
        alarmRule.setCondition(alarmCondition);
        deviceProfileAlarm.setClearRule(alarmRule);
        TreeMap<AlarmSeverity, AlarmRule> createRules = new TreeMap<>();
        createRules.put(AlarmSeverity.CRITICAL, alarmRule);
        deviceProfileAlarm.setCreateRules(createRules);
        alarms.add(deviceProfileAlarm);
        profileData.setAlarms(alarms);
        profileData.setProvisionConfiguration(new AllowCreateNewDevicesDeviceProfileProvisionConfiguration("123"));
    }

    private void testReceivedInitialData() throws Exception {
        log.info("Checking received data");
        Assert.assertTrue(edgeImitator.waitForMessages());

        EdgeConfiguration configuration = edgeImitator.getConfiguration();
        Assert.assertNotNull(configuration);

        testAutoGeneratedCodeByProtobuf(configuration);

        List<DeviceProfileUpdateMsg> deviceProfileUpdateMsgList = edgeImitator.findAllMessagesByType(DeviceProfileUpdateMsg.class);
        Assert.assertEquals(3, deviceProfileUpdateMsgList.size());
        Optional<DeviceProfileUpdateMsg> deviceProfileUpdateMsgOpt =
                deviceProfileUpdateMsgList.stream().filter(dfum -> CUSTOM_DEVICE_PROFILE_NAME.equals(dfum.getName())).findAny();
        Assert.assertTrue(deviceProfileUpdateMsgOpt.isPresent());
        DeviceProfileUpdateMsg deviceProfileUpdateMsg = deviceProfileUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, deviceProfileUpdateMsg.getMsgType());
        UUID deviceProfileUUID = new UUID(deviceProfileUpdateMsg.getIdMSB(), deviceProfileUpdateMsg.getIdLSB());
        DeviceProfile deviceProfile = doGet("/api/deviceProfile/" + deviceProfileUUID.toString(), DeviceProfile.class);
        Assert.assertNotNull(deviceProfile);
        Assert.assertNotNull(deviceProfile.getProfileData());
        Assert.assertNotNull(deviceProfile.getProfileData().getAlarms());
        Assert.assertNotNull(deviceProfile.getProfileData().getAlarms().get(0).getClearRule());

        testAutoGeneratedCodeByProtobuf(deviceProfileUpdateMsg);

        Optional<RuleChainUpdateMsg> ruleChainUpdateMsgOpt = edgeImitator.findMessageByType(RuleChainUpdateMsg.class);
        Assert.assertTrue(ruleChainUpdateMsgOpt.isPresent());
        RuleChainUpdateMsg ruleChainUpdateMsg = ruleChainUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, ruleChainUpdateMsg.getMsgType());
        UUID ruleChainUUID = new UUID(ruleChainUpdateMsg.getIdMSB(), ruleChainUpdateMsg.getIdLSB());
        RuleChain ruleChain = doGet("/api/ruleChain/" + ruleChainUUID.toString(), RuleChain.class);
        Assert.assertNotNull(ruleChain);
        List<RuleChain> edgeRuleChains = doGetTypedWithPageLink("/api/edge/" + edge.getId().getId().toString() + "/ruleChains?",
                new TypeReference<PageData<RuleChain>>() {}, new PageLink(100)).getData();
        Assert.assertTrue(edgeRuleChains.contains(ruleChain));

        testAutoGeneratedCodeByProtobuf(ruleChainUpdateMsg);

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgList = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(2, entityGroupUpdateMsgList.size());

        List<LoginWhiteLabelingParamsProto> loginWlpUpdateMsgList = edgeImitator.findAllMessagesByType(LoginWhiteLabelingParamsProto.class);
        Assert.assertEquals(2, loginWlpUpdateMsgList.size());

        List<WhiteLabelingParamsProto> wlpUpdateMsgList = edgeImitator.findAllMessagesByType(WhiteLabelingParamsProto.class);
        Assert.assertEquals(2, wlpUpdateMsgList.size());

        List<CustomTranslationProto> customTranslationProtoList = edgeImitator.findAllMessagesByType(CustomTranslationProto.class);
        Assert.assertEquals(2, customTranslationProtoList.size());

        List<AdminSettingsUpdateMsg> adminSettingsUpdateMsgList = edgeImitator.findAllMessagesByType(AdminSettingsUpdateMsg.class);
        Assert.assertEquals(2, adminSettingsUpdateMsgList.size());

        List<RoleProto> roleProtoList = edgeImitator.findAllMessagesByType(RoleProto.class);
        Assert.assertEquals(2, roleProtoList.size());

        List<RuleChainUpdateMsg> ruleChainUpdateMsgList = edgeImitator.findAllMessagesByType(RuleChainUpdateMsg.class);
        Assert.assertEquals(2, ruleChainUpdateMsgList.size());

        log.info("Received data checked");
    }

    private void testDevices() throws Exception {
        log.info("Testing devices");

        // 1
        edgeImitator.expectMessageAmount(1);
        EntityGroup deviceEntityGroup = new EntityGroup();
        deviceEntityGroup.setType(EntityType.DEVICE);
        deviceEntityGroup.setName("DeviceGroup");
        deviceEntityGroup = doPost("/api/entityGroup", deviceEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + deviceEntityGroup.getId().toString() + "/" + EntityType.DEVICE.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(entityGroupUpdateMsg.getIdMSB(), deviceEntityGroup.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getIdLSB(), deviceEntityGroup.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getName(), deviceEntityGroup.getName());
        Assert.assertEquals(entityGroupUpdateMsg.getType(), deviceEntityGroup.getType().name());
        testAutoGeneratedCodeByProtobuf(entityGroupUpdateMsg);

        // 2
        edgeImitator.expectMessageAmount(1);
        Device savedDevice = saveDevice("Edge Device 1", CUSTOM_DEVICE_PROFILE_NAME, deviceEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        DeviceUpdateMsg deviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        Assert.assertEquals(deviceUpdateMsg.getIdMSB(), savedDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getIdLSB(), savedDevice.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getName(), savedDevice.getName());
        Assert.assertEquals(deviceUpdateMsg.getType(), savedDevice.getType());

        // 3
        testDeviceEntityGroupRequestMsg(entityGroupUpdateMsg.getIdMSB(), entityGroupUpdateMsg.getIdLSB(), savedDevice.getId());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/device/" + savedDevice.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        deviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, deviceUpdateMsg.getMsgType());
        Assert.assertEquals(deviceUpdateMsg.getIdMSB(), savedDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceUpdateMsg.getIdLSB(), savedDevice.getUuidId().getLeastSignificantBits());

        // 5 - for relations and entity view tests
        edgeImitator.expectMessageAmount(1);
        globalTestDevice = saveDevice("Edge Device 1", CUSTOM_DEVICE_PROFILE_NAME, deviceEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        log.info("Devices tested successfully");
    }

    private void testDeviceEntityGroupRequestMsg(long msbId, long lsbId, DeviceId expectedDeviceId) throws Exception {
        EntityGroupRequestMsg.Builder deviceEntitiesGroupRequestMsgBuilder = EntityGroupRequestMsg.newBuilder()
                .setEntityGroupIdMSB(msbId)
                .setEntityGroupIdLSB(lsbId)
                .setType(EntityType.DEVICE.name());
        testAutoGeneratedCodeByProtobuf(deviceEntitiesGroupRequestMsgBuilder);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addEntityGroupEntitiesRequestMsg(deviceEntitiesGroupRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        DeviceUpdateMsg deviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        DeviceId receivedDeviceId =
                new DeviceId(new UUID(deviceUpdateMsg.getIdMSB(), deviceUpdateMsg.getIdLSB()));
        Assert.assertEquals(expectedDeviceId, receivedDeviceId);

        testAutoGeneratedCodeByProtobuf(deviceUpdateMsg);
    }

    private void testAssets() throws Exception {
        log.info("Testing assets");

        // 1
        edgeImitator.expectMessageAmount(1);
        EntityGroup assetEntityGroup = new EntityGroup();
        assetEntityGroup.setType(EntityType.ASSET);
        assetEntityGroup.setName("AssetGroup");
        assetEntityGroup = doPost("/api/entityGroup", assetEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + assetEntityGroup.getId().toString() + "/" + EntityType.ASSET.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(entityGroupUpdateMsg.getIdMSB(), assetEntityGroup.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getIdLSB(), assetEntityGroup.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getName(), assetEntityGroup.getName());
        Assert.assertEquals(entityGroupUpdateMsg.getType(), assetEntityGroup.getType().name());
        testAutoGeneratedCodeByProtobuf(entityGroupUpdateMsg);

        // 2
        edgeImitator.expectMessageAmount(1);
        Asset savedAsset = saveAsset("Edge Asset 1", "Building", assetEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetUpdateMsg);
        AssetUpdateMsg assetUpdateMsg = (AssetUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, assetUpdateMsg.getMsgType());
        Assert.assertEquals(assetUpdateMsg.getName(), savedAsset.getName());
        Assert.assertEquals(assetUpdateMsg.getType(), savedAsset.getType());
        Assert.assertEquals(assetUpdateMsg.getIdMSB(), savedAsset.getUuidId().getMostSignificantBits());
        Assert.assertEquals(assetUpdateMsg.getIdLSB(), savedAsset.getUuidId().getLeastSignificantBits());

        // 3
        testAssetEntityGroupRequestMsg(entityGroupUpdateMsg.getIdMSB(), entityGroupUpdateMsg.getIdLSB(), savedAsset.getId());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/asset/" + savedAsset.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetUpdateMsg);
        assetUpdateMsg = (AssetUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, assetUpdateMsg.getMsgType());
        Assert.assertEquals(assetUpdateMsg.getIdMSB(), savedAsset.getUuidId().getMostSignificantBits());
        Assert.assertEquals(assetUpdateMsg.getIdLSB(), savedAsset.getUuidId().getLeastSignificantBits());

        // 5 - for relations and entity view tests
        edgeImitator.expectMessageAmount(1);
        globalTestAsset = saveAsset("Edge Asset 1", "Building", assetEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        log.info("Assets tested successfully");
    }

    private void testAssetEntityGroupRequestMsg(long msbId, long lsbId, AssetId expectedAssetId) throws Exception {
        EntityGroupRequestMsg.Builder entitiesGroupRequestMsgBuilder = EntityGroupRequestMsg.newBuilder()
                .setEntityGroupIdMSB(msbId)
                .setEntityGroupIdLSB(lsbId)
                .setType(EntityType.ASSET.name());
        testAutoGeneratedCodeByProtobuf(entitiesGroupRequestMsgBuilder);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addEntityGroupEntitiesRequestMsg(entitiesGroupRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AssetUpdateMsg);
        AssetUpdateMsg assetUpdateMsg = (AssetUpdateMsg) latestMessage;
        AssetId receivedAssetId =
                new AssetId(new UUID(assetUpdateMsg.getIdMSB(), assetUpdateMsg.getIdLSB()));
        Assert.assertEquals(expectedAssetId, receivedAssetId);

        testAutoGeneratedCodeByProtobuf(assetUpdateMsg);
    }

    private void testRuleChains() throws Exception {
        log.info("Testing RuleChains");

        // 1
        edgeImitator.expectMessageAmount(2);
        RuleChain ruleChain = new RuleChain();
        ruleChain.setName("Edge Test Rule Chain");
        ruleChain.setType(RuleChainType.EDGE);
        RuleChain savedRuleChain = doPost("/api/ruleChain", ruleChain, RuleChain.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/ruleChain/" + savedRuleChain.getId().getId().toString(), RuleChain.class);
        createRuleChainMetadata(savedRuleChain);
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<RuleChainUpdateMsg> ruleChainUpdateMsgOpt = edgeImitator.findMessageByType(RuleChainUpdateMsg.class);
        Assert.assertTrue(ruleChainUpdateMsgOpt.isPresent());
        RuleChainUpdateMsg ruleChainUpdateMsg = ruleChainUpdateMsgOpt.get();
        Assert.assertTrue(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE.equals(ruleChainUpdateMsg.getMsgType()) ||
                UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE.equals(ruleChainUpdateMsg.getMsgType()));
        Assert.assertEquals(ruleChainUpdateMsg.getIdMSB(), savedRuleChain.getUuidId().getMostSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getIdLSB(), savedRuleChain.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getName(), savedRuleChain.getName());

        // 2
        testRuleChainMetadataRequestMsg(savedRuleChain.getId());

        // 3
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getId().getId().toString()
                + "/ruleChain/" + savedRuleChain.getId().getId().toString(), RuleChain.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        ruleChainUpdateMsgOpt = edgeImitator.findMessageByType(RuleChainUpdateMsg.class);
        Assert.assertTrue(ruleChainUpdateMsgOpt.isPresent());
        ruleChainUpdateMsg = ruleChainUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, ruleChainUpdateMsg.getMsgType());
        Assert.assertEquals(ruleChainUpdateMsg.getIdMSB(), savedRuleChain.getUuidId().getMostSignificantBits());
        Assert.assertEquals(ruleChainUpdateMsg.getIdLSB(), savedRuleChain.getUuidId().getLeastSignificantBits());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/ruleChain/" + savedRuleChain.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertFalse(edgeImitator.waitForMessages(1));

        log.info("RuleChains tested successfully");
    }

    private void testRuleChainMetadataRequestMsg(RuleChainId ruleChainId) throws Exception {
        RuleChainMetadataRequestMsg.Builder ruleChainMetadataRequestMsgBuilder = RuleChainMetadataRequestMsg.newBuilder()
                .setRuleChainIdMSB(ruleChainId.getId().getMostSignificantBits())
                .setRuleChainIdLSB(ruleChainId.getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(ruleChainMetadataRequestMsgBuilder);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addRuleChainMetadataRequestMsg(ruleChainMetadataRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RuleChainMetadataUpdateMsg);
        RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg = (RuleChainMetadataUpdateMsg) latestMessage;
        RuleChainId receivedRuleChainId =
                new RuleChainId(new UUID(ruleChainMetadataUpdateMsg.getRuleChainIdMSB(), ruleChainMetadataUpdateMsg.getRuleChainIdLSB()));
        Assert.assertEquals(ruleChainId, receivedRuleChainId);
    }

    private void createRuleChainMetadata(RuleChain ruleChain) throws Exception {
        RuleChainMetaData ruleChainMetaData = new RuleChainMetaData();
        ruleChainMetaData.setRuleChainId(ruleChain.getId());

        ObjectMapper mapper = new ObjectMapper();

        RuleNode ruleNode1 = new RuleNode();
        ruleNode1.setName("name1");
        ruleNode1.setType("type1");
        ruleNode1.setConfiguration(mapper.readTree("\"key1\": \"val1\""));

        RuleNode ruleNode2 = new RuleNode();
        ruleNode2.setName("name2");
        ruleNode2.setType("type2");
        ruleNode2.setConfiguration(mapper.readTree("\"key2\": \"val2\""));

        RuleNode ruleNode3 = new RuleNode();
        ruleNode3.setName("name3");
        ruleNode3.setType("type3");
        ruleNode3.setConfiguration(mapper.readTree("\"key3\": \"val3\""));

        List<RuleNode> ruleNodes = new ArrayList<>();
        ruleNodes.add(ruleNode1);
        ruleNodes.add(ruleNode2);
        ruleNodes.add(ruleNode3);
        ruleChainMetaData.setFirstNodeIndex(0);
        ruleChainMetaData.setNodes(ruleNodes);

        ruleChainMetaData.addConnectionInfo(0, 1, "success");
        ruleChainMetaData.addConnectionInfo(0, 2, "fail");
        ruleChainMetaData.addConnectionInfo(1, 2, "success");

        ruleChainMetaData.addRuleChainConnectionInfo(2, edge.getRootRuleChainId(), "success", mapper.createObjectNode());

        doPost("/api/ruleChain/metadata", ruleChainMetaData, RuleChainMetaData.class);
    }

    private void testDashboards() throws Exception {
        log.info("Testing Dashboards");

        // 1
        edgeImitator.expectMessageAmount(1);
        EntityGroup dashboardEntityGroup = new EntityGroup();
        dashboardEntityGroup.setType(EntityType.DASHBOARD);
        dashboardEntityGroup.setName("DashboardGroup");
        dashboardEntityGroup = doPost("/api/entityGroup", dashboardEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + dashboardEntityGroup.getId().toString() + "/" + EntityType.DASHBOARD.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(entityGroupUpdateMsg.getIdMSB(), dashboardEntityGroup.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getIdLSB(), dashboardEntityGroup.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getName(), dashboardEntityGroup.getName());
        Assert.assertEquals(entityGroupUpdateMsg.getType(), dashboardEntityGroup.getType().name());
        testAutoGeneratedCodeByProtobuf(entityGroupUpdateMsg);

        // 2
        edgeImitator.expectMessageAmount(1);
        Dashboard savedDashboard = saveDashboard("Edge Dashboard 1", dashboardEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        DashboardUpdateMsg dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardUpdateMsg.getIdMSB(), savedDashboard.getUuidId().getMostSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getIdLSB(), savedDashboard.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getTitle(), savedDashboard.getTitle());

        // 3
        testDashboardEntityGroupRequestMsg(entityGroupUpdateMsg.getIdMSB(), entityGroupUpdateMsg.getIdLSB(), savedDashboard.getId());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/dashboard/" + savedDashboard.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, dashboardUpdateMsg.getMsgType());
        Assert.assertEquals(dashboardUpdateMsg.getIdMSB(), savedDashboard.getUuidId().getMostSignificantBits());
        Assert.assertEquals(dashboardUpdateMsg.getIdLSB(), savedDashboard.getUuidId().getLeastSignificantBits());

        log.info("Dashboards tested successfully");
    }

    private void testDashboardEntityGroupRequestMsg(long msbId, long lsbId, DashboardId expectedDashboardId) throws Exception {
        EntityGroupRequestMsg.Builder entitiesGroupRequestMsgBuilder = EntityGroupRequestMsg.newBuilder()
                .setEntityGroupIdMSB(msbId)
                .setEntityGroupIdLSB(lsbId)
                .setType(EntityType.DASHBOARD.name());
        testAutoGeneratedCodeByProtobuf(entitiesGroupRequestMsgBuilder);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addEntityGroupEntitiesRequestMsg(entitiesGroupRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DashboardUpdateMsg);
        DashboardUpdateMsg dashboardUpdateMsg = (DashboardUpdateMsg) latestMessage;
        DashboardId receivedDashboardId =
                new DashboardId(new UUID(dashboardUpdateMsg.getIdMSB(), dashboardUpdateMsg.getIdLSB()));
        Assert.assertEquals(expectedDashboardId, receivedDashboardId);

        testAutoGeneratedCodeByProtobuf(dashboardUpdateMsg);
    }

    private void testRelations() throws Exception {
        log.info("Testing Relations");

        // 1
        edgeImitator.expectMessageAmount(1);
        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(globalTestDevice.getId());
        relation.setTo(globalTestAsset.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        doPost("/api/relation", relation);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        RelationUpdateMsg relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
        Assert.assertEquals(relationUpdateMsg.getType(), relation.getType());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getFromIdLSB(), relation.getFrom().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToIdLSB(), relation.getTo().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getTypeGroup(), relation.getTypeGroup().name());

        // 2
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/relation?" +
                "fromId=" + relation.getFrom().getId().toString() +
                "&fromType=" + relation.getFrom().getEntityType().name() +
                "&relationType=" + relation.getType() +
                "&relationTypeGroup=" + relation.getTypeGroup().name() +
                "&toId=" + relation.getTo().getId().toString() +
                "&toType=" + relation.getTo().getEntityType().name())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
        Assert.assertEquals(relationUpdateMsg.getType(), relation.getType());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getFromIdLSB(), relation.getFrom().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getFromIdMSB(), relation.getFrom().getId().getMostSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToIdLSB(), relation.getTo().getId().getLeastSignificantBits());
        Assert.assertEquals(relationUpdateMsg.getToEntityType(), relation.getTo().getEntityType().name());
        Assert.assertEquals(relationUpdateMsg.getTypeGroup(), relation.getTypeGroup().name());

        log.info("Relations tested successfully");
    }

    private void testAlarms() throws Exception {
        log.info("Testing Alarms");

        // 1
        edgeImitator.expectMessageAmount(1);
        Alarm alarm = new Alarm();
        alarm.setOriginator(globalTestDevice.getId());
        alarm.setStatus(AlarmStatus.ACTIVE_UNACK);
        alarm.setType("alarm");
        alarm.setSeverity(AlarmSeverity.CRITICAL);
        Alarm savedAlarm = doPost("/api/alarm", alarm, Alarm.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        AlarmUpdateMsg alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), globalTestDevice.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), savedAlarm.getStatus().name());
        Assert.assertEquals(alarmUpdateMsg.getSeverity(), savedAlarm.getSeverity().name());

        // 2
        edgeImitator.expectMessageAmount(1);
        doPost("/api/alarm/" + savedAlarm.getId().getId().toString() + "/ack");
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ALARM_ACK_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), globalTestDevice.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), AlarmStatus.ACTIVE_ACK.name());

        // 3
        edgeImitator.expectMessageAmount(1);
        doPost("/api/alarm/" + savedAlarm.getId().getId().toString() + "/clear");
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ALARM_CLEAR_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), globalTestDevice.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), AlarmStatus.CLEARED_ACK.name());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/alarm/" + savedAlarm.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages(1));
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof AlarmUpdateMsg);
        alarmUpdateMsg = (AlarmUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, alarmUpdateMsg.getMsgType());
        Assert.assertEquals(alarmUpdateMsg.getType(), savedAlarm.getType());
        Assert.assertEquals(alarmUpdateMsg.getName(), savedAlarm.getName());
        Assert.assertEquals(alarmUpdateMsg.getOriginatorName(), globalTestDevice.getName());
        Assert.assertEquals(alarmUpdateMsg.getStatus(), AlarmStatus.CLEARED_ACK.name());

        log.info("Alarms tested successfully");
    }

    private void testEntityView() throws Exception {
        log.info("Testing EntityView");

        // 1
        edgeImitator.expectMessageAmount(1);
        EntityGroup entityViewEntityGroup = new EntityGroup();
        entityViewEntityGroup.setType(EntityType.ENTITY_VIEW);
        entityViewEntityGroup.setName("EntityViewGroup");
        entityViewEntityGroup = doPost("/api/entityGroup", entityViewEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getId().getId().toString()
                + "/entityGroup/" + entityViewEntityGroup.getId().toString() + "/" + EntityType.ENTITY_VIEW.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(entityGroupUpdateMsg.getIdMSB(), entityViewEntityGroup.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getIdLSB(), entityViewEntityGroup.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityGroupUpdateMsg.getName(), entityViewEntityGroup.getName());
        Assert.assertEquals(entityGroupUpdateMsg.getType(), entityViewEntityGroup.getType().name());
        testAutoGeneratedCodeByProtobuf(entityGroupUpdateMsg);

        // 2
        edgeImitator.expectMessageAmount(1);
        EntityView savedEntityView = saveEntityView("Edge Entity View 1", "Default", globalTestDevice.getId(), entityViewEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, globalTestDevice);

        // 3
        testEntityViewEntityGroupRequestMsg(entityGroupUpdateMsg.getIdMSB(), entityGroupUpdateMsg.getIdLSB(), savedEntityView.getId());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/entityView/" + savedEntityView.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), savedEntityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), savedEntityView.getUuidId().getLeastSignificantBits());
        log.info("EntityView tested successfully");
    }

    private void testEntityViewEntityGroupRequestMsg(long msbId, long lsbId, EntityViewId expectedEntityViewId) throws Exception {
        EntityGroupRequestMsg.Builder entitiesGroupRequestMsgBuilder = EntityGroupRequestMsg.newBuilder()
                .setEntityGroupIdMSB(msbId)
                .setEntityGroupIdLSB(lsbId)
                .setType(EntityType.ENTITY_VIEW.name());
        testAutoGeneratedCodeByProtobuf(entitiesGroupRequestMsgBuilder);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addEntityGroupEntitiesRequestMsg(entitiesGroupRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        EntityViewId receivedEntityViewId =
                new EntityViewId(new UUID(entityViewUpdateMsg.getIdMSB(), entityViewUpdateMsg.getIdLSB()));
        Assert.assertEquals(expectedEntityViewId, receivedEntityViewId);

        testAutoGeneratedCodeByProtobuf(entityViewUpdateMsg);
    }

    private void verifyEntityViewUpdateMsg(EntityView entityView, Device device) {
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getType(), entityView.getType());
        Assert.assertEquals(entityViewUpdateMsg.getName(), entityView.getName());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), entityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), entityView.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getEntityIdMSB(), device.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getEntityIdLSB(), device.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getEntityType().name(), device.getId().getEntityType().name());
    }

    private void testWidgetsBundleAndWidgetType() throws Exception {
        log.info("Testing WidgetsBundle and WidgetType");

        // 1
        edgeImitator.expectMessageAmount(1);
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTitle("Test Widget Bundle");
        WidgetsBundle savedWidgetsBundle = doPost("/api/widgetsBundle", widgetsBundle, WidgetsBundle.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetsBundleUpdateMsg);
        WidgetsBundleUpdateMsg widgetsBundleUpdateMsg = (WidgetsBundleUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, widgetsBundleUpdateMsg.getMsgType());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdMSB(), savedWidgetsBundle.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdLSB(), savedWidgetsBundle.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(widgetsBundleUpdateMsg.getAlias(), savedWidgetsBundle.getAlias());
        Assert.assertEquals(widgetsBundleUpdateMsg.getTitle(), savedWidgetsBundle.getTitle());
        testAutoGeneratedCodeByProtobuf(widgetsBundleUpdateMsg);

        // 2
        edgeImitator.expectMessageAmount(1);
        WidgetType widgetType = new WidgetType();
        widgetType.setName("Test Widget Type");
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        ObjectNode descriptor = mapper.createObjectNode();
        descriptor.put("key", "value");
        widgetType.setDescriptor(descriptor);
        WidgetType savedWidgetType = doPost("/api/widgetType", widgetType, WidgetType.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetTypeUpdateMsg);
        WidgetTypeUpdateMsg widgetTypeUpdateMsg = (WidgetTypeUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, widgetTypeUpdateMsg.getMsgType());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdMSB(), savedWidgetType.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdLSB(), savedWidgetType.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(widgetTypeUpdateMsg.getAlias(), savedWidgetType.getAlias());
        Assert.assertEquals(widgetTypeUpdateMsg.getName(), savedWidgetType.getName());
        Assert.assertEquals(JacksonUtil.toJsonNode(widgetTypeUpdateMsg.getDescriptorJson()), savedWidgetType.getDescriptor());

        // 3
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/widgetType/" + savedWidgetType.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetTypeUpdateMsg);
        widgetTypeUpdateMsg = (WidgetTypeUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, widgetTypeUpdateMsg.getMsgType());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdMSB(), savedWidgetType.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetTypeUpdateMsg.getIdLSB(), savedWidgetType.getUuidId().getLeastSignificantBits());

        // 4
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/widgetsBundle/" + savedWidgetsBundle.getId().getId().toString())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof WidgetsBundleUpdateMsg);
        widgetsBundleUpdateMsg = (WidgetsBundleUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, widgetsBundleUpdateMsg.getMsgType());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdMSB(), savedWidgetsBundle.getUuidId().getMostSignificantBits());
        Assert.assertEquals(widgetsBundleUpdateMsg.getIdLSB(), savedWidgetsBundle.getUuidId().getLeastSignificantBits());

        log.info("WidgetsBundle and WidgetType tested successfully");
    }

    private void testTimeseries() throws Exception {
        log.info("Testing timeseries");

        edgeImitator.expectMessageAmount(1);
        String timeseriesData = "{\"data\":{\"temperature\":25},\"ts\":" + System.currentTimeMillis() + "}";
        JsonNode timeseriesEntityData = mapper.readTree(timeseriesData);
        EdgeEvent edgeEvent = constructEdgeEvent(tenantId, edge.getId(), EdgeEventActionType.TIMESERIES_UPDATED, globalTestDevice.getId().getId(), EdgeEventType.DEVICE, timeseriesEntityData);
        edgeEventService.save(edgeEvent);
        clusterService.onEdgeEventUpdate(tenantId, edge.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(latestEntityDataMsg.getEntityIdMSB(), globalTestDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityIdLSB(), globalTestDevice.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(latestEntityDataMsg.getEntityType(), globalTestDevice.getId().getEntityType().name());
        Assert.assertTrue(latestEntityDataMsg.hasPostTelemetryMsg());

        TransportProtos.PostTelemetryMsg postTelemetryMsg = latestEntityDataMsg.getPostTelemetryMsg();
        Assert.assertEquals(1, postTelemetryMsg.getTsKvListCount());
        TransportProtos.TsKvListProto tsKvListProto = postTelemetryMsg.getTsKvList(0);
        Assert.assertEquals(timeseriesEntityData.get("ts").asLong(), tsKvListProto.getTs());
        Assert.assertEquals(1, tsKvListProto.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = tsKvListProto.getKv(0);
        Assert.assertEquals("temperature", keyValueProto.getKey());
        Assert.assertEquals(25, keyValueProto.getLongV());
        log.info("Timeseries tested successfully");
    }

    private void testAttributes() throws Exception {
        log.info("Testing attributes");

        testAttributesUpdatedMsg(globalTestDevice);
        testPostAttributesMsg(globalTestDevice);
        testAttributesDeleteMsg(globalTestDevice);

        log.info("Attributes tested successfully");
    }

    private void testAttributesUpdatedMsg(Device device) throws JsonProcessingException, InterruptedException {
        String attributesData = "{\"scope\":\"SERVER_SCOPE\",\"kv\":{\"key1\":\"value1\"}}";
        JsonNode attributesEntityData = mapper.readTree(attributesData);
        EdgeEvent edgeEvent1 = constructEdgeEvent(tenantId, edge.getId(), EdgeEventActionType.ATTRIBUTES_UPDATED, device.getId().getId(), EdgeEventType.DEVICE, attributesEntityData);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.save(edgeEvent1);
        clusterService.onEdgeEventUpdate(tenantId, edge.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(device.getUuidId().getMostSignificantBits(), latestEntityDataMsg.getEntityIdMSB());
        Assert.assertEquals(device.getUuidId().getLeastSignificantBits(), latestEntityDataMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getEntityType().name(), latestEntityDataMsg.getEntityType());
        Assert.assertEquals("SERVER_SCOPE", latestEntityDataMsg.getPostAttributeScope());
        Assert.assertTrue(latestEntityDataMsg.hasAttributesUpdatedMsg());

        TransportProtos.PostAttributeMsg attributesUpdatedMsg = latestEntityDataMsg.getAttributesUpdatedMsg();
        Assert.assertEquals(1, attributesUpdatedMsg.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = attributesUpdatedMsg.getKv(0);
        Assert.assertEquals("key1", keyValueProto.getKey());
        Assert.assertEquals("value1", keyValueProto.getStringV());
    }

    private void testPostAttributesMsg(Device device) throws JsonProcessingException, InterruptedException {
        String postAttributesData = "{\"scope\":\"SERVER_SCOPE\",\"kv\":{\"key2\":\"value2\"}}";
        JsonNode postAttributesEntityData = mapper.readTree(postAttributesData);
        EdgeEvent edgeEvent = constructEdgeEvent(tenantId, edge.getId(), EdgeEventActionType.POST_ATTRIBUTES, device.getId().getId(), EdgeEventType.DEVICE, postAttributesEntityData);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.save(edgeEvent);
        clusterService.onEdgeEventUpdate(tenantId, edge.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(device.getUuidId().getMostSignificantBits(), latestEntityDataMsg.getEntityIdMSB());
        Assert.assertEquals(device.getUuidId().getLeastSignificantBits(), latestEntityDataMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getEntityType().name(), latestEntityDataMsg.getEntityType());
        Assert.assertEquals("SERVER_SCOPE", latestEntityDataMsg.getPostAttributeScope());
        Assert.assertTrue(latestEntityDataMsg.hasPostAttributesMsg());

        TransportProtos.PostAttributeMsg postAttributesMsg = latestEntityDataMsg.getPostAttributesMsg();
        Assert.assertEquals(1, postAttributesMsg.getKvCount());
        TransportProtos.KeyValueProto keyValueProto = postAttributesMsg.getKv(0);
        Assert.assertEquals("key2", keyValueProto.getKey());
        Assert.assertEquals("value2", keyValueProto.getStringV());
    }

    private void testAttributesDeleteMsg(Device device) throws JsonProcessingException, InterruptedException {
        String deleteAttributesData = "{\"scope\":\"SERVER_SCOPE\",\"keys\":[\"key1\",\"key2\"]}";
        JsonNode deleteAttributesEntityData = mapper.readTree(deleteAttributesData);
        EdgeEvent edgeEvent = constructEdgeEvent(tenantId, edge.getId(), EdgeEventActionType.ATTRIBUTES_DELETED, device.getId().getId(), EdgeEventType.DEVICE, deleteAttributesEntityData);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.save(edgeEvent);
        clusterService.onEdgeEventUpdate(tenantId, edge.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(device.getUuidId().getMostSignificantBits(), latestEntityDataMsg.getEntityIdMSB());
        Assert.assertEquals(device.getUuidId().getLeastSignificantBits(), latestEntityDataMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getEntityType().name(), latestEntityDataMsg.getEntityType());

        Assert.assertTrue(latestEntityDataMsg.hasAttributeDeleteMsg());

        AttributeDeleteMsg attributeDeleteMsg = latestEntityDataMsg.getAttributeDeleteMsg();
        Assert.assertEquals(attributeDeleteMsg.getScope(), deleteAttributesEntityData.get("scope").asText());

        Assert.assertEquals(2, attributeDeleteMsg.getAttributeNamesCount());
        Assert.assertEquals("key1", attributeDeleteMsg.getAttributeNames(0));
        Assert.assertEquals("key2", attributeDeleteMsg.getAttributeNames(1));
    }

    private void testRpcCall() throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("requestId", new Random().nextInt());
        body.put("requestUUID", Uuids.timeBased().toString());
        body.put("oneway", false);
        body.put("expirationTime", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10));
        body.put("method", "test_method");
        body.put("params", "{\"param1\":\"value1\"}");

        EdgeEvent edgeEvent = constructEdgeEvent(tenantId, edge.getId(), EdgeEventActionType.RPC_CALL, globalTestDevice.getId().getId(), EdgeEventType.DEVICE, body);
        edgeImitator.expectMessageAmount(1);
        edgeEventService.save(edgeEvent);
        clusterService.onEdgeEventUpdate(tenantId, edge.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceRpcCallMsg);
        DeviceRpcCallMsg latestDeviceRpcCallMsg = (DeviceRpcCallMsg) latestMessage;
        Assert.assertEquals("test_method", latestDeviceRpcCallMsg.getRequestMsg().getMethod());
    }

    private void testTimeseriesWithFailures() throws Exception {
        log.info("Testing timeseries with failures");

        int numberOfTimeseriesToSend = 1000;

        edgeImitator.setRandomFailuresOnTimeseriesDownlink(true);
        // imitator will generate failure in 5% of cases
        edgeImitator.setFailureProbability(5.0);

        edgeImitator.expectMessageAmount(numberOfTimeseriesToSend);
        Device device = saveDevice(RandomStringUtils.randomAlphanumeric(15), CUSTOM_DEVICE_PROFILE_NAME);
        for (int idx = 1; idx <= numberOfTimeseriesToSend; idx++) {
            String timeseriesData = "{\"data\":{\"idx\":" + idx + "},\"ts\":" + System.currentTimeMillis() + "}";
            JsonNode timeseriesEntityData = mapper.readTree(timeseriesData);
            EdgeEvent edgeEvent = constructEdgeEvent(tenantId, edge.getId(), EdgeEventActionType.TIMESERIES_UPDATED,
                    device.getId().getId(), EdgeEventType.DEVICE, timeseriesEntityData);
            edgeEventService.save(edgeEvent);
            clusterService.onEdgeEventUpdate(tenantId, edge.getId());
        }

        Assert.assertTrue(edgeImitator.waitForMessages(60));

        List<EntityDataProto> allTelemetryMsgs = edgeImitator.findAllMessagesByType(EntityDataProto.class);
        Assert.assertEquals(numberOfTimeseriesToSend, allTelemetryMsgs.size());

        for (int idx = 1; idx <= numberOfTimeseriesToSend; idx++) {
            Assert.assertTrue(isIdxExistsInTheDownlinkList(idx, allTelemetryMsgs));
        }

        edgeImitator.setRandomFailuresOnTimeseriesDownlink(false);
        log.info("Timeseries with failures tested successfully");
    }

    private boolean isIdxExistsInTheDownlinkList(int idx, List<EntityDataProto> allTelemetryMsgs) {
        for (EntityDataProto proto : allTelemetryMsgs) {
            TransportProtos.PostTelemetryMsg postTelemetryMsg = proto.getPostTelemetryMsg();
            Assert.assertEquals(1, postTelemetryMsg.getTsKvListCount());
            TransportProtos.TsKvListProto tsKvListProto = postTelemetryMsg.getTsKvList(0);
            Assert.assertEquals(1, tsKvListProto.getKvCount());
            TransportProtos.KeyValueProto keyValueProto = tsKvListProto.getKv(0);
            Assert.assertEquals("idx", keyValueProto.getKey());
            if (keyValueProto.getLongV() == idx) {
                return true;
            }
        }
        return false;
    }

    private void testSendMessagesToCloud() throws Exception {
        log.info("Sending messages to cloud");
        sendDevice();
        sendDeviceWithNameThatAlreadyExistsOnCloud();
        sendRelationRequest();
        sendAlarm();
        sendTelemetry();
        sendRelation();
        sendDeleteDeviceOnEdge();
        sendRuleChainMetadataRequest();
        // TODO: implement
        // sendUserCredentialsRequest();
        sendDeviceCredentialsRequest();
        sendDeviceRpcResponse();
        sendDeviceCredentialsUpdate();
        sendAttributesRequest();
        log.info("Messages were sent successfully");
    }

    private void sendDevice() throws Exception {
        UUID uuid = Uuids.timeBased();

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceUpdateMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        deviceUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        deviceUpdateMsgBuilder.setName("Edge Device 2");
        deviceUpdateMsgBuilder.setType("test");
        deviceUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        testAutoGeneratedCodeByProtobuf(deviceUpdateMsgBuilder);
        uplinkMsgBuilder.addDeviceUpdateMsg(deviceUpdateMsgBuilder.build());

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceCredentialsRequestMsg);
        DeviceCredentialsRequestMsg latestDeviceCredentialsRequestMsg = (DeviceCredentialsRequestMsg) latestMessage;
        Assert.assertEquals(uuid.getMostSignificantBits(), latestDeviceCredentialsRequestMsg.getDeviceIdMSB());
        Assert.assertEquals(uuid.getLeastSignificantBits(), latestDeviceCredentialsRequestMsg.getDeviceIdLSB());

        UUID newDeviceId = new UUID(latestDeviceCredentialsRequestMsg.getDeviceIdMSB(), latestDeviceCredentialsRequestMsg.getDeviceIdLSB());

        Device device = doGet("/api/device/" + newDeviceId, Device.class);
        Assert.assertNotNull(device);
        Assert.assertEquals("Edge Device 2", device.getName());
    }

    private void sendDeviceWithNameThatAlreadyExistsOnCloud() throws Exception {
        String deviceOnCloudName = RandomStringUtils.randomAlphanumeric(15);
        Device deviceOnCloud = saveDevice(deviceOnCloudName, "Default");

        UUID uuid = Uuids.timeBased();

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceUpdateMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceUpdateMsgBuilder.setIdMSB(uuid.getMostSignificantBits());
        deviceUpdateMsgBuilder.setIdLSB(uuid.getLeastSignificantBits());
        deviceUpdateMsgBuilder.setName(deviceOnCloudName);
        deviceUpdateMsgBuilder.setType("test");
        deviceUpdateMsgBuilder.setMsgType(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        testAutoGeneratedCodeByProtobuf(deviceUpdateMsgBuilder);
        uplinkMsgBuilder.addDeviceUpdateMsg(deviceUpdateMsgBuilder.build());

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(2);
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());

        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getMessageFromTail(2);
        Assert.assertTrue(latestMessage instanceof DeviceUpdateMsg);
        DeviceUpdateMsg latestDeviceUpdateMsg = (DeviceUpdateMsg) latestMessage;
        Assert.assertNotEquals(deviceOnCloudName, latestDeviceUpdateMsg.getName());
        Assert.assertEquals(deviceOnCloudName, latestDeviceUpdateMsg.getConflictName());

        UUID newDeviceId = new UUID(latestDeviceUpdateMsg.getIdMSB(), latestDeviceUpdateMsg.getIdLSB());

        Assert.assertNotEquals(deviceOnCloud.getId().getId(), newDeviceId);

        Device device = doGet("/api/device/" + newDeviceId, Device.class);
        Assert.assertNotNull(device);
        Assert.assertNotEquals(deviceOnCloudName, device.getName());

        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceCredentialsRequestMsg);
        DeviceCredentialsRequestMsg latestDeviceCredentialsRequestMsg = (DeviceCredentialsRequestMsg) latestMessage;
        Assert.assertEquals(uuid.getMostSignificantBits(), latestDeviceCredentialsRequestMsg.getDeviceIdMSB());
        Assert.assertEquals(uuid.getLeastSignificantBits(), latestDeviceCredentialsRequestMsg.getDeviceIdLSB());

        newDeviceId = new UUID(latestDeviceCredentialsRequestMsg.getDeviceIdMSB(), latestDeviceCredentialsRequestMsg.getDeviceIdLSB());

        device = doGet("/api/device/" + newDeviceId, Device.class);
        Assert.assertNotNull(device);
        Assert.assertNotEquals(deviceOnCloudName, device.getName());
    }

    private void sendRelationRequest() throws Exception {

        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(globalTestDevice.getId());
        relation.setTo(globalTestAsset.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);

        edgeImitator.expectMessageAmount(1);
        doPost("/api/relation", relation);
        Assert.assertTrue(edgeImitator.waitForMessages());

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        RelationRequestMsg.Builder relationRequestMsgBuilder = RelationRequestMsg.newBuilder();
        relationRequestMsgBuilder.setEntityIdMSB(globalTestDevice.getId().getId().getMostSignificantBits());
        relationRequestMsgBuilder.setEntityIdLSB(globalTestDevice.getId().getId().getLeastSignificantBits());
        relationRequestMsgBuilder.setEntityType(globalTestDevice.getId().getEntityType().name());
        testAutoGeneratedCodeByProtobuf(relationRequestMsgBuilder);

        uplinkMsgBuilder.addRelationRequestMsg(relationRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RelationUpdateMsg);
        RelationUpdateMsg relationUpdateMsg = (RelationUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, relationUpdateMsg.getMsgType());
        Assert.assertEquals(relation.getType(), relationUpdateMsg.getType());

        UUID fromUUID = new UUID(relationUpdateMsg.getFromIdMSB(), relationUpdateMsg.getFromIdLSB());
        EntityId fromEntityId = EntityIdFactory.getByTypeAndUuid(relationUpdateMsg.getFromEntityType(), fromUUID);
        Assert.assertEquals(relation.getFrom(), fromEntityId);

        UUID toUUID = new UUID(relationUpdateMsg.getToIdMSB(), relationUpdateMsg.getToIdLSB());
        EntityId toEntityId = EntityIdFactory.getByTypeAndUuid(relationUpdateMsg.getToEntityType(), toUUID);
        Assert.assertEquals(relation.getTo(), toEntityId);

        Assert.assertEquals(relation.getTypeGroup().name(), relationUpdateMsg.getTypeGroup());
    }

    private void sendAlarm() throws Exception {

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        AlarmUpdateMsg.Builder alarmUpdateMgBuilder = AlarmUpdateMsg.newBuilder();
        alarmUpdateMgBuilder.setName("alarm from edge");
        alarmUpdateMgBuilder.setStatus(AlarmStatus.ACTIVE_UNACK.name());
        alarmUpdateMgBuilder.setSeverity(AlarmSeverity.CRITICAL.name());
        alarmUpdateMgBuilder.setOriginatorName(globalTestDevice.getName());
        alarmUpdateMgBuilder.setOriginatorType(EntityType.DEVICE.name());
        testAutoGeneratedCodeByProtobuf(alarmUpdateMgBuilder);
        uplinkMsgBuilder.addAlarmUpdateMsg(alarmUpdateMgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());


        List<AlarmInfo> alarms = doGetTypedWithPageLink("/api/alarm/{entityType}/{entityId}?",
                new TypeReference<PageData<AlarmInfo>>() {},
                new PageLink(100), globalTestDevice.getId().getEntityType().name(), globalTestDevice.getId().getId().toString())
                .getData();
        Optional<AlarmInfo> foundAlarm = alarms.stream().filter(alarm -> alarm.getType().equals("alarm from edge")).findAny();
        Assert.assertTrue(foundAlarm.isPresent());
        AlarmInfo alarmInfo = foundAlarm.get();
        Assert.assertEquals(globalTestDevice.getId(), alarmInfo.getOriginator());
        Assert.assertEquals(AlarmStatus.ACTIVE_UNACK, alarmInfo.getStatus());
        Assert.assertEquals(AlarmSeverity.CRITICAL, alarmInfo.getSeverity());
    }

    private void sendTelemetry() throws Exception {
        edgeImitator.expectResponsesAmount(2);

        JsonObject data = new JsonObject();
        String timeseriesKey = "key";
        String timeseriesValue = "25";
        data.addProperty(timeseriesKey, timeseriesValue);
        UplinkMsg.Builder uplinkMsgBuilder1 = UplinkMsg.newBuilder();
        EntityDataProto.Builder entityDataBuilder = EntityDataProto.newBuilder();
        entityDataBuilder.setPostTelemetryMsg(JsonConverter.convertToTelemetryProto(data, System.currentTimeMillis()));
        entityDataBuilder.setEntityType(globalTestDevice.getId().getEntityType().name());
        entityDataBuilder.setEntityIdMSB(globalTestDevice.getUuidId().getMostSignificantBits());
        entityDataBuilder.setEntityIdLSB(globalTestDevice.getUuidId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(entityDataBuilder);
        uplinkMsgBuilder1.addEntityData(entityDataBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder1.build());

        JsonObject attributesData = new JsonObject();
        String attributesKey = "test_attr";
        String attributesValue = "test_value";
        attributesData.addProperty(attributesKey, attributesValue);
        UplinkMsg.Builder uplinkMsgBuilder2 = UplinkMsg.newBuilder();
        EntityDataProto.Builder entityDataBuilder2 = EntityDataProto.newBuilder();
        entityDataBuilder2.setEntityType(globalTestDevice.getId().getEntityType().name());
        entityDataBuilder2.setEntityIdMSB(globalTestDevice.getId().getId().getMostSignificantBits());
        entityDataBuilder2.setEntityIdLSB(globalTestDevice.getId().getId().getLeastSignificantBits());
        entityDataBuilder2.setAttributesUpdatedMsg(JsonConverter.convertToAttributesProto(attributesData));
        entityDataBuilder2.setPostAttributeScope(DataConstants.SERVER_SCOPE);
        testAutoGeneratedCodeByProtobuf(entityDataBuilder2);

        uplinkMsgBuilder2.addEntityData(entityDataBuilder2.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder2);

        edgeImitator.sendUplinkMsg(uplinkMsgBuilder2.build());
        Assert.assertTrue(edgeImitator.waitForResponses());

        Awaitility.await()
                .atMost(2, TimeUnit.SECONDS)
                .until(() -> loadDeviceTimeseries(globalTestDevice, timeseriesKey).containsKey(timeseriesKey));

        Map<String, List<Map<String, String>>> timeseries = loadDeviceTimeseries(globalTestDevice, timeseriesKey);
        Assert.assertTrue(timeseries.containsKey(timeseriesKey));
        Assert.assertEquals(1, timeseries.get(timeseriesKey).size());
        Assert.assertEquals(timeseriesValue, timeseries.get(timeseriesKey).get(0).get("value"));

        List<Map<String, String>> attributes =
                doGetAsyncTyped("/api/plugins/telemetry/DEVICE/" + globalTestDevice.getId() + "/values/attributes/" + DataConstants.SERVER_SCOPE, new TypeReference<>() {});
        Assert.assertEquals(2, attributes.size());
        Assert.assertEquals(attributes.get(0).get("key"), attributesKey);
        Assert.assertEquals(attributes.get(0).get("value"), attributesValue);

        doDelete("/api/plugins/telemetry/DEVICE/" + globalTestDevice.getId().getId() + "/SERVER_SCOPE?keys=test_attr", String.class);
    }

    private Map<String, List<Map<String, String>>> loadDeviceTimeseries(Device device, String timeseriesKey) throws Exception {
        return doGetAsyncTyped("/api/plugins/telemetry/DEVICE/" + device.getUuidId() + "/values/timeseries?keys=" + timeseriesKey,
                new TypeReference<>() {});
    }

    private void sendRelation() throws Exception {
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        RelationUpdateMsg.Builder relationUpdateMsgBuilder = RelationUpdateMsg.newBuilder();
        relationUpdateMsgBuilder.setType("test");
        relationUpdateMsgBuilder.setTypeGroup(RelationTypeGroup.COMMON.name());
        relationUpdateMsgBuilder.setToIdMSB(globalTestDevice.getId().getId().getMostSignificantBits());
        relationUpdateMsgBuilder.setToIdLSB(globalTestDevice.getId().getId().getLeastSignificantBits());
        relationUpdateMsgBuilder.setToEntityType(globalTestDevice.getId().getEntityType().name());
        relationUpdateMsgBuilder.setFromIdMSB(globalTestDevice.getId().getId().getMostSignificantBits());
        relationUpdateMsgBuilder.setFromIdLSB(globalTestDevice.getId().getId().getLeastSignificantBits());
        relationUpdateMsgBuilder.setFromEntityType(globalTestDevice.getId().getEntityType().name());
        relationUpdateMsgBuilder.setAdditionalInfo("{}");
        testAutoGeneratedCodeByProtobuf(relationUpdateMsgBuilder);
        uplinkMsgBuilder.addRelationUpdateMsg(relationUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());

        EntityRelation relation = doGet("/api/relation?" +
                "&fromId=" + globalTestAsset.getId().getId().toString() +
                "&fromType=" + globalTestAsset.getId().getEntityType().name() +
                "&relationType=" + "test" +
                "&relationTypeGroup=" + RelationTypeGroup.COMMON.name() +
                "&toId=" + globalTestDevice.getId().getId().toString() +
                "&toType=" + globalTestDevice.getId().getEntityType().name(), EntityRelation.class);
        Assert.assertNotNull(relation);
    }

    private void sendDeleteDeviceOnEdge() throws Exception {
        UplinkMsg.Builder upLinkMsgBuilder = UplinkMsg.newBuilder();
        DeviceUpdateMsg.Builder deviceDeleteMsgBuilder = DeviceUpdateMsg.newBuilder();
        deviceDeleteMsgBuilder.setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE);
        deviceDeleteMsgBuilder.setIdMSB(globalTestDevice.getId().getId().getMostSignificantBits());
        deviceDeleteMsgBuilder.setIdLSB(globalTestDevice.getId().getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(deviceDeleteMsgBuilder);

        upLinkMsgBuilder.addDeviceUpdateMsg(deviceDeleteMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(upLinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(upLinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        globalTestDevice = doGet("/api/device/" + globalTestDevice.getId().getId().toString(), Device.class);
        Assert.assertNotNull(globalTestDevice);
    }

    private void sendRuleChainMetadataRequest() throws Exception {
        RuleChainId edgeRootRuleChainId = edge.getRootRuleChainId();

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        RuleChainMetadataRequestMsg.Builder ruleChainMetadataRequestMsgBuilder = RuleChainMetadataRequestMsg.newBuilder();
        ruleChainMetadataRequestMsgBuilder.setRuleChainIdMSB(edgeRootRuleChainId.getId().getMostSignificantBits());
        ruleChainMetadataRequestMsgBuilder.setRuleChainIdLSB(edgeRootRuleChainId.getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(ruleChainMetadataRequestMsgBuilder);
        uplinkMsgBuilder.addRuleChainMetadataRequestMsg(ruleChainMetadataRequestMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof RuleChainMetadataUpdateMsg);
        RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg = (RuleChainMetadataUpdateMsg) latestMessage;
        Assert.assertEquals(ruleChainMetadataUpdateMsg.getRuleChainIdMSB(), edgeRootRuleChainId.getId().getMostSignificantBits());
        Assert.assertEquals(ruleChainMetadataUpdateMsg.getRuleChainIdLSB(), edgeRootRuleChainId.getId().getLeastSignificantBits());

        testAutoGeneratedCodeByProtobuf(ruleChainMetadataUpdateMsg);
    }

    private void sendUserCredentialsRequest() throws Exception {
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        UserCredentialsRequestMsg.Builder userCredentialsRequestMsgBuilder = UserCredentialsRequestMsg.newBuilder();
        userCredentialsRequestMsgBuilder.setUserIdMSB(tenantAdmin.getId().getId().getMostSignificantBits());
        userCredentialsRequestMsgBuilder.setUserIdLSB(tenantAdmin.getId().getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(userCredentialsRequestMsgBuilder);
        uplinkMsgBuilder.addUserCredentialsRequestMsg(userCredentialsRequestMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof UserCredentialsUpdateMsg);
        UserCredentialsUpdateMsg userCredentialsUpdateMsg = (UserCredentialsUpdateMsg) latestMessage;
        Assert.assertEquals(userCredentialsUpdateMsg.getUserIdMSB(), tenantAdmin.getId().getId().getMostSignificantBits());
        Assert.assertEquals(userCredentialsUpdateMsg.getUserIdLSB(), tenantAdmin.getId().getId().getLeastSignificantBits());

        testAutoGeneratedCodeByProtobuf(userCredentialsUpdateMsg);
    }

    private void sendDeviceCredentialsRequest() throws Exception {
        DeviceCredentials deviceCredentials = doGet("/api/device/" + globalTestDevice.getId().getId().toString() + "/credentials", DeviceCredentials.class);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        DeviceCredentialsRequestMsg.Builder deviceCredentialsRequestMsgBuilder = DeviceCredentialsRequestMsg.newBuilder();
        deviceCredentialsRequestMsgBuilder.setDeviceIdMSB(globalTestDevice.getUuidId().getMostSignificantBits());
        deviceCredentialsRequestMsgBuilder.setDeviceIdLSB(globalTestDevice.getUuidId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(deviceCredentialsRequestMsgBuilder);
        uplinkMsgBuilder.addDeviceCredentialsRequestMsg(deviceCredentialsRequestMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof DeviceCredentialsUpdateMsg);
        DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg = (DeviceCredentialsUpdateMsg) latestMessage;
        Assert.assertEquals(deviceCredentialsUpdateMsg.getDeviceIdMSB(), globalTestDevice.getUuidId().getMostSignificantBits());
        Assert.assertEquals(deviceCredentialsUpdateMsg.getDeviceIdLSB(), globalTestDevice.getUuidId().getLeastSignificantBits());
        Assert.assertEquals(deviceCredentialsUpdateMsg.getCredentialsType(), deviceCredentials.getCredentialsType().name());
        Assert.assertEquals(deviceCredentialsUpdateMsg.getCredentialsId(), deviceCredentials.getCredentialsId());
    }

    private void sendDeviceRpcResponse() throws Exception {
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        DeviceRpcCallMsg.Builder deviceRpcCallResponseBuilder = DeviceRpcCallMsg.newBuilder();
        deviceRpcCallResponseBuilder.setDeviceIdMSB(globalTestDevice.getUuidId().getMostSignificantBits());
        deviceRpcCallResponseBuilder.setDeviceIdLSB(globalTestDevice.getUuidId().getLeastSignificantBits());
        deviceRpcCallResponseBuilder.setOneway(true);
        deviceRpcCallResponseBuilder.setRequestId(0);
        deviceRpcCallResponseBuilder.setExpirationTime(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10));
        RpcResponseMsg.Builder responseBuilder =
                RpcResponseMsg.newBuilder().setResponse("{}");
        testAutoGeneratedCodeByProtobuf(responseBuilder);

        deviceRpcCallResponseBuilder.setResponseMsg(responseBuilder.build());
        testAutoGeneratedCodeByProtobuf(deviceRpcCallResponseBuilder);

        uplinkMsgBuilder.addDeviceRpcCallMsg(deviceRpcCallResponseBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
    }

    private void sendDeviceCredentialsUpdate() throws Exception {
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        DeviceCredentialsUpdateMsg.Builder deviceCredentialsUpdateMsgBuilder = DeviceCredentialsUpdateMsg.newBuilder();
        deviceCredentialsUpdateMsgBuilder.setDeviceIdMSB(globalTestDevice.getUuidId().getMostSignificantBits());
        deviceCredentialsUpdateMsgBuilder.setDeviceIdLSB(globalTestDevice.getUuidId().getLeastSignificantBits());
        deviceCredentialsUpdateMsgBuilder.setCredentialsType(DeviceCredentialsType.ACCESS_TOKEN.name());
        deviceCredentialsUpdateMsgBuilder.setCredentialsId("NEW_TOKEN");
        testAutoGeneratedCodeByProtobuf(deviceCredentialsUpdateMsgBuilder);
        uplinkMsgBuilder.addDeviceCredentialsUpdateMsg(deviceCredentialsUpdateMsgBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
    }

    private void sendAttributesRequest() throws Exception {
        sendAttributesRequest(globalTestDevice, DataConstants.SERVER_SCOPE, "{\"key1\":\"value1\"}",
                "key1", "value1", 2);
        sendAttributesRequest(globalTestDevice, DataConstants.SHARED_SCOPE, "{\"key2\":\"value2\"}",
                "key2", "value2", 1);
    }

    private void sendAttributesRequest(Device device, String scope, String attributesDataStr, String expectedKey,
                                       String expectedValue, int expectedSize) throws Exception {
        JsonNode attributesData = mapper.readTree(attributesDataStr);

        doPost("/api/plugins/telemetry/DEVICE/" + device.getId().getId().toString() + "/attributes/" + scope,
                attributesData);

        // Wait before device attributes saved to database before requesting them from edge
        // queue used to save attributes to database
        Thread.sleep(500);

        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        AttributesRequestMsg.Builder attributesRequestMsgBuilder = AttributesRequestMsg.newBuilder();
        attributesRequestMsgBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        attributesRequestMsgBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        attributesRequestMsgBuilder.setEntityType(EntityType.DEVICE.name());
        attributesRequestMsgBuilder.setScope(scope);
        testAutoGeneratedCodeByProtobuf(attributesRequestMsgBuilder);
        uplinkMsgBuilder.addAttributesRequestMsg(attributesRequestMsgBuilder.build());
        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());

        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityDataProto);
        EntityDataProto latestEntityDataMsg = (EntityDataProto) latestMessage;
        Assert.assertEquals(device.getUuidId().getMostSignificantBits(), latestEntityDataMsg.getEntityIdMSB());
        Assert.assertEquals(device.getUuidId().getLeastSignificantBits(), latestEntityDataMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getEntityType().name(), latestEntityDataMsg.getEntityType());
        Assert.assertEquals(scope, latestEntityDataMsg.getPostAttributeScope());
        Assert.assertTrue(latestEntityDataMsg.hasAttributesUpdatedMsg());

        TransportProtos.PostAttributeMsg attributesUpdatedMsg = latestEntityDataMsg.getAttributesUpdatedMsg();
        Assert.assertEquals(expectedSize, attributesUpdatedMsg.getKvList().size());
        for (TransportProtos.KeyValueProto keyValueProto : attributesUpdatedMsg.getKvList()) {
            if (keyValueProto.getKey().equals(expectedKey)) {
                Assert.assertEquals(expectedKey, keyValueProto.getKey());
                Assert.assertEquals(expectedValue, keyValueProto.getStringV());
            }
        }
    }

    // Utility methods

    private Device saveDevice(String deviceName, String type) throws Exception {
        return saveDevice(deviceName, type, null);
    }

    private Device saveDevice(String deviceName, String type, EntityGroupId entityGroupId) throws Exception {
        Device device = new Device();
        device.setName(deviceName);
        device.setType(type);
        if (entityGroupId != null) {
            return doPost("/api/device?entityGroupId={entityGroupId}", device, Device.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/device", device, Device.class);
        }
    }

    private Asset saveAsset(String assetName, String type, EntityGroupId entityGroupId) throws Exception {
        Asset asset = new Asset();
        asset.setName(assetName);
        asset.setType(type);
        return doPost("/api/asset?entityGroupId={entityGroupId}", asset, Asset.class, entityGroupId.getId().toString());
    }

    private Dashboard saveDashboard(String dashboardTitle, EntityGroupId entityGroupId) throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle(dashboardTitle);
        if (entityGroupId != null) {
            return doPost("/api/dashboard?entityGroupId={entityGroupId}", dashboard, Dashboard.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/dashboard", dashboard, Dashboard.class);
        }
    }

    private EntityView saveEntityView(String entityViewName, String type, DeviceId deviceId, EntityGroupId entityGroupId) throws Exception {
        EntityView entityView = new EntityView();
        entityView.setName("Edge EntityView 1");
        entityView.setType("test");
        entityView.setEntityId(deviceId);
        if (entityGroupId != null) {
            return doPost("/api/entityView?entityGroupId={entityGroupId}", entityView, EntityView.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/entityView", entityView, EntityView.class);
        }
    }

    private EdgeEvent constructEdgeEvent(TenantId tenantId, EdgeId edgeId, EdgeEventActionType edgeEventAction, UUID entityId, EdgeEventType edgeEventType, JsonNode entityBody) {
        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setEdgeId(edgeId);
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setAction(edgeEventAction);
        edgeEvent.setEntityId(entityId);
        edgeEvent.setType(edgeEventType);
        edgeEvent.setBody(entityBody);
        return edgeEvent;
    }

    private void testAutoGeneratedCodeByProtobuf(MessageLite.Builder builder) throws InvalidProtocolBufferException {
        MessageLite source = builder.build();

        testAutoGeneratedCodeByProtobuf(source);

        MessageLite target = source.getParserForType().parseFrom(source.toByteArray());
        builder.clear().mergeFrom(target);
    }

    private void testAutoGeneratedCodeByProtobuf(MessageLite source) throws InvalidProtocolBufferException {
        MessageLite target = source.getParserForType().parseFrom(source.toByteArray());
        Assert.assertEquals(source, target);
        Assert.assertEquals(source.hashCode(), target.hashCode());
    }
}
