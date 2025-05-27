/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.edqs.processor;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ExceptionUtil;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.ObjectType;
import org.thingsboard.server.common.data.edqs.EdqsEvent;
import org.thingsboard.server.common.data.edqs.EdqsEventType;
import org.thingsboard.server.common.data.edqs.EdqsObject;
import org.thingsboard.server.common.data.edqs.query.EdqsRequest;
import org.thingsboard.server.common.data.edqs.query.EdqsResponse;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.util.CollectionsUtil;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.edqs.repo.EdqsRepository;
import org.thingsboard.server.edqs.state.EdqsPartitionService;
import org.thingsboard.server.edqs.state.EdqsStateService;
import org.thingsboard.server.edqs.util.EdqsConverter;
import org.thingsboard.server.edqs.util.VersionsStore;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.EdqsEventMsg;
import org.thingsboard.server.gen.transport.TransportProtos.FromEdqsMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToEdqsMsg;
import org.thingsboard.server.queue.TbQueueHandler;
import org.thingsboard.server.queue.common.PartitionedQueueResponseTemplate;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.common.consumer.PartitionedQueueConsumerManager;
import org.thingsboard.server.queue.discovery.QueueKey;
import org.thingsboard.server.queue.discovery.TopicService;
import org.thingsboard.server.queue.discovery.event.PartitionChangeEvent;
import org.thingsboard.server.queue.edqs.EdqsComponent;
import org.thingsboard.server.queue.edqs.EdqsConfig;
import org.thingsboard.server.queue.edqs.EdqsConfig.EdqsPartitioningStrategy;
import org.thingsboard.server.queue.edqs.EdqsExecutors;
import org.thingsboard.server.queue.edqs.EdqsQueueFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.msg.queue.TopicPartitionInfo.withTopic;

@EdqsComponent
@Service
@RequiredArgsConstructor
@Slf4j
public class EdqsProcessor implements TbQueueHandler<TbProtoQueueMsg<ToEdqsMsg>, TbProtoQueueMsg<FromEdqsMsg>> {

    private final EdqsQueueFactory queueFactory;
    private final EdqsConverter converter;
    private final EdqsRepository repository;
    private final EdqsConfig config;
    private final EdqsExecutors edqsExecutors;
    private final EdqsPartitionService partitionService;
    private final TopicService topicService;
    private final ConfigurableApplicationContext applicationContext;
    private final EdqsStateService stateService;

    private PartitionedQueueConsumerManager<TbProtoQueueMsg<ToEdqsMsg>> eventConsumer;
    private PartitionedQueueResponseTemplate<TbProtoQueueMsg<ToEdqsMsg>, TbProtoQueueMsg<FromEdqsMsg>> responseTemplate;
    private ListeningExecutorService requestExecutor;

    private final VersionsStore versionsStore = new VersionsStore();
    private final AtomicInteger counter = new AtomicInteger();

    @Getter
    private Consumer<Throwable> errorHandler;

    @PostConstruct
    private void init() {
        errorHandler = error -> {
            if (error instanceof OutOfMemoryError) {
                log.error("OOM detected, shutting down");
                repository.clear();
                Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("edqs-shutdown"))
                        .execute(applicationContext::close);
            }
        };
        requestExecutor = edqsExecutors.getRequestExecutor();

        eventConsumer = PartitionedQueueConsumerManager.<TbProtoQueueMsg<ToEdqsMsg>>create()
                .queueKey(new QueueKey(ServiceType.EDQS, config.getEventsTopic()))
                .topic(topicService.buildTopicName(config.getEventsTopic()))
                .pollInterval(config.getPollInterval())
                .msgPackProcessor((msgs, consumer, config) -> {
                    for (TbProtoQueueMsg<ToEdqsMsg> queueMsg : msgs) {
                        if (consumer.isStopped()) {
                            return;
                        }
                        try {
                            ToEdqsMsg msg = queueMsg.getValue();
                            process(msg, true);
                        } catch (Exception t) {
                            log.error("Failed to process message: {}", queueMsg, t);
                        }
                    }
                    consumer.commit();
                })
                .consumerCreator((config, tpi) -> queueFactory.createEdqsEventsConsumer())
                .queueAdmin(queueFactory.getEdqsQueueAdmin())
                .consumerExecutor(edqsExecutors.getConsumersExecutor())
                .taskExecutor(edqsExecutors.getConsumerTaskExecutor())
                .scheduler(edqsExecutors.getScheduler())
                .uncaughtErrorHandler(errorHandler)
                .build();
        responseTemplate = queueFactory.createEdqsResponseTemplate(this);

