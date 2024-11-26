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

import { Injectable, NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { RouterTabsComponent } from '@home/components/router-tabs.component';
import { Authority } from '@shared/models/authority.enum';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { OAuth2Service } from '@core/http/oauth2.service';
import { Observable } from 'rxjs';
import { ClientsTableConfigResolver } from './clients/clients-table-config.resolver';
import { DomainTableConfigResolver } from '@home/pages/admin/oauth2/domains/domain-table-config.resolver';
import { EntityDetailsPageComponent } from '@home/components/entity/entity-details-page.component';
import { entityDetailsPageBreadcrumbLabelFunction } from '@home/pages/home-pages.models';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';
import { MenuId } from '@core/services/menu.models';

@Injectable()
export class OAuth2LoginProcessingUrlResolver  {

  constructor(private oauth2Service: OAuth2Service) {
  }

  resolve(): Observable<string> {
    return this.oauth2Service.getLoginProcessingUrl();
  }
}

export const oAuth2Routes: Routes = [
  {
    path: 'oauth2',
    component: RouterTabsComponent,
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
      breadcrumb: {
        label: 'admin.oauth2.oauth2',
        icon: 'mdi:shield-account'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
          redirectTo: {
            SYS_ADMIN: '/security-settings/oauth2/domains',
            TENANT_ADMIN: '/security-settings/oauth2/clients'
          }
        }
      },
      {
        path: 'domains',
        component: EntitiesTableComponent,
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'admin.oauth2.domains',
          breadcrumb: {
            menuId: MenuId.domains
          }
        },
        resolve: {
          entitiesTableConfig: DomainTableConfigResolver
        }
      },
      {
        path: 'clients',
        data: {
          title: 'admin.oauth2.clients',
          breadcrumb: {
            menuId:MenuId.clients
          }
        },
        children: [
          {
            path: '',
            component: EntitiesTableComponent,
            data: {
              auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
              title: 'admin.oauth2.clients'
            },
            resolve: {
              entitiesTableConfig: ClientsTableConfigResolver
            }
          },
          {
            path: 'details',
            children: [
              {
                path: ':entityId',
                component: EntityDetailsPageComponent,
                data: {
                  breadcrumb: {
                    labelFunction: entityDetailsPageBreadcrumbLabelFunction,
                    icon: 'public'
                  } as BreadCrumbConfig<EntityDetailsPageComponent>,
                  auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
                  title: 'admin.oauth2.clients',
                  hideTabs: true,
                  backNavigationCommands: ['../..']
                },
                resolve: {
                  entitiesTableConfig: ClientsTableConfigResolver
                }
              }
            ]
          }
        ]
      }
    ]
  }
];

@NgModule({
  providers: [
    OAuth2LoginProcessingUrlResolver,
    ClientsTableConfigResolver,
    DomainTableConfigResolver
  ],
  imports: [RouterModule.forChild(oAuth2Routes)],
  exports: [RouterModule]
})
export class Oauth2RoutingModule {
}
