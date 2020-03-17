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
import './select-entity-group.scss';

/*@ngInject*/
export default function SelectEntityGroupController($rootScope, $scope, $mdDialog, entityGroupService, securityTypes,
                                                    userPermissionsService, ownerId,
                                                    targetGroupType, selectEntityGroupTitle, confirmSelectTitle, placeholderText,
                                                    notFoundText, requiredText, onEntityGroupSelected, excludeGroupIds) {

    var vm = this;

    vm.ownerId = ownerId;
    vm.targetGroupType = targetGroupType;
    vm.selectEntityGroupTitle = selectEntityGroupTitle;
    vm.confirmSelectTitle = confirmSelectTitle;
    vm.placeholderText = placeholderText;
    vm.notFoundText = notFoundText;
    vm.requiredText = requiredText;
    vm.onEntityGroupSelected = onEntityGroupSelected;
    vm.excludeGroupIds = excludeGroupIds;

    vm.addToGroupType = 0;
    vm.newEntityGroup = {
        type: targetGroupType
    };

    vm.createEnabled = userPermissionsService.hasGenericEntityGroupTypePermission(securityTypes.operation.create, vm.targetGroupType);

    vm.selectEntityGroup = selectEntityGroup;
    vm.cancel = cancel;

    function cancel() {
        $mdDialog.cancel();
    }

    function selectEntityGroup() {
        $scope.theForm.$setPristine();
        if (vm.addToGroupType === 1) {
            vm.newEntityGroup.ownerId = vm.ownerId;
            entityGroupService.saveEntityGroup(vm.newEntityGroup).then(
                (entityGroup) => {
                    groupSelected({groupId: entityGroup.id.id, group: entityGroup, isNew: true});
                    if (userPermissionsService.isDirectlyOwnedGroup(entityGroup)) {
                        $rootScope.$broadcast(targetGroupType + 'changed');
                    }
                    //TODO: update hierarchy!
                }
            );
        } else {
            groupSelected({groupId: vm.targetEntityGroupId, isNew: false});
        }
    }

    function groupSelected(selectedGroupData) {
        if (onEntityGroupSelected) {
            onEntityGroupSelected(selectedGroupData).then(
                () => {
                    $mdDialog.hide();
                }
            );
        } else {
            $mdDialog.hide(selectedGroupData);
        }
    }
}
