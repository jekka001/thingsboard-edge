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
package org.thingsboard.server.service.apiusage;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.msg.tools.TbRateLimits;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class DefaultRateLimitService implements RateLimitService {

    private final TbTenantProfileCache tenantProfileCache;

    private final Map<String, Map<TenantId, TbRateLimits>> rateLimits = new ConcurrentHashMap<>();

    @Override
    public boolean checkEntityExportLimit(TenantId tenantId) {
        return checkLimit(tenantId, "entityExport", DefaultTenantProfileConfiguration::getTenantEntityExportRateLimit);
    }

    @Override
    public boolean checkEntityImportLimit(TenantId tenantId) {
        return checkLimit(tenantId, "entityImport", DefaultTenantProfileConfiguration::getTenantEntityImportRateLimit);
    }

    private boolean checkLimit(TenantId tenantId, String rateLimitsKey, Function<DefaultTenantProfileConfiguration, String> rateLimitConfigExtractor) {
        String rateLimitConfig = tenantProfileCache.get(tenantId).getProfileConfiguration()
                .map(rateLimitConfigExtractor).orElse(null);

        Map<TenantId, TbRateLimits> rateLimits = this.rateLimits.get(rateLimitsKey);
        if (StringUtils.isEmpty(rateLimitConfig)) {
            if (rateLimits != null) {
                rateLimits.remove(tenantId);
                if (rateLimits.isEmpty()) {
                    this.rateLimits.remove(rateLimitsKey);
                }
            }
            return true;
        }

        if (rateLimits == null) {
            rateLimits = new ConcurrentHashMap<>();
            this.rateLimits.put(rateLimitsKey, rateLimits);
        }
        TbRateLimits rateLimit = rateLimits.get(tenantId);
        if (rateLimit == null || !rateLimit.getConfiguration().equals(rateLimitConfig)) {
            rateLimit = new TbRateLimits(rateLimitConfig);
            rateLimits.put(tenantId, rateLimit);
        }

        return rateLimit.tryConsume();
    }

}
