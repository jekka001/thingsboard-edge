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
package org.thingsboard.server.service.entitiy.entityview;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thingsboard.rule.engine.api.TimeseriesSaveRequest;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.objects.AttributesEntityView;
import org.thingsboard.server.common.data.objects.TelemetryEntityView;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import java.util.List;
import java.util.UUID;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DefaultTbEntityViewServiceTest {

    final TenantId tenantId = TenantId.fromUUID(UUID.fromString("f09c8180-686c-11ef-9471-a71d33080e9c"));
    final EntityId entityId = DeviceId.fromString("782aaab0-c7a8-11ef-a668-79582e785d5f");

    @Mock
    EntityViewService entityViewService;
    @Mock
    AttributesService attributesService;
    @Mock
    TelemetrySubscriptionService tsSubService;
    @Mock
    TimeseriesService tsService;

    DefaultTbEntityViewService defaultTbEntityViewService;

    @BeforeEach
    void setup() {
        defaultTbEntityViewService = new DefaultTbEntityViewService(entityViewService, attributesService, tsSubService, tsService);
    }

    @Test
    void shouldNotSaveTimeseriesWhenCopyingLatestToEntityView() throws Exception {
        // GIVEN
        var entityView = new EntityView(new EntityViewId(UUID.randomUUID()));
        entityView.setTenantId(tenantId);
        entityView.setEntityId(entityId);
        entityView.setKeys(new TelemetryEntityView(List.of("temperature"), new AttributesEntityView()));

        List<TsKvEntry> latest = List.of(new BasicTsKvEntry(123L, new DoubleDataEntry("temperature", 22.3)));

        given(tsService.findAll(eq(tenantId), eq(entityId), anyList())).willReturn(immediateFuture(latest));

        // WHEN
        defaultTbEntityViewService.updateEntityViewAttributes(tenantId, entityView, null, null);

        // THEN
        var captor = ArgumentCaptor.forClass(TimeseriesSaveRequest.class);
        then(tsSubService).should().saveTimeseries(captor.capture());

        var expectedCopyLatestRequest = TimeseriesSaveRequest.builder()
                .tenantId(tenantId)
                .entityId(entityView.getId())
                .entries(latest)
                .ttl(0L)
                .strategy(TimeseriesSaveRequest.Strategy.LATEST_AND_WS)
                .build();

        var actualCopyLatestRequest = captor.getValue();

        assertThat(actualCopyLatestRequest)
                .usingRecursiveComparison()
                .ignoringFields("callback")
                .isEqualTo(expectedCopyLatestRequest);
    }

}
