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

import { Component, DestroyRef, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator,
  Validators
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import {
  HereMapProvider,
  HereMapProviderSettings,
  hereMapProviderTranslationMap
} from '@home/components/widget/lib/maps/map-models';
import { isDefinedAndNotNull } from '@core/utils';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-here-map-provider-settings',
  templateUrl: './here-map-provider-settings.component.html',
  styleUrls: ['./../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => HereMapProviderSettingsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => HereMapProviderSettingsComponent),
      multi: true
    }
  ]
})
export class HereMapProviderSettingsComponent extends PageComponent implements OnInit, ControlValueAccessor, Validator {

  @Input()
  disabled: boolean;

  private modelValue: HereMapProviderSettings;

  private propagateChange = null;

  public providerSettingsFormGroup: FormGroup;

  hereMapProviders = Object.values(HereMapProvider);

  hereMapProviderTranslations = hereMapProviderTranslationMap;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private fb: FormBuilder,
              private destroyRef: DestroyRef) {
    super(store);
  }

  ngOnInit(): void {
    this.providerSettingsFormGroup = this.fb.group({
      mapProviderHere: [null, [Validators.required]],
      credentials: this.fb.group({
        useV3: [true],
        app_id: [null, [Validators.required]],
        app_code: [null, [Validators.required]],
        apiKey: [null, [Validators.required]]
      })
    });
    this.providerSettingsFormGroup.get('credentials.useV3').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(value => {
      if (value) {
        this.providerSettingsFormGroup.get('credentials.apiKey').enable({emitEvent: false});
        this.providerSettingsFormGroup.get('credentials.app_id').disable({emitEvent: false});
        this.providerSettingsFormGroup.get('credentials.app_code').disable({emitEvent: false});
      } else {
        this.providerSettingsFormGroup.get('credentials.apiKey').disable({emitEvent: false});
        this.providerSettingsFormGroup.get('credentials.app_id').enable({emitEvent: false});
        this.providerSettingsFormGroup.get('credentials.app_code').enable({emitEvent: false});
      }
    });
    this.providerSettingsFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateModel();
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.providerSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.providerSettingsFormGroup.enable({emitEvent: false});
      this.providerSettingsFormGroup.get('credentials.useV3').updateValueAndValidity({onlySelf: true});
    }
  }

  writeValue(value: HereMapProviderSettings): void {
    if (!isDefinedAndNotNull(value.credentials.useV3)) {
      if (isDefinedAndNotNull(value.credentials.app_id) && isDefinedAndNotNull(value.credentials.app_code)) {
        value.credentials.useV3 = false;
      }
    }
    this.modelValue = value;
    this.providerSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
    this.providerSettingsFormGroup.get('credentials.useV3').updateValueAndValidity({onlySelf: true});
  }

  public validate(c: FormControl) {
    return this.providerSettingsFormGroup.valid ? null : {
      hereMapProviderSettings: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const value: HereMapProviderSettings = this.providerSettingsFormGroup.value;
    this.modelValue = value;
    this.propagateChange(this.modelValue);
  }
}
