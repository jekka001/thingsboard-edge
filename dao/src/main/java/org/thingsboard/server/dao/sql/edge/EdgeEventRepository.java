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
package org.thingsboard.server.dao.sql.edge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.EdgeEventEntity;

import java.util.UUID;

public interface EdgeEventRepository extends PagingAndSortingRepository<EdgeEventEntity, UUID>, JpaSpecificationExecutor<EdgeEventEntity> {

    @Query("SELECT e FROM EdgeEventEntity e WHERE " +
            "e.tenantId = :tenantId " +
            "AND e.edgeId = :edgeId " +
            "AND (:startTime IS NULL OR e.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR e.createdTime <= :endTime) "
    )
    Page<EdgeEventEntity> findEdgeEventsByTenantIdAndEdgeId(@Param("tenantId") UUID tenantId,
                                                            @Param("edgeId") UUID edgeId,
                                                            @Param("startTime") Long startTime,
                                                            @Param("endTime") Long endTime,
                                                            Pageable pageable);

    @Query("SELECT e FROM EdgeEventEntity e WHERE " +
            "e.tenantId = :tenantId " +
            "AND e.edgeId = :edgeId " +
            "AND (:startTime IS NULL OR e.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR e.createdTime <= :endTime) " +
            "AND e.edgeEventAction <> 'TIMESERIES_UPDATED'"
    )
    Page<EdgeEventEntity> findEdgeEventsByTenantIdAndEdgeIdWithoutTimeseriesUpdated(@Param("tenantId") UUID tenantId,
                                                                                    @Param("edgeId") UUID edgeId,
                                                                                    @Param("startTime") Long startTime,
                                                                                    @Param("endTime") Long endTime,
                                                                                    Pageable pageable);
}
