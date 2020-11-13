///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
  FormBuilder,
  FormControl, FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator, Validators
} from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { deepClone, isUndefined } from '@core/utils';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { AlarmCondition, AlarmConditionSpec, AlarmConditionType } from '@shared/models/device.models';
import {
  AlarmRuleConditionDialogComponent,
  AlarmRuleConditionDialogData
} from '@home/components/profile/alarm/alarm-rule-condition-dialog.component';
import { TimeUnit } from '@shared/models/time/time.models';

@Component({
  selector: 'tb-alarm-rule-condition',
  templateUrl: './alarm-rule-condition.component.html',
  styleUrls: ['./alarm-rule-condition.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AlarmRuleConditionComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => AlarmRuleConditionComponent),
      multi: true,
    }
  ]
})
export class AlarmRuleConditionComponent implements ControlValueAccessor, OnInit, Validator {

  @Input()
  disabled: boolean;

  alarmRuleConditionFormGroup: FormGroup;

  specText = '';

  private modelValue: AlarmCondition;

  private propagateChange = (v: any) => { };

  constructor(private dialog: MatDialog,
              private fb: FormBuilder,
              private translate: TranslateService,
              private datePipe: DatePipe) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.alarmRuleConditionFormGroup = this.fb.group({
      condition: [null, Validators.required],
      spec: [null, Validators.required]
    });
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.alarmRuleConditionFormGroup.disable({emitEvent: false});
    } else {
      this.alarmRuleConditionFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: AlarmCondition): void {
    this.modelValue = value;
    if (this.modelValue !== null && isUndefined(this.modelValue?.spec)) {
      this.modelValue = Object.assign(this.modelValue, {spec: {type: AlarmConditionType.SIMPLE}});
    }
    this.updateConditionInfo();
  }

  public conditionSet() {
    return this.modelValue && this.modelValue.condition.length;
  }

  public validate(c: FormControl) {
    return this.conditionSet() ? null : {
      alarmRuleCondition: {
        valid: false,
      },
    };
  }

  public openFilterDialog($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<AlarmRuleConditionDialogComponent, AlarmRuleConditionDialogData,
      AlarmCondition>(AlarmRuleConditionDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        readonly: this.disabled,
        condition: this.disabled ? this.modelValue : deepClone(this.modelValue)
      }
    }).afterClosed().subscribe((result) => {
      if (result) {
        this.modelValue = result;
        this.updateModel();
      }
    });
  }

  private updateConditionInfo() {
    this.alarmRuleConditionFormGroup.patchValue(
      {
        condition: this.modelValue?.condition,
        spec: this.modelValue?.spec
      }
    );
    this.updateSpecText();
  }

  private updateSpecText() {
    this.specText = '';
    if (this.modelValue && this.modelValue.spec) {
      const spec = this.modelValue.spec;
      switch (spec.type) {
        case AlarmConditionType.SIMPLE:
          break;
        case AlarmConditionType.DURATION:
          let duringText = '';
          switch (spec.unit) {
            case TimeUnit.SECONDS:
              duringText = this.translate.instant('timewindow.seconds', {seconds: spec.value});
              break;
            case TimeUnit.MINUTES:
              duringText = this.translate.instant('timewindow.minutes', {minutes: spec.value});
              break;
            case TimeUnit.HOURS:
              duringText = this.translate.instant('timewindow.hours', {hours: spec.value});
              break;
            case TimeUnit.DAYS:
              duringText = this.translate.instant('timewindow.days', {days: spec.value});
              break;
          }
          this.specText = this.translate.instant('device-profile.condition-during', {during: duringText});
          break;
        case AlarmConditionType.REPEATING:
          this.specText = this.translate.instant('device-profile.condition-repeat-times', {count: spec.count});
          break;
      }
    }
  }

  private updateModel() {
    this.updateConditionInfo();
    this.propagateChange(this.modelValue);
  }
}
