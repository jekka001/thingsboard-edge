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
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HttpResponseBase
} from '@angular/common/http';
import { Observable } from 'rxjs/internal/Observable';
import { Inject, Injectable } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { Constants } from '@shared/models/constants';
import { InterceptorHttpParams } from './interceptor-http-params';
import { catchError, delay, mergeMap, switchMap, tap } from 'rxjs/operators';
import { throwError, of } from 'rxjs';
import { InterceptorConfig } from './interceptor-config';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ActionLoadFinish, ActionLoadStart } from './load.actions';
import { ActionNotificationShow } from '@app/core/notification/notification.actions';
import { DialogService } from '@core/services/dialog.service';
import { TranslateService } from '@ngx-translate/core';

let tmpHeaders = {};

@Injectable()
export class GlobalHttpInterceptor implements HttpInterceptor {

  private AUTH_SCHEME = 'Bearer ';
  private AUTH_HEADER_NAME = 'X-Authorization';

  private internalUrlPrefixes = [
    '/api/auth/token',
    '/api/plugins/rpc'
  ];

  private activeRequests = 0;

  constructor(@Inject(Store) private store: Store<AppState>,
              @Inject(DialogService) private dialogService: DialogService,
              @Inject(TranslateService) private translate: TranslateService,
              @Inject(AuthService) private authService: AuthService) {
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (req.url.startsWith('/api/')) {
      const config = this.getInterceptorConfig(req);
      const isLoading = !this.isInternalUrlPrefix(req.url);
      this.updateLoadingState(config, isLoading);
      if (this.isTokenBasedAuthEntryPoint(req.url)) {
        if (!AuthService.getJwtToken() && !this.authService.refreshTokenPending()) {
          return this.handleResponseError(req, next, new HttpErrorResponse({error: {message: 'Unauthorized!'}, status: 401}));
        } else if (!AuthService.isJwtTokenValid()) {
          return this.handleResponseError(req, next, new HttpErrorResponse({error: {refreshTokenPending: true}}));
        } else {
          return this.jwtIntercept(req, next);
        }
      } else {
        return this.handleRequest(req, next);
      }
    } else {
      return next.handle(req);
    }
  }

