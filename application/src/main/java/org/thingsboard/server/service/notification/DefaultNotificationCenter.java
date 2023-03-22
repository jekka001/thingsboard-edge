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
package org.thingsboard.server.service.notification;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.rule.engine.api.NotificationCenter;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.NotificationId;
import org.thingsboard.server.common.data.id.NotificationRequestId;
import org.thingsboard.server.common.data.id.NotificationTargetId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.notification.AlreadySentException;
import org.thingsboard.server.common.data.notification.Notification;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;
import org.thingsboard.server.common.data.notification.NotificationProcessingContext;
import org.thingsboard.server.common.data.notification.NotificationRequest;
import org.thingsboard.server.common.data.notification.NotificationRequestConfig;
import org.thingsboard.server.common.data.notification.NotificationRequestStats;
import org.thingsboard.server.common.data.notification.NotificationRequestStatus;
import org.thingsboard.server.common.data.notification.NotificationStatus;
import org.thingsboard.server.common.data.notification.NotificationType;
import org.thingsboard.server.common.data.notification.info.RuleOriginatedNotificationInfo;
import org.thingsboard.server.common.data.notification.settings.NotificationSettings;
import org.thingsboard.server.common.data.notification.targets.NotificationRecipient;
import org.thingsboard.server.common.data.notification.targets.NotificationTarget;
import org.thingsboard.server.common.data.notification.targets.platform.PlatformUsersNotificationTargetConfig;
import org.thingsboard.server.common.data.notification.targets.platform.UsersFilterType;
import org.thingsboard.server.common.data.notification.targets.slack.SlackNotificationTargetConfig;
import org.thingsboard.server.common.data.notification.template.DeliveryMethodNotificationTemplate;
import org.thingsboard.server.common.data.notification.template.NotificationTemplate;
import org.thingsboard.server.common.data.notification.template.WebDeliveryMethodNotificationTemplate;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.common.msg.tools.TbRateLimitsException;
import org.thingsboard.server.dao.notification.NotificationRequestService;
import org.thingsboard.server.dao.notification.NotificationService;
import org.thingsboard.server.dao.notification.NotificationSettingsService;
import org.thingsboard.server.dao.notification.NotificationTargetService;
import org.thingsboard.server.dao.notification.NotificationTemplateService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.NotificationsTopicService;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.service.apiusage.limits.LimitedApi;
import org.thingsboard.server.service.apiusage.limits.RateLimitService;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.executors.NotificationExecutorService;
import org.thingsboard.server.service.notification.channels.NotificationChannel;
import org.thingsboard.server.service.subscription.TbSubscriptionUtils;
import org.thingsboard.server.service.telemetry.AbstractSubscriptionService;
import org.thingsboard.server.service.ws.notification.sub.NotificationRequestUpdate;
import org.thingsboard.server.service.ws.notification.sub.NotificationUpdate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@SuppressWarnings({"UnstableApiUsage", "rawtypes"})
public class DefaultNotificationCenter extends AbstractSubscriptionService implements NotificationCenter, NotificationChannel<User, WebDeliveryMethodNotificationTemplate> {

    private final NotificationTargetService notificationTargetService;
    private final NotificationRequestService notificationRequestService;
    private final NotificationService notificationService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationSettingsService notificationSettingsService;
    private final UserService userService;
    private final NotificationExecutorService notificationExecutor;
    private final DbCallbackExecutorService dbCallbackExecutorService;
    private final NotificationsTopicService notificationsTopicService;
    private final TbQueueProducerProvider producerProvider;
    private final RateLimitService rateLimitService;

    private Map<NotificationDeliveryMethod, NotificationChannel> channels;


