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

import defaultStateControllerTemplate from './default-state-controller.tpl.html';
import entityStateControllerTemplate from './entity-state-controller.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

import DefaultStateController from './default-state-controller';
import EntityStateController from './entity-state-controller';

/*@ngInject*/
export default function StatesControllerService() {

    var statesControllers = {};
    statesControllers['default'] = {
        controller: DefaultStateController,
        templateUrl: defaultStateControllerTemplate
    };
    statesControllers['entity'] = {
        controller: EntityStateController,
        templateUrl: entityStateControllerTemplate
    };

    var service = {
        registerStatesController: registerStatesController,
        getStateControllers: getStateControllers,
        getStateController: getStateController,
        preserveStateControllerState: preserveStateControllerState,
        withdrawStateControllerState: withdrawStateControllerState,
        cleanupPreservedStates: cleanupPreservedStates
    };

    return service;

    function registerStatesController(id, stateControllerInfo) {
        statesControllers[id] = stateControllerInfo;
    }

    function getStateControllers() {
        return statesControllers;
    }

    function getStateController(id) {
        return statesControllers[id];
    }

    function preserveStateControllerState(id, state) {
        statesControllers[id].state = angular.copy(state);
    }

    function withdrawStateControllerState(id) {
        var state = statesControllers[id].state;
        statesControllers[id].state = null;
        return state;
    }

    function cleanupPreservedStates() {
        for (var id in statesControllers) {
            statesControllers[id].state = null;
        }
    }

}
