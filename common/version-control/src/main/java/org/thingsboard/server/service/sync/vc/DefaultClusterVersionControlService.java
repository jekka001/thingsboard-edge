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
package org.thingsboard.server.service.sync.vc;

import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jgit.errors.LargeObjectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.SortOrder;
import org.thingsboard.server.common.data.sync.vc.VersionCreationResult;
import org.thingsboard.server.common.data.sync.vc.VersionedEntityInfo;
import org.thingsboard.server.common.data.util.CollectionsUtil;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.common.util.ProtoUtils;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.AddMsg;
import org.thingsboard.server.gen.transport.TransportProtos.BranchInfoProto;
import org.thingsboard.server.gen.transport.TransportProtos.CommitRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.CommitResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.DeleteMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntitiesContentRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntitiesContentResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntityContentRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntityContentResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntityVersionProto;
import org.thingsboard.server.gen.transport.TransportProtos.ListBranchesRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListBranchesResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListEntitiesRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListEntitiesResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListVersionsRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListVersionsResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PrepareMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToCoreNotificationMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToVersionControlServiceMsg;
import org.thingsboard.server.gen.transport.TransportProtos.VersionControlResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.VersionedEntityInfoProto;
import org.thingsboard.server.queue.TbQueueConsumer;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.common.consumer.QueueConsumerManager;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.discovery.TbApplicationEventListener;
import org.thingsboard.server.queue.discovery.TopicService;
import org.thingsboard.server.queue.discovery.event.PartitionChangeEvent;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.queue.provider.TbVersionControlQueueFactory;
import org.thingsboard.server.queue.util.AfterStartUp;
import org.thingsboard.server.queue.util.TbVersionControlComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.server.service.sync.vc.DefaultGitRepositoryService.fromRelativePath;

@Slf4j
@TbVersionControlComponent
@Service
@RequiredArgsConstructor
public class DefaultClusterVersionControlService extends TbApplicationEventListener<PartitionChangeEvent> implements ClusterVersionControlService {

    private final PartitionService partitionService;
    private final TbQueueProducerProvider producerProvider;
    private final TbVersionControlQueueFactory queueFactory;
    private final GitRepositoryService vcService;
    private final TopicService topicService;

    private final ConcurrentMap<TenantId, Lock> tenantRepoLocks = new ConcurrentHashMap<>();
    private final Map<TenantId, PendingCommit> pendingCommitMap = new HashMap<>();

    private volatile ExecutorService consumerExecutor;
    private volatile QueueConsumerManager<TbProtoQueueMsg<ToVersionControlServiceMsg>> consumer;
    private volatile TbQueueProducer<TbProtoQueueMsg<ToCoreNotificationMsg>> producer;

    @Value("${queue.vc.poll-interval:25}")
    private long pollDuration;
    @Value("${queue.vc.pack-processing-timeout:180000}")
    private long packProcessingTimeout;
    @Value("${vc.git.io_pool_size:3}")
    private int ioPoolSize;
    @Value("${queue.vc.msg-chunk-size:250000}")
    private int msgChunkSize;

    //We need to manually manage the threads since tasks for particular tenant need to be processed sequentially.
    private final List<ListeningExecutorService> ioThreads = new ArrayList<>();


