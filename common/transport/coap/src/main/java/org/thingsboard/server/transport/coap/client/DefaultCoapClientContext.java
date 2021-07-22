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
package org.thingsboard.server.transport.coap.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.springframework.stereotype.Service;
import org.thingsboard.server.coapserver.CoapServerContext;
import org.thingsboard.server.coapserver.TbCoapServerComponent;
import org.thingsboard.server.common.adaptor.AdaptorException;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.device.data.PowerMode;
import org.thingsboard.server.common.data.device.data.PowerSavingConfiguration;
import org.thingsboard.server.common.data.device.profile.CoapDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.CoapDeviceTypeConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultCoapDeviceTypeConfiguration;
import org.thingsboard.server.common.data.device.profile.DefaultDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.DeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.device.profile.JsonTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.ProtoTransportPayloadConfiguration;
import org.thingsboard.server.common.data.device.profile.TransportPayloadTypeConfiguration;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.msg.session.FeatureType;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.common.transport.SessionMsgListener;
import org.thingsboard.server.common.transport.TransportDeviceProfileCache;
import org.thingsboard.server.common.transport.TransportService;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.common.transport.auth.SessionInfoCreator;
import org.thingsboard.server.common.transport.auth.ValidateDeviceCredentialsResponse;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.transport.coap.CoapTransportContext;
import org.thingsboard.server.transport.coap.TbCoapMessageObserver;
import org.thingsboard.server.transport.coap.TransportConfigurationContainer;
import org.thingsboard.server.transport.coap.adaptors.CoapTransportAdaptor;
import org.thingsboard.server.transport.coap.callback.AbstractSyncSessionCallback;
import org.thingsboard.server.transport.coap.callback.CoapNoOpCallback;
import org.thingsboard.server.transport.coap.callback.CoapOkCallback;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.californium.core.coap.Message.MAX_MID;
import static org.eclipse.californium.core.coap.Message.NONE;

@Slf4j
@Service
@RequiredArgsConstructor
@TbCoapServerComponent
public class DefaultCoapClientContext implements CoapClientContext {

    private final CoapServerContext config;
    private final CoapTransportContext transportContext;
    private final TransportService transportService;
    private final TransportDeviceProfileCache profileCache;
    private final ConcurrentMap<DeviceId, TbCoapClientState> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TbCoapClientState> clientsByToken = new ConcurrentHashMap<>();

    @Override
    public boolean registerAttributeObservation(TbCoapClientState clientState, String token, CoapExchange exchange) {
        return registerFeatureObservation(clientState, token, exchange, FeatureType.ATTRIBUTES);
    }

    @Override
    public boolean registerRpcObservation(TbCoapClientState clientState, String token, CoapExchange exchange) {
        return registerFeatureObservation(clientState, token, exchange, FeatureType.RPC);
    }

    @Override
    public AtomicInteger getNotificationCounterByToken(String token) {
        TbCoapClientState state = clientsByToken.get(token);
        if (state == null) {
            log.trace("Failed to find state using token: {}", token);
            return null;
        }
        if (state.getAttrs() != null && state.getAttrs().getToken().equals(token)) {
            return state.getAttrs().getObserveCounter();
        } else {
            log.trace("Failed to find attr subscription using token: {}", token);
        }
        if (state.getRpc() != null && state.getRpc().getToken().equals(token)) {
            return state.getRpc().getObserveCounter();
        } else {
            log.trace("Failed to find rpc subscription using token: {}", token);
        }
        return null;
    }

    @Override
    public void registerObserveRelation(String token, ObserveRelation relation) {
        TbCoapClientState state = clientsByToken.get(token);
        if (state == null) {
            log.trace("Failed to find state using token: {}", token);
            return;
        }
        if (state.getAttrs() != null && state.getAttrs().getToken().equals(token)) {
            state.getAttrs().setObserveRelation(relation);
        } else {
            log.trace("Failed to find attr subscription using token: {}", token);
        }
        if (state.getRpc() != null && state.getRpc().getToken().equals(token)) {
            state.getRpc().setObserveRelation(relation);
        } else {
            log.trace("Failed to find rpc subscription using token: {}", token);
        }
    }

    @Override
    public void deregisterObserveRelation(String token) {
        TbCoapClientState state = clientsByToken.remove(token);
        if (state == null) {
            log.trace("Failed to find state using token: {}", token);
            return;
        }
        if (state.getAttrs() != null && state.getAttrs().getToken().equals(token)) {
            cancelAttributeSubscription(state);
        } else {
            log.trace("Failed to find attr subscription using token: {}", token);
        }
        if (state.getRpc() != null && state.getRpc().getToken().equals(token)) {
            cancelRpcSubscription(state);
        } else {
            log.trace("Failed to find rpc subscription using token: {}", token);
        }
    }