        stateService.init(eventConsumer, List.of(responseTemplate.getRequestConsumer()));
    }

    @EventListener
    public void onPartitionsChange(PartitionChangeEvent event) {
        if (event.getServiceType() != ServiceType.EDQS) {
            return;
        }
        try {
            Set<TopicPartitionInfo> newPartitions = event.getNewPartitions().get(new QueueKey(ServiceType.EDQS));
            stateService.process(withTopic(newPartitions, topicService.buildTopicName(config.getStateTopic())));
            // partitions for event and request consumers are updated by stateService

            Set<TopicPartitionInfo> oldPartitions = event.getOldPartitions().get(new QueueKey(ServiceType.EDQS));
            if (CollectionsUtil.isNotEmpty(oldPartitions)) {
                Set<Integer> removedPartitions = Sets.difference(oldPartitions, newPartitions).stream()
                        .map(tpi -> tpi.getPartition().orElse(-1)).collect(Collectors.toSet());
                if (removedPartitions.isEmpty()) {
                    return;
                }

                if (config.getPartitioningStrategy() == EdqsPartitioningStrategy.TENANT) {
                    repository.clearIf(tenantId -> {
                        Integer partition = partitionService.resolvePartition(tenantId, null);
                        return removedPartitions.contains(partition);
                    });
                } else {
                    log.warn("Partitions {} were removed but shouldn't be (due to NONE partitioning strategy)", removedPartitions);
                }
            }
        } catch (Throwable t) {
            log.error("Failed to handle partition change event {}", event, t);
        }
    }

    @Override
    public ListenableFuture<TbProtoQueueMsg<FromEdqsMsg>> handle(TbProtoQueueMsg<ToEdqsMsg> queueMsg) {
        ToEdqsMsg toEdqsMsg = queueMsg.getValue();
        return requestExecutor.submit(() -> {
            EdqsRequest request;
            TenantId tenantId;
            CustomerId customerId;
            try {
                request = Objects.requireNonNull(JacksonUtil.fromString(toEdqsMsg.getRequestMsg().getValue(), EdqsRequest.class));
                tenantId = getTenantId(toEdqsMsg);
                customerId = getCustomerId(toEdqsMsg);
            } catch (Exception e) {
                log.error("Failed to parse request msg: {}", toEdqsMsg, e);
                throw e;
            }

            EdqsResponse response = processRequest(tenantId, customerId, request);
            return new TbProtoQueueMsg<>(queueMsg.getKey(), FromEdqsMsg.newBuilder()
                    .setResponseMsg(TransportProtos.EdqsResponseMsg.newBuilder()
                            .setValue(JacksonUtil.toString(response))
                            .build())
                    .build(), queueMsg.getHeaders());
        });
    }

    private EdqsResponse processRequest(TenantId tenantId, CustomerId customerId, EdqsRequest request) {
        EdqsResponse response = new EdqsResponse();
        try {
            if (request.getEntityDataQuery() != null) {
                PageData<QueryResult> result = repository.findEntityDataByQuery(tenantId, customerId,
                        request.getUserPermissions(), request.getEntityDataQuery(), false);
                response.setEntityDataQueryResult(result.mapData(QueryResult::toOldEntityData));
            } else if (request.getEntityCountQuery() != null) {
                long result = repository.countEntitiesByQuery(tenantId, customerId, request.getUserPermissions(), request.getEntityCountQuery(), tenantId.isSysTenantId());
                response.setEntityCountQueryResult(result);
            }
            log.trace("[{}] Request: {}, response: {}", tenantId, request, response);
        } catch (Throwable e) {
            log.error("[{}] Failed to process request: {}", tenantId, request, e);
            response.setError(ExceptionUtil.getMessage(e));
        }
        return response;
    }

    public void process(ToEdqsMsg edqsMsg, boolean backup) {
        log.trace("Processing message: {}", edqsMsg);
        if (edqsMsg.hasEventMsg()) {
            EdqsEventMsg eventMsg = edqsMsg.getEventMsg();
            TenantId tenantId = getTenantId(edqsMsg);
            ObjectType objectType = ObjectType.valueOf(eventMsg.getObjectType());
            EdqsEventType eventType = EdqsEventType.valueOf(eventMsg.getEventType());
            String key = eventMsg.getKey();
            Long version = eventMsg.hasVersion() ? eventMsg.getVersion() : null;

            if (version != null) {
                if (!versionsStore.isNew(key, version)) {
                    return;
                }
            } else if (!ObjectType.unversionedTypes.contains(objectType)) {
                log.warn("[{}] {} {} doesn't have version", tenantId, objectType, key);
            }
            if (backup) {
                stateService.save(tenantId, objectType, key, eventType, edqsMsg);
            }

            EdqsObject object = converter.deserialize(objectType, eventMsg.getData().toByteArray());
            log.debug("[{}] Processing event [{}] [{}] [{}] [{}]", tenantId, objectType, eventType, key, version);
            int count = counter.incrementAndGet();
            if (count % 100000 == 0) {
                log.info("Processed {} events", count);
            }

            EdqsEvent event = EdqsEvent.builder()
                    .tenantId(tenantId)
                    .objectType(objectType)
                    .eventType(eventType)
                    .object(object)
                    .build();
            repository.processEvent(event);
        }
    }

    private TenantId getTenantId(ToEdqsMsg edqsMsg) {
        return TenantId.fromUUID(new UUID(edqsMsg.getTenantIdMSB(), edqsMsg.getTenantIdLSB()));
    }

    private CustomerId getCustomerId(ToEdqsMsg edqsMsg) {
        if (edqsMsg.getCustomerIdMSB() != 0 && edqsMsg.getCustomerIdLSB() != 0) {
            return new CustomerId(new UUID(edqsMsg.getCustomerIdMSB(), edqsMsg.getCustomerIdLSB()));
        } else {
            return null;
        }
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        eventConsumer.stop();
        eventConsumer.awaitStop();
        responseTemplate.stop();
        stateService.stop();
    }

}
