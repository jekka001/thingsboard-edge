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
package org.thingsboard.server.service.edqs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ObjectType;
import org.thingsboard.server.common.data.edqs.AttributeKv;
import org.thingsboard.server.common.data.edqs.EdqsEventType;
import org.thingsboard.server.common.data.edqs.EdqsObject;
import org.thingsboard.server.common.data.edqs.Entity;
import org.thingsboard.server.common.data.edqs.LatestTsKv;
import org.thingsboard.server.common.data.edqs.fields.EntityFields;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageDataIterable;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.dao.Dao;
import org.thingsboard.server.dao.attributes.AttributesDao;
import org.thingsboard.server.dao.dictionary.KeyDictionaryDao;
import org.thingsboard.server.dao.entity.EntityDaoRegistry;
import org.thingsboard.server.dao.group.EntityGroupDao;
import org.thingsboard.server.dao.model.sql.AttributeKvEntity;
import org.thingsboard.server.dao.model.sql.RelationEntity;
import org.thingsboard.server.dao.model.sqlts.dictionary.KeyDictionaryEntry;
import org.thingsboard.server.dao.model.sqlts.latest.TsKvLatestEntity;
import org.thingsboard.server.dao.sql.relation.RelationRepository;
import org.thingsboard.server.dao.sqlts.latest.TsKvLatestRepository;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.thingsboard.server.common.data.ObjectType.API_USAGE_STATE;
import static org.thingsboard.server.common.data.ObjectType.ASSET;
import static org.thingsboard.server.common.data.ObjectType.ASSET_PROFILE;
import static org.thingsboard.server.common.data.ObjectType.ATTRIBUTE_KV;
import static org.thingsboard.server.common.data.ObjectType.BLOB_ENTITY;
import static org.thingsboard.server.common.data.ObjectType.CONVERTER;
import static org.thingsboard.server.common.data.ObjectType.CUSTOMER;
import static org.thingsboard.server.common.data.ObjectType.DASHBOARD;
import static org.thingsboard.server.common.data.ObjectType.DEVICE;
import static org.thingsboard.server.common.data.ObjectType.DEVICE_PROFILE;
import static org.thingsboard.server.common.data.ObjectType.EDGE;
import static org.thingsboard.server.common.data.ObjectType.ENTITY_GROUP;
import static org.thingsboard.server.common.data.ObjectType.ENTITY_VIEW;
import static org.thingsboard.server.common.data.ObjectType.INTEGRATION;
import static org.thingsboard.server.common.data.ObjectType.LATEST_TS_KV;
import static org.thingsboard.server.common.data.ObjectType.QUEUE_STATS;
import static org.thingsboard.server.common.data.ObjectType.RELATION;
import static org.thingsboard.server.common.data.ObjectType.ROLE;
import static org.thingsboard.server.common.data.ObjectType.RULE_CHAIN;
import static org.thingsboard.server.common.data.ObjectType.SCHEDULER_EVENT;
import static org.thingsboard.server.common.data.ObjectType.TENANT_PROFILE;
import static org.thingsboard.server.common.data.ObjectType.USER;
import static org.thingsboard.server.common.data.ObjectType.WIDGETS_BUNDLE;
import static org.thingsboard.server.common.data.ObjectType.WIDGET_TYPE;

@Slf4j
public abstract class EdqsSyncService {

    @Value("${queue.edqs.sync.entity_batch_size:10000}")
    private int entityBatchSize;
    @Value("${queue.edqs.sync.ts_batch_size:10000}")
    private int tsBatchSize;
    @Autowired
    private EntityDaoRegistry entityDaoRegistry;
    @Autowired
    private AttributesDao attributesDao;
    @Autowired
    private KeyDictionaryDao keyDictionaryDao;
    @Autowired
    private RelationRepository relationRepository;
    @Autowired
    private EntityGroupDao entityGroupDao;
    @Autowired
    private TsKvLatestRepository tsKvLatestRepository;
    @Autowired
    @Lazy
    private DefaultEdqsService edqsService;

    private final ConcurrentHashMap<UUID, EntityIdInfo> entityInfoMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> keys = new ConcurrentHashMap<>();

    private final Map<ObjectType, AtomicInteger> counters = new ConcurrentHashMap<>();

    public static final Set<ObjectType> edqsTenantTypes = EnumSet.of(
            TENANT_PROFILE, CUSTOMER, DEVICE_PROFILE, DEVICE, ASSET_PROFILE, ASSET, EDGE, ENTITY_VIEW, USER, DASHBOARD,
            RULE_CHAIN, WIDGET_TYPE, WIDGETS_BUNDLE, CONVERTER, INTEGRATION, SCHEDULER_EVENT, ROLE,
            BLOB_ENTITY, API_USAGE_STATE, QUEUE_STATS
    );

    public abstract boolean isSyncNeeded();

