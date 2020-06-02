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
package org.thingsboard.integration.opcua;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfigBuilder;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.stack.client.UaTcpStackClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection;
import org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoredItemCreateRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.MonitoringParameters;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.core.util.ConversionUtil;
import org.thingsboard.integration.api.AbstractIntegration;
import org.thingsboard.integration.api.IntegrationContext;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.data.DownlinkData;
import org.thingsboard.integration.api.data.IntegrationDownlinkMsg;
import org.thingsboard.integration.api.data.IntegrationMetaData;
import org.thingsboard.integration.api.data.UplinkData;
import org.thingsboard.integration.api.data.UplinkMetaData;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgDataType;
import org.thingsboard.server.common.msg.TbMsgMetaData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by Valerii Sosliuk on 3/17/2018.
 */
@Slf4j
public class OpcUaIntegration extends AbstractIntegration<OpcUaIntegrationMsg> {

    private OpcUaServerConfiguration opcUaServerConfiguration;

    private volatile OpcUaClient client;
    private UaSubscription subscription;
    private Map<NodeId, OpcUaDevice> devices;
    private Map<NodeId, List<OpcUaDevice>> devicesByTags;
    private Map<Pattern, DeviceMapping> mappings;

    private final AtomicLong clientHandles = new AtomicLong(1L);
    // This variable describes the tsate of integration, whether it has to run or shut down, not the state of the opc-ua client
    private volatile boolean connected = false;
    private volatile boolean scheduleReconnect = false;
    private ScheduledFuture taskFuture;
    private volatile boolean stopped;

    @Override
    public void init(TbIntegrationInitParams params) throws Exception {
        super.init(params);
        if (!this.configuration.isEnabled()) {
            stopped = true;
            return;
        }
        stopped = false;
        opcUaServerConfiguration = mapper.readValue(
                mapper.writeValueAsString(configuration.getConfiguration().get("clientConfiguration")),
                OpcUaServerConfiguration.class);
        if (opcUaServerConfiguration.getMapping().isEmpty()) {
            throw new IllegalArgumentException("No mapping elements configured!");
        }
        this.devices = new ConcurrentHashMap<>();
        this.devicesByTags = new ConcurrentHashMap<>();
        opcUaServerConfiguration.getMapping().forEach(DeviceMapping::initMappingPatterns);
        this.mappings = opcUaServerConfiguration.getMapping().stream().collect(Collectors.toConcurrentMap(m -> Pattern.compile(m.getDeviceNodePattern()), Function.identity()));
        scheduleReconnect = true;
        connected = true;
        context.getScheduledExecutorService().execute(this::scanForDevices);
    }

    @Override
    protected void doValidateConfiguration(JsonNode configuration, boolean allowLocalNetworkHosts) {
        OpcUaServerConfiguration opcUaServerConfiguration;
        try {
            opcUaServerConfiguration = mapper.readValue(
                    mapper.writeValueAsString(configuration.get("clientConfiguration")),
                    OpcUaServerConfiguration.class);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            throw new IllegalArgumentException("Invalid OPC-UA Integration Configuration structure!");
        }
        if (!allowLocalNetworkHosts && isLocalNetworkHost(opcUaServerConfiguration.getHost())) {
            throw new IllegalArgumentException("Usage of local network host for OPC-UA server connection is not allowed!");
        }
    }

    @Override
    public void update(TbIntegrationInitParams params) throws Exception {
        destroy();
        init(params);
    }

    @Override
    public void process(OpcUaIntegrationMsg msg) {
        String status = "OK";
        Exception exception = null;
        try {
            doProcess(context, msg);
            integrationStatistics.incMessagesProcessed();
        } catch (Exception e) {
            log.warn("[{}] Failed to apply data converter function", this.configuration.getName(), e);
            exception = e;
            status = "ERROR";
        }
        if (!status.equals("OK")) {
            integrationStatistics.incErrorsOccurred();
        }
        if (configuration.isDebugMode()) {
            try {
                persistDebug(context, "Uplink", getUplinkContentType(), mapper.writeValueAsString(msg.toJson()), status, exception);
            } catch (Exception e) {
                log.warn("[{}] Failed to persist debug message", this.configuration.getName(), e);
            }
        }
    }

