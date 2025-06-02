///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2025 ThingsBoard, Inc. All Rights Reserved.
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

import { TranslateService } from '@ngx-translate/core';

export interface RateLimits {
  value: number;
  time: number;
}

export enum RateLimitsType {
  DEVICE_MESSAGES = 'DEVICE_MESSAGES',
  DEVICE_TELEMETRY_MESSAGES = 'DEVICE_TELEMETRY_MESSAGES',
  DEVICE_TELEMETRY_DATA_POINTS = 'DEVICE_TELEMETRY_DATA_POINTS',
  TENANT_MESSAGES = 'TENANT_MESSAGES',
  GATEWAY_MESSAGES = 'GATEWAY_MESSAGES',
  GATEWAY_TELEMETRY_MESSAGES = 'GATEWAY_TELEMETRY_MESSAGES',
  GATEWAY_TELEMETRY_DATA_POINTS = 'GATEWAY_TELEMETRY_DATA_POINTS',
  GATEWAY_DEVICE_MESSAGES = 'GATEWAY_DEVICE_MESSAGES',
  GATEWAY_DEVICE_TELEMETRY_MESSAGES = 'GATEWAY_DEVICE_TELEMETRY_MESSAGES',
  GATEWAY_DEVICE_TELEMETRY_DATA_POINTS = 'GATEWAY_DEVICE_TELEMETRY_DATA_POINTS',
  TENANT_TELEMETRY_MESSAGES = 'TENANT_TELEMETRY_MESSAGES',
  TENANT_TELEMETRY_DATA_POINTS = 'TENANT_TELEMETRY_DATA_POINTS',
  TENANT_SERVER_REST_LIMITS_CONFIGURATION = 'TENANT_SERVER_REST_LIMITS_CONFIGURATION',
  CUSTOMER_SERVER_REST_LIMITS_CONFIGURATION = 'CUSTOMER_SERVER_REST_LIMITS_CONFIGURATION',
  WS_UPDATE_PER_SESSION_RATE_LIMIT = 'WS_UPDATE_PER_SESSION_RATE_LIMIT',
  CASSANDRA_WRITE_QUERY_TENANT_CORE_RATE_LIMITS = 'CASSANDRA_WRITE_QUERY_TENANT_CORE_RATE_LIMITS',
  CASSANDRA_READ_QUERY_TENANT_CORE_RATE_LIMITS = 'CASSANDRA_READ_QUERY_TENANT_CORE_RATE_LIMITS',
  CASSANDRA_WRITE_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS = 'CASSANDRA_WRITE_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS',
  CASSANDRA_READ_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS = 'CASSANDRA_READ_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS',
  TENANT_ENTITY_EXPORT_RATE_LIMIT = 'TENANT_ENTITY_EXPORT_RATE_LIMIT',
  TENANT_ENTITY_IMPORT_RATE_LIMIT = 'TENANT_ENTITY_IMPORT_RATE_LIMIT',
  TENANT_NOTIFICATION_REQUEST_RATE_LIMIT = 'TENANT_NOTIFICATION_REQUEST_RATE_LIMIT',
  TENANT_NOTIFICATION_REQUESTS_PER_RULE_RATE_LIMIT = 'TENANT_NOTIFICATION_REQUESTS_PER_RULE_RATE_LIMIT',
  INTEGRATION_MSGS_PER_TENANT_RATE_LIMIT = 'INTEGRATION_MSGS_PER_TENANT_RATE_LIMIT',
  INTEGRATION_MSGS_PER_DEVICE_RATE_LIMIT = 'INTEGRATION_MSGS_PER_DEVICE_RATE_LIMIT',
  INTEGRATION_MSGS_PER_ASSET_RATE_LIMIT = 'INTEGRATION_MSGS_PER_ASSET_RATE_LIMIT',
  EDGE_EVENTS_RATE_LIMIT = 'EDGE_EVENTS_RATE_LIMIT',
  EDGE_EVENTS_PER_EDGE_RATE_LIMIT = 'EDGE_EVENTS_PER_EDGE_RATE_LIMIT',
  EDGE_UPLINK_MESSAGES_RATE_LIMIT = 'EDGE_UPLINK_MESSAGES_RATE_LIMIT',
  EDGE_UPLINK_MESSAGES_PER_EDGE_RATE_LIMIT = 'EDGE_UPLINK_MESSAGES_PER_EDGE_RATE_LIMIT',
  CALCULATED_FIELD_DEBUG_EVENT_RATE_LIMIT = 'CALCULATED_FIELD_DEBUG_EVENT_RATE_LIMIT',
}

