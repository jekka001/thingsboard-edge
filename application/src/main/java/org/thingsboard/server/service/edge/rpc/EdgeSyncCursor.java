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
package org.thingsboard.server.service.edge.rpc;

import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.service.edge.EdgeContextComponent;
import org.thingsboard.server.service.edge.rpc.fetch.AdminSettingsEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.CustomerRolesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.AssetProfilesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.DeviceProfilesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.EdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.EntityGroupEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.IntegrationEventsEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.OwnerEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.OtaPackagesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.QueuesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.RuleChainsEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.SchedulerEventsEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.SysAdminRolesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.SystemWidgetsBundlesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.TenantRolesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.TenantWidgetsBundlesEdgeEventFetcher;
import org.thingsboard.server.service.edge.rpc.fetch.WhiteLabelingEdgeEventFetcher;

import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

public class EdgeSyncCursor {

    List<EdgeEventFetcher> fetchers = new LinkedList<>();

    int currentIdx = 0;

    public EdgeSyncCursor(EdgeContextComponent ctx, Edge edge) {
        fetchers.add(new QueuesEdgeEventFetcher(ctx.getQueueService()));
        fetchers.add(new RuleChainsEdgeEventFetcher(ctx.getRuleChainService()));
        fetchers.add(new AdminSettingsEdgeEventFetcher(ctx.getAdminSettingsService(), ctx.getAttributesService()));
        fetchers.add(new DeviceProfilesEdgeEventFetcher(ctx.getDeviceProfileService()));
        fetchers.add(new AssetProfilesEdgeEventFetcher(ctx.getAssetProfileService()));
        if (EntityType.CUSTOMER.equals(edge.getOwnerId().getEntityType())) {
            fetchers.add(new OwnerEdgeEventFetcher());
            fetchers.add(new CustomerRolesEdgeEventFetcher(ctx.getRoleService(), new CustomerId(edge.getOwnerId().getId())));
        }
        fetchers.add(new SysAdminRolesEdgeEventFetcher(ctx.getRoleService()));
        fetchers.add(new TenantRolesEdgeEventFetcher(ctx.getRoleService()));
        fetchers.add(new WhiteLabelingEdgeEventFetcher(ctx.getWhiteLabelingService(), ctx.getCustomTranslationService()));
        fetchers.add(new SystemWidgetsBundlesEdgeEventFetcher(ctx.getWidgetsBundleService()));
        fetchers.add(new TenantWidgetsBundlesEdgeEventFetcher(ctx.getWidgetsBundleService()));
        fetchers.add(new EntityGroupEdgeEventFetcher(ctx.getEntityGroupService(), EntityType.DEVICE));
        fetchers.add(new EntityGroupEdgeEventFetcher(ctx.getEntityGroupService(), EntityType.ASSET));
        fetchers.add(new EntityGroupEdgeEventFetcher(ctx.getEntityGroupService(), EntityType.ENTITY_VIEW));
        fetchers.add(new EntityGroupEdgeEventFetcher(ctx.getEntityGroupService(), EntityType.DASHBOARD));
        fetchers.add(new EntityGroupEdgeEventFetcher(ctx.getEntityGroupService(), EntityType.USER));
        fetchers.add(new SchedulerEventsEdgeEventFetcher(ctx.getSchedulerEventService()));
        fetchers.add(new IntegrationEventsEdgeEventFetcher(ctx.getIntegrationService()));
        fetchers.add(new OtaPackagesEdgeEventFetcher(ctx.getOtaPackageService()));
    }

    public boolean hasNext() {
        return fetchers.size() > currentIdx;
    }

    public EdgeEventFetcher getNext() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        EdgeEventFetcher edgeEventFetcher = fetchers.get(currentIdx);
        currentIdx++;
        return edgeEventFetcher;
    }

    public int getCurrentIdx() {
        return currentIdx;
    }
}
