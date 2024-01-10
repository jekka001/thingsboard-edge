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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetInfo;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.asset.AssetProfileService;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.asset.BaseAssetService;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;
import org.thingsboard.server.service.edge.rpc.constructor.asset.AssetMsgConstructor;
import org.thingsboard.server.service.edge.rpc.processor.asset.profile.BaseAssetProfileProcessor;

import java.util.UUID;

@Component
@Slf4j
public class AssetProfileCloudProcessor extends BaseAssetProfileProcessor {

    @Autowired
    private AssetProfileService assetProfileService;

    @Autowired
    private AssetService assetService;

    public ListenableFuture<Void> processAssetProfileMsgFromCloud(TenantId tenantId, AssetProfileUpdateMsg assetProfileUpdateMsg) {
        AssetProfileId assetProfileId = new AssetProfileId(new UUID(assetProfileUpdateMsg.getIdMSB(), assetProfileUpdateMsg.getIdLSB()));
        try {
            cloudSynchronizationManager.getSync().set(true);

            switch (assetProfileUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    assetCreationLock.lock();
                    try {
                        AssetProfile assetProfileMsg = JacksonUtil.fromString(assetProfileUpdateMsg.getEntity(), AssetProfile.class, true);
                        if (assetProfileMsg == null) {
                            throw new RuntimeException("[{" + tenantId + "}] assetProfileUpdateMsg {" + assetProfileUpdateMsg + "} cannot be converted to asset profile");
                        }
                        AssetProfile assetProfileByName = assetProfileService.findAssetProfileByName(tenantId, assetProfileMsg.getName());
                        boolean removePreviousProfile = false;
                        if (assetProfileByName != null && !assetProfileByName.getId().equals(assetProfileId)) {
                            renamePreviousAssetProfile(assetProfileByName);
                            removePreviousProfile = true;
                        }
                        Pair<Boolean, Boolean> resultPair = super.saveOrUpdateAssetProfile(tenantId, assetProfileId, assetProfileUpdateMsg);
                        boolean created = resultPair.getFirst();
                        tbClusterService.broadcastEntityStateChangeEvent(tenantId, assetProfileId,
                                created ? ComponentLifecycleEvent.CREATED : ComponentLifecycleEvent.UPDATED);
                        AssetProfile assetProfile = assetProfileService.findAssetProfileById(tenantId, assetProfileId);
                        if (!assetProfile.isDefault() && assetProfileMsg.isDefault()) {
                            assetProfileService.setDefaultAssetProfile(tenantId, assetProfileId);
                        }
                        if (removePreviousProfile) {
                            updateAssets(tenantId, assetProfileId, assetProfileByName.getId());
                            assetProfileService.deleteAssetProfile(tenantId, assetProfileByName.getId());
                            tbClusterService.broadcastEntityStateChangeEvent(tenantId, assetProfileByName.getId(), ComponentLifecycleEvent.DELETED);
                        }
                        if (created) {
                            pushAssetProfileCreatedEventToRuleEngine(tenantId, assetProfileId);
                        }
                    } finally {
                        assetCreationLock.unlock();
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    AssetProfile assetProfile = assetProfileService.findAssetProfileById(tenantId, assetProfileId);
                    if (assetProfile != null) {
                        assetProfileService.deleteAssetProfile(tenantId, assetProfileId);
                        tbClusterService.broadcastEntityStateChangeEvent(tenantId, assetProfileId, ComponentLifecycleEvent.DELETED);
                        pushAssetProfileDeletedEventToRuleEngine(tenantId, assetProfile);
                    }
                    break;
                case UNRECOGNIZED:
                    return handleUnsupportedMsgType(assetProfileUpdateMsg.getMsgType());
            }
        } finally {
            cloudSynchronizationManager.getSync().remove();
        }
        return Futures.immediateFuture(null);
    }

    private void pushAssetProfileCreatedEventToRuleEngine(TenantId tenantId, AssetProfileId assetProfileId) {
        AssetProfile assetProfile = assetProfileService.findAssetProfileById(tenantId, assetProfileId);
        pushAssetProfileEventToRuleEngine(tenantId, assetProfile, TbMsgType.ENTITY_CREATED);
    }

    private void pushAssetProfileDeletedEventToRuleEngine(TenantId tenantId, AssetProfile assetProfile) {
        pushAssetProfileEventToRuleEngine(tenantId, assetProfile, TbMsgType.ENTITY_DELETED);
    }

    private void pushAssetProfileEventToRuleEngine(TenantId tenantId, AssetProfile assetProfile, TbMsgType msgType) {
        try {
            String assetProfileAsString = JacksonUtil.toString(assetProfile);
            pushEntityEventToRuleEngine(tenantId, assetProfile.getId(), null, msgType, assetProfileAsString, new TbMsgMetaData());
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to push asset profile action to rule engine: {}", tenantId, assetProfile.getId(), msgType.name(), e);
        }
    }

    private void renamePreviousAssetProfile(AssetProfile assetProfileByName) {
        assetProfileByName.setName(assetProfileByName.getName() + StringUtils.randomAlphanumeric(15));
        assetProfileService.saveAssetProfile(assetProfileByName);
    }

    private void updateAssets(TenantId tenantId, AssetProfileId newAssetProfileId, AssetProfileId previousAssetProfileId) {
        PageDataIterable<AssetInfo> assetInfosIterable = new PageDataIterable<>(
                link -> assetService.findAssetInfosByTenantIdAndAssetProfileId(tenantId, previousAssetProfileId, link), 1024);
        assetInfosIterable.forEach(assetInfo -> {
            assetInfo.setAssetProfileId(newAssetProfileId);
            assetService.saveAsset(new Asset(assetInfo));
        });
    }

    public UplinkMsg convertAssetProfileEventToUplink(CloudEvent cloudEvent, EdgeVersion edgeVersion) {
        AssetProfileId assetProfileId = new AssetProfileId(cloudEvent.getEntityId());
        UplinkMsg msg = null;
        switch (cloudEvent.getAction()) {
            case ADDED:
            case UPDATED:
                AssetProfile assetProfile = assetProfileService.findAssetProfileById(cloudEvent.getTenantId(), assetProfileId);
                if (assetProfile != null && !BaseAssetService.TB_SERVICE_QUEUE.equals(assetProfile.getName())) {
                    UpdateMsgType msgType = getUpdateMsgType(cloudEvent.getAction());
                    AssetProfileUpdateMsg assetProfileUpdateMsg = ((AssetMsgConstructor)
                            assetMsgConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructAssetProfileUpdatedMsg(msgType, assetProfile);
                    msg = UplinkMsg.newBuilder()
                            .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                            .addAssetProfileUpdateMsg(assetProfileUpdateMsg).build();
                } else {
                    log.info("Skipping event as asset profile was not found [{}]", cloudEvent);
                }
                break;
            case DELETED:
                AssetProfileUpdateMsg assetProfileUpdateMsg = ((AssetMsgConstructor)
                        assetMsgConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructAssetProfileDeleteMsg(assetProfileId);
                msg = UplinkMsg.newBuilder()
                        .setUplinkMsgId(EdgeUtils.nextPositiveInt())
                        .addAssetProfileUpdateMsg(assetProfileUpdateMsg).build();
                break;
        }
        return msg;
    }

    @Override
    protected AssetProfile constructAssetProfileFromUpdateMsg(TenantId tenantId, AssetProfileId assetProfileId, AssetProfileUpdateMsg assetProfileUpdateMsg) {
        return JacksonUtil.fromString(assetProfileUpdateMsg.getEntity(), AssetProfile.class, true);
    }

    @Override
    protected void setDefaultRuleChainId(TenantId tenantId, AssetProfile assetProfile, RuleChainId ruleChainId) {
        RuleChainId defaultRuleChainId = assetProfile.getDefaultEdgeRuleChainId();
        RuleChain ruleChain = null;
        if (defaultRuleChainId != null) {
            ruleChain = ruleChainService.findRuleChainById(tenantId, defaultRuleChainId);
        }
        assetProfile.setDefaultRuleChainId(ruleChain != null ? ruleChain.getId() : null);
    }

    @Override
    protected void setDefaultEdgeRuleChainId(AssetProfile assetProfile, RuleChainId ruleChainId, AssetProfileUpdateMsg assetProfileUpdateMsg) {
        assetProfile.setDefaultEdgeRuleChainId(null);
    }

    @Override
    protected void setDefaultDashboardId(TenantId tenantId, DashboardId dashboardId, AssetProfile assetProfile, AssetProfileUpdateMsg assetProfileUpdateMsg) {
        DashboardId defaultDashboardId = assetProfile.getDefaultDashboardId();
        DashboardInfo dashboard = null;
        if (defaultDashboardId != null) {
            dashboard = dashboardService.findDashboardInfoById(tenantId, defaultDashboardId);
        }
        assetProfile.setDefaultDashboardId(dashboard != null ? dashboard.getId() : null);
    }
}
