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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import org.thingsboard.server.common.data.domain.DomainOauth2Client;
import org.thingsboard.server.common.data.id.DomainId;
import org.thingsboard.server.common.data.id.OAuth2ClientId;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.model.ToData;

import java.util.UUID;

import static org.thingsboard.server.dao.model.ModelConstants.DOMAIN_OAUTH2_CLIENT_DOMAIN_ID_PROPERTY;
import static org.thingsboard.server.dao.model.ModelConstants.DOMAIN_OAUTH2_CLIENT_TABLE_NAME;

@Data
@Entity
@Table(name = DOMAIN_OAUTH2_CLIENT_TABLE_NAME)
@IdClass(DomainOauth2ClientCompositeKey.class)
public final class DomainOauth2ClientEntity implements ToData<DomainOauth2Client> {

    @Id
    @Column(name = DOMAIN_OAUTH2_CLIENT_DOMAIN_ID_PROPERTY, columnDefinition = "uuid")
    private UUID domainId;

    @Id
    @Column(name = ModelConstants.DOMAIN_OAUTH2_CLIENT_CLIENT_ID_PROPERTY, columnDefinition = "uuid")
    private UUID oauth2ClientId;


    public DomainOauth2ClientEntity() {
        super();
    }

    public DomainOauth2ClientEntity(DomainOauth2Client domainOauth2Client) {
        domainId = domainOauth2Client.getDomainId().getId();
        oauth2ClientId = domainOauth2Client.getOAuth2ClientId().getId();
    }

    @Override
    public DomainOauth2Client toData() {
        DomainOauth2Client result = new DomainOauth2Client();
        result.setDomainId(new DomainId(domainId));
        result.setOAuth2ClientId(new OAuth2ClientId(oauth2ClientId));
        return result;
    }
}
