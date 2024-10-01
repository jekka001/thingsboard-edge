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
package org.thingsboard.server.transport.coap.efento;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType;
import org.thingsboard.server.gen.transport.coap.MeasurementsProtos;
import org.thingsboard.server.gen.transport.coap.MeasurementsProtos.ProtoMeasurements;
import org.thingsboard.server.transport.coap.CoapTransportContext;
import org.thingsboard.server.transport.coap.efento.utils.CoapEfentoUtils;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_AMBIENT_LIGHT;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_ATMOSPHERIC_PRESSURE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_BREATH_VOC;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_CO2_EQUIVALENT;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_CURRENT;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_CURRENT_PRECISE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_DIFFERENTIAL_PRESSURE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_DISTANCE_MM;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_ELECTRICITY_METER;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_ELEC_METER_ACC_MAJOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_ELEC_METER_ACC_MINOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_FLOODING;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_HIGH_PRESSURE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_HUMIDITY;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_HUMIDITY_ACCURATE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_IAQ;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_OK_ALARM;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_OUTPUT_CONTROL;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_PERCENTAGE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_PULSE_CNT;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_PULSE_CNT_ACC_MAJOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_PULSE_CNT_ACC_MINOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_PULSE_CNT_ACC_WIDE_MAJOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_PULSE_CNT_ACC_WIDE_MINOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_SOIL_MOISTURE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_STATIC_IAQ;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_TEMPERATURE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_VOLTAGE;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_WATER_METER;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_WATER_METER_ACC_MAJOR;
import static org.thingsboard.server.gen.transport.coap.MeasurementTypeProtos.MeasurementType.MEASUREMENT_TYPE_WATER_METER_ACC_MINOR;
import static org.thingsboard.server.transport.coap.efento.utils.CoapEfentoUtils.convertTimestampToUtcString;

class CoapEfentoTransportResourceTest {

    private static CoapEfentoTransportResource coapEfentoTransportResource;

    @BeforeAll
    static void setUp() {
        var ctxMock = mock(CoapTransportContext.class);
        coapEfentoTransportResource = new CoapEfentoTransportResource(ctxMock, "testName");
    }

    @Test
    void checkContinuousSensorWithSomeMeasurements() {
        long tsInSec = Instant.now().getEpochSecond();
        ProtoMeasurements measurements = ProtoMeasurements.newBuilder()
                .setSerialNum(integerToByteString(1234))
                .setCloudToken("test_token")
                .setMeasurementPeriodBase(180)
                .setMeasurementPeriodFactor(5)
                .setBatteryStatus(true)
                .setSignal(0)
                .setNextTransmissionAt(1000)
                .setTransferReason(0)
                .setHash(0)
                .addAllChannels(List.of(MeasurementsProtos.ProtoChannel.newBuilder()
                                .setType(MeasurementType.MEASUREMENT_TYPE_TEMPERATURE)
                                .setTimestamp(Math.toIntExact(tsInSec))
                                .addAllSampleOffsets(List.of(223, 224))
                                .build(),
                        MeasurementsProtos.ProtoChannel.newBuilder()
                                .setType(MeasurementType.MEASUREMENT_TYPE_HUMIDITY)
                                .setTimestamp(Math.toIntExact(tsInSec))
                                .addAllSampleOffsets(List.of(20, 30))
                                .build()
                ))
                .build();
        List<CoapEfentoTransportResource.EfentoTelemetry> efentoMeasurements = coapEfentoTransportResource.getEfentoMeasurements(measurements, UUID.randomUUID());
        assertThat(efentoMeasurements).hasSize(2);
        assertThat(efentoMeasurements.get(0).getTs()).isEqualTo(tsInSec * 1000);
        assertThat(efentoMeasurements.get(0).getValues().getAsJsonObject().get("temperature_1").getAsDouble()).isEqualTo(22.3);
        assertThat(efentoMeasurements.get(0).getValues().getAsJsonObject().get("humidity_2").getAsDouble()).isEqualTo(20);
        assertThat(efentoMeasurements.get(1).getTs()).isEqualTo((tsInSec + 180 * 5) * 1000);
        assertThat(efentoMeasurements.get(1).getValues().getAsJsonObject().get("temperature_1").getAsDouble()).isEqualTo(22.4);
        assertThat(efentoMeasurements.get(1).getValues().getAsJsonObject().get("humidity_2").getAsDouble()).isEqualTo(30);
        checkDefaultMeasurements(measurements, efentoMeasurements, 180 * 5, false);
    }

