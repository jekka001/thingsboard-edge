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
package org.thingsboard.server.service.sync.vc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ExportableEntity;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.common.data.sync.vc.RepositorySettings;
import org.thingsboard.server.common.data.sync.vc.EntityVersion;
import org.thingsboard.server.common.data.sync.vc.EntityVersionsDiff;
import org.thingsboard.server.common.data.sync.vc.VersionCreationResult;
import org.thingsboard.server.common.data.sync.vc.VersionedEntityInfo;
import org.thingsboard.server.common.data.sync.vc.request.create.VersionCreateRequest;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.CommitRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntitiesContentRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.EntityContentRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GenericRepositoryRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListEntitiesRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ListVersionsRequestMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PrepareMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ToVersionControlServiceMsg;
import org.thingsboard.server.gen.transport.TransportProtos.VersionControlResponseMsg;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.queue.util.DataDecodingEncodingService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.sync.vc.data.*;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@TbCoreComponent
@Service
@Slf4j
public class DefaultGitVersionControlQueueService implements GitVersionControlQueueService {

    private final TbServiceInfoProvider serviceInfoProvider;
    private final TbClusterService clusterService;
    private final DataDecodingEncodingService encodingService;
    private final DefaultEntitiesVersionControlService entitiesVersionControlService;

    private final Map<UUID, PendingGitRequest<?>> pendingRequestMap = new HashMap<>();

    public DefaultGitVersionControlQueueService(TbServiceInfoProvider serviceInfoProvider, TbClusterService clusterService,
                                                DataDecodingEncodingService encodingService,
                                                @Lazy DefaultEntitiesVersionControlService entitiesVersionControlService) {
        this.serviceInfoProvider = serviceInfoProvider;
        this.clusterService = clusterService;
        this.encodingService = encodingService;
        this.entitiesVersionControlService = entitiesVersionControlService;
    }

    @Override
    public ListenableFuture<CommitGitRequest> prepareCommit(User user, VersionCreateRequest request) {
        SettableFuture<CommitGitRequest> future = SettableFuture.create();

        CommitGitRequest commit = new CommitGitRequest(user.getTenantId(), request);
        registerAndSend(commit, builder -> builder.setCommitRequest(
                buildCommitRequest(commit).setPrepareMsg(getCommitPrepareMsg(user, request)).build()
        ).build(), wrap(future, commit));
        return future;
    }

    @Override
    public ListenableFuture<Void> addToCommit(CommitGitRequest commit, EntityExportData<ExportableEntity<EntityId>> entityData) {
        return addToCommit(commit, Collections.emptyList(), entityData);
    }

    @Override
    public ListenableFuture<Void> addToCommit(CommitGitRequest commit, List<CustomerId> parents, EntityExportData<? extends ExportableEntity<? extends EntityId>> entityData) {
        SettableFuture<Void> future = SettableFuture.create();

        String path;
        if(EntityType.ENTITY_GROUP.equals(entityData.getEntityType())){
            EntityGroup group = (EntityGroup) entityData.getEntity();
            path = getHierarchyPath(parents) + getGroupPath(group);
        } else {
            path = getHierarchyPath(parents) + getRelativePath(entityData.getEntityType(), entityData.getExternalId());
        }

        String entityDataJson = JacksonUtil.toPrettyString(entityData.sort());

        registerAndSend(commit, builder -> builder.setCommitRequest(
                buildCommitRequest(commit).setAddMsg(
                        TransportProtos.AddMsg.newBuilder()
                                .setRelativePath(path)
                                .setEntityDataJson(entityDataJson).build()
                ).build()
        ).build(), wrap(future, null));
        return future;
    }

    @Override
    public ListenableFuture<Void> addToCommit(CommitGitRequest commit, List<CustomerId> parents, EntityType type, EntityId groupExternalId, List<EntityId> groupEntityIds) {
        SettableFuture<Void> future = SettableFuture.create();

        String path = getHierarchyPath(parents) + getGroupEntitiesListPath(type, groupExternalId);

        String entityDataJson = JacksonUtil.toPrettyString(groupEntityIds);

        registerAndSend(commit, builder -> builder.setCommitRequest(
                buildCommitRequest(commit).setAddMsg(
                        TransportProtos.AddMsg.newBuilder()
                                .setRelativePath(path)
                                .setEntityDataJson(entityDataJson).build()
                ).build()
        ).build(), wrap(future, null));
        return future;
    }

