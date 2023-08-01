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

import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, Input, NgZone, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, UntypedFormControl, ValidatorFn, Validators } from '@angular/forms';
import { EntityId } from '@shared/models/id/entity-id';
import { MatDialog } from '@angular/material/dialog';
import { AttributeService } from '@core/http/attribute.service';
import { DeviceService } from '@core/http/device.service';
import { TranslateService } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { AttributeData, AttributeScope } from '@shared/models/telemetry/telemetry.models';
import { PageComponent } from '@shared/components/page.component';
import { PageLink } from '@shared/models/page/page-link';
import { AttributeDatasource } from '@home/models/datasource/attribute-datasource';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { MatSort } from '@angular/material/sort';
import { TelemetryWebsocketService } from '@core/ws/telemetry-websocket.service';
import { MatTableDataSource } from '@angular/material/table';
import { GatewayLogLevel } from '@home/components/widget/lib/gateway/gateway-configuration.component';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { DialogService } from '@core/services/dialog.service';
import { WidgetContext } from '@home/models/widget-component.models';
import { deepClone } from '@core/utils';
import { NULL_UUID } from '@shared/models/id/has-uuid';
import { IWidgetSubscription, WidgetSubscriptionOptions } from '@core/api/widget-api.models';
import { DatasourceType, widgetType } from '@shared/models/widget.models';
import { UtilsService } from '@core/services/utils.service';
import { EntityType } from '@shared/models/entity-type.models';


export interface gatewayConnector {
  name: string;
  type: string;
  configuration?: string;
  configurationJson: string;
  logLevel: string;
  key?: string;
}


export const GatewayConnectorDefaultTypesTranslates = new Map<string, string>([
  ['mqtt', 'MQTT'],
  ['modbus', 'MODBUS'],
  ['grpc', 'GRPC'],
  ['opcua', 'OPCUA'],
  ['opcua_asyncio', 'OPCUA ASYNCIO'],
  ['ble', 'BLE'],
  ['request', 'REQUEST'],
  ['can', 'CAN'],
  ['bacnet', 'BACNET'],
  ['odbc', 'ODBC'],
  ['rest', 'REST'],
  ['snmp', 'SNMP'],
  ['ftp', 'FTP'],
  ['socket', 'SOCKET'],
  ['xmpp', 'XMPP'],
  ['ocpp', 'OCCP'],
  ['custom', 'CUSTOM']
]);

@Component({
  selector: 'tb-gateway-connector',
  templateUrl: './gateway-connectors.component.html',
  styleUrls: ['./gateway-connectors.component.scss']
})
export class GatewayConnectorComponent extends PageComponent implements AfterViewInit {

  pageLink: PageLink;

  attributeDataSource: AttributeDatasource;

  inactiveConnectorsDataSource: AttributeDatasource;

  serverDataSource: AttributeDatasource;

  dataSource: MatTableDataSource<AttributeData>;

  displayedColumns = ['enabled', 'key', 'type', 'syncStatus', 'errors', 'actions'];

  gatewayConnectorDefaultTypes = GatewayConnectorDefaultTypesTranslates;

  @Input()
  ctx: WidgetContext;

  @Input()
  device: EntityId;

  @ViewChild('nameInput') nameInput: ElementRef;
  @ViewChild(MatSort, { static: false }) sort: MatSort;

  connectorForm: FormGroup;

  viewsInited = false;

  textSearchMode: boolean;

  activeConnectors: Array<string>;

  inactiveConnectors: Array<string>;

  InitialActiveConnectors: Array<string>;

  gatewayLogLevel = Object.values(GatewayLogLevel);

  activeData: Array<any> = [];

  inactiveData: Array<any> = [];

  sharedAttributeData: Array<AttributeData> = [];

  initialConnector: gatewayConnector;

  subscriptionOptions: WidgetSubscriptionOptions = {
    callbacks: {
      onDataUpdated: () => this.ctx.ngZone.run(() => {
        this.onDataUpdated();
      }),
      onDataUpdateError: (subscription, e) => this.ctx.ngZone.run(() => {
        this.onDataUpdateError(e);
      })
    }
  };

  subscription: IWidgetSubscription;

