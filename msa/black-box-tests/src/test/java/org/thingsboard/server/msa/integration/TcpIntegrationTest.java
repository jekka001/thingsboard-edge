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
package org.thingsboard.server.msa.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.bytes.ByteArrayEncoder;
import io.netty.handler.codec.string.StringEncoder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.awaitility.Awaitility;
import org.testcontainers.containers.ContainerState;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.event.EventType;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.msa.WsClient;
import org.thingsboard.server.msa.mapper.WsTelemetryResponse;

import javax.xml.bind.DatatypeConverter;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static ch.qos.logback.core.encoder.ByteArrayUtil.hexStringToByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.thingsboard.server.common.data.DataConstants.DEVICE;
import static org.thingsboard.server.common.data.DataConstants.SHARED_SCOPE;
import static org.thingsboard.server.common.data.integration.IntegrationType.TCP;
import static org.thingsboard.server.msa.prototypes.TcpIntegrationPrototypes.defaultBinaryConfig;
import static org.thingsboard.server.msa.prototypes.TcpIntegrationPrototypes.defaultJsonConfig;
import static org.thingsboard.server.msa.prototypes.TcpIntegrationPrototypes.defaultTextConfig;

@Slf4j
public class TcpIntegrationTest extends AbstractIntegrationTest {
    private static final String ROUTING_KEY = "routing-key-tcp";
    private static final String SECRET_KEY = "secret-key-tcp";
    private static final int PORT = 10560;
    private static final String JSON_CONVERTER_CONFIG = "var data = decodeToJson(payload);\n" +
            "var deviceName =  '" + "DEVICE_NAME" + "';\n" +
            "var deviceType = 'DEFAULT';\n" +
            "var data = decodeToJson(payload);\n" +
            "var result = {\n" +
            "   deviceName: deviceName,\n" +
            "   deviceType: deviceType,\n" +
            "   attributes: {},\n" +
            "   telemetry: {\n" +
            "       temperature: data.temperature,\n" +
            "       humidity: data.humidity\n" +
            "   }\n" +
            "};\n" +
            "\n" +
            "/** Helper functions **/\n" +
            "function decodeToString(payload) {\n" +
            "   return String.fromCharCode.apply(String, payload);\n" +
            "}\n" +
            "function decodeToJson(payload) {\n" +
            "   // covert payload to string.\n" +
            "   var str = decodeToString(payload);\n" +
            "   var data = JSON.parse(str);\n" +
            "   return data;\n" +
            "}\n" +
            "return result;";

    private static final String TEXT_CONVERTER_CONFIG = "var strArray = decodeToString(payload);\n" +
            "var payloadArray = strArray.replace(/\\\"/g, \"\").replace(/\\s/g, \"\").split(',');\n" +
            "var telemetryKey = payloadArray[2];\n" +
            "var telemetryValue = payloadArray[3]; \n" +
            "var telemetryPayload = {};\n" +
            "telemetryPayload[telemetryKey] = telemetryValue;\n" +
            "var result = {\n" +
            "    deviceName: payloadArray[0],\n" +
            "    deviceType: payloadArray[1],\n" +
            "    telemetry: telemetryPayload,\n" +
            "    attributes: {}\n" +
            "  };\n" +
            "function decodeToString(payload) {\n" +
            "   return String.fromCharCode.apply(String, payload);\n" +
            "}\n" +
            "return result;";

    private static final String BINARY_CONVERTER_CONFIG = "var payloadStr = decodeToString(payload);\n" +
            "\n" +
            "var deviceName = payloadStr.substring(0,11);\n" +
            "var deviceType = payloadStr.substring(11,18);\n" +
            "\n" +
            "var result = {\n" +
            "   deviceName: deviceName,\n" +
            "   deviceType: deviceType,\n" +
            "   attributes: {},\n" +
            "   telemetry: {\n" +
            "       temperature: parseFloat(payloadStr.substring(18,24))\n" +
            "   }\n" +
            "};\n" +
            "\n" +
            "function decodeToString(payload) {\n" +
            "   return String.fromCharCode.apply(String, payload);\n" +
            "}\n" +
            "\n" +
            "return result;";

