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

import {AfterViewChecked, ChangeDetectorRef, Component, OnInit} from '@angular/core';
import {Title} from '@angular/platform-browser';
import {Router, ActivatedRoute, NavigationEnd} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';

import {MdlService} from './services/mdl.service';

import {User} from './models/user';
import {HeaderData} from './models/header-data';
import {CollapsibleNavService} from './services/collapsible-nav.service';
import {Loader, LoaderStatus} from './shared/utils/loader';
import {RbacService} from './services/rbac.service';
import {AuthenticationService} from './services/authentication.service';
import {AuthUtils} from './shared/utils/auth-utils';

export enum ViewPaneState {
  MAXIMISE, MINIMISE
}

@Component({
  selector: 'data-plane',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit, AfterViewChecked {

  marginLeft = 0;
  viewPaneStates = ViewPaneState;
  viewPaneState = ViewPaneState.MAXIMISE;
  headerData: HeaderData = new HeaderData();
  showLoader: LoaderStatus;

  constructor(
              private titleService: Title,
              private router: Router,
              private activatedRoute: ActivatedRoute,
              private mdlService: MdlService,
              private translateService: TranslateService,
              private collapsibleNavService: CollapsibleNavService,
              private rbacService: RbacService,
              private cdRef: ChangeDetectorRef,
              private authenticationService: AuthenticationService
            ) {

    translateService.setTranslation('en', require('../assets/i18n/en.json'));
    translateService.setDefaultLang('en');
    translateService.use('en');

    router.events.subscribe(event => {
      if(event instanceof NavigationEnd) {
        var titles = this.getTitles(router.routerState, router.routerState.root);
        if(titles.length > 0){
          titleService.setTitle(translateService.instant(`titles.${titles[titles.length - 1]}`));
        } else {
          titleService.setTitle(translateService.instant('titles.core'));
        }
      }
    });
  }

  isUserSignedIn() {
    return  AuthUtils.isUserLoggedIn();
  }

  getUser(): User {
    return AuthUtils.getUser();
  }

  isValidUser() {
    return AuthUtils.isValidUser();
  }

  ngOnInit() {
    AuthUtils.loggedIn$.subscribe(() => {
      this.setHeaderData();
    });

    if(this.isUserSignedIn()){
      this.setHeaderData();
    }

    this.collapsibleNavService.collpaseSideNav$.subscribe(collapsed => {
      this.viewPaneState = collapsed ? ViewPaneState.MINIMISE : ViewPaneState.MAXIMISE;
    });

    Loader.getStatus().subscribe(status => {
      this.showLoader = status
    });
  }

  ngAfterViewChecked() {
    this.cdRef.detectChanges();
  }

  setHeaderData() {
    this.headerData = new HeaderData();
    this.headerData.personas = this.rbacService.getPersonaDetails();
  }

  getTitles(state, parent) {
    var data = [];
    if(parent && parent.snapshot.data && parent.snapshot.data.title) {
      data.push(parent.snapshot.data.title);
    }

    if(state && parent) {
      data.push(...this.getTitles(state, state.firstChild(parent)));
    }
    return data;
  }

  doSignOut() {
    this.authenticationService
      .signOutAndRedirect();
  }
}
