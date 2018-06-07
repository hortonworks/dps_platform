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

package com.hortonworks.dataplane.db

import com.hortonworks.dataplane.commons.domain.Entities.{
  ClusterHost,
  ClusterService,
  Error,
  Errors
}
import com.hortonworks.dataplane.db.Webservice.{
  ClusterComponentService,
  ClusterHostsService
}
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClusterHostsServiceImpl(config: Config)(implicit ws: WSClient)
    extends ClusterHostsService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))
  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  override def createOrUpdate(host: ClusterHost): Future[Option[Errors]] = {
    ws.url(s"$url/clusters/hosts")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .put(Json.toJson(host))
      .map { res =>
        res.status match {
          case 200 => None
          case x => Some(Errors(Seq(Error(x, "Cannot update host"))))
        }
      }
      .recoverWith {
        case e: Exception =>
          Future.successful(Some(Errors(Seq(Error(500, e.getMessage)))))
      }

  }

  override def getHostsByCluster(
      clusterId: Long): Future[Either[Errors, Seq[ClusterHost]]] = {
    ws.url(s"$url/clusters/$clusterId/hosts")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .get()
      .map(mapToHosts)
  }

  private def mapToHost(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[ClusterHost](
          res,
          r => (r.json \ "results" \\ "data").head.validate[ClusterHost].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToHosts(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[Seq[ClusterHost]](res,
                                        r =>
                                          (r.json \ "results" \\ "data").map {
                                            d =>
                                              d.validate[ClusterHost].get
                                        })
      case _ => mapErrors(res)
    }
  }

  override def getHostByClusterAndName(
      clusterId: Long,
      hostName: String): Future[Either[Errors, ClusterHost]] = {
    ws.url(s"$url/clusters/$clusterId/host/$hostName")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .get()
      .map(mapToHost)
  }
}
