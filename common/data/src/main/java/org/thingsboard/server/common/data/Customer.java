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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.validation.NoXss;

public class Customer extends ContactBased<CustomerId> implements GroupEntity<CustomerId> {

    private static final long serialVersionUID = -1599722990298929275L;

    @NoXss
    private String title;
    private TenantId tenantId;
    private CustomerId parentCustomerId;

    public Customer() {
        super();
    }

    public Customer(CustomerId id) {
        super(id);
    }
    
    public Customer(Customer customer) {
        super(customer);
        this.tenantId = customer.getTenantId();
        this.title = customer.getTitle();
    }

    public TenantId getTenantId() {
        return tenantId;
    }

    public void setTenantId(TenantId tenantId) {
        this.tenantId = tenantId;
    }

    public CustomerId getParentCustomerId() {
        return parentCustomerId;
    }

    public void setParentCustomerId(CustomerId parentCustomerId) {
        this.parentCustomerId = parentCustomerId;
    }

    @Override
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public CustomerId getCustomerId() {
        return parentCustomerId;
    }

    @Override
    public EntityId getOwnerId() {
        return parentCustomerId != null && !parentCustomerId.isNullUid() ? parentCustomerId : tenantId;
    }

    @Override
    public void setOwnerId(EntityId entityId) {
        if (EntityType.CUSTOMER.equals(entityId.getEntityType())) {
            this.parentCustomerId = new CustomerId(entityId.getId());
        } else {
            this.parentCustomerId = new CustomerId(CustomerId.NULL_UUID);
        }
    }

    @JsonIgnore
    public boolean isSubCustomer() {
        return parentCustomerId != null && !parentCustomerId.isNullUid();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @JsonIgnore
    public boolean isPublic() {
        if (getAdditionalInfo() != null && getAdditionalInfo().has("isPublic")) {
            return getAdditionalInfo().get("isPublic").asBoolean();
        }

        return false;
    }

    @JsonIgnore
    public ShortCustomerInfo toShortCustomerInfo() {
        return new ShortCustomerInfo(id, title, isPublic());
    }

    @Override
    @JsonProperty(access = Access.READ_ONLY)
    public String getName() {
        return title;
    }

    @Override
    public String getSearchText() {
        return getTitle();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((tenantId == null) ? 0 : tenantId.hashCode());
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        Customer other = (Customer) obj;
        if (tenantId == null) {
            if (other.tenantId != null)
                return false;
        } else if (!tenantId.equals(other.tenantId))
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Customer [title=");
        builder.append(title);
        builder.append(", tenantId=");
        builder.append(tenantId);
        builder.append(", additionalInfo=");
        builder.append(getAdditionalInfo());
        builder.append(", country=");
        builder.append(country);
        builder.append(", state=");
        builder.append(state);
        builder.append(", city=");
        builder.append(city);
        builder.append(", address=");
        builder.append(address);
        builder.append(", address2=");
        builder.append(address2);
        builder.append(", zip=");
        builder.append(zip);
        builder.append(", phone=");
        builder.append(phone);
        builder.append(", email=");
        builder.append(email);
        builder.append(", createdTime=");
        builder.append(createdTime);
        builder.append(", id=");
        builder.append(id);
        builder.append("]");
        return builder.toString();
    }

    @Override
    @JsonIgnore
    public EntityType getEntityType() {
        return EntityType.CUSTOMER;
    }


}