    private void doProcess(IntegrationContext context, OpcUaIntegrationMsg msg) throws Exception {
        Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
        mdMap.putAll(msg.getDeviceMetadata());
        List<UplinkData> uplinkDataList = convertToUplinkDataList(context, msg.getPayload(), new UplinkMetaData(getUplinkContentType(), mdMap));
        if (uplinkDataList != null) {
            for (UplinkData data : uplinkDataList) {
                log.trace("[{}] Processing uplink data: {}", this.configuration.getName(), data);
                processUplinkData(context, data);
            }
        }
    }

    @Override
    public void onDownlinkMsg(IntegrationDownlinkMsg downlink) {
        TbMsg msg = downlink.getTbMsg();
        logDownlink(context, "Downlink: " + msg.getType(), msg);
        if (downlinkConverter != null) {
            processDownLinkMsg(context, msg);
        }
    }

    protected void processDownLinkMsg(IntegrationContext context, TbMsg msg) {
        String status = "OK";
        Exception exception = null;
        try {
            if (doProcessDownLinkMsg(context, msg)) {
                integrationStatistics.incMessagesProcessed();
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to process downLink message", this.configuration.getName(), e);
            exception = e;
            status = "ERROR";
        }
        reportDownlinkError(context, msg, status, exception);
    }

    private boolean doProcessDownLinkMsg(IntegrationContext context, TbMsg msg) throws Exception {
        if (!connected || scheduleReconnect) {
            return false;
        }
        Map<String, String> mdMap = new HashMap<>(metadataTemplate.getKvMap());
        List<DownlinkData> result = downlinkConverter.convertDownLink(context.getDownlinkConverterContext(), Collections.singletonList(msg), new IntegrationMetaData(mdMap));
        List<WriteValue> writeValues = prepareWriteValues(result);
        List<CallMethodRequest> callMethods = prepareCallMethods(result);

        logOpcUaDownlink(context, writeValues, callMethods);

        if (!writeValues.isEmpty()) {
            client.write(writeValues);
        }
        if (!callMethods.isEmpty()) {
            client.call(callMethods);
        }

        return !writeValues.isEmpty() || !callMethods.isEmpty();
    }

    private void initClient(OpcUaServerConfiguration configuration) throws OpcUaIntegrationException {
        try {

            log.info("[{}] Initializing OPC-UA server connection to [{}:{}]!", this.configuration.getName(), configuration.getHost(), configuration.getPort());

            SecurityPolicy securityPolicy = SecurityPolicy.valueOf(configuration.getSecurity());
            IdentityProvider identityProvider = configuration.getIdentity().toProvider();

            EndpointDescription[] endpoints;
            String endpointUrl = "opc.tcp://" + configuration.getHost() + ":" + configuration.getPort();
            try {
                endpoints = UaTcpStackClient.getEndpoints(endpointUrl).get();
            } catch (ExecutionException e) {
                log.error("[{}] Failed to connect to provided endpoint!", this.configuration.getName(), e);
                throw new OpcUaIntegrationException("Failed to connect to provided endpoint: " + endpointUrl, e);
            }

            EndpointDescription endpoint = Arrays.stream(endpoints)
                    .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getSecurityPolicyUri()))
                    .findFirst().orElseThrow(() -> new Exception("no desired endpoints returned"));

            if (!endpoint.getEndpointUrl().equals(endpointUrl)) {
                endpoint = new EndpointDescription(
                        endpointUrl,
                        endpoint.getServer(),
                        endpoint.getServerCertificate(),
                        endpoint.getSecurityMode(),
                        endpoint.getSecurityPolicyUri(),
                        endpoint.getUserIdentityTokens(),
                        endpoint.getTransportProfileUri(),
                        endpoint.getSecurityLevel());
            }

            OpcUaClientConfigBuilder configBuilder = OpcUaClientConfig.builder()
                    .setApplicationName(LocalizedText.english(configuration.getApplicationName()))
                    .setApplicationUri(configuration.getApplicationUri())
                    .setEndpoint(endpoint)
                    .setIdentityProvider(identityProvider)
                    .setRequestTimeout(Unsigned.uint(configuration.getTimeoutInMillis()));

            if (securityPolicy != SecurityPolicy.None) {
                CertificateInfo certificate = OpcUaConfigurationTools.loadCertificate(configuration.getKeystore());
                configBuilder.setCertificate(certificate.getCertificate())
                        .setKeyPair(certificate.getKeyPair());
            }

            OpcUaClientConfig config = configBuilder.build();

            client = new OpcUaClient(config);
            client.connect().get();
            log.info("[{}] OPC-UA Client connected successfully!", this.configuration.getName());
            sendConnectionSucceededMessageToRuleEngine();
            subscription = client.getSubscriptionManager().createSubscription(1000.0).get();
        } catch (Exception e) {
            Throwable t = e;
            if (e instanceof ExecutionException) {
                t = e.getCause();
            }
            log.error("[{}] Failed to connect to OPC-UA server. Reason: {}", this.configuration.getName(), t.getMessage(), t);
            throw new OpcUaIntegrationException("Failed to connect to OPC-UA server. Reason: " + t.getMessage(), e);
        }
    }

    private void sendConnectionSucceededMessageToRuleEngine() {
        String messageType = "OPC_UA_INT_SUCCESS";
        log.info("[{}] Sending OPC-UA integration succeeded message to Rule Engine", this.configuration.getName());
        sendAlertToRuleEngine(messageType);
    }

    private void sendConnectionFailedMessageToRuleEngine() {
        String messageType = "OPC_UA_INT_FAILURE";
        log.warn("[{}] Sending OPC-UA integration failed message to Rule Engine", this.configuration.getName());
        sendAlertToRuleEngine(messageType);
    }

    private void sendAlertToRuleEngine(String messageType) {
        TbMsgMetaData tbMsgMetaData = new TbMsgMetaData();
        tbMsgMetaData.putValue("name", this.configuration.getName());
        tbMsgMetaData.putValue("id", this.configuration.getId().getId().toString());
        tbMsgMetaData.putValue("host", opcUaServerConfiguration.getHost());
        tbMsgMetaData.putValue("port", Integer.toString(opcUaServerConfiguration.getPort()));
        TbMsg tbMsg = TbMsg.newMsg(messageType, this.configuration.getId(), tbMsgMetaData, TbMsgDataType.JSON, "{}");

        if (context != null) {
            context.processCustomMsg(tbMsg, null);
        }
    }

    @Override
    public void destroy() {
        stopped = true;
        if (connected) {
            try {
                connected = false;
                disconnect();
            } catch (Exception e) {
                log.warn("[{}] Failed to disconnect", this.configuration.getName(), e);
            }
        }
        if (taskFuture != null) {
            taskFuture.cancel(true);
        }
    }

    private boolean reconnect() {
        try {
            devices.clear();
            devicesByTags.clear();
            disconnect();
            initClient(opcUaServerConfiguration);
            scheduleReconnect = false;
            return true;
        } catch (Exception e) {
            log.warn("[{}] Failed to reconnect", this.configuration.getName(), e);
            sendConnectionFailedMessageToRuleEngine();
            if (!stopped) {
                scheduleReconnect = true;
            } else {
                scheduleReconnect = false;
            }
        }
        return false;
    }

    private void disconnect() {
        if (client != null) {
            try {
                client.disconnect().get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Error: ", e);
            }
            client = null;
            log.info("[{}] OPC-UA client disconnected", this.configuration.getName());
        }
    }

    private void scheduleScan() {
        taskFuture = context.getScheduledExecutorService().schedule((Runnable) this::scanForDevices, opcUaServerConfiguration.getScanPeriodInSeconds(), TimeUnit.SECONDS);
    }


    private void scanForDevices() {
        if (stopped) {
            log.info("[{}] Integration is already stopped!", this.configuration.getId());
            return;
        }
        try {
            if (connected && scheduleReconnect && !reconnect()) {
                log.debug("[{}] Scheduling next scan in {} seconds!", this.configuration.getId(), opcUaServerConfiguration.getScanPeriodInSeconds());
                scheduleScan();
                return;
            }

            long startTs = System.currentTimeMillis();
            scanForDevices(new OpcUaNode(Identifiers.RootFolder, ""));
            log.debug("[{}] Device scan cycle completed in {} ms", this.configuration.getId(), (System.currentTimeMillis() - startTs));
            List<OpcUaDevice> deleted = devices.entrySet().stream().filter(kv -> kv.getValue().getScanTs() < startTs).map(Map.Entry::getValue).collect(Collectors.toList());
            if (deleted.size() > 0) {
                log.info("[{}] Devices {} are no longer available", this.configuration.getId(), deleted);
            }
            deleted.forEach(devices::remove);

            if (connected) {
                log.debug("[{}] Scheduling next scan in {} seconds!", this.configuration.getId(), opcUaServerConfiguration.getScanPeriodInSeconds());
                scheduleScan();
            }
        } catch (Throwable e) {
            log.warn("[{}] Periodic device scan failed!", this.configuration.getId(), e);
            scheduleReconnect = true;
            scheduleScan();
        }
    }

    private boolean scanForChildren(OpcUaNode opcUaNode, DeviceMapping deviceMapping) {
        if (opcUaNode.getFqn().equals("")) {
            return true;
        }
        if (deviceMapping.getMappingType() == DeviceMappingType.FQN) {
            String[] opcUaFqnLevels = opcUaNode.getFqn().split("\\.");
            for (int i = 0; i < opcUaFqnLevels.length; i++) {
                if (deviceMapping.getMappingPathPatterns().size() > i) {
                    Pattern pattern = deviceMapping.getMappingPathPatterns().get(i);
                    if (!pattern.matcher(opcUaFqnLevels[i]).matches()) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            return true;
        }
    }

    private boolean scanById(OpcUaNode node, Map.Entry<Pattern, DeviceMapping> mappingEntry) {
        if (mappingEntry.getValue().getNamespace() != null) {
            return node.getNodeId().getNamespaceIndex().intValue() == mappingEntry.getValue().getNamespace().intValue()
                    && mappingEntry.getKey().matcher(node.getNodeId().getIdentifier().toString()).matches();
        } else {
            return mappingEntry.getKey().matcher(node.getNodeId().getIdentifier().toString()).matches();
        }
    }

    private boolean scanByFqn(OpcUaNode node, Map.Entry<Pattern, DeviceMapping> mappingEntry) {
        return mappingEntry.getKey().matcher(node.getFqn()).matches();
    }

    private BiFunction<OpcUaNode, Map.Entry<Pattern, DeviceMapping>, Boolean> getMatchingFunction(DeviceMappingType mappingType) {
        switch (mappingType) {
            case ID:
                return this::scanById;
            case FQN:
                return this::scanByFqn;
            default:
                throw new IllegalArgumentException("unknown DeviceMappingType: " + mappingType);
        }
    }


    private void scanForDevices(OpcUaNode node) {
        log.debug("[{}] Scanning node: {}", this.configuration.getName(), node);
        List<DeviceMapping> matchedMappings = new ArrayList<>();
        boolean scanChildren = false;
        for (Map.Entry<Pattern, DeviceMapping> mappingEntry : mappings.entrySet()) {
            if (getMatchingFunction(mappingEntry.getValue().getMappingType()).apply(node, mappingEntry)) {
                matchedMappings.add(mappingEntry.getValue());
            } else {
                scanChildren = scanChildren || scanForChildren(node, mappingEntry.getValue());
            }
        }

        matchedMappings.forEach(m -> {
            try {
                log.debug("[{}] Matched mapping: [{}]", this.configuration.getName(), m);
                scanDevice(node, m);
            } catch (Exception e) {
                log.error("[{}] Failed to scan device: {}", this.configuration.getName(), node, e);
                scheduleReconnect = true;
                scheduleScan();
            }
        });
        if (scanChildren) {
            try {
                if (client == null) {
                    initClient(opcUaServerConfiguration);
                    scheduleReconnect = false;
                }
                BrowseResult browseResult = client.browse(getBrowseDescription(node.getNodeId())).get();
                List<ReferenceDescription> references = ConversionUtil.toList(browseResult.getReferences());
                for (ReferenceDescription rd : references) {
                    if (rd.getNodeId().isLocal()) {
                        NodeId childNodeId = rd.getNodeId().local().get();
                        OpcUaNode childNode = new OpcUaNode(node, childNodeId, rd.getBrowseName().getName());
                        scanForDevices(childNode);
                    } else {
                        log.trace("[{}] Ignoring remote node: {}", this.configuration.getName(), rd.getNodeId());
                    }
                }
            } catch (Exception e) {
                if (connected) {
                    log.error("[{}] Browsing nodeId={} failed: {}", this.configuration.getName(), node, e.getMessage(), e);
                    sendConnectionFailedMessageToRuleEngine();
                    scheduleReconnect = true;
                    scheduleScan();
                }
            }
        } else {
            log.debug("[{}] Skip scanning children for node: {}", this.configuration.getName(), node);
        }
    }

    private void scanDevice(OpcUaNode node, DeviceMapping m) throws Exception {
        log.debug("[{}] Scanning device node: {}", this.configuration.getName(), node);
        Set<String> tags = m.getAllTags();
        log.debug("[{}] Scanning node hierarchy for tags: {}", this.configuration.getName(), tags);
        Map<String, NodeId> tagMap = lookupTags(node.getNodeId(), node.getName(), tags);
        log.debug("[{}] Scanned {} tags out of {}", this.configuration.getName(), tagMap.size(), tags.size());

        OpcUaDevice device;
        if (devices.containsKey(node.getNodeId())) {
            device = devices.get(node.getNodeId());
        } else {
            device = new OpcUaDevice(node, m);
            devices.put(node.getNodeId(), device);
            Map<String, NodeId> newTags = device.registerTags(tagMap);
            if (newTags.size() > 0) {
                for (NodeId tagId : newTags.values()) {
                    devicesByTags.computeIfAbsent(tagId, key -> new ArrayList<>()).add(device);
                    VariableNode varNode = client.getAddressSpace().createVariableNode(tagId);
                    DataValue dataValue = varNode.readValue().get();
                    if (dataValue != null) {
                        device.updateTag(tagId, dataValue);
                    }
                }
                log.debug("[{}] Going to subscribe to tags: {}", this.configuration.getName(), newTags);
                subscribeToTags(newTags);
            }
            onDeviceDataUpdate(device, null);
        }

        device.updateScanTs();

        Map<String, NodeId> newTags = device.registerTags(tagMap);
        if (newTags.size() > 0) {
            for (NodeId tagId : newTags.values()) {
                devicesByTags.computeIfAbsent(tagId, key -> new ArrayList<>()).add(device);
            }
            log.debug("[{}] Going to subscribe to tags: {}", this.configuration.getName(), newTags);
            subscribeToTags(newTags);
        }
    }

    private void onDeviceDataUpdate(OpcUaDevice device, NodeId affectedTagId) {
        OpcUaIntegrationMsg message = device.prepareMsg(affectedTagId);
        if (context != null) {
            process(message);
        }
    }

    private void subscribeToTags(Map<String, NodeId> newTags) throws InterruptedException, ExecutionException, TimeoutException {
        List<MonitoredItemCreateRequest> requests = new ArrayList<>();
        for (Map.Entry<String, NodeId> kv : newTags.entrySet()) {
            // subscribe to the Value attribute of the server's CurrentTime node
            ReadValueId readValueId = new ReadValueId(
                    kv.getValue(),
                    AttributeId.Value.uid(), null, QualifiedName.NULL_VALUE);
            // important: client handle must be unique per item
            UInteger clientHandle = Unsigned.uint(clientHandles.getAndIncrement());

            MonitoringParameters parameters = new MonitoringParameters(
                    clientHandle,
                    1000.0,     // sampling interval
                    null,       // filter, null means use default
                    Unsigned.uint(10),   // queue size
                    true        // discard oldest
            );

            requests.add(new MonitoredItemCreateRequest(
                    readValueId, MonitoringMode.Reporting, parameters));
        }

        BiConsumer<UaMonitoredItem, Integer> onItemCreated =
                (item, id) -> item.setValueConsumer(this::onSubscriptionValue);

        List<UaMonitoredItem> items = subscription.createMonitoredItems(
                TimestampsToReturn.Both,
                requests,
                onItemCreated
        ).get(5, TimeUnit.SECONDS);

        for (UaMonitoredItem item : items) {
            if (item.getStatusCode().isGood()) {
                log.trace("[{}] Monitoring Item created for nodeId={}", this.configuration.getName(), item.getReadValueId().getNodeId());
            } else {
                log.warn("[{}] Failed to create item for nodeId={} (status={})", this.configuration.getName(),
                        item.getReadValueId().getNodeId(), item.getStatusCode());
            }
        }
    }

    private void onSubscriptionValue(UaMonitoredItem item, DataValue dataValue) {
        try {
            if (context != null && !context.isClosed()) {
                log.debug("[{}] Subscription value received: item={}, value={}", this.configuration.getName(),
                        item.getReadValueId().getNodeId(), dataValue.getValue());
                NodeId tagId = item.getReadValueId().getNodeId();
                devicesByTags.getOrDefault(tagId, Collections.emptyList()).forEach(
                        device -> {
                            device.updateTag(tagId, dataValue);
                            onDeviceDataUpdate(device, tagId);
                        }
                );
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to process subscription value [{}][{}]", this.configuration.getName(), item.getReadValueId().getNodeId(), item.getStatusCode());
        }
    }

    private Map<String, NodeId> lookupTags(NodeId nodeId, String deviceNodeName, Set<String> tags) {
        Map<String, NodeId> values = new HashMap<>();
        try {
            BrowseResult browseResult = client.browse(getBrowseDescription(nodeId)).get(5, TimeUnit.SECONDS);
            List<ReferenceDescription> references = ConversionUtil.toList(browseResult.getReferences());

            for (ReferenceDescription rd : references) {
                NodeId childId;
                if (rd.getNodeId().isLocal()) {
                    childId = rd.getNodeId().local().get();
                } else {
                    log.trace("[{}] Ignoring remote node: {}", this.configuration.getName(), rd.getNodeId());
                    continue;
                }

                String name;
                String childIdStr = childId.getIdentifier().toString();
                if (childIdStr.contains(deviceNodeName)) {
                    name = childIdStr.substring(childIdStr.indexOf(deviceNodeName) + deviceNodeName.length() + 1, childIdStr.length());
                } else {
                    name = rd.getBrowseName().getName();
                }
                log.trace("[{}] Found tag: [{}].[{}]", this.configuration.getName(), nodeId, name);
                if (tags.contains(name)) {
                    values.put(name, childId);
                }
                // recursively browse children
                values.putAll(lookupTags(childId, deviceNodeName, tags));
            }
        } catch (Exception e) {
            log.error("[{}] Browsing nodeId={} failed: {}", this.configuration.getName(), nodeId, e.getMessage(), e);
        }
        return values;
    }

    private BrowseDescription getBrowseDescription(NodeId nodeId) {
        return new BrowseDescription(
                nodeId,
                BrowseDirection.Forward,
                Identifiers.References,
                true,
                Unsigned.uint(NodeClass.Object.getValue() | NodeClass.Variable.getValue()),
                Unsigned.uint(BrowseResultMask.All.getValue())
        );
    }

    private List<WriteValue> prepareWriteValues(List<DownlinkData> dataList) {
        List<WriteValue> writeValuesList = new ArrayList<>();
        for (DownlinkData data : dataList) {
            if (!data.isEmpty() && data.getContentType().equals("JSON")) {
                try {
                    JsonNode payload = mapper.readTree(data.getData());
                    if (payload.has("writeValues")) {
                        JsonNode writeValues = payload.get("writeValues");
                        if (writeValues.isArray()) {
                            for (JsonNode writeValueJson : writeValues) {
                                Optional<NodeId> nodeId = Optional.empty();
                                Optional<Variant> value = Optional.empty();
                                if (writeValueJson.has("nodeId")) {
                                    try {
                                        nodeId = NodeId.parseSafe(writeValueJson.get("nodeId").asText());
                                    } catch (Exception e) {
                                        log.error("[{}] Browsing nodeId={} failed: {}", this.configuration.getName(), nodeId, e.getMessage(), e);
                                    }
                                }
                                if (writeValueJson.has("value")) {
                                    JsonNode valueJson = writeValueJson.get("value");
                                    value = extractValue(valueJson);
                                }
                                if (nodeId.isPresent() && value.isPresent()) {
                                    WriteValue writeValue = new WriteValue(
                                            nodeId.get(), AttributeId.Value.uid(), null, DataValue.valueOnly(value.get()));
                                    writeValuesList.add(writeValue);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[{}] Preparing write values failed: {}", this.configuration.getName(), e.getMessage(), e);
                }
            }
        }
        return writeValuesList;
    }

    private List<CallMethodRequest> prepareCallMethods(List<DownlinkData> dataList) {
        List<CallMethodRequest> callMethodRequests = new ArrayList<>();
        for (DownlinkData data : dataList) {
            if (!data.isEmpty() && data.getContentType().equals("JSON")) {
                try {
                    JsonNode payload = mapper.readTree(data.getData());
                    if (payload.has("callMethods")) {
                        JsonNode callMethods = payload.get("callMethods");
                        if (callMethods.isArray()) {
                            for (JsonNode callMethodJson : callMethods) {
                                Optional<NodeId> objectId = Optional.empty();
                                Optional<NodeId> methodId = Optional.empty();
                                Optional<Variant[]> arguments = Optional.empty();
                                if (callMethodJson.has("objectId")) {
                                    try {
                                        objectId = NodeId.parseSafe(callMethodJson.get("objectId").asText());
                                    } catch (Exception e) {
                                        log.error("[{}] Parsing safe {}", this.configuration.getName(), e.getMessage(), e);
                                    }
                                }
                                if (callMethodJson.has("methodId")) {
                                    try {
                                        methodId = NodeId.parseSafe(callMethodJson.get("methodId").asText());
                                    } catch (Exception e) {
                                        log.error("[{}]Parsing safe {}", this.configuration.getName(), e.getMessage(), e);
                                    }
                                }
                                if (callMethodJson.has("args")) {
                                    JsonNode argsJson = callMethodJson.get("args");
                                    if (argsJson.isArray()) {
                                        List<Variant> argsList = new ArrayList<>();
                                        for (JsonNode argJson : argsJson) {
                                            Optional<Variant> value = extractValue(argJson);
                                            value.ifPresent(argsList::add);
                                        }
                                        arguments = Optional.of(argsList.toArray(new Variant[]{}));
                                    }
                                }
                                if (objectId.isPresent() && methodId.isPresent()) {
                                    Variant[] args = arguments.isPresent() ? arguments.get() : new Variant[]{};
                                    CallMethodRequest callMethodRequest = new CallMethodRequest(objectId.get(), methodId.get(), args);
                                    callMethodRequests.add(callMethodRequest);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[{}] PrepareCallMethods {}", this.configuration.getName(), e.getMessage(), e);
                }
            }
        }
        return callMethodRequests;
    }

    private Optional<Variant> extractValue(JsonNode valueJson) {
        Object val = null;
        if (valueJson.isValueNode()) {
            if (valueJson.isTextual()) {
                val = valueJson.asText();
            } else if (valueJson.isInt()) {
                val = valueJson.asInt();
            } else if (valueJson.isLong()) {
                val = valueJson.asLong();
            } else if (valueJson.isFloatingPointNumber()) {
                val = valueJson.asDouble();
            } else if (valueJson.isBoolean()) {
                val = valueJson.asBoolean();
            }
        }
        if (val != null) {
            return Optional.of(new Variant(val));
        }
        return Optional.empty();
    }

    private void logOpcUaDownlink(IntegrationContext context, List<WriteValue> writeValues, List<CallMethodRequest> callMethods) {
        if (configuration.isDebugMode() && (!writeValues.isEmpty() || !callMethods.isEmpty())) {
            try {
                ObjectNode json = mapper.createObjectNode();
                if (!writeValues.isEmpty()) {
                    json.set("writeValues", toJsonStringList(writeValues));
                }
                if (!callMethods.isEmpty()) {
                    json.set("callMethods", toJsonStringList(callMethods));
                }
                persistDebug(context, "Downlink", "JSON", mapper.writeValueAsString(json), downlinkConverter != null ? "OK" : "FAILURE", null);
            } catch (Exception e) {
                log.warn("[{}] Failed to persist debug message", this.configuration.getName(), e);
            }
        }
    }

    private JsonNode toJsonStringList(List<?> list) {
        ArrayNode arrayNode = mapper.createArrayNode();
        for (Object item : list) {
            arrayNode.add(item.toString());
        }
        return arrayNode;
    }

}
