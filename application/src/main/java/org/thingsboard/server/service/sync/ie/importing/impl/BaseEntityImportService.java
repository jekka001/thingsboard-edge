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
package org.thingsboard.server.service.sync.ie.importing.impl;

import com.google.api.client.util.Objects;
import com.google.common.util.concurrent.FutureCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ExportableEntity;
import org.thingsboard.server.common.data.HasCustomerId;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.JsonDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.sync.ie.AttributeExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.common.data.sync.ie.EntityImportResult;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.service.action.EntityActionService;
import org.thingsboard.server.service.entitiy.TbNotificationEntityService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.sync.ie.exporting.ExportableEntitiesService;
import org.thingsboard.server.service.sync.ie.importing.EntityImportService;
import org.thingsboard.server.service.sync.ie.importing.MissingEntityException;
import org.thingsboard.server.service.sync.vc.data.EntitiesImportCtx;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseEntityImportService<I extends EntityId, E extends ExportableEntity<I>, D extends EntityExportData<E>> implements EntityImportService<I, E, D> {

    @Autowired
    @Lazy
    protected ExportableEntitiesService exportableEntitiesService;
    @Autowired
    private RelationService relationService;
    @Autowired
    private TelemetrySubscriptionService tsSubService;
    @Autowired
    protected EntityActionService entityActionService;
    @Autowired
    protected TbClusterService clusterService;
    @Autowired
    protected TbNotificationEntityService entityNotificationService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public EntityImportResult<E> importEntity(EntitiesImportCtx ctx, D exportData) throws ThingsboardException {
        EntityImportResult<E> importResult = new EntityImportResult<>();
        IdProvider idProvider = new IdProvider(ctx, importResult);

        E entity = exportData.getEntity();
        E existingEntity = findExistingEntity(ctx, entity, idProvider);

        entity.setExternalId(entity.getId());

        setOwner(ctx.getTenantId(), entity, idProvider);
        if (existingEntity == null) {
            entity.setId(null);
        } else {
            entity.setId(existingEntity.getId());
            entity.setCreatedTime(existingEntity.getCreatedTime());
        }

        E savedEntity = prepareAndSave(ctx, entity, existingEntity, exportData, idProvider);

        importResult.setSavedEntity(savedEntity);
        importResult.setOldEntity(existingEntity);
        importResult.setEntityType(getEntityType());

        processAfterSaved(ctx, importResult, exportData, idProvider);

        ctx.putInternalId(exportData.getExternalId(), savedEntity.getId());

        return importResult;
    }

    @Override
    public EntityType getEntityType() {
        return null;
    }

    protected abstract void setOwner(TenantId tenantId, E entity, IdProvider idProvider);

    protected abstract E prepareAndSave(EntitiesImportCtx ctx, E entity, E oldEntity, D exportData, IdProvider idProvider);

    protected void processAfterSaved(EntitiesImportCtx ctx, EntityImportResult<E> importResult, D exportData, IdProvider idProvider) throws ThingsboardException {
        E savedEntity = importResult.getSavedEntity();
        E oldEntity = importResult.getOldEntity();

        importResult.addSendEventsCallback(() -> {
            onEntitySaved(ctx.getUser(), savedEntity, oldEntity);
        });

        if (ctx.isUpdateRelations() && exportData.getRelations() != null) {
            importRelations(ctx, exportData.getRelations(), importResult, idProvider);
        }
        if (ctx.isSaveAttributes() && exportData.getAttributes() != null) {
            importAttributes(ctx.getUser(), exportData.getAttributes(), importResult);
        }
    }

    private void importRelations(EntitiesImportCtx ctx, List<EntityRelation> relations, EntityImportResult<E> importResult, IdProvider idProvider) {
        var tenantId = ctx.getTenantId();
        E entity = importResult.getSavedEntity();
        importResult.addSaveReferencesCallback(() -> {
            for (EntityRelation relation : relations) {
                if (!relation.getTo().equals(entity.getId())) {
                    relation.setTo(idProvider.getInternalId(relation.getTo()));
                }
                if (!relation.getFrom().equals(entity.getId())) {
                    relation.setFrom(idProvider.getInternalId(relation.getFrom()));
                }
            }

            Map<EntityRelation, EntityRelation> relationsMap = new LinkedHashMap<>();
            relations.forEach(r -> relationsMap.put(r, r));

            if (importResult.getOldEntity() != null) {
                List<EntityRelation> existingRelations = new ArrayList<>();
                existingRelations.addAll(relationService.findByTo(tenantId, entity.getId(), RelationTypeGroup.COMMON));
                existingRelations.addAll(relationService.findByFrom(tenantId, entity.getId(), RelationTypeGroup.COMMON));

                for (EntityRelation existingRelation : existingRelations) {
                    EntityRelation relation = relationsMap.get(existingRelation);
                    if (relation == null) {
                        relationService.deleteRelation(tenantId, existingRelation);
                        importResult.addSendEventsCallback(() -> {
                            entityActionService.logEntityAction(ctx.getUser(), existingRelation.getFrom(), null, null,
                                    ActionType.RELATION_DELETED, null, existingRelation);
                            entityActionService.logEntityAction(ctx.getUser(), existingRelation.getTo(), null, null,
                                    ActionType.RELATION_DELETED, null, existingRelation);
                        });
                    } else if (Objects.equal(relation.getAdditionalInfo(), existingRelation.getAdditionalInfo())) {
                        relationsMap.remove(relation);
                    }
                }
            }

            ctx.addRelations(relationsMap.values());
        });
    }

    private void importAttributes(SecurityUser user, Map<String, List<AttributeExportData>> attributes, EntityImportResult<E> importResult) {
        E entity = importResult.getSavedEntity();
        importResult.addSaveReferencesCallback(() -> {
            attributes.forEach((scope, attributesExportData) -> {
                List<AttributeKvEntry> attributeKvEntries = attributesExportData.stream()
                        .map(attributeExportData -> {
                            KvEntry kvEntry;
                            String key = attributeExportData.getKey();
                            if (attributeExportData.getStrValue() != null) {
                                kvEntry = new StringDataEntry(key, attributeExportData.getStrValue());
                            } else if (attributeExportData.getBooleanValue() != null) {
                                kvEntry = new BooleanDataEntry(key, attributeExportData.getBooleanValue());
                            } else if (attributeExportData.getDoubleValue() != null) {
                                kvEntry = new DoubleDataEntry(key, attributeExportData.getDoubleValue());
                            } else if (attributeExportData.getLongValue() != null) {
                                kvEntry = new LongDataEntry(key, attributeExportData.getLongValue());
                            } else if (attributeExportData.getJsonValue() != null) {
                                kvEntry = new JsonDataEntry(key, attributeExportData.getJsonValue());
                            } else {
                                throw new IllegalArgumentException("Invalid attribute export data");
                            }
                            return new BaseAttributeKvEntry(kvEntry, attributeExportData.getLastUpdateTs());
                        })
                        .collect(Collectors.toList());
                // fixme: attributes are saved outside the transaction
                tsSubService.saveAndNotify(user.getTenantId(), entity.getId(), scope, attributeKvEntries, new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(@Nullable Void unused) {
                    }

                    @Override
                    public void onFailure(Throwable thr) {
                        log.error("Failed to import attributes for {} {}", entity.getId().getEntityType(), entity.getId(), thr);
                    }
                });
            });
        });
    }

    protected void onEntitySaved(SecurityUser user, E savedEntity, E oldEntity) throws ThingsboardException {
        entityActionService.logEntityAction(user, savedEntity.getId(), savedEntity,
                savedEntity instanceof HasCustomerId ? ((HasCustomerId) savedEntity).getCustomerId() : user.getCustomerId(),
                oldEntity == null ? ActionType.ADDED : ActionType.UPDATED, null);
    }


    @SuppressWarnings("unchecked")
    protected E findExistingEntity(EntitiesImportCtx ctx, E entity, IdProvider idProvider) {
        return (E) Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndExternalId(ctx.getTenantId(), entity.getId()))
                .or(() -> Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndId(ctx.getTenantId(), entity.getId())))
                .or(() -> {
                    if (ctx.isFindExistingByName()) {
                        return Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndName(ctx.getTenantId(), getEntityType(), entity.getName()));
                    } else {
                        return Optional.empty();
                    }
                })
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    protected <ID extends EntityId> HasId<ID> findInternalEntity(TenantId tenantId, ID externalId) {
        return (HasId<ID>) Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndExternalId(tenantId, externalId))
                .or(() -> Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndId(tenantId, externalId)))
                .orElseThrow(() -> new MissingEntityException(externalId));
    }


    @SuppressWarnings("unchecked")
    @RequiredArgsConstructor
    protected class IdProvider {
        private final EntitiesImportCtx ctx;
        private final EntityImportResult<E> importResult;

        public <ID extends EntityId> ID getInternalId(ID externalId) {
            return getInternalId(externalId, true);
        }

        public <ID extends EntityId> ID getInternalId(ID externalId, boolean throwExceptionIfNotFound) {
            if (externalId == null || externalId.isNullUid()) return null;

            EntityId localId = ctx.getInternalId(externalId);
            if (localId != null) {
                return (ID) localId;
            }

            HasId<ID> entity;
            try {
                entity = findInternalEntity(ctx.getTenantId(), externalId);
            } catch (Exception e) {
                if (throwExceptionIfNotFound) {
                    throw e;
                } else {
                    importResult.setUpdatedAllExternalIds(false);
                    return null;
                }
            }
            ctx.putInternalId(externalId, entity.getId());
            return entity.getId();
        }

        public Optional<EntityId> getInternalIdByUuid(UUID externalUuid, boolean fetchAllUUIDs, Set<EntityType> hints) {
            if (externalUuid.equals(EntityId.NULL_UUID)) return Optional.empty();

            for (EntityType entityType : EntityType.values()) {
                Optional<EntityId> externalIdOpt = buildEntityId(entityType, externalUuid);
                if (!externalIdOpt.isPresent()) {
                    continue;
                }
                EntityId internalId = ctx.getInternalId(externalIdOpt.get());
                if (internalId != null) {
                    return Optional.of(internalId);
                }
            }

            if (fetchAllUUIDs) {
                for (EntityType entityType : hints) {
                    Optional<EntityId> internalId = lookupInDb(externalUuid, entityType);
                    if (internalId.isPresent()) return internalId;
                }
                for (EntityType entityType : EntityType.values()) {
                    if (hints.contains(entityType)) {
                        continue;
                    }
                    Optional<EntityId> internalId = lookupInDb(externalUuid, entityType);
                    if (internalId.isPresent()) return internalId;
                }
            }

            importResult.setUpdatedAllExternalIds(false);
            return Optional.empty();
        }

        private Optional<EntityId> lookupInDb(UUID externalUuid, EntityType entityType) {
            Optional<EntityId> externalIdOpt = buildEntityId(entityType, externalUuid);
            if (externalIdOpt.isEmpty() || ctx.isNotFound(externalIdOpt.get())) {
                return Optional.empty();
            }
            EntityId internalId = getInternalId(externalIdOpt.get(), false);
            if (internalId != null) {
                return Optional.of(internalId);
            } else {
                ctx.registerNotFound(externalIdOpt.get());
            }
            return Optional.empty();
        }

        private Optional<EntityId> buildEntityId(EntityType entityType, UUID externalUuid) {
            try {
                return Optional.of(EntityIdFactory.getByTypeAndUuid(entityType, externalUuid));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

    }

}
