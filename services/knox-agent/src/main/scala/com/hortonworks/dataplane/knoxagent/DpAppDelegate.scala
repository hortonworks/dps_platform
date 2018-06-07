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

package com.hortonworks.dataplane.knoxagent

import akka.actor.ActorSystem
import akka.event.Logging
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import Formatters._

class DpAppDelegate(wsClient: WSClient, actorSystem: ActorSystem) {

  private val logger = Logging(actorSystem, "DpAppDelegate")

  def getLdapConfiguration(serviceUrl: String): Future[Option[KnoxConfig]] =
    wsClient
      .url(s"$serviceUrl/service/core/api/knox/configuration")
      .get()
      .map { resp =>
        logger.info("got resp from dpapp")
        resp.json.asOpt[KnoxConfig]
      }

  def getCertificates(serviceUrl: String): Future[Seq[Certificate]] =
    wsClient
    .url(s"$serviceUrl/service/db/certificates?active=true")
    .get()
    .map { response =>
      logger.info(s"got response from db ${response.status}")
      response.status match {
        case 200 => (response.json \ "results").as[Seq[Certificate]]
        case _ => throw new RuntimeException(s"Expected 200. Received ${response.status}: ${response.body}")
      }
    }
}
