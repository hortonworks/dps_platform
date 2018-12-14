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

// Protractor configuration file, see link for more information
// https://github.com/angular/protractor/blob/master/lib/config.ts

/*global jasmine */
const SpecReporter = require('jasmine-spec-reporter');
const shell = require('shelljs');

exports.config = {
  allScriptsTimeout: 11000,
  specs: [
    './e2e/sign-in.e2e-spec.ts',
    './e2e/onboard-welcome.e2e-spec.ts',
    './e2e/identity-provider.e2e-spec.ts',
    // './e2e/users-and-groups.e2e-spec.ts',
    './e2e/knox-sign-in.e2e-spec.ts',
    './e2e/onboard-welcome-after-idp.e2e-spec.ts',
    './e2e/identity.e2e-spec.ts',
    './e2e/infra/user-mgmt/users-list.e2e-spec.ts',
    './e2e/infra/user-mgmt/groups-list.e2e-spec.ts',
    './e2e/infra/clusters/cluster-add.e2e-spec.ts',
    './e2e/infra/services/service-enablement.e2e-spec.ts',
    './e2e/infra/services/service-verification.e2e-spec.ts',
    './e2e/infra/clusters/lake-list.e2e-spec.ts',
    './e2e/dss/dashboard-spec.ts',
    './e2e/dss/createCollection-spec.ts',
    './e2e/dss/dashboard-with-collections-spec.ts',
  ],
  multiCapabilities: [{
  //   'browserName': 'firefox',
  //   'moz:firefoxOptions': {
  //     'args': [
  //       '--headless',
  //     ],
  //   },
  // }, {
    'browserName': 'chrome',
    'chromeOptions': {
      'args': [
        // '--headless',
        '--disable-extensions',
        '--disable-web-security',
        '--disk-cache-size=1',
        '--media-cache-size=1',
        '--start-maximized',
        '--disable-gpu',
      ],
    },
  }],
  directConnect: true,
  baseUrl: 'http://localhost:4200',
  // baseUrl: 'https://dataplane',
  framework: 'jasmine',
  jasmineNodeOpts: {
    showColors: true,
    defaultTimeoutInterval: 30000,
    print: function() {},
  },
  useAllAngular2AppRoots: true,
  rootElement: 'data-plane',
  beforeLaunch: function() {
    require('ts-node').register({
      project: 'e2e'
    });
  },

  onPrepare: function () {
    jasmine.getEnv().addReporter(new SpecReporter({ displayStacktrace: 'specs' }));

    // cleanup db
    shell.pushd('../services/db-service/db');
    shell.exec('flyway clean migrate');
    shell.popd();
  },
	// Turn off control flow (https://github.com/angular/protractor/tree/master/exampleTypescript/asyncAwait)
  SELENIUM_PROMISE_MANAGER: false,
};
