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
package org.thingsboard.server.dao.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.HasId;
import org.thingsboard.server.common.data.id.MobileAppBundleId;
import org.thingsboard.server.common.data.id.OAuth2ClientId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.mobile.bundle.MobileAppBundle;
import org.thingsboard.server.common.data.mobile.bundle.MobileAppBundleInfo;
import org.thingsboard.server.common.data.mobile.bundle.MobileAppBundleOauth2Client;
import org.thingsboard.server.common.data.oauth2.OAuth2ClientInfo;
import org.thingsboard.server.common.data.oauth2.PlatformType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.entity.AbstractEntityService;
import org.thingsboard.server.dao.eventsourcing.DeleteEntityEvent;
import org.thingsboard.server.dao.eventsourcing.SaveEntityEvent;
import org.thingsboard.server.dao.oauth2.OAuth2ClientDao;
import org.thingsboard.server.dao.service.DataValidator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.service.Validator.checkNotNull;

@Slf4j
@Service
public class MobileAppBundleServiceImpl extends AbstractEntityService implements MobileAppBundleService {

    private static final String PLATFORM_TYPE_IS_REQUIRED = "Platform type is required if package name is specified";

    @Autowired
    private OAuth2ClientDao oauth2ClientDao;
    @Autowired
    private MobileAppBundleDao mobileAppBundleDao;
    @Autowired
    private MobileAppBundlePolicyInfoDao mobileAppBundlePolicyInfoDao;
    @Autowired
    private DataValidator<MobileAppBundle> mobileAppBundleDataValidator;

