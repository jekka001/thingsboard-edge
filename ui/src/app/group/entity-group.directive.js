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

import entityGroupFieldsetTemplate from './entity-group-fieldset.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export default function EntityGroupDirective($compile, $templateCache, toast, $translate, securityTypes, userPermissionsService) {
    var linker = function (scope, element) {
        var template = $templateCache.get(entityGroupFieldsetTemplate);
        element.html(template);

        scope.isPublic = false;

        scope.$watch('entityGroup', function(newVal) {
            if (newVal) {
                if (scope.entityGroup.id) {
                    var isPublicGroupType = securityTypes.publicGroupTypes[scope.entityGroup.type];
                    var isPublic = scope.entityGroup.additionalInfo && scope.entityGroup.additionalInfo.isPublic;
                    var isOwned = userPermissionsService.isDirectlyOwnedGroup(scope.entityGroup);
                    var isWriteAllowed = userPermissionsService.hasEntityGroupPermission(securityTypes.operation.write, scope.entityGroup);
                    scope.isPublic = isPublic;
                    scope.makePublicEnabled = isPublicGroupType && !isPublic && isOwned && isWriteAllowed;
                    scope.makePrivateEnabled = isPublicGroupType && isPublic && isOwned && isWriteAllowed;
                } else {
                    scope.isPublic = false;
                    scope.makePublicEnabled = false;
                    scope.makePrivateEnabled = false;
                }
            }
        });

        scope.onDeviceGroupIdCopied = function () {
            toast.showSuccess($translate.instant('entity-group.idCopiedMessage'), 750, angular.element(element).closest("md-tab-content"), 'bottom left');
        };

        $compile(element.contents())(scope);
    };
    return {
        restrict: "E",
        link: linker,
        scope: {
            entityGroup: '=',
            isEdit: '=',
            theForm: '=',
            onDeleteEntityGroup: '&',
            onMakePublic: '&',
            onMakePrivate: '&'
        }
    };
}
