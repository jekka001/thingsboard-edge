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

import { Component, EventEmitter, Input, OnInit, Output, ViewEncapsulation } from '@angular/core';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { merge } from 'rxjs';
import {
  ScadaSymbolBehavior,
  ScadaSymbolBehaviorType,
  scadaSymbolBehaviorTypes,
  scadaSymbolBehaviorTypeTranslations
} from '@app/modules/home/components/widget/lib/scada/scada-symbol.models';
import { ValueType, valueTypesMap } from '@shared/models/constants';
import { ValueToDataType } from '@shared/models/action-widget-settings.models';
import { WidgetService } from '@core/http/widget.service';

@Component({
  selector: 'tb-scada-symbol-behavior-panel',
  templateUrl: './scada-symbol-behavior-panel.component.html',
  styleUrls: ['./scada-symbol-behavior-panel.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class ScadaSymbolBehaviorPanelComponent implements OnInit {

  ScadaSymbolBehaviorType = ScadaSymbolBehaviorType;

  ValueType = ValueType;

  ValueToDataType = ValueToDataType;

  scadaSymbolBehaviorTypes = scadaSymbolBehaviorTypes;
  scadaSymbolBehaviorTypeTranslations = scadaSymbolBehaviorTypeTranslations;

  valueTypes = Object.keys(ValueType) as ValueType[];

  valueTypesMap = valueTypesMap;

  @Input()
  isAdd = false;

  @Input()
  behavior: ScadaSymbolBehavior;

  @Input()
  disabled: boolean;

  @Input()
  popover: TbPopoverComponent<ScadaSymbolBehaviorPanelComponent>;

  @Output()
  behaviorSettingsApplied = new EventEmitter<ScadaSymbolBehavior>();

  functionScopeVariables = this.widgetService.getWidgetScopeVariables();

  panelTitle: string;

  behaviorFormGroup: UntypedFormGroup;

  constructor(private fb: UntypedFormBuilder,
              private widgetService: WidgetService) {
  }

  ngOnInit(): void {
    this.panelTitle = this.isAdd ? 'scada.behavior.add-behavior' : 'scada.behavior.behavior-settings';
    this.behaviorFormGroup = this.fb.group(
      {
        id: [this.behavior.id, [Validators.required]],
        name: [this.behavior.name, [Validators.required]],
        hint: [this.behavior.hint, []],
        group: [this.behavior.group, []],
        type: [this.behavior.type, [Validators.required]],
        valueType: [this.behavior.valueType, [Validators.required]],
        defaultValue: [this.behavior.defaultValue, [Validators.required]],
        trueLabel: [this.behavior.trueLabel, []],
        falseLabel: [this.behavior.falseLabel, []],
        stateLabel: [this.behavior.stateLabel, []],
        valueToDataType: [this.behavior.valueToDataType, [Validators.required]],
        constantValue: [this.behavior.constantValue, [Validators.required]],
        valueToDataFunction: [this.behavior.valueToDataFunction, [Validators.required]]
      }
    );
    if (this.disabled) {
      this.behaviorFormGroup.disable({emitEvent: false});
    } else {
      merge(this.behaviorFormGroup.get('type').valueChanges,
        this.behaviorFormGroup.get('valueType').valueChanges,
        this.behaviorFormGroup.get('valueToDataType').valueChanges).subscribe(() => {
        this.updateValidators();
      });
      this.updateValidators();
    }
  }

  cancel() {
    this.popover?.hide();
  }

  applyBehaviorSettings() {
    const behavior = this.behaviorFormGroup.getRawValue();
    this.behaviorSettingsApplied.emit(behavior);
  }

  private updateValidators() {
    const type: ScadaSymbolBehaviorType = this.behaviorFormGroup.get('type').value;
    const valueType: ValueType = this.behaviorFormGroup.get('valueType').value;
    let valueToDataType: ValueToDataType = this.behaviorFormGroup.get('valueToDataType').value;
    this.behaviorFormGroup.disable({emitEvent: false});
    this.behaviorFormGroup.get('id').enable({emitEvent: false});
    this.behaviorFormGroup.get('name').enable({emitEvent: false});
    this.behaviorFormGroup.get('type').enable({emitEvent: false});
    this.behaviorFormGroup.get('hint').enable({emitEvent: false});
    this.behaviorFormGroup.get('group').enable({emitEvent: false});
    switch (type) {
      case ScadaSymbolBehaviorType.value:
        this.behaviorFormGroup.get('valueType').enable({emitEvent: false});
        this.behaviorFormGroup.get('defaultValue').enable({emitEvent: false});
        if (valueType === ValueType.BOOLEAN) {
          this.behaviorFormGroup.get('trueLabel').enable({emitEvent: false});
          this.behaviorFormGroup.get('falseLabel').enable({emitEvent: false});
          this.behaviorFormGroup.get('stateLabel').enable({emitEvent: false});
        }
        break;
      case ScadaSymbolBehaviorType.action:
        if (valueType === ValueType.BOOLEAN && valueToDataType === ValueToDataType.VALUE) {
          this.behaviorFormGroup.patchValue({valueToDataType: ValueToDataType.CONSTANT}, {emitEvent: false});
          valueToDataType = ValueToDataType.CONSTANT;
        } else if (valueType !== ValueType.BOOLEAN && valueToDataType === ValueToDataType.CONSTANT) {
          this.behaviorFormGroup.patchValue({valueToDataType: ValueToDataType.VALUE}, {emitEvent: false});
          valueToDataType = ValueToDataType.VALUE;
        }
        this.behaviorFormGroup.get('valueType').enable({emitEvent: false});
        this.behaviorFormGroup.get('valueToDataType').enable({emitEvent: false});
        if (valueToDataType === ValueToDataType.CONSTANT) {
          this.behaviorFormGroup.get('constantValue').enable({emitEvent: false});
        } else if (valueToDataType === ValueToDataType.FUNCTION) {
          this.behaviorFormGroup.get('valueToDataFunction').enable({emitEvent: false});
        }
        break;
      case ScadaSymbolBehaviorType.widgetAction:
        break;
    }
  }
}
