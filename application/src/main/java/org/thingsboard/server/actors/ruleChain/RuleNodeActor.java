/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.actors.ruleChain;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.actors.TbActor;
import org.thingsboard.server.actors.TbActorCtx;
import org.thingsboard.server.actors.TbActorId;
import org.thingsboard.server.actors.TbEntityActorId;
import org.thingsboard.server.actors.service.ComponentActor;
import org.thingsboard.server.actors.service.ContextBasedCreator;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.RuleNodeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.msg.TbActorMsg;
import org.thingsboard.server.common.msg.plugin.ComponentLifecycleMsg;
import org.thingsboard.server.common.msg.queue.PartitionChangeMsg;

@Slf4j
public class RuleNodeActor extends ComponentActor<RuleNodeId, RuleNodeActorMessageProcessor> {

    private final String ruleChainName;
    private final RuleChainId ruleChainId;
    private final RuleNodeId ruleNodeId;

    private RuleNodeActor(ActorSystemContext systemContext, TenantId tenantId, RuleChainId ruleChainId, String ruleChainName, RuleNodeId ruleNodeId) {
        super(systemContext, tenantId, ruleNodeId);
        this.ruleChainName = ruleChainName;
        this.ruleChainId = ruleChainId;
        this.ruleNodeId = ruleNodeId;
    }

    @Override
    protected RuleNodeActorMessageProcessor createProcessor(TbActorCtx ctx) {
        return new RuleNodeActorMessageProcessor(tenantId, this.ruleChainName, ruleNodeId, systemContext, ctx.getParentRef(), ctx);
    }

    @Override
    protected boolean doProcess(TbActorMsg msg) {
        switch (msg.getMsgType()) {
            case COMPONENT_LIFE_CYCLE_MSG:
            case RULE_NODE_UPDATED_MSG:
                onComponentLifecycleMsg((ComponentLifecycleMsg) msg);
                break;
            case RULE_CHAIN_TO_RULE_MSG:
                onRuleChainToRuleNodeMsg((RuleChainToRuleNodeMsg) msg);
                break;
            case RULE_TO_SELF_MSG:
                onRuleNodeToSelfMsg((RuleNodeToSelfMsg) msg);
                break;
            case STATS_PERSIST_TICK_MSG:
                onStatsPersistTick(id);
                break;
            case PARTITION_CHANGE_MSG:
                onClusterEventMsg((PartitionChangeMsg) msg);
                break;
            default:
                return false;
        }
        return true;
    }

    private void onRuleNodeToSelfMsg(RuleNodeToSelfMsg msg) {
        if (log.isDebugEnabled()) {
            log.debug("[{}][{}][{}] Going to process rule msg: {}", ruleChainId, id, processor.getComponentName(), msg.getMsg());
        }
        try {
            processor.onRuleToSelfMsg(msg);
            increaseMessagesProcessedCount();
        } catch (Exception e) {
            logAndPersist("onRuleMsg", e);
        }
    }

    private void onRuleChainToRuleNodeMsg(RuleChainToRuleNodeMsg msg) {
        if (log.isDebugEnabled()) {
            log.debug("[{}][{}][{}] Going to process rule msg: {}", ruleChainId, id, processor.getComponentName(), msg.getMsg());
        }
        try {
            processor.onRuleChainToRuleNodeMsg(msg);
            increaseMessagesProcessedCount();
        } catch (Exception e) {
            logAndPersist("onRuleMsg", e);
        }
    }

    public static class ActorCreator extends ContextBasedCreator {

        private final TenantId tenantId;
        private final RuleChainId ruleChainId;
        private final String ruleChainName;
        private final RuleNodeId ruleNodeId;

        public ActorCreator(ActorSystemContext context, TenantId tenantId, RuleChainId ruleChainId, String ruleChainName, RuleNodeId ruleNodeId) {
            super(context);
            this.tenantId = tenantId;
            this.ruleChainId = ruleChainId;
            this.ruleChainName = ruleChainName;
            this.ruleNodeId = ruleNodeId;

        }

        @Override
        public TbActorId createActorId() {
            return new TbEntityActorId(ruleNodeId);
        }

        @Override
        public TbActor createActor() {
            return new RuleNodeActor(context, tenantId, ruleChainId, ruleChainName, ruleNodeId);
        }
    }

    @Override
    protected long getErrorPersistFrequency() {
        return systemContext.getRuleNodeErrorPersistFrequency();
    }

}
