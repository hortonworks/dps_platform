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

import {Component, OnInit} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {AddOnAppService} from '../../../../../services/add-on-app.service';
import {ConfigPayload, SKU} from '../../../../../models/add-on-app';
import {ServiceErrorType} from "../../../../../shared/utils/enums";

@Component({
  selector: 'dp-verification',
  templateUrl: './verification.component.html',
  styleUrls: ['./verification.component.scss']
})
export class VerificationComponent implements OnInit {
  isInvalid = false;
  verificationInProgress = false;
  verified = false;
  smartSenseId: string;
  skuName: string;
  showError = false;
  errorMessage: string;
  descriptionParams: any;

  constructor(private router: Router,
              private translateService: TranslateService,
              private route: ActivatedRoute,
              private addOnAppService: AddOnAppService) {
  }

  ngOnInit() {
    let serviceName = this.route.snapshot.params['name'];
    this.addOnAppService.getServiceStatus(serviceName).subscribe(response => {
      if(!response.installed){
        this.router.navigate(['/service-error', ServiceErrorType.NOT_INSTALLED]);
      }else{
        this.addOnAppService.getServiceByName(serviceName).subscribe(sku => {
          this.descriptionParams = {
            serviceName : sku.description
          };
        });
      }
    });
    this.skuName = serviceName;
  }

  verifySmartSenseId() {
    this.verificationInProgress = true;
    this.addOnAppService.verify(this.smartSenseId).subscribe(response => {
      this.verificationInProgress = false;
      if (response.isValid) {
        this.verified = true;
      }
      this.isInvalid = !response.isValid;
    }, (error) => {
      this.verificationInProgress = false;
      this.isInvalid = false;
    });
  }

  next() {
    this.addOnAppService.enableService({smartSenseId: this.smartSenseId, skuName: this.skuName} as ConfigPayload).subscribe(() => {
      this.router.navigate(['/infra', 'services']).then(() => {
        this.addOnAppService.serviceEnabled.next(this.descriptionParams.serviceName);
      });
    }, (error) => {
      this.showError = true;
      this.errorMessage = this.translateService.instant('pages.services.description.enableError');
      console.log(error);
    });
  }

}
