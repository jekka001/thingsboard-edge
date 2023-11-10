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

import { Component, Inject, NgZone, OnDestroy, OnInit } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { DeviceService } from '@core/http/device.service';
import {
  AttributeData,
  AttributeScope,
  AttributesSubscriptionCmd,
  LatestTelemetry,
  TelemetrySubscriber
} from '@shared/models/telemetry/telemetry.models';
import { TelemetryWebsocketService } from '@core/ws/telemetry-websocket.service';
import { EntityId } from '@shared/models/id/entity-id';
import { EntityType } from '@shared/models/entity-type.models';
import { selectPersistDeviceStateToTelemetry } from '@core/auth/auth.selectors';
import { take } from 'rxjs/operators';
import {
  BasicTransportType,
  DeviceTransportType,
  deviceTransportTypeTranslationMap,
  NetworkTransportType,
  PublishTelemetryCommand
} from '@shared/models/device.models';
import { ActionPreferencesPutUserSettings } from '@core/auth/auth.actions';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { getOS } from '@core/utils';
import { WhiteLabelingService } from '@core/http/white-labeling.service';

export interface DeviceCheckConnectivityDialogData {
  deviceId: EntityId;
  afterAdd: boolean;
}

@Component({
  selector: 'tb-device-check-connectivity-dialog',
  templateUrl: './device-check-connectivity-dialog.component.html',
  styleUrls: ['./device-check-connectivity-dialog.component.scss']
})
export class DeviceCheckConnectivityDialogComponent extends
  DialogComponent<DeviceCheckConnectivityDialogComponent> implements OnInit, OnDestroy {

  loadedCommand = false;

  status: boolean;

  latestTelemetry: Array<AttributeData> = [];

  commands: PublishTelemetryCommand;

  allowTransportType = new Set<NetworkTransportType>();
  selectTransportType: NetworkTransportType;

  BasicTransportType = BasicTransportType;
  DeviceTransportType = DeviceTransportType;
  deviceTransportTypeTranslationMap = deviceTransportTypeTranslationMap;

  showDontShowAgain: boolean;
  dialogTitle: string;

  notShowAgain = false;

  helpBaseUrl = this.wl.getHelpLinkBaseUrl();

  httpTabIndex = 0;
  mqttTabIndex = 0;
  coapTabIndex = 0;

  private telemetrySubscriber: TelemetrySubscriber;

  private currentTime = Date.now();

  private transportTypes = [...Object.keys(BasicTransportType), ...Object.keys(DeviceTransportType)] as Array<NetworkTransportType>;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) private data: DeviceCheckConnectivityDialogData,
              public dialogRef: MatDialogRef<DeviceCheckConnectivityDialogComponent>,
              private deviceService: DeviceService,
              private telemetryWsService: TelemetryWebsocketService,
              private wl: WhiteLabelingService,
              private zone: NgZone) {
    super(store, router, dialogRef);

    if (this.data.afterAdd) {
      this.dialogTitle = 'device.connectivity.device-created-check-connectivity';
      this.showDontShowAgain = true;
    } else {
      this.dialogTitle = 'device.connectivity.check-connectivity';
      this.showDontShowAgain = false;
    }
  }

  ngOnInit() {
    this.loadCommands();
    this.subscribeToLatestTelemetry();
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.telemetrySubscriber?.complete();
    this.telemetrySubscriber?.unsubscribe();
  }

  close(): void {
    if (this.notShowAgain && this.showDontShowAgain) {
      this.store.dispatch(new ActionPreferencesPutUserSettings({ notDisplayConnectivityAfterAddDevice: true }));
      this.dialogRef.close(null);
    } else {
      this.dialogRef.close(null);
    }
  }

  createMarkDownCommand(commands: string | string[]): string {
    if (Array.isArray(commands)) {
      const formatCommands: Array<string> = [];
      commands.forEach(command => formatCommands.push(this.createMarkDownSingleCommand(command)));
      return formatCommands.join(`\n<br />\n\n`);
    } else {
      return this.createMarkDownSingleCommand(commands);
    }
  }

  private createMarkDownSingleCommand(command: string): string {
    return '```bash\n' +
      command +
      '{:copy-code}\n' +
      '```';
  }

  private loadCommands() {
    this.deviceService.getDevicePublishTelemetryCommands(this.data.deviceId.id).subscribe(
      commands => {
        this.commands = commands;
        const commandsProtocols = Object.keys(commands);
        this.transportTypes.forEach(transport => {
          const findCommand = commandsProtocols.find(item => item.toUpperCase().startsWith(transport));
          if (findCommand) {
            this.allowTransportType.add(transport);
          }
        });
        this.selectTransportType = this.allowTransportType.values().next().value;
        this.selectTabIndexForUserOS();
        this.loadedCommand = true;
      }
    );
  }

  private subscribeToLatestTelemetry() {
    this.store.pipe(select(selectPersistDeviceStateToTelemetry)).pipe(
      take(1)
    ).subscribe(persistToTelemetry => {
      this.telemetrySubscriber = TelemetrySubscriber.createEntityAttributesSubscription(
        this.telemetryWsService, this.data.deviceId, LatestTelemetry.LATEST_TELEMETRY, this.zone);
      if (!persistToTelemetry) {
        const subscriptionCommand = new AttributesSubscriptionCmd();
        subscriptionCommand.entityType = this.data.deviceId.entityType as EntityType;
        subscriptionCommand.entityId = this.data.deviceId.id;
        subscriptionCommand.scope = AttributeScope.SERVER_SCOPE;
        subscriptionCommand.keys = 'active';
        this.telemetrySubscriber.subscriptionCommands.push(subscriptionCommand);
      }

      this.telemetrySubscriber.subscribe();
      this.telemetrySubscriber.attributeData$().subscribe(
        (data) => {
          const telemetry = data.reduce<Array<AttributeData>>((accumulator, item) => {
            if (item.key === 'active') {
              this.status = coerceBooleanProperty(item.value);
            } else if (item.lastUpdateTs > this.currentTime) {
              accumulator.push(item);
            }
            return accumulator;
          }, []);
          this.latestTelemetry = telemetry.sort((a, b) => b.lastUpdateTs - a.lastUpdateTs);
        }
      );
    });
  }

  private selectTabIndexForUserOS() {
    const currentOS = getOS();
    switch (currentOS) {
      case 'linux':
      case 'android':
        this.httpTabIndex = 2;
        this.mqttTabIndex = 2;
        break;
      case 'macos':
      case 'ios':
        this.httpTabIndex = 1;
        this.mqttTabIndex = 1;
        this.coapTabIndex = 1;
        break;
      case 'windows':
        this.httpTabIndex = 0;
        this.mqttTabIndex = 0;
        this.coapTabIndex = 1;
        break;
      default:
        this.mqttTabIndex = this.commands.mqtt?.docker ? 3 : 0;
        this.coapTabIndex = this.commands.coap?.docker ? 1 : 0;
    }
  }

}
