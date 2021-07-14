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
package org.thingsboard.server.transport.coap;

import com.google.gson.JsonParseException;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.resources.ResourceObserver;
import org.springframework.util.CollectionUtils;
import org.thingsboard.server.coapserver.CoapServerService;
import org.thingsboard.server.coapserver.TbCoapDtlsSessionInfo;
import org.thingsboard.server.common.adaptor.AdaptorException;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TransportPayloadType;
import org.thingsboard.server.common.data.device.profile.CoapDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.CoapDeviceTypeConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultCoapDeviceTypeConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.DeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.JsonTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.ProtoTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.TransportPayloadTypeConfiguration;
import org.thingsboard.server.common.data.security.DeviceTokenCredentials;
import org.thingsboard.server.common.msg.session.FeatureType;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.common.transport.SessionMsgListener;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ProvisionDeviceResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ResponseStatus;
import org.thingsboard.server.gen.transport.TransportProtos.SessionCloseNotificationProto;
import org.thingsboard.server.gen.transport.TransportProtos.SessionEvent;
import org.thingsboard.server.gen.transport.TransportProtos.SessionInfoProto;
import org.thingsboard.server.gen.transport.TransportProtos.SubscribeToAttributeUpdatesMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SubscribeToRPCMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ValidateDeviceTokenRequestMsg;
import org.thingsboard.server.transport.coap.adaptors.CoapTransportAdaptor;
import org.thingsboard.server.transport.coap.callback.CoapDeviceAuthCallback;
import org.thingsboard.server.transport.coap.callback.CoapNoOpCallback;
import org.thingsboard.server.transport.coap.callback.CoapOkCallback;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class CoapTransportResource extends AbstractCoapTransportResource {
    private static final int ACCESS_TOKEN_POSITION = 3;
    private static final int FEATURE_TYPE_POSITION = 4;
    private static final int REQUEST_ID_POSITION = 5;

    private static final int FEATURE_TYPE_POSITION_CERTIFICATE_REQUEST = 3;
    private static final int REQUEST_ID_POSITION_CERTIFICATE_REQUEST = 4;
    private static final String DTLS_SESSION_ID_KEY = "DTLS_SESSION_ID";

    private static final String INTEGRATIONS_RESOURCE_NAME = "i";

    private final ConcurrentMap<String, CoapObserveSessionInfo> tokenToCoapSessionInfoMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<CoapObserveSessionInfo, ObserveRelation> sessionInfoToObserveRelationMap = new ConcurrentHashMap<>();

    private final Set<UUID> rpcSubscriptions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> attributeSubscriptions = ConcurrentHashMap.newKeySet();

    private final ConcurrentMap<String, TbCoapDtlsSessionInfo> dtlsSessionIdMap;
    private final long timeout;

    public CoapTransportResource(CoapTransportContext ctx, CoapServerService coapServerService, String name) {
        super(ctx, name);
        this.setObservable(true); // enable observing
        this.addObserver(new CoapResourceObserver());
        this.dtlsSessionIdMap = coapServerService.getDtlsSessionsMap();
        this.timeout = coapServerService.getTimeout();
        long sessionReportTimeout = ctx.getSessionReportTimeout();
        ctx.getScheduler().scheduleAtFixedRate(() -> {
            Set<CoapObserveSessionInfo> coapObserveSessionInfos = sessionInfoToObserveRelationMap.keySet();
            Set<TransportProtos.SessionInfoProto> observeSessions = coapObserveSessionInfos
                    .stream()
                    .map(CoapObserveSessionInfo::getSessionInfoProto)
                    .collect(Collectors.toSet());
            observeSessions.forEach(this::reportActivity);
        }, new Random().nextInt((int) sessionReportTimeout), sessionReportTimeout, TimeUnit.MILLISECONDS);
    }

    @Override
    public void checkObserveRelation(Exchange exchange, Response response) {
        String token = getTokenFromRequest(exchange.getRequest());
        final ObserveRelation relation = exchange.getRelation();
        if (relation == null || relation.isCanceled()) {
            return; // because request did not try to establish a relation
        }
        if (CoAP.ResponseCode.isSuccess(response.getCode())) {
            if (!relation.isEstablished()) {
                relation.setEstablished();
                addObserveRelation(relation);
            }
            AtomicInteger observeNotificationCounter = tokenToCoapSessionInfoMap.get(token).getObserveNotificationCounter();
            response.getOptions().setObserve(observeNotificationCounter.getAndIncrement());
        } // ObserveLayer takes care of the else case
    }

    private void clearAndNotifyObserveRelation(ObserveRelation relation, CoAP.ResponseCode code) {
        relation.cancel();
        relation.getExchange().sendResponse(new Response(code));
    }

    private Map<CoapObserveSessionInfo, ObserveRelation> getCoapSessionInfoToObserveRelationMap() {
        return sessionInfoToObserveRelationMap;
    }

    @Override
    protected void processHandleGet(CoapExchange exchange) {
        Optional<FeatureType> featureType = getFeatureType(exchange.advanced().getRequest());
        if (featureType.isEmpty()) {
            log.trace("Missing feature type parameter");
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        } else if (featureType.get() == FeatureType.TELEMETRY) {
            log.trace("Can't fetch/subscribe to timeseries updates");
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        } else if (exchange.getRequestOptions().hasObserve()) {
            processExchangeGetRequest(exchange, featureType.get());
        } else if (featureType.get() == FeatureType.ATTRIBUTES) {
            processRequest(exchange, SessionMsgType.GET_ATTRIBUTES_REQUEST);
        } else {
            log.trace("Invalid feature type parameter");
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        }
    }

    private void processExchangeGetRequest(CoapExchange exchange, FeatureType featureType) {
        boolean unsubscribe = exchange.getRequestOptions().getObserve() == 1;
        SessionMsgType sessionMsgType;
        if (featureType == FeatureType.RPC) {
            sessionMsgType = unsubscribe ? SessionMsgType.UNSUBSCRIBE_RPC_COMMANDS_REQUEST : SessionMsgType.SUBSCRIBE_RPC_COMMANDS_REQUEST;
        } else {
            sessionMsgType = unsubscribe ? SessionMsgType.UNSUBSCRIBE_ATTRIBUTES_REQUEST : SessionMsgType.SUBSCRIBE_ATTRIBUTES_REQUEST;
        }
        processRequest(exchange, sessionMsgType);
    }

    @Override
    protected void processHandlePost(CoapExchange exchange) {
        Optional<FeatureType> featureType = getFeatureType(exchange.advanced().getRequest());
        if (featureType.isEmpty()) {
            log.trace("Missing feature type parameter");
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        } else {
            switch (featureType.get()) {
                case ATTRIBUTES:
                    processRequest(exchange, SessionMsgType.POST_ATTRIBUTES_REQUEST);
                    break;
                case TELEMETRY:
                    processRequest(exchange, SessionMsgType.POST_TELEMETRY_REQUEST);
                    break;
                case RPC:
                    Optional<Integer> requestId = getRequestId(exchange.advanced().getRequest());
                    if (requestId.isPresent()) {
                        processRequest(exchange, SessionMsgType.TO_DEVICE_RPC_RESPONSE);
                    } else {
                        processRequest(exchange, SessionMsgType.TO_SERVER_RPC_REQUEST);
                    }
                    break;
                case CLAIM:
                    processRequest(exchange, SessionMsgType.CLAIM_REQUEST);
                    break;
                case PROVISION:
                    processProvision(exchange);
                    break;
            }
        }
    }

    private void processProvision(CoapExchange exchange) {
        exchange.accept();
        try {
            UUID sessionId = UUID.randomUUID();
            log.trace("[{}] Processing provision publish msg [{}]!", sessionId, exchange.advanced().getRequest());
            ProvisionDeviceRequestMsg provisionRequestMsg;
            TransportPayloadType payloadType;
            try {
                provisionRequestMsg = transportContext.getJsonCoapAdaptor().convertToProvisionRequestMsg(sessionId, exchange.advanced().getRequest());
                payloadType = TransportPayloadType.JSON;
            } catch (Exception e) {
                if (e instanceof JsonParseException || (e.getCause() != null && e.getCause() instanceof JsonParseException)) {
                    provisionRequestMsg = transportContext.getProtoCoapAdaptor().convertToProvisionRequestMsg(sessionId, exchange.advanced().getRequest());
                    payloadType = TransportPayloadType.PROTOBUF;
                } else {
                    throw new AdaptorException(e);
                }
            }
            transportService.process(provisionRequestMsg, new DeviceProvisionCallback(exchange, payloadType));
        } catch (AdaptorException e) {
            log.trace("Failed to decode message: ", e);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        }
    }

    private void processRequest(CoapExchange exchange, SessionMsgType type) {
        log.trace("Processing {}", exchange.advanced().getRequest());
        exchange.accept();
        Exchange advanced = exchange.advanced();
        Request request = advanced.getRequest();

        String dtlsSessionIdStr = request.getSourceContext().get(DTLS_SESSION_ID_KEY);
        if (dtlsSessionIdMap != null && StringUtils.isNotEmpty(dtlsSessionIdStr)) {
            TbCoapDtlsSessionInfo tbCoapDtlsSessionInfo = dtlsSessionIdMap
                    .computeIfPresent(dtlsSessionIdStr, (dtlsSessionId, dtlsSessionInfo) -> {
                        dtlsSessionInfo.setLastActivityTime(System.currentTimeMillis());
                        return dtlsSessionInfo;
                    });
            if (tbCoapDtlsSessionInfo != null) {
                processRequest(exchange, type, request, tbCoapDtlsSessionInfo.getSessionInfoProto(), tbCoapDtlsSessionInfo.getDeviceProfile());
            } else {
                processAccessTokenRequest(exchange, type, request);
            }
        } else {
            processAccessTokenRequest(exchange, type, request);
        }
    }

    private void processAccessTokenRequest(CoapExchange exchange, SessionMsgType type, Request request) {
        Optional<DeviceTokenCredentials> credentials = decodeCredentials(request);
        if (credentials.isEmpty()) {
            exchange.respond(CoAP.ResponseCode.UNAUTHORIZED);
            return;
        }
        transportService.process(DeviceTransportType.COAP, ValidateDeviceTokenRequestMsg.newBuilder().setToken(credentials.get().getCredentialsId()).build(),
                new CoapDeviceAuthCallback(transportContext, exchange, (sessionInfo, deviceProfile) -> {
                    processRequest(exchange, type, request, sessionInfo, deviceProfile);
                }));
    }

    private void processRequest(CoapExchange exchange, SessionMsgType type, Request request, SessionInfoProto sessionInfo, DeviceProfile deviceProfile) {
        UUID sessionId = toSessionId(sessionInfo);
        try {
            TransportConfigurationContainer transportConfigurationContainer = getTransportConfigurationContainer(deviceProfile);
            CoapTransportAdaptor coapTransportAdaptor = getCoapTransportAdaptor(transportConfigurationContainer.isJsonPayload());
            switch (type) {
                case POST_ATTRIBUTES_REQUEST:
                    transportService.process(sessionInfo,
                            coapTransportAdaptor.convertToPostAttributes(sessionId, request,
                                    transportConfigurationContainer.getAttributesMsgDescriptor()),
                            new CoapOkCallback(exchange, CoAP.ResponseCode.CREATED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    reportSubscriptionInfo(sessionInfo, attributeSubscriptions.contains(sessionId), rpcSubscriptions.contains(sessionId));
                    break;
                case POST_TELEMETRY_REQUEST:
                    transportService.process(sessionInfo,
                            coapTransportAdaptor.convertToPostTelemetry(sessionId, request,
                                    transportConfigurationContainer.getTelemetryMsgDescriptor()),
                            new CoapOkCallback(exchange, CoAP.ResponseCode.CREATED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    reportSubscriptionInfo(sessionInfo, attributeSubscriptions.contains(sessionId), rpcSubscriptions.contains(sessionId));
                    break;
                case CLAIM_REQUEST:
                    transportService.process(sessionInfo,
                            coapTransportAdaptor.convertToClaimDevice(sessionId, request, sessionInfo),
                            new CoapOkCallback(exchange, CoAP.ResponseCode.CREATED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    break;
                case SUBSCRIBE_ATTRIBUTES_REQUEST:
                    CoapObserveSessionInfo currentCoapObserveAttrSessionInfo = tokenToCoapSessionInfoMap.get(getTokenFromRequest(request));
                    if (currentCoapObserveAttrSessionInfo == null) {
                        attributeSubscriptions.add(sessionId);
                        registerAsyncCoapSession(exchange, coapTransportAdaptor, transportConfigurationContainer.getRpcRequestDynamicMessageBuilder(),
                                sessionInfo, getTokenFromRequest(request));
                        transportService.process(sessionInfo,
                                SubscribeToAttributeUpdatesMsg.getDefaultInstance(), new CoapNoOpCallback(exchange));
                        transportService.process(sessionInfo,
                                TransportProtos.GetAttributeRequestMsg.newBuilder().setOnlyShared(true).build(),
                                new CoapNoOpCallback(exchange));
                    }
                    break;
                case UNSUBSCRIBE_ATTRIBUTES_REQUEST:
                    CoapObserveSessionInfo coapObserveAttrSessionInfo = lookupAsyncSessionInfo(getTokenFromRequest(request));
                    if (coapObserveAttrSessionInfo != null) {
                        TransportProtos.SessionInfoProto attrSession = coapObserveAttrSessionInfo.getSessionInfoProto();
                        UUID attrSessionId = toSessionId(attrSession);
                        attributeSubscriptions.remove(attrSessionId);
                        transportService.process(attrSession,
                                TransportProtos.SubscribeToAttributeUpdatesMsg.newBuilder().setUnsubscribe(true).build(),
                                new CoapOkCallback(exchange, CoAP.ResponseCode.DELETED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    }
                    closeAndDeregister(sessionInfo);
                    break;
                case SUBSCRIBE_RPC_COMMANDS_REQUEST:
                    CoapObserveSessionInfo currentCoapObserveRpcSessionInfo = tokenToCoapSessionInfoMap.get(getTokenFromRequest(request));
                    if (currentCoapObserveRpcSessionInfo == null) {
                        rpcSubscriptions.add(sessionId);
                        registerAsyncCoapSession(exchange, coapTransportAdaptor, transportConfigurationContainer.getRpcRequestDynamicMessageBuilder()
                                , sessionInfo, getTokenFromRequest(request));
                        transportService.process(sessionInfo,
                                SubscribeToRPCMsg.getDefaultInstance(),
                                new CoapOkCallback(exchange, CoAP.ResponseCode.VALID, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    }
                    break;
                case UNSUBSCRIBE_RPC_COMMANDS_REQUEST:
                    CoapObserveSessionInfo coapObserveRpcSessionInfo = lookupAsyncSessionInfo(getTokenFromRequest(request));
                    if (coapObserveRpcSessionInfo != null) {
                        TransportProtos.SessionInfoProto rpcSession = coapObserveRpcSessionInfo.getSessionInfoProto();
                        UUID rpcSessionId = toSessionId(rpcSession);
                        rpcSubscriptions.remove(rpcSessionId);
                        transportService.process(rpcSession,
                                SubscribeToRPCMsg.newBuilder().setUnsubscribe(true).build(),
                                new CoapOkCallback(exchange, CoAP.ResponseCode.DELETED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    }
                    closeAndDeregister(sessionInfo);
                    break;
                case TO_DEVICE_RPC_RESPONSE:
                    transportService.process(sessionInfo,
                            coapTransportAdaptor.convertToDeviceRpcResponse(sessionId, request, transportConfigurationContainer.getRpcResponseMsgDescriptor()),
                            new CoapOkCallback(exchange, CoAP.ResponseCode.CREATED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
                    break;
                case TO_SERVER_RPC_REQUEST:
                    transportService.registerSyncSession(sessionInfo, getCoapSessionListener(exchange, coapTransportAdaptor,
                            transportConfigurationContainer.getRpcRequestDynamicMessageBuilder(), sessionInfo), timeout);
                    transportService.process(sessionInfo,
                            coapTransportAdaptor.convertToServerRpcRequest(sessionId, request),
                            new CoapNoOpCallback(exchange));
                    break;
                case GET_ATTRIBUTES_REQUEST:
                    transportService.registerSyncSession(sessionInfo, getCoapSessionListener(exchange, coapTransportAdaptor,
                            transportConfigurationContainer.getRpcRequestDynamicMessageBuilder(), sessionInfo), timeout);
                    transportService.process(sessionInfo,
                            coapTransportAdaptor.convertToGetAttributes(sessionId, request),
                            new CoapNoOpCallback(exchange));
                    break;
            }
        } catch (AdaptorException e) {
            log.trace("[{}] Failed to decode message: ", sessionId, e);
            exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
        }
    }

    private UUID toSessionId(TransportProtos.SessionInfoProto sessionInfoProto) {
        return new UUID(sessionInfoProto.getSessionIdMSB(), sessionInfoProto.getSessionIdLSB());
    }

    private CoapObserveSessionInfo lookupAsyncSessionInfo(String token) {
        return tokenToCoapSessionInfoMap.remove(token);
    }

    private void registerAsyncCoapSession(CoapExchange exchange, CoapTransportAdaptor coapTransportAdaptor,
                                          DynamicMessage.Builder rpcRequestDynamicMessageBuilder, TransportProtos.SessionInfoProto sessionInfo, String token) {
        tokenToCoapSessionInfoMap.putIfAbsent(token, new CoapObserveSessionInfo(sessionInfo));
        transportService.registerAsyncSession(sessionInfo, getCoapSessionListener(exchange, coapTransportAdaptor, rpcRequestDynamicMessageBuilder, sessionInfo));
        transportService.process(sessionInfo, getSessionEventMsg(SessionEvent.OPEN), null);
    }

    private CoapSessionListener getCoapSessionListener(CoapExchange exchange, CoapTransportAdaptor coapTransportAdaptor,
                                                       DynamicMessage.Builder rpcRequestDynamicMessageBuilder, TransportProtos.SessionInfoProto sessionInfo) {
        return new CoapSessionListener(exchange, coapTransportAdaptor, rpcRequestDynamicMessageBuilder, sessionInfo);
    }

    private String getTokenFromRequest(Request request) {
        return (request.getSourceContext() != null ? request.getSourceContext().getPeerAddress().getAddress().getHostAddress() : "null")
                + ":" + (request.getSourceContext() != null ? request.getSourceContext().getPeerAddress().getPort() : -1) + ":" + request.getTokenString();
    }

    private Optional<DeviceTokenCredentials> decodeCredentials(Request request) {
        List<String> uriPath = request.getOptions().getUriPath();
        if (uriPath.size() > ACCESS_TOKEN_POSITION) {
            return Optional.of(new DeviceTokenCredentials(uriPath.get(ACCESS_TOKEN_POSITION - 1)));
        } else {
            return Optional.empty();
        }
    }

    private Optional<FeatureType> getFeatureType(Request request) {
        List<String> uriPath = request.getOptions().getUriPath();
        try {
            if (uriPath.size() >= FEATURE_TYPE_POSITION) {
                return Optional.of(FeatureType.valueOf(uriPath.get(FEATURE_TYPE_POSITION - 1).toUpperCase()));
            } else if (uriPath.size() >= FEATURE_TYPE_POSITION_CERTIFICATE_REQUEST) {
                if (uriPath.contains(DataConstants.PROVISION)) {
                    return Optional.of(FeatureType.valueOf(DataConstants.PROVISION.toUpperCase()));
                }
                return Optional.of(FeatureType.valueOf(uriPath.get(FEATURE_TYPE_POSITION_CERTIFICATE_REQUEST - 1).toUpperCase()));
            }
        } catch (RuntimeException e) {
            log.warn("Failed to decode feature type: {}", uriPath);
        }
        return Optional.empty();
    }

    public static Optional<Integer> getRequestId(Request request) {
        List<String> uriPath = request.getOptions().getUriPath();
        try {
            if (uriPath.size() >= REQUEST_ID_POSITION) {
                return Optional.of(Integer.valueOf(uriPath.get(REQUEST_ID_POSITION - 1)));
            } else {
                return Optional.of(Integer.valueOf(uriPath.get(REQUEST_ID_POSITION_CERTIFICATE_REQUEST - 1)));
            }
        } catch (RuntimeException e) {
            log.warn("Failed to decode feature type: {}", uriPath);
        }
        return Optional.empty();
    }

    @Override
    public Resource getChild(String name) {
        if (INTEGRATIONS_RESOURCE_NAME.equals(name)) {
            Collection<Resource> children = getChildren();
            Resource integrationResource = null;
            for (Resource resource : children) {
                if (INTEGRATIONS_RESOURCE_NAME.equals(resource.getName())) {
                    integrationResource = resource;
                    break;
                }
            }
            return integrationResource;
        }
        return this;
    }

    private static class DeviceProvisionCallback implements TransportServiceCallback<ProvisionDeviceResponseMsg> {
        private final CoapExchange exchange;
        private final TransportPayloadType payloadType;

        DeviceProvisionCallback(CoapExchange exchange, TransportPayloadType payloadType) {
            this.exchange = exchange;
            this.payloadType = payloadType;
        }

        @Override
        public void onSuccess(ProvisionDeviceResponseMsg msg) {
            CoAP.ResponseCode responseCode = CoAP.ResponseCode.CREATED;
            if (!msg.getStatus().equals(ResponseStatus.SUCCESS)) {
                responseCode = CoAP.ResponseCode.BAD_REQUEST;
            }
            if (payloadType.equals(TransportPayloadType.JSON)) {
                exchange.respond(responseCode, JsonConverter.toJson(msg).toString());
            } else {
                exchange.respond(responseCode, msg.toByteArray());
            }
        }

        @Override
        public void onError(Throwable e) {
            log.warn("Failed to process request", e);
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @RequiredArgsConstructor
    private class CoapSessionListener implements SessionMsgListener {

        private final CoapExchange exchange;
        private final CoapTransportAdaptor coapTransportAdaptor;
        private final DynamicMessage.Builder rpcRequestDynamicMessageBuilder;
        private final TransportProtos.SessionInfoProto sessionInfo;

        @Override
        public void onGetAttributesResponse(TransportProtos.GetAttributeResponseMsg msg) {
            try {
                exchange.respond(coapTransportAdaptor.convertToPublish(isConRequest(), msg));
            } catch (AdaptorException e) {
                log.trace("Failed to reply due to error", e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        @Override
        public void onAttributeUpdate(UUID sessionId, TransportProtos.AttributeUpdateNotificationMsg msg) {
            log.trace("[{}] Received attributes update notification to device", sessionId);
            try {
                exchange.respond(coapTransportAdaptor.convertToPublish(isConRequest(), msg));
            } catch (AdaptorException e) {
                log.trace("Failed to reply due to error", e);
                closeObserveRelationAndNotify(sessionId, CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
                closeAndDeregister();
            }
        }

        @Override
        public void onRemoteSessionCloseCommand(UUID sessionId, TransportProtos.SessionCloseNotificationProto sessionCloseNotification) {
            log.trace("[{}] Received the remote command to close the session: {}", sessionId, sessionCloseNotification.getMessage());
            closeObserveRelationAndNotify(sessionId, CoAP.ResponseCode.SERVICE_UNAVAILABLE);
            closeAndDeregister();
        }

        @Override
        public void onToDeviceRpcRequest(UUID sessionId, TransportProtos.ToDeviceRpcRequestMsg msg) {
            log.trace("[{}] Received RPC command to device", sessionId);
            boolean sent = false;
            try {
                Response response = coapTransportAdaptor.convertToPublish(isConRequest(), msg, rpcRequestDynamicMessageBuilder);
                int requestId = getNextMsgId();
                response.setMID(requestId);

                if (msg.getPersisted() && isConRequest()) {
                    transportContext.getRpcAwaitingAck().put(requestId, msg);
                    transportContext.getScheduler().schedule(() -> {
                        TransportProtos.ToDeviceRpcRequestMsg awaitingAckMsg = transportContext.getRpcAwaitingAck().remove(requestId);
                        if (awaitingAckMsg != null) {
                            transportService.process(sessionInfo, msg, true, TransportServiceCallback.EMPTY);
                        }
                    }, Math.max(0, msg.getExpirationTime() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                    response.addMessageObserver(new TbCoapMessageObserver(requestId, id -> {
                        TransportProtos.ToDeviceRpcRequestMsg rpcRequestMsg = transportContext.getRpcAwaitingAck().remove(id);
                        if (rpcRequestMsg != null) {
                            transportService.process(sessionInfo, rpcRequestMsg, false, TransportServiceCallback.EMPTY);
                        }
                    }));
                }
                exchange.respond(response);
                sent = true;
            } catch (AdaptorException e) {
                log.trace("Failed to reply due to error", e);
                closeObserveRelationAndNotify(sessionId, CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
                closeAndDeregister();
            } finally {
                if (msg.getPersisted() && !isConRequest()) {
                    transportService.process(sessionInfo, msg, sent, TransportServiceCallback.EMPTY);
                }
            }
        }

        @Override
        public void onToServerRpcResponse(TransportProtos.ToServerRpcResponseMsg msg) {
            try {
                exchange.respond(coapTransportAdaptor.convertToPublish(isConRequest(), msg));
            } catch (AdaptorException e) {
                log.trace("Failed to reply due to error", e);
                exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
            }
        }

        private boolean isConRequest() {
            return exchange.advanced().getRequest().isConfirmable();
        }

        private void closeObserveRelationAndNotify(UUID sessionId, CoAP.ResponseCode responseCode) {
            Map<CoapObserveSessionInfo, ObserveRelation> sessionToObserveRelationMap = CoapTransportResource.this.getCoapSessionInfoToObserveRelationMap();
            if (CoapTransportResource.this.getObserverCount() > 0 && !CollectionUtils.isEmpty(sessionToObserveRelationMap)) {
                Optional<CoapObserveSessionInfo> observeSessionToClose = sessionToObserveRelationMap.keySet().stream().filter(coapObserveSessionInfo -> {
                    TransportProtos.SessionInfoProto sessionToDelete = coapObserveSessionInfo.getSessionInfoProto();
                    UUID observeSessionId = new UUID(sessionToDelete.getSessionIdMSB(), sessionToDelete.getSessionIdLSB());
                    return observeSessionId.equals(sessionId);
                }).findFirst();
                if (observeSessionToClose.isPresent()) {
                    CoapObserveSessionInfo coapObserveSessionInfo = observeSessionToClose.get();
                    ObserveRelation observeRelation = sessionToObserveRelationMap.get(coapObserveSessionInfo);
                    CoapTransportResource.this.clearAndNotifyObserveRelation(observeRelation, responseCode);
                }
            }
        }

        private void closeAndDeregister() {
            Request request = exchange.advanced().getRequest();
            String token = CoapTransportResource.this.getTokenFromRequest(request);
            CoapObserveSessionInfo deleted = CoapTransportResource.this.lookupAsyncSessionInfo(token);
            CoapTransportResource.this.closeAndDeregister(deleted.getSessionInfoProto());
        }

    }

    public class CoapResourceObserver implements ResourceObserver {

        @Override
        public void changedName(String old) {
        }

        @Override
        public void changedPath(String old) {
        }

        @Override
        public void addedChild(Resource child) {
        }

        @Override
        public void removedChild(Resource child) {
        }

        @Override
        public void addedObserveRelation(ObserveRelation relation) {
            Request request = relation.getExchange().getRequest();
            String token = getTokenFromRequest(request);
            sessionInfoToObserveRelationMap.putIfAbsent(tokenToCoapSessionInfoMap.get(token), relation);
            log.trace("Added Observe relation for token: {}", token);
        }

        @Override
        public void removedObserveRelation(ObserveRelation relation) {
            Request request = relation.getExchange().getRequest();
            String token = getTokenFromRequest(request);
            sessionInfoToObserveRelationMap.remove(tokenToCoapSessionInfoMap.get(token));
            log.trace("Relation removed for token: {}", token);
        }
    }

    private void closeAndDeregister(SessionInfoProto session) {
        UUID sessionId = toSessionId(session);
        transportService.process(session, getSessionEventMsg(SessionEvent.CLOSED), null);
        transportService.deregisterSession(session);
        rpcSubscriptions.remove(sessionId);
        attributeSubscriptions.remove(sessionId);
    }

    private TransportConfigurationContainer getTransportConfigurationContainer(DeviceProfile deviceProfile) throws AdaptorException {
        DeviceProfileTransportConfiguration transportConfiguration = deviceProfile.getProfileData().getTransportConfiguration();
        if (transportConfiguration instanceof DefaultDeviceProfileTransportConfiguration) {
            return new TransportConfigurationContainer(true);
        } else if (transportConfiguration instanceof CoapDeviceProfileTransportConfiguration) {
            CoapDeviceProfileTransportConfiguration coapDeviceProfileTransportConfiguration =
                    (CoapDeviceProfileTransportConfiguration) transportConfiguration;
            CoapDeviceTypeConfiguration coapDeviceTypeConfiguration =
                    coapDeviceProfileTransportConfiguration.getCoapDeviceTypeConfiguration();
            if (coapDeviceTypeConfiguration instanceof DefaultCoapDeviceTypeConfiguration) {
                DefaultCoapDeviceTypeConfiguration defaultCoapDeviceTypeConfiguration =
                        (DefaultCoapDeviceTypeConfiguration) coapDeviceTypeConfiguration;
                TransportPayloadTypeConfiguration transportPayloadTypeConfiguration =
                        defaultCoapDeviceTypeConfiguration.getTransportPayloadTypeConfiguration();
                if (transportPayloadTypeConfiguration instanceof JsonTransportPayloadConfiguration) {
                    return new TransportConfigurationContainer(true);
                } else {
                    ProtoTransportPayloadConfiguration protoTransportPayloadConfiguration =
                            (ProtoTransportPayloadConfiguration) transportPayloadTypeConfiguration;
                    String deviceTelemetryProtoSchema = protoTransportPayloadConfiguration.getDeviceTelemetryProtoSchema();
                    String deviceAttributesProtoSchema = protoTransportPayloadConfiguration.getDeviceAttributesProtoSchema();
                    String deviceRpcRequestProtoSchema = protoTransportPayloadConfiguration.getDeviceRpcRequestProtoSchema();
                    String deviceRpcResponseProtoSchema = protoTransportPayloadConfiguration.getDeviceRpcResponseProtoSchema();
                    return new TransportConfigurationContainer(false,
                            protoTransportPayloadConfiguration.getTelemetryDynamicMessageDescriptor(deviceTelemetryProtoSchema),
                            protoTransportPayloadConfiguration.getAttributesDynamicMessageDescriptor(deviceAttributesProtoSchema),
                            protoTransportPayloadConfiguration.getRpcResponseDynamicMessageDescriptor(deviceRpcResponseProtoSchema),
                            protoTransportPayloadConfiguration.getRpcRequestDynamicMessageBuilder(deviceRpcRequestProtoSchema)
                    );
                }
            } else {
                throw new AdaptorException("Invalid CoapDeviceTypeConfiguration type: " + coapDeviceTypeConfiguration.getClass().getSimpleName() + "!");
            }
        } else {
            throw new AdaptorException("Invalid DeviceProfileTransportConfiguration type" + transportConfiguration.getClass().getSimpleName() + "!");
        }
    }

    private CoapTransportAdaptor getCoapTransportAdaptor(boolean jsonPayloadType) {
        return jsonPayloadType ? transportContext.getJsonCoapAdaptor() : transportContext.getProtoCoapAdaptor();
    }

    @Data
    private static class TransportConfigurationContainer {

        private boolean jsonPayload;
        private Descriptors.Descriptor telemetryMsgDescriptor;
        private Descriptors.Descriptor attributesMsgDescriptor;
        private Descriptors.Descriptor rpcResponseMsgDescriptor;
        private DynamicMessage.Builder rpcRequestDynamicMessageBuilder;

        public TransportConfigurationContainer(boolean jsonPayload, Descriptors.Descriptor telemetryMsgDescriptor, Descriptors.Descriptor attributesMsgDescriptor, Descriptors.Descriptor rpcResponseMsgDescriptor, DynamicMessage.Builder rpcRequestDynamicMessageBuilder) {
            this.jsonPayload = jsonPayload;
            this.telemetryMsgDescriptor = telemetryMsgDescriptor;
            this.attributesMsgDescriptor = attributesMsgDescriptor;
            this.rpcResponseMsgDescriptor = rpcResponseMsgDescriptor;
            this.rpcRequestDynamicMessageBuilder = rpcRequestDynamicMessageBuilder;
        }

        public TransportConfigurationContainer(boolean jsonPayload) {
            this.jsonPayload = jsonPayload;
        }
    }

    @Data
    private static class CoapObserveSessionInfo {

        private final TransportProtos.SessionInfoProto sessionInfoProto;
        private final AtomicInteger observeNotificationCounter;

        private CoapObserveSessionInfo(TransportProtos.SessionInfoProto sessionInfoProto) {
            this.sessionInfoProto = sessionInfoProto;
            this.observeNotificationCounter = new AtomicInteger(0);
        }
    }

}
