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
package org.thingsboard.server.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.CollectionUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmSeverity;
import org.thingsboard.server.common.data.id.AlarmId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.dao.model.BaseEntity;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.util.mapping.JsonConverter;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.ALARM_ACKNOWLEDGED_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_ACK_TS_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_ASSIGNEE_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_ASSIGN_TS_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_CLEARED_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_CLEAR_TS_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_CUSTOMER_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_END_TS_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_ORIGINATOR_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_ORIGINATOR_TYPE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_PROPAGATE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_PROPAGATE_RELATION_TYPES;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_PROPAGATE_TO_OWNER_HIERARCHY_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_PROPAGATE_TO_OWNER_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_PROPAGATE_TO_TENANT_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_SEVERITY_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_START_TS_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_TENANT_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ALARM_TYPE_PROPERTY;

@Data
@EqualsAndHashCode(callSuper = true)
@MappedSuperclass
public abstract class AbstractAlarmEntity<T extends Alarm> extends BaseSqlEntity<T> implements BaseEntity<T> {

    @Column(name = ALARM_TENANT_ID_PROPERTY, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = ALARM_CUSTOMER_ID_PROPERTY)
    private UUID customerId;

    @Column(name = ALARM_ORIGINATOR_ID_PROPERTY, columnDefinition = "uuid")
    private UUID originatorId;

    @Column(name = ALARM_ORIGINATOR_TYPE_PROPERTY)
    private EntityType originatorType;

