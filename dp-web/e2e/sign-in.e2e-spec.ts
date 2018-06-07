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

import { SignInPage } from './core/sign-in.po';
import { helper } from './utils/helpers';
import { credential } from  './data';

describe('sign-in page', function() {
    let page: SignInPage;

    beforeEach(async () => {
      page = await SignInPage.get();
    });

    it('should display error message for invalid credentials', async () => {
        await page.doSetCredential('admin', 'wrong-password');
        await page.doSubmitAndSignIn();

        await helper.waitForElement(page.tErrorMessage);
        expect(await page.tErrorMessage.isDisplayed()).toBeTruthy();
    });

    it('should login for valid credentials', async () => {
        await page.doSetCredential(credential.username, credential.password);
        await page.doSubmitAndSignIn();

        await helper.waitForElement(page.bIdentityMenu);
        expect(await page.bIdentityMenu.isDisplayed()).toBeTruthy();
    });

    it('should allow users to login after a rejected login without refresh', async () => {
      await page.doSetCredential('admin', 'wrong-password');
      await page.doSubmitAndSignIn();

      await helper.waitForElement(page.tErrorMessage);

      await page.fUsername.clear();
      await page.fPassword.clear();

      await page.doSetCredential(credential.username, credential.password);
      await page.doSubmitAndSignIn();

      await helper.waitForElement(page.bIdentityMenu);
      expect(await page.bIdentityMenu.isDisplayed()).toBeTruthy();
    });

    afterEach(helper.cleanup);
});
