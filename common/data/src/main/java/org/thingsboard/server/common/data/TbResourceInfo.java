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
package org.thingsboard.server.common.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.validation.Length;
import org.thingsboard.server.common.data.validation.NoXss;

import java.util.Objects;
import java.util.function.UnaryOperator;

@Schema
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class TbResourceInfo extends BaseData<TbResourceId> implements HasName, TenantEntity, ExportableEntity<TbResourceId> {

    private static final long serialVersionUID = 7282664529021651736L;

    @Schema(description = "JSON object with Tenant Id. Tenant Id of the resource can't be changed.", accessMode = Schema.AccessMode.READ_ONLY)
    private TenantId tenantId;
    @Schema(description = "JSON object with Customer Id. Customer Id of the resource can't be changed.", accessMode = Schema.AccessMode.READ_ONLY)
    private CustomerId customerId;
    @NoXss
    @Length(fieldName = "title")
    @Schema(description = "Resource title.", example = "BinaryAppDataContainer id=19 v1.0")
    private String title;
    @Schema(description = "Resource type.", example = "LWM2M_MODEL", accessMode = Schema.AccessMode.READ_ONLY)
    private ResourceType resourceType;
    @Schema(description = "Resource sub type.", example = "IOT_SVG", accessMode = Schema.AccessMode.READ_ONLY)
    private ResourceSubType resourceSubType;
    @NoXss
    @Length(fieldName = "resourceKey")
    @Schema(description = "Resource key.", example = "19_1.0", accessMode = Schema.AccessMode.READ_ONLY)
    private String resourceKey;
    private boolean isPublic;
    private String publicResourceKey;
    @Schema(description = "Resource search text.", example = "19_1.0:binaryappdatacontainer", accessMode = Schema.AccessMode.READ_ONLY)
    private String searchText;

    @Schema(description = "Resource etag.", example = "33a64df551425fcc55e4d42a148795d9f25f89d4", accessMode = Schema.AccessMode.READ_ONLY)
    private String etag;
    @NoXss
    @Length(fieldName = "file name")
    @Schema(description = "Resource file name.", example = "19.xml", accessMode = Schema.AccessMode.READ_ONLY)
    private String fileName;
    private JsonNode descriptor;

    private TbResourceId externalId;

    public TbResourceInfo() {
        super();
    }

    public TbResourceInfo(TbResourceId id) {
        super(id);
    }

    public TbResourceInfo(TbResourceInfo resourceInfo) {
        super(resourceInfo);
        this.tenantId = resourceInfo.tenantId;
        this.customerId = resourceInfo.customerId;
        this.title = resourceInfo.title;
        this.resourceType = resourceInfo.resourceType;
        this.resourceSubType = resourceInfo.resourceSubType;
        this.resourceKey = resourceInfo.resourceKey;
        this.searchText = resourceInfo.searchText;
        this.isPublic = resourceInfo.isPublic;
        this.publicResourceKey = resourceInfo.publicResourceKey;
        this.etag = resourceInfo.etag;
        this.fileName = resourceInfo.fileName;
        this.descriptor = resourceInfo.descriptor != null ? resourceInfo.descriptor.deepCopy() : null;
        this.externalId = resourceInfo.externalId;
    }

    @Schema(description = "JSON object with the Resource Id. " +
            "Specify this field to update the Resource. " +
            "Referencing non-existing Resource Id will cause error. " +
            "Omit this field to create new Resource.")
    @Override
    public TbResourceId getId() {
        return super.getId();
    }

    @Schema(description = "Timestamp of the resource creation, in milliseconds", example = "1609459200000", accessMode = Schema.AccessMode.READ_ONLY)
    @Override
    public long getCreatedTime() {
        return super.getCreatedTime();
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getName() {
        return title;
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getLink() {
        String scope = (tenantId != null && tenantId.isSysTenantId()) ? "system" : "tenant"; // tenantId is null in case of export to git
        if (resourceType == ResourceType.IMAGE) {
            return "/api/images/" + scope + "/" + resourceKey;
        } else {
            return "/api/resource/" + resourceType.name().toLowerCase() + "/" + scope + "/" + resourceKey;
        }
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getPublicLink() {
        if (resourceType == ResourceType.IMAGE && isPublic) {
            return "/api/images/public/" + getPublicResourceKey();
        }
        return null;
    }

    @JsonIgnore
    public String getSearchText() {
        return title;
    }

    @Override
    @JsonIgnore
    public EntityType getEntityType() {
        return EntityType.TB_RESOURCE;
    }

    @SneakyThrows
    public <T> T getDescriptor(Class<T> type) {
        return descriptor != null ? mapper.treeToValue(descriptor, type) : null;
    }

    public <T> void updateDescriptor(Class<T> type, UnaryOperator<T> updater) {
        T descriptor = getDescriptor(type);
        descriptor = updater.apply(descriptor);
        setDescriptorValue(descriptor);
    }

    @JsonIgnore
    public void setDescriptorValue(Object value) {
        this.descriptor = value != null ? mapper.valueToTree(value) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TbResourceInfo that = (TbResourceInfo) o;

        if (isPublic != that.isPublic) return false;
        if (!Objects.equals(tenantId, that.tenantId)) return false;
        if (!Objects.equals(title, that.title)) return false;
        if (resourceType != that.resourceType) return false;
        if (resourceSubType != that.resourceSubType) return false;
        if (!Objects.equals(resourceKey, that.resourceKey)) return false;
        if (!Objects.equals(publicResourceKey, that.publicResourceKey))
            return false;
        if (!Objects.equals(getSearchText(), that.getSearchText())) return false;
        if (!Objects.equals(etag, that.etag)) return false;
        if (!Objects.equals(fileName, that.fileName)) return false;
        if (!Objects.equals(descriptor, that.descriptor)) {
            if (!((descriptor == null || descriptor.isNull()) && (that.descriptor == null || that.descriptor.isNull()))){
                return false;
            }
        }
        return Objects.equals(externalId, that.externalId);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (tenantId != null ? tenantId.hashCode() : 0);
        result = 31 * result + (title != null ? title.hashCode() : 0);
        result = 31 * result + (resourceType != null ? resourceType.hashCode() : 0);
        result = 31 * result + (resourceSubType != null ? resourceSubType.hashCode() : 0);
        result = 31 * result + (resourceKey != null ? resourceKey.hashCode() : 0);
        result = 31 * result + (isPublic ? 1 : 0);
        result = 31 * result + (publicResourceKey != null ? publicResourceKey.hashCode() : 0);
        result = 31 * result + (searchText != null ? searchText.hashCode() : 0);
        result = 31 * result + (etag != null ? etag.hashCode() : 0);
        result = 31 * result + (fileName != null ? fileName.hashCode() : 0);
        result = 31 * result + (descriptor != null ? descriptor.hashCode() : 0);
        result = 31 * result + (externalId != null ? externalId.hashCode() : 0);
        return result;
    }
}
