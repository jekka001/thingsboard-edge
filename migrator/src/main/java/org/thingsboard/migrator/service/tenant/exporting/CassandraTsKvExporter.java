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
package org.thingsboard.migrator.service.tenant.exporting;

import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.thingsboard.migrator.MigrationService;
import org.thingsboard.migrator.Table;
import org.thingsboard.migrator.utils.CassandraService;
import org.thingsboard.migrator.utils.Storage;

import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${mode}' == 'TENANT_DATA_EXPORT' and ${export.cassandra.enabled} == true")
@Order(2)
public class CassandraTsKvExporter extends MigrationService {

    private final Storage storage;
    private final CassandraService cassandraService;

    public static final String TS_KV_TABLE = "ts_kv_cf";
    public static final String TS_KV_PARTITIONS_TABLE = "ts_kv_partitions_cf";
    public static final String TS_KV_FILE = "ts_kv";

    private Writer writer;

    @Override
    protected void start() throws Exception {
        storage.newFile(TS_KV_FILE);
        writer = storage.newWriter(TS_KV_FILE);

        storage.readAndProcess(Table.LATEST_KV.getName(), latestKvRow -> {
            executor.submit(() -> {
                try {
                    getTsHistoryAndSave(latestKvRow);
                } catch (Exception e) {
                    log.error("Failed to retrieve timeseries history for {}", latestKvRow, e);
                }
            });
        });
    }

    private void getTsHistoryAndSave(Map<String, Object> latestKvRow) {
        String entityType = (String) latestKvRow.get("table_name");
        UUID entityId = (UUID) latestKvRow.get("entity_id");
        String key = (String) latestKvRow.get("key_name");

        List<Long> partitions = cassandraService.query("SELECT partition FROM " + TS_KV_PARTITIONS_TABLE + " " +
                "WHERE entity_type = ? AND entity_id = ? AND key = ?", Long.class, entityType, entityId, key);
        for (Long partition : partitions) {
            String query = "SELECT * FROM " + TS_KV_TABLE + " WHERE entity_type = ? AND entity_id = ? AND key = ? " +
                    "AND partition = ? ORDER BY ts";
            ResultSet rows = cassandraService.query(query, entityType, entityId, key, partition);
            for (Row row : rows) {
                Map<String, Object> data = new HashMap<>();
                for (ColumnDefinition columnDefinition : row.getColumnDefinitions()) {
                    String column = columnDefinition.getName().toString();
                    Object value = row.getObject(columnDefinition.getName());
                    if (column.endsWith("_v") && value == null) {
                        continue;
                    }
                    data.put(column, value);
                }
                storage.addToFile(writer, data);
                reportProcessed(TS_KV_TABLE, data);
            }
        }
    }

    @Override
    protected void afterFinished() throws Exception {
        finishedProcessing(TS_KV_TABLE);
        writer.close();
    }

}
