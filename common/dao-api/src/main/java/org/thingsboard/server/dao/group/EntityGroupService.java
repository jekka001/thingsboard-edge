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
package org.thingsboard.server.dao.group;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.group.EntityField;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public interface EntityGroupService {

    EntityGroup findEntityGroupById(TenantId tenantId, EntityGroupId entityGroupId);

    ListenableFuture<EntityGroup> findEntityGroupByIdAsync(TenantId tenantId, EntityGroupId entityGroupId);

    ListenableFuture<List<EntityGroup>> findEntityGroupByIdsAsync(TenantId tenantId, List<EntityGroupId> entityGroupIds);

    ListenableFuture<Boolean> checkEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroup entityGroup);

    ListenableFuture<Boolean> checkEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroupId entityGroupId, EntityType groupType);

    EntityGroup saveEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroup entityGroup);

    EntityGroup saveEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroup entityGroup, boolean forceCreate);

    EntityGroup createEntityGroupAll(TenantId tenantId, EntityId parentEntityId, EntityType groupType);

    EntityGroup findOrCreateUserGroup(TenantId tenantId, EntityId parentEntityId, String groupName, String description);

    EntityGroup findOrCreateEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String groupName,
                                        String description, CustomerId publicCustomerId);

    Optional<EntityGroup> findOwnerEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String groupName);

    EntityGroup findOrCreateTenantUsersGroup(TenantId tenantId);

    EntityGroup findOrCreateCustomerUsersGroup(TenantId tenantId, CustomerId customerId, CustomerId parentCustomerId);

    EntityGroup findOrCreatePublicUsersGroup(TenantId tenantId, CustomerId customerId);

    EntityGroup findOrCreateReadOnlyEntityGroupForCustomer(TenantId tenantId, CustomerId customerId, EntityType groupType);

    ListenableFuture<Optional<EntityGroup>> findPublicUserGroup(TenantId tenantId, CustomerId publicCustomerId);

    void deleteEntityGroup(TenantId tenantId, EntityGroupId entityGroupId);

    ListenableFuture<List<EntityGroup>> findAllEntityGroups(TenantId tenantId, EntityId parentEntityId);

    void deleteAllEntityGroups(TenantId tenantId, EntityId parentEntityId);

    ListenableFuture<List<EntityGroup>> findEntityGroupsByType(TenantId tenantId, EntityId parentEntityId, EntityType groupType);

    ListenableFuture<Optional<EntityGroup>> findEntityGroupByTypeAndName(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String name);

    void addEntityToEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId);

    void addEntityToEntityGroupAll(TenantId tenantId, EntityId parentEntityId, EntityId entityId);

    void addEntitiesToEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, List<EntityId> entityIds);

    void removeEntityFromEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId);

    void removeEntitiesFromEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, List<EntityId> entityIds);

    <E extends BaseData, I extends EntityId> ShortEntityView findGroupEntity(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId,
                                                                             Function<EntityId, I> toIdFunction,
                                                                             Function<I, E> toEntityFunction,
                                                                             BiFunction<E, List<EntityField>, ShortEntityView> transformFunction);

    <E extends BaseData, I extends EntityId> ListenableFuture<TimePageData<ShortEntityView>> findEntities(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink,
                                                                                                          Function<EntityId, I> toIdFunction,
                                                                                                          Function<List<I>, ListenableFuture<List<E>>> toEntitiesFunction,
                                                                                                          BiFunction<E, List<EntityField>, ShortEntityView> transformFunction);

    <E extends HasId<I>, I extends EntityId> ListenableFuture<TimePageData<E>> findEntities(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink,
                                                                                            Function<EntityId, I> toIdFunction,
                                                                                            Function<List<I>, ListenableFuture<List<E>>> toEntitiesFunction);

    ListenableFuture<List<EntityId>> findAllEntityIds(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink);

    ListenableFuture<List<EntityGroupId>> findEntityGroupsForEntity(TenantId tenantId, EntityId entityId);

}
