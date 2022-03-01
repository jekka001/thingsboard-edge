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
package org.thingsboard.server.service.ota;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.DonAsynchron;
import org.thingsboard.rule.engine.api.RuleEngineTelemetryService;
import org.thingsboard.rule.engine.api.msg.DeviceAttributesEventNotificationMsg;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.id.OtaPackageId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.kv.AttributeKey;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.ota.DeviceGroupOtaPackage;
import org.thingsboard.server.common.data.ota.OtaPackageType;
import org.thingsboard.server.common.data.ota.OtaPackageUpdateStatus;
import org.thingsboard.server.common.data.ota.OtaPackageUtil;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.msg.queue.TopicPartitionInfo;
import org.thingsboard.server.dao.attributes.AttributesService;
import org.thingsboard.server.dao.device.DeviceService;
import org.thingsboard.server.dao.ota.OtaPackageService;
import org.thingsboard.server.gen.transport.TransportProtos.ToOtaPackageStateServiceMsg;
import org.thingsboard.server.queue.TbQueueProducer;
import org.thingsboard.server.queue.common.TbProtoQueueMsg;
import org.thingsboard.server.queue.provider.TbCoreQueueFactory;
import org.thingsboard.server.queue.provider.TbRuleEngineQueueFactory;
import org.thingsboard.server.service.executors.DbCallbackExecutorService;
import org.thingsboard.server.cluster.TbClusterService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.thingsboard.server.common.data.ota.OtaPackageKey.CHECKSUM;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.CHECKSUM_ALGORITHM;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.ID;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.SIZE;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.STATE;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.TAG;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.TITLE;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.TS;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.URL;
import static org.thingsboard.server.common.data.ota.OtaPackageKey.VERSION;
import static org.thingsboard.server.common.data.ota.OtaPackageType.FIRMWARE;
import static org.thingsboard.server.common.data.ota.OtaPackageType.SOFTWARE;
import static org.thingsboard.server.common.data.ota.OtaPackageUtil.getAttributeKey;
import static org.thingsboard.server.common.data.ota.OtaPackageUtil.getTargetTelemetryKey;
import static org.thingsboard.server.common.data.ota.OtaPackageUtil.getTelemetryKey;

@Slf4j
@Service
public class DefaultOtaPackageStateService implements OtaPackageStateService {

    private final TbClusterService tbClusterService;
    private final OtaPackageService otaPackageService;
    private final DeviceService deviceService;
    private final RuleEngineTelemetryService telemetryService;
    private final AttributesService attributesService;
    private final DbCallbackExecutorService dbExecutor;
    private final TbQueueProducer<TbProtoQueueMsg<ToOtaPackageStateServiceMsg>> otaPackageStateMsgProducer;

    public DefaultOtaPackageStateService(@Lazy TbClusterService tbClusterService,
                                         OtaPackageService otaPackageService,
                                         DeviceService deviceService,
                                         @Lazy RuleEngineTelemetryService telemetryService,
                                         AttributesService attributesService,
                                         DbCallbackExecutorService dbExecutor,
                                         Optional<TbCoreQueueFactory> coreQueueFactory,
                                         Optional<TbRuleEngineQueueFactory> reQueueFactory) {
        this.tbClusterService = tbClusterService;
        this.otaPackageService = otaPackageService;
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.attributesService = attributesService;
        this.dbExecutor = dbExecutor;
        if (coreQueueFactory.isPresent()) {
            this.otaPackageStateMsgProducer = coreQueueFactory.get().createToOtaPackageStateServiceMsgProducer();
        } else {
            this.otaPackageStateMsgProducer = reQueueFactory.get().createToOtaPackageStateServiceMsgProducer();
        }
    }

