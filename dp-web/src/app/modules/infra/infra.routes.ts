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

import {Routes} from '@angular/router';

import {LakesComponent} from './views/lakes/lakes.component';
import {ClusterAddComponent} from './views/cluster-add/cluster-add.component';
import {ClusterEditComponent} from './views/cluster-edit/cluster-edit.component';
import {ClusterDetailsComponent} from './views/cluster-details/cluster-details.component';
import {AddUserComponent} from './views/user-management/add-user/add-user.component';
import {AddGroupComponent} from './views/user-management/add-group/add-group.component';
import {UsersComponent} from './views/user-management/users/users.component';
import {GroupsComponent} from './views/user-management/groups/groups.component';
import {ServiceManagementComponent} from './views/service-management/service-management.component';
import {VerificationComponent} from './views/service-management/verification/verification.component';
import {ManualInstallCheckComponent} from './views/service-management/manual-install-check/manual-install-check.component';
import {LdapEditConfigComponent} from "./views/ldap-edit-config/ldap-edit-config.component";
import {SettingsComponent} from "./views/settings/settings.component";

export const routes: Routes = [{
    path: '',
    pathMatch: 'full',
    redirectTo: 'clusters'
  }, {
    path: 'clusters',
    data: {
      crumb: 'infra.clusters'
    },
    children: [{
      path: '',
      pathMatch: 'full',
      component: LakesComponent,
      data: {
        crumb: undefined
      }
    }, {
      path: 'add',
      component: ClusterAddComponent,
      data: {
        crumb: 'infra.clusters.add'
      }
    }, {
      path: ':id',
      component: ClusterDetailsComponent,
      data: {
        crumb: 'infra.clusters.cCluster'
      }
    }, {
      path: ':id/edit',
      component: ClusterEditComponent,
      data: {
        crumb: 'infra.clusters.cCluster.edit'
      }
    }]
  }, {
    path: 'services',
    data: {
      crumb: 'infra.services'
    },
    children: [{
      path: '',
      component: ServiceManagementComponent,
      data: {
        crumb: undefined
      },
    }, {
      path: 'add',
      component: ManualInstallCheckComponent,
      data: {
        crumb: 'infra.services.add'
      }
    }, {
      path: ':name/verify',
      component: VerificationComponent,
      data: {
        crumb: 'infra.services.cService.verify'
      }
    }]
  }, {
    path: 'manage-access',
    pathMatch: 'full',
    redirectTo: 'manage-access/users'
  }, {
    path: 'manage-access/identity-provider-edit',
    component: LdapEditConfigComponent,
    data: {
      crumb: 'infra.access.identity_provider_edit'
    },
  },{
    path: 'manage-access/users',
    component: UsersComponent,
    data: {
      crumb: 'infra.access.users'
    },
    children: [{
      path: 'add',
      component: AddUserComponent,
      outlet: 'sidebar'
    }, {
      path: ':name/edit',
      component: AddUserComponent,
      outlet: 'sidebar'
    }]
  }, {
    path: 'manage-access/groups',
    component: GroupsComponent,
    data: {
      crumb: 'infra.access.groups'
    },
    children: [{
      path: 'add',
      component: AddGroupComponent,
      outlet: 'sidebar'
    }, {
      path: ':name/edit',
      component: AddGroupComponent,
      outlet: 'sidebar'
    }]
  }, {
  path: 'settings',
  component: SettingsComponent,
  data: {
    crumb: 'infra.settings'
  }
}];
