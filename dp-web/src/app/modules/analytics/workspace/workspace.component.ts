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

import { Component, OnInit, OnDestroy } from '@angular/core';
import { Router } from '@angular/router';
import {Subscription} from 'rxjs/Rx';

import {WorkspaceService} from '../../../services/workspace.service';
import {TabStyleType} from '../../../shared/tabs/tabs.component';
import {WorkspaceDTO} from '../../../models/workspace-dto';
import {Alerts} from '../../../shared/utils/alerts';
import {DialogBox, DialogType} from '../../../shared/utils/dialog-box';
import {TranslateService} from '@ngx-translate/core';

declare var zeppelinURL;

export enum ToggleView {
  TABLE, GRID
}

@Component({
  selector: 'dp-workspace',
  templateUrl: './workspace.component.html',
  styleUrls: ['./workspace.component.scss']
})
export class WorkspaceComponent implements OnInit, OnDestroy {
  toggleView = ToggleView;
  selectedViewType = ToggleView.TABLE;
  workspaceChanged: Subscription;
  workspacesDTOS: WorkspaceDTO[] = [];
  tabType = TabStyleType;

  tabImages = {'TABLE': 'fa-list-ul', 'GRID': 'fa-th'};

  constructor(private router: Router,
              private workspaceService: WorkspaceService,
              private  translateService: TranslateService) {}

  addWorkspace() {
    this.router.navigateByUrl('analytics/workspace/(dialog:add-workspace/new)');
  }

  editWorkspace($event, workspacesDTO: WorkspaceDTO) {
    if ($event.target.nodeName !== 'I') {
      /* Temporary arrangement for demos: zeppelinURL is defined in index.html*/
      if (typeof(zeppelinURL) !== 'undefined') {
        window.location.href = zeppelinURL +
                                '&workspaceId=' +encodeURIComponent(encodeURIComponent(String(workspacesDTO.workspace.id))) +
                                '&workspaceName=' +encodeURIComponent(encodeURIComponent(String(workspacesDTO.workspace.name)));
        return;
      } else {
        this.router.navigate(['/workspace/' + workspacesDTO.workspace.name + '/assets']);
      }
    }
  }

  delete(name: string) {
    DialogBox.showConfirmationMessage(this.translateService.instant('pages.infra.labels.confirmDelete'),
      'Do you wish to delete workspace ' + name,
      this.translateService.instant('common.confirm'), this.translateService.instant('common.cancel'),
      DialogType.DeleteConfirmation).subscribe(result => {
      if (result) {
        this.workspaceService.delete(name).subscribe(() => {
          Alerts.showSuccessMessage('Deleted workspace ' + name);
          this.workspaceService.dataChanged.next();
        });
      }
    });
  }

  getWorkspaces() {
    this.workspaceService.listDTO().subscribe((data) => {
      this.workspacesDTOS = data.sort((dto1, dto2) => dto1.workspace.name.localeCompare(dto2.workspace.name));
    });
  }

  ngOnInit() {
    this.workspaceChanged = this.workspaceService.dataChanged$.subscribe(() => {
      this.getWorkspaces();
    });
    this.workspaceService.dataChanged.next();
  }

  ngOnDestroy() {
    if (this.workspaceChanged && !this.workspaceChanged.closed) {
      this.workspaceChanged.unsubscribe();
    }
  }
}
