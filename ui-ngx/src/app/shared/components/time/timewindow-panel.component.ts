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

import { Component, Inject, InjectionToken, OnInit, ViewContainerRef } from '@angular/core';
import {
  aggregationTranslations,
  AggregationType,
  DAY,
  HistoryWindowType,
  Timewindow,
  TimewindowType
} from '@shared/models/time/time.models';
import { OverlayRef } from '@angular/cdk/overlay';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { TimeService } from '@core/services/time.service';

export const TIMEWINDOW_PANEL_DATA = new InjectionToken<any>('TimewindowPanelData');

export interface TimewindowPanelData {
  historyOnly: boolean;
  timewindow: Timewindow;
  aggregation: boolean;
  isEdit: boolean;
}

@Component({
  selector: 'tb-timewindow-panel',
  templateUrl: './timewindow-panel.component.html',
  styleUrls: ['./timewindow-panel.component.scss']
})
export class TimewindowPanelComponent extends PageComponent implements OnInit {

  historyOnly = false;

  aggregation = false;

  isEdit = false;

  timewindow: Timewindow;

  result: Timewindow;

  timewindowForm: FormGroup;

  historyTypes = HistoryWindowType;

  timewindowTypes = TimewindowType;

  aggregationTypes = AggregationType;

  aggregations = Object.keys(AggregationType);

  aggregationTypesTranslations = aggregationTranslations;

  constructor(@Inject(TIMEWINDOW_PANEL_DATA) public data: TimewindowPanelData,
              public overlayRef: OverlayRef,
              protected store: Store<AppState>,
              public fb: FormBuilder,
              private timeService: TimeService,
              public viewContainerRef: ViewContainerRef) {
    super(store);
    this.historyOnly = data.historyOnly;
    this.timewindow = data.timewindow;
    this.aggregation = data.aggregation;
    this.isEdit = data.isEdit;
  }

  ngOnInit(): void {
    const hideInterval = this.timewindow.hideInterval || false;
    const hideAggregation = this.timewindow.hideAggregation || false;
    const hideAggInterval = this.timewindow.hideAggInterval || false;

    this.timewindowForm = this.fb.group({
        realtime: this.fb.group(
          {
            timewindowMs: [
              this.timewindow.realtime && typeof this.timewindow.realtime.timewindowMs !== 'undefined'
                ? this.timewindow.realtime.timewindowMs : null
            ],
            interval: [
              this.timewindow.realtime && typeof this.timewindow.realtime.interval !== 'undefined'
                ? this.timewindow.realtime.interval : null
            ]
          }
        ),
        history: this.fb.group(
          {
            historyType: this.fb.control({
              value: this.timewindow.history && typeof this.timewindow.history.historyType !== 'undefined'
                ? this.timewindow.history.historyType : HistoryWindowType.LAST_INTERVAL,
              disabled: hideInterval
            }),
            timewindowMs: this.fb.control({
              value: this.timewindow.history && typeof this.timewindow.history.timewindowMs !== 'undefined'
                ? this.timewindow.history.timewindowMs : null,
              disabled: hideInterval
            }),
            interval: [
              this.timewindow.history && typeof this.timewindow.history.interval !== 'undefined'
                ? this.timewindow.history.interval : null
            ],
            fixedTimewindow: this.fb.control({
              value: this.timewindow.history && typeof this.timewindow.history.fixedTimewindow !== 'undefined'
                ? this.timewindow.history.fixedTimewindow : null,
              disabled: hideInterval
            })
          }
        ),
        aggregation: this.fb.group(
          {
            type: this.fb.control({
              value: this.timewindow.aggregation && typeof this.timewindow.aggregation.type !== 'undefined'
                ? this.timewindow.aggregation.type : null,
              disabled: hideAggregation
            }),
            limit: this.fb.control({
              value: this.timewindow.aggregation && typeof this.timewindow.aggregation.limit !== 'undefined'
                ? this.timewindow.aggregation.limit : null,
              disabled: hideAggInterval
            }, [Validators.min(this.minDatapointsLimit()), Validators.max(this.maxDatapointsLimit())])
          }
        )
    });
  }

  update() {
    const timewindowFormValue = this.timewindowForm.getRawValue();
    this.timewindow.realtime = {
      timewindowMs: timewindowFormValue.realtime.timewindowMs,
      interval: timewindowFormValue.realtime.interval
    };
    this.timewindow.history = {
      historyType: timewindowFormValue.history.historyType,
      timewindowMs: timewindowFormValue.history.timewindowMs,
      interval: timewindowFormValue.history.interval,
      fixedTimewindow: timewindowFormValue.history.fixedTimewindow
    };
    if (this.aggregation) {
      this.timewindow.aggregation = {
        type: timewindowFormValue.aggregation.type,
        limit: timewindowFormValue.aggregation.limit
      };
    }
    this.result = this.timewindow;
    this.overlayRef.dispose();
  }

  cancel() {
    this.overlayRef.dispose();
  }

  minDatapointsLimit() {
    return this.timeService.getMinDatapointsLimit();
  }

  maxDatapointsLimit() {
    return this.timeService.getMaxDatapointsLimit();
  }

  minRealtimeAggInterval() {
    return this.timeService.minIntervalLimit(this.timewindowForm.get('realtime.timewindowMs').value);
  }

  maxRealtimeAggInterval() {
    return this.timeService.maxIntervalLimit(this.timewindowForm.get('realtime.timewindowMs').value);
  }

  minHistoryAggInterval() {
    return this.timeService.minIntervalLimit(this.currentHistoryTimewindow());
  }

  maxHistoryAggInterval() {
    return this.timeService.maxIntervalLimit(this.currentHistoryTimewindow());
  }

  currentHistoryTimewindow() {
    const timewindowFormValue = this.timewindowForm.getRawValue();
    if (timewindowFormValue.history.historyType === HistoryWindowType.LAST_INTERVAL) {
      return timewindowFormValue.history.timewindowMs;
    } else if (timewindowFormValue.history.fixedTimewindow) {
      return timewindowFormValue.history.fixedTimewindow.endTimeMs -
        timewindowFormValue.history.fixedTimewindow.startTimeMs;
    } else {
      return DAY;
    }
  }

  onHideIntervalChanged() {
    if (this.timewindow.hideInterval) {
      this.timewindowForm.get('history.historyType').disable({emitEvent: false});
      this.timewindowForm.get('history.timewindowMs').disable({emitEvent: false});
      this.timewindowForm.get('history.fixedTimewindow').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('history.historyType').enable({emitEvent: false});
      this.timewindowForm.get('history.timewindowMs').enable({emitEvent: false});
      this.timewindowForm.get('history.fixedTimewindow').enable({emitEvent: false});
    }
    this.timewindowForm.markAsDirty();
  }

  onHideAggregationChanged() {
    if (this.timewindow.hideAggregation) {
      this.timewindowForm.get('aggregation.type').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('aggregation.type').enable({emitEvent: false});
    }
    this.timewindowForm.markAsDirty();
  }

  onHideAggIntervalChanged() {
    if (this.timewindow.hideAggInterval) {
      this.timewindowForm.get('aggregation.limit').disable({emitEvent: false});
    } else {
      this.timewindowForm.get('aggregation.limit').enable({emitEvent: false});
    }
    this.timewindowForm.markAsDirty();
  }

}
