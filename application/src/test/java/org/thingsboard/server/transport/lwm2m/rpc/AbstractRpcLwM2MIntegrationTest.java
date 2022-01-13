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
package org.thingsboard.server.transport.lwm2m.rpc;

import org.junit.Before;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.device.credentials.lwm2m.NoSecClientCredential;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.transport.lwm2m.AbstractLwM2MIntegrationTest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.eclipse.leshan.core.LwM2mId.ACCESS_CONTROL;
import static org.eclipse.leshan.core.LwM2mId.DEVICE;
import static org.eclipse.leshan.core.LwM2mId.FIRMWARE;
import static org.eclipse.leshan.core.LwM2mId.SERVER;
import static org.eclipse.leshan.core.LwM2mId.SOFTWARE_MANAGEMENT;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.BINARY_APP_DATA_CONTAINER;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.COAP_CONFIG;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.SECURITY;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.TEMPERATURE_SENSOR;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.OBJECT_ID_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.OBJECT_INSTANCE_ID_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.OBJECT_INSTANCE_ID_1;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_NAME_19_0_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_NAME_19_1_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_NAME_3_14;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_NAME_3_9;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_0;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_14;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.RESOURCE_ID_9;
import static org.thingsboard.server.transport.lwm2m.Lwm2mTestHelper.resources;

@DaoSqlTest
public abstract class AbstractRpcLwM2MIntegrationTest extends AbstractLwM2MIntegrationTest {

    protected String RPC_TRANSPORT_CONFIGURATION;

    protected String deviceId;
    public Set expectedObjects;
    public Set expectedObjectIdVers;
    public Set expectedInstances;
    public Set expectedObjectIdVerInstances;

    protected String objectInstanceIdVer_1;
    protected String objectIdVer_0;
    protected String objectIdVer_2;
    private static final Predicate PREDICATE_3 = path -> (!((String) path).contains("/" + TEMPERATURE_SENSOR) && ((String) path).contains("/" + DEVICE));
    protected String objectIdVer_3;
    protected String objectInstanceIdVer_3;
    protected String objectInstanceIdVer_5;
    protected String objectInstanceIdVer_9;
    protected String objectIdVer_19;
    protected final String OBJECT_ID_VER_50 = "/50";
    protected String objectIdVer_3303;
    protected static AtomicInteger endpointSequence = new AtomicInteger();
    protected static String DEVICE_ENDPOINT_RPC_PREF = "deviceEndpointRpc";

    public AbstractRpcLwM2MIntegrationTest(){
        setResources(resources);
    }

