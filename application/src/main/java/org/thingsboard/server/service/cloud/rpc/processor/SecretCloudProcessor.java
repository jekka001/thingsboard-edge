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
import org.thingsboard.server.common.data.id.SecretId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.secret.Secret;
import org.thingsboard.server.common.data.secret.SecretInfo;
import org.thingsboard.server.gen.edge.v1.SecretUpdateMsg;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;

import java.util.Base64;
import java.util.UUID;

@Slf4j
@Component
public class SecretCloudProcessor extends BaseEdgeProcessor {

    public ListenableFuture<Void> processSecretMsgFromCloud(TenantId tenantId, SecretUpdateMsg secretUpdateMsg) {
        SecretId secretId = new SecretId(new UUID(secretUpdateMsg.getIdMSB(), secretUpdateMsg.getIdLSB()));
        switch (secretUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                try {
                    log.info("Received SecretUpdateMsg entity: {}", secretUpdateMsg.getEntity());
                    Secret secret = JacksonUtil.fromString(secretUpdateMsg.getEntity(), Secret.class, true);
                    if (secret == null) {
                        throw new RuntimeException("[{" + tenantId + "}] SecretUpdateMsg {" + secretUpdateMsg + " } cannot be converted to secret");
                    }

                    log.info("Parsed Secret on Edge, rawValue length: {}", secret.getRawValue() == null ? 0 : secret.getRawValue().length);

                    if (secret.getRawValue() != null) {
                        log.info("Parsed Secret rawValue (Base64): {}", Base64.getEncoder().encodeToString(secret.getRawValue()));
                    }

                    edgeCtx.getSecretService().saveSecret(tenantId, secret, false);
                } catch (Exception e) {
                    log.error("[{}] Failed to process SecretUpdateMsg [{}]", tenantId, secretUpdateMsg, e);
                    throw e;
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                SecretInfo secretInfo = new SecretInfo(secretId);
                edgeCtx.getSecretService().deleteSecret(tenantId, secretInfo);
                break;
            default:
                log.warn("[{}] Unsupported msg type [{}] for secret", tenantId, secretUpdateMsg.getMsgType());
        }
        return Futures.immediateFuture(null);
    }

}
