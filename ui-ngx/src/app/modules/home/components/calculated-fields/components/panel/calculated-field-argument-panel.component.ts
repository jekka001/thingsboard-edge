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

import { AfterViewInit, ChangeDetectorRef, Component, Input, OnInit, output, ViewChild } from '@angular/core';
import { TbPopoverComponent } from '@shared/components/popover.component';
import { FormBuilder, FormControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import { charsWithNumRegex, oneSpaceInsideRegex } from '@shared/models/regex.constants';
import {
  ArgumentEntityType,
  ArgumentEntityTypeParamsMap,
  ArgumentEntityTypeTranslations,
  ArgumentType,
  ArgumentTypeTranslations,
  CalculatedFieldArgumentValue,
  CalculatedFieldType,
  getCalculatedFieldCurrentEntityFilter
} from '@shared/models/calculated-field.models';
import { debounceTime, delay, distinctUntilChanged, filter } from 'rxjs/operators';
import { EntityType } from '@shared/models/entity-type.models';
import { AttributeScope, DataKeyType } from '@shared/models/telemetry/telemetry.models';
import { DatasourceType } from '@shared/models/widget.models';
import { EntityId } from '@shared/models/id/entity-id';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EntityFilter } from '@shared/models/query/query.models';
import { AliasFilterType } from '@shared/models/alias.models';
import { BehaviorSubject, merge } from 'rxjs';
import { MINUTE } from '@shared/models/time/time.models';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { EntityAutocompleteComponent } from '@shared/components/entity/entity-autocomplete.component';
import { NULL_UUID } from '@shared/models/id/has-uuid';

@Component({
  selector: 'tb-calculated-field-argument-panel',
  templateUrl: './calculated-field-argument-panel.component.html',
  styleUrls: ['./calculated-field-argument-panel.component.scss']
})
export class CalculatedFieldArgumentPanelComponent implements OnInit, AfterViewInit {

  @Input() buttonTitle: string;
  @Input() argument: CalculatedFieldArgumentValue;
  @Input() entityId: EntityId;
  @Input() tenantId: string;
  @Input() entityName: string;
  @Input() calculatedFieldType: CalculatedFieldType;
  @Input() usedArgumentNames: string[];

  @ViewChild('entityAutocomplete') entityAutocomplete: EntityAutocompleteComponent;

  argumentsDataApplied = output<CalculatedFieldArgumentValue>();

  readonly maxDataPointsPerRollingArg = getCurrentAuthState(this.store).maxDataPointsPerRollingArg;
  readonly defaultLimit = Math.floor(this.maxDataPointsPerRollingArg / 10);

  argumentFormGroup = this.fb.group({
    argumentName: ['', [Validators.required, this.uniqNameRequired(), this.forbiddenArgumentNameValidator(), Validators.pattern(charsWithNumRegex), Validators.maxLength(255)]],
    refEntityId: this.fb.group({
      entityType: [ArgumentEntityType.Current],
      id: ['']
    }),
    refEntityKey: this.fb.group({
      type: [ArgumentType.LatestTelemetry, [Validators.required]],
      key: ['', [Validators.pattern(oneSpaceInsideRegex)]],
      scope: [{ value: AttributeScope.SERVER_SCOPE, disabled: true }, [Validators.required]],
    }),
    defaultValue: ['', [Validators.pattern(oneSpaceInsideRegex)]],
    limit: [{ value: this.defaultLimit, disabled: !this.maxDataPointsPerRollingArg }, [Validators.required, Validators.min(1), Validators.max(this.maxDataPointsPerRollingArg)]],
    timeWindow: [MINUTE * 15, [Validators.required]],
  });

  argumentTypes: ArgumentType[];
  entityFilter: EntityFilter;
  entityNameSubject = new BehaviorSubject<string>(null);