export const rateLimitsLabelTranslationMap = new Map<RateLimitsType, string>(
  [
    [RateLimitsType.TENANT_MESSAGES, 'tenant-profile.rate-limits.transport-tenant-msg'],
    [RateLimitsType.TENANT_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.transport-tenant-telemetry-msg'],
    [RateLimitsType.TENANT_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.transport-tenant-telemetry-data-points'],
    [RateLimitsType.DEVICE_MESSAGES, 'tenant-profile.rate-limits.transport-device-msg'],
    [RateLimitsType.DEVICE_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.transport-device-telemetry-msg'],
    [RateLimitsType.DEVICE_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.transport-device-telemetry-data-points'],
    [RateLimitsType.GATEWAY_MESSAGES, 'tenant-profile.rate-limits.transport-gateway-msg'],
    [RateLimitsType.GATEWAY_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.transport-gateway-telemetry-msg'],
    [RateLimitsType.GATEWAY_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.transport-gateway-telemetry-data-points'],
    [RateLimitsType.GATEWAY_DEVICE_MESSAGES, 'tenant-profile.rate-limits.transport-gateway-device-msg'],
    [RateLimitsType.GATEWAY_DEVICE_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.transport-gateway-device-telemetry-msg'],
    [RateLimitsType.GATEWAY_DEVICE_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.transport-gateway-device-telemetry-data-points'],
    [RateLimitsType.TENANT_SERVER_REST_LIMITS_CONFIGURATION, 'tenant-profile.rest-requests-for-tenant'],
    [RateLimitsType.CUSTOMER_SERVER_REST_LIMITS_CONFIGURATION, 'tenant-profile.customer-rest-limits'],
    [RateLimitsType.WS_UPDATE_PER_SESSION_RATE_LIMIT, 'tenant-profile.ws-limit-updates-per-session'],
    [RateLimitsType.CASSANDRA_WRITE_QUERY_TENANT_CORE_RATE_LIMITS, 'tenant-profile.cassandra-write-tenant-core-limits-configuration'],
    [RateLimitsType.CASSANDRA_READ_QUERY_TENANT_CORE_RATE_LIMITS, 'tenant-profile.cassandra-read-tenant-core-limits-configuration'],
    [RateLimitsType.CASSANDRA_WRITE_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS, 'tenant-profile.cassandra-write-tenant-rule-engine-limits-configuration'],
    [RateLimitsType.CASSANDRA_READ_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS, 'tenant-profile.cassandra-read-tenant-rule-engine-limits-configuration'],
    [RateLimitsType.TENANT_ENTITY_EXPORT_RATE_LIMIT, 'tenant-profile.tenant-entity-export-rate-limit'],
    [RateLimitsType.TENANT_ENTITY_IMPORT_RATE_LIMIT, 'tenant-profile.tenant-entity-import-rate-limit'],
    [RateLimitsType.TENANT_NOTIFICATION_REQUEST_RATE_LIMIT, 'tenant-profile.tenant-notification-request-rate-limit'],
    [RateLimitsType.TENANT_NOTIFICATION_REQUESTS_PER_RULE_RATE_LIMIT, 'tenant-profile.tenant-notification-requests-per-rule-rate-limit'],
    [RateLimitsType.INTEGRATION_MSGS_PER_TENANT_RATE_LIMIT, 'tenant-profile.integration-msgs-per-tenant-rate-limit'],
    [RateLimitsType.INTEGRATION_MSGS_PER_DEVICE_RATE_LIMIT, 'tenant-profile.integration-msgs-per-device-rate-limit'],
    [RateLimitsType.INTEGRATION_MSGS_PER_ASSET_RATE_LIMIT, 'tenant-profile.integration-msgs-per-asset-rate-limit'],
    [RateLimitsType.EDGE_EVENTS_RATE_LIMIT, 'tenant-profile.rate-limits.edge-events-rate-limit'],
    [RateLimitsType.EDGE_EVENTS_PER_EDGE_RATE_LIMIT, 'tenant-profile.rate-limits.edge-events-per-edge-rate-limit'],
    [RateLimitsType.EDGE_UPLINK_MESSAGES_RATE_LIMIT, 'tenant-profile.rate-limits.edge-uplink-messages-rate-limit'],
    [RateLimitsType.EDGE_UPLINK_MESSAGES_PER_EDGE_RATE_LIMIT, 'tenant-profile.rate-limits.edge-uplink-messages-per-edge-rate-limit'],
    [RateLimitsType.CALCULATED_FIELD_DEBUG_EVENT_RATE_LIMIT, 'tenant-profile.rate-limits.calculated-field-debug-event-rate-limit'],
  ]
);

export const rateLimitsDialogTitleTranslationMap = new Map<RateLimitsType, string>(
  [
    [RateLimitsType.TENANT_MESSAGES, 'tenant-profile.rate-limits.edit-transport-tenant-msg-title'],
    [RateLimitsType.TENANT_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.edit-transport-tenant-telemetry-msg-title'],
    [RateLimitsType.TENANT_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.edit-transport-tenant-telemetry-data-points-title'],
    [RateLimitsType.DEVICE_MESSAGES, 'tenant-profile.rate-limits.edit-transport-device-msg-title'],
    [RateLimitsType.DEVICE_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.edit-transport-device-telemetry-msg-title'],
    [RateLimitsType.DEVICE_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.edit-transport-device-telemetry-data-points-title'],
    [RateLimitsType.TENANT_SERVER_REST_LIMITS_CONFIGURATION, 'tenant-profile.rate-limits.edit-tenant-rest-limits-title'],
    [RateLimitsType.GATEWAY_MESSAGES, 'tenant-profile.rate-limits.edit-transport-gateway-msg-title'],
    [RateLimitsType.GATEWAY_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.edit-transport-gateway-telemetry-msg-title'],
    [RateLimitsType.GATEWAY_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.edit-transport-gateway-telemetry-data-points-title'],
    [RateLimitsType.GATEWAY_DEVICE_MESSAGES, 'tenant-profile.rate-limits.edit-transport-gateway-device-msg-title'],
    [RateLimitsType.GATEWAY_DEVICE_TELEMETRY_MESSAGES, 'tenant-profile.rate-limits.edit-transport-gateway-device-telemetry-msg-title'],
    [RateLimitsType.GATEWAY_DEVICE_TELEMETRY_DATA_POINTS, 'tenant-profile.rate-limits.edit-transport-gateway-device-telemetry-data-points-title'],
    [RateLimitsType.CUSTOMER_SERVER_REST_LIMITS_CONFIGURATION, 'tenant-profile.rate-limits.edit-customer-rest-limits-title'],
    [RateLimitsType.WS_UPDATE_PER_SESSION_RATE_LIMIT, 'tenant-profile.rate-limits.edit-ws-limit-updates-per-session-title'],
    [RateLimitsType.CASSANDRA_WRITE_QUERY_TENANT_CORE_RATE_LIMITS, 'tenant-profile.rate-limits.edit-cassandra-write-tenant-core-limits-configuration'],
    [RateLimitsType.CASSANDRA_READ_QUERY_TENANT_CORE_RATE_LIMITS, 'tenant-profile.rate-limits.edit-cassandra-read-tenant-core-limits-configuration'],
    [RateLimitsType.CASSANDRA_WRITE_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS, 'tenant-profile.rate-limits.edit-cassandra-write-tenant-rule-engine-limits-configuration'],
    [RateLimitsType.CASSANDRA_READ_QUERY_TENANT_RULE_ENGINE_RATE_LIMITS, 'tenant-profile.rate-limits.edit-cassandra-read-tenant-rule-engine-limits-configuration'],
    [RateLimitsType.TENANT_ENTITY_EXPORT_RATE_LIMIT, 'tenant-profile.rate-limits.edit-tenant-entity-export-rate-limit-title'],
    [RateLimitsType.TENANT_ENTITY_IMPORT_RATE_LIMIT, 'tenant-profile.rate-limits.edit-tenant-entity-import-rate-limit-title'],
    [RateLimitsType.TENANT_NOTIFICATION_REQUEST_RATE_LIMIT, 'tenant-profile.rate-limits.edit-tenant-notification-request-rate-limit-title'],
    [RateLimitsType.TENANT_NOTIFICATION_REQUESTS_PER_RULE_RATE_LIMIT, 'tenant-profile.rate-limits.edit-tenant-notification-requests-per-rule-rate-limit-title'],
    [RateLimitsType.INTEGRATION_MSGS_PER_TENANT_RATE_LIMIT, 'tenant-profile.rate-limits.edit-integration-msgs-per-tenant-rate-limit-title'],
    [RateLimitsType.INTEGRATION_MSGS_PER_DEVICE_RATE_LIMIT, 'tenant-profile.rate-limits.edit-integration-msgs-per-device-rate-limit-title'],
    [RateLimitsType.INTEGRATION_MSGS_PER_ASSET_RATE_LIMIT, 'tenant-profile.rate-limits.edit-integration-msgs-per-asset-rate-limit-title'],
    [RateLimitsType.EDGE_EVENTS_RATE_LIMIT, 'tenant-profile.rate-limits.edit-edge-events-rate-limit'],
    [RateLimitsType.EDGE_EVENTS_PER_EDGE_RATE_LIMIT, 'tenant-profile.rate-limits.edit-edge-events-per-edge-rate-limit'],
    [RateLimitsType.EDGE_UPLINK_MESSAGES_RATE_LIMIT, 'tenant-profile.rate-limits.edit-edge-uplink-messages-rate-limit'],
    [RateLimitsType.EDGE_UPLINK_MESSAGES_PER_EDGE_RATE_LIMIT, 'tenant-profile.rate-limits.edit-edge-uplink-messages-per-edge-rate-limit'],
    [RateLimitsType.CALCULATED_FIELD_DEBUG_EVENT_RATE_LIMIT, 'tenant-profile.rate-limits.edit-calculated-field-debug-event-rate-limit']
  ]
);

export function stringToRateLimitsArray(rateLimits: string): Array<RateLimits> {
  const result: Array<RateLimits> = [];
  if (rateLimits?.length > 0) {
    const rateLimitsArrays = rateLimits.split(',');
    for (const limit of rateLimitsArrays) {
      const [value, time] = limit.split(':');
      const rateLimitControl = {
        value: Number(value),
        time: Number(time)
      };
      result.push(rateLimitControl);
    }
  }
  return result;
}

export function rateLimitsArrayToString(rateLimits: Array<RateLimits>): string {
  let result = '';
  for (let i = 0; i < rateLimits.length; i++) {
    result = result.concat(rateLimits[i].value.toString(), ':', rateLimits[i].time.toString());
    if ((rateLimits.length > 1) && (i !== rateLimits.length - 1)) {
      result = result.concat(',');
    }
  }
  return result;
}

export function rateLimitsArrayToHtml(translate: TranslateService, rateLimitsArray: Array<RateLimits>): string {
  const rateLimitsHtml = rateLimitsArray.map((rateLimits, index) => {
    const isLast: boolean = index === rateLimitsArray.length - 1;
    return rateLimitsToHtml(translate, rateLimits, isLast);
  });
  let result: string;
  if (rateLimitsHtml.length > 1) {
    const andAlsoText = translate.instant('tenant-profile.rate-limits.and-also-less-than');
    result = rateLimitsHtml.join(` <span class="disabled">${andAlsoText}</span> `);
  } else {
    result = rateLimitsHtml[0];
  }
  return result;
}

function rateLimitsToHtml(translate: TranslateService, rateLimit: RateLimits, isLast: boolean): string {
  const value = rateLimit.value;
  const time = rateLimit.time;
  const operation = translate.instant('tenant-profile.rate-limits.messages-per');
  const seconds = translate.instant('tenant-profile.rate-limits.sec');
  const comma = isLast ? '' : ',';
  return `<span class="tb-rate-limits-value">${value}</span>
          <span>${operation}</span>
          <span class="tb-rate-limits-value"> ${time}</span>
          <span>${seconds}${comma}</span><br>`;
}
