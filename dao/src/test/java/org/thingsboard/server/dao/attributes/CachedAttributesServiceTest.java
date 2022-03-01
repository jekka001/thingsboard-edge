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
package org.thingsboard.server.dao.attributes;

import com.google.common.util.concurrent.MoreExecutors;
import org.junit.Test;
import org.thingsboard.server.dao.cache.CacheExecutorService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;

public class CachedAttributesServiceTest {

    public static final String REDIS = "redis";

    @Test
    public void givenLocalCacheTypeName_whenEquals_thenOK() {
        assertThat(CachedAttributesService.LOCAL_CACHE_TYPE, is("caffeine"));
    }

    @Test
    public void givenCacheType_whenGetExecutor_thenDirectExecutor() {
        CachedAttributesService cachedAttributesService = mock(CachedAttributesService.class);
        CacheExecutorService cacheExecutorService = mock(CacheExecutorService.class);
        willCallRealMethod().given(cachedAttributesService).getExecutor(any(), any());

        assertThat(cachedAttributesService.getExecutor(null, cacheExecutorService), is(MoreExecutors.directExecutor()));

        assertThat(cachedAttributesService.getExecutor("", cacheExecutorService), is(MoreExecutors.directExecutor()));

        assertThat(cachedAttributesService.getExecutor(CachedAttributesService.LOCAL_CACHE_TYPE, cacheExecutorService), is(MoreExecutors.directExecutor()));

    }

    @Test
    public void givenCacheType_whenGetExecutor_thenReturnCacheExecutorService() {
        CachedAttributesService cachedAttributesService = mock(CachedAttributesService.class);
        CacheExecutorService cacheExecutorService = mock(CacheExecutorService.class);
        willCallRealMethod().given(cachedAttributesService).getExecutor(any(String.class), any(CacheExecutorService.class));

        assertThat(cachedAttributesService.getExecutor(REDIS, cacheExecutorService), is(cacheExecutorService));

        assertThat(cachedAttributesService.getExecutor("unknownCacheType", cacheExecutorService), is(cacheExecutorService));

    }

}