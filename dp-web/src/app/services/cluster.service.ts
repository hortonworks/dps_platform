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

import {Cluster, ClusterHealthSummary} from '../models/cluster';
import {ClusterDetailRequest} from '../models/cluster-state';

@Injectable()
export class ClusterService {
  uri = 'api/clusters';

  constructor(private httpClient: HttpClient) {
  }

  syncCluster(lakeId): Observable<{ status: boolean }> {
    return this.httpClient.post<{ status: boolean }>(`/api/lakes/${lakeId}/sync`, {});
  }

  listByLakeId(lakeId: any): Observable<Cluster[]> {
    const uri = `${this.uri}?dpClusterId=${lakeId}`;
    return this.httpClient.get<Cluster[]>(uri);
  }

  list(): Observable<Cluster[]> {
    return this.httpClient.get<Cluster[]>(this.uri);
  }

  insert(cluster: Cluster): Observable<Cluster> {
    return this.httpClient.post<Cluster>(`${this.uri}`, cluster);
  }

  retrieveHealth(clusterId: number, dpClusterId: number): Observable<ClusterHealthSummary> {
    const uri = `${this.uri}/${clusterId}/health?dpClusterId=${dpClusterId}&summary=true`;
    return this.httpClient.get<ClusterHealthSummary>(uri);
  }

  retrieveDetailedHealth(clusterId: number, dpClusterId: number): Observable<any> {
    const uri = `${this.uri}/${clusterId}/health?dpClusterId=${dpClusterId}`;
    return this.httpClient.get<any>(uri);
  }

  retrieveResourceMangerHealth(clusterId: number): Observable<any> {
    const uri = `${this.uri}/${clusterId}/rmhealth`;
    return this.httpClient.get<any>(uri);
  }

  retrieveKafkaDetails(clusterId: number): Observable<any> {
    const uri = `${this.uri}/${clusterId}/kafka`;
    return this.httpClient.get<any>(uri);
  }

  retrieveDataNodeHealth(clusterId: number): Observable<any> {
    const uri = `${this.uri}/${clusterId}/dnhealth`;
    return this.httpClient.get<any>(uri);
  }

  getClusterInfo(clusterDetailRequest: ClusterDetailRequest): Observable<Cluster> {
    return this.httpClient.post<Cluster>(`api/ambari/details`, clusterDetailRequest);
  }

  getHostName(clusterId: number, ambariIp: string): Observable<any> {
    const uri = `${this.uri}/${clusterId}/hosts?ip=${ambariIp}`;
    return this.httpClient.get<any>(uri);
  }

}
