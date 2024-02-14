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
  legendPositions,
  legendPositionTranslationMap,
  WidgetSettings,
  WidgetSettingsComponent
} from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { formatValue } from '@core/utils';
import { DateFormatProcessor, DateFormatSettings } from '@shared/models/widget-settings.models';
import {
  barChartWithLabelsDefaultSettings
} from '@home/components/widget/lib/chart/bar-chart-with-labels-widget.models';

@Component({
  selector: 'tb-bar-chart-with-labels-widget-settings',
  templateUrl: './bar-chart-with-labels-widget-settings.component.html',
  styleUrls: []
})
export class BarChartWithLabelsWidgetSettingsComponent extends WidgetSettingsComponent {

  legendPositions = legendPositions;

  legendPositionTranslationMap = legendPositionTranslationMap;

  barChartWidgetSettingsForm: UntypedFormGroup;

  tooltipValuePreviewFn = this._tooltipValuePreviewFn.bind(this);

  tooltipDatePreviewFn = this._tooltipDatePreviewFn.bind(this);

  constructor(protected store: Store<AppState>,
              private $injector: Injector,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  protected settingsForm(): UntypedFormGroup {
    return this.barChartWidgetSettingsForm;
  }

  protected defaultSettings(): WidgetSettings {
    return {...barChartWithLabelsDefaultSettings};
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.barChartWidgetSettingsForm = this.fb.group({

      showBarLabel: [settings.showBarLabel, []],
      barLabelFont: [settings.barLabelFont, []],
      barLabelColor: [settings.barLabelColor, []],
      showBarValue: [settings.showBarValue, []],
      barValueFont: [settings.barValueFont, []],
      barValueColor: [settings.barValueColor, []],

      showLegend: [settings.showLegend, []],
      legendPosition: [settings.legendPosition, []],
      legendLabelFont: [settings.legendLabelFont, []],
      legendLabelColor: [settings.legendLabelColor, []],

      showTooltip: [settings.showTooltip, []],
      tooltipValueFont: [settings.tooltipValueFont, []],
      tooltipValueColor: [settings.tooltipValueColor, []],
      tooltipShowDate: [settings.tooltipShowDate, []],
      tooltipDateFormat: [settings.tooltipDateFormat, []],
      tooltipDateFont: [settings.tooltipDateFont, []],
      tooltipDateColor: [settings.tooltipDateColor, []],
      tooltipBackgroundColor: [settings.tooltipBackgroundColor, []],
      tooltipBackgroundBlur: [settings.tooltipBackgroundBlur, []],

      background: [settings.background, []]
    });
  }

  protected validatorTriggers(): string[] {
    return ['showBarLabel', 'showBarValue', 'showLegend', 'showTooltip', 'tooltipShowDate'];
  }

  protected updateValidators(emitEvent: boolean) {
    const showBarLabel: boolean = this.barChartWidgetSettingsForm.get('showBarLabel').value;
    const showBarValue: boolean = this.barChartWidgetSettingsForm.get('showBarValue').value;
    const showLegend: boolean = this.barChartWidgetSettingsForm.get('showLegend').value;
    const showTooltip: boolean = this.barChartWidgetSettingsForm.get('showTooltip').value;
    const tooltipShowDate: boolean = this.barChartWidgetSettingsForm.get('tooltipShowDate').value;

    if (showBarLabel) {
      this.barChartWidgetSettingsForm.get('barLabelFont').enable();
      this.barChartWidgetSettingsForm.get('barLabelColor').enable();
    } else {
      this.barChartWidgetSettingsForm.get('barLabelFont').disable();
      this.barChartWidgetSettingsForm.get('barLabelColor').disable();
    }

    if (showBarValue) {
      this.barChartWidgetSettingsForm.get('barValueFont').enable();
      this.barChartWidgetSettingsForm.get('barValueColor').enable();
    } else {
      this.barChartWidgetSettingsForm.get('barValueFont').disable();
      this.barChartWidgetSettingsForm.get('barValueColor').disable();
    }

    if (showLegend) {
      this.barChartWidgetSettingsForm.get('legendPosition').enable();
      this.barChartWidgetSettingsForm.get('legendLabelFont').enable();
      this.barChartWidgetSettingsForm.get('legendLabelColor').enable();
    } else {
      this.barChartWidgetSettingsForm.get('legendPosition').disable();
      this.barChartWidgetSettingsForm.get('legendLabelFont').disable();
      this.barChartWidgetSettingsForm.get('legendLabelColor').disable();
    }

    if (showTooltip) {
      this.barChartWidgetSettingsForm.get('tooltipValueFont').enable();
      this.barChartWidgetSettingsForm.get('tooltipValueColor').enable();
      this.barChartWidgetSettingsForm.get('tooltipShowDate').enable({emitEvent: false});
      this.barChartWidgetSettingsForm.get('tooltipBackgroundColor').enable();
      this.barChartWidgetSettingsForm.get('tooltipBackgroundBlur').enable();
      if (tooltipShowDate) {
        this.barChartWidgetSettingsForm.get('tooltipDateFormat').enable();
        this.barChartWidgetSettingsForm.get('tooltipDateFont').enable();
        this.barChartWidgetSettingsForm.get('tooltipDateColor').enable();
      } else {
        this.barChartWidgetSettingsForm.get('tooltipDateFormat').disable();
        this.barChartWidgetSettingsForm.get('tooltipDateFont').disable();
        this.barChartWidgetSettingsForm.get('tooltipDateColor').disable();
      }
    } else {
      this.barChartWidgetSettingsForm.get('tooltipValueFont').disable();
      this.barChartWidgetSettingsForm.get('tooltipValueColor').disable();
      this.barChartWidgetSettingsForm.get('tooltipShowDate').disable({emitEvent: false});
      this.barChartWidgetSettingsForm.get('tooltipDateFormat').disable();
      this.barChartWidgetSettingsForm.get('tooltipDateFont').disable();
      this.barChartWidgetSettingsForm.get('tooltipDateColor').disable();
      this.barChartWidgetSettingsForm.get('tooltipBackgroundColor').disable();
      this.barChartWidgetSettingsForm.get('tooltipBackgroundBlur').disable();
    }
  }

  private _tooltipValuePreviewFn(): string {
    const units: string = this.widgetConfig.config.units;
    const decimals: number = this.widgetConfig.config.decimals;
    return formatValue(22, decimals, units, false);
  }

  private _tooltipDatePreviewFn(): string {
    const dateFormat: DateFormatSettings = this.barChartWidgetSettingsForm.get('tooltipDateFormat').value;
    const processor = DateFormatProcessor.fromSettings(this.$injector, dateFormat);
    processor.update(Date.now());
    return processor.formatted;
  }

}
