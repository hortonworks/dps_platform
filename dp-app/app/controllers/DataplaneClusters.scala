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
import com.hortonworks.dataplane.db.Webservice.{DpClusterService, SkuService}
import models.{JsonResponses, WrappedErrorsException}
import play.api.{Configuration, Logger}
import play.api.libs.json.Json
import play.api.mvc._
import services.AmbariService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.cs.Webservice.AmbariWebService

import scala.util.Try

import scala.util.Try

class DataplaneClusters @Inject()(
    @Named("dpClusterService") val dpClusterService: DpClusterService,
    @Named("skuService") val skuService: SkuService,
    configuration: Configuration,
    ambariService: AmbariService)
    extends Controller {

  def list = Action.async {
    dpClusterService
      .list()
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(dataplaneClusters) => Ok(Json.toJson(dataplaneClusters))
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

  def retrieve(clusterId: String) = Action.async {
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

  def retrieveServices(dpClusterId: String) = Action.async {
    Logger.info("Received retrieve data centre request")
    dpClusterService
      .retrieveServiceInfo(dpClusterId)
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(clusterServices) => Ok(Json.toJson(clusterServices))
      }
  }

  def update(dpClusterId: String) = Action.async(parse.json) { request =>
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

  def delete(clusterId: String) = Action.async {
    Logger.info("Received delete data centre request")
    dpClusterService
      .delete(clusterId)
      .map {
        case Left(errors) =>
          InternalServerError(Json.toJson(errors))
        case Right(dataplaneCluster) => Ok(Json.toJson(dataplaneCluster))
      }
  }

  def ambariCheck(url: String, allowUntrusted: Boolean, behindGateway: Boolean) = AuthenticatedAction.async { request =>
    implicit val token = request.token
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
        case Left(errors) => {
          Logger.error(s"Failed to get cluster details ${errors}")
          throw WrappedErrorsException(errors)
        }
        case Right(dataplaneCluster) => getAmbariServicesInfo(dataplaneCluster)
      }
      .map{ servicesInfo => Ok(Json.toJson(servicesInfo)) }
      .recoverWith {
        case ex: WrappedErrorsException => {
          Logger.error(s"Failed to get services details ${ex.errors}")
          Future.successful(InternalServerError(Json.toJson(ex.errors)))
        }
      }
    }


  private def getAmbariServicesInfo(dpCluster: DataplaneCluster)(implicit token:Option[HJwtToken]): Future[Seq[ServiceInfo]] =  {
    skuService.getAllSkus()
      .map {
        case Left(errors: Errors) =>{
          Logger.error(s"Failed to get dp-dependent services ${errors}")
          throw WrappedErrorsException(errors)
        }
        case Right(skus: Seq[Sku]) => {
          val mandatoryServices = skus.flatMap(sku => configuration.getStringSeq(s"${sku.name}.dependent.services.mandatory").getOrElse(Nil)).distinct
          val optionalServices = skus.flatMap(sku => configuration.getStringSeq(s"${sku.name}.dependent.services.optional").getOrElse(Nil)).distinct
          (mandatoryServices.union(optionalServices)).distinct
        }
      }
      .flatMap { services =>
        ambariService
          .getClusterServices(DpClusterWithDpServices(dataplaneCluster = dpCluster, dpServices = services))
          .map {
            case Left(errors: Errors) =>{
              Logger.error(s"Failed to get services info ${errors}")
              throw WrappedErrorsException(errors)
            }
            case Right(servicesInfo: Seq[ServiceInfo]) => servicesInfo
          }
      }
  }

}
