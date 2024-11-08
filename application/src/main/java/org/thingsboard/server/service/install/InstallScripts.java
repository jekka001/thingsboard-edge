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
package org.thingsboard.server.service.install;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ImageDescriptor;
import org.thingsboard.server.common.data.ResourceSubType;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientRegistrationTemplate;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.oauth2.OAuth2ConfigTemplateService;
import org.thingsboard.server.dao.resource.ImageService;
import org.thingsboard.server.dao.resource.ResourceService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.util.ImageUtils;
import org.thingsboard.server.dao.widget.WidgetTypeService;
import org.thingsboard.server.dao.widget.WidgetsBundleService;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.exception.DataValidationException;
import org.thingsboard.server.service.install.update.ImagesUpdater;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.thingsboard.server.utils.LwM2mObjectModelUtils.toLwm2mResource;

/**
 * Created by ashvayka on 18.04.18.
 */
@Component
@Slf4j
public class InstallScripts {

    public static final String APP_DIR = "application";
    public static final String SRC_DIR = "src";
    public static final String MAIN_DIR = "main";
    public static final String DATA_DIR = "data";
    public static final String JSON_DIR = "json";
    public static final String SYSTEM_DIR = "system";
    public static final String TENANT_DIR = "tenant";
    public static final String EDGE_DIR = "edge";
    public static final String DEVICE_PROFILE_DIR = "device_profile";
    public static final String DEMO_DIR = "demo";
    public static final String RULE_CHAINS_DIR = "rule_chains";
    public static final String ROOT_RULE_CHAIN_DIR = "root_rule_chain";
    public static final String ROOT_RULE_CHAIN_JSON = "root_rule_chain.json";
    public static final String WIDGET_TYPES_DIR = "widget_types";
    public static final String WIDGET_BUNDLES_DIR = "widget_bundles";
    public static final String SCADA_SYMBOLS_DIR = "scada_symbols";
    public static final String OAUTH2_CONFIG_TEMPLATES_DIR = "oauth2_config_templates";
    public static final String DASHBOARDS_DIR = "dashboards";
    public static final String MAIL_TEMPLATES_DIR = "mail_templates";
    public static final String MAIL_TEMPLATES_JSON = "mail_templates.json";
    public static final String MODELS_LWM2M_DIR = "lwm2m-registry";
    public static final String SOLUTIONS_DIR = "solutions";
    public static final String RESOURCES_DIR = "resources";

    public static final String JSON_EXT = ".json";
    public static final String SVG_EXT = ".svg";
    public static final String XML_EXT = ".xml";

    private static final Pattern velocityVarPattern = Pattern.compile("\\$([a-zA-Z]+)");
    private static final String velocityVarToFreeMakerReplacementPattern = "\\${$1}";

    @Value("${install.data_dir:}")
    private String dataDir;

    @Autowired
    private RuleChainService ruleChainService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private WidgetTypeService widgetTypeService;

    @Autowired
    private WidgetsBundleService widgetsBundleService;

    @Autowired
    private WhiteLabelingService whiteLabelingService;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private OAuth2ConfigTemplateService oAuth2TemplateService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ImagesUpdater imagesUpdater;
    @Getter @Setter
    private boolean updateImages = false;

    @Autowired
    private ImageService imageService;

