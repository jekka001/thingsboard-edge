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

import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  EventEmitter,
  HostBinding,
  Input, OnChanges,
  OnDestroy,
  OnInit,
  Output,
  Renderer2, SimpleChanges,
  ViewChild, ViewContainerRef,
  ViewEncapsulation
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { DashboardWidget, DashboardWidgets } from '@home/models/dashboard-component.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { SafeStyle } from '@angular/platform-browser';
import { WidgetExportType, widgetExportTypeTranslationMap } from '@shared/models/widget.models';
import { isNotEmptyStr } from '@core/utils';
import { GridsterItemComponent } from 'angular-gridster2';
import { UtilsService } from '@core/services/utils.service';
import ITooltipsterInstance = JQueryTooltipster.ITooltipsterInstance;
import { from } from 'rxjs';
import { DashboardUtilsService } from '@core/services/dashboard-utils.service';

export enum WidgetComponentActionType {
  MOUSE_DOWN,
  CLICKED,
  CONTEXT_MENU,
  EDIT,
  EXPORT,
  REMOVE,
  REPLACE_REFERENCE_WITH_WIDGET_COPY,
}

export class WidgetComponentAction {
  event: MouseEvent;
  actionType: WidgetComponentActionType;
}

// @dynamic
@Component({
  selector: 'tb-widget-container',
  templateUrl: './widget-container.component.html',
  styleUrls: ['./widget-container.component.scss'],
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WidgetContainerComponent extends PageComponent implements OnInit, OnChanges, AfterViewInit, OnDestroy {

  @HostBinding('class')
  widgetContainerClass = 'tb-widget-container';

  @ViewChild('tbWidgetElement', {static: true})
  tbWidgetElement: ElementRef;

  @Input()
  gridsterItem: GridsterItemComponent;

  @Input()
  widget: DashboardWidget;

  @Input()
  dashboardStyle: {[klass: string]: any};

  @Input()
  backgroundImage: SafeStyle | string;

  @Input()
  isEdit: boolean;

  @Input()
  isEditingWidget: boolean;

  @Input()
  isPreview: boolean;

  @Input()
  isMobile: boolean;

  @Input()
  dashboardWidgets: DashboardWidgets;

  @Input()
  isEditActionEnabled: boolean;

  @Input()
  isExportActionEnabled: boolean;

  @Input()
  isRemoveActionEnabled: boolean;

  @Input()
  disableWidgetInteraction = false;

  @Output()
  widgetFullscreenChanged: EventEmitter<boolean> = new EventEmitter<boolean>();

  @Output()
  widgetComponentAction: EventEmitter<WidgetComponentAction> = new EventEmitter<WidgetComponentAction>();

  widgetExportType = WidgetExportType;
  widgetExportTypeTranslations = widgetExportTypeTranslationMap;

  hovered = false;
  isReferenceWidget = false;

  get widgetEditActionsEnabled(): boolean {
    return (this.isEditActionEnabled || this.isRemoveActionEnabled || this.isExportActionEnabled) && !this.widget?.isFullscreen;
  }

  private cssClass: string;

  private editWidgetActionsTooltip: ITooltipsterInstance;

  constructor(protected store: Store<AppState>,
              private cd: ChangeDetectorRef,
              private renderer: Renderer2,
              private container: ViewContainerRef,
              private dashboardUtils: DashboardUtilsService,
              private utils: UtilsService) {
    super(store);
  }

  ngOnInit(): void {
    this.widget.widgetContext.containerChangeDetector = this.cd;
    const cssString = this.widget.widget.config.widgetCss;
    if (isNotEmptyStr(cssString)) {
      this.cssClass =
        this.utils.applyCssToElement(this.renderer, this.gridsterItem.el, 'tb-widget-css', cssString);
    }
    $(this.gridsterItem.el).on('mousedown', (e) => this.onMouseDown(e.originalEvent));
    $(this.gridsterItem.el).on('click', (e) => this.onClicked(e.originalEvent));
    $(this.gridsterItem.el).on('contextmenu', (e) => this.onContextMenu(e.originalEvent));
    this.initEditWidgetActionTooltip();
  }

  ngAfterViewInit(): void {
    this.widget.widgetContext.$widgetElement = $(this.tbWidgetElement.nativeElement);
  }

  ngOnChanges(changes: SimpleChanges) {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (['isEditActionEnabled', 'isRemoveActionEnabled', 'isExportActionEnabled'].includes(propName)) {
          this.updateEditWidgetActionsTooltipState();
        }
      }
    }
  }

  ngOnDestroy(): void {
    if (this.cssClass) {
      this.utils.clearCssElement(this.renderer, this.cssClass);
    }
    if (this.editWidgetActionsTooltip) {
      this.editWidgetActionsTooltip.destroy();
    }
  }

  isHighlighted(widget: DashboardWidget) {
    return this.dashboardWidgets.isHighlighted(widget);
  }

  isNotHighlighted(widget: DashboardWidget) {
    return this.dashboardWidgets.isNotHighlighted(widget);
  }

  onFullscreenChanged(expanded: boolean) {
    if (expanded) {
      this.renderer.addClass(this.tbWidgetElement.nativeElement, this.cssClass);
    } else {
      this.renderer.removeClass(this.tbWidgetElement.nativeElement, this.cssClass);
    }
    this.widgetFullscreenChanged.emit(expanded);
  }

  onMouseDown(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.MOUSE_DOWN
    });
  }

  onClicked(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.CLICKED
    });
  }

  onContextMenu(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.CONTEXT_MENU
    });
  }

  onEdit(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.EDIT
    });
  }

  onReplaceReferenceWithWidgetCopy(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.REPLACE_REFERENCE_WITH_WIDGET_COPY
    });
  }

  onExport(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.EXPORT
    });
  }

  onRemove(event: MouseEvent) {
    this.widgetComponentAction.emit({
      event,
      actionType: WidgetComponentActionType.REMOVE
    });
  }

  updateEditWidgetActionsTooltipState() {
    if (this.editWidgetActionsTooltip) {
      if (this.widgetEditActionsEnabled) {
        this.editWidgetActionsTooltip.enable();
      } else {
        this.editWidgetActionsTooltip.disable();
      }
    }
  }

  private initEditWidgetActionTooltip() {
    from(import('tooltipster')).subscribe(() => {
      $(this.gridsterItem.el).tooltipster({
        delay: this.widget.selected ? [0, 10000000] : [0, 100],
        distance: 2,
        zIndex: 151,
        arrow: false,
        theme: ['tb-widget-edit-actions-tooltip'],
        interactive: true,
        trigger: 'custom',
        triggerOpen: {
          mouseenter: true
        },
        triggerClose: {
          mouseleave: true
        },
        side: ['top'],
        trackOrigin: true,
        trackerInterval: 25,
        content: '',
        functionPosition: (instance, helper, position) => {
          const clientRect = helper.origin.getBoundingClientRect();
          position.coord.left = clientRect.right - position.size.width;
          position.target = clientRect.right;
          return position;
        },
        functionReady: (_instance, helper) => {
          const tooltipEl = $(helper.tooltip);
          tooltipEl.on('mouseenter', () => {
            this.hovered = true;
            this.cd.markForCheck();
          });
          tooltipEl.on('mouseleave', () => {
            this.hovered = false;
            this.cd.markForCheck();
          });
        },
        functionAfter: () => {
          this.hovered = false;
          this.cd.markForCheck();
        },
        functionBefore: () => {
          this.widget.isReference = this.dashboardUtils.isReferenceWidget(
            this.widget.widgetContext.dashboard.stateController.dashboardCtrl.dashboardCtx.getDashboard(), this.widget.widgetId);
          componentRef.instance.cd.detectChanges();
        }
      });
      this.editWidgetActionsTooltip = $(this.gridsterItem.el).tooltipster('instance');
      const componentRef = this.container.createComponent(EditWidgetActionsTooltipComponent);
      componentRef.instance.container = this;
      componentRef.instance.viewInited.subscribe(() => {
        if (this.editWidgetActionsTooltip.status().open) {
          this.editWidgetActionsTooltip.reposition();
        }
      });
      this.editWidgetActionsTooltip.on('destroyed', () => {
        componentRef.destroy();
      });
      const parentElement = componentRef.instance.element.nativeElement;
      const content = parentElement.firstChild;
      parentElement.removeChild(content);
      parentElement.style.display = 'none';
      this.editWidgetActionsTooltip.content(content);
      this.updateEditWidgetActionsTooltipState();
      this.widget.onSelected((selected) =>
        this.updateEditWidgetActionsTooltipSelectedState(selected));
    });
  }

  private updateEditWidgetActionsTooltipSelectedState(selected: boolean) {
    if (this.editWidgetActionsTooltip) {
      if (selected) {
        this.editWidgetActionsTooltip.option('delay', [0, 10000000]);
        this.editWidgetActionsTooltip.option('triggerClose', {
          mouseleave: false
        });
        if (this.widgetEditActionsEnabled) {
          this.editWidgetActionsTooltip.open();
        }
      } else {
        this.editWidgetActionsTooltip.option('delay', [0, 100]);
        this.editWidgetActionsTooltip.option('triggerClose', {
          mouseleave: true
        });
        this.editWidgetActionsTooltip.close();
      }
    }
  }

}

