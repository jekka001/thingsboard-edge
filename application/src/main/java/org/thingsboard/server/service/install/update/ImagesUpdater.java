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
package org.thingsboard.server.service.install.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.ResourceType;
import org.thingsboard.server.common.data.TbResource;
import org.thingsboard.server.common.data.TbResourceInfo;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetsBundle;
import org.thingsboard.server.dao.resource.ImageService;
import org.thingsboard.server.dao.util.ImageUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ImagesUpdater {

    private final ImageService imageService;

    private static final String IMAGE_NAME_SUFFIX = " - image";
    private static final String BACKGROUND_IMAGE_NAME_SUFFIX = " - background image";
    private static final String BACKGROUND_IMAGE_KEY_SUFFIX = "#background_image";
    private static final String MAP_IMAGE_NAME_SUFFIX = " - map image";
    private static final String MAP_IMAGE_KEY_SUFFIX = "#map_image";
    private static final String MARKER_IMAGE_NAME_SUFFIX = " - marker image ";
    private static final String MARKER_IMAGE_KEY_SUFFIX = "#marker_image_";

    public boolean updateDashboard(Dashboard dashboard) {
        String imageKeyPrefix = "dashboard_" + dashboard.getUuidId();
        String image = dashboard.getImage();
        ImageSaveResult result = saveImage(dashboard.getTenantId(), dashboard.getTitle() + IMAGE_NAME_SUFFIX,
                imageKeyPrefix + ".image", image, null);
        dashboard.setImage(result.getLink());
        boolean updated = result.isUpdated();

        for (ObjectNode widgetConfig : dashboard.getWidgetsConfig()) {
            String fqn;
            if (widgetConfig.has("typeFullFqn")) {
                fqn = StringUtils.substringAfter(widgetConfig.get("typeFullFqn").asText(), "."); // removing prefix ('system' or 'tenant')
            } else {
                fqn = widgetConfig.get("bundleAlias").asText() + "." + widgetConfig.get("typeAlias").asText();
            }
            String widgetName = widgetConfig.get("config").get("title").asText();
            updated |= updateWidgetConfig(dashboard.getTenantId(), widgetConfig.get("config"),
                    dashboard.getTitle() + " - " + widgetName + " widget",
                    imageKeyPrefix + "." + fqn, fqn);
        }
        return updated;
    }

    public boolean updateWidgetsBundle(WidgetsBundle widgetsBundle) {
        String bundleName = widgetsBundle.getTitle();
        String bundleAlias = widgetsBundle.getAlias();

        String image = widgetsBundle.getImage();
        ImageSaveResult result = saveImage(widgetsBundle.getTenantId(), bundleName + IMAGE_NAME_SUFFIX, bundleAlias, image, bundleAlias);
        String imageLink = result.getLink();
        widgetsBundle.setImage(imageLink);

        return result.isUpdated();
    }

    public boolean updateWidget(WidgetTypeDetails widgetType) {
        String widgetName = widgetType.getName();
        String widgetFqn = widgetType.getFqn();
        boolean updated;

        String previewImage = widgetType.getImage();
        ImageSaveResult result = saveImage(widgetType.getTenantId(), widgetName + IMAGE_NAME_SUFFIX, widgetFqn, previewImage, widgetFqn);
        updated = result.isUpdated();
        widgetType.setImage(result.getLink());

        JsonNode descriptor = widgetType.getDescriptor();
        if (!descriptor.isObject()) {
            return updated;
        }
        JsonNode defaultConfig = JacksonUtil.toJsonNode(descriptor.get("defaultConfig").asText());
        updated |= updateWidgetConfig(widgetType.getTenantId(), defaultConfig, widgetName, widgetFqn, widgetFqn);
        ((ObjectNode) descriptor).put("defaultConfig", defaultConfig.toString());
        return updated;
    }

    private boolean updateWidgetConfig(TenantId tenantId, JsonNode widgetConfigJson, String imageNamePrefix, String imageKeyPrefix, String widgetFqn) {
        boolean updated = false;
        ObjectNode widgetSettings = (ObjectNode) widgetConfigJson.get("settings");
        ArrayNode markerImages = (ArrayNode) widgetSettings.get("markerImages");
        if (markerImages != null && !markerImages.isEmpty()) {
            for (int i = 0; i < markerImages.size(); i++) {
                String imageName = imageNamePrefix + MARKER_IMAGE_NAME_SUFFIX + (i + 1);
                String imageKey = imageKeyPrefix + MARKER_IMAGE_KEY_SUFFIX + (i + 1);
                ImageSaveResult result = saveImage(tenantId, imageName, imageKey, markerImages.get(i).asText(), widgetFqn);
                markerImages.set(i, result.getLink());
                updated |= result.isUpdated();
            }
        }

        String mapImage = getText(widgetSettings, "mapImageUrl");
        if (mapImage != null) {
            String imageName = imageNamePrefix + MAP_IMAGE_NAME_SUFFIX;
            String imageKey = imageKeyPrefix + MAP_IMAGE_KEY_SUFFIX;
            ImageSaveResult result = saveImage(tenantId, imageName, imageKey, mapImage, widgetFqn);
            widgetSettings.put("mapImageUrl", result.getLink());
            updated |= result.isUpdated();
        }

        String backgroundImage = getText(widgetSettings, "backgroundImageUrl");
        if (backgroundImage != null) {
            String imageName = imageNamePrefix + BACKGROUND_IMAGE_NAME_SUFFIX;
            String imageKey = imageKeyPrefix + BACKGROUND_IMAGE_KEY_SUFFIX;
            ImageSaveResult result = saveImage(tenantId, imageName, imageKey, backgroundImage, widgetFqn);
            widgetSettings.put("backgroundImageUrl", result.getLink());
            updated |= result.isUpdated();
        }

        JsonNode backgroundConfigNode = widgetSettings.get("background");
        if (backgroundConfigNode != null && backgroundConfigNode.isObject()) {
            ObjectNode backgroundConfig = (ObjectNode) backgroundConfigNode;
            if ("image".equals(getText(backgroundConfig, "type"))) {
                String imageBase64 = getText(backgroundConfig, "imageBase64");
                if (imageBase64 != null) {
                    String imageName = imageNamePrefix + BACKGROUND_IMAGE_NAME_SUFFIX;
                    String imageKey = imageKeyPrefix + BACKGROUND_IMAGE_KEY_SUFFIX;
                    ImageSaveResult result = saveImage(tenantId, imageName, imageKey, imageBase64, widgetFqn);
                    backgroundConfig.set("imageBase64", null);
                    backgroundConfig.put("imageUrl", result.getLink());
                    backgroundConfig.put("type", "imageUrl");
                    updated |= result.isUpdated();
                }
            }
        }
        return updated;
    }


    private ImageSaveResult saveImage(TenantId tenantId, String name, String key, String data,
                                      String existingImageQuery) {
        if (data == null) {
            return new ImageSaveResult(null, false);
        }
        String base64Data = StringUtils.substringAfter(data, "base64,");
        if (base64Data.isEmpty()) {
            return new ImageSaveResult(data, false);
        }

        String imageMediaType = StringUtils.substringBetween(data, "data:", ";base64");
        String extension = ImageUtils.mediaTypeToFileExtension(imageMediaType);
        key += "." + extension;

        byte[] imageData = Base64.getDecoder().decode(base64Data);
        String imageLink = saveImage(tenantId, name, key, imageData, imageMediaType, existingImageQuery);
        return new ImageSaveResult(imageLink, !imageLink.equals(data));
    }

    @SneakyThrows
    private String saveImage(TenantId tenantId, String name, String key, byte[] imageData, String mediaType,
                             String existingImageQuery) {
        TbResourceInfo imageInfo = imageService.getImageInfoByTenantIdAndKey(tenantId, key);
        if (imageInfo == null && !tenantId.isSysTenantId() && existingImageQuery != null) {
            List<TbResourceInfo> similarImages = imageService.findSimilarImagesByTenantIdAndKeyStartingWith(TenantId.SYS_TENANT_ID, imageData, existingImageQuery);
            if (similarImages.isEmpty()) {
                similarImages = imageService.findSimilarImagesByTenantIdAndKeyStartingWith(tenantId, imageData, existingImageQuery);
            }
            if (!similarImages.isEmpty()) {
                imageInfo = similarImages.get(0);
                if (similarImages.size() > 1) {
                    log.debug("Found more than one image resources for key {}: {}", existingImageQuery, similarImages);
                }
                String link = imageInfo.getLink();
                log.info("[{}] Using image {} for {}", tenantId, link, key);
                return link;
            }
        }
        TbResource image;
        if (imageInfo == null) {
            image = new TbResource();
            image.setTenantId(tenantId);
            image.setResourceType(ResourceType.IMAGE);
            image.setResourceKey(key);
        } else if (tenantId.isSysTenantId()) {
            image = new TbResource(imageInfo);
        } else {
            return imageInfo.getLink();
        }
        image.setTitle(name);
        image.setFileName(key);
        image.setDescriptor(JacksonUtil.newObjectNode()
                .put("mediaType", mediaType));
        image.setData(imageData);

        TbResourceInfo savedImage = imageService.saveImage(image);
        log.info("[{}] {} image '{}' ({})", tenantId, imageInfo == null ? "Created" : "Updated",
                image.getTitle(), image.getResourceKey());
        return savedImage.getLink();
    }

    private String getText(JsonNode jsonNode, String field) {
        return Optional.ofNullable(jsonNode.get(field))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText).orElse(null);
    }

    @Data
    public static class ImageSaveResult {
        private final String link;
        private final boolean updated;
    }

}
