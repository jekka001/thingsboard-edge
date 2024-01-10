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
package org.thingsboard.rule.engine.edge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.thingsboard.server.common.data.msg.TbMsgType.ACTIVITY_EVENT;
import static org.thingsboard.server.common.data.msg.TbMsgType.ALARM;
import static org.thingsboard.server.common.data.msg.TbMsgType.ATTRIBUTES_DELETED;
import static org.thingsboard.server.common.data.msg.TbMsgType.ATTRIBUTES_UPDATED;
import static org.thingsboard.server.common.data.msg.TbMsgType.CONNECT_EVENT;
import static org.thingsboard.server.common.data.msg.TbMsgType.DISCONNECT_EVENT;
import static org.thingsboard.server.common.data.msg.TbMsgType.INACTIVITY_EVENT;
import static org.thingsboard.server.common.data.msg.TbMsgType.POST_ATTRIBUTES_REQUEST;
import static org.thingsboard.server.common.data.msg.TbMsgType.POST_TELEMETRY_REQUEST;
import static org.thingsboard.server.common.data.msg.TbMsgType.TIMESERIES_UPDATED;
import static org.thingsboard.server.common.data.msg.TbMsgType.TO_SERVER_RPC_REQUEST;

@Slf4j
public abstract class   AbstractTbMsgPushNode<T extends BaseTbMsgPushNodeConfiguration, S, U> implements TbNode {

    protected T config;

