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
package org.thingsboard.script.api.tbel;

import com.google.common.primitives.Bytes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.mvel2.ExecutionContext;
import org.mvel2.ParserConfiguration;
import org.mvel2.execution.ExecutionArrayList;
import org.mvel2.execution.ExecutionHashMap;
import org.mvel2.util.MethodStub;
import org.thingsboard.server.common.data.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static java.lang.Character.MAX_RADIX;
import static java.lang.Character.MIN_RADIX;

@Slf4j
public class TbUtils {

    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    private static final int ZERO_RADIX = 0;
    private static final int OCTAL_RADIX = 8;
    private static final int DEC_RADIX = 10;
    private static final int HEX_RADIX = 16;
    private static final int HEX_LEN_MIN = -1;
    private static final int HEX_LEN_INT_MAX = 8;
    private static final int HEX_LEN_LONG_MAX = 16;
    private static final int BYTES_LEN_INT_MAX = 4;
    private static final int BYTES_LEN_LONG_MAX = 8;

    private static final LinkedHashMap<String, String> mdnEncodingReplacements = new LinkedHashMap<>();

    static {
        mdnEncodingReplacements.put("\\+", "%20");
        mdnEncodingReplacements.put("%21", "!");
        mdnEncodingReplacements.put("%27", "'");
        mdnEncodingReplacements.put("%28", "\\(");
        mdnEncodingReplacements.put("%29", "\\)");
        mdnEncodingReplacements.put("%7E", "~");
        mdnEncodingReplacements.put("%3B", ";");
        mdnEncodingReplacements.put("%2C", ",");
        mdnEncodingReplacements.put("%2F", "/");
        mdnEncodingReplacements.put("%3F", "\\?");
        mdnEncodingReplacements.put("%3A", ":");
        mdnEncodingReplacements.put("%40", "@");
        mdnEncodingReplacements.put("%26", "&");
        mdnEncodingReplacements.put("%3D", "=");
        mdnEncodingReplacements.put("%2B", "\\+");
        mdnEncodingReplacements.put("%24", Matcher.quoteReplacement("$"));
        mdnEncodingReplacements.put("%23", "#");
    }

