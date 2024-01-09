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

import { Component, Inject, OnInit, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { Observable, ReplaySubject } from 'rxjs';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import {
  AlarmInfo,
  alarmSeverityColors,
  alarmSeverityTranslations,
  AlarmStatus,
  alarmStatusTranslations
} from '@app/shared/models/alarm.models';
import { AlarmService } from '@core/http/alarm.service';
import { tap } from 'rxjs/operators';
import { DatePipe } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { AlarmCommentComponent } from '@home/components/alarm/alarm-comment.component';
import { MillisecondsToTimeStringPipe } from '@shared/pipe/milliseconds-to-time-string.pipe';
import { UtilsService } from '@core/services/utils.service';

export interface AlarmDetailsDialogData {
  alarmId?: string;
  alarm?: AlarmInfo;
  allowAcknowledgment: boolean;
  allowClear: boolean;
  displayDetails: boolean;
  allowAssign: boolean;
}

@Component({
  selector: 'tb-alarm-details-dialog',
  templateUrl: './alarm-details-dialog.component.html',
  styleUrls: ['./alarm-details-dialog.component.scss']
})
export class AlarmDetailsDialogComponent extends DialogComponent<AlarmDetailsDialogComponent, boolean> implements OnInit {

  alarmId: string;
  alarmFormGroup: UntypedFormGroup;

  allowAcknowledgment: boolean;
  allowClear: boolean;
  displayDetails: boolean;
  allowAssign: boolean;

  loadAlarmSubject = new ReplaySubject<AlarmInfo>();
  alarm$: Observable<AlarmInfo> = this.loadAlarmSubject.asObservable().pipe(
    tap(alarm => this.loadAlarmFields(alarm))
  );

  alarmSeverityColorsMap = alarmSeverityColors;
  alarmStatuses = AlarmStatus;

  alarmUpdated = false;

  @ViewChild('alarmCommentComponent', { static: true }) alarmCommentComponent: AlarmCommentComponent;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              private datePipe: DatePipe,
              private millisecondsToTimeStringPipe: MillisecondsToTimeStringPipe,
              private translate: TranslateService,
              @Inject(MAT_DIALOG_DATA) public data: AlarmDetailsDialogData,
              private alarmService: AlarmService,
              private utils: UtilsService,
              public dialogRef: MatDialogRef<AlarmDetailsDialogComponent, boolean>,
              public fb: UntypedFormBuilder) {
    super(store, router, dialogRef);

    this.allowAcknowledgment = data.allowAcknowledgment;
    this.allowClear = data.allowClear;
    this.displayDetails = data.displayDetails;
    this.allowAssign = data.allowAssign;

    this.alarmFormGroup = this.fb.group(
      {
        originatorName: [''],
        alarmSeverity: [''],
        startTime: [''],
        duration: [''],
        type: [''],
        alarmStatus: [''],
        alarmDetails: [null]
      }
    );

    if (!this.data.alarm) {
      this.alarmId = this.data.alarmId;
      this.loadAlarm();
    } else {
      this.alarmId = this.data.alarm?.id?.id;
      setTimeout(() => {
        this.loadAlarmSubject.next(this.data.alarm);
      }, 0);
    }
  }

  loadAlarm() {
    this.alarmService.getAlarmInfo(this.alarmId, {ignoreLoading: true}).subscribe(
      alarm => this.loadAlarmSubject.next(alarm)
    );
  }

  loadAlarmFields(alarm: AlarmInfo) {
    this.alarmFormGroup.get('originatorName')
      .patchValue(alarm.originatorLabel ? alarm.originatorLabel : alarm.originatorName);
    this.alarmFormGroup.get('alarmSeverity')
      .patchValue(this.translate.instant(alarmSeverityTranslations.get(alarm.severity)));
    if (alarm.startTs) {
      this.alarmFormGroup.get('startTime')
        .patchValue(this.datePipe.transform(alarm.startTs, 'yyyy-MM-dd HH:mm:ss'));
    }
    if (alarm.startTs || alarm.endTs) {
      let duration = '';
      if (alarm.startTs && (alarm.status === AlarmStatus.ACTIVE_ACK || alarm.status === AlarmStatus.ACTIVE_UNACK)) {
        duration = this.millisecondsToTimeStringPipe.transform(Date.now() - alarm.startTs);
      }
      if (alarm.endTs && (alarm.status === AlarmStatus.CLEARED_ACK || alarm.status === AlarmStatus.CLEARED_UNACK)) {
        duration = this.millisecondsToTimeStringPipe.transform(alarm.endTs - alarm.startTs);
      }
      this.alarmFormGroup.get('duration').patchValue(duration);
    }
    this.alarmFormGroup.get('type').patchValue(this.utils.customTranslation(alarm.type, alarm.type));
    this.alarmFormGroup.get('alarmStatus')
      .patchValue(this.translate.instant(alarmStatusTranslations.get(alarm.status)));
    if (alarm.details) {
      let stringDetails = JSON.stringify(alarm.details, undefined, 2);
      stringDetails = this.utils.customTranslation(stringDetails, stringDetails);
      const details = JSON.parse(stringDetails);
      this.alarmFormGroup.get('alarmDetails').patchValue(details);
    } else {
      this.alarmFormGroup.get('alarmDetails').patchValue(null);
    }
  }

  ngOnInit(): void {
  }

  close(): void {
    this.dialogRef.close(this.alarmUpdated);
  }

  acknowledge(): void {
    if (this.alarmId) {
      this.alarmService.ackAlarm(this.alarmId).subscribe(
        () => {
          this.alarmUpdated = true;
          this.loadAlarm();
          this.alarmCommentComponent.loadAlarmComments();
        }
      );
    }
  }

  clear(): void {
    if (this.alarmId) {
      this.alarmService.clearAlarm(this.alarmId).subscribe(
        () => {
          this.alarmUpdated = true;
          this.loadAlarm();
          this.alarmCommentComponent.loadAlarmComments();
        }
      );
    }
  }

  onReassign(): void {
    this.alarmUpdated = true;
    this.loadAlarm();
    this.alarmCommentComponent.loadAlarmComments();
  }
}
