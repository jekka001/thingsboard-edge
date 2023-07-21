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
package org.thingsboard.server.controller;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.ContactBased;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ShortEntityView;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.group.EntityGroupInfo;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.ota.DeviceGroupOtaPackage;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.permission.GroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedGroupPermissionInfo;
import org.thingsboard.server.common.data.permission.MergedGroupTypePermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.permission.ShareGroupRequest;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.dao.owner.OwnerService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.entity.group.TbEntityGroupService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.OwnersCacheService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.group.EntityGroup.EDGE_ENTITY_GROUP_TYPE_ALLOWABLE_VALUES;
import static org.thingsboard.server.controller.ControllerConstants.EDGE_ASSIGN_RECEIVE_STEP_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.EDGE_ID;
import static org.thingsboard.server.controller.ControllerConstants.EDGE_ID_PARAM_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.EDGE_UNASSIGN_RECEIVE_STEP_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.ENTITY_GROUP_ID;
import static org.thingsboard.server.controller.ControllerConstants.ENTITY_GROUP_ID_PARAM_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.ENTITY_GROUP_INCLUDE_SHARED_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.ENTITY_ID;
import static org.thingsboard.server.controller.ControllerConstants.ENTITY_ID_PARAM_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_DATA_PARAMETERS;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_NUMBER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_SIZE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_GROUP_ADD_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_GROUP_DELETE_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_GROUP_READ_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_GROUP_REMOVE_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_GROUP_WRITE_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_READ_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.RBAC_WRITE_CHECK;
import static org.thingsboard.server.controller.ControllerConstants.SORT_ORDER_ALLOWABLE_VALUES;
import static org.thingsboard.server.controller.ControllerConstants.SORT_ORDER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_PROPERTY_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH;
import static org.thingsboard.server.controller.ControllerConstants.UUID_WIKI_LINK;
import static org.thingsboard.server.dao.service.Validator.validateEntityId;

@RestController
@TbCoreComponent
@RequiredArgsConstructor
@RequestMapping("/api")
public class EntityGroupController extends AutoCommitController {

    private final TbEntityGroupService tbEntityGroupService;
    private final OwnersCacheService ownersCacheService;

    private final OwnerService ownerService;

    public static final String ENTITY_GROUP_DESCRIPTION = "Entity group allows you to group multiple entities of the same entity type (Device, Asset, Customer, User, Dashboard, etc). " +
            "Entity Group always have an owner - particular Tenant or Customer. Each entity may belong to multiple groups simultaneously.";

    public static final String ENTITY_GROUP_INFO_DESCRIPTION = "Entity Group Info extends Entity Group object and adds 'ownerIds' - a list of owner ids.";

    private static final String ENTITY_GROUP_ENTITY_INFO_DESCRIPTION = "Entity Info is a lightweight object that contains only id and name of the entity group. ";
    private static final String ENTITY_GROUP_UNIQUE_KEY = "Entity group name is unique in the scope of owner and entity type. For example, you can't create two tenant device groups called 'Water meters'. " +
            "However, you may create device and asset group with the same name. And also you may create groups with the same name for two different customers of the same tenant. ";
    private static final String OWNER_TYPE_DESCRIPTION = "Tenant or Customer";
    private static final String OWNER_ID_DESCRIPTION = "A string value representing the Tenant or Customer id";
    private static final String ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION = "Entity Group type";
    private static final String SHORT_ENTITY_VIEW_DESCRIPTION = "Short Entity View object contains the entity id and number of fields (attributes, telemetry, etc). " +
            "List of those fields is configurable and defined in the group configuration.";

    @ApiOperation(value = "Get Entity Group Info (getEntityGroupById)",
            notes = "Fetch the Entity Group object based on the provided Entity Group Id. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    "\n\n" + ENTITY_GROUP_UNIQUE_KEY +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityGroupInfo getEntityGroupById(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        return checkEntityGroupId(entityGroupId, Operation.READ);
    }

