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
import {Observable} from 'rxjs/Observable';

import {UserAndInitializationStatus} from '../models/user';
import {LDAPProperties, LDAPUpdateProperties} from '../models/ldap-properties';
import {HttpClient} from '@angular/common/http';

@Injectable()
export class ConfigurationService {
  uri = 'api/init';
  knowConfigUri = 'api/knox';
  gaConfigUri = 'api/config/dps.ga.tracking.enabled';

  constructor(private httpClient: HttpClient) {
  }

  retrieve(): Observable<UserAndInitializationStatus> {
    return this.httpClient.get<UserAndInitializationStatus>(this.uri);
  }

  configureLDAP(ldapProperties: LDAPProperties): Observable<{ configured: boolean }> {
    return this.httpClient
      .post<{ configured: boolean }>(`${this.knowConfigUri}/configure`, ldapProperties)
      .map(result => {
        if(result.configured) {
          return result;
        } else {
          throw new Error('Failed to configure LDAP. Please check logs.')
        }
      });
  }

  updateLDAP(ldapUpdateProperties: LDAPUpdateProperties): Observable<{ configured: boolean }> {
    return this.httpClient
      .post<{ configured: boolean }>(`${this.knowConfigUri}/ldap`, ldapUpdateProperties)
      .map(result => {
        if(result.configured) {
          return result;
        } else {
          throw new Error('Failed to configure LDAP. Please check logs.')
        }
      });;
  }

  isKnoxConfigured(): Observable<{ configured: boolean }> {
    return this.httpClient.get<{ configured: boolean }>(`${this.knowConfigUri}/status`);
  }

  getLdapConfiguration(): Observable<LDAPProperties> {
    return this.httpClient.get<LDAPProperties>(`${this.knowConfigUri}/ldap`);
  }

  getGATrackingStatus(): Observable<{ value: boolean }> {
    return this.httpClient.get<{ value: boolean }>(`${this.gaConfigUri}`);
  }
}
