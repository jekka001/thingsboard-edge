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
package org.thingsboard.rule.engine.metadata;

import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.TestDbCallbackExecutor;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.ReadTsKvQuery;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.kv.TsKvQuery;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.spy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;

@ExtendWith(MockitoExtension.class)
public class TbGetTelemetryNodeTest {

    private final TenantId TENANT_ID = TenantId.fromUUID(UUID.fromString("5738401b-9dba-422b-b656-a62fe7431917"));
    private final DeviceId DEVICE_ID = new DeviceId(UUID.fromString("8a8fd749-b2ec-488b-a6c6-fc66614d8686"));

    private final ListeningExecutor executor = new TestDbCallbackExecutor();

    private TbGetTelemetryNode node;
    private TbGetTelemetryNodeConfiguration config;

    @Mock
    private TbContext ctxMock;
    @Mock
    private TimeseriesService timeseriesServiceMock;

    @BeforeEach
    public void setUp() {
        node = spy(new TbGetTelemetryNode());
        config = new TbGetTelemetryNodeConfiguration().defaultConfiguration();
        config.setLatestTsKeyNames(List.of("temperature"));
    }

    @Test
    public void givenAggregationAsString_whenParseAggregation_thenReturnEnum() throws TbNodeException {
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);
        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        //compatibility with old configs without "aggregation" parameter
        assertThat(node.parseAggregationConfig(null)).isEqualTo(Aggregation.NONE);
        assertThat(node.parseAggregationConfig("")).isEqualTo(Aggregation.NONE);

        //common values
        assertThat(node.parseAggregationConfig("MIN")).isEqualTo(Aggregation.MIN);
        assertThat(node.parseAggregationConfig("MAX")).isEqualTo(Aggregation.MAX);
        assertThat(node.parseAggregationConfig("AVG")).isEqualTo(Aggregation.AVG);
        assertThat(node.parseAggregationConfig("SUM")).isEqualTo(Aggregation.SUM);
        assertThat(node.parseAggregationConfig("COUNT")).isEqualTo(Aggregation.COUNT);
        assertThat(node.parseAggregationConfig("NONE")).isEqualTo(Aggregation.NONE);

