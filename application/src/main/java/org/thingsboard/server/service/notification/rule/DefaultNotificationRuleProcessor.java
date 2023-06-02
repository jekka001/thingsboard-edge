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
package org.thingsboard.server.service.notification.rule;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.thingsboard.rule.engine.api.NotificationCenter;
import org.thingsboard.server.common.data.CacheConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.NotificationRequestId;
import org.thingsboard.server.common.data.id.NotificationRuleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.limit.LimitedApi;
import org.thingsboard.server.common.data.notification.NotificationRequest;
import org.thingsboard.server.common.data.notification.NotificationRequestConfig;
import org.thingsboard.server.common.data.notification.NotificationRequestStatus;
import org.thingsboard.server.common.data.notification.info.NotificationInfo;
import org.thingsboard.server.common.data.notification.rule.NotificationRule;
import org.thingsboard.server.common.data.notification.rule.trigger.NotificationRuleTriggerConfig;
import org.thingsboard.server.common.data.notification.rule.trigger.NotificationRuleTriggerType;
import org.thingsboard.server.common.data.notification.settings.TriggerTypeConfig;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.msg.notification.NotificationRuleProcessor;
import org.thingsboard.server.common.msg.notification.trigger.NotificationRuleTrigger;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.dao.notification.NotificationRequestService;
import org.thingsboard.server.dao.util.limits.RateLimitService;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.service.executors.NotificationExecutorService;
import org.thingsboard.server.service.notification.rule.cache.NotificationRulesCache;
import org.thingsboard.server.service.notification.rule.trigger.NotificationRuleTriggerProcessor;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "notification-system.rules")
@Slf4j
@SuppressWarnings({"rawtypes", "unchecked"})
public class DefaultNotificationRuleProcessor implements NotificationRuleProcessor {

    private final NotificationRulesCache notificationRulesCache;
    private final NotificationRequestService notificationRequestService;
    private final PartitionService partitionService;
    private final RateLimitService rateLimitService;
    @Autowired @Lazy
    private NotificationCenter notificationCenter;
    private final NotificationExecutorService notificationExecutor;
    private final CacheManager cacheManager;
    private Cache sentNotifications;
    @Setter
    private Map<NotificationRuleTriggerType, TriggerTypeConfig> triggerTypesConfigs;

    private final Map<NotificationRuleTriggerType, NotificationRuleTriggerProcessor> triggerProcessors = new EnumMap<>(NotificationRuleTriggerType.class);

    @PostConstruct
    private void init() {
        sentNotifications = cacheManager.getCache(CacheConstants.SENT_NOTIFICATIONS_CACHE);
        if (sentNotifications == null) {
            throw new IllegalStateException("Sent notifications cache is not set up");
        }
    }

    @Override
    public void process(NotificationRuleTrigger trigger) {
        NotificationRuleTriggerType triggerType = trigger.getType();
        TenantId tenantId = triggerType.isTenantLevel() ? trigger.getTenantId() : TenantId.SYS_TENANT_ID;

        try {
            List<NotificationRule> enabledRules = notificationRulesCache.getEnabled(tenantId, triggerType);
            if (enabledRules.isEmpty()) {
                return;
            }
            if (trigger.deduplicate()) {
                enabledRules = new ArrayList<>(enabledRules);
                enabledRules.removeIf(rule -> alreadySent(rule, trigger));
            }
            final List<NotificationRule> rules = enabledRules;
            notificationExecutor.submit(() -> {
                for (NotificationRule rule : rules) {
                    try {
                        processNotificationRule(rule, trigger);
                    } catch (Throwable e) {
                        log.error("Failed to process notification rule {} for trigger type {} with trigger object {}", rule.getId(), rule.getTriggerType(), trigger, e);
                    }
                }
            });
        } catch (Throwable e) {
            log.error("Failed to process notification rules for trigger: {}", trigger, e);
        }
    }

    private void processNotificationRule(NotificationRule rule, NotificationRuleTrigger trigger) {
        NotificationRuleTriggerConfig triggerConfig = rule.getTriggerConfig();
        log.debug("Processing notification rule '{}' for trigger type {}", rule.getName(), rule.getTriggerType());

        if (matchesClearRule(trigger, triggerConfig)) {
            List<NotificationRequest> notificationRequests = findAlreadySentNotificationRequests(rule, trigger);
            if (notificationRequests.isEmpty()) {
                return;
            }

            List<UUID> targets = notificationRequests.stream()
                    .filter(NotificationRequest::isSent)
                    .flatMap(notificationRequest -> notificationRequest.getTargets().stream())
                    .distinct().collect(Collectors.toList());
            NotificationInfo notificationInfo = constructNotificationInfo(trigger, triggerConfig);
            submitNotificationRequest(targets, rule, trigger.getOriginatorEntityId(), notificationInfo, 0);

            notificationRequests.forEach(notificationRequest -> {
                if (notificationRequest.isScheduled()) {
                    notificationCenter.deleteNotificationRequest(rule.getTenantId(), notificationRequest.getId());
                }
            });
            return;
        }

        if (matchesFilter(trigger, triggerConfig)) {
            if (!rateLimitService.checkRateLimit(LimitedApi.NOTIFICATION_REQUESTS_PER_RULE, rule.getTenantId(), rule.getId())) {
                log.debug("[{}] Rate limit for notification requests per rule was exceeded (rule '{}')", rule.getTenantId(), rule.getName());
                return;
            }

            NotificationInfo notificationInfo = constructNotificationInfo(trigger, triggerConfig);
            rule.getRecipientsConfig().getTargetsTable().forEach((delay, targets) -> {
                submitNotificationRequest(targets, rule, trigger.getOriginatorEntityId(), notificationInfo, delay);
            });
        }
    }

