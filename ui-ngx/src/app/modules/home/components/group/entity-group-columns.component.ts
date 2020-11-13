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
  FormGroup,
  NG_VALUE_ACCESSOR,
  Validators
} from '@angular/forms';
import { EntityType } from '@shared/models/entity-type.models';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  EntityGroupColumn,
  EntityGroupColumnType,
  entityGroupEntityFields,
  EntityGroupSortOrder
} from '@shared/models/entity-group.models';
import { Subscription } from 'rxjs';
import { DndDropEvent } from 'ngx-drag-drop/dnd-dropzone.directive';
import { isUndefined } from '@core/utils';

@Component({
  selector: 'tb-entity-group-columns',
  templateUrl: './entity-group-columns.component.html',
  styleUrls: ['./entity-group-columns.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EntityGroupColumnsComponent),
      multi: true
    }
  ]
})
export class EntityGroupColumnsComponent extends PageComponent implements ControlValueAccessor, OnInit {

  @Input() disabled: boolean;

  @Input() entityType: EntityType;

  columnsFormGroup: FormGroup;

  private propagateChange = null;

  private valueChangeSubscription: Subscription = null;

  constructor(protected store: Store<AppState>,
              private fb: FormBuilder) {
    super(store);
  }

  ngOnInit(): void {
    this.columnsFormGroup = this.fb.group(
      {
        columns: this.fb.array([])
      }
    );
  }

  columnsFormArray(): FormArray {
    return this.columnsFormGroup.get('columns') as FormArray;
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.columnsFormGroup.disable({emitEvent: false});
    } else {
      this.columnsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(columns: EntityGroupColumn[]): void {
    if (this.valueChangeSubscription) {
      this.valueChangeSubscription.unsubscribe();
    }
    const columnsControls: Array<AbstractControl> = [];
    if (columns) {
      columns.forEach((column) => {
        columnsControls.push(this.fb.control(column, [Validators.required]));
      });
    }
    this.columnsFormGroup.setControl('columns', this.fb.array(columnsControls));
    if (this.disabled) {
      this.columnsFormGroup.disable({emitEvent: false});
    } else {
      this.columnsFormGroup.enable({emitEvent: false});
    }
    this.valueChangeSubscription = this.columnsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
  }

  public removeColumn(index: number) {
    (this.columnsFormGroup.get('columns') as FormArray).removeAt(index);
  }

  public addColumn() {
    const columnsArray = this.columnsFormGroup.get('columns') as FormArray;
    columnsArray.push(this.fb.control({
      type: EntityGroupColumnType.ENTITY_FIELD,
      key: entityGroupEntityFields.name.value,
      sortOrder: EntityGroupSortOrder.NONE,
      mobileHide: false
    }, [Validators.required]));
  }

  public defaultSortOrderChanged(index: number, sortOrder?: EntityGroupSortOrder) {
    const columnsControls: FormArray = this.columnsFormGroup.get('columns') as FormArray;
    const column: EntityGroupColumn = columnsControls.at(index).value;
    sortOrder = sortOrder || column.sortOrder;
    if (sortOrder !== EntityGroupSortOrder.NONE) {
      for (let i = 0; i < columnsControls.length; i++) {
        if (i !== index) {
          const otherColumn: EntityGroupColumn = columnsControls.at(i).value;
          otherColumn.sortOrder = EntityGroupSortOrder.NONE;
          columnsControls.at(i).setValue(otherColumn);
        }
      }
    }
  }

  public updateColumn(index: number, column: EntityGroupColumn) {
    this.defaultSortOrderChanged(index, column.sortOrder);
  }

  public dndMoved(index: number) {
    this.columnsFormArray().removeAt(index)
  }

  public onDrop(event: DndDropEvent) {
    let index = event.index;
    if(isUndefined(index)) {
      index = this.columnsFormArray().length;
    }
    this.columnsFormArray().insert(index,
      this.fb.control(event.data, [Validators.required])
    );
  }

  private updateModel() {
    if (this.columnsFormGroup.valid) {
      const columns: EntityGroupColumn[] = this.columnsFormGroup.get('columns').value;
      this.propagateChange(columns);
    } else {
      this.propagateChange(null);
    }
  }
}
