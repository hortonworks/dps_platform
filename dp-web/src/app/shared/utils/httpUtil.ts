/*
 *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *
 *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *
 *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *   properly licensed third party, you do not have any rights to this code.
 *
 *   If this code is provided to you under the terms of the AGPLv3:
 *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *     FROM OR RELATED TO THE CODE; AND
 *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *     OR LOSS OR CORRUPTION OF DATA.
 */

import {Response, RequestOptionsArgs} from '@angular/http';
import {Observable}     from 'rxjs/Observable';
import {Headers} from '@angular/http';
import {Alerts} from './alerts';
import {AuthUtils} from './auth-utils';
import {Router} from '@angular/router';
import {CustomError} from "../../models/custom-error";

export const HEADER_CHALLENGE_HREF = 'X-Authenticate-Href';

export class HttpUtil {

  public static extractString(res: Response): string {
    let text: string = res.text();
    return text || '';
  }


  public static extractData(res: Response): any {
    let body = res.json();
    return body || {};
  }

  public static handleError(error: any) {
    if (error.status === 401) {
      const challengeAt = error.headers.get(HEADER_CHALLENGE_HREF);
      const redirectTo = `${window.location.protocol}//${window.location.host}/${challengeAt}`;
      if(window.location.href.startsWith(`${window.location.protocol}//${window.location.host}/sign-in`) === false) {
        window.location.href = `${redirectTo}?originalUrl=${window.location.href}`;
      }
      return Observable.throw(error);
    }

    if (error.status === 403) {
      AuthUtils.setValidUser(false);
      return Observable.throw(error);
    }

    if (error.status === 404) {
      window.location.href = AuthUtils.notExistsURL;
      return Observable.throw(error);
    }

    let errMsg = (error.message) ? error.message :
      error.status ? `${error.status} - ${error.statusText}` : 'Server error';
      console.error(errMsg); // log to console instead
    let message;
    if (error._body) {
      let errorJSON = JSON.parse(error._body);

      if(Array.isArray(errorJSON.errors) && errorJSON.errors[0] && errorJSON.errors[0].status && errorJSON.errors[0].message && errorJSON.errors[0].errorType) {
        message = errorJSON.errors.filter(err => {return (err.status && err.message && err.errorType)}).map(err => {return HttpUtil.processErrorMessage(err)}).join(', ');
      } else if(errorJSON.error && errorJSON.error.message){
        message = errorJSON.error.message;
      }else if(Array.isArray(errorJSON)){
        message = errorJSON.map(err => {return HttpUtil.truncateErrorMessage(err.message)}).join(', ')
      }else if(errorJSON.message){
        message = HttpUtil.truncateErrorMessage(errorJSON.message)
      }else if (errorJSON.errors){
        message = errorJSON.errors.map(err => {return HttpUtil.truncateErrorMessage(err.message)}).join(', ')
      }else {
        message = 'Error Occured while processing';
      }
      Alerts.showErrorMessage(message);
    }
    return Observable.throw(error);
  }

  private static isValidErrArray(errObject) {
    return errObject.errors[0] && errObject.errors[0].status && errObject.errors[0].errorType
  }
  private static getMsgsFromErrArray(errorObj){
    return errorObj.errors.filter(err => {return (err.status && err.errorType)}).map(err => {return HttpUtil.processErrorMessage(err)}).join(', ');
  }
  private static processErrorMessage(error:CustomError){
    return HttpUtil.truncateErrorMessage(error.message);
  }

  private static truncateErrorMessage(errorMessage: String){
    if(errorMessage.length < 256){
      return errorMessage;
    }
    return errorMessage.substring(0,256);
  }

  public static getHeaders(): RequestOptionsArgs {
    const headers = {
      'Content-Type': 'application/json',
      'Cache-Control': 'no-cache, no-store, max-age=0, must-revalidate',
      'X-Requested-With' : 'XMLHttpRequest'

    };
    return ({
      headers: new Headers(headers)
    });
  }
}
