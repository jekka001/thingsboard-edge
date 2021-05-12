/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.cloud.processor.downlink;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.GroupPermissionId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.dao.grouppermission.GroupPermissionService;
import org.thingsboard.server.gen.edge.GroupPermissionProto;
import org.thingsboard.server.service.security.permission.UserPermissionsService;

import java.util.UUID;

@Component
@Slf4j
public class GroupPermissionProcessor extends BaseProcessor {

    @Autowired
    private GroupPermissionService groupPermissionService;

    @Autowired
    private UserPermissionsService userPermissionsService;

    public ListenableFuture<Void> onGroupPermissionUpdate(TenantId tenantId, GroupPermissionProto groupPermissionProto) {
        try {
            GroupPermissionId groupPermissionId = new GroupPermissionId(new UUID(groupPermissionProto.getIdMSB(), groupPermissionProto.getIdLSB()));
            switch (groupPermissionProto.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    GroupPermission groupPermission = groupPermissionService.findGroupPermissionById(tenantId, groupPermissionId);
                    boolean created = false;
                    if (groupPermission == null) {
                        created = true;
                        groupPermission = new GroupPermission();
                        groupPermission.setId(groupPermissionId);
                        groupPermission.setCreatedTime(Uuids.unixTimestamp(groupPermissionId.getId()));
                        groupPermission.setTenantId(tenantId);
                    }
                    EntityGroupId userGroupId =
                            new EntityGroupId(new UUID(groupPermissionProto.getUserGroupIdMSB(), groupPermissionProto.getUserGroupIdLSB()));
                    groupPermission.setUserGroupId(userGroupId);

                    RoleId roleId = new RoleId(new UUID(groupPermissionProto.getRoleIdMSB(), groupPermissionProto.getRoleIdLSB()));
                    groupPermission.setRoleId(roleId);

                    if (!StringUtils.isEmpty(groupPermissionProto.getEntityGroupType())) {
                        EntityGroupId entityGroupId =
                                new EntityGroupId(new UUID(groupPermissionProto.getEntityGroupIdMSB(), groupPermissionProto.getEntityGroupIdLSB()));
                        groupPermission.setEntityGroupId(entityGroupId);
                        groupPermission.setEntityGroupType(EntityType.valueOf(groupPermissionProto.getEntityGroupType()));
                    }
                    groupPermission.setPublic(groupPermissionProto.getIsPublic());
                    GroupPermission saveGroupPermission = groupPermissionService.saveGroupPermission(tenantId, groupPermission);
                    userPermissionsService.onGroupPermissionUpdated(saveGroupPermission);

                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    GroupPermission groupPermissionById = groupPermissionService.findGroupPermissionById(tenantId, groupPermissionId);
                    if (groupPermissionById != null) {
                        groupPermissionService.deleteGroupPermission(tenantId, groupPermissionId);
                    }
                    break;
                case UNRECOGNIZED:
                    log.error("Unsupported msg type");
                    return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type" + groupPermissionProto.getMsgType()));
            }
        } catch (Exception e) {
            log.error("Can't process groupPermissionProto [{}]", groupPermissionProto, e);
            return Futures.immediateFailedFuture(new RuntimeException("Can't process groupPermissionProto " + groupPermissionProto, e));
        }
        return Futures.immediateFuture(null);
    }

}
