/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.importing;

import com.google.common.util.concurrent.FutureCallback;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.DataType;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.controller.BaseController;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;
import org.thingsboard.server.service.action.EntityActionService;
import org.thingsboard.server.service.importing.BulkImportRequest.ColumnMapping;
import org.thingsboard.server.service.security.AccessValidator;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.AccessControlService;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;
import org.thingsboard.server.utils.CsvUtils;
import org.thingsboard.server.utils.TypeCastUtil;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
public abstract class AbstractBulkImportService<E extends BaseData<? extends EntityId>> {
    protected final TelemetrySubscriptionService tsSubscriptionService;
    protected final TbTenantProfileCache tenantProfileCache;
    protected final AccessControlService accessControlService;
    protected final AccessValidator accessValidator;
    protected final EntityActionService entityActionService;
    protected final TbClusterService clusterService;

    private static ThreadPoolExecutor executor;

    @PostConstruct
    private void initExecutor() {
        if (executor == null) {
            executor = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
                    60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(150_000),
                    ThingsBoardThreadFactory.forName("bulk-import"), new ThreadPoolExecutor.CallerRunsPolicy());
            executor.allowCoreThreadTimeOut(true);
        }
    }

    public final BulkImportResult<E> processBulkImport(BulkImportRequest request, SecurityUser user, Consumer<ImportedEntityInfo<E>> onEntityImported, BiConsumer<E, UnaryOperator<E>> entityGroupAssigner) throws Exception {
        List<EntityData> entitiesData = parseData(request);

        BulkImportResult<E> result = new BulkImportResult<>();
        CountDownLatch completionLatch = new CountDownLatch(entitiesData.size());

        entitiesData.forEach(entityData -> DonAsynchron.submit(() -> {
                    ImportedEntityInfo<E> importedEntityInfo = new ImportedEntityInfo<>();
                    E entity = findOrCreateAndSetFields(request, entityData.getFields(), importedEntityInfo, user);

                    UnaryOperator<E> savingFunction = e -> {
                        E savedEntity = saveEntity(request, entityData.getFields(), e, user);
                        importedEntityInfo.setEntity(savedEntity);
                        return savedEntity;
                    };

                    if (entityGroupAssigner != null) {
                        entityGroupAssigner.accept(entity, savingFunction);
                    } else {
                        savingFunction.apply(entity);
                    }

                    onEntityImported.accept(importedEntityInfo);

                    E savedEntity = importedEntityInfo.getEntity();
                    saveKvs(user, savedEntity, entityData.getKvs());

                    return importedEntityInfo;
                },
                importedEntityInfo -> {
                    if (importedEntityInfo.isUpdated()) {
                        result.getUpdated().incrementAndGet();
                    } else {
                        result.getCreated().incrementAndGet();
                    }
                    completionLatch.countDown();
                },
                throwable -> {
                    result.getErrors().incrementAndGet();
                    result.getErrorsList().add(String.format("Line %d: %s", entityData.getLineNumber(), ExceptionUtils.getRootCauseMessage(throwable)));
                    completionLatch.countDown();
                },
                executor));

        completionLatch.await();
        return result;
    }

    protected abstract E saveEntity(BulkImportRequest importRequest, Map<BulkImportColumnType, String> fields, E entity, SecurityUser user);

    protected abstract E findOrCreateAndSetFields(BulkImportRequest request, Map<BulkImportColumnType, String> fields, ImportedEntityInfo<E> importedEntityInfo, SecurityUser user);

    /*
     * Attributes' values are firstly added to JsonObject in order to then make some type cast,
     * because we get all values as strings from CSV
     * */
    private void saveKvs(SecurityUser user, E entity, Map<ColumnMapping, ParsedValue> data) {
        Arrays.stream(BulkImportColumnType.values())
                .filter(BulkImportColumnType::isKv)
                .map(kvType -> {
                    JsonObject kvs = new JsonObject();
                    data.entrySet().stream()
                            .filter(dataEntry -> dataEntry.getKey().getType() == kvType &&
                                    StringUtils.isNotEmpty(dataEntry.getKey().getKey()))
                            .forEach(dataEntry -> kvs.add(dataEntry.getKey().getKey(), dataEntry.getValue().toJsonPrimitive()));
                    return Map.entry(kvType, kvs);
                })
                .filter(kvsEntry -> kvsEntry.getValue().entrySet().size() > 0)
                .forEach(kvsEntry -> {
                    BulkImportColumnType kvType = kvsEntry.getKey();
                    if (kvType == BulkImportColumnType.SHARED_ATTRIBUTE || kvType == BulkImportColumnType.SERVER_ATTRIBUTE) {
                        saveAttributes(user, entity, kvsEntry, kvType);
                    } else {
                        saveTelemetry(user, entity, kvsEntry);
                    }
                });
    }

    @SneakyThrows
    private void saveTelemetry(SecurityUser user, E entity, Map.Entry<BulkImportColumnType, JsonObject> kvsEntry) {
        List<TsKvEntry> timeseries = JsonConverter.convertToTelemetry(kvsEntry.getValue(), System.currentTimeMillis())
                .entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(kvEntry -> new BasicTsKvEntry(entry.getKey(), kvEntry)))
                .collect(Collectors.toList());

        accessValidator.validateEntityAndCallback(user, Operation.WRITE_TELEMETRY, entity.getId(), (result, tenantId, entityId) -> {
            TenantProfile tenantProfile = tenantProfileCache.get(tenantId);
            long tenantTtl = TimeUnit.DAYS.toSeconds(((DefaultTenantProfileConfiguration) tenantProfile.getProfileData().getConfiguration()).getDefaultStorageTtlDays());
            tsSubscriptionService.saveAndNotify(tenantId, user.getCustomerId(), entityId, timeseries, tenantTtl, new FutureCallback<Void>() {
                @Override
                public void onSuccess(@Nullable Void tmp) {
                    entityActionService.logEntityAction(user, (UUIDBased & EntityId) entityId, null, null,
                            ActionType.TIMESERIES_UPDATED, null, timeseries);
                }

                @Override
                public void onFailure(Throwable t) {
                    entityActionService.logEntityAction(user, (UUIDBased & EntityId) entityId, null, null,
                            ActionType.TIMESERIES_UPDATED, BaseController.toException(t), timeseries);
                    throw new RuntimeException(t);
                }
            });
        });
    }

    @SneakyThrows
    private void saveAttributes(SecurityUser user, E entity, Map.Entry<BulkImportColumnType, JsonObject> kvsEntry, BulkImportColumnType kvType) {
        String scope = kvType.getKey();
        List<AttributeKvEntry> attributes = new ArrayList<>(JsonConverter.convertToAttributes(kvsEntry.getValue()));

        accessValidator.validateEntityAndCallback(user, Operation.WRITE_ATTRIBUTES, entity.getId(), (result, tenantId, entityId) -> {
            tsSubscriptionService.saveAndNotify(tenantId, entityId, scope, attributes, new FutureCallback<>() {

                @Override
                public void onSuccess(Void unused) {
                    entityActionService.logEntityAction(user, (UUIDBased & EntityId) entityId, null,
                            null, ActionType.ATTRIBUTES_UPDATED, null, scope, attributes);
                }

                @Override
                public void onFailure(Throwable throwable) {
                    entityActionService.logEntityAction(user, (UUIDBased & EntityId) entityId, null,
                            null, ActionType.ATTRIBUTES_UPDATED, BaseController.toException(throwable),
                            scope, attributes);
                    throw new RuntimeException(throwable);
                }

            });
        });
    }

    private List<EntityData> parseData(BulkImportRequest request) throws Exception {
        List<List<String>> records = CsvUtils.parseCsv(request.getFile(), request.getMapping().getDelimiter());
        AtomicInteger linesCounter = new AtomicInteger(0);

        if (request.getMapping().getHeader()) {
            records.remove(0);
            linesCounter.incrementAndGet();
        }

        List<ColumnMapping> columnsMappings = request.getMapping().getColumns();
        return records.stream()
                .map(record -> {
                    EntityData entityData = new EntityData();
                    Stream.iterate(0, i -> i < record.size(), i -> i + 1)
                            .map(i -> Map.entry(columnsMappings.get(i), record.get(i)))
                            .filter(entry -> StringUtils.isNotEmpty(entry.getValue()))
                            .forEach(entry -> {
                                if (!entry.getKey().getType().isKv()) {
                                    entityData.getFields().put(entry.getKey().getType(), entry.getValue());
                                } else {
                                    Map.Entry<DataType, Object> castResult = TypeCastUtil.castValue(entry.getValue());
                                    entityData.getKvs().put(entry.getKey(), new ParsedValue(castResult.getValue(), castResult.getKey()));
                                }
                            });
                    entityData.setLineNumber(linesCounter.incrementAndGet());
                    return entityData;
                })
                .collect(Collectors.toList());
    }

    @PreDestroy
    private void shutdownExecutor() {
        if (!executor.isTerminating()) {
            executor.shutdown();
        }
    }

    @Data
    protected static class EntityData {
        private final Map<BulkImportColumnType, String> fields = new LinkedHashMap<>();
        private final Map<ColumnMapping, ParsedValue> kvs = new LinkedHashMap<>();
        private int lineNumber;
    }

    @Data
    protected static class ParsedValue {
        private final Object value;
        private final DataType dataType;

        public JsonPrimitive toJsonPrimitive() {
            switch (dataType) {
                case STRING:
                    return new JsonPrimitive((String) value);
                case LONG:
                    return new JsonPrimitive((Long) value);
                case DOUBLE:
                    return new JsonPrimitive((Double) value);
                case BOOLEAN:
                    return new JsonPrimitive((Boolean) value);
                default:
                    return null;
            }
        }

        public String stringValue() {
            return value.toString();
        }

    }

}
