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

import { Component, forwardRef } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { IntegrationForm } from '@home/components/integration/configuration/integration-form';
import { takeUntil } from 'rxjs/operators';
import { isDefinedAndNotNull } from '@core/utils';
import { AzureIotHubIntegration, IntegrationCredentialType, MqttQos } from '@shared/models/integration.models';
import { privateNetworkAddressValidator } from '@home/components/integration/integration.models';
import { DEFAULT_MQTT_VERSION, MqttVersion } from '@shared/models/mqtt.models';

@Component({
  selector: 'tb-azure-iot-hub-integration-form',
  templateUrl: './azure-iot-hub-integration-form.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => AzureIotHubIntegrationFormComponent),
    multi: true
  },
  {
    provide: NG_VALIDATORS,
    useExisting: forwardRef(() => AzureIotHubIntegrationFormComponent),
    multi: true,
  }]
})
export class AzureIotHubIntegrationFormComponent extends IntegrationForm implements ControlValueAccessor, Validator {

  azureIotConfigForm: UntypedFormGroup;

  IntegrationCredentialType = IntegrationCredentialType;
  MqttVersion = MqttVersion;
  MqttQos = MqttQos;

  private propagateChange = (v: any) => { };

  constructor(private fb: UntypedFormBuilder) {
    super();
    this.azureIotConfigForm = this.fb.group({
      clientConfiguration: this.fb.group({
        host: ['\<name\>.azure-devices.net', Validators.required],
        clientId: ['device_id', [Validators.required, Validators.maxLength(65535)]],
        maxBytesInMessage: [32368, [Validators.min(1), Validators.max(256000000)]],
        protocolVersion: [DEFAULT_MQTT_VERSION],
        credentials: [{
          type: IntegrationCredentialType.SAS
        }],
      }),
      topicFilters: [[{
        filter: '#',
        qos: 0
      }], Validators.required]
    });
    this.azureIotConfigForm.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => this.updateModels(this.azureIotConfigForm.getRawValue()));
  }

  writeValue(value: AzureIotHubIntegration) {
    if (isDefinedAndNotNull(value)) {
      this.azureIotConfigForm.reset(value, {emitEvent: false});
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any) { }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.azureIotConfigForm.disable({emitEvent: false});
    } else {
      this.azureIotConfigForm.enable({emitEvent: false});
    }
  }

  private updateModels(value) {
    this.propagateChange(value);
  }

  validate(): ValidationErrors | null {
    return this.azureIotConfigForm.valid ? null : {
      azureIotConfigForm: {valid: false}
    };
  }

  updatedValidationPrivateNetwork() {
    if (this.allowLocalNetwork) {
      this.azureIotConfigForm.get('clientConfiguration.host').removeValidators(privateNetworkAddressValidator);
    } else {
      this.azureIotConfigForm.get('clientConfiguration.host').addValidators(privateNetworkAddressValidator);
    }
    this.azureIotConfigForm.get('clientConfiguration.host').updateValueAndValidity({emitEvent: false});
  }
}
