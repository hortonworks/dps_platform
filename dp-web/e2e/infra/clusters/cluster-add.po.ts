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

import {browser, $, element, by} from 'protractor';
import { helper } from '../../utils/helpers';

export class ClusterAddPage {
  public url = '';

  public cTitle = $('[data-se="cluster_add__title"]');
  public bAddButton = $('[data-se="cluster_add__addButton"]');
  public bGoButton = $('[data-se="cluster_add__goButton"]');
  public tErrorMessage = $('[data-se="cluster_add__failReasons"]');
  public bClearButton = $('[data-se="cluster_add__clearButton"]');
  public fAmbariUrlInput = $('[data-se="cluster_add__ambariUrlInput"]');
  public tAddErrorMessage = $('[data-se="cluster_add__addError"]');
  public fDataCenter = $('[data-se="cluster_add__dataCenter"]');
  public bAddClusterButton = $('[data-se="cluster_add__addClusterButton"]');
  public fClusterLocation = $('[data-se="cluster_add__location"]');
  public fTags = $('[data-se="common__taggingWidget__tagInput"]');
  public tDescription = $('[data-se="clsuter_add__description"]');
  public bAddAndNewButton = $('[data-se="cluster_add__addAndNewClusterButton"]');
  public tAddSuccessMessage = $('[data-se="cluster_add__addSuccess"]');
  public bCancelButton = $('[data-se="cluster_add__cancelButton"]');

  async get() {
      await helper.safeGet('/infra/clusters/add');
  }

  async inputAmbariUrl(ambariUrl: string){
    await helper.waitForElement(this.fAmbariUrlInput);
    await this.fAmbariUrlInput.sendKeys(ambariUrl);
  }

  async clickGo(){// as this call will take more time, use increased timeout in subsequent waitForElement()
    await helper.waitForElement(this.bGoButton)
    await this.bGoButton.click();
  }

  async addCluster(){
    await  helper.waitForElement(this.bAddClusterButton);
    await this.bAddClusterButton.click();
  }

  async addAndNewCluster(){
    await  helper.waitForElement(this.bAddAndNewButton);
    await this.bAddAndNewButton.click();
  }

  async fillDataCenter(datacenter:string){
    await helper.waitForElement(this.fDataCenter);
    await this.fDataCenter.sendKeys(datacenter);
  }

  async  fillClusterLocation(locationPre: string){
    await helper.waitForElement(this.fClusterLocation);
    await this.fClusterLocation.sendKeys(locationPre);

    await browser.sleep(1000); // may need this.
    await $('.item.selected').click();
  }

  async fillTags(tag:string){ // TBD: pass array of tags
    await  helper.waitForElement(this.fTags);
    await this.fTags.sendKeys(tag);
  }

  async fillDescription(desc:string){
    await helper.waitForElement(this.tDescription);
    await this.tDescription.sendKeys(desc);
  }

  async clickClear(){
    await helper.waitForElement(this.bClearButton);
    await this.bClearButton.click();
  }

  async clickCancel(){
    await helper.waitForElement(this.bCancelButton);
    await this.bCancelButton.click();
  }

}
