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
package org.thingsboard.server.dao.settings;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.AdminSettingsId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.service.Validator;

@Service
@Slf4j
public class AdminSettingsServiceImpl implements AdminSettingsService {
    
    @Autowired
    private AdminSettingsDao adminSettingsDao;

    @Override
    public AdminSettings findAdminSettingsById(TenantId tenantId, AdminSettingsId adminSettingsId) {
        log.trace("Executing findAdminSettingsById [{}]", adminSettingsId);
        Validator.validateId(adminSettingsId, "Incorrect adminSettingsId " + adminSettingsId);
        return  adminSettingsDao.findById(tenantId, adminSettingsId.getId());
    }

    @Override
    public AdminSettings findAdminSettingsByKey(TenantId tenantId, String key) {
        log.trace("Executing findAdminSettingsByKey [{}]", key);
        Validator.validateString(key, "Incorrect key " + key);
        return adminSettingsDao.findByKey(tenantId, key);
    }

    @Override
    public void deleteAdminSettingsByKey(TenantId tenantId, String key) {
        log.trace("Executing deleteAdminSettingsByKey [{}]", key);
        AdminSettings adminSettings = findAdminSettingsByKey(tenantId, key);
        if (adminSettings != null) {
            adminSettingsDao.removeById(tenantId, adminSettings.getId().getId());
        }
    }

    public AdminSettings saveAdminSettings(TenantId tenantId, AdminSettings adminSettings) {
        log.trace("Executing saveAdminSettings [{}]", adminSettings);
        adminSettingsValidator.validate(adminSettings, data -> tenantId);
        if (adminSettings.getKey().equals("mail") && "".equals(adminSettings.getJsonValue().get("password").asText())) {
            AdminSettings mailSettings = findAdminSettingsByKey(tenantId, "mail");
            if (mailSettings != null) {
                ((ObjectNode) adminSettings.getJsonValue()).put("password", mailSettings.getJsonValue().get("password").asText());
            }
        }

        return adminSettingsDao.save(tenantId, adminSettings);
    }
    
    private DataValidator<AdminSettings> adminSettingsValidator =
            new DataValidator<AdminSettings>() {

                @Override
                protected void validateCreate(TenantId tenantId, AdminSettings adminSettings) {
                    AdminSettings existentAdminSettingsWithKey = findAdminSettingsByKey(tenantId, adminSettings.getKey());
                    if (existentAdminSettingsWithKey != null) {
                        throw new DataValidationException("Admin settings with such name already exists!");
                    }
                }

                @Override
                protected void validateUpdate(TenantId tenantId, AdminSettings adminSettings) {
                    AdminSettings existentAdminSettings = findAdminSettingsById(tenantId, adminSettings.getId());
                    if (existentAdminSettings != null) {
                        if (!existentAdminSettings.getKey().equals(adminSettings.getKey())) {
                            throw new DataValidationException("Changing key of admin settings entry is prohibited!");
                        }
                    }
                }

        
                @Override
                protected void validateDataImpl(TenantId tenantId, AdminSettings adminSettings) {
                    if (StringUtils.isEmpty(adminSettings.getKey())) {
                        throw new DataValidationException("Key should be specified!");
                    }
                    if (adminSettings.getJsonValue() == null) {
                        throw new DataValidationException("Json value should be specified!");
                    }
                }
    };

}
