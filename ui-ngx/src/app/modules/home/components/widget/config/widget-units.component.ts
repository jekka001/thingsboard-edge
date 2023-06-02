///
/// Copyright © 2016-2023 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import { ControlValueAccessor, FormControl, NG_VALUE_ACCESSOR, UntypedFormBuilder } from '@angular/forms';

@Component({
  selector: 'tb-widget-units',
  templateUrl: './widget-units.component.html',
  styleUrls: ['./widget-config.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => WidgetUnitsComponent),
      multi: true
    }
  ]
})
export class WidgetUnitsComponent implements ControlValueAccessor, OnInit {

  @Input()
  disabled: boolean;

  unitsFormControl: FormControl;

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder) {
  }

  ngOnInit() {
    this.unitsFormControl = this.fb.control('', []);
  }

  writeValue(units?: string): void {
    this.unitsFormControl.patchValue(units, {emitEvent: false});
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.unitsFormControl.disable({emitEvent: false});
    } else {
      this.unitsFormControl.enable({emitEvent: false});
    }
  }
}
