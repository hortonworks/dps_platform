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

import { $, $$ } from 'protractor';
import { helper } from '../../utils/helpers';
import {browser} from 'protractor';

export class DatasetDashboardPage {
    bModeList = $('[data-se="collection_dashboard__mode_list"]');
    bModeTile = $('[data-se="collection_dashboard__mode_tile"]');

    fCollectionFilter = $('[data-se="collection_dashboard__filter"]');

    bCreateCollection = $('[data-se="collection_dashboard__create"]');

    fTagsFilter = $('[data-se="collection_tags__filter"]');

    cCollections = $$('[data-se-group=]');
    lCollectionNames = $$('[data-se="dashboard_collection_list_itm_name"]');
    lCollectionActions = $$('[data-se="dashboard_collection_actions"]');

    bDeleteCollection = $('[data-se="dashboard_collection_action_delete"]');

    sDeleteConfirmCollecName = $('[data-se="delete_collection_confirmation_msg"] span');
    bDeleteConfirmCancle = $('[data-se="delete_collection_confirmation_cancel"]');
    bDeleteCollConfirm = $('[data-se="delete_collection_confirm"]');

    static async navigate() {
    	await helper.safeGet('/datasteward/collections');
    	return DatasetDashboardPage.get();
    }

    async letPageLoad(waitTime:number) {
    	await helper.waitForElement(this.bCreateCollection, waitTime);
    }

    static async get() {
    	await helper.waitForElement($('[data-se="collection_dashboard__create"]'));
    	return new DatasetDashboardPage();
  	}

    async naviagateToCreateCollection() {
      await helper.waitForElement(this.bCreateCollection);
      await this.bCreateCollection.click();
    }

    async searchCollectionByName(name) {
		await this.fCollectionFilter.sendKeys(name);
    }
    async letCollectionListLoad () {
    	await helper.waitForElements(this.lCollectionNames, 5000);
    }
    async getFirstCollectionName() {
    	return (await this.lCollectionNames.first()).getText();
    }
    async clickFirstCollectionDelete() {
    	let elm = await this.lCollectionActions.first()
    	await browser.sleep(1000);
    	browser.executeScript("arguments[0].scrollIntoView();", elm.getWebElement());
    	await browser.sleep(1000);
    	await (elm).click();
    	await helper.waitForElement(this.bDeleteCollection);
    	await this.bDeleteCollection.click();
    }
    async getDeleteConfirmCollectionName() {
    	await helper.waitForElement(this.sDeleteConfirmCollecName);
    	return this.sDeleteConfirmCollecName.getText();
    }
    async cancleDeleteConfirmation() {
      await helper.waitForElement(this.bDeleteConfirmCancle);
      await this.bDeleteConfirmCancle.click();
    }
    async confirmDeleteConfirmation() {
      await helper.waitForElement(this.bDeleteCollConfirm);
      await this.bDeleteCollConfirm.click();
    }
}
