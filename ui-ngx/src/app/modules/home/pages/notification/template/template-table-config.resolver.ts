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

import {
  CellActionDescriptor,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { EntityType, EntityTypeResource, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Direction } from '@shared/models/page/sort-order';
import { NotificationTemplate, NotificationTemplateTypeTranslateMap } from '@shared/models/notification.models';
import { NotificationService } from '@core/http/notification.service';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { MatDialog } from '@angular/material/dialog';
import {
  TemplateNotificationDialogComponent,
  TemplateNotificationDialogData
} from '@home/pages/notification/template/template-notification-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { TemplateTableHeaderComponent } from '@home/pages/notification/template/template-table-header.component';
import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { DatePipe } from '@angular/common';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { Operation, Resource } from '@shared/models/security.models';

@Injectable()
export class TemplateTableConfigResolver implements Resolve<EntityTableConfig<NotificationTemplate>> {

  private readonly config: EntityTableConfig<NotificationTemplate> = new EntityTableConfig<NotificationTemplate>();

  constructor(private notificationService: NotificationService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private datePipe: DatePipe,
              private userPermissionsService: UserPermissionsService) {

    this.config.entityType = EntityType.NOTIFICATION_TEMPLATE;
    this.config.detailsPanelEnabled = false;
    this.config.addEnabled = false;
    this.config.rowPointer = true;

    this.config.entityTranslations = entityTypeTranslations.get(EntityType.NOTIFICATION_TEMPLATE);
    this.config.entityResources = {} as EntityTypeResource<NotificationTemplate>;

    this.config.entitiesFetchFunction = pageLink => this.notificationService.getNotificationTemplates(pageLink);

    this.config.deleteEntityTitle = template => this.translate.instant('notification.delete-template-title', {templateName: template.name});
    this.config.deleteEntityContent = () => this.translate.instant('notification.delete-template-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('notification.delete-templates-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('notification.delete-templates-text');
    this.config.deleteEntity = id => this.notificationService.deleteNotificationTemplate(id.id);

    this.config.cellActionDescriptors = this.configureCellActions();

    this.config.headerComponent = TemplateTableHeaderComponent;
    this.config.onEntityAction = action => this.onTemplateAction(action);

    this.config.deleteEnabled = () => this.userPermissionsService.hasGenericPermission(Resource.NOTIFICATION, Operation.WRITE);
    this.config.entitySelectionEnabled = () => this.userPermissionsService.hasGenericPermission(Resource.NOTIFICATION, Operation.WRITE);

    this.config.defaultSortOrder = {property: 'createdTime', direction: Direction.DESC};

    this.config.handleRowClick = ($event, template) => {
      this.editTemplate($event, template);
      return true;
    };

    this.config.columns.push(
      new DateEntityTableColumn<NotificationTemplate>('createdTime', 'common.created-time', this.datePipe, '170px'),
      new EntityTableColumn<NotificationTemplate>('notificationType', 'notification.type', '20%',
        (template) => this.translate.instant(NotificationTemplateTypeTranslateMap.get(template.notificationType).name)),
      new EntityTableColumn<NotificationTemplate>('name', 'notification.template', '80%')
    );
  }

  resolve(route: ActivatedRouteSnapshot): EntityTableConfig<NotificationTemplate> {
    return this.config;
  }

  private configureCellActions(): Array<CellActionDescriptor<NotificationTemplate>> {
    return [
      {
        name: this.translate.instant('notification.copy-template'),
        icon: 'content_copy',
        isEnabled: () => this.userPermissionsService.hasGenericPermission(Resource.NOTIFICATION, Operation.WRITE),
        onAction: ($event, entity) => this.editTemplate($event, entity, false, true)
      }
    ];
  }

  private editTemplate($event: Event, template: NotificationTemplate, isAdd = false, isCopy = false) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialog.open<TemplateNotificationDialogComponent, TemplateNotificationDialogData,
      NotificationTemplate>(TemplateNotificationDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        isAdd,
        isCopy,
        template,
        readonly: !this.userPermissionsService.hasGenericPermission(Resource.NOTIFICATION, Operation.WRITE)
      }
    }).afterClosed()
      .subscribe((res) => {
        if (res) {
          this.config.updateData();
        }
      });
  }

  private onTemplateAction(action: EntityAction<NotificationTemplate>): boolean {
    switch (action.action) {
      case 'add':
        this.editTemplate(action.event, action.entity, true);
        return true;
    }
    return false;
  }

}
