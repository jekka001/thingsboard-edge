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
package org.thingsboard.server.common.data.permission;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.TenantEntity;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.GroupPermissionId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class GroupPermission extends BaseData<GroupPermissionId> implements HasName, TenantEntity {

    private static final long serialVersionUID = 5582010124562018986L;

    public static final List<Operation> READ_ONLY_GROUP_PERMISSIONS = Arrays.asList(
            Operation.READ,
            Operation.RPC_CALL,
            Operation.READ_CREDENTIALS,
            Operation.READ_ATTRIBUTES,
            Operation.READ_TELEMETRY
    );

    private static final List<Operation> NONE_PERMISSIONS = Arrays.asList();


    public static final Map<Resource, List<Operation>> EDGE_USER_PERMISSIONS = new HashMap<>();
    static {
        EDGE_USER_PERMISSIONS.put(Resource.ALL, READ_ONLY_GROUP_PERMISSIONS);

        EDGE_USER_PERMISSIONS.put(Resource.PROFILE, Arrays.asList(Operation.ALL));
        EDGE_USER_PERMISSIONS.put(Resource.DEVICE, Arrays.asList(Operation.ALL));
        EDGE_USER_PERMISSIONS.put(Resource.ALARM, Arrays.asList(Operation.ALL));

        EDGE_USER_PERMISSIONS.put(Resource.DEVICE_GROUP, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.ASSET_GROUP, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.USER_GROUP, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.ENTITY_VIEW_GROUP, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.DASHBOARD_GROUP, READ_ONLY_GROUP_PERMISSIONS);

        EDGE_USER_PERMISSIONS.put(Resource.CUSTOMER, NONE_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.CUSTOMER_GROUP, NONE_PERMISSIONS);

        EDGE_USER_PERMISSIONS.put(Resource.ASSET, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.DASHBOARD, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.ENTITY_VIEW, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.TENANT, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.RULE_CHAIN, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.USER, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.WIDGETS_BUNDLE, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.WIDGET_TYPE, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.SCHEDULER_EVENT, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.BLOB_ENTITY, READ_ONLY_GROUP_PERMISSIONS);

        EDGE_USER_PERMISSIONS.put(Resource.WHITE_LABELING, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.AUDIT_LOG, READ_ONLY_GROUP_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.ADMIN_SETTINGS, READ_ONLY_GROUP_PERMISSIONS);

        EDGE_USER_PERMISSIONS.put(Resource.ROLE, NONE_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.GROUP_PERMISSION, NONE_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.CONVERTER, NONE_PERMISSIONS);
        EDGE_USER_PERMISSIONS.put(Resource.INTEGRATION, NONE_PERMISSIONS);

    }

    public static final Map<Resource, List<Operation>> PUBLIC_USER_PERMISSIONS = new HashMap<>();
    static {
        PUBLIC_USER_PERMISSIONS.put(Resource.DASHBOARD, Arrays.asList(
                Operation.READ
        ));
        PUBLIC_USER_PERMISSIONS.put(Resource.WIDGETS_BUNDLE, Arrays.asList(
                Operation.READ
        ));
        PUBLIC_USER_PERMISSIONS.put(Resource.WIDGET_TYPE, Arrays.asList(
                Operation.READ
        ));
        PUBLIC_USER_PERMISSIONS.put(Resource.ALARM, Arrays.asList(
                Operation.READ
        ));
    }

    public static final List<Operation> PUBLIC_USER_ENTITY_GROUP_PERMISSIONS =
            Arrays.asList(Operation.READ, Operation.RPC_CALL, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY);

    private TenantId tenantId;
    private EntityGroupId userGroupId;
    private RoleId roleId;
    private EntityGroupId entityGroupId;
    private EntityType entityGroupType;
    private boolean isPublic;

    public GroupPermission() {
        super();
    }

    public GroupPermission(GroupPermissionId id) {
        super(id);
    }

    public GroupPermission(GroupPermission groupPermission) {
        super(groupPermission);
        this.tenantId = groupPermission.getTenantId();
        this.userGroupId = groupPermission.getUserGroupId();
        this.roleId = groupPermission.getRoleId();
        this.entityGroupId = groupPermission.getEntityGroupId();
        this.entityGroupType = groupPermission.getEntityGroupType();
        this.isPublic = groupPermission.isPublic();
    }

    @Override
    public TenantId getTenantId() {
        return tenantId;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Override
    public String getName() {
        if (entityGroupId != null && entityGroupType != null) {
            return String.format("GROUP_[%s]_[%s]_[%s]_[%s]", userGroupId.toString(), roleId.toString(), entityGroupId.toString(), entityGroupType.name());
        } else {
            return String.format("GENERIC_[%s]_[%s]", userGroupId.toString(), roleId.toString());
        }
    }

    @Override
    @JsonIgnore
    public EntityType getEntityType() {
        return EntityType.GROUP_PERMISSION;
    }

}