    public static void register(ParserConfiguration parserConfig) throws Exception {
        parserConfig.addImport("btoa", new MethodStub(TbUtils.class.getMethod("btoa",
                String.class)));
        parserConfig.addImport("atob", new MethodStub(TbUtils.class.getMethod("atob",
                String.class)));
        parserConfig.addImport("bytesToString", new MethodStub(TbUtils.class.getMethod("bytesToString",
                List.class)));
        parserConfig.addImport("bytesToString", new MethodStub(TbUtils.class.getMethod("bytesToString",
                List.class, String.class)));
        parserConfig.addImport("decodeToString", new MethodStub(TbUtils.class.getMethod("bytesToString",
                List.class)));
        parserConfig.addImport("decodeToJson", new MethodStub(TbUtils.class.getMethod("decodeToJson",
                ExecutionContext.class, List.class)));
        parserConfig.addImport("decodeToJson", new MethodStub(TbUtils.class.getMethod("decodeToJson",
                ExecutionContext.class, String.class)));
        parserConfig.addImport("stringToBytes", new MethodStub(TbUtils.class.getMethod("stringToBytes",
                ExecutionContext.class, Object.class)));
        parserConfig.addImport("stringToBytes", new MethodStub(TbUtils.class.getMethod("stringToBytes",
                ExecutionContext.class, Object.class, String.class)));
        parserConfig.registerNonConvertableMethods(TbUtils.class, Collections.singleton("stringToBytes"));
        parserConfig.addImport("parseInt", new MethodStub(TbUtils.class.getMethod("parseInt",
                String.class)));
        parserConfig.addImport("parseInt", new MethodStub(TbUtils.class.getMethod("parseInt",
                String.class, int.class)));
        parserConfig.addImport("parseLong", new MethodStub(TbUtils.class.getMethod("parseLong",
                String.class)));
        parserConfig.addImport("parseLong", new MethodStub(TbUtils.class.getMethod("parseLong",
                String.class, int.class)));
        parserConfig.addImport("parseFloat", new MethodStub(TbUtils.class.getMethod("parseFloat",
                String.class)));
        parserConfig.addImport("parseFloat", new MethodStub(TbUtils.class.getMethod("parseFloat",
                String.class, int.class)));
        parserConfig.addImport("parseHexIntLongToFloat", new MethodStub(TbUtils.class.getMethod("parseHexIntLongToFloat",
                String.class, boolean.class)));
        parserConfig.addImport("parseDouble", new MethodStub(TbUtils.class.getMethod("parseDouble",
                String.class)));
        parserConfig.addImport("parseLittleEndianHexToInt", new MethodStub(TbUtils.class.getMethod("parseLittleEndianHexToInt",
                String.class)));
        parserConfig.addImport("parseBigEndianHexToInt", new MethodStub(TbUtils.class.getMethod("parseBigEndianHexToInt",
                String.class)));
        parserConfig.addImport("parseHexToInt", new MethodStub(TbUtils.class.getMethod("parseHexToInt",
                String.class)));
        parserConfig.addImport("parseHexToInt", new MethodStub(TbUtils.class.getMethod("parseHexToInt",
                String.class, boolean.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                List.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                List.class, int.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                List.class, int.class, int.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                List.class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                byte[].class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                byte[].class, int.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                byte[].class, int.class, int.class)));
        parserConfig.addImport("parseBytesToInt", new MethodStub(TbUtils.class.getMethod("parseBytesToInt",
                byte[].class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseLittleEndianHexToLong", new MethodStub(TbUtils.class.getMethod("parseLittleEndianHexToLong",
                String.class)));
        parserConfig.addImport("parseBigEndianHexToLong", new MethodStub(TbUtils.class.getMethod("parseBigEndianHexToLong",
                String.class)));
        parserConfig.addImport("parseHexToLong", new MethodStub(TbUtils.class.getMethod("parseHexToLong",
                String.class)));
        parserConfig.addImport("parseHexToLong", new MethodStub(TbUtils.class.getMethod("parseHexToLong",
                String.class, boolean.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                List.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                List.class, int.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                List.class, int.class, int.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                List.class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                byte[].class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                byte[].class, int.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                byte[].class, int.class, int.class)));
        parserConfig.addImport("parseBytesToLong", new MethodStub(TbUtils.class.getMethod("parseBytesToLong",
                byte[].class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseLittleEndianHexToFloat", new MethodStub(TbUtils.class.getMethod("parseLittleEndianHexToFloat",
                String.class)));
        parserConfig.addImport("parseBigEndianHexToFloat", new MethodStub(TbUtils.class.getMethod("parseBigEndianHexToFloat",
                String.class)));
        parserConfig.addImport("parseHexToFloat", new MethodStub(TbUtils.class.getMethod("parseHexToFloat",
                String.class)));
        parserConfig.addImport("parseHexToFloat", new MethodStub(TbUtils.class.getMethod("parseHexToFloat",
                String.class, boolean.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                List.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                List.class, int.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                List.class, int.class, int.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                List.class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                byte[].class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                byte[].class, int.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                byte[].class, int.class, int.class)));
        parserConfig.addImport("parseBytesToFloat", new MethodStub(TbUtils.class.getMethod("parseBytesToFloat",
                byte[].class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseLittleEndianHexToDouble", new MethodStub(TbUtils.class.getMethod("parseLittleEndianHexToDouble",
                String.class)));
        parserConfig.addImport("parseBigEndianHexToDouble", new MethodStub(TbUtils.class.getMethod("parseBigEndianHexToDouble",
                String.class)));
        parserConfig.addImport("parseHexToDouble", new MethodStub(TbUtils.class.getMethod("parseHexToDouble",
                String.class)));
        parserConfig.addImport("parseHexToDouble", new MethodStub(TbUtils.class.getMethod("parseHexToDouble",
                String.class, boolean.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                List.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                List.class, int.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                List.class, int.class, int.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                List.class, int.class, int.class, boolean.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                byte[].class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                byte[].class, int.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                byte[].class, int.class, int.class)));
        parserConfig.addImport("parseBytesToDouble", new MethodStub(TbUtils.class.getMethod("parseBytesToDouble",
                byte[].class, int.class, int.class, boolean.class)));
        parserConfig.addImport("toFixed", new MethodStub(TbUtils.class.getMethod("toFixed",
                double.class, int.class)));
        parserConfig.addImport("toFixed", new MethodStub(TbUtils.class.getMethod("toFixed",
                float.class, int.class)));
        parserConfig.addImport("hexToBytes", new MethodStub(TbUtils.class.getMethod("hexToBytes",
                ExecutionContext.class, String.class)));
        parserConfig.addImport("intToHex", new MethodStub(TbUtils.class.getMethod("intToHex",
                Integer.class)));
        parserConfig.addImport("intToHex", new MethodStub(TbUtils.class.getMethod("intToHex",
                Integer.class, boolean.class)));
        parserConfig.addImport("intToHex", new MethodStub(TbUtils.class.getMethod("intToHex",
                Integer.class, boolean.class, boolean.class)));
        parserConfig.addImport("intToHex", new MethodStub(TbUtils.class.getMethod("intToHex",
                Integer.class, boolean.class, boolean.class, int.class)));
        parserConfig.addImport("longToHex", new MethodStub(TbUtils.class.getMethod("longToHex",
                Long.class)));
        parserConfig.addImport("longToHex", new MethodStub(TbUtils.class.getMethod("longToHex",
                Long.class, boolean.class)));
        parserConfig.addImport("longToHex", new MethodStub(TbUtils.class.getMethod("longToHex",
                Long.class, boolean.class, boolean.class)));
        parserConfig.addImport("longToHex", new MethodStub(TbUtils.class.getMethod("longToHex",
                Long.class, boolean.class, boolean.class, int.class)));
        parserConfig.addImport("intLongToString", new MethodStub(TbUtils.class.getMethod("intLongToString",
                Long.class)));
        parserConfig.addImport("intLongToString", new MethodStub(TbUtils.class.getMethod("intLongToString",
                Long.class, int.class)));
        parserConfig.addImport("intLongToString", new MethodStub(TbUtils.class.getMethod("intLongToString",
                Long.class, int.class, boolean.class)));
        parserConfig.addImport("intLongToString", new MethodStub(TbUtils.class.getMethod("intLongToString",
                Long.class, int.class, boolean.class, boolean.class)));
        parserConfig.addImport("floatToHex", new MethodStub(TbUtils.class.getMethod("floatToHex",
                Float.class)));
        parserConfig.addImport("floatToHex", new MethodStub(TbUtils.class.getMethod("floatToHex",
                Float.class, boolean.class)));
        parserConfig.addImport("doubleToHex", new MethodStub(TbUtils.class.getMethod("doubleToHex",
                Double.class)));
        parserConfig.addImport("doubleToHex", new MethodStub(TbUtils.class.getMethod("doubleToHex",
                Double.class, boolean.class)));
        parserConfig.addImport("printUnsignedBytes", new MethodStub(TbUtils.class.getMethod("printUnsignedBytes",
                ExecutionContext.class, List.class)));
        parserConfig.addImport("base64ToHex", new MethodStub(TbUtils.class.getMethod("base64ToHex",
                String.class)));
        parserConfig.addImport("base64ToBytes", new MethodStub(TbUtils.class.getMethod("base64ToBytes",
                String.class)));
        parserConfig.addImport("bytesToBase64", new MethodStub(TbUtils.class.getMethod("bytesToBase64",
                byte[].class)));
        parserConfig.addImport("bytesToHex", new MethodStub(TbUtils.class.getMethod("bytesToHex",
                byte[].class)));
        parserConfig.addImport("bytesToHex", new MethodStub(TbUtils.class.getMethod("bytesToHex",
                ExecutionArrayList.class)));
        parserConfig.addImport("toFlatMap", new MethodStub(TbUtils.class.getMethod("toFlatMap",
                ExecutionContext.class, Map.class)));
        parserConfig.addImport("toFlatMap", new MethodStub(TbUtils.class.getMethod("toFlatMap",
                ExecutionContext.class, Map.class, boolean.class)));
        parserConfig.addImport("toFlatMap", new MethodStub(TbUtils.class.getMethod("toFlatMap",
                ExecutionContext.class, Map.class, List.class)));
        parserConfig.addImport("toFlatMap", new MethodStub(TbUtils.class.getMethod("toFlatMap",
                ExecutionContext.class, Map.class, List.class, boolean.class)));
        parserConfig.addImport("encodeURI", new MethodStub(TbUtils.class.getMethod("encodeURI",
                String.class)));
        parserConfig.addImport("decodeURI", new MethodStub(TbUtils.class.getMethod("decodeURI",
                String.class)));
        parserConfig.addImport("raiseError", new MethodStub(TbUtils.class.getMethod("raiseError",
                String.class, Object.class)));
        parserConfig.addImport("raiseError", new MethodStub(TbUtils.class.getMethod("raiseError",
                String.class)));
        parserConfig.addImport("isBinary", new MethodStub(TbUtils.class.getMethod("isBinary",
                String.class)));
        parserConfig.addImport("isOctal", new MethodStub(TbUtils.class.getMethod("isOctal",
                String.class)));
        parserConfig.addImport("isDecimal", new MethodStub(TbUtils.class.getMethod("isDecimal",
                String.class)));
        parserConfig.addImport("isHexadecimal", new MethodStub(TbUtils.class.getMethod("isHexadecimal",
                String.class)));
    }

