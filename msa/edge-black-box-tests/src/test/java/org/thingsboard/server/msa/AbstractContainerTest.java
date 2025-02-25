/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rest.client.RestClient;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileProvisionType;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.HasVersion;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.converter.Converter;
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
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
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
import org.thingsboard.server.common.data.ota.ChecksumAlgorithm;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermissionInfo;
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
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetsBundle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public abstract class AbstractContainerTest {

    public static final String TB_MONOLITH_SERVICE_NAME = "tb-monolith";
    public static final String TB_EDGE_SERVICE_NAME = "tb-edge";
    protected static final String CUSTOM_DEVICE_PROFILE_NAME = "Custom Device Profile";

    public static final List<TestEdgeConfiguration> edgeConfigurations =
            Arrays.asList(
                    new TestEdgeConfiguration("280629c7-f853-ee3d-01c0-fffbb6f2ef38", "g9ta4soeylw6smqkky8g", 8082, ""),
                    new TestEdgeConfiguration("e29dadb1-c487-3b9e-1b5a-02193191c90e", "dmb17p71vz9svfl7tgnz", 8083, "kafka"),
                    new TestEdgeConfiguration("2cc28012-a2f3-8bff-7b1a-5e686c972e1e", "z2d2z90fqjylht011ram", 8084, "38"),
                    new TestEdgeConfiguration("774e5e4e-8ec7-9945-1c6a-4d6ba08cb5fc", "om3zzzadzlugth03nibn", 8085, "37")
            );


    protected static List<TestEdgeRuntimeParameters> testParameters = new ArrayList<>();

    protected static RestClient cloudRestClient = null;
    protected static String tbUrl;

    protected static Edge edge;
    protected static String edgeUrl;
    protected static RestClient edgeRestClient;

    protected void performTestOnEachEdge(Runnable runnable) {
        for (TestEdgeRuntimeParameters edgeTestParameter : testParameters) {
            edge = edgeTestParameter.getEdge();
            edgeUrl = edgeTestParameter.getUrl();
            edgeRestClient = edgeTestParameter.getRestClient();

            long startTime = System.currentTimeMillis();
            log.info("=================================================");
            log.info("STARTING TEST: {} for edge {} {}", Thread.currentThread().getStackTrace()[2].getMethodName(), edge.getName(), edge.getRoutingKey());
            log.info("=================================================");

            runnable.run();

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("=================================================");
            log.info("SUCCEEDED TEST: {} for edge {} {} in {} ms", Thread.currentThread().getStackTrace()[2].getMethodName(), edge.getName(), edge.getRoutingKey(), elapsedTime);
            log.info("=================================================");
        }
    }

    @BeforeClass
    public static void before() throws Exception {
        if (cloudRestClient == null) {
            String tbHost = ContainerTestSuite.testContainer.getServiceHost(TB_MONOLITH_SERVICE_NAME, 8080);
            Integer tbPort = ContainerTestSuite.testContainer.getServicePort(TB_MONOLITH_SERVICE_NAME, 8080);
            tbUrl = "http://" + tbHost + ":" + tbPort;
            cloudRestClient = new RestClient(tbUrl);
            cloudRestClient.login("tenant@thingsboard.org", "tenant");

            RuleChainId ruleChainId = updateRootRuleChain();
            RuleChainId edgeRuleChainId = updateEdgeRootRuleChain();

            for (TestEdgeConfiguration config : edgeConfigurations) {
                String edgeHost = ContainerTestSuite.testContainer.getServiceHost(TB_EDGE_SERVICE_NAME + config.getTagWithDash(), config.getPort());
                Integer edgePort = ContainerTestSuite.testContainer.getServicePort(TB_EDGE_SERVICE_NAME + config.getTagWithDash(), config.getPort());
                String edgeUrl = "http://" + edgeHost + ":" + edgePort;
                Edge edge = createEdge("edge" + config.getTagWithDash(), config.getRoutingKey(), config.getSecret());
                testParameters.add(new TestEdgeRuntimeParameters(new RestClient(edgeUrl), edge, edgeUrl));
            }

            createCustomDeviceProfile(CUSTOM_DEVICE_PROFILE_NAME, ruleChainId, edgeRuleChainId);

            for (TestEdgeRuntimeParameters testParameter : testParameters) {
                edgeRestClient = testParameter.getRestClient();
                edge = testParameter.getEdge();
                edgeUrl = testParameter.getUrl();

                log.info("=================================================");
                log.info("STARTING INIT for edge {}", edge.getName());
                log.info("=================================================");

                loginIntoEdgeWithRetries("tenant@thingsboard.org", "tenant");

                Optional<Tenant> tenant = edgeRestClient.getTenantById(edge.getTenantId());
                Assert.assertTrue(tenant.isPresent());
                Assert.assertEquals(edge.getTenantId(), tenant.get().getId());

                // This is a starting point to start other tests
                verifyWidgetBundles();

                log.info("=================================================");
                log.info("SUCCEEDED INIT for edge {}", edge.getName());
                log.info("=================================================");
            }
        }
    }

    protected static void loginIntoEdgeWithRetries(String userName, String password) {
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(300, TimeUnit.SECONDS)
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
                .atMost(60, TimeUnit.SECONDS).
                until(() -> {
                    try {
                        long totalElements = edgeRestClient.getWidgetsBundles(new PageLink(100)).getTotalElements();
                        final long expectedCount = 32;
                        if (totalElements != expectedCount) {
                            log.warn("Expected {} widget bundles, but got {}", expectedCount, totalElements);
                        }
                        return totalElements == expectedCount;
                    } catch (Throwable e) {
                        log.error("Failed to verify widget bundles", e);
                        return false;
                    }
                });

        PageData<WidgetsBundle> pageData = edgeRestClient.getWidgetsBundles(new PageLink(100));

        for (WidgetsBundle widgetsBundle : pageData.getData()) {
            Awaitility.await()
                    .pollInterval(1000, TimeUnit.MILLISECONDS)
                    .atMost(90, TimeUnit.SECONDS).
                    until(() -> {
                        try {
                            List<WidgetType> edgeBundleWidgetTypes = edgeRestClient.getBundleWidgetTypes(widgetsBundle.getId());
                            List<WidgetType> cloudBundleWidgetTypes = cloudRestClient.getBundleWidgetTypes(widgetsBundle.getId());
                            if (cloudBundleWidgetTypes == null || edgeBundleWidgetTypes == null) {
                                return false;
                            }
                            if (edgeBundleWidgetTypes.size() != cloudBundleWidgetTypes.size()) {
                                // Collect the names of the widget types for edge and cloud
                                String cloudWidgetTypeNames = cloudBundleWidgetTypes.stream()
                                        .map(WidgetType::getName)
                                        .collect(Collectors.joining(", "));
                                String edgeWidgetTypeNames = edgeBundleWidgetTypes.stream()
                                        .map(WidgetType::getName)
                                        .collect(Collectors.joining(", "));
                                log.warn("Expected {} widget types, but got {}. "
                                                + "widgetBundleId = {}, widgetsBundleName = {}"
                                                + "cloudBundleWidgetTypesNames = [{}], edgeBundleWidgetTypesNames = [{}]",
                                        cloudBundleWidgetTypes.size(), edgeBundleWidgetTypes.size(),
                                        widgetsBundle.getId(), widgetsBundle.getName(),
                                        cloudWidgetTypeNames, edgeWidgetTypeNames);
                            }
                            return edgeBundleWidgetTypes.size() == cloudBundleWidgetTypes.size();
                        } catch (Throwable e) {
                            return false;
                        }
                    });
            List<WidgetType> edgeBundleWidgetTypes = edgeRestClient.getBundleWidgetTypes(widgetsBundle.getId());
            List<WidgetType> cloudBundleWidgetTypes = cloudRestClient.getBundleWidgetTypes(widgetsBundle.getId());
            Assert.assertNotNull("edgeBundleWidgetTypes can't be null", edgeBundleWidgetTypes);
            Assert.assertNotNull("cloudBundleWidgetTypes can't be null", cloudBundleWidgetTypes);
        }
    }

    private static RuleChainId updateRootRuleChain() throws IOException {
        // Modifications:
        // - add rule node 'script' to create RPC reply message
        // - add rule node 'rpc call reply' to send RPC reply
        // - add connection - from 'RPC from Device' to 'script'
        // - add connection - from 'script' to 'rpc call reply'
        return updateRootRuleChain(RuleChainType.CORE, "Updated_RootRuleChainMetadata.json");
    }

    private static RuleChainId updateEdgeRootRuleChain() throws IOException {
        // Modifications:
        // - add connection - from 'RPC from Device' to 'Push to cloud'
        return updateRootRuleChain(RuleChainType.EDGE, "Updated_EdgeRootRuleChainMetadata.json");
    }

    private static RuleChainId updateRootRuleChain(RuleChainType ruleChainType, String updatedRootRuleChainFileName) throws IOException {
        PageData<RuleChain> ruleChains = cloudRestClient.getRuleChains(ruleChainType, new PageLink(100));
        RuleChainId rootRuleChainId = null;
        for (RuleChain datum : ruleChains.getData()) {
            if (datum.isRoot()) {
                rootRuleChainId = datum.getId();
                break;
            }
        }
        Assert.assertNotNull(rootRuleChainId);
        JsonNode configuration = JacksonUtil.OBJECT_MAPPER.readTree(AbstractContainerTest.class.getClassLoader().getResourceAsStream(updatedRootRuleChainFileName));
        RuleChainMetaData ruleChainMetaData = new RuleChainMetaData();
        ruleChainMetaData.setRuleChainId(rootRuleChainId);
        ruleChainMetaData.setFirstNodeIndex(configuration.get("firstNodeIndex").asInt());
        ruleChainMetaData.setNodes(Arrays.asList(JacksonUtil.treeToValue(configuration.get("nodes"), RuleNode[].class)));
        ruleChainMetaData.setConnections(Arrays.asList(JacksonUtil.treeToValue(configuration.get("connections"), NodeConnectionInfo[].class)));
        cloudRestClient.saveRuleChainMetaData(ruleChainMetaData);
        return rootRuleChainId;
    }

    protected static DeviceProfile createCustomDeviceProfile(String deviceProfileName,
                                                             DeviceProfileTransportConfiguration deviceProfileTransportConfiguration) {
        return doCreateDeviceProfile(deviceProfileName, null, null, deviceProfileTransportConfiguration, cloudRestClient);
    }

    protected static DeviceProfile createCustomDeviceProfile(String deviceProfileName) {
        return createCustomDeviceProfile(deviceProfileName, null);
    }

    protected static DeviceProfile createCustomDeviceProfile(String deviceProfileName, RuleChainId defaultRuleChain, RuleChainId defaultEdgeRuleChainId) {
        return doCreateDeviceProfile(deviceProfileName, defaultRuleChain, defaultEdgeRuleChainId, null, cloudRestClient);
    }

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            log.info("=================================================");
            log.info("STARTING TEST: {}", description.getMethodName());
            log.info("=================================================");
        }

        /**
         * Invoked when a test succeeds
         */
        protected void succeeded(Description description) {
            log.info("=================================================");
            log.info("SUCCEEDED TEST: {}", description.getMethodName());
            log.info("=================================================");
        }

        /**
         * Invoked when a test fails
         */
        protected void failed(Throwable e, Description description) {
            log.info("=================================================");
            log.info("FAILED TEST: {}", description.getMethodName(), e);
            log.info("=================================================");
        }
    };

    protected static DeviceProfile createDeviceProfileOnEdge(String name) {
        return doCreateDeviceProfile(name, null, null, new DefaultDeviceProfileTransportConfiguration(), edgeRestClient);
    }

    private static DeviceProfile doCreateDeviceProfile(String name, RuleChainId defaultRuleChain, RuleChainId defaultEdgeRuleChainId,
                                                       DeviceProfileTransportConfiguration deviceProfileTransportConfiguration, RestClient restClient) {
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
        deviceProfileData.setTransportConfiguration(Objects.requireNonNullElseGet(deviceProfileTransportConfiguration,
                DefaultDeviceProfileTransportConfiguration::new));
        deviceProfile.setProfileData(deviceProfileData);
        deviceProfile.setDefault(false);
        deviceProfile.setDefaultRuleChainId(defaultRuleChain);
        deviceProfile.setDefaultQueueName("Main");
        deviceProfile.setDefaultEdgeRuleChainId(defaultEdgeRuleChainId);
        extendDeviceProfileData(deviceProfile);
        return restClient.saveDeviceProfile(deviceProfile);
    }

    protected static void extendDeviceProfileData(DeviceProfile deviceProfile) {
        DeviceProfileData profileData = deviceProfile.getProfileData();
        List<DeviceProfileAlarm> alarms = new ArrayList<>();
        DeviceProfileAlarm deviceProfileAlarm = new DeviceProfileAlarm();
        deviceProfileAlarm.setAlarmType("High Temperature");
        deviceProfileAlarm.setId("High Temperature");
        AlarmRule alarmRule = new AlarmRule();
        alarmRule.setAlarmDetails("Alarm Details");
        AlarmCondition alarmCondition = new AlarmCondition();
        alarmCondition.setSpec(new SimpleAlarmConditionSpec());
        List<AlarmConditionFilter> condition = new ArrayList<>();
        AlarmConditionFilter alarmConditionFilter = new AlarmConditionFilter();
        alarmConditionFilter.setKey(new AlarmConditionFilterKey(AlarmConditionKeyType.TIME_SERIES, "temperature"));
        NumericFilterPredicate predicate = new NumericFilterPredicate();
        predicate.setOperation(NumericFilterPredicate.NumericOperation.GREATER_OR_EQUAL);
        predicate.setValue(new FilterPredicateValue<>(55.0));
        alarmConditionFilter.setPredicate(predicate);
        alarmConditionFilter.setValueType(EntityKeyValueType.NUMERIC);
        condition.add(alarmConditionFilter);
        alarmCondition.setCondition(condition);
        alarmRule.setCondition(alarmCondition);
        TreeMap<AlarmSeverity, AlarmRule> createRules = new TreeMap<>();
        createRules.put(AlarmSeverity.CRITICAL, alarmRule);
        deviceProfileAlarm.setCreateRules(createRules);
        alarms.add(deviceProfileAlarm);
        profileData.setAlarms(alarms);
        profileData.setProvisionConfiguration(new AllowCreateNewDevicesDeviceProfileProvisionConfiguration("123"));
    }

    protected static Edge createEdge(String name, String routingKey, String secret) {
        Edge edge = new Edge();
        edge.setName(name);
        edge.setType("DEFAULT");
        edge.setRoutingKey(routingKey);
        edge.setSecret(secret);
        edge.setEdgeLicenseKey("6qcGys6gz4M2ZuIqZ6hRDjWT");
        edge.setCloudEndpoint("http://tb-monolith:8080");
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
        Asset asset = saveAssetOnCloud(StringUtils.randomAlphanumeric(15), assetType, savedAssetEntityGroup.getId());

        assignEntityGroupToEdge(savedAssetEntityGroup);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getAssetById(asset.getId()).isPresent());
        return asset;
    }

    protected void assignEntityGroupToEdge(EntityGroup entityGroup) {
        cloudRestClient.assignEntityGroupToEdge(edge.getId(), entityGroup.getId(), entityGroup.getType());

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getEntityGroupById(entityGroup.getId()).isPresent());

        try {
            // wait until GROUP_ENTITIES_REQUEST message from edge processed
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception ignored) {}
    }

    protected Asset saveAssetOnCloud(String assetName, String type, EntityGroupId entityGroupId) {
        Asset asset = new Asset();
        asset.setName(assetName);
        asset.setType(type);
        return cloudRestClient.saveAsset(asset, entityGroupId);
    }

    protected Dashboard saveDashboardOnCloud(String dashboardTitle, EntityGroupId entityGroupId) {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle(dashboardTitle);
        return cloudRestClient.saveDashboard(dashboard, entityGroupId);
    }

    protected Asset saveAssetOnEdge(String assetName, String type, EntityGroupId entityGroupId) {
        Asset asset = new Asset();
        asset.setName(assetName);
        asset.setType(type);
        return edgeRestClient.saveAsset(asset, entityGroupId);
    }

    protected Dashboard saveDashboardOnEdge(String dashboardTitle, EntityGroupId entityGroupId) {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle(dashboardTitle);
        return edgeRestClient.saveDashboard(dashboard, entityGroupId);
    }

    protected void assertEntitiesByIdsAndType(List<EntityId> entityIds, EntityType entityType) {
        switch (entityType) {
            case DEVICE_PROFILE -> assertDeviceProfiles(entityIds);
            case ASSET_PROFILE -> assertAssetProfiles(entityIds);
            case RULE_CHAIN -> assertRuleChains(entityIds);
            case WIDGETS_BUNDLE -> assertWidgetsBundles(entityIds);
            case WIDGET_TYPE -> assertWidgetTypes(entityIds);
            case DEVICE -> assertDevices(entityIds);
            case ASSET -> assertAssets(entityIds);
            case ENTITY_VIEW -> assertEntityViews(entityIds);
            case DASHBOARD -> assertDashboards(entityIds);
            case USER -> assertUsers(entityIds);
            case OTA_PACKAGE -> assertOtaPackages(entityIds);
            case QUEUE -> assertQueues(entityIds);
            case ROLE -> assertRoles(entityIds);
            case CONVERTER -> assertConverters(entityIds);
            case INTEGRATION -> assertIntegrations(entityIds);
        }
    }

    private void assertDeviceProfiles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DeviceProfileId deviceProfileId = new DeviceProfileId(entityId.getId());
            Optional<DeviceProfile> edgeDeviceProfile = edgeRestClient.getDeviceProfileById(deviceProfileId);
            Optional<DeviceProfile> cloudDeviceProfile = cloudRestClient.getDeviceProfileById(deviceProfileId);
            DeviceProfile expected = edgeDeviceProfile.get();
            DeviceProfile actual = cloudDeviceProfile.get();
            Assert.assertEquals(expected.getDefaultRuleChainId(), actual.getDefaultEdgeRuleChainId());
            expected.setDefaultRuleChainId(null);
            actual.setDefaultEdgeRuleChainId(null);
            actual.setDefaultRuleChainId(null);
            cleanUpVersion(expected, actual);
            Assert.assertEquals("Device profiles on cloud and edge are different", expected, actual);
        }
    }

    private void assertAssetProfiles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            AssetProfileId assetProfileId = new AssetProfileId(entityId.getId());
            Optional<AssetProfile> edgeAssetProfile = edgeRestClient.getAssetProfileById(assetProfileId);
            Optional<AssetProfile> cloudAssetProfile = cloudRestClient.getAssetProfileById(assetProfileId);
            AssetProfile expected = edgeAssetProfile.get();
            AssetProfile actual = cloudAssetProfile.get();
            Assert.assertEquals(expected.getDefaultRuleChainId(), actual.getDefaultEdgeRuleChainId());
            expected.setDefaultRuleChainId(null);
            actual.setDefaultEdgeRuleChainId(null);
            cleanUpVersion(expected, actual);
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
            expected.setRoot(false);
            actual.setRoot(false);
            cleanUpVersion(expected, actual);
            Assert.assertEquals("Rule chains on cloud and edge are different (except type)", expected, actual);

            Awaitility.await()
                    .pollInterval(500, TimeUnit.MILLISECONDS)
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
            expected.setImage(null);
            actual.setImage(null);
            cleanUpVersion(expected, actual);
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
            expected.setImage(null);
            actual.setImage(null);
            cleanUpVersion(expected, actual);
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
            cleanUpVersion(expected, actual);
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
            cleanUpVersion(expected, actual);
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
            cleanUpVersion(expected, actual);
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
            cleanUpVersion(expected, actual);
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
            cleanUpVersion(expected, actual);
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
            cleanUpVersion(expected, actual);
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
            Assert.assertFalse("Edge converter isEdgeTemplate incorrect", expected.isEdgeTemplate());
            Assert.assertTrue("Cloud converter isEdgeTemplate incorrect", actual.isEdgeTemplate());
            actual.setEdgeTemplate(false);
            cleanUpVersion(expected, actual);
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
            Assert.assertFalse("Edge integration isEdgeTemplate incorrect", expected.isEdgeTemplate());
            Assert.assertTrue("Cloud integration isEdgeTemplate incorrect", actual.isEdgeTemplate());
            actual.setEdgeTemplate(false);

            // configuration must be reset, because configuration on cloud contains placeholders
            actual.setConfiguration(null);
            expected.setConfiguration(null);
            cleanUpVersion(expected, actual);

            Assert.assertEquals("Integrations on cloud and edge are different", expected, actual);
        }
    }

    protected void cleanUpVersion(HasVersion expected, HasVersion actual) {
        expected.setVersion(null);
        actual.setVersion(null);
    }

    private JsonNode cleanLastLoginTsFromAdditionalInfo(JsonNode additionalInfo) {
        if (additionalInfo != null && additionalInfo.has("lastLoginTs")) {
            ((ObjectNode) additionalInfo).remove("lastLoginTs");
        }
        return additionalInfo;
    }

    protected Device saveDeviceAndAssignEntityGroupToEdge(EntityGroup savedDeviceEntityGroup) {
        return saveDeviceAndAssignEntityGroupToEdge("default", savedDeviceEntityGroup);
    }

    protected Device saveDeviceAndAssignEntityGroupToEdge(String deviceType, EntityGroup savedDeviceEntityGroup) {
        Device device = saveDeviceOnCloud(StringUtils.randomAlphanumeric(15), deviceType, savedDeviceEntityGroup.getId());
        cloudRestClient.assignEntityGroupToEdge(edge.getId(), savedDeviceEntityGroup.getId(), EntityType.DEVICE);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDeviceById(device.getId()).isPresent());

        return device;
    }

    protected List<AttributeKvEntry> sendAttributesUpdated(RestClient sourceRestClient, RestClient targetRestClient,
                                                           JsonObject attributesPayload, List<String> keys, String scope) {

        Device device = saveDeviceAndAssignEntityGroupToEdge(createEntityGroup(EntityType.DEVICE));

        sourceRestClient.saveDeviceAttributes(device.getId(), scope, JacksonUtil.toJsonNode(attributesPayload.toString()));

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> targetRestClient.getAttributesByScope(device.getId(), scope, keys).size() == keys.size());

        List<AttributeKvEntry> attributeKvEntries =
                targetRestClient.getAttributesByScope(device.getId(), scope, keys);

        sourceRestClient.deleteEntityAttributes(device.getId(), scope, keys);

        verifyDeviceIsActive(targetRestClient, device.getId());

        // cleanup
        cloudRestClient.deleteDevice(device.getId());

        return attributeKvEntries;
    }

    protected void verifyDeviceIsActive(RestClient restClient, DeviceId deviceId) {
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
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
        return createEntityGroup(entityType, null);
    }

    protected EntityGroup createEntityGroup(EntityType entityType, EntityId ownerId) {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setType(entityType);
        entityGroup.setOwnerId(ownerId);
        entityGroup.setName(StringUtils.randomAlphanumeric(15));
        return cloudRestClient.saveEntityGroup(entityGroup);
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

    protected Customer saveCustomer(String title, CustomerId parentCustomerId) {
        Customer customer = new Customer();
        customer.setTitle(title);
        customer.setParentCustomerId(parentCustomerId);
        return cloudRestClient.saveCustomer(customer);
    }

    protected Optional<EntityGroupInfo> findTenantAdminsGroup() {
        return cloudRestClient.getEntityGroupInfoByOwnerAndNameAndType(edge.getTenantId(), EntityType.USER, EntityGroup.GROUP_TENANT_ADMINS_NAME);
    }

    protected Optional<EntityGroupInfo> findCustomerAdminsGroup(Customer customer) {
        return cloudRestClient.getEntityGroupInfoByOwnerAndNameAndType(customer.getId(), EntityType.USER, EntityGroup.GROUP_CUSTOMER_ADMINS_NAME);
    }

    protected void verifyThatCustomerAdminGroupIsCreatedOnEdge(Customer savedCustomer) {
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    Optional<EntityGroupInfo> customerAdminGroupOpt =
                            edgeRestClient.getEntityGroupInfoByOwnerAndNameAndType(
                                    savedCustomer.getId(), EntityType.USER, EntityGroup.GROUP_CUSTOMER_ADMINS_NAME);
                    if (customerAdminGroupOpt.isEmpty()) {
                        return false;
                    }
                    List<GroupPermissionInfo> entityGroupPermissions = edgeRestClient.getUserGroupPermissions(customerAdminGroupOpt.get().getId());
                    return entityGroupPermissions.stream().anyMatch(groupPermissionInfo ->
                            Role.ROLE_CUSTOMER_ADMIN_NAME.equals(groupPermissionInfo.getRole().getName()));
                });
    }

    protected RuleChainId createRuleChainAndAssignToEdge(String ruleChainName) {
        RuleChain ruleChain = new RuleChain();
        ruleChain.setName(ruleChainName);
        ruleChain.setType(RuleChainType.EDGE);
        RuleChain savedRuleChain = cloudRestClient.saveRuleChain(ruleChain);
        createRuleChainMetadata(savedRuleChain);

        cloudRestClient.assignRuleChainToEdge(edge.getId(), savedRuleChain.getId());

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getRuleChainById(savedRuleChain.getId()).isPresent());

        return savedRuleChain.getId();
    }

    private void createRuleChainMetadata(RuleChain ruleChain) {
        RuleChainMetaData ruleChainMetaData = new RuleChainMetaData();
        ruleChainMetaData.setRuleChainId(ruleChain.getId());

        RuleNode ruleNode1 = createRuleNode("name1");
        RuleNode ruleNode2 = createRuleNode("name2");
        RuleNode ruleNode3 = createRuleNode("name3");

        List<RuleNode> ruleNodes = new ArrayList<>();
        ruleNodes.add(ruleNode1);
        ruleNodes.add(ruleNode2);
        ruleNodes.add(ruleNode3);
        ruleChainMetaData.setFirstNodeIndex(0);
        ruleChainMetaData.setNodes(ruleNodes);

        ruleChainMetaData.addConnectionInfo(0, 1, "success");
        ruleChainMetaData.addConnectionInfo(0, 2, "fail");
        ruleChainMetaData.addConnectionInfo(1, 2, "success");

//         ruleChainMetaData.addRuleChainConnectionInfo(2, edge.getRootRuleChainId(), "success", JacksonUtil.newObjectNode());

        cloudRestClient.saveRuleChainMetaData(ruleChainMetaData);
    }

    private RuleNode createRuleNode(String name) {
        RuleNode ruleNode = new RuleNode();
        ruleNode.setName(name);
        ruleNode.setType("org.thingsboard.rule.engine.filter.TbMsgTypeSwitchNode");
        ruleNode.setDebugMode(true);
        ObjectNode configuration = JacksonUtil.newObjectNode();
        configuration.put("version", 0);
        ruleNode.setConfiguration(JacksonUtil.valueToTree(configuration));
        return ruleNode;
    }

    protected void unAssignFromEdgeAndDeleteRuleChain(RuleChainId ruleChainId) {
        // unassign rule chain from edge
        cloudRestClient.unassignRuleChainFromEdge(edge.getId(), ruleChainId);
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getRuleChainById(ruleChainId).isEmpty());

        // delete rule chain
        cloudRestClient.deleteRuleChain(ruleChainId);
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> cloudRestClient.getRuleChainById(ruleChainId).isEmpty());
    }

    protected DashboardId createDashboardAndAssignToEdge(String dashboardName, EntityGroup dashboardGroup) {
        Dashboard savedDashboard = saveDashboardOnCloud(dashboardName, dashboardGroup.getId());

        assignEntityGroupToEdge(dashboardGroup);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getDashboardById(savedDashboard.getId()).isPresent());
        return savedDashboard.getId();
    }

    protected void unAssignFromEdgeAndDeleteDashboard(DashboardId dashboardId, EntityGroupId dashboardGroupId) {
        cloudRestClient.unassignEntityGroupFromEdge(edge.getId(), dashboardGroupId, EntityType.DASHBOARD);
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> edgeRestClient.getEntityGroupById(dashboardGroupId).isEmpty());

        cloudRestClient.deleteDashboard(dashboardId);
        cloudRestClient.deleteEntityGroup(dashboardGroupId);
        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> cloudRestClient.getEntityGroupById(dashboardGroupId).isEmpty());
    }

    protected OtaPackageId createOtaPackageInfo(DeviceProfileId deviceProfileId, OtaPackageType otaPackageType) {
        OtaPackageInfo otaPackageInfo = new OtaPackageInfo();
        otaPackageInfo.setDeviceProfileId(deviceProfileId);
        otaPackageInfo.setType(otaPackageType);
        otaPackageInfo.setTitle("My " + otaPackageType + " #2");
        otaPackageInfo.setVersion("v2.0");
        otaPackageInfo.setTag("My " + otaPackageType + " #2 v2.0");
        otaPackageInfo.setHasData(false);
        OtaPackageInfo savedOtaPackageInfo = cloudRestClient.saveOtaPackageInfo(otaPackageInfo, false);

        cloudRestClient.saveOtaPackageData(savedOtaPackageInfo.getId(),
                null, ChecksumAlgorithm.SHA256, "firmware.bin", new byte[]{1, 3, 5});

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    PageData<OtaPackageInfo> otaPackages = edgeRestClient.getOtaPackages(new PageLink(100));
                    if (otaPackages.getData().isEmpty()) {
                        return false;
                    }
                    return otaPackages.getData().stream().map(OtaPackageInfo::getId).anyMatch(savedOtaPackageInfo.getId()::equals);
                });

        return savedOtaPackageInfo.getId();
    }

    protected Customer findPublicCustomer(EntityId ownerId) {
        Optional<EntityGroupInfo> customerAllEntityGroupOpt = cloudRestClient.getEntityGroupAllByOwnerAndType(ownerId, EntityType.CUSTOMER);
        Assert.assertTrue(customerAllEntityGroupOpt.isPresent());
        EntityGroupInfo customerAllEntityGroup = customerAllEntityGroupOpt.get();
        List<ShortEntityView> allCustomerViews = cloudRestClient.getEntities(customerAllEntityGroup.getId(), new PageLink(100)).getData();
        for (ShortEntityView customerView : allCustomerViews) {
            Optional<Customer> customerById = cloudRestClient.getCustomerById(new CustomerId(customerView.getId().getId()));
            if (customerById.isPresent() && customerById.get().isPublic()) {
                return customerById.get();
            }
        }
        Assert.fail("Public customer not found!");
        return null;
    }

}
