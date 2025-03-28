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
package org.thingsboard.migrator.service.latest_kv.exporting;

import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.thingsboard.migrator.MigrationService;
import org.thingsboard.migrator.utils.CassandraService;
import org.thingsboard.migrator.utils.Storage;

import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${mode}' == 'CASSANDRA_LATEST_KV_EXPORT'")
public class CassandraLatestKvExporter extends MigrationService {

    private final CassandraService cassandraService;
    private final Storage storage;

    private static final String LATEST_KV_TABLE = "ts_kv_latest_cf";
    public static final String LATEST_KV_FILE = "latest_kv";

    @Override
    protected void start() throws Exception {
        storage.newFile(LATEST_KV_FILE);
        try (Writer writer = storage.newWriter(LATEST_KV_FILE)) {
            String query = "SELECT * FROM " + LATEST_KV_TABLE;
            ResultSet rows = cassandraService.query(query);
            for (Row row : rows) {
                Map<String, Object> data = new HashMap<>();
                for (ColumnDefinition columnDefinition : row.getColumnDefinitions()) {
                    String column = columnDefinition.getName().toString();
                    Object value = row.getObject(columnDefinition.getName());
                    if (column.endsWith("_v") && value == null) {
                        continue;
                    }
                    if (column.equals("key")) {
                        column = "key_name";
                    }
                    data.put(column, value);
                }
                storage.addToFile(writer, data);
                reportProcessed(LATEST_KV_TABLE, data);
            }
        }
    }

    @Override
    protected void afterFinished() throws Exception {
        finishedProcessing(LATEST_KV_TABLE);
    }

}
