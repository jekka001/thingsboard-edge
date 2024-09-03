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
package org.thingsboard.server.controller;

import com.google.common.hash.Hashing;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.CustomMenuDeleteResult;
import org.thingsboard.server.common.data.EntityInfo;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.CustomMenuId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.menu.CMAssigneeType;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.menu.CustomMenuConfig;
import org.thingsboard.server.common.data.menu.CustomMenuInfo;
import org.thingsboard.server.common.data.menu.Views;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.menu.CustomMenuCacheKey;
import org.thingsboard.server.dao.menu.CustomMenuService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.custommenu.TbCustomMenuService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.thingsboard.server.controller.ControllerConstants.CUSTOM_MENU_ID;
import static org.thingsboard.server.controller.ControllerConstants.CUSTOM_MENU_ID_PARAM_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.CUSTOM_MENU_TEXT_SEARCH_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_NUMBER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_SIZE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_ORDER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_PROPERTY_DESCRIPTION;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class CustomMenuController extends BaseController {

    @Autowired
    private TbCustomMenuService tbCustomMenuService;
    @Autowired
    private CustomMenuService customMenuService;

    @ApiOperation(value = "Get all custom menus configured at user level (getCustomMenuInfos)",
            notes = "Returns a page of custom menu info objects owned by the tenant or the customer of a current user. "
                    + ControllerConstants.WL_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/customMenu/infos")
    public PageData<CustomMenuInfo> getCustomMenuInfos(@Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                       @RequestParam int pageSize,
                                                       @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                       @RequestParam int page,
                                                       @Parameter(description = CUSTOM_MENU_TEXT_SEARCH_DESCRIPTION)
                                                       @RequestParam(required = false) String textSearch,
                                                       @Parameter(description = SORT_PROPERTY_DESCRIPTION, schema = @Schema(allowableValues = {"createdTime", "title"}))
                                                       @RequestParam(required = false) String sortProperty,
                                                       @Parameter(description = SORT_ORDER_DESCRIPTION, schema = @Schema(allowableValues = {"ASC", "DESC"}))
                                                       @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        SecurityUser currentUser = getCurrentUser();
        checkWhiteLabelingPermissions(Operation.READ);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return customMenuService.findCustomMenuInfos(currentUser.getTenantId(), getCurrentUser().getCustomerId(), pageLink);
    }

    @ApiOperation(value = "Get end-user Custom Menu configuration (getCustomMenu)",
            notes = "Fetch the Custom Menu configuration object for the authorized user. " +
                    "The custom menu is configured in the white labeling parameters and has one of three user scopes:" +
                    "SYSTEM, TENANT, CUSTOMER and four assignee type: NO_ASSIGN, ALL, CUSTOMERS, USERS." +
                    "There are three default (assignee type: ALL) menus configured on the system level for each scope and if no other menu is configured for user, " +
                    "system configuration of the corresponding scope will be applied." +
                    "If a custom menu with assignee type ALL is configured on the tenant level, it overrides the menu configuration of the corresponding scope on the system level. " +
                    "If a custom menu with assignee type CUSTOMERS is configured on tenant level for specific customer, it will be applied to all customer users." +
                    "If a custom menu with assignee type ALL is configured on the customer level, it overrides the menu assigned on tenant level." +
                    "If a custom menu is assigned to specific list of users, it overrides all other configuration.")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/customMenu")
    public void getCustomMenu(@RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String etag,
                              HttpServletResponse response) throws ThingsboardException, IOException {
        SecurityUser currentUser = getCurrentUser();
        CustomMenuCacheKey cacheKey = CustomMenuCacheKey.forUser(currentUser.getTenantId(), currentUser.getId());
        if (StringUtils.isNotEmpty(etag) && StringUtils.remove(etag, '\"').equals(tbCustomMenuService.getETag(cacheKey))) {
            response.setStatus(HttpStatus.NOT_MODIFIED.value());
        } else {
            Authority authority = currentUser.getAuthority();
            CustomMenuConfig customMenuConfig = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                customMenuConfig = customMenuService.findSystemAdminCustomMenuConfig();
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                customMenuConfig = customMenuService.findTenantUserCustomMenuConfig(currentUser.getTenantId(), currentUser.getId());
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                customMenuConfig = customMenuService.findCustomerUserCustomMenuConfig(currentUser.getTenantId(), currentUser.getCustomerId(), currentUser.getId());
            }
            String customMenuView = JacksonUtil.writeValueAsViewIgnoringNullFields(customMenuConfig, Views.Public.class);
            String calculatedEtag = calculateCustomMenuEtag(customMenuView);
            tbCustomMenuService.putETag(cacheKey, calculatedEtag);
            response.setHeader("Etag", calculatedEtag);
            response.setContentType(APPLICATION_JSON_VALUE);
            response.getWriter().write(customMenuView);
        }
    }

    @ApiOperation(value = "Get Custom Menu Info (getCustomMenuInfoById)",
            notes = "Fetch the Custom Menu Info object based on the provided Custom Menu Id. " +
                    ControllerConstants.CUSTOM_MENU_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/customMenu/{customMenuId}/info")
    public CustomMenuInfo getCustomMenuInfoById(@Parameter(description = CUSTOM_MENU_ID_PARAM_DESCRIPTION)
                                                @PathVariable(CUSTOM_MENU_ID) UUID id) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        return checkCustomMenuInfoId(customMenuId, Operation.READ);
    }

    @ApiOperation(value = "Get Custom Menu assignee list (getCustomMenuAssigneeList)",
            notes = "Fetch the list of Entity Info objects that represents users or customers, or empty list if custom menu is not assigned or has NO_ASSIGN/ALL assignee type." +
                    ControllerConstants.CUSTOM_MENU_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/customMenu/{customMenuId}/assigneeList")
    public List<EntityInfo> getCustomMenuAssigneeList(@Parameter(description = CUSTOM_MENU_ID_PARAM_DESCRIPTION)
                                                      @PathVariable(CUSTOM_MENU_ID) UUID id) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        CustomMenuInfo customMenuInfo = checkCustomMenuInfoId(customMenuId, Operation.READ);
        return customMenuService.findCustomMenuAssigneeList(customMenuInfo);
    }

    @ApiOperation(value = "Get Custom Menu configuration by id (getCustomMenuConfig)",
            notes = "Fetch the Custom Menu configuration based on the provided Custom Menu Id. " +
                    ControllerConstants.CUSTOM_MENU_READ_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping(value = "/customMenu/{customMenuId}/config")
    public CustomMenuConfig getCustomMenuConfig(@Parameter(description = CUSTOM_MENU_ID_PARAM_DESCRIPTION)
                                                @PathVariable(CUSTOM_MENU_ID) UUID id) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        return checkCustomMenuId(customMenuId, Operation.READ).getConfig();
    }

    @ApiOperation(value = "Update Custom Menu configuration based on the provided Custom Menu Id (updateCustomMenuConfig)",
            notes = ControllerConstants.CUSTOM_MENU_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PutMapping(value = "/customMenu/{customMenuId}/config")
    public CustomMenu updateCustomMenuConfig(@Parameter(description = CUSTOM_MENU_ID_PARAM_DESCRIPTION)
                                             @PathVariable(CUSTOM_MENU_ID) UUID id,
                                             @Parameter(description = "A JSON value representing the custom menu configuration")
                                             @RequestBody @Valid CustomMenuConfig customMenuConfig) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        CustomMenu customMenu = checkNotNull(checkCustomMenuId(customMenuId, Operation.WRITE));
        CustomMenu newCustomMenu = new CustomMenu(customMenu);
        newCustomMenu.setConfig(customMenuConfig);
        return tbCustomMenuService.updateCustomMenu(newCustomMenu, false);
    }

    @ApiOperation(value = "Update Custom Menu name based on the provided Custom Menu Id (updateCustomMenuName)",
            notes = ControllerConstants.CUSTOM_MENU_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PutMapping(value = "/customMenu/{customMenuId}/name")
    public void updateCustomMenuName(@Parameter(description = CUSTOM_MENU_ID_PARAM_DESCRIPTION)
                                     @PathVariable(CUSTOM_MENU_ID) UUID id,
                                     @Parameter(description = "New name of the custom menu")
                                     @RequestBody String name) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        CustomMenu customMenu = checkNotNull(checkCustomMenuId(customMenuId, Operation.WRITE));
        CustomMenu newCustomMenu = new CustomMenu(customMenu);
        newCustomMenu.setName(name);
        customMenuService.updateCustomMenu(newCustomMenu, false);
    }

    @ApiOperation(value = "Create Custom Menu (createCustomMenu)",
            notes = "The api is designed to create Custom Menu without configuration. Is not applicable for update." +
                    ControllerConstants.CUSTOM_MENU_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PostMapping(value = "/customMenu")
    public CustomMenu createCustomMenu(
            @Parameter(description = "A list of entity ids, separated by comma ','", array = @ArraySchema(schema = @Schema(type = "string")))
            @RequestParam(name = "assignToList", required = false) UUID[] ids,
            @Parameter(description = "Use force if you want to create default menu that conflicts with the existing one (old one will be update NO_ASSIGN assignee type)")
            @RequestParam(name = "force", required = false) boolean force,
            @Parameter(description = "A JSON value representing the custom menu basic info fields")
            @RequestBody @Valid CustomMenuInfo customMenuInfo) throws ThingsboardException {
        SecurityUser currentUser = getCurrentUser();
        customMenuInfo.setTenantId(currentUser.getTenantId());
        customMenuInfo.setCustomerId(currentUser.getCustomerId());
        List<EntityId> assignToList = getAssigneeList(customMenuInfo.getAssigneeType(), ids);

        if (customMenuInfo.getId() == null) {
            checkWhiteLabelingPermissions(Operation.WRITE);
            return tbCustomMenuService.createCustomMenu(customMenuInfo, assignToList, force);
        } else {
            throw new ThingsboardException("Update is unsupported.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    @ApiOperation(value = "Update custom menu assignee list (updateCustomMenuAssigneeList)",
            notes = "The api designed to update the list of assignees or assignee type based on the provided Custom Menu Id. " +
                    "To change assignee type, put new assignee type in path parameter." +
                    ControllerConstants.CUSTOM_MENU_WRITE_CHECK)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @PutMapping(value = "/customMenu/{id}/assign/{assigneeType}")
    public void updateCustomMenuAssigneeList(@PathVariable("id") UUID id,
                                             @PathVariable("assigneeType") CMAssigneeType assigneeType,
                                             @Parameter(description = "Use force if you want to override default menu")
                                             @RequestParam(name = "force", required = false) boolean force,
                                             @RequestBody(required = false) UUID[] entityIds) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        CustomMenu customMenu = checkCustomMenuId(customMenuId, Operation.WRITE);
        List<EntityId> assignToList = getAssigneeList(assigneeType, entityIds);
        tbCustomMenuService.updateAssigneeList(customMenu, assigneeType, assignToList, force);
    }

    @ApiOperation(value = "Delete custom menu (deleteCustomMenu)",
            notes = "Deletes the custom menu based on the provided Custom Menu Id. " +
                    "Referencing non-existing custom menu Id will cause an error. " +
                    "If the custom menu is assigned to the list of users or customers bad request is returned." +
                    "To delete a custom menu that has assignee list set 'force' request param to true ")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @DeleteMapping(value = "/customMenu/{customMenuId}")
    public ResponseEntity<CustomMenuDeleteResult> deleteCustomMenu(@Parameter(required = true, description = CUSTOM_MENU_ID_PARAM_DESCRIPTION)
                                                                   @PathVariable(CUSTOM_MENU_ID) UUID id,
                                                                   @Parameter(description = "Force set to true will unassign menu before deletion")
                                                                   @RequestParam(name = "force", required = false) boolean force) throws ThingsboardException {
        CustomMenuId customMenuId = new CustomMenuId(id);
        CustomMenu customMenu = checkNotNull(checkCustomMenuId(customMenuId, Operation.DELETE));
        CustomMenuDeleteResult result = tbCustomMenuService.deleteCustomMenu(customMenu, force);
        return (result.isSuccess() ? ResponseEntity.ok() : ResponseEntity.badRequest()).body(result);
    }

    protected String calculateCustomMenuEtag(String customMenu) {
        return Hashing.sha256().hashString(customMenu, StandardCharsets.UTF_8).toString();
    }

    private List<EntityId> getAssigneeList(CMAssigneeType type, UUID[] ids) throws ThingsboardException {
        if (ids == null) {
            return Collections.emptyList();
        }
        List<EntityId> assignToList = new ArrayList<>();
        switch (type) {
            case NO_ASSIGN:
            case ALL:
                break;
            case CUSTOMERS:
                for (UUID id : ids) {
                    CustomerId customerId = new CustomerId(id);
                    checkCustomerId(customerId, Operation.WRITE);
                    assignToList.add(customerId);
                }
                break;
            case USERS:
                for (UUID id : ids) {
                    UserId userId = new UserId(id);
                    checkUserId(userId, Operation.WRITE);
                    assignToList.add(userId);
                }
                break;
            default:
                throw new IllegalArgumentException("Assignee list is applicable to 'CUSTOMERS' or 'USERS' assignee type only!");
        }
        return assignToList;
    }

    private void checkWhiteLabelingPermissions(Operation operation) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.WHITE_LABELING, operation);
    }
}
