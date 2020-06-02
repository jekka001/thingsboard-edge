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

import { IDashboardComponent } from '@home/models/dashboard-component.models';
import {
  DataSet,
  Datasource,
  DatasourceData,
  JsonSettingsSchema,
  Widget,
  WidgetActionDescriptor,
  WidgetActionSource,
  WidgetConfig,
  WidgetControllerDescriptor,
  WidgetType,
  widgetType,
  WidgetTypeDescriptor,
  WidgetTypeParameters,
  WidgetExportType
} from '@shared/models/widget.models';
import { Timewindow, WidgetTimewindow } from '@shared/models/time/time.models';
import {
  IAliasController,
  IStateController,
  IWidgetSubscription,
  IWidgetUtils,
  RpcApi,
  SubscriptionEntityInfo,
  TimewindowFunctions,
  WidgetActionsApi,
  WidgetSubscriptionApi
} from '@core/api/widget-api.models';
import { ChangeDetectorRef, ComponentFactory, Injector, NgZone, Type } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { RafService } from '@core/services/raf.service';
import { WidgetTypeId } from '@shared/models/id/widget-type-id';
import { TenantId } from '@shared/models/id/tenant-id';
import { WidgetLayout } from '@shared/models/dashboard.models';
import { formatValue, isDefined } from '@core/utils';
import { forkJoin, of } from 'rxjs';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  NotificationHorizontalPosition,
  NotificationType,
  NotificationVerticalPosition
} from '@core/notification/notification.models';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { AuthUser } from '@shared/models/user.model';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { DeviceService } from '@core/http/device.service';
import { AssetService } from '@core/http/asset.service';
import { EntityViewService } from '@core/http/entity-view.service';
import { CustomerService } from '@core/http/customer.service';
import { DashboardService } from '@core/http/dashboard.service';
import { UserService } from '@core/http/user.service';
import { AttributeService } from '@core/http/attribute.service';
import { EntityRelationService } from '@core/http/entity-relation.service';
import { EntityService } from '@core/http/entity.service';
import { DialogService } from '@core/services/dialog.service';
import { CustomDialogService } from '@home/components/widget/dialog/custom-dialog.service';
import { DatePipe } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { EntityGroupService } from '@core/http/entity-group.service';

export interface IWidgetAction {
  name: string;
  icon: string;
  onAction: ($event: Event) => void;
}

export interface WidgetHeaderAction extends IWidgetAction {
  displayName: string;
  descriptor: WidgetActionDescriptor;
}

export interface WidgetAction extends IWidgetAction {
  show: boolean;
}

export interface IDashboardWidget {
  updateWidgetParams();
}

export class WidgetContext {

  constructor(public dashboard: IDashboardComponent,
              private dashboardWidget: IDashboardWidget,
              private widget: Widget) {}

  get stateController(): IStateController {
    return this.dashboard.stateController;
  }

  get aliasController(): IAliasController {
    return this.dashboard.aliasController;
  }

  get dashboardTimewindow(): Timewindow {
    return this.dashboard.dashboardTimewindow;
  }

  get widgetConfig(): WidgetConfig {
    return this.widget.config;
  }

  get settings(): any {
    return this.widget.config.settings;
  }

  get units(): string {
    return this.widget.config.units || '';
  }

  get decimals(): number {
    return isDefined(this.widget.config.decimals) ? this.widget.config.decimals : 2;
  }

  set changeDetector(cd: ChangeDetectorRef) {
    this.changeDetectorValue = cd;
  }

  get currentUser(): AuthUser {
    if (this.store) {
      return getCurrentAuthUser(this.store);
    } else {
      return null;
    }
  }

  deviceService: DeviceService;
  assetService: AssetService;
  entityViewService: EntityViewService;
  customerService: CustomerService;
  dashboardService: DashboardService;
  userService: UserService;
  attributeService: AttributeService;
  entityRelationService: EntityRelationService;
  entityService: EntityService;
  entityGroupService: EntityGroupService;
  dialogs: DialogService;
  customDialog: CustomDialogService;
  date: DatePipe;
  translate: TranslateService;
  http: HttpClient;

  private changeDetectorValue: ChangeDetectorRef;

  inited = false;
  destroyed = false;

  subscriptions: {[id: string]: IWidgetSubscription} = {};
  defaultSubscription: IWidgetSubscription = null;

