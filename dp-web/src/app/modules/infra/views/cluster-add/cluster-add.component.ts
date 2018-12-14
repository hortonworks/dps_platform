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

import {Component, ElementRef, ViewChild, OnInit, HostListener} from '@angular/core';
import {Router, ActivatedRoute} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {Observable} from 'rxjs/Observable';

import {Cluster} from '../../../../models/cluster';
import {Lake} from '../../../../models/lake';
import {Location} from '../../../../models/location';
import {Point} from '../../../../models/map-data';
import {MapData} from '../../../../models/map-data';
import {MapConnectionStatus} from '../../../../models/map-data';
import {ClusterState, ClusterDetailRequest} from '../../../../models/cluster-state';

import {LakeService} from '../../../../services/lake.service';
import {ClusterService} from '../../../../services/cluster.service';
import {LocationService} from '../../../../services/location.service';


import {StringUtils} from '../../../../shared/utils/stringUtils';
import {NgForm} from '@angular/forms';
import {ConfigDialogComponent} from '../../widgets/config-dialog/config-dialog.component';
import {CustomError} from "../../../../models/custom-error";
import {DialogBox, DialogType} from "../../../../shared/utils/dialog-box";

@Component({
  selector: 'dp-cluster-add',
  templateUrl: './cluster-add.component.html',
  styleUrls: ['./cluster-add.component.scss']
})
export class ClusterAddComponent implements OnInit {

  @ViewChild('ambariInput') ambariInputContainer: ElementRef;
  @ViewChild('clusterForm') clusterForm: NgForm;
  @ViewChild('config') private config: ConfigDialogComponent;

  @HostListener('keydown', ['$event', '$event.target'])
  public onKeyDown($event: KeyboardEvent, targetElement: HTMLElement): void {
    const code = $event.which || $event.keyCode;
    if (code === 27 && this.showConfig) {
      this.closeConfig();
    }
  }


  _isClusterValidateInProgress = false;
  _isClusterValidateSuccessful = false;
  _clusterState: ClusterState = new ClusterState();
  _isClusterValid;
  showConfig = false;

  mapData: MapData[] = [];
  cluster: Cluster = new Cluster();
  searchTerm: string;
  dcName: string;
  location: Location;
  isLocationValid: boolean;

  onlyAllowTrusted: boolean = false;
  behindGateway: boolean = false;

  dpRequiredServices = ['ATLAS'];

  showNotification = false;
  showError = false;
  errorMessage = '';
  isInvalidAmbariUrl = false;
  isServiceHidden=true;

  showAmbariBehindKnoxWarning = false;
  showAmbariNotBehindKnoxWarning = false;

  errorCode='';

  constructor(private router: Router,
              private route: ActivatedRoute,
              private lakeService: LakeService,
              private clusterService: ClusterService,
              private locationService: LocationService,
              private  translateService: TranslateService) {
  };

  get dLFoundMessage() {
    let services = `${' ' + this.dpRequiredServices.join(', ')}`;
    return this.translateService.instant('pages.infra.description.datalake', {serviceNames: services});
  }

  get reasons() {
    let reasons: string[] = [];
    if (this._isClusterValidateSuccessful && !this._isClusterValid && !this._clusterState.alreadyExists) {
      let reasonsTranslation = this.translateService.instant('pages.infra.description.connectionFailureReasons');
      Object.keys(reasonsTranslation).forEach(key => {
        reasons.push(reasonsTranslation[key]);
      });
    } else if (this._clusterState.alreadyExists) {
      let reasonsTranslation = this.translateService.instant('pages.infra.description.clusterAlreadyExists');
      reasons.push(reasonsTranslation);
    } else if (this.isInvalidAmbariUrl) {
      let reasonsTranslation = this.translateService.instant('pages.infra.description.invalidAmbariUrl');
      reasons.push(reasonsTranslation);
    } else if(this.errorCode.indexOf('connection-refused') > -1){
      let connectionRefusedReasons = this.translateService.instant('error.cluster.connection-refused.reasons');
      Object.keys(connectionRefusedReasons).forEach(value =>{
        reasons.push(connectionRefusedReasons[value]);
      });
    }

    return reasons;
  }

  ngOnInit() {
    this.lakeService.clusterAdded$.subscribe(() => {
      this.showNotification = true;
    });
  }

  closeNotification() {
    this.showNotification = false;
  }

  closeError() {
    this.showError = false;
  }

  resetPage(){
    this.showError = false;
    this.isInvalidAmbariUrl = false;
    this.showNotification = false;
    this._isClusterValidateInProgress = false;
    this._isClusterValidateSuccessful = false;
    this.clusterForm.reset();
    this.cluster = new Cluster();

    window.setTimeout(() => {
      this.onlyAllowTrusted = false;
      this.behindGateway = false;
    }, 0);
  }

