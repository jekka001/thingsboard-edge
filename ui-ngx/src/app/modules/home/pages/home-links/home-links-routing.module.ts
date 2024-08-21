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

import { inject, NgModule } from '@angular/core';
import { ActivatedRouteSnapshot, ResolveFn, RouterModule, RouterStateSnapshot, Routes } from '@angular/router';

import { HomeLinksComponent } from './home-links.component';
import { Authority } from '@shared/models/authority.enum';
import { mergeMap, Observable, of } from 'rxjs';
import { HomeDashboard } from '@shared/models/dashboard.models';
import { DashboardService } from '@core/http/dashboard.service';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { catchError, first, map } from 'rxjs/operators';
import {
  getCurrentAuthUser,
  selectHomeDashboardParams,
  selectMobileQrEnabled,
  selectPersistDeviceStateToTelemetry
} from '@core/auth/auth.selectors';
import { EntityKeyType } from '@shared/models/query/query.models';
import { ResourcesService } from '@core/services/resources.service';
import { isDefinedAndNotNull } from '@core/utils';
import { MenuId } from '@core/services/menu.models';
import { MenuService } from '@core/services/menu.service';

const sysAdminHomePageJson = '/assets/dashboard/sys_admin_home_page.json';
const tenantAdminHomePageJson = '/assets/dashboard/tenant_admin_home_page.json';
// const customerUserHomePageJson = '/assets/dashboard/customer_user_home_page.json';

const getHomeDashboard = (store: Store<AppState>, resourcesService: ResourcesService) => {
  const authority = getCurrentAuthUser(store).authority;
  switch (authority) {
    case Authority.SYS_ADMIN:
      return applySystemParametersToHomeDashboard(store, resourcesService.loadJsonResource(sysAdminHomePageJson), authority);
    case Authority.TENANT_ADMIN:
      return applySystemParametersToHomeDashboard(store, resourcesService.loadJsonResource(tenantAdminHomePageJson), authority);
    // case Authority.CUSTOMER_USER:
    //   return applySystemParametersToHomeDashboard(store, resourcesService.loadJsonResource(customerUserHomePageJson), authority);
    default:
      return of(null);
  }
};

const applySystemParametersToHomeDashboard = (store: Store<AppState>,
                                              dashboard$: Observable<HomeDashboard>,
                                              authority: Authority): Observable<HomeDashboard> => {
  let selectParams$: Observable<{persistDeviceStateToTelemetry?: boolean; mobileQrEnabled?: boolean}>;
  switch (authority) {
    case Authority.SYS_ADMIN:
      selectParams$ = store.pipe(
        select(selectMobileQrEnabled),
        map(mobileQrEnabled => ({mobileQrEnabled}))
      );
      break;
    case Authority.TENANT_ADMIN:
      selectParams$ = store.pipe(select(selectHomeDashboardParams));
      break;
    case Authority.CUSTOMER_USER:
      selectParams$ = store.pipe(
        select(selectPersistDeviceStateToTelemetry),
        map(persistDeviceStateToTelemetry => ({persistDeviceStateToTelemetry}))
      );
      break;
  }
  return selectParams$.pipe(
    mergeMap((params) => dashboard$.pipe(
      map((dashboard) => {
        if (params.persistDeviceStateToTelemetry) {
          for (const filterId of Object.keys(dashboard.configuration.filters)) {
            if (['Active Devices', 'Inactive Devices'].includes(dashboard.configuration.filters[filterId].filter)) {
              dashboard.configuration.filters[filterId].keyFilters[0].key.type = EntityKeyType.TIME_SERIES;
            }
          }
        }
        if (isDefinedAndNotNull(params.mobileQrEnabled)) {
          for (const widgetId of Object.keys(dashboard.configuration.widgets)) {
            if (dashboard.configuration.widgets[widgetId].config.title === 'Select show mobile QR code') {
              dashboard.configuration.widgets[widgetId].config.settings.markdownTextFunction =
                (dashboard.configuration.widgets[widgetId].config.settings.markdownTextFunction as string)
                  .replace(/\${mobileQrEnabled:([^}]+)}/, `\${mobileQrEnabled:${String(params.mobileQrEnabled)}}`);
              break;
            }
          }
        }
        dashboard.hideDashboardToolbar = true;
        return dashboard;
      })
    ))
  );
};

const resolveHomeDashboard = (dashboardService: DashboardService,
                              resourcesService: ResourcesService,
                              store: Store<AppState>): Observable<HomeDashboard> =>
  dashboardService.getHomeDashboard().pipe(
    mergeMap((dashboard) => {
      if (!dashboard) {
        return getHomeDashboard(store, resourcesService);
      }
      return of(dashboard);
    })
  );

export const homeDashboardResolver: ResolveFn<HomeDashboard> = (
  _route: ActivatedRouteSnapshot,
  _state: RouterStateSnapshot,
  menuService = inject(MenuService),
  dashboardService = inject(DashboardService),
  resourcesService = inject(ResourcesService),
  store: Store<AppState> = inject(Store<AppState>)
): Observable<HomeDashboard> =>
  menuService.menuSections().pipe(first()).pipe(
    mergeMap((sections) => {
      const homeSection = sections.find(s => s.id === MenuId.home);
      if (homeSection?.homeDashboardId) {
        return dashboardService.getDashboard(homeSection.homeDashboardId, {ignoreErrors: true}).pipe(
          map((dashboard) => ({
              ...dashboard,
              hideDashboardToolbar: homeSection.homeHideDashboardToolbar
          })),
          catchError(() => resolveHomeDashboard(dashboardService, resourcesService, store))
        );
      } else {
        return resolveHomeDashboard(dashboardService, resourcesService, store);
      }
    })
  );

const routes: Routes = [
  {
    path: 'home',
    component: HomeLinksComponent,
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      title: 'home.home',
      breadcrumb: {
        menuId: MenuId.home
      }
    },
    resolve: {
      homeDashboard: homeDashboardResolver
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class HomeLinksRoutingModule { }
