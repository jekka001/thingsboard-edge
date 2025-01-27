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
package org.thingsboard.server.dao.sql.widget;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ObjectType;
import org.thingsboard.server.common.data.edqs.fields.UserFields;
import org.thingsboard.server.common.data.edqs.fields.WidgetTypeFields;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.widget.DeprecatedFilter;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.common.data.widget.WidgetTypeFilter;
import org.thingsboard.server.common.data.widget.WidgetTypeInfo;
import org.thingsboard.server.common.data.widget.WidgetsBundleWidget;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.TenantEntityDao;
import org.thingsboard.server.dao.model.sql.WidgetTypeDetailsEntity;
import org.thingsboard.server.dao.model.sql.WidgetTypeInfoEntity;
import org.thingsboard.server.dao.model.sql.WidgetsBundleWidgetCompositeKey;
import org.thingsboard.server.dao.model.sql.WidgetsBundleWidgetEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDao;
import org.thingsboard.server.dao.util.SqlDao;
import org.thingsboard.server.dao.widget.WidgetTypeDao;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.model.ModelConstants.NULL_UUID;

/**
 * Created by Valerii Sosliuk on 4/29/2017.
 */
@Component
@SqlDao
public class JpaWidgetTypeDao extends JpaAbstractDao<WidgetTypeDetailsEntity, WidgetTypeDetails> implements WidgetTypeDao, TenantEntityDao<WidgetTypeDetails> {

    @Autowired
    private WidgetTypeRepository widgetTypeRepository;

    @Autowired
    private WidgetTypeInfoRepository widgetTypeInfoRepository;

    @Autowired
    private WidgetsBundleWidgetRepository widgetsBundleWidgetRepository;

    @Override
    protected Class<WidgetTypeDetailsEntity> getEntityClass() {
        return WidgetTypeDetailsEntity.class;
    }

    @Override
    protected JpaRepository<WidgetTypeDetailsEntity, UUID> getRepository() {
        return widgetTypeRepository;
    }

    @Override
    public WidgetType findWidgetTypeById(TenantId tenantId, UUID widgetTypeId) {
        return DaoUtil.getData(widgetTypeRepository.findWidgetTypeById(widgetTypeId));
    }

    @Override
    public boolean existsByTenantIdAndId(TenantId tenantId, UUID widgetTypeId) {
        return widgetTypeRepository.existsByTenantIdAndId(tenantId.getId(), widgetTypeId);
    }

    @Override
    public PageData<WidgetTypeInfo> findSystemWidgetTypes(WidgetTypeFilter widgetTypeFilter, PageLink pageLink) {
        boolean deprecatedFilterEnabled = !DeprecatedFilter.ALL.equals(widgetTypeFilter.getDeprecatedFilter());
        boolean deprecatedFilterBool = DeprecatedFilter.DEPRECATED.equals(widgetTypeFilter.getDeprecatedFilter());
        boolean widgetTypesEmpty = widgetTypeFilter.getWidgetTypes() == null || widgetTypeFilter.getWidgetTypes().isEmpty();
        return DaoUtil.toPageData(
                widgetTypeInfoRepository
                        .findSystemWidgetTypes(
                                NULL_UUID,
                                pageLink.getTextSearch(),
                                widgetTypeFilter.isFullSearch(),
                                deprecatedFilterEnabled,
                                deprecatedFilterBool,
                                widgetTypesEmpty,
                                widgetTypeFilter.getWidgetTypes() == null ? Collections.emptyList() : widgetTypeFilter.getWidgetTypes(),
                                widgetTypeFilter.isScadaFirst(),
                                DaoUtil.toPageable(pageLink, WidgetTypeInfoEntity.SEARCH_COLUMNS_MAP)));
    }

