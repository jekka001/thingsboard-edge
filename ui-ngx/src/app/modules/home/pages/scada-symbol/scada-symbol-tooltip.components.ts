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
  Component,
  ComponentRef,
  Directive,
  ElementRef,
  EventEmitter,
  Input,
  NgModule, OnDestroy,
  OnInit,
  Output,
  Type,
  ViewChild,
  ViewContainerRef,
  ViewEncapsulation
} from '@angular/core';
import { ScadaSymbolElement } from '@home/pages/scada-symbol/scada-symbol-editor.models';
import { CommonModule } from '@angular/common';
import { SharedModule } from '@shared/shared.module';
import { ENTER } from '@angular/cdk/keycodes';
import Timeout = NodeJS.Timeout;
import { MatButton } from '@angular/material/button';
import ITooltipsterInstance = JQueryTooltipster.ITooltipsterInstance;
import { TranslateService } from '@ngx-translate/core';

@Directive()
abstract class ScadaSymbolPanelComponent implements AfterViewInit {

  @Input()
  symbolElement: ScadaSymbolElement;

  @Output()
  viewInited = new EventEmitter();

  protected constructor(public element: ElementRef<HTMLElement>) {
  }

  ngAfterViewInit() {
    this.viewInited.emit();
  }
}

@Component({
  template: `<div class="tb-scada-symbol-tooltip-panel">
    <span>{{ symbolElement?.element?.type }}{{ symbolElement?.invisible ? ' (' + ('scada.hidden' | translate) + ')' : '' }}</span>
    <button mat-stroked-button color="primary" (click)="onAddTag()">
      <mat-icon>add</mat-icon>
      <span translate>scada.tag.add-tag</span>
    </button>
  </div>`,
  styleUrls: ['./scada-symbol-tooltip.component.scss'],
  encapsulation: ViewEncapsulation.None
})
class ScadaSymbolAddTagPanelComponent extends ScadaSymbolPanelComponent {

  @Output()
  addTag = new EventEmitter();

  constructor(public element: ElementRef<HTMLElement>) {
    super(element);
  }

  public onAddTag() {
    this.addTag.emit();
  }

}

@Component({
  template: `<div class="tb-scada-symbol-tooltip-panel">
    <span>{{ (isAdd ? 'scada.tag.enter-tag' : 'scada.tag.update-tag' ) | translate }}:</span>
    <mat-form-field class="tb-inline-field" appearance="outline" subscriptSizing="dynamic">
      <input #tagField matInput [(ngModel)]="tag" (keydown)="tagEnter($event)" (blur)="onBlur()"
             placeholder="{{ 'widget-config.set' | translate }}">
    </mat-form-field>
    <button type="button" mat-icon-button class="tb-mat-20"
            matTooltip="{{ 'action.apply' | translate }}"
            matTooltipPosition="above"
            [disabled]="!tag"
            (click)="onApply()">
      <mat-icon>done</mat-icon>
    </button>
    <button type="button" mat-icon-button class="tb-mat-20"
            matTooltip="{{ 'action.cancel' | translate }}"
            matTooltipPosition="above"
            (click)="onCancel()">
      <mat-icon>close</mat-icon>
    </button>
  </div>`,
  styleUrls: ['./scada-symbol-tooltip.component.scss'],
  encapsulation: ViewEncapsulation.None
})
class ScadaSymbolTagInputPanelComponent extends ScadaSymbolPanelComponent implements AfterViewInit, OnDestroy {

  @ViewChild('tagField')
  tagField: ElementRef<HTMLInputElement>;

  @Input()
  isAdd: boolean;

  @Input()
  tag: string;

  @Output()
  apply = new EventEmitter<string>();

  @Output()
  cancel = new EventEmitter();

  private closed = false;

  private blurTimeout: Timeout;

  constructor(public element: ElementRef<HTMLElement>) {
    super(element);
  }

  ngAfterViewInit() {
    super.ngAfterViewInit();
    setTimeout(() => {
      this.tagField.nativeElement.focus();
    });
  }

  ngOnDestroy() {
    if (this.blurTimeout) {
      clearTimeout(this.blurTimeout);
      this.blurTimeout = null;
    }
  }