  constructor(protected router: Router,
              protected store: Store<AppState>,
              protected fb: FormBuilder,
              protected translate: TranslateService,
              protected attributeService: AttributeService,
              protected deviceService: DeviceService,
              protected dialogService: DialogService,
              private telemetryWsService: TelemetryWebsocketService,
              private zone: NgZone,
              private utils: UtilsService,
              private cd: ChangeDetectorRef,
              public dialog: MatDialog) {
    super(store);
    const sortOrder: SortOrder = {property: 'key', direction: Direction.ASC};
    this.pageLink = new PageLink(1000, 0, null, sortOrder);
    this.attributeDataSource = new AttributeDatasource(this.attributeService, this.telemetryWsService, this.zone, this.translate);
    this.inactiveConnectorsDataSource = new AttributeDatasource(this.attributeService, this.telemetryWsService, this.zone, this.translate);
    this.serverDataSource = new AttributeDatasource(this.attributeService, this.telemetryWsService, this.zone, this.translate);
    this.dataSource = new MatTableDataSource<AttributeData>([]);
    this.connectorForm = this.fb.group({
      name: ['', [Validators.required, this.uniqNameRequired()]],
      type: ['', [Validators.required]],
      logLevel: ['', [Validators.required]],
      key: ['auto'],
      class: [''],
      configuration: [''],
      configurationJson: [{}, [Validators.required]]
    });
    this.connectorForm.disable();
  }

  ngAfterViewInit() {
    this.connectorForm.valueChanges.subscribe(() => {
      this.cd.detectChanges();
    });

    this.dataSource.sort = this.sort;
    this.dataSource.sortingDataAccessor = (data: AttributeData, sortHeaderId: string) => {
      if (sortHeaderId === 'syncStatus') {
        return this.isConnectorSynced(data) ? 1 : 0;
      } else if (sortHeaderId === 'enabled') {
        return this.activeConnectors.includes(data.key) ? 1 : 0;
      }
      return data[sortHeaderId] || data.value[sortHeaderId];
    };

    this.viewsInited = true;
    if (this.device) {
      if (this.device.id === NULL_UUID) return;
      forkJoin(this.attributeService.getEntityAttributes(this.device, AttributeScope.SHARED_SCOPE, ['active_connectors']),
        this.attributeService.getEntityAttributes(this.device, AttributeScope.SERVER_SCOPE, ['inactive_connectors'])).subscribe(attributes => {
        if (attributes.length) {
          this.activeConnectors = attributes[0].length ? attributes[0][0].value : [];
          this.activeConnectors = typeof this.activeConnectors === 'string' ? JSON.parse(this.activeConnectors) : this.activeConnectors;
          this.inactiveConnectors = attributes[1].length ? attributes[1][0].value : [];
          this.inactiveConnectors = typeof this.inactiveConnectors === 'string' ? JSON.parse(this.inactiveConnectors) : this.inactiveConnectors;
          this.updateData(true);
        } else {
          this.activeConnectors = [];
          this.inactiveConnectors = [];
          this.updateData(true);
        }
      });
    }
  }

  uniqNameRequired(): ValidatorFn {
    return (c: UntypedFormControl) => {
      const newName = c.value.trim().toLowerCase();
      const found = this.dataSource.data.find((connectorAttr) => {
        const connectorData = connectorAttr.value;
        return connectorData.name.toLowerCase() === newName;
      });
      if (found) {
        if (this.initialConnector && this.initialConnector.name.toLowerCase() === newName) {
          return null;
        }
        return {
          duplicateName: {
            valid: false
          }
        };
      }
      return null;
    };
  }

  saveConnector(): void {
    const value = this.connectorForm.value;
    value.configuration = this.camelize(value.name) + '.json';
    if (value.type !== 'grpc') {
      delete value.key;
    }
    if (value.type !== 'custom') {
      delete value.class;
    }
    value.ts = new Date().getTime();
    const attributesToSave = [{
      key: value.name,
      value
    }];
    const attributesToDelete = [];
    const scope = (this.initialConnector && this.activeConnectors.includes(this.initialConnector.name)) ? AttributeScope.SHARED_SCOPE : AttributeScope.SERVER_SCOPE;
    let updateActiveConnectors = false;
    if (this.initialConnector && this.initialConnector.name !== value.name) {
      attributesToDelete.push({key: this.initialConnector.name});
      updateActiveConnectors = true;
      const activeIndex = this.activeConnectors.indexOf(this.initialConnector.name);
      const inactiveIndex = this.inactiveConnectors.indexOf(this.initialConnector.name);
      if (activeIndex !== -1) {
        this.activeConnectors.splice(activeIndex, 1);
      }
      if (inactiveIndex !== -1) {
        this.inactiveConnectors.splice(inactiveIndex, 1);
      }
    }
    if (!this.activeConnectors.includes(value.name) && scope == AttributeScope.SHARED_SCOPE) {
      this.activeConnectors.push(value.name);
      updateActiveConnectors = true;
    }
    if (!this.inactiveConnectors.includes(value.name) && scope == AttributeScope.SERVER_SCOPE) {
      this.inactiveConnectors.push(value.name);
      updateActiveConnectors = true;
    }
    const tasks = [this.attributeService.saveEntityAttributes(this.device, scope, attributesToSave)];
    if (updateActiveConnectors) {
      tasks.push(this.attributeService.saveEntityAttributes(this.device, scope, [{
        key: scope == AttributeScope.SHARED_SCOPE ? 'active_connectors' : 'inactive_connectors',
        value: scope == AttributeScope.SHARED_SCOPE ? this.activeConnectors : this.inactiveConnectors
      }]));
    }

    if (attributesToDelete.length) {
      tasks.push(this.attributeService.deleteEntityAttributes(this.device, scope, attributesToDelete));
    }
    forkJoin(tasks).subscribe(_ => {
      this.initialConnector = value;
      this.showToast('Update Successful');
      this.updateData(true);
    });
  }

