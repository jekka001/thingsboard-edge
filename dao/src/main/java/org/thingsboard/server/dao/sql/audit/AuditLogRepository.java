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
package org.thingsboard.server.dao.sql.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.dao.model.sql.AuditLogEntity;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
            "a.tenantId = :tenantId " +
            "AND (:startTime IS NULL OR a.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR a.createdTime <= :endTime) " +
            "AND ((:#{#actionTypes == null} = true) OR a.actionType IN (:actionTypes)) " + //HHH-15968
//            "AND ((:actionTypes) IS NULL OR a.actionType in (:actionTypes)) " +
            "AND (:textSearch IS NULL OR ilike(a.entityType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.entityName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.userName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionStatus, CONCAT('%', :textSearch, '%')) = true)"
    )
    Page<AuditLogEntity> findByTenantId(
                                 @Param("tenantId") UUID tenantId,
                                 @Param("textSearch") String textSearch,
                                 @Param("startTime") Long startTime,
                                 @Param("endTime") Long endTime,
                                 @Param("actionTypes") List<ActionType> actionTypes,
                                 Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
            "a.tenantId = :tenantId " +
            "AND a.entityType = :entityType AND a.entityId = :entityId " +
            "AND (:startTime IS NULL OR a.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR a.createdTime <= :endTime) " +
            "AND ((:#{#actionTypes == null} = true) OR a.actionType IN (:actionTypes)) " + //HHH-15968
//            "AND ((:actionTypes) IS NULL OR a.actionType in (:actionTypes)) " +
            "AND (:textSearch IS NULL OR ilike(a.entityName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.userName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionStatus, CONCAT('%', :textSearch, '%')) = true)"
    )
    Page<AuditLogEntity> findAuditLogsByTenantIdAndEntityId(@Param("tenantId") UUID tenantId,
                                                            @Param("entityType") EntityType entityType,
                                                            @Param("entityId") UUID entityId,
                                                            @Param("textSearch") String textSearch,
                                                            @Param("startTime") Long startTime,
                                                            @Param("endTime") Long endTime,
                                                            @Param("actionTypes") List<ActionType> actionTypes,
                                                            Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
            "a.tenantId = :tenantId " +
            "AND a.customerId = :customerId " +
            "AND (:startTime IS NULL OR a.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR a.createdTime <= :endTime) " +
            "AND ((:#{#actionTypes == null} = true) OR a.actionType IN (:actionTypes)) " + //HHH-15968
//            "AND ((:actionTypes) IS NULL OR a.actionType in (:actionTypes)) " +
            "AND (:textSearch IS NULL OR ilike(a.entityType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.entityName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.userName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionStatus, CONCAT('%', :textSearch, '%')) = true)"
    )
    Page<AuditLogEntity> findAuditLogsByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                                              @Param("customerId") UUID customerId,
                                                              @Param("textSearch") String textSearch,
                                                              @Param("startTime") Long startTime,
                                                              @Param("endTime") Long endTime,
                                                              @Param("actionTypes") List<ActionType> actionTypes,
                                                              Pageable pageable);

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
            "a.tenantId = :tenantId " +
            "AND a.userId = :userId " +
            "AND (:startTime IS NULL OR a.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR a.createdTime <= :endTime) " +
            "AND ((:#{#actionTypes == null} = true) OR a.actionType IN (:actionTypes)) " + //HHH-15968
//            "AND ((:actionTypes) IS NULL OR a.actionType in (:actionTypes)) " +
            "AND (:textSearch IS NULL OR ilike(a.entityType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.entityName, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionType, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.actionStatus, CONCAT('%', :textSearch, '%')) = true)"
    )
    Page<AuditLogEntity> findAuditLogsByTenantIdAndUserId(@Param("tenantId") UUID tenantId,
                                                          @Param("userId") UUID userId,
                                                          @Param("textSearch") String textSearch,
                                                          @Param("startTime") Long startTime,
                                                          @Param("endTime") Long endTime,
                                                          @Param("actionTypes") List<ActionType> actionTypes,
                                                          Pageable pageable);

}
