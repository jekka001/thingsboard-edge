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
package org.thingsboard.server.service.queue;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.DeviceStateServiceMsgProto;
import org.thingsboard.server.gen.transport.TransportProtos.SubscriptionMgrMsgProto;
import org.thingsboard.server.gen.transport.TransportProtos.ToCoreNotificationMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TransportToDeviceActorMsg;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TbCoreConsumerStats {

    private final AtomicInteger totalCounter = new AtomicInteger(0);
    private final AtomicInteger sessionEventCounter = new AtomicInteger(0);
    private final AtomicInteger getAttributesCounter = new AtomicInteger(0);
    private final AtomicInteger subscribeToAttributesCounter = new AtomicInteger(0);
    private final AtomicInteger subscribeToRPCCounter = new AtomicInteger(0);
    private final AtomicInteger toDeviceRPCCallResponseCounter = new AtomicInteger(0);
    private final AtomicInteger subscriptionInfoCounter = new AtomicInteger(0);
    private final AtomicInteger claimDeviceCounter = new AtomicInteger(0);

    private final AtomicInteger deviceStateMsgCounter = new AtomicInteger(0);
    private final AtomicInteger schedulerMsgCounter = new AtomicInteger(0);
    private final AtomicInteger subscriptionMsgCounter = new AtomicInteger(0);
    private final AtomicInteger toCoreNotificationsCounter = new AtomicInteger(0);

    public void log(TransportToDeviceActorMsg msg) {
        totalCounter.incrementAndGet();
        if (msg.hasSessionEvent()) {
            sessionEventCounter.incrementAndGet();
        }
        if (msg.hasGetAttributes()) {
            getAttributesCounter.incrementAndGet();
        }
        if (msg.hasSubscribeToAttributes()) {
            subscribeToAttributesCounter.incrementAndGet();
        }
        if (msg.hasSubscribeToRPC()) {
            subscribeToRPCCounter.incrementAndGet();
        }
        if (msg.hasToDeviceRPCCallResponse()) {
            toDeviceRPCCallResponseCounter.incrementAndGet();
        }
        if (msg.hasSubscriptionInfo()) {
            subscriptionInfoCounter.incrementAndGet();
        }
        if (msg.hasClaimDevice()) {
            claimDeviceCounter.incrementAndGet();
        }
    }

    public void log(DeviceStateServiceMsgProto msg) {
        totalCounter.incrementAndGet();
        deviceStateMsgCounter.incrementAndGet();
    }

    public void log(SubscriptionMgrMsgProto msg) {
        totalCounter.incrementAndGet();
        subscriptionMsgCounter.incrementAndGet();
    }

    public void log(TransportProtos.SchedulerServiceMsgProto schedulerServiceMsg) {
        totalCounter.incrementAndGet();
        schedulerMsgCounter.incrementAndGet();
    }

    public void logToCoreNotification() {
        totalCounter.incrementAndGet();
        toCoreNotificationsCounter.incrementAndGet();
    }

    public void printStats() {
        int total = totalCounter.getAndSet(0);
        if (total > 0) {
            log.info("Transport total [{}] sessionEvents [{}] getAttr [{}] subToAttr [{}] subToRpc [{}] toDevRpc [{}] subInfo [{}] claimDevice [{}]" +
                            " deviceState [{}] scheduler [{}], subMgr [{}] coreNfs [{}]",
                    total, sessionEventCounter.getAndSet(0),
                    getAttributesCounter.getAndSet(0), subscribeToAttributesCounter.getAndSet(0),
                    subscribeToRPCCounter.getAndSet(0), toDeviceRPCCallResponseCounter.getAndSet(0),
                    subscriptionInfoCounter.getAndSet(0), claimDeviceCounter.getAndSet(0),
                    deviceStateMsgCounter.getAndSet(0), schedulerMsgCounter.getAndSet(0),
                    subscriptionMsgCounter.getAndSet(0), toCoreNotificationsCounter.getAndSet(0));
        }
    }

}
