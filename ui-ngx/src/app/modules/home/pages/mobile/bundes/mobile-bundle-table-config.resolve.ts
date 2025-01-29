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
import {
  CellActionDescriptor,
  checkBoxCell,
  DateEntityTableColumn,
  defaultEntityTablePermissions,
  EntityChipsEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { MobileAppBundleInfo } from '@shared/models/mobile-app.models';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Direction } from '@shared/models/page/sort-order';
import { MobileBundleTableHeaderComponent } from '@home/pages/mobile/bundes/mobile-bundle-table-header.component';
import { DatePipe } from '@angular/common';
import { MobileAppService } from '@core/http/mobile-app.service';
import { finalize, map, skip, take, takeUntil } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import {
  MobileBundleDialogComponent,
  MobileBundleDialogData
} from '@home/pages/mobile/bundes/mobile-bundle-dialog.component';
import {
  MobileAppConfigurationDialogComponent,
  MobileAppConfigurationDialogData
} from '@home/pages/mobile/bundes/mobile-app-configuration-dialog.component';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { getCurrentAuthUser, selectUserSettingsProperty } from '@core/auth/auth.selectors';
import { forkJoin, Observable, of, switchMap } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
import { UserPermissionsService } from '@core/http/user-permissions.service';

@Injectable()
export class MobileBundleTableConfigResolver {

  private readonly config: EntityTableConfig<MobileAppBundleInfo> = new EntityTableConfig<MobileAppBundleInfo>();

  private openingEditDialog = false;

  constructor(
    private datePipe: DatePipe,
    private mobileAppService: MobileAppService,
    private translate : TranslateService,
    private dialog: MatDialog,
    private router: Router,
    private store: Store<AppState>,
    private userPermissionsService: UserPermissionsService,
  ) {
    this.config.selectionEnabled = false;
    this.config.entityType = EntityType.MOBILE_APP_BUNDLE;
    this.config.addAsTextButton = true;
    this.config.rowPointer = true;
    this.config.detailsPanelEnabled = false;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.MOBILE_APP_BUNDLE);
    this.config.entityResources = entityTypeResources.get(EntityType.MOBILE_APP_BUNDLE);
    this.config.headerComponent = MobileBundleTableHeaderComponent;
    this.config.addDialogStyle = {width: '850px', maxHeight: '100vh'};
    this.config.defaultSortOrder = {property: 'createdTime', direction: Direction.DESC};

    this.config.addEntity = () => this.editBundle(null, true);

    this.config.deleteEnabled = bundle => !(bundle.iosAppId || bundle.androidAppId);
    this.config.deleteEntityTitle = (bundle) => this.translate.instant('mobile.delete-applications-bundle-title', {bundleName: bundle.name});
    this.config.deleteEntityContent = () => this.translate.instant('mobile.delete-applications-bundle-text');
    this.config.deleteEntity = id => this.mobileAppService.deleteMobileAppBundle(id.id);

    this.config.entitiesFetchFunction = pageLink => this.mobileAppService.getTenantMobileAppBundleInfos(pageLink).pipe(
      map(bundles => {
        bundles.data.map(data => {
          if (data.androidPkgName) {
            data.androidPkg = {
              id: data.androidAppId,
              name: data.androidPkgName
            }
          }
          if (data.iosPkgName) {
            data.iosPkg = {
              id: data.iosAppId,
              name: data.iosPkgName
            }
          }
        })
        return bundles;
      })
    );

    this.config.handleRowClick = ($event, bundle) => {
      $event?.stopPropagation();
      if (!this.openingEditDialog) {
        this.openingEditDialog = true;
        this.mobileAppService.getMobileAppBundleInfoById(bundle.id.id).pipe(
          switchMap(appBundleInfo => this.editBundle(appBundleInfo)),
          takeUntil(this.router.events.pipe(skip(1))),
          finalize(() => {this.openingEditDialog = false;})
        ).subscribe((res) => {
          if (res) {
            this.config.updateData();
          }
        });
      }
      return true;
    };

    this.config.entityAdded = (bundle) => {
      this.store.pipe(select(selectUserSettingsProperty('notDisplayConfigurationAfterAddMobileBundle'))).pipe(
        take(1)
      ).subscribe((settings: boolean) => {
        if(!settings) {
          this.configurationApp(null, bundle, true);
        }
      });
    }

    this.config.cellActionDescriptors = this.configureCellActions();
  }

  resolve(_route: ActivatedRouteSnapshot): EntityTableConfig<MobileAppBundleInfo> {
    const authUser = getCurrentAuthUser(this.store);

    this.config.columns = [
      new DateEntityTableColumn<MobileAppBundleInfo>('createdTime', 'common.created-time', this.datePipe, '170px'),
      new EntityTableColumn<MobileAppBundleInfo>('title', 'mobile.title', '25%'),
      new EntityChipsEntityTableColumn<MobileAppBundleInfo>('oauth2ClientInfos', 'mobile.oauth-clients', '35%'),
      new EntityChipsEntityTableColumn<MobileAppBundleInfo>('androidPkg', 'mobile.android-app', '20%'),
      new EntityChipsEntityTableColumn<MobileAppBundleInfo>('iosPkg', 'mobile.ios-app', '20%'),
      new EntityTableColumn<MobileAppBundleInfo>('oauth2Enabled', 'mobile.enable-oauth', '140px',
        entity => checkBoxCell(entity.oauth2Enabled))
    ];

    if (authUser.authority !== Authority.SYS_ADMIN) {
      this.config.columns.push(
        new EntityTableColumn<MobileAppBundleInfo>('selfRegistrationParams.enabled', 'mobile.enable-self-registration', '140px',
          entity => checkBoxCell(entity.selfRegistrationParams?.enabled), () => ({}), false),
      )
    }
    defaultEntityTablePermissions(this.userPermissionsService, this.config);
    return this.config;
  }

  private configureCellActions(): Array<CellActionDescriptor<MobileAppBundleInfo>> {
    return [
      {
        name: this.translate.instant('mobile.configuration-app'),
        icon: 'code',
        isEnabled: () => true,
        onAction: ($event, entity) => this.configurationApp($event, entity)
      }
    ];
  }

  private editBundle(bundle: MobileAppBundleInfo, isAdd = false): Observable<MobileAppBundleInfo> {
    return this.dialog.open<MobileBundleDialogComponent, MobileBundleDialogData,
      MobileAppBundleInfo>(MobileBundleDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        isAdd,
        bundle
      }
    }).afterClosed();
  }

  private configurationApp($event: Event, entity: MobileAppBundleInfo, afterAdd = false) {
    if ($event) {
      $event.stopPropagation();
    }
    const task = {
      androidApp: entity.androidAppId ? this.mobileAppService.getMobileAppInfoById(entity.androidAppId.id) : of(null),
      iosApp: entity.iosAppId ? this.mobileAppService.getMobileAppInfoById(entity.iosAppId.id) : of(null)
    };
    forkJoin(task).subscribe(data => {
      this.dialog.open<MobileAppConfigurationDialogComponent, MobileAppConfigurationDialogData,
        MobileAppBundleInfo>(MobileAppConfigurationDialogComponent, {
        disableClose: true,
        panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
        data: {
          afterAdd,
          androidApp: data.androidApp,
          iosApp: data.iosApp
        }
      }).afterClosed()
        .subscribe(() => {
          if (afterAdd) {
            this.config.updateData();
          }
        });
    })
  }
}
