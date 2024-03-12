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

import { AttributeScope } from '@shared/models/telemetry/telemetry.models';
import { widgetType } from '@shared/models/widget.models';

export enum GetValueAction {
  DO_NOTHING = 'DO_NOTHING',
  EXECUTE_RPC = 'EXECUTE_RPC',
  GET_ATTRIBUTE = 'GET_ATTRIBUTE',
  GET_TIME_SERIES = 'GET_TIME_SERIES',
  GET_DASHBOARD_STATE = 'GET_DASHBOARD_STATE'
}

export const getValueActions = Object.keys(GetValueAction) as GetValueAction[];

export const getValueActionsByWidgetType = (type: widgetType): GetValueAction[] => {
  if (type !== widgetType.rpc) {
    return getValueActions.filter(action => action !== GetValueAction.EXECUTE_RPC);
  } else {
    return getValueActions;
  }
};

export const getValueActionTranslations = new Map<GetValueAction, string>(
  [
    [GetValueAction.DO_NOTHING, 'widgets.value-action.do-nothing'],
    [GetValueAction.EXECUTE_RPC, 'widgets.value-action.execute-rpc'],
    [GetValueAction.GET_ATTRIBUTE, 'widgets.value-action.get-attribute'],
    [GetValueAction.GET_TIME_SERIES, 'widgets.value-action.get-time-series'],
    [GetValueAction.GET_DASHBOARD_STATE, 'widgets.value-action.get-dashboard-state']
  ]
);

export interface RpcSettings {
  method: string;
  requestTimeout: number;
  requestPersistent: boolean;
  persistentPollingInterval: number;
}

export interface TelemetryValueSettings {
  key: string;
}

export interface GetAttributeValueSettings extends TelemetryValueSettings {
  scope: AttributeScope | null;
}

export interface SetAttributeValueSettings extends TelemetryValueSettings {
  scope: AttributeScope.SERVER_SCOPE | AttributeScope.SHARED_SCOPE;
}

export enum DataToValueType {
  NONE = 'NONE',
  FUNCTION = 'FUNCTION'
}

export interface DataToValueSettings {
  type: DataToValueType;
  dataToValueFunction: string;
  compareToValue?: any;
}

export interface ValueActionSettings {
  actionLabel?: string;
}

export interface GetValueSettings<V> extends ValueActionSettings {
  action: GetValueAction;
  defaultValue: V;
  executeRpc?: RpcSettings;
  getAttribute: GetAttributeValueSettings;
  getTimeSeries: TelemetryValueSettings;
  dataToValue: DataToValueSettings;
}

export enum SetValueAction {
  EXECUTE_RPC = 'EXECUTE_RPC',
  SET_ATTRIBUTE = 'SET_ATTRIBUTE',
  ADD_TIME_SERIES = 'ADD_TIME_SERIES'
}

export const setValueActions = Object.keys(SetValueAction) as SetValueAction[];

export const setValueActionsByWidgetType = (type: widgetType): SetValueAction[] => {
  if (type !== widgetType.rpc) {
    return setValueActions.filter(action => action !== SetValueAction.EXECUTE_RPC);
  } else {
    return setValueActions;
  }
};

export const setValueActionTranslations = new Map<SetValueAction, string>(
  [
    [SetValueAction.EXECUTE_RPC, 'widgets.value-action.execute-rpc'],
    [SetValueAction.SET_ATTRIBUTE, 'widgets.value-action.set-attribute'],
    [SetValueAction.ADD_TIME_SERIES, 'widgets.value-action.add-time-series']
  ]
);

export enum ValueToDataType {
  VALUE = 'VALUE',
  CONSTANT = 'CONSTANT',
  FUNCTION = 'FUNCTION',
  NONE = 'NONE'
}

export interface ValueToDataSettings {
  type: ValueToDataType;
  constantValue: any;
  valueToDataFunction: string;
}

export interface SetValueSettings extends ValueActionSettings {
  action: SetValueAction;
  executeRpc: RpcSettings;
  setAttribute: SetAttributeValueSettings;
  putTimeSeries: TelemetryValueSettings;
  valueToData: ValueToDataSettings;
}