    @Override
    public void update(TenantId tenantId, DeviceGroupOtaPackage newDeviceGroupOtaPackage, DeviceGroupOtaPackage oldDeviceGroupOtaPackage) {
        long ts = System.currentTimeMillis();

        if (oldDeviceGroupOtaPackage == null) {
            OtaPackageInfo newOtaPackage = otaPackageService.findOtaPackageById(tenantId, newDeviceGroupOtaPackage.getOtaPackageId());
            update(newDeviceGroupOtaPackage, newOtaPackage, ts);
        } else if (newDeviceGroupOtaPackage == null) {
            OtaPackageInfo oldOtaPackage = otaPackageService.findOtaPackageById(tenantId, oldDeviceGroupOtaPackage.getOtaPackageId());
            remove(oldDeviceGroupOtaPackage, oldOtaPackage, ts);
        } else {
            OtaPackageInfo newOtaPackage = otaPackageService.findOtaPackageById(tenantId, newDeviceGroupOtaPackage.getOtaPackageId());
            OtaPackageInfo oldOtaPackage = otaPackageService.findOtaPackageById(tenantId, oldDeviceGroupOtaPackage.getOtaPackageId());
            update(newDeviceGroupOtaPackage, newOtaPackage, ts);
            if (!newOtaPackage.getDeviceProfileId().equals(oldOtaPackage.getDeviceProfileId())) {
                remove(oldDeviceGroupOtaPackage, oldOtaPackage, ts);
            }
        }
    }

