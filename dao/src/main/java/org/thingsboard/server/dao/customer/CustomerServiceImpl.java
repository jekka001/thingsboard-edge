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
package org.thingsboard.server.dao.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.blob.BlobEntityService;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.exception.IncorrectParameterException;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.dao.scheduler.SchedulerEventService;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.PaginatedRemover;
import org.thingsboard.server.dao.service.Validator;
import org.thingsboard.server.dao.tenant.TenantDao;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.DaoUtil.toUUIDs;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateIds;
import static org.thingsboard.server.dao.service.Validator.validatePageLink;

@Service
@Slf4j
public class CustomerServiceImpl extends AbstractEntityService implements CustomerService {

    private static final String PUBLIC_CUSTOMER_TITLE = "Public";
    public static final String INCORRECT_CUSTOMER_ID = "Incorrect customerId ";
    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_OWNER_ID = "Incorrect ownerId ";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private CustomerDao customerDao;

    @Autowired
    private UserService userService;

    @Autowired
    private TenantDao tenantDao;

    @Autowired
    private AssetService assetService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    @Autowired
    private SchedulerEventService schedulerEventService;

    @Autowired
    private BlobEntityService blobEntityService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Override
    public Customer findCustomerById(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing findCustomerById [{}]", customerId);
        Validator.validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        return customerDao.findById(tenantId, customerId.getId());
    }

    @Override
    public Optional<Customer> findCustomerByTenantIdAndTitle(TenantId tenantId, String title) {
        log.trace("Executing findCustomerByTenantIdAndTitle [{}] [{}]", tenantId, title);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        return customerDao.findCustomersByTenantIdAndTitle(tenantId.getId(), title);
    }

    @Override
    public ListenableFuture<Customer> findCustomerByIdAsync(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing findCustomerByIdAsync [{}]", customerId);
        validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        return customerDao.findByIdAsync(tenantId, customerId.getId());
    }

    @Override
    public ListenableFuture<List<Customer>> findCustomersByTenantIdAndIdsAsync(TenantId tenantId, List<CustomerId> customerIds) {
        log.trace("Executing findCustomersByTenantIdAndIdsAsync, tenantId [{}], customerIds [{}]", tenantId, customerIds);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        validateIds(customerIds, "Incorrect customerIds " + customerIds);
        return customerDao.findCustomersByTenantIdAndIdsAsync(tenantId.getId(), toUUIDs(customerIds));
    }

    @Override
    public Customer saveCustomer(Customer customer) {
        log.trace("Executing saveCustomer [{}]", customer);
        customerValidator.validate(customer, Customer::getTenantId);
        return saveCustomerInternal(customer);
    }

