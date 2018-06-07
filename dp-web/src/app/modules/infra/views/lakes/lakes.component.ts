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

import {Component, OnInit} from '@angular/core';
import {Router} from '@angular/router';
import {Observable} from 'rxjs/Rx';

import {LakeService} from '../../../../services/lake.service';
import {LocationService} from '../../../../services/location.service';
import {ClusterService} from '../../../../services/cluster.service';

import {Lake} from '../../../../models/lake';
import {Cluster} from '../../../../models/cluster';
import {MapData} from '../../../../models/map-data';
import {MapConnectionStatus} from '../../../../models/map-data';
import {Point} from '../../../../models/map-data';
import {MapSize} from '../../../../models/map-data';

@Component({
  selector: 'dp-infra-lakes',
  templateUrl: './lakes.component.html',
  styleUrls: ['./lakes.component.scss']
})
export class LakesComponent implements OnInit {

  lakes: {
    data: Lake,
    clusters: Cluster[]
  }[];

  mapData: MapData[] = [];
  mapSet = new Map();
  health = new Map();
  mapSize: MapSize;
  private SYNCED = 'SYNCED';
  private SYNC_ERROR = 'SYNC_ERROR';
  private MAXCALLS = 10;
  private DELAY_IN_MS = 2000;
  showError = false;


  constructor(private router: Router,
              private lakeService: LakeService,
              private locationService: LocationService,
              private clusterService: ClusterService) {
  }

  ngOnInit() {
    this.mapSize = MapSize.EXTRALARGE;
    this.getClusters();
  }

  getClusters() {
    this.showError = false;
    let unSyncedLakes = [];
    this.lakeService.listWithClusters()
      .subscribe(lakes => {
        this.lakes = lakes;

        this.lakes.forEach((lake) => {
          let locationObserver;
          let isWaiting: boolean;
          lake.data.isWaiting = true;
          if (lake.data.state === this.SYNCED || lake.data.state === this.SYNC_ERROR) {
            isWaiting = false;
            if (lake.data.state === this.SYNCED || (lake.data.state === this.SYNC_ERROR && lake.clusters && lake.clusters.length > 0)) {
              locationObserver = this.getLocationInfoWithStatus(lake.data.location, lake.clusters[0].id, lake.data.id, lake.data.ambariUrl);
            } else {
              locationObserver = this.getLocationInfo(lake.data.location);
            }
          } else {
            isWaiting = true;
            unSyncedLakes.push(lake.data.id);
            locationObserver = this.getLocationInfo(lake.data.location);
          }
          this.updateHealth(lake, locationObserver, isWaiting);
        });
        this.updateUnSyncedLakes(unSyncedLakes);
      });
  }

  updateUnSyncedLakes(unSyncedLakes) {
    unSyncedLakes.forEach(cUnsyncedId => {
      let count = 1;
      this.lakeService
        .retrieve(cUnsyncedId)
        .delay(this.DELAY_IN_MS)
        .repeat(this.MAXCALLS)
        .skipWhile((lake) => lake.state !== this.SYNCED && lake.state !== this.SYNC_ERROR && count++ < this.MAXCALLS)
        .first()
        .subscribe(lake => {
          const cLake = this.lakes.find(cLake => cLake.data.id === lake.id);
          cLake.data = lake;

          let locationObserver;
          if (lake.state === this.SYNCED || lake.state === this.SYNC_ERROR) {
            this.clusterService.listByLakeId(lake.id)
              .subscribe(clusters => {
                cLake.clusters = clusters;

                if (clusters && clusters.length > 0) {
                  locationObserver = this.getLocationInfoWithStatus(
                    cLake.data.location,
                    clusters[0].id,
                    cLake.data.id,
                    cLake.data.ambariUrl
                  );
                } else {
                  locationObserver = this.getLocationInfo(cLake.data.location);
                }
                this.updateHealth(cLake, locationObserver, false);
              });
          } else {
            locationObserver = this.getLocationInfo(cLake.data.location);
            this.updateHealth(cLake, locationObserver, false);
          }
        });
    });
  }