    @Override
    public NotificationRequest processNotificationRequest(TenantId tenantId, NotificationRequest notificationRequest) {
        if (!rateLimitService.checkRateLimit(tenantId, LimitedApi.NOTIFICATION_REQUEST)) {
            throw new TbRateLimitsException(EntityType.TENANT);
        }
        NotificationSettings settings = notificationSettingsService.findNotificationSettings(tenantId);
        NotificationTemplate notificationTemplate;
        if (notificationRequest.getTemplateId() != null) {
            notificationTemplate = notificationTemplateService.findNotificationTemplateById(tenantId, notificationRequest.getTemplateId());
        } else {
            notificationTemplate = notificationRequest.getTemplate();
        }
        if (notificationTemplate == null) throw new IllegalArgumentException("Template is missing");

        List<NotificationTarget> targets = notificationTargetService.findNotificationTargetsByTenantIdAndIds(tenantId,
                notificationRequest.getTargets().stream().map(NotificationTargetId::new).collect(Collectors.toList()));

        notificationTemplate.getConfiguration().getDeliveryMethodsTemplates().forEach((deliveryMethod, template) -> {
            if (!template.isEnabled()) return;
            if (deliveryMethod == NotificationDeliveryMethod.SLACK) {
                if (!settings.getDeliveryMethodsConfigs().containsKey(deliveryMethod)) {
                    throw new IllegalArgumentException("Slack must be configured in the settings");
                }
            }
            if (notificationRequest.getRuleId() == null) {
                if (targets.stream().noneMatch(target -> target.getConfiguration().getType().getSupportedDeliveryMethods().contains(deliveryMethod))) {
                    throw new IllegalArgumentException("Target for " + deliveryMethod.getName() + " delivery method is missing");
                }
            }
        });

        if (notificationRequest.getAdditionalConfig() != null) {
            NotificationRequestConfig config = notificationRequest.getAdditionalConfig();
            if (config.getSendingDelayInSec() > 0 && notificationRequest.getId() == null) {
                notificationRequest.setStatus(NotificationRequestStatus.SCHEDULED);
                NotificationRequest savedNotificationRequest = notificationRequestService.saveNotificationRequest(tenantId, notificationRequest);
                forwardToNotificationSchedulerService(tenantId, savedNotificationRequest.getId());
                return savedNotificationRequest;
            }
        }

        log.debug("Processing notification request (tenantId: {}, targets: {})", tenantId, notificationRequest.getTargets());
        notificationRequest.setStatus(NotificationRequestStatus.PROCESSING);
        NotificationRequest savedNotificationRequest = notificationRequestService.saveNotificationRequest(tenantId, notificationRequest);

        NotificationProcessingContext ctx = NotificationProcessingContext.builder()
                .tenantId(tenantId)
                .request(savedNotificationRequest)
                .settings(settings)
                .template(notificationTemplate)
                .build();

        notificationExecutor.submit(() -> {
            List<ListenableFuture<Void>> results = new ArrayList<>();

            for (NotificationTarget target : targets) {
                List<ListenableFuture<Void>> result = processForTarget(target, ctx);
                results.addAll(result);
            }

            Futures.whenAllComplete(results).run(() -> {
                NotificationRequestId requestId = savedNotificationRequest.getId();
                log.debug("[{}] Notification request processing is finished", requestId);
                NotificationRequestStats stats = ctx.getStats();
                try {
                    notificationRequestService.updateNotificationRequest(tenantId, requestId, NotificationRequestStatus.SENT, stats);
                } catch (Exception e) {
                    log.error("[{}] Failed to update stats for notification request", requestId, e);
                }
            }, dbCallbackExecutorService);
        });

        return savedNotificationRequest;
    }

    private List<ListenableFuture<Void>> processForTarget(NotificationTarget target, NotificationProcessingContext ctx) {
        Iterable<? extends NotificationRecipient> recipients;
        switch (target.getConfiguration().getType()) {
            case PLATFORM_USERS: {
                PlatformUsersNotificationTargetConfig platformUsersTargetConfig = (PlatformUsersNotificationTargetConfig) target.getConfiguration();
                if (platformUsersTargetConfig.getUsersFilter().getType() == UsersFilterType.AFFECTED_USER) {
                    if (ctx.getRequest().getInfo() instanceof RuleOriginatedNotificationInfo) {
                        UserId targetUserId = ((RuleOriginatedNotificationInfo) ctx.getRequest().getInfo()).getTargetUserId();
                        if (targetUserId != null) {
                            recipients = List.of(userService.findUserById(ctx.getTenantId(), targetUserId));
                            break;
                        }
                    }
                    recipients = Collections.emptyList();
                } else {
                    recipients = new PageDataIterable<>(pageLink -> {
                        return notificationTargetService.findRecipientsForNotificationTargetConfig(ctx.getTenantId(), ctx.getCustomerId(), platformUsersTargetConfig, pageLink);
                    }, 500);
                }
                break;
            }
            case SLACK: {
                SlackNotificationTargetConfig slackTargetConfig = (SlackNotificationTargetConfig) target.getConfiguration();
                recipients = List.of(slackTargetConfig.getConversation());
                break;
            }
            default: {
                recipients = Collections.emptyList();
            }
        }

        Set<NotificationDeliveryMethod> deliveryMethods = new HashSet<>(ctx.getDeliveryMethods());
        deliveryMethods.removeIf(deliveryMethod -> !target.getConfiguration().getType().getSupportedDeliveryMethods().contains(deliveryMethod));
        log.debug("[{}] Processing notification request for {} target ({}) for delivery methods {}", ctx.getRequest().getId(), target.getConfiguration().getType(), target.getId(), deliveryMethods);

        List<ListenableFuture<Void>> results = new ArrayList<>();
        if (!deliveryMethods.isEmpty()) {
            for (NotificationRecipient recipient : recipients) {
                for (NotificationDeliveryMethod deliveryMethod : deliveryMethods) {
                    ListenableFuture<Void> resultFuture = processForRecipient(deliveryMethod, recipient, ctx);
                    DonAsynchron.withCallback(resultFuture, result -> {
                        ctx.getStats().reportSent(deliveryMethod, recipient);
                    }, error -> {
                        ctx.getStats().reportError(deliveryMethod, error, recipient);
                    });
                    results.add(resultFuture);
                }
            }
        }
        return results;
    }

