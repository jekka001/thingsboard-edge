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

import {
  GatewayConnector,
  ModbusBasicConfig,
  ModbusBasicConfig_v3_5_2,
  ModbusLegacyBasicConfig,
  ModbusLegacySlave,
  ModbusMasterConfig,
  ModbusSlave,
} from '../gateway-widget.models';
import { GatewayConnectorVersionProcessor } from './gateway-connector-version-processor.abstract';
import { ModbusVersionMappingUtil } from '@home/components/widget/lib/gateway/utils/modbus-version-mapping.util';

export class ModbusVersionProcessor extends GatewayConnectorVersionProcessor<any> {

  constructor(
    protected gatewayVersionStr: string,
    protected connector: GatewayConnector<ModbusBasicConfig>
  ) {
    super(gatewayVersionStr, connector);
  }

  getUpgradedVersion(): GatewayConnector<ModbusBasicConfig_v3_5_2> {
    const configurationJson = this.connector.configurationJson;
    return {
      ...this.connector,
      configurationJson: {
        master: configurationJson.master
          ? ModbusVersionMappingUtil.mapMasterToUpgradedVersion(configurationJson.master)
          : {} as ModbusMasterConfig,
        slave: configurationJson.slave
          ? ModbusVersionMappingUtil.mapSlaveToUpgradedVersion(configurationJson.slave as ModbusLegacySlave)
          : {} as ModbusSlave,
      },
      configVersion: this.gatewayVersionStr
    } as GatewayConnector<ModbusBasicConfig_v3_5_2>;
  }

  getDowngradedVersion(): GatewayConnector<ModbusLegacyBasicConfig> {
    const configurationJson = this.connector.configurationJson;
    return {
      ...this.connector,
      configurationJson: {
        ...configurationJson,
        slave: configurationJson.slave
          ? ModbusVersionMappingUtil.mapSlaveToDowngradedVersion(configurationJson.slave as ModbusSlave)
          : {} as ModbusLegacySlave,
        master: configurationJson.master,
      },
      configVersion: this.gatewayVersionStr
    } as GatewayConnector<ModbusLegacyBasicConfig>;
  }
}