    @Override
    public ListenableFuture<Void> deleteAll(CommitGitRequest commit, EntityType entityType) {
        SettableFuture<Void> future = SettableFuture.create();

        String path = getRelativePath(entityType, null);

        registerAndSend(commit, builder -> builder.setCommitRequest(
                buildCommitRequest(commit).setDeleteMsg(
                        TransportProtos.DeleteMsg.newBuilder().setRelativePath(path).build()
                ).build()
        ).build(), wrap(future, null));

        return future;
    }

    @Override
    public ListenableFuture<VersionCreationResult> push(CommitGitRequest commit) {
        registerAndSend(commit, builder -> builder.setCommitRequest(
                buildCommitRequest(commit).setPushMsg(
                        TransportProtos.PushMsg.newBuilder().build()
                ).build()
        ).build(), wrap(commit.getFuture()));

        return commit.getFuture();
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, PageLink pageLink) {

        return listVersions(tenantId,
                applyPageLinkParameters(
                        ListVersionsRequestMsg.newBuilder()
                                .setBranchName(branch),
                        pageLink
                ).build());
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, EntityType entityType, PageLink pageLink) {
        return listVersions(tenantId,
                applyPageLinkParameters(
                        ListVersionsRequestMsg.newBuilder()
                                .setBranchName(branch)
                                .setEntityType(entityType.name()),
                        pageLink
                ).build());
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, List<CustomerId> hierarchy, EntityType entityType, EntityId groupId, PageLink pageLink) {
        return listVersions(tenantId, branch, getHierarchyPath(hierarchy) + "groups/", entityType, groupId.getId(), pageLink);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, List<CustomerId> hierarchy, EntityId entityId, PageLink pageLink) {
        return listVersions(tenantId, branch, getHierarchyPath(hierarchy), entityId.getEntityType(), entityId.getId(), pageLink);
    }

