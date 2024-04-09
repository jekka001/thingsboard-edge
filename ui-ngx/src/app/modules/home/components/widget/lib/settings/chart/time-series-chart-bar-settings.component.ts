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
  BarSeriesSettings,
  seriesLabelPositions,
  seriesLabelPositionTranslations
} from '@home/components/widget/lib/chart/time-series-chart.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { merge } from 'rxjs';
import { formatValue, isDefinedAndNotNull } from '@core/utils';
import { DataKeyConfigComponent } from '@home/components/widget/config/data-key-config.component';

@Component({
  selector: 'tb-time-series-chart-bar-settings',
  templateUrl: './time-series-chart-bar-settings.component.html',
  styleUrls: ['./../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TimeSeriesChartBarSettingsComponent),
      multi: true
    }
  ]
})
export class TimeSeriesChartBarSettingsComponent implements OnInit, ControlValueAccessor {

  seriesLabelPositions = seriesLabelPositions;

  seriesLabelPositionTranslations = seriesLabelPositionTranslations;

  labelPreviewFn = this._labelPreviewFn.bind(this);

  @Input()
  disabled: boolean;

  private modelValue: BarSeriesSettings;

  private propagateChange = null;

  public barSettingsFormGroup: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              private dataKeyConfigComponent: DataKeyConfigComponent,
              private fb: UntypedFormBuilder) {
  }

  ngOnInit(): void {
    this.barSettingsFormGroup = this.fb.group({
      showBorder: [null, []],
      borderWidth: [null, [Validators.min(0)]],
      borderRadius: [null, [Validators.min(0)]],
      showLabel: [null, []],
      labelPosition: [null, []],
      labelFont: [null, []],
      labelColor: [null, []],
      enableLabelBackground: [null, []],
      labelBackground: [null, []],
      backgroundSettings: [null, []]
    });
    this.barSettingsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    merge(this.barSettingsFormGroup.get('showBorder').valueChanges,
      this.barSettingsFormGroup.get('showLabel').valueChanges,
      this.barSettingsFormGroup.get('enableLabelBackground').valueChanges)
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
      this.barSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.barSettingsFormGroup.enable({emitEvent: false});
      this.updateValidators();
    }
  }

  writeValue(value: BarSeriesSettings): void {
    this.modelValue = value;
    this.barSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
    this.updateValidators();
  }

  private updateValidators() {
    const showBorder: boolean = this.barSettingsFormGroup.get('showBorder').value;
    const showLabel: boolean = this.barSettingsFormGroup.get('showLabel').value;
    const enableLabelBackground: boolean = this.barSettingsFormGroup.get('enableLabelBackground').value;
    if (showBorder) {
      this.barSettingsFormGroup.get('borderWidth').enable({emitEvent: false});
    } else {
      this.barSettingsFormGroup.get('borderWidth').disable({emitEvent: false});
    }
    if (showLabel) {
      this.barSettingsFormGroup.get('labelPosition').enable({emitEvent: false});
      this.barSettingsFormGroup.get('labelFont').enable({emitEvent: false});
      this.barSettingsFormGroup.get('labelColor').enable({emitEvent: false});
      this.barSettingsFormGroup.get('enableLabelBackground').enable({emitEvent: false});
      if (enableLabelBackground) {
        this.barSettingsFormGroup.get('labelBackground').enable({emitEvent: false});
      } else {
        this.barSettingsFormGroup.get('labelBackground').disable({emitEvent: false});
      }
    } else {
      this.barSettingsFormGroup.get('labelPosition').disable({emitEvent: false});
      this.barSettingsFormGroup.get('labelFont').disable({emitEvent: false});
      this.barSettingsFormGroup.get('labelColor').disable({emitEvent: false});
      this.barSettingsFormGroup.get('enableLabelBackground').disable({emitEvent: false});
      this.barSettingsFormGroup.get('labelBackground').disable({emitEvent: false});
    }
  }

  private updateModel() {
    this.modelValue = this.barSettingsFormGroup.getRawValue();
    this.propagateChange(this.modelValue);
  }

  private _labelPreviewFn(): string {
    const dataKey = this.dataKeyConfigComponent.modelValue;
    const widgetConfig = this.dataKeyConfigComponent.widgetConfig;
    const units = dataKey.units && dataKey.units.length ? dataKey.units : widgetConfig.config.units;
    const decimals = isDefinedAndNotNull(dataKey.decimals) ? dataKey.decimals :
      (isDefinedAndNotNull(widgetConfig.config.decimals) ? widgetConfig.config.decimals : 2);
    return formatValue(22, decimals, units, false);
  }
}
