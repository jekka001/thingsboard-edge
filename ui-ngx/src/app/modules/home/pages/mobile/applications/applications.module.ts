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
import { MobileAppComponent } from '@home/pages/mobile/applications/mobile-app.component';
import { MobileAppTableHeaderComponent } from '@home/pages/mobile/applications/mobile-app-table-header.component';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';
import { ApplicationsRoutingModule } from '@home/pages/mobile/applications/applications-routing.module';
import { MobileAppDialogComponent } from '@home/pages/mobile/applications/mobile-app-dialog.component';
import { RemoveAppDialogComponent } from '@home/pages/mobile/applications/remove-app-dialog.component';
import {
  MobileAppConfigurationDialogComponent
} from '@home/pages/mobile/applications/mobile-app-configuration-dialog.component';
import { CommonMobileModule } from '@home/pages/mobile/common/common-mobile.module';

@NgModule({
  declarations: [
    MobileAppComponent,
    MobileAppTableHeaderComponent,
    MobileAppDialogComponent,
    RemoveAppDialogComponent,
    MobileAppConfigurationDialogComponent,
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    CommonMobileModule,
    ApplicationsRoutingModule,
  ],
  exports: [
    MobileAppDialogComponent,
  ]
})
export class MobileApplicationModule { }
