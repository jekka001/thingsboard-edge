///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
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

import { OAuth2ClientId } from '@shared/models/id/oauth2-client-id';
import { BaseData } from '@shared/models/base-data';
import { TenantId } from '@shared/models/id/tenant-id';
import { HasTenantId } from './entity.models';
import { DomainId } from './id/domain-id';
import { HasUUID } from '@shared/models/id/has-uuid';
import { MobileAppId } from '@shared/models/id/mobile-app-id';

export enum DomainSchema {
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

export enum PlatformType {
  WEB = 'WEB',
  ANDROID = 'ANDROID',
  IOS = 'IOS'
}

export interface OAuth2ClientRegistrationTemplate extends OAuth2RegistrationInfo {
  comment: string;
  createdTime: number;
  helpLink: string;
  name: string;
  providerId: string;
  id: HasUUID;
}

export interface OAuth2RegistrationInfo {
  loginButtonLabel: string;
  loginButtonIcon: string;
  clientId: string;
  clientSecret: string;
  accessTokenUri: string;
  authorizationUri: string;
  scope: string[];
  platforms: PlatformType[];
  jwkSetUri?: string;
  userInfoUri: string;
  clientAuthenticationMethod: ClientAuthenticationMethod;
  userNameAttributeName: string;
  mapperConfig: OAuth2MapperConfig;
  additionalInfo: string;
}

export enum ClientAuthenticationMethod {
  BASIC = 'BASIC',
  POST = 'POST'
}

export interface Domain extends BaseData<DomainId>, HasTenantId {
  tenantId?: TenantId;
  name: string;
  oauth2Enabled: boolean;
  propagateToEdge: boolean;
}

export interface HasOauth2Clients {
  oauth2ClientInfos?: Array<OAuth2ClientInfo> | Array<string>;
}

export interface DomainInfo extends Domain, HasOauth2Clients {
  oauth2ClientInfos?: Array<OAuth2ClientInfo> | Array<string>;
}

export interface MobileApp extends BaseData<MobileAppId>, HasTenantId {
  tenantId?: TenantId;
  pkgName: string;
  appSecret: string;
  oauth2Enabled: boolean;
}

export interface MobileAppInfo extends MobileApp, HasOauth2Clients {
  oauth2ClientInfos?: Array<OAuth2ClientInfo> | Array<string>;
}

export interface OAuth2Client extends BaseData<OAuth2ClientId>, HasTenantId {
  tenantId?: TenantId;
  title: string;
  mapperConfig: OAuth2MapperConfig;
  clientId: string;
  clientSecret: string;
  authorizationUri: string;
  accessTokenUri: string;
  scope: Array<string>;
  userInfoUri?: string;
  userNameAttributeName: string;
  jwkSetUri?: string;
  clientAuthenticationMethod: ClientAuthenticationMethod;
  loginButtonLabel: string;
  loginButtonIcon?: string;
  platforms?: Array<PlatformType>;
  additionalInfo: any;
}

export interface OAuth2MapperConfig {
  allowUserCreation: boolean;
  activateUser: boolean;
  type: MapperType;
  basic?: OAuth2BasicMapperConfig;
  custom?: OAuth2CustomMapperConfig
}

export enum MapperType {
  BASIC = 'BASIC',
  CUSTOM = 'CUSTOM',
  GITHUB = 'GITHUB',
  APPLE = 'APPLE'
}

export interface OAuth2BasicMapperConfig {
  emailAttributeKey?: string;
  firstNameAttributeKey?: string;
  lastNameAttributeKey?: string;
  tenantNameStrategy?: TenantNameStrategyType;
  tenantNamePattern?: string;
  customerNamePattern?: string;
  defaultDashboardName?: string;
  alwaysFullScreen?: boolean;
  parentCustomerNamePattern?: string;
  userGroupsNamePattern?: string[];
}

export enum TenantNameStrategyType {
  DOMAIN = 'DOMAIN',
  EMAIL = 'EMAIL',
  CUSTOM = 'CUSTOM'
}

export interface OAuth2CustomMapperConfig {
  url?: string;
  username?: string;
  password?: string;
  sendToken: boolean;
}

export const platformTypeTranslations = new Map<PlatformType, string>(
  [
    [PlatformType.WEB, 'admin.oauth2.platform-web'],
    [PlatformType.ANDROID, 'admin.oauth2.platform-android'],
    [PlatformType.IOS, 'admin.oauth2.platform-ios']
  ]
);

export interface OAuth2ClientInfo extends BaseData<OAuth2ClientId> {
  title: string;
  providerName: string;
  platforms?: Array<PlatformType>;
}

export interface OAuth2ClientLoginInfo {
  name: string;
  icon: string;
  url: string;
}

export function getProviderHelpLink(provider: Provider): string {
  if (providerHelpLinkMap.has(provider)) {
    return providerHelpLinkMap.get(provider);
  }
  return 'oauth2Settings';
}

export enum Provider {
  CUSTOM = 'Custom',
  FACEBOOK = 'Facebook',
  GOOGLE = 'Google',
  GITHUB = 'Github',
  APPLE = 'Apple'
}

const providerHelpLinkMap = new Map<Provider, string>(
  [
    [Provider.CUSTOM, 'oauth2Settings'],
    [Provider.APPLE, 'oauth2Apple'],
    [Provider.FACEBOOK, 'oauth2Facebook'],
    [Provider.GITHUB, 'oauth2Github'],
    [Provider.GOOGLE, 'oauth2Google'],
  ]
)
