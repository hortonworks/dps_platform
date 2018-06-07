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
import {ClusterAddPage} from "./cluster-add.po";
import {browser} from "protractor";
import {SignInPage} from "../../core/sign-in.po";
import {clusterAddData} from "../../data";

describe('cluster-add page', function() {
    let page: ClusterAddPage;
    let loginPage: SignInPage;
    let RETRIEVE_CLUSTER_TIMEOUT_MS=6000; //increase it if test cases timeout

    beforeAll(async() => {
      await browser.sleep(1000); //Increase it if knox opens
      loginPage = await SignInPage.get();
      loginPage.justSignIn();
      await browser.sleep(1000); // its needed for now. else knox page opens and that throws error. Increase it if knox opens
    })

    beforeEach(async () => {
        page = new ClusterAddPage();
        await page.get();
    });

    it('should display error message for invalid ambari url', async () => {
        await page.inputAmbariUrl(clusterAddData.inValidAmbariUrl);
        await page.clickGo();

        await helper.waitForElement(page.tErrorMessage);
        await helper.expectEqualText(page.tErrorMessage,clusterAddData.inValidAmbariMsg);
    });

    it('should be valid and activate clear button', async () => {
        await page.inputAmbariUrl(clusterAddData.ambariUrl1); //make sure this IP is valid and NOT already added
        await page.clickGo();

        await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);
        expect(await page.bClearButton.isDisplayed()).toBeTruthy();
    });

    it('Test for proxy url: should be valid ambari url', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrlProxy); //make sure this ambari address is valid and NOT already added
      await page.clickGo();

      expect(await page.tErrorMessage.isPresent()).toBeFalsy();
    });

    it('should give add error (network error)', async () => {
      await page.inputAmbariUrl(clusterAddData.notReachablrAmbari); // make sure this IP is NOT reachable
      await page.clickGo();

      await helper.waitForElement(page.tAddErrorMessage,RETRIEVE_CLUSTER_TIMEOUT_MS);
      await helper.expectEqualText(page.tAddErrorMessage,clusterAddData.ambariNetworkError);
    });

    it('should give add error (please fill all mandatory field) as cluster location is not filled', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl1); //make sure this IP is valid and NOT already added
      await page.clickGo();
      await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

      await page.fillDataCenter(clusterAddData.dataCenter1);
      await page.addCluster();

      await helper.waitForElement(page.tAddErrorMessage);
      await helper.expectEqualText(page.tAddErrorMessage,clusterAddData.fillAllMandatoryFieldsMsg);
    });

    it('should give add error (please fill all mandatory field) as datacenter is not filled', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl1); //make sure this IP is valid and NOT already added
      await page.clickGo();
      await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

      await page.fillClusterLocation(clusterAddData.locationPre);
      await page.addCluster();

      await helper.waitForElement(page.tAddErrorMessage);
      await helper.expectEqualText(page.tAddErrorMessage,clusterAddData.fillAllMandatoryFieldsMsg);
    });

    it('Test for clear button: Clear button should clear ambari Url and Go button should appear again', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl1); //make sure this IP is valid and NOT already added
      await page.clickGo();
      await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

      await page.clickClear();

      await helper.waitForElement(page.bGoButton);
      expect(await page.bGoButton.isDisplayed()).toBeTruthy();
    });

    it('Test for Cancle button: on clicking cancel button , Lake list page should open', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl1); //make sure this IP is valid and NOT already added
      await page.clickGo();
      await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

      await page.clickCancel();

      await helper.waitForElement(page.bAddButton);
      expect(await page.bAddButton.isDisplayed()).toBeTruthy();
    });

    it('should add the cluster to DP and should go to cluster list page', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl1); //make sure this IP is valid and NOT already added
      await page.clickGo();
      await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

      await page.fillClusterLocation(clusterAddData.locationPre);
      await page.fillDataCenter(clusterAddData.dataCenter1);
      await page.fillTags(clusterAddData.tagSingle);
      await page.fillDescription(clusterAddData.description);
      await page.addCluster();

      // await helper.waitForUrl(clusterAddData.addSuccessUrl);
      // expect(await helper.urlChanged(clusterAddData.addSuccessUrl)).toBeTruthy();
      await helper.waitForElement(page.bAddButton);
      expect(await page.bAddButton.isDisplayed()).toBeTruthy();
    });

    it('should add the cluster to DP and stay at cluster add page with message "cluster added successfully"', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl2); //make sure this IP is valid and NOT already added
      await page.clickGo();
      await helper.waitForElement(page.bClearButton,RETRIEVE_CLUSTER_TIMEOUT_MS);

      await page.fillClusterLocation(clusterAddData.locationPre);
      await page.fillDataCenter(clusterAddData.dataCenter2);
      await page.fillTags(clusterAddData.tagSingle);
      await page.fillDescription(clusterAddData.description);
      await page.addAndNewCluster();

      await helper.waitForElement(page.tAddSuccessMessage);
      await helper.expectEqualText(page.tAddSuccessMessage,clusterAddData.addSuccessMsg);
    });

    it('should display error message that cluster already exists', async () => {
      await page.inputAmbariUrl(clusterAddData.ambariUrl2);
      await page.clickGo();

      await helper.waitForElement(page.tErrorMessage,RETRIEVE_CLUSTER_TIMEOUT_MS);
      await helper.expectEqualText(page.tErrorMessage,clusterAddData.alreadyExistsmsg);
    });

    afterAll(helper.cleanup);
});
