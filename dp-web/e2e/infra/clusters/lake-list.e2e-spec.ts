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

import { helper } from '../../utils/helpers';
import {LakeListPage} from "./lake-list.po";
import {browser} from "protractor";
import {SignInPage} from "../../core/sign-in.po";
import {ClusterAddPage} from "./cluster-add.po";
import {clusterAddData, lakeList} from "../../data";
import {protractor} from "protractor/built/ptor";


describe('lake-list page\n', function() {
  let page: LakeListPage;
  let loginPage: SignInPage;
  let clusterAddPage: ClusterAddPage;
  let RETRIEVE_CLUSTER_TIMEOUT_MS=25000; //increase it if test cases timeout
  let WAIT_TIME_MS=3000;

  beforeAll(async() => {
    await browser.sleep(1000); //Increase it if knox opens
    loginPage = await SignInPage.get();
    await loginPage.justSignIn();
    await browser.sleep(1000); // its needed for now. else knox page opens and that throws error. Increase it if knox opens
    clusterAddPage = new ClusterAddPage();
    await clusterAddPage.get();
    await clusterAddPage.inputAmbariUrl(lakeList.ambariUrl1); //make sure this IP is valid and NOT already added
    await clusterAddPage.clickGo();
    await helper.waitForElement(clusterAddPage.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

    await clusterAddPage.fillClusterLocation(clusterAddData.locationPre);
    await clusterAddPage.fillDataCenter(clusterAddData.dataCenter1);
    await clusterAddPage.fillTags(clusterAddData.tagSingle);
    await clusterAddPage.fillDescription(clusterAddData.description);
    await clusterAddPage.addCluster();
  });

  beforeEach(async () => {
    page = await LakeListPage.get();
  });

  it('should go to cluster edit page', async () => {
    await page.clickDropDown();
    await helper.waitForElement(page.cEdit,WAIT_TIME_MS);
    await page.cEdit.click();

    await helper.waitForElement(page.tEditLabel);
    expect(await page.tEditLabel.isDisplayed()).toBeTruthy();
  });

  it('ambari link should be present', async () => {
    await page.clickDropDown();
    await helper.waitForElement(page.lAmbariLink,WAIT_TIME_MS);

    await page.lAmbariLink.getAttribute('href').then(await function (linkText) {
      expect(linkText).toContain(":8080");
    })
  });

  // it('uptime should change and new (absolute) uptime value should be larger', async () => { // this test case will fail if DP fails to fetch health info of cluster.
  //   await page.clickDropDown();
  //
  //   let uptimeMs: number;
  //   await helper.waitForElement(page.bDropDownEn);
  //   await page.bDropDownEn.getAttribute('data-se-value').then(await function(upTimeText){
  //     uptimeMs = Math.abs(Number(upTimeText));
  //   });
  //
  //   await helper.waitForElement(page.cRefresh,WAIT_TIME_MS);
  //   await page.cRefresh.click();
  //
  //   let refreshedUptimeMs: number;
  //   let EC = protractor.ExpectedConditions;
  //
  //   try{
  //     await browser.wait(EC.invisibilityOf(page.bDropDownEn),RETRIEVE_CLUSTER_TIMEOUT_MS);
  //     await helper.waitForElement(page.bDropDownEn,WAIT_TIME_MS);
  //     await browser.wait(async function() {
  //       const upTimeText = await page.bDropDownEn.getAttribute('data-se-value');
  //       return uptimeMs < Math.abs(Number(upTimeText));
  //     }, RETRIEVE_CLUSTER_TIMEOUT_MS);
  //     const upTimeText = await page.bDropDownEn.getAttribute('data-se-value');
  //     refreshedUptimeMs = Math.abs(Number(upTimeText));
  //     expect(uptimeMs < refreshedUptimeMs).toBeTruthy();
  //
  //   } catch(e){
  //     const upTimeText = await page.bDropDownEn.getAttribute('data-se-value')
  //     refreshedUptimeMs = Math.abs(Number(upTimeText));
  //     expect(uptimeMs < refreshedUptimeMs).toBeTruthy();
  //   }
  // });

  it('delete confirmation box should appear', async () => {
    await page.clickDropDown();
    await helper.waitForElement(page.cRemove,WAIT_TIME_MS);
    await page.cRemove.click();

    await helper.waitForElement(page.bCancelButton,WAIT_TIME_MS);
    expect(await page.bCancelButton.isDisplayed()).toBeTruthy();
  });

  it('cancel button should abort the process of deletion', async () => {
    await page.clickDropDown();
    await helper.waitForElement(page.cRemove,WAIT_TIME_MS);
    await page.cRemove.click();

    await helper.waitForElement(page.bCancelButton,WAIT_TIME_MS);
    await page.bCancelButton.click();
    await helper.waitForElement(page.bDropDownEn,WAIT_TIME_MS);
    expect(await page.bDropDownEn.isDisplayed()).toBeTruthy();
  });

  it('delete button should delete the respective cluster', async () => {
    await page.clickDropDown();
    await helper.waitForElement(page.cRemove,WAIT_TIME_MS);
    await page.cRemove.click();

    await helper.waitForElement(page.bDeleteButton,WAIT_TIME_MS);
    await page.bDeleteButton.click();
    var EC = protractor.ExpectedConditions;
    expect(EC.invisibilityOf(page.bDropDownEn)).toBeTruthy();
  });

  afterAll(async() => {
    helper.cleanup;
  })
});
