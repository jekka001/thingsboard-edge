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

import { Component, Inject, NgZone, OnDestroy, OnInit } from '@angular/core';
import { StateObject, StateParams } from '@core/api/widget-api.models';
import { ActivatedRoute, Router } from '@angular/router';
import { DashboardState } from '@shared/models/dashboard.models';
import { StateControllerState } from './state-controller.models';
import { StateControllerComponent } from './state-controller.component';
import { StatesControllerService } from '@home/components/dashboard-page/states/states-controller.service';
import { EntityId } from '@app/shared/models/id/entity-id';
import { UtilsService } from '@core/services/utils.service';
import { base64toObj, objToBase64URI } from '@app/core/utils';
import { DashboardUtilsService } from '@core/services/dashboard-utils.service';
import { EntityService } from '@core/http/entity.service';
import { WINDOW } from '@core/services/window.service';

// @dynamic
@Component({
  selector: 'tb-default-state-controller',
  templateUrl: './default-state-controller.component.html',
  styleUrls: ['./default-state-controller.component.scss']
})
export class DefaultStateControllerComponent extends StateControllerComponent implements OnInit, OnDestroy {

  constructor(protected router: Router,
              @Inject(WINDOW) protected window: Window,
              protected route: ActivatedRoute,
              protected ngZone: NgZone,
              protected statesControllerService: StatesControllerService,
              protected utils: UtilsService,
              private entityService: EntityService,
              private dashboardUtils: DashboardUtilsService) {
    super(router, route, utils, window, ngZone, statesControllerService);
  }

  ngOnInit(): void {
    super.ngOnInit();
  }

  ngOnDestroy(): void {
    super.ngOnDestroy();
  }

  protected init() {
    if (this.preservedState) {
      this.stateObject = this.preservedState;
      setTimeout(() => {
        this.gotoState(this.stateObject[0].id, true);
      }, 1);
    } else {
      const initialState = this.currentState;
      this.stateObject = this.parseState(initialState);
      setTimeout(() => {
        this.gotoState(this.stateObject[0].id, false);
      }, 1);
    }
  }

  protected onMobileChanged() {
  }

  protected onStateIdChanged() {
  }

  protected onStatesChanged() {
  }

  protected onStateChanged() {
    this.stateObject = this.parseState(this.currentState);
    this.gotoState(this.stateObject[0].id, false);
  }

  protected stateControllerId(): string {
    return 'default';
  }

  public getStateParams(): StateParams {
    if (this.stateObject && this.stateObject.length) {
      return this.stateObject[this.stateObject.length - 1].params;
    } else {
      return {};
    }
  }

  public openState(id: string, params?: StateParams, openRightLayout?: boolean): void {
    if (this.states && this.states[id]) {
      if (!params) {
        params = {};
      }
      const newState: StateObject = {
        id,
        params
      };
      this.stateObject[0] = newState;
      this.gotoState(this.stateObject[0].id, true, openRightLayout);
    }
  }

  public pushAndOpenState(states: Array<StateObject>, openRightLayout?: boolean): void {
    const state = states[states.length - 1];
    this.openState(state.id, state.params, openRightLayout);
  }

  public updateState(id: string, params?: StateParams, openRightLayout?: boolean): void {
    if (!id) {
      id = this.getStateId();
    }
    if (this.states && this.states[id]) {
      if (!params) {
        params = {};
      }
      const newState: StateObject = {
        id,
        params
      };
      this.stateObject[0] = newState;
      this.gotoState(this.stateObject[0].id, true, openRightLayout);
    }
  }

  public getEntityId(entityParamName: string): EntityId {
    return null;
  }

  public getStateId(): string {
    if (this.stateObject && this.stateObject.length) {
      return this.stateObject[this.stateObject.length - 1].id;
    } else {
      return '';
    }
  }

  public getStateIdAtIndex(index: number): string {
    if (this.stateObject && this.stateObject[index]) {
      return this.stateObject[index].id;
    } else {
      return '';
    }
  }

  public getStateIndex(): number {
    if (this.stateObject && this.stateObject.length) {
      return this.stateObject.length - 1;
    } else {
      return -1;
    }
  }

  public getStateParamsByStateId(stateId: string): StateParams {
    const stateObj = this.getStateObjById(stateId);
    if (stateObj) {
      return stateObj.params;
    } else {
      return null;
    }
  }

  public navigatePrevState(index: number): void {
    if (index < this.stateObject.length - 1) {
      this.stateObject.splice(index + 1, this.stateObject.length - index - 1);
      this.gotoState(this.stateObject[this.stateObject.length - 1].id, true);
    }
  }

  public resetState(): void {
    const rootStateId = this.dashboardUtils.getRootStateId(this.states);
    this.stateObject = [ { id: rootStateId, params: {} } ];
    this.gotoState(rootStateId, true);
  }

  public getStateName(id: string, state: DashboardState): string {
    return this.utils.customTranslation(state.name, id);
  }

  public displayStateSelection(): boolean {
    return this.states && Object.keys(this.states).length > 1;
  }

  public selectedStateIdChanged() {
    this.gotoState(this.stateObject[0].id, true);
  }

  private parseState(stateBase64: string): StateControllerState {
    let result: StateControllerState;
    if (stateBase64) {
      try {
        result = base64toObj(stateBase64);
      } catch (e) {
        result = [ { id: null, params: {} } ];
      }
    }
    if (!result) {
      result = [];
    }
    if (!result.length) {
      result[0] = { id: null, params: {} };
    } else if (result.length > 1) {
      const newResult = [];
      newResult.push(result[result.length - 1]);
      result = newResult;
    }
    const rootStateId = this.dashboardUtils.getRootStateId(this.states);
    if (!result[0].id) {
      result[0].id = rootStateId;
    }
    if (!this.states[result[0].id]) {
      result[0].id = rootStateId;
    }
    let i = result.length;
    while (i--) {
      if (!result[i].id || !this.states[result[i].id]) {
        result.splice(i, 1);
      }
    }
    return result;
  }

  private gotoState(stateId: string, update: boolean, openRightLayout?: boolean) {
    if (this.dashboardCtrl.dashboardCtx.state !== stateId) {
      this.dashboardCtrl.openDashboardState(stateId, openRightLayout);
      if (update) {
        this.updateLocation();
      }
    }
  }

  private updateLocation() {
    if (this.stateObject[0].id) {
      const newState = objToBase64URI(this.stateObject);
      this.updateStateParam(newState);
    }
  }

  private getStateObjById(id: string): StateObject {
    return this.stateObject.find((stateObj) => stateObj.id === id);
  }
}
