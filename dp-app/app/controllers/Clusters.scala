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

import java.net.URL
import javax.inject.Inject

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.domain.Ambari._
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{
  ClusterService,
  ClusterComponentService,
  DpClusterService,
  ClusterHostsService,
  SkuService,
  ClusterSkuService
}
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.cs.Webservice.AmbariWebService
import models.{ClusterHealthData, JsonResponses, WrappedErrorsException}
import com.hortonworks.dataplane.db.Webservice.{ClusterHostsService, ClusterService, SkuService}
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.cs.Webservice.{AmbariWebService, ClusterClient}
import models.{ClusterHealthData, JsonResponses, WrappedErrorsException}
import org.apache.http.client.utils.URIBuilder
import play.api.Logger
import play.api.mvc._
import play.api.libs.json._
import play.api.Configuration
import services.{AmbariService, ClusterHealthService, PluginManifestService}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Left
import scala.util.Success

class Clusters @Inject()(
    @Named("clusterService") val clusterService: ClusterService,
    @Named("dpClusterService") val dpClusterService: DpClusterService,
    @Named("clusterHostsService") val clusterHostsService: ClusterHostsService,
    @Named("clusterComponentsService") val clusterComponentService: ClusterComponentService,
    @Named("skuService") val skuService: SkuService,
    val clusterHealthService: ClusterHealthService,
    @Named("clusterSkuService") val clusterSkuService: ClusterSkuService,
    ambariService: AmbariService,
    @Named("clusterAmbariService") ambariWebService: AmbariWebService,
    @Named("pluginManifestService") val pluginService: PluginManifestService,
    @Named("clusterClient") clusterClient: ClusterClient,
    configuration: Configuration
) extends Controller {

  def list(dpClusterId: Option[String], plugin: Option[String]): Action[AnyContent]
    = AuthenticatedAction.async { request =>
    dpClusterId
      .map(dpClusterId => listByDpClusterId(dpClusterId))
      .getOrElse(getAllClusters())
      // future
      .flatMap { clusters =>
        plugin.map { plugin =>
          val futures = clusters.map { cCluster =>
            clusterSkuService
              .getClusterSku(pluginService.retrieve(plugin).get.id, cCluster.dataplaneClusterId.get)
              .map(_.map(_ => cCluster))
          }
          Future.sequence(futures).map(_.collect { case Some(cluster) => cluster })
        }.getOrElse(Future.successful(clusters))
      }
      .map { clusters => Ok(Json.toJson(clusters)) }
      .recover {
        case th: WrappedErrorsException => InternalServerError(Json.toJson(th.errors))
        case th: Throwable => InternalServerError(Json.toJson(th.getMessage))
      }
  }

  private def listByDpClusterId(dpClusterId: String) = {
    clusterService
      .getLinkedClusters(dpClusterId.toLong)
      .flatMap {
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(clusters) => Future.successful(clusters)
      }
  }

  private def getAmbariServicesInfo(dpCluster: DataplaneCluster)(implicit token:Option[HJwtToken]): Future[Seq[ServiceInfo]] =  {
    skuService.getAllSkus()
      .map {
        case Left(errors: Errors) =>
          Logger.error(s"Failed to get dp-dependent services $errors")
          throw WrappedErrorsException(errors)
        case Right(skus: Seq[Sku]) => skus.flatMap(sku => pluginService.getDependencies(sku.name))
      }
      .flatMap { services =>
        ambariService
          .getClusterServices(DpClusterWithDpServices(dataplaneCluster = dpCluster, dpServices = services))
          .map {
            case Left(errors: Errors) =>
              Logger.error(s"Failed to get services info $errors")
              throw WrappedErrorsException(errors)
            case Right(servicesInfo: Seq[ServiceInfo]) => servicesInfo
          }
      }
  }

  private def getAllClusters() = {
    clusterService
      .list()
      .flatMap{
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(clusters) => Future.successful(clusters)
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

  def update = AuthenticatedAction.async(parse.json) { req =>
    Future.successful(Ok(JsonResponses.statusOk))
  }

  def get(clusterId: String) = AuthenticatedAction.async {
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
                    val allRequiredServices = skus.flatMap(sku => pluginService.getRequiredDependencies(sku.name))
                    val newClusterDetails = clusterDetails.map { clDetails =>

                      val availableRequiredServices = allRequiredServices.intersect(clDetails.services)
                      val otherServices = clDetails.services.diff(availableRequiredServices)
                      val allAvailableServices = availableRequiredServices.union(otherServices)

                      AmbariCluster(clDetails.security,
                                    clDetails.clusterName,
                                    clDetails.clusterType,
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
        Ok(Json.toJson(Map("status" -> true)))
      case false =>
        Ok(Json.toJson(Map("status" -> false)))
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

  /**
    * Controller function to redirect a URL to cluster knox with a token following the TokenSSO topology.
    * This will help in SSO when a cluster URL is clicked from DP. This endpoint expects a redirectTo URL and then
    * gets a cluster token and attaches it to the final knox URL and adds redirectTo to originalURL.
    * Note: This method accepts dpClusterId
    * @param dpClusterId Cluster Id of the dataplane cluster
    * @param to This will be used as originalUrl parameter in the knox URL that is built
    * @return Result with a redirect to the cluster knox.
    */
  def redirectToService(dpClusterId: String, to: String) = AuthenticatedAction.async { request =>
    implicit val token: Option[HJwtToken] = request.token
    def getFinalRedirectUrl(redirectTo: String, knoxUrl: String, token : String) = {
      val builder = new URIBuilder(s"$knoxUrl/api/v1/websso")
      builder.addParameter("knoxtoken",token)
      builder.addParameter("originalUrl", redirectTo)
      builder.build().toString
    }

    def getCluster(clusterFuture: Future[Either[Errors, Seq[Cluster]]]): Future[Cluster] = {
      clusterFuture.flatMap {
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(clusters) => Future.successful(clusters.head)
      }
    }

    def shouldRedirectThroughKnox(clusterData: Cluster): Boolean = {
      val blackListEntries = configuration.getStringSeq("dp.knox.token.redirect.cluster.version.blacklist").getOrElse(Seq())
      if (clusterData.properties.isDefined) {
        val properties = clusterData.properties.get
        val version = (properties \ "version").as[String]
        !blackListEntries.exists(entry => version.toLowerCase.startsWith(entry.toLowerCase()))
      } else true
    }



    val finalRedirectToUrl = for {
      clusterData <- getCluster(clusterService.getLinkedClusters(dpClusterId.toLong))
      tokenData <- clusterClient.getKnoxToken(clusterData.id.get.toString) if shouldRedirectThroughKnox(clusterData)
      url = getFinalRedirectUrl(to, tokenData.knoxUrl, tokenData.token)
    } yield url


    finalRedirectToUrl.map { url =>
      Redirect(url, TEMPORARY_REDIRECT)
    }.recoverWith {
      case _ =>
        Future.successful(Redirect(to, TEMPORARY_REDIRECT))
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

  def getKafkaDetails(clusterId: String) = AuthenticatedAction.async {
    request =>
    {
      implicit val token = request.token
      val dnRequest =
        configuration.getString("cluster.kafka.details.request.param").get

      ambariWebService.requestAmbariClusterApi(clusterId.toLong, dnRequest).map {
        case Left(errors) => InternalServerError(Json.toJson(errors))
        case Right(kafkaDetails) =>
          Ok(Json.toJson(kafkaDetails))
      }
    }
  }

   def getServicesWithHosts(clusterId: String, serviceName: String) = AuthenticatedAction.async {
     (for {
       services <- clusterComponentService.listClusterServices(clusterId, Some(serviceName))
       json <- Future.sequence(
         services
           .map { cService =>
             clusterComponentService
               .listClusterServiceHosts(clusterId, cService.id.get.toString)
               .map { hosts =>
                 val json = Json.obj(
                    "id" -> cService.id.get,
                    "clusterId" -> cService.clusterId,
                    "serviceName" -> cService.serviceName,
                    "hosts" -> Json.toJson(hosts)
                  )

                 cService.properties
                   .flatMap(json => (json \ "properties").asOpt[JsValue])
                   .map(props => json ++ Json.obj("properties" -> props))
                   .getOrElse(json)
               }
           }
       )
     } yield json)
       .map(json => Ok(Json.toJson(json)))
       .recoverWith{
         case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
         case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
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
