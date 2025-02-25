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
package org.thingsboard.server.dao.entity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.GroupEntity;
import org.thingsboard.server.common.data.HasCustomerId;
import org.thingsboard.server.common.data.HasEmail;
import org.thingsboard.server.common.data.HasLabel;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTitle;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.edqs.query.EdqsRequest;
import org.thingsboard.server.common.data.edqs.query.EdqsResponse;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.NameLabelAndCustomerDetails;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.MergedGroupTypePermissionInfo;
import org.thingsboard.server.common.data.permission.MergedUserPermissions;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.query.EntityCountQuery;
import org.thingsboard.server.common.data.query.EntityData;
import org.thingsboard.server.common.data.query.EntityDataPageLink;
import org.thingsboard.server.common.data.query.EntityDataQuery;
import org.thingsboard.server.common.data.query.EntityFilterType;
import org.thingsboard.server.common.data.query.EntityGroupListFilter;
import org.thingsboard.server.common.data.query.EntityGroupNameFilter;
import org.thingsboard.server.common.data.query.EntityKey;
import org.thingsboard.server.common.data.query.EntityListFilter;
import org.thingsboard.server.common.data.query.EntityTypeFilter;
import org.thingsboard.server.common.data.query.KeyFilter;
import org.thingsboard.server.common.data.query.RelationsQueryFilter;
import org.thingsboard.server.common.data.query.StateEntityOwnerFilter;
import org.thingsboard.server.common.msg.edqs.EdqsService;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.sql.alarm.AlarmRepository;
import org.thingsboard.server.dao.sql.query.EntityMapping;
import org.thingsboard.server.dao.user.UserService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.query.EntityFilterType.ENTITY_GROUP_NAME;
import static org.thingsboard.server.common.data.query.EntityFilterType.ENTITY_TYPE;
import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;
import static org.thingsboard.server.dao.service.Validator.validateEntityDataPageLink;
import static org.thingsboard.server.dao.service.Validator.validateId;

/**
 * Created by ashvayka on 04.05.17.
 */
