///
/// Copyright © 2016-2025 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import {
  AdditionalDebugActionConfig,
  HasEntityDebugSettings,
  HasTenantId,
  HasVersion
} from '@shared/models/entity.models';
import { BaseData, ExportableEntity } from '@shared/models/base-data';
import { CalculatedFieldId } from '@shared/models/id/calculated-field-id';
import { EntityId } from '@shared/models/id/entity-id';
import { AttributeScope } from '@shared/models/telemetry/telemetry.models';
import { EntityType } from '@shared/models/entity-type.models';
import { AliasFilterType } from '@shared/models/alias.models';
import { Observable } from 'rxjs';
import { TbEditorCompleter } from '@shared/models/ace/completion.models';
import {
  AceHighlightRule,
  AceHighlightRules,
  dotOperatorHighlightRule,
  endGroupHighlightRule
} from '@shared/models/ace/ace.models';

export interface CalculatedField extends Omit<BaseData<CalculatedFieldId>, 'label'>, HasVersion, HasEntityDebugSettings, HasTenantId, ExportableEntity<CalculatedFieldId> {
  configuration: CalculatedFieldConfiguration;
  type: CalculatedFieldType;
  entityId: EntityId;
}

export enum CalculatedFieldType {
  SIMPLE = 'SIMPLE',
  SCRIPT = 'SCRIPT',
}

export const CalculatedFieldTypeTranslations = new Map<CalculatedFieldType, string>(
  [
    [CalculatedFieldType.SIMPLE, 'calculated-fields.type.simple'],
    [CalculatedFieldType.SCRIPT, 'calculated-fields.type.script'],
  ]
)

export interface CalculatedFieldConfiguration {
  type: CalculatedFieldType;
  expression: string;
  arguments: Record<string, CalculatedFieldArgument>;
  output: CalculatedFieldOutput;
}

export interface CalculatedFieldOutput {
  type: OutputType;
  name: string;
  scope?: AttributeScope;
}

export enum ArgumentEntityType {
  Current = 'CURRENT',
  Device = 'DEVICE',
  Asset = 'ASSET',
  Customer = 'CUSTOMER',
  Tenant = 'TENANT',
}

export const ArgumentEntityTypeTranslations = new Map<ArgumentEntityType, string>(
  [
    [ArgumentEntityType.Current, 'calculated-fields.argument-current'],
    [ArgumentEntityType.Device, 'calculated-fields.argument-device'],
    [ArgumentEntityType.Asset, 'calculated-fields.argument-asset'],
    [ArgumentEntityType.Customer, 'calculated-fields.argument-customer'],
    [ArgumentEntityType.Tenant, 'calculated-fields.argument-tenant'],
  ]
)

export enum ArgumentType {
  Attribute = 'ATTRIBUTE',
  LatestTelemetry = 'TS_LATEST',
  Rolling = 'TS_ROLLING',
}

export enum TestArgumentType {
  Single = 'SINGLE_VALUE',
  Rolling = 'TS_ROLLING',
}

export const TestArgumentTypeMap = new Map<ArgumentType, TestArgumentType>(
  [
    [ArgumentType.Attribute, TestArgumentType.Single],
    [ArgumentType.LatestTelemetry, TestArgumentType.Single],
    [ArgumentType.Rolling, TestArgumentType.Rolling],
  ]
)

export enum OutputType {
  Attribute = 'ATTRIBUTES',
  Timeseries = 'TIME_SERIES',
}

export const OutputTypeTranslations = new Map<OutputType, string>(
  [
    [OutputType.Attribute, 'calculated-fields.attribute'],
    [OutputType.Timeseries, 'calculated-fields.timeseries'],
  ]
)

export const ArgumentTypeTranslations = new Map<ArgumentType, string>(
  [
    [ArgumentType.Attribute, 'calculated-fields.attribute'],
    [ArgumentType.LatestTelemetry, 'calculated-fields.latest-telemetry'],
    [ArgumentType.Rolling, 'calculated-fields.rolling'],
  ]
)

