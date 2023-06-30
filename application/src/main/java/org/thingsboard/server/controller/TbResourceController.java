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
package org.thingsboard.server.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.TbResourceInfoFilter;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.TbResourceId;
import org.thingsboard.server.common.data.lwm2m.LwM2mObject;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.resource.TbResourceService;

import java.util.Base64;
import java.util.List;

import static org.thingsboard.server.controller.ControllerConstants.AVAILABLE_FOR_ANY_AUTHORIZED_USER;
import static org.thingsboard.server.controller.ControllerConstants.LWM2M_OBJECT_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_DATA_PARAMETERS;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_NUMBER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_SIZE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.RESOURCE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.RESOURCE_ID_PARAM_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.RESOURCE_INFO_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.RESOURCE_TEXT_SEARCH_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.RESOURCE_TYPE;
import static org.thingsboard.server.controller.ControllerConstants.SORT_ORDER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_PROPERTY_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH;
import static org.thingsboard.server.controller.ControllerConstants.TENANT_AUTHORITY_PARAGRAPH;
import static org.thingsboard.server.controller.ControllerConstants.UUID_WIKI_LINK;

@Slf4j
@RestController
@TbCoreComponent
@RequestMapping("/api")
@RequiredArgsConstructor
public class TbResourceController extends BaseController {

    private static final String DOWNLOAD_RESOURCE_IF_NOT_CHANGED = "Download Resource based on the provided Resource Id or return 304 status code if resource was not changed.";
    private final TbResourceService tbResourceService;

    public static final String RESOURCE_ID = "resourceId";

    @ApiOperation(value = "Download Resource (downloadResource)", notes = "Download Resource based on the provided Resource Id." + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/{resourceId}/download", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                                                                 @PathVariable(RESOURCE_ID) String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
        TbResource tbResource = checkResourceId(resourceId, Operation.READ);

        ByteArrayResource resource = new ByteArrayResource(Base64.getDecoder().decode(tbResource.getData().getBytes()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + tbResource.getFileName())
                .header("x-filename", tbResource.getFileName())
                .contentLength(resource.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @ApiOperation(value = "Download LWM2M Resource (downloadLwm2mResourceIfChanged)", notes = DOWNLOAD_RESOURCE_IF_NOT_CHANGED + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/lwm2m/{resourceId}/download", method = RequestMethod.GET, produces = "application/xml")
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadLwm2mResourceIfChanged(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                                                                               @PathVariable(RESOURCE_ID) String strResourceId,
                                                                                               @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String etag) throws ThingsboardException {
        return downloadResourceIfChanged(ResourceType.LWM2M_MODEL, strResourceId, etag);
    }

    @ApiOperation(value = "Download PKCS_12 Resource (downloadPkcs12ResourceIfChanged)", notes = DOWNLOAD_RESOURCE_IF_NOT_CHANGED + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/pkcs12/{resourceId}/download", method = RequestMethod.GET, produces = "application/x-pkcs12")
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadPkcs12ResourceIfChanged(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                                                                                @PathVariable(RESOURCE_ID) String strResourceId,
                                                                                                @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String etag) throws ThingsboardException {
        return downloadResourceIfChanged(ResourceType.PKCS_12, strResourceId, etag);
    }

    @ApiOperation(value = "Download JKS Resource (downloadJksResourceIfChanged)",
            notes = DOWNLOAD_RESOURCE_IF_NOT_CHANGED + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/jks/{resourceId}/download", method = RequestMethod.GET, produces = "application/x-java-keystore")
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadJksResourceIfChanged(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                                                                             @PathVariable(RESOURCE_ID) String strResourceId,
                                                                                             @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String etag) throws ThingsboardException {
        return downloadResourceIfChanged(ResourceType.JKS, strResourceId, etag);
    }

    @ApiOperation(value = "Download JS Resource (downloadJsResourceIfChanged)", notes = DOWNLOAD_RESOURCE_IF_NOT_CHANGED + AVAILABLE_FOR_ANY_AUTHORIZED_USER)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/resource/js/{resourceId}/download", method = RequestMethod.GET, produces = "application/javascript")
    @ResponseBody
    public ResponseEntity<org.springframework.core.io.Resource> downloadJsResourceIfChanged(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                                                                            @PathVariable(RESOURCE_ID) String strResourceId,
                                                                                            @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String etag) throws ThingsboardException {
        return downloadResourceIfChanged(ResourceType.JS_MODULE, strResourceId, etag);
    }

    @ApiOperation(value = "Get Resource Info (getResourceInfoById)",
            notes = "Fetch the Resource Info object based on the provided Resource Id. " +
                    RESOURCE_INFO_DESCRIPTION + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH,
            responses = @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)))
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/info/{resourceId}", method = RequestMethod.GET)
    @ResponseBody
    public TbResourceInfo getResourceInfoById(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                              @PathVariable(RESOURCE_ID) String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
        return checkResourceInfoId(resourceId, Operation.READ);
    }

    @ApiOperation(value = "Get Resource (getResourceById)",
            notes = "Fetch the Resource object based on the provided Resource Id. " +
                    RESOURCE_DESCRIPTION + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH,
            responses = @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)))
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/{resourceId}", method = RequestMethod.GET)
    @ResponseBody
    public TbResource getResourceById(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                                      @PathVariable(RESOURCE_ID) String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
        return checkResourceId(resourceId, Operation.READ);
    }

    @ApiOperation(value = "Create Or Update Resource (saveResource)",
            notes = "Create or update the Resource. When creating the Resource, platform generates Resource id as " + UUID_WIKI_LINK +
                    "The newly created Resource id will be present in the response. " +
                    "Specify existing Resource id to update the Resource. " +
                    "Referencing non-existing Resource Id will cause 'Not Found' error. " +
                    "\n\nResource combination of the title with the key is unique in the scope of tenant. " +
                    "Remove 'id', 'tenantId' from the request body example (below) to create new Resource entity." +
                    SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH,
            responses = @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)))
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource", method = RequestMethod.POST)
    @ResponseBody
    public TbResource saveResource(@Parameter(description = "A JSON value representing the Resource.")
                                   @RequestBody TbResource resource) throws Exception {
        resource.setTenantId(getTenantId());
        checkEntity(resource.getId(), resource, Resource.TB_RESOURCE, null);
        return tbResourceService.save(resource, getCurrentUser());
    }

