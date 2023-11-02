/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.install.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.flow.TbRuleChainInputNode;
import org.thingsboard.rule.engine.flow.TbRuleChainInputNodeConfiguration;
import org.thingsboard.rule.engine.profile.TbDeviceProfileNode;
import org.thingsboard.rule.engine.profile.TbDeviceProfileNodeConfiguration;
import org.thingsboard.rule.engine.transform.TbDuplicateMsgToGroupNode;
import org.thingsboard.rule.engine.transform.TbDuplicateMsgToGroupNodeConfiguration;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.ShortCustomerInfo;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmInfo;
import org.thingsboard.server.common.data.alarm.AlarmQuery;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.edge.EdgeSettings;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.integration.AbstractIntegration;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseReadTsKvQuery;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.query.DynamicValue;
import org.thingsboard.server.common.data.query.FilterPredicateValue;
import org.thingsboard.server.common.data.queue.ProcessingStrategy;
import org.thingsboard.server.common.data.queue.ProcessingStrategyType;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.data.queue.SubmitStrategy;
import org.thingsboard.server.common.data.queue.SubmitStrategyType;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.tenant.profile.TenantProfileQueueConfiguration;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.alarm.AlarmDao;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.audit.AuditLogDao;
import org.thingsboard.server.dao.blob.BlobEntityDao;
import org.thingsboard.server.dao.cloud.CloudEventDao;
import org.thingsboard.server.dao.cloud.CloudEventService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.device.DeviceConnectivityConfiguration;
import org.thingsboard.server.dao.edge.EdgeEventDao;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.entity.EntityService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.event.EventService;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.model.sql.DeviceProfileEntity;
import org.thingsboard.server.dao.queue.QueueService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.sql.JpaExecutorService;
import org.thingsboard.server.dao.sql.device.DeviceProfileRepository;
import org.thingsboard.server.dao.tenant.TenantProfileService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.service.component.ComponentDiscoveryService;
import org.thingsboard.server.service.component.RuleNodeClassInfo;
import org.thingsboard.server.service.install.InstallScripts;
import org.thingsboard.server.service.install.SystemDataLoaderService;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.StringUtils.isBlank;

@Service
@Profile("install")
@Slf4j
public class DefaultDataUpdateService implements DataUpdateService {

    private static final int MAX_PENDING_SAVE_RULE_NODE_FUTURES = 256;
    private static final int DEFAULT_PAGE_SIZE = 1024;
    private static final int DEFAULT_LIMIT = 100;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private RelationService relationService;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private InstallScripts installScripts;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminSettingsService adminSettingsService;

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private EdgeService edgeService;

    @Autowired
    private SystemDataLoaderService systemDataLoaderService;

    @Autowired
    private AttributesService attributesService;

    @Autowired
    private TimeseriesService tsService;

    @Autowired
    private EntityService entityService;

    @Autowired
    private AlarmDao alarmDao;

    @Autowired
    private DeviceProfileRepository deviceProfileRepository;

    @Autowired
    private RateLimitsUpdater rateLimitsUpdater;

    @Autowired
    private WidgetsBundleService widgetsBundleService;

    @Autowired
    private CloudEventService cloudEventService;

    @Autowired
    private TenantProfileService tenantProfileService;

    @Lazy
    @Autowired
    private QueueService queueService;

    @Autowired
    private ComponentDiscoveryService componentDiscoveryService;

    @Autowired
    private EventService eventService;

    @Autowired
    private AuditLogDao auditLogDao;

    @Autowired
    private BlobEntityDao blobEntityDao;
    @Autowired
    private EdgeEventDao edgeEventDao;

    @Autowired
    private CloudEventDao cloudEventDao;

    @Autowired
    private IntegrationRateLimitsUpdater integrationRateLimitsUpdater;

    @Autowired
    JpaExecutorService jpaExecutorService;

    @Autowired
    DeviceConnectivityConfiguration connectivityConfiguration;

    @Override
    public void updateData(String fromVersion) throws Exception {

        switch (fromVersion) {
            case "1.4.0":
                log.info("Updating data from version 1.4.0 to 2.0.0 ...");
                tenantsDefaultRuleChainUpdater.updateEntities(null);
                break;
            case "3.0.1":
                log.info("Updating data from version 3.0.1 to 3.1.0 ...");
                tenantsEntityViewsUpdater.updateEntities(null);
                break;
            case "3.1.1":
                log.info("Updating data from version 3.1.1 to 3.2.0 ...");
                tenantsRootRuleChainUpdater.updateEntities(null);
                break;
            case "3.2.2":
                log.info("Updating data from version 3.2.2 to 3.3.0 ...");
                tenantsDefaultEdgeRuleChainUpdater.updateEntities(null);
                tenantsAlarmsCustomerUpdater.updateEntities(null);
                deviceProfileEntityDynamicConditionsUpdater.updateEntities(null);
                updateOAuth2Params();
                break;
            case "3.3.2":
                log.info("Updating data from version 3.3.2 to 3.3.3 ...");
                updateNestedRuleChains();
                break;
            case "3.3.3":
                log.info("Updating data from version 3.3.3 to 3.3.4 ...");
                tenantsEdgeSettingsUpdater.updateEntities(null);
                break;
            case "3.3.4":
                log.info("Updating data from version 3.3.4 to 3.4.0 ...");
                tenantsProfileQueueConfigurationUpdater.updateEntities();
                rateLimitsUpdater.updateEntities();
                break;
            case "3.4.0":
                boolean skipEventsMigration = getEnv("TB_SKIP_EVENTS_MIGRATION", false);
                if (!skipEventsMigration) {
                    log.info("Updating data from version 3.4.0 to 3.4.1 ...");
                    eventService.migrateEvents();
                }
                break;
            case "3.4.1":
                log.info("Updating data from version 3.4.1 to 3.4.2 ...");
                systemDataLoaderService.saveLegacyYmlSettings();
                boolean skipAuditLogsMigration = getEnv("TB_SKIP_AUDIT_LOGS_MIGRATION", false);
                if (!skipAuditLogsMigration) {
                    log.info("Starting audit logs migration. Can be skipped with TB_SKIP_AUDIT_LOGS_MIGRATION env variable set to true");
                    auditLogDao.migrateAuditLogs();
                } else {
                    log.info("Skipping audit logs migration");
                }
                boolean skipBlobEntitiesMigration = getEnv("TB_SKIP_BLOB_ENTITIES_MIGRATION", false);
                if (!skipBlobEntitiesMigration) {
                    log.info("Starting blob entities migration. Can be skipped with TB_SKIP_BLOB_ENTITIES_MIGRATION set to true");
                    blobEntityDao.migrateBlobEntities();
                } else {
                    log.info("Skipping blob entities migration");
                }
                migrateEdgeEvents("Starting edge events migration. ");
                break;
            case "3.4.4":
                log.info("Updating data from version 3.4.4 to 3.5.0 ...");

                boolean skipCloudEventsMigration = getEnv("TB_SKIP_CLOUD_EVENTS_MIGRATION", false);
                if (!skipCloudEventsMigration) {
                    log.info("Starting cloud events migration. Can be skipped with TB_SKIP_CLOUD_EVENTS_MIGRATION env variable set to true");
                    cloudEventDao.migrateCloudEvents();
                } else {
                    log.info("Skipping cloud events migration");
                }
                break;
            case "3.5.1":
                log.info("Updating data from version 3.5.1 to 3.6.0 ...");
                integrationRateLimitsUpdater.updateEntities();
                migrateEdgeEvents("Starting edge events migration - adding seq_id column. ");
                break;
            case "3.6.0":
                log.info("Updating data from version 3.6.0 to 3.6.1 ...");
                migrateDeviceConnectivity();
                break;
            case "edge":
                // remove this line in 4+ release
                fixDuplicateSystemWidgetsBundles();

                // reset full sync required - to upload latest widgets from cloud
                tenantsFullSyncRequiredUpdater.updateEntities(null);
                break;
            case "ce":
                log.info("Updating data ...");
                tenantsCustomersGroupAllUpdater.updateEntities();
                tenantEntitiesGroupAllUpdater.updateEntities();
                // tenantIntegrationUpdater.updateEntities();

                //for 2.4.0
                JsonNode mailTemplatesSettings = whiteLabelingService.findMailTemplatesByTenantId(TenantId.SYS_TENANT_ID, TenantId.SYS_TENANT_ID);
                if (mailTemplatesSettings.isEmpty()) {
                    systemDataLoaderService.loadMailTemplates();
                } else {
                    systemDataLoaderService.updateMailTemplates(mailTemplatesSettings);
                }
                // todo: update TbDuplicateMsgToGroupNode to use TbVersionedNode interface.
                updateDuplicateMsgRuleNode();
                break;
            default:
                throw new RuntimeException("Unable to update data, unsupported fromVersion: " + fromVersion);
        }
    }