    @Override
    public MobileAppBundle saveMobileAppBundle(TenantId tenantId, MobileAppBundle mobileAppBundle) {
        log.trace("Executing saveMobileAppBundle [{}]", mobileAppBundle);
        mobileAppBundleDataValidator.validate(mobileAppBundle, b -> tenantId);
        try {
            MobileAppBundle savedMobileAppBundle = mobileAppBundlePolicyInfoDao.save(tenantId, mobileAppBundle);
            eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId).entity(savedMobileAppBundle).build());
            return savedMobileAppBundle;
        } catch (Exception e) {
            checkConstraintViolation(e,
                    Map.of("android_app_id_unq_key", "Android mobile app already exists in another bundle!",
                            "ios_app_id_unq_key", "IOS mobile app already exists in another bundle!"));
            throw e;
        }
    }

    @Override
    public void deleteMobileAppBundleById(TenantId tenantId, MobileAppBundleId mobileAppBundleId) {
        log.trace("Executing deleteMobileAppBundleById [{}]", mobileAppBundleId.getId());
        try {
            mobileAppBundleDao.removeById(tenantId, mobileAppBundleId.getId());
            eventPublisher.publishEvent(DeleteEntityEvent.builder().tenantId(tenantId).entityId(mobileAppBundleId).build());
        } catch (Exception e) {
            checkConstraintViolation(e, "fk_android_app_id", "The mobile app referenced by the mobile bundle cannot be deleted!",
                    "fk_ios_app_id", "The mobile app referenced by the mobile bundle cannot be deleted!");
            throw e;
        }
    }

    @Override
    public MobileAppBundle findMobileAppBundleById(TenantId tenantId, MobileAppBundleId mobileAppBundleId) {
        log.trace("Executing findMobileAppBundleById [{}] [{}]", tenantId, mobileAppBundleId);
        return mobileAppBundleDao.findById(tenantId, mobileAppBundleId.getId());
    }

    @Override
    public PageData<MobileAppBundleInfo> findMobileAppBundleInfosByTenantId(TenantId tenantId, PageLink pageLink) {
        log.trace("Executing findMobileAppBundleInfosByTenantId [{}]", tenantId);
        PageData<MobileAppBundleInfo> mobileBundles = mobileAppBundleDao.findInfosByTenantId(tenantId, pageLink);
        mobileBundles.getData().forEach(this::fetchOauth2Clients);
        return mobileBundles;
    }

    @Override
    public MobileAppBundleInfo findMobileAppBundleInfoById(TenantId tenantId, MobileAppBundleId mobileAppIdBundle) {
        log.trace("Executing findMobileAppBundleFullInfoById [{}] [{}]", tenantId, mobileAppIdBundle);
        MobileAppBundle mobileAppBundle = mobileAppBundlePolicyInfoDao.findById(tenantId, mobileAppIdBundle.getId());
        if (mobileAppBundle == null) {
            return null;
        }
        List<OAuth2ClientInfo> clients = oauth2ClientDao.findByMobileAppBundleId(mobileAppBundle.getUuidId()).stream()
                .map(OAuth2ClientInfo::new)
                .sorted(Comparator.comparing(OAuth2ClientInfo::getTitle))
                .collect(Collectors.toList());
        return new MobileAppBundleInfo(mobileAppBundle, clients);
    }

    @Override
    public void updateOauth2Clients(TenantId tenantId, MobileAppBundleId mobileAppBundleId, List<OAuth2ClientId> oAuth2ClientIds) {
        log.trace("Executing updateOauth2Clients, mobileAppId [{}], oAuth2ClientIds [{}]", mobileAppBundleId, oAuth2ClientIds);
        Set<MobileAppBundleOauth2Client> newClientList = oAuth2ClientIds.stream()
                .map(clientId -> new MobileAppBundleOauth2Client(mobileAppBundleId, clientId))
                .collect(Collectors.toSet());

        List<MobileAppBundleOauth2Client> existingClients = mobileAppBundleDao.findOauth2ClientsByMobileAppBundleId(tenantId, mobileAppBundleId);
        List<MobileAppBundleOauth2Client> toRemoveList = existingClients.stream()
                .filter(client -> !newClientList.contains(client))
                .toList();
        newClientList.removeIf(existingClients::contains);

        for (MobileAppBundleOauth2Client client : toRemoveList) {
            mobileAppBundleDao.removeOauth2Client(tenantId, client);
        }
        for (MobileAppBundleOauth2Client client : newClientList) {
            mobileAppBundleDao.addOauth2Client(tenantId, client);
        }
        eventPublisher.publishEvent(SaveEntityEvent.builder().tenantId(tenantId)
                .entityId(mobileAppBundleId).created(false).build());
    }

    @Override
    public MobileAppBundle findMobileAppBundleByPkgNameAndPlatform(TenantId tenantId, String pkgName, PlatformType platformType) {
        log.trace("Executing findMobileAppBundleByPkgNameAndPlatform, tenantId [{}], pkgName [{}], platform [{}]", tenantId, pkgName, platformType);
        checkNotNull(platformType, PLATFORM_TYPE_IS_REQUIRED);
        return mobileAppBundleDao.findByPkgNameAndPlatform(tenantId, pkgName, platformType);
    }

    @Override
    public Optional<HasId<?>> findEntity(TenantId tenantId, EntityId entityId) {
        return Optional.ofNullable(findMobileAppBundleById(tenantId, new MobileAppBundleId(entityId.getId())));
    }

    @Override
    @Transactional
    public void deleteEntity(TenantId tenantId, EntityId id, boolean force) {
        deleteMobileAppBundleById(tenantId, (MobileAppBundleId) id);
    }

    @Override
    public void deleteMobileAppBundlesByTenantId(TenantId tenantId) {
        log.trace("Executing deleteMobileAppsByTenantId, tenantId [{}]", tenantId);
        mobileAppBundleDao.deleteByTenantId(tenantId);
    }

    @Override
    public MobileAppBundle findMobileAppBundlePolicyInfoByPkgNameAndPlatform(TenantId tenantId, String pkgName, PlatformType platformType) {
        log.trace("Executing findMobileAppBundlePolicyInfoByPkgNameAndPlatform, tenantId [{}], pkgName [{}], platform [{}]", tenantId, pkgName, platformType);
        checkNotNull(platformType, PLATFORM_TYPE_IS_REQUIRED);
        return mobileAppBundlePolicyInfoDao.findPolicyInfoByPkgNameAndPlatform(tenantId, pkgName, platformType);
    }

    @Override
    public JsonNode findMobilePrivacyPolicy(TenantId tenantId, String pkgName, PlatformType platformType) {
        log.trace("Executing findMobilePrivacyPolicy, tenantId [{}], pkgName [{}], platform [{}]", tenantId, pkgName, platformType);
        checkNotNull(platformType, PLATFORM_TYPE_IS_REQUIRED);
        MobileAppBundle appBundle = findMobileAppBundlePolicyInfoByPkgNameAndPlatform(tenantId, pkgName, platformType);
        if (appBundle != null && appBundle.getSelfRegistrationParams() != null) {
            return JacksonUtil.toJsonNode(appBundle.getSelfRegistrationParams().getTermsOfUse());
        }
        return null;
    }

    @Override
    public JsonNode findMobileTermsOfUse(TenantId tenantId, String pkgName, PlatformType platformType) {
        log.trace("Executing findMobileTermsOfUse, tenantId [{}], pkgName [{}], platform [{}]", tenantId, pkgName, platformType);
        checkNotNull(platformType, PLATFORM_TYPE_IS_REQUIRED);
        MobileAppBundle appBundle = findMobileAppBundlePolicyInfoByPkgNameAndPlatform(tenantId, pkgName, platformType);
        if (appBundle != null && appBundle.getSelfRegistrationParams() != null) {
            return JacksonUtil.toJsonNode(appBundle.getSelfRegistrationParams().getTermsOfUse());
        }
        return null;
    }

    @Override
    public void deleteByTenantId(TenantId tenantId) {
        deleteMobileAppBundlesByTenantId(tenantId);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.MOBILE_APP_BUNDLE;
    }

    private void fetchOauth2Clients(MobileAppBundleInfo mobileAppBundleInfo) {
        List<OAuth2ClientInfo> clients = oauth2ClientDao.findByMobileAppBundleId(mobileAppBundleInfo.getUuidId()).stream()
                .map(OAuth2ClientInfo::new)
                .sorted(Comparator.comparing(OAuth2ClientInfo::getTitle))
                .collect(Collectors.toList());
        mobileAppBundleInfo.setOauth2ClientInfos(clients);
    }

}
