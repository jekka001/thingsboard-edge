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

import { Component, Inject, ViewChild } from '@angular/core';
import { MobileApp, MobileAppBundle, MobileAppBundleInfo } from '@shared/models/mobile-app.models';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MediaBreakpoints } from '@shared/models/constants';
import { map } from 'rxjs/operators';
import { forkJoin, Observable } from 'rxjs';
import { StepperOrientation, StepperSelectionEvent } from '@angular/cdk/stepper';
import { BreakpointObserver } from '@angular/cdk/layout';
import { FormBuilder, Validators } from '@angular/forms';
import { EntityType } from '@shared/models/entity-type.models';
import { PlatformType } from '@shared/models/oauth2.models';
import { MatStepper } from '@angular/material/stepper';
import {
  MobileAppDialogComponent,
  MobileAppDialogData
} from '@home/pages/mobile/applications/mobile-app-dialog.component';
import { ClientDialogComponent } from '@home/pages/admin/oauth2/clients/client-dialog.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MobileAppService } from '@core/http/mobile-app.service';
import { deepClone, deepTrim } from '@core/utils';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { Operation, Resource } from '@shared/models/security.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';

export interface MobileBundleDialogData {
  bundle?: MobileAppBundleInfo;
  isAdd?: boolean;
}

@Component({
  selector: 'tb-mobile-bundle-dialog',
  templateUrl: './mobile-bundle-dialog.component.html',
  styleUrls: ['./mobile-bundle-dialog.component.scss']
})
export class MobileBundleDialogComponent extends DialogComponent<MobileBundleDialogComponent, MobileAppBundle> {

  @ViewChild('addMobileBundle', {static: true}) addMobileBundle: MatStepper;

  readonly entityType = EntityType;
  readonly resource = Resource;
  readonly operation = Operation;

  selectedIndex = 0;

  dialogTitle = 'mobile.edit-bundle';

  stepperOrientation: Observable<StepperOrientation>;

  platformType = PlatformType;

