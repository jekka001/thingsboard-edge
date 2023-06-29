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
package org.thingsboard.server.service.edge.rpc.fetch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.edge.EdgeEventService;

@AllArgsConstructor
@Slf4j
public class GeneralEdgeEventFetcher implements EdgeEventFetcher {

    private final Long queueStartTs;
    private Long seqIdStart;
    @Getter
    private Long seqIdEnd;
    @Getter
    private boolean seqIdNewCycleStarted;
    private Long maxReadRecordsCount;
    private final EdgeEventService edgeEventService;

    @Override
    public PageLink getPageLink(int pageSize) {
        return new TimePageLink(
                pageSize,
                0,
                null,
                null,
                queueStartTs,
                System.currentTimeMillis());
    }

    @Override
    public PageData<EdgeEvent> fetchEdgeEvents(TenantId tenantId, Edge edge, PageLink pageLink) {
        try {
            PageData<EdgeEvent> edgeEvents = edgeEventService.findEdgeEvents(tenantId, edge.getId(), seqIdStart, seqIdEnd, (TimePageLink) pageLink);
            if (edgeEvents.getData().isEmpty()) {
                this.seqIdEnd = Math.max(this.maxReadRecordsCount, seqIdStart - this.maxReadRecordsCount);
                edgeEvents = edgeEventService.findEdgeEvents(tenantId, edge.getId(), 0L, seqIdEnd, (TimePageLink) pageLink);
                if (edgeEvents.getData().stream().anyMatch(ee -> ee.getSeqId() < seqIdStart)) {
                    log.info("[{}] seqId column of edge_event table started new cycle [{}]", tenantId, edge.getId());
                    this.seqIdNewCycleStarted = true;
                    this.seqIdStart = 0L;
                } else {
                    edgeEvents = new PageData<>();
                    log.warn("[{}] unexpected edge notification message received. " +
                            "no new events found and seqId column of edge_event table doesn't started new cycle [{}]", tenantId, edge.getId());
                }
            }
            return edgeEvents;
        } catch (Exception e) {
            log.error("[{}] failed to find edge events [{}]", tenantId, edge.getId());
        }
        return new PageData<>();
    }
}
