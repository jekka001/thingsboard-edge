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

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.group.ColumnConfiguration;
import org.thingsboard.server.common.data.group.ColumnType;
import org.thingsboard.server.common.data.group.EntityField;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupConfiguration;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.relation.RelationDao;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateEntityId;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Service
@Slf4j
public class BaseEntityGroupService extends AbstractEntityService implements EntityGroupService {

    public static final String ENTITY_GROUP_RELATION_PREFIX = "ENTITY_GROUP_";
    public static final String INCORRECT_PARENT_ENTITY_ID = "Incorrect parentEntityId ";
    public static final String INCORRECT_GROUP_TYPE = "Incorrect groupType ";
    public static final String INCORRECT_ENTITY_GROUP_ID = "Incorrect entityGroupId ";
    public static final String INCORRECT_ENTITY_ID = "Incorrect entityId ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String UNABLE_TO_FIND_ENTITY_GROUP_BY_ID = "Unable to find entity group by id ";

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ReentrantLock roleCreationLock = new ReentrantLock();

    @Autowired
    private EntityGroupDao entityGroupDao;

    @Autowired
    private RelationDao relationDao;

    @Autowired
    private AttributesService attributesService;

    @Autowired
    private TimeseriesService timeseriesService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Autowired
    private CustomerService customerService;

    @Override
    public EntityGroup findEntityGroupById(TenantId tenantId, EntityGroupId entityGroupId) {
        log.trace("Executing findEntityGroupById [{}]", entityGroupId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        return entityGroupDao.findById(tenantId, entityGroupId.getId());
    }

    @Override
    public ListenableFuture<EntityGroup> findEntityGroupByIdAsync(TenantId tenantId, EntityGroupId entityGroupId) {
        log.trace("Executing findEntityGroupByIdAsync [{}]", entityGroupId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        return entityGroupDao.findByIdAsync(tenantId, entityGroupId.getId());
    }

    @Override
    public ListenableFuture<List<EntityGroup>> findEntityGroupByIdsAsync(TenantId tenantId, List<EntityGroupId> entityGroupIds) {
        log.trace("Executing findEntityGroupByIdsAsync, entityGroupIds [{}]", entityGroupIds);
        validateIds(entityGroupIds, "Incorrect entityGroupIds " + entityGroupIds);
        return entityGroupDao.findEntityGroupsByIdsAsync(tenantId.getId(), toUUIDs(entityGroupIds));
    }

    @Override
    public EntityGroup saveEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroup entityGroup) {
        log.trace("Executing saveEntityGroup [{}]", entityGroup);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        if (entityGroup.getId() == null) {
            entityGroup.setOwnerId(parentEntityId);
        }
        new EntityGroupValidator(parentEntityId).validate(entityGroup, data -> tenantId);
        if (entityGroup.getId() == null && entityGroup.getConfiguration() == null) {
            EntityGroupConfiguration entityGroupConfiguration =
                    EntityGroupConfiguration.createDefaultEntityGroupConfiguration(entityGroup.getType());
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode jsonConfiguration = mapper.valueToTree(entityGroupConfiguration);
            jsonConfiguration.putObject("settings");
            jsonConfiguration.putObject("actions");
            entityGroup.setConfiguration(jsonConfiguration);
        }
        EntityGroup savedEntityGroup = entityGroupDao.save(tenantId, entityGroup);
        if (entityGroup.getId() == null) {
            EntityRelation entityRelation = new EntityRelation();
            entityRelation.setFrom(parentEntityId);
            entityRelation.setTo(savedEntityGroup.getId());
            entityRelation.setTypeGroup(RelationTypeGroup.TO_ENTITY_GROUP);
            entityRelation.setType(ENTITY_GROUP_RELATION_PREFIX + savedEntityGroup.getType().name());
            relationService.saveRelation(tenantId, entityRelation);
        }
        return savedEntityGroup;
    }

    @Override
    public ListenableFuture<Boolean> checkEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroup entityGroup) {
        log.trace("Executing checkEntityGroup [{}]", entityGroup);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        return relationService.checkRelation(tenantId, parentEntityId, entityGroup.getId(),
                ENTITY_GROUP_RELATION_PREFIX + entityGroup.getType().name()
                , RelationTypeGroup.TO_ENTITY_GROUP);
    }