  bundlesForms = this.fb.group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
    androidAppId: [null],
    iosAppId: [null],
    description: [''],
  });

  oauthForms = this.fb.group({
    oauth2Enabled: [true],
    oauth2ClientIds: [null]
  });

  layoutForms = this.fb.group({
    layoutConfig: [null]
  });

  selfRegistrationForm = this.fb.group({
    selfRegistrationParams: [null]
  });

  isAdd = false;
  readonly isSysAdmin = getCurrentAuthUser(this.store).authority === Authority.SYS_ADMIN;

  readonly = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              protected dialogRef: MatDialogRef<MobileBundleDialogComponent, MobileAppBundle>,
              @Inject(MAT_DIALOG_DATA) public data: MobileBundleDialogData,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder,
              private dialog: MatDialog,
              private mobileAppService: MobileAppService,
              private userPermissionsService: UserPermissionsService) {
    super(store, router, dialogRef);

    if (this.data.isAdd) {
      this.dialogTitle = 'mobile.add-bundle';
      this.isAdd = true;
      this.readonly = !this.userPermissionsService.hasGenericPermission(Resource.MOBILE_APP_BUNDLE, Operation.CREATE);
    } else {
      this.readonly = !this.userPermissionsService.hasGenericPermission(Resource.MOBILE_APP_BUNDLE, Operation.WRITE);
    }

    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-xs'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));

    this.oauthForms.get('oauth2Enabled').valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe(value => {
      if (value) {
        this.oauthForms.get('oauth2ClientIds').enable({emitEvent: false});
      } else {
        this.oauthForms.get('oauth2ClientIds').disable({emitEvent: false});
      }
    })

    if (!this.data.isAdd && this.data.bundle) {
      this.bundlesForms.patchValue(this.data.bundle, {emitEvent: false});
      this.oauthForms.get('oauth2Enabled').setValue(this.data.bundle.oauth2Enabled, {onlySelf: true});
      this.oauthForms.get('oauth2ClientIds')
        .setValue(deepClone(this.data.bundle.oauth2ClientInfos.map(item => item.id.id)), {emitEvent: false});
      this.layoutForms.patchValue(this.data.bundle, {emitEvent: false});
      this.selfRegistrationForm.patchValue(this.data.bundle, {emitEvent: false});
    }

    if (this.readonly) {
      this.bundlesForms.disable({emitEvent: false});
      this.oauthForms.disable({emitEvent: false});
      this.layoutForms.disable({emitEvent: false});
      this.selfRegistrationForm.disable({emitEvent: false});
    }
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  backStep() {
    this.addMobileBundle.previous();
  }

  nextStep() {
    if (this.selectedIndex >= this.maxStepperIndex) {
      this.readonly ? this.cancel() : this.add();
    } else {
      this.addMobileBundle.next();
    }
  }

  nextStepLabel(): string {
    if (this.selectedIndex >= this.maxStepperIndex) {
      if (this.readonly) {
        return 'action.close';
      }
      return this.data.isAdd ? 'action.add' : 'action.save';
    }
    return 'action.next';
  }

  changeStep($event: StepperSelectionEvent) {
    this.selectedIndex = $event.selectedIndex;
    if ($event.previouslySelectedIndex > $event.selectedIndex) {
      $event.previouslySelectedStep.interacted = false;
    }
  }

  createApplication(formControl: string, platformType: PlatformType) {
    this.dialog.open<MobileAppDialogComponent, MobileAppDialogData, MobileApp>(MobileAppDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        platformType
      }
    }).afterClosed()
      .subscribe((app) => {
        if (app) {
          this.bundlesForms.get(formControl).patchValue(app.id);
          this.bundlesForms.get(formControl).markAsDirty();
        }
      });
  }

  createClient($event: Event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    this.dialog.open<ClientDialogComponent>(ClientDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {}
    }).afterClosed()
      .subscribe((client) => {
        if (client) {
          const formValue = this.oauthForms.get('oauth2ClientIds').value ?
            [...this.oauthForms.get('oauth2ClientIds').value] : [];
          formValue.push(client.id.id);
          this.oauthForms.get('oauth2ClientIds').patchValue(formValue);
          this.oauthForms.get('oauth2ClientIds').markAsDirty();
        }
      });
  }

  private get maxStepperIndex(): number {
    return this.addMobileBundle?._steps?.length - 1;
  }

  private add(): void {
    if (this.allValid()) {
      let task = {
        mobileBundle: this.mobileAppService.saveMobileAppBundle(this.mobileAppBundleFormValue, this.oauthForms.value.oauth2ClientIds),
        oath2Clients: null
      }
      if (this.data.isAdd) {
        delete task.oath2Clients;
      } else {
        const mobileBundle: MobileAppBundle = {...this.data.bundle, ...this.mobileAppBundleFormValue};
        task.mobileBundle = this.mobileAppService.saveMobileAppBundle(mobileBundle);
        task.oath2Clients = this.mobileAppService.updateOauth2Clients(mobileBundle.id.id, this.oauthForms.get('oauth2ClientIds').value);
      }
      forkJoin(task).subscribe(
        (res) => this.dialogRef.close(res.mobileBundle)
      );
    }
  }

  private allValid(): boolean {
    return !this.addMobileBundle.steps.find((item, index) => {
      if (item.stepControl?.invalid) {
        item.interacted = true;
        this.addMobileBundle.selectedIndex = index;
        return true;
      } else {
        return false;
      }
    });
  }

  private get mobileAppBundleFormValue(): MobileAppBundle {
    const formValue = deepTrim(this.bundlesForms.value) as MobileAppBundle;
    formValue.layoutConfig = deepTrim(this.layoutForms.value.layoutConfig);
    formValue.oauth2Enabled = this.oauthForms.value.oauth2Enabled;
    if (!this.isSysAdmin) {
      formValue.selfRegistrationParams = this.selfRegistrationForm.value.selfRegistrationParams;
    }
    return formValue;
  }
}
