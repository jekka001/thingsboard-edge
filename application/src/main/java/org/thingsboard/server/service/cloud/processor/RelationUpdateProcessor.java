/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.cloud.processor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.gen.edge.RelationUpdateMsg;

import java.util.UUID;

@Component
@Slf4j
public class RelationUpdateProcessor extends BaseUpdateProcessor {

    public ListenableFuture<Void> onRelationUpdate(TenantId tenantId, RelationUpdateMsg relationUpdateMsg) {
        try {
            EntityRelation entityRelation = new EntityRelation();

            UUID fromUUID = new UUID(relationUpdateMsg.getFromIdMSB(), relationUpdateMsg.getFromIdLSB());
            EntityId fromId = EntityIdFactory.getByTypeAndUuid(EntityType.valueOf(relationUpdateMsg.getFromEntityType()), fromUUID);
            entityRelation.setFrom(fromId);

            UUID toUUID = new UUID(relationUpdateMsg.getToIdMSB(), relationUpdateMsg.getToIdLSB());
            EntityId toId = EntityIdFactory.getByTypeAndUuid(EntityType.valueOf(relationUpdateMsg.getToEntityType()), toUUID);
            entityRelation.setTo(toId);

            entityRelation.setType(relationUpdateMsg.getType());
            entityRelation.setTypeGroup(RelationTypeGroup.valueOf(relationUpdateMsg.getTypeGroup()));
            entityRelation.setAdditionalInfo(mapper.readTree(relationUpdateMsg.getAdditionalInfo()));

            switch (relationUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    if (isEntityExists(tenantId, entityRelation.getTo())
                            && isEntityExists(tenantId, entityRelation.getFrom())) {
                        relationService.saveRelationAsync(tenantId, entityRelation);
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    relationService.deleteRelation(tenantId, entityRelation);
                    break;
                case UNRECOGNIZED:
                    log.error("Unsupported msg type");
                    return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type" + relationUpdateMsg.getMsgType()));
            }
        } catch (Exception e) {
            log.error("Error during relation update msg", e);
            return Futures.immediateFailedFuture(new RuntimeException("Error during relation update msg", e));
        }
        return Futures.immediateFuture(null);
    }

    private boolean isEntityExists(TenantId tenantId, EntityId entityId) throws ThingsboardException {
        switch (entityId.getEntityType()) {
            case DEVICE:
                return deviceService.findDeviceById(tenantId, new DeviceId(entityId.getId())) != null;
            case ASSET:
                return assetService.findAssetById(tenantId, new AssetId(entityId.getId())) != null;
            case ENTITY_VIEW:
                return entityViewService.findEntityViewById(tenantId, new EntityViewId(entityId.getId())) != null;
            case CUSTOMER:
                return customerService.findCustomerById(tenantId, new CustomerId(entityId.getId())) != null;
            case USER:
                return userService.findUserById(tenantId, new UserId(entityId.getId())) != null;
            case DASHBOARD:
                return dashboardService.findDashboardById(tenantId, new DashboardId(entityId.getId())) != null;
            case ENTITY_GROUP:
                return entityGroupService.findEntityGroupById(tenantId, new EntityGroupId(entityId.getId())) != null;
            default:
                throw new ThingsboardException("Unsupported entity type " + entityId.getEntityType(), ThingsboardErrorCode.INVALID_ARGUMENTS);
        }

    }
}
