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
package org.thingsboard.integration.api.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Base64Utils;
import org.thingsboard.integration.api.IntegrationCallback;
import org.thingsboard.integration.api.util.ExceptionUtil;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.converter.Converter;

import java.nio.charset.StandardCharsets;

import static org.thingsboard.integration.api.util.ConvertUtil.toDebugMessage;

/**
 * Created by ashvayka on 18.12.17.
 */
@Slf4j
public abstract class AbstractDataConverter implements TBDataConverter {

    protected final ObjectMapper mapper = new ObjectMapper();
    protected Converter configuration;

    @Override
    public void init(Converter configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getName() {
        return configuration != null ? configuration.getName() : null;
    }

    protected String toString(Exception e) {
        return ExceptionUtil.toString(e, configuration.getId(), isExceptionStackTraceEnabled());
    }

    protected void persistDebug(ConverterContext context, String type, String inMessageType, byte[] inMessage,
                                String outMessageType, byte[] outMessage, String metadata, Exception exception) {
        ObjectNode node = mapper.createObjectNode()
                .put("server", context.getServiceId())
                .put("type", type)
                .put("inMessageType", inMessageType)
                .put("in", toDebugMessage(inMessageType, inMessage))
                .put("outMessageType", outMessageType)
                .put("out", toDebugMessage(outMessageType, outMessage))
                .put("metadata", metadata);

        if (exception != null) {
            node = node.put("error", toString(exception));
        }
        context.saveEvent(DataConstants.DEBUG_CONVERTER, node, new DebugEventCallback());
    }

    private static class DebugEventCallback implements IntegrationCallback<Void> {
        @Override
        public void onSuccess(Void msg) {
            if (log.isDebugEnabled()) {
                log.debug("Event has been saved successfully!");
            }
        }

        @Override
        public void onError(Throwable e) {
            log.error("Failed to save the debug event!", e);
        }
    }

    abstract boolean isExceptionStackTraceEnabled();
}
