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

import { ChangeDetectorRef, Component, Inject, Optional, Renderer2, ViewContainerRef } from '@angular/core';
import { EntityComponent } from '@home/components/entity/entity.component';
import { AppState } from '@core/core.state';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { FormBuilder, FormGroup, UntypedFormControl, Validators } from '@angular/forms';
import { randomAlphanumeric } from '@core/utils';
import { EntityType } from '@shared/models/entity-type.models';
import { MobileApp, MobileAppStatus, mobileAppStatusTranslations } from '@shared/models/mobile-app.models';
import { PlatformType, platformTypeTranslations } from '@shared/models/oauth2.models';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { TbPopoverService } from '@shared/components/popover.service';
import { ReleaseNotesPanelComponent } from '@home/pages/mobile/applications/release-notes-panel.component';

@Component({
  selector: 'tb-mobile-app',
  templateUrl: './mobile-app.component.html',
  styleUrls: ['./mobile-app.component.scss']
})
export class MobileAppComponent extends EntityComponent<MobileApp> {

  entityType = EntityType;

  platformTypes = [PlatformType.ANDROID, PlatformType.IOS];

  MobileAppStatus = MobileAppStatus;
  PlatformType = PlatformType;

  mobileAppStatuses = Object.keys(MobileAppStatus) as MobileAppStatus[];

  platformTypeTranslations = platformTypeTranslations;
  mobileAppStatusTranslations = mobileAppStatusTranslations;

  constructor(protected store: Store<AppState>,
              protected translate: TranslateService,
              @Optional() @Inject('entity') protected entityValue: MobileApp,
              @Optional() @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<MobileApp>,
              protected cd: ChangeDetectorRef,
              public fb: FormBuilder,
              private popoverService: TbPopoverService,
              private renderer: Renderer2,
              private viewContainerRef: ViewContainerRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: MobileApp): FormGroup {
    const form = this.fb.group({
      pkgName: [entity?.pkgName ? entity.pkgName : '', [Validators.required, Validators.maxLength(255),
        Validators.pattern(/^\S+$/)]],
      platformType: [entity?.platformType ? entity.platformType : PlatformType.ANDROID],
      appSecret: [entity?.appSecret ? entity.appSecret : btoa(randomAlphanumeric(64)), [Validators.required, this.base64Format]],
      status: [entity?.status ? entity.status : MobileAppStatus.DRAFT],
      versionInfo: this.fb.group({
        minVersion: [entity?.versionInfo?.minVersion ? entity.versionInfo.minVersion : ''],
        minVersionReleaseNotes: [entity?.versionInfo?.minVersionReleaseNotes ? entity.versionInfo.minVersionReleaseNotes : ''],
        latestVersion: [entity?.versionInfo?.latestVersion ? entity.versionInfo.latestVersion : ''],
        latestVersionReleaseNotes: [entity?.versionInfo?.latestVersionReleaseNotes ? entity.versionInfo.latestVersionReleaseNotes : ''],
      }),
      storeInfo: this.fb.group({
        storeLink: [entity?.storeInfo?.storeLink ? entity.storeInfo.storeLink : ''],
        sha256CertFingerprints: [entity?.storeInfo?.sha256CertFingerprints ? entity.storeInfo.sha256CertFingerprints : ''],
        appId: [entity?.storeInfo?.appId ? entity.storeInfo.appId : ''],
      }),
    });

    form.get('platformType').valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe((value: PlatformType) => {
      if (value === PlatformType.ANDROID) {
        form.get('storeInfo.sha256CertFingerprints').enable({emitEvent: false});
        form.get('storeInfo.appId').disable({emitEvent: false});
      } else if (value === PlatformType.IOS) {
        form.get('storeInfo.sha256CertFingerprints').disable({emitEvent: false});
        form.get('storeInfo.appId').enable({emitEvent: false});
      }
      form.get('storeInfo.storeLink').setValue('', {emitEvent: false});
    });

    form.get('status').valueChanges.pipe(
      takeUntilDestroyed()
    ).subscribe((value: MobileAppStatus) => {
      if (value !== MobileAppStatus.DRAFT) {
        form.get('storeInfo.storeLink').addValidators(Validators.required);
        form.get('storeInfo.sha256CertFingerprints').addValidators(Validators.required);
        form.get('storeInfo.appId').addValidators(Validators.required);
      } else {
        form.get('storeInfo.storeLink').clearValidators();
        form.get('storeInfo.sha256CertFingerprints').clearValidators();
        form.get('storeInfo.appId').clearValidators();
      }
      form.get('storeInfo.storeLink').updateValueAndValidity({emitEvent: false});
      form.get('storeInfo.sha256CertFingerprints').updateValueAndValidity({emitEvent: false});
      form.get('storeInfo.appId').updateValueAndValidity({emitEvent: false});
    });

    return form;
  }

