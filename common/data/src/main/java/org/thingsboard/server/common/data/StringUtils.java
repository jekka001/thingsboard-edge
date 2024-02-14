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
package org.thingsboard.server.common.data;

import com.google.common.base.Splitter;
import org.apache.commons.lang3.RandomStringUtils;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import static org.apache.commons.lang3.StringUtils.repeat;

public class StringUtils {

    private static final int DEFAULT_TOKEN_LENGTH = 8;

    public static final SecureRandom RANDOM = new SecureRandom();

    public static final String EMPTY = "";

    public static final int INDEX_NOT_FOUND = -1;

    public static boolean isEmpty(String source) {
        return source == null || source.isEmpty();
    }

    public static boolean isBlank(String source) {
        return source == null || source.isEmpty() || source.trim().isEmpty();
    }

    public static boolean isNotEmpty(String source) {
        return source != null && !source.isEmpty();
    }

    public static boolean isNotBlank(String source) {
        return source != null && !source.isEmpty() && !source.trim().isEmpty();
    }

    public static String notBlankOrDefault(String src, String def) {
        return isNotBlank(src) ? src : def;
    }

    public static String removeStart(final String str, final String remove) {
        if (isEmpty(str) || isEmpty(remove)) {
            return str;
        }
        if (str.startsWith(remove)) {
            return str.substring(remove.length());
        }
        return str;
    }

    public static String substringBefore(final String str, final String separator) {
        if (isEmpty(str) || separator == null) {
            return str;
        }
        if (separator.isEmpty()) {
            return EMPTY;
        }
        final int pos = str.indexOf(separator);
        if (pos == INDEX_NOT_FOUND) {
            return str;
        }
        return str.substring(0, pos);
    }

    public static String substringBetween(final String str, final String open, final String close) {
        if (str == null || open == null || close == null) {
            return null;
        }
        final int start = str.indexOf(open);
        if (start != INDEX_NOT_FOUND) {
            final int end = str.indexOf(close, start + open.length());
            if (end != INDEX_NOT_FOUND) {
                return str.substring(start + open.length(), end);
            }
        }
        return null;
    }

    public static String obfuscate(String input, int seenMargin, char obfuscationChar,
                                   int startIndexInclusive, int endIndexExclusive) {

        String part = input.substring(startIndexInclusive, endIndexExclusive);
        String obfuscatedPart;
        if (part.length() <= seenMargin * 2) {
            obfuscatedPart = repeat(obfuscationChar, part.length());
        } else {
            obfuscatedPart = part.substring(0, seenMargin)
                    + repeat(obfuscationChar, part.length() - seenMargin * 2)
                    + part.substring(part.length() - seenMargin);
        }
        return input.substring(0, startIndexInclusive) + obfuscatedPart + input.substring(endIndexExclusive);
    }

    public static String emptyIfNull(String src){
        return src != null ? src : "";
    }

    public static Iterable<String> split(String value, int maxPartSize) {
        return Splitter.fixedLength(maxPartSize).split(value);
    }

    public static boolean equalsIgnoreCase(String str1, String str2) {
        return str1 == null ? str2 == null : str1.equalsIgnoreCase(str2);
    }

    public static String join(String[] keyArray, String lwm2mSeparatorPath) {
        return org.apache.commons.lang3.StringUtils.join(keyArray, lwm2mSeparatorPath);
    }

    public static String trimToNull(String toString) {
        return org.apache.commons.lang3.StringUtils.trimToNull(toString);
    }

    public static boolean isNoneEmpty(String str) {
        return org.apache.commons.lang3.StringUtils.isNoneEmpty(str);
    }

    public static boolean endsWith(String str, String suffix) {
        return org.apache.commons.lang3.StringUtils.endsWith(str, suffix);
    }

    public static boolean hasLength(String str) {
        return org.springframework.util.StringUtils.hasLength(str);
    }

    public static boolean isNoneBlank(String... str) {
        return org.apache.commons.lang3.StringUtils.isNoneBlank(str);
    }

    public static boolean hasText(String str) {
        return org.springframework.util.StringUtils.hasText(str);
    }

