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

import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { Router, ActivatedRoute, NavigationEnd, Params, PRIMARY_OUTLET } from '@angular/router';

@Component({
  selector: 'dp-bread-crumb',
  templateUrl: './bread-crumb.component.html',
  styleUrls: ['./bread-crumb.component.scss']
})
export class BreadCrumbComponent implements OnInit, OnDestroy {

  breadcrumbs: IBreadcrumb[];

  constructor(
    private activatedRoute: ActivatedRoute,
    private router: Router
  ) {
    this.breadcrumbs = [];
  }

  ngOnInit(): void {
    this.router.events
      .filter(event => event instanceof NavigationEnd)
      .subscribe(event => {
        const root: ActivatedRoute = this.activatedRoute.root;
        this.breadcrumbs = this.getBreadcrumbs(root);
      });
  }

  ngOnDestroy(): void {
    // throw new Error('Method not implemented.');
  }

  private getBreadcrumbs(
    route: ActivatedRoute,
    url: string = '',
    breadcrumbs: IBreadcrumb[] = []
  ): IBreadcrumb[] {
    const ROUTE_DATA_BREADCRUMB: string = 'crumb';

    //get the child routes
    let children: ActivatedRoute[] = route.children;

    //run only for child in primary route
    const cChild = children.find(cChild => cChild.outlet === PRIMARY_OUTLET);

    //return if there are no more children in primary route
    if (!cChild) {
      return breadcrumbs;
    }

    const {data, params, url: cUrl} = cChild.snapshot;
    //verify the custom data property "breadcrumb" is specified on the route
    if (!data.hasOwnProperty(ROUTE_DATA_BREADCRUMB) || !data[ROUTE_DATA_BREADCRUMB]) {
      return this.getBreadcrumbs(cChild, url, breadcrumbs);
    }

    //get the route's URL segment
    let routeURL: string = cUrl.map(segment => segment.path).join("/");

    //append route URL to URL
    url += `/${routeURL}`;

    //add breadcrumb
    breadcrumbs.push({
      label: data[ROUTE_DATA_BREADCRUMB],
      params,
      url
    } as IBreadcrumb);

    //recursive
    return this.getBreadcrumbs(cChild, url, breadcrumbs);
  }
}

interface IBreadcrumb {
  label: string;
  params: Params;
  url: string;
}
