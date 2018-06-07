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
import {ActivatedRoute, Router} from '@angular/router';
import {AddOnAppService} from '../../../../services/add-on-app.service';
import {AddOnAppInfo, EnabledAppInfo} from '../../../../models/add-on-app';
import {LakeService} from '../../../../services/lake.service';
import {Observable} from 'rxjs/Observable';
import {ClusterService} from '../../../../services/cluster.service';
import {DateUtils} from '../../../../shared/utils/date-utils';
import {DialogBox, DialogType} from "../../../../shared/utils/dialog-box";
import {TranslateService} from "@ngx-translate/core";

@Component({
  selector: 'dp-service-management',
  templateUrl: './service-management.component.html',
  styleUrls: ['./service-management.component.scss']
})
export class ServiceManagementComponent implements OnInit {

  clusters: any[] = [];

  allServices: AddOnAppInfo[] = [];
  availableServices: any[] = [];
  enabledAppDetails: EnabledAppDetails[] = [];

  showNotification = false;

  serviceNameParams: any;

  constructor(private router: Router,
              private route: ActivatedRoute,
              private translateService: TranslateService,
              private addOnAppService: AddOnAppService,
              private lakeService: LakeService,
              private clusterService: ClusterService) {
  }

  ngOnInit() {
    this.addOnAppService.getAllServices().subscribe((services) => {
      this.allServices = services;
      this.allServices.forEach(service => {
        if(service.enabled === true){
          return;
        }
        let availableService = Object.assign({}, service, {fetchingStatus:true, installed: false, healthy: false});
        this.availableServices.push(availableService);
        this.addOnAppService.getServiceStatus(service.skuName).subscribe(response => {
          availableService.fetchingStatus = false;
          availableService.installed = response.installed;
          availableService.healthy = response.healthy;
        }, error => {
          availableService.fetchingStatus = false;
          console.error(error);
        });
      });
    });
    this.addOnAppService.getEnabledServices().subscribe((services) => {
      services.forEach(service => {
        this.enabledAppDetails.push(new EnabledAppDetails(service, [], false));
      });
    });
    this.addOnAppService.serviceEnabled$.subscribe((name) => {
      this.serviceNameParams = {
        serviceName: name
      };
      this.showNotification = true;
    });
  }

  getClusters(enbaledApp: EnabledAppDetails) {
    enbaledApp.isOpen = !enbaledApp.isOpen;
    if (enbaledApp.isOpen) {
      this.lisClusters(enbaledApp.service.skuName);
    } else {
      enbaledApp.clustersInfo = [];
    }
  }

  lisClusters(serviceName) {
    let clusters = [];
    let count = 0;
    let enabledApp = this.enabledAppDetails.find((enabledApp: EnabledAppDetails) => enabledApp.service.skuName === serviceName);
    Observable.forkJoin(
      this.addOnAppService.getServiceDependencies(serviceName),
      this.lakeService.listWithClusters()).subscribe(responses => {
      let serviceDependency = responses[0];
      let lakes = responses[1];
      lakes.forEach((lake) => {
        this.extractClusterInfo(serviceName, lake, serviceDependency).subscribe(clusterInfo => {
          clusters.push(clusterInfo);
          enabledApp.clustersInfo = clusters;
          enabledApp.isOpen = true;
          clusterInfo.syncInProgress = true;
          this.clusterService.syncCluster(lake.data.id).subscribe(response => {
            if (response._body === 'false') {
              clusterInfo.synced = false;
              clusterInfo.syncInProgress = false;
              return;
            }
            this.lakeService.retrieve(lake.data.id.toString())
              .delay(2000)
              .repeat(15)
              .skipWhile((lake) => lake.state !== 'SYNCED' && lake.state !== 'SYNC_ERROR' && ++count < 10)
              .first().subscribe(lakeUpdated => {
              if (lakeUpdated.state === 'SYNC_ERROR') {
                clusterInfo.synced = false;
                clusterInfo.syncInProgress = false;
                return;
              }
              this.extractClusterInfo(serviceName, lake, serviceDependency).subscribe(info => {
                clusterInfo.lastUpdated = DateUtils.toReadableDate(new Date().getTime() - new Date(lakeUpdated.updated).getTime());
                clusterInfo.syncInProgress = false;
                clusterInfo.synced = true;
                clusterInfo.mandatoryDependencies = info.mandatoryDependencies;
                clusterInfo.optionalDependencies = info.optionalDependencies;
                clusterInfo.dependenciesMet = info.dependenciesMet;
                clusterInfo.optionalDependenciesMet = info.optionalDependenciesMet;
              });
            });
          });
        });
      });
    });
  }

  extractClusterInfo(serviceName, lake, serviceDependency): Observable<any> {
    return Observable.create(observer => {
      let services = Object.keys(lake.clusters[0].properties.desired_service_config_versions);
      this.lakeService.getDiscoveredServices(lake.data.id).subscribe(discoveredServices => {
        let dependenciesMet = true;
        let optionalDependenciesMet = true;
        serviceDependency.mandatoryDependencies.forEach(dependency => {
          if (!services.find(key => key === dependency) && !discoveredServices.find(service => service.servicename === dependency)) {
            dependenciesMet = false;
          }
        });
        if(serviceDependency.optionalDependencies){
          serviceDependency.optionalDependencies.forEach(dependency => {
            if (!services.find(key => key === dependency) && !discoveredServices.find(service => service.servicename === dependency)) {
              optionalDependenciesMet = false;
            }
          });
        }
        return observer.next({
          dpClusterId: lake.data.id,
          name: lake.data.name,
          lastUpdated: DateUtils.toReadableDate(new Date().getTime() - new Date(lake.data.updated).getTime()),
          dependenciesMet: dependenciesMet,
          mandatoryDependencies: serviceDependency.mandatoryDependencies,
          optionalDependencies: serviceDependency.optionalDependencies,
          optionalDependenciesMet: optionalDependenciesMet
        });
      }, error => {

      });
    });

  }

  enableServiceOnCluster(dpClusterId, service) {
    this.router.navigate(['/infra/services/add'], {
      queryParams: {
        id: dpClusterId,
        name: service.skuName
      }
    });
  }

  onSort(e) {

  }

  showMessage(service){
    DialogBox.showErrorMessage(this.translateService.instant('common.services'),
      this.translateService.instant('pages.services.description.serviceNotInstalled', {serviceName: service.sku.description, installationStepsLink:this.translateService.instant(`pages.services.description.${service.skuName}InstallationLink`)}),
      this.translateService.instant('common.close'),
      DialogType.Error);
  }

  showWarning(service){
    DialogBox.showConfirmationMessage(this.translateService.instant('common.services'),
      this.translateService.instant('pages.services.description.serviceNotHealthy', {serviceName: service.sku.description}),
      this.translateService.instant('common.ok'),
      this.translateService.instant('common.cancel'),
      DialogType.Confirmation
    ).subscribe(result => {
      if(result){
        this.router.navigate(['/infra/services', service.skuName, 'verify'])
      }
    });
  }

}

export class EnabledAppDetails {
  constructor(service: EnabledAppInfo,
              clustersInfo: any[],
              isOpen: boolean) {

    this.service = service;
    this.clustersInfo = clustersInfo ? clustersInfo : [];
    this.isOpen = isOpen;

  }

  dpClusterId: number;
  service: EnabledAppInfo;
  clustersInfo: any[] = [];
  isOpen: boolean;
  showAll: boolean = false;
}
