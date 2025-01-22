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
package org.thingsboard.rule.engine.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.TimeseriesSaveRequest;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.telemetry.strategy.PersistenceStrategy;
import org.thingsboard.server.common.adaptor.JsonConverter;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.TenantProfile;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.data.tenant.profile.DefaultTenantProfileConfiguration;
import org.thingsboard.server.common.data.util.TbPair;
import org.thingsboard.server.common.msg.TbMsg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration.PersistenceSettings;
import static org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration.PersistenceSettings.Advanced;
import static org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration.PersistenceSettings.Deduplicate;
import static org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration.PersistenceSettings.OnEveryMessage;
import static org.thingsboard.rule.engine.telemetry.TbMsgTimeseriesNodeConfiguration.PersistenceSettings.WebSocketsOnly;
import static org.thingsboard.server.common.data.msg.TbMsgType.POST_TELEMETRY_REQUEST;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "save time series",
        configClazz = TbMsgTimeseriesNodeConfiguration.class,
        nodeDescription = """
                Saves time series data with a configurable TTL and according to configured persistence strategies.
                """,
        nodeDetails = """
                Node performs three <strong>actions:</strong>
                <ul>
                  <li><strong>Time series:</strong> save time series data to a <code>ts_kv</code> table in a DB.</li>
                  <li><strong>Latest values:</strong> save time series data to a <code>ts_kv_latest</code> table in a DB.</li>
                  <li><strong>WebSockets:</strong> notify WebSockets subscriptions about time series data updates.</li>
                </ul>
                
                For each <em>action</em>, three <strong>persistence strategies</strong> are available:
                <ul>
                  <li><strong>On every message:</strong> perform the action for every message.</li>
                  <li><strong>Deduplicate:</strong> perform the action only for the first message from a particular originator within a configurable interval.</li>
                  <li><strong>Skip:</strong> never perform the action.</li>
                </ul>
                
                <strong>Persistence strategies</strong> are configured using <em>persistence settings</em>, which support two modes:
                <ul>
                  <li><strong>Basic</strong>
                    <ul>
                      <li><strong>On every message:</strong> applies the "On every message" strategy to all actions.</li>
                      <li><strong>Deduplicate:</strong> applies the "Deduplicate" strategy (with a specified interval) to all actions.</li>
                      <li><strong>WebSockets only:</strong> applies the "Skip" strategy to Time series and Latest values, and the "On every message" strategy to WebSockets.</li>
                    </ul>
                  </li>
                  <li><strong>Advanced:</strong> configure each action’s strategy independently.</li>
                </ul>
                
                By default, the timestamp is taken from <code>metadata.ts</code>. You can enable
                <em>Use server timestamp</em> to always use the current server time instead. This is particularly
                useful in sequential processing scenarios where messages may arrive with out-of-order timestamps from
                multiple sources. Note that the DB layer may ignore older records for attributes and latest values,
                so enabling <em>Use server timestamp</em> can ensure correct ordering.
                <br><br>
                The TTL is taken first from <code>metadata.TTL</code>. If absent, the node configuration’s default
                TTL is used. If neither is set, the tenant profile default applies.
                <br><br>
                This node expects messages of type <code>POST_TELEMETRY_REQUEST</code>.
                <br><br>
                Output connections: <code>Success</code>, <code>Failure</code>.
                """,
        uiResources = {"static/rulenode/rulenode-core-config.js"},
        configDirective = "tbActionNodeTimeseriesConfig",
        icon = "file_upload",
        version = 1
)
public class TbMsgTimeseriesNode implements TbNode {

    private TbMsgTimeseriesNodeConfiguration config;
    private TbContext ctx;
    private long tenantProfileDefaultStorageTtl;

