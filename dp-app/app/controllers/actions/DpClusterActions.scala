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

package controllers.actions

import javax.inject.Inject

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.hortonworks.dataplane.commons.domain.Ambari.ServiceInfo
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{ClusterService, ClusterSkuService, DpClusterService, SkuService}
import models.WrappedErrorsException
import play.api.Logger
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Controller}
import services.{AmbariService, ConsulHealthService, PluginManifestService}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Left

class DpClusterActions @Inject()(
    @Named("dpClusterService") val dpClusterService: DpClusterService,
    @Named("clusterService") val clusterService: ClusterService,
    @Named("skuService") val skuService: SkuService,
    @Named("pluginManifestService") val pluginService: PluginManifestService,
    @Named("clusterSkuService") val clusterSkuService: ClusterSkuService,
    @Named("healthService") val healthService: ConsulHealthService,
    ambariService: AmbariService) extends Controller {

  def listWithClusters(plugin: Option[String]):
    Action[AnyContent] = AuthenticatedAction.async { request =>

    Logger.info("list lakes with clusters")

    retrieveLakes()
      .flatMap { lakes =>
        plugin.map { plugin =>
          val futures = lakes.map { dpCluster =>
            clusterSkuService
              .getClusterSku(pluginService.retrieve(plugin).get.id, dpCluster.id.get)
              .map(_.map(_ => dpCluster))
          }
          Future.sequence(futures).map(_.collect { case Some(lake) => lake })
        }.getOrElse(Future.successful(lakes))
      }
      .flatMap { lakes =>
        val lakeFutures =
          lakes
            .map { cLake =>
              for {
                lake <- Future.successful(cLake)
                clusters <- retrieveClusters(cLake.id.get)
              } yield Json.obj("data" -> lake, "clusters" -> clusters)
            }
        Future.sequence(lakeFutures)
      }
      .map(lakes => Ok(Json.toJson(lakes)))
      .recover {
        case WrappedErrorsException(ex) => InternalServerError(Json.toJson(ex.errors))
      }
  }

  private def retrieveLakes(): Future[Seq[DataplaneCluster]] = {
    dpClusterService
      .list()
      .flatMap({
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(lakes) => Future.successful(lakes)
      })
  }

  private def retrieveClusters(lakeId: Long): Future[Seq[Cluster]] = {
    clusterService
      .getLinkedClusters(lakeId)
      .flatMap({
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(clusters) => Future.successful(clusters)
      })
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
