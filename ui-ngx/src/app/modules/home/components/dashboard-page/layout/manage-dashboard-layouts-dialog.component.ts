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

import { Component, Inject, OnDestroy, SkipSelf, ViewChild } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  AbstractControl,
  FormGroupDirective,
  NgForm,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { DialogComponent } from '@app/shared/components/dialog.component';
import { TranslateService } from '@ngx-translate/core';
import {
  BreakpointId,
  DashboardLayout,
  DashboardLayoutId,
  DashboardStateLayouts,
  LayoutDimension,
  LayoutType,
  layoutTypes,
  layoutTypeTranslationMap,
  ViewFormatType
} from '@app/shared/models/dashboard.models';
import { deepClone, isDefined, isEqual } from '@core/utils';
import { DashboardUtilsService } from '@core/services/dashboard-utils.service';
import {
  DashboardSettingsDialogComponent,
  DashboardSettingsDialogData
} from '@home/components/dashboard-page/dashboard-settings-dialog.component';
import {
  LayoutFixedSize,
  LayoutPercentageSize,
  LayoutWidthType
} from '@home/components/dashboard-page/layout/layout.models';
import { Subscription } from 'rxjs';
import { MatTooltip } from '@angular/material/tooltip';
import {
  AddNewBreakpointDialogComponent,
  AddNewBreakpointDialogData,
  AddNewBreakpointDialogResult
} from '@home/components/dashboard-page/layout/add-new-breakpoint-dialog.component';
import { DialogService } from '@core/services/dialog.service';

export interface ManageDashboardLayoutsDialogData {
  layouts: DashboardStateLayouts;
}

export interface DashboardLayoutSettings {
  icon: string;
  name: string;
  descriptionSize?: string;
  layout: DashboardLayout;
  breakpoint: string;
}

