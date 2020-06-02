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

import { Injectable } from '@angular/core';
import { EMPTY, forkJoin, Observable, of, throwError } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { PageLink, TimePageLink } from '@shared/models/page/page-link';
import { AliasEntityType, EntityType } from '@shared/models/entity-type.models';
import { BaseData } from '@shared/models/base-data';
import { EntityId } from '@shared/models/id/entity-id';
import { DeviceService } from '@core/http/device.service';
import { TenantService } from '@core/http/tenant.service';
import { CustomerService } from '@core/http/customer.service';
import { UserService } from './user.service';
import { DashboardService } from '@core/http/dashboard.service';
import { Direction } from '@shared/models/page/sort-order';
import { PageData } from '@shared/models/page/page-data';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Authority } from '@shared/models/authority.enum';
import { Tenant } from '@shared/models/tenant.model';
import { catchError, concatMap, expand, map, mergeMap, toArray } from 'rxjs/operators';
import { Customer } from '@app/shared/models/customer.model';
import { AssetService } from '@core/http/asset.service';
import { EntityViewService } from '@core/http/entity-view.service';
import { AttributeScope, DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { RuleChainService } from '@core/http/rule-chain.service';
import { AliasInfo, StateParams, SubscriptionInfo } from '@core/api/widget-api.models';
import { Datasource, DatasourceType, KeyInfo } from '@app/shared/models/widget.models';
import { UtilsService } from '@core/services/utils.service';
import { AliasFilterType, EntityAlias, EntityAliasFilter, EntityAliasFilterResult } from '@shared/models/alias.models';
import { entityFields, EntityInfo, ImportEntitiesResultInfo, ImportEntityData } from '@shared/models/entity.models';
import {
  EntityRelationInfo,
  EntityRelationsQuery,
  EntitySearchDirection,
  EntitySearchQuery
} from '@shared/models/relation.models';
import { EntityRelationService } from '@core/http/entity-relation.service';
import { isDefined } from '@core/utils';
import { Asset, AssetSearchQuery } from '@shared/models/asset.models';
import { Device, DeviceCredentialsType, DeviceSearchQuery } from '@shared/models/device.models';
import { EntityView, EntityViewSearchQuery } from '@shared/models/entity-view.models';
import { AttributeService } from '@core/http/attribute.service';
import { ConverterService } from '@core/http/converter.service';
import { IntegrationService } from '@core/http/integration.service';
import { SchedulerEventService } from '@core/http/scheduler-event.service';
import { BlobEntityService } from '@core/http/blob-entity.service';
import { RoleService } from '@core/http/role.service';
import { EntityGroupService } from '@core/http/entity-group.service';
import { Dashboard } from '@shared/models/dashboard.models';
import { User } from '@shared/models/user.model';
import { RuleChain } from '@shared/models/rule-chain.models';
import { Converter } from '@shared/models/converter.models';
import { Integration } from '@shared/models/integration.models';
import { SchedulerEvent } from '@shared/models/scheduler-event.models';
import { Role } from '@shared/models/role.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { Operation, resourceByEntityType, RoleType } from '@shared/models/security.models';
import { EntityGroup } from '@shared/models/entity-group.models';
import { CustomerId } from '@shared/models/id/customer-id';

@Injectable({
  providedIn: 'root'
})
export class EntityService {

  constructor(
    private http: HttpClient,
    private store: Store<AppState>,
    private deviceService: DeviceService,
    private assetService: AssetService,
    private entityViewService: EntityViewService,
    private tenantService: TenantService,
    private customerService: CustomerService,
    private userService: UserService,
    private ruleChainService: RuleChainService,
    private dashboardService: DashboardService,
    private entityRelationService: EntityRelationService,
    private attributeService: AttributeService,
    private converterService: ConverterService,
    private integrationService: IntegrationService,
    private schedulerEventService: SchedulerEventService,
    private blobEntityService: BlobEntityService,
    private roleService: RoleService,
    private entityGroupService: EntityGroupService,
    private userPermissionsService: UserPermissionsService,
    private utils: UtilsService
  ) { }

  private getEntityObservable(entityType: EntityType, entityId: string,
                              config?: RequestConfig): Observable<BaseData<EntityId>> {

    let observable: Observable<BaseData<EntityId>>;
    switch (entityType) {
      case EntityType.DEVICE:
        observable = this.deviceService.getDevice(entityId, config);
        break;
      case EntityType.ASSET:
        observable = this.assetService.getAsset(entityId, config);
        break;
      case EntityType.ENTITY_VIEW:
        observable = this.entityViewService.getEntityView(entityId, config);
        break;
      case EntityType.TENANT:
        observable = this.tenantService.getTenant(entityId, config);
        break;
      case EntityType.CUSTOMER:
        observable = this.customerService.getCustomer(entityId, config);
        break;
      case EntityType.DASHBOARD:
        if (config && config.loadEntityDetails) {
          observable = this.dashboardService.getDashboard(entityId, config);
        } else {
          observable = this.dashboardService.getDashboardInfo(entityId, config);
        }
        break;
      case EntityType.USER:
        observable = this.userService.getUser(entityId, config);
        break;
      case EntityType.RULE_CHAIN:
        observable = this.ruleChainService.getRuleChain(entityId, config);
        break;
      case EntityType.ALARM:
        console.error('Get Alarm Entity is not implemented!');
        break;
      case EntityType.CONVERTER:
        observable = this.converterService.getConverter(entityId, config);
        break;
      case EntityType.INTEGRATION:
        observable = this.integrationService.getIntegration(entityId, config);
        break
      case EntityType.SCHEDULER_EVENT:
        observable = this.schedulerEventService.getSchedulerEventInfo(entityId, config);
        break;
      case EntityType.BLOB_ENTITY:
        observable = this.blobEntityService.getBlobEntityInfo(entityId, config);
        break;
      case EntityType.ROLE:
        observable = this.roleService.getRole(entityId, config);
        break;
      case EntityType.ENTITY_GROUP:
        observable = this.entityGroupService.getEntityGroup(entityId, config);
        break;
    }
    return observable;
  }

  private saveEntityObservable(entity: BaseData<EntityId>,
                               config?: RequestConfig): Observable<BaseData<EntityId>> {
    let observable: Observable<BaseData<EntityId>>;
    const entityType = entity.id.entityType;
    if (!entity.id.id) {
      delete entity.id;
    }
    switch (entityType) {
      case EntityType.DEVICE:
        observable = this.deviceService.saveDevice(entity as Device, null, config);
        break;
      case EntityType.ASSET:
        observable = this.assetService.saveAsset(entity as Asset, null, config);
        break;
      case EntityType.ENTITY_VIEW:
        observable = this.entityViewService.saveEntityView(entity as EntityView, null, config);
        break;
      case EntityType.TENANT:
        observable = this.tenantService.saveTenant(entity as Tenant, config);
        break;
      case EntityType.CUSTOMER:
        observable = this.customerService.saveCustomer(entity as Customer, null, config);
        break;
      case EntityType.DASHBOARD:
        observable = this.dashboardService.saveDashboard(entity as Dashboard, null, config);
        break;
      case EntityType.USER:
        observable = this.userService.saveUser(entity as User, false, null, config);
        break;
      case EntityType.RULE_CHAIN:
        observable = this.ruleChainService.saveRuleChain(entity as RuleChain, config);
        break;
      case EntityType.ALARM:
        console.error('Save Alarm Entity is not implemented!');
        break;
      case EntityType.CONVERTER:
        observable = this.converterService.saveConverter(entity as Converter, config);
        break;
      case EntityType.INTEGRATION:
        observable = this.integrationService.saveIntegration(entity as Integration, config);
        break;
      case EntityType.SCHEDULER_EVENT:
        observable = this.schedulerEventService.saveSchedulerEvent(entity as SchedulerEvent, config);
        break;
      case EntityType.ROLE:
        observable = this.roleService.saveRole(entity as Role, config);
        break;
    }
    return observable;
  }

  public getEntity(entityType: EntityType, entityId: string,
                   config?: RequestConfig): Observable<BaseData<EntityId>> {
    const entityObservable = this.getEntityObservable(entityType, entityId, config);
    if (entityObservable) {
      return entityObservable;
    } else {
      return throwError(null);
    }
  }

  public saveEntity(entity: BaseData<EntityId>,
                    config?: RequestConfig): Observable<BaseData<EntityId>> {
    const entityObservable = this.saveEntityObservable(entity, config);
    if (entityObservable) {
      return entityObservable;
    } else {
      return throwError(null);
    }
  }

  private saveGroupEntityObservable(entity: BaseData<EntityId>,
                                    entityGroupId: string,
                                    config?: RequestConfig): Observable<BaseData<EntityId>> {
    let observable: Observable<BaseData<EntityId>>;
    const entityType = entity.id.entityType;
    if (!entity.id.id) {
      delete entity.id;
    }
    switch (entityType) {
      case EntityType.DEVICE:
        observable = this.deviceService.saveDevice(entity as Device, entityGroupId, config);
        break;
      case EntityType.ASSET:
        observable = this.assetService.saveAsset(entity as Asset, entityGroupId, config);
        break;
      case EntityType.ENTITY_VIEW:
        observable = this.entityViewService.saveEntityView(entity as EntityView, entityGroupId, config);
        break;
      case EntityType.CUSTOMER:
        observable = this.customerService.saveCustomer(entity as Customer, entityGroupId, config);
        break;
      case EntityType.DASHBOARD:
        observable = this.dashboardService.saveDashboard(entity as Dashboard, entityGroupId, config);
        break;
      case EntityType.USER:
        observable = this.userService.saveUser(entity as User, false, entityGroupId, config);
        break;
    }
    return observable;
  }

  public saveGroupEntity(entity: BaseData<EntityId>,
                         entityGroupId: string,
                         config?: RequestConfig): Observable<BaseData<EntityId>> {
    const entityObservable = this.saveGroupEntityObservable(entity, entityGroupId, config);
    if (entityObservable) {
      return entityObservable;
    } else {
      return throwError(null);
    }
  }

  /*private getEntitiesByIdsObservable(fetchEntityFunction: (entityId: string) => Observable<BaseData<EntityId>>,
                                     entityIds: Array<string>): Observable<Array<BaseData<EntityId>>> {
    const tasks: Observable<BaseData<EntityId>>[] = [];
    entityIds.forEach((entityId) => {
      tasks.push(fetchEntityFunction(entityId));
    });
    return forkJoin(tasks).pipe(
      map((entities) => {
        if (entities) {
          entities.sort((entity1, entity2) => {
            const index1 = entityIds.indexOf(entity1.id.id);
            const index2 = entityIds.indexOf(entity2.id.id);
            return index1 - index2;
          });
          return entities;
        } else {
          return [];
        }
      })
    );
  }*/


  private getEntitiesObservable(entityType: EntityType, entityIds: Array<string>,
                                config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    let observable: Observable<Array<BaseData<EntityId>>>;
    switch (entityType) {
      case EntityType.DEVICE:
        observable = this.deviceService.getDevices(entityIds, config);
        break;
      case EntityType.ASSET:
        observable = this.assetService.getAssets(entityIds, config);
        break;
      case EntityType.ENTITY_VIEW:
        observable = this.entityViewService.getEntityViews(entityIds, config);
        break;
      case EntityType.TENANT:
        observable = this.tenantService.getTenantsByIds(entityIds, config);
        break;
      case EntityType.CUSTOMER:
        observable = this.customerService.getCustomersByIds(entityIds, config);
        break;
      case EntityType.DASHBOARD:
        observable = this.dashboardService.getDashboards(entityIds, config);
        break;
      case EntityType.USER:
        observable = this.userService.getUsers(entityIds, config);
        break;
      case EntityType.ALARM:
        console.error('Get Alarm Entity is not implemented!');
        break;
      case EntityType.ENTITY_GROUP:
        observable = this.entityGroupService.getEntityGroupsByIds(entityIds, config);
        break;
      case EntityType.CONVERTER:
        observable = this.converterService.getConvertersByIds(entityIds, config);
        break;
      case EntityType.INTEGRATION:
        observable = this.integrationService.getIntegrationsByIds(entityIds, config);
        break;
      case EntityType.SCHEDULER_EVENT:
        observable = this.schedulerEventService.getSchedulerEventsByIds(entityIds, config);
        break;
      case EntityType.BLOB_ENTITY:
        observable = this.blobEntityService.getBlobEntitiesByIds(entityIds, config);
        break;
      case EntityType.ROLE:
        observable = this.roleService.getRolesByIds(entityIds, config);
        break;
    }
    return observable;
  }

  public getEntities(entityType: EntityType, entityIds: Array<string>,
                     config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    const entitiesObservable = this.getEntitiesObservable(entityType, entityIds, config);
    if (entitiesObservable) {
      return entitiesObservable;
    } else {
      return throwError(null);
    }
  }

  private getSingleTenantByPageLinkObservable(pageLink: PageLink,
                                              config?: RequestConfig): Observable<PageData<Tenant>> {
    const authUser = getCurrentAuthUser(this.store);
    const tenantId = authUser.tenantId;
    return this.tenantService.getTenant(tenantId, config).pipe(
      map((tenant) => {
        const result = {
          data: [],
          totalPages: 0,
          totalElements: 0,
          hasNext: false
        } as PageData<Tenant>;
        if (tenant.title.toLowerCase().startsWith(pageLink.textSearch.toLowerCase())) {
          result.data.push(tenant);
          result.totalPages = 1;
          result.totalElements = 1;
        }
        return result;
      })
    );
  }

  private getEntitiesByPageLinkObservable(entityType: EntityType, pageLink: PageLink, subType: string = '',
                                          config?: RequestConfig): Observable<PageData<BaseData<EntityId>>> {
    let entitiesObservable: Observable<PageData<BaseData<EntityId>>>;
    const authUser = getCurrentAuthUser(this.store);
    const isGenericPermission = this.userPermissionsService.hasReadGenericPermission(resourceByEntityType.get(entityType));
    switch (entityType) {
      case EntityType.DEVICE:
        pageLink.sortOrder.property = 'name';
        if (authUser.authority === Authority.TENANT_ADMIN && isGenericPermission) {
          entitiesObservable = this.deviceService.getTenantDevices(pageLink, subType, config);
        } else {
          entitiesObservable = this.deviceService.getUserDevices(pageLink, subType, config);
        }
        break;
      case EntityType.ASSET:
        pageLink.sortOrder.property = 'name';
        if (authUser.authority === Authority.TENANT_ADMIN && isGenericPermission) {
          entitiesObservable = this.assetService.getTenantAssets(pageLink, subType, config);
        } else {
          entitiesObservable = this.assetService.getUserAssets(pageLink, subType, config);
        }
        break;
      case EntityType.ENTITY_VIEW:
        pageLink.sortOrder.property = 'name';
        if (authUser.authority === Authority.TENANT_ADMIN && isGenericPermission) {
          entitiesObservable = this.entityViewService.getTenantEntityViews(pageLink,
            subType, config);
        } else {
          entitiesObservable = this.entityViewService.getUserEntityViews(pageLink, subType, config);
        }
        break;
      case EntityType.TENANT:
        pageLink.sortOrder.property = 'title';
        if (authUser.authority === Authority.TENANT_ADMIN) {
          entitiesObservable = this.getSingleTenantByPageLinkObservable(pageLink, config);
        } else {
          entitiesObservable = this.tenantService.getTenants(pageLink, config);
        }
        break;
      case EntityType.CUSTOMER:
        pageLink.sortOrder.property = 'title';
        entitiesObservable = this.customerService.getUserCustomers(pageLink, config);
        break;
      case EntityType.RULE_CHAIN:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.ruleChainService.getRuleChains(pageLink, config);
        break;
      case EntityType.DASHBOARD:
        pageLink.sortOrder.property = 'title';
        if (authUser.authority === Authority.TENANT_ADMIN && isGenericPermission) {
          entitiesObservable = this.dashboardService.getTenantDashboards(pageLink, config);
        } else {
          entitiesObservable = this.dashboardService.getUserDashboards(null, null, pageLink, config);
        }
        break;
      case EntityType.USER:
        pageLink.sortOrder.property = 'email';
        entitiesObservable = this.userService.getUserUsers(pageLink, config);
        break;
      case EntityType.ALARM:
        console.error('Get Alarm Entities is not implemented!');
        break;
      case EntityType.ENTITY_GROUP:
        pageLink.sortOrder.property = 'name';
        if (subType && subType.length) {
          entitiesObservable = this.entityGroupService.getEntityGroupsByPageLink(pageLink, subType as EntityType, config);
        } else {
          entitiesObservable = of(null);
        }
        break;
      case EntityType.CONVERTER:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.converterService.getConverters(pageLink, config);
        break;
      case EntityType.INTEGRATION:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.integrationService.getIntegrations(pageLink, config);
        break;
      case EntityType.SCHEDULER_EVENT:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.schedulerEventService.getSchedulerEvents(null, config).pipe(
          map((schedulerEvents) => {
            return pageLink.filterData(schedulerEvents);
          })
        );
        break;
      case EntityType.BLOB_ENTITY:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.blobEntityService.getBlobEntities(pageLink as TimePageLink, null, config);
        break;
      case EntityType.ROLE:
        pageLink.sortOrder.property = 'name';
        entitiesObservable = this.roleService.getRoles(pageLink, subType as RoleType, config);
        break;
    }
    return entitiesObservable;
  }

  private getEntitiesByPageLink(entityType: EntityType, pageLink: PageLink, subType: string = '',
                                config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    const entitiesObservable: Observable<PageData<BaseData<EntityId>>> =
      this.getEntitiesByPageLinkObservable(entityType, pageLink, subType, config);
    if (entitiesObservable) {
      return entitiesObservable.pipe(
        expand((data) => {
          if (data.hasNext) {
            pageLink.page += 1;
            return this.getEntitiesByPageLinkObservable(entityType, pageLink, subType, config);
          } else {
            return EMPTY;
          }
        }),
        map((data) => data.data),
        concatMap((data) => data),
        toArray()
      );
    } else {
      return of(null);
    }
  }

  public getEntitiesByNameFilter(entityType: EntityType, entityNameFilter: string,
                                 pageSize: number, subType: string = '',
                                 config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    const pageLink = new PageLink(pageSize, 0, entityNameFilter, {
      property: 'name',
      direction: Direction.ASC
    });
    if (pageSize === -1) { // all
      pageLink.pageSize = 100;
      return this.getEntitiesByPageLink(entityType, pageLink, subType, config).pipe(
        map((data) => data && data.length ? data : null)
      );
    } else {
      const entitiesObservable: Observable<PageData<BaseData<EntityId>>> =
        this.getEntitiesByPageLinkObservable(entityType, pageLink, subType, config);
      if (entitiesObservable) {
        return entitiesObservable.pipe(
          map((data) => data && data.data.length ? data.data : null)
        );
      } else {
        return of(null);
      }
    }
  }

  private getEntityGroupEntitiesByPageLink(entityGroupId: string, pageLink: PageLink, entityGroupType: EntityType,
                                          config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    const entitiesObservable: Observable<PageData<BaseData<EntityId>>> =
      this.entityGroupService.getEntityGroupEntities(entityGroupId, pageLink, entityGroupType, config);
    if (entitiesObservable) {
      return entitiesObservable.pipe(
        expand((data) => {
          if (data.hasNext) {
            pageLink.page += 1;
            return this.entityGroupService.getEntityGroupEntities<BaseData<EntityId>>(entityGroupId, pageLink, entityGroupType, config);
          } else {
            return EMPTY;
          }
        }),
        map((data) => data.data),
        concatMap((data) => data),
        toArray()
      );
    } else {
      return of(null);
    }
  }

  public getEntityGroupEntities(entityGroupId: string, entityGroupType: EntityType,
                                pageSize: number, config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    const pageLink = new PageLink(pageSize, 0, null, {
      property: 'name',
      direction: Direction.ASC
    });
    if (pageSize === -1) { // all
      pageLink.pageSize = 100;
      return this.getEntityGroupEntitiesByPageLink(entityGroupId, pageLink, entityGroupType, config).pipe(
        map((data) => data && data.length ? data : null)
      );
    } else {
      const entitiesObservable: Observable<PageData<BaseData<EntityId>>> =
        this.entityGroupService.getEntityGroupEntities(entityGroupId, pageLink, entityGroupType, config);
      if (entitiesObservable) {
        return entitiesObservable.pipe(
          map((data) => data && data.data.length ? data.data : null)
        );
      } else {
        return of(null);
      }
    }
  }

  public getEntitiesByGroupName(entityType: EntityType, entityNameFilter: string,
                                pageSize: number, stateEntityId?: EntityId, config?: RequestConfig): Observable<Array<BaseData<EntityId>>> {
    let entityGroupsObservable: Observable<Array<EntityGroup>>;
    if (isDefined(stateEntityId)) {
      entityGroupsObservable = this.getEntity(stateEntityId.entityType as EntityType, stateEntityId.id, config).pipe(
        mergeMap((entity) => {
          let entityId: EntityId;
          if (entity.id.entityType === EntityType.CUSTOMER) {
            entityId = entity.id;
          } else {
            entityId = entity.ownerId;
          }
          return this.entityGroupService.getEntityGroupsByOwnerId(entityId.entityType as EntityType, entityId.id, entityType, config);
        }),
        catchError((err) => {
          return of(null);
        })
      );
    } else {
      entityGroupsObservable = this.entityGroupService.getEntityGroups(entityType, config);
    }
    return entityGroupsObservable.pipe(
      map((entityGroups) => {
        if (entityGroups && entityGroups.length) {
          for (const entityGroup of entityGroups) {
            if (entityGroup.name === entityNameFilter) {
              return entityGroup.id.id;
            }
          }
        }
        return null;
      }),
      catchError((err) => {
        return of(null as string);
      })
    ).pipe(
      mergeMap((groupId) => {
        if (groupId) {
          return this.getEntityGroupEntities(groupId, entityType, pageSize, config);
        } else {
          return of(null as Array<BaseData<EntityId>>);
        }
      }),
      catchError((err) => {
        return of(null as Array<BaseData<EntityId>>);
      })
    );
  }

  public getAliasFilterTypesByEntityTypes(entityTypes: Array<EntityType | AliasEntityType>): Array<AliasFilterType> {
    const allAliasFilterTypes: Array<AliasFilterType> = Object.keys(AliasFilterType).map((key) => AliasFilterType[key]);
    if (!entityTypes || !entityTypes.length) {
      return allAliasFilterTypes;
    }
    const result = [];
    for (const aliasFilterType of allAliasFilterTypes) {
      if (this.filterAliasFilterTypeByEntityTypes(aliasFilterType, entityTypes)) {
        result.push(aliasFilterType);
      }
    }
    return result;
  }

  public filterAliasByEntityTypes(entityAlias: EntityAlias, entityTypes: Array<EntityType | AliasEntityType>): boolean {
    const filter = entityAlias.filter;
    if (this.filterAliasFilterTypeByEntityTypes(filter.type, entityTypes)) {
      switch (filter.type) {
        case AliasFilterType.singleEntity:
          return entityTypes.indexOf(filter.singleEntity.entityType) > -1;
        case AliasFilterType.entityGroup:
          return entityTypes.indexOf(filter.groupType) > -1;
        case AliasFilterType.entityList:
          return entityTypes.indexOf(filter.entityType) > -1;
        case AliasFilterType.entityName:
          return entityTypes.indexOf(filter.entityType) > -1;
        case AliasFilterType.entityGroupList:
          return entityTypes.indexOf(EntityType.ENTITY_GROUP) > -1;
        case AliasFilterType.entityGroupName:
          return entityTypes.indexOf(EntityType.ENTITY_GROUP) > -1;
        case AliasFilterType.entitiesByGroupName:
          return entityTypes.indexOf(filter.entityType) > -1;
        case AliasFilterType.stateEntity:
        case AliasFilterType.stateEntityOwner:
          return true;
        case AliasFilterType.assetType:
          return entityTypes.indexOf(EntityType.ASSET) > -1;
        case AliasFilterType.deviceType:
          return entityTypes.indexOf(EntityType.DEVICE) > -1;
        case AliasFilterType.entityViewType:
          return entityTypes.indexOf(EntityType.ENTITY_VIEW) > -1;
        case AliasFilterType.relationsQuery:
          if (filter.filters && filter.filters.length) {
            let match = false;
            for (const relationFilter of filter.filters) {
              if (relationFilter.entityTypes && relationFilter.entityTypes.length) {
                for (const relationFilterEntityType of relationFilter.entityTypes) {
                  if (entityTypes.indexOf(relationFilterEntityType) > -1) {
                    match = true;
                    break;
                  }
                }
              } else {
                match = true;
                break;
              }
            }
            return match;
          } else {
            return true;
          }
        case AliasFilterType.assetSearchQuery:
          return entityTypes.indexOf(EntityType.ASSET) > -1;
        case AliasFilterType.deviceSearchQuery:
          return entityTypes.indexOf(EntityType.DEVICE) > -1;
        case AliasFilterType.entityViewSearchQuery:
          return entityTypes.indexOf(EntityType.ENTITY_VIEW) > -1;
      }
    }
    return false;
  }

  private filterAliasFilterTypeByEntityTypes(aliasFilterType: AliasFilterType,
                                             entityTypes: Array<EntityType | AliasEntityType>): boolean {
    if (!entityTypes || !entityTypes.length) {
      return true;
    }
    let valid = false;
    entityTypes.forEach((entityType) => {
      valid = valid || this.filterAliasFilterTypeByEntityType(aliasFilterType, entityType);
    });
    return valid;
  }

  private filterAliasFilterTypeByEntityType(aliasFilterType: AliasFilterType, entityType: EntityType | AliasEntityType): boolean {
    switch (aliasFilterType) {
      case AliasFilterType.singleEntity:
        return true;
      case AliasFilterType.entityGroup:
        return true;
      case AliasFilterType.entityList:
        return true;
      case AliasFilterType.entityName:
        return true;
      case AliasFilterType.entityGroupList:
        return entityType === EntityType.ENTITY_GROUP;
      case AliasFilterType.entityGroupName:
        return entityType === EntityType.ENTITY_GROUP;
      case AliasFilterType.entitiesByGroupName:
        return true;
      case AliasFilterType.stateEntity:
      case AliasFilterType.stateEntityOwner:
        return true;
      case AliasFilterType.assetType:
        return entityType === EntityType.ASSET;
      case AliasFilterType.deviceType:
        return entityType === EntityType.DEVICE;
      case AliasFilterType.entityViewType:
        return entityType === EntityType.ENTITY_VIEW;
      case AliasFilterType.relationsQuery:
        return true;
      case AliasFilterType.assetSearchQuery:
        return entityType === EntityType.ASSET;
      case AliasFilterType.deviceSearchQuery:
        return entityType === EntityType.DEVICE;
      case AliasFilterType.entityViewSearchQuery:
        return entityType === EntityType.ENTITY_VIEW;
    }
    return false;
  }

  public prepareAllowedEntityTypesList(allowedEntityTypes: Array<EntityType | AliasEntityType>,
                                       useAliasEntityTypes?: boolean, operation?: Operation): Array<EntityType | AliasEntityType> {
    const authUser = getCurrentAuthUser(this.store);
    const entityTypes: Array<EntityType | AliasEntityType> = [];
    switch (authUser.authority) {
      case Authority.SYS_ADMIN:
        entityTypes.push(EntityType.TENANT);
        break;
      case Authority.TENANT_ADMIN:
        entityTypes.push(EntityType.DEVICE);
        entityTypes.push(EntityType.ASSET);
        entityTypes.push(EntityType.ENTITY_VIEW);
        entityTypes.push(EntityType.TENANT);
        entityTypes.push(EntityType.CUSTOMER);
        entityTypes.push(EntityType.DASHBOARD);
        entityTypes.push(EntityType.USER);
        entityTypes.push(EntityType.CONVERTER);
        entityTypes.push(EntityType.INTEGRATION);
        entityTypes.push(EntityType.SCHEDULER_EVENT);
        entityTypes.push(EntityType.BLOB_ENTITY);
        entityTypes.push(EntityType.ROLE);
        if (useAliasEntityTypes) {
          entityTypes.push(AliasEntityType.CURRENT_CUSTOMER);
          entityTypes.push(AliasEntityType.CURRENT_TENANT);
        }
        break;
      case Authority.CUSTOMER_USER:
        entityTypes.push(EntityType.DEVICE);
        entityTypes.push(EntityType.ASSET);
        entityTypes.push(EntityType.ENTITY_VIEW);
        entityTypes.push(EntityType.CUSTOMER);
        entityTypes.push(EntityType.DASHBOARD);
        entityTypes.push(EntityType.USER);
        entityTypes.push(EntityType.SCHEDULER_EVENT);
        entityTypes.push(EntityType.BLOB_ENTITY);
        if (useAliasEntityTypes) {
          entityTypes.push(AliasEntityType.CURRENT_CUSTOMER);
        }
        break;
    }
    if (allowedEntityTypes && allowedEntityTypes.length) {
      for (let index = entityTypes.length - 1; index >= 0; index--) {
        if (allowedEntityTypes.indexOf(entityTypes[index]) === -1) {
          entityTypes.splice(index, 1);
        }
      }
    }
    if (operation) {
      for (let index = entityTypes.length - 1; index >= 0; index--) {
        const resource = resourceByEntityType.get(entityTypes[index] as EntityType);
        if (resource) {
          if (!this.userPermissionsService.hasGenericPermission(resource, operation)) {
            entityTypes.splice(index, 1);
          }
        }
      }
    }
    return entityTypes;
  }

  private getEntityFieldKeys (entityType: EntityType, searchText: string): Array<string> {
    const entityFieldKeys: string[] = [];
    const query = searchText.toLowerCase();
    switch(entityType) {
      case EntityType.USER:
        entityFieldKeys.push(entityFields.name.keyName);
        entityFieldKeys.push(entityFields.email.keyName);
        entityFieldKeys.push(entityFields.firstName.keyName);
        entityFieldKeys.push(entityFields.lastName.keyName);
        break;
      case EntityType.TENANT:
      case EntityType.CUSTOMER:
        entityFieldKeys.push(entityFields.title.keyName);
        entityFieldKeys.push(entityFields.email.keyName);
        entityFieldKeys.push(entityFields.country.keyName);
        entityFieldKeys.push(entityFields.state.keyName);
        entityFieldKeys.push(entityFields.city.keyName);
        entityFieldKeys.push(entityFields.address.keyName);
        entityFieldKeys.push(entityFields.address2.keyName);
        entityFieldKeys.push(entityFields.zip.keyName);
        entityFieldKeys.push(entityFields.phone.keyName);
        break;
      case EntityType.ENTITY_VIEW:
        entityFieldKeys.push(entityFields.name.keyName);
        entityFieldKeys.push(entityFields.type.keyName);
        break;
      case EntityType.DEVICE:
      case EntityType.ASSET:
        entityFieldKeys.push(entityFields.name.keyName);
        entityFieldKeys.push(entityFields.type.keyName);
        entityFieldKeys.push(entityFields.label.keyName);
        break;
      case EntityType.DASHBOARD:
        entityFieldKeys.push(entityFields.title.keyName);
        break;
    }
    return query ? entityFieldKeys.filter((entityField) => entityField.toLowerCase().indexOf(query) === 0) : entityFieldKeys;
  }

  public getEntityKeys(entityId: EntityId, query: string, type: DataKeyType,
                       config?: RequestConfig): Observable<Array<string>> {
    if (type === DataKeyType.entityField) {
      return of(this.getEntityFieldKeys(entityId.entityType as EntityType, query));
    }
    let url = `/api/plugins/telemetry/${entityId.entityType}/${entityId.id}/keys/`;
    if (type === DataKeyType.timeseries) {
      url += 'timeseries';
    } else if (type === DataKeyType.attribute) {
      url += 'attributes';
    }
    return this.http.get<Array<string>>(url,
      defaultHttpOptionsFromConfig(config))
      .pipe(
        map(
          (dataKeys) => {
            if (query) {
              const lowercaseQuery = query.toLowerCase();
              return dataKeys.filter((dataKey) => dataKey.toLowerCase().indexOf(lowercaseQuery) === 0);
            } else {
              return dataKeys;
            }
           }
        )
    );
  }

  public createDatasourcesFromSubscriptionsInfo(subscriptionsInfo: Array<SubscriptionInfo>): Observable<Array<Datasource>> {
    const observables = new Array<Observable<Array<Datasource>>>();
    subscriptionsInfo.forEach((subscriptionInfo) => {
      observables.push(this.createDatasourcesFromSubscriptionInfo(subscriptionInfo));
    });
    return forkJoin(observables).pipe(
      map((arrayOfDatasources) => {
        const result = new Array<Datasource>();
        arrayOfDatasources.forEach((datasources) => {
          result.push(...datasources);
        });
        this.utils.generateColors(result);
        return result;
      })
    );
  }

  public createAlarmSourceFromSubscriptionInfo(subscriptionInfo: SubscriptionInfo): Observable<Datasource> {
    if (subscriptionInfo.entityId && subscriptionInfo.entityType) {
      return this.getEntity(subscriptionInfo.entityType, subscriptionInfo.entityId,
        {ignoreLoading: true, ignoreErrors: true}).pipe(
        map((entity) => {
          const alarmSource = this.createDatasourceFromSubscription(subscriptionInfo, entity);
          this.utils.generateColors([alarmSource]);
          return alarmSource;
        })
      );
    } else {
      return throwError(null);
    }
  }

  public resolveAlias(entityAlias: EntityAlias, stateParams: StateParams): Observable<AliasInfo> {
    const filter = entityAlias.filter;
    return this.resolveAliasFilter(filter, stateParams, -1, false).pipe(
      map((result) => {
        const aliasInfo: AliasInfo = {
          alias: entityAlias.alias,
          stateEntity: result.stateEntity,
          entityParamName: result.entityParamName,
          resolveMultiple: filter.resolveMultiple
        };
        aliasInfo.resolvedEntities = result.entities;
        aliasInfo.currentEntity = null;
        if (aliasInfo.resolvedEntities.length) {
          aliasInfo.currentEntity = aliasInfo.resolvedEntities[0];
        }
        return aliasInfo;
      })
    );
  }

  public resolveAliasFilter(filter: EntityAliasFilter, stateParams: StateParams,
                            maxItems: number, failOnEmpty: boolean): Observable<EntityAliasFilterResult> {
    const result: EntityAliasFilterResult = {
      entities: [],
      stateEntity: false
    };
    if (filter.stateEntityParamName && filter.stateEntityParamName.length) {
      result.entityParamName = filter.stateEntityParamName;
    }
    const stateEntityInfo = this.getStateEntityInfo(filter, stateParams);
    const stateEntityId = stateEntityInfo.entityId;
    const stateEntityGroupType = stateEntityInfo.entityGroupType;
    switch (filter.type) {
      case AliasFilterType.singleEntity:
        const aliasEntityId = this.resolveAliasEntityId(filter.singleEntity.entityType, filter.singleEntity.id);
        return this.getEntity(aliasEntityId.entityType as EntityType, aliasEntityId.id, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entity) => {
            result.entities = this.entitiesToEntitiesInfo([entity]);
            return result;
          }
        ));
        break;
      case AliasFilterType.entityGroup:
        result.stateEntity = filter.groupStateEntity;
        let entityGroup: string;
        let entityType: EntityType;
        if (result.stateEntity && stateEntityId) {
          entityGroup = stateEntityId.id;
          entityType = stateEntityGroupType;
        } else {
          entityGroup = filter.entityGroup;
          entityType = filter.groupType;
        }
        return this.getEntityGroupEntities(entityGroup, entityType, maxItems, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
            if (entities && entities.length || !failOnEmpty) {
              result.entities = this.entitiesToEntitiesInfo(entities);
              return result;
            } else {
              throw new Error();
            }
          })
        );
        break;
      case AliasFilterType.entityList:
        return this.getEntities(filter.entityType, filter.entityList, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          ));
      case AliasFilterType.entityName:
        return this.getEntitiesByNameFilter(filter.entityType, filter.entityNameFilter, maxItems,
          '', {ignoreLoading: true, ignoreErrors: true}).pipe(
            map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
        break;
      case AliasFilterType.entityGroupList:
        return this.getEntities(EntityType.ENTITY_GROUP, filter.entityGroupList, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
        break;
      case AliasFilterType.entityGroupName:
        return this.getEntitiesByNameFilter(EntityType.ENTITY_GROUP, filter.entityGroupNameFilter, maxItems,
          filter.groupType, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
        break;
      case AliasFilterType.entitiesByGroupName:
        result.stateEntity = filter.groupStateEntity;
        return this.getEntitiesByGroupName(filter.groupType, filter.entityGroupNameFilter, maxItems, stateEntityId,
          {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
        break;
      case AliasFilterType.stateEntity:
        result.stateEntity = true;
        if (stateEntityId) {
          return this.getEntity(stateEntityId.entityType as EntityType, stateEntityId.id, {ignoreLoading: true, ignoreErrors: true}).pipe(
            map((entity) => {
                result.entities = this.entitiesToEntitiesInfo([entity]);
                return result;
              }
            ),
            catchError((err) => {
                return of(result);
              }
            )
          );
        } else {
          return of(result);
        }
        break;
      case AliasFilterType.stateEntityOwner:
        result.stateEntity = true;
        if (stateEntityId) {
          return this.getEntity(stateEntityId.entityType as EntityType, stateEntityId.id, {ignoreLoading: true, ignoreErrors: true}).pipe(
            mergeMap((entity) => {
              return this.getEntity(entity.ownerId.entityType as EntityType, entity.ownerId.id,
                {ignoreLoading: true, ignoreErrors: true}).pipe(
                map((ownerEntity) => {
                    result.entities = this.entitiesToEntitiesInfo([ownerEntity]);
                    return result;
                  }
                ),
                catchError((err) => {
                    return of(result);
                  }
                ));
              }
            ),
            catchError((err) => {
                return of(result);
              }
            )
          );
        } else {
          return of(result);
        }
      case AliasFilterType.assetType:
        return this.getEntitiesByNameFilter(EntityType.ASSET, filter.assetNameFilter, maxItems,
          filter.assetType, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
      case AliasFilterType.deviceType:
        return this.getEntitiesByNameFilter(EntityType.DEVICE, filter.deviceNameFilter, maxItems,
          filter.deviceType, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
      case AliasFilterType.entityViewType:
        return this.getEntitiesByNameFilter(EntityType.ENTITY_VIEW, filter.entityViewNameFilter, maxItems,
          filter.entityViewType, {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw new Error();
              }
            }
          )
        );
      case AliasFilterType.relationsQuery:
        result.stateEntity = filter.rootStateEntity;
        let rootEntityType;
        let rootEntityId;
        if (result.stateEntity && stateEntityId) {
          rootEntityType = stateEntityId.entityType;
          rootEntityId = stateEntityId.id;
        } else if (!result.stateEntity) {
          rootEntityType = filter.rootEntity.entityType;
          rootEntityId = filter.rootEntity.id;
        }
        if (rootEntityType && rootEntityId) {
          const relationQueryRootEntityId = this.resolveAliasEntityId(rootEntityType, rootEntityId);
          const searchQuery: EntityRelationsQuery = {
            parameters: {
              rootId: relationQueryRootEntityId.id,
              rootType: relationQueryRootEntityId.entityType as EntityType,
              direction: filter.direction,
              fetchLastLevelOnly: filter.fetchLastLevelOnly
            },
            filters: filter.filters
          };
          searchQuery.parameters.maxLevel = filter.maxLevel && filter.maxLevel > 0 ? filter.maxLevel : -1;
          return this.entityRelationService.findInfoByQuery(searchQuery, {ignoreLoading: true, ignoreErrors: true}).pipe(
            mergeMap((allRelations) => {
              if (allRelations && allRelations.length || !failOnEmpty) {
                if (isDefined(maxItems) && maxItems > 0 && allRelations) {
                  const limit = Math.min(allRelations.length, maxItems);
                  allRelations.length = limit;
                }
                return this.entityRelationInfosToEntitiesInfo(allRelations, filter.direction).pipe(
                  map((entities) => {
                    result.entities = entities;
                    return result;
                  })
                );
              } else {
                return throwError(null);
              }
            })
          );
        } else {
          return of(result);
        }
      case AliasFilterType.assetSearchQuery:
      case AliasFilterType.deviceSearchQuery:
      case AliasFilterType.entityViewSearchQuery:
        result.stateEntity = filter.rootStateEntity;
        if (result.stateEntity && stateEntityId) {
          rootEntityType = stateEntityId.entityType;
          rootEntityId = stateEntityId.id;
        } else if (!result.stateEntity) {
          rootEntityType = filter.rootEntity.entityType;
          rootEntityId = filter.rootEntity.id;
        }
        if (rootEntityType && rootEntityId) {
          const searchQueryRootEntityId = this.resolveAliasEntityId(rootEntityType, rootEntityId);
          const searchQuery: EntitySearchQuery = {
            parameters: {
              rootId: searchQueryRootEntityId.id,
              rootType: searchQueryRootEntityId.entityType as EntityType,
              direction: filter.direction,
              fetchLastLevelOnly: filter.fetchLastLevelOnly
            },
            relationType: filter.relationType
          };
          searchQuery.parameters.maxLevel = filter.maxLevel && filter.maxLevel > 0 ? filter.maxLevel : -1;
          let findByQueryObservable: Observable<Array<BaseData<EntityId>>>;
          if (filter.type === AliasFilterType.assetSearchQuery) {
            const assetSearchQuery = searchQuery as AssetSearchQuery;
            assetSearchQuery.assetTypes = filter.assetTypes;
            findByQueryObservable = this.assetService.findByQuery(assetSearchQuery, {ignoreLoading: true, ignoreErrors: true});
          } else if (filter.type === AliasFilterType.deviceSearchQuery) {
            const deviceSearchQuery = searchQuery as DeviceSearchQuery;
            deviceSearchQuery.deviceTypes = filter.deviceTypes;
            findByQueryObservable = this.deviceService.findByQuery(deviceSearchQuery, {ignoreLoading: true, ignoreErrors: true});
          } else if (filter.type === AliasFilterType.entityViewSearchQuery) {
            const entityViewSearchQuery = searchQuery as EntityViewSearchQuery;
            entityViewSearchQuery.entityViewTypes = filter.entityViewTypes;
            findByQueryObservable = this.entityViewService.findByQuery(entityViewSearchQuery, {ignoreLoading: true, ignoreErrors: true});
          }
          return findByQueryObservable.pipe(
            map((entities) => {
              if (entities && entities.length || !failOnEmpty) {
                if (isDefined(maxItems) && maxItems > 0 && entities) {
                  const limit = Math.min(entities.length, maxItems);
                  entities.length = limit;
                }
                result.entities = this.entitiesToEntitiesInfo(entities);
                return result;
              } else {
                throw Error();
              }
            })
          );
        } else {
          return of(result);
        }
    }
  }

  public checkEntityAlias(entityAlias: EntityAlias): Observable<boolean> {
    return this.resolveAliasFilter(entityAlias.filter, null, 1, true).pipe(
      map((result) => {
        if (result.stateEntity) {
          return true;
        } else {
          const entities = result.entities;
          if (entities && entities.length) {
            return true;
          } else {
            return false;
          }
        }
      }),
      catchError(err => of(false))
    );
  }

  public saveEntityParameters(customerId: CustomerId, entityType: EntityType, entityGroupId: string,
                              entityData: ImportEntityData, update: boolean,
                              config?: RequestConfig): Observable<ImportEntitiesResultInfo> {
    let saveEntityObservable: Observable<BaseData<EntityId>>;
    switch (entityType) {
      case EntityType.DEVICE:
        const device: Device = {
          name: entityData.name,
          type: entityData.type,
          label: entityData.label,
          customerId,
          additionalInfo: {
            description: entityData.description
          }
        };
        if (entityData.gateway !== null) {
          device.additionalInfo = {
            ...device.additionalInfo,
            gateway: entityData.gateway
          };
        }
        saveEntityObservable = this.deviceService.saveDevice(device, entityGroupId, config);
        break;
      case EntityType.ASSET:
        const asset: Asset = {
          name: entityData.name,
          type: entityData.type,
          label: entityData.label,
          customerId,
          additionalInfo: {
            description: entityData.description
          }
        };
        saveEntityObservable = this.assetService.saveAsset(asset, entityGroupId, config);
        break;
    }
    return saveEntityObservable.pipe(
      mergeMap((entity) => {
        return this.saveEntityData(entity.id, entityData, config).pipe(
          map(() => {
            return { create: { entity: 1 } } as ImportEntitiesResultInfo;
          }),
          catchError(err => of({ error: { entity: 1 } } as ImportEntitiesResultInfo))
        );
      }),
      catchError(err => {
        if (update) {
          let findEntityObservable: Observable<BaseData<EntityId>>;
          switch (entityType) {
            case EntityType.DEVICE:
              findEntityObservable = this.deviceService.findByName(entityData.name, config);
              break;
            case EntityType.ASSET:
              findEntityObservable = this.assetService.findByName(entityData.name, config);
              break;
          }
          return findEntityObservable.pipe(
            mergeMap((entity) => {
              const tasks: Observable<any>[] = [];
              const result: Device | Asset = entity as (Device | Asset);
              const additionalInfo = result.additionalInfo || {};
              if(result.label !== entityData.label ||
                 result.type !== entityData.type ||
                 additionalInfo.description !== entityData.description ||
                 (result.id.entityType === EntityType.DEVICE && (additionalInfo.gateway !== entityData.gateway)) ) {
                result.label = entityData.label;
                result.type = entityData.type;
                result.additionalInfo = additionalInfo;
                result.additionalInfo.description = entityData.description;
                if (result.id.entityType === EntityType.DEVICE) {
                  result.additionalInfo.gateway = entityData.gateway;
                }
                switch (result.id.entityType) {
                  case EntityType.DEVICE:
                    tasks.push(this.deviceService.saveDevice(result, entityGroupId, config));
                    break;
                  case EntityType.ASSET:
                    tasks.push(this.assetService.saveAsset(result, entityGroupId, config));
                    break;
                }
              }
              tasks.push(this.saveEntityData(entity.id, entityData, config));
              return forkJoin(tasks).pipe(
                map(() => {
                  return { update: { entity: 1 } } as ImportEntitiesResultInfo;
                }),
                catchError(updateError => of({ error: { entity: 1 } } as ImportEntitiesResultInfo))
              );
            }),
            catchError(findErr => of({ error: { entity: 1 } } as ImportEntitiesResultInfo))
          );
        } else {
          return of({ error: { entity: 1 } } as ImportEntitiesResultInfo);
        }
      })
    );
  }

  public saveEntityData(entityId: EntityId, entityData: ImportEntityData, config?: RequestConfig): Observable<any> {
    const observables: Observable<string>[] = [];
    let observable: Observable<string>;
    if (entityData.accessToken && entityData.accessToken !== '') {
      observable = this.deviceService.getDeviceCredentials(entityId.id, false, config).pipe(
        mergeMap((credentials) => {
          credentials.credentialsId = entityData.accessToken;
          credentials.credentialsType = DeviceCredentialsType.ACCESS_TOKEN;
          credentials.credentialsValue = null;
          return this.deviceService.saveDeviceCredentials(credentials, config).pipe(
            map(() => 'ok'),
            catchError(err => of('error'))
          );
        })
      );
      observables.push(observable);
    }
    if (entityData.attributes.shared && entityData.attributes.shared.length) {
      observable = this.attributeService.saveEntityAttributes(entityId, AttributeScope.SHARED_SCOPE,
        entityData.attributes.shared, config).pipe(
        map(() => 'ok'),
        catchError(err => of('error'))
      );
      observables.push(observable);
    }
    if (entityData.attributes.server && entityData.attributes.server.length) {
      observable = this.attributeService.saveEntityAttributes(entityId, AttributeScope.SERVER_SCOPE,
        entityData.attributes.server, config).pipe(
        map(() => 'ok'),
        catchError(err => of('error'))
      );
      observables.push(observable);
    }
    if (entityData.timeseries && entityData.timeseries.length) {
      observable = this.attributeService.saveEntityTimeseries(entityId, 'time', entityData.timeseries, config).pipe(
        map(() => 'ok'),
        catchError(err => of('error'))
      );
      observables.push(observable);
    }
    return forkJoin(observables).pipe(
      map((response) => {
        const hasError = response.filter((status) => status === 'error').length > 0;
        if (hasError) {
          throw Error();
        } else {
          return response;
        }
      })
    );
  }

  private entitiesToEntitiesInfo(entities: Array<BaseData<EntityId>>): Array<EntityInfo> {
    const entitiesInfo = [];
    if (entities) {
      entities.forEach((entity) => {
        entitiesInfo.push(this.entityToEntityInfo(entity));
      });
    }
    return entitiesInfo;
  }

  private entityToEntityInfo(entity: BaseData<EntityId>): EntityInfo {
    return {
      origEntity: entity,
      name: entity.name,
      label: (entity as any).label ? (entity as any).label : '',
      entityType: entity.id.entityType as EntityType,
      id: entity.id.id,
      entityDescription: (entity as any).additionalInfo ? (entity as any).additionalInfo.description : ''
    };
  }

  private entityRelationInfosToEntitiesInfo(entityRelations: Array<EntityRelationInfo>,
                                            direction: EntitySearchDirection): Observable<Array<EntityInfo>> {
    if (entityRelations.length) {
      const packs: Observable<EntityInfo>[][] = [];
      let packTasks: Observable<EntityInfo>[] = [];
      entityRelations.forEach((entityRelation) => {
        packTasks.push(this.entityRelationInfoToEntityInfo(entityRelation, direction));
        if (packTasks.length === 100) {
          packs.push(packTasks);
          packTasks = [];
        }
      });
      if (packTasks.length) {
        packs.push(packTasks);
      }
      return this.executePack(packs, 0);
    } else {
      return of([]);
    }
  }

  private executePack(packs: Observable<EntityInfo>[][], index: number): Observable<Array<EntityInfo>> {
    return forkJoin(packs[index]).pipe(
      expand(() => {
        index++;
        if (packs[index]) {
          return forkJoin(packs[index]);
        } else {
          return EMPTY;
        }
       }
      ),
      concatMap((data) => data),
      toArray()
    );
  }

  private entityRelationInfoToEntityInfo(entityRelationInfo: EntityRelationInfo, direction: EntitySearchDirection): Observable<EntityInfo> {
    const entityId = direction === EntitySearchDirection.FROM ? entityRelationInfo.to : entityRelationInfo.from;
    return this.getEntity(entityId.entityType as EntityType, entityId.id, {ignoreLoading: true, ignoreErrors: true}).pipe(
      map((entity) => {
        return this.entityToEntityInfo(entity);
      })
    );
  }

  private getStateEntityInfo(filter: EntityAliasFilter, stateParams: StateParams): {entityId: EntityId, entityGroupType: EntityType} {
    let entityId: EntityId = null;
    let entityGroupType: EntityType = null;
    if (stateParams) {
      if (filter.stateEntityParamName && filter.stateEntityParamName.length) {
        if (stateParams[filter.stateEntityParamName]) {
          entityId = stateParams[filter.stateEntityParamName].entityId;
          entityGroupType = stateParams[filter.stateEntityParamName].entityGroupType;
        }
      } else {
        entityId = stateParams.entityId;
        entityGroupType = stateParams.entityGroupType;
      }
    }
    if (!entityId) {
      if (filter.type === AliasFilterType.entityGroup && filter.defaultStateEntityGroup) {
        entityId = {
          entityType: EntityType.ENTITY_GROUP,
          id: filter.defaultStateEntityGroup
        };
        entityGroupType = filter.defaultStateGroupType;
      } else {
        entityId = filter.defaultStateEntity;
      }
    }
    if (entityId) {
      entityId = this.resolveAliasEntityId(entityId.entityType, entityId.id);
    }
    return {entityId, entityGroupType};
  }

  private resolveAliasEntityId(entityType: EntityType | AliasEntityType, id: string): EntityId {
    const entityId: EntityId = {
      entityType,
      id
    };
    if (entityType === AliasEntityType.CURRENT_CUSTOMER) {
      const authUser = getCurrentAuthUser(this.store);
      entityId.entityType = EntityType.CUSTOMER;
      if (authUser.authority === Authority.CUSTOMER_USER) {
        entityId.id = authUser.customerId;
      }
    } else if (entityType === AliasEntityType.CURRENT_TENANT){
      const authUser =  getCurrentAuthUser(this.store);
      entityId.entityType = EntityType.TENANT;
      entityId.id = authUser.tenantId;
    }
    return entityId;
  }

  private createDatasourcesFromSubscriptionInfo(subscriptionInfo: SubscriptionInfo): Observable<Array<Datasource>> {
    subscriptionInfo = this.validateSubscriptionInfo(subscriptionInfo);
    if (subscriptionInfo.type === DatasourceType.entity) {
      return this.resolveEntitiesFromSubscriptionInfo(subscriptionInfo).pipe(
        map((entities) => {
          const datasources = new Array<Datasource>();
          entities.forEach((entity) => {
            datasources.push(this.createDatasourceFromSubscription(subscriptionInfo, entity));
          });
          return datasources;
        })
      );
    } else if (subscriptionInfo.type === DatasourceType.function) {
      return of([this.createDatasourceFromSubscription(subscriptionInfo)]);
    } else {
      return of([]);
    }
  }

  private resolveEntitiesFromSubscriptionInfo(subscriptionInfo: SubscriptionInfo): Observable<Array<BaseData<EntityId>>> {
    if (subscriptionInfo.entityId) {
      if (subscriptionInfo.entityName) {
        const entity: BaseData<EntityId> = {
          id: {id: subscriptionInfo.entityId, entityType: subscriptionInfo.entityType},
          name: subscriptionInfo.entityName
        };
        return of([entity]);
      } else {
        return this.getEntity(subscriptionInfo.entityType, subscriptionInfo.entityId,
          {ignoreLoading: true, ignoreErrors: true}).pipe(
          map((entity) => [entity]),
          catchError(e => of([]))
        );
      }
    } else if (subscriptionInfo.entityName || subscriptionInfo.entityNamePrefix || subscriptionInfo.entityIds) {
      let entitiesObservable: Observable<Array<BaseData<EntityId>>>;
      if (subscriptionInfo.entityName) {
        entitiesObservable = this.getEntitiesByNameFilter(subscriptionInfo.entityType, subscriptionInfo.entityName,
          1, null, {ignoreLoading: true, ignoreErrors: true});
      } else if (subscriptionInfo.entityNamePrefix) {
        entitiesObservable = this.getEntitiesByNameFilter(subscriptionInfo.entityType, subscriptionInfo.entityNamePrefix,
          100, null, {ignoreLoading: true, ignoreErrors: true});
      } else if (subscriptionInfo.entityIds) {
        entitiesObservable = this.getEntities(subscriptionInfo.entityType, subscriptionInfo.entityIds,
          {ignoreLoading: true, ignoreErrors: true});
      }
      return entitiesObservable.pipe(
        catchError(e => of([]))
      );
    } else {
      return of([]);
    }
  }

  private validateSubscriptionInfo(subscriptionInfo: SubscriptionInfo): SubscriptionInfo {
    // @ts-ignore
    if (subscriptionInfo.type === 'device') {
      subscriptionInfo.type = DatasourceType.entity;
      subscriptionInfo.entityType = EntityType.DEVICE;
      if (subscriptionInfo.deviceId) {
        subscriptionInfo.entityId = subscriptionInfo.deviceId;
      } else if (subscriptionInfo.deviceName) {
        subscriptionInfo.entityName = subscriptionInfo.deviceName;
      } else if (subscriptionInfo.deviceNamePrefix) {
        subscriptionInfo.entityNamePrefix = subscriptionInfo.deviceNamePrefix;
      } else if (subscriptionInfo.deviceIds) {
        subscriptionInfo.entityIds = subscriptionInfo.deviceIds;
      }
    }
    return subscriptionInfo;
  }

  private createDatasourceFromSubscription(subscriptionInfo: SubscriptionInfo, entity?: BaseData<EntityId>): Datasource {
    let datasource: Datasource;
    if (subscriptionInfo.type === DatasourceType.entity) {
      datasource = {
        type: subscriptionInfo.type,
        entityName: entity.name,
        name: entity.name,
        entityType: subscriptionInfo.entityType,
        entityId: entity.id.id,
        dataKeys: []
      };
    } else if (subscriptionInfo.type === DatasourceType.function) {
      datasource = {
        type: subscriptionInfo.type,
        name: subscriptionInfo.name || DatasourceType.function,
        dataKeys: []
      };
    }
    if (subscriptionInfo.timeseries) {
      this.createDatasourceKeys(subscriptionInfo.timeseries, DataKeyType.timeseries, datasource);
    }
    if (subscriptionInfo.attributes) {
      this.createDatasourceKeys(subscriptionInfo.attributes, DataKeyType.attribute, datasource);
    }
    if (subscriptionInfo.functions) {
      this.createDatasourceKeys(subscriptionInfo.functions, DataKeyType.function, datasource);
    }
    if (subscriptionInfo.alarmFields) {
      this.createDatasourceKeys(subscriptionInfo.alarmFields, DataKeyType.alarm, datasource);
    }
    return datasource;
  }

  private createDatasourceKeys(keyInfos: Array<KeyInfo>, type: DataKeyType, datasource: Datasource) {
    keyInfos.forEach((keyInfo) => {
      const dataKey = this.utils.createKey(keyInfo, type);
      datasource.dataKeys.push(dataKey);
    });
  }
}
