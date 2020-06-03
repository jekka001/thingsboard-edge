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
import './entity-state-controller.scss';

/*@ngInject*/
export default function EntityStateController($rootScope, $scope, $timeout, $location, $window, $state, $stateParams,
                                              $q, $translate, utils, types, dashboardUtils, entityService, preservedState) {

    var vm = this;

    vm.inited = false;

    vm.skipStateChange = false;

    vm.openState = openState;
    vm.updateState = updateState;
    vm.resetState = resetState;
    vm.getStateObject = getStateObject;
    vm.navigatePrevState = navigatePrevState;
    vm.getStateId = getStateId;
    vm.getStateIndex = getStateIndex;
    vm.getStateIdAtIndex = getStateIdAtIndex;
    vm.getStateParams = getStateParams;
    vm.getStateParamsByStateId = getStateParamsByStateId;
    vm.getEntityId = getEntityId;

    vm.getStateName = getStateName;

    vm.selectedStateIndex = -1;

    function openState(id, params, openRightLayout) {
        if (vm.states && vm.states[id]) {
            resolveEntity(params).then(
                function success() {
                    var newState = {
                        id: id,
                        params: params
                    }
                    //append new state
                    vm.stateObject.push(newState);
                    vm.selectedStateIndex = vm.stateObject.length-1;
                    gotoState(vm.stateObject[vm.stateObject.length-1].id, true, openRightLayout);
                    vm.skipStateChange = true;
                }
            );
        }
    }

    function updateState(id, params, openRightLayout) {
        if (!id) {
            id = getStateId();
        }
        if (vm.states && vm.states[id]) {
            resolveEntity(params).then(
                function success() {
                    var newState = {
                        id: id,
                        params: params
                    }
                    //replace with new state
                    vm.stateObject[vm.stateObject.length - 1] = newState;
                    gotoState(vm.stateObject[vm.stateObject.length - 1].id, true, openRightLayout);
                    vm.skipStateChange = true;
                }
            );
        }
    }

    function resetState() {
        var rootStateId = dashboardUtils.getRootStateId(vm.states);
        vm.stateObject = [ { id: rootStateId, params: {} } ];
        gotoState(rootStateId, true);
    }

    function getStateObject() {
        return vm.stateObject;
    }

    function navigatePrevState(index) {
        if (index < vm.stateObject.length-1) {
            vm.stateObject.splice(index+1, vm.stateObject.length-index-1);
            vm.selectedStateIndex = vm.stateObject.length-1;
            gotoState(vm.stateObject[vm.stateObject.length-1].id, true);
            vm.skipStateChange = true;
        }
    }

    function getStateId() {
        if (vm.stateObject && vm.stateObject.length) {
            return vm.stateObject[vm.stateObject.length-1].id;
        } else {
            return '';
        }
    }

    function getStateIndex() {
        if (vm.stateObject && vm.stateObject.length) {
            return vm.stateObject.length-1;
        } else {
            return -1;
        }
    }

    function getStateIdAtIndex(index) {
        if (vm.stateObject && vm.stateObject[index]) {
            return vm.stateObject[index].id;
        } else {
            return '';
        }
    }

    function getStateParams() {
        if (vm.stateObject && vm.stateObject.length) {
            return vm.stateObject[vm.stateObject.length - 1].params;
        } else {
            return {};
        }
    }

    function getStateParamsByStateId(stateId) {
        var stateObj = getStateObjById(stateId);
        if (stateObj) {
            return stateObj.params;
        } else {
            return null;
        }
    }

    function getEntityId(entityParamName) {
        var stateParams = getStateParams();
        if (!entityParamName || !entityParamName.length) {
            return stateParams.entityId;
        } else if (stateParams[entityParamName]) {
            return stateParams[entityParamName].entityId;
        }
        return null;
    }

    function getStateObjById(id) {
        for (var i=0; i < vm.stateObject.length; i++) {
            if (vm.stateObject[i].id === id) {
                return vm.stateObject[i];
            }
        }
        return null;
    }

    function getStateName(index) {
        let result = '';
        if (vm.stateObject[index]) {
            let stateName = vm.states[vm.stateObject[index].id].name;
            stateName = utils.customTranslation(stateName, stateName);
            var params = vm.stateObject[index].params;

            let entityName = params && params.entityName ? params.entityName : '';
            let entityLabel = params && params.entityLabel ? params.entityLabel : entityName;

            result = utils.insertVariable(stateName, 'entityName', entityName);
            result = utils.insertVariable(result, 'entityLabel', entityLabel);
            for (let prop in params) {
                if (params[prop] && params[prop].entityName) {
                    result = utils.insertVariable(result, prop + ':entityName', params[prop].entityName);
                }
                if (params[prop] && params[prop].entityLabel) {
                    result = utils.insertVariable(result, prop + ':entityLabel', params[prop].entityLabel);
                }
            }
        }
        return result;
    }

    function resolveEntity(params) {
        var deferred = $q.defer();
        if (params && params.targetEntityParamName) {
            params = params[params.targetEntityParamName];
        }
        if (params && params.entityId && params.entityId.id && params.entityId.entityType) {
            if (isEntityResolved(params)) {
                deferred.resolve();
            } else {
                entityService.getEntity(params.entityId.entityType, params.entityId.id, {
                    ignoreLoading: true,
                    ignoreErrors: true
                }).then(
                    function success(entity) {
                        params.entityName = entity.name;
                        params.entityLabel = entity.label;
                        if (params.entityId.entityType === types.entityType.entityGroup) {
                            params.entityGroupType = entity.type;
                        }
                        deferred.resolve();
                    },
                    function fail() {
                        deferred.reject();
                    }
                );
            }
        } else {
            deferred.resolve();
        }
        return deferred.promise;
    }

    function isEntityResolved(params) {
        if (params.entityId.entityType === types.entityType.entityGroup && !params.entityGroupType) {
            return false;
        }
        if (!params.entityName || !params.entityName.length) {
            return false;
        }
        return true;
    }

    function parseState(stateBase64) {
        var result;
        if (stateBase64) {
            try {
                result = utils.base64toObj(stateBase64);
            } catch (e) {
                result = [ { id: null, params: {} } ];
            }
        }
        if (!result) {
            result = [];
        }
        if (!result.length) {
            result[0] = { id: null, params: {} }
        }
        var rootStateId = dashboardUtils.getRootStateId(vm.states);
        if (!result[0].id) {
            result[0].id = rootStateId;
        }
        if (!vm.states[result[0].id]) {
            result[0].id = rootStateId;
        }
        var i = result.length;
        while (i--) {
            if (!result[i].id || !vm.states[result[i].id]) {
                result.splice(i, 1);
            }
        }
        return result;
    }

    $scope.$watch('vm.states', function() {
        if (vm.states) {
            if (!vm.inited) {
                vm.inited = true;
                init();
            }
        }
    });

    function init() {
        if (preservedState) {
            vm.stateObject = preservedState;
            vm.selectedStateIndex = vm.stateObject.length-1;
            gotoState(vm.stateObject[vm.stateObject.length-1].id, true);
        } else {
            var initialState = $stateParams.state;
            vm.stateObject = parseState(initialState);
            vm.selectedStateIndex = vm.stateObject.length-1;
            gotoState(vm.stateObject[vm.stateObject.length-1].id, false);
        }

        $timeout(() => {
            $scope.$watchCollection(function () {
                return $state.params;
            }, function () {
                var currentState = $state.params.state;
                vm.stateObject = parseState(currentState);
            });

            $scope.$watch('vm.dashboardCtrl.dashboardCtx.state', function () {
                if (vm.stateObject[vm.stateObject.length - 1].id !== vm.dashboardCtrl.dashboardCtx.state) {
                    vm.stateObject[vm.stateObject.length - 1].id = vm.dashboardCtrl.dashboardCtx.state;
                    updateLocation();
                    vm.skipStateChange = true;
                }
            });

            $scope.$watch('vm.stateObject', function(newVal, prevVal) {
                if (!angular.equals(newVal, prevVal) && newVal) {
                    if (vm.skipStateChange) {
                        vm.skipStateChange = false;
                    } else {
                        vm.selectedStateIndex = vm.stateObject.length - 1;
                        gotoState(vm.stateObject[vm.stateObject.length - 1].id, true);
                    }
                }
            }, true);

            if (vm.dashboardCtrl.isMobile) {
                watchSelectedStateIndex();
            }

            $scope.$watch('vm.dashboardCtrl.isMobile', function (newVal, prevVal) {
                if (!angular.equals(newVal, prevVal)) {
                    if (vm.dashboardCtrl.isMobile) {
                        watchSelectedStateIndex();
                    } else {
                        stopWatchSelectedStateIndex();
                    }
                }
            });
        });

    }

    function stopWatchSelectedStateIndex() {
        if (vm.selectedStateIndexWatcher) {
            vm.selectedStateIndexWatcher();
            vm.selectedStateIndexWatcher = null;
        }
    }

    function watchSelectedStateIndex() {
        vm.selectedStateIndexWatcher = $scope.$watch('vm.selectedStateIndex', function(newVal, prevVal) {
            if (!angular.equals(newVal, prevVal)) {
                navigatePrevState(vm.selectedStateIndex);
            }
        });
    }

    function gotoState(stateId, update, openRightLayout) {
        vm.dashboardCtrl.openDashboardState(stateId, openRightLayout);
        if (update) {
            updateLocation();
        }
    }

    function updateLocation() {
        if (vm.stateObject[vm.stateObject.length-1].id) {
            if (isDefaultState()) {
                $location.search('state', null);
            } else {
                $location.search('state', utils.objToBase64(vm.stateObject));
            }
            notifyStateSelected();
        }
    }

    function notifyStateSelected() {
        if ($rootScope.stateSelectView) {
            var parentScope = $window.parent.angular.element($window.frameElement).scope();
            parentScope.$root.$broadcast('dashboardStateSelected', $location.search().state);
        }
    }

    function isDefaultState() {
        if (vm.stateObject.length == 1) {
            var state = vm.stateObject[0];
            var rootStateId = dashboardUtils.getRootStateId(vm.states);
            if (state.id == rootStateId && (!state.params || isEmpty(state.params))) {
                return true;
            }
        }
        return false;
    }

    function isEmpty(map) {
        for(var key in map) {
            return !Object.prototype.hasOwnProperty.call(map, key);
        }
        return true;
    }

}
