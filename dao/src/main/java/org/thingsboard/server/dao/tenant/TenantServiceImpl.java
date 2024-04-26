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
package org.thingsboard.server.dao.tenant;

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thingsboard.server.cache.TbTransactionalCache;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantInfo;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.TenantProfileId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.asset.AssetProfileService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.dao.entity.AbstractCachedEntityService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.notification.NotificationSettingsService;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.service.validator.TenantDataValidator;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.dao.translation.CustomTranslationService;
import org.thingsboard.server.dao.usagerecord.ApiUsageStateService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;

@Service("TenantDaoService")
@Slf4j
public class TenantServiceImpl extends AbstractCachedEntityService<TenantId, Tenant, TenantEvictEvent> implements TenantService {

    private static final String DEFAULT_TENANT_REGION = "Global";
    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";

    @Autowired
    private TenantDao tenantDao;
    @Autowired
    private TenantProfileService tenantProfileService;
    @Autowired
    @Lazy
    private UserService userService;
    @Autowired
    private AssetProfileService assetProfileService;
    @Autowired
    private DeviceProfileService deviceProfileService;
    @Lazy
    @Autowired
    private ApiUsageStateService apiUsageStateService;
    @Autowired
    private WhiteLabelingService whiteLabelingService;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private NotificationSettingsService notificationSettingsService;
    @Autowired
    private TenantDataValidator tenantValidator;
    @Autowired
    private CustomTranslationService customTranslationService;
    @Autowired
    protected TbTransactionalCache<TenantId, Boolean> existsTenantCache;

    @TransactionalEventListener(classes = TenantEvictEvent.class)
    @Override
    public void handleEvictEvent(TenantEvictEvent event) {
        TenantId tenantId = event.getTenantId();
        cache.evict(tenantId);
        if (event.isInvalidateExists()) {
            existsTenantCache.evict(tenantId);
        }
    }

    @Override
    public Tenant findTenantById(TenantId tenantId) {
        log.trace("Executing findTenantById [{}]", tenantId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);

        return cache.getAndPutInTransaction(tenantId, () -> tenantDao.findById(tenantId, tenantId.getId()), true);
    }

