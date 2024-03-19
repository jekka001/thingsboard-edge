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
package org.thingsboard.server.service.integration.rpc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.JavaSerDesUtil;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.event.ConverterDebugEvent;
import org.thingsboard.server.common.data.event.Event;
import org.thingsboard.server.common.data.event.EventType;
import org.thingsboard.server.common.data.event.IntegrationDebugEvent;
import org.thingsboard.server.common.data.event.LifecycleEvent;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.queue.TbMsgCallback;
import org.thingsboard.server.common.msg.tools.TbRateLimitsException;
import org.thingsboard.server.gen.integration.AssetUplinkDataProto;
import org.thingsboard.server.gen.integration.ConnectRequestMsg;
import org.thingsboard.server.gen.integration.ConnectResponseCode;
import org.thingsboard.server.gen.integration.ConnectResponseMsg;
import org.thingsboard.server.gen.integration.ConverterConfigurationProto;
import org.thingsboard.server.gen.integration.ConverterUpdateMsg;
import org.thingsboard.server.gen.integration.DeviceDownlinkDataProto;
import org.thingsboard.server.gen.integration.DeviceUplinkDataProto;
import org.thingsboard.server.gen.integration.DownlinkMsg;
import org.thingsboard.server.gen.integration.EntityViewDataProto;
import org.thingsboard.server.gen.integration.IntegrationConfigurationProto;
import org.thingsboard.server.gen.integration.IntegrationStatisticsProto;
import org.thingsboard.server.gen.integration.IntegrationUpdateMsg;
import org.thingsboard.server.gen.integration.MessageType;
import org.thingsboard.server.gen.integration.RequestMsg;
import org.thingsboard.server.gen.integration.ResponseMsg;
import org.thingsboard.server.gen.integration.TbEventProto;
import org.thingsboard.server.gen.integration.UplinkMsg;
import org.thingsboard.server.gen.integration.UplinkResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.service.integration.IntegrationContextComponent;

import jakarta.annotation.Nullable;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Data
@Slf4j
public final class IntegrationGrpcSession implements Closeable {

    private static final ReentrantLock entityCreationLock = new ReentrantLock();
    private final Gson gson = new Gson();

    private final UUID sessionId;
    private final BiConsumer<IntegrationId, IntegrationGrpcSession> sessionOpenListener;
    private final Consumer<IntegrationId> sessionCloseListener;
    private final SyncedStreamObserver<ResponseMsg> outputStream;

    private IntegrationContextComponent ctx;
    private Integration configuration;
    private StreamObserver<RequestMsg> inputStream;
    private volatile boolean connected;
    private String serviceId;

    IntegrationGrpcSession(IntegrationContextComponent ctx, StreamObserver<ResponseMsg> outputStream
            , BiConsumer<IntegrationId, IntegrationGrpcSession> sessionOpenListener
            , Consumer<IntegrationId> sessionCloseListener) {
        this.sessionId = UUID.randomUUID();
        this.ctx = ctx;
        this.outputStream = new SyncedStreamObserver<>(outputStream);
        this.sessionOpenListener = sessionOpenListener;
        this.sessionCloseListener = sessionCloseListener;
        initInputStream();
    }

