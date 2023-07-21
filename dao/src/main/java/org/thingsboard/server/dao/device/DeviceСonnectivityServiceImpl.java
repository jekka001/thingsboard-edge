/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.dao.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.DeviceTransportType;
import org.thingsboard.server.common.data.ResourceUtils;
import org.thingsboard.server.common.data.device.profile.MqttDeviceProfileTransportConfiguration;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.security.DeviceCredentials;
import org.thingsboard.server.common.data.security.DeviceCredentialsType;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.thingsboard.server.dao.service.Validator.validateId;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.CHECK_DOCUMENTATION;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.COAP;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.COAPS;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.DOCKER;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.HTTP;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.HTTPS;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.LINUX;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.MQTT;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.MQTTS;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.WINDOWS;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.getCoapClientCommand;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.getCurlCommand;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.getDockerMosquittoClientsPublishCommand;
import static org.thingsboard.server.dao.util.DeviceConnectivityUtil.getMosquittoPubPublishCommand;

@Service("DeviceConnectivityDaoService")
@Slf4j
public class DeviceСonnectivityServiceImpl implements DeviceConnectivityService {

    public static final String INCORRECT_TENANT_ID = "Incorrect tenantId ";
    public static final String INCORRECT_DEVICE_ID = "Incorrect deviceId ";
    public static final String DEFAULT_DEVICE_TELEMETRY_TOPIC = "v1/devices/me/telemetry";

    @Autowired
    private DeviceCredentialsService deviceCredentialsService;

    @Autowired
    private DeviceProfileService deviceProfileService;

    @Autowired
    private DeviceConnectivityConfiguration deviceConnectivityConfiguration;

    @Override
    public JsonNode findDevicePublishTelemetryCommands(String baseUrl, Device device) throws URISyntaxException {
        DeviceId deviceId = device.getId();
        log.trace("Executing findDevicePublishTelemetryCommands [{}]", deviceId);
        validateId(deviceId, INCORRECT_DEVICE_ID + deviceId);

        String defaultHostname = new URI(baseUrl).getHost();
        DeviceCredentials creds = deviceCredentialsService.findDeviceCredentialsByDeviceId(device.getTenantId(), deviceId);
        DeviceProfile deviceProfile = deviceProfileService.findDeviceProfileById(device.getTenantId(), device.getDeviceProfileId());
        DeviceTransportType transportType = deviceProfile.getTransportType();

        ObjectNode commands = JacksonUtil.newObjectNode();
        switch (transportType) {
            case DEFAULT:
                Optional.ofNullable(getHttpTransportPublishCommands(defaultHostname, creds))
                        .ifPresent(v -> commands.set(HTTP, v));
                Optional.ofNullable(getMqttTransportPublishCommands(defaultHostname, creds))
                        .ifPresent(v -> commands.set(MQTT, v));
                Optional.ofNullable(getCoapTransportPublishCommands(defaultHostname, creds))
                        .ifPresent(v -> commands.set(COAP, v));
                break;
            case MQTT:
                MqttDeviceProfileTransportConfiguration transportConfiguration =
                        (MqttDeviceProfileTransportConfiguration) deviceProfile.getProfileData().getTransportConfiguration();
                String topicName = transportConfiguration.getDeviceTelemetryTopic();

                Optional.ofNullable(getMqttTransportPublishCommands(defaultHostname, topicName, creds))
                        .ifPresent(v -> commands.set(MQTT, v));
                break;
            case COAP:
                Optional.ofNullable(getCoapTransportPublishCommands(defaultHostname, creds))
                        .ifPresent(v -> commands.set(COAP, v));
                break;
            default:
                commands.put(transportType.name(), CHECK_DOCUMENTATION);
        }
        return commands;
    }

    @Override
    public String getSslServerChain(String protocol) throws IOException {
        String mqttSslPemPath = deviceConnectivityConfiguration.getConnectivity()
                .get(protocol)
                .getSslServerPemPath();
        if (!mqttSslPemPath.isEmpty() && ResourceUtils.resourceExists(this, mqttSslPemPath)) {
            return FileUtils.readFileToString(new File(mqttSslPemPath), StandardCharsets.UTF_8);
        } else {
            return null;
        }
    }

    private JsonNode getHttpTransportPublishCommands(String defaultHostname, DeviceCredentials deviceCredentials) {
        ObjectNode httpCommands = JacksonUtil.newObjectNode();
        Optional.ofNullable(getHttpPublishCommand(HTTP, defaultHostname, deviceCredentials))
                .ifPresent(v -> httpCommands.put(HTTP, v));
        Optional.ofNullable(getHttpPublishCommand(HTTPS, defaultHostname, deviceCredentials))
                .ifPresent(v -> httpCommands.put(HTTPS, v));
        return httpCommands.isEmpty() ? null : httpCommands;
    }

