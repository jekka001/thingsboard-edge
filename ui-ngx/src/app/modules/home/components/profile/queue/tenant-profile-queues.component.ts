///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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

import { Component, forwardRef, Input, OnDestroy } from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormArray,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
  Validators
} from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { Subscription } from 'rxjs';
import { QueueInfo } from '@shared/models/queue.models';
import { UtilsService } from '@core/services/utils.service';
import { guid } from '@core/utils';

@Component({
  selector: 'tb-tenant-profile-queues',
  templateUrl: './tenant-profile-queues.component.html',
  styleUrls: ['./tenant-profile-queues.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => TenantProfileQueuesComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => TenantProfileQueuesComponent),
      multi: true,
    }
  ]
})
export class TenantProfileQueuesComponent implements ControlValueAccessor, Validator, OnDestroy {

  tenantProfileQueuesFormGroup: FormGroup;
  newQueue = false;

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  @Input()
  disabled: boolean;

  private valueChangeSubscription$: Subscription = null;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private utils: UtilsService,
              private fb: FormBuilder) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  ngOnDestroy() {
    if (this.valueChangeSubscription$) {
      this.valueChangeSubscription$.unsubscribe();
    }
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.tenantProfileQueuesFormGroup = this.fb.group({
      queues: this.fb.array([])
    });
  }

  get queuesFormArray(): FormArray {
    return this.tenantProfileQueuesFormGroup.get('queues') as FormArray;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.tenantProfileQueuesFormGroup.disable({emitEvent: false});
    } else {
      this.tenantProfileQueuesFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(queues: Array<QueueInfo> | null): void {
    if (this.valueChangeSubscription$) {
      this.valueChangeSubscription$.unsubscribe();
    }
    const queuesControls: Array<AbstractControl> = [];
    if (queues) {
      queues.forEach((queue) => {
        queuesControls.push(this.fb.control(queue, [Validators.required]));
      });
    }
    this.tenantProfileQueuesFormGroup.setControl('queues', this.fb.array(queuesControls));
    if (this.disabled) {
      this.tenantProfileQueuesFormGroup.disable({emitEvent: false});
    } else {
      this.tenantProfileQueuesFormGroup.enable({emitEvent: false});
    }
    this.valueChangeSubscription$ = this.tenantProfileQueuesFormGroup.valueChanges.subscribe(value => {
      this.updateModel();
    });
  }

  public trackByQueue(index: number, queueControl: AbstractControl) {
    if (queueControl) {
      return queueControl.value.id;
    }
    return null;
  }

  public removeQueue(index: number) {
    (this.tenantProfileQueuesFormGroup.get('queues') as FormArray).removeAt(index);
  }

  public addQueue() {
    const queue = {
      id: guid(),
      consumerPerPartition: false,
      name: '',
      packProcessingTimeout: 2000,
      partitions: 10,
      pollInterval: 25,
      processingStrategy: {
        failurePercentage: 0,
        maxPauseBetweenRetries: 3,
        pauseBetweenRetries: 3,
        retries: 3,
        type: ''
      },
      submitStrategy: {
        batchSize: 0,
        type: ''
      },
      topic: '',
      additionalInfo: {
        description: ''
      }
    };
    this.newQueue = true;
    const queuesArray = this.tenantProfileQueuesFormGroup.get('queues') as FormArray;
    queuesArray.push(this.fb.control(queue, []));
    this.tenantProfileQueuesFormGroup.updateValueAndValidity();
    if (!this.tenantProfileQueuesFormGroup.valid) {
      this.updateModel();
    }
  }

  public validate(c: AbstractControl): ValidationErrors | null {
    return this.tenantProfileQueuesFormGroup.valid ? null : {
      queues: {
        valid: false,
      },
    };
  }

  getName(value) {
    return this.utils.customTranslation(value, value);
  }

  private updateModel() {
    const queues: Array<QueueInfo> = this.tenantProfileQueuesFormGroup.get('queues').value;
    this.propagateChange(queues);
  }
}
