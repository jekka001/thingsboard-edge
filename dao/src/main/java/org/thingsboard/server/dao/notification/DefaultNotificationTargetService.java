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
package org.thingsboard.server.dao.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.NotificationTargetId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.notification.NotificationRequestStatus;
import org.thingsboard.server.common.data.notification.NotificationType;
import org.thingsboard.server.common.data.notification.info.RuleOriginatedNotificationInfo;
import org.thingsboard.server.common.data.notification.targets.NotificationTarget;
import org.thingsboard.server.common.data.notification.targets.NotificationTargetConfig;
import org.thingsboard.server.common.data.notification.targets.platform.CustomerUsersFilter;
import org.thingsboard.server.common.data.notification.targets.platform.PlatformUsersNotificationTargetConfig;
import org.thingsboard.server.common.data.notification.targets.platform.TenantAdministratorsFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UserGroupListFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UserListFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UserRoleFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UsersFilter;
import org.thingsboard.server.common.data.notification.targets.platform.UsersFilterType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entity.EntityDaoService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.user.UserService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultNotificationTargetService extends AbstractEntityService implements NotificationTargetService, EntityDaoService {

    private final NotificationTargetDao notificationTargetDao;
    private final NotificationRequestDao notificationRequestDao;
    private final NotificationRuleDao notificationRuleDao;
    private final UserService userService;
    private final RoleService roleService;

    @Override
    public NotificationTarget saveNotificationTarget(TenantId tenantId, NotificationTarget notificationTarget) {
        try {
            NotificationTarget savedTarget = notificationTargetDao.saveAndFlush(tenantId, notificationTarget);
            eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entityId(savedTarget.getId())
                    .created(notificationTarget.getId() == null).build());
            return savedTarget;
        } catch (Exception e) {
            checkConstraintViolation(e, Map.of(
                    "uq_notification_target_name", "Recipients group with such name already exists"
            ));
            throw e;
        }
    }

    @Override
    public NotificationTarget findNotificationTargetById(TenantId tenantId, NotificationTargetId id) {
        return notificationTargetDao.findById(tenantId, id.getId());
    }

    @Override
    public PageData<NotificationTarget> findNotificationTargetsByTenantId(TenantId tenantId, PageLink pageLink) {
        return notificationTargetDao.findByTenantIdAndPageLink(tenantId, pageLink);
    }

    @Override
    public PageData<NotificationTarget> findNotificationTargetsByTenantIdAndSupportedNotificationType(TenantId tenantId, NotificationType notificationType, PageLink pageLink) {
        return notificationTargetDao.findByTenantIdAndSupportedNotificationTypeAndPageLink(tenantId, notificationType, pageLink);
    }

    @Override
    public List<NotificationTarget> findNotificationTargetsByTenantIdAndIds(TenantId tenantId, List<NotificationTargetId> ids) {
        return notificationTargetDao.findByTenantIdAndIds(tenantId, ids);
    }

    @Override
    public List<NotificationTarget> findNotificationTargetsByTenantIdAndUsersFilterType(TenantId tenantId, UsersFilterType filterType) {
        return notificationTargetDao.findByTenantIdAndUsersFilterType(tenantId, filterType);
    }

    @Override
    public PageData<User> findRecipientsForNotificationTarget(TenantId tenantId, CustomerId customerId, NotificationTargetId targetId, PageLink pageLink) {
        NotificationTarget notificationTarget = findNotificationTargetById(tenantId, targetId);
        Objects.requireNonNull(notificationTarget, "Notification target [" + targetId + "] not found");
        NotificationTargetConfig configuration = notificationTarget.getConfiguration();
        return findRecipientsForNotificationTargetConfig(notificationTarget.getTenantId(), (PlatformUsersNotificationTargetConfig) configuration, pageLink);
    }

    @Override
    public PageData<User> findRecipientsForNotificationTargetConfig(TenantId tenantId, PlatformUsersNotificationTargetConfig targetConfig, PageLink pageLink) {
        UsersFilter usersFilter = targetConfig.getUsersFilter();
        switch (usersFilter.getType()) {
            case USER_LIST: {
                List<User> users = ((UserListFilter) usersFilter).getUsersIds().stream()
                        .limit(pageLink.getPageSize())
                        .map(UserId::new).map(userId -> userService.findUserById(tenantId, userId))
                        .filter(Objects::nonNull).collect(Collectors.toList());
                return new PageData<>(users, 1, users.size(), false);
            }
            case USER_GROUP_LIST: {
                List<EntityGroupId> groups = DaoUtil.fromUUIDs(((UserGroupListFilter) usersFilter).getGroupsIds(), EntityGroupId::new);
                return userService.findUsersByEntityGroupIds(groups, pageLink);
            }
            case USER_ROLE: {
                List<RoleId> roles = DaoUtil.fromUUIDs(((UserRoleFilter) usersFilter).getRolesIds(), RoleId::new);
                return userService.findUsersByTenantIdAndRoles(tenantId, roles, pageLink);
            }
            case CUSTOMER_USERS: {
                if (tenantId.equals(TenantId.SYS_TENANT_ID)) {
                    throw new IllegalArgumentException("Customer users target is not supported for system administrator");
                }
                CustomerUsersFilter filter = (CustomerUsersFilter) usersFilter;
                return userService.findCustomerUsers(tenantId, new CustomerId(filter.getCustomerId()), pageLink);
            }
            case TENANT_ADMINISTRATORS: {
                TenantAdministratorsFilter filter = (TenantAdministratorsFilter) usersFilter;
                Role tenantAdminsRole = roleService.findOrCreateTenantAdminRole();
                if (!tenantId.equals(TenantId.SYS_TENANT_ID)) {
                    return userService.findUsersByTenantsIdsAndRoleId(List.of(tenantId), tenantAdminsRole.getId(), pageLink);
                } else {
                    if (isNotEmpty(filter.getTenantsIds())) {
                        return userService.findUsersByTenantsIdsAndRoleId(filter.getTenantsIds().stream()
                                        .map(TenantId::fromUUID).collect(Collectors.toList()),
                                tenantAdminsRole.getId(), pageLink);
                    } else if (isNotEmpty(filter.getTenantProfilesIds())) {
                        return userService.findUsersByTenantProfilesIdsAndRoleId(filter.getTenantProfilesIds().stream()
                                        .map(TenantProfileId::new).collect(Collectors.toList()),
                                tenantAdminsRole.getId(), pageLink);
                    } else {
                        return userService.findAllUsersByRoleId(tenantAdminsRole.getId(), pageLink);
                    }
                }
            }
            case SYSTEM_ADMINISTRATORS:
                return userService.findSysAdmins(pageLink);
            case ALL_USERS: {
                if (!tenantId.equals(TenantId.SYS_TENANT_ID)) {
                    return userService.findUsersByTenantId(tenantId, pageLink);
                } else {
                    return userService.findAllUsers(pageLink);
                }
            }
            default:
                throw new IllegalArgumentException("Recipient type not supported");
        }
    }

    @Override
    public PageData<User> findRecipientsForRuleNotificationTargetConfig(TenantId tenantId, PlatformUsersNotificationTargetConfig targetConfig, RuleOriginatedNotificationInfo info, PageLink pageLink) {
        switch (targetConfig.getUsersFilter().getType()) {
            case ORIGINATOR_ENTITY_OWNER_USERS -> {
                CustomerId customerId = info.getAffectedCustomerId();
                if (customerId != null && !customerId.isNullUid()) {
                    return userService.findCustomerUsers(tenantId, customerId, pageLink);
                } else {
                    return userService.findTenantAdmins(tenantId, pageLink);
                }
            }
            case AFFECTED_USER -> {
                UserId userId = info.getAffectedUserId();
                if (userId != null) {
                    return new PageData<>(List.of(userService.findUserById(tenantId, userId)), 1, 1, false);
                }
            }
            case AFFECTED_TENANT_ADMINISTRATORS -> {
                TenantId affectedTenantId = info.getAffectedTenantId();
                if (affectedTenantId == null) {
                    affectedTenantId = tenantId;
                }
                if (!affectedTenantId.isNullUid()) {
                    return userService.findTenantAdmins(affectedTenantId, pageLink);
                }
            }
            default -> throw new IllegalArgumentException("Recipient type not supported");
        }
        return new PageData<>();
    }

    @Override
    public void deleteNotificationTargetById(TenantId tenantId, NotificationTargetId id) {
        deleteEntity(tenantId, id, false);
    }

    @Override
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        NotificationTargetId targetId = (NotificationTargetId) id;
        if (!force && notificationRequestDao.existsByTenantIdAndStatusAndTargetId(tenantId, NotificationRequestStatus.SCHEDULED, targetId)) {
            throw new IllegalArgumentException("Recipients group is referenced by scheduled notification request");
        }
        if (!force && notificationRuleDao.existsByTenantIdAndTargetId(tenantId, targetId)) {
            throw new IllegalArgumentException("Recipients group is being used in notification rule");
        }
        notificationTargetDao.removeById(tenantId, id.getId());
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(id).build());
    }

    @Override
    public void deleteNotificationTargetsByTenantId(TenantId tenantId) {
        notificationTargetDao.removeByTenantId(tenantId);
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        deleteNotificationTargetsByTenantId(tenantId);
    }

    @Override
    public long countNotificationTargetsByTenantId(TenantId tenantId) {
        return notificationTargetDao.countByTenantId(tenantId);
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findNotificationTargetById(tenantId, new NotificationTargetId(entityId.getId())));
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.NOTIFICATION_TARGET;
    }

}
