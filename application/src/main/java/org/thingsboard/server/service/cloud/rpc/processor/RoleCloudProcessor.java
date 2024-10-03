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
package org.thingsboard.server.service.cloud.rpc.processor;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.gen.edge.v1.RoleProto;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;
import org.thingsboard.server.service.security.permission.UserPermissionsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RoleCloudProcessor extends BaseEdgeProcessor {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserPermissionsService userPermissionsService;

    private final Set<Operation> groupRoleOperations = new HashSet<>(Arrays.asList(Operation.RPC_CALL,
            Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_CREDENTIALS, Operation.READ_TELEMETRY,
            Operation.WRITE_ATTRIBUTES, Operation.WRITE_TELEMETRY, Operation.WRITE_CREDENTIALS));

    private final Set<Operation> genericRoleOperations = new HashSet<>(Arrays.asList(Operation.RPC_CALL,
            Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_CREDENTIALS, Operation.READ_TELEMETRY,
            Operation.WRITE_ATTRIBUTES, Operation.WRITE_TELEMETRY, Operation.WRITE_CREDENTIALS));

    private final Set<Operation> profilesAndResourceOperations = new HashSet<>(Arrays.asList(
            Operation.CHANGE_OWNER, Operation.CREATE,
            Operation.READ, Operation.READ_ATTRIBUTES ,Operation.READ_TELEMETRY,
            Operation.WRITE, Operation.WRITE_ATTRIBUTES, Operation.WRITE_TELEMETRY));

    private final Set<Operation> entityGroupOperations = new HashSet<>(Arrays.asList(
            Operation.ADD_TO_GROUP, Operation.REMOVE_FROM_GROUP,
            Operation.READ, Operation.READ_ATTRIBUTES, Operation.READ_TELEMETRY,
            Operation.WRITE_ATTRIBUTES, Operation.WRITE_CREDENTIALS));

    public ListenableFuture<Void> processRoleMsgFromCloud(TenantId tenantId, RoleProto roleProto) {
        try {
            RoleId roleId = new RoleId(new UUID(roleProto.getIdMSB(), roleProto.getIdLSB()));
            switch (roleProto.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    Role role = JacksonUtil.fromString(roleProto.getEntity(), Role.class, true);
                    if (role == null) {
                        throw new RuntimeException("[{" + tenantId + "}] roleProto {" + roleProto + "} cannot be converted to rolo");
                    }
                    Role roleById = roleService.findRoleById(tenantId, roleId);
                    boolean created = false;
                    if (roleById == null) {
                        created = true;
                        role.setId(null);
                    }
                    role = replaceWriteOperationsToReadIfRequired(role);
                    roleValidator.validate(role, Role::getTenantId);

                    if (created) {
                        role.setId(roleId);
                    }

                    Role savedRole = roleService.saveRole(tenantId, role, false);

                    userPermissionsService.onRoleUpdated(savedRole);

                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    Role roleToDelete = roleService.findRoleById(tenantId, roleId);
                    if (roleToDelete != null) {
                        roleService.deleteRole(tenantId, roleId);
                    }
                    break;
                case UNRECOGNIZED:
                    return handleUnsupportedMsgType(roleProto.getMsgType());
            }
        } catch (Exception e) {
            String errMsg = String.format("Can't process roleProto [%s]", roleProto);
            log.error(errMsg, e);
            return Futures.immediateFailedFuture(new RuntimeException(errMsg, e));
        }
        return Futures.immediateFuture(null);
    }

    Role replaceWriteOperationsToReadIfRequired(Role role) {
        if (RoleType.GROUP.equals(role.getType())) {
            CollectionType collectionType = TypeFactory.defaultInstance().constructCollectionType(List.class, Operation.class);
            List<Operation> originOperations = JacksonUtil.fromString(JacksonUtil.toString(role.getPermissions()), collectionType);
            if (originOperations == null) {
                return role;
            }
            List<Operation> operations;
            if (originOperations.contains(Operation.ALL)) {
                operations = new ArrayList<>(groupRoleOperations);
            } else {
                operations = originOperations.stream()
                        .filter(groupRoleOperations::contains)
                        .collect(Collectors.toList());
            }
            role.setPermissions(JacksonUtil.valueToTree(operations));
        } else {
            CollectionType operationType = TypeFactory.defaultInstance().constructCollectionType(List.class, Operation.class);
            JavaType resourceType = JacksonUtil.OBJECT_MAPPER.getTypeFactory().constructType(Resource.class);
            MapType mapType = TypeFactory.defaultInstance().constructMapType(HashMap.class, resourceType, operationType);
            Map<Resource, List<Operation>> originPermissions = JacksonUtil.fromString(JacksonUtil.toString(role.getPermissions()), mapType);
            if (originPermissions == null) {
                return role;
            }
            Map<Resource, List<Operation>> newPermissions = new HashMap<>();
            for (Map.Entry<Resource, List<Operation>> entry : originPermissions.entrySet()) {
                List<Operation> originOperations = entry.getValue();
                List<Operation> newOperations;
                switch (entry.getKey()) {
                    case DEVICE:
                    case ASSET:
                    case ENTITY_VIEW:
                    case DASHBOARD:
                    case ALARM:
                    case NOTIFICATION:
                        newOperations = entry.getValue();
                        break;
                    case DEVICE_GROUP:
                    case ASSET_GROUP:
                    case ENTITY_VIEW_GROUP:
                    case DASHBOARD_GROUP:
                        if (originOperations.contains(Operation.ALL)) {
                            newOperations = new ArrayList<>(entityGroupOperations);
                        } else {
                            newOperations = originOperations.stream()
                                    .filter(entityGroupOperations::contains)
                                    .collect(Collectors.toList());
                        }
                        break;
                    case PROFILE:
                    case VERSION_CONTROL:
                        newOperations = new ArrayList<>();
                        break;
                    case DEVICE_PROFILE:
                    case ASSET_PROFILE:
                    case TB_RESOURCE:
                        if (originOperations.contains(Operation.ALL)) {
                            newOperations = new ArrayList<>(profilesAndResourceOperations);
                        } else {
                            newOperations = originOperations.stream()
                                    .filter(profilesAndResourceOperations::contains)
                                    .collect(Collectors.toList());
                        }
                        break;
                    case ALL:
                        if (originOperations.contains(Operation.ALL)) {
                            newPermissions.put(Resource.PROFILE, new ArrayList<>());
                            newPermissions.put(Resource.DEVICE, Collections.singletonList(Operation.ALL));
                            newPermissions.put(Resource.ASSET, Collections.singletonList(Operation.ALL));
                            newPermissions.put(Resource.ENTITY_VIEW, Collections.singletonList(Operation.ALL));
                            newPermissions.put(Resource.DASHBOARD, Collections.singletonList(Operation.ALL));
                            newPermissions.put(Resource.ALARM, Collections.singletonList(Operation.ALL));
                            newPermissions.put(Resource.NOTIFICATION, Collections.singletonList(Operation.ALL));
                            newPermissions.put(Resource.DEVICE_GROUP, new ArrayList<>(entityGroupOperations));
                            newPermissions.put(Resource.ASSET_GROUP, new ArrayList<>(entityGroupOperations));
                            newPermissions.put(Resource.ENTITY_VIEW_GROUP, new ArrayList<>(entityGroupOperations));
                            newPermissions.put(Resource.DASHBOARD_GROUP, new ArrayList<>(entityGroupOperations));
                            newPermissions.put(Resource.DEVICE_PROFILE, new ArrayList<>(profilesAndResourceOperations));
                            newPermissions.put(Resource.ASSET_PROFILE, new ArrayList<>(profilesAndResourceOperations));
                            newPermissions.put(Resource.TB_RESOURCE, new ArrayList<>(profilesAndResourceOperations));
                            newOperations = new ArrayList<>(genericRoleOperations);
                        } else {
                            newOperations = originOperations.stream()
                                    .filter(genericRoleOperations::contains)
                                    .collect(Collectors.toList());
                        }
                        break;
                    default:
                        if (originOperations.contains(Operation.ALL)) {
                            newOperations = new ArrayList<>(genericRoleOperations);
                        } else {
                            newOperations = originOperations.stream()
                                    .filter(genericRoleOperations::contains)
                                    .collect(Collectors.toList());
                        }
                        break;
                }
                newPermissions.put(entry.getKey(), newOperations);
            }
            role.setPermissions(JacksonUtil.valueToTree(newPermissions));
        }
        return role;
    }

}
