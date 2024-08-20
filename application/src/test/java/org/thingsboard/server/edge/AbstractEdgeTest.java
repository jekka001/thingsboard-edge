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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.SaveOtaPackageInfoRequest;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.device.profile.AlarmCondition;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilter;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilterKey;
import org.thingsboard.server.common.data.device.profile.AlarmConditionKeyType;
import org.thingsboard.server.common.data.device.profile.AlarmRule;
import org.thingsboard.server.common.data.device.profile.AllowCreateNewDevicesDeviceProfileProvisionConfiguration;
import org.thingsboard.server.common.data.device.profile.DeviceProfileAlarm;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.device.profile.JsonTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.SimpleAlarmConditionSpec;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.menu.CustomMenuItem;
import org.thingsboard.server.common.data.oauth2.OAuth2Client;
import org.thingsboard.server.common.data.ota.ChecksumAlgorithm;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.NumericFilterPredicate;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.security.model.JwtSettings;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingType;
import org.thingsboard.server.controller.AbstractControllerTest;
import org.thingsboard.server.dao.edge.EdgeEventService;
import org.thingsboard.server.edge.imitator.EdgeImitator;
import org.thingsboard.server.gen.edge.v1.AdminSettingsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeConfiguration;
import org.thingsboard.server.gen.edge.v1.EntityGroupUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OAuth2UpdateMsg;
import org.thingsboard.server.gen.edge.v1.QueueUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RoleProto;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataRequestMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.SyncCompletedMsg;
import org.thingsboard.server.gen.edge.v1.TenantProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.TenantUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.gen.edge.v1.WhiteLabelingProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "edges.enabled=true",
        "queue.rule-engine.stats.enabled=false",
        "edges.storage.sleep_between_batches=1000"
})
@Slf4j
abstract public class AbstractEdgeTest extends AbstractControllerTest {

    protected static final String THERMOSTAT_DEVICE_PROFILE_NAME = "Thermostat";

    protected DeviceProfile thermostatDeviceProfile;

    protected EdgeImitator edgeImitator;
    protected Edge edge;

    @Autowired
    protected EdgeEventService edgeEventService;

    @Autowired
    protected TbClusterService clusterService;

    @Before
    public void setupEdgeTest() throws Exception {
        loginSysAdmin();

        doPost("/api/whiteLabel/loginWhiteLabelParams", new LoginWhiteLabelingParams(), LoginWhiteLabelingParams.class);
        doPost("/api/whiteLabel/whiteLabelParams", new WhiteLabelingParams(), WhiteLabelingParams.class);

        // get jwt settings from yaml config
        JwtSettings settings = doGet("/api/admin/jwtSettings", JwtSettings.class);
        // save jwt settings into db
        doPost("/api/admin/jwtSettings", settings).andExpect(status().isOk());

        // create custom menu
        CustomMenu sysMenu = new CustomMenu();

        CustomMenuItem sysItem = new CustomMenuItem();
        sysItem.setName("System Menu");
        sysMenu.setMenuItems(new ArrayList<>(List.of(sysItem)));

        doPost("/api/customMenu/customMenu", sysMenu);

        loginTenantAdmin();

        installation();

        edgeImitator = new EdgeImitator("localhost", 7070, edge.getRoutingKey(), edge.getSecret());
        edgeImitator.ignoreType(OAuth2UpdateMsg.class);
        edgeImitator.expectMessageAmount(28);
        edgeImitator.connect();

        requestEdgeRuleChainMetadata();

        verifyEdgeConnectionAndInitialData();
    }

    private void requestEdgeRuleChainMetadata() throws Exception {
        RuleChainId rootRuleChainId = getEdgeRootRuleChainId();
        RuleChainMetadataRequestMsg.Builder builder = RuleChainMetadataRequestMsg.newBuilder()
                .setRuleChainIdMSB(rootRuleChainId.getId().getMostSignificantBits())
                .setRuleChainIdLSB(rootRuleChainId.getId().getLeastSignificantBits());
        testAutoGeneratedCodeByProtobuf(builder);
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder()
                .addRuleChainMetadataRequestMsg(builder.build());
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
    }

    private RuleChainId getEdgeRootRuleChainId() throws Exception {
        List<RuleChain> edgeRuleChains = doGetTypedWithPageLink("/api/edge/" + edge.getUuidId() + "/ruleChains?",
                new TypeReference<PageData<RuleChain>>() {
                }, new PageLink(100)).getData();
        for (RuleChain edgeRuleChain : edgeRuleChains) {
            if (edgeRuleChain.isRoot()) {
                return edgeRuleChain.getId();
            }
        }
        throw new RuntimeException("Root rule chain not found");
    }

    @After
    public void teardownEdgeTest() {
        try {
            loginTenantAdmin();

            doDelete("/api/edge/" + edge.getId().toString())
                    .andExpect(status().isOk());
            edgeImitator.disconnect();
        } catch (Exception ignored) {
        }
    }

    private void installation() {
        thermostatDeviceProfile = this.createDeviceProfile(THERMOSTAT_DEVICE_PROFILE_NAME,
                createMqttDeviceProfileTransportConfiguration(new JsonTransportPayloadConfiguration(), false));
        extendDeviceProfileData(thermostatDeviceProfile);
        thermostatDeviceProfile = doPost("/api/deviceProfile", thermostatDeviceProfile, DeviceProfile.class);

        edge = doPost("/api/edge", constructEdge("Test Edge", "test"), Edge.class);
        verifyTenantAdministratorsAndTenantUsersAssignedToEdge();
    }

