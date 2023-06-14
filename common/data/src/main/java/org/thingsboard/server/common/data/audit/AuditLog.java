/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.common.data.audit;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.id.AuditLogId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.validation.NoXss;

@Schema
@EqualsAndHashCode(callSuper = true)
@Data
public class AuditLog extends BaseData<AuditLogId> {

    @Schema(description = "JSON object with Tenant Id", accessMode = Schema.AccessMode.READ_ONLY)
    private TenantId tenantId;
    @Schema(description = "JSON object with Customer Id", accessMode = Schema.AccessMode.READ_ONLY)
    private CustomerId customerId;
    @Schema(description = "JSON object with Entity id", accessMode = Schema.AccessMode.READ_ONLY)
    private EntityId entityId;
    @NoXss
    @Schema(description = "Name of the logged entity", example = "Thermometer", accessMode = Schema.AccessMode.READ_ONLY)
    private String entityName;
    @Schema(description = "JSON object with User id.", accessMode = Schema.AccessMode.READ_ONLY)
    private UserId userId;
    @Schema(description = "Unique user name(email) of the user that performed some action on logged entity", example = "tenant@thingsboard.org", accessMode = Schema.AccessMode.READ_ONLY)
    private String userName;
    @Schema(description = "String represented Action type", example = "ADDED", accessMode = Schema.AccessMode.READ_ONLY)
    private ActionType actionType;
    @Schema(description = "JsonNode represented action data", accessMode = Schema.AccessMode.READ_ONLY)
    private JsonNode actionData;
    @Schema(description = "String represented Action status", example = "SUCCESS", allowableValues = "SUCCESS,FAILURE", accessMode = Schema.AccessMode.READ_ONLY)
    private ActionStatus actionStatus;
    @Schema(description = "Failure action details info. An empty string in case of action status type 'SUCCESS', otherwise includes stack trace of the caused exception.", accessMode = Schema.AccessMode.READ_ONLY)
    private String actionFailureDetails;

    public AuditLog() {
        super();
    }

    public AuditLog(AuditLogId id) {
        super(id);
    }

    public AuditLog(AuditLog auditLog) {
        super(auditLog);
        this.tenantId = auditLog.getTenantId();
        this.customerId = auditLog.getCustomerId();
        this.entityId = auditLog.getEntityId();
        this.entityName = auditLog.getEntityName();
        this.userId = auditLog.getUserId();
        this.userName = auditLog.getUserName();
        this.actionType = auditLog.getActionType();
        this.actionData = auditLog.getActionData();
        this.actionStatus = auditLog.getActionStatus();
        this.actionFailureDetails = auditLog.getActionFailureDetails();
    }

    @Schema(description = "Timestamp of the auditLog creation, in milliseconds", example = "1609459200000", accessMode = Schema.AccessMode.READ_ONLY)
    @Override
    public long getCreatedTime() {
        return super.getCreatedTime();
    }

    @Schema(description = "JSON object with the auditLog Id")
    @Override
    public AuditLogId getId() {
        return super.getId();
    }

}
