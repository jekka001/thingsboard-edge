/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.integration.tcpip.udp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.DownlinkData;
import org.thingsboard.integration.tcpip.AbstractIpIntegration;
import org.thingsboard.integration.tcpip.HandlerConfiguration;
import org.thingsboard.integration.tcpip.configs.TextHandlerConfiguration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
public class BasicUdpIntegration extends AbstractIpIntegration {

    private UdpConfigurationParameters udpConfigurationParameters;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        if (!this.configuration.isEnabled()) {
            return;
        }
        try {
            udpConfigurationParameters = mapper.readValue(mapper.writeValueAsString(configuration.getConfiguration().get("clientConfiguration")), UdpConfigurationParameters.class);
            workerGroup = new NioEventLoopGroup();
            startServer();
            log.info("UDP Server of [{}] started, BIND_PORT: [{}]", configuration.getName().toUpperCase(), udpConfigurationParameters.getPort());
        } catch (Exception e) {
            log.error("[{}] Integration exception while initialization UDP server: {}", configuration.getName().toUpperCase(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void bind() throws Exception {
        Bootstrap server = new Bootstrap().group(workerGroup);
        server.channel(NioDatagramChannel.class)
                .handler(createChannelHandlerInitializer(udpConfigurationParameters.getHandlerConfiguration()))
                .option(ChannelOption.SO_BROADCAST, udpConfigurationParameters.isSoBroadcast())
                .option(ChannelOption.SO_RCVBUF, udpConfigurationParameters.getSoRcvBuf() * 1024);
        serverChannel = server.bind(udpConfigurationParameters.getPort()).sync().channel();
        if (bindFuture != null) {
            bindFuture.cancel(true);
        }
    }

    @Override
    protected void doValidateConfiguration(JsonNode configuration, boolean allowLocalNetworkHosts) {
        UdpConfigurationParameters udpConfiguration;
        try {
            String stringUdpConfiguration = mapper.writeValueAsString(configuration.get("clientConfiguration"));
            udpConfiguration = mapper.readValue(stringUdpConfiguration, UdpConfigurationParameters.class);
            HandlerConfiguration handlerConfiguration = udpConfiguration.getHandlerConfiguration();
            if (handlerConfiguration == null) {
                throw new IllegalArgumentException("Handler Configuration is empty");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid UDP Integration Configuration structure! " + e.getMessage());
        }
        if (!allowLocalNetworkHosts) {
            throw new IllegalArgumentException("Usage of local network host for UDP Server connection is not allowed!");
        }
    }

    private ChannelInitializer<NioDatagramChannel> createChannelHandlerInitializer(HandlerConfiguration handlerConfig) {
        switch (handlerConfig.getHandlerType()) {
            case TEXT_PAYLOAD:
                return new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(final NioDatagramChannel channel) {
                        try {
                            TextHandlerConfiguration textHandlerConfiguration = (TextHandlerConfiguration) handlerConfig;
                            channel.pipeline()
                                    .addLast("datagramToStringDecoder", new AbstractUdpMsgDecoder<DatagramPacket, String>
                                            (msg -> msg.content().toString(Charset.forName(textHandlerConfiguration.getCharsetName()))) {
                                    })
                                    .addLast("udpStringHandler", new AbstractChannelHandler<String>(BasicUdpIntegration.this::writeValueAsBytes, StringUtils::isEmpty) {
                                    });
                        } catch (Exception e) {
                            log.error("Init Channel Exception: {}", e.getMessage(), e);
                            throw new RuntimeException(e);
                        }
                    }
                };
            case JSON_PAYLOAD:
                return new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(final NioDatagramChannel channel) throws Exception {
                        try {
                            channel.pipeline()
                                    .addLast("datagramJsonDecoder", new BasicUdpIntegration.AbstractUdpMsgDecoder<DatagramPacket, byte[]>(msg -> toByteArray(msg.content())) {
                                    })
                                    .addLast("datagramJsonHandler", new AbstractChannelHandler<byte[]>(msg -> msg, Objects::isNull) {
                                    });
                        } catch (Exception e) {
                            log.error("Init Channel Exception: {}", e.getMessage(), e);
                            throw new RuntimeException(e);
                        }
                    }
                };
            case BINARY_PAYLOAD:
                return new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(final NioDatagramChannel channel) {
                        try {
                            channel.pipeline()
                                    .addLast("datagramToByteDecoder", new AbstractUdpMsgDecoder<DatagramPacket, byte[]>(msg -> toByteArray(msg.content())) {
                                    })
                                    .addLast("udpByteHandler", new AbstractChannelHandler<byte[]>(byteArray -> byteArray, BasicUdpIntegration.this::isEmptyByteArray) {
                                    });
                        } catch (Exception e) {
                            log.error("Init Channel Exception: {}", e.getMessage(), e);
                            throw new RuntimeException(e);
                        }
                    }
                };
            case HEX_PAYLOAD:
                return new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(final NioDatagramChannel channel) throws Exception {
                        try {
                            channel.pipeline()
                                    .addLast("datagramToHexStringDecoder", new AbstractUdpMsgDecoder<DatagramPacket, ObjectNode>(msg -> getJsonHexReport(toByteArray(msg.content()))) {
                                    })
                                    .addLast("udpHexStringHandler", new AbstractChannelHandler<ObjectNode>(objectNode -> objectNode.toString().getBytes(), BasicUdpIntegration.this::isEmptyObjectNode) {
                                    });
                        } catch (Exception e) {
                            log.error("Init Channel Exception: {}", e.getMessage(), e);
                            throw new RuntimeException(e);
                        }
                    }
                };
            default:
                throw new RuntimeException("Unknown Channel Initializer");
        }
    }

    @Override
    protected void sendDownlink(ChannelHandlerContext deviceCtx, String entityName) {
        SocketAddress address = deviceSenderAddress.getIfPresent(entityName);
        if (address != null) {
            for (DownlinkData downlinkData : devicesDownlinkData.get(entityName)) {
                deviceCtx.write(new DatagramPacket(Unpooled.wrappedBuffer(downlinkData.getData()), (InetSocketAddress) address));
                deviceCtx.write(new DatagramPacket(Unpooled.wrappedBuffer(System.lineSeparator().getBytes()), (InetSocketAddress) address));
            }
            deviceCtx.flush();
            devicesDownlinkData.remove(entityName);
            connectedDevicesContexts.remove(entityName);
        }
    }

    @AllArgsConstructor
    private abstract class AbstractUdpMsgDecoder<T extends DatagramPacket, R> extends MessageToMessageDecoder<T> {

        private Function<T, R> transformer;

        @Override
        protected void decode(ChannelHandlerContext ctx, T msg, List<Object> out) throws Exception {
            try {
                out.add(new RawIpIntegrationMsg<R>(msg.sender(), transformer.apply(msg)));
            } catch (Exception e) {
                log.error("[{}] Exception during of decoding message", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }
    }

}
