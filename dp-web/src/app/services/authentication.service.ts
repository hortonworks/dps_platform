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

import {Injectable} from '@angular/core';
import {Http, RequestOptions} from '@angular/http';
import {Router} from '@angular/router';
import {Observable} from 'rxjs/Rx';

import {HttpUtil, HEADER_CHALLENGE_HREF} from '../shared/utils/httpUtil';

import {Credential} from '../models/credential';
import {User} from '../models/user';
import {AuthUtils} from '../shared/utils/auth-utils';

@Injectable()
export class AuthenticationService {

  private URI: string = 'auth';

  constructor(private http: Http, private router: Router) {
  }

  isAuthenticated(): Observable<boolean> {
    return Observable.create(observer => {
      if (!AuthUtils.isUserLoggedIn()) {
        observer.next(false);
      }else{
        observer.next(true);
      }
    });
  }

  getAuthServerStatus(url){
    return this.http
      .get(url)
      .catch(HttpUtil.handleError);
  }

  signIn(credential: Credential): Observable<User> {
    return this.http
      .post(`${this.URI}/in`, credential, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .do((user: User) => {
        AuthUtils.setUser(user);
        return user;
      });
  }

  signOut(): Observable<string> {
    return this.http
      .get(`${this.URI}/out`)
      .map(response => {
        const header = response.headers.get(HEADER_CHALLENGE_HREF);
        return typeof header === 'string' ? header : null;
      });
  }

  signOutAndRedirect() {
    this.signOut()
      .subscribe(challengeAt => {
        AuthUtils.clearUser();
        const redirectTo = `${window.location.protocol}//${window.location.host}/${challengeAt}`;
        window.location.href = `${redirectTo}?originalUrl=${window.location.protocol}//${window.location.host}/`;
      });
  }
}
