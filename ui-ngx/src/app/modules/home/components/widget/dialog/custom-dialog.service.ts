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

import { Inject, Injectable, Type } from '@angular/core';
import { Observable } from 'rxjs';
import { MatDialog, MatDialogConfig } from '@angular/material/dialog';
import { DynamicComponentFactoryService } from '@core/services/dynamic-component-factory.service';
import { CommonModule } from '@angular/common';
import { mergeMap, tap } from 'rxjs/operators';
import { CustomDialogComponent } from './custom-dialog.component';
import {
  CustomDialogContainerComponent,
  CustomDialogContainerData
} from '@home/components/widget/dialog/custom-dialog-container.component';
import { SHARED_MODULE_TOKEN } from '@shared/components/tokens';
import {
  HOME_COMPONENTS_MODULE_TOKEN,
  SHARED_HOME_COMPONENTS_MODULE_TOKEN,
  WIDGET_COMPONENTS_MODULE_TOKEN
} from '@home/components/tokens';

@Injectable()
export class CustomDialogService {

  private customImports: Array<Type<any>>;

  constructor(
    private dynamicComponentFactoryService: DynamicComponentFactoryService,
    @Inject(SHARED_MODULE_TOKEN) private sharedModule: Type<any>,
    @Inject(SHARED_HOME_COMPONENTS_MODULE_TOKEN) private sharedHomeComponentsModule: Type<any>,
    @Inject(HOME_COMPONENTS_MODULE_TOKEN) private homeComponentsModule: Type<any>,
    @Inject(WIDGET_COMPONENTS_MODULE_TOKEN) private widgetComponentsModule: Type<any>,
    public dialog: MatDialog
  ) {
  }

  setAdditionalImports(imports: Array<Type<any>>) {
    this.customImports = imports;
  }

  customDialog(template: string, controller: (instance: CustomDialogComponent) => void, data?: any,
               config?: MatDialogConfig): Observable<any> {
    const imports = [this.sharedModule, CommonModule, this.sharedHomeComponentsModule, this.homeComponentsModule,
      this.widgetComponentsModule];
    if (Array.isArray(this.customImports)) {
      imports.push(...this.customImports);
    }
    return this.dynamicComponentFactoryService.createDynamicComponent(
      class CustomDialogComponentInstance extends CustomDialogComponent {}, template, imports).pipe(
      mergeMap((componentType) => {
          const dialogData: CustomDialogContainerData = {
            controller,
            customComponentType: componentType,
            data
          };
          let dialogConfig: MatDialogConfig = {
            disableClose: true,
            panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
            data: dialogData
          };
          if (config) {
            dialogConfig = {...dialogConfig, ...config};
          }
          return this.dialog.open<CustomDialogContainerComponent, CustomDialogContainerData, any>(
            CustomDialogContainerComponent,
            dialogConfig).afterClosed().pipe(
            tap(() => {
              this.dynamicComponentFactoryService.destroyDynamicComponent(componentType);
            })
          );
        }
      ));
  }

}

