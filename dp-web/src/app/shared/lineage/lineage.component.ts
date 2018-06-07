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

import {
  Component,
  OnChanges,
  ViewChild,
  Input,
  SimpleChanges,
  AfterViewInit,
  ElementRef,
  HostListener
} from '@angular/core';
import {Router, ActivatedRoute} from '@angular/router';
import {TypeDefs} from '../../models/type-defs';
import {Observable} from 'rxjs/Rx';
import {Lineage} from '../../models/lineage';
import {AtlasService} from '../../services/atlas.service';

declare var d3: any;
declare var dagreD3: any;

@Component({
  selector: 'dp-lineage',
  templateUrl: './lineage.component.html',
  styleUrls: ['./lineage.component.scss']
})
export class LineageComponent implements OnChanges, AfterViewInit {

  @Input() guid: string = '1cb2fd1e-03b4-401f-a587-2151865d375a';
  @Input() entityDefCollection = new TypeDefs();
  @Input() clusterId = '1989';

  private readonly ENTITY_TYPE: string = 'entity';

  g: any;
  svg: any;
  zoom: any;
  typeMap: any = {};
  fromToObj: any = {};
  showLoader = false;
  layoutRendered = false;
  @ViewChild('graph') graph: ElementRef;
  asyncFetchCounter: number = 0;
  lineage: Lineage = new Lineage();
  activeNode = false;
  activeTip = false;
  tooltip: any;
  showLineage = true;

  readonly entityStateReadOnly = {
    'ACTIVE': false,
    'DELETED': true,
    'STATUS_ACTIVE': false,
    'STATUS_DELETED': true
  };

  constructor(private elementRef: ElementRef, private atlasService: AtlasService, private router: Router, private route: ActivatedRoute) {
  }

