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

import {
  ChangeDetectorRef,
  Component,
  forwardRef,
  Input,
  OnChanges,
  Renderer2,
  SimpleChanges,
  ViewContainerRef,
} from '@angular/core';
import {
  AbstractControl,
  ControlValueAccessor,
  FormBuilder,
  FormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors,
  Validator,
} from '@angular/forms';
import {
  ArgumentEntityType,
  ArgumentType,
  ArgumentTypeTranslations,
  CalculatedFieldArgument,
  CalculatedFieldArgumentValue,
  CalculatedFieldType,
} from '@shared/models/calculated-field.models';
import { CalculatedFieldArgumentPanelComponent } from '@home/components/calculated-fields/components/public-api';
import { MatButton } from '@angular/material/button';
import { TbPopoverService } from '@shared/components/popover.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EntityId } from '@shared/models/id/entity-id';
import { EntityType, entityTypeTranslations } from '@shared/models/entity-type.models';
import { isDefined, isDefinedAndNotNull } from '@core/utils';
import { TbPopoverComponent } from '@shared/components/popover.component';

@Component({
  selector: 'tb-calculated-field-arguments-table',
  templateUrl: './calculated-field-arguments-table.component.html',
  styleUrls: [`calculated-field-arguments-table.component.scss`],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => CalculatedFieldArgumentsTableComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => CalculatedFieldArgumentsTableComponent),
      multi: true
    }
  ],
})
export class CalculatedFieldArgumentsTableComponent implements ControlValueAccessor, Validator, OnChanges {

  @Input() entityId: EntityId;
  @Input() tenantId: string;
  @Input() entityName: string;
  @Input() calculatedFieldType: CalculatedFieldType;

  errorText = '';
  argumentsFormArray = this.fb.array<AbstractControl>([]);

  readonly entityTypeTranslations = entityTypeTranslations;
  readonly ArgumentTypeTranslations = ArgumentTypeTranslations;
  readonly EntityType = EntityType;
  readonly ArgumentEntityType = ArgumentEntityType;
  readonly ArgumentType = ArgumentType;
  readonly CalculatedFieldType = CalculatedFieldType;

  private popoverComponent: TbPopoverComponent<CalculatedFieldArgumentPanelComponent>;
  private propagateChange: (argumentsObj: Record<string, CalculatedFieldArgument>) => void = () => {};

  constructor(
    private fb: FormBuilder,
    private popoverService: TbPopoverService,
    private viewContainerRef: ViewContainerRef,
    private cd: ChangeDetectorRef,
    private renderer: Renderer2
  ) {
    this.argumentsFormArray.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.propagateChange(this.getArgumentsObject());
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes.calculatedFieldType?.previousValue
      && changes.calculatedFieldType.currentValue !== changes.calculatedFieldType.previousValue) {
      this.argumentsFormArray.updateValueAndValidity();
    }
  }

  registerOnChange(fn: (argumentsObj: Record<string, CalculatedFieldArgument>) => void): void {
    this.propagateChange = fn;
  }

  registerOnTouched(_): void {}

  validate(): ValidationErrors | null {
    this.updateErrorText();
    return this.errorText ? { argumentsFormArray: false } : null;
  }

  onDelete(index: number): void {
    this.argumentsFormArray.removeAt(index);
    this.argumentsFormArray.markAsDirty();
  }

  manageArgument($event: Event, matButton: MatButton, index?: number): void {
    $event?.stopPropagation();
    if (this.popoverComponent && !this.popoverComponent.tbHidden) {
      this.popoverComponent.hide();
    }
    const trigger = matButton._elementRef.nativeElement;
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const argumentObj = this.argumentsFormArray.at(index)?.getRawValue() ?? {};
      const ctx = {
        index,
        argument: argumentObj,
        entityId: this.entityId,
        calculatedFieldType: this.calculatedFieldType,
        buttonTitle: this.argumentsFormArray.at(index)?.value ? 'action.apply' : 'action.add',
        tenantId: this.tenantId,
        entityName: this.entityName,
        usedArgumentNames: this.argumentsFormArray.getRawValue().map(({ argumentName }) => argumentName).filter(name => name !== argumentObj.argumentName),
      };
      this.popoverComponent = this.popoverService.displayPopover(trigger, this.renderer,
        this.viewContainerRef, CalculatedFieldArgumentPanelComponent, isDefined(index) ? 'left' : 'right', false, null,
        ctx,
        {},
        {}, {}, true);
      this.popoverComponent.tbComponentRef.instance.argumentsDataApplied.subscribe(({ value, index }) => {
        this.popoverComponent.hide();
        const formGroup = this.getArgumentFormGroup(value);
        if (isDefinedAndNotNull(index)) {
          this.argumentsFormArray.setControl(index, formGroup);
        } else {
          this.argumentsFormArray.push(formGroup);
        }
        formGroup.markAsDirty();
        this.cd.markForCheck();
      });
    }
  }

  private updateErrorText(): void {
    if (this.calculatedFieldType === CalculatedFieldType.SIMPLE
      && this.argumentsFormArray.controls.some(control => control.get('refEntityKey').get('type').value === ArgumentType.Rolling)) {
      this.errorText = 'calculated-fields.hint.arguments-simple-with-rolling';
    } else if (!this.argumentsFormArray.controls.length) {
      this.errorText = 'calculated-fields.hint.arguments-empty';
    } else {
      this.errorText = '';
    }
  }

  private getArgumentsObject(): Record<string, CalculatedFieldArgument> {
    return this.argumentsFormArray.getRawValue().reduce((acc, rawValue) => {
      const { argumentName, ...argument } = rawValue as CalculatedFieldArgumentValue;
      acc[argumentName] = argument;
      return acc;
    }, {} as Record<string, CalculatedFieldArgument>);
  }

  writeValue(argumentsObj: Record<string, CalculatedFieldArgument>): void {
    this.argumentsFormArray.clear();
    this.populateArgumentsFormArray(argumentsObj)
  }

  private populateArgumentsFormArray(argumentsObj: Record<string, CalculatedFieldArgument>): void {
    Object.keys(argumentsObj).forEach(key => {
      const value: CalculatedFieldArgumentValue = {
        ...argumentsObj[key],
        argumentName: key
      };
      this.argumentsFormArray.push(this.getArgumentFormGroup(value), {emitEvent: false});
    });
  }

  private getArgumentFormGroup(value: CalculatedFieldArgumentValue): FormGroup {
    return this.fb.group({
      ...value,
      argumentName: [{ value: value.argumentName, disabled: true }],
      ...(value.refEntityId ? {
        refEntityId: this.fb.group({
          entityType: [{ value: value.refEntityId.entityType, disabled: true }],
          id: [{ value: value.refEntityId.id , disabled: true }],
        }),
      } : {}),
      refEntityKey: this.fb.group({
        ...value.refEntityKey,
        type: [{ value: value.refEntityKey.type, disabled: true }],
        key: [{ value: value.refEntityKey.key, disabled: true }],
      }),
    })
  }
}
