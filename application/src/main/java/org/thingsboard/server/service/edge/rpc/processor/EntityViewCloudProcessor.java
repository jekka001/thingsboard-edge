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
package org.thingsboard.server.service.edge.rpc.processor;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.edge.CloudType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.v1.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityViewsRequestMsg;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class EntityViewCloudProcessor extends BaseCloudProcessor {

    private final Lock entityViewCreationLock = new ReentrantLock();

    public ListenableFuture<Void> processEntityViewMsgFromCloud(TenantId tenantId, CustomerId customerId, EntityViewUpdateMsg entityViewUpdateMsg, CloudType cloudType) {
        EntityViewId entityViewId = new EntityViewId(new UUID(entityViewUpdateMsg.getIdMSB(), entityViewUpdateMsg.getIdLSB()));
        switch (entityViewUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                entityViewCreationLock.lock();
                try {
                    EntityView entityView = entityViewService.findEntityViewById(tenantId, entityViewId);
                    boolean created = false;
                    if (entityView == null) {
                        entityView = new EntityView();
                        entityView.setTenantId(tenantId);
                        entityView.setId(entityViewId);
                        entityView.setCreatedTime(Uuids.unixTimestamp(entityViewId.getId()));
                        created = true;
                    }
                    EntityId entityId = null;
                    switch (entityViewUpdateMsg.getEntityType()) {
                        case DEVICE:
                            entityId = new DeviceId(new UUID(entityViewUpdateMsg.getEntityIdMSB(), entityViewUpdateMsg.getEntityIdLSB()));
                            break;
                        case ASSET:
                            entityId = new AssetId(new UUID(entityViewUpdateMsg.getEntityIdMSB(), entityViewUpdateMsg.getEntityIdLSB()));
                            break;
                    }
                    entityView.setName(entityViewUpdateMsg.getName());
                    entityView.setType(entityViewUpdateMsg.getType());
                    entityView.setEntityId(entityId);
                    if (entityViewUpdateMsg.hasAdditionalInfo()) {
                        entityView.setAdditionalInfo(JacksonUtil.toJsonNode(entityViewUpdateMsg.getAdditionalInfo()));
                    }
                    CustomerId entityViewCustomerId = safeSetCustomerId(entityViewUpdateMsg, cloudType, entityView);
                    EntityView savedEntityView = entityViewService.saveEntityView(entityView, false);

                    if (created) {
                        entityGroupService.addEntityToEntityGroupAll(savedEntityView.getTenantId(), savedEntityView.getOwnerId(), savedEntityView.getId());
                    }

                    addToEntityGroup(tenantId, customerId, entityViewUpdateMsg, cloudType, entityViewId, entityViewCustomerId);
                } finally {
                    entityViewCreationLock.unlock();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                if (entityViewUpdateMsg.hasEntityGroupIdMSB() && entityViewUpdateMsg.hasEntityGroupIdLSB()) {
                    UUID entityGroupUUID = safeGetUUID(entityViewUpdateMsg.getEntityGroupIdMSB(),
                            entityViewUpdateMsg.getEntityGroupIdLSB());
                    EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
                    entityGroupService.removeEntityFromEntityGroup(tenantId, entityGroupId, entityViewId);
                } else {
                    EntityView entityViewById = entityViewService.findEntityViewById(tenantId, entityViewId);
                    if (entityViewById != null) {
                        entityViewService.deleteEntityView(tenantId, entityViewId);
                    }
                }
                break;
            case UNRECOGNIZED:
                log.error("Unsupported msg type");
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type " + entityViewUpdateMsg.getMsgType()));
        }
        return Futures.transform(requestForAdditionalData(tenantId, entityViewUpdateMsg.getMsgType(), entityViewId), future -> null, dbCallbackExecutor);
    }

    private CustomerId safeSetCustomerId(EntityViewUpdateMsg entityViewUpdateMsg, CloudType cloudType, EntityView entityView) {
        CustomerId entityViewCustomerId = safeGetCustomerId(entityViewUpdateMsg.getCustomerIdMSB(),
                entityViewUpdateMsg.getCustomerIdLSB());
        if (CloudType.PE.equals(cloudType)) {
            entityView.setCustomerId(entityViewCustomerId);
        }
        return entityViewCustomerId;
    }

    private void addToEntityGroup(TenantId tenantId, CustomerId customerId, EntityViewUpdateMsg entityViewUpdateMsg, CloudType cloudType, EntityViewId entityViewId, CustomerId entityViewCustomerId) {
        if (CloudType.CE.equals(cloudType)) {
            if (entityViewCustomerId != null && entityViewCustomerId.equals(customerId)) {
                EntityGroup customerEntityViewsEntityGroup =
                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.ENTITY_VIEW);
                entityGroupService.addEntityToEntityGroup(tenantId, customerEntityViewsEntityGroup.getId(), entityViewId);
            }
            if ((entityViewCustomerId == null || entityViewCustomerId.isNullUid()) &&
                    (customerId != null && !customerId.isNullUid())) {
                EntityGroup customerEntityViewsEntityGroup =
                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.ENTITY_VIEW);
                entityGroupService.removeEntityFromEntityGroup(tenantId, customerEntityViewsEntityGroup.getId(), entityViewId);
            }
        } else {
            if (entityViewUpdateMsg.hasEntityGroupIdMSB() && entityViewUpdateMsg.hasEntityGroupIdLSB()) {
                UUID entityGroupUUID = safeGetUUID(entityViewUpdateMsg.getEntityGroupIdMSB(),
                        entityViewUpdateMsg.getEntityGroupIdLSB());
                EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
                addEntityToGroup(tenantId, entityGroupId, entityViewId);
            }
        }
    }

    public UplinkMsg processEntityViewRequestMsgToCloud(CloudEvent cloudEvent) {
        EntityId entityId = EntityIdFactory.getByCloudEventTypeAndUuid(cloudEvent.getCloudEventType(), cloudEvent.getEntityId());
        EntityViewsRequestMsg entityViewsRequestMsg = EntityViewsRequestMsg.newBuilder()
                .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                .setEntityType(entityId.getEntityType().name())
                .build();
        UplinkMsg.Builder builder = UplinkMsg.newBuilder()
                .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                .addEntityViewsRequestMsg(entityViewsRequestMsg);
        return builder.build();
    }
}
