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
package org.thingsboard.server.service.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.action.TbChangeOwnerNode;
import org.thingsboard.rule.engine.action.TbSaveToCustomCassandraTableNode;
import org.thingsboard.rule.engine.aws.lambda.TbAwsLambdaNode;
import org.thingsboard.rule.engine.rest.TbSendRestApiCallReplyNode;
import org.thingsboard.rule.engine.telemetry.TbMsgAttributesNode;
import org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNode;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmComment;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.asset.AssetProfile;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.domain.DomainInfo;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.AssetProfileId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.DomainId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.GroupPermissionId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.NotificationRuleId;
import org.thingsboard.server.common.data.id.NotificationTargetId;
import org.thingsboard.server.common.data.id.NotificationTemplateId;
import org.thingsboard.server.common.data.id.OAuth2ClientId;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.QueueId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.SchedulerEventId;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.id.WidgetsBundleId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.menu.CustomMenu;
import org.thingsboard.server.common.data.notification.rule.NotificationRule;
import org.thingsboard.server.common.data.notification.targets.NotificationTarget;
import org.thingsboard.server.common.data.notification.template.NotificationTemplate;
import org.thingsboard.server.common.data.oauth2.OAuth2Client;
import org.thingsboard.server.common.data.ota.DeviceGroupOtaPackage;
import org.thingsboard.server.common.data.permission.GroupPermission;
import org.thingsboard.server.common.data.queue.Queue;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.scheduler.SchedulerEvent;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.UserCredentials;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.gen.edge.v1.AlarmCommentUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AlarmUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AssetUpdateMsg;
import org.thingsboard.server.gen.edge.v1.AttributeDeleteMsg;
import org.thingsboard.server.gen.edge.v1.ConverterUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CustomMenuProto;
import org.thingsboard.server.gen.edge.v1.CustomTranslationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.CustomerUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DashboardUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceGroupOtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.DeviceRpcCallMsg;
import org.thingsboard.server.gen.edge.v1.DeviceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EdgeConfiguration;
import org.thingsboard.server.gen.edge.v1.EdgeVersion;
import org.thingsboard.server.gen.edge.v1.EntityDataProto;
import org.thingsboard.server.gen.edge.v1.EntityGroupUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.v1.GroupPermissionProto;
import org.thingsboard.server.gen.edge.v1.IntegrationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.NotificationRuleUpdateMsg;
import org.thingsboard.server.gen.edge.v1.NotificationTargetUpdateMsg;
import org.thingsboard.server.gen.edge.v1.NotificationTemplateUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OAuth2ClientUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OAuth2DomainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.OtaPackageUpdateMsg;
import org.thingsboard.server.gen.edge.v1.QueueUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RelationUpdateMsg;
import org.thingsboard.server.gen.edge.v1.ResourceUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RoleProto;
import org.thingsboard.server.gen.edge.v1.RpcRequestMsg;
import org.thingsboard.server.gen.edge.v1.RpcResponseMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainMetadataUpdateMsg;
import org.thingsboard.server.gen.edge.v1.RuleChainUpdateMsg;
import org.thingsboard.server.gen.edge.v1.SchedulerEventUpdateMsg;
import org.thingsboard.server.gen.edge.v1.TenantProfileUpdateMsg;
import org.thingsboard.server.gen.edge.v1.TenantUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UserCredentialsUpdateMsg;
import org.thingsboard.server.gen.edge.v1.UserUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WhiteLabelingProto;
import org.thingsboard.server.gen.edge.v1.WidgetTypeUpdateMsg;
import org.thingsboard.server.gen.edge.v1.WidgetsBundleUpdateMsg;
import org.thingsboard.server.gen.transport.TransportProtos;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class EdgeMsgConstructorUtils {
    public static final Map<EdgeVersion, Map<String, String>> IGNORED_PARAMS_BY_EDGE_VERSION = Map.of(
            EdgeVersion.V_3_8_0,
            Map.of(
                    TbMsgTimeseriesNode.class.getName(), "processingSettings",
                    TbMsgAttributesNode.class.getName(), "processingSettings",
                    TbSaveToCustomCassandraTableNode.class.getName(), "defaultTtl"
            ),
            EdgeVersion.V_3_7_0,
            Map.of(
                    TbMsgTimeseriesNode.class.getName(), "processingSettings",
                    TbMsgAttributesNode.class.getName(), "processingSettings",
                    TbSaveToCustomCassandraTableNode.class.getName(), "defaultTtl",
                    TbChangeOwnerNode.class.getName(), "createOwnerOnOriginatorLevel"
            )
    );

    public static final Map<EdgeVersion, Set<String>> EXCLUDED_NODES_BY_EDGE_VERSION = Map.of(
            EdgeVersion.V_3_7_0,
            Set.of(
                    TbSendRestApiCallReplyNode.class.getName(),
                    TbAwsLambdaNode.class.getName()
            )
    );

    public static AlarmUpdateMsg constructAlarmUpdatedMsg(UpdateMsgType msgType, Alarm alarm) {
        return AlarmUpdateMsg.newBuilder().setMsgType(msgType)
                .setEntity(JacksonUtil.toString(alarm))
                .setIdMSB(alarm.getId().getId().getMostSignificantBits())
                .setIdLSB(alarm.getId().getId().getLeastSignificantBits()).build();
    }

    public static AlarmCommentUpdateMsg constructAlarmCommentUpdatedMsg(UpdateMsgType msgType, AlarmComment alarmComment) {
        return AlarmCommentUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(alarmComment)).build();
    }

    public static AssetUpdateMsg constructAssetUpdatedMsg(UpdateMsgType msgType, Asset asset, EntityGroupId entityGroupId) {
        AssetUpdateMsg.Builder builder = AssetUpdateMsg.newBuilder()
                .setMsgType(msgType).setEntity(JacksonUtil.toString(asset))
                .setIdMSB(asset.getUuidId().getMostSignificantBits())
                .setIdLSB(asset.getUuidId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static AssetUpdateMsg constructAssetDeleteMsg(AssetId assetId, EntityGroupId entityGroupId) {
        AssetUpdateMsg.Builder builder = AssetUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(assetId.getId().getMostSignificantBits())
                .setIdLSB(assetId.getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static AssetProfileUpdateMsg constructAssetProfileUpdatedMsg(UpdateMsgType msgType, AssetProfile assetProfile) {
        return AssetProfileUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(assetProfile))
                .setIdMSB(assetProfile.getId().getId().getMostSignificantBits())
                .setIdLSB(assetProfile.getId().getId().getLeastSignificantBits()).build();
    }

    public static AssetProfileUpdateMsg constructAssetProfileDeleteMsg(AssetProfileId assetProfileId) {
        return AssetProfileUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(assetProfileId.getId().getMostSignificantBits())
                .setIdLSB(assetProfileId.getId().getLeastSignificantBits()).build();
    }

    public static CustomerUpdateMsg constructCustomerUpdatedMsg(UpdateMsgType msgType, Customer customer, EntityGroupId entityGroupId) {
        CustomerUpdateMsg.Builder builder = CustomerUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(customer))
                .setIdMSB(customer.getId().getId().getMostSignificantBits())
                .setIdLSB(customer.getId().getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static CustomerUpdateMsg constructCustomerDeleteMsg(CustomerId customerId) {
        return CustomerUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(customerId.getId().getMostSignificantBits())
                .setIdLSB(customerId.getId().getLeastSignificantBits()).build();
    }

    public static DashboardUpdateMsg constructDashboardUpdatedMsg(UpdateMsgType msgType, Dashboard dashboard, EntityGroupId entityGroupId) {
        DashboardUpdateMsg.Builder builder = DashboardUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(dashboard))
                .setIdMSB(dashboard.getId().getId().getMostSignificantBits())
                .setIdLSB(dashboard.getId().getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static DashboardUpdateMsg constructDashboardDeleteMsg(DashboardId dashboardId, EntityGroupId entityGroupId) {
        DashboardUpdateMsg.Builder builder = DashboardUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(dashboardId.getId().getMostSignificantBits())
                .setIdLSB(dashboardId.getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static DeviceUpdateMsg constructDeviceUpdatedMsg(UpdateMsgType msgType, Device device, EntityGroupId entityGroupId) {
        DeviceUpdateMsg.Builder builder = DeviceUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(device))
                .setIdMSB(device.getId().getId().getMostSignificantBits())
                .setIdLSB(device.getId().getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static DeviceUpdateMsg constructDeviceDeleteMsg(DeviceId deviceId, EntityGroupId entityGroupId) {
        DeviceUpdateMsg.Builder builder = DeviceUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(deviceId.getId().getMostSignificantBits())
                .setIdLSB(deviceId.getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static DeviceCredentialsUpdateMsg constructDeviceCredentialsUpdatedMsg(DeviceCredentials deviceCredentials) {
        return DeviceCredentialsUpdateMsg.newBuilder().setEntity(JacksonUtil.toString(deviceCredentials)).build();
    }

    public static DeviceProfileUpdateMsg constructDeviceProfileUpdatedMsg(UpdateMsgType msgType, DeviceProfile deviceProfile) {
        return DeviceProfileUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(deviceProfile))
                .setIdMSB(deviceProfile.getId().getId().getMostSignificantBits())
                .setIdLSB(deviceProfile.getId().getId().getLeastSignificantBits()).build();
    }

    public static DeviceProfileUpdateMsg constructDeviceProfileDeleteMsg(DeviceProfileId deviceProfileId) {
        return DeviceProfileUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(deviceProfileId.getId().getMostSignificantBits())
                .setIdLSB(deviceProfileId.getId().getLeastSignificantBits()).build();
    }

    public static DeviceRpcCallMsg constructDeviceRpcCallMsg(UUID deviceId, JsonNode body) {
        DeviceRpcCallMsg.Builder builder = constructDeviceRpcMsg(deviceId, body);
        if (body.has("error") || body.has("response")) {
            RpcResponseMsg.Builder responseBuilder = RpcResponseMsg.newBuilder();
            if (body.has("error")) {
                responseBuilder.setError(body.get("error").asText());
            } else {
                responseBuilder.setResponse(body.get("response").asText());
            }
            builder.setResponseMsg(responseBuilder.build());
        } else {
            RpcRequestMsg.Builder requestBuilder = RpcRequestMsg.newBuilder();
            requestBuilder.setMethod(body.get("method").asText());
            requestBuilder.setParams(body.get("params").asText());
            builder.setRequestMsg(requestBuilder.build());
        }
        return builder.build();
    }

    private static DeviceRpcCallMsg.Builder constructDeviceRpcMsg(UUID deviceId, JsonNode body) {
        DeviceRpcCallMsg.Builder builder = DeviceRpcCallMsg.newBuilder()
                .setDeviceIdMSB(deviceId.getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getLeastSignificantBits())
                .setRequestId(body.get("requestId").asInt());
        if (body.get("oneway") != null) {
            builder.setOneway(body.get("oneway").asBoolean());
        }
        if (body.get("requestUUID") != null) {
            UUID requestUUID = UUID.fromString(body.get("requestUUID").asText());
            builder.setRequestUuidMSB(requestUUID.getMostSignificantBits())
                    .setRequestUuidLSB(requestUUID.getLeastSignificantBits());
        }
        if (body.get("expirationTime") != null) {
            builder.setExpirationTime(body.get("expirationTime").asLong());
        }
        if (body.get("persisted") != null) {
            builder.setPersisted(body.get("persisted").asBoolean());
        }
        if (body.get("retries") != null) {
            builder.setRetries(body.get("retries").asInt());
        }
        if (body.get("additionalInfo") != null) {
            builder.setAdditionalInfo(JacksonUtil.toString(body.get("additionalInfo")));
        }
        if (body.get("serviceId") != null) {
            builder.setServiceId(body.get("serviceId").asText());
        }
        if (body.get("sessionId") != null) {
            builder.setSessionId(body.get("sessionId").asText());
        }
        return builder;
    }

    public static EdgeConfiguration constructEdgeConfiguration(Edge edge) {
        EdgeConfiguration.Builder builder = EdgeConfiguration.newBuilder()
                .setEdgeIdMSB(edge.getId().getId().getMostSignificantBits())
                .setEdgeIdLSB(edge.getId().getId().getLeastSignificantBits())
                .setTenantIdMSB(edge.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(edge.getTenantId().getId().getLeastSignificantBits())
                .setName(edge.getName())
                .setType(edge.getType())
                .setRoutingKey(edge.getRoutingKey())
                .setSecret(edge.getSecret())
                .setEdgeLicenseKey(edge.getEdgeLicenseKey())
                .setCloudEndpoint(edge.getCloudEndpoint())
                .setAdditionalInfo(JacksonUtil.toString(edge.getAdditionalInfo()))
                .setCloudType("PE");
        if (edge.getCustomerId() != null) {
            builder.setCustomerIdMSB(edge.getCustomerId().getId().getMostSignificantBits())
                    .setCustomerIdLSB(edge.getCustomerId().getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static EntityViewUpdateMsg constructEntityViewUpdatedMsg(UpdateMsgType msgType, EntityView entityView, EntityGroupId entityGroupId) {
        EntityViewUpdateMsg.Builder builder = EntityViewUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(entityView))
                .setIdMSB(entityView.getId().getId().getMostSignificantBits())
                .setIdLSB(entityView.getId().getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static EntityViewUpdateMsg constructEntityViewDeleteMsg(EntityViewId entityViewId, EntityGroupId entityGroupId) {
        EntityViewUpdateMsg.Builder builder = EntityViewUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(entityViewId.getId().getMostSignificantBits())
                .setIdLSB(entityViewId.getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static NotificationRuleUpdateMsg constructNotificationRuleUpdateMsg(UpdateMsgType msgType, NotificationRule notificationRule) {
        return NotificationRuleUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(notificationRule)).build();
    }

    public static NotificationRuleUpdateMsg constructNotificationRuleDeleteMsg(NotificationRuleId notificationRuleId) {
        return NotificationRuleUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(notificationRuleId.getId().getMostSignificantBits())
                .setIdLSB(notificationRuleId.getId().getLeastSignificantBits()).build();
    }

    public static NotificationTargetUpdateMsg constructNotificationTargetUpdateMsg(UpdateMsgType msgType, NotificationTarget notificationTarget) {
        return NotificationTargetUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(notificationTarget)).build();
    }

    public static NotificationTargetUpdateMsg constructNotificationTargetDeleteMsg(NotificationTargetId notificationTargetId) {
        return NotificationTargetUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(notificationTargetId.getId().getMostSignificantBits())
                .setIdLSB(notificationTargetId.getId().getLeastSignificantBits()).build();
    }

    public static NotificationTemplateUpdateMsg constructNotificationTemplateUpdateMsg(UpdateMsgType msgType, NotificationTemplate notificationTemplate) {
        return NotificationTemplateUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(notificationTemplate)).build();
    }

    public static NotificationTemplateUpdateMsg constructNotificationTemplateDeleteMsg(NotificationTemplateId notificationTemplateId) {
        return NotificationTemplateUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(notificationTemplateId.getId().getMostSignificantBits())
                .setIdLSB(notificationTemplateId.getId().getLeastSignificantBits()).build();
    }

    public static OAuth2ClientUpdateMsg constructOAuth2ClientUpdateMsg(UpdateMsgType msgType, OAuth2Client oAuth2Client) {
        return OAuth2ClientUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(oAuth2Client))
                .setIdMSB(oAuth2Client.getId().getId().getMostSignificantBits())
                .setIdLSB(oAuth2Client.getId().getId().getLeastSignificantBits()).build();
    }

    public static OAuth2ClientUpdateMsg constructOAuth2ClientDeleteMsg(OAuth2ClientId oAuth2ClientId) {
        return OAuth2ClientUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(oAuth2ClientId.getId().getMostSignificantBits())
                .setIdLSB(oAuth2ClientId.getId().getLeastSignificantBits()).build();
    }

    public static OAuth2DomainUpdateMsg constructOAuth2DomainUpdateMsg(UpdateMsgType msgType, DomainInfo domainInfo) {
        return OAuth2DomainUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(domainInfo))
                .setIdMSB(domainInfo.getId().getId().getMostSignificantBits())
                .setIdLSB(domainInfo.getId().getId().getLeastSignificantBits()).build();
    }

    public static OAuth2DomainUpdateMsg constructOAuth2DomainDeleteMsg(DomainId domainId) {
        return OAuth2DomainUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(domainId.getId().getMostSignificantBits())
                .setIdLSB(domainId.getId().getLeastSignificantBits())
                .build();
    }

    public static OtaPackageUpdateMsg constructOtaPackageUpdatedMsg(UpdateMsgType msgType, OtaPackage otaPackage) {
        return OtaPackageUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(otaPackage))
                .setIdMSB(otaPackage.getId().getId().getMostSignificantBits())
                .setIdLSB(otaPackage.getId().getId().getLeastSignificantBits()).build();
    }

    public static OtaPackageUpdateMsg constructOtaPackageDeleteMsg(OtaPackageId otaPackageId) {
        return OtaPackageUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(otaPackageId.getId().getMostSignificantBits())
                .setIdLSB(otaPackageId.getId().getLeastSignificantBits()).build();
    }

    public static QueueUpdateMsg constructQueueUpdatedMsg(UpdateMsgType msgType, Queue queue) {
        return QueueUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(queue))
                .setIdMSB(queue.getId().getId().getMostSignificantBits())
                .setIdLSB(queue.getId().getId().getLeastSignificantBits()).build();
    }

    public static QueueUpdateMsg constructQueueDeleteMsg(QueueId queueId) {
        return QueueUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(queueId.getId().getMostSignificantBits())
                .setIdLSB(queueId.getId().getLeastSignificantBits()).build();
    }

    public static RelationUpdateMsg constructRelationUpdatedMsg(UpdateMsgType msgType, EntityRelation entityRelation) {
        return RelationUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(entityRelation)).build();
    }

    public static ResourceUpdateMsg constructResourceUpdatedMsg(UpdateMsgType msgType, TbResource tbResource) {
        return ResourceUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(tbResource))
                .setIdMSB(tbResource.getId().getId().getMostSignificantBits())
                .setIdLSB(tbResource.getId().getId().getLeastSignificantBits()).build();
    }

    public static ResourceUpdateMsg constructResourceDeleteMsg(TbResourceId tbResourceId) {
        return ResourceUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(tbResourceId.getId().getMostSignificantBits())
                .setIdLSB(tbResourceId.getId().getLeastSignificantBits()).build();
    }

    public static RuleChainUpdateMsg constructRuleChainUpdatedMsg(UpdateMsgType msgType, RuleChain ruleChain, boolean isRoot) {
        boolean isTemplateRoot = ruleChain.isRoot();
        ruleChain.setRoot(isRoot);
        RuleChainUpdateMsg result = RuleChainUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(ruleChain))
                .setIdMSB(ruleChain.getId().getId().getMostSignificantBits())
                .setIdLSB(ruleChain.getId().getId().getLeastSignificantBits()).build();
        ruleChain.setRoot(isTemplateRoot);
        return result;
    }

    public static RuleChainUpdateMsg constructRuleChainDeleteMsg(RuleChainId ruleChainId) {
        return RuleChainUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(ruleChainId.getId().getMostSignificantBits())
                .setIdLSB(ruleChainId.getId().getLeastSignificantBits()).build();
    }

    public static RuleChainMetadataUpdateMsg constructRuleChainMetadataUpdatedMsg(UpdateMsgType msgType, RuleChainMetaData ruleChainMetaData, EdgeVersion edgeVersion) {
        String metaData = sanitizeMetadataForLegacyEdgeVersion(ruleChainMetaData, edgeVersion);

        return RuleChainMetadataUpdateMsg.newBuilder()
                .setMsgType(msgType)
                .setEntity(metaData)
                .build();
    }

    private static String sanitizeMetadataForLegacyEdgeVersion(RuleChainMetaData ruleChainMetaData, EdgeVersion edgeVersion) {
        JsonNode jsonNode = JacksonUtil.valueToTree(ruleChainMetaData);
        JsonNode nodes = jsonNode.get("nodes");

        updateNodeConfigurationsForLegacyEdge(nodes, edgeVersion);
        removeExcludedNodesForLegacyEdge(nodes, edgeVersion);

        return JacksonUtil.toString(jsonNode);
    }

    private static void updateNodeConfigurationsForLegacyEdge(JsonNode nodes, EdgeVersion edgeVersion) {
        nodes.forEach(node -> {
            if (node.isObject() && node.has("configuration")) {
                String nodeType = node.get("type").asText();
                Map<String, String> ignoredParams = IGNORED_PARAMS_BY_EDGE_VERSION.get(edgeVersion);

                if (ignoredParams != null && ignoredParams.containsKey(nodeType)) {
                    ((ObjectNode) node.get("configuration")).remove(ignoredParams.get(nodeType));
                }
            }
        });
    }

    private static void removeExcludedNodesForLegacyEdge(JsonNode nodes, EdgeVersion edgeVersion) {
        Iterator<JsonNode> iterator = nodes.iterator();

        while (iterator.hasNext()) {
            JsonNode node = iterator.next();
            String type = node.get("type").asText();
            Set<String> missNodes = EXCLUDED_NODES_BY_EDGE_VERSION.get(edgeVersion);

            if (missNodes != null && missNodes.contains(type)) {
                iterator.remove();
            }
        }
    }

    public static EntityDataProto constructEntityDataMsg(TenantId tenantId, EntityId entityId, EdgeEventActionType actionType, JsonElement entityData) {
        EntityDataProto.Builder builder = EntityDataProto.newBuilder()
                .setEntityIdMSB(entityId.getId().getMostSignificantBits())
                .setEntityIdLSB(entityId.getId().getLeastSignificantBits())
                .setEntityType(entityId.getEntityType().name());
        long ts = getTs(entityData.getAsJsonObject());
        switch (actionType) {
            case TIMESERIES_UPDATED:
                try {
                    JsonObject data = entityData.getAsJsonObject();
                    builder.setPostTelemetryMsg(JsonConverter.convertToTelemetryProto(data.getAsJsonObject("data"), ts));
                } catch (Exception e) {
                    log.trace("[{}][{}] Can't convert to telemetry proto, entityData [{}]", tenantId, entityId, entityData, e);
                }
                break;
            case ATTRIBUTES_UPDATED:
                try {
                    JsonObject data = entityData.getAsJsonObject();
                    TransportProtos.PostAttributeMsg attributesUpdatedMsg = JsonConverter.convertToAttributesProto(data.getAsJsonObject("kv"));
                    if (data.has("isPostAttributes") && data.getAsJsonPrimitive("isPostAttributes").getAsBoolean()) {
                        builder.setPostAttributesMsg(attributesUpdatedMsg);
                    } else {
                        builder.setAttributesUpdatedMsg(attributesUpdatedMsg);
                    }
                    builder.setPostAttributeScope(getScopeOfDefault(data));
                    builder.setAttributeTs(ts);
                } catch (Exception e) {
                    log.trace("[{}][{}] Can't convert to AttributesUpdatedMsg proto, entityData [{}]", tenantId, entityId, entityData, e);
                }
                break;
            case POST_ATTRIBUTES:
                try {
                    JsonObject data = entityData.getAsJsonObject();
                    TransportProtos.PostAttributeMsg postAttributesMsg = JsonConverter.convertToAttributesProto(data.getAsJsonObject("kv"));
                    builder.setPostAttributesMsg(postAttributesMsg);
                    builder.setPostAttributeScope(getScopeOfDefault(data));
                    builder.setAttributeTs(ts);
                } catch (Exception e) {
                    log.trace("[{}][{}] Can't convert to PostAttributesMsg, entityData [{}]", tenantId, entityId, entityData, e);
                }
                break;
            case ATTRIBUTES_DELETED:
                try {
                    AttributeDeleteMsg.Builder attributeDeleteMsg = AttributeDeleteMsg.newBuilder();
                    attributeDeleteMsg.setScope(entityData.getAsJsonObject().getAsJsonPrimitive("scope").getAsString());
                    JsonArray jsonArray = entityData.getAsJsonObject().getAsJsonArray("keys");
                    List<String> keys = new Gson().fromJson(jsonArray.toString(), new TypeToken<>() {}.getType());
                    attributeDeleteMsg.addAllAttributeNames(keys);
                    attributeDeleteMsg.build();
                    builder.setAttributeDeleteMsg(attributeDeleteMsg);
                } catch (Exception e) {
                    log.trace("[{}][{}] Can't convert to AttributeDeleteMsg proto, entityData [{}]", tenantId, entityId, entityData, e);
                }
                break;
        }
        return builder.build();
    }

    private static long getTs(JsonObject data) {
        if (data.get("ts") != null && !data.get("ts").isJsonNull()) {
            return data.getAsJsonPrimitive("ts").getAsLong();
        }
        return System.currentTimeMillis();
    }

    private static String getScopeOfDefault(JsonObject data) {
        JsonPrimitive scope = data.getAsJsonPrimitive("scope");
        String result = DataConstants.SERVER_SCOPE;
        if (scope != null && StringUtils.isNotBlank(scope.getAsString())) {
            result = scope.getAsString();
        }
        return result;
    }

    public static TenantUpdateMsg constructTenantUpdateMsg(UpdateMsgType msgType, Tenant tenant) {
        return TenantUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(tenant)).build();
    }

    public static TenantProfileUpdateMsg constructTenantProfileUpdateMsg(UpdateMsgType msgType, TenantProfile tenantProfile) {
        return TenantProfileUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(tenantProfile)).build();
    }

    public static UserUpdateMsg constructUserUpdatedMsg(UpdateMsgType msgType, User user, EntityGroupId entityGroupId) {
        UserUpdateMsg.Builder builder = UserUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(user))
                .setIdMSB(user.getId().getId().getMostSignificantBits())
                .setIdLSB(user.getId().getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static UserUpdateMsg constructUserDeleteMsg(UserId userId, EntityGroupId entityGroupId) {
        UserUpdateMsg.Builder builder = UserUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(userId.getId().getMostSignificantBits())
                .setIdLSB(userId.getId().getLeastSignificantBits());
        if (entityGroupId != null) {
            builder.setEntityGroupIdMSB(entityGroupId.getId().getMostSignificantBits())
                    .setEntityGroupIdLSB(entityGroupId.getId().getLeastSignificantBits());
        }
        return builder.build();
    }

    public static UserCredentialsUpdateMsg constructUserCredentialsUpdatedMsg(UserCredentials userCredentials) {
        return UserCredentialsUpdateMsg.newBuilder().setEntity(JacksonUtil.toString(userCredentials)).build();
    }

    public static WidgetsBundleUpdateMsg constructWidgetsBundleUpdateMsg(UpdateMsgType msgType, WidgetsBundle widgetsBundle, List<String> widgets) {
        return WidgetsBundleUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(widgetsBundle))
                .setWidgets(JacksonUtil.toString(widgets))
                .setIdMSB(widgetsBundle.getId().getId().getMostSignificantBits())
                .setIdLSB(widgetsBundle.getId().getId().getLeastSignificantBits()).build();
    }

    public static WidgetsBundleUpdateMsg constructWidgetsBundleDeleteMsg(WidgetsBundleId widgetsBundleId) {
        return WidgetsBundleUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(widgetsBundleId.getId().getMostSignificantBits())
                .setIdLSB(widgetsBundleId.getId().getLeastSignificantBits())
                .build();
    }

    public static WidgetTypeUpdateMsg constructWidgetTypeUpdateMsg(UpdateMsgType msgType, WidgetTypeDetails widgetTypeDetails) {
        return WidgetTypeUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(widgetTypeDetails))
                .setIdMSB(widgetTypeDetails.getId().getId().getMostSignificantBits())
                .setIdLSB(widgetTypeDetails.getId().getId().getLeastSignificantBits()).build();
    }

    public static WidgetTypeUpdateMsg constructWidgetTypeDeleteMsg(WidgetTypeId widgetTypeId) {
        return WidgetTypeUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(widgetTypeId.getId().getMostSignificantBits())
                .setIdLSB(widgetTypeId.getId().getLeastSignificantBits())
                .build();
    }

    // PE constructors:

    public static ConverterUpdateMsg constructConverterUpdateMsg(UpdateMsgType msgType, Converter converter) {
        return ConverterUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(converter))
                .setIdMSB(converter.getUuidId().getMostSignificantBits())
                .setIdLSB(converter.getUuidId().getLeastSignificantBits()).build();
    }

    public static CustomMenuProto constructCustomMenuMsg(UpdateMsgType msgType, CustomMenu customMenu, List<EntityId> entityIds) {
        CustomMenuProto.Builder builder = CustomMenuProto.newBuilder();
        if (CollectionUtils.isNotEmpty(entityIds)) {
            builder.setAssigneeList(JacksonUtil.toString(entityIds));
        }
        return builder.setMsgType(msgType).setEntity(JacksonUtil.toString(customMenu)).build();
    }

    public static CustomTranslationUpdateMsg constructCustomTranslationMsg(UpdateMsgType msgType, CustomTranslation customTranslation) {
        return CustomTranslationUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(customTranslation)).build();
    }

    public static DeviceGroupOtaPackageUpdateMsg constructDeviceGroupOtaUpdateMsg(UpdateMsgType msgType, DeviceGroupOtaPackage deviceGroupOtaPackage) {
        return DeviceGroupOtaPackageUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(deviceGroupOtaPackage)).build();
    }

    public static EntityGroupUpdateMsg constructEntityGroupUpdatedMsg(UpdateMsgType msgType, EntityGroup entityGroup) {
        return EntityGroupUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(entityGroup)).setName(entityGroup.getName())
                .setIdMSB(entityGroup.getId().getId().getMostSignificantBits())
                .setIdLSB(entityGroup.getId().getId().getLeastSignificantBits()).build();
    }

    public static EntityGroupUpdateMsg constructEntityGroupDeleteMsg(EntityGroupId entityGroupId) {
        return EntityGroupUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(entityGroupId.getId().getMostSignificantBits())
                .setIdLSB(entityGroupId.getId().getLeastSignificantBits()).build();
    }

    public static GroupPermissionProto constructGroupPermissionProto(UpdateMsgType msgType, GroupPermission groupPermission) {
        return GroupPermissionProto.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(groupPermission))
                .setIdMSB(groupPermission.getId().getId().getMostSignificantBits())
                .setIdLSB(groupPermission.getId().getId().getLeastSignificantBits()).build();
    }

    public static GroupPermissionProto constructGroupPermissionDeleteMsg(GroupPermissionId groupPermissionId) {
        return GroupPermissionProto.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(groupPermissionId.getId().getMostSignificantBits())
                .setIdLSB(groupPermissionId.getId().getLeastSignificantBits()).build();
    }

    public static IntegrationUpdateMsg constructIntegrationUpdateMsg(UpdateMsgType msgType, Integration integration, JsonNode configuration) {
        JsonNode defaultConfig = integration.getConfiguration();
        integration.setConfiguration(configuration);
        IntegrationUpdateMsg integrationUpdateMsg = IntegrationUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(integration))
                .setIdMSB(integration.getId().getId().getMostSignificantBits())
                .setIdLSB(integration.getId().getId().getLeastSignificantBits()).build();
        integration.setConfiguration(defaultConfig);
        return integrationUpdateMsg;
    }

    public static IntegrationUpdateMsg constructIntegrationDeleteMsg(IntegrationId integrationId) {
        return IntegrationUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(integrationId.getId().getMostSignificantBits())
                .setIdLSB(integrationId.getId().getLeastSignificantBits()).build();
    }

    public static RoleProto constructRoleProto(UpdateMsgType msgType, Role role) {
        return RoleProto.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(role))
                .setIdMSB(role.getId().getId().getMostSignificantBits())
                .setIdLSB(role.getId().getId().getLeastSignificantBits()).build();
    }

    public static RoleProto constructRoleDeleteMsg(RoleId roleId) {
        return RoleProto.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(roleId.getId().getMostSignificantBits())
                .setIdLSB(roleId.getId().getLeastSignificantBits()).build();
    }

    public static SchedulerEventUpdateMsg constructSchedulerEventUpdatedMsg(UpdateMsgType msgType, SchedulerEvent schedulerEvent) {
        return SchedulerEventUpdateMsg.newBuilder().setMsgType(msgType).setEntity(JacksonUtil.toString(schedulerEvent))
                .setIdMSB(schedulerEvent.getId().getId().getMostSignificantBits())
                .setIdLSB(schedulerEvent.getId().getId().getLeastSignificantBits()).build();
    }

    public static SchedulerEventUpdateMsg constructEventDeleteMsg(SchedulerEventId schedulerEventId) {
        return SchedulerEventUpdateMsg.newBuilder()
                .setMsgType(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE)
                .setIdMSB(schedulerEventId.getId().getMostSignificantBits())
                .setIdLSB(schedulerEventId.getId().getLeastSignificantBits()).build();
    }

    public static WhiteLabelingProto constructWhiteLabeling(WhiteLabeling whiteLabeling) {
        return WhiteLabelingProto.newBuilder().setEntity(JacksonUtil.toString(whiteLabeling)).build();
    }

}
