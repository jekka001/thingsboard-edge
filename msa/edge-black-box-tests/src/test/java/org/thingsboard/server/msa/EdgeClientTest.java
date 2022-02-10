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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.testcontainers.shaded.org.apache.commons.lang.RandomStringUtils;
import org.thingsboard.rest.client.RestClient;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmSearchStatus;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.IdBased;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.id.WidgetsBundleId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class EdgeClientTest extends AbstractContainerTest {

    @Test
    public void testUsers() {
        verifyEntityGroups(EntityType.USER, 3);
    }

    @Test
    public void testRoles() {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  edgeRestClient.getRoles(RoleType.GENERIC, new PageLink(100)).getTotalElements() == 2);

        PageData<Role> genericPageData = edgeRestClient.getRoles(RoleType.GENERIC, new PageLink(100));

        List<EntityId> genericIds = genericPageData.getData().stream().map(IdBased::getId).collect(Collectors.toList());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  edgeRestClient.getRoles(RoleType.GROUP, new PageLink(100)).getTotalElements() == 1);
        PageData<Role> groupPageData = edgeRestClient.getRoles(RoleType.GROUP, new PageLink(100));
        List<EntityId> groupIds = groupPageData.getData().stream().map(IdBased::getId).collect(Collectors.toList());
        genericIds.addAll(groupIds);
        assertEntitiesByIdsAndType(genericIds, EntityType.ROLE);
    }

    @Test
    public void testDeviceProfiles() {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  edgeRestClient.getDeviceProfiles(new PageLink(100)).getTotalElements() == 3);

        PageData<DeviceProfile> pageData = edgeRestClient.getDeviceProfiles(new PageLink(100));
        assertEntitiesByIdsAndType(pageData.getData().stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.DEVICE_PROFILE);
    }

    private void verifyEntityGroups(EntityType entityType, int expectedGroupsCount) {
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

    @Test
    public void testWhiteLabeling() {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<WhiteLabelingParams> edgeWhiteLabelParams = edgeRestClient.getCurrentWhiteLabelParams();
                    Optional<WhiteLabelingParams> cloudWhiteLabelParams = restClient.getCurrentWhiteLabelParams();
                    return edgeWhiteLabelParams.isPresent() &&
                            cloudWhiteLabelParams.isPresent() &&
                            edgeWhiteLabelParams.get().equals(cloudWhiteLabelParams.get());
                });

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<LoginWhiteLabelingParams> edgeLoginWhiteLabelParams = edgeRestClient.getCurrentLoginWhiteLabelParams();
                    Optional<LoginWhiteLabelingParams> cloudLoginWhiteLabelParams = restClient.getCurrentLoginWhiteLabelParams();
                    return edgeLoginWhiteLabelParams.isPresent() &&
                            cloudLoginWhiteLabelParams.isPresent() &&
                            edgeLoginWhiteLabelParams.get().equals(cloudLoginWhiteLabelParams.get());
                });

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<CustomTranslation> edgeCustomTranslation = edgeRestClient.getCustomTranslation();
                    Optional<CustomTranslation> cloudCustomTranslation = restClient.getCustomTranslation();
                    return edgeCustomTranslation.isPresent() &&
                            cloudCustomTranslation.isPresent() &&
                            edgeCustomTranslation.get().equals(cloudCustomTranslation.get());
                });
    }

    @Test
    public void testTenantAdminSettings() {
        verifyTenantAdminSettingsByKey("general");
        verifyTenantAdminSettingsByKey("mailTemplates");
        verifyTenantAdminSettingsByKey("mail");

        // TODO: @voba - verify admin setting in next release. In the current there is no sysadmin on edge to fetch it
        // login as sysadmin on edge
        // login as sysadmin on cloud
        // verifyAdminSettingsByKey("general");
        // verifyAdminSettingsByKey("mailTemplates");
        // verifyAdminSettingsByKey("mail");
    }

    private void verifyTenantAdminSettingsByKey(String key) {
        Optional<AdminSettings> edgeAdminSettings = edgeRestClient.getAdminSettings(key);
        Assert.assertTrue("Admin settings is not available on edge, key = " + key, edgeAdminSettings.isPresent());
        Optional<AdminSettings> cloudAdminSettings = restClient.getAdminSettings(key);
        Assert.assertTrue("Admin settings is not available on cloud, key = " + key, cloudAdminSettings.isPresent());
        Assert.assertEquals("Admin settings on cloud and edge are different", edgeAdminSettings.get(), cloudAdminSettings.get());
    }

    @Test
    public void testWidgetsBundles() {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  edgeRestClient.getWidgetsBundles(new PageLink(100)).getTotalElements() == 16);
        PageData<WidgetsBundle> pageData = edgeRestClient.getWidgetsBundles(new PageLink(100));
        assertEntitiesByIdsAndType(pageData.getData().stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.WIDGETS_BUNDLE);

        for (String widgetsBundlesAlias : pageData.getData().stream().map(WidgetsBundle::getAlias).collect(Collectors.toList())) {
            Awaitility.await()
                    .atMost(30, TimeUnit.SECONDS).
                    until(() -> {
                        List<WidgetType> edgeBundleWidgetTypes = edgeRestClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
                        List<WidgetType> cloudBundleWidgetTypes = restClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
                        return cloudBundleWidgetTypes != null && edgeBundleWidgetTypes != null
                                && edgeBundleWidgetTypes.size() == cloudBundleWidgetTypes.size();
                    });
            List<WidgetType> edgeBundleWidgetTypes = edgeRestClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
            List<WidgetType> cloudBundleWidgetTypes = restClient.getBundleWidgetTypes(true, widgetsBundlesAlias);
            Assert.assertNotNull("edgeBundleWidgetTypes can't be null", edgeBundleWidgetTypes);
            Assert.assertNotNull("cloudBundleWidgetTypes can't be null", cloudBundleWidgetTypes);
            assertEntitiesByIdsAndType(edgeBundleWidgetTypes.stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.WIDGET_TYPE);
        }
    }

    private void assertEntitiesByIdsAndType(List<EntityId> entityIds, EntityType entityType) {
        switch (entityType) {
            case DEVICE_PROFILE:
                assertDeviceProfiles(entityIds);
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
            case ROLE:
                assertRoles(entityIds);
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
        }
    }

    private void assertDeviceProfiles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DeviceProfileId deviceProfileId = new DeviceProfileId(entityId.getId());
            Optional<DeviceProfile> edgeDeviceProfile = edgeRestClient.getDeviceProfileById(deviceProfileId);
            Optional<DeviceProfile> cloudDeviceProfile = restClient.getDeviceProfileById(deviceProfileId);
            DeviceProfile expected = edgeDeviceProfile.get();
            DeviceProfile actual = cloudDeviceProfile.get();
            actual.setDefaultRuleChainId(null);
            Assert.assertEquals("Device profiles on cloud and edge are different (except defaultRuleChainId)", expected, actual);
        }
    }

    private void assertRuleChains(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            RuleChainId ruleChainId = new RuleChainId(entityId.getId());
            Optional<RuleChain> edgeRuleChain = edgeRestClient.getRuleChainById(ruleChainId);
            Optional<RuleChain> cloudRuleChain = restClient.getRuleChainById(ruleChainId);
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
                        Optional<RuleChainMetaData> cloudRuleChainMetaData = restClient.getRuleChainMetaData(ruleChainId);
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
            // TODO: @voba - fix send of created time from cloud to edge
            actualNode.setCreatedTime(0);
            if (!expectedNode.equals(actualNode)) {
                return false;
            }
        }
        return true;
    }

    private void assertRoles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            RoleId roleId = new RoleId(entityId.getId());
            Optional<Role> edgeRole = edgeRestClient.getRoleById(roleId);
            Optional<Role> cloudRole = restClient.getRoleById(roleId);
            Role expected = edgeRole.get();
            Role actual = cloudRole.get();
            // permissions field is transient and not used in comparison
            Assert.assertEquals("Roles on cloud and edge are different", expected, actual);
        }
    }

    private void assertWidgetsBundles(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            WidgetsBundleId widgetsBundleId = new WidgetsBundleId(entityId.getId());
            Optional<WidgetsBundle> edgeWidgetsBundle = edgeRestClient.getWidgetsBundleById(widgetsBundleId);
            Optional<WidgetsBundle> cloudWidgetsBundle = restClient.getWidgetsBundleById(widgetsBundleId);
            WidgetsBundle expected = edgeWidgetsBundle.get();
            WidgetsBundle actual = cloudWidgetsBundle.get();
            Assert.assertEquals("Widgets bundles on cloud and edge are different", expected, actual);
        }
    }

    private void assertWidgetTypes(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            WidgetTypeId widgetTypeId = new WidgetTypeId(entityId.getId());
            Optional<WidgetTypeDetails> edgeWidgetsBundle = edgeRestClient.getWidgetTypeById(widgetTypeId);
            Optional<WidgetTypeDetails> cloudWidgetsBundle = restClient.getWidgetTypeById(widgetTypeId);
            WidgetTypeDetails expected = edgeWidgetsBundle.get();
            WidgetTypeDetails actual = cloudWidgetsBundle.get();
            Assert.assertEquals("Widget types on cloud and edge are different", expected, actual);
        }
    }

    private void assertDevices(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DeviceId deviceId = new DeviceId(entityId.getId());
            Optional<Device> edgeDevice = edgeRestClient.getDeviceById(deviceId);
            Optional<Device> cloudDevice = restClient.getDeviceById(deviceId);
            Device expected = edgeDevice.get();
            Device actual = cloudDevice.get();
            Assert.assertEquals("Devices on cloud and edge are different", expected, actual);
        }
    }

    private void assertAssets(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            AssetId assetId = new AssetId(entityId.getId());
            Optional<Asset> edgeAsset = edgeRestClient.getAssetById(assetId);
            Optional<Asset> cloudAsset = restClient.getAssetById(assetId);
            Asset expected = edgeAsset.get();
            Asset actual = cloudAsset.get();
            Assert.assertEquals("Assets on cloud and edge are different", expected, actual);
        }
    }

    private void assertEntityViews(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            EntityViewId entityViewId = new EntityViewId(entityId.getId());
            Optional<EntityView> edgeEntityView = edgeRestClient.getEntityViewById(entityViewId);
            Optional<EntityView> cloudEntityView = restClient.getEntityViewById(entityViewId);
            EntityView expected = edgeEntityView.get();
            EntityView actual = cloudEntityView.get();
            Assert.assertEquals("Entity Views on cloud and edge are different", expected, actual);
        }
    }

    private void assertDashboards(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            DashboardId dashboardId = new DashboardId(entityId.getId());
            Optional<Dashboard> edgeDashboard = edgeRestClient.getDashboardById(dashboardId);
            Optional<Dashboard> cloudDashboard = restClient.getDashboardById(dashboardId);
            Dashboard expected = edgeDashboard.get();
            Dashboard actual = cloudDashboard.get();
            Assert.assertEquals("Dashboards on cloud and edge are different", expected, actual);
        }
    }

    private void assertUsers(List<EntityId> entityIds) {
        for (EntityId entityId : entityIds) {
            UserId userId = new UserId(entityId.getId());
            Optional<User> edgeUser = edgeRestClient.getUserById(userId);
            Optional<User> cloudUser = restClient.getUserById(userId);
            User expected = edgeUser.get();
            User actual = cloudUser.get();
            expected.setAdditionalInfo(cleanLastLoginTsFromAdditionalInfo(expected.getAdditionalInfo()));
            actual.setAdditionalInfo(cleanLastLoginTsFromAdditionalInfo(actual.getAdditionalInfo()));
            Assert.assertEquals("Users on cloud and edge are different (except lastLoginTs)", expected, actual);
        }
    }

    private JsonNode cleanLastLoginTsFromAdditionalInfo(JsonNode additionalInfo) {
        if (additionalInfo != null && additionalInfo.has("lastLoginTs")) {
            ((ObjectNode) additionalInfo).remove("lastLoginTs");
        }
        return additionalInfo;
    }

    @Test
    public void testDevices() throws Exception {
        EntityGroup savedDeviceEntityGroup = createEntityGroup(EntityType.DEVICE);
        Device edgeDevice1 = saveAndAssignDeviceToEdge(savedDeviceEntityGroup);

        restClient.saveDeviceAttributes(edgeDevice1.getId(), DataConstants.SERVER_SCOPE, mapper.readTree("{\"key1\":\"value1\"}"));
        restClient.saveDeviceAttributes(edgeDevice1.getId(), DataConstants.SHARED_SCOPE, mapper.readTree("{\"key2\":\"value2\"}"));

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> verifyAttributeOnEdge(edgeDevice1.getId(), DataConstants.SERVER_SCOPE, "key1", "value1"));

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> verifyAttributeOnEdge(edgeDevice1.getId(), DataConstants.SHARED_SCOPE, "key2", "value2"));

        restClient.unassignEntityGroupFromEdge(edge.getId(), savedDeviceEntityGroup.getId(), EntityType.DEVICE);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDeviceById(edgeDevice1.getId()).isEmpty());

        restClient.assignEntityGroupToEdge(edge.getId(), savedDeviceEntityGroup.getId(), EntityType.DEVICE);
    }

    private boolean verifyAttributeOnEdge(EntityId entityId, String scope, String key, String expectedValue) {
        List<AttributeKvEntry> attributesByScope = edgeRestClient.getAttributesByScope(entityId, scope, Arrays.asList(key));
        if (attributesByScope.isEmpty()) {
            return false;
        }
        AttributeKvEntry attributeKvEntry = attributesByScope.get(0);
        return attributeKvEntry.getValueAsString().equals(expectedValue);
    }

    @Test
    public void testAssets() throws Exception {
        EntityGroup savedAssetEntityGroup = createEntityGroup(EntityType.ASSET);
        Asset savedAsset = saveAndAssignAssetToEdge(savedAssetEntityGroup);

        restClient.unassignEntityGroupFromEdge(edge.getId(), savedAssetEntityGroup.getId(), EntityType.ASSET);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getAssetById(savedAsset.getId()).isEmpty());

        restClient.assignEntityGroupToEdge(edge.getId(), savedAssetEntityGroup.getId(), EntityType.ASSET);
    }

    @Test
    public void testRuleChains() throws Exception {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getRuleChains(new PageLink(100)).getTotalElements() == 1);

        PageData<RuleChain> pageData = edgeRestClient.getRuleChains(new PageLink(100));
        assertEntitiesByIdsAndType(pageData.getData().stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.RULE_CHAIN);

        RuleChain ruleChain = new RuleChain();
        ruleChain.setName("Edge Test Rule Chain");
        ruleChain.setType(RuleChainType.EDGE);
        RuleChain savedRuleChain = restClient.saveRuleChain(ruleChain);
        restClient.assignRuleChainToEdge(edge.getId(), savedRuleChain.getId());
        createRuleChainMetadata(savedRuleChain);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getRuleChainById(savedRuleChain.getId()).isPresent());

        assertEntitiesByIdsAndType(Collections.singletonList(savedRuleChain.getId()), EntityType.RULE_CHAIN);

        restClient.unassignRuleChainFromEdge(edge.getId(), savedRuleChain.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getRuleChainById(savedRuleChain.getId()).isEmpty());

        restClient.deleteRuleChain(savedRuleChain.getId());
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

        restClient.saveRuleChainMetaData(ruleChainMetaData);
    }

    @Test
    public void testDashboards() throws Exception {
        verifyEntityGroups(EntityType.DASHBOARD, 1);

        EntityGroup dashboardEntityGroup = new EntityGroup();
        dashboardEntityGroup.setType(EntityType.DASHBOARD);
        dashboardEntityGroup.setName("DashboardGroup");
        EntityGroupInfo savedDashboardEntityGroup = restClient.saveEntityGroup(dashboardEntityGroup);
        Dashboard savedDashboardOnCloud = saveDashboardOnCloud("Edge Dashboard 1", savedDashboardEntityGroup.getId());

        restClient.assignEntityGroupToEdge(edge.getId(), savedDashboardEntityGroup.getId(), EntityType.DASHBOARD);

        verifyEntityGroups(EntityType.DASHBOARD, 2);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDashboardById(savedDashboardOnCloud.getId()).isPresent());

        restClient.unassignEntityGroupFromEdge(edge.getId(), savedDashboardEntityGroup.getId(), EntityType.DASHBOARD);

        verifyEntityGroups(EntityType.DASHBOARD, 1);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDashboardById(savedDashboardOnCloud.getId()).isEmpty());

        restClient.deleteDashboard(savedDashboardOnCloud.getId());
    }

    @Test
    public void testRelations() throws Exception {
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));
        Asset asset = saveAndAssignAssetToEdge(createEntityGroup(EntityType.ASSET));

        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(device.getId());
        relation.setTo(asset.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        restClient.saveRelation(relation);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getRelation(relation.getFrom(), relation.getType(), relation.getTypeGroup(), relation.getTo()).isPresent());

        restClient.deleteRelation(relation.getFrom(), relation.getType(), relation.getTypeGroup(), relation.getTo());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getRelation(relation.getFrom(), relation.getType(), relation.getTypeGroup(), relation.getTo()).isEmpty());
    }

    @Test
    public void testAlarms() throws Exception {
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        Alarm alarm = new Alarm();
        alarm.setOriginator(device.getId());
        alarm.setStatus(AlarmStatus.ACTIVE_UNACK);
        alarm.setType("alarm");
        alarm.setSeverity(AlarmSeverity.CRITICAL);
        Alarm savedAlarm = restClient.saveAlarm(alarm);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> getAlarmForEntityFromEdge(device.getId()).isPresent());

        restClient.ackAlarm(savedAlarm.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    AlarmInfo alarmData = getAlarmForEntityFromEdge(device.getId()).get();
                    return alarmData.getAckTs() > 0;
                });

        restClient.clearAlarm(savedAlarm.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    AlarmInfo alarmData = getAlarmForEntityFromEdge(device.getId()).get();
                    return alarmData.getClearTs() > 0;
                });

        restClient.deleteAlarm(savedAlarm.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> getAlarmForEntityFromEdge(device.getId()).isEmpty());
    }


    private Optional<AlarmInfo> getAlarmForEntityFromCloud(EntityId entityId) {
        return getAlarmForEntity(entityId, restClient);
    }

    private Optional<AlarmInfo> getAlarmForEntityFromEdge(EntityId entityId) {
        return getAlarmForEntity(entityId, edgeRestClient);
    }

    private Optional<AlarmInfo> getAlarmForEntity(EntityId entityId, RestClient restClient) {
        PageData<AlarmInfo> alarmDataByQuery =
                restClient.getAlarms(entityId, AlarmSearchStatus.ANY, null, new TimePageLink(1), false);
        if (alarmDataByQuery.getData().isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(alarmDataByQuery.getData().get(0));
        }
    }

    @Test
    public void testEntityViews() throws Exception {
        verifyEntityGroups(EntityType.ENTITY_VIEW, 1);

        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        EntityGroup entityViewEntityGroup = new EntityGroup();
        entityViewEntityGroup.setType(EntityType.ENTITY_VIEW);
        entityViewEntityGroup.setName("EntityViewGroup");
        EntityGroupInfo savedEntityViewEntityGroup = restClient.saveEntityGroup(entityViewEntityGroup);
        EntityView savedEntityViewOnCloud = saveEntityViewOnCloud("Edge Entity View 1", "Default", device.getId(), savedEntityViewEntityGroup.getId());

        restClient.assignEntityGroupToEdge(edge.getId(), savedEntityViewEntityGroup.getId(), EntityType.ENTITY_VIEW);

        verifyEntityGroups(EntityType.ENTITY_VIEW, 2);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getEntityViewById(savedEntityViewOnCloud.getId()).isPresent());

        restClient.unassignEntityGroupFromEdge(edge.getId(), savedEntityViewEntityGroup.getId(), EntityType.ENTITY_VIEW);

        verifyEntityGroups(EntityType.ENTITY_VIEW, 1);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getEntityViewById(savedEntityViewOnCloud.getId()).isEmpty());

        restClient.deleteEntityView(savedEntityViewOnCloud.getId());
    }

    @Test
    public void testWidgetsBundleAndWidgetType() throws Exception {
        WidgetsBundle widgetsBundle = new WidgetsBundle();
        widgetsBundle.setTitle("Test Widget Bundle");
        WidgetsBundle savedWidgetsBundle = restClient.saveWidgetsBundle(widgetsBundle);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getWidgetsBundleById(savedWidgetsBundle.getId()).isPresent());

        WidgetTypeDetails widgetType = new WidgetTypeDetails();
        widgetType.setName("Test Widget Type");
        widgetType.setBundleAlias(savedWidgetsBundle.getAlias());
        ObjectNode descriptor = mapper.createObjectNode();
        descriptor.put("key", "value");
        widgetType.setDescriptor(descriptor);
        WidgetType savedWidgetType = restClient.saveWidgetType(widgetType);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getWidgetTypeById(savedWidgetType.getId()).isPresent());

        restClient.deleteWidgetType(savedWidgetType.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getWidgetTypeById(savedWidgetType.getId()).isEmpty());

        restClient.deleteWidgetsBundle(savedWidgetsBundle.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getWidgetsBundleById(savedWidgetsBundle.getId()).isEmpty());
    }

    @Test
    public void testSendPostTelemetryRequestToCloud() throws Exception {
        List<String> keys = Arrays.asList("strTelemetryToCloud", "boolTelemetryToCloud", "doubleTelemetryToCloud", "longTelemetryToCloud");

        JsonObject timeseriesPayload = new JsonObject();
        timeseriesPayload.addProperty("strTelemetryToCloud", "value1");
        timeseriesPayload.addProperty("boolTelemetryToCloud", true);
        timeseriesPayload.addProperty("doubleTelemetryToCloud", 42.0);
        timeseriesPayload.addProperty("longTelemetryToCloud", 72L);

        List<TsKvEntry> kvEntries = sendPostTelemetryRequest(edgeRestClient, edgeUrl, restClient, timeseriesPayload, keys);

        for (TsKvEntry kvEntry : kvEntries) {
            if (kvEntry.getKey().equals("strTelemetryToCloud")) {
                Assert.assertEquals("value1", kvEntry.getStrValue().get());
            }
            if (kvEntry.getKey().equals("boolTelemetryToCloud")) {
                Assert.assertEquals(true, kvEntry.getBooleanValue().get());
            }
            if (kvEntry.getKey().equals("doubleTelemetryToCloud")) {
                Assert.assertEquals(42.0, (double) kvEntry.getDoubleValue().get(), 0.0);
            }
            if (kvEntry.getKey().equals("longTelemetryToCloud")) {
                Assert.assertEquals(72L, kvEntry.getLongValue().get().longValue());
            }
        }
    }

    @Test
    public void testSendPostTelemetryRequestToEdge() throws Exception {
        List<String> keys = Arrays.asList("strTelemetryToEdge", "boolTelemetryToEdge", "doubleTelemetryToEdge", "longTelemetryToEdge");

        JsonObject timeseriesPayload = new JsonObject();
        timeseriesPayload.addProperty("strTelemetryToEdge", "value1");
        timeseriesPayload.addProperty("boolTelemetryToEdge", true);
        timeseriesPayload.addProperty("doubleTelemetryToEdge", 42.0);
        timeseriesPayload.addProperty("longTelemetryToEdge", 72L);

        List<TsKvEntry> kvEntries = sendPostTelemetryRequest(restClient, CLOUD_HTTPS_URL, edgeRestClient, timeseriesPayload, keys);

        for (TsKvEntry kvEntry : kvEntries) {
            if (kvEntry.getKey().equals("strTelemetryToEdge")) {
                Assert.assertEquals("value1", kvEntry.getStrValue().get());
            }
            if (kvEntry.getKey().equals("boolTelemetryToEdge")) {
                Assert.assertEquals(true, kvEntry.getBooleanValue().get());
            }
            if (kvEntry.getKey().equals("doubleTelemetryToEdge")) {
                Assert.assertEquals(42.0, (double) kvEntry.getDoubleValue().get(), 0.0);
            }
            if (kvEntry.getKey().equals("longTelemetryToEdge")) {
                Assert.assertEquals(72L, kvEntry.getLongValue().get().longValue());
            }
        }
    }

    private List<TsKvEntry> sendPostTelemetryRequest(RestClient sourceRestClient, String sourceUrl, RestClient targetRestClient,
                                                     JsonObject timeseriesPayload, List<String> keys) throws Exception {
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        DeviceCredentials deviceCredentials = sourceRestClient.getDeviceCredentialsByDeviceId(device.getId()).get();
        String accessToken = deviceCredentials.getCredentialsId();

        ResponseEntity deviceTelemetryResponse = sourceRestClient.getRestTemplate()
                .postForEntity(sourceUrl + "/api/v1/{credentialsId}/telemetry",
                        mapper.readTree(timeseriesPayload.toString()),
                        ResponseEntity.class,
                        accessToken);
        Assert.assertTrue(deviceTelemetryResponse.getStatusCode().is2xxSuccessful());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> {
                    List<TsKvEntry> latestTimeseries;
                    try {
                        latestTimeseries = targetRestClient.getLatestTimeseries(device.getId(), keys);
                    } catch (Exception e) {
                        return false;
                    }
                    return latestTimeseries.size() == keys.size();
                });
        return targetRestClient.getLatestTimeseries(device.getId(), keys);
    }

    @Test
    public void testSendPostAttributesRequestToCloud() throws Exception {
        List<String> keys = Arrays.asList("strAttrToCloud", "boolAttrToCloud", "doubleAttrToCloud", "longAttrToCloud");

        JsonObject attrPayload = new JsonObject();
        attrPayload.addProperty("strAttrToCloud", "value1");
        attrPayload.addProperty("boolAttrToCloud", true);
        attrPayload.addProperty("doubleAttrToCloud", 42.0);
        attrPayload.addProperty("longAttrToCloud", 72L);

        List<AttributeKvEntry> kvEntries = testSendPostAttributesRequest(edgeRestClient, edgeUrl, restClient, attrPayload, keys);

        for (AttributeKvEntry attributeKvEntry : kvEntries) {
            if (attributeKvEntry.getKey().equals("strAttrToCloud")) {
                Assert.assertEquals("value1", attributeKvEntry.getStrValue().get());
            }
            if (attributeKvEntry.getKey().equals("boolAttrToCloud")) {
                Assert.assertEquals(true, attributeKvEntry.getBooleanValue().get());
            }
            if (attributeKvEntry.getKey().equals("doubleAttrToCloud")) {
                Assert.assertEquals(42.0, (double) attributeKvEntry.getDoubleValue().get(), 0.0);
            }
            if (attributeKvEntry.getKey().equals("longAttrToCloud")) {
                Assert.assertEquals(72L, attributeKvEntry.getLongValue().get().longValue());
            }
        }

    }

    @Test
    public void testSendPostAttributesRequestToEdge() throws Exception {
        List<String> keys = Arrays.asList("strAttrToEdge", "boolAttrToEdge", "doubleAttrToEdge", "longAttrToEdge");

        JsonObject attrPayload = new JsonObject();
        attrPayload.addProperty("strAttrToEdge", "value1");
        attrPayload.addProperty("boolAttrToEdge", true);
        attrPayload.addProperty("doubleAttrToEdge", 42.0);
        attrPayload.addProperty("longAttrToEdge", 72L);

        List<AttributeKvEntry> kvEntries = testSendPostAttributesRequest(restClient, CLOUD_HTTPS_URL, edgeRestClient, attrPayload, keys);

        for (AttributeKvEntry attributeKvEntry : kvEntries) {
            if (attributeKvEntry.getKey().equals("strAttrToEdge")) {
                Assert.assertEquals("value1", attributeKvEntry.getStrValue().get());
            }
            if (attributeKvEntry.getKey().equals("boolAttrToEdge")) {
                Assert.assertEquals(true, attributeKvEntry.getBooleanValue().get());
            }
            if (attributeKvEntry.getKey().equals("doubleAttrToEdge")) {
                Assert.assertEquals(42.0, (double) attributeKvEntry.getDoubleValue().get(), 0.0);
            }
            if (attributeKvEntry.getKey().equals("longAttrToEdge")) {
                Assert.assertEquals(72L, attributeKvEntry.getLongValue().get().longValue());
            }
        }
    }

    private List<AttributeKvEntry> testSendPostAttributesRequest(RestClient sourceRestClient, String sourceUrl, RestClient targetRestClient,
                                               JsonObject attributesPayload, List<String> keys) throws Exception {

        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        DeviceCredentials deviceCredentials = sourceRestClient.getDeviceCredentialsByDeviceId(device.getId()).get();
        String accessToken = deviceCredentials.getCredentialsId();

        ResponseEntity deviceClientsAttributes = sourceRestClient.getRestTemplate()
                .postForEntity(sourceUrl + "/api/v1/" + accessToken + "/attributes/", mapper.readTree(attributesPayload.toString()),
                        ResponseEntity.class,
                        accessToken);
        Assert.assertTrue(deviceClientsAttributes.getStatusCode().is2xxSuccessful());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> targetRestClient.getAttributesByScope(device.getId(), DataConstants.CLIENT_SCOPE, keys).size() == keys.size());

        List<AttributeKvEntry> attributeKvEntries = targetRestClient.getAttributesByScope(device.getId(), DataConstants.CLIENT_SCOPE, keys);

        sourceRestClient.deleteEntityAttributes(device.getId(), DataConstants.CLIENT_SCOPE, keys);

        // TODO: @voba - verify remove of attributes from cloud
