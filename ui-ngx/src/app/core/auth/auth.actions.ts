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

import { Action } from '@ngrx/store';
import { AuthUser, User } from '@shared/models/user.model';
import { AuthPayload } from '@core/auth/auth.models';
import { UserSettings } from '@shared/models/user-settings.models';

export enum AuthActionTypes {
  AUTHENTICATED = '[Auth] Authenticated',
  UNAUTHENTICATED = '[Auth] Unauthenticated',
  LOAD_USER = '[Auth] Load User',
  UPDATE_USER_DETAILS = '[Auth] Update User Details',
  UPDATE_AUTH_USER = '[Auth] Update Auth User',
  UPDATE_LAST_PUBLIC_DASHBOARD_ID = '[Auth] Update Last Public Dashboard Id',
  UPDATE_HAS_REPOSITORY = '[Auth] Change Has Repository',
  UPDATE_OPENED_MENU_SECTION = '[Preferences] Update Opened Menu Section',
  PUT_USER_SETTINGS = '[Preferences] Put user settings',
  DELETE_USER_SETTINGS = '[Preferences] Delete user settings',
}

export class ActionAuthAuthenticated implements Action {
  readonly type = AuthActionTypes.AUTHENTICATED;

  constructor(readonly payload: AuthPayload) {}
}

export class ActionAuthUnauthenticated implements Action {
  readonly type = AuthActionTypes.UNAUTHENTICATED;
}

export class ActionAuthLoadUser implements Action {
  readonly type = AuthActionTypes.LOAD_USER;

  constructor(readonly payload: { isUserLoaded: boolean }) {}
}

export class ActionAuthUpdateUserDetails implements Action {
  readonly type = AuthActionTypes.UPDATE_USER_DETAILS;

  constructor(readonly payload: { userDetails: User }) {}
}

export class ActionAuthUpdateAuthUser implements Action {
  readonly type = AuthActionTypes.UPDATE_AUTH_USER;

  constructor(readonly payload: Partial<AuthUser>) {}
}

export class ActionAuthUpdateLastPublicDashboardId implements Action {
  readonly type = AuthActionTypes.UPDATE_LAST_PUBLIC_DASHBOARD_ID;

  constructor(readonly payload: { lastPublicDashboardId: string }) {}
}

export class ActionAuthUpdateHasRepository implements Action {
  readonly type = AuthActionTypes.UPDATE_HAS_REPOSITORY;

  constructor(readonly payload: { hasRepository: boolean }) {}
}

export class ActionPreferencesUpdateOpenedMenuSection implements Action {
  readonly type = AuthActionTypes.UPDATE_OPENED_MENU_SECTION;

  constructor(readonly payload: { path: string; opened: boolean }) {}
}

export class ActionPreferencesPutUserSettings implements Action {
  readonly type = AuthActionTypes.PUT_USER_SETTINGS;

  constructor(readonly payload: Partial<UserSettings>) {}
}

export class ActionPreferencesDeleteUserSettings implements Action {
  readonly type = AuthActionTypes.DELETE_USER_SETTINGS;

  constructor(readonly payload: Array<NestedKeyOf<UserSettings>>) {}
}

export type AuthActions = ActionAuthAuthenticated | ActionAuthUnauthenticated |
  ActionAuthLoadUser | ActionAuthUpdateUserDetails | ActionAuthUpdateLastPublicDashboardId | ActionAuthUpdateHasRepository |
  ActionPreferencesUpdateOpenedMenuSection | ActionPreferencesPutUserSettings | ActionPreferencesDeleteUserSettings |
  ActionAuthUpdateAuthUser;
