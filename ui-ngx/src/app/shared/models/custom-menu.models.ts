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

import { defaultUserMenuMap, MenuId, MenuReference } from '@core/services/menu.models';
import { deepClone, isNotEmptyStr } from '@core/utils';
import { BaseData } from '@shared/models/base-data';
import { CustomMenuId } from '@shared/models/id/custom-menu-id';
import { TenantId } from '@shared/models/id/tenant-id';
import { CustomerId } from '@shared/models/id/customer-id';
import { EntityInfoData, HasTenantId } from '@shared/models/entity.models';
import { Authority } from '@shared/models/authority.enum';

export enum CMScope {
  SYSTEM = 'SYSTEM',
  TENANT = 'TENANT',
  CUSTOMER = 'CUSTOMER'
}

export const cmScopeTranslations = new Map<CMScope, string>(
  [
    [CMScope.SYSTEM, 'custom-menu.scope-system'],
    [CMScope.TENANT, 'custom-menu.scope-tenant'],
    [CMScope.CUSTOMER, 'custom-menu.scope-customer']
  ]
);

export const cmScopeToAuthority = (scope: CMScope): Authority => {
  switch (scope) {
    case CMScope.SYSTEM:
      return Authority.SYS_ADMIN;
    case CMScope.TENANT:
      return Authority.TENANT_ADMIN;
    case CMScope.CUSTOMER:
      return Authority.CUSTOMER_USER;
  }
};

export enum CMAssigneeType {
  NO_ASSIGN = 'NO_ASSIGN',
  ALL = 'ALL',
  CUSTOMERS = 'CUSTOMERS',
  USERS = 'USERS'
}

export const cmAssigneeTypeTranslations = new Map<CMAssigneeType, string>(
  [
    [CMAssigneeType.NO_ASSIGN, 'custom-menu.assignee-no-assign'],
    [CMAssigneeType.ALL, 'custom-menu.assignee-all'],
    [CMAssigneeType.CUSTOMERS, 'custom-menu.assignee-customers'],
    [CMAssigneeType.USERS, 'custom-menu.assignee-users']
  ]
);

export interface CustomMenuInfo extends BaseData<CustomMenuId>, HasTenantId {
  tenantId?: TenantId;
  customerId?: CustomerId;
  name: string;
  scope: CMScope;
  assigneeType: CMAssigneeType;
}

export enum MenuItemType {
  DEFAULT = 'DEFAULT',
  HOME = 'HOME',
  CUSTOM = 'CUSTOM'
}

export interface MenuItem {
  type?: MenuItemType;
  name?: string;
  icon?: string;
  visible?: boolean;
  pages?: MenuItem[];
}

export interface DefaultMenuItem extends MenuItem, MenuReference {
  id: MenuId;
  pages?: DefaultMenuItem[];
}

export enum HomeMenuItemType {
  DEFAULT = 'DEFAULT',
  DASHBOARD = 'DASHBOARD'
}

export const homeMenuItemTypes = Object.keys(HomeMenuItemType) as HomeMenuItemType[];

export const homeMenuItemTypeTranslations = new Map<HomeMenuItemType, string>(
  [
    [HomeMenuItemType.DEFAULT, 'custom-menu.home-menu-item-type-default'],
    [HomeMenuItemType.DASHBOARD, 'custom-menu.home-menu-item-type-dashboard']
  ]
);

export interface HomeMenuItem extends DefaultMenuItem {
  id: MenuId.home;
  homeType: HomeMenuItemType;
  dashboardId?: string;
  hideDashboardToolbar?: boolean;
}

export enum CMItemType {
  LINK = 'LINK',
  SECTION = 'SECTION'
}

export const cmItemTypes = Object.keys(CMItemType) as CMItemType[];

export const cmItemTypeTranslations = new Map<CMItemType, string>(
  [
    [CMItemType.LINK, 'custom-menu.item-type-link'],
    [CMItemType.SECTION, 'custom-menu.item-type-section']
  ]
);

export enum CMItemLinkType {
  URL = 'URL',
  DASHBOARD = 'DASHBOARD'
}

export const cmLinkTypes = Object.keys(CMItemLinkType) as CMItemLinkType[];

export const cmLinkTypeTranslations = new Map<CMItemLinkType, string>(
  [
    [CMItemLinkType.URL, 'custom-menu.link-type-url'],
    [CMItemLinkType.DASHBOARD, 'custom-menu.link-type-dashboard']
  ]
);

export interface CustomMenuItem extends MenuItem {
  name: string;
  icon: string;
  menuItemType: CMItemType;
  linkType?: CMItemLinkType;
  dashboardId?: string;
  hideDashboardToolbar?: boolean;
  url?: string;
  setAccessToken?: boolean;
  pages?: CustomMenuItem[];
}

export interface CustomMenuConfig {
  items: MenuItem[];
}

export interface CustomMenu extends CustomMenuInfo {
  config: CustomMenuConfig;
}

