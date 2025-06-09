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
package org.thingsboard.server.service.cloud;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeSettings;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.cloud.CloudEventService;
import org.thingsboard.server.dao.cloud.EdgeSettingsService;
import org.thingsboard.server.gen.edge.v1.AdminSettingsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AlarmCommentUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CalculatedFieldUpdateMsg;
import org.thingsboard.server.gen.edge.v1.ConverterUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsRequestMsg;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceGroupOtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.EntityDataProto;
import org.thingsboard.server.gen.edge.v1.EntityGroupUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.v1.GroupPermissionProto;
import org.thingsboard.server.gen.edge.v1.IntegrationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.NotificationRuleUpdateMsg;
import org.thingsboard.server.gen.edge.v1.NotificationTargetUpdateMsg;
import org.thingsboard.server.gen.edge.v1.NotificationTemplateUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OAuth2ClientUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OAuth2DomainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.QueueUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.ResourceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RoleProto;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.SchedulerEventUpdateMsg;
import org.thingsboard.server.gen.edge.v1.TenantProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.TenantUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UserUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WidgetTypeUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WidgetsBundleUpdateMsg;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.cloud.rpc.processor.AdminSettingsCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.AlarmCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.AlarmCommentCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.AssetCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.AssetProfileCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.CalculatedFieldCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.ConverterCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.CustomMenuCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.CustomTranslationCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.CustomerCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.DashboardCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.DeviceCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.DeviceProfileCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.EdgeCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.EntityGroupCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.EntityViewCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.GroupPermissionCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.IntegrationCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.NotificationCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.OAuth2CloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.OtaPackageCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.QueueCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.RelationCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.ResourceCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.RoleCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.RuleChainCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.SchedulerEventCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.TelemetryCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.TenantCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.TenantProfileCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.UserCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.WhiteLabelingCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.WidgetBundleCloudProcessor;
import org.thingsboard.server.service.cloud.rpc.processor.WidgetTypeCloudProcessor;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@TbCoreComponent
public class DefaultDownlinkMessageService implements DownlinkMessageService {

    private final Lock sequenceDependencyLock = new ReentrantLock();

    @Autowired
    private CloudEventService cloudEventService;

    @Autowired
    private EdgeSettingsService edgeSettingsService;

    @Autowired
    private EdgeCloudProcessor edgeCloudProcessor;

    @Autowired
    private RuleChainCloudProcessor ruleChainProcessor;

    @Autowired
    private TelemetryCloudProcessor telemetryProcessor;

    @Autowired
    private DeviceCloudProcessor deviceProcessor;

    @Autowired
    private DeviceProfileCloudProcessor deviceProfileProcessor;

    @Autowired
    private AssetProfileCloudProcessor assetProfileProcessor;

    @Autowired
    private AssetCloudProcessor assetProcessor;

    @Autowired
    private EntityViewCloudProcessor entityViewProcessor;

    @Autowired
    private RelationCloudProcessor relationProcessor;

    @Autowired
    private DashboardCloudProcessor dashboardProcessor;

    @Autowired
    private CustomerCloudProcessor customerProcessor;

    @Autowired
    private AlarmCloudProcessor alarmProcessor;

    @Autowired
    private AlarmCommentCloudProcessor alarmCommentProcessor;

    @Autowired
    private UserCloudProcessor userProcessor;

    @Autowired
    private EntityGroupCloudProcessor entityGroupProcessor;

    @Autowired
    private SchedulerEventCloudProcessor schedulerEventProcessor;

    @Autowired
    private RoleCloudProcessor roleProcessor;

    @Autowired
    private GroupPermissionCloudProcessor groupPermissionProcessor;

    @Autowired
    private CustomTranslationCloudProcessor customTranslationProcessor;

    @Autowired
    private CustomMenuCloudProcessor customMenuProcessor;

    @Autowired
    private WhiteLabelingCloudProcessor whiteLabelingProcessor;

