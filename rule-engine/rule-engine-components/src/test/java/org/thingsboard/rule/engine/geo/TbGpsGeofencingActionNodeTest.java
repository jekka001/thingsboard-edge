/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.rule.engine.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.attributes.AttributesService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.thingsboard.rule.engine.util.GpsGeofencingEvents.ENTERED;
import static org.thingsboard.rule.engine.util.GpsGeofencingEvents.INSIDE;
import static org.thingsboard.server.common.data.msg.TbNodeConnectionType.SUCCESS;

class TbGpsGeofencingActionNodeTest {
    private TbContext ctx;
    private TbGpsGeofencingActionNode node;
    private AttributesService attributesService;

    @BeforeEach
    void setUp() {
        ctx = mock(TbContext.class);
        attributesService = mock(AttributesService.class);
        node = new TbGpsGeofencingActionNode();
    }

    @AfterEach
    void tearDown() {
        node.destroy();
    }

    private static Stream<Arguments> givenDefaultConfig_whenOnMsg_thenVerifyOutputMsgTypes() {
        return Stream.of(
                // default config
                Arguments.of(false, List.of(
                        Map.of(ENTERED, 0, INSIDE, 0, SUCCESS, 0),
                        Map.of(ENTERED, 1, INSIDE, 0, SUCCESS, 0),
                        Map.of(ENTERED, 1, INSIDE, 0, SUCCESS, 1),
                        Map.of(ENTERED, 1, INSIDE, 1, SUCCESS, 1),
                        Map.of(ENTERED, 1, INSIDE, 1, SUCCESS, 2)
                )),
                // default config with presenceMonitoring
                Arguments.of(true, List.of(
                        Map.of(ENTERED, 0, INSIDE, 0, SUCCESS, 0),
                        Map.of(ENTERED, 1, INSIDE, 0, SUCCESS, 0),
                        Map.of(ENTERED, 1, INSIDE, 1, SUCCESS, 0),
                        Map.of(ENTERED, 1, INSIDE, 2, SUCCESS, 0),
                        Map.of(ENTERED, 1, INSIDE, 3, SUCCESS, 0)
                ))
        );
    }

    @ParameterizedTest
    @MethodSource
    void givenDefaultConfig_whenOnMsg_thenVerifyOutputMsgTypes( boolean presenceMonitoring, List<Map<String, Integer>> outputMsgTypesCountList) throws TbNodeException {
        // GIVEN
        var config = new TbGpsGeofencingActionNodeConfiguration().defaultConfiguration();
        config.setPresenceMonitoring(presenceMonitoring);
        node.init(ctx, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));

        DeviceId deviceId = new DeviceId(UUID.randomUUID());
        TbMsgMetaData metadata = getMetadataForNewVersionPolygonPerimeter();
        TbMsg msg = getTbMsg(deviceId, metadata,
                GeoUtilTest.POINT_OUTSIDE_SIMPLE_RECT.getLatitude(), GeoUtilTest.POINT_OUTSIDE_SIMPLE_RECT.getLongitude());

        when(ctx.getAttributesService()).thenReturn(attributesService);
        when(ctx
                .getAttributesService()
                .find(ctx.getTenantId(), msg.getOriginator(), DataConstants.SERVER_SCOPE, ctx.getServiceId()))
                .thenReturn(Futures.immediateFuture(Optional.empty()));

        // WHEN
        ArgumentCaptor<TbMsg> newMsgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        node.onMsg(ctx, msg);

        // THEN
        verifyNodeOutputs(newMsgCaptor, outputMsgTypesCountList.get(0));

        // WHEN
        msg = getTbMsg(deviceId, metadata,
                GeoUtilTest.POINT_INSIDE_SIMPLE_RECT_CENTER.getLatitude(), GeoUtilTest.POINT_INSIDE_SIMPLE_RECT_CENTER.getLongitude());
        node.onMsg(ctx, msg);

        // THEN
        verifyNodeOutputs(newMsgCaptor, outputMsgTypesCountList.get(1));

        // WHEN
        node.onMsg(ctx, msg);

        // THEN
        verifyNodeOutputs(newMsgCaptor, outputMsgTypesCountList.get(2));

