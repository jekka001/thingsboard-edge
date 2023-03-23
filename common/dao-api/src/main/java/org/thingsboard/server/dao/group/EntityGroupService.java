/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.dao.entity.EntityDaoService;

import java.util.List;
import java.util.Optional;

public interface EntityGroupService extends EntityDaoService {

    EntityGroup findEntityGroupById(TenantId tenantId, EntityGroupId entityGroupId);

    EntityGroupInfo findEntityGroupInfoById(TenantId tenantId, EntityGroupId entityGroupId);

    ListenableFuture<EntityGroup> findEntityGroupByIdAsync(TenantId tenantId, EntityGroupId entityGroupId);

    ListenableFuture<EntityGroupInfo> findEntityGroupInfoByIdAsync(TenantId tenantId, EntityGroupId entityGroupId);

    EntityGroup saveEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroup entityGroup);

    EntityGroup createEntityGroupAll(TenantId tenantId, EntityId parentEntityId, EntityType groupType);

    EntityGroup findOrCreateUserGroup(TenantId tenantId, EntityId parentEntityId, String groupName, String description);

    EntityGroup findOrCreateEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String groupName,
                                        String description, CustomerId publicCustomerId);

    Optional<EntityGroupInfo> findOwnerEntityGroupInfo(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String groupName);

    EntityGroup findOrCreateTenantUsersGroup(TenantId tenantId);

    EntityGroup findOrCreateTenantAdminsGroup(TenantId tenantId);

    EntityGroup findOrCreateCustomerUsersGroup(TenantId tenantId, CustomerId customerId, CustomerId parentCustomerId);

    EntityGroup findOrCreateCustomerAdminsGroup(TenantId tenantId, CustomerId customerId, CustomerId parentCustomerId);

    EntityGroup findOrCreatePublicUsersGroup(TenantId tenantId, CustomerId customerId);

    EntityGroup findOrCreateReadOnlyEntityGroupForCustomer(TenantId tenantId, CustomerId customerId, EntityType groupType);

    ListenableFuture<Optional<EntityGroup>> findPublicUserGroupAsync(TenantId tenantId, CustomerId publicCustomerId);

    void deleteEntityGroup(TenantId tenantId, EntityGroupId entityGroupId);

    PageData<EntityGroup> findAllEntityGroupsByParentRelation(TenantId tenantId, EntityId parentEntityId, PageLink pageLink);

    void deleteAllEntityGroups(TenantId tenantId, EntityId parentEntityId);

    PageData<EntityGroup> findEntityGroupsByType(TenantId tenantId, EntityId parentEntityId, EntityType groupType, PageLink pageLink);

    PageData<EntityGroupInfo> findEntityGroupInfosByType(TenantId tenantId, EntityId parentEntityId,
                                                         EntityType groupType, PageLink pageLink);

    PageData<EntityGroupInfo> findEntityGroupInfosByOwnersAndType(TenantId tenantId, List<EntityId> ownerIds,
                                                                  EntityType groupType, PageLink pageLink);

    PageData<EntityGroupInfo> findEntityGroupInfosByIds(TenantId tenantId, List<EntityGroupId> entityGroupIds, PageLink pageLink);

    PageData<EntityGroupInfo> findEntityGroupInfosByTypeOrIds(TenantId tenantId, EntityId parentEntityId,
                                                              EntityType groupType, List<EntityGroupId> entityGroupIds, PageLink pageLink);

    PageData<EntityGroupInfo> findEdgeEntityGroupInfosByOwnerIdType(TenantId tenantId, EdgeId edgeId, EntityId ownerId, EntityType groupType, PageLink pageLink);

    PageData<EntityGroup> findEntityGroupsByType(TenantId tenantId, EntityType groupType, PageLink pageLink);

    Optional<EntityGroup> findEntityGroupByTypeAndName(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String name);

    Optional<EntityGroupInfo> findEntityGroupInfoByTypeAndName(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String name);

    ListenableFuture<Optional<EntityGroup>> findEntityGroupByTypeAndNameAsync(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String name);

    void addEntityToEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId);

    void addEntityToEntityGroupAll(TenantId tenantId, EntityId parentEntityId, EntityId entityId);

    void addEntitiesToEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, List<EntityId> entityIds);

    void removeEntityFromEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId);

    void removeEntitiesFromEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, List<EntityId> entityIds);

    ShortEntityView findGroupEntity(TenantId tenantId, CustomerId customerId, MergedUserPermissions userPermissions, EntityGroupId entityGroupId, EntityId entityId);

    PageData<ShortEntityView> findGroupEntities(TenantId tenantId, CustomerId customerId, MergedUserPermissions userPermissions, EntityGroupId entityGroupId, PageLink pageLink);

    ListenableFuture<List<EntityId>> findAllEntityIdsAsync(TenantId tenantId, EntityGroupId entityGroupId, PageLink pageLink);

    PageData<EntityId> findEntityIds(TenantId tenantId, EntityType entityType, EntityGroupId entityGroupId, PageLink pageLink);

    ListenableFuture<List<EntityGroupId>> findEntityGroupsForEntityAsync(TenantId tenantId, EntityId entityId);

    boolean isEntityInGroup(TenantId tenantId, EntityId entityId, EntityGroupId entityGroupId);

    EntityGroup assignEntityGroupToEdge(TenantId tenantId, EntityGroupId entityGroupId, EdgeId edgeId, EntityType groupType);

    EntityGroup unassignEntityGroupFromEdge(TenantId tenantId, EntityGroupId entityGroupId, EdgeId edgeId, EntityType groupType);

    PageData<EntityGroup> findEdgeEntityGroupsByType(TenantId tenantId, EdgeId edgeId, EntityType groupType, PageLink pageLink);

    ListenableFuture<Boolean> checkEdgeEntityGroupByIdAsync(TenantId tenantId, EdgeId edgeId, EntityGroupId entityGroupId, EntityType groupType);

    ListenableFuture<EntityGroup> findOrCreateEdgeAllGroupAsync(TenantId tenantId, Edge edge, String edgeName, EntityType groupType);
}
