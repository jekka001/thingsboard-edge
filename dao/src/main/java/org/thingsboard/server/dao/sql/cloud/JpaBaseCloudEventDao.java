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
package org.thingsboard.server.dao.sql.cloud;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.id.CloudEventId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.stats.StatsFactory;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.cloud.CloudEventDao;
import org.thingsboard.server.dao.model.sql.CloudEventEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDao;
import org.thingsboard.server.dao.sql.ScheduledLogExecutorComponent;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueParams;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueWrapper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;

import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;

@Slf4j
@Component
public class JpaBaseCloudEventDao extends JpaAbstractDao<CloudEventEntity, CloudEvent> implements CloudEventDao {

    private final UUID systemTenantId = NULL_UUID;

    @Autowired
    ScheduledLogExecutorComponent logExecutor;

    @Autowired
    private StatsFactory statsFactory;

    @Value("${sql.cloud_events.batch_size:10000}")
    private int batchSize;

    @Value("${sql.cloud_events.batch_max_delay:100}")
    private long maxDelay;

    @Value("${sql.cloud_events.stats_print_interval_ms:10000}")
    private long statsPrintIntervalMs;

    @Value("${sql.cloud_events.batch_threads:3}")
    private int batchThreads;

    private TbSqlBlockingQueueWrapper<CloudEventEntity> queue;

    @Autowired
    private CloudEventRepository cloudEventRepository;

    @Autowired
    private CloudEventInsertRepository cloudEventInsertRepository;

    @Autowired
    private CloudEventCleanupRepository cloudEventCleanupRepository;

    @Override
    protected Class<CloudEventEntity> getEntityClass() {
        return CloudEventEntity.class;
    }

    @Override
    protected JpaRepository<CloudEventEntity, UUID> getRepository() {
        return cloudEventRepository;
    }

    @PostConstruct
    private void init() {
        TbSqlBlockingQueueParams params = TbSqlBlockingQueueParams.builder()
                .logName("Cloud Events")
                .batchSize(batchSize)
                .maxDelay(maxDelay)
                .statsPrintIntervalMs(statsPrintIntervalMs)
                .statsNamePrefix("cloud.events")
                .batchSortEnabled(true)
                .build();
        Function<CloudEventEntity, Integer> hashcodeFunction = entity -> {
            if (entity.getEntityId() != null) {
                return entity.getEntityId().hashCode();
            } else {
                return NULL_UUID.hashCode();
            }
        };
        queue = new TbSqlBlockingQueueWrapper<>(params, hashcodeFunction, batchThreads, statsFactory);
        queue.init(logExecutor, v -> cloudEventInsertRepository.save(v),
                Comparator.comparing(CloudEventEntity::getTs)
        );
    }

    @PreDestroy
    private void destroy() {
        if (queue != null) {
            queue.destroy();
        }
    }

    @Override
    public ListenableFuture<Void> saveAsync(CloudEvent cloudEvent) {
        log.debug("Save cloud event [{}] ", cloudEvent);
        if (cloudEvent.getId() == null) {
            UUID timeBased = Uuids.timeBased();
            cloudEvent.setId(new CloudEventId(timeBased));
            cloudEvent.setCreatedTime(Uuids.unixTimestamp(timeBased));
        } else if (cloudEvent.getCreatedTime() == 0L) {
            UUID eventId = cloudEvent.getId().getId();
            if (eventId.version() == 1) {
                cloudEvent.setCreatedTime(Uuids.unixTimestamp(eventId));
            } else {
                cloudEvent.setCreatedTime(System.currentTimeMillis());
            }
        }

        return save(new CloudEventEntity(cloudEvent));
    }

    private ListenableFuture<Void> save(CloudEventEntity entity) {
        log.debug("Save cloud event [{}] ", entity);
        if (entity.getTenantId() == null) {
            log.trace("Save system cloud event with predefined id {}", systemTenantId);
            entity.setTenantId(systemTenantId);
        }
        if (entity.getUuid() == null) {
            entity.setUuid(Uuids.timeBased());
        }

        return addToQueue(entity);
    }

    private ListenableFuture<Void> addToQueue(CloudEventEntity entity) {
        return queue.add(entity);
    }

    @Override
    public PageData<CloudEvent> findCloudEvents(UUID tenantId, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                cloudEventRepository
                        .findEventsByTenantId(
                                tenantId,
                                pageLink.getStartTime(),
                                pageLink.getEndTime(),
                                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public PageData<CloudEvent> findCloudEventsByEntityIdAndCloudEventActionAndCloudEventType(UUID tenantId,
                                                                                              UUID entityId,
                                                                                              CloudEventType cloudEventType,
                                                                                              String cloudEventAction,
                                                                                              TimePageLink pageLink) {
        return DaoUtil.toPageData(
                cloudEventRepository
                        .findEventsByTenantIdAndEntityIdAndCloudEventActionAndCloudEventType(
                                tenantId,
                                entityId,
                                cloudEventType,
                                cloudEventAction,
                                pageLink.getStartTime(),
                                pageLink.getEndTime(),
                                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public void cleanupEvents(long eventsTtl) {
        log.info("Going to cleanup old cloud events using debug events ttl: {}s", eventsTtl);
        cloudEventCleanupRepository.cleanupEvents(eventsTtl);
    }

}
