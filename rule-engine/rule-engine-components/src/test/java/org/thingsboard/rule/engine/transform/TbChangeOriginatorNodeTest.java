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
package org.thingsboard.rule.engine.transform;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.TestDbCallbackExecutor;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.AssetId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.asset.AssetService;

import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TbChangeOriginatorNodeTest {

    private static final String CUSTOMER_SOURCE = "CUSTOMER";

    private TbChangeOriginatorNode node;

    @Mock
    private TbContext ctx;
    @Mock
    private AssetService assetService;

    private ListeningExecutor dbExecutor;

    @Before
    public void before() {
        dbExecutor = new TestDbCallbackExecutor();
    }

    @Test
    public void originatorCanBeChangedToCustomerId() throws TbNodeException {
        init();
        AssetId assetId = new AssetId(Uuids.timeBased());
        CustomerId customerId = new CustomerId(Uuids.timeBased());
        Asset asset = new Asset();
        asset.setCustomerId(customerId);

        RuleChainId ruleChainId = new RuleChainId(Uuids.timeBased());
        RuleNodeId ruleNodeId = new RuleNodeId(Uuids.timeBased());

        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, assetId, TbMsgMetaData.EMPTY, TbMsgDataType.JSON, TbMsg.EMPTY_JSON_OBJECT, ruleChainId, ruleNodeId);

        when(ctx.getAssetService()).thenReturn(assetService);
        when(assetService.findAssetByIdAsync(any(),eq( assetId))).thenReturn(Futures.immediateFuture(asset));

        node.onMsg(ctx, msg);

        ArgumentCaptor<TbMsg> msgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        ArgumentCaptor<EntityId> originatorCaptor = ArgumentCaptor.forClass(EntityId.class);
        verify(ctx).transformMsgOriginator(msgCaptor.capture(), originatorCaptor.capture());

        assertEquals(customerId, originatorCaptor.getValue());
    }

    @Test
    public void newChainCanBeStarted() throws TbNodeException {
        init();
        AssetId assetId = new AssetId(Uuids.timeBased());
        CustomerId customerId = new CustomerId(Uuids.timeBased());
        Asset asset = new Asset();
        asset.setCustomerId(customerId);

        RuleChainId ruleChainId = new RuleChainId(Uuids.timeBased());
        RuleNodeId ruleNodeId = new RuleNodeId(Uuids.timeBased());

        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, assetId, TbMsgMetaData.EMPTY, TbMsgDataType.JSON,TbMsg.EMPTY_JSON_OBJECT, ruleChainId, ruleNodeId);

        when(ctx.getAssetService()).thenReturn(assetService);
        when(assetService.findAssetByIdAsync(any(), eq(assetId))).thenReturn(Futures.immediateFuture(asset));

        node.onMsg(ctx, msg);
        ArgumentCaptor<TbMsg> msgCaptor = ArgumentCaptor.forClass(TbMsg.class);
        ArgumentCaptor<EntityId> originatorCaptor = ArgumentCaptor.forClass(EntityId.class);
        verify(ctx).transformMsgOriginator(msgCaptor.capture(), originatorCaptor.capture());

        assertEquals(customerId, originatorCaptor.getValue());
    }

    @Test
    public void exceptionThrownIfCannotFindNewOriginator() throws TbNodeException {
        init();
        AssetId assetId = new AssetId(Uuids.timeBased());
        CustomerId customerId = new CustomerId(Uuids.timeBased());
        Asset asset = new Asset();
        asset.setCustomerId(customerId);

        RuleChainId ruleChainId = new RuleChainId(Uuids.timeBased());
        RuleNodeId ruleNodeId = new RuleNodeId(Uuids.timeBased());

        TbMsg msg = TbMsg.newMsg(TbMsgType.POST_TELEMETRY_REQUEST, assetId, TbMsgMetaData.EMPTY, TbMsgDataType.JSON,TbMsg.EMPTY_JSON_OBJECT, ruleChainId, ruleNodeId);

        when(ctx.getAssetService()).thenReturn(assetService);
        when(assetService.findAssetByIdAsync(any(), eq(assetId))).thenReturn(Futures.immediateFuture(null));

        ArgumentCaptor<NoSuchElementException> exceptionCaptor = ArgumentCaptor.forClass(NoSuchElementException.class);

        node.onMsg(ctx, msg);
        verify(ctx).tellFailure(same(msg), exceptionCaptor.capture());

        assertEquals("Failed to find new originator!", exceptionCaptor.getValue().getMessage());
    }

    public void init() throws TbNodeException {
        TbChangeOriginatorNodeConfiguration config = new TbChangeOriginatorNodeConfiguration();
        config.setOriginatorSource(CUSTOMER_SOURCE);
        TbNodeConfiguration nodeConfiguration = new TbNodeConfiguration(JacksonUtil.valueToTree(config));

        when(ctx.getDbCallbackExecutor()).thenReturn(dbExecutor);

        node = new TbChangeOriginatorNode();
        node.init(null, nodeConfiguration);
    }
}