export interface CustomMenuDeleteResult {
  success: boolean;
  assigneeType: CMAssigneeType;
  assigneeList: EntityInfoData[];
  error?: any;
}

export const toCustomMenuDeleteResult = (e?: any): CustomMenuDeleteResult => {
  const result = {success: true} as CustomMenuDeleteResult;
  if (e?.status === 400 && e?.error?.success === false && (e?.error?.assigneeType && e?.error?.assigneeList)) {
    result.success = false;
    result.assigneeType = e?.error?.assigneeType;
    result.assigneeList = e?.error?.assigneeList;
  } else if (e) {
    result.success = false;
    result.error = e;
  }
  return result;
};

export const isDefaultCustomMenuConflict = (e?: any): boolean =>
  e?.status === 400 && typeof e?.error?.message === 'string' && e?.error?.message.startsWith('There is already default menu for scope');

export const isDefaultMenuItem = (item: MenuItem): item is DefaultMenuItem => {
  const id = (item as DefaultMenuItem).id;
  return isNotEmptyStr(id) && !!MenuId[id];
};

export const isHomeMenuItem = (item: MenuItem): item is HomeMenuItem =>
  (item as DefaultMenuItem).id === MenuId.home;

export const isCustomMenuItem = (item: MenuItem): item is CustomMenuItem => {
  const customItem = item as CustomMenuItem;
  return isNotEmptyStr(customItem.name)
    && isNotEmptyStr(customItem.menuItemType) && !!CMItemType[customItem.menuItemType];
};

export const referenceToMenuItem = (reference: MenuReference): DefaultMenuItem => {
  const menuItem: DefaultMenuItem = {
    id: MenuId[reference.id],
    visible: true
  };
  if (isHomeMenuItem(menuItem)) {
    menuItem.homeType = HomeMenuItemType.DEFAULT;
  } else if (reference.pages?.length) {
    menuItem.pages = reference.pages.map(page =>
      referenceToMenuItem(page));
  }
  return menuItem;
};

const menuItemsIsEqualToReferences = (items: MenuItem[], references: MenuReference[]): boolean => {
  if (!items?.length && !references?.length) {
    return true;
  } else if (items.length !== references.length) {
    return false;
  } else {
    for (let i = 0; i < items.length; i++) {
      const item = items[i];
      const reference = references[i];
      if (!menuItemIsEqualToReference(item, reference)) {
        return false;
      }
    }
    return true;
  }
};

const menuItemIsEqualToReference = (item: MenuItem, reference: MenuReference): boolean => {
  if (isDefaultMenuItem(item) && item.visible) {
    if (item.id !== reference.id || !!item.name || !!item.icon) {
      return false;
    } else {
      if (isHomeMenuItem(item) && item.homeType !== HomeMenuItemType.DEFAULT) {
        return false;
      } else {
        return menuItemsIsEqualToReferences(item.pages, reference.pages);
      }
    }
  } else {
    return false;
  }
};

export const isDefaultMenuConfig = (config: CustomMenuConfig, scope: CMScope): boolean => {
  const authority = cmScopeToAuthority(scope);
  const references = defaultUserMenuMap.get(authority);
  return menuItemsIsEqualToReferences(config?.items, references);
};

const afterLoadMenuItems = (items: MenuItem[]): MenuItem[] => {
  for (const item of items) {
    if (item.type) {
      delete item.type;
    }
    if (item.pages) {
      item.pages = afterLoadMenuItems(item.pages);
    }
  }
  return items;
};

export const defaultCustomMenuConfig = (scope: CMScope): CustomMenuConfig => {
  const authority = cmScopeToAuthority(scope);
  const references = defaultUserMenuMap.get(authority);
  return {
    items: (references || []).map(r => referenceToMenuItem(r))
  };
};

export const afterLoadCustomMenuConfig = (config: CustomMenuConfig, scope: CMScope): CustomMenuConfig => {
  if (!config?.items?.length) {
    return defaultCustomMenuConfig(scope);
  } else {
    config.items = afterLoadMenuItems(config.items);
    return config;
  }
};

const beforeSaveMenuItems = (items: MenuItem[]): MenuItem[] => {
  for (const item of items) {
    let type: MenuItemType;
    if (isDefaultMenuItem(item)) {
      if (isHomeMenuItem(item)) {
        type = MenuItemType.HOME;
      } else {
        type = MenuItemType.DEFAULT;
      }
    } else {
      type = MenuItemType.CUSTOM;
    }
    item.type = type;
    if (item.pages) {
      item.pages = beforeSaveMenuItems(item.pages);
    }
  }
  return items;
};

export const beforeSaveCustomMenuConfig = (config: CustomMenuConfig, scope: CMScope): CustomMenuConfig => {
  if (isDefaultMenuConfig(config, scope)) {
    return {items: []};
  } else {
    config = deepClone(config);
    config.items = beforeSaveMenuItems(config.items);
    return config;
  }
};
