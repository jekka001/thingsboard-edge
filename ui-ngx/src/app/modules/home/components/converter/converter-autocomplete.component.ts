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

import { AfterViewInit, Component, ElementRef, forwardRef, Input, OnInit, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable } from 'rxjs';
import { map, mergeMap, share, tap } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { Converter, ConverterType } from '@shared/models/converter.models';
import { ConverterService } from '@core/http/converter.service';
import { ConverterId } from '@shared/models/id/converter-id';
import { PageLink } from '@shared/models/page/page-link';

@Component({
  selector: 'tb-converter-autocomplete',
  templateUrl: './converter-autocomplete.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => ConverterAutocompleteComponent),
    multi: true
  }]
})
export class ConverterAutocompleteComponent implements ControlValueAccessor, OnInit, AfterViewInit {

  selectConverterFormGroup: FormGroup;

  modelValue: ConverterId | string | null;

  converterTypeValue: ConverterType;

  @Input()
  useFullEntityId = false;

  @Input()
  set converterType(converterType: ConverterType) {
    if (this.converterTypeValue !== converterType) {
      this.converterTypeValue = converterType;
      this.load();
      this.reset();
      this.dirty = true;
    }
  }

  @Input()
  excludeEntityIds: Array<string>;

  @Input()
  labelText: string;

  @Input()
  requiredText: string;

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

  @ViewChild('converterInput', {static: true}) converterInput: ElementRef<HTMLElement>;

  entityText: string;
  entityRequiredText: string;

  filteredEntities: Observable<Array<Converter>>;

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              private converterService: ConverterService,
              private fb: FormBuilder) {
    this.selectConverterFormGroup = this.fb.group({
      entity: [null]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredEntities = this.selectConverterFormGroup.get('entity').valueChanges
      .pipe(
        tap(value => {
          let modelValue;
          if (typeof value === 'string' || !value) {
            modelValue = null;
          } else {
            modelValue = this.useFullEntityId ? value.id : value.id.id;
          }
          this.updateView(modelValue);
          if (value === null) {
            this.clear();
          }
        }),
        // startWith<string | BaseData<EntityId>>(''),
        map(value => value ? (typeof value === 'string' ? value : value.name) : ''),
        mergeMap(name => this.fetchEntities(name) ),
        share()
      );
  }

  ngAfterViewInit(): void {}

  load(): void {
    this.entityText = 'converter.converter';
    this.entityRequiredText = 'converter.converter-required';
    if (this.labelText && this.labelText.length) {
      this.entityText = this.labelText;
    }
    if (this.requiredText && this.requiredText.length) {
      this.entityRequiredText = this.requiredText;
    }
    const currentEntity = this.getCurrentEntity();
    if (currentEntity) {
      const currentConverterType = currentEntity.type;
      if (this.converterTypeValue && currentConverterType !== this.converterTypeValue) {
        this.reset();
      }
    }
  }

  getCurrentEntity(): Converter | null {
    const currentEntity = this.selectConverterFormGroup.get('entity').value;
    if (currentEntity && typeof currentEntity !== 'string') {
      return currentEntity as Converter;
    } else {
      return null;
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.selectConverterFormGroup.disable({emitEvent: false});
    } else {
      this.selectConverterFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: string | ConverterId | null): void {
    this.searchText = '';
    if (value != null) {
      const converterId = typeof value === 'string' ? value : value.id;
      this.converterService.getConverter(converterId, {ignoreLoading: true}).subscribe(
        (entity) => {
          this.modelValue = this.useFullEntityId ? entity.id : entity.id.id;
          this.selectConverterFormGroup.get('entity').patchValue(entity, {emitEvent: false});
        }
      );
    } else {
      this.modelValue = null;
      this.selectConverterFormGroup.get('entity').patchValue('', {emitEvent: false});
    }
    this.dirty = true;
  }

  onFocus() {
    if (this.dirty) {
      this.selectConverterFormGroup.get('entity').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  reset() {
    this.selectConverterFormGroup.get('entity').patchValue('', {emitEvent: false});
  }

  updateView(value: string | ConverterId | null) {
    if (this.modelValue !== value) {
      this.modelValue = value;
      this.propagateChange(this.modelValue);
    }
  }

  displayEntityFn(converter?: Converter): string | undefined {
    return converter ? converter.name : undefined;
  }

  fetchEntities(searchText?: string): Observable<Array<Converter>> {
    this.searchText = searchText;
    let limit = 50;
    if (this.excludeEntityIds && this.excludeEntityIds.length) {
      limit += this.excludeEntityIds.length;
    }
    const pageLink = new PageLink(limit, 0, this.searchText);
    return this.converterService.getConverters(pageLink, {ignoreLoading: true}).pipe(
      map((data) => {
          if (data) {
            let entities: Array<Converter> = [];
            if (this.excludeEntityIds && this.excludeEntityIds.length) {
              entities = data.data.filter((entity) => this.excludeEntityIds.indexOf(entity.id.id) === -1);
            } else {
              entities = data.data;
            }
            if (this.converterTypeValue) {
              entities = entities.filter((converter) => converter.type === this.converterTypeValue);
            }
            return entities;
          } else {
            return [];
          }
        }
      ));
  }

  clear() {
    this.selectConverterFormGroup.get('entity').patchValue('', {emitEvent: true});
    setTimeout(() => {
      this.converterInput.nativeElement.blur();
      this.converterInput.nativeElement.focus();
    }, 0);
  }

}
