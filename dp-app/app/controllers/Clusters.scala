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

package controllers

import javax.inject.Inject

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.domain.Ambari._
import com.hortonworks.dataplane.commons.domain.Entities.{
  Cluster,
  DataplaneClusterIdentifier,
  Error,
  ClusterHost,
  Errors
}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{
  ClusterHostsService,
  ClusterService,
  SkuService
}
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.cs.Webservice.AmbariWebService
import models.{ClusterHealthData, JsonResponses}
import play.api.Logger
import play.api.mvc._
import play.api.libs.json.Json
import play.api.Configuration
import services.ClusterHealthService
import services.AmbariService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Clusters @Inject()(
    @Named("clusterService") val clusterService: ClusterService,
    @Named("clusterHostsService") val clusterHostsService: ClusterHostsService,
    @Named("skuService") val skuService: SkuService,
    val clusterHealthService: ClusterHealthService,
    ambariService: AmbariService,
    @Named("clusterAmbariService") ambariWebService: AmbariWebService,
    configuration: Configuration
) extends Controller {

  def list(dpClusterId: Option[String]) = Action.async {
    dpClusterId match {
      case Some(id) => listByDpClusterId(id)
      case None => listAll()
    }
  }

  private def listByDpClusterId(dpClusterId: String) = {
    clusterService
      .getLinkedClusters(dpClusterId.toLong)
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(clusters) => Ok(Json.toJson(clusters))
      }
  }

  private def listAll() = {
    clusterService
      .list()
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(clusters) => Ok(Json.toJson(clusters))
      }
  }

  def create = AuthenticatedAction.async(parse.json) { request =>
    Logger.info("Received create cluster request")
    request.body
      .validate[Cluster]
      .map { cluster =>
        clusterService
          .create(cluster.copy(userid = request.user.id))
          .map {
            case Left(errors) =>
              InternalServerError(Json.toJson(errors))
            case Right(cluster) => Ok(Json.toJson(cluster))
          }
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def update = Action.async(parse.json) { req =>
    Future.successful(Ok(JsonResponses.statusOk))
  }

  def get(clusterId: String) = Action.async {
    Logger.info("Received get cluster request")

    clusterService
      .retrieve(clusterId)
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(cluster) => Ok(Json.toJson(cluster))
      }
  }

  def getDetails = AuthenticatedAction.async(parse.json) { request =>
    implicit val token = request.token
    request.body
      .validate[AmbariDetailRequest]
      .map { req =>
        ambariService
          .getClusterDetails(req)
          .flatMap {
            case Left(errors) =>
              Future.successful(InternalServerError(Json.toJson(errors)))
            case Right(clusterDetails) => {
              skuService
                .getAllSkus()
                .map {
                  case Left(errors) => InternalServerError(Json.toJson(errors))
                  case Right(skus) =>
                    val allDependentServices = skus
                      .flatMap(
                        sku =>
                          configuration
                            .getStringSeq(
                              s"${sku.name}.dependent.services.mandatory")
                            .getOrElse(Nil))
                      .distinct
                    val newClusterDetails = clusterDetails.map { clDetails =>
                      val availableRequiredServices =
                        allDependentServices.intersect(clDetails.services)
                      val otherServices =
                        clDetails.services.diff(availableRequiredServices)
                      val allAvailableServices =
                        availableRequiredServices.union(otherServices)
                      AmbariCluster(clDetails.security,
                                    clDetails.clusterName,
                                    allAvailableServices,
                                    clDetails.knoxUrl)
                    }
                    Ok(Json.toJson(newClusterDetails))
                }
            }
          }
      }
      .getOrElse(Future.successful(BadRequest))

  }

  def syncCluster(dpClusterId: String) = AuthenticatedAction.async { request =>
    implicit val token = request.token
    ambariService.syncCluster(DataplaneClusterIdentifier(dpClusterId.toLong)).map {
      case true =>
        Ok(Json.toJson(true))
      case false =>
        Ok(Json.toJson(false))
    }
  }

  import models.ClusterHealthData._

  def getHealth(clusterId: String, summary: Option[Boolean]) =
    AuthenticatedAction.async { request =>
      Logger.info("Received get cluster health request")
      implicit val token = request.token
      val dpClusterId = request.getQueryString("dpClusterId").get
      ambariService
        .syncCluster(DataplaneClusterIdentifier(dpClusterId.toLong))
        .flatMap {
          case true =>
            clusterHealthService
              .getClusterHealthData(clusterId.toLong, dpClusterId)
          case false =>
            Future.successful(Left(Errors(Seq(Error(500, "Sync failed")))))
        }
        .map {
          case Left(errors) => InternalServerError(Json.toJson(errors))
          case Right(clusterHealth) =>
            Ok(summary match {
              case Some(_) =>
                clusterHealth.nameNodeInfo match {
                  case Some(_) =>
                    mapToJson(clusterHealth)
                  case None =>
                    Json.obj(
                      "nodes" -> clusterHealth.hosts.length
                    )
                }
              case None => Json.toJson(clusterHealth)
            })
        }
    }

  private def mapToJson(clusterHealth: ClusterHealthData) = {
    Json.obj(
      "nodes" -> clusterHealth.hosts.length,
      "totalSize" -> humanizeBytes(
        clusterHealth.nameNodeInfo.get.CapacityTotal),
      "usedSize" -> humanizeBytes(clusterHealth.nameNodeInfo.get.CapacityUsed),
      "status" -> Json.obj(
        "state" -> (if (clusterHealth.syncState.get == "SYNC_ERROR")
                      clusterHealth.syncState.get
                    else clusterHealth.nameNodeInfo.get.state),
        "since" -> clusterHealth.nameNodeInfo.get.StartTime
          .map(_ =>
            clusterHealth.nameNodeInfo.get.StartTime.get - System
              .currentTimeMillis())
      )
    )
  }

  def getResourceManagerHealth(clusterId: String) = AuthenticatedAction.async {
    request =>
      {
        implicit val token = request.token
        val rmRequest =
          configuration.getString("cluster.rm.health.request.param").get;

        ambariWebService.requestAmbariClusterApi(clusterId.toLong, rmRequest).map {
          case Left(errors) => InternalServerError(Json.toJson(errors))
          case Right(resourceManagerHealth) =>
            Ok(Json.toJson(resourceManagerHealth))
        }
      }
  }

  def getHosts(clusterId: Long, ip: Option[String]) = AuthenticatedAction.async {

      val hostsSearchResult = for {
        hostsResponse <- clusterHostsService.getHostsByCluster(clusterId)
        hosts <- {
          if (hostsResponse.isLeft)
            Future.failed(new Exception(hostsResponse.left.get.firstMessage.toString))
          else
            Future.successful(hostsResponse.right.get)
        }
        hostList <- if (ip.isDefined) {
          Future.successful(hosts.filter(_.ipaddr == ip.get))
        } else {
          Future.successful(hosts)
        }

      } yield hostList

      hostsSearchResult
        .map { hsr =>
          Ok(Json.toJson(hsr))
        }
        .recoverWith {
          case e: Throwable =>
            Future.successful(
              InternalServerError(JsonResponses.statusError(e.getMessage)))
        }

    }

  def getDataNodeHealth(clusterId: String) = AuthenticatedAction.async {
    request =>
      {
        implicit val token = request.token
        val dnRequest =
          configuration.getString("cluster.dn.health.request.param").get

        ambariWebService.requestAmbariClusterApi(clusterId.toLong, dnRequest).map {
          case Left(errors) => InternalServerError(Json.toJson(errors))
          case Right(datanodeHealth) =>
            Ok(Json.toJson(datanodeHealth))
        }
      }
  }

  private def humanizeBytes(bytes: Option[Double]): String = {
    bytes match {
      case Some(bytes) =>
        if (bytes == 0) return "0 Bytes"
        val k = 1024
        val sizes = Array("Bytes ",
                          "KB ",
                          "MB ",
                          "GB ",
                          "TB ",
                          "PB ",
                          "EB ",
                          "ZB ",
                          "YB ")
        val i = Math.floor(Math.log(bytes) / Math.log(k)).toInt

        Math.round(bytes / Math.pow(k, i)) + " " + sizes(i)
      case None => return "NA"
    }
  }

}
