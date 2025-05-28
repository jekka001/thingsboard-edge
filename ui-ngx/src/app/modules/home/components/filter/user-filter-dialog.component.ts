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

import { Component, DestroyRef, Inject, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormArray, FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import { TranslateService } from '@ngx-translate/core';
import {
  EntityKeyValueType,
  Filter,
  FilterPredicateValue,
  filterToUserFilterInfoList,
  UserFilterInputInfo
} from '@shared/models/query/query.models';
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { UnitService } from '@core/services/unit.service';
import { getSourceTbUnitSymbol, TbUnitConverter } from '@shared/models/unit.models';

export interface UserFilterDialogData {
  filter: Filter;
}

@Component({
  selector: 'tb-user-filter-dialog',
  templateUrl: './user-filter-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: UserFilterDialogComponent}],
  styleUrls: ['./user-filter-dialog.component.scss'],
})
export class UserFilterDialogComponent extends DialogComponent<UserFilterDialogComponent, Filter>
  implements ErrorStateMatcher {

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
              private translate: TranslateService,
              private destroyRef: DestroyRef,
              private unitService: UnitService) {
    super(store, router, dialogRef);
    this.filter = data.filter;
    const userInputs = filterToUserFilterInfoList(this.filter, this.translate);

    const userInputControls: Array<FormGroup> = [];
    for (const userInput of userInputs) {
      userInputControls.push(this.createUserInputFormControl(userInput));
    }

    this.userFilterFormGroup = this.fb.group({
      userInputs: this.fb.array(userInputControls)
    });
  }

  private createUserInputFormControl(userInput: UserFilterInputInfo): FormGroup {
    const predicateValue: FilterPredicateValue<string | number | boolean> = (userInput.info.keyFilterPredicate as any).value;
    let value = isDefinedAndNotNull(predicateValue.userValue) ? predicateValue.userValue : predicateValue.defaultValue;
    let unitSymbol = '';
    let valueConvertor: TbUnitConverter;
    if (userInput.valueType === EntityKeyValueType.NUMERIC) {
      unitSymbol = this.unitService.getTargetUnitSymbol(userInput.unit);
      const sourceUnit = getSourceTbUnitSymbol(userInput.unit);
      value = this.unitService.convertUnitValue(value as number, userInput.unit);
      valueConvertor = this.unitService.geUnitConverter(unitSymbol, sourceUnit);
    }
    const userInputControl = this.fb.group({
      label: [userInput.label],
      valueType: [userInput.valueType],
      unitSymbol: [unitSymbol],
      value: [value,
        userInput.valueType === EntityKeyValueType.NUMERIC ||
        userInput.valueType === EntityKeyValueType.DATE_TIME  ? [Validators.required] : []]
    });
    userInputControl.get('value').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(userValue => {
      let value = userValue;
      if (valueConvertor) {
        value = valueConvertor(value as number);
      }
      (userInput.info.keyFilterPredicate as any).value.userValue = value;
    });
    return userInputControl;
  }

  userInputsFormArray(): FormArray {
    return this.userFilterFormGroup.get('userInputs') as FormArray;
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
