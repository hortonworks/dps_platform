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


import {$, browser} from "protractor";
import {lakeList} from "../../data";
import {helper} from "../../utils/helpers";

export class LakeListPage {
  public ambariLinkSeString = "lake_list__goTo_"+lakeList.ambariUrl1;
  public dropDownEnabledSeString = "lake_list__dropDownEn_"+lakeList.ambariUrl1;
  public refreshSeString = "lake_list__refresh_"+lakeList.ambariUrl1;
  public editSeString = "lake_list__edit_"+lakeList.ambariUrl1;
  public removeSeString = "lake_list__remove_"+lakeList.ambariUrl1;
  public DROP_DOWN_ENABLED_TIME_MS = 25000; //wait for given time for drop down to get enabled

  public bDropDownEn = $(`[data-se="${this.dropDownEnabledSeString}"]`);
  public tEditLabel = $(`[data-se="cluster_edit__editLabel"]`);
  public lAmbariLink = $(`[data-se="${this.ambariLinkSeString}"]`);
  public cRefresh = $(`[data-se="${this.refreshSeString}"]`);
  public cRemove = $(`[data-se="${this.removeSeString}"]`);
  public cEdit = $(`[data-se="${this.editSeString}"]`);
  public bCancelButton = $('[data-se="common_util__cancelButton"]');
  public bDeleteButton = $('[data-se="common_util__warningDeleteButton"]');

  static async get() {
    const page = await new LakeListPage();
    await helper.safeGet('/infra/clusters');
    return page;
  }

  async clickDropDown() {
    await browser.driver.sleep(1000);
    await helper.waitForElement(this.bDropDownEn,this.DROP_DOWN_ENABLED_TIME_MS);
    await this.bDropDownEn.click();
    await browser.driver.sleep(1000);
  }

}
