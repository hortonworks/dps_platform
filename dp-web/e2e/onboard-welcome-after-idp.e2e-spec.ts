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

import { KnoxSignInPage } from './core/knox-sign-in.po';
import { WelcomePage } from './onboard/welcome-after-idp.po';
import { ClusterAddPage } from './infra/clusters/cluster-add.po';
import { helper } from './utils/helpers';

describe('welcome page', function() {
    let page: WelcomePage;

    beforeAll(async () => {
      const authPage = await KnoxSignInPage.get();
      authPage.justSignIn();

      page = new WelcomePage();
    });

    it('should state cluster config as one of next steps', async () => {
        await helper.waitForElement(page.cBlockClusters);

        expect(await page.cBlockClusters.isDisplayed()).toBeTruthy();
    });

    it('should state user and group config as one of next steps', async () => {
        await helper.waitForElement(page.cBlockUsers);

        expect(await page.cBlockUsers.isDisplayed()).toBeTruthy();
    });

    it('should state service config as one of next steps', async () => {
        await helper.waitForElement(page.cBlockServices);

        expect(await page.cBlockServices.isDisplayed()).toBeTruthy();
    });

    it('should navigate to identity provider config page on click of start', async () => {
      await helper.waitForElement(page.bStart);
      await page.bStart.click();

      const nextPage = new ClusterAddPage();
      await helper.waitForElement(nextPage.cTitle);
      expect(await nextPage.cTitle.isDisplayed()).toBeTruthy();
    });

    afterAll(helper.cleanup);
});
