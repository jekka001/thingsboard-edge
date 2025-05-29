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
package org.thingsboard.mqtt;

import com.google.common.util.concurrent.Futures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.thingsboard.common.util.AbstractListeningExecutor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
class MqttClientTest {

    final int randomPort = 0;

    @Container
    HiveMQContainer broker = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce").withTag("2025.2"));

    MqttTestProxy proxy;

    MqttClient client;

    AbstractListeningExecutor handlerExecutor;

    @BeforeAll
    static void init() {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    @BeforeEach
    void setup() {
        handlerExecutor = new AbstractListeningExecutor() {
            @Override
            protected int getThreadPollSize() {
                return 1;
            }
        };
        handlerExecutor.init();
    }

    @AfterEach
    void cleanup() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        if (proxy != null) {
            proxy.stop();
            proxy = null;
        }
        handlerExecutor.destroy();
        handlerExecutor = null;
    }

    @Test
    void testConnectToBroker() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[ConnectToBroker]");
        clientConfig.setClientId("connect");

        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), broker.getMqttPort());

        // THEN
        assertThat(connectFuture).isNotNull();

        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isDone);

        assertThat(connectFuture.isSuccess()).isTrue();

        MqttConnectResult actualConnectResult = connectFuture.getNow();
        assertThat(actualConnectResult).isNotNull();
        assertThat(actualConnectResult.isSuccess()).isTrue();
        assertThat(actualConnectResult.getReturnCode()).isEqualTo(MqttConnectReturnCode.CONNECTION_ACCEPTED);

        assertThat(client.isConnected()).isTrue();
    }

    @Test
    void testDisconnectFromBroker() {
        // GIVEN
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[ConnectToBroker]");
        clientConfig.setClientId("connect");

        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN
        Promise<MqttConnectResult> connectFuture = client.connect(broker.getHost(), broker.getMqttPort());

        // THEN
        assertThat(connectFuture).isNotNull();

        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isDone);

        assertThat(connectFuture.isSuccess()).isTrue();

        // WHEN
        client.disconnect();

        // THEN
        Awaitility.await("waiting for client to disconnect")
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
    }

    @Test
    void testDisconnectDueToKeepAliveIfNoActivity() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> msg.fixedHeader().messageType() != MqttMessageType.PINGRESP) // drop all ping responses to simulate broker down
                .build();

        int idleTimeoutSeconds = 2;

        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[KeepAliveDisconnect]");
        clientConfig.setClientId("no-activity-disconnect");
        clientConfig.setTimeoutSeconds(idleTimeoutSeconds);
        clientConfig.setReconnect(false); // disable auto reconnect
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // WHEN-THEN
        connect(broker.getHost(), proxy.getPort());

        // no activity...

        Awaitility.await("waiting for client to disconnect")
                .pollDelay(Duration.ofSeconds(idleTimeoutSeconds * 2)) // 2 seconds to wait for the first idle event and then 2 seconds for scheduled disconnect to fire
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(client.isConnected()).isFalse());
    }

    @Test
    void testRetransmission() {
        // GIVEN
        proxy = MqttTestProxy.builder()
                .localPort(randomPort)
                .brokerHost(broker.getHost())
                .brokerPort(broker.getMqttPort())
                .brokerToClientInterceptor(msg -> msg.fixedHeader().messageType() != MqttMessageType.PUBACK) // drop all pubacks to allow retransmission to happen
                .build();

        // create client
        var clientConfig = new MqttClientConfig();
        clientConfig.setOwnerId("Test[Retransmission]");
        clientConfig.setClientId("retransmission");
        clientConfig.setRetransmissionConfig(new MqttClientConfig.RetransmissionConfig(1, 1000L, 0d));
        client = MqttClient.create(clientConfig, null, handlerExecutor);

        // connect to a broker
        connect(broker.getHost(), proxy.getPort());

        // subscribe to a topic
        String topic = "test-topic";
        List<ByteBuf> receivedMessages = Collections.synchronizedList(new ArrayList<>(2));
        Future<Void> subscribeFuture = client.on(topic, (__, payload) -> {
            receivedMessages.add(payload);
            return Futures.immediateVoidFuture();
        });
        Awaitility.await("waiting for client to subscribe to a topic")
                .atMost(Duration.ofSeconds(10L))
                .until(subscribeFuture::isDone);

        // WHEN
        // publish a message
        ByteBuf message = PooledByteBufAllocator.DEFAULT.buffer().writeBytes("test message".getBytes(StandardCharsets.UTF_8));
        client.publish(topic, message, MqttQoS.AT_LEAST_ONCE);

        // THEN
        // wait enough time so that retransmission happens and stops
        // if retransmission works incorrectly waiting 10 seconds allows for additional retransmissions to happen
        try {
            Awaitility.await("wait up to 10s, stop early if too many messages")
                    .atMost(Duration.ofSeconds(10L))
                    .pollInterval(Duration.ofMillis(100))
                    .until(() -> receivedMessages.size() > 2);
        } catch (ConditionTimeoutException __) {
            // didn't exceed 2 messages
        }

        assertThat(receivedMessages).size().describedAs("incorrect number of messages received, expected 2 (original plus one retransmitted)").isEqualTo(2);
    }

    private void connect(String host, int port) {
        Promise<MqttConnectResult> connectFuture = client.connect(host, port);
        Awaitility.await("waiting for client to connect")
                .atMost(Duration.ofSeconds(10L))
                .until(connectFuture::isSuccess);
    }

}