  getClusterInfo(event) {
    this.showError = false;
    this.isServiceHidden=true;
    this.isInvalidAmbariUrl = false;
    this.showNotification = false;
    this._isClusterValidateInProgress = true;
    this._isClusterValidateSuccessful = false;
    this._clusterState = new ClusterState();
    let cleanedUri = StringUtils.cleanupUri(this.cluster.ambariurl);
    if (!cleanedUri) {
      this.isInvalidAmbariUrl = true;
      this._isClusterValidateInProgress = false;
      this.applyErrorClass();
      return;
    }
    this.lakeService.validate(cleanedUri, !this.onlyAllowTrusted, this.behindGateway)
      .subscribe(
        response => {
          this._clusterState = response as ClusterState;
          if (response.ambariApiStatus === 200) {
            //TODO - Padma/Babu/Hemanth/Rohit :Display that Knox was detected
            this.isConfigValid(cleanedUri, response.knoxUrl).subscribe(response => {
              if(response){
                if(this.showAmbariBehindKnoxWarning){
                  this.behindGateway = true
                }
                if(this.showAmbariNotBehindKnoxWarning){
                  this.behindGateway = false;
                }
                let detailRequest = new ClusterDetailRequest();
                this.createDetailRequest(detailRequest, cleanedUri);
                this.requestClusterInfo(detailRequest, cleanedUri);
                this.removeValidationError();
              }else{
                this._isClusterValidateInProgress = false;
              }
            });
          } else {
            this._isClusterValidateInProgress = false;
            this._isClusterValidateSuccessful = true;
            this._isClusterValid = false;
            this.applyErrorClass();
          }
        },
        error => {
          let err = error.error.errors[0] as CustomError;
          this.onError(err);
        }
      );
  }

  isKnoxEnabled(ambariUrl, knoxUrl){
    return knoxUrl && knoxUrl.length && ambariUrl.startsWith(knoxUrl) && ambariUrl.indexOf('dp-proxy') === knoxUrl.length+1;
  }

  isConfigValid(ambariUrl, knoxUrl){
    if(!this.behindGateway && this.isKnoxEnabled(ambariUrl, knoxUrl)){
      this.showAmbariBehindKnoxWarning = true;
      return this.showDialog(this.translateService.instant('pages.infra.description.ambariBehindKnoxWarning'));
    }else if(this.behindGateway && !this.isKnoxEnabled(ambariUrl, knoxUrl)){
      this.showAmbariNotBehindKnoxWarning = true;
      return this.showDialog(this.translateService.instant('pages.infra.description.ambariNotBehindKnoxWarning'));
    }else{
      this.showAmbariBehindKnoxWarning = false;
      this.showAmbariNotBehindKnoxWarning = false;
      return Observable.create(observer => observer.next(true));
    }
  }

  showDialog(message: string){
    return DialogBox.showConfirmationMessage(
      this.translateService.instant('common.warning'),
      message,
      this.translateService.instant('common.proceed'),
      this.translateService.instant('common.cancel'),
      DialogType.Confirmation
    );
  }

  applyErrorClass() {
    if (this.ambariInputContainer.nativeElement.className.indexOf('validation-error') === -1) {
      this.ambariInputContainer.nativeElement.className += ' validation-error';
    }
  }

  getConfigs(detailRequest: any) {
    detailRequest.url = this.cluster.ambariurl;
    detailRequest.knoxUrl = this._clusterState.knoxUrl;
    detailRequest.knoxDetected = this._clusterState.knoxDetected;
    detailRequest.allowUntrusted = !this.onlyAllowTrusted;
    this.requestClusterInfo(detailRequest, this.cluster.ambariurl);
    this.removeValidationError();
    this.closeConfig();
  }

  closeConfig() {
    this._isClusterValidateInProgress = false;
    this.showConfig = false;
  }

  private removeValidationError() {
    this.ambariInputContainer.nativeElement.className = this.ambariInputContainer.nativeElement.className.replace('validation-error', '');
  }

  private requestClusterInfo(detailRequest: ClusterDetailRequest, cleanedUri: string) {
    this._isClusterValidateInProgress = true;
    this.clusterService.getClusterInfo(detailRequest).subscribe(clusterInfo => {
      this._isClusterValidateInProgress = false;
      this._isClusterValidateSuccessful = true;
      this._isClusterValid = true;
      this.extractClusterInfo(clusterInfo);
      this.cluster.ambariurl = cleanedUri;
      if (this._clusterState.knoxDetected) {
        // Update cluster state with the final knox URL - determined by
        this._clusterState.knoxUrl = clusterInfo[0].knoxUrl
      }
    }, (error) => {
      this.onError(null);
    });
  }

  private createDetailRequest(detailRequest: ClusterDetailRequest, cleanedUri: string) {
    detailRequest.url = cleanedUri;
    detailRequest.knoxDetected = this._clusterState.knoxDetected;
    detailRequest.knoxUrl = this._clusterState.knoxUrl;
    detailRequest.allowUntrusted = !this.onlyAllowTrusted
  }

