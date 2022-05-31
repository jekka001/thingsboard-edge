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
package org.thingsboard.server.service.resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.leshan.core.model.DDFFileParser;
import org.eclipse.leshan.core.model.DefaultDDFFileValidator;
import org.eclipse.leshan.core.model.InvalidDDFFileException;
import org.eclipse.leshan.core.model.ObjectModel;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.lwm2m.LwM2mInstance;
import org.thingsboard.server.common.data.lwm2m.LwM2mObject;
import org.thingsboard.server.common.data.lwm2m.LwM2mResourceObserve;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.exception.DataValidationException;
import org.thingsboard.server.dao.resource.ResourceService;
import org.thingsboard.server.service.entitiy.AbstractTbEntityService;
import org.thingsboard.server.service.security.model.SecurityUser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.thingsboard.server.common.data.lwm2m.LwM2mConstants.LWM2M_SEPARATOR_KEY;
import static org.thingsboard.server.common.data.lwm2m.LwM2mConstants.LWM2M_SEPARATOR_SEARCH_TEXT;
import static org.thingsboard.server.dao.device.DeviceServiceImpl.INCORRECT_TENANT_ID;
import static org.thingsboard.server.dao.service.Validator.validateId;

@Slf4j
@Service
public class DefaultTbResourceService extends AbstractTbEntityService implements TbResourceService {

    private final ResourceService resourceService;
    private final DDFFileParser ddfFileParser;

    public DefaultTbResourceService(ResourceService resourceService) {
        this.resourceService = resourceService;
        this.ddfFileParser = new DDFFileParser(new DefaultDDFFileValidator());
    }

    @Override
    public TbResource saveResource(TbResource resource) throws ThingsboardException {
        log.trace("Executing saveResource [{}]", resource);
        if (StringUtils.isEmpty(resource.getData())) {
            throw new DataValidationException("Resource data should be specified!");
        }
        if (ResourceType.LWM2M_MODEL.equals(resource.getResourceType())) {
            try {
                List<ObjectModel> objectModels =
                        ddfFileParser.parse(new ByteArrayInputStream(Base64.getDecoder().decode(resource.getData())), resource.getSearchText());
                if (!objectModels.isEmpty()) {
                    ObjectModel objectModel = objectModels.get(0);

                    String resourceKey = objectModel.id + LWM2M_SEPARATOR_KEY + objectModel.version;
                    String name = objectModel.name;
                    resource.setResourceKey(resourceKey);
                    if (resource.getId() == null) {
                        resource.setTitle(name + " id=" + objectModel.id + " v" + objectModel.version);
                    }
                    resource.setSearchText(resourceKey + LWM2M_SEPARATOR_SEARCH_TEXT + name);
                } else {
                    throw new DataValidationException(String.format("Could not parse the XML of objectModel with name %s", resource.getSearchText()));
                }
            } catch (InvalidDDFFileException e) {
                log.error("Failed to parse file {}", resource.getFileName(), e);
                throw new DataValidationException("Failed to parse file " + resource.getFileName());
            } catch (IOException e) {
                throw new ThingsboardException(e, ThingsboardErrorCode.GENERAL);
            }
            if (resource.getResourceType().equals(ResourceType.LWM2M_MODEL) && toLwM2mObject(resource, true) == null) {
                throw new DataValidationException(String.format("Could not parse the XML of objectModel with name %s", resource.getSearchText()));
            }
        } else {
            resource.setResourceKey(resource.getFileName());
        }

        return resourceService.saveResource(resource);
    }

    @Override
    public TbResource getResource(TenantId tenantId, ResourceType resourceType, String resourceId) {
        return resourceService.getResource(tenantId, resourceType, resourceId);
    }

    @Override
    public TbResource findResourceById(TenantId tenantId, TbResourceId resourceId) {
        return resourceService.findResourceById(tenantId, resourceId);
    }

    @Override
    public TbResourceInfo findResourceInfoById(TenantId tenantId, TbResourceId resourceId) {
        return resourceService.findResourceInfoById(tenantId, resourceId);
    }

    @Override
    public PageData<TbResourceInfo> findAllTenantResourcesByTenantId(TenantId tenantId, PageLink pageLink) {
        return resourceService.findAllTenantResourcesByTenantId(tenantId, pageLink);
    }

    @Override
    public PageData<TbResourceInfo> findTenantResourcesByTenantId(TenantId tenantId, PageLink pageLink) {
        return resourceService.findTenantResourcesByTenantId(tenantId, pageLink);
    }

    @Override
    public List<LwM2mObject> findLwM2mObject(TenantId tenantId, String sortOrder, String sortProperty, String[] objectIds) {
        log.trace("Executing findByTenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        List<TbResource> resources = resourceService.findTenantResourcesByResourceTypeAndObjectIds(tenantId, ResourceType.LWM2M_MODEL,
                objectIds);
        return resources.stream()
                .flatMap(s -> Stream.ofNullable(toLwM2mObject(s, false)))
                .sorted(getComparator(sortProperty, sortOrder))
                .collect(Collectors.toList());
    }

