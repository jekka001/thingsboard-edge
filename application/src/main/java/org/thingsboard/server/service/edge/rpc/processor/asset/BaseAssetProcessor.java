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
package org.thingsboard.server.service.edge.rpc.processor.asset;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.v1.AssetUpdateMsg;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;

import java.util.UUID;

@Slf4j
public abstract class BaseAssetProcessor extends BaseEdgeProcessor {

    protected Pair<Boolean, Boolean> saveOrUpdateAsset(TenantId tenantId, AssetId assetId, AssetUpdateMsg assetUpdateMsg) throws ThingsboardException {
        boolean created = false;
        boolean assetNameUpdated = false;
        assetCreationLock.lock();
        try {
            Asset asset = constructAssetFromUpdateMsg(tenantId, assetId, assetUpdateMsg);
            if (asset == null) {
                throw new RuntimeException("[{" + tenantId + "}] assetUpdateMsg {" + assetUpdateMsg + " } cannot be converted to asset");
            }
            Asset assetById = assetService.findAssetById(tenantId, assetId);
            if (assetById == null) {
                created = true;
                asset.setId(null);
            } else {
                asset.setId(assetId);
                changeOwnerIfRequired(tenantId, asset.getCustomerId(), assetId);
            }
            String assetName = asset.getName();
            Asset assetByName = assetService.findAssetByTenantIdAndName(tenantId, assetName);
            if (assetByName != null && !assetByName.getId().equals(assetId)) {
                assetName = assetName + "_" + StringUtils.randomAlphanumeric(15);
                log.warn("[{}] Asset with name {} already exists. Renaming asset name to {}",
                        tenantId, asset.getName(), assetName);
                assetNameUpdated = true;
            }
            asset.setName(assetName);
            setCustomerId(tenantId, created ? null : assetById.getCustomerId(), asset, assetUpdateMsg);

            assetValidator.validate(asset, Asset::getTenantId);
            if (created) {
                asset.setId(assetId);
            }
            Asset savedAsset = assetService.saveAsset(asset, false);
            if (created) {
                entityGroupService.addEntityToEntityGroupAll(savedAsset.getTenantId(), savedAsset.getOwnerId(), savedAsset.getId());
            }
            safeAddToEntityGroup(tenantId, assetUpdateMsg, assetId);
        } catch (Exception e) {
            log.error("[{}] Failed to process asset update msg [{}]", tenantId, assetUpdateMsg, e);
            throw e;
        } finally {
            assetCreationLock.unlock();
        }
        return Pair.of(created, assetNameUpdated);
    }

    private void safeAddToEntityGroup(TenantId tenantId, AssetUpdateMsg assetUpdateMsg, AssetId assetId) {
        if (assetUpdateMsg.hasEntityGroupIdMSB() && assetUpdateMsg.hasEntityGroupIdLSB()) {
            UUID entityGroupUUID = safeGetUUID(assetUpdateMsg.getEntityGroupIdMSB(),
                    assetUpdateMsg.getEntityGroupIdLSB());
            safeAddEntityToGroup(tenantId, new EntityGroupId(entityGroupUUID), assetId);
        }
    }

    protected abstract Asset constructAssetFromUpdateMsg(TenantId tenantId, AssetId assetId, AssetUpdateMsg assetUpdateMsg);


    protected abstract void setCustomerId(TenantId tenantId, CustomerId customerId, Asset asset, AssetUpdateMsg assetUpdateMsg);
}