  public tagEnter($event: KeyboardEvent) {
    if ($event.keyCode === ENTER) {
      $event.preventDefault();
      if (this.tag) {
        this.closed = true;
        this.apply.emit(this.tag);
      }
    }
  }

  public onApply() {
    this.closed = true;
    if (this.tag) {
      this.apply.emit(this.tag);
    } else {
      this.cancel.emit();
    }
  }

  public onCancel() {
    this.closed = true;
    this.cancel.emit();
  }

  public onBlur() {
    this.blurTimeout = setTimeout(() => {
      if (!this.closed) {
        this.closed = true;
        this.cancel.emit();
      }
    }, 300);
  }

}

@Component({
  template: `<div class="tb-scada-symbol-tooltip-panel">
    <span>{{ symbolElement?.element?.type }}{{ symbolElement?.invisible ? ' (' + ('scada.hidden' | translate) + ')' : '' }}:</span>
    <span><b>{{ symbolElement?.tag }}</b></span>
    <button *ngIf="!symbolElement?.readonly" type="button" mat-icon-button class="tb-mat-20"
            matTooltip="{{ 'scada.tag.update-tag' | translate }}"
            matTooltipPosition="above"
            (click)="onUpdateTag()">
      <mat-icon>edit</mat-icon>
    </button>
    <button *ngIf="displayTagSettings"
            #tagSettingsButton type="button"
            mat-icon-button class="tb-mat-20"
            matTooltip="{{ 'scada.tag.tag-settings' | translate }}"
            matTooltipPosition="above">
      <mat-icon>settings</mat-icon>
    </button>
    <button *ngIf="!symbolElement?.readonly"
            #removeTagButton
            type="button"
            mat-icon-button class="tb-mat-20"
            matTooltip="{{ 'scada.tag.remove-tag' | translate }}"
            matTooltipPosition="above">
      <mat-icon>delete</mat-icon>
    </button>
  </div>`,
  styleUrls: ['./scada-symbol-tooltip.component.scss'],
  encapsulation: ViewEncapsulation.None
})
class ScadaSymbolTagPanelComponent extends ScadaSymbolPanelComponent implements OnInit, AfterViewInit {

  @ViewChild('tagSettingsButton', {read: ElementRef})
  tagSettingsButton: ElementRef<HTMLElement>;

  @ViewChild('removeTagButton', {read: ElementRef})
  removeTagButton: ElementRef<HTMLElement>;

  @Output()
  updateTag = new EventEmitter();

  @Output()
  removeTag = new EventEmitter();

  displayTagSettings = true;

  constructor(public element: ElementRef<HTMLElement>,
              private container: ViewContainerRef) {
    super(element);
  }

  ngOnInit() {
    if (this.symbolElement?.readonly) {
      this.displayTagSettings = (this.symbolElement?.hasStateRenderFunction() || this.symbolElement?.hasClickAction());
    }
  }

  ngAfterViewInit() {
    super.ngAfterViewInit();
    setTimeout(() => {

      if (this.displayTagSettings) {
        const tagSettingsButton = $(this.tagSettingsButton.nativeElement);
        tagSettingsButton.tooltipster(
          {
            parent: this.symbolElement.tooltipContainer,
            zIndex: 200,
            arrow: true,
            theme: ['scada-symbol', 'tb-active'],
            interactive: true,
            trigger: 'click',
            trackOrigin: true,
            trackerInterval: 100,
            side: 'top',
            content: ''
          }
        );

        const scadaSymbolTagSettingsTooltip = tagSettingsButton.tooltipster('instance');
        setTooltipComponent(this.symbolElement, this.container, ScadaSymbolTagSettingsComponent, scadaSymbolTagSettingsTooltip);
      }

      if (!this.symbolElement.readonly) {
        const removeTagButton = $(this.removeTagButton.nativeElement);
        removeTagButton.tooltipster(
          {
            parent: this.symbolElement.tooltipContainer,
            zIndex: 200,
            arrow: true,
            theme: ['scada-symbol', 'tb-active'],
            interactive: true,
            trigger: 'click',
            trackOrigin: true,
            trackerInterval: 100,
            side: 'top',
            content: ''
          }
        );

        const scadaSymbolRemoveTagTooltip = removeTagButton.tooltipster('instance');
        const scadaSymbolRemoveTagCompRef =
          setTooltipComponent(this.symbolElement, this.container, ScadaSymbolRemoveTagConfirmComponent, scadaSymbolRemoveTagTooltip);
        scadaSymbolRemoveTagCompRef.instance.removeTag.subscribe(() => {
          scadaSymbolRemoveTagTooltip.destroy();
          this.removeTag.emit();
        });
        scadaSymbolRemoveTagCompRef.instance.cancel.subscribe(() => {
          scadaSymbolRemoveTagTooltip.close();
        });
        scadaSymbolRemoveTagTooltip.on('ready', () => {
          scadaSymbolRemoveTagCompRef.instance.yesButton.focus();
        });
      }
    });
  }

