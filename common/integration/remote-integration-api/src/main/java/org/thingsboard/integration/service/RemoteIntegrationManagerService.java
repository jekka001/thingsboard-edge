/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.EventUtil;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.common.util.ThingsBoardThreadFactory;
import org.thingsboard.integration.api.IntegrationStatistics;
import org.thingsboard.integration.api.TbIntegrationInitParams;
import org.thingsboard.integration.api.ThingsboardPlatformIntegration;
import org.thingsboard.integration.api.converter.ScriptDownlinkDataConverter;
import org.thingsboard.integration.api.converter.ScriptUplinkDataConverter;
import org.thingsboard.integration.api.converter.TBDataConverter;
import org.thingsboard.integration.api.converter.TBDownlinkDataConverter;
import org.thingsboard.integration.api.converter.TBUplinkDataConverter;
import org.thingsboard.integration.api.data.DefaultIntegrationDownlinkMsg;
import org.thingsboard.integration.api.util.IntegrationUtil;
import org.thingsboard.integration.api.util.LogSettingsComponent;
import org.thingsboard.integration.remote.RemoteIntegrationContext;
import org.thingsboard.integration.rpc.IntegrationRpcClient;
import org.thingsboard.integration.storage.EventStorage;
import org.thingsboard.script.api.js.JsInvokeService;
import org.thingsboard.script.api.mvel.MvelInvokeService;
import org.thingsboard.server.coapserver.CoapServerService;
import org.thingsboard.server.common.data.FSTUtils;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.converter.ConverterType;
import org.thingsboard.server.common.data.event.LifecycleEvent;
import org.thingsboard.server.common.data.event.StatisticsEvent;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.common.data.plugin.ComponentLifecycleEvent;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.queue.TbMsgCallback;
import org.thingsboard.server.gen.integration.ConverterConfigurationProto;
import org.thingsboard.server.gen.integration.DeviceDownlinkDataProto;
import org.thingsboard.server.gen.integration.IntegrationConfigurationProto;
import org.thingsboard.server.gen.integration.IntegrationStatisticsProto;
import org.thingsboard.server.gen.integration.TbEventProto;
import org.thingsboard.server.gen.integration.TbEventSource;
import org.thingsboard.server.gen.integration.UplinkMsg;
import org.thingsboard.server.gen.transport.TransportProtos.KeyValueProto;
import org.thingsboard.server.gen.transport.TransportProtos.KeyValueType;
import org.thingsboard.server.gen.transport.TransportProtos.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TsKvListProto;
import org.thingsboard.server.queue.util.TbIntegrationComponent;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service("RemoteIntegrationManagerService")
@TbIntegrationComponent
@Slf4j
@Data
public class RemoteIntegrationManagerService {

    public static final ObjectMapper mapper = new ObjectMapper();

    @Value("${rpc.client_id}")
    private String clientId;

    @Value("${server.port}")
    private int port;

    @Value("${integration.routingKey}")
    private String routingKey;

    @Value("${integration.secret}")
    private String routingSecret;

    @Value("${integration.allow_local_network_hosts:true}")
    private boolean allowLocalNetworkHosts;

    @Value("${executors.reconnect_timeout}")
    private long reconnectTimeoutMs;

    @Value("${integration.statistics.enabled}")
    private boolean statisticsEnabled;

    @Value("${integration.statistics.persist_frequency}")
    private long statisticsPersistFrequency;

    @Autowired
    private IntegrationRpcClient rpcClient;

    @Autowired
    private EventStorage eventStorage;

    @Autowired
    private JsInvokeService jsInvokeService;

    @Autowired(required = false)
    private MvelInvokeService mvelInvokeService;

    @Autowired
    private LogSettingsComponent logSettingsComponent;

    @Autowired(required = false)
    private CoapServerService coapServerService;

    private ThingsboardPlatformIntegration<?> integration;
    private ComponentLifecycleEvent integrationEvent;

    private TBUplinkDataConverter uplinkDataConverter;
    private TBDownlinkDataConverter downlinkDataConverter;

    private ConverterId uplinkConverterId;
    private ConverterId downlinkConverterId;

    private ExecutorService executor;
    private ScheduledExecutorService reconnectScheduler;
    private ScheduledExecutorService statisticsExecutorService;
    private ScheduledExecutorService schedulerService;
    private ExecutorService generalExecutorService;
    private ScheduledFuture<?> scheduledFuture;
    private ExecutorService callBackExecutorService;

    private volatile boolean initialized;
    private volatile boolean updatingIntegration;

    private String serviceId;

