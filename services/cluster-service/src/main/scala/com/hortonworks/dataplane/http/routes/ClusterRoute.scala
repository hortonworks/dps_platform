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

package com.hortonworks.dataplane.http.routes

import akka.http.scaladsl.model.{HttpHeader, StatusCodes}
import akka.http.scaladsl.server.Directives.{path, _}
import com.google.inject.Inject
import com.hortonworks.dataplane.commons.domain.Constants
import com.hortonworks.dataplane.cs.ClusterErrors.ClusterNotFound
import com.hortonworks.dataplane.cs._
import com.hortonworks.dataplane.http.BaseRoute
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.Json

import scala.util.{Failure, Success, Try}

class ClusterRoute @Inject()(val clusterDataApi: ClusterDataApi, private val config: Config)
  extends BaseRoute {

  import com.hortonworks.dataplane.commons.domain.Entities._
  import com.hortonworks.dataplane.http.JsonSupport._
  import scala.concurrent.ExecutionContext.Implicits.global

  val logger = Logger(classOf[ClusterRoute])


  val clusterKnoxTokenProxy = path("clusters" / LongNumber / "knox_token") { clusterId =>
    get {
      extractRequest { request =>
        val dpToken = request.getHeader(Constants.DPTOKEN)
        if(!dpToken.isPresent) {
          logger.error("Dataplane token unavailable")
          complete(StatusCodes.BadRequest, errors(400, "cluster.service.bad.request", "Dataplane token unavailable", new Exception("Dataplane token unavailable")))
        } else {
          val knoxData = for {
            knoxUrl <- clusterDataApi.getKnoxUrl(clusterId, forRedirect = true)
            token <- clusterDataApi.getTokenForCluster(clusterId,
              Some(HJwtToken(dpToken.get().value())),
              Try(config.getString("dp.services.knox.token.redirect.topology")).getOrElse("redirecttoken"))
          } yield (knoxUrl, token)

          onComplete(knoxData) {
            case Success((Some(knox), Some(token))) =>
              complete(getHdpTokenResponse(knox, token))

            case Success((_, _)) =>
              complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.knox.generic", "Either cluster is not knox enabled or failed to get token from cluster.", new Exception("Failed to get token from cluster.")))
            case Failure(th) =>
              th.getClass match {
                case c if c == classOf[ClusterNotFound] =>
                  logger.error(s"Failed to get the cluster details with clusterId `$clusterId", c)
                  complete(StatusCodes.NotFound, notFound)
                case x =>
                  logger.error(s"A generic error occurred while communicating with cluster knox.", x)
                  complete(StatusCodes.InternalServerError, errors(500, "cluster.ambari.knox.generic", "A generic error occured while communicating with Cluster knox.", th))
              }
          }
        }

      }

    }
  }

  private def getHdpTokenResponse(knoxUrl: String, token: String) = Json.obj("knox_url" -> knoxUrl, "token" -> token)

}
