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

import * as echarts from 'echarts/core';
import { Axis } from 'echarts';
import AxisModel from 'echarts/types/src/coord/cartesian/AxisModel';
import { formatValue, isDefinedAndNotNull, isNumber, isString } from '@core/utils';
import TimeScale from 'echarts/types/src/scale/Time';
import {
  DataZoomComponent, DataZoomComponentOption,
  GridComponent, GridComponentOption,
  MarkLineComponent, MarkLineComponentOption,
  TooltipComponent, TooltipComponentOption,
  VisualMapComponent, VisualMapComponentOption
} from 'echarts/components';
import {
  BarChart,
  LineChart,
  CustomChart,
  CustomSeriesOption,
  LineSeriesOption,
  BarSeriesOption, PieSeriesOption, PieChart
} from 'echarts/charts';
import { LabelLayout } from 'echarts/features';
import { CanvasRenderer, SVGRenderer } from 'echarts/renderers';
import { DataEntry, DataSet } from '@shared/models/widget.models';
import { Interval, IntervalMath } from '@shared/models/time/time.models';
import { CallbackDataParams } from 'echarts/types/dist/shared';
import { Renderer2 } from '@angular/core';
import { DateFormatProcessor, DateFormatSettings, Font } from '@shared/models/widget-settings.models';

class EChartsModule {
  private initialized = false;

  init() {
    if (!this.initialized) {
      const axisGetBandWidth = Axis.prototype.getBandWidth;

      Axis.prototype.getBandWidth = function(){
        const model: AxisModel = this.model;
        const axisOption = model.option;
        const tbTimewindowInterval: Interval = (axisOption as any).tbTimewindowInterval;
        if (this.scale.type === 'time' && (isNumber(tbTimewindowInterval) || isString(tbTimewindowInterval))) {
          const timeScale: TimeScale = this.scale;
          const axisExtent: [number, number] = this._extent;
          const dataExtent = timeScale.getExtent();
          const size = Math.abs(axisExtent[1] - axisExtent[0]);
          return IntervalMath.numberValue(tbTimewindowInterval) * (size / (dataExtent[1] - dataExtent[0]));
        } else {
          return axisGetBandWidth.call(this);
        }
      };

      echarts.use([
        TooltipComponent,
        GridComponent,
        VisualMapComponent,
        DataZoomComponent,
        MarkLineComponent,
        LineChart,
        BarChart,
        PieChart,
        CustomChart,
        LabelLayout,
        CanvasRenderer,
        SVGRenderer
      ]);

      this.initialized = true;
    }
  }
}

export const echartsModule = new EChartsModule();

export type EChartsOption = echarts.ComposeOption<
  | TooltipComponentOption
  | GridComponentOption
  | VisualMapComponentOption
  | DataZoomComponentOption
  | MarkLineComponentOption
  | LineSeriesOption
  | CustomSeriesOption
  | BarSeriesOption
  | PieSeriesOption
>;

export type ECharts = echarts.ECharts;

export type EChartsDataItem = [number, any, number, number];

export type NamedDataSet = {name: string; value: EChartsDataItem}[];

export const toNamedData = (data: DataSet): NamedDataSet => {
  if (!data?.length) {
    return [];
  } else {
    return data.map(d => {
      const ts = isDefinedAndNotNull(d[2]) ? d[2][0] : d[0];
      return {
        name: ts + '',
        value: toEChartsDataItem(d)
      };
    });
  }
};

const toEChartsDataItem = (entry: DataEntry): EChartsDataItem => {
  const item: EChartsDataItem = [entry[0], entry[1], entry[0], entry[0]];
  if (isDefinedAndNotNull(entry[2])) {
    item[2] = entry[2][0];
    item[3] = entry[2][1];
  }
  return item;
};

export interface EChartsTooltipWidgetSettings {
  showTooltip: boolean;
  tooltipValueFont: Font;
  tooltipValueColor: string;
  tooltipShowDate: boolean;
  tooltipDateFormat: DateFormatSettings;
  tooltipDateFont: Font;
  tooltipDateColor: string;
  tooltipBackgroundColor: string;
  tooltipBackgroundBlur: number;
}

