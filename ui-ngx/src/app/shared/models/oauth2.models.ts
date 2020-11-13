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

import { HasUUID } from '@shared/models/id/has-uuid';

export interface OAuth2ClientsParams {
  enabled: boolean;
  domainsParams: OAuth2ClientsDomainParams[];
}

export interface OAuth2ClientsDomainParams {
  clientRegistrations: ClientRegistration[];
  domainInfos: DomainInfo[];
}

export interface DomainInfo {
  name: string;
  scheme: DomainSchema;
}

export enum DomainSchema{
  HTTP = 'HTTP',
  HTTPS = 'HTTPS',
  MIXED = 'MIXED'
}

export const domainSchemaTranslations = new Map<DomainSchema, string>(
  [
    [DomainSchema.HTTP, 'admin.oauth2.domain-schema-http'],
    [DomainSchema.HTTPS, 'admin.oauth2.domain-schema-https'],
    [DomainSchema.MIXED, 'admin.oauth2.domain-schema-mixed']
  ]
);

export enum MapperConfigType{
  BASIC = 'BASIC',
  CUSTOM = 'CUSTOM',
  GITHUB = 'GITHUB'
}

export enum TenantNameStrategy{
  DOMAIN = 'DOMAIN',
  EMAIL = 'EMAIL',
  CUSTOM = 'CUSTOM'
}

export interface OAuth2ClientRegistrationTemplate extends ClientRegistration{
  comment: string;
  createdTime: number;
  helpLink: string;
  name: string;
  providerId: string;
  id: HasUUID;
}

export interface ClientRegistration {
  loginButtonLabel: string;
  loginButtonIcon: string;
  clientId: string;
  clientSecret: string;
  accessTokenUri: string;
  authorizationUri: string;
  scope: string[];
  jwkSetUri?: string;
  userInfoUri: string;
  clientAuthenticationMethod: ClientAuthenticationMethod;
  userNameAttributeName: string;
  mapperConfig: MapperConfig;
  additionalInfo: string;
}

export enum ClientAuthenticationMethod {
  BASIC = 'BASIC',
  POST = 'POST'
}

export interface MapperConfig {
  allowUserCreation: boolean;
  activateUser: boolean;
  type: MapperConfigType;
  basic?: MapperConfigBasic;
  custom?: MapperConfigCustom;
}

export interface MapperConfigBasic {
  emailAttributeKey: string;
  firstNameAttributeKey?: string;
  lastNameAttributeKey?: string;
  tenantNameStrategy: TenantNameStrategy;
  tenantNamePattern?: string;
  customerNamePattern?: string;
  defaultDashboardName?: string;
  alwaysFullScreen?: boolean;
  parentCustomerNamePattern?: string;
  userGroupsNamePattern?: string[];
}

export interface MapperConfigCustom {
  url: string;
  username?: string;
  password?: string;
}

export interface OAuth2ClientInfo {
  name: string;
  icon?: string;
  url: string;
}
