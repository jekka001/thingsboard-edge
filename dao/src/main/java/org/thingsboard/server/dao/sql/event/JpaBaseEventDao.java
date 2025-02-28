/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.sql.event;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.ListenableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.event.DebugConverterEventFilter;
import org.thingsboard.server.common.data.event.DebugIntegrationEventFilter;
import org.thingsboard.server.common.data.event.ErrorEventFilter;
import org.thingsboard.server.common.data.event.Event;
import org.thingsboard.server.common.data.event.EventFilter;
import org.thingsboard.server.common.data.event.EventType;
import org.thingsboard.server.common.data.event.LifeCycleEventFilter;
import org.thingsboard.server.common.data.event.RawDataEventFilter;
import org.thingsboard.server.common.data.event.RuleChainDebugEventFilter;
import org.thingsboard.server.common.data.event.RuleNodeDebugEventFilter;
import org.thingsboard.server.common.data.event.StatisticsEventFilter;
import org.thingsboard.server.common.data.id.EventId;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.stats.StatsFactory;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.config.DefaultDataSource;
import org.thingsboard.server.dao.event.EventDao;
import org.thingsboard.server.dao.model.sql.EventEntity;
import org.thingsboard.server.dao.sql.ScheduledLogExecutorComponent;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueParams;
import org.thingsboard.server.dao.sql.TbSqlBlockingQueueWrapper;
import org.thingsboard.server.dao.sqlts.insert.sql.SqlPartitioningRepository;
import org.thingsboard.server.dao.util.SqlDao;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@DefaultDataSource
@Component
@SqlDao
@RequiredArgsConstructor
@Slf4j
public class JpaBaseEventDao implements EventDao {

    private final EventPartitionConfiguration partitionConfiguration;
    private final SqlPartitioningRepository partitioningRepository;
    private final LifecycleEventRepository lcEventRepository;
    private final StatisticsEventRepository statsEventRepository;
    private final ErrorEventRepository errorEventRepository;
    private final EventInsertRepository eventInsertRepository;
    private final RuleNodeDebugEventRepository ruleNodeDebugEventRepository;
    private final RuleChainDebugEventRepository ruleChainDebugEventRepository;
    private final RawEventRepository rawEventRepository;
    private final IntegrationDebugEventRepository integrationDebugEventRepository;
    private final ConverterDebugEventRepository converterDebugEventRepository;
    private final ScheduledLogExecutorComponent logExecutor;
    private final StatsFactory statsFactory;

    @Value("${sql.events.batch_size:10000}")
    private int batchSize;

    @Value("${sql.events.batch_max_delay:100}")
    private long maxDelay;

    @Value("${sql.events.stats_print_interval_ms:10000}")
    private long statsPrintIntervalMs;

    @Value("${sql.events.batch_threads:3}")
    private int batchThreads;

    @Value("${sql.batch_sort:true}")
    private boolean batchSortEnabled;

    private TbSqlBlockingQueueWrapper<Event, Void> queue;

