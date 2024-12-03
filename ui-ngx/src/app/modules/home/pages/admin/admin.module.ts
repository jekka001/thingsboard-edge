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

import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { AdminRoutingModule } from './admin-routing.module';
import { SharedModule } from '@app/shared/shared.module';
import { MailServerComponent } from '@modules/home/pages/admin/mail-server.component';
import { GeneralSettingsComponent } from '@modules/home/pages/admin/general-settings.component';
import { SecuritySettingsComponent } from '@modules/home/pages/admin/security-settings.component';
import { HomeComponentsModule } from '@modules/home/components/home-components.module';
import { MailTemplatesComponent } from '@home/pages/admin/mail-templates.component';
import { WhiteLabelingComponent } from '@home/pages/admin/white-labeling.component';
import { PaletteComponent } from '@home/pages/admin/palette.component';
import { PaletteDialogComponent } from '@home/pages/admin/palette-dialog.component';
import { CustomCssDialogComponent } from '@home/pages/admin/custom-css-dialog.component';
import { SelfRegistrationComponent } from '@home/pages/admin/self-registration.component';
import { SmsProviderComponent } from '@home/pages/admin/sms-provider.component';
import { SendTestSmsDialogComponent } from '@home/pages/admin/send-test-sms-dialog.component';
import { HomeSettingsComponent } from '@home/pages/admin/home-settings.component';
import { ResourcesLibraryComponent } from '@home/pages/admin/resource/resources-library.component';
import { ResourcesTableHeaderComponent } from '@home/pages/admin/resource/resources-table-header.component';
import { QueueComponent } from '@home/pages/admin/queue/queue.component';
import { RepositoryAdminSettingsComponent } from '@home/pages/admin/repository-admin-settings.component';
import { AutoCommitAdminSettingsComponent } from '@home/pages/admin/auto-commit-admin-settings.component';
import { TwoFactorAuthSettingsComponent } from '@home/pages/admin/two-factor-auth-settings.component';
import { OAuth2Module } from '@home/pages/admin/oauth2/oauth2.module';
import { JsLibraryTableHeaderComponent } from '@home/pages/admin/resource/js-library-table-header.component';
import { JsResourceComponent } from '@home/pages/admin/resource/js-resource.component';
import { NgxFlowModule } from '@flowjs/ngx-flow';

@NgModule({
  declarations:
    [
      GeneralSettingsComponent,
      MailServerComponent,
      MailTemplatesComponent,
      SmsProviderComponent,
      SendTestSmsDialogComponent,
      WhiteLabelingComponent,
      SecuritySettingsComponent,
      PaletteComponent,
      PaletteDialogComponent,
      CustomCssDialogComponent,
      SelfRegistrationComponent,
      SecuritySettingsComponent,
      HomeSettingsComponent,
      ResourcesLibraryComponent,
      ResourcesTableHeaderComponent,
      JsResourceComponent,
      JsLibraryTableHeaderComponent,
      QueueComponent,
      RepositoryAdminSettingsComponent,
      AutoCommitAdminSettingsComponent,
      TwoFactorAuthSettingsComponent
    ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    AdminRoutingModule,
    OAuth2Module,
    NgxFlowModule
  ]
})
export class AdminModule { }
