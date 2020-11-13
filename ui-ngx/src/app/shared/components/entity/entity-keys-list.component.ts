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

import { COMMA, ENTER, SEMICOLON } from '@angular/cdk/keycodes';
import { AfterViewInit, Component, ElementRef, forwardRef, Input, OnInit, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { map, mergeMap, share } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { EntityId } from '@shared/models/id/entity-id';
import { EntityService } from '@core/http/entity.service';
import { MatAutocomplete, MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipInputEvent, MatChipList } from '@angular/material/chips';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { isEqual } from '@core/utils';

@Component({
  selector: 'tb-entity-keys-list',
  templateUrl: './entity-keys-list.component.html',
  styleUrls: ['./entity-keys-list.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EntityKeysListComponent),
      multi: true
    }
  ]
})
export class EntityKeysListComponent implements ControlValueAccessor, OnInit, AfterViewInit {

  keysListFormGroup: FormGroup;

  modelValue: Array<string> | null;

  entityIdValue: EntityId;

  @Input()
  set entityId(entityId: EntityId) {
    if (!isEqual(this.entityIdValue, entityId)) {
      this.entityIdValue = entityId;
      this.dirty = true;
    }
  }

  @Input()
  keysText: string;

  @Input()
  dataKeyType: DataKeyType;

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

  @ViewChild('keyInput') keyInput: ElementRef<HTMLInputElement>;
  @ViewChild('keyAutocomplete') matAutocomplete: MatAutocomplete;
  @ViewChild('chipList') chipList: MatChipList;

  filteredKeys: Observable<Array<string>>;

  separatorKeysCodes: number[] = [ENTER, COMMA, SEMICOLON];

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              private entityService: EntityService,
              private fb: FormBuilder) {
    this.keysListFormGroup = this.fb.group({
      key: [null]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredKeys = this.keysListFormGroup.get('key').valueChanges
      .pipe(
        map((value) => value ? value : ''),
        mergeMap(name => this.fetchKeys(name) ),
        share()
      );
  }

  ngAfterViewInit(): void {}

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.keysListFormGroup.disable({emitEvent: false});
    } else {
      this.keysListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: Array<string> | null): void {
    this.searchText = '';
    if (value != null) {
      this.modelValue = [...value];
    } else {
      this.modelValue = [];
    }
  }

  onFocus() {
    if (this.dirty) {
      this.keysListFormGroup.get('key').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  addKey(key: string): void {
    if (!this.modelValue || this.modelValue.indexOf(key) === -1) {
      if (!this.modelValue) {
        this.modelValue = [];
      }
      this.modelValue.push(key);
      if (this.required) {
        this.chipList.errorState = false;
      }
    }
    this.propagateChange(this.modelValue);
  }

  add(event: MatChipInputEvent): void {
   if (!this.matAutocomplete.isOpen) {
      const value = event.value;
      if ((value || '').trim()) {
        this.addKey(value.trim());
      }
      this.clear('');
   }
  }

  remove(key: string) {
    const index = this.modelValue.indexOf(key);
    if (index >= 0) {
      this.modelValue.splice(index, 1);
      if (!this.modelValue.length) {
        if (this.required) {
          this.chipList.errorState = true;
        }
      }
      this.propagateChange(this.modelValue.length ? this.modelValue : null);
    }
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    this.addKey(event.option.viewValue);
    this.clear('');
  }

  displayKeyFn(key?: string): string | undefined {
    return key ? key : undefined;
  }

  fetchKeys(searchText?: string): Observable<Array<string>> {
    this.searchText = searchText;
    return this.entityIdValue ? this.entityService.getEntityKeys(this.entityIdValue, searchText,
      this.dataKeyType, {ignoreLoading: true}).pipe(
      map((data) => data ? data : [])) : of([]);
  }

  clear(value: string = '') {
    this.keyInput.nativeElement.value = value;
    this.keysListFormGroup.get('key').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.keyInput.nativeElement.blur();
      this.keyInput.nativeElement.focus();
    }, 0);
  }

}
