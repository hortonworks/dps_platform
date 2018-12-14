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
import {ClusterService} from '../../../../services/cluster.service';
import {DialogBox, DialogType} from "../../../../shared/utils/dialog-box";
import {TranslateService} from "@ngx-translate/core";
import { PluginManifest } from '../../../../models/plugin-manifest';

@Component({
  selector: 'dp-service-management',
  templateUrl: './service-management.component.html',
  styleUrls: ['./service-management.component.scss']
})
export class ServiceManagementComponent implements OnInit {

  clusters: any[] = [];

  selectedIndex = 0;
  selectedService: AddOnAppInfo;
  plugins: PluginManifest[] = [];
  availableServices: any[] = [];
  filteredClusters: any[] = [];
  showAll = false;
  showNotification = false;
  pluginStatus = new Map();
  fetchingClustersInProgress = false;

  constructor(private translateService: TranslateService,
              private addOnAppService: AddOnAppService) {
  }

  ngOnInit() {
    this.addOnAppService.listPlugins()
      .subscribe(plugins => {
        this.plugins = plugins.filter(cPlugin => cPlugin.enablement_type === 'BY_ADMIN');
        this.plugins
          .forEach(cPlugin => {

            this.availableServices.push(cPlugin);

            this.addOnAppService.getServiceStatus(cPlugin.name).subscribe(response =>{
              this.pluginStatus.set(cPlugin.name, response);
            });

            this.getClusters(this.availableServices[this.selectedIndex], this.selectedIndex);
          });
      });
  }


  filter(){
    this.filteredClusters = this.clusters.filter(cluster => this.showAll || cluster.compatible === true);
  }

  getClusters(service: AddOnAppInfo, index: number) {
    this.clusters = [];
    this.filteredClusters = [];
    this.selectedService = service;
    this.selectedIndex = index;
    this.fetchingClustersInProgress = true;
    this.addOnAppService
      .getClustersForService(service.name)
      .subscribe(clusters => {
        this.clusters = clusters;
        this.filter();
        this.fetchingClustersInProgress = false;
      },error => {
        this.fetchingClustersInProgress = false;
      });
  }

  enableServiceOnCluster(lake){
    DialogBox.showConfirmationMessage(this.translateService.instant('common.services'),
      this.translateService.instant('pages.services.description.enableConfirmation', {
        serviceName: this.plugins[this.selectedIndex].label,
        clusterName: lake.name
      }),
      this.translateService.instant('common.ok'),
      this.translateService.instant('common.cancel'),
      DialogType.Confirmation
    ).subscribe(result => {
      if(result){
        this.addOnAppService.enableServiceOnCluster(this.selectedService.id, lake.id).subscribe(response =>{
          this.getClusters(this.selectedService, this.selectedIndex)
        }, error =>{

        });
      }
    });
  }

  getAgentsOfPlugin(plugin: PluginManifest) {
    return plugin.require_cluster_services
      .filter(cService => cService.is_agent)
      .map(cService => cService.label ? cService.label : cService.name)
      .join(', ');
  }

  getRequiredServices(plugin: PluginManifest) {
    return plugin.require_cluster_services
      .filter(cService => cService.type === 'REQUIRED')
      .map(cService => cService.label ? cService.label : cService.name);
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
