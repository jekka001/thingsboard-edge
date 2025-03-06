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
package org.thingsboard.server.edqs.repo;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ObjectType;
import org.thingsboard.server.common.data.edqs.AttributeKv;
import org.thingsboard.server.common.data.edqs.DataPoint;
import org.thingsboard.server.common.data.edqs.EdqsEvent;
import org.thingsboard.server.common.data.edqs.EdqsEventType;
import org.thingsboard.server.common.data.edqs.EdqsObject;
import org.thingsboard.server.common.data.edqs.Entity;
import org.thingsboard.server.common.data.edqs.LatestTsKv;
import org.thingsboard.server.common.data.edqs.fields.AssetFields;
import org.thingsboard.server.common.data.edqs.fields.CustomerFields;
import org.thingsboard.server.common.data.edqs.fields.EntityFields;
import org.thingsboard.server.common.data.edqs.fields.EntityGroupFields;
import org.thingsboard.server.common.data.edqs.query.QueryResult;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.QueryContext;
import org.thingsboard.server.common.data.query.EntityCountQuery;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityDataSortOrder;
import org.thingsboard.server.common.data.query.EntityFilter;
import org.thingsboard.server.common.data.query.EntityKeyType;
import org.thingsboard.server.common.data.query.SingleEntityFilter;
import org.thingsboard.server.common.data.query.StateEntityOwnerFilter;
import org.thingsboard.server.common.data.query.TsValue;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.edqs.data.ApiUsageStateData;
import org.thingsboard.server.edqs.data.AssetData;
import org.thingsboard.server.edqs.data.CustomerData;
import org.thingsboard.server.edqs.data.DeviceData;
import org.thingsboard.server.edqs.data.EntityData;
import org.thingsboard.server.edqs.data.EntityGroupData;
import org.thingsboard.server.edqs.data.EntityProfileData;
import org.thingsboard.server.edqs.data.GenericData;
import org.thingsboard.server.edqs.data.RelationsRepo;
import org.thingsboard.server.edqs.data.TenantData;
import org.thingsboard.server.edqs.query.EdqsDataQuery;
import org.thingsboard.server.edqs.query.EdqsQuery;
import org.thingsboard.server.edqs.query.SortableEntityData;
import org.thingsboard.server.edqs.query.processor.EntityQueryProcessor;
import org.thingsboard.server.edqs.query.processor.EntityQueryProcessorFactory;
import org.thingsboard.server.edqs.stats.EdqsStatsService;
import org.thingsboard.server.edqs.util.RepositoryUtils;
import org.thingsboard.server.edqs.util.TbStringPool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static org.thingsboard.server.edqs.util.RepositoryUtils.SORT_ASC;
import static org.thingsboard.server.edqs.util.RepositoryUtils.SORT_DESC;
import static org.thingsboard.server.edqs.util.RepositoryUtils.SYS_ADMIN_PERMISSIONS;
import static org.thingsboard.server.edqs.util.RepositoryUtils.resolveEntityType;

@Slf4j
public class TenantRepo {

    public static final Comparator<EntityData<?>> CREATED_TIME_COMPARATOR = Comparator.comparingLong(ed -> ed.getFields().getCreatedTime());
    public static final Comparator<EntityData<?>> CREATED_TIME_AND_ID_COMPARATOR = CREATED_TIME_COMPARATOR
            .thenComparing(EntityData::getId);
    public static final Comparator<EntityData<?>> CREATED_TIME_AND_ID_DESC_COMPARATOR = CREATED_TIME_AND_ID_COMPARATOR.reversed();

    private final ConcurrentMap<EntityType, Set<EntityData<?>>> entitySetByType = new ConcurrentHashMap<>();
    private final ConcurrentMap<EntityType, ConcurrentMap<UUID, EntityData<?>>> entityMapByType = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<UUID>> customersHierarchy = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, EntityGroupData> entityGroups = new ConcurrentHashMap<>();
    private final ConcurrentMap<RelationTypeGroup, RelationsRepo> relations = new ConcurrentHashMap<>();

