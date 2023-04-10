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

import {
  AlarmAction,
  AlarmActionTranslationMap,
  AlarmAssignmentAction,
  AlarmAssignmentActionTranslationMap,
  ComponentLifecycleEvent,
  ComponentLifecycleEventTranslationMap,
  DeviceEvent,
  DeviceEventTranslationMap,
  NotificationRule,
  NotificationTarget,
  TriggerType,
  TriggerTypeTranslationMap
} from '@shared/models/notification.models';
import { Component, Inject, OnDestroy, ViewChild } from '@angular/core';
import { DialogComponent } from '@shared/components/dialog.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { Router } from '@angular/router';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { NotificationService } from '@core/http/notification.service';
import { EntityType } from '@shared/models/entity-type.models';
import { deepClone, deepTrim, isDefined } from '@core/utils';
import { Observable, Subject } from 'rxjs';
import { map, takeUntil } from 'rxjs/operators';
import { StepperOrientation, StepperSelectionEvent } from '@angular/cdk/stepper';
import { MatStepper } from '@angular/material/stepper';
import { MediaBreakpoints } from '@shared/models/constants';
import { BreakpointObserver } from '@angular/cdk/layout';
import {
  AlarmSearchStatus,
  alarmSearchStatusTranslations,
  AlarmSeverity,
  alarmSeverityTranslations
} from '@shared/models/alarm.models';
import { TranslateService } from '@ngx-translate/core';
import {
  RecipientNotificationDialogComponent,
  RecipientNotificationDialogData
} from '@home/pages/notification/recipient/recipient-notification-dialog.component';
import { MatButton } from '@angular/material/button';
import { AuthState } from '@core/auth/auth.models';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { AuthUser } from '@shared/models/user.model';
import { Authority } from '@shared/models/authority.enum';
import {
  ApiFeature,
  ApiFeatureTranslationMap,
  ApiUsageStateValue,
  ApiUsageStateValueTranslationMap
} from '@shared/models/api-usage.models';
import { IntegrationType, integrationTypeInfoMap } from '@shared/models/integration.models';

export interface RuleNotificationDialogData {
  rule?: NotificationRule;
  isAdd?: boolean;
  isCopy?: boolean;
  readonly?: boolean;
}

