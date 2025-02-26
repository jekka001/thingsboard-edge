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
package org.thingsboard.server.dao.owner;

import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.HasOwnerId;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.edge.EdgeService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.scheduler.SchedulerEventService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.CacheConstants.ENTITY_OWNERS_CACHE;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;

@Service
@Slf4j
public class DefaultOwnerService implements OwnerService {

    private static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    private static final String INCORRECT_OWNER_IDS = "Incorrect ownerIds ";

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private EdgeService edgeService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private SchedulerEventService schedulerEventService;

    @Autowired
    private OwnerInfoDao ownerInfoDao;

    @Override
    public Set<EntityId> fetchOwnersHierarchy(TenantId tenantId, EntityId ownerId) {
        Set<EntityId> result = new LinkedHashSet<>();
        fetchOwnersHierarchy(tenantId, ownerId, result);
        return result;

    }

    private void fetchOwnersHierarchy(TenantId tenantId, EntityId entityId, Set<EntityId> result) {
        result.add(entityId);
        if (entityId.getEntityType() == EntityType.CUSTOMER) {
            Customer customer = getCustomerById(tenantId, entityId);
            fetchOwnersHierarchy(tenantId, customer.getOwnerId(), result);
        }
    }

    @Override
    public EntityId getOwner(TenantId tenantId, EntityId entityId) {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        String cacheKey = getOwnerCacheKey(entityId);
        byte[] data = cache.get(cacheKey, byte[].class);
        EntityId ownerId = null;
        if (data != null && data.length > 0) {
            try {
                ownerId = bytesToOwner(data);
            } catch (InvalidProtocolBufferException e) {
                log.warn("[{}][{}] Failed to decode owner id from cache: {}", tenantId, entityId, Arrays.toString(data));
            }
        }
        if (ownerId == null) {
            ownerId = fetchOwnerId(tenantId, entityId);
            cache.put(getOwnerCacheKey(entityId), toBytes(ownerId));
        }
        return ownerId;
    }

    @Override
    public Set<EntityId> getOwners(TenantId tenantId, EntityId entityId) {
        return fetchOwners(tenantId, entityId, null);
    }

    @Override
    public Set<EntityId> getOwners(TenantId tenantId, EntityId entityId, HasOwnerId hasOwnerId) {
        return fetchOwners(tenantId, entityId, id -> hasOwnerId);
    }

    @Override
    public Set<EntityId> getOwners(TenantId tenantId, EntityGroupId entityGroupId) {
        return fetchOwners(tenantId, entityGroupId, id -> entityGroupService.findEntityGroupById(tenantId, new EntityGroupId(id.getId())));
    }

    @Override
    public void clearOwners(EntityId entityId) {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        cache.evict(getOwnersCacheKey(entityId));
        cache.evict(getOwnerCacheKey(entityId));
    }

