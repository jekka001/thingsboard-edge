/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.EntityViewId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.objects.TelemetryEntityView;
import org.thingsboard.server.common.data.validation.NoXss;

/**
 * Created by Victor Basanets on 8/27/2017.
 */

@Data
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EntityView extends SearchTextBasedWithAdditionalInfo<EntityViewId>
        implements GroupEntity<EntityViewId> {

    private static final long serialVersionUID = 5582010124562018986L;

    private EntityId entityId;
    private TenantId tenantId;
    private CustomerId customerId;
    @NoXss
    private String name;
    @NoXss
    private String type;
    private TelemetryEntityView keys;
    private long startTimeMs;
    private long endTimeMs;

    public EntityView() {
        super();
    }

    public EntityView(EntityViewId id) {
        super(id);
    }

    public EntityView(EntityView entityView) {
        super(entityView);
        this.entityId = entityView.getEntityId();
        this.tenantId = entityView.getTenantId();
        this.customerId = entityView.getCustomerId();
        this.name = entityView.getName();
        this.type = entityView.getType();
        this.keys = entityView.getKeys();
        this.startTimeMs = entityView.getStartTimeMs();
        this.endTimeMs = entityView.getEndTimeMs();
    }

    @Override
    public String getSearchText() {
        return getName() /*What the ...*/;
    }

    @Override
    public CustomerId getCustomerId() {
        return customerId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public TenantId getTenantId() {
        return tenantId;
    }

    @Override
    public EntityId getOwnerId() {
        return customerId != null && !customerId.isNullUid() ? customerId : tenantId;
    }

    @Override
    public void setOwnerId(EntityId entityId) {
        if (EntityType.CUSTOMER.equals(entityId.getEntityType())) {
            this.customerId = new CustomerId(entityId.getId());
        } else {
            this.customerId = new CustomerId(CustomerId.NULL_UUID);
        }
    }

    @Override
    @JsonIgnore
    public EntityType getEntityType() {
        return EntityType.ENTITY_VIEW;
    }

}
