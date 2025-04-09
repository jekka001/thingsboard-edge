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
package org.thingsboard.server.common.data.edqs.fields;

import com.fasterxml.jackson.databind.JsonNode;
import org.thingsboard.server.common.data.ApiUsageState;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceProfileType;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.HasEntityType;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.blob.BlobEntity;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UUIDBased;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.queue.QueueStats;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetsBundle;

import java.util.UUID;

public class FieldsUtil {

    public static EntityFields toFields(Object entity) {
        if (entity instanceof Customer customer) {
            return toFields(customer);
        } else if (entity instanceof Tenant tenant) {
            return toFields(tenant);
        } else if (entity instanceof TenantProfile tenantProfile) {
            return toFields(tenantProfile);
        } else if (entity instanceof Device device) {
            return toFields(device);
        } else if (entity instanceof Asset asset) {
            return toFields(asset);
        } else if (entity instanceof Edge edge) {
            return toFields(edge);
        } else if (entity instanceof EntityView entityView) {
            return toFields(entityView);
        } else if (entity instanceof User user) {
            return toFields(user);
        } else if (entity instanceof Dashboard dashboard) {
            return toFields(dashboard);
        } else if (entity instanceof RuleChain ruleChain) {
            return toFields(ruleChain);
        } else if (entity instanceof RuleNode ruleNode) {
            return toFields(ruleNode);
        } else if (entity instanceof WidgetType widgetType) {
            return toFields(widgetType);
        } else if (entity instanceof WidgetsBundle widgetsBundle) {
            return toFields(widgetsBundle);
        } else if (entity instanceof Converter converter) {
            return toFields(converter);
        } else if (entity instanceof Integration integration) {
            return toFields(integration);
        } else if (entity instanceof SchedulerEvent schedulerEvent) {
            return toFields(schedulerEvent);
        } else if (entity instanceof Role role) {
            return toFields(role);
        } else if (entity instanceof EntityGroup entityGroup) {
            return toFields(entityGroup);
        } else if (entity instanceof DeviceProfile deviceProfile) {
            return toFields(deviceProfile);
        } else if (entity instanceof AssetProfile assetProfile) {
            return toFields(assetProfile);
        } else if (entity instanceof QueueStats queueStats) {
            return toFields(queueStats);
        } else if (entity instanceof ApiUsageState apiUsageState) {
            return toFields(apiUsageState);
        } else if (entity instanceof BlobEntity blobEntity) {
            return toFields(blobEntity);
        } else {
            throw new IllegalArgumentException("Unsupported entity type: " + entity.getClass().getName());
        }
    }

