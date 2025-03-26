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

import {
  Component,
  DestroyRef,
  forwardRef,
  Input,
  OnInit,
  Renderer2,
  ViewContainerRef,
  ViewEncapsulation
} from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR, UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { WidgetAction, WidgetActionType, widgetType } from '@shared/models/widget.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TbPopoverService } from '@shared/components/popover.service';
import { MapSettingsContext } from '@home/components/widget/lib/settings/common/map/map-settings.component.models';
import {
  WidgetActionSettingsPanelComponent
} from '@home/components/widget/lib/settings/common/action/widget-action-settings-panel.component';
import { MatButton } from '@angular/material/button';
import { TranslateService } from '@ngx-translate/core';
import { deepClone } from '@core/utils';

@Component({
  selector: 'tb-map-tooltip-tag-actions-panel',
  templateUrl: './map-tooltip-tag-actions.component.html',
  styleUrls: ['./map-tooltip-tag-actions.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => MapTooltipTagActionsComponent),
      multi: true
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class MapTooltipTagActionsComponent implements ControlValueAccessor, OnInit {

  @Input()
  disabled: boolean;

  @Input()
  context: MapSettingsContext;

  actionsFormGroup: UntypedFormGroup;

  private propagateChange = (_val: any) => {};

  constructor(private fb: UntypedFormBuilder,
              private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef,
              private translate: TranslateService,
              private destroyRef: DestroyRef) {
  }

  ngOnInit() {
    this.actionsFormGroup = this.fb.group({
      actions: [null, []]
    });
    this.actionsFormGroup.get('actions').valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(
      (val) => this.propagateChange(val)
    );
  }

  writeValue(actions?: WidgetAction[]): void {
    this.actionsFormGroup.get('actions').patchValue(actions || [], {emitEvent: false});
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.actionsFormGroup.disable({emitEvent: false});
    } else {
      this.actionsFormGroup.enable({emitEvent: false});
    }
  }

  removeAction(index: number): void {
    const actions: WidgetAction[] = this.actionsFormGroup.get('actions').value;
    if (actions[index]) {
      actions.splice(index, 1);
      this.actionsFormGroup.get('actions').patchValue(actions);
    }
  }

  addAction($event: Event, matButton: MatButton): void {
    if ($event) {
      $event.stopPropagation();
    }
    const action: WidgetAction = {
      name: '',
      type: WidgetActionType.doNothing
    };
    const trigger = matButton._elementRef.nativeElement;
    const actionNames = (this.actionsFormGroup.get('actions').value as WidgetAction[] || []).map(action => action.name);
    this.openActionSettingsPopup(trigger, action, actionNames, true, (added) => {
      if (added) {
        const actions: WidgetAction[] = this.actionsFormGroup.get('actions').value || [];
        actions.push(added);
        this.actionsFormGroup.get('actions').patchValue(actions);
      }
    });
  }

  editAction($event: Event, matButton: MatButton, index: number): void {
    if ($event) {
      $event.stopPropagation();
    }
    const actions: WidgetAction[] = this.actionsFormGroup.get('actions').value;
    if (actions[index]) {
      const action = deepClone(actions[index]);
      const trigger = matButton._elementRef.nativeElement;
      const actionNames = actions.filter((_action, current) => current !== index).map(action => action.name);
      this.openActionSettingsPopup(trigger, action, actionNames, false, (updated) => {
        if (updated) {
          actions[index] = updated;
          this.actionsFormGroup.get('actions').patchValue(actions);
        }
      });
    }
  }

  private openActionSettingsPopup(trigger: Element, action: WidgetAction, actionNames: string[], isAdd: boolean, callback: (action?: WidgetAction) => void) {
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const title = this.translate.instant(isAdd ? 'widgets.maps.data-layer.add-tooltip-tag-action' : 'widgets.maps.data-layer.edit-tooltip-tag-action');
      const applyTitle = this.translate.instant(isAdd ? 'action.add' : 'action.apply');
      const widgetActionSettingsPanelPopover = this.popoverService.displayPopover({
        trigger,
        renderer: this.renderer,
        componentType: WidgetActionSettingsPanelComponent,
        hostView: this.viewContainerRef,
        preferredPlacement: ['leftTopOnly', 'leftOnly', 'leftBottomOnly'],
        context: {
          widgetAction: action,
          withName: true,
          actionNames,
          panelTitle: title,
          applyTitle,
          widgetType: widgetType.latest,
          callbacks: this.context.callbacks
        },
        isModal: true
      });
      widgetActionSettingsPanelPopover.tbComponentRef.instance.widgetActionApplied.subscribe((widgetAction) => {
        widgetActionSettingsPanelPopover.hide();
        callback(widgetAction);
      });
    }
  }

}
