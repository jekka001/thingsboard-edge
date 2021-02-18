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

import {
  AfterViewInit,
  Component,
  ElementRef,
  forwardRef,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { filter, map, mergeMap, share, tap } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { MatAutocomplete } from '@angular/material/autocomplete';
import { MatChipList } from '@angular/material/chips';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { Operation, operationTypeTranslationMap, Resource } from '@shared/models/security.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';

interface OperationTypeInfo {
  name: string;
  value: Operation;
}

@Component({
  selector: 'tb-operation-type-list',
  templateUrl: './operation-type-list.component.html',
  styleUrls: ['./operation-type-list.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => OperationTypeListComponent),
      multi: true
    }
  ]
})
export class OperationTypeListComponent implements ControlValueAccessor, OnInit, AfterViewInit, OnChanges {

  operationTypeListFormGroup: FormGroup;

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    const newVal = coerceBooleanProperty(value);
    if (this.requiredValue !== newVal) {
      this.requiredValue = newVal;
      this.updateValidators();
    }
  }

  @Input()
  disabled: boolean;

  private groupRoleOperationsValue: boolean;
  get groupRoleOperations(): boolean {
    return this.groupRoleOperationsValue;
  }
  @Input()
  set groupRoleOperations(value: boolean) {
    const newVal = coerceBooleanProperty(value);
    if (this.groupRoleOperationsValue !== newVal) {
      this.groupRoleOperationsValue = newVal;
    }
  }

  @Input()
  resource: Resource;

  @ViewChild('operationTypeInput') operationTypeInput: ElementRef<HTMLInputElement>;
  @ViewChild('operationTypeAutocomplete') operationTypeAutocomplete: MatAutocomplete;
  @ViewChild('chipList', {static: true}) chipList: MatChipList;

  allOperationTypeList: Array<OperationTypeInfo> = [];
  operationTypeList: Array<OperationTypeInfo> = [];
  filteredOperationTypeList: Observable<Array<OperationTypeInfo>>;

  placeholder = this.translate.instant('permission.operation.enter-operation');
  secondaryPlaceholder = '+' + this.translate.instant('permission.operation.operation');

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              private userPermissionsService: UserPermissionsService,
              private fb: FormBuilder) {
    this.operationTypeListFormGroup = this.fb.group({
      operationTypeList: [this.operationTypeList, this.required ? [Validators.required] : []],
      operationType: [null]
    });
  }

  updateValidators() {
    this.operationTypeListFormGroup.get('operationTypeList').setValidators(this.required ? [Validators.required] : []);
    this.operationTypeListFormGroup.get('operationTypeList').updateValueAndValidity();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {

    this.updateOperationsList();

    this.filteredOperationTypeList = this.operationTypeListFormGroup.get('operationType').valueChanges
      .pipe(
        tap((value) => {
          if (value && typeof value !== 'string') {
            this.add(value);
          } else if (value === null) {
            this.clear(this.operationTypeInput.nativeElement.value);
          }
        }),
        filter((value) => typeof value === 'string'),
        map((value) => value ? (typeof value === 'string' ? value : value.name) : ''),
        mergeMap(name => this.fetchOperationTypes(name) ),
        share()
      );
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'resource' && change.currentValue) {
          this.reset();
        }
      }
    }
  }

  private reset() {
    this.updateOperationsList();
    this.validateOperationList();
  }

  private updateOperationsList() {

    this.allOperationTypeList = [];

    let operationTypes: Array<Operation>;
    if (this.groupRoleOperations) {
      operationTypes = this.userPermissionsService.getAllowedGroupRoleOperations()
    } else if (this.resource) {
      operationTypes = this.userPermissionsService.getOperationsByResource(this.resource);
    } else {
      operationTypes = [];
    }

    this.allOperationTypeList = operationTypes.map(operationType => {
      return {
        name: this.translate.instant(operationTypeTranslationMap.get(operationType)),
        value: operationType
      }
    }).sort(this.sortOperation);
  }

  private sortOperation(a: OperationTypeInfo, b: OperationTypeInfo): number {
    if (a.value === 'ALL' || b.value === 'ALL') return a.value === 'ALL' ? -1 : 1;
    if (a.name > b.name) return 1;
    if (a.name < b.name) return -1;
    return 0;
  }

  private validateOperationList() {
    let update = false;
    const newList: OperationTypeInfo[] = [];
    if (this.operationTypeList && this.operationTypeList.length) {
      for (const operation of this.operationTypeList) {
        const result = this.allOperationTypeList.find(existingOperation => existingOperation.value === operation.value);
        if (result) {
          newList.push(result);
        } else {
          update = true;
        }
      }
    }
    if (update) {
      this.operationTypeList = newList;
      this.checkOperationTypeAll();
      this.operationTypeListFormGroup.get('operationTypeList').setValue(this.operationTypeList);
    }
    if (this.operationTypeInput) {
      this.operationTypeInput.nativeElement.value = '';
    }
    this.operationTypeListFormGroup.get('operationType').patchValue('', {emitEvent: false});
    if (update) {
      this.notifyValueChanged();
    }
    this.dirty = true;
  }

  ngAfterViewInit(): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.operationTypeListFormGroup.disable({emitEvent: false});
    } else {
      this.operationTypeListFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: Array<Operation> | null): void {
    this.searchText = '';
    if (value != null && value.length > 0) {
      this.operationTypeList = [];
      value.forEach((operationType) => {
        this.operationTypeList.push({
          name: operationTypeTranslationMap.has(operationType) ? this.translate.instant(operationTypeTranslationMap.get(operationType)) : 'Unknown',
          value: operationType
        });
      });
      this.checkOperationTypeAll();
      this.operationTypeListFormGroup.get('operationTypeList').setValue(this.operationTypeList);
    } else {
      this.operationTypeList = [];
      this.operationTypeListFormGroup.get('operationTypeList').setValue(this.operationTypeList);
    }
    this.dirty = true;
  }

  add(operationType: OperationTypeInfo): void {
    if (this.operationTypeList.findIndex(operation => operation.value === operationType.value) === -1) {
      this.operationTypeList.push(operationType);
      this.checkOperationTypeAll();
      this.operationTypeListFormGroup.get('operationTypeList').setValue(this.operationTypeList);
      this.notifyValueChanged();
    }
    this.clear();
  }

  remove(operationType: OperationTypeInfo) {
    const index = this.operationTypeList.indexOf(operationType);
    if (index >= 0) {
      this.operationTypeList.splice(index, 1);
      this.checkOperationTypeAll();
      this.operationTypeListFormGroup.get('operationTypeList').setValue(this.operationTypeList);
      this.notifyValueChanged();
      this.clear();
    }
  }

  private notifyValueChanged() {
    let modelValue = this.operationTypeList.map(operationType => operationType.value);
    if (!modelValue.length) {
      modelValue = null;
    }
    this.propagateChange(modelValue);
  }

  private checkOperationTypeAll() {
    const result = this.operationTypeList.filter(operationType => operationType.value === Operation.ALL);
    if (result && result.length) {
      this.secondaryPlaceholder = '';
      if (this.operationTypeList.length > 1) {
        this.operationTypeList = result;
      }
    } else {
      this.secondaryPlaceholder = '+' + this.translate.instant('permission.operation.operation');
    }
  }

  displayOperationTypeFn(operationType?: OperationTypeInfo): string | undefined {
    return operationType ? operationType.name : undefined;
  }

  fetchOperationTypes(searchText?: string): Observable<Array<OperationTypeInfo>> {
    this.searchText = searchText;
    let result = this.allOperationTypeList;
    if (searchText && searchText.length) {
      result = this.allOperationTypeList.filter((operationTypeInfo) =>
        operationTypeInfo.name.toLowerCase().includes(searchText.toLowerCase()));
    }
    return of(result);
  }

  onFocus() {
    if (this.dirty) {
      this.operationTypeListFormGroup.get('operationType').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  clear(value: string = '') {
    this.operationTypeInput.nativeElement.value = value;
    this.operationTypeListFormGroup.get('operationType').patchValue(value, {emitEvent: true});
    setTimeout(() => {
      this.operationTypeInput.nativeElement.blur();
      this.operationTypeInput.nativeElement.focus();
    }, 0);
  }

}
