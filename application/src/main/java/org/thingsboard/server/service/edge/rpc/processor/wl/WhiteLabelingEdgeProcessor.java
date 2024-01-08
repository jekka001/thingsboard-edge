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
package org.thingsboard.server.service.edge.rpc.processor.wl;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingType;
import org.thingsboard.server.gen.edge.v1.CustomMenuProto;
import org.thingsboard.server.gen.edge.v1.CustomTranslationProto;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.LoginWhiteLabelingParamsProto;
import org.thingsboard.server.gen.edge.v1.WhiteLabelingParamsProto;
import org.thingsboard.server.gen.edge.v1.WhiteLabelingProto;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.edge.rpc.constructor.translation.CustomTranslationMsgConstructor;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;
import org.thingsboard.server.service.edge.rpc.utils.EdgeVersionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@TbCoreComponent
public class WhiteLabelingEdgeProcessor extends BaseEdgeProcessor {

    public DownlinkMsg convertWhiteLabelingEventToDownlink(EdgeEvent edgeEvent, EdgeVersion edgeVersion) {
        DownlinkMsg result = null;
        try {
            EntityId entityId = JacksonUtil.convertValue(edgeEvent.getBody(), EntityId.class);
            if (entityId == null) {
                return null;
            }
            if (EdgeVersionUtils.isEdgeVersionOlderThan(edgeVersion, EdgeVersion.V_3_6_1)) {
                return constructDeprecatedWhiteLabelingEvent(edgeEvent, entityId);
            }
            TenantId tenantId = EntityType.TENANT.equals(entityId.getEntityType()) ? (TenantId) entityId : edgeEvent.getTenantId();
            CustomerId customerId = EntityType.CUSTOMER.equals(entityId.getEntityType()) ? new CustomerId(entityId.getId()) : null;
            WhiteLabeling whiteLabeling = whiteLabelingService.findByEntityId(tenantId, customerId, getWhiteLabelingType(edgeEvent.getType()));
            if (whiteLabeling == null) {
                return null;
            }
            boolean isEdgeVersionOlderThan_3_6_2 = EdgeVersionUtils.isEdgeVersionOlderThan(edgeVersion, EdgeVersion.V_3_6_2);
            WhiteLabelingProto whiteLabelingProto = whiteLabelingParamsProtoConstructor.constructWhiteLabeling(whiteLabeling, isEdgeVersionOlderThan_3_6_2);
            result = DownlinkMsg.newBuilder()
                    .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                    .setWhiteLabelingProto(whiteLabelingProto)
                    .build();
        } catch (Exception e) {
            log.error("Can't process white labeling msg [{}]", edgeEvent, e);
        }
        return result;
    }

    private WhiteLabelingType getWhiteLabelingType(EdgeEventType type) {
        switch (type) {
            case WHITE_LABELING:
                return WhiteLabelingType.GENERAL;
            case MAIL_TEMPLATES:
                return WhiteLabelingType.MAIL_TEMPLATES;
            default:
                return null;
        }
    }

