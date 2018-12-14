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

import {browser, $$, ElementFinder, ElementArrayFinder} from 'protractor';
import * as shell from 'shelljs';
import { db } from '../data';

const TIMEOUT = 1000;
const DEFAULT_WIDTH = 1280;
const DEFAULT_HEIGHT = 1024;

async function safeGet(url: string) {
  browser.ignoreSynchronization = true;
  await browser.get(url);
  await maximizeWindow();
};

async function  maximizeWindow(width: number = DEFAULT_WIDTH, height: number = DEFAULT_HEIGHT) {
  await browser.driver.manage().window().setSize(width, height);
  // does not work on mac os
  // await browser.driver.manage().window().maximize();
}

async function waitForElement(element: ElementFinder, timeout: number = TIMEOUT) {
	return browser.wait(async function () {
    const isPresent = await element.isPresent()
    if (isPresent) {
      return element.isDisplayed();
    }
    else {
      return false;
    }
	}, timeout, `Wait for ${element.locator()} timed out.`);
}

async function waitForElements(elm: ElementArrayFinder, timeout: number = TIMEOUT) {
  return browser.wait(async function () {
    const isPresent = await elm.isPresent()
    if (isPresent) {
      // let dispElmPresent = null;
      // elm.each((element, indx)=>{
      //   if (dispElmPresent) return;
      //   dispElmPresent = element.isDisplayed();
      //   console.log("######",dispElmPresent, '#####');
      // })
      // return (dispElmPresent)?true:false;
      return true;
    }
    else {
      return false;
    }
  }, timeout, `Wait for ${elm.locator()} timed out.`);
}

async function waitForUrl(url: string, timeout: number = TIMEOUT){
  return browser.wait(async function () {
    return browser.getCurrentUrl().then(function (currentUrl) {
      return currentUrl.endsWith(url);
    })
  }, timeout);
}

async function urlChanged(url: string){
  return browser.getCurrentUrl().then(function (currentUrl) {
    return currentUrl.endsWith(url);
  })
}

async function expectEqualText(element: ElementFinder, targetText:string, loggoingMode:boolean = false){
  await element.getText().then(await function (text) {
    (loggoingMode && console.log(refinedText(text)));
    expect(text).toEqual(targetText);
  })
}

function refinedText(text:string){
  if(!(text.trim())){
    return "Empty text";
  }
  return text;
}

async function cleanup() {
    // browser.executeScript('window.sessionStorage.clear();');
    // browser.executeScript('window.localStorage.clear();');
    browser.manage().deleteAllCookies();
}

async function dbReset() {
  shell.pushd(db.path);
  shell.exec(db.cmd);
  shell.popd();
}

async function suspend() {
  await new Promise((resolve, reject) => {});
}

export const helper = {
  safeGet,
  maximizeWindow,
  waitForElement,
  waitForElements,
  cleanup,
  dbReset,
  suspend,
  waitForUrl,
  urlChanged,
  expectEqualText
};
