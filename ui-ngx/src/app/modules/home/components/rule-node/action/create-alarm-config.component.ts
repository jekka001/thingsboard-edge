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

import { Component, EventEmitter, ViewChild } from '@angular/core';
import { AppState, getCurrentAuthState, NodeScriptTestService } from '@core/public-api';
import { Store } from '@ngrx/store';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { COMMA, ENTER, SEMICOLON } from '@angular/cdk/keycodes';
import { MatChipInputEvent } from '@angular/material/chips';
import {
  RuleNodeConfiguration,
  RuleNodeConfigurationComponent,
  ScriptLanguage
} from '@app/shared/models/rule-node.models';
import type { JsFuncComponent } from '@app/shared/components/js-func.component';
import { AlarmSeverity, alarmSeverityTranslations } from '@app/shared/models/alarm.models';
import { DebugRuleNodeEventBody } from '@shared/models/event.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-action-node-create-alarm-config',
  templateUrl: './create-alarm-config.component.html',
  styleUrls: []
})
export class CreateAlarmConfigComponent extends RuleNodeConfigurationComponent {

  @ViewChild('jsFuncComponent', {static: false}) jsFuncComponent: JsFuncComponent;
  @ViewChild('tbelFuncComponent', {static: false}) tbelFuncComponent: JsFuncComponent;

  alarmSeverities = Object.keys(AlarmSeverity);
  alarmSeverityTranslationMap = alarmSeverityTranslations;
  createAlarmConfigForm: UntypedFormGroup;

  separatorKeysCodes = [ENTER, COMMA, SEMICOLON];

  tbelEnabled = getCurrentAuthState(this.store).tbelEnabled;

  scriptLanguage = ScriptLanguage;

  changeScript: EventEmitter<void> = new EventEmitter<void>();

  readonly hasScript = true;

  readonly testScriptLabel = 'rule-node-config.test-details-function';

  constructor(protected store: Store<AppState>,
              private fb: UntypedFormBuilder,
              private nodeScriptTestService: NodeScriptTestService,
              private translate: TranslateService) {
    super();
  }

  protected configForm(): UntypedFormGroup {
    return this.createAlarmConfigForm;
  }

  protected onConfigurationSet(configuration: RuleNodeConfiguration) {
    this.createAlarmConfigForm = this.fb.group({
      scriptLang: [configuration ? configuration.scriptLang : ScriptLanguage.JS, [Validators.required]],
      alarmDetailsBuildJs: [configuration ? configuration.alarmDetailsBuildJs : null, []],
      alarmDetailsBuildTbel: [configuration ? configuration.alarmDetailsBuildTbel : null, []],
      useMessageAlarmData: [configuration ? configuration.useMessageAlarmData : false, []],
      overwriteAlarmDetails: [configuration ? configuration.overwriteAlarmDetails : false, []],
      alarmType: [configuration ? configuration.alarmType : null, []],
      severity: [configuration ? configuration.severity : null, []],
      propagate: [configuration ? configuration.propagate : false, []],
      relationTypes: [configuration ? configuration.relationTypes : null, []],
      propagateToOwner: [configuration ? configuration.propagateToOwner : false, []],
      propagateToOwnerHierarchy: [configuration ? configuration.propagateToOwnerHierarchy : false, []],
      propagateToTenant: [configuration ? configuration.propagateToTenant : false, []],
      dynamicSeverity: false
    });

    this.createAlarmConfigForm.get('dynamicSeverity').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((dynamicSeverity) => {
      if(dynamicSeverity){
        this.createAlarmConfigForm.get('severity').patchValue('',{emitEvent:false});
      } else {
        this.createAlarmConfigForm.get('severity').patchValue(this.alarmSeverities[0],{emitEvent:false});
      }
    });

  }


  protected validatorTriggers(): string[] {
    return ['useMessageAlarmData', 'overwriteAlarmDetails', 'scriptLang'];
  }

