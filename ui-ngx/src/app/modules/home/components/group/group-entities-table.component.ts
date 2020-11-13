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

import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Inject,
  Input,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { BaseData, HasId } from '@shared/models/base-data';
import { EntityGroupStateInfo, GroupEntityTableConfig } from '@home/models/group/group-entities-table-config.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import { Subscription } from 'rxjs';
import {
  EntityGroupInfo,
  EntityGroupParams,
  resolveGroupParams,
  ShortEntityView
} from '@shared/models/entity-group.models';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { DialogService } from '@core/services/dialog.service';
import { WINDOW } from '@core/services/window.service';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { EntityGroupService } from '@core/http/entity-group.service';
import { EntityGroupsTableConfig } from '@home/components/group/entity-groups-table-config';
import { EntityGroupsTableConfigResolver } from '@home/components/group/entity-groups-table-config.resolver';
import { EntityGroupConfigResolver } from '@home/components/group/entity-group-config.resolver';

// @dynamic
@Component({
  selector: 'tb-group-entities-table',
  templateUrl: './group-entities-table.component.html',
  styleUrls: ['./group-entities-table.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class GroupEntitiesTableComponent extends PageComponent implements AfterViewInit, OnInit, OnDestroy, OnChanges {

  isGroupDetailsOpen = false;

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  @Input()
  groupParams: EntityGroupParams;

  @Input()
  entityGroup: EntityGroupStateInfo<BaseData<HasId>>;

  entityGroupConfig: GroupEntityTableConfig<BaseData<HasId>>;

  entityGroupDetailsConfig: EntityGroupsTableConfig;

  private rxSubscriptions = new Array<Subscription>();

  constructor(protected store: Store<AppState>,
              @Inject(WINDOW) private window: Window,
              private route: ActivatedRoute,
              private entityGroupsTableConfigResolver: EntityGroupsTableConfigResolver,
              private entityGroupConfigResolver: EntityGroupConfigResolver,
              private entityGroupService: EntityGroupService,
              private userPermissionsService: UserPermissionsService,
              public translate: TranslateService,
              public dialog: MatDialog,
              private dialogService: DialogService,
              private cd: ChangeDetectorRef) {
    super(store);
  }


  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'entityGroup' && change.currentValue) {
          this.init(change.currentValue, this.groupParams);
        }
      }
    }
  }

  ngOnInit(): void {
    if (this.entityGroup) {
      this.init(this.entityGroup, this.groupParams);
    } else {
      this.rxSubscriptions.push(this.route.data.subscribe(
        (data) => {
          const groupParams = resolveGroupParams(this.route.snapshot);
          this.init(data.entityGroup, groupParams);
        }
      ));
    }
  }

  ngOnDestroy(): void {
    this.rxSubscriptions.forEach((subscription) => {
      subscription.unsubscribe();
    });
    this.rxSubscriptions.length = 0;
  }

  onToggleEntityGroupDetails() {
    this.isGroupDetailsOpen = !this.isGroupDetailsOpen;
    if (this.isGroupDetailsOpen) {
      this.entitiesTable.isDetailsOpen = false;
    }
  }

  onToggleEntityDetails($event: Event, entity: ShortEntityView) {
    this.entitiesTable.toggleEntityDetails($event, entity);
  }

  onEntityGroupUpdated(entity: BaseData<HasId>) {
    const entityGroup = entity as EntityGroupInfo;
    if (this.groupParams.hierarchyView) {
      this.groupParams.hierarchyCallbacks.groupUpdated(entityGroup);
    }
    this.reloadEntityGroupConfig(entityGroup);
  }

  onEntityGroupAction(action: EntityAction<BaseData<HasId>>) {
    if (action.action === 'delete') {
      this.deleteEntityGroup(action.event, action.entity as EntityGroupInfo);
    }
  }

  private reloadEntityGroup() {
    this.entityGroupService.getEntityGroup(this.entityGroup.id.id).subscribe(
      (entityGroup) => {
        this.reloadEntityGroupConfig(entityGroup);
      }
    );
  }

  private reloadEntityGroupConfig(entityGroup: EntityGroupInfo) {
    this.entityGroupConfigResolver.constructGroupConfig<BaseData<HasId>>(this.groupParams, entityGroup).subscribe(
      (entityGroupConfig) => {
        this.init(entityGroupConfig, this.groupParams, false);
      }
    );
  }

  private deleteEntityGroup($event: Event, entity: EntityGroupInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.entityGroupDetailsConfig.deleteEntityTitle(entity),
      this.entityGroupDetailsConfig.deleteEntityContent(entity),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((result) => {
      if (result) {
        this.entityGroupDetailsConfig.deleteEntity(entity.id).subscribe(
          () => {
            if (this.groupParams.hierarchyView) {
              this.groupParams.hierarchyCallbacks.groupDeleted(this.groupParams.nodeId, entity.id.id);
            } else {
              this.window.history.back();
            }
          }
        );
      }
    });
  }

  private init(entityGroup: EntityGroupStateInfo<BaseData<HasId>>, groupParams: EntityGroupParams, closeGroupDetails = true) {
    if (closeGroupDetails) {
      this.isGroupDetailsOpen = false;
    }
    this.entityGroup = entityGroup;
    this.groupParams = groupParams || resolveGroupParams(this.route.snapshot);
    this.entityGroupConfig = entityGroup.entityGroupConfig;
    this.entityGroupConfig.onToggleEntityGroupDetails = this.onToggleEntityGroupDetails.bind(this);
    this.entityGroupConfig.onToggleEntityDetails = this.onToggleEntityDetails.bind(this);
    this.entitiesTable.detailsPanelOpened.subscribe((isDetailsOpened: boolean) => {
      if (isDetailsOpened) {
        this.isGroupDetailsOpen = false;
      }
    });
    this.entityGroupDetailsConfig =
      this.entityGroupsTableConfigResolver.resolveEntityGroupTableConfig(this.groupParams, false) as EntityGroupsTableConfig;
    this.entityGroupDetailsConfig.componentsData = {
      isGroupEntitiesView: true,
      reloadEntityGroup: this.reloadEntityGroup.bind(this)
    };
    this.cd.detectChanges();
  }

  ngAfterViewInit(): void {
  }

}
