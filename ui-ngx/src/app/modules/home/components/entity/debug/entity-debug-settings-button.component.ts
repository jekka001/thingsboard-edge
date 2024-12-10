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
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  forwardRef,
  Input,
  Renderer2,
  signal,
  ViewContainerRef
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { DurationLeftPipe } from '@shared/pipe/duration-left.pipe';
import { TbPopoverService } from '@shared/components/popover.service';
import { MatButton } from '@angular/material/button';
import { EntityDebugSettingsPanelComponent } from './entity-debug-settings-panel.component';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { of, shareReplay, timer } from 'rxjs';
import { SECOND, MINUTE } from '@shared/models/time/time.models';
import { EntityDebugSettings } from '@shared/models/entity.models';
import { map, switchMap, takeWhile } from 'rxjs/operators';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { ControlValueAccessor, FormBuilder, NG_VALUE_ACCESSOR } from '@angular/forms';

@Component({
  selector: 'tb-entity-debug-settings-button',
  templateUrl: './entity-debug-settings-button.component.html',
  standalone: true,
  imports: [
    CommonModule,
    SharedModule,
    DurationLeftPipe,
  ],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EntityDebugSettingsButtonComponent),
      multi: true
    },
  ],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EntityDebugSettingsButtonComponent implements ControlValueAccessor {

  @Input() debugLimitsConfiguration: string;

  debugSettingsFormGroup = this.fb.group({
    failuresEnabled: [false],
    allEnabled: [false],
    allEnabledUntil: []
  });

  disabled = false;
  allEnabled = signal(false);

  isDebugAllActive$ = toObservable(this.allEnabled).pipe(
    switchMap((value) => {
      if (value) {
        return of(true);
      } else {
        return timer(0, SECOND).pipe(
          map(() => this.allEnabledUntil > new Date().getTime()),
          takeWhile(value => value, true)
        );
      }
    }),
    takeUntilDestroyed(),
    shareReplay(1)
  );

  readonly maxDebugModeDuration = getCurrentAuthState(this.store).maxDebugModeDurationMinutes * MINUTE;

  private propagateChange: (settings: EntityDebugSettings) => void = () => {};

  constructor(private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private store: Store<AppState>,
              private viewContainerRef: ViewContainerRef,
              private fb: FormBuilder,
              private cd : ChangeDetectorRef,
  ) {
    this.debugSettingsFormGroup.valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe(value => {
      this.propagateChange(value);
    });

    this.debugSettingsFormGroup.get('allEnabled').valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe(value => this.allEnabled.set(value));
  }

  get failuresEnabled(): boolean {
    return this.debugSettingsFormGroup.get('failuresEnabled').value;
  }

  get allEnabledUntil(): number {
    return this.debugSettingsFormGroup.get('allEnabledUntil').value;
  }

  openDebugStrategyPanel($event: Event, matButton: MatButton): void {
    if ($event) {
      $event.stopPropagation();
    }
    const trigger = matButton._elementRef.nativeElement;
    const debugSettings = this.debugSettingsFormGroup.value;

    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const debugStrategyPopover = this.popoverService.displayPopover(trigger, this.renderer,
        this.viewContainerRef, EntityDebugSettingsPanelComponent, 'bottom', true, null,
        {
          ...debugSettings,
          maxDebugModeDuration: this.maxDebugModeDuration,
          debugLimitsConfiguration: this.debugLimitsConfiguration
        },
        {},
        {}, {}, true);
      debugStrategyPopover.tbComponentRef.instance.popover = debugStrategyPopover;
      debugStrategyPopover.tbComponentRef.instance.onSettingsApplied.subscribe((settings: EntityDebugSettings) => {
        this.debugSettingsFormGroup.patchValue(settings);
        this.cd.markForCheck();
        debugStrategyPopover.hide();
      });
    }
  }

  registerOnChange(fn: (settings: EntityDebugSettings) => void): void {
    this.propagateChange = fn;
  }

  registerOnTouched(_: () => void): void {}

  writeValue(settings: EntityDebugSettings): void {
    this.debugSettingsFormGroup.patchValue(settings, {emitEvent: false});
    this.allEnabled.set(settings?.allEnabled);
    this.debugSettingsFormGroup.get('allEnabled').updateValueAndValidity({onlySelf: true});
    this.cd.markForCheck();
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.debugSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.debugSettingsFormGroup.enable({emitEvent: false});
    }
  }
}
