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
package org.thingsboard.server.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.common.util.ThingsBoardExecutors;
import org.thingsboard.integration.api.IntegrationCallback;
import org.thingsboard.server.actors.ActorSystemContext;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.ApiUsageRecordKey;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.FSTUtils;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.event.Event;
import org.thingsboard.server.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.server.common.data.group.EntityGroup;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.RuleChainId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.IntegrationInfo;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.objects.TelemetryEntityView;
import org.thingsboard.server.common.data.relation.EntityRelation;
import org.thingsboard.server.common.data.relation.RelationTypeGroup;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.common.msg.queue.ServiceType;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.common.msg.session.SessionMsgType;
import org.thingsboard.server.common.msg.tools.TbRateLimits;
import org.thingsboard.server.common.msg.tools.TbRateLimitsException;
import org.thingsboard.server.common.stats.TbApiUsageReportClient;
import org.thingsboard.server.common.transport.util.JsonUtils;
import org.thingsboard.server.common.util.KvProtoUtil;
import org.thingsboard.server.dao.asset.AssetService;
import org.thingsboard.server.dao.customer.CustomerService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.entityview.EntityViewService;
import org.thingsboard.server.dao.event.EventService;
import org.thingsboard.server.dao.group.EntityGroupService;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.dao.relation.RelationService;
import org.thingsboard.server.exception.ThingsboardRuntimeException;
import org.thingsboard.server.gen.integration.AssetUplinkDataProto;
import org.thingsboard.server.gen.integration.DeviceUplinkDataProto;
import org.thingsboard.server.gen.integration.EntityViewDataProto;
import org.thingsboard.server.gen.integration.TbIntegrationEventProto;
import org.thingsboard.server.gen.integration.TbIntegrationTsDataProto;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.PostAttributeMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SessionInfoProto;
import org.thingsboard.server.queue.TbQueueCallback;
import org.thingsboard.server.queue.TbQueueMsgMetadata;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.discovery.PartitionService;
import org.thingsboard.server.queue.discovery.TbServiceInfoProvider;
import org.thingsboard.server.queue.provider.TbQueueProducerProvider;
import org.thingsboard.server.queue.util.DataDecodingEncodingService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.converter.DataConverterService;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.service.profile.DefaultTbDeviceProfileCache;
import org.thingsboard.server.service.state.DeviceStateService;
import org.thingsboard.server.service.telemetry.TelemetrySubscriptionService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.thingsboard.server.common.msg.session.SessionMsgType.POST_ATTRIBUTES_REQUEST;
import static org.thingsboard.server.common.msg.session.SessionMsgType.POST_TELEMETRY_REQUEST;

/**
 * Created by ashvayka on 02.12.17.
 */
@Slf4j
@TbCoreComponent
@Service
@Data
public class DefaultPlatformIntegrationService implements PlatformIntegrationService {

    private static final ReentrantLock entityCreationLock = new ReentrantLock();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TbClusterService clusterService;

    @Autowired
    private TbServiceInfoProvider serviceInfoProvider;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private DataConverterService dataConverterService;

    @Autowired
    protected IntegrationContextComponent contextComponent;

    @Autowired
    private PartitionService partitionService;

    @Autowired
    private EventService eventService;

    @Autowired
    @Lazy
    private TbQueueProducerProvider producerProvider;

    @Autowired
    @Lazy
    private DataDecodingEncodingService encodingService;

    @Autowired
    @Lazy
    private ActorSystemContext actorContext;

    @Autowired
    private TelemetrySubscriptionService telemetrySubscriptionService;

    @Autowired
    private RemoteIntegrationRpcService remoteIntegrationRpcService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private EntityViewService entityViewService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private RelationService relationService;

    @Autowired
    private DeviceStateService deviceStateService;

    @Autowired
    private EntityGroupService entityGroupService;

    @Autowired
    private DbCallbackExecutorService callbackExecutorService;

    @Autowired
    private TbApiUsageReportClient apiUsageReportClient;

    @Autowired
    private DefaultTbDeviceProfileCache deviceProfileCache;

    @Autowired
    private DeviceProfileService deviceProfileService;

    @Value("${integrations.rate_limits.enabled}")
    private boolean rateLimitEnabled;

    @Value("${integrations.rate_limits.tenant}")
    private String perTenantLimitsConf;

    @Value("${integrations.rate_limits.device}")
    private String perDevicesLimitsConf;

