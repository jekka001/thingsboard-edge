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

import { ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Dashboard } from '@shared/models/dashboard.models';
import { StateObject } from '@core/api/widget-api.models';
import { updateEntityParams, WidgetContext } from '@home/models/widget-component.models';
import { deepClone, objToBase64 } from '@core/utils';
import { IDashboardComponent } from '@home/models/dashboard-component.models';
import { EntityId } from '@shared/models/id/entity-id';
import { Subscription } from 'rxjs';

@Component({
  selector: 'tb-dashboard-state',
  templateUrl: './dashboard-state.component.html',
  styleUrls: []
})
export class DashboardStateComponent extends PageComponent implements OnInit, OnDestroy {

  @Input()
  ctx: WidgetContext;

  @Input()
  stateId: string;

  @Input()
  syncParentStateParams = false;

  @Input()
  entityParamName: string;

  @Input()
  entityId: EntityId;

  currentState: string;

  dashboard: Dashboard;

  parentDashboard: IDashboardComponent;

  private stateSubscription: Subscription;

  constructor(protected store: Store<AppState>,
              private cd: ChangeDetectorRef) {
    super(store);
  }

  ngOnInit(): void {
    this.dashboard = deepClone(this.ctx.stateController.dashboardCtrl.dashboardCtx.getDashboard());
    this.updateCurrentState();
    this.parentDashboard = this.ctx.parentDashboard ?
      this.ctx.parentDashboard : this.ctx.dashboard;
    if (this.syncParentStateParams) {
      this.stateSubscription = this.ctx.stateController.stateChanged().subscribe(() => {
        this.updateCurrentState();
        this.cd.markForCheck();
      });
    }
  }

  ngOnDestroy(): void {
    if (this.stateSubscription) {
      this.stateSubscription.unsubscribe();
    }
  }

  private updateCurrentState(): void {
    const stateObject: StateObject = {};
    const params = deepClone(this.ctx.stateController.getStateParams());
    updateEntityParams(params, this.entityParamName, this.entityId);
    stateObject.params = params;
    if (this.stateId) {
      stateObject.id = this.stateId;
    }
    this.currentState = objToBase64([stateObject]);
  }
}