    public void sync() {
        log.info("Synchronizing data to EDQS");
        long startTs = System.currentTimeMillis();
        counters.clear();

        syncTenantEntities();
        syncEntityGroups();
        syncRelations();
        loadKeyDictionary();
        syncAttributes();
        syncLatestTimeseries();

        counters.clear();
        log.info("Finishing synchronizing data to EDQS in {} ms", (System.currentTimeMillis() - startTs));
    }

    private void process(TenantId tenantId, ObjectType type, EdqsObject object) {
        AtomicInteger counter = counters.computeIfAbsent(type, t -> new AtomicInteger());
        if (counter.incrementAndGet() % 10000 == 0) {
            log.info("Processed {} {} objects", counter.get(), type);
        }
        edqsService.processEvent(tenantId, type, EdqsEventType.UPDATED, object);
    }

    private void syncTenantEntities() {
        for (ObjectType type : edqsTenantTypes) {
            log.info("Synchronizing {} entities to EDQS", type);
            long ts = System.currentTimeMillis();
            EntityType entityType = type.toEntityType();
            Dao<?> dao = entityDaoRegistry.getDao(entityType);
            UUID lastId = UUID.fromString("00000000-0000-0000-0000-000000000000");
            while (true) {
                var batch = dao.findNextBatch(lastId, entityBatchSize);
                if (batch.isEmpty()) {
                    break;
                }
                for (EntityFields entityFields : batch) {
                    TenantId tenantId = TenantId.fromUUID(entityFields.getTenantId());
                    entityInfoMap.put(entityFields.getId(), new EntityIdInfo(entityType, tenantId));
                    process(tenantId, type, new Entity(entityType, entityFields));
                }
                EntityFields lastRecord = batch.get(batch.size() - 1);
                lastId = lastRecord.getId();
            }
            log.info("Finished synchronizing {} entities to EDQS in {} ms", type, (System.currentTimeMillis() - ts));
        }
    }
    private void syncEntityGroups() {
        log.info("Synchronizing entity groups to EDQS");
        long ts = System.currentTimeMillis();

        UUID lastId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        while (true) {
            var batch = entityGroupDao.findNextBatch(lastId, entityBatchSize);
            if (batch.isEmpty()) {
                break;
            }
            for (EntityFields groupFields : batch) {
                EntityIdInfo entityIdInfo = entityInfoMap.get(groupFields.getOwnerId());
                if (entityIdInfo != null) {
                    entityInfoMap.put(groupFields.getId(), new EntityIdInfo(EntityType.ENTITY_GROUP, entityIdInfo.tenantId()));
                    process(entityIdInfo.tenantId(), ENTITY_GROUP, new Entity(EntityType.ENTITY_GROUP, groupFields));
                } else {
                    log.info("Entity group owner not found: " + groupFields.getOwnerId());
                }
            }
            EntityFields lastRecord = batch.get(batch.size() - 1);
            lastId = lastRecord.getId();
        }
        log.info("Finished synchronizing entity groups to EDQS in {} ms", (System.currentTimeMillis() - ts));
    }

    private void syncRelations() {
        log.info("Synchronizing relations to EDQS");
        long ts = System.currentTimeMillis();
        UUID lastFromEntityId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        String lastFromEntityType = "";
        String lastRelationTypeGroup = "";
        String lastRelationType = "";
        UUID lastToEntityId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        String lastToEntityType = "";

        while (true) {
            List<RelationEntity> batch = relationRepository.findNextBatch(lastFromEntityId, lastFromEntityType, lastRelationTypeGroup,
                    lastRelationType, lastToEntityId, lastToEntityType, entityBatchSize);
            if (batch.isEmpty()) {
                break;
            }
            processRelationBatch(batch);

            RelationEntity lastRecord = batch.get(batch.size() - 1);
            lastFromEntityId = lastRecord.getFromId();
            lastFromEntityType = lastRecord.getFromType();
            lastRelationTypeGroup = lastRecord.getRelationTypeGroup();
            lastRelationType = lastRecord.getRelationType();
            lastToEntityId = lastRecord.getToId();
            lastToEntityType = lastRecord.getToType();
        }
        log.info("Finished synchronizing relations to EDQS in {} ms", (System.currentTimeMillis() - ts));
    }

    private void processRelationBatch(List<RelationEntity> relations) {
        for (RelationEntity relation : relations) {
            if (RelationTypeGroup.COMMON.name().equals(relation.getRelationTypeGroup()) || (RelationTypeGroup.FROM_ENTITY_GROUP.name().equals(relation.getRelationTypeGroup()))) {
                EntityIdInfo entityIdInfo = entityInfoMap.get(relation.getFromId());
                if (entityIdInfo != null) {
                    process(entityIdInfo.tenantId(), RELATION, relation.toData());
                } else {
                    log.info("Relation from entity not found: " + relation.getFromType() + " " + relation.getFromId());
                }
            }
        }
    }

