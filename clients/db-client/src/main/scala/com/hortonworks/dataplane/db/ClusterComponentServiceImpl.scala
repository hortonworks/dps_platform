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

import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.Ambari.ClusterServiceWithConfigs
import com.hortonworks.dataplane.db.Webservice.ClusterComponentService
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClusterComponentServiceImpl(config: Config)(implicit ws: WSClient)
    extends ClusterComponentService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))
  import com.hortonworks.dataplane.commons.domain.JsonFormatters._
  val logger = Logger(classOf[ClusterComponentServiceImpl])

  override def create(clusterService: ClusterService)
    : Future[Either[Errors, ClusterService]] = {
    ws.url(s"$url/cluster/services")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .post(Json.toJson(clusterService))
      .map(mapToService)
      .recoverWith {
        case e: Exception =>
          Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
      }
  }

  private def mapToService(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[ClusterService](
          res,
          r =>
            (r.json \ "results" \\ "data").head.validate[ClusterService].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToHost(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[ClusterServiceHost](res,
                                          r =>
                                            (r.json \ "results" \\ "data").head
                                              .validate[ClusterServiceHost]
                                              .get)
      case _ => mapErrors(res)
    }
  }

  private def mapToServiceEndpoint(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[ClusterServiceWithConfigs](
          res,
          r =>
            (r.json \ "results" \\ "data").head.validate[ClusterServiceWithConfigs].get)
      case _ => mapErrors(res)
    }
  }

  private def mapToServiceEndpoints(res: WSResponse) = {
    res.status match {
      case 200 =>
        extractEntity[Seq[ClusterServiceWithConfigs]](
          res,
          r =>
            (r.json \ "results" \\ "data").map {
              _.validate[ClusterServiceWithConfigs].get
            })
      case _ => mapErrors(res)
    }
  }


  override def getServiceByName(
      clusterId: Long,
      serviceName: String): Future[Either[Errors, ClusterService]] = {
    ws.url(s"$url/clusters/$clusterId/service/$serviceName")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .get
      .map(mapToService)
      .recoverWith {
        case e: Exception =>
          Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
      }
  }

  override def updateServiceByName(clusterData: ClusterService): Future[Either[Errors, Boolean]] = {
    ws.url(s"$url/clusters/services")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .put(Json.toJson(clusterData))
      .map { res =>
        res.status match {
          case 200 => Right(true)
          case x => Left(Errors(Seq(Error(x, "Cannot update service"))))
        }
      }
      .recoverWith {
        case e: Exception =>
          Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
      }
  }

  private def httpHandler(res: WSResponse): JsValue = {
    res.status match {
      case 200 => res.json
      case _ => throw WrappedErrorException(Error(500, s"Unexpected error: Received ${res.status}", "cluster.http.db.generic"))
    }
  }

  override def upsertServiceByName(clusterId: String, serviceName: String, cs: ClusterService): Future[ClusterService] = {
    ws.url(s"$url/clusters/$clusterId/services/$serviceName")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .put(Json.toJson(cs))
      .map(httpHandler)
      .map(json => (json \ "results").asOpt[ClusterService])
      .map {
        case Some(result) => result
        case None => throw WrappedErrorException(Error(500, s"Unexpected error: Non JSON response", "cluster.http.db.json"))
      }
  }

  override def addClusterHosts(clusterServiceHosts: Seq[ClusterServiceHost])
    : Future[Seq[Either[Errors, ClusterServiceHost]]] = {

    val requests = clusterServiceHosts.map { cse =>
      ws.url(s"$url/services/endpoints")
        .withHeaders(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json"
        )
        .post(Json.toJson(cse))
        .map(mapToHost)
        .recoverWith {
          case e: Exception =>
            logger.error(s"Cannot add cluster endpoint $cse", e)
            Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
        }

    }

    Future.sequence(requests)
  }

  override def updateClusterHosts(clusterServiceHosts: Seq[ClusterServiceHost])
    : Future[Seq[Either[Errors, Boolean]]] = {

    val requests = clusterServiceHosts.map { cse =>
      ws.url(s"$url/services/endpoints")
        .withHeaders(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json"
        )
        .put(Json.toJson(cse))
        .map(res =>
          res.status match {
            case 200 => Right(true)
            case x =>
              Left(
                Errors(
                  Seq(Error(x, "Cannot update service endpoint"))))
        })
        .recoverWith {
          case e: Exception =>
            Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
        }
    }
    Future.sequence(requests)
  }

  def resolve(result: Either[Errors, ClusterService],
              clusterId: Long,
              service: String) = Future.successful {
    if (result.isLeft) {
      throw new Exception(
        s"Could not load the service for cluster $clusterId and service $service ${result.left.get}")
    }
    result.right.get
  }

  override def getEndpointsForCluster(
      clusterId: Long,
      service: String): Future[Either[Errors, ClusterServiceWithConfigs]] = {
    for {
      f1 <- getServiceByName(clusterId, service)
      f2 <- resolve(f1, clusterId, service)
      errorsOrEndpoints <- ws
        .url(s"$url/services/${f2.id.get}/endpoints")
        .withHeaders(
          "Content-Type" -> "application/json",
          "Accept" -> "application/json"
        )
        .get
        .map(mapToServiceEndpoint)
    } yield errorsOrEndpoints

  }

  override def getAllServiceEndpoints(serviceName: String): Future[Either[Errors, Seq[ClusterServiceWithConfigs]]] = {
    ws.url(s"$url/services/endpoints/$serviceName")
    .withHeaders(
      "Content-Type" -> "application/json",
      "Accept" -> "application/json"
    ).get.map(mapToServiceEndpoints).recoverWith {
        case e: Exception => Future.successful(Left(Errors(Seq(Error(500, e.getMessage)))))
      }
  }

  override def listClusterServices(clusterId: String, serviceName: Option[String]): Future[Seq[ClusterService]] = {
    var params: Map[String, String] = Map.empty
    serviceName.foreach(serviceName => params = params + ("serviceName" -> serviceName.toString))

    ws.url(s"$url/clusters/$clusterId/services")
      .withQueryString(params.toList: _*)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        res.status match {
          case 200 => (res.json \ "results" \\ "data").map(_.asOpt[ClusterService]).filter(_.isDefined).map(_.get)
          case _ => throw WrappedErrorException(Error(res.status, "", "core.api-error.failed-cluster-services"))
        }
      }
  }

  override def listClusterServiceHosts(clusterId: String, serviceId: String): Future[Seq[String]] = {
    ws.url(s"$url/clusters/$clusterId/services/$serviceId/hosts")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { res =>
        res.status match {
          case 200 => (res.json \ "results" \\ "host").map(host => host.asOpt[String]).filter(_.isDefined).map(_.get).distinct
          case _ => throw WrappedErrorException(Error(res.status, "", "core.api-error.failed-cluster-service-hosts"))
        }
      }
  }

}
