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

import { WidgetSettings, WidgetSettingsComponent } from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Component } from '@angular/core';
import { COMMA, ENTER, SEMICOLON } from '@angular/cdk/keycodes';

@Component({
  selector: 'tb-analogue-compass-widget-settings',
  templateUrl: './analogue-compass-widget-settings.component.html',
  styleUrls: ['./../widget-settings.scss']
})
export class AnalogueCompassWidgetSettingsComponent extends WidgetSettingsComponent {

  readonly separatorKeysCodes: number[] = [ENTER, COMMA, SEMICOLON];

  analogueCompassWidgetSettingsForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              protected fb: UntypedFormBuilder) {
    super(store);
  }

  protected settingsForm(): UntypedFormGroup {
    return this.analogueCompassWidgetSettingsForm;
  }

  protected defaultSettings(): WidgetSettings {
    return {
      majorTicks: [],
      minorTicks: 22,
      showStrokeTicks: false,
      needleCircleSize: 10,
      showBorder: true,
      borderOuterWidth: 10,
      colorPlate: '#222',
      colorMajorTicks: '#f5f5f5',
      colorMinorTicks: '#ddd',
      colorNeedle: '#f08080',
      colorNeedleCircle: '#e8e8e8',
      colorBorder: '#ccc',
      majorTickFont: {
        family: 'Roboto',
        size: 20,
        style: 'normal',
        weight: '500',
        color: '#ccc'
      },
      animation: true,
      animationDuration: 500,
      animationRule: 'cycle',
      animationTarget: 'needle'
    };
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.analogueCompassWidgetSettingsForm = this.fb.group({

      // Ticks settings
      majorTicks: [settings.majorTicks, []],
      colorMajorTicks: [settings.colorMajorTicks, []],
      minorTicks: [settings.minorTicks, [Validators.min(0)]],
      colorMinorTicks: [settings.colorMinorTicks, []],
      showStrokeTicks: [settings.showStrokeTicks, []],
      majorTickFont: [settings.majorTickFont, []],
      majorTickColor: [settings.majorTickFont.color, []],

      // Plate settings
      colorPlate: [settings.colorPlate, []],
      showBorder: [settings.showBorder, []],
      colorBorder: [settings.colorBorder, []],
      borderOuterWidth: [settings.borderOuterWidth, [Validators.min(0)]],

      // Needle settings
      needleCircleSize: [settings.needleCircleSize, [Validators.min(0)]],
      colorNeedle: [settings.colorNeedle, []],
      colorNeedleCircle: [settings.colorNeedleCircle, []],

      // Animation settings
      animation: [settings.animation, []],
      animationDuration: [settings.animationDuration, [Validators.min(0)]],
      animationRule: [settings.animationRule, []],
      animationTarget: [settings.animationTarget, []]
    });
  }

  protected validatorTriggers(): string[] {
    return ['showBorder', 'animation'];
  }

  protected updateValidators(emitEvent: boolean) {
    const showBorder: boolean = this.analogueCompassWidgetSettingsForm.get('showBorder').value;
    const animation: boolean = this.analogueCompassWidgetSettingsForm.get('animation').value;
    if (showBorder) {
      this.analogueCompassWidgetSettingsForm.get('colorBorder').enable();
      this.analogueCompassWidgetSettingsForm.get('borderOuterWidth').enable();
    } else {
      this.analogueCompassWidgetSettingsForm.get('colorBorder').disable();
      this.analogueCompassWidgetSettingsForm.get('borderOuterWidth').disable();
    }
    if (animation) {
      this.analogueCompassWidgetSettingsForm.get('animationDuration').enable();
      this.analogueCompassWidgetSettingsForm.get('animationRule').enable();
      this.analogueCompassWidgetSettingsForm.get('animationTarget').enable();
    } else {
      this.analogueCompassWidgetSettingsForm.get('animationDuration').disable();
      this.analogueCompassWidgetSettingsForm.get('animationRule').disable();
      this.analogueCompassWidgetSettingsForm.get('animationTarget').disable();
    }
    this.analogueCompassWidgetSettingsForm.get('colorBorder').updateValueAndValidity({emitEvent});
    this.analogueCompassWidgetSettingsForm.get('borderOuterWidth').updateValueAndValidity({emitEvent});
    this.analogueCompassWidgetSettingsForm.get('animationDuration').updateValueAndValidity({emitEvent});
    this.analogueCompassWidgetSettingsForm.get('animationRule').updateValueAndValidity({emitEvent});
    this.analogueCompassWidgetSettingsForm.get('animationTarget').updateValueAndValidity({emitEvent});
  }

  protected prepareOutputSettings(settings) {
    settings.majorTickFont.color = this.analogueCompassWidgetSettingsForm.get('majorTickColor').value;
    return settings;
  }
}
