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
package org.thingsboard.mqtt.integration;

import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.thingsboard.common.util.AbstractListeningExecutor;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.integration.server.MqttServer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ResourceLock("port8885") // test MQTT server port
@Slf4j
public class MqttIntegrationTest {

    static final String MQTT_HOST = "localhost";
    static final int KEEPALIVE_TIMEOUT_SECONDS = 2;
    static final long RECONNECT_DELAY_SECONDS = 10L;

    EventLoopGroup eventLoopGroup;
    MqttServer mqttServer;

    MqttClient mqttClient;

    AbstractListeningExecutor handlerExecutor;

    @BeforeEach
    public void init() throws Exception {
        this.handlerExecutor = new AbstractListeningExecutor() {
            @Override
            protected int getThreadPollSize() {
                return 4;
            }
        };
        handlerExecutor.init();

        this.eventLoopGroup = new NioEventLoopGroup();

        this.mqttServer = new MqttServer();
        this.mqttServer.init();
    }

    @AfterEach
    public void destroy() throws InterruptedException {
        if (this.mqttClient != null) {
            this.mqttClient.disconnect();
        }
        if (this.mqttServer != null) {
            this.mqttServer.shutdown();
        }
        if (this.eventLoopGroup != null) {
            this.eventLoopGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        }
        if (this.handlerExecutor != null) {
            this.handlerExecutor.destroy();
        }
    }

    @Test
    public void givenActiveMqttClient_whenNoActivityForKeepAliveTimeout_thenDisconnectClient() throws Throwable {
        //given
        this.mqttClient = initClient();

        log.warn("Sending publish messages...");
        CountDownLatch latch = new CountDownLatch(3);
        for (int i = 0; i < 3; i++) {
            Thread.sleep(30);
            Future<Void> pubFuture = publishMsg();
            pubFuture.addListener(future -> latch.countDown());
        }

        log.warn("Waiting for messages acknowledgments...");
        boolean awaitResult = latch.await(10, TimeUnit.SECONDS);
        Assertions.assertTrue(awaitResult);
        log.warn("Messages are delivered successfully...");

        //when
        log.warn("Starting idle period...");
        Thread.sleep(5000);

        //then
        List<MqttMessageType> allReceivedEvents = this.mqttServer.getEventsFromClient();
        long disconnectCount = allReceivedEvents.stream().filter(type -> type == MqttMessageType.DISCONNECT).count();

        Assertions.assertEquals(1, disconnectCount);
    }

    private Future<Void> publishMsg() {
        return this.mqttClient.publish(
                "test/topic",
                Unpooled.wrappedBuffer("payload".getBytes(StandardCharsets.UTF_8)),
                MqttQoS.AT_MOST_ONCE);
    }

    private MqttClient initClient() throws Exception {
        MqttClientConfig config = new MqttClientConfig();
        config.setOwnerId("MqttIntegrationTest");
        config.setTimeoutSeconds(KEEPALIVE_TIMEOUT_SECONDS);
        config.setReconnectDelay(RECONNECT_DELAY_SECONDS);
        MqttClient client = MqttClient.create(config, null, handlerExecutor);
        client.setEventLoop(this.eventLoopGroup);
        Promise<MqttConnectResult> connectFuture = client.connect(MQTT_HOST, this.mqttServer.getMqttPort());

        String hostPort = MQTT_HOST + ":" + this.mqttServer.getMqttPort();
        MqttConnectResult result;
        try {
            result = connectFuture.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            connectFuture.cancel(true);
            client.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT server at %s.", hostPort));
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            client.disconnect();
            throw new RuntimeException(String.format("Failed to connect to MQTT server at %s. Result code is: %s", hostPort, result.getReturnCode()));
        }
        return client;
    }
}