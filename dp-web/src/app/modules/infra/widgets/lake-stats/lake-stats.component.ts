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

import { Component, Input, OnInit } from '@angular/core';
import * as moment from 'moment';

import { LocationService } from '../../../../services/location.service';
import { ClusterService } from '../../../../services/cluster.service';

import { Lake } from '../../../../models/lake';
import { Location } from '../../../../models/location';
import { Cluster, ClusterHealthSummary } from '../../../../models/cluster';

@Component({
  selector: 'dp-lake-stats',
  templateUrl: './lake-stats.component.html',
  styleUrls: ['./lake-stats.component.scss'],
})
export class LakeStatsComponent implements OnInit {

  @Input('lake')
  lake: Lake;
  @Input('clusters')
  clusters: Cluster[];

  location: Location;

  cCluster: Cluster;
  cHealth: ClusterHealthSummary;

  constructor(
    private clusterService: ClusterService,
    private locationService: LocationService,
  ) { }

  ngOnInit() {
    this.cCluster = this.clusters[0];
    if(!this.cCluster) {
      this.cCluster = new Cluster();
      this.cCluster.ambariurl = this.lake.ambariUrl;
    }

    if(this.cCluster && this.cCluster.id) {
      this.clusterService
        .retrieveHealth(this.cCluster.id, this.lake.id)
        .subscribe(health => this.cHealth = health);
    }

    this.locationService
      .retrieve(this.lake.location)
      .subscribe(location => this.location = location);
  }

  doGetUptime(since: number) {
    if(since === 0){
      return 'NA';
    }
    return moment.duration(since).humanize();
  }

}
