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
import { isUndefined } from '@core/utils';
import { ValueType } from '@shared/models/constants';
import {
  powerButtonDefaultSettings,
  powerButtonLayoutImages,
  powerButtonLayouts,
  powerButtonLayoutTranslations,
  PowerButtonWidgetSettings
} from '@home/components/widget/lib/rpc/power-button-widget.models';
import { cssSizeToStrSize, resolveCssSize } from '@shared/models/widget-settings.models';

@Component({
  selector: 'tb-power-button-basic-config',
  templateUrl: './power-button-basic-config.component.html',
  styleUrls: ['../basic-config.scss']
})
export class PowerButtonBasicConfigComponent extends BasicWidgetConfigComponent {

  get targetDevice(): TargetDevice {
    return this.powerButtonWidgetConfigForm.get('targetDevice').value;
  }

  powerButtonLayouts = powerButtonLayouts;

  powerButtonLayoutTranslationMap = powerButtonLayoutTranslations;
  powerButtonLayoutImageMap = powerButtonLayoutImages;

  valueType = ValueType;

  powerButtonWidgetConfigForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              protected widgetConfigComponent: WidgetConfigComponent,
              private fb: UntypedFormBuilder) {
    super(store, widgetConfigComponent);
  }

  protected configForm(): UntypedFormGroup {
    return this.powerButtonWidgetConfigForm;
  }

  protected onConfigSet(configData: WidgetConfigComponentData) {
    const settings: PowerButtonWidgetSettings = {...powerButtonDefaultSettings, ...(configData.config.settings || {})};
    const iconSize = resolveCssSize(configData.config.iconSize);
    this.powerButtonWidgetConfigForm = this.fb.group({
      targetDevice: [configData.config.targetDevice, []],

      initialState: [settings.initialState, []],
      onUpdateState: [settings.onUpdateState, []],
      offUpdateState: [settings.offUpdateState, []],
      disabledState: [settings.disabledState, []],

      layout: [settings.layout, []],

      showTitle: [configData.config.showTitle, []],
      title: [configData.config.title, []],
      titleFont: [configData.config.titleFont, []],
      titleColor: [configData.config.titleColor, []],

      showIcon: [configData.config.showTitleIcon, []],
      iconSize: [iconSize[0], [Validators.min(0)]],
      iconSizeUnit: [iconSize[1], []],
      icon: [configData.config.titleIcon, []],
      iconColor: [configData.config.iconColor, []],

      onButtonIcon: this.fb.group({
        showIcon: [settings.onButtonIcon.showIcon, []],
        iconSize: [settings.onButtonIcon.iconSize, [Validators.min(0)]],
        iconSizeUnit: [settings.onButtonIcon.iconSizeUnit, []],
        icon: [settings.onButtonIcon.icon, []],
      }),
      offButtonIcon: this.fb.group({
        showIcon: [settings.offButtonIcon.showIcon, []],
        iconSize: [settings.offButtonIcon.iconSize, [Validators.min(0)]],
        iconSizeUnit: [settings.offButtonIcon.iconSizeUnit, []],
        icon: [settings.offButtonIcon.icon, []],
      }),

      mainColorOn: [settings.mainColorOn, []],
      backgroundColorOn: [settings.backgroundColorOn, []],

      mainColorOff: [settings.mainColorOff, []],
      backgroundColorOff: [settings.backgroundColorOff, []],

      mainColorDisabled: [settings.mainColorDisabled, []],
      backgroundColorDisabled: [settings.backgroundColorDisabled, []],

      background: [settings.background, []],

      cardButtons: [this.getCardButtons(configData.config), []],
      borderRadius: [configData.config.borderRadius, []],
      padding: [settings.padding, []],

      actions: [configData.config.actions || {}, []]
    });
  }

  protected prepareOutputConfig(config: any): WidgetConfigComponentData {
    this.widgetConfig.config.targetDevice = config.targetDevice;

    this.widgetConfig.config.showTitle = config.showTitle;
    this.widgetConfig.config.title = config.title;
    this.widgetConfig.config.titleFont = config.titleFont;
    this.widgetConfig.config.titleColor = config.titleColor;

    this.widgetConfig.config.showTitleIcon = config.showIcon;
    this.widgetConfig.config.iconSize = cssSizeToStrSize(config.iconSize, config.iconSizeUnit);
    this.widgetConfig.config.titleIcon = config.icon;
    this.widgetConfig.config.iconColor = config.iconColor;

    this.widgetConfig.config.settings = this.widgetConfig.config.settings || {};

    this.widgetConfig.config.settings.initialState = config.initialState;
    this.widgetConfig.config.settings.onUpdateState = config.onUpdateState;
    this.widgetConfig.config.settings.offUpdateState = config.offUpdateState;
    this.widgetConfig.config.settings.disabledState = config.disabledState;

    this.widgetConfig.config.settings.layout = config.layout;

    this.widgetConfig.config.settings.onButtonIcon = config.onButtonIcon;
    this.widgetConfig.config.settings.offButtonIcon = config.offButtonIcon;

    this.widgetConfig.config.settings.mainColorOn = config.mainColorOn;
    this.widgetConfig.config.settings.backgroundColorOn = config.backgroundColorOn;

    this.widgetConfig.config.settings.mainColorOff = config.mainColorOff;
    this.widgetConfig.config.settings.backgroundColorOff = config.backgroundColorOff;

    this.widgetConfig.config.settings.mainColorDisabled = config.mainColorDisabled;
    this.widgetConfig.config.settings.backgroundColorDisabled = config.backgroundColorDisabled;

    this.widgetConfig.config.settings.background = config.background;

    this.setCardButtons(config.cardButtons, this.widgetConfig.config);
    this.widgetConfig.config.borderRadius = config.borderRadius;
    this.widgetConfig.config.settings.padding = config.padding;

    this.widgetConfig.config.actions = config.actions;
    return this.widgetConfig;
  }

  protected validatorTriggers(): string[] {
    return ['showTitle', 'showIcon', 'onButtonIcon.showIcon', 'offButtonIcon.showIcon'];
  }

  protected updateValidators(emitEvent: boolean, trigger?: string) {
    const showTitle: boolean = this.powerButtonWidgetConfigForm.get('showTitle').value;
    const showIcon: boolean = this.powerButtonWidgetConfigForm.get('showIcon').value;
    const onButtonIcon: boolean = this.powerButtonWidgetConfigForm.get('onButtonIcon').get('showIcon').value;
    const offButtonIcon: boolean = this.powerButtonWidgetConfigForm.get('offButtonIcon').get('showIcon').value;
    if (showTitle) {
      this.powerButtonWidgetConfigForm.get('title').enable();
      this.powerButtonWidgetConfigForm.get('titleFont').enable();
      this.powerButtonWidgetConfigForm.get('titleColor').enable();
      this.powerButtonWidgetConfigForm.get('showIcon').enable({emitEvent: false});
      if (showIcon) {
        this.powerButtonWidgetConfigForm.get('iconSize').enable();
        this.powerButtonWidgetConfigForm.get('iconSizeUnit').enable();
        this.powerButtonWidgetConfigForm.get('icon').enable();
        this.powerButtonWidgetConfigForm.get('iconColor').enable();
      } else {
        this.powerButtonWidgetConfigForm.get('iconSize').disable();
        this.powerButtonWidgetConfigForm.get('iconSizeUnit').disable();
        this.powerButtonWidgetConfigForm.get('icon').disable();
        this.powerButtonWidgetConfigForm.get('iconColor').disable();
      }
    } else {
      this.powerButtonWidgetConfigForm.get('title').disable();
      this.powerButtonWidgetConfigForm.get('titleFont').disable();
      this.powerButtonWidgetConfigForm.get('titleColor').disable();
      this.powerButtonWidgetConfigForm.get('showIcon').disable({emitEvent: false});
      this.powerButtonWidgetConfigForm.get('iconSize').disable();
      this.powerButtonWidgetConfigForm.get('iconSizeUnit').disable();
      this.powerButtonWidgetConfigForm.get('icon').disable();
      this.powerButtonWidgetConfigForm.get('iconColor').disable();
    }
    if (onButtonIcon) {
      this.powerButtonWidgetConfigForm.get('onButtonIcon').get('iconSize').enable();
      this.powerButtonWidgetConfigForm.get('onButtonIcon').get('iconSizeUnit').enable();
      this.powerButtonWidgetConfigForm.get('onButtonIcon').get('icon').enable();
    } else {
      this.powerButtonWidgetConfigForm.get('onButtonIcon').get('iconSize').disable();
      this.powerButtonWidgetConfigForm.get('onButtonIcon').get('iconSizeUnit').disable();
      this.powerButtonWidgetConfigForm.get('onButtonIcon').get('icon').disable();
    }
    if (offButtonIcon) {
      this.powerButtonWidgetConfigForm.get('offButtonIcon').get('iconSize').enable();
      this.powerButtonWidgetConfigForm.get('offButtonIcon').get('iconSizeUnit').enable();
      this.powerButtonWidgetConfigForm.get('offButtonIcon').get('icon').enable();
    } else {
      this.powerButtonWidgetConfigForm.get('offButtonIcon').get('iconSize').disable();
      this.powerButtonWidgetConfigForm.get('offButtonIcon').get('iconSizeUnit').disable();
      this.powerButtonWidgetConfigForm.get('offButtonIcon').get('icon').disable();
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

}