    private PersistenceSettings persistenceSettings;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbMsgTimeseriesNodeConfiguration.class);
        this.ctx = ctx;
        ctx.addTenantProfileListener(this::onTenantProfileUpdate);
        onTenantProfileUpdate(ctx.getTenantProfile());
        persistenceSettings = config.getPersistenceSettings();
    }

    private void onTenantProfileUpdate(TenantProfile tenantProfile) {
        DefaultTenantProfileConfiguration configuration = (DefaultTenantProfileConfiguration) tenantProfile.getProfileData().getConfiguration();
        tenantProfileDefaultStorageTtl = TimeUnit.DAYS.toSeconds(configuration.getDefaultStorageTtlDays());
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (!msg.isTypeOf(POST_TELEMETRY_REQUEST)) {
            ctx.tellFailure(msg, new IllegalArgumentException("Unsupported msg type: " + msg.getType()));
            return;
        }
        long ts = computeTs(msg, config.isUseServerTs());

        TimeseriesSaveRequest.Strategy strategy = determineSaveStrategy(ts, msg.getOriginator().getId());

        // short-circuit
        if (!strategy.saveTimeseries() && !strategy.saveLatest() && !strategy.sendWsUpdate()) {
            ctx.tellSuccess(msg);
            return;
        }

        String src = msg.getData();
        Map<Long, List<KvEntry>> tsKvMap = JsonConverter.convertToTelemetry(JsonParser.parseString(src), ts);
        if (tsKvMap.isEmpty()) {
            ctx.tellFailure(msg, new IllegalArgumentException("Msg body is empty: " + src));
            return;
        }
        List<TsKvEntry> tsKvEntryList = new ArrayList<>();
        for (Map.Entry<Long, List<KvEntry>> tsKvEntry : tsKvMap.entrySet()) {
            for (KvEntry kvEntry : tsKvEntry.getValue()) {
                tsKvEntryList.add(new BasicTsKvEntry(tsKvEntry.getKey(), kvEntry));
            }
        }
        String ttlValue = msg.getMetaData().getValue("TTL");
        long ttl = !StringUtils.isEmpty(ttlValue) ? Long.parseLong(ttlValue) : config.getDefaultTTL();
        if (ttl == 0L) {
            ttl = tenantProfileDefaultStorageTtl;
        }
        String overwriteValueStr = msg.getMetaData().getValue("overwriteValue");
        boolean overwriteValue = Boolean.parseBoolean(overwriteValueStr);
        ctx.getTelemetryService().saveTimeseries(TimeseriesSaveRequest.builder()
                .tenantId(ctx.getTenantId())
                .customerId(msg.getCustomerId())
                .entityId(msg.getOriginator())
                .entries(tsKvEntryList)
                .ttl(ttl)
                .strategy(strategy)
                .overwriteValue(overwriteValue)
                .callback(new TelemetryNodeCallback(ctx, msg))
                .build());
    }

    public static long computeTs(TbMsg msg, boolean ignoreMetadataTs) {
        return ignoreMetadataTs ? System.currentTimeMillis() : msg.getMetaDataTs();
    }

    private TimeseriesSaveRequest.Strategy determineSaveStrategy(long ts, UUID originatorUuid) {
        if (persistenceSettings instanceof OnEveryMessage) {
            return TimeseriesSaveRequest.Strategy.SAVE_ALL;
        }
        if (persistenceSettings instanceof WebSocketsOnly) {
            return TimeseriesSaveRequest.Strategy.WS_ONLY;
        }
        if (persistenceSettings instanceof Deduplicate deduplicate) {
            boolean isFirstMsgInInterval = deduplicate.getDeduplicateStrategy().shouldPersist(ts, originatorUuid);
            return isFirstMsgInInterval ? TimeseriesSaveRequest.Strategy.SAVE_ALL : TimeseriesSaveRequest.Strategy.SKIP_ALL;
        }
        if (persistenceSettings instanceof Advanced advanced) {
            return new TimeseriesSaveRequest.Strategy(
                    advanced.timeseries().shouldPersist(ts, originatorUuid),
                    advanced.latest().shouldPersist(ts, originatorUuid),
                    advanced.webSockets().shouldPersist(ts, originatorUuid)
            );
        }
        // should not happen
        throw new IllegalArgumentException("Unknown persistence settings type: " + persistenceSettings.getClass().getSimpleName());
    }

    @Override
    public void destroy() {
        ctx.removeListeners();
    }

    @Override
    public TbPair<Boolean, JsonNode> upgrade(int fromVersion, JsonNode oldConfiguration) throws TbNodeException {
        boolean hasChanges = false;
        switch (fromVersion) {
            case 0:
                hasChanges = true;
                JsonNode skipLatestPersistence = oldConfiguration.get("skipLatestPersistence");
                if (skipLatestPersistence != null && "true".equals(skipLatestPersistence.asText())) {
                    var skipLatestPersistenceSettings = new Advanced(
                            PersistenceStrategy.onEveryMessage(),
                            PersistenceStrategy.skip(),
                            PersistenceStrategy.onEveryMessage()
                    );
                    ((ObjectNode) oldConfiguration).set("persistenceSettings", JacksonUtil.valueToTree(skipLatestPersistenceSettings));
                } else {
                    ((ObjectNode) oldConfiguration).set("persistenceSettings", JacksonUtil.valueToTree(new OnEveryMessage()));
                }
                ((ObjectNode) oldConfiguration).remove("skipLatestPersistence");
                break;
            default:
                break;
        }
        return new TbPair<>(hasChanges, oldConfiguration);
    }

}
