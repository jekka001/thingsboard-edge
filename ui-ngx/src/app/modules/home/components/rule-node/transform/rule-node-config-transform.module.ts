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
import { ChangeOriginatorConfigComponent } from './change-originator-config.component';
import { RuleNodeConfigCommonModule } from '../common/rule-node-config-common.module';
import { TransformScriptConfigComponent } from './script-config.component';
import { ToEmailConfigComponent } from './to-email-config.component';
import { CopyKeysConfigComponent } from './copy-keys-config.component';
import { RenameKeysConfigComponent } from './rename-keys-config.component';
import { NodeJsonPathConfigComponent } from './node-json-path-config.component';
import { DeleteKeysConfigComponent } from './delete-keys-config.component';
import { DeduplicationConfigComponent } from './deduplication-config.component';
import { ScriptConfigComponent } from '@home/components/rule-node/filter/script-config.component';

@NgModule({
  declarations: [
    ChangeOriginatorConfigComponent,
    TransformScriptConfigComponent,
    ToEmailConfigComponent,
    CopyKeysConfigComponent,
    RenameKeysConfigComponent,
    NodeJsonPathConfigComponent,
    DeleteKeysConfigComponent,
    DeduplicationConfigComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    RuleNodeConfigCommonModule
  ],
  exports: [
    ChangeOriginatorConfigComponent,
    TransformScriptConfigComponent,
    ToEmailConfigComponent,
    CopyKeysConfigComponent,
    RenameKeysConfigComponent,
    NodeJsonPathConfigComponent,
    DeleteKeysConfigComponent,
    DeduplicationConfigComponent
  ]
})
export class RuleNodeConfigTransformModule {
}

export const ruleNodeTransformConfigComponentsMap: Record<string, Type<IRuleNodeConfigurationComponent>> = {
  'tbTransformationNodeChangeOriginatorConfig': ChangeOriginatorConfigComponent,
  'tbTransformationNodeCopyKeysConfig': CopyKeysConfigComponent,
  'tbActionNodeMsgDeduplicationConfig': DeduplicationConfigComponent,
  'tbTransformationNodeDeleteKeysConfig': DeleteKeysConfigComponent,
  'tbTransformationNodeJsonPathConfig': NodeJsonPathConfigComponent,
  'tbTransformationNodeRenameKeysConfig': RenameKeysConfigComponent,
  'tbTransformationNodeScriptConfig': ScriptConfigComponent,
  'tbTransformationNodeToEmailConfig': ToEmailConfigComponent
}
