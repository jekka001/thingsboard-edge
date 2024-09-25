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

import { Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { BasicWidgetConfigComponent } from '@home/components/widget/config/widget-config.component.models';
import { WidgetConfigComponentData } from '@home/models/widget-component.models';
import { WidgetConfig } from '@shared/models/widget.models';
import { WidgetConfigComponent } from '@home/components/widget/widget-config.component';
import { isUndefined } from '@core/utils';
import { cssSizeToStrSize, resolveCssSize } from '@shared/models/widget-settings.models';
import {
  mobileAppQrCodeWidgetDefaultSettings,
  MobileAppQrCodeWidgetSettings
} from '@home/components/widget/lib/cards/mobile-app-qr-code-widget.models';
import { badgePositionTranslationsMap } from '@app/shared/models/mobile-app.models';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { Operation, Resource } from '@shared/models/security.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';

@Component({
  selector: 'tb-mobile-app-qr-code-basic-config',
  templateUrl: './mobile-app-qr-code-basic-config.component.html',
  styleUrls: ['../basic-config.scss']
})
export class MobileAppQrCodeBasicConfigComponent extends BasicWidgetConfigComponent {

  mobileAppQrCodeWidgetConfigForm: UntypedFormGroup;
  badgePositionTranslationsMap = badgePositionTranslationsMap;
  displayConfigurationHint = false;

  constructor(protected store: Store<AppState>,
              protected widgetConfigComponent: WidgetConfigComponent,
              private fb: UntypedFormBuilder,
              private userPermissionsService: UserPermissionsService) {
    super(store, widgetConfigComponent);
    this.displayConfigurationHint = getCurrentAuthUser(this.store).authority !== Authority.CUSTOMER_USER &&
      (this.userPermissionsService.hasGenericPermission(Resource.MOBILE_APP_SETTINGS, Operation.WRITE) ||
        this.userPermissionsService.hasGenericPermission(Resource.MOBILE_APP_SETTINGS, Operation.READ));
  }

  protected configForm(): UntypedFormGroup {
    return this.mobileAppQrCodeWidgetConfigForm;
  }

  protected setupConfig(widgetConfig: WidgetConfigComponentData) {
    super.setupConfig(widgetConfig);
  }

  protected onConfigSet(configData: WidgetConfigComponentData) {
    const settings: MobileAppQrCodeWidgetSettings = {...mobileAppQrCodeWidgetDefaultSettings, ...(configData.config.settings || {})};
    const iconSize = resolveCssSize(configData.config.iconSize);

    this.mobileAppQrCodeWidgetConfigForm = this.fb.group({
      useSystemSettings: [settings.useSystemSettings],

      badgeEnabled: [settings.qrCodeConfig.badgeEnabled],
      badgePosition: [settings.qrCodeConfig.badgePosition],
      qrCodeLabelEnabled: [settings.qrCodeConfig.qrCodeLabelEnabled],
      qrCodeLabel: [settings.qrCodeConfig.qrCodeLabel],

      showTitle: [configData.config.showTitle, []],
      title: [configData.config.title, []],
      titleFont: [configData.config.titleFont, []],
      titleColor: [configData.config.titleColor, []],
      showTitleIcon: [configData.config.showTitleIcon, []],
      iconSize: [iconSize[0], [Validators.min(0)]],
      iconSizeUnit: [iconSize[1], []],
      titleIcon: [configData.config.titleIcon, []],
      iconColor: [configData.config.iconColor, []],

      background: [settings.background, []],

      cardButtons: [this.getCardButtons(configData.config), []],
      borderRadius: [configData.config.borderRadius, []],
      padding: [settings.padding, []],

      actions: [configData.config.actions || {}, []]
    });
  }

  protected prepareOutputConfig(config: any): WidgetConfigComponentData {
    this.widgetConfig.config.showTitle = config.showTitle;
    this.widgetConfig.config.title = config.title;
    this.widgetConfig.config.titleFont = config.titleFont;
    this.widgetConfig.config.titleColor = config.titleColor;

    this.widgetConfig.config.showTitleIcon = config.showTitleIcon;
    this.widgetConfig.config.iconSize = cssSizeToStrSize(config.iconSize, config.iconSizeUnit);
    this.widgetConfig.config.titleIcon = config.titleIcon;
    this.widgetConfig.config.iconColor = config.iconColor;

    this.widgetConfig.config.settings = this.widgetConfig.config.settings || {};
    this.widgetConfig.config.settings.useSystemSettings = config.useSystemSettings;
    this.widgetConfig.config.settings.qrCodeConfig = {
      badgeEnabled: config.badgeEnabled,
      badgePosition: config.badgePosition,
      qrCodeLabelEnabled: config.qrCodeLabelEnabled,
      qrCodeLabel: config.qrCodeLabel
    };

    this.widgetConfig.config.settings.background = config.background;
    this.setCardButtons(config.cardButtons, this.widgetConfig.config);
    this.widgetConfig.config.borderRadius = config.borderRadius;
    this.widgetConfig.config.settings.padding = config.padding;

    this.widgetConfig.config.actions = config.actions;
    return this.widgetConfig;
  }

  protected validatorTriggers(): string[] {
    return ['showTitleIcon', 'badgeEnabled', 'qrCodeLabelEnabled', 'showTitleIcon', 'showTitle'];
  }

  protected updateValidators(emitEvent: boolean, trigger?: string) {
    const useSystemSettings = this.mobileAppQrCodeWidgetConfigForm.get('useSystemSettings').value;

    if (!useSystemSettings) {
      const badgeEnabled = this.mobileAppQrCodeWidgetConfigForm.get('badgeEnabled').value;
      const qrCodeLabelEnabled = this.mobileAppQrCodeWidgetConfigForm.get('qrCodeLabelEnabled').value;
      const showTitleIcon: boolean = this.mobileAppQrCodeWidgetConfigForm.get('showTitleIcon').value;
      const showTitle: boolean = this.mobileAppQrCodeWidgetConfigForm.get('showTitle').value;

      if (badgeEnabled) {
        this.mobileAppQrCodeWidgetConfigForm.get('badgePosition').enable({emitEvent: false});
      } else {
        this.mobileAppQrCodeWidgetConfigForm.get('badgePosition').disable({emitEvent: false});
      }
      if (qrCodeLabelEnabled) {
        this.mobileAppQrCodeWidgetConfigForm.get('qrCodeLabel').enable({emitEvent: false});
      } else {
        this.mobileAppQrCodeWidgetConfigForm.get('qrCodeLabel').disable({emitEvent: false});
      }
      if (showTitle) {
        this.mobileAppQrCodeWidgetConfigForm.get('title').enable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('titleFont').enable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('titleColor').enable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('showTitleIcon').enable({emitEvent: false});
        if (showTitleIcon) {
          this.mobileAppQrCodeWidgetConfigForm.get('titleIcon').enable({emitEvent: false});
          this.mobileAppQrCodeWidgetConfigForm.get('iconColor').enable({emitEvent: false});
          this.mobileAppQrCodeWidgetConfigForm.get('iconSize').enable({emitEvent: false});
          this.mobileAppQrCodeWidgetConfigForm.get('iconSizeUnit').enable({emitEvent: false});
        } else {
          this.mobileAppQrCodeWidgetConfigForm.get('titleIcon').disable({emitEvent: false});
          this.mobileAppQrCodeWidgetConfigForm.get('iconColor').disable({emitEvent: false});
          this.mobileAppQrCodeWidgetConfigForm.get('iconSize').disable({emitEvent: false});
          this.mobileAppQrCodeWidgetConfigForm.get('iconSizeUnit').disable({emitEvent: false});
        }
      } else {
        this.mobileAppQrCodeWidgetConfigForm.get('title').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('titleFont').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('titleColor').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('showTitleIcon').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('titleIcon').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('iconColor').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('iconSize').disable({emitEvent: false});
        this.mobileAppQrCodeWidgetConfigForm.get('iconSizeUnit').disable({emitEvent: false});
      }
    }
  }

  private getCardButtons(config: WidgetConfig): string[] {
    const buttons: string[] = [];
    if (isUndefined(config.enableFullscreen) || config.enableFullscreen) {
      buttons.push('fullscreen');
    }
    return buttons;
  }

  private setCardButtons(buttons: string[], config: WidgetConfig) {
    config.enableFullscreen = buttons.includes('fullscreen');
  }

  navigateToMobileAppSettings($event) {
    if ($event) {
      $event.stopPropagation();
    }
    window.open(window.location.origin + '/settings/mobile-app', '_blank');
  }

}
