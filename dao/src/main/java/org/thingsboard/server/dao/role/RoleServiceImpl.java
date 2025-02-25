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
package org.thingsboard.server.dao.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.dao.entity.CachedVersionedEntityService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.sql.JpaExecutorService;

import java.util.List;
import java.util.Optional;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service("RoleDaoService")
@Slf4j
public class RoleServiceImpl extends CachedVersionedEntityService<RoleCacheKey, Role, RoleEvictEvent> implements RoleService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_ROLE_ID = "Incorrect roleId ";
    public static final String INCORRECT_PAGE_LINK = "Incorrect page link ";
    public static final String INCORRECT_ROLE_NAME = "Incorrect role name ";

    @Autowired
    private RoleDao roleDao;

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Autowired
    private DataValidator<Role> roleValidator;

    @Autowired
    private JpaExecutorService executor;


    @TransactionalEventListener(classes = RoleEvictEvent.class)
    @Override
    public void handleEvictEvent(RoleEvictEvent event) {
        if (event.getSavedRole() != null) {
            cache.put(RoleCacheKey.forId(event.getSavedRole().getId()), event.getSavedRole());
        } else {
            cache.evict(RoleCacheKey.forId(event.getRoleId()));
        }
    }

    @Override
    public Role saveRole(TenantId tenantId, Role role, boolean doValidate) {
        log.trace("Executing save role [{}]", role);
        if (doValidate) {
            roleValidator.validate(role, Role::getTenantId);
        }
        try {
            Role savedRole = roleDao.save(tenantId, role);
            publishEvictEvent(new RoleEvictEvent(savedRole.getId(), savedRole));
            eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entityId(savedRole.getId())
                    .created(role.getId() == null).build());
            return savedRole;
        } catch (Exception t) {
            checkConstraintViolation(t,
                    "role_external_id_unq_key", "Role with such external id already exists!");
            throw t;
        }
    }

    @Override
    public Role saveRole(TenantId tenantId, Role role) {
        return saveRole(tenantId, role, true);
    }

    @Override
    public Role findRoleById(TenantId tenantId, RoleId roleId) {
        log.trace("Executing findRoleById [{}]", roleId);
        validateId(roleId, id -> INCORRECT_ROLE_ID + id);
        return cache.get(RoleCacheKey.forId(roleId), () -> roleDao.findById(tenantId, roleId.getId()));
    }

    @Override
    public ListenableFuture<List<Role>> findRolesByIdsAsync(TenantId tenantId, List<RoleId> roleIds) {
        log.trace("Executing findRolesByIdsAsync, tenantId [{}], roleIds [{}]", tenantId, roleIds);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateIds(roleIds, ids -> "Incorrect roleIds " + ids);
        return roleDao.findRolesByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(roleIds));
    }

    @Override
    public Optional<Role> findRoleByTenantIdAndName(TenantId tenantId, String name) {
        log.trace("Executing findRoleByTenantIdAndName, tenantId [{}], name [{}]", tenantId, name);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateString(name, n -> INCORRECT_ROLE_NAME + n);
        return roleDao.findRoleByTenantIdAndName(tenantId.getId(), name);
    }

    @Override
    public ListenableFuture<Optional<Role>> findRoleByTenantIdAndNameAsync(TenantId tenantId, String name) {
        log.trace("Executing findRoleByTenantIdAndNameAsync, tenantId [{}], name [{}]", tenantId, name);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateString(name, n -> INCORRECT_ROLE_NAME + n);
        return executor.submit(() -> findRoleByTenantIdAndName(tenantId, name));
    }

    @Override
    public Optional<Role> findRoleByByTenantIdAndCustomerIdAndName(TenantId tenantId, CustomerId customerId, String name) {
        log.trace("Executing findRoleByByTenantIdAndCustomerIdAndName, tenantId [{}], customerId [{}], name [{}]", tenantId, customerId, name);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(customerId, id -> INCORRECT_CUSTOMER_ID + id);
        validateString(name, n -> INCORRECT_ROLE_NAME + n);
        return roleDao.findRoleByByTenantIdAndCustomerIdAndName(tenantId.getId(), customerId.getId(), name);
    }

    @Override
    public PageData<Role> findRolesByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findRolesByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return roleDao.findRolesByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<Role> findRolesByTenantIdAndType(TenantId tenantId, PageLink pageLink, RoleType type) {
        log.trace("Executing findRolesByTenantIdAndType, tenantId [{}], pageLink [{}], type [{}]", tenantId, pageLink, type);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return roleDao.findRolesByTenantIdAndType(tenantId.getId(), type, pageLink);
    }

    @Override
    public ListenableFuture<Role> findRoleByIdAsync(TenantId tenantId, RoleId roleId) {
        log.trace("Executing findRoleByIdAsync [{}]", roleId);
        validateId(roleId, id -> INCORRECT_ROLE_ID + id);
        return roleDao.findByIdAsync(tenantId, roleId.getId());
    }

    @Override
    public void deleteRole(TenantId tenantId, RoleId roleId) {
        log.trace("Executing deleteRole [{}]", roleId);
        validateId(roleId, id -> INCORRECT_ROLE_ID + id);
        groupPermissionService.deleteGroupPermissionsByTenantIdAndRoleId(tenantId, roleId);
        roleDao.removeById(tenantId, roleId.getId());
        publishEvictEvent(new RoleEvictEvent(roleId));
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(roleId).build());
    }

    @Override
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        deleteRole(tenantId, (RoleId) id);
    }

    @Override
    public void deleteRolesByTenantId(TenantId tenantId) {
        log.trace("Executing deleteRolesByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        tenantRoleRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        deleteRolesByTenantId(tenantId);
    }

    @Override
    public void deleteRolesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing deleteRolesByTenantIdAndCustomerId, tenantId [{}], customerId [{}]", tenantId, customerId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(customerId, id -> INCORRECT_CUSTOMER_ID + id);
        customerRoleRemover.removeEntities(tenantId, customerId);
    }

    @Override
    public Role findOrCreateRole(TenantId tenantId, CustomerId customerId, RoleType type, String name, Object permissions, String description) {
        Optional<Role> roleOptional;
        if (customerId != null && !customerId.isNullUid()) {
            roleOptional = findRoleByByTenantIdAndCustomerIdAndName(tenantId, customerId, name);
        } else {
            roleOptional = findRoleByTenantIdAndName(tenantId, name);
        }
        if (!roleOptional.isPresent()) {
            Role role = new Role();
            role.setTenantId(tenantId);
            if (customerId != null) {
                role.setCustomerId(customerId);
            } else {
                role.setCustomerId(new CustomerId(EntityId.NULL_UUID));
            }
            role.setName(name);
            role.setType(type);
            role.setPermissions(JacksonUtil.valueToTree(permissions));
            JsonNode additionalInfo = role.getAdditionalInfo();
            if (additionalInfo == null) {
                additionalInfo = JacksonUtil.newObjectNode();
            }
            ((ObjectNode) additionalInfo).put("description", description);
            role.setAdditionalInfo(additionalInfo);
            return saveRole(tenantId, role);
        } else {
            return roleOptional.get();
        }
    }

    @Override
    public Role findOrCreateTenantUserRole() {
        return findOrCreateRole(TenantId.SYS_TENANT_ID, null, RoleType.GENERIC, Role.ROLE_TENANT_USER_NAME,
                GroupPermission.READ_ONLY_USER_PERMISSIONS, "Autogenerated Tenant User role with read-only permissions.");
    }

    @Override
    public Role findOrCreateTenantAdminRole() {
        return findOrCreateRole(TenantId.SYS_TENANT_ID, null, RoleType.GENERIC, Role.ROLE_TENANT_ADMIN_NAME,
                GroupPermission.ALL_PERMISSIONS, "Autogenerated Tenant Administrator role with all permissions.");
    }

    @Override
    public Role findOrCreateCustomerUserRole(TenantId tenantId, CustomerId customerId) {
        return findOrCreateRole(tenantId, customerId, RoleType.GENERIC, Role.ROLE_CUSTOMER_USER_NAME,
                GroupPermission.READ_ONLY_USER_PERMISSIONS,
                "Autogenerated Customer User role with read-only permissions.");
    }

    @Override
    public Role findOrCreateCustomerAdminRole(TenantId tenantId, CustomerId customerId) {
        return findOrCreateRole(tenantId, customerId, RoleType.GENERIC, Role.ROLE_CUSTOMER_ADMIN_NAME,
                GroupPermission.ALL_PERMISSIONS,
                "Autogenerated Customer Administrator role with all permissions.");
    }

    @Override
    public Role findOrCreatePublicUsersEntityGroupRole(TenantId tenantId, CustomerId customerId) {
        return findOrCreateRole(tenantId, customerId, RoleType.GROUP, Role.ROLE_PUBLIC_USER_ENTITY_GROUP_NAME,
                GroupPermission.PUBLIC_USER_ENTITY_GROUP_PERMISSIONS, "Autogenerated Public User group role with read-only permissions.");
    }

    @Override
    public Role findOrCreatePublicUserRole(TenantId tenantId, CustomerId customerId) {
        return findOrCreateRole(tenantId, customerId, RoleType.GENERIC, Role.ROLE_PUBLIC_USER_NAME,
                GroupPermission.PUBLIC_USER_PERMISSIONS, "Autogenerated Public User role with read-only permissions.");
    }

    @Override
    public Role findOrCreateReadOnlyEntityGroupRole(TenantId tenantId, CustomerId customerId) {
        return findOrCreateRole(tenantId, customerId, RoleType.GROUP, Role.ROLE_READ_ONLY_ENTITY_GROUP_NAME,
                GroupPermission.READ_ONLY_GROUP_PERMISSIONS, "Autogenerated group role with read-only permissions.");
    }

    @Override
    public Role findOrCreateWriteEntityGroupRole(TenantId tenantId, CustomerId customerId) {
        return findOrCreateRole(tenantId, customerId, RoleType.GROUP, Role.ROLE_WRITE_ENTITY_GROUP_NAME,
                GroupPermission.WRITE_GROUP_PERMISSIONS, "Autogenerated group role with write permissions.");
    }

    @Override
    public PageData<Role> findRolesByTenantIdAndCustomerId(TenantId tenantId, CustomerId customerId, PageLink pageLink) {
        log.trace("Executing findRolesByTenantIdAndCustomerId, tenantId [{}], customerId [{}], pageLink [{}]", tenantId, customerId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(customerId, id -> INCORRECT_CUSTOMER_ID + id);
        validatePageLink(pageLink);
        return roleDao.findRolesByTenantIdAndCustomerId(tenantId.getId(), customerId.getId(), pageLink);
    }

    @Override
    public PageData<Role> findRolesByTenantIdAndCustomerIdAndType(TenantId tenantId, CustomerId customerId, RoleType type, PageLink pageLink) {
        log.trace("Executing findRolesByTenantIdAndCustomerId, tenantId [{}], customerId [{}], type [{}], pageLink [{}]", tenantId, customerId, type, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(customerId, id -> INCORRECT_CUSTOMER_ID + id);
        validatePageLink(pageLink);
        return roleDao.findRolesByTenantIdAndCustomerIdAndType(tenantId.getId(), customerId.getId(), type, pageLink);
    }

    private PaginatedRemover<TenantId, Role> tenantRoleRemover =
            new PaginatedRemover<TenantId, Role>() {

                @Override
                protected PageData<Role> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
                    return roleDao.findRolesByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Role entity) {
                    deleteRole(tenantId, new RoleId(entity.getUuidId()));
                }
            };

    private PaginatedRemover<CustomerId, Role> customerRoleRemover =
            new PaginatedRemover<CustomerId, Role>() {

                @Override
                protected PageData<Role> findEntities(TenantId tenantId, CustomerId customerId, PageLink pageLink) {
                    return roleDao.findRolesByTenantIdAndCustomerId(tenantId.getId(), customerId.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Role entity) {
                    deleteRole(tenantId, new RoleId(entity.getUuidId()));
                }
            };

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findRoleById(tenantId, new RoleId(entityId.getId())));
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.ROLE;
    }

}
