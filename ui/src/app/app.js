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
import './ie.support';
import './tz.support';

import 'event-source-polyfill';

import angular from 'angular';
import ngMaterial from 'angular-material';
import ngMdIcons from 'angular-material-icons';
import ngCookies from 'angular-cookies';
import angularSocialshare from 'angular-socialshare';
import 'angular-translate';
import 'angular-translate-loader-static-files';
import 'angular-translate-storage-local';
import 'angular-translate-storage-cookie';
import 'angular-translate-handler-log';
import 'angular-translate-interpolation-messageformat';
import 'md-color-picker';
import 'md-date-range-picker';
import mdPickers from 'mdPickers';
import ngSanitize from 'angular-sanitize';
import FBAngular from 'angular-fullscreen';
import vAccordion from 'v-accordion';
import 'angular-material-expansion-panel';
import ngAnimate from 'angular-animate';
import 'angular-websocket';
import uiRouter from 'angular-ui-router';
import angularJwt from 'angular-jwt';
import 'angular-recaptcha';
import 'angular-drag-and-drop-lists';
import mdDataTable from 'angular-material-data-table';
import fixedTableHeader from 'angular-fixed-table-header';
import ngTouch from 'angular-touch';
import 'tinymce/tinymce.min';
import 'tinymce/themes/modern/theme.min';
import 'tinymce/plugins/colorpicker';
import 'tinymce/plugins/textcolor';
import 'tinymce/plugins/link';
import 'tinymce/plugins/table';
import 'tinymce/plugins/image';
import 'tinymce/plugins/imagetools';
import 'tinymce/plugins/code';
import 'tinymce/plugins/fullscreen';
import 'angular-ui-tinymce';
import 'angular-carousel';
import 'clipboard';
import 'ngclipboard';
import 'react';
import 'react-dom';
import 'material-ui';
import 'react-schema-form';
import react from 'ngreact';
import '@flowjs/ng-flow/dist/ng-flow-standalone.min';
import 'ngFlowchart/dist/ngFlowchart';
import 'fullcalendar/dist/fullcalendar.min.css';
import 'fullcalendar/dist/fullcalendar.min.js';
import 'angular-ui-calendar';
import 'moment-timezone';
import 'jstree/dist/jstree.min';

//import 'jstree/dist/themes/default/style.min.css';
import 'material-steppers/dist/material-steppers';
import 'material-steppers/dist/material-steppers.css';
import 'jstree-bootstrap-theme/dist/themes/proton/style.min.css';
import 'tinymce/skins/lightgray/skin.min.css';
import 'tinymce/skins/lightgray/content.min.css';
import 'typeface-roboto';
import 'font-awesome/css/font-awesome.min.css';
import 'angular-material/angular-material.min.css';
import 'angular-material-icons/angular-material-icons.css';
import 'angular-gridster/dist/angular-gridster.min.css';
import 'v-accordion/dist/v-accordion.min.css'
import 'md-color-picker/dist/mdColorPicker.min.css';
import 'mdPickers/dist/mdPickers.min.css';
import 'angular-hotkeys/build/hotkeys.min.css';
import 'angular-carousel/dist/angular-carousel.min.css';
import 'angular-material-expansion-panel/dist/md-expansion-panel.min.css';
import 'ngFlowchart/dist/flowchart.css';
import 'md-date-range-picker/src/md-date-range-picker.css';
import '../scss/main.scss';

