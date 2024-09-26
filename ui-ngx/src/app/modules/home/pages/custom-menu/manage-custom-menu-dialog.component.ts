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

import { Component, Inject, OnDestroy, OnInit } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import {
  CMAssigneeType,
  CmAssigneeTypeTranslations,
  CMScope,
  cmScopeTranslations,
  CustomMenu,
  CustomMenuInfo,
  isDefaultCustomMenuConflict
} from '@shared/models/custom-menu.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CustomMenuService } from '@core/http/custom-menu.service';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { EntityType } from '@shared/models/entity-type.models';
import { Observable, of } from 'rxjs';
import { User } from '@shared/models/user.model';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { UserService } from '@core/http/user.service';
import { catchError, map } from 'rxjs/operators';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import { EntityInfoData } from '@shared/models/entity.models';
import { parseHttpErrorMessage } from '@core/utils';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@core/services/dialog.service';

export interface ManageCustomMenuDialogData {
  add: boolean;
  customMenu?: CustomMenuInfo;
  assigneeList?: EntityInfoData[];
}

export interface ManageCustomMenuDialogResult {
  customMenu?: CustomMenu;
  assigneeType?: CMAssigneeType;
  assignToList?: string[];
}

@Component({
  selector: 'tb-manage-custom-menu-dialog',
  templateUrl: './manage-custom-menu-dialog.component.html',
  styleUrls: ['./manage-custom-menu-dialog.component.scss']
})
export class ManageCustomMenuDialogComponent
  extends DialogComponent<ManageCustomMenuDialogComponent, ManageCustomMenuDialogResult> implements OnInit, OnDestroy {

  Authority = Authority;

  EntityType = EntityType;

  CMAssigneeType = CMAssigneeType;

  cmScopeTranslations = cmScopeTranslations;

  cmAssigneeTypeTranslations = CmAssigneeTypeTranslations;

  authUser = getCurrentAuthUser(this.store);

  title: string;

  customMenuFormGroup: UntypedFormGroup;

  customMenuScopes: CMScope[] = this.authUser.authority === Authority.TENANT_ADMIN ?
    [CMScope.TENANT, CMScope.CUSTOMER] : [CMScope.CUSTOMER];

  assigneeTypes: CMAssigneeType[] = [];

  fetchUsersFunction = this.fetchUsers.bind(this);

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: ManageCustomMenuDialogData,
              public dialogRef: MatDialogRef<ManageCustomMenuDialogComponent, ManageCustomMenuDialogResult>,
              private customMenuService: CustomMenuService,
              private userService: UserService,
              private dialogService: DialogService,
              private translate: TranslateService,
              private fb: UntypedFormBuilder) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.customMenuFormGroup = this.fb.group({
      name: [this.data.add ? null : this.data.customMenu.name, [Validators.required]],
      scope: [this.data.add ? (this.authUser.authority === Authority.TENANT_ADMIN ? CMScope.TENANT : CMScope.CUSTOMER)
        : this.data.customMenu.scope],
      assigneeType: [this.data.add ? CMAssigneeType.NO_ASSIGN: this.data.customMenu.assigneeType],
      assignToList: [this.data.add ? null : this.data.assigneeList]
    });
    this.title = this.data.add ? 'custom-menu.add' : 'custom-menu.manage-assignees';
    this.updateAssigneeTypes(this.data.add);
    if (!this.data.add) {
      this.customMenuFormGroup.get('name').disable({emitEvent: false});
    }
    if (this.data.add && this.authUser.authority === Authority.TENANT_ADMIN) {
      this.customMenuFormGroup.get('scope').valueChanges.subscribe(() => {
        this.updateAssigneeTypes();
      });
    } else {
      this.customMenuFormGroup.get('scope').disable({emitEvent: false});
    }
    this.customMenuFormGroup.get('assigneeType').valueChanges.subscribe(() => {
      this.updateAssignToList();
    });
  }

  private updateAssigneeTypes(updateAssigneeList = true) {
    const scope: CMScope = this.customMenuFormGroup.get('scope').value;
    if (scope === CMScope.TENANT) {
      this.assigneeTypes = [CMAssigneeType.NO_ASSIGN, CMAssigneeType.ALL, CMAssigneeType.USERS];
      const assigneeType: CMAssigneeType = this.customMenuFormGroup.get('assigneeType').value;
      if (assigneeType === CMAssigneeType.CUSTOMERS) {
        this.customMenuFormGroup.get('assigneeType').patchValue(CMAssigneeType.NO_ASSIGN, {emitEvent: false});
      }
    } else {
      this.assigneeTypes = [CMAssigneeType.NO_ASSIGN, CMAssigneeType.ALL, CMAssigneeType.USERS, CMAssigneeType.CUSTOMERS];
    }
    if (updateAssigneeList) {
      this.updateAssignToList();
    }
  }

  private updateAssignToList() {
    const assigneeType: CMAssigneeType = this.customMenuFormGroup.get('assigneeType').value;
    this.customMenuFormGroup.get('assignToList').patchValue(null, {emitEvent: false});
    switch (assigneeType) {
      case CMAssigneeType.NO_ASSIGN:
      case CMAssigneeType.ALL:
        this.customMenuFormGroup.get('assignToList').disable({emitEvent: false});
        break;
      case CMAssigneeType.CUSTOMERS:
      case CMAssigneeType.USERS:
        this.customMenuFormGroup.get('assignToList').enable({emitEvent: false});
        break;
    }
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  submit() {
    if (this.data.add) {
      this.add();
    } else {
      this.updateAssignees();
    }
  }

  private add(): void {
    if (this.customMenuFormGroup.valid) {
      const customMenuInfo: CustomMenuInfo = {
        name: this.customMenuFormGroup.get('name').value,
        scope: this.customMenuFormGroup.get('scope').value,
        assigneeType: this.customMenuFormGroup.get('assigneeType').value
      };
      const assignToList: string[] = this.customMenuFormGroup.get('assignToList').value || [];
      this.handleMenuSaveOperation(
        customMenuInfo,
        this.customMenuService.saveCustomMenu(customMenuInfo, assignToList, false,
          {ignoreErrors: true}),
        customMenu => this.dialogRef.close({customMenu}),
        () => this.customMenuService.saveCustomMenu(customMenuInfo, assignToList, true)
      );
    }
  }

  private updateAssignees() {
    if (this.customMenuFormGroup.valid) {
      const customMenu = this.data.customMenu;
      const assigneeType: CMAssigneeType = this.customMenuFormGroup.get('assigneeType').value;
      const assignToList: string[] = this.customMenuFormGroup.get('assignToList').value || [];
      this.handleMenuSaveOperation(
        customMenu,
        this.customMenuService.assignCustomMenu(customMenu.id.id, assigneeType, assignToList, false,
          {ignoreErrors: true}),
        _res => this.dialogRef.close({assigneeType, assignToList}),
        () => this.customMenuService.assignCustomMenu(customMenu.id.id, assigneeType, assignToList, true)
      );
    }
  }

  private handleMenuSaveOperation<R>(customMenu: CustomMenuInfo,
                                     saveObservable: Observable<R>,
                                     onSuccess: (res: R) => void,
                                     onForceSave: () => Observable<R>) {
    saveObservable.pipe(
      map((res) => ({success: true, error: null, res})),
      catchError((err) => {
        if (isDefaultCustomMenuConflict(err)) {
          return of({success: false, error: null, res: null});
        } else {
          return of({success: false, error: err, res: null});
        }
      }),
    ).subscribe(
      (saveResult) => {
        if (saveResult.success) {
          onSuccess(saveResult.res);
        } else if (!saveResult.error) {
          const menuConflictMessage = this.translate.instant('custom-menu.menu-conflict-message',
            {scope: this.translate.instant(cmScopeTranslations.get(customMenu.scope))});
          this.dialogService.confirm(this.translate.instant('custom-menu.menu-conflict'), menuConflictMessage,
            this.translate.instant('action.cancel'), this.translate.instant('custom-menu.replace')).subscribe((res) => {
            if (res) {
              const forceSaveObservable = onForceSave();
              forceSaveObservable.subscribe((forceSaveRes) => {
                onSuccess(forceSaveRes);
              });
            }
          });
        } else {
          const errorMessageWithTimeout = parseHttpErrorMessage(saveResult.error, this.translate);
          setTimeout(() => {
            this.store.dispatch(new ActionNotificationShow({message: errorMessageWithTimeout.message, type: 'error'}));
          }, errorMessageWithTimeout.timeout);
        }
      }
    );
  }

  fetchUsers(searchText?: string): Observable<Array<User>> {
    const scope: CMScope = this.customMenuFormGroup.get('scope').value;
    let usersObservable: Observable<PageData<User>>;
    const pageLink = new PageLink(50, 0, searchText, {
      property: 'email',
      direction: Direction.ASC
    });
    if (scope === CMScope.TENANT) {
      usersObservable = this.userService.getAllUserInfos(false, pageLink, {ignoreLoading: true});
    } else if (scope === CMScope.CUSTOMER) {
      if (this.authUser.authority === Authority.TENANT_ADMIN) {
        usersObservable = this.userService.getAllCustomerUsers(pageLink, {ignoreLoading: true});
      } else {
        usersObservable = this.userService.getAllUserInfos(true, pageLink, {ignoreLoading: true});
      }
    } else {
      usersObservable = of(emptyPageData<User>());
    }
    return usersObservable.pipe(
      map(pageData => pageData.data)
    );
  }
}
