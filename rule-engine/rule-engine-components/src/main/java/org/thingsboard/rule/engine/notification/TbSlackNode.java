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
package org.thingsboard.rule.engine.notification;

import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.external.TbAbstractExternalNode;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.concurrent.ExecutionException;

@RuleNode(
        type = ComponentType.EXTERNAL,
        name = "send to slack",
        configClazz = TbSlackNodeConfiguration.class,
        nodeDescription = "Send message via Slack",
        nodeDetails = "Sends message to a Slack channel or user",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbExternalNodeSlackConfig",
        iconUrl = "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZD0iTTYsMTVBMiwyIDAgMCwxIDQsMTdBMiwyIDAgMCwxIDIsMTVBMiwyIDAgMCwxIDQsMTNINlYxNU03LDE1QTIsMiAwIDAsMSA5LDEzQTIsMiAwIDAsMSAxMSwxNVYyMEEyLDIgMCAwLDEgOSwyMkEyLDIgMCAwLDEgNywyMFYxNU05LDdBMiwyIDAgMCwxIDcsNUEyLDIgMCAwLDEgOSwzQTIsMiAwIDAsMSAxMSw1VjdIOU05LDhBMiwyIDAgMCwxIDExLDEwQTIsMiAwIDAsMSA5LDEySDRBMiwyIDAgMCwxIDIsMTBBMiwyIDAgMCwxIDQsOEg5TTE3LDEwQTIsMiAwIDAsMSAxOSw4QTIsMiAwIDAsMSAyMSwxMEEyLDIgMCAwLDEgMTksMTJIMTdWMTBNMTYsMTBBMiwyIDAgMCwxIDE0LDEyQTIsMiAwIDAsMSAxMiwxMFY1QTIsMiAwIDAsMSAxNCwzQTIsMiAwIDAsMSAxNiw1VjEwTTE0LDE4QTIsMiAwIDAsMSAxNiwyMEEyLDIgMCAwLDEgMTQsMjJBMiwyIDAgMCwxIDEyLDIwVjE4SDE0TTE0LDE3QTIsMiAwIDAsMSAxMiwxNUEyLDIgMCAwLDEgMTQsMTNIMTlBMiwyIDAgMCwxIDIxLDE1QTIsMiAwIDAsMSAxOSwxN0gxNFoiIC8+PC9zdmc+"
)
public class TbSlackNode extends TbAbstractExternalNode {

    private TbSlackNodeConfiguration config;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        super.init(ctx);
        this.config = TbNodeUtils.convert(configuration, TbSlackNodeConfiguration.class);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) throws ExecutionException, InterruptedException, TbNodeException {
        String token;
        if (config.isUseSystemSettings()) {
            token = ctx.getSlackService().getToken(ctx.getTenantId());
        } else {
            token = config.getBotToken();
        }
        if (token == null) {
            throw new IllegalArgumentException("Slack token is missing");
        }

        String message = TbNodeUtils.processPattern(config.getMessageTemplate(), msg);
        var tbMsg = ackIfNeeded(ctx, msg);
        DonAsynchron.withCallback(ctx.getExternalCallExecutor().executeAsync(() -> {
                    ctx.getSlackService().sendMessage(ctx.getTenantId(), token, config.getConversation().getId(), message);
                }),
                r -> tellSuccess(ctx, tbMsg),
                e -> tellFailure(ctx, tbMsg, e));
    }

}
