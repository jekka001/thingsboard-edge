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
package org.thingsboard.integration.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Promise;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.IntegrationContext;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.MqttHandler;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.msg.TbMsg;

import javax.net.ssl.SSLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Created by ashvayka on 25.12.17.
 */
@Slf4j
public abstract class AbstractMqttIntegration<T extends MqttIntegrationMsg> extends AbstractIntegration<T> {

    protected MqttClientConfiguration mqttClientConfiguration;
    protected MqttClient mqttClient;

    public void setMqttClient(MqttClient mqttClient) {
        this.mqttClient = mqttClient;
    }

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        mqttClientConfiguration = getClientConfiguration(configuration, MqttClientConfiguration.class);
        setupConfiguration(mqttClientConfiguration);
        if (mqttClientConfiguration.getConnectTimeoutSec() < 1) {
            mqttClientConfiguration.setConnectTimeoutSec(10);
        }
    }

    @Override
    protected void doValidateConfiguration(JsonNode configuration, boolean allowLocalNetworkHosts) {
        MqttClientConfiguration mqttClientConfiguration;
        try {
            mqttClientConfiguration = getClientConfiguration(configuration.get("clientConfiguration"), MqttClientConfiguration.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid MQTT Integration Configuration structure!");
        }
        if (!allowLocalNetworkHosts && isLocalNetworkHost(mqttClientConfiguration.getHost())) {
            throw new IllegalArgumentException("Usage of local network host for MQTT broker connection is not allowed!");
        }
        String clientId = mqttClientConfiguration.getClientId();
        if (StringUtils.isNotBlank(clientId) && clientId.length() > 23) {
            throw new IllegalArgumentException("Client ID is too long '" + clientId + "'. " +
                    "The length of Client ID cannot be longer than 23, but current length is " + clientId.length() + ".");
        }
    }

    protected void setupConfiguration(MqttClientConfiguration mqttClientConfiguration) {
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        if (mqttClient != null) {
            sendUnsubscribeRequestsIfNeeded(params.getConfiguration());
            mqttClient.disconnect();
        }
        init(params);
    }

    void sendUnsubscribeRequestsIfNeeded(Integration updatedIntegration) throws JsonProcessingException {
        MqttClientConfiguration newMqttClientConfiguration = getClientConfiguration(updatedIntegration, MqttClientConfiguration.class);
        if (isClientPersistedWithSpecifiedClientId(newMqttClientConfiguration)) {
            Set<String> oldTopics = getOldTopics(updatedIntegration);
            oldTopics.forEach(topic -> unsubscribe(topic, updatedIntegration));
        }
    }

    private boolean isClientPersistedWithSpecifiedClientId(MqttClientConfiguration newMqttClientConfiguration) {
        return !newMqttClientConfiguration.isCleanSession() && StringUtils.isNotEmpty(newMqttClientConfiguration.getClientId());
    }

    private Set<String> getOldTopics(Integration updatedIntegration) throws JsonProcessingException {
        Set<String> oldTopics = getTopics(configuration);
        Set<String> newTopics = getTopics(updatedIntegration);

        oldTopics.removeAll(newTopics);
        return oldTopics;
    }

    private Set<String> getTopics(Integration configuration) throws JsonProcessingException {
        List<MqttTopicFilter> topicFilters = getMqttTopicFilters(configuration);
        return topicFilters.stream().map(MqttTopicFilter::getFilter).collect(Collectors.toSet());
    }

    protected List<MqttTopicFilter> getMqttTopicFilters(Integration configuration) throws JsonProcessingException {
        return JacksonUtil.fromString(JacksonUtil.toString(configuration.getConfiguration().get("topicFilters")),
                new TypeReference<>() {
                });
    }

    private void unsubscribe(String topic, Integration updatedIntegration) {
        try {
            mqttClient.off(topic).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[{}] Failed to unsubscribe to the following topic: {}", updatedIntegration.getName(), topic, e);
        }
    }

    @Override
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
        }
    }

    @Override
    public void process(T msg) {
        throw new RuntimeException("MQTT Integration does not support sync processing");
    }

    @Override
    public ListenableFuture<Void> processAsync(T msg) {
        log.debug("Received the message for the topic: {}", msg.getTopic());
        ListenableFuture<Void> future = doProcess(context, msg);
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                integrationStatistics.incMessagesProcessed();
                log.debug("Successfully processed the message for the topic: {}", msg.getTopic());
                persistDebug(msg, "OK", null);
            }

            @Override
            public void onFailure(Throwable t) {
                integrationStatistics.incErrorsOccurred();
                log.debug("Failed to apply data converter function: {}", t.getMessage(), t);
                persistDebug(msg, "ERROR", t);
            }
        }, MoreExecutors.directExecutor());
        return future;
    }

    private void persistDebug(T msg, String status, Throwable exception) {
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", getDefaultUplinkContentType(), JacksonUtil.toString(msg.toJson()), status, exception);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

    @Override
    public void onDownlinkMsg(IntegrationDownlinkMsg downlink) {
        TbMsg msg = downlink.getTbMsg();
        logDownlink(context, "Downlink: " + msg.getType(), msg);
        if (downlinkConverter != null) {
            processDownLinkMsg(context, msg);
        }
    }

    protected void processDownLinkMsg(IntegrationContext context, TbMsg msg) {
        String status = "OK";
        Exception exception = null;
        try {
            if (doProcessDownLinkMsg(context, msg)) {
                integrationStatistics.incMessagesProcessed();
            }
        } catch (Exception e) {
            log.warn("Failed to process downLink message", e);
            exception = e;
            status = "ERROR";
        }
        reportDownlinkError(context, msg, status, exception);
    }

    protected abstract boolean doProcessDownLinkMsg(IntegrationContext context, TbMsg msg) throws Exception;

    protected abstract ListenableFuture<Void> doProcess(IntegrationContext context, T msg);

    protected MqttClient initClient(String ownerId, MqttClientConfiguration configuration, MqttHandler defaultHandler) throws Exception {
        Optional<SslContext> sslContextOpt = initSslContext(configuration);

        MqttClientConfig config = sslContextOpt.isPresent() ? new MqttClientConfig(sslContextOpt.get()) : new MqttClientConfig();
        config.setOwnerId(ownerId);
        if (!StringUtils.isEmpty(configuration.getClientId())) {
            config.setClientId(configuration.getClientId());
        }

        if (configuration.getMaxBytesInMessage() != null) {
            config.setMaxBytesInMessage(configuration.getMaxBytesInMessage());
        }
        config.setCleanSession(configuration.isCleanSession());

        configuration.getCredentials().configure(config);

        ListeningExecutorService listeningExecutorService = MoreExecutors.listeningDecorator(context.getExecutorService());
        ListeningExecutor handlerExecutor = new ListeningExecutor() {
            @Override
            public <T> ListenableFuture<T> executeAsync(Callable<T> task) {
                return listeningExecutorService.submit(task);
            }

            @Override
            public void execute(Runnable command) {
                listeningExecutorService.execute(command);
            }
        };

        MqttClient client = MqttClient.create(config, defaultHandler, handlerExecutor);
        client.setEventLoop(context.getEventLoopGroup());
        Promise<MqttConnectResult> connectFuture = client.connect(configuration.getHost(), configuration.getPort());
        MqttConnectResult result;
        try {
            result = connectFuture.get(configuration.getConnectTimeoutSec(), TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            connectFuture.cancel(true);
            client.disconnect();
            String hostPort = configuration.getHost() + ":" + configuration.getPort();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s.", hostPort));
        }
        if (!result.isSuccess()) {
            connectFuture.cancel(true);
            client.disconnect();
            String hostPort = configuration.getHost() + ":" + configuration.getPort();
            throw new RuntimeException(String.format("Failed to connect to MQTT broker at %s. Result code is: %s", hostPort, result.getReturnCode()));
        }
        return client;
    }

    protected Optional<SslContext> initSslContext(MqttClientConfiguration configuration) throws SSLException {
        Optional<SslContext> result = configuration.getCredentials().initSslContext();
        if (configuration.isSsl() && !result.isPresent()) {
            result = Optional.of(SslContextBuilder.forClient().build());
        }
        return result;
    }

}