    private final JsonNode DOWNLINK_CONVERTER_CONFIGURATION = JacksonUtil
            .newObjectNode().put("encoder", "var result = {\n" +
                    "    contentType: \"JSON\",\n" +
                    "    data: JSON.stringify(msg),\n" +
                    "    metadata: {\n" +
                    "    }\n" +
                    "};\n" +
                    "\n" +
                    "return result;");

    @AfterMethod
    public void afterMethod() {
        if (containerTestSuite.isActive()) {
            ContainerState tcpIntegrationContainer = containerTestSuite.getTestContainer().getContainerByServiceName("tb-pe-tcp-integration_1").get();
            tcpIntegrationContainer.getDockerClient().restartContainerCmd(tcpIntegrationContainer.getContainerId()).exec();
        }
    }

    @Test
    public void telemetryUploadWithJsonConverter() throws Exception {
        JsonNode configConverter = JacksonUtil.newObjectNode().put("decoder",
                JSON_CONVERTER_CONFIG.replaceAll("DEVICE_NAME", device.getName()));

        createIntegration(TCP, defaultJsonConfig(PORT), configConverter, ROUTING_KEY, SECRET_KEY, true);

        WsClient wsClient = subscribeToWebSocket(device.getId(), "LATEST_TELEMETRY", CmdsType.TS_SUB_CMDS);

        String jsonPayload = createPayloadForUplink().toString();
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Channel channel = openChannel(group, new StringEncoder(), new ClientHandler());
            channel.writeAndFlush(jsonPayload);
            channel.close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }

        WsTelemetryResponse actualLatestTelemetry = wsClient.getLastMessage();
        log.info("Received telemetry: {}", actualLatestTelemetry);
        wsClient.closeBlocking();

        Assert.assertEquals(1, actualLatestTelemetry.getData().size());
        Assert.assertEquals(Sets.newHashSet(TELEMETRY_KEY),
                actualLatestTelemetry.getLatestValues().keySet());

