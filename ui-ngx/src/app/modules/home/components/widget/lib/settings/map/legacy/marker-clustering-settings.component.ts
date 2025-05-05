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

import { Component, DestroyRef, forwardRef, Input, OnInit } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator,
  Validators
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { MarkerClusteringSettings } from '@home/components/widget/lib/maps-legacy/map-models';
import { WidgetService } from '@core/http/widget.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-marker-clustering-settings',
  templateUrl: './marker-clustering-settings.component.html',
  styleUrls: ['./../../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MarkerClusteringSettingsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MarkerClusteringSettingsComponent),
      multi: true
    }
  ]
})
export class MarkerClusteringSettingsComponent extends PageComponent implements OnInit, ControlValueAccessor, Validator {

  @Input()
  disabled: boolean;

  private modelValue: MarkerClusteringSettings;

  functionScopeVariables = this.widgetService.getWidgetScopeVariables();

  private propagateChange = null;

  public markerClusteringSettingsFormGroup: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private widgetService: WidgetService,
              private fb: UntypedFormBuilder,
              private destroyRef: DestroyRef) {
    super(store);
  }

  ngOnInit(): void {
    this.markerClusteringSettingsFormGroup = this.fb.group({
      useClusterMarkers: [null, []],
      zoomOnClick: [null, []],
      maxZoom: [null, [Validators.min(0), Validators.max(18)]],
      maxClusterRadius: [null, [Validators.min(0)]],
      animate: [null, []],
      spiderfyOnMaxZoom: [null, []],
      showCoverageOnHover: [null, []],
      chunkedLoading: [null, []],
      removeOutsideVisibleBounds: [null, []],
      useIconCreateFunction: [null, []],
      clusterMarkerFunction: [null, []]
    });
    this.markerClusteringSettingsFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateModel();
    });
    this.markerClusteringSettingsFormGroup.get('useClusterMarkers').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
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
      this.markerClusteringSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.markerClusteringSettingsFormGroup.enable({emitEvent: false});
      this.updateValidators(false);
    }
  }

  writeValue(value: MarkerClusteringSettings): void {
    this.modelValue = value;
    this.markerClusteringSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
    this.updateValidators(false);
  }

  public validate(c: UntypedFormControl) {
    return this.markerClusteringSettingsFormGroup.valid ? null : {
      markerClusteringSettings: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const value: MarkerClusteringSettings = this.markerClusteringSettingsFormGroup.value;
    this.modelValue = value;
    this.propagateChange(this.modelValue);
  }

  private updateValidators(emitEvent?: boolean): void {
    const useClusterMarkers: boolean = this.markerClusteringSettingsFormGroup.get('useClusterMarkers').value;

    this.markerClusteringSettingsFormGroup.disable({emitEvent: false});
    this.markerClusteringSettingsFormGroup.get('useClusterMarkers').enable({emitEvent: false});

    if (useClusterMarkers) {
      this.markerClusteringSettingsFormGroup.enable({emitEvent: false});
    }
    this.markerClusteringSettingsFormGroup.updateValueAndValidity({emitEvent: false});
  }
}
