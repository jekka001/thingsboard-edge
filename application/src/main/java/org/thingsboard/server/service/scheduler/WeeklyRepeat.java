/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.scheduler;

import lombok.Data;

import java.util.Calendar;
import java.util.List;

/**
 * Created by ashvayka on 28.11.17.
 */
@Data
public class WeeklyRepeat implements SchedulerRepeat {

    public static final long _1DAY = 1000 * 60 * 60 * 24;

    private long endsOn;
    private List<Integer> repeatOn;

    @Override
    public SchedulerRepeatType getType() {
        return SchedulerRepeatType.WEEKLY;
    }

    @Override
    public long getNext(long startTime, long ts, String timezone) {
        Calendar calendar = SchedulerUtils.getCalendarWithTimeZone(timezone);

        for (long tmp = startTime; tmp < endsOn; tmp += _1DAY) {
            if (tmp > ts) {
                calendar.setTimeInMillis(tmp);
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
                dayOfWeek = dayOfWeek - 1; // The UI calendar starts from 0;
                if (repeatOn.contains(dayOfWeek)) {
                    return tmp;
                }
            }
        }
        return 0L;
    }
}
