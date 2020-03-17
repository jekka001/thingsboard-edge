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
package org.thingsboard.server.common.transport.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.gen.transport.ClaimDeviceMsg;
import org.thingsboard.server.gen.transport.GetAttributeRequestMsg;
import org.thingsboard.server.gen.transport.GetOrCreateDeviceFromGatewayRequestMsg;
import org.thingsboard.server.gen.transport.GetOrCreateDeviceFromGatewayResponseMsg;
import org.thingsboard.server.gen.transport.PostAttributeMsg;
import org.thingsboard.server.gen.transport.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.SessionEventMsg;
import org.thingsboard.server.gen.transport.SessionInfoProto;
import org.thingsboard.server.gen.transport.SubscribeToAttributeUpdatesMsg;
import org.thingsboard.server.gen.transport.SubscribeToRPCMsg;
import org.thingsboard.server.gen.transport.SubscriptionInfoProto;
import org.thingsboard.server.gen.transport.ToDeviceRpcResponseMsg;
import org.thingsboard.server.gen.transport.ToRuleEngineMsg;
import org.thingsboard.server.gen.transport.ToServerRpcRequestMsg;
import org.thingsboard.server.gen.transport.ToTransportMsg;
import org.thingsboard.server.gen.transport.TransportApiRequestMsg;
import org.thingsboard.server.gen.transport.TransportApiResponseMsg;
import org.thingsboard.server.gen.transport.TransportToDeviceActorMsg;
import org.thingsboard.server.gen.transport.ValidateDeviceCredentialsResponseMsg;
import org.thingsboard.server.gen.transport.ValidateDeviceTokenRequestMsg;
import org.thingsboard.server.gen.transport.ValidateDeviceX509CertRequestMsg;
import org.thingsboard.server.kafka.AsyncCallbackTemplate;
import org.thingsboard.server.kafka.TBKafkaAdmin;
import org.thingsboard.server.kafka.TBKafkaConsumerTemplate;
import org.thingsboard.server.kafka.TBKafkaProducerTemplate;
import org.thingsboard.server.kafka.TbKafkaRequestTemplate;
import org.thingsboard.server.kafka.TbKafkaSettings;
import org.thingsboard.server.kafka.TbNodeIdProvider;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by ashvayka on 05.10.18.
 */
@ConditionalOnExpression("'${transport.type:null}'=='null'")
@Service
@Slf4j
public class RemoteTransportService extends AbstractTransportService {

    @Value("${kafka.rule_engine.topic}")
    private String ruleEngineTopic;
    @Value("${kafka.notifications.topic}")
    private String notificationsTopic;
    @Value("${kafka.notifications.poll_interval}")
    private int notificationsPollDuration;
    @Value("${kafka.notifications.auto_commit_interval}")
    private int notificationsAutoCommitInterval;
    @Value("${kafka.transport_api.requests_topic}")
    private String transportApiRequestsTopic;
    @Value("${kafka.transport_api.responses_topic}")
    private String transportApiResponsesTopic;
    @Value("${kafka.transport_api.max_pending_requests}")
    private long maxPendingRequests;
    @Value("${kafka.transport_api.max_requests_timeout}")
    private long maxRequestsTimeout;
    @Value("${kafka.transport_api.response_poll_interval}")
    private int responsePollDuration;
    @Value("${kafka.transport_api.response_auto_commit_interval}")
    private int autoCommitInterval;

    @Autowired
    private TbKafkaSettings kafkaSettings;
    //We use this to get the node id. We should replace this with a component that provides the node id.
    @Autowired
    private TbNodeIdProvider nodeIdProvider;

    private TbKafkaRequestTemplate<TransportApiRequestMsg, TransportApiResponseMsg> transportApiTemplate;
    private TBKafkaProducerTemplate<ToRuleEngineMsg> ruleEngineProducer;
    private TBKafkaConsumerTemplate<ToTransportMsg> mainConsumer;

    private ExecutorService mainConsumerExecutor = Executors.newSingleThreadExecutor();

    private volatile boolean stopped = false;

