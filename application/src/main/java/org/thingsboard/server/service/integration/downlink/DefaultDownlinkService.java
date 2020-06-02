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
package org.thingsboard.server.service.integration.downlink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.thingsboard.integration.api.data.DownLinkMsg;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.IntegrationId;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.thingsboard.server.common.data.CacheConstants.DOWNLINK_CACHE;

/**
 * Created by ashvayka on 22.02.18.
 */
@Service
@Slf4j
public class DefaultDownlinkService implements DownlinkService {

    @Autowired
    private CacheManager cacheManager;

    @Cacheable(cacheNames = DOWNLINK_CACHE, key = "{#integrationId, #entityId}")
    @Override
    public DownLinkMsg get(IntegrationId integrationId, EntityId entityId) {
        return null;
    }

    @Override
    public DownLinkMsg put(IntegrationDownlinkMsg msg) {
        return getAndMerge(msg, DownLinkMsg::from, DownLinkMsg::merge);
    }

    @CacheEvict(cacheNames = DOWNLINK_CACHE, key = "{#integrationId, #entityId}")
    @Override
    public void remove(IntegrationId integrationId, EntityId entityId) {

    }

    private <T extends IntegrationDownlinkMsg> DownLinkMsg getAndMerge(T msg, Function<T, DownLinkMsg> from, BiFunction<DownLinkMsg, T, DownLinkMsg> merge) {
        Cache cache = cacheManager.getCache(DOWNLINK_CACHE);
        List<Object> key = new ArrayList<>();
        key.add(msg.getIntegrationId());
        key.add(msg.getEntityId());

        DownLinkMsg result = cache.get(key, DownLinkMsg.class);

        if (result == null) {
            result = from.apply(msg);
        } else {
            result = merge.apply(result, msg);
        }

        cache.put(key, result);
        return result;
    }
}
