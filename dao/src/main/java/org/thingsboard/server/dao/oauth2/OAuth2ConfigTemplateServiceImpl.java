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
package org.thingsboard.server.dao.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.Tenant;
import org.thingsboard.server.common.data.id.OAuth2ClientRegistrationTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.oauth2.*;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;

import java.util.List;
import java.util.Optional;

import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.service.Validator.validateString;

@Slf4j
@Service
public class OAuth2ConfigTemplateServiceImpl extends AbstractEntityService implements OAuth2ConfigTemplateService {
    public static final String INCORRECT_CLIENT_REGISTRATION_TEMPLATE_ID = "Incorrect clientRegistrationTemplateId ";
    public static final String INCORRECT_CLIENT_REGISTRATION_PROVIDER_ID = "Incorrect clientRegistrationProviderId ";

    @Autowired
    private OAuth2ClientRegistrationTemplateDao clientRegistrationTemplateDao;

    @Override
    public OAuth2ClientRegistrationTemplate saveClientRegistrationTemplate(OAuth2ClientRegistrationTemplate clientRegistrationTemplate) {
        log.trace("Executing saveClientRegistrationTemplate [{}]", clientRegistrationTemplate);
        clientRegistrationTemplateValidator.validate(clientRegistrationTemplate, o -> TenantId.SYS_TENANT_ID);
        OAuth2ClientRegistrationTemplate savedClientRegistrationTemplate;
        try {
            savedClientRegistrationTemplate = clientRegistrationTemplateDao.save(TenantId.SYS_TENANT_ID, clientRegistrationTemplate);
        } catch (Exception t) {
            ConstraintViolationException e = extractConstraintViolationException(t).orElse(null);
            if (e != null && e.getConstraintName() != null && e.getConstraintName().equalsIgnoreCase("oauth2_template_provider_id_unq_key")) {
                throw new DataValidationException("Client registration template with such providerId already exists!");
            } else {
                throw t;
            }
        }
        return savedClientRegistrationTemplate;
    }

    @Override
    public Optional<OAuth2ClientRegistrationTemplate> findClientRegistrationTemplateByProviderId(String providerId) {
        log.trace("Executing findClientRegistrationTemplateByProviderId [{}]", providerId);
        validateString(providerId, INCORRECT_CLIENT_REGISTRATION_PROVIDER_ID + providerId);
        return clientRegistrationTemplateDao.findByProviderId(providerId);
    }

    @Override
    public OAuth2ClientRegistrationTemplate findClientRegistrationTemplateById(OAuth2ClientRegistrationTemplateId templateId) {
        log.trace("Executing findClientRegistrationTemplateById [{}]", templateId);
        validateId(templateId, INCORRECT_CLIENT_REGISTRATION_TEMPLATE_ID + templateId);
        return clientRegistrationTemplateDao.findById(TenantId.SYS_TENANT_ID, templateId.getId());
    }

    @Override
    public List<OAuth2ClientRegistrationTemplate> findAllClientRegistrationTemplates() {
        log.trace("Executing findAllClientRegistrationTemplates");
        return clientRegistrationTemplateDao.findAll();
    }

    @Override
    public void deleteClientRegistrationTemplateById(OAuth2ClientRegistrationTemplateId templateId) {
        log.trace("Executing deleteClientRegistrationTemplateById [{}]", templateId);
        validateId(templateId, INCORRECT_CLIENT_REGISTRATION_TEMPLATE_ID + templateId);
        clientRegistrationTemplateDao.removeById(TenantId.SYS_TENANT_ID, templateId.getId());
    }

    private final DataValidator<OAuth2ClientRegistrationTemplate> clientRegistrationTemplateValidator =
            new DataValidator<OAuth2ClientRegistrationTemplate>() {

                @Override
                protected void validateCreate(TenantId tenantId, OAuth2ClientRegistrationTemplate clientRegistrationTemplate) {
                }

                @Override
                protected void validateUpdate(TenantId tenantId, OAuth2ClientRegistrationTemplate clientRegistrationTemplate) {
                }

                @Override
                protected void validateDataImpl(TenantId tenantId, OAuth2ClientRegistrationTemplate clientRegistrationTemplate) {
                    if (StringUtils.isEmpty(clientRegistrationTemplate.getProviderId())) {
                        throw new DataValidationException("Provider ID should be specified!");
                    }
                    if (clientRegistrationTemplate.getMapperConfig() == null) {
                        throw new DataValidationException("Mapper config should be specified!");
                    }
                    if (clientRegistrationTemplate.getMapperConfig().getType() == null) {
                        throw new DataValidationException("Mapper type should be specified!");
                    }
                    if (clientRegistrationTemplate.getMapperConfig().getBasic() == null) {
                        throw new DataValidationException("Basic mapper config should be specified!");
                    }
                }
            };
}
