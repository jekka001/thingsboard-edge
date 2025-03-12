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

import {
  ChangeDetectorRef,
  Component,
  forwardRef,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, NgForm } from '@angular/forms';
import { resolveBreakpoint, ValueType, valueTypesMap } from '@shared/models/constants';
import { isObject } from '@core/utils';
import { MatDialog } from '@angular/material/dialog';
import {
  JsonObjectEditDialogComponent,
  JsonObjectEditDialogData
} from '@shared/components/dialog/json-object-edit-dialog.component';
import { BreakpointObserver } from '@angular/cdk/layout';
import { Subscription } from 'rxjs';
import { coerceBoolean } from '@shared/decorators/coercion';
import { TranslateService } from '@ngx-translate/core';

type Layout = 'column' | 'row';

export interface ValueInputLayout {
  layout: Layout;
  breakpoints?: {[breakpoint: string]: Layout};
}

@Component({
  selector: 'tb-value-input',
  templateUrl: './value-input.component.html',
  styleUrls: ['./value-input.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ValueInputComponent),
      multi: true
    }
  ]
})
export class ValueInputComponent implements OnInit, OnDestroy, OnChanges, ControlValueAccessor {

  @Input()
  disabled: boolean;

  @Input()
  requiredText: string;

  @Input()
  valueType: ValueType;

  @Input()
  allowedValueTypes: ValueType[];

  @Input()
  trueLabel: string;

  @Input()
  falseLabel: string;

  @Input()
  @coerceBoolean()
  shortBooleanField = false;

  @Input()
  @coerceBoolean()
  required = true;

  @Input()
  @coerceBoolean()
  hideJsonEdit = false;

  @Input()
  layout: ValueInputLayout | Layout = 'row';

  @ViewChild('inputForm', {static: true}) inputForm: NgForm;

  @Input()
  @coerceBoolean()
  stringNotRequired = false;

  modelValue: any;

  public valueTypeEnum = ValueType;

  valueTypeKeys = Object.keys(ValueType);

  valueTypes = valueTypesMap;

  showValueType = true;

  computedLayout: Layout;

  private propagateChange = null;

  private _subscription: Subscription;

  constructor(
    private breakpointObserver: BreakpointObserver,
    private cd: ChangeDetectorRef,
    private translate: TranslateService,
    public dialog: MatDialog,
  ) {

  }

  ngOnInit(): void {
    if (!this.trueLabel) {
      this.trueLabel = this.translate.instant('value.true');
    }
    if (!this.falseLabel) {
      this.falseLabel = this.translate.instant('value.false');
    }
    if (this.allowedValueTypes?.length) {
      this.valueTypeKeys = this.allowedValueTypes;
    }
    this._subscription = new Subscription();
    this.showValueType = !this.valueType;
    this.computedLayout = this._computeLayout();
    if (typeof this.layout === 'object' && this.layout.breakpoints) {
      const breakpoints = Object.keys(this.layout.breakpoints);
      this._subscription.add(this.breakpointObserver.observe(breakpoints.map(breakpoint => resolveBreakpoint(breakpoint))).subscribe(
        () => {
          this.computedLayout = this._computeLayout();
        }
      ));
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange) {
        if (propName === 'valueType') {
          this.showValueType = !this.valueType;
          if (this.valueType) {
            this.updateModelToValueType();
          } else {
            this.detectValueType();
          }
          this.cd.markForCheck();
        }
      }
    }
  }

  ngOnDestroy() {
    this._subscription.unsubscribe();
  }

  openEditJSONDialog($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<JsonObjectEditDialogComponent, JsonObjectEditDialogData, object>(JsonObjectEditDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        jsonValue: this.modelValue,
        required: true
      }
    }).afterClosed().subscribe(
      (res) => {
        if (res) {
          this.modelValue = res;
          this.inputForm.control.patchValue({value: this.modelValue});
          this.updateView();
        }
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
  }

  writeValue(value: any): void {
    this.modelValue = value;
    if (this.showValueType) {
      this.detectValueType();
    } else {
      setTimeout(() => {
        this.updateModelToValueType();
        this.cd.markForCheck();
      }, 0);
    }
  }

  updateView() {
    if (this.inputForm.valid || this.valueType === ValueType.BOOLEAN ||
      (this.valueType === ValueType.JSON && Array.isArray(this.modelValue))) {
      let value = this.modelValue;
      if (this.stringNotRequired && this.valueType === ValueType.STRING && !value) {
        value = '';
      }
      this.propagateChange(value);
    } else {
      this.propagateChange(null);
    }
  }

  onValueTypeChanged() {
    if (this.valueType === ValueType.BOOLEAN) {
      this.modelValue = false;
    } else if (this.valueType === ValueType.JSON) {
      this.modelValue = {};
      this.inputForm.form.get('value').patchValue({});
    } else if (this.valueType === ValueType.STRING && this.stringNotRequired) {
      this.modelValue = '';
    } else {
      this.modelValue = null;
    }
    this.updateView();
  }

  onValueChanged() {
    this.updateView();
  }

  private detectValueType() {
    if (this.modelValue === true || this.modelValue === false) {
      this.valueType = ValueType.BOOLEAN;
    } else if (typeof this.modelValue === 'number') {
      if (this.modelValue.toString().indexOf('.') === -1) {
        this.valueType = ValueType.INTEGER;
      } else {
        this.valueType = ValueType.DOUBLE;
      }
    } else if (isObject(this.modelValue)) {
      this.valueType = ValueType.JSON;
    } else {
      this.valueType = ValueType.STRING;
    }
  }

  private updateModelToValueType() {
    if (this.valueType === ValueType.BOOLEAN && typeof this.modelValue !== 'boolean') {
      this.modelValue = !!this.modelValue;
      this.updateView();
    } else if (this.valueType === ValueType.STRING && typeof this.modelValue !== 'string') {
      this.modelValue = null;
      this.updateView();
    } else if ([ValueType.DOUBLE, ValueType.INTEGER].includes(this.valueType) && typeof this.modelValue !== 'number') {
      this.modelValue = null;
      this.updateView();
    } else if (this.valueType === ValueType.JSON && typeof this.modelValue !== 'object') {
      this.modelValue = {};
      this.inputForm.form.get('value').patchValue({});
      this.updateView();
    }
  }

  private _computeLayout(): Layout {
    if (typeof this.layout !== 'object') {
      return this.layout;
    } else {
      let layout = this.layout.layout;
      if (this.layout.breakpoints) {
        for (const breakpoint of Object.keys(this.layout.breakpoints)) {
          const breakpointValue = resolveBreakpoint(breakpoint);
          if (this.breakpointObserver.isMatched(breakpointValue)) {
            layout = this.layout.breakpoints[breakpoint];
            break;
          }
        }
      }
      return layout;
    }
  }

}
