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
import { Resolve } from '@angular/router';
import {
  checkBoxCell,
  DateEntityTableColumn, defaultEntityTablePermissions,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { EntityAction } from '@home/models/entity/entity-component.models';
import { DialogService } from '@core/services/dialog.service';
import {
  DeviceProfile,
  deviceProfileTypeTranslationMap,
  deviceTransportTypeTranslationMap
} from '@shared/models/device.models';
import { DeviceProfileService } from '@core/http/device-profile.service';
import { DeviceProfileComponent } from '../../components/profile/device-profile.component';
import { DeviceProfileTabsComponent } from './device-profile-tabs.component';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { UtilsService } from '@core/services/utils.service';
import { Operation, Resource } from '@shared/models/security.models';
import { Observable } from 'rxjs';
import { MatDialog } from '@angular/material/dialog';
import {
  AddDeviceProfileDialogComponent,
  AddDeviceProfileDialogData
} from '../../components/profile/add-device-profile-dialog.component';

@Injectable()
export class DeviceProfilesTableConfigResolver implements Resolve<EntityTableConfig<DeviceProfile>> {

  private readonly config: EntityTableConfig<DeviceProfile> = new EntityTableConfig<DeviceProfile>();

  constructor(private deviceProfileService: DeviceProfileService,
              private userPermissionsService: UserPermissionsService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialogService: DialogService,
              private utils: UtilsService,
              private dialog: MatDialog) {

    this.config.entityType = EntityType.DEVICE_PROFILE;
    this.config.entityComponent = DeviceProfileComponent;
    this.config.entityTabsComponent = DeviceProfileTabsComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.DEVICE_PROFILE);
    this.config.entityResources = entityTypeResources.get(EntityType.DEVICE_PROFILE);

    this.config.hideDetailsTabsOnEdit = false;

    this.config.addDialogStyle = {width: '1000px'};

    this.config.entityTitle = (deviceProfile) => deviceProfile ?
      this.utils.customTranslation(deviceProfile.name, deviceProfile.name) : '';

    this.config.columns.push(
      new DateEntityTableColumn<DeviceProfile>('createdTime', 'common.created-time', this.datePipe, '150px'),
      new EntityTableColumn<DeviceProfile>('name', 'device-profile.name', '20%'),
      new EntityTableColumn<DeviceProfile>('type', 'device-profile.type', '20%', (deviceProfile) => {
        return this.translate.instant(deviceProfileTypeTranslationMap.get(deviceProfile.type));
      }),
      new EntityTableColumn<DeviceProfile>('transportType', 'device-profile.transport-type', '20%', (deviceProfile) => {
        return this.translate.instant(deviceTransportTypeTranslationMap.get(deviceProfile.transportType));
      }),
      new EntityTableColumn<DeviceProfile>('description', 'device-profile.description', '40%'),
      new EntityTableColumn<DeviceProfile>('isDefault', 'device-profile.default', '60px',
        entity => {
          return checkBoxCell(entity.default);
        })
    );

    this.config.cellActionDescriptors.push(
      {
        name: this.translate.instant('device-profile.set-default'),
        icon: 'flag',
        isEnabled: (deviceProfile) => !deviceProfile.default &&
          this.userPermissionsService.hasGenericPermission(Resource.DEVICE_PROFILE, Operation.WRITE),
        onAction: ($event, entity) => this.setDefaultDeviceProfile($event, entity)
      }
    );

    this.config.deleteEntityTitle = deviceProfile => this.translate.instant('device-profile.delete-device-profile-title',
      { deviceProfileName: deviceProfile.name });
    this.config.deleteEntityContent = () => this.translate.instant('device-profile.delete-device-profile-text');
    this.config.deleteEntitiesTitle = count => this.translate.instant('device-profile.delete-device-profiles-title', {count});
    this.config.deleteEntitiesContent = () => this.translate.instant('device-profile.delete-device-profiles-text');

    this.config.entitiesFetchFunction = pageLink => this.deviceProfileService.getDeviceProfiles(pageLink);
    this.config.loadEntity = id => this.deviceProfileService.getDeviceProfile(id.id);
    this.config.saveEntity = deviceProfile => this.deviceProfileService.saveDeviceProfile(deviceProfile);
    this.config.deleteEntity = id => this.deviceProfileService.deleteDeviceProfile(id.id);
    this.config.onEntityAction = action => this.onDeviceProfileAction(action);
    this.config.deleteEnabled = (deviceProfile) => deviceProfile && !deviceProfile.default &&
      this.userPermissionsService.hasGenericPermission(Resource.DEVICE_PROFILE, Operation.DELETE);
    this.config.entitySelectionEnabled = (deviceProfile) => deviceProfile && !deviceProfile.default &&
      this.userPermissionsService.hasGenericPermission(Resource.DEVICE_PROFILE, Operation.DELETE);
    this.config.addEntity = () => this.addDeviceProfile();
  }

  resolve(): EntityTableConfig<DeviceProfile> {
    this.config.tableTitle = this.translate.instant('device-profile.device-profiles');
    defaultEntityTablePermissions(this.userPermissionsService, this.config);
    return this.config;
  }

  addDeviceProfile(): Observable<DeviceProfile> {
    return this.dialog.open<AddDeviceProfileDialogComponent, AddDeviceProfileDialogData,
      DeviceProfile>(AddDeviceProfileDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        deviceProfileName: null,
        transportType: null
      }
    }).afterClosed();
  }

  setDefaultDeviceProfile($event: Event, deviceProfile: DeviceProfile) {
    if ($event) {
      $event.stopPropagation();
    }
    this.dialogService.confirm(
      this.translate.instant('device-profile.set-default-device-profile-title', {deviceProfileName: deviceProfile.name}),
      this.translate.instant('device-profile.set-default-device-profile-text'),
      this.translate.instant('action.no'),
      this.translate.instant('action.yes'),
      true
    ).subscribe((res) => {
        if (res) {
          this.deviceProfileService.setDefaultDeviceProfile(deviceProfile.id.id).subscribe(
            () => {
              this.config.table.updateData();
            }
          );
        }
      }
    );
  }

  onDeviceProfileAction(action: EntityAction<DeviceProfile>): boolean {
    switch (action.action) {
      case 'setDefault':
        this.setDefaultDeviceProfile(action.event, action.entity);
        return true;
    }
    return false;
  }

}