@Component({
  selector: 'tb-manage-dashboard-layouts-dialog',
  templateUrl: './manage-dashboard-layouts-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: ManageDashboardLayoutsDialogComponent}],
  styleUrls: ['./manage-dashboard-layouts-dialog.component.scss', '../../../components/dashboard/layout-button.scss']
})
export class ManageDashboardLayoutsDialogComponent extends DialogComponent<ManageDashboardLayoutsDialogComponent, DashboardStateLayouts>
  implements ErrorStateMatcher, OnDestroy {

  @ViewChild('tooltip') tooltip: MatTooltip;

  layoutsFormGroup: UntypedFormGroup;

  layoutWidthType = LayoutWidthType;

  layoutPercentageSize = LayoutPercentageSize;

  layoutFixedSize = LayoutFixedSize;

  layoutTypes = layoutTypes;
  layoutTypeTranslations = layoutTypeTranslationMap;

  layoutBreakpoints: DashboardLayoutSettings[] = [];
  private readonly layouts: DashboardStateLayouts;

  private subscriptions: Array<Subscription> = [];

  private submitted = false;

  allowBreakpointIds = [];
  selectedBreakpointIds = ['default'];

  constructor(protected store: Store<AppState>,
              protected router: Router,
              @Inject(MAT_DIALOG_DATA) private data: ManageDashboardLayoutsDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              protected dialogRef: MatDialogRef<ManageDashboardLayoutsDialogComponent, DashboardStateLayouts>,
              private fb: UntypedFormBuilder,
              private dashboardUtils: DashboardUtilsService,
              private translate: TranslateService,
              private dialog: MatDialog,
              private dialogs: DialogService) {
    super(store, router, dialogRef);

    this.layouts = this.data.layouts;

    let layoutType = LayoutType.default;
    if (isDefined(this.layouts.right)) {
      layoutType = LayoutType.divider;
    } else if (isDefined(this.layouts.main.gridSettings.layoutType)) {
      layoutType = this.layouts.main.gridSettings.layoutType;
    }

    this.layoutsFormGroup = this.fb.group({
        layoutType: [layoutType],
        main: [{value: isDefined(this.layouts.main), disabled: true}],
        sliderPercentage: [50],
        sliderFixed: [this.layoutFixedSize.MIN],
        leftWidthPercentage: [50,
          [Validators.min(this.layoutPercentageSize.MIN), Validators.max(this.layoutPercentageSize.MAX), Validators.required]],
        rightWidthPercentage: [50,
          [Validators.min(this.layoutPercentageSize.MIN), Validators.max(this.layoutPercentageSize.MAX), Validators.required]],
        type: [LayoutWidthType.PERCENTAGE],
        fixedWidth: [this.layoutFixedSize.MIN,
          [Validators.min(this.layoutFixedSize.MIN), Validators.max(this.layoutFixedSize.MAX), Validators.required]],
        fixedLayout: ['main', []]
      }
    );

    this.subscriptions.push(
      this.layoutsFormGroup.get('type').valueChanges.subscribe(
        (value) => {
          if (value === LayoutWidthType.FIXED) {
            this.layoutsFormGroup.get('leftWidthPercentage').disable();
            this.layoutsFormGroup.get('rightWidthPercentage').disable();
            this.layoutsFormGroup.get('fixedWidth').enable();
            this.layoutsFormGroup.get('fixedLayout').enable();
          } else {
            this.layoutsFormGroup.get('leftWidthPercentage').enable();
            this.layoutsFormGroup.get('rightWidthPercentage').enable();
            this.layoutsFormGroup.get('fixedWidth').disable();
            this.layoutsFormGroup.get('fixedLayout').disable();
          }
        }
      )
    );

    if (this.layouts.right) {
      if (this.layouts.right.gridSettings.layoutDimension) {
        this.layoutsFormGroup.patchValue({
          fixedLayout: this.layouts.right.gridSettings.layoutDimension.fixedLayout,
          type: LayoutWidthType.FIXED,
          fixedWidth: this.layouts.right.gridSettings.layoutDimension.fixedWidth,
          sliderFixed: this.layouts.right.gridSettings.layoutDimension.fixedWidth
        }, {emitEvent: false});
      } else if (this.layouts.main.gridSettings.layoutDimension) {
        if (this.layouts.main.gridSettings.layoutDimension.type === LayoutWidthType.FIXED) {
          this.layoutsFormGroup.patchValue({
            fixedLayout: this.layouts.main.gridSettings.layoutDimension.fixedLayout,
            type: LayoutWidthType.FIXED,
            fixedWidth: this.layouts.main.gridSettings.layoutDimension.fixedWidth,
            sliderFixed: this.layouts.main.gridSettings.layoutDimension.fixedWidth
          }, {emitEvent: false});
        } else {
          const leftWidthPercentage = Number(this.layouts.main.gridSettings.layoutDimension.leftWidthPercentage);
          this.layoutsFormGroup.patchValue({
            leftWidthPercentage,
            sliderPercentage: leftWidthPercentage,
            rightWidthPercentage: 100 - Number(leftWidthPercentage)
          }, {emitEvent: false});
        }
      }
    }

    if (!this.layouts.main) {
      this.layouts.main = this.dashboardUtils.createDefaultLayoutData();
    }
    if (!this.layouts.right) {
      this.layouts.right = this.dashboardUtils.createDefaultLayoutData();
    }

    this.addLayoutConfiguration('default');

    if (!this.isDividerLayout && this.layouts.main.breakpoints) {
      for (const breakpoint of (Object.keys(this.layouts.main.breakpoints) as BreakpointId[])) {
        this.addLayoutConfiguration(breakpoint);
        this.selectedBreakpointIds.push(breakpoint);
      }
    }

    this.allowBreakpointIds = Object.values(this.dashboardUtils.getListBreakpoint())
      .filter((item) => !this.selectedBreakpointIds.includes(item.id))
      .map(item => item.id);

    this.subscriptions.push(
      this.layoutsFormGroup.get('sliderPercentage').valueChanges
        .subscribe(
          (value) => this.layoutsFormGroup.get('leftWidthPercentage').patchValue(value)
        ));
    this.subscriptions.push(
      this.layoutsFormGroup.get('sliderFixed').valueChanges
        .subscribe(
          (value) => {
            this.layoutsFormGroup.get('fixedWidth').patchValue(value);
          }
        ));
    this.subscriptions.push(
      this.layoutsFormGroup.get('leftWidthPercentage').valueChanges
        .subscribe(
          (value) => {
            this.showTooltip(this.layoutsFormGroup.get('leftWidthPercentage'), LayoutWidthType.PERCENTAGE, 'main');
            this.layoutControlChange('rightWidthPercentage', value);
          }
        ));
    this.subscriptions.push(
      this.layoutsFormGroup.get('rightWidthPercentage').valueChanges
        .subscribe(
          (value) => {
            this.showTooltip(this.layoutsFormGroup.get('rightWidthPercentage'), LayoutWidthType.PERCENTAGE, 'right');
            this.layoutControlChange('leftWidthPercentage', value);
          }
        ));
    this.subscriptions.push(
      this.layoutsFormGroup.get('fixedWidth').valueChanges
        .subscribe(
          (value) => {
            this.showTooltip(this.layoutsFormGroup.get('fixedWidth'), LayoutWidthType.FIXED,
              this.layoutsFormGroup.get('fixedLayout').value);
            this.layoutsFormGroup.get('sliderFixed').setValue(value, {emitEvent: false});
          }
        ));
  }

  ngOnDestroy(): void {
    for (const subscription of this.subscriptions) {
      subscription.unsubscribe();
    }
  }

  isErrorState(control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  openLayoutSettings(layoutId: DashboardLayoutId, breakpointId: BreakpointId = 'default') {
    const layout = this.dashboardUtils.getDashboardLayoutConfig(this.layouts[layoutId], breakpointId);
    const gridSettings = layout.gridSettings;
    gridSettings.layoutType = this.layoutsFormGroup.get('layoutType').value;
    this.dialog.open<DashboardSettingsDialogComponent, DashboardSettingsDialogData,
      DashboardSettingsDialogData>(DashboardSettingsDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        settings: null,
        gridSettings,
        isRightLayout: this.isDividerLayout && layoutId === 'right',
        breakpointId
      }
    }).afterClosed().subscribe((data) => {
      if (data && data.gridSettings) {
        this.dashboardUtils.updateLayoutSettings(layout, data.gridSettings);
        this.layoutsFormGroup.markAsDirty();
      }
    });
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  save(): void {
    this.submitted = true;
    const layoutType = this.layoutsFormGroup.value.layoutType;
    this.layouts.main.gridSettings.layoutType = layoutType;
    if (!this.isDividerLayout) {
      delete this.layouts.right;
      for (const breakpoint of Object.values(this.layouts.main.breakpoints)) {
        breakpoint.gridSettings.layoutType = layoutType;
      }
    } else {
      delete this.layouts.main.breakpoints;
      this.layouts.right.gridSettings.layoutType = layoutType;
    }
    delete this.layouts.main.gridSettings.layoutDimension;
    if (this.layouts.right?.gridSettings) {
      delete this.layouts.right.gridSettings.layoutDimension;
    }
    if (this.isDividerLayout) {
      const formValues = this.layoutsFormGroup.value;
      const widthType = formValues.type;
      const layoutDimension: LayoutDimension = {
        type: widthType
      };
      if (widthType === LayoutWidthType.PERCENTAGE) {
        layoutDimension.leftWidthPercentage = formValues.leftWidthPercentage;
        this.layouts.main.gridSettings.layoutDimension = layoutDimension;
      } else {
        layoutDimension.fixedWidth = formValues.fixedWidth;
        layoutDimension.fixedLayout = formValues.fixedLayout;
        if (formValues.fixedLayout === 'main') {
          this.layouts.main.gridSettings.layoutDimension = layoutDimension;
        } else {
          this.layouts.right.gridSettings.layoutDimension = layoutDimension;
        }
      }
    }
    this.dialogRef.close(this.layouts);
  }

  buttonFlexValue(): number {
    const formValues = this.layoutsFormGroup.value;
    if (this.isDividerLayout) {
      if (formValues.type !== LayoutWidthType.FIXED) {
        return formValues.leftWidthPercentage;
      } else {
        if (formValues.fixedLayout === 'main') {
          return 10;
        } else {
          return 90;
        }
      }
    }
  }

  formatSliderTooltipLabel(value: number): string | number {
    return this.layoutsFormGroup.get('type').value === LayoutWidthType.FIXED ? value : `${value}|${100 - value}`;
  }

  private layoutControlChange(key: string, value: number) {
    const valueToSet = 100 - Number(value);
    this.layoutsFormGroup.get(key).setValue(valueToSet, {emitEvent: false});
    this.layoutsFormGroup.get('sliderPercentage')
      .setValue(key === 'leftWidthPercentage' ? valueToSet : Number(value), {emitEvent: false});
  }

  setFixedLayout(layout: string): void {
    if (this.layoutsFormGroup.get('type').value === LayoutWidthType.FIXED && this.isDividerLayout) {
      this.layoutsFormGroup.get('fixedLayout').setValue(layout);
      this.layoutsFormGroup.get('fixedLayout').markAsDirty();
    }
  }

  private showTooltip(control: AbstractControl, layoutType: LayoutWidthType, layoutSide: DashboardLayoutId): void {
    if (control.errors) {
      let message: string;
      const unit = layoutType === LayoutWidthType.FIXED ? 'px' : '%';

      if (control.errors.required) {
        if (layoutType === LayoutWidthType.FIXED) {
          message = this.translate.instant('layout.layout-fixed-width-required');
        } else {
          if (layoutSide === 'right') {
            message = this.translate.instant('layout.right-width-percentage-required');
          } else {
            message = this.translate.instant('layout.left-width-percentage-required');
          }
        }
      } else if (control.errors.min) {
        message = this.translate.instant('layout.value-min-error', {min: control.errors.min.min, unit});
      } else if (control.errors.max) {
        message = this.translate.instant('layout.value-max-error', {max: control.errors.max.max, unit});
      }

      if (layoutSide === 'main') {
        this.tooltip.tooltipClass = 'tb-layout-error-tooltip-main';
      } else {
        this.tooltip.tooltipClass = 'tb-layout-error-tooltip-right';
      }

      this.tooltip.message = message;
      this.tooltip.show(1300);
    } else {
      this.tooltip.message = '';
      this.tooltip.hide();
    }
  }

  layoutButtonClass(side: DashboardLayoutId, border: boolean = false): string {
    const formValues = this.layoutsFormGroup.value;
    if (this.isDividerLayout) {
      let classString = border ? 'tb-layout-button-main ' : '';
      if (!(formValues.fixedLayout === side || formValues.type === LayoutWidthType.PERCENTAGE)) {
        classString += 'tb-fixed-layout-button';
      }
      return classString;
    }
  }

  layoutButtonText(side: DashboardLayoutId): string {
    const formValues = this.layoutsFormGroup.value;
    if (!(formValues.fixedLayout === side || !this.isDividerLayout || formValues.type === LayoutWidthType.PERCENTAGE)) {
      if (side === 'main') {
        return this.translate.instant('layout.left-side');
      } else {
        return this.translate.instant('layout.right-side');
      }
    }
  }

  showPreviewInputs(side: DashboardLayoutId): boolean {
    const formValues = this.layoutsFormGroup.value;
    return this.isDividerLayout && (formValues.type === LayoutWidthType.PERCENTAGE || formValues.fixedLayout === side);
  }

  get isDividerLayout(): boolean {
    return this.layoutsFormGroup.get('layoutType').value === LayoutType.divider;
  }

  addBreakpoint() {
    this.dialog.open<AddNewBreakpointDialogComponent, AddNewBreakpointDialogData,
      AddNewBreakpointDialogResult>(AddNewBreakpointDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        allowBreakpointIds: this.allowBreakpointIds,
        selectedBreakpointIds: this.selectedBreakpointIds
      }
    }).afterClosed().subscribe((data) => {
      if (data) {
        this.createdNewBreakpoint(data.newBreakpointId, data.copyFrom);
        this.layoutsFormGroup.markAsDirty();
      }
    });
  }

  deleteBreakpoint($event: Event, breakpointId: string): void {
    if ($event) {
      $event.stopPropagation();
    }
    const title = this.translate.instant('layout.delete-breakpoint-title', {name: breakpointId});
    const content = this.translate.instant('layout.delete-breakpoint-text');
    this.dialogs.confirm(title, content, this.translate.instant('action.no'),
      this.translate.instant('action.yes')).subscribe((res) => {
        if (res) {
          delete this.layouts.main.breakpoints[breakpointId];
          if (isEqual(this.layouts.main.breakpoints, {})) {
            delete this.layouts.main.breakpoints;
          }
          this.layoutBreakpoints = this.layoutBreakpoints.filter((item) => item.breakpoint !== breakpointId);
          this.allowBreakpointIds.push(breakpointId);
          this.selectedBreakpointIds = this.selectedBreakpointIds.filter((item) => item !== breakpointId);
          this.layoutsFormGroup.markAsDirty();
        }
      }
    );
  }

  private createdNewBreakpoint(newBreakpointId: BreakpointId, copyFromBreakpointId: BreakpointId): void {
    const layoutConfig = this.layouts.main;
    const sourceLayout = copyFromBreakpointId === 'default' ? layoutConfig : layoutConfig.breakpoints[copyFromBreakpointId];
    const gridSettings = deepClone(sourceLayout.gridSettings);
    const widgets = deepClone(sourceLayout.widgets);

    if (copyFromBreakpointId === 'default') {
      const breakpointInfo = this.dashboardUtils.getBreakpointInfoById(newBreakpointId);
      if (breakpointInfo?.maxWidth < 960) {
        gridSettings.viewFormat = ViewFormatType.list;
        gridSettings.rowHeight = gridSettings.mobileRowHeight;
        gridSettings.autoFillHeight = gridSettings.mobileAutoFillHeight;
      }

      for (const widgetId in widgets) {
        if (widgets[widgetId]) {
          delete widgets[widgetId].desktopHide;
          delete widgets[widgetId].mobileHide;
        }
      }
    }

    if (!layoutConfig.breakpoints) {
      layoutConfig.breakpoints = {};
    }

    layoutConfig.breakpoints[newBreakpointId] = {
      gridSettings,
      widgets,
    };
    this.selectedBreakpointIds.push(newBreakpointId);
    this.allowBreakpointIds = this.allowBreakpointIds.filter((item) => item !== newBreakpointId);
    this.addLayoutConfiguration(newBreakpointId);
  }

  private addLayoutConfiguration(breakpointId: BreakpointId) {
    const layout = breakpointId === 'default' ? this.layouts.main : this.layouts.main.breakpoints[breakpointId];
    const size = breakpointId === 'default' ? '' : this.dashboardUtils.getBreakpointSizeDescription(breakpointId);
    this.layoutBreakpoints.push({
      icon: this.dashboardUtils.getBreakpointIcon(breakpointId),
      name: this.dashboardUtils.getBreakpointName(breakpointId),
      layout,
      descriptionSize: size,
      breakpoint: breakpointId
    });
  }
}
