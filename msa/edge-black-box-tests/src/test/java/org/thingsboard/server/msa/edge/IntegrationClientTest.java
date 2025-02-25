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
package org.thingsboard.server.msa.edge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.ResponseEntity;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.converter.ConverterType;
import org.thingsboard.server.common.data.id.IdBased;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.rule.NodeConnectionInfo;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.rule.RuleChainMetaData;
import org.thingsboard.server.common.data.rule.RuleNode;
import org.thingsboard.server.msa.AbstractContainerTest;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
public class IntegrationClientTest extends AbstractContainerTest {

    @Test
    public void testIntegrations() {
        performTestOnEachEdge(this::_testIntegrations);
    }

    private void _testIntegrations() {
        JsonNode edgeAttributes = JacksonUtil.toJsonNode("{\"valAttr\":\"val3\", \"baseUrl\":\"" + edgeUrl + "\"}");
        cloudRestClient.saveEntityAttributesV1(edge.getId(), DataConstants.SERVER_SCOPE, edgeAttributes);

        ObjectNode converterConfiguration = JacksonUtil.newObjectNode()
                .put("decoder", "return {deviceName: 'Device Converter ' + metadata['key'], deviceType: 'thermostat'};");
        Converter converter = new Converter();
        converter.setName("My converter");
        converter.setType(ConverterType.UPLINK);
        converter.setConfiguration(converterConfiguration);
        converter.setEdgeTemplate(true);
        Converter savedConverter = cloudRestClient.saveConverter(converter);

        Integration integration = new Integration();
        integration.setName("Edge integration");
        integration.setAllowCreateDevicesOrAssets(true);
        integration.setRoutingKey(StringUtils.randomAlphanumeric(15));
        integration.setDefaultConverterId(savedConverter.getId());
        integration.setType(IntegrationType.HTTP);

        ObjectNode integrationConfiguration = JacksonUtil.newObjectNode();
        integrationConfiguration.putObject("metadata").put("key", "val1");
        integrationConfiguration.put("baseUrl", "${{baseUrl}}");
        integration.setConfiguration(integrationConfiguration);
        integration.setEdgeTemplate(true);
        integration.setEnabled(true);
        Integration savedIntegration = cloudRestClient.saveIntegration(integration);

        validateIntegrationAssignToEdge(savedIntegration);

        verifyHttpIntegrationUpAndRunning(savedIntegration, "Device Converter val1", true);

        savedIntegration = validateIntegrationConfigurationUpdate(savedIntegration);

        savedIntegration = validateEdgeAttributesUpdate(savedIntegration);

        savedIntegration = validateIntegrationDefaultConverterUpdate(savedIntegration);

        savedIntegration = validateIntegrationDownlinkConverterUpdate(savedIntegration);

        validateIntegrationUnassignFromEdge(savedIntegration);

        validateRemoveOfIntegration(savedIntegration);
    }

