///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

import { NotificationId } from '@shared/models/id/notification-id';
import { NotificationRequestId } from '@shared/models/id/notification-request-id';
import { UserId } from '@shared/models/id/user-id';
import { BaseData } from '@shared/models/base-data';
import { TenantId } from '@shared/models/id/tenant-id';
import { NotificationTargetId } from '@shared/models/id/notification-target-id';
import { NotificationTemplateId } from '@shared/models/id/notification-template-id';
import { EntityId } from '@shared/models/id/entity-id';
import { NotificationRuleId } from '@shared/models/id/notification-rule-id';
import { AlarmSearchStatus, AlarmSeverity, AlarmStatus } from '@shared/models/alarm.models';
import { EntityType } from '@shared/models/entity-type.models';

export interface Notification {
  readonly id: NotificationId;
  readonly requestId: NotificationRequestId;
  readonly recipientId: UserId;
  readonly type: NotificationType;
  readonly subject: string;
  readonly text: string;
  readonly info: NotificationInfo;
  readonly status: NotificationStatus;
  readonly createdTime: number;
  readonly additionalConfig?: WebDeliveryMethodAdditionalConfig;
}

export interface NotificationInfo {
  description: string;
  type: string;
  alarmSeverity?: AlarmSeverity;
  alarmStatus?: AlarmStatus;
  alarmType?: string;
  stateEntityId?: EntityId;
}

export interface NotificationRequest extends Omit<BaseData<NotificationRequestId>, 'label'> {
  tenantId?: TenantId;
  targets: Array<string>;
  templateId?: NotificationTemplateId;
  template?: NotificationTemplate;
  info?: NotificationInfo;
  deliveryMethods: Array<NotificationDeliveryMethod>;
  originatorEntityId: EntityId;
  status: NotificationRequestStatus;
  stats: NotificationRequestStats;
  additionalConfig: NotificationRequestConfig;
}

export interface NotificationRequestInfo extends NotificationRequest {
  templateName: string;
  deliveryMethods: NotificationDeliveryMethod[];
}

export interface NotificationRequestPreview {
  totalRecipientsCount: number;
  recipientsCountByTarget: { [key in string]: number };
  processedTemplates: { [key in NotificationDeliveryMethod]: DeliveryMethodNotificationTemplate };
}

export interface NotificationRequestStats {
  sent: Map<NotificationDeliveryMethod, any>;
  errors: { [key in NotificationDeliveryMethod]: {[errorKey in string]: string}};
  processedRecipients: Map<NotificationDeliveryMethod, Set<UserId>>;
}

export interface NotificationRequestConfig {
  sendingDelayInSec: number;
}

export interface NotificationSettings {
  deliveryMethodsConfigs: { [key in NotificationDeliveryMethod]: NotificationDeliveryMethodConfig };
}

export interface NotificationDeliveryMethodConfig extends Partial<SlackNotificationDeliveryMethodConfig>{
  enabled: boolean;
  method: NotificationDeliveryMethod;
}

interface SlackNotificationDeliveryMethodConfig {
  botToken: string;
}

export interface SlackConversation {
  id: string;
  name: string;
}

export interface NotificationRule extends Omit<BaseData<NotificationRuleId>, 'label'>{
  tenantId: TenantId;
  templateId: NotificationTemplateId;
  triggerType: TriggerType;
  triggerConfig: NotificationRuleTriggerConfig;
  recipientsConfig: NotificationRuleRecipientConfig;
  additionalConfig: {description: string};
}

export type NotificationRuleTriggerConfig = Partial<AlarmNotificationRuleTriggerConfig & DeviceInactivityNotificationRuleTriggerConfig &
  EntityActionNotificationRuleTriggerConfig & AlarmCommentNotificationRuleTriggerConfig & AlarmAssignmentNotificationRuleTriggerConfig &
  RuleEngineLifecycleEventNotificationRuleTriggerConfig & EntitiesLimitNotificationRuleTriggerConfig>;

