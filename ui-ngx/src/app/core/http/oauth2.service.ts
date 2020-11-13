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
import { defaultHttpOptionsFromConfig, RequestConfig } from '@core/http/http-utils';
import { Observable } from 'rxjs';
import { OAuth2ClientRegistrationTemplate, OAuth2ClientsParams } from '@shared/models/oauth2.models';

@Injectable({
  providedIn: 'root'
})
export class OAuth2Service {

  constructor(
    private http: HttpClient
  ) { }

  public getOAuth2Settings(config?: RequestConfig): Observable<OAuth2ClientsParams> {
    return this.http.get<OAuth2ClientsParams>(`/api/oauth2/config`, defaultHttpOptionsFromConfig(config));
  }

  public getOAuth2Template(config?: RequestConfig): Observable<Array<OAuth2ClientRegistrationTemplate>> {
    return this.http.get<Array<OAuth2ClientRegistrationTemplate>>(`/api/oauth2/config/template`, defaultHttpOptionsFromConfig(config));
  }

  public saveOAuth2Settings(OAuth2Setting: OAuth2ClientsParams, config?: RequestConfig): Observable<OAuth2ClientsParams> {
    return this.http.post<OAuth2ClientsParams>('/api/oauth2/config', OAuth2Setting,
      defaultHttpOptionsFromConfig(config));
  }

  public getLoginProcessingUrl(config?: RequestConfig): Observable<string> {
    return this.http.get<string>(`/api/oauth2/loginProcessingUrl`, defaultHttpOptionsFromConfig(config));
  }
}