export interface CalculatedFieldArgument {
  refEntityKey: RefEntityKey;
  defaultValue?: string;
  refEntityId?: RefEntityKey;
  limit?: number;
  timeWindow?: number;
}

export interface RefEntityKey {
  key: string;
  type: ArgumentType;
  scope?: AttributeScope;
}

export interface RefEntityKey {
  entityType: ArgumentEntityType;
  id: string;
}

export interface CalculatedFieldArgumentValue extends CalculatedFieldArgument {
  argumentName: string;
}

export type CalculatedFieldTestScriptFn = (calculatedField: CalculatedField, argumentsObj?: Record<string, unknown>, closeAllOnSave?: boolean) => Observable<string>;

export interface CalculatedFieldDialogData {
  value?: CalculatedField;
  buttonTitle: string;
  entityId: EntityId;
  debugLimitsConfiguration: string;
  tenantId: string;
  entityName?: string;
  additionalDebugActionConfig: AdditionalDebugActionConfig<(calculatedField: CalculatedField) => void>;
  getTestScriptDialogFn: CalculatedFieldTestScriptFn;
  isDirty?: boolean;
}

export interface CalculatedFieldDebugDialogData {
  tenantId: string;
  value: CalculatedField;
  getTestScriptDialogFn: CalculatedFieldTestScriptFn;
}

export interface CalculatedFieldTestScriptInputParams {
  arguments: CalculatedFieldEventArguments;
  expression: string;
}

export interface CalculatedFieldTestScriptDialogData extends CalculatedFieldTestScriptInputParams {
  argumentsEditorCompleter: TbEditorCompleter;
  argumentsHighlightRules: AceHighlightRules;
  openCalculatedFieldEdit?: boolean;
}

export interface ArgumentEntityTypeParams {
  title: string;
  entityType: EntityType
}

export const ArgumentEntityTypeParamsMap =new Map<ArgumentEntityType, ArgumentEntityTypeParams>([
  [ArgumentEntityType.Device, { title: 'calculated-fields.device-name', entityType: EntityType.DEVICE }],
  [ArgumentEntityType.Asset, { title: 'calculated-fields.asset-name', entityType: EntityType.ASSET }],
  [ArgumentEntityType.Customer, { title: 'calculated-fields.customer-name', entityType: EntityType.CUSTOMER }],
])

export const getCalculatedFieldCurrentEntityFilter = (entityName: string, entityId: EntityId) => {
  switch (entityId.entityType) {
    case EntityType.ASSET_PROFILE:
      return {
        assetTypes: [entityName],
        type: AliasFilterType.assetType
      };
    case EntityType.DEVICE_PROFILE:
      return {
        deviceTypes: [entityName],
        type: AliasFilterType.deviceType
      };
    default:
      return {
        type: AliasFilterType.singleEntity,
        singleEntity: entityId,
      };
  }
}

export interface CalculatedFieldArgumentValueBase {
  argumentName: string;
  type: ArgumentType;
}

export interface CalculatedFieldAttributeArgumentValue<ValueType = unknown> extends CalculatedFieldArgumentValueBase {
  ts: number;
  value: ValueType;
}

export interface CalculatedFieldLatestTelemetryArgumentValue<ValueType = unknown> extends CalculatedFieldArgumentValueBase {
  ts: number;
  value: ValueType;
}

export interface CalculatedFieldRollingTelemetryArgumentValue<ValueType = unknown> extends CalculatedFieldArgumentValueBase {
  timeWindow: { startTs: number; endTs: number; limit: number };
  values: CalculatedFieldSingleArgumentValue<ValueType>[];
}

export type CalculatedFieldSingleArgumentValue<ValueType = unknown> = CalculatedFieldAttributeArgumentValue<ValueType> & CalculatedFieldLatestTelemetryArgumentValue<ValueType>;