export interface AlarmNotificationRuleTriggerConfig {
  alarmTypes?: Array<string>;
  alarmSeverities?: Array<AlarmSeverity>;
  notifyOn: Array<AlarmAction>;
  clearRule?: ClearRule;
}

interface ClearRule {
  alarmStatuses: Array<AlarmSearchStatus>;
}

export interface DeviceInactivityNotificationRuleTriggerConfig {
  devices?: Array<string>;
  deviceProfiles?: Array<string>;
}

export interface EntityActionNotificationRuleTriggerConfig {
  entityType: EntityType;
  created: boolean;
  updated: boolean;
  deleted: boolean;
}

export interface AlarmCommentNotificationRuleTriggerConfig {
  alarmTypes?: Array<string>;
  alarmSeverities?: Array<AlarmSeverity>;
  alarmStatuses?: Array<AlarmSearchStatus>;
  onlyUserComments?: boolean;
  notifyOnCommentUpdate?: boolean;
}

export interface AlarmAssignmentNotificationRuleTriggerConfig {
  alarmTypes?: Array<string>;
  alarmSeverities?: Array<AlarmSeverity>;
  alarmStatuses?: Array<AlarmSearchStatus>;
  notifyOn: Array<AlarmAssignmentAction>;
}

export interface RuleEngineLifecycleEventNotificationRuleTriggerConfig {
  ruleChains?: Array<string>;
  ruleChainEvents?: Array<ComponentLifecycleEvent>;
  onlyRuleChainLifecycleFailures: boolean;
  trackRuleNodeEvents: boolean;
  ruleNodeEvents: Array<any>;
  onlyRuleNodeLifecycleFailures: ComponentLifecycleEvent;
}

export interface EntitiesLimitNotificationRuleTriggerConfig {
  entityTypes: EntityType[];
  threshold: number;
}

export enum ComponentLifecycleEvent {
  STARTED = 'STARTED',
  UPDATED = 'UPDATED',
  STOPPED = 'STOPPED'
}

export const ComponentLifecycleEventTranslationMap = new Map<ComponentLifecycleEvent, string>([
  [ComponentLifecycleEvent.STARTED, 'event.started'],
  [ComponentLifecycleEvent.UPDATED, 'event.updated'],
  [ComponentLifecycleEvent.STOPPED, 'event.stopped']
]);

export enum AlarmAction {
  CREATED = 'CREATED',
  SEVERITY_CHANGED = 'SEVERITY_CHANGED',
  ACKNOWLEDGED = 'ACKNOWLEDGED',
  CLEARED = 'CLEARED'
}

export const AlarmActionTranslationMap = new Map<AlarmAction, string>([
  [AlarmAction.CREATED, 'notification.notify-alarm-action.created'],
  [AlarmAction.SEVERITY_CHANGED, 'notification.notify-alarm-action.severity-changed'],
  [AlarmAction.ACKNOWLEDGED, 'notification.notify-alarm-action.acknowledged'],
  [AlarmAction.CLEARED, 'notification.notify-alarm-action.cleared']
]);

export enum AlarmAssignmentAction {
  ASSIGNED = 'ASSIGNED',
  UNASSIGNED = 'UNASSIGNED'
}

export const AlarmAssignmentActionTranslationMap = new Map<AlarmAssignmentAction, string>([
  [AlarmAssignmentAction.ASSIGNED, 'notification.notify-alarm-action.assigned'],
  [AlarmAssignmentAction.UNASSIGNED, 'notification.notify-alarm-action.unassigned']
]);

export interface NotificationRuleRecipientConfig {
  targets?: Array<string>;
  escalationTable?: {[key: number]: Array<string>};
}

export interface NonConfirmedNotificationEscalation {
  delayInSec: number;
  targets: Array<string>;
}

