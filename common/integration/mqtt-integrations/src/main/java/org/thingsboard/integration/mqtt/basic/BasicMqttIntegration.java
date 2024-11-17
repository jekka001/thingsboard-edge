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
package org.thingsboard.integration.mqtt.basic;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttQoS;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.DebugModeUtil;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.TbStopWatch;
import org.thingsboard.integration.api.IntegrationContext;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.DownlinkData;
import org.thingsboard.integration.api.data.IntegrationMetaData;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.api.data.UplinkMetaData;
import org.thingsboard.integration.mqtt.AbstractMqttIntegration;
import org.thingsboard.integration.mqtt.BasicMqttIntegrationMsg;
import org.thingsboard.integration.mqtt.MqttClientConfiguration;
import org.thingsboard.integration.mqtt.MqttTopicFilter;
import org.thingsboard.mqtt.MqttClientCallback;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.msg.TbMsg;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by ashvayka on 25.12.17.
 */
@Slf4j
public class BasicMqttIntegration extends AbstractMqttIntegration<BasicMqttIntegrationMsg> {

    protected String downlinkTopicPattern = "${topic}";

    private static final String DEFAULT_DOWNLINK_TOPIC_PATTERN = "${topic}";

    private volatile WeakReference<ListenableFuture<?>> subscribeFuture = new WeakReference<>(Futures.immediateVoidFuture());

    String getOwnerId(Integration configuration) {
        return "Tenant[" + configuration.getTenantId().getId() + "]Integration[" + configuration.getId().getId() + "]";
    }

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        if (!this.configuration.isEnabled()) {
            return;
        }
        log.debug("[{}][{}] MQTT Integration initializing MQTT client", configuration.getId(), configuration.getName());
        mqttClient = initClient(getOwnerId(this.configuration), mqttClientConfiguration, (topic, data) -> processAsync(new BasicMqttIntegrationMsg(topic, data)));
        subscribeToTopics();
        this.downlinkTopicPattern = getDownlinkTopicPattern();
        this.mqttClient.setCallback(new MqttClientCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                log.info("[{}][{}] MQTT Integration lost connection to the target broker", configuration.getId(), configuration.getName());
            }