    @Value("${integrations.reinit.enabled:false}")
    private boolean reinitEnabled;

    @Value("${integrations.reinit.frequency:3600000}")
    private long reinitFrequency;

    @Value("${integrations.statistics.enabled}")
    private boolean statisticsEnabled;

    @Value("${integrations.statistics.persist_frequency}")
    private long statisticsPersistFrequency;

    @Value("${integrations.allow_Local_network_hosts:true}")
    private boolean allowLocalNetworkHosts;

    private ExecutorService callbackExecutor;

    private final Gson gson = new Gson();
    private volatile Set<TopicPartitionInfo> myPartitions = ConcurrentHashMap.newKeySet();

    private ConcurrentMap<TenantId, TbRateLimits> perTenantLimits = new ConcurrentHashMap<>();
    private ConcurrentMap<DeviceId, TbRateLimits> perDeviceLimits = new ConcurrentHashMap<>();
    private boolean initialized;

    protected TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToRuleEngineMsg>> ruleEngineMsgProducer;
    protected TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToCoreMsg>> tbCoreMsgProducer;
    protected TbQueueProducer<TbProtoQueueMsg<TransportProtos.ToRuleEngineMsg>> integrationRuleEngineMsgProducer;

    @PostConstruct
    public void init() {
        ruleEngineMsgProducer = producerProvider.getRuleEngineMsgProducer();
        tbCoreMsgProducer = producerProvider.getTbCoreMsgProducer();
        integrationRuleEngineMsgProducer = producerProvider.getIntegrationRuleEngineMsgProducer();
        this.callbackExecutor = ThingsBoardExecutors.newWorkStealingPool(20, "default-integration-callback");
    }

    @PreDestroy
    public void destroy() {
        if (callbackExecutor != null) {
            callbackExecutor.shutdownNow();
        }
    }

    @Override
    public void processUplinkData(IntegrationInfo integration, DeviceUplinkDataProto data, IntegrationCallback<Void> callback) {
        Device device = getOrCreateDevice(integration, data.getDeviceName(), data.getDeviceType(), data.getDeviceLabel(), data.getCustomerName(), data.getGroupName());

        UUID sessionId = integration.getId().getId(); //for local integration context sessionId is exact integrationId
        TransportProtos.SessionInfoProto.Builder builder = TransportProtos.SessionInfoProto.newBuilder()
                .setSessionIdMSB(sessionId.getMostSignificantBits())
                .setSessionIdLSB(sessionId.getLeastSignificantBits())
                .setTenantIdMSB(device.getTenantId().getId().getMostSignificantBits())
                .setTenantIdLSB(device.getTenantId().getId().getLeastSignificantBits())
                .setDeviceIdMSB(device.getId().getId().getMostSignificantBits())
                .setDeviceIdLSB(device.getId().getId().getLeastSignificantBits())
                .setDeviceName(device.getName())
                .setDeviceType(device.getType())
                .setDeviceProfileIdMSB(device.getDeviceProfileId().getId().getMostSignificantBits())
                .setDeviceProfileIdLSB(device.getDeviceProfileId().getId().getLeastSignificantBits());

        if (device.getCustomerId() != null && !device.getCustomerId().isNullUid()) {
            builder.setCustomerIdMSB(device.getCustomerId().getId().getMostSignificantBits());
            builder.setCustomerIdLSB(device.getCustomerId().getId().getLeastSignificantBits());
        }

        TransportProtos.SessionInfoProto sessionInfo = builder.build();

        if (data.hasPostTelemetryMsg()) {
            process(sessionInfo, data.getPostTelemetryMsg(), callback);
        }

        if (data.hasPostAttributesMsg()) {
            process(sessionInfo, data.getPostAttributesMsg(), callback);
        }
    }

