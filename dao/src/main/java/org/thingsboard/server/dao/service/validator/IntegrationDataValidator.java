/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.service.validator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.dao.converter.ConverterDao;
import org.thingsboard.server.dao.integration.IntegrationDao;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.tenant.TbTenantProfileCache;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.exception.DataValidationException;

@Component
public class IntegrationDataValidator extends DataValidator<Integration> {

    @Autowired
    private IntegrationDao integrationDao;

    @Autowired
    private TenantService tenantService;

    @Autowired
    private ConverterDao converterDao;

    @Autowired
    @Lazy
    private TbTenantProfileCache tenantProfileCache;

    @Override
    protected void validateCreate(TenantId tenantId, Integration integration) {
        DefaultTenantProfileConfiguration profileConfiguration =
                (DefaultTenantProfileConfiguration) tenantProfileCache.get(tenantId).getProfileData().getConfiguration();
        long maxIntegrations = profileConfiguration.getMaxIntegrations();
        validateNumberOfEntitiesPerTenant(tenantId, integrationDao, maxIntegrations, EntityType.INTEGRATION);
        integrationDao.findByRoutingKey(tenantId.getId(), integration.getRoutingKey()).ifPresent(
                d -> {
                    throw new DataValidationException("Integration with such routing key already exists!");
                }
        );
    }

    @Override
    protected Integration validateUpdate(TenantId tenantId, Integration integration) {
        var old = integrationDao.findByRoutingKey(tenantId.getId(), integration.getRoutingKey());
        old.ifPresent(
                d -> {
                    if (!d.getId().equals(integration.getId())) {
                        throw new DataValidationException("Integration with such routing key already exists!");
                    }
                }
        );
        return old.orElse(null);
    }

    @Override
    protected void validateDataImpl(TenantId tenantId, Integration integration) {
        if (org.springframework.util.StringUtils.isEmpty(integration.getName())) {
            throw new DataValidationException("Integration name should be specified!");
        }
        if (integration.getType() == null) {
            throw new DataValidationException("Integration type should be specified!");
        }
        if (StringUtils.isEmpty(integration.getRoutingKey())) {
            throw new DataValidationException("Integration routing key should be specified!");
        }
        if (integration.getTenantId() == null || integration.getTenantId().isNullUid()) {
            throw new DataValidationException("Integration should be assigned to tenant!");
        } else {
            if (!tenantService.tenantExists(integration.getTenantId())) {
                throw new DataValidationException("Integration is referencing to non-existent tenant!");
            }
        }
        if (integration.getDefaultConverterId() == null || integration.getDefaultConverterId().isNullUid()) {
            throw new DataValidationException("Integration default converter should be specified!");
        } else {
            Converter converter = converterDao.findById(tenantId, integration.getDefaultConverterId().getId());
            if (converter == null) {
                throw new DataValidationException("Integration is referencing to non-existent converter!");
            }
            if (!converter.getTenantId().equals(integration.getTenantId())) {
                throw new DataValidationException("Integration can't have converter from different tenant!");
            }
            if (converter.isEdgeTemplate() != integration.isEdgeTemplate()) {
                throw new DataValidationException("Edge integration can't have non-edge converter and vise versa!");
            }
        }
    }
}
