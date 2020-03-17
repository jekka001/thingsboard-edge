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

/* import addAssetTemplate from './add-asset.tpl.html';
import assetCard from './asset-card.tpl.html';
import addAssetsToCustomerTemplate from './add-assets-to-customer.tpl.html'; */

/* eslint-enable import/no-unresolved, import/default */

/*@ngInject*/
export function AssetCardController(types) {

    var vm = this;

    vm.types = types;
/*
    vm.isAssignedToCustomer = function() {
        if (vm.item && vm.item.customerId && vm.parentCtl.assetsScope === 'tenant' &&
            vm.item.customerId.id != vm.types.id.nullUid && !vm.item.assignedCustomer.isPublic) {
            return true;
        }
        return false;
    }

    vm.isPublic = function() {
        if (vm.item && vm.item.assignedCustomer && vm.parentCtl.assetsScope === 'tenant' && vm.item.assignedCustomer.isPublic) {
            return true;
        }
        return false;
    }*/
}


/*@ngInject*/
export function AssetController(/*$rootScope, tbDialogs, userService, assetService, customerService, $state, $stateParams,
                                $document, $mdDialog, $q, $translate, types, utils, importExport*/) {

/*    var customerId = $stateParams.customerId;

    var assetActionsList = [];

    var assetGroupActionsList = [];

    var assetAddItemActionsList = [
        {
            onAction: function ($event) {
                vm.grid.addItem($event);
            },
            name: function() { return $translate.instant('action.add') },
            details: function() { return $translate.instant('asset.add-asset-text') },
            icon: "insert_drive_file"
        },
        {
            onAction: function ($event) {
                importExport.importEntities($event, types.entityType.asset).then(
                    function() {
                        vm.grid.refreshList();
                    }
                );
            },
            name: function() { return $translate.instant('action.import') },
            details: function() { return $translate.instant('asset.import') },
            icon: "file_upload"
        }
    ];

    var vm = this;

    vm.types = types;

    vm.assetGridConfig = {
        deleteItemTitleFunc: deleteAssetTitle,
        deleteItemContentFunc: deleteAssetText,
        deleteItemsTitleFunc: deleteAssetsTitle,
        deleteItemsActionTitleFunc: deleteAssetsActionTitle,
        deleteItemsContentFunc: deleteAssetsText,

        saveItemFunc: saveAsset,

        getItemTitleFunc: getAssetTitle,

        itemCardController: 'AssetCardController',
        itemCardTemplateUrl: assetCard,
        parentCtl: vm,

        actionsList: assetActionsList,
        groupActionsList: assetGroupActionsList,
        addItemActions: assetAddItemActionsList,

        onGridInited: gridInited,

        addItemTemplateUrl: addAssetTemplate,

        addItemText: function() { return $translate.instant('asset.add-asset-text') },
        noItemsText: function() { return $translate.instant('asset.no-assets-text') },
        itemDetailsText: function() { return $translate.instant('asset.asset-details') },
        isDetailsReadOnly: isCustomerUser,
        isSelectionEnabled: function () {
            return !isCustomerUser();
        }
    };

    if (angular.isDefined($stateParams.items) && $stateParams.items !== null) {
        vm.assetGridConfig.items = $stateParams.items;
    }

    if (angular.isDefined($stateParams.topIndex) && $stateParams.topIndex > 0) {
        vm.assetGridConfig.topIndex = $stateParams.topIndex;
    }

    vm.assetsScope = $state.$current.data.assetsType;

    vm.assignToCustomer = assignToCustomer;
    vm.makePublic = makePublic;
    vm.unassignFromCustomer = unassignFromCustomer;

    initController();

    function initController() {
        var fetchAssetsFunction = null;
        var deleteAssetFunction = null;
        var refreshAssetsParamsFunction = null;

        var user = userService.getCurrentUser();

        if (user.authority === 'CUSTOMER_USER') {
            vm.assetsScope = 'customer_user';
            customerId = user.customerId;
        }
        if (customerId) {
            vm.customerAssetsTitle = $translate.instant('customer.assets');
            customerService.getShortCustomerInfo(customerId).then(
                function success(info) {
                    if (info.isPublic) {
                        vm.customerAssetsTitle = $translate.instant('customer.public-assets');
                    }
                }
            );
        }

        if (vm.assetsScope === 'tenant') {
            fetchAssetsFunction = function (pageLink, assetType) {
                return assetService.getTenantAssets(pageLink, true, null, assetType);
            };
            deleteAssetFunction = function (assetId) {
                return assetService.deleteAsset(assetId);
            };
            refreshAssetsParamsFunction = function() {
                return {"topIndex": vm.topIndex};
            };

            assetActionsList.push({
                onAction: function ($event, item) {
                    makePublic($event, item);
                },
                name: function() { return $translate.instant('action.share') },
                details: function() { return $translate.instant('asset.make-public') },
                icon: "share",
                isEnabled: function(asset) {
                    return asset && (!asset.customerId || asset.customerId.id === types.id.nullUid);
                }
            });

            assetActionsList.push(
                {
                    onAction: function ($event, item) {
                        assignToCustomer($event, [ item.id.id ]);
                    },
                    name: function() { return $translate.instant('action.assign') },
                    details: function() { return $translate.instant('asset.assign-to-customer') },
                    icon: "assignment_ind",
                    isEnabled: function(asset) {
                        return asset && (!asset.customerId || asset.customerId.id === types.id.nullUid);
                    }
                }
            );

            assetActionsList.push(
                {
                    onAction: function ($event, item) {
                        unassignFromCustomer($event, item, false);
                    },
                    name: function() { return $translate.instant('action.unassign') },
                    details: function() { return $translate.instant('asset.unassign-from-customer') },
                    icon: "assignment_return",
                    isEnabled: function(asset) {
                        return asset && asset.customerId && asset.customerId.id !== types.id.nullUid && !asset.assignedCustomer.isPublic;
                    }
                }
            );

            assetActionsList.push({
                onAction: function ($event, item) {
                    unassignFromCustomer($event, item, true);
                },
                name: function() { return $translate.instant('action.make-private') },
                details: function() { return $translate.instant('asset.make-private') },
                icon: "reply",
                isEnabled: function(asset) {
                    return asset && asset.customerId && asset.customerId.id !== types.id.nullUid && asset.assignedCustomer.isPublic;
                }
            });

            assetActionsList.push(
                {
                    onAction: function ($event, item) {
                        vm.grid.deleteItem($event, item);
                    },
                    name: function() { return $translate.instant('action.delete') },
                    details: function() { return $translate.instant('asset.delete') },
                    icon: "delete"
                }
            );

            assetGroupActionsList.push(
                {
                    onAction: function ($event, items) {
                        assignAssetsToCustomer($event, items);
                    },
                    name: function() { return $translate.instant('asset.assign-assets') },
                    details: function(selectedCount) {
                        return $translate.instant('asset.assign-assets-text', {count: selectedCount}, "messageformat");
                    },
                    icon: "assignment_ind"
                }
            );

            assetGroupActionsList.push(
                {
                    onAction: function ($event) {
                        vm.grid.deleteItems($event);
                    },
                    name: function() { return $translate.instant('asset.delete-assets') },
                    details: deleteAssetsActionTitle,
                    icon: "delete"
                }
            );



        } else if (vm.assetsScope === 'customer' || vm.assetsScope === 'customer_user') {
            fetchAssetsFunction = function (pageLink, assetType) {
                return assetService.getCustomerAssets(customerId, pageLink, true, null, assetType);
            };
            deleteAssetFunction = function (assetId) {
                return $q.when();//assetService.unassignAssetFromCustomer(assetId);
            };
            refreshAssetsParamsFunction = function () {
                return {"customerId": customerId, "topIndex": vm.topIndex};
            };

            if (vm.assetsScope === 'customer') {
                assetActionsList.push(
                    {
                        onAction: function ($event, item) {
                            unassignFromCustomer($event, item, false);
                        },
                        name: function() { return $translate.instant('action.unassign') },
                        details: function() { return $translate.instant('asset.unassign-from-customer') },
                        icon: "assignment_return",
                        isEnabled: function(asset) {
                            return asset && !asset.assignedCustomer.isPublic;
                        }
                    }
                );
                assetActionsList.push(
                    {
                        onAction: function ($event, item) {
                            unassignFromCustomer($event, item, true);
                        },
                        name: function() { return $translate.instant('action.make-private') },
                        details: function() { return $translate.instant('asset.make-private') },
                        icon: "reply",
                        isEnabled: function(asset) {
                            return asset && asset.assignedCustomer.isPublic;
                        }
                    }
                );

                assetGroupActionsList.push(
                    {
                        onAction: function ($event, items) {
                            unassignAssetsFromCustomer($event, items);
                        },
                        name: function() { return $translate.instant('asset.unassign-assets') },
                        details: function(selectedCount) {
                            return $translate.instant('asset.unassign-assets-action-title', {count: selectedCount}, "messageformat");
                        },
                        icon: "assignment_return"
                    }
                );

                vm.assetGridConfig.addItemAction = {
                    onAction: function ($event) {
                        addAssetsToCustomer($event);
                    },
                    name: function() { return $translate.instant('asset.assign-assets') },
                    details: function() { return $translate.instant('asset.assign-new-asset') },
                    icon: "add"
                };


            } else if (vm.assetsScope === 'customer_user') {
                vm.assetGridConfig.addItemAction = {};
            }
            vm.assetGridConfig.addItemActions = [];

        }

        vm.assetGridConfig.refreshParamsFunc = refreshAssetsParamsFunction;
        vm.assetGridConfig.fetchItemsFunc = fetchAssetsFunction;
        vm.assetGridConfig.deleteItemFunc = deleteAssetFunction;

    }

    function deleteAssetTitle(asset) {
        return $translate.instant('asset.delete-asset-title', {assetName: asset.name});
    }

    function deleteAssetText() {
        return $translate.instant('asset.delete-asset-text');
    }

    function deleteAssetsTitle(selectedCount) {
        return $translate.instant('asset.delete-assets-title', {count: selectedCount}, 'messageformat');
    }

    function deleteAssetsActionTitle(selectedCount) {
        return $translate.instant('asset.delete-assets-action-title', {count: selectedCount}, 'messageformat');
    }

    function deleteAssetsText () {
        return $translate.instant('asset.delete-assets-text');
    }

    function gridInited(grid) {
        vm.grid = grid;
    }

    function getAssetTitle(asset) {
        return asset ? utils.customTranslation(asset.name, asset.name) : '';
    }

    function saveAsset(asset) {
        var deferred = $q.defer();
        assetService.saveAsset(asset).then(
            function success(savedAsset) {
                $rootScope.$broadcast('assetSaved');
                var assets = [ savedAsset ];
                customerService.applyAssignedCustomersInfo(assets).then(
                    function success(items) {
                        if (items && items.length == 1) {
                            deferred.resolve(items[0]);
                        } else {
                            deferred.reject();
                        }
                    },
                    function fail() {
                        deferred.reject();
                    }
                );
            },
            function fail() {
                deferred.reject();
            }
        );
        return deferred.promise;
    }

    function isCustomerUser() {
        return vm.assetsScope === 'customer_user';
    }

    function assignToCustomer($event, assetIds) {
        tbDialogs.assignAssetsToCustomer($event, assetIds).then(
            () => {
                vm.grid.refreshList();
            }
        );
    }

    function addAssetsToCustomer($event) {
        if ($event) {
            $event.stopPropagation();
        }
        var pageSize = 10;
        assetService.getTenantAssets({limit: pageSize, textSearch: ''}, false).then(
            function success(_assets) {
                var assets = {
                    pageSize: pageSize,
                    data: _assets.data,
                    nextPageLink: _assets.nextPageLink,
                    selections: {},
                    selectedCount: 0,
                    hasNext: _assets.hasNext,
                    pending: false
                };
                if (assets.hasNext) {
                    assets.nextPageLink.limit = pageSize;
                }
                $mdDialog.show({
                    controller: 'AddAssetsToCustomerController',
                    controllerAs: 'vm',
                    templateUrl: addAssetsToCustomerTemplate,
                    locals: {customerId: customerId, assets: assets},
                    parent: angular.element($document[0].body),
                    fullscreen: true,
                    targetEvent: $event
                }).then(function () {
                    vm.grid.refreshList();
                }, function () {
                });
            },
            function fail() {
            });
    }

    function assignAssetsToCustomer($event, items) {
        var assetIds = [];
        for (var id in items.selections) {
            assetIds.push(id);
        }
        assignToCustomer($event, assetIds);
    }

    function unassignFromCustomer($event, asset, isPublic) {
        tbDialogs.unassignAssetFromCustomer($event, asset, isPublic).then(
            () => {
                vm.grid.refreshList();
            }
        );
    }

    function unassignAssetsFromCustomer($event, items) {
       var assetIds = [];
        for (var id in items.selections) {
            assetIds.push(id);
        }
        tbDialogs.unassignAssetsFromCustomer($event, assetIds).then(
            () => {
                vm.grid.refreshList();
            }
        );
    }

    function makePublic($event, asset) {
        tbDialogs.makeAssetPublic($event, asset).then(
            () => {
                vm.grid.refreshList();
            }
        );
    }*/
}
