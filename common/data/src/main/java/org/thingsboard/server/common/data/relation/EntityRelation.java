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
package org.thingsboard.server.common.data.relation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.BaseDataWithAdditionalInfo;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.validation.Length;

import java.io.Serializable;

@Slf4j
@ApiModel
public class EntityRelation implements Serializable {

    private static final long serialVersionUID = 2807343040519543363L;

    public static final String INTEGRATION_TYPE = "ManagedByIntegration";
    public static final String EDGE_TYPE = "ManagedByEdge";
    public static final String CONTAINS_TYPE = "Contains";
    public static final String MANAGES_TYPE = "Manages";

    private EntityId from;
    private EntityId to;
    @Length(fieldName = "type")
    private String type;
    private RelationTypeGroup typeGroup;
    private transient JsonNode additionalInfo;
    @JsonIgnore
    private byte[] additionalInfoBytes;

    public EntityRelation() {
        super();
    }

    public EntityRelation(EntityId from, EntityId to, String type) {
        this(from, to, type, RelationTypeGroup.COMMON);
    }

    public EntityRelation(EntityId from, EntityId to, String type, RelationTypeGroup typeGroup) {
        this(from, to, type, typeGroup, null);
    }

    public EntityRelation(EntityId from, EntityId to, String type, RelationTypeGroup typeGroup, JsonNode additionalInfo) {
        this.from = from;
        this.to = to;
        this.type = type;
        this.typeGroup = typeGroup;
        this.additionalInfo = additionalInfo;
    }

    public EntityRelation(EntityRelation entityRelation) {
        this.from = entityRelation.getFrom();
        this.to = entityRelation.getTo();
        this.type = entityRelation.getType();
        this.typeGroup = entityRelation.getTypeGroup();
        this.additionalInfo = entityRelation.getAdditionalInfo();
    }

    @ApiModelProperty(position = 1, value = "JSON object with [from] Entity Id.", accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    public EntityId getFrom() {
        return from;
    }

    public void setFrom(EntityId from) {
        this.from = from;
    }

    @ApiModelProperty(position = 2, value = "JSON object with [to] Entity Id.", accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    public EntityId getTo() {
        return to;
    }

    public void setTo(EntityId to) {
        this.to = to;
    }

    @ApiModelProperty(position = 3, value = "String value of relation type.", example = "Contains")
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @ApiModelProperty(position = 4, value = "Represents the type group of the relation.", example = "COMMON")
    public RelationTypeGroup getTypeGroup() {
        return typeGroup;
    }

    public void setTypeGroup(RelationTypeGroup typeGroup) {
        this.typeGroup = typeGroup;
    }

    @ApiModelProperty(position = 5, value = "Additional parameters of the relation", dataType = "com.fasterxml.jackson.databind.JsonNode")
    public JsonNode getAdditionalInfo() {
        return BaseDataWithAdditionalInfo.getJson(() -> additionalInfo, () -> additionalInfoBytes);
    }

    public void setAdditionalInfo(JsonNode addInfo) {
        BaseDataWithAdditionalInfo.setJson(addInfo, json -> this.additionalInfo = json, bytes -> this.additionalInfoBytes = bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EntityRelation that = (EntityRelation) o;

        if (from != null ? !from.equals(that.from) : that.from != null) return false;
        if (to != null ? !to.equals(that.to) : that.to != null) return false;
        if (type != null ? !type.equals(that.type) : that.type != null) return false;
        return typeGroup == that.typeGroup;
    }

    @Override
    public int hashCode() {
        int result = from != null ? from.hashCode() : 0;
        result = 31 * result + (to != null ? to.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (typeGroup != null ? typeGroup.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "EntityRelation{" +
                "from=" + from +
                ", to=" + to +
                ", type='" + type + '\'' +
                ", typeGroup=" + typeGroup +
                '}';
    }
}
