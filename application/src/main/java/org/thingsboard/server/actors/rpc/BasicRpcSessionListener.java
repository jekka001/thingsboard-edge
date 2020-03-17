/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.actors.rpc;

import akka.actor.ActorRef;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.service.ActorService;
import org.thingsboard.server.gen.cluster.ClusterAPIProtos;
import org.thingsboard.server.service.cluster.rpc.GrpcSession;
import org.thingsboard.server.service.cluster.rpc.GrpcSessionListener;
import org.thingsboard.server.service.executors.ClusterRpcCallbackExecutorService;

/**
 * @author Andrew Shvayka
 */
@Slf4j
public class BasicRpcSessionListener implements GrpcSessionListener {

    private final ClusterRpcCallbackExecutorService callbackExecutorService;
    private final ActorService service;
    private final ActorRef manager;
    private final ActorRef self;

    BasicRpcSessionListener(ActorSystemContext context, ActorRef manager, ActorRef self) {
        this.service = context.getActorService();
        this.callbackExecutorService = context.getClusterRpcCallbackExecutor();
        this.manager = manager;
        this.self = self;
    }

    @Override
    public void onConnected(GrpcSession session) {
        log.info("[{}][{}] session started", session.getRemoteServer(), getType(session));
        if (!session.isClient()) {
            manager.tell(new RpcSessionConnectedMsg(session.getRemoteServer(), session.getSessionId()), self);
        }
    }

    @Override
    public void onDisconnected(GrpcSession session) {
        log.info("[{}][{}] session closed", session.getRemoteServer(), getType(session));
        manager.tell(new RpcSessionDisconnectedMsg(session.isClient(), session.getRemoteServer()), self);
    }

    @Override
    public void onReceiveClusterGrpcMsg(GrpcSession session, ClusterAPIProtos.ClusterMessage clusterMessage) {
        log.trace("Received session actor msg from [{}][{}]: {}", session.getRemoteServer(), getType(session), clusterMessage);
        callbackExecutorService.execute(() -> {
            try {
                service.onReceivedMsg(session.getRemoteServer(), clusterMessage);
            } catch (Exception e) {
                log.debug("[{}][{}] Failed to process cluster message: {}", session.getRemoteServer(), getType(session), clusterMessage, e);
            }
        });
    }

    @Override
    public void onError(GrpcSession session, Throwable t) {
        log.warn("[{}][{}] session got error -> {}", session.getRemoteServer(), getType(session), t);
        manager.tell(new RpcSessionClosedMsg(session.isClient(), session.getRemoteServer()), self);
        session.close();
    }

    private static String getType(GrpcSession session) {
        return session.isClient() ? "Client" : "Server";
    }


}