    private Customer saveCustomerInternal(Customer customer) {
        Customer savedCustomer = customerDao.save(customer.getTenantId(), customer);
        if (customer.getId() == null) {
            entityGroupService.addEntityToEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getOwnerId(), savedCustomer.getId());
            entityGroupService.createEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getId(), EntityType.CUSTOMER);
            entityGroupService.createEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getId(), EntityType.ASSET);
            entityGroupService.createEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getId(), EntityType.DEVICE);
            entityGroupService.createEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getId(), EntityType.ENTITY_VIEW);
            entityGroupService.createEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getId(), EntityType.DASHBOARD);
            entityGroupService.createEntityGroupAll(savedCustomer.getTenantId(), savedCustomer.getId(), EntityType.USER);

            if (!customer.isPublic()) {
                entityGroupService.findOrCreateCustomerUsersGroup(savedCustomer.getTenantId(), savedCustomer.getId(), savedCustomer.getParentCustomerId());
                entityGroupService.findOrCreateCustomerAdminsGroup(savedCustomer.getTenantId(), savedCustomer.getId(), savedCustomer.getParentCustomerId());
            } else {
                entityGroupService.findOrCreatePublicUsersGroup(savedCustomer.getTenantId(), savedCustomer.getId());
            }
        }
        return savedCustomer;
    }

    @Override
    public void deleteCustomer(TenantId tenantId, CustomerId customerId) {
        log.trace("Executing deleteCustomer [{}]", customerId);
        Validator.validateId(customerId, INCORRECT_CUSTOMER_ID + customerId);
        deleteCustomer(tenantId, customerId, true);
    }

    private void deleteCustomer(TenantId tenantId, CustomerId customerId, boolean deleteSubcustomers) {
        Customer customer = findCustomerById(tenantId, customerId);
        if (customer == null) {
            throw new IncorrectParameterException("Unable to delete non-existent customer.");
        }
        if (deleteSubcustomers) {
            try {
                List<CustomerId> customerIds = fetchSubcustomers(tenantId, customerId);
                for (CustomerId subCustomerId : customerIds) {
                    deleteCustomer(tenantId, subCustomerId, true);
                }
            } catch (Exception e) {
                log.error("Failed to delete subcustomers", e);
                throw new RuntimeException(e);
            }
        }
        whiteLabelingService.deleteDomainWhiteLabelingByEntityId(tenantId, customerId);
        dashboardService.deleteDashboardsByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        entityViewService.deleteEntityViewsByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        assetService.deleteAssetsByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        deviceService.deleteDevicesByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        userService.deleteCustomerUsers(customer.getTenantId(), customerId);
        schedulerEventService.deleteSchedulerEventsByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        blobEntityService.deleteBlobEntitiesByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        deleteEntityGroups(tenantId, customerId);
        deleteEntityRelations(tenantId, customerId);
        roleService.deleteRolesByTenantIdAndCustomerId(customer.getTenantId(), customerId);
        customerDao.removeById(tenantId, customerId.getId());
    }

    private List<CustomerId> fetchSubcustomers(TenantId tenantId, CustomerId customerId) throws Exception {
        List<CustomerId> customerIds = new ArrayList<>();
        Optional<EntityGroup> entityGroup = entityGroupService.findEntityGroupByTypeAndName(tenantId, customerId, EntityType.CUSTOMER, EntityGroup.GROUP_ALL_NAME).get();
        if (entityGroup.isPresent()) {
            List<EntityId> childCustomerIds = entityGroupService.findAllEntityIds(tenantId, entityGroup.get().getId(), new TimePageLink(Integer.MAX_VALUE)).get();
            childCustomerIds.forEach(entityId -> customerIds.add(new CustomerId(entityId.getId())));
        }
        return customerIds;
    }

    @Override
    public Customer findOrCreatePublicCustomer(TenantId tenantId, EntityId ownerId) {
        log.trace("Executing findOrCreatePublicCustomer, tenantId [{}], ownerId [{}]", tenantId, ownerId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validateEntityId(ownerId, INCORRECT_OWNER_ID + ownerId);
        try {
            Optional<EntityGroup> entityGroup = entityGroupService.findEntityGroupByTypeAndName(tenantId, ownerId,
                    EntityType.CUSTOMER, EntityGroup.GROUP_ALL_NAME).get();
            if (entityGroup.isPresent()) {
                Customer publicCustomer = null;
                PageLink pageLink = new PageLink(100);
                PageData<Customer> customers;
                do {
                    customers = findCustomersByEntityGroupId(entityGroup.get().getId(), pageLink);
                    List<Customer> result = customers.getData().stream().filter(customer -> customer.isPublic()).collect(Collectors.toList());
                    if (!result.isEmpty()) {
                        publicCustomer = result.get(0);
                    } else if (customers.hasNext()) {
                        pageLink = pageLink.nextPageLink();
                    }
                } while (customers.hasNext() && publicCustomer == null);
                if (publicCustomer == null) {
                    publicCustomer = new Customer();
                    publicCustomer.setTenantId(tenantId);
                    publicCustomer.setTitle(PUBLIC_CUSTOMER_TITLE);
                    if (ownerId.getEntityType() == EntityType.CUSTOMER) {
                        publicCustomer.setParentCustomerId(new CustomerId(ownerId.getId()));
                    }
                    try {
                        publicCustomer.setAdditionalInfo(new ObjectMapper().readValue("{ \"isPublic\": true }", JsonNode.class));
                    } catch (IOException e) {
                        throw new IncorrectParameterException("Unable to create public customer.", e);
                    }
                    publicCustomer = saveCustomerInternal(publicCustomer);
                }
                return publicCustomer;
            } else {
                throw new RuntimeException("Fatal: entity group All is not present.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to find or create public customer.", e);
        }
    }

    @Override
    public EntityGroup findOrCreatePublicUserGroup(TenantId tenantId, EntityId ownerId) {
        log.trace("Executing findOrCreatePublicUserGroup, tenantId [{}], ownerId [{}]", tenantId, ownerId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validateEntityId(ownerId, INCORRECT_OWNER_ID + ownerId);
        Customer publicCustomer = findOrCreatePublicCustomer(tenantId, ownerId);
        return entityGroupService.findOrCreatePublicUsersGroup(publicCustomer.getTenantId(), publicCustomer.getId());
    }

    @Override
    public Role findOrCreatePublicUserEntityGroupRole(TenantId tenantId, EntityId ownerId) {
        log.trace("Executing findOrCreatePublicUserRole, tenantId [{}], ownerId [{}]", tenantId, ownerId);
        Validator.validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        Validator.validateEntityId(ownerId, INCORRECT_OWNER_ID + ownerId);
        Customer publicCustomer = findOrCreatePublicCustomer(tenantId, ownerId);
        Role publicUserRole = roleService.findOrCreatePublicUsersEntityGroupRole(publicCustomer.getTenantId(), publicCustomer.getId());
        return publicUserRole;
    }

    @Override
    public PageData<Customer> findCustomersByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findCustomersByTenantId, tenantId [{}], pageLink [{}]", tenantId, pageLink);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        Validator.validatePageLink(pageLink);
        return customerDao.findCustomersByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public void deleteCustomersByTenantId(TenantId tenantId) {
        log.trace("Executing deleteCustomersByTenantId, tenantId [{}]", tenantId);
        Validator.validateId(tenantId, "Incorrect tenantId " + tenantId);
        customersByTenantRemover.removeEntities(tenantId, tenantId);
    }

    @Override
    public PageData<Customer> findCustomersByEntityGroupId(EntityGroupId groupId, PageLink pageLink) {
        log.trace("Executing findCustomersByEntityGroupId, groupId [{}], pageLink [{}]", groupId, pageLink);
        validateId(groupId, "Incorrect entityGroupId " + groupId);
        validatePageLink(pageLink);
        return customerDao.findCustomersByEntityGroupId(groupId.getId(), pageLink);
    }

    @Override
    public PageData<Customer> findCustomersByEntityGroupIds(List<EntityGroupId> groupIds, List<CustomerId> additionalCustomerIds, PageLink pageLink) {
        log.trace("Executing findCustomersByEntityGroupId, groupIds [{}], additionalCustomerIds [{}], pageLink [{}]", groupIds, additionalCustomerIds, pageLink);
        validateIds(groupIds, "Incorrect groupIds " + groupIds);
        validatePageLink(pageLink);
        return customerDao.findCustomersByEntityGroupIds(toUUIDs(groupIds), toUUIDs(additionalCustomerIds), pageLink);
    }

    private DataValidator<Customer> customerValidator =
            new DataValidator<Customer>() {

                @Override
                protected void validateCreate(TenantId tenantId, Customer customer) {
                    customerDao.findCustomersByTenantIdAndTitle(customer.getTenantId().getId(), customer.getTitle()).ifPresent(
                            c -> {
                                throw new DataValidationException("Customer with such title already exists!");
                            }
                    );
                }

                @Override
                protected void validateUpdate(TenantId tenantId, Customer customer) {
                    customerDao.findCustomersByTenantIdAndTitle(customer.getTenantId().getId(), customer.getTitle()).ifPresent(
                            c -> {
                                if (!c.getId().equals(customer.getId())) {
                                    throw new DataValidationException("Customer with such title already exists!");
                                }
                            }
                    );
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, Customer customer) {
                    if (StringUtils.isEmpty(customer.getTitle())) {
                        throw new DataValidationException("Customer title should be specified!");
                    }
                    if (customer.getTitle().equals(PUBLIC_CUSTOMER_TITLE)) {
                        throw new DataValidationException("'Public' title for customer is system reserved!");
                    }
                    if (!StringUtils.isEmpty(customer.getEmail())) {
                        validateEmail(customer.getEmail());
                    }
                    if (customer.getTenantId() == null) {
                        throw new DataValidationException("Customer should be assigned to tenant!");
                    } else {
                        Tenant tenant = tenantDao.findById(tenantId, customer.getTenantId().getId());
                        if (tenant == null) {
                            throw new DataValidationException("Customer is referencing to non-existent tenant!");
                        }
                    }
                }
            };

    private PaginatedRemover<TenantId, Customer> customersByTenantRemover =
            new PaginatedRemover<TenantId, Customer>() {

                @Override
                protected PageData<Customer> findEntities(TenantId tenantId, TenantId id, PageLink pageLink) {
                    return customerDao.findCustomersByTenantId(id.getId(), pageLink);
                }

                @Override
                protected void removeEntity(TenantId tenantId, Customer entity) {
                    deleteCustomer(tenantId, new CustomerId(entity.getUuidId()), false);
                }
            };
}