  private jwtIntercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const newReq = this.updateAuthorizationHeader(req);
    if (newReq) {
      return this.handleRequest(newReq, next);
    } else {
      return this.handleRequestError(req, new Error('Could not get JWT token from store.'));
    }
  }

  private handleRequest(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      tap((event: HttpEvent<any>) => {
        if (event instanceof HttpResponseBase) {
          this.handleResponse(req, event as HttpResponseBase);
        }
      }),
      catchError((err) => {
        const errorResponse = err as HttpErrorResponse;
        return this.handleResponseError(req, next, errorResponse);
      }));
  }

  private handleRequestError(req: HttpRequest<any>, err): Observable<HttpEvent<any>> {
    const config = this.getInterceptorConfig(req);
    if (req.url.startsWith('/api/')) {
      this.updateLoadingState(config, false);
    }
    return throwError(err);
  }

  private handleResponse(req: HttpRequest<any>, response: HttpResponseBase) {
    const config = this.getInterceptorConfig(req);
    if (req.url.startsWith('/api/')) {
      this.updateLoadingState(config, false);
    }
  }

  private handleResponseError(req: HttpRequest<any>, next: HttpHandler, errorResponse: HttpErrorResponse): Observable<HttpEvent<any>> {
    const config = this.getInterceptorConfig(req);
    if (req.url.startsWith('/api/')) {
      this.updateLoadingState(config, false);
    }
    let unhandled = false;
    const ignoreErrors = config.ignoreErrors;
    const resendRequest = config.resendRequest;
    const errorCode = errorResponse.error ? errorResponse.error.errorCode : null;
    if (errorResponse.error && errorResponse.error.refreshTokenPending || errorResponse.status === 401) {
      if (errorResponse.error && errorResponse.error.refreshTokenPending ||
          errorCode && errorCode === Constants.serverErrorCode.jwtTokenExpired) {
          return this.refreshTokenAndRetry(req, next);
      } else if (errorCode !== Constants.serverErrorCode.credentialsExpired) {
        unhandled = true;
      }
    } else if (errorResponse.status === 429) {
      if (resendRequest) {
        return this.retryRequest(req, next);
      }
    } else if (errorResponse.status === 403) {
      if (!ignoreErrors) {
        this.dialogService.permissionDenied();
      }
    } else if (errorResponse.status === 0 || errorResponse.status === -1) {
        this.showError('Unable to connect');
    } else if (!req.url.startsWith('/api/plugins/rpc')) {
      if (errorResponse.status === 404) {
        if (!ignoreErrors) {
          this.showError(req.method + ': ' + req.url + '<br/>' +
            errorResponse.status + ': ' + errorResponse.statusText);
        }
      } else {
        unhandled = true;
      }
    }

    if (unhandled && !ignoreErrors) {
      let error = null;
      if (req.responseType === 'text') {
        try {
          error = errorResponse.error ? JSON.parse(errorResponse.error) : null;
        } catch (e) {}
      } else {
        error = errorResponse.error;
      }
      if (error && !error.message) {
        this.showError(this.prepareMessageFromData(error));
      } else if (error && error.message) {
        this.showError(error.message, error.timeout ? error.timeout : 0);
      } else {
        this.showError('Unhandled error code ' + (error ? error.status : '\'Unknown\''));
      }
    }
    return throwError(errorResponse);
  }

  private prepareMessageFromData(data) {
    if (typeof data === 'object' && data.constructor === ArrayBuffer) {
      const msg = String.fromCharCode.apply(null, new Uint8Array(data));
      try {
        const msgObj = JSON.parse(msg);
        if (msgObj.message) {
          return msgObj.message;
        } else {
          return msg;
        }
      } catch (e) {
        return msg;
      }
    } else {
      return data;
    }
  }

  private retryRequest(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const thisTimeout =  1000 + Math.random() * 3000;
    return of(null).pipe(
      delay(thisTimeout),
      mergeMap(() => {
        return this.jwtIntercept(req, next);
      }
    ));
  }

  private refreshTokenAndRetry(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return this.authService.refreshJwtToken().pipe(switchMap(() => {
      return this.jwtIntercept(req, next);
    }),
    catchError((err: Error) => {
      this.authService.logout(true);
      const message = err ? err.message : 'Unauthorized!';
      return this.handleResponseError(req, next, new HttpErrorResponse({error: {message, timeout: 200}, status: 401}));
    }));
  }

  private updateAuthorizationHeader(req: HttpRequest<any>): HttpRequest<any> {
    const jwtToken = AuthService.getJwtToken();
    if (jwtToken) {
      req = req.clone({
        setHeaders: (tmpHeaders = {},
          tmpHeaders[this.AUTH_HEADER_NAME] = '' + this.AUTH_SCHEME + jwtToken,
          tmpHeaders)
      });
      return req;
    } else {
      return null;
    }
  }

  private isInternalUrlPrefix(url): boolean {
    for (const index in this.internalUrlPrefixes) {
      if (url.startsWith(this.internalUrlPrefixes[index])) {
        return true;
      }
    }
    return false;
  }

  private isTokenBasedAuthEntryPoint(url): boolean {
    return  url.startsWith('/api/') &&
      !url.startsWith(Constants.entryPoints.login) &&
      !url.startsWith(Constants.entryPoints.tokenRefresh) &&
      !url.startsWith(Constants.entryPoints.nonTokenBased);
  }

  private updateLoadingState(config: InterceptorConfig, isLoading: boolean) {
    if (!config.ignoreLoading) {
      if (isLoading) {
        this.activeRequests++;
      } else {
        this.activeRequests--;
      }
      if (this.activeRequests === 1 && isLoading) {
        this.store.dispatch(new ActionLoadStart());
      } else if (this.activeRequests === 0) {
        this.store.dispatch(new ActionLoadFinish());
      }
    }
  }

  private getInterceptorConfig(req: HttpRequest<any>): InterceptorConfig {
    if (req.params && req.params instanceof InterceptorHttpParams) {
      return (req.params as InterceptorHttpParams).interceptorConfig;
    } else {
      return new InterceptorConfig(false, false);
    }
  }

  private showError(error: string, timeout: number = 0) {
    setTimeout(() => {
      this.store.dispatch(new ActionNotificationShow({message: error, type: 'error'}));
    }, timeout);
  }
}
