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
package org.thingsboard.server.common.data.notification.rule.trigger.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "triggerType")
@JsonSubTypes({
        @Type(value = AlarmNotificationRuleTriggerConfig.class, name = "ALARM"),
        @Type(value = DeviceActivityNotificationRuleTriggerConfig.class, name = "DEVICE_ACTIVITY"),
        @Type(value = EntityActionNotificationRuleTriggerConfig.class, name = "ENTITY_ACTION"),
        @Type(value = AlarmCommentNotificationRuleTriggerConfig.class, name = "ALARM_COMMENT"),
        @Type(value = RuleEngineComponentLifecycleEventNotificationRuleTriggerConfig.class, name = "RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT"),
        @Type(value = AlarmAssignmentNotificationRuleTriggerConfig.class, name = "ALARM_ASSIGNMENT"),
        @Type(value = NewPlatformVersionNotificationRuleTriggerConfig.class, name = "NEW_PLATFORM_VERSION"),
        @Type(value = EntitiesLimitNotificationRuleTriggerConfig.class, name = "ENTITIES_LIMIT"),
        @Type(value = ApiUsageLimitNotificationRuleTriggerConfig.class, name = "API_USAGE_LIMIT"),
        @Type(value = IntegrationLifecycleEventNotificationRuleTriggerConfig.class, name = "INTEGRATION_LIFECYCLE_EVENT"),
        @Type(value = RateLimitsNotificationRuleTriggerConfig.class, name = "RATE_LIMITS"),
        @Type(value = EdgeConnectionNotificationRuleTriggerConfig.class, name = "EDGE_CONNECTION"),
        @Type(value = EdgeCommunicationFailureNotificationRuleTriggerConfig.class, name = "EDGE_COMMUNICATION_FAILURE"),
        @Type(value = TaskProcessingFailureNotificationRuleTriggerConfig.class, name = "TASK_PROCESSING_FAILURE")
})
public interface NotificationRuleTriggerConfig extends Serializable {

    NotificationRuleTriggerType getTriggerType();

    @JsonIgnore
    default String getDeduplicationKey() {
        return "#";
    }

}