    @PostConstruct
    public void init() {
        consumerExecutor = Executors.newCachedThreadPool(ThingsBoardThreadFactory.forName("vc-consumer"));
        var threadFactory = ThingsBoardThreadFactory.forName("vc-io-thread");
        for (int i = 0; i < ioPoolSize; i++) {
            ioThreads.add(MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(threadFactory)));
        }
        producer = producerProvider.getTbCoreNotificationsMsgProducer();
        consumer = QueueConsumerManager.<TbProtoQueueMsg<ToVersionControlServiceMsg>>builder()
                .name("TB Version Control")
                .msgPackProcessor(this::processMsgs)
                .pollInterval(pollDuration)
                .consumerCreator(queueFactory::createToVersionControlMsgConsumer)
                .consumerExecutor(consumerExecutor)
                .build();
    }

    @PreDestroy
    public void stop() {
        if (consumer != null) {
            consumer.stop();
        }
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
        ioThreads.forEach(ExecutorService::shutdownNow);
    }

    @Override
    protected void onTbApplicationEvent(PartitionChangeEvent event) {
        for (TenantId tenantId : vcService.getActiveRepositoryTenants()) {
            if (!partitionService.isMyPartition(ServiceType.TB_VC_EXECUTOR, tenantId, tenantId)) {
                var lock = getRepoLock(tenantId);
                lock.lock();
                try {
                    pendingCommitMap.remove(tenantId);
                    vcService.clearRepository(tenantId);
                } catch (Exception e) {
                    log.warn("[{}] Failed to cleanup the tenant repository", tenantId, e);
                } finally {
                    lock.unlock();
                }
            }
        }
        consumer.subscribe(event.getNewPartitions().values().stream().findAny().orElse(Collections.emptySet()));
    }

    @Override
    protected boolean filterTbApplicationEvent(PartitionChangeEvent event) {
        return ServiceType.TB_VC_EXECUTOR.equals(event.getServiceType());
    }

    @AfterStartUp(order = 2)
    public void afterStartUp() {
        consumer.launch();
    }

    void processMsgs(List<TbProtoQueueMsg<ToVersionControlServiceMsg>> msgs, TbQueueConsumer<TbProtoQueueMsg<ToVersionControlServiceMsg>> consumer) throws Exception {
        List<ListenableFuture<?>> futures = new ArrayList<>();
        for (TbProtoQueueMsg<ToVersionControlServiceMsg> msgWrapper : msgs) {
            ToVersionControlServiceMsg msg = msgWrapper.getValue();
            var ctx = new VersionControlRequestCtx(msg, msg.hasClearRepositoryRequest() ? null : ProtoUtils.fromProto(msg.getVcSettings()));
            long startTs = System.currentTimeMillis();
            log.trace("[{}][{}] RECEIVED task: {}", ctx.getTenantId(), ctx.getRequestId(), msg);
            int threadIdx = Math.abs(ctx.getTenantId().hashCode() % ioPoolSize);
            ListenableFuture<Void> future = ioThreads.get(threadIdx).submit(() -> processMessage(ctx, msg));
            logTaskExecution(ctx, future, startTs);
            futures.add(future);
        }
        try {
            Futures.allAsList(futures).get(packProcessingTimeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("Timeout for processing the version control tasks.", e);
        }
        consumer.commit();
    }

    private Void processMessage(VersionControlRequestCtx ctx, ToVersionControlServiceMsg msg) {
        var lock = getRepoLock(ctx.getTenantId());
        lock.lock();
        try {
            if (msg.hasClearRepositoryRequest()) {
                handleClearRepositoryCommand(ctx);
            } else {
                if (msg.hasTestRepositoryRequest()) {
                    handleTestRepositoryCommand(ctx);
                } else if (msg.hasInitRepositoryRequest()) {
                    handleInitRepositoryCommand(ctx);
                } else {
                    var currentSettings = vcService.getRepositorySettings(ctx.getTenantId());
                    var newSettings = ctx.getSettings();
                    if (!newSettings.equals(currentSettings)) {
                        vcService.initRepository(ctx.getTenantId(), ctx.getSettings(), false);
                    }
                    if (msg.hasCommitRequest()) {
                        handleCommitRequest(ctx, msg.getCommitRequest());
                    } else if (msg.hasListBranchesRequest()) {
                        vcService.fetch(ctx.getTenantId());
                        handleListBranches(ctx, msg.getListBranchesRequest());
                    } else if (msg.hasListEntitiesRequest()) {
                        handleListEntities(ctx, msg.getListEntitiesRequest());
                    } else if (msg.hasListVersionRequest()) {
                        vcService.fetch(ctx.getTenantId());
                        handleListVersions(ctx, msg.getListVersionRequest());
                    } else if (msg.hasEntityContentRequest()) {
                        handleEntityContentRequest(ctx, msg.getEntityContentRequest());
                    } else if (msg.hasEntitiesContentRequest()) {
                        handleEntitiesContentRequest(ctx, msg.getEntitiesContentRequest());
                    } else if (msg.hasVersionsDiffRequest()) {
                        handleVersionsDiffRequest(ctx, msg.getVersionsDiffRequest());
                    }
                }
            }
        } catch (Exception e) {
            reply(ctx, Optional.of(handleError(e)));
        } finally {
            lock.unlock();
        }
        return null;
    }

    private void handleEntitiesContentRequest(VersionControlRequestCtx ctx, EntitiesContentRequestMsg request) throws Exception {
        var entityType = EntityType.valueOf(request.getEntityType());
        if (request.getIdsList().isEmpty()) {
            var ids = vcService.listEntitiesAtVersion(ctx.getTenantId(), request.getVersionId(), request.getPath(), entityType, request.getGroups(), request.getRecursive())
                    .skip(request.getOffset()).limit(request.getLimit()).collect(Collectors.toList());
            if (!ids.isEmpty()) {
                for (int i = 0; i < ids.size(); i++) {
                    var info = ids.get(i);
                    sendData(info.getPath(), request, ctx, i, ids.size());
                }
            } else {
                reply(ctx, Optional.empty(), builder -> builder.setEntitiesContentResponse(
                        EntitiesContentResponseMsg.newBuilder()
                                .setItemsCount(0)));
            }
        } else {
            for (int i = 0; i < request.getIdsList().size(); i++) {
                var idProto = request.getIdsList().get(i);
                UUID uuid = new UUID(idProto.getEntityIdMSB(), idProto.getEntityIdLSB());
                var entityPath = getRelativePath(EntityType.valueOf(request.getEntityType()), uuid.toString());
                sendData(entityPath, request, ctx, i, request.getIdsCount());
            }
        }
    }

    private void sendData(String entityPath, EntitiesContentRequestMsg request, VersionControlRequestCtx ctx, int itemIdx, int totalItemsCount) throws IOException {
        entityPath = StringUtils.isNotEmpty(request.getPath()) ? request.getPath() + entityPath : entityPath;
        var data = vcService.getFileContentAtCommit(ctx.getTenantId(), entityPath, request.getVersionId());

        Iterable<String> dataChunks = StringUtils.split(data, msgChunkSize);
        String chunkedMsgId = UUID.randomUUID().toString();
        int chunksCount = Iterables.size(dataChunks);
        AtomicInteger chunkIndex = new AtomicInteger();
        dataChunks.forEach(chunk -> {
            EntitiesContentResponseMsg.Builder response = EntitiesContentResponseMsg.newBuilder()
                    .setItemsCount(totalItemsCount)
                    .setItemIdx(itemIdx)
                    .setItem(EntityContentResponseMsg.newBuilder()
                            .setData(chunk)
                            .setChunksCount(chunksCount)
                            .setChunkIndex(chunkIndex.getAndIncrement())
                            .build());
            reply(ctx, Optional.empty(), builder -> builder.setEntitiesContentResponse(response));
        });
    }

    private void handleEntityContentRequest(VersionControlRequestCtx ctx, EntityContentRequestMsg request) throws IOException {
        String path = StringUtils.isNotEmpty(request.getPath()) ? request.getPath() : "";
        if (StringUtils.isNotEmpty(request.getEntityType())) {
            path = path + getRelativePath(EntityType.valueOf(request.getEntityType()), new UUID(request.getEntityIdMSB(), request.getEntityIdLSB()).toString());
        }
        log.debug("Executing handleEntityContentRequest [{}][{}]", ctx.getTenantId(), path);
        String data = vcService.getFileContentAtCommit(ctx.getTenantId(), path, request.getVersionId());

        Iterable<String> dataChunks = StringUtils.split(data, msgChunkSize);
        String chunkedMsgId = UUID.randomUUID().toString();
        int chunksCount = Iterables.size(dataChunks);

        AtomicInteger chunkIndex = new AtomicInteger();
        dataChunks.forEach(chunk -> {
            log.trace("[{}] sending chunk {} for 'getEntity'", chunkedMsgId, chunkIndex.get());
            reply(ctx, Optional.empty(), builder -> builder.setEntityContentResponse(EntityContentResponseMsg.newBuilder()
                    .setData(chunk).setChunksCount(chunksCount)
                    .setChunkIndex(chunkIndex.getAndIncrement())));
        });
    }

    private void handleListVersions(VersionControlRequestCtx ctx, ListVersionsRequestMsg request) throws Exception {
        String path;
        if (StringUtils.isNotEmpty(request.getEntityType())) {
            var entityType = EntityType.valueOf(request.getEntityType());
            if (request.getEntityIdLSB() != 0 || request.getEntityIdMSB() != 0) {
                path = getRelativePath(entityType, new UUID(request.getEntityIdMSB(), request.getEntityIdLSB()).toString());
            } else {
                path = getRelativePath(entityType, null);
            }
        } else {
            path = null;
        }
        SortOrder sortOrder = null;
        if (StringUtils.isNotEmpty(request.getSortProperty())) {
            var direction = SortOrder.Direction.DESC;
            if (StringUtils.isNotEmpty(request.getSortDirection())) {
                direction = SortOrder.Direction.valueOf(request.getSortDirection());
            }
            sortOrder = new SortOrder(request.getSortProperty(), direction);
        }
        if (StringUtils.isNotEmpty(request.getPath())) {
            path = request.getPath() + path;
        }
        var data = vcService.listVersions(ctx.getTenantId(), request.getBranchName(), path,
                new PageLink(request.getPageSize(), request.getPage(), request.getTextSearch(), sortOrder));
        reply(ctx, Optional.empty(), builder ->
                builder.setListVersionsResponse(ListVersionsResponseMsg.newBuilder()
                        .setTotalPages(data.getTotalPages())
                        .setTotalElements(data.getTotalElements())
                        .setHasNext(data.hasNext())
                        .addAllVersions(data.getData().stream().map(
                                v -> EntityVersionProto.newBuilder().setTs(v.getTimestamp()).setId(v.getId()).setName(v.getName()).setAuthor(v.getAuthor()).build()
                        ).collect(Collectors.toList())))
        );
    }

    private void handleListEntities(VersionControlRequestCtx ctx, ListEntitiesRequestMsg request) throws Exception {
        EntityType entityType = StringUtils.isNotEmpty(request.getEntityType()) ? EntityType.valueOf(request.getEntityType()) : null;
        var data = vcService.listEntitiesAtVersion(ctx.getTenantId(), request.getVersionId(), "", entityType, false, false);
        reply(ctx, Optional.empty(), builder ->
                builder.setListEntitiesResponse(ListEntitiesResponseMsg.newBuilder()
                        .addAllEntities(data.map(VersionedEntityInfo::getExternalId).map(
                                id -> VersionedEntityInfoProto.newBuilder()
                                        .setEntityType(id.getEntityType().name())
                                        .setEntityIdMSB(id.getId().getMostSignificantBits())
                                        .setEntityIdLSB(id.getId().getLeastSignificantBits()).build()
                        ).collect(Collectors.toList()))));
    }

    private void handleListBranches(VersionControlRequestCtx ctx, ListBranchesRequestMsg request) {
        var branches = vcService.listBranches(ctx.getTenantId()).stream()
                .map(branchInfo -> BranchInfoProto.newBuilder()
                        .setName(branchInfo.getName())
                        .setIsDefault(branchInfo.isDefault()).build()).collect(Collectors.toList());
        reply(ctx, Optional.empty(), builder -> builder.setListBranchesResponse(ListBranchesResponseMsg.newBuilder().addAllBranches(branches)));
    }

    private void handleVersionsDiffRequest(VersionControlRequestCtx ctx, TransportProtos.VersionsDiffRequestMsg request) throws IOException {
        List<TransportProtos.EntityVersionsDiff> diffList = vcService.getVersionsDiffList(ctx.getTenantId(), request.getPath(), request.getVersionId1(), request.getVersionId2()).stream()
                .map(diff -> {
                    EntityId entityId = fromRelativePath(diff.getFilePath());
                    return TransportProtos.EntityVersionsDiff.newBuilder()
                            .setEntityType(entityId.getEntityType().name())
                            .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                            .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                            .setEntityDataAtVersion1(diff.getFileContentAtCommit1())
                            .setEntityDataAtVersion2(diff.getFileContentAtCommit2())
                            .setRawDiff(diff.getDiffStringValue())
                            .build();
                })
                .collect(Collectors.toList());

        reply(ctx, builder -> builder.setVersionsDiffResponse(TransportProtos.VersionsDiffResponseMsg.newBuilder()
                .addAllDiff(diffList)));
    }

    private void handleCommitRequest(VersionControlRequestCtx ctx, CommitRequestMsg request) throws Exception {
        log.debug("Executing handleCommitRequest [{}][{}]", ctx.getTenantId(), ctx.getRequestId());
        var tenantId = ctx.getTenantId();
        UUID txId = UUID.fromString(request.getTxId());
        if (request.hasPrepareMsg()) {
            vcService.fetch(ctx.getTenantId());
            prepareCommit(ctx, txId, request.getPrepareMsg());
        } else if (request.hasAbortMsg()) {
            PendingCommit current = pendingCommitMap.get(tenantId);
            if (current != null && current.getTxId().equals(txId)) {
                doAbortCurrentCommit(tenantId, current);
            }
        } else {
            PendingCommit current = pendingCommitMap.get(tenantId);
            if (current != null && current.getTxId().equals(txId)) {
                try {
                    if (request.hasAddMsg()) {
                        addToCommit(ctx, current, request.getAddMsg());
                    } else if (request.hasDeleteMsg()) {
                        deleteFromCommit(ctx, current, request.getDeleteMsg());
                    } else if (request.hasPushMsg()) {
                        var result = vcService.push(current);
                        pendingCommitMap.remove(ctx.getTenantId());
                        reply(ctx, result);
                    }
                } catch (Exception e) {
                    doAbortCurrentCommit(tenantId, current, e);
                    throw e;
                }
            } else {
                log.debug("[{}] Ignore request due to stale commit: {}", txId, request);
            }
        }
    }

    private void prepareCommit(VersionControlRequestCtx ctx, UUID txId, PrepareMsg prepareMsg) {
        var tenantId = ctx.getTenantId();
        var pendingCommit = new PendingCommit(tenantId, ctx.getNodeId(), txId, prepareMsg.getBranchName(),
                prepareMsg.getCommitMsg(), prepareMsg.getAuthorName(), prepareMsg.getAuthorEmail());
        PendingCommit old = pendingCommitMap.get(tenantId);
        if (old != null) {
            doAbortCurrentCommit(tenantId, old);
        }
        pendingCommitMap.put(tenantId, pendingCommit);
        vcService.prepareCommit(pendingCommit);
    }

    private void deleteFromCommit(VersionControlRequestCtx ctx, PendingCommit commit, DeleteMsg deleteMsg) throws IOException {
        vcService.deleteFolderContent(commit, deleteMsg.getFolder(), deleteMsg.getRecursively());
    }

    private void addToCommit(VersionControlRequestCtx ctx, PendingCommit commit, AddMsg addMsg) throws IOException {
        log.debug("Executing addToCommit [{}][{}]", ctx.getTenantId(), ctx.getRequestId());
        log.trace("[{}] received chunk {} for 'addToCommit'", addMsg.getChunkedMsgId(), addMsg.getChunkIndex());
        Map<String, String[]> chunkedMsgs = commit.getChunkedMsgs();
        String[] msgChunks = chunkedMsgs.computeIfAbsent(addMsg.getChunkedMsgId(), id -> new String[addMsg.getChunksCount()]);
        msgChunks[addMsg.getChunkIndex()] = addMsg.getEntityDataJsonChunk();
        if (CollectionsUtil.countNonNull(msgChunks) == msgChunks.length) {
            log.trace("[{}] collected all chunks for 'addToCommit'", addMsg.getChunkedMsgId());
            String entityDataJson = String.join("", msgChunks);
            chunkedMsgs.remove(addMsg.getChunkedMsgId());
            vcService.add(commit, addMsg.getRelativePath(), entityDataJson);
        }
    }

    private void doAbortCurrentCommit(TenantId tenantId, PendingCommit current) {
        doAbortCurrentCommit(tenantId, current, null);
    }

    private void doAbortCurrentCommit(TenantId tenantId, PendingCommit current, Exception e) {
        vcService.abort(current);
        pendingCommitMap.remove(tenantId);
        //TODO: push notification to core using old.getNodeId() to cancel old commit processing on the caller side.
    }

    private void handleClearRepositoryCommand(VersionControlRequestCtx ctx) {
        try {
            vcService.clearRepository(ctx.getTenantId());
            reply(ctx, Optional.empty());
        } catch (Exception e) {
            log.debug("[{}] Failed to connect to the repository: ", ctx, e);
            reply(ctx, Optional.of(e));
        }
    }

    private void handleInitRepositoryCommand(VersionControlRequestCtx ctx) {
        try {
            vcService.initRepository(ctx.getTenantId(), ctx.getSettings(), false);
            reply(ctx, Optional.empty());
        } catch (Exception e) {
            log.debug("[{}] Failed to connect to the repository: ", ctx, e);
            reply(ctx, Optional.of(e));
        }
    }


    private void handleTestRepositoryCommand(VersionControlRequestCtx ctx) {
        try {
            vcService.testRepository(ctx.getTenantId(), ctx.getSettings());
            reply(ctx, Optional.empty());
        } catch (Exception e) {
            log.debug("[{}] Failed to connect to the repository: ", ctx, e);
            reply(ctx, Optional.of(e));
        }
    }

    private Exception handleError(Exception e) {
        if (e instanceof LargeObjectException) {
            return new RuntimeException("Version is too big");
        }
        return e;
    }

    private void reply(VersionControlRequestCtx ctx, VersionCreationResult result) {
        var responseBuilder = CommitResponseMsg.newBuilder().setAdded(result.getAdded())
                .setModified(result.getModified())
                .setRemoved(result.getRemoved());

        if (result.getVersion() != null) {
            responseBuilder.setTs(result.getVersion().getTimestamp())
                    .setCommitId(result.getVersion().getId())
                    .setName(result.getVersion().getName())
                    .setAuthor(result.getVersion().getAuthor());
        }

        reply(ctx, Optional.empty(), builder -> builder.setCommitResponse(responseBuilder));
    }

    private void reply(VersionControlRequestCtx ctx, Optional<Exception> e) {
        reply(ctx, e, null);
    }

    private void reply(VersionControlRequestCtx ctx, Function<VersionControlResponseMsg.Builder, VersionControlResponseMsg.Builder> enrichFunction) {
        reply(ctx, Optional.empty(), enrichFunction);
    }

    private void reply(VersionControlRequestCtx ctx, Optional<Exception> e, Function<VersionControlResponseMsg.Builder, VersionControlResponseMsg.Builder> enrichFunction) {
        TopicPartitionInfo tpi = topicService.getNotificationsTopic(ServiceType.TB_CORE, ctx.getNodeId());
        VersionControlResponseMsg.Builder builder = VersionControlResponseMsg.newBuilder()
                .setRequestIdMSB(ctx.getRequestId().getMostSignificantBits())
                .setRequestIdLSB(ctx.getRequestId().getLeastSignificantBits());
        if (e.isPresent()) {
            log.debug("[{}][{}] Failed to process task", ctx.getTenantId(), ctx.getRequestId(), e.get());
            var message = e.get().getMessage();
            builder.setError(message != null ? message : e.get().getClass().getSimpleName());
        } else {
            if (enrichFunction != null) {
                builder = enrichFunction.apply(builder);
            } else {
                builder.setGenericResponse(TransportProtos.GenericRepositoryResponseMsg.newBuilder().build());
            }
            log.debug("[{}][{}] Processed task", ctx.getTenantId(), ctx.getRequestId());
        }

        ToCoreNotificationMsg msg = ToCoreNotificationMsg.newBuilder().setVcResponseMsg(builder).build();
        log.trace("[{}][{}] PUSHING reply: {} to: {}", ctx.getTenantId(), ctx.getRequestId(), msg, tpi);
        producer.send(tpi, new TbProtoQueueMsg<>(UUID.randomUUID(), msg), null);
    }

    private String getRelativePath(EntityType entityType, String entityId) {
        String path = entityType.name().toLowerCase();
        if (entityId != null) {
            path += "/" + entityId + ".json";
        }
        return path;
    }

    private Lock getRepoLock(TenantId tenantId) {
        return tenantRepoLocks.computeIfAbsent(tenantId, t -> new ReentrantLock(true));
    }

    private void logTaskExecution(VersionControlRequestCtx ctx, ListenableFuture<Void> future, long startTs) {
        if (log.isTraceEnabled()) {
            Futures.addCallback(future, new FutureCallback<Object>() {

                @Override
                public void onSuccess(@Nullable Object result) {
                    log.trace("[{}][{}] Task processing took: {}ms", ctx.getTenantId(), ctx.getRequestId(), (System.currentTimeMillis() - startTs));
                }

                @Override
                public void onFailure(Throwable t) {
                    log.trace("[{}][{}] Task failed: ", ctx.getTenantId(), ctx.getRequestId(), t);
                }
            }, MoreExecutors.directExecutor());
        }
    }

}
