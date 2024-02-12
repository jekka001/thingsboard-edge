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

import { inject, Injectable, NgModule } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve, ResolveFn, RouterModule, RouterStateSnapshot, Routes } from '@angular/router';
import { MailServerComponent } from '@modules/home/pages/admin/mail-server.component';
import { SmsProviderComponent } from '@home/pages/admin/sms-provider.component';
import { ConfirmOnExitGuard } from '@core/guards/confirm-on-exit.guard';
import { Authority } from '@shared/models/authority.enum';
import { GeneralSettingsComponent } from '@home/pages/admin/general-settings.component';
import { SecuritySettingsComponent } from '@modules/home/pages/admin/security-settings.component';
import { MailTemplatesComponent } from '@home/pages/admin/mail-templates.component';
import { Observable } from 'rxjs';
import { MailTemplatesSettings } from '@shared/models/settings.models';
import { CustomTranslation } from '@shared/models/custom-translation.model';
import { CustomTranslationService } from '@core/http/custom-translation.service';
import { CustomTranslationComponent } from '@home/pages/admin/custom-translation.component';
import { CustomMenuComponent } from '@home/pages/admin/custom-menu.component';
import { CustomMenu } from '@shared/models/custom-menu.models';
import { CustomMenuService } from '@core/http/custom-menu.service';
import { WhiteLabelingComponent } from '@home/pages/admin/white-labeling.component';
import { SelfRegistrationComponent } from '@home/pages/admin/self-registration.component';
import { OAuth2SettingsComponent } from '@home/pages/admin/oauth2-settings.component';
import { OAuth2Service } from '@core/http/oauth2.service';
import { HomeSettingsComponent } from '@home/pages/admin/home-settings.component';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { ResourcesLibraryTableConfigResolver } from '@home/pages/admin/resource/resources-library-table-config.resolve';
import { EntityDetailsPageComponent } from '@home/components/entity/entity-details-page.component';
import { entityDetailsPageBreadcrumbLabelFunction } from '@home/pages/home-pages.models';
import { BreadCrumbConfig } from '@shared/components/breadcrumb';
import { QueuesTableConfigResolver } from '@home/pages/admin/queue/queues-table-config.resolver';
import { RepositoryAdminSettingsComponent } from '@home/pages/admin/repository-admin-settings.component';
import { AutoCommitAdminSettingsComponent } from '@home/pages/admin/auto-commit-admin-settings.component';
import { TwoFactorAuthSettingsComponent } from '@home/pages/admin/two-factor-auth-settings.component';
import { widgetsLibraryRoutes } from '@home/pages/widget/widget-library-routing.module';
import { RouterTabsComponent } from '@home/components/router-tabs.component';
import { auditLogsRoutes } from '@home/pages/audit-log/audit-log-routing.module';
import { ImageGalleryComponent } from '@shared/components/image/image-gallery.component';
import { rolesRoutes } from '@home/pages/role/role-routing.module';
import { WhiteLabelingService } from '@core/http/white-labeling.service';

export const mailTemplateSettingsResolver: ResolveFn<MailTemplatesSettings> = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot,
  wl = inject(WhiteLabelingService)
): Observable<MailTemplatesSettings> => wl.getMailTemplates(true);

@Injectable()
export class CustomTranslationResolver implements Resolve<CustomTranslation> {

  constructor(private customTranslationService: CustomTranslationService) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<CustomTranslation> {
    return this.customTranslationService.getCurrentCustomTranslation();
  }
}

@Injectable()
export class CustomMenuResolver implements Resolve<CustomMenu> {

  constructor(private customMenuService: CustomMenuService) {
  }

  resolve(route: ActivatedRouteSnapshot): Observable<CustomMenu> {
    return this.customMenuService.getCurrentCustomMenu();
  }
}

@Injectable()
export class OAuth2LoginProcessingUrlResolver implements Resolve<string> {

  constructor(private oauth2Service: OAuth2Service) {
  }

  resolve(): Observable<string> {
    return this.oauth2Service.getLoginProcessingUrl();
  }
}

