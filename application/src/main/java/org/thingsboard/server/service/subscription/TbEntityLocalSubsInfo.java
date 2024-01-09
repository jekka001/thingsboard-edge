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
package org.thingsboard.server.service.subscription;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Information about the local websocket subscriptions.
 */
@Slf4j
@RequiredArgsConstructor
public class TbEntityLocalSubsInfo {

    @Getter
    private final TenantId tenantId;
    @Getter
    private final EntityId entityId;
    @Getter
    private final Lock lock = new ReentrantLock();
    @Getter
    private final Set<TbSubscription<?>> subs = ConcurrentHashMap.newKeySet();
    private volatile TbSubscriptionsInfo state = new TbSubscriptionsInfo();

    private final Map<Integer, Set<TbSubscription<?>>> pendingSubs = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private int pendingTimeSeriesEvent;
    @Getter
    @Setter
    private long pendingTimeSeriesEventTs;
    @Getter
    @Setter
    private int pendingAttributesEvent;
    @Getter
    @Setter
    private long pendingAttributesEventTs;

    private int seqNumber = 0;

    public TbEntitySubEvent add(TbSubscription<?> subscription) {
        log.trace("[{}][{}][{}] Adding: {}", tenantId, entityId, subscription.getSubscriptionId(), subscription);
        boolean created = subs.isEmpty();
        subs.add(subscription);
        TbSubscriptionsInfo newState = created ? state : state.copy();
        boolean stateChanged = false;
        switch (subscription.getType()) {
            case NOTIFICATIONS:
            case NOTIFICATIONS_COUNT:
                if (!newState.notifications) {
                    newState.notifications = true;
                    stateChanged = true;
                }
                break;
            case ALARMS:
                if (!newState.alarms) {
                    newState.alarms = true;
                    stateChanged = true;
                }
                break;
            case ATTRIBUTES:
                var attrSub = (TbAttributeSubscription) subscription;
                if (!newState.attrAllKeys) {
                    if (attrSub.isAllKeys()) {
                        newState.attrAllKeys = true;
                        stateChanged = true;
                    } else {
                        if (newState.attrKeys == null) {
                            newState.attrKeys = new HashSet<>(attrSub.getKeyStates().keySet());
                            stateChanged = true;
                        } else if (newState.attrKeys.addAll(attrSub.getKeyStates().keySet())) {
                            stateChanged = true;
                        }
                    }
                }
                break;
            case TIMESERIES:
                var tsSub = (TbTimeSeriesSubscription) subscription;
                if (!newState.tsAllKeys) {
                    if (tsSub.isAllKeys()) {
                        newState.tsAllKeys = true;
                        stateChanged = true;
                    } else {
                        if (newState.tsKeys == null) {
                            newState.tsKeys = new HashSet<>(tsSub.getKeyStates().keySet());
                            stateChanged = true;
                        } else if (newState.tsKeys.addAll(tsSub.getKeyStates().keySet())) {
                            stateChanged = true;
                        }
                    }
                }
                break;
        }
        if (stateChanged) {
            state = newState;
        }
        if (created) {
            return toEvent(ComponentLifecycleEvent.CREATED);
        } else if (stateChanged) {
            return toEvent(ComponentLifecycleEvent.UPDATED);
        } else {
            return null;
        }
    }

