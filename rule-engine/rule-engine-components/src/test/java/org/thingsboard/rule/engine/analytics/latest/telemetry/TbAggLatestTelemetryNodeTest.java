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
package org.thingsboard.rule.engine.analytics.latest.telemetry;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.AbstractRuleNodeUpgradeTest;
import org.thingsboard.rule.engine.analytics.incoming.MathFunction;
import org.thingsboard.rule.engine.analytics.latest.ParentEntitiesRelationsQuery;
import org.thingsboard.rule.engine.api.ScriptEngine;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.TbPeContext;
import org.thingsboard.rule.engine.data.RelationsQuery;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationsQuery;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.RelationEntityTypeFilter;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.relation.RelationsSearchParameters;
import org.thingsboard.server.common.data.script.ScriptLanguage;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Slf4j
public class TbAggLatestTelemetryNodeTest extends AbstractRuleNodeUpgradeTest {

    private final Gson gson = new Gson();

    @Mock
    private TbContext ctx;
    @Mock
    private TbPeContext peCtx;
    @Mock
    private ListeningExecutor executor;
    @Mock
    private RelationService relationService;
    @Mock
    private TimeseriesService timeseriesService;
    @Mock
    private ScriptEngine scriptEngine;

    private TbAggLatestTelemetryNode node;
    private TbNodeConfiguration nodeConfiguration;

    private RelationsQuery relationsQuery;
    private EntityId rootEntityId;

    private Map<EntityId, Double> expectedAvgTempMap;
    private Map<EntityId, Integer> expectedDeviceCountMap;