export interface NotificationTarget extends Omit<BaseData<NotificationTargetId>, 'label'>{
  tenantId: TenantId;
  configuration: NotificationTargetConfig;
}

export interface NotificationTargetConfig extends Partial<PlatformUsersNotificationTargetConfig & SlackNotificationTargetConfig> {
  description?: string;
  type: NotificationTargetType;
}
export interface PlatformUsersNotificationTargetConfig {
  usersFilter: UsersFilter;
}

export interface UsersFilter extends
  Partial<UserListFilter & CustomerUsersFilter & TenantAdministratorsFilter & UserGroupListFilter & UserRoleFilter>{
  type: NotificationTargetConfigType;
}

interface UserListFilter {
  usersIds: Array<string>;
}

interface CustomerUsersFilter {
  customerId: string;
}

interface UserGroupListFilter {
  groupsIds: Array<string>;
}

interface UserRoleFilter {
  rolesIds: Array<string>;
}

interface TenantAdministratorsFilter {
  tenantsIds?: Array<string>;
  tenantProfilesIds?: Array<string>;
}

export interface SlackNotificationTargetConfig {
  conversationType: SlackChanelType;
  conversation: SlackConversation;
}
export enum NotificationTargetType {
  PLATFORM_USERS = 'PLATFORM_USERS',
  SLACK = 'SLACK'
}

export const NotificationTargetTypeTranslationMap = new Map<NotificationTargetType, string>([
  [NotificationTargetType.PLATFORM_USERS, 'notification.platform-users'],
  [NotificationTargetType.SLACK, 'notification.delivery-method.slack']
]);

export interface NotificationTemplate extends Omit<BaseData<NotificationTemplateId>, 'label'>{
  tenantId: TenantId;
  notificationType: NotificationType;
  configuration: NotificationTemplateConfig;
}

interface NotificationTemplateConfig {
  deliveryMethodsTemplates: {
    [key in NotificationDeliveryMethod]: DeliveryMethodNotificationTemplate
  };
}

export interface DeliveryMethodNotificationTemplate extends
  Partial<WebDeliveryMethodNotificationTemplate & EmailDeliveryMethodNotificationTemplate & SlackDeliveryMethodNotificationTemplate>{
  body?: string;
  enabled: boolean;
  method: NotificationDeliveryMethod;
}

interface WebDeliveryMethodNotificationTemplate {
  subject?: string;
  additionalConfig: WebDeliveryMethodAdditionalConfig;
}

interface WebDeliveryMethodAdditionalConfig {
  icon: {
    enabled: boolean;
    icon: string;
    color: string;
  };
  actionButtonConfig: {
    enabled: boolean;
    text: string;
    linkType: ActionButtonLinkType;
    link?: string;
    dashboardId?: string;
    dashboardState?: string;
    setEntityIdInState?: boolean;
  };
}

interface EmailDeliveryMethodNotificationTemplate {
  subject: string;
}

interface SlackDeliveryMethodNotificationTemplate {
  conversationType: SlackChanelType;
  conversationId: string;
}

export enum NotificationStatus {
  SENT = 'SENT',
  READ = 'READ'
}

export enum NotificationDeliveryMethod {
  WEB = 'WEB',
  SMS = 'SMS',
  EMAIL = 'EMAIL',
  SLACK = 'SLACK'
}

export const NotificationDeliveryMethodTranslateMap = new Map<NotificationDeliveryMethod, string>([
  [NotificationDeliveryMethod.WEB, 'notification.delivery-method.web'],
  [NotificationDeliveryMethod.SMS, 'notification.delivery-method.sms'],
  [NotificationDeliveryMethod.EMAIL, 'notification.delivery-method.email'],
  [NotificationDeliveryMethod.SLACK, 'notification.delivery-method.slack']
]);

export enum NotificationRequestStatus {
  PROCESSING = 'PROCESSING',
  SCHEDULED = 'SCHEDULED',
  SENT = 'SENT'
}

