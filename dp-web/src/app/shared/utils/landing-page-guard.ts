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

import {Injectable} from '@angular/core';
import {CanActivate, Router} from '@angular/router';
import {Observable} from 'rxjs/Observable'

import {RbacService} from '../../services/rbac.service';
import {ConfigurationService} from '../../services/configuration.service';
import { AddOnAppService } from '../../services/add-on-app.service';

@Injectable()
export class LandingPageGuard implements CanActivate {
  constructor(private router: Router,
              private rbacService: RbacService,
              private addOnAppService:AddOnAppService,
              private configService: ConfigurationService) {
  }

  canActivate() {
    return Observable.create(observer => {
      this.configService.retrieve().subscribe(({lakeWasInitialized}) => {
        this.addOnAppService.listPlugins('true','true','true').subscribe(plugins =>{
          this.rbacService.getLandingPage(lakeWasInitialized, plugins).subscribe(landingPage => {
            this.redirect(observer, true, landingPage);
          });
        });
      }, (error) => {
        console.error(error);
      });
    });
  }

  redirect(observer, canActivate, route?) {
    observer.next(canActivate);
    observer.complete();
    if (route) {
      if (this.router.config.filter(r => r.path === `/${route}`).length) {
        this.router.navigate([route]);
      } else {
        window.location.pathname = route;
      }
    }
  }
}
