/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
package org.thingsboard.server.transport.lwm2m.rpc.sql;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.eclipse.leshan.core.ResponseCode;
import org.eclipse.leshan.core.node.LwM2mPath;
import org.junit.Test;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.transport.lwm2m.rpc.AbstractRpcLwM2MIntegrationTest;

import static org.eclipse.leshan.core.LwM2mId.ACCESS_CONTROL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.OBJECT_INSTANCE_ID_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_14;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_3;
import static org.thingsboard.server.transport.lwm2m.utils.LwM2MTransportUtil.fromVersionedIdToObjectId;

public class RpcLwm2mIntegrationObserveTest extends AbstractRpcLwM2MIntegrationTest {

    /**
     * ObserveReadAll
     * @throws Exception
     */
    @Test
    public void testObserveReadAllNothingObservation_Result_CONTENT_Value_Count_0() throws Exception {
        String idVer_3_0_0 = objectInstanceIdVer_3 + "/" + RESOURCE_ID_0;
        sendObserve("Observe", fromVersionedIdToObjectId(idVer_3_0_0));
        String actualResultBefore = sendObserve("ObserveReadAll", null);
        ObjectNode rpcActualResultBefore = JacksonUtil.fromString(actualResultBefore, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResultBefore.get("result").asText());
        assertTrue(rpcActualResultBefore.get("value").asText().contains(fromVersionedIdToObjectId(idVer_3_0_0)));
        assertTrue(rpcActualResultBefore.get("value").asText().contains(fromVersionedIdToObjectId(idVer_3_0_9)));
        assertTrue(rpcActualResultBefore.get("value").asText().contains(fromVersionedIdToObjectId(idVer_19_0_0)));
        String actualResult = sendObserve("ObserveCancelAll", null);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResult.get("result").asText());
        assertEquals("3", rpcActualResult.get("value").asText());
        String actualResultAfter = sendObserve("ObserveReadAll", null);
        ObjectNode rpcActualResultAfter = JacksonUtil.fromString(actualResultAfter, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResultAfter.get("result").asText());
        String expectResultAfter = "[]";
        assertEquals( expectResultAfter, rpcActualResultAfter.get("value").asText());
    }

    /**
     * Observe {"id":"/3/0/0"}
     * @throws Exception
     */
    @Test
    public void testObserveSingleResourceWithout_IdVer_1_0_Result_CONTENT_Value_SingleResource() throws Exception {
        String expectedId = objectInstanceIdVer_3 + "/" + RESOURCE_ID_0;
        String actualResult = sendObserve("Observe", fromVersionedIdToObjectId(expectedId));
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResult.get("result").asText());
        assertTrue(rpcActualResult.get("value").asText().contains("LwM2mSingleResource"));
    }
    /**
     * Observe {"id":"/3_1.0/0/14"}
     * @throws Exception
     */
    @Test
    public void testObserveSingleResourceWith_IdVer_1_0_Result_CONTENT_Value_SingleResource() throws Exception {
        String expectedId = objectInstanceIdVer_3 + "/" + RESOURCE_ID_14;
        String actualResult = sendObserve("Observe", expectedId);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResult.get("result").asText());
        assertTrue(rpcActualResult.get("value").asText().contains("LwM2mSingleResource"));
    }

    /**
     * Observe {"id":"/3_1.1/0/13"}
     * @throws Exception
     */
    @Test
    public void testObserveWithBadVersion_Result_BadRequest_ErrorMsg_BadVersionMustBe1_0() throws Exception {
        String expectedInstance = (String) expectedInstances.stream().filter(path -> !((String)path).contains("_")).findFirst().get();
        LwM2mPath expectedPath = new LwM2mPath(expectedInstance);
        int expectedResource = lwM2MTestClient.getLeshanClient().getObjectTree().getObjectEnablers().get(expectedPath.getObjectId()).getObjectModel().resources.entrySet().stream().findAny().get().getKey();
        String expectedId = "/" + expectedPath.getObjectId() + "_1.2" + "/" + expectedPath.getObjectInstanceId() + "/" + expectedResource;
        String actualResult = sendObserve("Observe", expectedId);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.BAD_REQUEST.getName(), rpcActualResult.get("result").asText());
        String expected = "Specified resource id " + expectedId +" is not valid version! Must be version: 1.0";
        assertEquals(expected, rpcActualResult.get("error").asText());
    }

    /**
     * Not implemented Instance
     * Observe {"id":"/2/0"}
     * @throws Exception
     */
    @Test
    public void testObserveNoImplementedInstanceOnDevice_Result_NotFound() throws Exception {
        String objectInstanceIdVer = (String) expectedObjectIdVers.stream().filter(path -> ((String)path).contains("/" + ACCESS_CONTROL)).findFirst().get();
        String expected = objectInstanceIdVer + "/" + OBJECT_INSTANCE_ID_0;
        String actualResult = sendObserve("Observe", expected);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.NOT_FOUND.getName(), rpcActualResult.get("result").asText());
    }

    /**
     * Not implemented Resource
     * Observe {"id":"/19_1.1/0/0"}
     * @throws Exception
     */
    @Test
    public void testObserveNoImplementedResourceOnDeviceValueNull_Result_BadRequest() throws Exception {
        String expected = objectIdVer_19 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_3;
        String actualResult = sendObserve("Observe", expected);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        String expectedValue = "value MUST NOT be null";
        assertEquals(ResponseCode.BAD_REQUEST.getName(), rpcActualResult.get("result").asText());
        assertEquals(expectedValue, rpcActualResult.get("error").asText());
    }

    /**
     * Repeated request on Observe
     * Observe {"id":"/5/0/0"}
     * @throws Exception
     */
    @Test
    public void testObserveRSourceNotRead_Result_METHOD_NOT_ALLOWED() throws Exception {
        String expectedId = objectInstanceIdVer_5 + "/" + RESOURCE_ID_0;
        sendObserve("Observe", expectedId);
        String actualResult = sendObserve("Observe", expectedId);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.METHOD_NOT_ALLOWED.getName(), rpcActualResult.get("result").asText());
    }

    /**
     * Repeated request on Observe
     * Observe {"id":"/3/0/9"}
     * @throws Exception
     */
    @Test
    public void testObserveRepeatedRequestObserveOnDevice_Result_BAD_REQUEST_ErrorMsg_AlreadyRegistered() throws Exception {
        String idVer_3_0_0 = objectInstanceIdVer_3 + "/" + RESOURCE_ID_0;
        sendObserve("Observe", fromVersionedIdToObjectId(idVer_3_0_0));
        String actualResult = sendObserve("Observe", idVer_3_0_9);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.BAD_REQUEST.getName(), rpcActualResult.get("result").asText());
        String expected = "Observation is already registered!";
        assertEquals(expected, rpcActualResult.get("error").asText());
    }

    /**
     * ObserveReadAll
     * @throws Exception
     */
    @Test
    public void testObserveReadAll_Result_CONTENT_Value_Contains_Paths_Count_ObserveReadAll() throws Exception {
        String actualResultCancel = sendObserve("ObserveCancelAll", null);
        ObjectNode rpcActualResultCancel = JacksonUtil.fromString(actualResultCancel, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResultCancel.get("result").asText());
        sendObserve("Observe",idVer_19_0_0);
        sendObserve("Observe", idVer_3_0_9);
        String actualResult = sendObserve("ObserveReadAll", null);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResult.get("result").asText());
        String actualValues = rpcActualResult.get("value").asText();
        assertTrue(actualValues.contains(fromVersionedIdToObjectId(idVer_19_0_0)));
        assertTrue(actualValues.contains(fromVersionedIdToObjectId(idVer_3_0_9)));
        assertEquals(2, actualValues.split(",").length);
    }


    /**
     * ObserveCancel {"id":"/3/0/3"}
     * ObserveCancel {"id":"/5/0/3"}
     */
    @Test
    public void testObserveCancelOneResource_Result_CONTENT_Value_Count_1() throws Exception {
        sendObserve("ObserveCancelAll", null);
        String expectedId_3_0_3 = objectInstanceIdVer_3 + "/" + RESOURCE_ID_3;
        String expectedId_5_0_3 = objectInstanceIdVer_5 + "/" + RESOURCE_ID_3;
        sendObserve("Observe", expectedId_3_0_3);
        sendObserve("Observe", expectedId_5_0_3);
        String actualResult = sendObserve("ObserveCancel", expectedId_3_0_3);
        ObjectNode rpcActualResult = JacksonUtil.fromString(actualResult, ObjectNode.class);
        assertEquals(ResponseCode.CONTENT.getName(), rpcActualResult.get("result").asText());
        assertEquals("1", rpcActualResult.get("value").asText());
    }

    private String sendObserve(String method, String params) throws Exception {
        String sendRpcRequest;
        if (params == null) {
            sendRpcRequest = "{\"method\": \"" + method + "\"}";
        }
        else {
            sendRpcRequest = "{\"method\": \"" + method + "\", \"params\": {\"id\": \"" + params + "\"}}";
        }
        return doPostAsync("/api/plugins/rpc/twoway/" + deviceId, sendRpcRequest, String.class, status().isOk());
    }
}
