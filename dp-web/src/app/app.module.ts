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

import {BrowserModule} from '@angular/platform-browser';
import {APP_INITIALIZER, NgModule} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {HttpModule, Http} from '@angular/http';
import {RouterModule} from '@angular/router';
import {TranslateModule, TranslateLoader} from '@ngx-translate/core';
import {TranslateHttpLoader} from '@ngx-translate/http-loader';


import {routes} from './app.routes';

import {AppComponent} from './app.component';
import {SecuredRouteGuard, UnsecuredRouteGuard, DoCleanUpAndRedirectGuard} from './shared/utils/auth-guard';
import {LandingPageGuard} from './shared/utils/landing-page-guard';

import {NotFoundRouteComponent} from './views/not-found-route/not-found-route.component';
import {SignInComponent} from './views/sign-in/sign-in.component';
import {AuthenticationService} from './services/authentication.service';
import {LakeService} from './services/lake.service';
import {LocationService} from './services/location.service';
import {ClusterService} from './services/cluster.service';
import {IdentityService} from './services/identity.service';
import {ConfigurationService} from './services/configuration.service';
import {MdlService} from './services/mdl.service';
import {MdlDirective} from './directives/mdl.directive';

import {CategoryService} from './services/category.service';
import {DataSetService} from './services/dataset.service';
import {DatasetTagService} from './services/tag.service';
import {HeaderModule} from './widgets/header/header.module';
import {CollapsibleNavModule} from './shared/collapsible-nav/collapsible-nav.modue';
import {SidebarComponent} from './widgets/sidebar/sidebar.component';
import {UserService} from './services/user.service';
import {CollapsibleNavService} from './services/collapsible-nav.service';
import {AssetService} from './services/asset.service';
import {LoaderSpinModule} from './shared/loader-spin/loader-spin.module';
import {Loader} from './shared/utils/loader';
import {RbacService} from './services/rbac.service';
import {AuthErrorComponent} from './shared/auth-error/auth-error.component';
import {NavigationGuard} from './shared/utils/navigation-guard';
import {GroupService} from './services/group.service';

import {AuthUtils} from './shared/utils/auth-utils';
import {AddOnAppService} from './services/add-on-app.service';
import {ServiceErrorComponent} from './shared/service-error/service-error.component';
import {CommentService} from "./services/comment.service";
import {SettingsService} from "./services/settings.service";

export function HttpLoaderFactory(http: Http) {
  return new TranslateHttpLoader(http);
}

export function init_app(userService: UserService, configService: ConfigurationService) {
  return () => new Promise((resolve, reject) => {
    userService.getUserDetail()
      .finally(() =>{
      configService.getGATrackingStatus()
        .subscribe((gaProperties:any) => {
          AuthUtils.setGATrackingStatus(gaProperties.value === "true");
        }, (error) => {
          console.error("Could not fetch GA Properties", error);
        });
      }).subscribe(user => {
        AuthUtils.setUser(user);
        resolve(true)
      }, (error) => {
        resolve(false)
      });
  });
}

@NgModule({
  imports: [
    BrowserModule,
    FormsModule,
    HttpModule,
    HeaderModule,
    CollapsibleNavModule,
    RouterModule.forRoot(routes),
    LoaderSpinModule,
    TranslateModule.forRoot({
      loader: {
        provide: TranslateLoader,
        useFactory: HttpLoaderFactory,
        deps: [Http]
      }
    })
  ],
  declarations: [
    AppComponent,

    NotFoundRouteComponent,
    SignInComponent,
    SidebarComponent,
    AuthErrorComponent,
    ServiceErrorComponent,

    MdlDirective

  ],
  bootstrap: [AppComponent],
  providers: [
    AuthenticationService,
    DatasetTagService,
    CategoryService,
    DataSetService,
    LakeService,
    LocationService,
    ClusterService,
    CommentService,
    IdentityService,
    ConfigurationService,
    AssetService,
    UserService,
    {
      provide: APP_INITIALIZER,
      useFactory: init_app,
      deps: [UserService, ConfigurationService],
      multi: true
    },
    CollapsibleNavService,
    Loader,
    RbacService,
    GroupService,
    AddOnAppService,

    MdlService,
    SettingsService,

    SecuredRouteGuard,
    UnsecuredRouteGuard,
    DoCleanUpAndRedirectGuard,
    LandingPageGuard,
    NavigationGuard

  ]
})
export class AppModule {
}
