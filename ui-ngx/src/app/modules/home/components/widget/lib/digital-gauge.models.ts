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

import { GaugeType } from '@home/components/widget/lib/canvas-digital-gauge';
import { AnimationRule } from '@home/components/widget/lib/analogue-gauge.models';
import { FontSettings } from '@home/components/widget/lib/settings.models';
import {
  AdvancedColorRange,
  ColorSettings,
  ValueSourceType,
  ValueSourceTypeConfig
} from '@shared/models/widget-settings.models';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { isDefinedAndNotNull } from '@core/utils';

export interface AttributeSourceProperty {
  valueSource: string;
  entityAlias?: string;
  attribute?: string;
  value?: number;
}

export interface FixedLevelColors {
  from?: ValueSourceTypeConfig | number;
  to?: ValueSourceTypeConfig | number;
  color: string;
}

export interface ColorLevelSetting {
  value: number;
  color: string;
}

export type colorLevel = Array<string | ColorLevelSetting>;

export type attributesGaugeType = 'levelColors' | 'ticks';

export enum DigitalGaugeType {
  arc = 'arc',
  donut = 'donut',
  horizontalBar = 'horizontalBar',
  verticalBar = 'verticalBar'
}

export const digitalGaugeLayouts = Object.keys(DigitalGaugeType) as DigitalGaugeType[];

export const digitalGaugeLayoutTranslations = new Map<DigitalGaugeType, string>(
  [
    [DigitalGaugeType.arc, 'widgets.gauge.gauge-type-arc'],
    [DigitalGaugeType.donut, 'widgets.gauge.gauge-type-donut'],
    [DigitalGaugeType.horizontalBar, 'widgets.gauge.gauge-type-horizontal-bar'],
    [DigitalGaugeType.verticalBar, 'widgets.gauge.gauge-type-vertical-bar']
  ]
);

export const digitalGaugeLayoutImages = new Map<DigitalGaugeType, string>(
  [
    [DigitalGaugeType.arc, 'assets/widget/simple-gauge/arc-layout.svg'],
    [DigitalGaugeType.donut, 'assets/widget/simple-gauge/donut-layout.svg'],
    [DigitalGaugeType.horizontalBar, 'assets/widget/simple-gauge/horizontal-bar-layout.svg'],
    [DigitalGaugeType.verticalBar, 'assets/widget/simple-gauge/vertical-bar-layout.svg']
  ]
);

export interface DigitalGaugeSettings {
  minValue?: number;
  maxValue?: number;
  gaugeType?: GaugeType;
  donutStartAngle?: number;
  neonGlowBrightness?: number;
  dashThickness?: number;
  roundedLineCap?: boolean;
  title?: string;
  showTitle?: boolean;
  unitTitle?: string;
  showUnitTitle?: boolean;
  showTimestamp?: boolean;
  timestampFormat?: string;
  showValue?: boolean;
  showMinMax?: boolean;
  gaugeWidthScale?: number;
  defaultColor?: string;
  gaugeColor?: string;

  barColor?: ColorSettings;

  useFixedLevelColor?: boolean;
  levelColors?: colorLevel;
  fixedLevelColors?: FixedLevelColors[];
  animation?: boolean;
  animationDuration?: number;
  animationRule?: AnimationRule;
  titleFont?: FontSettings;
  labelFont?: FontSettings;
  valueFont?: FontSettings;
  minMaxFont?: FontSettings;
  decimals?: number;
  units?: string;
  hideValue?: boolean;
  hideMinMax?: boolean;
  showTicks?: boolean;
  ticksValue?: ValueSourceTypeConfig[];
  ticks?: number[];
  colorTicks?: string;
  tickWidth?: number;
}

export const defaultDigitalSimpleGaugeOptions: DigitalGaugeSettings = {
  gaugeType: DigitalGaugeType.donut,
  timestampFormat: 'yyyy-MM-dd HH:mm:ss',
};

export const backwardCompatibilityFixedLevelColors = (fixedLevelColors) => {
  const valueSourceWithDataKey: AdvancedColorRange[] = [];
  fixedLevelColors.forEach(fixedLevelColor => valueSourceWithDataKey.push({
    from: {
      type: fixedLevelColor?.from?.valueSource === 'predefinedValue' ? ValueSourceType.constant : ValueSourceType.entity,
      value: fixedLevelColor?.from?.value || null,
      entityAlias: fixedLevelColor?.from?.entityAlias || '',
      entityKey: fixedLevelColor?.from?.attribute || '',
      entityKeyType: DataKeyType.attribute
    },
    to: {
      type: fixedLevelColor?.to?.valueSource === 'predefinedValue' ? ValueSourceType.constant : ValueSourceType.entity,
      value: fixedLevelColor?.to?.value || null,
      entityAlias: fixedLevelColor?.to?.entityAlias || '',
      entityKey: fixedLevelColor?.to?.attribute || '',
      entityKeyType: DataKeyType.attribute
    },
    color: fixedLevelColor.color
  }) );
  return valueSourceWithDataKey;
};

export const backwardCompatibilityTicks = (ticksValue) => {
  const ticks: ValueSourceTypeConfig[] = [];
  if (ticksValue?.length && isDefinedAndNotNull(ticksValue[0]?.valueSource)) {
    ticksValue.forEach(tick => ticks.push({
      type: tick?.valueSource === 'predefinedValue' ? ValueSourceType.constant : ValueSourceType.entity,
      value: tick?.value || null,
      entityAlias: tick?.entityAlias || '',
      entityKey: tick?.attribute || '',
      entityKeyType: DataKeyType.attribute
    }) );
  } else {
    return ticksValue;
  }
  return ticks;
};
