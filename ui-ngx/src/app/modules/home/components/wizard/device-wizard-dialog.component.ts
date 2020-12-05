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

import { Component, Inject, OnDestroy, SkipSelf, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import {
  createDeviceProfileConfiguration,
  createDeviceProfileTransportConfiguration,
  Device,
  DeviceProfile,
  DeviceProfileType,
  DeviceProvisionConfiguration,
  DeviceProvisionType,
  DeviceTransportType,
  deviceTransportTypeConfigurationInfoMap,
  deviceTransportTypeHintMap,
  deviceTransportTypeTranslationMap
} from '@shared/models/device.models';
import { MatHorizontalStepper } from '@angular/material/stepper';
import { EntityType } from '@shared/models/entity-type.models';
import { DeviceProfileService } from '@core/http/device-profile.service';
import { EntityId } from '@shared/models/id/entity-id';
import { Observable, of, Subscription } from 'rxjs';
import { map, mergeMap, tap } from 'rxjs/operators';
import { DeviceService } from '@core/http/device.service';
import { ErrorStateMatcher } from '@angular/material/core';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { MediaBreakpoints } from '@shared/models/constants';
import { AddGroupEntityDialogData } from '@home/models/group/group-entity-component.models';
import { CustomerId } from '@shared/models/id/customer-id';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { Operation, Resource } from '@shared/models/security.models';
import { RuleChainId } from '@shared/models/id/rule-chain-id';
import { ServiceType } from '@shared/models/queue.models';
import { deepTrim } from '@core/utils';

@Component({
  selector: 'tb-device-wizard',
  templateUrl: './device-wizard-dialog.component.html',
  providers: [],
  styleUrls: ['./device-wizard-dialog.component.scss']
})
export class DeviceWizardDialogComponent extends
  DialogComponent<DeviceWizardDialogComponent, Device> implements OnDestroy, ErrorStateMatcher {

  @ViewChild('addDeviceWizardStepper', {static: true}) addDeviceWizardStepper: MatHorizontalStepper;

  resource = Resource;

  operation = Operation;

  selectedIndex = 0;

  showNext = true;

  createProfile = false;
  createTransportConfiguration = false;

  entityType = EntityType;

  deviceTransportTypes = Object.keys(DeviceTransportType);

  deviceTransportTypeTranslations = deviceTransportTypeTranslationMap;

  deviceTransportTypeHints = deviceTransportTypeHintMap;

  deviceWizardFormGroup: FormGroup;

  transportConfigFormGroup: FormGroup;

  alarmRulesFormGroup: FormGroup;

  provisionConfigFormGroup: FormGroup;

  credentialsFormGroup: FormGroup;

  labelPosition = 'end';

  entitiesTableConfig = this.data.entitiesTableConfig;

  entityGroup = this.entitiesTableConfig.entityGroup;

  serviceType = ServiceType.TB_RULE_ENGINE;

  private subscriptions: Subscription[] = [];

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: AddGroupEntityDialogData<Device>,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<DeviceWizardDialogComponent, Device>,
              private deviceProfileService: DeviceProfileService,
              private deviceService: DeviceService,
              private userPermissionService: UserPermissionsService,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder) {
    super(store, router, dialogRef);
    this.deviceWizardFormGroup = this.fb.group({
        name: ['', Validators.required],
        label: [''],
        gateway: [false],
        transportType: [DeviceTransportType.DEFAULT, Validators.required],
        addProfileType: [0],
        deviceProfileId: [null, Validators.required],
        newDeviceProfileTitle: [{value: null, disabled: true}],
        defaultRuleChainId: [{value: null, disabled: true}],
        defaultQueueName: [{value: null, disabled: true}],
        description: ['']
      }
    );

    this.subscriptions.push(this.deviceWizardFormGroup.get('addProfileType').valueChanges.subscribe(
      (addProfileType: number) => {
        if (addProfileType === 0) {
          this.deviceWizardFormGroup.get('deviceProfileId').setValidators([Validators.required]);
          this.deviceWizardFormGroup.get('deviceProfileId').enable();
          this.deviceWizardFormGroup.get('newDeviceProfileTitle').setValidators(null);
          this.deviceWizardFormGroup.get('newDeviceProfileTitle').disable();
          this.deviceWizardFormGroup.get('defaultRuleChainId').disable();
          this.deviceWizardFormGroup.get('defaultQueueName').disable();
          this.deviceWizardFormGroup.updateValueAndValidity();
          this.createProfile = false;
          this.createTransportConfiguration = false;
        } else {
          this.deviceWizardFormGroup.get('deviceProfileId').setValidators(null);
          this.deviceWizardFormGroup.get('deviceProfileId').disable();
          this.deviceWizardFormGroup.get('newDeviceProfileTitle').setValidators([Validators.required]);
          this.deviceWizardFormGroup.get('newDeviceProfileTitle').enable();
          this.deviceWizardFormGroup.get('defaultRuleChainId').enable();
          this.deviceWizardFormGroup.get('defaultQueueName').enable();

          this.deviceWizardFormGroup.updateValueAndValidity();
          this.createProfile = true;
          this.createTransportConfiguration = this.deviceWizardFormGroup.get('transportType').value &&
            deviceTransportTypeConfigurationInfoMap.get(this.deviceWizardFormGroup.get('transportType').value).hasProfileConfiguration;
        }
      }
    ));

    this.transportConfigFormGroup = this.fb.group(
      {
        transportConfiguration: [createDeviceProfileTransportConfiguration(DeviceTransportType.DEFAULT), Validators.required]
      }
    );
    this.subscriptions.push(this.deviceWizardFormGroup.get('transportType').valueChanges.subscribe((transportType) => {
      this.deviceProfileTransportTypeChanged(transportType);
    }));

    this.alarmRulesFormGroup = this.fb.group({
        alarms: [null]
      }
    );

    this.provisionConfigFormGroup = this.fb.group(
      {
        provisionConfiguration: [{
          type: DeviceProvisionType.DISABLED
        } as DeviceProvisionConfiguration, [Validators.required]]
      }
    );

    this.credentialsFormGroup  = this.fb.group({
        setCredential: [false],
        credential: [{value: null, disabled: true}]
      }
    );

    this.subscriptions.push(this.credentialsFormGroup.get('setCredential').valueChanges.subscribe((value) => {
      if (value) {
        this.credentialsFormGroup.get('credential').enable();
      } else {
        this.credentialsFormGroup.get('credential').disable();
      }
    }));

    this.labelPosition = this.breakpointObserver.isMatched(MediaBreakpoints['gt-sm']) ? 'end' : 'bottom';

    this.subscriptions.push(this.breakpointObserver
      .observe(MediaBreakpoints['gt-sm'])
      .subscribe((state: BreakpointState) => {
          if (state.matches) {
            this.labelPosition = 'end';
          } else {
            this.labelPosition = 'bottom';
          }
        }
      ));
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.subscriptions.forEach(s => s.unsubscribe());
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  previousStep(): void {
    this.addDeviceWizardStepper.previous();
  }

  nextStep(): void {
    this.addDeviceWizardStepper.next();
  }

  getFormLabel(index: number): string {
    if (index > 0) {
      if (!this.createProfile) {
        index += 3;
      } else if (!this.createTransportConfiguration) {
        index += 1;
      }
    }
    switch (index) {
      case 0:
        return 'device.wizard.device-details';
      case 1:
        return 'device-profile.transport-configuration';
      case 2:
        return 'device-profile.alarm-rules';
      case 3:
        return 'device-profile.device-provisioning';
      case 4:
        return 'device.credentials';
    }
  }

  get maxStepperIndex(): number {
    return this.addDeviceWizardStepper?._steps?.length - 1;
  }

  private deviceProfileTransportTypeChanged(deviceTransportType: DeviceTransportType): void {
    this.transportConfigFormGroup.patchValue(
      {transportConfiguration: createDeviceProfileTransportConfiguration(deviceTransportType)});
    this.createTransportConfiguration = this.createProfile && deviceTransportType &&
      deviceTransportTypeConfigurationInfoMap.get(deviceTransportType).hasProfileConfiguration;
  }

  add(): void {
    if (this.allValid()) {
      this.createDeviceProfile().pipe(
        mergeMap(profileId => this.createDevice(profileId)),
        mergeMap(device => this.saveCredentials(device))
      ).subscribe(
        (device) => {
          this.dialogRef.close(device);
        }
      );
    }
  }

  private createDeviceProfile(): Observable<EntityId> {
    if (this.deviceWizardFormGroup.get('addProfileType').value) {
      const deviceProvisionConfiguration: DeviceProvisionConfiguration = this.provisionConfigFormGroup.get('provisionConfiguration').value;
      const provisionDeviceKey = deviceProvisionConfiguration.provisionDeviceKey;
      delete deviceProvisionConfiguration.provisionDeviceKey;
      const deviceProfile: DeviceProfile = {
        name: this.deviceWizardFormGroup.get('newDeviceProfileTitle').value,
        type: DeviceProfileType.DEFAULT,
        transportType: this.deviceWizardFormGroup.get('transportType').value,
        provisionType: deviceProvisionConfiguration.type,
        provisionDeviceKey,
        profileData: {
          configuration: createDeviceProfileConfiguration(DeviceProfileType.DEFAULT),
          transportConfiguration: this.transportConfigFormGroup.get('transportConfiguration').value,
          alarms: this.alarmRulesFormGroup.get('alarms').value,
          provisionConfiguration: deviceProvisionConfiguration
        }
      };
      if (this.deviceWizardFormGroup.get('defaultRuleChainId').value) {
        deviceProfile.defaultRuleChainId = new RuleChainId(this.deviceWizardFormGroup.get('defaultRuleChainId').value);
      }
      return this.deviceProfileService.saveDeviceProfile(deepTrim(deviceProfile)).pipe(
        map(profile => profile.id),
        tap((profileId) => {
          this.deviceWizardFormGroup.patchValue({
            deviceProfileId: profileId,
            addProfileType: 0
          });
        })
      );
    } else {
      return of(this.deviceWizardFormGroup.get('deviceProfileId').value);
    }
  }

  private createDevice(profileId): Observable<Device> {
    const device = {
      name: this.deviceWizardFormGroup.get('name').value,
      label: this.deviceWizardFormGroup.get('label').value,
      deviceProfileId: profileId,
      additionalInfo: {
        gateway: this.deviceWizardFormGroup.get('gateway').value,
        description: this.deviceWizardFormGroup.get('description').value
      },
      customerId: null
    } as Device;
    if (this.entityGroup.ownerId.entityType === EntityType.CUSTOMER) {
      device.customerId = this.entityGroup.ownerId as CustomerId;
    }
    const entityGroupId = !this.entityGroup.groupAll ? this.entityGroup.id.id : null;
    return this.deviceService.saveDevice(deepTrim(device), entityGroupId);
  }

  private saveCredentials(device: Device): Observable<Device> {
    if (this.credentialsFormGroup.get('setCredential').value) {
      return this.deviceService.getDeviceCredentials(device.id.id).pipe(
        mergeMap(
          (deviceCredentials) => {
            const deviceCredentialsValue = {...deviceCredentials, ...this.credentialsFormGroup.value.credential};
            return this.deviceService.saveDeviceCredentials(deviceCredentialsValue);
          }
        ),
        map(() => device));
    }
    return of(device);
  }

  allValid(): boolean {
    if (this.addDeviceWizardStepper.steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addDeviceWizardStepper.selectedIndex = index;
        return true;
      } else {
        return false;
      }
    } )) {
      return false;
    } else {
      return true;
    }
  }

  changeStep($event: StepperSelectionEvent): void {
    this.selectedIndex = $event.selectedIndex;
    if (this.selectedIndex === this.maxStepperIndex) {
      this.showNext = false;
    } else {
      this.showNext = true;
    }
  }
}
