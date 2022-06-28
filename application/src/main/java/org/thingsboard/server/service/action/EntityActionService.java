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
package org.thingsboard.server.service.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.edge.EdgeEventActionType;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.DataType;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.audit.AuditLogService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntityActionService {
    private final TbClusterService tbClusterService;
    private final AuditLogService auditLogService;

    private static final ObjectMapper json = new ObjectMapper();

    public <E extends HasName, I extends EntityId> void pushEntityActionToRuleEngine(I entityId, E entity, TenantId tenantId, CustomerId customerId,
                                                                                     ActionType actionType, Object... additionalInfo) {
        pushEntityActionToRuleEngine(entityId, entity, null, tenantId, customerId, actionType, additionalInfo);
    }

    public <E extends HasName, I extends EntityId> void pushEntityActionToRuleEngine(I entityId, E entity, User user, CustomerId customerId,
                                                                                     ActionType actionType, Object... additionalInfo) {
        pushEntityActionToRuleEngine(entityId, entity, user, user.getTenantId(), customerId, actionType, additionalInfo);
    }

    public <E extends HasName, I extends EntityId> void pushEntityActionToRuleEngine(I entityId, E entity, User user, TenantId tenantId, CustomerId customerId,
                                                                                      ActionType actionType, Object... additionalInfo) {
        String msgType = null;
        switch (actionType) {
            case ADDED:
                msgType = DataConstants.ENTITY_CREATED;
                break;
            case DELETED:
                msgType = DataConstants.ENTITY_DELETED;
                break;
            case UPDATED:
                msgType = DataConstants.ENTITY_UPDATED;
                break;
            case ASSIGNED_TO_CUSTOMER:
                msgType = DataConstants.ENTITY_ASSIGNED;
                break;
            case CHANGE_OWNER:
                msgType = DataConstants.OWNER_CHANGED;
                break;
            case UNASSIGNED_FROM_CUSTOMER:
                msgType = DataConstants.ENTITY_UNASSIGNED;
                break;
            case ATTRIBUTES_UPDATED:
                msgType = DataConstants.ATTRIBUTES_UPDATED;
                break;
            case ATTRIBUTES_DELETED:
                msgType = DataConstants.ATTRIBUTES_DELETED;
                break;
            case ADDED_TO_ENTITY_GROUP:
                msgType = DataConstants.ADDED_TO_ENTITY_GROUP;
                break;
            case REMOVED_FROM_ENTITY_GROUP:
                msgType = DataConstants.REMOVED_FROM_ENTITY_GROUP;
                break;
            case ALARM_ACK:
                msgType = DataConstants.ALARM_ACK;
                break;
            case ALARM_CLEAR:
                msgType = DataConstants.ALARM_CLEAR;
                break;
            case ASSIGNED_FROM_TENANT:
                msgType = DataConstants.ENTITY_ASSIGNED_FROM_TENANT;
                break;
            case ASSIGNED_TO_TENANT:
                msgType = DataConstants.ENTITY_ASSIGNED_TO_TENANT;
                break;
            case PROVISION_SUCCESS:
                msgType = DataConstants.PROVISION_SUCCESS;
                break;
            case PROVISION_FAILURE:
                msgType = DataConstants.PROVISION_FAILURE;
                break;
            case TIMESERIES_UPDATED:
                msgType = DataConstants.TIMESERIES_UPDATED;
                break;
            case TIMESERIES_DELETED:
                msgType = DataConstants.TIMESERIES_DELETED;
                break;
            case ASSIGNED_TO_EDGE:
                msgType = DataConstants.ENTITY_ASSIGNED_TO_EDGE;
                break;
            case UNASSIGNED_FROM_EDGE:
                msgType = DataConstants.ENTITY_UNASSIGNED_FROM_EDGE;
                break;
        }
        if (!StringUtils.isEmpty(msgType)) {
            try {
                TbMsgMetaData metaData = new TbMsgMetaData();
                if(user != null) {
                    metaData.putValue("userId", user.getId().toString());
                    metaData.putValue("userName", user.getName());
                }
                if (customerId != null && !customerId.isNullUid()) {
                    metaData.putValue("customerId", customerId.toString());
                }
                if (actionType == ActionType.ASSIGNED_TO_CUSTOMER) {
                    String strCustomerId = extractParameter(String.class, 1, additionalInfo);
                    String strCustomerName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("assignedCustomerId", strCustomerId);
                    metaData.putValue("assignedCustomerName", strCustomerName);
                } else if (actionType == ActionType.UNASSIGNED_FROM_CUSTOMER) {
                    String strCustomerId = extractParameter(String.class, 1, additionalInfo);
                    String strCustomerName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("unassignedCustomerId", strCustomerId);
                    metaData.putValue("unassignedCustomerName", strCustomerName);
                } else if (actionType == ActionType.ADDED_TO_ENTITY_GROUP) {
                    String strEntityGroupId = extractParameter(String.class, 1, additionalInfo);
                    String strEntityGroupName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("addedToEntityGroupId", strEntityGroupId);
                    metaData.putValue("addedToEntityGroupName", strEntityGroupName);
                } else if (actionType == ActionType.REMOVED_FROM_ENTITY_GROUP) {
                    String strEntityGroupId = extractParameter(String.class, 1, additionalInfo);
                    String strEntityGroupName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("removedFromEntityGroupId", strEntityGroupId);
                    metaData.putValue("removedFromEntityGroupName", strEntityGroupName);
                } else if (actionType == ActionType.ASSIGNED_FROM_TENANT) {
                    String strTenantId = extractParameter(String.class, 0, additionalInfo);
                    String strTenantName = extractParameter(String.class, 1, additionalInfo);
                    metaData.putValue("assignedFromTenantId", strTenantId);
                    metaData.putValue("assignedFromTenantName", strTenantName);
                } else if (actionType == ActionType.ASSIGNED_TO_TENANT) {
                    String strTenantId = extractParameter(String.class, 0, additionalInfo);
                    String strTenantName = extractParameter(String.class, 1, additionalInfo);
                    metaData.putValue("assignedToTenantId", strTenantId);
                    metaData.putValue("assignedToTenantName", strTenantName);
                } else if (actionType == ActionType.CHANGE_OWNER) {
                    EntityId targetOwnerId = extractParameter(EntityId.class, 0, additionalInfo);
                    metaData.putValue("targetOwnerId", targetOwnerId.toString());
                    metaData.putValue("targetOwnerType", targetOwnerId.getEntityType().name());
                } else if (actionType == ActionType.ASSIGNED_TO_EDGE) {
                    String strEdgeId = extractParameter(String.class, 1, additionalInfo);
                    String strEdgeName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("assignedEdgeId", strEdgeId);
                    metaData.putValue("assignedEdgeName", strEdgeName);
                } else if (actionType == ActionType.UNASSIGNED_FROM_EDGE) {
                    String strEdgeId = extractParameter(String.class, 1, additionalInfo);
                    String strEdgeName = extractParameter(String.class, 2, additionalInfo);
                    metaData.putValue("unassignedEdgeId", strEdgeId);
                    metaData.putValue("unassignedEdgeName", strEdgeName);
                }
                ObjectNode entityNode;
                if (entity != null) {
                    entityNode = json.valueToTree(entity);
                    if (entityId.getEntityType() == EntityType.DASHBOARD) {
                        entityNode.put("configuration", "");
                    }
                } else {
                    entityNode = json.createObjectNode();
                    if (actionType == ActionType.ATTRIBUTES_UPDATED) {
                        String scope = extractParameter(String.class, 0, additionalInfo);
                        @SuppressWarnings("unchecked")
                        List<AttributeKvEntry> attributes = extractParameter(List.class, 1, additionalInfo);
                        metaData.putValue(DataConstants.SCOPE, scope);
                        if (attributes != null) {
                            for (AttributeKvEntry attr : attributes) {
                                addKvEntry(entityNode, attr);
                            }
                        }
                    } else if (actionType == ActionType.ATTRIBUTES_DELETED) {
                        String scope = extractParameter(String.class, 0, additionalInfo);
                        @SuppressWarnings("unchecked")
                        List<String> keys = extractParameter(List.class, 1, additionalInfo);
                        metaData.putValue(DataConstants.SCOPE, scope);
                        ArrayNode attrsArrayNode = entityNode.putArray("attributes");
                        if (keys != null) {
                            keys.forEach(attrsArrayNode::add);
                        }
                    } else if (actionType == ActionType.TIMESERIES_UPDATED) {
                        @SuppressWarnings("unchecked")
                        List<TsKvEntry> timeseries = extractParameter(List.class, 0, additionalInfo);
                        addTimeseries(entityNode, timeseries);
                    } else if (actionType == ActionType.TIMESERIES_DELETED) {
                        @SuppressWarnings("unchecked")
                        List<String> keys = extractParameter(List.class, 0, additionalInfo);
                        if (keys != null) {
                            ArrayNode timeseriesArrayNode = entityNode.putArray("timeseries");
                            keys.forEach(timeseriesArrayNode::add);
                        }
                        entityNode.put("startTs", extractParameter(Long.class, 1, additionalInfo));
                        entityNode.put("endTs", extractParameter(Long.class, 2, additionalInfo));
                    }
                }
                TbMsg tbMsg = TbMsg.newMsg(msgType, entityId, customerId, metaData, TbMsgDataType.JSON, json.writeValueAsString(entityNode));
                if (tenantId.isNullUid()) {
                    if (entity instanceof HasTenantId) {
                        tenantId = ((HasTenantId) entity).getTenantId();
                    }
                }
                tbClusterService.pushMsgToRuleEngine(tenantId, entityId, tbMsg, null);
            } catch (Exception e) {
                log.warn("[{}] Failed to push entity action to rule engine: {}", entityId, actionType, e);
            }
        }
    }

    public void sendEntityNotificationMsgToEdge(TenantId tenantId, EntityId entityId, EdgeEventActionType action) {
        tbClusterService.sendNotificationMsgToEdge(tenantId, null, entityId, null, null, action, null, null);
    }

    private void addKvEntry(ObjectNode entityNode, KvEntry kvEntry) throws Exception {
        if (kvEntry.getDataType() == DataType.BOOLEAN) {
            kvEntry.getBooleanValue().ifPresent(value -> entityNode.put(kvEntry.getKey(), value));
        } else if (kvEntry.getDataType() == DataType.DOUBLE) {
            kvEntry.getDoubleValue().ifPresent(value -> entityNode.put(kvEntry.getKey(), value));
        } else if (kvEntry.getDataType() == DataType.LONG) {
            kvEntry.getLongValue().ifPresent(value -> entityNode.put(kvEntry.getKey(), value));
        } else if (kvEntry.getDataType() == DataType.JSON) {
            if (kvEntry.getJsonValue().isPresent()) {
                entityNode.set(kvEntry.getKey(), json.readTree(kvEntry.getJsonValue().get()));
            }
        } else {
            entityNode.put(kvEntry.getKey(), kvEntry.getValueAsString());
        }
    }

    public <E extends HasName, I extends EntityId> void logEntityAction(User user, I entityId, E entity, CustomerId customerId,
                                                                           ActionType actionType, Exception e, Object... additionalInfo) {
        if (customerId == null || customerId.isNullUid()) {
            customerId = user.getCustomerId();
        }
        if (e == null) {
            pushEntityActionToRuleEngine(entityId, entity, user, customerId, actionType, additionalInfo);
        }
        auditLogService.logEntityAction(user.getTenantId(), customerId, user.getId(), user.getName(), entityId, entity, actionType, e, additionalInfo);
    }

    private <T> T extractParameter(Class<T> clazz, int index, Object... additionalInfo) {
        T result = null;
        if (additionalInfo != null && additionalInfo.length > index) {
            Object paramObject = additionalInfo[index];
            if (clazz.isInstance(paramObject)) {
                result = clazz.cast(paramObject);
            }
        }
        return result;
    }

    private void addTimeseries(ObjectNode entityNode, List<TsKvEntry> timeseries) throws Exception {
        if (timeseries != null && !timeseries.isEmpty()) {
            ArrayNode result = entityNode.putArray("timeseries");
            Map<Long, List<TsKvEntry>> groupedTelemetry = timeseries.stream()
                    .collect(Collectors.groupingBy(TsKvEntry::getTs));
            for (Map.Entry<Long, List<TsKvEntry>> entry : groupedTelemetry.entrySet()) {
                ObjectNode element = json.createObjectNode();
                element.put("ts", entry.getKey());
                ObjectNode values = element.putObject("values");
                for (TsKvEntry tsKvEntry : entry.getValue()) {
                    addKvEntry(values, tsKvEntry);
                }
                result.add(element);
            }
        }
    }

}
