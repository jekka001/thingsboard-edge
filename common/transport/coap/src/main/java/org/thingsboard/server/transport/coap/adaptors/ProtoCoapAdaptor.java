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
package org.thingsboard.server.transport.coap.adaptors;

import com.google.gson.JsonParser;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.adaptor.AdaptorException;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.adaptor.ProtoConverter;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.transport.coap.CoapTransportResource;

import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class ProtoCoapAdaptor implements CoapTransportAdaptor {

    @Override
    public TransportProtos.PostTelemetryMsg convertToPostTelemetry(UUID sessionId, Request inbound, Descriptors.Descriptor telemetryMsgDescriptor) throws AdaptorException {
        try {
            return JsonConverter.convertToTelemetryProto(new JsonParser().parse(dynamicMsgToJson(inbound.getPayload(), telemetryMsgDescriptor)));
        } catch (Exception e) {
            throw new AdaptorException(e);
        }
    }

    @Override
    public TransportProtos.PostAttributeMsg convertToPostAttributes(UUID sessionId, Request inbound, Descriptors.Descriptor attributesMsgDescriptor) throws AdaptorException {
        try {
            return JsonConverter.convertToAttributesProto(new JsonParser().parse(dynamicMsgToJson(inbound.getPayload(), attributesMsgDescriptor)));
        } catch (Exception e) {
            throw new AdaptorException(e);
        }
    }

    @Override
    public TransportProtos.GetAttributeRequestMsg convertToGetAttributes(UUID sessionId, Request inbound) throws AdaptorException {
        return CoapAdaptorUtils.toGetAttributeRequestMsg(inbound);
    }

    @Override
    public TransportProtos.ToDeviceRpcResponseMsg convertToDeviceRpcResponse(UUID sessionId, Request inbound) throws AdaptorException {
        Optional<Integer> requestId = CoapTransportResource.getRequestId(inbound);
        if (requestId.isEmpty()) {
            throw new AdaptorException("Request id is missing!");
        } else {
            try {
                String payload = TransportProtos.ToDeviceRpcResponseMsg.parseFrom(inbound.getPayload()).getPayload();
                return TransportProtos.ToDeviceRpcResponseMsg.newBuilder().setRequestId(requestId.get())
                        .setPayload(payload).build();
            } catch (InvalidProtocolBufferException e) {
                throw new AdaptorException(e);
            }
        }
    }

    @Override
    public TransportProtos.ToServerRpcRequestMsg convertToServerRpcRequest(UUID sessionId, Request inbound) throws AdaptorException {
        try {
            return ProtoConverter.convertToServerRpcRequest(inbound.getPayload(), 0);
        } catch (InvalidProtocolBufferException ex) {
            throw new AdaptorException(ex);
        }
    }

    @Override
    public TransportProtos.ClaimDeviceMsg convertToClaimDevice(UUID sessionId, Request inbound, TransportProtos.SessionInfoProto sessionInfo) throws AdaptorException {
        DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
        try {
            return ProtoConverter.convertToClaimDeviceProto(deviceId, inbound.getPayload());
        } catch (InvalidProtocolBufferException ex) {
            throw new AdaptorException(ex);
        }
    }

    @Override
    public TransportProtos.ProvisionDeviceRequestMsg convertToProvisionRequestMsg(UUID sessionId, Request inbound) throws AdaptorException {
        try {
            return ProtoConverter.convertToProvisionRequestMsg(inbound.getPayload());
        } catch (InvalidProtocolBufferException ex) {
            throw new AdaptorException(ex);
        }
    }

    @Override
    public Response convertToPublish(boolean isConfirmable, TransportProtos.AttributeUpdateNotificationMsg msg) throws AdaptorException {
        return getObserveNotification(isConfirmable, msg.toByteArray());
    }

    @Override
    public Response convertToPublish(boolean isConfirmable, TransportProtos.ToDeviceRpcRequestMsg msg) throws AdaptorException {
        return getObserveNotification(isConfirmable, msg.toByteArray());
    }

    @Override
    public Response convertToPublish(TransportProtos.ToServerRpcResponseMsg msg) throws AdaptorException {
        Response response = new Response(CoAP.ResponseCode.CONTENT);
        response.setPayload(msg.toByteArray());
        return response;
    }

    @Override
    public Response convertToPublish(TransportProtos.GetAttributeResponseMsg msg) throws AdaptorException {
        if (msg.getClientAttributeListCount() == 0 && msg.getSharedAttributeListCount() == 0) {
            return new Response(CoAP.ResponseCode.NOT_FOUND);
        } else {
            Response response = new Response(CoAP.ResponseCode.CONTENT);
            response.setPayload(msg.toByteArray());
            return response;
        }
    }

    private Response getObserveNotification(boolean confirmable, byte[] notification) {
        Response response = new Response(CoAP.ResponseCode.CONTENT);
        response.setPayload(notification);
        response.setConfirmable(confirmable);
        return response;
    }

    private String dynamicMsgToJson(byte[] bytes, Descriptors.Descriptor descriptor) throws InvalidProtocolBufferException {
        DynamicMessage dynamicMessage = DynamicMessage.parseFrom(descriptor, bytes);
        return JsonFormat.printer().includingDefaultValueFields().print(dynamicMessage);
    }

}
