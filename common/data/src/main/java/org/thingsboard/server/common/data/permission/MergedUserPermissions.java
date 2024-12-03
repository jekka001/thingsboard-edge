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
package org.thingsboard.server.common.data.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.EntityGroupId;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Schema
public final class MergedUserPermissions implements Serializable {

    @Schema(description = "Map of permissions defined using generic roles ('Customer Administrator', etc)")
    private final Map<Resource, Set<Operation>> genericPermissions;

    @Schema(description = "Map of permissions defined using group roles ('Read' or 'Write' access to specific entity group, etc)")
    private final Map<EntityGroupId, MergedGroupPermissionInfo> groupPermissions;

    @Schema(description = "Map of read permissions per entity type. Used on the UI to enable/disable certain components.")
    private final Map<EntityType, MergedGroupTypePermissionInfo> readGroupPermissions;

    @Schema(description = "Map of read permissions per resource. Used on the UI to enable/disable certain components.")
    private final Map<Resource, MergedGroupTypePermissionInfo> readEntityPermissions;

    @Schema(description = "Map of read entity attributes permissions per resource. Used on the UI to enable/disable certain tabs.")
    private final Map<Resource, MergedGroupTypePermissionInfo> readAttrPermissions;

    @Schema(description = "Map of read entity time-series permissions per resource. Used on the UI to enable/disable certain tabs.")
    private final Map<Resource, MergedGroupTypePermissionInfo> readTsPermissions;

    public MergedUserPermissions(Map<Resource, Set<Operation>> genericPermissions, Map<EntityGroupId, MergedGroupPermissionInfo> groupPermissions) {
        this.genericPermissions = genericPermissions;
        this.groupPermissions = groupPermissions;
        this.readGroupPermissions = new HashMap<>();
        this.readEntityPermissions = new HashMap<>();
        this.readAttrPermissions = new HashMap<>();
        this.readTsPermissions = new HashMap<>();
        for (EntityType groupType : EntityGroup.groupTypes) {
            Resource resource = Resource.groupResourceFromGroupType(groupType);
            boolean hasGenericRead = hasGenericPermission(resource, Operation.READ);
            this.readGroupPermissions.put(groupType, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericRead));
            this.readEntityPermissions.put(resource, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericRead));
            boolean hasGenericAttrRead = hasGenericPermission(resource, Operation.READ_ATTRIBUTES);
            this.readAttrPermissions.put(resource, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericAttrRead));
            boolean hasGenericTsRead = hasGenericPermission(resource, Operation.READ_TELEMETRY);
            this.readTsPermissions.put(resource, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericTsRead));
        }

        for (EntityType entityType : EntityType.values()) {
            Resource resource = Resource.resourceFromEntityType(entityType);
            if (resource != null) {
                boolean hasGenericRead = hasGenericPermission(resource, Operation.READ);
                this.readEntityPermissions.put(resource, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericRead));
                boolean hasGenericReadAttributes = hasGenericPermission(resource, Operation.READ_ATTRIBUTES);
                this.readAttrPermissions.put(resource, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericReadAttributes));
                boolean hasGenericReadTelemetry = hasGenericPermission(resource, Operation.READ_TELEMETRY);
                this.readTsPermissions.put(resource, MergedGroupTypePermissionInfo.ofEmptyGroups(hasGenericReadTelemetry));
            }
        }

        this.groupPermissions.forEach((id, info) -> {
            if (checkOperation(info.getOperations(), Operation.READ)) {
                addId(this.readGroupPermissions, info.getEntityType(), id);
                Resource entityResource = Resource.resourceFromEntityType(info.getEntityType());
                addId(this.readEntityPermissions, entityResource, id);
                Resource groupResource = Resource.groupResourceFromGroupType(info.getEntityType());
                if (groupResource != null) {
                    addId(this.readEntityPermissions, groupResource, id);
                }
            }
            if (checkOperation(info.getOperations(), Operation.READ_ATTRIBUTES)) {
                Resource entityResource = Resource.resourceFromEntityType(info.getEntityType());
                addId(this.readAttrPermissions, entityResource, id);
                Resource groupResource = Resource.groupResourceFromGroupType(info.getEntityType());
                if (groupResource != null) {
                    addId(this.readAttrPermissions, groupResource, id);
                }
            }
            if (checkOperation(info.getOperations(), Operation.READ_TELEMETRY)) {
                Resource entityResource = Resource.resourceFromEntityType(info.getEntityType());
                addId(this.readTsPermissions, entityResource, id);
                Resource groupResource = Resource.groupResourceFromGroupType(info.getEntityType());
                if (groupResource != null) {
                    addId(this.readTsPermissions, groupResource, id);
                }
            }
        });
    }

    // Immutable entityGroupIds brings a high complexity O(N^2 / 2).
    // GroupIds list should be as small as possible to keep the performance.
    // Most of the groupId is empty in most of the cases so problem expected
    <T> void addId(Map<T, MergedGroupTypePermissionInfo> permissions, T key, EntityGroupId id) {
        final MergedGroupTypePermissionInfo mergedGroupTypePermissionInfo = permissions.get(key);
        final MergedGroupTypePermissionInfo newInfo = mergedGroupTypePermissionInfo.addId(id);
        permissions.put(key, newInfo);
    }

    public MergedGroupTypePermissionInfo getGroupPermissionsByEntityTypeAndOperation(EntityType entityType, Operation operation) {
        Resource resource = Resource.resourceFromEntityType(entityType);
        boolean hasGenericPermission = hasGenericPermission(resource, operation);
        List<EntityGroupId> groupIds = this.groupPermissions.entrySet().stream().filter((entry) ->
                entry.getValue().getEntityType().equals(entityType) && checkOperation(entry.getValue().getOperations(), operation)
        ).map(Map.Entry::getKey).collect(Collectors.toList());
        return new MergedGroupTypePermissionInfo(groupIds, hasGenericPermission);
    }

    public boolean hasGenericPermission(Resource resource, Operation operation) {
        return hasGenericResourcePermission(resource, operation) || hasGenericAllPermission(operation);
    }

    private boolean hasGenericAllPermission(Operation operation) {
        Set<Operation> operations = genericPermissions.get(Resource.ALL);
        return operations != null && checkOperation(operations, operation);
    }

    private boolean hasGenericResourcePermission(Resource resource, Operation operation) {
        Set<Operation> operations = genericPermissions.get(resource);
        return operations != null && checkOperation(operations, operation);
    }

    private boolean checkOperation(Set<Operation> operations, Operation operation) {
        return operations.contains(Operation.ALL) || operations.contains(operation);
    }

    public boolean hasGroupPermissions(EntityGroupId entityGroupId, Operation operation) {
        MergedGroupPermissionInfo permissionInfo = groupPermissions.get(entityGroupId);
        return permissionInfo != null && checkOperation(permissionInfo.getOperations(), operation);
    }
}