    @Override
    public void processUplinkData(IntegrationInfo configuration, AssetUplinkDataProto data, IntegrationCallback<Void> callback) {
        Asset asset = getOrCreateAsset(configuration, data.getAssetName(), data.getAssetType(), data.getAssetLabel(), data.getCustomerName(), data.getGroupName());

        if (data.hasPostTelemetryMsg()) {
            data.getPostTelemetryMsg().getTsKvListList()
                    .forEach(tsKv -> {
                        TbMsgMetaData metaData = new TbMsgMetaData();
                        metaData.putValue("assetName", data.getAssetName());
                        metaData.putValue("assetType", data.getAssetType());
                        metaData.putValue("ts", tsKv.getTs() + "");
                        JsonObject json = JsonUtils.getJsonObject(tsKv.getKvList());
                        TbMsg tbMsg = TbMsg.newMsg(POST_TELEMETRY_REQUEST.name(), asset.getId(), asset.getCustomerId(), metaData, gson.toJson(json));
                        process(asset.getTenantId(), tbMsg, callback);
                    });
        }

        if (data.hasPostAttributesMsg()) {
            TbMsgMetaData metaData = new TbMsgMetaData();
            metaData.putValue("assetName", data.getAssetName());
            metaData.putValue("assetType", data.getAssetType());
            JsonObject json = JsonUtils.getJsonObject(data.getPostAttributesMsg().getKvList());
            TbMsg tbMsg = TbMsg.newMsg(POST_ATTRIBUTES_REQUEST.name(), asset.getId(), asset.getCustomerId(), metaData, gson.toJson(json));
            process(asset.getTenantId(), tbMsg, callback);
        }
    }

    @Override
    public void processUplinkData(IntegrationInfo integrationInfo, EntityViewDataProto data, IntegrationCallback<Void> callback) {
        Device device = getOrCreateDevice(integrationInfo, data.getDeviceName(), data.getDeviceType(), null, null, null);
        getOrCreateEntityView(integrationInfo, device, data);
        callback.onSuccess(null);
    }

    @Override
    public void processUplinkData(IntegrationInfo info, TbMsg data, IntegrationApiCallback callback) {
        process(info.getTenantId(), data, callback);
    }

    @Override
    public void processUplinkData(TbIntegrationEventProto data, IntegrationApiCallback callback) {
        TenantId tenantId = new TenantId(new UUID(data.getTenantIdMSB(), data.getTenantIdLSB()));
        var eventSource = data.getSource();
        EntityId entityid = null;
        switch (eventSource) {
            case DEVICE:
                Device device = deviceService.findDeviceByTenantIdAndName(tenantId, data.getDeviceName());
                if (device != null) {
                    entityid = device.getId();
                }
                break;
            case INTEGRATION:
                entityid = new IntegrationId(new UUID(data.getEventSourceIdMSB(), data.getEventSourceIdLSB()));
                break;
            case UPLINK_CONVERTER:
            case DOWNLINK_CONVERTER:
                entityid = new ConverterId(new UUID(data.getEventSourceIdMSB(), data.getEventSourceIdLSB()));
                break;
        }
        if (entityid != null) {
            saveEvent(tenantId, entityid, data, callback);
        } else {
            callback.onSuccess(null);
        }
    }

    @Override
    public void processUplinkData(TbIntegrationTsDataProto data, IntegrationApiCallback integrationApiCallback) {
        TenantId tenantId = new TenantId(new UUID(data.getTenantIdMSB(), data.getTenantIdLSB()));
        var eventSource = data.getSource();
        EntityId entityid;
        switch (eventSource) {
            case INTEGRATION:
                entityid = new IntegrationId(new UUID(data.getEntityIdMSB(), data.getEntityIdLSB()));
                break;
            case UPLINK_CONVERTER:
            case DOWNLINK_CONVERTER:
                entityid = new ConverterId(new UUID(data.getEntityIdMSB(), data.getEntityIdLSB()));
                break;
            default:
                throw new RuntimeException("Not supported!");
        }

        List<TsKvEntry> statistics = KvProtoUtil.toTsKvEntityList(data.getTsDataList());
        telemetrySubscriptionService.saveAndNotifyInternal(tenantId, entityid, statistics, new FutureCallback<>() {
            @Override
            public void onSuccess(Integer result) {
                log.trace("[{}] Persisted statistics telemetry: {}", entityid, statistics);
            }

            @Override
            public void onFailure(Throwable t) {
                log.warn("[{}] Failed to persist statistics telemetry: {}", entityid, statistics, t);
            }
        });
    }

    private void saveEvent(TenantId tenantId, EntityId entityId, TbIntegrationEventProto proto, IntegrationApiCallback callback) {
        try {
            Event event = FSTUtils.decode(proto.getEvent().toByteArray());
            event.setTenantId(tenantId);
            event.setEntityId(entityId.getId());
            DonAsynchron.withCallback(eventService.saveAsync(event), callback::onSuccess, callback::onError);
        } catch (Exception t) {
            log.error("[{}][{}][{}] Failed to save event!", tenantId, entityId, proto.getEvent(), t);
            callback.onError(t);
            throw t;
        }
    }

