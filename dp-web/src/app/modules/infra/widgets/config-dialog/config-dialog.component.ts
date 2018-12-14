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

import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild} from '@angular/core';
import {ClusterDetailRequest} from '../../../../models/cluster-state';
import {NgForm} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';

@Component({
  selector: 'dp-config-dialog',
  templateUrl: './config-dialog.component.html',
  styleUrls: ['./config-dialog.component.scss']
})
export class ConfigDialogComponent implements OnChanges {

  @Input() requestAmbariCreds;
  @Input() requestKnoxURL;
  @Input() show;
  @Output('onSave') saveEmitter: EventEmitter<ClusterDetailRequest> = new EventEmitter<ClusterDetailRequest>();
  @Output('onCancel') closeEmitter: EventEmitter<boolean> = new EventEmitter<boolean>();
  @ViewChild('configForm') configForm: NgForm;

  errorMessage: string;
  showError: boolean;

  clusterDetailsRequest: ClusterDetailRequest = new ClusterDetailRequest();

  constructor(private translateService: TranslateService) {

  }

  ngOnChanges(changes: SimpleChanges) {
    let dialog: any = document.querySelector('#dialog');
    if (changes['show'] && this.show) {
      if (dialog) {
        this.clusterDetailsRequest = new ClusterDetailRequest();
        dialog.showModal();
      }
    }
  }

  save() {
    if (!this.configForm.form.valid) {
      this.errorMessage = this.translateService.instant('common.defaultRequiredFields');
      this.showError = true;
      return;
    }
    let dialog: any = document.querySelector('#dialog');
    dialog.close();
    this.saveEmitter.emit(this.clusterDetailsRequest);
  }

  cancel() {
    let dialog: any = document.querySelector('#dialog');
    dialog.close();
    this.closeEmitter.emit(true);
  }

}
