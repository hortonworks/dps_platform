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

import {Component, EventEmitter, HostListener, Input, OnChanges, Output, SimpleChanges} from '@angular/core';
import {Router} from '@angular/router';

import {Sort} from '../../../../shared/utils/enums';
import {Cluster} from '../../../../models/cluster';
import {DateUtils} from '../../../../shared/utils/date-utils';
import {DialogBox, DialogType} from '../../../../shared/utils/dialog-box';
import {LakeService} from '../../../../services/lake.service';
import {TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'dp-lakes-list',
  templateUrl: './lakes-list.component.html',
  styleUrls: ['./lakes-list.component.scss'],
})

export class LakesListComponent implements OnChanges {
  lakesList: LakeInfo[] = [];
  lakesListCopy: LakeInfo[] = [];
  statusEnum = LakeStatus;
  filterOptions: any[] = [];
  filters = [];
  searchText: string;
  showFilterListing = false;
  selectedFilterIndex = -1;
  private availableFilterCount = 0;
  @Input() lakes = [];
  @Input() healths = new Map();
  @Output('onRefresh') refreshEmitter: EventEmitter<number> = new EventEmitter<number>();

  static optionListClass = 'option-value';
  static highlightClass = 'highlighted-filter';

  filterFields = [
    {key: 'name', display: 'Name'},
    {key: 'city', display: 'City'},
    {key: 'country', display: 'Country'},
    {key: 'dataCenter', display: 'Data Center'}];

  constructor(private lakeService: LakeService,
              private translateService: TranslateService) {
  }

