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
/* eslint-disable import/no-unresolved, import/default */

import attributeKeyValueTableTemplate from './attribute-key-value-table.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

import './attribute-key-value-table.scss';

/*@ngInject*/
export default function AttributeKeyValueTableDirective($compile, $templateCache, $mdUtil) {

    var linker = function (scope, element, attrs, ngModelCtrl) {
        var template = $templateCache.get(attributeKeyValueTableTemplate);
        element.html(template);

        scope.removeKeyVal = removeKeyVal;
        scope.addKeyVal = addKeyVal;

        ngModelCtrl.$render = function () {
            stopWatchKvList();
            var keyValMap = ngModelCtrl.$viewValue;
            scope.kvList = [];
            if (keyValMap) {
                for (var property in keyValMap) {
                    if (Object.prototype.hasOwnProperty.call(keyValMap, property)) {
                        scope.kvList.push(
                            {
                                key: property + '',
                                value: keyValMap[property]
                            }
                        );
                    }
                }
            }
            updateValidity();
            $mdUtil.nextTick(() => {
                watchKvList();
            });
        };

        function watchKvList() {
            scope.kvListWatcher = scope.$watch('kvList', (newVal, prevVal) => {
                if (!angular.equals(newVal, prevVal)) {
                    var keyValMap = {};
                    for (var i = 0; i < scope.kvList.length; i++) {
                        var entry = scope.kvList[i];
                        keyValMap[entry.key] = entry.value;
                    }
                    ngModelCtrl.$setViewValue(keyValMap);
                    updateValidity();
                }
            }, true);
        }

        function stopWatchKvList() {
            if (scope.kvListWatcher) {
                scope.kvListWatcher();
                scope.kvListWatcher = null;
            }
        }

        function removeKeyVal(index) {
            if (index > -1) {
                scope.kvList.splice(index, 1);
            }
        }

        function addKeyVal() {
            if (!scope.kvList) {
                scope.kvList = [];
            }
            scope.kvList.push(
                {
                    key: '',
                    value: ''
                }
            );
        }

        scope.$watch('required', () => {
            updateValidity();
        });

        function updateValidity() {
            var valid = true;
            if (scope.required && !scope.kvList.length) {
                valid = false;
            }
            ngModelCtrl.$setValidity('kvList', valid);
        }

        $compile(element.contents())(scope);
    };

    return {
        restrict: "E",
        require: "^ngModel",
        scope: {
            required: "=ngRequired",
            requiredPrompt: '@?',
            disabled: "=ngDisabled",
            titleText: '@?'
        },
        link: linker
    };
}
