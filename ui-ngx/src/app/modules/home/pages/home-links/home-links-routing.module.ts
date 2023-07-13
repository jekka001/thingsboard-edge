///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

import { Injectable, NgModule } from '@angular/core';
import { Resolve, RouterModule, Routes } from '@angular/router';

import { HomeLinksComponent } from './home-links.component';
import { Authority } from '@shared/models/authority.enum';
import { mergeMap, Observable, of } from 'rxjs';
import { HomeDashboard } from '@shared/models/dashboard.models';
import { DashboardService } from '@core/http/dashboard.service';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { map } from 'rxjs/operators';
import { getCurrentAuthUser, selectPersistDeviceStateToTelemetry } from '@core/auth/auth.selectors';
import { EntityKeyType } from '@shared/models/query/query.models';
import { ResourcesService } from '@core/services/resources.service';

const sysAdminHomePageJson = '/assets/dashboard/sys_admin_home_page.json';
const tenantAdminHomePageJson = '/assets/dashboard/tenant_admin_home_page.json';
// const customerUserHomePageJson = '/assets/dashboard/customer_user_home_page.json';

@Injectable()
export class HomeDashboardResolver implements Resolve<HomeDashboard> {

  constructor(private dashboardService: DashboardService,
              private resourcesService: ResourcesService,
              private store: Store<AppState>) {
  }

  resolve(): Observable<HomeDashboard> {
    return this.dashboardService.getHomeDashboard().pipe(
      mergeMap((dashboard) => {
        if (!dashboard) {
          let dashboard$: Observable<HomeDashboard>;
          const authority = getCurrentAuthUser(this.store).authority;
          switch (authority) {
            case Authority.SYS_ADMIN:
              dashboard$ = this.resourcesService.loadJsonResource(sysAdminHomePageJson);
              break;
            case Authority.TENANT_ADMIN:
              dashboard$ = this.updateDeviceActivityKeyFilterIfNeeded(this.resourcesService.loadJsonResource(tenantAdminHomePageJson));
              break;
            case Authority.CUSTOMER_USER:
              // dashboard$ = this.updateDeviceActivityKeyFilterIfNeeded(this.resourcesService.loadJsonResource(customerUserHomePageJson));
              break;
          }
          if (dashboard$) {
            return dashboard$.pipe(
              map((homeDashboard) => {
                homeDashboard.hideDashboardToolbar = true;
                return homeDashboard;
              })
            );
          }
        }
        return of(dashboard);
      })
    );
  }

  private updateDeviceActivityKeyFilterIfNeeded(dashboard$: Observable<HomeDashboard>): Observable<HomeDashboard> {
    return this.store.pipe(select(selectPersistDeviceStateToTelemetry)).pipe(
      mergeMap((persistToTelemetry) => dashboard$.pipe(
          map((dashboard) => {
            if (persistToTelemetry) {
              for (const filterId of Object.keys(dashboard.configuration.filters)) {
                if (['Active Devices', 'Inactive Devices'].includes(dashboard.configuration.filters[filterId].filter)) {
                  dashboard.configuration.filters[filterId].keyFilters[0].key.type = EntityKeyType.TIME_SERIES;
                }
              }
            }
            return dashboard;
          })
        ))
    );
  }
}

const routes: Routes = [
  {
    path: 'home',
    component: HomeLinksComponent,
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      title: 'home.home',
      breadcrumb: {
        label: 'home.home',
        icon: 'home'
      }
    },
    resolve: {
      homeDashboard: HomeDashboardResolver
    }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    HomeDashboardResolver
  ]
})
export class HomeLinksRoutingModule { }
