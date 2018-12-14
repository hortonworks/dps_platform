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
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import {Subject} from 'rxjs/Subject';

import {ServiceStatus} from '../models/service-status';
import {AddOnAppInfo, AppDependency, ConfigPayload, EnabledAppInfo, SKU} from '../models/add-on-app';
import { PluginManifest } from '../models/plugin-manifest';


@Injectable()
export class AddOnAppService {

  uri = 'api/services';
  healthApiUrl = 'api/health';
  serviceEnabled = new Subject<string>();
  serviceEnabled$ = this.serviceEnabled.asObservable();

  constructor(private httpClient: HttpClient) {
  }

  getServiceStatus(appName): Observable<ServiceStatus>{
    return this.httpClient.get<ServiceStatus>(`${this.healthApiUrl}/${appName}`);
  }

  getEnabledServices(): Observable<EnabledAppInfo[]> {
    return this.httpClient.get<EnabledAppInfo[]>(`${this.uri}/enabled`);
  }

  enableService(configPayload: ConfigPayload) {
    return this.httpClient.post(`${this.uri}/enable`, configPayload);
  }

  verify(smartSenseid: string): Observable<{isValid: boolean}> {
    return this.httpClient.post<{isValid: boolean}>(`${this.uri}/verifyCode?smartSenseId=${smartSenseid}`, {});
  }

  getClustersForService(name: string): Observable<any> {
    return this.httpClient.get<any>(`${this.uri}/${name}/clusters?all=true`);
  }

  enableServiceOnCluster(skuId: number, lakeId: string) {
    return this.httpClient.put<any>(`${this.uri}/${skuId}/clusters/${lakeId}/association`, {});
  }

  listPlugins(allowed?: string, installed?: string, healthy?: string): Observable<PluginManifest[]> {
    if (allowed === undefined && installed === undefined && healthy === undefined){
      return this.httpClient.get<PluginManifest[]>(`api/plugins`); 
    } else {
      return this.httpClient.get<PluginManifest[]>(`api/plugins?allowed=${allowed}&installed=${installed}&healthy=${healthy}`);
    }
  }
}