@Service
@Slf4j
public class BaseEntityService extends AbstractEntityService implements EntityService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final CustomerId NULL_CUSTOMER_ID = new CustomerId(NULL_UUID);

    private static final int MAX_ENTITY_IDS_SIZE = 1024;
    private static final Set<EntityFilterType> EXCLUDED_TYPES_FROM_OPTIMIZATION = Set.of(
            EntityFilterType.ENTITY_LIST, EntityFilterType.SINGLE_ENTITY, EntityFilterType.RELATIONS_QUERY, EntityFilterType.ENTITY_GROUP_LIST);

    @Autowired
    private AssetService assetService;

    @Autowired
    private AlarmRepository alarmRepository;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private UserService userService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EntityQueryDao entityQueryDao;

    @Autowired
    private EdgeService edgeService;

    @Autowired
    @Lazy
    EntityServiceRegistry entityServiceRegistry;

    @Autowired @Lazy
    private EdqsService edqsService;

    @Override
    public <T extends GroupEntity<? extends EntityId>> PageData<T> findUserEntities(TenantId tenantId, CustomerId customerId,
                                                                                    MergedUserPermissions userPermissions,
                                                                                    EntityType entityType, Operation operation, String type, PageLink pageLink) {
        return findUserEntities(tenantId, customerId, userPermissions, entityType, operation, type, pageLink, false, false);
    }

    @Override
    public <T extends GroupEntity<? extends EntityId>> PageData<T> findUserEntities(TenantId tenantId, CustomerId customerId,
                                                                                    MergedUserPermissions userPermissions,
                                                                                    EntityType entityType, Operation operation, String type, PageLink pageLink, boolean mobile, boolean idOnly) {
        MergedGroupTypePermissionInfo groupPermissions = userPermissions.getGroupPermissionsByEntityTypeAndOperation(entityType, operation);
        if (customerId == null || customerId.isNullUid()) {
            if (groupPermissions.isHasGenericRead()) {
                return getEntityPageDataByTenantId(entityType, type, tenantId, pageLink, mobile);
            } else {
                return getEntityPageDataByGroupIds(entityType, type, groupPermissions.getEntityGroupIds(), pageLink, mobile);
            }
        } else {
            if (groupPermissions.isHasGenericRead()) {
                if (groupPermissions.getEntityGroupIds().isEmpty()) {
                    return getEntityPageDataByCustomerId(entityType, type, tenantId, customerId, pageLink, mobile, idOnly);
                } else {
                    return getEntityPageDataByCustomerIdOrOtherGroupIds(entityType, type, tenantId, customerId, groupPermissions.getEntityGroupIds(), pageLink, mobile, idOnly);
                }
            } else {
                return getEntityPageDataByGroupIds(entityType, type, groupPermissions.getEntityGroupIds(), pageLink, mobile);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends GroupEntity<? extends EntityId>> PageData<T> getEntityPageDataByTenantId(EntityType entityType, String type, TenantId tenantId, PageLink pageLink, boolean mobile) {
        switch (entityType) {
            case DEVICE:
                if (type != null && type.trim().length() > 0) {
                    return (PageData<T>) deviceService.findDevicesByTenantIdAndType(tenantId, type, pageLink);
                } else {
                    return (PageData<T>) deviceService.findDevicesByTenantId(tenantId, pageLink);
                }
            case ASSET:
                if (type != null && type.trim().length() > 0) {
                    return (PageData<T>) assetService.findAssetsByTenantIdAndType(tenantId, type, pageLink);
                } else {
                    return (PageData<T>) assetService.findAssetsByTenantId(tenantId, pageLink);
                }
            case ENTITY_VIEW:
                if (type != null && type.trim().length() > 0) {
                    return (PageData<T>) entityViewService.findEntityViewByTenantIdAndType(tenantId, pageLink, type);
                } else {
                    return (PageData<T>) entityViewService.findEntityViewByTenantId(tenantId, pageLink);
                }
            case EDGE:
                if (type != null && type.trim().length() > 0) {
                    return (PageData<T>) edgeService.findEdgesByTenantIdAndType(tenantId, type, pageLink);
                } else {
                    return (PageData<T>) edgeService.findEdgesByTenantId(tenantId, pageLink);
                }
            case DASHBOARD:
                if (mobile) {
                    return (PageData<T>) dashboardService.findMobileDashboardsByTenantId(tenantId, pageLink);
                } else {
                    return (PageData<T>) dashboardService.findDashboardsByTenantId(tenantId, pageLink);
                }
            case CUSTOMER:
                return (PageData<T>) customerService.findCustomersByTenantId(tenantId, pageLink);
            case USER:
                return (PageData<T>) userService.findUsersByTenantId(tenantId, pageLink);
            default:
                return new PageData<>();
        }
    }

    private <T extends GroupEntity<? extends EntityId>> PageData<T> getEntityPageDataByCustomerId(EntityType entityType, String type, TenantId tenantId, CustomerId customerId, PageLink pageLink, boolean mobile, boolean idOnly) {
        return getEntityPageDataByCustomerIdOrOtherGroupIds(entityType, type, tenantId, customerId, Collections.emptyList(), pageLink, mobile, idOnly);
    }

    @SuppressWarnings("unchecked")
    private <T extends GroupEntity<? extends EntityId>> PageData<T> getEntityPageDataByCustomerIdOrOtherGroupIds(
            EntityType entityType, String type, TenantId tenantId, CustomerId customerId, List<EntityGroupId> groupIds, PageLink pageLink, boolean mobile, boolean idOnly) {
        if (type != null && type.trim().length() == 0) {
            type = null;
        }
        EntityMapping<?, ?> mapping = EntityMapping.get(entityType);
        if (idOnly) {
            mapping = mapping.onlyId();
        }
        return (PageData<T>) entityQueryDao.findInCustomerHierarchyByRootCustomerIdOrOtherGroupIdsAndType(
                tenantId, customerId, entityType, type, groupIds, pageLink, mapping, mobile);
    }

    @SuppressWarnings("unchecked")
    private <T extends GroupEntity<? extends EntityId>> PageData<T> getEntityPageDataByGroupIds(EntityType entityType, String type,
                                                                                                List<EntityGroupId> groupIds, PageLink pageLink, boolean mobile) {
        if (!groupIds.isEmpty()) {
            switch (entityType) {
                case DEVICE:
                    if (type != null && type.trim().length() > 0) {
                        return (PageData<T>) deviceService.findDevicesByEntityGroupIdsAndType(groupIds, type, pageLink);
                    } else {
                        return (PageData<T>) deviceService.findDevicesByEntityGroupIds(groupIds, pageLink);
                    }
                case ASSET:
                    if (type != null && type.trim().length() > 0) {
                        return (PageData<T>) assetService.findAssetsByEntityGroupIdsAndType(groupIds, type, pageLink);
                    } else {
                        return (PageData<T>) assetService.findAssetsByEntityGroupIds(groupIds, pageLink);
                    }
                case ENTITY_VIEW:
                    if (type != null && type.trim().length() > 0) {
                        return (PageData<T>) entityViewService.findEntityViewsByEntityGroupIdsAndType(groupIds, type, pageLink);
                    } else {
                        return (PageData<T>) entityViewService.findEntityViewsByEntityGroupIds(groupIds, pageLink);
                    }
                case EDGE:
                    if (type != null && type.trim().length() > 0) {
                        return (PageData<T>) edgeService.findEdgesByEntityGroupIdsAndType(groupIds, type, pageLink);
                    } else {
                        return (PageData<T>) edgeService.findEdgesByEntityGroupIds(groupIds, pageLink);
                    }
                case DASHBOARD:
                    if (mobile) {
                        return (PageData<T>) dashboardService.findMobileDashboardsByEntityGroupIds(groupIds, pageLink);
                    } else {
                        return (PageData<T>) dashboardService.findDashboardsByEntityGroupIds(groupIds, pageLink);
                    }
                case CUSTOMER:
                    return (PageData<T>) customerService.findCustomersByEntityGroupIds(groupIds, Collections.emptyList(), pageLink);
                case USER:
                    return (PageData<T>) userService.findUsersByEntityGroupIds(groupIds, pageLink);
            }
        }
        return new PageData<>();
    }

    @Override
    public long countEntitiesByQuery(TenantId tenantId, CustomerId customerId, MergedUserPermissions userPermissions, EntityCountQuery query) {
        log.trace("Executing countEntitiesByQuery, tenantId [{}], customerId [{}], query [{}]", tenantId, customerId, query);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(customerId, id -> INCORRECT_CUSTOMER_ID + id);
        validateEntityCountQuery(query);

        if (edqsService.isApiEnabled() && validForEdqs(query) && !tenantId.isSysTenantId()) {
            EdqsRequest request = EdqsRequest.builder()
                    .entityCountQuery(query)
                    .userPermissions(userPermissions)
                    .build();
            EdqsResponse response = processEdqsRequest(tenantId, customerId, request);
            return response.getEntityCountQueryResult();
        }
        return this.entityQueryDao.countEntitiesByQuery(tenantId, customerId, userPermissions, query);
    }

    @Override
    public PageData<EntityData> findEntityDataByQuery(TenantId tenantId, CustomerId customerId, MergedUserPermissions userPermissions, EntityDataQuery query) {
        log.trace("Executing findEntityDataByQuery, tenantId [{}], customerId [{}], query [{}]", tenantId, customerId, query);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateId(customerId, id -> INCORRECT_CUSTOMER_ID + id);
        validateEntityDataQuery(query);

        if (edqsService.isApiEnabled() && validForEdqs(query) && !tenantId.isSysTenantId()) {
            EdqsRequest request = EdqsRequest.builder()
                    .entityDataQuery(query)
                    .userPermissions(userPermissions)
                    .build();
            EdqsResponse response = processEdqsRequest(tenantId, customerId, request);
            return response.getEntityDataQueryResult();
        }

        if (!isValidForOptimization(query)) {
            return this.entityQueryDao.findEntityDataByQuery(tenantId, customerId, userPermissions, query);
        }

        // 1 step - find entity data by filter and sort columns
        PageData<EntityData> entityDataByQuery = findEntityIdsByFilterAndSorterColumns(tenantId, customerId, userPermissions, query);
        if (entityDataByQuery == null || entityDataByQuery.getData().isEmpty()) {
            return entityDataByQuery;
        }

        // 2 step - find entity data by entity ids from the 1st step
        List<EntityData> result = fetchEntityDataByIdsFromInitialQuery(tenantId, customerId, query, userPermissions, entityDataByQuery.getData());
        return new PageData<>(result, entityDataByQuery.getTotalPages(), entityDataByQuery.getTotalElements(), entityDataByQuery.hasNext());
    }

    private boolean validForEdqs(EntityCountQuery query) {
        return !(query.getEntityFilter() instanceof StateEntityOwnerFilter filter) || !EntityType.ALARM.equals(filter.getSingleEntity().getEntityType());
    }

    private EdqsResponse processEdqsRequest(TenantId tenantId, CustomerId customerId, EdqsRequest request) {
        EdqsResponse response;
        try {
            log.debug("[{}] Sending request to EDQS: {}", tenantId, request);
            response = edqsService.processRequest(tenantId, customerId, request).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        log.debug("[{}] Received response from EDQS: {}", tenantId, response);
        if (response.getError() != null) {
            throw new RuntimeException(response.getError());
        }
        return response;
    }

    @Override
    public Optional<String> fetchEntityName(TenantId tenantId, EntityId entityId) {
        log.trace("Executing fetchEntityName [{}]", entityId);
        return fetchAndConvert(tenantId, entityId, this::getName);
    }

    @Override
    public Optional<String> fetchEntityLabel(TenantId tenantId, EntityId entityId) {
        log.trace("Executing fetchEntityLabel [{}]", entityId);
        return fetchAndConvert(tenantId, entityId, this::getLabel);
    }

    @Override
    public Optional<CustomerId> fetchEntityCustomerId(TenantId tenantId, EntityId entityId) {
        log.trace("Executing fetchEntityCustomerId [{}]", entityId);
        return fetchAndConvert(tenantId, entityId, this::getCustomerId);
    }

    @Override
    public Optional<NameLabelAndCustomerDetails> fetchNameLabelAndCustomerDetails(TenantId tenantId, EntityId entityId) {
        log.trace("Executing fetchNameLabelAndCustomerDetails [{}]", entityId);
        return fetchAndConvert(tenantId, entityId, this::getNameLabelAndCustomerDetails);
    }

    private <T> Optional<T> fetchAndConvert(TenantId tenantId, EntityId entityId, Function<HasId<?>, T> converter) {
        EntityDaoService entityDaoService = entityServiceRegistry.getServiceByEntityType(entityId.getEntityType());
        Optional<HasId<?>> entityOpt = entityDaoService.findEntity(tenantId, entityId);
        return entityOpt.map(converter);
    }

    private String getName(HasId<?> entity) {
        return entity instanceof HasName ? ((HasName) entity).getName() : null;
    }

    private String getLabel(HasId<?> entity) {
        if (entity instanceof HasTitle && StringUtils.isNotEmpty(((HasTitle) entity).getTitle())) {
            return ((HasTitle) entity).getTitle();
        }
        if (entity instanceof HasLabel && StringUtils.isNotEmpty(((HasLabel) entity).getLabel())) {
            return ((HasLabel) entity).getLabel();
        }
        if (entity instanceof HasEmail && StringUtils.isNotEmpty(((HasEmail) entity).getEmail())) {
            return ((HasEmail) entity).getEmail();
        }
        if (entity instanceof HasName && StringUtils.isNotEmpty(((HasName) entity).getName())) {
            return ((HasName) entity).getName();
        }
        return null;
    }

    private CustomerId getCustomerId(HasId<?> entity) {
        if (entity instanceof HasCustomerId hasCustomerId) {
            CustomerId customerId = hasCustomerId.getCustomerId();
            if (customerId == null) {
                customerId = NULL_CUSTOMER_ID;
            }
            return customerId;
        }
        return NULL_CUSTOMER_ID;
    }

    private NameLabelAndCustomerDetails getNameLabelAndCustomerDetails(HasId<?> entity) {
        return new NameLabelAndCustomerDetails(getName(entity), getLabel(entity), getCustomerId(entity));
    }

    private static void validateEntityCountQuery(EntityCountQuery query) {
        if (query == null) {
            throw new IncorrectParameterException("Query must be specified.");
        } else if (query.getEntityFilter() == null) {
            throw new IncorrectParameterException("Query entity filter must be specified.");
        } else if (query.getEntityFilter().getType() == null) {
            throw new IncorrectParameterException("Query entity filter type must be specified.");
        } else if (query.getEntityFilter().getType().equals(EntityFilterType.RELATIONS_QUERY)) {
            validateRelationQuery((RelationsQueryFilter) query.getEntityFilter());
        } else if (query.getEntityFilter().getType().equals(ENTITY_TYPE)) {
            validateEntityTypeQuery((EntityTypeFilter) query.getEntityFilter());
        } else if (query.getEntityFilter().getType().equals(ENTITY_GROUP_NAME)) {
            validateGroupNameQuery((EntityGroupNameFilter) query.getEntityFilter());
        }
    }

    private static void validateEntityDataQuery(EntityDataQuery query) {
        validateEntityCountQuery(query);
        validateEntityDataPageLink(query.getPageLink());
    }

    private static void validateEntityTypeQuery(EntityTypeFilter filter) {
        if (filter.getEntityType() == null) {
            throw new IncorrectParameterException("Entity type is required");
        }
    }

    private static void validateGroupNameQuery(EntityGroupNameFilter filter) {
        if (filter.getGroupType() == null) {
            throw new IncorrectParameterException("Group type is required");
        }
    }

    private static void validateRelationQuery(RelationsQueryFilter queryFilter) {
        if (queryFilter.isMultiRoot() && queryFilter.getMultiRootEntitiesType() == null) {
            throw new IncorrectParameterException("Multi-root relation query filter should contain 'multiRootEntitiesType'");
        }
        if (queryFilter.isMultiRoot() && CollectionUtils.isEmpty(queryFilter.getMultiRootEntityIds())) {
            throw new IncorrectParameterException("Multi-root relation query filter should contain 'multiRootEntityIds' array that contains string representation of UUIDs");
        }
        if (!queryFilter.isMultiRoot() && queryFilter.getRootEntity() == null) {
            throw new IncorrectParameterException("Relation query filter root entity should not be blank");
        }
    }

    private boolean isValidForOptimization(EntityDataQuery query) {
        if (StringUtils.isNotEmpty(query.getPageLink().getTextSearch())) {
            return false;
        }

        if (EXCLUDED_TYPES_FROM_OPTIMIZATION.contains(query.getEntityFilter().getType())) {
            return false;
        }

        if ((query.getEntityFields() == null || query.getEntityFields().isEmpty()) &&
                (query.getLatestValues() == null || query.getLatestValues().isEmpty())) {
            return false;
        }

        Set<EntityKey> filteringKeys = new HashSet<>(Optional.ofNullable(query.getKeyFilters()).orElse(Collections.emptyList()).stream().map(KeyFilter::getKey).toList());
        Set<EntityKey> entityFields = new HashSet<>(Optional.ofNullable(query.getEntityFields()).orElse(Collections.emptyList()));
        Set<EntityKey> latestValues = new HashSet<>(Optional.ofNullable(query.getLatestValues()).orElse(Collections.emptyList()));

        return !(filteringKeys.containsAll(entityFields) && filteringKeys.containsAll(latestValues));
    }

    private PageData<EntityData> findEntityIdsByFilterAndSorterColumns(TenantId tenantId, CustomerId customerId,
                                                                       MergedUserPermissions userPermissions, EntityDataQuery query) {
        List<EntityKey> entityFields = null;
        List<EntityKey> latestValues = null;
        if (query.getPageLink().getSortOrder() != null) {
            if (query.getEntityFields() != null) {
                entityFields = query.getEntityFields().stream()
                        .filter(entityKey -> entityKey.getKey().equals(query.getPageLink().getSortOrder().getKey().getKey()))
                        .collect(Collectors.toList());
            }
            if (query.getLatestValues() != null) {
                latestValues = query.getLatestValues().stream()
                        .filter(entityKey -> entityKey.getKey().equals(query.getPageLink().getSortOrder().getKey().getKey()))
                        .collect(Collectors.toList());
            }
        }
        EntityDataQuery entityQuery = new EntityDataQuery(query.getEntityFilter(), query.getPageLink(), entityFields, latestValues, query.getKeyFilters());
        return this.entityQueryDao.findEntityDataByQuery(tenantId, customerId, userPermissions, entityQuery);
    }

    private List<EntityData> fetchEntityDataByIdsFromInitialQuery(TenantId tenantId, CustomerId customerId, EntityDataQuery query,
                                                                  MergedUserPermissions userPermissions, List<EntityData> initialQueryResult) {
        List<EntityData> result = new ArrayList<>();

        List<String> entityIds = initialQueryResult.stream().map(d -> d.getEntityId().getId().toString()).collect(Collectors.toList());
        EntityType entityType = initialQueryResult.get(0).getEntityId().getEntityType();

        if (entityIds.size() > MAX_ENTITY_IDS_SIZE) {
            List<List<String>> chunks = new ArrayList<>();
            for (int i = 0; i < entityIds.size(); i += MAX_ENTITY_IDS_SIZE) {
                chunks.add(entityIds.subList(i, Math.min(entityIds.size(), i + MAX_ENTITY_IDS_SIZE)));
            }
            for (List<String> chunk : chunks) {
                result.addAll(findEntityDataByEntityIds(tenantId, customerId, query, userPermissions, chunk, entityType, chunk.size()));
            }
        } else {
            result.addAll(findEntityDataByEntityIds(tenantId, customerId, query, userPermissions, entityIds, entityType, query.getPageLink().getPageSize()));
        }
        return result;
    }

    private List<EntityData> findEntityDataByEntityIds(TenantId tenantId, CustomerId customerId, EntityDataQuery query, MergedUserPermissions userPermissions,
                                                       List<String> entityIds, EntityType entityType, int pageSize) {
        EntityDataQuery entityQuery;
        EntityDataPageLink pageLink = new EntityDataPageLink(pageSize, 0, null, query.getPageLink().getSortOrder());

        if (EntityFilterType.ENTITY_GROUP_NAME.equals(query.getEntityFilter().getType())) {
            EntityGroupListFilter filter = new EntityGroupListFilter();
            filter.setGroupType(((EntityGroupNameFilter) query.getEntityFilter()).getGroupType());
            filter.setEntityGroupList(entityIds);

            entityQuery = new EntityDataQuery(filter, pageLink, query.getEntityFields(), query.getLatestValues(), null);
        } else {
            EntityListFilter filter = new EntityListFilter();
            filter.setEntityType(entityType);
            filter.setEntityList(entityIds);

            entityQuery = new EntityDataQuery(filter, pageLink, query.getEntityFields(), query.getLatestValues(), null);
        }
        return this.entityQueryDao.findEntityDataByQuery(tenantId, customerId, userPermissions, entityQuery).getData();
    }

}
