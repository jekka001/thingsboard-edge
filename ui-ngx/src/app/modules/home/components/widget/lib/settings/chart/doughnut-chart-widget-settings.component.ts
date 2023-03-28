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

import { Component } from '@angular/core';
import { WidgetSettings, WidgetSettingsComponent } from '@shared/models/widget.models';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';

@Component({
  selector: 'tb-doughnut-chart-widget-settings',
  templateUrl: './doughnut-chart-widget-settings.component.html',
  styleUrls: ['./../widget-settings.scss']
})
export class DoughnutChartWidgetSettingsComponent extends WidgetSettingsComponent {

  doughnutChartWidgetSettingsForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  protected settingsForm(): UntypedFormGroup {
    return this.doughnutChartWidgetSettingsForm;
  }

  protected defaultSettings(): WidgetSettings {
    return {
      showTooltip: true,
      borderWidth: 5,
      borderColor: '#fff',
      legend: {
        display: true,
        labelsFontColor: '#666'
      }
    };
  }

  protected onSettingsSet(settings: WidgetSettings) {
    this.doughnutChartWidgetSettingsForm = this.fb.group({

      // Common settings

      showTooltip: [settings.showTooltip, []],

      // Border settings

      borderWidth: [settings.borderWidth, [Validators.min(0)]],
      borderColor: [settings.borderColor, []],

      // Legend settings

      legend: this.fb.group({
        display: [settings.legend?.display, []],
        labelsFontColor: [settings.legend?.labelsFontColor, []]
      })
    });
  }

  protected validatorTriggers(): string[] {
    return ['legend.display'];
  }

  protected updateValidators(emitEvent: boolean) {
    const displayLegend: boolean = this.doughnutChartWidgetSettingsForm.get('legend.display').value;
    if (displayLegend) {
      this.doughnutChartWidgetSettingsForm.get('legend.labelsFontColor').enable();
    } else {
      this.doughnutChartWidgetSettingsForm.get('legend.labelsFontColor').disable();
    }
    this.doughnutChartWidgetSettingsForm.get('legend.labelsFontColor').updateValueAndValidity({emitEvent});
  }
}
