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
import uiRouter from 'angular-ui-router';
import ngMaterial from 'angular-material';
import ngMessages from 'angular-messages';
import thingsboardApiAdmin from '../api/admin.service';
import thingsboardConfirmOnExit from '../components/confirm-on-exit.directive';
import thingsboardToast from '../services/toast';

import AdminRoutes from './admin.routes';
import AdminController from './admin.controller';
import SecuritySettingsController from './security-settings.controller';
import WhiteLabelingController from './white-labeling.controller';
import CustomTranslationController from './custom-translation.controller';
import CustomMenuController from './custom-menu.controller';
import SelfRegistrationController from './self-registration';

export default angular.module('thingsboard.admin', [
    uiRouter,
    ngMaterial,
    ngMessages,
    thingsboardApiAdmin,
    thingsboardConfirmOnExit,
    thingsboardToast
])
    .config(AdminRoutes)
    .controller('AdminController', AdminController)
    .controller('SecuritySettingsController', SecuritySettingsController)
    .controller('WhiteLabelingController', WhiteLabelingController)
    .controller('CustomTranslationController', CustomTranslationController)
    .controller('CustomMenuController', CustomMenuController)
    .controller('SelfRegistrationController', SelfRegistrationController)
    .name;