    public static String btoa(String input) {
        return new String(Base64.getEncoder().encode(input.getBytes()));
    }

    public static String atob(String encoded) {
        return new String(Base64.getDecoder().decode(encoded));
    }

    public static Object decodeToJson(ExecutionContext ctx, List<Byte> bytesList) throws IOException {
        return TbJson.parse(ctx, bytesToString(bytesList));
    }

    public static Object decodeToJson(ExecutionContext ctx, String jsonStr) throws IOException {
        return TbJson.parse(ctx, jsonStr);
    }

    public static String bytesToString(List<?> bytesList) {
        byte[] bytes = bytesFromList(bytesList);
        return new String(bytes);
    }

    public static String bytesToString(List<?> bytesList, String charsetName) throws UnsupportedEncodingException {
        byte[] bytes = bytesFromList(bytesList);
        return new String(bytes, charsetName);
    }

    public static List<Byte> stringToBytes(ExecutionContext ctx, Object str) throws IllegalAccessException {
        if (str instanceof String) {
            byte[] bytes = str.toString().getBytes();
            return bytesToList(ctx, bytes);
        } else {
            throw new IllegalAccessException("Invalid type parameter [" + str.getClass().getSimpleName() + "]. Expected 'String'");
        }
    }

    public static List<Byte> stringToBytes(ExecutionContext ctx, Object str, String charsetName) throws UnsupportedEncodingException, IllegalAccessException {
        if (str instanceof String) {
            byte[] bytes = str.toString().getBytes(charsetName);
            return bytesToList(ctx, bytes);
        } else {
            throw new IllegalAccessException("Invalid type parameter [" + str.getClass().getSimpleName() + "]. Expected 'String'");
        }
    }

    private static byte[] bytesFromList(List<?> bytesList) {
        byte[] bytes = new byte[bytesList.size()];
        for (int i = 0; i < bytesList.size(); i++) {
            Object objectVal = bytesList.get(i);
            if (objectVal instanceof Integer) {
                bytes[i] = isValidIntegerToByte((Integer) objectVal);
            } else if (objectVal instanceof String) {
                bytes[i] = isValidIntegerToByte(parseInt((String) objectVal));
            } else if (objectVal instanceof Byte) {
                bytes[i] = (byte) objectVal;
            } else {
                throw new NumberFormatException("The value '" + objectVal + "' could not be correctly converted to a byte. " +
                        "Must be a HexDecimal/String/Integer/Byte format !");
            }
        }
        return bytes;
    }

    private static List<Byte> bytesToList(ExecutionContext ctx, byte[] bytes) {
        List<Byte> list = new ExecutionArrayList<>(ctx);
        for (byte aByte : bytes) {
            list.add(aByte);
        }
        return list;
    }

    public static Integer parseInt(String value) {
        return parseInt(value, ZERO_RADIX);
    }

    public static Integer parseInt(String value, int radix) {
        return parseInt(value, radix, true);
    }

    private static Integer parseInt(String value, int radix, boolean bigEndian) {
        String valueP = prepareNumberString(value, bigEndian);
        if (valueP != null) {
            int radixValue = isValidStringAndRadix(valueP, radix, value);
            if (radixValue >= 25 && radixValue <= MAX_RADIX) {
                return (Integer) compareIntLongValueMinMax(valueP, radixValue, Integer.MAX_VALUE, Integer.MIN_VALUE);
            }
            return switch (radixValue) {
                case MIN_RADIX -> parseBinaryStringAsSignedInteger(valueP);
                case OCTAL_RADIX, DEC_RADIX, HEX_RADIX -> Integer.parseInt(valueP, radixValue);
                default -> throw new IllegalArgumentException("Invalid radix: [" + radix + "]");
            };
        }
        return null;
    }

    public static Long parseLong(String value) {
        return parseLong(value, ZERO_RADIX);
    }

    public static Long parseLong(String value, int radix) {
        return parseLong(value, radix, true);
    }

