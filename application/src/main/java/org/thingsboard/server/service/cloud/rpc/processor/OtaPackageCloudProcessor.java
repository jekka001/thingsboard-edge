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
package org.thingsboard.server.service.cloud.rpc.processor;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.gen.edge.v1.OtaPackageUpdateMsg;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;

import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@TbCoreComponent
public class OtaPackageCloudProcessor extends BaseEdgeProcessor {

    private final Lock otaPackageCreationLock = new ReentrantLock();

    public ListenableFuture<Void> processOtaPackageMsgFromCloud(TenantId tenantId, OtaPackageUpdateMsg otaPackageUpdateMsg) {
        OtaPackageId otaPackageId = new OtaPackageId(new UUID(otaPackageUpdateMsg.getIdMSB(), otaPackageUpdateMsg.getIdLSB()));
        try {
            cloudSynchronizationManager.getSync().set(true);
            switch (otaPackageUpdateMsg.getMsgType()) {
                case ENTITY_CREATED_RPC_MESSAGE:
                case ENTITY_UPDATED_RPC_MESSAGE:
                    otaPackageCreationLock.lock();
                    try {
                        OtaPackage otaPackage = JacksonUtil.fromString(otaPackageUpdateMsg.getEntity(), OtaPackage.class, true);
                        if (otaPackage == null) {
                            throw new RuntimeException("[{" + tenantId + "}] otaPackageUpdateMsg {" + otaPackageUpdateMsg + "} cannot be converted to ota package");
                        }
                        if (otaPackage.getData() == null) {
                            edgeCtx.getOtaPackageService().saveOtaPackageInfo(new OtaPackageInfo(otaPackage), otaPackage.hasUrl(), false);
                        } else {
                            edgeCtx.getOtaPackageService().saveOtaPackage(otaPackage, false);
                        }
                    } finally {
                        otaPackageCreationLock.unlock();
                    }
                    break;
                case ENTITY_DELETED_RPC_MESSAGE:
                    OtaPackage otaPackage = edgeCtx.getOtaPackageService().findOtaPackageById(tenantId, otaPackageId);
                    if (otaPackage != null) {
                        edgeCtx.getOtaPackageService().deleteOtaPackage(tenantId, otaPackageId);
                    }
                    break;
                case UNRECOGNIZED:
                    return handleUnsupportedMsgType(otaPackageUpdateMsg.getMsgType());
            }
        } finally {
            cloudSynchronizationManager.getSync().remove();
        }
        return Futures.immediateFuture(null);
    }

}
