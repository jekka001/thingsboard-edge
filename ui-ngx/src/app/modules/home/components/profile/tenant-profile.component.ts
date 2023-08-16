///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

import { ChangeDetectorRef, Component, Inject, Input, Optional } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { createTenantProfileConfiguration, TenantProfile, TenantProfileType } from '@shared/models/tenant.model';
import { ActionNotificationShow } from '@app/core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { EntityComponent } from '../entity/entity.component';
import { guid } from '@core/utils';

@Component({
  selector: 'tb-tenant-profile',
  templateUrl: './tenant-profile.component.html',
  styleUrls: ['./tenant-profile.component.scss']
})
export class TenantProfileComponent extends EntityComponent<TenantProfile> {

  @Input()
  standalone = false;

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Optional() @Inject('entity') protected entityValue: TenantProfile,
              @Optional() @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<TenantProfile>,
              protected fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  hideDelete() {
    if (this.entitiesTableConfig) {
      return !this.entitiesTableConfig.deleteEnabled(this.entity);
    } else {
      return false;
    }
  }

  buildForm(entity: TenantProfile): UntypedFormGroup {
    const mainQueue = [
      {
        id: guid(),
        consumerPerPartition: true,
        name: 'Main',
        packProcessingTimeout: 2000,
        partitions: 2,
        pollInterval: 2000,
        processingStrategy: {
          failurePercentage: 0,
          maxPauseBetweenRetries: 3,
          pauseBetweenRetries: 3,
          retries: 3,
          type: 'SKIP_ALL_FAILURES'
        },
        submitStrategy: {
          batchSize: 1000,
          type: 'BURST'
        },
        topic: 'tb_rule_engine.main',
        additionalInfo: {
          description: '',
          customProperties: ''
        }
      },
      {
        id: guid(),
        name: 'HighPriority',
        topic: 'tb_rule_engine.hp',
        pollInterval: 2000,
        partitions: 2,
        consumerPerPartition: true,
        packProcessingTimeout: 2000,
        submitStrategy: {
          type: 'BURST',
          batchSize: 100
        },
        processingStrategy: {
          type: 'RETRY_FAILED_AND_TIMED_OUT',
          retries: 0,
          failurePercentage: 0,
          pauseBetweenRetries: 5,
          maxPauseBetweenRetries: 5
        },
        additionalInfo: {
          description: '',
          customProperties: ''
        }
      },
      {
        id: guid(),
        name: 'SequentialByOriginator',
        topic: 'tb_rule_engine.sq',
        pollInterval: 2000,
        partitions: 2,
        consumerPerPartition: true,
        packProcessingTimeout: 2000,
        submitStrategy: {
          type: 'SEQUENTIAL_BY_ORIGINATOR',
          batchSize: 100
        },
        processingStrategy: {
          type: 'RETRY_FAILED_AND_TIMED_OUT',
          retries: 3,
          failurePercentage: 0,
          pauseBetweenRetries: 5,
          maxPauseBetweenRetries: 5
        },
        additionalInfo: {
          description: '',
          customProperties: ''
        }
      }
    ];
    const formGroup = this.fb.group(
      {
        name: [entity ? entity.name : '', [Validators.required, Validators.maxLength(255)]],
        isolatedTbRuleEngine: [entity ? entity.isolatedTbRuleEngine : false, []],
        profileData: this.fb.group({
          configuration: [entity && !this.isAdd ? entity?.profileData.configuration
            : createTenantProfileConfiguration(TenantProfileType.DEFAULT), []],
          queueConfiguration: [entity && !this.isAdd ? entity?.profileData.queueConfiguration : null, []]
        }),
        description: [entity ? entity.description : '', []],
      }
    );
    formGroup.get('isolatedTbRuleEngine').valueChanges.subscribe((value) => {
      if (value) {
        formGroup.get('profileData').patchValue({
            queueConfiguration: mainQueue
          }, {emitEvent: false});
      } else {
        formGroup.get('profileData').patchValue({
            queueConfiguration: null
          }, {emitEvent: false});
      }
    });
    return formGroup;
  }

  updateForm(entity: TenantProfile) {
    this.entityForm.patchValue({name: entity.name}, {emitEvent: false});
    this.entityForm.patchValue({isolatedTbRuleEngine: entity.isolatedTbRuleEngine}, {emitEvent: false});
    this.entityForm.get('profileData').patchValue({
      configuration: !this.isAdd ? entity.profileData?.configuration : createTenantProfileConfiguration(TenantProfileType.DEFAULT)
    }, {emitEvent: false});
    this.entityForm.get('profileData').patchValue({queueConfiguration: entity.profileData?.queueConfiguration}, {emitEvent: false});
    this.entityForm.patchValue({description: entity.description}, {emitEvent: false});
  }

  updateFormState() {
    if (this.entityForm) {
      if (this.isEditValue) {
        this.entityForm.enable({emitEvent: false});
      } else {
        this.entityForm.disable({emitEvent: false});
      }
    }
  }

  onTenantProfileIdCopied(event) {
    this.store.dispatch(new ActionNotificationShow(
      {
        message: this.translate.instant('tenant-profile.idCopiedMessage'),
        type: 'success',
        duration: 750,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
  }

}