export const echartsTooltipFormatter = (renderer: Renderer2,
                                        tooltipDateFormat: DateFormatProcessor,
                                        settings: EChartsTooltipWidgetSettings,
                                        params: CallbackDataParams[],
                                        decimals: number,
                                        units: string,
                                        focusedSeriesIndex: number): null | HTMLElement => {
  if (!params.length || !params[0]) {
    return null;
  }
  const tooltipElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(tooltipElement, 'display', 'flex');
  renderer.setStyle(tooltipElement, 'flex-direction', 'column');
  renderer.setStyle(tooltipElement, 'align-items', 'flex-start');
  renderer.setStyle(tooltipElement, 'gap', '4px');
  if (settings.tooltipShowDate) {
    const dateElement: HTMLElement = renderer.createElement('div');
    let dateText: string;
    const startTs = params[0].value[2];
    const endTs = params[0].value[3];
    if (startTs && endTs && (endTs - 1) > startTs) {
      const startDateText = tooltipDateFormat.update(startTs);
      const endDateText = tooltipDateFormat.update(endTs - 1);
      if (startDateText === endDateText) {
        dateText = startDateText;
      } else {
        dateText = startDateText + ' - ' + endDateText;
      }
    } else {
      const ts = params[0].value[0];
      dateText = tooltipDateFormat.update(ts);
    }
    renderer.appendChild(dateElement, renderer.createText(dateText));
    renderer.setStyle(dateElement, 'font-family', settings.tooltipDateFont.family);
    renderer.setStyle(dateElement, 'font-size', settings.tooltipDateFont.size + settings.tooltipDateFont.sizeUnit);
    renderer.setStyle(dateElement, 'font-style', settings.tooltipDateFont.style);
    renderer.setStyle(dateElement, 'font-weight', settings.tooltipDateFont.weight);
    renderer.setStyle(dateElement, 'line-height', settings.tooltipDateFont.lineHeight);
    renderer.setStyle(dateElement, 'color', settings.tooltipDateColor);
    renderer.appendChild(tooltipElement, dateElement);
  }
  let seriesParams: CallbackDataParams = null;
  if (focusedSeriesIndex > -1) {
    seriesParams = params.find(param => param.seriesIndex === focusedSeriesIndex);
  }
  if (seriesParams) {
    renderer.appendChild(tooltipElement, constructEchartsTooltipSeriesElement(renderer, settings, seriesParams, decimals, units));
  } else {
    for (seriesParams of params) {
      renderer.appendChild(tooltipElement, constructEchartsTooltipSeriesElement(renderer, settings, seriesParams, decimals, units));
    }
  }
  return tooltipElement;
};

const constructEchartsTooltipSeriesElement = (renderer: Renderer2,
                                              settings: EChartsTooltipWidgetSettings,
                                              seriesParams: CallbackDataParams,
                                              decimals: number,
                                              units: string): HTMLElement => {
  const labelValueElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(labelValueElement, 'display', 'flex');
  renderer.setStyle(labelValueElement, 'flex-direction', 'row');
  renderer.setStyle(labelValueElement, 'align-items', 'center');
  renderer.setStyle(labelValueElement, 'align-self', 'stretch');
  renderer.setStyle(labelValueElement, 'gap', '12px');
  const labelElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(labelElement, 'display', 'flex');
  renderer.setStyle(labelElement, 'align-items', 'center');
  renderer.setStyle(labelElement, 'gap', '8px');
  renderer.appendChild(labelValueElement, labelElement);
  const circleElement: HTMLElement = renderer.createElement('div');
  renderer.setStyle(circleElement, 'width', '8px');
  renderer.setStyle(circleElement, 'height', '8px');
  renderer.setStyle(circleElement, 'border-radius', '50%');
  renderer.setStyle(circleElement, 'background', seriesParams.color);
  renderer.appendChild(labelElement, circleElement);
  const labelTextElement: HTMLElement = renderer.createElement('div');
  renderer.appendChild(labelTextElement, renderer.createText(seriesParams.seriesName));
  renderer.setStyle(labelTextElement, 'font-family', 'Roboto');
  renderer.setStyle(labelTextElement, 'font-size', '12px');
  renderer.setStyle(labelTextElement, 'font-style', 'normal');
  renderer.setStyle(labelTextElement, 'font-weight', '400');
  renderer.setStyle(labelTextElement, 'line-height', '16px');
  renderer.setStyle(labelTextElement, 'letter-spacing', '0.4px');
  renderer.setStyle(labelTextElement, 'color', 'rgba(0, 0, 0, 0.76)');
  renderer.appendChild(labelElement, labelTextElement);
  const valueElement: HTMLElement = renderer.createElement('div');
  const value = formatValue(seriesParams.value[1], decimals, units, false);
  renderer.appendChild(valueElement, renderer.createText(value));
  renderer.setStyle(valueElement, 'flex', '1');
  renderer.setStyle(valueElement, 'text-align', 'end');
  renderer.setStyle(valueElement, 'font-family', settings.tooltipValueFont.family);
  renderer.setStyle(valueElement, 'font-size', settings.tooltipValueFont.size + settings.tooltipValueFont.sizeUnit);
  renderer.setStyle(valueElement, 'font-style', settings.tooltipValueFont.style);
  renderer.setStyle(valueElement, 'font-weight', settings.tooltipValueFont.weight);
  renderer.setStyle(valueElement, 'line-height', settings.tooltipValueFont.lineHeight);
  renderer.setStyle(valueElement, 'color', settings.tooltipValueColor);
  renderer.appendChild(labelValueElement, valueElement);
  return labelValueElement;
};
