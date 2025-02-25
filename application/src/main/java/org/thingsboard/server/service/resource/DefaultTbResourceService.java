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
package org.thingsboard.server.service.resource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ResourceExportData;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.audit.ActionType;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.lwm2m.LwM2mObject;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.dao.resource.ImageService;
import org.thingsboard.server.dao.resource.ResourceService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.AbstractTbEntityService;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.permission.AccessControlService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.thingsboard.server.dao.device.DeviceServiceImpl.INCORRECT_TENANT_ID;
import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.utils.LwM2mObjectModelUtils.toLwM2mObject;
import static org.thingsboard.server.utils.LwM2mObjectModelUtils.toLwm2mResource;

@Slf4j
@Service
@TbCoreComponent
@RequiredArgsConstructor
public class DefaultTbResourceService extends AbstractTbEntityService implements TbResourceService {

    private final ResourceService resourceService;
    private final ImageService imageService;
    private final TbImageService tbImageService;
    private final AccessControlService accessControlService;

    @Override
    public TbResourceInfo save(TbResource resource, SecurityUser user) throws ThingsboardException {
        if (resource.getResourceType() == ResourceType.IMAGE) {
            throw new IllegalArgumentException("Image resource type is not supported");
        }
        ActionType actionType = resource.getId() == null ? ActionType.ADDED : ActionType.UPDATED;
        TenantId tenantId = resource.getTenantId();
        try {
            if (ResourceType.LWM2M_MODEL.equals(resource.getResourceType())) {
                toLwm2mResource(resource);
            } else if (resource.getResourceKey() == null) {
                resource.setResourceKey(resource.getFileName());
            }
            TbResourceInfo savedResource = new TbResourceInfo(resourceService.saveResource(resource));
            logEntityActionService.logEntityAction(tenantId, savedResource.getId(), savedResource, actionType, user);
            return savedResource;
        } catch (Exception e) {
            logEntityActionService.logEntityAction(tenantId, emptyId(EntityType.TB_RESOURCE), new TbResourceInfo(resource), actionType, user, e);
            throw e;
        }
    }

    @Override
    public void delete(TbResource tbResource, User user) {
        if (tbResource.getResourceType() == ResourceType.IMAGE) {
            throw new IllegalArgumentException("Image resource type is not supported");
        }
        ActionType actionType = ActionType.DELETED;
        TbResourceId resourceId = tbResource.getId();
        TenantId tenantId = tbResource.getTenantId();
        try {
            resourceService.deleteResource(tenantId, resourceId);
            logEntityActionService.logEntityAction(tenantId, resourceId, tbResource, actionType, user, resourceId.toString());
        } catch (Exception e) {
            logEntityActionService.logEntityAction(tenantId, emptyId(EntityType.TB_RESOURCE),
                    actionType, user, e, resourceId.toString());
            throw e;
        }
    }



    @Override
    public List<LwM2mObject> findLwM2mObject(TenantId tenantId, String sortOrder, String sortProperty, String[] objectIds) {
        log.trace("Executing findByTenantId [{}]", tenantId);
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
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
        validateId(tenantId, id -> INCORRECT_TENANT_ID + id);
        PageData<TbResource> resourcePageData = resourceService.findTenantResourcesByResourceTypeAndPageLink(tenantId, ResourceType.LWM2M_MODEL, pageLink);
        return resourcePageData.getData().stream()
                .flatMap(s -> Stream.ofNullable(toLwM2mObject(s, false)))
                .sorted(getComparator(sortProperty, sortOrder))
                .collect(Collectors.toList());
    }

    @Override
    public List<ResourceExportData> exportResources(Dashboard dashboard, SecurityUser user) throws ThingsboardException {
        return exportResources(() -> imageService.getUsedImages(dashboard), () -> resourceService.getUsedResources(user.getTenantId(), dashboard), user);
    }

    @Override
    public List<ResourceExportData> exportResources(WidgetTypeDetails widgetTypeDetails, SecurityUser user) throws ThingsboardException {
        return exportResources(() -> imageService.getUsedImages(widgetTypeDetails), () -> resourceService.getUsedResources(user.getTenantId(), widgetTypeDetails), user);
    }

    @Override
    public void importResources(List<ResourceExportData> resources, SecurityUser user) throws Exception {
        for (ResourceExportData resourceData : resources) {
            TbResourceInfo resourceInfo;
            if (resourceData.getType() == ResourceType.IMAGE) {
                resourceInfo = tbImageService.importImage(resourceData, true, user);
            } else {
                resourceInfo = importResource(resourceData, user);
            }
            resourceData.setNewLink(resourceInfo.getLink());
        }
    }

    private <T> List<ResourceExportData> exportResources(Supplier<Collection<TbResourceInfo>> imagesProcessor,
                                                         Supplier<Collection<TbResourceInfo>> resourcesProcessor,
                                                         SecurityUser user) throws ThingsboardException {
        List<TbResourceInfo> resources = new ArrayList<>();
        resources.addAll(imagesProcessor.get());
        resources.addAll(resourcesProcessor.get());
        for (TbResourceInfo resourceInfo : resources) {
            accessControlService.checkPermission(user, Resource.TB_RESOURCE, Operation.READ, resourceInfo.getId(), resourceInfo);
        }

        return resourceService.exportResources(user.getTenantId(), resources);
    }

    private TbResourceInfo importResource(ResourceExportData resourceData, SecurityUser user) throws ThingsboardException {
        TbResource resource = resourceService.toResource(user.getTenantId(), resourceData);
        if (resource.getData() != null) {
            accessControlService.checkPermission(user, Resource.TB_RESOURCE, Operation.CREATE, null, resource);
            return save(resource, user);
        } else {
            accessControlService.checkPermission(user, Resource.TB_RESOURCE, Operation.READ, resource.getId(), resource);
            return resource;
        }
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

}
