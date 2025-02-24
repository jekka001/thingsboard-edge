/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.service.edge.rpc.processor.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.AttributeScope;
import org.thingsboard.server.common.data.EdgeUtils;
import org.thingsboard.server.common.data.converter.Converter;
import org.thingsboard.server.common.data.edge.EdgeEvent;
import org.thingsboard.server.common.data.edge.EdgeEventType;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.gen.edge.v1.DownlinkMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.edge.EdgeMsgConstructorUtils;
import org.thingsboard.server.service.edge.rpc.processor.BaseEdgeProcessor;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@TbCoreComponent
public class IntegrationEdgeProcessor extends BaseEdgeProcessor {

    @Override
    public DownlinkMsg convertEdgeEventToDownlink(EdgeEvent edgeEvent) {
        IntegrationId integrationId = new IntegrationId(edgeEvent.getEntityId());
        DownlinkMsg downlinkMsg = null;
        UpdateMsgType msgType = getUpdateMsgType(edgeEvent.getAction());
        switch (msgType) {
            case ENTITY_CREATED_RPC_MESSAGE, ENTITY_UPDATED_RPC_MESSAGE -> {
                Integration integration = edgeCtx.getIntegrationService().findIntegrationById(edgeEvent.getTenantId(), integrationId);
                if (integration != null) {
                    JsonNode updatedConfiguration = replaceAttributePlaceholders(edgeEvent, integration.getConfiguration());
                    DownlinkMsg.Builder builder = DownlinkMsg.newBuilder()
                            .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                            .addIntegrationMsg(EdgeMsgConstructorUtils.constructIntegrationUpdateMsg(msgType, integration, updatedConfiguration));

                    Converter uplinkConverter = edgeCtx.getConverterService().findConverterById(edgeEvent.getTenantId(), integration.getDefaultConverterId());
                    builder.addConverterMsg(EdgeMsgConstructorUtils.constructConverterUpdateMsg(msgType, uplinkConverter));

                    if (integration.getDownlinkConverterId() != null) {
                        Converter converter = edgeCtx.getConverterService().findConverterById(edgeEvent.getTenantId(), integration.getDownlinkConverterId());
                        builder.addConverterMsg(EdgeMsgConstructorUtils.constructConverterUpdateMsg(msgType, converter));
                    }

                    downlinkMsg = builder.build();
                }
            }
            case ENTITY_DELETED_RPC_MESSAGE -> downlinkMsg = DownlinkMsg.newBuilder()
                    .setDownlinkMsgId(EdgeUtils.nextPositiveInt())
                    .addIntegrationMsg(EdgeMsgConstructorUtils.constructIntegrationDeleteMsg(integrationId))
                    .build();
        }
        return downlinkMsg;
    }

    private JsonNode replaceAttributePlaceholders(EdgeEvent edgeEvent, JsonNode originalConfiguration) {
        try {
            Set<String> attributeKeysFromConfiguration =
                    EdgeUtils.getAttributeKeysFromConfiguration(originalConfiguration.toString());
            if (attributeKeysFromConfiguration.isEmpty()) {
                return originalConfiguration;
            }
            List<AttributeKvEntry> attributeKvEntries =
                    edgeCtx.getAttributesService().find(edgeEvent.getTenantId(),
                            edgeEvent.getEdgeId(),
                            AttributeScope.SERVER_SCOPE,
                            attributeKeysFromConfiguration).get();
            String updatedConfiguration = originalConfiguration.toString();
            for (AttributeKvEntry attributeKvEntry : attributeKvEntries) {
                updatedConfiguration =
                        updatedConfiguration.replaceAll(EdgeUtils.formatAttributeKeyToRegexpPlaceholderFormat(attributeKvEntry.getKey()), attributeKvEntry.getValueAsString());
            }
            return JacksonUtil.toJsonNode(updatedConfiguration);
        } catch (Exception e) {
            log.warn("Failed to replace attribute placeholders in configuration [{}]", originalConfiguration, e);
            return originalConfiguration;
        }
    }

    @Override
    public EdgeEventType getEdgeEventType() {
        return EdgeEventType.INTEGRATION;
    }

}
