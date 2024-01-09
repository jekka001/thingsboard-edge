///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  Input,
  OnDestroy,
  OnInit,
  Renderer2,
  TemplateRef,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import { WidgetContext } from '@home/models/widget-component.models';
import {
  backgroundStyle,
  ColorRange,
  ComponentStyle,
  DateFormatProcessor,
  filterIncludingColorRanges,
  getDataKey,
  overlayStyle,
  sortedColorRange,
  textStyle
} from '@shared/models/widget-settings.models';
import { ResizeObserver } from '@juggle/resize-observer';
import * as echarts from 'echarts/core';
import { formatValue, isDefinedAndNotNull, isNumber } from '@core/utils';
import { rangeChartDefaultSettings, RangeChartWidgetSettings } from './range-chart-widget.models';
import { Observable } from 'rxjs';
import { ImagePipe } from '@shared/pipe/image.pipe';
import { DomSanitizer } from '@angular/platform-browser';
import {
  ECharts,
  echartsModule,
  EChartsOption,
  echartsTooltipFormatter,
  toNamedData
} from '@home/components/widget/lib/chart/echarts-widget.models';
import { CallbackDataParams } from 'echarts/types/dist/shared';

interface VisualPiece {
  lt?: number;
  gt?: number;
  lte?: number;
  gte?: number;
  value?: number;
  color?: string;
}

interface RangeItem {
  index: number;
  from?: number;
  to?: number;
  piece: VisualPiece;
  color: string;
  label: string;
  visible: boolean;
  enabled: boolean;
}

const rangeItemLabel = (from?: number, to?: number): string => {
  if (isNumber(from) && isNumber(to)) {
    if (from === to) {
      return `${from}`;
    } else {
      return `${from} - ${to}`;
    }
  } else if (isNumber(from)) {
    return `≥ ${from}`;
  } else if (isNumber(to)) {
    return `< ${to}`;
  } else {
    return null;
  }
};

const toVisualPiece = (color: string, from?: number, to?: number): VisualPiece => {
  const piece: VisualPiece = {
    color
  };
  if (isNumber(from) && isNumber(to)) {
    if (from === to) {
      piece.value = from;
    } else {
      piece.gte = from;
      piece.lt = to;
    }
  } else if (isNumber(from)) {
    piece.gte = from;
  } else if (isNumber(to)) {
    piece.lt = to;
  }
  return piece;
};

const toRangeItems = (colorRanges: Array<ColorRange>): RangeItem[] => {
  const rangeItems: RangeItem[] = [];
  let counter = 0;
  const ranges = sortedColorRange(filterIncludingColorRanges(colorRanges)).filter(r => isNumber(r.from) || isNumber(r.to));
  for (let i = 0; i < ranges.length; i++) {
    const range = ranges[i];
    let from = range.from;
    const to = range.to;
    if (i > 0) {
      const prevRange = ranges[i - 1];
      if (isNumber(prevRange.to) && isNumber(from) && from < prevRange.to) {
        from = prevRange.to;
      }
    }
    rangeItems.push(
      {
        index: counter++,
        color: range.color,
        enabled: true,
        visible: true,
        from,
        to,
        label: rangeItemLabel(from, to),
        piece: toVisualPiece(range.color, from, to)
      }
    );
    if (!isNumber(from) || !isNumber(to)) {
      const value = !isNumber(from) ? to : from;
      rangeItems.push(
        {
          index: counter++,
          color: 'transparent',
          enabled: true,
          visible: false,
          label: '',
          piece: { gt: value - 0.000000001, lt: value + 0.000000001, color: 'transparent'}
        }
      );
    }
  }
  return rangeItems;
};

const getMarkPoints = (ranges: Array<RangeItem>): number[] => {
  const points = new Set<number>();
  for (const range of ranges) {
    if (range.visible) {
      if (isNumber(range.from)) {
        points.add(range.from);
      }
      if (isNumber(range.to)) {
        points.add(range.to);
      }
    }
  }
  return Array.from(points).sort();
};

