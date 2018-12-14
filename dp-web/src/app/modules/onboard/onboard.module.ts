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

import {NgModule} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {TranslateModule} from '@ngx-translate/core';

import {routes} from './onboard.routes';
import {NguiAutoCompleteModule} from '@ngui/auto-complete';
import {FirstRunComponent} from './views/first-run/first-run.component';
import {DpOnboardComponent} from './views/dp-onboard/dp-onboard.component';
import {MultiSelect} from '../../shared/multi-select/multi-select.component';
import {LdapConfigComponent} from './views/ldap-config/ldap-config.component';
import {UserAddComponent} from './views/user-add/user-add.component';
import {TaggingWidgetModule} from '../../shared/tagging-widget/tagging-widget.module';
import {StatusCheckGuard} from './guards/status-check-guard';
import {ConfigCheckGuard} from './guards/config-check-guard';
import { SharedModule } from '../../shared/shared.module';

@NgModule({
  imports: [
    FormsModule,
    CommonModule,
    NguiAutoCompleteModule,
    RouterModule.forChild(routes),
    TaggingWidgetModule,
    TranslateModule,
    SharedModule
  ],
  declarations: [
    FirstRunComponent,
    MultiSelect,
    DpOnboardComponent,
    LdapConfigComponent,
    UserAddComponent
  ],
  providers: [
    StatusCheckGuard,
    ConfigCheckGuard
  ]
})
export class OnboardModule {
}