@Component({
  template: `
    <div class="tb-widget-action-container">
      <div class="tb-widget-reference-panel tb-primary-fill" *ngIf="container.widget.isReference">
        {{ 'widget.reference' | translate }}
        <button mat-icon-button class="tb-mat-16"
                color="primary"
                [fxShow]="container.isEditActionEnabled"
                (click)="container.onReplaceReferenceWithWidgetCopy($event)"
                matTooltip="{{ 'widget.replace-reference-with-widget-copy' | translate }}"
                matTooltipPosition="above">
          <tb-icon matButtonIcon>mdi:file-replace-outline</tb-icon>
        </button>
      </div>
      <div class="tb-widget-actions-panel">
        <button mat-icon-button class="tb-mat-20"
                [fxShow]="container.isEditActionEnabled"
                (click)="container.onEdit($event)"
                matTooltip="{{ 'widget.edit' | translate }}"
                matTooltipPosition="above">
          <tb-icon>edit</tb-icon>
        </button>
        <button mat-icon-button class="tb-mat-20"
                [fxShow]="container.isExportActionEnabled"
                (click)="container.onExport($event)"
                matTooltip="{{ 'widget.export' | translate }}"
                matTooltipPosition="above">
          <tb-icon>file_download</tb-icon>
        </button>
        <button mat-icon-button class="tb-mat-20"
                [fxShow]="container.isRemoveActionEnabled"
                (click)="container.onRemove($event);"
                matTooltip="{{ 'widget.remove' | translate }}"
                matTooltipPosition="above">
          <tb-icon>close</tb-icon>
        </button>
      </div>
    </div>`,
  styles: [],
  encapsulation: ViewEncapsulation.None
})
export class EditWidgetActionsTooltipComponent implements AfterViewInit {

  @Input()
  container: WidgetContainerComponent;

  @Output()
  viewInited = new EventEmitter();

  constructor(public element: ElementRef<HTMLElement>,
              public cd: ChangeDetectorRef) {
  }

  ngAfterViewInit() {
    this.viewInited.emit();
  }
}
