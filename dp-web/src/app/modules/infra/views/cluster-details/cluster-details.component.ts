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

import {Component, OnInit, ElementRef, ViewChild, AfterViewInit} from '@angular/core';
import {Router, ActivatedRoute} from '@angular/router';
import {Observable} from 'rxjs/Observable';

import {Cluster, ClusterDetails, ServiceInfo} from '../../../../models/cluster';
import {Lake} from '../../../../models/lake';
import {Location} from '../../../../models/location';

import {LakeService} from '../../../../services/lake.service';
import {ClusterService} from '../../../../services/cluster.service';
import {LocationService} from '../../../../services/location.service';
import {StringUtils} from '../../../../shared/utils/stringUtils';
import {UserService} from '../../../../services/user.service';
import {DateUtils} from '../../../../shared/utils/date-utils';
import {Loader} from '../../../../shared/utils/loader';
import {CustomError} from "../../../../models/custom-error";
import {TranslateService} from "@ngx-translate/core";

@Component({
  selector: 'dp-cluster-details',
  templateUrl: './cluster-details.component.html',
  styleUrls: ['./cluster-details.component.scss']
})
export class ClusterDetailsComponent implements OnInit, AfterViewInit {

  constructor(private router: Router,
              private route: ActivatedRoute,
              private lakeService: LakeService,
              private clusterService: ClusterService,
              private locationService: LocationService,
              private userService: UserService,
              private translateService: TranslateService) {
  }