  updateHealth(lake, locationObserver: Observable<any>, isWaiting: boolean) {
    locationObserver
      .subscribe(locationInfo => {
        lake.data.isWaiting = isWaiting;
        if (lake.data.state === this.SYNCED || lake.data.state === this.SYNC_ERROR) {
          lake.data.ambariUrl = locationInfo.ambariUrl;
        }
        this.health.set(lake.data.id, locationInfo);
        this.health = new Map(this.health.entries());
        this.mapSet.set(lake.data.id, new MapData(this.extractMapPoints(locationInfo, lake)));
        let mapPoints = [];
        this.mapSet.forEach(mapData => {
          mapPoints.push(mapData)
        });
        this.mapData = mapPoints;
      }, error => {
        lake.data.isWaiting = isWaiting;
        this.health = new Map(this.health.entries());
      });
  }

  private getLocationInfoWithStatus(locationId, clusterId, lakeId, ambariUrl): Observable<any> {
    return Observable.forkJoin(
      this.locationService
        .retrieve(locationId)
        .map((res) => res)
        .catch(err => {
          return Observable.of(null);
        }),
      this.clusterService
        .retrieveHealth(clusterId, lakeId)
        .map((res) => res)
        .catch(err => {
          return Observable.of(null);
        }),
      this.getAmbariUrl(clusterId, ambariUrl),
      (location, health, ambariUrl) => ({location, health, ambariUrl}));
  }

  private getAmbariUrl(clusterId, ambariUrl): Observable<string> {
    let parsedAmbariUrl = new URL(ambariUrl);
    let validIpAddressRegex = new RegExp('^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$');
    if (!validIpAddressRegex.test(parsedAmbariUrl.hostname)) {
      return Observable.of(ambariUrl);
    }
    let parsedIpAddress = new URL(ambariUrl);
    return this.clusterService.getHostName(clusterId, parsedIpAddress.hostname)
      .map(response => {
        if (response && response.length) {
          let host = response[0].host;
          return `${parsedIpAddress.protocol}//${host}:${parsedIpAddress.port}`;
        } else {
          return ambariUrl;
        }
      })
      .catch(() => Observable.of(ambariUrl));
  }

  private getLocationInfo(locationId) {
    return this.locationService.retrieve(locationId)
      .map(location => ({
        location: location
      }));
  }

  private extractMapPoints(locationInfo, lake) {
    if (!locationInfo.health && locationInfo.location) {
      return new Point(locationInfo.location.latitude, locationInfo.location.longitude, MapConnectionStatus.NA, lake.data.name, lake.data.dcName, `${locationInfo.location.city}, ${locationInfo.location.country}`)
    } else if (locationInfo.health && locationInfo.location) {
      let health = locationInfo.health;
      let location = locationInfo.location;
      let status;
      if (health && health.status && health.status.state === 'STARTED') {
        status = MapConnectionStatus.UP;
      } else if (health && health.status && (health.status.state === 'NOT STARTED' || health.status.state === this.SYNC_ERROR)) {
        status = MapConnectionStatus.DOWN;
      } else {
        status = MapConnectionStatus.NA;
      }
      return new Point(location.latitude, location.longitude, status, lake.data.name, lake.data.dcName, `${locationInfo.location.city}, ${locationInfo.location.country}`);
    }
  }

  onRefresh(lakeId) {
    let lakeInfo = this.lakes.find(lake => lake.data.id === lakeId);
    if (lakeInfo.data.state === this.SYNCED) {
      this.updateHealth(lakeInfo, this.getLocationInfoWithStatus(lakeInfo.data.location, lakeInfo.clusters[0].id, lakeId, lakeInfo.data.ambariUrl), false);
    } else {
      this.updateUnSyncedLakes([lakeInfo.data.id]);
    }
  }

}
