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
package org.thingsboard.server.controller;

import com.google.common.util.concurrent.ListenableFuture;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DashboardInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.page.TimePageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.thingsboard.server.controller.EntityGroupController.ENTITY_GROUP_ID;


@RestController
@TbCoreComponent
@RequestMapping("/api")
public class DashboardController extends BaseController {

    public static final String DASHBOARD_ID = "dashboardId";

    @Value("${dashboard.max_datapoints_limit}")
    private long maxDatapointsLimit;


    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/serverTime", method = RequestMethod.GET)
    @ResponseBody
    public long getServerTime() throws ThingsboardException {
        return System.currentTimeMillis();
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/maxDatapointsLimit", method = RequestMethod.GET)
    @ResponseBody
    public long getMaxDatapointsLimit() throws ThingsboardException {
        return maxDatapointsLimit;
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/info/{dashboardId}", method = RequestMethod.GET)
    @ResponseBody
    public DashboardInfo getDashboardInfoById(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            return checkDashboardInfoId(dashboardId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/{dashboardId}", method = RequestMethod.GET)
    @ResponseBody
    public Dashboard getDashboardById(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            return checkDashboardId(dashboardId, Operation.READ);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard", method = RequestMethod.POST)
    @ResponseBody
    public Dashboard saveDashboard(@RequestBody Dashboard dashboard,
                                   @RequestParam(name = "entityGroupId", required = false) String strEntityGroupId) throws ThingsboardException {
        return saveGroupEntity(dashboard, strEntityGroupId, dashboardService::saveDashboard);
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboard/{dashboardId}", method = RequestMethod.DELETE)
    @ResponseStatus(value = HttpStatus.OK)
    public void deleteDashboard(@PathVariable(DASHBOARD_ID) String strDashboardId) throws ThingsboardException {
        checkParameter(DASHBOARD_ID, strDashboardId);
        try {
            DashboardId dashboardId = new DashboardId(toUUID(strDashboardId));
            Dashboard dashboard = checkDashboardId(dashboardId, Operation.DELETE);
            dashboardService.deleteDashboard(getCurrentUser().getTenantId(), dashboardId);
            logEntityAction(dashboardId, dashboard,
                    null,
                    ActionType.DELETED, null, strDashboardId);

        } catch (Exception e) {
            logEntityAction(emptyId(EntityType.DASHBOARD),
                    null,
                    null,
                    ActionType.DELETED, e, strDashboardId);
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/tenant/{tenantId}/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<DashboardInfo> getTenantDashboards(
            @PathVariable("tenantId") String strTenantId,
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            TenantId tenantId = new TenantId(toUUID(strTenantId));
            checkTenantId(tenantId, Operation.READ);
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            return checkNotNull(dashboardService.findDashboardsByTenantId(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/tenant/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<DashboardInfo> getTenantDashboards(
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            accessControlService.checkPermission(getCurrentUser(), Resource.DASHBOARD, Operation.READ);
            TenantId tenantId = getCurrentUser().getTenantId();
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            return checkNotNull(dashboardService.findDashboardsByTenantId(tenantId, pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @RequestMapping(value = "/user/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<DashboardInfo> getUserDashboards(
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset,
            @RequestParam(required = false) String operation,
            @RequestParam(name = "userId", required = false) String strUserId) throws ThingsboardException {
        try {
            SecurityUser securityUser;
            if (!StringUtils.isEmpty(strUserId)) {
                UserId userId = new UserId(toUUID(strUserId));
                User user = checkUserId(userId, Operation.READ);
                UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
                securityUser = new SecurityUser(user, true, principal, getMergedUserPermissions(user, false));
            } else {
                securityUser = getCurrentUser();
            }
            Operation operationType = Operation.READ;
            if (!StringUtils.isEmpty(operation)) {
                try {
                    operationType = Operation.valueOf(operation);
                } catch (IllegalArgumentException e) {
                    throw new ThingsboardException("Unsupported operation type '" + operation + "'!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
                }
            }
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            return getGroupEntitiesByPageLink(securityUser, EntityType.DASHBOARD, operationType, entityId -> new DashboardId(entityId.getId()),
                    (entityIds) -> {
                        try {
                            return dashboardService.findDashboardInfoByIdsAsync(getTenantId(), entityIds).get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    },
                    pageLink);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/dashboards", params = { "limit" }, method = RequestMethod.GET)
    @ResponseBody
    public TextPageData<DashboardInfo> getGroupDashboards(
            @PathVariable("entityGroupId") String strEntityGroupId,
            @RequestParam int limit,
            @RequestParam(required = false) String textSearch,
            @RequestParam(required = false) String idOffset,
            @RequestParam(required = false) String textOffset) throws ThingsboardException {
        try {
            checkParameter("entityGroupId", strEntityGroupId);
            EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
            EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
            if (entityGroup.getType() != EntityType.DASHBOARD) {
                throw new ThingsboardException("Invalid entity group type '" + entityGroup.getType() + "'! Should be 'DASHBOARD'.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
            TextPageLink pageLink = createPageLink(limit, textSearch, idOffset, textOffset);
            List<EntityId> ids = entityGroupService.findAllEntityIds(getTenantId(), entityGroupId, new TimePageLink(Integer.MAX_VALUE)).get();
            List<DashboardId> dashboardIdsList = new ArrayList<>();
            ids.forEach((dashboardId) -> dashboardIdsList.add(new DashboardId(dashboardId.getId())));
            return loadAndFilterEntities(dashboardIdsList, (entityIds) -> {
                try {
                    return dashboardService.findDashboardInfoByIdsAsync(getTenantId(), entityIds).get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, pageLink);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/dashboards", params = {"dashboardIds"}, method = RequestMethod.GET)
    @ResponseBody
    public List<DashboardInfo> getDashboardsByIds(
            @RequestParam("dashboardIds") String[] strDashboardIds) throws ThingsboardException {
        checkArrayParameter("dashboardIds", strDashboardIds);
        try {
            SecurityUser user = getCurrentUser();
            TenantId tenantId = user.getTenantId();
            List<DashboardId> dashboardIds = new ArrayList<>();
            for (String strDashboardId : strDashboardIds) {
                dashboardIds.add(new DashboardId(toUUID(strDashboardId)));
            }
            List<DashboardInfo> dashboards = checkNotNull(dashboardService.findDashboardInfoByIdsAsync(tenantId, dashboardIds).get());
            return filterDashboardsByReadPermission(dashboards);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/entityGroup/{entityGroupId}/dashboards", method = RequestMethod.GET)
    @ResponseBody
    public TimePageData<DashboardInfo> getDashboardsByEntityGroupId(
            @PathVariable(ENTITY_GROUP_ID) String strEntityGroupId,
            @ApiParam(value = "Page link limit", required = true, allowableValues = "range[1, infinity]") @RequestParam int limit,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false, defaultValue = "false") boolean ascOrder,
            @RequestParam(required = false) String offset
    ) throws ThingsboardException {
        checkParameter(ENTITY_GROUP_ID, strEntityGroupId);
        EntityGroupId entityGroupId = new EntityGroupId(toUUID(strEntityGroupId));
        EntityGroup entityGroup = checkEntityGroupId(entityGroupId, Operation.READ);
        EntityType entityType = entityGroup.getType();
        checkEntityGroupType(entityType);
        try {
            TimePageLink pageLink = createPageLink(limit, startTime, endTime, ascOrder, offset);
            ListenableFuture<TimePageData<DashboardInfo>> asyncResult = dashboardService.findDashboardEntitiesByEntityGroupId(getTenantId(), entityGroupId, pageLink);
            checkNotNull(asyncResult);
            if (asyncResult != null) {
                return checkNotNull(asyncResult.get());
            } else {
                throw new ThingsboardException("Requested item wasn't found!", ThingsboardErrorCode.ITEM_NOT_FOUND);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private List<DashboardInfo> filterDashboardsByReadPermission(List<DashboardInfo> dashboards) {
        return dashboards.stream().filter(dashboard -> {
            try {
                return accessControlService.hasPermission(getCurrentUser(), Resource.DASHBOARD, Operation.READ, dashboard.getId(), dashboard);
            } catch (ThingsboardException e) {
                return false;
            }
        }).collect(Collectors.toList());
    }

}