    @ParameterizedTest
    @MethodSource
    void checkContinuousSensor(MeasurementType measurementType, List<Integer> sampleOffsets, String property, double expectedValue) {
        long tsInSec = Instant.now().getEpochSecond();
        ProtoMeasurements measurements = ProtoMeasurements.newBuilder()
                .setSerialNum(integerToByteString(1234))
                .setCloudToken("test_token")
                .setMeasurementPeriodBase(180)
                .setMeasurementPeriodFactor(0)
                .setBatteryStatus(true)
                .setSignal(0)
                .setNextTransmissionAt(1000)
                .setTransferReason(0)
                .setHash(0)
                .addAllChannels(List.of(MeasurementsProtos.ProtoChannel.newBuilder()
                        .setType(measurementType)
                        .setTimestamp(Math.toIntExact(tsInSec))
                        .addAllSampleOffsets(sampleOffsets)
                        .build()
                ))
                .build();
        List<CoapEfentoTransportResource.EfentoTelemetry> efentoMeasurements = coapEfentoTransportResource.getEfentoMeasurements(measurements, UUID.randomUUID());
        assertThat(efentoMeasurements).hasSize(1);
        assertThat(efentoMeasurements.get(0).getTs()).isEqualTo(tsInSec * 1000);
        assertThat(efentoMeasurements.get(0).getValues().getAsJsonObject().get(property).getAsDouble()).isEqualTo(expectedValue);
        checkDefaultMeasurements(measurements, efentoMeasurements, 180, false);
    }

    private static Stream<Arguments> checkContinuousSensor() {
        return Stream.of(
                Arguments.of(MEASUREMENT_TYPE_TEMPERATURE, List.of(223), "temperature_1", 22.3),
                Arguments.of(MEASUREMENT_TYPE_WATER_METER, List.of(1050), "pulse_counter_water_1", 1050),
                Arguments.of(MEASUREMENT_TYPE_HUMIDITY, List.of(20), "humidity_1", 20),
                Arguments.of(MEASUREMENT_TYPE_ATMOSPHERIC_PRESSURE, List.of(1013), "pressure_1", 101.3),
                Arguments.of(MEASUREMENT_TYPE_DIFFERENTIAL_PRESSURE, List.of(500), "pressure_diff_1", 500),
                Arguments.of(MEASUREMENT_TYPE_PULSE_CNT, List.of(300), "pulse_cnt_1", 300),
                Arguments.of(MEASUREMENT_TYPE_IAQ, List.of(150), "iaq_1", 50.0),
                Arguments.of(MEASUREMENT_TYPE_ELECTRICITY_METER, List.of(1200), "watt_hour_1", 1200),
                Arguments.of(MEASUREMENT_TYPE_SOIL_MOISTURE, List.of(35), "soil_moisture_1", 35),
                Arguments.of(MEASUREMENT_TYPE_AMBIENT_LIGHT, List.of(500), "ambient_light_1", 50),
                Arguments.of(MEASUREMENT_TYPE_HIGH_PRESSURE, List.of(200000), "high_pressure_1", 200000),
                Arguments.of(MEASUREMENT_TYPE_DISTANCE_MM, List.of(1500), "distance_mm_1", 1500),
                Arguments.of(MEASUREMENT_TYPE_WATER_METER_ACC_MINOR, List.of(125), "water_cnt_acc_minor_1", 20),
                Arguments.of(MEASUREMENT_TYPE_WATER_METER_ACC_MAJOR, List.of(2500), "water_cnt_acc_major_1", 625),
                Arguments.of(MEASUREMENT_TYPE_HUMIDITY_ACCURATE, List.of(525), "humidity_relative_1", 52.5),
                Arguments.of(MEASUREMENT_TYPE_STATIC_IAQ, List.of(110), "static_iaq_1", 36),
                Arguments.of(MEASUREMENT_TYPE_CO2_EQUIVALENT, List.of(450), "co2_1", 150),
                Arguments.of(MEASUREMENT_TYPE_BREATH_VOC, List.of(220), "breath_voc_1", 73),
                Arguments.of(MEASUREMENT_TYPE_PERCENTAGE, List.of(80), "percentage_1", 80),
                Arguments.of(MEASUREMENT_TYPE_VOLTAGE, List.of(2400), "voltage_1", 2400.0),
                Arguments.of(MEASUREMENT_TYPE_CURRENT, List.of(550), "current_1", 550.0),
                Arguments.of(MEASUREMENT_TYPE_PULSE_CNT_ACC_MINOR, List.of(180), "pulse_cnt_acc_minor_1", 30),
                Arguments.of(MEASUREMENT_TYPE_PULSE_CNT_ACC_MAJOR, List.of(1200), "pulse_cnt_acc_major_1", 300),
                Arguments.of(MEASUREMENT_TYPE_ELEC_METER_ACC_MINOR, List.of(550), "elec_meter_acc_minor_1", 91),
                Arguments.of(MEASUREMENT_TYPE_ELEC_METER_ACC_MAJOR, List.of(5500), "elec_meter_acc_major_1", 1375),
                Arguments.of(MEASUREMENT_TYPE_PULSE_CNT_ACC_WIDE_MINOR, List.of(230), "pulse_cnt_acc_wide_minor_1", 38),
                Arguments.of(MEASUREMENT_TYPE_PULSE_CNT_ACC_WIDE_MAJOR, List.of(1700), "pulse_cnt_acc_wide_major_1", 425),
                Arguments.of(MEASUREMENT_TYPE_CURRENT_PRECISE, List.of(275), "current_precise_1", 275.0)
        );
    }

