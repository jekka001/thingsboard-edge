/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.common.data.edqs.fields.AssetFields;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.dao.ExportableEntityRepository;
import org.thingsboard.server.dao.model.sql.AssetEntity;

import java.util.List;
import java.util.UUID;

/**
 * Created by Valerii Sosliuk on 5/21/2017.
 */
public interface AssetRepository extends JpaRepository<AssetEntity, UUID>, ExportableEntityRepository<AssetEntity> {

    @Query("SELECT a FROM AssetEntity a WHERE a.tenantId = :tenantId " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "  OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true " +
            "  OR ilike(a.type, CONCAT('%', :textSearch, '%')) = true)")
    Page<AssetEntity> findByTenantId(@Param("tenantId") UUID tenantId,
                                     @Param("textSearch") String textSearch,
                                     Pageable pageable);

    @Query("SELECT a FROM AssetEntity a WHERE a.tenantId = :tenantId " +
            "AND a.customerId = :customerId " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "  OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true " +
            "  OR ilike(a.type, CONCAT('%', :textSearch, '%')) = true)")
    Page<AssetEntity> findByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                                  @Param("customerId") UUID customerId,
                                                  @Param("textSearch") String textSearch,
                                                  Pageable pageable);

    @Query("SELECT a FROM AssetEntity a WHERE a.tenantId = :tenantId " +
            "AND a.assetProfileId = :profileId " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.type, CONCAT('%', :textSearch, '%')) = true) ")
    Page<AssetEntity> findByTenantIdAndProfileId(@Param("tenantId") UUID tenantId,
                                                 @Param("profileId") UUID profileId,
                                                 @Param("textSearch") String textSearch,
                                                 Pageable pageable);

    @Query("SELECT a FROM AssetEntity a, " +
            "RelationEntity re " +
            "WHERE a.id = re.toId AND re.toType = 'ASSET' " +
            "AND re.relationTypeGroup = 'FROM_ENTITY_GROUP' " +
            "AND re.relationType = 'Contains' " +
            "AND re.fromId = :groupId AND re.fromType = 'ENTITY_GROUP' " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.type, CONCAT('%', :textSearch, '%')) = true) ")
    Page<AssetEntity> findByEntityGroupId(@Param("groupId") UUID groupId,
                                          @Param("textSearch") String textSearch,
                                          Pageable pageable);

    @Query("SELECT a FROM AssetEntity a, " +
            "RelationEntity re " +
            "WHERE a.id = re.toId AND re.toType = 'ASSET' " +
            "AND re.relationTypeGroup = 'FROM_ENTITY_GROUP' " +
            "AND re.relationType = 'Contains' " +
            "AND re.fromId in :groupIds AND re.fromType = 'ENTITY_GROUP' " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.type, CONCAT('%', :textSearch, '%')) = true) ")
    Page<AssetEntity> findByEntityGroupIds(@Param("groupIds") List<UUID> groupIds,
                                           @Param("textSearch") String textSearch,
                                           Pageable pageable);

    @Query("SELECT a.id FROM AssetEntity a " +
            "WHERE a.tenantId = :tenantId " +
            "AND a.assetProfileId = :assetProfileId " +
            "AND (:textSearch IS NULL OR ilike(a.type, CONCAT('%', :textSearch, '%')) = true) ")
    Page<UUID> findAssetIdsByTenantIdAndAssetProfileId(@Param("tenantId") UUID tenantId,
                                                       @Param("assetProfileId") UUID assetProfileId,
                                                       @Param("textSearch") String textSearch,
                                                       Pageable pageable);

    @Query("SELECT a FROM AssetEntity a, " +
            "RelationEntity re " +
            "WHERE a.id = re.toId AND re.toType = 'ASSET' " +
            "AND re.relationTypeGroup = 'FROM_ENTITY_GROUP' " +
            "AND re.relationType = 'Contains' " +
            "AND re.fromId in :groupIds AND re.fromType = 'ENTITY_GROUP' " +
            "AND a.type = :type " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true) ")
    Page<AssetEntity> findByEntityGroupIdsAndType(@Param("groupIds") List<UUID> groupIds,
                                                  @Param("type") String type,
                                                  @Param("textSearch") String textSearch,
                                                  Pageable pageable);

    List<AssetEntity> findByTenantIdAndIdIn(UUID tenantId, List<UUID> assetIds);

    List<AssetEntity> findByTenantIdAndCustomerIdAndIdIn(UUID tenantId, UUID customerId, List<UUID> assetIds);

    AssetEntity findByTenantIdAndName(UUID tenantId, String name);

    @Query("SELECT a FROM AssetEntity a WHERE a.tenantId = :tenantId " +
            "AND a.type = :type " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "  OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true)")
    Page<AssetEntity> findByTenantIdAndType(@Param("tenantId") UUID tenantId,
                                            @Param("type") String type,
                                            @Param("textSearch") String textSearch,
                                            Pageable pageable);


    @Query("SELECT a FROM AssetEntity a WHERE a.tenantId = :tenantId " +
            "AND a.customerId = :customerId AND a.type = :type " +
            "AND (:textSearch IS NULL OR ilike(a.name, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(a.label, CONCAT('%', :textSearch, '%')) = true) ")
    Page<AssetEntity> findByTenantIdAndCustomerIdAndType(@Param("tenantId") UUID tenantId,
                                                         @Param("customerId") UUID customerId,
                                                         @Param("type") String type,
                                                         @Param("textSearch") String textSearch,
                                                         Pageable pageable);

    Long countByAssetProfileId(UUID assetProfileId);

    Long countByTenantId(UUID tenantId);

    @Query("SELECT a.id FROM AssetEntity a WHERE a.tenantId = :tenantId AND (a.customerId is null OR a.customerId = org.thingsboard.server.common.data.id.EntityId.NULL_UUID)")
    Page<UUID> findIdsByTenantIdAndNullCustomerId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT a.id FROM AssetEntity a WHERE a.tenantId = :tenantId AND a.customerId = :customerId")
    Page<UUID> findIdsByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                              @Param("customerId") UUID customerId,
                                              Pageable pageable);

    @Query("SELECT externalId FROM AssetEntity WHERE id = :id")
    UUID getExternalIdById(@Param("id") UUID id);

    @Query(value = "SELECT DISTINCT new org.thingsboard.server.common.data.util.TbPair(a.tenantId , a.type) FROM  AssetEntity a")
    Page<TbPair<UUID, String>> getAllAssetTypes(Pageable pageable);


    @Query("SELECT new org.thingsboard.server.common.data.edqs.fields.AssetFields(a.id, a.createdTime, a.tenantId, a.customerId," +
            "a.name, a.version, a.type, a.label, a.assetProfileId, a.additionalInfo) FROM AssetEntity a WHERE a.id > :id ORDER BY a.id")
    List<AssetFields> findAllFields(@Param("id") UUID id, Limit limit);

}
