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

import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import {
  AutoDateFormatSettings, defaultAutoDateFormatSettings,
  FormatTimeUnit,
  formatTimeUnits,
  formatTimeUnitTranslations
} from '@shared/models/widget-settings.models';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-auto-date-format-settings-panel',
  templateUrl: './auto-date-format-settings-panel.component.html',
  providers: [],
  styleUrls: ['./auto-date-format-settings-panel.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class AutoDateFormatSettingsPanelComponent extends PageComponent implements OnInit {

  formatTimeUnits = formatTimeUnits;

  formatTimeUnitTranslations = formatTimeUnitTranslations;

  @Input()
  autoDateFormatSettings: AutoDateFormatSettings;

  @Input()
  defaultValues = defaultAutoDateFormatSettings;

  @Input()
  popover: TbPopoverComponent<AutoDateFormatSettingsPanelComponent>;

  @Output()
  autoDateFormatSettingsApplied = new EventEmitter<AutoDateFormatSettings>();

  autoDateFormatFormGroup: UntypedFormGroup;

  previewText: {[unit in FormatTimeUnit]: string} = {} as any;

  constructor(private date: DatePipe,
              private fb: UntypedFormBuilder,
              protected store: Store<AppState>,
              private destroyRef: DestroyRef) {
    super(store);
  }

  ngOnInit(): void {
    this.autoDateFormatFormGroup = this.fb.group({});
    for (const unit of formatTimeUnits) {
      this.autoDateFormatFormGroup.addControl(unit,
        this.fb.control(this.autoDateFormatSettings[unit] || this.defaultValues[unit], [Validators.required]));
      this.autoDateFormatFormGroup.get(unit).valueChanges.pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe((value: string) => {
        this.previewText[unit] = this.date.transform(Date.now(), value);
      });
      this.previewText[unit] = this.date.transform(Date.now(), this.autoDateFormatSettings[unit] || this.defaultValues[unit]);
    }
  }

  cancel() {
    this.popover?.hide();
  }

  applyAutoDateFormatSettings() {
    const autoDateFormatSettings: AutoDateFormatSettings = this.autoDateFormatFormGroup.value;
    for (const unit of formatTimeUnits) {
      if (autoDateFormatSettings[unit] === this.defaultValues[unit]) {
        delete autoDateFormatSettings[unit];
      }
    }
    this.autoDateFormatSettingsApplied.emit(autoDateFormatSettings);
  }

}
