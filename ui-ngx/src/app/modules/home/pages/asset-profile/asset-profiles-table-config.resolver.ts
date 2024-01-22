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

import { Injectable } from '@angular/core';
import { Resolve, Router } from '@angular/router';
import {
  checkBoxCell,
  DateEntityTableColumn, defaultEntityTablePermissions,
  EntityTableColumn,
  EntityTableConfig,
  HeaderActionDescriptor
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { DialogService } from '@core/services/dialog.service';
import { MatDialog } from '@angular/material/dialog';
import { ImportExportService } from '@shared/import-export/import-export.service';
import { HomeDialogsService } from '@home/dialogs/home-dialogs.service';
import { AssetProfile, TB_SERVICE_QUEUE } from '@shared/models/asset.models';
import { AssetProfileService } from '@core/http/asset-profile.service';
import { AssetProfileComponent } from '@home/components/profile/asset-profile.component';
import { AssetProfileTabsComponent } from './asset-profile-tabs.component';
import { Operation, Resource } from '@shared/models/security.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';

@Injectable()
export class AssetProfilesTableConfigResolver implements Resolve<EntityTableConfig<AssetProfile>> {

  private readonly config: EntityTableConfig<AssetProfile> = new EntityTableConfig<AssetProfile>();

  constructor(private assetProfileService: AssetProfileService,
              private importExport: ImportExportService,
              private userPermissionsService: UserPermissionsService,
              private homeDialogs: HomeDialogsService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialogService: DialogService,
              private router: Router,
              private dialog: MatDialog) {

    this.config.entityType = EntityType.ASSET_PROFILE;
    this.config.entityComponent = AssetProfileComponent;
    this.config.entityTabsComponent = AssetProfileTabsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.ASSET_PROFILE);
    this.config.entityResources = entityTypeResources.get(EntityType.ASSET_PROFILE);

    this.config.hideDetailsTabsOnEdit = false;

    this.config.columns.push(
      new DateEntityTableColumn<AssetProfile>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<AssetProfile>('name', 'asset-profile.name', '50%'),
      new EntityTableColumn<AssetProfile>('description', 'asset-profile.description', '50%'),
      new EntityTableColumn<AssetProfile>('isDefault', 'asset-profile.default', '60px',
        entity => {
          return checkBoxCell(entity.default);
        })
    );

    this.config.cellActionDescriptors.push(
      {
        name: this.translate.instant('asset-profile.export'),
        icon: 'file_download',
        isEnabled: () => true,
        onAction: ($event, entity) => this.exportAssetProfile($event, entity)
      },
      {
        name: this.translate.instant('asset-profile.set-default'),
        icon: 'flag',
        isEnabled: (assetProfile) => !assetProfile.default  &&
          this.userPermissionsService.hasGenericPermission(Resource.ASSET_PROFILE, Operation.WRITE) &&
          TB_SERVICE_QUEUE !== assetProfile.name,
        onAction: ($event, entity) => this.setDefaultAssetProfile($event, entity)
      }
    );

    this.config.deleteEntityTitle = assetProfile => this.translate.instant('asset-profile.delete-asset-profile-title',
      { assetProfileName: assetProfile.name });
    this.config.deleteEntityContent = () => this.translate.instant('asset-profile.delete-asset-profile-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('asset-profile.delete-asset-profiles-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('asset-profile.delete-asset-profiles-text');

    this.config.entitiesFetchFunction = pageLink => this.assetProfileService.getAssetProfiles(pageLink);
    this.config.loadEntity = id => this.assetProfileService.getAssetProfile(id.id);
    this.config.saveEntity = assetProfile => this.assetProfileService.saveAssetProfile(assetProfile);
    this.config.deleteEntity = id => this.assetProfileService.deleteAssetProfile(id.id);
    this.config.onEntityAction = action => this.onAssetProfileAction(action);
    this.config.deleteEnabled = (assetProfile) => assetProfile && !assetProfile.default &&
      this.userPermissionsService.hasGenericPermission(Resource.ASSET_PROFILE, Operation.DELETE) && TB_SERVICE_QUEUE !== assetProfile.name;
    this.config.entitySelectionEnabled = (assetProfile) => assetProfile && !assetProfile.default &&
      this.userPermissionsService.hasGenericPermission(Resource.ASSET_PROFILE, Operation.DELETE) && TB_SERVICE_QUEUE !== assetProfile.name;
    this.config.detailsReadonly = (assetProfile) =>
      !this.userPermissionsService.hasGenericPermission(Resource.ASSET_PROFILE, Operation.WRITE)
      || assetProfile && TB_SERVICE_QUEUE === assetProfile.name;
    this.config.addActionDescriptors = this.configureAddActions();
  }

  resolve(): EntityTableConfig<AssetProfile> {
    this.config.tableTitle = this.translate.instant('asset-profile.asset-profiles');
    defaultEntityTablePermissions(this.userPermissionsService, this.config);
    return this.config;
  }

  configureAddActions(): Array<HeaderActionDescriptor> {
    const actions: Array<HeaderActionDescriptor> = [];
    actions.push(
      {
        name: this.translate.instant('asset-profile.create-asset-profile'),
        icon: 'insert_drive_file',
        isEnabled: () => true,
        onAction: ($event) => this.config.getTable().addEntity($event)
      },
      {
        name: this.translate.instant('asset-profile.import'),
        icon: 'file_upload',
        isEnabled: () => true,
        onAction: ($event) => this.importAssetProfile($event)
      }
    );
    return actions;
  }

  setDefaultAssetProfile($event: Event, assetProfile: AssetProfile) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('asset-profile.set-default-asset-profile-title', {assetProfileName: assetProfile.name}),
      this.translate.instant('asset-profile.set-default-asset-profile-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.assetProfileService.setDefaultAssetProfile(assetProfile.id.id).subscribe(
            () => {
              this.config.updateData();
            }
          );
        }
      }
    );
  }

  private openAssetProfile($event: Event, assetProfile: AssetProfile) {
    if ($event) {
      $event.stopPropagation();
    }
    const url = this.router.createUrlTree(['profiles', 'assetProfiles', assetProfile.id.id]);
    this.router.navigateByUrl(url);
  }

  importAssetProfile($event: Event) {
    this.importExport.importAssetProfile().subscribe(
      (assetProfile) => {
        if (assetProfile) {
          this.config.updateData();
        }
      }
    );
  }

  exportAssetProfile($event: Event, assetProfile: AssetProfile) {
    if ($event) {
      $event.stopPropagation();
    }
    this.importExport.exportAssetProfile(assetProfile.id.id);
  }

  onAssetProfileAction(action: EntityAction<AssetProfile>): boolean {
    switch (action.action) {
      case 'open':
        this.openAssetProfile(action.event, action.entity);
        return true;
      case 'setDefault':
        this.setDefaultAssetProfile(action.event, action.entity);
        return true;
      case 'export':
        this.exportAssetProfile(action.event, action.entity);
        return true;
    }
    return false;
  }

}