  timewindowFunctions: TimewindowFunctions = {
    onUpdateTimewindow: (startTimeMs, endTimeMs, interval) => {
      if (this.defaultSubscription) {
        this.defaultSubscription.onUpdateTimewindow(startTimeMs, endTimeMs, interval);
      }
    },
    onResetTimewindow: () => {
      if (this.defaultSubscription) {
        this.defaultSubscription.onResetTimewindow();
      }
    }
  };

  controlApi: RpcApi = {
    sendOneWayCommand: (method, params, timeout) => {
      if (this.defaultSubscription) {
        return this.defaultSubscription.sendOneWayCommand(method, params, timeout);
      } else {
        return of(null);
      }
    },
    sendTwoWayCommand: (method, params, timeout) => {
      if (this.defaultSubscription) {
        return this.defaultSubscription.sendTwoWayCommand(method, params, timeout);
      } else {
        return of(null);
      }
    }
  };

  utils: IWidgetUtils = {
    formatValue
  };

  $container: JQuery<HTMLElement>;
  $containerParent: JQuery<HTMLElement>;
  width: number;
  height: number;
  $scope: IDynamicWidgetComponent;
  isEdit: boolean;
  isMobile: boolean;

  widgetNamespace?: string;
  subscriptionApi?: WidgetSubscriptionApi;

  actionsApi?: WidgetActionsApi;
  activeEntityInfo?: SubscriptionEntityInfo;

  exportWidgetData: (widgetExportType: WidgetExportType) => void;
  customDataExport?: () => {[key: string]: any}[];

  datasources?: Array<Datasource>;
  data?: Array<DatasourceData>;
  hiddenData?: Array<{data: DataSet}>;
  timeWindow?: WidgetTimewindow;

  hideTitlePanel = false;

  widgetTitle?: string;
  widgetTitleTooltip?: string;
  customHeaderActions?: Array<WidgetHeaderAction>;
  widgetActions?: Array<WidgetAction>;

  servicesMap?: Map<string, Type<any>>;

  $injector?: Injector;

  ngZone?: NgZone;

  store?: Store<AppState>;

  rxjs = {
    forkJoin,
    of
  };

  showSuccessToast(message: string, duration: number = 1000,
                   verticalPosition: NotificationVerticalPosition = 'bottom',
                   horizontalPosition: NotificationHorizontalPosition = 'left',
                   target?: string) {
    this.showToast('success', message, duration, verticalPosition, horizontalPosition, target);
  }

  showErrorToast(message: string,
                 verticalPosition: NotificationVerticalPosition = 'bottom',
                 horizontalPosition: NotificationHorizontalPosition = 'left',
                 target?: string) {
    this.showToast('error', message, undefined, verticalPosition, horizontalPosition, target);
  }

  showToast(type: NotificationType, message: string, duration: number,
            verticalPosition: NotificationVerticalPosition = 'bottom',
            horizontalPosition: NotificationHorizontalPosition = 'left',
            target?: string) {
    this.store.dispatch(new ActionNotificationShow(
      {
        message,
        type,
        duration,
        verticalPosition,
        horizontalPosition,
        target,
        panelClass: this.widgetNamespace,
        forceDismiss: true
      }));
  }

  detectChanges(updateWidgetParams: boolean = false) {
    if (!this.destroyed) {
      if (updateWidgetParams) {
        this.dashboardWidget.updateWidgetParams();
      }
      try {
        this.changeDetectorValue.detectChanges();
      } catch (e) {
        // console.log(e);
      }
    }
  }

  updateWidgetParams() {
    if (!this.destroyed) {
      setTimeout(() => {
        this.dashboardWidget.updateWidgetParams();
      }, 0);
    }
  }

  updateAliases(aliasIds?: Array<string>) {
    this.aliasController.updateAliases(aliasIds);
  }

  reset() {
    this.destroyed = false;
    this.hideTitlePanel = false;
    this.widgetTitle = undefined;
    this.widgetActions = undefined;
  }
}

export interface IDynamicWidgetComponent {
  readonly ctx: WidgetContext;
  readonly errorMessages: string[];
  readonly $injector: Injector;
  executingRpcRequest: boolean;
  rpcEnabled: boolean;
  rpcErrorText: string;
  rpcRejection: HttpErrorResponse;
  raf: RafService;
  [key: string]: any;
}

export interface WidgetInfo extends WidgetTypeDescriptor, WidgetControllerDescriptor {
  widgetName: string;
  alias: string;
  typeSettingsSchema?: string | any;
  typeDataKeySettingsSchema?: string | any;
  componentFactory?: ComponentFactory<IDynamicWidgetComponent>;
}

