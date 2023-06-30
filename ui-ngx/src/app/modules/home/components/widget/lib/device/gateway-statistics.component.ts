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

import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { AttributeService } from '@core/http/attribute.service';
import { DeviceService } from '@core/http/device.service';
import { TranslateService } from '@ngx-translate/core';
import { AttributeData, AttributeScope } from '@shared/models/telemetry/telemetry.models';
import { PageComponent } from '@shared/components/page.component';
import { DialogService } from '@core/services/dialog.service';
import { WidgetContext } from '@home/models/widget-component.models';
import { TbFlot } from '@home/components/widget/lib/flot-widget';
import { ResizeObserver } from '@juggle/resize-observer';
import { IWidgetSubscription, SubscriptionInfo, WidgetSubscriptionOptions } from '@core/api/widget-api.models';
import { UtilsService } from '@core/services/utils.service';
import { DatasourceType, LegendConfig, LegendData, LegendPosition, widgetType } from '@shared/models/widget.models';
import { EntityType } from '@shared/models/entity-type.models';
import { EntityId } from '@shared/models/id/entity-id';
import { BaseData } from '@shared/models/base-data';
import { PageLink } from '@shared/models/page/page-link';
import { Direction, SortOrder } from '@shared/models/page/sort-order';
import { MatTableDataSource } from '@angular/material/table';
import { MatSort } from '@angular/material/sort';
import { NULL_UUID } from '@shared/models/id/has-uuid';


@Component({
  selector: 'tb-gateway-statistics',
  templateUrl: './gateway-statistics.component.html',
  styleUrls: ['./gateway-statistics.component.scss']
})
export class GatewayStatisticsComponent extends PageComponent implements AfterViewInit {

  @ViewChild(MatSort) sort: MatSort;
  @ViewChild('statisticChart') statisticChart: ElementRef;

  @Input()
  ctx: WidgetContext;

  @Input()
  public general: boolean;

  public isNumericData: boolean = false;
  public chartInited: boolean;
  private flot: TbFlot;
  private flotCtx;
  public statisticForm: FormGroup;
  public statisticsKeys = [];
  public commands = [];
  public commandObj: any;
  public dataSource: MatTableDataSource<any>;
  public pageLink: PageLink;
  private resize$: ResizeObserver;
  private subscription: IWidgetSubscription;
  private subscriptionInfo: SubscriptionInfo [];
  public legendData: LegendData;
  public displayedColumns: Array<string>;
  private subscriptionOptions: WidgetSubscriptionOptions = {
    callbacks: {
      onDataUpdated: () => this.ctx.ngZone.run(() => {
        this.onDataUpdated();
      }),
      onDataUpdateError: (subscription, e) => this.ctx.ngZone.run(() => {
        this.onDataUpdateError(e);
      })
    },
    useDashboardTimewindow: false,
    legendConfig: {
      position: LegendPosition.bottom
    } as LegendConfig
  };


