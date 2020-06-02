/*
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
'use strict';

var config = require('config');
var logger = require('../../config/logger')('ReportController');

const defaultPageNavigationTimeout = Number(config.get('browser.defaultPageNavigationTimeout'));
const dashboardLoadWaitTime = Number(config.get('browser.dashboardLoadWaitTime'));

exports.genDashboardReport = function(req, res, browser) {
    var body = req.body;
    if (body.baseUrl && body.dashboardId) {
        var baseUrl = body.baseUrl;
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        var dashboardUrl = baseUrl;
        dashboardUrl += "dashboard/"+body.dashboardId;
        var url = dashboardUrl;
        var token = body.token;
        var name = body.name;
        var timewindow = null;
        var type = 'pdf'; // 'jpeg', 'png';
        var timezone = null;

        var reportParams = body.reportParams;
        if (reportParams) {
            if (reportParams.type && reportParams.type.length) {
                type = reportParams.type;
            }
            var urlParams = [];
            urlParams.push("reportView=true");
            urlParams.push("accessToken="+token);

            if (reportParams.state && reportParams.state.length) {
                urlParams.push("state="+reportParams.state);
            }
            if (reportParams.publicId && reportParams.publicId.length) {
                urlParams.push("publicId="+reportParams.publicId);
            }
            if (reportParams.timewindow) {
                timewindow = reportParams.timewindow;
                var timewindowStr = JSON.stringify(timewindow);
                urlParams.push("reportTimewindow="+encodeURIComponent(timewindowStr));
            }
            if (typeof reportParams.timezone === 'string') {
                timezone = reportParams.timezone;
            }

            if (urlParams.length) {
                url += "?" + urlParams.join('&');
            }
        }

        var contentType, ext;
        if (type === 'pdf') {
            contentType = 'application/pdf';
            ext = '.pdf';
        } else if (type === 'jpeg') {
            contentType = 'image/jpeg';
            ext = '.jpg';
        } else if (type === 'png') {
            contentType = 'image/png';
            ext = '.png';
        } else {
            res.statusMessage = 'Unsupported report type format: ' + type;
            res.status(400).end();
            return;
        }
        logger.info('Generating dashboard report: %s', dashboardUrl);
        generateDashboardReport(browser, url, type, timezone).then(
            (reportBuffer) => {
                res.attachment(name + ext);
                res.contentType(contentType);
                res.send(reportBuffer);
                logger.info('Report data sent.');
            },
            (e) => {
                logger.error(e);
                res.statusMessage = 'Failed to load dashboard page: ' + e;
                res.status(500).end();
            }
        );
    } else {
        res.statusMessage = 'Base url or Dashboard Id parameters are missing';
        res.status(400).end()
    }
};

async function generateDashboardReport(browser, url, type, timezone) {
    var page = await browser.newPage();
    page.setDefaultNavigationTimeout(defaultPageNavigationTimeout);
    //page.on('console', msg => logger.info('PAGE LOG: %s', msg.text()));
    try {
        if (timezone) {
            await page.emulateTimezone(timezone);
        }
        await page.setViewport({
            width: 1920,
            height: 1080,
            deviceScaleFactor: 1,
            isMobile: false,
            isLandscape: false
        });

        await page.emulateMedia('screen');

        const dashboardLoadResponse = await page.goto(url, {waitUntil: 'networkidle2'});
        if (dashboardLoadResponse._status < 400) {
            await page.waitFor(dashboardLoadWaitTime);
        } else {
            throw new Error("Dashboard page load returned error status: " + dashboardLoadResponse._status);
        }

        var toEval = "var height = 0;\n" +
            "     var gridsterChild = document.getElementById('gridster-child');\n" +
            "     if (gridsterChild) {\n" +
            "         height = Number(document.getElementById('gridster-child').offsetHeight);\n" +
            "         var dashboardTitleElements = document.getElementsByClassName(\"tb-dashboard-title\");\n" +
            "         if (dashboardTitleElements && dashboardTitleElements.length) {\n" +
            "              height += Number(dashboardTitleElements[0].offsetHeight);\n" +
            "         }\n" +
            "     }\n" +
            "     Math.round(height);";

        const fullHeight = await page.evaluate(toEval);

        await page.setViewport({
            width: 1920,
            height: fullHeight || 1080,
            deviceScaleFactor: 1,
            isMobile: false,
            isLandscape: false
        });
        var buffer;
        if (type === 'pdf') {
            buffer = await page.pdf({printBackground: true, width: '1920px', height: fullHeight + 'px'});
        } else {
            var options = {omitBackground: false, fullPage: true, type: type};
            if (type === 'jpeg') {
                options.quality = 100;
            }
            buffer = await page.screenshot(options);
        }
    } finally {
        await page.close();
    }
    return buffer;
}
