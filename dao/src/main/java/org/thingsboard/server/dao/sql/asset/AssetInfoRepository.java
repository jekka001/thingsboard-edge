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
package org.thingsboard.server.dao.sql.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.AssetInfoEntity;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.SUB_CUSTOMERS_QUERY;

public interface AssetInfoRepository extends JpaRepository<AssetInfoEntity, UUID> {

    @Query("SELECT ai FROM AssetInfoEntity ai " +
            "WHERE ai.tenantId = :tenantId " +
            "AND (:searchText IS NULL OR ilike(ai.name, CONCAT('%', :searchText, '%')) = true " +
            "OR ilike(ai.ownerName, CONCAT('%', :searchText, '%')) = true)")
    Page<AssetInfoEntity> findByTenantId(@Param("tenantId") UUID tenantId,
                                         @Param("searchText") String searchText,
                                         Pageable pageable);

    @Query("SELECT ai FROM AssetInfoEntity ai " +
            "WHERE ai.tenantId = :tenantId " +
            "AND ai.assetProfileId = :assetProfileId " +
            "AND (:searchText IS NULL OR ilike(ai.name, CONCAT('%', :searchText, '%')) = true " +
            "OR ilike(ai.ownerName, CONCAT('%', :searchText, '%')) = true)")
    Page<AssetInfoEntity> findByTenantIdAndAssetProfileId(@Param("tenantId") UUID tenantId,
                                                          @Param("assetProfileId") UUID assetProfileId,
                                                          @Param("searchText") String searchText,
                                                          Pageable pageable);

    @Query("SELECT ai FROM AssetInfoEntity ai " +
            "WHERE ai.tenantId = :tenantId AND (ai.customerId IS NULL OR ai.customerId = uuid('13814000-1dd2-11b2-8080-808080808080')) " +
            "AND (:searchText IS NULL OR ilike(ai.name, CONCAT('%', :searchText, '%')) = true) ")
    Page<AssetInfoEntity> findTenantAssetsByTenantId(@Param("tenantId") UUID tenantId,
                                                     @Param("searchText") String searchText,
                                                     Pageable pageable);

    @Query("SELECT ai FROM AssetInfoEntity ai " +
            "WHERE ai.tenantId = :tenantId AND (ai.customerId IS NULL OR ai.customerId = uuid('13814000-1dd2-11b2-8080-808080808080')) " +
            "AND ai.assetProfileId = :assetProfileId " +
            "AND (:searchText IS NULL OR ilike(ai.name, CONCAT('%', :searchText, '%')) = true) ")
    Page<AssetInfoEntity> findTenantAssetsByTenantIdAndAssetProfileId(@Param("tenantId") UUID tenantId,
                                                                      @Param("assetProfileId") UUID assetProfileId,
                                                                      @Param("searchText") String searchText,
                                                                      Pageable pageable);

    @Query("SELECT ai FROM AssetInfoEntity ai WHERE ai.tenantId = :tenantId AND ai.customerId = :customerId " +
            "AND (:searchText IS NULL OR ilike(ai.name, CONCAT('%', :searchText, '%')) = true) ")
    Page<AssetInfoEntity> findByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                                      @Param("customerId") UUID customerId,
                                                      @Param("searchText") String searchText,
                                                      Pageable pageable);

    @Query("SELECT ai FROM AssetInfoEntity ai WHERE ai.tenantId = :tenantId AND ai.customerId = :customerId " +
            "AND ai.assetProfileId = :assetProfileId " +
            "AND (:searchText IS NULL OR ilike(ai.name, CONCAT('%', :searchText, '%')) = true) ")
    Page<AssetInfoEntity> findByTenantIdAndCustomerIdAndAssetProfileId(@Param("tenantId") UUID tenantId,
                                                                       @Param("customerId") UUID customerId,
                                                                       @Param("assetProfileId") UUID assetProfileId,
                                                                       @Param("searchText") String searchText,
                                                                       Pageable pageable);

    @Query(value = "SELECT e.*, e.owner_name as ownername, e.created_time as createdtime " +
            "FROM (select a.id, a.created_time, a.additional_info, a.customer_id, a.\"name\", a.label, " +
            "a.tenant_id, a.type, a.external_id, a.asset_profile_id, a.groups, " +
            "c.title as owner_name from asset_info_view a " +
            "LEFT JOIN customer c on c.id = a.customer_id AND c.id != :customerId) e " +
            "WHERE" + SUB_CUSTOMERS_QUERY +
            "AND (:searchText IS NULL OR e.name ILIKE CONCAT('%', :searchText, '%') " +
            "OR e.owner_name ILIKE CONCAT('%', :searchText, '%'))",
            countQuery = "SELECT count(e.id) FROM asset e " +
                    "LEFT JOIN customer c on c.id = e.customer_id AND c.id != :customerId " +
                    "WHERE" + SUB_CUSTOMERS_QUERY +
                    "AND (:searchText IS NULL OR e.name ILIKE CONCAT('%', :searchText, '%') " +
                    "OR c.title ILIKE CONCAT('%', :searchText, '%'))",
            nativeQuery = true)
    Page<AssetInfoEntity> findByTenantIdAndCustomerIdIncludingSubCustomers(@Param("tenantId") UUID tenantId,
                                                                           @Param("customerId") UUID customerId,
                                                                           @Param("searchText") String searchText,
                                                                           Pageable pageable);

    @Query(value = "SELECT e.*, e.owner_name as ownername, e.created_time as createdtime " +
            "FROM (select a.id, a.created_time, a.additional_info, a.customer_id, a.\"name\", a.label, " +
            "a.tenant_id, a.type, a.external_id, a.asset_profile_id, a.groups, " +
            "c.title as owner_name from asset_info_view a " +
            "LEFT JOIN customer c on c.id = a.customer_id AND c.id != :customerId) e " +
            "WHERE" + SUB_CUSTOMERS_QUERY +
            "AND e.asset_profile_id = :assetProfileId " +
            "AND (:searchText IS NULL OR e.name ILIKE CONCAT('%', :searchText, '%') " +
            "OR e.owner_name ILIKE CONCAT('%', :searchText, '%'))",
            countQuery = "SELECT count(es.id) FROM asset e " +
                    "LEFT JOIN customer c on c.id = e.customer_id AND c.id != :customerId " +
                    "WHERE" + SUB_CUSTOMERS_QUERY +
                    "AND e.asset_profile_id = :assetProfileId " +
                    "AND (:searchText IS NULL OR e.name ILIKE CONCAT('%', :searchText, '%') " +
                    "OR c.title ILIKE CONCAT('%', :searchText, '%'))",
            nativeQuery = true)
    Page<AssetInfoEntity> findByTenantIdAndCustomerIdAndAssetProfileIdIncludingSubCustomers(@Param("tenantId") UUID tenantId,
                                                                                            @Param("customerId") UUID customerId,
                                                                                            @Param("assetProfileId") UUID assetProfileId,
                                                                                            @Param("searchText") String searchText,
                                                                                            Pageable pageable);
}