    protected void extendDeviceProfileData(DeviceProfile deviceProfile) {
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


    private void verifyEdgeConnectionAndInitialData() throws Exception {
        Assert.assertTrue(edgeImitator.waitForMessages());

        validateEdgeConfiguration();

        // 1 message from queue fetcher
        validateMsgsCnt(QueueUpdateMsg.class, 1);
        validateQueues();

        // 1 from rule chain fetcher
        validateMsgsCnt(RuleChainUpdateMsg.class, 1);
        UUID ruleChainUUID = validateRuleChains();

        // 1 from request message
        validateMsgsCnt(RuleChainMetadataUpdateMsg.class, 2);
        validateRuleChainMetadataUpdates(ruleChainUUID);

        // 5 messages ('general', 'mail', 'connectivity', 'jwt', 'customMenu')
        validateMsgsCnt(AdminSettingsUpdateMsg.class, 5);
        validateAdminSettings(5);

        // 3 messages
        // - 1 from default profile fetcher
        // - 2 from device profile fetcher (default and thermostat)
        validateMsgsCnt(DeviceProfileUpdateMsg.class, 3);
        validateDeviceProfiles(3);

        // 2 messages
        // - 1 from default profile fetcher
        // - 1 message from asset profile fetcher
        validateMsgsCnt(AssetProfileUpdateMsg.class, 2);
        validateAssetProfiles(2);

        // 1 message from public customer fetcher
        validateMsgsCnt(CustomerUpdateMsg.class, 1);
        validatePublicCustomer();

        // 5 messages
        // - 2 messages from SysAdminRolesEdgeEventFetcher
        // - 2 messages from TenantRolesEdgeEventFetcher
        // - 1 message from public customer role
        validateMsgsCnt(RoleProto.class, 5);
        validateRoles();

        // 3 messages
        // - 2 messages from fetcher
        // - 1 message from public customer user group
        validateMsgsCnt(EntityGroupUpdateMsg.class, 3);
        validateEntityGroups();

        // 1 from tenant fetcher
        validateMsgsCnt(TenantUpdateMsg.class, 1);
        validateTenant();

        // 1 from tenant profile fetcher
        validateMsgsCnt(TenantProfileUpdateMsg.class, 1);
        validateTenantProfile();

        // 2 messages from fetcher: 'login' and 'general'
        validateMsgsCnt(WhiteLabelingProto.class, 2);
        validateWhiteLabeling();

        // 1 message sync completed
        validateMsgsCnt(SyncCompletedMsg.class, 1);
        validateSyncCompleted();
    }

    private <T extends AbstractMessage> void validateMsgsCnt(Class<T> clazz, int expectedMsgCnt) {
        List<T> downlinkMsgsByType = edgeImitator.findAllMessagesByType(clazz);
        if (downlinkMsgsByType.size() != expectedMsgCnt) {
            List<AbstractMessage> downlinkMsgs = edgeImitator.getDownlinkMsgs();
            for (AbstractMessage downlinkMsg : downlinkMsgs) {
                log.error("{}\n{}", downlinkMsg.getClass(), downlinkMsg);
            }
            Assert.fail("Unexpected message count for " + clazz + "! Expected: " + expectedMsgCnt + ", but found: " + downlinkMsgsByType.size());
        }
    }

    private void validateEdgeConfiguration() throws Exception {
        EdgeConfiguration configuration = edgeImitator.getConfiguration();
        Assert.assertNotNull(configuration);
        testAutoGeneratedCodeByProtobuf(configuration);
    }

    private void validateTenant() throws Exception {
        Optional<TenantUpdateMsg> tenantUpdateMsgOpt = edgeImitator.findMessageByType(TenantUpdateMsg.class);
        Assert.assertTrue(tenantUpdateMsgOpt.isPresent());
        TenantUpdateMsg tenantUpdateMsg = tenantUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, tenantUpdateMsg.getMsgType());
        Tenant tenantMsg = JacksonUtil.fromString(tenantUpdateMsg.getEntity(), Tenant.class, true);
        Assert.assertNotNull(tenantMsg);
        Tenant tenant = doGet("/api/tenant/" + tenantMsg.getUuidId(), Tenant.class);
        Assert.assertNotNull(tenant);
        testAutoGeneratedCodeByProtobuf(tenantUpdateMsg);
    }

