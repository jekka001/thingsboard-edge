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
package org.thingsboard.integration.api.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.integration.api.data.IntegrationMetaData;
import org.thingsboard.js.api.JsInvokeService;
import org.thingsboard.js.api.JsScriptType;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.msg.TbMsg;

import javax.script.ScriptException;
import java.util.concurrent.ExecutionException;

@Slf4j
public class JSDownlinkEvaluator extends AbstractJSEvaluator {

    private static final ObjectMapper mapper = new ObjectMapper();

    public JSDownlinkEvaluator(JsInvokeService jsInvokeService, EntityId entityId, String script) {
        super(jsInvokeService, entityId, JsScriptType.DOWNLINK_CONVERTER_SCRIPT, script);
    }

    public JsonNode execute(TbMsg msg, IntegrationMetaData metadata) throws ScriptException {
        try {
            validateSuccessfulScriptLazyInit();
            String[] inArgs = prepareArgs(msg, metadata);
            String eval = jsInvokeService.invokeFunction(this.scriptId, inArgs[0], inArgs[1], inArgs[2], inArgs[3]).get().toString();
            return mapper.readTree(eval);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ScriptException) {
                throw (ScriptException)e.getCause();
            } else {
                throw new ScriptException("Failed to execute js script: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new ScriptException("Failed to execute js script: " + e.getMessage());
        }
    }

    private static String[] prepareArgs(TbMsg msg, IntegrationMetaData metadata) {
        try {
            String[] args = new String[4];
            if (msg.getData() != null) {
                args[0] = msg.getData();
            } else {
                args[0] = "";
            }
            args[1] = mapper.writeValueAsString(msg.getMetaData().getData());
            args[2] = msg.getType();
            args[3] = mapper.writeValueAsString(metadata.getKvMap());
            return args;
        } catch (Throwable th) {
            throw new IllegalArgumentException("Cannot bind js args", th);
        }
    }

}