    @ApiOperation(value = "Get Resource Infos (getResources)",
            notes = "Returns a page of Resource Info objects owned by tenant or sysadmin. " +
                    PAGE_DATA_PARAMETERS + RESOURCE_INFO_DESCRIPTION + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH,
            responses = @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)))
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource", method = RequestMethod.GET)
    @ResponseBody
    public PageData<TbResourceInfo> getResources(@Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                 @RequestParam int pageSize,
                                                 @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                 @RequestParam int page,
                                                 @Parameter(description = RESOURCE_TYPE, schema = @Schema(allowableValues = {"LWM2M_MODEL", "JKS", "PKCS_12", "JS_MODULE"}))
                                                 @RequestParam(required = false) String resourceType,
                                                 @Parameter(description = RESOURCE_TEXT_SEARCH_DESCRIPTION)
                                                 @RequestParam(required = false) String textSearch,
                                                 @Parameter(description = SORT_PROPERTY_DESCRIPTION, schema = @Schema(allowableValues = {"createdTime", "title", "resourceType", "tenantId"}))
                                                 @RequestParam(required = false) String sortProperty,
                                                 @Parameter(description = SORT_ORDER_DESCRIPTION, schema = @Schema(allowableValues = {"ASC", "DESC"}))
                                                 @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        TbResourceInfoFilter.TbResourceInfoFilterBuilder filter = TbResourceInfoFilter.builder();
        filter.tenantId(getTenantId());
        if (StringUtils.isNotEmpty(resourceType)) {
            filter.resourceType(ResourceType.valueOf(resourceType));
        }
        if (Authority.SYS_ADMIN.equals(getCurrentUser().getAuthority())) {
            return checkNotNull(resourceService.findTenantResourcesByTenantId(filter.build(), pageLink));
        } else {
            return checkNotNull(resourceService.findAllTenantResourcesByTenantId(filter.build(), pageLink));
        }
    }

    @ApiOperation(value = "Get LwM2M Objects (getLwm2mListObjectsPage)",
            notes = "Returns a page of LwM2M objects parsed from Resources with type 'LWM2M_MODEL' owned by tenant or sysadmin. " +
                    PAGE_DATA_PARAMETERS + LWM2M_OBJECT_DESCRIPTION + TENANT_AUTHORITY_PARAGRAPH,
            responses = @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)))
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/resource/lwm2m/page", method = RequestMethod.GET)
    @ResponseBody
    public List<LwM2mObject> getLwm2mListObjectsPage(@Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                     @RequestParam int pageSize,
                                                     @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                     @RequestParam int page,
                                                     @Parameter(description = RESOURCE_TEXT_SEARCH_DESCRIPTION)
                                                     @RequestParam(required = false) String textSearch,
                                                     @Parameter(description = SORT_PROPERTY_DESCRIPTION, schema = @Schema(allowableValues = {"id", "name"}))
                                                     @RequestParam(required = false) String sortProperty,
                                                     @Parameter(description = SORT_ORDER_DESCRIPTION, schema = @Schema(allowableValues = {"ASC", "DESC"}))
                                                     @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = new PageLink(pageSize, page, textSearch);
        return checkNotNull(resourceService.findLwM2mObjectPage(getTenantId(), sortProperty, sortOrder, pageLink));
    }

    @ApiOperation(value = "Get LwM2M Objects (getLwm2mListObjects)",
            notes = "Returns a page of LwM2M objects parsed from Resources with type 'LWM2M_MODEL' owned by tenant or sysadmin. " +
                    "You can specify parameters to filter the results. " + LWM2M_OBJECT_DESCRIPTION + TENANT_AUTHORITY_PARAGRAPH,
            responses = @ApiResponse(content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)))
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/resource/lwm2m", method = RequestMethod.GET)
    @ResponseBody
    public List<LwM2mObject> getLwm2mListObjects(@Parameter(description = SORT_ORDER_DESCRIPTION, schema = @Schema(allowableValues = {"ASC", "DESC"}, required = true))
                                                 @RequestParam String sortOrder,
                                                 @Parameter(description = SORT_PROPERTY_DESCRIPTION, schema = @Schema(allowableValues = {"id", "name"}, required = true))
                                                 @RequestParam String sortProperty,
                                                 @Parameter(description = "LwM2M Object ids.", required = true)
                                                 @RequestParam(required = false) String[] objectIds) throws ThingsboardException {
        return checkNotNull(resourceService.findLwM2mObject(getTenantId(), sortOrder, sortProperty, objectIds));
    }

    @ApiOperation(value = "Delete Resource (deleteResource)",
            notes = "Deletes the Resource. Referencing non-existing Resource Id will cause an error." + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @RequestMapping(value = "/resource/{resourceId}", method = RequestMethod.DELETE)
    @ResponseBody
    public void deleteResource(@Parameter(description = RESOURCE_ID_PARAM_DESCRIPTION)
                               @PathVariable("resourceId") String strResourceId) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));
        TbResource tbResource = checkResourceId(resourceId, Operation.DELETE);
        tbResourceService.delete(tbResource, getCurrentUser());
    }

    private ResponseEntity<org.springframework.core.io.Resource> downloadResourceIfChanged(ResourceType type, String strResourceId, String etag) throws ThingsboardException {
        checkParameter(RESOURCE_ID, strResourceId);
        TbResourceId resourceId = new TbResourceId(toUUID(strResourceId));

        if (etag != null) {
            TbResourceInfo tbResourceInfo = checkResourceInfoId(resourceId, Operation.READ);
            if (etag.equals(tbResourceInfo.getEtag())) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(tbResourceInfo.getEtag())
                        .build();
            }
        }

        TbResource tbResource = checkResourceId(resourceId, Operation.READ);
        ByteArrayResource resource = new ByteArrayResource(Base64.getDecoder().decode(tbResource.getData().getBytes()));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + tbResource.getFileName())
                .header("x-filename", tbResource.getFileName())
                .contentLength(resource.contentLength())
                .header("Content-Type", type.getMediaType())
                .cacheControl(CacheControl.noCache())
                .eTag(tbResource.getEtag())
                .body(resource);
    }
}