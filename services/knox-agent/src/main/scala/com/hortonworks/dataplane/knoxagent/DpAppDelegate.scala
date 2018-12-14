/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
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