  createGraph() {
    let that = this;

    this.g.nodes().forEach((v) => {
      let node = this.g.node(v);
      // Round the corners of the nodes
      if (node) {
        node.rx = node.ry = 5;
      }
    });
    // Create the renderer
    let render = new dagreD3.render();
    // Add our custom arrow (a hollow-point)
    render.arrows().arrowPoint = function normal(parent, id, edge, type) {
      let marker = parent.append("marker")
        .attr("id", id)
        .attr("viewBox", "0 0 10 10")
        .attr("refX", 9)
        .attr("refY", 5)
        .attr("markerUnits", "strokeWidth")
        .attr("markerWidth", 10)
        .attr("markerHeight", 8)
        .attr("orient", "auto");

      let path = marker.append("path")
        .attr("d", "M 0 0 L 10 5 L 0 10 z")
        .style("stroke-width", 1)
        .style("stroke-dasharray", "1,0")
        .style("fill", edge.styleObj.stroke)
        .style("stroke", edge.styleObj.stroke);
      dagreD3.util.applyStyle(path, edge[type + "Style"]);
    };
    render.shapes().img = function circle(parent, bbox, node) {
      //var r = Math.max(bbox.width, bbox.height) / 2,
      let currentNode = false;
      if (node.id == that.guid) {
        currentNode = true
      }
      let shapeSvg = parent.append('circle')
        .attr('fill', 'url(#img_' + node.id + ')')
        .attr('r', currentNode ? '15px' : '14px')
        .attr("class", "nodeImage " + (currentNode ? "currentNode" : (node.isProcess ? "blue" : "green")));

      parent.insert("defs")
        .append("pattern")
        .attr("x", "0%")
        .attr("y", "0%")
        .attr("patternUnits", "objectBoundingBox")
        .attr("id", "img_" + node.id)
        .attr("width", "100%")
        .attr("height", "100%")
        .append('image')
        .attr("xlink:href", function (d) {
          if (node) {
            if (node.isProcess) {
              if (that.entityStateReadOnly[node.status]) {
                return 'assets/images/icon-gear-delete.png';
              } else if (node.id == this.guid) {
                return 'assets/images/icon-gear-active.png';
              } else {
                return 'assets/images/icon-gear.png';
              }
            } else {
              if (that.entityStateReadOnly[node.status]) {
                return 'assets/images/icon-table-delete.png';
              } else if (node.id == this.guid) {
                return 'assets/images/icon-table-active.png';
              } else {
                return 'assets/images/icon-table.png';
              }
            }
          }
        })
        .attr("x", "2")
        .attr("y", "2")
        .attr("width", currentNode ? "26" : "24")
        .attr("height", currentNode ? "26" : "24")

      node.intersect = function (point) {
        //return dagreD3.intersect.circle(node, points, point);
        return dagreD3.intersect.circle(node, currentNode ? 16 : 13, point);
      };
      return shapeSvg;
    };
    // Set up an SVG group so this we can translate the final graph.
    let svg = this.svg = d3.select(this.graph.nativeElement),
      svgGroup = svg.append("g");
    let zoom = this.zoom = d3.behavior.zoom()
      .scaleExtent([0.5, 6])
      .on("zoom", this.zoomed.bind(this));


    function interpolateZoom(translate, scale) {
      let self = this;
      return d3.transition().duration(350).tween("zoom", function () {
        var iTranslate = d3.interpolate(zoom.translate(), translate),
          iScale = d3.interpolate(zoom.scale(), scale);
        return function (t) {
          zoom
            .scale(iScale(t))
            .translate(iTranslate(t));

          that.zoomed();
        };
      });
    }

    function zoomClick() {
      var clicked = d3.event.target,
        direction = 1,
        factor = 0.2,
        target_zoom = 1,
        center = [that.g.graph().width / 2, that.g.graph().height / 2],
        extent = zoom.scaleExtent(),
        translate = zoom.translate(),
        translate0 = [],
        l = [],
        view = {x: translate[0], y: translate[1], k: zoom.scale()};

      d3.event.preventDefault();
      direction = (this.id === 'zoom_in') ? 1 : -1;
      target_zoom = zoom.scale() * (1 + factor * direction);

      if (target_zoom < extent[0] || target_zoom > extent[1]) {
        return false;
      }

      translate0 = [(center[0] - view.x) / view.k, (center[1] - view.y) / view.k];
      view.k = target_zoom;
      l = [translate0[0] * view.k + view.x, translate0[1] * view.k + view.y];

      view.x += center[0] - l[0];
      view.y += center[1] - l[1];

      interpolateZoom([view.x, view.y], view.k);
    }

    d3.selectAll(this.elementRef.nativeElement.querySelectorAll('span.lineageZoomButton')).on('click', zoomClick);
    this.tooltip = d3.tip()
      .attr('class', 'd3-tip')
      .offset([-18, 0])
      .html(function (d) {
        let value = that.g.node(d);
        let htmlStr = "";
        if (value.id !== that.guid) {
          htmlStr = "<h5 style='text-align: center;'>" + (value.isLineage ? "Lineage" : "Impact") + "</h5>";
        }
        htmlStr += "<h5 class='text-center'><span style='color:#359f89'>" + value.toolTipLabel + "</span></h5> ";
        if (value.typeName) {
          htmlStr += "<h5 class='text-center'><span>(" + value.typeName + ")</span></h5> ";
        }
        if (value.queryText) {
          htmlStr += "<h5>Query: <span style='color:#359f89'>" + value.queryText + "</span></h5> ";
        }
        return "<div class='tip-inner-scroll'>" + htmlStr + "</div>";
      });

    svg.call(zoom).call(this.tooltip);
    this.showLoader = false;

    render(svgGroup, this.g);
    svg.on("dblclick.zoom", null)
      .on("wheel.zoom", null);
    //change text postion
    svgGroup.selectAll("g.nodes g.label")
      .attr("transform", "translate(2,-30)");
    svgGroup.selectAll("g.nodes g.node")
      .on('click', function (d) {
        that.tooltip.hide(d);
        that.router.navigate([{outlets: {'sidebar': ['nodes', d]}}], { relativeTo: that.route, skipLocationChange: true});
      }).on('mouseleave', function (d) {
      that.activeNode = false;
      let nodeEL = this;
      setTimeout(function (argument) {
        if (!(that.activeTip || that.activeNode)) {
          nodeEL.classList.remove('active');
          that.tooltip.hide(d);
        }
      }, 400)
    });


    // Center the graph
    this.setGraphZoomPositionCal();
    zoom.event(svg);
    // svg.attr('height', this.g.graph().height * initialScale + 40);

  }

  ngAfterViewInit() {
  }

  checkForLineageOrImpactFlag(relations: any[], guid: string) {
    let nodeFound = relations.filter(relation => relation['fromEntityId'] === guid);
    if (nodeFound.length) {
      nodeFound.forEach((node) => {
        this.fromToObj[node.toEntityId]['isLineage'] = false;
        let styleObj = {
          fill: 'none',
          stroke: '#fb4200'
        };
        this.g.setEdge(node.fromEntityId, node.toEntityId, {
          'arrowhead': "arrowPoint",
          lineInterpolate: 'basis',
          "style": "fill:" + styleObj.fill + ";stroke:" + styleObj.stroke + "",
          'styleObj': styleObj
        });
        this.checkForLineageOrImpactFlag(relations, node.toEntityId);
      });
    }
  }

  fetchGraphData() {
    this.fromToObj = {};
    if (this.lineage.relations.length) {
      this.generateData(this.lineage.relations, this.lineage.guidEntityMap);
    } else {
      this.noLineage();
    }
  }

