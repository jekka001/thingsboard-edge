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
package org.thingsboard.server.service.translation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.translation.CustomTranslation;
import org.thingsboard.server.common.data.translation.TranslationInfo;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.translation.CustomTranslationService;
import org.thingsboard.server.dao.translation.TranslationCacheKey;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.entitiy.AbstractEtagCacheService;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.thingsboard.common.util.JacksonUtil.extractKeys;
import static org.thingsboard.common.util.JacksonUtil.merge;
import static org.thingsboard.common.util.JacksonUtil.newObjectNode;
import static org.thingsboard.server.common.data.wl.WhiteLabelingType.LOGIN;

@Service
@Slf4j
@TbCoreComponent
public class DefaultTbTranslationService extends AbstractEtagCacheService<TranslationCacheKey> implements TbTranslationService {

    public static final String LOCALE_FILES_DIRECTORY_PATH = "public/assets/locale";
    public static final Pattern LOCALE_FILE_PATTERN = Pattern.compile("locale\\.constant-(.*?)\\.json");
    public static final String DEFAULT_LOCALE_CODE = "en_US";
    private static final Set<String> DEFAULT_LOCALE_KEYS;
    private static final Map<String, JsonNode> TRANSLATION_VALUE_MAP = new HashMap<>();
    private static final Map<String, TranslationInfo> TRANSLATION_INFO_MAP = new HashMap<>();
    private final CustomTranslationService customTranslationService;
    private final WhiteLabelingService whiteLabelingService;
    private final CustomerService customerService;
    private final TbClusterService clusterService;

    static {
        JsonNode defaultTranslation = readResourceLocaleTranslation(DEFAULT_LOCALE_CODE);
        DEFAULT_LOCALE_KEYS = extractKeys(defaultTranslation);

        Set<String> systemLocaleCodes = getAvailableResourceLocaleCodes();
        for (String localeCode : systemLocaleCodes) {
            JsonNode resourceLocaleTranslation = readResourceLocaleTranslation(localeCode);
            TRANSLATION_INFO_MAP.put(localeCode, createTranslationInfo(DEFAULT_LOCALE_KEYS, localeCode, resourceLocaleTranslation, false));
            TRANSLATION_VALUE_MAP.put(localeCode, merge(defaultTranslation.deepCopy(), resourceLocaleTranslation));
        }
    }

    public DefaultTbTranslationService(TbClusterService clusterService, CustomTranslationService customTranslationService,
                                       WhiteLabelingService whiteLabelingService, CustomerService customerService,
                                       @Value("${cache.translation.etag.timeToLiveInMinutes:44640}") int cacheTtl,
                                       @Value("${cache.translation.etag.maxSize:1000000}") int cacheMaxSize) {
        super(cacheTtl, cacheMaxSize);
        this.clusterService = clusterService;
        this.customTranslationService = customTranslationService;
        this.whiteLabelingService = whiteLabelingService;
        this.customerService = customerService;
    }

    @Override
    public List<TranslationInfo> getTranslationInfos(TenantId tenantId, CustomerId customerId) {
        Map<String, TranslationInfo> translationInfos = JacksonUtil.clone(TRANSLATION_INFO_MAP);

        Set<String> customizedLocales = getMergedCustomizedLocales(tenantId, customerId);
        Set<String> currentCustomizedLocales = customTranslationService.getCurrentCustomizedLocales(tenantId, customerId);

        Set<String> engLocaleKeys = extractKeys(getFullTranslation(tenantId, customerId, DEFAULT_LOCALE_CODE));
        for (String customizedLocale : customizedLocales) {
            JsonNode customTranslation = getMergedCustomTranslation(tenantId, customerId, customizedLocale);
            if (translationInfos.containsKey(customizedLocale)) {
                JsonNode resourceTranslation = readResourceLocaleTranslation(customizedLocale);
                customTranslation = merge(resourceTranslation, customTranslation);
            }
            boolean customized = currentCustomizedLocales.contains(customizedLocale);
            translationInfos.put(customizedLocale, createTranslationInfo(engLocaleKeys, customizedLocale, customTranslation, customized));
        }
        return new ArrayList<>(translationInfos.values());
    }