  updateForm(entity: MobileApp) {
    this.entityForm.patchValue(entity, {emitEvent: false});
  }

  override updateFormState(): void {
    super.updateFormState();
    if (this.isEdit && this.entityForm && !this.isAdd) {
      this.entityForm.get('status').updateValueAndValidity({onlySelf: false});
      this.entityForm.get('platformType').disable({emitEvent: false});
      if (this.entityForm.get('platformType').value === PlatformType.ANDROID) {
        this.entityForm.get('storeInfo.appId').disable({emitEvent: false});
      } else if (this.entityForm.get('platformType').value === PlatformType.IOS) {
        this.entityForm.get('storeInfo.sha256CertFingerprints').disable({emitEvent: false});
      }
    }
    if (this.entityForm && this.isAdd) {
      this.entityForm.get('storeInfo.appId').disable({emitEvent: false});
    }
  }

  override prepareFormValue(value: MobileApp): MobileApp {
    value.storeInfo = this.entityForm.get('storeInfo').value;
    return super.prepareFormValue(value);
  }

  generateAppSecret($event: Event) {
    $event.stopPropagation();
    this.entityForm.get('appSecret').setValue(btoa(randomAlphanumeric(64)));
    this.entityForm.get('appSecret').markAsDirty();
  }

  editReleaseNote($event: Event, matButton: MatButton, isLatest: boolean) {
    if ($event) {
      $event.stopPropagation();
    }
    const trigger = matButton._elementRef.nativeElement;
    if (this.popoverService.hasPopover(trigger)) {
      this.popoverService.hidePopover(trigger);
    } else {
      const ctx: any = {
        disabled: !(this.isAdd || this.isEdit),
        isLatest: isLatest,
        releaseNotes: isLatest
          ? this.entityForm.get('versionInfo.latestVersionReleaseNotes').value
          : this.entityForm.get('versionInfo.minVersionReleaseNotes').value
      };
      const releaseNotesPanelPopover = this.popoverService.displayPopover(trigger, this.renderer,
        this.viewContainerRef, ReleaseNotesPanelComponent, ['leftOnly', 'leftBottomOnly', 'leftTopOnly'], true, null,
        ctx,
        {},
        {}, {}, false, () => {}, {padding: '16px 24px'});
      releaseNotesPanelPopover.tbComponentRef.instance.popover = releaseNotesPanelPopover;
      releaseNotesPanelPopover.tbComponentRef.instance.releaseNotesApplied.subscribe((releaseNotes) => {
        releaseNotesPanelPopover.hide();
        if (isLatest) {
          this.entityForm.get('versionInfo.latestVersionReleaseNotes').setValue(releaseNotes);
          this.entityForm.get('versionInfo.latestVersionReleaseNotes').markAsDirty();
        } else {
          this.entityForm.get('versionInfo.minVersionReleaseNotes').setValue(releaseNotes);
          this.entityForm.get('versionInfo.minVersionReleaseNotes').markAsDirty();
        }
      });
    }
  }

  private base64Format(control: UntypedFormControl): { [key: string]: boolean } | null {
    if (control.value === '') {
      return null;
    }
    try {
      const value = atob(control.value);
      if (value.length < 64) {
        return {minLength: true};
      }
      return null;
    } catch (e) {
      return {base64: true};
    }
  }
}
