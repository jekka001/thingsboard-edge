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
package org.thingsboard.rule.engine.delay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.exception.DataValidationException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.thingsboard.server.dao.service.ConstraintValidator.validateFields;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "delay (deprecated)",
        version = 1,
        configClazz = TbMsgDelayNodeConfiguration.class,
        nodeDescription = "Delays incoming message (deprecated)",
        nodeDetails = "Delays messages for a configurable period. " +
                "Please note, this node acknowledges the message from the current queue (message will be removed from queue). " +
                "Deprecated because the acknowledged message still stays in memory (to be delayed) and this " +
                "does not guarantee that message will be processed even if the \"retry failures and timeouts\" processing strategy will be chosen.",
        icon = "pause",
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeMsgDelayConfig"
)
public class TbMsgDelayNode implements TbNode {

    private static final Set<TimeUnit> supportedTimeUnits = EnumSet.of(TimeUnit.SECONDS, TimeUnit.MINUTES, TimeUnit.HOURS);
    private static final String supportedTimeUnitsStr = supportedTimeUnits.stream().map(TimeUnit::name).collect(Collectors.joining(", "));

    private TbMsgDelayNodeConfiguration config;
    private ConcurrentMap<UUID, TbMsg> pendingMsgs;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbMsgDelayNodeConfiguration.class);
        String errorPrefix = "'" + ctx.getSelf().getName() + "' node configuration is invalid: ";
        try {
            validateFields(config, errorPrefix);
        } catch (DataValidationException e) {
            throw new TbNodeException(e, true);
        }
        this.pendingMsgs = new ConcurrentHashMap<>();
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (msg.isTypeOf(TbMsgType.DELAY_TIMEOUT_SELF_MSG)) {
            TbMsg pendingMsg = pendingMsgs.remove(UUID.fromString(msg.getData()));
            if (pendingMsg != null) {
                ctx.enqueueForTellNext(
                        TbMsg.newMsg(
                                pendingMsg.getQueueName(),
                                pendingMsg.getInternalType(),
                                pendingMsg.getOriginator(),
                                pendingMsg.getCustomerId(),
                                pendingMsg.getMetaData(),
                                pendingMsg.getData()
                        ),
                        TbNodeConnectionType.SUCCESS
                );
            }
        } else {
            if (pendingMsgs.size() < config.getMaxPendingMsgs()) {
                pendingMsgs.put(msg.getId(), msg);
                TbMsg tickMsg = ctx.newMsg(null, TbMsgType.DELAY_TIMEOUT_SELF_MSG, ctx.getSelfId(), msg.getCustomerId(), TbMsgMetaData.EMPTY, msg.getId().toString());
                ctx.tellSelf(tickMsg, getDelay(msg));
                ctx.ack(msg);
            } else {
                ctx.tellFailure(msg, new RuntimeException("Max limit of pending messages reached!"));
            }
        }
    }

    private long getDelay(TbMsg msg) {
        String timeUnitPattern = TbNodeUtils.processPattern(config.getTimeUnit(), msg);
        String periodPattern = TbNodeUtils.processPattern(config.getPeriod(), msg);
        try {
            TimeUnit timeUnit = TimeUnit.valueOf(timeUnitPattern.toUpperCase());
            if (!supportedTimeUnits.contains(timeUnit)) {
                throw new RuntimeException("Time unit '" + timeUnit + "' is not supported! " +
                        "Only " + supportedTimeUnitsStr + " are supported.");
            }
            int period = Integer.parseInt(periodPattern);
            return timeUnit.toMillis(period);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Can't parse period value : " + periodPattern);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid value for period time unit : " + timeUnitPattern);
        }
    }

    @Override
    public void destroy() {
        pendingMsgs.clear();
    }

    @Override
    public TbPair<Boolean, JsonNode> upgrade(int fromVersion, JsonNode oldConfiguration) throws TbNodeException {
        boolean hasChanges = false;
        switch (fromVersion) {
            case 0:
                var periodInSeconds = "periodInSeconds";
                var periodInSecondsPattern = "periodInSecondsPattern";
                var useMetadataPeriodInSecondsPatterns = "useMetadataPeriodInSecondsPatterns";
                var period = "period";
                if (oldConfiguration.has(useMetadataPeriodInSecondsPatterns)) {
                    var isUsedPattern = oldConfiguration.get(useMetadataPeriodInSecondsPatterns).booleanValue();
                    if (isUsedPattern) {
                        if (!oldConfiguration.has(periodInSecondsPattern)) {
                            throw new TbNodeException("Property to update: '" + periodInSecondsPattern + "' does not exist in configuration.");
                        }
                        ((ObjectNode) oldConfiguration).set(period, oldConfiguration.get(periodInSecondsPattern));
                    } else {
                        if (!oldConfiguration.has(periodInSeconds)) {
                            throw new TbNodeException("Property to update: '" + periodInSeconds + "' does not exist in configuration.");
                        }
                        ((ObjectNode) oldConfiguration).put(period, oldConfiguration.get(periodInSeconds).asText());
                    }
                    hasChanges = true;
                } else if (oldConfiguration.has(periodInSeconds)) {
                    ((ObjectNode) oldConfiguration).put(period, oldConfiguration.get(periodInSeconds).asText());
                    hasChanges = true;
                }
                if (!oldConfiguration.has(period)) {
                    ((ObjectNode) oldConfiguration).put(period, "60");
                    hasChanges = true;
                }
                var timeUnit = "timeUnit";
                if (!oldConfiguration.has(timeUnit)) {
                    ((ObjectNode) oldConfiguration).put(timeUnit, TimeUnit.SECONDS.name());
                    hasChanges = true;
                }
                ((ObjectNode) oldConfiguration).remove(List.of(periodInSeconds, periodInSecondsPattern, useMetadataPeriodInSecondsPatterns));
                break;
            default:
                break;
        }
        return new TbPair<>(hasChanges, oldConfiguration);
    }
}
