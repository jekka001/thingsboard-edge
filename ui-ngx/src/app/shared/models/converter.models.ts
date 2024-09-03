///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import { BaseData, ExportableEntity } from '@shared/models/base-data';
import { TenantId } from '@shared/models/id/tenant-id';
import { ConverterId } from '@shared/models/id/converter-id';
import { ContentType } from '@shared/models/constants';
import { ActivatedRouteSnapshot } from '@angular/router';
import { IntegrationType } from '@shared/models/integration.models';

export enum ConverterType {
  UPLINK = 'UPLINK',
  DOWNLINK = 'DOWNLINK'
}

export const IntegrationTbelDefaultConvertersUrl = new Map<IntegrationType, string>([
  [IntegrationType.CHIRPSTACK, '/assets/converters/tbel-chirpstack-decoder.raw'],
  [IntegrationType.LORIOT, '/assets/converters/tbel-loriot-decoder.raw'],
  [IntegrationType.TTI,'/assets/converters/tbel-tti-decoder.raw'],
  [IntegrationType.TTN, '/assets/converters/tbel-ttn-decoder.raw'],
  [IntegrationType.SIGFOX, '/assets/converters/tbel-sigfox-decoder.raw'],
  [IntegrationType.AZURE_IOT_HUB, '/assets/converters/tbel-azure-decoder.raw'],
  [IntegrationType.AZURE_EVENT_HUB, '/assets/converters/tbel-azure-decoder.raw'],
  [IntegrationType.AZURE_SERVICE_BUS, '/assets/converters/tbel-azure-decoder.raw'],
  [IntegrationType.AWS_IOT, '/assets/converters/tbel-aws-iot-decoder.raw'],
  [IntegrationType.KPN, '/assets/converters/tbel-kpn-decoder.raw']
]);

export const jsDefaultConvertorsUrl = new Map<ConverterType, string>([
  [ConverterType.UPLINK, '/assets/converters/js-decoder.raw' ],
  [ConverterType.DOWNLINK, '/assets/converters/js-encoder.raw'],
]);

export const tbelDefaultConvertorsUrl = new Map<ConverterType, string>([
  [ConverterType.UPLINK, '/assets/converters/tbel-decoder.raw' ],
  [ConverterType.DOWNLINK, '/assets/converters/tbel-encoder.raw'],
]);

export const DefaultUpdateOnlyKeysValue = ['manufacturer'];
export type DefaultUpdateOnlyKeys = {[key in IntegrationType]?: Array<string>};

export const converterTypeTranslationMap = new Map<ConverterType, string>(
  [
    [ConverterType.UPLINK, 'converter.type-uplink'],
    [ConverterType.DOWNLINK, 'converter.type-downlink'],
  ]
);

export interface Converter extends BaseData<ConverterId>, ExportableEntity<ConverterId> {
  tenantId?: TenantId;
  name: string;
  type: ConverterType;
  debugMode: boolean;
  configuration: any;
  additionalInfo?: any;
  edgeTemplate: boolean;
}

export interface TestUpLinkInputParams {
  metadata: {[key: string]: string};
  payload: string;
  decoder: string;
}

export interface TestDownLinkInputParams {
  metadata: {[key: string]: string};
  msg: string;
  msgType: string;
  integrationMetadata: {[key: string]: string};
  encoder: string;
}

export interface LatestConverterParameters {
  converterType: ConverterType;
  integrationType?: IntegrationType;
  integrationName?: string;
}

export type TestConverterInputParams = TestUpLinkInputParams & TestDownLinkInputParams;

export interface TestConverterResult {
  output: string;
  error: string;
}

export interface ConverterDebugInput {
  inContentType: ContentType;
  inContent: string;
  inMetadata: string;
  inMsgType: string;
  inIntegrationMetadata: string;
}

export interface Vendor {
  name: string;
  logo: string;
}

export interface Model {
  name: string;
  photo: string;
  info: {
    description: string;
    label: string;
    url: string;
  }
}

export function getConverterHelpLink(converter: Converter) {
  let link = 'converters';
  if (converter && converter.type) {
    if (converter.type === ConverterType.UPLINK) {
      link = 'uplinkConverters';
    } else {
      link = 'downlinkConverters';
    }
  }
  return link;
}

export interface ConverterParams {
  converterScope: string;
}

export function resolveConverterParams(route: ActivatedRouteSnapshot): ConverterParams {
  return {
    converterScope: route.data.convertersType ? route.data.convertersType : 'tenant'
  };
}
