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

import { Component, DestroyRef, Inject, OnInit, SkipSelf } from '@angular/core';
import { ErrorStateMatcher } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  FormGroupDirective,
  NgForm,
  UntypedFormBuilder,
  UntypedFormControl,
  UntypedFormGroup,
  Validators
} from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { ImageService } from '@core/http/image.service';
import { ImageResource, ImageResourceInfo, imageResourceType, ResourceSubType } from '@shared/models/resource.models';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { forkJoin } from 'rxjs';
import { blobToBase64, blobToText, updateFileContent } from '@core/utils';
import {
  emptyMetadata,
  ScadaSymbolMetadata,
  parseScadaSymbolMetadataFromContent,
  updateScadaSymbolMetadataInContent
} from '@home/components/widget/lib/scada/scada-symbol.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActionNotificationShow } from '@core/notification/notification.actions';

export interface UploadImageDialogData {
  imageSubType: ResourceSubType;
  image?: ImageResourceInfo;
}

export interface UploadImageDialogResult {
  image?: ImageResource;
  scadaSymbolContent?: string;
}

@Component({
  selector: 'tb-upload-image-dialog',
  templateUrl: './upload-image-dialog.component.html',
  providers: [{provide: ErrorStateMatcher, useExisting: UploadImageDialogComponent}],
  styleUrls: []
})
export class UploadImageDialogComponent extends
  DialogComponent<UploadImageDialogComponent, UploadImageDialogResult> implements OnInit, ErrorStateMatcher {

  uploadImageFormGroup: UntypedFormGroup;

  uploadImage = true;

  submitted = false;

  maxResourceSize = getCurrentAuthState(this.store).maxResourceSize;

  get isScada() {
    return this.data.imageSubType === ResourceSubType.SCADA_SYMBOL;
  }

  private scadaSymbolContent: string;
  private scadaSymbolMetadata: ScadaSymbolMetadata;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              private imageService: ImageService,
              @Inject(MAT_DIALOG_DATA) public data: UploadImageDialogData,
              @SkipSelf() private errorStateMatcher: ErrorStateMatcher,
              public dialogRef: MatDialogRef<UploadImageDialogComponent, UploadImageDialogResult>,
              public fb: UntypedFormBuilder,
              private destroyRef: DestroyRef) {
    super(store, router, dialogRef);
  }

  ngOnInit(): void {
    this.uploadImage = !this.data?.image;
    this.uploadImageFormGroup = this.fb.group({
      file: [this.data?.image?.link, [Validators.required]]
    });
    if (this.uploadImage) {
      this.uploadImageFormGroup.addControl('title', this.fb.control(null, [Validators.required]));
      if (this.isScada) {
        this.uploadImageFormGroup.get('file').valueChanges.pipe(
          takeUntilDestroyed(this.destroyRef)
        ).subscribe((file: File) => {
          if (file) {
            blobToText(file).subscribe(content => {
              this.scadaSymbolContent = content;
              this.scadaSymbolMetadata = parseScadaSymbolMetadataFromContent(this.scadaSymbolContent);
              const titleControl = this.uploadImageFormGroup.get('title');
              if (this.scadaSymbolMetadata.title && (!titleControl.value || !titleControl.touched)) {
                titleControl.setValue(this.scadaSymbolMetadata.title);
              }
            });
          }
        });
      }
    }
  }

  imageFileNameChanged(fileName: string) {
    if (this.uploadImage) {
      const titleControl = this.uploadImageFormGroup.get('title');
      if (!titleControl.value || !titleControl.touched) {
        titleControl.setValue(fileName);
      }
    }
  }

  isErrorState(control: UntypedFormControl | null, form: FormGroupDirective | NgForm | null): boolean {
    const originalErrorState = this.errorStateMatcher.isErrorState(control, form);
    const customErrorState = !!(control && control.invalid && this.submitted);
    return originalErrorState || customErrorState;
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  upload(): void {
    this.submitted = true;
    let file: File = this.uploadImageFormGroup.get('file').value;
    try {
      if (this.uploadImage) {
        const title: string = this.uploadImageFormGroup.get('title').value;
        if (this.isScada) {
          if (!this.scadaSymbolMetadata) {
            this.scadaSymbolMetadata = emptyMetadata();
          }
          if (this.scadaSymbolMetadata.title !== title) {
            this.scadaSymbolMetadata.title = title;
          }
          const newContent = updateScadaSymbolMetadataInContent(this.scadaSymbolContent, this.scadaSymbolMetadata);
          file = updateFileContent(file, newContent);
        }
        forkJoin([
          this.imageService.uploadImage(file, title, this.data.imageSubType),
          blobToBase64(file)
        ]).subscribe(([imageInfo, base64]) => {
          this.dialogRef.close({image: Object.assign(imageInfo, {base64})});
        });
      } else {
        if (this.isScada) {
          blobToText(file).subscribe(scadaSymbolContent => {
            this.dialogRef.close({scadaSymbolContent});
          });
        } else {
          const image = this.data.image;
          forkJoin([
            this.imageService.updateImage(imageResourceType(image), image.resourceKey, file),
            blobToBase64(file)
          ]).subscribe(([imageInfo, base64]) => {
            this.dialogRef.close({image:Object.assign(imageInfo, {base64})});
          });
        }
      }
    } catch (e) {
      this.store.dispatch(new ActionNotificationShow({
        message: e.message,
        type: 'error',
        verticalPosition: 'bottom',
        horizontalPosition: 'right',
        target: 'uploadRoot'
      }));
    }
  }
}
