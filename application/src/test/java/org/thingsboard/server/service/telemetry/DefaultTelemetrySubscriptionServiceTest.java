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
package org.thingsboard.server.service.telemetry;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.rule.engine.api.AttributesSaveRequest;
import org.thingsboard.rule.engine.api.TimeseriesSaveRequest;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.ApiUsageState;
import org.thingsboard.server.common.data.ApiUsageStateValue;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TimeseriesSaveResult;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.objects.AttributesEntityView;
import org.thingsboard.server.common.data.objects.TelemetryEntityView;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.common.stats.TbApiUsageReportClient;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.discovery.QueueKey;
import org.thingsboard.server.queue.discovery.event.PartitionChangeEvent;
import org.thingsboard.server.service.apiusage.TbApiUsageStateService;
import org.thingsboard.server.service.cf.CalculatedFieldQueueService;
import org.thingsboard.server.service.entitiy.entityview.TbEntityViewService;
import org.thingsboard.server.service.subscription.SubscriptionManagerService;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class DefaultTelemetrySubscriptionServiceTest {

    final TenantId tenantId = TenantId.fromUUID(UUID.fromString("a00ec470-c6b4-11ef-8c88-63b5533fb5bc"));
    final CustomerId customerId = new CustomerId(UUID.fromString("7bdc9750-c775-11ef-8e03-ff69ed8da327"));
    final EntityId entityId = DeviceId.fromString("cc51e450-53e1-11ee-883e-e56b48fd2088");

    final long sampleTtl = 10_000L;

    final List<TsKvEntry> sampleTimeseries = List.of(
            new BasicTsKvEntry(100L, new DoubleDataEntry("temperature", 65.2)),
            new BasicTsKvEntry(100L, new DoubleDataEntry("humidity", 33.1))
    );

    ApiUsageState apiUsageState;

    final TopicPartitionInfo tpi = TopicPartitionInfo.builder()
            .tenantId(tenantId)
            .myPartition(true)
            .build();

    ExecutorService wsCallBackExecutor;
    ExecutorService tsCallBackExecutor;

    @Mock
    TbClusterService clusterService;
    @Mock
    PartitionService partitionService;
    @Mock
    SubscriptionManagerService subscriptionManagerService;
    @Mock
    AttributesService attrService;
    @Mock
    TimeseriesService tsService;
    @Mock
    TbEntityViewService tbEntityViewService;
    @Mock
    TbApiUsageReportClient apiUsageClient;
    @Mock
    TbApiUsageStateService apiUsageStateService;
    @Mock
    CalculatedFieldQueueService calculatedFieldQueueService;

    DefaultTelemetrySubscriptionService telemetryService;

    @BeforeEach
    void setup() {
        telemetryService = new DefaultTelemetrySubscriptionService(attrService, tsService, tbEntityViewService, apiUsageClient, apiUsageStateService, calculatedFieldQueueService);
        ReflectionTestUtils.setField(telemetryService, "clusterService", clusterService);
        ReflectionTestUtils.setField(telemetryService, "partitionService", partitionService);
        ReflectionTestUtils.setField(telemetryService, "subscriptionManagerService", Optional.of(subscriptionManagerService));

        wsCallBackExecutor = MoreExecutors.newDirectExecutorService();
        ReflectionTestUtils.setField(telemetryService, "wsCallBackExecutor", wsCallBackExecutor);

        tsCallBackExecutor = MoreExecutors.newDirectExecutorService();
        ReflectionTestUtils.setField(telemetryService, "tsCallBackExecutor", tsCallBackExecutor);

        apiUsageState = new ApiUsageState();
        apiUsageState.setDbStorageState(ApiUsageStateValue.ENABLED);
        lenient().when(apiUsageStateService.getApiUsageState(tenantId)).thenReturn(apiUsageState);

        lenient().when(partitionService.resolve(ServiceType.TB_CORE, tenantId, entityId)).thenReturn(tpi);

        lenient().when(tsService.save(tenantId, entityId, sampleTimeseries, sampleTtl, false)).thenReturn(immediateFuture(TimeseriesSaveResult.of(sampleTimeseries.size(), listOfNNumbers(sampleTimeseries.size()))));
        lenient().when(tsService.saveWithoutLatest(tenantId, entityId, sampleTimeseries, sampleTtl, false)).thenReturn(immediateFuture(TimeseriesSaveResult.of(sampleTimeseries.size(), null)));
        lenient().when(tsService.saveLatest(tenantId, entityId, sampleTimeseries)).thenReturn(immediateFuture(TimeseriesSaveResult.of(sampleTimeseries.size(), listOfNNumbers(sampleTimeseries.size()))));

        // mock no entity views
        lenient().when(tbEntityViewService.findEntityViewsByTenantIdAndEntityIdAsync(tenantId, entityId)).thenReturn(immediateFuture(Collections.emptyList()));

        // mock that calls to CF queue service are always successful
        lenient().doAnswer(inv -> {
            FutureCallback<Void> callback = inv.getArgument(2);
            callback.onSuccess(null);
            return null;
        }).when(calculatedFieldQueueService).pushRequestToQueue(any(TimeseriesSaveRequest.class), any(), any());

        // send partition change event so currentPartitions set is populated
        telemetryService.onTbApplicationEvent(new PartitionChangeEvent(this, ServiceType.TB_CORE, Map.of(new QueueKey(ServiceType.TB_CORE), Set.of(tpi))));
    }

    @AfterEach
    void cleanup() {
        wsCallBackExecutor.shutdownNow();
        tsCallBackExecutor.shutdownNow();
    }

    @Test
    void shouldReportStorageDataPointsApiUsageWhenTimeSeriesIsSaved() {
        // GIVEN
        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(new TimeseriesSaveRequest.Strategy(true, false, false, false))
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        then(apiUsageClient).should().report(tenantId, customerId, ApiUsageRecordKey.STORAGE_DP_COUNT, sampleTimeseries.size());
    }

    @Test
    void shouldNotReportStorageDataPointsApiUsageWhenTimeSeriesIsNotSaved() {
        // GIVEN
        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(TimeseriesSaveRequest.Strategy.LATEST_AND_WS)
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        then(apiUsageClient).shouldHaveNoInteractions();
    }

    @Test
    void shouldThrowStorageDisabledWhenTimeSeriesIsSavedAndStorageIsDisabled() {
        // GIVEN
        apiUsageState.setDbStorageState(ApiUsageStateValue.DISABLED);

        SettableFuture<Void> future = SettableFuture.create();
        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(TimeseriesSaveRequest.Strategy.PROCESS_ALL)
                .future(future)
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        assertThat(future).failsWithin(Duration.ofSeconds(5))
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(RuntimeException.class)
                .withMessageContaining("DB storage writes are disabled due to API limits!");
    }

    @Test
    void shouldNotThrowStorageDisabledWhenTimeSeriesIsNotSavedAndStorageIsDisabled() {
        // GIVEN
        apiUsageState.setDbStorageState(ApiUsageStateValue.DISABLED);

        SettableFuture<Void> future = SettableFuture.create();
        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(TimeseriesSaveRequest.Strategy.LATEST_AND_WS)
                .future(future)
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        assertThat(future).succeedsWithin(Duration.ofSeconds(5));
    }

    @Test
    void shouldCopyLatestToEntityViewWhenLatestIsSavedOnMainEntity() {
        // GIVEN
        var entityView = new EntityView(new EntityViewId(UUID.randomUUID()));
        entityView.setTenantId(tenantId);
        entityView.setCustomerId(customerId);
        entityView.setEntityId(entityId);
        entityView.setKeys(new TelemetryEntityView(sampleTimeseries.stream().map(KvEntry::getKey).toList(), new AttributesEntityView()));

        // mock that there is one entity view
        given(tbEntityViewService.findEntityViewsByTenantIdAndEntityIdAsync(tenantId, entityId)).willReturn(immediateFuture(List.of(entityView)));
        // mock that save latest call for entity view is successful
        given(tsService.saveLatest(tenantId, entityView.getId(), sampleTimeseries)).willReturn(immediateFuture(TimeseriesSaveResult.of(sampleTimeseries.size(), listOfNNumbers(sampleTimeseries.size()))));
        // mock TPI for entity view
        given(partitionService.resolve(ServiceType.TB_CORE, tenantId, entityView.getId())).willReturn(tpi);

        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(new TimeseriesSaveRequest.Strategy(false, true, false, false))
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        // should save latest to both the main entity and it's entity view
        then(tsService).should().saveLatest(tenantId, entityId, sampleTimeseries);
        then(tsService).should().saveLatest(tenantId, entityView.getId(), sampleTimeseries);
        then(tsService).shouldHaveNoMoreInteractions();

        // should send WS update only for entity view (WS update for the main entity is disabled in the save request)
        then(subscriptionManagerService).should().onTimeSeriesUpdate(tenantId, entityView.getId(), sampleTimeseries, TbCallback.EMPTY);
        then(subscriptionManagerService).shouldHaveNoMoreInteractions();
    }

    @Test
    void shouldNotCopyLatestToEntityViewWhenLatestIsNotSavedOnMainEntity() {
        // GIVEN
        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(new TimeseriesSaveRequest.Strategy(true, false, false, false))
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        // should save only time series for the main entity
        then(tsService).should().saveWithoutLatest(tenantId, entityId, sampleTimeseries, sampleTtl, false);
        then(tsService).shouldHaveNoMoreInteractions();

        // should not send any WS updates
        then(subscriptionManagerService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("allCombinationsOfFourBooleans")
    void shouldCallCorrectSaveTimeseriesApiBasedOnBooleanFlagsInTheSaveRequest(boolean saveTimeseries, boolean saveLatest, boolean sendWsUpdate, boolean processCalculatedFields) {
        // GIVEN
        var request = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .customerId(customerId)
                .entityId(entityId)
                .entries(sampleTimeseries)
                .ttl(sampleTtl)
                .strategy(new TimeseriesSaveRequest.Strategy(saveTimeseries, saveLatest, sendWsUpdate, processCalculatedFields))
                .build();

        // WHEN
        telemetryService.saveTimeseries(request);

        // THEN
        if (saveTimeseries && saveLatest) {
            then(tsService).should().save(tenantId, entityId, sampleTimeseries, sampleTtl, false);
        } else if (saveLatest) {
            then(tsService).should().saveLatest(tenantId, entityId, sampleTimeseries);
        } else if (saveTimeseries) {
            then(tsService).should().saveWithoutLatest(tenantId, entityId, sampleTimeseries, sampleTtl, false);
        }
        then(tsService).shouldHaveNoMoreInteractions();

        if (processCalculatedFields) {
            then(calculatedFieldQueueService).should().pushRequestToQueue(eq(request), any(), eq(request.getCallback()));
        }

        if (sendWsUpdate) {
            then(subscriptionManagerService).should().onTimeSeriesUpdate(tenantId, entityId, sampleTimeseries, TbCallback.EMPTY);
        } else {
            then(subscriptionManagerService).shouldHaveNoInteractions();
        }
    }

    private static Stream<Arguments> allCombinationsOfFourBooleans() {
        return Stream.of(
                Arguments.of(true, true, true, true),
                Arguments.of(true, true, true, false),
                Arguments.of(true, true, false, true),
                Arguments.of(true, true, false, false),
                Arguments.of(true, false, true, true),
                Arguments.of(true, false, true, false),
                Arguments.of(true, false, false, true),
                Arguments.of(true, false, false, false),
                Arguments.of(false, true, true, true),
                Arguments.of(false, true, true, false),
                Arguments.of(false, true, false, true),
                Arguments.of(false, true, false, false),
                Arguments.of(false, false, true, true),
                Arguments.of(false, false, true, false),
                Arguments.of(false, false, false, true),
                Arguments.of(false, false, false, false)
        );
    }

    @ParameterizedTest
    @MethodSource("allCombinationsOfTwoBooleans")
    void shouldCallCorrectSaveAttributesApiBasedOnBooleanFlagsInTheSaveRequest(boolean saveAttributes, boolean sendWsUpdate) {
        // GIVEN
        var request = AttributesSaveRequest.builder()
                .tenantId(tenantId)
                .entityId(entityId)
                .scope(AttributeScope.SERVER_SCOPE)
                .entry(new DoubleDataEntry("temperature", 65.2))
                .notifyDevice(false)
                .strategy(new AttributesSaveRequest.Strategy(saveAttributes, sendWsUpdate))
                .build();

        lenient().when(attrService.save(tenantId, entityId, request.getScope(), request.getEntries())).thenReturn(immediateFuture(listOfNNumbers(request.getEntries().size())));

        // WHEN
        telemetryService.saveAttributes(request);

        // THEN
        if (saveAttributes) {
            then(attrService).should().save(tenantId, entityId, request.getScope(), request.getEntries());
        } else {
            then(attrService).shouldHaveNoInteractions();
        }

        if (sendWsUpdate) {
            then(subscriptionManagerService).should().onAttributesUpdate(tenantId, entityId, request.getScope().name(), request.getEntries(), request.isNotifyDevice(), TbCallback.EMPTY);
        } else {
            then(subscriptionManagerService).shouldHaveNoInteractions();
        }
    }

    static Stream<Arguments> allCombinationsOfTwoBooleans() {
        return Stream.of(
                Arguments.of(true, true),
                Arguments.of(true, false),
                Arguments.of(false, true),
                Arguments.of(false, false)
        );
    }

    // used to emulate versions returned by save APIs
    private static List<Long> listOfNNumbers(int N) {
        return LongStream.range(0, N).boxed().toList();
    }

}