    private ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, String path, EntityType entityType, UUID entityUuid, PageLink pageLink) {
        return listVersions(tenantId,
                applyPageLinkParameters(
                        ListVersionsRequestMsg.newBuilder()
                                .setBranchName(branch)
                                .setPath(path)
                                .setEntityType(entityType.name())
                                .setEntityIdMSB(entityUuid.getMostSignificantBits())
                                .setEntityIdLSB(entityUuid.getLeastSignificantBits()),
                        pageLink
                ).build());
    }

    private ListVersionsRequestMsg.Builder applyPageLinkParameters(ListVersionsRequestMsg.Builder builder, PageLink pageLink) {
        builder.setPageSize(pageLink.getPageSize())
                .setPage(pageLink.getPage());
        if (pageLink.getTextSearch() != null) {
            builder.setTextSearch(pageLink.getTextSearch());
        }
        if (pageLink.getSortOrder() != null) {
            if (pageLink.getSortOrder().getProperty() != null) {
                builder.setSortProperty(pageLink.getSortOrder().getProperty());
            }
            if (pageLink.getSortOrder().getDirection() != null) {
                builder.setSortDirection(pageLink.getSortOrder().getDirection().name());
            }
        }
        return builder;
    }

    private ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, ListVersionsRequestMsg requestMsg) {
        ListVersionsGitRequest request = new ListVersionsGitRequest(tenantId);
        return sendRequest(request, builder -> builder.setListVersionRequest(requestMsg));
    }

    @Override
    public ListenableFuture<List<VersionedEntityInfo>> listEntitiesAtVersion(TenantId tenantId, String branch, String versionId, EntityType entityType) {
        return listEntitiesAtVersion(tenantId, ListEntitiesRequestMsg.newBuilder()
                .setBranchName(branch)
                .setVersionId(versionId)
                .setEntityType(entityType.name())
                .build());
    }

    @Override
    public ListenableFuture<List<VersionedEntityInfo>> listEntitiesAtVersion(TenantId tenantId, String branch, String versionId) {
        return listEntitiesAtVersion(tenantId, ListEntitiesRequestMsg.newBuilder()
                .setBranchName(branch)
                .setVersionId(versionId)
                .build());
    }

    private ListenableFuture<List<VersionedEntityInfo>> listEntitiesAtVersion(TenantId tenantId, TransportProtos.ListEntitiesRequestMsg requestMsg) {
        ListEntitiesGitRequest request = new ListEntitiesGitRequest(tenantId);
        return sendRequest(request, builder -> builder.setListEntitiesRequest(requestMsg));
    }

    @Override
    public ListenableFuture<List<String>> listBranches(TenantId tenantId) {
        ListBranchesGitRequest request = new ListBranchesGitRequest(tenantId);
        return sendRequest(request, builder -> builder.setListBranchesRequest(TransportProtos.ListBranchesRequestMsg.newBuilder().build()));
    }

    @Override
    public ListenableFuture<List<EntityVersionsDiff>> getVersionsDiff(TenantId tenantId, EntityType entityType, EntityId externalId, String versionId1, String versionId2) {
        String path = entityType != null ? getRelativePath(entityType, externalId) : "";
        VersionsDiffGitRequest request = new VersionsDiffGitRequest(tenantId, path, versionId1, versionId2);
        return sendRequest(request, builder -> builder.setVersionsDiffRequest(TransportProtos.VersionsDiffRequestMsg.newBuilder()
                .setPath(request.getPath())
                .setVersionId1(request.getVersionId1())
                .setVersionId2(request.getVersionId2())
                .build()));
    }

    @Override
    public ListenableFuture<String> getContentsDiff(TenantId tenantId, String content1, String content2) {
        ContentsDiffGitRequest request = new ContentsDiffGitRequest(tenantId, content1, content2);
        return sendRequest(request, builder -> builder.setContentsDiffRequest(TransportProtos.ContentsDiffRequestMsg.newBuilder()
                .setContent1(content1)
                .setContent2(content2)));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ListenableFuture<EntityExportData> getEntity(TenantId tenantId, String versionId, List<CustomerId> hierarchy, EntityId entityId) {
        EntityContentGitRequest request = new EntityContentGitRequest(tenantId, versionId, entityId);
        registerAndSend(request, builder -> builder.setEntityContentRequest(EntityContentRequestMsg.newBuilder()
                        .setVersionId(versionId)
                        .setPath(getHierarchyPath(hierarchy))
                        .setEntityType(entityId.getEntityType().name())
                        .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                        .setEntityIdLSB(entityId.getId().getLeastSignificantBits())).build()
                , wrap(request.getFuture()));
        return request.getFuture();
    }

    @Override
    public ListenableFuture<List<EntityId>> getGroupEntityIds(TenantId tenantId, String versionId, List<CustomerId> ownerIds, EntityType type, EntityId externalId) {
        String path = getHierarchyPath(ownerIds) + "groups/" + type.name().toLowerCase() + "/" + externalId.getId() + "_entities.json";
        FileContentGitRequest request = new FileContentGitRequest(tenantId, versionId, path);
        registerAndSend(request, builder -> builder.setEntityContentRequest(EntityContentRequestMsg.newBuilder().setVersionId(versionId).setPath(path)).build()
                , wrap(request.getFuture()));
        return Futures.transform(request.getFuture(), data -> JacksonUtil.fromString(data, new TypeReference<>() {}), MoreExecutors.directExecutor());
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ListenableFuture<EntityExportData> getEntityGroup(TenantId tenantId, String versionId, List<CustomerId> hierarchy, EntityType groupType, EntityId groupId) {
        EntityContentGitRequest request = new EntityContentGitRequest(tenantId, versionId, groupId);
        registerAndSend(request, builder -> builder.setEntityContentRequest(EntityContentRequestMsg.newBuilder()
                        .setVersionId(versionId)
                        .setPath(getHierarchyPath(hierarchy) + "groups/")
                        .setEntityType(groupType.name())
                        .setEntityIdMSB(groupId.getId().getMostSignificantBits())
                        .setEntityIdLSB(groupId.getId().getLeastSignificantBits())).build()
                , wrap(request.getFuture()));
        return request.getFuture();
    }

    private <T> void registerAndSend(PendingGitRequest<T> request,
                                     Function<ToVersionControlServiceMsg.Builder, ToVersionControlServiceMsg> enrichFunction, TbQueueCallback callback) {
        registerAndSend(request, enrichFunction, null, callback);
    }

    private <T> void registerAndSend(PendingGitRequest<T> request,
                                     Function<ToVersionControlServiceMsg.Builder, ToVersionControlServiceMsg> enrichFunction, RepositorySettings settings, TbQueueCallback callback) {
        if (!request.getFuture().isDone()) {
            pendingRequestMap.putIfAbsent(request.getRequestId(), request);
            var requestBody = enrichFunction.apply(newRequestProto(request, settings));
            log.trace("[{}][{}] PUSHING request: {}", request.getTenantId(), request.getRequestId(), requestBody);
            clusterService.pushMsgToVersionControl(request.getTenantId(), requestBody, callback);
        } else {
            throw new RuntimeException("Future is already done!");
        }
    }

    private <T> ListenableFuture<T> sendRequest(PendingGitRequest<T> request, Consumer<ToVersionControlServiceMsg.Builder> enrichFunction) {
        registerAndSend(request, builder -> {
            enrichFunction.accept(builder);
            return builder.build();
        }, wrap(request.getFuture()));
        return request.getFuture();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ListenableFuture<List<EntityExportData>> getEntities(TenantId tenantId, String versionId, List<CustomerId> hierarchy, EntityType entityType, int offset, int limit) {
        EntitiesContentGitRequest request = new EntitiesContentGitRequest(tenantId, versionId, entityType);

        registerAndSend(request, builder -> builder.setEntitiesContentRequest(EntitiesContentRequestMsg.newBuilder()
                        .setVersionId(versionId)
                        .setPath(getHierarchyPath(hierarchy))
                        .setEntityType(entityType.name())
                        .setOffset(offset)
                        .setLimit(limit)
                ).build()
                , wrap(request.getFuture()));

        return request.getFuture();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public ListenableFuture<List<EntityExportData>> getEntities(TenantId tenantId, String versionId, List<CustomerId> hierarchy, EntityType entityType, List<UUID> ids) {
        EntitiesContentGitRequest request = new EntitiesContentGitRequest(tenantId, versionId, entityType);

        var idProtos = ids.stream().map(id -> TransportProtos.EntityIdProto.newBuilder()
                .setEntityIdMSB(id.getMostSignificantBits()).setEntityIdLSB(id.getLeastSignificantBits()).build()).collect(Collectors.toList());

        registerAndSend(request, builder -> builder.setEntitiesContentRequest(EntitiesContentRequestMsg.newBuilder()
                        .setVersionId(versionId)
                        .setPath(getHierarchyPath(hierarchy))
                        .setEntityType(entityType.name())
                        .addAllIds(idProtos)
                ).build()
                , wrap(request.getFuture()));

        return request.getFuture();
    }

    @Override
    public ListenableFuture<Void> initRepository(TenantId tenantId, RepositorySettings settings) {
        VoidGitRequest request = new VoidGitRequest(tenantId);

        registerAndSend(request, builder -> builder.setInitRepositoryRequest(GenericRepositoryRequestMsg.newBuilder().build()).build()
                , settings, wrap(request.getFuture()));

        return request.getFuture();
    }

    @Override
    public ListenableFuture<Void> testRepository(TenantId tenantId, RepositorySettings settings) {
        VoidGitRequest request = new VoidGitRequest(tenantId);

        registerAndSend(request, builder -> builder
                        .setTestRepositoryRequest(GenericRepositoryRequestMsg.newBuilder().build()).build()
                , settings, wrap(request.getFuture()));

        return request.getFuture();
    }

    @Override
    public ListenableFuture<Void> clearRepository(TenantId tenantId) {
        ClearRepositoryGitRequest request = new ClearRepositoryGitRequest(tenantId);

        registerAndSend(request, builder -> builder.setClearRepositoryRequest(GenericRepositoryRequestMsg.newBuilder().build()).build()
                , wrap(request.getFuture()));

        return request.getFuture();
    }

    @Override
    public void processResponse(VersionControlResponseMsg vcResponseMsg) {
        UUID requestId = new UUID(vcResponseMsg.getRequestIdMSB(), vcResponseMsg.getRequestIdLSB());
        PendingGitRequest<?> request = pendingRequestMap.get(requestId);
        if (request == null) {
            log.debug("[{}] received stale response: {}", requestId, vcResponseMsg);
            return;
        } else {
            log.debug("[{}] processing response: {}", requestId, vcResponseMsg);
        }
        var future = request.getFuture();
        if (!StringUtils.isEmpty(vcResponseMsg.getError())) {
            future.setException(new RuntimeException(vcResponseMsg.getError()));
        } else {
            if (vcResponseMsg.hasGenericResponse()) {
                future.set(null);
            } else if (vcResponseMsg.hasCommitResponse()) {
                var commitResponse = vcResponseMsg.getCommitResponse();
                var commitResult = new VersionCreationResult();
                if (commitResponse.getTs() > 0) {
                    commitResult.setVersion(new EntityVersion(commitResponse.getTs(), commitResponse.getCommitId(), commitResponse.getName(), commitResponse.getAuthor()));
                }
                commitResult.setAdded(commitResponse.getAdded());
                commitResult.setRemoved(commitResponse.getRemoved());
                commitResult.setModified(commitResponse.getModified());
                ((CommitGitRequest) request).getFuture().set(commitResult);
            } else if (vcResponseMsg.hasListBranchesResponse()) {
                var listBranchesResponse = vcResponseMsg.getListBranchesResponse();
                ((ListBranchesGitRequest) request).getFuture().set(listBranchesResponse.getBranchesList());
            } else if (vcResponseMsg.hasListEntitiesResponse()) {
                var listEntitiesResponse = vcResponseMsg.getListEntitiesResponse();
                ((ListEntitiesGitRequest) request).getFuture().set(
                        listEntitiesResponse.getEntitiesList().stream().map(this::getVersionedEntityInfo).collect(Collectors.toList()));
            } else if (vcResponseMsg.hasListVersionsResponse()) {
                var listVersionsResponse = vcResponseMsg.getListVersionsResponse();
                ((ListVersionsGitRequest) request).getFuture().set(toPageData(listVersionsResponse));
            } else if (vcResponseMsg.hasEntityContentResponse()) {
                var data = vcResponseMsg.getEntityContentResponse().getData();
                if(request instanceof EntityContentGitRequest){
                    ((EntityContentGitRequest) request).getFuture().set(toData(data));
                } else if (request instanceof FileContentGitRequest){
                    ((FileContentGitRequest) request).getFuture().set(data);
                } else {
                    throw new RuntimeException("Unsupported request: " + request.getClass());
                }
            } else if (vcResponseMsg.hasEntitiesContentResponse()) {
                var dataList = vcResponseMsg.getEntitiesContentResponse().getDataList();
                ((EntitiesContentGitRequest) request).getFuture()
                        .set(dataList.stream().map(this::toData).collect(Collectors.toList()));
            } else if (vcResponseMsg.hasVersionsDiffResponse()) {
                TransportProtos.VersionsDiffResponseMsg diffResponse = vcResponseMsg.getVersionsDiffResponse();
                List<EntityVersionsDiff> entityVersionsDiffList = diffResponse.getDiffList().stream()
                        .map(diff -> EntityVersionsDiff.builder()
                                .externalId(EntityIdFactory.getByTypeAndUuid(EntityType.valueOf(diff.getEntityType()),
                                        new UUID(diff.getEntityIdMSB(), diff.getEntityIdLSB())))
                                .entityDataAtVersion1(StringUtils.isNotEmpty(diff.getEntityDataAtVersion1()) ?
                                        toData(diff.getEntityDataAtVersion1()) : null)
                                .entityDataAtVersion2(StringUtils.isNotEmpty(diff.getEntityDataAtVersion2()) ?
                                        toData(diff.getEntityDataAtVersion2()) : null)
                                .rawDiff(diff.getRawDiff())
                                .build())
                        .collect(Collectors.toList());
                ((VersionsDiffGitRequest) request).getFuture().set(entityVersionsDiffList);
            } else if (vcResponseMsg.hasContentsDiffResponse()) {
                String diff = vcResponseMsg.getContentsDiffResponse().getDiff();
                ((ContentsDiffGitRequest) request).getFuture().set(diff);
            }
        }
    }

    private PageData<EntityVersion> toPageData(TransportProtos.ListVersionsResponseMsg listVersionsResponse) {
        var listVersions = listVersionsResponse.getVersionsList().stream().map(this::getEntityVersion).collect(Collectors.toList());
        return new PageData<>(listVersions, listVersionsResponse.getTotalPages(), listVersionsResponse.getTotalElements(), listVersionsResponse.getHasNext());
    }

    private EntityVersion getEntityVersion(TransportProtos.EntityVersionProto proto) {
        return new EntityVersion(proto.getTs(), proto.getId(), proto.getName(), proto.getAuthor());
    }

    private VersionedEntityInfo getVersionedEntityInfo(TransportProtos.VersionedEntityInfoProto proto) {
        return new VersionedEntityInfo(EntityIdFactory.getByTypeAndUuid(proto.getEntityType(), new UUID(proto.getEntityIdMSB(), proto.getEntityIdLSB())));
    }

    @SuppressWarnings("rawtypes")
    @SneakyThrows
    private EntityExportData toData(String data) {
        return JacksonUtil.fromString(data, EntityExportData.class);
    }

    private static <T> TbQueueCallback wrap(SettableFuture<T> future) {
        return new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
            }

            @Override
            public void onFailure(Throwable t) {
                future.setException(t);
            }
        };
    }

    private static <T> TbQueueCallback wrap(SettableFuture<T> future, T value) {
        return new TbQueueCallback() {
            @Override
            public void onSuccess(TbQueueMsgMetadata metadata) {
                future.set(value);
            }

            @Override
            public void onFailure(Throwable t) {
                future.setException(t);
            }
        };
    }

    private String getHierarchyPath(List<CustomerId> parents) {
        StringBuilder path = new StringBuilder();
        for(EntityId entityId: parents){
            path.append("hierarchy/").append(entityId.getId()).append("/");
        }
        return path.toString();
    }

    private static String getGroupEntitiesListPath(EntityType groupType, EntityId groupExternalId) {
        return "groups/" + groupType.name().toLowerCase() + "/" + groupExternalId + "_entities.json";
    }

    private static String getGroupPath(EntityGroup group) {
        return "groups/" + group.getType().name().toLowerCase() + "/" + (group.getExternalId() != null ? group.getExternalId() : group.getId()) + ".json";
    }

    private static String getRelativePath(EntityType entityType, EntityId entityId) {
        String path = entityType.name().toLowerCase();
        if (entityId != null) {
            path += "/" + entityId + ".json";
        }
        return path;
    }

    private static PrepareMsg getCommitPrepareMsg(User user, VersionCreateRequest request) {
        return PrepareMsg.newBuilder().setCommitMsg(request.getVersionName())
                .setBranchName(request.getBranch()).setAuthorName(getAuthorName(user)).setAuthorEmail(user.getEmail()).build();
    }

    private static String getAuthorName(User user) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.isNotBlank(user.getFirstName())) {
            parts.add(user.getFirstName());
        }
        if (StringUtils.isNotBlank(user.getLastName())) {
            parts.add(user.getLastName());
        }
        if (parts.isEmpty()) {
            parts.add(user.getName());
        }
        return String.join(" ", parts);
    }

    private ToVersionControlServiceMsg.Builder newRequestProto(PendingGitRequest<?> request, RepositorySettings settings) {
        var tenantId = request.getTenantId();
        var requestId = request.getRequestId();
        var builder = ToVersionControlServiceMsg.newBuilder()
                .setNodeId(serviceInfoProvider.getServiceId())
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                .setRequestIdMSB(requestId.getMostSignificantBits())
                .setRequestIdLSB(requestId.getLeastSignificantBits());
        RepositorySettings vcSettings = settings;
        if (vcSettings == null && request.requiresSettings()) {
            vcSettings = entitiesVersionControlService.getVersionControlSettings(tenantId);
        }
        if (vcSettings != null) {
            builder.setVcSettings(ByteString.copyFrom(encodingService.encode(vcSettings)));
        } else if (request.requiresSettings()) {
            throw new RuntimeException("No entity version control settings provisioned!");
        }
        return builder;
    }

    private CommitRequestMsg.Builder buildCommitRequest(CommitGitRequest commit) {
        return CommitRequestMsg.newBuilder().setTxId(commit.getTxId().toString());
    }
}