export const NotificationRequestStatusTranslateMap = new Map<NotificationRequestStatus, string>([
  [NotificationRequestStatus.PROCESSING, 'notification.request-status.processing'],
  [NotificationRequestStatus.SCHEDULED, 'notification.request-status.scheduled'],
  [NotificationRequestStatus.SENT, 'notification.request-status.sent']
]);

export enum SlackChanelType {
  PUBLIC_CHANNEL = 'PUBLIC_CHANNEL',
  PRIVATE_CHANNEL = 'PRIVATE_CHANNEL',
  DIRECT= 'DIRECT'
}

export const SlackChanelTypesTranslateMap = new Map<SlackChanelType, string>([
  [SlackChanelType.DIRECT, 'notification.slack-chanel-types.direct'],
  [SlackChanelType.PUBLIC_CHANNEL, 'notification.slack-chanel-types.public-channel'],
  [SlackChanelType.PRIVATE_CHANNEL, 'notification.slack-chanel-types.private-channel']
]);

export enum NotificationTargetConfigType {
  ALL_USERS = 'ALL_USERS',
  TENANT_ADMINISTRATORS = 'TENANT_ADMINISTRATORS',
  USER_LIST = 'USER_LIST',
  USER_GROUP_LIST = 'USER_GROUP_LIST',
  CUSTOMER_USERS = 'CUSTOMER_USERS',
  USER_ROLE = 'USER_ROLE',
  ORIGINATOR_ENTITY_OWNER_USERS = 'ORIGINATOR_ENTITY_OWNER_USERS',
  AFFECTED_USER = 'AFFECTED_USER'
}

interface NotificationTargetConfigTypeInfo {
  name: string;
  hint?: string;
}

export const NotificationTargetConfigTypeInfoMap = new Map<NotificationTargetConfigType, NotificationTargetConfigTypeInfo>([
  [NotificationTargetConfigType.ALL_USERS,
    {
      name: 'notification.target-type.all-users'
    }
  ],
  [NotificationTargetConfigType.TENANT_ADMINISTRATORS,
    {
      name: 'notification.target-type.tenant-administrators'
    }
  ],
  [NotificationTargetConfigType.CUSTOMER_USERS,
    {
      name: 'notification.target-type.customer-users'
    }
  ],
  [NotificationTargetConfigType.USER_LIST,
    {
      name: 'notification.target-type.user-list'
    }
  ],
  [NotificationTargetConfigType.ORIGINATOR_ENTITY_OWNER_USERS,
    {
      name: 'notification.target-type.users-entity-owner',
      hint: 'notification.target-type.users-entity-owner-hint'
    }
  ],
  [NotificationTargetConfigType.AFFECTED_USER,
    {
      name: 'notification.target-type.affected-user',
      hint: 'notification.target-type.affected-user-hint'
    }
  ],
  [NotificationTargetConfigType.USER_ROLE,
    {
      name: 'notification.target-type.user-role'
    }
  ],
  [NotificationTargetConfigType.USER_GROUP_LIST,
    {
      name: 'notification.target-type.user-group-list'
    }
  ]
]);

export enum NotificationType {
  GENERAL = 'GENERAL',
  ALARM = 'ALARM',
  DEVICE_INACTIVITY = 'DEVICE_INACTIVITY',
  ENTITY_ACTION = 'ENTITY_ACTION',
  ALARM_COMMENT = 'ALARM_COMMENT',
  ALARM_ASSIGNMENT = 'ALARM_ASSIGNMENT',
  RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT = 'RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT',
  ENTITIES_LIMIT = 'ENTITIES_LIMIT'
}

