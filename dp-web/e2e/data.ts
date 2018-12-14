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

export const credential = {
  username: 'admin',
  password: 'admin'
};

export const knoxCredential = {
  username: 'admin',
  password: 'admin-password'
};

export const db = {
  path: '../services/db-service/db',
  cmd: 'flyway clean migrate'
};

export const clusterAddData = {
  inValidAmbariUrl: "http:////12.113",
  ambariUrl1: "http://172.27.32.8:8080",  //make sure this IP is valid and NOT already added
  ambariUrl2: "http://172.27.18.11:8080", //make sure this IP is valid and NOT already added
  notReachablrAmbari: "http://172.27.32.9:8080",  // make sure this IP is NOT reachable
  ambariUrlProxy: "https://40.118.130.196/ambari", // make sure this is valid ambari address and is not already added. If knox is installed then please follow pre-addition steps of adding public key.
  dataCenter1: "dc999999990",
  dataCenter2: "dc999999991",
  tagSingle: "tag1",
  tagsArray:["tag1","tag2","tag3","tag4"],
  locationPre: "cala",
  description: "desc1",
  addSuccessUrl: "/infra/clusters",
  inValidAmbariMsg: "Invalid Ambari URL. Please enter a valid URL of the form http[s]://hostname[:port][/path].",
  ambariNetworkError: "There was a network error when connecting to Ambari.",
  fillAllMandatoryFieldsMsg: "Please fill in mandatory fields marked with '*'",
  addSuccessMsg: "Your cluster has been added to DataPlane.",
  alreadyExistsmsg: "Cluster already exists in DataPlane"
};

export const lakeList = {
  ambariUrl1: "http://172.27.18.11:8080",  //this should be IP (not host name) and make sure this IP is valid
  ambariUrl2: "http://172.27.32.8:8080" //this should be IP (not host name) and make sure this IP is valid
};

export const ldapConfig = {
  uri: 'ldap://knox:33389',

  admin_bind_dn: 'uid=admin,ou=people,dc=hadoop,dc=apache,dc=org',
  admin_password: 'admin-password',

  user_search_base: 'ou=people,dc=hadoop,dc=apache,dc=org',
  user_search_attr: 'uid',
  group_search_base: 'ou=groups,dc=hadoop,dc=apache,dc=org',
  group_search_attr: 'cn',
  group_object_class: 'groupofnames',
  group_member_attr: 'member',
};
