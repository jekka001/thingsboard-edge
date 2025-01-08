///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import { NgModule, Type } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IRuleNodeConfigurationComponent, SharedModule } from '@shared/public-api';
import { CheckMessageConfigComponent } from './check-message-config.component';
import { CheckRelationConfigComponent } from './check-relation-config.component';
import { GpsGeoFilterConfigComponent } from './gps-geo-filter-config.component';
import { MessageTypeConfigComponent } from './message-type-config.component';
import { OriginatorTypeConfigComponent } from './originator-type-config.component';
import { ScriptConfigComponent } from './script-config.component';
import { SwitchConfigComponent } from './switch-config.component';
import { CheckAlarmStatusComponent } from './check-alarm-status.component';
import { RuleNodeConfigCommonModule } from '../common/rule-node-config-common.module';

@NgModule({
  declarations: [
    CheckMessageConfigComponent,
    CheckRelationConfigComponent,
    GpsGeoFilterConfigComponent,
    MessageTypeConfigComponent,
    OriginatorTypeConfigComponent,
    ScriptConfigComponent,
    SwitchConfigComponent,
    CheckAlarmStatusComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    RuleNodeConfigCommonModule
  ],
  exports: [
    CheckMessageConfigComponent,
    CheckRelationConfigComponent,
    GpsGeoFilterConfigComponent,
    MessageTypeConfigComponent,
    OriginatorTypeConfigComponent,
    ScriptConfigComponent,
    SwitchConfigComponent,
    CheckAlarmStatusComponent
  ]
})
export class RuleNodeConfigFilterModule {
}

export const ruleNodeFilterConfigComponentsMap: Record<string, Type<IRuleNodeConfigurationComponent>> = {
  'tbFilterNodeCheckAlarmStatusConfig': CheckAlarmStatusComponent,
  'tbFilterNodeCheckMessageConfig': CheckMessageConfigComponent,
  'tbFilterNodeCheckRelationConfig': CheckRelationConfigComponent,
  'tbFilterNodeGpsGeofencingConfig': GpsGeoFilterConfigComponent,
  'tbFilterNodeMessageTypeConfig': MessageTypeConfigComponent,
  'tbFilterNodeOriginatorTypeConfig': OriginatorTypeConfigComponent,
  'tbFilterNodeScriptConfig': ScriptConfigComponent,
  'tbFilterNodeSwitchConfig': SwitchConfigComponent
}