  readonly argumentEntityTypes = Object.values(ArgumentEntityType) as ArgumentEntityType[];
  readonly ArgumentEntityTypeTranslations = ArgumentEntityTypeTranslations;
  readonly ArgumentType = ArgumentType;
  readonly DataKeyType = DataKeyType;
  readonly EntityType = EntityType;
  readonly datasourceType = DatasourceType;
  readonly ArgumentTypeTranslations = ArgumentTypeTranslations;
  readonly AttributeScope = AttributeScope;
  readonly ArgumentEntityType = ArgumentEntityType;
  readonly ArgumentEntityTypeParamsMap = ArgumentEntityTypeParamsMap;

  private currentEntityFilter: EntityFilter;

  constructor(
    private fb: FormBuilder,
    private cd: ChangeDetectorRef,
    private popover: TbPopoverComponent<CalculatedFieldArgumentPanelComponent>,
    private store: Store<AppState>
  ) {
    this.observeEntityFilterChanges();
    this.observeEntityTypeChanges();
    this.observeEntityKeyChanges();
    this.observeUpdatePosition();
  }

  get entityType(): ArgumentEntityType {
    return this.argumentFormGroup.get('refEntityId').get('entityType').value;
  }

  get refEntityIdFormGroup(): FormGroup {
    return this.argumentFormGroup.get('refEntityId') as FormGroup;
  }

  get refEntityKeyFormGroup(): FormGroup {
    return this.argumentFormGroup.get('refEntityKey') as FormGroup;
  }

  get enableAttributeScopeSelection(): boolean {
    return this.entityType === ArgumentEntityType.Device
      || (this.entityType === ArgumentEntityType.Current
        && (this.entityId.entityType === EntityType.DEVICE || this.entityId.entityType === EntityType.DEVICE_PROFILE))
  }

  ngOnInit(): void {
    this.argumentFormGroup.patchValue(this.argument, {emitEvent: false});
    this.currentEntityFilter = getCalculatedFieldCurrentEntityFilter(this.entityName, this.entityId);
    this.updateEntityFilter(this.argument.refEntityId?.entityType, true);
    this.toggleByEntityKeyType(this.argument.refEntityKey?.type);
    this.setInitialEntityKeyType();

    this.argumentTypes = Object.values(ArgumentType)
      .filter(type => type !== ArgumentType.Rolling || this.calculatedFieldType === CalculatedFieldType.SCRIPT);
  }

  ngAfterViewInit(): void {
    if (this.argument.refEntityId?.id === NULL_UUID) {
      this.entityAutocomplete.selectEntityFormGroup.get('entity').markAsTouched();
    }
  }

  saveArgument(): void {
    const { refEntityId, ...restConfig } = this.argumentFormGroup.value;
    const value = (refEntityId.entityType === ArgumentEntityType.Current ? restConfig : { refEntityId, ...restConfig }) as CalculatedFieldArgumentValue;
    if (refEntityId.entityType === ArgumentEntityType.Tenant) {
      refEntityId.id = this.tenantId;
    }
    if (refEntityId.entityType !== ArgumentEntityType.Current && refEntityId.entityType !== ArgumentEntityType.Tenant) {
      value.entityName = this.entityNameSubject.value;
    }
    if (value.defaultValue) {
      value.defaultValue = value.defaultValue.trim();
    }
    value.refEntityKey.key = value.refEntityKey.key.trim();
    this.argumentsDataApplied.emit(value);
  }

  cancel(): void {
    this.popover.hide();
  }

  private toggleByEntityKeyType(type: ArgumentType): void {
    const isAttribute = type === ArgumentType.Attribute;
    const isRolling = type === ArgumentType.Rolling;
    this.argumentFormGroup.get('refEntityKey').get('scope')[isAttribute? 'enable' : 'disable']({ emitEvent: false });
    this.argumentFormGroup.get('limit')[isRolling? 'enable' : 'disable']({ emitEvent: false });
    this.argumentFormGroup.get('timeWindow')[isRolling? 'enable' : 'disable']({ emitEvent: false });
    this.argumentFormGroup.get('defaultValue')[isRolling? 'disable' : 'enable']({ emitEvent: false });
  }

