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
package org.thingsboard.server.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.dao.edge.EdgeEventService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.edge.rpc.processor.AlarmEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.AssetEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.AssetProfileEdgeProcessor;
<<<<<<< HEAD
import org.thingsboard.server.service.edge.rpc.processor.ConverterEdgeProcessor;
=======
>>>>>>> ce-upstream/feature/edge-multi-customers
import org.thingsboard.server.service.edge.rpc.processor.CustomerEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.DashboardEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.DeviceEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.DeviceProfileEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.EdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.EntityGroupEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.EntityViewEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.GroupPermissionsEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.IntegrationEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.OtaPackageEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.QueueEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.RelationEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.RoleEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.RuleChainEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.SchedulerEventEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.UserEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.WidgetBundleEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.processor.WidgetTypeEdgeProcessor;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@TbCoreComponent
@Slf4j
public class DefaultEdgeNotificationService implements EdgeNotificationService {

    @Autowired
    private EdgeService edgeService;

    @Autowired
    private EdgeEventService edgeEventService;

    @Autowired
    private TbClusterService clusterService;

    @Autowired
    private EdgeProcessor edgeProcessor;

    @Autowired
    private AssetEdgeProcessor assetProcessor;

    @Autowired
    private DeviceEdgeProcessor deviceProcessor;

    @Autowired
    private EntityViewEdgeProcessor entityViewProcessor;

    @Autowired
    private DashboardEdgeProcessor dashboardProcessor;

    @Autowired
    private RuleChainEdgeProcessor ruleChainProcessor;

    @Autowired
    private UserEdgeProcessor userProcessor;

    @Autowired
    private CustomerEdgeProcessor customerProcessor;

    @Autowired
    private DeviceProfileEdgeProcessor deviceProfileProcessor;

    @Autowired
    private AssetProfileEdgeProcessor assetProfileProcessor;

    @Autowired
    private OtaPackageEdgeProcessor otaPackageProcessor;

    @Autowired
    private WidgetBundleEdgeProcessor widgetBundleProcessor;

    @Autowired
    private WidgetTypeEdgeProcessor widgetTypeProcessor;

    @Autowired
    private QueueEdgeProcessor queueProcessor;

    @Autowired
    private AlarmEdgeProcessor alarmProcessor;

    @Autowired
    private RelationEdgeProcessor relationProcessor;


    private ExecutorService dbCallBackExecutor;

    // PE context

    @Autowired
    private RoleEdgeProcessor roleProcessor;

    @Autowired
    private GroupPermissionsEdgeProcessor groupPermissionsProcessor;

    @Autowired
    private SchedulerEventEdgeProcessor schedulerEventProcessor;

    @Autowired
    private EntityGroupEdgeProcessor entityGroupProcessor;

    @Autowired
    private IntegrationEdgeProcessor integrationProcessor;

    @Autowired
    private ConverterEdgeProcessor converterProcessor;

