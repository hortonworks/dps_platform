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

import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import {TranslateModule} from "@ngx-translate/core";

import {SharedModule} from '../../shared/shared.module';
import { WorkspaceComponent } from './workspace/workspace.component';
import {analyticsRoutes} from './analytics.routes';
import {WorkspaceService} from '../../services/workspace.service';
import {TabsModule} from '../../shared/tabs/tabs.module';
import { AddWorkspaceComponent } from './workspace/add-workspace/add-workspace.component';
import {ClusterService} from '../../services/cluster.service';
import {SelectModule} from '../../shared/select/select.module';
import { AssetsComponent } from './workspace/assets/assets.component';
// import {DatasetSharedModule} from '../dataset/dataset-shared.module';
import {WorkspaceAssetsService} from '../../services/workspace-assets.service';
// import {DsAssetsService} from '../dataset/services/dsAssetsService';

@NgModule({
  imports: [
    RouterModule.forChild(analyticsRoutes),
    // DatasetSharedModule,
    CommonModule,
    SharedModule,
    SelectModule,
    TabsModule,
    TranslateModule
  ],
  declarations: [
    AddWorkspaceComponent,
    AssetsComponent,
    WorkspaceComponent
  ],
  providers: [
    // DsAssetsService,
    ClusterService,
    WorkspaceService,
    WorkspaceAssetsService
  ]
})
export class AnalyticsModule { }