    private static Long parseLong(String value, int radix, boolean bigEndian) {
        String valueP = prepareNumberString(value, bigEndian);
        if (valueP != null) {
            int radixValue = isValidStringAndRadix(valueP, radix, value);
            if (radixValue >= 25 && radixValue <= MAX_RADIX) {
                return (Long) compareIntLongValueMinMax(valueP, radixValue, Long.MAX_VALUE, Long.MIN_VALUE);
            }
            return switch (radixValue) {
                case MIN_RADIX -> parseBinaryStringAsSignedLong(valueP);
                case OCTAL_RADIX, DEC_RADIX, HEX_RADIX -> Long.parseLong(valueP, radixValue);
                default -> throw new IllegalArgumentException("Invalid radix: [" + radix + "]");
            };
        }
        return null;
    }

    private static int parseBinaryStringAsSignedInteger(String binaryString) {
        if (binaryString.length() != 32) {
            // Pad the binary string to 64 bits if it is not already
            binaryString = String.format("%32s", binaryString).replace(' ', '0');
        }

        // If the MSB is 1, the number is negative in two's complement
        if (binaryString.charAt(0) == '1') {
            // Calculate the two's complement
            String invertedBinaryString = invertBinaryString(binaryString);
            int positiveValue = Integer.parseInt(invertedBinaryString, MIN_RADIX) + 1;
            return -positiveValue;
        } else {
            return Integer.parseInt(binaryString, MIN_RADIX);
        }
    }

    private static long parseBinaryStringAsSignedLong(String binaryString) {
        if (binaryString.length() != 64) {
            // Pad the binary string to 64 bits if it is not already
            binaryString = String.format("%64s", binaryString).replace(' ', '0');
        }

        // If the MSB is 1, the number is negative in two's complement
        if (binaryString.charAt(0) == '1') {
            // Calculate the two's complement
            String invertedBinaryString = invertBinaryString(binaryString);
            long positiveValue = Long.parseLong(invertedBinaryString, MIN_RADIX) + 1;
            return -positiveValue;
        } else {
            return Long.parseLong(binaryString, MIN_RADIX);
        }
    }

    private static String invertBinaryString(String binaryString) {
        StringBuilder invertedString = new StringBuilder();
        for (char bit : binaryString.toCharArray()) {
            invertedString.append(bit == '0' ? '1' : '0');
        }
        return invertedString.toString();
    }

    private static int getRadix10_16(String value) {
        int radix = isDecimal(value) > 0 ? DEC_RADIX : isHexadecimal(value);
        if (radix > 0) {
            return radix;
        } else {
            throw new NumberFormatException("Value: \"" + value + "\" is not numeric or hexDecimal format!");
        }
    }

    public static Float parseFloat(String value) {
        return parseFloat(value, ZERO_RADIX);
    }

    public static Float parseFloat(String value, int radix) {
        String valueP = prepareNumberString(value, true);
        if (valueP != null) {
            return parseFloatFromString(value, valueP, radix);
        }
        return null;
    }

    private static Float parseFloatFromString(String value, String valueP, int radix) {
        int radixValue = isValidStringAndRadix(valueP, radix, value);
        if (radixValue == HEX_RADIX) {
            int bits = (int) Long.parseLong(valueP, HEX_RADIX);
            // Hex representation is a standard IEEE 754 float value (eg "0x41200000" for 10.0f).
            return Float.intBitsToFloat(bits);
        } else {
            return Float.parseFloat(value);
        }
    }

    public static Float parseHexIntLongToFloat(String value, boolean bigEndian) {
        String valueP = prepareNumberString(value, bigEndian);
        if (valueP != null) {
            int radixValue = isValidStringAndRadix(valueP, HEX_RADIX, value);
            if (radixValue == HEX_RADIX) {
                int bits = (int) Long.parseLong(valueP, HEX_RADIX);
                // If the length is not equal to 8 characters, we process it as an integer (eg "0x0A" for 10.0f).
                float floatValue = (float) bits;
                return Float.valueOf(floatValue);
            }
        }
        return null;
    }


    public static Double parseDouble(String value) {
        int radix = getRadix10_16(value);
        return parseDouble(value, radix);
    }

    public static Double parseDouble(String value, int radix) {
        return parseDouble(value, radix, true);
    }

    private static Double parseDouble(String value, int radix, boolean bigEndian) {
        String valueP = prepareNumberString(value, bigEndian);
        if (valueP != null) {
            int radixValue = isValidStringAndRadix(valueP, radix, value);
            if (radixValue == DEC_RADIX) {
                return Double.parseDouble(valueP);
            } else {
                long bits = Long.parseUnsignedLong(valueP, HEX_RADIX);
                return Double.longBitsToDouble(bits);
            }
        }
        return null;
    }

    public static int parseLittleEndianHexToInt(String hex) {
        return parseHexToInt(hex, false);
    }

    public static int parseBigEndianHexToInt(String hex) {
        return parseHexToInt(hex, true);
    }

    public static int parseHexToInt(String hex) {
        return parseHexToInt(hex, true);
    }

    public static Integer parseHexToInt(String value, boolean bigEndian) {
        return parseInt(value, HEX_RADIX, bigEndian);
    }

    public static long parseLittleEndianHexToLong(String hex) {
        return parseHexToLong(hex, false);
    }

    public static long parseBigEndianHexToLong(String hex) {
        return parseHexToLong(hex, true);
    }

    public static long parseHexToLong(String hex) {
        return parseHexToLong(hex, true);
    }

    public static Long parseHexToLong(String value, boolean bigEndian) {
        return parseLong(value, HEX_RADIX, bigEndian);
    }

    public static float parseLittleEndianHexToFloat(String hex) {
        return parseHexToFloat(hex, false);
    }

    public static float parseBigEndianHexToFloat(String hex) {
        return parseHexToFloat(hex, true);
    }

    public static float parseHexToFloat(String hex) {
        return parseHexToFloat(hex, true);
    }

    public static Float parseHexToFloat(String value, boolean bigEndian) {
        String valueP = prepareNumberString(value, bigEndian);
        if (valueP != null) {
            return parseFloatFromString(value, valueP, HEX_RADIX);
        }
        return null;
    }

    public static double parseLittleEndianHexToDouble(String hex) {
        return parseHexToDouble(hex, false);
    }