    private void loadKeyDictionary() {
        log.info("Loading key dictionary");
        long ts = System.currentTimeMillis();
        var keyDictionaryEntries = new PageDataIterable<>(keyDictionaryDao::findAll, 10000);
        for (KeyDictionaryEntry keyDictionaryEntry : keyDictionaryEntries) {
            keys.put(keyDictionaryEntry.getKeyId(), keyDictionaryEntry.getKey());
        }
        log.info("Finished loading key dictionary in {} ms", (System.currentTimeMillis() - ts));
    }

    private void syncAttributes() {
        log.info("Synchronizing attributes to EDQS");
        long ts = System.currentTimeMillis();

        UUID lastEntityId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        int lastAttributeType = Integer.MIN_VALUE;
        int lastAttributeKey = Integer.MIN_VALUE;

        while (true) {
            List<AttributeKvEntity> batch = attributesDao.findNextBatch(lastEntityId, lastAttributeType, lastAttributeKey, tsBatchSize);
            if (batch.isEmpty()) {
                break;
            }
            processAttributeBatch(batch);

            AttributeKvEntity lastRecord = batch.get(batch.size() - 1);
            lastEntityId = lastRecord.getId().getEntityId();
            lastAttributeType = lastRecord.getId().getAttributeType();
            lastAttributeKey = lastRecord.getId().getAttributeKey();
        }
        log.info("Finished synchronizing attributes to EDQS in {} ms", (System.currentTimeMillis() - ts));
    }

    private void processAttributeBatch(List<AttributeKvEntity> batch) {
        for (AttributeKvEntity attribute : batch) {
            attribute.setStrKey(getStrKeyOrFetchFromDb(attribute.getId().getAttributeKey()));
            UUID entityId = attribute.getId().getEntityId();
            EntityIdInfo entityIdInfo = entityInfoMap.get(entityId);
            if (entityIdInfo == null) {
                log.debug("Skipping attribute with entity UUID {} as it is not found in entityInfoMap", entityId);
                continue;
            }
            AttributeKv attributeKv = new AttributeKv(
                    EntityIdFactory.getByTypeAndUuid(entityIdInfo.entityType(), entityId),
                    AttributeScope.valueOf(attribute.getId().getAttributeType()),
                    attribute.toData(),
                    attribute.getVersion());
            process(entityIdInfo.tenantId(), ATTRIBUTE_KV, attributeKv);
        }
    }

    private void syncLatestTimeseries() {
        log.info("Synchronizing latest timeseries to EDQS");
        long ts = System.currentTimeMillis();
        UUID lastEntityId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        int lastKey = Integer.MIN_VALUE;

        while (true) {
            List<TsKvLatestEntity> batch = tsKvLatestRepository.findNextBatch(lastEntityId, lastKey, tsBatchSize);
            if (batch.isEmpty()) {
                break;
            }
            processTsKvLatestBatch(batch);

            TsKvLatestEntity lastRecord = batch.get(batch.size() - 1);
            lastEntityId = lastRecord.getEntityId();
            lastKey = lastRecord.getKey();
        }
        log.info("Finished synchronizing latest timeseries to EDQS in {} ms", (System.currentTimeMillis() - ts));
    }

    private void processTsKvLatestBatch(List<TsKvLatestEntity> tsKvLatestEntities) {
        for (TsKvLatestEntity tsKvLatestEntity : tsKvLatestEntities) {
            try {
                String strKey = getStrKeyOrFetchFromDb(tsKvLatestEntity.getKey());
                if (strKey == null) {
                    log.debug("Skipping latest timeseries with key {} as it is not found in key dictionary", tsKvLatestEntity.getKey());
                    continue;
                }
                tsKvLatestEntity.setStrKey(strKey);
                UUID entityUuid = tsKvLatestEntity.getEntityId();
                EntityIdInfo entityIdInfo = entityInfoMap.get(entityUuid);
                if (entityIdInfo != null) {
                    EntityId entityId = EntityIdFactory.getByTypeAndUuid(entityIdInfo.entityType(), entityUuid);
                    LatestTsKv latestTsKv = new LatestTsKv(entityId, tsKvLatestEntity.toData(), tsKvLatestEntity.getVersion());
                    process(entityIdInfo.tenantId(), LATEST_TS_KV, latestTsKv);
                }
            } catch (Exception e) {
                log.error("Failed to sync latest timeseries: {}", tsKvLatestEntity, e);
            }
        }
    }

    private String getStrKeyOrFetchFromDb(int key) {
        String strKey = keys.get(key);
        if (strKey != null) {
            return strKey;
        } else {
            strKey = keyDictionaryDao.getKey(key);
            if (strKey != null) {
                keys.put(key, strKey);
            }
        }
        return strKey;
    }

    public record EntityIdInfo(EntityType entityType, TenantId tenantId) {}

}
