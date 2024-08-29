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
import { SharedModule } from '@shared/shared.module';
import { HomeComponentsModule } from '@home/components/home-components.module';
import { CustomMenuRoutingModule } from '@home/pages/custom-menu/custom-menu-routing.module';
import { CustomMenuTableHeaderComponent } from '@home/pages/custom-menu/custom-menu-table-header.component';
import { CustomMenuConfigComponent } from '@home/pages/custom-menu/custom-menu-config.component';
import { CustomMenuTableComponent } from '@home/pages/custom-menu/custom-menu-table.component';
import { ManageCustomMenuDialogComponent } from '@home/pages/custom-menu/manage-custom-menu-dialog.component';
import { EditCustomMenuNamePanelComponent } from '@home/pages/custom-menu/edit-custom-menu-name-panel.component';
import { CustomMenuIsAssignedDialogComponent } from '@home/pages/custom-menu/custom-menu-is-assigned.dialog.component';
import { CustomMenuItemRowComponent } from '@home/pages/custom-menu/custom-menu-item-row.component';
import { DefaultMenuItemPanelComponent } from '@home/pages/custom-menu/default-menu-item-panel.component';
import { CustomMenuItemComponent } from '@home/pages/custom-menu/custom-menu-item.component';
import { AddCustomMenuItemDialogComponent } from '@home/pages/custom-menu/add-custom-menu-item.dialog.component';
import { CustomMenuItemPanelComponent } from '@home/pages/custom-menu/custom-menu-item-panel.component';

@NgModule({
  declarations: [
    CustomMenuTableHeaderComponent,
    CustomMenuTableComponent,
    ManageCustomMenuDialogComponent,
    CustomMenuIsAssignedDialogComponent,
    EditCustomMenuNamePanelComponent,
    CustomMenuConfigComponent,
    CustomMenuItemRowComponent,
    DefaultMenuItemPanelComponent,
    CustomMenuItemComponent,
    AddCustomMenuItemDialogComponent,
    CustomMenuItemPanelComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    CustomMenuRoutingModule
  ]
})
export class CustomMenuModule { }