    private void verifyHttpIntegrationUpAndRunning(Integration integration, String expectedDeviceName, boolean cleanup) {
        try {
            SECONDS.sleep(1); // wait for integration to be recompiled and restarted with updated config
        } catch (Throwable ignored) {}

        ObjectNode values = JacksonUtil.newObjectNode();
        values.put("deviceName", expectedDeviceName);
        edgeRestClient.getRestTemplate().postForEntity(edgeUrl + "/api/v1/integrations/http/" + integration.getRoutingKey(),
                values,
                ResponseEntity.class);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<Device> tenantDevice = cloudRestClient.getTenantDevice(expectedDeviceName);
                    return tenantDevice.isPresent();
                });

        if (cleanup) {
            Optional<Device> device = cloudRestClient.getTenantDevice(expectedDeviceName);
            cloudRestClient.deleteDevice(device.get().getId());
        }
    }

    private void validateIntegrationAssignToEdge(Integration savedIntegration) {
        cloudRestClient.assignIntegrationToEdge(edge.getId(), savedIntegration.getId());

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getIntegrations(new PageLink(100)).getTotalElements() == 1);

        PageData<Converter> converters = edgeRestClient.getConverters(new PageLink(100));
        assertEntitiesByIdsAndType(converters.getData().stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.CONVERTER);

        PageData<Integration> integrations = edgeRestClient.getIntegrations(new PageLink(100));
        assertEntitiesByIdsAndType(integrations.getData().stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.INTEGRATION);
    }

    private Integration validateIntegrationConfigurationUpdate(Integration savedIntegration) {
        ObjectNode updatedIntegrationConfig = JacksonUtil.newObjectNode();
        updatedIntegrationConfig.putObject("metadata").put("key", "val2");
        savedIntegration.setConfiguration(updatedIntegrationConfig);
        savedIntegration = cloudRestClient.saveIntegration(savedIntegration);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    PageData<Integration> integrations = edgeRestClient.getIntegrations(new PageLink(100));
                    Integration integration = integrations.getData().get(0);
                    return updatedIntegrationConfig.equals(integration.getConfiguration());
                });

        verifyHttpIntegrationUpAndRunning(savedIntegration, "Device Converter val2", true);

        return savedIntegration;
    }

    private Integration validateEdgeAttributesUpdate(Integration savedIntegration) {
        ObjectNode updatedIntegrationConfig = JacksonUtil.newObjectNode();
        updatedIntegrationConfig.putObject("metadata").put("key", "${{valAttr}}");
        savedIntegration.setConfiguration(updatedIntegrationConfig);
        savedIntegration = cloudRestClient.saveIntegration(savedIntegration);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    PageData<Integration> integrations = edgeRestClient.getIntegrations(new PageLink(100));
                    Integration integration = integrations.getData().get(0);
                    return integration.getConfiguration().toString().contains("val3");
                });

        verifyHttpIntegrationUpAndRunning(savedIntegration, "Device Converter val3", true);

        JsonNode edgeAttributes = JacksonUtil.toJsonNode("{\"valAttr\":\"val4\", \"baseUrl\":\"" + edgeUrl + "\"}");
        cloudRestClient.saveEntityAttributesV1(edge.getId(), DataConstants.SERVER_SCOPE, edgeAttributes);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    PageData<Integration> integrations = edgeRestClient.getIntegrations(new PageLink(100));
                    Integration integration = integrations.getData().get(0);
                    return integration.getConfiguration().toString().contains("val4");
                });

        verifyHttpIntegrationUpAndRunning(savedIntegration, "Device Converter val4", true);

        return savedIntegration;
    }

    private Integration validateIntegrationDefaultConverterUpdate(Integration savedIntegration) {
        ObjectNode newConverterConfiguration = JacksonUtil.newObjectNode()
                .put("decoder", "return {deviceName: 'Device Converter val5', deviceType: 'default'};");
        Converter converter = new Converter();
        converter.setName("My new converter");
        converter.setType(ConverterType.UPLINK);
        converter.setConfiguration(newConverterConfiguration);
        converter.setEdgeTemplate(true);
        Converter newSavedConverter = cloudRestClient.saveConverter(converter);

        savedIntegration.setDefaultConverterId(newSavedConverter.getId());
        savedIntegration = cloudRestClient.saveIntegration(savedIntegration);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    PageData<Integration> integrations = edgeRestClient.getIntegrations(new PageLink(100));
                    Integration integration = integrations.getData().get(0);
                    return newSavedConverter.getId().equals(integration.getDefaultConverterId());
                });

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getConverters(new PageLink(100)).getTotalElements() == 1);

        verifyHttpIntegrationUpAndRunning(savedIntegration, "Device Converter val5", false);

        return savedIntegration;
    }

    private Integration validateIntegrationDownlinkConverterUpdate(Integration savedIntegration) {
        ObjectNode downlinkConverterConfiguration = JacksonUtil.newObjectNode()
                .put("encoder", "return {contentType: 'JSON', data: '{\"pin\": 3}'};");
        Converter converter = new Converter();
        converter.setName("My downlink converter");
        converter.setType(ConverterType.DOWNLINK);
        converter.setConfiguration(downlinkConverterConfiguration);
        converter.setEdgeTemplate(true);
        Converter savedDownlinkConverter = cloudRestClient.saveConverter(converter);

        savedIntegration.setDownlinkConverterId(savedDownlinkConverter.getId());
        savedIntegration = cloudRestClient.saveIntegration(savedIntegration);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    PageData<Integration> integrations = edgeRestClient.getIntegrations(new PageLink(100));
                    Integration integration = integrations.getData().get(0);
                    return savedDownlinkConverter.getId().equals(integration.getDownlinkConverterId());
                });

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getConverters(new PageLink(100)).getTotalElements() == 2);

        addIntegrationDownlinkRuleNodeToEdgeRootRuleChain(savedIntegration);

        String deviceName = "Device Converter val5";
        sendRpcRequestToDevice(deviceName);

        sendHttpUplinkAndVerifyDownlink(savedIntegration);

        savedIntegration.setDownlinkConverterId(null);
        savedIntegration = cloudRestClient.saveIntegration(savedIntegration);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getConverters(new PageLink(100)).getTotalElements() == 1);

        Optional<Device> device = cloudRestClient.getTenantDevice(deviceName);
        cloudRestClient.deleteDevice(device.get().getId());

        return savedIntegration;
    }

    private void sendHttpUplinkAndVerifyDownlink(Integration savedIntegration) {
        ObjectNode values = JacksonUtil.newObjectNode();
        values.put("result", "ok");
        ResponseEntity<JsonNode> responseEntityResponseEntity =
                edgeRestClient.getRestTemplate().postForEntity(
                        edgeUrl + "/api/v1/integrations/http/" + savedIntegration.getRoutingKey(),
                        values,
                        JsonNode.class);
        Assert.assertNotNull(responseEntityResponseEntity.getBody());
        Assert.assertEquals(3, responseEntityResponseEntity.getBody().get("pin").asInt());
    }

    private void sendRpcRequestToDevice(String deviceName) {
        Optional<Device> tenantDeviceOpt = edgeRestClient.getTenantDevice(deviceName);
        Assert.assertTrue(tenantDeviceOpt.isPresent());
        Device device = tenantDeviceOpt.get();

        ObjectNode rpcRequest = JacksonUtil.newObjectNode();
        rpcRequest.put("method", "rpcCommand");
        rpcRequest.set("params", JacksonUtil.newObjectNode());
        rpcRequest.put("persistent", false);
        rpcRequest.put("timeout", 5000);
        edgeRestClient.handleOneWayDeviceRPCRequest(device.getId(), rpcRequest);

        try {
            SECONDS.sleep(1); // wait for rpc request to be stored in downlink cache
        } catch (Throwable ignored) {}
    }

    private void addIntegrationDownlinkRuleNodeToEdgeRootRuleChain(Integration savedIntegration) {
        RuleChain rootRuleChain = getEdgeRootRuleChain();
        Assert.assertNotNull(rootRuleChain);

        addIntegrationDownlinkRuleNode(rootRuleChain, savedIntegration);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> {
                    Optional<RuleChainMetaData> ruleChainMetaDataOpt = edgeRestClient.getRuleChainMetaData(rootRuleChain.getId());
                    if (ruleChainMetaDataOpt.isPresent()) {
                        RuleChainMetaData ruleChainMetaData = ruleChainMetaDataOpt.get();
                        for (RuleNode node : ruleChainMetaData.getNodes()) {
                            if (node.getType().equals("org.thingsboard.rule.engine.integration.TbIntegrationDownlinkNode")) {
                                return true;
                            }
                        }
                    }
                    return false;
                });

        try {
            SECONDS.sleep(1); // wait for edge root rule chain to be restarted with updated config
        } catch (Throwable ignored) {}
    }

    private void addIntegrationDownlinkRuleNode(RuleChain rootRuleChain, Integration savedIntegration) {
        Optional<RuleChainMetaData> ruleChainMetaDataOpt = cloudRestClient.getRuleChainMetaData(rootRuleChain.getId());
        if (ruleChainMetaDataOpt.isPresent()) {
            RuleChainMetaData ruleChainMetaData = ruleChainMetaDataOpt.get();

            int msgTypeSwitchIdx = 0;
            for (RuleNode node : ruleChainMetaData.getNodes()) {
                if (node.getType().equals("org.thingsboard.rule.engine.filter.TbMsgTypeSwitchNode")) {
                    break;
                }
                msgTypeSwitchIdx = msgTypeSwitchIdx + 1;
            }

            RuleNode ruleNode = new RuleNode();
            ruleNode.setName("Integration Downlink");
            ruleNode.setType("org.thingsboard.rule.engine.integration.TbIntegrationDownlinkNode");
            ruleNode.setDebugMode(true);
            ObjectNode configuration = JacksonUtil.newObjectNode();
            configuration.put("integrationId", savedIntegration.getId().getId().toString());
            ruleNode.setConfiguration(configuration);

            ObjectNode additionalInfo = JacksonUtil.newObjectNode();
            additionalInfo.put("layoutX", 514);
            additionalInfo.put("layoutY", 511);
            additionalInfo.put("additionalInfo", "");
            ruleNode.setAdditionalInfo(additionalInfo);

            ruleNode.setRuleChainId(rootRuleChain.getId());
            ruleChainMetaData.getNodes().add(ruleNode);

            NodeConnectionInfo e = new NodeConnectionInfo();
            e.setType("RPC Request to Device");
            e.setFromIndex(msgTypeSwitchIdx);
            e.setToIndex(ruleChainMetaData.getNodes().size() - 1);
            ruleChainMetaData.getConnections().add(e);

            cloudRestClient.saveRuleChainMetaData(ruleChainMetaData);
        }
    }

    private RuleChain getEdgeRootRuleChain() {
        PageData<RuleChain> edgeRuleChains = cloudRestClient.getEdgeRuleChains(edge.getId(), new PageLink(1024));
        for (RuleChain edgeRuleChain : edgeRuleChains.getData()) {
            if (edgeRuleChain.isRoot()) {
                return edgeRuleChain;
            }
        }
        return null;
    }

    private void validateIntegrationUnassignFromEdge(Integration savedIntegration) {
        cloudRestClient.unassignIntegrationFromEdge(edge.getId(), savedIntegration.getId());

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getIntegrations(new PageLink(100)).getTotalElements() == 0);

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getConverters(new PageLink(100)).getTotalElements() == 0);
    }

    private void validateRemoveOfIntegration(Integration savedIntegration) {
        cloudRestClient.deleteIntegration(savedIntegration.getId());

        Awaitility.await()
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .atMost(30, TimeUnit.SECONDS).
                until(() -> edgeRestClient.getIntegrations(new PageLink(100), true).getTotalElements() == 0);

        PageData<Converter> converters = cloudRestClient.getConverters(new PageLink(100), true);
        for (Converter datum : converters.getData()) {
            cloudRestClient.deleteConverter(datum.getId());
        }
    }

}