const routes: Routes = [
  {
    path: 'resources',
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      breadcrumb: {
        label: 'admin.resources',
        icon: 'folder'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          redirectTo: {
            SYS_ADMIN: '/resources/widgets-library',
            TENANT_ADMIN: '/resources/widgets-library',
            CUSTOMER_USER: '/resources/images'
          }
        }
      },
      ...widgetsLibraryRoutes,
      {
        path: 'images',
        data: {
          breadcrumb: {
            label: 'image.gallery',
            icon: 'filter'
          }
        },
        children: [
          {
            path: '',
            component: ImageGalleryComponent,
            data: {
              auth: [Authority.TENANT_ADMIN, Authority.SYS_ADMIN, Authority.CUSTOMER_USER],
              title: 'image.gallery',
            },
          }
        ]
      },
      {
        path: 'resources-library',
        data: {
          breadcrumb: {
            label: 'resource.resources-library',
            icon: 'mdi:rhombus-split'
          }
        },
        children: [
          {
            path: '',
            component: EntitiesTableComponent,
            data: {
              auth: [Authority.TENANT_ADMIN, Authority.SYS_ADMIN],
              title: 'resource.resources-library',
            },
            resolve: {
              entitiesTableConfig: ResourcesLibraryTableConfigResolver
            }
          },
          {
            path: ':entityId',
            component: EntityDetailsPageComponent,
            canDeactivate: [ConfirmOnExitGuard],
            data: {
              breadcrumb: {
                labelFunction: entityDetailsPageBreadcrumbLabelFunction,
                icon: 'mdi:rhombus-split'
              } as BreadCrumbConfig<EntityDetailsPageComponent>,
              auth: [Authority.TENANT_ADMIN, Authority.SYS_ADMIN],
              title: 'resource.resources-library'
            },
            resolve: {
              entitiesTableConfig: ResourcesLibraryTableConfigResolver
            }
          }
        ]
      }
    ]
  },
  {
    path: 'settings',
    component: RouterTabsComponent,
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      showMainLoadingBar: false,
      breadcrumb: {
        label: 'admin.settings',
        icon: 'settings'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          redirectTo: {
            SYS_ADMIN: '/settings/general',
            TENANT_ADMIN: '/settings/home',
            CUSTOMER_USER: '/settings/home'
          }
        }
      },
      {
        path: 'general',
        component: GeneralSettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'admin.general-settings',
          breadcrumb: {
            label: 'admin.general',
            icon: 'settings_applications'
          }
        }
      },
      {
        path: 'outgoing-mail',
        component: MailServerComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
          title: 'admin.outgoing-mail-settings',
          breadcrumb: {
            label: 'admin.outgoing-mail',
            icon: 'mail'
          }
        }
      },
      {
        path: 'notifications',
        component: SmsProviderComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
          title: 'admin.notifications-settings',
          breadcrumb: {
            label: 'admin.notifications',
            icon: 'mdi:message-badge'
          }
        }
      },
      {
        path: 'queues',
        data: {
          breadcrumb: {
            label: 'admin.queues',
            icon: 'swap_calls'
          }
        },
        children: [
          {
            path: '',
            component: EntitiesTableComponent,
            data: {
              auth: [Authority.SYS_ADMIN],
              title: 'admin.queues'
            },
            resolve: {
              entitiesTableConfig: QueuesTableConfigResolver
            }
          },
          {
            path: ':entityId',
            component: EntityDetailsPageComponent,
            canDeactivate: [ConfirmOnExitGuard],
            data: {
              breadcrumb: {
                labelFunction: entityDetailsPageBreadcrumbLabelFunction,
                icon: 'swap_calls'
              } as BreadCrumbConfig<EntityDetailsPageComponent>,
              auth: [Authority.SYS_ADMIN],
              title: 'admin.queues'
            },
            resolve: {
              entitiesTableConfig: QueuesTableConfigResolver
            }
          }
        ]
      },
      {
        path: 'home',
        component: HomeSettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          title: 'admin.home-settings',
          breadcrumb: {
            label: 'admin.home-settings',
            icon: 'settings_applications'
          }
        }
      },
      {
        path: 'repository',
        component: RepositoryAdminSettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.TENANT_ADMIN],
          title: 'admin.repository-settings',
          breadcrumb: {
            label: 'admin.repository-settings',
            icon: 'manage_history'
          }
        }
      },
      {
        path: 'auto-commit',
        component: AutoCommitAdminSettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.TENANT_ADMIN],
          title: 'admin.auto-commit-settings',
          breadcrumb: {
            label: 'admin.auto-commit-settings',
            icon: 'settings_backup_restore'
          }
        }
      },
      {
        path: 'security-settings',
        redirectTo: '/security-settings/general'
      },
      {
        path: 'selfRegistration',
        redirectTo: '/security-settings/selfRegistration'
      },
      {
        path: 'oauth2',
        redirectTo: '/security-settings/oauth2'
      },
      {
        path: 'resources-library',
        pathMatch: 'full',
        redirectTo: '/resources/resources-library'
      },
      {
        path: 'resources-library/:entityId',
        redirectTo: '/resources/resources-library/:entityId'
      },
      {
        path: '2fa',
        redirectTo: '/security-settings/2fa'
      },
      {
        path: 'sms-provider',
        redirectTo: '/settings/notifications'
      }
    ]
  },
  {
    path: 'security-settings',
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      breadcrumb: {
        label: 'security.security',
        icon: 'security'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          redirectTo: {
            SYS_ADMIN: '/security-settings/general',
            TENANT_ADMIN: '/security-settings/2fa',
            CUSTOMER_USER: '/security-settings/roles',
          }
        }
      },
      {
        path: 'general',
        component: SecuritySettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'admin.general',
          breadcrumb: {
            label: 'admin.general',
            icon: 'settings_applications'
          }
        }
      },
      {
        path: '2fa',
        component: TwoFactorAuthSettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
          title: 'admin.2fa.2fa',
          breadcrumb: {
            label: 'admin.2fa.2fa',
            icon: 'mdi:two-factor-authentication'
          }
        }
      },
      {
        path: 'oauth2',
        component: OAuth2SettingsComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN],
          title: 'admin.oauth2.oauth2',
          breadcrumb: {
            label: 'admin.oauth2.oauth2',
            icon: 'security'
          }
        },
        resolve: {
          loginProcessingUrl: OAuth2LoginProcessingUrlResolver
        }
      },
      ...rolesRoutes,
      {
        path: 'selfRegistration',
        component: SelfRegistrationComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.TENANT_ADMIN],
          title: 'self-registration.self-registration',
          breadcrumb: {
            label: 'self-registration.self-registration',
            icon: 'group_add'
          }
        }
      },
      ...auditLogsRoutes
    ]
  },
  {
    path: 'white-labeling',
    component: RouterTabsComponent,
    data: {
      auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
      showMainLoadingBar: false,
      breadcrumb: {
        label: 'white-labeling.white-labeling',
        icon: 'format_paint'
      }
    },
    children: [
      {
        path: '',
        children: [],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          redirectTo: {
            SYS_ADMIN: '/white-labeling/whiteLabel',
            TENANT_ADMIN: '/white-labeling/whiteLabel',
            CUSTOMER_USER: '/white-labeling/whiteLabel'
          }
        }
      },
      {
        path: 'whiteLabel',
        component: WhiteLabelingComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          title: 'white-labeling.white-labeling',
          isLoginWl: false,
          breadcrumb: {
            label: 'white-labeling.general',
            icon: 'format_paint'
          }
        }
      },
      {
        path: 'loginWhiteLabel',
        component: WhiteLabelingComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          title: 'white-labeling.login-white-labeling',
          isLoginWl: true,
          breadcrumb: {
            label: 'white-labeling.login',
            icon: 'format_paint'
          }
        }
      },
      {
        path: 'mail-template',
        component: MailTemplatesComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN],
          title: 'admin.mail-template-settings',
          breadcrumb: {
            label: 'admin.mail-templates',
            icon: 'format_shapes'
          }
        },
        resolve: {
          mailTemplatesSettings: mailTemplateSettingsResolver
        }
      },
      {
        path: 'customTranslation',
        component: CustomTranslationComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          title: 'custom-translation.custom-translation',
          breadcrumb: {
            label: 'custom-translation.custom-translation',
            icon: 'language'
          }
        },
        resolve: {
          customTranslation: CustomTranslationResolver
        }
      },
      {
        path: 'customMenu',
        component: CustomMenuComponent,
        canDeactivate: [ConfirmOnExitGuard],
        data: {
          auth: [Authority.SYS_ADMIN, Authority.TENANT_ADMIN, Authority.CUSTOMER_USER],
          title: 'custom-menu.custom-menu',
          breadcrumb: {
            label: 'custom-menu.custom-menu',
            icon: 'list'
          }
        },
        resolve: {
          customMenu: CustomMenuResolver
        }
      },
      {
        path: 'selfRegistration',
        redirectTo: '/security-settings/selfRegistration'
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
  providers: [
    CustomTranslationResolver,
    CustomMenuResolver,
    OAuth2LoginProcessingUrlResolver,
    ResourcesLibraryTableConfigResolver,
    QueuesTableConfigResolver
  ]
})
export class AdminRoutingModule { }
