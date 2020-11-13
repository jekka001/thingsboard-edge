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

import { Inject, Injectable, NgModule, Optional } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  CanActivate,
  Resolve,
  Router,
  RouterModule,
  RouterStateSnapshot,
  Routes,
  UrlTree
} from '@angular/router';

import { EntitiesTableComponent } from '../../components/entity/entities-table.component';
import { Authority } from '@shared/models/authority.enum';
import { RuleChainsTableConfigResolver } from '@modules/home/pages/rulechain/rulechains-table-config.resolver';
import { Observable } from 'rxjs';
import { BreadCrumbConfig, BreadCrumbLabelFunction } from '@shared/components/breadcrumb';
import { ResolvedRuleChainMetaData, RuleChain } from '@shared/models/rule-chain.models';
import { RuleChainService } from '@core/http/rule-chain.service';
import { RuleChainPageComponent } from '@home/pages/rulechain/rulechain-page.component';
import { RuleNodeComponentDescriptor } from '@shared/models/rule-node.models';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { ItemBufferService } from '@core/public-api';
import { MODULES_MAP } from '@shared/public-api';

@Injectable()
export class RuleChainResolver implements Resolve<RuleChain> {

  constructor(private ruleChainService: RuleChainService) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<RuleChain> {
    const ruleChainId = route.params.ruleChainId;
    return this.ruleChainService.getRuleChain(ruleChainId);
  }
}

@Injectable()
export class ResolvedRuleChainMetaDataResolver implements Resolve<ResolvedRuleChainMetaData> {

  constructor(private ruleChainService: RuleChainService) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<ResolvedRuleChainMetaData> {
    const ruleChainId = route.params.ruleChainId;
    return this.ruleChainService.getResolvedRuleChainMetadata(ruleChainId);
  }
}

@Injectable()
export class RuleNodeComponentsResolver implements Resolve<Array<RuleNodeComponentDescriptor>> {

  constructor(private ruleChainService: RuleChainService,
              @Optional() @Inject(MODULES_MAP) private modulesMap: {[key: string]: any}) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<Array<RuleNodeComponentDescriptor>> {
    return this.ruleChainService.getRuleNodeComponents(this.modulesMap);
  }
}

@Injectable()
export class RuleChainImportGuard implements CanActivate {

  constructor(private itembuffer: ItemBufferService,
              private router: Router) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot):
    Observable<boolean | UrlTree> | Promise<boolean | UrlTree> | boolean | UrlTree {
    if (this.itembuffer.hasRuleChainImport()) {
      return true;
    } else {
      return this.router.parseUrl('ruleChains');
    }
  }

}

export const ruleChainBreadcumbLabelFunction: BreadCrumbLabelFunction<RuleChainPageComponent>
  = ((route, translate, component) => {
  let label: string = component.ruleChain.name;
  if (component.ruleChain.root) {
    label += ` (${translate.instant('rulechain.root')})`;
  }
  return label;
});

export const importRuleChainBreadcumbLabelFunction: BreadCrumbLabelFunction<RuleChainPageComponent> =
  ((route, translate, component) => {
  return `${translate.instant('rulechain.import')}: ${component.ruleChain.name}`;
});

const routes: Routes = [
  {
    path: 'ruleChains',
    data: {
      breadcrumb: {
        label: 'rulechain.rulechains',
        icon: 'settings_ethernet'
      }
    },
    children: [
      {
        path: '',
        component: EntitiesTableComponent,
        data: {
          auth: [Authority.TENANT_ADMIN],
          title: 'rulechain.rulechains'
        },
        resolve: {
          entitiesTableConfig: RuleChainsTableConfigResolver
        }
      },
      {
        path: ':ruleChainId',
        component: RuleChainPageComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          breadcrumb: {
            labelFunction: ruleChainBreadcumbLabelFunction,
            icon: 'settings_ethernet'
          } as BreadCrumbConfig<RuleChainPageComponent>,
          auth: [Authority.TENANT_ADMIN],
          title: 'rulechain.rulechain',
          import: false
        },
        resolve: {
          ruleChain: RuleChainResolver,
          ruleChainMetaData: ResolvedRuleChainMetaDataResolver,
          ruleNodeComponents: RuleNodeComponentsResolver
        }
      },
      {
        path: 'ruleChain/import',
        component: RuleChainPageComponent,
        canActivate: [RuleChainImportGuard],
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          breadcrumb: {
            labelFunction: importRuleChainBreadcumbLabelFunction,
            icon: 'settings_ethernet'
          } as BreadCrumbConfig<RuleChainPageComponent>,
          auth: [Authority.TENANT_ADMIN],
          title: 'rulechain.rulechain',
          import: true
        },
        resolve: {
          ruleNodeComponents: RuleNodeComponentsResolver
        }
      }
    ]
  }
];

// @dynamic
@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    RuleChainsTableConfigResolver,
    RuleChainResolver,
    ResolvedRuleChainMetaDataResolver,
    RuleNodeComponentsResolver,
    RuleChainImportGuard
  ]
})
export class RuleChainRoutingModule { }
