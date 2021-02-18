///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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

import {
  AfterViewInit,
  Component,
  ComponentFactoryResolver,
  Inject,
  Injector,
  SkipSelf,
  ViewChild
} from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormControl, FormGroupDirective, NgForm } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { TenantProfile } from '@shared/models/tenant.model';
import { TenantProfileComponent } from './tenant-profile.component';
import { TenantProfileService } from '@core/http/tenant-profile.service';

export interface TenantProfileDialogData {
  tenantProfile: TenantProfile;
  isAdd: boolean;
}

@Component({
  selector: 'tb-tenant-profile-dialog',
  templateUrl: './tenant-profile-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: TenantProfileDialogComponent}],
  styleUrls: []
})
export class TenantProfileDialogComponent extends
  DialogComponent<TenantProfileDialogComponent, TenantProfile> implements ErrorStateMatcher, AfterViewInit {

  isAdd: boolean;
  tenantProfile: TenantProfile;

  submitted = false;

  @ViewChild('tenantProfileComponent', {static: true}) tenantProfileComponent: TenantProfileComponent;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: TenantProfileDialogData,
              public dialogRef: MatDialogRef<TenantProfileDialogComponent, TenantProfile>,
              private componentFactoryResolver: ComponentFactoryResolver,
              private injector: Injector,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              private tenantProfileService: TenantProfileService) {
    super(store, router, dialogRef);
    this.isAdd = this.data.isAdd;
    this.tenantProfile = this.data.tenantProfile;
  }

  ngAfterViewInit(): void {
    if (this.isAdd) {
      setTimeout(() => {
        this.tenantProfileComponent.entityForm.markAsDirty();
      }, 0);
    }
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    if (this.tenantProfileComponent.entityForm.valid) {
      this.tenantProfile = {...this.tenantProfile, ...this.tenantProfileComponent.entityFormValue()};
      this.tenantProfileService.saveTenantProfile(this.tenantProfile).subscribe(
        (tenantProfile) => {
          this.dialogRef.close(tenantProfile);
        }
      );
    }
  }

}