  public onUpdateTag() {
    this.updateTag.emit();
  }
}

@Component({
  template: `<div class="tooltipster-content tb-scada-symbol-tooltip-panel column">
    <div class="tb-confirm-text" [innerHTML]="deleteText"></div>
    <div class="tb-scada-symbol-tooltip-panel">
      <button mat-stroked-button color="primary" (click)="onCancel()">
        <mat-icon>close</mat-icon>
        <span translate>action.no</span>
      </button>
      <button #yesButton
              mat-stroked-button color="primary" (click)="onRemoveTag()">
        <mat-icon>done</mat-icon>
        <span translate>action.yes</span>
      </button>
    </div>
  </div>`,
  styleUrls: ['./scada-symbol-tooltip.component.scss'],
  encapsulation: ViewEncapsulation.None
})
class ScadaSymbolRemoveTagConfirmComponent extends ScadaSymbolPanelComponent implements OnInit, AfterViewInit {

  @ViewChild('yesButton')
  yesButton: MatButton;

  deleteText: string;

  @Output()
  cancel = new EventEmitter();

  @Output()
  removeTag = new EventEmitter();

  constructor(public element: ElementRef<HTMLElement>,
              private translate: TranslateService) {
    super(element);
  }

  ngOnInit() {
    this.deleteText = this.translate.instant('scada.tag.delete-tag-text',
      {tag: this.symbolElement?.tag, elementType: this.symbolElement?.element?.type});
  }

  public onCancel() {
    this.cancel.emit();
  }

  public onRemoveTag() {
    this.removeTag.emit();
  }
}

@Component({
  template: `<div class="tooltipster-content tb-scada-symbol-tooltip-panel column flex-start">
    <div *ngIf="!symbolElement?.readonly || hasStateRenderFunction" translate>scada.state-render-function</div>
    <button *ngIf="hasStateRenderFunction"
            mat-stroked-button
            color="primary"
            (click)="editStateRenderFunction()">
      <mat-icon>{{ symbolElement?.readonly ? 'visibility' : 'edit' }}</mat-icon>
      <span>{{ (symbolElement?.readonly ? 'action.view' : 'action.edit') | translate }}</span>
    </button>
    <button *ngIf="!hasStateRenderFunction && !symbolElement?.readonly"
            mat-stroked-button
            color="primary"
            (click)="editStateRenderFunction()">
      <mat-icon>add</mat-icon>
      <span translate>action.add</span>
    </button>
    <div *ngIf="!symbolElement?.readonly || hasClickAction" translate>scada.tag.on-click-action</div>
    <button *ngIf="hasClickAction"
            mat-stroked-button
            color="primary"
            (click)="editClickAction()">
      <mat-icon>{{ symbolElement?.readonly ? 'visibility' : 'edit' }}</mat-icon>
      <span>{{ (symbolElement?.readonly ? 'action.view' : 'action.edit') | translate }}</span>
    </button>
    <button *ngIf="!hasClickAction && !symbolElement?.readonly"
            mat-stroked-button
            color="primary"
            (click)="editClickAction()">
      <mat-icon>add</mat-icon>
      <span translate>action.add</span>
    </button>
  </div>`,
  styleUrls: ['./scada-symbol-tooltip.component.scss'],
  encapsulation: ViewEncapsulation.None
})
class ScadaSymbolTagSettingsComponent extends ScadaSymbolPanelComponent implements OnInit, AfterViewInit {

  hasStateRenderFunction = false;

  hasClickAction = false;