    private final Map<EventType, EventRepository<?, ?>> repositories = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        TbSqlBlockingQueueParams params = TbSqlBlockingQueueParams.builder()
                .logName("Events")
                .batchSize(batchSize)
                .maxDelay(maxDelay)
                .statsPrintIntervalMs(statsPrintIntervalMs)
                .statsNamePrefix("events")
                .batchSortEnabled(batchSortEnabled)
                .build();
        Function<Event, Integer> hashcodeFunction = entity -> Objects.hash(super.hashCode(), entity.getTenantId(), entity.getEntityId());
        queue = new TbSqlBlockingQueueWrapper<>(params, hashcodeFunction, batchThreads, statsFactory);
        queue.init(logExecutor, v -> eventInsertRepository.save(v), Comparator.comparing(Event::getCreatedTime));
        repositories.put(EventType.LC_EVENT, lcEventRepository);
        repositories.put(EventType.STATS, statsEventRepository);
        repositories.put(EventType.ERROR, errorEventRepository);
        repositories.put(EventType.RAW_DATA, rawEventRepository);
        repositories.put(EventType.DEBUG_RULE_NODE, ruleNodeDebugEventRepository);
        repositories.put(EventType.DEBUG_RULE_CHAIN, ruleChainDebugEventRepository);
        repositories.put(EventType.DEBUG_CONVERTER, converterDebugEventRepository);
        repositories.put(EventType.DEBUG_INTEGRATION, integrationDebugEventRepository);

    }

    @PreDestroy
    private void destroy() {
        if (queue != null) {
            queue.destroy();
        }
    }

    @Override
    public ListenableFuture<Void> saveAsync(Event event) {
        log.debug("Save event [{}] ", event);
        if (event.getId() == null) {
            UUID timeBased = Uuids.timeBased();
            event.setId(new EventId(timeBased));
            event.setCreatedTime(Uuids.unixTimestamp(timeBased));
        } else if (event.getCreatedTime() == 0L) {
            UUID eventId = event.getId().getId();
            if (eventId.version() == 1) {
                event.setCreatedTime(Uuids.unixTimestamp(eventId));
            } else {
                event.setCreatedTime(System.currentTimeMillis());
            }
        }
        partitioningRepository.createPartitionIfNotExists(event.getType().getTable(), event.getCreatedTime(),
                partitionConfiguration.getPartitionSizeInMs(event.getType()));
        return queue.add(event);
    }

    @Override
    public PageData<? extends Event> findEvents(UUID tenantId, UUID entityId, EventType eventType, TimePageLink pageLink) {
        return DaoUtil.toPageData(getEventRepository(eventType).findEvents(tenantId, entityId, pageLink.getStartTime(), pageLink.getEndTime(), DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap)));
    }

    @Override
    public PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, EventFilter eventFilter, TimePageLink pageLink) {
        if (eventFilter.isNotEmpty()) {
            switch (eventFilter.getEventType()) {
                case DEBUG_RULE_NODE:
                    return findEventByFilter(tenantId, entityId, (RuleNodeDebugEventFilter) eventFilter, pageLink);
                case DEBUG_RULE_CHAIN:
                    return findEventByFilter(tenantId, entityId, (RuleChainDebugEventFilter) eventFilter, pageLink);
                case DEBUG_INTEGRATION:
                    return findEventByFilter(tenantId, entityId, (DebugIntegrationEventFilter) eventFilter, pageLink);
                case DEBUG_CONVERTER:
                    return findEventByFilter(tenantId, entityId, (DebugConverterEventFilter) eventFilter, pageLink);
                case LC_EVENT:
                    return findEventByFilter(tenantId, entityId, (LifeCycleEventFilter) eventFilter, pageLink);
                case ERROR:
                    return findEventByFilter(tenantId, entityId, (ErrorEventFilter) eventFilter, pageLink);
                case STATS:
                    return findEventByFilter(tenantId, entityId, (StatisticsEventFilter) eventFilter, pageLink);
                case RAW_DATA:
                    return findEventByFilter(tenantId, entityId, (RawDataEventFilter) eventFilter, pageLink);
                default:
                    throw new RuntimeException("Not supported event type: " + eventFilter.getEventType());
            }
        } else {
            return findEvents(tenantId, entityId, eventFilter.getEventType(), pageLink);
        }
    }

    @Override
    public void removeEvents(UUID tenantId, UUID entityId, Long startTime, Long endTime) {
        log.debug("[{}][{}] Remove events [{}-{}] ", tenantId, entityId, startTime, endTime);
        for (EventType eventType : EventType.values()) {
            getEventRepository(eventType).removeEvents(tenantId, entityId, startTime, endTime);
        }
    }

    @Override
    public void removeEvents(UUID tenantId, UUID entityId, EventFilter eventFilter, Long startTime, Long endTime) {
        if (eventFilter.isNotEmpty()) {
            switch (eventFilter.getEventType()) {
                case DEBUG_RULE_NODE:
                    removeEventsByFilter(tenantId, entityId, (RuleNodeDebugEventFilter) eventFilter, startTime, endTime);
                    break;
                case DEBUG_RULE_CHAIN:
                    removeEventsByFilter(tenantId, entityId, (RuleChainDebugEventFilter) eventFilter, startTime, endTime);
                    break;
                case DEBUG_INTEGRATION:
                    removeEventsByFilter(tenantId, entityId, (DebugIntegrationEventFilter) eventFilter, startTime, endTime);
                    break;
                case DEBUG_CONVERTER:
                    removeEventsByFilter(tenantId, entityId, (DebugConverterEventFilter) eventFilter, startTime, endTime);
                    break;
                case LC_EVENT:
                    removeEventsByFilter(tenantId, entityId, (LifeCycleEventFilter) eventFilter, startTime, endTime);
                    break;
                case ERROR:
                    removeEventsByFilter(tenantId, entityId, (ErrorEventFilter) eventFilter, startTime, endTime);
                    break;
                case STATS:
                    removeEventsByFilter(tenantId, entityId, (StatisticsEventFilter) eventFilter, startTime, endTime);
                    break;
                case RAW_DATA:
                    removeEventsByFilter(tenantId, entityId, (RawDataEventFilter) eventFilter, startTime, endTime);
                    break;
                default:
                    throw new RuntimeException("Not supported event type: " + eventFilter.getEventType());
            }
        } else {
            getEventRepository(eventFilter.getEventType()).removeEvents(tenantId, entityId, startTime, endTime);
        }
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, RuleChainDebugEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                ruleChainDebugEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getMessage(),
                        eventFilter.isError(),
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap)));
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, RuleNodeDebugEventFilter eventFilter, TimePageLink pageLink) {
        parseUUID(eventFilter.getEntityId(), "Entity Id");
        parseUUID(eventFilter.getMsgId(), "Message Id");
        return DaoUtil.toPageData(
                ruleNodeDebugEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getMsgDirectionType(),
                        StringUtils.isBlank(eventFilter.getEntityId()) ? null : UUID.fromString(eventFilter.getEntityId()),
                        eventFilter.getEntityType(),
                        StringUtils.isBlank(eventFilter.getMsgId()) ? null : UUID.fromString(eventFilter.getMsgId()),
                        eventFilter.getMsgType(),
                        eventFilter.getRelationType(),
                        eventFilter.getDataSearch(),
                        eventFilter.getMetadataSearch(),
                        eventFilter.isError(),
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap)));
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, DebugIntegrationEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                integrationDebugEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getType(),
                        eventFilter.getMessage(),
                        eventFilter.getStatusIntegration(),
                        eventFilter.isError(),
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap)));
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, DebugConverterEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                converterDebugEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getType(),
                        eventFilter.getIn(),
                        eventFilter.getOut(),
                        eventFilter.getMetadata(),
                        eventFilter.isError(),
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap)));
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, ErrorEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                errorEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getMethod(),
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap))
        );
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, LifeCycleEventFilter eventFilter, TimePageLink pageLink) {
        boolean statusFilterEnabled = !StringUtils.isEmpty(eventFilter.getStatus());
        boolean statusFilter = statusFilterEnabled && eventFilter.getStatus().equalsIgnoreCase(TbNodeConnectionType.SUCCESS);
        return DaoUtil.toPageData(
                lcEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getEvent(),
                        statusFilterEnabled,
                        statusFilter,
                        eventFilter.getErrorStr(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap))
        );
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, StatisticsEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                statsEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getMinMessagesProcessed(),
                        eventFilter.getMaxMessagesProcessed(),
                        eventFilter.getMinErrorsOccurred(),
                        eventFilter.getMaxErrorsOccurred(),
                        DaoUtil.toPageable(pageLink, EventEntity.eventColumnMap))
        );
    }

    private PageData<? extends Event> findEventByFilter(UUID tenantId, UUID entityId, RawDataEventFilter eventFilter, TimePageLink pageLink) {
        return DaoUtil.toPageData(
                rawEventRepository.findEvents(
                        tenantId,
                        entityId,
                        pageLink.getStartTime(),
                        pageLink.getEndTime(),
                        eventFilter.getServer(),
                        eventFilter.getUuid(),
                        eventFilter.getMessageType(),
                        eventFilter.getMessage(),
                        DaoUtil.toPageable(pageLink)));
    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, RuleChainDebugEventFilter eventFilter, Long startTime, Long endTime) {
        ruleChainDebugEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getMessage(),
                eventFilter.isError(),
                eventFilter.getErrorStr());
    }


    private void removeEventsByFilter(UUID tenantId, UUID entityId, DebugConverterEventFilter eventFilter, Long startTime, Long endTime) {
        converterDebugEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getType(),
                eventFilter.getIn(),
                eventFilter.getOut(),
                eventFilter.getMetadata(),
                eventFilter.isError(),
                eventFilter.getErrorStr());
    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, DebugIntegrationEventFilter eventFilter, Long startTime, Long endTime) {
        integrationDebugEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getType(),
                eventFilter.getMessage(),
                eventFilter.getStatusIntegration(),
                eventFilter.isError(),
                eventFilter.getErrorStr());
    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, RuleNodeDebugEventFilter eventFilter, Long startTime, Long endTime) {
        parseUUID(eventFilter.getEntityId(), "Entity Id");
        parseUUID(eventFilter.getMsgId(), "Message Id");
        ruleNodeDebugEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getMsgDirectionType(),
                eventFilter.getEntityId(),
                eventFilter.getEntityType(),
                eventFilter.getMsgId(),
                eventFilter.getMsgType(),
                eventFilter.getRelationType(),
                eventFilter.getDataSearch(),
                eventFilter.getMetadataSearch(),
                eventFilter.isError(),
                eventFilter.getErrorStr());
    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, ErrorEventFilter eventFilter, Long startTime, Long endTime) {
        errorEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getMethod(),
                eventFilter.getErrorStr());

    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, LifeCycleEventFilter eventFilter, Long startTime, Long endTime) {
        boolean statusFilterEnabled = !StringUtils.isEmpty(eventFilter.getStatus());
        boolean statusFilter = statusFilterEnabled && eventFilter.getStatus().equalsIgnoreCase(TbNodeConnectionType.SUCCESS);
        lcEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getEvent(),
                statusFilterEnabled,
                statusFilter,
                eventFilter.getErrorStr());
    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, StatisticsEventFilter eventFilter, Long startTime, Long endTime) {
        statsEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getMinMessagesProcessed(),
                eventFilter.getMaxMessagesProcessed(),
                eventFilter.getMinErrorsOccurred(),
                eventFilter.getMaxErrorsOccurred()
        );
    }

    private void removeEventsByFilter(UUID tenantId, UUID entityId, RawDataEventFilter eventFilter, Long startTime, Long endTime) {
        rawEventRepository.removeEvents(
                tenantId,
                entityId,
                startTime,
                endTime,
                eventFilter.getServer(),
                eventFilter.getUuid(),
                eventFilter.getMessageType(),
                eventFilter.getMessage()
        );
    }

    @Override
    public List<? extends Event> findLatestEvents(UUID tenantId, UUID entityId, EventType eventType, int limit) {
        return DaoUtil.convertDataList(getEventRepository(eventType).findLatestEvents(tenantId, entityId, limit));
    }

    @Override
    public Event findLatestDebugRuleNodeInEvent(UUID tenantId, UUID entityId) {
        return DaoUtil.getData(ruleNodeDebugEventRepository.findLatestDebugRuleNodeInEvent(tenantId, entityId));
    }

    @Override
    public void cleanupEvents(long regularEventExpTs, long debugEventExpTs, boolean cleanupDb) {
        if (regularEventExpTs > 0) {
            log.info("Going to cleanup regular events with exp time: {}", regularEventExpTs);
            if (cleanupDb) {
                cleanupEvents(regularEventExpTs, false);
            } else {
                cleanupPartitionsCache(regularEventExpTs, false);
            }
        }
        if (debugEventExpTs > 0) {
            log.info("Going to cleanup debug events with exp time: {}", debugEventExpTs);
            if (cleanupDb) {
                cleanupEvents(debugEventExpTs, true);
            } else {
                cleanupPartitionsCache(debugEventExpTs, true);
            }
        }
    }

    private void cleanupEvents(long eventExpTime, boolean debug) {
        for (EventType eventType : EventType.values()) {
            if (eventType.isDebug() == debug) {
                cleanupPartitions(eventType, eventExpTime);
            }
        }
    }

    private void cleanupPartitions(EventType eventType, long eventExpTime) {
        partitioningRepository.dropPartitionsBefore(eventType.getTable(), eventExpTime, partitionConfiguration.getPartitionSizeInMs(eventType));
    }

    private void cleanupPartitionsCache(long expTime, boolean isDebug) {
        for (EventType eventType : EventType.values()) {
            if (eventType.isDebug() == isDebug) {
                partitioningRepository.cleanupPartitionsCache(eventType.getTable(), expTime, partitionConfiguration.getPartitionSizeInMs(eventType));
            }
        }
    }

    private void parseUUID(String src, String paramName) {
        if (!StringUtils.isEmpty(src)) {
            try {
                UUID.fromString(src);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Failed to convert " + paramName + " to UUID!");
            }
        }
    }

    private EventRepository<? extends EventEntity<?>, ?> getEventRepository(EventType eventType) {
        var repository = repositories.get(eventType);
        if (repository == null) {
            throw new RuntimeException("Event type: " + eventType + " is not supported!");
        }
        return repository;
    }


}
