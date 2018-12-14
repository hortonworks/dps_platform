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

import {Router, ActivatedRoute, Params} from '@angular/router';
import {Subject} from 'rxjs/Rx';

import {Credential} from '../../models/credential';
import {AuthenticationService} from '../../services/authentication.service';
import {ConfigurationService} from '../../services/configuration.service';
import {TranslateService} from '@ngx-translate/core';
import {AuthUtils} from "../../shared/utils/auth-utils";

@Component({
  selector: 'dp-sign-in',
  templateUrl: './sign-in.component.html',
  styleUrls: ['./sign-in.component.scss']
})
export class SignInComponent implements OnInit {

  _isAuthInProgress = false;
  _isAuthSuccessful = false;
  message = '';
  landingPage: String;

  credential: Credential = new Credential('', '');
  authenticate: Subject<Credential>;
  originalUrl = `${window.location.protocol}//${window.location.host}/`;

  constructor(private authenticaionService: AuthenticationService,
              private router: Router,
              private activatedRoute: ActivatedRoute,
              private configService: ConfigurationService,
              private translateService: TranslateService) {
    if (window.location.hash.length > 0 && window.location.hash === '#SESSEXPIRED') {
      this.message = 'SESSIONEXPIRED';
    }
  }

  get gaTrackingStatus(){
    return AuthUtils.getGATrackingStatus();
  }

  ngOnInit() {
    let currentLocation = window.location.href.split('/');
    this.landingPage = `${currentLocation[0]}//${currentLocation[2]}`;
    this.activatedRoute
      .queryParams
      .subscribe((params: Params) => {
        this.originalUrl = params['originalUrl'] ? params['originalUrl'] : `${window.location.protocol}//${window.location.host}/`;
      });
  }

  onSubmit(event) {
    this._isAuthInProgress = true;
    this.authenticaionService
      .signIn(this.credential)
      .finally(() => {
        this._isAuthInProgress = false;
      }).subscribe(
      (() => {
        this._isAuthSuccessful = true;
        window.location.replace(this.originalUrl); // we need to force a refresh for apps
      }),
      error => {
        if (error.status === 401) {
          this.message = this.translateService.instant("pages.signin.description.invalidCredentials");
        }else{
          this.message = this.translateService.instant("pages.signin.description.systemError");
        }
        this._isAuthSuccessful = false;
      }
    );
  }
}
