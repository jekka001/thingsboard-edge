///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
  ElementRef,
  EventEmitter,
  forwardRef,
  Input,
  OnChanges,
  OnInit,
  Output,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable } from 'rxjs';
import { map, mergeMap, publishReplay, refCount, share, tap } from 'rxjs/operators';
import { Store } from '@ngrx/store';
import { AppState } from '@app/core/core.state';
import { TranslateService } from '@ngx-translate/core';
import { EntityType } from '@shared/models/entity-type.models';
import { EntityId } from '@shared/models/id/entity-id';
import { coerceBooleanProperty } from '@angular/cdk/coercion';
import { EntityGroupInfo } from '@shared/models/entity-group.models';
import { EntityGroupService } from '@core/http/entity-group.service';
import { isEqual } from '@core/utils';

@Component({
  selector: 'tb-entity-group-autocomplete',
  templateUrl: './entity-group-autocomplete.component.html',
  styleUrls: [],
  providers: [{
    provide: NG_VALUE_ACCESSOR,
    useExisting: forwardRef(() => EntityGroupAutocompleteComponent),
    multi: true
  }]
})
export class EntityGroupAutocompleteComponent implements ControlValueAccessor, OnInit, OnChanges, AfterViewInit {

  selectEntityGroupFormGroup: FormGroup;

  modelValue: string | null;

  @Input()
  groupType: EntityType;

  @Input()
  ownerId: EntityId;

  @Input()
  excludeGroupIds: Array<string>;

  @Input()
  excludeGroupAll: boolean;

  @Input()
  placeholderText: string;

  @Input()
  notFoundText: string;

  @Input()
  requiredText: string;

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
  entityGroupLoaded = new EventEmitter<EntityGroupInfo>();

  @ViewChild('entityGroupInput', {static: true}) entityGroupInput: ElementRef<HTMLInputElement>;

  filteredEntityGroups: Observable<Array<EntityGroupInfo>>;

  allEntityGroups: Observable<Array<EntityGroupInfo>>;

  searchText = '';

  private dirty = false;

  private propagateChange = (v: any) => { };

  constructor(private store: Store<AppState>,
              public translate: TranslateService,
              private entityGroupService: EntityGroupService,
              private fb: FormBuilder) {
    this.selectEntityGroupFormGroup = this.fb.group({
      entityGroup: [null]
    });
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  ngOnInit() {
    this.filteredEntityGroups = this.selectEntityGroupFormGroup.get('entityGroup').valueChanges
      .pipe(
        tap(value => {
          let modelValue;
          if (typeof value === 'string' || !value) {
            modelValue = null;
          } else {
            modelValue = value.id.id;
          }
          this.updateView(modelValue);
          if (value === null) {
            this.clear();
          }
        }),
        map(value => value ? (typeof value === 'string' ? value : value.name) : ''),
        mergeMap(name => this.fetchEntityGroups(name) ),
        share()
      );
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && !isEqual(change.currentValue, change.previousValue)) {
        if (propName === 'groupType') {
          const currentEntityGroup = this.getCurrentEntityGroup();
          if (!currentEntityGroup || currentEntityGroup.type !== this.groupType) {
            this.reset();
            this.dirty = true;
          }
        } else if (propName === 'ownerId') {
            this.reset();
            this.dirty = true;
        }
      }
    }
  }


  ngAfterViewInit(): void {}

  getCurrentEntityGroup(): EntityGroupInfo | null {
    const currentEntityGroup = this.selectEntityGroupFormGroup.get('entityGroup').value;
    if (currentEntityGroup && typeof currentEntityGroup !== 'string') {
      return currentEntityGroup as EntityGroupInfo;
    } else {
      return null;
    }
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.selectEntityGroupFormGroup.disable({emitEvent: false});
    } else {
      this.selectEntityGroupFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: string | null): void {
    this.searchText = '';
    if (value !== null) {
      this.entityGroupService.getEntityGroup(value, {ignoreLoading: true}).subscribe(
        (entityGroup) => {
          this.modelValue = entityGroup.id.id;
          this.selectEntityGroupFormGroup.get('entityGroup').patchValue(entityGroup, {emitEvent: false});
          this.entityGroupLoaded.next(entityGroup);
        },
        () => {
          this.modelValue = null;
          this.selectEntityGroupFormGroup.get('entityGroup').patchValue('', {emitEvent: false});
          this.entityGroupLoaded.next(null);
        }
      );
    } else {
      this.modelValue = null;
      this.selectEntityGroupFormGroup.get('entityGroup').patchValue('', {emitEvent: false});
      this.entityGroupLoaded.next(null);
    }
    this.dirty = true;
  }

  onFocus() {
    if (this.dirty) {
      this.selectEntityGroupFormGroup.get('entityGroup').updateValueAndValidity({onlySelf: true, emitEvent: true});
      this.dirty = false;
    }
  }

  reset() {
    this.allEntityGroups = null;
    this.selectEntityGroupFormGroup.get('entityGroup').patchValue('', {emitEvent: false});
  }

  updateView(value: string | null) {
    if (this.modelValue !== value) {
      this.modelValue = value;
      this.propagateChange(this.modelValue);
    }
  }

  displayEntityGroupFn(entityGroup?: EntityGroupInfo): string | undefined {
    return entityGroup ? entityGroup.name : undefined;
  }

  fetchEntityGroups(searchText?: string): Observable<Array<EntityGroupInfo>> {
    this.searchText = searchText;
    return this.getEntityGroups().pipe(
      map((groups) => groups.filter(group => {
        return searchText ? group.name.toUpperCase().startsWith(searchText.toUpperCase()) : true;
      }))
    );
  }

  getEntityGroups(): Observable<Array<EntityGroupInfo>> {
    if (!this.allEntityGroups) {
      let entityGroupsObservable: Observable<Array<EntityGroupInfo>>;
      if (this.ownerId) {
        entityGroupsObservable = this.entityGroupService
          .getEntityGroupsByOwnerId(this.ownerId.entityType as EntityType, this.ownerId.id, this.groupType, {ignoreLoading: true});
      } else {
        entityGroupsObservable = this.entityGroupService.getEntityGroups(this.groupType, {ignoreLoading: true});
      }
      this.allEntityGroups = entityGroupsObservable.pipe(
        map(data => {
          if (data) {
            if (this.excludeGroupAll) {
              data = data.filter(group => !group.groupAll);
            }
            if (this.excludeGroupIds && this.excludeGroupIds.length) {
              const groups: Array<EntityGroupInfo> = [];
              data.forEach((group) => {
                if (this.excludeGroupIds.indexOf(group.id.id) === -1) {
                  groups.push(group);
                }
              });
              return groups;
            } else {
              return data;
            }
          } else {
            return [];
          }
        }),
        publishReplay(1),
        refCount()
      );
    }
    return this.allEntityGroups;
  }

  clear() {
    this.selectEntityGroupFormGroup.get('entityGroup').patchValue('', {emitEvent: true});
    setTimeout(() => {
      this.entityGroupInput.nativeElement.blur();
      this.entityGroupInput.nativeElement.focus();
    }, 0);
  }

}
