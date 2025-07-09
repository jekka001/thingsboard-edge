/**
 * Copyright © 2016-2025 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.edge.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.edge.EdgeSettings;
import org.thingsboard.server.common.data.id.EdgeId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.msg.TbMsgType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.dao.cloud.EdgeSettingsService;
import org.thingsboard.server.dao.timeseries.TimeseriesService;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EdgeCommunicationStatsService {

    @Autowired
    private EdgeSettingsService edgeSettingsService;
    @Autowired
    private TimeseriesService tsService;
    @Autowired
    private TbClusterService tbClusterService;

    private TenantId tenantId;
    private EdgeId edgeId;

    @Value("${edge.stats.ttl-days:7}")
    private int edgeStatsTtlDays;

    private final AtomicLong uplinkMsgsAdded = new AtomicLong();
    private final AtomicLong uplinkMsgsPushed = new AtomicLong();
    private final AtomicLong uplinkMsgsPermanentlyFailed = new AtomicLong();
    private final AtomicLong uplinkMsgsTmpFailed = new AtomicLong();
    private final AtomicLong uplinkMsgsLag = new AtomicLong();

    private final AtomicLong downlinkMsgsAdded = new AtomicLong();
    private final AtomicLong downlinkMsgsPushed = new AtomicLong();
    private final AtomicLong downlinkMsgsPermanentlyFailed = new AtomicLong();
    private final AtomicLong downlinkMsgsLag = new AtomicLong();

    @Scheduled(fixedRateString = "${edge.stats.report-interval-seconds:20000}")
    public void reportStats() {
        try {
            ensureInitialized();

            long ts = System.currentTimeMillis();
            List<TsKvEntry> statsEntries = List.of(
                    new BasicTsKvEntry(ts, new LongDataEntry("uplinkMsgsAdded", uplinkMsgsAdded.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("uplinkMsgsPushed", uplinkMsgsPushed.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("uplinkMsgsPermanentlyFailed", uplinkMsgsPermanentlyFailed.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("uplinkMsgsTmpFailed", uplinkMsgsTmpFailed.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("uplinkMsgsLag", uplinkMsgsLag.get())),

                    new BasicTsKvEntry(ts, new LongDataEntry("downlinkMsgsAdded", downlinkMsgsAdded.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("downlinkMsgsPushed", downlinkMsgsPushed.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("downlinkMsgsPermanentlyFailed", downlinkMsgsPermanentlyFailed.get())),
                    new BasicTsKvEntry(ts, new LongDataEntry("downlinkMsgsLag", downlinkMsgsLag.get()))
            );
            log.trace("Reported Edge communication stats: {}",
                    statsEntries.stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValueAsString())
                            .collect(Collectors.joining(", "))
            );
            long telemetryTtlSeconds = edgeStatsTtlDays * 24L * 60 * 60;

            tsService.save(tenantId, edgeId, statsEntries, telemetryTtlSeconds);

            try {
                String statsJson = statsEntries.stream()
                        .collect(Collectors.toMap(
                                TsKvEntry::getKey,
                                TsKvEntry::getValueAsString
                        ))
                        .entrySet().stream()
                        .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                        .collect(Collectors.joining(", ", "{", "}"));

                TbMsg tbMsg = TbMsg.newMsg()
                        .type(TbMsgType.POST_TELEMETRY_REQUEST)
                        .originator(edgeId)
                        .dataType(TbMsgDataType.JSON)
                        .data(statsJson)
                        .build();

                tbClusterService.pushMsgToRuleEngine(tenantId, edgeId, tbMsg, null);
                log.info("Successfully pushed telemetry TbMsg to Rule Engine: {}", statsJson);
            } catch (Exception e) {
                log.warn("Failed to push telemetry TbMsg to Rule Engine", e);
            }

            clearCounters();
        } catch (Exception e) {
            log.warn("Failed to report Edge communication stats", e);
        }
    }

    private void ensureInitialized() {
        if (tenantId == null || edgeId == null) {
            EdgeSettings edgeSettings = edgeSettingsService.findEdgeSettings();
            this.tenantId = TenantId.fromUUID(UUID.fromString(edgeSettings.getTenantId()));
            this.edgeId = new EdgeId(UUID.fromString(edgeSettings.getEdgeId()));
        }
    }

    private void clearCounters() {
        uplinkMsgsAdded.set(0);
        uplinkMsgsPushed.set(0);
        uplinkMsgsPermanentlyFailed.set(0);
        uplinkMsgsTmpFailed.set(0);
        downlinkMsgsAdded.set(0);
        downlinkMsgsPushed.set(0);
        downlinkMsgsPermanentlyFailed.set(0);
    }

    public void incrementUplinkMsgsAdded(int count) {
        uplinkMsgsAdded.addAndGet(count);
    }

    public void incrementUplinkMsgsPushed(int count) {
        uplinkMsgsPushed.addAndGet(count);
    }

    public void incrementUplinkMsgsPermanentlyFailed(int count) {
        uplinkMsgsPermanentlyFailed.addAndGet(count);
    }

    public void incrementUplinkMsgsTmpFailed(int count) {
        uplinkMsgsTmpFailed.addAndGet(count);
    }

    public void incrementUplinkMsgsLag(int count) {
        uplinkMsgsLag.addAndGet(count);
    }

    public void decrementUplinkMsgsLag(int count) {
        uplinkMsgsLag.addAndGet(-count);
    }

    public void incrementDownlinkMsgsAdded() {
        downlinkMsgsAdded.incrementAndGet();
    }

    public void incrementDownlinkMsgsPushed() {
        downlinkMsgsPushed.incrementAndGet();
    }

    public void incrementDownlinkMsgsPermanentlyFailed() {
        downlinkMsgsPermanentlyFailed.incrementAndGet();
    }

    public void incrementDownlinkMsgsLag() {
        downlinkMsgsLag.incrementAndGet();
    }

    public void decrementDownlinkMsgsLag() {
        downlinkMsgsLag.decrementAndGet();
    }

}
