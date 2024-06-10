///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import express from 'express';
import bodyParser from 'body-parser';
import config from 'config';
import { _logger } from './config/logger';
import { chromium } from 'playwright-chromium';
import { Browser, LaunchOptions } from 'playwright-core';
import { route } from './api/routes/tbWebReportRoutes';
import { TbWebReportPageQueue } from './api/controllers/tbWebReportPageQueue';

const logger = _logger('main');
const app = express();

app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());

const address: string = config.get('server.address');
const port = Number(config.get('server.port'));
const maxPages = Number(config.get('browser.maxPages'));
const useNewPage = Boolean(config.get('browser.useNewPage'));

logger.info('Bind address: %s', address);
logger.info('Bind port: %s', port);

let browser: Browser;
let pagesQueue: TbWebReportPageQueue;

(async() => {
    try {
        logger.info('Starting ThingsBoard Web Report Microservice...');

        const browserOptions: LaunchOptions  = {
            headless: true,
            handleSIGHUP: false,
            handleSIGINT: false,
            handleSIGTERM: false,
            args: ['--no-sandbox'],
            timeout: Number(config.get('browser.launchTimeout')),
            channel: 'chrome'
        };
        if (typeof process.env.CHROME_EXECUTABLE !== 'undefined') {
            logger.info('Chrome headless browser executable: %s', process.env.CHROME_EXECUTABLE);
            browserOptions.executablePath = process.env.CHROME_EXECUTABLE;
        }
        browser = await chromium.launch(browserOptions);

        logger.info('Started chrome headless browser.');

        const ver = browser.version();

        logger.info('Headless chrome browser version: %s', ver);

        pagesQueue = new TbWebReportPageQueue(browser, maxPages, useNewPage);
        await pagesQueue.init();

        // @ts-ignore
        route(app, pagesQueue); //register the route
        app.use((req, res) => {
            res.statusMessage = req.originalUrl + ' not found';
            res.status(404).end();
        });
        app.listen(port, address, () => {
            logger.info('==> 🌎  ThingsBoard Web Report Service listening on http://%s:%s/.', address, port);
            logger.info('Started ThingsBoard Web Report Microservice.');
        }).on('error', async (error) => {
            logger.error('Failed to start ThingsBoard Web Report Microservice: %s', error.message);
            logger.error(error);
            await exit(-1);
        });
    } catch (e: any) {
        logger.error('Failed to start ThingsBoard Web Report Microservice: %s', e.message);
        logger.error(e);
        await exit(-1);
    }
})();

[`SIGINT`, `SIGUSR1`, `SIGUSR2`, `uncaughtException`, `SIGTERM`].forEach((eventType) => {
    process.once(eventType, async () => {
        logger.info(`${eventType} signal received`);
        await exit(0);
    });
})

process.on('exit', async (code: number) => {
    logger.info(`ThingsBoard Web Reporting Microservice has been stopped. Exit code: ${code}.`);
});

async function exit(status: number) {
    logger.info('Exiting with status: %d ...', status);
    if (pagesQueue) {
        await pagesQueue.destroy();
    }
    if (browser) {
        logger.info('Closing browser...');
        try {
            await browser.close();
        } catch (e) {
            logger.error(e);
        }
        logger.info('Browser closed.');
    }
    process.exit(status);
}
