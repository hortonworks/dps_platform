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
import {Observable} from 'rxjs/Observable';
import {AssetDetails} from '../models/asset-property';
import {AssetTag} from '../models/asset-tag';

import {HttpUtil} from '../shared/utils/httpUtil';
import {AssetSchema, AssetModel} from '../models/asset-schema';

@Injectable()
export class AssetService {
  uri = 'api/assets';

  constructor(private http: Http) {
  }

  checkMockAuditVisualStatus(){
    return this.http
      .get(`${this.uri}/auditMockStatus`, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(HttpUtil.handleError);
  }

  getDetailsFromDb (guid: string) : Observable<AssetModel> {
    const uri = `${this.uri}/byguid/${guid}`;
    return this.http
      .get(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(HttpUtil.handleError);
  }

  getDetails(clusterId:string, assetId: string) : Observable<AssetDetails>{
    const uri = `${this.uri}/details/${clusterId}/${assetId}`;
    return this.http
      .get(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(HttpUtil.handleError);
  }

  startProfiling(clusterId:string, dbName:string, tableName:string) : Observable<any>{
    const uri = `api/dpProfiler/startJob?clusterId=${clusterId}&dbName=${dbName}&tableName=${tableName}`;
    return this.http
      .post(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(err => {
        if(err.status == 404 || err.status == 405) return Observable.throw(err);
        return HttpUtil.handleError(err)
      });

  }

  getProfilingStatus(clusterId:string, dbName:string, tableName:string) : Observable<any>{
    const uri = `api/dpProfiler/jobStatus?clusterId=${clusterId}&dbName=${dbName}&tableName=${tableName}`;
    return this.http
      .get(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(err => {
        if(err.status == 404 || err.status == 405) return Observable.throw(err);
        return HttpUtil.handleError(err)
      });
  }

  getScheduleInfo(clusterId:number, datasetId:number) : Observable<any> {
    const uri = `api/dpProfiler/scheduleStatus?clusterId=${clusterId}&dataSetId=${datasetId}`
        return this.http
      .get(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(err => {
        if(err.status == 404 || err.status == 405) return Observable.throw(err);
        return HttpUtil.handleError(err)
      });
  }

  getProfilerAuditResults(clusterId:string, dbName:string, tableName:string, userName:string, dateModel:any) : Observable<any>{
    const endDate = `${dateModel.endDate.year}-${dateModel.endDate.month}-${dateModel.endDate.day}`
    const startDate = `${dateModel.beginDate.year}-${dateModel.beginDate.month}-${dateModel.beginDate.day}`
    const uri = `api/dpProfiler/auditResults?clusterId=${clusterId}&dbName=${dbName}&tableName=${tableName}&startDate=${startDate}&endDate=${endDate}${userName?("&userName="+userName):""}`;
    return this.http
      .get(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(err => {
        if(err.status == 404 || err.status == 405 || err.status == 503 || err.status == 500) return Observable.throw(err);
        return HttpUtil.handleError(err)
      });
  }

  getProfilerAuditActions(clusterId:string, dbName:string, tableName:string, userName:string, dateModel:any) : Observable<any>{
    const endDate = `${dateModel.endDate.year}-${dateModel.endDate.month}-${dateModel.endDate.day}`
    const startDate = `${dateModel.beginDate.year}-${dateModel.beginDate.month}-${dateModel.beginDate.day}`
    const uri = `api/dpProfiler/auditActions?clusterId=${clusterId}&dbName=${dbName}&tableName=${tableName}&startDate=${startDate}&endDate=${endDate}${userName?("&userName="+userName):""}`;
    return this.http
      .get(uri, new RequestOptions(HttpUtil.getHeaders()))
      .map(HttpUtil.extractData)
      .catch(err => {
        if(err.status == 404 || err.status == 405 || err.status == 503 || err.status == 500) return Observable.throw(err);
        return HttpUtil.handleError(err)
      });
  }

}
