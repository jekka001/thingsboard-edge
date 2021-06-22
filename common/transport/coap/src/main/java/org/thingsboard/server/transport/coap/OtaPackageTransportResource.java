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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.observe.ObserveRelation;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;
import org.eclipse.californium.core.server.resources.ResourceObserver;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.security.DeviceTokenCredentials;
import org.thingsboard.server.common.transport.TransportServiceCallback;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class OtaPackageTransportResource extends AbstractCoapTransportResource {
    private static final int ACCESS_TOKEN_POSITION = 2;

    private final OtaPackageType otaPackageType;

    public OtaPackageTransportResource(CoapTransportContext ctx, OtaPackageType otaPackageType) {
        super(ctx, otaPackageType.getKeyPrefix());
        this.setObservable(true);
        this.addObserver(new OtaPackageTransportResource.CoapResourceObserver());
        this.otaPackageType = otaPackageType;
    }

    @Override
    protected void processHandleGet(CoapExchange exchange) {
        log.trace("Processing {}", exchange.advanced().getRequest());
        exchange.accept();
        Exchange advanced = exchange.advanced();
        Request request = advanced.getRequest();
        processAccessTokenRequest(exchange, request);
    }

    @Override
    protected void processHandlePost(CoapExchange exchange) {
        exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
    }

    private void processAccessTokenRequest(CoapExchange exchange, Request request) {
        Optional<DeviceTokenCredentials> credentials = decodeCredentials(request);
        if (credentials.isEmpty()) {
            exchange.respond(CoAP.ResponseCode.UNAUTHORIZED);
            return;
        }
        transportService.process(DeviceTransportType.COAP, TransportProtos.ValidateDeviceTokenRequestMsg.newBuilder().setToken(credentials.get().getCredentialsId()).build(),
                new CoapDeviceAuthCallback(transportContext, exchange, (sessionInfo, deviceProfile) -> {
                    getOtaPackageCallback(sessionInfo, exchange, otaPackageType);
                }));
    }

    private void getOtaPackageCallback(TransportProtos.SessionInfoProto sessionInfo, CoapExchange exchange, OtaPackageType firmwareType) {
        TransportProtos.GetOtaPackageRequestMsg requestMsg = TransportProtos.GetOtaPackageRequestMsg.newBuilder()
                .setTenantIdMSB(sessionInfo.getTenantIdMSB())
                .setTenantIdLSB(sessionInfo.getTenantIdLSB())
                .setDeviceIdMSB(sessionInfo.getDeviceIdMSB())
                .setDeviceIdLSB(sessionInfo.getDeviceIdLSB())
                .setType(firmwareType.name()).build();
        transportContext.getTransportService().process(sessionInfo, requestMsg, new OtaPackageCallback(exchange));
    }

    private Optional<DeviceTokenCredentials> decodeCredentials(Request request) {
        List<String> uriPath = request.getOptions().getUriPath();
        if (uriPath.size() == ACCESS_TOKEN_POSITION) {
            return Optional.of(new DeviceTokenCredentials(uriPath.get(ACCESS_TOKEN_POSITION - 1)));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Resource getChild(String name) {
        return this;
    }

    private class OtaPackageCallback implements TransportServiceCallback<TransportProtos.GetOtaPackageResponseMsg> {
        private final CoapExchange exchange;

        OtaPackageCallback(CoapExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public void onSuccess(TransportProtos.GetOtaPackageResponseMsg msg) {
            String title = exchange.getQueryParameter("title");
            String version = exchange.getQueryParameter("version");
            if (msg.getResponseStatus().equals(TransportProtos.ResponseStatus.SUCCESS)) {
                String firmwareId = new UUID(msg.getOtaPackageIdMSB(), msg.getOtaPackageIdLSB()).toString();
                if ((title == null || msg.getTitle().equals(title)) && (version == null || msg.getVersion().equals(version))) {
                    String strChunkSize = exchange.getQueryParameter("size");
                    String strChunk = exchange.getQueryParameter("chunk");
                    int chunkSize = StringUtils.isEmpty(strChunkSize) ? 0 : Integer.parseInt(strChunkSize);
                    int chunk = StringUtils.isEmpty(strChunk) ? 0 : Integer.parseInt(strChunk);
                    respondOtaPackage(exchange, transportContext.getOtaPackageDataCache().get(firmwareId, chunkSize, chunk));
                } else {
                    exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
                }
            } else {
                exchange.respond(CoAP.ResponseCode.NOT_FOUND);
            }
        }

        @Override
        public void onError(Throwable e) {
            log.warn("Failed to process request", e);
            exchange.respond(CoAP.ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void respondOtaPackage(CoapExchange exchange, byte[] data) {
        Response response = new Response(CoAP.ResponseCode.CONTENT);
        if (data != null && data.length > 0) {
            response.setPayload(data);
            if (exchange.getRequestOptions().getBlock2() != null) {
                int chunkSize = exchange.getRequestOptions().getBlock2().getSzx();
                boolean lastFlag = data.length > chunkSize;
                response.getOptions().setUriPath(exchange.getRequestOptions().getUriPathString());
                response.getOptions().setBlock2(chunkSize, lastFlag, 0);
            }
            exchange.respond(response);
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

        }

        @Override
        public void removedObserveRelation(ObserveRelation relation) {

        }
    }

}
