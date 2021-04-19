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

import { Observable, of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { UtilsService } from '@core/services/utils.service';
import {
  EntityGroupStateConfigFactory,
  EntityGroupStateInfo,
  GroupEntityTableConfig
} from '@home/models/group/group-entities-table-config.models';
import { Injectable } from '@angular/core';
import { EntityType } from '@shared/models/entity-type.models';
import { tap } from 'rxjs/operators';
import { BroadcastService } from '@core/services/broadcast.service';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { MatDialog } from '@angular/material/dialog';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { EntityGroupParams, ShortEntityView } from '@shared/models/entity-group.models';
import { HomeDialogsService } from '@home/dialogs/home-dialogs.service';
import { CustomerId } from '@shared/models/id/customer-id';
import { GroupConfigTableConfigService } from '@home/components/group/group-config-table-config.service';
import { Operation, Resource } from '@shared/models/security.models';
import { Edge } from "@shared/models/edge.models";
import { EdgeService } from "@core/http/edge.service";
import { EdgeComponent } from "@home/pages/edge/edge.component";
import { Router } from "@angular/router";
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';

@Injectable()
export class EdgeGroupConfigFactory implements EntityGroupStateConfigFactory<Edge> {

  constructor(private groupConfigTableConfigService: GroupConfigTableConfigService<Edge>,
              private userPermissionsService: UserPermissionsService,
              private store: Store<AppState>,
              private translate: TranslateService,
              private utils: UtilsService,
              private dialog: MatDialog,
              private homeDialogs: HomeDialogsService,
              private edgeService: EdgeService,
              private broadcast: BroadcastService,
              private router: Router) {
  }

  createConfig(params: EntityGroupParams, entityGroup: EntityGroupStateInfo<Edge>): Observable<GroupEntityTableConfig<Edge>> {
    const config = new GroupEntityTableConfig<Edge>(entityGroup, params);
    let ownerId = this.userPermissionsService.getUserOwnerId();
    const manageRuleChainsEnabled = this.userPermissionsService.hasGenericPermission(Resource.EDGE, Operation.WRITE) && ownerId.entityType !== EntityType.CUSTOMER;

    config.entityComponent = EdgeComponent;

    config.entityTitle = (edge) => edge ?
      this.utils.customTranslation(edge.name, edge.name) : '';

    config.deleteEntityTitle = edge => this.translate.instant('edge.delete-edge-title', { edgeName: edge.name });
    config.deleteEntityContent = () => this.translate.instant('edge.delete-edge-text');
    config.deleteEntitiesTitle = count => this.translate.instant('edge.delete-edges-title', {count});
    config.deleteEntitiesContent = () => this.translate.instant('edge.delete-edges-text');

    config.loadEntity = id => this.edgeService.getEdge(id.id);
    config.saveEntity = edge => {
      return this.edgeService.saveEdge(edge).pipe(
        tap(() => {
          this.broadcast.broadcast('edgeSaved');
        }));
    };
    config.deleteEntity = id => this.edgeService.deleteEdge(id.id);

    config.onEntityAction = action => this.onEdgeAction(action, config, params);

    if (params.hierarchyView) {
      config.entityAdded = edge => {
        params.hierarchyCallbacks.edgeAdded(params.nodeId, edge);
      };
      config.entityUpdated = edge => {
        params.hierarchyCallbacks.edgeUpdated(edge);
      };
      config.entitiesDeleted = edgeIds => {
        params.hierarchyCallbacks.edgesDeleted(edgeIds.map(id => id.id));
      };
    }

    if (this.userPermissionsService.hasGroupEntityPermission(Operation.CREATE, config.entityGroup)) {
      config.headerActionDescriptors.push(
        {
          name: this.translate.instant('edge.import'),
          icon: 'file_upload',
          isEnabled: () => true,
          onAction: ($event) => this.importEdges($event, config)
        }
      );
    }

    if (this.userPermissionsService.hasGenericPermission(Resource.EDGE, Operation.READ)) {
      config.cellActionDescriptors.push(
        {
          name: this.translate.instant('edge.manage-edge-user-groups'),
          icon: 'account_circle',
          isEnabled: config.manageUsersEnabled,
          onAction: ($event, entity) => this.manageUsers($event, entity, config, params)
        },
        {
          name: this.translate.instant('edge.manage-edge-asset-groups'),
          icon: 'domain',
          isEnabled: config.manageAssetsEnabled,
          onAction: ($event, entity) => this.manageAssets($event, entity, config, params)
        },
        {
          name: this.translate.instant('edge.manage-edge-device-groups'),
          icon: 'devices_other',
          isEnabled: config.manageDevicesEnabled,
          onAction: ($event, entity) => this.manageDevices($event, entity, config, params)
        },
        {
          name: this.translate.instant('edge.manage-edge-entity-view-groups'),
          icon: 'view_quilt',
          isEnabled: config.manageEntityViewsEnabled,
          onAction: ($event, entity) => this.manageEntityViews($event, entity, config, params)
        },
        {
          name: this.translate.instant('edge.manage-edge-dashboard-groups'),
          icon: 'dashboard',
          isEnabled: config.manageDashboardsEnabled,
          onAction: ($event, entity) => this.manageDashboards($event, entity, config, params)
        },
        {
          name: this.translate.instant('edge.manage-edge-scheduler-events'),
          icon: 'schedule',
          isEnabled: config.manageSchedulerEventsEnabled,
          onAction: ($event, entity) => this.manageSchedulerEvents($event, entity, config, params)
        }
      );
      if (manageRuleChainsEnabled) {
        config.cellActionDescriptors.push(
          {
            name: this.translate.instant('edge.manage-edge-rule-chains'),
            icon: 'settings_ethernet',
            isEnabled: () => true,
            onAction: ($event, entity) => this.manageRuleChains($event, entity, config, params)
          }
        );
      }
    }

    return of(this.groupConfigTableConfigService.prepareConfiguration(params, config));
  }

  importEdges($event: Event, config: GroupEntityTableConfig<Edge>) {
    const entityGroup = config.entityGroup;
    const entityGroupId = !entityGroup.groupAll ? entityGroup.id.id : null;
    let customerId: CustomerId = null;
    if (entityGroup.ownerId.entityType === EntityType.CUSTOMER) {
      customerId = entityGroup.ownerId as CustomerId;
    }
    this.homeDialogs.importEntities(customerId, EntityType.EDGE, entityGroupId).subscribe((res) => {
      if (res) {
        this.broadcast.broadcast('edgeSaved');
        config.table.updateData();
      }
    });
  }

  onEdgeAction(action: EntityAction<Edge>, config: GroupEntityTableConfig<Edge>, params: EntityGroupParams): boolean {
    switch (action.action) {
      case 'manageUsers':
        this.manageUsers(action.event, action.entity, config, params);
        return true;
      case 'manageAssets':
        this.manageAssets(action.event, action.entity, config, params);
        return true;
      case 'manageDevices':
        this.manageDevices(action.event, action.entity, config, params);
        return true;
      case 'manageEntityViews':
        this.manageEntityViews(action.event, action.entity, config, params);
        return true;
      case 'manageDashboards':
        this.manageDashboards(action.event, action.entity, config, params);
        return true;
      case 'manageSchedulerEvents':
        this.manageSchedulerEvents(action.event, action.entity, config, params);
        return true;
      case 'manageRuleChains':
        this.manageRuleChains(action.event, action.entity, config, params);
        return true;
    }
    return false;
  }

  manageUsers($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
               params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.USER);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/userGroups`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/userGroups`);
    }
  }

  manageAssets($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
               params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.ASSET);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/assetGroups`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/assetGroups`);
    }
  }

  manageDevices($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
                params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.DEVICE);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/deviceGroups`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/deviceGroups`);
    }
  }

  manageEntityViews($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
                    params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.ENTITY_VIEW);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/entityViewGroups`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/entityViewGroups`);
    }
  }

  manageDashboards($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
                   params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.DASHBOARD);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/dashboardGroups`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/dashboardGroups`);
    }
  }

  manageSchedulerEvents($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
                        params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.SCHEDULER_EVENT);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/scheduler`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/scheduler`);
    }
  }

  manageRuleChains($event: Event, edge: Edge | ShortEntityView, config: GroupEntityTableConfig<Edge>,
                   params: EntityGroupParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (params.hierarchyView) {
      params.hierarchyCallbacks.edgeGroupsSelected(params.nodeId, edge.id.id, EntityType.RULE_CHAIN);
    } else if (this.isCustomerScope(params)) {
      this.router.navigateByUrl(`customerGroups/${params.entityGroupId}/${params.customerId}/edgeGroups/${params.childEntityGroupId}/${edge.id.id}/ruleChains`);
    } else {
      this.router.navigateByUrl(`edgeGroups/${config.entityGroup.id.id}/${edge.id.id}/ruleChains`);
    }
  }

  private isCustomerScope(params: EntityGroupParams): boolean {
    return params.childGroupScope === 'customer';
  }

}
