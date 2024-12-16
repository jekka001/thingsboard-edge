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
package org.thingsboard.rule.engine.gcp.pubsub;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.TestDbCallbackExecutor;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.spy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class TbPubSubNodeTest {

    private final DeviceId DEVICE_ID = new DeviceId(UUID.fromString("d29849c2-3f21-48e2-8557-74cdd6403290"));
    private final ListeningExecutor executor = new TestDbCallbackExecutor();

    private TbPubSubNode node;
    private TbPubSubNodeConfiguration config;

    @Mock
    private Publisher pubSubClientMock;
    @Mock
    private TbContext ctxMock;

    @BeforeEach
    public void setUp() throws IOException {
        node = spy(new TbPubSubNode());
        config = new TbPubSubNodeConfiguration().defaultConfiguration();
    }

    @Test
    public void verifyDefaultConfig() {
        assertThat(config.getProjectId()).isEqualTo("my-google-cloud-project-id");
        assertThat(config.getTopicName()).isEqualTo("my-pubsub-topic-name");
        assertThat(config.getMessageAttributes()).isEmpty();
        assertThat(config.getServiceAccountKey()).isNull();
        assertThat(config.getServiceAccountKeyFileName()).isNull();
    }

    @Test
    public void givenValidConfig_whenInit_thenOk() throws IOException {
        willReturn(pubSubClientMock).given(node).initPubSubClient(ctxMock);

        assertThatNoException().isThrownBy(() -> node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config))));
    }

    @Test
    public void givenErrorOccursDuringInitClient_whenInit_thenThrowsException() throws IOException {
        willThrow(new RuntimeException("Could not initialize client!")).given(node).initPubSubClient(ctxMock);

        assertThatThrownBy(() -> node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config))))
                .isInstanceOf(TbNodeException.class).hasMessage("java.lang.RuntimeException: Could not initialize client!");
    }

    @ParameterizedTest
    @MethodSource
    public void givenForceAckIsTrueAndMessageAttributesPatterns_whenOnMsg_thenEnqueueForTellNext(
            String attributeName, String attributeValue, TbMsgMetaData metaData, String data) throws IOException, TbNodeException {
        config.setMessageAttributes(Map.of(attributeName, attributeValue));
        given(ctxMock.isExternalNodeForceAck()).willReturn(true);
        willReturn(pubSubClientMock).given(node).initPubSubClient(ctxMock);

        String messageId = "2070443601311540";
        given(pubSubClientMock.publish(any())).willReturn(ApiFutures.immediateFuture(messageId));
        given(ctxMock.getExternalCallExecutor()).willReturn(executor);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));
        TbMsg msg = TbMsg.newMsg()
                .type(TbMsgType.POST_TELEMETRY_REQUEST)
                .originator(DEVICE_ID)
                .metaData(metaData.copy())
                .data(data)
                .build();
        node.onMsg(ctxMock, msg);

        then(ctxMock).should().ack(msg);
        PubsubMessage.Builder pubsubMessageBuilder = PubsubMessage.newBuilder();
        pubsubMessageBuilder.setData(ByteString.copyFromUtf8(msg.getData()));
        this.config.getMessageAttributes().forEach((k, v) -> {
            String name = TbNodeUtils.processPattern(k, msg);
            String val = TbNodeUtils.processPattern(v, msg);
            pubsubMessageBuilder.putAttributes(name, val);
        });
        then(pubSubClientMock).should().publish(pubsubMessageBuilder.build());
        ArgumentCaptor<TbMsg> actualMsg = ArgumentCaptor.forClass(TbMsg.class);
        then(ctxMock).should().enqueueForTellNext(actualMsg.capture(), eq(TbNodeConnectionType.SUCCESS));
        metaData.putValue("messageId", messageId);
        TbMsg expectedMsg = msg.transform()
                .metaData(metaData)
                .build();
        assertThat(actualMsg.getValue())
                .usingRecursiveComparison()
                .ignoringFields("ctx")
                .isEqualTo(expectedMsg);
    }

    private static Stream<Arguments> givenForceAckIsTrueAndMessageAttributesPatterns_whenOnMsg_thenEnqueueForTellNext() {
        return Stream.of(
                Arguments.of("attributeName", "attributeValue", new TbMsgMetaData(), TbMsg.EMPTY_JSON_OBJECT),
                Arguments.of("${mdAttrName}", "${mdAttrValue}", new TbMsgMetaData(
                        Map.of(
                                "mdAttrName", "mdAttributeName",
                                "mdAttrValue", "mdAttributeValue"
                        )), TbMsg.EMPTY_JSON_OBJECT),
                Arguments.of("$[msgAttrName]", "$[msgAttrValue]", new TbMsgMetaData(),
                        "{\"msgAttrName\": \"msgAttributeName\", \"msgAttrValue\": \"mdAttributeValue\"}")
        );
    }

    @Test
    public void givenForceAckIsFalse_whenOnMsg_thenTellSuccess() throws IOException, TbNodeException {
        given(ctxMock.isExternalNodeForceAck()).willReturn(false);
        willReturn(pubSubClientMock).given(node).initPubSubClient(ctxMock);

        String messageId = "2070443601311540";
        given(pubSubClientMock.publish(any())).willReturn(ApiFutures.immediateFuture(messageId));
        given(ctxMock.getExternalCallExecutor()).willReturn(executor);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));
        TbMsgMetaData metadata = new TbMsgMetaData();
        TbMsg msg = TbMsg.newMsg()
                .type(TbMsgType.POST_TELEMETRY_REQUEST)
                .originator(DEVICE_ID)
                .metaData(metadata.copy())
                .data(TbMsg.EMPTY_JSON_OBJECT)
                .build();
        node.onMsg(ctxMock, msg);

        then(ctxMock).should(never()).ack(msg);
        PubsubMessage.Builder pubsubMessageBuilder = PubsubMessage.newBuilder();
        pubsubMessageBuilder.setData(ByteString.copyFromUtf8(msg.getData()));
        then(pubSubClientMock).should().publish(pubsubMessageBuilder.build());
        ArgumentCaptor<TbMsg> actualMsg = ArgumentCaptor.forClass(TbMsg.class);
        then(ctxMock).should().tellSuccess(actualMsg.capture());
        metadata.putValue("messageId", messageId);
        TbMsg expectedMsg = msg.transform()
                .metaData(metadata)
                .build();
        assertThat(actualMsg.getValue())
                .usingRecursiveComparison()
                .ignoringFields("ctx")
                .isEqualTo(expectedMsg);
    }

    @Test
    public void givenForceAckIsFalseAndErrorOccursOnTheGCP_whenOnMsg_thenTellFailure() throws IOException, TbNodeException {
        given(ctxMock.isExternalNodeForceAck()).willReturn(false);
        willReturn(pubSubClientMock).given(node).initPubSubClient(ctxMock);

        String errorMsg = "Something went wrong!";
        ApiFuture<String> failedFuture = ApiFutures.immediateFailedFuture(new RuntimeException(errorMsg));
        given(pubSubClientMock.publish(any())).willReturn(failedFuture);
        given(ctxMock.getExternalCallExecutor()).willReturn(executor);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));
        TbMsgMetaData metaData = new TbMsgMetaData();
        TbMsg msg = TbMsg.newMsg()
                .type(TbMsgType.POST_TELEMETRY_REQUEST)
                .originator(DEVICE_ID)
                .metaData(metaData.copy())
                .data(TbMsg.EMPTY_JSON_OBJECT)
                .build();
        node.onMsg(ctxMock, msg);

        then(ctxMock).should(never()).ack(any());
        ArgumentCaptor<TbMsg> actualMsg = ArgumentCaptor.forClass(TbMsg.class);
        ArgumentCaptor<Throwable> actualError = ArgumentCaptor.forClass(Throwable.class);
        then(ctxMock).should().tellFailure(actualMsg.capture(), actualError.capture());
        metaData.putValue("error", RuntimeException.class + ": " + errorMsg);
        TbMsg expectedMsg = msg.transform()
                .metaData(metaData)
                .build();
        assertThat(actualMsg.getValue())
                .usingRecursiveComparison()
                .ignoringFields("ctx")
                .isEqualTo(expectedMsg);
        assertThat(actualError.getValue()).isInstanceOf(RuntimeException.class).hasMessage(errorMsg);
    }

    @Test
    public void givenForceAckIsTrueAndErrorOccursOnTheGCP_whenOnMsg_thenEnqueueForTellFailure() throws IOException, TbNodeException {
        given(ctxMock.isExternalNodeForceAck()).willReturn(true);
        willReturn(pubSubClientMock).given(node).initPubSubClient(ctxMock);

        String errorMsg = "Something went wrong!";
        ApiFuture<String> failedFuture = ApiFutures.immediateFailedFuture(new RuntimeException(errorMsg));
        given(pubSubClientMock.publish(any())).willReturn(failedFuture);
        given(ctxMock.getExternalCallExecutor()).willReturn(executor);

        node.init(ctxMock, new TbNodeConfiguration(JacksonUtil.valueToTree(config)));
        TbMsgMetaData metaData = new TbMsgMetaData();
        TbMsg msg = TbMsg.newMsg()
                .type(TbMsgType.POST_TELEMETRY_REQUEST)
                .originator(DEVICE_ID)
                .metaData(metaData.copy())
                .data(TbMsg.EMPTY_JSON_OBJECT)
                .build();
        node.onMsg(ctxMock, msg);

        then(ctxMock).should().ack(msg);
        ArgumentCaptor<TbMsg> actualMsg = ArgumentCaptor.forClass(TbMsg.class);
        ArgumentCaptor<Throwable> actualError = ArgumentCaptor.forClass(Throwable.class);
        then(ctxMock).should().enqueueForTellFailure(actualMsg.capture(), actualError.capture());
        metaData.putValue("error", RuntimeException.class + ": " + errorMsg);
        TbMsg expectedMsg = msg.transform()
                .metaData(metaData)
                .build();
        assertThat(actualMsg.getValue())
                .usingRecursiveComparison()
                .ignoringFields("ctx")
                .isEqualTo(expectedMsg);
        assertThat(actualError.getValue()).isInstanceOf(RuntimeException.class).hasMessage(errorMsg);
    }

    @Test
    public void givenPubSubClientIsNotNull_whenDestroy_thenShutDownAndAwaitTermination() throws InterruptedException {
        ReflectionTestUtils.setField(node, "pubSubClient", pubSubClientMock);
        node.destroy();
        then(pubSubClientMock).should().shutdown();
        then(pubSubClientMock).should().awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    public void givenPubSubClientIsNull_whenDestroy_thenShutDownAndAwaitTermination() {
        ReflectionTestUtils.setField(node, "pubSubClient", null);
        node.destroy();
        then(pubSubClientMock).shouldHaveNoInteractions();
    }

}
