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

name := """dataplane"""

Common.settings

lazy val dpCommons = (project in file("dp-commons"))

lazy val consul = (project in file("services/dp-consular"))

lazy val dbClient = (project in file("clients/db-client")).
  dependsOn(dpCommons)

lazy val csClient = (project in file("clients/cs-client")).
  dependsOn(dpCommons)

lazy val gatewayClient = (project in file("clients/knox-gateway-client")).
  dependsOn(dpCommons)

lazy val restMock = (project in file("services/rest-mock"))

lazy val dbService = (project in file("services/db-service")).enablePlugins(PlayScala).
  dependsOn(dpCommons,consul)

lazy val dpApp = (project in file("dp-app")).enablePlugins(PlayScala).
  dependsOn(dbClient, csClient, consul)

lazy val clusterService = (project in file("services/cluster-service")).
  dependsOn(restMock, dpCommons,dbClient, csClient,gatewayClient,consul)

lazy val knoxAgent = (project in file("services/knox-agent")).
  dependsOn(consul)

lazy val bcrypter = (project in file("tools/bcrypter"))
