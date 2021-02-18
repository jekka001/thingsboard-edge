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

import { ActivationEnd, Router } from '@angular/router';
import { Injectable } from '@angular/core';
import { select, Store } from '@ngrx/store';
import { Actions, Effect, ofType } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';
import { merge } from 'rxjs';
import { distinctUntilChanged, filter, map, tap, withLatestFrom } from 'rxjs/operators';

import { SettingsActions, SettingsActionTypes, } from './settings.actions';
import { selectSettingsState } from './settings.selectors';
import { AppState } from '@app/core/core.state';
import { LocalStorageService } from '@app/core/local-storage/local-storage.service';
import { TitleService } from '@app/core/services/title.service';
import { updateUserLang } from '@app/core/settings/settings.utils';
import { AuthService } from '@core/auth/auth.service';
import { UtilsService } from '@core/services/utils.service';
import { getCurrentAuthState, getCurrentAuthUser } from '@core/auth/auth.selectors';
import { ActionAuthUpdateLastPublicDashboardId } from '../auth/auth.actions';
import { FaviconService } from '@core/services/favicon.service';
import { CustomTranslationService } from '@core/http/custom-translation.service';

export const SETTINGS_KEY = 'SETTINGS';

@Injectable()
export class SettingsEffects {
  constructor(
    private actions$: Actions<SettingsActions>,
    private store: Store<AppState>,
    private authService: AuthService,
    private utils: UtilsService,
    private router: Router,
    private localStorageService: LocalStorageService,
    private titleService: TitleService,
    private faviconService: FaviconService,
    private translate: TranslateService,
    private customTranslationService: CustomTranslationService
  ) {
  }

  @Effect({dispatch: false})
  persistSettings = this.actions$.pipe(
    ofType(
      SettingsActionTypes.CHANGE_LANGUAGE,
    ),
    withLatestFrom(this.store.pipe(select(selectSettingsState))),
    tap(([action, settings]) =>
      this.localStorageService.setItem(SETTINGS_KEY, settings)
    )
  );

  @Effect({dispatch: false})
  setTranslateServiceLanguage = this.store.pipe(
    select(selectSettingsState),
    map(settings => settings.userLang),
    distinctUntilChanged(),
    tap(userLang => {
      updateUserLang(this.translate, userLang).subscribe(() => {
        if (getCurrentAuthState(this.store).isAuthenticated) {
          this.customTranslationService.updateCustomTranslations();
        }
      })
    })
  );

  @Effect({dispatch: false})
  setTitle = merge(
    this.actions$.pipe(ofType(SettingsActionTypes.CHANGE_LANGUAGE, SettingsActionTypes.CHANGE_WHITE_LABELING)),
    this.router.events.pipe(filter(event => event instanceof ActivationEnd))
  ).pipe(
    tap(() => {
      this.titleService.setTitle(
        this.router.routerState.snapshot.root,
        this.translate
      );
    })
  );

  @Effect({dispatch: false})
  setFavicon = merge(
    this.actions$.pipe(ofType(SettingsActionTypes.CHANGE_WHITE_LABELING)),
  ).pipe(
    tap(() => {
      this.faviconService.setFavicon()
    })
  );

  @Effect({dispatch: false})
  setPublicId = merge(
    this.router.events.pipe(filter(event => event instanceof ActivationEnd))
  ).pipe(
    tap((event) => {
      const authUser = getCurrentAuthUser(this.store);
      const snapshot = (event as ActivationEnd).snapshot;
      if (authUser && authUser.isPublic && snapshot.url && snapshot.url.length
          && snapshot.url[0].path === 'dashboard') {
        this.utils.updateQueryParam('publicId', authUser.sub);
        this.store.dispatch(new ActionAuthUpdateLastPublicDashboardId(
          { lastPublicDashboardId: snapshot.params.dashboardId}));
      }
    })
  );
}