    private final Lock entityUpdateLock = new ReentrantLock();

    private final TenantId tenantId;
    private final Optional<EdqsStatsService> edqsStatsService;

    public TenantRepo(TenantId tenantId, Optional<EdqsStatsService> edqsStatsService) {
        this.tenantId = tenantId;
        this.edqsStatsService = edqsStatsService;
    }

    public void processEvent(EdqsEvent event) {
        EdqsObject edqsObject = event.getObject();
        log.trace("[{}] Processing event: {}", tenantId, event);
        if (event.getEventType() == EdqsEventType.UPDATED) {
            addOrUpdate(edqsObject);
        } else if (event.getEventType() == EdqsEventType.DELETED) {
            remove(edqsObject);
        }
    }

    public void addOrUpdate(EdqsObject object) {
        if (object instanceof EntityRelation relation) {
            addOrUpdateRelation(relation);
        } else if (object instanceof AttributeKv attributeKv) {
            addOrUpdateAttribute(attributeKv);
        } else if (object instanceof LatestTsKv latestTsKv) {
            addOrUpdateLatestKv(latestTsKv);
        } else if (object instanceof Entity entity) {
            addOrUpdateEntity(entity);
        }
    }

    public void remove(EdqsObject object) {
        if (object instanceof EntityRelation relation) {
            removeRelation(relation);
        } else if (object instanceof AttributeKv attributeKv) {
            removeAttribute(attributeKv);
        } else if (object instanceof LatestTsKv latestTsKv) {
            removeLatestKv(latestTsKv);
        } else if (object instanceof Entity entity) {
            removeEntity(entity);
        }
    }

