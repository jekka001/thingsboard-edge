///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2021 ThingsBoard, Inc. All Rights Reserved.
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
  Compiler,
  Component,
  ComponentFactory,
  Injectable,
  Injector,
  NgModule,
  NgModuleRef,
  OnDestroy,
  Type
} from '@angular/core';
import { Observable, ReplaySubject } from 'rxjs';
import { CommonModule } from '@angular/common';

@NgModule()
export abstract class DynamicComponentModule implements OnDestroy {

  ngOnDestroy(): void {
  }

}

interface DynamicComponentModuleData {
  moduleRef: NgModuleRef<DynamicComponentModule>;
  moduleType: Type<DynamicComponentModule>;
}

@Injectable(
  {
    providedIn: 'root'
  }
)
export class DynamicComponentFactoryService {

  private dynamicComponentModulesMap = new Map<ComponentFactory<any>, DynamicComponentModuleData>();

  constructor(private compiler: Compiler,
              private injector: Injector) {
  }

  public createDynamicComponentFactory<T>(
                     componentType: Type<T>,
                     template: string,
                     modules?: Type<any>[]): Observable<ComponentFactory<T>> {
    const dymamicComponentFactorySubject = new ReplaySubject<ComponentFactory<T>>();
    import('@angular/compiler').then(
      () => {
        const comp = this.createDynamicComponent(componentType, template);
        let moduleImports: Type<any>[] = [CommonModule];
        if (modules) {
          moduleImports = [...moduleImports, ...modules];
        }
        // noinspection AngularInvalidImportedOrDeclaredSymbol
        const dynamicComponentInstanceModule = NgModule({
          declarations: [comp],
          imports: moduleImports
        })(class DynamicComponentInstanceModule extends DynamicComponentModule {});
        try {
          this.compiler.compileModuleAsync(dynamicComponentInstanceModule).then(
            (module) => {
              const moduleRef = module.create(this.injector);
              const factory = moduleRef.componentFactoryResolver.resolveComponentFactory(comp);
              this.dynamicComponentModulesMap.set(factory, {
                moduleRef,
                moduleType: module.moduleType
              });
              dymamicComponentFactorySubject.next(factory);
              dymamicComponentFactorySubject.complete();
            }
          ).catch(
            (e) => {
              dymamicComponentFactorySubject.error(e);
            }
          );
        } catch (e) {
          dymamicComponentFactorySubject.error(e);
        }
      }
    );
    return dymamicComponentFactorySubject.asObservable();
  }

  public destroyDynamicComponentFactory<T>(factory: ComponentFactory<T>) {
    const moduleData = this.dynamicComponentModulesMap.get(factory);
    if (moduleData) {
      moduleData.moduleRef.destroy();
      this.compiler.clearCacheFor(moduleData.moduleType);
      this.dynamicComponentModulesMap.delete(factory);
    }
  }

  private createDynamicComponent<T>(componentType: Type<T>, template: string): Type<T> {
    // noinspection AngularMissingOrInvalidDeclarationInModule
    return Component({
      template
    })(componentType);
  }

}
