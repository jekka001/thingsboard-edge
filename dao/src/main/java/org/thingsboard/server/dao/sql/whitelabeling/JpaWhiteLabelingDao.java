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
package org.thingsboard.server.dao.sql.whitelabeling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.page.SortOrder;
import org.thingsboard.server.common.data.wl.WhiteLabeling;
import org.thingsboard.server.common.data.wl.WhiteLabelingType;
import org.thingsboard.server.dao.DaoUtil;
import org.thingsboard.server.dao.model.sql.WhiteLabelingCompositeKey;
import org.thingsboard.server.dao.model.sql.WhiteLabelingEntity;
import org.thingsboard.server.dao.sql.JpaAbstractDaoListeningExecutorService;
import org.thingsboard.server.dao.util.SqlDao;
import org.thingsboard.server.dao.wl.WhiteLabelingDao;

import java.util.List;
import java.util.Set;

@Component
@Slf4j
@SqlDao
public class JpaWhiteLabelingDao extends JpaAbstractDaoListeningExecutorService implements WhiteLabelingDao {

    @Autowired
    private WhiteLabelingRepository whiteLabelingRepository;

    @Override
    public WhiteLabeling save(TenantId tenantId, WhiteLabeling whiteLabeling) {
        return DaoUtil.getData(whiteLabelingRepository.save(new WhiteLabelingEntity(whiteLabeling)));
    }

    @Override
    public WhiteLabeling findById(TenantId tenantId, WhiteLabelingCompositeKey key) {
        return DaoUtil.getData(whiteLabelingRepository.findById(key));
    }

    @Override
    public WhiteLabeling findByDomainAndType(TenantId tenantId, String domain, WhiteLabelingType type) {
        return DaoUtil.getData(whiteLabelingRepository.findByDomainAndType(domain, type));
    }

    @Override
    public void removeById(TenantId tenantId, WhiteLabelingCompositeKey key) {
        whiteLabelingRepository.deleteById(key);
    }

    @Override
    public List<WhiteLabeling> findByTenantAndImageLink(TenantId tenantId, String imageLink, int limit) {
        return DaoUtil.convertDataList(whiteLabelingRepository.findByTenantAndImageLink(tenantId.getId(), imageLink, limit));
    }

    @Override
    public List<WhiteLabeling> findByImageLink(String imageLink, int limit) {
        return DaoUtil.convertDataList(whiteLabelingRepository.findByImageLink(imageLink, limit));
    }

    @Override
    public PageData<WhiteLabeling> findAllByType(PageLink pageLink, Set<WhiteLabelingType> types) {
        return DaoUtil.toPageData(whiteLabelingRepository.findAllByTypeIn(types, DaoUtil.toPageable(pageLink, List.of(
                new SortOrder("tenantId"), new SortOrder("customerId"), new SortOrder("type")
        ))));
    }


}
