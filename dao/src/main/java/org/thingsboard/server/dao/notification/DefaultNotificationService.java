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
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.NotificationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.notification.Notification;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.NotificationStatus;
import org.thingsboard.server.common.data.notification.NotificationType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.SortOrder;
import org.thingsboard.server.dao.entity.EntityDaoService;
import org.thingsboard.server.dao.sql.query.EntityKeyMapping;

import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultNotificationService implements NotificationService, EntityDaoService {

    private final NotificationDao notificationDao;

    @Override
    public Notification saveNotification(TenantId tenantId, Notification notification) {
        return notificationDao.save(tenantId, notification);
    }

    @Override
    public Notification findNotificationById(TenantId tenantId, NotificationId notificationId) {
        return notificationDao.findById(tenantId, notificationId.getId());
    }

    @Override
    public boolean markNotificationAsRead(TenantId tenantId, UserId recipientId, NotificationId notificationId) {
        return notificationDao.updateStatusByIdAndRecipientId(tenantId, recipientId, notificationId, NotificationStatus.READ);
    }

    @Override
    public int markAllNotificationsAsRead(TenantId tenantId, NotificationDeliveryMethod deliveryMethod, UserId recipientId) {
        return notificationDao.updateStatusByDeliveryMethodAndRecipientId(tenantId, deliveryMethod, recipientId, NotificationStatus.READ);
    }

    @Override
    public PageData<Notification> findNotificationsByRecipientIdAndReadStatus(TenantId tenantId, NotificationDeliveryMethod deliveryMethod, UserId recipientId, boolean unreadOnly, PageLink pageLink) {
        if (unreadOnly) {
            return notificationDao.findUnreadByDeliveryMethodAndRecipientIdAndPageLink(tenantId, deliveryMethod, recipientId, pageLink);
        } else {
            return notificationDao.findByDeliveryMethodAndRecipientIdAndPageLink(tenantId, deliveryMethod, recipientId, pageLink);
        }
    }

    @Override
    public PageData<Notification> findLatestUnreadNotificationsByRecipientIdAndNotificationTypes(TenantId tenantId, NotificationDeliveryMethod deliveryMethod, UserId recipientId, Set<NotificationType> types, int limit) {
        SortOrder sortOrder = new SortOrder(EntityKeyMapping.CREATED_TIME, SortOrder.Direction.DESC);
        PageLink pageLink = new PageLink(limit, 0, null, sortOrder);
        return notificationDao.findUnreadByDeliveryMethodAndRecipientIdAndNotificationTypesAndPageLink(tenantId, deliveryMethod, recipientId, types, pageLink);
    }

    @Override
    public int countUnreadNotificationsByRecipientId(TenantId tenantId, NotificationDeliveryMethod deliveryMethod, UserId recipientId) {
        return notificationDao.countUnreadByDeliveryMethodAndRecipientId(tenantId, deliveryMethod, recipientId);
    }

    @Override
    public boolean deleteNotification(TenantId tenantId, UserId recipientId, NotificationId notificationId) {
        return notificationDao.deleteByIdAndRecipientId(tenantId, recipientId, notificationId);
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findNotificationById(tenantId, new NotificationId(entityId.getId())));
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.NOTIFICATION;
    }

}
