/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.edge.rpc.processor.asset.profile;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.exception.DataValidationException;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.service.edge.rpc.constructor.asset.AssetMsgConstructor;

import java.util.UUID;

@Slf4j
public abstract class AssetProfileEdgeProcessor extends BaseAssetProfileProcessor implements AssetProfileProcessor {

    @Override
    public ListenableFuture<Void> processAssetProfileMsgFromEdge(TenantId tenantId, Edge edge, AssetProfileUpdateMsg assetProfileUpdateMsg) {
        log.trace("[{}] executing processAssetProfileMsgFromEdge [{}] from edge [{}]", tenantId, assetProfileUpdateMsg, edge.getId());
        AssetProfileId assetProfileId = new AssetProfileId(new UUID(assetProfileUpdateMsg.getIdMSB(), assetProfileUpdateMsg.getIdLSB()));
        try {
            edgeSynchronizationManager.getEdgeId().set(edge.getId());

            switch (assetProfileUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    saveOrUpdateAssetProfile(tenantId, assetProfileId, assetProfileUpdateMsg, edge);
                    return Futures.immediateFuture(null);
                case ENTITY_DELETED_RPC_MESSAGE:
                case UNRECOGNIZED:
                default:
                    return handleUnsupportedMsgType(assetProfileUpdateMsg.getMsgType());
            }
        } catch (DataValidationException e) {
            log.warn("[{}] Failed to process AssetProfileUpdateMsg from Edge [{}]", tenantId, assetProfileUpdateMsg, e);
            return Futures.immediateFailedFuture(e);
        } finally {
            edgeSynchronizationManager.getEdgeId().remove();
        }
    }

    private void saveOrUpdateAssetProfile(TenantId tenantId, AssetProfileId assetProfileId, AssetProfileUpdateMsg assetProfileUpdateMsg, Edge edge) {
        Pair<Boolean, Boolean> resultPair = super.saveOrUpdateAssetProfile(tenantId, assetProfileId, assetProfileUpdateMsg);
        Boolean created = resultPair.getFirst();
        if (created) {
            createRelationFromEdge(tenantId, edge.getId(), assetProfileId);
            pushAssetProfileCreatedEventToRuleEngine(tenantId, edge, assetProfileId);
        }
        Boolean assetProfileNameUpdated = resultPair.getSecond();
        if (assetProfileNameUpdated) {
            saveEdgeEvent(tenantId, edge.getId(), EdgeEventType.ASSET_PROFILE, EdgeEventActionType.UPDATED, assetProfileId, null);
        }
    }

    private void pushAssetProfileCreatedEventToRuleEngine(TenantId tenantId, Edge edge, AssetProfileId assetProfileId) {
        try {
            AssetProfile assetProfile = assetProfileService.findAssetProfileById(tenantId, assetProfileId);
            String assetProfileAsString = JacksonUtil.toString(assetProfile);
            TbMsgMetaData msgMetaData = getEdgeActionTbMsgMetaData(edge, null);
            pushEntityEventToRuleEngine(tenantId, assetProfileId, null, TbMsgType.ENTITY_CREATED, assetProfileAsString, msgMetaData);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to push asset profile action to rule engine: {}", tenantId, assetProfileId, TbMsgType.ENTITY_CREATED.name(), e);
        }
    }

    @Override
    public DownlinkMsg convertAssetProfileEventToDownlink(EdgeEvent edgeEvent, EdgeId edgeId, EdgeVersion edgeVersion) {
        AssetProfileId assetProfileId = new AssetProfileId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        switch (edgeEvent.getAction()) {
            case ADDED:
            case UPDATED:
                AssetProfile assetProfile = assetProfileService.findAssetProfileById(edgeEvent.getTenantId(), assetProfileId);
                if (assetProfile != null) {
                    UpdateMsgType msgType = getUpdateMsgType(edgeEvent.getAction());
                    assetProfile = checkIfAssetProfileDefaultFieldsAssignedToEdge(edgeEvent.getTenantId(), edgeId, assetProfile, edgeVersion);
                    AssetProfileUpdateMsg assetProfileUpdateMsg = ((AssetMsgConstructor)
                            assetMsgConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructAssetProfileUpdatedMsg(msgType, assetProfile);
                    downlinkMsg = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .addAssetProfileUpdateMsg(assetProfileUpdateMsg)
                            .build();
                }
                break;
            case DELETED:
                AssetProfileUpdateMsg assetProfileUpdateMsg = ((AssetMsgConstructor)
                        assetMsgConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructAssetProfileDeleteMsg(assetProfileId);
                downlinkMsg = DownlinkMsg.newBuilder()
                        .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                        .addAssetProfileUpdateMsg(assetProfileUpdateMsg)
                        .build();
                break;
        }
        return downlinkMsg;
    }
}
