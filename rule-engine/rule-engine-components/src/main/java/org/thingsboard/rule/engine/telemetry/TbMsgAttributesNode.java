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
package org.thingsboard.rule.engine.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.AttributesSaveRequest;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.telemetry.settings.AttributesProcessingSettings;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.rule.engine.telemetry.settings.AttributesProcessingSettings.Advanced;
import static org.thingsboard.rule.engine.telemetry.settings.AttributesProcessingSettings.Deduplicate;
import static org.thingsboard.rule.engine.telemetry.settings.AttributesProcessingSettings.OnEveryMessage;
import static org.thingsboard.rule.engine.telemetry.settings.AttributesProcessingSettings.WebSocketsOnly;
import static org.thingsboard.server.common.data.DataConstants.NOTIFY_DEVICE_METADATA_KEY;
import static org.thingsboard.server.common.data.DataConstants.SCOPE;
import static org.thingsboard.server.common.data.msg.TbMsgType.POST_ATTRIBUTES_REQUEST;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "save attributes",
        configClazz = TbMsgAttributesNodeConfiguration.class,
        version = 3,
        nodeDescription = """
                Saves attribute data with a configurable scope and according to configured processing strategies.
                """,
        nodeDetails = """
                Node performs two <strong>actions:</strong>
                <ul>
                  <li><strong>Attributes:</strong> save attribute data to a database.</li>
                  <li><strong>WebSockets:</strong> notify WebSockets subscriptions about attribute data updates.</li>
                </ul>
                
                For each <em>action</em>, three <strong>processing strategies</strong> are available:
                <ul>
                  <li><strong>On every message:</strong> perform the action for every message.</li>
                  <li><strong>Deduplicate:</strong> perform the action only for the first message from a particular originator within a configurable interval.</li>
                  <li><strong>Skip:</strong> never perform the action.</li>
                </ul>
                
                <strong>Processing strategies</strong> are configured using <em>processing settings</em>, which support two modes:
                <ul>
                  <li><strong>Basic</strong>
                    <ul>
                      <li><strong>On every message:</strong> applies the "On every message" strategy to all actions.</li>
                      <li><strong>Deduplicate:</strong> applies the "Deduplicate" strategy (with a specified interval) to all actions.</li>
                      <li><strong>WebSockets only:</strong> applies the "Skip" strategy to Attributes, and the "On every message" strategy to WebSockets.</li>
                    </ul>
                  </li>
                  <li><strong>Advanced:</strong> configure each action’s strategy independently.</li>
                </ul>
                
                Additionally:
                <ul>
                  <li>If <b>Save attributes only if the value changes</b> is enabled, the rule node compares the received attribute value with the current stored value and skips the save operation if they match.</li>
                  <li>If <b>Send attributes updated notification</b> is enabled, the rule node will put the <b>Attributes Updated</b> event for <code>SHARED_SCOPE</code> and <code>SERVER_SCOPE</code> attribute updates to the queue named <code>Main</code>.</li>
                  <li>If <b>Force notification to the device</b> is enabled, then rule node will always notify device about <code>SHARED_SCOPE</code> attribute updates, regardless of the value of <code>notifyDevice</code> metadata property.</li>
                </ul>
                
                This node expects messages of type <code>POST_ATTRIBUTES_REQUEST</code>.
                <br><br>
                Output connections: <code>Success</code>, <code>Failure</code>.
                """,
        configDirective = "tbActionNodeAttributesConfig",
        icon = "file_upload"
)
public class TbMsgAttributesNode implements TbNode {

    static final String NOTIFY_DEVICE_KEY = "notifyDevice";
    static final String SEND_ATTRIBUTES_UPDATED_NOTIFICATION_KEY = "sendAttributesUpdatedNotification";
    static final String UPDATE_ATTRIBUTES_ONLY_ON_VALUE_CHANGE_KEY = "updateAttributesOnlyOnValueChange";

    private TbMsgAttributesNodeConfiguration config;