    @Override
    public PageData<WidgetTypeInfo> findAllTenantWidgetTypesByTenantId(WidgetTypeFilter widgetTypeFilter, PageLink pageLink) {
        boolean deprecatedFilterEnabled = !DeprecatedFilter.ALL.equals(widgetTypeFilter.getDeprecatedFilter());
        boolean deprecatedFilterBool = DeprecatedFilter.DEPRECATED.equals(widgetTypeFilter.getDeprecatedFilter());
        boolean widgetTypesEmpty = widgetTypeFilter.getWidgetTypes() == null || widgetTypeFilter.getWidgetTypes().isEmpty();
        return DaoUtil.toPageData(
                widgetTypeInfoRepository
                        .findAllTenantWidgetTypesByTenantId(
                                widgetTypeFilter.getTenantId().getId(),
                                NULL_UUID,
                                pageLink.getTextSearch(),
                                widgetTypeFilter.isFullSearch(),
                                deprecatedFilterEnabled,
                                deprecatedFilterBool,
                                widgetTypesEmpty,
                                widgetTypeFilter.getWidgetTypes() == null ? Collections.emptyList() : widgetTypeFilter.getWidgetTypes(),
                                widgetTypeFilter.isScadaFirst(),
                                DaoUtil.toPageable(pageLink, WidgetTypeInfoEntity.SEARCH_COLUMNS_MAP)));
    }

    @Override
    public PageData<WidgetTypeInfo> findTenantWidgetTypesByTenantId(WidgetTypeFilter widgetTypeFilter, PageLink pageLink) {
        boolean deprecatedFilterEnabled = !DeprecatedFilter.ALL.equals(widgetTypeFilter.getDeprecatedFilter());
        boolean deprecatedFilterBool = DeprecatedFilter.DEPRECATED.equals(widgetTypeFilter.getDeprecatedFilter());
        boolean widgetTypesEmpty = widgetTypeFilter.getWidgetTypes() == null || widgetTypeFilter.getWidgetTypes().isEmpty();
        return DaoUtil.toPageData(
                widgetTypeInfoRepository
                        .findTenantWidgetTypesByTenantId(
                                widgetTypeFilter.getTenantId().getId(),
                                pageLink.getTextSearch(),
                                widgetTypeFilter.isFullSearch(),
                                deprecatedFilterEnabled,
                                deprecatedFilterBool,
                                widgetTypesEmpty,
                                widgetTypeFilter.getWidgetTypes() == null ? Collections.emptyList() : widgetTypeFilter.getWidgetTypes(),
                                widgetTypeFilter.isScadaFirst(),
                                DaoUtil.toPageable(pageLink, WidgetTypeInfoEntity.SEARCH_COLUMNS_MAP)));
    }

    @Override
    public List<WidgetType> findWidgetTypesByWidgetsBundleId(UUID tenantId, UUID widgetsBundleId) {
        return DaoUtil.convertDataList(widgetTypeRepository.findWidgetTypesByWidgetsBundleId(widgetsBundleId));
    }

    @Override
    public List<WidgetTypeDetails> findWidgetTypesDetailsByWidgetsBundleId(UUID tenantId, UUID widgetsBundleId) {
        return DaoUtil.convertDataList(widgetTypeRepository.findWidgetTypesDetailsByWidgetsBundleId(widgetsBundleId));
    }

    @Override
    public PageData<WidgetTypeInfo> findWidgetTypesInfosByWidgetsBundleId(UUID tenantId, UUID widgetsBundleId, boolean fullSearch, DeprecatedFilter deprecatedFilter, List<String> widgetTypes, PageLink pageLink) {
        boolean deprecatedFilterEnabled = !DeprecatedFilter.ALL.equals(deprecatedFilter);
        boolean deprecatedFilterBool = DeprecatedFilter.DEPRECATED.equals(deprecatedFilter);
        boolean widgetTypesEmpty = widgetTypes == null || widgetTypes.isEmpty();
        return DaoUtil.toPageData(
                widgetTypeInfoRepository
                        .findWidgetTypesInfosByWidgetsBundleId(
                                widgetsBundleId,
                                Objects.toString(pageLink.getTextSearch(), ""),
                                fullSearch,
                                deprecatedFilterEnabled,
                                deprecatedFilterBool,
                                widgetTypesEmpty,
                                widgetTypes == null ? Collections.emptyList() : widgetTypes,
                                DaoUtil.toPageable(pageLink, WidgetTypeInfoEntity.SEARCH_COLUMNS_MAP)));
    }

    @Override
    public List<String> findWidgetFqnsByWidgetsBundleId(UUID tenantId, UUID widgetsBundleId) {
        return widgetTypeRepository.findWidgetFqnsByWidgetsBundleId(widgetsBundleId);
    }

    @Override
    public WidgetType findByTenantIdAndFqn(UUID tenantId, String fqn) {
        return DaoUtil.getData(widgetTypeRepository.findWidgetTypeByTenantIdAndFqn(tenantId, fqn));
    }

