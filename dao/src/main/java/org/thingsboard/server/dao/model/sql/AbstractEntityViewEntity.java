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
package org.thingsboard.server.dao.model.sql;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.objects.TelemetryEntityView;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.util.mapping.JsonStringType;

import javax.persistence.*;
import java.io.IOException;
import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.ENTITY_TYPE_PROPERTY;

/**
 * Created by Victor Basanets on 8/30/2017.
 */

@Data
@EqualsAndHashCode(callSuper = true)
@TypeDef(name = "json", typeClass = JsonStringType.class)
@MappedSuperclass
@Slf4j
public abstract class AbstractEntityViewEntity<T extends EntityView> extends BaseSqlEntity<T> implements SearchTextEntity<T> {

    @Column(name = ModelConstants.ENTITY_VIEW_ENTITY_ID_PROPERTY)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = ENTITY_TYPE_PROPERTY)
    private EntityType entityType;

    @Column(name = ModelConstants.ENTITY_VIEW_TENANT_ID_PROPERTY)
    private String tenantId;

    @Column(name = ModelConstants.ENTITY_VIEW_CUSTOMER_ID_PROPERTY)
    private String customerId;

    @Column(name = ModelConstants.DEVICE_TYPE_PROPERTY)
    private String type;

    @Column(name = ModelConstants.ENTITY_VIEW_NAME_PROPERTY)
    private String name;

    @Column(name = ModelConstants.ENTITY_VIEW_KEYS_PROPERTY)
    private String keys;

    @Column(name = ModelConstants.ENTITY_VIEW_START_TS_PROPERTY)
    private long startTs;

    @Column(name = ModelConstants.ENTITY_VIEW_END_TS_PROPERTY)
    private long endTs;

    @Column(name = ModelConstants.SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Type(type = "json")
    @Column(name = ModelConstants.ENTITY_VIEW_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    private static final ObjectMapper mapper = new ObjectMapper();

    public AbstractEntityViewEntity() {
        super();
    }

    public AbstractEntityViewEntity(EntityView entityView) {
        if (entityView.getId() != null) {
            this.setUuid(entityView.getId().getId());
        }
        if (entityView.getEntityId() != null) {
            this.entityId = toString(entityView.getEntityId().getId());
            this.entityType = entityView.getEntityId().getEntityType();
        }
        if (entityView.getTenantId() != null) {
            this.tenantId = toString(entityView.getTenantId().getId());
        }
        if (entityView.getCustomerId() != null) {
            this.customerId = toString(entityView.getCustomerId().getId());
        }
        this.type = entityView.getType();
        this.name = entityView.getName();
        try {
            this.keys = mapper.writeValueAsString(entityView.getKeys());
        } catch (IOException e) {
            log.error("Unable to serialize entity view keys!", e);
        }
        this.startTs = entityView.getStartTimeMs();
        this.endTs = entityView.getEndTimeMs();
        this.searchText = entityView.getSearchText();
        this.additionalInfo = entityView.getAdditionalInfo();
    }

    public AbstractEntityViewEntity(EntityViewEntity entityViewEntity) {
        this.setId(entityViewEntity.getId());
        this.entityId = entityViewEntity.getEntityId();
        this.entityType = entityViewEntity.getEntityType();
        this.tenantId = entityViewEntity.getTenantId();
        this.customerId = entityViewEntity.getCustomerId();
        this.type = entityViewEntity.getType();
        this.name = entityViewEntity.getName();
        this.keys = entityViewEntity.getKeys();
        this.startTs = entityViewEntity.getStartTs();
        this.endTs = entityViewEntity.getEndTs();
        this.searchText = entityViewEntity.getSearchText();
        this.additionalInfo = entityViewEntity.getAdditionalInfo();
    }

    @Override
    public String getSearchTextSource() {
        return name;
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    protected EntityView toEntityView() {
        EntityView entityView = new EntityView(new EntityViewId(getUuid()));
        entityView.setCreatedTime(Uuids.unixTimestamp(getUuid()));

        if (entityId != null) {
            entityView.setEntityId(EntityIdFactory.getByTypeAndId(entityType.name(), toUUID(entityId).toString()));
        }
        if (tenantId != null) {
            entityView.setTenantId(new TenantId(toUUID(tenantId)));
        }
        if (customerId != null) {
            entityView.setCustomerId(new CustomerId(toUUID(customerId)));
        }
        entityView.setType(type);
        entityView.setName(name);
        try {
            entityView.setKeys(mapper.readValue(keys, TelemetryEntityView.class));
        } catch (IOException e) {
            log.error("Unable to read entity view keys!", e);
        }
        entityView.setStartTimeMs(startTs);
        entityView.setEndTimeMs(endTs);
        entityView.setAdditionalInfo(additionalInfo);
        return entityView;
    }
}