  @HostListener('document:click', ['$event', '$event.target'])
  public onClick($event: MouseEvent, targetElement: HTMLElement): void {
    if (targetElement.id === 'search') {
      return;
    }
    this.showFilterListing = false;
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!this.lakes) {
      return;
    }
    if (changes['lakes'] || changes['healths']) {
      let lakesList: LakeInfo[] = [];
      this.lakes.forEach((lake) => {
        let lakeHealthInfo = this.healths.get(lake.data.id);
        if (lakeHealthInfo) {
          lakesList.push(this.extractLakeInfo(lake, lakeHealthInfo.health, lakeHealthInfo.location));
        } else {
          lakesList.push(this.extractLakeInfo(lake, null, null));
        }
      });
      this.lakesList = lakesList;
      this.lakesListCopy = lakesList;
      if (this.filters && this.filters.length) {
        this.filter(true);
      }
    }
  }

  private extractLakeInfo(lake, health, location) {
    let lakeInfo: LakeInfo = new LakeInfo();
    lakeInfo.id = lake.data.id;
    lakeInfo.name = lake.data.name;
    lakeInfo.ambariUrl = lake.data.ambariUrl;
    lakeInfo.ambariIpAddress = lake.data.ambariIpAddress;
    lakeInfo.lakeId = lake.data.id;
    lakeInfo.dataCenter = lake.data.dcName;
    lakeInfo.cluster = lake.clusters && lake.clusters.length ? lake.clusters[0] : null;
    lakeInfo.services = lake.data.services ? lake.data.services : 'NA';
    lakeInfo.isWaiting = lake.data.isWaiting;
    lakeInfo.clusterType = lake.data.clusterType;
    lakeInfo.status = this.getLakeStatus(lake.data.state);
    if (health) {
      this.populateHealthInfo(lakeInfo, health);
    } else {
      lakeInfo.status = lakeInfo.isWaiting ? LakeStatus.WAITING : LakeStatus.NA;
    }
    if (location) {
      lakeInfo.city = location.city;
      lakeInfo.country = location.country;
    }
    return lakeInfo;
  }

  private populateHealthInfo(lakeInfo, health) {
    lakeInfo.hdfsUsed = (health.usedSize && !this.isSyncError(health)) ? health.usedSize : 'NA';
    lakeInfo.hdfsTotal = (health.totalSize && !this.isSyncError(health)) ? health.totalSize : 'NA';
    lakeInfo.nodes = (health.nodes && !this.isSyncError(health)) ? health.nodes : 'NA';
    lakeInfo.startTime = (health.status && !this.isSyncError(health)) ? health.status.startTime : null;
  }

  isSyncError(health) {
    return (health.status && (health.status.state === 'SYNC_ERROR'));
  }

  private getLakeStatus(status){
    if(status === 'SYNCED'){
      return LakeStatus.UP;
    }else if(status === 'SYNC_IN_PROGRESS'){
      return LakeStatus.DOWN;
    }else if(status === 'SYNC_ERROR'){
      return LakeStatus.WAITING;
    }else{
      return LakeStatus.NA;
    }
  }

  private getLocationInfo(lakeInfo){
    if(lakeInfo.city && lakeInfo.country){
      return lakeInfo.city+", "+lakeInfo.country;
    }else{
      return "NA";
    }
  }

  filter(isAddition) {
    if (!this.filters || this.filters.length === 0) {
      this.lakesList = this.lakesListCopy.slice();
      this.showFilterListing = false;
      return;
    }
    if (isAddition) {
      this.filterOnAddition();
    } else {
      this.filterOnRemoval();
    }
    this.selectedFilterIndex = -1;
  }

  private filterOnAddition() {
    this.filters.forEach(filter => {
      this.lakesList = this.lakesList.filter(lakeInfo => {
        return lakeInfo[filter.key] === filter.value;
      });
    });
  }

  private filterOnRemoval() {
    this.filters.forEach(filter => {
      this.lakesList = this.lakesListCopy.filter(lakeInfo => {
        return lakeInfo[filter.key] === filter.value;
      });
    });
  }

  removeFilter(filter) {
    for (let i = 0; i < this.filters.length; i++) {
      let filterItem = this.filters[i];
      if (filterItem.key === filter.key && filterItem.value === filter.value) {
        this.filters.splice(i, 1);
        break;
      }
    }
    this.filter(false);
  }

  addToFilter(display, key, value) {
    if (!this.filters.find(filter => filter.key === key && filter.value === value)) {
      this.filters.push({'key': key, 'value': value, 'display': display});
    }
    this.filter(true);
    this.searchText = '';
    this.showFilterListing = false;
  }

  handleKeyboardEvents(event, display?, key?, value?) {
    let keyPressed = event.keyCode || event.which;
    if (keyPressed === 40 && this.selectedFilterIndex < this.availableFilterCount - 1) {
      ++this.selectedFilterIndex;
      this.highlightSelected();
      return;
    } else if (keyPressed === 38 && this.selectedFilterIndex !== 0) {
      --this.selectedFilterIndex;
      this.highlightSelected();
      return;
    } else if (keyPressed === 13 && this.selectedFilterIndex !== -1) {
      this.addToFilter(display, key, value);
      return;
    }
  }

  private highlightSelected() {
    let filterOptions = document.getElementsByClassName(LakesListComponent.optionListClass);
    let highlighted = document.getElementsByClassName(LakesListComponent.highlightClass);
    for (let i = 0; i < highlighted.length; i++) {
      let elt = highlighted.item(i);
      elt.className = 'option-value';
    }
    let highlightedOption: any = filterOptions[this.selectedFilterIndex];
    highlightedOption.focus();
    highlightedOption.className += ` ${LakesListComponent.highlightClass}`;
  }

  showOptions(event) {
    let keyPressed = event.keyCode || event.which;
    if (keyPressed === 38 || keyPressed === 40) {
      this.handleKeyboardEvents(event);
    } else {
      this.filterOptions = [];
      let filterOptionsMap = new Map();
      let term = event.target.value.trim().toLowerCase();
      if (term.length === 0) {
        this.selectedFilterIndex = -1;
        this.showFilterListing = false;
        return;
      }
      this.availableFilterCount = 0;
      this.lakesList.forEach(lakeInfo => {
        this.filterFields.forEach(field => {
          if (lakeInfo[field.key] && lakeInfo[field.key].toLowerCase().indexOf(term) >= 0) {
            this.availableFilterCount++;
            let values = filterOptionsMap.get(field.key);
            if (values && values.indexOf(lakeInfo[field.key]) === -1) {
              values.push(lakeInfo[field.key]);
            } else if (!values) {
              values = [lakeInfo[field.key]];
            }
            filterOptionsMap.set(field.key, values);
          }
        });
      });
      this.populateFilterOptions(filterOptionsMap);
      this.showFilterListing = true;
    }
  }


  private populateFilterOptions(filterOptionsMap: Map<string, Array<any>>) {
    this.filterFields.forEach(filterField => {
      let values = filterOptionsMap.get(filterField.key);
      if (values && values.length > 0) {
        this.filterOptions.push({'displayName': filterField.display, 'key': filterField.key, values: values});
      }
    });
  }

  refresh(lakeInfo) {
    this.refreshEmitter.emit(lakeInfo.lakeId);
  }

  onSort($event) {
    this.lakesList.sort((obj1: any, obj2: any) => {
      try {
        let val1 = $event.sortBy.split('.').reduce((obj1, k) => {
          return obj1[k];
        }, obj1);
        let val2 = $event.sortBy.split('.').reduce((obj2, k) => {
          return obj2[k];
        }, obj2);

        if ($event.sortOrder === Sort.ASC) {
          if ($event.type === 'string') {
            return val1.localeCompare(val2);
          }
          if ($event.type === 'number') {
            return val1 > val2;
          }
          if ($event.type === 'duration') {
            return DateUtils.compare(val1, val2);
          }
        }
        if ($event.type === 'string') {
          return val2.localeCompare(val1);
        }
        if ($event.type === 'number') {
          return val1 < val2;
        }
        if ($event.type === 'duration') {
          return DateUtils.compare(val2, val1);
        }
      } catch (e) {
      }
    });
  }
}

export enum LakeStatus {
  UP,
  DOWN,
  WAITING,
  NA
}

export class LakeInfo {
  id: number;
  name: string;
  lakeId: number;
  ambariUrl: string;
  ambariIpAddress: string;
  cluster?: Cluster;
  clusterType: string;
  status?: LakeStatus;
  dataCenter: string;
  city?: string;
  country?: string;
  nodes?: string = 'NA';
  services?: number;
  hdfsUsed?: string = 'NA';
  hdfsTotal?: string = 'NA';
  startTime?: number;
  isWaiting: boolean;

  get hdfsUsedInBytes(): number {
    return this.toBytes(this.hdfsUsed);
  }

  private toBytes(byteWithSize) {
    if (byteWithSize === 'NA') {
      return 0;
    } else {
      let values = byteWithSize.trim().split(' ');
      let size = values[1];
      let k = 1024;
      let sizes = Array('Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB');
      let i = sizes.indexOf(size);
      return parseInt(values[0], 10) * Math.pow(k, i);
    }
  }
}