@Component({
  selector: 'tb-range-chart-widget',
  templateUrl: './range-chart-widget.component.html',
  styleUrls: ['./range-chart-widget.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class RangeChartWidgetComponent implements OnInit, OnDestroy, AfterViewInit {

  @ViewChild('chartShape', {static: false})
  chartShape: ElementRef<HTMLElement>;

  settings: RangeChartWidgetSettings;

  @Input()
  ctx: WidgetContext;

  @Input()
  widgetTitlePanel: TemplateRef<any>;

  showLegend: boolean;
  legendClass: string;

  backgroundStyle$: Observable<ComponentStyle>;
  overlayStyle: ComponentStyle = {};

  legendLabelStyle: ComponentStyle;
  disabledLegendLabelStyle: ComponentStyle;
  visibleRangeItems: RangeItem[];

  private rangeItems: RangeItem[];

  private shapeResize$: ResizeObserver;

  private decimals = 0;
  private units = '';

  private drawChartPending = false;
  private rangeChart: ECharts;
  private rangeChartOptions: EChartsOption;
  private selectedRanges: {[key: number]: boolean} = {};

  private tooltipDateFormat: DateFormatProcessor;

  constructor(private imagePipe: ImagePipe,
              private sanitizer: DomSanitizer,
              private renderer: Renderer2,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit(): void {
    this.ctx.$scope.rangeChartWidget = this;
    this.settings = {...rangeChartDefaultSettings, ...this.ctx.settings};

    this.decimals = this.ctx.decimals;
    this.units = this.ctx.units;
    const dataKey = getDataKey(this.ctx.datasources);
    if (isDefinedAndNotNull(dataKey?.decimals)) {
      this.decimals = dataKey.decimals;
    }
    if (dataKey?.units) {
      this.units = dataKey.units;
    }


    this.backgroundStyle$ = backgroundStyle(this.settings.background, this.imagePipe, this.sanitizer);
    this.overlayStyle = overlayStyle(this.settings.background.overlay);

    this.rangeItems = toRangeItems(this.settings.rangeColors);
    this.visibleRangeItems = this.rangeItems.filter(item => item.visible);
    for (const range of this.rangeItems) {
      this.selectedRanges[range.index] = true;
    }

    this.showLegend = this.settings.showLegend && !!this.rangeItems.length;

    if (this.showLegend) {
      this.legendClass = `legend-${this.settings.legendPosition}`;
      this.legendLabelStyle = textStyle(this.settings.legendLabelFont);
      this.disabledLegendLabelStyle = textStyle(this.settings.legendLabelFont);
      this.legendLabelStyle.color = this.settings.legendLabelColor;
    }

    if (this.settings.showTooltip && this.settings.tooltipShowDate) {
      this.tooltipDateFormat = DateFormatProcessor.fromSettings(this.ctx.$injector, this.settings.tooltipDateFormat);
    }
  }

  ngAfterViewInit() {
    if (this.drawChartPending) {
      this.drawChart();
    }
  }

  ngOnDestroy() {
    if (this.shapeResize$) {
      this.shapeResize$.disconnect();
    }
    if (this.rangeChart) {
      this.rangeChart.dispose();
    }
  }

  public onInit() {
    const borderRadius = this.ctx.$widgetElement.css('borderRadius');
    this.overlayStyle = {...this.overlayStyle, ...{borderRadius}};
    if (this.chartShape) {
      this.drawChart();
    } else {
      this.drawChartPending = true;
    }
    this.cd.detectChanges();
  }

  public onDataUpdated() {
    if (this.rangeChart) {
      this.rangeChart.setOption({
        xAxis: {
          min: this.ctx.defaultSubscription.timeWindow.minTime,
          max: this.ctx.defaultSubscription.timeWindow.maxTime
        },
        series: [
          {data: this.ctx.data?.length ? toNamedData(this.ctx.data[0].data) : []}
        ],
        visualMap: {
          selected: this.selectedRanges
        }
      });
    }
  }

  public toggleRangeItem(item: RangeItem) {
    item.enabled = !item.enabled;
    this.selectedRanges[item.index] = item.enabled;
    this.rangeChart.dispatchAction({
      type: 'selectDataRange',
      selected: this.selectedRanges
    });
  }

  private drawChart() {
    echartsModule.init();
    const dataKey = getDataKey(this.ctx.datasources);
    this.rangeChart = echarts.init(this.chartShape.nativeElement, null, {
      renderer: 'canvas',
    });
    this.rangeChartOptions = {
      tooltip: {
        trigger: 'none'
      },
      grid: {
        containLabel: true,
        top: '30',
        left: 0,
        right: 0,
        bottom: this.settings.dataZoom ? 60 : 0
      },
      xAxis: {
        type: 'time',
        axisTick: {
          show: true
        },
        axisLabel: {
          hideOverlap: true,
          fontSize: 10
        },
        axisLine: {
          onZero: false
        },
        min: this.ctx.defaultSubscription.timeWindow.minTime,
        max: this.ctx.defaultSubscription.timeWindow.maxTime
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value: any) => formatValue(value, this.decimals, this.units, false)
        }
      },
      series: [{
        type: 'line',
        name: dataKey?.label,
        smooth: false,
        showSymbol: false,
        animation: true,
        areaStyle: this.settings.fillArea ? {} : undefined,
        data: this.ctx.data?.length ? toNamedData(this.ctx.data[0].data) : [],
        markLine: this.rangeItems.length ? {
          animation: true,
          symbol: ['circle', 'arrow'],
          symbolSize: [5, 7],
          lineStyle: {
            width: 1,
            type: [3, 3],
            color: '#37383b'
          },
          label: {
            position: 'insideEndTop',
            color: '#37383b',
            backgroundColor: 'rgba(255,255,255,0.56)',
            padding: [4, 5],
            borderRadius: 4,
            formatter: params => formatValue(params.value, this.decimals, this.units, false)
          },
          emphasis: {
            disabled: true
          },
          data: getMarkPoints(this.rangeItems).map(point => ({ yAxis: point }))
        } : undefined
      }],
      dataZoom: [
        {
          type: 'inside',
          disabled: !this.settings.dataZoom
        },
        {
          type: 'slider',
          show: this.settings.dataZoom,
          showDetail: false,
          right: 10
        }
      ],
      visualMap: {
        show: false,
        type: 'piecewise',
        selected: this.selectedRanges,
        pieces: this.rangeItems.map(item => item.piece),
        outOfRange: {
          color: this.settings.outOfRangeColor
        },
        inRange: !this.rangeItems.length ? {
          color: this.settings.outOfRangeColor
        } : undefined
      }
    };

    if (this.settings.showTooltip) {
      this.rangeChartOptions.tooltip = {
        trigger: 'axis',
        formatter: (params: CallbackDataParams[]) => echartsTooltipFormatter(this.renderer, this.tooltipDateFormat,
            this.settings, params, this.decimals, this.units, 0),
        padding: [8, 12],
        backgroundColor: this.settings.tooltipBackgroundColor,
        borderWidth: 0,
        extraCssText: `line-height: 1; backdrop-filter: blur(${this.settings.tooltipBackgroundBlur}px);`
      };
    }

    this.rangeChart.setOption(this.rangeChartOptions);

    this.shapeResize$ = new ResizeObserver(() => {
      this.onResize();
    });
    this.shapeResize$.observe(this.chartShape.nativeElement);
    this.onResize();
  }

  private onResize() {
    const width = this.rangeChart.getWidth();
    const height = this.rangeChart.getHeight();
    const shapeWidth = this.chartShape.nativeElement.offsetWidth;
    const shapeHeight = this.chartShape.nativeElement.offsetHeight;
    if (width !== shapeWidth || height !== shapeHeight) {
      this.rangeChart.resize();
    }
  }
}
