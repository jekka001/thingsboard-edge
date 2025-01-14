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

import { Component, EventEmitter, OnDestroy, ViewChild } from '@angular/core';
import { getCurrentAuthState, isDefinedAndNotNull, NodeScriptTestService } from '@core/public-api';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import {
  RuleNodeConfiguration,
  RuleNodeConfigurationComponent,
  ScriptLanguage
} from '@app/shared/models/rule-node.models';
import type { JsFuncComponent } from '@app/shared/components/js-func.component';
import { EntityType } from '@app/shared/models/entity-type.models';
import { DebugRuleNodeEventBody } from '@shared/models/event.models';
import { allowedEntityGroupTypes } from '@home/components/rule-node/rule-node-config.models';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

@Component({
  selector: 'tb-action-node-generator-config',
  templateUrl: './generator-config.component.html',
  styleUrls: ['generator-config.component.scss']
})
export class GeneratorConfigComponent extends RuleNodeConfigurationComponent implements OnDestroy {

  @ViewChild('jsFuncComponent', {static: false}) jsFuncComponent: JsFuncComponent;
  @ViewChild('tbelFuncComponent', {static: false}) tbelFuncComponent: JsFuncComponent;

  generatorConfigForm: UntypedFormGroup;
  entityGroupTypes = allowedEntityGroupTypes;
  private destroy$ = new Subject<void>();

  tbelEnabled = getCurrentAuthState(this.store).tbelEnabled;

  scriptLanguage = ScriptLanguage;

  changeScript: EventEmitter<void> = new EventEmitter<void>();

  allowedEntityTypes = [
    EntityType.DEVICE, EntityType.ASSET, EntityType.ENTITY_VIEW, EntityType.CUSTOMER,
    EntityType.USER, EntityType.DASHBOARD, EntityType.CONVERTER,
    EntityType.INTEGRATION, EntityType.SCHEDULER_EVENT, EntityType.BLOB_ENTITY, EntityType.ROLE, EntityType.EDGE
  ];

  additionEntityTypes = {
    TENANT: this.translate.instant('rule-node-config.current-tenant'),
    RULE_NODE: this.translate.instant('rule-node-config.current-rule-node')
  };

  readonly hasScript = true;

  readonly testScriptLabel = 'rule-node-config.test-generator-function';

  constructor(private fb: UntypedFormBuilder,
              private nodeScriptTestService: NodeScriptTestService,
              private translate: TranslateService) {
    super();
  }

  protected configForm(): UntypedFormGroup {
    return this.generatorConfigForm;
  }

  protected onConfigurationSet(configuration: RuleNodeConfiguration) {
    this.generatorConfigForm = this.fb.group({
      isEntityGroup: [configuration ? configuration.isEntityGroup : false, []],
      msgCount: [configuration ? configuration.msgCount : null, [Validators.required, Validators.min(0)]],
      periodInSeconds: [configuration ? configuration.periodInSeconds : null, [Validators.required, Validators.min(1)]],
      originator: [configuration ? configuration.originator : null, [Validators.required]],
      scriptLang: [configuration ? configuration.scriptLang : ScriptLanguage.JS, [Validators.required]],
      jsScript: [configuration ? configuration.jsScript : null, []],
      tbelScript: [configuration ? configuration.tbelScript : null, []]
    });

    this.generatorConfigForm.get('isEntityGroup').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => this.cleanKeys());
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private cleanKeys() {
    this.generatorConfigForm.get('originator').patchValue(null, {emitEvent: false});
  }

  protected validatorTriggers(): string[] {
    return ['scriptLang'];
  }

  protected updateValidators(emitEvent: boolean) {
    let scriptLang: ScriptLanguage = this.generatorConfigForm.get('scriptLang').value;
    if (scriptLang === ScriptLanguage.TBEL && !this.tbelEnabled) {
      scriptLang = ScriptLanguage.JS;
      this.generatorConfigForm.get('scriptLang').patchValue(scriptLang, {emitEvent: false});
      setTimeout(() => {this.generatorConfigForm.updateValueAndValidity({emitEvent: true});});
    }
    this.generatorConfigForm.get('jsScript').setValidators(scriptLang === ScriptLanguage.JS ? [Validators.required] : []);
    this.generatorConfigForm.get('jsScript').updateValueAndValidity({emitEvent});
    this.generatorConfigForm.get('tbelScript').setValidators(scriptLang === ScriptLanguage.TBEL ? [Validators.required] : []);
    this.generatorConfigForm.get('tbelScript').updateValueAndValidity({emitEvent});
  }

  protected prepareInputConfig(configuration: RuleNodeConfiguration): RuleNodeConfiguration {
    return {
      isEntityGroup: isDefinedAndNotNull(configuration?.originatorType) ? configuration.originatorType == EntityType.ENTITY_GROUP : false,
      msgCount: isDefinedAndNotNull(configuration?.msgCount) ? configuration?.msgCount : 0,
      periodInSeconds: isDefinedAndNotNull(configuration?.periodInSeconds) ? configuration?.periodInSeconds : 1,
      originator: {
        id: isDefinedAndNotNull(configuration?.originatorId) ? configuration?.originatorId : null,
        entityType: isDefinedAndNotNull(configuration?.originatorType) ? configuration?.originatorType :  EntityType.RULE_NODE
      },
      scriptLang: isDefinedAndNotNull(configuration?.scriptLang) ? configuration?.scriptLang : ScriptLanguage.JS,
      tbelScript: isDefinedAndNotNull(configuration?.tbelScript) ? configuration?.tbelScript : null,
      jsScript: isDefinedAndNotNull(configuration?.jsScript) ? configuration?.jsScript : null,
    };
  }

  protected prepareOutputConfig(configuration: RuleNodeConfiguration): RuleNodeConfiguration {
    if (isDefinedAndNotNull(configuration.originator)) {
      configuration.originatorId = configuration.originator.id;
      configuration.originatorType = configuration.originator.entityType;
    } else {
      configuration.originatorId = null;
      configuration.originatorType = null;
    }
    delete configuration.originator;
    delete configuration.isEntityGroup;
    return configuration;
  }

  testScript(debugEventBody?: DebugRuleNodeEventBody) {
    const scriptLang: ScriptLanguage = this.generatorConfigForm.get('scriptLang').value;
    const scriptField = scriptLang === ScriptLanguage.JS ? 'jsScript' : 'tbelScript';
    const helpId = scriptLang === ScriptLanguage.JS ? 'rulenode/generator_node_script_fn' : 'rulenode/tbel/generator_node_script_fn';
    const script: string = this.generatorConfigForm.get(scriptField).value;
    this.nodeScriptTestService.testNodeScript(
      script,
      'generate',
      this.translate.instant('rule-node-config.generator'),
      'Generate',
      ['prevMsg', 'prevMetadata', 'prevMsgType'],
      this.ruleNodeId,
      helpId,
      scriptLang,
      debugEventBody
    ).subscribe((theScript) => {
      if (theScript) {
        this.generatorConfigForm.get(scriptField).setValue(theScript);
        this.changeScript.emit();
      }
    });
  }

  protected onValidate() {
    const scriptLang: ScriptLanguage = this.generatorConfigForm.get('scriptLang').value;
    if (scriptLang === ScriptLanguage.JS) {
      this.jsFuncComponent.validateOnSubmit();
    }
  }
}
