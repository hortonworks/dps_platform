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

import {ServiceEnablementPage} from './service-enablement.po';
import {browser} from 'protractor';
import {helper} from '../../utils/helpers';
import {ServiceVerificationPage} from './service-verification.po';
import {SignInPage} from '../../core/sign-in.po';
import {IdentityComponent} from '../../components/identity.po';
describe('service enablement page', function () {

  let page: ServiceEnablementPage;
  let loginPage: SignInPage;
  let identityComponent: IdentityComponent;

  beforeAll(async () => {
    loginPage = await SignInPage.get();
    await loginPage.justSignIn();

    identityComponent = new IdentityComponent();
  });

  beforeEach(async () => {
    page = new ServiceEnablementPage();
    await page.getEnablementPage();
  });

  it('On Hover of an available service should show Enable button', async () => {
    await helper.waitForElement(page.cAvailableServices.first());
    browser.actions().mouseMove(page.cAvailableServices.first()).perform();
    await helper.waitForElement(page.getActionButton(page.cAvailableServices.first()));
    expect(await page.getActionButton(page.cAvailableServices.first()).isDisplayed()).toBeTruthy();
  });

  it('Click of enable button should go to verification page', async () => {
    await helper.waitForElement(page.cAvailableServices.first());
    browser.actions().mouseMove(page.cAvailableServices.first()).perform();
    await helper.waitForElement(page.getActionButton(page.cAvailableServices.first()));
    page.getActionButton(page.cAvailableServices.first()).click();
    let verificationPage = new ServiceVerificationPage();
    await helper.waitForElement(verificationPage.iSmartSenseInput);
    expect(await verificationPage.iSmartSenseInput.isDisplayed()).toBeTruthy();
  });

  afterAll(helper.cleanup);

});
