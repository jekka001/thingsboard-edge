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
package org.thingsboard.server.service.housekeeper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.gen.transport.TransportProtos.HousekeeperTaskProto;
import org.thingsboard.server.gen.transport.TransportProtos.ToHousekeeperServiceMsg;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.provider.TbCoreQueueFactory;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.queue.util.AfterStartUp;
import org.thingsboard.server.queue.util.TbCoreComponent;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TbCoreComponent
@Service
@Slf4j
public class HousekeeperReprocessingService {

    private final DefaultHousekeeperService housekeeperService;
    private final TbQueueProducerProvider producerProvider;
    private final TbQueueConsumer<TbProtoQueueMsg<ToHousekeeperServiceMsg>> consumer;
    private final ExecutorService consumerExecutor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("housekeeper-reprocessing-consumer"));

    @Value("${queue.core.housekeeper.poll-interval-ms:10000}")
    private int pollInterval;

    private final long startTs = System.currentTimeMillis(); // fixme: some other tb-core might start earlier and submit for reprocessing
    private boolean stopped;
    // todo: stats

    public HousekeeperReprocessingService(@Lazy DefaultHousekeeperService housekeeperService,
                                          TbCoreQueueFactory queueFactory,
                                          TbQueueProducerProvider producerProvider) {
        this.housekeeperService = housekeeperService;
        this.consumer = queueFactory.createHousekeeperDelayedMsgConsumer();
        this.producerProvider = producerProvider;
    }

    @AfterStartUp(order = AfterStartUp.REGULAR_SERVICE)
    public void afterStartUp() {
        consumer.subscribe();
        consumerExecutor.submit(() -> {
            while (!stopped && !consumer.isStopped()) {
                try {
                    List<TbProtoQueueMsg<ToHousekeeperServiceMsg>> msgs = consumer.poll(pollInterval);
                    if (msgs.isEmpty()) {
                        stop();
                        return;
                    }

                    if (msgs.stream().anyMatch(msg -> {
                        boolean newMsg = msg.getValue().getTask().getTs() >= startTs;
                        if (newMsg) {
                            log.info("Stopping reprocessing due to msg is new {}", msg);
                        }
                        return newMsg;
                    })) {
                        stop(); // fixme: we should commit already reprocessed messages; maybe submit for reprocessing again and commit?
                        // msg batch size should be 1. otherwise some tasks won't be reprocessed
                        return;
                    }
                    for (TbProtoQueueMsg<ToHousekeeperServiceMsg> msg : msgs) {
                        try {
                            housekeeperService.processTask(msg.getValue());// fixme: or should we submit to queue?
                            Thread.sleep(1000);
                        } catch (Exception e) {
                            log.error("Message processing failed", e);
                        }
                    }
                    consumer.commit();
                } catch (Throwable t) {
                    if (!consumer.isStopped()) {
                        log.warn("Failed to process messages from queue", t);
                        try {
                            Thread.sleep(pollInterval);
                        } catch (InterruptedException interruptedException) {
                            log.trace("Failed to wait until the server has capacity to handle new requests", interruptedException);
                        }
                    }
                }
            }
        });
        log.info("Started Housekeeper tasks reprocessing");
    }

    public void submitForReprocessing(ToHousekeeperServiceMsg msg) {
        HousekeeperTaskProto task = msg.getTask();
        int attempt = task.getAttempt() + 1;
        msg = msg.toBuilder()
                .setTask(task.toBuilder()
                        .setAttempt(attempt)
                        .setTs(System.currentTimeMillis()) // maybe set ts + 1 hour so that no-one reprocesses it immediately
                        .build())
                .build();

        var producer = producerProvider.getHousekeeperDelayedMsgProducer();
        TopicPartitionInfo tpi = TopicPartitionInfo.builder().topic(producer.getDefaultTopic()).build();
        // fixme submit with the same msg key, so that the messages goes to this consumer and will not be processed by anyone else
        producer.send(tpi, new TbProtoQueueMsg<>(UUID.randomUUID(), msg), null);
    }

    @PreDestroy
    private void stop() {
        log.info("Stopped Housekeeper tasks reprocessing");
        stopped = true;
        consumer.unsubscribe();
        consumerExecutor.shutdownNow();
    }

}