    @Override
    public Set<String> getAvailableLocaleCodes(TenantId tenantId, CustomerId customerId) {
        Set<String> availableLocaleCodes = new HashSet<>(TRANSLATION_INFO_MAP.keySet());
        Set<String> customizedLocales = getMergedCustomizedLocales(tenantId, customerId);
        availableLocaleCodes.addAll(customizedLocales);
        return availableLocaleCodes;
    }

    @Override
    public JsonNode getLoginTranslation(String localeCode, String domainName) {
        WhiteLabeling whiteLabeling = whiteLabelingService.findWhiteLabelingByDomainAndType(domainName, LOGIN);
        JsonNode fullTranslation;
        if (whiteLabeling != null) {
            TenantId tenantId = whiteLabeling.getTenantId();
            CustomerId customerId = whiteLabeling.getCustomerId();
            fullTranslation = getFullTranslation(tenantId, customerId, localeCode);
        } else {
            fullTranslation = TRANSLATION_VALUE_MAP.getOrDefault(localeCode, TRANSLATION_VALUE_MAP.get(DEFAULT_LOCALE_CODE)).deepCopy();
        }
        ObjectNode loginPageTranslation = newObjectNode();
        loginPageTranslation.set("login", fullTranslation.get("login"));
        loginPageTranslation.set("signup", fullTranslation.get("signup"));
        loginPageTranslation.set("common", fullTranslation.get("common"));
        loginPageTranslation.set("action", fullTranslation.get("action"));
        loginPageTranslation.set("security", newObjectNode().set("2fa", fullTranslation.get("security").get("2fa")));
        loginPageTranslation.set("access", fullTranslation.get("access"));
        return loginPageTranslation;
    }

    @Override
    public JsonNode getFullTranslation(TenantId tenantId, CustomerId customerId, String localeCode) {
        JsonNode customTranslation = getMergedCustomTranslation(tenantId, customerId, localeCode);
        JsonNode defaultTranslation = TRANSLATION_VALUE_MAP.getOrDefault(localeCode, TRANSLATION_VALUE_MAP.get(DEFAULT_LOCALE_CODE)).deepCopy();
        JsonNode merged = merge(defaultTranslation, customTranslation);

        // add new keys from default locale custom translation
        JsonNode defaultCustomTranslation = getMergedCustomTranslation(tenantId, customerId, DEFAULT_LOCALE_CODE);
        addNonExisting(merged, defaultCustomTranslation);
        return merged;
    }

    @Override
    public JsonNode getTranslationForBasicEdit(TenantId tenantId, CustomerId customerId, String localeCode) {
        JsonNode fullTranslation = getFullTranslation(tenantId, customerId, localeCode).deepCopy();
        JsonNode currentCustomTranslation = customTranslationService.getCurrentCustomTranslation(tenantId, customerId, localeCode);
        JsonNode originalTranslation = getTranslatedOnly(tenantId, customerId, DEFAULT_LOCALE_CODE, TRANSLATION_VALUE_MAP.get(DEFAULT_LOCALE_CODE).deepCopy());
        JsonNode parentOriginalTranslation = getParentTranslatedOnly(tenantId, customerId, DEFAULT_LOCALE_CODE, TRANSLATION_VALUE_MAP.get(DEFAULT_LOCALE_CODE).deepCopy());
        JsonNode resourceTranslation = TRANSLATION_VALUE_MAP.containsKey(localeCode) ? readResourceLocaleTranslation(localeCode) : JacksonUtil.newObjectNode();
        JsonNode translated = getTranslatedOnly(tenantId, customerId, localeCode, resourceTranslation.deepCopy());
        JsonNode parentTranslated = getParentTranslatedOnly(tenantId, customerId, localeCode, resourceTranslation.deepCopy());
        buildTranslationInfoForEdit(fullTranslation, translated, parentTranslated, originalTranslation, currentCustomTranslation, parentOriginalTranslation);
        return fullTranslation;
    }

    @Override
    public void saveCustomTranslation(CustomTranslation customTranslation) {
        customTranslationService.saveCustomTranslation(customTranslation);
        evictFromCache(customTranslation.getTenantId());
    }

    @Override
    public void patchCustomTranslation(TenantId tenantId, CustomerId customerId, String localeCode, JsonNode customTranslation) {
        customTranslationService.patchCustomTranslation(tenantId, customerId, localeCode, customTranslation);
        evictFromCache(tenantId);
    }

