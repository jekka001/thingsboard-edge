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

import { Component, Injector } from '@angular/core';
import { BasicWidgetConfigComponent } from '@home/components/widget/config/widget-config.component.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { WidgetConfigComponentData } from '@home/models/widget-component.models';
import { isDefinedAndNotNull, isUndefined, mergeDeep, mergeDeepIgnoreArray } from '@core/utils';
import { mapWidgetDefaultSettings, MapWidgetSettings } from '@home/components/widget/lib/maps/map-widget.models';
import { cssSizeToStrSize, resolveCssSize } from '@shared/models/widget-settings.models';
import { WidgetConfig } from '@shared/models/widget.models';
import {
  getTimewindowConfig,
  setTimewindowConfig
} from '@home/components/widget/config/timewindow-config-panel.component';

@Component({
  selector: 'tb-map-basic-config',
  templateUrl: './map-basic-config.component.html',
  styleUrls: ['../basic-config.scss']
})
export class MapBasicConfigComponent extends BasicWidgetConfigComponent {

  mapWidgetConfigForm: UntypedFormGroup;

  trip = false;

  constructor(protected store: Store<AppState>,
              protected widgetConfigComponent: WidgetConfigComponent,
              private $injector: Injector,
              private fb: UntypedFormBuilder) {
    super(store, widgetConfigComponent);
  }

  protected configForm(): UntypedFormGroup {
    return this.mapWidgetConfigForm;
  }

  protected setupConfig(widgetConfig: WidgetConfigComponentData) {
    const params = widgetConfig.typeParameters as any;
    if (isDefinedAndNotNull(params.trip)) {
      this.trip = params.trip === true;
    }
    super.setupConfig(widgetConfig);
  }

  protected setupDefaults(configData: WidgetConfigComponentData) {
    const settings = configData.config.settings as MapWidgetSettings;
    if (settings?.markers?.length) {
      settings.markers = [];
    }
    if (settings?.polygons?.length) {
      settings.polygons = [];
    }
    if (settings?.circles?.length) {
      settings.circles = [];
    }
    if (this.trip) {
      if (settings?.trips?.length) {
        settings.trips = [];
      }
    }
  }

  protected onConfigSet(configData: WidgetConfigComponentData) {
    const settings: MapWidgetSettings = mergeDeepIgnoreArray<MapWidgetSettings>({} as MapWidgetSettings,
      mapWidgetDefaultSettings, configData.config.settings as MapWidgetSettings);
    const iconSize = resolveCssSize(configData.config.iconSize);
    this.mapWidgetConfigForm = this.fb.group({
      mapSettings: [settings, []],

      showTitle: [configData.config.showTitle, []],
      title: [configData.config.title, []],
      titleFont: [configData.config.titleFont, []],
      titleColor: [configData.config.titleColor, []],

      showIcon: [configData.config.showTitleIcon, []],
      iconSize: [iconSize[0], [Validators.min(0)]],
      iconSizeUnit: [iconSize[1], []],
      icon: [configData.config.titleIcon, []],
      iconColor: [configData.config.iconColor, []],

      background: [settings.background, []],

      cardButtons: [this.getCardButtons(configData.config), []],
      borderRadius: [configData.config.borderRadius, []],
      padding: [settings.padding, []],

      actions: [configData.config.actions || {}, []]
    });
    if (this.trip) {
      this.mapWidgetConfigForm.addControl('timewindowConfig', this.fb.control(getTimewindowConfig(configData.config)))
    }
  }

  protected prepareOutputConfig(config: any): WidgetConfigComponentData {
    if (this.trip) {
      setTimewindowConfig(this.widgetConfig.config, config.timewindowConfig);
    }
    this.widgetConfig.config.settings = config.mapSettings || {};

    this.widgetConfig.config.showTitle = config.showTitle;
    this.widgetConfig.config.title = config.title;
    this.widgetConfig.config.titleFont = config.titleFont;
    this.widgetConfig.config.titleColor = config.titleColor;

    this.widgetConfig.config.showTitleIcon = config.showIcon;
    this.widgetConfig.config.iconSize = cssSizeToStrSize(config.iconSize, config.iconSizeUnit);
    this.widgetConfig.config.titleIcon = config.icon;
    this.widgetConfig.config.iconColor = config.iconColor;

    this.widgetConfig.config.settings.background = config.background;

    this.setCardButtons(config.cardButtons, this.widgetConfig.config);
    this.widgetConfig.config.borderRadius = config.borderRadius;
    this.widgetConfig.config.settings.padding = config.padding;

    this.widgetConfig.config.actions = config.actions;

    return this.widgetConfig;
  }

  protected validatorTriggers(): string[] {
    return ['showTitle', 'showIcon'];
  }

  protected updateValidators(emitEvent: boolean, trigger?: string) {
    const showTitle: boolean = this.mapWidgetConfigForm.get('showTitle').value;
    const showIcon: boolean = this.mapWidgetConfigForm.get('showIcon').value;

    if (showTitle) {
      this.mapWidgetConfigForm.get('title').enable();
      this.mapWidgetConfigForm.get('titleFont').enable();
      this.mapWidgetConfigForm.get('titleColor').enable();
      this.mapWidgetConfigForm.get('showIcon').enable({emitEvent: false});
      if (showIcon) {
        this.mapWidgetConfigForm.get('iconSize').enable();
        this.mapWidgetConfigForm.get('iconSizeUnit').enable();
        this.mapWidgetConfigForm.get('icon').enable();
        this.mapWidgetConfigForm.get('iconColor').enable();
      } else {
        this.mapWidgetConfigForm.get('iconSize').disable();
        this.mapWidgetConfigForm.get('iconSizeUnit').disable();
        this.mapWidgetConfigForm.get('icon').disable();
        this.mapWidgetConfigForm.get('iconColor').disable();
      }
    } else {
      this.mapWidgetConfigForm.get('title').disable();
      this.mapWidgetConfigForm.get('titleFont').disable();
      this.mapWidgetConfigForm.get('titleColor').disable();
      this.mapWidgetConfigForm.get('showIcon').disable({emitEvent: false});
      this.mapWidgetConfigForm.get('iconSize').disable();
      this.mapWidgetConfigForm.get('iconSizeUnit').disable();
      this.mapWidgetConfigForm.get('icon').disable();
      this.mapWidgetConfigForm.get('iconColor').disable();
    }
  }

  private getCardButtons(config: WidgetConfig): string[] {
    const buttons: string[] = [];
    if (isUndefined(config.enableDataExport) || config.enableDataExport) {
      buttons.push('dataExport');
    }
    if (isUndefined(config.enableFullscreen) || config.enableFullscreen) {
      buttons.push('fullscreen');
    }
    return buttons;
  }

  private setCardButtons(buttons: string[], config: WidgetConfig) {
    config.enableDataExport = buttons.includes('dataExport');
    config.enableFullscreen = buttons.includes('fullscreen');
  }

}
