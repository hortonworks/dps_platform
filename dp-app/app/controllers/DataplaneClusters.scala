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
import com.hortonworks.dataplane.commons.domain.Ambari.ServiceInfo
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{ClusterSkuService, DpClusterService, SkuService}
import models.{JsonResponses, WrappedErrorsException}
import play.api.{Configuration, Logger}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import services.{AmbariService, ConsulHealthService, PluginManifestService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction

import scala.util.{Left, Try}

class DataplaneClusters @Inject()(
    @Named("dpClusterService") val dpClusterService: DpClusterService,
    @Named("skuService") val skuService: SkuService,
    @Named("pluginManifestService") val pluginService: PluginManifestService,
    @Named("clusterSkuService") val clusterSkuService: ClusterSkuService,
    @Named("healthService") val healthService: ConsulHealthService,
    configuration: Configuration,
    ambariService: AmbariService)
    extends Controller {

  def list(plugin: Option[String]): Action[AnyContent] = AuthenticatedAction.async {
    listAllClusters()
      .flatMap { lakes =>
        plugin.map { plugin =>
          val futures = lakes.map { cDpCluster =>
            clusterSkuService
              .getClusterSku(pluginService.retrieve(plugin).get.id, cDpCluster.id.get)
              .map(_.map(_ => cDpCluster))
          }
          Future.sequence(futures).map(_.collect { case Some(lake) => lake })
        }.getOrElse(Future.successful(lakes))
      }
      .map { lakes => Ok(Json.toJson(lakes)) }
      .recover {
        case th: WrappedErrorsException => InternalServerError(Json.toJson(th.errors))
        case th: Throwable => InternalServerError(Json.toJson(th.getMessage))
      }
  }

  private def listAllClusters() = {
    dpClusterService
      .list()
      .flatMap{
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(lakes) => Future.successful(lakes)
      }
  }

  def create = AuthenticatedAction.async(parse.json) { request =>
    implicit val token = request.token
    Logger.info("Received create data centre request")
    request.body
      .validate[DataplaneCluster]
      .map { dataplaneCluster =>
        dpClusterService
          .create(
            dataplaneCluster.copy(
              createdBy = request.user.id,
              ambariUrl = dataplaneCluster.ambariUrl.replaceFirst("/$", "")))
          .map {
            case Left(errors) =>
              InternalServerError(Json.toJson(errors))
            case Right(dpCluster) =>
              syncCluster(DataplaneClusterIdentifier(dpCluster.id.get))
              Ok(Json.toJson(dpCluster))
          }
      }
      .getOrElse(Future.successful(BadRequest))
  }

  private def syncCluster(dataplaneCluster: DataplaneClusterIdentifier)(
      implicit hJwtToken: Option[HJwtToken]): Future[Boolean] = {
    ambariService.syncCluster(dataplaneCluster).map { result =>
      Logger.info(s"Asking Cluster service to discover ${dataplaneCluster.id}")
      result
    }

  }

  def retrieve(clusterId: String) = AuthenticatedAction.async {
    Logger.info("Received retrieve data centre request")
    if(Try(clusterId.toLong).isFailure){
      Future.successful(NotFound)
    }else{
      dpClusterService
        .retrieve(clusterId.toString)
        .map {
          case Left(errors) =>
            errors.firstMessage match {
              case 404 => NotFound(JsonResponses.statusError(s"${Json.toJson(errors)}"))
              case _ => InternalServerError(Json.toJson(errors))
            }
          case Right(dataplaneCluster) => Ok(Json.toJson(dataplaneCluster))
        }
    }
  }

  def retrieveClusterServices(dpClusterId: String) = AuthenticatedAction.async {
    Logger.info("Received retrieve data centre request")
    dpClusterService
      .retrieveServiceInfo(dpClusterId)
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(clusterServices) => Ok(Json.toJson(clusterServices))
      }
  }

  def retrieveServices(dpClusterId: String): Action[AnyContent] = AuthenticatedAction.async { request =>
    implicit val token = request.token
    getServiceStatus(dpClusterId)
  }

  private def getServiceStatus(dpClusterId: String)(implicit token:Option[HJwtToken]) = {
    val manifests: Seq[PluginManifest] =  pluginService.list().filter(_.enablement_type == "BY_ADMIN")
      val futures = manifests.map( manifest => {
        for {
          sku <- getService(manifest.name)
          health <- healthService.getServiceHealth(sku.name)
          clusterSku <- getClusterSku(sku.id.get, dpClusterId.toLong)
          cluster <- retrieveClusterById(dpClusterId)
          clusterServices <- getAmbariServicesInfo(cluster)
          serviceManifest <- Future.successful(manifest)
          mandatoryServices <-  Future.successful(pluginService.getRequiredDependencies(sku.name))
        } yield {
          var enabled = false
          var isCompatible = false
          if (clusterSku.isEmpty) {
            isCompatible = clusterServices.map(_.serviceName).intersect(mandatoryServices).length == mandatoryServices.length
          } else {
            enabled = true
            isCompatible = true
          }
          Json.obj(
            "sku" -> sku,
            "compatible" -> isCompatible,
            "enabled" -> enabled,
            "dependencies" -> mandatoryServices,
            "health" -> health,
            "version" -> serviceManifest.version
          )
        }
      })
      Future.sequence(futures)
      .map( services => Ok(Json.toJson(services)))
      .recover {
        case WrappedErrorsException(ex) => InternalServerError(Json.toJson(ex.errors))
      }
  }

  private def getService(name: String) = {
    skuService.getSku(name).flatMap {
      case Left(errors) => Future.failed(WrappedErrorsException(errors))
      case Right(sku) => Future.successful(sku)
    }
  }

  private def getClusterSku(skuId: Long, dpClusterId: Long): Future[Option[ClusterSku]] = {
    clusterSkuService.getClusterSku(skuId, dpClusterId).flatMap {
      case Some(clusterSku) => Future.successful(Some(clusterSku))
      case None => Future.successful(None)
    }
  }

  def update(dpClusterId: String) = AuthenticatedAction.async(parse.json) { request =>
    Logger.info("Received update data centre request")
    request.body
      .validate[DataplaneCluster]
      .map { dpCluster =>
        (for {
          cluster <- retrieveClusterById(dpClusterId)
          newCluster <- Future.successful(cluster.copy(
            id = dpCluster.id,
            dcName = dpCluster.dcName,
            description = dpCluster.description,
            location= dpCluster.location,
            properties = dpCluster.properties
          ))
          updated <- updateClusterById(newCluster)
        } yield {
          Ok(Json.toJson(updated))
        })
        .recover{
          case ex: WrappedErrorsException => InternalServerError(Json.toJson(ex.errors))
        }
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def delete(clusterId: String) = AuthenticatedAction.async {
    Logger.info("Received delete data centre request")
    dpClusterService
      .delete(clusterId)
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(dataplaneCluster) => Ok(Json.toJson(dataplaneCluster))
      }
  }

  def deleteByParams(clusterName: Option[String], datacenterName: Option[String]) = Action.async { req =>
    Logger.info("Received delete dp cluster request with clustername and datacenter")
    if(clusterName.isDefined && datacenterName.isDefined) {
      dpClusterService
        .delete(clusterName.get, datacenterName.get)
        .map {
          case Left(errors) =>
            InternalServerError(Json.toJson(errors))
          case Right(dataplaneCluster) => Ok(Json.toJson(dataplaneCluster))
        }
    } else {
      Future(BadRequest("'clusterName' and 'datacenterName' query parameters are required."))
    }


  }

  def ambariCheck(url: String, allowUntrusted: Boolean, behindGateway: Boolean) = AuthenticatedAction.async { request =>
    implicit val token: Option[HJwtToken] = request.token
    ambariService
      .statusCheck(url, allowUntrusted, behindGateway)
      .flatMap {
        case Left(errors) => Future.successful(InternalServerError(Json.toJson(errors)))
        case Right(checkResponse) => {
//          TODO: use url instead of ip address
          dpClusterService.checkExistenceByUrl(checkResponse.ambariIpAddress).map {
            case Left(errors) => InternalServerError(Json.toJson(errors))
            case Right(status) =>
              if(status){
                InternalServerError(Json.toJson(Errors(Seq(Error(500, "This Ambari cluster has already been added.", "core.ambari.status.already-added")))))
              } else {
                Ok(Json.toJson(checkResponse))
              }
          }
        }
      }
  }

  private def retrieveClusterById(dpClusterId: String): Future[DataplaneCluster] = {
    dpClusterService.retrieve(dpClusterId)
        .flatMap {
          case Left(errors) => Future.failed(WrappedErrorsException(errors))
          case Right(cluster) => Future.successful(cluster)
        }
  }

  private def updateClusterById(cluster: DataplaneCluster): Future[DataplaneCluster] = {
    dpClusterService.update(cluster)
        .flatMap {
          case Left(errors) => Future.failed(WrappedErrorsException(errors))
          case Right(cluster) => Future.successful(cluster)
        }
  }

  def getDependentServicesDetails(clusterId: String): Action[AnyContent] = AuthenticatedAction.async { request =>
    implicit val token = request.token
    dpClusterService
      .retrieve(clusterId)
      .flatMap {
        case Left(errors) =>
          Logger.error(s"Failed to get cluster details $errors")
          throw WrappedErrorsException(errors)
        case Right(dataplaneCluster) => getAmbariServicesInfo(dataplaneCluster)
      }
      .map{ servicesInfo => Ok(Json.toJson(servicesInfo)) }
      .recoverWith {
        case ex: WrappedErrorsException =>
          Logger.error(s"Failed to get services details ${ex.errors}")
          Future.successful(InternalServerError(Json.toJson(ex.errors)))
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

}
