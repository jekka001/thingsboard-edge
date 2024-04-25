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
package org.thingsboard.migrator.service.tenant.importing;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.thingsboard.migrator.MigrationService;
import org.thingsboard.migrator.Table;
import org.thingsboard.migrator.utils.SqlPartitionService;
import org.thingsboard.migrator.utils.Storage;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${mode}' == 'TENANT_DATA_IMPORT' and ${import.postgres.enabled} == true")
@Order(1)
@Slf4j
public class PostgresTenantDataImporter extends MigrationService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Storage storage;
    private final SqlPartitionService partitionService;

    @Value("${skipped_tables}")
    private Set<Table> skippedTables;
    @Value("${import.postgres.delay_between_queries}")
    private int delayBetweenQueries;

    @Value("${import.postgres.enable_partition_creation}")
    private boolean enablePartitionCreation;
    @Value("${import.postgres.update_tenant_profile}")
    private boolean updateTenantProfile;
    @Value("${import.postgres.update_ts_kv_dictionary}")
    private boolean updateTsKvDictionary;
    @Value("${import.postgres.resolve_unknown_roles}")
    private boolean resolveUnknownRoles;

    private final Map<Table, Map<String, String>> columns = new HashMap<>();

    @Override
    protected void start() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            for (Table table : Table.values()) {
                if (skippedTables.contains(table)) {
                    continue;
                }
                importTableData(table);
            }
        });
    }

    @SneakyThrows
    private void importTableData(Table table) {
        storage.readAndProcess(table.getName(), row -> {
            saveRow(table, row);
        });
        finishedProcessing(table.getName());
    }

    private void saveRow(Table table, Map<String, Object> row) {
        row = prepareRow(table, row);
        if (table.isPartitioned() && enablePartitionCreation) {
            partitionService.createPartition(table, row);
        }

        String columnsStatement = "";
        String valuesStatement = "";
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String column = entry.getKey();
            Object value = entry.getValue();

            if (!columnsStatement.isEmpty()) {
                columnsStatement += ",";
            }
            columnsStatement += column;

            if (!valuesStatement.isEmpty()) {
                valuesStatement += ",";
            }
            valuesStatement += "?";
            if (value instanceof JsonNode) {
                entry.setValue(value.toString());
            } else if (value instanceof String[]) {
                try {
                    entry.setValue(jdbcTemplate.getDataSource().getConnection().createArrayOf("text", (String[]) value));
                    continue;
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
            String valueType = columns.get(table).get(column);
            valuesStatement += "::" + valueType;
        }

        String query = format("INSERT INTO %s (%s) VALUES (%s)", table.getName(), columnsStatement, valuesStatement);
        jdbcTemplate.update(query, row.values().toArray());
        reportProcessed(table.getName(), row);
        try {
            TimeUnit.MILLISECONDS.sleep(delayBetweenQueries);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> prepareRow(Table table, Map<String, Object> row) {
        if (table == Table.TENANT) {
            if (updateTenantProfile) {
                UUID defaultTenantProfile = jdbcTemplate.queryForList("SELECT id FROM tenant_profile WHERE is_default = TRUE", UUID.class).get(0);
                row = new LinkedHashMap<>(row);
                row.put("tenant_profile_id", defaultTenantProfile);
            }
        } else if (table == Table.LATEST_KV) {
            String keyName = (String) row.remove("key_name");
            if (updateTsKvDictionary) {
                Integer keyId = jdbcTemplate.queryForList("SELECT key_id FROM ts_kv_dictionary WHERE key = ?", Integer.class, keyName).stream().findFirst().orElse(null);
                if (keyId == null) {
                    jdbcTemplate.update("INSERT INTO ts_kv_dictionary (key) VALUES (?)", keyName);
                    keyId = jdbcTemplate.queryForObject("SELECT key_id FROM ts_kv_dictionary WHERE key = ?", Integer.class, keyName);
                }
                Object oldKey = row.put("key", keyId);
            }
        } else if (table == Table.GROUP_PERMISSION) {
            UUID roleId = (UUID) row.get("role_id");
            String roleName = (String) row.remove("role_name");
            Boolean roleExists = jdbcTemplate.queryForObject("SELECT EXISTS (SELECT * FROM role WHERE id = ?)", Boolean.class, roleId);
            if (!roleExists) {
                if (!resolveUnknownRoles) {
                    throw new IllegalArgumentException("Role with id " + roleId + " not found");
                }
                // happens when system role is used (e.g. 'Tenant Administrators')
                log.info("Role for id {} does not exist. Finding by name {}", roleId, roleName);
                Map<String, Object> role = jdbcTemplate.queryForList("SELECT * FROM role WHERE name = ?", roleName).stream()
                        .findFirst().orElse(null);
                if (role == null) {
                    throw new IllegalArgumentException("Role not found for name " + roleName);
                }
                row.put("role_id", role.get("id"));
            }
        }

        Map<String, String> existingColumns = columns.computeIfAbsent(table, t -> {
            return jdbcTemplate.queryForList("SELECT column_name, udt_name FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name = '" + table.getName() + "'").stream()
                    .collect(Collectors.toMap(vals -> vals.get("column_name").toString(), vals -> vals.get("udt_name").toString()));
        });
        row.keySet().removeIf(column -> {
            if (column.equals("table_name")) {
                return true;
            }
            boolean unknownColumn = !existingColumns.containsKey(column);
            if (unknownColumn) {
                log.warn("Skipping unknown column {} for table {}", column, table.getName());
            }
            return unknownColumn;
        });
        return row;
    }

}
