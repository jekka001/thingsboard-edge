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
package org.thingsboard.server.service.security.permission;

import org.thingsboard.server.common.data.*;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.*;
import org.thingsboard.server.common.data.page.TextPageData;
import org.thingsboard.server.common.data.page.TextPageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;


public interface OwnersCacheService {

    Set<EntityId> fetchOwners(TenantId tenantId, EntityId ownerId);

    Set<EntityId> getOwners(TenantId tenantId, EntityId entityId, HasOwnerId hasOwnerId);

    Set<EntityId> getOwners(TenantId tenantId, EntityGroupId entityGroupId);

    EntityId getOwner(TenantId tenantId, EntityId entityId);

    void clearOwners(EntityId entityId);

    Set<EntityId> getChildOwners(TenantId tenantId, EntityId parentOwnerId);

    void changeDashboardOwner(TenantId tenantId, EntityId targetOwnerId, Dashboard dashboard) throws ThingsboardException;

    void changeUserOwner(TenantId tenantId, EntityId targetOwnerId, User user) throws ThingsboardException;

    void changeCustomerOwner(TenantId tenantId, EntityId targetOwnerId, Customer customer) throws ThingsboardException;

    void changeEntityViewOwner(TenantId tenantId, EntityId targetOwnerId, EntityView entityView) throws ThingsboardException;

    void changeAssetOwner(TenantId tenantId, EntityId targetOwnerId, Asset asset) throws ThingsboardException;

    void changeDeviceOwner(TenantId tenantId, EntityId targetOwnerId, Device device) throws ThingsboardException;

    void changeEntityOwner(TenantId tenantId, EntityId targetOwnerId, EntityId entityId, EntityType entityType) throws ThingsboardException;

    boolean isChildOwner(TenantId tenantId, CustomerId parentOwnerId, CustomerId childOwnerId);

    <E extends SearchTextBased<? extends UUIDBased>, I extends EntityId> TextPageData<E>
    getGroupEntitiesByPageLink(TenantId tenantId, SecurityUser securityUser, EntityType entityType, Operation operation,
                               Function<EntityId, I> toIdFunction, Function<List<I>, List<E>> toEntitiesFunction,
                               List<Predicate<E>> entityFilters, List<I> additionalEntityIds, TextPageLink pageLink) throws Exception;

    <E extends SearchTextBased<? extends UUIDBased>, I extends EntityId> TextPageData<E>
    loadAndFilterEntities(List<I> entityIds, Function<List<I>, List<E>> toEntitiesFunction, List<Predicate<E>> entityFilters, TextPageLink pageLink);

    Comparator<SearchTextBased<? extends UUIDBased>> entityComparator();

    Predicate<SearchTextBased<? extends UUIDBased>> entityPageLinkFilter(TextPageLink pageLink);
}
