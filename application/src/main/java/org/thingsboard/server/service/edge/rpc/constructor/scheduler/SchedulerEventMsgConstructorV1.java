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
package org.thingsboard.server.service.edge.rpc.constructor.scheduler;

import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.gen.edge.v1.SchedulerEventUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.queue.util.TbCoreComponent;

@Component
@TbCoreComponent
public class SchedulerEventMsgConstructorV1 extends BaseSchedulerEventMsgConstructor {

    @Override
    public SchedulerEventUpdateMsg constructSchedulerEventUpdatedMsg(UpdateMsgType msgType, SchedulerEvent schedulerEvent) {
        SchedulerEventUpdateMsg.Builder builder = SchedulerEventUpdateMsg.newBuilder();
        builder.setMsgType(msgType);
        builder.setIdMSB(schedulerEvent.getId().getId().getMostSignificantBits());
        builder.setIdLSB(schedulerEvent.getId().getId().getLeastSignificantBits());
        builder.setName(schedulerEvent.getName());
        builder.setType(schedulerEvent.getType());
        if (schedulerEvent.getSchedule() != null) {
            builder.setSchedule(JacksonUtil.toString(schedulerEvent.getSchedule()));
        }
        if (schedulerEvent.getConfiguration() != null) {
            builder.setConfiguration(JacksonUtil.toString(schedulerEvent.getConfiguration()));
        }
        if (schedulerEvent.getCustomerId() != null) {
            builder.setCustomerIdMSB(schedulerEvent.getCustomerId().getId().getMostSignificantBits())
                    .setCustomerIdLSB(schedulerEvent.getCustomerId().getId().getLeastSignificantBits());
        }
        if (schedulerEvent.getAdditionalInfo() != null) {
            builder.setAdditionalInfo(JacksonUtil.toString(schedulerEvent.getAdditionalInfo()));
        }
        if (schedulerEvent.getOriginatorId() != null && !schedulerEvent.getOriginatorId().getId().equals(ModelConstants.NULL_UUID)) {
            builder.setOriginatorType(schedulerEvent.getOriginatorId().getEntityType().name())
                    .setOriginatorIdMSB(schedulerEvent.getOriginatorId().getId().getMostSignificantBits())
                    .setOriginatorIdLSB(schedulerEvent.getOriginatorId().getId().getLeastSignificantBits());
        }
        return builder.build();
    }

}
