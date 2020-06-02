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

import { Component, forwardRef, Injector, Input, OnInit } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormGroup,
  NG_VALUE_ACCESSOR,
  NgControl,
  Validators
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Subscription } from 'rxjs';
import { GenericRolePermissions } from '@shared/models/role.models';
import { Operation, Resource } from '@shared/models/security.models';

@Component({
  selector: 'tb-permission-list',
  templateUrl: './permission-list.component.html',
  styleUrls: ['./permission-list.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => PermissionListComponent),
      multi: true
    }
  ]
})
export class PermissionListComponent extends PageComponent implements ControlValueAccessor, OnInit {

  @Input() disabled: boolean;

  permissionListFormGroup: FormGroup;

  private propagateChange = null;

  private valueChangeSubscription: Subscription = null;

  ngControl: NgControl;

  constructor(protected store: Store<AppState>,
              private injector: Injector,
              private fb: FormBuilder) {
    super(store);
  }

  ngOnInit(): void {
    this.ngControl = this.injector.get(NgControl);
    this.permissionListFormGroup = this.fb.group({});
    this.permissionListFormGroup.addControl('permissions',
      this.fb.array([]));
  }

  permissionsFormArray(): FormArray {
    return this.permissionListFormGroup.get('permissions') as FormArray;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.permissionListFormGroup.disable({emitEvent: false});
    } else {
      this.permissionListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(permissions: GenericRolePermissions): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const permissionsControls: Array<AbstractControl> = [];
    if (permissions) {
      for (const resource of Object.keys(permissions)) {
        const permissionControl = this.fb.group({
          resource: [Resource[resource], [Validators.required]],
          operations: [permissions[resource], [Validators.required]]
        });
        if (this.disabled) {
          permissionControl.disable();
        }
        permissionsControls.push(permissionControl);
      }
    }
    this.permissionListFormGroup.setControl('permissions', this.fb.array(permissionsControls));
    this.valueChangeSubscription = this.permissionListFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
  }

  public removePermission(index: number) {
    (this.permissionListFormGroup.get('permissions') as FormArray).removeAt(index);
  }

  public addPermission() {
    const permissionsFormArray = this.permissionListFormGroup.get('permissions') as FormArray;
    permissionsFormArray.push(this.fb.group({
      resource: [null, [Validators.required]],
      operations: [null, [Validators.required]]
    }));
  }

  private updateModel() {
    const permissionList: {resource: Resource; operations: Operation[]}[] = this.permissionListFormGroup.get('permissions').value;
    const permissions: GenericRolePermissions = {};
    permissionList.forEach((entry) => {
      permissions[entry.resource] = entry.operations;
    });
    this.propagateChange(permissions);
  }
}
