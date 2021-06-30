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
package org.thingsboard.server.service.edge;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.edge.rpc.EdgeRpcClient;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Edge;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.edge.CloudType;
import org.thingsboard.server.common.data.edge.EdgeSettings;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.translation.CustomTranslationService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.DownlinkResponseMsg;
import org.thingsboard.server.gen.edge.v1.EdgeConfiguration;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.gen.edge.v1.UplinkResponseMsg;
import org.thingsboard.server.service.edge.rpc.processor.AlarmCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.DeviceCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.DeviceProfileCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.EntityCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.EntityGroupCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.EntityViewCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.GroupPermissionCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.RelationCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.RuleChainCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.TelemetryCloudProcessor;
import org.thingsboard.server.service.edge.rpc.processor.WidgetBundleCloudProcessor;
import org.thingsboard.server.service.edge.rpc.CloudEventStorageSettings;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.install.InstallScripts;
import org.thingsboard.server.service.queue.TbClusterService;
import org.thingsboard.server.service.state.DefaultDeviceStateService;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CloudManagerService extends BaseCloudEventService {

    private static final ReentrantLock uplinkMsgsPackLock = new ReentrantLock();

    private static final String QUEUE_START_TS_ATTR_KEY = "queueStartTs";

    @Value("${cloud.routingKey}")
    private String routingKey;

    @Value("${cloud.secret}")
    private String routingSecret;

    @Value("${cloud.reconnect_timeout}")
    private long reconnectTimeoutMs;

    @Autowired
    private EdgeService edgeService;

    @Autowired
    private AttributesService attributesService;

    @Autowired
    protected TelemetrySubscriptionService tsSubService;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    protected TbClusterService tbClusterService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceProfileService deviceProfileService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserService userService;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private WidgetsBundleService widgetsBundleService;

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    @Autowired
    private CustomTranslationService customTranslationService;

    @Autowired
    private AdminSettingsService adminSettingsService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Autowired
    private DbCallbackExecutorService dbCallbackExecutorService;

    @Autowired
    private CloudEventStorageSettings cloudEventStorageSettings;

    @Autowired
    private DownlinkMessageService downlinkMessageService;

    @Autowired
    private EdgeRpcClient edgeRpcClient;

    @Autowired
    private RelationCloudProcessor relationProcessor;

    @Autowired
    private DeviceCloudProcessor deviceProcessor;

    @Autowired
    private DeviceProfileCloudProcessor deviceProfileProcessor;

    @Autowired
    private AlarmCloudProcessor alarmProcessor;

    @Autowired
    private EntityCloudProcessor entityProcessor;

    @Autowired
    private TelemetryCloudProcessor telemetryProcessor;

    @Autowired
    private WidgetBundleCloudProcessor widgetBundleProcessor;

    @Autowired
    private EntityViewCloudProcessor entityViewProcessor;

    @Autowired
    private RuleChainCloudProcessor ruleChainProcessor;

    @Autowired
    private GroupPermissionCloudProcessor groupPermissionProcessor;

    @Autowired
    private EntityGroupCloudProcessor entityGroupProcessor;

    @Autowired
    private InstallScripts installScripts;

    private CountDownLatch latch;

    private EdgeId edgeId;
    private EdgeSettings currentEdgeSettings;

    private ExecutorService executor;
    private ScheduledExecutorService reconnectScheduler;
    private ScheduledFuture<?> scheduledFuture;
    private volatile boolean initialized;

    private final Map<Integer, UplinkMsg> pendingMsgsMap = new HashMap<>();

    private TenantId tenantId;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationEvent(ApplicationReadyEvent event) {
        validateRoutingKeyAndSecret();

        log.info("Starting Cloud Edge service");
        edgeRpcClient.connect(routingKey, routingSecret,
                this::onUplinkResponse,
                this::onEdgeUpdate,
                this::onDownlink,
                this::scheduleReconnect);
        executor = Executors.newSingleThreadExecutor();
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor();
        processHandleMessages();
    }

    private void validateRoutingKeyAndSecret() {
        if (StringUtils.isBlank(routingKey) || StringUtils.isBlank(routingSecret)) {
            new Thread(() -> {
                log.error("Routing Key and Routing Secret must be provided and can't be blank. Please configure Routing Key and Routing Secret in the tb-edge.yml file or add CLOUD_ROUTING_KEY and CLOUD_ROUTING_SECRET export to the tb-edge.conf file. Stopping ThingsBoard Edge application...");
                System.exit(-1);
            }, "Shutdown Thread").start();
        }
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        String edgeId = currentEdgeSettings != null ? currentEdgeSettings.getEdgeId() : "";
        log.info("[{}] Starting destroying process", edgeId);
        try {
            edgeRpcClient.disconnect(false);
        } catch (Exception e) {
            log.error("Exception during disconnect", e);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
        }
        log.info("[{}] Destroy was successful", edgeId);
    }

    private void processHandleMessages() {
        executor.submit(() -> {
            while (!Thread.interrupted()) {
                try {
                    if (initialized) {
                        Long queueStartTs = getQueueStartTs().get();
                        TimePageLink pageLink =
                                new TimePageLink(new TimePageLink(cloudEventStorageSettings.getMaxReadRecordsCount()),
                                        queueStartTs,
                                        System.currentTimeMillis());
                        PageData<CloudEvent> pageData;
                        UUID ifOffset = null;
                        boolean success = true;
                        do {
                            pageData = cloudEventService.findCloudEvents(tenantId, pageLink);
                            if (initialized && !pageData.getData().isEmpty()) {
                                log.trace("[{}] event(s) are going to be converted.", pageData.getData().size());
                                List<UplinkMsg> uplinkMsgsPack = convertToUplinkMsgsPack(pageData.getData());
                                success = sendUplinkMsgsPack(uplinkMsgsPack);
                                ifOffset = pageData.getData().get(pageData.getData().size() - 1).getUuidId();
                                if (success) {
                                    pageLink = pageLink.nextPageLink();
                                }
                            }
                        } while (initialized && (!success || pageData.hasNext()));
                        if (ifOffset != null) {
                            Long newStartTs = Uuids.unixTimestamp(ifOffset);
                            updateQueueStartTs(newStartTs);
                        }
                        try {
                            Thread.sleep(cloudEventStorageSettings.getNoRecordsSleepInterval());
                        } catch (InterruptedException e) {
                            log.error("Error during sleep", e);
                        }
                    } else {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                    }
                } catch (Exception e) {
                    log.warn("Failed to process messages handling!", e);
                }
            }
        });
    }

    private boolean sendUplinkMsgsPack(List<UplinkMsg> uplinkMsgsPack) throws InterruptedException {
        try {
            uplinkMsgsPackLock.lock();
            boolean success;
            pendingMsgsMap.clear();
            uplinkMsgsPack.forEach(msg -> pendingMsgsMap.put(msg.getUplinkMsgId(), msg));
            do {
                log.trace("[{}] uplink msg(s) are going to be send.", pendingMsgsMap.values().size());
                latch = new CountDownLatch(pendingMsgsMap.values().size());
                List<UplinkMsg> copy = new ArrayList<>(pendingMsgsMap.values());
                for (UplinkMsg uplinkMsg : copy) {
                    edgeRpcClient.sendUplinkMsg(uplinkMsg);
                }
                success = latch.await(10, TimeUnit.SECONDS);
                if (!success || pendingMsgsMap.values().size() > 0) {
                    log.warn("Failed to deliver the batch: {}", pendingMsgsMap.values());
                }
                if (initialized && (!success || pendingMsgsMap.values().size() > 0)) {
                    try {
                        Thread.sleep(cloudEventStorageSettings.getSleepIntervalBetweenBatches());
                    } catch (InterruptedException e) {
                        log.error("Error during sleep between batches", e);
                    }
                }
            } while (initialized && (!success || pendingMsgsMap.values().size() > 0));
            return success;
        } finally {
            uplinkMsgsPackLock.unlock();
        }
    }

    private List<UplinkMsg> convertToUplinkMsgsPack(List<CloudEvent> cloudEvents) {
        List<UplinkMsg> result = new ArrayList<>();
        for (CloudEvent cloudEvent : cloudEvents) {
            log.trace("Processing cloud event [{}]", cloudEvent);
            UplinkMsg uplinkMsg = null;
            try {
                ActionType edgeEventAction = ActionType.valueOf(cloudEvent.getCloudEventAction());
                switch (edgeEventAction) {
                    case UPDATED:
                    case ADDED:
                    case DELETED:
                    case ALARM_ACK:
                    case ALARM_CLEAR:
                    case CREDENTIALS_UPDATED:
                    case RELATION_ADD_OR_UPDATE:
                    case RELATION_DELETED:
                    case ADDED_TO_ENTITY_GROUP:
                    case REMOVED_FROM_ENTITY_GROUP:
                        uplinkMsg = processEntityMessage(this.tenantId, cloudEvent, edgeEventAction);
                        break;
                    case ATTRIBUTES_UPDATED:
                    case ATTRIBUTES_DELETED:
                    case TIMESERIES_UPDATED:
                        uplinkMsg = telemetryProcessor.processTelemetryMessageMsgToCloud(cloudEvent);
                        break;
                    case ATTRIBUTES_REQUEST:
                        uplinkMsg = telemetryProcessor.processAttributesRequestMsgToCloud(cloudEvent);
                        break;
                    case RELATION_REQUEST:
                        uplinkMsg = relationProcessor.processRelationRequestMsgToCloud(cloudEvent);
                        break;
                    case RULE_CHAIN_METADATA_REQUEST:
                        uplinkMsg = ruleChainProcessor.processRuleChainMetadataRequestMsgToCloud(cloudEvent);
                        break;
                    case CREDENTIALS_REQUEST:
                        uplinkMsg = entityProcessor.processCredentialsRequestMsgToCloud(cloudEvent);
                        break;
                    case GROUP_ENTITIES_REQUEST:
                        uplinkMsg = entityGroupProcessor.processGroupEntitiesRequestMsgToCloud(cloudEvent);
                        break;
                    case GROUP_PERMISSIONS_REQUEST:
                        uplinkMsg = groupPermissionProcessor.processEntityGroupPermissionsRequestMsgToCloud(cloudEvent);
                        break;
                    case RPC_CALL:
                        uplinkMsg = deviceProcessor.processRpcCallResponseMsgToCloud(cloudEvent);
                        break;
                    case DEVICE_PROFILE_DEVICES_REQUEST:
                        uplinkMsg = deviceProfileProcessor.processDeviceProfileDevicesRequestMsgToCloud(cloudEvent);
                        break;
                    case WIDGET_BUNDLE_TYPES_REQUEST:
                        uplinkMsg = widgetBundleProcessor.processWidgetBundleTypesRequestMsgToCloud(cloudEvent);
                        break;
                    case ENTITY_VIEW_REQUEST:
                        uplinkMsg = entityViewProcessor.processEntityViewRequestMsgToCloud(cloudEvent);
                        break;
                }
            } catch (Exception e) {
                log.error("Exception during processing events from queue, skipping event [{}]", cloudEvent, e);
            }
            if (uplinkMsg != null) {
                result.add(uplinkMsg);
            }
        }
        return result;
    }

    private UplinkMsg processEntityMessage(TenantId tenantId, CloudEvent cloudEvent, ActionType edgeEventAction)
            throws ExecutionException, InterruptedException {
        UpdateMsgType msgType = getResponseMsgType(ActionType.valueOf(cloudEvent.getCloudEventAction()));
        log.trace("Executing processEntityMessage, cloudEvent [{}], edgeEventAction [{}], msgType [{}]", cloudEvent, edgeEventAction, msgType);
        switch (cloudEvent.getCloudEventType()) {
            case DEVICE:
                return deviceProcessor.processDeviceMsgToCloud(tenantId, cloudEvent, msgType, edgeEventAction);
            case ALARM:
                return alarmProcessor.processAlarmMsgToCloud(tenantId, cloudEvent, msgType);
            case RELATION:
                return relationProcessor.processRelationMsgToCloud(cloudEvent, msgType);
            default:
                log.warn("Unsupported cloud event type [{}]", cloudEvent);
                return null;
        }
    }

    private UpdateMsgType getResponseMsgType(ActionType actionType) {
        switch (actionType) {
            case UPDATED:
            case CREDENTIALS_UPDATED:
                return UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE;
            case ADDED:
            case RELATION_ADD_OR_UPDATE:
            case ADDED_TO_ENTITY_GROUP:
                return UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE;
            case DELETED:
            case RELATION_DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
                return UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE;
            case ALARM_ACK:
                return UpdateMsgType.ALARM_ACK_RPC_MESSAGE;
            case ALARM_CLEAR:
                return UpdateMsgType.ALARM_CLEAR_RPC_MESSAGE;
            default:
                throw new RuntimeException("Unsupported actionType [" + actionType + "]");
        }
    }

    private ListenableFuture<Long> getQueueStartTs() {
        ListenableFuture<Optional<AttributeKvEntry>> future =
                attributesService.find(tenantId, tenantId, DataConstants.SERVER_SCOPE, QUEUE_START_TS_ATTR_KEY);
        return Futures.transform(future, attributeKvEntryOpt -> {
            if (attributeKvEntryOpt != null && attributeKvEntryOpt.isPresent()) {
                AttributeKvEntry attributeKvEntry = attributeKvEntryOpt.get();
                return attributeKvEntry.getLongValue().isPresent() ? attributeKvEntry.getLongValue().get() : 0L;
            } else {
                return 0L;
            }
        }, dbCallbackExecutorService);
    }

    private void updateQueueStartTs(Long newStartTs) {
        newStartTs = ++newStartTs; // increments ts by 1 - next cloud event search starts from current offset + 1
        List<AttributeKvEntry> attributes = Collections.singletonList(new BaseAttributeKvEntry(new LongDataEntry(QUEUE_START_TS_ATTR_KEY, newStartTs), System.currentTimeMillis()));
        attributesService.save(tenantId, tenantId, DataConstants.SERVER_SCOPE, attributes);
    }

    private void onUplinkResponse(UplinkResponseMsg msg) {
        try {
            if (msg.getSuccess()) {
                pendingMsgsMap.remove(msg.getUplinkMsgId());
                log.debug("[{}] Msg has been processed successfully! {}", routingKey, msg);
            } else {
                log.error("[{}] Msg processing failed! Error msg: {}", routingKey, msg.getErrorMsg());
            }
            latch.countDown();
        } catch (Exception e) {
            log.error("Can't process uplink response message [{}]", msg, e);
        }
    }

    private void onEdgeUpdate(EdgeConfiguration edgeConfiguration) {
        try {
            boolean updatingEdge = this.currentEdgeSettings != null;

            if (scheduledFuture != null) {
                scheduledFuture.cancel(true);
                scheduledFuture = null;
            }

            UUID tenantUUID = new UUID(edgeConfiguration.getTenantIdMSB(), edgeConfiguration.getTenantIdLSB());
            this.tenantId = getOrCreateTenant(new TenantId(tenantUUID), CloudType.valueOf(edgeConfiguration.getCloudType())).getTenantId();

            this.currentEdgeSettings = cloudEventService.findEdgeSettings(tenantId);
            EdgeSettings newEdgeSetting = constructEdgeSettings(edgeConfiguration);
            if (this.currentEdgeSettings == null || !this.currentEdgeSettings.getEdgeId().equals(newEdgeSetting.getEdgeId())) {
                cleanUp();
                this.currentEdgeSettings = newEdgeSetting;
            }

            // TODO: voba - should sync be executed in some other cases ???
            edgeRpcClient.sendSyncRequestMsg(this.currentEdgeSettings.isFullSyncRequired());

            cloudEventService.saveEdgeSettings(tenantId, newEdgeSetting);

            // TODO: voba - verify storage of edge entity
            saveEdge(edgeConfiguration);

            save(DefaultDeviceStateService.ACTIVITY_STATE, true);
            save(DefaultDeviceStateService.LAST_CONNECT_TIME, System.currentTimeMillis());

            AdminSettings existingMailTemplates = adminSettingsService.findAdminSettingsByKey(tenantId, "mailTemplates");
            if (newEdgeSetting.getCloudType().equals(CloudType.CE) && existingMailTemplates == null) {
                installScripts.loadMailTemplates();
            }

            initialized = true;
        } catch (Exception e) {
            log.error("Can't process edge configuration message [{}]", edgeConfiguration, e);
        }
    }
    private void saveEdge(EdgeConfiguration edgeConfiguration) {
        Edge edge = new Edge();
        UUID edgeUUID = new UUID(edgeConfiguration.getEdgeIdMSB(), edgeConfiguration.getEdgeIdLSB());
        edgeId = new EdgeId(edgeUUID);
        edge.setId(edgeId);
        UUID tenantUUID = new UUID(edgeConfiguration.getTenantIdMSB(), edgeConfiguration.getTenantIdLSB());
        edge.setTenantId(new TenantId(tenantUUID));
        // TODO: voba - can't assign edge to non-existing customer
        // UUID customerUUID = new UUID(edgeConfiguration.getCustomerIdMSB(), edgeConfiguration.getCustomerIdLSB());
        // edge.setCustomerId(new CustomerId(customerUUID));
        edge.setName(edgeConfiguration.getName());
        edge.setType(edgeConfiguration.getType());
        edge.setRoutingKey(edgeConfiguration.getRoutingKey());
        edge.setSecret(edgeConfiguration.getSecret());
        edge.setEdgeLicenseKey(edgeConfiguration.getEdgeLicenseKey());
        edge.setCloudEndpoint(edgeConfiguration.getCloudEndpoint());
        edge.setAdditionalInfo(JacksonUtil.toJsonNode(edgeConfiguration.getAdditionalInfo()));
        edgeService.saveEdge(edge, false);
        saveCloudEvent(tenantId, CloudEventType.EDGE, ActionType.ATTRIBUTES_REQUEST, edgeId, null);
        saveCloudEvent(tenantId, CloudEventType.EDGE, ActionType.RELATION_REQUEST, edgeId, null);
    }

    private void cleanUp() {
        log.debug("Starting clean up procedure");
        PageData<Tenant> tenants = tenantService.findTenants(new PageLink(Integer.MAX_VALUE));
        for (Tenant tenant : tenants.getData()) {
            cleanUpTenant(tenant);
        }

        Tenant systemTenant = new Tenant();
        systemTenant.setId(TenantId.SYS_TENANT_ID);
        systemTenant.setTitle("System");
        cleanUpTenant(systemTenant);

        log.debug("Clean up procedure successfully finished!");
    }

    private void cleanUpTenant(Tenant tenant) {
        log.debug("Removing entities for the tenant [{}][{}]", tenant.getTitle(), tenant.getId());
        userService.deleteTenantAdmins(tenant.getId());
        PageData<Customer> customers = customerService.findCustomersByTenantId(tenant.getId(), new PageLink(Integer.MAX_VALUE));
        if (customers != null && customers.getData() != null && !customers.getData().isEmpty()) {
            for (Customer customer : customers.getData()) {
                userService.deleteCustomerUsers(tenant.getId(), customer.getId());
            }
        }
        ruleChainService.deleteRuleChainsByTenantId(tenant.getId());
        entityViewService.deleteEntityViewsByTenantId(tenant.getId());
        deviceService.deleteDevicesByTenantId(tenant.getId());
        deviceProfileService.deleteDeviceProfilesByTenantId(tenant.getId());
        assetService.deleteAssetsByTenantId(tenant.getId());
        dashboardService.deleteDashboardsByTenantId(tenant.getId());
        adminSettingsService.deleteAdminSettingsByKey(tenant.getId(), "mailTemplates");
        adminSettingsService.deleteAdminSettingsByKey(tenant.getId(), "mail");
        widgetsBundleService.deleteWidgetsBundlesByTenantId(tenant.getId());
        whiteLabelingService.saveSystemLoginWhiteLabelingParams(new LoginWhiteLabelingParams());
        whiteLabelingService.saveTenantWhiteLabelingParams(tenant.getId(), new WhiteLabelingParams());
        customTranslationService.saveTenantCustomTranslation(tenant.getId(), new CustomTranslation());
        roleService.deleteRolesByTenantId(tenant.getId());
        groupPermissionService.deleteGroupPermissionsByTenantId(tenant.getId());
        cloudEventService.deleteCloudEventsByTenantId(tenant.getId());
        try {
            List<AttributeKvEntry> attributeKvEntries = attributesService.findAll(tenant.getId(), tenant.getId(), DataConstants.SERVER_SCOPE).get();
            List<String> attrKeys = attributeKvEntries.stream().map(KvEntry::getKey).collect(Collectors.toList());
            attributesService.removeAll(tenant.getId(), tenant.getId(), DataConstants.SERVER_SCOPE, attrKeys);
            ListenableFuture<List<EntityGroup>> entityGroupsFuture = entityGroupService.findAllEntityGroups(tenant.getId(), tenant.getId());
            List<EntityGroup> entityGroups = entityGroupsFuture.get();
            entityGroups.stream()
                    .filter(e -> !e.getName().equals(EntityGroup.GROUP_ALL_NAME))
                    .filter(e -> !e.getName().equals(EntityGroup.GROUP_EDGE_CE_TENANT_ADMINS_NAME))
                    .filter(e -> !e.getName().equals(EntityGroup.GROUP_EDGE_CE_CUSTOMER_USERS_NAME))
                    .forEach(entityGroup -> entityGroupService.deleteEntityGroup(tenant.getId(), entityGroup.getId()));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Unable to delete entity groups", e);
        }
    }

    private Tenant getOrCreateTenant(TenantId tenantId, CloudType cloudType) {
        Tenant tenant = tenantService.findTenantById(tenantId);
        if (tenant == null) {
            tenant = new Tenant();
            tenant.setTitle("Tenant");
            tenant.setId(tenantId);
            Tenant savedTenant = tenantService.saveTenant(tenant, true);
            if (CloudType.CE.equals(cloudType)) {
                entityGroupService.findOrCreateTenantUsersGroup(savedTenant.getId());
                entityGroupService.findOrCreateTenantAdminsGroup(savedTenant.getId());
            }
        }
        return tenant;
    }

    private EdgeSettings constructEdgeSettings(EdgeConfiguration edgeConfiguration) {
        EdgeSettings edgeSettings = new EdgeSettings();
        UUID edgeUUID = new UUID(edgeConfiguration.getEdgeIdMSB(), edgeConfiguration.getEdgeIdLSB());
        edgeSettings.setEdgeId(edgeUUID.toString());
        UUID tenantUUID = new UUID(edgeConfiguration.getTenantIdMSB(), edgeConfiguration.getTenantIdLSB());
        edgeSettings.setTenantId(tenantUUID.toString());
        edgeSettings.setName(edgeConfiguration.getName());
        edgeSettings.setType(edgeConfiguration.getType());
        edgeSettings.setRoutingKey(edgeConfiguration.getRoutingKey());
        edgeSettings.setCloudType(CloudType.valueOf(edgeConfiguration.getCloudType()));
        edgeSettings.setFullSyncRequired(true);
        return edgeSettings;
    }

    private void onDownlink(DownlinkMsg downlinkMsg) {
        ListenableFuture<List<Void>> future = downlinkMessageService.processDownlinkMsg(tenantId, downlinkMsg, this.currentEdgeSettings);
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable List<Void> result) {
                DownlinkResponseMsg downlinkResponseMsg = DownlinkResponseMsg.newBuilder()
                        .setDownlinkMsgId(downlinkMsg.getDownlinkMsgId())
                        .setSuccess(true).build();
                edgeRpcClient.sendDownlinkResponseMsg(downlinkResponseMsg);
            }

            @Override
            public void onFailure(Throwable t) {
                String errorMsg = t.getMessage() != null ? t.getMessage() : "";
                DownlinkResponseMsg downlinkResponseMsg = DownlinkResponseMsg.newBuilder()
                        .setDownlinkMsgId(downlinkMsg.getDownlinkMsgId())
                        .setSuccess(false).setErrorMsg(errorMsg).build();
                edgeRpcClient.sendDownlinkResponseMsg(downlinkResponseMsg);
            }
        }, MoreExecutors.directExecutor());
    }

    private void scheduleReconnect(Exception e) {
        initialized = false;
        if (tenantId != null) {
            save(DefaultDeviceStateService.ACTIVITY_STATE, false);
            save(DefaultDeviceStateService.LAST_DISCONNECT_TIME, System.currentTimeMillis());
        }
        if (scheduledFuture == null) {
            scheduledFuture = reconnectScheduler.scheduleAtFixedRate(() -> {
                log.info("Trying to reconnect due to the error: {}!", e.getMessage());
                try {
                    edgeRpcClient.disconnect(true);
                } catch (Exception ex) {
                    log.error("Exception during disconnect: {}", ex.getMessage());
                }
                edgeRpcClient.connect(routingKey, routingSecret,
                        this::onUplinkResponse,
                        this::onEdgeUpdate,
                        this::onDownlink,
                        this::scheduleReconnect);
            }, reconnectTimeoutMs, reconnectTimeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private void save(String key, long value) {
        tsSubService.saveAttrAndNotify(TenantId.SYS_TENANT_ID, tenantId, DataConstants.SERVER_SCOPE, key, value, new AttributeSaveCallback(key, value));
    }

    private void save(String key, boolean value) {
        tsSubService.saveAttrAndNotify(TenantId.SYS_TENANT_ID, tenantId, DataConstants.SERVER_SCOPE, key, value, new AttributeSaveCallback(key, value));
    }

    private static class AttributeSaveCallback implements FutureCallback<Void> {
        private final String key;
        private final Object value;

        AttributeSaveCallback(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public void onSuccess(@javax.annotation.Nullable Void result) {
            log.trace("Successfully updated attribute [{}] with value [{}]", key, value);
        }

        @Override
        public void onFailure(Throwable t) {
            log.warn("Failed to update attribute [{}] with value [{}]", key, value, t);
        }
    }
}
