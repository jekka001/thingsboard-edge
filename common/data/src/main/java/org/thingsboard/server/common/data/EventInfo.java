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
package org.thingsboard.server.common.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EventId;
import org.thingsboard.server.common.data.id.TenantId;

import static org.thingsboard.server.common.data.BaseDataWithAdditionalInfo.getJson;
import static org.thingsboard.server.common.data.BaseDataWithAdditionalInfo.setJson;

/**
 * @author Andrew Shvayka
 */
@Data
@Slf4j
@Schema
public class EventInfo extends BaseData<EventId> {

    @Schema(description = "JSON object with Tenant Id.", accessMode = Schema.AccessMode.READ_ONLY)
    private TenantId tenantId;
    @Schema(description = "Event type", example = "STATS")
    private String type;
    @Schema(description = "string", example = "784f394c-42b6-435a-983c-b7beff2784f9")
    private String uid;
    @Schema(description = "JSON object with Entity Id for which event is created.", accessMode = Schema.AccessMode.READ_ONLY)
    private EntityId entityId;
    @Schema(description = "Event body.",implementation = com.fasterxml.jackson.databind.JsonNode.class)
    private transient JsonNode body;
    @JsonIgnore
    private byte[] bodyBytes;

    public EventInfo() {
        super();
    }

    public EventInfo(EventId id) {
        super(id);
    }

    public EventInfo(EventInfo event) {
        super(event);
        this.setBody(event.getBody());
    }

    public JsonNode getBody() {
        return getJson(() -> body, () -> bodyBytes);
    }

    public void setBody(JsonNode body) {
        setJson(body, json -> this.body = json, bytes -> this.bodyBytes = bytes);
    }

    @Schema(description = "Timestamp of the event creation, in milliseconds", example = "1609459200000", accessMode = Schema.AccessMode.READ_ONLY)
    @Override
    public long getCreatedTime() {
        return super.getCreatedTime();
    }
}
