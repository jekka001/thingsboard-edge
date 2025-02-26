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
package org.thingsboard.server.queue.kafka;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.thingsboard.server.queue.util.PropertyUtils;

import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "queue", value = "type", havingValue = "kafka")
public class TbKafkaTopicConfigs {

    public static final String NUM_PARTITIONS_SETTING = "partitions";

    @Value("${queue.kafka.topic-properties.core:}")
    private String coreProperties;
    @Value("${queue.kafka.topic-properties.rule-engine:}")
    private String ruleEngineProperties;
    @Value("${queue.kafka.topic-properties.transport-api:}")
    private String transportApiProperties;
    @Value("${queue.kafka.topic-properties.integration-api:}")
    private String integrationApiProperties;
    @Value("${queue.kafka.topic-properties.notifications:}")
    private String notificationsProperties;
    @Value("${queue.kafka.topic-properties.js-executor:}")
    private String jsExecutorProperties;
    @Value("${queue.kafka.topic-properties.ota-updates:}")
    private String fwUpdatesProperties;
    @Value("${queue.kafka.topic-properties.version-control:}")
    private String vcProperties;
    @Value("${queue.kafka.topic-properties.edge:}")
    private String edgeProperties;
    @Value("${queue.kafka.topic-properties.edge-event:}")
    private String edgeEventProperties;
    @Value("${queue.kafka.topic-properties.housekeeper:}")
    private String housekeeperProperties;
    @Value("${queue.kafka.topic-properties.housekeeper-reprocessing:}")
    private String housekeeperReprocessingProperties;

    @Getter
    private Map<String, String> coreConfigs;
    @Getter
    private Map<String, String> ruleEngineConfigs;
    @Getter
    private Map<String, String> transportApiRequestConfigs;
    @Getter
    private Map<String, String> transportApiResponseConfigs;
    @Getter
    private Map<String, String> integrationApiRequestConfigs;
    @Getter
    private Map<String, String> integrationApiResponseConfigs;
    @Getter
    private Map<String, String> notificationsConfigs;
    @Getter
    private Map<String, String> jsExecutorRequestConfigs;
    @Getter
    private Map<String, String> jsExecutorResponseConfigs;
    @Getter
    private Map<String, String> fwUpdatesConfigs;
    @Getter
    private Map<String, String> vcConfigs;
    @Getter
    private Map<String, String> housekeeperConfigs;
    @Getter
    private Map<String, String> housekeeperReprocessingConfigs;
    @Getter
    private Map<String, String> edgeConfigs;
    @Getter
    private Map<String, String> edgeEventConfigs;

    @PostConstruct
    private void init() {
        coreConfigs = PropertyUtils.getProps(coreProperties);
        ruleEngineConfigs = PropertyUtils.getProps(ruleEngineProperties);
        transportApiRequestConfigs = PropertyUtils.getProps(transportApiProperties);
        transportApiResponseConfigs = PropertyUtils.getProps(transportApiProperties);
        transportApiResponseConfigs.put(NUM_PARTITIONS_SETTING, "1");
        integrationApiRequestConfigs = PropertyUtils.getProps(integrationApiProperties);
        integrationApiResponseConfigs = PropertyUtils.getProps(integrationApiProperties);
        integrationApiResponseConfigs.put(NUM_PARTITIONS_SETTING, "1");
        notificationsConfigs = PropertyUtils.getProps(notificationsProperties);
        jsExecutorRequestConfigs = PropertyUtils.getProps(jsExecutorProperties);
        jsExecutorResponseConfigs = PropertyUtils.getProps(jsExecutorProperties);
        jsExecutorResponseConfigs.put(NUM_PARTITIONS_SETTING, "1");
        fwUpdatesConfigs = PropertyUtils.getProps(fwUpdatesProperties);
        vcConfigs = PropertyUtils.getProps(vcProperties);
        housekeeperConfigs = PropertyUtils.getProps(housekeeperProperties);
        housekeeperReprocessingConfigs = PropertyUtils.getProps(housekeeperReprocessingProperties);
        edgeConfigs = PropertyUtils.getProps(edgeProperties);
        edgeEventConfigs = PropertyUtils.getProps(edgeEventProperties);
    }

}
