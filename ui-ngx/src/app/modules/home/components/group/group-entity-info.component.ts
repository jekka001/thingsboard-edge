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

import { Component, Input } from '@angular/core';
import { EntityType, groupUrlPrefixByEntityType } from '@shared/models/entity-type.models';
import { EntityId } from '@shared/models/id/entity-id';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { GroupEntityInfo } from '@shared/models/base-data';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';

@Component({
  selector: 'tb-group-entity-info',
  templateUrl: './group-entity-info.component.html',
  styleUrls: ['./group-entity-info.component.scss']
})
export class GroupEntityInfoComponent {

  groupEntityValue?: GroupEntityInfo<EntityId>;

  @Input()
  set groupEntity(value: GroupEntityInfo<EntityId>) {
    this.groupEntityValue = value;
    this.update();
  }

  get groupEntity(): GroupEntityInfo<EntityId> {
    return this.groupEntityValue;
  }

  authUser = getCurrentAuthUser(this.store);

  displayOwner: boolean;
  ownerLabel: string;
  groupPrefixUrl: string;

  constructor(private store: Store<AppState>,
              private userPermissionsService: UserPermissionsService) {
  }

  update(): void {
    if (this.groupEntity && this.groupEntity.id) {
      this.groupPrefixUrl = groupUrlPrefixByEntityType.get(this.groupEntity.id.entityType as EntityType);
      this.displayOwner = !this.userPermissionsService.isDirectOwner(this.groupEntity.ownerId);
      this.ownerLabel = this.authUser.authority === Authority.CUSTOMER_USER
        ? 'entity.sub-customer-name' : 'entity.customer-name';
      if (this.groupEntity.ownerId && !this.userPermissionsService.isDirectOwner(this.groupEntity.ownerId)) {
        this.groupPrefixUrl = `/customers/all/${this.groupEntity.ownerId.id}${this.groupPrefixUrl}`;
      }
    }
  }

}