    private String getHttpPublishCommand(String protocol, String defaultHostname, DeviceCredentials deviceCredentials) {
        DeviceConnectivityInfo httpProps = deviceConnectivityConfiguration.getConnectivity().get(protocol);
        if (httpProps == null || !httpProps.getEnabled() ||
                deviceCredentials.getCredentialsType() != DeviceCredentialsType.ACCESS_TOKEN) {
            return null;
        }
        String hostName = httpProps.getHost().isEmpty() ? defaultHostname : httpProps.getHost();
        String port = httpProps.getPort().isEmpty() ? "" : ":" + httpProps.getPort();

        return getCurlCommand(protocol, hostName, port, deviceCredentials);
    }

    private JsonNode getMqttTransportPublishCommands(String defaultHostname, DeviceCredentials deviceCredentials) {
        return getMqttTransportPublishCommands(defaultHostname, DEFAULT_DEVICE_TELEMETRY_TOPIC, deviceCredentials);
    }

    private JsonNode getMqttTransportPublishCommands(String defaultHostname, String topic, DeviceCredentials deviceCredentials) {
        ObjectNode mqttCommands = JacksonUtil.newObjectNode();

        Optional.ofNullable(getMqttPublishCommand(MQTT, defaultHostname, topic, deviceCredentials))
                .ifPresent(v -> mqttCommands.put(MQTT, v));
        Optional.ofNullable(getMqttPublishCommand(MQTTS, defaultHostname, topic, deviceCredentials))
                .ifPresent(v -> mqttCommands.put(MQTTS, v));

        ObjectNode dockerMqttCommands = JacksonUtil.newObjectNode();
        Optional.ofNullable(getDockerMqttPublishCommand(MQTT, defaultHostname, topic, deviceCredentials))
                .ifPresent(v -> dockerMqttCommands.put(MQTT, v));
        Optional.ofNullable(getDockerMqttPublishCommand(MQTTS, defaultHostname, topic, deviceCredentials))
                .ifPresent(v -> dockerMqttCommands.put(MQTTS, v));

        if (!dockerMqttCommands.isEmpty()) {
            mqttCommands.set(DOCKER, dockerMqttCommands);
        }
        return mqttCommands.isEmpty() ? null : mqttCommands;
    }

    private String getMqttPublishCommand(String protocol, String defaultHostname, String deviceTelemetryTopic, DeviceCredentials deviceCredentials) {
        if (MQTTS.equals(protocol) && deviceCredentials.getCredentialsType() == DeviceCredentialsType.X509_CERTIFICATE) {
            return CHECK_DOCUMENTATION;
        }
        DeviceConnectivityInfo properties = deviceConnectivityConfiguration.getConnectivity().get(protocol);
        if (properties == null || !properties.getEnabled()) {
            return null;
        }
        String mqttHost = properties.getHost().isEmpty() ? defaultHostname : properties.getHost();
        String mqttPort = properties.getPort().isEmpty() ? null : properties.getPort();
        return getMosquittoPubPublishCommand(protocol, mqttHost, mqttPort, deviceTelemetryTopic, deviceCredentials);
    }

    private String getDockerMqttPublishCommand(String protocol, String defaultHostname, String deviceTelemetryTopic, DeviceCredentials deviceCredentials) {
        DeviceConnectivityInfo properties = deviceConnectivityConfiguration.getConnectivity().get(protocol);
        if (properties == null || !properties.getEnabled()) {
            return null;
        }
        String mqttHost = properties.getHost().isEmpty() ? defaultHostname : properties.getHost();
        String mqttPort = properties.getPort().isEmpty() ? null : properties.getPort();
        return getDockerMosquittoClientsPublishCommand(protocol, mqttHost, mqttPort, deviceTelemetryTopic, deviceCredentials);
    }

    private JsonNode getCoapTransportPublishCommands(String defaultHostname, DeviceCredentials deviceCredentials) {
        ObjectNode coapCommands = JacksonUtil.newObjectNode();

        Optional.ofNullable(getCoapPublishCommand(LINUX, COAP, defaultHostname, deviceCredentials))
                .ifPresent(v -> coapCommands.put(COAP, v));
        Optional.ofNullable(getCoapPublishCommand(LINUX, COAPS, defaultHostname, deviceCredentials))
                .ifPresent(v -> coapCommands.put(COAPS, v));

        return coapCommands.isEmpty() ? null : coapCommands;
    }

    private String getCoapPublishCommand(String os, String protocol, String defaultHostname, DeviceCredentials deviceCredentials) {
        if (COAPS.equals(protocol) && deviceCredentials.getCredentialsType() == DeviceCredentialsType.X509_CERTIFICATE) {
            return CHECK_DOCUMENTATION;
        }
        DeviceConnectivityInfo properties = deviceConnectivityConfiguration.getConnectivity().get(protocol);
        if (properties == null || !properties.getEnabled()) {
            return null;
        }
        String hostName = properties.getHost().isEmpty() ? defaultHostname : properties.getHost();
        String port = properties.getPort().isEmpty() ? "" : ":" + properties.getPort();

        switch (os) {
            case LINUX:
                return getCoapClientCommand(protocol, hostName, port, deviceCredentials);
            default:
                throw new IllegalArgumentException("Unsupported operating system: " + os);
        }
    }
}
