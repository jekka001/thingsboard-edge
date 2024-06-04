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

import { Component, Injector } from '@angular/core';
import { Datasource, WidgetSettings, WidgetSettingsComponent } from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { formatValue } from '@core/utils';
import {
  batteryLevelDefaultSettings, BatteryLevelLayout,
  batteryLevelLayoutImages,
  batteryLevelLayouts,
  batteryLevelLayoutTranslations
} from '@home/components/widget/lib/indicator/battery-level-widget.models';

@Component({
  selector: 'tb-battery-level-widget-settings',
  templateUrl: './battery-level-widget-settings.component.html',
  styleUrls: []
})
export class BatteryLevelWidgetSettingsComponent extends WidgetSettingsComponent {

  batteryLevelLayouts = batteryLevelLayouts;

  batteryLevelLayoutTranslationMap = batteryLevelLayoutTranslations;
  batteryLevelLayoutImageMap = batteryLevelLayoutImages;

  batteryLevelWidgetSettingsForm: UntypedFormGroup;

  valuePreviewFn = this._valuePreviewFn.bind(this);

  get sectionsCountEnabled(): boolean {
    const layout: BatteryLevelLayout = this.batteryLevelWidgetSettingsForm.get('layout').value;
    return [BatteryLevelLayout.vertical_divided, BatteryLevelLayout.horizontal_divided].includes(layout);
  }

  public get datasource(): Datasource {
    const datasources: Datasource[] = this.widgetConfig.config.datasources;
    if (datasources && datasources.length) {
      return datasources[0];
    } else {
      return null;
    }
  }

  constructor(protected store: Store<AppState>,
              private $injector: Injector,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  protected settingsForm(): UntypedFormGroup {
    return this.batteryLevelWidgetSettingsForm;
  }

  protected defaultSettings(): WidgetSettings {
    return {...batteryLevelDefaultSettings};
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.batteryLevelWidgetSettingsForm = this.fb.group({
      layout: [settings.layout, []],
      sectionsCount: [settings.sectionsCount, [Validators.min(2), Validators.max(20)]],

      showValue: [settings.showValue, []],
      autoScaleValueSize: [settings.autoScaleValueSize, []],
      valueFont: [settings.valueFont, []],
      valueColor: [settings.valueColor, []],

      batteryLevelColor: [settings.batteryLevelColor, []],
      batteryShapeColor: [settings.batteryShapeColor, []],

      background: [settings.background, []],
      padding: [settings.padding, []]
    });
  }

  protected validatorTriggers(): string[] {
    return ['showValue', 'layout'];
  }

  protected updateValidators(emitEvent: boolean) {
    const showValue: boolean = this.batteryLevelWidgetSettingsForm.get('showValue').value;
    const layout: BatteryLevelLayout = this.batteryLevelWidgetSettingsForm.get('layout').value;
    const divided = [BatteryLevelLayout.vertical_divided, BatteryLevelLayout.horizontal_divided].includes(layout);

    if (showValue) {
      this.batteryLevelWidgetSettingsForm.get('autoScaleValueSize').enable();
      this.batteryLevelWidgetSettingsForm.get('valueFont').enable();
      this.batteryLevelWidgetSettingsForm.get('valueColor').enable();
    } else {
      this.batteryLevelWidgetSettingsForm.get('autoScaleValueSize').disable();
      this.batteryLevelWidgetSettingsForm.get('valueFont').disable();
      this.batteryLevelWidgetSettingsForm.get('valueColor').disable();
    }

    if (divided) {
      this.batteryLevelWidgetSettingsForm.get('sectionsCount').enable();
    } else {
      this.batteryLevelWidgetSettingsForm.get('sectionsCount').disable();
    }

    this.batteryLevelWidgetSettingsForm.get('autoScaleValueSize').updateValueAndValidity({emitEvent});
    this.batteryLevelWidgetSettingsForm.get('valueFont').updateValueAndValidity({emitEvent});
    this.batteryLevelWidgetSettingsForm.get('valueColor').updateValueAndValidity({emitEvent});
    this.batteryLevelWidgetSettingsForm.get('sectionsCount').updateValueAndValidity({emitEvent});
  }

  private _valuePreviewFn(): string {
    const units: string = this.widgetConfig.config.units;
    const decimals: number = this.widgetConfig.config.decimals;
    return formatValue(22, decimals, units, true);
  }

}