    public static double parseBigEndianHexToDouble(String hex) {
        return parseHexToDouble(hex, true);
    }

    public static double parseHexToDouble(String hex) {
        return parseHexToDouble(hex, true);
    }

    public static double parseHexToDouble(String value, boolean bigEndian) {
        return parseDouble(value, HEX_RADIX, bigEndian);
    }

    public static ExecutionArrayList<Byte> hexToBytes(ExecutionContext ctx, String value) {
        String hex = prepareNumberString(value, true);
        int len = hex.length();
        if (len % 2 > 0) {
            throw new IllegalArgumentException("Hex string must be even-length.");
        }
        ExecutionArrayList<Byte> data = new ExecutionArrayList<>(ctx);
        for (int i = 0; i < hex.length(); i += 2) {
            // Extract two characters from the hex string
            String byteString = hex.substring(i, i + 2);
            // Parse the hex string to a byte
            byte byteValue = (byte) Integer.parseInt(byteString, HEX_RADIX);
            // Add the byte to the ArrayList
            data.add(byteValue);
        }
        return data;
    }

    public static List<Integer> printUnsignedBytes(ExecutionContext ctx, List<Byte> byteArray) {
        ExecutionArrayList<Integer> data = new ExecutionArrayList<>(ctx);
        for (Byte b : byteArray) {
            // Convert signed byte to unsigned integer
            int unsignedByte = Byte.toUnsignedInt(b);
            data.add(unsignedByte);
        }
        return data;
    }

    public static String intToHex(Integer i) {
        return prepareNumberHexString(i.longValue(), true, false, HEX_LEN_MIN, HEX_LEN_INT_MAX);
    }

    public static String intToHex(Integer i, boolean bigEndian) {
        return prepareNumberHexString(i.longValue(), bigEndian, false, HEX_LEN_MIN, HEX_LEN_INT_MAX);
    }

    public static String intToHex(Integer i, boolean bigEndian, boolean pref) {
        return prepareNumberHexString(i.longValue(), bigEndian, pref, HEX_LEN_MIN, HEX_LEN_INT_MAX);
    }

    public static String intToHex(Integer i, boolean bigEndian, boolean pref, int len) {
        return prepareNumberHexString(i.longValue(), bigEndian, pref, len, HEX_LEN_INT_MAX);
    }

    public static String longToHex(Long l) {
        return prepareNumberHexString(l, true, false, HEX_LEN_MIN, HEX_LEN_LONG_MAX);
    }

    public static String longToHex(Long l, boolean bigEndian) {
        return prepareNumberHexString(l, bigEndian, false, HEX_LEN_MIN, HEX_LEN_LONG_MAX);
    }

    public static String longToHex(Long l, boolean bigEndian, boolean pref) {
        return prepareNumberHexString(l, bigEndian, pref, HEX_LEN_MIN, HEX_LEN_LONG_MAX);
    }

    public static String longToHex(Long l, boolean bigEndian, boolean pref, int len) {
        return prepareNumberHexString(l, bigEndian, pref, len, HEX_LEN_LONG_MAX);
    }

    public static String intLongToString(Long number) {
        return intLongToString(number, DEC_RADIX);
    }

    public static String intLongToString(Long number, int radix) {
        return intLongToString(number, radix, true);
    }

    public static String intLongToString(Long number, int radix, boolean bigEndian) {
        return intLongToString(number, radix, bigEndian, false);
    }

    public static String intLongToString(Long number, int radix, boolean bigEndian, boolean pref) {
        if (radix >= 25 && radix <= MAX_RADIX) {
            return Long.toString(number, radix);
        }
        return switch (radix) {
            case MIN_RADIX -> Long.toBinaryString(number);
            case OCTAL_RADIX -> Long.toOctalString(number);
            case DEC_RADIX -> Long.toString(number);
            case HEX_RADIX -> prepareNumberHexString(number, bigEndian, pref, -1, -1);
            default -> throw new IllegalArgumentException("Invalid radix: [" + radix + "]");
        };
    }

    private static Number compareIntLongValueMinMax(String valueP, int radix, Number maxValue, Number minValue) {
        boolean isInteger = maxValue.getClass().getSimpleName().equals("Integer");
        try {
            if (isInteger) {
                return Integer.parseInt(valueP, radix);
            } else {
                return Long.parseLong(valueP, radix);
            }
        } catch (NumberFormatException e) {
            BigInteger bi = new BigInteger(valueP, radix);
            long maxValueL = isInteger ? maxValue.longValue() : (long) maxValue;
            if (bi.compareTo(BigInteger.valueOf(maxValueL)) > 0) {
                throw new NumberFormatException("Value \"" + valueP + "\"is greater than the maximum " + maxValue.getClass().getSimpleName() + " value " + maxValue + " !");
            }
            long minValueL = isInteger ? minValue.longValue() : (long) minValue;
            if (bi.compareTo(BigInteger.valueOf(minValueL)) < 0) {
                throw new NumberFormatException("Value \"" + valueP + "\" is  less than the minimum " + minValue.getClass().getSimpleName() + " value " + minValue + " !");
            }
            throw new NumberFormatException(e.getMessage());
        }
    }

    private static String prepareNumberHexString(Long number, boolean bigEndian, boolean pref, int len, int hexLenMax) {
        String hex = Long.toHexString(number).toUpperCase();
        hexLenMax = hexLenMax < 0 ? hex.length() : hexLenMax;
        String hexWithoutZeroFF = removeLeadingZero_FF(hex, number, hexLenMax);
        hexWithoutZeroFF = bigEndian ? hexWithoutZeroFF : reverseHexStringByOrder(hexWithoutZeroFF);
        len = len == HEX_LEN_MIN ? hexWithoutZeroFF.length() : len;
        String result = hexWithoutZeroFF.substring(hexWithoutZeroFF.length() - len);
        return pref ? "0x" + result : result;
    }