    private void initInputStream() {
        this.inputStream = new StreamObserver<>() {
            @Override
            public void onNext(RequestMsg requestMsg) {
                if (!connected && requestMsg.getMessageType().equals(MessageType.CONNECT_RPC_MESSAGE)) {
                    ConnectResponseMsg responseMsg = processConnect(requestMsg.getConnectRequestMsg());
                    outputStream.onNext(ResponseMsg.newBuilder()
                            .setConnectResponseMsg(responseMsg)
                            .build());
                    if (ConnectResponseCode.ACCEPTED != responseMsg.getResponseCode()) {
                        outputStream.onError(new RuntimeException(responseMsg.getErrorMsg()));
                    } else {
                        connected = true;
                        serviceId = requestMsg.getConnectRequestMsg().getServiceId();
                    }
                }
                if (connected) {
                    if (requestMsg.getMessageType().equals(MessageType.UPLINK_RPC_MESSAGE) && requestMsg.hasUplinkMsg()) {
                        outputStream.onNext(ResponseMsg.newBuilder()
                                .setUplinkResponseMsg(processUplinkMsg(requestMsg.getUplinkMsg()))
                                .build());
                    }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.error("[{}] Failed to deliver message from client!", configuration.getId(), t);
                closeSession();
            }

            @Override
            public void onCompleted() {
                closeSession();
            }

            private void closeSession() {
                connected = false;
                if (configuration != null) {
                    try {
                        sessionCloseListener.accept(configuration.getId());
                    } catch (Exception ignored) {
                        //Do nothing
                    }
                }
                try {
                    outputStream.onCompleted();
                } catch (Exception ignored) {
                    //Do nothing
                }
            }
        };
    }

    private ConnectResponseMsg processConnect(ConnectRequestMsg request) {
        Optional<Integration> optional = ctx.getIntegrationService().findIntegrationByRoutingKey(TenantId.SYS_TENANT_ID, request.getIntegrationRoutingKey());
        if (optional.isPresent()) {
            configuration = optional.get();
            try {
                if (configuration.isRemote() && configuration.getSecret().equals(request.getIntegrationSecret())) {
                    Converter defaultConverter = ctx.getConverterService().findConverterById(configuration.getTenantId(),
                            configuration.getDefaultConverterId());
                    ConverterConfigurationProto defaultConverterProto = constructConverterConfigProto(defaultConverter);

                    ConverterConfigurationProto downLinkConverterProto = ConverterConfigurationProto.getDefaultInstance();
                    if (configuration.getDownlinkConverterId() != null) {
                        Converter downlinkConverter = ctx.getConverterService().findConverterById(configuration.getTenantId(),
                                configuration.getDownlinkConverterId());
                        downLinkConverterProto = constructConverterConfigProto(downlinkConverter);
                    }
                    connected = true;
                    sessionOpenListener.accept(configuration.getId(), this);
                    return ConnectResponseMsg.newBuilder()
                            .setResponseCode(ConnectResponseCode.ACCEPTED)
                            .setErrorMsg("")
                            .setConfiguration(constructIntegrationConfigProto(configuration, defaultConverterProto, downLinkConverterProto)).build();
                }
                return ConnectResponseMsg.newBuilder()
                        .setResponseCode(ConnectResponseCode.BAD_CREDENTIALS)
                        .setErrorMsg("Failed to validate the integration!")
                        .setConfiguration(IntegrationConfigurationProto.getDefaultInstance()).build();
            } catch (Exception e) {
                log.error("[{}] Failed to process integration connection!", request.getIntegrationRoutingKey(), e);
                return ConnectResponseMsg.newBuilder()
                        .setResponseCode(ConnectResponseCode.SERVER_UNAVAILABLE)
                        .setErrorMsg("Failed to process integration connection!")
                        .setConfiguration(IntegrationConfigurationProto.getDefaultInstance()).build();
            }
        }
        return ConnectResponseMsg.newBuilder()
                .setResponseCode(ConnectResponseCode.BAD_CREDENTIALS)
                .setErrorMsg("Failed to find the integration! Routing key: " + request.getIntegrationRoutingKey())
                .setConfiguration(IntegrationConfigurationProto.getDefaultInstance()).build();
    }

    private UplinkResponseMsg processUplinkMsg(UplinkMsg msg) {
        try {
            if (msg.getDeviceDataCount() > 0) {
                for (DeviceUplinkDataProto data : msg.getDeviceDataList()) {
                    ctx.getRateLimitService().checkLimit(configuration.getTenantId(), data::toString);
                    ctx.getRateLimitService().checkLimit(configuration.getTenantId(), data.getDeviceName(), data::toString);

                    final UUID sessionId = this.sessionId;
                    ctx.getPlatformIntegrationService().processUplinkData(configuration, sessionId, data, null);
                }
            }

            if (msg.getAssetDataCount() > 0) {
                for (AssetUplinkDataProto data : msg.getAssetDataList()) {
                    ctx.getRateLimitService().checkLimit(configuration.getTenantId(), data::toString);
                    ctx.getPlatformIntegrationService().processUplinkData(configuration, data, null);
                }
            }

            if (msg.getEntityViewDataCount() > 0) {
                for (EntityViewDataProto data : msg.getEntityViewDataList()) {
                    ctx.getPlatformIntegrationService().processUplinkData(configuration, data, null);
                }
            }

            if (msg.getIntegrationStatisticsCount() > 0) {
                for (IntegrationStatisticsProto data : msg.getIntegrationStatisticsList()) {
                    processIntegrationStatistics(data);
                }
            }

            if (msg.getEventsDataCount() > 0) {
                for (TbEventProto proto : msg.getEventsDataList()) {
                    switch (proto.getSource()) {
                        case INTEGRATION:
                            ctx.getRateLimitService().checkLimit(configuration.getTenantId(), configuration.getId(), true);
                            saveEvent(configuration.getTenantId(), configuration.getId(), proto);
                            break;
                        case UPLINK_CONVERTER:
                            if (ctx.getRateLimitService().checkLimit(configuration.getTenantId(), configuration.getDefaultConverterId(), false)) {
                                saveEvent(configuration.getTenantId(), configuration.getDefaultConverterId(), proto);
                            } else {
                                sendConverterRateLimitEvent(proto);
                            }
                            break;
                        case DOWNLINK_CONVERTER:
                            saveEvent(configuration.getTenantId(), configuration.getDownlinkConverterId(), proto);
                            break;
                        case DEVICE:
                            Device device = ctx.getDeviceService().findDeviceByTenantIdAndName(configuration.getTenantId(), proto.getDeviceName());
                            if (device != null) {
                                saveEvent(configuration.getTenantId(), device.getId(), proto);
                            }
                            break;
                    }
                }
            }

            if (msg.getTbMsgCount() > 0) {
                for (ByteString tbMsgByteString : msg.getTbMsgList()) {
                    TbMsg tbMsg = TbMsg.fromBytes(null, tbMsgByteString.toByteArray(), TbMsgCallback.EMPTY);
                    ctx.getPlatformIntegrationService().process(this.configuration.getTenantId(), tbMsg, null);
                }
            }
        } catch (Exception e) {
            if (e instanceof TbRateLimitsException) {
                sendIntegrationRateLimitEvent((TbRateLimitsException) e, msg.toString());
            }

            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            return UplinkResponseMsg.newBuilder()
                    .setSuccess(false)
                    .setErrorMsg(errorMsg) // can't set null value as error msg
                    .build();
        }
        return UplinkResponseMsg.newBuilder()
                .setSuccess(true)
                .setErrorMsg("")
                .build();
    }

    private void sendIntegrationRateLimitEvent(TbRateLimitsException exception, String message) {
        IntegrationId integrationId = configuration.getId();
        EntityType limitedEntity = exception.getEntityType();
        if (ctx.getRateLimitService().alreadyProcessed(integrationId, limitedEntity)) {
            log.trace("[{}] [{}] [{}] Rate limited debug event already sent.", configuration.getTenantId(), integrationId, limitedEntity);
            return;
        }

        var event = IntegrationDebugEvent.builder()
                .tenantId(configuration.getTenantId())
                .entityId(configuration.getId().getId())
                .serviceId(serviceId)
                .eventType("Uplink")
                .message(message)
                .status("ERROR")
                .error(exception.getMessage());
        saveEvent(configuration.getTenantId(), configuration.getId(), event.build());
    }

    private void sendConverterRateLimitEvent(TbEventProto proto) {
        ConverterId converterId = configuration.getDefaultConverterId();
        if (ctx.getRateLimitService().alreadyProcessed(converterId, EntityType.CONVERTER)) {
            log.trace("[{}] [{}] Converter rate limited debug event already sent.", configuration.getTenantId(), converterId);
            return;
        }

        ConverterDebugEvent event = JavaSerDesUtil.decode(proto.getEvent().toByteArray());

        var newConverterEvent = ConverterDebugEvent.builder()
                .tenantId(configuration.getTenantId())
                .entityId(converterId.getId())
                .serviceId(getServiceId())
                .eventType("Uplink")
                .inMsgType(event.getInMsgType())
                .inMsg(event.getInMsg())
                .outMsgType(null)
                .outMsg(null)
                .metadata(event.getMetadata())
                .error("Converter debug rate limits reached!");

        saveEvent(configuration.getTenantId(), converterId, newConverterEvent.build());
    }

    private void saveEvent(TenantId tenantId, EntityId entityId, TbEventProto proto) {
        try {
            if (proto.getEvent() != null && !proto.getEvent().isEmpty()) {
                Event event = JavaSerDesUtil.decode(proto.getEvent().toByteArray());
                event.setTenantId(tenantId);
                event.setEntityId(entityId.getId());
                saveEvent(tenantId, entityId, event);
            } else {
                //TODO: support backward compatibility by parsing the incoming data and converting it to the corresponding event.
                log.warn("[{}][{}] Remote integration [{}] version is not compatible with new event api", configuration.getTenantId(), configuration.getId(), configuration.getName());
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to convert event body!", proto.getEvent(), e);
        }
    }

    private void saveEvent(TenantId tenantId, EntityId entityId, Event event) {
        try {
            ListenableFuture<Void> future = ctx.getEventService().saveAsync(event);

            if (entityId.getEntityType().equals(EntityType.INTEGRATION) && event.getType().equals(EventType.LC_EVENT)) {
                LifecycleEvent lcEvent = (LifecycleEvent) event;
                String key = "integration_status_" + event.getServiceId().toLowerCase();
                if (lcEvent.getLcEventType().equals("STARTED") || lcEvent.getLcEventType().equals("UPDATED")) {
                    ObjectNode value = JacksonUtil.newObjectNode();

                    if (lcEvent.isSuccess()) {
                        value.put("success", true);
                    } else {
                        value.put("success", false);
                        value.put("serviceId", lcEvent.getServiceId());
                        value.put("error", lcEvent.getError());
                    }

                    AttributeKvEntry attr = new BaseAttributeKvEntry(new JsonDataEntry(key, JacksonUtil.toString(value)), event.getCreatedTime());

                    future = Futures.transform(future, v -> {
                        ctx.getAttributesService().save(tenantId, entityId, AttributeScope.SERVER_SCOPE, Collections.singletonList(attr));
                        return null;
                    }, MoreExecutors.directExecutor());
                } else if (lcEvent.getLcEventType().equals("STOPPED")) {
                    future = Futures.transform(future, v -> {
                        ctx.getAttributesService().removeAll(tenantId, entityId, AttributeScope.SERVER_SCOPE, Collections.singletonList(key));
                        return null;
                    }, MoreExecutors.directExecutor());
                }
            }

            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Void event) {
                }

                @Override
                public void onFailure(Throwable th) {
                    log.error("[{}] Failed to save event!", event, th);
                }
            }, MoreExecutors.directExecutor());
        } catch (Exception e) {
            log.warn("[{}] Failed to save event!", event, e);
        }
    }

    private IntegrationConfigurationProto constructIntegrationConfigProto(Integration configuration, ConverterConfigurationProto defaultConverterProto, ConverterConfigurationProto downLinkConverterProto) throws JsonProcessingException {
        return IntegrationConfigurationProto.newBuilder()
                .setIntegrationIdMSB(configuration.getId().getId().getMostSignificantBits())
                .setIntegrationIdLSB(configuration.getId().getId().getLeastSignificantBits())
                .setTenantIdMSB(configuration.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(configuration.getTenantId().getId().getLeastSignificantBits())
                .setUplinkConverter(defaultConverterProto)
                .setDownlinkConverter(downLinkConverterProto)
                .setName(configuration.getName())
                .setRoutingKey(configuration.getRoutingKey())
                .setType(configuration.getType().toString())
                .setDebugMode(configuration.isDebugMode())
                .setConfiguration(JacksonUtil.writeValueAsString(configuration.getConfiguration()))
                .setAdditionalInfo(JacksonUtil.writeValueAsString(configuration.getAdditionalInfo()))
                .setEnabled(configuration.isEnabled())
                .build();
    }

    private ConverterConfigurationProto constructConverterConfigProto(Converter converter) throws JsonProcessingException {
        return ConverterConfigurationProto.newBuilder()
                .setTenantIdMSB(converter.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(converter.getTenantId().getId().getLeastSignificantBits())
                .setConverterIdMSB(converter.getId().getId().getMostSignificantBits())
                .setConverterIdLSB(converter.getId().getId().getLeastSignificantBits())
                .setName(converter.getName())
                .setDebugMode(converter.isDebugMode())
                .setConfiguration(JacksonUtil.toString(converter.getConfiguration()))
                .setAdditionalInfo(JacksonUtil.toString(converter.getAdditionalInfo()))
                .build();
    }

    @Override
    public void close() {
        log.debug("[{}][{}] Closing session", sessionId, configuration.getId());
        connected = false;
        try {
            outputStream.onCompleted();
        } catch (Exception e) {
            log.debug("[{}] Failed to close output stream: {}", sessionId, e.getMessage());
        }
    }

    void onConfigurationUpdate(Integration configuration) {
        try {
            Converter defaultConverter = ctx.getConverterService().findConverterById(configuration.getTenantId(),
                    configuration.getDefaultConverterId());
            ConverterConfigurationProto defaultConverterProto = constructConverterConfigProto(defaultConverter);

            ConverterConfigurationProto downLinkConverterProto = ConverterConfigurationProto.getDefaultInstance();
            if (configuration.getDownlinkConverterId() != null) {
                Converter downlinkConverter = ctx.getConverterService().findConverterById(configuration.getTenantId(),
                        configuration.getDownlinkConverterId());
                downLinkConverterProto = constructConverterConfigProto(downlinkConverter);
            }
            this.configuration = configuration;
            outputStream.onNext(ResponseMsg.newBuilder()
                    .setIntegrationUpdateMsg(IntegrationUpdateMsg.newBuilder()
                            .setConfiguration(constructIntegrationConfigProto(configuration, defaultConverterProto, downLinkConverterProto))
                            .build())
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to construct proto objects!", e);
        }
    }

    void onConverterUpdate(Converter converter) {
        try {
            ConverterConfigurationProto defaultConverterProto = constructConverterConfigProto(converter);

            outputStream.onNext(ResponseMsg.newBuilder()
                    .setConverterUpdateMsg(ConverterUpdateMsg.newBuilder()
                            .setConfiguration(defaultConverterProto)
                            .build())
                    .build());
        } catch (JsonProcessingException e) {
            log.error("Failed to construct proto objects!", e);
        }
    }

    void onDownlink(Device device, IntegrationDownlinkMsg msg) {
        log.debug("[{}] Sending downlink msg [{}]", this.sessionId, msg);
        if (isConnected()) {
            try {
                outputStream.onNext(ResponseMsg.newBuilder()
                        .setDownlinkMsg(DownlinkMsg.newBuilder()
                                .setDeviceData(
                                        DeviceDownlinkDataProto.newBuilder()
                                                .setDeviceName(device.getName())
                                                .setDeviceType(device.getType())
                                                .setTbMsg(TbMsg.toByteString(msg.getTbMsg()))
                                                .build()
                                )
                                .build())
                        .build());
            } catch (Exception e) {
                log.error("[{}] Failed to send downlink msg [{}]", this.sessionId, msg, e);
                connected = false;
                sessionCloseListener.accept(configuration.getId());
            }
            log.debug("[{}] Downlink msg successfully sent [{}]", this.sessionId, msg);
        } else {
            log.debug("[{}] Ignore downlink due to disconnected session", this.sessionId);
        }
    }

    private void processIntegrationStatistics(IntegrationStatisticsProto data) {
        List<TsKvEntry> statsTs = new ArrayList<>();
        for (TransportProtos.TsKvListProto tsKvListProto : data.getPostTelemetryMsg().getTsKvListList()) {
            for (TransportProtos.KeyValueProto keyValueProto : tsKvListProto.getKvList()) {
                if (keyValueProto.getType().equals(TransportProtos.KeyValueType.LONG_V)) {
                    statsTs.add(new BasicTsKvEntry(tsKvListProto.getTs(), new LongDataEntry(keyValueProto.getKey(), keyValueProto.getLongV())));
                } else if (keyValueProto.getType().equals(TransportProtos.KeyValueType.DOUBLE_V)) {
                    statsTs.add(new BasicTsKvEntry(tsKvListProto.getTs(), new DoubleDataEntry(keyValueProto.getKey(), keyValueProto.getDoubleV())));
                } else if (keyValueProto.getType().equals(TransportProtos.KeyValueType.BOOLEAN_V)) {
                    statsTs.add(new BasicTsKvEntry(tsKvListProto.getTs(), new BooleanDataEntry(keyValueProto.getKey(), keyValueProto.getBoolV())));
                } else {
                    statsTs.add(new BasicTsKvEntry(tsKvListProto.getTs(), new StringDataEntry(keyValueProto.getKey(), keyValueProto.getStringV())));
                }
            }
        }
        ctx.getTelemetrySubscriptionService().saveAndNotifyInternal(configuration.getTenantId(), configuration.getId(), statsTs, new FutureCallback<Integer>() {
            @Override
            public void onSuccess(@Nullable Integer result) {
                log.trace("[{}] Persisted statistics telemetry!", configuration.getId());
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to persist statistics telemetry!", configuration.getId(), t);
            }
        });
    }
}