  clusterStatus:any;
  lake: Lake = new Lake();
  clusters: Cluster[];
  servicesInfo: ServiceInfo[];
  cluster: any;
  clusterHealth: any;
  rmHealth: any;
  dnHealth: any;
  clusterDetails: ClusterDetails = new ClusterDetails();
  location: Location = new Location();
  user: any;
  @ViewChild('hdfsProgress') hdfsProgress: ElementRef;
  @ViewChild('rmHeapProgress') rmHeapProgress: ElementRef;
  @ViewChild('heapProgress') heapProgress: ElementRef;
  hdfsProgressObservable: Observable<boolean> = Observable.create();
  heapProgressObservable: Observable<boolean> = Observable.create();
  rmHeapProgressObservable: Observable<boolean> = Observable.create();
  rmHeapPercent: string;
  hdfsPercent: string;
  heapPercent: string;
  clusterHealthInProgress = false;
  showError: boolean = false;
  errorMessage: string;
  serviceLoadError: boolean = false;

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.fetchClusterDetails(params['id']);
    });
  }

  fetchClusterDetails(lakeId) {
    Loader.show();
    this.showError = false;
    this.serviceLoadError = false;
    this.lakeService.retrieve(lakeId).subscribe((lake: Lake) => {
      this.lake = lake;
      this.populateGeneralProperties();
      this.getClusterDetails();
    }, error => {
      Loader.hide();
    });
  }

  private populateGeneralProperties() {
    let tags = '';
    this.lake.properties.tags.forEach(tag => {
      tags = `${tags}${tags.length ? ', ' : ''}${tag.name}`;
    });
    this.clusterDetails = new ClusterDetails();
    this.clusterDetails.tags = tags;
    this.clusterDetails.dataCenter = this.lake.dcName;
    this.userService.getUserById(this.lake.createdBy).subscribe(user => {
      this.user = user;
    });
  }

  private getClusterDetails() {
    Loader.show();
    Observable.forkJoin(
      this.clusterService.listByLakeId(this.lake.id),
      this.locationService.retrieve(this.lake.location),
    ).subscribe(responses => {
      Loader.show();
      this.location = responses[1];
      this.clusterDetails.location = `${this.location.city}, ${this.location.country}`;
      this.clusters = responses[0];
      if (this.clusters && this.clusters.length) {
        this.cluster = this.clusters[0];
        this.populateClusterProperties();
      }
      this.clusterHealthInProgress = true;
      this.clusterService.syncCluster(this.lake.id).subscribe(res => {
        Loader.show();
        let count = 0;
        this.getServicesInfo(this.lake.id);
        this.lakeService.retrieve(this.lake.id.toString())
          .delay(2000)
          .repeat(15)
          .skipWhile(lake => lake.state !== 'SYNCED' && lake.state !== 'SYNC_ERROR' && ++count < 10)
          .first().subscribe(lakeUpdated => {
            this.getAmbariUrl(this.cluster.id, this.lake.ambariUrl).subscribe(ambariUrl =>{
              this.lake = lakeUpdated;
              this.clusterStatus = lakeUpdated.state;
              this.lake.ambariUrl = ambariUrl;
              this.getClusterHealth(this.cluster.id, this.lake.id);
            });
        });

        this.getDataNodeHealth(this.cluster.id);
        this.getRMHealth(this.cluster.id);
      });
    });
  }

  private onError(error){
    if(!this.showError && error._body){
      let errsWrap = JSON.parse(error._body);
      if(errsWrap && errsWrap.errors && errsWrap.errors.length > 0){
        let err = errsWrap.errors[0] as CustomError;
        this.showError = true;
        this.errorMessage = this.translateService.instant('pages.infra.description.backenderrors.General');
      }
    }
  }

  closeError() {
    this.showError = false;
  }

  private getServicesInfo(lakeId){
    this.serviceLoadError = false;
    this.lakeService.getServicesInfo(lakeId.toString()).subscribe(res =>{
      this.servicesInfo = res;
    }, error => {
      this.serviceLoadError = true;
      this.onError(error);
    });
  }

  private getDataNodeHealth(clusterId) {
    this.clusterService.retrieveDataNodeHealth(this.cluster.id).subscribe(dnHealth => {
      this.dnHealth = dnHealth;
      this.populateDataNodeHealth();
    }, error => {
      this.onError(error);
    });
  }

  private getClusterHealth(clusterId, lakeId) {
    Loader.show();
    this.clusterService
      .retrieveDetailedHealth(clusterId, lakeId)
      .subscribe(
        health => {
          this.clusterHealthInProgress = false;
          this.clusterHealth = health;
          this.populateClusterDetails();
          this.processHealthProgressbarInfo();
          Loader.hide();
        }, error => {
          Loader.hide();
        }
      );
  }

  private getRMHealth(clusterId) {
    Loader.show();
    this.clusterService.retrieveResourceMangerHealth(clusterId).subscribe(rmHealth => {
      this.rmHealth = rmHealth;
      this.populateRMHealthProperties();
      this.processRMProgressbarsInfo();
      if (!this.clusterHealthInProgress) {
        Loader.hide();
      }
    }, error => {
      this.onError(error);
      if (!this.clusterHealthInProgress) {
        Loader.hide();
      }
    });
  }

  ngAfterViewInit() {
    let self = this;
    this.hdfsProgress.nativeElement.addEventListener('mdl-componentupgraded', function () {
      this.MaterialProgress.setProgress(0);
      self.hdfsProgressObservable = Observable.create(observer => {
        observer.next(true);
      });
    });
    this.heapProgress.nativeElement.addEventListener('mdl-componentupgraded', function () {
      this.MaterialProgress.setProgress(0);
      self.heapProgressObservable = Observable.create(observer => {
        observer.next(true);
      });
    });
    this.rmHeapProgress.nativeElement.addEventListener('mdl-componentupgraded', function () {
      this.MaterialProgress.setProgress(0);
      self.rmHeapProgressObservable = Observable.create(observer => {
        observer.next(true);
      });
    });
  }

  private processHealthProgressbarInfo() {
    this.hdfsProgressObservable.subscribe(() => {
      let percent = this.getPercent(this.clusterHealth.nameNodeInfo.CapacityUsed, this.clusterHealth.nameNodeInfo.CapacityTotal);
      this.hdfsPercent = `${percent}%`;
      this.updateProgess(this.hdfsProgress, percent);
    });
    this.heapProgressObservable.subscribe(() => {
      let percent = this.getPercent(this.clusterHealth.nameNodeInfo.HeapMemoryUsed, this.clusterHealth.nameNodeInfo.HeapMemoryMax);
      this.heapPercent = `${percent}%`;
      this.updateProgess(this.heapProgress, percent);
    });
  }

  private processRMProgressbarsInfo() {
    this.rmHeapProgressObservable.subscribe(() => {
      let percent = this.getPercent(this.rmHealth.metrics.jvm.HeapMemoryUsed, this.rmHealth.metrics.jvm.HeapMemoryMax);
      this.rmHeapPercent = `${percent}%`;
      this.updateProgess(this.rmHeapProgress, percent);
    });
  }

  updateProgess(element, progress) {
    element.nativeElement.MaterialProgress.setProgress(progress);
  }

  getPercent(used, total) {
    return ((parseInt(used) / parseInt(total)) * 100).toFixed(2);
  }


  private populateClusterProperties() {
    this.clusterDetails.nodes = this.cluster.properties.total_hosts;
    this.clusterDetails.healthyNodes = this.cluster.properties.health_report['Host/host_state/HEALTHY'];
    this.clusterDetails.unhealthyNodes = this.cluster.properties.health_report['Host/host_state/UNHEALTHY'];
    this.clusterDetails.securityType = this.cluster.properties['security_type'];
    this.clusterDetails.hdpVersion = this.cluster.properties.version;
    this.clusterDetails.noOfSerices = Object.keys(this.cluster.properties['desired_service_config_versions']).length;

  }

  private populateClusterDetails() {
    if (this.lake.state === 'SYNC_ERROR') {
      this.clusterDetails.status = this.lake.state;
    } else {
      this.clusterDetails.status = this.clusterHealth.nameNodeInfo.state;
    }
    this.clusterDetails.hdfsUsed = StringUtils.humanizeBytes(this.clusterHealth.nameNodeInfo.CapacityUsed);
    this.clusterDetails.hdfsTotal = StringUtils.humanizeBytes(this.clusterHealth.nameNodeInfo.CapacityTotal);
    this.clusterDetails.heapSizeTotal = StringUtils.humanizeBytes(this.clusterHealth.nameNodeInfo.HeapMemoryMax);
    this.clusterDetails.heapSizeUsed = StringUtils.humanizeBytes(this.clusterHealth.nameNodeInfo.HeapMemoryUsed);
    this.clusterDetails.uptime = DateUtils.toReadableDate(new Date().getTime() - this.clusterHealth.nameNodeInfo.StartTime);
  }

  private populateRMHealthProperties() {
    if (this.rmHealth && this.rmHealth.ServiceComponentInfo && this.rmHealth.ServiceComponentInfo.rm_metrics && this.rmHealth.metrics && this.rmHealth.metrics.jvm) {
      this.clusterDetails.nodeManagersActive = this.rmHealth.ServiceComponentInfo.rm_metrics.cluster.activeNMcount;
      this.clusterDetails.nodeManagersInactive = this.rmHealth.ServiceComponentInfo.rm_metrics.cluster.lostNMcount;
      this.clusterDetails.rmHeapTotal = StringUtils.humanizeBytes(this.rmHealth.metrics.jvm.HeapMemoryMax);
      this.clusterDetails.rmHeapUsed = StringUtils.humanizeBytes(this.rmHealth.metrics.jvm.HeapMemoryUsed);
      this.clusterDetails.rmUptime = DateUtils.toReadableDate(new Date().getTime() - this.rmHealth.metrics.runtime.StartTime);
    }
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
      }).catch(() => Observable.of(ambariUrl));
  }

  private populateDataNodeHealth() {
    if (
      this.dnHealth &&
      this.dnHealth.ServiceComponentInfo &&
      this.dnHealth.ServiceComponentInfo.started_count !== undefined &&
      this.dnHealth.ServiceComponentInfo.total_count !== undefined
    ) {
      this.clusterDetails.healthyDataNodes = this.dnHealth.ServiceComponentInfo.started_count;
      this.clusterDetails.unhealthyDataNodes = this.dnHealth.ServiceComponentInfo.total_count - this.dnHealth.ServiceComponentInfo.started_count;
    }

  }

}
