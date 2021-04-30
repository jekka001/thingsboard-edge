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

import {Component, forwardRef, Input, OnInit} from '@angular/core';
import {ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators} from '@angular/forms';
import {Store} from '@ngrx/store';
import {AppState} from '@app/core/core.state';
import {coerceBooleanProperty} from '@angular/cdk/coercion';
import {
  DeviceTransportConfiguration,
  DeviceTransportType,
  SnmpDeviceTransportConfiguration
} from '@shared/models/device.models';

@Component({
  selector: 'tb-snmp-device-transport-configuration',
  templateUrl: './snmp-device-transport-configuration.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SnmpDeviceTransportConfigurationComponent),
    multi: true
  }]
})
export class SnmpDeviceTransportConfigurationComponent implements ControlValueAccessor, OnInit {

  snmpDeviceTransportConfigurationFormGroup: FormGroup;

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

  private propagateChange = (v: any) => {
  };

  constructor(private store: Store<AppState>,
              private fb: FormBuilder) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.snmpDeviceTransportConfigurationFormGroup = this.fb.group({
      configuration: [null, Validators.required]
    });
    this.snmpDeviceTransportConfigurationFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.snmpDeviceTransportConfigurationFormGroup.disable({emitEvent: false});
    } else {
      this.snmpDeviceTransportConfigurationFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: SnmpDeviceTransportConfiguration | null): void {
    this.snmpDeviceTransportConfigurationFormGroup.patchValue({configuration: value}, {emitEvent: false});
  }

  private updateModel() {
    let configuration: DeviceTransportConfiguration = null;
    if (this.snmpDeviceTransportConfigurationFormGroup.valid) {
      configuration = this.snmpDeviceTransportConfigurationFormGroup.getRawValue().configuration;
      configuration.type = DeviceTransportType.SNMP;
    }
    this.propagateChange(configuration);
  }
}
