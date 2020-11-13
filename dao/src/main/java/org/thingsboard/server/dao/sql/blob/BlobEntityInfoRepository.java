/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.sql.blob;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.BlobEntityInfoEntity;
import org.thingsboard.server.dao.model.sql.BlobEntityWithCustomerInfoEntity;

import java.util.List;
import java.util.UUID;

public interface BlobEntityInfoRepository extends CrudRepository<BlobEntityInfoEntity, UUID> {

    @Query("SELECT new org.thingsboard.server.dao.model.sql.BlobEntityWithCustomerInfoEntity(b, c.title, c.additionalInfo) " +
            "FROM BlobEntityInfoEntity b " +
            "LEFT JOIN CustomerEntity c on c.id = b.customerId " +
            "WHERE b.id = :blobEntityId")
    BlobEntityWithCustomerInfoEntity findBlobEntityWithCustomerInfoById(@Param("blobEntityId") UUID blobEntityId);

    List<BlobEntityInfoEntity> findBlobEntitiesByTenantIdAndIdIn(UUID tenantId, List<UUID> blobEntityIds);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.BlobEntityWithCustomerInfoEntity(b, c.title, c.additionalInfo) " +
            "FROM BlobEntityInfoEntity b " +
            "LEFT JOIN CustomerEntity c on c.id = b.customerId " +
            "WHERE b.tenantId = :tenantId " +
            "AND (:startTime IS NULL OR b.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR b.createdTime <= :endTime) " +
            "AND (LOWER(b.searchText) LIKE LOWER(CONCAT(:textSearch, '%')))"
    )
    Page<BlobEntityWithCustomerInfoEntity> findByTenantId(
            @Param("tenantId") UUID tenantId,
            @Param("textSearch") String textSearch,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.BlobEntityWithCustomerInfoEntity(b, c.title, c.additionalInfo) " +
            "FROM BlobEntityInfoEntity b " +
            "LEFT JOIN CustomerEntity c on c.id = b.customerId " +
            "WHERE b.tenantId = :tenantId " +
            "AND b.type = :type " +
            "AND (:startTime IS NULL OR b.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR b.createdTime <= :endTime) " +
            "AND (LOWER(b.searchText) LIKE LOWER(CONCAT(:textSearch, '%')))"
    )
    Page<BlobEntityWithCustomerInfoEntity> findByTenantIdAndType(
            @Param("tenantId") UUID tenantId,
            @Param("type") String type,
            @Param("textSearch") String textSearch,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.BlobEntityWithCustomerInfoEntity(b, c.title, c.additionalInfo) " +
            "FROM BlobEntityInfoEntity b " +
            "LEFT JOIN CustomerEntity c on c.id = b.customerId " +
            "WHERE b.tenantId = :tenantId " +
            "AND b.customerId = :customerId " +
            "AND (:startTime IS NULL OR b.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR b.createdTime <= :endTime) " +
            "AND (LOWER(b.searchText) LIKE LOWER(CONCAT(:textSearch, '%')))"
    )
    Page<BlobEntityWithCustomerInfoEntity> findByTenantIdAndCustomerId(
            @Param("tenantId") UUID tenantId,
            @Param("customerId") UUID customerId,
            @Param("textSearch") String textSearch,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.BlobEntityWithCustomerInfoEntity(b, c.title, c.additionalInfo) " +
            "FROM BlobEntityInfoEntity b " +
            "LEFT JOIN CustomerEntity c on c.id = b.customerId " +
            "WHERE b.tenantId = :tenantId " +
            "AND b.customerId = :customerId " +
            "AND b.type = :type " +
            "AND (:startTime IS NULL OR b.createdTime >= :startTime) " +
            "AND (:endTime IS NULL OR b.createdTime <= :endTime) " +
            "AND (LOWER(b.searchText) LIKE LOWER(CONCAT(:textSearch, '%')))"
    )
    Page<BlobEntityWithCustomerInfoEntity> findByTenantIdAndCustomerIdAndType(
            @Param("tenantId") UUID tenantId,
            @Param("customerId") UUID customerId,
            @Param("type") String type,
            @Param("textSearch") String textSearch,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable);

}
