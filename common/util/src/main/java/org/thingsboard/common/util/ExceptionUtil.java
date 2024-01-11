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
package org.thingsboard.common.util;

import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.StringUtils;
import org.thingsboard.server.common.data.id.EntityId;

import javax.script.ScriptException;
import java.io.PrintWriter;
import java.io.StringWriter;

@Slf4j
public class ExceptionUtil {

    @SuppressWarnings("unchecked")
    public static <T extends Exception> T lookupException(Throwable source, Class<T> clazz) {
        Exception e = lookupExceptionInCause(source, clazz);
        if (e != null) {
            return (T) e;
        } else {
            return null;
        }
    }

    public static Exception lookupExceptionInCause(Throwable source, Class<? extends Exception>... clazzes) {
        while (source != null) {
            for (Class<? extends Exception> clazz : clazzes) {
                if (clazz.isAssignableFrom(source.getClass())) {
                    return (Exception) source;
                }
            }
            source = source.getCause();
        }
        return null;
    }

    public static String toString(Exception e, EntityId componentId, boolean stackTraceEnabled) {
        Exception exception = lookupExceptionInCause(e, ScriptException.class, JsonParseException.class);
        if (exception != null && StringUtils.isNotEmpty(exception.getMessage())) {
            return exception.getMessage();
        } else {
            if (stackTraceEnabled) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                return sw.toString();
            } else {
                log.debug("[{}] Unknown error during message processing", componentId, e);
                return "Please contact system administrator";
            }
        }
    }
}