import thingsboardThirdpartyFix from './common/thirdparty-fix';
import thingsboardTranslateHandler from './locale/translate-handler';
import thingsboardSignUp from './signup';
import thingsboardLogin from './login';
import thingsboardDatakeyConfigDialog from './components/datakey-config-dialog.controller';
import thingsboardDialogs from './dialog';
import thingsboardMenu from './services/menu.service';
import thingsboardRaf from './common/raf.provider';
import thingsboardUtils from './common/utils.service';
import thingsboardDashboardUtils from './common/dashboard-utils.service';
import thingsboardTypes from './common/types.constant';
import thingsboardSecurityTypes from './common/security-types.constant';
import thingsboardApiTime from './api/time.service';
import thingsboardKeyboardShortcut from './components/keyboard-shortcut.filter';
import thingsboardHelp from './help/help.directive';
import thingsboardToast from './services/toast';
import thingsboardClipboard from './services/clipboard.service';
import thingsboardHome from './layout';
import thingsboardApiSignUp from './api/signup.service';
import thingsboardApiLogin from './api/login.service';
import thingsboardApiDevice from './api/device.service';
import thingsboardApiEntityView from './api/entity-view.service';
import thingsboardApiUser from './api/user.service';
import thingsboardApiEntityRelation from './api/entity-relation.service';
import thingsboardApiAsset from './api/asset.service';
import thingsboardApiAttribute from './api/attribute.service';
import thingsboardApiEntity from './api/entity.service';
import thingsboardApiAlarm from './api/alarm.service';
import thingsboardApiEntityGroup from './api/entity-group.service';
import thingsboardApiWhiteLabeling from './api/white-labeling.service';
import thingsboardApiConverter from './api/converter.service';
import thingsboardApiIntegration from './api/integration.service';
import thingsboardApiAuditLog from './api/audit-log.service';
import thingsboardApiComponentDescriptor from './api/component-descriptor.service';
import thingsboardApiRuleChain from './api/rule-chain.service';
import thingsboardApiSchedulerEvent from './api/scheduler-event.service';
import thingsboardApiReport from './api/report.service';
import thingsboardApiBlobEntity from './api/blob-entity.service';
import thingsboardApiCustomTranslation from './api/custom-translation.service';
import thingsboardApiCustomMenu from './api/custom-menu.service';
import thingsboardApiRole from './api/role.service';
import thingsboardApiUserPermissions from './api/user-permissions.service';
import thingsboardApiSelfRegistration from './api/self-register.service';

import AppConfig from './app.config';
import GlobalInterceptor from './global-interceptor.service';
import AppRun from './app.run';

angular.module('thingsboard', [
    ngMaterial,
    ngMdIcons,
    ngCookies,
    angularSocialshare,
    'pascalprecht.translate',
    'vcRecaptcha',
    'mdColorPicker',
    'ngMaterialDateRangePicker',
    mdPickers,
    ngSanitize,
    FBAngular.name,
    vAccordion,
    'material.components.expansionPanels',
    ngAnimate,
    'ngWebSocket',
    angularJwt,
    'dndLists',
    mdDataTable,
    fixedTableHeader,
    ngTouch,
    'ui.tinymce',
    'angular-carousel',
    'ngclipboard',
    react.name,
    'flow',
    'flowchart',
    'ui.calendar',
    'mdSteppers',
    thingsboardThirdpartyFix,
    thingsboardTranslateHandler,
    thingsboardSignUp,
    thingsboardLogin,
    thingsboardDatakeyConfigDialog,
    thingsboardDialogs,
    thingsboardMenu,
    thingsboardRaf,
    thingsboardUtils,
    thingsboardDashboardUtils,
    thingsboardTypes,
    thingsboardSecurityTypes,
    thingsboardApiTime,
    thingsboardKeyboardShortcut,
    thingsboardHelp,
    thingsboardToast,
    thingsboardClipboard,
    thingsboardHome,
    thingsboardApiSignUp,
    thingsboardApiLogin,
    thingsboardApiDevice,
    thingsboardApiEntityView,
    thingsboardApiUser,
    thingsboardApiEntityRelation,
    thingsboardApiAsset,
    thingsboardApiAttribute,
    thingsboardApiEntity,
    thingsboardApiAlarm,
    thingsboardApiEntityGroup,
    thingsboardApiWhiteLabeling,
    thingsboardApiConverter,
    thingsboardApiIntegration,
    thingsboardApiAuditLog,
    thingsboardApiComponentDescriptor,
    thingsboardApiRuleChain,
    thingsboardApiSchedulerEvent,
    thingsboardApiReport,
    thingsboardApiBlobEntity,
    thingsboardApiCustomTranslation,
    thingsboardApiCustomMenu,
    thingsboardApiRole,
    thingsboardApiUserPermissions,
    thingsboardApiSelfRegistration,
    uiRouter])
    .config(AppConfig)
    .factory('globalInterceptor', GlobalInterceptor)
    .run(AppRun);
