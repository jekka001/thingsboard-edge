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
package org.thingsboard.server.service.cloud.rpc.processor;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.gen.edge.v1.WidgetTypeUpdateMsg;

import java.util.UUID;

@Component
@Slf4j
public class WidgetTypeCloudProcessor extends BaseCloudProcessor {

    public ListenableFuture<Void> processWidgetTypeMsgFromCloud(TenantId tenantId, WidgetTypeUpdateMsg widgetTypeUpdateMsg) {
        WidgetTypeId widgetTypeId = new WidgetTypeId(new UUID(widgetTypeUpdateMsg.getIdMSB(), widgetTypeUpdateMsg.getIdLSB()));
        switch (widgetTypeUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                widgetCreationLock.lock();
                try {
                    WidgetTypeDetails widgetTypeDetails = widgetTypeService.findWidgetTypeDetailsById(tenantId, widgetTypeId);
                    if (widgetTypeDetails == null) {
                        widgetTypeDetails = new WidgetTypeDetails();
                        if (widgetTypeUpdateMsg.getIsSystem()) {
                            widgetTypeDetails.setTenantId(TenantId.SYS_TENANT_ID);
                        } else {
                            widgetTypeDetails.setTenantId(tenantId);
                        }
                        widgetTypeDetails.setId(widgetTypeId);
                        widgetTypeDetails.setCreatedTime(Uuids.unixTimestamp(widgetTypeId.getId()));
                    }
                    if (widgetTypeUpdateMsg.hasBundleAlias()) {
                        widgetTypeDetails.setBundleAlias(widgetTypeUpdateMsg.getBundleAlias());
                    }
                    if (widgetTypeUpdateMsg.hasAlias()) {
                        widgetTypeDetails.setAlias(widgetTypeUpdateMsg.getAlias());
                    }
                    if (widgetTypeUpdateMsg.hasName()) {
                        widgetTypeDetails.setName(widgetTypeUpdateMsg.getName());
                    }
                    if (widgetTypeUpdateMsg.hasDescriptorJson()) {
                        widgetTypeDetails.setDescriptor(JacksonUtil.toJsonNode(widgetTypeUpdateMsg.getDescriptorJson()));
                    }
                    if (widgetTypeUpdateMsg.hasImage()) {
                        widgetTypeDetails.setImage(widgetTypeUpdateMsg.getImage());
                    }
                    if (widgetTypeUpdateMsg.hasDescription()) {
                        widgetTypeDetails.setDescription(widgetTypeUpdateMsg.getDescription());
                    }
                    widgetTypeService.saveWidgetType(widgetTypeDetails, false);
                } finally {
                    widgetCreationLock.unlock();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                WidgetType widgetType = widgetTypeService.findWidgetTypeById(tenantId, widgetTypeId);
                if (widgetType != null) {
                    widgetTypeService.deleteWidgetType(tenantId, widgetType.getId());
                }
                break;
            case UNRECOGNIZED:
                log.error("Unsupported msg type");
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type " + widgetTypeUpdateMsg.getMsgType()));
        }
        return Futures.immediateFuture(null);
    }
}
