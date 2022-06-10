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
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.sync.ie.AttributeExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.common.data.sync.ie.EntityImportResult;
import org.thingsboard.server.common.data.sync.ie.EntityImportSettings;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.service.action.EntityActionService;
import org.thingsboard.server.service.entitiy.TbNotificationEntityService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.sync.ie.exporting.ExportableEntitiesService;
import org.thingsboard.server.service.sync.ie.importing.EntityImportService;
import org.thingsboard.server.service.sync.ie.importing.MissingEntityException;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseEntityImportService<I extends EntityId, E extends ExportableEntity<I>, D extends EntityExportData<E>> implements EntityImportService<I, E, D> {

    @Autowired @Lazy
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
    public EntityImportResult<E> importEntity(SecurityUser user, D exportData, EntityImportSettings importSettings) throws ThingsboardException {
        E entity = exportData.getEntity();
        E existingEntity = findExistingEntity(user.getTenantId(), entity, importSettings);

        entity.setExternalId(entity.getId());

        EntityImportResult<E> importResult = new EntityImportResult<>();
        IdProvider idProvider = new IdProvider(user, importSettings, importResult);
        setOwner(user.getTenantId(), entity, idProvider);
        if (existingEntity == null) {
            entity.setId(null);
        } else {
            entity.setId(existingEntity.getId());
            entity.setCreatedTime(existingEntity.getCreatedTime());
        }

        E savedEntity = prepareAndSave(user.getTenantId(), entity, existingEntity, exportData, idProvider, importSettings);

        importResult.setSavedEntity(savedEntity);
        importResult.setOldEntity(existingEntity);
        importResult.setEntityType(getEntityType());

        processAfterSaved(user, importResult, exportData, idProvider, importSettings);

        return importResult;
    }

    protected abstract void setOwner(TenantId tenantId, E entity, IdProvider idProvider);

    protected abstract E prepareAndSave(TenantId tenantId, E entity, E oldEntity, D exportData, IdProvider idProvider, EntityImportSettings importSettings);

    protected void processAfterSaved(SecurityUser user, EntityImportResult<E> importResult, D exportData,
                                     IdProvider idProvider, EntityImportSettings importSettings) throws ThingsboardException {
        E savedEntity = importResult.getSavedEntity();
        E oldEntity = importResult.getOldEntity();

        importResult.addSendEventsCallback(() -> {
            onEntitySaved(user, savedEntity, oldEntity);
        });

        if (importSettings.isUpdateRelations() && exportData.getRelations() != null) {
            importRelations(user, exportData.getRelations(), importResult);
        }
        if (importSettings.isSaveAttributes() && exportData.getAttributes() != null) {
            importAttributes(user, exportData.getAttributes(), importResult);
        }
    }

    private void importRelations(SecurityUser user, List<EntityRelation> relations, EntityImportResult<E> importResult) {
        E entity = importResult.getSavedEntity();
        importResult.addSaveReferencesCallback(() -> {
            for (EntityRelation relation : relations) {
                if (!relation.getTo().equals(entity.getId())) {
                    HasId<EntityId> to = findInternalEntity(user.getTenantId(), relation.getTo());
                    exportableEntitiesService.checkPermission(user, to, to.getId().getEntityType(), Operation.WRITE);
                    relation.setTo(to.getId());
                }
                if (!relation.getFrom().equals(entity.getId())) {
                    HasId<EntityId> from = findInternalEntity(user.getTenantId(), relation.getFrom());
                    exportableEntitiesService.checkPermission(user, from, from.getId().getEntityType(), Operation.WRITE);
                    relation.setFrom(from.getId());
                }
            }

            if (importResult.getOldEntity() != null) {
                List<EntityRelation> existingRelations = new ArrayList<>();
                existingRelations.addAll(relationService.findByTo(user.getTenantId(), entity.getId(), RelationTypeGroup.COMMON));
                existingRelations.addAll(relationService.findByFrom(user.getTenantId(), entity.getId(), RelationTypeGroup.COMMON));

                for (EntityRelation existingRelation : existingRelations) {
                    if (!relations.contains(existingRelation)) {
                        EntityId otherEntity = null;
                        if (!existingRelation.getTo().equals(entity.getId())) {
                            otherEntity = existingRelation.getTo();
                        } else if (!existingRelation.getFrom().equals(entity.getId())) {
                            otherEntity = existingRelation.getFrom();
                        }
                        if (otherEntity != null) {
                            exportableEntitiesService.checkPermission(user, otherEntity, Operation.WRITE);
                        }
                        relationService.deleteRelation(user.getTenantId(), existingRelation);
                        importResult.addSendEventsCallback(() -> {
                            entityActionService.logEntityAction(user, existingRelation.getFrom(), null, null,
                                    ActionType.RELATION_DELETED, null, existingRelation);
                            entityActionService.logEntityAction(user, existingRelation.getTo(), null, null,
                                    ActionType.RELATION_DELETED, null, existingRelation);
                        });
                    }
                }
            }

            for (EntityRelation relation : relations) {
                relationService.saveRelation(user.getTenantId(), relation);
                importResult.addSendEventsCallback(() -> {
                    entityActionService.logEntityAction(user, relation.getFrom(), null, null,
                            ActionType.RELATION_ADD_OR_UPDATE, null, relation);
                    entityActionService.logEntityAction(user, relation.getTo(), null, null,
                            ActionType.RELATION_ADD_OR_UPDATE, null, relation);
                });
            }
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
    protected E findExistingEntity(TenantId tenantId, E entity, EntityImportSettings importSettings) {
        return (E) Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndExternalId(tenantId, entity.getId()))
                .or(() -> Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndId(tenantId, entity.getId())))
                .or(() -> {
                    if (importSettings.isFindExistingByName()) {
                        return Optional.ofNullable(exportableEntitiesService.findEntityByTenantIdAndName(tenantId, getEntityType(), entity.getName()));
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


    @RequiredArgsConstructor
    protected class IdProvider {
        private final SecurityUser user;
        private final EntityImportSettings importSettings;
        private final EntityImportResult<E> importResult;

        public <ID extends EntityId> ID getInternalId(ID externalId) {
            return getInternalId(externalId, true);
          }

        public <ID extends EntityId> ID getInternalId(ID externalId, boolean throwExceptionIfNotFound) {
            if (externalId == null || externalId.isNullUid()) return null;

            HasId<ID> entity;
            try {
                entity = findInternalEntity(user.getTenantId(), externalId);
            } catch (Exception e) {
                if (throwExceptionIfNotFound) {
                    throw e;
                } else {
                    importResult.setUpdatedAllExternalIds(false);
                    return null;
                }
            }
            try {
                exportableEntitiesService.checkPermission(user, entity, entity.getId().getEntityType(), Operation.READ);
            } catch (ThingsboardException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
            return entity.getId();
        }

        public Optional<EntityId> getInternalIdByUuid(UUID externalUuid) {
            if (externalUuid.equals(EntityId.NULL_UUID)) return Optional.empty();

            for (EntityType entityType : EntityType.values()) {
                EntityId externalId;
                try {
                    externalId = EntityIdFactory.getByTypeAndUuid(entityType, externalUuid);
                } catch (Exception e) {
                    continue;
                }

                EntityId internalId = getInternalId(externalId, false);
                if (internalId != null) {
                    return Optional.of(internalId);
                } else if (importSettings.isResetExternalIdsOfAnotherTenant()) {
                    try {
                        if (exportableEntitiesService.findEntityById(externalId) != null) {
                            return Optional.of(EntityIdFactory.getByTypeAndUuid(entityType, EntityId.NULL_UUID));
                        }
                    } catch (Exception ignored) {}
                }
            }

            importResult.setUpdatedAllExternalIds(false);
            return Optional.empty();
        }

    }

}