    @Override
    public TenantInfo findTenantInfoById(TenantId tenantId) {
        log.trace("Executing findTenantInfoById [{}]", tenantId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        return tenantDao.findTenantInfoById(tenantId, tenantId.getId());
    }

    @Override
    public ListenableFuture<Tenant> findTenantByIdAsync(TenantId callerId, TenantId tenantId) {
        log.trace("Executing findTenantByIdAsync [{}]", tenantId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        return tenantDao.findByIdAsync(callerId, tenantId.getId());
    }

    @Override
    public ListenableFuture<List<Tenant>> findTenantsByIdsAsync(TenantId callerId, List<TenantId> tenantIds) {
        log.trace("Executing findTenantsByIdsAsync, callerId [{}], tenantIds [{}]", callerId, tenantIds);
        validateIds(tenantIds, ids -> "Incorrect tenantIds " + ids);
        return tenantDao.findTenantsByIdsAsync(callerId.getId(), toUUIDs(tenantIds));
    }

    @Transactional
    public Tenant saveTenant(Tenant tenant) {
        return saveTenant(tenant, null, true);
    }

    @Override
    @Transactional
    public Tenant saveTenant(Tenant tenant, Consumer<TenantId> defaultEntitiesCreator, boolean doValidate) {
        log.trace("Executing saveTenant [{}]", tenant);
        tenant.setRegion(DEFAULT_TENANT_REGION);
        if (tenant.getTenantProfileId() == null) {
            TenantProfile tenantProfile = this.tenantProfileService.findOrCreateDefaultTenantProfile(TenantId.SYS_TENANT_ID);
            tenant.setTenantProfileId(tenantProfile.getId());
        }
        if (doValidate) {
            tenantValidator.validate(tenant, Tenant::getId);
        }
        boolean create = tenant.getId() == null;

        Tenant savedTenant = tenantDao.save(tenant.getId(), tenant);
        TenantId tenantId = savedTenant.getId();
        publishEvictEvent(new TenantEvictEvent(tenantId, create));
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId)
                .entityId(tenantId).entity(savedTenant).created(create).build());

        if (create) {
            deviceProfileService.createDefaultDeviceProfile(tenantId);
            assetProfileService.createDefaultAssetProfile(tenantId);

            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.CUSTOMER);
            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.ASSET);
            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.DEVICE);
            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.ENTITY_VIEW);
            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.EDGE);
            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.DASHBOARD);
            entityGroupService.createEntityGroupAll(tenantId, tenantId, EntityType.USER);

            entityGroupService.findOrCreateTenantUsersGroup(tenantId);
            entityGroupService.findOrCreateTenantAdminsGroup(tenantId);
            apiUsageStateService.createDefaultApiUsageState(tenantId, null);
            notificationSettingsService.createDefaultNotificationConfigs(tenantId);

            if (defaultEntitiesCreator != null) {
                defaultEntitiesCreator.accept(tenantId);
            }
        }
        return savedTenant;
    }

    @Override
    public void deleteTenant(TenantId tenantId) {
        log.trace("Executing deleteTenant [{}]", tenantId);
        Tenant tenant = findTenantById(tenantId);
        Validator.validateId(tenantId, id -> INCORRECT_TENANT_ID + id);

        userService.deleteAllByTenantId(tenantId);
        whiteLabelingService.deleteDomainWhiteLabelingByEntityId(tenantId, null);
        customTranslationService.deleteCustomTranslationByTenantId(tenantId);
        adminSettingsService.deleteAdminSettingsByTenantId(tenantId);
        notificationSettingsService.deleteNotificationSettings(tenantId);
        tenantDao.removeById(tenantId, tenantId.getId());
        publishEvictEvent(new TenantEvictEvent(tenantId, true));

        cleanUpService.removeTenantEntities(tenantId, // don't forget to implement deleteByTenantId from EntityDaoService when adding entity type to this list
                EntityType.ENTITY_VIEW, EntityType.WIDGETS_BUNDLE, EntityType.WIDGET_TYPE,
                EntityType.ASSET, EntityType.ASSET_PROFILE, EntityType.DEVICE, EntityType.DEVICE_PROFILE,
                EntityType.DASHBOARD, EntityType.EDGE, EntityType.RULE_CHAIN, EntityType.INTEGRATION,
                EntityType.CONVERTER, EntityType.SCHEDULER_EVENT, EntityType.BLOB_ENTITY, EntityType.ENTITY_GROUP,
                EntityType.GROUP_PERMISSION, EntityType.ROLE, EntityType.API_USAGE_STATE, EntityType.TB_RESOURCE,
                EntityType.OTA_PACKAGE, EntityType.RPC, EntityType.QUEUE, EntityType.NOTIFICATION_REQUEST,
                EntityType.NOTIFICATION_RULE, EntityType.NOTIFICATION_TEMPLATE, EntityType.NOTIFICATION_TARGET,
                EntityType.QUEUE_STATS, EntityType.CUSTOMER
        );
        eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(tenantId).entity(tenant).build());
    }

    @Override
    public PageData<Tenant> findTenants(PageLink pageLink) {
        log.trace("Executing findTenants pageLink [{}]", pageLink);
        Validator.validatePageLink(pageLink);
        return tenantDao.findTenants(TenantId.SYS_TENANT_ID, pageLink);
    }

    @Override
    public PageData<TenantInfo> findTenantInfos(PageLink pageLink) {
        log.trace("Executing findTenantInfos pageLink [{}]", pageLink);
        Validator.validatePageLink(pageLink);
        return tenantDao.findTenantInfos(TenantId.SYS_TENANT_ID, pageLink);
    }

    @Override
    public List<TenantId> findTenantIdsByTenantProfileId(TenantProfileId tenantProfileId) {
        log.trace("Executing findTenantsByTenantProfileId [{}]", tenantProfileId);
        return tenantDao.findTenantIdsByTenantProfileId(tenantProfileId);
    }

    @Override
    public void deleteTenants() {
        log.trace("Executing deleteTenants");
        tenantsRemover.removeEntities(TenantId.SYS_TENANT_ID, TenantId.SYS_TENANT_ID);
    }

    @Override
    public PageData<TenantId> findTenantsIds(PageLink pageLink) {
        log.trace("Executing findTenantsIds");
        Validator.validatePageLink(pageLink);
        return tenantDao.findTenantsIds(pageLink);
    }

    @Override
    public boolean tenantExists(TenantId tenantId) {
        return existsTenantCache.getAndPutInTransaction(tenantId, () -> tenantDao.existsById(tenantId, tenantId.getId()), false);
    }

    private PaginatedRemover<TenantId, Tenant> tenantsRemover = new PaginatedRemover<>() {

        @Override
        protected PageData<Tenant> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
            return tenantDao.findTenants(tenantId, pageLink);
        }

        @Override
        protected void removeEntity(TenantId tenantId, Tenant entity) {
            deleteTenant(TenantId.fromUUID(entity.getUuidId()));
        }
    };

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findTenantById(new TenantId(entityId.getId())));
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.TENANT;
    }

}
