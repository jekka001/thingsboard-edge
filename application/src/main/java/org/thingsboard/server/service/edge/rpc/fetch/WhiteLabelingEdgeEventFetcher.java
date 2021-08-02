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
package org.thingsboard.server.service.edge.rpc.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.translation.CustomTranslationService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.service.edge.rpc.EdgeEventUtils;

import java.util.ArrayList;
import java.util.List;


@AllArgsConstructor
@Slf4j
public class WhiteLabelingEdgeEventFetcher implements EdgeEventFetcher {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final WhiteLabelingService whiteLabelingService;
    private final CustomTranslationService customTranslationService;

    @Override
    public PageLink getPageLink(int pageSize) {
        return null;
    }

    @Override
    public PageData<EdgeEvent> fetchEdgeEvents(TenantId tenantId, Edge edge, PageLink pageLink) throws Exception {
        List<EdgeEvent> result = new ArrayList<>();
        List<EdgeEvent> loginWhiteLabelingEdgeEvents = getLoginWhiteLabelingEdgeEvents(tenantId, edge);
        if (!loginWhiteLabelingEdgeEvents.isEmpty()) {
            result.addAll(loginWhiteLabelingEdgeEvents);
        }
        List<EdgeEvent> whiteLabelingEdgeEvents = getWhiteLabelingEdgeEvents(tenantId, edge);
        if (!whiteLabelingEdgeEvents.isEmpty()) {
            result.addAll(whiteLabelingEdgeEvents);
        }
        List<EdgeEvent> customTranslationEdgeEvents = getCustomTranslationEdgeEvents(tenantId, edge);
        if (!customTranslationEdgeEvents.isEmpty()) {
            result.addAll(customTranslationEdgeEvents);
        }
        // @voba - returns PageData object to be in sync with other fetchers
        return new PageData<>(result, 1, result.size(), false);
    }

    private List<EdgeEvent> getLoginWhiteLabelingEdgeEvents(TenantId tenantId, Edge edge) {
        try {
            EntityId ownerId = edge.getOwnerId();
            List<EdgeEvent> result = new ArrayList<>();
            result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                    EdgeEventType.LOGIN_WHITE_LABELING, EdgeEventActionType.UPDATED, null, mapper.valueToTree(new TenantId(EntityId.NULL_UUID))));
            result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                    EdgeEventType.LOGIN_WHITE_LABELING, EdgeEventActionType.UPDATED, null, mapper.valueToTree(tenantId)));
            if (EntityType.CUSTOMER.equals(ownerId.getEntityType())) {
                CustomerId customerId = new CustomerId(ownerId.getId());
                result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                        EdgeEventType.LOGIN_WHITE_LABELING, EdgeEventActionType.UPDATED, null, mapper.valueToTree(customerId)));
            }
            return result;
        } catch (Exception e) {
            log.error("Can't load login white labeling params", e);
            throw new RuntimeException(e);
        }
    }

    private List<EdgeEvent> getWhiteLabelingEdgeEvents(TenantId tenantId, Edge edge) {
        try {
            EntityId ownerId = edge.getOwnerId();
            List<EdgeEvent> result = new ArrayList<>();
            result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                    EdgeEventType.WHITE_LABELING, EdgeEventActionType.UPDATED, null, mapper.valueToTree(new TenantId(EntityId.NULL_UUID))));
            result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                    EdgeEventType.WHITE_LABELING, EdgeEventActionType.UPDATED, null, mapper.valueToTree(tenantId)));
            if (EntityType.CUSTOMER.equals(ownerId.getEntityType())) {
                CustomerId customerId = new CustomerId(ownerId.getId());
                result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                        EdgeEventType.WHITE_LABELING, EdgeEventActionType.UPDATED, null, mapper.valueToTree(customerId)));
            }
            return result;
        } catch (Exception e) {
            log.error("Can't load white labeling params", e);
            throw new RuntimeException(e);
        }
    }

    private List<EdgeEvent> getCustomTranslationEdgeEvents(TenantId tenantId, Edge edge) {
        try {

            EntityId ownerId = edge.getOwnerId();
            List<EdgeEvent> result = new ArrayList<>();
            result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                    EdgeEventType.CUSTOM_TRANSLATION, EdgeEventActionType.UPDATED, null, mapper.valueToTree(new TenantId(EntityId.NULL_UUID))));
            result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                    EdgeEventType.CUSTOM_TRANSLATION, EdgeEventActionType.UPDATED, null, mapper.valueToTree(tenantId)));
            if (EntityType.CUSTOMER.equals(ownerId.getEntityType())) {
                CustomerId customerId = new CustomerId(ownerId.getId());
                result.add(EdgeEventUtils.constructEdgeEvent(tenantId, edge.getId(),
                        EdgeEventType.CUSTOM_TRANSLATION, EdgeEventActionType.UPDATED, null, mapper.valueToTree(customerId)));
            }
            return result;
        } catch (Exception e) {
            log.error("Can't load custom translation", e);
            throw new RuntimeException(e);
        }
    }

}




