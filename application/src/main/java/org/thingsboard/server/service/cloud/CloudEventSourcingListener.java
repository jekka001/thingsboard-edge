/**
 * Copyright © 2016-2023 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.dao.edge.EdgeSynchronizationManager;
import org.thingsboard.server.dao.eventsourcing.ActionEntityEvent;
import org.thingsboard.server.dao.eventsourcing.ActionRelationEvent;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;

import javax.annotation.PostConstruct;

import java.util.Arrays;
import java.util.List;

import static org.thingsboard.server.service.entitiy.DefaultTbNotificationEntityService.edgeTypeByActionType;

/**
 * This event listener does not support async event processing because relay on ThreadLocal
 * Another possible approach is to implement a special annotation and a bunch of classes similar to TransactionalApplicationListener
 * This class is the simplest approach to maintain replica synchronization within the single class.
 * <p>
 * For async event publishers, you have to decide whether publish event on creating async task in the same thread where dao method called
 * @Autowired
 * EdgeEventSynchronizationManager edgeSynchronizationManager
 * ...
 *   //some async write action make future
 *   if (!edgeSynchronizationManager.isSync()) {
 *     future.addCallback(eventPublisher.publishEvent(...))
 *   }
 * */
@Component
@RequiredArgsConstructor
@Slf4j
public class CloudEventSourcingListener {

    private final TbClusterService tbClusterService;
    private final EdgeSynchronizationManager edgeSynchronizationManager;

    private List<EntityType> supportableEntityTypes = Arrays.asList(EntityType.DEVICE, EntityType.ALARM);

    @PostConstruct
    public void init() {
        log.info("EdgeEventSourcingListener initiated");
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(SaveEntityEvent event) {
        if (edgeSynchronizationManager.isSync()) {
            return;
        }
        if (event.getEntityId() != null && !supportableEntityTypes.contains(event.getEntityId().getEntityType())) {
            return;
        }
        log.trace("[{}] EntitySaveEvent called: {}", event.getEntityId().getEntityType(), event);
        EdgeEventActionType action = Boolean.TRUE.equals(event.getAdded()) ? EdgeEventActionType.ADDED : EdgeEventActionType.UPDATED;
        tbClusterService.sendNotificationMsgToCloud(event.getTenantId(), event.getEntityId(),
                null, null, action, null);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(DeleteEntityEvent event) {
        if (edgeSynchronizationManager.isSync()) {
            return;
        }
        if (event.getEntityId() != null && !supportableEntityTypes.contains(event.getEntityId().getEntityType())) {
            return;
        }
        log.trace("[{}] EntityDeleteEvent called: {}", event.getEntityId().getEntityType(), event);
        tbClusterService.sendNotificationMsgToCloud(event.getTenantId(), event.getEntityId(),
                event.getBody(), null, EdgeEventActionType.DELETED, null);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(ActionEntityEvent event) {
        if (edgeSynchronizationManager.isSync()) {
            return;
        }
        if (event.getEntityId() != null && !supportableEntityTypes.contains(event.getEntityId().getEntityType())) {
            return;
        }
        log.trace("[{}] EntityActionEvent called: {}", event.getEntityId().getEntityType(), event);
        tbClusterService.sendNotificationMsgToCloud(event.getTenantId(), event.getEntityId(),
                event.getBody(), null, edgeTypeByActionType(event.getActionType()), event.getEntityGroupId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void handleEvent(ActionRelationEvent event) {
        if (edgeSynchronizationManager.isSync()) {
            return;
        }
        log.trace("EntityRelationActionEvent called: {}", event);
        tbClusterService.sendNotificationMsgToCloud(event.getTenantId(), null,
                event.getBody(), CloudEventType.RELATION, edgeTypeByActionType(event.getActionType()), null);
    }
}
