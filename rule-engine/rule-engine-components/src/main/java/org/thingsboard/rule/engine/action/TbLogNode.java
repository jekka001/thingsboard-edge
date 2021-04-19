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
package org.thingsboard.rule.engine.action;

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.ListeningExecutor;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.ScriptEngine;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

import static org.thingsboard.common.util.DonAsynchron.withCallback;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "log",
        configClazz = TbLogNodeConfiguration.class,
        nodeDescription = "Log incoming messages using JS script for transformation Message into String",
        nodeDetails = "Transform incoming Message with configured JS function to String and log final value into Thingsboard log file. " +
                "Message payload can be accessed via <code>msg</code> property. For example <code>'temperature = ' + msg.temperature ;</code>. " +
                "Message metadata can be accessed via <code>metadata</code> property. For example <code>'name = ' + metadata.customerName;</code>.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeLogConfig",
        icon = "menu"
)
public class TbLogNode implements TbNode {

    private TbLogNodeConfiguration config;
    private ScriptEngine jsEngine;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbLogNodeConfiguration.class);
        this.jsEngine = ctx.createJsScriptEngine(config.getJsScript());
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        ListeningExecutor jsExecutor = ctx.getJsExecutor();
        ctx.logJsEvalRequest();
        withCallback(jsExecutor.executeAsync(() -> jsEngine.executeToString(msg)),
                toString -> {
                    ctx.logJsEvalResponse();
                    log.info(toString);
                    ctx.tellSuccess(msg);
                },
                t -> {
                    ctx.logJsEvalResponse();
                    ctx.tellFailure(msg, t);
                });
    }

    @Override
    public void destroy() {
        if (jsEngine != null) {
            jsEngine.destroy();
        }
    }
}
