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
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validator
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import {
  CircleSettings,
  CommonMapSettings,
  defaultCircleSettings,
  defaultCommonMapSettings,
  defaultMapEditorSettings,
  defaultMapProviderSettings,
  defaultMarkerClusteringSettings,
  defaultMarkersSettings,
  defaultPolygonSettings,
  MapEditorSettings,
  MapProviders,
  MapProviderSettings,
  MarkerClusteringSettings,
  MarkersSettings,
  PolygonSettings,
  PolylineSettings,
  UnitedMapSettings
} from '@home/components/widget/lib/maps/map-models';
import { extractType } from '@core/utils';
import { IAliasController } from '@core/api/widget-api.models';
import { Widget } from '@shared/models/widget.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

@Component({
  selector: 'tb-map-settings',
  templateUrl: './map-settings.component.html',
  styleUrls: ['./../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MapSettingsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => MapSettingsComponent),
      multi: true
    }
  ]
})
export class MapSettingsComponent extends PageComponent implements OnInit, ControlValueAccessor, Validator {

  @Input()
  disabled: boolean;

  @Input()
  aliasController: IAliasController;

  @Input()
  widget: Widget;

  @Input()
  routeMap = false;

  mapProvider = MapProviders;

  private modelValue: UnitedMapSettings;

  private propagateChange = null;

  public mapSettingsFormGroup: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private fb: UntypedFormBuilder,
              private destroyRef: DestroyRef) {
    super(store);
  }

  ngOnInit(): void {
    this.mapSettingsFormGroup = this.fb.group({
      mapProviderSettings: [null, []],
      commonMapSettings: [null, []],
      markersSettings: [null, []],
      polygonSettings: [null, []],
      circleSettings: [null, []],
    });
    if (this.routeMap) {
      this.mapSettingsFormGroup.addControl('routeMapSettings', this.fb.control(null, []));
    } else {
      this.mapSettingsFormGroup.addControl('markerClusteringSettings', this.fb.control(null, []));
    }
    this.mapSettingsFormGroup.addControl('mapEditorSettings', this.fb.control(null, []));
    this.mapSettingsFormGroup.get('mapProviderSettings').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateValidators(true);
    });
    this.mapSettingsFormGroup.get('markersSettings').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateValidators(true);
    });
    this.mapSettingsFormGroup.get('polygonSettings').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateValidators(true);
    });
    this.mapSettingsFormGroup.get('circleSettings').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateValidators(true);
    });
    this.mapSettingsFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateModel();
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
      this.mapSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.mapSettingsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: UnitedMapSettings): void {
    this.modelValue = value;
    const mapProviderSettings = extractType<MapProviderSettings>(value, Object.keys(defaultMapProviderSettings) as (keyof MapProviderSettings)[]);
    const commonMapSettings = extractType<CommonMapSettings>(value, Object.keys(defaultCommonMapSettings) as (keyof CommonMapSettings)[]);
    const markersSettings = extractType<MarkersSettings>(value, Object.keys(defaultMarkersSettings) as (keyof MarkersSettings)[]);
    const polygonSettings = extractType<PolygonSettings>(value, Object.keys(defaultPolygonSettings) as (keyof PolygonSettings)[]);
    const circleSettings = extractType<CircleSettings>(value, Object.keys(defaultCircleSettings) as (keyof CircleSettings)[]);
    const mapEditorSettings = extractType<MapEditorSettings>(value, Object.keys(defaultMapEditorSettings) as (keyof MapEditorSettings)[]);
    const formValue = {
      mapProviderSettings,
      commonMapSettings,
      markersSettings,
      polygonSettings,
      circleSettings,
      mapEditorSettings
    } as any;
    if (this.routeMap) {
      formValue.routeMapSettings = extractType<PolylineSettings>(value, ['strokeWeight', 'strokeOpacity']);
    } else {
      formValue.markerClusteringSettings = extractType<MarkerClusteringSettings>(value, Object.keys(defaultMarkerClusteringSettings) as (keyof MarkerClusteringSettings)[]);
    }
    this.mapSettingsFormGroup.patchValue( formValue, {emitEvent: false} );
    this.updateValidators(false);
  }

  public validate(c: UntypedFormControl) {
    return this.mapSettingsFormGroup.valid ? null : {
      mapSettings: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const value = this.mapSettingsFormGroup.value;
    this.modelValue = {
      ...value.mapProviderSettings,
      ...value.commonMapSettings,
      ...value.markersSettings,
      ...value.polygonSettings,
      ...value.circleSettings,
      ...value.mapEditorSettings
    };
    if (this.routeMap) {
      this.modelValue = {...this.modelValue, ...value.routeMapSettings};
    } else {
      this.modelValue = {...this.modelValue, ...value.markerClusteringSettings};
    }
    this.propagateChange(this.modelValue);
  }

  displayEditorSettings(): boolean {
    const markersSettings: MarkersSettings = this.mapSettingsFormGroup.get('markersSettings').value;
    const polygonSettings: PolygonSettings = this.mapSettingsFormGroup.get('polygonSettings').value;
    const circleSettings: CircleSettings = this.mapSettingsFormGroup.get('circleSettings').value;
    return markersSettings?.draggableMarker || polygonSettings?.editablePolygon || circleSettings?.editableCircle;
  }

  private updateValidators(emitEvent?: boolean): void {
    const displayEditorSettings = this.displayEditorSettings();
    if (displayEditorSettings) {
      this.mapSettingsFormGroup.get('mapEditorSettings').enable({emitEvent});
    } else {
      this.mapSettingsFormGroup.get('mapEditorSettings').disable({emitEvent});
    }
    if (!this.routeMap) {
      const mapProviderSettings: MapProviderSettings = this.mapSettingsFormGroup.get('mapProviderSettings').value;
      if (mapProviderSettings?.provider === MapProviders.image) {
        this.mapSettingsFormGroup.get('markerClusteringSettings').disable({emitEvent});
      } else {
        this.mapSettingsFormGroup.get('markerClusteringSettings').enable({emitEvent});
      }
      this.mapSettingsFormGroup.get('markerClusteringSettings').updateValueAndValidity({emitEvent: false});
    }
    this.mapSettingsFormGroup.get('mapEditorSettings').updateValueAndValidity({emitEvent: false});
  }
}
