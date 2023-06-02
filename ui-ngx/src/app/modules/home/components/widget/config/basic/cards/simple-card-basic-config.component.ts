///
/// Copyright © 2016-2023 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { BasicWidgetConfigComponent } from '@home/components/widget/config/widget-config.component.models';
import { WidgetConfigComponentData } from '@home/models/widget-component.models';
import {
  Datasource,
  datasourcesHasAggregation,
  datasourcesHasOnlyComparisonAggregation
} from '@shared/models/widget.models';

@Component({
  selector: 'tb-simple-card-basic-config',
  templateUrl: './simple-card-basic-config.component.html',
  styleUrls: ['../basic-config.scss', '../../widget-config.scss']
})
export class SimpleCardBasicConfigComponent extends BasicWidgetConfigComponent {

  public get displayTimewindowConfig(): boolean {
    const datasources = this.simpleCardWidgetConfigForm.get('datasources').value;
    return datasourcesHasAggregation(datasources);
  }

  public onlyHistoryTimewindow(): boolean {
    const datasources = this.simpleCardWidgetConfigForm.get('datasources').value;
    return datasourcesHasOnlyComparisonAggregation(datasources);
  }

  simpleCardWidgetConfigForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  protected configForm(): UntypedFormGroup {
    return this.simpleCardWidgetConfigForm;
  }

  protected onConfigSet(configData: WidgetConfigComponentData) {
    this.simpleCardWidgetConfigForm = this.fb.group({
      timewindowConfig: [{
        useDashboardTimewindow: configData.config.useDashboardTimewindow,
        displayTimewindow: configData.config.useDashboardTimewindow,
        timewindow: configData.config.timewindow
      }, []],
      datasources: [configData.config.datasources, []],
      label: [this.getDataKeyLabel(configData.config.datasources), []],
      labelPosition: [configData.config.settings?.labelPosition, []],
      units: [configData.config.units, []],
      decimals: [configData.config.decimals, []],
      color: [configData.config.color, []],
      backgroundColor: [configData.config.backgroundColor, []],
      actions: [configData.config.actions || {}, []]
    });
  }

  protected prepareOutputConfig(config: any): WidgetConfigComponentData {
    this.widgetConfig.config.useDashboardTimewindow = config.timewindowConfig.useDashboardTimewindow;
    this.widgetConfig.config.displayTimewindow = config.timewindowConfig.displayTimewindow;
    this.widgetConfig.config.timewindow = config.timewindowConfig.timewindow;
    this.widgetConfig.config.datasources = config.datasources;
    this.setDataKeyLabel(config.label, this.widgetConfig.config.datasources);
    this.widgetConfig.config.actions = config.actions;
    this.widgetConfig.config.units = config.units;
    this.widgetConfig.config.decimals = config.decimals;
    this.widgetConfig.config.color = config.color;
    this.widgetConfig.config.backgroundColor = config.backgroundColor;
    this.widgetConfig.config.settings = this.widgetConfig.config.settings || {};
    this.widgetConfig.config.settings.labelPosition = config.labelPosition;
    return this.widgetConfig;
  }

  private getDataKeyLabel(datasources?: Datasource[]): string {
    if (datasources && datasources.length) {
      const dataKeys = datasources[0].dataKeys;
      if (dataKeys && dataKeys.length) {
        return dataKeys[0].label;
      }
    }
    return '';
  }

  private setDataKeyLabel(label: string, datasources?: Datasource[]) {
    if (datasources && datasources.length) {
      const dataKeys = datasources[0].dataKeys;
      if (dataKeys && dataKeys.length) {
        dataKeys[0].label = label;
      }
    }
  }

}