    @ApiOperation(value = "Get Entity Group Entity Info (getEntityGroupEntityInfoById)",
            notes = "Fetch the Entity Group Entity Info object based on the provided Entity Group Id. "
                    + ENTITY_GROUP_ENTITY_INFO_DESCRIPTION +
                    "\n\n" + ENTITY_GROUP_UNIQUE_KEY +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupInfo/{entityGroupId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityInfo getEntityGroupEntityInfoById(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
        return new EntityInfo(entityGroup.getId(), entityGroup.getName());
    }

    @ApiOperation(value = "Get Entity Group by owner, type and name (getEntityGroupByOwnerAndNameAndType)",
            notes = "Fetch the Entity Group object based on the provided Entity Group Id. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    "\n\n" + ENTITY_GROUP_UNIQUE_KEY +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{ownerType}/{ownerId}/{groupType}/{groupName}", method = RequestMethod.GET)
    @ResponseBody
    public EntityGroupInfo getEntityGroupByOwnerAndNameAndType(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = "Entity Group type", required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = "Entity Group name", required = true)
            @PathVariable("groupName") String groupName) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        checkParameter("groupName", groupName);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        checkEntityId(ownerId, Operation.READ);
        SecurityUser currentUser = getCurrentUser();
        Optional<EntityGroupInfo> entityGroupOptional = entityGroupService.findOwnerEntityGroupInfo(currentUser.getTenantId(), ownerId, groupType, groupName);
        if (entityGroupOptional.isPresent()) {
            accessControlService.checkEntityGroupInfoPermission(getCurrentUser(), Operation.READ, entityGroupOptional.get());
            return entityGroupOptional.get();
        } else {
            throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
    }

    @ApiOperation(value = "Create Or Update Entity Group (saveEntityGroup)",
            notes = "Create or update the Entity Group. When creating Entity Group, platform generates Entity Group Id as " + UUID_WIKI_LINK +
                    "The newly created Entity Group Id will be present in the response. " +
                    "Specify existing Entity Group Id to update the group. " +
                    "Referencing non-existing Entity Group Id will cause 'Not Found' error." +
                    "Remove 'id', 'tenantId' and optionally 'ownerId' from the request body example (below) to create new Entity Group entity. " +
                    "\n\n" + ENTITY_GROUP_UNIQUE_KEY + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup", method = RequestMethod.POST)
    @ResponseBody
    public EntityGroupInfo saveEntityGroup(
            @ApiParam(value = "A JSON value representing the entity group.", required = true)
            @RequestBody EntityGroup entityGroup) throws Exception {
        SecurityUser currentUser = getCurrentUser();
        checkEntityGroupType(entityGroup.getType());
        Operation operation = entityGroup.getId() == null ? Operation.CREATE : Operation.WRITE;
        EntityId parentEntityId = entityGroup.getOwnerId();
        if (operation == Operation.CREATE) {
            if (parentEntityId == null || parentEntityId.isNullUid()) {
                parentEntityId = currentUser.getOwnerId();
            } else {
                if (!ownersCacheService.fetchOwnersHierarchy(getTenantId(), parentEntityId).contains(currentUser.getOwnerId())) {
                    throw new ThingsboardException("Unable to create entity group: " +
                            "Invalid entity group ownerId!", ThingsboardErrorCode.PERMISSION_DENIED);
                }
            }
        } else {
            validateEntityId(parentEntityId, "Incorrect entity group ownerId " + parentEntityId);
        }

        EntityGroupInfo entityGroupInfo = new EntityGroupInfo(entityGroup, ownersCacheService.fetchOwnersHierarchy(getTenantId(), parentEntityId));

        accessControlService.checkEntityGroupInfoPermission(currentUser, operation, entityGroupInfo);

        return tbEntityGroupService.save(getTenantId(), parentEntityId, entityGroup, getCurrentUser());
    }

    @ApiOperation(value = "Delete Entity Group (deleteEntityGroup)",
            notes = "Deletes the entity group but does not delete the entities in the group, since they are also present in reserved group 'All'. " +
                    "Referencing non-existing Entity Group Id will cause an error." + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_DELETE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteEntityGroup(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws Exception {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.DELETE);
        if (entityGroup.isGroupAll()) {
            throw new ThingsboardException("Unable to remove entity group: " +
                    "Removal of entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
        }

        List<GroupPermissionInfo> groupPermissions = new ArrayList<>(
                groupPermissionService.findGroupPermissionInfoListByTenantIdAndEntityGroupIdAsync(getTenantId(), entityGroupId).get());
        if (EntityType.USER.equals(entityGroup.getType())) {
            groupPermissions.addAll(groupPermissionService.findGroupPermissionInfoListByTenantIdAndUserGroupIdAsync(getTenantId(), entityGroupId).get());
        }

        for (GroupPermission groupPermission : groupPermissions) {
            userPermissionsService.onGroupPermissionDeleted(groupPermission);
        }

        tbEntityGroupService.delete(getTenantId(), entityGroup, getCurrentUser());
    }

    @ApiOperation(value = "Get Entity Groups by entity type (getEntityGroupsByType)",
            notes = "Fetch the list of Entity Group Info objects based on the provided Entity Type. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getEntityGroupsByType(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = ENTITY_GROUP_INCLUDE_SHARED_DESCRIPTION)
            @RequestParam(required = false) Boolean includeShared) throws ThingsboardException {
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead() || !groupTypePermissionInfo.getEntityGroupIds().isEmpty() && (includeShared == null || includeShared)) {
            PageData<EntityGroupInfo> entityGroupInfos;
            if (groupTypePermissionInfo.isHasGenericRead()) {
                EntityId parentEntityId = getCurrentUser().isTenantAdmin() ? getCurrentUser().getTenantId() : getCurrentUser().getCustomerId();
                if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty() && (includeShared == null || includeShared)) {
                    entityGroupInfos = entityGroupService.findEntityGroupInfosByTypeOrIds(getTenantId(),
                            parentEntityId, groupType, groupTypePermissionInfo.getEntityGroupIds(), new PageLink(Integer.MAX_VALUE));
                } else {
                    entityGroupInfos = entityGroupService.findEntityGroupInfosByType(getTenantId(),
                            parentEntityId, groupType, new PageLink(Integer.MAX_VALUE));
                }
            } else {
                entityGroupInfos = entityGroupService.findEntityGroupInfosByIds(getTenantId(),
                        groupTypePermissionInfo.getEntityGroupIds(), new PageLink(Integer.MAX_VALUE));
            }
            return entityGroupInfos.getData();
        } else {
            return Collections.emptyList();
        }
    }

    @ApiOperation(value = "Get Entity Groups by entity type and page link (getEntityGroupsByTypeAndPageLink)",
            notes = "Returns a page of Entity Group Info objects based on the provided Entity Type and Page Link. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityGroupInfo> getEntityGroupsByTypeAndPageLink(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = ENTITY_GROUP_INCLUDE_SHARED_DESCRIPTION)
            @RequestParam(required = false) Boolean includeShared,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead() || !groupTypePermissionInfo.getEntityGroupIds().isEmpty() && (includeShared == null || includeShared)) {
            if (groupTypePermissionInfo.isHasGenericRead()) {
                EntityId parentEntityId = getCurrentUser().isTenantAdmin() ? getCurrentUser().getTenantId() : getCurrentUser().getCustomerId();
                if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty() && (includeShared == null || includeShared)) {
                    return entityGroupService.findEntityGroupInfosByTypeOrIds(getTenantId(),
                            parentEntityId, groupType, groupTypePermissionInfo.getEntityGroupIds(), pageLink);
                } else {
                    return entityGroupService.findEntityGroupInfosByType(getTenantId(),
                            parentEntityId, groupType, pageLink);
                }
            } else {
                return entityGroupService.findEntityGroupInfosByIds(getTenantId(),
                        groupTypePermissionInfo.getEntityGroupIds(), pageLink);
            }
        } else {
            return PageData.emptyPageData();
        }
    }

