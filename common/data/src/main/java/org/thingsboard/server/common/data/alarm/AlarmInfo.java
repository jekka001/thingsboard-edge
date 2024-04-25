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
package org.thingsboard.server.common.data.alarm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Schema
public class AlarmInfo extends Alarm {

    private static final long serialVersionUID = 2807343093519543363L;

    @Getter
    @Setter
    @Schema(description = "Alarm originator name", example = "Thermostat")
    private String originatorName;

    @Getter
    @Setter
    @Schema(description = "Alarm originator label", example = "Thermostat label")
    private String originatorLabel;

    @Getter
    @Setter
    @Schema(description = "Alarm assignee")
    private AlarmAssignee assignee;

    public AlarmInfo() {
        super();
    }

    public AlarmInfo(Alarm alarm) {
        super(alarm);
    }

    public AlarmInfo(AlarmInfo alarmInfo) {
        super(alarmInfo);
        this.originatorName = alarmInfo.originatorName;
        this.originatorLabel = alarmInfo.originatorLabel;
        this.assignee = alarmInfo.getAssignee();
    }

    public AlarmInfo(Alarm alarm, String originatorName, String originatorLabel, AlarmAssignee assignee) {
        super(alarm);
        this.originatorName = originatorName;
        this.originatorLabel = originatorLabel;
        this.assignee = assignee;
    }

}
