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

import { Browser } from 'playwright-core';
import { TbWebReportPage } from './tbWebReportPage';
import { _logger } from '../../config/logger';
import { GenerateReportRequest, RequestState } from './tbWebReportModels';

const logger = _logger('TbWebReportPageQueue');

export class TbWebReportPageQueue {

    private pages: Array<TbWebReportPage> = [];

    constructor(private browser: Browser,
                private maxPageCount: number,
                private useNewPage: boolean) {
    }

    async init(): Promise<void> {
        logger.info('Initializing pages queue with size: %s', this.maxPageCount);
        for (let i = 0; i < this.maxPageCount; i++) {
            await this.createdPages(i + 1);
        }
        logger.info('Pages queue initialized.');
    }

    async destroy(): Promise<void> {
        logger.info('Closing web browser...');
        await this.browser.close();
        logger.info('Web browser closed.');
    }

    async generateDashboardReport(requestState: RequestState, request: GenerateReportRequest): Promise<Buffer> {
        const page = this.pages.pop();
        if (page) {
            logger.debug('Acquired (pop) from page pool with first try: %s, request dashboardId %s, pages pool left %s', page.id, request.dashboardId, this.pages.length);
            return await this.doGenerateDashboardReport(page, request);
        } else {
            logger.debug('Page pool is empty. Set a waiting interval 100ms to acquire the page until timeout or closed: %s', request.dashboardId);
            return new Promise<Buffer>(
                (resolve, reject) => {
                    const waitInterval = setInterval(() => {
                        if (requestState.closed || requestState.timeout) {
                            clearInterval(waitInterval);
                            logger.silly('Cleared wait interval: request dashboardId %s', request.dashboardId);
                            reject(requestState.closed ? 'Request cancelled!' : 'Generate report timeout!');
                        } else {
                            const page = this.pages.pop();
                            if (page) {
                                clearInterval(waitInterval);
                                logger.debug('Acquired (pop) from page pool after awaiting: [%s], request dashboardId %s, pages pool left %s', page.id, request.dashboardId, this.pages.length);
                                logger.silly('Cleared wait interval: request dashboardId %s', request.dashboardId);
                                this.doGenerateDashboardReport(page, request).then(
                                    (buffer) => {
                                        resolve(buffer);
                                    },
                                    (e) => {
                                        reject(e);
                                    }
                                );
                            } else {
                                logger.silly('Page pool is still empty, awaiting in waitInterval: %s , request dashboardId %s', waitInterval, request.dashboardId);
                            }
                        }
                    }, 100);
                }
            )
        }
    }

    private async createdPages(index: number) {
        const page = new TbWebReportPage(this.browser, index);
        await page.init();
        this.pages.push(page);
    }

    private async doGenerateDashboardReport(page: TbWebReportPage, request: GenerateReportRequest): Promise<Buffer> {
        try {
            return await page.generateDashboardReport(request);
        } finally {
            if (this.useNewPage || page.hasAnyFailureOccurred) {
                try {
                    await page.destroy();
                } catch (e) {
                    logger.error('Failed to destroy page %s, dashboardId %s', page, request.dashboardId);
                }
                await this.createdPages(page.id);
            } else {
                this.pages.push(page);
            }

            if (!this.browser.isConnected()) {
                logger.error('The Browser is not connected. Exiting! Page %s, dashboardId %s', page, request.dashboardId);
                process.exit(-1);
            }
        }
    }
}
