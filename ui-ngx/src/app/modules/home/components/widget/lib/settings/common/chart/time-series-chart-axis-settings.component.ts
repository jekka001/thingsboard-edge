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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators
} from '@angular/forms';
import {
  AxisPosition,
  timeSeriesAxisPositionTranslations,
  TimeSeriesChartAxisSettings,
  TimeSeriesChartYAxisSettings
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { merge } from 'rxjs';
import { coerceBoolean } from '@shared/decorators/coercion';
import { WidgetService } from '@core/http/widget.service';

@Component({
  selector: 'tb-time-series-chart-axis-settings',
  templateUrl: './time-series-chart-axis-settings.component.html',
  styleUrls: ['./../../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TimeSeriesChartAxisSettingsComponent),
      multi: true
    }
  ]
})
export class TimeSeriesChartAxisSettingsComponent implements OnInit, ControlValueAccessor {

  @Input()
  @coerceBoolean()
  alwaysExpanded = false;

  settingsExpanded = false;

  axisPositions: AxisPosition[];

  timeSeriesAxisPositionTranslations = timeSeriesAxisPositionTranslations;

  functionScopeVariables = this.widgetService.getWidgetScopeVariables();

  @Input()
  disabled: boolean;

  @Input()
  axisType: 'xAxis' | 'yAxis' = 'xAxis';

  @Input()
  @coerceBoolean()
  advanced = false;

  private modelValue: TimeSeriesChartAxisSettings | TimeSeriesChartYAxisSettings;

  private propagateChange = null;

  public axisSettingsFormGroup: UntypedFormGroup;

  constructor(private fb: UntypedFormBuilder,
              private widgetService: WidgetService,) {
  }

  ngOnInit(): void {

    this.axisPositions = this.axisType === 'xAxis' ? [AxisPosition.top, AxisPosition.bottom] :
      [AxisPosition.left, AxisPosition.right];

    this.axisSettingsFormGroup = this.fb.group({
      show: [null, []],
      label: [null, []],
      labelFont: [null, []],
      labelColor: [null, []],
      position: [null, []],
      showTickLabels: [null, []],
      tickLabelFont: [null, []],
      tickLabelColor: [null, []],
      showTicks: [null, []],
      ticksColor: [null, []],
      showLine: [null, []],
      lineColor: [null, []],
      showSplitLines: [null, []],
      splitLinesColor: [null, []]
    });
    if (this.axisType === 'yAxis') {
      this.axisSettingsFormGroup.addControl('units', this.fb.control(null, []));
      this.axisSettingsFormGroup.addControl('decimals', this.fb.control(null, [Validators.min(0)]));
      this.axisSettingsFormGroup.addControl('ticksFormatter', this.fb.control(null, []));
      this.axisSettingsFormGroup.addControl('min', this.fb.control(null, []));
      this.axisSettingsFormGroup.addControl('max', this.fb.control(null, []));
    }
    this.axisSettingsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    merge(this.axisSettingsFormGroup.get('show').valueChanges,
          this.axisSettingsFormGroup.get('showTickLabels').valueChanges,
          this.axisSettingsFormGroup.get('showTicks').valueChanges,
          this.axisSettingsFormGroup.get('showLine').valueChanges,
          this.axisSettingsFormGroup.get('showSplitLines').valueChanges)
    .subscribe(() => {
      this.updateValidators();
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(_fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.axisSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.axisSettingsFormGroup.enable({emitEvent: false});
      this.updateValidators();
    }
  }

  writeValue(value: TimeSeriesChartAxisSettings | TimeSeriesChartYAxisSettings): void {
    this.modelValue = value;
    this.axisSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
    this.updateValidators();
    this.axisSettingsFormGroup.get('show').valueChanges.subscribe((show) => {
      this.settingsExpanded = show;
    });
  }

  private updateValidators() {
    const show: boolean = this.axisSettingsFormGroup.get('show').value;
    const showTickLabels: boolean = this.axisSettingsFormGroup.get('showTickLabels').value;
    const showTicks: boolean = this.axisSettingsFormGroup.get('showTicks').value;
    const showLine: boolean = this.axisSettingsFormGroup.get('showLine').value;
    const showSplitLines: boolean = this.axisSettingsFormGroup.get('showSplitLines').value;
    if (show) {
      this.axisSettingsFormGroup.enable({emitEvent: false});
      if (showTickLabels) {
        this.axisSettingsFormGroup.get('tickLabelFont').enable({emitEvent: false});
        this.axisSettingsFormGroup.get('tickLabelColor').enable({emitEvent: false});
        if (this.axisType === 'yAxis') {
          this.axisSettingsFormGroup.get('ticksFormatter').enable({emitEvent: false});
        }
      } else {
        this.axisSettingsFormGroup.get('tickLabelFont').disable({emitEvent: false});
        this.axisSettingsFormGroup.get('tickLabelColor').disable({emitEvent: false});
        if (this.axisType === 'yAxis') {
          this.axisSettingsFormGroup.get('ticksFormatter').disable({emitEvent: false});
        }
      }
      if (showTicks) {
        this.axisSettingsFormGroup.get('ticksColor').enable({emitEvent: false});
      } else {
        this.axisSettingsFormGroup.get('ticksColor').disable({emitEvent: false});
      }
      if (showLine) {
        this.axisSettingsFormGroup.get('lineColor').enable({emitEvent: false});
      } else {
        this.axisSettingsFormGroup.get('lineColor').disable({emitEvent: false});
      }
      if (showSplitLines) {
        this.axisSettingsFormGroup.get('splitLinesColor').enable({emitEvent: false});
      } else {
        this.axisSettingsFormGroup.get('splitLinesColor').disable({emitEvent: false});
      }
    } else {
      this.axisSettingsFormGroup.disable({emitEvent: false});
      this.axisSettingsFormGroup.get('show').enable({emitEvent: false});
      if (this.axisType === 'yAxis') {
        this.axisSettingsFormGroup.get('min').enable({emitEvent: false});
        this.axisSettingsFormGroup.get('max').enable({emitEvent: false});
      }
    }
  }

  private updateModel() {
    this.modelValue = this.axisSettingsFormGroup.getRawValue();
    this.propagateChange(this.modelValue);
  }
}
