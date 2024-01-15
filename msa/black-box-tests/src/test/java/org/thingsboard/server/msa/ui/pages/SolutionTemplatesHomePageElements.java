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
package org.thingsboard.server.msa.ui.pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.thingsboard.server.msa.ui.base.AbstractBasePage;

public class SolutionTemplatesHomePageElements extends AbstractBasePage {
    public SolutionTemplatesHomePageElements(WebDriver driver) {
        super(driver);
    }

    private static final String SOLUTION_TEMPLATE = "//span[text()='%s']//ancestor::tb-solution-template-card";
    private static final String SOLUTION_TEMPLATE_DETAILS_BTN = SOLUTION_TEMPLATE + "//span[text()='Details']";
    private static final String SOLUTION_TEMPLATE_INSTALL_BTN = SOLUTION_TEMPLATE + "//span[text()='Install']";
    private static final String SOLUTION_TEMPLATE_DELETE_BTN = SOLUTION_TEMPLATE + "//span[text()='Delete']";
    private static final String SOLUTION_TEMPLATE_INSTRUCTION_BTN = SOLUTION_TEMPLATE + "//button[@mat-stroked-button][not(contains(@style,'margin-right'))]";
    private final String TEMPERATURE_HUMIDITY_SENSOR = "Temperature & Humidity Sensors";
    private final String SMART_OFFICE = "Smart office";
    private final String FLEET_TRACKING = "Fleet tracking";
    private final String AIR_QUALITY_MONITORING = "Air Quality Monitoring";
    private final String WATER_METERING = "Water metering";
    private final String SMART_RETAIL = "Smart Retail";
    private final String SMART_IRRIGATION = "Smart Irrigation";
    private final String ASSISTED_LIVING = "Assisted Living";

    public WebElement waterMetering() {
        return waitUntilVisibilityOfElementLocated(String.format(SOLUTION_TEMPLATE, WATER_METERING));
    }

    public WebElement smartRetail() {
        return waitUntilVisibilityOfElementLocated(String.format(SOLUTION_TEMPLATE, SMART_RETAIL));
    }

    public WebElement temperatureHumiditySensorsDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, TEMPERATURE_HUMIDITY_SENSOR));
    }

    public WebElement smartOfficeDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, SMART_OFFICE));
    }

    public WebElement fleetTrackingDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, FLEET_TRACKING));
    }

    public WebElement airQualityMonitoringDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, AIR_QUALITY_MONITORING));
    }

    public WebElement waterMeteringDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, WATER_METERING));
    }

    public WebElement smartRetailDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, SMART_RETAIL));
    }

    public WebElement smartIrrigationDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, SMART_IRRIGATION));
    }

    public WebElement assistedLivingDetailsBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DETAILS_BTN, ASSISTED_LIVING));
    }

    public WebElement temperatureHumiditySensorsInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, TEMPERATURE_HUMIDITY_SENSOR));
    }

    public WebElement temperatureHumiditySensorsDeleteBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, TEMPERATURE_HUMIDITY_SENSOR));
    }

    public WebElement waterMeteringDeleteBtn() {
        scrollToElement(waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, WATER_METERING)));
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, WATER_METERING));
    }

    public WebElement smartOfficeDeleteBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, SMART_OFFICE));
    }

    public WebElement fleetTrackingDeleteBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, FLEET_TRACKING));
    }

    public WebElement airQualityMonitoringDeleteBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, AIR_QUALITY_MONITORING));
    }

    public WebElement smartRetailDeleteBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, SMART_RETAIL));
    }

    public WebElement smartIrrigationDeleteBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_DELETE_BTN, SMART_IRRIGATION));
    }

    public WebElement smartOfficeInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, SMART_OFFICE));
    }

    public WebElement fleetTrackingInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, FLEET_TRACKING));
    }

    public WebElement airQualityMonitoringInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, AIR_QUALITY_MONITORING));
    }

    public WebElement waterMeteringInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, WATER_METERING));
    }

    public WebElement smartRetailInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, SMART_RETAIL));
    }

    public WebElement smartIrrigationInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, SMART_IRRIGATION));
    }

    public WebElement assistedLivingInstallBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTALL_BTN, ASSISTED_LIVING));
    }

    public WebElement temperatureHumiditySensorsInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, TEMPERATURE_HUMIDITY_SENSOR));
    }

    public WebElement smartOfficeInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, SMART_OFFICE));
    }

    public WebElement fleetTrackingInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, FLEET_TRACKING));
    }

    public WebElement waterMeteringInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, WATER_METERING));
    }

    public WebElement airQualityMonitoringInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, AIR_QUALITY_MONITORING));
    }

    public WebElement smartRetailInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, SMART_RETAIL));
    }

    public WebElement smartIrrigationInstructionBtn() {
        return waitUntilElementToBeClickable(String.format(SOLUTION_TEMPLATE_INSTRUCTION_BTN, SMART_IRRIGATION));
    }
}