            @Override
            public void onSuccessfulReconnect() {
                log.info("[{}][{}] MQTT Integration successfully reconnected to the target broker", configuration.getId(), configuration.getName());
                Optional.ofNullable(subscribeFuture.get()).ifPresent(f -> f.cancel(true));
                var future = mqttClient.getHandlerExecutor().submit(() -> {
                    try {
                        subscribeToTopics();
                    } catch (IOException e) {
                        log.info("[{}][{}] MQTT Integration failed to subscribe to topics", configuration.getId(), configuration.getName());
                    }
                });
                subscribeFuture = new WeakReference<>(future);
            }
        });
    }

    @Override
    public void doCheckConnection(Integration integration, IntegrationContext ctx) throws ThingsboardException {
        context = ctx;
        this.configuration = integration;
        try {
            mqttClientConfiguration = getClientConfiguration(configuration, MqttClientConfiguration.class);
            log.debug("mqttClientConfiguration from JSON: {}", mqttClientConfiguration);
            if (mqttClientConfiguration.getConnectTimeoutSec() > ctx.getIntegrationConnectTimeoutSec() && ctx.getIntegrationConnectTimeoutSec() > 0) {
                log.debug("Reduce connection timeout sec down to the limit [{}]", mqttClientConfiguration.getConnectTimeoutSec());
                mqttClientConfiguration.setConnectTimeoutSec(ctx.getIntegrationConnectTimeoutSec());
            }
            mqttClient = initClient(getOwnerId(integration), mqttClientConfiguration, (topic, data) -> processAsync(new BasicMqttIntegrationMsg(topic, data)));
        } catch (RuntimeException e) {
            throw new ThingsboardException(e.getMessage(), ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    private void subscribeToTopics() throws java.io.IOException {
        List<MqttTopicFilter> topics = getMqttTopicFilters(configuration);

        for (MqttTopicFilter topicFilter : topics) {
            mqttClient.on(topicFilter.getFilter(), (topic, data) ->
                    processAsync(new BasicMqttIntegrationMsg(topic, data)), MqttQoS.valueOf(topicFilter.getQos()));
        }
    }

    protected String getDownlinkTopicPattern() {
        String downlinkTopicPattern = null;
        if (configuration.getConfiguration().has("downlinkTopicPattern")) {
            downlinkTopicPattern = configuration.getConfiguration().get("downlinkTopicPattern").asText();
        }
        if (StringUtils.isEmpty(downlinkTopicPattern)) {
            downlinkTopicPattern = DEFAULT_DOWNLINK_TOPIC_PATTERN;
        }
        return downlinkTopicPattern;
    }

    @Override
    protected List<UplinkData> convertToUplinkDataList(IntegrationContext context, byte[] data, UplinkMetaData md) throws Exception {
        throw new RuntimeException("MQTT integrations does not support blocking call on convertToUplinkDataList, use convertToUplinkDataListAsync instead");
    }

    @Override
    protected ListenableFuture<Void> doProcess(IntegrationContext context, BasicMqttIntegrationMsg msg) {
        Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
        mdMap.put("topic", msg.getTopic());

        var stopWatch = TbStopWatch.create();
        ListenableFuture<List<UplinkData>> uplinkDataListFuture = convertToUplinkDataListAsync(context, msg.getPayload(), new UplinkMetaData(msg.getContentType(), mdMap));
        ListenableFuture<Void> future = Futures.transform(uplinkDataListFuture, (uplinkDataList) -> {
            if (log.isDebugEnabled()) {
                log.debug("convertToUplinkDataList took {}ms for integration {}", stopWatch.stopAndGetTotalTimeMillis(), configuration.getName());
            }

            if (uplinkDataList != null) {
                for (UplinkData data : uplinkDataList) {
                    processUplinkData(context, data);
                    log.trace("[{}] Processing uplink data: {}", configuration.getId(), data);
                }
            }
            return null;
        }, MoreExecutors.directExecutor());
        return future;
    }

    @Override
    protected boolean doProcessDownLinkMsg(IntegrationContext context, TbMsg msg) throws Exception {
        Map<String, List<DownlinkData>> topicToDataMap = convertDownLinkMsg(context, msg);
        for (Map.Entry<String, List<DownlinkData>> topicEntry : topicToDataMap.entrySet()) {
            for (DownlinkData data : topicEntry.getValue()) {
                String topic = topicEntry.getKey();
                logMqttDownlink(context, topic, data);
                mqttClient.publish(topic, Unpooled.wrappedBuffer(data.getData()), MqttQoS.AT_LEAST_ONCE, mqttClientConfiguration.isRetainedMessage());
            }
        }
        return !topicToDataMap.isEmpty();
    }

    private Map<String, List<DownlinkData>> convertDownLinkMsg(IntegrationContext context, TbMsg msg) throws Exception {
        Map<String, List<DownlinkData>> topicToDataMap = new HashMap<>();
        Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
        List<DownlinkData> result = downlinkConverter.convertDownLink(context.getDownlinkConverterContext(), Collections.singletonList(msg), new IntegrationMetaData(mdMap));
        for (DownlinkData data : result) {
            if (!data.isEmpty()) {
                String downlinkTopic = compileDownlinkTopic(data.getMetadata());
                topicToDataMap.computeIfAbsent(downlinkTopic, k -> new ArrayList<>()).add(data);
            }
        }
        return topicToDataMap;
    }

    private String compileDownlinkTopic(Map<String, String> md) {
        if (md != null) {
            String result = downlinkTopicPattern;
            for (Map.Entry<String, String> mdEntry : md.entrySet()) {
                String key = "${" + mdEntry.getKey() + "}";
                result = result.replace(key, mdEntry.getValue());
            }
            return result;
        }
        return downlinkTopicPattern;
    }

    private void logMqttDownlink(IntegrationContext context, String topic, DownlinkData data) {
        String status = downlinkConverter != null ? "OK" : "FAILURE";
        if (DebugModeUtil.isDebugAvailable(configuration, status)) {
            try {
                ObjectNode json = JacksonUtil.newObjectNode();
                json.put("topic", topic);
                json.set("payload", getDownlinkPayloadJson(data));
                persistDebug(context, "Downlink", "JSON", JacksonUtil.toString(json), status, null);
            } catch (Exception e) {
                log.warn("Failed to persist debug message", e);
            }
        }
    }

}
