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
package org.thingsboard.server.transport.lwm2m.utils;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.eclipse.leshan.core.node.codec.CodecException;
import org.eclipse.leshan.core.node.codec.LwM2mValueConverter;
import org.eclipse.leshan.core.util.Hex;
import org.eclipse.leshan.core.util.StringUtils;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.eclipse.leshan.core.model.ResourceModel.Type.OPAQUE;

@Slf4j
public class LwM2mValueConverterImpl implements LwM2mValueConverter {

    private static final LwM2mValueConverterImpl INSTANCE = new LwM2mValueConverterImpl();

    public static LwM2mValueConverterImpl getInstance() {
        return INSTANCE;
    }

    @Override
    public Object convertValue(Object value, Type currentType, Type expectedType, LwM2mPath resourcePath)
            throws CodecException {
        if (expectedType == null) {
            /** unknown resource, trusted value */
            return value;
        }

        if (currentType == expectedType) {
            /** expected type */
            return value;
        }
        if (currentType == null) {
            currentType = OPAQUE;
        }

        switch (expectedType) {
            case INTEGER:
                switch (currentType) {
                    case FLOAT:
                        log.debug("Trying to convert float value [{}] to integer", value);
                        Long longValue = ((Double) value).longValue();
                        if ((double) value == longValue.doubleValue()) {
                            return longValue;
                        }
                    default:
                        break;
                }
                break;
            case FLOAT:
                switch (currentType) {
                    case INTEGER:
                        log.debug("Trying to convert integer value [{}] to float", value);
                        Double floatValue = ((Long) value).doubleValue();
                        if ((long) value == floatValue.longValue()) {
                            return floatValue;
                        }
                    default:
                        break;
                }
                break;
            case BOOLEAN:
                switch (currentType) {
                    case STRING:
                        log.debug("Trying to convert string value {} to boolean", value);
                        if (StringUtils.equalsIgnoreCase((String) value, "true")) {
                            return true;
                        } else if (StringUtils.equalsIgnoreCase((String) value, "false")) {
                            return false;
                        }
                        break;
                    case INTEGER:
                        log.debug("Trying to convert int value {} to boolean", value);
                        Long val = (Long) value;
                        if (val == 1) {
                            return true;
                        } else if (val == 0) {
                            return false;
                        }
                        break;
                    default:
                        break;
                }
                break;
            case TIME:
                switch (currentType) {
                    case INTEGER:
                        log.debug("Trying to convert long value {} to date", value);
                        /** let's assume we received the millisecond since 1970/1/1 */
                        return new Date((Long) value);
                    case STRING:
                        log.debug("Trying to convert string value {} to date", value);
                        /** let's assume we received an ISO 8601 format date */
                        try {
                            DatatypeFactory datatypeFactory = DatatypeFactory.newInstance();
                            XMLGregorianCalendar cal = datatypeFactory.newXMLGregorianCalendar((String) value);
                            return cal.toGregorianCalendar().getTime();
                        } catch (DatatypeConfigurationException | IllegalArgumentException e) {
                            log.debug("Unable to convert string to date", e);
                            throw new CodecException("Unable to convert string (%s) to date for resource %s", value,
                                    resourcePath);
                        }
                    default:
                        break;
                }
                break;
            case STRING:
                switch (currentType) {
                    case BOOLEAN:
                    case INTEGER:
                    case FLOAT:
                        return String.valueOf(value);
                    case TIME:
                        String DATE_FORMAT = "MMM d, yyyy HH:mm a";
                        Long timeValue;
                        try {
                            timeValue = ((Date) value).getTime();
                        }
                        catch (Exception e){
                           timeValue = new BigInteger((byte [])value).longValue();
                        }
                        DateFormat formatter = new SimpleDateFormat(DATE_FORMAT);
                        return formatter.format(new Date(timeValue));
                    case OPAQUE:
                        return Hex.encodeHexString((byte[])value);
                    default:
                        break;
                }
                break;
            case OPAQUE:
                if (currentType == Type.STRING) {
                    /** let's assume we received an hexadecimal string */
                    log.debug("Trying to convert hexadecimal string [{}] to byte array", value);
                    // TODO check if we shouldn't instead assume that the string contains Base64 encoded data
                    try {
                        return Hex.decodeHex(((String)value).toCharArray());
                    } catch (IllegalArgumentException e) {
                        throw new CodecException("Unable to convert hexastring [%s] to byte array for resource %s", value,
                                resourcePath);
                    }
                }
                break;
            default:
        }

        throw new CodecException("Invalid value type for resource %s, expected %s, got %s", resourcePath, expectedType,
                currentType);
    }
}
