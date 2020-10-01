/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.rule.engine.edge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.EmptyNodeConfiguration;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.CloudUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.cloud.CloudEventType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.session.SessionMsgType;

import javax.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.thingsboard.rule.engine.api.TbRelationTypes.SUCCESS;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "push to cloud",
        configClazz = EmptyNodeConfiguration.class,
        nodeDescription = "Pushes messages to cloud",
        nodeDetails = "Pushes messages to cloud. This node is used only on Edge instances to push messages from Edge to Cloud.",
        uiResources = {"static/rulenode/rulenode-core-config.js", "static/rulenode/rulenode-core-config.css"},
        configDirective = "tbNodeEmptyConfig",
        icon = "cloud_upload"
)
public class TbMsgPushToCloudNode implements TbNode {

    private EmptyNodeConfiguration config;

    private static final ObjectMapper json = new ObjectMapper();

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, EmptyNodeConfiguration.class);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (DataConstants.CLOUD_MSG_SOURCE.equalsIgnoreCase(msg.getMetaData().getValue(DataConstants.MSG_SOURCE_KEY))) {
            log.debug("Ignoring msg from the cloud, msg [{}]", msg);
            ctx.ack(msg);
            return;
        }
        if (isSupportedOriginator(msg.getOriginator().getEntityType())) {
            if (isSupportedMsgType(msg.getType())) {
                try {
                    CloudEvent cloudEvent = buildCloudEvent(msg, ctx);
                    if (cloudEvent == null) {
                        log.debug("Cloud event type is null. Entity Type {}", msg.getOriginator().getEntityType());
                        ctx.tellFailure(msg, new RuntimeException("Cloud event type is null. Entity Type '" + msg.getOriginator().getEntityType() + "'"));
                    } else {
                        ListenableFuture<CloudEvent> saveFuture = ctx.getCloudEventService().saveAsync(cloudEvent);
                        Futures.addCallback(saveFuture, new FutureCallback<CloudEvent>() {
                            @Override
                            public void onSuccess(@Nullable CloudEvent event) {
                                ctx.tellNext(msg, SUCCESS);
                            }

                            @Override
                            public void onFailure(Throwable th) {
                                log.error("Could not save cloud event", th);
                                ctx.tellFailure(msg, th);
                            }
                        }, ctx.getDbCallbackExecutor());
                    }
                } catch (JsonProcessingException e) {
                    log.error("Failed to build cloud event", e);
                    ctx.tellFailure(msg, e);
                }
            } else {
                log.debug("Unsupported msg type {}", msg.getType());
                ctx.tellFailure(msg, new RuntimeException("Unsupported msg type '" + msg.getType() + "'"));
            }
        } else {
            log.debug("Unsupported originator type {}", msg.getOriginator().getEntityType());
            ctx.tellFailure(msg, new RuntimeException("Unsupported originator type '" + msg.getOriginator().getEntityType() + "'"));
        }
    }

    private UUID getUUIDFromMsgData(TbMsg msg) throws JsonProcessingException {
        JsonNode data = json.readTree(msg.getData()).get("id");
        String id = json.treeToValue(data.get("id"), String.class);
        return UUID.fromString(id);
    }

    private CloudEvent buildCloudEvent(TbMsg msg, TbContext ctx) throws JsonProcessingException {
        String msgType = msg.getType();
        if (DataConstants.ALARM.equals(msgType)) {
            return buildCloudEvent(ctx.getTenantId(), ActionType.ADDED, getUUIDFromMsgData(msg), CloudEventType.ALARM, null);
        } else {
            CloudEventType cloudEventTypeByEntityType = CloudUtils.getCloudEventTypeByEntityType(msg.getOriginator().getEntityType());
            if (cloudEventTypeByEntityType == null) {
                return null;
            }
            ActionType actionType = getActionTypeByMsgType(msgType);
            Map<String, Object> entityBody = new HashMap<>();
            Map<String, String> metadata = msg.getMetaData().getData();
            JsonNode dataJson = json.readTree(msg.getData());
            switch (actionType) {
                case ATTRIBUTES_UPDATED:
                    entityBody.put("kv", dataJson);
                    entityBody.put("scope", metadata.get("scope"));
                    if (SessionMsgType.POST_ATTRIBUTES_REQUEST.name().equals(msgType)) {
                        entityBody.put("isPostAttributes", true);
                    }
                    break;
                case ATTRIBUTES_DELETED:
                    List<String> keys = json.treeToValue(dataJson.get("attributes"), List.class);
                    entityBody.put("keys", keys);
                    entityBody.put("scope", metadata.get("scope"));
                    break;
                case TIMESERIES_UPDATED:
                    entityBody.put("data", dataJson);
                    entityBody.put("ts", metadata.get("ts"));
                    break;
            }
            return buildCloudEvent(ctx.getTenantId(), actionType, msg.getOriginator().getId(), cloudEventTypeByEntityType, json.valueToTree(entityBody));
        }
    }

    private CloudEvent buildCloudEvent(TenantId tenantId, ActionType cloudEventAction, UUID entityId, CloudEventType cloudEventType, JsonNode entityBody) {
        CloudEvent cloudEvent = new CloudEvent();
        cloudEvent.setTenantId(tenantId);
        cloudEvent.setCloudEventAction(cloudEventAction.name());
        cloudEvent.setEntityId(entityId);
        cloudEvent.setCloudEventType(cloudEventType);
        cloudEvent.setEntityBody(entityBody);
        return cloudEvent;
    }

    private ActionType getActionTypeByMsgType(String msgType) {
        ActionType actionType;
        if (SessionMsgType.POST_TELEMETRY_REQUEST.name().equals(msgType)) {
            actionType = ActionType.TIMESERIES_UPDATED;
        } else if (SessionMsgType.POST_ATTRIBUTES_REQUEST.name().equals(msgType)
                || DataConstants.ATTRIBUTES_UPDATED.equals(msgType)) {
            actionType = ActionType.ATTRIBUTES_UPDATED;
        } else {
            actionType = ActionType.ATTRIBUTES_DELETED;
        }
        return actionType;
    }

    private boolean isSupportedMsgType(String msgType) {
        return SessionMsgType.POST_TELEMETRY_REQUEST.name().equals(msgType)
                || SessionMsgType.POST_ATTRIBUTES_REQUEST.name().equals(msgType)
                || DataConstants.ATTRIBUTES_UPDATED.equals(msgType)
                || DataConstants.ATTRIBUTES_DELETED.equals(msgType)
                || DataConstants.ALARM.equals(msgType);
    }

    private boolean isSupportedOriginator(EntityType entityType) {
        switch (entityType) {
            case DEVICE:
            case ASSET:
            case ENTITY_VIEW:
            case DASHBOARD:
            case ENTITY_GROUP:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void destroy() {
    }

}
