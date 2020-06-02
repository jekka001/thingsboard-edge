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

import lombok.extern.slf4j.Slf4j;
import org.thingsboard.js.api.JsInvokeService;
import org.thingsboard.js.api.JsScriptType;
import org.thingsboard.server.common.data.id.EntityId;

import java.util.UUID;

@Slf4j
public abstract class AbstractJSEvaluator {

    protected final JsInvokeService jsInvokeService;
    private final JsScriptType scriptType;
    private final String script;
    protected final EntityId entityId;

    protected volatile UUID scriptId;
    private volatile boolean isErrorScript = false;


    public AbstractJSEvaluator(JsInvokeService jsInvokeService, EntityId entityId, JsScriptType scriptType, String script) {
        this.jsInvokeService = jsInvokeService;
        this.scriptType = scriptType;
        this.script = script;
        this.entityId = entityId;
    }

    public void destroy() {
        if (this.scriptId != null) {
            this.jsInvokeService.release(this.scriptId);
        }
    }

    void validateSuccessfulScriptLazyInit() {
        if (this.scriptId != null) {
            return;
        }

        if (isErrorScript) {
            throw new IllegalArgumentException("Can't compile uplink converter script ");
        }

        synchronized (this) {
            if (this.scriptId == null) {
                try {
                    this.scriptId = this.jsInvokeService.eval(scriptType, script).get();
                } catch (Exception e) {
                    isErrorScript = true;
                    throw new IllegalArgumentException("Can't compile script: " + e.getMessage(), e);
                }
            }
        }
    }

}
