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

import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TbEntityLocalSubsInfoTest {

    @Test
    void addTest() {
        Set<TbAttributeSubscription> expectedSubs = new HashSet<>();
        TbEntityLocalSubsInfo subsInfo = createSubsInfo();
        TenantId tenantId = subsInfo.getTenantId();
        EntityId entityId = subsInfo.getEntityId();
        TbAttributeSubscription attrSubscription1 = TbAttributeSubscription.builder()
                .sessionId("session1")
                .tenantId(tenantId)
                .entityId(entityId)
                .keyStates(Map.of("key1", 1L, "key2", 2L))
                .build();
        expectedSubs.add(attrSubscription1);
        TbEntitySubEvent created = subsInfo.add(attrSubscription1);
        assertFalse(subsInfo.isEmpty());
        assertNotNull(created);
        assertEquals(expectedSubs, subsInfo.getSubs());
        checkEvent(created, expectedSubs, ComponentLifecycleEvent.CREATED);

        assertNull(subsInfo.add(attrSubscription1));

        TbAttributeSubscription attrSubscription2 = TbAttributeSubscription.builder()
                .sessionId("session2")
                .tenantId(tenantId)
                .entityId(entityId)
                .keyStates(Map.of("key3", 3L, "key4", 4L))
                .build();
        expectedSubs.add(attrSubscription2);
        TbEntitySubEvent updated = subsInfo.add(attrSubscription2);
        assertNotNull(updated);

        assertEquals(expectedSubs, subsInfo.getSubs());
        checkEvent(updated, expectedSubs, ComponentLifecycleEvent.UPDATED);
    }

    @Test
    void removeTest() {
        Set<TbAttributeSubscription> expectedSubs = new HashSet<>();
        TbEntityLocalSubsInfo subsInfo = createSubsInfo();
        TenantId tenantId = subsInfo.getTenantId();
        EntityId entityId = subsInfo.getEntityId();
        TbAttributeSubscription attrSubscription1 = TbAttributeSubscription.builder()
                .sessionId("session1")
                .tenantId(tenantId)
                .entityId(entityId)
                .keyStates(Map.of("key1", 1L, "key2", 2L))
                .build();

        TbAttributeSubscription attrSubscription2 = TbAttributeSubscription.builder()
                .sessionId("session2")
                .tenantId(tenantId)
                .entityId(entityId)
                .keyStates(Map.of("key3", 3L, "key4", 4L))
                .build();

        expectedSubs.add(attrSubscription1);
        expectedSubs.add(attrSubscription2);

        subsInfo.add(attrSubscription1);
        subsInfo.add(attrSubscription2);

        assertEquals(expectedSubs, subsInfo.getSubs());

        TbEntitySubEvent updatedEvent = subsInfo.remove(attrSubscription1);
        expectedSubs.remove(attrSubscription1);
        assertNotNull(updatedEvent);
        assertEquals(expectedSubs, subsInfo.getSubs());
        checkEvent(updatedEvent, expectedSubs, ComponentLifecycleEvent.UPDATED);

        TbEntitySubEvent deletedEvent = subsInfo.remove(attrSubscription2);
        expectedSubs.remove(attrSubscription2);
        assertNotNull(deletedEvent);
        assertEquals(expectedSubs, subsInfo.getSubs());
        checkEvent(deletedEvent, expectedSubs, ComponentLifecycleEvent.DELETED);

        assertTrue(subsInfo.isEmpty());
    }

    @Test
    void removeAllTest() {
        List<TbAttributeSubscription> subs = new ArrayList<>();
        TbEntityLocalSubsInfo subsInfo = createSubsInfo();
        TenantId tenantId = subsInfo.getTenantId();
        EntityId entityId = subsInfo.getEntityId();
        TbAttributeSubscription attrSubscription1 = TbAttributeSubscription.builder()
                .sessionId("session1")
                .tenantId(tenantId)
                .entityId(entityId)
                .keyStates(Map.of("key1", 1L, "key2", 2L))
                .build();

        TbAttributeSubscription attrSubscription2 = TbAttributeSubscription.builder()
                .sessionId("session2")
                .tenantId(tenantId)
                .entityId(entityId)
                .keyStates(Map.of("key3", 3L, "key4", 4L))
                .build();

        subs.add(attrSubscription1);
        subs.add(attrSubscription2);

        subsInfo.add(attrSubscription1);
        subsInfo.add(attrSubscription2);

        assertFalse(subsInfo.isEmpty());

        TbEntitySubEvent deletedEvent = subsInfo.removeAll(subs);
        assertNotNull(deletedEvent);
        checkEvent(deletedEvent, subs, ComponentLifecycleEvent.DELETED);

        assertTrue(subsInfo.isEmpty());
    }

    private TbEntityLocalSubsInfo createSubsInfo() {
        return new TbEntityLocalSubsInfo(new TenantId(UUID.randomUUID()), new DeviceId(UUID.randomUUID()));
    }

    private void checkEvent(TbEntitySubEvent event, Collection<TbAttributeSubscription> expectedSubs, ComponentLifecycleEvent expectedType) {
        assertEquals(expectedType, event.getType());
        TbSubscriptionsInfo info = event.getInfo();
        if (event.getType() == ComponentLifecycleEvent.DELETED) {
            assertNull(info);
            return;
        }
        assertNotNull(info);
        assertFalse(info.notifications);
        assertFalse(info.alarms);
        assertFalse(info.attrAllKeys);
        assertFalse(info.tsAllKeys);
        assertNull(info.tsKeys);
        assertEquals(getAttrKeys(expectedSubs), info.attrKeys);
    }

    private Set<String> getAttrKeys(Collection<TbAttributeSubscription> attributeSubscriptions) {
        return attributeSubscriptions.stream().map(s -> s.getKeyStates().keySet()).flatMap(Collection::stream).collect(Collectors.toSet());
    }
}
