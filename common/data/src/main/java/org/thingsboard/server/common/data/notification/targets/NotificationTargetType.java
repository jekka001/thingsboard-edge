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
package org.thingsboard.server.common.data.notification.targets;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.thingsboard.server.common.data.notification.NotificationDeliveryMethod;

import java.util.Arrays;
import java.util.Set;

@RequiredArgsConstructor
public enum NotificationTargetType {

    PLATFORM_USERS(Set.of(NotificationDeliveryMethod.WEB, NotificationDeliveryMethod.EMAIL, NotificationDeliveryMethod.SMS)),
    SLACK(Set.of(NotificationDeliveryMethod.SLACK)),
    MICROSOFT_TEAMS(Set.of(NotificationDeliveryMethod.MICROSOFT_TEAMS));

    @Getter
    private final Set<NotificationDeliveryMethod> supportedDeliveryMethods;

    public static NotificationTargetType forDeliveryMethod(NotificationDeliveryMethod deliveryMethod) {
        return Arrays.stream(values())
                .filter(targetType -> targetType.getSupportedDeliveryMethods().contains(deliveryMethod))
                .findFirst().orElse(null);
    }

}