    public static String defaultString(String s, String defaultValue) {
        return org.apache.commons.lang3.StringUtils.defaultString(s, defaultValue);
    }

    public static boolean isNumeric(String str) {
        return org.apache.commons.lang3.StringUtils.isNumeric(str);
    }

    public static boolean equals(String str1, String str2) {
        return org.apache.commons.lang3.StringUtils.equals(str1, str2);
    }

    public static boolean equalsAny(String string, String... otherStrings) {
        return equalsAny(string, Arrays.asList(otherStrings));
    }

    public static boolean equalsAny(String string, List<String> otherStrings) {
        for (String otherString : otherStrings) {
            if (equals(string, otherString)) {
                return true;
            }
        }
        return false;
    }

    public static boolean equalsAnyIgnoreCase(String string, String... otherStrings) {
        for (String otherString : otherStrings) {
            if (equalsIgnoreCase(string, otherString)) {
                return true;
            }
        }
        return false;
    }

    public static String substringBeforeLast(String str, String separator) {
        return org.apache.commons.lang3.StringUtils.substringBeforeLast(str, separator);
    }

    public static String substringAfterLast(String str, String sep) {
        return org.apache.commons.lang3.StringUtils.substringAfterLast(str, sep);
    }

    public static String removeEnd(String str, String suffix) {
        return org.apache.commons.lang3.StringUtils.removeEnd(str, suffix);
    }

    public static boolean containedByAny(String searchString, String... strings) {
        if (searchString == null) return false;
        for (String string : strings) {
            if (string != null && string.contains(searchString)) {
                return true;
            }
        }
        return false;
    }

    public static boolean contains(final CharSequence seq, final CharSequence searchSeq) {
        return org.apache.commons.lang3.StringUtils.contains(seq, searchSeq);
    }

    /**
     * Use this to prevent org.postgresql.util.PSQLException: ERROR: invalid byte sequence for encoding "UTF8": 0x00
     **/
    public static boolean contains0x00(final String s) {
        return s != null && s.contains("\u0000");
    }

    public static String randomNumeric(int length) {
        return RandomStringUtils.randomNumeric(length);
    }

    public static String random(int length) {
        return RandomStringUtils.random(length);
    }

    public static String random(int length, String chars) {
        return RandomStringUtils.random(length, chars);
    }

    public static String randomAlphanumeric(int count) {
        return RandomStringUtils.randomAlphanumeric(count);
    }

    public static String randomAlphabetic(int count) {
        return RandomStringUtils.randomAlphabetic(count);
    }

    public static String generateSafeToken(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(bytes);
    }

    public static String generateSafeToken() {
        return generateSafeToken(DEFAULT_TOKEN_LENGTH);
    }

    public static String truncate(String string, int maxLength) {
        return truncate(string, maxLength, n -> "...[truncated " + n + " symbols]");
    }

    public static String truncate(String string, int maxLength, Function<Integer, String> truncationMarkerFunc) {
        if (string == null || maxLength <= 0 || string.length() <= maxLength) {
            return string;
        }
        int truncatedSymbols = string.length() - maxLength;
        return string.substring(0, maxLength) + truncationMarkerFunc.apply(truncatedSymbols);
    }

    public static String toLowerCase(String string) {
        return isNotEmpty(string) ? string.toLowerCase() : string;
    }

    public static List<String> splitByCommaWithoutQuotes(String value) {
        List<String> splitValues = List.of(value.trim().split("\\s*,\\s*"));
        List<String> result = new ArrayList<>();
        char lastWayInputValue = '#';
        for (String str : splitValues) {
            char startWith = str.charAt(0);
            char endWith = str.charAt(str.length() - 1);

            // if first value is not quote, so we return values after split
            if (startWith != '\'' && startWith != '"') return splitValues;

            // if value is not in quote, so we return values after split
            if (startWith != endWith) return splitValues;

            // if different way values, so don't replace quote and return values after split
            if (lastWayInputValue != '#' && startWith != lastWayInputValue) return splitValues;

            result.add(str.substring(1, str.length() - 1));
            lastWayInputValue = startWith;
        }
        return result;
    }

}
