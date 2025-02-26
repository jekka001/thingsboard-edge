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

import { Component, DestroyRef, forwardRef, Input, OnInit, ViewEncapsulation } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormArray,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validator
} from '@angular/forms';
import {
  TimeSeriesChartThreshold,
  timeSeriesChartThresholdDefaultSettings,
  timeSeriesChartThresholdValid,
  timeSeriesChartThresholdValidator,
  TimeSeriesChartYAxisId
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { mergeDeep } from '@core/utils';
import { IAliasController } from '@core/api/widget-api.models';
import { DataKeysCallbacks } from '@home/components/widget/config/data-keys.component.models';
import { DataKey, Datasource, WidgetConfig } from '@shared/models/widget.models';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { coerceBoolean } from '@shared/decorators/coercion';
import { ValueSourceType } from '@shared/models/widget-settings.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-time-series-chart-thresholds-panel',
  templateUrl: './time-series-chart-thresholds-panel.component.html',
  styleUrls: ['./time-series-chart-thresholds-panel.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TimeSeriesChartThresholdsPanelComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => TimeSeriesChartThresholdsPanelComponent),
      multi: true
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class TimeSeriesChartThresholdsPanelComponent implements ControlValueAccessor, OnInit, Validator {

  @Input()
  disabled: boolean;

  @Input()
  aliasController: IAliasController;

  @Input()
  dataKeyCallbacks: DataKeysCallbacks;

  @Input()
  datasource: Datasource;

  @Input()
  widgetConfig: WidgetConfig;

  @Input()
  yAxisIds: TimeSeriesChartYAxisId[];

  @Input()
  @coerceBoolean()
  hideYAxis = false;

  thresholdsFormGroup: UntypedFormGroup;

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private destroyRef: DestroyRef) {
  }

  ngOnInit() {
    this.thresholdsFormGroup = this.fb.group({
      thresholds: [this.fb.array([]), []]
    });
    this.thresholdsFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(
      () => {
        let thresholds: TimeSeriesChartThreshold[] = this.thresholdsFormGroup.get('thresholds').value;
        if (thresholds) {
          thresholds = thresholds.filter(t => timeSeriesChartThresholdValid(t));
        }
        this.updateLatestDataKeys(thresholds);
        this.propagateChange(thresholds);
      }
    );
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.thresholdsFormGroup.disable({emitEvent: false});
    } else {
      this.thresholdsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: TimeSeriesChartThreshold[] | undefined): void {
    const thresholds = this.checkLatestDataKeys(value || []);
    this.thresholdsFormGroup.setControl('thresholds', this.prepareThresholdsFormArray(thresholds), {emitEvent: false});
  }

  public validate(c: UntypedFormControl) {
    const valid = this.thresholdsFormGroup.valid;
    return valid ? null : {
      thresholds: {
        valid: false,
      },
    };
  }

  thresholdsFormArray(): UntypedFormArray {
    return this.thresholdsFormGroup.get('thresholds') as UntypedFormArray;
  }

  trackByThreshold(index: number, thresholdControl: AbstractControl): any {
    return thresholdControl;
  }

  removeThreshold(index: number) {
    (this.thresholdsFormGroup.get('thresholds') as UntypedFormArray).removeAt(index);
  }

  addThreshold() {
    const threshold = mergeDeep<TimeSeriesChartThreshold>({} as TimeSeriesChartThreshold,
      timeSeriesChartThresholdDefaultSettings);
    const thresholdsArray = this.thresholdsFormGroup.get('thresholds') as UntypedFormArray;
    const thresholdControl = this.fb.control(threshold, [timeSeriesChartThresholdValidator]);
    thresholdsArray.push(thresholdControl);
  }

  private prepareThresholdsFormArray(thresholds: TimeSeriesChartThreshold[] | undefined): UntypedFormArray {
    const thresholdsControls: Array<AbstractControl> = [];
    if (thresholds) {
      thresholds.forEach((threshold) => {
        thresholdsControls.push(this.fb.control(threshold, [timeSeriesChartThresholdValidator]));
      });
    }
    return this.fb.array(thresholdsControls);
  }

  private checkLatestDataKeys(thresholds: TimeSeriesChartThreshold[]): TimeSeriesChartThreshold[] {
    const result: TimeSeriesChartThreshold[] = [];
    const latestKeys = this.datasource?.latestDataKeys || [];
    for (const threshold of thresholds) {
      if (threshold.type === ValueSourceType.latestKey) {
        const found = latestKeys.find(k => this.isThresholdKey(k, threshold));
        if (found) {
          result.push(threshold);
        }
      } else {
        result.push(threshold);
      }
    }
    return result;
  }

  private updateLatestDataKeys(thresholds: TimeSeriesChartThreshold[]) {
    if (this.datasource) {
      let latestKeys = this.datasource.latestDataKeys;
      if (!latestKeys) {
        latestKeys = [];
        this.datasource.latestDataKeys = latestKeys;
      }
      const existingThresholdKeys = latestKeys.filter(k => k.settings?.__thresholdKey === true);
      const foundThresholdKeys: DataKey[] = [];
      for (const threshold of thresholds) {
        if (threshold.type === ValueSourceType.latestKey) {
          const found = existingThresholdKeys.find(k => this.isThresholdKey(k, threshold));
          if (!found) {
            const newKey = this.dataKeyCallbacks.generateDataKey(threshold.latestKey, threshold.latestKeyType,
              null, true, null);
            newKey.settings.__thresholdKey = true;
            latestKeys.push(newKey);
          } else if (foundThresholdKeys.indexOf(found) === -1) {
            foundThresholdKeys.push(found);
          }
        }
      }
      const toRemove = existingThresholdKeys.filter(k => foundThresholdKeys.indexOf(k) === -1);
      for (const key of toRemove) {
        const index = latestKeys.indexOf(key);
        if (index > -1) {
          latestKeys.splice(index, 1);
        }
      }
    }
  }

  private isThresholdKey(d: DataKey, threshold: TimeSeriesChartThreshold): boolean {
    return (d.type === DataKeyType.function && d.label === threshold.latestKey) ||
    (d.type !== DataKeyType.function && d.name === threshold.latestKey &&
      d.type === threshold.latestKeyType);
  }
}
