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

import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { FormGroup, NG_VALIDATORS, NG_VALUE_ACCESSOR } from '@angular/forms';
import {
  MappingType,
  OPCBasicConfig_v3_5_2,
  OPCLegacyBasicConfig, ServerConfig,
} from '@home/components/widget/lib/gateway/gateway-widget.models';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { MappingTableComponent } from '@home/components/widget/lib/gateway/connectors-configuration/mapping-table/mapping-table.component';
import {
  SecurityConfigComponent
} from '@home/components/widget/lib/gateway/connectors-configuration/security-config/security-config.component';
import {
  OpcServerConfigComponent
} from '@home/components/widget/lib/gateway/connectors-configuration/opc/opc-server-config/opc-server-config.component';
import {
  GatewayConnectorBasicConfigDirective
} from '@home/components/widget/lib/gateway/abstract/gateway-connector-basic-config.abstract';
import { OpcVersionMappingUtil } from '@home/components/widget/lib/gateway/utils/opc-version-mapping.util';

@Component({
  selector: 'tb-opc-ua-legacy-basic-config',
  templateUrl: './opc-ua-basic-config.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => OpcUaLegacyBasicConfigComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => OpcUaLegacyBasicConfigComponent),
      multi: true
    }
  ],
  standalone: true,
  imports: [
    CommonModule,
    SharedModule,
    SecurityConfigComponent,
    MappingTableComponent,
    OpcServerConfigComponent,
  ],
  styleUrls: ['./opc-ua-basic-config.component.scss']
})
export class OpcUaLegacyBasicConfigComponent extends GatewayConnectorBasicConfigDirective<OPCBasicConfig_v3_5_2, OPCLegacyBasicConfig> {

  mappingTypes = MappingType;

  protected override initBasicFormGroup(): FormGroup {
    return this.fb.group({
      mapping: [],
      server: [],
    });
  }

  protected override mapConfigToFormValue(config: OPCLegacyBasicConfig): OPCBasicConfig_v3_5_2 {
    return {
      server: config.server ? OpcVersionMappingUtil.mapServerToUpgradedVersion(config.server) : {} as ServerConfig,
      mapping: config.server?.mapping ? OpcVersionMappingUtil.mapMappingToUpgradedVersion(config.server.mapping) : [],
    };
  }

  protected override getMappedValue(value: OPCBasicConfig_v3_5_2): OPCLegacyBasicConfig {
    return {
      server: OpcVersionMappingUtil.mapServerToDowngradedVersion(value),
    };
  }
}
