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

import {Component, OnInit, ViewChild, ElementRef, OnChanges, Input, SimpleChanges, HostListener} from '@angular/core';
import * as L from 'leaflet';
import 'leaflet-curve';

import {MapData, Point} from '../../../../models/map-data';
import {MapDimensions} from '../../../../models/map-data';
import {MapSize} from '../../../../models/map-data';
import {MapConnectionStatus} from '../../../../models/map-data';

import {GeographyService} from '../../../../services/geography.service';
import Layer = L.Layer;
import LayerGroup = L.LayerGroup;

@Component({
  selector: 'dp-map',
  templateUrl: './map.component.html',
  styleUrls: ['./map.component.scss'],
  providers: [GeographyService]
})

export class MapComponent implements OnChanges, OnInit {
  map: L.Map;
  @ViewChild('mapcontainer') mapcontainer: ElementRef;
  @Input('mapData') mapData: MapData[] = [];
  @Input('mapSize') mapSize: MapSize = MapSize.EXTRALARGE;
  @Input('showCount') showCount = false;

  pathLookup = [];

  statusColorUp = '#3FAE2A';
  statusColorDown = '#EF6162';
  statusColorNA = '#53646A';

  mapColor = '#F2F7FC';
  mapBoundary = '#D0D3D7';

  mapOptions = {
    scrollWheelZoom: true,
    zoomControl: true,
    dragging: true,
    boxZoom: true,
    doubleClickZoom: false,
    zoomSnap: 0.1,
    zoomAnimation: false,
    attributionControl: false
  };

  markers: L.Marker[] = [];
  paths: Layer[] = [];
  markerGroup: LayerGroup;
  pathGroup: LayerGroup;

  private countMap = new Map();

  defaultMapSizes: MapDimensions[] = [
    new MapDimensions('240px', '420px', 0.5),
    new MapDimensions('420px', '540px', 1),
    new MapDimensions('480px', '680px', 1.3),
    new MapDimensions('500px', '100%', 1.54)
  ];

  constructor(private geographyService: GeographyService) {
  }

  ngOnInit() {
    //The TRANSITION is causing a horizontal scrollbar disabling for now via hack
    L.DomUtil['TRANSITION'] = 'unused';
    if (this.map) {
      this.map.remove();
    }
    this.geographyService.getCountries().subscribe((countries) => {
      this.drawMap(countries);
    });
  }

  drawMap(countries) {
    let mapDimensions = this.defaultMapSizes[this.mapSize] || this.defaultMapSizes[MapSize.EXTRALARGE];
    this.mapcontainer.nativeElement.style.height = mapDimensions.height;
    this.mapcontainer.nativeElement.style.width = mapDimensions.width;
    const map = L.map(this.mapcontainer.nativeElement, this.mapOptions);
    let countriesLayer = L.geoJSON(countries, {
      style: feature => ({
        fillColor: this.mapColor,
        fillOpacity: 1,
        weight: 1,
        color: this.mapBoundary
      })
    }).addTo(map);
    map.fitBounds(countriesLayer.getBounds());
    this.map = map;
    this.map.setZoom(mapDimensions.zoom);
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!changes['mapData'] || !this.map) {
      return;
    }
    this.countMap = new Map();
    this.removeExistingMarker();
    this.mapData.forEach((data) => {
      let start = data.start;
      let end = data.end;
      if (start) {
        this.plotPoint(start);
      }
      if (start && end) {
        this.plotPoint(end);
        this.drawConnection(start, end);
      }
    });
    this.markerGroup = L.layerGroup(this.markers);
    this.pathGroup = L.layerGroup(this.paths);
  }

  createPopup(marker, data: MarkerData) {
    const makeList = (cluster) => `<div class="details-container">
         <div class="status"><i class="fa fa-circle" style="color:${this.getStatusColor(cluster.status)}"></i> ${cluster.datacenter} / ${cluster.name}</div> 
         <div class="location">${data.location}</div>
       </div>`;
    let html = `<div class="pop-up-container">${data.clusters.map(makeList).join('')}</div>`;
    marker.bindTooltip(html, {
      direction: 'right',
      offset: [10, 0]
    });
  }

  private getStatusColor(status) {
    let color;
    if (status === MapConnectionStatus.UP) {
      color = this.statusColorUp;
    } else if (status === MapConnectionStatus.DOWN) {
      color = this.statusColorDown;
    } else {
      color = this.statusColorNA;
    }
    return color;
  }

  plotPoint(position: Point) {
    let latLng = L.latLng(position.latitude, position.longitude);
    let key = `${position.latitude}-${position.longitude}`;
    let existingCount = this.countMap.get(key);
    let count = existingCount ? existingCount.count + 1 : 1;
    let clusterInfo = new ClusterInfo(position.clusterName, position.status, position.datacenter);
    let clusters = existingCount ? existingCount.clusters.concat([clusterInfo]) : [clusterInfo];
    let markerData = new MarkerData(position.location, count, clusters);
    this.countMap.set(key, markerData);
    let marker = this.createMarker(latLng, count);
    if (position.datacenter && position.location) {
      this.createPopup(marker, markerData);
    }
  }

  private createMarker(latLng, count) {
    let marker = L.marker(latLng, {
      icon: this.getMarkerIcon(count),
    }).addTo(this.map);
    this.markers.push(marker);
    return marker;
  }

  removeExistingMarker() {
    if (this.markerGroup) {
      this.markerGroup.eachLayer(layer => {
        layer.remove();
      });
    }
    if (this.pathGroup) {
      this.pathGroup.eachLayer(layer => {
        layer.remove();
      });
    }
    this.pathLookup = [];
  }

  private getMarkerIcon(count: number) {
    let html = `<div class="marker-wrapper">
                <i class="fa fa-map-marker marker-icon" ></i>
                <span class="marker-counter">
                  ${this.showCount ? count : ''}
                </span>
          </div>`;
    return L.divIcon(({
      iconSize: null,
      iconAnchor: [0, 0],
      className: 'custom-map-marker',
      html: html
    }));
  }

  private pathExists(curve) {
    return this.pathLookup.find(path => {
      return path.start.latitude === curve.start.latitude && path.start.longitude === curve.start.longitude
        && path.end.latitude === curve.end.latitude && path.end.longitude === curve.end.longitude;
    });
  }

  drawConnection(start, end) {
    let midPoint = {x: (start.latitude + end.latitude) / 2, y: (start.longitude + end.longitude) / 2};
    let distance = Math.sqrt(Math.pow(end.latitude - start.latitude, 2) + Math.pow(end.longitude - start.longitude, 2));
    let path = L.curve(
      ['M', [start.latitude, start.longitude], 'Q', [midPoint.x + distance / 3, midPoint.y + distance / 3], [end.latitude, end.longitude]],
      {
        color: this.statusColorUp,
        fill: false,
        dashArray: '1, 5',
        weight: 2
      });
    if (this.pathExists({start: start, end: end}) || this.pathExists({start: end, end: start})) {
      return;
    }
    this.pathLookup.push({start: start, end: end});
    path.addTo(this.map);
    this.paths.push(path);
  }
}

export class MarkerData {
  constructor(public location: string, public count: number, public clusters: any[]) {
  };
}

export class ClusterInfo {
  constructor(public name: string, public status: MapConnectionStatus, public datacenter: string) {
  }
}
