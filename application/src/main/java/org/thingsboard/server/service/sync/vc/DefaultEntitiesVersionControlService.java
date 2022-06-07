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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.sync.ThrowingRunnable;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportSettings;
import org.thingsboard.server.common.data.sync.ie.EntityImportResult;
import org.thingsboard.server.common.data.sync.ie.EntityImportSettings;
import org.thingsboard.server.common.data.sync.vc.*;
import org.thingsboard.server.common.data.sync.vc.request.create.*;
import org.thingsboard.server.common.data.sync.vc.request.load.*;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.exception.DeviceCredentialsValidationException;
import org.thingsboard.server.dao.owner.OwnerService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.TbNotificationEntityService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.sync.ie.EntitiesExportImportService;
import org.thingsboard.server.service.sync.ie.exporting.ExportableEntitiesService;
import org.thingsboard.server.service.sync.ie.importing.MissingEntityException;
import org.thingsboard.server.service.sync.vc.autocommit.TbAutoCommitSettingsService;
import org.thingsboard.server.service.sync.vc.data.CommitGitRequest;
import org.thingsboard.server.service.sync.vc.repository.TbRepositorySettingsService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.google.common.util.concurrent.Futures.transform;
import static com.google.common.util.concurrent.Futures.transformAsync;

@Service
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class DefaultEntitiesVersionControlService implements EntitiesVersionControlService {

    private final TbRepositorySettingsService repositorySettingsService;
    private final TbAutoCommitSettingsService autoCommitSettingsService;
    private final GitVersionControlQueueService gitServiceQueue;
    private final EntitiesExportImportService exportImportService;
    private final ExportableEntitiesService exportableEntitiesService;
    private final TbNotificationEntityService entityNotificationService;
    private final TransactionTemplate transactionTemplate;
    private final CustomerService customerService;
    private final OwnerService ownersService;

    private ListeningExecutorService executor;

    @Value("${vc.thread_pool_size:4}")
    private int threadPoolSize;

    @PostConstruct
    public void init() {
        executor = MoreExecutors.listeningDecorator(ThingsBoardExecutors.newWorkStealingPool(threadPoolSize, DefaultEntitiesVersionControlService.class));
    }

    @PreDestroy
    public void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public ListenableFuture<VersionCreationResult> saveEntitiesVersion(SecurityUser user, VersionCreateRequest request) throws Exception {
        var pendingCommit = gitServiceQueue.prepareCommit(user, request);

        return transformAsync(pendingCommit, commit -> {
            List<ListenableFuture<Void>> gitFutures = new ArrayList<>();
            switch (request.getType()) {
                case SINGLE_ENTITY: {
                    handleSingleEntityRequest(user, commit, gitFutures, (SingleEntityVersionCreateRequest) request);
                    break;
                }
                case COMPLEX: {
                    handleComplexRequest(user, commit, gitFutures, (ComplexVersionCreateRequest) request);
                    break;
                }
            }
            return transformAsync(Futures.allAsList(gitFutures), success -> gitServiceQueue.push(commit), executor);
        }, executor);
    }

    private void handleSingleEntityRequest(SecurityUser user, CommitGitRequest commit, List<ListenableFuture<Void>> gitFutures, SingleEntityVersionCreateRequest vcr) throws Exception {
        EntityId entityId = vcr.getEntityId();
        var config = vcr.getConfig();
        EntityExportSettings exportSettings = EntityExportSettings.builder()
                .exportRelations(config.isSaveRelations())
                .exportAttributes(config.isSaveAttributes())
                .exportCredentials(config.isSaveCredentials())
                .build();
        if (EntityType.ENTITY_GROUP.equals(entityId.getEntityType())) {

        } else {
            EntityExportData<ExportableEntity<EntityId>> entityData = exportImportService.exportEntity(user, entityId, exportSettings);
            ExportableEntity<EntityId> entity = entityData.getEntity();
            if (entity instanceof HasOwnerId) {
                //ownersService.getOwners returns LinkedHashSet;
                List<CustomerId> customerIds = getCustomerExternalIds(user.getTenantId(), entityId, (HasOwnerId) entity);
                List<CustomerId> hierarchy = new ArrayList<>(customerIds.size());
                for (CustomerId ownerId : customerIds) {
                    EntityExportData<ExportableEntity<EntityId>> ownerData = exportImportService.exportEntity(user, ownerId, exportSettings);
                    gitFutures.add(gitServiceQueue.addToCommit(commit, new ArrayList<>(hierarchy), ownerData));
                    hierarchy.add(ownerId);
                }
                gitFutures.add(gitServiceQueue.addToCommit(commit, hierarchy, entityData));
            } else {
                gitFutures.add(gitServiceQueue.addToCommit(commit, entityData));
            }
        }
    }

    private void handleComplexRequest(SecurityUser user, CommitGitRequest commit, List<ListenableFuture<Void>> gitFutures, ComplexVersionCreateRequest versionCreateRequest) {
        versionCreateRequest.getEntityTypes().forEach((entityType, config) -> {
            if (ObjectUtils.defaultIfNull(config.getSyncStrategy(), versionCreateRequest.getSyncStrategy()) == SyncStrategy.OVERWRITE) {
                gitFutures.add(gitServiceQueue.deleteAll(commit, entityType));
            }

            if (config.isAllEntities()) {
                DaoUtil.processInBatches(pageLink -> exportableEntitiesService.findEntitiesByTenantId(user.getTenantId(), entityType, pageLink)
                        , 100, entity -> {
                            try {
                                gitFutures.add(saveEntityData(user, commit, entity.getId(), config));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            } else {
                for (UUID entityId : config.getEntityIds()) {
                    try {
                        gitFutures.add(saveEntityData(user, commit, EntityIdFactory.getByTypeAndUuid(entityType, entityId), config));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private ListenableFuture<Void> saveEntityData(SecurityUser user, CommitGitRequest commit, EntityId entityId, VersionCreateConfig config) throws Exception {
        EntityExportData<ExportableEntity<EntityId>> entityData = exportImportService.exportEntity(user, entityId, EntityExportSettings.builder()
                .exportRelations(config.isSaveRelations())
                .exportAttributes(config.isSaveAttributes())
                .exportCredentials(config.isSaveCredentials())
                .build());
        return gitServiceQueue.addToCommit(commit, entityData);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listEntityVersions(TenantId tenantId, String branch, EntityId externalId, EntityId internalId, PageLink pageLink) throws Exception {
        return gitServiceQueue.listVersions(tenantId, branch, getCustomerExternalIds(tenantId, internalId != null ? internalId : externalId), externalId, pageLink);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listEntityTypeVersions(TenantId tenantId, String branch, EntityType entityType, PageLink pageLink) throws Exception {
        return gitServiceQueue.listVersions(tenantId, branch, entityType, pageLink);
    }

    @Override
    public ListenableFuture<PageData<EntityVersion>> listVersions(TenantId tenantId, String branch, PageLink pageLink) throws Exception {
        return gitServiceQueue.listVersions(tenantId, branch, pageLink);
    }

    @Override
    public ListenableFuture<List<VersionedEntityInfo>> listEntitiesAtVersion(TenantId tenantId, String branch, String versionId, EntityType entityType) throws Exception {
        return gitServiceQueue.listEntitiesAtVersion(tenantId, branch, versionId, entityType);
    }

    @Override
    public ListenableFuture<List<VersionedEntityInfo>> listAllEntitiesAtVersion(TenantId tenantId, String branch, String versionId) throws Exception {
        return gitServiceQueue.listEntitiesAtVersion(tenantId, branch, versionId);
    }

    @SuppressWarnings({"UnstableApiUsage", "rawtypes"})
    @Override
    public ListenableFuture<VersionLoadResult> loadEntitiesVersion(SecurityUser user, VersionLoadRequest request) throws Exception {
        switch (request.getType()) {
            case SINGLE_ENTITY: {
                SingleEntityVersionLoadRequest versionLoadRequest = (SingleEntityVersionLoadRequest) request;
                EntityId internalId = versionLoadRequest.getInternalEntityId();
                List<CustomerId> ownerIds = internalId != null ? getCustomerExternalIds(user.getTenantId(), internalId) : Collections.emptyList();
                VersionLoadConfig config = versionLoadRequest.getConfig();
                ListenableFuture<EntityExportData> future = gitServiceQueue.getEntity(user.getTenantId(), request.getVersionId(), ownerIds, versionLoadRequest.getExternalEntityId());
                return Futures.transform(future, entityData -> doInTemplate(status -> loadSingleEntity(user, config, entityData)), executor);
            }
            case ENTITY_TYPE: {
                EntityTypeVersionLoadRequest versionLoadRequest = (EntityTypeVersionLoadRequest) request;
                return executor.submit(() -> doInTemplate(status -> loadMultipleEntities(user, versionLoadRequest)));
            }
            default:
                throw new IllegalArgumentException("Unsupported version load request");
        }
    }

    private List<CustomerId> getCustomerExternalIds(TenantId tenantId, EntityId entityId) {
        return getCustomerExternalIds(tenantId, entityId, null);
    }

    private List<CustomerId> getCustomerExternalIds(TenantId tenantId, EntityId entityId, HasOwnerId entity) {
        Set<EntityId> ownersSet;
        if(entity != null){
            ownersSet = ownersService.getOwners(tenantId, entityId, entity);
        } else {
            ownersSet = ownersService.getOwners(tenantId, entityId);
        }
        List<EntityId> owners = new ArrayList<>(ownersSet);
        if(owners.size() == 1){
            return Collections.emptyList();
        } else {
            Collections.reverse(owners);
            List<CustomerId> result = new ArrayList<>(Math.max(1, owners.size() - 1));
            for(EntityId ownerId: owners){
                if(EntityType.TENANT.equals(ownerId.getEntityType())){
                    continue;
                }
                CustomerId internalId = new CustomerId(ownerId.getId());
                Customer customer = customerService.findCustomerById(tenantId, internalId);
                if(customer == null) {
                    throw new RuntimeException("Failed to fetch customer with id: " + internalId);
                }
                result.add(customer.getExternalId() != null ? customer.getExternalId() : internalId);
            }
            return result;
        }
    }

    private VersionLoadResult doInTemplate(TransactionCallback<VersionLoadResult> result) {
        try {
            return transactionTemplate.execute(result);
        } catch (LoadEntityException e) {
            return onError(e.getData(), e.getCause());
        }
    }

    private VersionLoadResult loadSingleEntity(SecurityUser user, VersionLoadConfig config, EntityExportData entityData) {
        try {
            EntityImportResult<?> importResult = exportImportService.importEntity(user, entityData,
                    EntityImportSettings.builder()
                            .updateRelations(config.isLoadRelations())
                            .saveAttributes(config.isLoadAttributes())
                            .saveCredentials(config.isLoadCredentials())
                            .findExistingByName(false)
                            .build(), true, true);
            return VersionLoadResult.success(EntityTypeLoadResult.builder()
                    .entityType(importResult.getEntityType())
                    .created(importResult.getOldEntity() == null ? 1 : 0)
                    .updated(importResult.getOldEntity() != null ? 1 : 0)
                    .deleted(0)
                    .build());
        } catch (Exception e) {
            throw new LoadEntityException(entityData, e);
        }
    }

    private VersionLoadResult loadMultipleEntities(SecurityUser user, EntityTypeVersionLoadRequest request) {
        Map<EntityType, EntityTypeLoadResult> results = new HashMap<>();
        Map<EntityType, Set<EntityId>> importedEntities = new HashMap<>();
        Map<EntityId, EntityImportSettings> toReimport = new HashMap<>();
        List<ThrowingRunnable> saveReferencesCallbacks = new ArrayList<>();
        List<ThrowingRunnable> sendEventsCallbacks = new ArrayList<>();

        List<EntityType> entityTypes = request.getEntityTypes().keySet().stream()
                .sorted(exportImportService.getEntityTypeComparatorForImport()).collect(Collectors.toList());
        for (EntityType entityType : entityTypes) {
            EntityTypeVersionLoadConfig config = request.getEntityTypes().get(entityType);
            AtomicInteger created = new AtomicInteger();
            AtomicInteger updated = new AtomicInteger();

            int limit = 100;
            int offset = 0;
            List<EntityExportData> entityDataList;
            do {
                try {
                    entityDataList = gitServiceQueue.getEntities(user.getTenantId(), request.getVersionId(), entityType, offset, limit).get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
                EntityImportSettings importSettings = EntityImportSettings.builder()
                        .updateRelations(config.isLoadRelations())
                        .saveAttributes(config.isLoadAttributes())
                        .findExistingByName(config.isFindExistingEntityByName())
                        .build();
                for (EntityExportData entityData : entityDataList) {
                    EntityImportResult<?> importResult;
                    try {
                        importResult = exportImportService.importEntity(user, entityData,
                                importSettings, false, false);
                    } catch (Exception e) {
                        throw new LoadEntityException(entityData, e);
                    }
                    if (importResult.getUpdatedAllExternalIds() != null && !importResult.getUpdatedAllExternalIds()) {
                        toReimport.put(entityData.getEntity().getExternalId(), importSettings);
                        continue;
                    }

                    if (importResult.getOldEntity() == null) created.incrementAndGet();
                    else updated.incrementAndGet();
                    saveReferencesCallbacks.add(importResult.getSaveReferencesCallback());
                    sendEventsCallbacks.add(importResult.getSendEventsCallback());

                    importedEntities.computeIfAbsent(entityType, t -> new HashSet<>())
                            .add(importResult.getSavedEntity().getId());
                }
                offset += limit;
            } while (entityDataList.size() == limit);
            results.put(entityType, EntityTypeLoadResult.builder()
                    .entityType(entityType)
                    .created(created.get())
                    .updated(updated.get())
                    .build());
        }

        toReimport.forEach((externalId, importSettings) -> {
            try {
                EntityExportData entityData = gitServiceQueue.getEntity(user.getTenantId(), request.getVersionId(), Collections.emptyList(), externalId).get();
                importSettings.setResetExternalIdsOfAnotherTenant(true);
                EntityImportResult<?> importResult = exportImportService.importEntity(user, entityData,
                        importSettings, false, false);

                EntityTypeLoadResult stats = results.get(externalId.getEntityType());
                if (importResult.getOldEntity() == null) stats.setCreated(stats.getCreated() + 1);
                else stats.setUpdated(stats.getUpdated() + 1);
                saveReferencesCallbacks.add(importResult.getSaveReferencesCallback());
                sendEventsCallbacks.add(importResult.getSendEventsCallback());
                importedEntities.computeIfAbsent(externalId.getEntityType(), t -> new HashSet<>())
                        .add(importResult.getSavedEntity().getId());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        request.getEntityTypes().keySet().stream()
                .filter(entityType -> request.getEntityTypes().get(entityType).isRemoveOtherEntities())
                .sorted(exportImportService.getEntityTypeComparatorForImport().reversed())
                .forEach(entityType -> {
                    DaoUtil.processInBatches(pageLink -> {
                        return exportableEntitiesService.findEntitiesByTenantId(user.getTenantId(), entityType, pageLink);
                    }, 100, entity -> {
                        if (importedEntities.get(entityType) == null || !importedEntities.get(entityType).contains(entity.getId())) {
                            try {
                                exportableEntitiesService.checkPermission(user, entity, entityType, Operation.DELETE);
                            } catch (ThingsboardException e) {
                                throw new RuntimeException(e);
                            }
                            exportableEntitiesService.deleteByTenantIdAndId(user.getTenantId(), entity.getId());

                            sendEventsCallbacks.add(() -> {
                                entityNotificationService.notifyDeleteEntity(user.getTenantId(), entity.getId(),
                                        entity, null, ActionType.DELETED, null, user);
                            });
                            EntityTypeLoadResult result = results.get(entityType);
                            result.setDeleted(result.getDeleted() + 1);
                        }
                    });
                });

        for (ThrowingRunnable saveReferencesCallback : saveReferencesCallbacks) {
            try {
                saveReferencesCallback.run();
            } catch (ThingsboardException e) {
                throw new RuntimeException(e);
            }
        }
        for (ThrowingRunnable sendEventsCallback : sendEventsCallbacks) {
            try {
                sendEventsCallback.run();
            } catch (Exception e) {
                log.error("Failed to send events for entity", e);
            }
        }
        return VersionLoadResult.success(new ArrayList<>(results.values()));
    }

    private VersionLoadResult onError(EntityExportData<?> entityData, Throwable e) {
        return analyze(e, entityData).orElseThrow(() -> new RuntimeException(e));
    }

    private Optional<VersionLoadResult> analyze(Throwable e, EntityExportData<?> entityData) {
        if (e == null) {
            return Optional.empty();
        } else {
            if (e instanceof DeviceCredentialsValidationException) {
                return Optional.of(VersionLoadResult.error(EntityLoadError.credentialsError(entityData.getExternalId())));
            } else if (e instanceof MissingEntityException) {
                return Optional.of(VersionLoadResult.error(EntityLoadError.referenceEntityError(entityData.getExternalId(), ((MissingEntityException) e).getEntityId())));
            } else {
                return analyze(e.getCause(), entityData);
            }
        }
    }

    @Override
    public ListenableFuture<EntityDataDiff> compareEntityDataToVersion(SecurityUser user, String branch, EntityId entityId, String versionId) throws Exception {
        HasId<EntityId> entity = exportableEntitiesService.findEntityByTenantIdAndId(user.getTenantId(), entityId);
        if (!(entity instanceof ExportableEntity)) throw new IllegalArgumentException("Unsupported entity type");

        EntityId externalId = ((ExportableEntity<EntityId>) entity).getExternalId();
        if (externalId == null) externalId = entityId;

        return transformAsync(gitServiceQueue.getEntity(user.getTenantId(), versionId, getCustomerExternalIds(user.getTenantId(), entityId), externalId),
                otherVersion -> {
                    EntityExportData<?> currentVersion = exportImportService.exportEntity(user, entityId, EntityExportSettings.builder()
                            .exportRelations(otherVersion.hasRelations())
                            .exportAttributes(otherVersion.hasAttributes())
                            .exportCredentials(otherVersion.hasCredentials())
                            .build());
                    return transform(gitServiceQueue.getContentsDiff(user.getTenantId(),
                                    JacksonUtil.toPrettyString(currentVersion.sort()),
                                    JacksonUtil.toPrettyString(otherVersion.sort())),
                            rawDiff -> new EntityDataDiff(currentVersion, otherVersion, rawDiff), MoreExecutors.directExecutor());
                }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<EntityDataInfo> getEntityDataInfo(SecurityUser user, EntityId externalId, EntityId internalId, String versionId) {
        List<CustomerId> customerIds = internalId != null ? getCustomerExternalIds(user.getTenantId(), internalId) : Collections.emptyList();
        return Futures.transform(gitServiceQueue.getEntity(user.getTenantId(), versionId, customerIds, externalId),
                entity -> new EntityDataInfo(entity.hasRelations(), entity.hasAttributes(), entity.hasCredentials(), entity.hasPermissions(), entity.hasGroupEntities()), MoreExecutors.directExecutor());
    }


    @Override
    public ListenableFuture<List<String>> listBranches(TenantId tenantId) throws Exception {
        return gitServiceQueue.listBranches(tenantId);
    }

    @Override
    public RepositorySettings getVersionControlSettings(TenantId tenantId) {
        return repositorySettingsService.get(tenantId);
    }

    @Override
    public ListenableFuture<RepositorySettings> saveVersionControlSettings(TenantId tenantId, RepositorySettings versionControlSettings) {
        var restoredSettings = this.repositorySettingsService.restore(tenantId, versionControlSettings);
        try {
            var future = gitServiceQueue.initRepository(tenantId, restoredSettings);
            return Futures.transform(future, f -> repositorySettingsService.save(tenantId, restoredSettings), MoreExecutors.directExecutor());
        } catch (Exception e) {
            log.debug("{} Failed to init repository: {}", tenantId, versionControlSettings, e);
            throw new RuntimeException("Failed to init repository!", e);
        }
    }

    @Override
    public ListenableFuture<Void> deleteVersionControlSettings(TenantId tenantId) throws Exception {
        if (repositorySettingsService.delete(tenantId)) {
            return gitServiceQueue.clearRepository(tenantId);
        } else {
            return Futures.immediateFuture(null);
        }
    }

    @Override
    public ListenableFuture<Void> checkVersionControlAccess(TenantId tenantId, RepositorySettings settings) throws ThingsboardException {
        settings = this.repositorySettingsService.restore(tenantId, settings);
        try {
            return gitServiceQueue.testRepository(tenantId, settings);
        } catch (Exception e) {
            throw new ThingsboardException(String.format("Unable to access repository: %s", getCauseMessage(e)),
                    ThingsboardErrorCode.GENERAL);
        }
    }

    @Override
    public ListenableFuture<VersionCreationResult> autoCommit(SecurityUser user, EntityId entityId) throws Exception {
        var repositorySettings = repositorySettingsService.get(user.getTenantId());
        if (repositorySettings == null) {
            return Futures.immediateFuture(null);
        }
        var autoCommitSettings = autoCommitSettingsService.get(user.getTenantId());
        if (autoCommitSettings == null) {
            return Futures.immediateFuture(null);
        }
        var entityType = entityId.getEntityType();
        AutoVersionCreateConfig autoCommitConfig = autoCommitSettings.get(entityType);
        if (autoCommitConfig == null) {
            return Futures.immediateFuture(null);
        }
        SingleEntityVersionCreateRequest vcr = new SingleEntityVersionCreateRequest();
        var autoCommitBranchName = autoCommitConfig.getBranch();
        if (StringUtils.isEmpty(autoCommitBranchName)) {
            autoCommitBranchName = StringUtils.isNotEmpty(repositorySettings.getDefaultBranch()) ? repositorySettings.getDefaultBranch() : "auto-commits";
        }
        vcr.setBranch(autoCommitBranchName);
        vcr.setVersionName("auto-commit at " + Instant.ofEpochSecond(System.currentTimeMillis() / 1000));
        vcr.setEntityId(entityId);
        vcr.setConfig(autoCommitConfig);
        return saveEntitiesVersion(user, vcr);
    }

    @Override
    public ListenableFuture<VersionCreationResult> autoCommit(SecurityUser user, EntityType entityType, List<UUID> entityIds) throws Exception {
        var repositorySettings = repositorySettingsService.get(user.getTenantId());
        if (repositorySettings == null) {
            return Futures.immediateFuture(null);
        }
        var autoCommitSettings = autoCommitSettingsService.get(user.getTenantId());
        if (autoCommitSettings == null) {
            return Futures.immediateFuture(null);
        }
        AutoVersionCreateConfig autoCommitConfig = autoCommitSettings.get(entityType);
        if (autoCommitConfig == null) {
            return Futures.immediateFuture(null);
        }
        var autoCommitBranchName = autoCommitConfig.getBranch();
        if (StringUtils.isEmpty(autoCommitBranchName)) {
            autoCommitBranchName = StringUtils.isNotEmpty(repositorySettings.getDefaultBranch()) ? repositorySettings.getDefaultBranch() : "auto-commits";
        }
        ComplexVersionCreateRequest vcr = new ComplexVersionCreateRequest();
        vcr.setBranch(autoCommitBranchName);
        vcr.setVersionName("auto-commit at " + Instant.ofEpochSecond(System.currentTimeMillis() / 1000));
        vcr.setSyncStrategy(SyncStrategy.MERGE);

        EntityTypeVersionCreateConfig vcrConfig = new EntityTypeVersionCreateConfig();
        vcrConfig.setEntityIds(entityIds);
        vcr.setEntityTypes(Collections.singletonMap(entityType, vcrConfig));
        return saveEntitiesVersion(user, vcr);
    }

    private String getCauseMessage(Exception e) {
        String message;
        if (e.getCause() != null && StringUtils.isNotEmpty(e.getCause().getMessage())) {
            message = e.getCause().getMessage();
        } else {
            message = e.getMessage();
        }
        return message;
    }

}
