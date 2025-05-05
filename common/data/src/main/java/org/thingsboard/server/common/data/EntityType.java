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
package org.thingsboard.server.common.data;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Andrew Shvayka
 */
public enum EntityType {
    TENANT(1),
    CUSTOMER(2, true),
    USER(3, "tb_user", true),
    DASHBOARD(4, true),
    ASSET(5, true),
    DEVICE(6, true),
    ALARM(7),
    ENTITY_GROUP(100) {
        // backward compatibility for TbOriginatorTypeSwitchNode to return correct rule node connection.
        @Override
        public String getNormalName() {
            return "Entity Group";
        }
    },
    CONVERTER(101),
    INTEGRATION(102),
    RULE_CHAIN(11),
    RULE_NODE(12),
    SCHEDULER_EVENT(103),
    BLOB_ENTITY(104),
    ENTITY_VIEW(15, true) {
        // backward compatibility for TbOriginatorTypeSwitchNode to return correct rule node connection.
        @Override
        public String getNormalName() {
            return "Entity View";
        }
    },
    WIDGETS_BUNDLE(16),
    WIDGET_TYPE(17),
    ROLE(105),
    GROUP_PERMISSION(106),
    TENANT_PROFILE(20),
    DEVICE_PROFILE(21),
    ASSET_PROFILE(22),
    API_USAGE_STATE(23),
    TB_RESOURCE(24, "resource"),
    OTA_PACKAGE(25),
    EDGE(26, true),
    RPC(27),
    QUEUE(28),
    NOTIFICATION_TARGET(29),
    NOTIFICATION_TEMPLATE(30),
    NOTIFICATION_REQUEST(31),
    NOTIFICATION(32),
    NOTIFICATION_RULE(33),
    QUEUE_STATS(34),
    OAUTH2_CLIENT(35),
    DOMAIN(36),
    MOBILE_APP(37),
    MOBILE_APP_BUNDLE(38),
    SECRET(39);

    @Getter
    private final int protoNumber; // Corresponds to EntityTypeProto
    @Getter
    private final String tableName;
    @Getter
    private final boolean groupEntityType;
    @Getter
    private final String normalName = StringUtils.capitalize(StringUtils.removeStart(name(), "TB_")
            .toLowerCase().replaceAll("_", " "));

    public static final List<EntityType> GROUP_ENTITY_TYPES = EnumSet.allOf(EntityType.class).stream()
            .filter(EntityType::isGroupEntityType).collect(Collectors.toUnmodifiableList());

    public static final List<String> NORMAL_NAMES = EnumSet.allOf(EntityType.class).stream()
            .map(EntityType::getNormalName).collect(Collectors.toUnmodifiableList());

    EntityType(int protoNumber) {
        this(protoNumber, false);
    }

    EntityType(int protoNumber, boolean groupEntityType) {
        this.protoNumber = protoNumber;
        this.groupEntityType = groupEntityType;
        this.tableName = name().toLowerCase();
    }

    EntityType(int protoNumber, String tableName) {
        this(protoNumber, tableName, false);
    }

    EntityType(int protoNumber, String tableName, boolean groupEntityType) {
        this.protoNumber = protoNumber;
        this.tableName = tableName;
        this.groupEntityType = groupEntityType;
    }

}
