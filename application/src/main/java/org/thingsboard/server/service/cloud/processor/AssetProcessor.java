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
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.edge.CloudType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.AssetUpdateMsg;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class AssetProcessor extends BaseProcessor {

    private final Lock assetCreationLock = new ReentrantLock();

    public ListenableFuture<Void> onAssetUpdate(TenantId tenantId, CustomerId customerId, AssetUpdateMsg assetUpdateMsg, CloudType cloudType) {
        AssetId assetId = new AssetId(new UUID(assetUpdateMsg.getIdMSB(), assetUpdateMsg.getIdLSB()));
        switch (assetUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                try {
                    assetCreationLock.lock();
                    Asset asset = assetService.findAssetById(tenantId, assetId);
                    boolean created = false;
                    if (asset == null) {
                        asset = new Asset();
                        asset.setTenantId(tenantId);
                        asset.setId(assetId);
                        created = true;
                    }
                    asset.setName(assetUpdateMsg.getName());
                    asset.setType(assetUpdateMsg.getType());
                    asset.setLabel(assetUpdateMsg.getLabel());
                    CustomerId assetCustomerId = safeSetCustomerId(assetUpdateMsg, cloudType, asset);
                    assetService.saveAsset(asset, created);
                    addToEntityGroup(tenantId, customerId, assetUpdateMsg, cloudType, assetId, assetCustomerId);
                } finally {
                    assetCreationLock.unlock();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                UUID entityGroupUUID = safeGetUUID(assetUpdateMsg.getEntityGroupIdMSB(), assetUpdateMsg.getEntityGroupIdLSB());
                if (entityGroupUUID != null) {
                    EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
                    entityGroupService.removeEntityFromEntityGroup(tenantId, entityGroupId, assetId);
                } else {
                    Asset assetById = assetService.findAssetById(tenantId, assetId);
                    if (assetById != null) {
                        assetService.deleteAsset(tenantId, assetId);
                    }
                }
                break;
            case UNRECOGNIZED:
                log.error("Unsupported msg type");
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type" + assetUpdateMsg.getMsgType()));
        }

        return Futures.transform(requestForAdditionalData(tenantId, assetUpdateMsg.getMsgType(), assetId), future -> null, dbCallbackExecutor);
    }

    private void addToEntityGroup(TenantId tenantId, CustomerId customerId, AssetUpdateMsg assetUpdateMsg, CloudType cloudType, AssetId assetId, CustomerId assetCustomerId) {
        if (CloudType.CE.equals(cloudType)) {
            if (assetCustomerId != null && assetCustomerId.equals(customerId)) {
                EntityGroup customerAssetsEntityGroup =
                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.ASSET);
                entityGroupService.addEntityToEntityGroup(tenantId, customerAssetsEntityGroup.getId(), assetId);
            }
            if ((assetCustomerId == null || assetCustomerId.isNullUid()) &&
                    (customerId != null && !customerId.isNullUid())) {
                EntityGroup customerAssetsEntityGroup =
                        entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.ASSET);
                entityGroupService.removeEntityFromEntityGroup(tenantId, customerAssetsEntityGroup.getId(), assetId);
            }
        } else {
            UUID entityGroupUUID = safeGetUUID(assetUpdateMsg.getEntityGroupIdMSB(), assetUpdateMsg.getEntityGroupIdLSB());
            if (entityGroupUUID != null) {
                EntityGroupId entityGroupId = new EntityGroupId(entityGroupUUID);
                addEntityToGroup(tenantId, entityGroupId, assetId);
            }
        }
    }

    private CustomerId safeSetCustomerId(AssetUpdateMsg assetUpdateMsg, CloudType cloudType, Asset asset) {
        CustomerId assetCustomerId = safeGetCustomerId(assetUpdateMsg.getCustomerIdMSB(), assetUpdateMsg.getCustomerIdLSB());
        if (CloudType.PE.equals(cloudType)) {
            asset.setCustomerId(assetCustomerId);
        }
        return assetCustomerId;
    }

}
