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

import { Component, Inject, OnInit, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import { UtilsService } from '@core/services/utils.service';
import { Dashboard, DashboardLayoutId } from '@app/shared/models/dashboard.models';
import { objToBase64 } from '@core/utils';
import { DashboardUtilsService } from '@core/services/dashboard-utils.service';
import { EntityId } from '@app/shared/models/id/entity-id';
import { Widget } from '@app/shared/models/widget.models';
import { DashboardService } from '@core/http/dashboard.service';
import { forkJoin, Observable, of } from 'rxjs';
import { SelectTargetLayoutDialogComponent } from '@home/components/dashboard/select-target-layout-dialog.component';
import {
  SelectTargetStateDialogComponent,
  SelectTargetStateDialogData
} from '@home/components/dashboard/select-target-state-dialog.component';
import { mergeMap } from 'rxjs/operators';
import { AliasesInfo } from '@shared/models/alias.models';
import { ItemBufferService } from '@core/services/item-buffer.service';
import { StateObject } from '@core/api/widget-api.models';

export interface AddWidgetToDashboardDialogData {
  entityId: EntityId;
  entityName: string;
  widget: Widget;
}

@Component({
  selector: 'tb-add-widget-to-dashboard-dialog',
  templateUrl: './add-widget-to-dashboard-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: AddWidgetToDashboardDialogComponent}],
  styleUrls: ['./add-widget-to-dashboard-dialog.component.scss']
})
export class AddWidgetToDashboardDialogComponent extends
  DialogComponent<AddWidgetToDashboardDialogComponent, void>
  implements OnInit, ErrorStateMatcher {

  addWidgetFormGroup: FormGroup;

  submitted = false;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: AddWidgetToDashboardDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<AddWidgetToDashboardDialogComponent, void>,
              private fb: FormBuilder,
              private utils: UtilsService,
              private dashboardUtils: DashboardUtilsService,
              private dashboardService: DashboardService,
              private itembuffer: ItemBufferService,
              private dialog: MatDialog) {
    super(store, router, dialogRef);

    this.addWidgetFormGroup = this.fb.group(
      {
        addToDashboardType: [0, []],
        dashboardId: [null, [Validators.required]],
        newDashboardTitle: [{value: null, disabled: true}, []],
        openDashboard: [false, []]
      }
    );

    this.addWidgetFormGroup.get('addToDashboardType').valueChanges.subscribe(
      (addToDashboardType: number) => {
        if (addToDashboardType === 0) {
          this.addWidgetFormGroup.get('dashboardId').setValidators([Validators.required]);
          this.addWidgetFormGroup.get('dashboardId').enable();
          this.addWidgetFormGroup.get('newDashboardTitle').setValidators([]);
          this.addWidgetFormGroup.get('newDashboardTitle').disable();
          this.addWidgetFormGroup.get('dashboardId').updateValueAndValidity();
          this.addWidgetFormGroup.get('newDashboardTitle').updateValueAndValidity();
        } else {
          this.addWidgetFormGroup.get('dashboardId').setValidators([]);
          this.addWidgetFormGroup.get('dashboardId').disable();
          this.addWidgetFormGroup.get('newDashboardTitle').setValidators([Validators.required]);
          this.addWidgetFormGroup.get('newDashboardTitle').enable();
          this.addWidgetFormGroup.get('dashboardId').updateValueAndValidity();
          this.addWidgetFormGroup.get('newDashboardTitle').updateValueAndValidity();
        }
      }
    );
  }

  ngOnInit(): void {
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  add(): void {
    this.submitted = true;
    const addToDashboardType: number = this.addWidgetFormGroup.get('addToDashboardType').value;
    if (addToDashboardType === 0) {
      const dashboardId: string = this.addWidgetFormGroup.get('dashboardId').value;
      this.dashboardService.getDashboard(dashboardId).pipe(
        mergeMap((dashboard) => {
          dashboard = this.dashboardUtils.validateAndUpdateDashboard(dashboard);
          return this.selectTargetState(dashboard).pipe(
            mergeMap((targetState) => {
              return forkJoin([of(dashboard), of(targetState), this.selectTargetLayout(dashboard, targetState)]);
            })
          );
        })
      ).subscribe((res) => {
        this.addWidgetToDashboard(res[0], res[1], res[2]);
      });
    } else {
      const dashboardTitle: string = this.addWidgetFormGroup.get('newDashboardTitle').value;
      const newDashboard: Dashboard = {
        title: dashboardTitle
      };
      this.addWidgetToDashboard(newDashboard, 'default', 'main');
    }
  }

  private selectTargetState(dashboard: Dashboard): Observable<string> {
    const states = dashboard.configuration.states;
    const stateIds = Object.keys(states);
    if (stateIds.length > 1) {
      return this.dialog.open<SelectTargetStateDialogComponent, SelectTargetStateDialogData,
        string>(SelectTargetStateDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          states
        }
      }).afterClosed();
    } else {
      return of(stateIds[0]);
    }
  }

  private selectTargetLayout(dashboard: Dashboard, targetState: string): Observable<DashboardLayoutId> {
    const layouts = dashboard.configuration.states[targetState].layouts;
    const layoutIds = Object.keys(layouts);
    if (layoutIds.length > 1) {
      return this.dialog.open<SelectTargetLayoutDialogComponent, any,
        DashboardLayoutId>(SelectTargetLayoutDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog']
      }).afterClosed();
    } else {
      return of(layoutIds[0] as DashboardLayoutId);
    }
  }

  private addWidgetToDashboard(dashboard: Dashboard, targetState: string, targetLayout: DashboardLayoutId) {
    const aliasesInfo: AliasesInfo = {
      datasourceAliases: {},
      targetDeviceAliases: {}
    };
    this.dashboardUtils.createSingleEntityFilter(this.data.entityId).subscribe((filter) => {
      aliasesInfo.datasourceAliases[0] = {
        alias: this.data.entityName,
        filter
      };
      this.itembuffer.addWidgetToDashboard(dashboard, targetState,
        targetLayout, this.data.widget, aliasesInfo, null,
        48, null, -1, -1).pipe(
        mergeMap((theDashboard) => {
          return this.dashboardService.saveDashboard(theDashboard);
        })
      ).subscribe(
        (theDashboard) => {
          const openDashboard: boolean = this.addWidgetFormGroup.get('openDashboard').value;
          this.dialogRef.close();
          if (openDashboard) {
            let url;
            const stateIds = Object.keys(dashboard.configuration.states);
            const stateIndex = stateIds.indexOf(targetState);
            if (stateIndex > 0) {
              const stateObject: StateObject = {
                id: targetState,
                params: {}
              };
              const state = objToBase64([ stateObject ]);
              url = `/dashboards/${theDashboard.id.id}?state=${state}&edit=true`;
            } else {
              url = `/dashboards/${theDashboard.id.id}?edit=true`;
            }
            this.router.navigateByUrl(url);
          }
        }
      );
    });
  }
}
