///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import { NgModule } from '@angular/core';
import { ActivatedRouteSnapshot, Route, RouterModule, Routes } from '@angular/router';

import { EntitiesTableComponent } from '../../components/entity/entities-table.component';
import { Authority } from '@shared/models/authority.enum';
import { EntityViewsTableConfigResolver } from '@modules/home/pages/entity-view/entity-views-table-config.resolver';
import { EntityDetailsPageComponent } from '@home/components/entity/entity-details-page.component';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { entityDetailsPageBreadcrumbLabelFunction } from '@home/pages/home-pages.models';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';
import { EntityType } from '@shared/models/entity-type.models';
import { EntityGroupResolver, groupEntitiesLabelFunction } from '@home/pages/group/entity-group.shared';
import { EntityGroupsTableConfigResolver } from '@home/components/group/entity-groups-table-config.resolver';
import { GroupEntitiesTableComponent } from '@home/components/group/group-entities-table.component';
import { RouterTabsComponent } from '@home/components/router-tabs.component';
import { AssetsTableConfigResolver } from '@home/pages/asset/assets-table-config.resolver';
import { CustomerTitleResolver } from '@home/pages/customer/customer.shared';
import { entityGroupsTitle } from '@shared/models/entity-group.models';
import { MenuId } from '@core/services/menu.models';

const entityViewRoute = (entityGroup: any, entitiesTableConfig: any): Route =>
  ({
    path: ':entityId',
    component: EntityDetailsPageComponent,
    canDeactivate: [ConfirmOnExitGuard],
    data: {
      groupType: EntityType.ENTITY_VIEW,
      breadcrumb: {
        labelFunction: entityDetailsPageBreadcrumbLabelFunction,
        icon: 'view_quilt'
      } as BreadCrumbConfig<EntityDetailsPageComponent>,
      auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      title: 'entity-view.entity-view',
      hideTabs: true
    },
    resolve: {
      entityGroup,
      entitiesTableConfig
    }
  });

const entityViewGroupsChildrenRoutesTemplate = (shared: boolean): Routes => [
  {
    path: '',
    component: EntitiesTableComponent,
    data: {
      auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      title: entityGroupsTitle(EntityType.ENTITY_VIEW, shared),
      groupType: EntityType.ENTITY_VIEW
    },
    resolve: {
      entityGroup: EntityGroupResolver,
      entitiesTableConfig: EntityGroupsTableConfigResolver
    }
  },
  {
    path: ':entityGroupId',
    data: {
      groupType: EntityType.ENTITY_VIEW,
      breadcrumb: {
        icon: 'view_quilt',
        labelFunction: groupEntitiesLabelFunction
      } as BreadCrumbConfig<GroupEntitiesTableComponent>
    },
    children: [
      {
        path: '',
        component: GroupEntitiesTableComponent,
        data: {
          auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          title: 'entity-group.entity-view-group',
          groupType: EntityType.ENTITY_VIEW,
          backNavigationCommands: ['../']
        },
        resolve: {
          entityGroup: EntityGroupResolver
        }
      },
      entityViewRoute(EntityGroupResolver, 'emptyEntityViewTableConfigResolver')
    ]
  }
];

export const entityViewGroupsRoute: Route = {
  path: 'groups',
  data: {
    groupType: EntityType.ENTITY_VIEW,
    breadcrumb: {
      menuId: MenuId.entity_view_groups
    }
  },
  children: entityViewGroupsChildrenRoutesTemplate(false)
};

const entityViewSharedGroupsRoute: Route = {
  path: 'shared',
  data: {
    groupType: EntityType.ENTITY_VIEW,
    shared: true,
    breadcrumb: {
      menuId: MenuId.entity_view_shared
    }
  },
  children: entityViewGroupsChildrenRoutesTemplate(true)
};

export const entityViewsRoute = (root = false): Route => {
  const routeConfig: Route = {
    path: 'entityViews',
    component: RouterTabsComponent,
    data: {
      auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      breadcrumb: {
        labelFunction: (route, translate) =>
          (route.data.customerTitle ? (route.data.customerTitle + ': ') : '') + translate.instant('entity-view.entity-views'),
        icon: 'view_quilt'
      }
    },
    resolve: {
      customerTitle: CustomerTitleResolver
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          redirectTo: 'all'
        }
      },
      {
        path: 'all',
        data: {
          groupType: EntityType.ENTITY_VIEW,
          auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          breadcrumb: {
            menuId: MenuId.entity_view_all
          }
        },
        children: [
          {
            path: '',
            component: EntitiesTableComponent,
            data: {
              auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
              title: 'entity-view.entity-views'
            },
            resolve: {
              entitiesTableConfig: EntityViewsTableConfigResolver,
              entityGroup: EntityGroupResolver
            }
          },
          entityViewRoute(EntityGroupResolver, EntityViewsTableConfigResolver)
        ]
      },
      entityViewGroupsRoute
    ]
  };
  if (root) {
    routeConfig.children.push(entityViewSharedGroupsRoute);
  }
  return routeConfig;
};

const routes: Routes = [
  {
    path: 'entityViews',
    pathMatch: 'full',
    redirectTo: '/entities/entityViews'
  },
  {
    path: 'entityViews/all',
    pathMatch: 'full',
    redirectTo: '/entities/entityViews/all'
  },
  {
    path: 'entityViews/:entityId',
    redirectTo: '/entities/entityViews/all/:entityId'
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    EntityViewsTableConfigResolver,
    {
      provide: 'emptyEntityViewTableConfigResolver',
      useValue: (route: ActivatedRouteSnapshot) => null
    }
  ]
})
export class EntityViewRoutingModule { }
