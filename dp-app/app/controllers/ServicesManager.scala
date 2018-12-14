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


import java.util.concurrent.atomic.AtomicInteger

import com.google.inject.Inject
import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.commons.domain.Entities
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.db.Webservice.{ClusterService, ClusterSkuService, DpClusterService, SkuService, UserService}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Controller, Result}

import scala.concurrent.ExecutionContext.Implicits.global
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import models.{JsonResponses, WrappedErrorsException}

import scala.concurrent.Future
import scala.util.{Failure, Left, Success, Try}
import play.api.{Configuration, Logger}
import services.{AmbariService, ConsulHealthService, PluginManifestService}

class ServicesManager @Inject()(@Named("skuService") val skuService:SkuService,
                                @Named("clusterSkuService") val clusterSkuService: ClusterSkuService,
                                @Named("dpClusterService") val dpClusterService: DpClusterService,
                                @Named("clusterService") val clusterService: ClusterService,
                                @Named("healthService") val healthService: ConsulHealthService,
                                @Named("userService") val userService: UserService,
                                @Named("pluginManifestService") val pluginService: PluginManifestService,
                                private val ambariService: AmbariService,
                                private val configuration: Configuration) extends Controller{

  private val smartSenseRegex: String = configuration.underlying.getString("smartsense.regex")

  def getServiceHealth(skuName: String) = Action.async { request =>
    healthService.getServiceHealth(skuName)
      .map {
      statusResponse => Ok(Json.toJson(statusResponse))
    }.recoverWith {
      case e: Throwable =>
        Future.successful(
          InternalServerError(JsonResponses.statusError(e.getMessage)))
    }
  }

  def getServices = AuthenticatedAction.async { request =>
    skuService.getAllSkus().map {
      case Left(errors) => InternalServerError(Json.toJson(errors))
      case Right(skus) => Ok(Json.toJson(skus))
    }
  }

  def verifySmartSense = AuthenticatedAction.async(parse.json) { request =>
    val smartSenseId = request.getQueryString("smartSenseId");
    if (smartSenseId.isEmpty) {
      Future.successful(BadRequest("smartSenseId is required"))
    } else {
      if (verifySmartSenseCode(smartSenseId.get)) {
        //TODO meaningful regex from config
        Future.successful(Ok(Json.obj("isValid" -> true)))
      } else {
        Future.successful(Ok(Json.obj("isValid" -> false)))
      }
    }
  }

  def getSkuByName(skuName: String) = AuthenticatedAction.async { request =>
    skuService.getSku(skuName).map {
      case Left(errors) => handleErrors(errors)
      case Right(services) => {
        Ok(Json.toJson(services))
      }
    }
  }

  private def verifySmartSenseCode(smartSenseId: String) = {
    smartSenseId.matches(smartSenseRegex)
  }

  def enableServiceOnCluster(skuId: Long, dpClusterId: Long) = AuthenticatedAction.async {
    clusterSkuService
      .enableSkuForCluster(skuId, dpClusterId)
      .map { response => Ok(Json.toJson(response)) }
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
  }

  def disableServiceOnCluster(skuId: Long, dpClusterId: Long) = AuthenticatedAction.async {
    clusterSkuService.disableSkuForCluster(skuId, dpClusterId).map { response =>
      Ok(Json.toJson(response))
    }
    .recoverWith {
      case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
      case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
    }
  }

  def enableService=AuthenticatedAction.async(parse.json) { request =>
    request.body.validate[DpServiceEnableConfig].map{config=>
      if (!verifySmartSenseCode(config.smartSenseId)){
        Future.successful(BadRequest("Invalid Smart SenseId"))
      } else {
        skuService.getSku(config.skuName).flatMap {
          case Left(errors) => Future.successful(handleErrors(errors))
          case Right(sku) => {
            val enabledSku = EnabledSku(
              skuId = sku.id.get,
              enabledBy = request.user.id.get,
              smartSenseId = config.smartSenseId,
              subscriptionId = config.smartSenseId //TODO check subscription id later.
            )
            skuService.enableSku(enabledSku).map {
              case Left(errors) => handleErrors(errors)
              case Right(enabledSku) => Ok(Json.toJson(enabledSku))
            }
          }
        }
      }
    }.getOrElse(Future.successful(BadRequest))
  }

  def getEnabledServiceDetail = AuthenticatedAction.async { request =>
    //TODO imiplementation
    Future.successful(Ok)
  }


  def listClustersForSku(skuName: String) = AuthenticatedAction.async{ request =>
    if(request.getQueryString("all").isEmpty && request.getQueryString("compatible").isEmpty){
      getSku(skuName).flatMap{skuInfo =>
        getEnabledClusters(skuInfo.id.get).flatMap(clusters => clusters)
      }
    } else if(request.getQueryString("compatible").isDefined && request.getQueryString("compatible").get == "true"){
      getAllClusters(skuName, request.token).map( lakes =>
        Ok(Json.toJson(lakes.filter(_.compatible == true)))
      ).recover {
          case WrappedErrorsException(ex) => InternalServerError(Json.toJson(ex.errors))
      }
    } else {
      getAllClusters(skuName, request.token).map( lakes => Ok(Json.toJson(lakes))).recover {
          case WrappedErrorsException(ex) => InternalServerError(Json.toJson(ex.errors))
      }
    }

  }

  private def getAllClusters(skuName: String, token: Option[HJwtToken]): Future[Seq[DpClusterSku]] = {
    retrieveLakes().flatMap { lakes =>
      val lakeFutures: Seq[Future[DpClusterSku]] = lakes.map{ cLake =>
        for {
          lake <- Future.successful(cLake)
          services <- getServices(cLake.id.get.toString)
          sku <- getSku(skuName)
          clusterSku <- getClusterSku(sku.id.get, lake.id.get)
          clusters <- retrieveClusters(lake.id.get)
          ambariVersion <- ambariService.retrieveAmbariVersion(clusters.head.id.get, token)
          mandatoryServices <- Future.successful(pluginService.getRequiredDependencies(skuName))
        } yield {
          var enabled = false
          var isCompatible = false
          if(clusterSku.isEmpty){
            isCompatible = services.map(_.serviceName).intersect(mandatoryServices).length == mandatoryServices.length

            isCompatible = isCompatible && pluginService.isVersionCompatible(skuName, ambariVersion)
          } else {
            enabled = true
            isCompatible = true
          }
          DpClusterSku(id=lake.id.get,
            name=lake.name,
            dcName = lake.dcName,
            clusterStatus = lake.state,
            enabled = enabled,
            compatible = isCompatible,
            cluster = clusters.headOption)
        }
      }
      processLakeFutures(lakeFutures)
    }
  }

  private def processLakeFutures(lakeFutures:Seq[Future[DpClusterSku]]): Future[Seq[DpClusterSku]] = {
    Future.sequence(
      lakeFutures.map(_.map(Success(_)).recover({case x => Failure(x)})))
      .map(_.collect { case Success(lake) => lake })
  }

  private def getClusterSku(skuId: Long, dpCluserId: Long): Future[Option[ClusterSku]] = {
    clusterSkuService.getClusterSku(skuId, dpCluserId).map{
      clusterSku => clusterSku
    }
  }

  private def getEnabledClusters(skuId: Long) = {
    clusterSkuService.getAllBySkuId(skuId).map { clusterSkus => {
      val lakeFutures =
        clusterSkus
          .map({ clusterSku =>
            for {
              sku <- Future.successful(clusterSku)
              lake <- retrieveClusterById(sku.dpClusterId.toString)
              cluster <- retrieveClusters(lake.id.get)
            } yield
              DpClusterSku(id=lake.id.get,
                name=lake.name,
                dcName = lake.dcName,
                clusterStatus = lake.state,
                enabled = true,
                compatible = true,
                cluster = cluster.headOption)
          })
      Future.sequence(lakeFutures)
    }.map({ enabledClusters =>
      Ok(Json.toJson(enabledClusters))
    })
      .recover {
        case WrappedErrorsException(ex) =>
          InternalServerError(Json.toJson(ex.errors))
      }
    }
  }

  private def retrieveClusters(lakeId: Long): Future[Seq[Cluster]] = {
    clusterService
      .getLinkedClusters(lakeId)
      .flatMap({
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(clusters) => Future.successful(clusters)
      })
  }

  private def retrieveLakes(): Future[Seq[DataplaneCluster]] = {
    dpClusterService
      .list()
      .flatMap({
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(lakes) => Future.successful(lakes)
      })
  }

  private def retrieveClusterById(dpClusterId: String): Future[DataplaneCluster] = {
    dpClusterService.retrieve(dpClusterId)
      .flatMap {
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(cluster) => Future.successful(cluster)
      }
  }

  private def getServices(dpClusterId: String) = {
    dpClusterService.retrieveServiceInfo(dpClusterId)
      .flatMap({
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(services) => Future.successful(services)
      })
  }

  private def getSku(skuName: String) = {
    skuService.getSku(skuName).flatMap({
      case Left(errors) => Future.failed(WrappedErrorsException(errors))
      case Right(sku) => Future.successful(sku)
    })
  }

  private def handleErrors(errors: Errors) = {
    if (errors.errors.exists(_.status == "400"))
      BadRequest(Json.toJson(errors))
    else if (errors.errors.exists(_.status == "403"))
      Forbidden(Json.toJson(errors))
    else
      InternalServerError(Json.toJson(errors))
  }

}
