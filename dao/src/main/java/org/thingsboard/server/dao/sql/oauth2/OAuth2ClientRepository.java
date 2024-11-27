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
package org.thingsboard.server.dao.sql.oauth2;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.thingsboard.server.dao.model.sql.OAuth2ClientEntity;

import java.util.List;
import java.util.UUID;

public interface OAuth2ClientRepository extends JpaRepository<OAuth2ClientEntity, UUID> {

    @Query("SELECT с FROM OAuth2ClientEntity с WHERE с.tenantId = :tenantId AND с.customerId = :customerId AND " +
            "(:searchText is NULL OR ilike(с.title, concat('%', :searchText, '%')) = true)")
    Page<OAuth2ClientEntity> findByTenantIdAndCustomerId(@Param("tenantId") UUID tenantId,
                                                         @Param("customerId") UUID customerId,
                                                         @Param("searchText") String searchText,
                                                         Pageable pageable);

    @Query("SELECT c " +
            "FROM OAuth2ClientEntity c " +
            "LEFT JOIN DomainOauth2ClientEntity dc ON c.id = dc.oauth2ClientId " +
            "LEFT JOIN DomainEntity domain ON dc.domainId = domain.id " +
            "WHERE domain.name = :domainName AND domain.oauth2Enabled = true " +
            "AND (:platformFilter IS NULL OR c.platforms IS NULL OR c.platforms = '' OR ilike(c.platforms, CONCAT('%', :platformFilter, '%')) = true)")
    List<OAuth2ClientEntity> findEnabledByDomainNameAndPlatformType(@Param("domainName") String domainName,
                                                                    @Param("platformFilter") String platformFilter);

    @Query("SELECT c " +
            "FROM OAuth2ClientEntity c " +
            "LEFT JOIN MobileAppBundleOauth2ClientEntity ac ON c.id = ac.oauth2ClientId " +
            "LEFT JOIN MobileAppBundleEntity b ON ac.mobileAppBundleId = b.id " +
            "LEFT JOIN MobileAppEntity andApp ON b.androidAppId = andApp.id LEFT JOIN MobileAppEntity iosApp ON b.iosAppID = iosApp.id " +
            "WHERE andApp.pkgName = :pkgName OR iosApp.pkgName = :pkgName AND b.oauth2Enabled = true " +
            "AND (:platformFilter IS NULL OR c.platforms IS NULL OR c.platforms = '' OR ilike(c.platforms, CONCAT('%', :platformFilter, '%')) = true)")
    List<OAuth2ClientEntity> findEnabledByPkgNameAndPlatformType(@Param("pkgName") String pkgName,
                                                                 @Param("platformFilter") String platformFilter);

    @Query("SELECT c " +
            "FROM OAuth2ClientEntity c " +
            "LEFT JOIN DomainOauth2ClientEntity dc ON dc.oauth2ClientId = c.id " +
            "WHERE dc.domainId = :domainId ")
    List<OAuth2ClientEntity> findByDomainId(@Param("domainId") UUID domainId);

    @Query("SELECT c " +
            "FROM OAuth2ClientEntity c " +
            "LEFT JOIN MobileAppBundleOauth2ClientEntity bc ON bc.oauth2ClientId = c.id " +
            "WHERE bc.mobileAppBundleId = :mobileAppBundleId ")
    List<OAuth2ClientEntity> findByMobileAppBundleId(@Param("mobileAppBundleId") UUID mobileAppBundleId);

    @Query("SELECT a.appSecret " +
            "FROM MobileAppEntity a " +
            "LEFT JOIN MobileAppBundleEntity b ON (b.androidAppId = a.id OR b.iosAppID = a.id) " +
            "LEFT JOIN MobileAppBundleOauth2ClientEntity bc ON bc.mobileAppBundleId = b.id " +
            "LEFT JOIN OAuth2ClientEntity c ON bc.oauth2ClientId = c.id " +
            "WHERE c.id = :clientId " +
            "AND a.pkgName = :pkgName and a.platformType = :platformType")
    String findAppSecret(@Param("clientId") UUID id,
                         @Param("pkgName") String pkgName,
                         @Param("platformType") String platformType);

    @Transactional
    @Modifying
    @Query("DELETE FROM OAuth2ClientEntity t WHERE t.tenantId = :tenantId")
    void deleteByTenantId(@Param("tenantId") UUID tenantId);

    List<OAuth2ClientEntity> findByTenantIdAndIdIn(UUID tenantId, List<UUID> uuids);

    @Query("SELECT COUNT(d) > 0 FROM DomainEntity d " +
            "JOIN DomainOauth2ClientEntity doc ON d.id = doc.domainId " +
            "WHERE d.tenantId = :tenantId AND doc.oauth2ClientId = :oAuth2ClientId AND d.propagateToEdge = true")
    boolean isPropagateToEdge(@Param("tenantId") UUID tenantId, @Param("oAuth2ClientId") UUID oAuth2ClientId);

}
