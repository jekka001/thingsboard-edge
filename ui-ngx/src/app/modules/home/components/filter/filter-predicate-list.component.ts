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
  FormGroup,
  NG_VALUE_ACCESSOR,
  Validators
} from '@angular/forms';
import { Observable, of, Subscription } from 'rxjs';
import {
  ComplexFilterPredicateInfo,
  ComplexOperation,
  complexOperationTranslationMap,
  createDefaultFilterPredicateInfo,
  EntityKeyValueType,
  KeyFilterPredicateInfo
} from '@shared/models/query/query.models';
import {
  ComplexFilterPredicateDialogComponent,
  ComplexFilterPredicateDialogData
} from '@home/components/filter/complex-filter-predicate-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { map } from 'rxjs/operators';

@Component({
  selector: 'tb-filter-predicate-list',
  templateUrl: './filter-predicate-list.component.html',
  styleUrls: ['./filter-predicate-list.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => FilterPredicateListComponent),
      multi: true
    }
  ]
})
export class FilterPredicateListComponent implements ControlValueAccessor, OnInit {

  @Input() disabled: boolean;

  @Input() valueType: EntityKeyValueType;

  @Input() key: string;

  @Input() operation: ComplexOperation = ComplexOperation.AND;

  @Input() displayUserParameters = true;

  @Input() allowUserDynamicSource = true;

  @Input() onlyUserDynamicSource = false;

  filterListFormGroup: FormGroup;

  valueTypeEnum = EntityKeyValueType;

  complexOperationTranslations = complexOperationTranslationMap;

  private propagateChange = null;

  private valueChangeSubscription: Subscription = null;

  constructor(private fb: FormBuilder,
              private dialog: MatDialog) {
  }

  ngOnInit(): void {
    this.filterListFormGroup = this.fb.group({});
    this.filterListFormGroup.addControl('predicates',
      this.fb.array([]));
  }

  predicatesFormArray(): FormArray {
    return this.filterListFormGroup.get('predicates') as FormArray;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.filterListFormGroup.disable({emitEvent: false});
    } else {
      this.filterListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(predicates: Array<KeyFilterPredicateInfo>): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const predicateControls: Array<AbstractControl> = [];
    if (predicates) {
      for (const predicate of predicates) {
        predicateControls.push(this.fb.control(predicate, [Validators.required]));
      }
    }
    this.filterListFormGroup.setControl('predicates', this.fb.array(predicateControls));
    this.valueChangeSubscription = this.filterListFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    if (this.disabled) {
      this.filterListFormGroup.disable({emitEvent: false});
    } else {
      this.filterListFormGroup.enable({emitEvent: false});
    }
  }

  public removePredicate(index: number) {
    (this.filterListFormGroup.get('predicates') as FormArray).removeAt(index);
  }

  public addPredicate(complex: boolean) {
    const predicatesFormArray = this.filterListFormGroup.get('predicates') as FormArray;
    const predicate = createDefaultFilterPredicateInfo(this.valueType, complex);
    let observable: Observable<KeyFilterPredicateInfo>;
    if (complex) {
      observable = this.openComplexFilterDialog(predicate);
    } else {
      observable = of(predicate);
    }
    observable.subscribe((result) => {
      if (result) {
        predicatesFormArray.push(this.fb.control(result, [Validators.required]));
      }
    });
  }

  private openComplexFilterDialog(predicate: KeyFilterPredicateInfo): Observable<KeyFilterPredicateInfo> {
    return this.dialog.open<ComplexFilterPredicateDialogComponent, ComplexFilterPredicateDialogData,
      ComplexFilterPredicateInfo>(ComplexFilterPredicateDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        complexPredicate: predicate.keyFilterPredicate as ComplexFilterPredicateInfo,
        readonly: this.disabled,
        valueType: this.valueType,
        key: this.key,
        isAdd: true,
        displayUserParameters: this.displayUserParameters,
        allowUserDynamicSource: this.allowUserDynamicSource,
        onlyUserDynamicSource: this.onlyUserDynamicSource
      }
    }).afterClosed().pipe(
      map((result) => {
        if (result) {
          predicate.keyFilterPredicate = result;
          return predicate;
        } else {
          return null;
        }
      })
    );
  }

  private updateModel() {
    const predicates: Array<KeyFilterPredicateInfo> = this.filterListFormGroup.getRawValue().predicates;
    if (this.filterListFormGroup.valid && predicates.length) {
      this.propagateChange(predicates);
    } else {
      this.propagateChange(null);
    }
  }
}
