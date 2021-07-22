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
package org.thingsboard.server.dao.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.cloud.CloudEvent;
import org.thingsboard.server.common.data.edge.EdgeSettings;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.TimePageLink;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.exception.DataValidationException;
import org.thingsboard.server.dao.service.DataValidator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.thingsboard.server.dao.service.Validator.validateId;

@Service
@Slf4j
public class BaseCloudEventService implements CloudEventService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public CloudEventDao cloudEventDao;

    @Autowired
    public AttributesService attributesService;

    @Override
    public CloudEvent save(CloudEvent cloudEvent) {
        cloudEventValidator.validate(cloudEvent, CloudEvent::getTenantId);
        return cloudEventDao.save(cloudEvent);
    }

    @Override
    public PageData<CloudEvent> findCloudEvents(TenantId tenantId, TimePageLink pageLink) {
        return cloudEventDao.findCloudEvents(tenantId.getId(), pageLink);
    }

    @Override
    public EdgeSettings findEdgeSettings(TenantId tenantId) {
        try {
            Optional<AttributeKvEntry> attr =
                    attributesService.find(tenantId, tenantId, DataConstants.SERVER_SCOPE, DataConstants.EDGE_SETTINGS_ATTR_KEY).get();
            if (attr.isPresent()) {
                return mapper.readValue(attr.get().getValueAsString(), EdgeSettings.class);
            } else {
                return null;
            }
        } catch (Exception e) {
            log.error("Exception while fetching edge settings", e);
            throw new RuntimeException("Exception while fetching edge settings", e);
        }
    }

    @Override
    public void deleteCloudEventsByTenantId(TenantId tenantId) {
        log.trace("Executing deleteCloudEventsByTenantId, tenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        TimePageLink pageLink = new TimePageLink(100);
        PageData<CloudEvent> pageData;
        do {
            pageData = findCloudEvents(tenantId, pageLink);
            for (CloudEvent entity : pageData.getData()) {
                cloudEventDao.removeById(tenantId, entity.getId().getId());
            }
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
    }

    @Override
    public ListenableFuture<List<Void>> saveEdgeSettings(TenantId tenantId, EdgeSettings edgeSettings) {
        try {
            BaseAttributeKvEntry edgeSettingAttr =
                    new BaseAttributeKvEntry(new StringDataEntry(DataConstants.EDGE_SETTINGS_ATTR_KEY, mapper.writeValueAsString(edgeSettings)), System.currentTimeMillis());
            List<AttributeKvEntry> attributes =
                    Collections.singletonList(edgeSettingAttr);
             return attributesService.save(tenantId, tenantId, DataConstants.SERVER_SCOPE, attributes);
        } catch (Exception e) {
            log.error("Exception while saving edge settings", e);
            throw new RuntimeException("Exception while saving edge settings", e);
        }
    }

    private DataValidator<CloudEvent> cloudEventValidator =
            new DataValidator<CloudEvent>() {
                @Override
                protected void validateDataImpl(TenantId tenantId, CloudEvent cloudEvent) {
                    if (StringUtils.isEmpty(cloudEvent.getCloudEventAction())) {
                        throw new DataValidationException("Cloud Event action should be specified!");
                    }
                }
            };
}
