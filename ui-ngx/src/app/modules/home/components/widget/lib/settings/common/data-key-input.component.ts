///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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
  ElementRef,
  EventEmitter,
  forwardRef,
  HostBinding,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormControl,
  Validators
} from '@angular/forms';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { COMMA, ENTER, SEMICOLON } from '@angular/cdk/keycodes';
import { MatAutocomplete, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatChipGrid, MatChipInputEvent } from '@angular/material/chips';
import { coerceBoolean } from '@shared/decorators/coercion';
import { DataKey, DatasourceType, widgetType } from '@shared/models/widget.models';
import { Observable, of } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { TruncatePipe } from '@shared/pipe/truncate.pipe';
import { UtilsService } from '@core/services/utils.service';
import { alarmFields } from '@shared/models/alarm.models';
import { filter, map, mergeMap, publishReplay, refCount, share, tap } from 'rxjs/operators';
import { AggregationType } from '@shared/models/time/time.models';
import { DataKeysCallbacks } from '@home/components/widget/config/data-keys.component.models';
import { IAliasController } from '@core/api/widget-api.models';

@Component({
  selector: 'tb-data-key-input',
  templateUrl: './data-key-input.component.html',
  styleUrls: ['./data-key-input.component.scss', '../../../config/data-keys.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DataKeyInputComponent),
      multi: true
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class DataKeyInputComponent implements ControlValueAccessor, OnInit, OnChanges {

  @HostBinding('class')
  hostClass = 'tb-data-key-input';

  DataKeyType = DataKeyType;

  separatorKeysCodes: number[] = [ENTER, COMMA, SEMICOLON];

  @ViewChild('keyInput') keyInput: ElementRef<HTMLInputElement>;
  @ViewChild('keyAutocomplete') matAutocomplete: MatAutocomplete;
  @ViewChild(MatAutocompleteTrigger) autocomplete: MatAutocompleteTrigger;
  @ViewChild('chipList') chipList: MatChipGrid;

  @Input()
  disabled: boolean;

  @Input()
  @coerceBoolean()
  required = false;

  @Input()
  requiredText: string;

  @Input()
  @coerceBoolean()
  isLatestDataKeys = false;

  @Input()
  @coerceBoolean()
  editable = true;

  @Input()
  @coerceBoolean()
  removable = true;

  @Input()
  datasourceType: DatasourceType;

  @Input()
  entityAliasId: string;

  @Input()
  entityAlias: string;

  @Input()
  deviceId: string;

  @Input()
  widgetType: widgetType;

  @Input()
  callbacks: DataKeysCallbacks;

  @Input()
  aliasController: IAliasController;

  @Input()
  dataKeyType: DataKeyType;

  @Input()
  dataKeyTypes: DataKeyType[];

  @Input()
  generateKey: (key: DataKey) => DataKey = (key) => key;

  @Output()
  keyEdit = new EventEmitter<DataKey>();

  keysFormControl: UntypedFormControl;

  keyFormControl: UntypedFormControl;

  modelValue: DataKey;

  filteredKeys: Observable<Array<DataKey>>;

  keySearchText = '';

  alarmKeys: Array<DataKey>;
  functionTypeKeys: Array<DataKey>;

  allowedDataKeyTypes: DataKeyType[] = [];

  private latestKeySearchTextResult: Array<DataKey> = null;
  private keyFetchObservable$: Observable<Array<DataKey>> = null;

  get isEntityDatasource(): boolean {
    return [DatasourceType.device, DatasourceType.entity].includes(this.datasourceType);
  }

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private dialog: MatDialog,
              private cd: ChangeDetectorRef,
              public translate: TranslateService,
              public truncate: TruncatePipe,
              private utils: UtilsService) {
  }

  ngOnInit() {
    this.alarmKeys = [];
    for (const name of Object.keys(alarmFields)) {
      this.alarmKeys.push({
        name,
        type: DataKeyType.alarm
      });
    }
    this.functionTypeKeys = [];
    for (const type of this.utils.getPredefinedFunctionsList()) {
      this.functionTypeKeys.push({
        name: type,
        type: DataKeyType.function
      });
    }
    this.keyFormControl = this.fb.control('');
    this.keysFormControl = this.fb.control([], this.required ? [Validators.required] : []);
    this.filteredKeys = this.keyFormControl.valueChanges
    .pipe(
      tap((value: string | DataKey) => {
        if (value && typeof value !== 'string') {
          this.addKeyFromChipValue(value);
        } else if (value === null) {
          this.clearKeyChip(this.keyInput.nativeElement.value);
        }
      }),
      filter((value) => typeof value === 'string'),
      map((value) => value ? value : ''),
      mergeMap(name => this.fetchKeys(name) ),
      share()
    );
    this.updateAllowedDataKeys();
  }

  private updateAllowedDataKeys() {
    this.allowedDataKeyTypes.length = 0;
    if (this.dataKeyTypes?.length) {
      this.allowedDataKeyTypes = this.allowedDataKeyTypes.concat(this.dataKeyTypes);
    } else {
      this.allowedDataKeyTypes = [DataKeyType.timeseries];
      if (this.isLatestDataKeys || this.widgetType === widgetType.latest || this.widgetType === widgetType.alarm) {
        this.allowedDataKeyTypes.push(DataKeyType.attribute);
        this.allowedDataKeyTypes.push(DataKeyType.entityField);
        if (this.widgetType === widgetType.alarm) {
          this.allowedDataKeyTypes.push(DataKeyType.alarm);
        }
      }
    }
  }

  private reset() {
    if (this.keyInput) {
      this.keyInput.nativeElement.value = '';
    }
    this.keyFormControl.patchValue('', {emitEvent: false});
    this.latestKeySearchTextResult = null;
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (['deviceId', 'entityAliasId', 'entityAlias', 'isLatestDataKeys'].includes(propName)) {
          this.clearKeySearchCache();
          if (propName === 'isLatestDataKeys') {
            this.updateAllowedDataKeys();
            if (!this.isLatestDataKeys) {
              if (this.widgetType === widgetType.timeseries &&
                this.modelValue?.type &&
                this.modelValue.type !== DataKeyType.timeseries) {
                setTimeout(() => {
                  this.modelValue = null;
                  this.updateModel();
                  this.clearKeyChip('', false);
                }, 1);
              }
            }
          }
        } else if (['datasourceType'].includes(propName)) {
          if ([DatasourceType.device, DatasourceType.entity].includes(change.previousValue) &&
            [DatasourceType.device, DatasourceType.entity].includes(change.currentValue)) {
            this.clearKeySearchCache();
          } else {
            this.clearKeySearchCache();
            setTimeout(() => {
              this.reset();
            }, 1);
          }
        }
      }
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.keysFormControl.disable({emitEvent: false});
    } else {
      this.keysFormControl.enable({emitEvent: false});
    }
  }

  writeValue(value: DataKey): void {
    this.modelValue = (value?.name && value?.type) ? value : null;
    this.keysFormControl.patchValue(this.modelValue ? [this.modelValue] : [], {emitEvent: false});
    this.cd.markForCheck();
  }

  dataKeyHasAggregation(): boolean {
    return this.widgetType === widgetType.latest && this.modelValue?.type === DataKeyType.timeseries
      && this.modelValue?.aggregationType && this.modelValue?.aggregationType !== AggregationType.NONE;
  }

  dataKeyHasPostprocessing(): boolean {
    return !!this.modelValue?.postFuncBody;
  }

  displayKeyFn(key?: DataKey): string | undefined {
    return key ? key.name : undefined;
  }

  createKey(name: string, dataKeyType: DataKeyType = this.dataKeyType) {
    this.addKeyFromChipValue({name: name ? name.trim() : '', type: dataKeyType});
  }

  addKey(event: MatChipInputEvent): void {
    const value = event.value;
    if ((value || '').trim() && this.dataKeyType) {
      this.addKeyFromChipValue({name: value.trim(), type: this.dataKeyType});
    } else {
      this.clearKeyChip();
    }
  }

  editKey() {
    this.keyEdit.emit(this.modelValue);
  }

  removeKey() {
    this.modelValue = null;
    this.updateModel();
    this.clearKeyChip();
  }

  textIsNotEmpty(text: string): boolean {
    return text && text.length > 0;
  }

  clearKeyChip(value: string = '', focus = true) {
    this.autocomplete.closePanel();
    this.keyInput.nativeElement.value = value;
    this.keyFormControl.patchValue(value, {emitEvent: focus});
    if (focus) {
      setTimeout(() => {
        this.keyInput.nativeElement.blur();
        this.keyInput.nativeElement.focus();
      }, 0);
    }
  }

  onKeyInputFocus() {
    if (!this.modelValue?.type) {
      this.keyFormControl.updateValueAndValidity({onlySelf: true, emitEvent: true});
    }
  }

  private fetchKeys(searchText?: string): Observable<Array<DataKey>> {
    if (this.keySearchText !== searchText || this.latestKeySearchTextResult === null) {
      this.keySearchText = searchText;
      const dataKeyFilter = this.createDataKeyFilter(this.keySearchText);
      return this.getKeys().pipe(
        map(name => name.filter(dataKeyFilter)),
        tap(res => this.latestKeySearchTextResult = res)
      );
    }
    return of(this.latestKeySearchTextResult);
  }

  private getKeys(): Observable<Array<DataKey>> {
    if (this.keyFetchObservable$ === null) {
      let fetchObservable: Observable<Array<DataKey>>;
      if (this.datasourceType === DatasourceType.function) {
        const targetKeysList = this.widgetType === widgetType.alarm ? this.alarmKeys : this.functionTypeKeys;
        fetchObservable = of(targetKeysList);
      } else if (this.datasourceType === DatasourceType.entity && (this.entityAliasId || this.entityAlias) ||
        this.datasourceType === DatasourceType.device && this.deviceId) {
        if (this.datasourceType === DatasourceType.device) {
          fetchObservable = this.callbacks.fetchEntityKeysForDevice(this.deviceId, this.allowedDataKeyTypes);
        } else {
          let entityAliasId = this.entityAliasId;
          if (!entityAliasId && this.entityAlias && this.aliasController) {
            entityAliasId = this.aliasController.getEntityAliasId(this.entityAlias);
          }
          fetchObservable = entityAliasId ? this.callbacks.fetchEntityKeys(entityAliasId, this.allowedDataKeyTypes) : of([]);
        }
      } else {
        fetchObservable = of([]);
      }
      this.keyFetchObservable$ = fetchObservable.pipe(
        publishReplay(1),
        refCount()
      );
    }
    return this.keyFetchObservable$;
  }

  private createDataKeyFilter(query: string): (key: DataKey) => boolean {
    const lowercaseQuery = query.toLowerCase();
    return key => key.name.toLowerCase().startsWith(lowercaseQuery);
  }

  private addKeyFromChipValue(chip: DataKey) {
    this.modelValue = this.generateKey(chip);
    this.updateModel();
    this.clearKeyChip('', false);
  }

  private clearKeySearchCache() {
    this.keySearchText = '';
    this.keyFetchObservable$ = null;
    this.latestKeySearchTextResult = null;
  }

  private updateModel() {
    this.keysFormControl.patchValue(this.modelValue ? [this.modelValue] : [], {emitEvent: false});
    this.propagateChange(this.modelValue);
  }

}