    private void validateTenantProfile() throws Exception {
        Optional<TenantProfileUpdateMsg> tenantProfileUpdateMsgOpt = edgeImitator.findMessageByType(TenantProfileUpdateMsg.class);
        Assert.assertTrue(tenantProfileUpdateMsgOpt.isPresent());
        TenantProfileUpdateMsg tenantProfileUpdateMsg = tenantProfileUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, tenantProfileUpdateMsg.getMsgType());
        TenantProfile tenantProfile = JacksonUtil.fromString(tenantProfileUpdateMsg.getEntity(), TenantProfile.class, true);
        Assert.assertNotNull(tenantProfile);
        Tenant tenant = doGet("/api/tenant/" + tenantId.getId(), Tenant.class);
        Assert.assertNotNull(tenant);
        Assert.assertEquals(tenantProfile.getId(), tenant.getTenantProfileId());
        testAutoGeneratedCodeByProtobuf(tenantProfileUpdateMsg);
    }

    private void validateDeviceProfiles(int expectedMsgCnt) throws Exception {
        List<DeviceProfileUpdateMsg> deviceProfileUpdateMsgList = edgeImitator.findAllMessagesByType(DeviceProfileUpdateMsg.class);
        // default msg default device profile from fetcher
        // default msg
        // thermostat msg from device profile fetcher
        Assert.assertEquals(expectedMsgCnt, deviceProfileUpdateMsgList.size());
        Optional<DeviceProfileUpdateMsg> thermostatProfileUpdateMsgOpt =
                deviceProfileUpdateMsgList.stream().filter(dfum -> {
                    DeviceProfile deviceProfile = JacksonUtil.fromString(dfum.getEntity(), DeviceProfile.class, true);
                    Assert.assertNotNull(deviceProfile);
                    return THERMOSTAT_DEVICE_PROFILE_NAME.equals(deviceProfile.getName());
                }).findAny();
        Assert.assertTrue(thermostatProfileUpdateMsgOpt.isPresent());
        DeviceProfileUpdateMsg thermostatProfileUpdateMsg = thermostatProfileUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, thermostatProfileUpdateMsg.getMsgType());
        UUID deviceProfileUUID = new UUID(thermostatProfileUpdateMsg.getIdMSB(), thermostatProfileUpdateMsg.getIdLSB());
        DeviceProfile deviceProfile = doGet("/api/deviceProfile/" + deviceProfileUUID, DeviceProfile.class);
        Assert.assertNotNull(deviceProfile);
        Assert.assertNotNull(deviceProfile.getProfileData());
        Assert.assertNotNull(deviceProfile.getProfileData().getAlarms());
        Assert.assertNotNull(deviceProfile.getProfileData().getAlarms().get(0).getClearRule());

        testAutoGeneratedCodeByProtobuf(thermostatProfileUpdateMsg);
    }

    private UUID validateRuleChains() throws Exception {
        Optional<RuleChainUpdateMsg> ruleChainUpdateMsgOpt = edgeImitator.findMessageByType(RuleChainUpdateMsg.class);
        Assert.assertTrue(ruleChainUpdateMsgOpt.isPresent());
        RuleChainUpdateMsg ruleChainUpdateMsg = ruleChainUpdateMsgOpt.get();
        validateRuleChain(ruleChainUpdateMsg, UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE);
        return new UUID(ruleChainUpdateMsg.getIdMSB(), ruleChainUpdateMsg.getIdLSB());
    }

    private void validateRuleChain(RuleChainUpdateMsg ruleChainUpdateMsg, UpdateMsgType expectedMsgType) throws Exception {
        Assert.assertEquals(expectedMsgType, ruleChainUpdateMsg.getMsgType());
        UUID ruleChainUUID = new UUID(ruleChainUpdateMsg.getIdMSB(), ruleChainUpdateMsg.getIdLSB());
        RuleChain ruleChain = doGet("/api/ruleChain/" + ruleChainUUID, RuleChain.class);
        Assert.assertNotNull(ruleChain);
        List<RuleChain> edgeRuleChains = doGetTypedWithPageLink("/api/edge/" + edge.getUuidId() + "/ruleChains?",
                new TypeReference<PageData<RuleChain>>() {
                }, new PageLink(100)).getData();
        Assert.assertTrue(edgeRuleChains.contains(ruleChain));
        testAutoGeneratedCodeByProtobuf(ruleChainUpdateMsg);
    }

    private void validateRuleChainMetadataUpdates(UUID expectedRuleChainUUID) {
        Optional<RuleChainMetadataUpdateMsg> ruleChainMetadataUpdateOpt = edgeImitator.findMessageByType(RuleChainMetadataUpdateMsg.class);
        Assert.assertTrue(ruleChainMetadataUpdateOpt.isPresent());
        RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg = ruleChainMetadataUpdateOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, ruleChainMetadataUpdateMsg.getMsgType());
        RuleChainMetaData ruleChainMetaData = JacksonUtil.fromString(ruleChainMetadataUpdateMsg.getEntity(), RuleChainMetaData.class, true);
        Assert.assertNotNull(ruleChainMetaData);
        Assert.assertEquals(expectedRuleChainUUID, ruleChainMetaData.getRuleChainId().getId());
    }

    private void validateAdminSettings(int expectedMsgCnt) {
        List<AdminSettingsUpdateMsg> adminSettingsUpdateMsgs = edgeImitator.findAllMessagesByType(AdminSettingsUpdateMsg.class);
        Assert.assertEquals(expectedMsgCnt, adminSettingsUpdateMsgs.size());

        for (AdminSettingsUpdateMsg adminSettingsUpdateMsg : adminSettingsUpdateMsgs) {
            AdminSettings adminSettings = JacksonUtil.fromString(adminSettingsUpdateMsg.getEntity(), AdminSettings.class, true);
            Assert.assertNotNull(adminSettings);
            if (adminSettings.getKey().equals("general")) {
                validateGeneralAdminSettings(adminSettings);
            }
            if (adminSettings.getKey().equals("mail")) {
                validateMailAdminSettings(adminSettings);
            }
            if (adminSettings.getKey().equals("connectivity")) {
                validateConnectivityAdminSettings(adminSettings);
            }
            if (adminSettings.getKey().equals("jwt")) {
                validateJwtAdminSettings(adminSettings);
            }
            if (adminSettings.getKey().equals("customMenu")) {
                validateCustomMenuAdminSettings(adminSettings);
            }
        }
    }

    private void validateGeneralAdminSettings(AdminSettings adminSettings) {
        Assert.assertNotNull(adminSettings.getJsonValue().get("baseUrl"));
    }

    private void validateMailAdminSettings(AdminSettings adminSettings) {
        JsonNode jsonNode = adminSettings.getJsonValue();
        Assert.assertNotNull(jsonNode.get("mailFrom"));
        Assert.assertNotNull(jsonNode.get("smtpProtocol"));
        Assert.assertNotNull(jsonNode.get("smtpHost"));
        Assert.assertNotNull(jsonNode.get("smtpPort"));
        Assert.assertNotNull(jsonNode.get("timeout"));
    }

    private void validateConnectivityAdminSettings(AdminSettings adminSettings) {
        JsonNode jsonNode = adminSettings.getJsonValue();
        Assert.assertNotNull(jsonNode.get("http"));
        Assert.assertNotNull(jsonNode.get("https"));
        Assert.assertNotNull(jsonNode.get("mqtt"));
        Assert.assertNotNull(jsonNode.get("mqtts"));
        Assert.assertNotNull(jsonNode.get("coap"));
        Assert.assertNotNull(jsonNode.get("coaps"));
    }

    private void validateJwtAdminSettings(AdminSettings adminSettings) {
        JsonNode jsonNode = adminSettings.getJsonValue();
        Assert.assertNotNull(jsonNode.get("tokenExpirationTime"));
        Assert.assertNotNull(jsonNode.get("refreshTokenExpTime"));
        Assert.assertNotNull(jsonNode.get("tokenIssuer"));
        Assert.assertNotNull(jsonNode.get("tokenSigningKey"));
    }

    private void validateCustomMenuAdminSettings(AdminSettings adminSettings) {
        Assert.assertNotNull(adminSettings.getJsonValue().get("value"));
    }

    private void validateAssetProfiles(int expectedMsgCnt) throws Exception {
        List<AssetProfileUpdateMsg> assetProfileUpdateMsgs = edgeImitator.findAllMessagesByType(AssetProfileUpdateMsg.class);
        Assert.assertEquals(expectedMsgCnt, assetProfileUpdateMsgs.size());
        AssetProfileUpdateMsg assetProfileUpdateMsg = assetProfileUpdateMsgs.get(0);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, assetProfileUpdateMsg.getMsgType());
        UUID assetProfileUUID = new UUID(assetProfileUpdateMsg.getIdMSB(), assetProfileUpdateMsg.getIdLSB());
        AssetProfile assetProfile = doGet("/api/assetProfile/" + assetProfileUUID, AssetProfile.class);
        Assert.assertNotNull(assetProfile);
        Assert.assertEquals("default", assetProfile.getName());
        Assert.assertTrue(assetProfile.isDefault());
        testAutoGeneratedCodeByProtobuf(assetProfileUpdateMsg);
    }

    private void validateQueues() throws Exception {
        Optional<QueueUpdateMsg> queueUpdateMsgOpt = edgeImitator.findMessageByType(QueueUpdateMsg.class);
        Assert.assertTrue(queueUpdateMsgOpt.isPresent());
        QueueUpdateMsg queueUpdateMsg = queueUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, queueUpdateMsg.getMsgType());
        UUID queueUUID = new UUID(queueUpdateMsg.getIdMSB(), queueUpdateMsg.getIdLSB());
        Queue queue = doGet("/api/queues/" + queueUUID, Queue.class);
        Assert.assertNotNull(queue);
        Assert.assertEquals(DataConstants.MAIN_QUEUE_NAME, queue.getName());
        Assert.assertEquals(DataConstants.MAIN_QUEUE_TOPIC, queue.getTopic());
        Assert.assertEquals(10, queue.getPartitions());
        Assert.assertEquals(25, queue.getPollInterval());
        testAutoGeneratedCodeByProtobuf(queueUpdateMsg);
    }

    private void validateEntityGroups() {
        List<EntityGroupUpdateMsg> entityGroupUpdateMsgList = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(3, entityGroupUpdateMsgList.size());
    }

    private void validateRoles() {
        List<RoleProto> roleProtoList = edgeImitator.findAllMessagesByType(RoleProto.class);
        Assert.assertEquals(5, roleProtoList.size());
    }

    private void validatePublicCustomer() throws Exception {
        Optional<CustomerUpdateMsg> customerUpdateMsgOpt = edgeImitator.findMessageByType(CustomerUpdateMsg.class);
        Assert.assertTrue(customerUpdateMsgOpt.isPresent());
        CustomerUpdateMsg customerUpdateMsg = customerUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerUpdateMsg.getMsgType());
        UUID customerUUID = new UUID(customerUpdateMsg.getIdMSB(), customerUpdateMsg.getIdLSB());
        Customer customer = doGet("/api/customer/" + customerUUID, Customer.class);
        Assert.assertNotNull(customer);
        Assert.assertTrue(customer.isPublic());
    }

    private void validateWhiteLabeling() {
        List<WhiteLabelingProto> whiteLabelingProto = edgeImitator.findAllMessagesByType(WhiteLabelingProto.class);
        Assert.assertEquals(2, whiteLabelingProto.size());
        Optional<WhiteLabelingProto> loginWhiteLabeling =
                whiteLabelingProto.stream().filter(login -> {
                    WhiteLabeling whiteLabeling = JacksonUtil.fromString(login.getEntity(), WhiteLabeling.class, true);
                    Assert.assertNotNull(whiteLabeling);
                    return WhiteLabelingType.LOGIN.equals(whiteLabeling.getType());
                }).findAny();
        Assert.assertTrue(loginWhiteLabeling.isPresent());
        Optional<WhiteLabelingProto> generalWhiteLabeling =
                whiteLabelingProto.stream().filter(general -> {
                    WhiteLabeling whiteLabeling = JacksonUtil.fromString(general.getEntity(), WhiteLabeling.class, true);
                    Assert.assertNotNull(whiteLabeling);
                    return WhiteLabelingType.LOGIN.equals(whiteLabeling.getType());
                }).findAny();
        Assert.assertTrue(generalWhiteLabeling.isPresent());
    }

    private void validateOAuth2() throws Exception {
        Optional<OAuth2UpdateMsg> oAuth2UpdateMsgOpt = edgeImitator.findMessageByType(OAuth2UpdateMsg.class);
        Assert.assertTrue(oAuth2UpdateMsgOpt.isPresent());
        OAuth2UpdateMsg oAuth2ProviderUpdateMsg = oAuth2UpdateMsgOpt.get();
        OAuth2Client oAuth2Client = JacksonUtil.fromString(oAuth2ProviderUpdateMsg.getEntity(), OAuth2Client.class, true);
        Assert.assertNotNull(oAuth2Client);
        OAuth2Client auth2Info = doGet("/api/oauth2/config", OAuth2Client.class);
        Assert.assertNotNull(auth2Info);
        Assert.assertEquals(oAuth2Client, auth2Info);
        testAutoGeneratedCodeByProtobuf(oAuth2ProviderUpdateMsg);
    }

    private void validateSyncCompleted() {
        Optional<SyncCompletedMsg> syncCompletedMsgOpt = edgeImitator.findMessageByType(SyncCompletedMsg.class);
        Assert.assertTrue(syncCompletedMsgOpt.isPresent());
    }

    protected Device saveDeviceOnCloudAndVerifyDeliveryToEdge() throws Exception {
        // create device and assign to edge
        edgeImitator.expectMessageAmount(1);
        EntityGroup deviceEntityGroup = new EntityGroup();
        deviceEntityGroup.setType(EntityType.DEVICE);
        deviceEntityGroup.setName(StringUtils.randomAlphanumeric(15));
        deviceEntityGroup = doPost("/api/entityGroup", deviceEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityGroup/" + deviceEntityGroup.getId().toString() + "/" + EntityType.DEVICE.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityGroupUpdateMsg(edgeImitator.getLatestMessage(), deviceEntityGroup);

        edgeImitator.expectMessageAmount(2);
        Device savedDevice = saveDevice(StringUtils.randomAlphanumeric(15), THERMOSTAT_DEVICE_PROFILE_NAME, deviceEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());

        Optional<DeviceProfileUpdateMsg> deviceProfileUpdateMsgOpt = edgeImitator.findMessageByType(DeviceProfileUpdateMsg.class);
        Assert.assertTrue(deviceProfileUpdateMsgOpt.isPresent());
        DeviceProfileUpdateMsg deviceProfileUpdateMsg = deviceProfileUpdateMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, deviceProfileUpdateMsg.getMsgType());
        Assert.assertEquals(thermostatDeviceProfile.getUuidId().getMostSignificantBits(), deviceProfileUpdateMsg.getIdMSB());
        Assert.assertEquals(thermostatDeviceProfile.getUuidId().getLeastSignificantBits(), deviceProfileUpdateMsg.getIdLSB());
        return savedDevice;
    }

    protected Device saveDevice(String deviceName, String type) {
        return saveDevice(deviceName, type, null);
    }

    protected Device saveDevice(String deviceName, String type, EntityGroupId entityGroupId) {
        Device device = new Device();
        device.setName(deviceName);
        device.setType(type);
        if (entityGroupId != null) {
            return doPost("/api/device?entityGroupId={entityGroupId}", device, Device.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/device", device, Device.class);
        }
    }

    protected Asset saveAsset(String assetName) {
        return saveAsset(assetName, "default", null);
    }

    protected Asset saveAsset(String assetName, String type, EntityGroupId entityGroupId) {
        Asset asset = new Asset();
        asset.setName(assetName);
        asset.setType(type);
        if (entityGroupId != null) {
            return doPost("/api/asset?entityGroupId={entityGroupId}", asset, Asset.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/asset", asset, Asset.class);
        }
    }

    protected OtaPackageInfo saveOtaPackageInfo(DeviceProfileId deviceProfileId, OtaPackageType type) {
        SaveOtaPackageInfoRequest firmwareInfo = new SaveOtaPackageInfoRequest();
        firmwareInfo.setDeviceProfileId(deviceProfileId);
        firmwareInfo.setType(type);
        firmwareInfo.setTitle(type.name() + " Edge " + StringUtils.randomAlphanumeric(3));
        firmwareInfo.setVersion("v1.0");
        firmwareInfo.setTag("My " + type.name() + " #1 v1.0");
        firmwareInfo.setUsesUrl(true);
        firmwareInfo.setUrl("http://localhost:8080/v1/package");
        firmwareInfo.setAdditionalInfo(JacksonUtil.newObjectNode());
        firmwareInfo.setChecksumAlgorithm(ChecksumAlgorithm.SHA256);
        return doPost("/api/otaPackage", firmwareInfo, OtaPackageInfo.class);
    }

    protected EdgeEvent constructEdgeEvent(TenantId tenantId, EdgeId edgeId, EdgeEventActionType edgeEventAction,
                                           UUID entityId, EdgeEventType edgeEventType, JsonNode entityBody) {
        EdgeEvent edgeEvent = new EdgeEvent();
        edgeEvent.setEdgeId(edgeId);
        edgeEvent.setTenantId(tenantId);
        edgeEvent.setAction(edgeEventAction);
        edgeEvent.setEntityId(entityId);
        edgeEvent.setType(edgeEventType);
        edgeEvent.setBody(entityBody);
        return edgeEvent;
    }

    protected void testAutoGeneratedCodeByProtobuf(MessageLite.Builder builder) throws InvalidProtocolBufferException {
        MessageLite source = builder.build();

        testAutoGeneratedCodeByProtobuf(source);

        MessageLite target = source.getParserForType().parseFrom(source.toByteArray());
        builder.clear().mergeFrom(target);
    }

    protected void testAutoGeneratedCodeByProtobuf(MessageLite source) throws InvalidProtocolBufferException {
        MessageLite target = source.getParserForType().parseFrom(source.toByteArray());
        Assert.assertEquals(source, target);
        Assert.assertEquals(source.hashCode(), target.hashCode());
    }

    protected Asset saveAssetOnCloudAndVerifyDeliveryToEdge() throws Exception {
        edgeImitator.expectMessageAmount(1);
        EntityGroup assetEntityGroup = new EntityGroup();
        assetEntityGroup.setType(EntityType.ASSET);
        assetEntityGroup.setName(StringUtils.randomAlphanumeric(15));
        assetEntityGroup = doPost("/api/entityGroup", assetEntityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityGroup/" + assetEntityGroup.getId().toString() + "/" + EntityType.ASSET.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityGroupUpdateMsg(edgeImitator.getLatestMessage(), assetEntityGroup);

        edgeImitator.expectMessageAmount(3); // asset + assetProfile, assetProfile
        Asset savedAsset = saveAsset(StringUtils.randomAlphanumeric(15), "Building", assetEntityGroup.getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        return savedAsset;
    }

    protected void verifyEntityGroupUpdateMsg(AbstractMessage latestMessage, EntityGroup entityGroup) {
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        EntityGroup entityGroupMsg = JacksonUtil.fromString(entityGroupUpdateMsg.getEntity(), EntityGroup.class, true);
        Assert.assertNotNull(entityGroupMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(entityGroup, entityGroupMsg);
    }

    protected EntityView saveEntityView(String name, DeviceId deviceId, EntityGroupId entityGroupId) {
        EntityView entityView = new EntityView();
        entityView.setName(name);
        entityView.setType("test");
        entityView.setEntityId(deviceId);
        if (entityGroupId != null) {
            return doPost("/api/entityView?entityGroupId={entityGroupId}", entityView, EntityView.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/entityView", entityView, EntityView.class);
        }
    }

    protected Customer saveCustomer(String title, CustomerId parentCustomerId) {
        Customer customer = new Customer();
        customer.setTitle(title);
        customer.setParentCustomerId(parentCustomerId);
        return doPost("/api/customer", customer, Customer.class);
    }

    protected void changeEdgeOwnerToCustomer(Customer customer) throws Exception {
        edgeImitator.expectMessageAmount(6);
        doPost("/api/owner/CUSTOMER/" + customer.getId().getId() + "/EDGE/" + edge.getId().getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<CustomerUpdateMsg> customerUpdateMsgs = edgeImitator.findMessageByType(CustomerUpdateMsg.class);
        Assert.assertTrue(customerUpdateMsgs.isPresent());
        CustomerUpdateMsg customerAUpdateMsg = customerUpdateMsgs.get();
        Customer customerMsg = JacksonUtil.fromString(customerAUpdateMsg.getEntity(), Customer.class, true);
        Assert.assertNotNull(customerMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerAUpdateMsg.getMsgType());
        Assert.assertEquals(customer.getUuidId().getMostSignificantBits(), customerAUpdateMsg.getIdMSB());
        Assert.assertEquals(customer.getUuidId().getLeastSignificantBits(), customerAUpdateMsg.getIdLSB());
        Assert.assertEquals(customer.getTitle(), customerMsg.getTitle());

        List<RoleProto> roleProtos = edgeImitator.findAllMessagesByType(RoleProto.class);
        Assert.assertEquals(2, roleProtos.size());

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgs = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(2, entityGroupUpdateMsgs.size());

        Optional<EdgeConfiguration> edgeConfigurationOpt = edgeImitator.findMessageByType(EdgeConfiguration.class);
        Assert.assertTrue(edgeConfigurationOpt.isPresent());
    }

    protected RuleChainId createEdgeRuleChainAndAssignToEdge(String ruleChainName) throws Exception {
        edgeImitator.expectMessageAmount(1);
        RuleChain ruleChain = new RuleChain();
        ruleChain.setName(ruleChainName);
        ruleChain.setType(RuleChainType.EDGE);
        RuleChain savedRuleChain = doPost("/api/ruleChain", ruleChain, RuleChain.class);
        doPost("/api/edge/" + edge.getUuidId()
                + "/ruleChain/" + savedRuleChain.getUuidId(), RuleChain.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        return savedRuleChain.getId();
    }

    protected void unAssignFromEdgeAndDeleteRuleChain(RuleChainId ruleChainId) throws Exception {
        edgeImitator.expectMessageAmount(2);
        doDelete("/api/edge/" + edge.getUuidId()
                + "/ruleChain/" + ruleChainId.getId(), RuleChain.class);

        // delete rule chain
        doDelete("/api/ruleChain/" + ruleChainId.getId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
    }

    protected Dashboard saveDashboard(String dashboardTitle, EntityGroupId entityGroupId) {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle(dashboardTitle);
        if (entityGroupId != null) {
            return doPost("/api/dashboard?entityGroupId={entityGroupId}", dashboard, Dashboard.class, entityGroupId.getId().toString());
        } else {
            return doPost("/api/dashboard", dashboard, Dashboard.class);
        }
    }

    protected void changeEdgeOwnerFromCustomerToCustomer(Customer previousCustomer, Customer newCustomer, int expectedNumberOfDeleteEntityGroupMsgs) throws Exception {
        edgeImitator.expectMessageAmount(7 + expectedNumberOfDeleteEntityGroupMsgs);
        doPost("/api/owner/CUSTOMER/" + newCustomer.getId().getId() + "/EDGE/" + edge.getId().getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        List<CustomerUpdateMsg> customerMsgs = edgeImitator.findAllMessagesByType(CustomerUpdateMsg.class);
        Assert.assertEquals(2, customerMsgs.size());

        CustomerUpdateMsg previousCustomerDeleteMsg = customerMsgs.get(0);
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, previousCustomerDeleteMsg.getMsgType());
        Assert.assertEquals(previousCustomer.getUuidId().getMostSignificantBits(), previousCustomerDeleteMsg.getIdMSB());
        Assert.assertEquals(previousCustomer.getUuidId().getLeastSignificantBits(), previousCustomerDeleteMsg.getIdLSB());

        CustomerUpdateMsg newCustomerUpdateMsg =  customerMsgs.get(1);
        Customer customerMsg = JacksonUtil.fromString(newCustomerUpdateMsg.getEntity(), Customer.class, true);
        Assert.assertNotNull(customerMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, newCustomerUpdateMsg.getMsgType());
        Assert.assertEquals(newCustomer.getUuidId().getMostSignificantBits(), newCustomerUpdateMsg.getIdMSB());
        Assert.assertEquals(newCustomer.getUuidId().getLeastSignificantBits(), newCustomerUpdateMsg.getIdLSB());
        Assert.assertEquals(newCustomer.getTitle(), customerMsg.getTitle());

        List<RoleProto> roleProtos = edgeImitator.findAllMessagesByType(RoleProto.class);
        Assert.assertEquals(2, roleProtos.size());

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgs = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(2 + expectedNumberOfDeleteEntityGroupMsgs, entityGroupUpdateMsgs.size());

        Optional<EdgeConfiguration> edgeConfigurationOpt = edgeImitator.findMessageByType(EdgeConfiguration.class);
        Assert.assertTrue(edgeConfigurationOpt.isPresent());
    }

    protected void changeEdgeOwnerFromCustomerToTenant(Customer customer, int expectedNumberOfDeleteEntityGroupMsgs) throws Exception {
        edgeImitator.expectMessageAmount(2 + expectedNumberOfDeleteEntityGroupMsgs);
        doPost("/api/owner/TENANT/" + tenantId.getId() + "/EDGE/" + edge.getId().getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<CustomerUpdateMsg> customerDeleteMsgs = edgeImitator.findMessageByType(CustomerUpdateMsg.class);
        Assert.assertTrue(customerDeleteMsgs.isPresent());
        CustomerUpdateMsg customerADeleteMsg = customerDeleteMsgs.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, customerADeleteMsg.getMsgType());
        Assert.assertEquals(customer.getUuidId().getMostSignificantBits(), customerADeleteMsg.getIdMSB());
        Assert.assertEquals(customer.getUuidId().getLeastSignificantBits(), customerADeleteMsg.getIdLSB());

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgs = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(expectedNumberOfDeleteEntityGroupMsgs, entityGroupUpdateMsgs.size());
    }

    protected void changeEdgeOwnerFromTenantToSubCustomer(Customer parentCustomer, Customer customer) throws Exception {
        edgeImitator.expectMessageAmount(11);
        doPost("/api/owner/CUSTOMER/" + customer.getId().getId() + "/EDGE/" + edge.getId().getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        List<CustomerUpdateMsg> customerUpdateMsgs = edgeImitator.findAllMessagesByType(CustomerUpdateMsg.class);
        Assert.assertEquals(2, customerUpdateMsgs.size());
        CustomerUpdateMsg customerAUpdateMsg = customerUpdateMsgs.get(0);
        Customer customerMsg = JacksonUtil.fromString(customerAUpdateMsg.getEntity(), Customer.class, true);
        Assert.assertNotNull(customerMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, customerAUpdateMsg.getMsgType());
        Assert.assertEquals(parentCustomer.getUuidId().getMostSignificantBits(), customerAUpdateMsg.getIdMSB());
        Assert.assertEquals(parentCustomer.getUuidId().getLeastSignificantBits(), customerAUpdateMsg.getIdLSB());
        Assert.assertEquals(parentCustomer.getTitle(), customerMsg.getTitle());
        testAutoGeneratedCodeByProtobuf(customerAUpdateMsg);
        CustomerUpdateMsg subCustomerAUpdateMsg = customerUpdateMsgs.get(1);
        customerMsg = JacksonUtil.fromString(subCustomerAUpdateMsg.getEntity(), Customer.class, true);
        Assert.assertNotNull(customerMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, subCustomerAUpdateMsg.getMsgType());
        Assert.assertEquals(customer.getUuidId().getMostSignificantBits(), subCustomerAUpdateMsg.getIdMSB());
        Assert.assertEquals(customer.getUuidId().getLeastSignificantBits(), subCustomerAUpdateMsg.getIdLSB());
        Assert.assertEquals(customer.getTitle(), customerMsg.getTitle());
        Assert.assertEquals(EntityType.CUSTOMER, customerMsg.getOwnerId().getEntityType());
        Assert.assertEquals(parentCustomer.getId(), customerMsg.getOwnerId());

        List<RoleProto> roleProtos = edgeImitator.findAllMessagesByType(RoleProto.class);
        Assert.assertEquals(4, roleProtos.size());

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgs = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(4, entityGroupUpdateMsgs.size());

        Optional<EdgeConfiguration> edgeConfigurationOpt = edgeImitator.findMessageByType(EdgeConfiguration.class);
        Assert.assertTrue(edgeConfigurationOpt.isPresent());
    }

    protected void changeEdgeOwnerFromSubCustomerToTenant(Customer parentCustomer, Customer customer, int expectedNumberOfDeleteEntityGroupMsgs) throws Exception {
        edgeImitator.expectMessageAmount(3 + expectedNumberOfDeleteEntityGroupMsgs);
        doPost("/api/owner/TENANT/" + tenantId.getId() + "/EDGE/" + edge.getId().getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        List<CustomerUpdateMsg> customerDeleteMsgs = edgeImitator.findAllMessagesByType(CustomerUpdateMsg.class);
        Assert.assertEquals(2, customerDeleteMsgs.size());
        CustomerUpdateMsg customerADeleteMsg = customerDeleteMsgs.get(0);
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, customerADeleteMsg.getMsgType());
        Assert.assertEquals(parentCustomer.getUuidId().getMostSignificantBits(), customerADeleteMsg.getIdMSB());
        Assert.assertEquals(parentCustomer.getUuidId().getLeastSignificantBits(), customerADeleteMsg.getIdLSB());
        CustomerUpdateMsg subCustomerADeleteMsg = customerDeleteMsgs.get(1);
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, subCustomerADeleteMsg.getMsgType());
        Assert.assertEquals(customer.getUuidId().getMostSignificantBits(), subCustomerADeleteMsg.getIdMSB());
        Assert.assertEquals(customer.getUuidId().getLeastSignificantBits(), subCustomerADeleteMsg.getIdLSB());

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgs = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(expectedNumberOfDeleteEntityGroupMsgs, entityGroupUpdateMsgs.size());
    }

    protected void changeEdgeOwnerFromSubCustomerToCustomer(Customer parentCustomer, Customer customer, int expectedNumberOfDeleteEntityGroupMsgs) throws Exception {
        edgeImitator.expectMessageAmount(2 + expectedNumberOfDeleteEntityGroupMsgs);
        doPost("/api/owner/CUSTOMER/" + parentCustomer.getId().getId() + "/EDGE/" + edge.getId().getId());
        Assert.assertTrue(edgeImitator.waitForMessages());
        Optional<CustomerUpdateMsg> customerDeleteMsgOpt = edgeImitator.findMessageByType(CustomerUpdateMsg.class);
        Assert.assertTrue(customerDeleteMsgOpt.isPresent());
        CustomerUpdateMsg customerDeleteMsg = customerDeleteMsgOpt.get();
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, customerDeleteMsg.getMsgType());
        Assert.assertEquals(customer.getUuidId().getMostSignificantBits(), customerDeleteMsg.getIdMSB());
        Assert.assertEquals(customer.getUuidId().getLeastSignificantBits(), customerDeleteMsg.getIdLSB());

        List<EntityGroupUpdateMsg> entityGroupUpdateMsgs = edgeImitator.findAllMessagesByType(EntityGroupUpdateMsg.class);
        Assert.assertEquals(expectedNumberOfDeleteEntityGroupMsgs, entityGroupUpdateMsgs.size());
    }

    protected EntityGroup createEntityGroupAndAssignToEdge(EntityType groupType, String groupName, EntityId ownerId) throws Exception {
        edgeImitator.expectMessageAmount(1);
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setType(groupType);
        entityGroup.setName(groupName);
        entityGroup.setOwnerId(ownerId);
        EntityGroup savedEntityGroup = doPost("/api/entityGroup", entityGroup, EntityGroup.class);
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityGroup/" + savedEntityGroup.getId().toString() + "/" + groupType.name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        EntityGroup entityGroupMsg = JacksonUtil.fromString(entityGroupUpdateMsg.getEntity(), EntityGroup.class, true);
        Assert.assertNotNull(entityGroupMsg);
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(savedEntityGroup, entityGroupMsg);
        testAutoGeneratedCodeByProtobuf(entityGroupUpdateMsg);
        return savedEntityGroup;
    }

    protected void unAssignEntityGroupFromEdge(EntityGroup entityGroup) throws Exception {
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getUuidId()
                + "/entityGroup/" + entityGroup.getUuidId().toString() + "/" + entityGroup.getType().name(), EntityGroup.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityGroupUpdateMsg);
        EntityGroupUpdateMsg entityGroupUpdateMsg = (EntityGroupUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityGroupUpdateMsg.getMsgType());
        Assert.assertEquals(entityGroup.getUuidId().getMostSignificantBits(), entityGroupUpdateMsg.getIdMSB());
        Assert.assertEquals(entityGroup.getUuidId().getLeastSignificantBits(), entityGroupUpdateMsg.getIdLSB());
    }

    protected void validateThatEntityGroupAssignedToEdge(EntityGroupId entityGroupId, EntityType groupType) throws Exception {
        validateThatEntityGroupAssignedOrNotToEdge(entityGroupId, groupType, true);
    }

    protected void validateThatEntityGroupNotAssignedToEdge(EntityGroupId entityGroupId, EntityType groupType) throws Exception {
        validateThatEntityGroupAssignedOrNotToEdge(entityGroupId, groupType, false);
    }

    private void validateThatEntityGroupAssignedOrNotToEdge(EntityGroupId entityGroupId, EntityType groupType, boolean assigned) throws Exception {
        List<EntityGroupInfo> entityGroupInfos =
                JacksonUtil.convertValue(doGet("/api/allEntityGroups/edge/" + edge.getUuidId() + "/" + groupType.name(), JsonNode.class), new TypeReference<>() {});
        Assert.assertNotNull(entityGroupInfos);
        List<EntityGroupId> entityGroupIds = entityGroupInfos.stream().map(EntityGroup::getId).collect(Collectors.toList());
        Assert.assertEquals(assigned, entityGroupIds.contains(entityGroupId));
    }

    protected List<EntityGroupInfo> getEntityGroupsByOwnerAndType(EntityId ownerId, EntityType groupType) throws Exception {
        return JacksonUtil.convertValue(
                        doGet("/api/entityGroups/" + ownerId.getEntityType() + "/" + ownerId.getId() + "/" + groupType.name(), JsonNode.class),
                        new TypeReference<>() {});
    }

    protected void addEntitiesToEntityGroup(List<EntityId> entityIds, EntityGroupId entityGroupId) throws Exception {
        Object[] entityIdsArray = entityIds.stream().map(entityId -> entityId.getId().toString()).toArray();
        doPost("/api/entityGroup/" + entityGroupId.getId() + "/addEntities", entityIdsArray);
    }

    protected void deleteEntitiesFromEntityGroup(List<EntityId> entityIds, EntityGroupId entityGroupId) throws Exception {
        Object[] entityIdsArray = entityIds.stream().map(entityId -> entityId.getId().toString()).toArray();
        doPost("/api/entityGroup/" + entityGroupId.getId() + "/deleteEntities", entityIdsArray);
    }

    protected EntityGroupInfo findGroupByOwnerIdTypeAndName(EntityId ownerId, EntityType groupType, String name) throws Exception {
        List<EntityGroupInfo> groupsList = getEntityGroupsByOwnerAndType(ownerId, groupType);
        EntityGroupInfo result = null;
        for (EntityGroupInfo tmp : groupsList) {
            if (name.equals(tmp.getName())) {
                result = tmp;
            }
        }
        Assert.assertNotNull(result);
        return result;
    }

    protected EntityGroupInfo findCustomerAdminsGroup(Customer savedCustomer) throws Exception {
        return findGroupByOwnerIdTypeAndName(savedCustomer.getId(), EntityType.USER, EntityGroup.GROUP_CUSTOMER_ADMINS_NAME);
    }

    protected EntityGroupInfo findTenantAdminsGroup() throws Exception {
        return findGroupByOwnerIdTypeAndName(tenantId, EntityType.USER, EntityGroup.GROUP_TENANT_ADMINS_NAME);
    }

    protected ObjectNode getCustomTranslationHomeObject(String homeValue) {
        ObjectNode objectNode = JacksonUtil.newObjectNode();
        return objectNode.put("home", homeValue);
    }

    private void verifyTenantAdministratorsAndTenantUsersAssignedToEdge() {
        verifyGroupTenantNameAssignedToEdge(EntityGroup.GROUP_TENANT_ADMINS_NAME);
        verifyGroupTenantNameAssignedToEdge(EntityGroup.GROUP_TENANT_USERS_NAME);
    }

    private void verifyGroupTenantNameAssignedToEdge(String groupTenantName) {
        Awaitility.await()
                .atMost(TIMEOUT, TimeUnit.SECONDS)
                .alias("verifyGroupTenantNameAssignedToEdge {" + groupTenantName + "}")
                .until(() -> getEntityGroupsByOwnerAndType(tenantId, EntityType.USER).stream()
                        .map(EntityGroupInfo::getName)
                        .filter(groupTenantName::equals)
                        .count() == 1);
    }

    protected List<EntityGroupId> getEntityGroupsIdsForEntity(EntityId entityId) throws Exception {
        return JacksonUtil.convertValue(
                doGet("/api/entityGroups/" + entityId.getEntityType() + "/" + entityId.getId(), JsonNode.class),
                new TypeReference<>() {});
    }

}