    Path getTenantRuleChainsDir() {
        return Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, RULE_CHAINS_DIR);
    }

    public Path getRootTenantRuleChainFile() {
        return Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, ROOT_RULE_CHAIN_DIR, ROOT_RULE_CHAIN_JSON);
    }

    Path getDeviceProfileDefaultRuleChainTemplateFilePath() {
        return Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, DEVICE_PROFILE_DIR, "rule_chain_template.json");
    }

    Path getEdgeRuleChainsDir() {
        return Paths.get(getDataDir(), JSON_DIR, EDGE_DIR, RULE_CHAINS_DIR);
    }

    public String getDataDir() {
        if (!StringUtils.isEmpty(dataDir)) {
            if (!Paths.get(this.dataDir).toFile().isDirectory()) {
                throw new RuntimeException("'install.data_dir' property value is not a valid directory!");
            }
            return dataDir;
        } else {
            String workDir = System.getProperty("user.dir");
            if (workDir.endsWith("application")) {
                return Paths.get(workDir, SRC_DIR, MAIN_DIR, DATA_DIR).toString();
            } else {
                Path dataDirPath = Paths.get(workDir, APP_DIR, SRC_DIR, MAIN_DIR, DATA_DIR);
                if (Files.exists(dataDirPath)) {
                    return dataDirPath.toString();
                } else {
                    throw new RuntimeException("Not valid working directory: " + workDir + ". Please use either root project directory, application module directory or specify valid \"install.data_dir\" ENV variable to avoid automatic data directory lookup!");
                }
            }
        }
    }

    public void createDefaultRuleChains(TenantId tenantId) {
        Map<String, RuleChainId> ruleChainIdMap = loadAdditionalTenantRuleChains(tenantId, getTenantRuleChainsDir());
        Path rootRuleChainFile = getRootTenantRuleChainFile();
        loadRootRuleChain(tenantId, ruleChainIdMap, rootRuleChainFile);
    }

    private RuleChain loadRuleChain(Path path, JsonNode ruleChainJson, TenantId tenantId, String newRuleChainName) {
        try {
            RuleChain ruleChain = JacksonUtil.treeToValue(ruleChainJson.get("ruleChain"), RuleChain.class);
            RuleChainMetaData ruleChainMetaData = JacksonUtil.treeToValue(ruleChainJson.get("metadata"), RuleChainMetaData.class);

            ruleChain.setTenantId(tenantId);
            if (!StringUtils.isEmpty(newRuleChainName)) {
                ruleChain.setName(newRuleChainName);
            }
            ruleChain = ruleChainService.saveRuleChain(ruleChain, false);

            ruleChainMetaData.setRuleChainId(ruleChain.getId());
            ruleChainService.saveRuleChainMetaData(TenantId.SYS_TENANT_ID, ruleChainMetaData, Function.identity(), false);
            return ruleChain;
        } catch (Exception e) {
            log.error("Unable to load rule chain from json: [{}]", path.toString());
            throw new RuntimeException("Unable to load rule chain from json", e);
        }
    }

    List<Path> findRuleChainsFromPath(Path ruleChainsPath) {
        try (Stream<Path> files = listDir(ruleChainsPath).filter(path -> path.toString().endsWith(InstallScripts.JSON_EXT))) {
            return files.toList();
        }
    }

    public RuleChain createDefaultRuleChain(TenantId tenantId, String ruleChainName) {
        return createRuleChainFromFile(tenantId, getDeviceProfileDefaultRuleChainTemplateFilePath(), ruleChainName);
    }

    public RuleChain createRuleChainFromFile(TenantId tenantId, Path templateFilePath, String newRuleChainName) {
        JsonNode ruleChainJson = JacksonUtil.toJsonNode(templateFilePath.toFile());
        return this.loadRuleChain(templateFilePath, ruleChainJson, tenantId, newRuleChainName);
    }

    public void createDefaultEdgeRuleChains(TenantId tenantId) {
        Path edgeChainsDir = getEdgeRuleChainsDir();
        loadAdditionalTenantRuleChains(tenantId, edgeChainsDir);
    }

    public void loadSystemWidgets() {
        log.info("Loading system widgets");
        Map<Path, JsonNode> widgetsBundlesMap = new HashMap<>();
        Path widgetBundlesDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, WIDGET_BUNDLES_DIR);
        try (Stream<Path> dirStream = listDir(widgetBundlesDir).filter(path -> path.toString().endsWith(JSON_EXT))) {
            dirStream.forEach(
                    path -> {
                        JsonNode widgetsBundleDescriptorJson;
                        try {
                            widgetsBundleDescriptorJson = JacksonUtil.toJsonNode(path.toFile());
                        } catch (Exception e) {
                            log.error("Unable to parse widgets bundle from json: [{}]", path);
                            throw new RuntimeException("Unable to parse widgets bundle from json", e);
                        }
                        if (widgetsBundleDescriptorJson == null || !widgetsBundleDescriptorJson.has("widgetsBundle")) {
                            log.error("Invalid widgets bundle json: [{}]", path);
                            throw new RuntimeException("Invalid widgets bundle json: [" + path + "]");
                        }
                        widgetsBundlesMap.put(path, widgetsBundleDescriptorJson);
                        JsonNode bundleAliasNode = widgetsBundleDescriptorJson.get("widgetsBundle").get("alias");
                        if (bundleAliasNode == null || !bundleAliasNode.isTextual()) {
                            log.error("Invalid widgets bundle json: [{}]", path);
                            throw new RuntimeException("Invalid widgets bundle json: [" + path + "]");
                        }
                        String bundleAlias = bundleAliasNode.asText();
                        try {
                            this.deleteSystemWidgetBundle(bundleAlias);
                        } catch (Exception e) {
                            log.error("Failed to delete system widgets bundle: [{}]", bundleAlias);
                            throw new RuntimeException("Failed to delete system widgets bundle: [" + bundleAlias + "]", e);
                        }
                    }
            );
        }
        Path widgetTypesDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, WIDGET_TYPES_DIR);
        if (Files.exists(widgetTypesDir)) {
            try (Stream<Path> dirStream = listDir(widgetTypesDir).filter(path -> path.toString().endsWith(JSON_EXT))) {
                dirStream.forEach(
                        path -> {
                            try {
                                String widgetTypeJson = Files.readString(path);
                                widgetTypeJson = resourceService.checkSystemResourcesUsage(widgetTypeJson, ResourceType.JS_MODULE);

                                WidgetTypeDetails widgetTypeDetails = JacksonUtil.fromString(widgetTypeJson, WidgetTypeDetails.class);
                                widgetTypeService.saveWidgetType(widgetTypeDetails);
                            } catch (Exception e) {
                                log.error("Unable to load widget type from json: [{}]", path.toString());
                                throw new RuntimeException("Unable to load widget type from json", e);
                            }
                        }
                );
            }
        }
        this.loadSystemScadaSymbols();
        for (var widgetsBundleDescriptorEntry : widgetsBundlesMap.entrySet()) {
            Path path = widgetsBundleDescriptorEntry.getKey();
            try {
                JsonNode widgetsBundleDescriptorJson = widgetsBundleDescriptorEntry.getValue();
                JsonNode widgetsBundleJson = widgetsBundleDescriptorJson.get("widgetsBundle");
                WidgetsBundle widgetsBundle = JacksonUtil.treeToValue(widgetsBundleJson, WidgetsBundle.class);
                WidgetsBundle savedWidgetsBundle = widgetsBundleService.saveWidgetsBundle(widgetsBundle);
                List<String> widgetTypeFqns = new ArrayList<>();
                if (widgetsBundleDescriptorJson.has("widgetTypes")) {
                    JsonNode widgetTypesArrayJson = widgetsBundleDescriptorJson.get("widgetTypes");
                    widgetTypesArrayJson.forEach(
                            widgetTypeJson -> {
                                try {
                                    WidgetTypeDetails widgetTypeDetails = JacksonUtil.treeToValue(widgetTypeJson, WidgetTypeDetails.class);
                                    var savedWidgetType = widgetTypeService.saveWidgetType(widgetTypeDetails);
                                    widgetTypeFqns.add(savedWidgetType.getFqn());
                                } catch (Exception e) {
                                    log.error("Unable to load widget type from json: [{}]", path.toString());
                                    throw new RuntimeException("Unable to load widget type from json", e);
                                }
                            }
                    );
                }
                if (widgetsBundleDescriptorJson.has("widgetTypeFqns")) {
                    JsonNode widgetFqnsArrayJson = widgetsBundleDescriptorJson.get("widgetTypeFqns");
                    widgetFqnsArrayJson.forEach(fqnJson -> {
                        widgetTypeFqns.add(fqnJson.asText());
                    });
                }
                widgetTypeService.updateWidgetsBundleWidgetFqns(TenantId.SYS_TENANT_ID, savedWidgetsBundle.getId(), widgetTypeFqns);
            } catch (Exception e) {
                log.error("Unable to load widgets bundle from json: [{}]", path.toString());
                throw new RuntimeException("Unable to load widgets bundle from json", e);
            }
        }
    }

    private void loadSystemScadaSymbols() {
        log.info("Loading system SCADA symbols");
        Path scadaSymbolsDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, SCADA_SYMBOLS_DIR);
        if (Files.exists(scadaSymbolsDir)) {
            WidgetTypeDetails scadaSymbolWidgetTemplate = widgetTypeService.findWidgetTypeDetailsByTenantIdAndFqn(TenantId.SYS_TENANT_ID, "scada_symbol");
            try (Stream<Path> dirStream = listDir(scadaSymbolsDir).filter(path -> path.toString().endsWith(SVG_EXT))) {
                dirStream.forEach(
                        path -> {
                            try {
                                var fileName = path.getFileName().toString();
                                var scadaSymbolData = Files.readAllBytes(path);
                                var metadata = ImageUtils.processScadaSymbolMetadata(fileName, scadaSymbolData);
                                TbResourceInfo savedScadaSymbol = saveScadaSymbol(metadata, fileName, scadaSymbolData);
                                if (scadaSymbolWidgetTemplate != null) {
                                    saveScadaSymbolWidget(scadaSymbolWidgetTemplate, savedScadaSymbol, metadata);
                                }
                            } catch (Exception e) {
                                log.error("Unable to load SCADA symbol from file: [{}]", path.toString());
                                throw new RuntimeException("Unable to load SCADA symbol from file", e);
                            }
                        }
                );
            }
        }
    }

    private TbResourceInfo saveScadaSymbol(ImageUtils.ScadaSymbolMetadataInfo metadata, String fileName, byte[] scadaSymbolData) {
        String etag = imageService.calculateImageEtag(scadaSymbolData);
        var existingImage = imageService.findSystemOrTenantImageByEtag(TenantId.SYS_TENANT_ID, etag);
        if (existingImage != null && ResourceSubType.SCADA_SYMBOL.equals(existingImage.getResourceSubType())) {
            return existingImage;
        } else {
            var existing = imageService.getImageInfoByTenantIdAndKey(TenantId.SYS_TENANT_ID, fileName);
            if (existing != null && ResourceSubType.SCADA_SYMBOL.equals(existing.getResourceSubType())) {
                imageService.deleteImage(existing, true);
            }
            TbResource image = new TbResource();
            image.setTenantId(TenantId.SYS_TENANT_ID);
            image.setFileName(fileName);
            image.setTitle(metadata.getTitle());
            image.setResourceSubType(ResourceSubType.SCADA_SYMBOL);
            image.setResourceType(ResourceType.IMAGE);
            image.setPublic(true);
            ImageDescriptor descriptor = new ImageDescriptor();
            descriptor.setMediaType("image/svg+xml");
            image.setDescriptorValue(descriptor);
            image.setData(scadaSymbolData);
            return imageService.saveImage(image);
        }
    }

    private WidgetTypeDetails saveScadaSymbolWidget(WidgetTypeDetails template, TbResourceInfo scadaSymbol,
                                                    ImageUtils.ScadaSymbolMetadataInfo metadata) {
        String symbolUrl = DataConstants.TB_IMAGE_PREFIX + scadaSymbol.getLink();
        WidgetTypeDetails scadaSymbolWidget = new WidgetTypeDetails();
        JsonNode descriptor = JacksonUtil.clone(template.getDescriptor());
        scadaSymbolWidget.setDescriptor(descriptor);
        scadaSymbolWidget.setName(metadata.getTitle());
        scadaSymbolWidget.setImage(symbolUrl);
        scadaSymbolWidget.setDescription(metadata.getDescription());
        scadaSymbolWidget.setTags(metadata.getSearchTags());
        scadaSymbolWidget.setScada(true);
        ObjectNode defaultConfig = null;
        if (descriptor.has("defaultConfig")) {
            defaultConfig = JacksonUtil.fromString(descriptor.get("defaultConfig").asText(), ObjectNode.class);
        }
        if (defaultConfig == null) {
            defaultConfig = JacksonUtil.newObjectNode();
        }
        defaultConfig.put("title", metadata.getTitle());
        ObjectNode settings;
        if (defaultConfig.has("settings")) {
            settings = (ObjectNode) defaultConfig.get("settings");
        } else {
            settings = JacksonUtil.newObjectNode();
            defaultConfig.set("settings", settings);
        }
        settings.put("scadaSymbolUrl", symbolUrl);
        ((ObjectNode) descriptor).put("defaultConfig", JacksonUtil.toString(defaultConfig));
        ((ObjectNode) descriptor).put("sizeX", metadata.getWidgetSizeX());
        ((ObjectNode) descriptor).put("sizeY", metadata.getWidgetSizeY());
        String controllerScript = descriptor.get("controllerScript").asText();
        controllerScript = controllerScript.replaceAll("previewWidth: '\\d*px'", "previewWidth: '" + (metadata.getWidgetSizeX() * 100) + "px'");
        controllerScript = controllerScript.replaceAll("previewHeight: '\\d*px'", "previewHeight: '" + (metadata.getWidgetSizeY() * 100 + 20) + "px'");
        ((ObjectNode) descriptor).put("controllerScript", controllerScript);
        return widgetTypeService.saveWidgetType(scadaSymbolWidget);
    }

    private void deleteSystemWidgetBundle(String bundleAlias) {
        WidgetsBundle widgetsBundle = widgetsBundleService.findWidgetsBundleByTenantIdAndAlias(TenantId.SYS_TENANT_ID, bundleAlias);
        if (widgetsBundle != null) {
            widgetTypeService.deleteWidgetTypesByBundleId(TenantId.SYS_TENANT_ID, widgetsBundle.getId());
            widgetsBundleService.deleteWidgetsBundle(TenantId.SYS_TENANT_ID, widgetsBundle.getId());
        }
    }

    public void updateImages() {
        imagesUpdater.updateWidgetsBundlesImages();
        imagesUpdater.updateWidgetTypesImages();
        imagesUpdater.updateWhiteLabelingImages();
        imagesUpdater.updateDashboardsImages();
        imagesUpdater.updateDeviceProfilesImages();
        imagesUpdater.updateAssetProfilesImages();
    }

    public void loadSystemImages() {
        log.info("Loading system images...");
        Stream<Path> dashboardsFiles = Stream.concat(Stream.concat(listDir(Paths.get(getDataDir(), JSON_DIR, DEMO_DIR, DASHBOARDS_DIR)),
                        listDir(Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, DASHBOARDS_DIR))),
                listDir(Paths.get(getDataDir(), JSON_DIR, SOLUTIONS_DIR))
                        .filter(file -> file.toFile().isDirectory())
                        .flatMap(solutionDir -> listDir(solutionDir.resolve(DASHBOARDS_DIR))));
        try (dashboardsFiles) {
            dashboardsFiles.forEach(file -> {
                try {
                    Dashboard dashboard = JacksonUtil.OBJECT_MAPPER.readValue(file.toFile(), Dashboard.class);
                    imagesUpdater.createSystemImages(dashboard);
                } catch (Exception e) {
                    log.error("Failed to create system images for default dashboard {}", file.getFileName(), e);
                }
            });
        }
    }

    public void loadDashboards(TenantId tenantId, CustomerId customerId) {
        Path dashboardsDir = Paths.get(getDataDir(), JSON_DIR, DEMO_DIR, DASHBOARDS_DIR);
        loadDashboardsFromDir(tenantId, customerId, dashboardsDir);
    }

    public void createDefaultTenantDashboards(TenantId tenantId, CustomerId customerId) {
        Path dashboardsDir = Paths.get(getDataDir(), JSON_DIR, TENANT_DIR, DASHBOARDS_DIR);
        loadDashboardsFromDir(tenantId, customerId, dashboardsDir);
    }

    private void loadDashboardsFromDir(TenantId tenantId, CustomerId customerId, Path dashboardsDir) {
        try (Stream<Path> dashboards = listDir(dashboardsDir).filter(path -> path.toString().endsWith(JSON_EXT))) {
            dashboards.forEach(path -> {
                try {
                    JsonNode dashboardJson = JacksonUtil.toJsonNode(path.toFile());
                    Dashboard dashboard = JacksonUtil.treeToValue(dashboardJson, Dashboard.class);
                    dashboard.setTenantId(tenantId);
                    Dashboard savedDashboard = dashboardService.saveDashboard(dashboard);
                    if (customerId != null && !customerId.isNullUid()) {
                        EntityGroup dashboardGroup = entityGroupService.findOrCreateReadOnlyEntityGroupForCustomer(tenantId, customerId, EntityType.DASHBOARD);
                        entityGroupService.addEntityToEntityGroup(tenantId, dashboardGroup.getId(), savedDashboard.getId());
                    }
                } catch (Exception e) {
                    log.error("Unable to load dashboard from json: [{}]", path.toString());
                    throw new RuntimeException("Unable to load dashboard from json", e);
                }
            });
        }
    }

    public void loadMailTemplates() throws Exception {
        JsonNode mailTemplatesJson = readMailTemplates();
        whiteLabelingService.saveMailTemplates(TenantId.SYS_TENANT_ID, mailTemplatesJson);
    }

    public void updateMailTemplates(JsonNode oldTemplates) throws IOException {
        JsonNode newTemplates = readMailTemplates();

        ObjectNode result = JacksonUtil.newObjectNode();
        Iterator<String> fieldsIterator = newTemplates.fieldNames();
        while (fieldsIterator.hasNext()) {
            String field = fieldsIterator.next();
            if (oldTemplates.has(field)) {
                result.set(field, oldTemplates.get(field));
            } else {
                result.set(field, newTemplates.get(field));
            }
        }
        Optional<String> updated = updateMailTemplatesFromVelocityToFreeMarker(JacksonUtil.toString(result));
        if (updated.isPresent()) {
            result = (ObjectNode) JacksonUtil.toJsonNode(updated.get());
        }

        whiteLabelingService.saveMailTemplates(TenantId.SYS_TENANT_ID, result);
    }

    public Optional<String> updateMailTemplatesFromVelocityToFreeMarker(String mailTemplatesJsonString) {
        Matcher matcher = velocityVarPattern.matcher(mailTemplatesJsonString);
        if (matcher.find()) {
            mailTemplatesJsonString = matcher.replaceAll(velocityVarToFreeMakerReplacementPattern);
            return Optional.of(mailTemplatesJsonString);
        } else {
            return Optional.empty();
        }
    }

    private JsonNode readMailTemplates() throws IOException {
        Path mailTemplatesFile = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, MAIL_TEMPLATES_DIR, MAIL_TEMPLATES_JSON);
        return JacksonUtil.toJsonNode(mailTemplatesFile.toFile());
    }

    public void loadDemoRuleChains(TenantId tenantId) {
        try {
            createDefaultRuleChains(tenantId);
            createDefaultRuleChain(tenantId, "Thermostat");
            createDefaultEdgeRuleChains(tenantId);
        } catch (Exception e) {
            log.error("Unable to load rule chain from json", e);
            throw new RuntimeException("Unable to load rule chain from json", e);
        }
    }

    @SneakyThrows
    private void loadRootRuleChain(TenantId tenantId, Map<String, RuleChainId> ruleChainIdMap, Path rootRuleChainFile) {
        String rootRuleChainContent = FileUtils.readFileToString(rootRuleChainFile.toFile(), "UTF-8");
        for (Map.Entry<String, RuleChainId> entry : ruleChainIdMap.entrySet()) {
            String key = "${" + entry.getKey() + "}";
            rootRuleChainContent = rootRuleChainContent.replace(key, entry.getValue().toString());
        }
        JsonNode rootRuleChainJson = JacksonUtil.toJsonNode(rootRuleChainContent);
        loadRuleChain(rootRuleChainFile, rootRuleChainJson, tenantId, null);
    }

    @SneakyThrows
    private Map<String, RuleChainId> loadAdditionalTenantRuleChains(TenantId tenantId, Path chainsDir) {
        Map<String, RuleChainId> ruleChainIdMap = new HashMap<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(chainsDir, path -> path.toString().endsWith(InstallScripts.JSON_EXT))) {
            dirStream.forEach(
                    path -> {
                        try {
                            JsonNode ruleChainJson = JacksonUtil.toJsonNode(path.toFile());

                            RuleChain ruleChain = loadRuleChain(path, ruleChainJson, tenantId, null);
                            ruleChainIdMap.put(ruleChain.getName(), ruleChain.getId());

                        } catch (Exception e) {
                            log.error("Unable to load rule chain from json: [{}]", path.toString());
                            throw new RuntimeException("Unable to load rule chain from json", e);
                        }
                    }
            );
        }
        return ruleChainIdMap;
    }

    public void createOAuth2Templates() {
        Path oauth2ConfigTemplatesDir = Paths.get(getDataDir(), JSON_DIR, SYSTEM_DIR, OAUTH2_CONFIG_TEMPLATES_DIR);
        try (Stream<Path> dirStream = listDir(oauth2ConfigTemplatesDir).filter(path -> path.toString().endsWith(JSON_EXT))) {
            dirStream.forEach(
                    path -> {
                        try {
                            JsonNode oauth2ConfigTemplateJson = JacksonUtil.toJsonNode(path.toFile());
                            OAuth2ClientRegistrationTemplate clientRegistrationTemplate = JacksonUtil.treeToValue(oauth2ConfigTemplateJson, OAuth2ClientRegistrationTemplate.class);
                            Optional<OAuth2ClientRegistrationTemplate> existingClientRegistrationTemplate =
                                    oAuth2TemplateService.findClientRegistrationTemplateByProviderId(clientRegistrationTemplate.getProviderId());
                            if (existingClientRegistrationTemplate.isPresent()) {
                                clientRegistrationTemplate.setId(existingClientRegistrationTemplate.get().getId());
                            }
                            oAuth2TemplateService.saveClientRegistrationTemplate(clientRegistrationTemplate);
                        } catch (Exception e) {
                            log.error("Unable to load oauth2 config templates from json: [{}]", path.toString());
                            throw new RuntimeException("Unable to load oauth2 config templates from json", e);
                        }
                    }
            );
        }
    }

    public void loadSystemLwm2mResources() {
        Path resourceLwm2mPath = Paths.get(getDataDir(), MODELS_LWM2M_DIR);
        try (Stream<Path> dirStream = listDir(resourceLwm2mPath).filter(path -> path.toString().endsWith(InstallScripts.XML_EXT))) {
            dirStream.forEach(
                    path -> {
                        try {
                            byte[] data = Files.readAllBytes(path);
                            TbResource tbResource = new TbResource();
                            tbResource.setTenantId(TenantId.SYS_TENANT_ID);
                            tbResource.setData(data);
                            tbResource.setResourceType(ResourceType.LWM2M_MODEL);
                            tbResource.setFileName(path.toFile().getName());
                            doSaveLwm2mResource(tbResource);
                        } catch (Exception e) {
                            log.error("Unable to load resource lwm2m object model from file: [{}]", path.toString());
                            throw new RuntimeException("resource lwm2m object model from file", e);
                        }
                    }
            );
        } catch (Exception e) {
            log.error("Unable to load resources lwm2m object model from file: [{}]", resourceLwm2mPath);
            throw new RuntimeException("resource lwm2m object model from file", e);
        }
    }

    public void loadSystemResources() {
        Path resourcesDir = Path.of(getDataDir(), RESOURCES_DIR);
        loadSystemResources(resourcesDir.resolve("js_modules"), ResourceType.JS_MODULE);
        loadSystemResources(resourcesDir.resolve("dashboards"), ResourceType.DASHBOARD);
    }

    private void loadSystemResources(Path dir, ResourceType resourceType) {
        listDir(dir).forEach(resourceFile -> {
            String resourceKey = resourceFile.getFileName().toString();
            try {
                String data = getContent(resourceFile);
                TbResource resource = resourceService.createOrUpdateSystemResource(resourceType, resourceKey, data);
                log.info("{} resource {}", (resource.getId() == null ? "Created" : "Updated"), resourceKey);
            } catch (Exception e) {
                throw new RuntimeException("Unable to load system resource " + resourceFile, e);
            }
        });
    }

    private String getContent(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<Path> listDir(Path dir) {
        try {
            return Files.list(dir);
        } catch (NoSuchFileException e) {
            return Stream.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void doSaveLwm2mResource(TbResource resource) throws ThingsboardException {
        log.trace("Executing saveResource [{}]", resource);
        if (resource.getData() == null || resource.getData().length == 0) {
            throw new DataValidationException("Resource data should be specified!");
        }
        toLwm2mResource(resource);
        TbResource foundResource = resourceService.findResourceByTenantIdAndKey(TenantId.SYS_TENANT_ID, ResourceType.LWM2M_MODEL, resource.getResourceKey());
        if (foundResource == null) {
            resourceService.saveResource(resource);
        }
    }

}
