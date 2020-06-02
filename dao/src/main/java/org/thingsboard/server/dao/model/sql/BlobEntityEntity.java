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
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.util.Base64Utils;
import org.thingsboard.server.common.data.UUIDConverter;
import org.thingsboard.server.common.data.blob.BlobEntity;
import org.thingsboard.server.common.data.id.BlobEntityId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.model.BaseSqlEntity;
import org.thingsboard.server.dao.model.SearchTextEntity;
import org.thingsboard.server.dao.util.mapping.JsonStringType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.nio.ByteBuffer;

import static org.thingsboard.server.dao.model.ModelConstants.*;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@TypeDef(name = "json", typeClass = JsonStringType.class)
@Table(name = BLOB_ENTITY_COLUMN_FAMILY_NAME)
public final class BlobEntityEntity extends BaseSqlEntity<BlobEntity> implements SearchTextEntity<BlobEntity> {

    @Column(name = BLOB_ENTITY_TENANT_ID_PROPERTY)
    private String tenantId;

    @Column(name = BLOB_ENTITY_CUSTOMER_ID_PROPERTY)
    private String customerId;

    @Column(name = BLOB_ENTITY_NAME_PROPERTY)
    private String name;

    @Column(name = BLOB_ENTITY_TYPE_PROPERTY)
    private String type;

    @Column(name = BLOB_ENTITY_CONTENT_TYPE_PROPERTY)
    private String contentType;

    @Column(name = SEARCH_TEXT_PROPERTY)
    private String searchText;

    @Column(name = BLOB_ENTITY_DATA_PROPERTY)
    private String data;

    @Type(type = "json")
    @Column(name = BLOB_ENTITY_ADDITIONAL_INFO_PROPERTY)
    private JsonNode additionalInfo;

    public BlobEntityEntity() {
        super();
    }

    public BlobEntityEntity(BlobEntity blobEntity) {
        if (blobEntity.getId() != null) {
            this.setUuid(blobEntity.getId().getId());
        }
        if (blobEntity.getTenantId() != null) {
            this.tenantId = UUIDConverter.fromTimeUUID(blobEntity.getTenantId().getId());
        }
        if (blobEntity.getCustomerId() != null) {
            this.customerId = UUIDConverter.fromTimeUUID(blobEntity.getCustomerId().getId());
        }
        this.name = blobEntity.getName();
        this.type = blobEntity.getType();
        this.contentType = blobEntity.getContentType();
        this.additionalInfo = blobEntity.getAdditionalInfo();
        this.data = Base64Utils.encodeToString(blobEntity.getData().array());
    }

    @Override
    public String getSearchTextSource() {
        return name;
    }

    @Override
    public void setSearchText(String searchText) {
        this.searchText = searchText;
    }

    public String getSearchText() {
        return searchText;
    }

    @Override
    public BlobEntity toData() {
        BlobEntity blobEntity = new BlobEntity(new BlobEntityId(UUIDConverter.fromString(id)));
        blobEntity.setCreatedTime(Uuids.unixTimestamp(UUIDConverter.fromString(id)));
        if (tenantId != null) {
            blobEntity.setTenantId(new TenantId(UUIDConverter.fromString(tenantId)));
        }
        if (customerId != null) {
            blobEntity.setCustomerId(new CustomerId(UUIDConverter.fromString(customerId)));
        }
        blobEntity.setName(name);
        blobEntity.setType(type);
        blobEntity.setContentType(contentType);
        blobEntity.setAdditionalInfo(additionalInfo);
        blobEntity.setData(ByteBuffer.wrap(Base64Utils.decodeFromString(data)));
        return blobEntity;
    }

}
