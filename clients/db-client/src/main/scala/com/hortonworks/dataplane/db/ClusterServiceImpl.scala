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
  Cluster,
  Error,
  Errors
}
import com.hortonworks.dataplane.db.Webservice.ClusterService
import com.typesafe.config.Config
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClusterServiceImpl(config: Config)(implicit ws: WSClient)
    extends ClusterService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  private def mapToClusters(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[Seq[Cluster]](res,
                                    r =>
                                      (r.json \ "results" \\ "data").map { d =>
                                        d.validate[Cluster].get
                                    })
      case 404 => Left(Errors(Seq(Error(404, "Cluster not found"))))
      case _ => mapErrors(res)
    }
  }

  private def mapToCluster(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[Cluster](
          res,
          r => (r.json \ "results" \\ "data").head.validate[Cluster].get)
      case 404 => Left(Errors(Seq(Error(404, "Cluster not found"))))
      case _ => mapErrors(res)
    }
  }

  override def getLinkedClusters(
      dpClusterId: Long): Future[Either[Errors, Seq[Cluster]]] = {
    ws.url(s"$url/dp/clusters/$dpClusterId/clusters")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToClusters)
  }

  override def list(): Future[Either[Errors, Seq[Cluster]]] = {
    ws.url(s"$url/clusters")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToClusters)

  }

  override def create(cluster: Cluster): Future[Either[Errors, Cluster]] = {
    ws.url(s"$url/clusters")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .post(Json.toJson(cluster))
      .map(mapToCluster)
  }

  override def retrieve(clusterId: String): Future[Either[Errors, Cluster]] = {
    ws.url(s"$url/clusters/$clusterId")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCluster)
  }

}
