/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.controller;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.kv.Aggregation;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityFilter;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.service.telemetry.cmd.TelemetryPluginCmdsWrapper;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityCountCmd;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityCountUpdate;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityDataCmd;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityDataUpdate;
import org.thingsboard.server.service.telemetry.cmd.v2.EntityHistoryCmd;
import org.thingsboard.server.service.telemetry.cmd.v2.LatestValueCmd;
import org.thingsboard.server.service.telemetry.cmd.v2.TimeSeriesCmd;

import java.net.URI;
import java.nio.channels.NotYetConnectedException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TbTestWebSocketClient extends WebSocketClient {

    private volatile String lastMsg;
    private CountDownLatch reply;
    private CountDownLatch update;

    public TbTestWebSocketClient(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {

    }

    @Override
    public void onMessage(String s) {
        log.info("RECEIVED: {}", s);
        lastMsg = s;
        if (reply != null) {
            reply.countDown();
        }
        if (update != null) {
            update.countDown();
        }
    }

    @Override
    public void onClose(int i, String s, boolean b) {
        log.info("CLOSED.");
    }

    @Override
    public void onError(Exception e) {
        log.error("ERROR:", e);
    }

    public void registerWaitForUpdate() {
        lastMsg = null;
        update = new CountDownLatch(1);
    }

    @Override
    public void send(String text) throws NotYetConnectedException {
        reply = new CountDownLatch(1);
        super.send(text);
    }

    public void send(EntityDataCmd cmd) throws NotYetConnectedException {
        TelemetryPluginCmdsWrapper wrapper = new TelemetryPluginCmdsWrapper();
        wrapper.setEntityDataCmds(Collections.singletonList(cmd));
        this.send(JacksonUtil.toString(wrapper));
    }

    public void send(EntityCountCmd cmd) throws NotYetConnectedException {
        TelemetryPluginCmdsWrapper wrapper = new TelemetryPluginCmdsWrapper();
        wrapper.setEntityCountCmds(Collections.singletonList(cmd));
        this.send(JacksonUtil.toString(wrapper));
    }

    public String waitForUpdate() {
        return waitForUpdate(TimeUnit.SECONDS.toMillis(3));
    }

    public String waitForUpdate(long ms) {
        try {
            update.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("Failed to await reply", e);
        }
        return lastMsg;
    }

    public String waitForReply() {
        try {
            reply.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Failed to await reply", e);
        }
        return lastMsg;
    }

    public EntityDataUpdate parseDataReply(String msg) {
        return JacksonUtil.fromString(msg, EntityDataUpdate.class);
    }

    public EntityCountUpdate parseCountReply(String msg) {
        return JacksonUtil.fromString(msg, EntityCountUpdate.class);
    }

    public EntityDataUpdate subscribeLatestUpdate(List<EntityKey> keys, EntityFilter entityFilter) {
        EntityDataQuery edq = new EntityDataQuery(entityFilter, new EntityDataPageLink(1, 0, null, null),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return subscribeLatestUpdate(keys, edq);
    }

    public EntityDataUpdate subscribeLatestUpdate(List<EntityKey> keys) {
        return subscribeLatestUpdate(keys, (EntityDataQuery) null);
    }

    public EntityDataUpdate subscribeLatestUpdate(List<EntityKey> keys, EntityDataQuery edq) {
        LatestValueCmd latestCmd = new LatestValueCmd();
        latestCmd.setKeys(keys);
        EntityDataCmd cmd = new EntityDataCmd(1, edq, null, latestCmd, null);
        send(cmd);
        return parseDataReply(waitForReply());
    }

    public EntityDataUpdate subscribeTsUpdate(List<String> keys, long startTs, long timeWindow) {
        return subscribeTsUpdate(keys, startTs, timeWindow, null);
    }

    public EntityDataUpdate subscribeTsUpdate(List<String> keys, long startTs, long timeWindow, EntityDataQuery edq) {
        TimeSeriesCmd tsCmd = new TimeSeriesCmd();
        tsCmd.setKeys(keys);
        tsCmd.setAgg(Aggregation.NONE);
        tsCmd.setLimit(1000);
        tsCmd.setStartTs(startTs - timeWindow);
        tsCmd.setTimeWindow(timeWindow);

        EntityDataCmd cmd = new EntityDataCmd(1, edq, null, null, tsCmd);

        send(cmd);
        return parseDataReply(waitForReply());
    }

    public EntityDataUpdate sendHistoryCmd(List<String> keys, long startTs, long timeWindow) {
        return sendHistoryCmd(keys, startTs, timeWindow, (EntityDataQuery) null);
    }

    public EntityDataUpdate sendHistoryCmd(List<String> keys, long startTs, long timeWindow, EntityFilter entityFilter) {
        EntityDataQuery edq = new EntityDataQuery(entityFilter,
                new EntityDataPageLink(1, 0, null, null),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return sendHistoryCmd(keys, startTs, timeWindow, edq);
    }

    public EntityDataUpdate sendHistoryCmd(List<String> keys, long startTs, long timeWindow, EntityDataQuery edq) {
        EntityHistoryCmd historyCmd = new EntityHistoryCmd();
        historyCmd.setKeys(keys);
        historyCmd.setAgg(Aggregation.NONE);
        historyCmd.setLimit(1000);
        historyCmd.setStartTs(startTs - timeWindow);
        historyCmd.setEndTs(startTs);

        EntityDataCmd cmd = new EntityDataCmd(1, edq, historyCmd, null, null);

        send(cmd);
        return parseDataReply(this.waitForReply());
    }

    public EntityDataUpdate sendEntityDataQuery(EntityDataQuery edq) {
        log.warn("sendEntityDataQuery {}", edq);
        EntityDataCmd cmd = new EntityDataCmd(1, edq, null, null, null);
        send(cmd);
        String msg = this.waitForReply();
        return parseDataReply(msg);
    }

    public EntityDataUpdate sendEntityDataQuery(EntityFilter entityFilter) {
        log.warn("sendEntityDataQuery {}", entityFilter);
        EntityDataQuery edq = new EntityDataQuery(entityFilter, new EntityDataPageLink(1, 0, null, null),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        return sendEntityDataQuery(edq);
    }

}
