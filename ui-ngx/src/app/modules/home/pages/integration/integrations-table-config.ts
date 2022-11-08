///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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

import {
  CellActionDescriptor,
  ChartEntityTableColumn,
  checkBoxCell,
  DateEntityTableColumn,
  defaultEntityTablePermissions,
  EntityColumn,
  EntityTableColumn,
  EntityTableConfig,
  GroupActionDescriptor,
  HeaderActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import {
  getIntegrationHelpLink,
  Integration,
  IntegrationBasic,
  IntegrationInfo,
  IntegrationParams,
  integrationTypeInfoMap
} from '@shared/models/integration.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { UtilsService } from '@core/services/utils.service';
import { Router, UrlTree } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { IntegrationService } from '@core/http/integration.service';
import { EdgeService } from '@core/http/edge.service';
import { DialogService } from '@core/services/dialog.service';
import { EntityType, entityTypeTranslations } from '@shared/models/entity-type.models';
import { IntegrationComponent } from '@home/pages/integration/integration.component';
import { IntegrationTabsComponent } from '@home/pages/integration/integration-tabs.component';
import { Operation, Resource } from '@shared/models/security.models';
import { forkJoin, Observable } from 'rxjs';
import { isUndefined } from '@core/utils';
import { map, mergeMap } from 'rxjs/operators';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { PageData } from '@shared/models/page/page-data';
import { Edge } from '@shared/models/edge.models';
import {
  AddEntitiesToEdgeDialogComponent,
  AddEntitiesToEdgeDialogData
} from '@home/dialogs/add-entities-to-edge-dialog.component';
import { PageLink } from '@shared/models/page/page-link';
import {
  IntegrationWizardData,
  IntegrationWizardDialogComponent
} from '@home/components/wizard/integration-wizard-dialog.component';
import { EventType } from '@shared/models/event.models';

export class IntegrationsTableConfig extends EntityTableConfig<Integration, PageLink, IntegrationInfo> {

  constructor(private integrationService: IntegrationService,
              private userPermissionsService: UserPermissionsService,
              private edgeService: EdgeService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private router: Router,
              private utils: UtilsService,
              private dialogService: DialogService,
              private dialog: MatDialog,
              private params: IntegrationParams) {
    super();

    this.entityType = EntityType.INTEGRATION;
    this.entityComponent = IntegrationComponent;
    this.entityTabsComponent = IntegrationTabsComponent;
    this.entityTranslations = entityTypeTranslations.get(EntityType.INTEGRATION);
    this.entityResources = {
      helpLinkId: null,
      helpLinkIdForEntity(entity: Integration): string {
        return getIntegrationHelpLink(entity);
      }
    };
    this.addDialogStyle = {width: '800px'};

    this.componentsData = params;

    this.deleteEntityTitle = integration =>
      this.translate.instant('integration.delete-integration-title', { integrationName: integration.name });
    this.deleteEntityContent = () => this.translate.instant('integration.delete-integration-text');
    this.deleteEntitiesTitle = count => this.translate.instant('integration.delete-integrations-title', {count});
    this.deleteEntitiesContent = () => this.translate.instant('integration.delete-integrations-text');

    this.tableTitle = this.translate.instant('integration.integrations');

    this.columns = this.configureEntityTableColumns();
    this.groupActionDescriptors = this.configureGroupActions(this.componentsData.integrationScope);
    this.addActionDescriptors = this.configureAddActions(this.componentsData.integrationScope);
    this.cellActionDescriptors = this.configureCellActions(this.componentsData);

    this.loadEntity = id => this.integrationService.getIntegration(id.id);
    this.entitiesFetchFunction = this.configureEntityFunctions(this.componentsData.integrationScope, this.componentsData.edgeId);
    this.saveEntity = integration => this.saveIntegration(integration);
    this.deleteEntity = id => this.integrationService.deleteIntegration(id.id);

    this.onEntityAction = action => this.onIntegrationAction(action, this.componentsData);

    this.handleRowClick = (event, entity) => {
      this.getTable().toggleEntityDetails(event, entity);
      if ((event.target as HTMLElement).getElementsByClassName('status').length || (event.target as HTMLElement).className === 'status') {
        setTimeout(() => {
          this.getTable().entityDetailsPanel.matTabGroup.selectedIndex = 1;
          (this.getTable().entityDetailsPanel.entityTabsComponent as any).defaultEventType = EventType.LC_EVENT;
        }, 0);
      } else {
        (this.getTable().entityDetailsPanel.entityTabsComponent as any).defaultEventType = '';
      }
      return true;
    };

    this.configureIntegrationScope();

    this.addEntity = () => this.addIntegration();

    defaultEntityTablePermissions(this.userPermissionsService, this);
  }


  private configureEntityTableColumns(): Array<EntityColumn<IntegrationInfo>> {
    const columns: Array<EntityColumn<IntegrationInfo>> = [];

    this.entityTitle = (integration) => integration ?
      this.utils.customTranslation(integration.name, integration.name) : '';

    columns.push(
      new DateEntityTableColumn<IntegrationInfo>('createdTime', 'common.created-time', this.datePipe, '15%'),
      new EntityTableColumn<IntegrationInfo>('name', 'converter.name', '15%', this.entityTitle),
      new EntityTableColumn<IntegrationInfo>('type', 'converter.type', '51%', (integration) => {
        return this.translate.instant(integrationTypeInfoMap.get(integration.type).name);
      }),
      new ChartEntityTableColumn<IntegrationInfo>('dailyRate', 'integration.daily-activity', '9%',
        (integration) => integration.stats,
        () => ({
          chartRangeMin: '',
          chartRangeMax: '',
          minSpotColor: false,
          maxSpotColor: false,
          spotColor: false,
          height: '36px',
          width: '72px',
          fillColor: false,
          lineColor: 'rgba(0, 0, 0, 0.54)',
          lineWidth: '2'
        })),
      new EntityTableColumn<IntegrationInfo>('status', 'integration.status.status', '80px',
        integration => this.integrationStatus(integration),
        integration => this.integrationStatusStyle(integration), false),
      new EntityTableColumn<IntegrationInfo>('remote', 'integration.remote', '60px',
        integration => {
            return checkBoxCell(integration.remote);
        }, () => ({}), false)
    );
    return columns;
  }

  private configureGroupActions(integrationScope: string): Array<GroupActionDescriptor<IntegrationInfo>> {
    const actions: Array<GroupActionDescriptor<IntegrationInfo>> = [];
    if (integrationScope === 'edge') {
      actions.push(
        {
          name: this.translate.instant('integration.unassign-integrations'),
          icon: 'assignment_return',
          isEnabled: this.userPermissionsService.hasGenericPermission(Resource.EDGE, Operation.WRITE),
          onAction: ($event, entities) => this.unassignIntegrationsFromEdge($event, entities)
        }
      );
    }
    return actions;
  }

  private configureAddActions(integrationScope: string): Array<HeaderActionDescriptor> {
    const actions: Array<HeaderActionDescriptor> = [];
    if (integrationScope === 'tenant' || integrationScope === 'edges') {
      actions.push(
        {
          name: this.translate.instant('integration.add'),
          icon: 'add',
          isEnabled: () => true,
          onAction: ($event) => this.getTable().addEntity($event)
        }
      );
    }
    if (integrationScope === 'edge') {
      actions.push(
        {
          name: this.translate.instant('edge.assign-to-edge'),
          icon: 'add',
          isEnabled: () => true,
          onAction: ($event) => this.assignIntegrationsToEdge($event)
        }
      );
    }
    return actions;
  }

  private configureCellActions(params: IntegrationParams): Array<CellActionDescriptor<IntegrationInfo>> {
    const actions: Array<CellActionDescriptor<IntegrationInfo>> = [];
    if (params.integrationScope === 'edge') {
      actions.push(
        {
          name: this.translate.instant('edge.unassign-from-edge'),
          icon: 'assignment_return',
          isEnabled: () => true,
          onAction: ($event, entity) => this.unassignFromEdge($event, entity)
        }
      );
    }
    actions.push(
      {
        name: '',
        nameFunction: (entity) => this.translate.instant(entity.debugMode ? 'integration.disable-debug-mode' : 'integration.enable-debug-mode'),
        mdiIcon: 'mdi:bug',
        isEnabled: () => true,
        mdiIconFunction: (entity) => entity.debugMode ? 'mdi:bug' : 'mdi:bug-outline',
        onAction: ($event, entity) => this.toggleDebugMode($event, entity)
      }
    );
    return actions;
  }

  private saveIntegration(integration: Integration): Observable<Integration> {
    if (isUndefined(integration.edgeTemplate)) {
      if (this.componentsData.integrationScope === 'tenant') {
        integration.edgeTemplate = false;
      } else if (this.componentsData.integrationScope === 'edges') {
        integration.edgeTemplate = true;
      } else {
        // safe fallback to default
        integration.edgeTemplate = false;
      }
    }
    return this.integrationService.saveIntegration(integration).pipe(
      map((integrationInfo) => {
        if (this.componentsData.integrationScope === 'edges') {
          this.edgeService.findAllRelatedEdgesMissingAttributes(integrationInfo.id.id).subscribe(
            (missingEdgeAttributes) => {
              if (missingEdgeAttributes && Object.keys(missingEdgeAttributes).length > 0) {
                const formattedMissingEdgeAttributes: Array<string> = new Array<string>();
                for (const missingEdgeAttribute of Object.keys(missingEdgeAttributes)) {
                  const arrayOfMissingAttributes = missingEdgeAttributes[missingEdgeAttribute];
                  const tmp = '- \'' + missingEdgeAttribute + '\': \'' + arrayOfMissingAttributes.join('\', \'') + '\'';
                  formattedMissingEdgeAttributes.push(tmp);
                }
                const message = this.translate.instant('edge.missing-all-related-attributes-text',
                  {missingEdgeAttributes: formattedMissingEdgeAttributes.join('<br>')});
                this.dialogService.alert(this.translate.instant('edge.missing-all-related-attributes-title'),
                  message, this.translate.instant('action.close'), true);
              }
            }
          );
        }
        return integrationInfo;
      })
    );
  }

  openIntegration($event: Event, integration: Integration, params?: IntegrationParams) {
    if ($event) {
      $event.stopPropagation();
    }
    if (this.componentsData.integrationScope === 'edge') {
      let url: UrlTree;
      if (params && params.hierarchyView) {
        url = this.router.createUrlTree(['customerGroups', params.entityGroupId, params.customerId,
          'edgeGroups', params.childEntityGroupId, params.edgeId, 'integrations', integration.id.id]);
        window.open(window.location.origin + url, '_blank');
      } else {
        url = this.router.createUrlTree([integration.id.id], { relativeTo: this.getActivatedRoute() });
        this.router.navigateByUrl(url);
      }
    } else if (this.componentsData.integrationScope === 'edges') {
      this.router.navigateByUrl(`edgeManagement/integrations/${integration.id.id}`);
    } else {
      this.router.navigateByUrl(`integrations/${integration.id.id}`);
    }
  }

  onIntegrationAction(action: EntityAction<Integration>, params: IntegrationParams): boolean {
    switch (action.action) {
      case 'open':
        this.openIntegration(action.event, action.entity, params);
        return true;
      case 'unassignFromEdge':
        this.unassignFromEdge(action.event, action.entity);
        return true;
    }
    return false;
  }

  private configureEntityFunctions(integrationScope: string, edgeId: string): (pageLink) => Observable<PageData<IntegrationInfo>> {
    if (integrationScope === 'tenant') {
      return pageLink => this.integrationService.getIntegrationsInfo(pageLink, false);
    } else if (integrationScope === 'edges') {
      return pageLink => this.integrationService.getIntegrationsInfo(pageLink, true);
    } else if (integrationScope === 'edge') {
      return pageLink => this.integrationService.getEdgeIntegrations(edgeId, pageLink);
    }
  }

  private configureIntegrationScope(): void {
    if (this.componentsData.integrationScope === 'tenant' || this.componentsData.integrationScope === 'edges') {
      this.tableTitle = this.configureTableTitle(this.componentsData.integrationScope, null);
    } else if (this.componentsData.integrationScope === 'edge') {
      this.edgeService.getEdge(this.componentsData.edgeId).subscribe(edge => {
        this.componentsData.edge = edge;
        this.tableTitle = this.configureTableTitle(this.componentsData.integrationScope, edge);
      });
      this.addEnabled = false;
      this.entitiesDeleteEnabled = false;
      this.deleteEnabled = () => false;
      this.detailsReadonly = () => true;
      this.headerActionDescriptors = [];
      if (this.userPermissionsService.hasGenericPermission(Resource.EDGE, Operation.WRITE)) {
        this.headerActionDescriptors.push({
            name: this.translate.instant('edge.assign-to-edge'),
            icon: 'add',
            isEnabled: () => true,
            onAction: ($event) => {
              this.assignIntegrationsToEdge($event);
            }
          }
        );
      }
    }
  }

  private configureTableTitle(integrationScope: string, edge: Edge): string {
    if (integrationScope === 'tenant') {
      return this.translate.instant('integration.integrations');
    } else if (integrationScope === 'edges') {
      return this.translate.instant('edge.integration-templates');
    } else if (integrationScope === 'edge') {
      return this.tableTitle = edge.name + ': ' + this.translate.instant('edge.integrations');
    }
  }

  private assignIntegrationsToEdge($event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<AddEntitiesToEdgeDialogComponent, AddEntitiesToEdgeDialogData,
      Array<string>>(AddEntitiesToEdgeDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        edgeId: this.componentsData.edgeId,
        entityType: EntityType.INTEGRATION
      }
    }).afterClosed()
      .subscribe((res) => {
          if (res) {
            this.edgeService.findEdgeMissingAttributes(res, this.componentsData.edgeId).subscribe(
              (missingEdgeAttributes) => {
                if (missingEdgeAttributes && Object.keys(missingEdgeAttributes).length > 0) {
                  const formattedMissingEdgeAttributes: Array<string> = new Array<string>();
                  for (const missingEdgeAttribute of Object.keys(missingEdgeAttributes)) {
                    const arrayOfMissingAttributes = missingEdgeAttributes[missingEdgeAttribute];
                    const tmp = '- \'' + missingEdgeAttribute + '\': \'' + arrayOfMissingAttributes.join('\', \'') + '\'';
                    formattedMissingEdgeAttributes.push(tmp);
                  }
                  const message = this.translate.instant('edge.missing-attributes-text',
                    {missingEdgeAttributes: formattedMissingEdgeAttributes.join('<br>')});
                  this.dialogService.alert(this.translate.instant('edge.missing-attributes-title'),
                    message, this.translate.instant('action.close'), true).subscribe(
                    () => {
                      this.updateData();
                    }
                  );
                } else {
                  this.updateData();
                }
              }
            );
          }
        }
      );
  }

  private unassignFromEdge($event: Event, integration: IntegrationBasic): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('integration.unassign-integration-title', {integrationName: integration.name}),
      this.translate.instant('integration.unassign-integration-from-edge-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.integrationService.unassignIntegrationFromEdge(this.componentsData.edgeId, integration.id.id).subscribe(
            () => {
              this.updateData(this.componentsData.integrationScope !== 'tenant');
            }
          );
        }
      }
    );
  }

  private unassignIntegrationsFromEdge($event: Event, integrations: Array<IntegrationInfo>): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('integration.unassign-integrations-from-edge-title', {count: integrations.length}),
      this.translate.instant('integration.unassign-integrations-from-edge-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          const tasks: Observable<any>[] = [];
          integrations.forEach(
            (integration) => {
              tasks.push(this.integrationService.unassignIntegrationFromEdge(this.componentsData.edgeId, integration.id.id));
            }
          );
          forkJoin(tasks).subscribe(
            () => {
              this.updateData();
            }
          );
        }
      }
    );
  }

  private addIntegration(): Observable<Integration> {
    return this.dialog.open<IntegrationWizardDialogComponent, IntegrationWizardData<IntegrationInfo>,
      Integration>(IntegrationWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entitiesTableConfig: this as any,
        edgeTemplate: this.componentsData.integrationScope === 'edges'
      }
    }).afterClosed();
  }

  private integrationStatus(integration: IntegrationInfo): string {
    let translateKey = 'integration.status.active';
    let backgroundColor = 'rgba(25, 128, 56, 0.08)';
    if (!integration.enabled) {
      translateKey = 'integration.status.disabled';
      backgroundColor = 'rgba(0, 0, 0, 0.08)';
    } else if (!integration.status) {
      translateKey = 'integration.status.disabled';
      backgroundColor = 'rgba(212, 125, 24, 0.08)';
    } else if (!integration.status.success) {
      translateKey = 'integration.status.failed';
      backgroundColor = 'rgba(209, 39, 48, 0.08)';
    }
    return `<div class="status" style="border-radius: 16px; height: 32px; line-height: 32px; padding: 0 12px; width: fit-content; background-color: ${backgroundColor}">
                ${this.translate.instant(translateKey)}
            </div>`;
  }

  private integrationStatusStyle(integration: IntegrationInfo): object {
    const styleObj = {
      fontSize: '14px',
      color: '#198038',
      cursor: 'pointer'
    };
    if (!integration.enabled) {
      styleObj.color = 'rgba(0, 0, 0, 0.54)';
    } else if (!integration.status) {
      styleObj.color = '#D47D18';
    } else if (!integration.status.success) {
      styleObj.color = '#d12730';
    }
    return styleObj;
  }

  private toggleDebugMode($event: Event, integrations: IntegrationInfo): void {
    if ($event) {
      $event.stopPropagation();
    }
    this.integrationService.getIntegration(integrations.id.id, {ignoreLoading: true})
      .pipe(
        mergeMap(integration => {
          integration.debugMode = !integration.debugMode;
          return this.integrationService.saveIntegration(integration, {ignoreLoading: true});
        }))
      .subscribe((integrationData) => {
        integrations.debugMode = integrationData.debugMode;
        this.getTable().detectChanges();
      });
  }
}
