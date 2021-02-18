///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
  NG_VALUE_ACCESSOR,
  Validator,
  Validators
} from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { DeviceProfileAlarm, deviceProfileAlarmValidator } from '@shared/models/device.models';
import { guid } from '@core/utils';
import { Subscription } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { EntityId } from '@shared/models/id/entity-id';

@Component({
  selector: 'tb-device-profile-alarms',
  templateUrl: './device-profile-alarms.component.html',
  styleUrls: ['./device-profile-alarms.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DeviceProfileAlarmsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => DeviceProfileAlarmsComponent),
      multi: true,
    }
  ]
})
export class DeviceProfileAlarmsComponent implements ControlValueAccessor, OnInit, Validator {

  deviceProfileAlarmsFormGroup: FormGroup;

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  @Input()
  disabled: boolean;

  @Input()
  deviceProfileId: EntityId;

  private valueChangeSubscription: Subscription = null;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private fb: FormBuilder,
              private dialog: MatDialog) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.deviceProfileAlarmsFormGroup = this.fb.group({
      alarms: this.fb.array([])
    });
  }

  alarmsFormArray(): FormArray {
    return this.deviceProfileAlarmsFormGroup.get('alarms') as FormArray;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.deviceProfileAlarmsFormGroup.disable({emitEvent: false});
    } else {
      this.deviceProfileAlarmsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(alarms: Array<DeviceProfileAlarm> | null): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const alarmsControls: Array<AbstractControl> = [];
    if (alarms) {
      alarms.forEach((alarm) => {
        alarmsControls.push(this.fb.control(alarm, [Validators.required]));
      });
    }
    this.deviceProfileAlarmsFormGroup.setControl('alarms', this.fb.array(alarmsControls));
    if (this.disabled) {
      this.deviceProfileAlarmsFormGroup.disable({emitEvent: false});
    } else {
      this.deviceProfileAlarmsFormGroup.enable({emitEvent: false});
    }
    this.valueChangeSubscription = this.deviceProfileAlarmsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
  }

  public trackByAlarm(index: number, alarmControl: AbstractControl): string {
    if (alarmControl) {
      return alarmControl.value.id;
    } else {
      return null;
    }
  }

  public removeAlarm(index: number) {
    (this.deviceProfileAlarmsFormGroup.get('alarms') as FormArray).removeAt(index);
  }

  public addAlarm() {
    const alarm: DeviceProfileAlarm = {
      id: guid(),
      alarmType: '',
      createRules: {
        CRITICAL: {
          condition: {
            condition: []
          }
        }
      }
    };
    const alarmsArray = this.deviceProfileAlarmsFormGroup.get('alarms') as FormArray;
    alarmsArray.push(this.fb.control(alarm, [deviceProfileAlarmValidator]));
    this.deviceProfileAlarmsFormGroup.updateValueAndValidity();
    if (!this.deviceProfileAlarmsFormGroup.valid) {
      this.updateModel();
    }
  }

  public validate(c: FormControl) {
    return (this.deviceProfileAlarmsFormGroup.valid) ? null : {
      alarms: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const alarms: Array<DeviceProfileAlarm> = this.deviceProfileAlarmsFormGroup.get('alarms').value;
    this.propagateChange(alarms);
  }
}
