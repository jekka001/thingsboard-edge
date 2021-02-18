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
package org.thingsboard.rule.engine.analytics.latest.telemetry;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Futures;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.internal.verification.Times;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.Stubber;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.analytics.incoming.MathFunction;
import org.thingsboard.rule.engine.analytics.latest.ParentEntitiesRelationsQuery;
import org.thingsboard.rule.engine.api.ScriptEngine;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.TbPeContext;
import org.thingsboard.rule.engine.data.RelationsQuery;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.EntityRelationsQuery;
import org.thingsboard.server.common.data.relation.EntitySearchDirection;
import org.thingsboard.server.common.data.relation.EntityTypeFilter;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.relation.RelationsSearchParameters;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import javax.script.ScriptException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.thingsboard.rule.engine.api.TbRelationTypes.SUCCESS;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class TbAggLatestTelemetryNodeTest {

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
    private AttributesService attributesService;

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

    @Before
    @SuppressWarnings("unchecked")
    public void init() {

        TbAggLatestTelemetryNodeConfiguration config = new TbAggLatestTelemetryNodeConfiguration();
        node = new TbAggLatestTelemetryNode();

        doAnswer((Answer<TbMsg>) invocationOnMock -> {
            String queueName = (String) (invocationOnMock.getArguments())[0];
            String type = (String) (invocationOnMock.getArguments())[1];
            EntityId originator = (EntityId) (invocationOnMock.getArguments())[2];
            TbMsgMetaData metaData = (TbMsgMetaData) (invocationOnMock.getArguments())[3];
            String data = (String) (invocationOnMock.getArguments())[4];
            return TbMsg.newMsg(type, originator, metaData.copy(), data);
        }).when(ctx).newMsg(ArgumentMatchers.any(String.class), ArgumentMatchers.any(String.class), ArgumentMatchers.nullable(EntityId.class),
                ArgumentMatchers.any(TbMsgMetaData.class), ArgumentMatchers.any(String.class));

        scheduleCount = 0;

        doAnswer((Answer<Void>) invocationOnMock -> {
            scheduleCount++;
            if (scheduleCount == 1) {
                TbMsg msg = (TbMsg) (invocationOnMock.getArguments())[0];
                node.onMsg(ctx, msg);
            }
            return null;
        }).when(ctx).tellSelf(ArgumentMatchers.any(TbMsg.class), ArgumentMatchers.anyLong());

        when(ctx.getPeContext()).thenReturn(peCtx);

        when(peCtx.isLocalEntity(ArgumentMatchers.any(EntityId.class))).thenReturn(true);

        when(ctx.getJsExecutor()).thenReturn(executor);
        when(ctx.getDbCallbackExecutor()).thenReturn(executor);

        Stubber executorAnswer = doAnswer(invocationOnMock -> {
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

        when(ctx.getRelationService()).thenReturn(relationService);
        when(ctx.getTimeseriesService()).thenReturn(timeseriesService);

        String attributesFilterScript = "return Number(attributes['temperature']) > 21;";

        when(peCtx.createAttributesJsScriptEngine(attributesFilterScript)).thenReturn(scriptEngine);

        try {
            when(scriptEngine.executeAttributesFilter(ArgumentMatchers.anyMap())).then(
                    (Answer<Boolean>) invocation -> {
                        Map<String, String> attributes = (Map<String, String>) (invocation.getArguments())[0];
                        if (attributes.containsKey("temperature")) {
                            try {
                                double temperature = Double.parseDouble(attributes.get("temperature"));
                                return temperature > 21;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        }
                        return false;
                    }
            );
        } catch (ScriptException e) {
            log.error("Failed to execute script", e);
        }

        relationsQuery = new RelationsQuery();
        relationsQuery.setDirection(EntitySearchDirection.FROM);
        relationsQuery.setMaxLevel(1);
        EntityTypeFilter entityTypeFilter = new EntityTypeFilter(EntityRelation.CONTAINS_TYPE, Collections.emptyList());
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
        filter.setFilterFunction("return Number(attributes['temperature']) > 21;");

        countMapping.setFilter(filter);

        aggMappings.add(countMapping);

        config.setAggMappings(aggMappings);

        config.setPeriodTimeUnit(TimeUnit.MILLISECONDS);
        config.setPeriodValue(0);

        ObjectMapper mapper = new ObjectMapper();
        nodeConfiguration = new TbNodeConfiguration(mapper.valueToTree(config));
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
        verify(ctx, new Times(parentCount * 2)).enqueueForTellNext(captor.capture(), eq(SUCCESS));

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
        verify(ctx, new Times(successMsgCount)).enqueueForTellNext(captor.capture(), eq(SUCCESS));

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
                Assert.assertTrue(t.startsWith("Aggregation failed. Unable to parse value"));
                String invalidValue = invalidValueMap.get(failedMsg.getOriginator());
                Assert.assertNotNull(invalidValue);
                Assert.assertTrue(t.contains(invalidValue));
            }
        }
    }

    private void verifyMessage(TbMsg msg) {
        Assert.assertEquals(SessionMsgType.POST_TELEMETRY_REQUEST.name(), msg.getType());
        EntityId entityId = msg.getOriginator();
        Assert.assertNotNull(entityId);
        String data = msg.getData();
        Assert.assertNotNull(data);
        JsonObject dataJson = gson.fromJson(data, JsonObject.class);

        Assert.assertTrue(dataJson.has("latestAvgTemperature") || dataJson.has("deviceCount"));
        if (dataJson.has("latestAvgTemperature")) {
            JsonElement elem = dataJson.get("latestAvgTemperature");
            Assert.assertTrue(elem.isJsonPrimitive());
            double doubleVal = elem.getAsDouble();
            Assert.assertEquals(expectedAvgTempMap.get(entityId).doubleValue(), doubleVal, 0.0);
        }
        if (dataJson.has("deviceCount")) {
            JsonElement elem = dataJson.get("deviceCount");
            Assert.assertTrue(elem.isJsonPrimitive());
            long longVal = elem.getAsLong();
            Assert.assertEquals(expectedDeviceCountMap.get(entityId).longValue(), longVal);
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
}