    @Column(name = ALARM_TYPE_PROPERTY)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = ALARM_SEVERITY_PROPERTY)
    private AlarmSeverity severity;

    @Column(name = ALARM_ASSIGNEE_ID_PROPERTY)
    private UUID assigneeId;

    @Column(name = ALARM_START_TS_PROPERTY)
    private Long startTs;

    @Column(name = ALARM_END_TS_PROPERTY)
    private Long endTs;

    @Column(name = ALARM_ACKNOWLEDGED_PROPERTY)
    private boolean acknowledged;

    @Column(name = ALARM_ACK_TS_PROPERTY)
    private Long ackTs;

    @Column(name = ALARM_CLEARED_PROPERTY)
    private boolean cleared;

    @Column(name = ALARM_CLEAR_TS_PROPERTY)
    private Long clearTs;

    @Column(name = ALARM_ASSIGN_TS_PROPERTY)
    private Long assignTs;

    @Convert(converter = JsonConverter.class)
    @Column(name = ModelConstants.ALARM_DETAILS_PROPERTY)
    private JsonNode details;

    @Column(name = ALARM_PROPAGATE_PROPERTY)
    private Boolean propagate;

    @Column(name = ALARM_PROPAGATE_TO_OWNER_PROPERTY)
    private Boolean propagateToOwner;

    @Column(name = ALARM_PROPAGATE_TO_OWNER_HIERARCHY_PROPERTY)
    private Boolean propagateToOwnerHierarchy;

    @Column(name = ALARM_PROPAGATE_TO_TENANT_PROPERTY)
    private Boolean propagateToTenant;

    @Column(name = ALARM_PROPAGATE_RELATION_TYPES)
    private String propagateRelationTypes;

    public AbstractAlarmEntity() {
        super();
    }

    public AbstractAlarmEntity(Alarm alarm) {
        if (alarm.getId() != null) {
            this.setUuid(alarm.getUuidId());
        }
        this.setCreatedTime(alarm.getCreatedTime());
        if (alarm.getTenantId() != null) {
            this.tenantId = alarm.getTenantId().getId();
        }
        if (alarm.getCustomerId() != null) {
            this.customerId = alarm.getCustomerId().getId();
        }
        this.type = alarm.getType();
        this.originatorId = alarm.getOriginator().getId();
        this.originatorType = alarm.getOriginator().getEntityType();
        this.type = alarm.getType();
        this.severity = alarm.getSeverity();
        this.acknowledged = alarm.isAcknowledged();
        this.cleared = alarm.isCleared();
        if (alarm.getAssigneeId() != null) {
            this.assigneeId = alarm.getAssigneeId().getId();
        }
        this.propagate = alarm.isPropagate();
        this.propagateToOwner = alarm.isPropagateToOwner();
        this.propagateToOwnerHierarchy = alarm.isPropagateToOwnerHierarchy();
        this.propagateToTenant = alarm.isPropagateToTenant();
        this.startTs = alarm.getStartTs();
        this.endTs = alarm.getEndTs();
        this.ackTs = alarm.getAckTs();
        this.clearTs = alarm.getClearTs();
        this.assignTs = alarm.getAssignTs();
        this.details = alarm.getDetails();
        if (!CollectionUtils.isEmpty(alarm.getPropagateRelationTypes())) {
            this.propagateRelationTypes = String.join(",", alarm.getPropagateRelationTypes());
        } else {
            this.propagateRelationTypes = "";
        }
    }

    public AbstractAlarmEntity(AlarmEntity alarmEntity) {
        this.setId(alarmEntity.getId());
        this.setCreatedTime(alarmEntity.getCreatedTime());
        this.tenantId = alarmEntity.getTenantId();
        this.customerId = alarmEntity.getCustomerId();
        this.type = alarmEntity.getType();
        this.originatorId = alarmEntity.getOriginatorId();
        this.originatorType = alarmEntity.getOriginatorType();
        this.type = alarmEntity.getType();
        this.severity = alarmEntity.getSeverity();
        this.acknowledged = alarmEntity.isAcknowledged();
        this.cleared = alarmEntity.isCleared();
        this.assigneeId = alarmEntity.getAssigneeId();
        this.propagate = alarmEntity.getPropagate();
        this.propagateToOwner = alarmEntity.getPropagateToOwner();
        this.propagateToOwnerHierarchy = alarmEntity.getPropagateToOwnerHierarchy();
        this.propagateToTenant = alarmEntity.getPropagateToTenant();
        this.startTs = alarmEntity.getStartTs();
        this.endTs = alarmEntity.getEndTs();
        this.ackTs = alarmEntity.getAckTs();
        this.clearTs = alarmEntity.getClearTs();
        this.assignTs = alarmEntity.getAssignTs();
        this.details = alarmEntity.getDetails();
        this.propagateRelationTypes = alarmEntity.getPropagateRelationTypes();
    }

    protected Alarm toAlarm() {
        Alarm alarm = new Alarm(new AlarmId(id));
        alarm.setCreatedTime(createdTime);
        if (tenantId != null) {
            alarm.setTenantId(TenantId.fromUUID(tenantId));
        }
        if (customerId != null) {
            alarm.setCustomerId(new CustomerId(customerId));
        }
        alarm.setOriginator(EntityIdFactory.getByTypeAndUuid(originatorType, originatorId));
        alarm.setType(type);
        alarm.setSeverity(severity);
        alarm.setAcknowledged(acknowledged);
        alarm.setCleared(cleared);
        if (assigneeId != null) {
            alarm.setAssigneeId(new UserId(assigneeId));
        }
        alarm.setPropagate(propagate);
        alarm.setPropagateToOwner(propagateToOwner);
        alarm.setPropagateToOwnerHierarchy(propagateToOwnerHierarchy);
        alarm.setPropagateToTenant(propagateToTenant);
        alarm.setStartTs(startTs);
        alarm.setEndTs(endTs);
        alarm.setAckTs(ackTs);
        alarm.setClearTs(clearTs);
        alarm.setAssignTs(assignTs);
        alarm.setDetails(details);
        if (!StringUtils.isEmpty(propagateRelationTypes)) {
            alarm.setPropagateRelationTypes(Arrays.asList(propagateRelationTypes.split(",")));
        } else {
            alarm.setPropagateRelationTypes(Collections.emptyList());
        }
        return alarm;
    }
}
