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
  OnChanges,
  OnInit,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import {
  ControlValueAccessor,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validator,
  Validators
} from '@angular/forms';
import { PageComponent } from '@shared/components/page.component';
import { emptyMetadata, ScadaSymbolMetadata } from '@home/components/widget/lib/scada/scada-symbol.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ToggleHeaderOption } from '@shared/components/toggle-header.component';
import { TranslateService } from '@ngx-translate/core';
import {
  ScadaSymbolMetadataTagsComponent
} from '@home/pages/scada-symbol/metadata-components/scada-symbol-metadata-tags.component';
import { TbEditorCompleter } from '@shared/models/ace/completion.models';
import {
  clickActionFunctionCompletions,
  elementStateRenderFunctionCompletions,
  generalStateRenderFunctionCompletions,
  scadaSymbolContextCompletion,
  scadaSymbolGeneralStateHighlightRules
} from '@home/pages/scada-symbol/scada-symbol-editor.models';
import { CustomTranslatePipe } from '@shared/pipe/custom-translate.pipe';
import { IAliasController } from '@core/api/widget-api.models';
import { WidgetActionCallbacks } from '@home/components/widget/action/manage-widget-actions.component.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { WhiteLabelingService } from '@core/http/white-labeling.service';

@Component({
  selector: 'tb-scada-symbol-metadata',
  templateUrl: './scada-symbol-metadata.component.html',
  styleUrls: ['./scada-symbol-metadata.component.scss'],
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => ScadaSymbolMetadataComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => ScadaSymbolMetadataComponent),
      multi: true
    }
  ],
  encapsulation: ViewEncapsulation.None
})
export class ScadaSymbolMetadataComponent extends PageComponent implements OnInit, OnChanges, ControlValueAccessor, Validator {

  @ViewChild('symbolMetadataTags')
  symbolMetadataTags: ScadaSymbolMetadataTagsComponent;

  @Input()
  aliasController: IAliasController;

  @Input()
  callbacks: WidgetActionCallbacks;

  @Input()
  disabled: boolean;

  @Input()
  tags: string[];

  modelValue: ScadaSymbolMetadata;

  private propagateChange = null;

  public metadataFormGroup: UntypedFormGroup;

  headerOptions: ToggleHeaderOption[] = [
    {
      name: this.translate.instant('scada.general'),
      value: 'general'
    },
    {
      name: this.translate.instant('scada.tags'),
      value: 'tags'
    },
    {
      name: this.translate.instant('scada.behavior.behavior'),
      value: 'behavior'
    },
    {
      name: this.translate.instant('scada.properties'),
      value: 'properties'
    }
  ];

  selectedOption = 'general';

  generalStateRenderFunctionCompleter: TbEditorCompleter;
  elementStateRenderFunctionCompleter: TbEditorCompleter;
  clickActionFunctionCompleter: TbEditorCompleter;

  highlightRules = scadaSymbolGeneralStateHighlightRules;

  constructor(protected store: Store<AppState>,
              private fb: UntypedFormBuilder,
              private translate: TranslateService,
              private customTranslate: CustomTranslatePipe,
              private destroyRef: DestroyRef,
              private wl: WhiteLabelingService) {
    super(store);
  }

  ngOnInit(): void {
    this.metadataFormGroup = this.fb.group({
      title: [null, [Validators.required]],
      description: [null],
      searchTags: [null],
      widgetSizeX: [null, [Validators.min(1), Validators.max(24), Validators.required]],
      widgetSizeY: [null, [Validators.min(1), Validators.max(24), Validators.required]],
      stateRenderFunction: [null],
      tags: [null],
      behavior: [null],
      properties: [null]
    });
    this.updateFunctionCompleters(emptyMetadata());

    this.metadataFormGroup.valueChanges.pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => {
      this.updateModel();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (['tags'].includes(propName)) {
          this.updateFunctionCompleters(this.modelValue);
        }
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
      this.metadataFormGroup.disable({emitEvent: false});
    } else {
      this.metadataFormGroup.enable({emitEvent: false});
    }
  }

  writeValue(value: ScadaSymbolMetadata): void {
    this.modelValue = value;
    this.metadataFormGroup.patchValue(
      {
        title: value?.title,
        description: value?.description,
        searchTags: value?.searchTags,
        widgetSizeX: value?.widgetSizeX || 3,
        widgetSizeY: value?.widgetSizeY || 3,
        stateRenderFunction: value?.stateRenderFunction,
        tags: value?.tags,
        behavior: value?.behavior,
        properties: value?.properties
      }, {emitEvent: false}
    );
    this.updateFunctionCompleters(value);
  }

  editTagStateRenderFunction(tag: string): void {
    this.selectedOption = 'tags';
    this.symbolMetadataTags.editTagStateRenderFunction(tag);
  }

  editTagClickAction(tag: string): void {
    this.selectedOption = 'tags';
    this.symbolMetadataTags.editTagClickAction(tag);
  }

  public validate(c: UntypedFormControl) {
    const valid = this.metadataFormGroup.valid;
    return valid ? null : {
      metadata: {
        valid: false,
      },
    };
  }

  private updateModel() {
    const metadata: ScadaSymbolMetadata = this.metadataFormGroup.getRawValue();
    this.modelValue = metadata;
    this.propagateChange(this.modelValue);
    this.updateFunctionCompleters(metadata);
  }

  private updateFunctionCompleters(metadata: ScadaSymbolMetadata) {
    const contextCompleter = scadaSymbolContextCompletion(metadata, this.tags, this.customTranslate, this.wl);
    const generalStateRender = generalStateRenderFunctionCompletions(contextCompleter);
    if (!this.generalStateRenderFunctionCompleter) {
      this.generalStateRenderFunctionCompleter = new TbEditorCompleter(generalStateRender);
    } else {
      this.generalStateRenderFunctionCompleter.updateCompletions(generalStateRender);
    }
    const elementStateRender = elementStateRenderFunctionCompletions(contextCompleter);
    if (!this.elementStateRenderFunctionCompleter) {
      this.elementStateRenderFunctionCompleter = new TbEditorCompleter(elementStateRender);
    } else {
      this.elementStateRenderFunctionCompleter.updateCompletions(elementStateRender);
    }
    const clickAction = clickActionFunctionCompletions(contextCompleter);
    if (!this.clickActionFunctionCompleter) {
      this.clickActionFunctionCompleter = new TbEditorCompleter(clickAction);
    } else {
      this.clickActionFunctionCompleter.updateCompletions(clickAction);
    }
  }
}
