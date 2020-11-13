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

import { Injectable } from '@angular/core';
import { AuthService } from '../auth/auth.service';
import { select, Store } from '@ngrx/store';
import { AppState } from '../core.state';
import { selectAuth, selectIsAuthenticated } from '../auth/auth.selectors';
import { filter, map, mergeMap, publishReplay, refCount, take } from 'rxjs/operators';
import { HomeSection, MenuSection } from '@core/services/menu.models';
import { BehaviorSubject, Observable, of, Subject, Subscription } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
import { CustomMenuService } from '@core/http/custom-menu.service';
import { EntityGroupService } from '@core/http/entity-group.service';
import { EntityType } from '@shared/models/entity-type.models';
import { BroadcastService } from '@core/services/broadcast.service';
import { ActivationEnd, Params, Router } from '@angular/router';
import { UserPermissionsService } from '@core/http/user-permissions.service';
import { Resource } from '@shared/models/security.models';
import { AuthState } from '@core/auth/auth.models';
import { CustomMenuItem } from '@shared/models/custom-menu.models';
import { guid } from '@core/utils';

@Injectable({
  providedIn: 'root'
})
export class MenuService {

  private menuSections$: Subject<Array<MenuSection>> = new BehaviorSubject<Array<MenuSection>>([]);
  private homeSections$: Subject<Array<HomeSection>> = new BehaviorSubject<Array<HomeSection>>([]);

  private entityGroupSections: Array<EntityGroupSection> = [];

  private currentMenuSections: Array<MenuSection> = [];
  private currentHomeSections: Array<HomeSection> = [];

  private currentCustomSection: MenuSection = null;
  private currentCustomChildSection: MenuSection = null;

  constructor(private store: Store<AppState>,
              private router: Router,
              private customMenuService: CustomMenuService,
              private entityGroupService: EntityGroupService,
              private broadcast: BroadcastService,
              private userPermissionsService: UserPermissionsService,
              private authService: AuthService) {
    this.store.pipe(select(selectIsAuthenticated)).subscribe(
      (authenticated: boolean) => {
        if (authenticated) {
          this.buildMenu();
        }
      }
    );
    this.customMenuService.customMenuChanged$.subscribe(() => {
      this.buildMenu();
    });
    this.router.events.pipe(filter(event => event instanceof ActivationEnd)).subscribe(() => {
      this.updateCurrentCustomSection();
    });
  }

  private buildMenu() {
    for (const entityGroupSection of this.entityGroupSections) {
      entityGroupSection.destroy();
    }
    this.entityGroupSections.length = 0;
    this.currentMenuSections.length = 0;
    this.currentHomeSections.length = 0;
    this.store.pipe(select(selectAuth), take(1)).subscribe(
      (authState: AuthState) => {
        if (authState.authUser) {
          const customMenu = this.customMenuService.getCustomMenu();
          let disabledItems: string[] = [];
          if (customMenu && customMenu.disabledMenuItems) {
            disabledItems = customMenu.disabledMenuItems;
          }
          switch (authState.authUser.authority) {
            case Authority.SYS_ADMIN:
              this.currentMenuSections = this.buildSysAdminMenu(authState, disabledItems);
              this.currentHomeSections = this.buildSysAdminHome(authState, disabledItems);
              break;
            case Authority.TENANT_ADMIN:
              this.currentMenuSections = this.buildTenantAdminMenu(authState, disabledItems);
              this.currentHomeSections = this.buildTenantAdminHome(authState, disabledItems);
              break;
            case Authority.CUSTOMER_USER:
              this.currentMenuSections = this.buildCustomerUserMenu(authState, disabledItems);
              this.currentHomeSections = this.buildCustomerUserHome(authState, disabledItems);
              break;
          }
          let customMenuItems: CustomMenuItem[] = [];
          if (customMenu && customMenu.menuItems) {
            customMenuItems = customMenu.menuItems;
          }
          this.buildCustomMenu(customMenuItems);
          this.menuSections$.next(this.currentMenuSections);
          this.homeSections$.next(this.currentHomeSections);
        }
      }
    );
  }

  private createEntityGroupSection(groupType: EntityType): MenuSection {
    const entityGroupSection = new EntityGroupSection(this.router, groupType, this.broadcast, this.entityGroupService);
    this.entityGroupSections.push(entityGroupSection);
    return entityGroupSection.getMenuSection();
  }

