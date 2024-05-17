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
package org.thingsboard.server.cache.limits;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.exception.TenantProfileNotFoundException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.limit.LimitedApi;
import org.thingsboard.server.common.data.notification.rule.trigger.RateLimitsTrigger;
import org.thingsboard.server.common.msg.notification.NotificationRuleProcessor;
import org.thingsboard.server.common.msg.tools.TbRateLimits;

import java.util.concurrent.TimeUnit;

@Lazy
@Service
@Slf4j
public class DefaultRateLimitService implements RateLimitService {

    private final TenantProfileProvider tenantProfileProvider;
    private final NotificationRuleProcessor notificationRuleProcessor;

    public DefaultRateLimitService(TenantProfileProvider tenantProfileProvider,
                                   @Lazy NotificationRuleProcessor notificationRuleProcessor,
                                   @Value("${cache.rateLimits.timeToLiveInMinutes:120}") int rateLimitsTtl,
                                   @Value("${cache.rateLimits.maxSize:200000}") int rateLimitsCacheMaxSize) {
        this.tenantProfileProvider = tenantProfileProvider;
        this.notificationRuleProcessor = notificationRuleProcessor;
        this.rateLimits = Caffeine.newBuilder()
                .expireAfterAccess(rateLimitsTtl, TimeUnit.MINUTES)
                .maximumSize(rateLimitsCacheMaxSize)
                .build();
    }

    private final Cache<RateLimitKey, TbRateLimits> rateLimits;

    @Override
    public boolean checkRateLimit(LimitedApi api, TenantId tenantId) {
        return checkRateLimit(api, tenantId, tenantId);
    }

    @Override
    public boolean checkRateLimit(LimitedApi api, TenantId tenantId, Object level) {
        return checkRateLimit(api, tenantId, level, false);
    }

    @Override
    public boolean checkRateLimit(LimitedApi api, TenantId tenantId, Object level, boolean ignoreTenantNotFound) {
        if (tenantId.isSysTenantId()) {
            return true;
        }
        TenantProfile tenantProfile = tenantProfileProvider.get(tenantId);
        if (tenantProfile == null) {
            if (ignoreTenantNotFound) {
                return true;
            } else {
                throw new TenantProfileNotFoundException(tenantId);
            }
        }

        String rateLimitConfig = tenantProfile.getProfileConfiguration()
                .map(api::getLimitConfig).orElse(null);
        boolean success = checkRateLimit(api, level, rateLimitConfig);
        if (!success) {
            notificationRuleProcessor.process(RateLimitsTrigger.builder()
                    .tenantId(tenantId)
                    .api(api)
                    .limitLevel(level instanceof EntityId ? (EntityId) level : tenantId)
                    .limitLevelEntityName(null)
                    .build());
        }
        return success;
    }

    @Override
    public boolean checkRateLimit(LimitedApi api, Object level, String rateLimitConfig) {
        RateLimitKey key = new RateLimitKey(api, level);
        if (StringUtils.isEmpty(rateLimitConfig)) {
            rateLimits.invalidate(key);
            return true;
        }
        log.trace("[{}] Checking rate limit for {} ({})", level, api, rateLimitConfig);

        TbRateLimits rateLimit = rateLimits.asMap().compute(key, (k, limit) -> {
            if (limit == null || !limit.getConfiguration().equals(rateLimitConfig)) {
                limit = new TbRateLimits(rateLimitConfig, api.isRefillRateLimitIntervally());
                log.trace("[{}] Created new rate limit bucket for {} ({})", level, api, rateLimitConfig);
            }
            return limit;
        });
        boolean success = rateLimit.tryConsume();
        if (!success) {
            log.debug("[{}] Rate limit exceeded for {} ({})", level, api, rateLimitConfig);
        }
        return success;
    }

    @Override
    public void cleanUp(LimitedApi api, Object level) {
        RateLimitKey key = new RateLimitKey(api, level);
        rateLimits.invalidate(key);
    }

    @Data(staticConstructor = "of")
    private static class RateLimitKey {
        private final LimitedApi api;
        private final Object level;
    }

}