    @Autowired
    private WidgetBundleCloudProcessor widgetsBundleProcessor;

    @Autowired
    private WidgetTypeCloudProcessor widgetTypeProcessor;

    @Autowired
    private AdminSettingsCloudProcessor adminSettingsProcessor;

    @Autowired
    private ConverterCloudProcessor converterProcessor;

    @Autowired
    private OtaPackageCloudProcessor otaPackageProcessor;

    @Autowired
    private QueueCloudProcessor queueCloudProcessor;

    @Autowired
    private IntegrationCloudProcessor integrationProcessor;

    @Autowired
    private TenantCloudProcessor tenantCloudProcessor;

    @Autowired
    private TenantProfileCloudProcessor tenantProfileCloudProcessor;

    @Autowired
    private ResourceCloudProcessor tbResourceCloudProcessor;

    @Autowired
    private NotificationCloudProcessor notificationCloudProcessor;

    @Autowired
    private OAuth2CloudProcessor oAuth2CloudProcessor;

    @Autowired
    private CalculatedFieldCloudProcessor calculatedFieldCloudProcessor;

    @Autowired
    private DbCallbackExecutorService dbCallbackExecutorService;

    public ListenableFuture<List<Void>> processDownlinkMsg(TenantId tenantId,
                                                           CustomerId edgeCustomerId,
                                                           DownlinkMsg downlinkMsg,
                                                           EdgeSettings currentEdgeSettings) {
        List<ListenableFuture<Void>> result = new ArrayList<>();
        try {
            log.debug("[{}] Starting process DownlinkMsg. downlinkMsgId [{}],",
                    tenantId, downlinkMsg.getDownlinkMsgId());
            log.trace("downlink msg body [{}]", StringUtils.truncate(downlinkMsg.toString(), 10000));
            if (downlinkMsg.hasSyncCompletedMsg()) {
                result.add(updateSyncRequiredState(tenantId, edgeCustomerId, currentEdgeSettings));
            }
            if (downlinkMsg.hasEdgeConfiguration()) {
                result.add(edgeCloudProcessor.processEdgeConfigurationMsgFromCloud(tenantId, downlinkMsg.getEdgeConfiguration()));
            }
            if (downlinkMsg.getEntityDataCount() > 0) {
                for (EntityDataProto entityData : downlinkMsg.getEntityDataList()) {
                    result.addAll(telemetryProcessor.processTelemetryMsg(tenantId, entityData));
                }
            }
            if (downlinkMsg.getDeviceRpcCallMsgCount() > 0) {
                for (DeviceRpcCallMsg deviceRpcRequestMsg : downlinkMsg.getDeviceRpcCallMsgList()) {
                    result.add(deviceProcessor.processDeviceRpcCallFromCloud(tenantId, deviceRpcRequestMsg));
                }
            }
            if (downlinkMsg.getDeviceCredentialsRequestMsgCount() > 0) {
                for (DeviceCredentialsRequestMsg deviceCredentialsRequestMsg : downlinkMsg.getDeviceCredentialsRequestMsgList()) {
                    result.add(processDeviceCredentialsRequestMsg(tenantId, deviceCredentialsRequestMsg));
                }
            }
            if (downlinkMsg.getDeviceProfileUpdateMsgCount() > 0) {
                for (DeviceProfileUpdateMsg deviceProfileUpdateMsg : downlinkMsg.getDeviceProfileUpdateMsgList()) {
                    result.add(deviceProfileProcessor.processDeviceProfileMsgFromCloud(tenantId, deviceProfileUpdateMsg));
                }
            }
            if (downlinkMsg.getDeviceUpdateMsgCount() > 0) {
                for (DeviceUpdateMsg deviceUpdateMsg : downlinkMsg.getDeviceUpdateMsgList()) {
                    ListenableFuture<Void> future = deviceProcessor.processDeviceMsgFromCloud(tenantId, deviceUpdateMsg);
                    future.get();
                    result.add(future);
                }
            }
            if (downlinkMsg.getDeviceCredentialsUpdateMsgCount() > 0) {
                for (DeviceCredentialsUpdateMsg deviceCredentialsUpdateMsg : downlinkMsg.getDeviceCredentialsUpdateMsgList()) {
                    result.add(deviceProcessor.processDeviceCredentialsMsgFromCloud(tenantId, deviceCredentialsUpdateMsg));
                }
            }
            if (downlinkMsg.getAssetProfileUpdateMsgCount() > 0) {
                for (AssetProfileUpdateMsg assetProfileUpdateMsg : downlinkMsg.getAssetProfileUpdateMsgList()) {
                    result.add(assetProfileProcessor.processAssetProfileMsgFromCloud(tenantId, assetProfileUpdateMsg));
                }
            }
            if (downlinkMsg.getAssetUpdateMsgCount() > 0) {
                for (AssetUpdateMsg assetUpdateMsg : downlinkMsg.getAssetUpdateMsgList()) {
                    ListenableFuture<Void> future = assetProcessor.processAssetMsgFromCloud(tenantId, assetUpdateMsg);
                    future.get();
                    result.add(future);
                }
            }
            if (downlinkMsg.getEntityViewUpdateMsgCount() > 0) {
                for (EntityViewUpdateMsg entityViewUpdateMsg : downlinkMsg.getEntityViewUpdateMsgList()) {
                    ListenableFuture<Void> future = entityViewProcessor.processEntityViewMsgFromCloud(tenantId, entityViewUpdateMsg);
                    future.get();
                    result.add(future);
                }
            }
            if (downlinkMsg.getRuleChainUpdateMsgCount() > 0) {
                for (RuleChainUpdateMsg ruleChainUpdateMsg : downlinkMsg.getRuleChainUpdateMsgList()) {
                    result.add(ruleChainProcessor.processRuleChainMsgFromCloud(tenantId, ruleChainUpdateMsg));
                }
            }
            if (downlinkMsg.getRuleChainMetadataUpdateMsgCount() > 0) {
                for (RuleChainMetadataUpdateMsg ruleChainMetadataUpdateMsg : downlinkMsg.getRuleChainMetadataUpdateMsgList()) {
                    result.add(ruleChainProcessor.processRuleChainMetadataMsgFromCloud(tenantId, ruleChainMetadataUpdateMsg));
                }
            }
            if (downlinkMsg.getDashboardUpdateMsgCount() > 0) {
                for (DashboardUpdateMsg dashboardUpdateMsg : downlinkMsg.getDashboardUpdateMsgList()) {
                    ListenableFuture<Void> future = dashboardProcessor.processDashboardMsgFromCloud(tenantId, dashboardUpdateMsg);
                    future.get();
                    result.add(future);
                }
            }
            if (downlinkMsg.getAlarmUpdateMsgCount() > 0) {
                for (AlarmUpdateMsg alarmUpdateMsg : downlinkMsg.getAlarmUpdateMsgList()) {
                    result.add(alarmProcessor.processAlarmMsgFromCloud(tenantId, alarmUpdateMsg));
                }
            }
            if (downlinkMsg.getAlarmCommentUpdateMsgCount() > 0) {
                for (AlarmCommentUpdateMsg alarmCommentUpdateMsg : downlinkMsg.getAlarmCommentUpdateMsgList()) {
                    result.add(alarmCommentProcessor.processAlarmCommentMsgFromCloud(tenantId, alarmCommentUpdateMsg));
                }
            }
            if (downlinkMsg.getCustomerUpdateMsgCount() > 0) {
                for (CustomerUpdateMsg customerUpdateMsg : downlinkMsg.getCustomerUpdateMsgList()) {
                    sequenceDependencyLock.lock();
                    try {
                        result.add(customerProcessor.processCustomerMsgFromCloud(tenantId, customerUpdateMsg));
                    } finally {
                        sequenceDependencyLock.unlock();
                    }
                }
            }
            if (downlinkMsg.getRelationUpdateMsgCount() > 0) {
                for (RelationUpdateMsg relationUpdateMsg : downlinkMsg.getRelationUpdateMsgList()) {
                    result.add(relationProcessor.processRelationMsgFromCloud(tenantId, relationUpdateMsg));
                }
            }
            if (downlinkMsg.getWidgetsBundleUpdateMsgCount() > 0) {
                for (WidgetsBundleUpdateMsg widgetsBundleUpdateMsg : downlinkMsg.getWidgetsBundleUpdateMsgList()) {
                    result.add(widgetsBundleProcessor.processWidgetsBundleMsgFromCloud(tenantId, widgetsBundleUpdateMsg));
                }
            }
            if (downlinkMsg.getWidgetTypeUpdateMsgCount() > 0) {
                for (WidgetTypeUpdateMsg widgetTypeUpdateMsg : downlinkMsg.getWidgetTypeUpdateMsgList()) {
                    result.add(widgetTypeProcessor.processWidgetTypeMsgFromCloud(tenantId, widgetTypeUpdateMsg));
                }
            }
            if (downlinkMsg.getUserUpdateMsgCount() > 0) {
                for (UserUpdateMsg userUpdateMsg : downlinkMsg.getUserUpdateMsgList()) {
                    sequenceDependencyLock.lock();
                    try {
                        result.add(userProcessor.processUserMsgFromCloud(tenantId, userUpdateMsg));
                    } finally {
                        sequenceDependencyLock.unlock();
                    }
                }
            }
            if (downlinkMsg.getUserCredentialsUpdateMsgCount() > 0) {
                for (UserCredentialsUpdateMsg userCredentialsUpdateMsg : downlinkMsg.getUserCredentialsUpdateMsgList()) {
                    result.add(userProcessor.processUserCredentialsMsgFromCloud(tenantId, userCredentialsUpdateMsg));
                }
            }
            if (downlinkMsg.getEntityGroupUpdateMsgCount() > 0) {
                for (EntityGroupUpdateMsg entityGroupUpdateMsg : downlinkMsg.getEntityGroupUpdateMsgList()) {
                    result.add(entityGroupProcessor.processEntityGroupMsgFromCloud(tenantId, entityGroupUpdateMsg));
                }
            }
            if (downlinkMsg.hasCustomTranslationUpdateMsg()) {
                result.add(customTranslationProcessor.processCustomTranslationMsgFromCloud(tenantId, downlinkMsg.getCustomTranslationUpdateMsg()));
            }
            if (downlinkMsg.hasCustomMenuProto()) {
                result.add(customMenuProcessor.processCustomMenuMsgFromCloud(tenantId, downlinkMsg.getCustomMenuProto()));
            }
            if (downlinkMsg.hasWhiteLabelingProto()) {
                result.add(whiteLabelingProcessor.processWhiteLabelingMsgFromCloud(tenantId, downlinkMsg.getWhiteLabelingProto()));
            }
            if (downlinkMsg.getSchedulerEventUpdateMsgCount() > 0) {
                for (SchedulerEventUpdateMsg schedulerEventUpdateMsg : downlinkMsg.getSchedulerEventUpdateMsgList()) {
                    result.add(schedulerEventProcessor.processScheduleEventFromCloud(tenantId, schedulerEventUpdateMsg));
                }
            }
            if (downlinkMsg.getAdminSettingsUpdateMsgCount() > 0) {
                for (AdminSettingsUpdateMsg adminSettingsUpdateMsg : downlinkMsg.getAdminSettingsUpdateMsgList()) {
                    result.add(adminSettingsProcessor.processAdminSettingsMsgFromCloud(tenantId, adminSettingsUpdateMsg));
                }
            }
            if (downlinkMsg.getOtaPackageUpdateMsgCount() > 0) {
                for (OtaPackageUpdateMsg otaPackageUpdateMsg : downlinkMsg.getOtaPackageUpdateMsgList()) {
                    result.add(otaPackageProcessor.processOtaPackageMsgFromCloud(tenantId, otaPackageUpdateMsg));
                }
            }
            if (downlinkMsg.getQueueUpdateMsgCount() > 0) {
                for (QueueUpdateMsg queueUpdateMsg : downlinkMsg.getQueueUpdateMsgList()) {
                    result.add(queueCloudProcessor.processQueueMsgFromCloud(tenantId, queueUpdateMsg));
                }
            }
            if (downlinkMsg.getRoleMsgCount() > 0) {
                for (RoleProto roleProto : downlinkMsg.getRoleMsgList()) {
                    result.add(roleProcessor.processRoleMsgFromCloud(tenantId, roleProto));
                }
            }
            if (downlinkMsg.getGroupPermissionMsgCount() > 0) {
                for (GroupPermissionProto groupPermissionProto : downlinkMsg.getGroupPermissionMsgList()) {
                    result.add(groupPermissionProcessor.processGroupPermissionMsgFromCloud(tenantId, groupPermissionProto));
                }
            }
            if (downlinkMsg.getConverterMsgCount() > 0) {
                for (ConverterUpdateMsg converterUpdateMsg : downlinkMsg.getConverterMsgList()) {
                    result.add(converterProcessor.processConverterMsgFromCloud(tenantId, converterUpdateMsg));
                }
            }
            if (downlinkMsg.getIntegrationMsgCount() > 0) {
                for (IntegrationUpdateMsg integrationUpdateMsg : downlinkMsg.getIntegrationMsgList()) {
                    result.add(integrationProcessor.processIntegrationMsgFromCloud(tenantId, integrationUpdateMsg));
                }
            }
            if (downlinkMsg.getTenantProfileUpdateMsgCount() > 0) {
                for (TenantProfileUpdateMsg tenantProfileUpdateMsg : downlinkMsg.getTenantProfileUpdateMsgList()) {
                    result.add(tenantProfileCloudProcessor.processTenantProfileMsgFromCloud(tenantId, tenantProfileUpdateMsg));
                }
            }
            if (downlinkMsg.getNotificationTemplateUpdateMsgCount() > 0) {
                for (NotificationTemplateUpdateMsg notificationTemplateUpdateMsg : downlinkMsg.getNotificationTemplateUpdateMsgList()) {
                    result.add(notificationCloudProcessor.processNotificationTemplateMsgFromCloud(tenantId, notificationTemplateUpdateMsg));
                }
            }
            if (downlinkMsg.getNotificationTargetUpdateMsgCount() > 0) {
                for (NotificationTargetUpdateMsg notificationTargetUpdateMsg : downlinkMsg.getNotificationTargetUpdateMsgList()) {
                    result.add(notificationCloudProcessor.processNotificationTargetMsgFromCloud(tenantId, notificationTargetUpdateMsg));
                }
            }
            if (downlinkMsg.getNotificationRuleUpdateMsgCount() > 0) {
                for (NotificationRuleUpdateMsg notificationRuleUpdateMsg : downlinkMsg.getNotificationRuleUpdateMsgList()) {
                    result.add(notificationCloudProcessor.processNotificationRuleMsgFromCloud(tenantId, notificationRuleUpdateMsg));
                }
            }
            if (downlinkMsg.getOAuth2ClientUpdateMsgCount() > 0) {
                for (OAuth2ClientUpdateMsg oAuth2ClientUpdateMsg : downlinkMsg.getOAuth2ClientUpdateMsgList()) {
                    result.add(oAuth2CloudProcessor.processOAuth2ClientMsgFromCloud(oAuth2ClientUpdateMsg));
                }
            }
            if (downlinkMsg.getOAuth2DomainUpdateMsgCount() > 0) {
                for (OAuth2DomainUpdateMsg oAuth2DomainUpdateMsg : downlinkMsg.getOAuth2DomainUpdateMsgList()) {
                    result.add(oAuth2CloudProcessor.processDomainMsgFromCloud(oAuth2DomainUpdateMsg));
                }
            }
            if (downlinkMsg.getTenantUpdateMsgCount() > 0) {
                for (TenantUpdateMsg tenantUpdateMsg : downlinkMsg.getTenantUpdateMsgList()) {
                    result.add(tenantCloudProcessor.processTenantMsgFromCloud(tenantUpdateMsg));
                }
            }
            if (downlinkMsg.getResourceUpdateMsgCount() > 0) {
                for (ResourceUpdateMsg resourceUpdateMsg : downlinkMsg.getResourceUpdateMsgList()) {
                    result.add(tbResourceCloudProcessor.processResourceMsgFromCloud(tenantId, resourceUpdateMsg));
                }
            }
            if (downlinkMsg.getDeviceGroupOtaPackageUpdateMsgCount() > 0) {
                for (DeviceGroupOtaPackageUpdateMsg deviceGroupOtaPackageUpdateMsg : downlinkMsg.getDeviceGroupOtaPackageUpdateMsgList()) {
                    result.add(deviceProcessor.processDeviceGroupOtaPackageFromCloud(tenantId, deviceGroupOtaPackageUpdateMsg));
                }
            }
            if (downlinkMsg.getCalculatedFieldUpdateMsgCount() > 0) {
                for (CalculatedFieldUpdateMsg calculatedFieldUpdateMsg : downlinkMsg.getCalculatedFieldUpdateMsgList()) {
                    result.add(calculatedFieldCloudProcessor.processCalculatedFieldMsgFromCloud(tenantId, calculatedFieldUpdateMsg));
                }
            }

            log.trace("Finished processing DownlinkMsg {}", downlinkMsg.getDownlinkMsgId());
        } catch (Exception e) {
            log.error("Can't process downlink message [{}]", downlinkMsg, e);
            return Futures.immediateFailedFuture(new RuntimeException("Can't process downlink message", e));
        }
        return Futures.allAsList(result);
    }

