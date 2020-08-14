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
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.AssetUpdateMsg;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class AssetUpdateProcessor extends BaseUpdateProcessor {

    private final Lock assetCreationLock = new ReentrantLock();

    public void onAssetUpdate(TenantId tenantId, AssetUpdateMsg assetUpdateMsg) {
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
                    assetService.saveAsset(asset, created);

                    if (isNonEmptyGroupId(assetUpdateMsg.getEntityGroupIdMSB(), assetUpdateMsg.getEntityGroupIdLSB())) {
                        EntityGroupId entityGroupId = new EntityGroupId(new UUID(assetUpdateMsg.getEntityGroupIdMSB(), assetUpdateMsg.getEntityGroupIdLSB()));
                        addEntityToGroup(tenantId, entityGroupId, asset.getId());
                    }

                } finally {
                    assetCreationLock.unlock();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                if (isNonEmptyGroupId(assetUpdateMsg.getEntityGroupIdMSB(), assetUpdateMsg.getEntityGroupIdLSB())) {
                    EntityGroupId entityGroupId = new EntityGroupId(new UUID(assetUpdateMsg.getEntityGroupIdMSB(), assetUpdateMsg.getEntityGroupIdLSB()));
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
        }

        requestForAdditionalData(tenantId, assetUpdateMsg.getMsgType(), assetId);
    }

}
