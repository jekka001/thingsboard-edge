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
package org.thingsboard.server.service.edge.rpc.processor.dashboard;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.exception.DataValidationException;
import org.thingsboard.server.gen.edge.v1.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.service.edge.rpc.constructor.dashboard.DashboardMsgConstructor;

import java.util.UUID;

@Slf4j
public abstract class DashboardEdgeProcessor extends BaseDashboardProcessor implements DashboardProcessor {

    @Override
    public ListenableFuture<Void> processDashboardMsgFromEdge(TenantId tenantId, Edge edge, DashboardUpdateMsg dashboardUpdateMsg) {
        log.trace("[{}] executing processDashboardMsgFromEdge [{}] from edge [{}]", tenantId, dashboardUpdateMsg, edge.getId());
        DashboardId dashboardId = new DashboardId(new UUID(dashboardUpdateMsg.getIdMSB(), dashboardUpdateMsg.getIdLSB()));
        try {
            edgeSynchronizationManager.getEdgeId().set(edge.getId());

            switch (dashboardUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    saveOrUpdateDashboard(tenantId, dashboardId, dashboardUpdateMsg, edge);
                    return Futures.immediateFuture(null);
                case ENTITY_DELETED_RPC_MESSAGE:
                    if (dashboardUpdateMsg.hasEntityGroupIdMSB() && dashboardUpdateMsg.hasEntityGroupIdLSB()) {
                        EntityGroupId entityGroupId = new EntityGroupId(
                                new UUID(dashboardUpdateMsg.getEntityGroupIdMSB(), dashboardUpdateMsg.getEntityGroupIdLSB()));
                        entityGroupService.removeEntityFromEntityGroup(tenantId, entityGroupId, dashboardId);
                    } else {
                        removeDashboardFromEdgeAllDashboardGroup(tenantId, edge, dashboardId);
                    }
                    return Futures.immediateFuture(null);
                case UNRECOGNIZED:
                default:
                    return handleUnsupportedMsgType(dashboardUpdateMsg.getMsgType());
            }
        } catch (DataValidationException | ThingsboardException e) {
            if (e.getMessage().contains("limit reached")) {
                log.warn("[{}] Number of allowed dashboard violated {}", tenantId, dashboardUpdateMsg, e);
                return Futures.immediateFuture(null);
            } else {
                return Futures.immediateFailedFuture(e);
            }
        } finally {
            edgeSynchronizationManager.getEdgeId().remove();
        }
    }

    private void saveOrUpdateDashboard(TenantId tenantId, DashboardId dashboardId, DashboardUpdateMsg dashboardUpdateMsg, Edge edge) throws ThingsboardException {
        boolean created = super.saveOrUpdateDashboard(tenantId, dashboardId, dashboardUpdateMsg);
        if (created) {
            createRelationFromEdge(tenantId, edge.getId(), dashboardId);
            pushDashboardCreatedEventToRuleEngine(tenantId, edge, dashboardId);
        }
        addDashboardToEdgeAllDashboardGroup(tenantId, edge, dashboardId);
    }

    private void pushDashboardCreatedEventToRuleEngine(TenantId tenantId, Edge edge, DashboardId dashboardId) {
        try {
            Dashboard dashboard = dashboardService.findDashboardById(tenantId, dashboardId);
            String dashboardAsString = JacksonUtil.toString(dashboard);
            TbMsgMetaData msgMetaData = getEdgeActionTbMsgMetaData(edge, null);
            pushEntityEventToRuleEngine(tenantId, dashboardId, null, TbMsgType.ENTITY_CREATED, dashboardAsString, msgMetaData);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to push dashboard action to rule engine: {}", tenantId, dashboardId, TbMsgType.ENTITY_CREATED.name(), e);
        }
    }

    private void removeDashboardFromEdgeAllDashboardGroup(TenantId tenantId, Edge edge, DashboardId dashboardId) {
        Dashboard dashboardToDelete = dashboardService.findDashboardById(tenantId, dashboardId);
        if (dashboardToDelete != null) {
            try {
                EntityGroup edgeDashboardGroup = entityGroupService.findOrCreateEdgeAllGroupAsync(tenantId, edge, edge.getName(), EntityType.DASHBOARD).get();
                if (edgeDashboardGroup != null) {
                    entityGroupService.removeEntityFromEntityGroup(tenantId, edgeDashboardGroup.getId(), dashboardToDelete.getId());
                }
            } catch (Exception e) {
                log.warn("[{}] Can't delete dashboard from edge dashboard 'All' group, dashboard id [{}]", tenantId, dashboardToDelete, e);
                throw new RuntimeException(e);
            }
        }
    }

    private void addDashboardToEdgeAllDashboardGroup(TenantId tenantId, Edge edge, DashboardId dashboardId) {
        try {
            EntityGroup edgeDashboardGroup = entityGroupService.findOrCreateEdgeAllGroupAsync(tenantId, edge, edge.getName(), EntityType.DASHBOARD).get();
            if (edgeDashboardGroup != null) {
                entityGroupService.addEntityToEntityGroup(tenantId, edgeDashboardGroup.getId(), dashboardId);
            }
        } catch (Exception e) {
            log.warn("[{}] Can't add dashboard to edge dashboard group, dashboard id [{}]", tenantId, dashboardId, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public DownlinkMsg convertDashboardEventToDownlink(EdgeEvent edgeEvent, EdgeVersion edgeVersion) {
        DashboardId dashboardId = new DashboardId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        EntityGroupId entityGroupId = edgeEvent.getEntityGroupId() != null ? new EntityGroupId(edgeEvent.getEntityGroupId()) : null;
        switch (edgeEvent.getAction()) {
            case ADDED:
            case ADDED_TO_ENTITY_GROUP:
            case UPDATED:
            case ASSIGNED_TO_EDGE:
                Dashboard dashboard = dashboardService.findDashboardById(edgeEvent.getTenantId(), dashboardId);
                if (dashboard != null) {
                    UpdateMsgType msgType = getUpdateMsgType(edgeEvent.getAction());
                    DashboardUpdateMsg dashboardUpdateMsg = ((DashboardMsgConstructor)
                            dashboardMsgConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructDashboardUpdatedMsg(msgType, dashboard, entityGroupId);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .addDashboardUpdateMsg(dashboardUpdateMsg)
                            .build();
                }
                break;
            case DELETED:
            case REMOVED_FROM_ENTITY_GROUP:
            case UNASSIGNED_FROM_EDGE:
            case CHANGE_OWNER:
                DashboardUpdateMsg dashboardUpdateMsg = ((DashboardMsgConstructor)
                        dashboardMsgConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructDashboardDeleteMsg(dashboardId, entityGroupId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                        .addDashboardUpdateMsg(dashboardUpdateMsg)
                        .build();
                break;
        }
        return downlinkMsg;
    }
}