//        Awaitility.await()
//                .atMost(30, TimeUnit.SECONDS)
//                .until(() -> targetRestClient.getAttributeKvEntries(globalTestDevice.getId(), keys).isEmpty());

        return attributeKvEntries;
    }

    @Test
    public void testSendAttributesUpdatedToEdge() throws Exception {
        List<String> keys = Arrays.asList("strAttrToEdge", "boolAttrToEdge", "doubleAttrToEdge", "longAttrToEdge");

        JsonObject attrPayload = new JsonObject();
        attrPayload.addProperty("strAttrToEdge", "value1");
        attrPayload.addProperty("boolAttrToEdge", true);
        attrPayload.addProperty("doubleAttrToEdge", 42.0);
        attrPayload.addProperty("longAttrToEdge", 72L);

        List<AttributeKvEntry> kvEntries = sendAttributesUpdated(restClient, edgeRestClient, attrPayload, keys, DataConstants.SERVER_SCOPE);
        verifyAttributesUpdatedToEdge(kvEntries);

        kvEntries = sendAttributesUpdated(restClient, edgeRestClient, attrPayload, keys, DataConstants.SHARED_SCOPE);
        verifyAttributesUpdatedToEdge(kvEntries);
    }

    private void verifyAttributesUpdatedToEdge(List<AttributeKvEntry> kvEntries) {
        for (AttributeKvEntry attributeKvEntry : kvEntries) {
            if (attributeKvEntry.getKey().equals("strAttrToEdge")) {
                Assert.assertEquals("value1", attributeKvEntry.getStrValue().get());
            }
            if (attributeKvEntry.getKey().equals("boolAttrToEdge")) {
                Assert.assertEquals(true, attributeKvEntry.getBooleanValue().get());
            }
            if (attributeKvEntry.getKey().equals("doubleAttrToEdge")) {
                Assert.assertEquals(42.0, (double) attributeKvEntry.getDoubleValue().get(), 0.0);
            }
            if (attributeKvEntry.getKey().equals("longAttrToEdge")) {
                Assert.assertEquals(72L, attributeKvEntry.getLongValue().get().longValue());
            }
        }
    }

    @Test
    public void testSendAttributesUpdatedToCloud() throws Exception {
        List<String> keys = Arrays.asList("strAttrToCloud", "boolAttrToCloud", "doubleAttrToCloud", "longAttrToCloud");

        JsonObject attrPayload = new JsonObject();
        attrPayload.addProperty("strAttrToCloud", "value1");
        attrPayload.addProperty("boolAttrToCloud", true);
        attrPayload.addProperty("doubleAttrToCloud", 42.0);
        attrPayload.addProperty("longAttrToCloud", 72L);

        List<AttributeKvEntry> kvEntries = sendAttributesUpdated(edgeRestClient, restClient, attrPayload, keys, DataConstants.SERVER_SCOPE);
        verifyAttributesUpdatedToCloud(kvEntries);

        kvEntries = sendAttributesUpdated(edgeRestClient, restClient, attrPayload, keys, DataConstants.SHARED_SCOPE);
        verifyAttributesUpdatedToCloud(kvEntries);
    }

    private void verifyAttributesUpdatedToCloud(List<AttributeKvEntry> kvEntries) {
        for (AttributeKvEntry attributeKvEntry : kvEntries) {
            if (attributeKvEntry.getKey().equals("strAttrToCloud")) {
                Assert.assertEquals("value1", attributeKvEntry.getStrValue().get());
            }
            if (attributeKvEntry.getKey().equals("boolAttrToCloud")) {
                Assert.assertEquals(true, attributeKvEntry.getBooleanValue().get());
            }
            if (attributeKvEntry.getKey().equals("doubleAttrToCloud")) {
                Assert.assertEquals(42.0, (double) attributeKvEntry.getDoubleValue().get(), 0.0);
            }
            if (attributeKvEntry.getKey().equals("longAttrToCloud")) {
                Assert.assertEquals(72L, attributeKvEntry.getLongValue().get().longValue());
            }
        }
    }

    private List<AttributeKvEntry> sendAttributesUpdated(RestClient sourceRestClient, RestClient targetRestClient,
                                                         JsonObject attributesPayload, List<String> keys, String scope) throws Exception {

        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        sourceRestClient.saveDeviceAttributes(device.getId(), scope, mapper.readTree(attributesPayload.toString()));

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .until(() -> targetRestClient.getAttributesByScope(device.getId(), scope, keys).size() == keys.size());

        List<AttributeKvEntry> attributeKvEntries =
                targetRestClient.getAttributesByScope(device.getId(), scope, keys);

        sourceRestClient.deleteEntityAttributes(device.getId(), scope, keys);

        // TODO: @voba - verify remove of attributes from edge
//        Awaitility.await()
//                .atMost(30, TimeUnit.SECONDS)
//                .until(() -> targetRestClient.getAttributeKvEntries(globalTestDevice.getId(), keys).isEmpty());

        return attributeKvEntries;
    }

    @Test
    public void sendDeviceToCloud() throws Exception {
        Device savedDeviceOnEdge = saveDeviceOnEdge("Edge Device 2", "default");

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getDeviceById(savedDeviceOnEdge.getId()).isPresent());

        verifyDeviceCredentialsOnCloudAndEdge(savedDeviceOnEdge);

        Optional<DeviceCredentials> deviceCredentialsByDeviceId =
                edgeRestClient.getDeviceCredentialsByDeviceId(savedDeviceOnEdge.getId());
        Assert.assertTrue(deviceCredentialsByDeviceId.isPresent());
        DeviceCredentials deviceCredentials = deviceCredentialsByDeviceId.get();
        deviceCredentials.setCredentialsId("UpdatedToken");
        edgeRestClient.saveDeviceCredentials(deviceCredentials);

        verifyDeviceCredentialsOnCloudAndEdge(savedDeviceOnEdge);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getEntityGroupsForEntity(savedDeviceOnEdge.getId()).size() == 2);

        edgeRestClient.deleteDevice(savedDeviceOnEdge.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getEntityGroupsForEntity(savedDeviceOnEdge.getId()).size() == 1);
    }

    private void verifyDeviceCredentialsOnCloudAndEdge(Device savedDeviceOnEdge) {
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getDeviceCredentialsByDeviceId(savedDeviceOnEdge.getId()).isPresent());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDeviceCredentialsByDeviceId(savedDeviceOnEdge.getId()).isPresent());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    DeviceCredentials deviceCredentialsOnEdge =
                            edgeRestClient.getDeviceCredentialsByDeviceId(savedDeviceOnEdge.getId()).get();
                    DeviceCredentials deviceCredentialsOnCloud =
                            restClient.getDeviceCredentialsByDeviceId(savedDeviceOnEdge.getId()).get();
                    // TODO: @voba - potential fix for future releases
                    deviceCredentialsOnCloud.setId(null);
                    deviceCredentialsOnEdge.setId(null);
                    deviceCredentialsOnCloud.setCreatedTime(0);
                    deviceCredentialsOnEdge.setCreatedTime(0);
                    return deviceCredentialsOnCloud.equals(deviceCredentialsOnEdge);
                });
    }

    @Test
    public void sendDeviceWithNameThatAlreadyExistsOnCloud() throws Exception {
        String deviceName = RandomStringUtils.randomAlphanumeric(15);
        Device savedDeviceOnCloud = saveDeviceOnCloud(deviceName, "default");
        Device savedDeviceOnEdge = saveDeviceOnEdge(deviceName, "default");

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getDeviceById(savedDeviceOnEdge.getId()).isPresent());

        // device on edge must be renamed
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> !edgeRestClient.getDeviceById(savedDeviceOnEdge.getId()).get().getName().equals(deviceName));

        edgeRestClient.deleteDevice(savedDeviceOnEdge.getId());
        restClient.deleteDevice(savedDeviceOnEdge.getId());
        restClient.deleteDevice(savedDeviceOnCloud.getId());
    }

    @Test
    public void sendRelationToCloud() throws Exception {
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        Device savedDeviceOnEdge = saveDeviceOnEdge("Test Device 3", "default");
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getDeviceById(savedDeviceOnEdge.getId()).isPresent());

        EntityRelation relation = new EntityRelation();
        relation.setType("test");
        relation.setFrom(device.getId());
        relation.setTo(savedDeviceOnEdge.getId());
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        edgeRestClient.saveRelation(relation);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getRelation(relation.getFrom(), relation.getType(), relation.getTypeGroup(), relation.getTo()).isPresent());

        edgeRestClient.deleteRelation(relation.getFrom(), relation.getType(), relation.getTypeGroup(), relation.getTo());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> restClient.getRelation(relation.getFrom(), relation.getType(), relation.getTypeGroup(), relation.getTo()).isEmpty());

        edgeRestClient.deleteDevice(savedDeviceOnEdge.getId());
        restClient.deleteDevice(savedDeviceOnEdge.getId());
    }

    @Test
    public void sendAlarmToCloud() throws Exception {
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        Alarm alarm = new Alarm();
        alarm.setOriginator(device.getId());
        alarm.setStatus(AlarmStatus.ACTIVE_UNACK);
        alarm.setType("alarm from edge");
        alarm.setSeverity(AlarmSeverity.MAJOR);
        Alarm savedAlarm = edgeRestClient.saveAlarm(alarm);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> getAlarmForEntityFromCloud(device.getId()).isPresent());

        Assert.assertEquals("Alarm on edge and cloud have different types",
                "alarm from edge", getAlarmForEntityFromCloud(device.getId()).get().getType());

        edgeRestClient.ackAlarm(savedAlarm.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    AlarmInfo alarmData = getAlarmForEntityFromCloud(device.getId()).get();
                    return alarmData.getAckTs() > 0;
                });

        edgeRestClient.clearAlarm(savedAlarm.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    AlarmInfo alarmData = getAlarmForEntityFromCloud(device.getId()).get();
                    return alarmData.getClearTs() > 0;
                });

        edgeRestClient.deleteAlarm(savedAlarm.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> getAlarmForEntityFromCloud(device.getId()).isEmpty());
    }

    @Test
    public void changeOwnerToCustomer() {
        // create device and assign it to edge
        // create customer A on cloud
        // add admin users to customer A
        // change edge owner from tenant to customer A
        // login to edge with customer A admin user
        // make sure that device assigned to edge from tenant is not available on edge anymore
        // change edge owner from customer A to tenant
        // make sure that login edge with customer A admin user doesn't work
    }

    @Test
    public void testOneWayRpcCall() throws Exception {
        // create device on cloud and assign to edge
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<DeviceCredentials> edgeDeviceCredentials = edgeRestClient.getDeviceCredentialsByDeviceId(device.getId());
                    Optional<DeviceCredentials> cloudDeviceCredentials = restClient.getDeviceCredentialsByDeviceId(device.getId());
                    return edgeDeviceCredentials.isPresent() &&
                            cloudDeviceCredentials.isPresent() &&
                            edgeDeviceCredentials.get().getCredentialsId().equals(cloudDeviceCredentials.get().getCredentialsId());
                });

        Optional<DeviceCredentials> deviceCredentials = edgeRestClient.getDeviceCredentialsByDeviceId(device.getId());

        Assert.assertTrue(deviceCredentials.isPresent());

        // subscribe to rpc requests to edge
        final ResponseEntity<JsonNode>[] rpcSubscriptionRequest = new ResponseEntity[]{null};

        new Thread(() -> {
            String subscribeToRpcRequestUrl = edgeUrl + "/api/v1/" + deviceCredentials.get().getCredentialsId() + "/rpc?timeout=20000";
            rpcSubscriptionRequest[0] = edgeRestClient.getRestTemplate().getForEntity(subscribeToRpcRequestUrl, JsonNode.class);
        }).start();

        // send rpc request to device over cloud
        ObjectNode initialRequestBody = mapper.createObjectNode();
        initialRequestBody.put("method", "setGpio");
        initialRequestBody.put("params", "{\"pin\":\"23\", \"value\": 1}");
        restClient.handleOneWayDeviceRPCRequest(device.getId(), initialRequestBody);

        // verify that rpc request was received
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    if (rpcSubscriptionRequest[0] == null || rpcSubscriptionRequest[0].getBody() == null) {
                        return false;
                    }
                    JsonNode requestBody = rpcSubscriptionRequest[0].getBody();
                    if (requestBody.get("id") == null) {
                        return false;
                    }
                    return initialRequestBody.get("method").equals(requestBody.get("method"))
                            && initialRequestBody.get("params").equals(requestBody.get("params"));
                });
    }

    @Test
    public void testTwoWayRpcCall() throws Exception {
        // create device on cloud and assign to edge
        Device device = saveAndAssignDeviceToEdge(createEntityGroup(EntityType.DEVICE));

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<DeviceCredentials> edgeDeviceCredentials = edgeRestClient.getDeviceCredentialsByDeviceId(device.getId());
                    Optional<DeviceCredentials> cloudDeviceCredentials = restClient.getDeviceCredentialsByDeviceId(device.getId());
                    return edgeDeviceCredentials.isPresent() &&
                            cloudDeviceCredentials.isPresent() &&
                            edgeDeviceCredentials.get().getCredentialsId().equals(cloudDeviceCredentials.get().getCredentialsId());
                });

        Optional<DeviceCredentials> deviceCredentials = edgeRestClient.getDeviceCredentialsByDeviceId(device.getId());

        Assert.assertTrue(deviceCredentials.isPresent());

        // subscribe to rpc requests to edge
        final ResponseEntity<JsonNode>[] rpcSubscriptionRequest = new ResponseEntity[]{null};

        new Thread(() -> {
            String subscribeToRpcRequestUrl = edgeUrl + "/api/v1/" + deviceCredentials.get().getCredentialsId() + "/rpc?timeout=20000";
            rpcSubscriptionRequest[0] = edgeRestClient.getRestTemplate().getForEntity(subscribeToRpcRequestUrl, JsonNode.class);
        }).start();

        // send two-way rpc request to device over cloud
        ObjectNode initialRequestBody = mapper.createObjectNode();
        initialRequestBody.put("method", "setGpio");
        initialRequestBody.put("params", "{\"pin\":\"23\", \"value\": 1}");

        final JsonNode[] rpcTwoWayRequest = new JsonNode[]{null};
        new Thread(() -> {
            rpcTwoWayRequest[0] = restClient.handleTwoWayDeviceRPCRequest(device.getId(), initialRequestBody);
        }).start();

        // verify that rpc request was received
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    if (rpcSubscriptionRequest[0] == null || rpcSubscriptionRequest[0].getBody() == null) {
                        return false;
                    }
                    JsonNode requestBody = rpcSubscriptionRequest[0].getBody();
                    if (requestBody.get("id") == null) {
                        return false;
                    }
                    return initialRequestBody.get("method").equals(requestBody.get("method"))
                            && initialRequestBody.get("params").equals(requestBody.get("params"));
                });

        // send response back to the rpc request
        ObjectNode replyBody = mapper.createObjectNode();
        replyBody.put("result", "ok");

        String rpcReply = edgeUrl + "/api/v1/" + deviceCredentials.get().getCredentialsId() + "/rpc/" + rpcSubscriptionRequest[0].getBody().get("id");
        edgeRestClient.getRestTemplate().postForEntity(rpcReply, replyBody, Void.class);

        // verify on the cloud that rpc response was received
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    if (rpcTwoWayRequest[0] == null) {
                        return false;
                    }
                    JsonNode responseBody = rpcTwoWayRequest[0];
                    return "ok".equals(responseBody.get("result").textValue());
                });
    }

    // Utility methods
    private Device saveDeviceOnEdge(String deviceName, String type) throws Exception {
        return saveDevice(deviceName, type, null, edgeRestClient);
    }

    private Device saveDeviceOnCloud(String deviceName, String type) throws Exception {
        return saveDevice(deviceName, type, null, restClient);
    }

    private Device saveDeviceOnCloud(String deviceName, String type, EntityGroupId entityGroupId) throws Exception {
        return saveDevice(deviceName, type, entityGroupId, restClient);
    }

    private Device saveAndAssignDeviceToEdge(EntityGroup savedDeviceEntityGroup) throws Exception {
        Device device = saveDeviceOnCloud(RandomStringUtils.randomAlphanumeric(15), "default", savedDeviceEntityGroup.getId());
        restClient.assignEntityGroupToEdge(edge.getId(), savedDeviceEntityGroup.getId(), EntityType.DEVICE);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getDeviceById(device.getId()).isPresent());

        return device;
    }

    private EntityGroup createEntityGroup(EntityType entityType) {
        EntityGroup assetEntityGroup = new EntityGroup();
        assetEntityGroup.setType(entityType);
        assetEntityGroup.setName(RandomStringUtils.randomAlphanumeric(15));
        return restClient.saveEntityGroup(assetEntityGroup);
    }

    private Asset saveAndAssignAssetToEdge(EntityGroup savedAssetEntityGroup) throws Exception {
        Asset asset = saveAssetOnCloud(RandomStringUtils.randomAlphanumeric(15), "Building", savedAssetEntityGroup.getId());
        restClient.assignEntityGroupToEdge(edge.getId(), savedAssetEntityGroup.getId(), EntityType.ASSET);

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getAssetById(asset.getId()).isPresent());

        return asset;
    }

    private Device saveDevice(String deviceName, String type, EntityGroupId entityGroupId, RestClient restClient) throws Exception {
        Device device = new Device();
        device.setName(deviceName);
        device.setType(type);
        Device savedDevice = restClient.saveDevice(device);
        if (entityGroupId != null) {
            restClient.addEntitiesToEntityGroup(entityGroupId, Arrays.asList(savedDevice.getId()));
        }
        return savedDevice;
    }

    private Asset saveAssetOnCloud(String assetName, String type, EntityGroupId entityGroupId) throws Exception {
        Asset asset = new Asset();
        asset.setName(assetName);
        asset.setType(type);
        Asset savedAsset = restClient.saveAsset(asset);
        if (entityGroupId != null) {
            restClient.addEntitiesToEntityGroup(entityGroupId, Arrays.asList(savedAsset.getId()));
        }
        return savedAsset;
    }

    private Dashboard saveDashboardOnCloud(String dashboardTitle, EntityGroupId entityGroupId) throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setTitle(dashboardTitle);
        Dashboard savedDashboard = restClient.saveDashboard(dashboard);
        if (entityGroupId != null) {
            restClient.addEntitiesToEntityGroup(entityGroupId, Arrays.asList(savedDashboard.getId()));
        }
        return savedDashboard;
    }

    private EntityView saveEntityViewOnCloud(String entityViewName, String type, DeviceId deviceId, EntityGroupId entityGroupId) throws Exception {
        EntityView entityView = new EntityView();
        entityView.setName(entityViewName);
        entityView.setType(type);
        entityView.setEntityId(deviceId);
        EntityView savedEntityView = restClient.saveEntityView(entityView);
        if (entityGroupId != null) {
            restClient.addEntitiesToEntityGroup(entityGroupId, Arrays.asList(savedEntityView.getId()));
        }
        return savedEntityView;
    }



}