@Component({
  selector: 'tb-rule-notification-dialog',
  templateUrl: './rule-notification-dialog.component.html',
  styleUrls: ['rule-notification-dialog.component.scss']
})
export class RuleNotificationDialogComponent extends
  DialogComponent<RuleNotificationDialogComponent, NotificationRule> implements OnDestroy {

  @ViewChild('addNotificationRule', {static: true}) addNotificationRule: MatStepper;

  stepperOrientation: Observable<StepperOrientation>;

  ruleNotificationForm: FormGroup;
  alarmTemplateForm: FormGroup;
  deviceInactivityTemplateForm: FormGroup;
  entityActionTemplateForm: FormGroup;
  alarmCommentTemplateForm: FormGroup;
  alarmAssignmentTemplateForm: FormGroup;
  ruleEngineEventsTemplateForm: FormGroup;
  entitiesLimitTemplateForm: FormGroup;
  apiUsageLimitTemplateForm: FormGroup;
  integrationEventsTemplateForm: FormGroup;
  newPlatformVersionTemplateForm: FormGroup;

  triggerType = TriggerType;
  triggerTypes: TriggerType[];
  triggerTypeTranslationMap = TriggerTypeTranslationMap;

  alarmSearchStatuses = [
    AlarmSearchStatus.ACTIVE,
    AlarmSearchStatus.CLEARED,
    AlarmSearchStatus.ACK,
    AlarmSearchStatus.UNACK
  ];
  alarmSearchStatusTranslationMap = alarmSearchStatusTranslations;

  alarmSeverityTranslationMap = alarmSeverityTranslations;
  alarmSeverities = Object.keys(AlarmSeverity) as Array<AlarmSeverity>;

  alarmActions: AlarmAction[] = Object.values(AlarmAction);
  alarmActionTranslationMap = AlarmActionTranslationMap;

  alarmAssignmentActions: AlarmAssignmentAction[] = Object.values(AlarmAssignmentAction);
  alarmAssignmentActionTranslationMap = AlarmAssignmentActionTranslationMap;

  componentLifecycleEvents: ComponentLifecycleEvent[] = Object.values(ComponentLifecycleEvent);
  componentLifecycleEventTranslationMap = ComponentLifecycleEventTranslationMap;

  deviceEvents: DeviceEvent[] = Object.values(DeviceEvent);
  deviceEventTranslationMap = DeviceEventTranslationMap;

  apiUsageStateValues: ApiUsageStateValue[] = Object.values(ApiUsageStateValue);
  apiUsageStateValueTranslationMap = ApiUsageStateValueTranslationMap;

  apiFeatures: ApiFeature[] = Object.values(ApiFeature);
  apiFeatureTranslationMap = ApiFeatureTranslationMap;

  integrationTypes: IntegrationType[] = Object.values(IntegrationType);
  integrationTypeInfoMap = integrationTypeInfoMap;

  entityType = EntityType;
  isAdd = true;

  allowEntityTypeForEntitiesLimit = [
    EntityType.DEVICE,
    EntityType.ASSET,
    EntityType.CUSTOMER,
    EntityType.USER,
    EntityType.DASHBOARD,
    EntityType.RULE_CHAIN,
    EntityType.INTEGRATION,
    EntityType.CONVERTER,
    EntityType.SCHEDULER_EVENT
  ];

  selectedIndex = 0;

  dialogTitle = 'notification.edit-rule';

  private destroy$ = new Subject<void>();

  private readonly ruleNotification: NotificationRule;

  private triggerTypeFormsMap: Map<TriggerType, FormGroup>;
  private authState: AuthState = getCurrentAuthState(this.store);
  private authUser: AuthUser = this.authState.authUser;
  private _allowEntityTypeForEntityAction: EntityType[];

  constructor(protected store: Store<AppState>,
              protected router: Router,
              protected dialogRef: MatDialogRef<RuleNotificationDialogComponent, NotificationRule>,
              @Inject(MAT_DIALOG_DATA) public data: RuleNotificationDialogData,
              private breakpointObserver: BreakpointObserver,
              private fb: FormBuilder,
              public translate: TranslateService,
              private notificationService: NotificationService,
              private dialog: MatDialog) {
    super(store, router, dialogRef);

    this.triggerTypes = this.allowTriggerTypes();

    if (isDefined(data.isAdd)) {
      this.isAdd = data.isAdd;
    }

    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-xs'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));

    this.ruleNotificationForm = this.fb.group({
      name: [null, Validators.required],
      templateId: [null, Validators.required],
      triggerType: [this.isSysAdmin() ? TriggerType.ENTITIES_LIMIT : TriggerType.ALARM, Validators.required],
      recipientsConfig: this.fb.group({
        targets: [{value: null, disabled: !this.isSysAdmin()}, Validators.required],
        escalationTable: [{value: null, disabled: this.isSysAdmin()}, Validators.required]
      }),
      triggerConfig: [null],
      additionalConfig: this.fb.group({
        description: ['']
      })
    });

    this.ruleNotificationForm.get('triggerType').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => {
      if (value === TriggerType.ALARM) {
        this.ruleNotificationForm.get('recipientsConfig.escalationTable').enable({emitEvent: false});
        this.ruleNotificationForm.get('recipientsConfig.targets').disable({emitEvent: false});
      } else {
        this.ruleNotificationForm.get('recipientsConfig.escalationTable').disable({emitEvent: false});
        this.ruleNotificationForm.get('recipientsConfig.targets').enable({emitEvent: false});
      }
    });

    this.ruleNotificationForm.get('recipientsConfig.escalationTable').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      if (this.countRecipientsChainConfig() > 1) {
        this.alarmTemplateForm.get('triggerConfig.clearRule').enable({emitEvent: false});
      } else {
        this.alarmTemplateForm.get('triggerConfig.clearRule').disable({emitEvent: false});
      }
    });

    this.alarmTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        alarmTypes: [null],
        alarmSeverities: [[]],
        clearRule: this.fb.group({
          alarmStatuses: [[]]
        }),
        notifyOn: [[AlarmAction.CREATED], Validators.required]
      })
    });

    this.deviceInactivityTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        filterByDevice: [true],
        devices: [null],
        deviceProfiles: [{value: null, disabled: true}],
        notifyOn: [[DeviceEvent.INACTIVE], Validators.required]
      })
    });

    this.deviceInactivityTemplateForm.get('triggerConfig.filterByDevice').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => {
        if (value) {
          this.deviceInactivityTemplateForm.get('triggerConfig.devices').enable({emitEvent: false});
          this.deviceInactivityTemplateForm.get('triggerConfig.deviceProfiles').disable({emitEvent: false});
        } else {
          this.deviceInactivityTemplateForm.get('triggerConfig.deviceProfiles').enable({emitEvent: false});
          this.deviceInactivityTemplateForm.get('triggerConfig.devices').disable({emitEvent: false});
        }
    });

    this.entityActionTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        entityTypes: [[EntityType.DEVICE], Validators.required],
        created: [false],
        updated: [false],
        deleted: [false]
      })
    });

    this.alarmCommentTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        alarmTypes: [null],
        alarmSeverities: [[]],
        alarmStatuses: [[]],
        onlyUserComments: [false],
        notifyOnCommentUpdate: [false]
      })
    });

    this.alarmAssignmentTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        alarmTypes: [null],
        alarmSeverities: [[]],
        alarmStatuses: [[]],
        notifyOn: [[AlarmAssignmentAction.ASSIGNED], Validators.required]
      })
    });

    this.ruleEngineEventsTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        ruleChains: [null],
        ruleChainEvents: [null],
        onlyRuleChainLifecycleFailures: [false],
        trackRuleNodeEvents: [false],
        ruleNodeEvents: [null],
        onlyRuleNodeLifecycleFailures: [false]
      })
    });

    this.entitiesLimitTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        entityTypes: [],
        threshold: [.8, [Validators.min(0), Validators.max(1)]]
      })
    });

    this.apiUsageLimitTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        apiFeatures: [[]],
        notifyOn: [[ApiUsageStateValue.WARNING], Validators.required]
      })
    });

    this.integrationEventsTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({
        filterByIntegration: [false],
        integrationTypes: [[]],
        integrations: [{value: null, disabled: true}],
        notifyOn: [[ComponentLifecycleEvent.STOPPED], Validators.required],
        onlyOnError: [false]
      })
    });

    this.integrationEventsTemplateForm.get('triggerConfig.filterByIntegration').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(value => {
      if (value) {
        this.integrationEventsTemplateForm.get('triggerConfig.integrations').enable({emitEvent: false});
        this.integrationEventsTemplateForm.get('triggerConfig.integrationTypes').disable({emitEvent: false});
      } else {
        this.integrationEventsTemplateForm.get('triggerConfig.integrationTypes').enable({emitEvent: false});
        this.integrationEventsTemplateForm.get('triggerConfig.integrations').disable({emitEvent: false});
      }
    });

    this.newPlatformVersionTemplateForm = this.fb.group({
      triggerConfig: this.fb.group({

      })
    });

    this.triggerTypeFormsMap = new Map<TriggerType, FormGroup>([
      [TriggerType.ALARM, this.alarmTemplateForm],
      [TriggerType.ALARM_COMMENT, this.alarmCommentTemplateForm],
      [TriggerType.DEVICE_ACTIVITY, this.deviceInactivityTemplateForm],
      [TriggerType.ENTITY_ACTION, this.entityActionTemplateForm],
      [TriggerType.ALARM_ASSIGNMENT, this.alarmAssignmentTemplateForm],
      [TriggerType.RULE_ENGINE_COMPONENT_LIFECYCLE_EVENT, this.ruleEngineEventsTemplateForm],
      [TriggerType.ENTITIES_LIMIT, this.entitiesLimitTemplateForm],
      [TriggerType.API_USAGE_LIMIT, this.apiUsageLimitTemplateForm],
      [TriggerType.INTEGRATION_LIFECYCLE_EVENT, this.integrationEventsTemplateForm],
      [TriggerType.NEW_PLATFORM_VERSION, this.newPlatformVersionTemplateForm]
    ]);

    if (data.isAdd || data.isCopy) {
      this.dialogTitle = 'notification.add-rule';
    }
    this.ruleNotification = deepClone(this.data.rule);

    if (this.ruleNotification) {
      if (this.data.isCopy) {
        this.ruleNotification.name += ` (${this.translate.instant('action.copy')})`;
      } else {
        this.ruleNotificationForm.get('triggerType').disable({emitEvent: false});
      }
      this.ruleNotificationForm.reset({}, {emitEvent: false});
      this.ruleNotificationForm.patchValue(this.ruleNotification, {emitEvent: false});
      this.ruleNotificationForm.get('triggerType').updateValueAndValidity({onlySelf: true});
      const currentForm = this.triggerTypeFormsMap.get(this.ruleNotification.triggerType);
      currentForm.patchValue(this.ruleNotification, {emitEvent: false});
      if (this.ruleNotification.triggerType === TriggerType.DEVICE_ACTIVITY) {
        this.deviceInactivityTemplateForm.get('triggerConfig.filterByDevice')
          .patchValue(!!this.ruleNotification.triggerConfig.devices, {onlySelf: true});
      }
      if (this.ruleNotification.triggerType === TriggerType.INTEGRATION_LIFECYCLE_EVENT) {
        this.integrationEventsTemplateForm.get('triggerConfig.filterByIntegration')
          .patchValue(!!this.ruleNotification.triggerConfig.integrations, {onlySelf: true});
      }
    }

    if(data?.readonly) {
      this.dialogTitle = 'notification.view-rule';
      this.ruleNotificationForm.disable({emitEvent: false});
      Array.from(this.triggerTypeFormsMap.values()).map(form => form.disable({emitEvent: false}));
    }
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  changeStep($event: StepperSelectionEvent) {
    this.selectedIndex = $event.selectedIndex;
  }

  backStep() {
    this.addNotificationRule.previous();
  }

  nextStep() {
    if (this.selectedIndex >= this.maxStepperIndex) {
      this.add();
    } else {
      this.addNotificationRule.next();
    }
  }

  nextStepLabel(): string {
    if (this.selectedIndex !== 0) {
      return (this.data.isAdd || this.data.isCopy) ? 'action.add' : 'action.save';
    }
    return 'action.next';
  }

  get maxStepperIndex(): number {
    return this.addNotificationRule?._steps?.length - 1;
  }

  private add(): void {
    if (this.allValid()) {
      let formValue = this.ruleNotificationForm.value;
      const triggerType: TriggerType = this.ruleNotificationForm.get('triggerType').value;
      const currentForm = this.triggerTypeFormsMap.get(triggerType);
      Object.assign(formValue, currentForm.value);
      if (triggerType === TriggerType.DEVICE_ACTIVITY) {
        delete formValue.triggerConfig.filterByDevice;
      }
      if (triggerType === TriggerType.INTEGRATION_LIFECYCLE_EVENT) {
        delete formValue.triggerConfig.filterByIntegration;
      }
      formValue.recipientsConfig.triggerType = triggerType;
      formValue.triggerConfig.triggerType = triggerType;
      if (this.ruleNotification && !this.data.isCopy) {
        formValue = {...this.ruleNotification, ...formValue};
      }
      this.notificationService.saveNotificationRule(deepTrim(formValue)).subscribe(
        (target) => this.dialogRef.close(target)
      );
    }
  }

  private allValid(): boolean {
    return !this.addNotificationRule.steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addNotificationRule.selectedIndex = index;
        return true;
      } else {
        return false;
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  createTarget($event: Event, button: MatButton) {
    if ($event) {
      $event.stopPropagation();
    }
    button._elementRef.nativeElement.blur();
    this.dialog.open<RecipientNotificationDialogComponent, RecipientNotificationDialogData,
      NotificationTarget>(RecipientNotificationDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {}
    }).afterClosed()
      .subscribe((res) => {
        if (res) {
          let formValue: string[] = this.ruleNotificationForm.get('recipientsConfig.targets').value;
          if (!formValue) {
            formValue = [];
          }
          formValue.push(res.id.id);
          this.ruleNotificationForm.get('recipientsConfig.targets').patchValue(formValue);
        }
      });
  }

  countRecipientsChainConfig(): number {
    return Object.keys(this.ruleNotificationForm.get('recipientsConfig.escalationTable').value ?? {}).length;
  }

  formatLabel(value: number): string {
    const formatValue = (value * 100).toFixed();
    return `${formatValue}%`;
  }

  private isSysAdmin(): boolean {
    return this.authUser.authority === Authority.SYS_ADMIN;
  }

  private allowTriggerTypes(): TriggerType[] {
    if (this.isSysAdmin()) {
      return [TriggerType.ENTITIES_LIMIT, TriggerType.API_USAGE_LIMIT, TriggerType.NEW_PLATFORM_VERSION];
    }
    return Object.values(TriggerType).filter(type => type !== TriggerType.ENTITIES_LIMIT && type !== TriggerType.API_USAGE_LIMIT
      && type !== TriggerType.NEW_PLATFORM_VERSION);
  }

  get allowEntityTypeForEntityAction(): EntityType[] {
    if (!this._allowEntityTypeForEntityAction) {
      const excludeEntityType: Set<EntityType> = new Set([
        EntityType.API_USAGE_STATE,
        EntityType.TENANT_PROFILE,
        EntityType.RPC,
        EntityType.QUEUE,
        EntityType.NOTIFICATION,
        EntityType.NOTIFICATION_REQUEST,
        EntityType.WIDGET_TYPE,
        EntityType.GROUP_PERMISSION
      ]);
      this._allowEntityTypeForEntityAction = Object.values(EntityType).filter(type => !excludeEntityType.has(type));
    }
    return this._allowEntityTypeForEntityAction;
  }
}
