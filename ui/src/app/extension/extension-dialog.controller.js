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
import beautify from 'js-beautify';

const js_beautify =  beautify.js;

/*@ngInject*/
export default function ExtensionDialogController($scope, $mdDialog, $translate, isAdd, readonly, allExtensions, entityId, entityType, extension, types, attributeService) {

    var vm = this;

    vm.types = types;
    vm.isAdd = isAdd;
    vm.readonly = readonly;
    vm.entityType = entityType;
    vm.entityId = entityId;
    vm.allExtensions = allExtensions;


    if (extension) {
        vm.extension = angular.copy(extension);
        editTransformers(vm.extension);
    } else {
        vm.extension = {};
    }


    vm.extensionTypeChange = function () {

        if (vm.extension.type === "HTTP") {
            vm.extension.configuration = {
                "converterConfigurations": []
            };
        }
        if (vm.extension.type === "MQTT") {
            vm.extension.configuration = {
                "brokers": []
            };
        }
        if (vm.extension.type === "OPC UA" || vm.extension.type === "MODBUS") {
            vm.extension.configuration = {
                "servers": []
            };
        }
    };

    vm.cancel = cancel;
    function cancel() {
        $mdDialog.cancel();
    }

    vm.save = save;
    function save() {
        let $errorElement = angular.element('[name=theForm]').find('.ng-invalid');

        if ($errorElement.length) {

            let $mdDialogScroll = angular.element('md-dialog-content').scrollTop();
            let $mdDialogTop = angular.element('md-dialog-content').offset().top;
            let $errorElementTop = angular.element('[name=theForm]').find('.ng-invalid').eq(0).offset().top;


            if ($errorElementTop !== $mdDialogTop) {
                angular.element('md-dialog-content').animate({
                    scrollTop: $mdDialogScroll + ($errorElementTop - $mdDialogTop) - 50
                }, 500);
                $errorElement.eq(0).focus();
            }
        } else {

            if(vm.isAdd) {
                vm.allExtensions.push(vm.extension);
            } else {
                var index = vm.allExtensions.indexOf(extension);
                if(index > -1) {
                    vm.allExtensions[index] = vm.extension;
                }
            }

            $mdDialog.hide();
            saveTransformers();

            var editedValue = angular.toJson(vm.allExtensions);

            attributeService
                .saveEntityAttributes(
                    vm.entityType,
                    vm.entityId,
                    types.attributesScope.shared.value,
                    [{key:"configuration", value:editedValue}]
                )
                .then(function success() {
                });

        }
    }
    
    vm.validateId = function() {
        var coincidenceArray = vm.allExtensions.filter(function(ext) {
            return ext.id == vm.extension.id;
        });
        if(coincidenceArray.length) {
            if(!vm.isAdd) {
                if(coincidenceArray[0].id == extension.id) {
                    $scope.theForm.extensionId.$setValidity('uniqueIdValidation', true);
                } else {
                    $scope.theForm.extensionId.$setValidity('uniqueIdValidation', false);
                }
            } else {
                $scope.theForm.extensionId.$setValidity('uniqueIdValidation', false);
            }
        } else {
            $scope.theForm.extensionId.$setValidity('uniqueIdValidation', true);
        }
    };

    function saveTransformers() {
        if(vm.extension.type == types.extensionType.http) {
            var config = vm.extension.configuration.converterConfigurations;
            if(config && config.length > 0) {
                for(let i=0;i<config.length;i++) {
                    for(let j=0;j<config[i].converters.length;j++){
                        for(let k=0;k<config[i].converters[j].attributes.length;k++){
                            if(config[i].converters[j].attributes[k].transformerType == "toDouble"){
                                config[i].converters[j].attributes[k].transformer = {type: "intToDouble"};
                            }
                            delete config[i].converters[j].attributes[k].transformerType;
                        }
                        for(let l=0;l<config[i].converters[j].timeseries.length;l++) {
                            if(config[i].converters[j].timeseries[l].transformerType == "toDouble"){
                                config[i].converters[j].timeseries[l].transformer = {type: "intToDouble"};
                            }
                            delete config[i].converters[j].timeseries[l].transformerType;
                        }
                    }
                }
            }
        }
        if(vm.extension.type == types.extensionType.mqtt) {
            var brokers = vm.extension.configuration.brokers;
            if(brokers && brokers.length > 0) {
                for(let i=0;i<brokers.length;i++) {
                    if(brokers[i].mapping && brokers[i].mapping.length > 0) {
                        for(let j=0;j<brokers[i].mapping.length;j++) {
                            if(brokers[i].mapping[j].converterType == "json") {
                                delete brokers[i].mapping[j].converter.nameExp;
                                delete brokers[i].mapping[j].converter.typeExp;
                            }
                            delete brokers[i].mapping[j].converterType;
                        }
                    }
                    if(brokers[i].connectRequests && brokers[i].connectRequests.length > 0) {
                        for(let j=0;j<brokers[i].connectRequests.length;j++) {
                            delete brokers[i].connectRequests[j].nameExp;
                        }
                    }
                    if(brokers[i].disconnectRequests && brokers[i].disconnectRequests.length > 0) {
                        for(let j=0;j<brokers[i].disconnectRequests.length;j++) {
                            delete brokers[i].disconnectRequests[j].nameExp;
                        }
                    }
                    if(brokers[i].attributeRequests && brokers[i].attributeRequests.length > 0) {
                        for(let j=0;j<brokers[i].attributeRequests.length;j++) {
                            delete brokers[i].attributeRequests[j].nameExp;
                        }
                        for(let j=0;j<brokers[i].attributeRequests.length;j++) {
                            delete brokers[i].attributeRequests[j].attrKey;
                        }
                        for(let j=0;j<brokers[i].attributeRequests.length;j++) {
                            delete brokers[i].attributeRequests[j].requestId;
                        }
                    }
                }
            }
        }
    }

    function editTransformers(extension) {
        if(extension.type == types.extensionType.http) {
            var config = extension.configuration.converterConfigurations;
            for(let i=0;i<config.length;i++) {
                for(let j=0;j<config[i].converters.length;j++){
                    for(let k=0;k<config[i].converters[j].attributes.length;k++){
                        if(config[i].converters[j].attributes[k].transformer){
                            if(config[i].converters[j].attributes[k].transformer.type == "intToDouble"){
                                config[i].converters[j].attributes[k].transformerType = "toDouble";
                            } else {
                                config[i].converters[j].attributes[k].transformerType = "custom";
                                config[i].converters[j].attributes[k].transformer = js_beautify(config[i].converters[j].attributes[k].transformer, {indent_size: 4});
                            }
                        }
                    }
                    for(let l=0;l<config[i].converters[j].timeseries.length;l++) {
                        if(config[i].converters[j].timeseries[l].transformer){
                            if(config[i].converters[j].timeseries[l].transformer.type == "intToDouble"){
                                config[i].converters[j].timeseries[l].transformerType = "toDouble";
                            } else {
                                config[i].converters[j].timeseries[l].transformerType = "custom";
                                config[i].converters[j].timeseries[l].transformer = js_beautify(config[i].converters[j].timeseries[l].transformer, {indent_size: 4});
                            }
                        }
                    }
                }
            }
        }
        if(extension.type == types.extensionType.mqtt) {
            var brokers = extension.configuration.brokers;
            for(let i=0;i<brokers.length;i++) {
                if(brokers[i].mapping && brokers[i].mapping.length > 0) {
                    for(let j=0;j<brokers[i].mapping.length;j++) {
                        if(brokers[i].mapping[j].converter.type == "json") {
                            if(brokers[i].mapping[j].converter.deviceNameTopicExpression) {
                                brokers[i].mapping[j].converter.nameExp = "deviceNameTopicExpression";
                            } else {
                                brokers[i].mapping[j].converter.nameExp = "deviceNameJsonExpression";
                            }
                            if(brokers[i].mapping[j].converter.deviceTypeTopicExpression) {
                                brokers[i].mapping[j].converter.typeExp = "deviceTypeTopicExpression";
                            } else {
                                brokers[i].mapping[j].converter.typeExp = "deviceTypeJsonExpression";
                            }
                            brokers[i].mapping[j].converterType = "json";
                        } else {
                            brokers[i].mapping[j].converterType = "custom";
                        }
                    }
                }
                if(brokers[i].connectRequests && brokers[i].connectRequests.length > 0) {
                    for(let j=0;j<brokers[i].connectRequests.length;j++) {
                        if(brokers[i].connectRequests[j].deviceNameTopicExpression) {
                            brokers[i].connectRequests[j].nameExp = "deviceNameTopicExpression";
                        } else {
                            brokers[i].connectRequests[j].nameExp = "deviceNameJsonExpression";
                        }
                    }
                }
                if(brokers[i].disconnectRequests && brokers[i].disconnectRequests.length > 0) {
                    for(let j=0;j<brokers[i].disconnectRequests.length;j++) {
                        if(brokers[i].disconnectRequests[j].deviceNameTopicExpression) {
                            brokers[i].disconnectRequests[j].nameExp = "deviceNameTopicExpression";
                        } else {
                            brokers[i].disconnectRequests[j].nameExp = "deviceNameJsonExpression";
                        }
                    }
                }
                if(brokers[i].attributeRequests && brokers[i].attributeRequests.length > 0) {
                    for(let j=0;j<brokers[i].attributeRequests.length;j++) {
                        if(brokers[i].attributeRequests[j].deviceNameTopicExpression) {
                            brokers[i].attributeRequests[j].nameExp = "deviceNameTopicExpression";
                        } else {
                            brokers[i].attributeRequests[j].nameExp = "deviceNameJsonExpression";
                        }
                        if(brokers[i].attributeRequests[j].attributeKeyTopicExpression) {
                            brokers[i].attributeRequests[j].attrKey = "attributeKeyTopicExpression";
                        } else {
                            brokers[i].attributeRequests[j].attrKey = "attributeKeyJsonExpression";
                        }
                        if(brokers[i].attributeRequests[j].requestIdTopicExpression) {
                            brokers[i].attributeRequests[j].requestId = "requestIdTopicExpression";
                        } else {
                            brokers[i].attributeRequests[j].requestId = "requestIdJsonExpression";
                        }
                    }
                }
            }
        }
    }
}

/*@ngInject*/
export function ParseToNull() {
    var linker = function (scope, elem, attrs, ngModel) {
        ngModel.$parsers.push(function(value) {
            if(value === "") {
                return null;
            }
            return value;
        })
    };
    return {
        restrict: "A",
        link: linker,
        require: "ngModel"
    }
}