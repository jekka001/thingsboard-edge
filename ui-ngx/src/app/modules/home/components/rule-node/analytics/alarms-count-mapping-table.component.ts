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

import { Component, forwardRef, Injector, Input, OnInit } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  AlarmSeverity,
  alarmSeverityTranslations,
  AlarmStatus,
  alarmStatusTranslations,
  MillisecondsToTimeStringPipe,
  PageComponent
} from '@shared/public-api';
import { Store } from '@ngrx/store';
import { AppState, deepClone } from '@core/public-api';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { TranslateService } from '@ngx-translate/core';
import { MatDialog } from '@angular/material/dialog';
import { AlarmsCountMapping } from './alarms-count-mapping.models';
import {
  AlarmsCountMappingDialogComponent,
  AlarmsCountMappingDialogData
} from './alarms-count-mapping-dialog.component';

@Component({
  selector: 'tb-alarms-count-mapping-table',
  templateUrl: './alarms-count-mapping-table.component.html',
  styleUrls: ['./alarms-count-mapping-table.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => AlarmsCountMappingTableComponent),
      multi: true
    }
  ]
})
export class AlarmsCountMappingTableComponent extends PageComponent implements ControlValueAccessor, OnInit {

  @Input() disabled: boolean;

  private requiredValue: boolean;
  get required(): boolean {
    return this.requiredValue;
  }
  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  mappings: Array<AlarmsCountMapping>;

  private propagateChange = null;

  constructor(protected store: Store<AppState>,
              public translate: TranslateService,
              public injector: Injector,
              private millisecondsToTimeStringPipe: MillisecondsToTimeStringPipe,
              private dialog: MatDialog) {
    super(store);
  }

  ngOnInit(): void {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  writeValue(mappings: Array<AlarmsCountMapping>): void {
    this.mappings = mappings || [];
  }

  public mappingDisplayValue(alarmsCountMapping: AlarmsCountMapping): string {
    let toDisplay = this.translate.instant('rule-node-config.func-count') + '(' + this.filterText(alarmsCountMapping);
    if (alarmsCountMapping.latestInterval > 0) {
      toDisplay += ' '  + this.translate.instant('rule-node-config.for') +
        ' ' + this.translate.instant('timewindow.last-prefix') +
        ' ' + this.millisecondsToTimeStringPipe.transform(alarmsCountMapping.latestInterval);
    }
    toDisplay = toDisplay.trim();
    toDisplay += ')';
    toDisplay += ' -> ' + alarmsCountMapping.target;
    return toDisplay;
  }

  private filterText(alarmsCountMapping: AlarmsCountMapping): string {
    let filterText = '';
    filterText = this.statusFilterText(alarmsCountMapping.statusList, filterText);
    filterText = this.severityFilterText(alarmsCountMapping.severityList, filterText);
    filterText = this.typeFilterText(alarmsCountMapping.typesList, filterText);
    if (!filterText.length) {
      filterText = this.translate.instant('rule-node-config.all-alarms') + '';
    }
    return filterText;
  }

  private statusFilterText(statusList: AlarmStatus[], text: string): string {
    const result: string[] = [];
    if (statusList && statusList.length) {
      statusList.forEach((status) =>
        result.push(this.translate.instant(alarmStatusTranslations.get(status))));
    }
    return this.updateFilterText(result, text);
  }

  private severityFilterText(severityList: AlarmSeverity[], text: string): string {
    const result: string[] = [];
    if (severityList && severityList.length) {
      severityList.forEach((severity) =>
        result.push(this.translate.instant(alarmSeverityTranslations.get(severity))));
    }
    return this.updateFilterText(result, text);
  }

  private typeFilterText(typesList: string[], text: string): string {
    const result: string[] = [];
    if (typesList && typesList.length) {
      typesList.forEach((type) => result.push(type));
    }
    return this.updateFilterText(result, text);
  }

  private updateFilterText(resultList: string[], text: string): string {
    const filterText = resultList.join(', ');
    if (filterText && filterText.length) {
      if (text.length) {
        text += ' ' + this.translate.instant('rule-node-config.and') + ' ';
      }
      text += filterText;
    }
    return text;
  }

  public removeAlarmsCountMapping(index: number) {
    if (index > -1) {
      this.mappings.splice(index, 1);
      this.updateModel();
    }
  }

  public addAlarmsCountMapping($event: Event) {
    this.openAlarmsCountMappingDialog($event);
  }

  public editAlarmsCountMapping($event: Event, aggMapping: AlarmsCountMapping) {
    this.openAlarmsCountMappingDialog($event, aggMapping);
  }

  private openAlarmsCountMappingDialog($event: Event, mapping?: AlarmsCountMapping) {
    if ($event) {
      $event.stopPropagation();
    }
    const isAdd = !mapping;
    let mappingIndex: number;
    if (!isAdd) {
      mappingIndex = this.mappings.indexOf(mapping);
    }
    this.dialog.open<AlarmsCountMappingDialogComponent,
      AlarmsCountMappingDialogData, AlarmsCountMapping>(AlarmsCountMappingDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        isAdd,
        mapping: isAdd ? null : deepClone(mapping)
      }
    }).afterClosed().subscribe(
      (newMapping) => {
        if (newMapping) {
          if (isAdd) {
            this.mappings.push(newMapping);
          } else {
            this.mappings[mappingIndex] = newMapping;
          }
          this.updateModel();
        }
      }
    );
  }

  private updateModel() {
    if (this.required && !this.mappings.length) {
      this.propagateChange(null);
    } else {
      this.propagateChange(this.mappings);
    }
  }
}
