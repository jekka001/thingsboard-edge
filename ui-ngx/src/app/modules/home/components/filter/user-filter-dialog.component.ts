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

import { Component, Inject, OnInit, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  AbstractControl, FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  FormGroupDirective,
  NgForm,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import { TranslateService } from '@ngx-translate/core';
import {
  EntityKeyValueType,
  Filter, FilterPredicateValue,
  filterToUserFilterInfoList,
  UserFilterInputInfo
} from '@shared/models/query/query.models';
import { isDefinedAndNotNull } from '@core/utils';

export interface UserFilterDialogData {
  filter: Filter;
}

@Component({
  selector: 'tb-user-filter-dialog',
  templateUrl: './user-filter-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: UserFilterDialogComponent}],
  styleUrls: []
})
export class UserFilterDialogComponent extends DialogComponent<UserFilterDialogComponent, Filter>
  implements OnInit, ErrorStateMatcher {

  filter: Filter;

  userFilterFormGroup: FormGroup;

  valueTypeEnum = EntityKeyValueType;

  submitted = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: UserFilterDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<UserFilterDialogComponent, Filter>,
              private fb: FormBuilder,
              public translate: TranslateService) {
    super(store, router, dialogRef);
    this.filter = data.filter;
    const userInputs = filterToUserFilterInfoList(this.filter, translate);

    const userInputControls: Array<AbstractControl> = [];
    for (const userInput of userInputs) {
      userInputControls.push(this.createUserInputFormControl(userInput));
    }

    this.userFilterFormGroup = this.fb.group({
      userInputs: this.fb.array(userInputControls)
    });
  }

  private createUserInputFormControl(userInput: UserFilterInputInfo): AbstractControl {
    const predicateValue: FilterPredicateValue<string | number | boolean> = (userInput.info.keyFilterPredicate as any).value;
    const value = isDefinedAndNotNull(predicateValue.userValue) ? predicateValue.userValue : predicateValue.defaultValue;
    const userInputControl = this.fb.group({
      label: [userInput.label],
      valueType: [userInput.valueType],
      value: [value,
        userInput.valueType === EntityKeyValueType.NUMERIC ||
        userInput.valueType === EntityKeyValueType.DATE_TIME  ? [Validators.required] : []]
    });
    userInputControl.get('value').valueChanges.subscribe(userValue => {
      (userInput.info.keyFilterPredicate as any).value.userValue = userValue;
    });
    return userInputControl;
  }

  userInputsFormArray(): FormArray {
    return this.userFilterFormGroup.get('userInputs') as FormArray;
  }

  ngOnInit(): void {
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    this.dialogRef.close(this.filter);
  }
}
