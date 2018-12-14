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

import { browser } from 'protractor';

import { SignInPage } from './core/sign-in.po';
import { IdentityComponent } from './components/identity.po';
import { helper } from './utils/helpers';

describe('identity component', function() {
    let component: IdentityComponent;

    beforeEach(async () => {
      // login
      const page = await SignInPage.get();
      page.justSignIn();

      component = new IdentityComponent();
    });

    it('should display the right username in header after login', async () => {
      await helper.waitForElement(component.bIdentityMenu);
      await component.bIdentityMenu.click();

      await helper.waitForElement(component.tDisplayName);
      const displayName = await component.tDisplayName.getText();

      expect(displayName).toEqual('Super Administrator');
    });

    it('should logout on click of logout', async () => {
      await helper.waitForElement(component.bIdentityMenu);
      await component.bIdentityMenu.click();

      await helper.waitForElement(component.bSignOut);
      await component.bSignOut.click();

      const cookies = await browser.manage().getCookies();
      expect(cookies.length).toEqual(0);
    });

    afterEach(helper.cleanup);
});