    @Override
    public void reportActivity() {
        for (TbCoapClientState state : clients.values()) {
            if (state.getSession() != null) {
                transportService.reportActivity(state.getSession());
            }
        }
    }

    private void onUplink(TbCoapClientState client) {
        PowerMode powerMode = client.getPowerMode();
        PowerSavingConfiguration profileSettings = null;
        if (powerMode == null) {
            var clientProfile = getProfile(client.getProfileId());
            if (clientProfile.isPresent()) {
                profileSettings = clientProfile.get().getClientSettings();
                if (profileSettings != null) {
                    powerMode = profileSettings.getPowerMode();
                }
            }
        }
        if (powerMode == null || PowerMode.DRX.equals(powerMode)) {
            client.updateLastUplinkTime();
            return;
        }
        client.lock();
        try {
            long uplinkTime = client.updateLastUplinkTime();
            long timeout;
            if (PowerMode.PSM.equals(powerMode)) {
                Long psmActivityTimer = client.getPsmActivityTimer();
                if (psmActivityTimer == null && profileSettings != null) {
                    psmActivityTimer = profileSettings.getPsmActivityTimer();

                }
                if (psmActivityTimer == null || psmActivityTimer == 0L) {
                    psmActivityTimer = config.getPsmActivityTimer();
                }

                timeout = psmActivityTimer;
            } else {
                Long pagingTransmissionWindow = client.getPagingTransmissionWindow();
                if (pagingTransmissionWindow == null && profileSettings != null) {
                    pagingTransmissionWindow = profileSettings.getPagingTransmissionWindow();

                }
                if (pagingTransmissionWindow == null || pagingTransmissionWindow == 0L) {
                    pagingTransmissionWindow = config.getPagingTransmissionWindow();
                }
                timeout = pagingTransmissionWindow;
            }
            Future<Void> sleepTask = client.getSleepTask();
            if (sleepTask != null) {
                sleepTask.cancel(false);
            }
            Future<Void> task = transportContext.getScheduler().schedule(() -> {
                if (uplinkTime == client.getLastUplinkTime()) {
                    asleep(client);
                }
                return null;
            }, timeout, TimeUnit.MILLISECONDS);
            client.setSleepTask(task);
        } finally {
            client.unlock();
        }
    }

    private boolean registerFeatureObservation(TbCoapClientState state, String token, CoapExchange exchange, FeatureType featureType) {
        state.lock();
        try {
            boolean newObservation;
            if (FeatureType.ATTRIBUTES.equals(featureType)) {
                if (state.getAttrs() == null) {
                    newObservation = true;
                    state.setAttrs(new TbCoapObservationState(exchange, token));
                } else {
                    newObservation = !state.getAttrs().getToken().equals(token);
                    if (newObservation) {
                        TbCoapObservationState old = state.getAttrs();
                        state.setAttrs(new TbCoapObservationState(exchange, token));
                        old.getExchange().respond(CoAP.ResponseCode.DELETED);
                    }
                }
            } else {
                if (state.getRpc() == null) {
                    newObservation = true;
                    state.setRpc(new TbCoapObservationState(exchange, token));
                } else {
                    newObservation = !state.getRpc().getToken().equals(token);
                    if (newObservation) {
                        TbCoapObservationState old = state.getRpc();
                        state.setRpc(new TbCoapObservationState(exchange, token));
                        old.getExchange().respond(CoAP.ResponseCode.DELETED);
                    }
                }
            }
            if (newObservation) {
                clientsByToken.put(token, state);
                if (state.getSession() == null) {
                    TransportProtos.SessionInfoProto session = SessionInfoCreator.create(state.getCredentials(), transportContext, UUID.randomUUID());
                    state.setSession(session);
                    CoapSessionListener listener = new CoapSessionListener(state);
                    state.setListener(listener);
                    transportService.registerAsyncSession(session, state.getListener());
                    transportService.process(session, getSessionEventMsg(TransportProtos.SessionEvent.OPEN), null);
                }
                if (FeatureType.ATTRIBUTES.equals(featureType)) {
                    transportService.process(state.getSession(),
                            TransportProtos.SubscribeToAttributeUpdatesMsg.getDefaultInstance(), new CoapNoOpCallback(exchange));
                    transportService.process(state.getSession(),
                            TransportProtos.GetAttributeRequestMsg.newBuilder().setOnlyShared(true).build(),
                            new CoapNoOpCallback(exchange));
                } else {
                    transportService.process(state.getSession(),
                            TransportProtos.SubscribeToRPCMsg.getDefaultInstance(),
                            new CoapOkCallback(exchange, CoAP.ResponseCode.VALID, CoAP.ResponseCode.INTERNAL_SERVER_ERROR)
                    );
                }
            }
            return newObservation;
        } finally {
            state.unlock();
        }
    }

