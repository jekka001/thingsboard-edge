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

import { Component, EventEmitter, forwardRef, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ControlValueAccessor, FormBuilder, FormGroup, NG_VALUE_ACCESSOR, Validators } from '@angular/forms';
import { AliasFilterType, aliasFilterTypeTranslationMap, EntityAliasFilter } from '@shared/models/alias.models';
import { AliasEntityType, EntityType } from '@shared/models/entity-type.models';
import { EntityService } from '@core/http/entity.service';
import { EntitySearchDirection, entitySearchDirectionTranslations } from '@shared/models/relation.models';
import { Subject, Subscription } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { entityGroupTypes } from '@app/shared/models/entity-group.models';

@Component({
  selector: 'tb-entity-filter',
  templateUrl: './entity-filter.component.html',
  styleUrls: ['./entity-filter.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => EntityFilterComponent),
      multi: true
    }
  ]
})
export class EntityFilterComponent implements ControlValueAccessor, OnInit, OnDestroy {

  @Input() disabled: boolean;

  @Input() allowedEntityTypes: Array<EntityType | AliasEntityType>;

  @Input() resolveMultiple: boolean;

  @Output() resolveMultipleChanged: EventEmitter<boolean> = new EventEmitter<boolean>();

  entityFilterFormGroup: FormGroup;
  filterFormGroup: FormGroup;

  aliasFilterTypes: Array<AliasFilterType>;
  entityGroupTypes: Array<EntityType>;

  aliasFilterType = AliasFilterType;
  aliasFilterTypeTranslations = aliasFilterTypeTranslationMap;
  entityType = EntityType;

  directionTypes = Object.keys(EntitySearchDirection);
  directionTypeTranslations = entitySearchDirectionTranslations;
  directionTypeEnum = EntitySearchDirection;

  private propagateChange = null;

  private destroy$ = new Subject<void>();
  private subscriptions = new Subscription();

  constructor(private entityService: EntityService,
              private fb: FormBuilder) {
  }

