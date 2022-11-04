///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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

import { Component, forwardRef } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { isDefinedAndNotNull, isNumber } from '@core/utils';
import { takeUntil } from 'rxjs/operators';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import {
  ThingsStartHostType,
  ThingsStartHostTypeTranslation,
  ttnVersion,
  ttnVersionMap
} from '@home/components/integration/integration.models';
import { IntegrationCredentialType, TtnIntegration, } from '@shared/models/integration.models';

@Component({
  selector: 'tb-ttn-integration-form',
  templateUrl: './tts-integration-form.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => TtnIntegrationFormComponent),
    multi: true
  },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => TtnIntegrationFormComponent),
      multi: true,
    }]
})
export class TtnIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator {

  ttnIntegrationConfigForm: FormGroup;

  hostEdit: FormControl;
  apiVersion: FormControl;

  ThingsStartHostType = ThingsStartHostType;
  ThingsStartHostTypes = Object.values(ThingsStartHostType).filter(v => isNumber(v));
  ThingsStartHostTypeTranslation = ThingsStartHostTypeTranslation;
  IntegrationCredentialType = IntegrationCredentialType;

  hostRegionSuffix = '.cloud.thethings.network';
  hideSelectVersion = false;

  userNameLabel = 'integration.application-id';
  userNameRequired = 'integration.application-id-required';
  passwordLabel = 'integration.access-key';
  passwordRequired = 'integration.access-key-required';

  private downlinkPattern = ttnVersionMap.get(ttnVersion.v3).downlinkPattern;
  private propagateChange = (v: any) => { };

  constructor(protected fb: FormBuilder) {
    super();
    this.hostEdit = this.fb.control('', Validators.required);
    this.apiVersion = this.fb.control(true);
    this.ttnIntegrationConfigForm = this.fb.group({
      clientConfiguration: this.fb.group({
        host: ['', Validators.required],
        customHost: [false],
        port: [8883],
        ssl: [true],
        maxBytesInMessage: [32368, [Validators.min(1), Validators.max(256000000)]],
        connectTimeoutSec: [10, [Validators.required, Validators.min(1), Validators.max(200)]],
        credentials: [{
          type: 'basic',
          username: '',
          password: ''
        }]
      }),
      topicFilters: [{value: ttnVersionMap.get(ttnVersion.v3).uplinkTopic, disabled: true}, Validators.required],
      downlinkTopicPattern: [this.downlinkPattern, Validators.required]
    });

    this.hostEdit.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value) => {
      this.ttnIntegrationConfigForm.get('clientConfiguration.host').patchValue(this.buildHostName(value));
      if (this.ttnIntegrationConfigForm.get('clientConfiguration.host').pristine) {
        this.ttnIntegrationConfigForm.get('clientConfiguration.host').markAsDirty();
      }
    });

    this.apiVersion.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((value: boolean) => {
      this.updateTtnVersionState(Number(value));
    });

    this.ttnIntegrationConfigForm.get('clientConfiguration.customHost').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.hostEdit.patchValue('');
      this.hostEdit.markAsUntouched();
      this.hostEdit.markAsPristine();
    });

    this.ttnIntegrationConfigForm.get('clientConfiguration.credentials').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(name => {
      this.updateDownlinkPattern(name.username);
    });

    this.ttnIntegrationConfigForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateModels(this.ttnIntegrationConfigForm.getRawValue());
    });
  }

  writeValue(value: TtnIntegration) {
    if (isDefinedAndNotNull(value)) {
      this.ttnIntegrationConfigForm.reset(value, {emitEvent: false});
      if (isDefinedAndNotNull(value.clientConfiguration)) {
        if (value.clientConfiguration.customHost === !!ThingsStartHostType.Custom) {
          this.hostEdit.patchValue('', {emitEvent: false});
        } else if (value.clientConfiguration.host && value.clientConfiguration.host.endsWith(this.hostRegionSuffix)) {
          this.hostEdit.patchValue(value.clientConfiguration.host.slice(0, -this.hostRegionSuffix.length), {emitEvent: false});
        } else {
          this.hostEdit.patchValue(value.clientConfiguration.host, {emitEvent: false});
        }
      }
      let apiVersion = false;
      if (value.downlinkTopicPattern?.startsWith('v3')) {
        apiVersion = true;
      }
      this.apiVersion.patchValue(apiVersion, {emitEvent: false});
      this.downlinkPattern = ttnVersionMap.get(Number(apiVersion)).downlinkPattern;
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.ttnIntegrationConfigForm.disable({emitEvent: false});
      this.hostEdit.disable({emitEvent: false});
      this.apiVersion.disable({emitEvent: false});
    } else {
      this.ttnIntegrationConfigForm.enable({emitEvent: false});
      this.hostEdit.enable({emitEvent: false});
      this.apiVersion.enable({emitEvent: false});
      this.ttnIntegrationConfigForm.get('topicFilters').disable({emitEvent: false});
    }
  }

  private updateModels(value) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.ttnIntegrationConfigForm.valid ? null : {
      ttnIntegrationConfigForm: {valid: false}
    };
  }

  private buildHostName(host: string): string {
    if (this.ttnIntegrationConfigForm.get('clientConfiguration.customHost').value === !!ThingsStartHostType.Region) {
      if (host.length) {
        return host + this.hostRegionSuffix;
      }
      return '';
    }
    return host;
  }

  private updateTtnVersionState(value: ttnVersion) {
    this.downlinkPattern = ttnVersionMap.get(value).downlinkPattern;
    this.ttnIntegrationConfigForm.get('topicFilters').patchValue(ttnVersionMap.get(value).uplinkTopic, {emitEvent: false});

    this.updateDownlinkPattern(this.ttnIntegrationConfigForm.get('clientConfiguration.credentials').value.username, true);
  }

  private updateDownlinkPattern(name: string, emitEvent = false) {
    let pattern = this.downlinkPattern;
    if (name.length) {
      pattern = this.downlinkPattern.replace('${applicationId}', name);
    }
    this.ttnIntegrationConfigForm.get('downlinkTopicPattern').patchValue(pattern, {emitEvent});
  }
}
