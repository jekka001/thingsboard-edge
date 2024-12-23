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

import { WidgetActionCallbacks } from '@home/components/widget/action/manage-widget-actions.component.models';
import { DatasourceCallbacks } from '@home/components/widget/config/datasource.component.models';
import { WidgetConfigComponentData } from '@home/models/widget-component.models';
import { Observable } from 'rxjs';
import { AfterViewInit, DestroyRef, Directive, EventEmitter, inject, Inject, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { AbstractControl, UntypedFormGroup } from '@angular/forms';
import { DataKey, DatasourceType, WidgetConfigMode, widgetType } from '@shared/models/widget.models';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import { isDefinedAndNotNull } from '@core/utils';
import { IAliasController } from '@core/api/widget-api.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

export type WidgetConfigCallbacks = DatasourceCallbacks & WidgetActionCallbacks;

export interface IBasicWidgetConfigComponent {
  isAdd: boolean;
  widgetConfig: WidgetConfigComponentData;
  widgetConfigChanged: Observable<WidgetConfigComponentData>;
  validateConfig(): boolean;

}

@Directive()
// eslint-disable-next-line @angular-eslint/directive-class-suffix
export abstract class BasicWidgetConfigComponent extends PageComponent implements
  IBasicWidgetConfigComponent, OnInit, AfterViewInit {

  isAdd = false;

  basicMode = WidgetConfigMode.basic;

  widgetConfigValue: WidgetConfigComponentData;

  set widgetConfig(value: WidgetConfigComponentData) {
    this.widgetConfigValue = value;
    this.setupConfig(this.widgetConfigValue);
  }

  get widgetConfig(): WidgetConfigComponentData {
    return this.widgetConfigValue;
  }

  get aliasController(): IAliasController {
    return this.widgetConfigComponent.aliasController;
  }

  get callbacks(): WidgetConfigCallbacks {
    return this.widgetConfigComponent.widgetConfigCallbacks;
  }

  get widgetType(): widgetType {
    return this.widgetConfigComponent.widgetType;
  }

  get widgetEditMode(): boolean {
    return this.widgetConfigComponent.widgetEditMode;
  }

  widgetConfigChangedEmitter = new EventEmitter<WidgetConfigComponentData>();
  widgetConfigChanged = this.widgetConfigChangedEmitter.asObservable();

  protected destroyRef = inject(DestroyRef);

  protected constructor(@Inject(Store) protected store: Store<AppState>,
                        protected widgetConfigComponent: WidgetConfigComponent) {
    super(store);
  }

  ngOnInit() {}

  ngAfterViewInit(): void {
    if (!this.validateConfig()) {
      setTimeout(() => {
          this.onConfigChanged(this.prepareOutputConfig(this.configForm().getRawValue()));
      }, 0);
    }
  }

  protected setupConfig(widgetConfig: WidgetConfigComponentData) {
    if (this.isAdd) {
      this.setupDefaults(widgetConfig);
    }
    this.onConfigSet(widgetConfig);
    this.updateValidators(false);
    for (const trigger of this.validatorTriggers()) {
      const path = trigger.split('.');
      let control: AbstractControl = this.configForm();
      for (const part of path) {
        control = control.get(part);
      }
      control.valueChanges.pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe(() => {
        this.updateValidators(true, trigger);
      });
    }
    this.configForm().valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.onConfigChanged(this.prepareOutputConfig(this.configForm().getRawValue()));
    });
  }

  protected setupDefaults(configData: WidgetConfigComponentData) {
    const params = configData.typeParameters;
    let dataKeys: DataKey[];
    let latestDataKeys: DataKey[];
    if (params.defaultDataKeysFunction) {
      dataKeys = params.defaultDataKeysFunction(this, configData);
    }
    if (params.defaultLatestDataKeysFunction) {
      latestDataKeys = params.defaultLatestDataKeysFunction(this, configData);
    }
    if (!dataKeys) {
      dataKeys = this.defaultDataKeys(configData);
    }
    if (!latestDataKeys) {
      latestDataKeys = this.defaultLatestDataKeys(configData);
    }
    if (dataKeys || latestDataKeys) {
      this.setupDefaultDatasource(configData, dataKeys, latestDataKeys);
    }
  }

  protected defaultDataKeys(configData: WidgetConfigComponentData): DataKey[] {
    return null;
  }

  protected defaultLatestDataKeys(configData: WidgetConfigComponentData): DataKey[] {
    return null;
  }

  protected updateValidators(emitEvent: boolean, trigger?: string) {
  }

  protected validatorTriggers(): string[] {
    return [];
  }

  protected onConfigChanged(widgetConfig: WidgetConfigComponentData) {
    this.widgetConfigValue = widgetConfig;
    this.widgetConfigChangedEmitter.emit(this.widgetConfigValue);
  }

  protected prepareOutputConfig(config: any): WidgetConfigComponentData {
    return config;
  }

  public validateConfig(): boolean {
    return this.configForm().valid;
  }

  protected setupDefaultDatasource(configData: WidgetConfigComponentData, keys?: DataKey[], latestKeys?: DataKey[]) {
    let datasources = configData.config.datasources;
    if (!datasources || !datasources.length) {
      datasources = [
        {
          type: DatasourceType.device,
          dataKeys: []
        }
      ];
      configData.config.datasources = datasources;
    }
    let dataKeys = datasources[0].dataKeys;
    if (!dataKeys) {
      dataKeys = [];
      datasources[0].dataKeys = dataKeys;
    }
    let latestDataKeys = datasources[0].latestDataKeys;
    if (!latestDataKeys) {
      latestDataKeys = [];
      datasources[0].latestDataKeys = latestDataKeys;
    }
    if (keys && keys.length) {
      dataKeys.length = 0;
      keys.forEach(key => {
        const dataKey = this.constructDataKey(configData, key, false);
        dataKeys.push(dataKey);
      });
    }
    if (latestKeys && latestKeys.length) {
      latestDataKeys.length = 0;
      latestKeys.forEach(key => {
        const dataKey = this.constructDataKey(configData, key, true);
        latestDataKeys.push(dataKey);
      });
    }
  }

  protected constructDataKey(configData: WidgetConfigComponentData, key: DataKey, isLatestKey: boolean): DataKey {
    const dataKey =
      this.widgetConfigComponent.widgetConfigCallbacks.generateDataKey(key.name, key.type,
        configData.dataKeySettingsSchema, isLatestKey, configData.dataKeySettingsFunction);
    if (key.label) {
      dataKey.label = key.label;
    }
    if (key.units) {
      dataKey.units = key.units;
    }
    if (isDefinedAndNotNull(key.decimals)) {
      dataKey.decimals = key.decimals;
    }
    if (key.color) {
      dataKey.color = key.color;
    }
    if (isDefinedAndNotNull(key.settings)) {
      dataKey.settings = key.settings;
    }
    if (isDefinedAndNotNull(key.aggregationType)) {
      dataKey.aggregationType = key.aggregationType;
    }
    if (isDefinedAndNotNull(key.comparisonEnabled)) {
      dataKey.comparisonEnabled = key.comparisonEnabled;
    }
    if (isDefinedAndNotNull(key.timeForComparison)) {
      dataKey.timeForComparison = key.timeForComparison;
    }
    if (isDefinedAndNotNull(key.comparisonCustomIntervalValue)) {
      dataKey.comparisonCustomIntervalValue = key.comparisonCustomIntervalValue;
    }
    if (isDefinedAndNotNull(key.comparisonResultType)) {
      dataKey.comparisonResultType = key.comparisonResultType;
    }
    return dataKey;
  }

  protected abstract configForm(): UntypedFormGroup;

  protected abstract onConfigSet(configData: WidgetConfigComponentData);

}
