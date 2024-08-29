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

import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { CMItemLinkType, CMItemType, CMScope, CustomMenuItem } from '@shared/models/custom-menu.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { UntypedFormBuilder, UntypedFormControl } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

export interface AddCustomMenuItemDialogData {
  scope: CMScope;
  subItem: boolean;
}

@Component({
  selector: 'tb-add-custom-menu-item-dialog',
  templateUrl: './add-custom-menu-item-dialog.component.html',
  styleUrls: ['./add-custom-menu-item-dialog.component.scss']
})
export class AddCustomMenuItemDialogComponent
  extends DialogComponent<AddCustomMenuItemDialogComponent, CustomMenuItem> implements OnInit, OnDestroy {

  title: string;

  customMenuItemControl: UntypedFormControl;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: AddCustomMenuItemDialogData,
              public dialogRef: MatDialogRef<AddCustomMenuItemDialogComponent, CustomMenuItem>,
              private translate: TranslateService,
              private fb: UntypedFormBuilder) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    const menuItem: CustomMenuItem = {
      visible: true,
      icon: 'star',
      name: '',
      menuItemType: CMItemType.LINK,
      linkType: CMItemLinkType.URL,
      url: '',
      setAccessToken: true,
      dashboardId: null,
      hideDashboardToolbar: true
    };
    this.customMenuItemControl = this.fb.control(menuItem);
    this.title = this.data.subItem ? 'custom-menu.add-custom-menu-subitem' : 'custom-menu.add-custom-menu-item';
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  submit() {
    if (this.customMenuItemControl.valid) {
      const menuItem: CustomMenuItem = this.customMenuItemControl.value;
      this.dialogRef.close(menuItem);
    }
  }
}