  protected updateValidators(emitEvent: boolean) {
    const useMessageAlarmData: boolean = this.createAlarmConfigForm.get('useMessageAlarmData').value;
    const overwriteAlarmDetails: boolean = this.createAlarmConfigForm.get('overwriteAlarmDetails').value;
    if (useMessageAlarmData) {
      this.createAlarmConfigForm.get('alarmType').setValidators([]);
      this.createAlarmConfigForm.get('severity').setValidators([]);
    } else {
      this.createAlarmConfigForm.get('alarmType').setValidators([Validators.required]);
      this.createAlarmConfigForm.get('severity').setValidators([Validators.required]);
    }
    this.createAlarmConfigForm.get('alarmType').updateValueAndValidity({emitEvent});
    this.createAlarmConfigForm.get('severity').updateValueAndValidity({emitEvent});

    let scriptLang: ScriptLanguage = this.createAlarmConfigForm.get('scriptLang').value;
    if (scriptLang === ScriptLanguage.TBEL && !this.tbelEnabled) {
      scriptLang = ScriptLanguage.JS;
      this.createAlarmConfigForm.get('scriptLang').patchValue(scriptLang, {emitEvent: false});
      setTimeout(() => {this.createAlarmConfigForm.updateValueAndValidity({emitEvent: true});});
    }
    const useAlarmDetailsBuildScript = useMessageAlarmData === false || overwriteAlarmDetails === true;
    this.createAlarmConfigForm.get('alarmDetailsBuildJs')
      .setValidators(useAlarmDetailsBuildScript && scriptLang === ScriptLanguage.JS ? [Validators.required] : []);
    this.createAlarmConfigForm.get('alarmDetailsBuildTbel')
      .setValidators(useAlarmDetailsBuildScript && scriptLang === ScriptLanguage.TBEL ? [Validators.required] : []);
    this.createAlarmConfigForm.get('alarmDetailsBuildJs').updateValueAndValidity({emitEvent});
    this.createAlarmConfigForm.get('alarmDetailsBuildTbel').updateValueAndValidity({emitEvent});
  }

  protected prepareInputConfig(configuration: RuleNodeConfiguration): RuleNodeConfiguration {
    if (configuration) {
      if (!configuration.scriptLang) {
        configuration.scriptLang = ScriptLanguage.JS;
      }
    }
    return configuration;
  }

  testScript(debugEventBody?: DebugRuleNodeEventBody) {
    const scriptLang: ScriptLanguage = this.createAlarmConfigForm.get('scriptLang').value;
    const scriptField = scriptLang === ScriptLanguage.JS ? 'alarmDetailsBuildJs' : 'alarmDetailsBuildTbel';
    const helpId = scriptLang === ScriptLanguage.JS ? 'rulenode/create_alarm_node_script_fn' : 'rulenode/tbel/create_alarm_node_script_fn';
    const script: string = this.createAlarmConfigForm.get(scriptField).value;
    this.nodeScriptTestService.testNodeScript(
      script,
      'json',
      this.translate.instant('rule-node-config.details'),
      'Details',
      ['msg', 'metadata', 'msgType'],
      this.ruleNodeId,
      helpId,
      scriptLang,
      debugEventBody
    ).subscribe((theScript) => {
      if (theScript) {
        this.createAlarmConfigForm.get(scriptField).setValue(theScript);
        this.changeScript.emit();
      }
    });
  }

  removeKey(key: string, keysField: string): void {
    const keys: string[] = this.createAlarmConfigForm.get(keysField).value;
    const index = keys.indexOf(key);
    if (index >= 0) {
      keys.splice(index, 1);
      this.createAlarmConfigForm.get(keysField).setValue(keys, {emitEvent: true});
    }
  }

  addKey(event: MatChipInputEvent, keysField: string): void {
    const input = event.input;
    let value = event.value;
    if ((value || '').trim()) {
      value = value.trim();
      let keys: string[] = this.createAlarmConfigForm.get(keysField).value;
      if (!keys || keys.indexOf(value) === -1) {
        if (!keys) {
          keys = [];
        }
        keys.push(value);
        this.createAlarmConfigForm.get(keysField).setValue(keys, {emitEvent: true});
      }
    }
    if (input) {
      input.value = '';
    }
  }

  protected onValidate() {
    const useMessageAlarmData: boolean = this.createAlarmConfigForm.get('useMessageAlarmData').value;
    const overwriteAlarmDetails: boolean = this.createAlarmConfigForm.get('overwriteAlarmDetails').value;
    if (!useMessageAlarmData || overwriteAlarmDetails) {
      const scriptLang: ScriptLanguage = this.createAlarmConfigForm.get('scriptLang').value;
      if (scriptLang === ScriptLanguage.JS) {
        this.jsFuncComponent.validateOnSubmit();
      }
    }
  }
}
