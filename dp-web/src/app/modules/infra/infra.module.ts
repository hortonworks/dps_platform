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
import {RouterModule} from '@angular/router';

import {TranslateModule} from '@ngx-translate/core';
import {NguiAutoCompleteModule} from '@ngui/auto-complete';
import {routes} from './infra.routes';
import {LakesComponent} from './views/lakes/lakes.component';
import {ClusterAddComponent} from './views/cluster-add/cluster-add.component';
import {ClusterEditComponent} from './views/cluster-edit/cluster-edit.component';
import {LakeStatsComponent} from './widgets/lake-stats/lake-stats.component';
import {LakesListComponent} from './widgets/lakes-list/lakes-list.component';
import {MapComponent} from './widgets/map/map.component';
import {TaggingWidgetModule} from '../../shared/tagging-widget/tagging-widget.module';
import {CollapsibleNavModule} from '../../shared/collapsible-nav/collapsible-nav.modue';
import {SharedModule} from '../../shared/shared.module';
import {DpSorterModule} from '../../shared/dp-table/dp-sorter/dp-sorter.module';
import {ClusterDetailsComponent} from './views/cluster-details/cluster-details.component';
import {UserManagementComponent} from './views/user-management/user-management.component';
import {DropdownModule} from '../../shared/dropdown/dropdown.module';
import {AddUserComponent} from './views/user-management/add-user/add-user.component';
import {PaginationModule} from '../../shared/pagination/pagination.module';
import {UsersComponent} from './views/user-management/users/users.component';
import {GroupsComponent} from './views/user-management/groups/groups.component';
import {AddGroupComponent} from './views/user-management/add-group/add-group.component';
import {ConfigDialogComponent} from './widgets/config-dialog/config-dialog.component';
import {ServiceManagementComponent} from './views/service-management/service-management.component';
import {VerificationComponent} from './views/service-management/verification/verification.component';
import {TabsModule} from '../../shared/tabs/tabs.module';
import { ManualInstallCheckComponent } from './views/service-management/manual-install-check/manual-install-check.component';
import { LdapEditConfigComponent } from './views/ldap-edit-config/ldap-edit-config.component';
import { CommonTopRowComponent } from './views/user-management/common-top-row/common-top-row.component';
import {SettingsComponent} from "./views/settings/settings.component";

@NgModule({
  imports: [
    RouterModule.forChild(routes),
    SharedModule,
    DpSorterModule,
    NguiAutoCompleteModule,
    TaggingWidgetModule,
    CollapsibleNavModule,
    DropdownModule,
    TranslateModule,
    PaginationModule,
    TabsModule
  ],

  declarations: [
    LakesComponent,
    ClusterAddComponent,
    ClusterEditComponent,
    LakeStatsComponent,
    LakesListComponent,
    ClusterDetailsComponent,
    MapComponent,
    UserManagementComponent,
    AddUserComponent,
    UsersComponent,
    GroupsComponent,
    AddGroupComponent,
    ConfigDialogComponent,
    ServiceManagementComponent,
    VerificationComponent,
    ManualInstallCheckComponent,
    LdapEditConfigComponent,
    CommonTopRowComponent,
    SettingsComponent
  ]
})
export class InfraModule {
}

