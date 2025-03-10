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

import { Component, Inject, OnInit, SkipSelf, ViewChild } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  FormGroupDirective,
  NgForm,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@shared/components/dialog.component';
import { DataKey, DataKeyConfigMode, Widget, widgetType } from '@shared/models/widget.models';
import { DataKeyConfigComponent } from './data-key-config.component';
import { Dashboard } from '@shared/models/dashboard.models';
import { IAliasController } from '@core/api/widget-api.models';
import { ToggleHeaderOption } from '@shared/components/toggle-header.component';
import { TranslateService } from '@ngx-translate/core';
import { WidgetConfigCallbacks } from '@home/components/widget/config/widget-config.component.models';
import { FormProperty } from '@shared/models/dynamic-form.models';

export interface DataKeyConfigDialogData {
  dataKey: DataKey;
  dataKeyConfigMode?: DataKeyConfigMode;
  dataKeySettingsForm: FormProperty[];
  dataKeySettingsDirective: string;
  dashboard: Dashboard;
  aliasController: IAliasController;
  widget: Widget;
  widgetType: widgetType;
  deviceId?: string;
  entityAliasId?: string;
  showPostProcessing?: boolean;
  callbacks?: WidgetConfigCallbacks;
  hideDataKeyName?: boolean;
  hideDataKeyLabel?: boolean;
  hideDataKeyColor?: boolean;
  hideDataKeyUnits?: boolean;
  hideDataKeyDecimals?: boolean;
}

@Component({
  selector: 'tb-data-key-config-dialog',
  templateUrl: './data-key-config-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: DataKeyConfigDialogComponent}],
  styleUrls: ['./data-key-config-dialog.component.scss']
})
export class DataKeyConfigDialogComponent extends DialogComponent<DataKeyConfigDialogComponent, DataKey>
  implements OnInit, ErrorStateMatcher {

  @ViewChild('dataKeyConfig', {static: true}) dataKeyConfig: DataKeyConfigComponent;

  hasAdvanced = false;

  dataKeyConfigHeaderOptions: ToggleHeaderOption[];

  dataKeyConfigMode: DataKeyConfigMode = DataKeyConfigMode.general;

  dataKeyFormGroup: UntypedFormGroup;

  submitted = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: DataKeyConfigDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<DataKeyConfigDialogComponent, DataKey>,
              private translate: TranslateService,
              public fb: UntypedFormBuilder) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.dataKeyFormGroup = this.fb.group({
      dataKey: [this.data.dataKey, [Validators.required]]
    });
    if (this.data.dataKeySettingsForm?.length ||
      this.data.dataKeySettingsDirective?.length) {
      this.hasAdvanced = true;
      this.dataKeyConfigHeaderOptions = [
        {
          name: this.translate.instant('datakey.general'),
          value: DataKeyConfigMode.general
        },
        {
          name: this.translate.instant('datakey.advanced'),
          value: DataKeyConfigMode.advanced
        }
      ];
      if (this.data.dataKeyConfigMode) {
        this.dataKeyConfigMode = this.data.dataKeyConfigMode;
      }
    }
  }

  isErrorState(control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    this.dataKeyConfig.validateOnSubmit().subscribe(() => {
      if (this.dataKeyFormGroup.valid) {
        const dataKey: DataKey = this.dataKeyFormGroup.get('dataKey').value;
        this.dialogRef.close(dataKey);
      }
    });
  }
}
