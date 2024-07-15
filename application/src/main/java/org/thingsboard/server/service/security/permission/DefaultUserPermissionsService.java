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
package org.thingsboard.server.service.security.permission;

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.cache.TbTransactionalCache;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.permission.MergedGroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.user.UserPermissionCacheKey;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class DefaultUserPermissionsService implements UserPermissionsService {

    @Autowired
    protected TbTransactionalCache<UserPermissionCacheKey, MergedUserPermissions> cache;

    private static final MergedUserPermissions sysAdminPermissions;

    static {
        Map<Resource, Set<Operation>> sysAdminGenericPermissions = new HashMap<>();
        sysAdminGenericPermissions.put(Resource.PROFILE, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.ADMIN_SETTINGS, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.DASHBOARD, Set.of(Operation.READ));
        sysAdminGenericPermissions.put(Resource.ALARM, Set.of(Operation.READ));
        sysAdminGenericPermissions.put(Resource.TENANT, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.TENANT_PROFILE, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.RULE_CHAIN, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.USER, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.WIDGETS_BUNDLE, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.WIDGET_TYPE, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.WHITE_LABELING, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.TB_RESOURCE, Set.of(Operation.ALL));
        sysAdminGenericPermissions.put(Resource.NOTIFICATION, Set.of(Operation.ALL));
        sysAdminPermissions = new MergedUserPermissions(sysAdminGenericPermissions, new HashMap<>());
    }

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DbCallbackExecutorService dbCallbackExecutorService;

    @Override
    public MergedUserPermissions getMergedPermissions(User user, boolean isPublic) throws ThingsboardException {
        if (Authority.SYS_ADMIN.equals(user.getAuthority())) {
            return sysAdminPermissions;
        }
        MergedUserPermissions result = getMergedPermissionsFromCache(user.getTenantId(), user.getCustomerId(), user.getId());
        if (result == null) {
            ListenableFuture<List<EntityGroupId>> groups;
            if (isPublic) {
                ListenableFuture<Optional<EntityGroup>> publicUserGroup = entityGroupService.findPublicUserGroupAsync(user.getTenantId(), user.getCustomerId());
                groups = Futures.transform(publicUserGroup, groupOptional -> groupOptional.map(entityGroup -> Collections.singletonList(entityGroup.getId())).orElse(Collections.emptyList()), MoreExecutors.directExecutor());
            } else {
                groups = entityGroupService.findEntityGroupsForEntityAsync(user.getTenantId(), user.getId());
            }
            ListenableFuture<List<GroupPermission>> permissions = Futures.transformAsync(groups, toGroupPermissionsList(user.getTenantId()), dbCallbackExecutorService);
            try {
                result = Futures.transform(permissions, groupPermissions -> toMergedUserPermissions(user.getTenantId(), groupPermissions), dbCallbackExecutorService).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ThingsboardException(e, ThingsboardErrorCode.GENERAL);
            }
            putMergedPermissionsToCache(user.getTenantId(), user.getCustomerId(), user.getId(), result);
        }
        return result;
    }

    @Override
    public void onRoleUpdated(Role role) throws ThingsboardException {
        PageData<GroupPermission> groupPermissions =
                groupPermissionService.findGroupPermissionByTenantIdAndRoleId(role.getTenantId(), role.getId(), new PageLink(Integer.MAX_VALUE));
        Set<EntityGroupId> uniqueUserGroups = new HashSet<>();
        for (GroupPermission gpe : groupPermissions.getData()) {
            uniqueUserGroups.add(gpe.getUserGroupId());
        }
        evictCacheForUserGroups(role.getTenantId(), uniqueUserGroups, false);
    }

    @Override
    public void onGroupPermissionUpdated(GroupPermission groupPermission) throws ThingsboardException {
        evictCacheForUserGroups(groupPermission.getTenantId(), Collections.singleton(groupPermission.getUserGroupId()), groupPermission.isPublic());
    }

    @Override
    public void onGroupPermissionDeleted(GroupPermission groupPermission) throws ThingsboardException {
        evictCacheForUserGroups(groupPermission.getTenantId(), Collections.singleton(groupPermission.getUserGroupId()), groupPermission.isPublic());
    }

    @Override
    public void onUserUpdatedOrRemoved(User user) {
        evictMergedPermissionsToCache(user.getTenantId(), user.getCustomerId(), user.getId());
    }

    private void evictCacheForUserGroups(TenantId tenantId, Set<EntityGroupId> uniqueUserGroups, boolean isPublic) throws ThingsboardException {
        Map<EntityId, Set<EntityId>> usersByOwnerMap = new HashMap<>();
        for (EntityGroupId userGroupId : uniqueUserGroups) {
            EntityGroup userGroup = entityGroupService.findEntityGroupById(tenantId, userGroupId);
            try {
                List<EntityId> entityIds;
                if (isPublic) {
                    entityIds = Collections.singletonList(new UserId(EntityId.NULL_UUID));
                } else {
                    entityIds = entityGroupService.findAllEntityIdsAsync(tenantId, userGroupId, new PageLink(Integer.MAX_VALUE)).get();
                }
                usersByOwnerMap.computeIfAbsent(userGroup.getOwnerId(), ownerId -> new HashSet<>()).addAll(entityIds);
            } catch (InterruptedException | ExecutionException e) {
                throw new ThingsboardException(e, ThingsboardErrorCode.GENERAL);
            }
        }
        usersByOwnerMap.forEach((ownerId, userIds) ->
                userIds.forEach(userId -> evictMergedPermissionsToCache(tenantId,
                                EntityType.CUSTOMER.equals(ownerId.getEntityType()) ? new CustomerId(ownerId.getId()) : new CustomerId(CustomerId.NULL_UUID), userId)));
    }

    private MergedUserPermissions getMergedPermissionsFromCache(TenantId tenantId, CustomerId customerId, UserId userId) {
        var data = cache.get(new UserPermissionCacheKey(tenantId, customerId, userId));
        if (data == null) {
            log.debug("[{}][{}][{}] Not user permissions in cache", tenantId, customerId, userId);
            return null;
        }
        return data.get();
    }

    private void putMergedPermissionsToCache(TenantId tenantId, CustomerId customerId, UserId userId, MergedUserPermissions permissions) {
        log.debug("[{}][{}][{}] Pushing user permissions to cache: {}", tenantId, customerId, userId, permissions);
        cache.put(new UserPermissionCacheKey(tenantId, customerId, userId), permissions);
    }

    private void evictMergedPermissionsToCache(TenantId tenantId, CustomerId customerId, EntityId userId) {
        log.debug("[{}][{}][{}] Evict user permissions to cache", tenantId, customerId, userId);
        cache.evict(new UserPermissionCacheKey(tenantId, customerId, userId));
    }

    private AsyncFunction<List<EntityGroupId>, List<GroupPermission>> toGroupPermissionsList(TenantId tenantId) {
        return groupIds -> {
            List<GroupPermission> result = new ArrayList<>(groupIds.size());
            for (EntityGroupId userGroupId : groupIds) {
                result.addAll(groupPermissionService.findGroupPermissionListByTenantIdAndUserGroupId(tenantId, userGroupId));
            }
            return Futures.immediateFuture(result);
        };
    }

    private MergedUserPermissions toMergedUserPermissions(TenantId tenantId, List<GroupPermission> groupPermissions) {
        Map<Resource, Set<Operation>> genericPermissions = new HashMap<>();
        Map<EntityGroupId, MergedGroupPermissionInfo> groupSpecificPermissions = new HashMap<>();
        for (GroupPermission groupPermission : groupPermissions) {
            Role role = roleService.findRoleById(tenantId, groupPermission.getRoleId());
            if (role.getType() == RoleType.GENERIC) {
                addGenericRolePermissions(role, genericPermissions);
            } else {
                addGroupSpecificRolePermissions(role, groupSpecificPermissions, groupPermission);
            }
        }
        return new MergedUserPermissions(genericPermissions, groupSpecificPermissions);
    }

    private void addGenericRolePermissions(Role role, Map<Resource, Set<Operation>> target) {
        Map<Resource, List<Operation>> rolePermissions = new HashMap<>();
        for (Resource resource : Resource.values()) {
            if (role.getPermissions().has(resource.name())) {
                List<Operation> operations = new ArrayList<>();
                rolePermissions.put(resource, operations);
                role.getPermissions().get(resource.name()).forEach(node -> operations.add(Operation.valueOf(node.asText())));
            }
        }
        rolePermissions.forEach(((resource, operations) -> target.computeIfAbsent(resource, r -> new HashSet<>()).addAll(operations)));
    }

    private void addGroupSpecificRolePermissions(Role role, Map<EntityGroupId, MergedGroupPermissionInfo> target, GroupPermission groupPermission) {
        List<Operation> roleOperations = new ArrayList<>();
        role.getPermissions().forEach(node -> roleOperations.add(Operation.valueOf(node.asText())));
        target.computeIfAbsent(groupPermission.getEntityGroupId(), id -> new MergedGroupPermissionInfo(groupPermission.getEntityGroupType(), new HashSet<>())).getOperations().addAll(roleOperations);
    }

}
