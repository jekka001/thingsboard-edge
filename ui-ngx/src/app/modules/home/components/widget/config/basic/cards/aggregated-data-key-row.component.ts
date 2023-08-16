///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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
  EventEmitter,
  forwardRef,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  ViewEncapsulation
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import {
  ComparisonResultType,
  DataKey,
  DataKeyConfigMode,
  DatasourceType, Widget,
  widgetType
} from '@shared/models/widget.models';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { AggregationType } from '@shared/models/time/time.models';
import { DataKeysCallbacks } from '@home/components/widget/config/data-keys.component.models';
import { TranslateService } from '@ngx-translate/core';
import { TruncatePipe } from '@shared/pipe/truncate.pipe';
import {
  DataKeyConfigDialogComponent,
  DataKeyConfigDialogData
} from '@home/components/widget/config/data-key-config-dialog.component';
import { deepClone, formatValue } from '@core/utils';
import {
  AggregatedValueCardKeyPosition,
  aggregatedValueCardKeyPositionTranslations,
  AggregatedValueCardKeySettings
} from '@home/components/widget/lib/cards/aggregated-value-card.models';

@Component({
  selector: 'tb-aggregated-data-key-row',
  templateUrl: './aggregated-data-key-row.component.html',
  styleUrls: ['./aggregated-data-key-row.component.scss', '../../data-keys.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AggregatedDataKeyRowComponent),
      multi: true
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class AggregatedDataKeyRowComponent implements ControlValueAccessor, OnInit, OnChanges {

  aggregatedValueCardKeyPositions: AggregatedValueCardKeyPosition[] =
    Object.keys(AggregatedValueCardKeyPosition).map(value => AggregatedValueCardKeyPosition[value]);

  aggregatedValueCardKeyPositionTranslationMap = aggregatedValueCardKeyPositionTranslations;

  dataKeyTypes = DataKeyType;

  aggregationTypes = AggregationType;

  comparisonResultTypes = ComparisonResultType;

  @Input()
  disabled: boolean;

  @Input()
  datasourceType: DatasourceType;

  @Input()
  keyName: string;

  @Output()
  keyRemoved = new EventEmitter();

  keyRowFormGroup: UntypedFormGroup;

  modelValue: DataKey;

  valuePreviewFn = this._valuePreviewFn.bind(this);

  get callbacks(): DataKeysCallbacks {
    return this.widgetConfigComponent.widgetConfigCallbacks;
  }

  get widget(): Widget {
    return this.widgetConfigComponent.widget;
  }

  get isEntityDatasource(): boolean {
    return [DatasourceType.device, DatasourceType.entity].includes(this.datasourceType);
  }

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private dialog: MatDialog,
              private cd: ChangeDetectorRef,
              public translate: TranslateService,
              public truncate: TruncatePipe,
              private widgetConfigComponent: WidgetConfigComponent) {
  }

  ngOnInit() {
    this.keyRowFormGroup = this.fb.group({
      position: [null, []],
      units: [null, []],
      decimals: [null, []],
      font: [null, []],
      color: [null, []]
    });
    this.keyRowFormGroup.valueChanges.subscribe(
      () => this.updateModel()
    );
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (['keyName'].includes(propName)) {
          if (change.currentValue) {
            this.modelValue.name = change.currentValue;
            setTimeout(() => {
              this.updateModel();
            }, 0);
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
      this.keyRowFormGroup.disable({emitEvent: false});
    } else {
      this.keyRowFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: DataKey): void {
    this.modelValue = value || {} as DataKey;
    const settings: AggregatedValueCardKeySettings = (this.modelValue.settings || {});
    this.keyRowFormGroup.patchValue(
      {
        position: settings.position || AggregatedValueCardKeyPosition.center,
        units: value?.units,
        decimals: value?.decimals,
        font: settings.font,
        color: settings.color
      }, {emitEvent: false}
    );
    this.cd.markForCheck();
  }

  dataKeyHasPostprocessing(): boolean {
    return !!this.modelValue?.postFuncBody;
  }

  editKey() {
    this.dialog.open<DataKeyConfigDialogComponent, DataKeyConfigDialogData, DataKey>(DataKeyConfigDialogComponent,
      {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          dataKey: deepClone(this.modelValue),
          dataKeyConfigMode: DataKeyConfigMode.general,
          dataKeySettingsSchema: null,
          dataKeySettingsDirective: null,
          dashboard: null,
          aliasController: null,
          widget: this.widget,
          widgetType: widgetType.latest,
          deviceId: null,
          entityAliasId: null,
          showPostProcessing: true,
          callbacks: this.callbacks,
          hideDataKeyName: true,
          hideDataKeyLabel: true,
          hideDataKeyColor: true
        }
      }).afterClosed().subscribe((updatedDataKey) => {
      if (updatedDataKey) {
        this.modelValue = updatedDataKey;
        this.keyRowFormGroup.get('units').patchValue(this.modelValue.units, {emitEvent: false});
        this.keyRowFormGroup.get('decimals').patchValue(this.modelValue.decimals, {emitEvent: false});
        this.updateModel();
      }
    });
  }

  private updateModel() {
    const value = this.keyRowFormGroup.value;
    this.modelValue.settings = this.modelValue.settings || {};
    this.modelValue.settings.position = value.position;
    this.modelValue.settings.font = value.font;
    this.modelValue.settings.color = value.color;
    this.modelValue.units = value.units;
    this.modelValue.decimals = value.decimals;
    this.propagateChange(this.modelValue);
  }

  private _valuePreviewFn(): string {
    const units: string = this.keyRowFormGroup.get('units').value;
    const decimals: number = this.keyRowFormGroup.get('decimals').value;
    return formatValue(22, decimals, units, true);
  }
}
