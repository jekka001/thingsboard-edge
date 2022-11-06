/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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

import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.NotificationId;
import org.thingsboard.server.common.data.id.NotificationRequestId;
import org.thingsboard.server.common.data.id.NotificationRuleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.notification.Notification;
import org.thingsboard.server.common.data.notification.NotificationInfo;
import org.thingsboard.server.common.data.notification.NotificationRequest;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;

import java.util.List;

public interface NotificationService {

    NotificationRequest saveNotificationRequest(TenantId tenantId, NotificationRequest notificationRequest);

    NotificationRequest findNotificationRequestById(TenantId tenantId, NotificationRequestId id);

    PageData<NotificationRequest> findNotificationRequestsByTenantId(TenantId tenantId, PageLink pageLink);

    List<NotificationRequest> findNotificationRequestsByRuleIdAndAlarmId(TenantId tenantId, NotificationRuleId ruleId, AlarmId alarmId);

    void deleteNotificationRequestById(TenantId tenantId, NotificationRequestId id);


    Notification saveNotification(TenantId tenantId, Notification notification);

    Notification findNotificationById(TenantId tenantId, NotificationId notificationId);

    boolean markNotificationAsRead(TenantId tenantId, UserId userId, NotificationId notificationId);

    PageData<Notification> findNotificationsByUserIdAndReadStatus(TenantId tenantId, UserId userId, boolean unreadOnly, PageLink pageLink);

    PageData<Notification> findLatestUnreadNotificationsByUserId(TenantId tenantId, UserId userId, int limit);

    int countUnreadNotificationsByUserId(TenantId tenantId, UserId userId);

    int updateNotificationsInfosByRequestId(TenantId tenantId, NotificationRequestId notificationRequestId, NotificationInfo notificationInfo);

}
