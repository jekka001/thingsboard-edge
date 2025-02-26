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
package org.thingsboard.rule.engine.util;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.HasCustomerId;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.UserId;

public class EntitiesCustomerIdAsyncLoader {

    public static ListenableFuture<CustomerId> findEntityIdAsync(TbContext ctx, EntityId originator) {
        switch (originator.getEntityType()) {
            case CUSTOMER:
                return toCustomerIdAsync(ctx, ctx.getCustomerService().findCustomerByIdAsync(ctx.getTenantId(), (CustomerId) originator));
            case USER:
                return toCustomerIdAsync(ctx, ctx.getUserService().findUserByIdAsync(ctx.getTenantId(), (UserId) originator));
            case ASSET:
                return toCustomerIdAsync(ctx, ctx.getAssetService().findAssetByIdAsync(ctx.getTenantId(), (AssetId) originator));
            case DEVICE:
                return toCustomerIdAsync(ctx, Futures.immediateFuture(ctx.getDeviceService().findDeviceById(ctx.getTenantId(), (DeviceId) originator)));
            default:
                return Futures.immediateFailedFuture(new TbNodeException("Unexpected originator EntityType: " + originator.getEntityType()));
        }
    }

    private static <T extends HasCustomerId> ListenableFuture<CustomerId> toCustomerIdAsync(TbContext ctx, ListenableFuture<T> future) {
        return Futures.transform(future, in -> in != null ? in.getCustomerId() : null, ctx.getDbCallbackExecutor());
    }

}
