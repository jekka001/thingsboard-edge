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
  ChangeDetectorRef,
  Component,
  EventEmitter,
  HostBinding,
  OnDestroy,
  OnInit,
  ViewChild,
  ViewEncapsulation
} from '@angular/core';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ActivatedRoute } from '@angular/router';
import { map, switchMap, takeUntil } from 'rxjs/operators';
import { Subject } from 'rxjs';
import { ScadaSymbolData, ScadaSymbolEditObjectCallbacks } from '@home/pages/scada-symbol/scada-symbol-editor.models';
import {
  parseScadaSymbolMetadataFromContent,
  ScadaSymbolMetadata,
  ScadaSymbolObjectSettings,
  updateScadaSymbolMetadataInContent
} from '@home/components/widget/lib/scada/scada-symbol.models';
import { UntypedFormBuilder, UntypedFormGroup } from '@angular/forms';
import { createFileFromContent, deepClone } from '@core/utils';
import {
  ScadaSymbolEditorComponent,
  ScadaSymbolEditorData
} from '@home/pages/scada-symbol/scada-symbol-editor.component';
import { ImageService } from '@core/http/image.service';
import { imageResourceType, IMAGES_URL_PREFIX, TB_IMAGE_PREFIX } from '@shared/models/resource.models';
import { HasDirtyFlag } from '@core/guards/confirm-on-exit.guard';
import { IAliasController, IStateController, StateParams } from '@core/api/widget-api.models';
import { EntityAliases } from '@shared/models/alias.models';
import { Filters } from '@shared/models/query/query.models';
import { AliasController } from '@core/api/alias-controller';
import { EntityService } from '@core/http/entity.service';
import { UtilsService } from '@core/services/utils.service';
import { TranslateService } from '@ngx-translate/core';
import { Widget, WidgetConfig, widgetType, WidgetTypeDetails } from '@shared/models/widget.models';
import {
  scadaSymbolWidgetDefaultSettings,
  ScadaSymbolWidgetSettings
} from '@home/components/widget/lib/scada/scada-symbol-widget.models';
import { WidgetActionCallbacks } from '@home/components/widget/action/manage-widget-actions.component.models';
import {
  ScadaSymbolMetadataComponent
} from '@home/pages/scada-symbol/metadata-components/scada-symbol-metadata.component';
import { getCurrentAuthUser } from '@core/auth/auth.selectors';
import { Authority } from '@shared/models/authority.enum';
import { NULL_UUID } from '@shared/models/id/has-uuid';
import {
  UploadImageDialogComponent,
  UploadImageDialogData,
  UploadImageDialogResult
} from '@shared/components/image/upload-image-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { colorBackground } from '@shared/models/widget-settings.models';
import { GridType } from 'angular-gridster2';
import {
  SaveWidgetTypeAsDialogComponent,
  SaveWidgetTypeAsDialogData,
  SaveWidgetTypeAsDialogResult
} from '@home/pages/widget/save-widget-type-as-dialog.component';
import { WidgetService } from '@core/http/widget.service';
import { Operation, Resource } from '@shared/models/security.models';
import { UserPermissionsService } from '@core/http/user-permissions.service';

