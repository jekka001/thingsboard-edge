/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import com.google.common.util.concurrent.ListenableFuture;
import org.springframework.context.ApplicationListener;
import org.thingsboard.integration.api.IntegrationCallback;
import org.thingsboard.integration.api.ThingsboardPlatformIntegration;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.asset.Asset;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.queue.TbCallback;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.PostAttributeMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.TransportProtos.SessionInfoProto;
import org.thingsboard.server.queue.discovery.PartitionChangeEvent;

/**
 * Created by ashvayka on 02.12.17.
 */
public interface PlatformIntegrationService {

    void validateIntegrationConfiguration(Integration integration);

    void checkIntegrationConnection(Integration integration) throws Exception;

    ListenableFuture<ThingsboardPlatformIntegration> createIntegration(Integration integration);

    ListenableFuture<ThingsboardPlatformIntegration> updateIntegration(Integration integration);

    ListenableFuture<Void> deleteIntegration(IntegrationId integration);

    ListenableFuture<ThingsboardPlatformIntegration> getIntegrationByRoutingKey(String key);

    void onQueueMsg(TransportProtos.IntegrationDownlinkMsgProto msg, TbCallback callback);

    void process(SessionInfoProto sessionInfo, PostTelemetryMsg msg, IntegrationCallback<Void> callback);

    void process(SessionInfoProto sessionInfo, PostAttributeMsg msg, IntegrationCallback<Void> callback);

    void process(TenantId asset, TbMsg tbMsg, IntegrationCallback<Void> callback);

    Device getOrCreateDevice(Integration integration, String deviceName, String deviceType, String customerName, String groupName);

    Asset getOrCreateAsset(Integration configuration, String assetName, String assetType, String customerName, String groupName);

    EntityView getOrCreateEntityView(Integration configuration, Device device, org.thingsboard.server.gen.integration.EntityViewDataProto proto);
}
