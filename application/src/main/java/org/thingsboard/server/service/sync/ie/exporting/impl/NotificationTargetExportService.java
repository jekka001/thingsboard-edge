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
package org.thingsboard.server.service.sync.ie.exporting.impl;

import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.NotificationTargetId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.notification.targets.NotificationTarget;
import org.thingsboard.server.common.data.notification.targets.NotificationTargetType;
import org.thingsboard.server.common.data.notification.targets.platform.CustomerUsersFilter;
import org.thingsboard.server.common.data.notification.targets.platform.PlatformUsersNotificationTargetConfig;
import org.thingsboard.server.common.data.notification.targets.platform.UserGroupListFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UserRoleFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UsersFilter;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.sync.vc.data.EntitiesExportCtx;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@TbCoreComponent
public class NotificationTargetExportService extends BaseEntityExportService<NotificationTargetId, NotificationTarget, EntityExportData<NotificationTarget>> {

    @Override
    protected void setRelatedEntities(EntitiesExportCtx<?> ctx, NotificationTarget notificationTarget, EntityExportData<NotificationTarget> exportData) {
        if (notificationTarget.getConfiguration().getType() == NotificationTargetType.PLATFORM_USERS) {
            UsersFilter usersFilter = ((PlatformUsersNotificationTargetConfig) notificationTarget.getConfiguration()).getUsersFilter();
            switch (usersFilter.getType()) {
                case CUSTOMER_USERS:
                    CustomerUsersFilter customerUsersFilter = (CustomerUsersFilter) usersFilter;
                    customerUsersFilter.setCustomerId(getExternalIdOrElseInternal(ctx, new CustomerId(customerUsersFilter.getCustomerId())).getId());
                    break;
                case USER_GROUP_LIST:
                    UserGroupListFilter userGroupListFilter = (UserGroupListFilter) usersFilter;
                    userGroupListFilter.setGroupsIds(toExternalIds(userGroupListFilter.getGroupsIds(), EntityGroupId::new, ctx).collect(Collectors.toList()));
                    break;
                case USER_ROLE:
                    UserRoleFilter userRoleFilter = (UserRoleFilter) usersFilter;
                    userRoleFilter.setRolesIds(toExternalIds(userRoleFilter.getRolesIds(), RoleId::new, ctx).collect(Collectors.toList()));
                case USER_LIST:
                    // users list stays as is and is replaced with current user id on import (due to user entities not being supported by VC)
                    break;
            }
        }
    }

    @Override
    public Set<EntityType> getSupportedEntityTypes() {
        return Set.of(EntityType.NOTIFICATION_TARGET);
    }

}