  generateData(relations: any[], guidEntityMap: any) {
    relations.forEach((obj, index) => {
      if (!this.fromToObj[obj.fromEntityId]) {
        this.fromToObj[obj.fromEntityId] = this.makeNodeObj(guidEntityMap[obj.fromEntityId]);
        this.g.setNode(obj.fromEntityId, this.fromToObj[obj.fromEntityId]);
      }
      if (!this.fromToObj[obj.toEntityId]) {
        this.fromToObj[obj.toEntityId] = this.makeNodeObj(guidEntityMap[obj.toEntityId]);
        this.g.setNode(obj.toEntityId, this.fromToObj[obj.toEntityId]);
      }
      let styleObj = {
        fill: 'none',
        stroke: '#8bc152'
      };
      this.g.setEdge(obj.fromEntityId, obj.toEntityId, {
        'arrowhead': "arrowPoint",
        lineInterpolate: 'basis',
        "style": "fill:" + styleObj.fill + ";stroke:" + styleObj.stroke + "",
        'styleObj': styleObj
      });
    });

    if (this.fromToObj[this.guid]) {
      this.fromToObj[this.guid]['isLineage'] = false;
      this.checkForLineageOrImpactFlag(relations, this.guid);
    }
    if (this.asyncFetchCounter == 0) {
      this.createGraph();
    }
  }

  getData() {
    Observable.forkJoin(
      this.atlasService.getEntityTypeDefs(this.clusterId, this.ENTITY_TYPE),
      this.atlasService.getLineage(this.clusterId, this.guid)
    ).subscribe((response: any) => {
      this.prepareData(response[0], response[1]);
    }, error => {
      this.noLineage();
    });
  }

  initialize() {
    // this.entityModel = new VEntity();
    // this.collection = new VLineageList();
    this.typeMap = {};
    this.asyncFetchCounter = 0;

    this.onRender();
    this.fetchGraphData();
  }

  setGraphZoomPositionCal() {
    let initialScale = 1.2;
    let svgEl = this.graph.nativeElement;
    let scaleEl = this.graph.nativeElement.querySelector('g');
    let graphBoundingClientRect = this.graph.nativeElement.getBoundingClientRect();

    let translateValue = [(graphBoundingClientRect.width - this.g.graph().width * initialScale) / 2, (graphBoundingClientRect.height - this.g.graph().height * initialScale) / 2];

    if (Object.keys(this.g._nodes).length > 15) {
      translateValue = [((this.graph.nativeElement.width() / 2)) / 2, 20];
      initialScale = 0;
      this.graph.nativeElement.classList.add('noScale');
    }

    // if (svgEl.parents('.panel.panel-fullscreen').length && svgEl.hasClass('noScale')) {
    //   if (!scaleEl.hasClass('scaleLinage')) {
    //     scaleEl.addClass('scaleLinage');
    //     initialScale = 1.2;
    //   } else {
    //     scaleEl.removeClass('scaleLinage');
    //     initialScale = 0;
    //   }
    // } else {
    //   scaleEl.removeClass('scaleLinage');
    // }
    this.zoom.translate(translateValue)
      .scale(initialScale);
  }

  makeNodeObj(relationObj) {
    var obj = {};
    obj['shape'] = "img";
    obj['typeName'] = relationObj.typeName;
    obj['label'] = relationObj.displayText.length > 17 ? relationObj.displayText.substr(0, 17) + '...' : relationObj.displayText;
    obj['toolTipLabel'] = relationObj.displayText;
    obj['id'] = relationObj.guid;
    obj['isLineage'] = true;
    obj['queryText'] = relationObj.queryText;
    if (relationObj.status) {
      obj['status'] = relationObj.status;
    }
    // let entityDef = this.entityDefCollection.fullCollection.find({ name: relationObj.typeName });
    let entityDef = this.entityDefCollection.entityDefs.find(entity => entity.name === relationObj.typeName);

    if (entityDef && entityDef['superTypes']) {
      obj['isProcess'] = entityDef['superTypes'].indexOf("Process") > -1 ? true : false;
    }

    return obj;
  }

  noLineage() {
    this.showLoader = false;
    this.showLineage = false;
    this.graph.nativeElement.innerHTML = '<text x="' + (this.graph.nativeElement.getBoundingClientRect().width - 150) / 2 + '" y="' + this.graph.nativeElement.getBoundingClientRect().height / 2 + '" fill="#666666">No lineage data found</text>';
  }

  onRender() {
    this.showLoader = true;
    if (!this.layoutRendered) {
      this.g = new dagreD3.graphlib.Graph()
        .setGraph({
          nodesep: 50,
          ranksep: 90,
          rankdir: "LR",
          marginx: 20,
          marginy: 20,
          transition: function transition(selection) {
            return selection.transition().duration(500);
          }
        })
        .setDefaultEdgeLabel(function () {
          return {};
        });
      this.layoutRendered = true;
    }
  }

  prepareData(typeDefs: TypeDefs, lineage: Lineage) {
    this.entityDefCollection = typeDefs;
    this.lineage = lineage;

    this.initialize();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes && changes['guid']) {
      this.showLoader = true;
      this.showLineage = true;
      this.getData();
    }
  }

  zoomed() {
    this.graph.nativeElement.querySelector('g').setAttribute("transform",
      "translate(" + this.zoom.translate() + ")" +
      "scale(" + this.zoom.scale() + ")"
    )
  }
}