export interface WidgetConfigComponentData {
  config: WidgetConfig;
  layout: WidgetLayout;
  widgetType: widgetType;
  typeParameters: WidgetTypeParameters;
  actionSources: {[actionSourceId: string]: WidgetActionSource};
  isDataEnabled: boolean;
  settingsSchema: JsonSettingsSchema;
  dataKeySettingsSchema: JsonSettingsSchema;
}

export const MissingWidgetType: WidgetInfo = {
  type: widgetType.latest,
  widgetName: 'Widget type not found',
  alias: 'undefined',
  sizeX: 8,
  sizeY: 6,
  resources: [],
  templateHtml: '<div class="tb-widget-error-container">' +
    '<div class="tb-widget-error-msg" innerHTML="{{\'widget.widget-type-not-found\' | translate }}"></div>' +
    '</div>',
  templateCss: '',
  controllerScript: 'self.onInit = function() {}',
  settingsSchema: '{}\n',
  dataKeySettingsSchema: '{}\n',
  defaultConfig: '{\n' +
    '"title": "Widget type not found",\n' +
    '"datasources": [],\n' +
    '"settings": {}\n' +
    '}\n',
  typeParameters: {}
};

export const ErrorWidgetType: WidgetInfo = {
  type: widgetType.latest,
  widgetName: 'Error loading widget',
  alias: 'error',
  sizeX: 8,
  sizeY: 6,
  resources: [],
  templateHtml: '<div class="tb-widget-error-container">' +
                   '<div translate class="tb-widget-error-msg">widget.widget-type-load-error</div>' +
                   '<div *ngFor="let error of errorMessages" class="tb-widget-error-msg">{{ error }}</div>' +
                '</div>',
  templateCss: '',
  controllerScript: 'self.onInit = function() {}',
  settingsSchema: '{}\n',
  dataKeySettingsSchema: '{}\n',
  defaultConfig: '{\n' +
    '"title": "Widget failed to load",\n' +
    '"datasources": [],\n' +
    '"settings": {}\n' +
    '}\n',
  typeParameters: {}
};

export interface WidgetTypeInstance {
  getSettingsSchema?: () => string;
  getDataKeySettingsSchema?: () => string;
  typeParameters?: () => WidgetTypeParameters;
  useCustomDatasources?: () => boolean;
  actionSources?: () => {[actionSourceId: string]: WidgetActionSource};

  onInit?: () => void;
  onDataUpdated?: () => void;
  onResize?: () => void;
  onEditModeChanged?: () => void;
  onMobileModeChanged?: () => void;
  onDestroy?: () => void;
}

export function toWidgetInfo(widgetTypeEntity: WidgetType): WidgetInfo {
  return {
    widgetName: widgetTypeEntity.name,
    alias: widgetTypeEntity.alias,
    type: widgetTypeEntity.descriptor.type,
    sizeX: widgetTypeEntity.descriptor.sizeX,
    sizeY: widgetTypeEntity.descriptor.sizeY,
    resources: widgetTypeEntity.descriptor.resources,
    templateHtml: widgetTypeEntity.descriptor.templateHtml,
    templateCss: widgetTypeEntity.descriptor.templateCss,
    controllerScript: widgetTypeEntity.descriptor.controllerScript,
    settingsSchema: widgetTypeEntity.descriptor.settingsSchema,
    dataKeySettingsSchema: widgetTypeEntity.descriptor.dataKeySettingsSchema,
    defaultConfig: widgetTypeEntity.descriptor.defaultConfig
  };
}

export function toWidgetType(widgetInfo: WidgetInfo, id: WidgetTypeId, tenantId: TenantId, bundleAlias: string): WidgetType {
  const descriptor: WidgetTypeDescriptor = {
    type: widgetInfo.type,
    sizeX: widgetInfo.sizeX,
    sizeY: widgetInfo.sizeY,
    resources: widgetInfo.resources,
    templateHtml: widgetInfo.templateHtml,
    templateCss: widgetInfo.templateCss,
    controllerScript: widgetInfo.controllerScript,
    settingsSchema: widgetInfo.settingsSchema,
    dataKeySettingsSchema: widgetInfo.dataKeySettingsSchema,
    defaultConfig: widgetInfo.defaultConfig
  };
  return {
    id,
    tenantId,
    bundleAlias,
    alias: widgetInfo.alias,
    name: widgetInfo.widgetName,
    descriptor
  };
}
