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

import { Component, OnInit, ViewChild } from '@angular/core';
import { NgForm } from '@angular/forms';
import { Router } from '@angular/router';
import {Observable} from 'rxjs/Observable';

import {WorkspaceService} from '../../../../services/workspace.service';
import {Workspace} from '../../../../models/workspace';
import {Alerts} from '../../../../shared/utils/alerts';
import {ClusterService} from '../../../../services/cluster.service';
import {Cluster} from '../../../../models/cluster';
import {TranslateService} from '@ngx-translate/core/src/translate.service';

@Component({
  selector: 'dp-add-workspace',
  templateUrl: './add-workspace.component.html',
  styleUrls: ['./add-workspace.component.scss']
})
export class AddWorkspaceComponent implements OnInit {

  workspace = new Workspace();
  clusterServiceObservable: Observable<Cluster[]>;
  @ViewChild('workspaceForm') workspaceForm: NgForm;

  constructor(private workspaceService: WorkspaceService,
              private clusterService: ClusterService,
              private translate: TranslateService,
              private router: Router) { }

  ngOnInit() {
    this.clusterServiceObservable = this.clusterService.list();
  }

  cancel() {
     this.router.navigateByUrl('analytics/workspace');
  }

  save() {
    if (!this.workspaceForm.form.valid) {
      this.translate.get('common.defaultRequiredFields').subscribe(msg => Alerts.showErrorMessage(msg));
      return;
    }
    this.workspaceService.save(this.workspace).subscribe(() => {
      Alerts.showSuccessMessage('Added workspace ' + this.workspace.name);
      this.workspaceService.dataChanged.next();
      this.router.navigateByUrl('analytics/workspace');
    });
  }

  updateVal() {
    this.workspace.source = parseInt(this.workspace.source + '', 10);
  }
}
