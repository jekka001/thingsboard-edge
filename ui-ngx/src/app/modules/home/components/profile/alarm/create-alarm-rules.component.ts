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
  AbstractControl,
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR, ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { AlarmRule, alarmRuleValidator } from '@shared/models/device.models';
import { MatDialog } from '@angular/material/dialog';
import { Subscription } from 'rxjs';
import { AlarmSeverity, alarmSeverityTranslations } from '../../../../../shared/models/alarm.models';

@Component({
  selector: 'tb-create-alarm-rules',
  templateUrl: './create-alarm-rules.component.html',
  styleUrls: ['./create-alarm-rules.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CreateAlarmRulesComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => CreateAlarmRulesComponent),
      multi: true,
    }
  ]
})
export class CreateAlarmRulesComponent implements ControlValueAccessor, OnInit, Validator {

  alarmSeverities = Object.keys(AlarmSeverity);
  alarmSeverityEnum = AlarmSeverity;

  alarmSeverityTranslationMap = alarmSeverityTranslations;

  @Input()
  disabled: boolean;

  createAlarmRulesFormGroup: FormGroup;

  private usedSeverities: AlarmSeverity[] = [];

  private valueChangeSubscription: Subscription = null;

  private propagateChange = (v: any) => { };

  constructor(private dialog: MatDialog,
              private fb: FormBuilder) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.createAlarmRulesFormGroup = this.fb.group({
      createAlarmRules: this.fb.array([])
    });
  }

  createAlarmRulesFormArray(): FormArray {
    return this.createAlarmRulesFormGroup.get('createAlarmRules') as FormArray;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.createAlarmRulesFormGroup.disable({emitEvent: false});
    } else {
      this.createAlarmRulesFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(createAlarmRules: {[severity: string]: AlarmRule}): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const createAlarmRulesControls: Array<AbstractControl> = [];
    if (createAlarmRules) {
      Object.keys(createAlarmRules).forEach((severity) => {
        const createAlarmRule = createAlarmRules[severity];
        if (severity === 'empty') {
          severity = null;
        }
        createAlarmRulesControls.push(this.fb.group({
          severity: [severity, Validators.required],
          alarmRule: [createAlarmRule, Validators.required]
        }));
      });
    }
    this.createAlarmRulesFormGroup.setControl('createAlarmRules', this.fb.array(createAlarmRulesControls));
    if (this.disabled) {
      this.createAlarmRulesFormGroup.disable({emitEvent: false});
    } else {
      this.createAlarmRulesFormGroup.enable({emitEvent: false});
    }
    this.valueChangeSubscription = this.createAlarmRulesFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    this.updateUsedSeverities();
    if (!this.disabled && !this.createAlarmRulesFormGroup.valid) {
      this.updateModel();
    }
  }

  public removeCreateAlarmRule(index: number) {
    (this.createAlarmRulesFormGroup.get('createAlarmRules') as FormArray).removeAt(index);
  }

  public addCreateAlarmRule() {
    const createAlarmRule: AlarmRule = {
      condition: {
        condition: []
      }
    };
    const createAlarmRulesArray = this.createAlarmRulesFormGroup.get('createAlarmRules') as FormArray;
    createAlarmRulesArray.push(this.fb.group({
      severity: [this.getFirstUnusedSeverity(), Validators.required],
      alarmRule: [createAlarmRule, alarmRuleValidator]
    }));
    this.createAlarmRulesFormGroup.updateValueAndValidity();
    if (!this.createAlarmRulesFormGroup.valid) {
      this.updateModel();
    }
  }

  private getFirstUnusedSeverity(): AlarmSeverity {
    for (const severityKey of Object.keys(AlarmSeverity)) {
      const severity = AlarmSeverity[severityKey];
      if (this.usedSeverities.indexOf(severity) === -1) {
        return severity;
      }
    }
    return null;
  }

  public validate(c: FormControl) {
    return (this.createAlarmRulesFormGroup.valid) ? null : {
      createAlarmRules: {
        valid: false,
      },
    };
  }

  public isDisabledSeverity(severity: AlarmSeverity, index: number): boolean {
    const usedIndex = this.usedSeverities.indexOf(severity);
    return usedIndex > -1 && usedIndex !== index;
  }

  private updateUsedSeverities() {
    this.usedSeverities = [];
    const value: {severity: string, alarmRule: AlarmRule}[] = this.createAlarmRulesFormGroup.get('createAlarmRules').value;
    value.forEach((rule, index) => {
      this.usedSeverities[index] = AlarmSeverity[rule.severity];
    });
  }

  private updateModel() {
    const value: {severity: string, alarmRule: AlarmRule}[] = this.createAlarmRulesFormGroup.get('createAlarmRules').value;
    const createAlarmRules: {[severity: string]: AlarmRule} = {};
    value.forEach(v => {
      createAlarmRules[v.severity] = v.alarmRule;
    });
    this.updateUsedSeverities();
    this.propagateChange(createAlarmRules);
  }
}
