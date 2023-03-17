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

import { Component, ElementRef, forwardRef, Input, OnInit, ViewChild } from '@angular/core';
import {
  ControlValueAccessor,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  Validator
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { ImageMapProviderSettings } from '@home/components/widget/lib/maps/map-models';
import { IAliasController } from '@core/api/widget-api.models';
import { Observable, of } from 'rxjs';
import { catchError, map, mergeMap, publishReplay, refCount, startWith, tap } from 'rxjs/operators';
import { DataKey } from '@shared/models/widget.models';
import { DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { EntityService } from '@core/http/entity.service';

@Component({
  selector: 'tb-image-map-provider-settings',
  templateUrl: './image-map-provider-settings.component.html',
  styleUrls: ['./../widget-settings.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ImageMapProviderSettingsComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => ImageMapProviderSettingsComponent),
      multi: true
    }
  ]
})
export class ImageMapProviderSettingsComponent extends PageComponent implements OnInit, ControlValueAccessor, Validator {

  @ViewChild('entityAliasInput') entityAliasInput: ElementRef;

  @ViewChild('keyInput') keyInput: ElementRef;

  @Input()
  disabled: boolean;

  @Input()
  aliasController: IAliasController;

  private modelValue: ImageMapProviderSettings;

  private propagateChange = null;

  public providerSettingsFormGroup: UntypedFormGroup;

  filteredEntityAliases: Observable<Array<string>>;
  aliasSearchText = '';

  filteredKeys: Observable<Array<string>>;
  keySearchText = '';

  private latestKeySearchResult: Array<string> = null;
  private keysFetchObservable$: Observable<Array<string>> = null;

  private entityAliasList: Array<string> = [];

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private entityService: EntityService,
              private fb: UntypedFormBuilder) {
    super(store);
  }

  ngOnInit(): void {
    this.providerSettingsFormGroup = this.fb.group({
      mapImageUrl: [null, []],
      imageEntityAlias: [null, []],
      imageUrlAttribute: [null, []]
    });
    this.providerSettingsFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });

    this.filteredEntityAliases = this.providerSettingsFormGroup.get('imageEntityAlias').valueChanges
      .pipe(
        tap((value) => {
          if (this.modelValue?.imageEntityAlias !== value) {
            this.latestKeySearchResult = null;
            this.keysFetchObservable$ = null;
            this.providerSettingsFormGroup.get('imageUrlAttribute').setValue(this.providerSettingsFormGroup.get('imageUrlAttribute').value);
          }
        }),
        map(value => value ? value : ''),
        mergeMap(name => this.fetchEntityAliases(name) )
      );

    this.filteredKeys = this.providerSettingsFormGroup.get('imageUrlAttribute').valueChanges
      .pipe(
        map(value => value ? value : ''),
        mergeMap(name => this.fetchKeys(name) )
      );

    if (this.aliasController) {
      const entityAliases = this.aliasController.getEntityAliases();
      for (const aliasId of Object.keys(entityAliases)) {
        this.entityAliasList.push(entityAliases[aliasId].alias);
      }
    }
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (isDisabled) {
      this.providerSettingsFormGroup.disable({emitEvent: false});
    } else {
      this.providerSettingsFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: ImageMapProviderSettings): void {
    this.modelValue = value;
    this.providerSettingsFormGroup.patchValue(
      value, {emitEvent: false}
    );
  }

  public validate(c: UntypedFormControl) {
    return this.providerSettingsFormGroup.valid ? null : {
      imageMapProviderSettings: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const value: ImageMapProviderSettings = this.providerSettingsFormGroup.value;
    this.modelValue = value;
    this.propagateChange(this.modelValue);
  }

  clearEntityAlias() {
    this.providerSettingsFormGroup.get('imageEntityAlias').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.entityAliasInput.nativeElement.blur();
      this.entityAliasInput.nativeElement.focus();
    }, 0);
  }

  onEntityAliasFocus() {
    this.providerSettingsFormGroup.get('imageEntityAlias').updateValueAndValidity({onlySelf: true, emitEvent: true});
  }

  clearKey() {
    this.providerSettingsFormGroup.get('imageUrlAttribute').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.keyInput.nativeElement.blur();
      this.keyInput.nativeElement.focus();
    }, 0);
  }

  onKeyFocus() {
    this.providerSettingsFormGroup.get('imageUrlAttribute').updateValueAndValidity({onlySelf: true, emitEvent: true});
  }

  private fetchEntityAliases(searchText?: string): Observable<Array<string>> {
    this.aliasSearchText = searchText;
    let result = this.entityAliasList;
    if (searchText && searchText.length) {
      result = this.entityAliasList.filter((entityAlias) => entityAlias.toLowerCase().includes(searchText.toLowerCase()));
    }
    return of(result);
  }

  private fetchKeys(searchText?: string): Observable<Array<string>> {
    if (this.keySearchText !== searchText || this.latestKeySearchResult === null) {
      this.keySearchText = searchText;
      const dataKeyFilter = this.createKeyFilter(this.keySearchText);
      return this.getKeys().pipe(
        map(name => name.filter(dataKeyFilter)),
        tap(res => this.latestKeySearchResult = res)
      );
    }
    return of(this.latestKeySearchResult);
  }

  private getKeys() {
    if (this.keysFetchObservable$ === null) {
      let fetchObservable: Observable<Array<DataKey>>;
      let entityAliasId: string;
      const entityAlias: string = this.providerSettingsFormGroup.get('imageEntityAlias').value;
      if (entityAlias && this.aliasController) {
        entityAliasId = this.aliasController.getEntityAliasId(entityAlias);
      }
      if (entityAliasId) {
        const dataKeyTypes = [DataKeyType.attribute];
        fetchObservable = this.fetchEntityKeys(entityAliasId, dataKeyTypes);
      } else {
        fetchObservable = of([]);
      }
      this.keysFetchObservable$ = fetchObservable.pipe(
        map((dataKeys) => dataKeys.map((dataKey) => dataKey.name)),
        publishReplay(1),
        refCount()
      );
    }
    return this.keysFetchObservable$;
  }

  private fetchEntityKeys(entityAliasId: string, dataKeyTypes: Array<DataKeyType>): Observable<Array<DataKey>> {
    return this.aliasController.getAliasInfo(entityAliasId).pipe(
      mergeMap((aliasInfo) => {
        return this.entityService.getEntityKeysByEntityFilter(
          aliasInfo.entityFilter,
          dataKeyTypes,
          {ignoreLoading: true, ignoreErrors: true}
        ).pipe(
          catchError(() => of([]))
        );
      }),
      catchError(() => of([] as Array<DataKey>))
    );
  }

  private createKeyFilter(query: string): (key: string) => boolean {
    const lowercaseQuery = query.toLowerCase();
    return key => key.toLowerCase().startsWith(lowercaseQuery);
  }
}
