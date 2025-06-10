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
package org.thingsboard.server.service.entitiy;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.JobManager;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.ApiUsageState;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.cf.CalculatedField;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.job.Job;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.notification.NotificationRequest;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainType;
import org.thingsboard.server.common.data.secret.Secret;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.edge.EdgeEventUpdateMsg;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.rule.engine.DeviceCredentialsUpdateNotificationMsg;
import org.thingsboard.server.dao.edge.EdgeSynchronizationManager;
import org.thingsboard.server.dao.eventsourcing.ActionEntityEvent;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.secret.SecretService;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.queue.TbQueueCallback;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntityStateSourcingListener {

    private final TenantService tenantService;
    private final TbClusterService tbClusterService;
    private final EdgeSynchronizationManager edgeSynchronizationManager;
    private final JobManager jobManager;
    private final SecretService secretService;

    @PostConstruct
    public void init() {
        log.debug("EntityStateSourcingListener initiated");
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(SaveEntityEvent<?> event) {
        if (Boolean.FALSE.equals(event.getBroadcastEvent())) {
            log.trace("Ignoring event {}", event);
            return;
        }

        TenantId tenantId = event.getTenantId();
        EntityId entityId = event.getEntityId();
        if (entityId == null) {
            return;
        }
        EntityType entityType = entityId.getEntityType();
        log.debug("[{}][{}][{}] Handling entity save event: {}", tenantId, entityType, entityId, event);
        boolean isCreated = event.getCreated() != null && event.getCreated();
        ComponentLifecycleEvent lifecycleEvent = isCreated ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED;

        switch (entityType) {
            case ASSET -> {
                onAssetUpdate(event.getEntity(), event.getOldEntity());
            }
            case ASSET_PROFILE, ENTITY_VIEW, NOTIFICATION_RULE -> {
                tbClusterService.broadcastEntityStateChangeEvent(tenantId, entityId, lifecycleEvent);
            }
            case RULE_CHAIN -> {
                RuleChain ruleChain = (RuleChain) event.getEntity();
                if (RuleChainType.CORE.equals(ruleChain.getType())) {
                    tbClusterService.broadcastEntityStateChangeEvent(ruleChain.getTenantId(), ruleChain.getId(), lifecycleEvent);
                }
            }
            case TENANT -> {
                Tenant tenant = (Tenant) event.getEntity();
                onTenantUpdate(tenant, lifecycleEvent);
            }
            case TENANT_PROFILE -> {
                TenantProfile tenantProfile = (TenantProfile) event.getEntity();
                onTenantProfileUpdate(tenantProfile, lifecycleEvent);
            }
            case DEVICE -> {
                onDeviceUpdate(event.getEntity(), event.getOldEntity());
            }
            case DEVICE_PROFILE -> {
                DeviceProfile deviceProfile = (DeviceProfile) event.getEntity();
                onDeviceProfileUpdate(deviceProfile, event.getOldEntity(), isCreated);
            }
            case EDGE -> {
                onEdgeEvent(tenantId, entityId, event.getEntity(), lifecycleEvent);
            }
            case TB_RESOURCE -> {
                TbResource tbResource = (TbResource) event.getEntity();
                tbClusterService.onResourceChange(tbResource, null);
            }
            case API_USAGE_STATE -> {
                ApiUsageState apiUsageState = (ApiUsageState) event.getEntity();
                tbClusterService.onApiStateChange(apiUsageState, null);
            }
            case INTEGRATION -> {
                Integration integration = (Integration) event.getEntity();
                if (!integration.isEdgeTemplate()) {
                    tbClusterService.broadcastEntityStateChangeEvent(tenantId, integration.getId(), lifecycleEvent);
                }
            }
            case CONVERTER -> {
                Converter converter = (Converter) event.getEntity();
                if (!converter.isEdgeTemplate()) {
                    tbClusterService.broadcastEntityStateChangeEvent(tenantId, converter.getId(), lifecycleEvent);
                }
            }
            case CUSTOMER -> {
                if (!isCreated) {
                    tbClusterService.onCustomerUpdated((Customer) event.getEntity(), (Customer) event.getOldEntity());
                }
            }
            case USER -> {
                if (!isCreated) {
                    tbClusterService.onUserUpdated((User) event.getEntity(), (User) event.getOldEntity());
                }
            }
            case CALCULATED_FIELD -> {
                onCalculatedFieldUpdate(event.getEntity(), event.getOldEntity());
            }
            case JOB -> {
                onJobUpdate((Job) event.getEntity());
            }
            case SECRET -> {
                if (isCreated) {
                    break;
                }
                Secret secret = (Secret) event.getEntity();
                var entityInfos = secretService.findEntitiesBySecret(tenantId, secret);
                entityInfos.values().stream().flatMap(List::stream).forEach(entityInfo ->
                        tbClusterService.broadcastEntityStateChangeEvent(tenantId, entityInfo.getId(), lifecycleEvent));
            }
            default -> {}
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(DeleteEntityEvent<?> event) {
        TenantId tenantId = event.getTenantId();
        EntityId entityId = event.getEntityId();
        if (entityId == null) {
            return;
        }
        EntityType entityType = entityId.getEntityType();
        if (!tenantId.isSysTenantId() && entityType != EntityType.TENANT && !tenantService.tenantExists(tenantId)) {
            log.debug("[{}] Ignoring DeleteEntityEvent because tenant does not exist: {}", tenantId, event);
            return;
        }
        log.debug("[{}][{}][{}] Handling entity deletion event: {}", tenantId, entityType, entityId, event);

        switch (entityType) {
            case ASSET -> {
                Asset asset = (Asset) event.getEntity();
                tbClusterService.onAssetDeleted(tenantId, asset, null);
            }
            case ASSET_PROFILE, ENTITY_VIEW, CUSTOMER, EDGE, NOTIFICATION_RULE -> {
                tbClusterService.broadcastEntityStateChangeEvent(tenantId, entityId, ComponentLifecycleEvent.DELETED);
            }
            case NOTIFICATION_REQUEST -> {
                NotificationRequest request = (NotificationRequest) event.getEntity();
                if (request.isScheduled()) {
                    tbClusterService.broadcastEntityStateChangeEvent(tenantId, entityId, ComponentLifecycleEvent.DELETED);
                }
            }
            case RULE_CHAIN -> {
                RuleChain ruleChain = (RuleChain) event.getEntity();
                if (RuleChainType.CORE.equals(ruleChain.getType())) {
                    Set<RuleChainId> referencingRuleChainIds = JacksonUtil.fromString(event.getBody(), new TypeReference<>() {
                    });
                    if (referencingRuleChainIds != null) {
                        referencingRuleChainIds.forEach(referencingRuleChainId ->
                                tbClusterService.broadcastEntityStateChangeEvent(tenantId, referencingRuleChainId, ComponentLifecycleEvent.UPDATED));
                    }
                    tbClusterService.broadcastEntityStateChangeEvent(tenantId, ruleChain.getId(), ComponentLifecycleEvent.DELETED);
                }
            }
            case TENANT -> {
                Tenant tenant = (Tenant) event.getEntity();
                onTenantDeleted(tenant);
            }
            case TENANT_PROFILE -> {
                TenantProfile tenantProfile = (TenantProfile) event.getEntity();
                tbClusterService.onTenantProfileDelete(tenantProfile, TbQueueCallback.EMPTY);
            }
            case DEVICE -> {
                Device device = (Device) event.getEntity();
                tbClusterService.onDeviceDeleted(tenantId, device, TbQueueCallback.EMPTY);
            }
            case DEVICE_PROFILE -> {
                DeviceProfile deviceProfile = (DeviceProfile) event.getEntity();
                onDeviceProfileDelete(event.getTenantId(), event.getEntityId(), deviceProfile);
            }
            case TB_RESOURCE -> {
                TbResourceInfo tbResource = (TbResourceInfo) event.getEntity();
                tbClusterService.onResourceDeleted(tbResource, TbQueueCallback.EMPTY);
            }
            case CALCULATED_FIELD -> {
                CalculatedField calculatedField = (CalculatedField) event.getEntity();
                tbClusterService.onCalculatedFieldDeleted(calculatedField, TbQueueCallback.EMPTY);
            }
            case INTEGRATION -> {
                Integration integration = (Integration) event.getEntity();
                if (!integration.isEdgeTemplate()) {
                    tbClusterService.broadcastEntityStateChangeEvent(integration.getTenantId(), integration.getId(), ComponentLifecycleEvent.DELETED);
                }
            }
            case CONVERTER -> {
                Converter converter = (Converter) event.getEntity();
                if (!converter.isEdgeTemplate()) {
                    tbClusterService.broadcastEntityStateChangeEvent(tenantId, converter.getId(), ComponentLifecycleEvent.DELETED);
                }
            }
            default -> {}
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(ActionEntityEvent<?> event) {
        log.trace("[{}] ActionEntityEvent called: {}", event.getTenantId(), event);
        if (ActionType.CREDENTIALS_UPDATED.equals(event.getActionType()) &&
                EntityType.DEVICE.equals(event.getEntityId().getEntityType())
                && event.getEntity() instanceof DeviceCredentials) {
            tbClusterService.pushMsgToCore(new DeviceCredentialsUpdateNotificationMsg(event.getTenantId(),
                    (DeviceId) event.getEntityId(), (DeviceCredentials) event.getEntity()), null);
        } else if (ActionType.ASSIGNED_TO_TENANT.equals(event.getActionType()) && event.getEntity() instanceof Device device) {
            Tenant tenant = JacksonUtil.fromString(event.getBody(), Tenant.class);
            if (tenant != null) {
                tbClusterService.onDeviceAssignedToTenant(tenant.getId(), device);
            }
            pushAssignedFromNotification(tenant, event.getTenantId(), device);
        }
    }

    private void onTenantUpdate(Tenant tenant, ComponentLifecycleEvent lifecycleEvent) {
        tbClusterService.onTenantChange(tenant, null);
        tbClusterService.broadcastEntityStateChangeEvent(tenant.getId(), tenant.getId(), lifecycleEvent);
    }

    private void onTenantDeleted(Tenant tenant) {
        tbClusterService.onTenantDelete(tenant, null);
        tbClusterService.broadcastEntityStateChangeEvent(tenant.getId(), tenant.getId(), ComponentLifecycleEvent.DELETED);
    }

    private void onTenantProfileUpdate(TenantProfile tenantProfile, ComponentLifecycleEvent lifecycleEvent) {
        tbClusterService.onTenantProfileChange(tenantProfile, null);
        tbClusterService.broadcastEntityStateChangeEvent(TenantId.SYS_TENANT_ID, tenantProfile.getId(), lifecycleEvent);
    }

    private void onDeviceProfileUpdate(DeviceProfile deviceProfile, Object oldEntity, boolean isCreated) {
        DeviceProfile oldDeviceProfile = null;
        if (!isCreated) {
            oldDeviceProfile = getOldDeviceProfile(oldEntity);
        }
        tbClusterService.onDeviceProfileChange(deviceProfile, oldDeviceProfile, null);
    }

    private DeviceProfile getOldDeviceProfile(Object oldEntity) {
        return oldEntity instanceof DeviceProfile ? (DeviceProfile) oldEntity : null;
    }

    private void onDeviceProfileDelete(TenantId tenantId, EntityId entityId, DeviceProfile deviceProfile) {
        tbClusterService.onDeviceProfileDelete(deviceProfile, null);
        tbClusterService.broadcastEntityStateChangeEvent(tenantId, entityId, ComponentLifecycleEvent.DELETED);
    }

    private void onDeviceUpdate(Object entity, Object oldEntity) {
        Device device = (Device) entity;
        Device oldDevice = null;
        if (oldEntity instanceof Device) {
            oldDevice = (Device) oldEntity;
        }
        tbClusterService.onDeviceUpdated(device, oldDevice);
    }

    private void onAssetUpdate(Object entity, Object oldEntity) {
        Asset asset = (Asset) entity;
        Asset oldAsset = null;
        if (oldEntity instanceof Asset) {
            oldAsset = (Asset) oldEntity;
        }
        tbClusterService.onAssetUpdated(asset, oldAsset);
    }

    private void onEdgeEvent(TenantId tenantId, EntityId entityId, Object entity, ComponentLifecycleEvent lifecycleEvent) {
        if (entity instanceof Edge) {
            if (entityId.equals(edgeSynchronizationManager.getEdgeId().get())) {
                return;
            }
            tbClusterService.onEdgeStateChangeEvent(new ComponentLifecycleMsg(tenantId, entityId, lifecycleEvent));
        } else if (entity instanceof EdgeEvent edgeEvent) {
            tbClusterService.onEdgeEventUpdate(new EdgeEventUpdateMsg(tenantId, edgeEvent.getEdgeId()));
        }
    }

    private void onCalculatedFieldUpdate(Object entity, Object oldEntity) {
        CalculatedField calculatedField = (CalculatedField) entity;
        CalculatedField oldCalculatedField = null;
        if (oldEntity instanceof CalculatedField) {
            oldCalculatedField = (CalculatedField) oldEntity;
        }
        tbClusterService.onCalculatedFieldUpdated(calculatedField, oldCalculatedField, TbQueueCallback.EMPTY);
    }

    private void onJobUpdate(Job job) {
        jobManager.onJobUpdate(job);

        ComponentLifecycleEvent event;
        if (job.getResult().getCancellationTs() > 0) {
            event = ComponentLifecycleEvent.STOPPED;
        } else if (job.getResult().getGeneralError() != null) {
            event = ComponentLifecycleEvent.FAILED;
        } else {
            return;
        }
        ComponentLifecycleMsg msg = ComponentLifecycleMsg.builder()
                .tenantId(job.getTenantId())
                .entityId(job.getId())
                .event(event)
                .info(JacksonUtil.newObjectNode()
                        .put("tasksKey", job.getConfiguration().getTasksKey()))
                .build();
        // task processors will add this job to the list of discarded
        tbClusterService.broadcast(msg);
    }

    private void pushAssignedFromNotification(Tenant currentTenant, TenantId newTenantId, Device assignedDevice) {
        String data = JacksonUtil.toString(JacksonUtil.valueToTree(assignedDevice));
        if (data != null) {
            TbMsg tbMsg = TbMsg.newMsg()
                    .type(TbMsgType.ENTITY_ASSIGNED_FROM_TENANT)
                    .originator(assignedDevice.getId())
                    .customerId(assignedDevice.getCustomerId())
                    .copyMetaData(getMetaDataForAssignedFrom(currentTenant))
                    .dataType(TbMsgDataType.JSON)
                    .data(data)
                    .build();
            tbClusterService.pushMsgToRuleEngine(newTenantId, assignedDevice.getId(), tbMsg, null);
        }
    }

    private TbMsgMetaData getMetaDataForAssignedFrom(Tenant tenant) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("assignedFromTenantId", tenant.getId().getId().toString());
        metaData.putValue("assignedFromTenantName", tenant.getName());
        return metaData;
    }

}