    public TbEntitySubEvent remove(TbSubscription<?> sub) {
        log.trace("[{}][{}][{}] Removing: {}", tenantId, entityId, sub.getSubscriptionId(), sub);
        if (!subs.remove(sub)) {
            return null;
        }
        if (subs.isEmpty()) {
            return toEvent(ComponentLifecycleEvent.DELETED);
        }
        TbSubscriptionsInfo oldState = state.copy();
        TbSubscriptionsInfo newState = new TbSubscriptionsInfo();
        for (TbSubscription<?> subscription : subs) {
            switch (subscription.getType()) {
                case NOTIFICATIONS:
                case NOTIFICATIONS_COUNT:
                    if (!newState.notifications) {
                        newState.notifications = true;
                    }
                    break;
                case ALARMS:
                    if (!newState.alarms) {
                        newState.alarms = true;
                    }
                    break;
                case ATTRIBUTES:
                    var attrSub = (TbAttributeSubscription) subscription;
                    if (!newState.attrAllKeys && attrSub.isAllKeys()) {
                        newState.attrAllKeys = true;
                        continue;
                    }
                    if (newState.attrKeys == null) {
                        newState.attrKeys = new HashSet<>(attrSub.getKeyStates().keySet());
                    } else {
                        newState.attrKeys.addAll(attrSub.getKeyStates().keySet());
                    }
                    break;
                case TIMESERIES:
                    var tsSub = (TbTimeSeriesSubscription) subscription;
                    if (!newState.tsAllKeys && tsSub.isAllKeys()) {
                        newState.tsAllKeys = true;
                        continue;
                    }
                    if (newState.tsKeys == null) {
                        newState.tsKeys = new HashSet<>(tsSub.getKeyStates().keySet());
                    } else {
                        newState.tsKeys.addAll(tsSub.getKeyStates().keySet());
                    }
                    break;
            }
        }
        if (newState.equals(oldState)) {
            return null;
        } else {
            this.state = newState;
            return toEvent(ComponentLifecycleEvent.UPDATED);
        }
    }

    public TbEntitySubEvent toEvent(ComponentLifecycleEvent type) {
        seqNumber++;
        var result = TbEntitySubEvent.builder().tenantId(tenantId).entityId(entityId).type(type).seqNumber(seqNumber);
        if (!ComponentLifecycleEvent.DELETED.equals(type)) {
            result.info(state.copy(seqNumber));
        }
        return result.build();
    }

    public boolean isNf() {
        return state.notifications;
    }


    public boolean isEmpty() {
        return state.isEmpty();
    }

    public TbSubscription<?> registerPendingSubscription(TbSubscription<?> subscription, TbEntitySubEvent event) {
        if (TbSubscriptionType.ATTRIBUTES.equals(subscription.getType())) {
            if (event != null) {
                log.trace("[{}][{}] Registering new pending attributes subscription event: {} for subscription: {}", tenantId, entityId, event.getSeqNumber(), subscription.getSubscriptionId());
                pendingAttributesEvent = event.getSeqNumber();
                pendingAttributesEventTs = System.currentTimeMillis();
                pendingSubs.computeIfAbsent(pendingAttributesEvent, e -> new HashSet<>()).add(subscription);
            } else if (pendingAttributesEvent > 0) {
                log.trace("[{}][{}] Registering pending attributes subscription {} for event: {} ", tenantId, entityId, subscription.getSubscriptionId(), pendingAttributesEvent);
                pendingSubs.computeIfAbsent(pendingAttributesEvent, e -> new HashSet<>()).add(subscription);
            } else {
                return subscription;
            }
        } else if (subscription instanceof TbTimeSeriesSubscription) {
            if (event != null) {
                log.trace("[{}][{}] Registering new pending time-series subscription event: {} for subscription: {}", tenantId, entityId, event.getSeqNumber(), subscription.getSubscriptionId());
                pendingTimeSeriesEvent = event.getSeqNumber();
                pendingTimeSeriesEventTs = System.currentTimeMillis();
                pendingSubs.computeIfAbsent(pendingTimeSeriesEvent, e -> new HashSet<>()).add(subscription);
            } else if (pendingTimeSeriesEvent > 0) {
                log.trace("[{}][{}] Registering pending time-series subscription {} for event: {} ", tenantId, entityId, subscription.getSubscriptionId(), pendingTimeSeriesEvent);
                pendingSubs.computeIfAbsent(pendingTimeSeriesEvent, e -> new HashSet<>()).add(subscription);
            } else {
                return subscription;
            }
        }
        return null;
    }

    public Set<TbSubscription<?>> clearPendingSubscriptions(int seqNumber) {
        if (pendingTimeSeriesEvent == seqNumber) {
            pendingTimeSeriesEvent = 0;
            pendingTimeSeriesEventTs = 0L;
        } else if (pendingAttributesEvent == seqNumber) {
            pendingAttributesEvent = 0;
            pendingAttributesEventTs = 0L;
        }
        return pendingSubs.remove(seqNumber);
    }
}