    @Override
    public void deleteCustomTranslationKey(TenantId tenantId, CustomerId customerId, String localeCode, String keyPath) {
        customTranslationService.deleteCustomTranslationKeyByPath(tenantId, customerId, localeCode, keyPath);
        evictFromCache(tenantId);
    }

    @Override
    public void deleteCustomTranslation(TenantId tenantId, CustomerId customerId, String localeCode) {
        customTranslationService.deleteCustomTranslation(tenantId, customerId, localeCode);
        evictFromCache(tenantId);
    }

    private static TranslationInfo createTranslationInfo(Set<String> engLocaleKeys, String localeCode, JsonNode translation, boolean customized) {
        int progress = calculateTranslationProgress(engLocaleKeys, translation);
        Locale locale = Locale.forLanguageTag(localeCode.replace("_", "-"));
        return TranslationInfo.builder()
                .customized(customized)
                .localeCode(localeCode)
                .country(locale.getDisplayCountry())
                .language(StringUtils.capitalize(locale.getDisplayLanguage(locale) + " (" + locale.getDisplayLanguage() + ")"))
                .progress(progress)
                .build();
    }

    private JsonNode getMergedCustomTranslation(TenantId tenantId, CustomerId customerId, String localeCode) {
        JsonNode customTranslation;
        if (tenantId.isSysTenantId()) {
            customTranslation = customTranslationService.getCurrentCustomTranslation(TenantId.SYS_TENANT_ID, null, localeCode);
        } else if (customerId == null || customerId.isNullUid()) {
            customTranslation = customTranslationService.getMergedTenantCustomTranslation(tenantId, localeCode);
        } else {
            customTranslation = customTranslationService.getMergedCustomerCustomTranslation(tenantId, customerId, localeCode);
        }
        return customTranslation;
    }

    private Set<String> getMergedCustomizedLocales(TenantId tenantId, CustomerId customerId) {
        Set<String> customizedLocales;
        if (tenantId.isSysTenantId()) {
            customizedLocales = customTranslationService.getCurrentCustomizedLocales(TenantId.SYS_TENANT_ID, null);
        } else if (customerId == null || customerId.isNullUid()) {
            customizedLocales = customTranslationService.getMergedTenantCustomizedLocales(tenantId);
        } else {
            customizedLocales = customTranslationService.getMergedCustomerCustomizedLocales(tenantId, customerId);
        }
        return customizedLocales;
    }

