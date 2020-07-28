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
package org.thingsboard.server.dao;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.util.CollectionUtils;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.dao.model.ToData;
import org.thingsboard.server.dao.sql.JpaExecutorService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.UUIDConverter.fromTimeUUIDs;

public abstract class DaoUtil {
    private static final int MAX_IN_VALUE = Short.MAX_VALUE / 2;

    private DaoUtil() {
    }

    public static <T> List<T> convertDataList(Collection<? extends ToData<T>> toDataList) {
        List<T> list = Collections.emptyList();
        if (toDataList != null && !toDataList.isEmpty()) {
            list = new ArrayList<>();
            for (ToData<T> object : toDataList) {
                if (object != null) {
                    list.add(object.toData());
                }
            }
        }
        return list;
    }

    public static <T> T getData(ToData<T> data) {
        T object = null;
        if (data != null) {
            object = data.toData();
        }
        return object;
    }

    public static <T> T getData(Optional<? extends ToData<T>> data) {
        T object = null;
        if (data.isPresent()) {
            object = data.get().toData();
        }
        return object;
    }

    public static UUID getId(UUIDBased idBased) {
        UUID id = null;
        if (idBased != null) {
            id = idBased.getId();
        }
        return id;
    }

    public static List<UUID> toUUIDs(List<? extends UUIDBased> idBasedIds) {
        List<UUID> ids = new ArrayList<>();
        for (UUIDBased idBased : idBasedIds) {
            ids.add(getId(idBased));
        }
        return ids;
    }

    public static <T> ListenableFuture<List<T>> getEntitiesByTenantIdAndIdIn(List<UUID> entityIds,
                                                                             Function<List<String>, Collection<? extends ToData<T>>> daoConsumer,
                                                                             JpaExecutorService service) {
        int size = entityIds.size();
        List<ListenableFuture<List<T>>> resultList = new ArrayList<>();
        if (size > MAX_IN_VALUE) {
            int startIndex = 0;
            int currentSize = 0;
            while (startIndex + currentSize < size) {
                startIndex += currentSize;
                currentSize = Math.min(size - startIndex, MAX_IN_VALUE);

                List<UUID> currentEntityIds = entityIds.subList(startIndex, startIndex + currentSize);
                resultList.add(service.submit(() -> convertDataList(daoConsumer.apply(fromTimeUUIDs(currentEntityIds)))));
            }
            return Futures.transform(Futures.allAsList(resultList), list -> {
                if (!CollectionUtils.isEmpty(list)) {
                    return list.stream().flatMap(List::stream).collect(Collectors.toList());
                }

                return Collections.emptyList();
            }, service);

        } else {
            return service.submit(() -> convertDataList(daoConsumer.apply(fromTimeUUIDs(entityIds))));
        }
    }
}
