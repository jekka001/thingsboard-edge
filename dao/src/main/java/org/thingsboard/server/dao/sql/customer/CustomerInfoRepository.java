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
package org.thingsboard.server.dao.sql.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.CustomerInfoEntity;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.CUSTOMERS_SUB_CUSTOMERS_QUERY;

public interface CustomerInfoRepository extends JpaRepository<CustomerInfoEntity, UUID> {

    @Query("SELECT ci FROM CustomerInfoEntity ci " +
            "WHERE ci.tenantId = :tenantId " +
            "AND (:searchText IS NULL OR ilike(ci.title, CONCAT('%', :searchText, '%')) = true " +
            "OR ilike(ci.ownerName, CONCAT('%', :searchText, '%')) = true)")
    Page<CustomerInfoEntity> findByTenantId(@Param("tenantId") UUID tenantId,
                                            @Param("searchText") String searchText,
                                            Pageable pageable);

    @Query("SELECT ci FROM CustomerInfoEntity ci " +
            "WHERE ci.tenantId = :tenantId AND (ci.parentCustomerId IS NULL OR ci.parentCustomerId = uuid('13814000-1dd2-11b2-8080-808080808080')) " +
            "AND (:searchText IS NULL OR ilike(ci.title, CONCAT('%', :searchText, '%')) = true)")
    Page<CustomerInfoEntity> findTenantCustomersByTenantId(@Param("tenantId") UUID tenantId,
                                                           @Param("searchText") String searchText,
                                                           Pageable pageable);

    @Query("SELECT ci FROM CustomerInfoEntity ci WHERE ci.tenantId = :tenantId AND ci.parentCustomerId = :customerId " +
            "AND (:searchText IS NULL OR ilike(ci.title, CONCAT('%', :searchText, '%')) = true)")
    Page<CustomerInfoEntity> findByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                                         @Param("customerId") UUID customerId,
                                                         @Param("searchText") String searchText,
                                                         Pageable pageable);

    @Query(value = "SELECT e.*, e.owner_name as ownername, e.created_time as createdtime " +
            "FROM (select ce.id, ce.created_time, ce.additional_info, ce.address, ce.address2, ce.city, " +
            "ce.country, ce.email, ce.phone, ce.state, ce.tenant_id, " +
            "ce.parent_customer_id, ce.title, ce.zip, ce.external_id, ce.groups, ce.is_public, " +
            "c.title as owner_name from customer_info_view ce " +
            "LEFT JOIN customer c on c.id = ce.parent_customer_id AND c.id != :customerId) e " +
            "WHERE" + CUSTOMERS_SUB_CUSTOMERS_QUERY +
            "AND (:searchText IS NULL OR e.title ILIKE CONCAT('%', :searchText, '%') " +
            "OR e.owner_name ILIKE CONCAT('%', :searchText, '%'))",
            countQuery = "SELECT count(e.id) FROM customer e " +
                    "LEFT JOIN customer c on c.id = e.parent_customer_id AND c.id != :customerId " +
                    "WHERE" + CUSTOMERS_SUB_CUSTOMERS_QUERY +
                    "AND (:searchText IS NULL OR e.title ILIKE CONCAT('%', :searchText, '%') " +
                    "OR c.title ILIKE CONCAT('%', :searchText, '%'))",
            nativeQuery = true)
    Page<CustomerInfoEntity> findByTenantIdAndCustomerIdIncludingSubCustomers(@Param("tenantId") UUID tenantId,
                                                                              @Param("customerId") UUID customerId,
                                                                              @Param("searchText") String searchText,
                                                                              Pageable pageable);

}