    private int scheduleCount = 0;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void init() {
        TbAggLatestTelemetryNodeConfiguration config = new TbAggLatestTelemetryNodeConfiguration();
        node = spy(new TbAggLatestTelemetryNode());

        lenient().doAnswer((Answer<TbMsg>) invocationOnMock -> {
            TbMsgType type = (TbMsgType) (invocationOnMock.getArguments())[1];
            EntityId originator = (EntityId) (invocationOnMock.getArguments())[2];
            TbMsgMetaData metaData = (TbMsgMetaData) (invocationOnMock.getArguments())[3];
            String data = (String) (invocationOnMock.getArguments())[4];
            return TbMsg.newMsg()
                    .type(type)
                    .originator(originator)
                    .metaData(metaData.copy().copy())
                    .data(data)
                    .build();
        }).when(ctx).newMsg(ArgumentMatchers.isNull(), ArgumentMatchers.any(TbMsgType.class), ArgumentMatchers.nullable(EntityId.class),
                ArgumentMatchers.any(TbMsgMetaData.class), ArgumentMatchers.any(String.class));

        scheduleCount = 0;

        lenient().doAnswer((Answer<Void>) invocationOnMock -> {
            scheduleCount++;
            if (scheduleCount == 1) {
                TbMsg msg = (TbMsg) (invocationOnMock.getArguments())[0];
                node.onMsg(ctx, msg);
            }
            return null;
        }).when(ctx).tellSelf(ArgumentMatchers.any(TbMsg.class), ArgumentMatchers.anyLong());

        lenient().when(ctx.getPeContext()).thenReturn(peCtx);

        lenient().when(peCtx.isLocalEntity(ArgumentMatchers.any(EntityId.class))).thenReturn(true);

        lenient().when(ctx.getDbCallbackExecutor()).thenReturn(executor);

        Stubber executorAnswer = lenient().doAnswer(invocationOnMock -> {
            try {
                Object arg = (invocationOnMock.getArguments())[0];
                Object result = null;
                if (arg instanceof Callable) {
                    Callable task = (Callable) arg;
                    result = task.call();
                } else if (arg instanceof Runnable) {
                    Runnable task = (Runnable) arg;
                    task.run();
                }
                return Futures.immediateFuture(result);
            } catch (Throwable th) {
                return Futures.immediateFailedFuture(th);
            }
        });

        executorAnswer.when(executor).execute(ArgumentMatchers.any(Runnable.class));

        lenient().when(ctx.getRelationService()).thenReturn(relationService);
        lenient().when(ctx.getTimeseriesService()).thenReturn(timeseriesService);

        String attributesFilterScript = "return Number(attributes['temperature']) > 21;";

        lenient().when(peCtx.createAttributesScriptEngine(ScriptLanguage.JS, attributesFilterScript)).thenReturn(scriptEngine);

        lenient().when(scriptEngine.executeAttributesFilterAsync(ArgumentMatchers.anyMap())).then(
                (Answer<ListenableFuture<Boolean>>) invocation -> {
                    Map<String, KvEntry> attributes = (Map<String, KvEntry>) (invocation.getArguments())[0];
                    if (attributes.containsKey("temperature")) {
                        String temperature = attributes.get("temperature").getValueAsString();
                        try {
                            return Futures.immediateFuture(Double.parseDouble(temperature) > 21);
                        } catch (NumberFormatException e) {
                            return Futures.immediateFuture(false);
                        }
                    }
                    return Futures.immediateFuture(false);
                }
        );

        relationsQuery = new RelationsQuery();
        relationsQuery.setDirection(EntitySearchDirection.FROM);
        relationsQuery.setMaxLevel(1);
        RelationEntityTypeFilter entityTypeFilter = new RelationEntityTypeFilter(EntityRelation.CONTAINS_TYPE, Collections.emptyList());
        relationsQuery.setFilters(Collections.singletonList(entityTypeFilter));

        rootEntityId = new TenantId(Uuids.timeBased());

        ParentEntitiesRelationsQuery parentEntitiesRelationsQuery = new ParentEntitiesRelationsQuery();
        parentEntitiesRelationsQuery.setRootEntityId(rootEntityId);

        parentEntitiesRelationsQuery.setRelationsQuery(relationsQuery);
        parentEntitiesRelationsQuery.setChildRelationsQuery(relationsQuery);

        config.setParentEntitiesQuery(parentEntitiesRelationsQuery);

        List<AggLatestMapping> aggMappings = new ArrayList<>();

        AggLatestMapping avgTempMapping = new AggLatestMapping();
        avgTempMapping.setSource("temperature");
        avgTempMapping.setSourceScope("LATEST_TELEMETRY");
        avgTempMapping.setAggFunction(MathFunction.AVG);
        avgTempMapping.setDefaultValue(0);
        avgTempMapping.setTarget("latestAvgTemperature");
        aggMappings.add(avgTempMapping);

        AggLatestMapping countMapping = new AggLatestMapping();
        countMapping.setAggFunction(MathFunction.COUNT);
        countMapping.setTarget("deviceCount");

        AggLatestMappingFilter filter = new AggLatestMappingFilter();
        filter.setLatestTsKeyNames(Collections.singletonList("temperature"));
        filter.setScriptLang(ScriptLanguage.JS);
        filter.setFilterFunction("return Number(attributes['temperature']) > 21;");

        countMapping.setFilter(filter);

        aggMappings.add(countMapping);

        config.setAggMappings(aggMappings);

        config.setPeriodTimeUnit(TimeUnit.MILLISECONDS);
        config.setPeriodValue(0);
        config.setOutMsgType(TbMsgType.POST_TELEMETRY_REQUEST.name());

        nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));
        expectedAvgTempMap = new HashMap<>();
        expectedDeviceCountMap = new HashMap<>();
    }

    @Test
    public void parentEntitiesByRelationQueryAttributesAggregated() throws TbNodeException {

        List<EntityRelation> parentEntityRelations = new ArrayList<>();

        int parentCount = 10 + (int) (Math.random() * 20);

        for (int i = 0; i < parentCount; i++) {
            EntityId parentEntityId = new AssetId(Uuids.timeBased());
            parentEntityRelations.add(createEntityRelation(rootEntityId, parentEntityId));

            List<EntityRelation> childRelations = new ArrayList<>();
            int childCount = 10 + (int) (Math.random() * 20);

            BigDecimal sum = BigDecimal.ZERO;

            int expectedDeviceCount = 0;

            for (int c = 0; c < childCount; c++) {
                EntityId childEntityId = new DeviceId(Uuids.timeBased());
                childRelations.add(createEntityRelation(parentEntityId, childEntityId));

                TsKvEntry kvEntry = null;
                if (Math.random() > 0.5) {
                    double temperature = 17 + Math.random() * 10;
                    sum = sum.add(BigDecimal.valueOf(temperature));
                    kvEntry = new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry("temperature", "" + temperature));
                    if (temperature > 21) {
                        expectedDeviceCount++;
                    }
                }
                when(timeseriesService.findLatest(ArgumentMatchers.any(), ArgumentMatchers.eq(childEntityId), ArgumentMatchers.eq(Collections.singletonList("temperature")))).thenReturn(
                        Futures.immediateFuture(kvEntry != null ? Collections.singletonList(kvEntry) : Collections.emptyList())
                );

                Map<String, String> attributes = new HashMap<>();
                if (kvEntry != null) {
                    attributes.put("temperature", kvEntry.getValueAsString());
                }

            }

            expectedDeviceCountMap.put(parentEntityId, expectedDeviceCount);

            expectedAvgTempMap.put(parentEntityId,
                    sum.divide(BigDecimal.valueOf(childCount), 2, RoundingMode.HALF_UP).doubleValue());

            when(relationService.findByQuery(ArgumentMatchers.any(), ArgumentMatchers.eq(buildQuery(parentEntityId, relationsQuery)))).thenReturn(Futures.immediateFuture(childRelations));
        }

        when(relationService.findByQuery(ArgumentMatchers.any(), ArgumentMatchers.eq(buildQuery(rootEntityId, relationsQuery)))).thenReturn(Futures.immediateFuture(parentEntityRelations));

        node.init(ctx, nodeConfiguration);

        ArgumentCaptor<TbMsg> captor = ArgumentCaptor.forClass(TbMsg.class);
        verify(ctx, new Times(parentCount * 2)).enqueueForTellNext(captor.capture(), eq(TbNodeConnectionType.SUCCESS));

        List<TbMsg> messages = captor.getAllValues();
        for (TbMsg msg : messages) {
            verifyMessage(msg);
        }
    }

    @Test
    public void someFailedOtherAggregated() throws TbNodeException {
        List<EntityRelation> parentEntityRelations = new ArrayList<>();

        int parentCount = 10 + (int) (Math.random() * 20);

        int successAvgTempCount = 0;

        Map<EntityId, String> invalidValueMap = new HashMap<>();

        for (int i = 0; i < parentCount; i++) {
            EntityId parentEntityId = new AssetId(Uuids.timeBased());
            parentEntityRelations.add(createEntityRelation(rootEntityId, parentEntityId));

            List<EntityRelation> childRelations = new ArrayList<>();
            int childCount = 10 + (int) (Math.random() * 20);

            BigDecimal sum = BigDecimal.ZERO;

            int expectedDeviceCount = 0;

            int failedChildIndex = -1;
            boolean shouldFail = Math.random() > 0.5;
            if (!shouldFail) {
                successAvgTempCount++;
            } else {
                failedChildIndex = (int) Math.floor(Math.random() * childCount);
            }

            for (int c = 0; c < childCount; c++) {
                EntityId childEntityId = new DeviceId(Uuids.timeBased());
                childRelations.add(createEntityRelation(parentEntityId, childEntityId));
                double temperature = 17 + Math.random() * 10;

                sum = sum.add(BigDecimal.valueOf(temperature));

                boolean setInvalidTemperature = failedChildIndex == c;

                String temperatureString = (setInvalidTemperature ? "invalid" : "") + temperature;
                if (setInvalidTemperature) {
                    invalidValueMap.put(parentEntityId, temperatureString);
                } else if (temperature > 21) {
                    expectedDeviceCount++;
                }

                TsKvEntry kvEntry = new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry("temperature", temperatureString));
                when(timeseriesService.findLatest(ArgumentMatchers.any(), ArgumentMatchers.eq(childEntityId), ArgumentMatchers.eq(Collections.singletonList("temperature")))).thenReturn(
                        Futures.immediateFuture(Collections.singletonList(kvEntry))
                );

            }

            expectedDeviceCountMap.put(parentEntityId, expectedDeviceCount);

            expectedAvgTempMap.put(parentEntityId,
                    sum.divide(BigDecimal.valueOf(childCount), 2, RoundingMode.HALF_UP).doubleValue());

            when(relationService.findByQuery(ArgumentMatchers.any(), ArgumentMatchers.eq(buildQuery(parentEntityId, relationsQuery)))).thenReturn(Futures.immediateFuture(childRelations));
        }

        when(relationService.findByQuery(ArgumentMatchers.any(), ArgumentMatchers.eq(buildQuery(rootEntityId, relationsQuery)))).thenReturn(Futures.immediateFuture(parentEntityRelations));

        node.init(ctx, nodeConfiguration);

        int successMsgCount = parentCount + successAvgTempCount;

        ArgumentCaptor<TbMsg> captor = ArgumentCaptor.forClass(TbMsg.class);
        verify(ctx, new Times(successMsgCount)).enqueueForTellNext(captor.capture(), eq(TbNodeConnectionType.SUCCESS));

        List<TbMsg> messages = captor.getAllValues();
        for (TbMsg msg : messages) {
            verifyMessage(msg);
        }

        int failedMsgCount = parentCount - successAvgTempCount;

        if (failedMsgCount > 0) {
            ArgumentCaptor<TbMsg> failureMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
            ArgumentCaptor<String> throwableCaptor = ArgumentCaptor.forClass(String.class);

            verify(ctx, new Times(failedMsgCount)).enqueueForTellFailure(failureMsgCaptor.capture(), throwableCaptor.capture());

            List<TbMsg> failedMessages = failureMsgCaptor.getAllValues();
            List<String> throwables = throwableCaptor.getAllValues();
            for (int i = 0; i < failedMessages.size(); i++) {
                TbMsg failedMsg = failedMessages.get(i);
                String t = throwables.get(i);
                Assertions.assertTrue(t.startsWith("Aggregation failed. Unable to parse value"));
                String invalidValue = invalidValueMap.get(failedMsg.getOriginator());
                Assertions.assertNotNull(invalidValue);
                Assertions.assertTrue(t.contains(invalidValue));
            }
        }
    }

    // Rule nodes upgrade
    public static final String EXPECTED_CONFIG = "{\"parentEntitiesQuery\":{\"type\":\"group\",\"entityGroupId\":null}," +
            "\"periodTimeUnit\":\"MINUTES\",\"periodValue\":5," +
            "\"aggMappings\":[{\"source\":\"temperature\",\"sourceScope\":\"LATEST_TELEMETRY\"," +
            "\"defaultValue\":0.0,\"target\":\"latestAvgTemperature\",\"aggFunction\":\"AVG\",\"filter\":null}],\"outMsgType\":\"POST_TELEMETRY_REQUEST\"}";

    private static Stream<Arguments> givenFromVersionAndConfig_whenUpgrade_thenVerifyHasChangesAndConfig() {
        return Stream.of(
                // default config for version 0
                Arguments.of(0,
                        "{\"parentEntitiesQuery\":{\"type\":\"group\",\"entityGroupId\":null}," +
                                "\"periodTimeUnit\":\"MINUTES\",\"periodValue\":5," +
                                "\"aggMappings\":[{\"source\":\"temperature\",\"sourceScope\":\"LATEST_TELEMETRY\"," +
                                "\"defaultValue\":0.0,\"target\":\"latestAvgTemperature\",\"aggFunction\":\"AVG\",\"filter\":null}],\"queueName\":null}",
                        true,
                        EXPECTED_CONFIG),
                // default config for version 0 with queueName
                Arguments.of(0,
                        "{\"parentEntitiesQuery\":{\"type\":\"group\",\"entityGroupId\":null}," +
                                "\"periodTimeUnit\":\"MINUTES\",\"periodValue\":5," +
                                "\"aggMappings\":[{\"source\":\"temperature\",\"sourceScope\":\"LATEST_TELEMETRY\"," +
                                "\"defaultValue\":0.0,\"target\":\"latestAvgTemperature\",\"aggFunction\":\"AVG\",\"filter\":null}],\"queueName\":\"Main\"}",
                        true,
                        EXPECTED_CONFIG),
                // default config for version 1 with upgrade from version 1
                Arguments.of(1,
                        "{\"parentEntitiesQuery\":{\"type\":\"group\",\"entityGroupId\":null}," +
                                "\"periodTimeUnit\":\"MINUTES\",\"periodValue\":5," +
                                "\"aggMappings\":[{\"source\":\"temperature\",\"sourceScope\":\"LATEST_TELEMETRY\"," +
                                "\"defaultValue\":0.0,\"target\":\"latestAvgTemperature\",\"aggFunction\":\"AVG\",\"filter\":null}],\"queueName\":\"Main\",\"outMsgType\":\"POST_TELEMETRY_REQUEST\"}",
                        true,
                        EXPECTED_CONFIG),
                // default config for version 2 with upgrade from version 0
                Arguments.of(0, EXPECTED_CONFIG, false, EXPECTED_CONFIG)
        );
    }

    private void verifyMessage(TbMsg msg) {
        Assertions.assertTrue(msg.isTypeOf(TbMsgType.POST_TELEMETRY_REQUEST));
        EntityId entityId = msg.getOriginator();
        Assertions.assertNotNull(entityId);
        String data = msg.getData();
        Assertions.assertNotNull(data);
        JsonObject dataJson = gson.fromJson(data, JsonObject.class);

        Assertions.assertTrue(dataJson.has("latestAvgTemperature") || dataJson.has("deviceCount"));
        if (dataJson.has("latestAvgTemperature")) {
            JsonElement elem = dataJson.get("latestAvgTemperature");
            Assertions.assertTrue(elem.isJsonPrimitive());
            double doubleVal = elem.getAsDouble();
            Assertions.assertEquals(expectedAvgTempMap.get(entityId).doubleValue(), doubleVal, 0.0);
        }
        if (dataJson.has("deviceCount")) {
            JsonElement elem = dataJson.get("deviceCount");
            Assertions.assertTrue(elem.isJsonPrimitive());
            long longVal = elem.getAsLong();
            Assertions.assertEquals(expectedDeviceCountMap.get(entityId).longValue(), longVal);
        }
    }

    private static EntityRelation createEntityRelation(EntityId from, EntityId to) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(from);
        relation.setTo(to);
        relation.setType(EntityRelation.CONTAINS_TYPE);
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        return relation;
    }

    private static EntityRelationsQuery buildQuery(EntityId originator, RelationsQuery relationsQuery) {
        EntityRelationsQuery query = new EntityRelationsQuery();
        RelationsSearchParameters parameters = new RelationsSearchParameters(originator,
                relationsQuery.getDirection(), relationsQuery.getMaxLevel(), false);
        query.setParameters(parameters);
        query.setFilters(relationsQuery.getFilters());
        return query;
    }

    @Override
    protected TbNode getTestNode() {
        return node;
    }
}