export type CalculatedFieldArgumentEventValue<ValueType = unknown> = CalculatedFieldAttributeArgumentValue<ValueType> | CalculatedFieldLatestTelemetryArgumentValue<ValueType> | CalculatedFieldRollingTelemetryArgumentValue<ValueType>;

export type CalculatedFieldEventArguments<ValueType = unknown> = Record<string, CalculatedFieldArgumentEventValue<ValueType>>;

export const CalculatedFieldLatestTelemetryArgumentAutocomplete = {
  meta: 'object',
  type: '{ ts: number; value: any; }',
  description: 'Calculated field latest telemetry value argument.',
  children: {
    ts: {
      meta: 'number',
      type: 'number',
      description: 'Time stamp',
    },
    value: {
      meta: 'any',
      type: 'any',
      description: 'Value',
    }
  },
};

export const CalculatedFieldAttributeValueArgumentAutocomplete = {
  meta: 'object',
  type: '{ ts: number; value: any; }',
  description: 'Calculated field attribute value argument.',
  children: {
    ts: {
      meta: 'number',
      type: 'number',
      description: 'Time stamp',
    },
    value: {
      meta: 'any',
      type: 'any',
      description: 'Value',
    }
  },
};
export const CalculatedFieldRollingValueArgumentFunctionsAutocomplete = {
  max: {
    meta: 'function',
    description: 'Computes the maximum value in the list of rolling argument values. Returns NaN if any value is NaN and ignoreNaN is false.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The maximum value, or NaN if applicable',
      type: 'number'
    }
  },
  min: {
    meta: 'function',
    description: 'Computes the minimum value in the list of rolling argument values. Returns NaN if any value is NaN and ignoreNaN is false.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The minimum value, or NaN if applicable',
      type: 'number'
    }
  },
  mean: {
    meta: 'function',
    description: 'Computes the mean value of the rolling argument values list. Returns NaN if any value is NaN and ignoreNaN is false.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The mean value, or NaN if applicable',
      type: 'number'
    }
  },
  std: {
    meta: 'function',
    description: 'Computes the standard deviation in the list of rolling argument values. Returns NaN if any value is NaN and ignoreNaN is false.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The standard deviation, or NaN if applicable',
      type: 'number'
    }
  },
  median: {
    meta: 'function',
    description: 'Computes the median value of the rolling argument values list. Returns NaN if any value is NaN and ignoreNaN is false.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The median value, or NaN if applicable',
      type: 'number'
    }
  },
  count: {
    meta: 'function',
    description: 'Counts values in the list of rolling argument values. Counts non-NaN values if ignoreNaN is true, otherwise - total size.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The count of values',
      type: 'number'
    }
  },
  last: {
    meta: 'function',
    description: 'Returns the last non-NaN value in the list of rolling argument values if ignoreNaN is true, otherwise - the last value.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The last value, or NaN if applicable',
      type: 'number'
    }
  },
  first: {
    meta: 'function',
    description: 'Returns the first non-NaN value in the list of rolling argument values if ignoreNaN is true, otherwise - the first value.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The first value, or NaN if applicable',
      type: 'number'
    }
  },
  sum: {
    meta: 'function',
    description: 'Computes the sum of values in the list of rolling argument values. Returns NaN if any value is NaN and ignoreNaN is false.',
    args: [
      {
        name: 'ignoreNaN',
        description: 'Whether to ignore NaN values. Equals true by default.',
        type: 'boolean',
        optional: true,
      }
    ],
    return: {
      description: 'The sum of values, or NaN if applicable',
      type: 'number'
    }
  }
};

