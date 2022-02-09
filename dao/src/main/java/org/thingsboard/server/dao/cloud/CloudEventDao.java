/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.cloud;

import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.Dao;

import java.util.UUID;

/**
 * The Interface CloudEventDao.
 */
public interface CloudEventDao extends Dao<CloudEvent> {

    /**
     * Save or update cloud event object
     *
     * @param cloudEvent the event object
     * @return saved cloud event object future
     */
    CloudEvent save(CloudEvent cloudEvent);


    /**
     * Find cloud events by tenantId and pageLink.
     *
     * @param tenantId the tenantId
     * @param pageLink the pageLink
     * @return the event list
     */
    PageData<CloudEvent> findCloudEvents(UUID tenantId, TimePageLink pageLink);

    PageData<CloudEvent> findCloudEventsByEntityIdAndCloudEventActionAndCloudEventType(
            UUID tenantId,
            UUID entityId,
            CloudEventType cloudEventType,
            String cloudEventAction,
            TimePageLink pageLink);

    /**
     * Executes stored procedure to cleanup old cloud events.
     * @param eventsTtl the ttl for cloud events in seconds
     */
    void cleanupEvents(long eventsTtl);

}