  constructor(protected router: Router,
              protected store: Store<AppState>,
              protected fb: FormBuilder,
              protected translate: TranslateService,
              protected attributeService: AttributeService,
              protected deviceService: DeviceService,
              protected dialogService: DialogService,
              private cd: ChangeDetectorRef,
              private utils: UtilsService,
              public dialog: MatDialog) {
    super(store);
    const sortOrder: SortOrder = {property: '0', direction: Direction.DESC};
    this.pageLink = new PageLink(Number.POSITIVE_INFINITY, 0, null, sortOrder);
    this.displayedColumns = ['0', '1'];
    this.dataSource = new MatTableDataSource<any>([]);
    this.statisticForm = this.fb.group({
      statisticKey: [null, []]
    })

    this.statisticForm.get('statisticKey').valueChanges.subscribe(value => {
      this.commandObj = null;
      if (this.commands.length) {
        this.commandObj = this.commands.find(command => command.attributeOnGateway === value);
      }
      if (this.subscriptionInfo) this.createChartsSubscription(this.ctx.defaultSubscription.datasources[0].entity, value);
    })
  }


  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
    this.sort.sortChange.subscribe(_=>{
      this.sortData();
    })
    this.init();
    if (this.ctx.defaultSubscription.datasources.length) {
      const gateway = this.ctx.defaultSubscription.datasources[0].entity;
      if (gateway.id.id === NULL_UUID) return;
      if (!this.general) {
        this.attributeService.getEntityAttributes(gateway.id, AttributeScope.SHARED_SCOPE, ["general_configuration"]).subscribe((resp: AttributeData[]) => {
          if (resp && resp.length) {
            this.commands = resp[0].value.statistics.commands;
            if (!this.statisticForm.get('statisticKey').value && this.commands && this.commands.length) {
              this.statisticForm.get('statisticKey').setValue(this.commands[0].attributeOnGateway);
              this.createChartsSubscription(gateway, this.commands[0].attributeOnGateway);
            }
          }
        })
      } else {
        let connectorsTs;
        this.attributeService.getEntityTimeseriesLatest(gateway.id).subscribe(
          data => {
            connectorsTs = Object.keys(data)
              .filter(el => el.includes(
                'ConnectorEventsProduced'
              ) || el.includes(
                'ConnectorEventsSent'))
            this.createGeneralChartsSubscription(gateway, connectorsTs);
          })
      }
    }
  }

  public sortData () {
    this.dataSource.sortData(this.dataSource.data, this.sort);
  }

  public onLegendKeyHiddenChange(index: number) {
    this.legendData.keys[index].dataKey.hidden = !this.legendData.keys[index].dataKey.hidden;
    this.subscription.updateDataVisibility(index);
  }

  private createChartsSubscription(gateway: BaseData<EntityId>, attr: string) {
    let subscriptionInfo = [{
      type: DatasourceType.entity,
      entityType: EntityType.DEVICE,
      entityId: gateway.id.id,
      entityName: gateway.name,
      timeseries: []
    }];

    subscriptionInfo[0].timeseries = [{name: attr, label: attr}];
    this.subscriptionInfo = subscriptionInfo;
    this.changeSubscription(subscriptionInfo);
  }

  private createGeneralChartsSubscription(gateway: BaseData<EntityId>, attrData: [string]) {
    let subscriptionInfo = [{
      type: DatasourceType.entity,
      entityType: EntityType.DEVICE,
      entityId: gateway.id.id,
      entityName: gateway.name,
      timeseries: []
    }];
    subscriptionInfo[0].timeseries = [];
    if (attrData && attrData.length) {
      attrData.forEach(attr => {
        subscriptionInfo[0].timeseries.push({name: attr, label: attr})
      })
    }
    this.ctx.defaultSubscription.datasources[0].dataKeys.forEach(dataKey => {
      subscriptionInfo[0].timeseries.push({name: dataKey.name, label: dataKey.label})
    })

    this.subscriptionInfo = subscriptionInfo;
    this.changeSubscription(subscriptionInfo);
  }

  init = () => {
    this.flotCtx = {
      $scope: this.ctx.$scope,
      $injector: this.ctx.$injector,
      utils: this.ctx.utils,
      isMobile: this.ctx.isMobile,
      isEdit: this.ctx.isEdit,
      subscriptionApi: this.ctx.subscriptionApi,
      detectChanges: this.ctx.detectChanges,
      settings: this.ctx.settings
    };
  }

  updateChart = () => {
    if (this.flot && this.ctx.defaultSubscription.data.length) {
      this.flot.update();
    }
  }

  resize = () => {
    if (this.flot) {
      this.flot.resize();
    }
  }

  private reset() {
    if (this.resize$) {
      this.resize$.disconnect();
    }
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
    if (this.flot) {
      this.flot.destroy();
    }
  }

  private onDataUpdateError(e: any) {
    const exceptionData = this.utils.parseException(e);
    let errorText = exceptionData.name;
    if (exceptionData.message) {
      errorText += ': ' + exceptionData.message;
    }
    console.error(errorText);
  }

  private onDataUpdated() {
    this.isDataOnlyNumbers();
    if (this.isNumericData) {
      if (this.chartInited) {
        if (this.flot) {
          this.flot.update();
        }
      } else {
        this.initChart();
      }
    }
  }

  private initChart() {
    this.chartInited = true;
    this.flotCtx.$container = $(this.statisticChart.nativeElement);
    this.resize$.observe(this.statisticChart.nativeElement);
    this.flot = new TbFlot(this.flotCtx as WidgetContext, "line");
    this.flot.update();
  }

  private isDataOnlyNumbers() {
    if (this.general) {
      this.isNumericData = true;
      return;
    }
    this.dataSource.data = this.subscription.data.length ? this.subscription.data[0].data : [];
    this.isNumericData = this.dataSource.data.every(data => !isNaN(data[1]));
  }


  changeSubscription(subscriptionInfo: SubscriptionInfo[]) {
    if (this.subscription) {
      this.reset();
    }
    if (this.ctx.datasources[0].entity) {
      this.ctx.subscriptionApi.createSubscriptionFromInfo(widgetType.timeseries, subscriptionInfo, this.subscriptionOptions, false, true).subscribe(subscription => {
        this.subscription = subscription;
        this.isDataOnlyNumbers();
        this.legendData = this.subscription.legendData;
        this.flotCtx.defaultSubscription = subscription;
        this.resize$ = new ResizeObserver(() => {
          this.resize();
        });
        this.ctx.detectChanges();
        if (this.isNumericData) {
          this.initChart();
        }
      })

    }
  }

}
