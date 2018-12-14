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

import { Routes } from '@angular/router';

import { SignInComponent } from './views/sign-in/sign-in.component';

import { UnsecuredRouteGuard, DoCleanUpAndRedirectGuard } from './shared/utils/auth-guard';
import { LandingPageGuard } from './shared/utils/landing-page-guard';
import { NotFoundRouteComponent } from './views/not-found-route/not-found-route.component';
import {NavigationGuard} from './shared/utils/navigation-guard';
import {AuthErrorComponent} from './shared/auth-error/auth-error.component';
import {LoaderSpinComponent} from './shared/loader-spin/loader-spin.component';
import {ServiceErrorComponent} from './shared/service-error/service-error.component';

export const routes: Routes = [{
  path: 'onboard',
  loadChildren: './modules/onboard/onboard.module#OnboardModule',
  canActivate:[ NavigationGuard ],
  data: {
    crumb: 'onboard',
    title: 'core',
  }
}, {
  path: 'infra',
  loadChildren: './modules/infra/infra.module#InfraModule',
  canActivate:[ NavigationGuard ],
  data: {
    crumb: 'infra',
    title: 'core',
  }
}, {
  path: 'profile',
  loadChildren: './modules/profile/profile.module#ProfileModule',
  canActivate:[ NavigationGuard ],
  data: {
    crumb: 'profile',
    title: 'core',
  }
}, {
  path: 'sign-in',
  component: SignInComponent,
  canActivate:[ UnsecuredRouteGuard ],
  data: {
    title: 'core',
  }
}, {
  path: 'sign-out',
  component: SignInComponent,
  canActivate: [ DoCleanUpAndRedirectGuard ],
  data: {
    title: 'core',
  }
}, {
  path: 'unauthorized',
  component: AuthErrorComponent,
  data: {
    crumb: 'unauthorized',
    title: 'core',
  }
}, {
  path: 'service-error/:type',
  component: ServiceErrorComponent,
  data: {
    crumb: 'service_not_enabled',
    title: 'core',
  }
},{
  path: '',
  pathMatch: 'full',
  component: LoaderSpinComponent,
  canActivate: [ LandingPageGuard ],
  data: {
    title: 'core',
  }
}, {
  path: '**',
  component: NotFoundRouteComponent,
  data: {
    crumb: 'not_found',
    title: 'core',
  }
}];
