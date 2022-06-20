///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2022 ThingsBoard, Inc. All Rights Reserved.
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
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { Observable, of, throwError } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { DeviceProfile, DeviceProfileInfo, DeviceTransportType } from '@shared/models/device.models';
import { deepClone, isDefinedAndNotNull, isEmptyStr } from '@core/utils';
import {
  ObjectLwM2M,
  ServerSecurityConfig,
  ServerSecurityConfigInfo
} from '@home/components/profile/device/lwm2m/lwm2m-profile-config.models';
import { SortOrder } from '@shared/models/page/sort-order';
import { OtaPackageService } from '@core/http/ota-package.service';
import { map, mergeMap, tap } from 'rxjs/operators';
import { Lwm2mSecurityType } from '@shared/models/lwm2m-security-config.models';
import { Integration } from '@shared/models/integration.models';
import { sortEntitiesByIds } from '@shared/models/base-data';

@Injectable({
  providedIn: 'root'
})
export class DeviceProfileService {

  private lwm2mBootstrapSecurityInfoInMemoryCache = new Map<boolean, ServerSecurityConfigInfo>();

  constructor(
    private http: HttpClient,
    private otaPackageService: OtaPackageService
  ) {
  }

  public getDeviceProfiles(pageLink: PageLink, config?: RequestConfig): Observable<PageData<DeviceProfile>> {
    return this.http.get<PageData<DeviceProfile>>(`/api/deviceProfiles${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public getDeviceProfile(deviceProfileId: string, config?: RequestConfig): Observable<DeviceProfile> {
    return this.http.get<DeviceProfile>(`/api/deviceProfile/${deviceProfileId}`, defaultHttpOptionsFromConfig(config));
  }

  public getLwm2mObjects(sortOrder: SortOrder, objectIds?: string[], searchText?: string, config?: RequestConfig):
    Observable<Array<ObjectLwM2M>> {
    let url = `/api/resource/lwm2m/?sortProperty=${sortOrder.property}&sortOrder=${sortOrder.direction}`;
    if (isDefinedAndNotNull(objectIds) && objectIds.length > 0) {
      url += `&objectIds=${objectIds}`;
    }
    if (isDefinedAndNotNull(searchText) && !isEmptyStr(searchText)) {
      url += `&searchText=${searchText}`;
    }
    return this.http.get<Array<ObjectLwM2M>>(url, defaultHttpOptionsFromConfig(config));
  }

  public getLwm2mBootstrapSecurityInfo(isBootstrapServer: boolean, config?: RequestConfig): Observable<ServerSecurityConfigInfo> {
    const securityConfig = this.lwm2mBootstrapSecurityInfoInMemoryCache.get(isBootstrapServer);
    if (securityConfig) {
      return of(securityConfig);
    } else {
      return this.http.get<ServerSecurityConfigInfo>(
        `/api/lwm2m/deviceProfile/bootstrap/${isBootstrapServer}`,
        defaultHttpOptionsFromConfig(config)
      ).pipe(
        tap(serverConfig => this.lwm2mBootstrapSecurityInfoInMemoryCache.set(isBootstrapServer, serverConfig))
      );
    }
  }

  public getLwm2mBootstrapSecurityInfoBySecurityType(isBootstrapServer: boolean, securityMode = Lwm2mSecurityType.NO_SEC,
                                                     config?: RequestConfig): Observable<ServerSecurityConfig> {
    return this.getLwm2mBootstrapSecurityInfo(isBootstrapServer, config).pipe(
      map(securityConfig => {
        const serverSecurityConfigInfo = deepClone(securityConfig);
        if (serverSecurityConfigInfo) {
          switch (securityMode) {
            case Lwm2mSecurityType.PSK:
              serverSecurityConfigInfo.port = serverSecurityConfigInfo.securityPort;
              serverSecurityConfigInfo.host = serverSecurityConfigInfo.securityHost;
              serverSecurityConfigInfo.serverPublicKey = '';
              break;
            case Lwm2mSecurityType.RPK:
            case Lwm2mSecurityType.X509:
              serverSecurityConfigInfo.port = serverSecurityConfigInfo.securityPort;
              serverSecurityConfigInfo.host = serverSecurityConfigInfo.securityHost;
              break;
            case Lwm2mSecurityType.NO_SEC:
              serverSecurityConfigInfo.serverPublicKey = '';
              break;
          }
        }
        return serverSecurityConfigInfo;
      })
    );
  }

  public getLwm2mObjectsPage(pageLink: PageLink, config?: RequestConfig): Observable<Array<ObjectLwM2M>> {
    return this.http.get<Array<ObjectLwM2M>>(
      `/api/resource/lwm2m/page${pageLink.toQuery()}`,
      defaultHttpOptionsFromConfig(config)
    );
  }

  public saveDeviceProfileAndConfirmOtaChange(originDeviceProfile: DeviceProfile, deviceProfile: DeviceProfile,
                                              config?: RequestConfig): Observable<DeviceProfile> {
    return this.otaPackageService.confirmDialogUpdatePackage(deviceProfile, originDeviceProfile).pipe(
      mergeMap((update) => update ? this.saveDeviceProfile(deviceProfile, config) : throwError('Canceled saving device profiles'))
    );
  }

  public saveDeviceProfile(deviceProfile: DeviceProfile, config?: RequestConfig): Observable<DeviceProfile> {
    return this.http.post<DeviceProfile>('/api/deviceProfile', deviceProfile, defaultHttpOptionsFromConfig(config));
  }

  public deleteDeviceProfile(deviceProfileId: string, config?: RequestConfig) {
    return this.http.delete(`/api/deviceProfile/${deviceProfileId}`, defaultHttpOptionsFromConfig(config));
  }

  public setDefaultDeviceProfile(deviceProfileId: string, config?: RequestConfig): Observable<DeviceProfile> {
    return this.http.post<DeviceProfile>(`/api/deviceProfile/${deviceProfileId}/default`, defaultHttpOptionsFromConfig(config));
  }

  public getDefaultDeviceProfileInfo(config?: RequestConfig): Observable<DeviceProfileInfo> {
    return this.http.get<DeviceProfileInfo>('/api/deviceProfileInfo/default', defaultHttpOptionsFromConfig(config));
  }

  public getDeviceProfileInfo(deviceProfileId: string, config?: RequestConfig): Observable<DeviceProfileInfo> {
    return this.http.get<DeviceProfileInfo>(`/api/deviceProfileInfo/${deviceProfileId}`, defaultHttpOptionsFromConfig(config));
  }

  public getDeviceProfileInfos(pageLink: PageLink, transportType?: DeviceTransportType,
                               config?: RequestConfig): Observable<PageData<DeviceProfileInfo>> {
    let url = `/api/deviceProfileInfos${pageLink.toQuery()}`;
    if (isDefinedAndNotNull(transportType)) {
      url += `&transportType=${transportType}`;
    }
    return this.http.get<PageData<DeviceProfileInfo>>(url, defaultHttpOptionsFromConfig(config));
  }

  public getDeviceProfilesByIds(deviceProfileIds: Array<string>, config?: RequestConfig): Observable<Array<DeviceProfileInfo>> {
    return this.http.get<Array<DeviceProfileInfo>>(`/api/deviceProfileInfos?deviceProfileIds=${deviceProfileIds.join(',')}`,
      defaultHttpOptionsFromConfig(config)).pipe(
      map((integrations) => sortEntitiesByIds(integrations, deviceProfileIds))
    );
  }

  public getDeviceProfileDevicesAttributesKeys(deviceProfileId?: string, config?: RequestConfig): Observable<Array<string>> {
    let url = `/api/deviceProfile/devices/keys/attributes`;
    if (isDefinedAndNotNull(deviceProfileId)) {
      url += `?deviceProfileId=${deviceProfileId}`;
    }
    return this.http.get<Array<string>>(url, defaultHttpOptionsFromConfig(config));
  }

  public getDeviceProfileDevicesTimeseriesKeys(deviceProfileId?: string, config?: RequestConfig): Observable<Array<string>> {
    let url = `/api/deviceProfile/devices/keys/timeseries`;
    if (isDefinedAndNotNull(deviceProfileId)) {
      url += `?deviceProfileId=${deviceProfileId}`;
    }
    return this.http.get<Array<string>>(url, defaultHttpOptionsFromConfig(config));
  }

}