    private AttributesProcessingSettings processingSettings;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        config = TbNodeUtils.convert(configuration, TbMsgAttributesNodeConfiguration.class);
        processingSettings = config.getProcessingSettings();
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (!msg.isTypeOf(POST_ATTRIBUTES_REQUEST)) {
            ctx.tellFailure(msg, new IllegalArgumentException("Unsupported msg type: " + msg.getType()));
            return;
        }
        String src = msg.getData();
        List<AttributeKvEntry> newAttributes = new ArrayList<>(JsonConverter.convertToAttributes(JsonParser.parseString(src)));
        if (newAttributes.isEmpty()) {
            ctx.tellSuccess(msg);
            return;
        }

        AttributesSaveRequest.Strategy strategy = determineSaveStrategy(msg.getMetaDataTs(), msg.getOriginator().getId());

        // short-circuit
        if (!strategy.saveAttributes() && !strategy.sendWsUpdate() && !strategy.processCalculatedFields()) {
            ctx.tellSuccess(msg);
            return;
        }

        AttributeScope scope = getScope(msg.getMetaData().getValue(SCOPE));
        boolean sendAttributesUpdateNotification = checkSendNotification(scope);

        if (!config.isUpdateAttributesOnlyOnValueChange()) {
            saveAttr(newAttributes, ctx, msg, scope, sendAttributesUpdateNotification, strategy);
            return;
        }

        List<String> keys = newAttributes.stream().map(KvEntry::getKey).collect(Collectors.toList());
        ListenableFuture<List<AttributeKvEntry>> findFuture = ctx.getAttributesService().find(ctx.getTenantId(), msg.getOriginator(), scope, keys);

