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
package org.thingsboard.server.dao.sql.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.thingsboard.server.common.data.event.ConverterDebugEvent;
import org.thingsboard.server.common.data.event.ErrorEvent;
import org.thingsboard.server.common.data.event.Event;
import org.thingsboard.server.common.data.event.EventType;
import org.thingsboard.server.common.data.event.IntegrationDebugEvent;
import org.thingsboard.server.common.data.event.LifecycleEvent;
import org.thingsboard.server.common.data.event.RawDataEvent;
import org.thingsboard.server.common.data.event.RuleChainDebugEvent;
import org.thingsboard.server.common.data.event.RuleNodeDebugEvent;
import org.thingsboard.server.common.data.event.StatisticsEvent;
import org.thingsboard.server.dao.util.SqlDao;

import javax.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@Transactional
@SqlDao
public class EventInsertRepository {

    private static final ThreadLocal<Pattern> PATTERN_THREAD_LOCAL = ThreadLocal.withInitial(() -> Pattern.compile(String.valueOf(Character.MIN_VALUE)));

    private static final String EMPTY_STR = "";

    private final Map<EventType, String> insertStmtMap = new ConcurrentHashMap<>();

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Value("${sql.remove_null_chars:true}")
    private boolean removeNullChars;

    @PostConstruct
    public void init() {
        insertStmtMap.put(EventType.ERROR, "INSERT INTO " + EventType.ERROR.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_method, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.LC_EVENT, "INSERT INTO " + EventType.LC_EVENT.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_type, e_success, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.STATS, "INSERT INTO " + EventType.STATS.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_messages_processed, e_errors_occurred) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.RAW_DATA, "INSERT INTO " + EventType.RAW_DATA.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_uuid, e_message_type, e_message) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.DEBUG_RULE_NODE, "INSERT INTO " + EventType.DEBUG_RULE_NODE.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_type, e_entity_id, e_entity_type, e_msg_id, e_msg_type, e_data_type, e_relation_type, e_data, e_metadata, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.DEBUG_RULE_CHAIN, "INSERT INTO " + EventType.DEBUG_RULE_CHAIN.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_message, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.DEBUG_CONVERTER, "INSERT INTO " + EventType.DEBUG_CONVERTER.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_type, e_in_message_type, e_in_message, e_out_message_type, e_out_message, e_metadata, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
        insertStmtMap.put(EventType.DEBUG_INTEGRATION, "INSERT INTO " + EventType.DEBUG_INTEGRATION.getTable() +
                " (id, tenant_id, ts, entity_id, service_id, e_type, e_message, e_message_type, e_status, e_error) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING;");
    }

    public void save(List<Event> entities) {
        Map<EventType, List<Event>> eventsByType = entities.stream().collect(Collectors.groupingBy(Event::getType, Collectors.toList()));
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                for (var entry : eventsByType.entrySet()) {
                    jdbcTemplate.batchUpdate(insertStmtMap.get(entry.getKey()), getStatementSetter(entry.getKey(), entry.getValue()));
                }
            }
        });
    }

    private BatchPreparedStatementSetter getStatementSetter(EventType eventType, List<Event> events) {
        switch (eventType) {
            case ERROR:
                return getErrorEventSetter(events);
            case LC_EVENT:
                return getLcEventSetter(events);
            case STATS:
                return getStatsEventSetter(events);
            case RAW_DATA:
                return getRawDataEventSetter(events);
            case DEBUG_RULE_NODE:
                return getRuleNodeEventSetter(events);
            case DEBUG_RULE_CHAIN:
                return getRuleChainEventSetter(events);
            case DEBUG_CONVERTER:
                return getConverterEventSetter(events);
            case DEBUG_INTEGRATION:
                return getIntegrationEventSetter(events);
            default:
                throw new RuntimeException(eventType + " support is not implemented!");
        }
    }

    private BatchPreparedStatementSetter getErrorEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ErrorEvent event = (ErrorEvent) events.get(i);
                setCommonEventFields(ps, event);
                safePutString(ps, 6, event.getMethod());
                safePutString(ps, 7, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getLcEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                LifecycleEvent event = (LifecycleEvent) events.get(i);
                setCommonEventFields(ps, event);
                safePutString(ps, 6, event.getLcEventType());
                ps.setBoolean(7, event.isSuccess());
                safePutString(ps, 8, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getStatsEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                StatisticsEvent event = (StatisticsEvent) events.get(i);
                setCommonEventFields(ps, event);
                ps.setLong(6, event.getMessagesProcessed());
                ps.setLong(7, event.getErrorsOccurred());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getRawDataEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                RawDataEvent event = (RawDataEvent) events.get(i);
                setCommonEventFields(ps, event);
                ps.setString(6, event.getUuid());
                ps.setString(7, event.getMessageType());
                ps.setString(8, event.getMessage());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getConverterEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ConverterDebugEvent event = (ConverterDebugEvent) events.get(i);
                setCommonEventFields(ps, event);
                ps.setString(6, event.getEventType());
                ps.setString(7, event.getInMsgType());
                ps.setString(8, event.getInMsg());
                ps.setString(9, event.getOutMsgType());
                ps.setString(10, event.getOutMsg());
                ps.setString(11, event.getMetadata());
                ps.setString(12, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getIntegrationEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                IntegrationDebugEvent event = (IntegrationDebugEvent) events.get(i);
                setCommonEventFields(ps, event); // e_type, e_message, e_status, e_error
                ps.setString(6, event.getEventType());
                ps.setString(7, event.getMessage());
                ps.setString(8, event.getMessageType());
                ps.setString(9, event.getStatus());
                ps.setString(10, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getRuleNodeEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                RuleNodeDebugEvent event = (RuleNodeDebugEvent) events.get(i);
                setCommonEventFields(ps, event);
                safePutString(ps, 6, event.getEventType());
                safePutUUID(ps, 7, event.getEventEntity() != null ? event.getEventEntity().getId() : null);
                safePutString(ps, 8, event.getEventEntity() != null ? event.getEventEntity().getEntityType().name() : null);
                safePutUUID(ps, 9, event.getMsgId());
                safePutString(ps, 10, event.getMsgType());
                safePutString(ps, 11, event.getDataType());
                safePutString(ps, 12, event.getRelationType());
                safePutString(ps, 13, event.getData());
                safePutString(ps, 14, event.getMetadata());
                safePutString(ps, 15, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    private BatchPreparedStatementSetter getRuleChainEventSetter(List<Event> events) {
        return new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                RuleChainDebugEvent event = (RuleChainDebugEvent) events.get(i);
                setCommonEventFields(ps, event);
                safePutString(ps, 6, event.getMessage());
                safePutString(ps, 7, event.getError());
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        };
    }

    void safePutString(PreparedStatement ps, int parameterIdx, String value) throws SQLException {
        if (value != null) {
            ps.setString(parameterIdx, replaceNullChars(value));
        } else {
            ps.setNull(parameterIdx, Types.VARCHAR);
        }
    }

    void safePutUUID(PreparedStatement ps, int parameterIdx, UUID value) throws SQLException {
        if (value != null) {
            ps.setObject(parameterIdx, value);
        } else {
            ps.setNull(parameterIdx, Types.OTHER);
        }
    }

    private void setCommonEventFields(PreparedStatement ps, Event event) throws SQLException {
        ps.setObject(1, event.getId().getId());
        ps.setObject(2, event.getTenantId().getId());
        ps.setLong(3, event.getCreatedTime());
        ps.setObject(4, event.getEntityId());
        ps.setString(5, event.getServiceId());
    }

    private String replaceNullChars(String strValue) {
        if (removeNullChars && strValue != null) {
            return PATTERN_THREAD_LOCAL.get().matcher(strValue).replaceAll(EMPTY_STR);
        }
        return strValue;
    }
}
