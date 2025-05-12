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
package org.thingsboard.monitoring.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.thingsboard.monitoring.client.WsClient;
import org.thingsboard.monitoring.config.MonitoringConfig;
import org.thingsboard.monitoring.config.MonitoringTarget;
import org.thingsboard.monitoring.data.Latencies;
import org.thingsboard.monitoring.data.MonitoredServiceKey;
import org.thingsboard.monitoring.data.ServiceFailureException;
import org.thingsboard.monitoring.util.TbStopWatch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public abstract class BaseHealthChecker<C extends MonitoringConfig, T extends MonitoringTarget> {

    @Getter
    protected final C config;
    @Getter
    protected final T target;

    private Object info;

    @Autowired
    protected MonitoringEntityService entityService;
    @Autowired
    private MonitoringReporter reporter;
    @Autowired
    private TbStopWatch stopWatch;
    @Value("${monitoring.check_timeout_ms}")
    private int resultCheckTimeoutMs;

    @Getter
    private final Map<String, BaseHealthChecker<C, T>> associates = new HashMap<>();

    public static final String TEST_TELEMETRY_KEY = "testData";
    public static final String TEST_CF_TELEMETRY_KEY = "testDataCf";

    @PostConstruct
    private void init() {
        info = getInfo();
    }

    protected abstract void initialize();

    public final void check(WsClient wsClient) {
        log.debug("[{}] Checking", info);
        try {
            int expectedUpdatesCount = isCfMonitoringEnabled() ? 2 : 1;
            wsClient.registerWaitForUpdates(expectedUpdatesCount);

            String testValue = UUID.randomUUID().toString();
            String testPayload = createTestPayload(testValue);
            try {
                initClient();
                stopWatch.start();
                sendTestPayload(testPayload);
                reporter.reportLatency(Latencies.request(getKey()), stopWatch.getTime());
                log.trace("[{}] Sent test payload ({})", info, testPayload);
            } catch (Throwable e) {
                throw new ServiceFailureException(info, e);
            }

            log.trace("[{}] Waiting for WS update", info);
            checkWsUpdates(wsClient, testValue);

            reporter.serviceIsOk(info);
            reporter.serviceIsOk(MonitoredServiceKey.GENERAL);
        } catch (ServiceFailureException e) {
            reporter.serviceFailure(e.getServiceKey(), e);
        } catch (Exception e) {
            reporter.serviceFailure(MonitoredServiceKey.GENERAL, e);
        }

        associates.values().forEach(healthChecker -> {
            healthChecker.check(wsClient);
        });
    }

    private void checkWsUpdates(WsClient wsClient, String testValue) {
        stopWatch.start();
        wsClient.waitForUpdates(resultCheckTimeoutMs);
        log.trace("[{}] Waited for WS update. Last WS msgs: {}", info, wsClient.lastMsgs);
        Map<String, String> latest = wsClient.getLatest(target.getDeviceId());
        if (latest.isEmpty()) {
            throw new ServiceFailureException(info, "No WS update arrived within " + resultCheckTimeoutMs + " ms");
        }
        String actualValue = latest.get(TEST_TELEMETRY_KEY);
        if (!testValue.equals(actualValue)) {
            throw new ServiceFailureException(info, "Was expecting value " + testValue + " but got " + actualValue);
        }
        if (isCfMonitoringEnabled()) {
            String cfTestValue = testValue + "-cf";
            String actualCfValue = latest.get(TEST_CF_TELEMETRY_KEY);
            if (actualCfValue == null) {
                throw new ServiceFailureException(MonitoredServiceKey.CF, "No CF value arrived");
            } else if (!cfTestValue.equals(actualCfValue)) {
                throw new ServiceFailureException(MonitoredServiceKey.CF, "Was expecting CF value " + cfTestValue + " but got " + actualCfValue);
            } else {
                reporter.serviceIsOk(MonitoredServiceKey.CF);
            }
        }
        reporter.reportLatency(Latencies.wsUpdate(getKey()), stopWatch.getTime());
    }

    protected abstract void initClient() throws Exception;

    protected abstract String createTestPayload(String testValue);

    protected abstract void sendTestPayload(String payload) throws Exception;

    @PreDestroy
    protected abstract void destroyClient() throws Exception;

    protected abstract Object getInfo();

    protected abstract String getKey();

    protected abstract boolean isCfMonitoringEnabled();

}
