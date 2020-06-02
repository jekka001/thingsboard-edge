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

import { Component, Inject, NgZone, OnDestroy, OnInit } from '@angular/core';
import { StateObject, StateParams } from '@core/api/widget-api.models';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { StateControllerState } from './state-controller.models';
import { StateControllerComponent } from './state-controller.component';
import { StatesControllerService } from '@home/pages/dashboard/states/states-controller.service';
import { EntityId } from '@app/shared/models/id/entity-id';
import { UtilsService } from '@core/services/utils.service';
import { base64toObj, insertVariable, isEmpty, objToBase64 } from '@app/core/utils';
import { DashboardUtilsService } from '@core/services/dashboard-utils.service';
import { EntityService } from '@core/http/entity.service';
import { EntityType } from '@shared/models/entity-type.models';
import { map, tap } from 'rxjs/operators';
import { WINDOW } from '@core/services/window.service';
import { EntityGroupInfo } from '@shared/models/entity-group.models';

// @dynamic
@Component({
  selector: 'tb-entity-state-controller',
  templateUrl: './entity-state-controller.component.html',
  styleUrls: ['./entity-state-controller.component.scss']
})
export class EntityStateControllerComponent extends StateControllerComponent implements OnInit, OnDestroy {

  selectedStateIndex = -1;

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
      this.selectedStateIndex = this.stateObject.length - 1;
      setTimeout(() => {
        this.gotoState(this.stateObject[this.stateObject.length - 1].id, true);
      }, 1);
    } else {
      const initialState = this.currentState;
      this.stateObject = this.parseState(initialState);
      this.selectedStateIndex = this.stateObject.length - 1;
      setTimeout(() => {
        this.gotoState(this.stateObject[this.stateObject.length - 1].id, false);
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
    this.selectedStateIndex = this.stateObject.length - 1;
    this.gotoState(this.stateObject[this.stateObject.length - 1].id, false);
  }

  protected stateControllerId(): string {
    return 'entity';
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
      this.resolveEntity(params).subscribe(
        () => {
          const newState: StateObject = {
            id,
            params
          };
          this.stateObject.push(newState);
          this.selectedStateIndex = this.stateObject.length - 1;
          this.gotoState(this.stateObject[this.stateObject.length - 1].id, true, openRightLayout);
        }
      );
    }
  }

  public updateState(id: string, params?: StateParams, openRightLayout?: boolean): void {
    if (!id) {
      id = this.getStateId();
    }
    if (this.states && this.states[id]) {
      this.resolveEntity(params).subscribe(
        () => {
          this.stateObject[this.stateObject.length - 1] = {
            id,
            params
          };
          this.gotoState(this.stateObject[this.stateObject.length - 1].id, true, openRightLayout);
        }
      );
    }
  }

  public getEntityId(entityParamName: string): EntityId {
    const stateParams = this.getStateParams();
    if (!entityParamName || !entityParamName.length) {
      return stateParams.entityId;
    } else if (stateParams[entityParamName]) {
      return stateParams[entityParamName].entityId;
    }
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
      this.selectedStateIndex = this.stateObject.length - 1;
      this.gotoState(this.stateObject[this.stateObject.length - 1].id, true);
    }
  }

  public resetState(): void {
    const rootStateId = this.dashboardUtils.getRootStateId(this.states);
    this.stateObject = [ { id: rootStateId, params: {} } ];
    this.gotoState(rootStateId, true);
  }

  public getStateName(index: number): string {
    let result = '';
    const state = this.stateObject[index];
    if (state) {
      const dashboardState = this.states[state.id];
      if (dashboardState) {
        let stateName = dashboardState.name;
        stateName = this.utils.customTranslation(stateName, stateName);
        const params = this.stateObject[index].params;
        const entityName = params && params.entityName ? params.entityName : '';
        const entityLabel = params && params.entityLabel ? params.entityLabel : '';
        result = insertVariable(stateName, 'entityName', entityName);
        result = insertVariable(result, 'entityLabel', entityLabel);
        for (const prop of Object.keys(params)) {
          if (params[prop] && params[prop].entityName) {
            result = insertVariable(result, prop + ':entityName', params[prop].entityName);
          }
          if (params[prop] && params[prop].entityLabel) {
            result = insertVariable(result, prop + ':entityLabel', params[prop].entityLabel);
          }
        }
      }
    }
    return result;
  }

  public selectedStateIndexChanged() {
    this.navigatePrevState(this.selectedStateIndex);
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
    this.dashboardCtrl.openDashboardState(stateId, openRightLayout);
    if (update) {
      this.updateLocation();
    }
  }

  private updateLocation() {
    if (this.stateObject[this.stateObject.length - 1].id) {
      let newState;
      if (this.isDefaultState()) {
        newState = null;
      } else {
        newState = objToBase64(this.stateObject);
      }
      this.updateStateParam(newState);
    }
  }

  private isDefaultState(): boolean {
    if (this.stateObject.length === 1) {
      const state = this.stateObject[0];
      const rootStateId = this.dashboardUtils.getRootStateId(this.states);
      if (state.id === rootStateId && (!state.params || isEmpty(state.params))) {
        return true;
      }
    }
    return false;
  }

  private resolveEntity(params: StateParams): Observable<void> {
    if (params && params.targetEntityParamName) {
      params = params[params.targetEntityParamName];
    }
    if (params && params.entityId && params.entityId.id && params.entityId.entityType) {
      if (this.isEntityResolved(params)) {
        return of(null);
      } else {
        return this.entityService.getEntity(params.entityId.entityType as EntityType,
          params.entityId.id, {ignoreLoading: true, ignoreErrors: true}).pipe(
            tap((entity) => {
              params.entityName = entity.name;
              params.entityLabel = entity.label;
              if (params.entityId.entityType === EntityType.ENTITY_GROUP) {
                params.entityGroupType = (entity as EntityGroupInfo).type;
              }
            }),
          map(() => null)
        );
      }
    } else {
      return of(null);
    }
  }

  private isEntityResolved(params: StateParams): boolean {
    if (params.entityId && params.entityId.entityType === EntityType.ENTITY_GROUP && !params.entityGroupType) {
      return false;
    }
    return !(!params.entityName || !params.entityName.length);
  }

  private getStateObjById(id: string): StateObject {
    return this.stateObject.find((stateObj) => stateObj.id === id);
  }
}