    private void migrateEdgeEvents(String logPrefix) {
        boolean skipEdgeEventsMigration = getEnv("TB_SKIP_EDGE_EVENTS_MIGRATION", false);
        if (!skipEdgeEventsMigration) {
            log.info(logPrefix + "Can be skipped with TB_SKIP_EDGE_EVENTS_MIGRATION env variable set to true");
            edgeEventDao.migrateEdgeEvents();
        } else {
            log.info("Skipping edge events migration");
        }
    }

    private void migrateDeviceConnectivity() {
        if (adminSettingsService.findAdminSettingsByKey(TenantId.SYS_TENANT_ID, "connectivity") == null) {
            AdminSettings connectivitySettings = new AdminSettings();
            connectivitySettings.setTenantId(TenantId.SYS_TENANT_ID);
            connectivitySettings.setKey("connectivity");
            connectivitySettings.setJsonValue(JacksonUtil.valueToTree(connectivityConfiguration.getConnectivity()));
            adminSettingsService.saveAdminSettings(TenantId.SYS_TENANT_ID, connectivitySettings);
        }
    }

    @Override
    public void upgradeRuleNodes() {
        try {
            int totalRuleNodesUpgraded = 0;
            log.info("Starting rule nodes upgrade ...");
            var nodeClassToVersionMap = componentDiscoveryService.getVersionedNodes();
            log.debug("Found {} versioned nodes to check for upgrade!", nodeClassToVersionMap.size());
            for (var ruleNodeClassInfo : nodeClassToVersionMap) {
                var ruleNodeTypeForLogs = ruleNodeClassInfo.getSimpleName();
                var toVersion = ruleNodeClassInfo.getCurrentVersion();
                log.debug("Going to check for nodes with type: {} to upgrade to version: {}.", ruleNodeTypeForLogs, toVersion);
                var ruleNodesIdsToUpgrade = getRuleNodesIdsWithTypeAndVersionLessThan(ruleNodeClassInfo.getClassName(), toVersion);
                if (ruleNodesIdsToUpgrade.isEmpty()) {
                    log.debug("There are no active nodes with type {}, or all nodes with this type already set to latest version!", ruleNodeTypeForLogs);
                    continue;
                }
                var ruleNodeIdsPartitions = Lists.partition(ruleNodesIdsToUpgrade, MAX_PENDING_SAVE_RULE_NODE_FUTURES);
                for (var ruleNodePack : ruleNodeIdsPartitions) {
                    totalRuleNodesUpgraded += processRuleNodePack(ruleNodePack, ruleNodeClassInfo);
                    log.info("{} upgraded rule nodes so far ...", totalRuleNodesUpgraded);
                }
            }
            log.info("Finished rule nodes upgrade. Upgraded rule nodes count: {}", totalRuleNodesUpgraded);
        } catch (Exception e) {
            log.error("Unexpected error during rule nodes upgrade: ", e);
        }
    }

    private int processRuleNodePack(List<RuleNodeId> ruleNodeIdsBatch, RuleNodeClassInfo ruleNodeClassInfo) {
        var saveFutures = new ArrayList<ListenableFuture<?>>(MAX_PENDING_SAVE_RULE_NODE_FUTURES);
        String ruleNodeType = ruleNodeClassInfo.getSimpleName();
        int toVersion = ruleNodeClassInfo.getCurrentVersion();
        var ruleNodesPack = ruleChainService.findAllRuleNodesByIds(ruleNodeIdsBatch);
        for (var ruleNode : ruleNodesPack) {
            if (ruleNode == null) {
                continue;
            }
            var ruleNodeId = ruleNode.getId();
            var oldConfiguration = ruleNode.getConfiguration();
            int fromVersion = ruleNode.getConfigurationVersion();
            log.debug("Going to upgrade rule node with id: {} type: {} fromVersion: {} toVersion: {}",
                    ruleNodeId, ruleNodeType, fromVersion, toVersion);
            try {
                var tbVersionedNode = (TbNode) ruleNodeClassInfo.getClazz().getDeclaredConstructor().newInstance();
                TbPair<Boolean, JsonNode> upgradeRuleNodeConfigurationResult = tbVersionedNode.upgrade(fromVersion, oldConfiguration);
                if (upgradeRuleNodeConfigurationResult.getFirst()) {
                    ruleNode.setConfiguration(upgradeRuleNodeConfigurationResult.getSecond());
                }
                ruleNode.setConfigurationVersion(toVersion);
                saveFutures.add(jpaExecutorService.submit(() -> {
                    ruleChainService.saveRuleNode(TenantId.SYS_TENANT_ID, ruleNode);
                    log.debug("Successfully upgrade rule node with id: {} type: {} fromVersion: {} toVersion: {}",
                            ruleNodeId, ruleNodeType, fromVersion, toVersion);
                }));
            } catch (Exception e) {
                log.warn("Failed to upgrade rule node with id: {} type: {} fromVersion: {} toVersion: {} due to: ",
                        ruleNodeId, ruleNodeType, fromVersion, toVersion, e);
            }
        }
        try {
            return Futures.allAsList(saveFutures).get().size();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed to process save rule nodes requests due to: ", e);
        }
    }

