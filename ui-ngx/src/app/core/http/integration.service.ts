///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2020 ThingsBoard, Inc. All Rights Reserved.
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
import { PageLink } from '@shared/models/page/page-link';
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { map } from 'rxjs/operators';
import { sortEntitiesByIds } from '@shared/models/base-data';
import { Integration, IntegrationType } from '@shared/models/integration.models';

@Injectable({
  providedIn: 'root'
})
export class IntegrationService {

  constructor(
    private http: HttpClient
  ) { }

  public getIntegrations(pageLink: PageLink, config?: RequestConfig): Observable<PageData<Integration>> {
    return this.http.get<PageData<Integration>>(`/api/integrations${pageLink.toQuery()}`,
      defaultHttpOptionsFromConfig(config));
  }

  public getIntegrationsByIds(integrationIds: Array<string>, config?: RequestConfig): Observable<Array<Integration>> {
    return this.http.get<Array<Integration>>(`/api/integrations?integrationIds=${integrationIds.join(',')}`,
      defaultHttpOptionsFromConfig(config)).pipe(
      map((integrations) => sortEntitiesByIds(integrations, integrationIds))
    );
  }

  public getIntegration(integrationId: string, config?: RequestConfig): Observable<Integration> {
    return this.http.get<Integration>(`/api/integration/${integrationId}`, defaultHttpOptionsFromConfig(config));
  }

  public saveIntegration(integration: Integration, config?: RequestConfig): Observable<Integration> {
    return this.http.post<Integration>('/api/integration', integration, defaultHttpOptionsFromConfig(config));
  }

  public deleteIntegration(integrationId: string, config?: RequestConfig) {
    return this.http.delete(`/api/integration/${integrationId}`, defaultHttpOptionsFromConfig(config));
  }

  public getIntegrationHttpEndpointLink(configuration: any, integrationType: IntegrationType, routingKey: string): string {
    let url: string = configuration.baseUrl;
    const type = integrationType ? integrationType.toLowerCase() : '';
    const key = routingKey ? routingKey : '';
    url += `/api/v1/integrations/${type}/${key}`;
    return url;
  }
}
