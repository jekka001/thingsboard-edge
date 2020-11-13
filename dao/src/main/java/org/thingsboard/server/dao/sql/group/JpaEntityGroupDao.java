/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.sql.group;

import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.group.EntityGroupDao;
import org.thingsboard.server.dao.model.sql.EntityGroupEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDao;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Component
public class JpaEntityGroupDao extends JpaAbstractDao<EntityGroupEntity, EntityGroup> implements EntityGroupDao {

    @Autowired
    EntityGroupRepository entityGroupRepository;

    @Override
    protected Class<EntityGroupEntity> getEntityClass() {
        return EntityGroupEntity.class;
    }

    @Override
    protected CrudRepository<EntityGroupEntity, UUID> getCrudRepository() {
        return entityGroupRepository;
    }

    @Override
    public ListenableFuture<List<EntityGroup>> findEntityGroupsByIdsAsync(UUID tenantId, List<UUID> entityGroupIds) {
        return service.submit(() -> DaoUtil.convertDataList(entityGroupRepository.findEntityGroupsByIdIn(entityGroupIds)));
    }

    @Override
    public ListenableFuture<List<EntityGroup>> findEntityGroupsByType(UUID tenantId, UUID parentEntityId, EntityType parentEntityType, String relationType) {
        return service.submit(() -> DaoUtil.convertDataList(entityGroupRepository.findEntityGroupsByType(
                parentEntityId,
                parentEntityType.name(),
                relationType)));
    }

    @Override
    public ListenableFuture<PageData<EntityGroup>> findEntityGroupsByTypeAndPageLink(UUID tenantId, UUID parentEntityId,
                                                                                     EntityType parentEntityType, String relationType, PageLink pageLink) {
        return service.submit(() -> DaoUtil.toPageData(entityGroupRepository
                .findEntityGroupsByTypeAndPageLink(
                        parentEntityId,
                        parentEntityType.name(),
                        relationType,
                        Objects.toString(pageLink.getTextSearch(), ""),
                        DaoUtil.toPageable(pageLink))));
    }

    @Override
    public ListenableFuture<List<EntityGroup>> findAllEntityGroups(UUID tenantId, UUID parentEntityId, EntityType parentEntityType) {
        return service.submit(() -> DaoUtil.convertDataList(entityGroupRepository.findAllEntityGroups(
                parentEntityId,
                parentEntityType.name())));
    }

    @Override
    public ListenableFuture<Optional<EntityGroup>> findEntityGroupByTypeAndName(UUID tenantId, UUID parentEntityId, EntityType parentEntityType,
                                                                                String relationType, String name) {
        return service.submit(() ->
                Optional.ofNullable(DaoUtil.getData(entityGroupRepository.findEntityGroupByTypeAndName(
                        parentEntityId,
                        parentEntityType.name(),
                        relationType,
                        name))));
    }

    @Override
    public ListenableFuture<PageData<EntityId>> findGroupEntityIds(EntityType entityType, UUID groupId, PageLink pageLink) {
        return service.submit(() -> {
            Page<UUID> page = entityGroupRepository.findGroupEntityIds(groupId, entityType.name(), DaoUtil.toPageable(pageLink));
            List<EntityId> entityIds = page.getContent().stream().map(id ->
                    EntityIdFactory.getByTypeAndUuid(entityType, id)).collect(Collectors.toList());
            return new PageData(entityIds, page.getTotalPages(), page.getTotalElements(), page.hasNext());
        });
    }

    @Override
    public boolean isEntityInGroup(EntityId entityId, EntityGroupId entityGroupId) {
        return entityGroupRepository.isEntityInGroup(entityId.getId(), entityGroupId.getId());
    }
}
