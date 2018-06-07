
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

import { SignInPage } from '../core/sign-in.po';
import { helper } from '../utils/helpers';
import { DatasetDashboardPage } from './po/dssDashboard.po';
import { AddCollectionPage } from './po/addCollection.po';
import {browser} from 'protractor';


describe('DSS Add Collection Page', function() {
	let dashBoardpage: DatasetDashboardPage;
	let addColPage: AddCollectionPage;
	beforeAll(async () => {
		const authPage = await SignInPage.get();
		await authPage.justSignIn();
		dashBoardpage = await DatasetDashboardPage.navigate();
    });

    it('Collection creation without name or description or datalake must fail', async () => {
    	await dashBoardpage.naviagateToCreateCollection();
    	addColPage = await AddCollectionPage.get();
    	await addColPage.nextOnInfoStage();
    	expect(addColPage.bNextOnInfo.isDisplayed()).toBeTruthy();
    	await addColPage.fillRandomName();
    	await addColPage.nextOnInfoStage();
    	expect(addColPage.bNextOnInfo.isDisplayed()).toBeTruthy();
    	await addColPage.fillRandomDescription();
    	await addColPage.nextOnInfoStage();
    	expect(addColPage.bNextOnInfo.isDisplayed()).toBeTruthy();
    	await addColPage.selectFirstLakeOption();
    	// await browser.sleep(5000);
    	await addColPage.nextOnInfoStage();
    	expect(addColPage.bAddAsset.isDisplayed()).toBeTruthy();
    })

    it('Collection creation add asset, Asvanced search and Basic search', async () => {
    	await addColPage.invokeAddAsset();
    	await addColPage.loadAdvSearch();
    	await addColPage.showFirst100Results()
    	let name = await addColPage.getFirstSelectableName();
    	await addColPage.loadBasicSearch();
    	await addColPage.sendKeysToBasicSearch(name);
    	await addColPage.bSearchOnPopup.click();
    	expect(addColPage.getFirstSelectableName()).toEqual(name);
    	await addColPage.doneAssetSelection();
    	expect(addColPage.bNextOnAssetHolder.isDisplayed()).toBeTruthy();
    })

    it('Collection creation save', async () => {
    	await addColPage.doneAssetHOlder();
    	await addColPage.saveAssetCollection();
    	await dashBoardpage.letPageLoad(5000);
    	expect(dashBoardpage.bCreateCollection.isDisplayed()).toBeTruthy();
    })

    it('Validate newly created collection', async () => {
    	await dashBoardpage.searchCollectionByName(addColPage.collName);
    	await dashBoardpage.letCollectionListLoad();
    	expect(dashBoardpage.getFirstCollectionName()).toEqual(addColPage.collName);
    })

    it('Collection creation with duplicate name must fail', async () => {
    	await dashBoardpage.naviagateToCreateCollection();
    	await addColPage.fillName(addColPage.collName);
    	expect(addColPage.bNextOnInfo.getAttribute('disabled')).toBe(true);
    })


    afterAll(helper.cleanup);
})
