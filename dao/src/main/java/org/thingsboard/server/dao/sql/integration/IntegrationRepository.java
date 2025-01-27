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
package org.thingsboard.server.dao.sql.integration;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.common.data.edqs.fields.IntegrationFields;
import org.thingsboard.server.dao.ExportableEntityRepository;
import org.thingsboard.server.dao.model.sql.IntegrationEntity;

import java.util.List;
import java.util.UUID;

public interface IntegrationRepository extends JpaRepository<IntegrationEntity, UUID>, ExportableEntityRepository<IntegrationEntity> {

    @Query("SELECT a FROM IntegrationEntity a WHERE a.tenantId = :tenantId " +
            "AND (:searchText IS NULL OR ilike(a.name, CONCAT('%', :searchText, '%')) = true)")
    Page<IntegrationEntity> findByTenantId(@Param("tenantId") UUID tenantId,
                                           @Param("searchText") String searchText,
                                           Pageable pageable);

    @Query("SELECT a FROM IntegrationEntity a WHERE a.tenantId = :tenantId " +
            "AND a.edgeTemplate = :isEdgeTemplate " +
            "AND (:searchText IS NULL OR ilike(a.name, CONCAT('%', :searchText, '%')) = true)")
    Page<IntegrationEntity> findByTenantIdAndIsEdgeTemplate(@Param("tenantId") UUID tenantId,
                                                            @Param("searchText") String searchText,
                                                            @Param("isEdgeTemplate") boolean isEdgeTemplate,
                                                            Pageable pageable);

    IntegrationEntity findByRoutingKey(String routingKey);

    @Query("SELECT a FROM IntegrationEntity a WHERE a.tenantId = :tenantId AND (a.converterId = :converterId OR a.downlinkConverterId = :converterId)")
    List<IntegrationEntity> findByConverterId(@Param("tenantId") UUID tenantId,
                                              @Param("converterId") UUID converterId);

    List<IntegrationEntity> findIntegrationsByTenantIdAndIdIn(UUID tenantId, List<UUID> integrationIds);

    @Query("SELECT ie FROM IntegrationEntity ie, RelationEntity re WHERE ie.tenantId = :tenantId " +
            "AND ie.id = re.toId AND re.toType = 'INTEGRATION' AND re.relationTypeGroup = 'EDGE' " +
            "AND re.relationType = 'Contains' AND re.fromId = :edgeId AND re.fromType = 'EDGE' " +
            "AND (:searchText IS NULL OR ilike(ie.name, CONCAT('%', :searchText, '%')) = true)")
    Page<IntegrationEntity> findByTenantIdAndEdgeId(@Param("tenantId") UUID tenantId,
                                                    @Param("edgeId") UUID edgeId,
                                                    @Param("searchText") String searchText,
                                                    Pageable pageable);

    Long countByTenantId(UUID tenantId);

    Long countByTenantIdAndEdgeTemplateFalse(UUID tenantId);

    Long countByEdgeTemplateFalse();

    List<IntegrationEntity> findByTenantIdAndName(UUID tenantId, String name);

    @Query("SELECT externalId FROM IntegrationEntity WHERE id = :id")
    UUID getExternalIdById(@Param("id") UUID id);

    @Query("SELECT new org.thingsboard.server.common.data.edqs.fields.IntegrationFields(i.id, i.createdTime," +
            "i.tenantId, i.name, i.version, i.type, i.additionalInfo) FROM IntegrationEntity i")
    Page<IntegrationFields> findAllFields(Pageable pageable);

}