    @PostConstruct
    public void initExecutor() {
        dbCallBackExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("edge-notifications"));
    }

    @PreDestroy
    public void shutdownExecutor() {
        if (dbCallBackExecutor != null) {
            dbCallBackExecutor.shutdownNow();
        }
    }

    @Override
    public Edge setEdgeRootRuleChain(TenantId tenantId, Edge edge, RuleChainId ruleChainId) throws Exception {
        edge.setRootRuleChainId(ruleChainId);
        Edge savedEdge = edgeService.saveEdge(edge);
        saveEdgeEvent(tenantId, edge.getId(), EdgeEventType.RULE_CHAIN, EdgeEventActionType.UPDATED, ruleChainId, null).get();
        return savedEdge;
    }

    private ListenableFuture<Void> saveEdgeEvent(TenantId tenantId,
                               EdgeId edgeId,
                               EdgeEventType type,
                               EdgeEventActionType action,
                               EntityId entityId,
                               JsonNode body) {
        log.debug("Pushing edge event to edge queue. tenantId [{}], edgeId [{}], type [{}], action[{}], entityId [{}], body [{}]",
                tenantId, edgeId, type, action, entityId, body);

        EdgeEvent edgeEvent = EdgeUtils.constructEdgeEvent(tenantId, edgeId, type, action, entityId, body);

        return Futures.transform(edgeEventService.saveAsync(edgeEvent), unused -> {
            clusterService.onEdgeEventUpdate(tenantId, edgeId);
            return null;
        }, dbCallBackExecutor);
    }

    @Override
    public void pushNotificationToEdge(TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg, TbCallback callback) {
        log.debug("Pushing notification to edge {}", edgeNotificationMsg);
        try {
            TenantId tenantId = TenantId.fromUUID(new UUID(edgeNotificationMsg.getTenantIdMSB(), edgeNotificationMsg.getTenantIdLSB()));
            EdgeEventType type = EdgeEventType.valueOf(edgeNotificationMsg.getType());
            ListenableFuture<Void> future;
            switch (type) {
                case EDGE:
                    future = edgeProcessor.processEdgeNotification(tenantId, edgeNotificationMsg);
                    break;
                case ASSET:
                    future = assetProcessor.processAssetNotification(tenantId, edgeNotificationMsg);
                    break;
                case DEVICE:
                    future = deviceProcessor.processDeviceNotification(tenantId, edgeNotificationMsg);
                    break;
                case ENTITY_VIEW:
                    future = entityViewProcessor.processEntityViewNotification(tenantId, edgeNotificationMsg);
                    break;
                case DASHBOARD:
                    future = dashboardProcessor.processDashboardNotification(tenantId, edgeNotificationMsg);
                    break;
                case RULE_CHAIN:
                    future = ruleChainProcessor.processRuleChainNotification(tenantId, edgeNotificationMsg);
                    break;
                case USER:
                    future = userProcessor.processUserNotification(tenantId, edgeNotificationMsg);
                    break;
                case CUSTOMER:
                    future = customerProcessor.processCustomerNotification(tenantId, edgeNotificationMsg);
                    break;
                case DEVICE_PROFILE:
                    future = deviceProfileProcessor.processDeviceProfileNotification(tenantId, edgeNotificationMsg);
                    break;
                case ASSET_PROFILE:
                    future = assetProfileProcessor.processAssetProfileNotification(tenantId, edgeNotificationMsg);
                    break;
                case OTA_PACKAGE:
                    future = otaPackageProcessor.processOtaPackageNotification(tenantId, edgeNotificationMsg);
                    break;
                case WIDGETS_BUNDLE:
                    future = widgetBundleProcessor.processWidgetsBundleNotification(tenantId, edgeNotificationMsg);
                    break;
                case WIDGET_TYPE:
                    future = widgetTypeProcessor.processWidgetTypeNotification(tenantId, edgeNotificationMsg);
                    break;
                case QUEUE:
                    future = queueProcessor.processQueueNotification(tenantId, edgeNotificationMsg);
                    break;
                case ALARM:
                    future = alarmProcessor.processAlarmNotification(tenantId, edgeNotificationMsg);
                    break;
                case RELATION:
                    future = relationProcessor.processRelationNotification(tenantId, edgeNotificationMsg);
                    break;
                case ROLE:
                    future = roleProcessor.processRoleNotification(tenantId, edgeNotificationMsg);
                    break;
                case GROUP_PERMISSION:
                    future = groupPermissionsProcessor.processGroupPermissionNotification(tenantId, edgeNotificationMsg);
                    break;
                case SCHEDULER_EVENT:
                    future = schedulerEventProcessor.processSchedulerEventNotification(tenantId, edgeNotificationMsg);
                    break;
                case ENTITY_GROUP:
                    future = entityGroupProcessor.processEntityGroupNotification(tenantId, edgeNotificationMsg);
                    break;
                case INTEGRATION:
                    future = integrationProcessor.processIntegrationNotification(tenantId, edgeNotificationMsg);
                    break;
                case CONVERTER:
                    future = converterProcessor.processConverterNotification(tenantId, edgeNotificationMsg);
                    break;
                default:
                    log.warn("Edge event type [{}] is not designed to be pushed to edge", type);
                    future = Futures.immediateFuture(null);
            }
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Void unused) {
                    callback.onSuccess();
                }

                @Override
                public void onFailure(Throwable throwable) {
                    callBackFailure(edgeNotificationMsg, callback, throwable);
                }
            }, dbCallBackExecutor);
        } catch (Exception e) {
            callBackFailure(edgeNotificationMsg, callback, e);
        }
    }

    private void callBackFailure(TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg, TbCallback callback, Throwable throwable) {
        String errMsg = String.format("Can't push to edge updates, edgeNotificationMsg [%s]", edgeNotificationMsg);
        log.error(errMsg, throwable);
        callback.onFailure(throwable);
    }

}

