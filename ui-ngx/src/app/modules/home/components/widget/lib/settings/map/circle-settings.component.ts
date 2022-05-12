///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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

import { Component, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  FormControl,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator,
  Validators
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import {
  CircleSettings,
  ShowTooltipAction,
  showTooltipActionTranslationMap
} from '@home/components/widget/lib/maps/map-models';
import { WidgetService } from '@core/http/widget.service';
import { Widget } from '@shared/models/widget.models';

@Component({
  selector: 'tb-circle-settings',
  templateUrl: './circle-settings.component.html',
  styleUrls: ['./../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CircleSettingsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => CircleSettingsComponent),
      multi: true
    }
  ]
})
export class CircleSettingsComponent extends PageComponent implements OnInit, ControlValueAccessor, Validator {

  @Input()
  disabled: boolean;

  @Input()
  widget: Widget;

  functionScopeVariables = this.widgetService.getWidgetScopeVariables();

  private modelValue: CircleSettings;

  private propagateChange = null;

  public circleSettingsFormGroup: FormGroup;

  showTooltipActions = Object.values(ShowTooltipAction);

  showTooltipActionTranslations = showTooltipActionTranslationMap;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private widgetService: WidgetService,
              private fb: FormBuilder) {
    super(store);
  }

  ngOnInit(): void {
    this.circleSettingsFormGroup = this.fb.group({
      showCircle: [null, []],
      circleKeyName: [null, [Validators.required]],
      editableCircle: [null, []],
      showCircleLabel: [null, []],
      useCircleLabelFunction: [null, []],
      circleLabel: [null, []],
      circleLabelFunction: [null, []],
      showCircleTooltip: [null, []],
      showCircleTooltipAction: [null, []],
      autoCloseCircleTooltip: [null, []],
      useCircleTooltipFunction: [null, []],
      circleTooltipPattern: [null, []],
      circleTooltipFunction: [null, []],
      circleFillColor: [null, []],
      circleFillColorOpacity: [null, [Validators.min(0), Validators.max(1)]],
      useCircleFillColorFunction: [null, []],
      circleFillColorFunction: [null, []],
      circleStrokeColor: [null, []],
      circleStrokeOpacity: [null, [Validators.min(0), Validators.max(1)]],
      circleStrokeWeight: [null, [Validators.min(0)]],
      useCircleStrokeColorFunction: [null, []],
      circleStrokeColorFunction: [null, []]
    });
    this.circleSettingsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    this.circleSettingsFormGroup.get('showCircle').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.circleSettingsFormGroup.get('showCircleLabel').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.circleSettingsFormGroup.get('useCircleLabelFunction').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.circleSettingsFormGroup.get('showCircleTooltip').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.circleSettingsFormGroup.get('useCircleTooltipFunction').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.circleSettingsFormGroup.get('useCircleFillColorFunction').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.circleSettingsFormGroup.get('useCircleStrokeColorFunction').valueChanges.subscribe(() => {
      this.updateValidators(true);
    });
    this.updateValidators(false);
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.circleSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.circleSettingsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: CircleSettings): void {
    this.modelValue = value;
    this.circleSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
    this.updateValidators(false);
  }

  public validate(c: FormControl) {
    return this.circleSettingsFormGroup.valid ? null : {
      circleSettings: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const value: CircleSettings = this.circleSettingsFormGroup.value;
    this.modelValue = value;
    this.propagateChange(this.modelValue);
  }

  private updateValidators(emitEvent?: boolean): void {
    const showCircle: boolean = this.circleSettingsFormGroup.get('showCircle').value;
    const showCircleLabel: boolean = this.circleSettingsFormGroup.get('showCircleLabel').value;
    const useCircleLabelFunction: boolean = this.circleSettingsFormGroup.get('useCircleLabelFunction').value;
    const showCircleTooltip: boolean = this.circleSettingsFormGroup.get('showCircleTooltip').value;
    const useCircleTooltipFunction: boolean = this.circleSettingsFormGroup.get('useCircleTooltipFunction').value;
    const useCircleFillColorFunction: boolean = this.circleSettingsFormGroup.get('useCircleFillColorFunction').value;
    const useCircleStrokeColorFunction: boolean = this.circleSettingsFormGroup.get('useCircleStrokeColorFunction').value;

    this.circleSettingsFormGroup.disable({emitEvent: false});
    this.circleSettingsFormGroup.get('showCircle').enable({emitEvent: false});

    if (showCircle) {
      this.circleSettingsFormGroup.get('circleKeyName').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('editableCircle').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('showCircleLabel').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('showCircleTooltip').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('circleFillColor').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('circleFillColorOpacity').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('useCircleFillColorFunction').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('circleStrokeColor').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('circleStrokeOpacity').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('circleStrokeWeight').enable({emitEvent: false});
      this.circleSettingsFormGroup.get('useCircleStrokeColorFunction').enable({emitEvent: false});
      if (showCircleLabel) {
        this.circleSettingsFormGroup.get('useCircleLabelFunction').enable({emitEvent: false});
        if (useCircleLabelFunction) {
          this.circleSettingsFormGroup.get('circleLabelFunction').enable({emitEvent});
          this.circleSettingsFormGroup.get('circleLabel').disable({emitEvent});
        } else {
          this.circleSettingsFormGroup.get('circleLabelFunction').disable({emitEvent});
          this.circleSettingsFormGroup.get('circleLabel').enable({emitEvent});
        }
      } else {
        this.circleSettingsFormGroup.get('useCircleLabelFunction').disable({emitEvent: false});
        this.circleSettingsFormGroup.get('circleLabelFunction').disable({emitEvent});
        this.circleSettingsFormGroup.get('circleLabel').disable({emitEvent});
      }
      if (showCircleTooltip) {
        this.circleSettingsFormGroup.get('showCircleTooltipAction').enable({emitEvent});
        this.circleSettingsFormGroup.get('autoCloseCircleTooltip').enable({emitEvent});
        this.circleSettingsFormGroup.get('useCircleTooltipFunction').enable({emitEvent: false});
        if (useCircleTooltipFunction) {
          this.circleSettingsFormGroup.get('circleTooltipFunction').enable({emitEvent});
          this.circleSettingsFormGroup.get('circleTooltipPattern').disable({emitEvent});
        } else {
          this.circleSettingsFormGroup.get('circleTooltipFunction').disable({emitEvent});
          this.circleSettingsFormGroup.get('circleTooltipPattern').enable({emitEvent});
        }
      } else {
        this.circleSettingsFormGroup.get('showCircleTooltipAction').disable({emitEvent});
        this.circleSettingsFormGroup.get('autoCloseCircleTooltip').disable({emitEvent});
        this.circleSettingsFormGroup.get('useCircleTooltipFunction').disable({emitEvent: false});
        this.circleSettingsFormGroup.get('circleTooltipFunction').disable({emitEvent});
        this.circleSettingsFormGroup.get('circleTooltipPattern').disable({emitEvent});
      }
      if (useCircleFillColorFunction) {
        this.circleSettingsFormGroup.get('circleFillColorFunction').enable({emitEvent});
      } else {
        this.circleSettingsFormGroup.get('circleFillColorFunction').disable({emitEvent});
      }
      if (useCircleStrokeColorFunction) {
        this.circleSettingsFormGroup.get('circleStrokeColorFunction').enable({emitEvent});
      } else {
        this.circleSettingsFormGroup.get('circleStrokeColorFunction').disable({emitEvent});
      }
    }
    this.circleSettingsFormGroup.updateValueAndValidity({emitEvent: false});
  }
}