    @Override
    public List<LwM2mObject> findLwM2mObjectPage(TenantId tenantId, String sortProperty, String sortOrder, PageLink pageLink) {
        log.trace("Executing findByTenantId [{}]", tenantId);
        validateId(tenantId, INCORRECT_TENANT_ID + tenantId);
        PageData<TbResource> resourcePageData = resourceService.findTenantResourcesByResourceTypeAndPageLink(tenantId, ResourceType.LWM2M_MODEL, pageLink);
        return resourcePageData.getData().stream()
                .flatMap(s -> Stream.ofNullable(toLwM2mObject(s, false)))
                .sorted(getComparator(sortProperty, sortOrder))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteResource(TenantId tenantId, TbResourceId resourceId) {
        resourceService.deleteResource(tenantId, resourceId);
    }

    @Override
    public void deleteResourcesByTenantId(TenantId tenantId) {
        resourceService.deleteResourcesByTenantId(tenantId);
    }

    @Override
    public long sumDataSizeByTenantId(TenantId tenantId) {
        return resourceService.sumDataSizeByTenantId(tenantId);
    }

    private Comparator<? super LwM2mObject> getComparator(String sortProperty, String sortOrder) {
        Comparator<LwM2mObject> comparator;
        if ("name".equals(sortProperty)) {
            comparator = Comparator.comparing(LwM2mObject::getName);
        } else {
            comparator = Comparator.comparingLong(LwM2mObject::getId);
        }
        return "DESC".equals(sortOrder) ? comparator.reversed() : comparator;
    }

    private LwM2mObject toLwM2mObject(TbResource resource, boolean isSave) {
        try {
            DDFFileParser ddfFileParser = new DDFFileParser(new DefaultDDFFileValidator());
            List<ObjectModel> objectModels =
                    ddfFileParser.parse(new ByteArrayInputStream(Base64.getDecoder().decode(resource.getData())), resource.getSearchText());
            if (objectModels.size() == 0) {
                return null;
            } else {
                ObjectModel obj = objectModels.get(0);
                LwM2mObject lwM2mObject = new LwM2mObject();
                lwM2mObject.setId(obj.id);
                lwM2mObject.setKeyId(resource.getResourceKey());
                lwM2mObject.setName(obj.name);
                lwM2mObject.setMultiple(obj.multiple);
                lwM2mObject.setMandatory(obj.mandatory);
                LwM2mInstance instance = new LwM2mInstance();
                instance.setId(0);
                List<LwM2mResourceObserve> resources = new ArrayList<>();
                obj.resources.forEach((k, v) -> {
                    if (isSave) {
                        LwM2mResourceObserve lwM2MResourceObserve = new LwM2mResourceObserve(k, v.name, false, false, false);
                        resources.add(lwM2MResourceObserve);
                    } else if (v.operations.isReadable()) {
                        LwM2mResourceObserve lwM2MResourceObserve = new LwM2mResourceObserve(k, v.name, false, false, false);
                        resources.add(lwM2MResourceObserve);
                    }
                });
                if (isSave || resources.size() > 0) {
                    instance.setResources(resources.toArray(LwM2mResourceObserve[]::new));
                    lwM2mObject.setInstances(new LwM2mInstance[]{instance});
                    return lwM2mObject;
                } else {
                    return null;
                }
            }
        } catch (IOException | InvalidDDFFileException e) {
            log.error("Could not parse the XML of objectModel with name [{}]", resource.getSearchText(), e);
            return null;
        }
    }

    @Override
    public TbResource save(TbResource tbResource, EntityGroup entityGroup, SecurityUser user) throws ThingsboardException {
        ActionType actionType = tbResource.getId() == null ? ActionType.ADDED : ActionType.UPDATED;
        TenantId tenantId = tbResource.getTenantId();
        try {

            TbResource savedResource = checkNotNull(resourceService.saveResource(tbResource));
            tbClusterService.onResourceChange(savedResource, null);
            notificationEntityService.notifyCreateOrUpdateOrDelete(tenantId, null, savedResource.getId(),
                    savedResource, user, actionType, false, null);
            return savedResource;
        } catch (Exception e) {
            notificationEntityService.notifyCreateOrUpdateOrDelete(tenantId, null, emptyId(EntityType.TB_RESOURCE),
                    tbResource, user, actionType, false, e);
            throw handleException(e);
        }
    }

    @Override
    public void delete(TbResource tbResource, SecurityUser user) throws ThingsboardException {
        TbResourceId resourceId = tbResource.getId();
        TenantId tenantId = tbResource.getTenantId();
        try {
            resourceService.deleteResource(tenantId, resourceId);
            tbClusterService.onResourceDeleted(tbResource, null);
            notificationEntityService.notifyCreateOrUpdateOrDelete(tenantId, null, resourceId, tbResource, user, ActionType.DELETED,
                    false, null, resourceId.toString());
        } catch (Exception e) {
            notificationEntityService.notifyCreateOrUpdateOrDelete(tenantId, null, emptyId(EntityType.TB_RESOURCE), null, user, ActionType.DELETED,
                    false, e, resourceId.toString());
            throw handleException(e);
        }
    }
}