  private onError(err: CustomError) {
    this._isClusterValidateSuccessful = false;
    this._isClusterValidateInProgress = false;
    this.showError = true;
    if(!err) {
      this.errorCode = 'pages.infra.description.connectionFailed';
      this.errorMessage = this.translateService.instant('pages.infra.description.connectionFailed');
    } else {
      let message = this.translateService.instant(`error.${err.code}`);
      if(message === `error.${err.code}` || (err.code === 'generic' && err.message)) {
        // no localized message configured
        message = err.message;
      }

      this.errorMessage = message;
      this.errorCode = err.code;
    }
  }

  private extractClusterInfo(clusterInfo) {
    this.cluster.name = clusterInfo[0].clusterName;
    this.cluster.services = clusterInfo[0].services;
    this.cluster.clusterType = clusterInfo[0].clusterType;
    this.cluster.ipAddress = this._clusterState.ambariIpAddress;
  }

  get showClusterDetails() {
    return this._isClusterValidateSuccessful && this._isClusterValid;
  }

  get isDataLake() {
    for (let name of this.dpRequiredServices) {
      if (this.cluster.services.indexOf(name) === -1) {
        return false;
      }
    }
    return true;
  }

  getIPAddress(amabariUrl){
    let urlParts = amabariUrl.split('/');
    return urlParts.length ? urlParts[2].substr(0, urlParts[2].lastIndexOf(':')) : '';
  }

  locationFormatter(location: Location): string {
    return `${location.city}${location.province ? ', ' + location.province : ''}, ${location.country}`;
  }

  getLocations(searchTerm) {
    return this.locationService.retrieveOptions(searchTerm);
  }

  setLocation() {
    if (this.searchTerm.length === 0) {
      this.mapData = [];
      this.cluster.location = null;
    }
  }

  setLocationValidity(location : Location){
    this.isLocationValid = true;
    if(!location || !location.id){
      this.isLocationValid = false;
    }
  }
  resetLocationValidity(){
    this.isLocationValid = true;
  }

  onSelectLocation(location: Location) {
    this.mapData = [];
    if(location && location.id) {
      let point = new Point(location.latitude, location.longitude, MapConnectionStatus.UP);
      this.mapData = [new MapData(point)];
    }
    this.cluster.location = location;
  }

  onNewTagAddition(text: string) {
    this.cluster.tags.push(text);
  }

  onCreate() {
    this.showError = false;
    if (!this.isFormValid() || !this.isLocationValid ) {
      return;
    }
    this.createCluster()
      .subscribe(
        (res) => (this.router.navigate([`/infra/clusters/${res.id}`])),
        error => this.handleError(error)
      );
  }

  isFormValid() {
    if (!this.clusterForm.form.valid) {
      this.errorMessage = this.translateService.instant('common.defaultRequiredFields');
      this.showError = true;
      window.scrollTo(0, 0);
      return false;
    }
    return true;
  }

  handleError(error) {
    this.showError = true;
    if (JSON.stringify(error.error).indexOf('unique_name_and_dc_name_constraint') >= 0) {
      this.errorMessage = this.translateService.instant('pages.infra.description.duplicateDataCenter');
    } else {
      this.errorMessage = this.translateService.instant('pages.infra.description.clusterAddError');
    }
  }

  onKeyPress(event) {
    if (event.keyCode === 13) {
      this.getClusterInfo(event);
    }
  }

  createCluster() {
    let lake = new Lake();
    lake.dcName = this.dcName;
    lake.ambariUrl = this.cluster.ambariurl;
    lake.location = this.cluster.location.id;
    lake.isDatalake = this.isDataLake;
    lake.name = this.cluster.name;
    lake.description = this.cluster.description || '';
    lake.dcName = this.cluster.dcName;
    lake.state = 'TO_SYNC';
    lake.ambariIpAddress = this.cluster.ipAddress;
    lake.allowUntrusted = !this.onlyAllowTrusted;
    lake.behindGateway = this.behindGateway;
    lake.clusterType = this.cluster.clusterType;

    if (this._clusterState.knoxDetected) {
      lake.knoxEnabled = true;
      lake.knoxUrl = this._clusterState.knoxUrl;
    }
    let properties = {tags: []};
    this.cluster.tags.forEach(tag => properties.tags.push({'name': tag}));
    lake.properties = properties;
    return this.lakeService.insert(lake);
  }

  onCreateAndAdd() {
    this.showError = false;
    if (!this.isLocationValid || !this.isFormValid()) {
      return;
    }
    this.createCluster().subscribe(
      () => {
        this.cluster = new Cluster();
        this._isClusterValid = false;
        this._isClusterValidateSuccessful = false;
        this.clusterForm.reset();
        this.router.navigate(['/infra/clusters/add']).then(() => {
          this.lakeService.clusterAdded.next();
        });
        window.setTimeout(() => {
          this.onlyAllowTrusted = false;
          this.behindGateway = false;
        }, 0);
      },
      error => {
        this.handleError(error);
      }
    );
  }
}