    private List<RuleNodeId> getRuleNodesIdsWithTypeAndVersionLessThan(String type, int toVersion) {
        var ruleNodeIds = new ArrayList<RuleNodeId>();
        new PageDataIterable<>(pageLink ->
                ruleChainService.findAllRuleNodeIdsByTypeAndVersionLessThan(type, toVersion, pageLink), DEFAULT_PAGE_SIZE
        ).forEach(ruleNodeIds::add);
        return ruleNodeIds;
    }

    private final PaginatedUpdater<String, DeviceProfileEntity> deviceProfileEntityDynamicConditionsUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Device Profile Entity Dynamic Conditions Updater";
                }

                @Override
                protected PageData<DeviceProfileEntity> findEntities(String id, PageLink pageLink) {
                    return DaoUtil.pageToPageData(deviceProfileRepository.findAll(DaoUtil.toPageable(pageLink)));
                }

                @Override
                protected void updateEntity(DeviceProfileEntity deviceProfile) {
                    if (convertDeviceProfileForVersion330(deviceProfile.getProfileData())) {
                        deviceProfileRepository.save(deviceProfile);
                    }
                }
            };

    boolean convertDeviceProfileForVersion330(JsonNode profileData) {
        boolean isUpdated = false;
        if (profileData.has("alarms") && !profileData.get("alarms").isNull()) {
            JsonNode alarms = profileData.get("alarms");
            for (JsonNode alarm : alarms) {
                if (alarm.has("createRules")) {
                    JsonNode createRules = alarm.get("createRules");
                    for (AlarmSeverity severity : AlarmSeverity.values()) {
                        if (createRules.has(severity.name())) {
                            JsonNode spec = createRules.get(severity.name()).get("condition").get("spec");
                            if (convertDeviceProfileAlarmRulesForVersion330(spec)) {
                                isUpdated = true;
                            }
                        }
                    }
                }
                if (alarm.has("clearRule") && !alarm.get("clearRule").isNull()) {
                    JsonNode spec = alarm.get("clearRule").get("condition").get("spec");
                    if (convertDeviceProfileAlarmRulesForVersion330(spec)) {
                        isUpdated = true;
                    }
                }
            }
        }
        return isUpdated;
    }

    private final PaginatedUpdater<String, Tenant> tenantsDefaultRuleChainUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants default rule chain updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain ruleChain = ruleChainService.getRootTenantRuleChain(tenant.getId());
                        if (ruleChain == null) {
                            installScripts.createDefaultRuleChains(tenant.getId());
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private final PaginatedUpdater<String, Tenant> tenantsEdgeSettingsUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants edge settings updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        Optional<AttributeKvEntry> attr =
                                attributesService.find(tenant.getTenantId(), tenant.getTenantId(), DataConstants.SERVER_SCOPE, DataConstants.EDGE_SETTINGS_ATTR_KEY).get();
                        if (attr.isPresent()) {
                            AttributeKvEntry edgeSettingsAttr = attr.get();
                            ObjectNode tmp = (ObjectNode) JacksonUtil.toJsonNode(edgeSettingsAttr.getValueAsString());
                            tmp.remove("cloudType");
                            StringDataEntry edgeSettingsKvEntry = new StringDataEntry(DataConstants.EDGE_SETTINGS_ATTR_KEY, JacksonUtil.toString(tmp));
                            BaseAttributeKvEntry edgeSettingAttr =
                                    new BaseAttributeKvEntry(edgeSettingsKvEntry, edgeSettingsAttr.getLastUpdateTs());
                            List<AttributeKvEntry> attributes =
                                    Collections.singletonList(edgeSettingAttr);
                            attributesService.save(tenant.getTenantId(), tenant.getTenantId(), DataConstants.SERVER_SCOPE, attributes).get();
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private void updateNestedRuleChains() {
        try {
            var updated = 0;
            boolean hasNext = true;
            while (hasNext) {
                List<EntityRelation> relations = relationService.findRuleNodeToRuleChainRelations(TenantId.SYS_TENANT_ID, RuleChainType.CORE, DEFAULT_PAGE_SIZE);
                hasNext = relations.size() == DEFAULT_PAGE_SIZE;
                for (EntityRelation relation : relations) {
                    try {
                        RuleNodeId sourceNodeId = new RuleNodeId(relation.getFrom().getId());
                        RuleNode sourceNode = ruleChainService.findRuleNodeById(TenantId.SYS_TENANT_ID, sourceNodeId);
                        if (sourceNode == null) {
                            log.info("Skip processing of relation for non existing source rule node: [{}]", sourceNodeId);
                            relationService.deleteRelation(TenantId.SYS_TENANT_ID, relation);
                            continue;
                        }
                        RuleChainId sourceRuleChainId = sourceNode.getRuleChainId();
                        RuleChainId targetRuleChainId = new RuleChainId(relation.getTo().getId());
                        RuleChain targetRuleChain = ruleChainService.findRuleChainById(TenantId.SYS_TENANT_ID, targetRuleChainId);
                        if (targetRuleChain == null) {
                            log.info("Skip processing of relation for non existing target rule chain: [{}]", targetRuleChainId);
                            relationService.deleteRelation(TenantId.SYS_TENANT_ID, relation);
                            continue;
                        }
                        TenantId tenantId = targetRuleChain.getTenantId();
                        RuleNode targetNode = new RuleNode();
                        targetNode.setName(targetRuleChain.getName());
                        targetNode.setRuleChainId(sourceRuleChainId);
                        targetNode.setType(TbRuleChainInputNode.class.getName());
                        TbRuleChainInputNodeConfiguration configuration = new TbRuleChainInputNodeConfiguration();
                        configuration.setRuleChainId(targetRuleChain.getId().toString());
                        targetNode.setConfiguration(JacksonUtil.valueToTree(configuration));
                        targetNode.setAdditionalInfo(relation.getAdditionalInfo());
                        targetNode.setDebugMode(false);
                        targetNode = ruleChainService.saveRuleNode(tenantId, targetNode);

                        EntityRelation sourceRuleChainToRuleNode = new EntityRelation();
                        sourceRuleChainToRuleNode.setFrom(sourceRuleChainId);
                        sourceRuleChainToRuleNode.setTo(targetNode.getId());
                        sourceRuleChainToRuleNode.setType(EntityRelation.CONTAINS_TYPE);
                        sourceRuleChainToRuleNode.setTypeGroup(RelationTypeGroup.RULE_CHAIN);
                        relationService.saveRelation(tenantId, sourceRuleChainToRuleNode);

                        EntityRelation sourceRuleNodeToTargetRuleNode = new EntityRelation();
                        sourceRuleNodeToTargetRuleNode.setFrom(sourceNode.getId());
                        sourceRuleNodeToTargetRuleNode.setTo(targetNode.getId());
                        sourceRuleNodeToTargetRuleNode.setType(relation.getType());
                        sourceRuleNodeToTargetRuleNode.setTypeGroup(RelationTypeGroup.RULE_NODE);
                        sourceRuleNodeToTargetRuleNode.setAdditionalInfo(relation.getAdditionalInfo());
                        relationService.saveRelation(tenantId, sourceRuleNodeToTargetRuleNode);

                        //Delete old relation
                        relationService.deleteRelation(tenantId, relation);
                        updated++;
                    } catch (Exception e) {
                        log.info("Failed to update RuleNodeToRuleChainRelation: {}", relation, e);
                    }
                }
                if (updated > 0) {
                    log.info("RuleNodeToRuleChainRelations: {} entities updated so far...", updated);
                }
            }
        } catch (Exception e) {
            log.error("Unable to update Tenant", e);
        }
    }

    private void updateDuplicateMsgRuleNode() {
        PageDataIterable<RuleNode> ruleNodesIterator = new PageDataIterable<>(
                link -> ruleChainService.findAllRuleNodesByType(TbDuplicateMsgToGroupNode.class.getName(), link), 1024);
        ruleNodesIterator.forEach(ruleNode -> {
            TbDuplicateMsgToGroupNodeConfiguration configNode = JacksonUtil.convertValue(ruleNode.getConfiguration(), TbDuplicateMsgToGroupNodeConfiguration.class);
            if (!configNode.isEntityGroupIsMessageOriginator()) {
                if (configNode.getGroupOwnerId() == null) {
                    RuleChain targetRuleChain = ruleChainService.findRuleChainById(TenantId.SYS_TENANT_ID, ruleNode.getRuleChainId());
                    if (targetRuleChain != null) {
                        TenantId tenantId = targetRuleChain.getTenantId();
                        EntityGroup entityGroup = entityGroupService.findEntityGroupById(tenantId, configNode.getEntityGroupId());
                        if (entityGroup != null) {
                            configNode.setGroupOwnerId(entityGroup.getOwnerId());
                            ruleNode.setConfiguration(JacksonUtil.valueToTree(configNode));
                            ruleChainService.saveRuleNode(tenantId, ruleNode);
                        }
                    }
                }
            }
        });
    }

    private final PaginatedUpdater<String, Tenant> tenantsDefaultEdgeRuleChainUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants default edge rule chain updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain defaultEdgeRuleChain = ruleChainService.getEdgeTemplateRootRuleChain(tenant.getId());
                        if (defaultEdgeRuleChain == null) {
                            installScripts.createDefaultEdgeRuleChains(tenant.getId());
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private final PaginatedUpdater<String, Tenant> tenantsRootRuleChainUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants root rule chain updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        RuleChain ruleChain = ruleChainService.getRootTenantRuleChain(tenant.getId());
                        if (ruleChain == null) {
                            installScripts.createDefaultRuleChains(tenant.getId());
                        } else {
                            RuleChainMetaData md = ruleChainService.loadRuleChainMetaData(tenant.getId(), ruleChain.getId());
                            int oldIdx = md.getFirstNodeIndex();
                            int newIdx = md.getNodes().size();

                            if (md.getNodes().size() < oldIdx) {
                                // Skip invalid rule chains
                                return;
                            }

                            RuleNode oldFirstNode = md.getNodes().get(oldIdx);
                            if (oldFirstNode.getType().equals(TbDeviceProfileNode.class.getName())) {
                                // No need to update the rule node twice.
                                return;
                            }

                            RuleNode ruleNode = new RuleNode();
                            ruleNode.setRuleChainId(ruleChain.getId());
                            ruleNode.setName("Device Profile Node");
                            ruleNode.setType(TbDeviceProfileNode.class.getName());
                            ruleNode.setDebugMode(false);
                            TbDeviceProfileNodeConfiguration ruleNodeConfiguration = new TbDeviceProfileNodeConfiguration().defaultConfiguration();
                            ruleNode.setConfiguration(JacksonUtil.valueToTree(ruleNodeConfiguration));
                            ObjectNode additionalInfo = JacksonUtil.newObjectNode();
                            additionalInfo.put("description", "Process incoming messages from devices with the alarm rules defined in the device profile. Dispatch all incoming messages with \"Success\" relation type.");
                            additionalInfo.put("layoutX", 204);
                            additionalInfo.put("layoutY", 240);
                            ruleNode.setAdditionalInfo(additionalInfo);

                            md.getNodes().add(ruleNode);
                            md.setFirstNodeIndex(newIdx);
                            md.addConnectionInfo(newIdx, oldIdx, TbNodeConnectionType.SUCCESS);
                            ruleChainService.saveRuleChainMetaData(tenant.getId(), md, Function.identity());
                        }
                    } catch (Exception e) {
                        log.error("[{}] Unable to update Tenant: {}", tenant.getId(), tenant.getName(), e);
                    }
                }
            };

    private final PaginatedUpdater<String, Tenant> tenantsEntityViewsUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants entity views updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    updateTenantEntityViews(tenant.getId());
                }
            };

    private PaginatedUpdater<String, Tenant> tenantsCustomersGroupAllUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants customers group all updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    new EntityGroupsOwnerUpdater(tenant.getId()).updateEntities(tenant.getId());
                    EntityGroup entityGroup;
                    Optional<EntityGroup> customerGroupOptional =
                            entityGroupService.findEntityGroupByTypeAndName(TenantId.SYS_TENANT_ID, tenant.getId(), EntityType.CUSTOMER, EntityGroup.GROUP_ALL_NAME);
                    if (!customerGroupOptional.isPresent()) {
                        entityGroup = entityGroupService.createEntityGroupAll(TenantId.SYS_TENANT_ID, tenant.getId(), EntityType.CUSTOMER);
                    } else {
                        entityGroup = customerGroupOptional.get();
                    }
                    new CustomersGroupAllUpdater(entityGroup).updateEntities(tenant.getId());
                }
            };

    private PaginatedUpdater<String, Tenant> tenantEntitiesGroupAllUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenant entities group all updater";
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        EntityType[] entityGroupTypes = new EntityType[]{EntityType.USER, EntityType.ASSET, EntityType.DEVICE, EntityType.DASHBOARD, EntityType.ENTITY_VIEW, EntityType.EDGE};
                        for (EntityType groupType : entityGroupTypes) {
                            EntityGroup entityGroup;
                            Optional<EntityGroup> entityGroupOptional =
                                    entityGroupService.findEntityGroupByTypeAndName(TenantId.SYS_TENANT_ID, tenant.getId(), groupType, EntityGroup.GROUP_ALL_NAME);
                            boolean fetchAllTenantEntities;
                            if (!entityGroupOptional.isPresent()) {
                                entityGroup = entityGroupService.createEntityGroupAll(TenantId.SYS_TENANT_ID, tenant.getId(), groupType);
                                fetchAllTenantEntities = true;
                            } else {
                                entityGroup = entityGroupOptional.get();
                                fetchAllTenantEntities = false;
                            }
                            switch (groupType) {
                                case USER:
                                    new CustomerUsersTenantGroupAllRemover(entityGroup).updateEntities(tenant.getId());
                                    entityGroupService.findOrCreateTenantUsersGroup(tenant.getId());
                                    Optional<EntityGroup> tenantAdminsOptional =
                                            entityGroupService.findEntityGroupByTypeAndName(tenant.getId(), tenant.getId(), EntityType.USER, EntityGroup.GROUP_TENANT_ADMINS_NAME);
                                    if (!tenantAdminsOptional.isPresent()) {
                                        EntityGroup tenantAdmins = entityGroupService.findOrCreateTenantAdminsGroup(tenant.getId());
                                        new TenantAdminsGroupAllUpdater(entityGroup, tenantAdmins).updateEntities(tenant.getId());
                                    }
                                    break;
                                case ASSET:
                                    new AssetsGroupAllUpdater(assetService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case DEVICE:
                                    new DevicesGroupAllUpdater(deviceService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case ENTITY_VIEW:
                                    new EntityViewGroupAllUpdater(entityViewService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case EDGE:
                                    new EdgesGroupAllUpdater(edgeService, customerService, entityGroupService, entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                                case DASHBOARD:
                                    new DashboardsGroupAllUpdater(entityGroup, fetchAllTenantEntities).updateEntities(tenant.getId());
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };

    private class EntityGroupsOwnerUpdater extends PaginatedUpdater<EntityId, EntityGroup> {

        private final EntityId ownerId;

        public EntityGroupsOwnerUpdater(EntityId ownerId) {
            this.ownerId = ownerId;
        }

        @Override
        protected String getName() {
            return "Entity groups owner updater";
        }

        @Override
        protected PageData<EntityGroup> findEntities(EntityId parentEntityId, PageLink pageLink) {
            return entityGroupService.findAllEntityGroupsByParentRelation(TenantId.SYS_TENANT_ID, parentEntityId, pageLink);
        }

        @Override
        protected void updateEntity(EntityGroup entityGroup) {
            if (entityGroup.getOwnerId() == null || entityGroup.getOwnerId().isNullUid()) {
                entityGroup.setOwnerId(this.ownerId);
                entityGroupService.saveEntityGroup(TenantId.SYS_TENANT_ID, this.ownerId, entityGroup);
            }
        }

    }

    private class TenantAdminsGroupAllUpdater extends GroupAllPaginatedUpdater<TenantId, User> {

        private final EntityGroup tenantAdmins;

        public TenantAdminsGroupAllUpdater(EntityGroup groupAll, EntityGroup tenantAdmins) {
            super(groupAll);
            this.tenantAdmins = tenantAdmins;
        }

        @Override
        protected String getName() {
            return "Tenant admins group all updater";
        }

        @Override
        protected PageData<User> findEntities(TenantId id, PageLink pageLink) {
            return userService.findTenantAdmins(id, pageLink);
        }

        @Override
        protected void updateGroupEntity(User entity, EntityGroup groupAll) {
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), entity.getId());
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, tenantAdmins.getId(), entity.getId());
        }
    }

    private class CustomerUsersTenantGroupAllRemover extends PaginatedUpdater<TenantId, User> {

        private final EntityGroup groupAll;

        public CustomerUsersTenantGroupAllRemover(EntityGroup groupAll) {
            this.groupAll = groupAll;
        }

        @Override
        protected String getName() {
            return "Customer users tenant group all remover";
        }

        @Override
        protected PageData<User> findEntities(TenantId id, PageLink pageLink) {
            try {
                List<EntityId> entityIds = entityGroupService.findAllEntityIdsAsync(TenantId.SYS_TENANT_ID, groupAll.getId(), new PageLink(Integer.MAX_VALUE)).get();
                List<UserId> userIds = entityIds.stream().map(entityId -> new UserId(entityId.getId())).collect(Collectors.toList());
                List<User> users;
                if (!userIds.isEmpty()) {
                    users = userService.findUsersByTenantIdAndIdsAsync(id, userIds).get();
                } else {
                    users = Collections.emptyList();
                }
                return new PageData<>(users, 1, users.size(), false);
            } catch (Exception e) {
                log.error("Failed to get users from group all!", e);
                throw new RuntimeException("Failed to get users from group all!", e);
            }
        }

        @Override
        protected void updateEntity(User entity) {
            if (Authority.CUSTOMER_USER.equals(entity.getAuthority())) {
                entityGroupService.removeEntityFromEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), entity.getId());
            }
        }
    }

    private class CustomerUsersGroupAllUpdater extends GroupAllPaginatedUpdater<CustomerId, User> {

        private final TenantId tenantId;
        private final EntityGroup customerUsers;

        public CustomerUsersGroupAllUpdater(TenantId tenantId, EntityGroup groupAll, EntityGroup customerUsers) {
            super(groupAll);
            this.tenantId = tenantId;
            this.customerUsers = customerUsers;
        }

        @Override
        protected String getName() {
            return "Customer users group all updater";
        }

        @Override
        protected PageData<User> findEntities(CustomerId id, PageLink pageLink) {
            return userService.findCustomerUsers(this.tenantId, id, pageLink);
        }

        @Override
        protected void updateGroupEntity(User entity, EntityGroup groupAll) {
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), entity.getId());
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, customerUsers.getId(), entity.getId());
        }
    }

    private class CustomersGroupAllUpdater extends GroupAllPaginatedUpdater<TenantId, Customer> {

        public CustomersGroupAllUpdater(EntityGroup groupAll) {
            super(groupAll);
        }

        @Override
        protected String getName() {
            return "Customers group all updater";
        }

        @Override
        protected PageData<Customer> findEntities(TenantId id, PageLink pageLink) {
            return customerService.findCustomersByTenantId(id, pageLink);
        }

        @Override
        protected void updateGroupEntity(Customer customer, EntityGroup groupAll) {
            if (customer.getId() == null || customer.getId().isNullUid()) {
                log.warn("Customer has invalid id [{}]", customer.getId());
                log.warn("[{}]", customer);
                return;
            }
            if (customer.isSubCustomer()) {
                return;
            }
            entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, groupAll.getId(), customer.getId());
            new EntityGroupsOwnerUpdater(customer.getId()).updateEntities(customer.getId());
            EntityType[] entityGroupTypes = new EntityType[]{EntityType.USER, EntityType.CUSTOMER, EntityType.ASSET, EntityType.DEVICE, EntityType.DASHBOARD, EntityType.ENTITY_VIEW, EntityType.EDGE};
            for (EntityType groupType : entityGroupTypes) {
                Optional<EntityGroup> entityGroupOptional =
                        entityGroupService.findEntityGroupByTypeAndName(TenantId.SYS_TENANT_ID, customer.getId(), groupType, EntityGroup.GROUP_ALL_NAME);
                if (!entityGroupOptional.isPresent()) {
                    EntityGroup entityGroup = entityGroupService.createEntityGroupAll(TenantId.SYS_TENANT_ID, customer.getId(), groupType);
                    if (groupType == EntityType.USER) {
                        if (!customer.isPublic()) {
                            entityGroupService.findOrCreateCustomerAdminsGroup(customer.getTenantId(), customer.getId(), null);
                            Optional<EntityGroup> customerUsersOptional =
                                    entityGroupService.findEntityGroupByTypeAndName(customer.getTenantId(), customer.getId(), EntityType.USER, EntityGroup.GROUP_CUSTOMER_USERS_NAME);
                            if (!customerUsersOptional.isPresent()) {
                                EntityGroup customerUsers = entityGroupService.findOrCreateCustomerUsersGroup(customer.getTenantId(), customer.getId(), null);
                                new CustomerUsersGroupAllUpdater(customer.getTenantId(), entityGroup, customerUsers).updateEntities(customer.getId());
                            }
                        } else {
                            entityGroupService.findOrCreatePublicUsersGroup(customer.getTenantId(), customer.getId());
                        }
                    }
                }
            }
        }
    }


    private class DashboardsGroupAllUpdater extends PaginatedUpdater<TenantId, DashboardInfo> {

        private final EntityGroup groupAll;
        private final boolean fetchAllTenantEntities;

        private Map<CustomerId, EntityGroupId> customersGroupMap = new HashMap<>();
        private Map<CustomerId, Customer> customersMap = new HashMap<>();

        public DashboardsGroupAllUpdater(EntityGroup groupAll,
                                         boolean fetchAllTenantEntities) {
            this.groupAll = groupAll;
            this.fetchAllTenantEntities = fetchAllTenantEntities;
        }

        @Override
        protected String getName() {
            return "Dashboards group all updater";
        }

        @Override
        protected PageData<DashboardInfo> findEntities(TenantId id, PageLink pageLink) {
            if (fetchAllTenantEntities) {
                return dashboardService.findDashboardsByTenantId(id, pageLink);
            } else {
                try {
                    List<EntityId> entityIds = entityGroupService.findAllEntityIdsAsync(TenantId.SYS_TENANT_ID, groupAll.getId(), new PageLink(Integer.MAX_VALUE)).get();
                    List<DashboardId> dashboardIds = entityIds.stream().map(entityId -> new DashboardId(entityId.getId())).collect(Collectors.toList());
                    List<DashboardInfo> dashboards;
                    if (!dashboardIds.isEmpty()) {
                        dashboards = dashboardService.findDashboardInfoByIdsAsync(TenantId.SYS_TENANT_ID, dashboardIds).get();
                    } else {
                        dashboards = Collections.emptyList();
                    }
                    return new PageData<>(dashboards, 1, dashboards.size(), false);
                } catch (Exception e) {
                    log.error("Failed to get dashboards from group all!", e);
                    throw new RuntimeException("Failed to get dashboards from group all!", e);
                }
            }
        }

        @Override
        protected void updateEntity(DashboardInfo entity) {
            entityGroupService.addEntityToEntityGroupAll(TenantId.SYS_TENANT_ID, entity.getTenantId(), entity.getId());
            if (entity.getAssignedCustomers() != null) {
                for (ShortCustomerInfo customer : entity.getAssignedCustomers()) {
                    Customer customer1 = customersMap.computeIfAbsent(customer.getCustomerId(), customerId ->
                            customerService.findCustomerById(entity.getTenantId(), customer.getCustomerId()));
                    if (customer1 != null) {
                        EntityGroupId customerEntityGroupId = customersGroupMap.computeIfAbsent(
                                customer.getCustomerId(), customerId ->
                                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(entity.getTenantId(),
                                                customerId, entity.getEntityType()).getId()
                        );
                        entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, customerEntityGroupId, entity.getId());
                        dashboardService.unassignDashboardFromCustomer(TenantId.SYS_TENANT_ID, entity.getId(), customer.getCustomerId());
                    } else {
                        Dashboard dashboard = dashboardService.findDashboardById(TenantId.SYS_TENANT_ID, entity.getId());
                        if (dashboard.removeAssignedCustomerInfo(customer)) {
                            EntityRelation relationToDelete =
                                    new EntityRelation(customer.getCustomerId(), entity.getId(), EntityRelation.CONTAINS_TYPE, RelationTypeGroup.DASHBOARD);
                            relationService.deleteRelation(TenantId.SYS_TENANT_ID, relationToDelete);
                            dashboardService.saveDashboard(dashboard);
                        }
                    }
                }
            }
        }
    }

    private PaginatedUpdater<String, Tenant> tenantIntegrationUpdater = new PaginatedUpdater<String, Tenant>() {
        @Override
        protected PageData<Tenant> findEntities(String id, PageLink pageLink) {
            return tenantService.findTenants(pageLink);
        }

        @Override
        protected String getName() {
            return "Tenant integration updater";
        }

        @Override
        protected void updateEntity(Tenant tenant) {
            updateTenantIntegrations(tenant.getId());
        }
    };

    private void updateTenantEntityViews(TenantId tenantId) {
        PageLink pageLink = new PageLink(100);
        PageData<EntityView> pageData = entityViewService.findEntityViewByTenantId(tenantId, pageLink);
        boolean hasNext = true;
        while (hasNext) {
            List<ListenableFuture<List<Void>>> updateFutures = new ArrayList<>();
            for (EntityView entityView : pageData.getData()) {
                updateFutures.add(updateEntityViewLatestTelemetry(entityView));
            }

            try {
                Futures.allAsList(updateFutures).get();
            } catch (InterruptedException | ExecutionException e) {
                log.error("Failed to copy latest telemetry to entity view", e);
            }

            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
                pageData = entityViewService.findEntityViewByTenantId(tenantId, pageLink);
            } else {
                hasNext = false;
            }
        }
    }

    private ListenableFuture<List<Void>> updateEntityViewLatestTelemetry(EntityView entityView) {
        EntityViewId entityId = entityView.getId();
        List<String> keys = entityView.getKeys() != null && entityView.getKeys().getTimeseries() != null ?
                entityView.getKeys().getTimeseries() : Collections.emptyList();
        long startTs = entityView.getStartTimeMs();
        long endTs = entityView.getEndTimeMs() == 0 ? Long.MAX_VALUE : entityView.getEndTimeMs();
        ListenableFuture<List<String>> keysFuture;
        if (keys.isEmpty()) {
            keysFuture = Futures.transform(tsService.findAllLatest(TenantId.SYS_TENANT_ID,
                    entityView.getEntityId()), latest -> latest.stream().map(TsKvEntry::getKey).collect(Collectors.toList()), MoreExecutors.directExecutor());
        } else {
            keysFuture = Futures.immediateFuture(keys);
        }
        ListenableFuture<List<TsKvEntry>> latestFuture = Futures.transformAsync(keysFuture, fetchKeys -> {
            List<ReadTsKvQuery> queries = fetchKeys.stream().filter(key -> !isBlank(key)).map(key -> new BaseReadTsKvQuery(key, startTs, endTs, 1, "DESC")).collect(Collectors.toList());
            if (!queries.isEmpty()) {
                return tsService.findAll(TenantId.SYS_TENANT_ID, entityView.getEntityId(), queries);
            } else {
                return Futures.immediateFuture(null);
            }
        }, MoreExecutors.directExecutor());
        return Futures.transformAsync(latestFuture, latestValues -> {
            if (latestValues != null && !latestValues.isEmpty()) {
                ListenableFuture<List<Void>> saveFuture = tsService.saveLatest(TenantId.SYS_TENANT_ID, entityId, latestValues);
                return saveFuture;
            }
            return Futures.immediateFuture(null);
        }, MoreExecutors.directExecutor());
    }

    private void updateTenantIntegrations(TenantId tenantId) {
        PageLink pageLink = new PageLink(DEFAULT_LIMIT);
        PageData<Integration> pageData = integrationService.findTenantIntegrations(tenantId, pageLink);
        boolean hasNext = true;
        while (hasNext) {
            for (Integration integration : pageData.getData()) {
                try {
                    Field enabledField = AbstractIntegration.class.getDeclaredField("enabled");
                    enabledField.setAccessible(true);
                    Boolean booleanVal = (Boolean) enabledField.get(integration);
                    if (booleanVal == null) {
                        integration.setEnabled(true);
                        integrationService.saveIntegration(integration);
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    log.error(e.getMessage(), e);
                }
            }
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
                pageData = integrationService.findTenantIntegrations(tenantId, pageLink);
            } else {
                hasNext = false;
            }
        }
    }

    private final PaginatedUpdater<String, Tenant> tenantsAlarmsCustomerUpdater =
            new PaginatedUpdater<>() {

                final AtomicLong processed = new AtomicLong();

                @Override
                protected String getName() {
                    return "Tenants alarms customer updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    updateTenantAlarmsCustomer(tenant.getId(), getName(), processed);
                }
            };

    private void updateTenantAlarmsCustomer(TenantId tenantId, String name, AtomicLong processed) {
        AlarmQuery alarmQuery = new AlarmQuery(null, new TimePageLink(1024 * 4), null, null, null, false);
        PageData<AlarmInfo> alarms = alarmDao.findAlarms(tenantId, alarmQuery);
        boolean hasNext = true;
        while (hasNext) {
            for (Alarm alarm : alarms.getData()) {
                if (alarm.getCustomerId() == null && alarm.getOriginator() != null) {
                    alarm.setCustomerId(entityService.fetchEntityCustomerId(tenantId, alarm.getOriginator()).get());
                    alarmDao.save(tenantId, alarm);
                }
                if (processed.incrementAndGet() % 1000 == 0) {
                    log.info("{}: {} alarms processed so far...", name, processed);
                }
            }
            if (alarms.hasNext()) {
                alarmQuery.setPageLink(alarmQuery.getPageLink().nextPageLink());
                alarms = alarmDao.findAlarms(tenantId, alarmQuery);
            } else {
                hasNext = false;
            }
        }
    }

       private String getEntityAttributeValue(EntityId entityId, String key) {
        List<AttributeKvEntry> attributeKvEntries = null;
        try {
            attributeKvEntries = attributesService.find(TenantId.SYS_TENANT_ID, entityId, DataConstants.SERVER_SCOPE, Arrays.asList(key)).get();
        } catch (Exception e) {
            log.error("Unable to find attribute for " + key + "!", e);
        }
        if (attributeKvEntries != null && !attributeKvEntries.isEmpty()) {
            AttributeKvEntry kvEntry = attributeKvEntries.get(0);
            return kvEntry.getValueAsString();
        } else {
            return "";
        }
    }

    private ListenableFuture<List<String>> saveEntityAttribute(EntityId entityId, String key, String value) {
        List<AttributeKvEntry> attributes = new ArrayList<>();
        long ts = System.currentTimeMillis();
        attributes.add(new BaseAttributeKvEntry(new StringDataEntry(key, value), ts));
        try {
            return attributesService.save(TenantId.SYS_TENANT_ID, entityId, DataConstants.SERVER_SCOPE, attributes);
        } catch (Exception e) {
            log.error("Unable to save White Labeling Params to attributes!", e);
            throw new IncorrectParameterException("Unable to save White Labeling Params to attributes!");
        }
    }

    boolean convertDeviceProfileAlarmRulesForVersion330(JsonNode spec) {
        if (spec != null) {
            if (spec.has("type") && spec.get("type").asText().equals("DURATION")) {
                if (spec.has("value")) {
                    long value = spec.get("value").asLong();
                    var predicate = new FilterPredicateValue<>(
                            value, null, new DynamicValue<>(null, null, false)
                    );
                    ((ObjectNode) spec).remove("value");
                    ((ObjectNode) spec).putPOJO("predicate", predicate);
                    return true;
                }
            } else if (spec.has("type") && spec.get("type").asText().equals("REPEATING")) {
                if (spec.has("count")) {
                    int count = spec.get("count").asInt();
                    var predicate = new FilterPredicateValue<>(
                            count, null, new DynamicValue<>(null, null, false)
                    );
                    ((ObjectNode) spec).remove("count");
                    ((ObjectNode) spec).putPOJO("predicate", predicate);
                    return true;
                }
            }
        }
        return false;
    }

    private void updateOAuth2Params() {
        log.warn("CAUTION: Update of Oauth2 parameters from 3.2.2 to 3.3.0 available only in ThingsBoard versions 3.3.0/3.3.1");
    }

    private final PaginatedUpdater<String, TenantProfile> tenantsProfileQueueConfigurationUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenant profiles queue configuration updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<TenantProfile> findEntities(String id, PageLink pageLink) {
                    return tenantProfileService.findTenantProfiles(TenantId.SYS_TENANT_ID, pageLink);
                }

                @Override
                protected void updateEntity(TenantProfile tenantProfile) {
                    updateTenantProfileQueueConfiguration(tenantProfile);
                }
            };

    private void updateTenantProfileQueueConfiguration(TenantProfile profile) {
        try {
            List<TenantProfileQueueConfiguration> queueConfiguration = profile.getProfileData().getQueueConfiguration();
            if (profile.isIsolatedTbRuleEngine() && (queueConfiguration == null || queueConfiguration.isEmpty())) {
                TenantProfileQueueConfiguration mainQueueConfig = getMainQueueConfiguration();
                profile.getProfileData().setQueueConfiguration(Collections.singletonList((mainQueueConfig)));
                tenantProfileService.saveTenantProfile(TenantId.SYS_TENANT_ID, profile);
                List<TenantId> isolatedTenants = tenantService.findTenantIdsByTenantProfileId(profile.getId());
                isolatedTenants.forEach(tenantId -> {
                    queueService.saveQueue(new Queue(tenantId, mainQueueConfig));
                });
            }
        } catch (Exception e) {
            log.error("Failed to update tenant profile queue configuration name=[" + profile.getName() + "], id=[" + profile.getId().getId() + "]", e);
        }
    }

    private TenantProfileQueueConfiguration getMainQueueConfiguration() {
        TenantProfileQueueConfiguration mainQueueConfiguration = new TenantProfileQueueConfiguration();
        mainQueueConfiguration.setName(DataConstants.MAIN_QUEUE_NAME);
        mainQueueConfiguration.setTopic(DataConstants.MAIN_QUEUE_TOPIC);
        mainQueueConfiguration.setPollInterval(25);
        mainQueueConfiguration.setPartitions(10);
        mainQueueConfiguration.setConsumerPerPartition(true);
        mainQueueConfiguration.setPackProcessingTimeout(2000);
        SubmitStrategy mainQueueSubmitStrategy = new SubmitStrategy();
        mainQueueSubmitStrategy.setType(SubmitStrategyType.BURST);
        mainQueueSubmitStrategy.setBatchSize(1000);
        mainQueueConfiguration.setSubmitStrategy(mainQueueSubmitStrategy);
        ProcessingStrategy mainQueueProcessingStrategy = new ProcessingStrategy();
        mainQueueProcessingStrategy.setType(ProcessingStrategyType.SKIP_ALL_FAILURES);
        mainQueueProcessingStrategy.setRetries(3);
        mainQueueProcessingStrategy.setFailurePercentage(0);
        mainQueueProcessingStrategy.setPauseBetweenRetries(3);
        mainQueueProcessingStrategy.setMaxPauseBetweenRetries(3);
        mainQueueConfiguration.setProcessingStrategy(mainQueueProcessingStrategy);
        return mainQueueConfiguration;
    }

    public static boolean getEnv(String name, boolean defaultValue) {
        String env = System.getenv(name);
        if (env == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(env);
        }
    }

    private void fixDuplicateSystemWidgetsBundles() {
        try {
            List<WidgetsBundle> systemWidgetsBundles = widgetsBundleService.findSystemWidgetsBundles(TenantId.SYS_TENANT_ID);
            for (WidgetsBundle widgetsBundle : systemWidgetsBundles) {
                try {
                    widgetsBundleService.findWidgetsBundleByTenantIdAndAlias(TenantId.SYS_TENANT_ID, widgetsBundle.getAlias());
                } catch (IncorrectResultSizeDataAccessException e) {
                    // fix for duplicate entries of system widgets
                    for (WidgetsBundle systemWidgetsBundle : systemWidgetsBundles) {
                        if (systemWidgetsBundle.getAlias().equals(widgetsBundle.getAlias())) {
                            widgetsBundleService.deleteWidgetsBundle(TenantId.SYS_TENANT_ID, systemWidgetsBundle.getId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unable to fix duplicate system widgets bundles", e);
        }
    }

    private final PaginatedUpdater<String, Tenant> tenantsFullSyncRequiredUpdater =
            new PaginatedUpdater<>() {

                @Override
                protected String getName() {
                    return "Tenants edge full sync required updater";
                }

                @Override
                protected boolean forceReportTotal() {
                    return true;
                }

                @Override
                protected PageData<Tenant> findEntities(String region, PageLink pageLink) {
                    return tenantService.findTenants(pageLink);
                }

                @Override
                protected void updateEntity(Tenant tenant) {
                    try {
                        EdgeSettings edgeSettings = cloudEventService.findEdgeSettings(tenant.getId());
                        if (edgeSettings != null) {
                            edgeSettings.setFullSyncRequired(true);
                            cloudEventService.saveEdgeSettings(tenant.getId(), edgeSettings);
                        } else {
                            log.warn("Edge settings not found for tenant: {}", tenant.getId());
                        }
                    } catch (Exception e) {
                        log.error("Unable to update Tenant", e);
                    }
                }
            };
}