    private DownlinkMsg constructDeprecatedWhiteLabelingEvent(EdgeEvent edgeEvent, EntityId entityId) {
        DownlinkMsg result = null;
        switch (entityId.getEntityType()) {
            case TENANT:
                if (TenantId.SYS_TENANT_ID.equals(entityId)) {
                    WhiteLabelingParams systemWhiteLabelingParams =
                            whiteLabelingService.getSystemWhiteLabelingParams();
                    if (isDefaultWhiteLabeling(systemWhiteLabelingParams)) {
                        return null;
                    }
                    WhiteLabelingParamsProto whiteLabelingParamsProto =
                            whiteLabelingParamsProtoConstructor.constructWhiteLabelingParamsProto(TenantId.SYS_TENANT_ID, systemWhiteLabelingParams, entityId);
                    result = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .setSystemWhiteLabelingParams(whiteLabelingParamsProto)
                            .build();
                } else {
                    WhiteLabelingParams tenantWhiteLabelingParams =
                            whiteLabelingService.getTenantWhiteLabelingParams(edgeEvent.getTenantId());
                    if (isDefaultWhiteLabeling(tenantWhiteLabelingParams)) {
                        return null;
                    }
                    WhiteLabelingParamsProto whiteLabelingParamsProto =
                            whiteLabelingParamsProtoConstructor.constructWhiteLabelingParamsProto(TenantId.fromUUID(entityId.getId()), tenantWhiteLabelingParams, entityId);
                    result = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .setTenantWhiteLabelingParams(whiteLabelingParamsProto)
                            .build();
                }
                break;
            case CUSTOMER:
                CustomerId customerId = new CustomerId(entityId.getId());
                WhiteLabelingParams customerWhiteLabelingParams =
                        whiteLabelingService.getCustomerWhiteLabelingParams(edgeEvent.getTenantId(), customerId);
                if (isDefaultWhiteLabeling(customerWhiteLabelingParams)) {
                    return null;
                }
                WhiteLabelingParamsProto whiteLabelingParamsProto =
                        whiteLabelingParamsProtoConstructor.constructWhiteLabelingParamsProto(edgeEvent.getTenantId(), customerWhiteLabelingParams, customerId);
                result = DownlinkMsg.newBuilder()
                        .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                        .setCustomerWhiteLabelingParams(whiteLabelingParamsProto)
                        .build();
        }
        return result;
    }

    private boolean isDefaultWhiteLabeling(WhiteLabelingParams whiteLabelingParams) {
        return new WhiteLabelingParams().equals(whiteLabelingParams);
    }

    public DownlinkMsg convertLoginWhiteLabelingEventToDownlink(EdgeEvent edgeEvent, EdgeVersion edgeVersion) {
        DownlinkMsg result = null;
        try {
            EntityId entityId = JacksonUtil.convertValue(edgeEvent.getBody(), EntityId.class);
            if (entityId == null) {
                return null;
            }
            if (EdgeVersionUtils.isEdgeVersionOlderThan(edgeVersion, EdgeVersion.V_3_6_1)) {
                return constructDeprecatedLoginWhiteLabelingEvent(edgeEvent, entityId);
            }
            TenantId tenantId = EntityType.TENANT.equals(entityId.getEntityType()) ? (TenantId) entityId : edgeEvent.getTenantId();
            CustomerId customerId = EntityType.CUSTOMER.equals(entityId.getEntityType()) ? new CustomerId(entityId.getId()) : null;
            WhiteLabeling whiteLabeling = whiteLabelingService.findByEntityId(tenantId, customerId, WhiteLabelingType.LOGIN);
            if (whiteLabeling == null) {
                return null;
            }
            boolean isEdgeVersionOlderThan_3_6_2 = EdgeVersionUtils.isEdgeVersionOlderThan(edgeVersion, EdgeVersion.V_3_6_2);
            WhiteLabelingProto whiteLabelingProto = whiteLabelingParamsProtoConstructor.constructWhiteLabeling(whiteLabeling, isEdgeVersionOlderThan_3_6_2);
            result = DownlinkMsg.newBuilder()
                    .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                    .setWhiteLabelingProto(whiteLabelingProto)
                    .build();
        } catch (Exception e) {
            log.error("Can't process login white labeling msg [{}]", edgeEvent, e);
        }
        return result;
    }

