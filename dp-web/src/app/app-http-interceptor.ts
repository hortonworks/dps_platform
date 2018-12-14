import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Rx';
import { CustomError } from './models/custom-error';
import { Alerts } from './shared/utils/alerts';
import { AuthUtils } from './shared/utils/auth-utils';
import { HEADER_CHALLENGE_HREF } from './shared/utils/httpUtil';

@Injectable()
export class AppHttpInterceptor implements HttpInterceptor {

  private readonly headers = {
    setHeaders: {
      'Cache-Control': 'no-cache, no-store, max-age=0, must-revalidate',
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest'
    }
  };

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const reqClone = req.clone(this.headers);
    return next.handle(reqClone)
          .catch(err => this.handleError(err));
  }

  private handleError(httpErrResponse: HttpErrorResponse) {
    let message = '';

    if (httpErrResponse.status === 401) {
      return this.redirectToLogin(httpErrResponse);
    }

    if (httpErrResponse.status === 403) {
      return this.setUserAsInvalid(httpErrResponse);
    }

    if (httpErrResponse.error) {
      const errorJSON = httpErrResponse.error;

      if (Array.isArray(errorJSON.errors) && errorJSON.errors[0] && errorJSON.errors[0].status &&
                        errorJSON.errors[0].message && errorJSON.errors[0].errorType) {
        message = errorJSON.errors.filter(err => (err.status && err.message && err.errorType))
                                  .map(err => this.processErrorMessage(err))
                                  .join(', ');
      } else if (errorJSON.error && errorJSON.error.message) {
        message = errorJSON.error.message;
      } else if (Array.isArray(errorJSON)) {
        message = errorJSON.map(err => this.truncateErrorMessage(err.message))
                            .join(', ');
      } else if (errorJSON.message) {
        message = this.truncateErrorMessage(errorJSON.message);
      } else if (errorJSON.errors) {
        message = errorJSON.errors.map(err => this.truncateErrorMessage(err.message))
                                   .join(', ');
      } else {
        message = 'Error Occurred while processing';
      }
      Alerts.showErrorMessage(message);
    }

    return Observable.throwError(httpErrResponse);
  }

  private processErrorMessage(error: CustomError) {
    return this.truncateErrorMessage(error.message);
  }

  private truncateErrorMessage(errorMessage: string) {
    if (errorMessage.length < 256) {
      return errorMessage;
    }
    return errorMessage.substring(0, 256);
  }

  private setUserAsInvalid(httpErrResponse: HttpErrorResponse) {
    AuthUtils.setValidUser(false);
    return Observable.throwError(httpErrResponse);
  }

  private redirectToLogin(httpErrResponse: HttpErrorResponse) {
    const challengeAt = httpErrResponse.headers.get(HEADER_CHALLENGE_HREF);
    if (challengeAt && challengeAt.length > 0) {
      const redirectTo = `${window.location.protocol}//${window.location.host}/${challengeAt}`;
      if (window.location.href.startsWith(`${window.location.protocol}//${window.location.host}/sign-in`) === false) {
        window.location.href = `${redirectTo}?originalUrl=${window.location.href}`;
      }
    }
    return Observable.throwError(httpErrResponse);
  }
}
