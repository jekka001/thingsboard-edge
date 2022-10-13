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
package org.thingsboard.server.msa;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rest.client.RestClient;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.device.profile.AlarmCondition;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilter;
import org.thingsboard.server.common.data.device.profile.AlarmConditionFilterKey;
import org.thingsboard.server.common.data.device.profile.AlarmConditionKeyType;
import org.thingsboard.server.common.data.device.profile.AlarmRule;
import org.thingsboard.server.common.data.device.profile.AllowCreateNewDevicesDeviceProfileProvisionConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultDeviceProfileConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.DeviceProfileAlarm;
import org.thingsboard.server.common.data.device.profile.DeviceProfileData;
import org.thingsboard.server.common.data.device.profile.DeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.SimpleAlarmConditionSpec;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.IdBased;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.QueueId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.id.WidgetsBundleId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.query.EntityKeyValueType;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.query.NumericFilterPredicate;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractContainerTest {

    private static final String CUSTOM_DEVICE_PROFILE_NAME = "Custom Device Profile";

    protected static final String CLOUD_HTTPS_URL = "https://localhost";
    protected static final String WSS_URL = "wss://localhost";
    protected static RestClient cloudRestClient = null;

    protected static RestClient edgeRestClient;

    protected static Edge edge;
    protected static String edgeUrl;

    @BeforeClass
    public static void before() throws Exception {
        if (cloudRestClient == null) {
            cloudRestClient = new RestClient(CLOUD_HTTPS_URL);
            cloudRestClient.getRestTemplate().setRequestFactory(getRequestFactoryForSelfSignedCert());
            cloudRestClient.login("tenant@thingsboard.org", "tenant");

            String edgeHost = ContainerTestSuite.testContainer.getServiceHost("tb-edge", 8082);
            Integer edgePort = ContainerTestSuite.testContainer.getServicePort("tb-edge", 8082);
            edgeUrl = "http://" + edgeHost + ":" + edgePort;
            edgeRestClient = new RestClient(edgeUrl);

            setWhiteLabelingAndCustomTranslation();

            Awaitility.await()
                    .pollInterval(500, TimeUnit.MILLISECONDS)
                    .atMost(30, TimeUnit.SECONDS).
                    until(() -> {
                                Optional<LoginWhiteLabelingParams> cloudLoginWhiteLabelParams = cloudRestClient.getCurrentLoginWhiteLabelParams();
                                return cloudLoginWhiteLabelParams.isPresent() &&
                                        "tenant.org".equals(cloudLoginWhiteLabelParams.get().getDomainName());
                            });


            edge = createEdge("test", "280629c7-f853-ee3d-01c0-fffbb6f2ef38", "g9ta4soeylw6smqkky8g");

            loginIntoEdgeWithRetries("tenant@thingsboard.org", "tenant");

            Optional<Tenant> tenant = edgeRestClient.getTenantById(edge.getTenantId());
            Assert.assertTrue(tenant.isPresent());
            Assert.assertEquals(edge.getTenantId(), tenant.get().getId());

            updateRootRuleChain();

            createCustomDeviceProfile(CUSTOM_DEVICE_PROFILE_NAME);

            // This is a starting point to start other tests
            verifyWidgetBundles();
        }
    }

    protected static void loginIntoEdgeWithRetries(String userName, String password) {
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(90, TimeUnit.SECONDS)
                .until(() -> {
                    boolean loginSuccessful = false;
                    try {
                        edgeRestClient.login(userName, password);
                        loginSuccessful = true;
                    } catch (Exception ignored) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ignored2) {
                        }
                    }
                    return loginSuccessful;
                });
    }

    private static void verifyWidgetBundles() {
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  {
                    try {
                        return edgeRestClient.getWidgetsBundles(new PageLink(100)).getTotalElements() == 16;
                    } catch (Throwable e) {
                        return false;
                    }
                });

        PageData<WidgetsBundle> pageData = edgeRestClient.getWidgetsBundles(new PageLink(100));

        for (String widgetsBundlesAlias : pageData.getData().stream().map(WidgetsBundle::getAlias).collect(Collectors.toList())) {
            Awaitility.await()
                    .pollInterval(1000, TimeUnit.MILLISECONDS)
                    .atMost(60, TimeUnit.SECONDS).
                    until(() -> {
                        try {
                            List<WidgetType> edgeBundleWidgetTypes = edgeRestClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
                            List<WidgetType> cloudBundleWidgetTypes = cloudRestClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
                            return cloudBundleWidgetTypes != null && edgeBundleWidgetTypes != null
                                    && edgeBundleWidgetTypes.size() == cloudBundleWidgetTypes.size();
                        } catch (Throwable e) {
                            return false;
                        }
                    });
            List<WidgetType> edgeBundleWidgetTypes = edgeRestClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
            List<WidgetType> cloudBundleWidgetTypes = cloudRestClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
            Assert.assertNotNull("edgeBundleWidgetTypes can't be null", edgeBundleWidgetTypes);
            Assert.assertNotNull("cloudBundleWidgetTypes can't be null", cloudBundleWidgetTypes);
        }
    }

    protected static void updateRootRuleChain() throws IOException {
        PageData<RuleChain> ruleChains = cloudRestClient.getRuleChains(new PageLink(100));
        RuleChainId rootRuleChainId = null;
        for (RuleChain datum : ruleChains.getData()) {
            if (datum.isRoot()) {
                rootRuleChainId = datum.getId();
                break;
            }
        }
        Assert.assertNotNull(rootRuleChainId);
        JsonNode configuration = JacksonUtil.OBJECT_MAPPER.readTree(AbstractContainerTest.class.getClassLoader().getResourceAsStream("PushToEdgeRootRuleChainMetadata.json"));
        RuleChainMetaData ruleChainMetaData = new RuleChainMetaData();
        ruleChainMetaData.setRuleChainId(rootRuleChainId);
        ruleChainMetaData.setFirstNodeIndex(configuration.get("firstNodeIndex").asInt());
        ruleChainMetaData.setNodes(Arrays.asList(JacksonUtil.OBJECT_MAPPER.treeToValue(configuration.get("nodes"), RuleNode[].class)));
        ruleChainMetaData.setConnections(Arrays.asList(JacksonUtil.OBJECT_MAPPER.treeToValue(configuration.get("connections"), NodeConnectionInfo[].class)));
        cloudRestClient.saveRuleChainMetaData(ruleChainMetaData);
    }

    protected static DeviceProfile createCustomDeviceProfile(String deviceProfileName,
                                                             DeviceProfileTransportConfiguration deviceProfileTransportConfiguration) {
        DeviceProfile deviceProfile = createDeviceProfile(deviceProfileName, deviceProfileTransportConfiguration);
        extendDeviceProfileData(deviceProfile);
        return cloudRestClient.saveDeviceProfile(deviceProfile);
    }

    protected static DeviceProfile createCustomDeviceProfile(String deviceProfileName) {
        return createCustomDeviceProfile(deviceProfileName, null);
    }

    private static void setWhiteLabelingAndCustomTranslation() throws JsonProcessingException {
        cloudRestClient.login("sysadmin@thingsboard.org", "sysadmin");

        CustomTranslation content = new CustomTranslation();
        ObjectNode enUsSysAdmin = JacksonUtil.OBJECT_MAPPER.createObjectNode();
        enUsSysAdmin.put("home.home", "SYS_ADMIN_HOME");
        content.getTranslationMap().put("en_us", JacksonUtil.OBJECT_MAPPER.writeValueAsString(enUsSysAdmin));
        cloudRestClient.saveCustomTranslation(content);

        WhiteLabelingParams whiteLabelingParams = new WhiteLabelingParams();
        whiteLabelingParams.setAppTitle("Sys Admin TB");
        cloudRestClient.saveWhiteLabelParams(whiteLabelingParams);

        LoginWhiteLabelingParams loginWhiteLabelingParams = new LoginWhiteLabelingParams();
        loginWhiteLabelingParams.setDomainName("sysadmin.org");
        cloudRestClient.saveLoginWhiteLabelParams(loginWhiteLabelingParams);

        cloudRestClient.login("tenant@thingsboard.org", "tenant");

        content = new CustomTranslation();
        ObjectNode enUsTenant = JacksonUtil.OBJECT_MAPPER.createObjectNode();
        enUsTenant.put("home.home", "TENANT_HOME");
        content.getTranslationMap().put("en_us", JacksonUtil.OBJECT_MAPPER.writeValueAsString(enUsTenant));
        cloudRestClient.saveCustomTranslation(content);

        whiteLabelingParams = new WhiteLabelingParams();
        whiteLabelingParams.setAppTitle("Tenant TB");
        cloudRestClient.saveWhiteLabelParams(whiteLabelingParams);

        loginWhiteLabelingParams = new LoginWhiteLabelingParams();
        loginWhiteLabelingParams.setDomainName("tenant.org");
        cloudRestClient.saveLoginWhiteLabelParams(loginWhiteLabelingParams);
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            log.info("=================================================");
            log.info("STARTING TEST: {}" , description.getMethodName());
            log.info("=================================================");
        }

        /**
         * Invoked when a test succeeds
         */
        protected void succeeded(Description description) {
            log.info("=================================================");
            log.info("SUCCEEDED TEST: {}" , description.getMethodName());
            log.info("=================================================");
        }

        /**
         * Invoked when a test fails
         */
        protected void failed(Throwable e, Description description) {
            log.info("=================================================");
            log.info("FAILED TEST: {}" , description.getMethodName(), e);
            log.info("=================================================");
        }
    };

    protected static DeviceProfile createDeviceProfile(String name, DeviceProfileTransportConfiguration deviceProfileTransportConfiguration) {
        DeviceProfile deviceProfile = new DeviceProfile();
        deviceProfile.setName(name);
        deviceProfile.setType(DeviceProfileType.DEFAULT);
        deviceProfile.setImage("iVBORw0KGgoAAAANSUhEUgAAAQAAAAEABA");
        deviceProfile.setTransportType(DeviceTransportType.DEFAULT);
        deviceProfile.setDescription(null);
        deviceProfile.setProvisionType(DeviceProfileProvisionType.DISABLED);
        DeviceProfileData deviceProfileData = new DeviceProfileData();
        DefaultDeviceProfileConfiguration configuration = new DefaultDeviceProfileConfiguration();
        deviceProfileData.setConfiguration(configuration);
        if (deviceProfileTransportConfiguration != null) {
            deviceProfileData.setTransportConfiguration(deviceProfileTransportConfiguration);
        } else {
            deviceProfileData.setTransportConfiguration(new DefaultDeviceProfileTransportConfiguration());
        }
        deviceProfile.setProfileData(deviceProfileData);
        deviceProfile.setDefault(false);
        deviceProfile.setDefaultRuleChainId(null);
        deviceProfile.setDefaultQueueName("Main");
        extendDeviceProfileData(deviceProfile);
        return cloudRestClient.saveDeviceProfile(deviceProfile);
    }

    protected static void extendDeviceProfileData(DeviceProfile deviceProfile) {
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

    private static HttpComponentsClientHttpRequestFactory getRequestFactoryForSelfSignedCert() throws Exception {
        SSLContextBuilder builder = SSLContexts.custom();
        builder.loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true);
        SSLContext sslContext = builder.build();
        SSLConnectionSocketFactory sslSelfSigned = new SSLConnectionSocketFactory(sslContext, (s, sslSession) -> true);

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
                .<ConnectionSocketFactory>create()
                .register("https", sslSelfSigned)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(cm).build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    protected static Edge createEdge(String name, String routingKey, String secret) {
        Edge edge = new Edge();
        edge.setName(name + RandomStringUtils.randomAlphanumeric(7));
        edge.setType("DEFAULT");
        edge.setRoutingKey(routingKey);
        edge.setSecret(secret);
        edge.setEdgeLicenseKey("123");
        edge.setCloudEndpoint("tb-monolith");
        return cloudRestClient.saveEdge(edge);
    }

    protected Device saveDeviceOnEdge(String deviceName, String type) {
        return saveDevice(deviceName, type, null, edgeRestClient);
    }

    protected Device saveDeviceOnCloud(String deviceName, String type) {
        return saveDevice(deviceName, type, null, cloudRestClient);
    }

    protected Device saveDeviceOnCloud(String deviceName, String deviceType, EntityGroupId entityGroupId) {
        return saveDevice(deviceName, deviceType, entityGroupId, cloudRestClient);
    }

    private Device saveDevice(String deviceName, String type, EntityGroupId entityGroupId, RestClient restClient) {
        Device device = new Device();
        device.setName(deviceName);
        device.setType(type);
        return restClient.saveDevice(device, null, entityGroupId);
    }

    protected Asset saveAssetAndAssignEntityGroupToEdge(EntityGroup savedAssetEntityGroup) {
        return saveAssetAndAssignEntityGroupToEdge("default", savedAssetEntityGroup);
    }

    protected Asset saveAssetAndAssignEntityGroupToEdge(String assetType, EntityGroup savedAssetEntityGroup) {
        Asset asset = saveAssetOnCloud(RandomStringUtils.randomAlphanumeric(15), assetType, savedAssetEntityGroup.getId());

        assignEntityGroupToEdge(savedAssetEntityGroup);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getAssetById(asset.getId()).isPresent());
        return asset;
    }

    protected void assignEntityGroupToEdge(EntityGroup entityGroup) {
        cloudRestClient.assignEntityGroupToEdge(edge.getId(), entityGroup.getId(), entityGroup.getType());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getEntityGroupById(entityGroup.getId()).isPresent());
    }

    protected Asset saveAssetOnCloud(String assetName, String type, EntityGroupId entityGroupId) {
        Asset asset = new Asset();
        asset.setName(assetName);
        asset.setType(type);
        return cloudRestClient.saveAsset(asset, entityGroupId);
    }

    protected Dashboard saveDashboardOnCloud(String dashboardTitle) {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle(dashboardTitle);
        return cloudRestClient.saveDashboard(dashboard);
    }

    protected void assertEntitiesByIdsAndType(List<EntityId> entityIds, EntityType entityType) {
        switch (entityType) {
            case DEVICE_PROFILE:
                assertDeviceProfiles(entityIds);
                break;
            case ASSET_PROFILE:
                assertAssetProfiles(entityIds);
                break;
            case RULE_CHAIN:
                assertRuleChains(entityIds);
                break;
            case WIDGETS_BUNDLE:
                assertWidgetsBundles(entityIds);
                break;
            case WIDGET_TYPE:
                assertWidgetTypes(entityIds);
                break;
            case DEVICE:
                assertDevices(entityIds);
                break;
            case ASSET:
                assertAssets(entityIds);
                break;
            case ENTITY_VIEW:
                assertEntityViews(entityIds);
                break;
            case DASHBOARD:
                assertDashboards(entityIds);
                break;
            case USER:
                assertUsers(entityIds);
                break;
            case OTA_PACKAGE:
                assertOtaPackages(entityIds);
                break;
            case QUEUE:
                assertQueues(entityIds);
                break;
            case ROLE:
                assertRoles(entityIds);
                break;
            case CONVERTER:
                assertConverters(entityIds);
                break;
            case INTEGRATION:
                assertIntegrations(entityIds);
                break;
        }
    }

    private void assertDeviceProfiles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DeviceProfileId deviceProfileId = new DeviceProfileId(entityId.getId());
            Optional<DeviceProfile> edgeDeviceProfile = edgeRestClient.getDeviceProfileById(deviceProfileId);
            Optional<DeviceProfile> cloudDeviceProfile = cloudRestClient.getDeviceProfileById(deviceProfileId);
            DeviceProfile expected = edgeDeviceProfile.get();
            DeviceProfile actual = cloudDeviceProfile.get();
            actual.setDefaultRuleChainId(null);
            Assert.assertEquals("Device profiles on cloud and edge are different (except defaultRuleChainId)", expected, actual);
        }
    }

    private void assertAssetProfiles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            AssetProfileId assetProfileId = new AssetProfileId(entityId.getId());
            Optional<AssetProfile> edgeAssetProfile = edgeRestClient.getAssetProfileById(assetProfileId);
            Optional<AssetProfile> cloudAssetProfile = cloudRestClient.getAssetProfileById(assetProfileId);
            AssetProfile expected = edgeAssetProfile.get();
            AssetProfile actual = cloudAssetProfile.get();
            Assert.assertEquals("Asset profiles on cloud and edge are different", expected, actual);
        }
    }

    private void assertOtaPackages(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            OtaPackageId otaPackageId = new OtaPackageId(entityId.getId());
            OtaPackage edgeOtaPackage = edgeRestClient.getOtaPackageById(otaPackageId);
            OtaPackage cloudOtaPackage = cloudRestClient.getOtaPackageById(otaPackageId);
            Assert.assertEquals("Ota packages on cloud and edge are different", edgeOtaPackage, cloudOtaPackage);
        }
    }

    private void assertQueues(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            QueueId queueId = new QueueId(entityId.getId());
            Queue edgeQueue = edgeRestClient.getQueueById(queueId);
            Queue cloudQueue = cloudRestClient.getQueueById(queueId);
            Assert.assertEquals("Queues on cloud and edge are different", edgeQueue, cloudQueue);
        }
    }

    private void assertRuleChains(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            RuleChainId ruleChainId = new RuleChainId(entityId.getId());
            Optional<RuleChain> edgeRuleChain = edgeRestClient.getRuleChainById(ruleChainId);
            Optional<RuleChain> cloudRuleChain = cloudRestClient.getRuleChainById(ruleChainId);
            RuleChain expected = edgeRuleChain.get();
            RuleChain actual = cloudRuleChain.get();
            Assert.assertEquals("Edge rule chain type is incorrect", RuleChainType.CORE, expected.getType());
            Assert.assertEquals("Cloud rule chain type is incorrect", RuleChainType.EDGE, actual.getType());
            expected.setType(null);
            actual.setType(null);
            Assert.assertEquals("Rule chains on cloud and edge are different (except type)", expected, actual);

            Awaitility.await()
                    .atMost(30, TimeUnit.SECONDS).
                    until(() -> {
                        Optional<RuleChainMetaData> edgeRuleChainMetaData = edgeRestClient.getRuleChainMetaData(ruleChainId);
                        Optional<RuleChainMetaData> cloudRuleChainMetaData = cloudRestClient.getRuleChainMetaData(ruleChainId);
                        if (edgeRuleChainMetaData.isEmpty()) {
                            return false;
                        }
                        if (cloudRuleChainMetaData.isEmpty()) {
                            return false;
                        }
                        return validateRuleChainMetadata(edgeRuleChainMetaData.get(), cloudRuleChainMetaData.get());
                    });
        }
    }

    private boolean validateRuleChainMetadata(RuleChainMetaData expectedMetadata, RuleChainMetaData actualMetadata) {
        if (!expectedMetadata.getRuleChainId().equals(actualMetadata.getRuleChainId())) {
            return false;
        }
        if (expectedMetadata.getNodes().size() != actualMetadata.getNodes().size()) {
            return false;
        }
        if (expectedMetadata.getConnections().size() != actualMetadata.getConnections().size()) {
            return false;
        }
        for (RuleNode expectedNode : expectedMetadata.getNodes()) {
            Optional<RuleNode> actualNodeOpt =
                    actualMetadata.getNodes().stream().filter(n -> n.getId().equals(expectedNode.getId())).findFirst();
            if (actualNodeOpt.isEmpty()) {
                return false;
            }
            RuleNode actualNode = actualNodeOpt.get();
            if (!expectedNode.equals(actualNode)) {
                return false;
            }
        }
        return true;
    }

    private void assertWidgetsBundles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            WidgetsBundleId widgetsBundleId = new WidgetsBundleId(entityId.getId());
            Optional<WidgetsBundle> edgeWidgetsBundle = edgeRestClient.getWidgetsBundleById(widgetsBundleId);
            Optional<WidgetsBundle> cloudWidgetsBundle = cloudRestClient.getWidgetsBundleById(widgetsBundleId);
            WidgetsBundle expected = edgeWidgetsBundle.get();
            WidgetsBundle actual = cloudWidgetsBundle.get();
            Assert.assertEquals("Widgets bundles on cloud and edge are different", expected, actual);
        }
    }

    private void assertWidgetTypes(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            WidgetTypeId widgetTypeId = new WidgetTypeId(entityId.getId());
            Optional<WidgetTypeDetails> edgeWidgetsBundle = edgeRestClient.getWidgetTypeById(widgetTypeId);
            Optional<WidgetTypeDetails> cloudWidgetsBundle = cloudRestClient.getWidgetTypeById(widgetTypeId);
            WidgetTypeDetails expected = edgeWidgetsBundle.get();
            WidgetTypeDetails actual = cloudWidgetsBundle.get();
            Assert.assertEquals("Widget types on cloud and edge are different", expected, actual);
        }
    }

    private void assertDevices(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DeviceId deviceId = new DeviceId(entityId.getId());
            Optional<Device> edgeDevice = edgeRestClient.getDeviceById(deviceId);
            Optional<Device> cloudDevice = cloudRestClient.getDeviceById(deviceId);
            Device expected = edgeDevice.get();
            Device actual = cloudDevice.get();
            Assert.assertEquals("Devices on cloud and edge are different", expected, actual);
        }
    }

    private void assertAssets(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            AssetId assetId = new AssetId(entityId.getId());
            Optional<Asset> edgeAsset = edgeRestClient.getAssetById(assetId);
            Optional<Asset> cloudAsset = cloudRestClient.getAssetById(assetId);
            Asset expected = edgeAsset.get();
            Asset actual = cloudAsset.get();
            Assert.assertEquals("Assets on cloud and edge are different", expected, actual);
        }
    }

    private void assertEntityViews(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            EntityViewId entityViewId = new EntityViewId(entityId.getId());
            Optional<EntityView> edgeEntityView = edgeRestClient.getEntityViewById(entityViewId);
            Optional<EntityView> cloudEntityView = cloudRestClient.getEntityViewById(entityViewId);
            EntityView expected = edgeEntityView.get();
            EntityView actual = cloudEntityView.get();
            Assert.assertEquals("Entity Views on cloud and edge are different", expected, actual);
        }
    }

    private void assertDashboards(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DashboardId dashboardId = new DashboardId(entityId.getId());
            Optional<Dashboard> edgeDashboard = edgeRestClient.getDashboardById(dashboardId);
            Optional<Dashboard> cloudDashboard = cloudRestClient.getDashboardById(dashboardId);
            Dashboard expected = edgeDashboard.get();
            Dashboard actual = cloudDashboard.get();
            Assert.assertEquals("Dashboards on cloud and edge are different", expected, actual);
        }
    }

    private void assertUsers(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            UserId userId = new UserId(entityId.getId());
            Optional<User> edgeUser = edgeRestClient.getUserById(userId);
            Optional<User> cloudUser = cloudRestClient.getUserById(userId);
            User expected = edgeUser.get();
            User actual = cloudUser.get();
            expected.setAdditionalInfo(cleanLastLoginTsFromAdditionalInfo(expected.getAdditionalInfo()));
            actual.setAdditionalInfo(cleanLastLoginTsFromAdditionalInfo(actual.getAdditionalInfo()));
            Assert.assertEquals("Users on cloud and edge are different (except lastLoginTs)", expected, actual);
        }
    }

    private void assertRoles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            RoleId roleId = new RoleId(entityId.getId());
            Optional<Role> edgeRole = edgeRestClient.getRoleById(roleId);
            Optional<Role> cloudRole = cloudRestClient.getRoleById(roleId);
            Role expected = edgeRole.get();
            Role actual = cloudRole.get();
            // permissions field is transient and not used in comparison
            Assert.assertEquals("Roles on cloud and edge are different", expected, actual);
        }
    }

    private void assertConverters(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            ConverterId converterId = new ConverterId(entityId.getId());
            Optional<Converter> edgeConverter = edgeRestClient.getConverterById(converterId);
            Optional<Converter> cloudConverter = cloudRestClient.getConverterById(converterId);
            Converter expected = edgeConverter.get();
            Converter actual = cloudConverter.get();
            Assert.assertEquals("Edge converter isEdgeTemplate incorrect", false, expected.isEdgeTemplate());
            Assert.assertEquals("Cloud converter isEdgeTemplate incorrect", true, actual.isEdgeTemplate());
            actual.setEdgeTemplate(false);
            Assert.assertEquals("Converters on cloud and edge are different", expected, actual);
        }
    }

    private void assertIntegrations(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            IntegrationId integrationId = new IntegrationId(entityId.getId());
            Optional<Integration> edgeIntegration = edgeRestClient.getIntegrationById(integrationId);
            Optional<Integration> cloudIntegration = cloudRestClient.getIntegrationById(integrationId);
            Integration expected = edgeIntegration.get();
            Integration actual = cloudIntegration.get();
            Assert.assertEquals("Edge integration isEdgeTemplate incorrect", false, expected.isEdgeTemplate());
            Assert.assertEquals("Cloud integration isEdgeTemplate incorrect", true, actual.isEdgeTemplate());
            actual.setEdgeTemplate(false);

            // configuration must be reset, because configuration on cloud contains placeholders
            actual.setConfiguration(null);
            expected.setConfiguration(null);

            Assert.assertEquals("Integrations on cloud and edge are different", expected, actual);
        }
    }

    private JsonNode cleanLastLoginTsFromAdditionalInfo(JsonNode additionalInfo) {
        if (additionalInfo != null && additionalInfo.has("lastLoginTs")) {
            ((ObjectNode) additionalInfo).remove("lastLoginTs");
        }
        return additionalInfo;
    }

    protected Device saveDeviceAndAssignEntityGroupToEdge(EntityGroup savedDeviceEntityGroup) {
        return saveDeviceAndAssignEntityGroupToEdge("default", savedDeviceEntityGroup );
    }

    protected Device saveDeviceAndAssignEntityGroupToEdge(String deviceType, EntityGroup savedDeviceEntityGroup) {
        Device device = saveDeviceOnCloud(RandomStringUtils.randomAlphanumeric(15), deviceType, savedDeviceEntityGroup.getId());
        cloudRestClient.assignEntityGroupToEdge(edge.getId(), savedDeviceEntityGroup.getId(), EntityType.DEVICE);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDeviceById(device.getId()).isPresent());

        return device;
    }

    protected List<AttributeKvEntry> sendAttributesUpdated(RestClient sourceRestClient, RestClient targetRestClient,
                                                         JsonObject attributesPayload, List<String> keys, String scope) throws Exception {

        Device device = saveDeviceAndAssignEntityGroupToEdge(createEntityGroup(EntityType.DEVICE));

        sourceRestClient.saveDeviceAttributes(device.getId(), scope, JacksonUtil.OBJECT_MAPPER.readTree(attributesPayload.toString()));

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> targetRestClient.getAttributesByScope(device.getId(), scope, keys).size() == keys.size());

        List<AttributeKvEntry> attributeKvEntries =
                targetRestClient.getAttributesByScope(device.getId(), scope, keys);

        sourceRestClient.deleteEntityAttributes(device.getId(), scope, keys);

        verifyDeviceIsActive(targetRestClient, device.getId());

        return attributeKvEntries;
    }

    protected void verifyDeviceIsActive(RestClient restClient, DeviceId deviceId) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    List<AttributeKvEntry> attributeKvEntries =
                            restClient.getAttributesByScope(deviceId, DataConstants.SERVER_SCOPE, Collections.singletonList("active"));
                    if (attributeKvEntries.size() != 1) {
                        return false;
                    }
                    AttributeKvEntry activeAttributeKv = attributeKvEntries.get(0);
                    return activeAttributeKv.getValueAsString().equals("true");
                });
    }

    protected EntityGroup createEntityGroup(EntityType entityType) {
        EntityGroup assetEntityGroup = new EntityGroup();
        assetEntityGroup.setType(entityType);
        assetEntityGroup.setName(RandomStringUtils.randomAlphanumeric(15));
        return cloudRestClient.saveEntityGroup(assetEntityGroup);
    }

    protected void verifyEntityGroups(EntityType entityType, int expectedGroupsCount) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    List<EntityGroupInfo> entityGroupsByType = edgeRestClient.getEntityGroupsByType(entityType);
                    return entityGroupsByType.size() == expectedGroupsCount;
                });
        List<EntityGroupInfo> entityGroupsByType = edgeRestClient.getEntityGroupsByType(entityType);
        for (EntityGroupInfo entityGroupInfo : entityGroupsByType) {
            List<EntityId> entityIds;
            switch (entityType) {
                case DEVICE:
                    PageData<Device> devicesByEntityGroupId = edgeRestClient.getDevicesByEntityGroupId(entityGroupInfo.getId(), new PageLink(1000));
                    entityIds = devicesByEntityGroupId.getData().stream().map(IdBased::getId).collect(Collectors.toList());
                    break;
                case ASSET:
                    PageData<Asset> assetsByEntityGroupId = edgeRestClient.getAssetsByEntityGroupId(entityGroupInfo.getId(), new PageLink(1000));
                    entityIds = assetsByEntityGroupId.getData().stream().map(IdBased::getId).collect(Collectors.toList());
                    break;
                case ENTITY_VIEW:
                    PageData<EntityView> entityViewsByEntityGroupId = edgeRestClient.getEntityViewsByEntityGroupId(entityGroupInfo.getId(), new PageLink(1000));
                    entityIds = entityViewsByEntityGroupId.getData().stream().map(IdBased::getId).collect(Collectors.toList());
                    break;
                case DASHBOARD:
                    PageData<DashboardInfo> dashboardsByEntityGroupId = edgeRestClient.getGroupDashboards(entityGroupInfo.getId(), new PageLink(1000));
                    entityIds = dashboardsByEntityGroupId.getData().stream().map(IdBased::getId).collect(Collectors.toList());
                    break;
                case USER:
                    PageData<User> usersByEntityGroupId = edgeRestClient.getUsersByEntityGroupId(entityGroupInfo.getId(), new PageLink(1000));
                    entityIds = usersByEntityGroupId.getData().stream().map(IdBased::getId).collect(Collectors.toList());
                    break;
                default:
                    throw new IllegalArgumentException("Incorrect entity type provided " + entityType);
            }
            assertEntitiesByIdsAndType(entityIds, entityType);
        }
    }

    protected boolean verifyAttributeOnEdge(EntityId entityId, String scope, String key, String expectedValue) {
        return verifyAttribute(entityId, scope, key, expectedValue, edgeRestClient);
    }

    protected boolean verifyAttributeOnCloud(EntityId entityId, String scope, String key, String expectedValue) {
        return verifyAttribute(entityId, scope, key, expectedValue, cloudRestClient);
    }

    private boolean verifyAttribute(EntityId entityId, String scope, String key, String expectedValue, RestClient restClient) {
        List<AttributeKvEntry> attributesByScope = restClient.getAttributesByScope(entityId, scope, Arrays.asList(key));
        if (attributesByScope.isEmpty()) {
            return false;
        }
        AttributeKvEntry attributeKvEntry = attributesByScope.get(0);
        return attributeKvEntry.getValueAsString().equals(expectedValue);
    }
}