    private DownlinkMsg constructDeprecatedLoginWhiteLabelingEvent(EdgeEvent edgeEvent, EntityId entityId) throws Exception {
        DownlinkMsg result = null;
        switch (entityId.getEntityType()) {
            case TENANT:
                if (TenantId.SYS_TENANT_ID.equals(entityId)) {
                    LoginWhiteLabelingParams systemLoginWhiteLabelingParams =
                            whiteLabelingService.getSystemLoginWhiteLabelingParams();
                    if (isDefaultLoginWhiteLabeling(systemLoginWhiteLabelingParams)) {
                        return null;
                    }
                    LoginWhiteLabelingParamsProto loginWhiteLabelingParamsProto =
                            whiteLabelingParamsProtoConstructor.constructLoginWhiteLabelingParamsProto(TenantId.SYS_TENANT_ID, systemLoginWhiteLabelingParams, entityId);
                    result = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .setSystemLoginWhiteLabelingParams(loginWhiteLabelingParamsProto)
                            .build();
                } else {
                    LoginWhiteLabelingParams tenantLoginWhiteLabelingParams =
                            whiteLabelingService.getTenantLoginWhiteLabelingParams(edgeEvent.getTenantId());
                    if (isDefaultLoginWhiteLabeling(tenantLoginWhiteLabelingParams)) {
                        return null;
                    }
                    LoginWhiteLabelingParamsProto loginWhiteLabelingParamsProto =
                            whiteLabelingParamsProtoConstructor.constructLoginWhiteLabelingParamsProto(TenantId.fromUUID(entityId.getId()), tenantLoginWhiteLabelingParams, entityId);
                    result = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .setTenantLoginWhiteLabelingParams(loginWhiteLabelingParamsProto)
                            .build();
                }
                break;
            case CUSTOMER:
                CustomerId customerId = new CustomerId(entityId.getId());
                LoginWhiteLabelingParams customerLoginWhiteLabelingParams =
                        whiteLabelingService.getCustomerLoginWhiteLabelingParams(edgeEvent.getTenantId(), customerId);
                if (isDefaultLoginWhiteLabeling(customerLoginWhiteLabelingParams)) {
                    return null;
                }
                LoginWhiteLabelingParamsProto loginWhiteLabelingParamsProto =
                        whiteLabelingParamsProtoConstructor.constructLoginWhiteLabelingParamsProto(edgeEvent.getTenantId(), customerLoginWhiteLabelingParams, customerId);
                result = DownlinkMsg.newBuilder()
                        .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                        .setCustomerLoginWhiteLabelingParams(loginWhiteLabelingParamsProto)
                        .build();
        }
        return result;
    }

    private boolean isDefaultLoginWhiteLabeling(LoginWhiteLabelingParams loginWhiteLabelingParams) {
        return new LoginWhiteLabelingParams().equals(loginWhiteLabelingParams);
    }

