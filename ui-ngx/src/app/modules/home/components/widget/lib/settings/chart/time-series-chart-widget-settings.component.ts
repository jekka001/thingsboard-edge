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

import { Component, Injector } from '@angular/core';
import {
  DataKey,
  Datasource,
  legendPositions,
  legendPositionTranslationMap,
  WidgetSettings,
  WidgetSettingsComponent
} from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { formatValue, isDefinedAndNotNull, mergeDeep } from '@core/utils';
import { DateFormatProcessor, DateFormatSettings } from '@shared/models/widget-settings.models';
import { EChartsTooltipTrigger } from '../../chart/echarts-widget.models';
import {
  timeSeriesChartWidgetDefaultSettings,
  TimeSeriesChartWidgetSettings
} from '@home/components/widget/lib/chart/time-series-chart-widget.models';
import {
  TimeSeriesChartKeySettings,
  TimeSeriesChartType,
  TimeSeriesChartYAxes,
  TimeSeriesChartYAxisId
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { WidgetConfigComponentData } from '@home/models/widget-component.models';
import { WidgetService } from '@core/http/widget.service';

@Component({
  selector: 'tb-time-series-chart-widget-settings',
  templateUrl: './time-series-chart-widget-settings.component.html',
  styleUrls: ['./../widget-settings.scss']
})
export class TimeSeriesChartWidgetSettingsComponent extends WidgetSettingsComponent {

  public get datasource(): Datasource {
    const datasources: Datasource[] = this.widgetConfig.config.datasources;
    if (datasources && datasources.length) {
      return datasources[0];
    } else {
      return null;
    }
  }

  public get yAxisIds(): TimeSeriesChartYAxisId[] {
    const yAxes: TimeSeriesChartYAxes = this.timeSeriesChartWidgetSettingsForm.get('yAxes').value;
    return Object.keys(yAxes);
  }

  TimeSeriesChartType = TimeSeriesChartType;

  EChartsTooltipTrigger = EChartsTooltipTrigger;

  legendPositions = legendPositions;

  legendPositionTranslationMap = legendPositionTranslationMap;

  timeSeriesChartWidgetSettingsForm: UntypedFormGroup;

  tooltipValuePreviewFn = this._tooltipValuePreviewFn.bind(this);

  tooltipDatePreviewFn = this._tooltipDatePreviewFn.bind(this);

  chartType: TimeSeriesChartType = TimeSeriesChartType.default;

  functionScopeVariables = this.widgetService.getWidgetScopeVariables();

  constructor(protected store: Store<AppState>,
              private $injector: Injector,
              private widgetService: WidgetService,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  public yAxisRemoved(yAxisId: TimeSeriesChartYAxisId): void {
    if (this.widgetConfig.config.datasources && this.widgetConfig.config.datasources.length > 1) {
      for (let i = 1; i < this.widgetConfig.config.datasources.length; i++) {
        const datasource = this.widgetConfig.config.datasources[i];
        this.removeYaxisId(datasource.dataKeys, yAxisId);
      }
    }
  }

  protected settingsForm(): UntypedFormGroup {
    return this.timeSeriesChartWidgetSettingsForm;
  }

  protected onWidgetConfigSet(widgetConfig: WidgetConfigComponentData) {
    const params = widgetConfig.typeParameters as any;
    if (isDefinedAndNotNull(params.chartType)) {
      this.chartType = params.chartType;
    }
  }

  protected defaultSettings(): WidgetSettings {
    return mergeDeep<TimeSeriesChartWidgetSettings>({} as TimeSeriesChartWidgetSettings, timeSeriesChartWidgetDefaultSettings);
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.timeSeriesChartWidgetSettingsForm = this.fb.group({

      comparisonEnabled: [settings.comparisonEnabled, []],
      timeForComparison: [settings.timeForComparison, []],
      comparisonCustomIntervalValue: [settings.comparisonCustomIntervalValue, [Validators.min(0)]],
      comparisonXAxis: [settings.comparisonXAxis, []],

      yAxes: [settings.yAxes, []],
      thresholds: [settings.thresholds, []],

      dataZoom: [settings.dataZoom, []],
      stack: [settings.stack, []],

      grid: [settings.grid, []],

      xAxis: [settings.xAxis, []],

      noAggregationBarWidthSettings: [settings.noAggregationBarWidthSettings, []],

      showLegend: [settings.showLegend, []],
      legendColumnTitleFont: [settings.legendColumnTitleFont, []],
      legendColumnTitleColor: [settings.legendColumnTitleColor, []],
      legendLabelFont: [settings.legendLabelFont, []],
      legendLabelColor: [settings.legendLabelColor, []],
      legendValueFont: [settings.legendValueFont, []],
      legendValueColor: [settings.legendValueColor, []],
      legendConfig: [settings.legendConfig, []],

      showTooltip: [settings.showTooltip, []],
      tooltipTrigger: [settings.tooltipTrigger, []],
      tooltipLabelFont: [settings.tooltipLabelFont, []],
      tooltipLabelColor: [settings.tooltipLabelColor, []],
      tooltipValueFont: [settings.tooltipValueFont, []],
      tooltipValueColor: [settings.tooltipValueColor, []],
      tooltipValueFormatter: [settings.tooltipValueFormatter, []],
      tooltipShowDate: [settings.tooltipShowDate, []],
      tooltipDateFormat: [settings.tooltipDateFormat, []],
      tooltipDateFont: [settings.tooltipDateFont, []],
      tooltipDateColor: [settings.tooltipDateColor, []],
      tooltipDateInterval: [settings.tooltipDateInterval, []],

      tooltipBackgroundColor: [settings.tooltipBackgroundColor, []],
      tooltipBackgroundBlur: [settings.tooltipBackgroundBlur, []],

      animation: [settings.animation, []],

      background: [settings.background, []],
      padding: [settings.padding, []]
    });
    if (this.chartType === TimeSeriesChartType.state) {
      this.timeSeriesChartWidgetSettingsForm.addControl('states', this.fb.control(settings.states, []));
    }
  }

  protected validatorTriggers(): string[] {
    return ['comparisonEnabled', 'showLegend', 'showTooltip', 'tooltipShowDate'];
  }

  protected updateValidators(emitEvent: boolean) {
    const comparisonEnabled: boolean = this.timeSeriesChartWidgetSettingsForm.get('comparisonEnabled').value;
    const showLegend: boolean = this.timeSeriesChartWidgetSettingsForm.get('showLegend').value;
    const showTooltip: boolean = this.timeSeriesChartWidgetSettingsForm.get('showTooltip').value;
    const tooltipShowDate: boolean = this.timeSeriesChartWidgetSettingsForm.get('tooltipShowDate').value;

    if (comparisonEnabled) {
      this.timeSeriesChartWidgetSettingsForm.get('timeForComparison').enable();
      this.timeSeriesChartWidgetSettingsForm.get('comparisonCustomIntervalValue').enable();
      this.timeSeriesChartWidgetSettingsForm.get('comparisonXAxis').enable();
    } else {
      this.timeSeriesChartWidgetSettingsForm.get('timeForComparison').disable();
      this.timeSeriesChartWidgetSettingsForm.get('comparisonCustomIntervalValue').disable();
      this.timeSeriesChartWidgetSettingsForm.get('comparisonXAxis').disable();
    }

    if (showLegend) {
      this.timeSeriesChartWidgetSettingsForm.get('legendColumnTitleFont').enable();
      this.timeSeriesChartWidgetSettingsForm.get('legendColumnTitleColor').enable();
      this.timeSeriesChartWidgetSettingsForm.get('legendLabelFont').enable();
      this.timeSeriesChartWidgetSettingsForm.get('legendLabelColor').enable();
      this.timeSeriesChartWidgetSettingsForm.get('legendValueFont').enable();
      this.timeSeriesChartWidgetSettingsForm.get('legendValueColor').enable();
      this.timeSeriesChartWidgetSettingsForm.get('legendConfig').enable();
    } else {
      this.timeSeriesChartWidgetSettingsForm.get('legendColumnTitleFont').disable();
      this.timeSeriesChartWidgetSettingsForm.get('legendColumnTitleColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('legendLabelFont').disable();
      this.timeSeriesChartWidgetSettingsForm.get('legendLabelColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('legendValueFont').disable();
      this.timeSeriesChartWidgetSettingsForm.get('legendValueColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('legendConfig').disable();
    }

    if (showTooltip) {
      this.timeSeriesChartWidgetSettingsForm.get('tooltipTrigger').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipLabelFont').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipLabelColor').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipValueFont').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipValueColor').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipValueFormatter').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipShowDate').enable({emitEvent: false});
      this.timeSeriesChartWidgetSettingsForm.get('tooltipBackgroundColor').enable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipBackgroundBlur').enable();
      if (tooltipShowDate) {
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFormat').enable();
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFont').enable();
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateColor').enable();
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateInterval').enable();
      } else {
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFormat').disable();
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFont').disable();
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateColor').disable();
        this.timeSeriesChartWidgetSettingsForm.get('tooltipDateInterval').disable();
      }
    } else {
      this.timeSeriesChartWidgetSettingsForm.get('tooltipTrigger').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipLabelFont').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipLabelColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipValueFont').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipValueColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipValueFormatter').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipShowDate').disable({emitEvent: false});
      this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFormat').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFont').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipDateColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipDateInterval').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipBackgroundColor').disable();
      this.timeSeriesChartWidgetSettingsForm.get('tooltipBackgroundBlur').disable();
    }
  }

  private removeYaxisId(series: DataKey[], yAxisId: TimeSeriesChartYAxisId): boolean {
    let changed = false;
    if (series) {
      series.forEach(key => {
        const keySettings = ((key.settings || {}) as TimeSeriesChartKeySettings);
        if (keySettings.yAxisId === yAxisId) {
          keySettings.yAxisId = 'default';
          changed = true;
        }
      });
    }
    return changed;
  }

  private _tooltipValuePreviewFn(): string {
    return formatValue(22, 0, '°C', false);
  }

  private _tooltipDatePreviewFn(): string {
    const dateFormat: DateFormatSettings = this.timeSeriesChartWidgetSettingsForm.get('tooltipDateFormat').value;
    const processor = DateFormatProcessor.fromSettings(this.$injector, dateFormat);
    processor.update(Date.now());
    return processor.formatted;
  }
}