    @PostConstruct
    public void init() {
        if (StringUtils.isBlank(routingKey)) {
            log.error("The routing key is blank. Please define 'INTEGRATION_ROUTING_KEY' environment variable!");
            System.exit(-1);
        }
        if (StringUtils.isBlank(routingSecret)) {
            log.error("The routing secret is blank. Please define 'INTEGRATION_SECRET' environment variable!");
            System.exit(-1);
        }
        if ("PUT_YOUR_ROUTING_KEY_HERE".equals(routingKey)) {
            log.error("The routing key is default. Please define 'INTEGRATION_ROUTING_KEY' environment variable!");
            System.exit(-1);
        }
        if ("PUT_YOUR_SECRET_HERE".equals(routingSecret)) {
            log.error("The routing secret is default. Please define 'INTEGRATION_SECRET' environment variable!");
            System.exit(-1);
        }
        serviceId = "[" + clientId + ":" + port + "]";
        rpcClient.connect(routingKey, routingSecret, serviceId, this::onConfigurationUpdate, this::onConverterConfigurationUpdate, this::onDownlink, this::scheduleReconnect);
        executor = Executors.newSingleThreadExecutor(ThingsBoardThreadFactory.forName("remote-integration-manager-service"));
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("remote-integration-manager-service-reconnect"));
        schedulerService = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("remote-integration-manager-service-scheduler"));
        generalExecutorService = ThingsBoardExecutors.newWorkStealingPool(Math.max(2, Runtime.getRuntime().availableProcessors()), "remote-integration-general");
        callBackExecutorService = ThingsBoardExecutors.newWorkStealingPool(Math.max(2, Runtime.getRuntime().availableProcessors()), "remote-integration-callback");
        processHandleMessages();
        if (statisticsEnabled) {
            statisticsExecutorService = Executors.newSingleThreadScheduledExecutor(ThingsBoardThreadFactory.forName("remote-integration-manager-service-stats"));
            statisticsExecutorService.scheduleAtFixedRate(this::persistStatistics, statisticsPersistFrequency, statisticsPersistFrequency, TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        log.info("[{}] Starting destroying process", serviceId);
        initialized = false;
        if (uplinkDataConverter != null) {
            uplinkDataConverter.destroy();
        }
        if (downlinkDataConverter != null) {
            downlinkDataConverter.destroy();
        }
        if (integration != null) {
            integration.destroy();
        }
        try {
            rpcClient.disconnect();
        } catch (Exception e) {
            log.error("Exception during disconnect", e);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdownNow();
        }
        if (statisticsEnabled && statisticsExecutorService != null) {
            statisticsExecutorService.shutdownNow();
        }
        if (schedulerService != null) {
            schedulerService.shutdownNow();
        }
        if (generalExecutorService != null) {
            generalExecutorService.shutdownNow();
        }
        if (callBackExecutorService != null) {
            callBackExecutorService.shutdownNow();
        }
        log.info("[{}] Destroy was successful", serviceId);
    }

    private void onConfigurationUpdate(IntegrationConfigurationProto integrationConfigurationProto) {
        if (integration != null) {
            updatingIntegration = true;
            integration.destroy();
        }
        //canceling reconnect scheduler
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
        try {
            Integration configuration = createIntegrationConfiguration(integrationConfigurationProto);
            if (configuration.isEnabled()) {
                integration = IntegrationUtil.createPlatformIntegration(IntegrationType.valueOf(integrationConfigurationProto.getType()), configuration.getConfiguration(), true, coapServerService);
                integration.validateConfiguration(configuration, allowLocalNetworkHosts);

                if (uplinkDataConverter == null || !uplinkDataConverter.getName().equals(integrationConfigurationProto.getUplinkConverter().getName())) {
                    uplinkDataConverter = createUplinkConverter(integrationConfigurationProto.getUplinkConverter());
                }

                if (downlinkDataConverter == null || !downlinkDataConverter.getName().equals(integrationConfigurationProto.getDownlinkConverter().getName())) {
                    downlinkDataConverter = createDownlinkConverter(integrationConfigurationProto.getDownlinkConverter());
                }

                TbIntegrationInitParams params = new TbIntegrationInitParams(
                        new RemoteIntegrationContext(eventStorage, schedulerService, generalExecutorService, callBackExecutorService,
                                configuration, clientId, port),
                        configuration,
                        uplinkDataConverter,
                        downlinkDataConverter);
                integration.init(params);
                if (updatingIntegration) {
                    integrationEvent = ComponentLifecycleEvent.UPDATED;
                    persistLifecycleEvent(ComponentLifecycleEvent.UPDATED, null);
                } else {
                    integrationEvent = ComponentLifecycleEvent.STARTED;
                    persistLifecycleEvent(ComponentLifecycleEvent.STARTED, null);
                }
            } else if (!ComponentLifecycleEvent.STOPPED.equals(integrationEvent)) {
                integrationEvent = ComponentLifecycleEvent.STOPPED;
                persistLifecycleEvent(ComponentLifecycleEvent.STOPPED, null);
                integration = null;
            }
            initialized = true;
        } catch (Exception e) {
            log.error("Failed to initialize platform integration!", e);
            integrationEvent = ComponentLifecycleEvent.FAILED;
            persistLifecycleEvent(ComponentLifecycleEvent.FAILED, e);
        }
    }

    private void onConverterConfigurationUpdate(ConverterConfigurationProto converterProto) {
        ConverterId converterId = new ConverterId(new UUID(converterProto.getConverterIdMSB(), converterProto.getConverterIdLSB()));
        ConverterType converterType;
        TBDataConverter tbDataConverter;
        if (converterId.equals(uplinkConverterId)) {
            converterType = ConverterType.UPLINK;
            tbDataConverter = uplinkDataConverter;
        } else {
            converterType = ConverterType.DOWNLINK;
            tbDataConverter = downlinkDataConverter;
        }
        try {
            tbDataConverter.update(constructConverter(converterProto, converterType));
        } catch (IOException e) {
            log.error("[{}] Failed to update converter configuration", converterId, e);
        }
    }

    private void onDownlink(DeviceDownlinkDataProto deviceDownlinkDataProto) {
        DefaultIntegrationDownlinkMsg downlinkMsg = new DefaultIntegrationDownlinkMsg(
                integration.getConfiguration().getTenantId(),
                integration.getConfiguration().getId(),
                TbMsg.fromBytes(null, deviceDownlinkDataProto.getTbMsg().toByteArray(), TbMsgCallback.EMPTY),
                deviceDownlinkDataProto.getDeviceName());

        integration.onDownlinkMsg(downlinkMsg);
    }

    private TBUplinkDataConverter createUplinkConverter(ConverterConfigurationProto uplinkConverter) throws IOException {
        ScriptUplinkDataConverter uplinkDataConverter = new ScriptUplinkDataConverter(jsInvokeService, mvelInvokeService, logSettingsComponent);
        Converter converter = constructConverter(uplinkConverter, ConverterType.UPLINK);
        uplinkConverterId = converter.getId();
        uplinkDataConverter.init(converter);
        return uplinkDataConverter;
    }

    private TBDownlinkDataConverter createDownlinkConverter(ConverterConfigurationProto downLinkConverter) throws IOException {
        if (!StringUtils.isEmpty(downLinkConverter.getConfiguration())) {
            ScriptDownlinkDataConverter downlinkDataConverter = new ScriptDownlinkDataConverter(jsInvokeService, mvelInvokeService, logSettingsComponent);
            Converter converter = constructConverter(downLinkConverter, ConverterType.DOWNLINK);
            downlinkConverterId = converter.getId();
            downlinkDataConverter.init(converter);
            return downlinkDataConverter;
        }
        return null;
    }

    private Converter constructConverter(ConverterConfigurationProto converterProto, ConverterType converterType) throws IOException {
        Converter converter = new Converter();
        converter.setId(new ConverterId(new UUID(converterProto.getConverterIdMSB(), converterProto.getConverterIdLSB())));
        converter.setTenantId(new TenantId(new UUID(converterProto.getTenantIdMSB(), converterProto.getTenantIdLSB())));
        converter.setName(converterProto.getName());
        converter.setType(converterType);
        converter.setDebugMode(converterProto.getDebugMode());
        converter.setConfiguration(mapper.readTree(converterProto.getConfiguration()));
        converter.setAdditionalInfo(mapper.readTree(converterProto.getAdditionalInfo()));
        return converter;
    }

    private Integration createIntegrationConfiguration(IntegrationConfigurationProto integrationConfigurationProto) throws IOException {
        Integration integration = new Integration();

        IntegrationId integrationId = new IntegrationId(new UUID(integrationConfigurationProto.getIntegrationIdMSB(), integrationConfigurationProto.getIntegrationIdLSB()));
        TenantId tenantId = new TenantId(new UUID(integrationConfigurationProto.getTenantIdMSB(), integrationConfigurationProto.getTenantIdLSB()));

        ConverterId defaultConverterId = new ConverterId(new UUID(integrationConfigurationProto.getUplinkConverter().getConverterIdMSB(), integrationConfigurationProto.getUplinkConverter().getConverterIdLSB()));
        ConverterId downlinkConverterId = new ConverterId(new UUID(integrationConfigurationProto.getDownlinkConverter().getConverterIdMSB(), integrationConfigurationProto.getDownlinkConverter().getConverterIdLSB()));

        integration.setId(integrationId);
        integration.setTenantId(tenantId);
        integration.setDefaultConverterId(defaultConverterId);
        integration.setDownlinkConverterId(downlinkConverterId);
        integration.setName(integrationConfigurationProto.getName());
        integration.setRoutingKey(integrationConfigurationProto.getRoutingKey());
        integration.setType(IntegrationType.valueOf(integrationConfigurationProto.getType()));
        integration.setDebugMode(integrationConfigurationProto.getDebugMode());
        integration.setRemote(true);
        integration.setSecret(routingSecret);
        integration.setConfiguration(mapper.readTree(integrationConfigurationProto.getConfiguration()));
        integration.setAdditionalInfo(mapper.readTree(integrationConfigurationProto.getAdditionalInfo()));

        Descriptors.FieldDescriptor enabledField = integrationConfigurationProto.getDescriptorForType().findFieldByName("enabled");
        if (enabledField == null) {
            integration.setEnabled(true);
        } else {
            integration.setEnabled(integrationConfigurationProto.getEnabled());
        }

        log.info("Received Integration update: {}!", integration);
        return integration;
    }

    private void scheduleReconnect(Exception e) {
        initialized = false;
        if (scheduledFuture == null) {
            scheduledFuture = reconnectScheduler.scheduleAtFixedRate(() -> {
                log.info("Trying to reconnect due to the error: {}!", e.getMessage());
                try {
                    rpcClient.disconnect();
                } catch (Exception ex) {
                    log.error("Exception during disconnect: {}", ex.getMessage());
                }
                rpcClient.connect(routingKey, routingSecret, serviceId, this::onConfigurationUpdate, this::onConverterConfigurationUpdate, this::onDownlink, this::scheduleReconnect);
            }, 0, reconnectTimeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    private void processHandleMessages() {
        executor.submit(() -> {
            boolean interrupted = false;
            while (!interrupted) {
                try {
                    if (initialized) {
                        rpcClient.handleMsgs();
                    } else {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (Exception e) {
                    log.warn("Failed to process messages handling!", e);
                }
                if (!interrupted) {
                    interrupted = Thread.interrupted();
                }
            }
        });
    }

    private void persistStatistics() {
        long ts = System.currentTimeMillis();
        IntegrationStatistics statistics = integration.popStatistics();
        try {
            var statsEvent = StatisticsEvent.builder()
                    .tenantId(integration.getConfiguration().getTenantId())
                    .entityId(integration.getConfiguration().getId().getId())
                    .serviceId(getServiceId())
                    .messagesProcessed(statistics.getMessagesProcessed())
                    .errorsOccurred(statistics.getErrorsOccurred())
                    .build();
            eventStorage.write(UplinkMsg.newBuilder()
                    .addEventsData(TbEventProto.newBuilder()
                            .setSource(TbEventSource.INTEGRATION)
                            .setEvent(ByteString.copyFrom(FSTUtils.encode(statsEvent)))
                            .build())
                    .build(), null);

            PostTelemetryMsg.Builder telemetryBuilder = PostTelemetryMsg.newBuilder();
            List<KeyValueProto> telemetryResult = new ArrayList<>();
            telemetryResult.add(KeyValueProto.newBuilder().setKey("messagesCount").setType(KeyValueType.LONG_V)
                    .setLongV(statistics.getMessagesProcessed()).build());
            telemetryResult.add(KeyValueProto.newBuilder().setKey("errorsCount").setType(KeyValueType.LONG_V)
                    .setLongV(statistics.getErrorsOccurred()).build());
            telemetryResult.add(KeyValueProto.newBuilder().setKey("state").setType(KeyValueType.STRING_V)
                    .setStringV(integrationEvent != null ? integrationEvent.name() : "N/A").build());

            TsKvListProto.Builder tsKvListBuilder = TsKvListProto.newBuilder();
            tsKvListBuilder.setTs(ts);
            tsKvListBuilder.addAllKv(telemetryResult);
            telemetryBuilder.addTsKvList(tsKvListBuilder.build());

            eventStorage.write(UplinkMsg.newBuilder()
                    .addIntegrationStatistics(IntegrationStatisticsProto
                            .newBuilder()
                            .setPostTelemetryMsg(telemetryBuilder.build())
                            .build())
                    .build(), null);
        } catch (Exception e) {
            log.warn("[{}] Failed to persist statistics: {}", integration.getConfiguration().getId(), statistics, e);
        }
    }

    private void persistLifecycleEvent(ComponentLifecycleEvent event, Exception e) {
        var lcEvent = LifecycleEvent.builder()
                .tenantId(integration.getConfiguration().getTenantId())
                .entityId(integration.getConfiguration().getId().getId())
                .serviceId(getServiceId())
                .lcEventType(event.name());
        if (e != null) {
            lcEvent.success(false).error(EventUtil.toString(e));
        } else {
            lcEvent.success(true);
        }

        eventStorage.write(UplinkMsg.newBuilder()
                .addEventsData(TbEventProto.newBuilder()
                        .setSource(TbEventSource.INTEGRATION)
                        .setEvent(ByteString.copyFrom(FSTUtils.encode(lcEvent.build())))
                        .build())
                .build(), null);
    }

}