    private static final String SCOPE = "scope";

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, getConfigClazz());
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (getIgnoredMessageSource().equalsIgnoreCase(msg.getMetaData().getValue(DataConstants.MSG_SOURCE_KEY))) {
            log.debug("Ignoring msg from the {}, msg [{}]", getIgnoredMessageSource(), msg);
            ctx.ack(msg);
            return;
        }
        if (isSupportedOriginator(msg.getOriginator().getEntityType())) {
            if (isSupportedMsgType(msg)) {
                processMsg(ctx, msg);
            } else {
                String errMsg = String.format("Unsupported msg type %s", msg.getType());
                log.debug(errMsg);
                ctx.tellFailure(msg, new RuntimeException(errMsg));
            }
        } else {
            String errMsg = String.format("Unsupported originator type %s", msg.getOriginator().getEntityType());
            log.debug(errMsg);
            ctx.tellFailure(msg, new RuntimeException(errMsg));
        }
    }

    protected S buildEvent(TbMsg msg, TbContext ctx) throws JsonProcessingException {
        if (msg.isTypeOf(ALARM)) {
            EdgeEventActionType actionType = getAlarmActionType(msg);
            return buildEvent(ctx.getTenantId(), actionType, getUUIDFromMsgData(msg), getAlarmEventType(), null);
        } else {
            Map<String, String> metadata = msg.getMetaData().getData();
            EdgeEventActionType actionType = getEdgeEventActionTypeByMsgType(msg);
            Map<String, Object> entityBody = new HashMap<>();
            JsonNode dataJson = JacksonUtil.toJsonNode(msg.getData());
            switch (actionType) {
                case ATTRIBUTES_UPDATED:
                case POST_ATTRIBUTES:
                    entityBody.put("kv", dataJson);
                    entityBody.put(SCOPE, getScope(metadata));
                    if (EdgeEventActionType.POST_ATTRIBUTES.equals(actionType)) {
                        entityBody.put("isPostAttributes", true);
                    }
                    break;
                case ATTRIBUTES_DELETED:
                    List<String> keys = JacksonUtil.convertValue(dataJson.get("attributes"), new TypeReference<>() {});
                    entityBody.put("keys", keys);
                    entityBody.put(SCOPE, getScope(metadata));
                    break;
                case TIMESERIES_UPDATED:
                    entityBody.put("data", dataJson);
                    entityBody.put("ts", msg.getMetaDataTs());
                    break;
                case RPC_CALL:
                    addRpcRequestsDetailsIntoEventBody(entityBody, msg, metadata);
                    break;
            }
            return buildEvent(ctx.getTenantId(),
                    actionType,
                    msg.getOriginator().getId(),
                    getEventTypeByEntityType(msg.getOriginator().getEntityType()),
                    JacksonUtil.valueToTree(entityBody));
        }
    }

    private static EdgeEventActionType getAlarmActionType(TbMsg msg) {
        boolean isNewAlarm = Boolean.parseBoolean(msg.getMetaData().getValue(DataConstants.IS_NEW_ALARM));
        boolean isClearedAlarm = Boolean.parseBoolean(msg.getMetaData().getValue(DataConstants.IS_CLEARED_ALARM));
        EdgeEventActionType eventAction;
        if (isNewAlarm) {
            eventAction = EdgeEventActionType.ADDED;
        } else if (isClearedAlarm) {
            eventAction = EdgeEventActionType.ALARM_CLEAR;
        } else {
            eventAction = EdgeEventActionType.UPDATED;
        }
        return eventAction;
    }

    private void addRpcRequestsDetailsIntoEventBody(Map<String, Object> entityBody, TbMsg msg, Map<String, String> metadata) throws JsonProcessingException {
        entityBody.put("requestId", metadata.get("requestId"));
        entityBody.put("serviceId", metadata.get("serviceId"));
        entityBody.put("sessionId", metadata.get("sessionId"));
        JsonNode data = JacksonUtil.OBJECT_MAPPER.readTree(msg.getData());
        entityBody.put("method", data.get("method").asText());
        entityBody.put("params", JacksonUtil.OBJECT_MAPPER.writeValueAsString(data.get("params")));
    }

    abstract S buildEvent(TenantId tenantId, EdgeEventActionType eventAction, UUID entityId, U eventType, JsonNode entityBody);

    abstract U getEventTypeByEntityType(EntityType entityType);

    abstract U getAlarmEventType();

    abstract String getIgnoredMessageSource();

    abstract protected Class<T> getConfigClazz();

    abstract void processMsg(TbContext ctx, TbMsg msg);

    protected UUID getUUIDFromMsgData(TbMsg msg) {
        JsonNode data = JacksonUtil.toJsonNode(msg.getData()).get("id");
        String id = JacksonUtil.convertValue(data.get("id"), String.class);
        return UUID.fromString(id);
    }

    protected String getScope(Map<String, String> metadata) {
        String scope = metadata.get(SCOPE);
        if (StringUtils.isEmpty(scope)) {
            scope = config.getScope();
        }
        return scope;
    }

    protected EdgeEventActionType getEdgeEventActionTypeByMsgType(TbMsg msg) {
        EdgeEventActionType actionType;
        if (msg.isTypeOneOf(POST_TELEMETRY_REQUEST, TIMESERIES_UPDATED)) {
            actionType = EdgeEventActionType.TIMESERIES_UPDATED;
        } else if (msg.isTypeOf(ATTRIBUTES_UPDATED)) {
            actionType = EdgeEventActionType.ATTRIBUTES_UPDATED;
        } else if (msg.isTypeOf(POST_ATTRIBUTES_REQUEST)) {
            actionType = EdgeEventActionType.POST_ATTRIBUTES;
        } else if (msg.isTypeOf(ATTRIBUTES_DELETED)) {
            actionType = EdgeEventActionType.ATTRIBUTES_DELETED;
        } else if (msg.isTypeOf(TO_SERVER_RPC_REQUEST)) {
            actionType = EdgeEventActionType.RPC_CALL;
        } else if (msg.isTypeOneOf(CONNECT_EVENT, DISCONNECT_EVENT, ACTIVITY_EVENT, INACTIVITY_EVENT)) {
            String scope = msg.getMetaData().getValue(SCOPE);
            actionType = StringUtils.isEmpty(scope) ?
                    EdgeEventActionType.TIMESERIES_UPDATED : EdgeEventActionType.ATTRIBUTES_UPDATED;
        } else {
            String type = msg.getType();
            log.warn("Unsupported msg type [{}]", type);
            throw new IllegalArgumentException("Unsupported msg type: " + type);
        }
        return actionType;
    }

    protected boolean isSupportedMsgType(TbMsg msg) {
        return msg.isTypeOneOf(POST_TELEMETRY_REQUEST, POST_ATTRIBUTES_REQUEST, ATTRIBUTES_UPDATED,
                ATTRIBUTES_DELETED, TIMESERIES_UPDATED, ALARM, CONNECT_EVENT, DISCONNECT_EVENT, ACTIVITY_EVENT, INACTIVITY_EVENT, TO_SERVER_RPC_REQUEST);
    }

    protected boolean isSupportedOriginator(EntityType entityType) {
        switch (entityType) {
            case DEVICE:
            case ASSET:
            case ENTITY_VIEW:
            case DASHBOARD:
            case TENANT:
            case CUSTOMER:
            case USER:
            case EDGE:
            case ENTITY_GROUP:
                return true;
            default:
                return false;
        }
    }
}