  private buildSysAdminMenu(authState: AuthState, disabledItems: string[]): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'home',
        disabled: disabledItems.indexOf('home') > -1
      },
      {
        id: guid(),
        name: 'tenant.tenants',
        type: 'link',
        path: '/tenants',
        icon: 'supervisor_account',
        disabled: disabledItems.indexOf('tenants') > -1
      },
      {
        id: guid(),
        name: 'tenant-profile.tenant-profiles',
        type: 'link',
        path: '/tenantProfiles',
        icon: 'mdi:alpha-t-box',
        isMdiIcon: true,
        disabled: disabledItems.indexOf('tenant_profiles') > -1
      },
      {
        id: guid(),
        name: 'widget.widget-library',
        type: 'link',
        path: '/widgets-bundles',
        icon: 'now_widgets',
        disabled: disabledItems.indexOf('widget_library') > -1
      }
    );

    const pages: Array<MenuSection> = [
      {
        id: guid(),
        name: 'admin.outgoing-mail',
        type: 'link',
        path: '/settings/outgoing-mail',
        icon: 'mail',
        disabled: disabledItems.indexOf('mail_server') > -1
      },
      {
        id: guid(),
        name: 'admin.mail-templates',
        type: 'link',
        path: '/settings/mail-template',
        icon: 'format_shapes',
        disabled: disabledItems.indexOf('mail_templates') > -1
      },
      {
        id: guid(),
        name: 'white-labeling.white-labeling',
        type: 'link',
        path: '/settings/whiteLabel',
        icon: 'format_paint',
        disabled: disabledItems.indexOf('white_labeling') > -1
      },
      {
        id: guid(),
        name: 'white-labeling.login-white-labeling',
        type: 'link',
        path: '/settings/loginWhiteLabel',
        icon: 'format_paint',
        disabled: disabledItems.indexOf('login_white_labeling') > -1
      },
      {
        id: guid(),
        name: 'custom-translation.custom-translation',
        type: 'link',
        path: '/settings/customTranslation',
        icon: 'language',
        disabled: disabledItems.indexOf('custom_translation') > -1
      },
      {
        id: guid(),
        name: 'custom-menu.custom-menu',
        type: 'link',
        path: '/settings/customMenu',
        icon: 'list',
        disabled: disabledItems.indexOf('custom_menu') > -1
      },
      {
        id: guid(),
        name: 'admin.security-settings',
        type: 'link',
        path: '/settings/security-settings',
        icon: 'security',
        disabled: disabledItems.indexOf('security_settings') > -1
      },
      {
        id: guid(),
        name: 'admin.oauth2.oauth2',
        type: 'link',
        path: '/settings/oauth2',
        icon: 'security',
        disabled: disabledItems.indexOf('oauth2') > -1
      }
    ];

    const section: MenuSection = {
      id: guid(),
      name: 'admin.system-settings',
      type: 'toggle',
      path: '/settings',
      icon: 'settings',
      pages,
      asyncPages: of(pages)
    };

    sections.push(section);
    return sections;
  }

  private buildSysAdminHome(authState: AuthState, disabledItems: string[]): Array<HomeSection> {
    const homeSections: Array<HomeSection> = [];
    homeSections.push(
      {
        name: 'tenant.management',
        places: [
          {
            name: 'tenant.tenants',
            icon: 'supervisor_account',
            path: '/tenants',
            disabled: disabledItems.indexOf('tenants') > -1
          },
          {
            name: 'tenant-profile.tenant-profiles',
            icon: 'mdi:alpha-t-box',
            isMdiIcon: true,
            path: '/tenantProfiles',
            disabled: disabledItems.indexOf('tenant_profiles') > -1
          }
        ]
      },
      {
        name: 'widget.management',
        places: [
          {
            name: 'widget.widget-library',
            icon: 'now_widgets',
            path: '/widgets-bundles',
            disabled: disabledItems.indexOf('widget_library') > -1
          }
        ]
      },
      {
        name: 'admin.system-settings',
        places: [
          {
            name: 'admin.outgoing-mail',
            icon: 'mail',
            path: '/settings/outgoing-mail',
            disabled: disabledItems.indexOf('mail_server') > -1
          },
          {
            name: 'admin.mail-templates',
            icon: 'format_shapes',
            path: '/settings/mail-template',
            disabled: disabledItems.indexOf('mail_templates') > -1
          },
          {
            name: 'admin.security-settings',
            icon: 'security',
            path: '/settings/security-settings',
            disabled: disabledItems.indexOf('security_settings') > -1
          },
          {
            name: 'admin.oauth2.oauth2',
            icon: 'security',
            path: '/settings/oauth2',
            disabled: disabledItems.indexOf('oauth2') > -1
          }
        ]
      },
      {
        name: 'white-labeling.white-labeling',
        places: [
          {
            name: 'white-labeling.white-labeling',
            icon: 'format_paint',
            path: '/settings/whiteLabel',
            disabled: disabledItems.indexOf('white_labeling') > -1
          },
          {
            name: 'white-labeling.login-white-labeling',
            icon: 'format_paint',
            path: '/settings/loginWhiteLabel',
            disabled: disabledItems.indexOf('login_white_labeling') > -1
          }
        ]
      },
      {
        name: 'custom-translation.custom-translation',
        places: [
          {
            name: 'custom-translation.custom-translation',
            icon: 'language',
            path: '/settings/customTranslation',
            disabled: disabledItems.indexOf('custom_translation') > -1
          }
        ]
      },
      {
        name: 'custom-menu.custom-menu',
        places: [
          {
            name: 'custom-menu.custom-menu',
            icon: 'list',
            path: '/settings/customMenu',
            disabled: disabledItems.indexOf('custom_menu') > -1
          }
        ]
      }
    );
    return homeSections;
  }

  private buildTenantAdminMenu(authState: AuthState, disabledItems: string[]): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'home',
        disabled: disabledItems.indexOf('home') > -1
      }
    );
    if (this.userPermissionsService.hasReadGenericPermission(Resource.RULE_CHAIN)) {
      sections.push(
        {
          id: guid(),
          name: 'rulechain.rulechains',
          type: 'link',
          path: '/ruleChains',
          icon: 'settings_ethernet',
          disabled: disabledItems.indexOf('rule_chains') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.CONVERTER)) {
      sections.push(
        {
          id: guid(),
          name: 'converter.converters',
          type: 'link',
          path: '/converters',
          icon: 'transform',
          disabled: disabledItems.indexOf('converters') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.INTEGRATION)) {
      sections.push(
        {
          id: guid(),
          name: 'integration.integrations',
          type: 'link',
          path: '/integrations',
          icon: 'input',
          disabled: disabledItems.indexOf('integrations') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.ROLE)) {
      sections.push(
        {
          id: guid(),
          name: 'role.roles',
          type: 'link',
          path: '/roles',
          icon: 'security',
          disabled: disabledItems.indexOf('roles') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.CUSTOMER)) {
      sections.push(
        {
          id: guid(),
          name: 'customers-hierarchy.customers-hierarchy',
          type: 'link',
          path: '/customersHierarchy',
          icon: 'sort',
          disabled: disabledItems.indexOf('customers_hierarchy') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.USER) && disabledItems.indexOf('user_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.USER));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.CUSTOMER) && disabledItems.indexOf('customer_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.CUSTOMER));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ASSET) && disabledItems.indexOf('asset_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.ASSET));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DEVICE) && disabledItems.indexOf('device_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.DEVICE));
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.DEVICE_PROFILE)) {
      sections.push(
        {
          id: guid(),
          name: 'device-profile.device-profiles',
          type: 'link',
          path: '/deviceProfiles',
          icon: 'mdi:alpha-d-box',
          isMdiIcon: true,
          disabled: disabledItems.indexOf('device_profiles') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ENTITY_VIEW) && disabledItems.indexOf('entity_view_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.ENTITY_VIEW));
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.WIDGETS_BUNDLE)) {
      sections.push(
        {
          id: guid(),
          name: 'widget.widget-library',
          type: 'link',
          path: '/widgets-bundles',
          icon: 'now_widgets',
          disabled: disabledItems.indexOf('widget_library') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DASHBOARD) && disabledItems.indexOf('dashboard_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.DASHBOARD));
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.SCHEDULER_EVENT)) {
      sections.push(
        {
          id: guid(),
          name: 'scheduler.scheduler',
          type: 'link',
          path: '/scheduler',
          icon: 'schedule',
          disabled: disabledItems.indexOf('scheduler') > -1
        }
      );
    }
    if (authState.whiteLabelingAllowed && this.userPermissionsService.hasReadGenericPermission(Resource.WHITE_LABELING)) {
      const pages: Array<MenuSection> = [
        {
          id: guid(),
          name: 'admin.outgoing-mail',
          type: 'link',
          path: '/settings/outgoing-mail',
          icon: 'mail',
          disabled: disabledItems.indexOf('mail_server') > -1
        },
        {
          id: guid(),
          name: 'admin.mail-templates',
          type: 'link',
          path: '/settings/mail-template',
          icon: 'format_shapes',
          disabled: disabledItems.indexOf('mail_templates') > -1
        },
        {
          id: guid(),
          name: 'custom-translation.custom-translation',
          type: 'link',
          path: '/settings/customTranslation',
          icon: 'language',
          disabled: disabledItems.indexOf('custom_translation') > -1
        },
        {
          id: guid(),
          name: 'custom-menu.custom-menu',
          type: 'link',
          path: '/settings/customMenu',
          icon: 'list',
          disabled: disabledItems.indexOf('custom_menu') > -1
        },
        {
          id: guid(),
          name: 'white-labeling.white-labeling',
          type: 'link',
          path: '/settings/whiteLabel',
          icon: 'format_paint',
          disabled: disabledItems.indexOf('white_labeling') > -1
        },
        {
          id: guid(),
          name: 'white-labeling.login-white-labeling',
          type: 'link',
          path: '/settings/loginWhiteLabel',
          icon: 'format_paint',
          disabled: disabledItems.indexOf('login_white_labeling') > -1
        },
        {
          id: guid(),
          name: 'self-registration.self-registration',
          type: 'link',
          path: '/settings/selfRegistration',
          icon: 'group_add',
          disabled: disabledItems.indexOf('self_registration') > -1
        }
      ];
      sections.push(
        {
          id: guid(),
          name: 'white-labeling.white-labeling',
          type: 'toggle',
          path: '/settings',
          icon: 'format_paint',
          pages,
          asyncPages: of(pages)
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.AUDIT_LOG)) {
      sections.push(
        {
          id: guid(),
          name: 'audit-log.audit-logs',
          type: 'link',
          path: '/auditLogs',
          icon: 'track_changes',
          disabled: disabledItems.indexOf('audit_log') > -1
        }
      );
    }
    return sections;
  }

  private buildTenantAdminHome(authState: AuthState, disabledItems: string[]): Array<HomeSection> {
    const homeSections: Array<HomeSection> = [];
    if (this.userPermissionsService.hasReadGenericPermission(Resource.RULE_CHAIN)) {
      homeSections.push(
        {
          name: 'rulechain.management',
          places: [
            {
              name: 'rulechain.rulechains',
              icon: 'settings_ethernet',
              path: '/ruleChains',
              disabled: disabledItems.indexOf('rule_chains') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.CONVERTER)) {
      homeSections.push(
        {
          name: 'converter.management',
          places: [
            {
              name: 'converter.converters',
              icon: 'transform',
              path: '/converters',
              disabled: disabledItems.indexOf('converters') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.INTEGRATION)) {
      homeSections.push(
        {
          name: 'integration.management',
          places: [
            {
              name: 'integration.integrations',
              icon: 'input',
              path: '/integrations',
              disabled: disabledItems.indexOf('integrations') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.ROLE)) {
      homeSections.push(
        {
          name: 'role.management',
          places: [
            {
              name: 'role.roles',
              icon: 'security',
              path: '/roles',
              disabled: disabledItems.indexOf('roles') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.USER)) {
      homeSections.push(
        {
          name: 'user.management',
          places: [
            {
              name: 'user.users',
              icon: 'account_circle',
              path: '/userGroups',
              disabled: disabledItems.indexOf('user_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.CUSTOMER)) {
      homeSections.push(
        {
          name: 'customer.management',
          places: [
            {
              name: 'customer.customers',
              icon: 'supervisor_account',
              path: '/customerGroups',
              disabled: disabledItems.indexOf('customer_groups') > -1
            },
            {
              name: 'customers-hierarchy.customers-hierarchy',
              icon: 'sort',
              path: '/customersHierarchy',
              disabled: disabledItems.indexOf('customers_hierarchy') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ASSET)) {
      homeSections.push(
        {
          name: 'asset.management',
          places: [
            {
              name: 'asset.assets',
              icon: 'domain',
              path: '/assetGroups',
              disabled: disabledItems.indexOf('asset_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DEVICE) ||
      this.userPermissionsService.hasReadGenericPermission(Resource.DEVICE_PROFILE)) {
      const deviceManagementSection: HomeSection = {
        name: 'device.management',
        places: []
      };
      homeSections.push(deviceManagementSection);
      if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DEVICE)) {
        deviceManagementSection.places.push(
          {
            name: 'device.devices',
            icon: 'devices_other',
            path: '/deviceGroups',
            disabled: disabledItems.indexOf('device_groups') > -1
          }
        );
      }
      if (this.userPermissionsService.hasReadGenericPermission(Resource.DEVICE_PROFILE)) {
        deviceManagementSection.places.push(
          {
            name: 'device-profile.device-profiles',
            icon: 'mdi:alpha-d-box',
            isMdiIcon: true,
            path: '/deviceProfiles',
            disabled: disabledItems.indexOf('device_profiles') > -1
          }
        );
      }
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ENTITY_VIEW)) {
      homeSections.push(
        {
          name: 'entity-view.management',
          places: [
            {
              name: 'entity-view.entity-views',
              icon: 'view_quilt',
              path: '/entityViewGroups',
              disabled: disabledItems.indexOf('entity_view_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DASHBOARD) ||
        this.userPermissionsService.hasReadGenericPermission(Resource.WIDGETS_BUNDLE)) {
      const dashboardManagement: HomeSection = {
        name: 'dashboard.management',
        places: []
      };
      homeSections.push(
        dashboardManagement
      );
      if (this.userPermissionsService.hasReadGenericPermission(Resource.WIDGETS_BUNDLE)) {
        dashboardManagement.places.push(
          {
            name: 'widget.widget-library',
            icon: 'now_widgets',
            path: '/widgets-bundles',
            disabled: disabledItems.indexOf('widget_library') > -1
          }
        );
      }
      if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DASHBOARD)) {
        dashboardManagement.places.push(
          {
            name: 'dashboard.dashboards',
            icon: 'dashboard',
            path: '/dashboardGroups',
            disabled: disabledItems.indexOf('dashboard_groups') > -1
          }
        );
      }
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.SCHEDULER_EVENT)) {
      homeSections.push(
        {
          name: 'scheduler.management',
          places: [
            {
              name: 'scheduler.scheduler',
              icon: 'schedule',
              path: '/scheduler',
              disabled: disabledItems.indexOf('scheduler') > -1
            }
          ]
        }
      );
    }
    if (authState.whiteLabelingAllowed && this.userPermissionsService.hasReadGenericPermission(Resource.WHITE_LABELING)) {
      homeSections.push(
        {
          name: 'white-labeling.white-labeling',
          places: [
            {
              name: 'admin.outgoing-mail',
              icon: 'mail',
              path: '/settings/outgoing-mail',
              disabled: disabledItems.indexOf('mail_server') > -1
            },
            {
              name: 'admin.mail-templates',
              icon: 'format_shapes',
              path: '/settings/mail-template',
              disabled: disabledItems.indexOf('mail_templates') > -1
            },
            {
              name: 'white-labeling.white-labeling',
              icon: 'format_paint',
              path: '/settings/whiteLabel',
              disabled: disabledItems.indexOf('white_labeling') > -1
            },
            {
              name: 'white-labeling.login-white-labeling',
              icon: 'format_paint',
              path: '/settings/loginWhiteLabel',
              disabled: disabledItems.indexOf('login_white_labeling') > -1
            }
          ]
        }
      );
      homeSections.push(
        {
          name: 'custom-translation.custom-translation',
          places: [
            {
              name: 'custom-translation.custom-translation',
              icon: 'language',
              path: '/settings/customTranslation',
              disabled: disabledItems.indexOf('custom_translation') > -1
            }
          ]
        }
      );
      homeSections.push(
        {
          name: 'custom-menu.custom-menu',
          places: [
            {
              name: 'custom-menu.custom-menu',
              icon: 'list',
              path: '/settings/customMenu',
              disabled: disabledItems.indexOf('custom_menu') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.AUDIT_LOG)) {
      homeSections.push(
        {
          name: 'audit-log.audit',
          places: [
            {
              name: 'audit-log.audit-logs',
              icon: 'track_changes',
              path: '/auditLogs',
              disabled: disabledItems.indexOf('audit_log') > -1
            }
          ]
        }
      );
    }
    return homeSections;
  }

  private buildCustomerUserMenu(authState: AuthState, disabledItems: string[]): Array<MenuSection> {
    const sections: Array<MenuSection> = [];
    sections.push(
      {
        id: guid(),
        name: 'home.home',
        type: 'link',
        path: '/home',
        icon: 'home',
        disabled: disabledItems.indexOf('home') > -1
      }
    );
    if (this.userPermissionsService.hasReadGenericPermission(Resource.ROLE)) {
      sections.push(
        {
          id: guid(),
          name: 'role.roles',
          type: 'link',
          path: '/roles',
          icon: 'security',
          disabled: disabledItems.indexOf('roles') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.CUSTOMER)) {
      sections.push(
        {
          id: guid(),
          name: 'customers-hierarchy.customers-hierarchy',
          type: 'link',
          path: '/customersHierarchy',
          icon: 'sort',
          disabled: disabledItems.indexOf('customers_hierarchy') > -1
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.USER) && disabledItems.indexOf('user_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.USER));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.CUSTOMER) && disabledItems.indexOf('customer_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.CUSTOMER));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ASSET) && disabledItems.indexOf('asset_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.ASSET));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DEVICE) && disabledItems.indexOf('device_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.DEVICE));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ENTITY_VIEW) && disabledItems.indexOf('entity_view_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.ENTITY_VIEW));
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DASHBOARD) && disabledItems.indexOf('dashboard_groups') === -1) {
      sections.push(this.createEntityGroupSection(EntityType.DASHBOARD));
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.SCHEDULER_EVENT)) {
      sections.push(
        {
          id: guid(),
          name: 'scheduler.scheduler',
          type: 'link',
          path: '/scheduler',
          icon: 'schedule',
          disabled: disabledItems.indexOf('scheduler') > -1
        }
      );
    }
    if (authState.whiteLabelingAllowed && this.userPermissionsService.hasReadGenericPermission(Resource.WHITE_LABELING)) {
      const pages: Array<MenuSection> = [
        {
          id: guid(),
          name: 'custom-translation.custom-translation',
          type: 'link',
          path: '/settings/customTranslation',
          icon: 'language',
          disabled: disabledItems.indexOf('custom_translation') > -1
        },
        {
          id: guid(),
          name: 'custom-menu.custom-menu',
          type: 'link',
          path: '/settings/customMenu',
          icon: 'list',
          disabled: disabledItems.indexOf('custom_menu') > -1
        },
        {
          id: guid(),
          name: 'white-labeling.white-labeling',
          type: 'link',
          path: '/settings/whiteLabel',
          icon: 'format_paint',
          disabled: disabledItems.indexOf('white_labeling') > -1
        },
        {
          id: guid(),
          name: 'white-labeling.login-white-labeling',
          type: 'link',
          path: '/settings/loginWhiteLabel',
          icon: 'format_paint',
          disabled: disabledItems.indexOf('login_white_labeling') > -1
        }
      ];
      sections.push(
        {
          id: guid(),
          name: 'white-labeling.white-labeling',
          type: 'toggle',
          path: '/settings',
          icon: 'format_paint',
          pages,
          asyncPages: of(pages)
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.AUDIT_LOG)) {
      sections.push(
        {
          id: guid(),
          name: 'audit-log.audit-logs',
          type: 'link',
          path: '/auditLogs',
          icon: 'track_changes',
          disabled: disabledItems.indexOf('audit_log') > -1
        }
      );
    }
    return sections;
  }

  private buildCustomerUserHome(authState: AuthState, disabledItems: string[]): Array<HomeSection> {
    const homeSections: Array<HomeSection> = [];
    if (this.userPermissionsService.hasReadGenericPermission(Resource.ROLE)) {
      homeSections.push(
        {
          name: 'role.management',
          places: [
            {
              name: 'role.roles',
              icon: 'security',
              path: '/roles',
              disabled: disabledItems.indexOf('roles') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.USER)) {
      homeSections.push(
        {
          name: 'user.management',
          places: [
            {
              name: 'user.users',
              icon: 'account_circle',
              path: '/userGroups',
              disabled: disabledItems.indexOf('user_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.CUSTOMER)) {
      homeSections.push(
        {
          name: 'customer.management',
          places: [
            {
              name: 'customer.customers',
              icon: 'supervisor_account',
              path: '/customerGroups',
              disabled: disabledItems.indexOf('customer_groups') > -1
            },
            {
              name: 'customers-hierarchy.customers-hierarchy',
              icon: 'sort',
              path: '/customersHierarchy',
              disabled: disabledItems.indexOf('customers_hierarchy') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ASSET)) {
      homeSections.push(
        {
          name: 'asset.management',
          places: [
            {
              name: 'asset.assets',
              icon: 'domain',
              path: '/assetGroups',
              disabled: disabledItems.indexOf('asset_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DEVICE)) {
      homeSections.push(
        {
          name: 'device.management',
          places: [
            {
              name: 'device.devices',
              icon: 'devices_other',
              path: '/deviceGroups',
              disabled: disabledItems.indexOf('device_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.ENTITY_VIEW)) {
      homeSections.push(
        {
          name: 'entity-view.management',
          places: [
            {
              name: 'entity-view.entity-views',
              icon: 'view_quilt',
              path: '/entityViewGroups',
              disabled: disabledItems.indexOf('entity_view_groups') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGroupsPermission(EntityType.DASHBOARD)) {
      homeSections.push({
        name: 'dashboard.management',
        places: [
          {
            name: 'dashboard.dashboards',
            icon: 'dashboard',
            path: '/dashboardGroups',
            disabled: disabledItems.indexOf('dashboard_groups') > -1
          }
        ]
      });
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.SCHEDULER_EVENT)) {
      homeSections.push(
        {
          name: 'scheduler.management',
          places: [
            {
              name: 'scheduler.scheduler',
              icon: 'schedule',
              path: '/scheduler',
              disabled: disabledItems.indexOf('scheduler') > -1
            }
          ]
        }
      );
    }
    if (authState.whiteLabelingAllowed && this.userPermissionsService.hasReadGenericPermission(Resource.WHITE_LABELING)) {
      homeSections.push(
        {
          name: 'white-labeling.white-labeling',
          places: [
            {
              name: 'white-labeling.white-labeling',
              icon: 'format_paint',
              path: '/settings/whiteLabel',
              disabled: disabledItems.indexOf('white_labeling') > -1
            },
            {
              name: 'white-labeling.login-white-labeling',
              icon: 'format_paint',
              path: '/settings/loginWhiteLabel',
              disabled: disabledItems.indexOf('login_white_labeling') > -1
            }
          ]
        }
      );
      homeSections.push(
        {
          name: 'custom-translation.custom-translation',
          places: [
            {
              name: 'custom-translation.custom-translation',
              icon: 'language',
              path: '/settings/customTranslation',
              disabled: disabledItems.indexOf('custom_translation') > -1
            }
          ]
        }
      );
      homeSections.push(
        {
          name: 'custom-menu.custom-menu',
          places: [
            {
              name: 'custom-menu.custom-menu',
              icon: 'list',
              path: '/settings/customMenu',
              disabled: disabledItems.indexOf('custom_menu') > -1
            }
          ]
        }
      );
    }
    if (this.userPermissionsService.hasReadGenericPermission(Resource.AUDIT_LOG)) {
      homeSections.push(
        {
          name: 'audit-log.audit',
          places: [
            {
              name: 'audit-log.audit-logs',
              icon: 'track_changes',
              path: '/auditLogs',
              disabled: disabledItems.indexOf('audit_log') > -1
            }
          ]
        }
      );
    }
    return homeSections;
  }

  private buildCustomMenu(customMenuItems: CustomMenuItem[]) {
    const stateIds: {[stateId: string]: boolean} = {};
    for (const customMenuItem of customMenuItems) {
      const stateId = this.getCustomMenuStateId(customMenuItem.name, stateIds);
      const customMenuSection = {
        isCustom: true,
        stateId,
        name: customMenuItem.name,
        icon: customMenuItem.materialIcon,
        iconUrl: customMenuItem.iconUrl,
        path: '/iframeView'
      } as MenuSection;
      customMenuSection.queryParams = {
        stateId,
        iframeUrl: customMenuItem.iframeUrl,
        setAccessToken: customMenuItem.setAccessToken
      };
      if (customMenuItem.childMenuItems && customMenuItem.childMenuItems.length) {
        customMenuSection.type = 'toggle';
        const pages: MenuSection[] = [];
        const childStateIds: {[stateId: string]: boolean} = {};
        for (const customMenuChildItem of customMenuItem.childMenuItems) {
          const childStateId = this.getCustomMenuStateId(customMenuChildItem.name, stateIds);
          const customMenuChildSection: MenuSection = {
            id: guid(),
            isCustom: true,
            stateId: childStateId,
            name: customMenuChildItem.name,
            type: 'link',
            icon: customMenuChildItem.materialIcon,
            iconUrl: customMenuChildItem.iconUrl,
            path: '/iframeView/child'
          };
          customMenuChildSection.queryParams = {
            stateId,
            iframeUrl: customMenuItem.iframeUrl,
            setAccessToken: customMenuItem.setAccessToken,
            childStateId,
            childIframeUrl: customMenuChildItem.iframeUrl,
            childSetAccessToken: customMenuChildItem.setAccessToken
          };
          pages.push(customMenuChildSection);
          childStateIds[childStateId] = true;
        }
        customMenuSection.pages = pages;
        customMenuSection.asyncPages = of(pages);
        customMenuSection.childStateIds = childStateIds;
      } else {
        customMenuSection.type = 'link';
      }
      this.currentMenuSections.push(customMenuSection);
    }
    this.updateCurrentCustomSection();
  }

  private getCustomMenuStateId(name: string, stateIds: {[stateId: string]: boolean}): string {
    const origName = (' ' + name).slice(1);
    let stateId = origName;
    let inc = 1;
    while (stateIds[stateId]) {
      stateId = origName + inc;
      inc++;
    }
    stateIds[stateId] = true;
    return stateId;
  }

  public menuSections(): Observable<Array<MenuSection>> {
    return this.menuSections$;
  }

  public homeSections(): Observable<Array<HomeSection>> {
    return this.homeSections$;
  }

  public sectionActive(section: MenuSection): boolean {
    if (section.isCustom) {
      const queryParams = this.extractQueryParams();
      if (queryParams) {
        if (queryParams.childStateId) {
          return section.stateId === queryParams.childStateId ||
            (section.childStateIds && section.childStateIds[queryParams.childStateId]);
        } else if (queryParams.stateId) {
          return section.stateId === queryParams.stateId;
        } else {
          return false;
        }
      } else {
        return false;
      }
    } else {
      return this.router.isActive(section.path, false);
    }
  }

  public getCurrentCustomSection(): MenuSection {
    return this.currentCustomSection;
  }

  public getCurrentCustomChildSection(): MenuSection {
    return this.currentCustomChildSection;
  }

  private updateCurrentCustomSection() {
    const queryParams = this.extractQueryParams();
    this.currentCustomSection = this.detectCurrentCustomSection(queryParams);
    this.currentCustomChildSection = this.detectCurrentCustomChildSection(queryParams);
  }

  private detectCurrentCustomSection(queryParams: Params): MenuSection {
    if (queryParams && queryParams.stateId) {
      const stateId: string = queryParams.stateId;
      const found =
        this.currentMenuSections.find((section) => section.isCustom && section.stateId === stateId);
      if (found) {
        return found;
      }
    }
    return null;
  }

  private detectCurrentCustomChildSection(queryParams: Params): MenuSection {
    if (queryParams && queryParams.childStateId) {
      const stateId = queryParams.childStateId;
      for (const section of this.currentMenuSections) {
        if (section.isCustom && section.pages && section.pages.length) {
          const found =
            section.pages.find((childSection) => childSection.stateId === stateId);
          if (found) {
            return found;
          }
        }
      }
    }
    return null;
  }

  private extractQueryParams(): Params {
    const state = this.router.routerState;
    const snapshot =  state.snapshot;
    let lastChild = snapshot.root;
    while (lastChild.children.length) {
      lastChild = lastChild.children[0];
    }
    return lastChild.queryParams;
  }

  public getRedirectPath(parentPath: string, redirectPath: string): Observable<string> {
    return this.menuSections$.pipe(
      mergeMap((sections) => {
        const filtered = sections.filter((section) => section.path === parentPath);
        if (filtered && filtered.length) {
          const parentSection = filtered[0];
          if (parentSection.asyncPages) {
            return parentSection.asyncPages.pipe(
              map((childPages) => {
                const filteredPages = childPages.filter((page) => !page.disabled);
                if (filteredPages && filteredPages.length) {
                  const redirectPage = filteredPages.filter((page) => page.path === redirectPath);
                  if (!redirectPage || !redirectPage.length) {
                    return filteredPages[0].path;
                  }
                }
                return redirectPath;
              })
            );
          }
        }
        return of(redirectPath);
      })
    );
  }
}

class EntityGroupSection {

  private section: MenuSection;

  private loadedGroupPages: Observable<Array<MenuSection>> = null;

  private subscriptions: Subscription[] = [];

  private groupsPagesSubject: BehaviorSubject<Array<MenuSection>> = new BehaviorSubject([]);

  constructor(private router: Router,
              private groupType: EntityType,
              private broadcast: BroadcastService,
              private entityGroupService: EntityGroupService) {
    this.subscriptions.push(this.broadcast.on(this.groupType + 'changed', () => {
      this.reloadGroups();
    }));
    this.subscriptions.push(this.router.events.pipe(filter(event => event instanceof ActivationEnd)).subscribe(
      () => {
        this.loadGroups();
      }
    ));
    this.buildMenuSection();
    this.loadGroups();
  }

  public getMenuSection(): MenuSection {
    return this.section;
  }

  public destroy() {
    for (const subscription of this.subscriptions) {
      subscription.unsubscribe();
    }
    this.subscriptions.length = 0;
  }

  private reloadGroups() {
    this.loadedGroupPages = null;
    this.loadGroups();
  }

  private loadGroups() {
    if (this.router.isActive(this.section.path, false) && !this.loadedGroupPages) {
      this.loadGroupPages().subscribe((groupPages) => {
        this.groupsPagesSubject.next(groupPages);
      });
    }
  }

  private buildMenuSection() {
    let name: string;
    let path: string;
    let icon: string;
    switch (this.groupType) {
      case EntityType.DEVICE:
        name = 'entity-group.device-groups';
        path = '/deviceGroups';
        icon = 'devices_other';
        break;
      case EntityType.ASSET:
        name = 'entity-group.asset-groups';
        path = '/assetGroups';
        icon = 'domain';
        break;
      case EntityType.ENTITY_VIEW:
        name = 'entity-group.entity-view-groups';
        path = '/entityViewGroups';
        icon = 'view_quilt';
        break;
      case EntityType.DASHBOARD:
        name = 'entity-group.dashboard-groups';
        path = '/dashboardGroups';
        icon = 'dashboard';
        break;
      case EntityType.USER:
        name = 'entity-group.user-groups';
        path = '/userGroups';
        icon = 'account_circle';
        break;
      case EntityType.CUSTOMER:
        name = 'entity-group.customer-groups';
        path = '/customerGroups';
        icon = 'supervisor_account';
        break;
    }
    this.section = {
      id: guid(),
      name,
      type: 'toggle',
      path,
      icon,
      groupType: this.groupType,
      asyncPages: this.groupsPagesSubject,
      pages: this.groupsPagesSubject.value
    };
  }

  private loadGroupPages(): Observable<Array<MenuSection>> {
    if (!this.loadedGroupPages) {
      this.loadedGroupPages = this.entityGroupService.getEntityGroups(this.groupType).pipe(
        map((groups) => {
          const pages: MenuSection[] = [];
          groups.forEach((entityGroup) => {
            pages.push(
              {
                id: entityGroup.id.id,
                name: entityGroup.name,
                path: `${this.section.path}/${entityGroup.id.id}`,
                type: 'link',
                icon: this.section.icon,
                ignoreTranslate: true
              }
            );
          });
          this.section.pages = pages;
          return pages;
        }),
        publishReplay(1),
        refCount()
      );
    }
    return this.loadedGroupPages;
  }

}

