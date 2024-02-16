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
package org.thingsboard.rule.engine.action;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.thingsboard.rule.engine.api.RuleEngineDeviceStateManager;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.data.msg.TbNodeConnectionType;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.queue.PartitionChangeMsg;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.common.msg.tools.TbRateLimits;

import java.util.EnumSet;
import java.util.Set;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "device state",
        nodeDescription = "Triggers device connectivity events",
        nodeDetails = "If incoming message originator is a device, registers configured event for that device in the Device State Service, which sends appropriate message to the Rule Engine." +
                " If metadata <code>ts</code> property is present, it will be used as event timestamp. Otherwise, the message timestamp will be used." +
                " If originator entity type is not <code>DEVICE</code> or unexpected error happened during processing, then incoming message is forwarded using <code>Failure</code> chain." +
                " If rate of connectivity events for a given originator is too high, then incoming message is forwarded using <code>Rate limited</code> chain. " +
                "<br>" +
                "Supported device connectivity events are:" +
                "<ul>" +
                "<li>Connect event</li>" +
                "<li>Disconnect event</li>" +
                "<li>Activity event</li>" +
                "<li>Inactivity event</li>" +
                "</ul>" +
                "This node is particularly useful when device isn't using transports to receive data, such as when fetching data from external API or computing new data within the rule chain.",
        configClazz = TbDeviceStateNodeConfiguration.class,
        relationTypes = {TbNodeConnectionType.SUCCESS, TbNodeConnectionType.FAILURE, "Rate limited"},
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeDeviceStateConfig"
)
public class TbDeviceStateNode implements TbNode {

    private static final Set<TbMsgType> SUPPORTED_EVENTS = EnumSet.of(
            TbMsgType.CONNECT_EVENT, TbMsgType.ACTIVITY_EVENT, TbMsgType.DISCONNECT_EVENT, TbMsgType.INACTIVITY_EVENT
    );
    private static final String DEFAULT_RATE_LIMIT_CONFIG = "1:1,30:60,60:3600";
    private ConcurrentReferenceHashMap<DeviceId, TbRateLimits> rateLimits;
    private String rateLimitConfig;
    private TbMsgType event;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        TbMsgType event = TbNodeUtils.convert(configuration, TbDeviceStateNodeConfiguration.class).getEvent();
        if (event == null) {
            throw new TbNodeException("Event cannot be null!", true);
        }
        if (!SUPPORTED_EVENTS.contains(event)) {
            throw new TbNodeException("Unsupported event: " + event, true);
        }
        this.event = event;
        rateLimits = new ConcurrentReferenceHashMap<>();
        String deviceStateNodeRateLimitConfig = ctx.getDeviceStateNodeRateLimitConfig();
        try {
            rateLimitConfig = new TbRateLimits(deviceStateNodeRateLimitConfig).getConfiguration();
        } catch (Exception e) {
            log.error("[{}][{}] Invalid rate limit configuration provided: [{}]. Will use default value [{}].",
                    ctx.getTenantId().getId(), ctx.getSelfId().getId(), deviceStateNodeRateLimitConfig, DEFAULT_RATE_LIMIT_CONFIG, e);
            rateLimitConfig = DEFAULT_RATE_LIMIT_CONFIG;
        }
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        EntityType originatorEntityType = msg.getOriginator().getEntityType();
        if (!EntityType.DEVICE.equals(originatorEntityType)) {
            ctx.tellFailure(msg, new IllegalArgumentException(
                    "Unsupported originator entity type: [" + originatorEntityType + "]. Only DEVICE entity type is supported."
            ));
            return;
        }
        DeviceId originator = new DeviceId(msg.getOriginator().getId());
        rateLimits.compute(originator, (__, rateLimit) -> {
            if (rateLimit == null) {
                rateLimit = new TbRateLimits(rateLimitConfig);
            }
            boolean isNotRateLimited = rateLimit.tryConsume();
            if (isNotRateLimited) {
                sendEventAndTell(ctx, originator, msg);
            } else {
                ctx.tellNext(msg, "Rate limited");
            }
            return rateLimit;
        });
    }

    private void sendEventAndTell(TbContext ctx, DeviceId originator, TbMsg msg) {
        TenantId tenantId = ctx.getTenantId();
        long eventTs = msg.getMetaDataTs();

        RuleEngineDeviceStateManager deviceStateManager = ctx.getDeviceStateManager();
        TbCallback callback = getMsgEnqueuedCallback(ctx, msg);

        switch (event) {
            case CONNECT_EVENT:
                deviceStateManager.onDeviceConnect(tenantId, originator, eventTs, callback);
                break;
            case ACTIVITY_EVENT:
                deviceStateManager.onDeviceActivity(tenantId, originator, eventTs, callback);
                break;
            case DISCONNECT_EVENT:
                deviceStateManager.onDeviceDisconnect(tenantId, originator, eventTs, callback);
                break;
            case INACTIVITY_EVENT:
                deviceStateManager.onDeviceInactivity(tenantId, originator, eventTs, callback);
                break;
            default:
                ctx.tellFailure(msg, new IllegalStateException("Configured event [" + event + "] is not supported!"));
        }
    }

    private TbCallback getMsgEnqueuedCallback(TbContext ctx, TbMsg msg) {
        return new TbCallback() {
            @Override
            public void onSuccess() {
                ctx.tellSuccess(msg);
            }

            @Override
            public void onFailure(Throwable t) {
                ctx.tellFailure(msg, t);
            }
        };
    }

    @Override
    public void onPartitionChangeMsg(TbContext ctx, PartitionChangeMsg msg) {
        rateLimits.entrySet().removeIf(entry -> !ctx.isLocalEntity(entry.getKey()));
    }

    @Override
    public void destroy() {
        if (rateLimits != null) {
            rateLimits.clear();
            rateLimits = null;
        }
        rateLimitConfig = null;
        event = null;
    }

}
