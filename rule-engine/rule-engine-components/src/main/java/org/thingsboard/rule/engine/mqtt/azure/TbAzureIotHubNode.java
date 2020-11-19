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
package org.thingsboard.rule.engine.mqtt.azure;

import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.AzureIotHubUtil;
import org.thingsboard.mqtt.MqttClientConfig;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.rule.engine.api.util.TbNodeUtils;
import org.thingsboard.rule.engine.mqtt.TbMqttNode;
import org.thingsboard.rule.engine.mqtt.TbMqttNodeConfiguration;
import org.thingsboard.rule.engine.mqtt.credentials.CertPemClientCredentials;
import org.thingsboard.rule.engine.mqtt.credentials.MqttClientCredentials;
import org.thingsboard.server.common.data.plugin.ComponentType;

import java.util.Optional;

@Slf4j
@RuleNode(
        type = ComponentType.EXTERNAL,
        name = "azure iot hub",
        configClazz = TbAzureIotHubNodeConfiguration.class,
        nodeDescription = "Publish messages to the Azure IoT Hub",
        nodeDetails = "Will publish message payload to the Azure IoT Hub with QoS <b>AT_LEAST_ONCE</b>.",
        uiResources = {"static/rulenode/rulenode-core-config.js", "static/rulenode/rulenode-core-config.css"},
        configDirective = "tbActionNodeAzureIotHubConfig"
)
public class TbAzureIotHubNode extends TbMqttNode {
    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        try {
            this.mqttNodeConfiguration = TbNodeUtils.convert(configuration, TbMqttNodeConfiguration.class);
            mqttNodeConfiguration.setPort(8883);
            mqttNodeConfiguration.setCleanSession(true);
            MqttClientCredentials credentials = mqttNodeConfiguration.getCredentials();
            mqttNodeConfiguration.setCredentials(new MqttClientCredentials() {
                @Override
                public Optional<SslContext> initSslContext() {
                    if (credentials instanceof AzureIotHubSasCredentials) {
                        AzureIotHubSasCredentials sasCredentials = (AzureIotHubSasCredentials) credentials;
                        if (sasCredentials.getCaCert() == null || sasCredentials.getCaCert().isEmpty()) {
                            sasCredentials.setCaCert(AzureIotHubUtil.getDefaultCaCert());
                        }
                    } else if (credentials instanceof CertPemClientCredentials) {
                        CertPemClientCredentials pemCredentials = (CertPemClientCredentials) credentials;
                        if (pemCredentials.getCaCert() == null || pemCredentials.getCaCert().isEmpty()) {
                            pemCredentials.setCaCert(AzureIotHubUtil.getDefaultCaCert());
                        }
                    }
                    return credentials.initSslContext();
                }

                @Override
                public void configure(MqttClientConfig config) {
                    config.setProtocolVersion(MqttVersion.MQTT_3_1_1);
                    config.setUsername(AzureIotHubUtil.buildUsername(mqttNodeConfiguration.getHost(), config.getClientId()));
                    if (credentials instanceof AzureIotHubSasCredentials) {
                        AzureIotHubSasCredentials sasCredentials = (AzureIotHubSasCredentials) credentials;
                        config.setPassword(AzureIotHubUtil.buildSasToken(mqttNodeConfiguration.getHost(), sasCredentials.getSasKey()));
                    }
                }
            });

            this.mqttClient = initClient(ctx);
        } catch (Exception e) {
            throw new TbNodeException(e);
        }    }
}