    private void update(DeviceGroupOtaPackage deviceGroupOtaPackage, OtaPackageInfo packageFromGroup, long ts) {
        PageLink pageLink = createPageLink();
        PageData<Device> pageData;
        do {
            pageData = deviceService.findByEntityGroupAndDeviceProfileAndEmptyOtaPackage(deviceGroupOtaPackage.getGroupId(),
                    packageFromGroup.getDeviceProfileId(), deviceGroupOtaPackage.getOtaPackageType(), pageLink);
            pageData.getData().forEach(d ->
                    send(d.getTenantId(), d.getId(), deviceGroupOtaPackage.getOtaPackageId(), ts, deviceGroupOtaPackage.getOtaPackageType()));

            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
    }

    private void remove(DeviceGroupOtaPackage deviceGroupOtaPackage, OtaPackageInfo otaPackageFromGroup, long ts) {
        OtaPackageType otaPackageType = deviceGroupOtaPackage.getOtaPackageType();
        PageLink pageLink = createPageLink();
        PageData<Device> pageData;
        do {
            pageData = deviceService.findByEntityGroupAndDeviceProfileAndEmptyOtaPackage(deviceGroupOtaPackage.getGroupId(),
                    otaPackageFromGroup.getDeviceProfileId(), otaPackageType, pageLink);
            pageData.getData().forEach(device -> {
                OtaPackageInfo otaPackageForDevice = otaPackageService.findOtaPackageInfoByDeviceIdAndType(device.getId(), deviceGroupOtaPackage.getOtaPackageType());
                if (otaPackageForDevice != null) {
                    ListenableFuture<Optional<AttributeKvEntry>> oldFirmwareIdFuture =
                            attributesService.find(device.getTenantId(), device.getId(), DataConstants.SERVER_SCOPE, getAttributeKey(otaPackageType, ID));
                    DonAsynchron.withCallback(oldFirmwareIdFuture, oldIdOpt -> {
                        if (oldIdOpt.isPresent()) {
                            OtaPackageId oldFirmwareId = new OtaPackageId(UUID.fromString(oldIdOpt.get().getValueAsString()));
                            if (!otaPackageForDevice.getId().equals(oldFirmwareId)) {
                                send(device.getTenantId(), device.getId(), otaPackageForDevice.getId(), ts, otaPackageType);
                            }
                        } else {
                            log.trace("[{}] OtaPackage id attribute not found!", device.getId());
                        }
                    }, (e) -> log.error("Failed to get OtaPackage id attribute for device!", e), dbExecutor);
                } else {
                    remove(device, otaPackageType);
                }
            });

            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
    }

    @Override
    public void update(TenantId tenantId, List<DeviceId> deviceIds, boolean isFirmware, boolean isSoftware) {
        deviceIds.forEach(id -> {
            if (isFirmware) {
                update(tenantId, id, FIRMWARE);
            }
            if (isSoftware) {
                update(tenantId, id, SOFTWARE);
            }
        });
    }

    private void update(TenantId tenantId, DeviceId deviceId, OtaPackageType otaPackageType) {
        OtaPackageInfo otaPackage = otaPackageService.findOtaPackageInfoByDeviceIdAndType(deviceId, otaPackageType);
        if (otaPackage != null) {
            ListenableFuture<Optional<AttributeKvEntry>> oldFirmwareIdFuture =
                    attributesService.find(tenantId, deviceId, DataConstants.SERVER_SCOPE, getAttributeKey(otaPackageType, ID));
            DonAsynchron.withCallback(oldFirmwareIdFuture, oldIdOpt -> {
                if (oldIdOpt.isPresent()) {
                    OtaPackageId otaPackageId = new OtaPackageId(UUID.fromString(oldIdOpt.get().getValueAsString()));
                    if (!otaPackage.getId().equals(otaPackageId)) {
                        send(tenantId, deviceId, otaPackage.getId(), System.currentTimeMillis(), otaPackageType);
                    }
                } else {
                    send(tenantId, deviceId, otaPackage.getId(), System.currentTimeMillis(), otaPackageType);
                }
            }, (e) -> log.error("Failed to get OtaPackage id attribute for device!", e), dbExecutor);
        } else {
            Device device = deviceService.findDeviceById(tenantId, deviceId);
            remove(device, otaPackageType);
        }
    }

    @Override
    public void update(Device device) {
        updateFirmware(device);
        updateSoftware(device);
    }

    private void updateFirmware(Device device) {
        ListenableFuture<Optional<AttributeKvEntry>> oldFirmwareIdFuture = attributesService.find(device.getTenantId(), device.getId(), DataConstants.SERVER_SCOPE, getAttributeKey(FIRMWARE, ID));
        DonAsynchron.withCallback(oldFirmwareIdFuture, oldIdOpt -> {

            OtaPackageId oldFirmwareId = null;

            if (oldIdOpt.isPresent()) {
                oldFirmwareId = new OtaPackageId(UUID.fromString(oldIdOpt.get().getValueAsString()));
            }

            OtaPackageInfo fw = otaPackageService.findOtaPackageInfoByDeviceIdAndType(device.getId(), FIRMWARE);

            if (fw == null) {
                if (oldFirmwareId != null) {
                    remove(device, FIRMWARE);
                }
            } else if (!fw.getId().equals(oldFirmwareId)) {
                send(device.getTenantId(), device.getId(), fw.getId(), System.currentTimeMillis(), FIRMWARE);
            }

        }, (e) -> log.error("Failed to get firmware id attribute!", e), dbExecutor);
    }

    private void updateSoftware(Device device) {
        ListenableFuture<Optional<AttributeKvEntry>> oldSoftwareIdFuture = attributesService.find(device.getTenantId(), device.getId(), DataConstants.SERVER_SCOPE, getAttributeKey(SOFTWARE, ID));
        DonAsynchron.withCallback(oldSoftwareIdFuture, oldIdOpt -> {

            OtaPackageId oldSoftwareId = null;

            if (oldIdOpt.isPresent()) {
                oldSoftwareId = new OtaPackageId(UUID.fromString(oldIdOpt.get().getValueAsString()));
            }

            OtaPackageInfo sw = otaPackageService.findOtaPackageInfoByDeviceIdAndType(device.getId(), SOFTWARE);

            if (sw == null) {
                if (oldSoftwareId != null) {
                    remove(device, SOFTWARE);
                }
            } else if (!sw.getId().equals(oldSoftwareId)) {
                send(device.getTenantId(), device.getId(), sw.getId(), System.currentTimeMillis(), SOFTWARE);
            }

        }, (e) -> log.error("Failed to get software id attribute!", e), dbExecutor);
    }

    @Override
    public void update(DeviceProfile deviceProfile, boolean isFirmwareChanged, boolean isSoftwareChanged) {
        TenantId tenantId = deviceProfile.getTenantId();

        if (isFirmwareChanged) {
            update(tenantId, deviceProfile, FIRMWARE);
        }
        if (isSoftwareChanged) {
            update(tenantId, deviceProfile, SOFTWARE);
        }
    }

    private void update(TenantId tenantId, DeviceProfile deviceProfile, OtaPackageType otaPackageType) {
        Consumer<Device> updateConsumer;

        OtaPackageId firmwareId = OtaPackageUtil.getOtaPackageId(deviceProfile, otaPackageType);

        if (firmwareId != null) {
            long ts = System.currentTimeMillis();
            updateConsumer = d -> send(d.getTenantId(), d.getId(), firmwareId, ts, otaPackageType);
        } else {
            updateConsumer = d -> remove(d, otaPackageType);
        }

        PageLink pageLink = createPageLink();
        PageData<Device> pageData;
        do {
            pageData = deviceService.findByDeviceProfileAndEmptyOtaPackage(tenantId, deviceProfile.getId(), otaPackageType, pageLink);
            pageData.getData().forEach(updateConsumer);

            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
    }

    @Override
    public boolean process(ToOtaPackageStateServiceMsg msg) {
        boolean isSuccess = false;
        OtaPackageId targetOtaPackageId = new OtaPackageId(new UUID(msg.getOtaPackageIdMSB(), msg.getOtaPackageIdLSB()));
        DeviceId deviceId = new DeviceId(new UUID(msg.getDeviceIdMSB(), msg.getDeviceIdLSB()));
        TenantId tenantId = TenantId.fromUUID(new UUID(msg.getTenantIdMSB(), msg.getTenantIdLSB()));
        OtaPackageType firmwareType = OtaPackageType.valueOf(msg.getType());
        long ts = msg.getTs();

        Device device = deviceService.findDeviceById(tenantId, deviceId);
        if (device == null) {
            log.warn("[{}] [{}] Device was removed during OtaPackage update msg was queued!", tenantId, deviceId);
        } else {
            OtaPackageInfo currentOtaPackage = otaPackageService.findOtaPackageInfoByDeviceIdAndType(deviceId, firmwareType);

            if (currentOtaPackage != null && targetOtaPackageId.equals(currentOtaPackage.getId())) {
                update(device, currentOtaPackage, ts);
                isSuccess = true;
            } else {
                log.warn("[{}] [{}] Can`t update OtaPackage for the device, target firmwareId: [{}], current firmware: [{}]!", tenantId, deviceId, targetOtaPackageId, currentOtaPackage);
            }
        }
        return isSuccess;
    }

    private void send(TenantId tenantId, DeviceId deviceId, OtaPackageId otaPackageId, long ts, OtaPackageType otaPackageType) {
        dbExecutor.execute(() -> {
            ToOtaPackageStateServiceMsg msg = ToOtaPackageStateServiceMsg.newBuilder()
                    .setTenantIdMSB(tenantId.getId().getMostSignificantBits())
                    .setTenantIdLSB(tenantId.getId().getLeastSignificantBits())
                    .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                    .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                    .setOtaPackageIdMSB(otaPackageId.getId().getMostSignificantBits())
                    .setOtaPackageIdLSB(otaPackageId.getId().getLeastSignificantBits())
                    .setType(otaPackageType.name())
                    .setTs(ts)
                    .build();

            OtaPackageInfo firmware = otaPackageService.findOtaPackageInfoById(tenantId, otaPackageId);
            if (firmware == null) {
                log.warn("[{}] Failed to send OtaPackage update because firmware was already deleted", otaPackageId);
                return;
            }

            CountDownLatch latch = new CountDownLatch(1);

            List<AttributeKvEntry> attributes = new ArrayList<>();
            attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(firmware.getType(), ID), firmware.getId().toString())));

            telemetryService.saveAndNotify(tenantId, deviceId, DataConstants.SERVER_SCOPE, attributes, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Void tmp) {
                    log.trace("[{}] Success save attributes with target OtaPackage!", deviceId);
                    latch.countDown();
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("[{}] Failed to save attributes with target OtaPackage!", deviceId, t);
                    latch.countDown();
                }
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                log.error("Failed to await saving {} id to attributes.", otaPackageType);
                return;
            }

            List<TsKvEntry> telemetry = new ArrayList<>();
            telemetry.add(new BasicTsKvEntry(ts, new StringDataEntry(getTargetTelemetryKey(firmware.getType(), TITLE), firmware.getTitle())));
            telemetry.add(new BasicTsKvEntry(ts, new StringDataEntry(getTargetTelemetryKey(firmware.getType(), VERSION), firmware.getVersion())));

            if (StringUtils.isNotEmpty(firmware.getTag())) {
                telemetry.add(new BasicTsKvEntry(ts, new StringDataEntry(getTargetTelemetryKey(firmware.getType(), TAG), firmware.getTag())));
            }

            telemetry.add(new BasicTsKvEntry(ts, new LongDataEntry(getTargetTelemetryKey(firmware.getType(), TS), ts)));
            telemetry.add(new BasicTsKvEntry(ts, new StringDataEntry(getTelemetryKey(firmware.getType(), STATE), OtaPackageUpdateStatus.QUEUED.name())));

            telemetryService.saveAndNotify(tenantId, deviceId, telemetry, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Void tmp) {
                    log.trace("[{}] Success save OtaPackage status!", deviceId);
                }

                @Override
                public void onFailure(Throwable t) {
                    log.error("[{}] Failed to save OtaPackage status!", deviceId, t);
                }
            });

