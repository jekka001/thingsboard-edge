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

import {
  AfterViewInit,
  Component,
  ComponentRef,
  forwardRef,
  Input,
  NgModuleRef,
  OnChanges,
  OnDestroy,
  OnInit,
  SimpleChanges,
  Type,
  ViewChild,
  ViewContainerRef
} from '@angular/core';
import {
  AbstractControl, AsyncValidator,
  ControlValueAccessor,
  NG_ASYNC_VALIDATORS,
  NG_VALUE_ACCESSOR,
  ValidationErrors, Validator
} from '@angular/forms';
import { Observable, of, Subject } from 'rxjs';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { SchedulerEventConfiguration } from '@shared/models/scheduler-event.models';
import { cloneMetadata, deepClone } from '@core/utils';
import {
  DynamicComponentFactoryService,
  DynamicComponentModule
} from '@core/services/dynamic-component-factory.service';
import { CustomSchedulerEventConfigComponent } from '@home/components/scheduler/config/custom-scheduler-event-config.component';
import { SharedModule } from '@shared/shared.module';
import { SchedulerEventConfigType } from '@home/components/scheduler/scheduler-event-config.models';
import { map, tap } from 'rxjs/operators';
import { OtaUpdateEventConfigComponent } from '@home/components/scheduler/config/ota-update-event-config.component';

@Component({
  selector: 'tb-scheduler-event-template-config',
  template: '<ng-container #configContent></ng-container>',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => SchedulerEventTemplateConfigComponent),
    multi: true
  },
  {
    provide: NG_ASYNC_VALIDATORS,
    useExisting: forwardRef(() => SchedulerEventTemplateConfigComponent),
    multi: true
  }]
})
export class SchedulerEventTemplateConfigComponent implements ControlValueAccessor, OnInit, AfterViewInit, OnChanges, OnDestroy, AsyncValidator {

  @ViewChild('configContent', {read: ViewContainerRef, static: true}) configContentContainer: ViewContainerRef;

  private configuration: SchedulerEventConfiguration | null;

  @Input()
  disabled: boolean;

  @Input()
  schedulerEventConfigTypes: {[eventType: string]: SchedulerEventConfigType};

  @Input()
  schedulerEventType: string;

  private customSchedulerEventComponentType: Type<CustomSchedulerEventConfigComponent>;
  private configComponentRef: ComponentRef<ControlValueAccessor & Validator>;
  private configComponent: ControlValueAccessor & Validator;

  private configTemplate: string;

  private configComponent$: Subject<ControlValueAccessor & Validator> = null;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private dynamicComponentFactoryService: DynamicComponentFactoryService) {
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.loadTemplate();
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'schedulerEventType') {
          this.loadTemplate();
        }
      }
    }
  }

  private loadTemplate() {
    if (this.configComponentRef) {
      this.configComponentRef.destroy();
      this.configComponentRef = null;
      this.configComponent = null;
    }
    if (this.customSchedulerEventComponentType) {
      this.dynamicComponentFactoryService.destroyDynamicComponent(this.customSchedulerEventComponentType);
      this.customSchedulerEventComponentType = null;
    }
    if (this.schedulerEventType) {
      this.configTemplate = '<div>Not defined!</div>';
      let componentType: Type<ControlValueAccessor>;
      const configType = this.schedulerEventConfigTypes[this.schedulerEventType];
      if (configType) {
        if (configType.componentType) {
          componentType = configType.componentType;
          // template = `<${selector} [(ngModel)]="configuration"></${selector}>`;
        } else if (configType.template) {
          this.configTemplate = configType.template;
        }
      }
      this.resolveComponent(componentType, this.configTemplate).subscribe((data) => {
        this.configContentContainer.clear();
        const options = {} as any;
        if (data[1]) {
          options.ngModuleRef = data[1];
        }
        this.configComponentRef = this.configContentContainer
          .createComponent(data[0], options) as ComponentRef<ControlValueAccessor & Validator>;
        this.configComponent = this.configComponentRef.instance;
        if (this.configComponent instanceof OtaUpdateEventConfigComponent) {
          this.configComponent.schedulerEventType = this.schedulerEventType;
        }
        this.configComponent.registerOnChange((configuration: SchedulerEventConfiguration) => {
          this.updateModel(configuration);
        });
        this.configComponent.setDisabledState(this.disabled);
        this.configComponent.writeValue(this.configuration);
      });
    }
  }

  private resolveComponent(componentType: Type<ControlValueAccessor>,
                           template: string): Observable<[Type<ControlValueAccessor|OtaUpdateEventConfigComponent>,
                                                          NgModuleRef<DynamicComponentModule>]> {
    if (componentType) {
      return of([componentType, null]);
    } else if (template) {
      class CustomSchedulerEventConfigComponentInstance extends CustomSchedulerEventConfigComponent {
      }
      cloneMetadata(CustomSchedulerEventConfigComponent, CustomSchedulerEventConfigComponentInstance);
      return this.dynamicComponentFactoryService.createDynamicComponent(
        CustomSchedulerEventConfigComponentInstance,
        template,
        [SharedModule]).pipe(
        tap((componentData) => {
          this.customSchedulerEventComponentType = componentData.componentType;
        }),
        map((componentData) => [componentData.componentType, componentData.componentModuleRef])
      );
    } else {
      return of([null, null]);
    }
  }

  ngAfterViewInit(): void {
  }

  ngOnDestroy(): void {
    if (this.configComponentRef) {
      this.configComponentRef.destroy();
    }
    if (this.customSchedulerEventComponentType) {
      this.dynamicComponentFactoryService.destroyDynamicComponent(this.customSchedulerEventComponentType);
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.configComponent) {
      this.configComponent.setDisabledState(isDisabled);
    }
  }

  writeValue(value: SchedulerEventConfiguration | null): void {
    this.configuration = deepClone(value);
    if (this.configComponent) {
      this.configComponent.writeValue(this.configuration);
    }
  }

  private updateModel(configuration: SchedulerEventConfiguration) {
    this.propagateChange(configuration);
  }

  validate(control: AbstractControl) : Observable<ValidationErrors | null> {
    let comp$: Observable<ControlValueAccessor & Validator>;
    if (this.configComponent) {
      comp$ = of(this.configComponent);
    } else {
      if (this.configTemplate) {
        this.configComponent$ = new Subject();
        comp$ = this.configComponent$;
      } else {
        comp$ = of(null);
      }
    }

    return comp$.pipe(
      map((comp) => this.doValidate(comp, control))
    );
  }

  private doValidate(configComponent?: ControlValueAccessor & Validator, control?: AbstractControl): ValidationErrors | null {
    if (configComponent) {
      return configComponent.validate(control);
    }

    return null;
  }

}
