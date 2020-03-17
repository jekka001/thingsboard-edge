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

import activationLinkDialogTemplate from './activation-link.dialog.tpl.html';

/* eslint-enable import/no-unresolved, import/default */


/*@ngInject*/
export default function AddGroupUserController($scope, $mdDialog, $state, $stateParams,
                                               $document, $q, types, userService, helpLinks, entityGroupService, entityGroup) {

    var vm = this;

    vm.entityGroup = entityGroup;
    vm.helpLinks = helpLinks;
    vm.item = {};

    vm.activationMethods = [
        {
            value: 'displayActivationLink',
            name: 'user.display-activation-link'
        },
        {
            value: 'sendActivationMail',
            name: 'user.send-activation-mail'
        }
    ];

    vm.userActivationMethod = 'displayActivationLink';

    vm.add = add;
    vm.cancel = cancel;

    function cancel() {
        $mdDialog.cancel();
    }

    function add($event) {
        var sendActivationMail = false;
        if (vm.userActivationMethod == 'sendActivationMail') {
            sendActivationMail = true;
        }
        if (vm.entityGroup.ownerId.entityType === types.entityType.tenant) {
            vm.item.authority = "TENANT_ADMIN";
        } else if (vm.entityGroup.ownerId.entityType === types.entityType.customer) {
            vm.item.authority = "CUSTOMER_USER";
            vm.item.customerId = vm.entityGroup.ownerId;
        }
        var entityGroupId = !vm.entityGroup.groupAll ? vm.entityGroup.id.id : null;
        userService.saveUser(vm.item, sendActivationMail, entityGroupId).then(function success(item) {
            $scope.theForm.$setPristine();
            onAdd($event, item);
        });
    }

    function onAdd($event, user) {
        if (vm.userActivationMethod == 'displayActivationLink') {
            userService.getActivationLink(user.id.id).then(
                function success(activationLink) {
                    displayActivationLink($event, activationLink).then(
                        function() {
                            $mdDialog.hide(user);
                        }
                    );
                }
            );
        } else {
            $mdDialog.hide(user);
        }
    }

    function displayActivationLink($event, activationLink) {
        var deferred = $q.defer();
        $mdDialog.show({
            controller: 'ActivationLinkDialogController',
            controllerAs: 'vm',
            templateUrl: activationLinkDialogTemplate,
            locals: {
                activationLink: activationLink
            },
            parent: angular.element($document[0].body),
            fullscreen: true,
            multiple: true,
            targetEvent: $event
        }).then(function () {
            deferred.resolve();
        });
        return deferred.promise;
    }

}