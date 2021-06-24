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
package org.thingsboard.server.msa;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.thingsboard.server.msa.mapper.WsTelemetryResponse;

import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class WsClient extends WebSocketClient {
    private static final ObjectMapper mapper = new ObjectMapper();
    private WsTelemetryResponse message;

    private volatile boolean firstReplyReceived;
    private CountDownLatch firstReply = new CountDownLatch(1);
    private CountDownLatch latch = new CountDownLatch(1);

    WsClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
    }

    @Override
    public void onMessage(String message) {
        if (!firstReplyReceived) {
            firstReplyReceived = true;
            firstReply.countDown();
        } else {
            try {
                WsTelemetryResponse response = mapper.readValue(message, WsTelemetryResponse.class);
                if (!response.getData().isEmpty()) {
                    this.message = response;
                    latch.countDown();
                }
            } catch (IOException e) {
                log.error("ws message can't be read");
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("ws is closed, due to [{}]", reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public WsTelemetryResponse getLastMessage() {
        try {
            latch.await(10, TimeUnit.SECONDS);
            return this.message;
        } catch (InterruptedException e) {
            log.error("Timeout, ws message wasn't received");
        }
        return null;
    }

    void waitForFirstReply() {
        try {
            firstReply.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error("Timeout, ws message wasn't received");
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onSetSSLParameters(SSLParameters sslParameters) {
        sslParameters.setEndpointIdentificationAlgorithm(null);
    }
}
