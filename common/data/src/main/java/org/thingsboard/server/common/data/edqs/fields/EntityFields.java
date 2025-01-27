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
package org.thingsboard.server.common.data.edqs.fields;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thingsboard.server.common.data.id.EntityId;

import java.util.UUID;

public interface EntityFields {

    Logger log = LoggerFactory.getLogger(EntityFields.class);

    default UUID getId() {
        return null;
    }

    default UUID getTenantId() {
        return null;
    }

    default UUID getCustomerId() {
        return null;
    }

    default long getCreatedTime() {
        return 0;
    }

    default String getName() {
        return "";
    }

    default String getType() {
        return "";
    }

    default String getLabel() {
        return "";
    }

    default String getAdditionalInfo() {
        return "";
    }

    default String getEmail() {
        return "";
    }

    default String getCountry() {
        return "";
    }

    default String getState() {
        return "";
    }

    default String getCity() {
        return "";
    }

    default String getAddress() {
        return "";
    }

    default String getAddress2() {
        return "";
    }

    default String getZip() {
        return "";
    }

    default String getPhone() {
        return "";
    }

    default String getRegion() {
        return "";
    }

    default String getFirstName() {
        return "";
    }

    default String getLastName() {
        return "";
    }

    default boolean isEdgeTemplate() {
        return false;
    }

    default String getConfiguration() {
        return "";
    }

    default String getSchedule() {
        return "";
    }

    default EntityId getOriginatorId() {
        return null;
    }

    default String getQueueName() {
        return "";
    }

    default String getServiceId() {
        return "";
    }

    default boolean isDefault() {
        return false;
    }

    default UUID getOwnerId() {
        return null;
    }

    default Long getVersion() {
        return null;
    }

    default String getAsString(String key) {
        return switch (key) {
            case "createdTime" -> Long.toString(getCreatedTime());
            case "type" -> getType();
            case "label" -> getLabel();
            case "additionalInfo" -> getAdditionalInfo();
            case "email" -> getEmail();
            case "country" -> getCountry();
            case "state" -> getState();
            case "city" -> getCity();
            case "address" -> getAddress();
            case "address2" -> getAddress2();
            case "zip" -> getZip();
            case "phone" -> getPhone();
            case "region" -> getRegion();
            case "firstName" -> getFirstName();
            case "lastName" -> getLastName();
            case "edgeTemplate" -> Boolean.toString(isEdgeTemplate());
            case "configuration" -> getConfiguration();
            case "schedule" -> getSchedule();
            case "originatorId" -> getOriginatorId().getId().toString();
            case "originatorType" -> getOriginatorId().getEntityType().toString();
            case "queueName" -> getQueueName();
            case "serviceId" -> getServiceId();
            default -> {
                log.warn("Unknown field '{}'", key);
                yield null;
            }
        };
    }

}
