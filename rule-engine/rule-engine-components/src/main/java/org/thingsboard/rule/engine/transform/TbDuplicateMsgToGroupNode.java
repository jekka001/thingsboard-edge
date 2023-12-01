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

import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.EntityGroupId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.List;

@Slf4j
@RuleNode(
        type = ComponentType.TRANSFORMATION,
        name = "duplicate to group",
        configClazz = TbDuplicateMsgToGroupNodeConfiguration.class,
        nodeDescription = "Duplicates message to all entities belonging to specific entity group",
        nodeDetails = "Entities are fetched from entity group that is detected according to the configuration. " +
                "Entity group can be specified directly or can be message originator entity itself. " +
                "For each entity from group new message is created with entity as originator " +
                "and message parameters copied from original message.<br><br>" +
                "Output connections: <code>Success</code>, <code>Failure</code>.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbTransformationNodeDuplicateToGroupConfig",
        icon = "call_split"
)
public class TbDuplicateMsgToGroupNode extends TbAbstractDuplicateMsgNode<TbDuplicateMsgToGroupNodeConfiguration> {

    @Override
    protected TbDuplicateMsgToGroupNodeConfiguration loadNodeConfiguration(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        var config = TbNodeUtils.convert(configuration, TbDuplicateMsgToGroupNodeConfiguration.class);
        if (!config.isEntityGroupIsMessageOriginator()) {
            if (config.getEntityGroupId() == null || config.getEntityGroupId().isNullUid()) {
                throw new IllegalArgumentException("EntityGroupId should be specified!");
            }
            ctx.checkTenantEntity(config.getEntityGroupId());
        }
        return config;
    }

    @Override
    protected ListenableFuture<List<TbMsg>> transform(TbContext ctx, TbMsg msg) {
        return duplicate(ctx, msg);
    }

    @Override
    protected ListenableFuture<List<EntityId>> getNewOriginators(TbContext ctx, EntityId original) {
        return ctx.getPeContext().getEntityGroupService().findAllEntityIdsAsync(ctx.getTenantId(), detectTargetEntityGroupId(original), new PageLink(Integer.MAX_VALUE));
    }

    private EntityGroupId detectTargetEntityGroupId(EntityId original) {
        if (config.isEntityGroupIsMessageOriginator()) {
            if (EntityType.ENTITY_GROUP.equals(original.getEntityType())) {
                return new EntityGroupId(original.getId());
            } else {
                throw new RuntimeException("Message originator is not an entity group!");
            }
        } else {
            return config.getEntityGroupId();
        }
    }

}