    @Override
    public void process(SessionInfoProto sessionInfo, PostTelemetryMsg msg, IntegrationCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivity(sessionInfo);
            TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
            DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
            int dataPoints = 0;
            for (TransportProtos.TsKvListProto tsKv : msg.getTsKvListList()) {
                dataPoints += tsKv.getKvCount();
            }
            MsgPackCallback packCallback = new MsgPackCallback(msg.getTsKvListCount(), new ApiStatsProxyCallback<>(tenantId, getCustomerId(sessionInfo), dataPoints, callback));
            for (TransportProtos.TsKvListProto tsKv : msg.getTsKvListList()) {
                TbMsgMetaData metaData = new TbMsgMetaData();
                metaData.putValue("deviceName", sessionInfo.getDeviceName());
                metaData.putValue("deviceType", sessionInfo.getDeviceType());
                metaData.putValue("ts", tsKv.getTs() + "");
                JsonObject json = JsonUtils.getJsonObject(tsKv.getKvList());
                sendToRuleEngine(tenantId, deviceId, sessionInfo, json, metaData, SessionMsgType.POST_TELEMETRY_REQUEST, packCallback);
            }
        }
    }

    @Override
    public void process(SessionInfoProto sessionInfo, PostAttributeMsg msg, IntegrationCallback<Void> callback) {
        if (checkLimits(sessionInfo, msg, callback)) {
            reportActivity(sessionInfo);
            TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
            DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
            JsonObject json = JsonUtils.getJsonObject(msg.getKvList());
            TbMsgMetaData metaData = new TbMsgMetaData();
            metaData.putValue("deviceName", sessionInfo.getDeviceName());
            metaData.putValue("deviceType", sessionInfo.getDeviceType());
            sendToRuleEngine(tenantId, deviceId, sessionInfo, json, metaData, SessionMsgType.POST_ATTRIBUTES_REQUEST,
                    new IntegrationTbQueueCallback(new ApiStatsProxyCallback<>(tenantId, getCustomerId(sessionInfo), msg.getKvList().size(), callback)));
        }
    }

    @Override
    public void process(TenantId tenantId, TbMsg tbMsg, IntegrationCallback<Void> callback) {
        sendToRuleEngine(tenantId, tbMsg, new IntegrationTbQueueCallback(new ApiStatsProxyCallback<>(tenantId, tbMsg.getCustomerId(), 1, callback)));
    }


    @Override
    public Device getOrCreateDevice(IntegrationInfo integration, String deviceName, String deviceType, String deviceLabel, String customerName, String groupName) {
        Device device = deviceService.findDeviceByTenantIdAndName(integration.getTenantId(), deviceName);
        if (device == null) {
            entityCreationLock.lock();
            try {
                return processGetOrCreateDevice(integration, deviceName, deviceType, deviceLabel, customerName, groupName);
            } finally {
                entityCreationLock.unlock();
            }
        }
        return device;
    }

    @Override
    public Asset getOrCreateAsset(IntegrationInfo integration, String assetName, String assetType, String assetLabel, String customerName, String groupName) {
        Asset asset = assetService.findAssetByTenantIdAndName(integration.getTenantId(), assetName);
        if (asset == null) {
            entityCreationLock.lock();
            try {
                return processGetOrCreateAsset(integration, assetName, assetType, assetLabel, customerName, groupName);
            } finally {
                entityCreationLock.unlock();
            }
        }
        return asset;
    }

    @Override
    public EntityView getOrCreateEntityView(IntegrationInfo configuration, Device device, EntityViewDataProto proto) {
        String entityViewName = proto.getViewName();
        EntityView entityView = entityViewService.findEntityViewByTenantIdAndName(configuration.getTenantId(), entityViewName);
        if (entityView == null) {
            entityCreationLock.lock();
            try {
                entityView = entityViewService.findEntityViewByTenantIdAndName(configuration.getTenantId(), entityViewName);
                if (entityView == null) {
                    entityView = new EntityView();
                    entityView.setName(entityViewName);
                    entityView.setType(proto.getViewType());
                    entityView.setTenantId(configuration.getTenantId());
                    entityView.setEntityId(device.getId());

                    TelemetryEntityView telemetryEntityView = new TelemetryEntityView();
                    telemetryEntityView.setTimeseries(proto.getTelemetryKeysList());
                    entityView.setKeys(telemetryEntityView);

                    entityView = entityViewService.saveEntityView(entityView);
                    createRelationFromIntegration(configuration, entityView.getId());
                }
            } finally {
                entityCreationLock.unlock();
            }
        }
        return entityView;
    }

    private Device processGetOrCreateDevice(IntegrationInfo integration, String deviceName, String deviceType, String deviceLabel, String customerName, String groupName) {
        Device device = deviceService.findDeviceByTenantIdAndName(integration.getTenantId(), deviceName);
        if (device == null && integration.isAllowCreateDevicesOrAssets()) {
            device = new Device();
            device.setName(deviceName);

            // TODO: @voba device profiles are not created on edge at the moment
            String deviceTypeOrDefault = checkDeviceTypeExistsOrDefault(integration.getTenantId(), deviceType);
            device.setType(deviceTypeOrDefault);
            // device.setType(deviceType);

            device.setTenantId(integration.getTenantId());
            if (!StringUtils.isEmpty(deviceLabel)) {
                device.setLabel(deviceLabel);
            }
            if (!StringUtils.isEmpty(customerName)) {
                Customer customer = getOrCreateCustomer(integration, customerName);
                device.setCustomerId(customer.getId());
            }

            device = deviceService.saveDevice(device);

            if (!StringUtils.isEmpty(groupName)) {
                addEntityToEntityGroup(groupName, integration, device.getId(), device.getOwnerId(), device.getEntityType());
            }

            createRelationFromIntegration(integration, device.getId());
            clusterService.onDeviceUpdated(device, null);
            pushDeviceCreatedEventToRuleEngine(integration, device);
        } else {
            throw new ThingsboardRuntimeException("Creating devices is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        return device;
    }

    private String checkDeviceTypeExistsOrDefault(TenantId tenantId, String deviceType) {
        DeviceProfile deviceProfileByName = deviceProfileService.findDeviceProfileByName(tenantId, deviceType);
        if (deviceProfileByName != null) {
            return deviceType;
        }
        return deviceProfileService.findDefaultDeviceProfile(tenantId).getName();
    }

    private Asset processGetOrCreateAsset(IntegrationInfo integration, String assetName, String assetType, String assetLabel, String customerName, String groupName) {
        Asset asset = assetService.findAssetByTenantIdAndName(integration.getTenantId(), assetName);
        if (asset == null && integration.isAllowCreateDevicesOrAssets()) {
            asset = new Asset();
            asset.setName(assetName);
            asset.setType(assetType);
            asset.setTenantId(integration.getTenantId());
            if (!StringUtils.isEmpty(assetLabel)) {
                asset.setLabel(assetLabel);
            }
            if (!StringUtils.isEmpty(customerName)) {
                Customer customer = getOrCreateCustomer(integration, customerName);
                asset.setCustomerId(customer.getId());
            }
            asset = assetService.saveAsset(asset);

            if (!StringUtils.isEmpty(groupName)) {
                addEntityToEntityGroup(groupName, integration, asset.getId(), asset.getOwnerId(), asset.getEntityType());
            }

            createRelationFromIntegration(integration, asset.getId());
            pushAssetCreatedEventToRuleEngine(integration, asset);
        } else {
            throw new ThingsboardRuntimeException("Creating assets is forbidden!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        return asset;
    }

    private Customer getOrCreateCustomer(IntegrationInfo integration, String customerName) {
        Customer customer;
        Optional<Customer> customerOptional = customerService.findCustomerByTenantIdAndTitle(integration.getTenantId(), customerName);
        if (customerOptional.isPresent()) {
            customer = customerOptional.get();
        } else {
            customer = new Customer();
            // TODO: @voba customers are not created on the edge at the moment
            // customer.setTitle(customerName);
            // customer.setTenantId(integration.getTenantId());
            // customer = customerService.saveCustomer(customer);
            // pushCustomerCreatedEventToRuleEngine(integration, customer);
        }
        return customer;
    }

    private void addEntityToEntityGroup(String groupName, IntegrationInfo integration, EntityId entityId, EntityId parentId, EntityType entityType) {
        TenantId tenantId = integration.getTenantId();
        ListenableFuture<Optional<EntityGroup>> futureEntityGroup = entityGroupService
                .findEntityGroupByTypeAndNameAsync(tenantId, parentId, entityType, groupName);

        DonAsynchron.withCallback(futureEntityGroup, optionalEntityGroup -> {
            if  (optionalEntityGroup.isPresent()) {
                EntityGroup entityGroup =
                        optionalEntityGroup.orElseGet(() -> createEntityGroup(groupName, parentId, entityType, tenantId));
                pushEntityGroupCreatedEventToRuleEngine(integration, entityGroup);
                entityGroupService.addEntityToEntityGroup(tenantId, entityGroup.getId(), entityId);
            } else {
                // TODO: @voba entity groups are not created on the edge at the moment
                log.warn("[{}][{}] Entity group [{}] not found! Please create group on the cloud and assign it to the edge first!", tenantId, parentId, groupName);
            }
        }, throwable -> log.warn("[{}][{}] Failed to find entity group: {}:{}", tenantId, parentId, entityType, groupName, throwable), callbackExecutorService);
    }

    private EntityGroup createEntityGroup(String entityGroupName, EntityId parentEntityId, EntityType entityType, TenantId tenantId) {
        EntityGroup entityGroup = new EntityGroup();
        entityGroup.setName(entityGroupName);
        entityGroup.setType(entityType);
        return entityGroupService.saveEntityGroup(tenantId, parentEntityId, entityGroup);
    }

    private void createRelationFromIntegration(IntegrationInfo integration, EntityId entityId) {
        EntityRelation relation = new EntityRelation();
        relation.setFrom(integration.getId());
        relation.setTo(entityId);
        relation.setTypeGroup(RelationTypeGroup.COMMON);
        relation.setType(EntityRelation.INTEGRATION_TYPE);
        relationService.saveRelation(integration.getTenantId(), relation);
    }

    private void pushDeviceCreatedEventToRuleEngine(IntegrationInfo integration, Device device) {
        try {
            DeviceProfile deviceProfile = deviceProfileCache.find(device.getDeviceProfileId());
            RuleChainId ruleChainId;
            String queueName;

            if (deviceProfile == null) {
                ruleChainId = null;
                queueName = null;
            } else {
                ruleChainId = deviceProfile.getDefaultRuleChainId();
                queueName = deviceProfile.getDefaultQueueName();
            }

            ObjectNode entityNode = mapper.valueToTree(device);
            TbMsg tbMsg = TbMsg.newMsg(queueName, DataConstants.ENTITY_CREATED, device.getId(), deviceActionTbMsgMetaData(integration, device),
                    mapper.writeValueAsString(entityNode), ruleChainId, null);

            process(device.getTenantId(), tbMsg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push device action to rule engine: {}", device.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    private void pushAssetCreatedEventToRuleEngine(IntegrationInfo integration, Asset asset) {
        try {
            ObjectNode entityNode = mapper.valueToTree(asset);
            TbMsg tbMsg = TbMsg.newMsg(DataConstants.ENTITY_CREATED, asset.getId(), asset.getCustomerId(), assetActionTbMsgMetaData(integration, asset), mapper.writeValueAsString(entityNode));
            process(integration.getTenantId(), tbMsg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push asset action to rule engine: {}", asset.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }


    private void pushEntityGroupCreatedEventToRuleEngine(IntegrationInfo integration, EntityGroup entityGroup) {
        try {
            ObjectNode entityNode = mapper.valueToTree(entityGroup);
            TbMsg tbMsg = TbMsg.newMsg(DataConstants.ENTITY_CREATED, entityGroup.getId(), getTbMsgMetaData(integration), mapper.writeValueAsString(entityNode));
            process(integration.getTenantId(), tbMsg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push entityGroup action to rule engine: {}", entityGroup.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    private void pushCustomerCreatedEventToRuleEngine(IntegrationInfo integration, Customer customer) {
        try {
            ObjectNode entityNode = mapper.valueToTree(customer);
            TbMsg tbMsg = TbMsg.newMsg(DataConstants.ENTITY_CREATED, customer.getId(), customer.getParentCustomerId(), getTbMsgMetaData(integration), mapper.writeValueAsString(entityNode));
            process(customer.getTenantId(), tbMsg, null);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("[{}] Failed to push customer action to rule engine: {}", customer.getId(), DataConstants.ENTITY_CREATED, e);
        }
    }

    private TbMsgMetaData deviceActionTbMsgMetaData(IntegrationInfo integration, Device device) {
        return getActionTbMsgMetaData(integration, device.getCustomerId());
    }

    private TbMsgMetaData assetActionTbMsgMetaData(IntegrationInfo integration, Asset asset) {
        return getActionTbMsgMetaData(integration, asset.getCustomerId());
    }

    private TbMsgMetaData getActionTbMsgMetaData(IntegrationInfo integration, CustomerId customerId) {
        TbMsgMetaData metaData = getTbMsgMetaData(integration);
        if (customerId != null && !customerId.isNullUid()) {
            metaData.putValue("customerId", customerId.toString());
        }
        return metaData;
    }

    private TbMsgMetaData getTbMsgMetaData(IntegrationInfo integration) {
        TbMsgMetaData metaData = new TbMsgMetaData();
        metaData.putValue("integrationId", integration.getId().toString());
        metaData.putValue("integrationName", integration.getName());
        return metaData;
    }

    private void reportActivity(SessionInfoProto sessionInfo) {
        TransportProtos.SubscriptionInfoProto subscriptionInfoProto = TransportProtos.SubscriptionInfoProto.newBuilder()
                .setAttributeSubscription(false).setRpcSubscription(false)
                .setLastActivityTime(System.currentTimeMillis()).build();
        TransportProtos.TransportToDeviceActorMsg msg = TransportProtos.TransportToDeviceActorMsg.newBuilder().setSessionInfo(sessionInfo)
                .setSubscriptionInfo(subscriptionInfoProto).build();
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_CORE, getTenantId(sessionInfo), getDeviceId(sessionInfo));
        tbCoreMsgProducer.send(tpi, new TbProtoQueueMsg<>(getRoutingKey(sessionInfo),
                TransportProtos.ToCoreMsg.newBuilder().setToDeviceActorMsg(msg).build()), null);
    }

    private void sendToRuleEngine(TenantId tenantId, DeviceId deviceId, TransportProtos.SessionInfoProto sessionInfo, JsonObject json,
                                  TbMsgMetaData metaData, SessionMsgType sessionMsgType, TbQueueCallback callback) {
        DeviceProfileId deviceProfileId = new DeviceProfileId(new UUID(sessionInfo.getDeviceProfileIdMSB(), sessionInfo.getDeviceProfileIdLSB()));

        DeviceProfile deviceProfile = deviceProfileCache.get(tenantId, deviceProfileId);
        RuleChainId ruleChainId;
        String queueName;

        if (deviceProfile == null) {
            log.warn("[{}] Device profile is null!", deviceProfileId);
            ruleChainId = null;
            queueName = null;
        } else {
            ruleChainId = deviceProfile.getDefaultRuleChainId();
            queueName = deviceProfile.getDefaultQueueName();
        }

        TbMsg tbMsg = TbMsg.newMsg(queueName, sessionMsgType.name(), deviceId, getCustomerId(sessionInfo), metaData, gson.toJson(json), ruleChainId, null);

        sendToRuleEngine(tenantId, tbMsg, callback);
    }

    private void sendToRuleEngine(TenantId tenantId, TbMsg tbMsg, TbQueueCallback callback) {
        TopicPartitionInfo tpi = partitionService.resolve(ServiceType.TB_RULE_ENGINE, tbMsg.getQueueName(), tenantId, tbMsg.getOriginator());
        TransportProtos.ToRuleEngineMsg msg = TransportProtos.ToRuleEngineMsg.newBuilder().setTbMsg(TbMsg.toByteString(tbMsg))
                .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                .setTenantIdLSB(tenantId.getId().getLeastSignificantBits()).build();
        integrationRuleEngineMsgProducer.send(tpi, new TbProtoQueueMsg<>(tbMsg.getId(), msg), callback);
    }

    protected UUID getRoutingKey(TransportProtos.SessionInfoProto sessionInfo) {
        return new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB());
    }

    protected TenantId getTenantId(TransportProtos.SessionInfoProto sessionInfo) {
        return new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
    }

    protected DeviceId getDeviceId(TransportProtos.SessionInfoProto sessionInfo) {
        return new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
    }

    private boolean checkLimits(SessionInfoProto sessionInfo, Object msg, IntegrationCallback<Void> callback) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Processing msg: {}", toId(sessionInfo), msg);
        }
        if (!rateLimitEnabled) {
            return true;
        }
        TenantId tenantId = new TenantId(new UUID(sessionInfo.getTenantIdMSB(), sessionInfo.getTenantIdLSB()));
        TbRateLimits rateLimits = perTenantLimits.computeIfAbsent(tenantId, id -> new TbRateLimits(perTenantLimitsConf));
        if (!rateLimits.tryConsume()) {
            if (callback != null) {
                callback.onError(new TbRateLimitsException(EntityType.TENANT));
            }
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Tenant level rate limit detected: {}", toId(sessionInfo), tenantId, msg);
            }
            return false;
        }
        DeviceId deviceId = new DeviceId(new UUID(sessionInfo.getDeviceIdMSB(), sessionInfo.getDeviceIdLSB()));
        rateLimits = perDeviceLimits.computeIfAbsent(deviceId, id -> new TbRateLimits(perDevicesLimitsConf));
        if (!rateLimits.tryConsume()) {
            if (callback != null) {
                callback.onError(new TbRateLimitsException(EntityType.DEVICE));
            }
            if (log.isTraceEnabled()) {
                log.trace("[{}][{}] Device level rate limit detected: {}", toId(sessionInfo), deviceId, msg);
            }
            return false;
        }
        return true;
    }

    private UUID toId(SessionInfoProto sessionInfo) {
        return new UUID(sessionInfo.getSessionIdMSB(), sessionInfo.getSessionIdLSB());
    }

    private class IntegrationTbQueueCallback implements TbQueueCallback {
        private final IntegrationCallback<Void> callback;

        private IntegrationTbQueueCallback(IntegrationCallback<Void> callback) {
            this.callback = callback;
        }

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            DefaultPlatformIntegrationService.this.callbackExecutor.submit(() -> {
                if (callback != null) {
                    callback.onSuccess(null);
                }
            });
        }

        @Override
        public void onFailure(Throwable t) {
            DefaultPlatformIntegrationService.this.callbackExecutor.submit(() -> {
                if (callback != null) {
                    callback.onError(t);
                }
            });
        }
    }

    private class MsgPackCallback implements TbQueueCallback {
        private final AtomicInteger msgCount;
        private final IntegrationCallback<Void> callback;

        public MsgPackCallback(Integer msgCount, IntegrationCallback<Void> callback) {
            this.msgCount = new AtomicInteger(msgCount);
            this.callback = callback;
        }

        @Override
        public void onSuccess(TbQueueMsgMetadata metadata) {
            if (msgCount.decrementAndGet() <= 0 && callback != null) {
                DefaultPlatformIntegrationService.this.callbackExecutor.submit(() -> callback.onSuccess(null));
            }
        }

        @Override
        public void onFailure(Throwable t) {
            if (callback != null) {
                callback.onError(t);
            }
        }
    }

    private class ApiStatsProxyCallback<T> implements IntegrationCallback<T> {
        private final TenantId tenantId;
        private final CustomerId customerId;
        private final int dataPoints;
        private final IntegrationCallback<T> callback;

        public ApiStatsProxyCallback(TenantId tenantId, CustomerId customerId, int dataPoints, IntegrationCallback<T> callback) {
            this.tenantId = tenantId;
            this.customerId = customerId;
            this.dataPoints = dataPoints;
            this.callback = callback;
        }

        @Override
        public void onSuccess(T msg) {
            try {
                apiUsageReportClient.report(tenantId, customerId, ApiUsageRecordKey.TRANSPORT_MSG_COUNT, 1);
                apiUsageReportClient.report(tenantId, customerId, ApiUsageRecordKey.TRANSPORT_DP_COUNT, dataPoints);
            } finally {
                if (callback != null) {
                    callback.onSuccess(msg);
                }
            }
        }

        @Override
        public void onError(Throwable e) {
            if (callback != null) {
                callback.onError(e);
            }
        }
    }


    private static CustomerId getCustomerId(SessionInfoProto sessionInfo) {
        CustomerId customerId;
        if (sessionInfo.getCustomerIdMSB() > 0 && sessionInfo.getCustomerIdLSB() > 0) {
            customerId = new CustomerId(new UUID(sessionInfo.getCustomerIdMSB(), sessionInfo.getCustomerIdLSB()));
        } else {
            customerId = null;
        }
        return customerId;
    }
}