        Assert.assertTrue(verify(actualLatestTelemetry, TELEMETRY_KEY, TELEMETRY_VALUE));
    }

    @Test
    public void telemetryUploadWithTextConverter() throws Exception {
        JsonNode configConverter = JacksonUtil.newObjectNode().put("decoder", TEXT_CONVERTER_CONFIG);

        createIntegration(TCP, defaultTextConfig(PORT), configConverter, ROUTING_KEY, SECRET_KEY, true);

        WsClient wsClient = subscribeToWebSocket(device.getId(), "LATEST_TELEMETRY", CmdsType.TS_SUB_CMDS);

        String textPayload = device.getName() + ",default,temperature,25.7\n";
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Channel channel = openChannel(group, new StringEncoder(), new ClientHandler());
            channel.writeAndFlush(textPayload);
            channel.close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }

        WsTelemetryResponse actualLatestTelemetry = wsClient.getLastMessage();
        log.info("Received telemetry: {}", actualLatestTelemetry);
        wsClient.closeBlocking();

        Assert.assertEquals(1, actualLatestTelemetry.getData().size());
        Assert.assertEquals(Sets.newHashSet(TELEMETRY_KEY),
                actualLatestTelemetry.getLatestValues().keySet());

        Assert.assertTrue(verify(actualLatestTelemetry, TELEMETRY_KEY, "25.7"));
    }

    @Test
    public void telemetryUploadWithBinaryConverter() throws Exception {
        JsonNode configConverter = JacksonUtil.newObjectNode().put("decoder", BINARY_CONVERTER_CONFIG);

        createIntegration(TCP, defaultBinaryConfig(PORT), configConverter, ROUTING_KEY, SECRET_KEY, true);

        WsClient wsClient = subscribeToWebSocket(device.getId(), "LATEST_TELEMETRY", CmdsType.TS_SUB_CMDS);

        //skip 4 first byte, set 5th byte to 22
        String hexString = "0000000016" + DatatypeConverter.printHexBinary((device.getName() + "default25.7").getBytes());
        byte[] bytes = hexStringToByteArray(hexString);

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Channel channel = openChannel(group, new ByteArrayEncoder(), new ClientHandler());
            channel.writeAndFlush(bytes);
            channel.close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }

        WsTelemetryResponse actualLatestTelemetry = wsClient.getLastMessage();
        log.info("Received telemetry: {}", actualLatestTelemetry);
        wsClient.closeBlocking();

        Assert.assertEquals(1, actualLatestTelemetry.getData().size());
        Assert.assertEquals(Sets.newHashSet(TELEMETRY_KEY),
                actualLatestTelemetry.getLatestValues().keySet());

        Assert.assertTrue(verify(actualLatestTelemetry, TELEMETRY_KEY, "25.7"));
    }

    @Test
    public void checkDownlinkMessageWasSent() throws Exception {
        JsonNode configConverter = JacksonUtil.newObjectNode().put("decoder",
                JSON_CONVERTER_CONFIG.replaceAll("DEVICE_NAME", device.getName()));

        createIntegration(TCP, defaultJsonConfig(PORT), configConverter, DOWNLINK_CONVERTER_CONFIGURATION, ROUTING_KEY, SECRET_KEY, true);

        String jsonPayload = createPayloadForUplink().toString();
        EventLoopGroup group = new NioEventLoopGroup();
        ClientHandler clientHandler = new ClientHandler();
        try {
            Channel channel = openChannel(group, new StringEncoder(), clientHandler);
            channel.writeAndFlush(jsonPayload);

            //check downlink uploaded after attribute updated
            RuleChainId ruleChainId = createRootRuleChainWithIntegrationDownlinkNode(integration.getId());

            JsonNode attributes = JacksonUtil.toJsonNode(createPayload().toString());
            testRestClient.saveEntityAttributes(DEVICE, device.getId().toString(), SHARED_SCOPE, attributes);

            RuleChainMetaData ruleChainMetadata = testRestClient.getRuleChainMetadata(ruleChainId);
            RuleNode integrationNode = ruleChainMetadata.getNodes().stream().filter(ruleNode -> ruleNode.getType().equals("org.thingsboard.rule.engine.integration.TbIntegrationDownlinkNode")).findFirst().get();
            waitTillRuleNodeReceiveMsg(integrationNode.getId(), EventType.DEBUG_RULE_NODE, integration.getTenantId(), "ATTRIBUTES_UPDATED");

            //check downlink
            Awaitility
                    .await()
                    .alias("Get message from server")
                    .atMost(20, TimeUnit.SECONDS)
                    .until(() -> clientHandler.getMessageList().size() > 0);

            BlockingQueue<String> messages = clientHandler.getMessageList();
            JsonNode actual = JacksonUtil.toJsonNode(Objects.requireNonNull(messages.poll()));

            assertThat(actual.get("stringKey")).isEqualTo(attributes.get("stringKey"));
            assertThat(actual.get("booleanKey")).isEqualTo(attributes.get("booleanKey"));
            assertThat(actual.get("doubleKey")).isEqualTo(attributes.get("doubleKey"));
            assertThat(actual.get("longKey")).isEqualTo(attributes.get("longKey"));

            channel.close().sync();
        } finally {
            group.shutdownGracefully().sync();
        }
    }

    private Channel openChannel(EventLoopGroup group, ChannelHandler channelHandler, ClientHandler clientHandler) throws InterruptedException {
        Bootstrap client = new Bootstrap();

        client.group(group);
        client.channel(NioSocketChannel.class);
        client.remoteAddress(new InetSocketAddress(PORT));
        client.handler(new ChannelInitializer<SocketChannel>() {
            protected void initChannel(SocketChannel socketChannel) {
                socketChannel.pipeline().addLast(channelHandler, clientHandler);
            }
        });

        return client.connect().sync().channel();
    }

    public static class ClientHandler extends ChannelInboundHandlerAdapter {
        private final BlockingQueue<String> messageList = new ArrayBlockingQueue<>(100);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws DecoderException, UnsupportedEncodingException {
            ByteBuf buf = (ByteBuf) msg;
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            String hexString = Hex.encodeHexString(bytes);

            String string = new String(Hex.decodeHex(hexString.toCharArray()), "UTF-8");
            messageList.add(string);
        }

        public BlockingQueue<String> getMessageList() {
            return messageList;
        }
    }

    @Override
    protected String getDevicePrototypeSufix() {
        return "tcp_";
    }
}