    @Override
    public void deregisterAttributeObservation(TbCoapClientState state, String token, CoapExchange exchange) {
        state.lock();
        try {
            clientsByToken.remove(token);
            if (state.getSession() == null) {
                log.trace("[{}] Failed to delete attribute observation: {}. Session is not present.", state.getDeviceId(), token);
                return;
            }
            if (state.getAttrs() == null) {
                log.trace("[{}] Failed to delete attribute observation: {}. It is not registered.", state.getDeviceId(), token);
                return;
            }
            if (!state.getAttrs().getToken().equals(token)) {
                log.trace("[{}] Failed to delete attribute observation: {}. Token mismatch.", state.getDeviceId(), token);
                return;
            }
            cancelAttributeSubscription(state);
        } finally {
            state.unlock();
        }
    }

    @Override
    public void deregisterRpcObservation(TbCoapClientState state, String token, CoapExchange exchange) {
        state.lock();
        try {
            clientsByToken.remove(token);
            if (state.getSession() == null) {
                log.trace("[{}] Failed to delete rpc observation: {}. Session is not present.", state.getDeviceId(), token);
                return;
            }
            if (state.getRpc() == null) {
                log.trace("[{}] Failed to delete rpc observation: {}. It is not registered.", state.getDeviceId(), token);
                return;
            }
            if (!state.getRpc().getToken().equals(token)) {
                log.trace("[{}] Failed to delete rpc observation: {}. Token mismatch.", state.getDeviceId(), token);
                return;
            }
            cancelRpcSubscription(state);
        } finally {
            state.unlock();
        }
    }

    @Override
    public TbCoapClientState getOrCreateClient(SessionMsgType type, ValidateDeviceCredentialsResponse deviceCredentials, DeviceProfile deviceProfile) throws AdaptorException {
        DeviceId deviceId = deviceCredentials.getDeviceInfo().getDeviceId();
        TbCoapClientState state = getClientState(deviceId);
        state.lock();
        try {
            if (state.getConfiguration() == null || state.getAdaptor() == null) {
                initStateAdaptor(deviceProfile, state);
            }
            if (state.getCredentials() == null) {
                state.init(deviceCredentials);
            }
        } finally {
            state.unlock();
        }
        return state;
    }

    @Override
    public TransportProtos.SessionInfoProto getNewSyncSession(TbCoapClientState state) {
        return SessionInfoCreator.create(state.getCredentials(), transportContext, UUID.randomUUID());
    }

    private TbCoapClientState getClientState(DeviceId deviceId) {
        return clients.computeIfAbsent(deviceId, TbCoapClientState::new);
    }

