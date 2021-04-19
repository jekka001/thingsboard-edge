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

import { Injectable } from '@angular/core';

import { ActivatedRouteSnapshot, Resolve, Router } from '@angular/router';
import {
  CellActionDescriptor,
  checkBoxCell,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor,
  HeaderActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { forkJoin, Observable, of } from 'rxjs';
import { select, Store } from '@ngrx/store';
import { selectAuthUser } from '@core/auth/auth.selectors';
import { map, mergeMap, take, tap } from 'rxjs/operators';
import { AppState } from '@core/core.state';
import { Authority } from '@app/shared/models/authority.enum';
import { CustomerService } from '@core/http/customer.service';
import { Customer } from '@app/shared/models/customer.model';
import { NULL_UUID } from '@shared/models/id/has-uuid';
import { BroadcastService } from '@core/services/broadcast.service';
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from '@core/services/dialog.service';
import {
  AssignToCustomerDialogComponent,
  AssignToCustomerDialogData
} from '@modules/home/dialogs/assign-to-customer-dialog.component';
import {
  AddEntitiesToCustomerDialogComponent,
  AddEntitiesToCustomerDialogData
} from '../../dialogs/add-entities-to-customer-dialog.component';
import { HomeDialogsService } from '@home/dialogs/home-dialogs.service';
import { Edge, EdgeInfo } from '@shared/models/edge.models';
import { EdgeService } from '@core/http/edge.service';
import { EdgeComponent } from '@home/pages/edge/edge.component';
import { EdgeTableHeaderComponent } from '@home/pages/edge/edge-table-header.component';
import { EdgeId } from '@shared/models/id/edge-id';
import { EdgeTabsComponent } from '@home/pages/edge/edge-tabs.component';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { Edge } from "@shared/models/edge.models";
import { EdgeService } from '@core/http/edge.service';
import { EdgeTableHeaderComponent } from "@home/pages/edge/edge-table-header.component";
import { EdgeId } from "@shared/models/id/edge-id";
import { EdgeTabsComponent } from "@home/pages/edge/edge-tabs.component";
import { UtilsService } from "@core/services/utils.service";

@Injectable()
export class EdgesTableConfigResolver implements Resolve<EntityTableConfig<Edge>> {

  private readonly config: EntityTableConfig<Edge> = new EntityTableConfig<Edge>();

  private customerId: string;

  constructor(private store: Store<AppState>,
              private broadcast: BroadcastService,
              private edgeService: EdgeService,
              private customerService: CustomerService,
              private dialogService: DialogService,
              private homeDialogs: HomeDialogsService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private utils: UtilsService,
              private router: Router,
              private dialog: MatDialog) {

    this.config.entityType = EntityType.EDGE;
    // this.config.entityComponent = EdgeComponent;
    this.config.entityTabsComponent = EdgeTabsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.EDGE);
    this.config.entityResources = entityTypeResources.get(EntityType.EDGE);

    this.config.entityTitle = (edge) => edge ?
      this.utils.customTranslation(edge.name, edge.name) : '';

    this.config.deleteEntityTitle = edge => this.translate.instant('edge.delete-edge-title', {edgeName: edge.name});
    this.config.deleteEntityContent = () => this.translate.instant('edge.delete-edge-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('edge.delete-edges-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('edge.delete-edges-text');

    this.config.loadEntity = id => this.edgeService.getEdge(id.id);
    this.config.saveEntity = edge => {
      return this.edgeService.saveEdge(edge).pipe(
        tap(() => {
          this.broadcast.broadcast('edgeSaved');
        }),
        mergeMap((savedEdge) => this.edgeService.getEdge(savedEdge.id.id)
        ));
    };
    this.config.onEntityAction = action => this.onEdgeAction(action);
    this.config.detailsReadonly = () => this.config.componentsData.edgeScope === 'customer_user';
    this.config.headerComponent = EdgeTableHeaderComponent;
  }

  resolve(route: ActivatedRouteSnapshot): Observable<EntityTableConfig<Edge>> {
    const routeParams = route.params;
    this.config.componentsData = {
      edgeScope: route.data.edgesType,
      edgeType: ''
    };
    this.customerId = routeParams.customerId;
    return this.store.pipe(select(selectAuthUser), take(1)).pipe(
      tap((authUser) => {
        if (authUser.authority === Authority.CUSTOMER_USER) {
          this.config.componentsData.edgeScope = 'customer_user';
          this.customerId = authUser.customerId;
        }
      }),
      mergeMap(() =>
        this.customerId ? this.customerService.getCustomer(this.customerId) : of(null as Customer)
      ),
      map((parentCustomer) => {
        if (parentCustomer) {
          if (parentCustomer.additionalInfo && parentCustomer.additionalInfo.isPublic) {
            this.config.tableTitle = this.translate.instant('customer.public-edges');
          } else {
            this.config.tableTitle = parentCustomer.title + ': ' + this.translate.instant('edge.edge-instances');
          }
        } else {
          this.config.tableTitle = this.translate.instant('edge.edge-instances');
        }
        this.config.columns = this.configureColumns(this.config.componentsData.edgeScope);
        this.configureEntityFunctions(this.config.componentsData.edgeScope);
        this.config.cellActionDescriptors = this.configureCellActions(this.config.componentsData.edgeScope);
        this.config.groupActionDescriptors = this.configureGroupActions(this.config.componentsData.edgeScope);
        this.config.addActionDescriptors = this.configureAddActions(this.config.componentsData.edgeScope);
        this.config.addEnabled = this.config.componentsData.edgeScope !== 'customer_user';
        this.config.entitiesDeleteEnabled = this.config.componentsData.edgeScope === 'tenant';
        this.config.deleteEnabled = () => this.config.componentsData.edgeScope === 'tenant';
        return this.config;
      })
    );
  }

  configureColumns(edgeScope: string): Array<EntityTableColumn<Edge>> {
    const columns: Array<EntityTableColumn<Edge>> = [
      new DateEntityTableColumn<Edge>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<Edge>('name', 'edge.name', '25%', this.config.entityTitle),
      new EntityTableColumn<Edge>('type', 'edge.edge-type', '25%'),
      new EntityTableColumn<Edge>('label', 'edge.label', '25%')
    ];
    // if (edgeScope === 'tenant') {
    //   columns.push(
    //     new EntityTableColumn<EdgeInfo>('customerTitle', 'customer.customer', '25%'),
    //     new EntityTableColumn<EdgeInfo>('customerIsPublic', 'edge.public', '60px',
    //       entity => {
    //         return checkBoxCell(entity.customerIsPublic);
    //       }, () => ({}), false)
    //   );
    // }
    return columns;
  }

  configureEntityFunctions(edgeScope: string): void {
    if (edgeScope === 'tenant') {
      this.config.entitiesFetchFunction = pageLink =>
        this.edgeService.getTenantEdgeInfos(pageLink, this.config.componentsData.edgeType);
      this.config.deleteEntity = id => this.edgeService.deleteEdge(id.id);
    }
    if (edgeScope === 'customer') {
      this.config.entitiesFetchFunction = pageLink =>
        this.edgeService.getCustomerEdgeInfos(this.customerId, pageLink, this.config.componentsData.edgeType);
      this.config.deleteEntity = id => this.edgeService.unassignEdgeFromCustomer(id.id);
    }
    if (edgeScope === 'customer_user') {
      this.config.entitiesFetchFunction = pageLink =>
        this.edgeService.getCustomerEdgeInfos(this.customerId, pageLink, this.config.componentsData.edgeType);
      this.config.deleteEntity = id => this.edgeService.unassignEdgeFromCustomer(id.id);
    }
  }

  configureCellActions(edgeScope: string): Array<CellActionDescriptor<Edge>> {
    const actions: Array<CellActionDescriptor<Edge>> = [];
    if (edgeScope === 'tenant') {
      actions.push(
        // {
        //   name: this.translate.instant('edge.make-public'),
        //   icon: 'share',
        //   isEnabled: (entity) => (!entity.customerId || entity.customerId.id === NULL_UUID),
        //   onAction: ($event, entity) => this.makePublic()
        // },
        // {
        //   name: this.translate.instant('edge.assign-to-customer'),
        //   icon: 'assignment_ind',
        //   isEnabled: (entity) => (!entity.customerId || entity.customerId.id === NULL_UUID),
        //   onAction: ($event, entity) => this.assignToCustomer($event, [entity.id])
        // },
        // {
        //   name: this.translate.instant('edge.unassign-from-customer'),
        //   icon: 'assignment_return',
        //   isEnabled: (entity) => (entity.customerId && entity.customerId.id !== NULL_UUID && !entity.customerIsPublic),
        //   onAction: ($event, entity) => this.unassignFromCustomer($event, entity)
        // },
        // {
        //   name: this.translate.instant('edge.make-private'),
        //   icon: 'reply',
        //   isEnabled: (entity) => (entity.customerId && entity.customerId.id !== NULL_UUID && entity.customerIsPublic),
        //   onAction: ($event, entity) => this.unassignFromCustomer($event, entity)
        // },
        {
          name: this.translate.instant('edge.manage-edge-users'),
          nameFunction: (edge) => {
            return edge.additionalInfo && edge.additionalInfo.isPublic
              ? this.translate.instant('edge.manage-public-users')
              : this.translate.instant('edge.manage-edge-users');
          },
          icon: 'account_circle',
          isEnabled: (entity) => true,
          onAction: ($event, entity) => this.manageEdgeUsers($event, entity)
        },
        {
          name: this.translate.instant('edge.manage-edge-assets'),
          nameFunction: (edge) => {
            return edge.additionalInfo && edge.additionalInfo.isPublic
              ? this.translate.instant('edge.manage-public-assets')
              : this.translate.instant('edge.manage-edge-assets');
          },
          icon: 'domain',
          isEnabled: (entity) => true,
          onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.ASSET)
        },
        {
          name: this.translate.instant('edge.manage-edge-devices'),
          nameFunction: (edge) => {
            return edge.additionalInfo && edge.additionalInfo.isPublic
              ? this.translate.instant('edge.manage-public-devices')
              : this.translate.instant('edge.manage-edge-devices');
          },
          icon: 'devices_other',
          isEnabled: (entity) => true,
          onAction: ($event,entity) => this.openEdgeEntitiesByType($event, entity, EntityType.DEVICE)
        },
        {
          name: this.translate.instant('edge.manage-edge-entity-views'),
          nameFunction: (edge) => {
            return edge.additionalInfo && edge.additionalInfo.isPublic
              ? this.translate.instant('edge.manage-public-entity-views')
              : this.translate.instant('edge.manage-edge-entity-views');
          },
          icon: 'view_quilt',
          isEnabled: (entity) => true,
          onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.ENTITY_VIEW)
        },
        {
          name: this.translate.instant('edge.manage-edge-dashboards'),
          nameFunction: (edge) => {
            return edge.additionalInfo && edge.additionalInfo.isPublic
              ? this.translate.instant('edge.manage-public-dashboards')
              : this.translate.instant('edge.manage-edge-dashboards');
          },
          icon: 'dashboard',
          isEnabled: (entity) => true,
          onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.DASHBOARD)
        },
        {
          name: this.translate.instant('edge.manage-edge-rulechains'),
          icon: 'settings_ethernet',
          isEnabled: (entity) => true,
          onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.RULE_CHAIN)
        }
      )
    }
    if (edgeScope === 'customer') {
      actions.push(
        // {
        //   name: this.translate.instant('edge.unassign-from-customer'),
        //   icon: 'assignment_return',
        //   isEnabled: (entity) => (entity.customerId && entity.customerId.id !== NULL_UUID && !entity.customerIsPublic),
        //   onAction: ($event, entity) => this.unassignFromCustomer($event, entity)
        // },
        // {
        //   name: this.translate.instant('edge.make-private'),
        //   icon: 'reply',
        //   isEnabled: (entity) => (entity.customerId && entity.customerId.id !== NULL_UUID && entity.customerIsPublic),
        //   onAction: ($event, entity) => this.unassignFromCustomer($event, entity)
        // },
      );
    }

    // if (edgeScope === 'customer_user') {
    //   actions.push(
    //     {
    //       name: this.translate.instant('edge.manage-edge-assets'),
    //       icon: 'domain',
    //       isEnabled: (entity) => true,
    //       onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.ASSET)
    //     },
    //     {
    //       name: this.translate.instant('edge.manage-edge-devices'),
    //       icon: 'devices_other',
    //       isEnabled: (entity) => true,
    //       onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.DEVICE)
    //     },
    //     {
    //       name: this.translate.instant('edge.manage-edge-entity-views'),
    //       icon: 'view_quilt',
    //       isEnabled: (entity) => true,
    //       onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.ENTITY_VIEW)
    //     },
    //     {
    //       name: this.translate.instant('edge.manage-edge-dashboards'),
    //       icon: 'dashboard',
    //       isEnabled: (entity) => true,
    //       onAction: ($event, entity) => this.openEdgeEntitiesByType($event, entity, EntityType.DASHBOARD)
    //     }
    //   );
    // }
    return actions;
  }

  configureGroupActions(edgeScope: string): Array<GroupActionDescriptor<Edge>> {
    const actions: Array<GroupActionDescriptor<Edge>> = [];
    /* if (edgeScope === 'tenant') {
      actions.push(
        {
          name: this.translate.instant('edge.assign-edge-to-customer-text'),
          icon: 'assignment_ind',
          isEnabled: true,
          onAction: ($event, entities) => this.assignToCustomer($event, entities.map((entity) => entity.id))
        }
      );
    }
    if (edgeScope === 'customer') {
      actions.push(
        {
          name: this.translate.instant('edge.unassign-from-customer'),
          icon: 'assignment_return',
          isEnabled: true,
          onAction: ($event, entities) => this.unassignEdgesFromCustomer($event, entities)
        }
      );
    }*/
    return actions;
  }

  configureAddActions(edgeScope: string): Array<HeaderActionDescriptor> {
    const actions: Array<HeaderActionDescriptor> = [];
    /*if (edgeScope === 'tenant') {
      actions.push(
        {
          name: this.translate.instant('edge.add-edge-text'),
          icon: 'insert_drive_file',
          isEnabled: () => true,
          onAction: ($event) => this.config.table.addEntity($event)
        },
        {
          name: this.translate.instant('edge.import'),
          icon: 'file_upload',
          isEnabled: () => true,
          onAction: ($event) => this.importEdges($event)
        }
      );
    }
    if (edgeScope === 'customer') {
      actions.push(
        {
          name: this.translate.instant('edge.assign-new-edge'),
          icon: 'add',
          isEnabled: () => true,
          onAction: ($event) => this.addEdgesToCustomer($event)
        }
      );
    }*/
    return actions;
  }

  importEdges($event: Event) {
    /*this.homeDialogs.importEntities(customerId, EntityType.EDGE, entityGroupId).subscribe((res) => {
      if (res) {
        this.broadcast.broadcast('edgeSaved');
        this.config.table.updateData();
      }
    });*/
  }

  addEdgesToCustomer($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<AddEntitiesToCustomerDialogComponent, AddEntitiesToCustomerDialogData,
      boolean>(AddEntitiesToCustomerDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        customerId: this.customerId,
        entityType: EntityType.EDGE
      }
    }).afterClosed()
      .subscribe((res) => {
        if (res) {
          this.config.table.updateData();
        }
      });
  }

  makePublic($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('edge.make-public-edge-title', {edgeName: edge.name}),
      this.translate.instant('edge.make-public-edge-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.edgeService.makeEdgePublic(edge.id.id).subscribe(
            () => {
              this.config.table.updateData();
            }
          );
        }
      }
    );
  }

  openEdgeEntitiesByType($event: Event, edge: Edge, entityType: EntityType) {
    if ($event) {
      $event.stopPropagation();
    }
    let suffix: string;
    switch (entityType) {
      case EntityType.DEVICE:
        suffix = 'devices';
        break;
      case EntityType.ASSET:
        suffix = 'assets';
        break;
      case EntityType.EDGE:
        suffix = 'assets';
        break;
      case EntityType.ENTITY_VIEW:
        suffix = 'entityViews';
        break;
      case EntityType.DASHBOARD:
        suffix = 'dashboards';
        break;
      case EntityType.RULE_CHAIN:
        suffix = 'ruleChains';
        break;
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/${suffix}`);
  }

  assignToCustomer($event: Event, edgesIds: Array<EdgeId>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<AssignToCustomerDialogComponent, AssignToCustomerDialogData,
      boolean>(AssignToCustomerDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entityIds: edgesIds,
        entityType: EntityType.EDGE
      }
    }).afterClosed()
      .subscribe((res) => {
        if (res) {
          this.config.table.updateData();
        }
      });
  }

  /*unassignFromCustomer($event: Event, edge: EdgeInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    const isPublic = edge.customerIsPublic;
    let title;
    let content;
    if (isPublic) {
      title = this.translate.instant('edge.make-private-edge-title', {edgeName: edge.name});
      content = this.translate.instant('edge.make-private-edge-text');
    } else {
      title = this.translate.instant('edge.unassign-edge-title', {edgeName: edge.name});
      content = this.translate.instant('edge.unassign-edge-text');
    }
    this.dialogService.confirm(
      title,
      content,
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.edgeService.unassignEdgeFromCustomer(edge.id.id).subscribe(
            () => {
              this.config.table.updateData();
            }
          );
        }
      }
    );
  }

  unassignEdgesFromCustomer($event: Event, edges: Array<EdgeInfo>) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('edge.unassign-edges-title', {count: edges.length}),
      this.translate.instant('edge.unassign-edges-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          const tasks: Observable<any>[] = [];
          edges.forEach(
            (edge) => {
              tasks.push(this.edgeService.unassignEdgeFromCustomer(edge.id.id));
            }
          );
          forkJoin(tasks).subscribe(
            () => {
              this.config.table.updateData();
            }
          );
        }
      }
    );
  }*/

  syncEdge($event, edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.edgeService.syncEdge(edge.id.id).subscribe(
      () => {
        this.store.dispatch(new ActionNotificationShow(
          {
            message: this.translate.instant('edge.sync-process-started-successfully'),
            type: 'success',
            duration: 750,
            verticalPosition: 'bottom',
            horizontalPosition: 'right'
          }));
      }
    );
  }

  onEdgeAction(action: EntityAction<EdgeInfo>): boolean {
    switch (action.action) {
      // case 'makePublic':
      //   this.makePublic(action.event, action.entity);
      //   return true;
      // case 'assignToCustomer':
      //   this.assignToCustomer(action.event, [action.entity.id]);
      //   return true;
      // case 'unassignFromCustomer':
      //   this.unassignFromCustomer(action.event, action.entity);
      //   return true;
      case 'openEdgeAssets':
        this.openEdgeEntitiesByType(action.event, action.entity, EntityType.ASSET);
        return true;
      case 'openEdgeDevices':
        this.openEdgeEntitiesByType(action.event, action.entity, EntityType.DEVICE);
        return true;
      case 'openEdgeEntityViews':
        this.openEdgeEntitiesByType(action.event, action.entity, EntityType.ENTITY_VIEW);
        return true;
      case 'openEdgeDashboards':
        this.openEdgeEntitiesByType(action.event, action.entity, EntityType.DASHBOARD);
        return true;
      case 'openEdgeRuleChains':
        this.openEdgeEntitiesByType(action.event, action.entity, EntityType.RULE_CHAIN);
        return true;
      case 'syncEdge':
        this.syncEdge(action.event, action.entity);
        return true;
    }
    return true;
  }

  manageEdgeUsers($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/users`);
  }

  manageEdgeAssets($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/assets`);
  }

  manageEdgeDevices($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/devices`);
  }

  manageEdgeEntityViews($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/entityViews`);
  }

  manageEdgeDashboards($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/dashboards`);
  }

  manageEdgeRuleChains($event: Event, edge: Edge) {
    if ($event) {
      $event.stopPropagation();
    }
    this.router.navigateByUrl(`edges/${edge.id.id}/ruleChains`);
  }

}
