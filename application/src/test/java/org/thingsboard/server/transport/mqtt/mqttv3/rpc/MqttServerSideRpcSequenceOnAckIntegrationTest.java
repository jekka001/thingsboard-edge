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
package org.thingsboard.server.transport.mqtt.mqttv3.rpc;

import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.TestPropertySource;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.transport.mqtt.MqttTestConfigProperties;

@Slf4j
@DaoSqlTest
@TestPropertySource(properties = {
        "actors.rpc.submit_strategy=SEQUENTIAL_ON_ACK_FROM_DEVICE",
})
public class MqttServerSideRpcSequenceOnAckIntegrationTest extends AbstractMqttServerSideRpcIntegrationTest {

    @Before
    public void beforeTest() throws Exception {
        MqttTestConfigProperties configProperties = MqttTestConfigProperties.builder()
                .deviceName("RPC test device")
                .gatewayName("RPC test gateway")
                .build();
        processBeforeTest(configProperties);
    }

    @Test
    public void testSequenceServerMqttOneWayRpcQoSAtMostOnce() throws Exception {
        processSequenceOneWayRpcTest(MqttQoS.AT_MOST_ONCE);
    }

    @Test
    public void testSequenceServerMqttOneWayRpcQoSAtLeastOnce() throws Exception {
        processSequenceOneWayRpcTest(MqttQoS.AT_LEAST_ONCE);
    }

    @Test
    public void testSequenceServerMqttTwoWayRpcQoSAtMostOnce() throws Exception {
        processSequenceTwoWayRpcTest(MqttQoS.AT_MOST_ONCE);
    }

    @Test
    public void testSequenceServerMqttTwoWayRpcQoSAtLeastOnce() throws Exception {
        processSequenceTwoWayRpcTest(MqttQoS.AT_LEAST_ONCE);
    }

    @Test
    public void testSequenceServerMqttTwoWayRpcQoSAtMostOnceWithManualAcksEnabled() throws Exception {
        processSequenceTwoWayRpcTest(MqttQoS.AT_MOST_ONCE, true);
    }

    @Test
    public void testSequenceServerMqttTwoWayRpcQoSAtLeastOnceWithoutManualAcksEnabled() throws Exception {
        processSequenceTwoWayRpcTest(MqttQoS.AT_LEAST_ONCE, true);
    }

}
