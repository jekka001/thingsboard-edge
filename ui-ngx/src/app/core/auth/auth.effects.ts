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

import { Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UserSettingsService } from '@core/http/user-settings.service';
import { mergeMap, withLatestFrom } from 'rxjs/operators';
import { AuthActions, AuthActionTypes } from '@core/auth/auth.actions';
import { selectAuthState } from '@core/auth/auth.selectors';

@Injectable()
export class AuthEffects {
  constructor(
    private actions$: Actions<AuthActions>,
    private store: Store<AppState>,
    private userSettingsService: UserSettingsService
  ) {
  }

  persistOpenedMenuSections = createEffect(() => this.actions$.pipe(
    ofType(
      AuthActionTypes.UPDATE_OPENED_MENU_SECTION,
    ),
    withLatestFrom(this.store.pipe(select(selectAuthState))),
    mergeMap(([action, state]) => this.userSettingsService.putUserSettings({ openedMenuSections: state.userSettings.openedMenuSections }))
  ), {dispatch: false});

  putUserSettings = createEffect(() => this.actions$.pipe(
    ofType(
      AuthActionTypes.PUT_USER_SETTINGS,
    ),
    mergeMap((state) => this.userSettingsService.putUserSettings(state.payload))
  ), {dispatch: false});

  deleteUserSettings = createEffect(() => this.actions$.pipe(
    ofType(
      AuthActionTypes.DELETE_USER_SETTINGS,
    ),
    mergeMap((state) => this.userSettingsService.deleteUserSettings(state.payload))
  ), {dispatch: false});
}