    private static JsonNode readResourceLocaleTranslation(String localeCode) {
        String filePath = LOCALE_FILES_DIRECTORY_PATH + "/locale.constant-" + localeCode + ".json";
        try (InputStream in = DefaultTbTranslationService.class.getClassLoader().getResourceAsStream(filePath)) {
            return JacksonUtil.OBJECT_MAPPER.readTree(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read locale translation for " + localeCode + "!", e);
        }
    }

    private static Set<String> getAvailableResourceLocaleCodes() {
        List<String> filenames = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath:public/assets/locale/*.json");
            Arrays.stream(resources).forEach(resource -> filenames.add(resource.getFilename()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to get list of system locales!", e);
        }
        return filenames.stream().map(DefaultTbTranslationService::getLocaleFromFileName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static String getLocaleFromFileName(String filename) {
        Matcher matcher = LOCALE_FILE_PATTERN.matcher(filename);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    private static int calculateTranslationProgress(Set<String> defaultLocaleKeys, JsonNode translation) {
        Set<String> localeKeys = extractKeys(translation);
        long translated = defaultLocaleKeys.stream()
                .filter(localeKeys::contains)
                .count();
        return (int) (((translated) * 100) / defaultLocaleKeys.size());
    }

    private void buildTranslationInfoForEdit(JsonNode fullTranslation, JsonNode translated, JsonNode parentTranslated, JsonNode original, JsonNode custom, JsonNode parentOrigin) {
        Iterator<String> fieldNamesIterator = fullTranslation.fieldNames();
        while (fieldNamesIterator.hasNext()) {
            String fieldName = fieldNamesIterator.next();
            JsonNode fullNode = fullTranslation.get(fieldName);
            JsonNode customNode = custom == null ? null : custom.get(fieldName);
            JsonNode parentTranslatedNode = parentTranslated == null ? null : parentTranslated.get(fieldName);
            JsonNode originNode = original == null ? null : original.get(fieldName);
            JsonNode translatedNode = translated == null ? null : translated.get(fieldName);
            JsonNode parentOriginNode = parentOrigin == null ? null : parentOrigin.get(fieldName);
            if (fullNode.isObject()) {
                buildTranslationInfoForEdit(fullNode, translatedNode, parentTranslatedNode, originNode, customNode, parentOriginNode);
            } else {
                ObjectNode info = newObjectNode();
                // original translation
                if (originNode != null) {
                    info.put("o", originNode.asText());
                }
                // parent translation
                if (parentTranslatedNode != null) {
                    info.put("p", parentTranslatedNode.asText());
                }
                if (customNode != null) {
                    info.put("s", "C"); // customized key
                    info.put("t", customNode.asText());
                } else if (translatedNode != null) {
                    info.put("t", translatedNode.asText());
                    info.put("s", "T"); // translated key
                } else {
                    info.put("s", "U"); // untranslated key
                }
                //check if key added on current level
                if (parentTranslatedNode == null && parentOriginNode == null) {
                    info.put("s", "A"); // added key
                }
                ((ObjectNode) fullTranslation).set(fieldName, info);
            }
        }
    }

    private JsonNode getTranslatedOnly(TenantId tenantId, CustomerId customerId, String localeCode, JsonNode resourceTranslation) {
        JsonNode customTranslation = getMergedCustomTranslation(tenantId, customerId, localeCode);
        if (resourceTranslation != null) {
            return merge(resourceTranslation, customTranslation);
        } else {
            return customTranslation;
        }
    }

    private JsonNode getParentTranslatedOnly(TenantId tenantId, CustomerId customerId, String localeCode, JsonNode resourceTranslation) {
        JsonNode parentTranslation;
        if (customerId != null && !customerId.isNullUid()) {
            Customer customer = customerService.findCustomerById(tenantId, customerId);
            if (customer.isSubCustomer()) {
                parentTranslation = getTranslatedOnly(tenantId, customer.getParentCustomerId(), localeCode, resourceTranslation);
            } else {
                parentTranslation = getTranslatedOnly(tenantId, null, localeCode, resourceTranslation);
            }
        } else {
            if (tenantId.isSysTenantId()) {
                parentTranslation = resourceTranslation;
            } else {
                parentTranslation = getTranslatedOnly(TenantId.SYS_TENANT_ID, null, localeCode, resourceTranslation);
            }
        }
        return parentTranslation;
    }

    private void addNonExisting(JsonNode mainNode, JsonNode newNode) {
        Iterator<String> fieldNames = newNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode jsonNode = mainNode.get(fieldName);
            if (jsonNode != null) {
                if (jsonNode.isObject()) {
                    addNonExisting(jsonNode, newNode.get(fieldName));
                } else if (jsonNode.isArray()) {
                    for (int i = 0; i < jsonNode.size(); i++) {
                        addNonExisting(jsonNode.get(i), newNode.get(fieldName).get(i));
                    }
                }
            } else {
                if (mainNode instanceof ObjectNode) {
                    // Overwrite field
                    JsonNode value = newNode.get(fieldName);
                    if (value.isNull()) {
                        continue;
                    }
                    ((ObjectNode) mainNode).set(fieldName, value);
                }
            }
        }
    }

    private void evictFromCache(TenantId tenantId) {
        evictETags(TranslationCacheKey.forTenant(tenantId));
        clusterService.broadcastToCore(TransportProtos.ToCoreNotificationMsg.newBuilder()
                .setTranslationCacheInvalidateMsg(TransportProtos.TranslationCacheInvalidateMsg.newBuilder()
                        .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                        .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                        .build())
                .build());
    }

    @Override
    public void evictETags(TranslationCacheKey cacheKey) {
        TenantId tenantId = cacheKey.getTenantId();
        if (tenantId.isSysTenantId()) {
            etagCache.invalidateAll();
        } else {
            invalidateByFilter(key -> tenantId.equals(key.getTenantId()));
        }
    }

}