    @Override
    public WidgetTypeDetails findDetailsByTenantIdAndFqn(UUID tenantId, String fqn) {
        return DaoUtil.getData(widgetTypeRepository.findByTenantIdAndFqn(tenantId, fqn));
    }

    @Override
    public List<String> findWidgetTypesNamesByTenantIdAndResourceLink(UUID tenantId, String link) {
        return widgetTypeRepository.findNamesByTenantIdAndResourceLink(tenantId, link);
    }

    @Override
    public List<WidgetTypeId> findWidgetTypeIdsByTenantIdAndFqns(UUID tenantId, List<String> widgetFqns) {
        var idFqnPairs = widgetTypeRepository.findWidgetTypeIdsByTenantIdAndFqns(tenantId, widgetFqns);
        idFqnPairs.sort(Comparator.comparingInt(o -> widgetFqns.indexOf(o.getFqn())));
        return idFqnPairs.stream()
                .map(id -> new WidgetTypeId(id.getId())).collect(Collectors.toList());
    }

    @Override
    public WidgetTypeDetails findByTenantIdAndExternalId(UUID tenantId, UUID externalId) {
        return DaoUtil.getData(widgetTypeRepository.findByTenantIdAndExternalId(tenantId, externalId));
    }

    @Override
    public PageData<WidgetTypeDetails> findByTenantId(UUID tenantId, PageLink pageLink) {
        return DaoUtil.toPageData(
                widgetTypeRepository
                        .findTenantWidgetTypeDetailsByTenantId(
                                tenantId,
                                pageLink.getTextSearch(),
                                DaoUtil.toPageable(pageLink)));
    }

    @Override
    public PageData<WidgetTypeId> findIdsByTenantId(UUID tenantId, PageLink pageLink) {
        return DaoUtil.pageToPageData(widgetTypeRepository.findIdsByTenantId(tenantId, DaoUtil.toPageable(pageLink))
                .map(WidgetTypeId::new));
    }

    @Override
    public WidgetTypeId getExternalIdByInternal(WidgetTypeId internalId) {
        return Optional.ofNullable(widgetTypeRepository.getExternalIdById(internalId.getId()))
                .map(WidgetTypeId::new).orElse(null);
    }

    @Override
    public List<WidgetsBundleWidget> findWidgetsBundleWidgetsByWidgetsBundleId(UUID tenantId, UUID widgetsBundleId) {
        return DaoUtil.convertDataList(widgetsBundleWidgetRepository.findAllByWidgetsBundleId(widgetsBundleId));
    }

    @Override
    public void saveWidgetsBundleWidget(WidgetsBundleWidget widgetsBundleWidget) {
        widgetsBundleWidgetRepository.save(new WidgetsBundleWidgetEntity(widgetsBundleWidget));
    }

    @Override
    public void removeWidgetTypeFromWidgetsBundle(UUID widgetsBundleId, UUID widgetTypeId) {
        widgetsBundleWidgetRepository.deleteById(new WidgetsBundleWidgetCompositeKey(widgetsBundleId, widgetTypeId));
    }

    @Override
    public PageData<WidgetTypeId> findAllWidgetTypesIds(PageLink pageLink) {
        return DaoUtil.pageToPageData(widgetTypeRepository.findAllIds(DaoUtil.toPageable(pageLink)).map(WidgetTypeId::new));
    }

    @Override
    public List<WidgetTypeInfo> findByTenantAndImageLink(TenantId tenantId, String imageUrl, int limit) {
        return DaoUtil.convertDataList(widgetTypeInfoRepository.findByTenantAndImageUrl(tenantId.getId(), imageUrl, limit));
    }

    @Override
    public List<WidgetTypeInfo> findByImageLink(String imageUrl, int limit) {
        return DaoUtil.convertDataList(widgetTypeInfoRepository.findByImageUrl(imageUrl, limit));
    }

    @Override
    public PageData<WidgetTypeDetails> findAllByTenantId(TenantId tenantId, PageLink pageLink) {
        return findByTenantId(tenantId.getId(), pageLink);
    }

    @Override
    public PageData<WidgetTypeFields> findAllFields(PageLink pageLink) {
        return DaoUtil.pageToPageData(widgetTypeRepository.findAllFields(DaoUtil.toPageable(pageLink)));
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.WIDGET_TYPE;
    }

    @Override
    public ObjectType getType() {
        return ObjectType.WIDGET_TYPE;
    }

}
