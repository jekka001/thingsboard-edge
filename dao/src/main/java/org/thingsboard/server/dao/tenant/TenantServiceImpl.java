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
package org.thingsboard.server.dao.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.blob.BlobEntityService;
import org.thingsboard.server.dao.converter.ConverterService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.scheduler.SchedulerEventService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;

import java.util.List;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;

@Service
@Slf4j
public class TenantServiceImpl extends AbstractEntityService implements TenantService {

    private static final String DEFAULT_TENANT_REGION = "Global";
    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private UserService userService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private WidgetsBundleService widgetsBundleService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private ConverterService converterService;

    @Autowired
    private SchedulerEventService schedulerEventService;

    @Autowired
    private BlobEntityService blobEntityService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    @Override
    public Tenant findTenantById(TenantId tenantId) {
        log.trace("Executing findTenantById [{}]", tenantId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        return tenantDao.findById(tenantId, tenantId.getId());
    }

    @Override
    public ListenableFuture<Tenant> findTenantByIdAsync(TenantId callerId, TenantId tenantId) {
        log.trace("Executing TenantIdAsync [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        return tenantDao.findByIdAsync(callerId, tenantId.getId());
    }

    @Override
    public ListenableFuture<List<Tenant>> findTenantsByIdsAsync(TenantId callerId, List<TenantId> tenantIds) {
        log.trace("Executing findTenantsByIdsAsync, callerId [{}], tenantIds [{}]", callerId, tenantIds);
        validateIds(tenantIds, "Incorrect tenantIds " + tenantIds);
        return tenantDao.findTenantsByIdsAsync(callerId.getId(), toUUIDs(tenantIds));
    }

    @Override
    public Tenant saveTenant(Tenant tenant) {
        log.trace("Executing saveTenant [{}]", tenant);
        tenant.setRegion(DEFAULT_TENANT_REGION);
        tenantValidator.validate(tenant, Tenant::getId);
        Tenant savedTenant = tenantDao.save(tenant.getId(), tenant);
        if (tenant.getId() == null) {
            entityGroupService.createEntityGroupAll(savedTenant.getId(), savedTenant.getId(), EntityType.CUSTOMER);
            entityGroupService.createEntityGroupAll(savedTenant.getId(), savedTenant.getId(), EntityType.ASSET);
            entityGroupService.createEntityGroupAll(savedTenant.getId(), savedTenant.getId(), EntityType.DEVICE);
            entityGroupService.createEntityGroupAll(savedTenant.getId(), savedTenant.getId(), EntityType.ENTITY_VIEW);
            entityGroupService.createEntityGroupAll(savedTenant.getId(), savedTenant.getId(), EntityType.DASHBOARD);
            entityGroupService.createEntityGroupAll(savedTenant.getId(), savedTenant.getId(), EntityType.USER);

            entityGroupService.findOrCreateTenantUsersGroup(savedTenant.getId());
            entityGroupService.findOrCreateTenantAdminsGroup(savedTenant.getId());
        }

        return savedTenant;
    }

    @Override
    public void deleteTenant(TenantId tenantId) {
        log.trace("Executing deleteTenant [{}]", tenantId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        whiteLabelingService.deleteDomainWhiteLabelingByEntityId(tenantId, tenantId);
        customerService.deleteCustomersByTenantId(tenantId);
        widgetsBundleService.deleteWidgetsBundlesByTenantId(tenantId);
        dashboardService.deleteDashboardsByTenantId(tenantId);
        entityViewService.deleteEntityViewsByTenantId(tenantId);
        assetService.deleteAssetsByTenantId(tenantId);
        deviceService.deleteDevicesByTenantId(tenantId);
        userService.deleteTenantAdmins(tenantId);
        integrationService.deleteIntegrationsByTenantId(tenantId);
        converterService.deleteConvertersByTenantId(tenantId);
        ruleChainService.deleteRuleChainsByTenantId(tenantId);
        schedulerEventService.deleteSchedulerEventsByTenantId(tenantId);
        blobEntityService.deleteBlobEntitiesByTenantId(tenantId);
        deleteEntityGroups(tenantId, tenantId);
        deleteEntityRelations(tenantId,tenantId);
        groupPermissionService.deleteGroupPermissionsByTenantId(tenantId);
        roleService.deleteRolesByTenantId(tenantId);
        tenantDao.removeById(tenantId, tenantId.getId());
    }

    @Override
    public PageData<Tenant> findTenants(PageLink pageLink) {
        log.trace("Executing findTenants pageLink [{}]", pageLink);
        Validator.validatePageLink(pageLink);
        return tenantDao.findTenantsByRegion(new TenantId(EntityId.NULL_UUID), DEFAULT_TENANT_REGION, pageLink);
    }

    @Override
    public void deleteTenants() {
        log.trace("Executing deleteTenants");
        tenantsRemover.removeEntities(new TenantId(EntityId.NULL_UUID), DEFAULT_TENANT_REGION);
    }

    private DataValidator<Tenant> tenantValidator =
            new DataValidator<Tenant>() {
                @Override
                protected void validateDataImpl(TenantId tenantId, Tenant tenant) {
                    if (StringUtils.isEmpty(tenant.getTitle())) {
                        throw new DataValidationException("Tenant title should be specified!");
                    }
                    if (!StringUtils.isEmpty(tenant.getEmail())) {
                        validateEmail(tenant.getEmail());
                    }
                }

                @Override
                protected void validateUpdate(TenantId tenantId, Tenant tenant) {
                    Tenant old = tenantDao.findById(TenantId.SYS_TENANT_ID, tenantId.getId());
                    if (old == null) {
                        throw new DataValidationException("Can't update non existing tenant!");
                    } else if (old.isIsolatedTbRuleEngine() != tenant.isIsolatedTbRuleEngine()) {
                        throw new DataValidationException("Can't update isolatedTbRuleEngine property!");
                    } else if (old.isIsolatedTbCore() != tenant.isIsolatedTbCore()) {
                        throw new DataValidationException("Can't update isolatedTbCore property!");
                    }
                }
            };

    private PaginatedRemover<String, Tenant> tenantsRemover =
            new PaginatedRemover<String, Tenant>() {

                @Override
                protected PageData<Tenant> findEntities(TenantId tenantId, String region, PageLink pageLink) {
                    return tenantDao.findTenantsByRegion(tenantId, region, pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Tenant entity) {
                    deleteTenant(new TenantId(entity.getUuidId()));
                }
            };
}
