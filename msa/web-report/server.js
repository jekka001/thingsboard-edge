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
var express = require('express'),
  app = express(),
  bodyParser = require('body-parser'),
  puppeteer = require('puppeteer'),
  config = require('config');

var logger = require('./config/logger')('main');

app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

var routes = require('./api/routes/tbWebReportRoutes'); //importing route

var address = config.get('server.address');
var port = config.get('server.port');

logger.info('Bind address: %s', address);
logger.info('Bind port: %s', port);

var browser;

(async() => {
    try {
        logger.info('Starting chrome headless browser...');

        var browserOptions = {
            headless: true,
            ignoreHTTPSErrors: true,
            args: ['--no-sandbox'],
            timeout: Number(config.get('browser.launchTimeout'))
        };
        if (typeof process.env.CHROME_EXECUTABLE === 'string') {
            logger.info('Chrome headless browser executable: %s', process.env.CHROME_EXECUTABLE);
            browserOptions.executablePath = process.env.CHROME_EXECUTABLE;
        }
        browser = await puppeteer.launch(browserOptions);

        logger.info('Started chrome headless browser.');

        var ver = await browser.version();

        logger.info('Headless chrome browser version: %s', ver);

    } catch (e) {
        logger.error('Failed to start headless browser: %s', e.message);
        logger.error(e);
        exit(-1);
    }
    routes(app, browser); //register the route
    app.use(function(req, res) {
        res.statusMessage = req.originalUrl + ' not found';
        res.status(404).end();
    });
    app.listen(port, address, (error) => {
        if (error) {
            logger.error(error);
            exit(-1);
        } else {
            logger.info('==> 🌎  ThingsBoard Web Reporting Service listening on http://%s:%s/.', address, port);
        }
    });
})();

process.on('exit', function () {
    exit(0);
});

function exit(status) {
    logger.info('Exiting with status: %d ...', status);
    if (browser) {
        browser.close().then(
            () => {
                process.exit(status);
            }
        );
    } else {
        process.exit(status);
    }
}