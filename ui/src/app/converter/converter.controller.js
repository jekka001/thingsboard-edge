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

import addConverterTemplate from './add-converter.tpl.html';
import converterCard from './converter-card.tpl.html';

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export function ConverterCardController(types) {

    var vm = this;
    vm.types = types;

}

/*@ngInject*/
export function ConverterController(converterService, $state, $stateParams, $translate, importExport, securityTypes, types, helpLinks, utils, userPermissionsService) {

    var converterActionsList = [
        {
            onAction: function ($event, item) {
                exportConverter($event, item);
            },
            name: function() { $translate.instant('action.export') },
            details: function() { return $translate.instant('converter.export') },
            icon: "file_download"
        },
        {
            onAction: function ($event, item) {
                vm.grid.deleteItem($event, item);
            },
            name: function() { return $translate.instant('action.delete') },
            details: function() { return $translate.instant('converter.delete') },
            icon: "delete",
            isEnabled: function() {
                return userPermissionsService.hasGenericPermission(securityTypes.resource.converter, securityTypes.operation.delete);
            }
        }
    ];

    var converterAddItemActionsList = [
        {
            onAction: function ($event) {
                vm.grid.addItem($event);
            },
            name: function() { return $translate.instant('action.create') },
            details: function() { return $translate.instant('converter.create-new-converter') },
            icon: "insert_drive_file"
        },
        {
            onAction: function ($event) {
                importExport.importConverter($event).then(
                    function() {
                        vm.grid.refreshList();
                    }
                );
            },
            name: function() { return $translate.instant('action.import') },
            details: function() { return $translate.instant('converter.import') },
            icon: "file_upload"
        }
    ];

    var vm = this;

    vm.types = types;

    vm.helpLinkIdForConverter = helpLinkIdForConverter;

    vm.converterGridConfig = {

        resource: securityTypes.resource.converter,

        refreshParamsFunc: null,

        deleteItemTitleFunc: deleteConverterTitle,
        deleteItemContentFunc: deleteConverterText,
        deleteItemsTitleFunc: deleteConvertersTitle,
        deleteItemsActionTitleFunc: deleteConvertersActionTitle,
        deleteItemsContentFunc: deleteConvertersText,

        fetchItemsFunc: fetchConverters,
        saveItemFunc: saveConverter,
        deleteItemFunc: deleteConverter,

        getItemTitleFunc: getConverterTitle,

        itemCardController: 'ConverterCardController',
        itemCardTemplateUrl: converterCard,
        parentCtl: vm,

        actionsList: converterActionsList,
        addItemActions: converterAddItemActionsList,

        onGridInited: gridInited,

        addItemTemplateUrl: addConverterTemplate,

        addItemText: function() { return $translate.instant('converter.add-converter-text') },
        noItemsText: function() { return $translate.instant('converter.no-converters-text') },
        itemDetailsText: function() {
            return $translate.instant('converter.converter-details');
        },
        isSelectionEnabled: function () {
            return userPermissionsService.hasGenericPermission(securityTypes.resource.converter, securityTypes.operation.delete);
        },
        isDetailsReadOnly: function () {
            return !userPermissionsService.hasGenericPermission(securityTypes.resource.converter, securityTypes.operation.write);
        }
    };

    if (angular.isDefined($stateParams.items) && $stateParams.items !== null) {
        vm.converterGridConfig.items = $stateParams.items;
    }

    if (angular.isDefined($stateParams.topIndex) && $stateParams.topIndex > 0) {
        vm.converterGridConfig.topIndex = $stateParams.topIndex;
    }

    vm.exportConverter = exportConverter;

    function helpLinkIdForConverter() {
        return helpLinks.getConverterLink(vm.grid.operatingItem());
    }

    function deleteConverterTitle(converter) {
        return $translate.instant('converter.delete-converter-title', {converterName: converter.name});
    }

    function deleteConverterText() {
        return $translate.instant('converter.delete-converter-text');
    }

    function deleteConvertersTitle(selectedCount) {
        return $translate.instant('converter.delete-converters-title', {count: selectedCount}, 'messageformat');
    }

    function deleteConvertersActionTitle(selectedCount) {
        return $translate.instant('converter.delete-converters-action-title', {count: selectedCount}, 'messageformat');
    }

    function deleteConvertersText() {
        return $translate.instant('converter.delete-converters-text');
    }

    function gridInited(grid) {
        vm.grid = grid;
    }

    function fetchConverters(pageLink) {
        return converterService.getConverters(pageLink);
    }

    function saveConverter(converter) {
        return converterService.saveConverter(converter);
    }

    function deleteConverter(converterId) {
        return converterService.deleteConverter(converterId);
    }

    function getConverterTitle(converter) {
        return converter ? utils.customTranslation(converter.name, converter.name) : '';
    }

    function exportConverter($event, converter) {
        $event.stopPropagation();
        importExport.exportConverter(converter.id.id);
    }

}
