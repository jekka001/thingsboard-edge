///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2023 ThingsBoard, Inc. All Rights Reserved.
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

import { AfterViewInit, Component, ElementRef, forwardRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ControlValueAccessor, UntypedFormBuilder, UntypedFormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { Observable, of, ReplaySubject, Subscription, throwError } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  switchMap,
  tap,
  share,
} from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { DeviceProfileService } from '@core/http/device-profile.service';
import { EntityType } from '@app/shared/models/entity-type.models';
import { BroadcastService } from '@app/core/services/broadcast.service';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { AssetProfileService } from '@core/http/asset-profile.service';
import { EntityViewService } from '@core/http/entity-view.service';
import { EdgeService } from '@core/http/edge.service';
import { MatFormFieldAppearance } from '@angular/material/form-field';
import { EntityService } from '@core/http/entity.service';

@Component({
  selector: 'tb-entity-subtype-autocomplete',
  templateUrl: './entity-subtype-autocomplete.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => EntitySubTypeAutocompleteComponent),
    multi: true
  }]
})
export class EntitySubTypeAutocompleteComponent implements ControlValueAccessor, OnInit, AfterViewInit, OnDestroy {

  subTypeFormGroup: UntypedFormGroup;

  modelValue: string | null;

  @Input()
  entityType: EntityType;

  private requiredValue: boolean;

  get required(): boolean {
    return this.requiredValue;
  }

  @Input()
  set required(value: boolean) {
    this.requiredValue = coerceBooleanProperty(value);
  }

  @Input()
  disabled: boolean;

  @Input()
  excludeSubTypes: Array<string>;

  @Input()
  appearance: MatFormFieldAppearance = 'fill';

  @ViewChild('subTypeInput', {static: true}) subTypeInput: ElementRef;

  selectEntitySubtypeText: string;
  entitySubtypeText: string;
  entitySubtypeRequiredText: string;
  entitySubtypeMaxLength: string;

  filteredSubTypes: Observable<Array<string>>;

  subTypes: Observable<Array<string>>;

  private broadcastSubscription: Subscription;

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              private broadcast: BroadcastService,
              public translate: TranslateService,
              private deviceProfileService: DeviceProfileService,
              private assetProfileService: AssetProfileService,
              private entityViewService: EntityViewService,
              private edgeService: EdgeService,
              private fb: UntypedFormBuilder,
              private entityService: EntityService) {
    this.subTypeFormGroup = this.fb.group({
      subType: [null, Validators.maxLength(255)]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {

    switch (this.entityType) {
      case EntityType.ASSET:
        this.selectEntitySubtypeText = 'asset.select-asset-type';
        this.entitySubtypeText = 'asset.asset-type';
        this.entitySubtypeRequiredText = 'asset.asset-type-required';
        this.entitySubtypeMaxLength = 'asset.asset-type-max-length';
        this.broadcastSubscription = this.broadcast.on('assetSaved', () => {
          this.subTypes = null;
        });
        break;
      case EntityType.DEVICE:
        this.selectEntitySubtypeText = 'device.select-device-type';
        this.entitySubtypeText = 'device.device-type';
        this.entitySubtypeRequiredText = 'device.device-type-required';
        this.entitySubtypeMaxLength = 'device.device-type-max-length';
        this.broadcastSubscription = this.broadcast.on('deviceSaved', () => {
          this.subTypes = null;
        });
        break;
      case EntityType.EDGE:
        this.selectEntitySubtypeText = 'edge.select-edge-type';
        this.entitySubtypeText = 'edge.edge-type';
        this.entitySubtypeRequiredText = 'edge.edge-type-required';
        this.entitySubtypeMaxLength = 'edge.type-max-length';
        this.broadcastSubscription = this.broadcast.on('edgeSaved', () => {
          this.subTypes = null;
        });
        break;
      case EntityType.ENTITY_VIEW:
        this.selectEntitySubtypeText = 'entity-view.select-entity-view-type';
        this.entitySubtypeText = 'entity-view.entity-view-type';
        this.entitySubtypeRequiredText = 'entity-view.entity-view-type-required';
        this.entitySubtypeMaxLength = 'entity-view.type-max-length'
        this.broadcastSubscription = this.broadcast.on('entityViewSaved', () => {
          this.subTypes = null;
        });
        break;
    }

    this.filteredSubTypes = this.subTypeFormGroup.get('subType').valueChanges
      .pipe(
        debounceTime(150),
        distinctUntilChanged(),
        tap(value => {
          this.updateView(value);
        }),
        // startWith<string | EntitySubtype>(''),
        map(value => value ? value : ''),
        switchMap(type => this.fetchSubTypes(type))
      );
  }

  ngAfterViewInit(): void {
  }

  ngOnDestroy(): void {
    if (this.broadcastSubscription) {
      this.broadcastSubscription.unsubscribe();
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.subTypeFormGroup.disable({emitEvent: false});
    } else {
      this.subTypeFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: string | null): void {
    this.searchText = '';
    this.modelValue = value;
    this.subTypeFormGroup.get('subType').patchValue(value, {emitEvent: false});
    this.dirty = true;
  }

  onFocus() {
    if (this.dirty) {
      this.subTypeFormGroup.get('subType').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  updateView(value: string | null) {
    if (this.modelValue !== value) {
      this.modelValue = value;
      this.propagateChange(this.modelValue);
    }
  }

  displaySubTypeFn(subType?: string): string | undefined {
    return subType ? subType : undefined;
  }

  fetchSubTypes(searchText?: string, strictMatch: boolean = false): Observable<Array<string>> {
    this.searchText = searchText;
    return this.getSubTypes().pipe(
      map(subTypes => subTypes.filter(subType => {
        if (strictMatch) {
          return searchText ? subType === searchText : false;
        } else {
          return searchText ? subType.toUpperCase().startsWith(searchText.toUpperCase()) : true;
        }
      }))
    );
  }

  getSubTypes(): Observable<Array<string>> {
    if (!this.subTypes) {
      const subTypesObservable = this.entityService.getEntitySubtypesObservable(this.entityType);
      if (subTypesObservable) {
        const excludeSubTypesSet = new Set(this.excludeSubTypes);
        this.subTypes = subTypesObservable.pipe(
          catchError(() => of([] as Array<string>)),
          map(subTypes => {
            const filteredSubTypes: Array<string> = [];
            subTypes.forEach(subType => !excludeSubTypesSet.has(subType) && filteredSubTypes.push(subType));
            return filteredSubTypes;
          }),
          share({
            connector: () => new ReplaySubject(1),
            resetOnError: false,
            resetOnComplete: false,
            resetOnRefCountZero: false,
          })
        );
      } else {
        return throwError(null);
      }
    }
    return this.subTypes;
  }

  clear() {
    this.subTypeFormGroup.get('subType').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.subTypeInput.nativeElement.blur();
      this.subTypeInput.nativeElement.focus();
    }, 0);
  }

}
