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

import { Component, DestroyRef, forwardRef, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormControl,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormArray,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validator
} from '@angular/forms';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import {
  Datasource,
  DatasourceType,
  datasourceValid,
  WidgetConfigMode,
  widgetType
} from '@shared/models/widget.models';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { deepClone } from '@core/utils';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { UtilsService } from '@core/services/utils.service';
import { DataKeysCallbacks, DataKeySettingsFunction } from '@home/components/widget/config/data-keys.component.models';
import { TranslateService } from '@ngx-translate/core';
import { coerceBoolean } from '@shared/decorators/coercion';
import { FormProperty } from '@shared/models/dynamic-form.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-datasources',
  templateUrl: './datasources.component.html',
  styleUrls: ['./datasources.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DatasourcesComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => DatasourcesComponent),
      multi: true,
    }
  ]
})
export class DatasourcesComponent implements ControlValueAccessor, OnInit, Validator, OnChanges {

  datasourceType = DatasourceType;


  public get isAlarmSource(): boolean {
    return this.widgetConfigComponent.widgetType === widgetType.alarm;
  }

  public get basicMode(): boolean {
    return !this.widgetConfigComponent.widgetEditMode && this.configMode === WidgetConfigMode.basic;
  }

  public get maxDatasources(): number {
    return (this.forceSingleDatasource || this.isAlarmSource) ? 1 : this.widgetConfigComponent.modelValue?.typeParameters?.maxDatasources;
  }

  public get singleDatasource(): boolean {
    return this.maxDatasources === 1;
  }

  public get showAddDatasource(): boolean {
   return this.widgetConfigComponent.modelValue?.typeParameters &&
    (this.maxDatasources === -1 || this.datasourcesFormArray.length < this.maxDatasources);
  }

  public get dragDisabled(): boolean {
    return this.disabled || this.singleDatasource || this.datasourcesFormArray.length < 2;
  }

  @Input()
  disabled: boolean;

  @Input()
  @coerceBoolean()
  hideDatasourcesMode = false;

  @Input()
  @coerceBoolean()
  hideDatasourceLabel = false;

  @Input()
  @coerceBoolean()
  displayDatasourceFilterForBasicMode = false;

  @Input()
  @coerceBoolean()
  hideDataKeyLabel = false;

  @Input()
  @coerceBoolean()
  hideDataKeyColor = false;

  @Input()
  @coerceBoolean()
  hideDataKeyUnits = false;

  @Input()
  @coerceBoolean()
  hideDataKeyDecimals = false;

  @Input()
  @coerceBoolean()
  hideDataKeys = false;

  @Input()
  @coerceBoolean()
  hideLatestDataKeys = false;

  @Input()
  @coerceBoolean()
  hideAlarmFilter = false;

  @Input()
  @coerceBoolean()
  forceSingleDatasource = false;

  @Input()
  configMode: WidgetConfigMode;

  datasourcesFormGroup: UntypedFormGroup;

  timeseriesKeyError = false;

  datasourceError: string[] = [];