export const CalculatedFieldRollingValueArgumentAutocomplete = {
  meta: 'object',
  type: '{ values: { ts: number; value: any; }[]; timeWindow: { startTs: number; endTs: number; limit: number } }; }',
  description: 'Calculated field rolling value argument.',
  children: {
    ...CalculatedFieldRollingValueArgumentFunctionsAutocomplete,
    values: {
      meta: 'array',
      type: '{ ts: number; value: any; }[]',
      description: 'Values array',
    },
    timeWindow: {
      meta: 'object',
      type: '{ startTs: number; endTs: number; limit: number }',
      description: 'Time window configuration',
      children: {
        startTs: {
          meta: 'number',
          type: 'number',
          description: 'Start time stamp',
        },
        endTs: {
          meta: 'number',
          type: 'number',
          description: 'End time stamp',
        },
        limit: {
          meta: 'number',
          type: 'number',
          description: 'Limit',
        }
      }
    }
  },
};

export const getCalculatedFieldArgumentsEditorCompleter = (argumentsObj: Record<string, CalculatedFieldArgument>): TbEditorCompleter => {
  return new TbEditorCompleter(Object.keys(argumentsObj).reduce((acc, key) => {
    switch (argumentsObj[key].refEntityKey.type) {
      case ArgumentType.Attribute:
        acc[key] = CalculatedFieldAttributeValueArgumentAutocomplete;
        break;
      case ArgumentType.LatestTelemetry:
        acc[key] = CalculatedFieldLatestTelemetryArgumentAutocomplete;
        break;
      case ArgumentType.Rolling:
        acc[key] = CalculatedFieldRollingValueArgumentAutocomplete;
        break;
    }
    return acc;
  }, {}));
}

export const getCalculatedFieldArgumentsHighlights = (
  argumentsObj: Record<string, CalculatedFieldArgument>
): AceHighlightRules => {
  return {
    start: Object.keys(argumentsObj).map(key => ({
      token: 'tb.calculated-field-key',
      regex: `\\b${key}\\b`,
      next: argumentsObj[key].refEntityKey.type === ArgumentType.Rolling
        ? 'calculatedFieldRollingArgumentValue'
        : 'calculatedFieldSingleArgumentValue'
    })),
    ...calculatedFieldSingleArgumentValueHighlightRules,
    ...calculatedFieldRollingArgumentValueHighlightRules,
    ...calculatedFieldTimeWindowArgumentValueHighlightRules
  };
};

const calculatedFieldSingleArgumentValueHighlightRules: AceHighlightRules = {
  calculatedFieldSingleArgumentValue: [
    dotOperatorHighlightRule,
    {
      token: 'tb.calculated-field-value',
      regex: /value/,
      next: 'no_regex'
    },
    {
      token: 'tb.calculated-field-ts',
      regex: /ts/,
      next: 'no_regex'
    },
    endGroupHighlightRule
  ],
}

const calculatedFieldRollingArgumentValueFunctionsHighlightRules: Array<AceHighlightRule> =
  ['max', 'min', 'mean', 'std', 'median', 'count', 'last', 'first', 'sum'].map(funcName => ({
    token: 'tb.calculated-field-func',
    regex: `\\b${funcName}\\b`,
    next: 'no_regex'
  }));

const calculatedFieldRollingArgumentValueHighlightRules: AceHighlightRules = {
  calculatedFieldRollingArgumentValue: [
    dotOperatorHighlightRule,
    {
      token: 'tb.calculated-field-values',
      regex: /values/,
      next: 'no_regex'
    },
    {
      token: 'tb.calculated-field-time-window',
      regex: /timeWindow/,
      next: 'calculatedFieldRollingArgumentTimeWindow'
    },
    ...calculatedFieldRollingArgumentValueFunctionsHighlightRules,
    endGroupHighlightRule
  ],
}

const calculatedFieldTimeWindowArgumentValueHighlightRules: AceHighlightRules = {
  calculatedFieldRollingArgumentTimeWindow: [
    dotOperatorHighlightRule,
    {
      token: 'tb.calculated-field-start-ts',
      regex: /startTs/,
      next: 'no_regex'
    },
    {
      token: 'tb.calculated-field-end-ts',
      regex: /endTs/,
      next: 'no_regex'
    },
    {
      token: 'tb.calculated-field-limit',
      regex: /limit/,
      next: 'no_regex'
    },
    endGroupHighlightRule
  ]
}