    private static String removeLeadingZero_FF(String hex, Long number, int hexLenMax) {
        String hexWithoutZero = hex.replaceFirst("^0+(?!$)", ""); // Remove leading zeros except for the last one
        hexWithoutZero = hexWithoutZero.length() % 2 > 0 ? "0" + hexWithoutZero : hexWithoutZero;
        if (number >= 0) {
            return hexWithoutZero;
        } else {
            String hexWithoutZeroFF = hexWithoutZero.replaceFirst("^F+(?!$)", "");
            hexWithoutZeroFF = hexWithoutZeroFF.length() % 2 > 0 ? "F" + hexWithoutZeroFF : hexWithoutZeroFF;
            if (hexWithoutZeroFF.length() > hexLenMax) {
                return hexWithoutZeroFF.substring(hexWithoutZeroFF.length() - hexLenMax);
            } else if (hexWithoutZeroFF.length() == hexLenMax) {
                return hexWithoutZeroFF;
            } else {
                return "FF" + hexWithoutZeroFF;
            }
        }
    }

    public static String floatToHex(Float f) {
        return floatToHex(f, true);
    }

    public static String floatToHex(Float f, boolean bigEndian) {
        // Convert the float to its raw integer bits representation
        int bits = Float.floatToIntBits(f);

        // Format the integer bits as a hexadecimal string
        String result = String.format("0x%08X", bits);
        return bigEndian ? result : reverseHexStringByOrder(result);
    }

    public static String doubleToHex(Double d) {
        return doubleToHex(d, true);
    }

    public static String doubleToHex(Double d, boolean bigEndian) {
        long bits = Double.doubleToRawLongBits(d);

        // Format the integer bits as a hexadecimal string
        String result = String.format("0x%16X", bits);
        return bigEndian ? result : reverseHexStringByOrder(result);
    }

    public static String base64ToHex(String base64) {
        return bytesToHex(Base64.getDecoder().decode(base64));
    }