        //all possible values in future
        for (Aggregation aggEnum : Aggregation.values()) {
            assertThat(node.parseAggregationConfig(aggEnum.name())).isEqualTo(aggEnum);
        }
    }

    @Test
    public void givenAggregationWhiteSpace_whenParseAggregation_thenException() throws TbNodeException {
        // GIVEN
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);
        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        // WHEN-THEN
        assertThatThrownBy(() -> node.parseAggregationConfig(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void givenAggregationIncorrect_whenParseAggregation_thenException() throws TbNodeException {
        // GIVEN
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);
        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        // WHEN-THEN
        assertThatThrownBy(() -> node.parseAggregationConfig("TOP")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void verifyDefaultConfig() {
        config = new TbGetTelemetryNodeConfiguration().defaultConfiguration();
        assertThat(config.getStartInterval()).isEqualTo(2);
        assertThat(config.getEndInterval()).isEqualTo(1);
        assertThat(config.getStartIntervalPattern()).isEqualTo("");
        assertThat(config.getEndIntervalPattern()).isEqualTo("");
        assertThat(config.isUseMetadataIntervalPatterns()).isFalse();
        assertThat(config.getStartIntervalTimeUnit()).isEqualTo(TimeUnit.MINUTES.name());
        assertThat(config.getEndIntervalTimeUnit()).isEqualTo(TimeUnit.MINUTES.name());
        assertThat(config.getFetchMode()).isEqualTo(TbGetTelemetryNodeConfiguration.FETCH_MODE_FIRST);
        assertThat(config.getOrderBy()).isEqualTo("ASC");
        assertThat(config.getAggregation()).isEqualTo(Aggregation.NONE.name());
        assertThat(config.getLimit()).isEqualTo(1000);
        assertThat(config.getLatestTsKeyNames()).isEmpty();
    }

    @Test
    public void givenEmptyTsKeyNames_whenInit_thenThrowsException() {
        // GIVEN
        config.setLatestTsKeyNames(Collections.emptyList());

        // WHEN-THEN
        assertThatThrownBy(() -> node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config))))
                .isInstanceOf(TbNodeException.class)
                .hasMessage("Telemetry is not selected!")
                .extracting(e -> ((TbNodeException) e).isUnrecoverable())
                .isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 1001, 2000})
    public void givenFetchModeAllAndLimitIsOutOfRange_whenInit_thenThrowsException(int limit) {
        // GIVEN
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);
        config.setLimit(limit);

        // THEN
        assertThatThrownBy(() -> node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config))))
                .isInstanceOf(TbNodeException.class)
                .hasMessage("Limit should be in a range from 2 to 1000.")
                .extracting(e -> ((TbNodeException) e).isUnrecoverable())
                .isEqualTo(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {".ASC", "ascending", "DESCENDING"})
    public void givenFetchModeAllAndInvalidOrderBy_whenInit_thenThrowsException(String orderBy) {
        // GIVEN
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);
        config.setLimit(2);
        config.setOrderBy(orderBy);

        // WHEN-THEN
        assertThatThrownBy(() -> node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config))))
                .isInstanceOf(TbNodeException.class)
                .hasMessage("Invalid fetch order selected.")
                .extracting(e -> ((TbNodeException) e).isUnrecoverable())
                .isEqualTo(true);
    }

    @Test
    public void givenUseMetadataIntervalPatternsIsTrue_whenOnMsg_thenVerifyStartAndEndTsInQuery() throws TbNodeException {
        // GIVEN
        config.setUseMetadataIntervalPatterns(true);
        config.setStartIntervalPattern("${mdStartInterval}");
        config.setEndIntervalPattern("$[msgEndInterval]");

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(Collections.emptyList()));

        // WHEN
        long startTs = 1719220350000L;
        long endTs = 1719220353000L;
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("mdStartInterval", String.valueOf(startTs));
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, metaData, "{\"msgEndInterval\":\"" + endTs + "\"}");
        node.onMsg(ctxMock, msg);

        /// THEN
        ArgumentCaptor<List<ReadTsKvQuery>> actualReadTsKvQueryList = ArgumentCaptor.forClass(List.class);
        then(timeseriesServiceMock).should().findAll(eq(TENANT_ID), eq(DEVICE_ID), actualReadTsKvQueryList.capture());
        ReadTsKvQuery actualReadTsKvQuery = actualReadTsKvQueryList.getValue().get(0);
        assertThat(actualReadTsKvQuery.getStartTs()).isEqualTo(startTs);
        assertThat(actualReadTsKvQuery.getEndTs()).isEqualTo(endTs);
    }

    @Test
    public void givenUseMetadataIntervalPatternsIsFalse_whenOnMsg_thenVerifyStartAndEndTsInQuery() throws TbNodeException {
        // GIVEN
        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        long ts = System.currentTimeMillis();
        willReturn(ts).given(node).getCurrentTimeMillis();
        mockTimeseriesService();
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(Collections.emptyList()));

        // WHEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<List<ReadTsKvQuery>> actualReadTsKvQueryList = ArgumentCaptor.forClass(List.class);
        then(timeseriesServiceMock).should().findAll(eq(TENANT_ID), eq(DEVICE_ID), actualReadTsKvQueryList.capture());
        ReadTsKvQuery actualReadTsKvQuery = actualReadTsKvQueryList.getValue().get(0);
        assertThat(actualReadTsKvQuery.getStartTs()).isEqualTo(ts - TimeUnit.MINUTES.toMillis(config.getStartInterval()));
        assertThat(actualReadTsKvQuery.getEndTs()).isEqualTo(ts - TimeUnit.MINUTES.toMillis(config.getEndInterval()));
    }

    @Test
    public void givenTsKeyNamesPatterns_whenOnMsg_thenVerifyTsKeyNamesInQuery() throws TbNodeException {
        // GIVEN
        config.setLatestTsKeyNames(List.of("temperature", "${mdTsKey}", "$[msgTsKey]"));

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(Collections.emptyList()));

        // WHEN
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("mdTsKey", "humidity");
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, metaData, "{\"msgTsKey\":\"pressure\"}");
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<List<ReadTsKvQuery>> actualReadTsKvQueryList = ArgumentCaptor.forClass(List.class);
        then(timeseriesServiceMock).should().findAll(eq(TENANT_ID), eq(DEVICE_ID), actualReadTsKvQueryList.capture());
        List<String> actualKeys = actualReadTsKvQueryList.getValue().stream().map(TsKvQuery::getKey).toList();
        assertThat(actualKeys).containsAll(List.of("temperature", "humidity", "pressure"));
    }

    @ParameterizedTest
    @MethodSource
    public void givenAggregation_whenOnMsg_thenVerifyAggregationStepInQuery(String aggregation, Consumer<ReadTsKvQuery> verifyAggregationStepInQuery) throws TbNodeException {
        // GIVEN
        config.setStartInterval(5);
        config.setEndInterval(1);
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);
        config.setAggregation(aggregation);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(Collections.emptyList()));

        // WHEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<List<ReadTsKvQuery>> actualReadTsKvQueryList = ArgumentCaptor.forClass(List.class);
        then(timeseriesServiceMock).should().findAll(eq(TENANT_ID), eq(DEVICE_ID), actualReadTsKvQueryList.capture());
        ReadTsKvQuery actualReadTsKvQuery = actualReadTsKvQueryList.getValue().get(0);
        verifyAggregationStepInQuery.accept(actualReadTsKvQuery);
    }

    private static Stream<Arguments> givenAggregation_whenOnMsg_thenVerifyAggregationStepInQuery() {
        return Stream.of(
                Arguments.of("", (Consumer<ReadTsKvQuery>) query -> assertThat(query.getInterval()).isEqualTo(1)),
                Arguments.of("MIN", (Consumer<ReadTsKvQuery>) query -> assertThat(query.getInterval()).isEqualTo(query.getEndTs() - query.getStartTs()))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void givenFetchModeAndLimit_whenOnMsg_thenVerifyLimitInQuery(String fetchMode, int limit, Consumer<ReadTsKvQuery> verifyLimitInQuery) throws TbNodeException {
        // GIVEN
        config.setFetchMode(fetchMode);
        config.setLimit(limit);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(Collections.emptyList()));

        // WHEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<List<ReadTsKvQuery>> actualReadTsKvQueryList = ArgumentCaptor.forClass(List.class);
        then(timeseriesServiceMock).should().findAll(eq(TENANT_ID), eq(DEVICE_ID), actualReadTsKvQueryList.capture());
        ReadTsKvQuery actualReadTsKvQuery = actualReadTsKvQueryList.getValue().get(0);
        verifyLimitInQuery.accept(actualReadTsKvQuery);
    }

    private static Stream<Arguments> givenFetchModeAndLimit_whenOnMsg_thenVerifyLimitInQuery() {
        return Stream.of(
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL,
                        5,
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getLimit()).isEqualTo(5)),
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_FIRST,
                        TbGetTelemetryNodeConfiguration.MAX_FETCH_SIZE,
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getLimit()).isEqualTo(1)),
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_LAST,
                        10,
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getLimit()).isEqualTo(1))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void givenFetchModeAndOrder_whenOnMsg_thenVerifyOrderInQuery(String fetchMode, String orderBy, Consumer<ReadTsKvQuery> verifyOrderInQuery) throws TbNodeException {
        // GIVEN
        config.setFetchMode(fetchMode);
        config.setOrderBy(orderBy);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(Collections.emptyList()));

        // WHEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<List<ReadTsKvQuery>> actualReadTsKvQueryList = ArgumentCaptor.forClass(List.class);
        then(timeseriesServiceMock).should().findAll(eq(TENANT_ID), eq(DEVICE_ID), actualReadTsKvQueryList.capture());
        ReadTsKvQuery actualReadTsKvQuery = actualReadTsKvQueryList.getValue().get(0);
        verifyOrderInQuery.accept(actualReadTsKvQuery);
    }

    private static Stream<Arguments> givenFetchModeAndOrder_whenOnMsg_thenVerifyOrderInQuery() {
        return Stream.of(
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL,
                        "",
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getOrder()).isEqualTo("ASC")),
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL,
                        "DESC",
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getOrder()).isEqualTo("DESC")),
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_FIRST,
                        "ASC",
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getOrder()).isEqualTo("ASC")),
                Arguments.of(
                        TbGetTelemetryNodeConfiguration.FETCH_MODE_LAST,
                        "ASC",
                        (Consumer<ReadTsKvQuery>) query -> assertThat(query.getOrder()).isEqualTo("DESC"))
        );
    }

    @ParameterizedTest
    @MethodSource
    public void givenInvalidIntervalPatterns_whenOnMsg_thenThrowsException(String startIntervalPattern, String errorMsg) throws TbNodeException {
        // GIVEN
        config.setUseMetadataIntervalPatterns(true);
        config.setStartIntervalPattern(startIntervalPattern);
        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        // WHEN-THEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, "{\"msgStartInterval\":\"start\"}");
        assertThatThrownBy(() -> node.onMsg(ctxMock, msg)).isInstanceOf(IllegalArgumentException.class).hasMessage(errorMsg);
    }

    private static Stream<Arguments> givenInvalidIntervalPatterns_whenOnMsg_thenThrowsException() {
        return Stream.of(
                Arguments.of("${mdStartInterval}", "Message value: 'mdStartInterval' is undefined"),
                Arguments.of("$[msgStartInterval]", "Message value: 'msgStartInterval' has invalid format")
        );
    }

    @Test
    public void givenFetchModeAll_whenOnMsg_thenTellSuccessAndVerifyMsg() throws TbNodeException {
        // GIVEN
        config.setLatestTsKeyNames(List.of("temperature", "humidity"));
        config.setFetchMode(TbGetTelemetryNodeConfiguration.FETCH_MODE_ALL);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        long ts = System.currentTimeMillis();
        List<TsKvEntry> tsKvEntries = List.of(
                new BasicTsKvEntry(ts - 5, new DoubleDataEntry("temperature", 23.1)),
                new BasicTsKvEntry(ts - 4, new DoubleDataEntry("temperature", 22.4)),
                new BasicTsKvEntry(ts - 4, new DoubleDataEntry("humidity", 55.5))
        );
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(tsKvEntries));

        // WHEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<TbMsg> actualMsg = ArgumentCaptor.forClass(TbMsg.class);
        then(ctxMock).should().tellSuccess(actualMsg.capture());
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temperature", "[{\"ts\":" + (ts - 5) + ",\"value\":23.1},{\"ts\":" + (ts - 4) + ",\"value\":22.4}]");
        metaData.putValue("humidity", "[{\"ts\":" + (ts - 4) + ",\"value\":55.5}]");
        TbMsg expectedMsg = TbMsg.transformMsgMetadata(msg, metaData);
        assertThat(actualMsg.getValue()).usingRecursiveComparison().ignoringFields("ctx").isEqualTo(expectedMsg);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIRST", "LAST"})
    public void givenFetchMode_whenOnMsg_thenTellSuccessAndVerifyMsg(String fetchMode) throws TbNodeException {
        // GIVEN
        config.setFetchMode(fetchMode);
        config.setLatestTsKeyNames(List.of("temperature", "humidity"));

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        mockTimeseriesService();
        long ts = System.currentTimeMillis();
        List<TsKvEntry> tsKvEntries = List.of(
                new BasicTsKvEntry(ts - 4, new DoubleDataEntry("temperature", 22.4)),
                new BasicTsKvEntry(ts - 4, new DoubleDataEntry("humidity", 55.5))
        );
        given(timeseriesServiceMock.findAll(any(TenantId.class), any(EntityId.class), anyList())).willReturn(Futures.immediateFuture(tsKvEntries));

        // WHEN
        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, DEVICE_ID, TbMsgMetaData.EMPTY, TbMsg.EMPTY_JSON_OBJECT);
        node.onMsg(ctxMock, msg);

        // THEN
        ArgumentCaptor<TbMsg> actualMsg = ArgumentCaptor.forClass(TbMsg.class);
        then(ctxMock).should().tellSuccess(actualMsg.capture());
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("temperature", "\"22.4\"");
        metaData.putValue("humidity", "\"55.5\"");
        TbMsg expectedMsg = TbMsg.transformMsgMetadata(msg, metaData);
        assertThat(actualMsg.getValue()).usingRecursiveComparison().ignoringFields("ctx").isEqualTo(expectedMsg);
    }

    private void mockTimeseriesService() {
        given(ctxMock.getTimeseriesService()).willReturn(timeseriesServiceMock);
        given(ctxMock.getTenantId()).willReturn(TENANT_ID);
        given(ctxMock.getDbCallbackExecutor()).willReturn(executor);
    }

}