  datasourcesMode: DatasourceType;

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private utils: UtilsService,
              public translate: TranslateService,
              private widgetConfigComponent: WidgetConfigComponent,
              private destroyRef: DestroyRef) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
    if (this.validate(null)) {
      setTimeout(() => {
        this.datasourcesUpdated(this.datasourcesFormGroup.get('datasources').value);
      }, 0);
    }
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.datasourcesFormGroup.disable({emitEvent: false});
    } else {
      this.datasourcesFormGroup.enable({emitEvent: false});
    }
  }

  ngOnInit() {
    this.datasourcesFormGroup = this.fb.group({
      datasources: this.fb.array([])
    });
    this.datasourcesFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(
      () => {
        this.datasourcesUpdated(this.datasourcesFormGroup.get('datasources').value);
      }
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'configMode') {
          this.configModeChanged();
        }
      }
    }
  }

  writeValue(datasources?: Datasource[]): void {
    this.datasourcesFormArray.clear({emitEvent: false});
    this.datasourcesMode = this.detectDatasourcesMode(datasources);
    let changed = false;
    if (datasources) {
      let length;
      if (this.maxDatasources === -1) {
        length = datasources.length;
      } else {
        length = Math.min(this.maxDatasources, datasources.length);
      }
      for (let i = 0; i < length; i++) {
        const datasource = datasources[i];
        if (this.basicMode && datasource.type !== this.datasourcesMode) {
          datasource.type = this.datasourcesMode;
          changed = true;
        }
        this.datasourcesFormArray.push(this.fb.control(datasource, []), {emitEvent: false});
      }
    }
    if (this.singleDatasource && !this.datasourcesFormArray.length) {
      this.addDatasource(false);
    }
    if (changed) {
      setTimeout(() => {
        this.datasourcesUpdated(this.datasourcesFormGroup.get('datasources').value);
      }, 0);
    }
  }

  validate(c: UntypedFormControl) {
    this.timeseriesKeyError = false;
    this.datasourceError = [];
    if (!this.datasourcesFormGroup.valid) {
      return {
        datasources: {
          valid: false,
        }
      };
    }
    const datasources: Datasource[] = this.datasourcesFormGroup.get('datasources').value;
    if (!this.datasourcesOptional && (!datasources || !datasources.length)) {
      return {
        datasources: {
          valid: false
        }
      };
    }
    if (this.hasAdditionalLatestDataKeys && !this.basicMode) {
      let valid = datasources.filter(datasource => datasource?.dataKeys?.length).length > 0;
      if (!valid) {
        this.timeseriesKeyError = true;
        return {
          timeseriesDataKeys: {
            valid: false
          }
        };
      } else {
        const emptyDatasources = datasources.filter(datasource => !datasource?.dataKeys?.length &&
          !datasource?.latestDataKeys?.length);
        valid = emptyDatasources.length === 0;
        if (!valid) {
          for (const emptyDatasource of emptyDatasources) {
            const i = datasources.indexOf(emptyDatasource);
            this.datasourceError[i] = 'At least one data key should be specified';
          }
          return {
            dataKeys: {
              valid: false
            }
          };
        }
      }
    }
    return null;
  }

  datasourcesModeChange(datasourcesMode: DatasourceType) {
    this.datasourcesMode = datasourcesMode;
    if (this.basicMode) {
      for (const datasourceControl of this.datasourcesControls) {
        const datasource: Datasource = datasourceControl.value;
        if (datasource.type !== datasourcesMode) {
          datasource.type = datasourcesMode;
          datasourceControl.patchValue(datasource);
        }
      }
    }
  }

  private configModeChanged() {
    if (this.basicMode) {
      const datasourcesMode = this.detectDatasourcesMode(this.datasourcesFormGroup.get('datasources').value);
      this.datasourcesModeChange(datasourcesMode);
    }
  }

  private detectDatasourcesMode(datasources?: Datasource[]) {
    let datasourcesMode = DatasourceType.device;
    if (datasources && datasources.length) {
      datasourcesMode = datasources[0].type;
    }
    if (!this.hideDatasourcesMode
      && datasourcesMode !== DatasourceType.device && datasourcesMode !== DatasourceType.entity) {
      datasourcesMode = DatasourceType.device;
    }
    return datasourcesMode;
  }

  get datasourcesFormArray(): UntypedFormArray {
    return this.datasourcesFormGroup.get('datasources') as UntypedFormArray;
  }

  get datasourcesControls(): FormControl[] {
    return this.datasourcesFormArray.controls as FormControl[];
  }

  public trackByDatasource(index: number, datasourceControl: AbstractControl): any {
    return datasourceControl;
  }

  private datasourcesUpdated(datasources: Datasource[]) {
    if (this.datasourcesOptional) {
      datasources = datasources ? datasources.filter(d => datasourceValid(d)) : [];
    }
    this.propagateChange(datasources);
  }

  public onDatasourceDrop(event: CdkDragDrop<string[]>) {
    const datasourceForm = this.datasourcesFormArray.at(event.previousIndex);
    this.datasourcesFormArray.removeAt(event.previousIndex);
    this.datasourcesFormArray.insert(event.currentIndex, datasourceForm);
  }

  public removeDatasource(index: number) {
    this.datasourcesFormArray.removeAt(index);
  }

  public addDatasource(emitEvent = true) {
    let newDatasource: Datasource;
    if (this.widgetConfigComponent.functionsOnly) {
      newDatasource = deepClone(this.utils.getDefaultDatasource(this.dataKeySettingsForm));
      newDatasource.dataKeys = [this.dataKeysCallbacks.generateDataKey('Sin', DataKeyType.function, this.dataKeySettingsForm,
        false, this.dataKeySettingsFunction)];
    } else {
      const type = this.basicMode ? this.datasourcesMode : DatasourceType.entity;
      newDatasource = { type,
        dataKeys: []
      };
    }
    if (this.hasAdditionalLatestDataKeys) {
      newDatasource.latestDataKeys = [];
    }
    this.datasourcesFormArray.push(this.fb.control(newDatasource, []), {emitEvent});
  }

  private get dataKeySettingsForm(): FormProperty[] {
    return this.widgetConfigComponent.modelValue?.dataKeySettingsForm;
  }

  private get dataKeySettingsFunction(): DataKeySettingsFunction {
    return this.widgetConfigComponent.modelValue?.dataKeySettingsFunction;
  }

  private get dataKeysCallbacks(): DataKeysCallbacks {
    return this.widgetConfigComponent.widgetConfigCallbacks;
  }

  private get hasAdditionalLatestDataKeys(): boolean {
    return this.widgetConfigComponent.widgetType === widgetType.timeseries &&
      this.widgetConfigComponent.modelValue?.typeParameters?.hasAdditionalLatestDataKeys;
  }

  private get datasourcesOptional(): boolean {
    return this.widgetConfigComponent.modelValue?.typeParameters?.datasourcesOptional;
  }
}
