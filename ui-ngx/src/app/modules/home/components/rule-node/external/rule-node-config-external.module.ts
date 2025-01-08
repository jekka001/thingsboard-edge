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
import { SnsConfigComponent } from './sns-config.component';
import { SqsConfigComponent } from './sqs-config.component';
import { PubSubConfigComponent } from './pubsub-config.component';
import { KafkaConfigComponent } from './kafka-config.component';
import { MqttConfigComponent } from './mqtt-config.component';
import { NotificationConfigComponent } from './notification-config.component';
import { RabbitMqConfigComponent } from './rabbit-mq-config.component';
import { RestApiCallConfigComponent } from './rest-api-call-config.component';
import { SendEmailConfigComponent } from './send-email-config.component';
import { AzureIotHubConfigComponent } from './azure-iot-hub-config.component';
import { SendSmsConfigComponent } from './send-sms-config.component';
import { CommonModule } from '@angular/common';
import { IRuleNodeConfigurationComponent, SharedModule } from '@shared/public-api';
import { HomeComponentsModule } from '@home/components/public-api';
import { RuleNodeConfigCommonModule } from '../common/rule-node-config-common.module';
import { SlackConfigComponent } from './slack-config.component';
import { LambdaConfigComponent } from './lambda-config.component';

@NgModule({
  declarations: [
    SnsConfigComponent,
    SqsConfigComponent,
    LambdaConfigComponent,
    PubSubConfigComponent,
    KafkaConfigComponent,
    MqttConfigComponent,
    NotificationConfigComponent,
    RabbitMqConfigComponent,
    RestApiCallConfigComponent,
    SendEmailConfigComponent,
    AzureIotHubConfigComponent,
    SendSmsConfigComponent,
    SlackConfigComponent
  ],
  imports: [
    CommonModule,
    SharedModule,
    HomeComponentsModule,
    RuleNodeConfigCommonModule
  ],
  exports: [
    SnsConfigComponent,
    SqsConfigComponent,
    LambdaConfigComponent,
    PubSubConfigComponent,
    KafkaConfigComponent,
    MqttConfigComponent,
    NotificationConfigComponent,
    RabbitMqConfigComponent,
    RestApiCallConfigComponent,
    SendEmailConfigComponent,
    AzureIotHubConfigComponent,
    SendSmsConfigComponent,
    SlackConfigComponent
  ]
})
export class RuleNodeConfigExternalModule {
}

export const ruleNodeExternalConfigComponentsMap: Record<string, Type<IRuleNodeConfigurationComponent>> = {
  'tbExternalNodeAzureIotHubConfig': AzureIotHubConfigComponent,
  'tbExternalNodeKafkaConfig': KafkaConfigComponent,
  'tbExternalNodeLambdaConfig': LambdaConfigComponent,
  'tbExternalNodeMqttConfig': MqttConfigComponent,
  'tbExternalNodeNotificationConfig': NotificationConfigComponent,
  'tbExternalNodePubSubConfig': PubSubConfigComponent,
  'tbExternalNodeRabbitMqConfig': RabbitMqConfigComponent,
  'tbExternalNodeRestApiCallConfig': RestApiCallConfigComponent,
  'tbExternalNodeSendEmailConfig': SendEmailConfigComponent,
  'tbExternalNodeSendSmsConfig': SendSmsConfigComponent,
  'tbExternalNodeSlackConfig': SlackConfigComponent,
  'tbExternalNodeSnsConfig': SnsConfigComponent,
  'tbExternalNodeSqsConfig': SqsConfigComponent
}
