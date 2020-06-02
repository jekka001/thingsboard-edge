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
package org.thingsboard.server.dao.sqlts.psql;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.dao.model.sqlts.ts.TsKvEntity;
import org.thingsboard.server.dao.sqlts.AbstractChunkedAggregationTimeseriesDao;
import org.thingsboard.server.dao.sqlts.insert.psql.PsqlPartitioningRepository;
import org.thingsboard.server.dao.timeseries.PsqlPartition;
import org.thingsboard.server.dao.timeseries.SqlTsPartitionDate;
import org.thingsboard.server.dao.util.PsqlDao;
import org.thingsboard.server.dao.util.SqlTsDao;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;


@Component
@Slf4j
@SqlTsDao
@PsqlDao
public class JpaPsqlTimeseriesDao extends AbstractChunkedAggregationTimeseriesDao {

    private final Map<Long, PsqlPartition> partitions = new ConcurrentHashMap<>();
    private static final ReentrantLock partitionCreationLock = new ReentrantLock();

    @Autowired
    private PsqlPartitioningRepository partitioningRepository;

    private SqlTsPartitionDate tsFormat;

    @Value("${sql.postgres.ts_key_value_partitioning:MONTHS}")
    private String partitioning;

    @Override
    protected void init() {
        super.init();
        Optional<SqlTsPartitionDate> partition = SqlTsPartitionDate.parse(partitioning);
        if (partition.isPresent()) {
            tsFormat = partition.get();
        } else {
            log.warn("Incorrect configuration of partitioning {}", partitioning);
            throw new RuntimeException("Failed to parse partitioning property: " + partitioning + "!");
        }
    }

    @Override
    public ListenableFuture<Void> save(TenantId tenantId, EntityId entityId, TsKvEntry tsKvEntry, long ttl) {
        savePartitionIfNotExist(tsKvEntry.getTs());
        String strKey = tsKvEntry.getKey();
        Integer keyId = getOrSaveKeyId(strKey);
        TsKvEntity entity = new TsKvEntity();
        entity.setEntityId(entityId.getId());
        entity.setTs(tsKvEntry.getTs());
        entity.setKey(keyId);
        entity.setStrValue(tsKvEntry.getStrValue().orElse(null));
        entity.setDoubleValue(tsKvEntry.getDoubleValue().orElse(null));
        entity.setLongValue(tsKvEntry.getLongValue().orElse(null));
        entity.setBooleanValue(tsKvEntry.getBooleanValue().orElse(null));
        entity.setJsonValue(tsKvEntry.getJsonValue().orElse(null));
        log.trace("Saving entity: {}", entity);
        return tsQueue.add(entity);
    }

    private void savePartitionIfNotExist(long ts) {
        if (!tsFormat.equals(SqlTsPartitionDate.INDEFINITE) && ts >= 0) {
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC);
            LocalDateTime localDateTimeStart = tsFormat.trancateTo(time);
            long partitionStartTs = toMills(localDateTimeStart);
            if (partitions.get(partitionStartTs) == null) {
                LocalDateTime localDateTimeEnd = tsFormat.plusTo(localDateTimeStart);
                long partitionEndTs = toMills(localDateTimeEnd);
                ZonedDateTime zonedDateTime = localDateTimeStart.atZone(ZoneOffset.UTC);
                String partitionDate = zonedDateTime.format(DateTimeFormatter.ofPattern(tsFormat.getPattern()));
                savePartition(new PsqlPartition(partitionStartTs, partitionEndTs, partitionDate));
            }
        }
    }

    private void savePartition(PsqlPartition psqlPartition) {
        if (!partitions.containsKey(psqlPartition.getStart())) {
            partitionCreationLock.lock();
            try {
                log.trace("Saving partition: {}", psqlPartition);
                partitioningRepository.save(psqlPartition);
                log.trace("Adding partition to Set: {}", psqlPartition);
                partitions.put(psqlPartition.getStart(), psqlPartition);
            } finally {
                partitionCreationLock.unlock();
            }
        }
    }

    private static long toMills(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
