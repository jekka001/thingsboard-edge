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
package org.thingsboard.server.dao.sql.scheduler;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.dao.model.sql.SchedulerEventInfoEntity;
import org.thingsboard.server.dao.model.sql.SchedulerEventWithCustomerInfoEntity;

import java.util.List;
import java.util.UUID;

public interface SchedulerEventInfoRepository extends CrudRepository<SchedulerEventInfoEntity, UUID> {

    @Query("SELECT new org.thingsboard.server.dao.model.sql.SchedulerEventWithCustomerInfoEntity(s, c.title, c.additionalInfo) " +
            "FROM SchedulerEventInfoEntity s " +
            "LEFT JOIN CustomerEntity c on c.id = s.customerId " +
            "WHERE s.id = :schedulerEventId")
    SchedulerEventWithCustomerInfoEntity findSchedulerEventWithCustomerInfoById(@Param("schedulerEventId") UUID schedulerEventId);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.SchedulerEventWithCustomerInfoEntity(s, c.title, c.additionalInfo) " +
            "FROM SchedulerEventInfoEntity s " +
            "LEFT JOIN CustomerEntity c on c.id = s.customerId " +
            "WHERE s.tenantId = :tenantId")
    List<SchedulerEventWithCustomerInfoEntity> findSchedulerEventsWithCustomerInfoByTenantId(@Param("tenantId") UUID tenantId);

    List<SchedulerEventInfoEntity> findSchedulerEventInfoEntitiesByTenantId(UUID tenantId);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.SchedulerEventWithCustomerInfoEntity(s, c.title, c.additionalInfo) " +
            "FROM SchedulerEventInfoEntity s " +
            "LEFT JOIN CustomerEntity c on c.id = s.customerId " +
            "WHERE s.tenantId = :tenantId " +
            "AND s.type = :type")
    List<SchedulerEventWithCustomerInfoEntity> findByTenantIdAndType(@Param("tenantId") UUID tenantId,
                                                                     @Param("type") String type);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.SchedulerEventWithCustomerInfoEntity(s, c.title, c.additionalInfo) " +
            "FROM SchedulerEventInfoEntity s " +
            "LEFT JOIN CustomerEntity c on c.id = s.customerId " +
            "WHERE s.tenantId = :tenantId " +
            "AND s.customerId = :customerId")
    List<SchedulerEventWithCustomerInfoEntity> findByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                                                           @Param("customerId") UUID customerId);

    @Query("SELECT new org.thingsboard.server.dao.model.sql.SchedulerEventWithCustomerInfoEntity(s, c.title, c.additionalInfo) " +
            "FROM SchedulerEventInfoEntity s " +
            "LEFT JOIN CustomerEntity c on c.id = s.customerId " +
            "WHERE s.tenantId = :tenantId " +
            "AND s.customerId = :customerId " +
            "AND s.type = :type")
    List<SchedulerEventWithCustomerInfoEntity> findByTenantIdAndCustomerIdAndType(@Param("tenantId") UUID tenantId,
                                                                                  @Param("customerId") UUID customerId,
                                                                                  @Param("type") String type);

    List<SchedulerEventInfoEntity> findSchedulerEventsByTenantIdAndIdIn(UUID tenantId, List<UUID> schedulerEventIds);

}