    private List<NotificationRequest> findAlreadySentNotificationRequests(NotificationRule rule, NotificationRuleTrigger trigger) {
        return notificationRequestService.findNotificationRequestsByRuleIdAndOriginatorEntityId(rule.getTenantId(), rule.getId(), trigger.getOriginatorEntityId());
    }

    private void submitNotificationRequest(List<UUID> targets, NotificationRule rule,
                                           EntityId originatorEntityId, NotificationInfo notificationInfo, int delayInSec) {
        NotificationRequestConfig config = new NotificationRequestConfig();
        if (delayInSec > 0) {
            config.setSendingDelayInSec(delayInSec);
        }
        NotificationRequest notificationRequest = NotificationRequest.builder()
                .tenantId(rule.getTenantId())
                .targets(targets)
                .templateId(rule.getTemplateId())
                .additionalConfig(config)
                .info(notificationInfo)
                .ruleId(rule.getId())
                .originatorEntityId(originatorEntityId)
                .build();

        try {
            log.debug("Submitting notification request for rule '{}' with delay of {} sec to targets {}", rule.getName(), delayInSec, targets);
            notificationCenter.processNotificationRequest(rule.getTenantId(), notificationRequest, null);
        } catch (Exception e) {
            log.error("Failed to process notification request for tenant {} for rule {}", rule.getTenantId(), rule.getId(), e);
        }
    }

    private boolean matchesFilter(NotificationRuleTrigger trigger, NotificationRuleTriggerConfig triggerConfig) {
        return triggerProcessors.get(triggerConfig.getTriggerType()).matchesFilter(trigger, triggerConfig);
    }

    private boolean matchesClearRule(NotificationRuleTrigger trigger, NotificationRuleTriggerConfig triggerConfig) {
        return triggerProcessors.get(triggerConfig.getTriggerType()).matchesClearRule(trigger, triggerConfig);
    }

    private NotificationInfo constructNotificationInfo(NotificationRuleTrigger trigger, NotificationRuleTriggerConfig triggerConfig) {
        return triggerProcessors.get(triggerConfig.getTriggerType()).constructNotificationInfo(trigger);
    }

    private boolean alreadySent(NotificationRule rule, NotificationRuleTrigger trigger) {
        String deduplicationKey = getDeduplicationKey(trigger, rule);

        boolean alreadySent = false;
        Long lastSentTs = sentNotifications.get(deduplicationKey, Long.class);
        if (lastSentTs != null) {
            long deduplicationDuration = Optional.ofNullable(triggerTypesConfigs)
                    .map(triggerTypes -> triggerTypes.get(trigger.getType()))
                    .map(TriggerTypeConfig::getDeduplicationDuration)
                    .orElseGet(trigger::getDefaultDeduplicationDuration);
            long passed = System.currentTimeMillis() - lastSentTs;
            log.trace("Deduplicating trigger {} for rule '{}' by key '{}'. Deduplication duration: {} ms, passed: {} ms",
                    trigger.getType(), rule.getName(), deduplicationKey, deduplicationDuration, passed);
            if (deduplicationDuration == 0 || passed <= deduplicationDuration) {
                alreadySent = true;
            }
        }
        if (!alreadySent) {
            lastSentTs = System.currentTimeMillis();
        }
        sentNotifications.put(deduplicationKey, lastSentTs);
        return alreadySent;
    }

    public static String getDeduplicationKey(NotificationRuleTrigger trigger, NotificationRule rule) {
        return String.join("_", trigger.getDeduplicationKey(), rule.getDeduplicationKey());
    }

    @EventListener(ComponentLifecycleMsg.class)
    public void onNotificationRuleDeleted(ComponentLifecycleMsg componentLifecycleMsg) {
        if (componentLifecycleMsg.getEvent() != ComponentLifecycleEvent.DELETED ||
                componentLifecycleMsg.getEntityId().getEntityType() != EntityType.NOTIFICATION_RULE) {
            return;
        }

        TenantId tenantId = componentLifecycleMsg.getTenantId();
        NotificationRuleId notificationRuleId = (NotificationRuleId) componentLifecycleMsg.getEntityId();
        if (partitionService.isMyPartition(ServiceType.TB_CORE, tenantId, notificationRuleId)) {
            notificationExecutor.submit(() -> {
                List<NotificationRequestId> scheduledForRule = notificationRequestService.findNotificationRequestsIdsByStatusAndRuleId(tenantId, NotificationRequestStatus.SCHEDULED, notificationRuleId);
                for (NotificationRequestId notificationRequestId : scheduledForRule) {
                    notificationCenter.deleteNotificationRequest(tenantId, notificationRequestId);
                }
            });
        }
    }

    @Autowired
    public void setTriggerProcessors(Collection<NotificationRuleTriggerProcessor> processors) {
        processors.forEach(processor -> {
            triggerProcessors.put(processor.getTriggerType(), processor);
        });
    }

}
