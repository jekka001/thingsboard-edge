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
package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.NotificationId;
import org.thingsboard.server.common.data.id.NotificationRequestId;
import org.thingsboard.server.common.data.notification.Notification;
import org.thingsboard.server.common.data.notification.NotificationRequest;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.dao.notification.NotificationService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.notification.NotificationSubscriptionService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.UUID;

@RestController
@TbCoreComponent
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class NotificationController extends BaseController {

    private final NotificationService notificationService;
    private final NotificationSubscriptionService notificationSubscriptionService;

    @GetMapping("/notifications")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    public PageData<Notification> getNotifications(@RequestParam int pageSize,
                                                   @RequestParam int page,
                                                   @RequestParam(required = false) String textSearch,
                                                   @RequestParam(required = false) String sortProperty,
                                                   @RequestParam(required = false) String sortOrder,
                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                   @AuthenticationPrincipal SecurityUser user) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return notificationService.findNotificationsByUserIdAndReadStatus(user.getTenantId(), user.getId(), unreadOnly, pageLink);
    }

    @PutMapping("/notification/{id}/read") // or maybe to NotificationUpdateRequest for the future
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    public void markNotificationAsRead(@PathVariable UUID id,
                                       @AuthenticationPrincipal SecurityUser user) {
        NotificationId notificationId = new NotificationId(id);
        notificationSubscriptionService.markNotificationAsRead(user.getTenantId(), user.getId(), notificationId);
    }

    // delete notification?

    @PostMapping("/notification/request")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    public NotificationRequest createNotificationRequest(@RequestBody NotificationRequest notificationRequest,
                                                         @AuthenticationPrincipal SecurityUser user) throws ThingsboardException {
        accessControlService.checkPermission(user, Resource.NOTIFICATION_REQUEST, Operation.CREATE, null, notificationRequest);
        if (notificationRequest.getId() != null) {
            throw new IllegalArgumentException("Notification request cannot be changed. You can delete it and create a new one");
        }
        try {
            NotificationRequest savedNotificationRequest = notificationSubscriptionService.processNotificationRequest(user.getTenantId(), notificationRequest);
            logEntityAction(user, EntityType.NOTIFICATION_REQUEST, savedNotificationRequest, ActionType.ADDED);
            return savedNotificationRequest;
        } catch (Exception e) {
            logEntityAction(user, EntityType.NOTIFICATION_REQUEST, notificationRequest, null, ActionType.ADDED, e);
            throw e;
        }
    }

    @GetMapping("/notification/request/{id}")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    public NotificationRequest getNotificationRequestById(@PathVariable UUID id,
                                                          @AuthenticationPrincipal SecurityUser user) {
        NotificationRequestId notificationRequestId = new NotificationRequestId(id);
        return notificationService.findNotificationRequestById(user.getTenantId(), notificationRequestId);
    }

    @GetMapping("/notification/requests")
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    public PageData<NotificationRequest> getNotificationRequests(@RequestParam int pageSize,
                                                                 @RequestParam int page,
                                                                 @RequestParam(required = false) String textSearch,
                                                                 @RequestParam(required = false) String sortProperty,
                                                                 @RequestParam(required = false) String sortOrder,
                                                                 @AuthenticationPrincipal SecurityUser user) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return notificationService.findNotificationRequestsByTenantId(user.getTenantId(), pageLink);
    }

    @DeleteMapping("/notification/request/{id}")
    public void deleteNotificationRequest(@PathVariable UUID id,
                                          @AuthenticationPrincipal SecurityUser user) throws ThingsboardException {
        NotificationRequestId notificationRequestId = new NotificationRequestId(id);
        NotificationRequest notificationRequest = notificationService.findNotificationRequestById(user.getTenantId(), notificationRequestId);
        accessControlService.checkPermission(user, Resource.NOTIFICATION_REQUEST, Operation.DELETE, notificationRequestId, notificationRequest);
        try {
            notificationSubscriptionService.deleteNotificationRequest(user.getTenantId(), notificationRequestId);
            logEntityAction(user, EntityType.NOTIFICATION_REQUEST, notificationRequest, ActionType.DELETED);
        } catch (Exception e) {
            logEntityAction(user, EntityType.NOTIFICATION_REQUEST, notificationRequest, notificationRequest, ActionType.DELETED, e);
            throw e;
        }
    }

}
