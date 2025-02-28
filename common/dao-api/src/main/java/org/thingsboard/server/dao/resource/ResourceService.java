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
package org.thingsboard.server.dao.resource;

import com.google.common.util.concurrent.ListenableFuture;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.ResourceExportData;
import org.thingsboard.server.common.data.ResourceSubType;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceDeleteResult;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.TbResourceInfoFilter;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.dao.entity.EntityDaoService;

import java.util.Collection;
import java.util.List;

public interface ResourceService extends EntityDaoService {

    TbResource saveResource(TbResource resource);

    TbResource saveResource(TbResource resource, boolean doValidate);

    TbResource findResourceByTenantIdAndKey(TenantId tenantId, ResourceType resourceType, String resourceKey);

    TbResource findResourceById(TenantId tenantId, TbResourceId resourceId);

    byte[] getResourceData(TenantId tenantId, TbResourceId resourceId);

    ResourceExportData exportResource(TbResourceInfo resourceInfo);

    List<ResourceExportData> exportResources(TenantId tenantId, Collection<TbResourceInfo> resources);

    TbResource toResource(TenantId tenantId, ResourceExportData exportData);

    void importResources(TenantId tenantId, CustomerId customerId, List<ResourceExportData> resources);

    TbResourceInfo findResourceInfoById(TenantId tenantId, TbResourceId resourceId);

    TbResourceInfo findResourceInfoByTenantIdAndKey(TenantId tenantId, ResourceType resourceType, String resourceKey);

    PageData<TbResource> findAllTenantResources(TenantId tenantId, PageLink pageLink);

    ListenableFuture<TbResourceInfo> findResourceInfoByIdAsync(TenantId tenantId, TbResourceId resourceId);

    PageData<TbResourceInfo> findAllTenantResourcesByTenantId(TbResourceInfoFilter filter, PageLink pageLink);

    PageData<TbResourceInfo> findTenantResourcesByTenantId(TbResourceInfoFilter filter, PageLink pageLink);

    List<TbResource> findTenantResourcesByResourceTypeAndObjectIds(TenantId tenantId, ResourceType lwm2mModel, String[] objectIds);

    PageData<TbResource> findTenantResourcesByResourceTypeAndPageLink(TenantId tenantId, ResourceType lwm2mModel, PageLink pageLink);

    TbResourceDeleteResult deleteResource(TenantId tenantId, TbResourceId resourceId, boolean force);

    void deleteResourcesByTenantId(TenantId tenantId);

    long sumDataSizeByTenantId(TenantId tenantId);

    String calculateEtag(byte[] data);

    TbResourceInfo findSystemOrTenantResourceByEtag(TenantId tenantId, ResourceType resourceType, String etag);

    boolean updateResourcesUsage(TenantId tenantId, Dashboard dashboard);

    boolean updateResourcesUsage(TenantId tenantId, WidgetTypeDetails widgetTypeDetails);

    Collection<TbResourceInfo> getUsedResources(TenantId tenantId, Dashboard dashboard);

    Collection<TbResourceInfo> getUsedResources(TenantId tenantId, WidgetTypeDetails widgetTypeDetails);

    TbResource createOrUpdateSystemResource(ResourceType resourceType, ResourceSubType resourceSubType, String resourceKey, byte[] data);

}