    private ListenableFuture<Void> processForRecipient(NotificationDeliveryMethod deliveryMethod, NotificationRecipient recipient, NotificationProcessingContext ctx) {
        if (ctx.getStats().contains(deliveryMethod, recipient.getId())) {
            return Futures.immediateFailedFuture(new AlreadySentException());
        }
        Map<String, String> templateContext;
        if (recipient instanceof User) {
            templateContext = ctx.createTemplateContext(((User) recipient));
        } else {
            templateContext = Collections.emptyMap();
        }
        DeliveryMethodNotificationTemplate processedTemplate;
        try {
            processedTemplate = ctx.getProcessedTemplate(deliveryMethod, templateContext);
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }

        NotificationChannel notificationChannel = channels.get(deliveryMethod);
        log.trace("[{}] Sending {} notification for recipient {}", ctx.getRequest().getId(), deliveryMethod, recipient);
        return notificationChannel.sendNotification(recipient, processedTemplate, ctx);
    }

    @Override
    public ListenableFuture<Void> sendNotification(User recipient, WebDeliveryMethodNotificationTemplate processedTemplate, NotificationProcessingContext ctx) {
        NotificationRequest request = ctx.getRequest();
        Notification notification = Notification.builder()
                .requestId(request.getId())
                .recipientId(recipient.getId())
                .type(ctx.getNotificationTemplate().getNotificationType())
                .subject(processedTemplate.getSubject())
                .text(processedTemplate.getBody())
                .additionalConfig(processedTemplate.getAdditionalConfig())
                .info(request.getInfo())
                .status(NotificationStatus.SENT)
                .build();
        try {
            notification = notificationService.saveNotification(recipient.getTenantId(), notification);
        } catch (Exception e) {
            log.error("Failed to create notification for recipient {}", recipient.getId(), e);
            return Futures.immediateFailedFuture(e);
        }

        NotificationUpdate update = NotificationUpdate.builder()
                .created(true)
                .notification(notification)
                .build();
        return onNotificationUpdate(recipient.getTenantId(), recipient.getId(), update);
    }

    @Override
    public void sendBasicNotification(TenantId tenantId, UserId recipientId, String subject, String text) {
        Notification notification = Notification.builder()
                .recipientId(recipientId)
                .type(NotificationType.GENERAL)
                .subject(subject)
                .text(text)
                .status(NotificationStatus.SENT)
                .build();
        notification = notificationService.saveNotification(TenantId.SYS_TENANT_ID, notification);

        NotificationUpdate update = NotificationUpdate.builder()
                .created(true)
                .notification(notification)
                .build();
        onNotificationUpdate(tenantId, recipientId, update);
    }

    @Override
    public void markNotificationAsRead(TenantId tenantId, UserId recipientId, NotificationId notificationId) {
        boolean updated = notificationService.markNotificationAsRead(tenantId, recipientId, notificationId);
        if (updated) {
            log.trace("Marked notification {} as read (recipient id: {}, tenant id: {})", notificationId, recipientId, tenantId);
            NotificationUpdate update = NotificationUpdate.builder()
                    .updated(true)
                    .notificationId(notificationId)
                    .newStatus(NotificationStatus.READ)
                    .build();
            onNotificationUpdate(tenantId, recipientId, update);
        }
    }

