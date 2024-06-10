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

import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import {
  ColorSettings,
  ColorType,
  colorTypeTranslations,
  defaultGradient,
  defaultRange
} from '@shared/models/widget-settings.models';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { deepClone } from '@core/utils';
import { WidgetService } from '@core/http/widget.service';
import { ColorSettingsComponent } from '@home/components/widget/lib/settings/common/color-settings.component';
import { IAliasController } from '@core/api/widget-api.models';
import { coerceBoolean, coerceNumber } from '@shared/decorators/coercion';
import { DataKeysCallbacks } from '@home/components/widget/config/data-keys.component.models';
import { Datasource } from '@shared/models/widget.models';

@Component({
  selector: 'tb-color-settings-panel',
  templateUrl: './color-settings-panel.component.html',
  providers: [],
  styleUrls: ['./color-settings-panel.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class ColorSettingsPanelComponent extends PageComponent implements OnInit {

  @Input()
  colorSettings: ColorSettings;

  @Input()
  popover: TbPopoverComponent<ColorSettingsPanelComponent>;

  @Input()
  settingsComponents: ColorSettingsComponent[];

  @Output()
  colorSettingsApplied = new EventEmitter<ColorSettings>();

  @Input()
  aliasController: IAliasController;

  @Input()
  dataKeyCallbacks: DataKeysCallbacks;

  @Input()
  datasource: Datasource;

  @Input()
  @coerceBoolean()
  rangeAdvancedMode = false;

  @Input()
  @coerceBoolean()
  gradientAdvancedMode = false;

  @Input()
  @coerceNumber()
  minValue: number;

  @Input()
  @coerceNumber()
  maxValue: number;

  colorType = ColorType;

  colorTypes = Object.keys(ColorType) as ColorType[];

  colorTypeTranslationsMap = colorTypeTranslations;

  colorSettingsFormGroup: UntypedFormGroup;

  functionScopeVariables = this.widgetService.getWidgetScopeVariables();

  constructor(private fb: UntypedFormBuilder,
              private widgetService: WidgetService,
              protected store: Store<AppState>) {
    super(store);
  }

  ngOnInit(): void {
    this.colorSettingsFormGroup = this.fb.group(
      {
        type: [this.colorSettings?.type || ColorType.constant, []],
        color: [this.colorSettings?.color, []],
        gradient: [this.colorSettings?.gradient || defaultGradient(this.minValue, this.maxValue), []],
        rangeList: [this.colorSettings?.rangeList || defaultRange(), []],
        colorFunction: [this.colorSettings?.colorFunction, []]
      }
    );
    this.colorSettingsFormGroup.get('type').valueChanges.subscribe(() => {
      Promise.resolve().then(() => this.popover?.updatePosition());
    });
  }

  copyColorSettings(comp: ColorSettingsComponent) {
    this.colorSettings = deepClone(comp.modelValue);
    this.colorSettingsFormGroup.patchValue({
      type: this.colorSettings.type,
      color: this.colorSettings.color,
      gradient: this.colorSettings.gradient || null,
      colorFunction: this.colorSettings.colorFunction,
      rangeList: this.colorSettings.rangeList || null
    }, {emitEvent: false});
    this.colorSettingsFormGroup.markAsDirty();
  }

  cancel() {
    this.popover?.hide();
  }

  applyColorSettings() {
    const colorSettings = this.colorSettingsFormGroup.value;
    this.colorSettingsApplied.emit(colorSettings);
  }

}