    private static TransportProtos.SessionEventMsg getSessionEventMsg(TransportProtos.SessionEvent event) {
        return TransportProtos.SessionEventMsg.newBuilder()
                .setSessionType(TransportProtos.SessionType.ASYNC)
                .setEvent(event).build();
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

    private void initStateAdaptor(DeviceProfile deviceProfile, TbCoapClientState state) throws AdaptorException {
        state.setConfiguration(getTransportConfigurationContainer(deviceProfile));
        state.setAdaptor(getCoapTransportAdaptor(state.getConfiguration().isJsonPayload()));
        state.setContentFormat(state.getAdaptor().getContentFormat());
    }

    private CoapTransportAdaptor getCoapTransportAdaptor(boolean jsonPayloadType) {
        return jsonPayloadType ? transportContext.getJsonCoapAdaptor() : transportContext.getProtoCoapAdaptor();
    }

    @RequiredArgsConstructor
    public class CoapSessionListener implements SessionMsgListener {

        private final TbCoapClientState state;

        @Override
        public void onGetAttributesResponse(TransportProtos.GetAttributeResponseMsg msg) {
            TbCoapObservationState attrs = state.getAttrs();
            if (attrs != null) {
                try {
                    boolean conRequest = AbstractSyncSessionCallback.isConRequest(state.getAttrs());
                    Response response = state.getAdaptor().convertToPublish(conRequest, msg);
                    respond(attrs.getExchange(), response, state.getContentFormat());
                } catch (AdaptorException e) {
                    log.trace("Failed to reply due to error", e);
                    cancelObserveRelation(attrs);
                    cancelAttributeSubscription(state);
                }
            } else {
                log.debug("[{}] Get Attrs exchange is empty", state.getDeviceId());
            }
        }

        @Override
        public void onAttributeUpdate(UUID sessionId, TransportProtos.AttributeUpdateNotificationMsg msg) {
            if (!isDownlinkAllowed(state)) {
                log.trace("[{}] ignore downlink request cause client is sleeping.", state.getDeviceId());
                state.lock();
                try {
                    state.addQueuedNotification(msg);
                } finally {
                    state.unlock();
                }
                return;
            }
            log.trace("[{}] Received attributes update notification to device", sessionId);
            TbCoapObservationState attrs = state.getAttrs();
            if (attrs != null) {
                try {
                    boolean conRequest = AbstractSyncSessionCallback.isConRequest(state.getAttrs());
                    int requestId = getNextMsgId();
                    Response response = state.getAdaptor().convertToPublish(conRequest, msg);
                    response.setMID(requestId);
                    if (conRequest) {
                        response.addMessageObserver(new TbCoapMessageObserver(requestId, id -> awake(state), id -> asleep(state)));
                    }
                    respond(attrs.getExchange(), response, state.getContentFormat());
                } catch (AdaptorException e) {
                    log.trace("[{}] Failed to reply due to error", state.getDeviceId(), e);
                    cancelObserveRelation(attrs);
                    cancelAttributeSubscription(state);
                }
            } else {
                log.debug("[{}] Get Attrs exchange is empty", state.getDeviceId());
            }
        }

        @Override
        public void onDeviceProfileUpdate(TransportProtos.SessionInfoProto newSessionInfo, DeviceProfile deviceProfile) {
            try {
                initStateAdaptor(deviceProfile, state);
            } catch (AdaptorException e) {
                log.warn("[{}] Failed to update device profile: ", deviceProfile.getId(), e);
            }
        }

        @Override
        public void onDeviceUpdate(TransportProtos.SessionInfoProto sessionInfo, Device device, Optional<DeviceProfile> deviceProfileOpt) {
            if (deviceProfileOpt.isPresent()) {
                try {
                    initStateAdaptor(deviceProfileOpt.get(), state);
                } catch (AdaptorException e) {
                    log.warn("[{}] Failed to update device: ", device.getId(), e);
                }
            }
            state.onDeviceUpdate(device);
        }

        @Override
        public void onDeviceDeleted(DeviceId deviceId) {
            cancelRpcSubscription(state);
            cancelAttributeSubscription(state);
        }

        @Override
        public void onRemoteSessionCloseCommand(UUID sessionId, TransportProtos.SessionCloseNotificationProto sessionCloseNotification) {
            log.trace("[{}] Received the remote command to close the session: {}", sessionId, sessionCloseNotification.getMessage());
            cancelRpcSubscription(state);
            cancelAttributeSubscription(state);
        }

        @Override
        public void onToDeviceRpcRequest(UUID sessionId, TransportProtos.ToDeviceRpcRequestMsg msg) {
            log.trace("[{}] Received RPC command to device", sessionId);
            if (!isDownlinkAllowed(state)) {
                log.trace("[{}] ignore downlink request cause client is sleeping.", state.getDeviceId());
                return;
            }
            boolean sent = false;
            boolean conRequest = AbstractSyncSessionCallback.isConRequest(state.getRpc());
            try {
                Response response = state.getAdaptor().convertToPublish(conRequest, msg, state.getConfiguration().getRpcRequestDynamicMessageBuilder());
                int requestId = getNextMsgId();
                response.setMID(requestId);
                if (msg.getPersisted() && conRequest) {
                    transportContext.getRpcAwaitingAck().put(requestId, msg);
                    transportContext.getScheduler().schedule(() -> {
                        TransportProtos.ToDeviceRpcRequestMsg awaitingAckMsg = transportContext.getRpcAwaitingAck().remove(requestId);
                        if (awaitingAckMsg != null) {
                            transportService.process(state.getSession(), msg, true, TransportServiceCallback.EMPTY);
                        }
                    }, Math.max(0, msg.getExpirationTime() - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                    response.addMessageObserver(new TbCoapMessageObserver(requestId, id -> {
                        TransportProtos.ToDeviceRpcRequestMsg rpcRequestMsg = transportContext.getRpcAwaitingAck().remove(id);
                        if (rpcRequestMsg != null) {
                            transportService.process(state.getSession(), rpcRequestMsg, false, TransportServiceCallback.EMPTY);
                        }
                    }, null));
                }
                if (conRequest) {
                    response.addMessageObserver(new TbCoapMessageObserver(requestId, id -> awake(state), id -> asleep(state)));
                }
                respond(state.getRpc().getExchange(), response, state.getContentFormat());
                sent = true;
            } catch (AdaptorException e) {
                log.trace("Failed to reply due to error", e);
                cancelObserveRelation(state.getRpc());
                cancelRpcSubscription(state);
            } finally {
                if (msg.getPersisted() && !conRequest) {
                    transportService.process(state.getSession(), msg, sent, TransportServiceCallback.EMPTY);
                }
            }
        }

        @Override
        public void onToServerRpcResponse(TransportProtos.ToServerRpcResponseMsg msg) {
            log.trace("[{}] Received server rpc response in the wrong session.", state.getSession());
        }

        private void cancelObserveRelation(TbCoapObservationState attrs) {
            if (attrs.getObserveRelation() != null) {
                attrs.getObserveRelation().cancel();
            }
        }
    }

    private boolean asleep(TbCoapClientState client) {
        boolean changed = compareAndSetSleepFlag(client, true);
        if (changed) {
            log.debug("[{}] client is sleeping", client.getDeviceId());
            transportService.log(client.getSession(), "Info: Client is sleeping!");
        }
        return changed;
    }

    @Override
    public boolean awake(TbCoapClientState client) {
        onUplink(client);
        boolean changed = compareAndSetSleepFlag(client, false);
        if (changed) {
            log.debug("[{}] client is awake", client.getDeviceId());
            transportService.log(client.getSession(), "Info: Client is awake!");
            sendMsgsAfterSleeping(client);
        }
        return changed;
    }

    private void sendMsgsAfterSleeping(TbCoapClientState client) {
        if (client.getRpc() != null) {
            TransportProtos.TransportToDeviceActorMsg persistentRpcRequestMsg = TransportProtos.TransportToDeviceActorMsg
                    .newBuilder()
                    .setSessionInfo(client.getSession())
                    .setSendPendingRPC(TransportProtos.SendPendingRPCMsg.newBuilder().build())
                    .build();
            transportService.process(persistentRpcRequestMsg, TransportServiceCallback.EMPTY);
        }
        if (client.getAttrs() != null && client.getMissedAttributeUpdates() != null) {
            client.getListener().onAttributeUpdate(new UUID(client.getSession().getSessionIdMSB(), client.getSession().getSessionIdLSB()), client.getAndClearMissedUpdates());
        }
    }

    private boolean compareAndSetSleepFlag(TbCoapClientState client, boolean sleeping) {
        if (sleeping == client.isAsleep()) {
            log.trace("[{}] Client is already at sleeping: {}, ignoring event: {}", client.getDeviceId(), client.isAsleep(), sleeping);
            return false;
        }
        client.lock();
        try {
            if (sleeping == client.isAsleep()) {
                log.trace("[{}] Client is already at sleeping: {}, ignoring event: {}", client.getDeviceId(), client.isAsleep(), sleeping);
                return false;
            } else {
                PowerMode powerMode = getPowerMode(client);
                if (PowerMode.PSM.equals(powerMode) || PowerMode.E_DRX.equals(powerMode)) {
                    log.trace("[{}] Switch sleeping from: {} to: {}", client.getDeviceId(), client.isAsleep(), sleeping);
                    client.setAsleep(sleeping);
                    // TODO: persist changes.
                    // update(client);
                    return true;
                } else {
                    return false;
                }
            }
        } finally {
            client.unlock();
        }
    }

    private boolean isDownlinkAllowed(TbCoapClientState client) {
        PowerMode powerMode = client.getPowerMode();
        PowerSavingConfiguration profileSettings = null;
        if (powerMode == null) {
            var clientProfile = getProfile(client.getProfileId());
            if (clientProfile.isPresent()) {
                profileSettings = clientProfile.get().getClientSettings();
                if (profileSettings != null) {
                    powerMode = profileSettings.getPowerMode();
                }
            }
        }
        if (powerMode == null || PowerMode.DRX.equals(powerMode)) {
            return true;
        }
        client.lock();
        long timeSinceLastUplink = System.currentTimeMillis() - client.getLastUplinkTime();
        try {
            if (PowerMode.PSM.equals(powerMode)) {
                Long psmActivityTimer = client.getPsmActivityTimer();
                if (psmActivityTimer == null && profileSettings != null) {
                    psmActivityTimer = profileSettings.getPsmActivityTimer();

                }
                if (psmActivityTimer == null || psmActivityTimer == 0L) {
                    psmActivityTimer = config.getPsmActivityTimer();
                }
                return timeSinceLastUplink <= psmActivityTimer;
            } else {
                Long pagingTransmissionWindow = client.getPagingTransmissionWindow();
                if (pagingTransmissionWindow == null && profileSettings != null) {
                    pagingTransmissionWindow = profileSettings.getPagingTransmissionWindow();

                }
                if (pagingTransmissionWindow == null || pagingTransmissionWindow == 0L) {
                    pagingTransmissionWindow = config.getPagingTransmissionWindow();
                }
                boolean allowed = timeSinceLastUplink <= pagingTransmissionWindow;
                if (!allowed) {
                    return client.checkFirstDownlink();
                } else {
                    return true;
                }
            }
        } finally {
            client.unlock();
        }
    }

    private PowerMode getPowerMode(TbCoapClientState client) {
        PowerMode powerMode = client.getPowerMode();
        if (powerMode == null) {
            Optional<CoapDeviceProfileTransportConfiguration> deviceProfile = getProfile(client.getProfileId());
            if (deviceProfile.isPresent()) {
                powerMode = deviceProfile.get().getClientSettings().getPowerMode();
            } else {
                powerMode = PowerMode.PSM;
            }
        }
        return powerMode;
    }

    public Optional<CoapDeviceProfileTransportConfiguration> getProfile(DeviceProfileId profileId) {
        DeviceProfile deviceProfile = profileCache.get(profileId);
        if (deviceProfile.getTransportType().equals(DeviceTransportType.COAP)) {
            return Optional.of((CoapDeviceProfileTransportConfiguration) deviceProfile.getProfileData().getTransportConfiguration());
        } else if (deviceProfile.getTransportType().equals(DeviceTransportType.DEFAULT)) {
            return Optional.empty();
        } else {
            log.warn("[{}] Invalid device profile type: {}", profileId, deviceProfile.getTransportType());
            throw new IllegalArgumentException("Invalid device profile type: " + deviceProfile.getTransportType());
        }
    }

    protected int getNextMsgId() {
        return ThreadLocalRandom.current().nextInt(NONE, MAX_MID + 1);
    }

    private void cancelRpcSubscription(TbCoapClientState state) {
        if (state.getRpc() != null) {
            clientsByToken.remove(state.getRpc().getToken());
            CoapExchange exchange = state.getAttrs().getExchange();
            state.setRpc(null);
            transportService.process(state.getSession(),
                    TransportProtos.SubscribeToRPCMsg.newBuilder().setUnsubscribe(true).build(),
                    new CoapOkCallback(exchange, CoAP.ResponseCode.DELETED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
            if (state.getAttrs() == null) {
                closeAndCleanup(state);
            }
        }
    }

    private void cancelAttributeSubscription(TbCoapClientState state) {
        if (state.getAttrs() != null) {
            clientsByToken.remove(state.getAttrs().getToken());
            CoapExchange exchange = state.getAttrs().getExchange();
            state.setAttrs(null);
            transportService.process(state.getSession(),
                    TransportProtos.SubscribeToAttributeUpdatesMsg.newBuilder().setUnsubscribe(true).build(),
                    new CoapOkCallback(exchange, CoAP.ResponseCode.DELETED, CoAP.ResponseCode.INTERNAL_SERVER_ERROR));
            if (state.getRpc() == null) {
                closeAndCleanup(state);
            }
        }
    }

    private void closeAndCleanup(TbCoapClientState state) {
        transportService.process(state.getSession(), getSessionEventMsg(TransportProtos.SessionEvent.CLOSED), null);
        transportService.deregisterSession(state.getSession());
        state.setSession(null);
        state.setConfiguration(null);
        state.setCredentials(null);
        state.setAdaptor(null);
        //TODO: add optimistic lock check that the client was already deleted and cleanup "clients" map.
    }

    private void respond(CoapExchange exchange, Response response, int defContentFormat) {
        response.getOptions().setContentFormat(TbCoapContentFormatUtil.getContentFormat(exchange.getRequestOptions().getContentFormat(), defContentFormat));
        exchange.respond(response);
    }
}
