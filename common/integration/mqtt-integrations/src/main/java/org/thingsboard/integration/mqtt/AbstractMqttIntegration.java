/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.IntegrationContext;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.mqtt.MqttClient;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.mqtt.MqttConnectResult;
import org.thingsboard.mqtt.MqttHandler;
import org.thingsboard.server.common.msg.TbMsg;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by ashvayka on 25.12.17.
 */
@Slf4j
public abstract class AbstractMqttIntegration<T extends MqttIntegrationMsg> extends AbstractIntegration<T> {

    protected MqttClientConfiguration mqttClientConfiguration;
    protected MqttClient mqttClient;
    protected IntegrationContext ctx;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        this.ctx = params.getContext();
        mqttClientConfiguration = mapper.readValue(
                mapper.writeValueAsString(configuration.getConfiguration().get("clientConfiguration")),
                MqttClientConfiguration.class);
        setupConfiguration(mqttClientConfiguration);
    }

    @Override
    protected void doValidateConfiguration(JsonNode configuration, boolean allowLocalNetworkHosts) {
        MqttClientConfiguration mqttClientConfiguration;
        try {
            mqttClientConfiguration = mapper.readValue(
                    mapper.writeValueAsString(configuration.get("clientConfiguration")),
                    MqttClientConfiguration.class);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid MQTT Integration Configuration structure!");
        }
        if (!allowLocalNetworkHosts && isLocalNetworkHost(mqttClientConfiguration.getHost())) {
            throw new IllegalArgumentException("Usage of local network host for MQTT broker connection is not allowed!");
        }
    }

    protected void setupConfiguration(MqttClientConfiguration mqttClientConfiguration) {
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        if (mqttClient != null) {
            mqttClient.disconnect();
        }
        init(params);
    }

    @Override
    public void destroy() {
        if (mqttClient != null) {
            mqttClient.disconnect();
        }
    }

    @Override
    public void process(T msg) {
        String status = "OK";
        Exception exception = null;
        try {
            doProcess(context, msg);
            integrationStatistics.incMessagesProcessed();
        } catch (Exception e) {
            log.debug("Failed to apply data converter function: {}", e.getMessage(), e);
            exception = e;
            status = "ERROR";
        }
        if (!status.equals("OK")) {
            integrationStatistics.incErrorsOccurred();
        }
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", getUplinkContentType(), mapper.writeValueAsString(msg.toJson()), status, exception);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

    @Override
    public void onDownlinkMsg(IntegrationDownlinkMsg downlink){
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

    protected abstract void doProcess(IntegrationContext context, T msg) throws Exception;

    protected MqttClient initClient(MqttClientConfiguration configuration, MqttHandler defaultHandler) throws Exception {
        Optional<SslContext> sslContextOpt = initSslContext(configuration);

        MqttClientConfig config = sslContextOpt.isPresent() ? new MqttClientConfig(sslContextOpt.get()) : new MqttClientConfig();
        if (!StringUtils.isEmpty(configuration.getClientId())) {
            config.setClientId(configuration.getClientId());
        }
        config.setCleanSession(configuration.isCleanSession());

        configuration.getCredentials().configure(config);

        MqttClient client = MqttClient.create(config, defaultHandler);
        client.setEventLoop(context.getEventLoopGroup());
        Future<MqttConnectResult> connectFuture = client.connect(configuration.getHost(), configuration.getPort());
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
