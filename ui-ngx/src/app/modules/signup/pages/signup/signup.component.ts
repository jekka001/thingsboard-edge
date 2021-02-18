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

import { Component, OnInit, ViewChild } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { FormBuilder } from '@angular/forms';
import { SignupRequest, SignUpResult } from '@shared/models/signup.models';
import { ActivatedRoute, Router } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { SignupService } from '@core/http/signup.service';
import { DialogService } from '@core/services/dialog.service';
import { RecaptchaComponent } from 'ng-recaptcha';
import { SelfRegistrationService } from '@core/http/self-register.service';
import { WhiteLabelingService } from '@core/http/white-labeling.service';
import { MatDialog } from '@angular/material/dialog';
import { PrivacyPolicyDialogComponent } from '@modules/signup/pages/signup/privacy-policy-dialog.component';

@Component({
  selector: 'tb-signup',
  templateUrl: './signup.component.html',
  styleUrls: ['./signup.component.scss']
})
export class SignupComponent extends PageComponent implements OnInit {

  @ViewChild('recaptcha') recaptchaComponent: RecaptchaComponent;

  signup = this.fb.group(SignupRequest.create());
  passwordCheck: string;
  acceptPrivacyPolicy: boolean;
  signupParams = this.selfRegistrationService.signUpParams;

  constructor(protected store: Store<AppState>,
              private route: ActivatedRoute,
              private router: Router,
              private authService: AuthService,
              private signupService: SignupService,
              public wl: WhiteLabelingService,
              private selfRegistrationService: SelfRegistrationService,
              private dialogService: DialogService,
              private translate: TranslateService,
              private dialog: MatDialog,
              public fb: FormBuilder) {
    super(store);
  }

  ngOnInit() {
  }

  signUp(): void {
    if (this.signup.valid) {
      if (this.validateSignUpRequest()) {
        this.signupService.signup(this.signup.value).subscribe(
          (signupResult) => {
            if (signupResult === SignUpResult.INACTIVE_USER_EXISTS) {
              this.promptToResendEmailVerification();
              this.recaptchaComponent.reset();
            } else {
              this.router.navigateByUrl('/signup/emailVerification?email=' + this.signup.get('email').value);
            }
          }, () => {
            this.recaptchaComponent.reset();
          }
        );
      }
    } else {
      Object.keys(this.signup.controls).forEach(field => {
        const control = this.signup.get(field);
        control.markAsTouched({onlySelf: true});
      });
    }
  }

  promptToResendEmailVerification() {
    this.dialogService.confirm(
      this.translate.instant('signup.inactive-user-exists-title'),
      this.translate.instant('signup.inactive-user-exists-text'),
      this.translate.instant('action.cancel'),
      this.translate.instant('signup.resend')
    ).subscribe((result) => {
      if (result) {
        this.authService.resendEmailActivation(this.signup.get('email').value).subscribe(
          () => {
            this.router.navigateByUrl('/signup/emailVerification?email=' + this.signup.get('email').value);
          }
        );
      }
    });
  }

  validateSignUpRequest(): boolean {
    if (this.passwordCheck !== this.signup.get('password').value) {
      this.store.dispatch(new ActionNotificationShow({ message: this.translate.instant('login.passwords-mismatch-error'),
        type: 'error' }));
      return false;
    }
    if (this.signup.get('password').value.length < 6) {
      this.store.dispatch(new ActionNotificationShow({ message: this.translate.instant('signup.password-length-message'),
        type: 'error' }));
      return false;
    }
    if (!this.signup.get('recaptchaResponse').value || this.signup.get('recaptchaResponse').value.length < 1) {
      this.store.dispatch(new ActionNotificationShow({ message: this.translate.instant('signup.no-captcha-message'),
        type: 'error' }));
      return false;
    }
    if (!this.acceptPrivacyPolicy) {
      this.store.dispatch(new ActionNotificationShow({ message: 'You must accept our Privacy Policy',
        type: 'error' }));
      return false;
    }
    return true;
  }

  openPrivacyPolicy($event: Event) {
    if ($event) {
      $event.stopPropagation();
      $event.preventDefault();
    }
    this.dialog.open<PrivacyPolicyDialogComponent, any, boolean>
    (PrivacyPolicyDialogComponent, {
      disableClose: false,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
    }).afterClosed()
      .subscribe((res) => {
        if (res) {
          this.acceptPrivacyPolicy = true;
        }
      });
  }

}