  private updateEntityFilter(entityType: ArgumentEntityType = ArgumentEntityType.Current, onInit = false): void {
    let entityFilter: EntityFilter;
    switch (entityType) {
      case ArgumentEntityType.Current:
        entityFilter = this.currentEntityFilter;
        break;
      case ArgumentEntityType.Tenant:
        entityFilter = {
          type: AliasFilterType.singleEntity,
          singleEntity: {
            id: this.tenantId,
            entityType: EntityType.TENANT
          },
        };
        break;
      default:
        entityFilter = {
          type: AliasFilterType.singleEntity,
          singleEntity: this.argumentFormGroup.get('refEntityId').value as unknown as EntityId,
        };
    }
    if (!onInit) {
      this.argumentFormGroup.get('refEntityKey').get('key').setValue('');
    }
    this.entityFilter = entityFilter;
    this.cd.markForCheck();
  }

  private observeEntityFilterChanges(): void {
    merge(
      this.refEntityIdFormGroup.get('entityType').valueChanges,
      this.refEntityKeyFormGroup.get('type').valueChanges,
      this.refEntityIdFormGroup.get('id').valueChanges.pipe(filter(Boolean)),
      this.refEntityKeyFormGroup.get('scope').valueChanges,
    )
      .pipe(debounceTime(50), takeUntilDestroyed())
      .subscribe(() => this.updateEntityFilter(this.entityType));
  }

  private observeEntityTypeChanges(): void {
    this.refEntityIdFormGroup.get('entityType').valueChanges
      .pipe(distinctUntilChanged(), takeUntilDestroyed())
      .subscribe(type => {
        this.argumentFormGroup.get('refEntityId').get('id').setValue('');
        const isEntityWithId = type !== ArgumentEntityType.Tenant && type !== ArgumentEntityType.Current;
        this.argumentFormGroup.get('refEntityId')
          .get('id')[isEntityWithId ? 'enable' : 'disable']();
        if (!isEntityWithId) {
          this.entityNameSubject.next(null);
        }
        if (!this.enableAttributeScopeSelection) {
          this.refEntityKeyFormGroup.get('scope').setValue(AttributeScope.SERVER_SCOPE);
        }
      });
  }

  private uniqNameRequired(): ValidatorFn {
    return (control: FormControl) => {
      const newName = control.value.trim().toLowerCase();
      const isDuplicate = this.usedArgumentNames?.some(name => name.toLowerCase() === newName);

      return isDuplicate ? { duplicateName: true } : null;
    };
  }

  private observeEntityKeyChanges(): void {
    this.argumentFormGroup.get('refEntityKey').get('type').valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(type => this.toggleByEntityKeyType(type));
  }

  private setInitialEntityKeyType(): void {
    if (this.calculatedFieldType === CalculatedFieldType.SIMPLE && this.argument.refEntityKey?.type === ArgumentType.Rolling) {
      const typeControl = this.argumentFormGroup.get('refEntityKey').get('type');
      typeControl.setValue(null);
      typeControl.markAsTouched();
    }
  }

  private forbiddenArgumentNameValidator(): ValidatorFn {
    return (control: FormControl) => {
      const trimmedValue = control.value.trim().toLowerCase();
      const forbiddenArgumentNames = ['ctx', 'e', 'pi'];
      return forbiddenArgumentNames.includes(trimmedValue) ? { forbiddenName: true } : null;
    };
  }

  private observeUpdatePosition(): void {
    merge(
      this.refEntityIdFormGroup.get('entityType').valueChanges,
      this.refEntityKeyFormGroup.get('type').valueChanges,
      this.argumentFormGroup.get('timeWindow').valueChanges,
      this.refEntityIdFormGroup.get('id').valueChanges.pipe(filter(Boolean)),
    )
      .pipe(delay(50), takeUntilDestroyed())
      .subscribe(() => this.popover.updatePosition());
  }
}