    private void addOrUpdateRelation(EntityRelation entity) {
        entityUpdateLock.lock();
        try {
            if (RelationTypeGroup.COMMON.equals(entity.getTypeGroup())) {
                RelationsRepo repo = relations.computeIfAbsent(entity.getTypeGroup(), tg -> new RelationsRepo());
                EntityData<?> from = getOrCreate(entity.getFrom());
                EntityData<?> to = getOrCreate(entity.getTo());
                boolean added = repo.add(from, to, TbStringPool.intern(entity.getType()));
                if (added) {
                    edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.RELATION, EdqsEventType.UPDATED));
                }
            } else if (RelationTypeGroup.FROM_ENTITY_GROUP.equals(entity.getTypeGroup())) {
                var eg = getEntityGroup(entity.getFrom().getId());
                if (eg != null) {
                    eg.addOrUpdate(getOrCreate(entity.getTo()));
                }
            }
        } finally {
            entityUpdateLock.unlock();
        }
    }

    private void removeRelation(EntityRelation entityRelation) {
        if (RelationTypeGroup.COMMON.equals(entityRelation.getTypeGroup())) {
            RelationsRepo relationsRepo = relations.get(entityRelation.getTypeGroup());
            if (relationsRepo != null) {
                boolean removed = relationsRepo.remove(entityRelation.getFrom().getId(), entityRelation.getTo().getId(), entityRelation.getType());
                if (removed) {
                    edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.RELATION, EdqsEventType.DELETED));
                }
            }
        } else if (RelationTypeGroup.FROM_ENTITY_GROUP.equals(entityRelation.getTypeGroup())) {
            var eg = getEntityGroup(entityRelation.getFrom().getId());
            if (eg != null) {
                eg.remove(entityRelation.getTo().getId());
            }
        }
    }

    private void addOrUpdateEntity(Entity entity) {
        entityUpdateLock.lock();
        try {
            log.trace("[{}] addOrUpdateEntity: {}", tenantId, entity);
            EntityFields fields = entity.getFields();
            UUID entityId = fields.getId();
            EntityType entityType = entity.getType();

            EntityData entityData = getOrCreate(entityType, entityId);
            processFields(fields);
            EntityFields oldFields = entityData.getFields();
            entityData.setFields(fields);
            if (oldFields == null) {
                getEntitySet(entityType).add(entityData);
            }

            switch (entity.getType()) {
                case ENTITY_GROUP -> {
                    EntityGroupFields entityGroupFields = (EntityGroupFields) fields;
                    UUID ownerId = entityGroupFields.getOwnerId();
                    if (EntityType.CUSTOMER.equals(entityGroupFields.getOwnerType())) {
                        entityData.setCustomerId(ownerId);
                        ((CustomerData) getEntityMap(EntityType.CUSTOMER).computeIfAbsent(ownerId, CustomerData::new)).addOrUpdate(entityData);
                    }
                    entityGroups.put(fields.getId(), (EntityGroupData) entityData);
                }
                case CUSTOMER -> {
                    CustomerFields customerFields = (CustomerFields) fields;
                    UUID newParentId = customerFields.getCustomerId(); // for customer, customerId is parentCustomerId
                    UUID oldParentId = entityData.getCustomerId();
                    entityData.setCustomerId(newParentId);
                    if (entityIdMismatch(oldParentId, newParentId)) {
                        if (oldParentId != null) {
                            customersHierarchy.computeIfAbsent(oldParentId, id -> new HashSet<>()).remove(entityData.getId());
                        }
                        if (newParentId != null) {
                            customersHierarchy.computeIfAbsent(newParentId, id -> new HashSet<>()).add(entityData.getId());
                        }
                    }
                }
                default -> {
                    UUID newCustomerId = fields.getCustomerId();
                    UUID oldCustomerId = entityData.getCustomerId();
                    entityData.setCustomerId(newCustomerId);
                    if (entityIdMismatch(oldCustomerId, newCustomerId)) {
                        if (oldCustomerId != null) {
                            CustomerData old = (CustomerData) getEntityMap(EntityType.CUSTOMER).get(oldCustomerId);
                            if (old != null) {
                                old.remove(entityData);
                            }
                        }
                        if (newCustomerId != null) {
                            CustomerData newData = (CustomerData) getEntityMap(EntityType.CUSTOMER).computeIfAbsent(newCustomerId, CustomerData::new);
                            newData.addOrUpdate(entityData);
                        }
                    }
                }
            }
        } finally {
            entityUpdateLock.unlock();
        }
    }

    public void removeEntity(Entity entity) {
        entityUpdateLock.lock();
        try {
            UUID entityId = entity.getFields().getId();
            EntityType entityType = entity.getType();
            EntityData<?> removed = getEntityMap(entityType).remove(entityId);
            if (removed != null) {
                if (removed.getFields() != null) {
                    getEntitySet(entityType).remove(removed);
                }
                edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.fromEntityType(entityType), EdqsEventType.DELETED));
            }
            switch (entityType) {
                case ENTITY_GROUP -> {
                    entityGroups.remove(entityId);
                }
                case CUSTOMER -> {
                    customersHierarchy.remove(entityId);
                }
            }
        } finally {
            entityUpdateLock.unlock();
        }
    }

    public void addOrUpdateAttribute(AttributeKv attributeKv) {
        var entityData = getOrCreate(attributeKv.getEntityId());
        if (entityData != null) {
            Integer keyId = KeyDictionary.get(attributeKv.getKey());
            boolean added = entityData.putAttr(keyId, attributeKv.getScope(), attributeKv.getDataPoint());
            if (added) {
                edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.ATTRIBUTE_KV, EdqsEventType.UPDATED));
            }
        }
    }

    private void removeAttribute(AttributeKv attributeKv) {
        var entityData = get(attributeKv.getEntityId());
        if (entityData != null) {
            boolean removed = entityData.removeAttr(KeyDictionary.get(attributeKv.getKey()), attributeKv.getScope());
            if (removed) {
                edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.ATTRIBUTE_KV, EdqsEventType.DELETED));
            }
        }
    }

    public void addOrUpdateLatestKv(LatestTsKv latestTsKv) {
        var entityData = getOrCreate(latestTsKv.getEntityId());
        if (entityData != null) {
            Integer keyId = KeyDictionary.get(latestTsKv.getKey());
            boolean added = entityData.putTs(keyId, latestTsKv.getDataPoint());
            if (added) {
                edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.LATEST_TS_KV, EdqsEventType.UPDATED));
            }
        }
    }

    private void removeLatestKv(LatestTsKv latestTsKv) {
        var entityData = get(latestTsKv.getEntityId());
        if (entityData != null) {
            boolean removed = entityData.removeTs(KeyDictionary.get(latestTsKv.getKey()));
            if (removed) {
                edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.LATEST_TS_KV, EdqsEventType.DELETED));
            }
        }
    }

    public void processFields(EntityFields fields) {
        if (fields instanceof AssetFields assetFields) {
            assetFields.setType(TbStringPool.intern(assetFields.getType()));
        }
    }

    public ConcurrentMap<UUID, EntityData<?>> getEntityMap(EntityType entityType) {
        return entityMapByType.computeIfAbsent(entityType, et -> new ConcurrentHashMap<>());
    }

    //TODO: automatically remove entities that has nothing except the ID.
    private EntityData<?> getOrCreate(EntityId entityId) {
        return getOrCreate(entityId.getEntityType(), entityId.getId());
    }

    private EntityData<?> getOrCreate(EntityType entityType, UUID entityId) {
        return getEntityMap(entityType).computeIfAbsent(entityId, id -> {
            log.debug("[{}] Adding {} {}", tenantId, entityType, id);
            EntityData<?> entityData = constructEntityData(entityType, entityId);
            edqsStatsService.ifPresent(statService -> statService.reportEvent(tenantId, ObjectType.fromEntityType(entityType), EdqsEventType.UPDATED));
            return entityData;
        });
    }

    private EntityData<?> get(EntityId entityId) {
        return getEntityMap(entityId.getEntityType()).get(entityId.getId());
    }

    private EntityData<?> constructEntityData(EntityType entityType, UUID id) {
        EntityData<?> entityData = switch (entityType) {
            case DEVICE -> new DeviceData(id);
            case ASSET -> new AssetData(id);
            case DEVICE_PROFILE, ASSET_PROFILE -> new EntityProfileData(id, entityType);
            case CUSTOMER -> new CustomerData(id);
            case TENANT -> new TenantData(id);
            case ENTITY_GROUP -> new EntityGroupData(id);
            case API_USAGE_STATE -> new ApiUsageStateData(id);
            default -> new GenericData(entityType, id);
        };
        entityData.setRepo(this);
        return entityData;
    }

    private static boolean entityIdMismatch(UUID oldOrNull, UUID newOrNull) {
        if (oldOrNull == null) {
            return newOrNull != null;
        } else {
            return !oldOrNull.equals(newOrNull);
        }
    }

    public Set<EntityData<?>> getEntitySet(EntityType entityType) {
        return entitySetByType.computeIfAbsent(entityType, et -> new ConcurrentSkipListSet<>(CREATED_TIME_AND_ID_DESC_COMPARATOR));
    }

    public PageData<QueryResult> findEntityDataByQuery(CustomerId customerId, MergedUserPermissions userPermissions,
                                                       EntityDataQuery oldQuery, boolean ignorePermissionCheck) {
        EdqsDataQuery query = RepositoryUtils.toNewQuery(oldQuery);
        log.info("[{}][{}] findEntityDataByQuery: {}", tenantId, customerId, query);
        QueryContext ctx = buildContext(customerId, userPermissions, query.getEntityFilter(), ignorePermissionCheck);
        if (ctx == null) {
            return PageData.emptyPageData();
        }
        EntityQueryProcessor queryProcessor = EntityQueryProcessorFactory.create(this, ctx, query);
        return sortAndConvert(query, queryProcessor.processQuery(), ctx);
    }

    public long countEntitiesByQuery(CustomerId customerId, MergedUserPermissions userPermissions, EntityCountQuery oldQuery, boolean ignorePermissionCheck) {
        EdqsQuery query = RepositoryUtils.toNewQuery(oldQuery);
        log.info("[{}][{}] countEntitiesByQuery: {}", tenantId, customerId, query);
        QueryContext ctx = buildContext(customerId, userPermissions, query.getEntityFilter(), ignorePermissionCheck);
        if (ctx == null) {
            return 0;
        }
        EntityQueryProcessor queryProcessor = EntityQueryProcessorFactory.create(this, ctx, query);
        return queryProcessor.count();
    }

    private PageData<QueryResult> sortAndConvert(EdqsDataQuery query, List<SortableEntityData> data, QueryContext ctx) {
        int totalSize = data.size();
        int totalPages = (int) Math.ceil((float) totalSize / query.getPageSize());
        int offset = query.getPage() * query.getPageSize();
        if (offset > totalSize) {
            return new PageData<>(Collections.emptyList(), totalPages, totalSize, false);
        } else {
            Comparator<SortableEntityData> comparator = EntityDataSortOrder.Direction.ASC.equals(query.getSortDirection()) ? SORT_ASC : SORT_DESC;
            long startTs = System.nanoTime();
//          IMPLEMENTATION THAT IS BASED ON PRIORITY_QUEUE
//            var requiredSize = Math.min(offset + query.getPageSize(), totalSize);
//            PriorityQueue<SortableEntityData> topN = new PriorityQueue<>(requiredSize, comparator.reversed());
//            for (SortableEntityData item : data) {
//                topN.add(item);
//                if (topN.size() > requiredSize) {
//                    topN.poll();
//                }
//            }
//            List<SortableEntityData> result = new ArrayList<>(topN);
//            Collections.reverse(result);
//            result = result.subList(offset, requiredSize);
//          IMPLEMENTATION THAT IS BASED ON TREE SET  (For offset + query.getPageSize() << totalSize)
            var requiredSize = Math.min(offset + query.getPageSize(), totalSize);
            TreeSet<SortableEntityData> topNSet = new TreeSet<>(comparator);
            for (SortableEntityData sp : data) {
                topNSet.add(sp);
                if (topNSet.size() > requiredSize) {
                    topNSet.pollLast();
                }
            }
            var result = topNSet.stream().skip(offset).limit(query.getPageSize()).collect(Collectors.toList());
//          IMPLEMENTATION THAT IS BASED ON TIM SORT (For offset + query.getPageSize() > totalSize / 2)
//            data.sort(comparator);
//            var result = data.subList(offset, endIndex);
            log.trace("EDQ Sorted in {}", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTs));
            return new PageData<>(toQueryResult(result, query, ctx), totalPages, totalSize, totalSize > requiredSize);
        }
    }

    private List<QueryResult> toQueryResult(List<SortableEntityData> data, EdqsDataQuery query, QueryContext ctx) {
        long ts = System.currentTimeMillis();
        List<QueryResult> results = new ArrayList<>(data.size());
        for (SortableEntityData entityData : data) {
            Map<EntityKeyType, Map<String, TsValue>> latest = new HashMap<>();
            for (var key : query.getEntityFields()) {
                DataPoint dp = entityData.getEntityData().getDataPoint(key, ctx);
                TsValue v = RepositoryUtils.toTsValue(ts, dp);
                latest.computeIfAbsent(EntityKeyType.ENTITY_FIELD, t -> new HashMap<>()).put(key.key(), v);
            }
            for (var key : query.getLatestValues()) {
                DataPoint dp = entityData.getEntityData().getDataPoint(key, ctx);
                TsValue v = RepositoryUtils.toTsValue(ts, dp);
                latest.computeIfAbsent(key.type(), t -> new HashMap<>()).put(KeyDictionary.get(key.keyId()), v);
            }

            results.add(new QueryResult(entityData.getEntityId(), entityData.isReadAttrs(), entityData.isReadTs(), latest));
        }
        return results;
    }

    private QueryContext buildContext(CustomerId customerId, MergedUserPermissions userPermissions, EntityFilter filter, boolean ignorePermissionCheck) {
        QueryContext queryContext;
        if (TenantId.SYS_TENANT_ID.equals(tenantId)) {
            queryContext = new QueryContext(tenantId, customerId, resolveEntityType(filter), SYS_ADMIN_PERMISSIONS, filter, ignorePermissionCheck);
        } else {
            switch (filter.getType()) {
                case STATE_ENTITY_OWNER:
                    var singleEntity = ((StateEntityOwnerFilter) filter).getSingleEntity();
                    EntityData ed = getEntityMap(singleEntity.getEntityType()).get(singleEntity.getId());
                    if (ed != null) {
                        EntityId owner = ed.getCustomerId() != null ? new CustomerId(ed.getCustomerId()) : tenantId;
                        queryContext = new QueryContext(tenantId, customerId, owner.getEntityType(), userPermissions, filter, owner, ignorePermissionCheck);
                    } else {
                        return null;
                    }
                    break;
                case SINGLE_ENTITY:
                    SingleEntityFilter seFilter = (SingleEntityFilter) filter;
                    EntityId entityId = seFilter.getSingleEntity();
                    if (entityId != null && entityId.getEntityType().equals(EntityType.ENTITY_GROUP)) {
                        EntityGroupData entityGroupData = entityGroups.get(entityId.getId());
                        if (entityGroupData != null) {
                            queryContext = new QueryContext(tenantId, customerId, EntityType.ENTITY_GROUP, userPermissions, filter, entityGroupData.getEntityType(), ignorePermissionCheck);
                        } else {
                            return null;
                        }
                    } else {
                        queryContext = new QueryContext(tenantId, customerId, resolveEntityType(filter), userPermissions, filter, ignorePermissionCheck);
                    }
                    break;
                default:
                    queryContext = new QueryContext(tenantId, customerId, resolveEntityType(filter), userPermissions, filter, ignorePermissionCheck);
            }
        }
        return queryContext;
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public Set<UUID> getAllCustomers(UUID customerId) {
        Set<UUID> result = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();

        if (customerId != null) {
            queue.add(customerId);
        }

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (!result.contains(current)) {
                result.add(current);
                Set<UUID> children = customersHierarchy.get(current);
                if (children != null) {
                    queue.addAll(children);
                }
            }
        }

        return result;
    }

    public boolean contains(UUID entityGroupID, UUID entityId) {
        var groupData = entityGroups.get(entityGroupID);
        return groupData != null && groupData.getEntity(entityId) != null;
    }

    public EntityGroupData getEntityGroup(UUID groupId) {
        return entityGroups.get(groupId);
    }

    public RelationsRepo getRelations(RelationTypeGroup relationTypeGroup) {
        return relations.get(relationTypeGroup);
    }

    public String getOwnerName(EntityId ownerId) {
        if (ownerId == null || (EntityType.CUSTOMER.equals(ownerId.getEntityType()) && CustomerId.NULL_UUID.equals(ownerId.getId()))) {
            ownerId = tenantId;
        }
        return getEntityName(ownerId);
    }

    private String getEntityName(EntityId entityId) {
        EntityType entityType = entityId.getEntityType();
        return switch (entityType) {
            case CUSTOMER, TENANT -> getEntityMap(entityType).get(entityId.getId()).getFields().getName();
            default -> throw new RuntimeException("Unsupported entity type: " + entityType);
        };
    }

}
