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
package org.thingsboard.server.service.sync.ie.exporting.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ExportableEntity;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.data.sync.ie.AttributeExportData;
import org.thingsboard.server.common.data.sync.ie.EntityExportData;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.sync.ie.exporting.EntityExportService;
import org.thingsboard.server.service.sync.ie.exporting.ExportableEntitiesService;
import org.thingsboard.server.service.sync.vc.data.EntitiesExportCtx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@TbCoreComponent
@Primary
public class DefaultEntityExportService<I extends EntityId, E extends ExportableEntity<I>, D extends EntityExportData<E>> implements EntityExportService<I, E, D> {

    @Autowired
    @Lazy
    protected ExportableEntitiesService exportableEntitiesService;
    @Autowired
    protected RelationService relationService;
    @Autowired
    protected AttributesService attributesService;

    @Override
    public final D getExportData(EntitiesExportCtx<?> ctx, I entityId) throws ThingsboardException {
        D exportData = newExportData();

        E entity = exportableEntitiesService.findEntityByTenantIdAndId(ctx.getTenantId(), entityId);
        if (entity == null) {
            throw new IllegalArgumentException(entityId.getEntityType() + " [" + entityId.getId() + "] not found");
        }

        exportData.setEntity(entity);
        exportData.setEntityType(entityId.getEntityType());
        setAdditionalExportData(ctx, entity, exportData);

        var externalId = entity.getExternalId() != null ? entity.getExternalId() : entity.getId();
        ctx.putExternalId(entityId, externalId);
        entity.setId(externalId);
        entity.setTenantId(null);

        return exportData;
    }

    protected void setAdditionalExportData(EntitiesExportCtx<?> ctx, E entity, D exportData) throws ThingsboardException {
        var exportSettings = ctx.getSettings();
        if (exportSettings.isExportRelations()) {
            List<EntityRelation> relations = exportRelations(ctx, entity);
            relations.forEach(relation -> {
                relation.setFrom(getExternalIdOrElseInternal(ctx, relation.getFrom()));
                relation.setTo(getExternalIdOrElseInternal(ctx, relation.getTo()));
            });
            exportData.setRelations(relations);
        }
        if (exportSettings.isExportAttributes()) {
            Map<String, List<AttributeExportData>> attributes = exportAttributes(ctx, entity);
            exportData.setAttributes(attributes);
        }
    }

    private List<EntityRelation> exportRelations(EntitiesExportCtx<?> ctx, E entity) throws ThingsboardException {
        List<EntityRelation> relations = new ArrayList<>();

        List<EntityRelation> inboundRelations = relationService.findByTo(ctx.getTenantId(), entity.getId(), RelationTypeGroup.COMMON);
        relations.addAll(inboundRelations);

        List<EntityRelation> outboundRelations = relationService.findByFrom(ctx.getTenantId(), entity.getId(), RelationTypeGroup.COMMON);
        relations.addAll(outboundRelations);
        return relations;
    }

    private Map<String, List<AttributeExportData>> exportAttributes(EntitiesExportCtx<?> ctx, E entity) throws ThingsboardException {
        List<String> scopes;
        if (entity.getId().getEntityType() == EntityType.DEVICE) {
            scopes = List.of(DataConstants.SERVER_SCOPE, DataConstants.SHARED_SCOPE);
        } else {
            scopes = Collections.singletonList(DataConstants.SERVER_SCOPE);
        }
        Map<String, List<AttributeExportData>> attributes = new LinkedHashMap<>();
        scopes.forEach(scope -> {
            try {
                attributes.put(scope, attributesService.findAll(ctx.getTenantId(), entity.getId(), scope).get().stream()
                        .map(attribute -> {
                            AttributeExportData attributeExportData = new AttributeExportData();
                            attributeExportData.setKey(attribute.getKey());
                            attributeExportData.setLastUpdateTs(attribute.getLastUpdateTs());
                            attributeExportData.setStrValue(attribute.getStrValue().orElse(null));
                            attributeExportData.setDoubleValue(attribute.getDoubleValue().orElse(null));
                            attributeExportData.setLongValue(attribute.getLongValue().orElse(null));
                            attributeExportData.setBooleanValue(attribute.getBooleanValue().orElse(null));
                            attributeExportData.setJsonValue(attribute.getJsonValue().orElse(null));
                            return attributeExportData;
                        })
                        .collect(Collectors.toList()));
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        return attributes;
    }

    @Override
    public <ID extends EntityId> ID getExternalIdOrElseInternal(EntitiesExportCtx<?> ctx, ID internalId) {
        if (internalId == null || internalId.isNullUid()) return internalId;
        var result = ctx.getExternalId(internalId);
        if (result == null) {
            result = Optional.ofNullable(exportableEntitiesService.getExternalIdByInternal(internalId))
                    .orElse(internalId);
            ctx.putExternalId(internalId, result);
        }
        return result;
    }

    protected UUID getExternalIdOrElseInternalByUuid(EntitiesExportCtx<?> ctx, UUID internalUuid) {
        for (EntityType entityType : EntityType.values()) {
            EntityId internalId;
            try {
                internalId = EntityIdFactory.getByTypeAndUuid(entityType, internalUuid);
            } catch (Exception e) {
                continue;
            }
            EntityId externalId = ctx.getExternalId(internalId);
            if (externalId != null) {
                return externalId.getId();
            }
        }
        for (EntityType entityType : EntityType.values()) {
            EntityId internalId;
            try {
                internalId = EntityIdFactory.getByTypeAndUuid(entityType, internalUuid);
            } catch (Exception e) {
                continue;
            }
            EntityId externalId = exportableEntitiesService.getExternalIdByInternal(internalId);
            if (externalId != null) {
                ctx.putExternalId(internalId, externalId);
                return externalId.getId();
            }
        }
        return internalUuid;
    }

    protected D newExportData() {
        return (D) new EntityExportData<E>();
    }

}
