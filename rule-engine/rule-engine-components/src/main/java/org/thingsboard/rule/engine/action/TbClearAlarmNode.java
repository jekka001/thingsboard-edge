/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.alarm.Alarm;
import org.thingsboard.server.common.data.alarm.AlarmStatus;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "clear alarm", relationTypes = {"Cleared", "False"},
        configClazz = TbClearAlarmNodeConfiguration.class,
        nodeDescription = "Clear Alarm",
        nodeDetails =
                "Details - JS function that creates JSON object based on incoming message. This object will be added into Alarm.details field.\n" +
                        "Node output:\n" +
                        "If alarm was not cleared, original message is returned. Otherwise new Message returned with type 'ALARM', Alarm object in 'msg' property and 'metadata' will contains 'isClearedAlarm' property. " +
                        "Message payload can be accessed via <code>msg</code> property. For example <code>'temperature = ' + msg.temperature ;</code>. " +
                        "Message metadata can be accessed via <code>metadata</code> property. For example <code>'name = ' + metadata.customerName;</code>.",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeClearAlarmConfig",
        icon = "notifications_off"
)
public class TbClearAlarmNode extends TbAbstractAlarmNode<TbClearAlarmNodeConfiguration> {

    @Override
    protected TbClearAlarmNodeConfiguration loadAlarmNodeConfig(TbNodeConfiguration configuration) throws TbNodeException {
        return TbNodeUtils.convert(configuration, TbClearAlarmNodeConfiguration.class);
    }

    @Override
    protected ListenableFuture<AlarmResult> processAlarm(TbContext ctx, TbMsg msg) {
        String alarmType = TbNodeUtils.processPattern(this.config.getAlarmType(), msg.getMetaData());
        ListenableFuture<Alarm> latest = ctx.getAlarmService().findLatestByOriginatorAndType(ctx.getTenantId(), msg.getOriginator(), alarmType);
        return Futures.transformAsync(latest, a -> {
            if (a != null && !a.getStatus().isCleared()) {
                return clearAlarm(ctx, msg, a);
            }
            return Futures.immediateFuture(new AlarmResult(false, false, false, null));
        }, ctx.getDbCallbackExecutor());
    }

    private ListenableFuture<AlarmResult> clearAlarm(TbContext ctx, TbMsg msg, Alarm alarm) {
        ctx.logJsEvalRequest();
        ListenableFuture<JsonNode> asyncDetails = buildAlarmDetails(ctx, msg, alarm.getDetails());
        return Futures.transformAsync(asyncDetails, details -> {
            ctx.logJsEvalResponse();
            ListenableFuture<Boolean> clearFuture = ctx.getAlarmService().clearAlarm(ctx.getTenantId(), alarm.getId(), details, System.currentTimeMillis());
            return Futures.transformAsync(clearFuture, cleared -> {
                ListenableFuture<Alarm> savedAlarmFuture = ctx.getAlarmService().findAlarmByIdAsync(ctx.getTenantId(), alarm.getId());
                return Futures.transformAsync(savedAlarmFuture, savedAlarm -> {
                    if (cleared && savedAlarm != null) {
                        alarm.setDetails(savedAlarm.getDetails());
                        alarm.setEndTs(savedAlarm.getEndTs());
                        alarm.setClearTs(savedAlarm.getClearTs());
                    }
                    alarm.setStatus(alarm.getStatus().isAck() ? AlarmStatus.CLEARED_ACK : AlarmStatus.CLEARED_UNACK);
                    return Futures.immediateFuture(new AlarmResult(false, false, true, alarm));
                }, ctx.getDbCallbackExecutor());
            }, ctx.getDbCallbackExecutor());
        }, ctx.getDbCallbackExecutor());
    }
}
