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
package org.thingsboard.server.dao.sql.rule;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.dao.AbstractJpaDaoTest;
import org.thingsboard.server.dao.rule.RuleChainDao;
import org.thingsboard.server.dao.rule.RuleNodeDao;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;

public class JpaRuleNodeDaoTest extends AbstractJpaDaoTest {

    public static final int COUNT = 40;
    public static final String PREFIX_FOR_RULE_NODE_NAME = "SEARCH_TEXT_";
    List<UUID> ruleNodeIds;
    TenantId tenantId1;
    TenantId tenantId2;
    RuleChainId ruleChainId1;
    RuleChainId ruleChainId2;

    @Autowired
    private RuleChainDao ruleChainDao;

    @Autowired
    private RuleNodeDao ruleNodeDao;

    ListeningExecutorService executor;

    @Before
    public void setUp() {
        tenantId1 = TenantId.fromUUID(Uuids.timeBased());
        ruleChainId1 = new RuleChainId(UUID.randomUUID());
        tenantId2 = TenantId.fromUUID(Uuids.timeBased());
        ruleChainId2 = new RuleChainId(UUID.randomUUID());

        ruleNodeIds = createRuleNodes(tenantId1, tenantId2, ruleChainId1, ruleChainId2, COUNT);
    }

    @After
    public void tearDown() throws Exception {
        ruleNodeDao.removeAllByIds(ruleNodeIds);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    public void testSaveRuleName0x00_thenSomeDatabaseException() {
        RuleNode ruleNode = getRuleNode(ruleChainId1, "T", "\u0000");
        assertThatThrownBy(() -> ruleNodeIds.add(ruleNodeDao.save(tenantId1, ruleNode).getUuidId()));
    }

    @Test
    public void testFindRuleNodesByTenantIdAndType() {
        List<RuleNode> ruleNodes1 = ruleNodeDao.findRuleNodesByTenantIdAndType(tenantId1, "A", PREFIX_FOR_RULE_NODE_NAME);
        assertEquals(20, ruleNodes1.size());

        List<RuleNode> ruleNodes2 = ruleNodeDao.findRuleNodesByTenantIdAndType(tenantId2, "B", PREFIX_FOR_RULE_NODE_NAME);
        assertEquals(20, ruleNodes2.size());

        ruleNodes1 = ruleNodeDao.findRuleNodesByTenantIdAndType(tenantId1, "A", null);
        assertEquals(20, ruleNodes1.size());

        ruleNodes2 = ruleNodeDao.findRuleNodesByTenantIdAndType(tenantId2, "B", null);
        assertEquals(20, ruleNodes2.size());
    }

    @Test
    public void testFindRuleNodesByType() {
        PageData<RuleNode> ruleNodes = ruleNodeDao.findAllRuleNodesByType( "A", new PageLink(10, 0, PREFIX_FOR_RULE_NODE_NAME));
        assertEquals(20, ruleNodes.getTotalElements());
        assertEquals(2, ruleNodes.getTotalPages());
        assertEquals(10, ruleNodes.getData().size());

        ruleNodes = ruleNodeDao.findAllRuleNodesByType( "A", new PageLink(10, 0));
        assertEquals(20, ruleNodes.getTotalElements());
        assertEquals(2, ruleNodes.getTotalPages());
        assertEquals(10, ruleNodes.getData().size());
    }

    @Test
    public void testFindRuleNodesByTypeAndVersionLessThan() {
        PageData<RuleNode> ruleNodes = ruleNodeDao.findAllRuleNodesByTypeAndVersionLessThan( "A", 1, new PageLink(10, 0, PREFIX_FOR_RULE_NODE_NAME));
        assertEquals(20, ruleNodes.getTotalElements());
        assertEquals(2, ruleNodes.getTotalPages());
        assertEquals(10, ruleNodes.getData().size());

        ruleNodes = ruleNodeDao.findAllRuleNodesByTypeAndVersionLessThan( "A", 1, new PageLink(10, 0));
        assertEquals(20, ruleNodes.getTotalElements());
        assertEquals(2, ruleNodes.getTotalPages());
        assertEquals(10, ruleNodes.getData().size());
    }

    @Test
    public void testFindRuleNodeIdsByTypeAndVersionLessThan() {
        // test - search text ignored
        PageData<RuleNodeId> ruleNodeIds = ruleNodeDao.findAllRuleNodeIdsByTypeAndVersionLessThan( "A", 1, new PageLink(10, 0, PREFIX_FOR_RULE_NODE_NAME));
        assertEquals(0, ruleNodeIds.getTotalElements()); // due to DaoUtil.pageToPageData impl for Slice
        assertEquals(0, ruleNodeIds.getTotalPages()); // due to DaoUtil.pageToPageData impl for Slice
        assertEquals(10, ruleNodeIds.getData().size());

        ruleNodeIds = ruleNodeDao.findAllRuleNodeIdsByTypeAndVersionLessThan( "A", 1, new PageLink(10, 0));
        assertEquals(0, ruleNodeIds.getTotalElements()); // due to DaoUtil.pageToPageData impl for Slice
        assertEquals(0, ruleNodeIds.getTotalPages()); // due to DaoUtil.pageToPageData impl for Slice
        assertEquals(10, ruleNodeIds.getData().size());
    }

    @Test
    public void testFindAllRuleNodeByIds() {
        var fromUUIDs = ruleNodeIds.stream().map(RuleNodeId::new).collect(Collectors.toList());
        var ruleNodes = ruleNodeDao.findAllRuleNodeByIds(fromUUIDs);
        assertEquals(40, ruleNodes.size());
    }

    private List<UUID> createRuleNodes(TenantId tenantId1, TenantId tenantId2, RuleChainId ruleChainId1, RuleChainId ruleChainId2, int count) {
        return createRuleNodes(tenantId1, tenantId2, ruleChainId1, ruleChainId2, "A", "B", count);
    }

    private List<UUID> createRuleNodes(TenantId tenantId1, TenantId tenantId2,
                                       RuleChainId ruleChainId1, RuleChainId ruleChainId2,
                                       String typeA, String typeB, int count) {
        var chain1 = new RuleChain(ruleChainId1);
        chain1.setTenantId(tenantId1);
        chain1.setName(ruleChainId1.toString());
        ruleChainDao.save(tenantId1, chain1);
        var chain2 = new RuleChain(ruleChainId2);
        chain2.setTenantId(tenantId2);
        chain2.setName(ruleChainId2.toString());
        ruleChainDao.save(tenantId2, chain2);
        List<UUID> savedRuleNodeIds = new ArrayList<>();
        for (int i = 0; i < count / 2; i++) {
            savedRuleNodeIds.add(ruleNodeDao.save(tenantId1, getRuleNode(ruleChainId1, typeA, Integer.toString(i))).getUuidId());
            savedRuleNodeIds.add(ruleNodeDao.save(tenantId2, getRuleNode(ruleChainId2, typeB, Integer.toString(i + count / 2))).getUuidId());
        }
        return savedRuleNodeIds;
    }

    private RuleNode getRuleNode(RuleChainId ruleChainId, String type, String nameSuffix) {
        return getRuleNode(ruleChainId, Uuids.timeBased(), type, nameSuffix);
    }

    private RuleNode getRuleNode(RuleChainId ruleChainId, UUID ruleNodeId, String type, String nameSuffix) {
        RuleNode ruleNode = new RuleNode();
        ruleNode.setId(new RuleNodeId(ruleNodeId));
        ruleNode.setRuleChainId(ruleChainId);
        ruleNode.setName(nameSuffix);
        ruleNode.setType(type);
        ruleNode.setConfiguration(JacksonUtil.newObjectNode().put("searchHint", PREFIX_FOR_RULE_NODE_NAME + nameSuffix));
        ruleNode.setConfigurationVersion(0);
        return ruleNode;
    }
}