    @Override
    public PageData<EntityInfo> findTenantOwnerByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findTenantOwnerByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return this.ownerInfoDao.findTenantOwnerByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<EntityInfo> findCustomerOwnersByTenantIdIncludingTenant(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findCustomerOwnersByTenantIdIncludingTenant, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return this.ownerInfoDao.findCustomerOwnersByTenantIdIncludingTenant(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<EntityInfo> findCustomerOwnersByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findCustomerOwnersByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validatePageLink(pageLink);
        return this.ownerInfoDao.findCustomerOwnersByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<EntityInfo> findCustomerOwnersByIdsAndTenantId(TenantId tenantId, List<CustomerId> ownerIds, PageLink pageLink) {
        log.trace("Executing findCustomerOwnersByIdsAndTenantIdIncludingTenant, tenantId [{}], ownerIds [{}], pageLink [{}]", tenantId, ownerIds, pageLink);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        validateIds(ownerIds, ids -> INCORRECT_OWNER_IDS + ids);
        validatePageLink(pageLink);
        return this.ownerInfoDao.findCustomerOwnersByIdsAndTenantId(tenantId.getId(),
                ownerIds.stream().map(CustomerId::getId).collect(Collectors.toList()), pageLink);
    }

    private Set<EntityId> fetchOwners(TenantId tenantId, EntityId entityId, Function<EntityId, HasOwnerId> fetchHasOwnerId) {
        Cache cache = cacheManager.getCache(ENTITY_OWNERS_CACHE);
        String cacheKey = getOwnersCacheKey(entityId);
        byte[] data = cache.get(cacheKey, byte[].class);
        Set<EntityId> result = null;
        if (data != null && data.length > 0) {
            try {
                result = bytesToOwners(data);
            } catch (InvalidProtocolBufferException e) {
                log.warn("[{}][{}] Failed to decode owners list from cache: {}", tenantId, entityId, Arrays.toString(data));
            }
        }
        if (result == null) {
            EntityId ownerId;
            HasOwnerId hasOwnerId = fetchHasOwnerId != null ? fetchHasOwnerId.apply(entityId) : null;
            if (hasOwnerId != null) {
                ownerId = hasOwnerId.getOwnerId();
                cache.put(getOwnerCacheKey(entityId), toBytes(ownerId));
            } else {
                ownerId = getOwner(tenantId, entityId);
            }
            result = new LinkedHashSet<>();
            fetchOwnersHierarchy(tenantId, ownerId, result);
            cache.put(cacheKey, toBytes(result));
        }
        return result;
    }

    private EntityId fetchOwnerId(TenantId tenantId, EntityId entityId) {
        switch (entityId.getEntityType()) {
            case DEVICE:
                return getOwnerId(getDeviceById(tenantId, entityId));
            case ASSET:
                return getOwnerId(getAssetById(tenantId, entityId));
            case CUSTOMER:
                return getOwnerId(getCustomerById(tenantId, entityId));
            case ENTITY_VIEW:
                return getOwnerId(getEntityViewById(tenantId, entityId));
            case EDGE:
                return getOwnerId(getEdgeById(tenantId, entityId));
            case DASHBOARD:
                return getOwnerId(getDashboardById(tenantId, entityId));
            case USER:
                return getOwnerId(getUserById(tenantId, entityId));
            case ENTITY_GROUP:
                return getOwnerId(entityGroupService.findEntityGroupById(tenantId, new EntityGroupId(entityId.getId())));
            case ROLE:
                return getOwnerId(roleService.findRoleById(tenantId, new RoleId(entityId.getId())));
            case SCHEDULER_EVENT:
                return getOwnerId(schedulerEventService.findSchedulerEventById(tenantId, new SchedulerEventId(entityId.getId())));
            default:
                return tenantId;
        }
    }

    private Device getDeviceById(TenantId tenantId, EntityId entityId) {
        return deviceService.findDeviceById(tenantId, new DeviceId(entityId.getId()));
    }

    private Asset getAssetById(TenantId tenantId, EntityId entityId) {
        return assetService.findAssetById(tenantId, new AssetId(entityId.getId()));
    }

    private Customer getCustomerById(TenantId tenantId, EntityId entityId) {
        return customerService.findCustomerById(tenantId, new CustomerId(entityId.getId()));
    }

    private User getUserById(TenantId tenantId, EntityId entityId) {
        return userService.findUserById(tenantId, new UserId(entityId.getId()));
    }

    private Dashboard getDashboardById(TenantId tenantId, EntityId entityId) {
        return dashboardService.findDashboardById(tenantId, new DashboardId(entityId.getId()));
    }

    private EntityView getEntityViewById(TenantId tenantId, EntityId entityId) {
        return entityViewService.findEntityViewById(tenantId, new EntityViewId(entityId.getId()));
    }

    private Edge getEdgeById(TenantId tenantId, EntityId entityId) {
        return edgeService.findEdgeById(tenantId, new EdgeId(entityId.getId()));
    }

    private EntityId getOwnerId(HasOwnerId entity) {
        return entity != null ? entity.getOwnerId() : null;
    }

    private EntityId bytesToOwner(byte[] data) throws InvalidProtocolBufferException {
        TransportProtos.EntityIdProto proto = TransportProtos.EntityIdProto.parseFrom(data);
        return EntityIdFactory.getByTypeAndUuid(proto.getEntityType(), new UUID(proto.getEntityIdMSB(), proto.getEntityIdLSB()));
    }

    private Set<EntityId> bytesToOwners(byte[] data) throws InvalidProtocolBufferException {
        TransportProtos.OwnersListProto proto = TransportProtos.OwnersListProto.parseFrom(data);
        return proto.getEntityIdsList().stream().map(entityIdProto ->
                EntityIdFactory.getByTypeAndUuid(entityIdProto.getEntityType(),
                        new UUID(entityIdProto.getEntityIdMSB(), entityIdProto.getEntityIdLSB()))).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private byte[] toBytes(EntityId entityId) {
        return TransportProtos.EntityIdProto.newBuilder()
                .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                .setEntityType(entityId.getEntityType().name()).build().toByteArray();
    }

    private byte[] toBytes(Set<EntityId> result) {
        TransportProtos.OwnersListProto.Builder builder = TransportProtos.OwnersListProto.newBuilder();
        builder.addAllEntityIds(result.stream().map(entityId ->
                TransportProtos.EntityIdProto.newBuilder()
                        .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                        .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                        .setEntityType(entityId.getEntityType().name()).build()).collect(Collectors.toList()));
        return builder.build().toByteArray();
    }

    private String getOwnersCacheKey(EntityId entityId) {
        return ENTITY_OWNERS_CACHE + "_" + entityId.getId().toString();
    }

    private String getOwnerCacheKey(EntityId entityId) {
        return ENTITY_OWNERS_CACHE + "_owner_" + entityId.getId().toString();
    }

}
