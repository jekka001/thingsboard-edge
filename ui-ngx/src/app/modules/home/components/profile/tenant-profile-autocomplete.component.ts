///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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

import { Component, ElementRef, EventEmitter, forwardRef, Input, OnInit, Output, ViewChild } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable } from 'rxjs';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { map, mergeMap, share, startWith, tap } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { TenantProfileId } from '@shared/models/id/tenant-profile-id';
import { EntityInfoData } from '@shared/models/entity.models';
import { TenantProfileService } from '@core/http/tenant-profile.service';
import { entityIdEquals } from '@shared/models/id/entity-id';
import { TruncatePipe } from '@shared//pipe/truncate.pipe';
import { ENTER } from '@angular/cdk/keycodes';
import { TenantProfile } from '@shared/models/tenant.model';
import { MatDialog } from '@angular/material/dialog';
import { TenantProfileDialogComponent, TenantProfileDialogData } from './tenant-profile-dialog.component';

@Component({
  selector: 'tb-tenant-profile-autocomplete',
  templateUrl: './tenant-profile-autocomplete.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => TenantProfileAutocompleteComponent),
    multi: true
  }]
})
export class TenantProfileAutocompleteComponent implements ControlValueAccessor, OnInit {

  selectTenantProfileFormGroup: FormGroup;

  modelValue: TenantProfileId | null;

  @Input()
  selectDefaultProfile = false;

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

  @Output()
  tenantProfileUpdated = new EventEmitter<TenantProfileId>();

  @ViewChild('tenantProfileInput', {static: true}) tenantProfileInput: ElementRef;

  filteredTenantProfiles: Observable<Array<EntityInfoData>>;

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              public truncate: TruncatePipe,
              private tenantProfileService: TenantProfileService,
              private fb: FormBuilder,
              private dialog: MatDialog) {
    this.selectTenantProfileFormGroup = this.fb.group({
      tenantProfile: [null]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredTenantProfiles = this.selectTenantProfileFormGroup.get('tenantProfile').valueChanges
      .pipe(
        tap((value: EntityInfoData | string) => {
          let modelValue: TenantProfileId | null;
          if (typeof value === 'string' || !value) {
            modelValue = null;
          } else {
            modelValue = new TenantProfileId(value.id.id);
          }
          this.updateView(modelValue);
        }),
        map(value => value ? (typeof value === 'string' ? value : value.name) : ''),
        mergeMap(name => this.fetchTenantProfiles(name) ),
        share()
      );
  }

  selectDefaultTenantProfileIfNeeded(): void {
    if (this.selectDefaultProfile && !this.modelValue) {
      this.tenantProfileService.getDefaultTenantProfileInfo().subscribe(
        (profile) => {
          if (profile) {
            this.modelValue = new TenantProfileId(profile.id.id);
            this.selectTenantProfileFormGroup.get('tenantProfile').patchValue(profile, {emitEvent: false});
            this.propagateChange(this.modelValue);
          }
        }
      );
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  writeValue(value: TenantProfileId | null): void {
    this.searchText = '';
    if (value != null) {
      this.tenantProfileService.getTenantProfileInfo(value.id).subscribe(
        (profile) => {
          this.modelValue = new TenantProfileId(profile.id.id);
          this.selectTenantProfileFormGroup.get('tenantProfile').patchValue(profile, {emitEvent: false});
        }
      );
    } else {
      this.modelValue = null;
      this.selectTenantProfileFormGroup.get('tenantProfile').patchValue(null, {emitEvent: false});
      this.selectDefaultTenantProfileIfNeeded();
    }
    this.dirty = true;
  }

  onFocus() {
    if (this.dirty) {
      this.selectTenantProfileFormGroup.get('tenantProfile').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  updateView(value: TenantProfileId | null) {
    if (!entityIdEquals(this.modelValue, value)) {
      this.modelValue = value;
      this.propagateChange(this.modelValue);
    }
  }

  displayTenantProfileFn(profile?: EntityInfoData): string | undefined {
    return profile ? profile.name : undefined;
  }

  fetchTenantProfiles(searchText?: string): Observable<Array<EntityInfoData>> {
    this.searchText = searchText;
    const pageLink = new PageLink(10, 0, searchText, {
      property: 'name',
      direction: Direction.ASC
    });
    return this.tenantProfileService.getTenantProfileInfos(pageLink, {ignoreLoading: true}).pipe(
      map(pageData => {
        return pageData.data;
      })
    );
  }

  clear() {
    this.selectTenantProfileFormGroup.get('tenantProfile').patchValue(null, {emitEvent: true});
    setTimeout(() => {
      this.tenantProfileInput.nativeElement.blur();
      this.tenantProfileInput.nativeElement.focus();
    }, 0);
  }

  textIsNotEmpty(text: string): boolean {
    return (text && text.length > 0);
  }

  tenantProfileEnter($event: KeyboardEvent) {
    if ($event.keyCode === ENTER) {
      $event.preventDefault();
      if (!this.modelValue) {
        this.createTenantProfile($event, this.searchText);
      }
    }
  }

  createTenantProfile($event: Event, profileName: string) {
    $event.preventDefault();
    const tenantProfile: TenantProfile = {
      id: null,
      name: profileName
    };
    this.openTenantProfileDialog(tenantProfile, true);
  }

  editTenantProfile($event: Event) {
    $event.preventDefault();
    this.tenantProfileService.getTenantProfile(this.modelValue.id).subscribe(
      (tenantProfile) => {
        this.openTenantProfileDialog(tenantProfile, false);
      }
    );
  }

  openTenantProfileDialog(tenantProfile: TenantProfile, isAdd: boolean) {
    this.dialog.open<TenantProfileDialogComponent, TenantProfileDialogData,
      TenantProfile>(TenantProfileDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        isAdd,
        tenantProfile
      }
    }).afterClosed().subscribe(
      (savedTenantProfile) => {
        if (!savedTenantProfile) {
          setTimeout(() => {
            this.tenantProfileInput.nativeElement.blur();
            this.tenantProfileInput.nativeElement.focus();
          }, 0);
        } else {
          this.tenantProfileService.getTenantProfileInfo(savedTenantProfile.id.id).subscribe(
            (profile) => {
              this.modelValue = new TenantProfileId(profile.id.id);
              this.selectTenantProfileFormGroup.get('tenantProfile').patchValue(profile, {emitEvent: true});
              if (isAdd) {
                this.propagateChange(this.modelValue);
              } else {
                this.tenantProfileUpdated.next(savedTenantProfile.id);
              }
            }
          );
        }
      }
    );
  }
}
