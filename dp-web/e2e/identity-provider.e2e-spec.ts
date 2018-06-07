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
import { WelcomePage } from './onboard/welcome.po';
import { IdentityProviderPage } from './onboard/identity-provider.po';
import { helper } from './utils/helpers';
import { ldapConfig } from './data';

describe('identity-provider page', function() {
  let page: IdentityProviderPage;

  beforeAll(async () => {
    const signIn = await SignInPage.get();
    await signIn.justSignIn();

    const welcomePage = await WelcomePage.get();
    await welcomePage.bStart.click();

    page = await IdentityProviderPage.get();
  });

  it('should show up with password mode set to masked', async () => {
    await helper.waitForElement(page.fAdminPasswordMasked);

    expect(await page.fAdminPasswordMasked.isDisplayed()).toBeTruthy();
    expect(await page.fAdminPasswordPlain.isPresent()).toBeFalsy();
  });

  it('should change password mode to plain / unmasked on click of toggle', async () => {
    await helper.waitForElement(page.fAdminPasswordToggle);
    await page.fAdminPasswordToggle.click();

    expect(await page.fAdminPasswordPlain.isDisplayed()).toBeTruthy();
    expect(await page.fAdminPasswordMasked.isPresent()).toBeFalsy();
  });

  it('should change password mode back to masked on click of toggle', async () => {
    await helper.waitForElement(page.fAdminPasswordToggle);
    await page.fAdminPasswordToggle.click();

    expect(await page.fAdminPasswordMasked.isDisplayed()).toBeTruthy();
    expect(await page.fAdminPasswordPlain.isPresent()).toBeFalsy();
  });

  it('should display error message for invalid ldap uri', async () => {
    await helper.waitForElement(page.cForm);

    await page.fURI.sendKeys('ldap://some-unreachable-ldap-server.test');

    await page.fUserSearchBase.sendKeys(ldapConfig.user_search_base);
    await page.fUserSearchAttr.sendKeys(ldapConfig.user_search_attr);
    await page.fGroupSearchBase.sendKeys(ldapConfig.group_search_base);
    await page.fGroupSearchAttr.sendKeys(ldapConfig.group_search_attr);
    await page.fGroupObjectClass.sendKeys(ldapConfig.group_object_class);
    await page.fGroupMemberAttr.sendKeys(ldapConfig.group_member_attr);

    await page.fAdminBindDN.sendKeys(ldapConfig.admin_bind_dn);
    await page.fAdminPasswordMasked.sendKeys(ldapConfig.admin_password);

    await page.bSave.click();

    await helper.waitForElement(page.cNotifications);
    expect(await page.cNotifications.isDisplayed()).toBeTruthy();
  });

  it('should display error message for invalid password', async () => {
    await helper.waitForElement(page.cForm);

    await page.clearForm();

    await page.fURI.sendKeys(ldapConfig.uri);

    await page.fUserSearchBase.sendKeys(ldapConfig.user_search_base);
    await page.fUserSearchAttr.sendKeys(ldapConfig.user_search_attr);
    await page.fGroupSearchBase.sendKeys(ldapConfig.group_search_base);
    await page.fGroupSearchAttr.sendKeys(ldapConfig.group_search_attr);
    await page.fGroupObjectClass.sendKeys(ldapConfig.group_object_class);
    await page.fGroupMemberAttr.sendKeys(ldapConfig.group_member_attr);

    await page.fAdminBindDN.sendKeys(ldapConfig.admin_bind_dn);
    await page.fAdminPasswordMasked.sendKeys('wrong-password');

    await page.bSave.click();

    await helper.waitForElement(page.cNotifications);
    expect(await page.cNotifications.isDisplayed()).toBeTruthy();
  });

  it('should successfully save ldap config and navigate to access management', async () => {
    await helper.waitForElement(page.cForm);

    await page.clearForm();

    await page.fURI.sendKeys(ldapConfig.uri);

    await page.fUserSearchBase.sendKeys(ldapConfig.user_search_base);
    await page.fUserSearchAttr.sendKeys(ldapConfig.user_search_attr);
    await page.fGroupSearchBase.sendKeys(ldapConfig.group_search_base);
    await page.fGroupSearchAttr.sendKeys(ldapConfig.group_search_attr);
    await page.fGroupObjectClass.sendKeys(ldapConfig.group_object_class);
    await page.fGroupMemberAttr.sendKeys(ldapConfig.group_member_attr);

    await page.fAdminBindDN.sendKeys(ldapConfig.admin_bind_dn);
    await page.fAdminPasswordMasked.sendKeys(ldapConfig.admin_password);

    await page.bSave.click();

    await helper.waitForElement(page.cNotifications);
    expect(await page.cNotifications.isPresent()).toBeFalsy();
  });

  afterAll(helper.cleanup);
});
