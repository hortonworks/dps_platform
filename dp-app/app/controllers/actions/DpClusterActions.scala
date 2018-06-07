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
import com.hortonworks.dataplane.commons.domain.Entities.{Cluster, DataplaneCluster}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{ClusterService, DpClusterService}
import models.{JsonResponses, WrappedErrorsException}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DpClusterActions @Inject()(
    @Named("dpClusterService") val dpClusterService: DpClusterService,
    @Named("clusterService") val clusterService: ClusterService) extends Controller {

  def listWithClusters() = Action.async {
    Logger.info("list lakes with clusters")

    retrieveLakes()
      .flatMap({ lakes =>
        val lakeFutures =
          lakes
            .map({ cLake =>
              for {
                lake <- Future.successful(cLake)
                clusters <- retrieveClusters(cLake.id.get)
              } yield
                Json.obj(
                  "data" -> lake,
                  "clusters" -> clusters
                )
            })
        Future.sequence(lakeFutures)
      })
      .map({ lakes =>
        Ok(Json.toJson(lakes))
      })
      .recover {
        case WrappedErrorsException(ex) =>
          InternalServerError(Json.toJson(ex.errors))
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

}
