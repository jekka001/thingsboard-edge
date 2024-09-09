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

import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import {
  CMScope,
  DefaultMenuItem,
  HomeMenuItem,
  HomeMenuItemType,
  homeMenuItemTypes,
  homeMenuItemTypeTranslations,
  isHomeMenuItem
} from '@shared/models/custom-menu.models';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { MenuSection, menuSectionMap } from '@core/services/menu.models';
import { TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { isDefinedAndNotNull } from '@core/utils';

@Component({
  selector: 'tb-default-menu-item-panel',
  templateUrl: './default-menu-item-panel.component.html',
  styleUrls: ['./default-menu-item-panel.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class DefaultMenuItemPanelComponent implements OnInit {

  HomeMenuItemType = HomeMenuItemType;

  homeMenuItemTypes = homeMenuItemTypes;

  homeMenuItemTypeTranslations = homeMenuItemTypeTranslations;

  @Input()
  disabled: boolean;

  @Input()
  scope: CMScope;

  @Input()
  menuItem: DefaultMenuItem;

  @Input()
  popover: TbPopoverComponent<DefaultMenuItemPanelComponent>;

  @Output()
  defaultMenuItemApplied = new EventEmitter<DefaultMenuItem>();

  menuItemFormGroup: UntypedFormGroup;

  defaultItemName: string;

  isHomeMenuItem = false;

  isHomeTypeEditable = false;

  isCleanupEnabled = false;

  private defaultMenuSection: MenuSection;

  constructor(private fb: UntypedFormBuilder,
              private store: Store<AppState>,
              private translate: TranslateService) {
  }

  ngOnInit(): void {
    if (isHomeMenuItem(this.menuItem)) {
      this.isHomeMenuItem = true;
    }
    this.defaultMenuSection = menuSectionMap.get(this.menuItem.id);
    this.defaultItemName = this.translate.instant(this.defaultMenuSection.name);

    this.menuItemFormGroup = this.fb.group({
      visible: [this.menuItem.visible, []],
      icon: [this.menuItem.icon ? this.menuItem.icon : this.defaultMenuSection.icon, []],
      name: [this.menuItem.name, []],
    });

    if (this.isHomeMenuItem) {
      const authUser = getCurrentAuthUser(this.store);
      this.isHomeTypeEditable = authUser.authority !== Authority.SYS_ADMIN && this.scope !== CMScope.SYSTEM;

      this.menuItemFormGroup.get('visible').disable({emitEvent: false});
      const homeMenuItem = this.menuItem as HomeMenuItem;
      this.menuItemFormGroup.setControl('homeType',
        this.fb.control(this.isHomeTypeEditable ? homeMenuItem.homeType : HomeMenuItemType.DEFAULT), {emitEvent: false});
      this.menuItemFormGroup.setControl('dashboardId',
        this.fb.control(homeMenuItem.dashboardId, [Validators.required]), {emitEvent: false});
      this.menuItemFormGroup.setControl('hideDashboardToolbar',
        this.fb.control(isDefinedAndNotNull(homeMenuItem.hideDashboardToolbar) ?
          homeMenuItem.hideDashboardToolbar : true), {emitEvent: false});
      this.homeTypeChanged();
      if (!this.disabled) {
        if (this.isHomeTypeEditable) {
          this.menuItemFormGroup.get('homeType').valueChanges.subscribe(() => {
            this.homeTypeChanged();
          });
        } else {
          this.menuItemFormGroup.get('homeType').disable({emitEvent: false});
        }
      }
    }

    if (this.disabled) {
      this.menuItemFormGroup.disable({emitEvent: false});
    } else {
      this.menuItemFormGroup.valueChanges.subscribe(() => {
        this.updateModel();
      });
      this.updateCleanupState();
    }
  }

  cancel() {
    this.popover?.hide();
  }

  apply() {
    this.defaultMenuItemApplied.emit(this.menuItem);
  }

  cleanup() {
    this.menuItemFormGroup.patchValue(
      {
        visible: true,
        icon: this.defaultMenuSection.icon,
        name: null
      }, {emitEvent: false}
    );
    if (this.isHomeMenuItem) {
      this.menuItemFormGroup.patchValue(
        {
          homeType: HomeMenuItemType.DEFAULT,
          dashboardId: null,
          hideDashboardToolbar: true
        }, {emitEvent: false}
      );
      this.homeTypeChanged();
    }
    this.menuItemFormGroup.markAsDirty();
    this.updateModel();
  }

  private updateModel() {
    if (!this.isHomeMenuItem) {
      this.menuItem.visible = this.menuItemFormGroup.get('visible').value;
    }
    const name = this.menuItemFormGroup.get('name').value;
    if (name) {
      this.menuItem.name = name;
    } else {
      delete this.menuItem.name;
    }
    const icon = this.menuItemFormGroup.get('icon').value;
    if (icon !== this.defaultMenuSection.icon) {
      this.menuItem.icon = icon;
    } else {
      delete this.menuItem.icon;
    }

    if (this.isHomeMenuItem) {
      const homeMenuItem = this.menuItem as HomeMenuItem;
      homeMenuItem.homeType = this.menuItemFormGroup.get('homeType').value;
      if (homeMenuItem.homeType === HomeMenuItemType.DEFAULT) {
        delete homeMenuItem.dashboardId;
        delete homeMenuItem.hideDashboardToolbar;
      } else {
        homeMenuItem.dashboardId = this.menuItemFormGroup.get('dashboardId').value;
        homeMenuItem.hideDashboardToolbar = this.menuItemFormGroup.get('hideDashboardToolbar').value;
      }
    }
    this.updateCleanupState();
  }

  private updateCleanupState() {
    this.isCleanupEnabled = !this.menuItem.visible ||
      !!this.menuItem.name ||
      !!this.menuItem.icon ||
      (this.isHomeMenuItem && (this.menuItem as HomeMenuItem).homeType !== HomeMenuItemType.DEFAULT);
  }

  private homeTypeChanged() {
    const homeType: HomeMenuItemType = this.menuItemFormGroup.get('homeType').value;
    if (homeType === HomeMenuItemType.DEFAULT) {
      this.menuItemFormGroup.get('dashboardId').disable({emitEvent: false});
      this.menuItemFormGroup.get('hideDashboardToolbar').disable({emitEvent: false});
    } else {
      this.menuItemFormGroup.get('dashboardId').enable({emitEvent: false});
      this.menuItemFormGroup.get('hideDashboardToolbar').enable({emitEvent: false});
    }
  }
}