    public static String bytesToBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] base64ToBytes(String input) {
        return Base64.getDecoder().decode(input);
    }

    public static int parseBytesToInt(List<Byte> data) {
        return parseBytesToInt(Bytes.toArray(data));
    }

    public static int parseBytesToInt(List<Byte> data, int offset) {
        return parseBytesToInt(Bytes.toArray(data), offset);
    }

    public static int parseBytesToInt(List<Byte> data, int offset, int length) {
        return parseBytesToInt(Bytes.toArray(data), offset, length);
    }

    public static int parseBytesToInt(List<Byte> data, int offset, int length, boolean bigEndian) {
        return parseBytesToInt(Bytes.toArray(data), offset, length, bigEndian);
    }

    public static int parseBytesToInt(byte[] data) {
        return parseBytesToInt(data, 0);
    }

    public static int parseBytesToInt(byte[] data, int offset) {
        return parseBytesToInt(data, offset, BYTES_LEN_INT_MAX);
    }

    public static int parseBytesToInt(byte[] data, int offset, int length) {
        return parseBytesToInt(data, offset, length, true);
    }

    public static int parseBytesToInt(byte[] data, int offset, int length, boolean bigEndian) {
        if (offset > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " is out of bounds for array with length: " + data.length + "!");
        }
        if (length > BYTES_LEN_INT_MAX) {
            throw new IllegalArgumentException("Length: " + length + " is too large. Maximum 4 bytes is allowed!");
        }
        if (offset + length > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " and Length: " + length + " is out of bounds for array with length: " + data.length + "!");
        }
        var bb = ByteBuffer.allocate(4);
        if (!bigEndian) {
            bb.order(ByteOrder.LITTLE_ENDIAN);
        }
        bb.position(bigEndian ? 4 - length : 0);
        bb.put(data, offset, length);
        bb.position(0);
        return bb.getInt();
    }

    public static long parseBytesToLong(List<Byte> data) {
        return parseBytesToLong(Bytes.toArray(data));
    }

    public static long parseBytesToLong(List<Byte> data, int offset) {
        return parseBytesToLong(Bytes.toArray(data), offset);
    }

    public static long parseBytesToLong(List<Byte> data, int offset, int length) {
        return parseBytesToLong(Bytes.toArray(data), offset, length);
    }

    public static long parseBytesToLong(List<Byte> data, int offset, int length, boolean bigEndian) {
        return parseBytesToLong(Bytes.toArray(data), offset, length, bigEndian);
    }

    public static long parseBytesToLong(byte[] data) {
        return parseBytesToLong(data, 0);
    }

    public static long parseBytesToLong(byte[] data, int offset) {
        return parseBytesToLong(data, offset, BYTES_LEN_LONG_MAX);
    }

    public static long parseBytesToLong(byte[] data, int offset, int length) {
        return parseBytesToLong(data, offset, length, true);
    }

    public static long parseBytesToLong(byte[] data, int offset, int length, boolean bigEndian) {
        if (offset > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " is out of bounds for array with length: " + data.length + "!");
        }
        if (length > BYTES_LEN_LONG_MAX) {
            throw new IllegalArgumentException("Length: " + length + " is too large. Maximum " + BYTES_LEN_LONG_MAX + " bytes is allowed!");
        }
        if (offset + length > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " and Length: " + length + " is out of bounds for array with length: " + data.length + "!");
        }
        var bb = ByteBuffer.allocate(BYTES_LEN_LONG_MAX);
        if (!bigEndian) {
            bb.order(ByteOrder.LITTLE_ENDIAN);
        }
        bb.position(bigEndian ? BYTES_LEN_LONG_MAX - length : 0);
        bb.put(data, offset, length);
        bb.position(0);
        return bb.getLong();
    }

    public static float parseBytesToFloat(List data) {
        return parseBytesToFloat(Bytes.toArray(data), 0);
    }

    public static float parseBytesToFloat(List data, int offset) {
        return parseBytesToFloat(Bytes.toArray(data), offset, BYTES_LEN_INT_MAX);
    }

    public static float parseBytesToFloat(List data, int offset, int length) {
        return parseBytesToFloat(Bytes.toArray(data), offset, length, true);
    }

    public static float parseBytesToFloat(List data, int offset, int length, boolean bigEndian) {
        return parseBytesToFloat(Bytes.toArray(data), offset, length, bigEndian);
    }

    public static float parseBytesToFloat(byte[] data) {
        return parseBytesToFloat(data, 0);
    }

    public static float parseBytesToFloat(byte[] data, int offset) {
        return parseBytesToFloat(data, offset, BYTES_LEN_INT_MAX);
    }

    public static float parseBytesToFloat(byte[] data, int offset, int length) {
        return parseBytesToFloat(data, offset, length, true);
    }

    public static float parseBytesToFloat(byte[] data, int offset, int length, boolean bigEndian) {
        if (length > BYTES_LEN_INT_MAX) {
            throw new IllegalArgumentException("Length: " + length + " is too large. Maximum " + BYTES_LEN_INT_MAX + " bytes is allowed!");
        }
        if (offset + length > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " and Length: " + length + " is out of bounds for array with length: " + data.length + "!");
        }
        byte[] bytesToNumber = prepareBytesToNumber(data, offset, length, bigEndian);
        if (bytesToNumber.length < BYTES_LEN_INT_MAX) {
            byte[] extendedBytes = new byte[BYTES_LEN_INT_MAX];
            Arrays.fill(extendedBytes, (byte) 0);
            System.arraycopy(bytesToNumber, 0, extendedBytes, 0, bytesToNumber.length);
            bytesToNumber = extendedBytes;
        }
        float floatValue = ByteBuffer.wrap(bytesToNumber).getFloat();
        if (!Float.isNaN(floatValue)) {
            return floatValue;
        } else {
            long longValue = parseBytesToLong(bytesToNumber, 0, BYTES_LEN_INT_MAX);
            BigDecimal bigDecimalValue = new BigDecimal(longValue);
            return bigDecimalValue.floatValue();
        }
    }

    public static double parseBytesToDouble(List data) {
        return parseBytesToDouble(Bytes.toArray(data));
    }

    public static double parseBytesToDouble(List data, int offset) {
        return parseBytesToDouble(Bytes.toArray(data), offset);
    }

    public static double parseBytesToDouble(List data, int offset, int length) {
        return parseBytesToDouble(Bytes.toArray(data), offset, length);
    }

    public static double parseBytesToDouble(List data, int offset, int length, boolean bigEndian) {
        return parseBytesToDouble(Bytes.toArray(data), offset, length, bigEndian);
    }

    public static double parseBytesToDouble(byte[] data) {
        return parseBytesToDouble(data, 0);
    }

    public static double parseBytesToDouble(byte[] data, int offset) {
        return parseBytesToDouble(data, offset, BYTES_LEN_LONG_MAX);
    }

    public static double parseBytesToDouble(byte[] data, int offset, int length) {
        return parseBytesToDouble(data, offset, length, true);
    }

    public static double parseBytesToDouble(byte[] data, int offset, int length, boolean bigEndian) {
        if (length > BYTES_LEN_LONG_MAX) {
            throw new IllegalArgumentException("Length: " + length + " is too large. Maximum " + BYTES_LEN_LONG_MAX + " bytes is allowed!");
        }
        if (offset + length > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " and Length: " + length + " is out of bounds for array with length: " + data.length + "!");
        }
        byte[] bytesToNumber = prepareBytesToNumber(data, offset, length, bigEndian);
        if (bytesToNumber.length < BYTES_LEN_LONG_MAX) {
            byte[] extendedBytes = new byte[BYTES_LEN_LONG_MAX];
            Arrays.fill(extendedBytes, (byte) 0);
            System.arraycopy(bytesToNumber, 0, extendedBytes, 0, bytesToNumber.length);
            bytesToNumber = extendedBytes;
        }
        double doubleValue = ByteBuffer.wrap(bytesToNumber).getDouble();
        if (!Double.isNaN(doubleValue)) {
            return doubleValue;
        } else {
            BigInteger bigInt = new BigInteger(1, bytesToNumber);
            BigDecimal bigDecimalValue = new BigDecimal(bigInt);
            return bigDecimalValue.doubleValue();
        }
    }

    private static byte[] prepareBytesToNumber(byte[] data, int offset, int length, boolean bigEndian) {
        if (offset > data.length) {
            throw new IllegalArgumentException("Offset: " + offset + " is out of bounds for array with length: " + data.length + "!");
        }
        if ((offset + length) > data.length) {
            throw new IllegalArgumentException("Default length is always " + length + " bytes. Offset: " + offset + " and Length: " + length + " is out of bounds for array with length: " + data.length + "!");
        }
        byte[] dataBytesArray = Arrays.copyOfRange(data, offset, (offset + length));
        if (!bigEndian) {
            ArrayUtils.reverse(dataBytesArray);
        }
        return dataBytesArray;
    }

    public static String bytesToHex(ExecutionArrayList<?> bytesList) {
        byte[] bytes = new byte[bytesList.size()];
        for (int i = 0; i < bytesList.size(); i++) {
            bytes[i] = Byte.parseByte(bytesList.get(i).toString());
        }
        return bytesToHex(bytes);
    }

    public static String bytesToHex(byte[] bytes) {
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    public static double toFixed(double value, int precision) {
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP).doubleValue();
    }

    public static float toFixed(float value, int precision) {
        return BigDecimal.valueOf(value).setScale(precision, RoundingMode.HALF_UP).floatValue();
    }

    public static ExecutionHashMap<String, Object> toFlatMap(ExecutionContext ctx, Map<String, Object> json) {
        return toFlatMap(ctx, json, new ArrayList<>(), true);
    }

    public static ExecutionHashMap<String, Object> toFlatMap(ExecutionContext ctx, Map<String, Object> json, boolean pathInKey) {
        return toFlatMap(ctx, json, new ArrayList<>(), pathInKey);
    }

    public static ExecutionHashMap<String, Object> toFlatMap(ExecutionContext ctx, Map<String, Object> json, List<String> excludeList) {
        return toFlatMap(ctx, json, excludeList, true);
    }

    public static ExecutionHashMap<String, Object> toFlatMap(ExecutionContext ctx, Map<String, Object> json, List<String> excludeList, boolean pathInKey) {
        ExecutionHashMap<String, Object> map = new ExecutionHashMap<>(16, ctx);
        parseRecursive(json, map, excludeList, "", pathInKey);
        return map;
    }


    public static String encodeURI(String uri) {
        String encoded = URLEncoder.encode(uri, StandardCharsets.UTF_8);
        for (var entry : mdnEncodingReplacements.entrySet()) {
            encoded = encoded.replaceAll(entry.getKey(), entry.getValue());
        }
        return encoded;
    }

    public static String decodeURI(String uri) {
        ArrayList<String> allKeys = new ArrayList<>(mdnEncodingReplacements.keySet());
        Collections.reverse(allKeys);
        for (String strKey : allKeys) {
            uri = uri.replaceAll(mdnEncodingReplacements.get(strKey), strKey);
        }
        return URLDecoder.decode(uri, StandardCharsets.UTF_8);
    }

    public static void raiseError(String message) {
        raiseError(message, null);
    }

    public static void raiseError(String message, Object value) {
        String msg = value == null ? message : message + " for value " + value;
        throw new RuntimeException(msg);
    }

    private static void parseRecursive(Object json, Map<String, Object> map, List<String> excludeList, String path, boolean pathInKey) {
        if (json instanceof Map.Entry) {
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) json;
            if (StringUtils.isNotBlank(path)) {
                path += ".";
            }
            if (excludeList.contains(entry.getKey())) {
                return;
            }
            path += entry.getKey();
            json = entry.getValue();
        }
        if (json instanceof Set || json instanceof List) {
            String arrayPath = path + ".";
            Object[] collection = ((Collection<?>) json).toArray();
            for (int index = 0; index < collection.length; index++) {
                parseRecursive(collection[index], map, excludeList, arrayPath + index, pathInKey);
            }
        } else if (json instanceof Map) {
            Map<?, ?> node = (Map<?, ?>) json;
            for (Map.Entry<?, ?> entry : node.entrySet()) {
                parseRecursive(entry, map, excludeList, path, pathInKey);
            }
        } else {
            if (pathInKey) {
                map.put(path, json);
            } else {
                String key = path.substring(path.lastIndexOf('.') + 1);
                if (StringUtils.isNumeric(key)) {
                    int pos = path.length();
                    for (int i = 0; i < 2; i++) {
                        pos = path.lastIndexOf('.', pos - 1);
                    }
                    key = path.substring(pos + 1);
                }
                map.put(key, json);
            }
        }
    }

    private static String prepareNumberString(String value, boolean bigEndian) {
        if (StringUtils.isNotBlank(value)) {
            value = value.trim();
            value = value.replace("0x", "");
            value = value.replace("0X", "");
            value = value.replace(",", ".");
            return bigEndian ? value : reverseHexStringByOrder(value);
        }
        return null;
    }

    private static int isValidStringAndRadix(String valueP, int radix, String value) {
        int radixValue;
        if (radix == 0) {
            radixValue = getRadix10_16(valueP);
        } else if (radix >= 25 && radix <= MAX_RADIX) {
            return radix;
        } else {
            radixValue = switch (radix) {
                case MIN_RADIX -> isBinary(valueP);
                case OCTAL_RADIX -> isOctal(valueP);
                case DEC_RADIX -> isDecimal(valueP);
                case HEX_RADIX -> isHexadecimal(valueP);
                default -> throw new IllegalArgumentException("Invalid radix: [" + radix + "]");

            };
        }

        if (radixValue > 0) {
            if (value.startsWith("0x")) radixValue = HEX_RADIX;
            if (radixValue == HEX_RADIX) {
                valueP = value.startsWith("-") ? value.substring(1) : value;
                if (valueP.length() % 2 > 0) {
                    throw new NumberFormatException("The hexadecimal value: \"" + value + "\" must be of even length, or if the decimal value must be a number!");
                }
            }
            return radixValue;
        } else {
            if (radix > 0) {
                throw new NumberFormatException("Failed radix [" + radix + "] for value: \"" + value + "\", must be [" + radixValue + "] !");
            } else {
                throw new NumberFormatException("Invalid \"" + value + "\". It is not numeric or hexadecimal format!");
            }
        }
    }

    public static int isBinary(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        return str.matches("[01]+") ? MIN_RADIX : -1;
    }

    public static int isOctal(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        return str.matches("[0-7]+") ? OCTAL_RADIX : -1;
    }

    public static int isDecimal(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        return str.matches("-?\\d+(\\.\\d+)?") ? DEC_RADIX : -1;
    }

    public static int isHexadecimal(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        return str.matches("^-?(0[xX])?[0-9a-fA-F]+$") ? HEX_RADIX : -1;
    }

    private static byte isValidIntegerToByte(Integer val) {
        if (val > 255 || val < -128) {
            throw new NumberFormatException("The value '" + val + "' could not be correctly converted to a byte. " +
                    "Integer to byte conversion requires the use of only 8 bits (with a range of min/max = -128/255)!");
        } else {
            return val.byteValue();
        }
    }

    private static String reverseHexStringByOrder(String value) {
        if (value.startsWith("-")) {
            throw new IllegalArgumentException("The hexadecimal string must be without a negative sign.");
        }
        boolean isHexPref = value.startsWith("0x");
        String hex = isHexPref ? value.substring(2) : value;
        if (hex.length() % 2 > 0) {
            throw new IllegalArgumentException("The hexadecimal string must be even-length.");
        }
        // Split the hex string into bytes (2 characters each)
        StringBuilder reversedHex = new StringBuilder(BYTES_LEN_LONG_MAX);
        for (int i = hex.length() - 2; i >= 0; i -= 2) {
            reversedHex.append(hex, i, i + 2);
        }
        String result = reversedHex.toString();
        return isHexPref ? "0x" + result : result;
    }
}

