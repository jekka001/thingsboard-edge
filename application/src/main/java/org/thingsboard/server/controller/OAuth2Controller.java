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
package org.thingsboard.server.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.OAuth2ClientId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.oauth2.OAuth2Client;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientInfo;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientLoginInfo;
import org.thingsboard.server.common.data.oauth2.PlatformType;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.oauth2.OAuth2Configuration;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.oauth2client.TbOauth2ClientService;
import org.thingsboard.server.utils.MiscUtils;

import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import static org.thingsboard.server.common.data.permission.Resource.MOBILE_APP;
import static org.thingsboard.server.common.data.permission.Resource.OAUTH2_CLIENT;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_NUMBER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.PAGE_SIZE_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_ORDER_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SORT_PROPERTY_DESCRIPTION;
import static org.thingsboard.server.controller.ControllerConstants.SYSTEM_AUTHORITY_PARAGRAPH;
import static org.thingsboard.server.controller.ControllerConstants.SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH;

@RestController
@TbCoreComponent
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller extends BaseController {

    private final OAuth2Configuration oAuth2Configuration;

    private final TbOauth2ClientService tbOauth2ClientService;


    @ApiOperation(value = "Get OAuth2 clients (getOAuth2Clients)", notes = "Get the list of OAuth2 clients " +
            "to log in with, available for such domain scheme (HTTP or HTTPS) (if x-forwarded-proto request header is present - " +
            "the scheme is known from it) and domain name and port (port may be known from x-forwarded-port header)")
    @PostMapping(value = "/noauth/oauth2Clients")
    public List<OAuth2ClientLoginInfo> getOAuth2Clients(HttpServletRequest request,
                                                        @Parameter(description = "Mobile application package name, to find OAuth2 clients " +
                                                                "where there is configured mobile application with such package name")
                                                        @RequestParam(required = false) String pkgName,
                                                        @Parameter(description = "Platform type to search OAuth2 clients for which " +
                                                                "the usage with this platform type is allowed in the settings. " +
                                                                "If platform type is not one of allowable values - it will just be ignored",
                                                                schema = @Schema(allowableValues = {"WEB", "ANDROID", "IOS"}))
                                                        @RequestParam(required = false) String platform) {
        if (log.isDebugEnabled()) {
            log.debug("Executing getOAuth2Clients: [{}][{}][{}]", request.getScheme(), request.getServerName(), request.getServerPort());
            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String header = headerNames.nextElement();
                log.debug("Header: {} {}", header, request.getHeader(header));
            }
        }
        PlatformType platformType = null;
        if (StringUtils.isNotEmpty(platform)) {
            platformType = PlatformType.valueOf(platform);
        }
        if (StringUtils.isNotEmpty(pkgName)) {
            return oAuth2ClientService.findOAuth2ClientLoginInfosByMobilePkgNameAndPlatformType(pkgName, platformType);
        } else {
            return oAuth2ClientService.findOAuth2ClientLoginInfosByDomainName(MiscUtils.getDomainNameAndPort(request));
        }
    }

    @ApiOperation(value = "Save OAuth2 Client (saveOAuth2Client)", notes = SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @PostMapping(value = "/oauth2/client")
    public OAuth2Client saveOAuth2Client(@RequestBody @Valid OAuth2Client oAuth2Client) throws Exception {
        TenantId tenantId = getTenantId();
        oAuth2Client.setTenantId(tenantId);
        checkEntity(oAuth2Client.getId(), oAuth2Client, OAUTH2_CLIENT);
        return tbOauth2ClientService.save(oAuth2Client, getCurrentUser());
    }

    @ApiOperation(value = "Get OAuth2 Client infos (findTenantOAuth2ClientInfos)", notes = SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @GetMapping(value = "/oauth2/client/infos")
    public PageData<OAuth2ClientInfo> findTenantOAuth2ClientInfos(@Parameter(description = PAGE_SIZE_DESCRIPTION, required = true)
                                                                  @RequestParam int pageSize,
                                                                  @Parameter(description = PAGE_NUMBER_DESCRIPTION, required = true)
                                                                  @RequestParam int page,
                                                                  @Parameter(description = "Case-insensitive 'substring' filter based on client's title")
                                                                  @RequestParam(required = false) String textSearch,
                                                                  @Parameter(description = SORT_PROPERTY_DESCRIPTION)
                                                                  @RequestParam(required = false) String sortProperty,
                                                                  @Parameter(description = SORT_ORDER_DESCRIPTION)
                                                                  @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), OAUTH2_CLIENT, Operation.READ);
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return oAuth2ClientService.findOAuth2ClientInfosByTenantId(getTenantId(), pageLink);
    }

    @ApiOperation(value = "Get OAuth2 Client infos By Ids (findTenantOAuth2ClientInfosByIds)",
            notes = "Fetch OAuth2 Client info objects based on the provided ids. " + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @GetMapping(value = "/oauth2/client/infos", params = {"clientIds"})
    public List<OAuth2ClientInfo> findTenantOAuth2ClientInfosByIds(
            @Parameter(description = "A list of oauth2 ids, separated by comma ','", array = @ArraySchema(schema = @Schema(type = "string")), required = true)
            @RequestParam("clientIds") UUID[] clientIds) throws ThingsboardException {
        List<OAuth2ClientId> oAuth2ClientIds = getOAuth2ClientIds(clientIds);
        return oAuth2ClientService.findOAuth2ClientInfosByIds(getTenantId(), oAuth2ClientIds);
    }

    @ApiOperation(value = "Get OAuth2 Client by id (getOAuth2ClientById)", notes = SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @GetMapping(value = "/oauth2/client/{id}")
    public OAuth2Client getOAuth2ClientById(@PathVariable UUID id) throws ThingsboardException {
        OAuth2ClientId oAuth2ClientId = new OAuth2ClientId(id);
        return checkEntityId(oAuth2ClientId, oAuth2ClientService::findOAuth2ClientById, Operation.READ);
    }

    @ApiOperation(value = "Delete oauth2 client (deleteOauth2Client)",
            notes = "Deletes the oauth2 client. Referencing non-existing oauth2 client Id will cause an error." + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @DeleteMapping(value = "/oauth2/client/{id}")
    public void deleteOauth2Client(@PathVariable UUID id) throws Exception {
        OAuth2ClientId oAuth2ClientId = new OAuth2ClientId(id);
        OAuth2Client oAuth2Client = checkOauth2ClientId(oAuth2ClientId, Operation.DELETE);
        tbOauth2ClientService.delete(oAuth2Client, getCurrentUser());
    }

    @ApiOperation(value = "Get OAuth2 log in processing URL (getLoginProcessingUrl)", notes = "Returns the URL enclosed in " +
            "double quotes. After successful authentication with OAuth2 provider, it makes a redirect to this path so that the platform can do " +
            "further log in processing. This URL may be configured as 'security.oauth2.loginProcessingUrl' property in yml configuration file, or " +
            "as 'SECURITY_OAUTH2_LOGIN_PROCESSING_URL' env variable. By default it is '/login/oauth2/code/'" + SYSTEM_OR_TENANT_AUTHORITY_PARAGRAPH)
    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @GetMapping(value = "/oauth2/loginProcessingUrl")
    public String getLoginProcessingUrl() {
        return "\"" + oAuth2Configuration.getLoginProcessingUrl() + "\"";
    }

}