    @Override
    public ListenableFuture<Boolean> checkEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityGroupId entityGroupId, EntityType groupType) {
        log.trace("Executing checkEntityGroup, entityGroupId [{}], groupType [{}]", entityGroupId, groupType);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        validateEntityId(parentEntityId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        if (groupType == null) {
            throw new IncorrectParameterException(INCORRECT_GROUP_TYPE + groupType);
        }
        return relationService.checkRelation(tenantId, parentEntityId, entityGroupId,
                ENTITY_GROUP_RELATION_PREFIX + groupType.name()
                , RelationTypeGroup.TO_ENTITY_GROUP);
    }

    @Override
    public EntityGroup createEntityGroupAll(TenantId tenantId, EntityId parentEntityId, EntityType groupType) {
        log.trace("Executing createEntityGroupAll, parentEntityId [{}], groupType [{}]", parentEntityId, groupType);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        if (groupType == null) {
            throw new IncorrectParameterException(INCORRECT_GROUP_TYPE + groupType);
        }
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName(EntityGroup.GROUP_ALL_NAME);
        entityGroup.setType(groupType);
        return saveEntityGroup(tenantId, parentEntityId, entityGroup);
    }

    @Override
    public EntityGroup findOrCreateUserGroup(TenantId tenantId, EntityId parentEntityId, String groupName, String description) {
        log.trace("Executing findOrCreateUserGroup, parentEntityId [{}], groupName [{}]", parentEntityId, groupName);
        return findOrCreateEntityGroup(tenantId, parentEntityId, EntityType.USER, groupName, description, null);
    }

    @Override
    public EntityGroup findOrCreateEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String groupName,
                                               String description, CustomerId publicCustomerId) {
        log.trace("Executing findOrCreateEntityGroup, parentEntityId [{}], groupType [{}], groupName [{}]", parentEntityId, groupType, groupName);
        try {
            Optional<EntityGroup> entityGroupOptional = findEntityGroupByTypeAndName(tenantId, parentEntityId, groupType, groupName).get();
            if (entityGroupOptional.isPresent()) {
                return entityGroupOptional.get();
            } else {
                EntityGroup entityGroup = new EntityGroup();
                entityGroup.setName(groupName);
                entityGroup.setType(groupType);
                JsonNode additionalInfo = entityGroup.getAdditionalInfo();
                if (additionalInfo == null) {
                    additionalInfo = mapper.createObjectNode();
                }
                ((ObjectNode) additionalInfo).put("description", description);
                if (publicCustomerId != null && !publicCustomerId.isNullUid()) {
                    ((ObjectNode) additionalInfo).put("isPublic", true);
                    ((ObjectNode) additionalInfo).put("publicCustomerId", publicCustomerId.getId().toString());
                }
                entityGroup.setAdditionalInfo(additionalInfo);
                return saveEntityGroup(tenantId, parentEntityId, entityGroup);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Unable find or create entity group!", e);
        }
    }

    @Override
    public Optional<EntityGroup> findOwnerEntityGroup(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String groupName) {
        log.trace("Executing findEntityGroup, parentEntityId [{}], groupType [{}], groupName [{}]", parentEntityId, groupType, groupName);
        try {
            return findEntityGroupByTypeAndName(tenantId, parentEntityId, groupType, groupName).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Entity group with name: " + groupName + " and type: " + groupType + " doesn't exist!", e);
        }
    }


    @Override
    public EntityGroup findOrCreateTenantUsersGroup(TenantId tenantId) {

        // User Group 'Tenant Users' -> 'Tenant User' role -> Read only permissions

        EntityGroup tenantUsers = findOrCreateUserGroup(tenantId,
                tenantId, EntityGroup.GROUP_TENANT_USERS_NAME, "Autogenerated Tenant Users group with read-only permissions.");
        roleCreationLock.lock();
        try {
            log.trace("Executing findOrCreateTenantUserRole, TenantId [{}]", tenantId);
            Role tenantUserRole = roleService.findOrCreateTenantUserRole();
            log.trace("Executing findOrCreateUserGroupPermission, ParentEntityId [{}], groupType [{}], groupName [{}]", tenantId, tenantUsers.getType(), tenantUsers.getName());
            findOrCreateUserGroupPermission(tenantId, tenantUsers.getId(), tenantUserRole.getId());
        } catch (Exception e) {
            log.trace("Unexpected error during execution findOrCreateTenantUserRole & findOrCreateUserGroupPermission: ", e);
        } finally {
            roleCreationLock.unlock();
        }
        return tenantUsers;
    }

    @Override
    public EntityGroup findOrCreateTenantAdminsGroup(TenantId tenantId) {

        // User Group 'Tenant Administrators' -> 'Tenant Administrator' role -> All permissions

        EntityGroup tenantAdmins = findOrCreateUserGroup(tenantId,
                tenantId, EntityGroup.GROUP_TENANT_ADMINS_NAME, "Autogenerated Tenant Administrators group with all permissions.");
        roleCreationLock.lock();
        try {
            log.trace("Executing findOrCreateTenantAdminRole, TenantId [{}]", tenantId);
            Role tenantAdminRole = roleService.findOrCreateTenantAdminRole();
            log.trace("Executing findOrCreateUserGroupPermission, ParentEntityId [{}], groupType [{}], groupName [{}]", tenantId, tenantAdmins.getType(), tenantAdmins.getName());
            findOrCreateUserGroupPermission(tenantId, tenantAdmins.getId(), tenantAdminRole.getId());
        } catch (Exception e) {
            log.trace("Unexpected error during execution findOrCreateTenantAdminRole & findOrCreateUserGroupPermission: ", e);
        } finally {
            roleCreationLock.unlock();
        }
        return tenantAdmins;
    }

    @Override
    public EntityGroup findOrCreateCustomerUsersGroup(TenantId tenantId, CustomerId customerId, CustomerId parentCustomerId) {

        // User Group 'Customer Users' -> 'Customer User' role -> Read only permissions

        EntityGroup customerUsers = findOrCreateUserGroup(tenantId, customerId,
                EntityGroup.GROUP_CUSTOMER_USERS_NAME, "Autogenerated Customer Users group with read-only permissions.");
        roleCreationLock.lock();
        try {
            log.trace("Executing findOrCreateCustomerUserRole, TenantId [{}]", tenantId);
            Role customerUserRole = roleService.findOrCreateCustomerUserRole(tenantId, parentCustomerId);
            log.trace("Executing findOrCreateUserGroupPermission, ParentEntityId [{}], groupType [{}], groupName [{}]", tenantId, customerUsers.getType(), customerUsers.getName());
            findOrCreateUserGroupPermission(tenantId, customerUsers.getId(), customerUserRole.getId());
        } catch (Exception e) {
            log.trace("Unexpected error during execution findOrCreateCustomerUserRole & findOrCreateUserGroupPermission: ", e);
        } finally {
            roleCreationLock.unlock();
        }
        return customerUsers;
    }

    @Override
    public EntityGroup findOrCreateCustomerAdminsGroup(TenantId tenantId, CustomerId customerId, CustomerId parentCustomerId) {

        // User Group 'Customer Administrators' -> 'Customer Administrator' role -> All permissions

        EntityGroup customerAdmins = findOrCreateUserGroup(tenantId, customerId,
                EntityGroup.GROUP_CUSTOMER_ADMINS_NAME, "Autogenerated Customer Administrators group with all permissions.");
        roleCreationLock.lock();
        try {
            log.trace("Executing findOrCreateCustomerAdminRole, TenantId [{}]", tenantId);
            Role customerAdminRole = roleService.findOrCreateCustomerAdminRole(tenantId, parentCustomerId);
            log.trace("Executing findOrCreateUserGroupPermission, ParentEntityId [{}], groupType [{}], groupName [{}]", tenantId, customerAdmins.getType(), customerAdmins.getName());
            findOrCreateUserGroupPermission(tenantId, customerAdmins.getId(), customerAdminRole.getId());
        } catch (Exception e) {
            log.trace("Unexpected error during execution findOrCreateCustomerAdminRole & findOrCreateUserGroupPermission: ", e);
        } finally {
            roleCreationLock.unlock();
        }
        return customerAdmins;
    }

    @Override
    public EntityGroup findOrCreatePublicUsersGroup(TenantId tenantId, CustomerId customerId) {
        EntityGroup publicUsers = findOrCreateUserGroup(tenantId, customerId,
                EntityGroup.GROUP_PUBLIC_USERS_NAME, "Autogenerated Public Users group with read-only permissions.");
        roleCreationLock.lock();
        try {
            log.trace("Executing findOrCreatePublicUserRole, TenantId [{}]", tenantId);
            Role publicUserRole = roleService.findOrCreatePublicUserRole(tenantId, customerId);
            log.trace("Executing findOrCreateUserGroupPermission, ParentEntityId [{}], groupType [{}], groupName [{}]", tenantId, publicUsers.getType(), publicUsers.getName());
            findOrCreateUserGroupPermission(tenantId, publicUsers.getId(), publicUserRole.getId());
        } catch (Exception e) {
            log.trace("Unexpected error during execution findOrCreatePublicUserRole & findOrCreateUserGroupPermission: ", e);
        } finally {
            roleCreationLock.unlock();
        }
        return publicUsers;
    }

    @Override
    public EntityGroup findOrCreateReadOnlyEntityGroupForCustomer(TenantId tenantId, CustomerId customerId, EntityType groupType) {

        Customer customer = customerService.findCustomerById(tenantId, customerId);
        if (customer == null) {
            throw new RuntimeException("Customer with id '" + customerId.getId().toString() + "' is not present in database!");
        }

        String groupName = customer.getTitle();
        String description = "Autogenerated ";
        if (customer.isPublic()) {
            description += "Public ";
        }
        switch (groupType) {
            case DEVICE:
                groupName += " Devices";
                description += "Device";
                break;
            case ASSET:
                groupName += " Assets";
                description += "Asset";
                break;
            case ENTITY_VIEW:
                groupName += " Entity Views";
                description += "Entity View";
                break;
            case DASHBOARD:
                groupName += " Dashboards";
                description += "Dashboard";
                break;
            default:
                throw new RuntimeException("Invalid entity group type '" + groupType + "' specified for read-only entity group for customer!");
        }
        if (customer.isPublic()) {
            description += " group";
        } else {
            description += " group with read-only access for customer '" + customer.getTitle() + "'";
        }
        EntityGroup group;
        if (customer.isPublic()) {
            group = findOrCreateEntityGroup(tenantId, tenantId, groupType, groupName, description, customer.getId());
            EntityGroup publicUsers = findOrCreatePublicUsersGroup(tenantId, customer.getId());
            Role publicUserEntityGroupRole = roleService.findOrCreatePublicUsersEntityGroupRole(tenantId, customer.getId());
            findOrCreateEntityGroupPermission(tenantId, group.getId(), group.getType(), publicUsers.getId(), publicUserEntityGroupRole.getId(), true);
        } else {
            group = findOrCreateEntityGroup(tenantId, tenantId, groupType, groupName, description, null);
            Role readOnlyGroupRole = roleService.findOrCreateReadOnlyEntityGroupRole(tenantId, null);
            EntityGroup customerUsers = findOrCreateCustomerUsersGroup(tenantId, customer.getId(), null);
            findOrCreateEntityGroupPermission(tenantId, group.getId(), group.getType(), customerUsers.getId(), readOnlyGroupRole.getId(), false);
        }
        return group;
    }

    @Override
    public ListenableFuture<Optional<EntityGroup>> findPublicUserGroup(TenantId tenantId, CustomerId publicCustomerId) {
        log.trace("Executing findPublicUserGroup, tenantId [{}], publicCustomerId [{}]", tenantId, publicCustomerId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validateId(publicCustomerId, INCORRECT_CUSTOMER_ID + publicCustomerId);
        return findEntityGroupByTypeAndName(tenantId, publicCustomerId, EntityType.USER, EntityGroup.GROUP_PUBLIC_USERS_NAME);
    }

    private GroupPermission findOrCreateUserGroupPermission(TenantId tenantId, EntityGroupId userGroupId, RoleId roleId) {
        List<GroupPermission> userGroupPermissions =
                groupPermissionService.findGroupPermissionByTenantIdAndUserGroupIdAndRoleId(tenantId,
                        userGroupId, roleId, new PageLink(Integer.MAX_VALUE)).getData();
        if (userGroupPermissions.isEmpty()) {
            GroupPermission userGroupPermission = new GroupPermission();
            userGroupPermission.setTenantId(tenantId);
            userGroupPermission.setUserGroupId(userGroupId);
            userGroupPermission.setRoleId(roleId);
            return groupPermissionService.saveGroupPermission(tenantId, userGroupPermission);
        } else {
            return userGroupPermissions.get(0);
        }
    }

    private GroupPermission findOrCreateEntityGroupPermission(TenantId tenantId, EntityGroupId entityGroupId,
                                                              EntityType entityGroupType,
                                                              EntityGroupId userGroupId, RoleId roleId, boolean isPublic) {
        List<GroupPermission> entityGroupPermissions =
                groupPermissionService.findGroupPermissionByTenantIdAndEntityGroupIdAndUserGroupIdAndRoleId(tenantId,
                        entityGroupId, userGroupId, roleId, new PageLink(Integer.MAX_VALUE)).getData();
        if (entityGroupPermissions.isEmpty()) {
            GroupPermission entityGroupPermission = new GroupPermission();
            entityGroupPermission.setTenantId(tenantId);
            entityGroupPermission.setEntityGroupId(entityGroupId);
            entityGroupPermission.setEntityGroupType(entityGroupType);
            entityGroupPermission.setUserGroupId(userGroupId);
            entityGroupPermission.setRoleId(roleId);
            entityGroupPermission.setPublic(isPublic);
            return groupPermissionService.saveGroupPermission(tenantId, entityGroupPermission);
        } else {
            return entityGroupPermissions.get(0);
        }
    }

    @Override
    public void deleteEntityGroup(TenantId tenantId, EntityGroupId entityGroupId) {
        log.trace("Executing deleteEntityGroup [{}]", entityGroupId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        groupPermissionService.deleteGroupPermissionsByTenantIdAndUserGroupId(tenantId, entityGroupId);
        groupPermissionService.deleteGroupPermissionsByTenantIdAndEntityGroupId(tenantId, entityGroupId);
        deleteEntityRelations(tenantId, entityGroupId);
        entityGroupDao.removeById(tenantId, entityGroupId.getId());
    }

    @Override
    public ListenableFuture<List<EntityGroup>> findAllEntityGroups(TenantId tenantId, EntityId parentEntityId) {
        log.trace("Executing findAllEntityGroups, parentEntityId [{}]", parentEntityId);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        return this.entityGroupDao.findAllEntityGroups(tenantId.getId(), parentEntityId.getId(), parentEntityId.getEntityType());
    }

    @Override
    public void deleteAllEntityGroups(TenantId tenantId, EntityId parentEntityId) {
        log.trace("Executing deleteAllEntityGroups, parentEntityId [{}]", parentEntityId);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        ListenableFuture<List<EntityGroup>> entityGroupsFuture = findAllEntityGroups(tenantId, parentEntityId);
        try {
            List<EntityGroup> entityGroups = entityGroupsFuture.get();
            entityGroups.forEach(entityGroup -> deleteEntityGroup(tenantId, entityGroup.getId()));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Unable to delete entity groups", e);
        }
    }

    @Override
    public ListenableFuture<List<EntityGroup>> findEntityGroupsByType(TenantId tenantId, EntityId parentEntityId, EntityType groupType) {
        log.trace("Executing findEntityGroupsByType, parentEntityId [{}], groupType [{}]", parentEntityId, groupType);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        if (groupType == null) {
            throw new IncorrectParameterException(INCORRECT_GROUP_TYPE + groupType);
        }
        String relationType = ENTITY_GROUP_RELATION_PREFIX + groupType.name();
        return this.entityGroupDao.findEntityGroupsByType(tenantId.getId(), parentEntityId.getId(), parentEntityId.getEntityType(), relationType);
    }

    @Override
    public ListenableFuture<PageData<EntityGroup>> findEntityGroupsByTypeAndPageLink(TenantId tenantId, EntityId parentEntityId,
                                                                                     EntityType groupType, PageLink pageLink) {
        log.trace("Executing findEntityGroupsByTypeAndPageLink, parentEntityId [{}], groupType [{}], pageLink [{}]", parentEntityId, groupType, pageLink);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        if (groupType == null) {
            throw new IncorrectParameterException(INCORRECT_GROUP_TYPE + groupType);
        }
        validatePageLink(pageLink);
        String relationType = ENTITY_GROUP_RELATION_PREFIX + groupType.name();
        return this.entityGroupDao.findEntityGroupsByTypeAndPageLink(tenantId.getId(), parentEntityId.getId(),
                parentEntityId.getEntityType(), relationType, pageLink);
    }

    @Override
    public ListenableFuture<Optional<EntityGroup>> findEntityGroupByTypeAndName(TenantId tenantId, EntityId parentEntityId, EntityType groupType, String name) {
        log.trace("Executing findEntityGroupByTypeAndName, parentEntityId [{}], groupType [{}], name [{}]", parentEntityId, groupType, name);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        if (groupType == null) {
            throw new IncorrectParameterException(INCORRECT_GROUP_TYPE + groupType);
        }
        validateString(name, "Incorrect name " + name);
        String relationType = ENTITY_GROUP_RELATION_PREFIX + groupType.name();
        return this.entityGroupDao.findEntityGroupByTypeAndName(tenantId.getId(), parentEntityId.getId(),
                parentEntityId.getEntityType(), relationType, name);
    }

    @Override
    public void addEntityToEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId) {
        log.trace("Executing addEntityToEntityGroup, entityGroupId [{}], entityId [{}]", entityGroupId, entityId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        validateEntityId(entityId, INCORRECT_ENTITY_ID + entityId);
        EntityRelation entityRelation = new EntityRelation();
        entityRelation.setFrom(entityGroupId);
        entityRelation.setTo(entityId);
        entityRelation.setTypeGroup(RelationTypeGroup.FROM_ENTITY_GROUP);
        entityRelation.setType(EntityRelation.CONTAINS_TYPE);
        relationService.saveRelation(tenantId, entityRelation);
    }

    @Override
    public void addEntityToEntityGroupAll(TenantId tenantId, EntityId parentEntityId, EntityId entityId) {
        log.trace("Executing addEntityToEntityGroupAll, parentEntityId [{}], entityId [{}]", parentEntityId, entityId);
        validateEntityId(parentEntityId, INCORRECT_PARENT_ENTITY_ID + parentEntityId);
        validateEntityId(entityId, INCORRECT_ENTITY_ID + entityId);
        try {
            Optional<EntityGroup> entityGroup = findEntityGroupByTypeAndName(tenantId, parentEntityId, entityId.getEntityType(), EntityGroup.GROUP_ALL_NAME).get();
            if (entityGroup.isPresent()) {
                addEntityToEntityGroup(tenantId, entityGroup.get().getId(), entityId);
            } else {
                throw new DataValidationException("Group All of type " + entityId.getEntityType() + " is absent for entityId " + parentEntityId);
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Unable to add entity to group All", e);
        }
    }

    @Override
    public void addEntitiesToEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, List<EntityId> entityIds) {
        log.trace("Executing addEntitiesToEntityGroup, entityGroupId [{}], entityIds [{}]", entityGroupId, entityIds);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        entityIds.forEach(entityId -> addEntityToEntityGroup(tenantId, entityGroupId, entityId));
    }

    @Override
    public void removeEntityFromEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId) {
        log.trace("Executing removeEntityFromEntityGroup, entityGroupId [{}], entityId [{}]", entityGroupId, entityId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        validateEntityId(entityId, INCORRECT_ENTITY_ID + entityId);
        relationService.deleteRelation(tenantId, entityGroupId, entityId, EntityRelation.CONTAINS_TYPE, RelationTypeGroup.FROM_ENTITY_GROUP);
    }

    @Override
    public void removeEntitiesFromEntityGroup(TenantId tenantId, EntityGroupId entityGroupId, List<EntityId> entityIds) {
        log.trace("Executing removeEntitiesFromEntityGroup, entityGroupId [{}], entityIds [{}]", entityGroupId, entityIds);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        entityIds.forEach(entityId -> removeEntityFromEntityGroup(tenantId, entityGroupId, entityId));
    }

    @Override
    public ShortEntityView findGroupEntity(TenantId tenantId, EntityGroupId entityGroupId, EntityId entityId) {
        log.trace("Executing findGroupEntity, entityGroupId [{}], entityId [{}]", entityGroupId, entityId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        validateEntityId(entityId, INCORRECT_ENTITY_ID + entityId);
        EntityGroup entityGroup = findEntityGroupById(tenantId, entityGroupId);
        if (entityGroup == null) {
            throw new IncorrectParameterException(UNABLE_TO_FIND_ENTITY_GROUP_BY_ID + entityGroupId);
        }
        List<ColumnConfiguration> columns = getEntityGroupColumns(entityGroup);
        return this.entityGroupDao.findGroupEntity(entityId, entityGroupId.getId(), columns);
    }

    @Override
    public PageData<ShortEntityView> findGroupEntities(TenantId tenantId, EntityGroupId entityGroupId, PageLink pageLink) {
        log.trace("Executing findGroupEntities, entityGroupId [{}], pageLink [{}]", entityGroupId, pageLink);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        validatePageLink(pageLink);
        EntityGroup entityGroup = findEntityGroupById(tenantId, entityGroupId);
        if (entityGroup == null) {
            throw new IncorrectParameterException(UNABLE_TO_FIND_ENTITY_GROUP_BY_ID + entityGroupId);
        }
        List<ColumnConfiguration> columns = getEntityGroupColumns(entityGroup);
        return this.entityGroupDao.findGroupEntities(entityGroup.getType(), entityGroupId.getId(), columns, pageLink);
    }

    @Override
    public ListenableFuture<List<EntityId>> findAllEntityIds(TenantId tenantId, EntityGroupId entityGroupId, TimePageLink pageLink) {
        log.trace("Executing findEntities, entityGroupId [{}], pageLink [{}]", entityGroupId);
        validateId(entityGroupId, INCORRECT_ENTITY_GROUP_ID + entityGroupId);
        EntityGroup entityGroup = findEntityGroupById(tenantId, entityGroupId);
        if (entityGroup == null) {
            throw new IncorrectParameterException(UNABLE_TO_FIND_ENTITY_GROUP_BY_ID + entityGroupId);
        }
        return findEntityIds(tenantId, entityGroupId, entityGroup.getType(), pageLink);
    }

    @Override
    public ListenableFuture<List<EntityGroupId>> findEntityGroupsForEntity(TenantId tenantId, EntityId entityId) {
        ListenableFuture<List<EntityRelation>> relations = relationDao.findAllByToAndType(tenantId, entityId,
                EntityRelation.CONTAINS_TYPE, RelationTypeGroup.FROM_ENTITY_GROUP);
        return Futures.transform(relations, input -> {
            List<EntityGroupId> entityGroupIds = new ArrayList<>(input.size());
            for (EntityRelation relation : input) {
                entityGroupIds.add(new EntityGroupId(relation.getFrom().getId()));
            }
            return entityGroupIds;
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<List<EntityId>> findEntityIds(TenantId tenantId, EntityGroupId entityGroupId, EntityType groupType, TimePageLink pageLink) {
        ListenableFuture<PageData<EntityRelation>> relations = relationDao.findRelations(tenantId, entityGroupId,
                EntityRelation.CONTAINS_TYPE, RelationTypeGroup.FROM_ENTITY_GROUP, groupType, pageLink);
        return Futures.transform(relations, input -> {
            List<EntityId> entityIds = new ArrayList<>(input.getData().size());
            for (EntityRelation relation : input.getData()) {
                entityIds.add(relation.getTo());
            }
            return entityIds;
        }, MoreExecutors.directExecutor());
    }

    private List<ColumnConfiguration> getEntityGroupColumns(EntityGroup entityGroup) {
        JsonNode jsonConfiguration = entityGroup.getConfiguration();
        List<ColumnConfiguration> columns = null;
        if (jsonConfiguration != null) {
            try {
                EntityGroupConfiguration entityGroupConfiguration =
                        new ObjectMapper().treeToValue(jsonConfiguration, EntityGroupConfiguration.class);
                columns = entityGroupConfiguration.getColumns();
            } catch (JsonProcessingException e) {
                log.error("Unable to read entity group configuration", e);
                throw new RuntimeException("Unable to read entity group configuration", e);
            }
        }
        if (columns == null) {
            columns = Collections.emptyList();
        }
        return columns;
    }

    private EntityGroupColumnsInfo getEntityGroupColumnsInfo(EntityGroup entityGroup) {
        List<ColumnConfiguration> columns = getEntityGroupColumns(entityGroup);
        EntityGroupColumnsInfo columnsInfo = new EntityGroupColumnsInfo();
        columns.forEach(column -> {
            if (column.getType() == ColumnType.ENTITY_FIELD) {
                processEntityFieldColumnInfo(column, columnsInfo);
            } else if (column.getType().isAttribute()) {
                processAttributeColumnInfo(column, columnsInfo);
            } else if (column.getType() == ColumnType.TIMESERIES) {
                columnsInfo.timeseriesKeys.add(column.getKey());
            }
        });
        return columnsInfo;
    }

    private void processEntityFieldColumnInfo(ColumnConfiguration column, EntityGroupColumnsInfo columnsInfo) {
        EntityField entityField = null;
        try {
            entityField = EntityField.valueOf(column.getKey().toUpperCase());
        } catch (Exception e) {
        }
        if (entityField != null) {
            if (entityField == EntityField.CREATED_TIME) {
                columnsInfo.commonEntityFields.add(entityField);
            } else {
                columnsInfo.entityFields.add(entityField);
            }
        }
    }

    private void processAttributeColumnInfo(ColumnConfiguration column, EntityGroupColumnsInfo columnsInfo) {
        String scope = column.getType().getAttributeScope();
        List<String> keys = columnsInfo.attributeScopeToKeysMap.get(scope);
        if (keys == null) {
            keys = new ArrayList<>();
            columnsInfo.attributeScopeToKeysMap.put(scope, keys);
        }
        keys.add(column.getKey());
    }

    private <E extends BaseData> ShortEntityView toEntityView(TenantId tenantId, E entity, EntityGroupColumnsInfo columnsInfo,
                                                              BiFunction<E, List<EntityField>, ShortEntityView> transformFunction) {
        ShortEntityView entityView = transformFunction.apply(entity, columnsInfo.entityFields);
        for (EntityField entityField : columnsInfo.commonEntityFields) {
            if (entityField == EntityField.CREATED_TIME) {
                long timestamp = Uuids.unixTimestamp(entity.getId().getId());
                entityView.put(EntityField.CREATED_TIME.name().toLowerCase(), timestamp + "");
            }
        }
        if (!entityView.isSkipEntity()) {
            fetchEntityAttributes(tenantId, entityView, columnsInfo.attributeScopeToKeysMap, columnsInfo.timeseriesKeys);
        }
        return entityView;
    }

    private void fetchEntityAttributes(TenantId tenantId, ShortEntityView entityView,
                                       Map<String, List<String>> attributeScopeToKeysMap,
                                       List<String> timeseriesKeys) {
        EntityId entityId = entityView.getId();
        attributeScopeToKeysMap.forEach((scope, attributeKeys) -> {
            try {
                List<AttributeKvEntry> attributeKvEntries = attributesService.find(tenantId, entityId, scope, attributeKeys).get();
                attributeKvEntries.forEach(attributeKvEntry -> {
                    entityView.put(attributeKvEntry.getKey(), attributeKvEntry.getValueAsString());
                });
            } catch (InterruptedException | ExecutionException e) {
                log.error("Unable to fetch entity attributes", e);
            }
        });
        if (!timeseriesKeys.isEmpty()) {
            try {
                List<TsKvEntry> tsKvEntries = timeseriesService.findLatest(tenantId, entityId, timeseriesKeys).get();
                tsKvEntries.forEach(tsKvEntry -> {
                    entityView.put(tsKvEntry.getKey(), tsKvEntry.getValueAsString());
                });
            } catch (InterruptedException | ExecutionException e) {
                log.error("Unable to fetch entity telemetry timeseries", e);
            }
        }
    }

    private class EntityGroupColumnsInfo {
        List<EntityField> commonEntityFields = new ArrayList<>();
        List<EntityField> entityFields = new ArrayList<>();
        Map<String, List<String>> attributeScopeToKeysMap = new HashMap<>();
        List<String> timeseriesKeys = new ArrayList<>();
    }

    private class EntityGroupValidator extends DataValidator<EntityGroup> {

        private final EntityId parentEntityId;

        EntityGroupValidator(EntityId parentEntityId) {
            this.parentEntityId = parentEntityId;
        }

        @Override
        protected void validateCreate(TenantId tenantId, EntityGroup entityGroup) {
            try {
                findEntityGroupByTypeAndName(tenantId, this.parentEntityId, entityGroup.getType(), entityGroup.getName()).get().ifPresent(
                        d -> {
                            throw new DataValidationException("Entity group with such name already present in " +
                                    this.parentEntityId.getEntityType().toString() + "!");
                        }
                );
            } catch (InterruptedException | ExecutionException e) {
                log.error("Unable to validate creation of entity group.", e);
            }
        }

        @Override
        protected void validateUpdate(TenantId tenantId, EntityGroup entityGroup) {
            try {
                findEntityGroupByTypeAndName(tenantId, this.parentEntityId, entityGroup.getType(), entityGroup.getName()).get().ifPresent(
                        d -> {
                            if (!d.getId().equals(entityGroup.getId())) {
                                throw new DataValidationException("Entity group with such name already present in " +
                                        this.parentEntityId.getEntityType().toString() + "!");
                            }
                        }
                );
            } catch (InterruptedException | ExecutionException e) {
                log.error("Unable to validate update of entity group.", e);
            }
        }

        @Override
        protected void validateDataImpl(TenantId tenantId, EntityGroup entityGroup) {
            if (entityGroup.getType() == null) {
                throw new DataValidationException("Entity group type should be specified!");
            }
            if (StringUtils.isEmpty(entityGroup.getName())) {
                throw new DataValidationException("Entity group name should be specified!");
            }
            if (entityGroup.getOwnerId() == null || entityGroup.getOwnerId().isNullUid()) {
                throw new DataValidationException("Entity group ownerId should be specified!");
            }
        }
    }

}
