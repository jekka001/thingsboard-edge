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
package org.thingsboard.server.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.AdminSettingsId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseEntity;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.util.mapping.JsonConverter;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.ADMIN_SETTINGS_JSON_VALUE_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ADMIN_SETTINGS_KEY_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.ADMIN_SETTINGS_TABLE_NAME;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ADMIN_SETTINGS_TABLE_NAME)
public final class AdminSettingsEntity extends BaseSqlEntity<AdminSettings> implements BaseEntity<AdminSettings> {

    @Column(name = ModelConstants.ADMIN_SETTINGS_TENANT_ID_PROPERTY)
    private UUID tenantId;

    @Column(name = ADMIN_SETTINGS_KEY_PROPERTY)
    private String key;

    @Convert(converter = JsonConverter.class)
    @Column(name = ADMIN_SETTINGS_JSON_VALUE_PROPERTY)
    private JsonNode jsonValue;

    public AdminSettingsEntity() {
        super();
    }

    public AdminSettingsEntity(AdminSettings adminSettings) {
        if (adminSettings.getId() != null) {
            this.setUuid(adminSettings.getId().getId());
        }
        this.setCreatedTime(adminSettings.getCreatedTime());
        this.tenantId = adminSettings.getTenantId().getId();
        this.key = adminSettings.getKey();
        this.jsonValue = adminSettings.getJsonValue();
    }

    @Override
    public AdminSettings toData() {
        AdminSettings adminSettings = new AdminSettings(new AdminSettingsId(id));
        adminSettings.setCreatedTime(createdTime);
        adminSettings.setTenantId(TenantId.fromUUID(tenantId));
        adminSettings.setKey(key);
        adminSettings.setJsonValue(jsonValue);
        return adminSettings;
    }

}
