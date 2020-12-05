///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALUE_ACCESSOR,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import {
  DeviceProfileTransportConfiguration,
  DeviceTransportType,
  MqttDeviceProfileTransportConfiguration,
  MqttTransportPayloadType,
  mqttTransportPayloadTypeTranslationMap
} from '@shared/models/device.models';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-mqtt-device-profile-transport-configuration',
  templateUrl: './mqtt-device-profile-transport-configuration.component.html',
  styleUrls: ['./mqtt-device-profile-transport-configuration.component.scss'],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => MqttDeviceProfileTransportConfigurationComponent),
    multi: true
  }]
})
export class MqttDeviceProfileTransportConfigurationComponent implements ControlValueAccessor, OnInit {

  mqttTransportPayloadTypes = Object.keys(MqttTransportPayloadType);

  mqttTransportPayloadTypeTranslations = mqttTransportPayloadTypeTranslationMap;

  mqttDeviceProfileTransportConfigurationFormGroup: FormGroup;

  private defaultTelemetrySchema =
    'syntax ="proto3";\n' +
    'package telemetry;\n' +
    '\n' +
    'message SensorDataReading {\n' +
    '\n' +
    '  double temperature = 1;\n' +
    '  double humidity = 2;\n' +
    '  InnerObject innerObject = 3;\n' +
    '\n' +
    '  message InnerObject {\n' +
    '    string key1 = 1;\n' +
    '    bool key2 = 2;\n' +
    '    double key3 = 3;\n' +
    '    int32 key4 = 4;\n' +
    '    string key5 = 5;\n' +
    '  }\n' +
    '}\n';

  private defaultAttributesSchema =
    'syntax ="proto3";\n' +
    'package attributes;\n' +
    '\n' +
    'message SensorConfiguration {\n' +
    '  string firmwareVersion = 1;\n' +
    '  string serialNumber = 2;\n' +
    '}';

  private requiredValue: boolean;

  get required(): boolean {
    return this.requiredValue;
  }

  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  @Input()
  disabled: boolean;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private fb: FormBuilder) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.mqttDeviceProfileTransportConfigurationFormGroup = this.fb.group({
        deviceAttributesTopic: [null, [Validators.required, this.validationMQTTTopic()]],
        deviceTelemetryTopic: [null, [Validators.required, this.validationMQTTTopic()]],
        transportPayloadTypeConfiguration: this.fb.group({
          transportPayloadType: [MqttTransportPayloadType.JSON, Validators.required],
          deviceTelemetryProtoSchema: [this.defaultTelemetrySchema, Validators.required],
          deviceAttributesProtoSchema: [this.defaultAttributesSchema, Validators.required]
        })
      }, {validator: this.uniqueDeviceTopicValidator}
    );
    this.mqttDeviceProfileTransportConfigurationFormGroup.get('transportPayloadTypeConfiguration.transportPayloadType')
      .valueChanges.subscribe(payloadType => {
      this.updateTransportPayloadBasedControls(payloadType, true);
    });
    this.mqttDeviceProfileTransportConfigurationFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.mqttDeviceProfileTransportConfigurationFormGroup.disable({emitEvent: false});
    } else {
      this.mqttDeviceProfileTransportConfigurationFormGroup.enable({emitEvent: false});
    }
  }

  get protoPayloadType(): boolean {
    const transportPayloadType = this.mqttDeviceProfileTransportConfigurationFormGroup.get('transportPayloadTypeConfiguration.transportPayloadType').value;
    return transportPayloadType === MqttTransportPayloadType.PROTOBUF;
  }

  writeValue(value: MqttDeviceProfileTransportConfiguration | null): void {
    if (isDefinedAndNotNull(value)) {
      this.mqttDeviceProfileTransportConfigurationFormGroup.patchValue(value, {emitEvent: false});
      this.updateTransportPayloadBasedControls(value.transportPayloadTypeConfiguration?.transportPayloadType);
    }
  }

  private updateModel() {
    let configuration: DeviceProfileTransportConfiguration = null;
    if (this.mqttDeviceProfileTransportConfigurationFormGroup.valid) {
      configuration = this.mqttDeviceProfileTransportConfigurationFormGroup.value;
      configuration.type = DeviceTransportType.MQTT;
    }
    this.propagateChange(configuration);
  }

  private updateTransportPayloadBasedControls(type: MqttTransportPayloadType, forceUpdated = false) {
    const transportPayloadTypeForm = this.mqttDeviceProfileTransportConfigurationFormGroup
      .get('transportPayloadTypeConfiguration') as FormGroup;
    if (forceUpdated) {
      transportPayloadTypeForm.patchValue({
        deviceTelemetryProtoSchema: this.defaultTelemetrySchema,
        deviceAttributesProtoSchema: this.defaultAttributesSchema
      }, {emitEvent: false});
    }
    if (type === MqttTransportPayloadType.PROTOBUF && !this.disabled) {
      transportPayloadTypeForm.get('deviceTelemetryProtoSchema').enable({emitEvent: false});
      transportPayloadTypeForm.get('deviceAttributesProtoSchema').enable({emitEvent: false});
    } else {
      transportPayloadTypeForm.get('deviceTelemetryProtoSchema').disable({emitEvent: false});
      transportPayloadTypeForm.get('deviceAttributesProtoSchema').disable({emitEvent: false});
    }
  }

  private validationMQTTTopic(): ValidatorFn {
    return (c: FormControl) => {
      const newTopic = c.value;
      const wildcardSymbols = /[#+]/g;
      let findSymbol = wildcardSymbols.exec(newTopic);
      while (findSymbol) {
        const index = findSymbol.index;
        const currentSymbol = findSymbol[0];
        const prevSymbol = index > 0 ? newTopic[index - 1] : null;
        const nextSymbol = index < (newTopic.length - 1) ? newTopic[index + 1] : null;
        if (currentSymbol === '#' && (index !== (newTopic.length - 1) || (prevSymbol !== null && prevSymbol !== '/'))) {
          return {
            invalidMultiTopicCharacter: {
              valid: false
            }
          };
        }
        if (currentSymbol === '+' && ((prevSymbol !== null && prevSymbol !== '/') || (nextSymbol !== null && nextSymbol !== '/'))) {
          return {
            invalidSingleTopicCharacter: {
              valid: false
            }
          };
        }
        findSymbol = wildcardSymbols.exec(newTopic);
      }
      return null;
    };
  }

  private uniqueDeviceTopicValidator(control: FormGroup): { [key: string]: boolean } | null {
    if (control.value) {
      const formValue = control.value as MqttDeviceProfileTransportConfiguration;
      if (formValue.deviceAttributesTopic === formValue.deviceTelemetryTopic) {
        return {unique: true};
      }
    }
    return null;
  }
}
