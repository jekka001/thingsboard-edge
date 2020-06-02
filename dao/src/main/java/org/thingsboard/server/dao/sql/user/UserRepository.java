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
package org.thingsboard.server.dao.sql.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.model.sql.UserEntity;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.List;

/**
 * @author Valerii Sosliuk
 */
@SqlDao
public interface UserRepository extends PagingAndSortingRepository<UserEntity, String> {

    UserEntity findByEmail(String email);

    @Query("SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId " +
            "AND u.customerId = :customerId AND u.authority = :authority " +
            "AND LOWER(u.searchText) LIKE LOWER(CONCAT(:searchText, '%'))")
    Page<UserEntity> findUsersByAuthority(@Param("tenantId") String tenantId,
                                          @Param("customerId") String customerId,
                                          @Param("searchText") String searchText,
                                          @Param("authority") Authority authority,
                                          Pageable pageable);

    @Query("SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId " +
            "AND u.authority = :authority " +
            "AND LOWER(u.searchText) LIKE LOWER(CONCAT(:searchText, '%'))")
    Page<UserEntity> findAllTenantUsersByAuthority(@Param("tenantId") String tenantId,
                                                   @Param("searchText") String searchText,
                                                   @Param("authority") Authority authority,
                                                   Pageable pageable);

    @Query("SELECT u FROM UserEntity u, " +
            "RelationEntity re " +
            "WHERE u.id = re.toId AND re.toType = 'USER' " +
            "AND re.relationTypeGroup = 'FROM_ENTITY_GROUP' " +
            "AND re.relationType = 'Contains' " +
            "AND re.fromId = :groupId AND re.fromType = 'ENTITY_GROUP' " +
            "AND LOWER(u.searchText) LIKE LOWER(CONCAT(:textSearch, '%'))")
    Page<UserEntity> findByEntityGroupId(@Param("groupId") String groupId,
                                         @Param("textSearch") String textSearch,
                                         Pageable pageable);

    @Query("SELECT u FROM UserEntity u, " +
            "RelationEntity re " +
            "WHERE u.id = re.toId AND re.toType = 'USER' " +
            "AND re.relationTypeGroup = 'FROM_ENTITY_GROUP' " +
            "AND re.relationType = 'Contains' " +
            "AND re.fromId in :groupIds AND re.fromType = 'ENTITY_GROUP' " +
            "AND LOWER(u.searchText) LIKE LOWER(CONCAT(:textSearch, '%'))")
    Page<UserEntity> findByEntityGroupIds(@Param("groupIds") List<String> groupIds,
                                          @Param("textSearch") String textSearch,
                                          Pageable pageable);

    List<UserEntity> findUsersByTenantIdAndIdIn(String tenantId, List<String> userIds);

    Page<UserEntity> findUsersByTenantId(String tenantId, Pageable pageable);

}
