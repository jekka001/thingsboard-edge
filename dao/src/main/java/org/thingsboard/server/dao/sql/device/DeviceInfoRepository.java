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
package org.thingsboard.server.dao.sql.device;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.DeviceInfoEntity;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.SUB_CUSTOMERS_QUERY;

public interface DeviceInfoRepository extends JpaRepository<DeviceInfoEntity, UUID> {

    @Query("SELECT d FROM DeviceInfoEntity d " +
            "WHERE d.tenantId = :tenantId " +
            "AND (" +
            "((:customerId IS NULL AND (:includeCustomers) = TRUE)) " +
            "OR ((:customerId IS NULL AND (:includeCustomers) = FALSE) AND (d.customerId IS NULL OR d.customerId = org.thingsboard.server.common.data.id.EntityId.NULL_UUID)) " +
            "OR (:customerId IS NOT NULL AND d.customerId = :customerId) " +
            ") " +
            "AND (:deviceProfileId IS NULL OR d.deviceProfileId = :deviceProfileId) " +
            "AND ((:filterByActive) = FALSE OR d.active = :deviceActive) " +
            "AND (:textSearch IS NULL OR ilike(d.name, CONCAT('%', :textSearch, '%'))  = true " +
            "OR ilike(d.label, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(d.type, CONCAT('%', :textSearch, '%')) = true " +
            "OR ilike(d.ownerName, CONCAT('%', :textSearch, '%')) = true )")
    Page<DeviceInfoEntity> findDeviceInfosByFilter(@Param("tenantId") UUID tenantId,
                                                   @Param("includeCustomers") boolean includeCustomers,
                                                   @Param("customerId") UUID customerId,
                                                   @Param("deviceProfileId") UUID deviceProfileId,
                                                   @Param("filterByActive") boolean filterByActive,
                                                   @Param("deviceActive") boolean active,
                                                   @Param("textSearch") String textSearch,
                                                   Pageable pageable);

    @Query(value = "SELECT e.*, e.owner_name as ownername, e.created_time as createdtime " +
            "FROM (select d.id, d.created_time, d.additional_info, d.customer_id, d.device_profile_id, " +
            "d.device_data, d.type, d.name, d.label, d.tenant_id, d.firmware_id, d.software_id, d.external_id, d.groups, " +
            "c.title as owner_name, d.active as active from device_info_view d " +
            "LEFT JOIN customer c on c.id = d.customer_id AND c.id != :customerId) e " +
            "WHERE" + SUB_CUSTOMERS_QUERY +
            "AND (:deviceProfileId IS NULL OR e.device_profile_id = :deviceProfileId) " +
            "AND ((:filterByActive) IS FALSE OR e.active = :deviceActive) " +
            "AND (:textSearch IS NULL OR e.name ILIKE CONCAT('%', :textSearch, '%') " +
            "OR e.label ILIKE CONCAT('%', :textSearch, '%') " +
            "OR e.type ILIKE CONCAT('%', :textSearch, '%') " +
            "OR e.owner_name ILIKE CONCAT('%', :textSearch, '%'))",
            countQuery = "SELECT count(e.id) FROM device_info_view e " +
                    "LEFT JOIN customer c on c.id = e.customer_id AND c.id != :customerId " +
                    "WHERE" + SUB_CUSTOMERS_QUERY +
                    "AND (:deviceProfileId IS NULL OR e.device_profile_id = :deviceProfileId) " +
                    "AND ((:filterByActive) IS FALSE OR e.active = :deviceActive) " +
                    "AND (:textSearch IS NULL OR e.name ILIKE CONCAT('%', :textSearch, '%') " +
                    "OR e.label ILIKE CONCAT('%', :textSearch, '%') " +
                    "OR e.type ILIKE CONCAT('%', :textSearch, '%') " +
                    "OR e.owner_name ILIKE CONCAT('%', :textSearch, '%'))",
            nativeQuery = true)
    Page<DeviceInfoEntity> findDeviceInfosByFilterIncludingSubCustomers(@Param("tenantId") UUID tenantId,
                                                        @Param("customerId") UUID customerId,
                                                        @Param("deviceProfileId") UUID deviceProfileId,
                                                        @Param("filterByActive") boolean filterByActive,
                                                        @Param("deviceActive") boolean active,
                                                        @Param("textSearch") String textSearch, Pageable pageable);
}