    private static CustomerFields toFields(Customer entity) {
        return CustomerFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getTitle())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .email(entity.getEmail())
                .country(entity.getCountry())
                .state(entity.getState())
                .city(entity.getCity())
                .address(entity.getAddress())
                .address2(entity.getAddress2())
                .zip(entity.getZip())
                .phone(entity.getPhone())
                .version(entity.getVersion())
                .build();
    }

    private static TenantFields toFields(Tenant entity) {
        return TenantFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getTitle())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .email(entity.getEmail())
                .country(entity.getCountry())
                .state(entity.getState())
                .city(entity.getCity())
                .address(entity.getAddress())
                .address2(entity.getAddress2())
                .zip(entity.getZip())
                .phone(entity.getPhone())
                .region(entity.getRegion())
                .version(entity.getVersion())
                .build();
    }

    private static TenantProfileFields toFields(TenantProfile tenantProfile) {
        return TenantProfileFields.builder()
                .id(tenantProfile.getUuidId())
                .createdTime(tenantProfile.getCreatedTime())
                .name(tenantProfile.getName())
                .isDefault(tenantProfile.isDefault())
                .build();
    }

    private static DeviceFields toFields(Device entity) {
        return DeviceFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType())
                .deviceProfileId(entity.getDeviceProfileId().getId())
                .label(entity.getLabel())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static AssetFields toFields(Asset entity) {
        return AssetFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType())
                .assetProfileId(entity.getAssetProfileId().getId())
                .label(entity.getLabel())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static EdgeFields toFields(Edge entity) {
        return EdgeFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType())
                .label(entity.getLabel())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static EntityViewFields toFields(EntityView entity) {
        return EntityViewFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static UserFields toFields(User entity) {
        return UserFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static DashboardFields toFields(Dashboard entity) {
        return DashboardFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getTitle())
                .version(entity.getVersion())
                .build();
    }

    private static RuleChainFields toFields(RuleChain entity) {
        return RuleChainFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static RuleNodeFields toFields(RuleNode entity) {
        return RuleNodeFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .build();
    }

    private static WidgetTypeFields toFields(WidgetType entity) {
        return WidgetTypeFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .version(entity.getVersion())
                .build();
    }

    private static WidgetsBundleFields toFields(WidgetsBundle entity) {
        return WidgetsBundleFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .version(entity.getVersion())
                .build();
    }

    private static ConverterFields toFields(Converter entity) {
        return ConverterFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .type(entity.getType().name())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static IntegrationFields toFields(Integration entity) {
        return IntegrationFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .type(entity.getType().name())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static SchedulerEventFields toFields(SchedulerEvent entity) {
        return SchedulerEventFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType())
                .originatorId(entity.getOriginatorId())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static RoleFields toFields(Role entity) {
        return RoleFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType().name())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .version(entity.getVersion())
                .build();
    }

    private static EntityGroupFields toFields(EntityGroup entity) {
        return EntityGroupFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .type(entity.getType().name())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .ownerId(entity.getOwnerId().getId())
                .ownerType(entity.getOwnerId().getEntityType())
                .version(entity.getVersion())
                .build();
    }

    private static AssetProfileFields toFields(AssetProfile entity) {
        return AssetProfileFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .isDefault(entity.isDefault())
                .version(entity.getVersion())
                .build();
    }

    private static DeviceProfileFields toFields(DeviceProfile entity) {
        return DeviceProfileFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .name(entity.getName())
                .type(DeviceProfileType.DEFAULT.name())
                .isDefault(entity.isDefault())
                .version(entity.getVersion())
                .build();
    }

    private static QueueStatsFields toFields(QueueStats entity) {
        return QueueStatsFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .queueName(entity.getQueueName())
                .serviceId(entity.getServiceId())
                .build();
    }

    private static ApiUsageStateFields toFields(ApiUsageState entity) {
        return ApiUsageStateFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(entity.getEntityId().getEntityType() == EntityType.CUSTOMER ? entity.getEntityId().getId() : null)
                .entityId(entity.getEntityId())
                .transportState(entity.getTransportState())
                .dbStorageState(entity.getDbStorageState())
                .reExecState(entity.getReExecState())
                .jsExecState(entity.getJsExecState())
                .tbelExecState(entity.getTbelExecState())
                .emailExecState(entity.getEmailExecState())
                .smsExecState(entity.getSmsExecState())
                .alarmExecState(entity.getAlarmExecState())
                .version(entity.getVersion())
                .build();
    }

    private static BlobEntityFields toFields(BlobEntity entity) {
        return BlobEntityFields.builder()
                .id(entity.getUuidId())
                .createdTime(entity.getCreatedTime())
                .customerId(getCustomerId(entity.getCustomerId()))
                .name(entity.getName())
                .type(entity.getType())
                .additionalInfo(getText(entity.getAdditionalInfo()))
                .build();
    }

    public static String getText(JsonNode node) {
        return node != null && !node.isNull() ? node.toString() : "";
    }

    private static UUID getCustomerId(CustomerId customerId) {
        return (customerId != null && !customerId.getId().equals(CustomerId.NULL_UUID)) ? customerId.getId() : null;
    }

}
