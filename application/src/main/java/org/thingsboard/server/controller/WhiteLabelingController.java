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
package org.thingsboard.server.controller;

import com.google.common.base.Charsets;
import io.bit3.jsass.Compiler;
import io.bit3.jsass.Options;
import io.bit3.jsass.Output;
import io.bit3.jsass.OutputStyle;
import io.bit3.jsass.importer.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.permission.Operation;
import org.thingsboard.server.common.data.permission.Resource;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.Palette;
import org.thingsboard.server.common.data.wl.PaletteSettings;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@TbCoreComponent
@RequestMapping("/api")
public class WhiteLabelingController extends BaseController {

    private static final String SCSS_EXTENSION = ".scss";
    private static final String SCSS_CLASSPATH_PATTERN = "classpath:scss/*.scss";
    private static final String APP_THEME_SCSS = "app-theme.scss";
    private static final String LOGIN_THEME_SCSS = "login-theme.scss";

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    private Map<String, Import> scssImportMap;
    private String scssAppTheme;
    private String scssLoginTheme;
    private Options saasOptions;
    private Compiler saasCompiler;

    @PostConstruct
    public void init() throws Exception {
        this.initSaasCompiler();
    }

    private void initSaasCompiler() throws Exception {
        this.scssImportMap = new HashMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        org.springframework.core.io.Resource[] scssResources = resolver.getResources(SCSS_CLASSPATH_PATTERN);
        for (org.springframework.core.io.Resource scssResource : scssResources) {
            String scssContent = StreamUtils.copyToString(scssResource.getInputStream(), Charsets.UTF_8);
            String fileName = scssResource.getFilename();
            if (APP_THEME_SCSS.equals(fileName)) {
                this.scssAppTheme = scssContent;
            } else if (LOGIN_THEME_SCSS.equals(fileName)) {
                this.scssLoginTheme = scssContent;
            } else if (fileName != null) {
                URI scssFileUri = scssResource.getURI();
                final Import scssImport = new Import(scssFileUri, scssFileUri, scssContent);
                String path = fileName.substring(0, fileName.length() - SCSS_EXTENSION.length());
                scssImportMap.put(path, scssImport);
            }
        }
        this.saasCompiler = new Compiler();
        this.saasOptions = new Options();
        this.saasOptions.setImporters(Collections.singleton(
                (url, previous) -> Collections.singletonList(this.scssImportMap.get(url))
        ));
        this.saasOptions.setOutputStyle(OutputStyle.COMPRESSED);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/whiteLabelParams", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public WhiteLabelingParams getWhiteLabelParams(
            @RequestParam(required = false) String logoImageChecksum,
            @RequestParam(required = false) String faviconChecksum) throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            WhiteLabelingParams whiteLabelingParams = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                whiteLabelingParams = whiteLabelingService.getMergedSystemWhiteLabelingParams(TenantId.SYS_TENANT_ID, logoImageChecksum, faviconChecksum);
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                whiteLabelingParams = whiteLabelingService.getMergedTenantWhiteLabelingParams(getCurrentUser().getTenantId(),
                        logoImageChecksum, faviconChecksum);
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                whiteLabelingParams = whiteLabelingService.getMergedCustomerWhiteLabelingParams(getCurrentUser().getTenantId(),
                        getCurrentUser().getCustomerId(), logoImageChecksum, faviconChecksum);
            }
            return whiteLabelingParams;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @RequestMapping(value = "/noauth/whiteLabel/loginWhiteLabelParams", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public LoginWhiteLabelingParams getLoginWhiteLabelParams(
            @RequestParam(required = false) String logoImageChecksum,
            @RequestParam(required = false) String faviconChecksum,
            HttpServletRequest request) throws ThingsboardException {
        try {
            return whiteLabelingService.getMergedLoginWhiteLabelingParams(TenantId.SYS_TENANT_ID, request.getServerName(), logoImageChecksum, faviconChecksum);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/currentWhiteLabelParams", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public WhiteLabelingParams getCurrentWhiteLabelParams() throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            checkWhiteLabelingPermissions(Operation.READ);
            WhiteLabelingParams whiteLabelingParams = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                whiteLabelingParams = whiteLabelingService.getSystemWhiteLabelingParams(TenantId.SYS_TENANT_ID);
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                whiteLabelingParams = whiteLabelingService.getTenantWhiteLabelingParams(getCurrentUser().getTenantId()).get();
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                whiteLabelingParams = whiteLabelingService.getCustomerWhiteLabelingParams(getTenantId(), getCurrentUser().getCustomerId()).get();
            }
            return whiteLabelingParams;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/currentLoginWhiteLabelParams", method = RequestMethod.GET, produces = "application/json")
    @ResponseBody
    public LoginWhiteLabelingParams getCurrentLoginWhiteLabelParams() throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            checkWhiteLabelingPermissions(Operation.READ);
            LoginWhiteLabelingParams loginWhiteLabelingParams = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                loginWhiteLabelingParams = whiteLabelingService.getSystemLoginWhiteLabelingParams(TenantId.SYS_TENANT_ID);
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                loginWhiteLabelingParams = whiteLabelingService.getTenantLoginWhiteLabelingParams(getCurrentUser().getTenantId());
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                loginWhiteLabelingParams = whiteLabelingService.getCustomerLoginWhiteLabelingParams(getTenantId(), getCurrentUser().getCustomerId());
            }
            return loginWhiteLabelingParams;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/whiteLabelParams", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public WhiteLabelingParams saveWhiteLabelParams(@RequestBody WhiteLabelingParams whiteLabelingParams) throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            checkWhiteLabelingPermissions(Operation.WRITE);
            WhiteLabelingParams savedWhiteLabelingParams = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                savedWhiteLabelingParams = whiteLabelingService.saveSystemWhiteLabelingParams(whiteLabelingParams);
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                savedWhiteLabelingParams = whiteLabelingService.saveTenantWhiteLabelingParams(getCurrentUser().getTenantId(), whiteLabelingParams).get();
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                savedWhiteLabelingParams = whiteLabelingService.saveCustomerWhiteLabelingParams(getTenantId(), getCurrentUser().getCustomerId(), whiteLabelingParams).get();
            }
            return savedWhiteLabelingParams;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/loginWhiteLabelParams", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public LoginWhiteLabelingParams saveLoginWhiteLabelParams(@RequestBody LoginWhiteLabelingParams loginWhiteLabelingParams) throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            checkWhiteLabelingPermissions(Operation.WRITE);
            LoginWhiteLabelingParams savedLoginWhiteLabelingParams = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                savedLoginWhiteLabelingParams = whiteLabelingService.saveSystemLoginWhiteLabelingParams(loginWhiteLabelingParams);
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                savedLoginWhiteLabelingParams = whiteLabelingService.saveTenantLoginWhiteLabelingParams(getCurrentUser().getTenantId(), loginWhiteLabelingParams);
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                savedLoginWhiteLabelingParams = whiteLabelingService.saveCustomerLoginWhiteLabelingParams(getTenantId(), getCurrentUser().getCustomerId(), loginWhiteLabelingParams);
            }
            return savedLoginWhiteLabelingParams;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/previewWhiteLabelParams", method = RequestMethod.POST)
    @ResponseStatus(value = HttpStatus.OK)
    public WhiteLabelingParams previewWhiteLabelParams(@RequestBody WhiteLabelingParams whiteLabelingParams) throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            checkWhiteLabelingPermissions(Operation.WRITE);
            WhiteLabelingParams mergedWhiteLabelingParams = null;
            if (Authority.SYS_ADMIN.equals(authority)) {
                mergedWhiteLabelingParams = whiteLabelingService.mergeSystemWhiteLabelingParams(whiteLabelingParams);
            } else if (Authority.TENANT_ADMIN.equals(authority)) {
                mergedWhiteLabelingParams = whiteLabelingService.mergeTenantWhiteLabelingParams(getTenantId(), whiteLabelingParams);
            } else if (Authority.CUSTOMER_USER.equals(authority)) {
                mergedWhiteLabelingParams = whiteLabelingService.mergeCustomerWhiteLabelingParams(getCurrentUser().getTenantId(), whiteLabelingParams);
            }
            return mergedWhiteLabelingParams;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/isWhiteLabelingAllowed", method = RequestMethod.GET)
    @ResponseBody
    public Boolean isWhiteLabelingAllowed() throws ThingsboardException {
        try {
            Authority authority = getCurrentUser().getAuthority();
            EntityId entityId;
            if (Authority.TENANT_ADMIN.equals(authority)) {
                entityId = getCurrentUser().getTenantId();
            } else {
                entityId = getCurrentUser().getCustomerId();
            }
            return whiteLabelingService.isWhiteLabelingAllowed(getTenantId(), entityId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @RequestMapping(value = "/whiteLabel/isCustomerWhiteLabelingAllowed", method = RequestMethod.GET)
    @ResponseBody
    public Boolean isCustomerWhiteLabelingAllowed() throws ThingsboardException {
        try {
            return whiteLabelingService.isCustomerWhiteLabelingAllowed(getCurrentUser().getTenantId());
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @RequestMapping(value = "/noauth/whiteLabel/loginThemeCss", method = RequestMethod.POST, produces = "plain/text")
    @ResponseStatus(value = HttpStatus.OK)
    public String getLoginThemeCss(@RequestBody PaletteSettings paletteSettings,
                                   @RequestParam(name = "darkForeground", required = false) boolean darkForeground) throws ThingsboardException {
        try {
            return this.generateThemeCss(paletteSettings, true, darkForeground);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @RequestMapping(value = "/whiteLabel/appThemeCss", method = RequestMethod.POST, produces = "plain/text")
    @ResponseStatus(value = HttpStatus.OK)
    public String getAppThemeCss(@RequestBody PaletteSettings paletteSettings) throws ThingsboardException {
        try {
            return this.generateThemeCss(paletteSettings, false, false);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    private String generateThemeCss(PaletteSettings paletteSettings,
                                    boolean loginTheme,
                                    boolean darkForeground) throws Exception {
        String primaryPaletteName = getPaletteName(paletteSettings.getPrimaryPalette(), true);
        String primaryColors = getPaletteColors(paletteSettings.getPrimaryPalette());
        String accentPaletteName = getPaletteName(paletteSettings.getAccentPalette(), false);
        String accentColors = getPaletteColors(paletteSettings.getAccentPalette());
        String targetTheme = loginTheme ? this.scssLoginTheme : this.scssAppTheme;

        targetTheme = targetTheme.replaceAll("##primary-palette##", primaryPaletteName);
        targetTheme = targetTheme.replaceAll("##primary-colors##", primaryColors);
        targetTheme = targetTheme.replaceAll("##accent-palette##", accentPaletteName);
        targetTheme = targetTheme.replaceAll("##accent-colors##", accentColors);

        if (loginTheme) {
            targetTheme = targetTheme.replaceAll("##dark-foreground##", darkForeground ? "true" : "false");
        }
        Output output = this.saasCompiler.compileString(targetTheme, this.saasOptions);
        return output.getCss();
    }

    private String getPaletteName(Palette palette, boolean primary) {
        if (palette == null) {
            return primary ? "tb-primary" : "tb-accent";
        }
        if (palette.getType().equals("custom")) {
            return palette.getExtendsPalette();
        } else {
            return palette.getType();
        }
    }

    private String getPaletteColors(Palette palette) {
        if (palette != null && palette.getColors() != null && !palette.getColors().isEmpty()) {
            List<String> colorsList = new ArrayList<>();
            palette.getColors().forEach(
                    (hue, hex) -> colorsList.add(String.format("%s: %s", hue, hex))
            );
            return "\n"+String.join(",\n", colorsList)+"\n";
        } else {
            return "";
        }
    }

    private void checkWhiteLabelingPermissions(Operation operation) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.WHITE_LABELING, operation);
    }
}