    @Test
    void checkBinarySensor() {
        long tsInSec = Instant.now().getEpochSecond();
        ProtoMeasurements measurements = ProtoMeasurements.newBuilder()
                .setSerialNum(integerToByteString(1234))
                .setCloudToken("test_token")
                .setMeasurementPeriodBase(180)
                .setMeasurementPeriodFactor(0)
                .setBatteryStatus(true)
                .setSignal(0)
                .setNextTransmissionAt(1000)
                .setTransferReason(0)
                .setHash(0)
                .addChannels(MeasurementsProtos.ProtoChannel.newBuilder()
                        .setType(MEASUREMENT_TYPE_OK_ALARM)
                        .setTimestamp(Math.toIntExact(tsInSec))
                        .addAllSampleOffsets(List.of(1, 1))
                        .build())
                .build();
        List<CoapEfentoTransportResource.EfentoTelemetry> efentoMeasurements = coapEfentoTransportResource.getEfentoMeasurements(measurements, UUID.randomUUID());
        assertThat(efentoMeasurements).hasSize(1);
        assertThat(efentoMeasurements.get(0).getTs()).isEqualTo(tsInSec * 1000);
        assertThat(efentoMeasurements.get(0).getValues().getAsJsonObject().get("ok_alarm_1").getAsString()).isEqualTo("ALARM");
        checkDefaultMeasurements(measurements, efentoMeasurements, 180 * 14, true);
    }

    @ParameterizedTest
    @MethodSource
    void checkBinarySensorWhenValueIsVarying(MeasurementType measurementType, String property, String expectedValueWhenOffsetNotOk, String expectedValueWhenOffsetOk) {
        long tsInSec = Instant.now().getEpochSecond();
        ProtoMeasurements measurements = ProtoMeasurements.newBuilder()
                .setSerialNum(integerToByteString(1234))
                .setCloudToken("test_token")
                .setMeasurementPeriodBase(180)
                .setMeasurementPeriodFactor(1)
                .setBatteryStatus(true)
                .setSignal(0)
                .setNextTransmissionAt(1000)
                .setTransferReason(0)
                .setHash(0)
                .addChannels(MeasurementsProtos.ProtoChannel.newBuilder()
                        .setType(measurementType)
                        .setTimestamp(Math.toIntExact(tsInSec))
                        .addAllSampleOffsets(List.of(1, -10))
                        .build())
                .build();
        List<CoapEfentoTransportResource.EfentoTelemetry> efentoMeasurements = coapEfentoTransportResource.getEfentoMeasurements(measurements, UUID.randomUUID());
        assertThat(efentoMeasurements).hasSize(2);
        assertThat(efentoMeasurements.get(0).getTs()).isEqualTo(tsInSec * 1000);
        assertThat(efentoMeasurements.get(0).getValues().getAsJsonObject().get(property).getAsString()).isEqualTo(expectedValueWhenOffsetNotOk);
        assertThat(efentoMeasurements.get(1).getTs()).isEqualTo((tsInSec + 9) * 1000);
        assertThat(efentoMeasurements.get(1).getValues().getAsJsonObject().get(property).getAsString()).isEqualTo(expectedValueWhenOffsetOk);
        checkDefaultMeasurements(measurements, efentoMeasurements, 180, true);
    }

    private static Stream<Arguments> checkBinarySensorWhenValueIsVarying() {
        return Stream.of(
                Arguments.of(MEASUREMENT_TYPE_OK_ALARM, "ok_alarm_1", "ALARM", "OK"),
                Arguments.of(MEASUREMENT_TYPE_FLOODING, "flooding_1", "WATER_DETECTED", "OK"),
                Arguments.of(MEASUREMENT_TYPE_OUTPUT_CONTROL, "output_control_1", "ON", "OFF")
        );
    }