    private ListenableFuture<Void> updateSyncRequiredState(TenantId tenantId, CustomerId customerId, EdgeSettings currentEdgeSettings) {
        log.debug("Marking full sync required to false");
        if (currentEdgeSettings != null) {
            currentEdgeSettings.setFullSyncRequired(false);
            try {
                cloudEventService.saveCloudEvent(tenantId, CloudEventType.TENANT, EdgeEventActionType.ATTRIBUTES_REQUEST, tenantId, null, null);
                if (customerId != null && !EntityId.NULL_UUID.equals(customerId.getId())) {
                    cloudEventService.saveCloudEvent(tenantId, CloudEventType.CUSTOMER, EdgeEventActionType.ATTRIBUTES_REQUEST, customerId, null, null);
                }
            } catch (Exception e) {
                log.error("Failed to request attributes for tenant and customer entities", e);
            }
            return Futures.transform(edgeSettingsService.saveEdgeSettings(tenantId, currentEdgeSettings),
                    result -> {
                        log.debug("Full sync required marked as false");
                        return null;
                    },
                    dbCallbackExecutorService);
        } else {
            return Futures.immediateFuture(null);
        }
    }

    private ListenableFuture<Void> processDeviceCredentialsRequestMsg(TenantId tenantId, DeviceCredentialsRequestMsg deviceCredentialsRequestMsg) {
        if (deviceCredentialsRequestMsg.getDeviceIdMSB() != 0 && deviceCredentialsRequestMsg.getDeviceIdLSB() != 0) {
            DeviceId deviceId = new DeviceId(new UUID(deviceCredentialsRequestMsg.getDeviceIdMSB(), deviceCredentialsRequestMsg.getDeviceIdLSB()));
            return cloudEventService.saveCloudEventAsync(tenantId, CloudEventType.DEVICE, EdgeEventActionType.CREDENTIALS_UPDATED, deviceId, null, null);
        } else {
            return Futures.immediateFuture(null);
        }
    }

}
