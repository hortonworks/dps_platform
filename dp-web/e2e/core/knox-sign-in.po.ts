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

import { browser, $ } from 'protractor';
import { helper } from '../utils/helpers';
import { knoxCredential } from '../data';

export class KnoxSignInPage {
    public fUsername = $('[data-se="knox__username"]');
    public fPassword = $('[data-se="knox__password"]');
    public bSubmit = $('[data-se="knox__sign_in"]');
    public tErrorMessage = $('[data-se="knox__error"]');
    public bIdentityMenu = $('[data-se="identity__menu"]');

    private constructor() {}

    static async get() {
      const page = new KnoxSignInPage();

      await helper.safeGet('/');
      await helper.waitForElement($('[data-se="knox__username"]'));

      return page;
    }

    async doSetCredential(username: string, password: string) {
      await helper.waitForElement(this.fUsername);
      this.fUsername.sendKeys(username);

      await helper.waitForElement(this.fPassword);
      this.fPassword.sendKeys(password);
    }

    async doSubmitAndSignIn() {
      await helper.waitForElement(this.bSubmit);
      await this.bSubmit.click();
    }

    async doGetErrorMessage() {
      await helper.waitForElement(this.tErrorMessage);
      return this.tErrorMessage.getText();
    }

    async justSignIn() {
      await this.doSetCredential(knoxCredential.username, knoxCredential.password);
      await this.doSubmitAndSignIn();
      await helper.waitForElement(this.bIdentityMenu);
    }
}
