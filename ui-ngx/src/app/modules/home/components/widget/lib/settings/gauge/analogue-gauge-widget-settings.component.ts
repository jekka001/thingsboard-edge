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

import { WidgetSettings, WidgetSettingsComponent } from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';

export class AnalogueGaugeWidgetSettingsComponent extends WidgetSettingsComponent {

  analogueGaugeWidgetSettingsForm: UntypedFormGroup;

  ctx = {
    settingsForm: null
  };

  constructor(protected store: Store<AppState>,
              protected fb: UntypedFormBuilder) {
    super(store);
  }

  protected settingsForm(): UntypedFormGroup {
    return this.analogueGaugeWidgetSettingsForm;
  }

  protected defaultSettings(): WidgetSettings {
    return {
      startAngle: 45,
      ticksAngle: 270,
      needleCircleSize: 10,
      minValue: 0,
      maxValue: 100,
      showUnitTitle: true,
      unitTitle: null,
      majorTicksCount: null,
      minorTicks: 2,
      valueBox: true,
      valueInt: 3,
      defaultColor: null,
      colorPlate: '#fff',
      colorMajorTicks: '#444',
      colorMinorTicks: '#666',
      colorNeedle: null,
      colorNeedleEnd: null,
      colorNeedleShadowUp: 'rgba(2,255,255,0.2)',
      colorNeedleShadowDown: 'rgba(188,143,143,0.45)',
      colorValueBoxRect: '#888',
      colorValueBoxRectEnd: '#666',
      colorValueBoxBackground: '#babab2',
      colorValueBoxShadow: 'rgba(0,0,0,1)',
      highlights: [],
      highlightsWidth: 15,
      showBorder: true,
      numbersFont: {
        family: 'Roboto',
        size: 18,
        style: 'normal',
        weight: '500',
        color: null
      },
      titleFont: {
        family: 'Roboto',
        size: 24,
        style: 'normal',
        weight: '500',
        color: '#888'
      },
      unitsFont: {
        family: 'Roboto',
        size: 22,
        style: 'normal',
        weight: '500',
        color: '#888'
      },
      valueFont: {
        family: 'Roboto',
        size: 40,
        style: 'normal',
        weight: '500',
        color: '#444',
        shadowColor: 'rgba(0,0,0,0.3)'
      },
      animation: true,
      animationDuration: 500,
      animationRule: 'cycle'
    };
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.analogueGaugeWidgetSettingsForm = this.fb.group({

      // Radial gauge settings
      startAngle: [settings.startAngle, [Validators.min(0), Validators.max(360)]],
      ticksAngle: [settings.ticksAngle, [Validators.min(0), Validators.max(360)]],
      needleCircleSize: [settings.needleCircleSize, [Validators.min(0)]],

      defaultColor: [settings.defaultColor, []],

      // Ticks settings
      minValue: [settings.minValue, []],
      maxValue: [settings.maxValue, []],
      majorTicksCount: [settings.majorTicksCount, [Validators.min(0)]],
      colorMajorTicks: [settings.colorMajorTicks, []],
      minorTicks: [settings.minorTicks, [Validators.min(0)]],
      colorMinorTicks: [settings.colorMinorTicks, []],
      numbersFont: [settings.numbersFont, []],
      numbersColor: [settings.numbersFont.color, []],

      // Unit title settings
      showUnitTitle: [settings.showUnitTitle, []],
      unitTitle: [settings.unitTitle, []],
      titleFont: [settings.titleFont, []],
      titleColor: [settings.titleFont.color, []],

      // Units settings
      unitsFont: [settings.unitsFont, []],
      unitsColor: [settings.unitsFont.color, []],

      // Value box settings
      valueBox: [settings.valueBox, []],
      valueInt: [settings.valueInt, [Validators.min(0)]],
      valueFont: [settings.valueFont, []],
      valueColor: [settings.valueFont.color, []],
      valueColorShadow: [settings.valueFont.shadowColor, []],
      colorValueBoxRect: [settings.colorValueBoxRect, []],
      colorValueBoxRectEnd: [settings.colorValueBoxRectEnd, []],
      colorValueBoxBackground: [settings.colorValueBoxBackground, []],
      colorValueBoxShadow: [settings.colorValueBoxShadow, []],

      // Plate settings
      showBorder: [settings.showBorder, []],
      colorPlate: [settings.colorPlate, []],

      // Needle settings
      colorNeedle: [settings.colorNeedle, []],
      colorNeedleEnd: [settings.colorNeedleEnd, []],
      colorNeedleShadowUp: [settings.colorNeedleShadowUp, []],
      colorNeedleShadowDown: [settings.colorNeedleShadowDown, []],

      // Highlights settings
      highlightsWidth: [settings.highlightsWidth, [Validators.min(0)]],
      highlights: [settings.highlights, []],

      // Animation settings
      animation: [settings.animation, []],
      animationDuration: [settings.animationDuration, [Validators.min(0)]],
      animationRule: [settings.animationRule, []],
    });
    this.ctx.settingsForm = this.analogueGaugeWidgetSettingsForm;
  }