    @Before
    public void beforeTest() throws Exception {
        String endpoint = DEVICE_ENDPOINT_RPC_PREF + endpointSequence.incrementAndGet();
        init();
        createNewClient (SECURITY, COAP_CONFIG, true, endpoint);

        expectedObjects = ConcurrentHashMap.newKeySet();
        expectedObjectIdVers = ConcurrentHashMap.newKeySet();
        expectedInstances = ConcurrentHashMap.newKeySet();
        expectedObjectIdVerInstances = ConcurrentHashMap.newKeySet();
        client.getClient().getObjectTree().getObjectEnablers().forEach((key, val) -> {
            if (key > 0) {
                String objectVerId = "/" + key;
                if (!val.getObjectModel().version.equals("1.0")) {
                    objectVerId += ("_" + val.getObjectModel().version);
                }
                expectedObjects.add("/" + key);
                expectedObjectIdVers.add(objectVerId);
                String finalObjectVerId = objectVerId;
                val.getAvailableInstanceIds().forEach(inststanceId -> {
                    expectedInstances.add("/" + key + "/" + inststanceId);
                    expectedObjectIdVerInstances.add(finalObjectVerId + "/" + inststanceId);
                });
            }
        });
        String ver_Id_0 = client.getClient().getObjectTree().getModel().getObjectModel(OBJECT_ID_0).version;
        if ("1.0".equals(ver_Id_0)) {
            objectIdVer_0 = "/" + OBJECT_ID_0;
        }
        else {
            objectIdVer_0 = "/" + OBJECT_ID_0 + "_" + ver_Id_0;
        }
        objectIdVer_2 = (String) expectedObjectIdVers.stream().filter(path -> ((String) path).contains("/" + ACCESS_CONTROL)).findFirst().get();
        objectIdVer_3 = (String) expectedObjects.stream().filter(PREDICATE_3).findFirst().get();
        objectIdVer_19 = (String) expectedObjectIdVers.stream().filter(path -> ((String) path).contains("/" + BINARY_APP_DATA_CONTAINER)).findFirst().get();
        objectIdVer_3303 = (String) expectedObjectIdVers.stream().filter(path -> ((String) path).contains("/" + TEMPERATURE_SENSOR)).findFirst().get();
        objectInstanceIdVer_1 = (String) expectedObjectIdVerInstances.stream().filter(path -> (!((String) path).contains("/" + BINARY_APP_DATA_CONTAINER) && ((String) path).contains("/" + SERVER))).findFirst().get();
        objectInstanceIdVer_3 = (String) expectedObjectIdVerInstances.stream().filter(PREDICATE_3).findFirst().get();
        objectInstanceIdVer_5 = (String) expectedObjectIdVerInstances.stream().filter(path -> ((String) path).contains("/" + FIRMWARE)).findFirst().get();
        objectInstanceIdVer_9 = (String) expectedObjectIdVerInstances.stream().filter(path -> ((String) path).contains("/" + SOFTWARE_MANAGEMENT)).findFirst().get();

        RPC_TRANSPORT_CONFIGURATION = "{\n" +
                "  \"type\": \"LWM2M\",\n" +
                "  \"observeAttr\": {\n" +
                "    \"keyName\": {\n" +
                "      \""  + objectIdVer_3 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_9 + "\": \"" + RESOURCE_ID_NAME_3_9 + "\",\n" +
                "      \"" + objectIdVer_3 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_14 + "\": \"" + RESOURCE_ID_NAME_3_14 + "\",\n" +
                "      \""  + objectIdVer_19 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_0 + "\": \"" + RESOURCE_ID_NAME_19_0_0 + "\",\n" +
                "      \"" + objectIdVer_19 + "/" + OBJECT_INSTANCE_ID_1 + "/" + RESOURCE_ID_0 + "\": \"" + RESOURCE_ID_NAME_19_1_0 + "\"\n" +
                "    },\n" +
                "    \"observe\": [\n" +
                "      \"" + objectIdVer_3 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_9 + "\",\n" +
                "      \""  + objectIdVer_19 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_0 + "\"\n" +
                "    ],\n" +
                "    \"attribute\": [\n" +
                "    ],\n" +
                "    \"telemetry\": [\n" +
                "      \""  + objectIdVer_3 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_9 + "\",\n" +
                "      \""  + objectIdVer_3 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_14 + "\",\n" +
                "      \""  + objectIdVer_19 + "/" + OBJECT_INSTANCE_ID_0 + "/" + RESOURCE_ID_0 + "\",\n" +
                "      \""  + objectIdVer_19 + "/" + OBJECT_INSTANCE_ID_1 + "/" + RESOURCE_ID_0 + "\"\n" +
                "    ],\n" +
                "    \"attributeLwm2m\": {}\n" +
                "  },\n" +
                "  \"bootstrapServerUpdateEnable\": true,\n" +
                "  \"bootstrap\": [\n" +
                "    {\n" +
                "       \"host\": \"0.0.0.0\",\n" +
                "       \"port\": 5687,\n" +
                "       \"binding\": \"U\",\n" +
                "       \"lifetime\": 300,\n" +
                "       \"securityMode\": \"NO_SEC\",\n" +
                "       \"shortServerId\": 111,\n" +
                "       \"notifIfDisabled\": true,\n" +
                "       \"serverPublicKey\": \"\",\n" +
                "       \"defaultMinPeriod\": 1,\n" +
                "       \"bootstrapServerIs\": true,\n" +
                "       \"clientHoldOffTime\": 1,\n" +
                "       \"bootstrapServerAccountTimeout\": 0\n" +
                "    },\n" +
                "    {\n" +
                "       \"host\": \"0.0.0.0\",\n" +
                "       \"port\": 5685,\n" +
                "       \"binding\": \"U\",\n" +
                "       \"lifetime\": 300,\n" +
                "       \"securityMode\": \"NO_SEC\",\n" +
                "       \"shortServerId\": 123,\n" +
                "       \"notifIfDisabled\": true,\n" +
                "       \"serverPublicKey\": \"\",\n" +
                "       \"defaultMinPeriod\": 1,\n" +
                "       \"bootstrapServerIs\": false,\n" +
                "       \"clientHoldOffTime\": 1,\n" +
                "       \"bootstrapServerAccountTimeout\": 0\n" +
                "    }\n" +
                "  ],\n" +
                "  \"clientLwM2mSettings\": {\n" +
                "    \"edrxCycle\": null,\n" +
                "    \"powerMode\": \"DRX\",\n" +
                "    \"fwUpdateResource\": null,\n" +
                "    \"fwUpdateStrategy\": 1,\n" +
                "    \"psmActivityTimer\": null,\n" +
                "    \"swUpdateResource\": null,\n" +
                "    \"swUpdateStrategy\": 1,\n" +
                "    \"pagingTransmissionWindow\": null,\n" +
                "    \"clientOnlyObserveAfterConnect\": 1\n" +
                "  }\n" +
                "}";
        createDeviceProfile(RPC_TRANSPORT_CONFIGURATION);

        NoSecClientCredential credentials =  createNoSecClientCredentials(endpoint);
        final Device device = createDevice(credentials);
        deviceId = device.getId().getId().toString();

        client.start();
     }

    protected String pathIdVerToObjectId(String pathIdVer) {
        if (pathIdVer.contains("_")){
            String [] objVer = pathIdVer.split("/");
            objVer[1] =  objVer[1].split("_")[0];
            return String.join("/",  objVer);
        }
        return pathIdVer;
    }

}