    @Test
    void checkExceptionWhenChannelsListIsEmpty() {
        ProtoMeasurements measurements = ProtoMeasurements.newBuilder()
                .setSerialNum(integerToByteString(1234))
                .setCloudToken("test_token")
                .setMeasurementPeriodBase(180)
                .setMeasurementPeriodFactor(1)
                .setBatteryStatus(true)
                .setSignal(0)
                .setNextTransmissionAt(1000)
                .setTransferReason(0)
                .setHash(0)
                .build();
        UUID sessionId = UUID.randomUUID();

        assertThatThrownBy(() -> coapEfentoTransportResource.getEfentoMeasurements(measurements, sessionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[" + sessionId + "]: Failed to get Efento measurements, reason: channels list is empty!");
    }

    @Test
    void checkExceptionWhenValuesMapIsEmpty() {
        long tsInSec = Instant.now().getEpochSecond();
        ProtoMeasurements measurements = ProtoMeasurements.newBuilder()
                .setSerialNum(integerToByteString(1234))
                .setCloudToken("test_token")
                .setMeasurementPeriodBase(180)
                .setMeasurementPeriodFactor(1)
                .setBatteryStatus(true)
                .setSignal(0)
                .setNextTransmissionAt(1000)
                .setTransferReason(0)
                .setHash(0)
                .addChannels(MeasurementsProtos.ProtoChannel.newBuilder()
                        .setType(MEASUREMENT_TYPE_TEMPERATURE)
                        .setTimestamp(Math.toIntExact(tsInSec))
                        .build())
                .build();
        UUID sessionId = UUID.randomUUID();

        assertThatThrownBy(() -> coapEfentoTransportResource.getEfentoMeasurements(measurements, sessionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("[" + sessionId + "]: Failed to collect Efento measurements, reason, values map is empty!");
    }

    public static ByteString integerToByteString(Integer intValue) {
        // Allocate a ByteBuffer with the size of an integer (4 bytes)
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);

        // Put the integer value into the ByteBuffer
        buffer.putInt(intValue);

        // Convert the ByteBuffer to a byte array
        byte[] byteArray = buffer.array();

        // Create a ByteString from the byte array
        return ByteString.copyFrom(byteArray);
    }

    private void checkDefaultMeasurements(ProtoMeasurements incomingMeasurements,
                                          List<CoapEfentoTransportResource.EfentoTelemetry> actualEfentoMeasurements,
                                          long expectedMeasurementInterval,
                                          boolean isBinarySensor) {
        for (int i = 0; i < actualEfentoMeasurements.size(); i++) {
            CoapEfentoTransportResource.EfentoTelemetry actualEfentoMeasurement = actualEfentoMeasurements.get(i);
            assertThat(actualEfentoMeasurement.getValues().getAsJsonObject().get("serial").getAsString()).isEqualTo(CoapEfentoUtils.convertByteArrayToString(incomingMeasurements.getSerialNum().toByteArray()));
            assertThat(actualEfentoMeasurement.getValues().getAsJsonObject().get("battery").getAsString()).isEqualTo(incomingMeasurements.getBatteryStatus() ? "ok" : "low");
            MeasurementsProtos.ProtoChannel protoChannel = incomingMeasurements.getChannelsList().get(0);
            long measuredAt = isBinarySensor ?
                    TimeUnit.SECONDS.toMillis(protoChannel.getTimestamp()) + Math.abs(TimeUnit.SECONDS.toMillis(protoChannel.getSampleOffsetsList().get(i))) - 1000 :
                    TimeUnit.SECONDS.toMillis(protoChannel.getTimestamp() + i * expectedMeasurementInterval);
            assertThat(actualEfentoMeasurement.getValues().getAsJsonObject().get("measured_at").getAsString()).isEqualTo(convertTimestampToUtcString(measuredAt));
            assertThat(actualEfentoMeasurement.getValues().getAsJsonObject().get("next_transmission_at").getAsString()).isEqualTo(convertTimestampToUtcString(TimeUnit.SECONDS.toMillis(incomingMeasurements.getNextTransmissionAt())));
            assertThat(actualEfentoMeasurement.getValues().getAsJsonObject().get("signal").getAsLong()).isEqualTo(incomingMeasurements.getSignal());
            assertThat(actualEfentoMeasurement.getValues().getAsJsonObject().get("measurement_interval").getAsDouble()).isEqualTo(expectedMeasurementInterval);
        }
    }

}
