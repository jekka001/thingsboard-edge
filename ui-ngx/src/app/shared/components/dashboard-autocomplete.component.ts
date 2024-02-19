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

import { AfterViewInit, Component, ElementRef, forwardRef, Input, OnInit, ViewChild } from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormGroup,
  Validators
} from '@angular/forms';
import { Observable, of } from 'rxjs';
import { PageLink } from '@shared/models/page/page-link';
import { Direction } from '@shared/models/page/sort-order';
import { catchError, debounceTime, distinctUntilChanged, map, share, switchMap, tap } from 'rxjs/operators';
import { emptyPageData, PageData } from '@shared/models/page/page-data';
import { DashboardInfo } from '@app/shared/models/dashboard.models';
import { DashboardService } from '@core/http/dashboard.service';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { Operation } from '@shared/models/security.models';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { FloatLabelType, MatFormFieldAppearance, SubscriptSizing } from '@angular/material/form-field';
import { getEntityDetailsPageURL } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { AuthUser } from '@shared/models/user.model';
import { coerceBoolean } from '@shared/decorators/coercion';

@Component({
  selector: 'tb-dashboard-autocomplete',
  templateUrl: './dashboard-autocomplete.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => DashboardAutocompleteComponent),
    multi: true
  }]
})
export class DashboardAutocompleteComponent implements ControlValueAccessor, OnInit, AfterViewInit {

  private dirty = false;

  selectDashboardFormGroup: UntypedFormGroup;

  modelValue: DashboardInfo | string | null;

  @Input()
  useIdValue = true;

  @Input()
  selectFirstDashboard = false;

  @Input()
  label = this.translate.instant('dashboard.dashboard');

  @Input()
  placeholder: string;

  @Input()
  userId: string;

  @Input()
  tenantId: string;

  @Input()
  operation: Operation;

  @Input()
  floatLabel: FloatLabelType = 'auto';

  @Input()
  appearance: MatFormFieldAppearance = 'fill';

  @Input()
  subscriptSizing: SubscriptSizing = 'fixed';

  @Input()
  @coerceBoolean()
  inlineField: boolean;

  @Input()
  requiredText: string;

  @Input()
  @coerceBoolean()
  required: boolean;

  @Input()
  disabled: boolean;

  @ViewChild('dashboardInput', {static: true}) dashboardInput: ElementRef;

  filteredDashboards: Observable<Array<DashboardInfo>>;

  searchText = '';

  dashboardURL: string;

  useDashboardLink = true;

  private authUser: AuthUser;

  private propagateChange = (_v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              private dashboardService: DashboardService,
              private fb: UntypedFormBuilder) {
    this.authUser = getCurrentAuthUser(this.store);
    if (this.authUser.authority === Authority.SYS_ADMIN) {
      this.useDashboardLink = false;
    }

    this.selectDashboardFormGroup = this.fb.group({
      dashboard: [null, this.required ? [Validators.required] : []]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(_fn: any): void {
  }

  ngOnInit() {
    this.filteredDashboards = this.selectDashboardFormGroup.get('dashboard').valueChanges
      .pipe(
        debounceTime(150),
        tap(value => {
          let modelValue: string | DashboardInfo;
          if (typeof value === 'string' || !value) {
            modelValue = null;
          } else {
            modelValue = this.useIdValue ? value.id.id : value;
          }
          this.updateView(modelValue);
        }),
        map(value => value ? (typeof value === 'string' ? value : value.name) : ''),
        distinctUntilChanged(),
        switchMap(name => this.fetchDashboards(name) ),
        share()
      );
  }

  ngAfterViewInit(): void {
    // this.selectFirstDashboardIfNeeded();
  }

  selectFirstDashboardIfNeeded(): void {
    if (this.selectFirstDashboard && !this.modelValue) {
      this.getDashboards(new PageLink(1, 0, null, {
        property: 'title',
        direction: Direction.ASC
      })).subscribe(
        (data) => {
          if (data.data.length) {
            const dashboard = data.data[0];
            this.modelValue = this.useIdValue ? dashboard.id.id : dashboard;
            this.selectDashboardFormGroup.get('dashboard').patchValue(dashboard, {emitEvent: false});
            this.propagateChange(this.modelValue);
          }
        }
      );
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.selectDashboardFormGroup.disable({emitEvent: false});
    } else {
      this.selectDashboardFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: DashboardInfo | string | null): void {
    this.searchText = '';
    if (value != null) {
      if (typeof value === 'string') {
        this.dashboardService.getDashboardInfo(value, {ignoreLoading: true, ignoreErrors: true}).subscribe(
          (dashboard) => {
            this.modelValue = this.useIdValue ? dashboard.id.id : dashboard;
            if (this.useDashboardLink) {
              this.dashboardURL = getEntityDetailsPageURL(this.modelValue as string, EntityType.DASHBOARD);
            }
            this.selectDashboardFormGroup.get('dashboard').patchValue(dashboard, {emitEvent: false});
          }
        );
      } else {
        this.modelValue = this.useIdValue ? value.id.id : value;
        this.selectDashboardFormGroup.get('dashboard').patchValue(value, {emitEvent: false});
      }
    } else {
      this.modelValue = null;
      this.selectDashboardFormGroup.get('dashboard').patchValue('', {emitEvent: false});
      this.selectFirstDashboardIfNeeded();
    }
    this.dirty = true;
  }

  updateView(value: DashboardInfo | string | null) {
    if (this.modelValue !== value) {
      this.modelValue = value;
      this.propagateChange(this.modelValue);
    }
  }

  displayDashboardFn(dashboard?: DashboardInfo): string | undefined {
    return dashboard ? dashboard.title : undefined;
  }

  fetchDashboards(searchText?: string): Observable<Array<DashboardInfo>> {
    this.searchText = searchText;
    const pageLink = new PageLink(25, 0, searchText, {
      property: 'title',
      direction: Direction.ASC
    });
    return this.getDashboards(pageLink).pipe(
      catchError(() => of(emptyPageData<DashboardInfo>())),
      map(pageData => pageData.data)
    );
  }

  getDashboards(pageLink: PageLink): Observable<PageData<DashboardInfo>> {
    let dashboardsObservable: Observable<PageData<DashboardInfo>>;
    const authUser = getCurrentAuthUser(this.store);
    if (authUser.authority === Authority.SYS_ADMIN && this.tenantId) {
      dashboardsObservable = this.dashboardService.getTenantDashboardsByTenantId(this.tenantId, pageLink,
        {ignoreLoading: true});
    } else {
      let userId = this.userId;
      if (!userId) {
        userId = authUser.userId;
      }
      dashboardsObservable = this.dashboardService.getUserDashboards(userId, this.operation, pageLink,
        {ignoreLoading: true});
    }
    return dashboardsObservable;
  }

  onFocus() {
    if (this.dirty) {
      this.selectDashboardFormGroup.get('dashboard').updateValueAndValidity({onlySelf: true});
      this.dirty = false;
    }
  }

  clear() {
    this.selectDashboardFormGroup.get('dashboard').patchValue('');
    setTimeout(() => {
      this.dashboardInput.nativeElement.blur();
      this.dashboardInput.nativeElement.focus();
    }, 0);
  }

}
