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
package org.thingsboard.server.service.cf;

import com.google.common.util.concurrent.FutureCallback;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.AttributesSaveRequest;
import org.thingsboard.rule.engine.api.TimeseriesSaveRequest;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.cf.CalculatedFieldLink;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CalculatedFieldId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.TimeseriesSaveResult;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.util.ProtoUtils;
import org.thingsboard.server.gen.transport.TransportProtos.AttributeScopeProto;
import org.thingsboard.server.gen.transport.TransportProtos.AttributeValueProto;
import org.thingsboard.server.gen.transport.TransportProtos.CalculatedFieldIdProto;
import org.thingsboard.server.gen.transport.TransportProtos.CalculatedFieldTelemetryMsgProto;
import org.thingsboard.server.gen.transport.TransportProtos.ToCalculatedFieldMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TsKvProto;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.service.cf.ctx.state.CalculatedFieldCtx;
import org.thingsboard.server.service.profile.TbAssetProfileCache;
import org.thingsboard.server.service.profile.TbDeviceProfileCache;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.thingsboard.server.common.util.ProtoUtils.toTsKvProto;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultCalculatedFieldQueueService implements CalculatedFieldQueueService {

    public static final TbQueueCallback DUMMY_TB_QUEUE_CALLBACK = new TbQueueCallback() {
        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
        }

        @Override
        public void onFailure(Throwable t) {
        }
    };

    private final TbAssetProfileCache assetProfileCache;
    private final TbDeviceProfileCache deviceProfileCache;
    private final CalculatedFieldCache calculatedFieldCache;
    private final TbClusterService clusterService;

    private static final Set<EntityType> supportedReferencedEntities = EnumSet.of(
            EntityType.DEVICE, EntityType.ASSET, EntityType.CUSTOMER, EntityType.TENANT
    );

    @Value("${calculatedField.initFetchPackSize:50000}")
    @Getter
    private int initFetchPackSize;

    @Override
    public void pushRequestToQueue(TimeseriesSaveRequest request, TimeseriesSaveResult result, FutureCallback<Void> callback) {
        var tenantId = request.getTenantId();
        var entityId = request.getEntityId();
        //TODO: 1. check that request entity has calculated fields for entity or profile. If yes - push to corresponding partitions;
        //TODO: 2. check that request entity has calculated field links. If yes - push to corresponding partitions;
        //TODO: in 1 and 2 we should do the check as quick as possible. Should we also check the field/link keys?;
        checkEntityAndPushToQueue(tenantId, entityId, cf -> cf.matches(request.getEntries()), cf -> cf.linkMatches(entityId, request.getEntries()),
                () -> toCalculatedFieldTelemetryMsgProto(request, result), callback);
    }

    @Override
    public void pushRequestToQueue(AttributesSaveRequest request, List<Long> result, FutureCallback<Void> callback) {
        var tenantId = request.getTenantId();
        var entityId = request.getEntityId();
        checkEntityAndPushToQueue(tenantId, entityId, cf -> cf.matches(request.getEntries(), request.getScope()), cf -> cf.linkMatches(entityId, request.getEntries(), request.getScope()),
                () -> toCalculatedFieldTelemetryMsgProto(request, result), callback);
    }

    private void checkEntityAndPushToQueue(TenantId tenantId, EntityId entityId,
                                           Predicate<CalculatedFieldCtx> mainEntityFilter, Predicate<CalculatedFieldCtx> linkedEntityFilter,
                                           Supplier<ToCalculatedFieldMsg> msg, FutureCallback<Void> callback) {
        boolean send = checkEntityForCalculatedFields(tenantId, entityId, mainEntityFilter, linkedEntityFilter);
        if (send) {
            clusterService.pushMsgToCalculatedFields(tenantId, entityId, msg.get(), wrap(callback));
        } else {
            if (callback != null) {
                callback.onSuccess(null);
            }
        }
    }

    private boolean checkEntityForCalculatedFields(TenantId tenantId, EntityId entityId, Predicate<CalculatedFieldCtx> filter, Predicate<CalculatedFieldCtx> linkedEntityFilter) {
        boolean send = false;
        if (supportedReferencedEntities.contains(entityId.getEntityType())) {
            send = calculatedFieldCache.getCalculatedFieldCtxsByEntityId(entityId).stream().anyMatch(filter);
            if (!send) {
                send = calculatedFieldCache.getCalculatedFieldCtxsByEntityId(getProfileId(tenantId, entityId)).stream().anyMatch(filter);
            }
            if (!send) {
                send = calculatedFieldCache.getCalculatedFieldLinksByEntityId(entityId).stream()
                        .map(CalculatedFieldLink::getCalculatedFieldId)
                        .map(calculatedFieldCache::getCalculatedFieldCtx)
                        .anyMatch(linkedEntityFilter);
            }
        }
        return send;
    }

    private EntityId getProfileId(TenantId tenantId, EntityId entityId) {
        return switch (entityId.getEntityType()) {
            case ASSET -> assetProfileCache.get(tenantId, (AssetId) entityId).getId();
            case DEVICE -> deviceProfileCache.get(tenantId, (DeviceId) entityId).getId();
            default -> null;
        };
    }

    private ToCalculatedFieldMsg toCalculatedFieldTelemetryMsgProto(TimeseriesSaveRequest request, TimeseriesSaveResult result) {
        ToCalculatedFieldMsg.Builder msg = ToCalculatedFieldMsg.newBuilder();

        CalculatedFieldTelemetryMsgProto.Builder telemetryMsg = buildTelemetryMsgProto(request.getTenantId(), request.getEntityId(), request.getPreviousCalculatedFieldIds(), request.getTbMsgId(), request.getTbMsgType());
        List<TsKvEntry> entries = request.getEntries();
        List<Long> versions = result.getVersions();
        for (int i = 0; i < entries.size(); i++) {
            long tsVersion = versions.get(i);
            TsKvProto tsProto = toTsKvProto(entries.get(i)).toBuilder().setVersion(tsVersion).build();
            telemetryMsg.addTsData(tsProto);
        }
        msg.setTelemetryMsg(telemetryMsg.build());

        return msg.build();
    }

    private ToCalculatedFieldMsg toCalculatedFieldTelemetryMsgProto(AttributesSaveRequest request, List<Long> versions) {
        ToCalculatedFieldMsg.Builder msg = ToCalculatedFieldMsg.newBuilder();

        CalculatedFieldTelemetryMsgProto.Builder telemetryMsg = buildTelemetryMsgProto(request.getTenantId(), request.getEntityId(), request.getPreviousCalculatedFieldIds(), request.getTbMsgId(), request.getTbMsgType());
        telemetryMsg.setScope(AttributeScopeProto.valueOf(request.getScope().name()));
        List<AttributeKvEntry> entries = request.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            long attrVersion = versions.get(i);
            AttributeValueProto attrProto = ProtoUtils.toProto(entries.get(i)).toBuilder().setVersion(attrVersion).build();
            telemetryMsg.addAttrData(attrProto);
        }
        msg.setTelemetryMsg(telemetryMsg.build());

        return msg.build();
    }

    private CalculatedFieldTelemetryMsgProto.Builder buildTelemetryMsgProto(TenantId tenantId, EntityId entityId, List<CalculatedFieldId> calculatedFieldIds, UUID tbMsgId, TbMsgType tbMsgType) {
        CalculatedFieldTelemetryMsgProto.Builder telemetryMsg = CalculatedFieldTelemetryMsgProto.newBuilder();

        telemetryMsg.setTenantIdMSB(tenantId.getId().getMostSignificantBits());
        telemetryMsg.setTenantIdLSB(tenantId.getId().getLeastSignificantBits());

        telemetryMsg.setEntityType(entityId.getEntityType().name());
        telemetryMsg.setEntityIdMSB(entityId.getId().getMostSignificantBits());
        telemetryMsg.setEntityIdLSB(entityId.getId().getLeastSignificantBits());

        if (calculatedFieldIds != null) {
            for (CalculatedFieldId cfId : calculatedFieldIds) {
                telemetryMsg.addPreviousCalculatedFields(toProto(cfId));
            }
        }

        if (tbMsgId != null) {
            telemetryMsg.setTbMsgIdMSB(tbMsgId.getMostSignificantBits());
            telemetryMsg.setTbMsgIdLSB(tbMsgId.getLeastSignificantBits());
        }

        if (tbMsgType != null) {
            telemetryMsg.setTbMsgType(tbMsgType.name());
        }

        return telemetryMsg;
    }

    private CalculatedFieldIdProto toProto(CalculatedFieldId cfId) {
        return CalculatedFieldIdProto.newBuilder()
                .setCalculatedFieldIdMSB(cfId.getId().getMostSignificantBits())
                .setCalculatedFieldIdLSB(cfId.getId().getLeastSignificantBits())
                .build();
    }

    private static TbQueueCallback wrap(FutureCallback<Void> callback) {
        if (callback != null) {
            return new FutureCallbackWrapper(callback);
        } else {
            return DUMMY_TB_QUEUE_CALLBACK;
        }
    }

    private static class FutureCallbackWrapper implements TbQueueCallback {
        private final FutureCallback<Void> callback;

        public FutureCallbackWrapper(FutureCallback<Void> callback) {
            this.callback = callback;
        }

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            callback.onSuccess(null);
        }

        @Override
        public void onFailure(Throwable t) {
            callback.onFailure(t);
        }
    }

}
