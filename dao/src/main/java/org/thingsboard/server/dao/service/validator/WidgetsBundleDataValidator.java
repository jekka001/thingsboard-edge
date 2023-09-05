/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.model.ModelConstants;
import org.thingsboard.server.dao.service.DataValidator;
import org.thingsboard.server.dao.tenant.TenantService;
import org.thingsboard.server.dao.widget.WidgetsBundleDao;
import org.thingsboard.server.exception.DataValidationException;

@Component
@AllArgsConstructor
public class WidgetsBundleDataValidator extends DataValidator<WidgetsBundle> {

    private final WidgetsBundleDao widgetsBundleDao;
    private final TenantService tenantService;

    @Override
    protected void validateDataImpl(TenantId tenantId, WidgetsBundle widgetsBundle) {
        validateString("Widgets bundle title", widgetsBundle.getTitle());
        if (widgetsBundle.getTenantId() == null) {
            widgetsBundle.setTenantId(TenantId.fromUUID(ModelConstants.NULL_UUID));
        }
        if (!widgetsBundle.getTenantId().getId().equals(ModelConstants.NULL_UUID)) {
            if (!tenantService.tenantExists(widgetsBundle.getTenantId())) {
                throw new DataValidationException("Widgets bundle is referencing to non-existent tenant!");
            }
        }
    }

    @Override
    protected void validateCreate(TenantId tenantId, WidgetsBundle widgetsBundle) {
        String alias = widgetsBundle.getAlias();
        if (alias == null || alias.trim().isEmpty()) {
            alias = widgetsBundle.getTitle().toLowerCase().replaceAll("\\W+", "_");
        }
        String originalAlias = alias;
        int c = 1;
        WidgetsBundle withSameAlias;
        do {
            withSameAlias = widgetsBundleDao.findWidgetsBundleByTenantIdAndAlias(widgetsBundle.getTenantId().getId(), alias);
            if (withSameAlias != null) {
                alias = originalAlias + (++c);
            }
        } while (withSameAlias != null);
        widgetsBundle.setAlias(alias);
    }

    @Override
    protected WidgetsBundle validateUpdate(TenantId tenantId, WidgetsBundle widgetsBundle) {
        WidgetsBundle storedWidgetsBundle = widgetsBundleDao.findById(tenantId, widgetsBundle.getId().getId());
        if (!storedWidgetsBundle.getTenantId().getId().equals(widgetsBundle.getTenantId().getId())) {
            throw new DataValidationException("Can't move existing widgets bundle to different tenant!");
        }
        if (!storedWidgetsBundle.getAlias().equals(widgetsBundle.getAlias())) {
            throw new DataValidationException("Update of widgets bundle alias is prohibited!");
        }
        return storedWidgetsBundle;
    }
}
