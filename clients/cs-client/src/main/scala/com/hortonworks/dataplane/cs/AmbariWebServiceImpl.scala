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

package com.hortonworks.dataplane.cs

import com.hortonworks.dataplane.commons.domain.Ambari.{AmbariCheckResponse, AmbariCluster, ServiceInfo}
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.{Ambari, Entities}
import com.hortonworks.dataplane.cs.Webservice.AmbariWebService
import com.typesafe.config.Config
import play.api.libs.json._
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AmbariWebServiceImpl(val config: Config)(implicit ws: ClusterWsClient)
    extends AmbariWebService {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._
  import com.hortonworks.dataplane.commons.domain.Ambari._


  private def mapResponse(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[AmbariCheckResponse](
          res,
          r => (r.json \ "results" \ "data").validate[AmbariCheckResponse].get)
      case _ => mapErrors(res)
    }

  }

  override def checkAmbariStatus(ambariUrl: String, allowUntrusted: Boolean, behindGateway: Boolean)(
      implicit token: Option[Entities.HJwtToken])
    : Future[Either[Errors, AmbariCheckResponse]] = {
    ws.url(s"$url/ambari/status?url=$ambariUrl&allowUntrusted=$allowUntrusted&behindGateway=$behindGateway")
      .withToken(token)
      .withHeaders("Content-Type" -> "application/json",
                   "Accept" -> "application/json")
      .get()
      .map(mapResponse)
  }

  override def getAmbariDetails(
      ambariDetailRequest: Ambari.AmbariDetailRequest)(
      implicit token: Option[Entities.HJwtToken])
    : Future[Either[Errors, Seq[Ambari.AmbariCluster]]] = {
    ws.url(s"$url/ambari/details")
      .withToken(token)
      .post(Json.toJson(ambariDetailRequest))
      .map { response =>
        if (response.status == 200) {
          val seq = response.json \ "results" \ "data"
          Right(seq.validate[Seq[AmbariCluster]].get)
        } else {
          Left(
            Errors(Seq(
              Error(500, (response.json \ "error" \ "message").as[String]))))
        }
      }
  }

  override def getAmbariServicesInfo(dpcwServices: DpClusterWithDpServices)(implicit token: Option[Entities.HJwtToken])
  : Future[Either[Errors, Seq[ServiceInfo]]] = {
    ws.url(s"$url/ambari/servicesInfo")
      .withToken(token)
      .post(Json.toJson(dpcwServices))
      .map { response =>
        if (response.status == 200) {
          val seq = response.json \ "results" \ "data"
          Right(seq.validate[Seq[ServiceInfo]].get)
        } else {
          mapErrors(response)
        }
      }
  }

  override def syncAmbari(dpCluster: Entities.DataplaneClusterIdentifier)(
      implicit token: Option[Entities.HJwtToken]): Future[Boolean] = {
    ws.url(s"$url/cluster/sync")
      .withToken(token)
      .post(Json.toJson(dpCluster))
      .map(_.status == 200)
  }

  private def insert(path: JsPath, value: JsValue) = __.json.update(path.json.put(value))

  private def mapToResults(res: WSResponse)(implicit clusterId: Option[Long])  = {
    res.status match {
      case 200 =>
        clusterId match {
          case None => Right(res.json)
          case Some(value) =>
            Right(Json.toJson(AmbariResponseWithDpClusterId(value,res.json)))
        }
      case _ => mapErrors(res)
    }
  }


  override def requestAmbariApi(clusterId: Long, ambariUrl: String, addClusterIdToResponse: Boolean = false)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]] = {
    implicit val _ = if (addClusterIdToResponse) Some(clusterId) else None
    ws.url(s"$url/$clusterId/ambari?request=$ambariUrl")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResults)
  }

  override def requestAmbariClusterApi(clusterId: Long, ambariUrl: String, addClusterIdToResponse: Boolean = false)(implicit token:Option[HJwtToken]): Future[Either[Errors, JsValue]] = {
    implicit val _ = if (addClusterIdToResponse) Some(clusterId) else None
    ws.url(s"$url/$clusterId/ambari/cluster?request=$ambariUrl")
      .withToken(token)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToResults)
  }

  override def isSingleNode = ws.url(s"$url/ambari/config?key=dp.service.ambari.single.node.cluster&boolean=true")
    .withToken(None)
    .get().map( res => {
     res.status match {
       case 204 => true
       case _ => false
     }
  }).recoverWith {
    case e:Throwable =>
      Future.successful(false)
  }
}