        DonAsynchron.withCallback(findFuture,
                currentAttributes -> {
                    List<AttributeKvEntry> attributesChanged = filterChangedAttr(currentAttributes, newAttributes);
                    saveAttr(attributesChanged, ctx, msg, scope, sendAttributesUpdateNotification, strategy);
                },
                throwable -> ctx.tellFailure(msg, throwable),
                MoreExecutors.directExecutor());
    }

    private AttributesSaveRequest.Strategy determineSaveStrategy(long ts, UUID originatorUuid) {
        if (processingSettings instanceof OnEveryMessage) {
            return AttributesSaveRequest.Strategy.PROCESS_ALL;
        }
        if (processingSettings instanceof WebSocketsOnly) {
            return AttributesSaveRequest.Strategy.WS_ONLY;
        }
        if (processingSettings instanceof Deduplicate deduplicate) {
            boolean isFirstMsgInInterval = deduplicate.getProcessingStrategy().shouldProcess(ts, originatorUuid);
            return isFirstMsgInInterval ? AttributesSaveRequest.Strategy.PROCESS_ALL : AttributesSaveRequest.Strategy.SKIP_ALL;
        }
        if (processingSettings instanceof Advanced advanced) {
            return new AttributesSaveRequest.Strategy(
                    advanced.attributes().shouldProcess(ts, originatorUuid),
                    advanced.webSockets().shouldProcess(ts, originatorUuid),
                    advanced.calculatedFields().shouldProcess(ts, originatorUuid)
            );
        }
        // should not happen
        throw new IllegalArgumentException("Unknown processing settings type: " + processingSettings.getClass().getSimpleName());
    }

    private void saveAttr(
            List<AttributeKvEntry> attributes,
            TbContext ctx,
            TbMsg msg,
            AttributeScope scope,
            boolean sendAttributesUpdateNotification,
            AttributesSaveRequest.Strategy strategy
    ) {
        if (attributes.isEmpty()) {
            ctx.tellSuccess(msg);
            return;
        }
        FutureCallback<Void> callback = sendAttributesUpdateNotification ?
                new AttributesUpdateNodeCallback(ctx, msg, scope.name(), attributes) :
                new TelemetryNodeCallback(ctx, msg);
        ctx.getTelemetryService().saveAttributes(AttributesSaveRequest.builder()
                .tenantId(ctx.getTenantId())
                .entityId(msg.getOriginator())
                .scope(scope)
                .entries(attributes)
                .notifyDevice(config.isNotifyDevice() || checkNotifyDeviceMdValue(msg.getMetaData().getValue(NOTIFY_DEVICE_METADATA_KEY)))
                .strategy(strategy)
                .previousCalculatedFieldIds(msg.getPreviousCalculatedFieldIds())
                .tbMsgId(msg.getId())
                .tbMsgType(msg.getInternalType())
                .callback(callback)
                .build());
    }

    private List<AttributeKvEntry> filterChangedAttr(List<AttributeKvEntry> currentAttributes, List<AttributeKvEntry> newAttributes) {
        if (currentAttributes == null || currentAttributes.isEmpty()) {
            return newAttributes;
        }

        Map<String, AttributeKvEntry> currentAttrMap = currentAttributes.stream()
                .collect(Collectors.toMap(AttributeKvEntry::getKey, Function.identity(), (existing, replacement) -> existing));

        return newAttributes.stream()
                .filter(item -> {
                    AttributeKvEntry cacheAttr = currentAttrMap.get(item.getKey());
                    return cacheAttr == null
                            || !Objects.equals(item.getValue(), cacheAttr.getValue()) //JSON and String can be equals by value, but different by type
                            || !Objects.equals(item.getDataType(), cacheAttr.getDataType());
                })
                .collect(Collectors.toList());
    }

    private boolean checkSendNotification(AttributeScope scope) {
        return config.isSendAttributesUpdatedNotification() && AttributeScope.CLIENT_SCOPE != scope;
    }

    private boolean checkNotifyDeviceMdValue(String notifyDeviceMdValue) {
        // Check for empty string for backward-compatibility. A while ago node always notified devices.
        return StringUtils.isEmpty(notifyDeviceMdValue) || Boolean.parseBoolean(notifyDeviceMdValue);
    }

    private AttributeScope getScope(String mdScopeValue) {
        if (StringUtils.isNotEmpty(mdScopeValue)) {
            return AttributeScope.valueOf(mdScopeValue);
        }
        return AttributeScope.valueOf(config.getScope());
    }

    @Override
    public TbPair<Boolean, JsonNode> upgrade(int fromVersion, JsonNode oldConfiguration) throws TbNodeException {
        boolean hasChanges = false;
        switch (fromVersion) {
            case 0:
                if (!oldConfiguration.has(UPDATE_ATTRIBUTES_ONLY_ON_VALUE_CHANGE_KEY)) {
                    hasChanges = true;
                    ((ObjectNode) oldConfiguration).put(UPDATE_ATTRIBUTES_ONLY_ON_VALUE_CHANGE_KEY, false);
                }
            case 1:
                // update notifyDevice. set true if null or property doesn't exist for backward-compatibility.
                hasChanges = fixEscapedBooleanConfigParameter(oldConfiguration, NOTIFY_DEVICE_KEY, hasChanges, true);
                // update sendAttributesUpdatedNotification.
                hasChanges = fixEscapedBooleanConfigParameter(oldConfiguration, SEND_ATTRIBUTES_UPDATED_NOTIFICATION_KEY, hasChanges, false);
                // update updateAttributesOnlyOnValueChange.
                hasChanges = fixEscapedBooleanConfigParameter(oldConfiguration, UPDATE_ATTRIBUTES_ONLY_ON_VALUE_CHANGE_KEY, hasChanges, true);
            case 2:
                hasChanges = true;
                ((ObjectNode) oldConfiguration).set("processingSettings", JacksonUtil.valueToTree(new OnEveryMessage()));
                break;
            default:
                break;
        }
        return new TbPair<>(hasChanges, oldConfiguration);
    }

    private boolean fixEscapedBooleanConfigParameter(JsonNode oldConfiguration, String boolKey, boolean hasChanges, boolean valueIfNull) {
        if (oldConfiguration.hasNonNull(boolKey)) {
            var value = oldConfiguration.get(boolKey);
            if (value.isTextual()) {
                hasChanges = true;
                ((ObjectNode) oldConfiguration)
                        .put(boolKey, value.asBoolean(valueIfNull));
            }
        } else {
            hasChanges = true;
            ((ObjectNode) oldConfiguration)
                    .put(boolKey, valueIfNull);
        }
        return hasChanges;
    }

}
