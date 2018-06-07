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

import javax.inject._

import domain.API.{users, dpClusters}
import domain.ClusterRepo
import com.hortonworks.dataplane.commons.domain.Entities.{Cluster}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DiscoveredClusters @Inject()(clusterRepo: ClusterRepo)(
    implicit exec: ExecutionContext)
    extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def all(dpClusterId: Option[Long]) =
    Action.async {
      clusterRepo
        .all(dpClusterId)
        .map(clusters => success(clusters.map(c => linkData(c, makeLink(c)))))
        .recoverWith(apiError)
    }

  def getByDpClusterId(dpClusterId: Long) = Action.async {
    clusterRepo
      .findByDpClusterId(dpClusterId)
      .map(clusters => success(clusters.map(c => linkData(c, makeLink(c)))))
      .recoverWith(apiError)
  }

  private def makeLink(c: Cluster) = {
    Map("dpCluster" -> s"${dpClusters}/${c.dataplaneClusterId.get}",
        "user" -> s"${users}/${c.userid.get}")
  }

  def load(clusterId: Long) = Action.async {
    clusterRepo
      .findById(clusterId)
      .map { co =>
        co.map { c =>
            success(linkData(c, makeLink(c)))
          }
          .getOrElse(NotFound)
      }
      .recoverWith(apiError)
  }

  def delete(clusterId: Long) = Action.async { req =>
    val future = clusterRepo.deleteById(clusterId)
    future.map(i => success(i)).recoverWith(apiError)
  }

  def add = Action.async(parse.json) { req =>
    req.body
      .validate[Cluster]
      .map { cl =>
        val created = clusterRepo.insert(cl)
        created
          .map(c => success(linkData(c, makeLink(c))))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

}