    @PostConstruct
    public void init() {
        super.init();

        TBKafkaProducerTemplate.TBKafkaProducerTemplateBuilder<TransportApiRequestMsg> requestBuilder = TBKafkaProducerTemplate.builder();
        requestBuilder.settings(kafkaSettings);
        requestBuilder.clientId("producer-transport-api-request-" + nodeIdProvider.getNodeId());
        requestBuilder.defaultTopic(transportApiRequestsTopic);
        requestBuilder.encoder(new TransportApiRequestEncoder());

        TBKafkaConsumerTemplate.TBKafkaConsumerTemplateBuilder<TransportApiResponseMsg> responseBuilder = TBKafkaConsumerTemplate.builder();
        responseBuilder.settings(kafkaSettings);
        responseBuilder.topic(transportApiResponsesTopic + "." + nodeIdProvider.getNodeId());
        responseBuilder.clientId("transport-api-client-" + nodeIdProvider.getNodeId());
        responseBuilder.groupId("transport-api-client");
        responseBuilder.autoCommit(true);
        responseBuilder.autoCommitIntervalMs(autoCommitInterval);
        responseBuilder.decoder(new TransportApiResponseDecoder());

        TbKafkaRequestTemplate.TbKafkaRequestTemplateBuilder
                <TransportApiRequestMsg, TransportApiResponseMsg> builder = TbKafkaRequestTemplate.builder();
        builder.requestTemplate(requestBuilder.build());
        builder.responseTemplate(responseBuilder.build());
        builder.maxPendingRequests(maxPendingRequests);
        builder.maxRequestTimeout(maxRequestsTimeout);
        builder.pollInterval(responsePollDuration);
        transportApiTemplate = builder.build();
        transportApiTemplate.init();

        TBKafkaProducerTemplate.TBKafkaProducerTemplateBuilder<ToRuleEngineMsg> ruleEngineProducerBuilder = TBKafkaProducerTemplate.builder();
        ruleEngineProducerBuilder.settings(kafkaSettings);
        ruleEngineProducerBuilder.clientId("producer-rule-engine-request-" + nodeIdProvider.getNodeId());
        ruleEngineProducerBuilder.defaultTopic(ruleEngineTopic);
        ruleEngineProducerBuilder.encoder(new ToRuleEngineMsgEncoder());
        ruleEngineProducer = ruleEngineProducerBuilder.build();
        ruleEngineProducer.init();

        String notificationsTopicName = notificationsTopic + "." + nodeIdProvider.getNodeId();

        try {
            TBKafkaAdmin admin = new TBKafkaAdmin(kafkaSettings);
            CreateTopicsResult result = admin.createTopic(new NewTopic(notificationsTopicName, 1, (short) 1));
            result.all().get();
        } catch (Exception e) {
            log.trace("Failed to create topic: {}", e.getMessage(), e);
        }

        TBKafkaConsumerTemplate.TBKafkaConsumerTemplateBuilder<ToTransportMsg> mainConsumerBuilder = TBKafkaConsumerTemplate.builder();
        mainConsumerBuilder.settings(kafkaSettings);
        mainConsumerBuilder.topic(notificationsTopicName);
        mainConsumerBuilder.clientId("transport-" + nodeIdProvider.getNodeId());
        mainConsumerBuilder.groupId("transport");
        mainConsumerBuilder.autoCommit(true);
        mainConsumerBuilder.autoCommitIntervalMs(notificationsAutoCommitInterval);
        mainConsumerBuilder.decoder(new ToTransportMsgResponseDecoder());
        mainConsumer = mainConsumerBuilder.build();
        mainConsumer.subscribe();

        mainConsumerExecutor.execute(() -> {
            while (!stopped) {
                try {
                    ConsumerRecords<String, byte[]> records = mainConsumer.poll(Duration.ofMillis(notificationsPollDuration));
                    records.forEach(record -> {
                        try {
                            ToTransportMsg toTransportMsg = mainConsumer.decode(record);
                            if (toTransportMsg.hasToDeviceSessionMsg()) {
                                processToTransportMsg(toTransportMsg.getToDeviceSessionMsg());
                            }
                        } catch (Throwable e) {
                            log.warn("Failed to process the notification.", e);
                        }
                    });
                } catch (Exception e) {
                    log.warn("Failed to obtain messages from queue.", e);
                    try {
                        Thread.sleep(notificationsPollDuration);
                    } catch (InterruptedException e2) {
                        log.trace("Failed to wait until the server has capacity to handle new requests", e2);
                    }
                }
            }
        });
    }

