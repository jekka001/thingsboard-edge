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
  DestroyRef,
  ElementRef,
  EventEmitter,
  forwardRef,
  Input,
  OnInit,
  Output,
  Renderer2,
  ViewChild,
  ViewContainerRef,
  ViewEncapsulation
} from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validator,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { deepClone } from '@core/utils';
import { MatButton } from '@angular/material/button';
import { TbPopoverService } from '@shared/components/popover.service';
import {
  defaultPropertyValue,
  FormProperty,
  FormPropertyType,
  formPropertyTypes,
  formPropertyTypeTranslations, propertyValid
} from '@shared/models/dynamic-form.models';
import {
  DynamicFormPropertiesComponent
} from '@home/components/widget/lib/settings/common/dynamic-form/dynamic-form-properties.component';
import {
  DynamicFormPropertyPanelComponent
} from '@home/components/widget/lib/settings/common/dynamic-form/dynamic-form-property-panel.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-dynamic-form-property-row',
  templateUrl: './dynamic-form-property-row.component.html',
  styleUrls: ['./dynamic-form-property-row.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DynamicFormPropertyRowComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => DynamicFormPropertyRowComponent),
      multi: true
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class DynamicFormPropertyRowComponent implements ControlValueAccessor, OnInit, Validator {

  @ViewChild('idInput')
  idInput: ElementRef<HTMLInputElement>;

  @ViewChild('editButton')
  editButton: MatButton;

  formPropertyTypes = formPropertyTypes;
  formPropertyTypeTranslations = formPropertyTypeTranslations;

  @Input()
  disabled: boolean;

  @Input()
  index: number;

  @Input()
  booleanPropertyIds: string[];

  @Output()
  propertyRemoved = new EventEmitter();

  propertyRowFormGroup: UntypedFormGroup;

  modelValue: FormProperty;

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private cd: ChangeDetectorRef,
              private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef,
              private propertiesComponent: DynamicFormPropertiesComponent,
              private destroyRef: DestroyRef) {
  }

  ngOnInit() {
    this.propertyRowFormGroup = this.fb.group({
      id: [null, [this.propertyIdValidator()]],
      name: [null, [Validators.required]],
      type: [null, [Validators.required]]
    });
    this.propertyRowFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(
      () => this.updateModel()
    );
    this.propertyRowFormGroup.get('type').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe((newType: FormPropertyType) => {
      this.onTypeChanged(newType);
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(_fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.propertyRowFormGroup.disable({emitEvent: false});
    } else {
      this.propertyRowFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: FormProperty): void {
    this.modelValue = value;
    this.propertyRowFormGroup.patchValue(
      {
        id: value?.id,
        name: value?.name,
        type: value?.type
      }, {emitEvent: false}
    );
    this.cd.markForCheck();
  }

  editProperty($event: Event, matButton: MatButton, add = false, editCanceled = () => {}) {
    if ($event) {
      $event.stopPropagation();
    }
    const trigger = matButton._elementRef.nativeElement;
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const dynamicFormPropertyPanelPopover = this.popoverService.displayPopover({
        trigger,
        renderer: this.renderer,
        componentType: DynamicFormPropertyPanelComponent,
        hostView: this.viewContainerRef,
        preferredPlacement: ['leftOnly', 'leftTopOnly', 'leftBottomOnly'],
        context: {
          isAdd: add,
          disabled: this.disabled,
          booleanPropertyIds: this.booleanPropertyIds,
          property: deepClone(this.modelValue)
        },
        isModal: true
      });
      dynamicFormPropertyPanelPopover.tbComponentRef.instance.popover = dynamicFormPropertyPanelPopover;
      dynamicFormPropertyPanelPopover.tbComponentRef.instance.propertySettingsApplied.subscribe((property) => {
        dynamicFormPropertyPanelPopover.hide();
        this.propertyRowFormGroup.patchValue(
          {
            id: property.id,
            name: property.name,
            type: property.type
          }, {emitEvent: false}
        );
        this.modelValue = property;
        this.propagateChange(this.modelValue);
      });
      dynamicFormPropertyPanelPopover.tbDestroy.subscribe(() => {
        if (!propertyValid(this.modelValue)) {
          editCanceled();
        }
      });
    }
  }

  focus() {
    this.idInput.nativeElement.scrollIntoView();
    this.idInput.nativeElement.focus();
  }

  onAdd(onCanceled: () => void) {
    this.idInput.nativeElement.scrollIntoView();
    this.editProperty(null, this.editButton, true, onCanceled);
  }

  public validate(_c: UntypedFormControl) {
    const idControl = this.propertyRowFormGroup.get('id');
    if (idControl.hasError('propertyIdNotUnique')) {
      idControl.updateValueAndValidity({onlySelf: false, emitEvent: false});
    }
    if (idControl.hasError('propertyIdNotUnique')) {
      this.propertyRowFormGroup.get('id').markAsTouched();
      return {
        propertyIdNotUnique: true
      };
    }
    const property: FormProperty = {...this.modelValue, ...this.propertyRowFormGroup.value};
    if (!propertyValid(property)) {
      return {
        property: true
      };
    }
    return null;
  }

  private propertyIdValidator(): ValidatorFn {
    return control => {
      if (!control.value) {
        return {
          required: true
        };
      }
      if (!this.propertiesComponent.propertyIdUnique(control.value, this.index)) {
        return {
          propertyIdNotUnique: true
        };
      }
      return null;
    };
  }

  private onTypeChanged(newType: FormPropertyType) {
    this.modelValue = {...this.modelValue, ...{type: newType}};
    this.modelValue.default = defaultPropertyValue(newType);
  }

  private updateModel() {
    const value: FormProperty = this.propertyRowFormGroup.value;
    this.modelValue = {...this.modelValue, ...value};
    this.propagateChange(this.modelValue);
  }

}
