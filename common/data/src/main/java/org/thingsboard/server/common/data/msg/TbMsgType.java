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
package org.thingsboard.server.common.data.msg;

import lombok.Getter;
import org.thingsboard.server.common.data.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public enum TbMsgType {

    POST_ATTRIBUTES_REQUEST("Post attributes"),
    POST_TELEMETRY_REQUEST("Post telemetry"),
    TO_SERVER_RPC_REQUEST("RPC Request from Device"),
    ACTIVITY_EVENT("Activity Event"),
    INACTIVITY_EVENT("Inactivity Event"),
    CONNECT_EVENT("Connect Event"),
    DISCONNECT_EVENT("Disconnect Event"),
    ENTITY_CREATED("Entity Created"),
    ENTITY_UPDATED("Entity Updated"),
    ENTITY_DELETED("Entity Deleted"),
    ENTITY_ASSIGNED("Entity Assigned"),
    ENTITY_UNASSIGNED("Entity Unassigned"),
    ATTRIBUTES_UPDATED("Attributes Updated"),
    ATTRIBUTES_DELETED("Attributes Deleted"),
    ALARM,
    ALARM_ACK("Alarm Acknowledged"),
    ALARM_CLEAR("Alarm Cleared"),
    ALARM_DELETE,
    ALARM_ASSIGNED("Alarm Assigned"),
    ALARM_UNASSIGNED("Alarm Unassigned"),
    COMMENT_CREATED("Comment Created"),
    COMMENT_UPDATED("Comment Updated"),
    RPC_CALL_FROM_SERVER_TO_DEVICE("RPC Request to Device"),
    ENTITY_ASSIGNED_FROM_TENANT("Entity Assigned From Tenant"),
    ENTITY_ASSIGNED_TO_TENANT("Entity Assigned To Tenant"),
    ENTITY_ASSIGNED_TO_EDGE,
    ENTITY_UNASSIGNED_FROM_EDGE,
    TIMESERIES_UPDATED("Timeseries Updated"),
    TIMESERIES_DELETED("Timeseries Deleted"),
    RPC_QUEUED("RPC Queued"),
    RPC_SENT("RPC Sent"),
    RPC_DELIVERED("RPC Delivered"),
    RPC_SUCCESSFUL("RPC Successful"),
    RPC_TIMEOUT("RPC Timeout"),
    RPC_EXPIRED("RPC Expired"),
    RPC_FAILED("RPC Failed"),
    RPC_DELETED("RPC Deleted"),
    RELATION_ADD_OR_UPDATE("Relation Added or Updated"),
    RELATION_DELETED("Relation Deleted"),
    RELATIONS_DELETED("All Relations Deleted"),
    PROVISION_SUCCESS,
    PROVISION_FAILURE,
    SEND_EMAIL,

    // tellSelfOnly types
    GENERATOR_NODE_SELF_MSG(null, true),
    DEVICE_PROFILE_PERIODIC_SELF_MSG(null, true),
    DEVICE_PROFILE_UPDATE_SELF_MSG(null, true),
    DEVICE_UPDATE_SELF_MSG(null, true),
    DEDUPLICATION_TIMEOUT_SELF_MSG(null, true),
    DELAY_TIMEOUT_SELF_MSG(null, true),
    MSG_COUNT_SELF_MSG(null, true),

    // PE only
    TB_AGG_LATEST_SELF_MSG(null, true),
    TB_AGG_LATEST_CLEAR_INACTIVE_ENTITIES_SELF_MSG(null, true),
    TB_ALARMS_COUNT_SELF_MSG(null, true),
    TB_SIMPLE_AGG_REPORT_SELF_MSG(null, true),
    TB_SIMPLE_AGG_PERSIST_SELF_MSG(null, true),
    TB_SIMPLE_AGG_ENTITIES_SELF_MSG(null, true),

    OWNER_CHANGED("Owner changed"),
    ADDED_TO_ENTITY_GROUP("Added to Group"),
    REMOVED_FROM_ENTITY_GROUP("Removed from Group"),
    REST_API_REQUEST("REST API request"),
    generateReport("Generate Report"),
    OPC_UA_INT_SUCCESS,
    OPC_UA_INT_FAILURE,

    // Custom or N/A type:
    NA;

    public static final List<String> NODE_CONNECTIONS = EnumSet.allOf(TbMsgType.class).stream()
            .filter(tbMsgType -> !tbMsgType.isTellSelfOnly())
            .map(TbMsgType::getRuleNodeConnection)
            .filter(connection -> !TbNodeConnectionType.OTHER.equals(connection))
            .collect(Collectors.toUnmodifiableList());

    @Getter
    private final String ruleNodeConnection;

    @Getter
    private final boolean tellSelfOnly;

    TbMsgType(String ruleNodeConnection, boolean tellSelfOnly) {
        this.ruleNodeConnection = StringUtils.isNotEmpty(ruleNodeConnection) ? ruleNodeConnection : TbNodeConnectionType.OTHER;
        this.tellSelfOnly = tellSelfOnly;
    }

    TbMsgType(String ruleNodeConnection) {
        this(ruleNodeConnection, false);
    }

    TbMsgType() {
        this(null, false);
    }

}
