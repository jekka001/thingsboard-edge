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
package org.thingsboard.server.common.data.notification.rule.trigger;

import lombok.Builder;
import lombok.Data;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.notification.rule.trigger.config.NotificationRuleTriggerType;

import java.io.Serial;
import java.util.concurrent.TimeUnit;

@Data
@Builder
public class EdgeConnectionTrigger implements NotificationRuleTrigger {

    @Serial
    private static final long serialVersionUID = -261939829962721957L;

    private final TenantId tenantId;
    private final CustomerId customerId;
    private final EdgeId edgeId;
    private final boolean connected;
    private final String edgeName;

    @Override
    public DeduplicationStrategy getDeduplicationStrategy() {
        return DeduplicationStrategy.ALL;
    }

    @Override
    public String getDeduplicationKey() {
        return String.join(":", NotificationRuleTrigger.super.getDeduplicationKey(), String.valueOf(connected));
    }

    @Override
    public long getDefaultDeduplicationDuration() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    public NotificationRuleTriggerType getType() {
        return NotificationRuleTriggerType.EDGE_CONNECTION;
    }

    @Override
    public EntityId getOriginatorEntityId() {
        return edgeId;
    }

}
