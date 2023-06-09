/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mvel2.ExecutionContext;
import org.mvel2.ParserContext;
import org.mvel2.SandboxedParserConfiguration;
import org.mvel2.execution.ExecutionArrayList;
import org.mvel2.execution.ExecutionHashMap;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

@Slf4j

public class TbUtilsTest {

    private ExecutionContext ctx;

    @Before
    public void before() {
        SandboxedParserConfiguration parserConfig = ParserContext.enableSandboxedMode();
        parserConfig.addImport("JSON", TbJson.class);
        parserConfig.registerDataType("Date", TbDate.class, date -> 8L);
        parserConfig.registerDataType("Random", Random.class, date -> 8L);
        parserConfig.registerDataType("Calendar", Calendar.class, date -> 8L);
        try {
            TbUtils.register(parserConfig);
        } catch (Exception e) {
            log.error("Cannot register functions", e);
        }
        ctx = new ExecutionContext(parserConfig);
        Assert.assertNotNull(ctx);
    }

    @After
    public void after() {
        ctx.stop();
    }

    @Test
    public void parseHexToInt() {
        Assert.assertEquals(0xAB, TbUtils.parseHexToInt("AB"));
        Assert.assertEquals(0xABBA, TbUtils.parseHexToInt("ABBA", true));
        Assert.assertEquals(0xBAAB, TbUtils.parseHexToInt("ABBA", false));
        Assert.assertEquals(0xAABBCC, TbUtils.parseHexToInt("AABBCC", true));
        Assert.assertEquals(0xAABBCC, TbUtils.parseHexToInt("CCBBAA", false));
        Assert.assertEquals(0xAABBCCDD, TbUtils.parseHexToInt("AABBCCDD", true));
        Assert.assertEquals(0xAABBCCDD, TbUtils.parseHexToInt("DDCCBBAA", false));
        Assert.assertEquals(0xDDCCBBAA, TbUtils.parseHexToInt("DDCCBBAA", true));
        Assert.assertEquals(0xDDCCBBAA, TbUtils.parseHexToInt("AABBCCDD", false));
    }

    @Test
    public void parseBytesToInt_checkPrimitives() {
        int expected = 257;
        byte[] data = ByteBuffer.allocate(4).putInt(expected).array();
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4));
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 2, 2, true));
        Assert.assertEquals(1, TbUtils.parseBytesToInt(data, 3, 1, true));

        expected = Integer.MAX_VALUE;
        data = ByteBuffer.allocate(4).putInt(expected).array();
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));

        expected = 0xAABBCCDD;
        data = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD};
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));
        data = new byte[]{(byte) 0xDD, (byte) 0xCC, (byte) 0xBB, (byte) 0xAA};
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, false));

        expected = 0xAABBCC;
        data = new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC};
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, true));
        data = new byte[]{(byte) 0xCC, (byte) 0xBB, (byte) 0xAA};
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, false));
    }

    @Test
    public void parseBytesToInt_checkLists() {
        int expected = 257;
        List<Byte> data = toList(ByteBuffer.allocate(4).putInt(expected).array());
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4));
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 2, 2, true));
        Assert.assertEquals(1, TbUtils.parseBytesToInt(data, 3, 1, true));

        expected = Integer.MAX_VALUE;
        data = toList(ByteBuffer.allocate(4).putInt(expected).array());
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));

        expected = 0xAABBCCDD;
        data = toList(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD});
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, true));
        data = toList(new byte[]{(byte) 0xDD, (byte) 0xCC, (byte) 0xBB, (byte) 0xAA});
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 4, false));

        expected = 0xAABBCC;
        data = toList(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC});
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, true));
        data = toList(new byte[]{(byte) 0xCC, (byte) 0xBB, (byte) 0xAA});
        Assert.assertEquals(expected, TbUtils.parseBytesToInt(data, 0, 3, false));
    }

    @Test
    public void toFlatMap() {
        ExecutionHashMap<String, Object> inputMap = new ExecutionHashMap<>(16, ctx);
        inputMap.put("name", "Alice");
        inputMap.put("age", 30);
        inputMap.put("devices", new ExecutionArrayList<>(List.of(
                new ExecutionHashMap<>(16, ctx) {{
                    put("id", "dev001");
                    put("type", "sensor");
                }},
                new ExecutionHashMap<>(16, ctx) {{
                    put("id", "dev002");
                    put("type", "actuator");
                }}
        ), ctx));
        inputMap.put("settings", new ExecutionHashMap<>(16, ctx) {{
            put("notifications", true);
            put("timezone", "UTC-5");
            put("params", new ExecutionHashMap<>(16, ctx) {{
                put("param1", "value1");
                put("param2", "value2");
                put("param3", new ExecutionHashMap<>(16, ctx) {{
                    put("subParam1", "value1");
                    put("subParam2", "value2");
                }});
            }});
        }});
        ExecutionArrayList<String> excludeList = new ExecutionArrayList<>(ctx);
        excludeList.addAll(List.of("age", "id", "param1", "subParam2"));

        ExecutionHashMap<String, Object> expectedMapWithPath = new ExecutionHashMap<>(16, ctx);
        expectedMapWithPath.put("name", "Alice");
        expectedMapWithPath.put("devices.0.type", "sensor");
        expectedMapWithPath.put("devices.1.type", "actuator");
        expectedMapWithPath.put("settings.notifications", true);
        expectedMapWithPath.put("settings.timezone", "UTC-5");
        expectedMapWithPath.put("settings.params.param2", "value2");
        expectedMapWithPath.put("settings.params.param3.subParam1", "value1");

        ExecutionHashMap<String, Object> actualMapWithPaths = TbUtils.toFlatMap(ctx, inputMap, excludeList, true);

        Assert.assertEquals(expectedMapWithPath, actualMapWithPaths);

        ExecutionHashMap<String, Object> expectedMapWithoutPaths = new ExecutionHashMap<>(16, ctx);
        expectedMapWithoutPaths.put("timezone", "UTC-5");
        expectedMapWithoutPaths.put("name", "Alice");
        expectedMapWithoutPaths.put("id", "dev002");
        expectedMapWithoutPaths.put("subParam2", "value2");
        expectedMapWithoutPaths.put("type", "actuator");
        expectedMapWithoutPaths.put("subParam1", "value1");
        expectedMapWithoutPaths.put("param1", "value1");
        expectedMapWithoutPaths.put("notifications", true);
        expectedMapWithoutPaths.put("age", 30);
        expectedMapWithoutPaths.put("param2", "value2");

        ExecutionHashMap<String, Object> actualMapWithoutPaths = TbUtils.toFlatMap(ctx, inputMap, false);

        Assert.assertEquals(expectedMapWithoutPaths, actualMapWithoutPaths);
    }


    private static String keyToValue(String key, String extraSymbol) {
        return key + "Value" + (extraSymbol == null ? "" : extraSymbol);
    }

    private static List<Byte> toList(byte[] data) {
        List<Byte> result = new ArrayList<>(data.length);
        for (Byte b : data) {
            result.add(b);
        }
        return result;
    }

}
