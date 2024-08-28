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

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
  CMAssigneeType,
  CustomMenu, CustomMenuAssignResult,
  CustomMenuConfig,
  CustomMenuDeleteResult,
  CustomMenuInfo
} from '@shared/models/custom-menu.models';
import { mergeMap, Observable, of, Subject } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { PageLink } from '@shared/models/page/page-link';
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { PageData } from '@shared/models/page/page-data';
import { EntityInfoData } from '@shared/models/entity.models';

@Injectable({
  providedIn: 'root'
})
export class CustomMenuService {

  private customMenuConfig: CustomMenuConfig = null;

  private customMenuConfigChanged: Subject<CustomMenuConfig> = new Subject<CustomMenuConfig>();

  public customMenuConfigChanged$: Observable<CustomMenuConfig> = this.customMenuConfigChanged.asObservable();

  constructor(
    private http: HttpClient
  ) {}

  public getCustomMenu(): CustomMenuConfig {
    return this.customMenuConfig;
  }

  public loadCustomMenu(notify = false): Observable<CustomMenuConfig> {
    return this.http.get<CustomMenuConfig>('/api/customMenu')
    .pipe(
      tap((customMenuConfig) => {
        this.customMenuConfig = customMenuConfig;
        if (notify) {
          this.customMenuConfigChanged.next(customMenuConfig);
        }
      })
    );
  }

  public getCustomMenuInfos(pageLink: PageLink, config?: RequestConfig): Observable<PageData<CustomMenuInfo>> {
    return this.http.get<PageData<CustomMenuInfo>>(`/api/customMenu/infos${pageLink.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getCustomMenuInfo(customMenuId: string, config?: RequestConfig): Observable<CustomMenuInfo> {
    return this.http.get<CustomMenuInfo>(`/api/customMenu/${customMenuId}/info`, defaultHttpOptionsFromConfig(config));
  }

  public getCustomMenuConfig(customMenuId: string, config?: RequestConfig): Observable<CustomMenuConfig> {
    return this.http.get<CustomMenuConfig>(`/api/customMenu/${customMenuId}/config`, defaultHttpOptionsFromConfig(config));
  }

  public getCustomMenuAssigneeList(customMenuId: string, config?: RequestConfig): Observable<Array<EntityInfoData>> {
    return this.http.get<Array<EntityInfoData>>(`/api/customMenu/${customMenuId}/assigneeList`, defaultHttpOptionsFromConfig(config));
  }

  public updateCustomMenuConfig(customMenuId: string, customMenuConfig: CustomMenuConfig, config?: RequestConfig): Observable<CustomMenu> {
    return this.http.put<CustomMenu>(`/api/customMenu/${customMenuId}/config`, customMenuConfig,
      defaultHttpOptionsFromConfig(config)).pipe(
        mergeMap((res) => this.loadCustomMenu(true).pipe( map(() => res) ))
    );
  }

  public updateCustomMenuName(customMenuId: string, name: string, config?: RequestConfig): Observable<void> {
    return this.http.put<void>(`/api/customMenu/${customMenuId}/name`, name,
      defaultHttpOptionsFromConfig(config));
  }

  public assignCustomMenu(customMenuId: string, assigneeType: CMAssigneeType,
                          entityIds: string[], force = false, config?: RequestConfig): Observable<CustomMenuAssignResult> {
    return this.http.put<CustomMenuAssignResult>(`/api/customMenu/${customMenuId}/assign/${assigneeType}?force=${force}`, entityIds,
      defaultHttpOptionsFromConfig(config)).pipe(
        mergeMap((res) => this.loadCustomMenu(true).pipe( map(() => res) ))
    );
  }

  public saveCustomMenu(customMenuInfo: CustomMenuInfo, assignToList?: string[],
                        force = false, config?: RequestConfig): Observable<CustomMenu> {
    let url = `/api/customMenu?force=${force}`;
    if (assignToList && Array.isArray(assignToList)) {
        url += `&assignToList=${assignToList.join(',')}`;
    }
    return this.http.post<CustomMenu>(url, customMenuInfo,
      defaultHttpOptionsFromConfig(config)).pipe(
        mergeMap((res) => this.loadCustomMenu(true).pipe( map(() => res) ))
    );
  }

  public deleteCustomMenu(customMenuId: string, force = false, config?: RequestConfig): Observable<CustomMenuDeleteResult> {
    return this.http.delete<CustomMenuDeleteResult>(`/api/customMenu/${customMenuId}?force=${force}`,
      defaultHttpOptionsFromConfig(config)).pipe(
        mergeMap((res) => (res.success ? this.loadCustomMenu(true) : of(null)).pipe( map(() => res) ))
    );
  }

}