  protected validatorTriggers(): string[] {
    return ['showUnitTitle', 'valueBox', 'animation'];
  }

  protected updateValidators(emitEvent: boolean) {
    const showUnitTitle: boolean = this.analogueGaugeWidgetSettingsForm.get('showUnitTitle').value;
    const valueBox: boolean = this.analogueGaugeWidgetSettingsForm.get('valueBox').value;
    const animation: boolean = this.analogueGaugeWidgetSettingsForm.get('animation').value;
    if (showUnitTitle) {
      this.analogueGaugeWidgetSettingsForm.get('unitTitle').enable();
      this.analogueGaugeWidgetSettingsForm.get('titleFont').enable();
      this.analogueGaugeWidgetSettingsForm.get('titleColor').enable();
    } else {
      this.analogueGaugeWidgetSettingsForm.get('unitTitle').disable();
      this.analogueGaugeWidgetSettingsForm.get('titleFont').disable();
      this.analogueGaugeWidgetSettingsForm.get('titleColor').disable();
    }
    if (valueBox) {
      this.analogueGaugeWidgetSettingsForm.get('valueInt').enable();
      this.analogueGaugeWidgetSettingsForm.get('valueFont').enable();
      this.analogueGaugeWidgetSettingsForm.get('valueColor').enable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxRect').enable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxRectEnd').enable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxBackground').enable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxShadow').enable();
    } else {
      this.analogueGaugeWidgetSettingsForm.get('valueInt').disable();
      this.analogueGaugeWidgetSettingsForm.get('valueFont').disable();
      this.analogueGaugeWidgetSettingsForm.get('valueColor').disable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxRect').disable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxRectEnd').disable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxBackground').disable();
      this.analogueGaugeWidgetSettingsForm.get('colorValueBoxShadow').disable();
    }
    if (animation) {
      this.analogueGaugeWidgetSettingsForm.get('animationDuration').enable();
      this.analogueGaugeWidgetSettingsForm.get('animationRule').enable();
    } else {
      this.analogueGaugeWidgetSettingsForm.get('animationDuration').disable();
      this.analogueGaugeWidgetSettingsForm.get('animationRule').disable();
    }
    this.analogueGaugeWidgetSettingsForm.get('unitTitle').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('titleFont').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('titleColor').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('valueInt').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('valueFont').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('valueColor').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('colorValueBoxRect').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('colorValueBoxRectEnd').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('colorValueBoxBackground').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('colorValueBoxShadow').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('animationDuration').updateValueAndValidity({emitEvent});
    this.analogueGaugeWidgetSettingsForm.get('animationRule').updateValueAndValidity({emitEvent});
  }

  protected prepareOutputSettings(settings) {
    settings.numbersFont.color = this.analogueGaugeWidgetSettingsForm.get('numbersColor').value;
    if (settings.titleFont) {
      settings.titleFont.color = this.analogueGaugeWidgetSettingsForm.get('titleColor').value;
    }
    settings.unitsFont.color = this.analogueGaugeWidgetSettingsForm.get('unitsColor').value;
    if (settings.valueFont) {
      settings.valueFont.color = this.analogueGaugeWidgetSettingsForm.get('valueColor').value;
      settings.valueFont.shadowColor = this.analogueGaugeWidgetSettingsForm.get('valueColorShadow').value;
    }
    return settings;
  }

}
