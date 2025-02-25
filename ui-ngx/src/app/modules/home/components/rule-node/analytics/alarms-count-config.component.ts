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

import { Component } from '@angular/core';
import { deepClone } from '@core/public-api';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { RuleNodeConfiguration, RuleNodeConfigurationComponent } from '@app/shared/models/rule-node.models';
import {
  defaultRelationsQuery,
  ParentEntitiesQueryType,
  prepareParentEntitiesQuery,
  TimeUnit,
  timeUnitTranslations
} from '../rule-node-config.models';

const intervalValidators = [Validators.required,
  Validators.min(1), Validators.max(2147483647)];

@Component({
  selector: 'tb-analytics-node-alarms-count-config',
  templateUrl: './alarms-count-config.component.html',
  styleUrls: ['./alarms-count-config.component.scss']
})
export class AlarmsCountConfigComponent extends RuleNodeConfigurationComponent {

  alarmsCountConfigForm: UntypedFormGroup;

  aggPeriodTimeUnits: TimeUnit[] = [TimeUnit.MINUTES, TimeUnit.HOURS, TimeUnit.DAYS];

  timeUnits = Object.keys(TimeUnit);
  timeUnitsTranslationMap = timeUnitTranslations;

  constructor(private fb: UntypedFormBuilder) {
    super();
  }

  protected configForm(): UntypedFormGroup {
    return this.alarmsCountConfigForm;
  }

  protected onConfigurationSet(configuration: RuleNodeConfiguration) {
    this.alarmsCountConfigForm = this.fb.group({
      periodValue: [configuration ? configuration.periodValue : null, intervalValidators],
      periodTimeUnit: [configuration ? configuration.periodTimeUnit : null, [Validators.required]],
      outMsgType: [configuration ? configuration.outMsgType : null, [Validators.required]],
      countAlarmsForChildEntities: [configuration ? configuration.countAlarmsForChildEntities : null, []],
      parentEntitiesQuery: this.fb.group(
        {
          type: [configuration && configuration.parentEntitiesQuery ?
            configuration.parentEntitiesQuery.type : null, [Validators.required]],
          rootEntityId: [configuration && configuration.parentEntitiesQuery ?
            configuration.parentEntitiesQuery.rootEntityId : null, []],
          relationsQuery: [configuration && configuration.parentEntitiesQuery ?
            configuration.parentEntitiesQuery.relationsQuery : null, []],
          entityId: [configuration && configuration.parentEntitiesQuery ?
            configuration.parentEntitiesQuery.entityId : null, []],
          entityGroupId: [configuration && configuration.parentEntitiesQuery ?
            configuration.parentEntitiesQuery.entityGroupId : null, []],
          childRelationsQuery: [configuration && configuration.parentEntitiesQuery ?
            configuration.parentEntitiesQuery.childRelationsQuery : null, []]
        }
      ),
      alarmsCountMappings: [configuration ? configuration.alarmsCountMappings : null, [Validators.required]]
    });
  }

  protected validatorTriggers(): string[] {
    return ['parentEntitiesQuery.type', 'countAlarmsForChildEntities'];
  }

  protected updateValidators(emitEvent: boolean, trigger?: string) {
    const parentEntitiesQueryControl = this.alarmsCountConfigForm.get('parentEntitiesQuery');
    const parentEntitiesQueryType: ParentEntitiesQueryType = parentEntitiesQueryControl.get('type').value;
    const countAlarmsForChildEntities: boolean = this.alarmsCountConfigForm.get('countAlarmsForChildEntities').value;
    if (emitEvent) {
      if (trigger === 'parentEntitiesQuery.type') {
        const parentEntitiesQuery = {
          type: parentEntitiesQueryType
        } as any;
        if (parentEntitiesQueryType === 'relationsQuery') {
          parentEntitiesQuery.rootEntityId = null;
          parentEntitiesQuery.relationsQuery = deepClone(defaultRelationsQuery);
          if (countAlarmsForChildEntities) {
            parentEntitiesQuery.childRelationsQuery = deepClone(defaultRelationsQuery);
          }
        } else if (parentEntitiesQueryType === 'single') {
          parentEntitiesQuery.entityId = null;
          if (countAlarmsForChildEntities) {
            parentEntitiesQuery.childRelationsQuery = deepClone(defaultRelationsQuery);
          }
        } else if (parentEntitiesQueryType === 'group') {
          parentEntitiesQuery.entityGroupId = null;
        }
        parentEntitiesQueryControl.reset(parentEntitiesQuery, {emitEvent: false});
      }
    }

    parentEntitiesQueryControl.get('rootEntityId').setValidators([]);
    parentEntitiesQueryControl.get('relationsQuery').setValidators([]);
    parentEntitiesQueryControl.get('entityId').setValidators([]);
    parentEntitiesQueryControl.get('entityGroupId').setValidators([]);
    parentEntitiesQueryControl.get('childRelationsQuery').setValidators([]);

    if (parentEntitiesQueryType === 'relationsQuery') {
      parentEntitiesQueryControl.get('rootEntityId').setValidators([Validators.required]);
      parentEntitiesQueryControl.get('relationsQuery').setValidators([Validators.required]);
      if (countAlarmsForChildEntities) {
        parentEntitiesQueryControl.get('childRelationsQuery').setValidators([Validators.required]);
      }
    } else if (parentEntitiesQueryType === 'single') {
      parentEntitiesQueryControl.get('entityId').setValidators([Validators.required]);
      if (countAlarmsForChildEntities) {
        parentEntitiesQueryControl.get('childRelationsQuery').setValidators([Validators.required]);
      }
    } else if (parentEntitiesQueryType === 'group') {
      parentEntitiesQueryControl.get('entityGroupId').setValidators([Validators.required]);
    }
    parentEntitiesQueryControl.get('rootEntityId').updateValueAndValidity({emitEvent});
    parentEntitiesQueryControl.get('relationsQuery').updateValueAndValidity({emitEvent});
    parentEntitiesQueryControl.get('entityId').updateValueAndValidity({emitEvent});
    parentEntitiesQueryControl.get('entityGroupId').updateValueAndValidity({emitEvent});
    parentEntitiesQueryControl.get('childRelationsQuery').updateValueAndValidity({emitEvent});
  }

  protected prepareOutputConfig(configuration: RuleNodeConfiguration): RuleNodeConfiguration {
    configuration.parentEntitiesQuery = prepareParentEntitiesQuery(configuration.parentEntitiesQuery);
    return configuration;
  }
}
