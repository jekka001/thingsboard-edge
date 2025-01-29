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
package org.thingsboard.server.service.cloud.rpc.processor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.WidgetsBundleId;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.gen.edge.v1.WidgetsBundleUpdateMsg;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class WidgetBundleCloudProcessor extends BaseEdgeProcessor {

    public ListenableFuture<Void> processWidgetsBundleMsgFromCloud(TenantId tenantId, WidgetsBundleUpdateMsg widgetsBundleUpdateMsg) {
        WidgetsBundleId widgetsBundleId = new WidgetsBundleId(new UUID(widgetsBundleUpdateMsg.getIdMSB(), widgetsBundleUpdateMsg.getIdLSB()));
        try {
            cloudSynchronizationManager.getSync().set(true);
            switch (widgetsBundleUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    widgetCreationLock.lock();
                    try {
                        WidgetsBundle widgetsBundle = JacksonUtil.fromString(widgetsBundleUpdateMsg.getEntity(), WidgetsBundle.class, true);
                        if (widgetsBundle == null) {
                            throw new RuntimeException("[{" + tenantId + "}] widgetsBundleUpdateMsg {" + widgetsBundleUpdateMsg + "} cannot be converted to widget bundle");
                        }
                        deleteSystemWidgetBundleIfAlreadyExists(widgetsBundle.getAlias(), widgetsBundleId);
                        edgeCtx.getWidgetsBundleService().saveWidgetsBundle(widgetsBundle, false);

                        String[] widgetFqns = JacksonUtil.fromString(widgetsBundleUpdateMsg.getWidgets(), String[].class);
                        if (widgetFqns != null && widgetFqns.length > 0) {
                            edgeCtx.getWidgetTypeService().updateWidgetsBundleWidgetFqns(widgetsBundle.getTenantId(), widgetsBundleId, Arrays.asList(widgetFqns));
                        }
                    } finally {
                        widgetCreationLock.unlock();
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    WidgetsBundle widgetsBundle = edgeCtx.getWidgetsBundleService().findWidgetsBundleById(tenantId, widgetsBundleId);
                    if (widgetsBundle != null) {
                        edgeCtx.getWidgetsBundleService().deleteWidgetsBundle(tenantId, widgetsBundle.getId());
                    }
                    break;
                case UNRECOGNIZED:
                    return handleUnsupportedMsgType(widgetsBundleUpdateMsg.getMsgType());
            }
        } finally {
            cloudSynchronizationManager.getSync().remove();
        }
        return Futures.immediateFuture(null);
    }

    private void deleteSystemWidgetBundleIfAlreadyExists(String bundleAlias, WidgetsBundleId widgetsBundleId) {
        try {
            WidgetsBundle widgetsBundle = edgeCtx.getWidgetsBundleService().findWidgetsBundleByTenantIdAndAlias(TenantId.SYS_TENANT_ID, bundleAlias);
            if (widgetsBundle != null && !widgetsBundleId.equals(widgetsBundle.getId())) {
                edgeCtx.getWidgetsBundleService().deleteWidgetsBundle(TenantId.SYS_TENANT_ID, widgetsBundle.getId());
            }
        } catch (IncorrectResultSizeDataAccessException e) {
            // fix for duplicate entries of system widgets
            List<WidgetsBundle> systemWidgetsBundles = edgeCtx.getWidgetsBundleService().findSystemWidgetsBundles(TenantId.SYS_TENANT_ID);
            for (WidgetsBundle systemWidgetsBundle : systemWidgetsBundles) {
                if (systemWidgetsBundle.getAlias().equals(bundleAlias) &&
                        !systemWidgetsBundle.getId().equals(widgetsBundleId)) {
                    edgeCtx.getWidgetsBundleService().deleteWidgetsBundle(TenantId.SYS_TENANT_ID, systemWidgetsBundle.getId());
                }
            }
            log.warn("Duplicate widgets bundle found, alias {}. Removed all duplicates!", bundleAlias);
        }
    }

}