  updateData(reload: boolean = false) {
    this.pageLink.sortOrder.property = this.sort.active;
    this.pageLink.sortOrder.direction = Direction[this.sort.direction.toUpperCase()];
    this.attributeDataSource.loadAttributes(this.device, AttributeScope.CLIENT_SCOPE, this.pageLink, reload).subscribe(data => {
      this.activeData = data.data.filter(value => this.activeConnectors.includes(value.key));
      this.combineData();
      this.generateSubscription();
    });
    this.inactiveConnectorsDataSource.loadAttributes(this.device, AttributeScope.SHARED_SCOPE, this.pageLink, reload).subscribe(data => {
      this.sharedAttributeData = data.data.filter(value => this.activeConnectors.includes(value.key));
      this.combineData();
    });
    this.serverDataSource.loadAttributes(this.device, AttributeScope.SERVER_SCOPE, this.pageLink, reload).subscribe(data => {
      this.inactiveData = data.data.filter(value => this.inactiveConnectors.includes(value.key));
      this.combineData();
    });
  }

  isConnectorSynced(attribute: AttributeData) {
    const connectorData =  attribute.value;
    if (!connectorData.ts) return false;
    const clientIndex = this.activeData.findIndex(data => {
      const sharedData =  data.value;
      return sharedData.name === connectorData.name;
    })
    if (clientIndex == -1) return false;
    const sharedIndex = this.sharedAttributeData.findIndex(data => {
      const sharedData =  data.value;
      return sharedData.name === connectorData.name && sharedData.ts && sharedData.ts <= connectorData.ts;
    })
    return sharedIndex !== -1;
  }

  combineData() {
    this.dataSource.data = [...this.activeData, ...this.inactiveData, ...this.sharedAttributeData].filter((item, index, self) =>
      index === self.findIndex((t) => t.key === item.key)
    ).map(attribute=>{
      attribute.value = typeof attribute.value === 'string' ? JSON.parse(attribute.value) : attribute.value;
      return attribute
    });
  }

  addAttribute(): void {
    if (this.connectorForm.disabled) {
      this.connectorForm.enable();
    }
    this.nameInput.nativeElement.focus();
    this.clearOutConnectorForm();

  }

  clearOutConnectorForm(): void {
    this.connectorForm.setValue({
      name: '',
      type: 'mqtt',
      logLevel: GatewayLogLevel.info,
      key: 'auto',
      class: '',
      configuration: '',
      configurationJson: {}
    });
    this.initialConnector = null;
    this.connectorForm.markAsPristine();
  }

  selectConnector(attribute): void {
    if (this.connectorForm.disabled) {
      this.connectorForm.enable();
    }
    const connector = attribute.value;
    if (!connector.configuration) {
      connector.configuration = '';
    }
    if (!connector.key) {
      connector.key = 'auto';
    }
    this.initialConnector = connector;
    this.connectorForm.patchValue(connector);
    this.connectorForm.markAsPristine();
  }

  isSameConnector(attribute): boolean {
    if (!this.initialConnector) return false;
    const connector = attribute.value;
    return this.initialConnector.name === connector.name;
  }

  showToast(message: string) {
    this.store.dispatch(new ActionNotificationShow(
      {
        message,
        type: 'success',
        duration: 1000,
        verticalPosition: 'top',
        horizontalPosition: 'right',
        target: 'dashboardRoot',
        // panelClass: this.widgetNamespace,
        forceDismiss: true
      }));
  }

  returnType(attribute) {
    const value = attribute.value;
    return this.gatewayConnectorDefaultTypes.get(value.type);
  }

