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
package org.thingsboard.server.dao.sql.resource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.TbResourceInfoEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TbResourceInfoRepository extends JpaRepository<TbResourceInfoEntity, UUID> {

    @Query("SELECT tr FROM TbResourceInfoEntity tr WHERE " +
            "(:searchText IS NULL OR ilike(tr.title, CONCAT('%', :searchText, '%')) = true) " +
            "AND ((tr.tenantId = :tenantId AND (tr.customerId IS NULL OR tr.customerId = org.thingsboard.server.common.data.id.EntityId.NULL_UUID))" +
            "OR (tr.tenantId = :systemTenantId " +
            "AND NOT EXISTS " +
            "(SELECT sr FROM TbResourceEntity sr " +
            "WHERE sr.tenantId = :tenantId " +
            "AND tr.resourceType = sr.resourceType " +
            "AND tr.resourceKey = sr.resourceKey)))" +
            "AND tr.resourceType IN :resourceTypes " +
            "AND (:resourceSubTypes IS NULL OR tr.resourceSubType IN :resourceSubTypes)")
    Page<TbResourceInfoEntity> findAllTenantResourcesByTenantId(@Param("tenantId") UUID tenantId,
                                                                @Param("systemTenantId") UUID systemTenantId,
                                                                @Param("resourceTypes") List<String> resourceTypes,
                                                                @Param("resourceSubTypes") List<String> resourceSubTypes,
                                                                @Param("searchText") String searchText,
                                                                Pageable pageable);

    @Query("SELECT ri FROM TbResourceInfoEntity ri WHERE " +
            "ri.tenantId = :tenantId AND (ri.customerId IS NULL OR ri.customerId = org.thingsboard.server.common.data.id.EntityId.NULL_UUID) " +
            "AND ri.resourceType IN :resourceTypes " +
            "AND (:resourceSubTypes IS NULL OR ri.resourceSubType IN :resourceSubTypes) " +
            "AND (:searchText IS NULL OR ilike(ri.title, CONCAT('%', :searchText, '%')) = true)")
    Page<TbResourceInfoEntity> findTenantResourcesByTenantId(@Param("tenantId") UUID tenantId,
                                                             @Param("resourceTypes") List<String> resourceTypes,
                                                             @Param("resourceSubTypes") List<String> resourceSubTypes,
                                                             @Param("searchText") String searchText,
                                                             Pageable pageable);

    @Query("SELECT ri FROM TbResourceInfoEntity ri WHERE " +
            "ri.tenantId = :tenantId AND ri.customerId = :customerId " +
            "AND ri.resourceType IN :resourceTypes " +
            "AND (:resourceSubTypes IS NULL OR ri.resourceSubType IN :resourceSubTypes) " +
            "AND (:searchText IS NULL OR ilike(ri.title, CONCAT('%', :searchText, '%')) = true)")
    Page<TbResourceInfoEntity> findTenantResourcesByCustomerId(@Param("tenantId") UUID tenantId,
                                                               @Param("customerId") UUID customerId,
                                                               @Param("resourceTypes") List<String> resourceTypes,
                                                               @Param("resourceSubTypes") List<String> resourceSubTypes,
                                                               @Param("searchText") String searchText,
                                                               Pageable pageable);

    TbResourceInfoEntity findByTenantIdAndResourceTypeAndResourceKey(UUID tenantId, String resourceType, String resourceKey);

    TbResourceInfoEntity findByTenantIdAndCustomerIdAndResourceTypeAndResourceKey(UUID tenantId, UUID customerId, String resourceType, String resourceKey);

    boolean existsByTenantIdAndResourceTypeAndResourceKey(UUID tenantId, String resourceType, String resourceKey);

    @Query(value = "SELECT r.resource_key FROM resource r WHERE r.tenant_id = :tenantId AND r.resource_type = :resourceType " +
            "AND starts_with(r.resource_key, :prefix)", nativeQuery = true)
    Set<String> findKeysByTenantIdAndResourceTypeAndResourceKeyStartingWith(@Param("tenantId") UUID tenantId,
                                                                            @Param("resourceType") String resourceType,
                                                                            @Param("prefix") String prefix);

    List<TbResourceInfoEntity> findByTenantIdAndEtagAndResourceKeyStartingWith(UUID tenantId, String etag, String query);

    @Query(value = "SELECT * FROM resource r WHERE (r.tenant_id = '13814000-1dd2-11b2-8080-808080808080' OR r.tenant_id = :tenantId) " +
            "AND r.resource_type = :resourceType AND r.etag = :etag ORDER BY created_time, id LIMIT 1", nativeQuery = true)
    TbResourceInfoEntity findSystemOrTenantResourceByEtag(@Param("tenantId") UUID tenantId,
                                                          @Param("resourceType") String resourceType,
                                                          @Param("etag") String etag);

    @Query(value = "SELECT * FROM resource r WHERE (r.tenant_id = '13814000-1dd2-11b2-8080-808080808080' " +
            "OR (r.tenant_id = :tenantId AND r.customer_id = :customerId)) " +
            "AND r.resource_type = :resourceType AND r.etag = :etag LIMIT 1", nativeQuery = true)
    TbResourceInfoEntity findSystemOrCustomerImageByEtag(@Param("tenantId") UUID tenantId,
                                                         @Param("customerId") UUID customerId,
                                                         @Param("resourceType") String resourceType,
                                                         @Param("etag") String etag);

    boolean existsByResourceTypeAndPublicResourceKey(String resourceType, String publicResourceKey);

    TbResourceInfoEntity findByResourceTypeAndPublicResourceKeyAndIsPublicTrue(String resourceType, String publicResourceKey);

}
