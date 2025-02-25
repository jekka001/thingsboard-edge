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
package org.thingsboard.server.common.data.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.thingsboard.server.common.data.StringUtils;

@Data
@Schema
public class StatisticsEventFilter implements EventFilter {

    @Schema(description = "String value representing the server name, identifier or ip address where the platform is running", example = "ip-172-31-24-152")
    protected String server;
    @Schema(description = "The minimum number of successfully processed messages", example = "25")
    protected Integer minMessagesProcessed;
    @Schema(description = "The maximum number of successfully processed messages", example = "250")
    protected Integer maxMessagesProcessed;
    @Schema(description = "The minimum number of errors occurred during messages processing", example = "30")
    protected Integer minErrorsOccurred;
    @Schema(description = "The maximum number of errors occurred during messages processing", example = "300")
    protected Integer maxErrorsOccurred;

    @Override
    public EventType getEventType() {
        return EventType.STATS;
    }

    @Override
    public boolean isNotEmpty() {
        return !StringUtils.isEmpty(server)
                || (minMessagesProcessed != null && minMessagesProcessed > 0) || (minErrorsOccurred != null && minErrorsOccurred > 0)
                || (maxMessagesProcessed != null && maxMessagesProcessed > 0) || (maxErrorsOccurred != null && maxErrorsOccurred > 0);
    }
}
