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

import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import {
  ActionButtonLinkType,
  AlarmSeverityNotificationColors,
  Notification,
  NotificationStatus,
  NotificationType,
  NotificationTypeIcons
} from '@shared/models/notification.models';
import { UtilsService } from '@core/services/utils.service';
import { Router } from '@angular/router';
import { alarmSeverityTranslations } from '@shared/models/alarm.models';
import tinycolor from 'tinycolor2';
import { StateObject } from '@core/api/widget-api.models';
import { objToBase64URI } from '@core/utils';
import { coerceBoolean } from '@shared/decorators/coercion';

@Component({
  selector: 'tb-notification',
  templateUrl: './notification.component.html',
  styleUrls: ['./notification.component.scss']
})
export class NotificationComponent implements OnInit {

  @Input()
  notification: Notification;

  @Input()
  onClose: () => void;

  @Output()
  markAsRead = new EventEmitter<string>();

  @Input()
  @coerceBoolean()
  preview = false;

  showIcon = false;
  showButton = false;
  buttonLabel = '';
  hideMarkAsReadButton = false;

  notificationType = NotificationType;
  notificationTypeIcons = NotificationTypeIcons;
  alarmSeverityTranslations = alarmSeverityTranslations;

  currentDate = Date.now();

  title = '';
  message = '';

  constructor(
    private utils: UtilsService,
    private router: Router
  ) {
  }

  ngOnInit() {
    this.showIcon = this.notification.additionalConfig?.icon?.enabled;
    this.showButton = this.notification.additionalConfig?.actionButtonConfig?.enabled;
    this.hideMarkAsReadButton = this.notification.status === NotificationStatus.READ;
    this.title = this.utils.customTranslation(this.notification.subject, this.notification.subject);
    this.message = this.utils.customTranslation(this.notification.text, this.notification.text);
    if (this.showButton) {
      this.buttonLabel = this.utils.customTranslation(this.notification.additionalConfig.actionButtonConfig.text,
                                                      this.notification.additionalConfig.actionButtonConfig.text);
    }
  }

  markRead($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    if (!this.preview) {
      this.markAsRead.next(this.notification.id.id);
    }
  }

  navigate($event: Event) {
    if ($event) {
      $event.stopPropagation();
    }
    if (!this.preview) {
      let link: string;
      if (this.notification.additionalConfig.actionButtonConfig.linkType === ActionButtonLinkType.DASHBOARD) {
        let state = null;
        if (this.notification.additionalConfig.actionButtonConfig.dashboardState ||
          this.notification.additionalConfig.actionButtonConfig.setEntityIdInState) {
          const stateObject: StateObject = {};
          if (this.notification.additionalConfig.actionButtonConfig.setEntityIdInState) {
            stateObject.params = {
              entityId: this.notification.info?.stateEntityId ?? null
            };
          } else {
            stateObject.params = {};
          }
          if (this.notification.additionalConfig.actionButtonConfig.dashboardState) {
            stateObject.id = this.notification.additionalConfig.actionButtonConfig.dashboardState;
          }
          state = objToBase64URI([ stateObject ]);
        }
        link = `/dashboards/${this.notification.additionalConfig.actionButtonConfig.dashboardId}`;
        if (state) {
          link += `?state=${state}`;
        }
      } else {
        link = this.notification.additionalConfig.actionButtonConfig.link;
      }
      if (link.startsWith('/')) {
        this.router.navigateByUrl(this.router.parseUrl(link)).then(() => {
        });
      } else {
        window.open(link, '_blank');
      }
      if (this.onClose) {
        this.onClose();
      }
    }
  }

  alarmColorSeverity(alpha: number) {
    return tinycolor(AlarmSeverityNotificationColors.get(this.notification.info.alarmSeverity)).setAlpha(alpha).toRgbString();
  }

  notificationColor(): string {
    if (this.notification.type === NotificationType.ALARM && !this.notification.info.cleared) {
      return AlarmSeverityNotificationColors.get(this.notification.info.alarmSeverity);
    }
    return 'transparent';
  }

  notificationBackgroundColor(): string {
    if (this.notification.type === NotificationType.ALARM && !this.notification.info.cleared) {
      return '#fff';
    }
    return 'transparent';
  }

  notificationIconColor(): object {
    if (this.notification.type === NotificationType.ALARM) {
      return {color: AlarmSeverityNotificationColors.get(this.notification.info.alarmSeverity)};
    } else if (this.notification.type === NotificationType.RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT) {
      return {color: '#D12730'};
    }
    return null;
  }
}
