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
package org.thingsboard.server.transport.lwm2m.client;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.client.servers.LwM2mServer;
import org.eclipse.leshan.core.Destroyable;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.model.ResourceModel.Type;
import org.eclipse.leshan.core.node.LwM2mResource;
import org.eclipse.leshan.core.request.argument.Arguments;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.core.response.WriteResponse;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SimpleLwM2MDevice extends BaseInstanceEnabler implements Destroyable {


    private static final Random RANDOM = new Random();
    private static final int min = 5;
    private static final int max = 50;
    private static final PrimitiveIterator.OfInt randomIterator = new Random().ints(min, max + 1).iterator();
    private static final List<Integer> supportedResources = Arrays.asList(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21);
    /**
     * 0: DC power
     * 1: Internal Battery
     * 2: External Battery
     * 3: Fuel Cell
     * 4: Power over Ethernet
     * 5: USB
     * 6: AC (Mains) power
     * 7: Solar
     */
    private static final Map<Integer, Long> availablePowerSources =
            Map.of(0, 0L, 1, 1L, 2, 7L);
    private static Map<Integer, Long> powerSourceVoltage =
            Map.of(0, 12000L, 1, 12400L, 7, 14600L);   //mV
    private static Map<Integer, Long> powerSourceCurrent =
            Map.of(0, 72000L, 1, 2000L, 7, 25000L);    // mA

    /**
     * 0=No error
     * 1=Low battery power
     * 2=External power supply off
     * 3=GPS module failure
     * 4=Low received signal strength
     * 5=Out of memory
     * 6=SMS failure
     * 7=IP connectivity failure
     * 8=Peripheral malfunction
     * 9..15=Reserved for future use
     * 16..32=Device specific error codes
     *
     * When the single Device Object Instance is initiated, there is only one error code Resource Instance whose value is equal to 0 that means no error.
     * When the first error happens, the LwM2M Client changes error code Resource Instance to any non-zero value to indicate the error type.
     * When any other error happens, a new error code Resource Instance is created.
     * When an error associated with a Resource Instance is no longer present, that Resource Instance is deleted.
     * When the single existing error is no longer present, the LwM2M Client returns to the original no error state where Instance 0 has value 0.
     */
    private static Map<Integer, Long> errorCode =
            Map.of(0, 0L);    // 0-32

    public SimpleLwM2MDevice() {
    }

    public SimpleLwM2MDevice(ScheduledExecutorService executorService) {
        try {
            executorService.scheduleWithFixedDelay(() -> {
                        fireResourceChange(9);
                    }
                    , 1, 1, TimeUnit.SECONDS); // 2 sec
//                    , 1800000, 1800000, TimeUnit.MILLISECONDS); // 30 MIN
        } catch (Throwable e) {
            log.error("[{}]Throwable", e.toString());
            e.printStackTrace();
        }
    }


    @Override
    public ReadResponse read(LwM2mServer identity, int resourceId) {
        if (!identity.isSystem())
            log.info("Read on Device resource /{}/{}/{}", getModel().id, getId(), resourceId);
        switch (resourceId) {
            case 0:
                return ReadResponse.success(resourceId, getManufacturer());
            case 1:
                return ReadResponse.success(resourceId, getModelNumber());
            case 2:
                return ReadResponse.success(resourceId, getSerialNumber());
            case 3:
                return ReadResponse.success(resourceId, getFirmwareVersion());
            case 6:
                return ReadResponse.success(resourceId, getAvailablePowerSources(), Type.INTEGER);
            case 7:
                return ReadResponse.success(resourceId, getPowerSourceVoltage(), Type.INTEGER);
            case 8:
                return ReadResponse.success(resourceId, getPowerSourceCurrent(), Type.INTEGER);
            case 9:
                return ReadResponse.success(resourceId, getBatteryLevel());
            case 10:
                return ReadResponse.success(resourceId, getMemoryFree());
            case 11:
                return ReadResponse.success(resourceId, getErrorCodes(), Type.INTEGER);
            case 14:
                return ReadResponse.success(resourceId, getUtcOffset());
            case 15:
                return ReadResponse.success(resourceId, getTimezone());
            case 16:
                return ReadResponse.success(resourceId, getSupportedBinding());
            case 17:
                return ReadResponse.success(resourceId, getDeviceType());
            case 18:
                return ReadResponse.success(resourceId, getHardwareVersion());
            case 19:
                return ReadResponse.success(resourceId, getSoftwareVersion());
            case 20:
                return ReadResponse.success(resourceId, getBatteryStatus());
            case 21:
                return ReadResponse.success(resourceId, getMemoryTotal());
            default:
                return super.read(identity, resourceId);
        }
    }

    @Override
    public ExecuteResponse execute(LwM2mServer identity, int resourceId, Arguments arguments) {
        String withArguments = "";
        if (!arguments.isEmpty())
            withArguments = " with arguments " + arguments;
        log.info("Execute on Device resource /{}/{}/{} {}", getModel().id, getId(), resourceId, withArguments);
        return ExecuteResponse.success();
    }

    @Override
    public WriteResponse write(LwM2mServer identity, boolean replace, int resourceId, LwM2mResource value) {
        log.info("Write on Device resource /{}/{}/{}", getModel().id, getId(), resourceId);

        switch (resourceId) {
            case 13:
                return WriteResponse.notFound();
            case 14:
                setUtcOffset((String) value.getValue());
                fireResourceChange(resourceId);
                return WriteResponse.success();
            case 15:
                setTimezone((String) value.getValue());
                fireResourceChange(resourceId);
                return WriteResponse.success();
            default:
                return super.write(identity, replace, resourceId, value);
        }
    }

    private String getManufacturer() {
        return "Thingsboard Demo Lwm2mDevice";
    }

    private String getModelNumber() {
        return "Model 500";
    }

    private String getSerialNumber() {
        return "Thingsboard-500-000-0001";
    }

    private String getFirmwareVersion() {
        return "1.0.2";
    }

    private Map<Integer, ?> getAvailablePowerSources() {
        return availablePowerSources;
    }

    private Map<Integer, ?> getPowerSourceVoltage() {
        return powerSourceVoltage;
    }
    private Map<Integer, ?> getPowerSourceCurrent() {
        return powerSourceCurrent;
    }

    private Map<Integer, ?> getErrorCodes() {
        return errorCode;
    }

    private int getBatteryLevel() {
        int valBattery = randomIterator.nextInt();
        log.trace("Send from client [3/0/9] val: [{}]", valBattery);
        return valBattery;
    }

    private long getMemoryFree() {
        return Runtime.getRuntime().freeMemory() / 1024;
    }

    private String utcOffset = new SimpleDateFormat("X").format(Calendar.getInstance().getTime());

    private String getUtcOffset() {
        return utcOffset;
    }

    private void setUtcOffset(String t) {
        utcOffset = t;
    }

    private String timeZone = TimeZone.getDefault().getID();

    private String getTimezone() {
        return timeZone;
    }

    private void setTimezone(String t) {
        timeZone = t;
    }

    private String getSupportedBinding() {
        return "U";
    }

    private String getDeviceType() {
        return "Demo";
    }

    private String getHardwareVersion() {
        return "1.0.1";
    }

    private String getSoftwareVersion() {
        return "1.0.2";
    }

    private int getBatteryStatus() {
        return RANDOM.nextInt(7);
    }

    private long getMemoryTotal() {
        return Runtime.getRuntime().totalMemory() / 1024;
    }

    @Override
    public List<Integer> getAvailableResourceIds(ObjectModel model) {
        return supportedResources;
    }

    @Override
    public void destroy() {
    }
}