    @PreDestroy
    public void destroy() {
        super.destroy();
        stopped = true;
        if (transportApiTemplate != null) {
            transportApiTemplate.stop();
        }
        if (mainConsumer != null) {
            mainConsumer.unsubscribe();
        }
        if (mainConsumerExecutor != null) {
            mainConsumerExecutor.shutdownNow();
        }
    }

    @Override
    public void process(ValidateDeviceTokenRequestMsg msg, TransportServiceCallback<ValidateDeviceCredentialsResponseMsg> callback) {
        log.trace("Processing msg: {}", msg);
        AsyncCallbackTemplate.withCallback(transportApiTemplate.post(msg.getToken(),
                TransportApiRequestMsg.newBuilder().setValidateTokenRequestMsg(msg).build()),
                response -> callback.onSuccess(response.getValidateTokenResponseMsg()), callback::onError, transportCallbackExecutor);
    }

    @Override
    public void process(ValidateDeviceX509CertRequestMsg msg, TransportServiceCallback<ValidateDeviceCredentialsResponseMsg> callback) {
        log.trace("Processing msg: {}", msg);
        AsyncCallbackTemplate.withCallback(transportApiTemplate.post(msg.getHash(),
                TransportApiRequestMsg.newBuilder().setValidateX509CertRequestMsg(msg).build()),
                response -> callback.onSuccess(response.getValidateTokenResponseMsg()), callback::onError, transportCallbackExecutor);
    }

    @Override
    public void process(GetOrCreateDeviceFromGatewayRequestMsg msg, TransportServiceCallback<GetOrCreateDeviceFromGatewayResponseMsg> callback) {
        log.trace("Processing msg: {}", msg);
        AsyncCallbackTemplate.withCallback(transportApiTemplate.post(msg.getDeviceName(),
                TransportApiRequestMsg.newBuilder().setGetOrCreateDeviceRequestMsg(msg).build()),
                response -> callback.onSuccess(response.getGetOrCreateDeviceResponseMsg()), callback::onError, transportCallbackExecutor);
    }

    @Override
    public void process(SessionInfoProto sessionInfo, SubscriptionInfoProto msg, TransportServiceCallback<Void> callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing msg: {}", toId(sessionInfo), msg);
        }
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setSubscriptionInfo(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, SessionEventMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setSessionEvent(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, PostTelemetryMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setPostTelemetry(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, PostAttributeMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setPostAttributes(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, GetAttributeRequestMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setGetAttributes(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, SubscribeToAttributeUpdatesMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setSubscribeToAttributes(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, SubscribeToRPCMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setSubscribeToRPC(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, ToDeviceRpcResponseMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setToDeviceRPCCallResponse(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void doProcess(SessionInfoProto sessionInfo, ToServerRpcRequestMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setToServerRPCCallRequest(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    @Override
    protected void registerClaimingInfo(SessionInfoProto sessionInfo, ClaimDeviceMsg msg, TransportServiceCallback<Void> callback) {
        ToRuleEngineMsg toRuleEngineMsg = ToRuleEngineMsg.newBuilder().setToDeviceActorMsg(
                TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                        .setClaimDevice(msg).build()
        ).build();
        send(sessionInfo, toRuleEngineMsg, callback);
    }

    private static class TransportCallbackAdaptor implements Callback {
        private final TransportServiceCallback<Void> callback;

        TransportCallbackAdaptor(TransportServiceCallback<Void> callback) {
            this.callback = callback;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if (exception == null) {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            } else {
                if (callback != null) {
                    callback.onError(exception);
                }
            }
        }
    }

    private void send(SessionInfoProto sessionInfo, ToRuleEngineMsg toRuleEngineMsg, TransportServiceCallback<Void> callback) {
        ruleEngineProducer.send(getRoutingKey(sessionInfo), toRuleEngineMsg, new TransportCallbackAdaptor(callback));
    }
}
