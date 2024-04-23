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

import {
  LatestChartTooltipValueType,
  LatestChartWidgetSettings
} from '@home/components/widget/lib/chart/latest-chart.models';
import { BackgroundType, Font } from '@shared/models/widget-settings.models';
import { LegendPosition } from '@shared/models/widget.models';
import { DeepPartial } from '@shared/models/common';
import {
  pieChartAnimationDefaultSettings,
  PieChartLabelPosition,
  PieChartSettings
} from '@home/components/widget/lib/chart/pie-chart.models';
import { isDefinedAndNotNull, mergeDeep } from '@core/utils';
import { EChartsAnimationSettings } from '@home/components/widget/lib/chart/echarts-widget.models';

export interface PieChartWidgetSettings extends LatestChartWidgetSettings {
  showLabel: boolean;
  labelPosition: PieChartLabelPosition;
  labelFont: Font;
  labelColor: string;
  borderWidth: number;
  borderColor: string;
  radius: number;
  clockwise: boolean;
}

export const pieChartWidgetDefaultSettings: PieChartWidgetSettings = {
  showLabel: true,
  labelPosition: PieChartLabelPosition.outside,
  labelFont: {
    family: 'Roboto',
    size: 12,
    sizeUnit: 'px',
    style: 'normal',
    weight: 'normal',
    lineHeight: '1.2'
  },
  labelColor: '#000',
  borderWidth: 0,
  borderColor: '#000',
  radius: 80,
  clockwise: false,
  sortSeries: false,
  animation: mergeDeep({} as EChartsAnimationSettings,
    pieChartAnimationDefaultSettings),
  showLegend: true,
  legendPosition: LegendPosition.bottom,
  legendLabelFont: {
    family: 'Roboto',
    size: 12,
    sizeUnit: 'px',
    style: 'normal',
    weight: '400',
    lineHeight: '16px'
  },
  legendLabelColor: 'rgba(0, 0, 0, 0.38)',
  legendValueFont: {
    family: 'Roboto',
    size: 14,
    sizeUnit: 'px',
    style: 'normal',
    weight: '500',
    lineHeight: '20px'
  },
  legendValueColor: 'rgba(0, 0, 0, 0.87)',
  showTooltip: true,
  tooltipValueType: LatestChartTooltipValueType.percentage,
  tooltipValueDecimals: 0,
  tooltipValueFont: {
    family: 'Roboto',
    size: 13,
    sizeUnit: 'px',
    style: 'normal',
    weight: '500',
    lineHeight: '16px'
  },
  tooltipValueColor: 'rgba(0, 0, 0, 0.76)',
  tooltipBackgroundColor: 'rgba(255, 255, 255, 0.76)',
  tooltipBackgroundBlur: 4,
  background: {
    type: BackgroundType.color,
    color: '#fff',
    overlay: {
      enabled: false,
      color: 'rgba(255,255,255,0.72)',
      blur: 3
    }
  }
};

export const pieChartWidgetPieChartSettings = (settings: PieChartWidgetSettings): DeepPartial<PieChartSettings> => ({
  autoScale: false,
  doughnut: false,
  clockwise: settings.clockwise,
  sortSeries: settings.sortSeries,
  showTotal: false,
  animation: settings.animation,
  showLegend: settings.showLegend,
  showLabel: settings.showLabel,
  labelPosition: settings.labelPosition,
  labelFont: settings.labelFont,
  labelColor: settings.labelColor,
  borderWidth: settings.borderWidth,
  borderColor: settings.borderColor,
  radius: isDefinedAndNotNull(settings.radius) ? settings.radius + '%' : undefined,
  emphasisBorderWidth: settings.borderWidth,
  emphasisBorderColor: settings.borderColor,
  showTooltip: settings.showTooltip,
  tooltipValueType: settings.tooltipValueType,
  tooltipValueDecimals: settings.tooltipValueDecimals,
  tooltipValueFont: settings.tooltipValueFont,
  tooltipValueColor: settings.tooltipValueColor,
  tooltipBackgroundColor: settings.tooltipBackgroundColor,
  tooltipBackgroundBlur: settings.tooltipBackgroundBlur
});
