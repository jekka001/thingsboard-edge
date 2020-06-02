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
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.role.RoleType;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

import static org.thingsboard.server.dao.model.ModelConstants.*;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = ModelConstants.ROLE_TABLE_FAMILY_NAME)
@Slf4j
public class RoleEntity extends BaseSqlEntity<Role> implements SearchTextEntity<Role> {

    @Column(name = ROLE_TENANT_ID_PROPERTY)
    private String tenantId;

    @Column(name = ROLE_CUSTOMER_ID_PROPERTY)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = ROLE_TYPE_PROPERTY)
    private RoleType type;

    @Column(name = ROLE_NAME_PROPERTY)
    private String name;

    @Type(type = "json")
    @Column(name = ROLE_PERMISSIONS_PROPERTY)
    private JsonNode permissions;

    @Column(name = SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Type(type = "json")
    @Column(name = ModelConstants.ENTITY_VIEW_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    private static final ObjectMapper mapper = new ObjectMapper();

    public RoleEntity() {
        super();
    }

    public RoleEntity(Role role) {
        if (role.getId() != null) {
            this.setUuid(role.getId().getId());
        }
        if (role.getTenantId() != null) {
            this.tenantId = toString(role.getTenantId().getId());
        }
        if (role.getCustomerId() != null) {
            this.customerId = toString(role.getCustomerId().getId());
        }
        this.type = role.getType();
        this.name = role.getName();
        this.permissions = role.getPermissions();
        this.searchText = role.getSearchText();
        this.additionalInfo = role.getAdditionalInfo();
    }

    @Override
    public String getSearchTextSource() {
        return name;
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    @Override
    public Role toData() {
        Role role = new Role(new RoleId(getUuid()));
        role.setCreatedTime(Uuids.unixTimestamp(getUuid()));

        if (tenantId != null) {
            role.setTenantId(new TenantId(toUUID(tenantId)));
        }
        if (customerId != null) {
            role.setCustomerId(new CustomerId(toUUID(customerId)));
        }
        role.setType(type);
        role.setName(name);
        role.setPermissions(permissions);
        role.setAdditionalInfo(additionalInfo);
        return role;
    }
}