    public DownlinkMsg convertCustomTranslationEventToDownlink(EdgeEvent edgeEvent, EdgeVersion edgeVersion) {
        DownlinkMsg result = null;
        try {
            EntityId entityId = JacksonUtil.convertValue(edgeEvent.getBody(), EntityId.class);
            if (entityId == null) {
                return null;
            }
            switch (entityId.getEntityType()) {
                case TENANT:
                    if (TenantId.SYS_TENANT_ID.equals(entityId)) {
                        CustomTranslation systemCustomTranslation =
                                customTranslationService.getSystemCustomTranslation(edgeEvent.getTenantId());
                        if (isDefaultCustomTranslation(systemCustomTranslation)) {
                            return null;
                        }
                        CustomTranslationProto customTranslationProto = ((CustomTranslationMsgConstructor)
                                customTranslationConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructCustomTranslationProto(systemCustomTranslation, entityId);
                        result = DownlinkMsg.newBuilder()
                                .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                                .setSystemCustomTranslationMsg(customTranslationProto)
                                .build();
                    } else {
                        CustomTranslation tenantCustomTranslation =
                                customTranslationService.getTenantCustomTranslation(edgeEvent.getTenantId());
                        if (isDefaultCustomTranslation(tenantCustomTranslation)) {
                            return null;
                        }
                        CustomTranslationProto customTranslationProto = ((CustomTranslationMsgConstructor)
                                customTranslationConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructCustomTranslationProto(tenantCustomTranslation, entityId);
                        result = DownlinkMsg.newBuilder()
                                .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                                .setTenantCustomTranslationMsg(customTranslationProto)
                                .build();
                    }
                    break;
                case CUSTOMER:
                    CustomerId customerId = new CustomerId(entityId.getId());
                    CustomTranslation customerCustomTranslation =
                            customTranslationService.getCustomerCustomTranslation(edgeEvent.getTenantId(), customerId);
                    if (isDefaultCustomTranslation(customerCustomTranslation)) {
                        return null;
                    }
                    CustomTranslationProto customTranslationProto = ((CustomTranslationMsgConstructor)
                            customTranslationConstructorFactory.getMsgConstructorByEdgeVersion(edgeVersion)).constructCustomTranslationProto(customerCustomTranslation, customerId);
                    result = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .setCustomerCustomTranslationMsg(customTranslationProto)
                            .build();
            }
        } catch (Exception e) {
            log.error("Can't process custom translation msg [{}]", edgeEvent, e);
        }
        return result;
    }

    private boolean isDefaultCustomTranslation(CustomTranslation customTranslation) {
        return new CustomTranslation().equals(customTranslation);
    }

    public DownlinkMsg convertCustomMenuEventToDownlink(EdgeEvent edgeEvent) {
        DownlinkMsg downlinkMsg = null;
        try {
            EntityId entityId = JacksonUtil.convertValue(edgeEvent.getBody(), EntityId.class);
            if (entityId == null || TenantId.SYS_TENANT_ID.equals(entityId)) {
                return null;
            }
            CustomMenu customMenu = getCustomMenuForEntity(edgeEvent.getTenantId(), entityId);
            if (customMenu == null) {
                customMenu = new CustomMenu();
            }
            CustomMenuProto customMenuProto = customMenuMsgConstructor.constructCustomMenuProto(customMenu, entityId);
            if (customMenuProto != null) {
                downlinkMsg = DownlinkMsg.newBuilder()
                        .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                        .setCustomMenuProto(customMenuProto)
                        .build();
            }
        } catch (Exception e) {
            log.error("Can't process custom menu msg [{}]", edgeEvent, e);
        }
        return downlinkMsg;
    }

    private CustomMenu getCustomMenuForEntity(TenantId tenantId, EntityId entityId) {
        switch (entityId.getEntityType()) {
            case TENANT:
                return customMenuService.getTenantCustomMenu(tenantId);
            case CUSTOMER:
                return customMenuService.getCustomerCustomMenu(tenantId, new CustomerId(entityId.getId()));
            default:
                return null;
        }
    }

    public ListenableFuture<Void> processNotification(TenantId tenantId, TransportProtos.EdgeNotificationMsgProto edgeNotificationMsg) {
        EdgeEventActionType actionType = EdgeEventActionType.valueOf(edgeNotificationMsg.getAction());
        EdgeEventType type = EdgeEventType.valueOf(edgeNotificationMsg.getType());
        EntityId entityId = EntityIdFactory.getByEdgeEventTypeAndUuid(EdgeEventType.valueOf(edgeNotificationMsg.getEntityType()),
                new UUID(edgeNotificationMsg.getEntityIdMSB(), edgeNotificationMsg.getEntityIdLSB()));
        EdgeId sourceEdgeId = safeGetEdgeId(edgeNotificationMsg.getOriginatorEdgeIdMSB(), edgeNotificationMsg.getOriginatorEdgeIdLSB());
        switch (entityId.getEntityType()) {
            case TENANT:
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                if (TenantId.SYS_TENANT_ID.equals(tenantId)) {
                    PageLink pageLink = new PageLink(DEFAULT_PAGE_SIZE);
                    PageData<TenantId> tenantsIds;
                    do {
                        tenantsIds = tenantService.findTenantsIds(pageLink);
                        for (TenantId tenantId1 : tenantsIds.getData()) {
                            futures.addAll(processActionForAllEdgesByTenantId(tenantId1, type, actionType, null, JacksonUtil.valueToTree(entityId), sourceEdgeId, null));
                        }
                        pageLink = pageLink.nextPageLink();
                    } while (tenantsIds.hasNext());
                } else {
                    futures = processActionForAllEdgesByTenantId(tenantId, type, actionType, null, JacksonUtil.valueToTree(entityId), sourceEdgeId, null);
                }
                return Futures.transform(Futures.allAsList(futures), voids -> null, dbCallbackExecutorService);
            case CUSTOMER:
                if (EdgeEventActionType.UPDATED.equals(actionType)) {
                    List<EdgeId> edgesByCustomerId =
                            customersHierarchyEdgeService.findAllEdgesInHierarchyByCustomerId(tenantId, new CustomerId(entityId.getId()));
                    if (edgesByCustomerId != null) {
                        for (EdgeId edgeId : edgesByCustomerId) {
                            saveEdgeEvent(tenantId, edgeId, type, actionType, null, JacksonUtil.valueToTree(entityId));
                        }
                    }
                }
                break;
        }
        return Futures.immediateFuture(null);
    }
}