@Component({
  selector: 'tb-scada-symbol',
  templateUrl: './scada-symbol.component.html',
  styleUrls: ['./scada-symbol.component.scss'],
  encapsulation: ViewEncapsulation.None
})
export class ScadaSymbolComponent extends PageComponent
  implements OnInit, OnDestroy, HasDirtyFlag, ScadaSymbolEditObjectCallbacks {

  widgetType = widgetType;

  GridType = GridType;

  @HostBinding('style.width') width = '100%';
  @HostBinding('style.height') height = '100%';

  @ViewChild('symbolEditor', {static: false})
  symbolEditor: ScadaSymbolEditorComponent;

  @ViewChild('symbolMetadata')
  symbolMetadata: ScadaSymbolMetadataComponent;

  symbolData: ScadaSymbolData;
  symbolEditorData: ScadaSymbolEditorData;

  previewMode = false;
  previewMetadata: ScadaSymbolMetadata;

  scadaSymbolFormGroup: UntypedFormGroup;

  scadaPreviewFormGroup: UntypedFormGroup;

  private destroy$ = new Subject<void>();

  private origSymbolData: ScadaSymbolData;

  updateBreadcrumbs = new EventEmitter();

  aliasController: IAliasController;

  widgetActionCallbacks: WidgetActionCallbacks = {
    fetchDashboardStates: () => [],
    fetchCellClickColumns: () => []
  };

  previewWidget: Widget;

  previewWidgets: Array<Widget> = [];

  tags: string[];

  editObjectCallbacks: ScadaSymbolEditObjectCallbacks = this;

  symbolEditorDirty = false;

  symbolEditorValid = true;

  private previewScadaSymbolObjectSettings: ScadaSymbolObjectSettings;

  private forcePristine = false;

  private authUser = getCurrentAuthUser(this.store);

  readonly: boolean;

  showHiddenElements = false;

  showCreateWidgetButton = true;

  get isDirty(): boolean {
    return (this.scadaSymbolFormGroup.dirty || this.symbolEditorDirty) && !this.forcePristine;
  }

  set isDirty(value: boolean) {
    this.forcePristine = !value;
  }

  constructor(protected store: Store<AppState>,
              private route: ActivatedRoute,
              private fb: UntypedFormBuilder,
              private cd: ChangeDetectorRef,
              private entityService: EntityService,
              private utils: UtilsService,
              private translate: TranslateService,
              private imageService: ImageService,
              private widgetService: WidgetService,
              private userPermissionsService: UserPermissionsService,
              private dialog: MatDialog) {
    super(store);

    const authUser = getCurrentAuthUser(store);
    if (authUser.authority === Authority.CUSTOMER_USER) {
      this.showCreateWidgetButton = false;
    } else {
      this.showCreateWidgetButton = this.userPermissionsService.hasGenericPermission(Resource.WIDGET_TYPE, Operation.CREATE);
    }
  }

  ngOnInit(): void {
    this.scadaSymbolFormGroup = this.fb.group({
      metadata: [null]
    });
    this.scadaPreviewFormGroup = this.fb.group({
      scadaSymbolObjectSettings: [null]
    });

    const entitiAliases: EntityAliases = {};

    // @ts-ignore
    const stateController: IStateController = {
      getStateParams: (): StateParams => ({})
    };

    const filters: Filters = {};

    this.aliasController = new AliasController(this.utils,
      this.entityService,
      this.translate,
      () => stateController, entitiAliases, filters);

    this.route.data.pipe(
      takeUntil(this.destroy$)
    ).subscribe(
      () => {
        this.reset();
        this.init(this.route.snapshot.data.symbolData);
      }
    );
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  onApplyScadaSymbolConfig() {
    if (this.scadaSymbolFormGroup.valid) {
      if (this.symbolEditor.editorMode === 'xml') {
        const tags = this.symbolEditor.getTags();
        this.editObjectCallbacks.tagsUpdated(tags);
      }
      const metadata: ScadaSymbolMetadata = this.scadaSymbolFormGroup.get('metadata').value;
      const scadaSymbolContent = this.prepareScadaSymbolContent(metadata);
      const file = createFileFromContent(scadaSymbolContent, this.symbolData.imageResource.fileName,
        this.symbolData.imageResource.descriptor.mediaType);
      const type = imageResourceType(this.symbolData.imageResource);
      let imageInfoObservable =
        this.imageService.updateImage(type, this.symbolData.imageResource.resourceKey, file);
      if (metadata.title !== this.symbolData.imageResource.title) {
        imageInfoObservable = imageInfoObservable.pipe(
          switchMap(imageInfo => {
            imageInfo.title = metadata.title;
            return this.imageService.updateImageInfo(imageInfo);
          })
        );
      }
      imageInfoObservable.pipe(
        switchMap(imageInfo => this.imageService.getImageString(
            `${IMAGES_URL_PREFIX}/${type}/${encodeURIComponent(imageInfo.resourceKey)}`).pipe(
              map(content => ({
                imageResource: imageInfo,
                scadaSymbolContent: content
              }))
          ))
      ).subscribe(data => {
        this.init(data);
        this.updateBreadcrumbs.emit();
      });
    }
  }

  onRevertScadaSymbolConfig() {
    this.init(this.origSymbolData);
  }

  enterPreviewMode() {
    this.previewMetadata = this.scadaSymbolFormGroup.get('metadata').value;
    this.symbolData.scadaSymbolContent = this.prepareScadaSymbolContent(this.previewMetadata);
    this.previewScadaSymbolObjectSettings = {
      behavior: {},
      properties: {}
    };
    this.scadaPreviewFormGroup.patchValue({
      scadaSymbolObjectSettings: this.previewScadaSymbolObjectSettings
    }, {emitEvent: false});
    this.scadaPreviewFormGroup.markAsPristine();
    const settings: ScadaSymbolWidgetSettings = {...scadaSymbolWidgetDefaultSettings,
      ...{
          simulated: true,
          scadaSymbolUrl: null,
          scadaSymbolContent: this.symbolData.scadaSymbolContent,
          scadaSymbolObjectSettings: this.previewScadaSymbolObjectSettings,
          padding: '0',
          background: colorBackground('rgba(0,0,0,0)')
         }
    };
    this.previewWidget = {
      typeFullFqn: 'system.scada_symbol',
      type: widgetType.rpc,
      sizeX: this.previewMetadata.widgetSizeX || 3,
      sizeY: this.previewMetadata.widgetSizeY || 3,
      row: 0,
      col: 0,
      config: {
        settings,
        showTitle: false,
        dropShadow: false,
        padding: '0',
        margin: '0',
        backgroundColor: 'rgba(0,0,0,0)'
      }
    };
    this.previewWidgets = [this.previewWidget];
    this.previewMode = true;
  }

  exitPreviewMode() {
    this.symbolEditorData = {
      scadaSymbolContent: this.symbolData.scadaSymbolContent
    };
    this.previewMode = false;
  }

  onRevertPreviewSettings() {
    this.scadaPreviewFormGroup.patchValue({
      scadaSymbolObjectSettings: this.previewScadaSymbolObjectSettings
    }, {emitEvent: false});
    this.scadaPreviewFormGroup.markAsPristine();
  }

  onApplyPreviewSettings() {
    this.scadaPreviewFormGroup.markAsPristine();
    this.previewScadaSymbolObjectSettings = this.scadaPreviewFormGroup.get('scadaSymbolObjectSettings').value;
    this.updatePreviewWidgetSettings();
  }

  tagsUpdated(tags: string[]) {
    this.tags = tags;
  }

  tagHasStateRenderFunction(tag: string): boolean {
    const metadata: ScadaSymbolMetadata = this.scadaSymbolFormGroup.get('metadata').value;
    if (metadata.tags) {
      const found = metadata.tags.find(t => t.tag === tag);
      return !!found?.stateRenderFunction;
    }
    return false;
  }

  tagHasClickAction(tag: string): boolean {
    const metadata: ScadaSymbolMetadata = this.scadaSymbolFormGroup.get('metadata').value;
    if (metadata.tags) {
      const found = metadata.tags.find(t => t.tag === tag);
      return !!found?.actions?.click?.actionFunction;
    }
    return false;
  }

  editTagStateRenderFunction(tag: string) {
    this.symbolMetadata.editTagStateRenderFunction(tag);
  }

  editTagClickAction(tag: string) {
    this.symbolMetadata.editTagClickAction(tag);
  }

  onSymbolEditObjectDirty(dirty: boolean) {
    this.symbolEditorDirty = dirty;
  }

  onSymbolEditObjectValid(valid: boolean) {
    this.symbolEditorValid = valid;
  }

  updateScadaSymbol() {
    this.dialog.open<UploadImageDialogComponent, UploadImageDialogData,
      UploadImageDialogResult>(UploadImageDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        imageSubType: this.symbolData.imageResource.resourceSubType,
        image: this.symbolData.imageResource
      }
    }).afterClosed().subscribe((result) => {
      if (result?.scadaSymbolContent) {
        this.symbolData.scadaSymbolContent = result.scadaSymbolContent;
        this.symbolEditorData = {
          scadaSymbolContent: this.symbolData.scadaSymbolContent
        };
        this.symbolEditorDirty = true;
        this.symbolEditorValid = true;
      }
    });
  }

  downloadScadaSymbol() {
    let metadata: ScadaSymbolMetadata;
    if (this.scadaSymbolFormGroup.valid) {
      metadata = this.scadaSymbolFormGroup.get('metadata').value;
    } else {
      metadata = parseScadaSymbolMetadataFromContent(this.origSymbolData.scadaSymbolContent);
    }
    const linkElement = document.createElement('a');
    const scadaSymbolContent = this.prepareScadaSymbolContent(metadata);
    const blob = new Blob([scadaSymbolContent], { type: this.symbolData.imageResource.descriptor.mediaType });
    const url = URL.createObjectURL(blob);
    linkElement.setAttribute('href', url);
    linkElement.setAttribute('download', this.symbolData.imageResource.fileName);
    const clickEvent = new MouseEvent('click',
      {
        view: window,
        bubbles: true,
        cancelable: false
      }
    );
    linkElement.dispatchEvent(clickEvent);
  }

  createWidget() {
    const metadata: ScadaSymbolMetadata = this.scadaSymbolFormGroup.get('metadata').value;
    this.dialog.open<SaveWidgetTypeAsDialogComponent, SaveWidgetTypeAsDialogData,
      SaveWidgetTypeAsDialogResult>(SaveWidgetTypeAsDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        title: metadata.title,
        dialogTitle: 'scada.create-widget-from-symbol',
        saveAsActionTitle: 'action.create'
      }
    }).afterClosed().subscribe(
      (saveWidgetAsData) => {
        if (saveWidgetAsData) {
          this.widgetService.getWidgetType('system.scada_symbol').subscribe(
            (widgetTemplate) => {
              const symbolUrl = TB_IMAGE_PREFIX + this.symbolData.imageResource.link;
              const widget: WidgetTypeDetails = {
                image: symbolUrl,
                description: metadata.description,
                tags: metadata.searchTags,
                ...widgetTemplate
              };
              widget.fqn = undefined;
              widget.id = undefined;
              widget.name = saveWidgetAsData.widgetName;
              const descriptor = widget.descriptor;
              descriptor.sizeX = metadata.widgetSizeX;
              descriptor.sizeY = metadata.widgetSizeY;
              let controllerScriptBody: string;
              if (typeof descriptor.controllerScript === 'string') {
                controllerScriptBody = descriptor.controllerScript;
              } else {
                controllerScriptBody = descriptor.controllerScript.body;
              }
              controllerScriptBody = controllerScriptBody
                  .replace(/previewWidth: '\d*px'/gm, `previewWidth: '${metadata.widgetSizeX * 100}px'`);
              controllerScriptBody = controllerScriptBody
                  .replace(/previewHeight: '\d*px'/gm, `previewHeight: '${metadata.widgetSizeY * 100 + 20}px'`);
              if (typeof descriptor.controllerScript === 'string') {
                descriptor.controllerScript = controllerScriptBody;
              } else {
                descriptor.controllerScript.body = controllerScriptBody;
              }
              const config: WidgetConfig = JSON.parse(descriptor.defaultConfig);
              config.title = saveWidgetAsData.widgetName;
              config.settings = config.settings || {};
              config.settings.scadaSymbolUrl = symbolUrl;
              descriptor.defaultConfig = JSON.stringify(config);
              this.widgetService.saveWidgetType(widget).subscribe((saved) => {
                if (saveWidgetAsData.widgetBundleId) {
                  this.widgetService.addWidgetFqnToWidgetBundle(saveWidgetAsData.widgetBundleId, saved.fqn).subscribe();
                }
              });
            }
          );
        }
      }
    );
  }

  private updatePreviewWidgetSettings() {
    this.previewWidget = deepClone(this.previewWidget);
    this.previewWidget.config.settings.scadaSymbolObjectSettings = this.previewScadaSymbolObjectSettings;
    this.previewWidgets = [this.previewWidget];
  }

  private prepareScadaSymbolContent(metadata: ScadaSymbolMetadata): string {
    const svgContent = this.symbolEditor.getContent();
    return updateScadaSymbolMetadataInContent(svgContent, metadata);
  }

  private reset(): void {
    if (this.symbolMetadata) {
      this.symbolMetadata.selectedOption = 'general';
    }
    this.previewMode = false;
  }

  private init(data: ScadaSymbolData) {
    this.readonly = this.authUser.authority !== Authority.SYS_ADMIN && data.imageResource.tenantId.id === NULL_UUID;
    this.origSymbolData = data;
    this.symbolData = deepClone(data);
    this.symbolEditorData = {
      scadaSymbolContent: this.symbolData.scadaSymbolContent
    };
    const metadata = parseScadaSymbolMetadataFromContent(this.symbolData.scadaSymbolContent);
    this.scadaSymbolFormGroup.patchValue({
      metadata
    }, {emitEvent: false});
    if (this.readonly) {
      this.scadaSymbolFormGroup.disable({emitEvent: false});
    } else {
      this.scadaSymbolFormGroup.enable({emitEvent: false});
    }
    this.scadaSymbolFormGroup.markAsPristine();
    this.symbolEditorDirty = false;
    this.symbolEditorValid = true;
    this.cd.markForCheck();
  }
}

