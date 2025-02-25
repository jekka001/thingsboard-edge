/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.thingsboard.server.transport.lwm2m.rpc.AbstractRpcLwM2MIntegrationObserve_Ver_1_0_Test;
import org.thingsboard.server.transport.lwm2m.rpc.AbstractRpcLwM2MIntegrationObserve_Ver_1_2_Test;

import static org.junit.Assert.assertTrue;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_NAME_3_9;

@Slf4j
public class RpcLwm2mIntegrationObserve_Ver_1_2_Test extends AbstractRpcLwM2MIntegrationObserve_Ver_1_2_Test {

    @Before
    public void setupObserveTest() throws Exception {
        awaitObserveReadAll(4,lwM2MTestClient.getDeviceIdStr());
    }

    /**
     * Observe "3_1.2/0/9"
     * @throws Exception
     */
    @Test
    public void testObserveOneResource_Result_CONTENT_Value_Count_3_After_Cancel_Count_2() throws Exception {
        long initSendTelemetryAtCount = countSendParametersOnThingsboardTelemetryResource(RESOURCE_ID_NAME_3_9);
        sendObserveCancelAllWithAwait(lwM2MTestClient.getDeviceIdStr());
        sendRpcObserveWithContainsLwM2mSingleResource(idVer_3_0_9);
        updateRegAtLeastOnceAfterAction();
        long lastSendTelemetryAtCount = countSendParametersOnThingsboardTelemetryResource(RESOURCE_ID_NAME_3_9);
        assertTrue(lastSendTelemetryAtCount > initSendTelemetryAtCount);
    }
}