  ngOnInit(): void {

    this.aliasFilterTypes = this.entityService.getAliasFilterTypesByEntityTypes(this.allowedEntityTypes);
    this.entityGroupTypes = entityGroupTypes.filter((entityType) =>
      this.allowedEntityTypes ? this.allowedEntityTypes.indexOf(entityType) > - 1 : true
    );

    this.entityFilterFormGroup = this.fb.group({
      type: [null, [Validators.required]]
    });
    this.entityFilterFormGroup.get('type').valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe((type: AliasFilterType) => {
      this.filterTypeChanged(type);
    });
    this.entityFilterFormGroup.valueChanges.pipe(
      takeUntil(this.destroy$)
    ).subscribe(() => {
      this.updateModel();
    });
    this.filterFormGroup = this.fb.group({});
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.subscriptions.unsubscribe();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState?(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  writeValue(filter: EntityAliasFilter): void {
    if (!filter) {
      filter = {
        type: null,
        resolveMultiple: this.resolveMultiple
      };
    }
    this.entityFilterFormGroup.get('type').patchValue(filter.type, {emitEvent: false});
    if (filter && filter.type) {
      this.updateFilterFormGroup(filter.type, filter);
    }
  }

  private updateFilterFormGroup(type: AliasFilterType, filter?: EntityAliasFilter) {
    this.subscriptions.unsubscribe();
    this.subscriptions = new Subscription();
    switch (type) {
      case AliasFilterType.singleEntity:
        this.filterFormGroup = this.fb.group({
          singleEntity: [filter ? filter.singleEntity : null, [Validators.required]]
        });
        break;
      case AliasFilterType.entityGroup:
        this.filterFormGroup = this.fb.group({
          groupStateEntity: [filter ? filter.groupStateEntity : false, []],
          stateEntityParamName: [filter ? filter.stateEntityParamName : null, []],
          defaultStateGroupType: [filter ? filter.defaultStateGroupType : null, []],
          defaultStateEntityGroup: [filter ? filter.defaultStateEntityGroup : null, []],
          groupType: [filter ? filter.groupType : null, (filter && filter.groupStateEntity) ? [] : [Validators.required]],
          entityGroup: [filter ? filter.entityGroup : null, (filter && filter.groupStateEntity) ? [] : [Validators.required]],
        });
        const groupStateEntitySubscription = this.filterFormGroup.get('groupStateEntity').valueChanges.subscribe((groupStateEntity: boolean) => {
          this.filterFormGroup.get('groupType').setValidators(groupStateEntity ? [] : [Validators.required]);
          this.filterFormGroup.get('entityGroup').setValidators(groupStateEntity ? [] : [Validators.required]);
          this.filterFormGroup.get('groupType').updateValueAndValidity();
          this.filterFormGroup.get('entityGroup').updateValueAndValidity();
        });
        this.subscriptions.add(groupStateEntitySubscription);
        break;
      case AliasFilterType.entityList:
        this.filterFormGroup = this.fb.group({
          entityType: [filter ? filter.entityType : null, [Validators.required]],
          entityList: [{
            value: filter ? filter.entityList : [],
            disabled: !filter?.entityType
          }, [Validators.required]],
        });
        const entityTypeSubscription = this.filterFormGroup.get('entityType').valueChanges.subscribe((entityType) => {
          if (entityType && this.filterFormGroup.get('entityList').disabled) {
            this.filterFormGroup.get('entityList').enable({emitEvent: false});
          }
        });
        this.subscriptions.add(entityTypeSubscription);
        break;
      case AliasFilterType.entityName:
        this.filterFormGroup = this.fb.group({
          entityType: [filter ? filter.entityType : null, [Validators.required]],
          entityNameFilter: [filter ? filter.entityNameFilter : '', [Validators.required]],
        });
        break;
      case AliasFilterType.entityType:
        this.filterFormGroup = this.fb.group({
          entityType: [filter ? filter.entityType : null, [Validators.required]]
        });
        break;
      case AliasFilterType.entityGroupList:
        this.filterFormGroup = this.fb.group({
          groupType: [filter ? filter.groupType : null, [Validators.required]],
          entityGroupList: [{
            value: filter ? filter.entityGroupList : [],
            disabled: !filter?.groupType
          }, [Validators.required]],
        });
        const groupTypeSubscription = this.filterFormGroup.get('groupType').valueChanges.subscribe((groupType) => {
          if (groupType && this.filterFormGroup.get('entityGroupList').disabled) {
            this.filterFormGroup.get('entityGroupList').enable({emitEvent: false});
          }
        });
        this.subscriptions.add(groupTypeSubscription);
        break;
      case AliasFilterType.entityGroupName:
        this.filterFormGroup = this.fb.group({
          groupType: [filter ? filter.groupType : null, [Validators.required]],
          entityGroupNameFilter: [filter ? filter.entityGroupNameFilter : '', [Validators.required]],
        });
        break;
      case AliasFilterType.entitiesByGroupName:
        this.filterFormGroup = this.fb.group({
          groupStateEntity: [filter ? filter.groupStateEntity : false, []],
          stateEntityParamName: [filter ? filter.stateEntityParamName : null, []],
          groupType: [filter ? filter.groupType : null, [Validators.required]],
          entityGroupNameFilter: [filter ? filter.entityGroupNameFilter : '', [Validators.required]],
        });
        break;
      case AliasFilterType.stateEntity:
      case AliasFilterType.stateEntityOwner:
        this.filterFormGroup = this.fb.group({
          stateEntityParamName: [filter ? filter.stateEntityParamName : null, []],
          defaultStateEntity: [filter ? filter.defaultStateEntity : null, []],
        });
        break;
      case AliasFilterType.assetType:
        this.filterFormGroup = this.fb.group({
          assetTypes: [filter ? filter.assetTypes : null, [Validators.required]],
          assetNameFilter: [filter ? filter.assetNameFilter : '', []],
        });
        break;
      case AliasFilterType.deviceType:
        this.filterFormGroup = this.fb.group({
          deviceTypes: [filter ? filter.deviceTypes : null, [Validators.required]],
          deviceNameFilter: [filter ? filter.deviceNameFilter : '', []],
        });
        break;
      case AliasFilterType.edgeType:
        this.filterFormGroup = this.fb.group({
          edgeTypes: [filter ? filter.edgeTypes : null, [Validators.required]],
          edgeNameFilter: [filter ? filter.edgeNameFilter : '', []],
        });
        break;
      case AliasFilterType.entityViewType:
        this.filterFormGroup = this.fb.group({
          entityViewTypes: [filter ? filter.entityViewTypes : null, [Validators.required]],
          entityViewNameFilter: [filter ? filter.entityViewNameFilter : '', []],
        });
        break;
      case AliasFilterType.apiUsageState:
        this.filterFormGroup = this.fb.group({});
        break;
      case AliasFilterType.relationsQuery:
      case AliasFilterType.assetSearchQuery:
      case AliasFilterType.deviceSearchQuery:
      case AliasFilterType.edgeSearchQuery:
      case AliasFilterType.entityViewSearchQuery:
        this.filterFormGroup = this.fb.group({
          rootStateEntity: [filter ? filter.rootStateEntity : false, []],
          stateEntityParamName: [filter ? filter.stateEntityParamName : null, []],
          defaultStateEntity: [filter ? filter.defaultStateEntity : null, []],
          rootEntity: [filter ? filter.rootEntity : null, (filter && filter.rootStateEntity) ? [] : [Validators.required]],
          direction: [filter ? filter.direction : EntitySearchDirection.FROM, [Validators.required]],
          maxLevel: [filter ? filter.maxLevel : 1, []],
          fetchLastLevelOnly: [filter ? filter.fetchLastLevelOnly : false, []]
        });
        const rootStateSubscription = this.filterFormGroup.get('rootStateEntity').valueChanges.subscribe((rootStateEntity: boolean) => {
          this.filterFormGroup.get('rootEntity').setValidators(rootStateEntity ? [] : [Validators.required]);
          this.filterFormGroup.get('rootEntity').updateValueAndValidity();
        });
        this.subscriptions.add(rootStateSubscription);
        if (type === AliasFilterType.relationsQuery) {
          this.filterFormGroup.addControl('filters',
            this.fb.control(filter ? filter.filters : [], []));
        } else {
          this.filterFormGroup.addControl('relationType',
            this.fb.control(filter ? filter.relationType : null, []));
          if (type === AliasFilterType.assetSearchQuery) {
            this.filterFormGroup.addControl('assetTypes',
              this.fb.control(filter ? filter.assetTypes : [], [Validators.required]));
          } else if (type === AliasFilterType.deviceSearchQuery) {
            this.filterFormGroup.addControl('deviceTypes',
              this.fb.control(filter ? filter.deviceTypes : [], [Validators.required]));
          } else if (type === AliasFilterType.edgeSearchQuery) {
            this.filterFormGroup.addControl('edgeTypes',
              this.fb.control(filter ? filter.edgeTypes : [], [Validators.required]));
          } else if (type === AliasFilterType.entityViewSearchQuery) {
            this.filterFormGroup.addControl('entityViewTypes',
              this.fb.control(filter ? filter.entityViewTypes : [], [Validators.required]));
          }
        }
        break;
      case AliasFilterType.schedulerEvent:
        this.filterFormGroup = this.fb.group({
          originatorStateEntity: [filter ? filter.originatorStateEntity : false, []],
          stateEntityParamName: [filter ? filter.stateEntityParamName : null, []],
          defaultStateEntity: [filter ? filter.defaultStateEntity : null, []],
          originator: [filter ? filter.originator : null, []],
          eventType: [filter ? filter.eventType : null, []]
        });
        break;
    }
    const filterFormSubscription = this.filterFormGroup.valueChanges.subscribe(() => {
      this.updateModel();
    });
    this.subscriptions.add(filterFormSubscription);
  }

  private filterTypeChanged(type: AliasFilterType) {
    let resolveMultiple = true;
    if (type === AliasFilterType.singleEntity || type === AliasFilterType.stateEntity || type === AliasFilterType.apiUsageState ||
        type === AliasFilterType.stateEntityOwner) {
      resolveMultiple = false;
    }
    if (this.resolveMultiple !== resolveMultiple) {
      this.resolveMultipleChanged.emit(resolveMultiple);
    }
    this.updateFilterFormGroup(type);
  }

  private updateModel() {
    let filter = null;
    if (this.entityFilterFormGroup.valid && this.filterFormGroup.valid) {
      filter = {
        type: this.entityFilterFormGroup.get('type').value,
        resolveMultiple: this.resolveMultiple
      };
      filter = {...filter, ...this.filterFormGroup.value};
    }
    this.propagateChange(filter);
  }
}
