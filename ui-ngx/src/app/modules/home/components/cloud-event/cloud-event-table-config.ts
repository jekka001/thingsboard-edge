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

import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import {EntityType, EntityTypeResource } from '@shared/models/entity-type.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { Direction } from '@shared/models/page/sort-order';
import { MatDialog } from '@angular/material/dialog';
import { TimePageLink } from '@shared/models/page/page-link';
import { EntityId } from '@shared/models/id/entity-id';
import { UtilsService } from '@core/services/utils.service';
import { EdgeService } from "@core/http/edge.service";
import {
  CloudEvent, cloudEventActionTypeTranslations,
  CloudEventType,
  cloudEventTypeTranslations,
  EdgeEventStatus,
  edgeEventStatusColor
} from "@shared/models/edge.models";
import { getCurrentAuthUser } from "@core/auth/auth.selectors";
import { AttributeScope } from "@shared/models/telemetry/telemetry.models";
import { Store } from "@ngrx/store";
import { AppState } from "@core/core.state";
import { AttributeService } from "@core/http/attribute.service";
import { CloudEventDetailsDialogComponent } from "@home/components/cloud-event/cloud-event-details-dialog.component";

export class CloudEventTableConfig extends EntityTableConfig<CloudEvent, TimePageLink> {

  queueStartTs: number = 0;

  constructor(private translate: TranslateService,
              private utils: UtilsService,
              private datePipe: DatePipe,
              private dialog: MatDialog,
              private edgeService: EdgeService,
              private store: Store<AppState>,
              private attributeService: AttributeService,
              updateOnInit = true) {
    super();
    this.loadDataOnInit = updateOnInit;
    this.tableTitle = '';
    this.useTimePageLink = true;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.searchEnabled = false;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.actionsColumnTitle = 'cloud-event.details';
    this.entityTranslations = {
      noEntities: 'cloud-event.no-cloud-events-prompt',
      search: 'cloud-event.search'
    };
    this.entityResources = {
    } as EntityTypeResource<CloudEvent>;

    this.entitiesFetchFunction = pageLink => this.edgeService.getCloudEvents(pageLink);

    this.defaultSortOrder = {property: 'createdTime', direction: Direction.DESC};

    this.columns.push(
      new DateEntityTableColumn<CloudEvent>('createdTime', 'cloud-event.created-time', this.datePipe, '150px'),
      new EntityTableColumn<CloudEvent>('cloudEventAction', 'cloud-event.action', '20%',
        entity => this.translate.instant(cloudEventActionTypeTranslations.get(entity.cloudEventAction)), entity => ({}), false),
      new EntityTableColumn<CloudEvent>('cloudEventType', 'cloud-event.entity-type', '20%',
        entity => this.translate.instant(cloudEventTypeTranslations.get(entity.cloudEventType)), entity => ({}), false),
      new EntityTableColumn<CloudEvent>('entityId', 'cloud-event.entity-id', '30%'),
      new EntityTableColumn<CloudEvent>('status', 'event.status', '20%',
        (entity) => this.updateEdgeEventStatus(entity.createdTime),
        entity => ({
          color: this.isPending(entity.createdTime) ? edgeEventStatusColor.get(EdgeEventStatus.PENDING) : edgeEventStatusColor.get(EdgeEventStatus.DEPLOYED)
        }), false),
      );

    this.cellActionDescriptors.push(
      {
        name: this.translate.instant('cloud-event.details'),
        icon: 'more_horiz',
        isEnabled: () => true,
        onAction: ($event, entity) => this.showCloudEventDetails(entity)
      }
    );

    this.loadEdgeStatus();
  }

  showCloudEventDetails(entity: CloudEvent) {
    this.dialog.open<CloudEventDetailsDialogComponent, CloudEventDetailsDialogComponent>(CloudEventDetailsDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      // @ts-ignore
      data: {
        cloudEvent: entity
      }
    });
  }

  isPending(createdTime) {
    return createdTime > this.queueStartTs;
  }

  updateEdgeEventStatus(createdTime) {
    if (this.queueStartTs && createdTime < this.queueStartTs) {
      return this.translate.instant('edge.deployed');
    } else {
      return this.translate.instant('edge.pending');
    }
  }

  loadEdgeStatus() {
    const authUser = getCurrentAuthUser(this.store);
    const currentTenant: EntityId = {
      id: authUser.tenantId,
      entityType: EntityType.TENANT
    }
    this.attributeService.getEntityAttributes(currentTenant, AttributeScope.SERVER_SCOPE, ['queueStartTs'])
      .subscribe(attributes => this.queueStartTs = attributes[0].lastUpdateTs);
  }
}