export const NotificationTypeIcons = new Map<NotificationType, string | null>([
  [NotificationType.ALARM, 'warning'],
  [NotificationType.DEVICE_INACTIVITY, 'phonelink_off'],
  [NotificationType.ENTITY_ACTION, 'devices'],
  [NotificationType.ALARM_COMMENT, 'comment'],
  [NotificationType.ALARM_ASSIGNMENT, 'assignment_turned_in'],
  [NotificationType.RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT, 'settings_ethernet'],
  [NotificationType.ENTITIES_LIMIT, 'data_thresholding'],
]);

export const AlarmSeverityNotificationColors = new Map<AlarmSeverity, string>(
  [
    [AlarmSeverity.CRITICAL, '#D12730'],
    [AlarmSeverity.MAJOR, '#FEAC0C'],
    [AlarmSeverity.MINOR, '#F2DA05'],
    [AlarmSeverity.WARNING, '#F66716'],
    [AlarmSeverity.INDETERMINATE, '#00000061']
  ]
);

export enum ActionButtonLinkType {
  LINK = 'LINK',
  DASHBOARD  = 'DASHBOARD'
}

export const ActionButtonLinkTypeTranslateMap = new Map<ActionButtonLinkType, string>([
  [ActionButtonLinkType.LINK, 'notification.link-type.link'],
  [ActionButtonLinkType.DASHBOARD, 'notification.link-type.dashboard']
]);

interface NotificationTemplateTypeTranslate {
  name: string;
  helpId?: string;
}

export const NotificationTemplateTypeTranslateMap = new Map<NotificationType, NotificationTemplateTypeTranslate>([
  [NotificationType.GENERAL,
    {
      name: 'notification.template-type.general',
      helpId: 'notification/general'
    }
  ],
  [NotificationType.ALARM,
    {
      name: 'notification.template-type.alarm',
      helpId: 'notification/alarm'
    }
  ],
  [NotificationType.DEVICE_INACTIVITY,
    {
      name: 'notification.template-type.device-inactivity',
      helpId: 'notification/device_inactivity'
    }
  ],
  [NotificationType.ENTITY_ACTION,
    {
      name: 'notification.template-type.entity-action',
      helpId: 'notification/entity_action'
    }
  ],
  [NotificationType.ALARM_COMMENT,
    {
      name: 'notification.template-type.alarm-comment',
      helpId: 'notification/alarm_comment'
    }
  ],
  [NotificationType.ALARM_ASSIGNMENT,
    {
      name: 'notification.template-type.alarm-assignment',
      helpId: 'notification/alarm_assignment'
    }
  ],
  [NotificationType.RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT,
    {
      name: 'notification.template-type.rule-engine-lifecycle-event',
      helpId: 'notification/rule_engine_lifecycle_event'
    }
  ],
  [NotificationType.ENTITIES_LIMIT,
    {
      name: 'notification.template-type.entities-limit',
      helpId: 'notification/entities_limit'
    }]
]);

export enum TriggerType {
  ALARM = 'ALARM',
  DEVICE_INACTIVITY = 'DEVICE_INACTIVITY',
  ENTITY_ACTION = 'ENTITY_ACTION',
  ALARM_COMMENT = 'ALARM_COMMENT',
  ALARM_ASSIGNMENT = 'ALARM_ASSIGNMENT',
  RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT = 'RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT',
  ENTITIES_LIMIT = 'ENTITIES_LIMIT'
}

export const TriggerTypeTranslationMap = new Map<TriggerType, string>([
  [TriggerType.ALARM, 'notification.trigger.alarm'],
  [TriggerType.DEVICE_INACTIVITY, 'notification.trigger.device-inactivity'],
  [TriggerType.ENTITY_ACTION, 'notification.trigger.entity-action'],
  [TriggerType.ALARM_COMMENT, 'notification.trigger.alarm-comment'],
  [TriggerType.ALARM_ASSIGNMENT, 'notification.trigger.alarm-assignment'],
  [TriggerType.RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT, 'notification.trigger.rule-engine-lifecycle-event'],
  [TriggerType.ENTITIES_LIMIT, 'notification.trigger.entities-limit'],
]);