            TopicPartitionInfo tpi = new TopicPartitionInfo(otaPackageStateMsgProducer.getDefaultTopic(), null, null, false);
            otaPackageStateMsgProducer.send(tpi, new TbProtoQueueMsg<>(UUID.randomUUID(), msg), null);
        });
    }

    private void update(Device device, OtaPackageInfo otaPackage, long ts) {
        TenantId tenantId = device.getTenantId();
        DeviceId deviceId = device.getId();
        OtaPackageType otaPackageType = otaPackage.getType();

        BasicTsKvEntry status = new BasicTsKvEntry(System.currentTimeMillis(), new StringDataEntry(getTelemetryKey(otaPackageType, STATE), OtaPackageUpdateStatus.INITIATED.name()));

        telemetryService.saveAndNotify(tenantId, deviceId, Collections.singletonList(status), new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Void tmp) {
                log.trace("[{}] Success save telemetry with target {} for device!", deviceId, otaPackage);
                updateAttributes(device, otaPackage, ts, tenantId, deviceId, otaPackageType);
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("[{}] Failed to save telemetry with target {} for device!", deviceId, otaPackage, t);
                updateAttributes(device, otaPackage, ts, tenantId, deviceId, otaPackageType);
            }
        });
    }

    private void updateAttributes(Device device, OtaPackageInfo otaPackage, long ts, TenantId tenantId, DeviceId deviceId, OtaPackageType otaPackageType) {
        List<AttributeKvEntry> attributes = new ArrayList<>();
        List<String> attrToRemove = new ArrayList<>();
        attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, TITLE), otaPackage.getTitle())));
        attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, VERSION), otaPackage.getVersion())));
        if (StringUtils.isNotEmpty(otaPackage.getTag())) {
            attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, TAG), otaPackage.getTag())));
        } else {
            attrToRemove.add(getAttributeKey(otaPackageType, TAG));
        }
        if (otaPackage.hasUrl()) {
            attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, URL), otaPackage.getUrl())));

            if (otaPackage.getDataSize() == null) {
                attrToRemove.add(getAttributeKey(otaPackageType, SIZE));
            } else {
                attributes.add(new BaseAttributeKvEntry(ts, new LongDataEntry(getAttributeKey(otaPackageType, SIZE), otaPackage.getDataSize())));
            }

            if (otaPackage.getChecksumAlgorithm() != null) {
                attrToRemove.add(getAttributeKey(otaPackageType, CHECKSUM_ALGORITHM));
            } else {
                attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, CHECKSUM_ALGORITHM), otaPackage.getChecksumAlgorithm().name())));
            }

            if (StringUtils.isEmpty(otaPackage.getChecksum())) {
                attrToRemove.add(getAttributeKey(otaPackageType, CHECKSUM));
            } else {
                attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, CHECKSUM), otaPackage.getChecksum())));
            }
        } else {
            attributes.add(new BaseAttributeKvEntry(ts, new LongDataEntry(getAttributeKey(otaPackageType, SIZE), otaPackage.getDataSize())));
            attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, CHECKSUM_ALGORITHM), otaPackage.getChecksumAlgorithm().name())));
            attributes.add(new BaseAttributeKvEntry(ts, new StringDataEntry(getAttributeKey(otaPackageType, CHECKSUM), otaPackage.getChecksum())));
            attrToRemove.add(getAttributeKey(otaPackageType, URL));
        }

        remove(device, otaPackageType, attrToRemove);

        telemetryService.saveAndNotify(tenantId, deviceId, DataConstants.SHARED_SCOPE, attributes, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Void tmp) {
                log.trace("[{}] Success save attributes with target OtaPackage!", deviceId);
            }

            @Override
            public void onFailure(Throwable t) {
                log.error("[{}] Failed to save attributes with target OtaPackage!", deviceId, t);
            }
        });
    }

    private void remove(Device device, OtaPackageType otaPackageType) {
        remove(device, otaPackageType, OtaPackageUtil.getAttributeKeys(otaPackageType));
        String idKey = OtaPackageUtil.getAttributeKey(otaPackageType, ID);

        telemetryService.deleteAndNotify(device.getTenantId(), device.getId(), DataConstants.SERVER_SCOPE, Collections.singletonList(idKey),
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@Nullable Void tmp) {
                        log.trace("[{}] Success remove OtaPackage id attribute!", device.getId());
                        Set<AttributeKey> keysToNotify = new HashSet<>();
                        keysToNotify.add(new AttributeKey(DataConstants.SERVER_SCOPE, idKey));
                        tbClusterService.pushMsgToCore(DeviceAttributesEventNotificationMsg.onDelete(device.getTenantId(), device.getId(), keysToNotify), null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("[{}] Failed to remove target {} attributes!", device.getId(), otaPackageType, t);
                    }
                });
    }

    private void remove(Device device, OtaPackageType otaPackageType, List<String> attributesKeys) {
        telemetryService.deleteAndNotify(device.getTenantId(), device.getId(), DataConstants.SHARED_SCOPE, attributesKeys,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(@Nullable Void tmp) {
                        log.trace("[{}] Success remove target {} attributes!", device.getId(), otaPackageType);
                        Set<AttributeKey> keysToNotify = new HashSet<>();
                        attributesKeys.forEach(key -> keysToNotify.add(new AttributeKey(DataConstants.SHARED_SCOPE, key)));
                        tbClusterService.pushMsgToCore(DeviceAttributesEventNotificationMsg.onDelete(device.getTenantId(), device.getId(), keysToNotify), null);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        log.error("[{}] Failed to remove target {} attributes!", device.getId(), otaPackageType, t);
                    }
                });
    }

    private PageLink createPageLink() {
        return new PageLink(100);
    }
}
