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
package org.thingsboard.server.service.edge.rpc.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.CloudUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.BlobEntityId;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.page.PageDataIterableByTenantIdEntityId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainConnectionInfo;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.alarm.AlarmCommentService;
import org.thingsboard.server.dao.alarm.AlarmService;
import org.thingsboard.server.dao.asset.AssetProfileService;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.cloud.CloudEventService;
import org.thingsboard.server.dao.cloud.CloudSynchronizationManager;
import org.thingsboard.server.dao.converter.ConverterService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceCredentialsService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeEventService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.edge.EdgeSynchronizationManager;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.menu.CustomMenuService;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.notification.NotificationRuleService;
import org.thingsboard.server.dao.notification.NotificationTargetService;
import org.thingsboard.server.dao.notification.NotificationTemplateService;
import org.thingsboard.server.dao.oauth2.OAuth2Service;
import org.thingsboard.server.dao.ota.OtaPackageService;
import org.thingsboard.server.dao.queue.QueueService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.resource.ResourceService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.scheduler.SchedulerEventService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.tenant.TenantProfileService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.translation.CustomTranslationService;
import org.thingsboard.server.dao.usagerecord.ApiUsageStateService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.widget.WidgetTypeService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.service.edge.rpc.CustomersHierarchyEdgeService;
import org.thingsboard.server.service.edge.rpc.constructor.alarm.AlarmMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.asset.AssetMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.converter.ConverterMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.customer.CustomerMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.dashboard.DashboardMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.device.DeviceMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.edge.EdgeMsgConstructor;
import org.thingsboard.server.service.edge.rpc.constructor.entityview.EntityViewMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.group.GroupMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.integration.IntegrationMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.menu.CustomMenuMsgConstructor;
import org.thingsboard.server.service.edge.rpc.constructor.notification.NotificationMsgConstructor;
import org.thingsboard.server.service.edge.rpc.constructor.oauth2.OAuth2MsgConstructor;
import org.thingsboard.server.service.edge.rpc.constructor.ota.OtaPackageMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.queue.QueueMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.relation.RelationMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.resource.ResourceMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.role.RoleMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.rule.RuleChainMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.scheduler.SchedulerEventMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.settings.AdminSettingsMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.telemetry.EntityDataMsgConstructor;
import org.thingsboard.server.service.edge.rpc.constructor.tenant.TenantMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.translation.CustomTranslationMsgConstructor;
import org.thingsboard.server.service.edge.rpc.constructor.user.UserMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.widget.WidgetMsgConstructorFactory;
import org.thingsboard.server.service.edge.rpc.constructor.wl.WhiteLabelingParamsProtoConstructor;
import org.thingsboard.server.service.edge.rpc.processor.alarm.AlarmEdgeProcessorFactory;
import org.thingsboard.server.service.edge.rpc.processor.asset.AssetEdgeProcessorFactory;
import org.thingsboard.server.service.edge.rpc.processor.entityview.EntityViewProcessorFactory;
import org.thingsboard.server.service.entitiy.TbLogEntityActionService;
import org.thingsboard.server.service.entitiy.queue.TbQueueService;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.ota.OtaPackageStateService;
import org.thingsboard.server.service.profile.TbAssetProfileCache;
import org.thingsboard.server.service.profile.TbDeviceProfileCache;
import org.thingsboard.server.service.rpc.TbCoreDeviceRpcService;
import org.thingsboard.server.service.security.permission.OwnersCacheService;
import org.thingsboard.server.service.security.permission.UserPermissionsService;
import org.thingsboard.server.service.state.DefaultDeviceStateService;
import org.thingsboard.server.service.state.DeviceStateService;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class BaseEdgeProcessor {

    protected static final Lock deviceCreationLock = new ReentrantLock();
    protected static final Lock assetCreationLock = new ReentrantLock();
    protected static final Lock widgetCreationLock = new ReentrantLock();
    protected static final Lock customerCreationLock = new ReentrantLock();

    protected static final int DEFAULT_PAGE_SIZE = 100;

    @Autowired
    protected TelemetrySubscriptionService tsSubService;

    @Autowired
    protected TbLogEntityActionService logEntityActionService;

    @Autowired
    protected RuleChainService ruleChainService;

    @Autowired
    protected AlarmService alarmService;

    @Autowired
    protected AlarmCommentService alarmCommentService;

    @Autowired
    protected DeviceService deviceService;

    @Autowired
    protected TbDeviceProfileCache deviceProfileCache;

    @Autowired
    protected TbAssetProfileCache assetProfileCache;

    @Autowired
    protected DashboardService dashboardService;

    @Autowired
    protected AssetService assetService;

    @Autowired
    protected EntityViewService entityViewService;

    @Autowired
    protected TenantService tenantService;

    @Autowired
    protected TenantProfileService tenantProfileService;

    @Autowired
    protected EdgeService edgeService;

    @Autowired
    protected CustomerService customerService;

    @Autowired
    protected UserService userService;

    @Autowired
    protected DeviceProfileService deviceProfileService;

    @Autowired
    protected AssetProfileService assetProfileService;

    @Autowired
    protected RelationService relationService;

    @Autowired
    protected DeviceCredentialsService deviceCredentialsService;

    @Autowired
    protected AttributesService attributesService;

    @Autowired
    protected TbClusterService tbClusterService;

    @Autowired
    protected DeviceStateService deviceStateService;

    @Autowired
    protected EdgeEventService edgeEventService;

    @Autowired
    protected WidgetsBundleService widgetsBundleService;

    @Autowired
    protected WidgetTypeService widgetTypeService;

    @Autowired
    protected OtaPackageService otaPackageService;

    @Autowired
    protected QueueService queueService;

    @Autowired
    protected PartitionService partitionService;

    // Edge services:
    @Autowired
    protected AdminSettingsService adminSettingsService;

    @Autowired
    protected ApiUsageStateService apiUsageStateService;

    @Autowired
    protected TbQueueService tbQueueService;

    @Autowired
    protected CloudEventService cloudEventService;

    @Autowired
    protected TbCoreDeviceRpcService tbCoreDeviceRpcService;

    @Autowired
    protected OtaPackageStateService otaPackageStateService;

    @Autowired
    protected ResourceService resourceService;

    @Autowired
    protected NotificationRuleService notificationRuleService;

    @Autowired
    protected NotificationTargetService notificationTargetService;

    @Autowired
    protected NotificationTemplateService notificationTemplateService;

    @Autowired
    protected OAuth2Service oAuth2Service;

    @Autowired
    @Lazy
    protected TbQueueProducerProvider producerProvider;

    @Autowired
    protected DataValidator<Device> deviceValidator;

    @Autowired
    protected DataValidator<Asset> assetValidator;

    @Autowired
    protected DataValidator<Role> roleValidator;

    @Autowired
    protected DataValidator<Converter> converterValidator;

    @Autowired
    protected DataValidator<Integration> integrationValidator;

    @Autowired
    protected DataValidator<Tenant> tenantValidator;

    @Autowired
    protected DataValidator<DeviceProfile> deviceProfileValidator;

    @Autowired
    protected DataValidator<AssetProfile> assetProfileValidator;

    @Autowired
    protected DataValidator<Dashboard> dashboardValidator;

    @Autowired
    protected DataValidator<EntityView> entityViewValidator;

    @Autowired
    protected DataValidator<SchedulerEvent> schedulerEventValidator;

    @Autowired
    protected DataValidator<TbResource> resourceValidator;

    @Autowired
    protected EdgeMsgConstructor edgeMsgConstructor;

    @Autowired
    protected EntityDataMsgConstructor entityDataMsgConstructor;

    @Autowired
    protected CustomMenuMsgConstructor customMenuMsgConstructor;

    @Autowired
    protected NotificationMsgConstructor notificationMsgConstructor;

    @Autowired
    protected OAuth2MsgConstructor oAuth2MsgConstructor;

    @Autowired
    protected RuleChainMsgConstructorFactory ruleChainMsgConstructorFactory;

    @Autowired
    protected AlarmMsgConstructorFactory alarmMsgConstructorFactory;

    @Autowired
    protected DeviceMsgConstructorFactory deviceMsgConstructorFactory;

    @Autowired
    protected AssetMsgConstructorFactory assetMsgConstructorFactory;

    @Autowired
    protected EntityViewMsgConstructorFactory entityViewMsgConstructorFactory;

    @Autowired
    protected DashboardMsgConstructorFactory dashboardMsgConstructorFactory;

    @Autowired
    protected RelationMsgConstructorFactory relationMsgConstructorFactory;

    @Autowired
    protected UserMsgConstructorFactory userMsgConstructorFactory;

    @Autowired
    protected CustomerMsgConstructorFactory customerMsgConstructorFactory;

    @Autowired
    protected TenantMsgConstructorFactory tenantMsgConstructorFactory;

    @Autowired
    protected WidgetMsgConstructorFactory widgetMsgConstructorFactory;

    @Autowired
    protected AdminSettingsMsgConstructorFactory adminSettingsMsgConstructorFactory;

    @Autowired
    protected OtaPackageMsgConstructorFactory otaPackageMsgConstructorFactory;

    @Autowired
    protected QueueMsgConstructorFactory queueMsgConstructorFactory;

    @Autowired
    protected ResourceMsgConstructorFactory resourceMsgConstructorFactory;

    @Autowired
    protected AlarmEdgeProcessorFactory alarmEdgeProcessorFactory;

    @Autowired
    protected AssetEdgeProcessorFactory assetEdgeProcessorFactory;

    @Autowired
    protected EntityViewProcessorFactory entityViewProcessorFactory;

    @Autowired
    protected EdgeSynchronizationManager edgeSynchronizationManager;

    @Autowired
    protected CloudSynchronizationManager cloudSynchronizationManager;

    @Autowired
    protected DbCallbackExecutorService dbCallbackExecutorService;

    // PE context
    @Autowired
    protected WhiteLabelingService whiteLabelingService;

    @Autowired
    protected CustomTranslationService customTranslationService;

    @Autowired
    protected CustomMenuService customMenuService;

    @Autowired
    protected EntityGroupService entityGroupService;

    @Autowired
    protected RoleService roleService;

    @Autowired
    protected GroupPermissionService groupPermissionService;

    @Autowired
    protected UserPermissionsService userPermissionsService;

    @Autowired
    protected SchedulerEventService schedulerEventService;

    @Autowired
    protected IntegrationService integrationService;

    @Autowired
    protected ConverterService converterService;

    @Autowired
    protected WhiteLabelingParamsProtoConstructor whiteLabelingParamsProtoConstructor;

    @Autowired
    protected CustomTranslationMsgConstructor customTranslationMsgConstructor;

    @Autowired
    protected RoleMsgConstructorFactory roleMsgConstructorFactory;

    @Autowired
    protected SchedulerEventMsgConstructorFactory schedulerEventMsgConstructorFactory;

    @Autowired
    protected GroupMsgConstructorFactory groupMsgConstructorFactory;

    @Autowired
    protected ConverterMsgConstructorFactory converterMsgConstructorFactory;

    @Autowired
    protected IntegrationMsgConstructorFactory integrationMsgConstructorFactory;

    @Autowired
    protected CustomersHierarchyEdgeService customersHierarchyEdgeService;

    @Autowired
    protected OwnersCacheService ownersCacheService;

    protected ListenableFuture<Void> saveEdgeEvent(TenantId tenantId, EdgeId edgeId, EdgeEventType type, EdgeEventActionType action, EntityId entityId, JsonNode body) {
        return saveEdgeEvent(tenantId, edgeId, type, action, entityId, body, null);
    }

    protected ListenableFuture<Void> saveEdgeEvent(TenantId tenantId,
                                                   EdgeId edgeId,
                                                   EdgeEventType type,
                                                   EdgeEventActionType action,
                                                   EntityId entityId,
                                                   JsonNode body,
                                                   EntityGroupId entityGroupId) {
        ListenableFuture<Optional<AttributeKvEntry>> future =
                attributesService.find(tenantId, edgeId, AttributeScope.SERVER_SCOPE, DefaultDeviceStateService.ACTIVITY_STATE);
        return Futures.transformAsync(future, activeOpt -> {
            if (activeOpt.isEmpty()) {
                log.trace("Edge is not activated. Skipping event. tenantId [{}], edgeId [{}], type[{}], " +
                                "action [{}], entityId [{}], body [{}], entityGroupId [{}]",
                        tenantId, edgeId, type, action, entityId, body, entityGroupId);
                return Futures.immediateFuture(null);
            }
            if (activeOpt.get().getBooleanValue().isPresent() && activeOpt.get().getBooleanValue().get()) {
                return doSaveEdgeEvent(tenantId, edgeId, type, action, entityId, body, entityGroupId);
            } else {
                if (doSaveIfEdgeIsOffline(type, action)) {
                    return doSaveEdgeEvent(tenantId, edgeId, type, action, entityId, body, entityGroupId);
                } else {
                    log.trace("Edge is not active at the moment. Skipping event. tenantId [{}], edgeId [{}], type[{}], " +
                                    "action [{}], entityId [{}], body [{}], entityGroupId [{}]",
                            tenantId, edgeId, type, action, entityId, body, entityGroupId);
                    return Futures.immediateFuture(null);
                }
            }
        }, dbCallbackExecutorService);
    }

    private boolean doSaveIfEdgeIsOffline(EdgeEventType type,
                                          EdgeEventActionType action) {
        return switch (action) {
            case TIMESERIES_UPDATED, ALARM_ACK, ALARM_CLEAR, ALARM_ASSIGNED, ALARM_UNASSIGNED, CREDENTIALS_REQUEST, ADDED_COMMENT, UPDATED_COMMENT ->
                    true;
            default -> switch (type) {
                case ALARM, ALARM_COMMENT, RULE_CHAIN, RULE_CHAIN_METADATA, CUSTOMER, TENANT, TENANT_PROFILE, WIDGETS_BUNDLE, WIDGET_TYPE,
                        ADMIN_SETTINGS, OTA_PACKAGE, QUEUE, RELATION, NOTIFICATION_TEMPLATE, NOTIFICATION_TARGET, NOTIFICATION_RULE,
                        ROLE, INTEGRATION, CONVERTER, WHITE_LABELING, LOGIN_WHITE_LABELING, CUSTOM_TRANSLATION, CUSTOM_MENU, MAIL_TEMPLATES ->
                        true;
                default -> false;
            };
        };

    }

    private ListenableFuture<Void> doSaveEdgeEvent(TenantId tenantId, EdgeId edgeId, EdgeEventType type, EdgeEventActionType action, EntityId entityId, JsonNode body,
                                                   EntityGroupId entityGroupId) {
        log.debug("Pushing event to edge queue. tenantId [{}], edgeId [{}], type[{}], " +
                        "action [{}], entityId [{}], body [{}], entityGroupId [{}]",
                tenantId, edgeId, type, action, entityId, body, entityGroupId);

        EdgeEvent edgeEvent = EdgeUtils.constructEdgeEvent(tenantId, edgeId, type, action, entityId, body, entityGroupId);

        return edgeEventService.saveAsync(edgeEvent);
    }

    protected ListenableFuture<Void> processActionForAllEdges(TenantId tenantId, EdgeEventType type,
                                                              EdgeEventActionType actionType, EntityId entityId,
                                                              JsonNode body, EdgeId sourceEdgeId) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        if (TenantId.SYS_TENANT_ID.equals(tenantId)) {
            PageDataIterable<TenantId> tenantIds = new PageDataIterable<>(link -> tenantService.findTenantsIds(link), 1024);
            for (TenantId tenantId1 : tenantIds) {
                futures.addAll(processActionForAllEdgesByTenantId(tenantId1, type, actionType, entityId, body, sourceEdgeId, null));
            }
        } else {
            futures = processActionForAllEdgesByTenantId(tenantId, type, actionType, entityId, null, sourceEdgeId, null);
        }
        return Futures.transform(Futures.allAsList(futures), voids -> null, dbCallbackExecutorService);
    }

    protected List<ListenableFuture<Void>> processActionForAllEdgesByTenantId(TenantId tenantId,
                                                                              EdgeEventType type,
                                                                              EdgeEventActionType actionType,
                                                                              EntityId entityId,
                                                                              JsonNode body,
                                                                              EdgeId sourceEdgeId,
                                                                              EntityGroupId entityGroupId) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        PageDataIterable<Edge> edges = new PageDataIterable<>(link -> edgeService.findEdgesByTenantId(tenantId, link), 1024);
        for (Edge edge : edges) {
            if (!edge.getId().equals(sourceEdgeId)) {
                futures.add(saveEdgeEvent(tenantId, edge.getId(), type, actionType, entityId, body, entityGroupId));
            }
        }
        return futures;
    }

    protected ListenableFuture<Void> handleUnsupportedMsgType(UpdateMsgType msgType) {
        String errMsg = String.format("Unsupported msg type %s", msgType);
        log.error(errMsg);
        return Futures.immediateFailedFuture(new RuntimeException(errMsg));
    }

    protected UpdateMsgType getUpdateMsgType(EdgeEventActionType actionType) {
        return switch (actionType) {
            case UPDATED, CREDENTIALS_UPDATED, UPDATED_COMMENT -> UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE;
            case ADDED, ADDED_TO_ENTITY_GROUP, ASSIGNED_TO_EDGE, RELATION_ADD_OR_UPDATE, ADDED_COMMENT ->
                    UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE;
            case DELETED, UNASSIGNED_FROM_EDGE, RELATION_DELETED, REMOVED_FROM_ENTITY_GROUP, CHANGE_OWNER, DELETED_COMMENT, ALARM_DELETE ->
                    UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE;
            case ALARM_ACK -> UpdateMsgType.ALARM_ACK_RPC_MESSAGE;
            case ALARM_CLEAR -> UpdateMsgType.ALARM_CLEAR_RPC_MESSAGE;
            default -> throw new RuntimeException("Unsupported actionType [" + actionType + "]");
        };
    }

    public ListenableFuture<Void> processEntityNotification(TenantId tenantId, TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg) {
        EdgeEventType type = EdgeEventType.valueOf(edgeNotificationMsg.getType());
        EdgeEventActionType actionType = EdgeEventActionType.valueOf(edgeNotificationMsg.getAction());
        EntityId entityId = EntityIdFactory.getByEdgeEventTypeAndUuid(type, new UUID(edgeNotificationMsg.getEntityIdMSB(), edgeNotificationMsg.getEntityIdLSB()));
        EdgeId originatorEdgeId = safeGetEdgeId(edgeNotificationMsg.getOriginatorEdgeIdMSB(), edgeNotificationMsg.getOriginatorEdgeIdLSB());
        if (type.isAllEdgesRelated()) {
            return processEntityNotificationForAllEdges(tenantId, type, actionType, entityId, originatorEdgeId);
        } else {
            JsonNode body = JacksonUtil.toJsonNode(edgeNotificationMsg.getBody());
            EdgeId edgeId = safeGetEdgeId(edgeNotificationMsg.getEdgeIdMSB(), edgeNotificationMsg.getEdgeIdLSB());
            EntityGroupId entityGroupId = constructEntityGroupId(tenantId, edgeNotificationMsg);
            switch (actionType) {
                case UPDATED:
                case CREDENTIALS_UPDATED:
                case ADDED_TO_ENTITY_GROUP:
                    if (edgeId != null) {
                        return saveEdgeEvent(tenantId, edgeId, type, actionType, entityId, body, entityGroupId);
                    } else {
                        return pushNotificationToAllRelatedEdges(tenantId, entityId, type, actionType, body, entityGroupId, originatorEdgeId);
                    }
                case DELETED:
                case REMOVED_FROM_ENTITY_GROUP:
                    if (edgeId != null) {
                        return saveEdgeEvent(tenantId, edgeId, type, actionType, entityId, body, entityGroupId);
                    } else {
                        return Futures.transform(Futures.allAsList(processActionForAllEdgesByTenantId(tenantId, type, actionType, entityId, body, originatorEdgeId, entityGroupId)),
                                voids -> null, dbCallbackExecutorService);
                    }
                case ASSIGNED_TO_EDGE:
                case UNASSIGNED_FROM_EDGE:
                    if (originatorEdgeId == null) {
                        ListenableFuture<Void> future = saveEdgeEvent(tenantId, edgeId, type, actionType, entityId, body);
                        return Futures.transformAsync(future, unused -> {
                            if (type.equals(EdgeEventType.RULE_CHAIN)) {
                                return updateDependentRuleChains(tenantId, new RuleChainId(entityId.getId()), edgeId);
                            } else {
                                return Futures.immediateFuture(null);
                            }
                        }, dbCallbackExecutorService);
                    } else {
                        return Futures.immediateFuture(null);
                    }
                case CHANGE_OWNER:
                    if (edgeId != null) {
                        return saveEdgeEvent(tenantId, edgeId, type, actionType, entityId, body);
                    } else {
                        // TODO: @voba - provide logic for customer
                        return Futures.transform(Futures.allAsList(processActionForAllEdgesByTenantId(
                                tenantId, type, actionType, entityId, body, originatorEdgeId, null)), voids -> null, dbCallbackExecutorService);
                    }
                default:
                    return Futures.immediateFuture(null);
            }
        }
    }

    public ListenableFuture<Void> processDeviceOtaNotification(TenantId tenantId, TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg) {
        EdgeEventType type = EdgeEventType.valueOf(edgeNotificationMsg.getType());
        EdgeEventActionType actionType = EdgeEventActionType.valueOf(edgeNotificationMsg.getAction());
        EntityId entityId = EntityIdFactory.getByEdgeEventTypeAndUuid(type, new UUID(edgeNotificationMsg.getEntityIdMSB(), edgeNotificationMsg.getEntityIdLSB()));
        JsonNode body = JacksonUtil.toJsonNode(edgeNotificationMsg.getBody());
        return switch (actionType) {
            case ADDED, UPDATED, DELETED -> pushNotificationToAllRelatedEdges(tenantId, entityId, type, actionType, body, null, null);
            default -> Futures.immediateFuture(null);
        };
    }

    protected EdgeId safeGetEdgeId(long edgeIdMSB, long edgeIdLSB) {
        if (edgeIdMSB != 0 && edgeIdLSB != 0) {
            return new EdgeId(new UUID(edgeIdMSB, edgeIdLSB));
        } else {
            return null;
        }
    }

    private EntityGroupId constructEntityGroupId(TenantId tenantId, TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg) {
        if (edgeNotificationMsg.getEntityGroupIdMSB() != 0 && edgeNotificationMsg.getEntityGroupIdLSB() != 0) {
            EntityGroupId entityGroupId = new EntityGroupId(new UUID(edgeNotificationMsg.getEntityGroupIdMSB(), edgeNotificationMsg.getEntityGroupIdLSB()));
            EntityGroup entityGroup = entityGroupService.findEntityGroupById(tenantId, entityGroupId);
            if (entityGroup == null) {
                return null;
            }
            if (entityGroup.isEdgeGroupAll()) {
                return null;
            } else {
                return entityGroupId;
            }
        } else {
            return null;
        }
    }

    private ListenableFuture<Void> pushNotificationToAllRelatedEdges(TenantId tenantId, EntityId entityId, EdgeEventType type,
                                                                     EdgeEventActionType actionType, JsonNode body,
                                                                     EntityGroupId entityGroupId, EdgeId sourceEdgeId) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        PageDataIterableByTenantIdEntityId<EdgeId> edgeIds =
                new PageDataIterableByTenantIdEntityId<>(edgeService::findRelatedEdgeIdsByEntityId, tenantId, entityId, DEFAULT_PAGE_SIZE);
        for (EdgeId relatedEdgeId : edgeIds) {
            if (!relatedEdgeId.equals(sourceEdgeId)) {
                futures.add(saveEdgeEvent(tenantId, relatedEdgeId, type, actionType, entityId, body, entityGroupId));
            }
        }
        return Futures.transform(Futures.allAsList(futures), voids -> null, dbCallbackExecutorService);
    }

    private ListenableFuture<Void> updateDependentRuleChains(TenantId tenantId, RuleChainId processingRuleChainId, EdgeId edgeId) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        PageDataIterable<RuleChain> ruleChains = new PageDataIterable<>(link -> ruleChainService.findRuleChainsByTenantIdAndEdgeId(tenantId, edgeId, link), 1024);
        for (RuleChain ruleChain : ruleChains) {
            List<RuleChainConnectionInfo> connectionInfos =
                    ruleChainService.loadRuleChainMetaData(ruleChain.getTenantId(), ruleChain.getId()).getRuleChainConnections();
            if (connectionInfos != null && !connectionInfos.isEmpty()) {
                for (RuleChainConnectionInfo connectionInfo : connectionInfos) {
                    if (connectionInfo.getTargetRuleChainId().equals(processingRuleChainId)) {
                        futures.add(saveEdgeEvent(tenantId,
                                edgeId,
                                EdgeEventType.RULE_CHAIN_METADATA,
                                EdgeEventActionType.UPDATED,
                                ruleChain.getId(),
                                null));
                    }
                }
            }
        }
        return Futures.transform(Futures.allAsList(futures), voids -> null, dbCallbackExecutorService);
    }

    private ListenableFuture<Void> processEntityNotificationForAllEdges(TenantId tenantId, EdgeEventType type, EdgeEventActionType actionType, EntityId entityId, EdgeId sourceEdgeId) {
        return switch (actionType) {
            case ADDED, UPDATED, DELETED, CREDENTIALS_UPDATED -> // used by USER entity
                    processActionForAllEdges(tenantId, type, actionType, entityId, null, sourceEdgeId);
            default -> Futures.immediateFuture(null);
        };
    }

    protected EntityId constructEntityId(String entityTypeStr, long entityIdMSB, long entityIdLSB) {
        EntityType entityType = EntityType.valueOf(entityTypeStr);
        return switch (entityType) {
            case DEVICE -> new DeviceId(new UUID(entityIdMSB, entityIdLSB));
            case ASSET -> new AssetId(new UUID(entityIdMSB, entityIdLSB));
            case ENTITY_VIEW -> new EntityViewId(new UUID(entityIdMSB, entityIdLSB));
            case DASHBOARD -> new DashboardId(new UUID(entityIdMSB, entityIdLSB));
            case TENANT -> TenantId.fromUUID(new UUID(entityIdMSB, entityIdLSB));
            case CUSTOMER -> new CustomerId(new UUID(entityIdMSB, entityIdLSB));
            case USER -> new UserId(new UUID(entityIdMSB, entityIdLSB));
            case EDGE -> new EdgeId(new UUID(entityIdMSB, entityIdLSB));
            case ENTITY_GROUP -> new EntityGroupId(new UUID(entityIdMSB, entityIdLSB));
            case CONVERTER -> new ConverterId(new UUID(entityIdMSB, entityIdLSB));
            case INTEGRATION -> new IntegrationId(new UUID(entityIdMSB, entityIdLSB));
            case SCHEDULER_EVENT -> new SchedulerEventId(new UUID(entityIdMSB, entityIdLSB));
            case BLOB_ENTITY -> new BlobEntityId(new UUID(entityIdMSB, entityIdLSB));
            case ROLE -> new RoleId(new UUID(entityIdMSB, entityIdLSB));
            default -> {
                log.warn("Unsupported entity type [{}] during construct of entity id. entityIdMSB [{}], entityIdLSB [{}]",
                        entityTypeStr, entityIdMSB, entityIdLSB);
                yield null;
            }
        };
    }

    protected UUID safeGetUUID(long mSB, long lSB) {
        return mSB != 0 && lSB != 0 ? new UUID(mSB, lSB) : null;
    }

    protected CustomerId safeGetCustomerId(long mSB, long lSB) {
        CustomerId customerId = null;
        UUID customerUUID = safeGetUUID(mSB, lSB);
        if (customerUUID != null) {
            customerId = new CustomerId(customerUUID);
        }
        return customerId;
    }

    protected boolean isEntityExists(TenantId tenantId, EntityId entityId) {
        return switch (entityId.getEntityType()) {
            case TENANT -> tenantService.findTenantById(tenantId) != null;
            case DEVICE -> deviceService.findDeviceById(tenantId, new DeviceId(entityId.getId())) != null;
            case ASSET -> assetService.findAssetById(tenantId, new AssetId(entityId.getId())) != null;
            case ENTITY_VIEW -> entityViewService.findEntityViewById(tenantId, new EntityViewId(entityId.getId())) != null;
            case CUSTOMER -> customerService.findCustomerById(tenantId, new CustomerId(entityId.getId())) != null;
            case USER -> userService.findUserById(tenantId, new UserId(entityId.getId())) != null;
            case DASHBOARD -> dashboardService.findDashboardById(tenantId, new DashboardId(entityId.getId())) != null;
            case EDGE -> edgeService.findEdgeById(tenantId, new EdgeId(entityId.getId())) != null;
            case ENTITY_GROUP ->
                    entityGroupService.findEntityGroupById(tenantId, new EntityGroupId(entityId.getId())) != null;
            default -> false;
        };
    }

    protected void createRelationFromEdge(TenantId tenantId, EdgeId edgeId, EntityId entityId) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(edgeId);
        relation.setTo(entityId);
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        relation.setType(EntityRelation.EDGE_TYPE);
        relationService.saveRelation(tenantId, relation);
    }

    protected TbMsgMetaData getEdgeActionTbMsgMetaData(Edge edge, CustomerId customerId) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("edgeId", edge.getId().toString());
        metaData.putValue("edgeName", edge.getName());
        if (customerId != null && !customerId.isNullUid()) {
            metaData.putValue("customerId", customerId.toString());
        }
        return metaData;
    }

    protected void pushEntityEventToRuleEngine(TenantId tenantId, EntityId entityId, CustomerId customerId,
                                               TbMsgType msgType, String msgData, TbMsgMetaData metaData) {
        TbMsg tbMsg = TbMsg.newMsg(msgType, entityId, customerId, metaData, TbMsgDataType.JSON, msgData);
        tbClusterService.pushMsgToRuleEngine(tenantId, entityId, tbMsg, new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                log.debug("[{}] Successfully send ENTITY_CREATED EVENT to rule engine [{}]", tenantId, msgData);
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to send ENTITY_CREATED EVENT to rule engine [{}]", tenantId, msgData, t);
            }
        });
    }

    protected void changeOwnerIfRequired(TenantId tenantId, CustomerId customerId, EntityId entityId) throws ThingsboardException {
        EntityId newOwnerId = getOwnerId(tenantId, customerId);
        EntityId currentOwnerId;
        switch (entityId.getEntityType()) {
            case DEVICE -> {
                Device device = deviceService.findDeviceById(tenantId, new DeviceId(entityId.getId()));
                currentOwnerId = device.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeDeviceOwner(tenantId, newOwnerId, device);
                }
            }
            case ASSET -> {
                Asset asset = assetService.findAssetById(tenantId, new AssetId(entityId.getId()));
                currentOwnerId = asset.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeAssetOwner(tenantId, newOwnerId, asset);
                }
            }
            case ENTITY_VIEW -> {
                EntityView entityView = entityViewService.findEntityViewById(tenantId, new EntityViewId(entityId.getId()));
                currentOwnerId = entityView.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeEntityViewOwner(tenantId, newOwnerId, entityView);
                }
            }
            case USER -> {
                User user = userService.findUserById(tenantId, new UserId(entityId.getId()));
                currentOwnerId = user.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeUserOwner(tenantId, newOwnerId, user);
                }
            }
            case DASHBOARD -> {
                Dashboard dashboard = dashboardService.findDashboardById(tenantId, new DashboardId(entityId.getId()));
                currentOwnerId = dashboard.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeDashboardOwner(tenantId, newOwnerId, dashboard);
                }
            }
            case CUSTOMER -> {
                Customer customer = customerService.findCustomerById(tenantId, new CustomerId(entityId.getId()));
                currentOwnerId = customer.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeCustomerOwner(tenantId, newOwnerId, customer);
                }
            }
            case EDGE -> {
                Edge edge = edgeService.findEdgeById(tenantId, new EdgeId(entityId.getId()));
                currentOwnerId = edge.getOwnerId();
                if (!newOwnerId.equals(currentOwnerId) && checkIfNewOwnerIsCustomerAndExists(tenantId, newOwnerId)) {
                    ownersCacheService.changeEdgeOwner(tenantId, newOwnerId, edge);
                }
            }
        }
    }

    private boolean checkIfNewOwnerIsCustomerAndExists(TenantId tenantId, EntityId newOwnerId) {
        if (EntityType.TENANT.equals(newOwnerId.getEntityType())) {
            return true;
        }
        if (EntityId.NULL_UUID.equals(newOwnerId.getId())) {
            return false;
        }
        Customer customerById = customerService.findCustomerById(tenantId, new CustomerId(newOwnerId.getId()));
        return customerById != null;
    }

    protected boolean isCustomerNotExists(TenantId tenantId, CustomerId customerId) {
        if (customerId == null) {
            return false;
        }
        if (EntityId.NULL_UUID.equals(customerId.getId())) {
            return false;
        }
        Customer customerById = customerService.findCustomerById(tenantId, customerId);
        return customerById == null;
    }

    private EntityId getOwnerId(TenantId tenantId, CustomerId customerId) {
        return customerId != null && !customerId.isNullUid() ? customerId : tenantId;
    }

    protected void safeAddEntityToGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId) {
        if (entityGroupId != null && !ModelConstants.NULL_UUID.equals(entityGroupId.getId())) {
            EntityGroup entityGroup = entityGroupService.findEntityGroupById(tenantId, entityGroupId);
            if (entityGroup != null) {
                entityGroupService.addEntityToEntityGroup(tenantId, entityGroupId, entityId);
            }
        }
    }

    protected ListenableFuture<Void> requestForAdditionalData(TenantId tenantId, EntityId entityId, Long queueStartTs) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        CloudEventType cloudEventType = CloudUtils.getCloudEventTypeByEntityType(entityId.getEntityType());
        log.info("Adding ATTRIBUTES_REQUEST/RELATION_REQUEST {} {}", entityId, cloudEventType);

        futures.add(cloudEventService.saveCloudEventAsync(tenantId, cloudEventType,
                EdgeEventActionType.ATTRIBUTES_REQUEST, entityId, null, null, queueStartTs));
        futures.add(cloudEventService.saveCloudEventAsync(tenantId, cloudEventType,
                EdgeEventActionType.RELATION_REQUEST, entityId, null, null, queueStartTs));
        if (CloudEventType.DEVICE.equals(cloudEventType) || CloudEventType.ASSET.equals(cloudEventType)) {
            futures.add(cloudEventService.saveCloudEventAsync(tenantId, cloudEventType,
                    EdgeEventActionType.ENTITY_VIEW_REQUEST, entityId, null, null, queueStartTs));
        }
        return Futures.transform(Futures.allAsList(futures), voids -> null, dbCallbackExecutorService);
    }

    protected EntityId safeGetOwnerId(TenantId tenantId, String ownerEntityTypeStr, long mSB, long lSB) {
        if (StringUtils.isEmpty(ownerEntityTypeStr)) {
            return tenantId;
        }
        EntityType ownerEntityType = EntityType.valueOf(ownerEntityTypeStr);
        if (EntityType.CUSTOMER.equals(ownerEntityType)) {
            return new CustomerId(new UUID(mSB, lSB));
        } else {
            return tenantId;
        }
    }

    protected AssetProfile checkIfAssetProfileDefaultFieldsAssignedToEdge(TenantId tenantId, EdgeId edgeId, AssetProfile assetProfile, EdgeVersion edgeVersion) {
        if (EdgeVersion.V_3_3_0.equals(edgeVersion) || EdgeVersion.V_3_3_3.equals(edgeVersion) || EdgeVersion.V_3_4_0.equals(edgeVersion)) {
            if (assetProfile.getDefaultDashboardId() != null && isEntityNotAssignedToEdge(tenantId, assetProfile.getDefaultDashboardId(), edgeId)) {
                assetProfile.setDefaultDashboardId(null);
            }
            if (assetProfile.getDefaultEdgeRuleChainId() != null && isEntityNotAssignedToEdge(tenantId, assetProfile.getDefaultEdgeRuleChainId(), edgeId)) {
                assetProfile.setDefaultEdgeRuleChainId(null);
            }
        }
        return assetProfile;
    }

    protected DeviceProfile checkIfDeviceProfileDefaultFieldsAssignedToEdge(TenantId tenantId, EdgeId edgeId, DeviceProfile deviceProfile, EdgeVersion edgeVersion) {
        if (EdgeVersion.V_3_3_0.equals(edgeVersion) || EdgeVersion.V_3_3_3.equals(edgeVersion) || EdgeVersion.V_3_4_0.equals(edgeVersion)) {
            if (deviceProfile.getDefaultDashboardId() != null && isEntityNotAssignedToEdge(tenantId, deviceProfile.getDefaultDashboardId(), edgeId)) {
                deviceProfile.setDefaultDashboardId(null);
            }
            if (deviceProfile.getDefaultEdgeRuleChainId() != null && isEntityNotAssignedToEdge(tenantId, deviceProfile.getDefaultEdgeRuleChainId(), edgeId)) {
                deviceProfile.setDefaultEdgeRuleChainId(null);
            }
        }
        return deviceProfile;
    }

    private boolean isEntityNotAssignedToEdge(TenantId tenantId, EntityId entityId, EdgeId edgeId) {
        PageDataIterableByTenantIdEntityId<EdgeId> edgeIds =
                new PageDataIterableByTenantIdEntityId<>(edgeService::findRelatedEdgeIdsByEntityId, tenantId, entityId, DEFAULT_PAGE_SIZE);
        for (EdgeId edgeId1 : edgeIds) {
            if (edgeId1.equals(edgeId)) {
                return false;
            }
        }
        return true;
    }

    protected ListenableFuture<Void> removeEntityIfInSingleAllGroup(TenantId tenantId, EntityId entityId, Runnable provider) {
        ListenableFuture<List<EntityGroupId>> future =
                entityGroupService.findEntityGroupsForEntityAsync(tenantId, entityId);
        return Futures.transform(future, input -> {
            if (input.size() == 1) {
                provider.run();
            }
            return null;
        }, dbCallbackExecutorService);
    }

}
