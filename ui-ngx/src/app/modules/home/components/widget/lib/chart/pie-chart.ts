///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import { pieChartDefaultSettings, PieChartSettings } from '@home/components/widget/lib/chart/pie-chart.models';
import { WidgetContext } from '@home/models/widget-component.models';
import { DeepPartial } from '@shared/models/common';
import { Renderer2 } from '@angular/core';
import { ColorProcessor, textStyle } from '@shared/models/widget-settings.models';
import { PieDataItemOption } from 'echarts/types/src/chart/pie/PieSeries';
import { Text } from '@svgdotjs/svg.js';
import { TranslateService } from '@ngx-translate/core';
import { TbLatestChart } from '@home/components/widget/lib/chart/latest-chart';
import { formatValue } from '@core/utils';
import { toAnimationOption } from '@home/components/widget/lib/chart/chart.models';

const shapeSize = 134;
const shapeSegmentWidth = 13.4;

export class TbPieChart extends TbLatestChart<PieChartSettings> {

  private totalValueColor: ColorProcessor;
  private totalTextNode: Text;

  constructor(ctx: WidgetContext,
              inputSettings: DeepPartial<PieChartSettings>,
              chartElement: HTMLElement,
              renderer: Renderer2,
              translate: TranslateService,
              autoResize = true) {

    super(ctx, inputSettings, chartElement, renderer, translate, autoResize);
  }

  protected defaultSettings(): PieChartSettings {
      return pieChartDefaultSettings;
  }

  protected initSettings() {
    if (this.settings.showTotal) {
      this.totalValueColor = ColorProcessor.fromSettings(this.settings.totalValueColor);
    }
  }

  protected prepareLatestChartOption() {
    const shapeWidth = this.chartElement.offsetWidth;
    const shapeHeight = this.chartElement.offsetHeight;
    const size = this.settings.autoScale ? shapeSize : Math.min(shapeWidth, shapeHeight);
    const innerRadius = size / 2 - shapeSegmentWidth;
    const outerRadius = size / 2;
    const labelStyle = textStyle(this.settings.labelFont);
    labelStyle.fontSize = this.settings.labelFont.size;
    labelStyle.lineHeight = labelStyle.fontSize * 1.2;
    labelStyle.color = this.settings.labelColor;
    this.latestChartOption.series = [
      {
        type: 'pie',
        clockwise: this.settings.clockwise,
        radius: this.settings.doughnut ? [innerRadius, outerRadius] : this.settings.radius,
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: this.settings.borderRadius,
          borderWidth: this.settings.borderWidth,
          borderColor: this.settings.borderColor
        },
        label: {
          show: this.settings.showLabel,
          position: this.settings.labelPosition,
          formatter: (params) => {
            const percents = params.percent;
            const value = formatValue(percents, 0, '%', false);
            return `{label|${params.name}\n${value}}`;
          },
          rich: {
            label: labelStyle
          }
        },
        emphasis: {
          scale: this.settings.emphasisScale,
          itemStyle: {
            borderColor: this.settings.emphasisBorderColor,
            borderWidth: this.settings.emphasisBorderWidth,
            shadowColor: this.settings.emphasisShadowColor,
            shadowBlur: this.settings.emphasisShadowBlur
          },
          label: {
            show: this.settings.showLabel
          }
        },
        ...toAnimationOption(this.ctx, this.settings.animation)
      }
    ];
  }

  protected afterDrawChart() {
    if (this.settings.showTotal) {
      this.totalTextNode = this.svgShape.text('').font({
        family: 'Roboto',
        leading: 1
      }).attr({'text-anchor': 'middle'});
      this.renderTotal();
    }
  };

  protected doUpdateSeriesData() {
    const seriesData: PieDataItemOption[] = [];
    const enabledDataItems = this.dataItems.filter(item => item.enabled && item.hasValue);
    for (const dataItem of this.dataItems) {
      if (dataItem.enabled && dataItem.hasValue) {
        seriesData.push(
          {id: dataItem.id, value: dataItem.value, name: dataItem.dataKey.label, itemStyle: {color: dataItem.dataKey.color}}
        );
      }
    }
    if (this.settings.doughnut) {
      this.latestChartOption.series[0].padAngle = enabledDataItems.length > 1 ? 2 : 0;
    }
    this.latestChartOption.series[0].data = seriesData;
  }

  protected afterUpdateSeriesData(initial: boolean) {
    if (this.settings.showTotal) {
      this.totalValueColor.update(this.total);
      if (!initial) {
        this.renderTotal();
      }
    }
  };

  protected initialShapeWidth(): number {
    return shapeSize;
  }

  protected initialShapeHeight(): number {
    return shapeSize;
  }

  protected beforeResize(shapeWidth: number, shapeHeight: number) {
    if (!this.settings.autoScale) {
      if (this.settings.doughnut) {
        const size = Math.min(shapeWidth, shapeHeight);
        const innerRadius = size / 2 - shapeSegmentWidth;
        const outerRadius = size / 2;
        this.latestChartOption.series[0].radius = [innerRadius, outerRadius];
        this.latestChart.setOption(this.latestChartOption);
      }
    }
  };

  protected afterResize(shapeWidth: number, shapeHeight: number) {
    if (this.settings.showTotal) {
      this.totalTextNode.center((this.settings.autoScale ? shapeSize : shapeWidth) / 2,
        (this.settings.autoScale ? shapeSize : shapeHeight) / 2);
    }
  };

  private renderTotal() {
    this.totalTextNode.text(add => {
      add.tspan(this.translate.instant('widgets.latest-chart.total')).font({size: '12px', weight: 400}).fill('rgba(0, 0, 0, 0.38)');
      add.tspan('').newLine().font({size: '4px'});
      add.tspan(this.totalText).newLine().font(
        {family: this.settings.totalValueFont.family,
          size: this.settings.totalValueFont.size + this.settings.totalValueFont.sizeUnit,
          weight: this.settings.totalValueFont.weight,
          style: this.settings.totalValueFont.style}
      ).fill(this.totalValueColor.color);
    }).center(this.svgShape.bbox().width / 2, this.svgShape.bbox().height / 2);
  }
}
