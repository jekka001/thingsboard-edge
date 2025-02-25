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
package org.thingsboard.server.common.data.alarm;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.validation.NoXss;

@Data
@Builder
public class AlarmUpdateRequest implements AlarmModificationRequest {

    @NotNull
    @Schema(description = "JSON object with Tenant Id", accessMode = Schema.AccessMode.READ_ONLY)
    private TenantId tenantId;
    @NotNull
    @Schema(description = "JSON object with the alarm Id. " +
            "Specify this field to update the alarm. " +
            "Referencing non-existing alarm Id will cause error. " +
            "Omit this field to create new alarm.")
    private AlarmId alarmId;
    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Alarm severity", example = "CRITICAL")
    private AlarmSeverity severity;
    @Schema(description = "Timestamp of the alarm start time, in milliseconds", example = "1634058704565")
    private long startTs;
    @Schema(description = "Timestamp of the alarm end time(last time update), in milliseconds", example = "1634111163522")
    private long endTs;

    @ToString.Exclude
    @NoXss
    @Schema(description = "JSON object with alarm details")
    private JsonNode details;

    @Valid
    @Schema(description = "JSON object with propagation details")
    private AlarmPropagationInfo propagation;

    private UserId userId;

    public static AlarmUpdateRequest fromAlarm(Alarm a) {
        return fromAlarm(a, null);
    }

    public static AlarmUpdateRequest fromAlarm(Alarm a, UserId userId) {
        return AlarmUpdateRequest.builder()
                .tenantId(a.getTenantId())
                .alarmId(a.getId())
                .severity((a.getSeverity()))
                .startTs(a.getStartTs())
                .endTs(a.getEndTs())
                .details(a.getDetails())
                .propagation(AlarmPropagationInfo.builder()
                        .propagate(a.isPropagate())
                        .propagateToOwner(a.isPropagateToOwner())
                        .propagateToOwnerHierarchy(a.isPropagateToOwnerHierarchy())
                        .propagateToTenant(a.isPropagateToTenant())
                        .propagateRelationTypes(a.getPropagateRelationTypes()).build())
                .userId(userId)
                .build();
    }
}
