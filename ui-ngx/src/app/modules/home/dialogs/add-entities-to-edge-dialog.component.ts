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

import { Component, Inject, OnInit, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormControl, FormGroup, FormGroupDirective, NgForm, Validators } from '@angular/forms';
import { EntityType } from '@shared/models/entity-type.models';
import { forkJoin, Observable } from 'rxjs';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { RuleChainType } from '@shared/models/rule-chain.models';
import { RuleChainService } from '@core/http/rule-chain.service';
import { SchedulerEventService } from '@core/http/scheduler-event.service';

export interface AddEntitiesToEdgeDialogData {
  edgeId: string;
  entityType: EntityType;
}

@Component({
  selector: 'tb-add-entities-to-edge-dialog',
  templateUrl: './add-entities-to-edge-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: AddEntitiesToEdgeDialogComponent}],
  styleUrls: []
})
export class AddEntitiesToEdgeDialogComponent extends DialogComponent<AddEntitiesToEdgeDialogComponent, boolean> implements OnInit, ErrorStateMatcher {

  addEntitiesToEdgeFormGroup: FormGroup;

  submitted = false;

  entityType: EntityType;
  subType: string;
  edgeId: string;

  assignToEdgeTitle: string;
  assignToEdgeText: string;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) public data: AddEntitiesToEdgeDialogData,
              private ruleChainService: RuleChainService,
              private schedulerEventService: SchedulerEventService,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<AddEntitiesToEdgeDialogComponent, boolean>,
              public fb: FormBuilder) {
    super(store, router, dialogRef);
    this.entityType = this.data.entityType;
    this.edgeId = this.data.edgeId;
  }

  ngOnInit(): void {
    this.addEntitiesToEdgeFormGroup = this.fb.group({
      entityIds: [null, [Validators.required]]
    });
    switch (this.entityType) {
      case EntityType.RULE_CHAIN:
        this.assignToEdgeTitle = 'rulechain.assign-rulechain-to-edge-title';
        this.assignToEdgeText = 'rulechain.assign-rulechain-to-edge-text';
        break;
      case EntityType.SCHEDULER_EVENT:
        this.assignToEdgeTitle = 'rulechain.assign-scheduler-event-to-edge-title';
        this.assignToEdgeText = 'rulechain.assign-scheduler-event-to-edge-text';
        break;
    }
    this.subType = RuleChainType.EDGE;
  }

  isErrorState(control: FormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(false);
  }

  assign(): void {
    this.submitted = true;
    const entityIds: Array<string> = this.addEntitiesToEdgeFormGroup.get('entityIds').value;
    const tasks: Observable<any>[] = [];
    entityIds.forEach(
      (entityId) => {
        switch (this.entityType) {
          case EntityType.RULE_CHAIN:
            tasks.push(this.ruleChainService.assignRuleChainToEdge(this.edgeId, entityId));
            break;
          case EntityType.SCHEDULER_EVENT:
            tasks.push(this.schedulerEventService.assignSchedulerEventToEdge(this.edgeId, entityId));
            break;
        }
      }
    );
    forkJoin(tasks).subscribe(
      () => {
        this.dialogRef.close(true);
      }
    );
  }

}
