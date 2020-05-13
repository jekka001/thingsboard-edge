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
import injectTapEventPlugin from 'react-tap-event-plugin';
import UrlHandler from './url.handler';

/* eslint-disable import/no-unresolved, import/default */

import mdiIconSet from '../svg/mdi.svg';

/* eslint-enable import/no-unresolved, import/default */

const PRIMARY_BACKGROUND_COLOR = "#305680";//#2856b6";//"#3f51b5";
const SECONDARY_BACKGROUND_COLOR = "#527dad";
const HUE3_COLOR = "#a7c1de";

/*@ngInject*/
export default function AppConfig($provide,
                                  $urlRouterProvider,
                                  $locationProvider,
                                  $mdIconProvider,
                                  ngMdIconServiceProvider,
                                  $mdThemingProvider,
                                  $httpProvider,
                                  $translateProvider,
                                  storeProvider) {

    injectTapEventPlugin();
    $locationProvider.html5Mode(true);
    $urlRouterProvider.otherwise(UrlHandler);
    storeProvider.setCaching(false);

    $translateProvider.useSanitizeValueStrategy(null)
                      .useMissingTranslationHandler('tbMissingTranslationHandler')
                      .addInterpolation('$translateMessageFormatInterpolation')
                      .useStaticFilesLoader({
                          files: [
                              {
                                  prefix: PUBLIC_PATH + 'locale/locale.constant-', //eslint-disable-line
                                  suffix: '.json'
                              }
                          ]
                      })
                      .registerAvailableLanguageKeys(SUPPORTED_LANGS, getLanguageAliases(SUPPORTED_LANGS)) //eslint-disable-line
                      .fallbackLanguage('en_US') // must be before determinePreferredLanguage
                      .uniformLanguageTag('java')  // must be before determinePreferredLanguage
                      .determinePreferredLanguage();

    $provide.value('$translateProvider', $translateProvider);

    $httpProvider.interceptors.push('globalInterceptor');

    $provide.decorator("$exceptionHandler", ['$delegate', '$injector', function ($delegate/*, $injector*/) {
        return function (exception, cause) {
/*            var rootScope = $injector.get("$rootScope");
            var $window = $injector.get("$window");
            var utils = $injector.get("utils");
            if (rootScope.widgetEditMode) {
                var parentScope = $window.parent.angular.element($window.frameElement).scope();
                var data = utils.parseException(exception);
                parentScope.$emit('widgetException', data);
                parentScope.$apply();
            }*/
            $delegate(exception, cause);
        };
    }]);

    $mdIconProvider.iconSet('mdi', mdiIconSet);

    ngMdIconServiceProvider
        .addShape('alpha-a-circle-outline', '<path d="M11,7H13A2,2 0 0,1 15,9V17H13V13H11V17H9V9A2,2 0 0,1 11,7M11,9V11H13V9H11M12,20A8,8 0 0,0 20,12A8,8 0 0,0 12,4A8,8 0 0,0 4,12A8,8 0 0,0 12,20M12,2A10,10 0 0,1 22,12A10,10 0 0,1 12,22A10,10 0 0,1 2,12A10,10 0 0,1 12,2Z" />');

    configureTheme();

    function blueGrayTheme() {
        var tbPrimaryPalette = $mdThemingProvider.extendPalette('blue-grey');
        var tbAccentPalette = $mdThemingProvider.extendPalette('orange', {
            'contrastDefaultColor': 'light'
        });

        $mdThemingProvider.definePalette('tb-primary', tbPrimaryPalette);
        $mdThemingProvider.definePalette('tb-accent', tbAccentPalette);

        $mdThemingProvider.theme('default')
            .primaryPalette('tb-primary')
            .accentPalette('tb-accent');

        $mdThemingProvider.theme('tb-dark')
            .primaryPalette('tb-primary')
            .accentPalette('tb-accent')
            .backgroundPalette('tb-primary')
            .dark();
    }

    function indigoTheme() {
        var tbPrimaryPalette = $mdThemingProvider.extendPalette('indigo', {
            '500': PRIMARY_BACKGROUND_COLOR,
            '600': SECONDARY_BACKGROUND_COLOR,
            'A100': HUE3_COLOR
        });

        var tbAccentPalette = $mdThemingProvider.extendPalette('deep-orange');

        $mdThemingProvider.definePalette('tb-primary', tbPrimaryPalette);
        $mdThemingProvider.definePalette('tb-accent', tbAccentPalette);

        var tbDarkPrimaryPalette = $mdThemingProvider.extendPalette('tb-primary', {
            '500': '#9fa8da'
        });

        var tbDarkPrimaryBackgroundPalette = $mdThemingProvider.extendPalette('tb-primary', {
            '800': PRIMARY_BACKGROUND_COLOR
        });

        $mdThemingProvider.definePalette('tb-dark-primary', tbDarkPrimaryPalette);
        $mdThemingProvider.definePalette('tb-dark-primary-background', tbDarkPrimaryBackgroundPalette);

        $mdThemingProvider.theme('default')
            .primaryPalette('tb-primary')
            .accentPalette('tb-accent');

        $mdThemingProvider.theme('tb-dark')
            .primaryPalette('tb-dark-primary')
            .accentPalette('tb-accent')
            .backgroundPalette('tb-dark-primary-background')
            .dark();
    }

    function peTheme() {
        var tbPrimaryPalette = $mdThemingProvider.extendPalette('teal', {
            '500': '#277865'
        });

        var tbAccentPalette = $mdThemingProvider.extendPalette('deep-orange');

        $mdThemingProvider.definePalette('tb-primary', tbPrimaryPalette);
        $mdThemingProvider.definePalette('tb-accent', tbAccentPalette);

        var tbDarkPrimaryPalette = $mdThemingProvider.extendPalette('teal', {
            '500': '#00c3b6'
        });

        var tbDarkPrimaryBackgroundPalette = $mdThemingProvider.extendPalette('teal', {
            '800': '#277865'
        });

        $mdThemingProvider.definePalette('tb-dark-primary', tbDarkPrimaryPalette);
        $mdThemingProvider.definePalette('tb-dark-primary-background', tbDarkPrimaryBackgroundPalette);

        $mdThemingProvider.theme('default')
            .primaryPalette('tb-primary')
            .accentPalette('tb-accent');

        $mdThemingProvider.theme('tb-dark')
            .primaryPalette('tb-dark-primary')
            .accentPalette('tb-accent')
            .backgroundPalette('tb-dark-primary-background')
            .dark();
    }

    function configureTheme() {
        //white-labeling
        $mdThemingProvider.generateThemesOnDemand(true);
        $provide.value('themeProvider', $mdThemingProvider);

        var theme = 'pe';

        if (theme === 'pe') {
            peTheme();
        } else if (theme === 'blueGray') {
            blueGrayTheme();
        } else {
            indigoTheme();
        }

        $mdThemingProvider.setDefaultTheme('default');
        $mdThemingProvider.alwaysWatchTheme(true);
    }

    function getLanguageAliases(supportedLangs) {
        var aliases = {};

        supportedLangs.sort().forEach(function(item, index, array) {
            if (item.length === 2) {
                aliases[item] = item;
                aliases[item + '_*'] = item;
            } else {
                var key = item.slice(0, 2);
                if (index === 0 || key !== array[index - 1].slice(0, 2)) {
                    aliases[key] = item;
                    aliases[key + '_*'] = item;
                } else {
                    aliases[item] = item;
                }
            }
        });

        return aliases;
    }
}