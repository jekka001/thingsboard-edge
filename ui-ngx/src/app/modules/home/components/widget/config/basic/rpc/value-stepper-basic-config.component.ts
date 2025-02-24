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

import { Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { BasicWidgetConfigComponent } from '@home/components/widget/config/widget-config.component.models';
import { WidgetConfigComponentData } from '@home/models/widget-component.models';
import { TargetDevice, WidgetConfig, } from '@shared/models/widget.models';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import { formatValue, isUndefined } from '@core/utils';
import { ValueType } from '@shared/models/constants';
import {
  valueStepperDefaultSettings,
  valueStepperTypeImages,
  valueStepperTypes,
  valueStepperTypeTranslations,
  ValueStepperWidgetSettings
} from '@home/components/widget/lib/rpc/value-stepper-widget.models';

type ButtonAppearanceType = 'left' | 'right';

@Component({
  selector: 'tb-value-stepper-basic-config',
  templateUrl: './value-stepper-basic-config.component.html',
  styleUrls: ['../basic-config.scss']
})
export class ValueStepperBasicConfigComponent extends BasicWidgetConfigComponent {

  get targetDevice(): TargetDevice {
    return this.valueStepperWidgetConfigForm.get('targetDevice').value;
  }

  valueStepperTypeTranslationMap = valueStepperTypeTranslations;
  valueStepperTypes = valueStepperTypes;
  valueStepperTypeImageMap = valueStepperTypeImages;

  buttonAppearanceType: ButtonAppearanceType = 'left';

  valueType = ValueType;

  valueStepperWidgetConfigForm: UntypedFormGroup;

  valuePreviewFn = this._valuePreviewFn.bind(this);

  constructor(protected store: Store<AppState>,
              protected widgetConfigComponent: WidgetConfigComponent,
              private fb: UntypedFormBuilder) {
    super(store, widgetConfigComponent);
  }

  protected configForm(): UntypedFormGroup {
    return this.valueStepperWidgetConfigForm;
  }

  protected onConfigSet(configData: WidgetConfigComponentData) {
    const settings: ValueStepperWidgetSettings = {...valueStepperDefaultSettings, ...(configData.config.settings || {})};
    this.valueStepperWidgetConfigForm = this.fb.group({
      targetDevice: [configData.config.targetDevice, []],

      initialState: [settings.initialState, []],
      leftButtonClick: [settings.leftButtonClick, []],
      rightButtonClick: [settings.rightButtonClick, []],
      disabledState: [settings.disabledState, []],

      appearance: this.fb.group({
        type: [settings.appearance.type, []],
        autoScale: [settings.appearance.autoScale, []],
        minValueRange: [settings.appearance.minValueRange, []],
        maxValueRange: [settings.appearance.maxValueRange, []],
        valueStep: [settings.appearance.valueStep, [Validators.min(0)]],
        showValueBox: [settings.appearance.showValueBox, []],
        valueUnits: [settings.appearance.valueUnits, []],
        valueDecimals: [settings.appearance.valueDecimals, []],
        valueFont: [settings.appearance.valueFont, []],
        valueColor: [settings.appearance.valueColor],
        valueBoxBackground: [settings.appearance.valueBoxBackground, []],
        showBorder: [settings.appearance.showBorder, []],
        borderWidth: [settings.appearance.borderWidth, []],
        borderColor: [settings.appearance.borderColor, []]
      }),

      buttonAppearance: this.fb.group({
        leftButton: this.fb.group({
          showButton: [settings.buttonAppearance.leftButton.showButton],
          icon: [settings.buttonAppearance.leftButton.icon],
          iconSize: [settings.buttonAppearance.leftButton.iconSize],
          iconSizeUnit: [settings.buttonAppearance.leftButton.iconSizeUnit],
          mainColorOn: [settings.buttonAppearance.leftButton.mainColorOn, []],
          backgroundColorOn: [settings.buttonAppearance.leftButton.backgroundColorOn, []],
          mainColorDisabled: [settings.buttonAppearance.leftButton.mainColorDisabled, []],
          backgroundColorDisabled: [settings.buttonAppearance.leftButton.backgroundColorDisabled, []]
        }),
        rightButton: this.fb.group({
          showButton: [settings.buttonAppearance.rightButton.showButton],
          icon: [settings.buttonAppearance.rightButton.icon],
          iconSize: [settings.buttonAppearance.rightButton.iconSize],
          iconSizeUnit: [settings.buttonAppearance.rightButton.iconSizeUnit],
          mainColorOn: [settings.buttonAppearance.rightButton.mainColorOn, []],
          backgroundColorOn: [settings.buttonAppearance.rightButton.backgroundColorOn, []],
          mainColorDisabled: [settings.buttonAppearance.rightButton.mainColorDisabled, []],
          backgroundColorDisabled: [settings.buttonAppearance.rightButton.backgroundColorDisabled, []]
        })
      }),

      background: [settings.background, []],
      cardButtons: [this.getCardButtons(configData.config), []],
      borderRadius: [configData.config.borderRadius, []],
      padding: [settings.padding, []],

      actions: [configData.config.actions || {}, []]
    });
  }


  protected prepareOutputConfig(config: any): WidgetConfigComponentData {
    this.widgetConfig.config.targetDevice = config.targetDevice;
    this.widgetConfig.config.settings = this.widgetConfig.config.settings || {};
    this.widgetConfig.config.settings.initialState = config.initialState;
    this.widgetConfig.config.settings.disabledState = config.disabledState;
    this.widgetConfig.config.settings.leftButtonClick = config.leftButtonClick;
    this.widgetConfig.config.settings.rightButtonClick = config.rightButtonClick;
    this.widgetConfig.config.settings.appearance = config.appearance;
    this.widgetConfig.config.settings.buttonAppearance = config.buttonAppearance;

    this.widgetConfig.config.settings.background = config.background;
    this.setCardButtons(config.cardButtons, this.widgetConfig.config);
    this.widgetConfig.config.borderRadius = config.borderRadius;
    this.widgetConfig.config.settings.padding = config.padding;
    this.widgetConfig.config.actions = config.actions;

    return this.widgetConfig;
  }


  protected validatorTriggers(): string[] {
    return ['appearance.showValueBox', 'appearance.showBorder',
      'buttonAppearance.leftButton.showButton', 'buttonAppearance.rightButton.showButton'];
  }

  protected updateValidators(emitEvent: boolean, trigger?: string) {
    const showValueBox: boolean = this.valueStepperWidgetConfigForm.get('appearance').get('showValueBox').value;
    const showBorder: boolean = this.valueStepperWidgetConfigForm.get('appearance').get('showBorder').value;
    const showLeftButton: boolean = this.valueStepperWidgetConfigForm.get('buttonAppearance').get('leftButton').get('showButton').value;
    const showRightButton: boolean = this.valueStepperWidgetConfigForm.get('buttonAppearance').get('rightButton').get('showButton').value;
    if (showValueBox) {
      this.valueStepperWidgetConfigForm.get('appearance').get('valueUnits').enable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueDecimals').enable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueFont').enable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueColor').enable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueBoxBackground').enable();
      this.valueStepperWidgetConfigForm.get('appearance').get('showBorder').enable({emitEvent: false});
      if (showBorder) {
        this.valueStepperWidgetConfigForm.get('appearance').get('borderWidth').enable();
        this.valueStepperWidgetConfigForm.get('appearance').get('borderColor').enable();
      } else {
        this.valueStepperWidgetConfigForm.get('appearance').get('borderWidth').disable();
        this.valueStepperWidgetConfigForm.get('appearance').get('borderColor').disable();
      }
    } else {
      this.valueStepperWidgetConfigForm.get('appearance').get('valueUnits').disable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueDecimals').disable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueFont').disable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueColor').disable();
      this.valueStepperWidgetConfigForm.get('appearance').get('valueBoxBackground').disable();
      this.valueStepperWidgetConfigForm.get('appearance').get('showBorder').disable({emitEvent: false});
      this.valueStepperWidgetConfigForm.get('appearance').get('borderWidth').disable();
      this.valueStepperWidgetConfigForm.get('appearance').get('borderColor').disable();
    }
    this.buttonValidators(showLeftButton, 'leftButton');
    this.buttonValidators(showRightButton, 'rightButton');
  }

  private buttonValidators(showButtonValue: boolean, button: string) {
    if (showButtonValue) {
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('icon').enable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('iconSize').enable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('iconSizeUnit').enable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('mainColorOn').enable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('backgroundColorOn').enable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('mainColorDisabled').enable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('backgroundColorDisabled').enable()
    } else {
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('icon').disable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('iconSize').disable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('iconSizeUnit').disable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('mainColorOn').disable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('backgroundColorOn').disable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('mainColorDisabled').disable()
      this.valueStepperWidgetConfigForm.get('buttonAppearance').get(button).get('backgroundColorDisabled').disable()
    }
  }

  private getCardButtons(config: WidgetConfig): string[] {
    const buttons: string[] = [];
    if (isUndefined(config.enableFullscreen) || config.enableFullscreen) {
      buttons.push('fullscreen');
    }
    return buttons;
  }

  private setCardButtons(buttons: string[], config: WidgetConfig) {
    config.enableFullscreen = buttons.includes('fullscreen');
  }

  private _valuePreviewFn(): string {
    const units: string = this.valueStepperWidgetConfigForm.get('appearance').get('valueUnits').value;
    const decimals: number = this.valueStepperWidgetConfigForm.get('appearance').get('valueDecimals').value;
    return formatValue(48, decimals, units, false);
  }
}