  deleteConnector(attribute: AttributeData, $event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    const title = `Delete connector ${attribute.key}?`;
    const content = `All connector data will be deleted.`;
    this.dialogService.confirm(title, content, 'Cancel', 'Delete').subscribe(result => {
      if (result) {
        const tasks = [];
        const scope = (this.initialConnector && this.activeConnectors.includes(this.initialConnector.name)) ? AttributeScope.SHARED_SCOPE : AttributeScope.SERVER_SCOPE;
        tasks.push(this.attributeService.deleteEntityAttributes(this.device, AttributeScope.SHARED_SCOPE, [attribute]));
        const activeIndex = this.activeConnectors.indexOf(attribute.key);
        const inactiveIndex = this.inactiveConnectors.indexOf(attribute.key);
        if (activeIndex !== -1) {
          this.activeConnectors.splice(activeIndex, 1);
        }
        if (inactiveIndex !== -1) {
          this.inactiveConnectors.splice(inactiveIndex, 1);
        }
        tasks.push(this.attributeService.saveEntityAttributes(this.device, scope, [{
          key: scope == AttributeScope.SHARED_SCOPE ? 'active_connectors' : 'inactive_connectors',
          value: scope == AttributeScope.SHARED_SCOPE ? this.activeConnectors : this.inactiveConnectors
        }]));
        forkJoin(tasks).subscribe(_ => {
          if (this.initialConnector ? this.initialConnector.name === attribute.key : true) {
            this.clearOutConnectorForm();
            this.cd.detectChanges();
            this.connectorForm.disable();
          }
          this.updateData(true);
        });
      }
    });
  }

  camelize(str): string {
    return str.toLowerCase().replace(/\s+/g, '_');
  }

  connectorLogs(attribute: AttributeData, $event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    const params = deepClone(this.ctx.stateController.getStateParams());
    params.connector_logs = attribute;
    params.targetEntityParamName = 'connector_logs';
    this.ctx.stateController.openState('connector_logs', params);
  }

  connectorRpc(attribute: AttributeData, $event: Event): void {
    if ($event) {
      $event.stopPropagation();
    }
    const params = deepClone(this.ctx.stateController.getStateParams());
    params.connector_rpc = attribute;
    params.targetEntityParamName = 'connector_rpc';
    this.ctx.stateController.openState('connector_rpc', params);
  }


  enableConnector(attribute): void {
    const wasEnabled = this.activeConnectors.includes(attribute.key);
    const scopeOld = wasEnabled ? AttributeScope.SHARED_SCOPE : AttributeScope.SERVER_SCOPE;
    const scopeNew = !wasEnabled ? AttributeScope.SHARED_SCOPE : AttributeScope.SERVER_SCOPE;
    attribute.value.ts = new Date().getTime();
    const tasks = [this.attributeService.saveEntityAttributes(this.device, AttributeScope.SHARED_SCOPE, [{
      key: 'active_connectors',
      value: this.activeConnectors
    }]), this.attributeService.saveEntityAttributes(this.device, AttributeScope.SERVER_SCOPE, [{
      key: 'inactive_connectors',
      value: this.inactiveConnectors
    }]), this.attributeService.deleteEntityAttributes(this.device, scopeOld, [attribute]),
      this.attributeService.saveEntityAttributes(this.device, scopeNew, [attribute])];
    if (wasEnabled) {
      const index = this.activeConnectors.indexOf(attribute.key);
      this.activeConnectors.splice(index, 1);
      this.inactiveConnectors.push(attribute.key);
    } else {
      const index = this.inactiveConnectors.indexOf(attribute.key);
      this.inactiveConnectors.splice(index, 1);
      this.activeConnectors.push(attribute.key);
    }
    forkJoin(tasks).subscribe(_ => {
      this.updateData(true);
    });
  }

  onDataUpdateError(e: any) {
    const exceptionData = this.utils.parseException(e);
    let errorText = exceptionData.name;
    if (exceptionData.message) {
      errorText += ': ' + exceptionData.message;
    }
    console.error(errorText);
  }

  onDataUpdated() {
    this.cd.detectChanges();
  }

  generateSubscription() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
    if (this.device) {
      const subscriptionInfo = [{
        type: DatasourceType.entity,
        entityType: EntityType.DEVICE,
        entityId: this.device.id,
        entityName: "Gateway",
        timeseries: []
      }];
      this.dataSource.data.forEach(value => {
        subscriptionInfo[0].timeseries.push({name: `${value.key}_ERRORS_COUNT`, label: `${value.key}_ERRORS_COUNT`})
      })
      this.ctx.subscriptionApi.createSubscriptionFromInfo(widgetType.latest, subscriptionInfo,this.subscriptionOptions, false, true).subscribe(subscription => {
        this.subscription = subscription;
      });
    }
  }

  getErrorsCount(attribute) {
    const connectorName = attribute.key;
    const connector = this.subscription && this.subscription.data.find(data=>data && data.dataKey.name === `${connectorName}_ERRORS_COUNT`);
    return (connector && this.activeConnectors.includes(connectorName))? connector.data[0][1]: 'Inactive';
  }
}