  constructor(public element: ElementRef<HTMLElement>) {
    super(element);
  }

  ngOnInit() {
    this.updateFunctionsState();
  }

  private updateFunctionsState() {
    this.hasStateRenderFunction = this.symbolElement.hasStateRenderFunction();
    this.hasClickAction = this.symbolElement.hasClickAction();
  }

  editStateRenderFunction() {
    this.symbolElement.editStateRenderFunction();
  }

  editClickAction() {
    this.symbolElement.editClickAction();
  }
}

@NgModule({
  declarations:
    [
      ScadaSymbolAddTagPanelComponent,
      ScadaSymbolTagInputPanelComponent,
      ScadaSymbolTagPanelComponent,
      ScadaSymbolRemoveTagConfirmComponent,
      ScadaSymbolTagSettingsComponent
    ],
  imports: [
    CommonModule,
    SharedModule
  ]
})
export class ScadaSymbolTooltipComponentsModule { }

export const setupAddTagPanelTooltip = (symbolElement: ScadaSymbolElement, container: ViewContainerRef) => {
  symbolElement.stopEdit();
  symbolElement.tooltip.off('close');
  const componentRef = setTooltipComponent(symbolElement, container, ScadaSymbolAddTagPanelComponent);
  componentRef.instance.addTag.subscribe(() => {
    componentRef.destroy();
    setupTagInputPanelTooltip(symbolElement, container, true);
  });
};

export const setupTagPanelTooltip = (symbolElement: ScadaSymbolElement, container: ViewContainerRef) => {
  symbolElement.stopEdit();
  symbolElement.unhighlight();
  const componentRef = setTooltipComponent(symbolElement, container, ScadaSymbolTagPanelComponent);
  componentRef.instance.updateTag.subscribe(() => {
    componentRef.destroy();
    setupTagInputPanelTooltip(symbolElement, container, false);
  });
  componentRef.instance.removeTag.subscribe(() => {
    componentRef.destroy();
    symbolElement.clearTag();
  });
  symbolElement.tooltip.open();
};

const setupTagInputPanelTooltip = (symbolElement: ScadaSymbolElement, container: ViewContainerRef, isAdd: boolean) => {

  symbolElement.startEdit(() => {
    if (isAdd) {
      symbolElement.unhighlight();
      symbolElement.tooltip.close();
    } else {
      componentRef.destroy();
      setupTagPanelTooltip(symbolElement, container);
    }
  });

  const componentRef = setTooltipComponent(symbolElement, container, ScadaSymbolTagInputPanelComponent);

  componentRef.instance.isAdd = isAdd;
  if (!isAdd) {
    componentRef.instance.tag = symbolElement.tag;
  }
  componentRef.instance.apply.subscribe((newTag) => {
    componentRef.destroy();
    if (isAdd) {
      symbolElement.tooltip.off('close');
    }
    symbolElement.setTag(newTag);
  });
  componentRef.instance.cancel.subscribe(() => {
    symbolElement.stopEdit(true);
  });
  if (isAdd) {
    symbolElement.tooltip.option('delay', [0, 10000000]);
    symbolElement.tooltip.on('close', () => {
      componentRef.destroy();
      symbolElement.tooltip.option('delay', [0, 300]);
      setupAddTagPanelTooltip(symbolElement, container);
    });
  }
};

const setTooltipComponent = <T extends ScadaSymbolPanelComponent>(symbolElement: ScadaSymbolElement,
                                                                  container: ViewContainerRef,
                                                                  componentType: Type<T>,
                                                                  tooltip?: ITooltipsterInstance): ComponentRef<T> => {
  if (!tooltip) {
    tooltip = symbolElement.tooltip;
  }
  const componentRef = container.createComponent(componentType);
  componentRef.instance.symbolElement = symbolElement;
  componentRef.instance.viewInited.subscribe(() => {
    if (tooltip.status().open) {
      tooltip.reposition();
    }
  });
  tooltip.on('destroyed', () => {
    componentRef.destroy();
  });
  const parentElement = componentRef.instance.element.nativeElement;
  const content = parentElement.firstChild;
  parentElement.removeChild(content);
  parentElement.style.display = 'none';
  tooltip.content(content);
  return componentRef;
};
