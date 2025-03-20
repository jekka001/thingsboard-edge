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
package org.thingsboard.server.edqs.query.processor;

import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.permission.MergedGroupTypePermissionInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.edqs.DataPoint;
import org.thingsboard.server.common.data.permission.QueryContext;
import org.thingsboard.server.common.data.query.EntityFilter;
import org.thingsboard.server.edqs.data.EntityData;
import org.thingsboard.server.edqs.query.DataKey;
import org.thingsboard.server.edqs.query.EdqsDataQuery;
import org.thingsboard.server.edqs.query.EdqsQuery;
import org.thingsboard.server.edqs.query.SortableEntityData;
import org.thingsboard.server.edqs.repo.TenantRepo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.thingsboard.server.edqs.util.RepositoryUtils.checkFilters;

public abstract class AbstractQueryProcessor<T extends EntityFilter> implements EntityQueryProcessor {

    protected final TenantRepo repository;
    protected final QueryContext ctx;
    protected final EdqsQuery query;
    protected final DataKey sortKey;
    protected final T filter;

    public AbstractQueryProcessor(TenantRepo repository, QueryContext ctx, EdqsQuery query, T filter) {
        this.repository = repository;
        this.ctx = ctx;
        this.query = query;
        this.sortKey = query instanceof EdqsDataQuery dataQuery ? dataQuery.getSortKey() : null;
        this.filter = filter;
    }

    protected CombinedPermissions getCombinedPermissions(UUID id, boolean genericRead, boolean genericAttrs, boolean genericTs, List<GroupPermissions> groupPermissions) {
        return getCombinedPermissionsInternal(id, genericRead, genericRead && genericAttrs, genericRead && genericTs, groupPermissions);
    }

    protected CombinedPermissions getCombinedPermissions(UUID id, List<GroupPermissions> groupPermissions) {
        return getCombinedPermissionsInternal(id, false, false, false, groupPermissions);
    }

    protected CombinedPermissions getCombinedPermissionsInternal(UUID id, boolean read, boolean readAttrs, boolean readTs, List<GroupPermissions> groupPermissions) {
        for (GroupPermissions eg : groupPermissions) {
            if (read && readAttrs && readTs) {
                break;
            }
            boolean hasMorePermissions = !read || (!readAttrs && eg.readAttrs) || (!readTs && eg.readTs);
            if (hasMorePermissions && repository.contains(eg.groupId, id)) {
                read = true;
                readAttrs = readAttrs || eg.readAttrs;
                readTs = readTs || eg.readTs;
            }
        }
        return new CombinedPermissions(read, readAttrs, readTs);
    }

    protected SortableEntityData toSortDataGroupsOnly(EntityData<?> ed, List<GroupPermissions> groupPermissions) {
        SortableEntityData sortData;
        CombinedPermissions permissions = getCombinedPermissions(ed.getId(), groupPermissions);
        if (permissions.isRead()) {
            sortData = toSortData(ed, permissions);
        } else {
            sortData = null;
        }
        return sortData;
    }

    protected SortableEntityData toSortData(EntityData<?> ed, boolean readAttrs, boolean readTs) {
        SortableEntityData sortData = new SortableEntityData(ed);
        DataPoint sortValue = ed.getDataPoint(sortKey, ctx);
        sortData.setSortValue(sortValue);
        sortData.setReadAttrs(readAttrs);
        sortData.setReadTs(readTs);
        return sortData;
    }

    protected SortableEntityData toSortData(EntityData<?> ed, Permissions permissions) {
        return toSortData(ed, permissions.isReadAttrs(), permissions.isReadTs());
    }

    protected static List<GroupPermissions> toGroupPermissions(MergedGroupTypePermissionInfo readPermissions,
                                                               MergedGroupTypePermissionInfo readAttrPermissions,
                                                               MergedGroupTypePermissionInfo readTsPermissions) {
        List<GroupPermissions> permissions = new ArrayList<>();
        for (EntityGroupId egId : readPermissions.getEntityGroupIds()) {
            permissions.add(new GroupPermissions(egId.getId(),
                    readAttrPermissions.getEntityGroupIds() != null && readAttrPermissions.getEntityGroupIds().contains(egId),
                    readTsPermissions.getEntityGroupIds() != null && readTsPermissions.getEntityGroupIds().contains(egId)));
        }
        return permissions;
    }

    protected static boolean checkCustomerHierarchy(Set<UUID> customers, EntityData<?> ed) {
        return ed.getCustomerId() != null && customers.contains(ed.getCustomerId());
    }

    protected void process(Collection<EntityData<?>> entities, Consumer<EntityData<?>> processor) {
        for (EntityData<?> ed : entities) {
            if (matches(ed)) {
                processor.accept(ed);
            }
        }
    }

    protected boolean matches(EntityData<?> ed) {
        return checkFilters(query, ed);
    }

}
