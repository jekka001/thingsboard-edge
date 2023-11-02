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
package org.thingsboard.server.common.data;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.cloud.CloudEventType;

@Slf4j
public final class CloudUtils {

    private CloudUtils() {
    }

    public static CloudEventType getCloudEventTypeByEntityType(EntityType entityType) {
        switch (entityType) {
            case DEVICE:
                return CloudEventType.DEVICE;
            case DEVICE_PROFILE:
                return CloudEventType.DEVICE_PROFILE;
            case ASSET:
                return CloudEventType.ASSET;
            case ASSET_PROFILE:
                return CloudEventType.ASSET_PROFILE;
            case ENTITY_VIEW:
                return CloudEventType.ENTITY_VIEW;
            case DASHBOARD:
                return CloudEventType.DASHBOARD;
            case USER:
                return CloudEventType.USER;
            case ALARM:
                return CloudEventType.ALARM;
            case TENANT:
                return CloudEventType.TENANT;
            case TENANT_PROFILE:
                return CloudEventType.TENANT_PROFILE;
            case CUSTOMER:
                return CloudEventType.CUSTOMER;
            case ENTITY_GROUP:
                return CloudEventType.ENTITY_GROUP;
            case EDGE:
                return CloudEventType.EDGE;
            case TB_RESOURCE:
                return CloudEventType.TB_RESOURCE;
            default:
                log.warn("Unsupported entity type: [{}]", entityType);
                return null;
        }
    }
}
