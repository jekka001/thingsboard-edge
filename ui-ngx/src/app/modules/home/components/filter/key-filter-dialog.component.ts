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
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import {
  EntityKeyType,
  entityKeyTypeTranslationMap,
  EntityKeyValueType,
  entityKeyValueTypesMap,
  KeyFilterInfo,
  KeyFilterPredicate
} from '@shared/models/query/query.models';
import { DialogService } from '@core/services/dialog.service';
import { TranslateService } from '@ngx-translate/core';
import { EntityField, entityFields } from '@shared/models/entity.models';
import { Observable } from 'rxjs';
import { filter, map, startWith } from 'rxjs/operators';

export interface KeyFilterDialogData {
  keyFilter: KeyFilterInfo;
  isAdd: boolean;
  displayUserParameters: boolean;
  allowUserDynamicSource: boolean;
  readonly: boolean;
  telemetryKeysOnly: boolean;
}

@Component({
  selector: 'tb-key-filter-dialog',
  templateUrl: './key-filter-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: KeyFilterDialogComponent}],
  styleUrls: ['./key-filter-dialog.component.scss']
})
export class KeyFilterDialogComponent extends
  DialogComponent<KeyFilterDialogComponent, KeyFilterInfo>
  implements OnInit, ErrorStateMatcher {

  keyFilterFormGroup: FormGroup;

  entityKeyTypes =
    this.data.telemetryKeysOnly ?
      [EntityKeyType.ATTRIBUTE, EntityKeyType.TIME_SERIES] :
      [EntityKeyType.ENTITY_FIELD, EntityKeyType.ATTRIBUTE, EntityKeyType.TIME_SERIES];

  entityKeyTypeTranslations = entityKeyTypeTranslationMap;

  entityKeyValueTypesKeys = Object.keys(EntityKeyValueType);

  entityKeyValueTypeEnum = EntityKeyValueType;

  entityKeyValueTypes = entityKeyValueTypesMap;

  submitted = false;

  entityFields: { [fieldName: string]: EntityField };

  entityFieldsList: string[];

  readonly entityField = EntityKeyType.ENTITY_FIELD;

  filteredEntityFields: Observable<string[]>;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: KeyFilterDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<KeyFilterDialogComponent, KeyFilterInfo>,
              private dialogs: DialogService,
              private translate: TranslateService,
              private fb: FormBuilder) {
    super(store, router, dialogRef);

    this.keyFilterFormGroup = this.fb.group(
      {
        key: this.fb.group(
          {
            type: [this.data.keyFilter.key.type, [Validators.required]],
            key: [this.data.keyFilter.key.key, [Validators.required]]
          }
        ),
        valueType: [this.data.keyFilter.valueType, [Validators.required]],
        predicates: [this.data.keyFilter.predicates, [Validators.required]]
      }
    );

    if (!this.data.readonly) {
      this.keyFilterFormGroup.get('valueType').valueChanges.subscribe((valueType: EntityKeyValueType) => {
        const prevValue: EntityKeyValueType = this.keyFilterFormGroup.value.valueType;
        const predicates: KeyFilterPredicate[] = this.keyFilterFormGroup.get('predicates').value;
        if (prevValue && prevValue !== valueType && predicates && predicates.length) {
          this.dialogs.confirm(this.translate.instant('filter.key-value-type-change-title'),
            this.translate.instant('filter.key-value-type-change-message')).subscribe(
            (result) => {
              if (result) {
                this.keyFilterFormGroup.get('predicates').setValue([]);
              } else {
                this.keyFilterFormGroup.get('valueType').setValue(prevValue, {emitEvent: false});
              }
            }
          );
        }
      });

      this.keyFilterFormGroup.get('key.key').valueChanges.pipe(
        filter((keyName) => this.keyFilterFormGroup.get('key.type').value === this.entityField && this.entityFields.hasOwnProperty(keyName))
      ).subscribe((keyName: string) => {
        const prevValueType: EntityKeyValueType = this.keyFilterFormGroup.value.valueType;
        const newValueType = this.entityFields[keyName]?.time ? EntityKeyValueType.DATE_TIME : EntityKeyValueType.STRING;
        if (prevValueType !== newValueType) {
          this.keyFilterFormGroup.get('valueType').patchValue(newValueType, {emitEvent: false});
        }
      });
    } else {
      this.keyFilterFormGroup.disable({emitEvent: false});
    }

    this.entityFields = entityFields;
    this.entityFieldsList = Object.values(entityFields).map(entityField => entityField.keyName).sort();
  }

  ngOnInit(): void {
    this.filteredEntityFields = this.keyFilterFormGroup.get('key.key').valueChanges.pipe(
      startWith(''),
      map(value => {
        return this.entityFieldsList.filter(option => option.startsWith(value));
      })
    );
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
    if (this.keyFilterFormGroup.valid) {
      const keyFilter: KeyFilterInfo = this.keyFilterFormGroup.getRawValue();
      this.dialogRef.close(keyFilter);
    }
  }
}
