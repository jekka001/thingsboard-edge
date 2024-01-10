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

import { createFeatureSelector, createSelector, select, Store } from '@ngrx/store';

import { AppState } from '../core.state';
import { AuthState } from './auth.models';
import { take } from 'rxjs/operators';
import { AuthUser } from '@shared/models/user.model';
import { UserSettings } from '@shared/models/user-settings.models';
import { getDescendantProp } from '@core/utils';

export const selectAuthState = createFeatureSelector< AuthState>(
  'auth'
);

export const selectAuth = createSelector(
  selectAuthState,
  (state: AuthState) => state
);

export const selectIsAuthenticated = createSelector(
  selectAuthState,
  (state: AuthState) => state.isAuthenticated
);

export const selectIsUserLoaded = createSelector(
  selectAuthState,
  (state: AuthState) => state.isUserLoaded
);

export const selectAuthUser = createSelector(
  selectAuthState,
  (state: AuthState) => state.authUser
);

export const selectUserDetails = createSelector(
  selectAuthState,
  (state: AuthState) => state.userDetails
);

export const selectUserTokenAccessEnabled = createSelector(
  selectAuthState,
  (state: AuthState) => state.userTokenAccessEnabled
);

export const selectHasRepository = createSelector(
  selectAuthState,
  (state: AuthState) => state.hasRepository
);

export const selectTbelEnabled = createSelector(
  selectAuthState,
  (state: AuthState) => state.tbelEnabled
);

export const selectPersistDeviceStateToTelemetry = createSelector(
  selectAuthState,
  (state: AuthState) => state.persistDeviceStateToTelemetry
);

export const selectUserSettings = createSelector(
  selectAuthState,
  (state: AuthState) => state.userSettings
);

export const selectUserSettingsProperty = (path: NestedKeyOf<UserSettings>) => createSelector(
  selectAuthState,
  (state: AuthState) => getDescendantProp(state.userSettings, path)
);

export const selectOpenedMenuSections = createSelector(
  selectAuthState,
  (state: AuthState) => state.userSettings.openedMenuSections
);


export const getCurrentAuthState = (store: Store<AppState>): AuthState => {
  let state: AuthState;
  store.pipe(select(selectAuth), take(1)).subscribe(
    val => state = val
  );
  return state;
};

export const getCurrentAuthUser = (store: Store<AppState>): AuthUser => {
  let authUser: AuthUser;
  store.pipe(select(selectAuthUser), take(1)).subscribe(
    val => authUser = val
  );
  return authUser;
};

export const getCurrentUserSettings = (store: Store<AppState>): UserSettings => {
  let userSettings: UserSettings;
  store.pipe(select(selectUserSettings), take(1)).subscribe(
    val => userSettings = val
  );
  return userSettings;
};

export const getCurrentOpenedMenuSections = (store: Store<AppState>): string[] => {
  let openedMenuSections: string[];
  store.pipe(select(selectOpenedMenuSections), take(1)).subscribe(
    val => openedMenuSections = val
  );
  return openedMenuSections;
};
