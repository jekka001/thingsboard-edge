///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import { BaseData } from "@shared/models/base-data";
import { TenantId } from "@shared/models/id/tenant-id";
import { EntityId } from "@shared/models/id/entity-id";
import { HasUUID } from "@shared/models/id/has-uuid";
import { EntityGroupId } from "@shared/models/id/entity-group-id";

export interface EdgeSettings {
  edgeId: string;
  tenantId: string
  name: string;
  type: string;
  routingKey: string;
  cloudType: CloudType.PE | CloudType.CE;
}

export interface CloudEvent extends BaseData<CloudEventId> {
  cloudEventAction: string;
  cloudEventType: CloudEventType;
  entityBody: any;
  entityGroupId: EntityGroupId;
  entityId: EntityId;
  tenantId: TenantId;
}

export class CloudEventId implements HasUUID {
  id: string;
  constructor(id: string) {
    this.id = id;
  }
}

export enum CloudType {
  PE = "PE",
  CE = "CE"
}

export enum cloudConnectionStatus {
  true = 'edge.connected',
  false = 'edge.disconnected'
}

export interface CloudStatus {
  label: string,
  isActive: boolean
}

export enum CloudEventType {
  DASHBOARD = "DASHBOARD",
  ASSET = "ASSET",
  DEVICE = "DEVICE",
  DEVICE_PROFILE = "DEVICE_PROFILE",
  ENTITY_VIEW = "ENTITY_VIEW",
  ALARM = "ALARM",
  RULE_CHAIN = "RULE_CHAIN",
  RULE_CHAIN_METADATA = "RULE_CHAIN_METADATA",
  USER = "USER",
  CUSTOMER = "CUSTOMER",
  RELATION = "RELATION",
  ENTITY_GROUP = "ENTITY_GROUP",
  EDGE = "EDGE",
  SCHEDULER_EVENT = "SCHEDULER_EVENT"
}

export enum CloudEventActionType {
  ADDED = "ADDED",
  DELETED = "DELETED",
  UPDATED = "UPDATED",
  ATTRIBUTES_UPDATED = "ATTRIBUTES_UPDATED",
  ATTRIBUTES_DELETED = "ATTRIBUTES_DELETED",
  TIMESERIES_DELETED = "TIMESERIES_DELETED",
  TIMESERIES_UPDATED = "TIMESERIES_UPDATED",
  RPC_CALL = "RPC_CALL",
  CREDENTIALS_UPDATED = "CREDENTIALS_UPDATED",
  RELATION_ADD_OR_UPDATE = "RELATION_ADD_OR_UPDATE",
  RELATION_DELETED = "RELATION_DELETED",
  RELATIONS_DELETED = "RELATIONS_DELETED",
  ALARM_ACK = "ALARM_ACK",
  ALARM_CLEAR = "ALARM_CLEAR",
  ADDED_TO_ENTITY_GROUP = "ADDED_TO_ENTITY_GROUP",
  REMOVED_FROM_ENTITY_GROUP = "REMOVED_FROM_ENTITY_GROUP",
  ATTRIBUTES_REQUEST = "ATTRIBUTES_REQUEST",
  RULE_CHAIN_METADATA_REQUEST = "RULE_CHAIN_METADATA_REQUEST",
  RELATION_REQUEST = "RELATION_REQUEST",
  CREDENTIALS_REQUEST = "CREDENTIALS_REQUEST",
  GROUP_ENTITIES_REQUEST = "GROUP_ENTITIES_REQUEST",
  GROUP_PERMISSIONS_REQUEST = "GROUP_PERMISSIONS_REQUEST"
}

export enum EdgeEventStatus {
  DEPLOYED = "DEPLOYED",
  PENDING = "PENDING"
}

export const edgeEventStatusColor = new Map<EdgeEventStatus, string> (
  [
    [EdgeEventStatus.DEPLOYED, '#000000'],
    [EdgeEventStatus.PENDING, '#9e9e9e']
  ]
);

