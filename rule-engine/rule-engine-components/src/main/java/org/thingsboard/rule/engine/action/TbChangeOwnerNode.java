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
package org.thingsboard.rule.engine.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.exception.DataValidationException;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.thingsboard.common.util.DonAsynchron.withCallback;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "change owner",
        configClazz = TbChangeOwnerNodeConfiguration.class,
        version = 1,
        nodeDescription = "Changes owner of the originator entity to the selected owner by type Tenant or Customer.",
        nodeDetails = "If <b>Tenant</b> is selected, rule node changes the owner of the originator to the tenant.<br>" +
                "If <b>Customer</b> is selected, rule node finds target owner by owner name pattern and then change the owner of the originator entity.</br>" +
                "If the target owner does not exist and the 'Create new owner if not exists' toggle is enabled, the rule node will create a new owner.<br>" +
                "If both 'Create new owner if not exists' and 'Create new owner as sub-customer of current owner' are enabled, the rule node creates a new owner as a sub-customer of the current owner.<br><br>" +
                "Output connections: <code>Success</code> - if an entity already belongs to this owner or entity owner is successfully changed, otherwise - <code>Failure</code>.",
        configDirective = "tbActionNodeChangeOwnerConfig",
        icon = "assignment_ind"
)
public class TbChangeOwnerNode implements TbNode {

    private static final Set<EntityType> supportedEntityTypes = EnumSet.of(EntityType.TENANT, EntityType.CUSTOMER);
    private static final String supportedEntityTypesStr = supportedEntityTypes.stream().map(Enum::name).collect(Collectors.joining(", "));

    private TbChangeOwnerNodeConfiguration config;
    private EntityType ownerType;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        config = TbNodeUtils.convert(configuration, TbChangeOwnerNodeConfiguration.class);
        ownerType = config.getOwnerType();
        if (ownerType == null) {
            throw new TbNodeException("Owner type should be specified!", true);
        }
        if (!supportedEntityTypes.contains(ownerType)) {
            throw new TbNodeException("Unsupported owner type '" + ownerType +
                    "'! Only " + supportedEntityTypesStr + " types are allowed.", true);
        }
        if (EntityType.TENANT.equals(ownerType)) {
            return;
        }
        if (StringUtils.isBlank(config.getOwnerNamePattern())) {
            throw new TbNodeException("Owner name should be specified!", true);
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) throws ExecutionException, InterruptedException, TbNodeException {
        EntityId originator = msg.getOriginator();
        ListenableFuture<Void> changeOwnerFuture;
        switch (ownerType) {
            case TENANT -> changeOwnerFuture = changeOwnerAsync(ctx, originator, ctx.getTenantId(), false);
            case CUSTOMER -> {
                String ownerName = TbNodeUtils.processPattern(config.getOwnerNamePattern(), msg);
                ListenableFuture<Pair<CustomerId, Boolean>> customerIdFuture = findOrCreateCustomerAsync(ctx, msg, ownerName);
                changeOwnerFuture = Futures.transformAsync(customerIdFuture, customerId ->
                        changeOwnerAsync(ctx, originator, customerId.getFirst(), customerId.getSecond()), MoreExecutors.directExecutor());
            }
            default -> throw new UnsupportedOperationException("Unsupported owner type '" + ownerType +
                    "'! Only " + supportedEntityTypesStr + " types are allowed.");
        }
        withCallback(changeOwnerFuture, __ -> ctx.tellSuccess(msg), t -> ctx.tellFailure(msg, t), MoreExecutors.directExecutor());
    }

    private ListenableFuture<Pair<CustomerId, Boolean>> findOrCreateCustomerAsync(TbContext ctx, TbMsg msg, String ownerName) {
        CustomerService customerService = ctx.getCustomerService();
        TenantId tenantId = ctx.getTenantId();
        ListenableFuture<Optional<Customer>> optionalCustomerListenableFuture = customerService.findCustomerByTenantIdAndTitleAsync(tenantId, ownerName);
        return Futures.transform(optionalCustomerListenableFuture, customerOpt -> {
            if (customerOpt.isPresent()) {
                return Pair.of(customerOpt.get().getId(), false);
            }
            if (config.isCreateOwnerIfNotExists()) {
                try {
                    Customer newCustomer = new Customer();
                    newCustomer.setTitle(ownerName);
                    newCustomer.setTenantId(tenantId);
                    if (config.isCreateOwnerOnOriginatorLevel()) {
                        EntityId currentOriginatorOwnerId = ctx.getPeContext()
                                .getOwner(tenantId, msg.getOriginator());
                        newCustomer.setOwnerId(currentOriginatorOwnerId);
                    }
                    Customer savedCustomer = customerService.saveCustomer(newCustomer);
                    ctx.enqueue(ctx.customerCreatedMsg(savedCustomer, ctx.getSelfId()),
                            () -> log.trace("Pushed Customer Created message: {}", savedCustomer),
                            throwable -> log.warn("Failed to push Customer Created message: {}", savedCustomer, throwable));
                    return Pair.of(savedCustomer.getId(), true);
                } catch (DataValidationException e) {
                    return customerService.findCustomerByTenantIdAndTitle(tenantId, ownerName)
                            .map(customer -> Pair.of(customer.getId(), false))
                            .orElseThrow(() -> new RuntimeException("Failed to create customer with title '" + ownerName + "'", e));
                }
            }
            throw new NoSuchElementException("Customer with title '" + ownerName + "' doesn't exist!");
        }, MoreExecutors.directExecutor());
    }

    private ListenableFuture<Void> changeOwnerAsync(TbContext ctx, EntityId originator, EntityId targetOwnerId, boolean newOwnerCreated) {
        TenantId tenantId = ctx.getTenantId();
        return ctx.getDbCallbackExecutor().executeAsync(() -> {
            if (newOwnerCreated || !ctx.getPeContext().getOwner(tenantId, originator).equals(targetOwnerId)) {
                ctx.getPeContext().changeEntityOwner(tenantId, targetOwnerId, originator);
            }
            return null;
        });
    }

    @Override
    public TbPair<Boolean, JsonNode> upgrade(int fromVersion, JsonNode oldConfiguration) throws TbNodeException {
        boolean hasChanges = false;
        switch (fromVersion) {
            case 0:
                if (!oldConfiguration.has("createOwnerOnOriginatorLevel")) {
                    hasChanges = true;
                    ((ObjectNode) oldConfiguration).put("createOwnerOnOriginatorLevel", false)
                            .remove("ownerCacheExpiration");
                }
                break;
            default:
                break;
        }
        return new TbPair<>(hasChanges, oldConfiguration);
    }

}
