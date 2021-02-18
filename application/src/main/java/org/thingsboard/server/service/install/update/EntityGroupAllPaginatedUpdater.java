/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.install.update;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.HasCustomerId;
import org.thingsboard.server.common.data.SearchTextBased;
import org.thingsboard.server.common.data.TenantEntity;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.group.EntityGroupService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public abstract class EntityGroupAllPaginatedUpdater<I extends UUIDBased, D extends SearchTextBased<I>
        & TenantEntity & HasCustomerId> extends PaginatedUpdater<TenantId,D> {

    protected final EntityGroup groupAll;
    private final CustomerService customerService;
    private final EntityGroupService entityGroupService;
    private final boolean fetchAllTenantEntities;
    private final BiFunction<TenantId, PageLink, PageData<D>> findAllTenantEntitiesFunction;
    private final BiFunction<TenantId, List<I>, ListenableFuture<List<D>>> idsToEntitiesAsyncFunction;
    private final Function<EntityId, I> toIdFunction;
    private final Function<D, EntityId> getEntityIdFunction;

    private Map<CustomerId, Optional<EntityGroupId>> customersGroupMap = new HashMap<>();

    public EntityGroupAllPaginatedUpdater(CustomerService customerService,
                                          EntityGroupService entityGroupService,
                                          EntityGroup groupAll,
                                          boolean fetchAllTenantEntities,
                                          BiFunction<TenantId, PageLink, PageData<D>> findAllTenantEntitiesFunction,
                                          BiFunction<TenantId, List<I>, ListenableFuture<List<D>>> idsToEntitiesAsyncFunction,
                                          Function<EntityId, I> toIdFunction,
                                          Function<D, EntityId> getEntityIdFunction) {
        this.customerService = customerService;
        this.entityGroupService = entityGroupService;
        this.groupAll = groupAll;
        this.fetchAllTenantEntities = fetchAllTenantEntities;
        this.findAllTenantEntitiesFunction = findAllTenantEntitiesFunction;
        this.idsToEntitiesAsyncFunction = idsToEntitiesAsyncFunction;
        this.toIdFunction = toIdFunction;
        this.getEntityIdFunction = getEntityIdFunction;
    }

    protected PageData<D> findEntities(TenantId id, PageLink pageLink) {
        if (fetchAllTenantEntities) {
            return this.findAllTenantEntitiesFunction.apply(id, pageLink);
        } else {
            try {
                List<EntityId> entityIds = entityGroupService.findAllEntityIds(TenantId.SYS_TENANT_ID, groupAll.getId(), new PageLink(Integer.MAX_VALUE)).get();
                List<I> ids = entityIds.stream().map(entityId -> toIdFunction.apply(entityId)).collect(Collectors.toList());
                List<D> entities;
                if (!ids.isEmpty()) {
                    entities = idsToEntitiesAsyncFunction.apply(id, ids).get();
                } else {
                    entities = Collections.emptyList();
                }
                return new PageData<>(entities, 1, entities.size(), false);
            } catch (Exception e) {
                log.error("Failed to get entities from group all!", e);
                throw new RuntimeException("Failed to get entities from group all!", e);
            }
        }
    }

    @Override
    protected void updateEntity(D entity) {
        EntityId entityId = getEntityIdFunction.apply(entity);
        entityGroupService.addEntityToEntityGroupAll(TenantId.SYS_TENANT_ID, entity.getTenantId(), entityId);
        if (entity.getCustomerId() != null && !entity.getCustomerId().isNullUid()) {
            Optional<EntityGroupId> customerEntityGroupId = customersGroupMap.computeIfAbsent(
                    entity.getCustomerId(), customerId -> {
                        Customer customer = customerService.findCustomerById(entity.getTenantId(), customerId);
                        if (customer != null) {
                            EntityGroupId entityGroupId = entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(entity.getTenantId(),
                                    customerId, entity.getEntityType()).getId();
                            return Optional.of(entityGroupId);
                        } else {
                            return Optional.empty();
                        }
                    }
            );
            if (customerEntityGroupId.isPresent()) {
                entityGroupService.addEntityToEntityGroup(TenantId.SYS_TENANT_ID, customerEntityGroupId.get(), entityId);
            }
            unassignFromCustomer(entity);
        }
    }

    protected abstract void unassignFromCustomer(D entity);

}