export const cloudEventTypeTranslations = new Map<CloudEventType, string>(
  [
    [CloudEventType.DASHBOARD, 'cloud-event.cloud-event-type-dashboard'],
    [CloudEventType.ASSET, 'cloud-event.cloud-event-type-asset'],
    [CloudEventType.DEVICE, 'cloud-event.cloud-event-type-device'],
    [CloudEventType.DEVICE_PROFILE, 'cloud-event.cloud-event-type-device-profile'],
    [CloudEventType.ENTITY_VIEW, 'cloud-event.cloud-event-type-entity-view'],
    [CloudEventType.ENTITY_GROUP, 'cloud-event.cloud-event-type-entity-group'],
    [CloudEventType.ALARM, 'cloud-event.cloud-event-type-alarm'],
    [CloudEventType.RULE_CHAIN, 'cloud-event.cloud-event-type-rule-chain'],
    [CloudEventType.RULE_CHAIN_METADATA, 'cloud-event.cloud-event-type-rule-chain-metadata'],
    [CloudEventType.EDGE, 'cloud-event.cloud-event-type-edge'],
    [CloudEventType.USER, 'cloud-event.cloud-event-type-user'],
    [CloudEventType.CUSTOMER, 'cloud-event.cloud-event-type-customer'],
    [CloudEventType.RELATION, 'cloud-event.cloud-event-type-relation'],
    [CloudEventType.SCHEDULER_EVENT, 'cloud-event.cloud-event-type-scheduler-event']
  ]
);

export const cloudEventActionTypeTranslations = new Map<string, string>(
  [
    [CloudEventActionType.ADDED, 'cloud-event.cloud-event-action-added'],
    [CloudEventActionType.DELETED, 'cloud-event.cloud-event-action-deleted'],
    [CloudEventActionType.UPDATED, 'cloud-event.cloud-event-action-updated'],
    [CloudEventActionType.ATTRIBUTES_UPDATED, 'cloud-event.cloud-event-action-attributes-updated'],
    [CloudEventActionType.ATTRIBUTES_DELETED, 'cloud-event.cloud-event-action-attributes-deleted'],
    [CloudEventActionType.TIMESERIES_DELETED, 'cloud-event.cloud-event-action-timeseries-deleted'],
    [CloudEventActionType.TIMESERIES_UPDATED, 'cloud-event.cloud-event-action-timeseries-updated'],
    [CloudEventActionType.RPC_CALL, 'cloud-event.cloud-event-action-rpc-call'],
    [CloudEventActionType.CREDENTIALS_UPDATED, 'cloud-event.cloud-event-action-credentials-updated'],
    [CloudEventActionType.RELATION_ADD_OR_UPDATE, 'cloud-event.cloud-event-action-relation-add-or-update'],
    [CloudEventActionType.RELATION_DELETED, 'cloud-event.cloud-event-action-relation-deleted'],
    [CloudEventActionType.RELATIONS_DELETED, 'cloud-event.cloud-event-action-relations-deleted'],
    [CloudEventActionType.ALARM_ACK, 'cloud-event.cloud-event-action-alarm-ack'],
    [CloudEventActionType.ALARM_CLEAR, 'cloud-event.cloud-event-action-alarm-clear'],
    [CloudEventActionType.ADDED_TO_ENTITY_GROUP, 'cloud-event.cloud-event-action-added-to-entity-group'],
    [CloudEventActionType.REMOVED_FROM_ENTITY_GROUP, 'cloud-event.cloud-event-action-removed-from-entity-group'],
    [CloudEventActionType.ATTRIBUTES_REQUEST, 'cloud-event.cloud-event-action-attributes-request'],
    [CloudEventActionType.RULE_CHAIN_METADATA_REQUEST, 'cloud-event.cloud-event-action-rule-chain-metadata-request'],
    [CloudEventActionType.RELATION_REQUEST, 'cloud-event.cloud-event-action-relation-request'],
    [CloudEventActionType.CREDENTIALS_REQUEST, 'cloud-event.cloud-event-action-credentials-request'],
    [CloudEventActionType.GROUP_ENTITIES_REQUEST, 'cloud-event.cloud-event-action-group-entities-request'],
    [CloudEventActionType.GROUP_PERMISSIONS_REQUEST, 'cloud-event.cloud-event-action-group-permissions-request']
  ]
);

