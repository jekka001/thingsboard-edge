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
package org.thingsboard.server.dao.sql.scheduler;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.common.data.edqs.fields.SchedulerEventFields;
import org.thingsboard.server.dao.model.sql.SchedulerEventEntity;

import java.util.List;
import java.util.UUID;

public interface SchedulerEventRepository extends JpaRepository<SchedulerEventEntity, UUID> {

    Long countByTenantId(UUID tenantId);

    @Query("SELECT se FROM SchedulerEventEntity se, RelationEntity re WHERE se.tenantId = :tenantId " +
            "AND se.id = re.toId AND re.toType = 'SCHEDULER_EVENT' AND re.relationTypeGroup = 'EDGE' " +
            "AND re.relationType = 'Contains' AND re.fromId = :edgeId AND re.fromType = 'EDGE' " +
            "AND (:searchText IS NULL OR ilike(se.name, CONCAT('%', :searchText, '%')) = true)")
    Page<SchedulerEventEntity> findByTenantIdAndEdgeId(@Param("tenantId") UUID tenantId,
                                                       @Param("edgeId") UUID edgeId,
                                                       @Param("searchText") String searchText,
                                                       Pageable pageable);

    Page<SchedulerEventEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("SELECT new org.thingsboard.server.common.data.edqs.fields.SchedulerEventFields(e.id, e.createdTime, " +
            "e.tenantId, e.customerId, e.name, e.version, e.type, e.additionalInfo, e.originatorId, e.originatorType) " +
            "FROM SchedulerEventEntity e WHERE e.id > :id ORDER BY e.id")
    List<SchedulerEventFields> findNextBatch(@Param("id") UUID id, Limit limit);

}