    @ApiOperation(value = "Get Entity Group Entity Infos by entity type and page link (getEntityGroupEntityInfosByTypeAndPageLink)",
            notes = "Returns a page of Entity Group Entity Info objects based on the provided Entity Type and Page Link. "
                    + ENTITY_GROUP_ENTITY_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupInfos/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityInfo> getEntityGroupEntityInfosByTypeAndPageLink(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = ENTITY_GROUP_INCLUDE_SHARED_DESCRIPTION)
            @RequestParam(required = false) Boolean includeShared,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead() || !groupTypePermissionInfo.getEntityGroupIds().isEmpty() && (includeShared == null || includeShared)) {
            if (groupTypePermissionInfo.isHasGenericRead()) {
                EntityId parentEntityId = getCurrentUser().isTenantAdmin() ? getCurrentUser().getTenantId() : getCurrentUser().getCustomerId();
                if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty() && (includeShared == null || includeShared)) {
                    return entityGroupService.findEntityGroupEntityInfosByTypeOrIds(getTenantId(),
                            parentEntityId, groupType, groupTypePermissionInfo.getEntityGroupIds(), pageLink);
                } else {
                    return entityGroupService.findEntityGroupEntityInfosByType(getTenantId(),
                            parentEntityId, groupType, pageLink);
                }
            } else {
                return entityGroupService.findEntityGroupEntityInfosByIds(getTenantId(),
                        groupTypePermissionInfo.getEntityGroupIds(), pageLink);
            }
        } else {
            return PageData.emptyPageData();
        }
    }

    @ApiOperation(value = "Get Shared Entity Groups by entity type (getSharedEntityGroupsByType)",
            notes = "Fetch the list of Shared Entity Group Info objects based on the provided Entity Type. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{groupType}/shared", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getSharedEntityGroupsByType(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
            PageData<EntityGroupInfo> entityGroupInfos = entityGroupService.findEntityGroupInfosByIds(getTenantId(),
                    groupTypePermissionInfo.getEntityGroupIds(), new PageLink(Integer.MAX_VALUE));
            return entityGroupInfos.getData();
        } else {
            return Collections.emptyList();
        }
    }

    @ApiOperation(value = "Get Shared Entity Groups by entity type and page link (getSharedEntityGroupsByTypeAndPageLink)",
            notes = "Returns a page of Shared Entity Group Info objects based on the provided Entity Type and Page Link. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{groupType}/shared", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityGroupInfo> getSharedEntityGroupsByTypeAndPageLink(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
            return entityGroupService.findEntityGroupInfosByIds(getTenantId(),
                    groupTypePermissionInfo.getEntityGroupIds(), pageLink);
        } else {
            return PageData.emptyPageData();
        }
    }

    @ApiOperation(value = "Get Shared Entity Group Entity Infos by entity type and page link (getSharedEntityGroupEntityInfosByTypeAndPageLink)",
            notes = "Returns a page of Shared Entity Group Entity Info objects based on the provided Entity Type and Page Link. "
                    + ENTITY_GROUP_ENTITY_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupInfos/{groupType}/shared", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityInfo> getSharedEntityGroupEntityInfosByTypeAndPageLink(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (!groupTypePermissionInfo.getEntityGroupIds().isEmpty()) {
            return entityGroupService.findEntityGroupEntityInfosByIds(getTenantId(),
                    groupTypePermissionInfo.getEntityGroupIds(), pageLink);
        } else {
            return PageData.emptyPageData();
        }
    }

    @ApiOperation(value = "Get Entity Groups by owner and entity type (getEntityGroupsByOwnerAndType)",
            notes = "Fetch the list of Entity Group Info objects based on the provided Owner Id and Entity Type. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{ownerType}/{ownerId}/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getEntityGroupsByOwnerAndType(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        checkEntityId(ownerId, Operation.READ);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead()) {
            PageData<EntityGroupInfo> entityGroupInfos = entityGroupService.findEntityGroupInfosByType(getTenantId(),
                    ownerId, groupType, new PageLink(Integer.MAX_VALUE));
            return entityGroupInfos.getData();
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Get Entity Groups by owner and entity type and page link (getEntityGroupsByOwnerAndTypeAndPageLink)",
            notes = "Returns a page of Entity Group objects based on the provided Owner Id and Entity Type and Page Link. " +
                    ENTITY_GROUP_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{ownerType}/{ownerId}/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityGroupInfo> getEntityGroupsByOwnerAndTypeAndPageLink(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder
    ) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        checkEntityId(ownerId, Operation.READ);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead()) {
            return entityGroupService.findEntityGroupInfosByType(getTenantId(), ownerId, groupType, pageLink);
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Get Entity Group Entity Infos by owner and entity type and page link (getEntityGroupEntityInfosByOwnerAndTypeAndPageLink)",
            notes = "Returns a page of Entity Group Entity Info objects based on the provided Owner Id and Entity Type and Page Link. " +
                    ENTITY_GROUP_ENTITY_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupInfos/{ownerType}/{ownerId}/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityInfo> getEntityGroupEntityInfosByOwnerAndTypeAndPageLink(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder
    ) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        checkEntityId(ownerId, Operation.READ);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead()) {
            return entityGroupService.findEntityGroupEntityInfosByType(getTenantId(), ownerId, groupType, pageLink);
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Get Entity Groups for all owners starting from specified than ending with owner of current user (getEntityGroupsHierarchyByOwnerAndTypeAndPageLink)",
            notes = "Returns a page of Entity Group objects based on the provided Owner Id and Entity Type and Page Link. " +
                    ENTITY_GROUP_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupsHierarchy/{ownerType}/{ownerId}/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityGroupInfo> getEntityGroupsHierarchyByOwnerAndTypeAndPageLink(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder
    ) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        checkEntityId(ownerId, Operation.READ);
        Set<EntityId> ownerIds = ownersCacheService.fetchOwnersHierarchy(getTenantId(), ownerId);
        EntityId currentUserOwnerId = getCurrentUser().getOwnerId();
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (!ownerIds.isEmpty() && ownerIds.contains(currentUserOwnerId) && groupTypePermissionInfo.isHasGenericRead()) {
            List<EntityId> targetOwnerIds = ownerIds.stream().takeWhile(entityId -> !entityId.equals(currentUserOwnerId)).collect(Collectors.toCollection(ArrayList::new));
            targetOwnerIds.add(currentUserOwnerId);
            return entityGroupService.findEntityGroupInfosByOwnersAndType(getTenantId(), targetOwnerIds, groupType, pageLink);
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Get Entity Group Entity Infos for all owners starting from specified than ending with owner of current user (getEntityGroupEntityInfosHierarchyByOwnerAndTypeAndPageLink)",
            notes = "Returns a page of Entity Group Entity Info objects based on the provided Owner Id and Entity Type and Page Link. " +
                    ENTITY_GROUP_ENTITY_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupInfosHierarchy/{ownerType}/{ownerId}/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityInfo> getEntityGroupEntityInfosHierarchyByOwnerAndTypeAndPageLink(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder
    ) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        checkEntityId(ownerId, Operation.READ);
        Set<EntityId> ownerIds = ownersCacheService.fetchOwnersHierarchy(getTenantId(), ownerId);
        EntityId currentUserOwnerId = getCurrentUser().getOwnerId();
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (!ownerIds.isEmpty() && ownerIds.contains(currentUserOwnerId) && groupTypePermissionInfo.isHasGenericRead()) {
            List<EntityId> targetOwnerIds = ownerIds.stream().takeWhile(entityId -> !entityId.equals(currentUserOwnerId)).collect(Collectors.toCollection(ArrayList::new));
            targetOwnerIds.add(currentUserOwnerId);
            return entityGroupService.findEntityGroupEntityInfosByOwnersAndType(getTenantId(), targetOwnerIds, groupType, pageLink);
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Get special group All by owner and entity type (getEntityGroupsByOwnerAndType)",
            notes = "Fetch reserved group 'All' based on the provided Owner Id and Entity Type. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/all/{ownerType}/{ownerId}/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public EntityGroupInfo getEntityGroupAllByOwnerAndType(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId,
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        checkEntityId(ownerId, Operation.READ);
        Optional<EntityGroupInfo> entityGroup = entityGroupService.findEntityGroupInfoByTypeAndName(getTenantId(), ownerId,
                groupType, EntityGroup.GROUP_ALL_NAME);
        if (entityGroup.isPresent()) {
            accessControlService.checkEntityGroupInfoPermission(getCurrentUser(), Operation.READ, entityGroup.get());
            return entityGroup.get();
        } else {
            throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
    }

    @ApiOperation(value = "Add entities to the group (addEntitiesToEntityGroup)",
            notes = "Add entities to the specified entity group. "
                    + ENTITY_GROUP_DESCRIPTION + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_ADD_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/addEntities", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void addEntitiesToEntityGroup(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = "A list of entity ids", required = true)
            @RequestBody String[] strEntityIds) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        checkArrayParameter("entityIds", strEntityIds);
        EntityGroup entityGroup = null;
        ActionType actionType = ActionType.ADDED_TO_ENTITY_GROUP;
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.ADD_TO_GROUP);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to add entities to entity group: " +
                        "Addition to entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            List<EntityId> entityIds = new ArrayList<>();
            for (String strEntityId : strEntityIds) {
                EntityId entityId = EntityIdFactory.getByTypeAndId(entityGroup.getType(), strEntityId);
                checkEntityId(entityId, Operation.READ);
                entityIds.add(entityId);
            }
            entityGroupService.addEntitiesToEntityGroup(getTenantId(), entityGroupId, entityIds);
            if (entityGroup.getType() == EntityType.USER) {
                for (EntityId entityId : entityIds) {
                    userPermissionsService.onUserUpdatedOrRemoved(userService.findUserById(getTenantId(), new UserId(entityId.getId())));
                }
            } else if (entityGroup.getType() == EntityType.DEVICE) {
                DeviceGroupOtaPackage fw =
                        deviceGroupOtaPackageService.findDeviceGroupOtaPackageByGroupIdAndType(entityGroupId, OtaPackageType.FIRMWARE);
                DeviceGroupOtaPackage sw =
                        deviceGroupOtaPackageService.findDeviceGroupOtaPackageByGroupIdAndType(entityGroupId, OtaPackageType.SOFTWARE);
                if (fw != null || sw != null) {
                    List<DeviceId> deviceIds = entityIds.stream().map(id -> new DeviceId(id.getId())).collect(Collectors.toList());
                    otaPackageStateService.update(getTenantId(), deviceIds, fw != null, sw != null);
                }
            }

            for (EntityId entityId : entityIds) {
                notificationEntityService.logEntityAction(getTenantId(), entityId, null,
                        actionType, getCurrentUser(), entityId.toString(), strEntityGroupId, entityGroup.getName());
            }
        } catch (Exception e) {
            if (entityGroup != null) {
                EntityType entityType = entityGroup.getType();
                String groupName = entityGroup.getName();
                for (String strEntityId : strEntityIds) {
                    notificationEntityService.logEntityAction(getTenantId(), emptyId(entityType),
                            actionType, getCurrentUser(), e, strEntityId, strEntityGroupId, groupName);
                }
            }
            throw e;
        }
    }

    @ApiOperation(value = "Remove entities from the group (removeEntitiesFromEntityGroup)",
            notes = "Removes entities from the specified entity group. "
                    + ENTITY_GROUP_DESCRIPTION + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_REMOVE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/deleteEntities", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void removeEntitiesFromEntityGroup(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = "A list of entity ids", required = true)
            @RequestBody String[] strEntityIds) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        checkArrayParameter("entityIds", strEntityIds);
        EntityGroup entityGroup = null;
        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.REMOVE_FROM_GROUP);
            if (entityGroup.isGroupAll()) {
                throw new ThingsboardException("Unable to remove entities from entity group: " +
                        "Removal from entity group 'All' is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            List<EntityId> entityIds = new ArrayList<>();
            for (String strEntityId : strEntityIds) {
                EntityId entityId = EntityIdFactory.getByTypeAndId(entityGroup.getType(), strEntityId);
                checkEntityId(entityId, Operation.READ);
                entityIds.add(entityId);
            }
            entityGroupService.removeEntitiesFromEntityGroup(getTenantId(), entityGroupId, entityIds);
            if (entityGroup.getType() == EntityType.USER) {
                for (EntityId entityId : entityIds) {
                    userPermissionsService.onUserUpdatedOrRemoved(userService.findUserById(getTenantId(), new UserId(entityId.getId())));
                }
            } else if (entityGroup.getType() == EntityType.DEVICE) {
                DeviceGroupOtaPackage fw =
                        deviceGroupOtaPackageService.findDeviceGroupOtaPackageByGroupIdAndType(entityGroupId, OtaPackageType.FIRMWARE);
                DeviceGroupOtaPackage sw =
                        deviceGroupOtaPackageService.findDeviceGroupOtaPackageByGroupIdAndType(entityGroupId, OtaPackageType.SOFTWARE);
                if (fw != null || sw != null) {
                    List<DeviceId> deviceIds = entityIds.stream().map(id -> new DeviceId(id.getId())).collect(Collectors.toList());
                    otaPackageStateService.update(getTenantId(), deviceIds, fw != null, sw != null);
                }
            }

            for (EntityId entityId : entityIds) {
                notificationEntityService.logEntityAction(getTenantId(), entityId, null,
                        ActionType.REMOVED_FROM_ENTITY_GROUP, getCurrentUser(), entityId.toString(), strEntityGroupId, entityGroup.getName());
            }
        } catch (Exception e) {
            if (entityGroup != null) {
                EntityType entityType = entityGroup.getType();
                String groupName = entityGroup.getName();
                for (String strEntityId : strEntityIds) {
                    notificationEntityService.logEntityAction(getTenantId(), emptyId(entityType),
                            ActionType.REMOVED_FROM_ENTITY_GROUP, getCurrentUser(), e, strEntityId, strEntityGroupId, groupName);
                }
            }
            throw e;
        }
    }

    @ApiOperation(value = "Get Group Entity (getGroupEntity)",
            notes = "Fetch the Short Entity View object based on the group and entity id. " +
                    SHORT_ENTITY_VIEW_DESCRIPTION + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public ShortEntityView getGroupEntity(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = ENTITY_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ENTITY_ID) String strEntityId) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        checkParameter("entityId", strEntityId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
        EntityType entityType = entityGroup.getType();
        checkEntityGroupType(entityType);
        EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, strEntityId);
        checkEntityId(entityId, Operation.READ);
        SecurityUser currentUser = getCurrentUser();
        MergedUserPermissions mergedUserPermissions = currentUser.getUserPermissions();
        ShortEntityView result = entityGroupService.findGroupEntity(getTenantId(), currentUser.getCustomerId(), mergedUserPermissions, entityGroupId, entityId);
        return checkNotNull(result);
    }

    @ApiOperation(value = "Get Group Entities (getEntities)",
            notes = "Returns a page of Short Entity View objects that belongs to specified Entity Group Id. " +
                    SHORT_ENTITY_VIEW_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/entities", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ShortEntityView> getEntities(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder
    ) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
        EntityType entityType = entityGroup.getType();
        checkEntityGroupType(entityType);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        SecurityUser currentUser = getCurrentUser();
        MergedUserPermissions mergedUserPermissions = currentUser.getUserPermissions();
        return checkNotNull(entityGroupService.findGroupEntities(getTenantId(), currentUser.getCustomerId(), mergedUserPermissions, entityGroupId, pageLink));
    }

    @ApiOperation(value = "Get Entity Groups by Entity Id (getEntityGroupsForEntity)",
            notes = "Returns a list of groups that contain the specified Entity Id. " +
                    "For example, all device groups that contain specific device. " +
                    "The list always contain at least one element - special group 'All'." +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/{entityType}/{entityId}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupId> getEntityGroupsForEntity(
            @ApiParam(value = ENTITY_GROUP_TYPE_PARAMETER_DESCRIPTION, required = true, allowableValues = EntityGroup.ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("entityType") String strEntityType,
            @ApiParam(value = ENTITY_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable("entityId") String strEntityId) throws ThingsboardException, ExecutionException, InterruptedException {
        checkParameter("entityType", strEntityType);
        checkParameter("entityId", strEntityId);
        EntityType entityType = checkStrEntityGroupType("entityType", strEntityType);
        EntityId entityId = EntityIdFactory.getByTypeAndId(entityType, strEntityId);
        checkEntityId(entityId, Operation.READ);
        return checkNotNull(entityGroupService.findEntityGroupsForEntityAsync(getTenantId(), entityId).get());
    }

    @ApiOperation(value = "Get Entity Groups by Ids (getEntityGroupsByIds)",
            notes = "Fetch the list of Entity Group Info objects based on the provided entity group ids list. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups", params = {"entityGroupIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getEntityGroupsByIds(
            @ApiParam(value = "A list of group ids, separated by comma ','")
            @RequestParam("entityGroupIds") String[] strEntityGroupIds) throws ThingsboardException {
        checkArrayParameter("entityGroupIds", strEntityGroupIds);
        SecurityUser user = getCurrentUser();
        TenantId tenantId = user.getTenantId();
        List<EntityGroupId> entityGroupIds = new ArrayList<>();
        for (String strEntityGroupId : strEntityGroupIds) {
            entityGroupIds.add(new EntityGroupId(toUUID(strEntityGroupId)));
        }
        List<EntityGroupInfo> entityGroups = checkNotNull(
                entityGroupService.findEntityGroupInfosByIds(tenantId, entityGroupIds, new PageLink(Integer.MAX_VALUE)).getData());
        return filterEntityGroupsByReadPermission(entityGroups);
    }

    @ApiOperation(value = "Get Entity Group Entity Infos by Ids (getEntityGroupEntityInfosByIds)",
            notes = "Fetch the list of Entity Group Entity Info objects based on the provided entity group ids list. "
                    + ENTITY_GROUP_ENTITY_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroupInfos", params = {"entityGroupIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<EntityInfo> getEntityGroupEntityInfosByIds(
            @ApiParam(value = "A list of group ids, separated by comma ','")
            @RequestParam("entityGroupIds") String[] strEntityGroupIds) throws ThingsboardException {
        checkArrayParameter("entityGroupIds", strEntityGroupIds);
        SecurityUser user = getCurrentUser();
        TenantId tenantId = user.getTenantId();
        List<EntityGroupId> entityGroupIds = new ArrayList<>();
        for (String strEntityGroupId : strEntityGroupIds) {
            entityGroupIds.add(new EntityGroupId(toUUID(strEntityGroupId)));
        }
        return checkNotNull(entityGroupService.findEntityGroupEntityInfosByIds(tenantId, entityGroupIds, new PageLink(Integer.MAX_VALUE)).getData());
    }

    @ApiOperation(value = "Get Owners (getOwners)",
            notes = "Provides a rage view of Customers that the current user has READ access to. " +
                    "If the current user is Tenant administrator, the result set also contains the tenant. " +
                    "The call is designed for the UI auto-complete component to show tenant and all possible Customers " +
                    "that the user may select to change the owner of the particular entity or entity group."
                    + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/owners", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<ContactBased<?>> getOwners(
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException, ExecutionException, InterruptedException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        List<ContactBased<?>> owners = new ArrayList<>();
        if (Authority.TENANT_ADMIN.equals(getCurrentUser().getAuthority())) {
            if (accessControlService.hasPermission(getCurrentUser(), Resource.TENANT, Operation.READ)) {
                owners.add(tenantService.findTenantById(getCurrentUser().getTenantId()));
            }
        }
        if (accessControlService.hasPermission(getCurrentUser(), Resource.CUSTOMER, Operation.READ)) {
            if (Authority.TENANT_ADMIN.equals(getCurrentUser().getAuthority())) {
                owners.addAll(customerService.findCustomersByTenantId(getTenantId(), pageLink)
                        .getData().stream().filter(customer -> !customer.isPublic()).collect(Collectors.toList()));
            } else {
                Set<EntityId> ownerIds = ownersCacheService.getChildOwners(getTenantId(), getCurrentUser().getOwnerId());
                if (!ownerIds.isEmpty()) {
                    List<CustomerId> customerIds = new ArrayList<>();
                    for (EntityId ownerId : ownerIds) {
                        customerIds.add(new CustomerId(ownerId.getId()));
                    }
                    owners.addAll(customerService.findCustomersByTenantIdAndIdsAsync(getTenantId(), customerIds).get()
                            .stream().filter(customer -> !customer.isPublic()).collect(Collectors.toList()));
                }
            }
        }
        owners = owners.stream().sorted(entityComparator).filter(new EntityPageLinkFilter(pageLink)).collect(Collectors.toList());
        return toPageData(owners, pageLink);
    }

    @ApiOperation(value = "Get Owner Infos (getOwnerInfos)",
            notes = "Provides a rage view of Customers that the current user has READ access to. " +
                    "If the current user is Tenant administrator, the result set also contains the tenant. " +
                    "The call is designed for the UI auto-complete component to show tenant and all possible Customers " +
                    "that the user may select to change the owner of the particular entity or entity group."
                    + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/ownerInfos", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityInfo> getOwnerInfos(
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        if (Authority.TENANT_ADMIN.equals(getCurrentUser().getAuthority())) {
            if (accessControlService.hasPermission(getCurrentUser(), Resource.TENANT, Operation.READ)) {
                if (accessControlService.hasPermission(getCurrentUser(), Resource.CUSTOMER, Operation.READ)) {
                    return ownerService.findCustomerOwnersByTenantIdIncludingTenant(getCurrentUser().getTenantId(), pageLink);
                } else {
                    return ownerService.findTenantOwnerByTenantId(getCurrentUser().getTenantId(), pageLink);
                }
            } else if (accessControlService.hasPermission(getCurrentUser(), Resource.CUSTOMER, Operation.READ)) {
                return ownerService.findCustomerOwnersByTenantId(getCurrentUser().getTenantId(), pageLink);
            }
        } else if (accessControlService.hasPermission(getCurrentUser(), Resource.CUSTOMER, Operation.READ)) {
            Set<EntityId> ownerIds = ownersCacheService.getChildOwners(getTenantId(), getCurrentUser().getOwnerId());
            List<CustomerId> customerIds = ownerIds.stream().map(id -> new CustomerId(id.getId())).collect(Collectors.toList());
            return ownerService.findCustomerOwnersByIdsAndTenantId(getCurrentUser().getTenantId(), customerIds, pageLink);
        }
        return PageData.emptyPageData();
    }

    @ApiOperation(value = "Get Owner Info (getOwnerInfo)",
            notes = "Fetch the owner info (tenant or customer) presented as Entity Info object based on the provided owner Id. " +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/ownerInfo/{ownerType}/{ownerId}", method = RequestMethod.GET)
    @ResponseBody
    public EntityInfo getOwnerInfo(
            @ApiParam(value = OWNER_TYPE_DESCRIPTION, required = true, allowableValues = "TENANT,CUSTOMER")
            @PathVariable("ownerType") String strOwnerType,
            @ApiParam(value = OWNER_ID_DESCRIPTION, required = true, example = "784f394c-42b6-435a-983c-b7beff2784f9")
            @PathVariable("ownerId") String strOwnerId) throws ThingsboardException {

        checkParameter("ownerId", strOwnerId);
        checkParameter("ownerType", strOwnerType);
        EntityId ownerId = EntityIdFactory.getByTypeAndId(strOwnerType, strOwnerId);
        if (!EntityType.TENANT.equals(ownerId.getEntityType()) && !EntityType.CUSTOMER.equals(ownerId.getEntityType())) {
            throw new ThingsboardException("Unsupported owner type '" + ownerId.getEntityType() + "'! Only 'TENANT' or 'CUSTOMER' types are allowed.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else if (EntityType.TENANT.equals(ownerId.getEntityType())) {
            TenantId tenantId = new TenantId(ownerId.getId());
            Tenant tenant = checkTenantId(tenantId, Operation.READ);
            return new EntityInfo(tenant.getUuidId(), EntityType.TENANT.name(), tenant.getTitle());
        } else {
            CustomerId customerId = new CustomerId(ownerId.getId());
            Customer customer = checkCustomerId(customerId, Operation.READ);
            return new EntityInfo(customer.getUuidId(), EntityType.CUSTOMER.name(), customer.getTitle());
        }
    }

    @ApiOperation(value = "Make Entity Group Publicly available (makeEntityGroupPublic)",
            notes = "Make the entity group available for non authorized users. " +
                    "Useful for public dashboards that will be embedded into the public websites. "
                    + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/makePublic", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void makeEntityGroupPublic(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);

        EntityGroup entityGroup = null;

        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.WRITE);
            if (!getCurrentUser().getOwnerId().equals(entityGroup.getOwnerId())) {
                throw permissionDenied();
            }

            tbEntityGroupService.makePublic(getTenantId(), entityGroup, getCurrentUser());
        } catch (Exception e) {
            if (entityGroup != null) {
                notificationEntityService.logEntityAction(getTenantId(), entityGroup.getId(), ActionType.MADE_PUBLIC,
                        getCurrentUser(), e, strEntityGroupId, entityGroup.getName());
            }
            throw e;
        }
    }

    @ApiOperation(value = "Make Entity Group Private (makeEntityGroupPrivate)",
            notes = "Make the entity group not available for non authorized users. Every group is private by default. " +
                    "This call is useful to hide the group that was previously made public."
                    + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/makePrivate", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void makeEntityGroupPrivate(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);

        EntityGroup entityGroup = null;

        try {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.WRITE);
            if (!getCurrentUser().getOwnerId().equals(entityGroup.getOwnerId())) {
                throw permissionDenied();
            }

            tbEntityGroupService.makePrivate(getTenantId(), entityGroup, getCurrentUser());
        } catch (Exception e) {
            if (entityGroup != null) {
                notificationEntityService.logEntityAction(getTenantId(), entityGroup.getId(), ActionType.MADE_PRIVATE,
                        getCurrentUser(), e, strEntityGroupId, entityGroup.getName());
            }
            throw e;
        }
    }

    @ApiOperation(value = "Share the Entity Group (shareEntityGroup)",
            notes = "Share the entity group with certain user group based on the provided Share Group Request. " +
                    "The request is quite flexible and processing of the request involves multiple security checks using platform RBAC feature."
                    + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/share", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void shareEntityGroup(
            @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION, required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = "The Share Group Request JSON", required = true)
            @RequestBody ShareGroupRequest shareGroupRequest) throws ThingsboardException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroup entityGroup;
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.GROUP_PERMISSION, Operation.CREATE);
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            entityGroup = checkEntityGroupId(entityGroupId, Operation.WRITE);
            checkSharableEntityGroupType(entityGroup.getType());

            EntityGroupInfo userGroup;
            if (shareGroupRequest.isAllUserGroup()) {
                Optional<EntityGroupInfo> userGroupOptional = entityGroupService.findEntityGroupInfoByTypeAndName(getTenantId(), shareGroupRequest.getOwnerId(),
                        EntityType.USER, EntityGroup.GROUP_ALL_NAME);
                if (userGroupOptional.isPresent()) {
                    userGroup = userGroupOptional.get();
                } else {
                    throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
                }
            } else {
                userGroup = entityGroupService.findEntityGroupInfoById(getTenantId(), shareGroupRequest.getUserGroupId());
            }
            accessControlService.checkEntityGroupInfoPermission(getCurrentUser(), Operation.WRITE, userGroup);

            List<RoleId> roleIds;
            if (shareGroupRequest.getRoleIds() != null && !shareGroupRequest.getRoleIds().isEmpty()) {
                roleIds = shareGroupRequest.getRoleIds();
            } else {
                Role role;
                if (shareGroupRequest.isReadElseWrite()) {
                    role = roleService.findOrCreateReadOnlyEntityGroupRole(getTenantId(), getCurrentUser().getCustomerId());
                } else {
                    role = roleService.findOrCreateWriteEntityGroupRole(getTenantId(), getCurrentUser().getCustomerId());
                }
                roleIds = Collections.singletonList(role.getId());
            }

            for (RoleId roleId : roleIds) {
                GroupPermission groupPermission = new GroupPermission();
                groupPermission.setTenantId(getTenantId());
                groupPermission.setEntityGroupId(entityGroup.getId());
                groupPermission.setEntityGroupType(entityGroup.getType());
                groupPermission.setRoleId(roleId);
                groupPermission.setUserGroupId(userGroup.getId());

                GroupPermission savedGroupPermission = checkNotNull(groupPermissionService.saveGroupPermission(getTenantId(), groupPermission));
                userPermissionsService.onGroupPermissionUpdated(savedGroupPermission);
                notificationEntityService.logEntityAction(getTenantId(), savedGroupPermission.getId(), savedGroupPermission,
                        ActionType.ADDED, getCurrentUser());
            }

        } catch (Exception e) {
            notificationEntityService.logEntityAction(getTenantId(), emptyId(EntityType.GROUP_PERMISSION),
                    ActionType.ADDED, getCurrentUser(), e);
            throw e;
        }
    }

    @ApiOperation(value = "Share the Entity Group with User group (shareEntityGroupToChildOwnerUserGroup)",
            notes = "Share the entity group with specified user group using specified role. "
                    + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_GROUP_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/{userGroupId}/{roleId}/share", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public void shareEntityGroupToChildOwnerUserGroup(
            @ApiParam(value = "A string value representing the Entity Group Id that you would like to share. For example, '784f394c-42b6-435a-983c-b7beff2784f9'", required = true)
            @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = "A string value representing the Entity(User) Group Id that you would like to share with. For example, '784f394c-42b6-435a-983c-b7beff2784f9'", required = true)
            @PathVariable("userGroupId") String strUserGroupId,
            @ApiParam(value = "A string value representing the Role Id that describes set of permissions you would like to share (read, write, etc). For example, '784f394c-42b6-435a-983c-b7beff2784f9'", required = true)
            @PathVariable("roleId") String strRoleId) throws ThingsboardException, IOException {
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        checkParameter("userGroupId", strUserGroupId);
        checkParameter("roleId", strRoleId);
        EntityGroupId userGroupId = new EntityGroupId(toUUID(strUserGroupId));
        EntityGroup userGroup = entityGroupService.findEntityGroupById(getTenantId(), userGroupId);
        if (userGroup == null) {
            throw new ThingsboardException("User group with requested id: " + userGroupId + " wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        Set<EntityId> userGroupOwnerIds = ownersCacheService.fetchOwnersHierarchy(getTenantId(), userGroup.getOwnerId());
        EntityId currentUserOwnerId = getCurrentUser().getOwnerId();
        if (!CollectionUtils.isEmpty(userGroupOwnerIds) && userGroupOwnerIds.contains(currentUserOwnerId)) {
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = entityGroupService.findEntityGroupById(getTenantId(), entityGroupId);
            if (entityGroup == null) {
                throw new ThingsboardException("Entity group with requested id: " + entityGroupId + " wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
            Set<EntityId> groupToShareOwnerIds = ownersCacheService.fetchOwnersHierarchy(getTenantId(), entityGroup.getOwnerId());
            Set<Operation> mergedOperations = new HashSet<>();
            MergedUserPermissions userPermissions = getCurrentUser().getUserPermissions();
            if (groupToShareOwnerIds.contains(currentUserOwnerId)) {
                if (hasGenenericPermissionToShareGroup(entityGroup.getType())) {
                    Map<Resource, Set<Operation>> genericPermissions = userPermissions.getGenericPermissions();
                    genericPermissions.forEach((resource, operations) -> {
                        if (resource.equals(Resource.ALL) || (resource.getEntityTypes().contains(EntityType.ENTITY_GROUP))) {
                            mergedOperations.addAll(operations);
                        }
                    });
                }
            }
            if (hasGroupPermissionsToShareGroup(entityGroupId)) {
                Map<EntityGroupId, MergedGroupPermissionInfo> groupPermissions = userPermissions.getGroupPermissions();
                MergedGroupPermissionInfo mergedGroupPermissionInfo = groupPermissions.get(entityGroupId);
                mergedOperations.addAll(mergedGroupPermissionInfo.getOperations());
            }
            RoleId roleId = new RoleId(toUUID(strRoleId));
            Role role = roleService.findRoleById(getTenantId(), roleId);
            if (role == null) {
                throw new ThingsboardException("Role with requested id: " + roleId + " wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
            Set<EntityId> roleOwnerIds = ownersCacheService.fetchOwnersHierarchy(getTenantId(), role.getOwnerId());
            if (roleOwnerIds.contains(currentUserOwnerId) || userGroupOwnerIds.containsAll(roleOwnerIds)) {
                shareGroup(role, userGroup, entityGroup, mergedOperations);
            } else {
                throw permissionDenied();
            }
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Assign entity group to edge (assignEntityGroupToEdge)",
            notes = "Creates assignment of an existing entity group to an instance of The Edge. " +
                    "Assignment works in async way - first, notification event pushed to edge service queue on platform. " +
                    "Second, remote edge service will receive a copy of assignment entity group " +
                    EDGE_ASSIGN_RECEIVE_STEP_DESCRIPTION +
                    "Third, once entity group will be delivered to edge service, edge will request entities of this group to be send to edge. " +
                    "Once entities will be delivered to edge service, they are going to be available for usage on remote edge instance." +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_WRITE_CHECK,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edge/{edgeId}/entityGroup/{entityGroupId}/{groupType}", method = RequestMethod.POST)
    @ResponseBody
    public EntityGroup assignEntityGroupToEdge(@ApiParam(value = EDGE_ID_PARAM_DESCRIPTION)
                                               @PathVariable(EDGE_ID) String strEdgeId,
                                               @ApiParam(value = "EntityGroup type", required = true, allowableValues = EDGE_ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
                                               @PathVariable("groupType") String strGroupType,
                                               @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION)
                                               @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        ActionType actionType = ActionType.ASSIGNED_TO_EDGE;
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.WRITE);

            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            checkEntityGroupId(entityGroupId, Operation.READ);
            EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);

            EntityGroup savedEntityGroup = checkNotNull(entityGroupService.assignEntityGroupToEdge(getCurrentUser().getTenantId(), entityGroupId, edgeId, groupType));

            notificationEntityService.logEntityAction(getTenantId(), entityGroupId, savedEntityGroup,
                    actionType, getCurrentUser(), strEntityGroupId, savedEntityGroup.getName(), strEdgeId, edge.getName());

            return savedEntityGroup;
        } catch (Exception e) {
            notificationEntityService.logEntityAction(getTenantId(), emptyId(EntityType.ENTITY_GROUP),
                    actionType, getCurrentUser(), e, strEntityGroupId, strEdgeId);

            throw e;
        }
    }

    @ApiOperation(value = "Unassign entity group from edge (unassignEntityGroupFromEdge)",
            notes = "Clears assignment of the entity group to the edge. " +
                    "Unassignment works in async way - first, 'unassign' notification event pushed to edge queue on platform. " +
                    "Second, remote edge service will receive an 'unassign' command to remove entity group " +
                    EDGE_UNASSIGN_RECEIVE_STEP_DESCRIPTION +
                    "Third, once 'unassign' command will be delivered to edge service, it's going to remove entity group and entities inside this group locally." +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_WRITE_CHECK,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/edge/{edgeId}/entityGroup/{entityGroupId}/{groupType}", method = RequestMethod.DELETE)
    @ResponseBody
    public EntityGroup unassignEntityGroupFromEdge(@ApiParam(value = EDGE_ID_PARAM_DESCRIPTION)
                                                   @PathVariable(EDGE_ID) String strEdgeId,
                                                   @ApiParam(value = "EntityGroup type", required = true, allowableValues = EDGE_ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
                                                   @PathVariable("groupType") String strGroupType,
                                                   @ApiParam(value = ENTITY_GROUP_ID_PARAM_DESCRIPTION)
                                                   @PathVariable(ControllerConstants.ENTITY_GROUP_ID) String strEntityGroupId) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        checkParameter(ControllerConstants.ENTITY_GROUP_ID, strEntityGroupId);
        ActionType actionType = ActionType.UNASSIGNED_FROM_EDGE;
        try {
            EdgeId edgeId = new EdgeId(toUUID(strEdgeId));
            Edge edge = checkEdgeId(edgeId, Operation.WRITE);
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
            EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);

            EntityGroup savedEntityGroup = checkNotNull(entityGroupService.unassignEntityGroupFromEdge(getCurrentUser().getTenantId(), entityGroupId, edgeId, groupType));

            notificationEntityService.logEntityAction(getTenantId(), entityGroupId, entityGroup,
                    actionType, getCurrentUser(), strEntityGroupId, savedEntityGroup.getName(), strEdgeId, edge.getName());

            return savedEntityGroup;
        } catch (Exception e) {
            notificationEntityService.logEntityAction(getTenantId(), emptyId(EntityType.ENTITY_GROUP),
                    actionType, getCurrentUser(), e, strEntityGroupId);

            throw e;
        }
    }

    @ApiOperation(value = "Get All Edge Entity Groups by entity type (getAllEdgeEntityGroups)",
            notes = "Fetch the list of Entity Group Info objects based on the provided Entity Type and assigned to the provided Edge entity. "
                    + ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/allEntityGroups/edge/{edgeId}/{groupType}", method = RequestMethod.GET)
    @ResponseBody
    public List<EntityGroupInfo> getAllEdgeEntityGroups(
            @ApiParam(value = EDGE_ID_PARAM_DESCRIPTION)
            @PathVariable("edgeId") String strEdgeId,
            @ApiParam(value = "EntityGroup type", required = true, allowableValues = EDGE_ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        EdgeId edgeId = new EdgeId(UUID.fromString(strEdgeId));
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        checkEdgeId(edgeId, Operation.READ);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead()) {
            PageLink pageLink = new PageLink(Integer.MAX_VALUE);
            PageData<EntityGroupInfo> pageData = entityGroupService.findEdgeEntityGroupInfosByOwnerIdType(getTenantId(), edgeId, getCurrentUser().getOwnerId(), groupType, pageLink);
            return pageData.getData();
        } else {
            throw permissionDenied();
        }
    }

    @ApiOperation(value = "Get Edge Entity Groups by entity type (getEdgeEntityGroups)",
            notes = "Returns a page of Entity Group Info objects based on the provided Entity Type and assigned to the provided Edge entity. " +
                    ENTITY_GROUP_DESCRIPTION + ENTITY_GROUP_INFO_DESCRIPTION +
                    PAGE_DATA_PARAMETERS + TENANT_OR_CUSTOMER_AUTHORITY_PARAGRAPH + RBAC_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroups/edge/{edgeId}/{groupType}", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<EntityGroupInfo> getEdgeEntityGroups(
            @ApiParam(value = EDGE_ID_PARAM_DESCRIPTION)
            @PathVariable(EDGE_ID) String strEdgeId,
            @ApiParam(value = "EntityGroup type", required = true, allowableValues = EDGE_ENTITY_GROUP_TYPE_ALLOWABLE_VALUES)
            @PathVariable("groupType") String strGroupType,
            @ApiParam(value = PAGE_SIZE_DESCRIPTION, required = true, allowableValues = "range[1, infinity]")
            @RequestParam int pageSize,
            @ApiParam(value = PAGE_NUMBER_DESCRIPTION, required = true, allowableValues = "range[0, infinity]")
            @RequestParam int page,
            @ApiParam(value = ENTITY_GROUP_TEXT_SEARCH_DESCRIPTION)
            @RequestParam(required = false) String textSearch,
            @ApiParam(value = SORT_PROPERTY_DESCRIPTION)
            @RequestParam(required = false) String sortProperty,
            @ApiParam(value = SORT_ORDER_DESCRIPTION, allowableValues = SORT_ORDER_ALLOWABLE_VALUES)
            @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        checkParameter("edgeId", strEdgeId);
        EdgeId edgeId = new EdgeId(UUID.fromString(strEdgeId));
        EntityType groupType = checkStrEntityGroupType("groupType", strGroupType);
        checkEdgeId(edgeId, Operation.READ);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        MergedGroupTypePermissionInfo groupTypePermissionInfo = getCurrentUser().getUserPermissions().getReadGroupPermissions().get(groupType);
        if (groupTypePermissionInfo.isHasGenericRead()) {
            return entityGroupService.findEdgeEntityGroupInfosByOwnerIdType(getTenantId(), edgeId, getCurrentUser().getOwnerId(), groupType, pageLink);
        } else {
            throw permissionDenied();
        }
    }

    private void shareGroup(Role role, EntityGroup userGroup, EntityGroup entityGroup, Set<Operation> mergedOperations) throws ThingsboardException, IOException {
        CollectionType collectionType = TypeFactory.defaultInstance().constructCollectionType(List.class, Operation.class);
        List<Operation> roleOperations = JacksonUtil.readValue(role.getPermissions().toString(), collectionType);
        if (!mergedOperations.isEmpty() && (mergedOperations.contains(Operation.ALL) || mergedOperations.containsAll(roleOperations))) {
            groupPermissionService.saveGroupPermission(getTenantId(), new GroupPermission(getTenantId(), userGroup.getId(), role.getId(), entityGroup.getId(), entityGroup.getId().getEntityType(), false));
        } else {
            throw permissionDenied();
        }
    }

    private boolean hasGenenericPermissionToShareGroup(EntityType type) throws ThingsboardException {
        return getCurrentUser().getUserPermissions().hasGenericPermission(Resource.groupResourceFromGroupType(type), Operation.SHARE_GROUP);
    }

    private boolean hasGroupPermissionsToShareGroup(EntityGroupId entityGroupId) throws ThingsboardException {
        return getCurrentUser().getUserPermissions().hasGroupPermissions(entityGroupId, Operation.SHARE_GROUP);
    }

    private List<EntityGroupInfo> filterEntityGroupsByReadPermission(List<EntityGroupInfo> entityGroups) {
        return entityGroups.stream().filter(entityGroup -> {
            try {
                return accessControlService.hasEntityGroupInfoPermission(getCurrentUser(), Operation.READ, entityGroup);
            } catch (ThingsboardException e) {
                return false;
            }
        }).collect(Collectors.toList());
    }
}