        // WHEN
        config.setMinInsideDuration(0);
        node.init(ctx, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));
        node.onMsg(ctx, msg);

        // THEN
        verifyNodeOutputs(newMsgCaptor, outputMsgTypesCountList.get(3));

        // WHEN
        node.onMsg(ctx, msg);

        // THEN
        verifyNodeOutputs(newMsgCaptor, outputMsgTypesCountList.get(4));
    }

    private TbMsg getTbMsg(EntityId entityId, TbMsgMetaData metadata, double latitude, double longitude) {
        String data = "{\"latitude\": " + latitude + ", \"longitude\": " + longitude + "}";
        return TbMsg.newMsg(TbMsgType.POST_ATTRIBUTES_REQUEST, entityId, metadata, data);
    }

    private TbMsgMetaData getMetadataForNewVersionPolygonPerimeter() {
        var metadata = new TbMsgMetaData();
        metadata.putValue("ss_perimeter", GeoUtilTest.SIMPLE_RECT);
        return metadata;
    }

    private void verifyNodeOutputs(ArgumentCaptor<TbMsg> newMsgCaptor, Map<String, Integer> outputMsgTypesCount) {
        verify(this.ctx, times(outputMsgTypesCount.get(ENTERED))).tellNext(newMsgCaptor.capture(), eq(ENTERED));
        verify(this.ctx, times(outputMsgTypesCount.get(INSIDE))).tellNext(newMsgCaptor.capture(), eq(INSIDE));
        verify(this.ctx, times(outputMsgTypesCount.get(SUCCESS))).tellSuccess(newMsgCaptor.capture());
    }

    // Rule nodes upgrade
    private static Stream<Arguments> givenFromVersionAndConfig_whenUpgrade_thenVerifyHasChangesAndConfig() {
        return Stream.of(
                // default config for version 0
                Arguments.of(0,
                        "{\n" +
                                "  \"minInsideDuration\": 1,\n" +
                                "  \"minOutsideDuration\": 1,\n" +
                                "  \"minInsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"minOutsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"latitudeKeyName\": \"latitude\",\n" +
                                "  \"longitudeKeyName\": \"longitude\",\n" +
                                "  \"perimeterType\": \"POLYGON\",\n" +
                                "  \"fetchPerimeterInfoFromMessageMetadata\": true,\n" +
                                "  \"perimeterKeyName\": \"ss_perimeter\",\n" +
                                "  \"polygonsDefinition\": null,\n" +
                                "  \"centerLatitude\": null,\n" +
                                "  \"centerLongitude\": null,\n" +
                                "  \"range\": null,\n" +
                                "  \"rangeUnit\": null\n" +
                                "}\n",
                        true,
                        "{\n" +
                                "  \"minInsideDuration\": 1,\n" +
                                "  \"minOutsideDuration\": 1,\n" +
                                "  \"minInsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"minOutsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"presenceMonitoring\": false,\n" +
                                "  \"latitudeKeyName\": \"latitude\",\n" +
                                "  \"longitudeKeyName\": \"longitude\",\n" +
                                "  \"perimeterType\": \"POLYGON\",\n" +
                                "  \"fetchPerimeterInfoFromMessageMetadata\": true,\n" +
                                "  \"perimeterKeyName\": \"ss_perimeter\",\n" +
                                "  \"polygonsDefinition\": null,\n" +
                                "  \"centerLatitude\": null,\n" +
                                "  \"centerLongitude\": null,\n" +
                                "  \"range\": null,\n" +
                                "  \"rangeUnit\": null\n" +
                                "}\n"),
                // default config for version 1 with upgrade from version 0
                Arguments.of(0,
                        "{\n" +
                                "  \"minInsideDuration\": 1,\n" +
                                "  \"minOutsideDuration\": 1,\n" +
                                "  \"minInsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"minOutsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"presenceMonitoring\": false,\n" +
                                "  \"latitudeKeyName\": \"latitude\",\n" +
                                "  \"longitudeKeyName\": \"longitude\",\n" +
                                "  \"perimeterType\": \"POLYGON\",\n" +
                                "  \"fetchPerimeterInfoFromMessageMetadata\": true,\n" +
                                "  \"perimeterKeyName\": \"ss_perimeter\",\n" +
                                "  \"polygonsDefinition\": null,\n" +
                                "  \"centerLatitude\": null,\n" +
                                "  \"centerLongitude\": null,\n" +
                                "  \"range\": null,\n" +
                                "  \"rangeUnit\": null\n" +
                                "}\n",
                        false,
                        "{\n" +
                                "  \"minInsideDuration\": 1,\n" +
                                "  \"minOutsideDuration\": 1,\n" +
                                "  \"minInsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"minOutsideDurationTimeUnit\": \"MINUTES\",\n" +
                                "  \"presenceMonitoring\": false,\n" +
                                "  \"latitudeKeyName\": \"latitude\",\n" +
                                "  \"longitudeKeyName\": \"longitude\",\n" +
                                "  \"perimeterType\": \"POLYGON\",\n" +
                                "  \"fetchPerimeterInfoFromMessageMetadata\": true,\n" +
                                "  \"perimeterKeyName\": \"ss_perimeter\",\n" +
                                "  \"polygonsDefinition\": null,\n" +
                                "  \"centerLatitude\": null,\n" +
                                "  \"centerLongitude\": null,\n" +
                                "  \"range\": null,\n" +
                                "  \"rangeUnit\": null\n" +
                                "}\n")
        );
    }

    @ParameterizedTest
    @MethodSource
    void givenFromVersionAndConfig_whenUpgrade_thenVerifyHasChangesAndConfig(int givenVersion, String givenConfigStr, boolean hasChanges, String expectedConfigStr) throws TbNodeException {
        // GIVEN
        JsonNode givenConfig = JacksonUtil.toJsonNode(givenConfigStr);
        JsonNode expectedConfig = JacksonUtil.toJsonNode(expectedConfigStr);

        // WHEN
        TbPair<Boolean, JsonNode> upgradeResult = node.upgrade(givenVersion, givenConfig);

        // THEN
        assertThat(upgradeResult.getFirst()).isEqualTo(hasChanges);
        ObjectNode upgradedConfig = (ObjectNode) upgradeResult.getSecond();
        assertThat(upgradedConfig).isEqualTo(expectedConfig);
    }

}
