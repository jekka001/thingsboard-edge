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
  Datasource,
  legendPositions,
  legendPositionTranslationMap,
  WidgetSettings,
  WidgetSettingsComponent
} from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { formatValue, mergeDeep } from '@core/utils';
import {
  rangeChartDefaultSettings,
  RangeChartWidgetSettings
} from '@home/components/widget/lib/chart/range-chart-widget.models';
import { DateFormatProcessor, DateFormatSettings } from '@shared/models/widget-settings.models';
import {
  lineSeriesStepTypes,
  lineSeriesStepTypeTranslations,
  timeSeriesLineTypes,
  timeSeriesLineTypeTranslations
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { chartLabelPositions, chartLabelPositionTranslations, chartShapes, chartShapeTranslations } from '@home/components/widget/lib/chart/chart.models';

@Component({
  selector: 'tb-range-chart-widget-settings',
  templateUrl: './range-chart-widget-settings.component.html',
  styleUrls: []
})
export class RangeChartWidgetSettingsComponent extends WidgetSettingsComponent {

  public get datasource(): Datasource {
    const datasources: Datasource[] = this.widgetConfig.config.datasources;
    if (datasources && datasources.length) {
      return datasources[0];
    } else {
      return null;
    }
  }

  lineSeriesStepTypes = lineSeriesStepTypes;

  lineSeriesStepTypeTranslations = lineSeriesStepTypeTranslations;

  timeSeriesLineTypes = timeSeriesLineTypes;

  timeSeriesLineTypeTranslations = timeSeriesLineTypeTranslations;

  chartLabelPositions = chartLabelPositions;

  chartLabelPositionTranslations = chartLabelPositionTranslations;

  chartShapes = chartShapes;

  chartShapeTranslations = chartShapeTranslations;

  legendPositions = legendPositions;

  legendPositionTranslationMap = legendPositionTranslationMap;

  rangeChartWidgetSettingsForm: UntypedFormGroup;

  pointLabelPreviewFn = this._pointLabelPreviewFn.bind(this);

  tooltipValuePreviewFn = this._tooltipValuePreviewFn.bind(this);

  tooltipDatePreviewFn = this._tooltipDatePreviewFn.bind(this);

  constructor(protected store: Store<AppState>,
              private $injector: Injector,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  protected settingsForm(): UntypedFormGroup {
    return this.rangeChartWidgetSettingsForm;
  }

  protected defaultSettings(): WidgetSettings {
    return mergeDeep<RangeChartWidgetSettings>({} as RangeChartWidgetSettings, rangeChartDefaultSettings);
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.rangeChartWidgetSettingsForm = this.fb.group({
      dataZoom: [settings.dataZoom, []],
      rangeColors: [settings.rangeColors, []],
      outOfRangeColor: [settings.outOfRangeColor, []],
      showRangeThresholds: [settings.showRangeThresholds, []],
      rangeThreshold: [settings.rangeThreshold, []],
      fillArea: [settings.fillArea, []],
      fillAreaOpacity: [settings.fillAreaOpacity, [Validators.min(0), Validators.max(1)]],

      showLine: [settings.showLine, []],
      step: [settings.step, []],
      stepType: [settings.stepType, []],
      smooth: [settings.smooth, []],
      lineType: [settings.lineType, []],
      lineWidth: [settings.lineWidth, [Validators.min(0)]],

      showPoints: [settings.showPoints, []],
      showPointLabel: [settings.showPointLabel, []],
      pointLabelPosition: [settings.pointLabelPosition, []],
      pointLabelFont: [settings.pointLabelFont, []],
      pointLabelColor: [settings.pointLabelColor, []],
      enablePointLabelBackground: [settings.enablePointLabelBackground, []],
      pointLabelBackground: [settings.pointLabelBackground, []],
      pointShape: [settings.pointShape, []],
      pointSize: [settings.pointSize, [Validators.min(0)]],

      grid: [settings.grid, []],

      yAxis: [settings.yAxis, []],
      xAxis: [settings.xAxis, []],

      thresholds: [settings.thresholds, []],

      animation: [settings.animation, []],

      showLegend: [settings.showLegend, []],
      legendPosition: [settings.legendPosition, []],
      legendLabelFont: [settings.legendLabelFont, []],
      legendLabelColor: [settings.legendLabelColor, []],

      showTooltip: [settings.showTooltip, []],
      tooltipLabelFont: [settings.tooltipLabelFont, []],
      tooltipLabelColor: [settings.tooltipLabelColor, []],
      tooltipValueFont: [settings.tooltipValueFont, []],
      tooltipValueColor: [settings.tooltipValueColor, []],
      tooltipShowDate: [settings.tooltipShowDate, []],
      tooltipDateFormat: [settings.tooltipDateFormat, []],
      tooltipDateFont: [settings.tooltipDateFont, []],
      tooltipDateColor: [settings.tooltipDateColor, []],
      tooltipDateInterval: [settings.tooltipDateInterval, []],

      tooltipBackgroundColor: [settings.tooltipBackgroundColor, []],
      tooltipBackgroundBlur: [settings.tooltipBackgroundBlur, []],

      background: [settings.background, []],
      padding: [settings.padding, []]
    });
  }

  protected validatorTriggers(): string[] {
    return ['showRangeThresholds', 'fillArea', 'showLine', 'step', 'showPointLabel', 'enablePointLabelBackground',
      'showLegend', 'showTooltip', 'tooltipShowDate'];
  }

  protected updateValidators() {
    const showRangeThresholds: boolean = this.rangeChartWidgetSettingsForm.get('showRangeThresholds').value;
    const fillArea: boolean = this.rangeChartWidgetSettingsForm.get('fillArea').value;
    const showLine: boolean = this.rangeChartWidgetSettingsForm.get('showLine').value;
    const step: boolean = this.rangeChartWidgetSettingsForm.get('step').value;
    const showPointLabel: boolean = this.rangeChartWidgetSettingsForm.get('showPointLabel').value;
    const enablePointLabelBackground: boolean = this.rangeChartWidgetSettingsForm.get('enablePointLabelBackground').value;
    const showLegend: boolean = this.rangeChartWidgetSettingsForm.get('showLegend').value;
    const showTooltip: boolean = this.rangeChartWidgetSettingsForm.get('showTooltip').value;
    const tooltipShowDate: boolean = this.rangeChartWidgetSettingsForm.get('tooltipShowDate').value;

    if (showRangeThresholds) {
      this.rangeChartWidgetSettingsForm.get('rangeThreshold').enable();
    } else {
      this.rangeChartWidgetSettingsForm.get('rangeThreshold').disable();
    }

    if (fillArea) {
      this.rangeChartWidgetSettingsForm.get('fillAreaOpacity').enable();
    } else {
      this.rangeChartWidgetSettingsForm.get('fillAreaOpacity').disable();
    }

    if (showLine) {
      this.rangeChartWidgetSettingsForm.get('step').enable({emitEvent: false});
      if (step) {
        this.rangeChartWidgetSettingsForm.get('stepType').enable();
        this.rangeChartWidgetSettingsForm.get('smooth').disable();
      } else {
        this.rangeChartWidgetSettingsForm.get('stepType').disable();
        this.rangeChartWidgetSettingsForm.get('smooth').enable();
      }
      this.rangeChartWidgetSettingsForm.get('lineType').enable();
      this.rangeChartWidgetSettingsForm.get('lineWidth').enable();
    } else {
      this.rangeChartWidgetSettingsForm.get('step').disable({emitEvent: false});
      this.rangeChartWidgetSettingsForm.get('stepType').disable();
      this.rangeChartWidgetSettingsForm.get('smooth').disable();
      this.rangeChartWidgetSettingsForm.get('lineType').disable();
      this.rangeChartWidgetSettingsForm.get('lineWidth').disable();
    }
    if (showPointLabel) {
      this.rangeChartWidgetSettingsForm.get('pointLabelPosition').enable();
      this.rangeChartWidgetSettingsForm.get('pointLabelFont').enable();
      this.rangeChartWidgetSettingsForm.get('pointLabelColor').enable();
      this.rangeChartWidgetSettingsForm.get('enablePointLabelBackground').enable({emitEvent: false});
      if (enablePointLabelBackground) {
        this.rangeChartWidgetSettingsForm.get('pointLabelBackground').enable();
      } else {
        this.rangeChartWidgetSettingsForm.get('pointLabelBackground').disable();
      }
    } else {
      this.rangeChartWidgetSettingsForm.get('pointLabelPosition').disable();
      this.rangeChartWidgetSettingsForm.get('pointLabelFont').disable();
      this.rangeChartWidgetSettingsForm.get('pointLabelColor').disable();
      this.rangeChartWidgetSettingsForm.get('enablePointLabelBackground').disable({emitEvent: false});
      this.rangeChartWidgetSettingsForm.get('pointLabelBackground').disable();
    }

    if (showLegend) {
      this.rangeChartWidgetSettingsForm.get('legendPosition').enable();
      this.rangeChartWidgetSettingsForm.get('legendLabelFont').enable();
      this.rangeChartWidgetSettingsForm.get('legendLabelColor').enable();
    } else {
      this.rangeChartWidgetSettingsForm.get('legendPosition').disable();
      this.rangeChartWidgetSettingsForm.get('legendLabelFont').disable();
      this.rangeChartWidgetSettingsForm.get('legendLabelColor').disable();
    }

    if (showTooltip) {
      this.rangeChartWidgetSettingsForm.get('tooltipLabelFont').enable();
      this.rangeChartWidgetSettingsForm.get('tooltipLabelColor').enable();
      this.rangeChartWidgetSettingsForm.get('tooltipValueFont').enable();
      this.rangeChartWidgetSettingsForm.get('tooltipValueColor').enable();
      this.rangeChartWidgetSettingsForm.get('tooltipShowDate').enable({emitEvent: false});
      this.rangeChartWidgetSettingsForm.get('tooltipBackgroundColor').enable();
      this.rangeChartWidgetSettingsForm.get('tooltipBackgroundBlur').enable();
      if (tooltipShowDate) {
        this.rangeChartWidgetSettingsForm.get('tooltipDateFormat').enable();
        this.rangeChartWidgetSettingsForm.get('tooltipDateFont').enable();
        this.rangeChartWidgetSettingsForm.get('tooltipDateColor').enable();
        this.rangeChartWidgetSettingsForm.get('tooltipDateInterval').enable();
      } else {
        this.rangeChartWidgetSettingsForm.get('tooltipDateFormat').disable();
        this.rangeChartWidgetSettingsForm.get('tooltipDateFont').disable();
        this.rangeChartWidgetSettingsForm.get('tooltipDateColor').disable();
        this.rangeChartWidgetSettingsForm.get('tooltipDateInterval').disable();
      }
    } else {
      this.rangeChartWidgetSettingsForm.get('tooltipLabelFont').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipLabelColor').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipValueFont').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipValueColor').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipShowDate').disable({emitEvent: false});
      this.rangeChartWidgetSettingsForm.get('tooltipDateFormat').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipDateFont').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipDateColor').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipDateInterval').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipBackgroundColor').disable();
      this.rangeChartWidgetSettingsForm.get('tooltipBackgroundBlur').disable();
    }
  }

  private _pointLabelPreviewFn(): string {
    const units: string = this.widgetConfig.config.units;
    const decimals: number = this.widgetConfig.config.decimals;
    return formatValue(22, decimals, units, false);
  }

  private _tooltipValuePreviewFn(): string {
    const units: string = this.widgetConfig.config.units;
    const decimals: number = this.widgetConfig.config.decimals;
    return formatValue(22, decimals, units, false);
  }

  private _tooltipDatePreviewFn(): string {
    const dateFormat: DateFormatSettings = this.rangeChartWidgetSettingsForm.get('tooltipDateFormat').value;
    const processor = DateFormatProcessor.fromSettings(this.$injector, dateFormat);
    processor.update(Date.now());
    return processor.formatted;
  }
}