    @Override
    public void markAllNotificationsAsRead(TenantId tenantId, UserId recipientId) {
        int updatedCount = notificationService.markAllNotificationsAsRead(tenantId, recipientId);
        if (updatedCount > 0) {
            log.trace("Marked all notifications as read (recipient id: {}, tenant id: {})", recipientId, tenantId);
            NotificationUpdate update = NotificationUpdate.builder()
                    .updated(true)
                    .allNotifications(true)
                    .newStatus(NotificationStatus.READ)
                    .build();
            onNotificationUpdate(tenantId, recipientId, update);
        }
    }

    @Override
    public void deleteNotification(TenantId tenantId, UserId recipientId, NotificationId notificationId) {
        Notification notification = notificationService.findNotificationById(tenantId, notificationId);
        boolean deleted = notificationService.deleteNotification(tenantId, recipientId, notificationId);
        if (deleted) {
            NotificationUpdate update = NotificationUpdate.builder()
                    .deleted(true)
                    .notification(notification)
                    .build();
            onNotificationUpdate(tenantId, recipientId, update);
        }
    }

    @Override
    public void deleteNotificationRequest(TenantId tenantId, NotificationRequestId notificationRequestId) {
        log.debug("Deleting notification request {}", notificationRequestId);
        NotificationRequest notificationRequest = notificationRequestService.findNotificationRequestById(tenantId, notificationRequestId);
        notificationRequestService.deleteNotificationRequest(tenantId, notificationRequestId);

        if (notificationRequest.isSent()) {
            // TODO: no need to send request update for other than PLATFORM_USERS target type
            onNotificationRequestUpdate(tenantId, NotificationRequestUpdate.builder()
                    .notificationRequestId(notificationRequestId)
                    .deleted(true)
                    .build());
        } else if (notificationRequest.isScheduled()) {
            clusterService.broadcastEntityStateChangeEvent(tenantId, notificationRequestId, ComponentLifecycleEvent.DELETED);
        }
    }

    private void forwardToNotificationSchedulerService(TenantId tenantId, NotificationRequestId notificationRequestId) {
        TransportProtos.NotificationSchedulerServiceMsg.Builder msg = TransportProtos.NotificationSchedulerServiceMsg.newBuilder()
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setRequestIdMSB(notificationRequestId.getId().getMostSignificantBits())
                .setRequestIdLSB(notificationRequestId.getId().getLeastSignificantBits())
                .setTs(System.currentTimeMillis());
        TransportProtos.ToCoreMsg toCoreMsg = TransportProtos.ToCoreMsg.newBuilder()
                .setNotificationSchedulerServiceMsg(msg)
                .build();
        clusterService.pushMsgToCore(tenantId, notificationRequestId, toCoreMsg, null);
    }

    private ListenableFuture<Void> onNotificationUpdate(TenantId tenantId, UserId recipientId, NotificationUpdate update) {
        log.trace("Submitting notification update for recipient {}: {}", recipientId, update);
        return Futures.submit(() -> {
            forwardToSubscriptionManagerService(tenantId, recipientId, subscriptionManagerService -> {
                subscriptionManagerService.onNotificationUpdate(tenantId, recipientId, update, TbCallback.EMPTY);
            }, () -> TbSubscriptionUtils.notificationUpdateToProto(tenantId, recipientId, update));
        }, wsCallBackExecutor);
    }

    private void onNotificationRequestUpdate(TenantId tenantId, NotificationRequestUpdate update) {
        log.trace("Submitting notification request update: {}", update);
        wsCallBackExecutor.submit(() -> {
            TransportProtos.ToCoreNotificationMsg notificationRequestUpdateProto = TbSubscriptionUtils.notificationRequestUpdateToProto(tenantId, update);
            Set<String> coreServices = new HashSet<>(partitionService.getAllServiceIds(ServiceType.TB_CORE));
            for (String serviceId : coreServices) {
                TopicPartitionInfo tpi = notificationsTopicService.getNotificationsTopic(ServiceType.TB_CORE, serviceId);
                producerProvider.getTbCoreNotificationsMsgProducer().send(tpi, new TbProtoQueueMsg<>(UUID.randomUUID(), notificationRequestUpdateProto), null);
            }
        });
    }

    @Override
    public NotificationDeliveryMethod getDeliveryMethod() {
        return NotificationDeliveryMethod.WEB;
    }

    @Override
    protected String getExecutorPrefix() {
        return "notification";
    }

    @Autowired
    public void setChannels(List<NotificationChannel> channels, NotificationCenter webNotificationChannel) {
        this.channels = channels.stream().collect(Collectors.toMap(NotificationChannel::getDeliveryMethod, c -> c));
        this.channels.put(NotificationDeliveryMethod.WEB, (NotificationChannel) webNotificationChannel);
    }

}
