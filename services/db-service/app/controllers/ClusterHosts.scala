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

import com.hortonworks.dataplane.commons.domain.Entities.ClusterHost
import domain.API.clusters
import domain.ClusterHostRepo
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClusterHosts @Inject()(clusterHostRepo: ClusterHostRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  private def makeLink(c: ClusterHost) = {
    Map("cluster" -> s"${clusters}/${c.clusterId}")
  }

  def allWithCluster(clusterId:Long) = Action.async {
    clusterHostRepo.allWithCluster(clusterId).map(cs => success(cs.map(c=>linkData(c,makeLink(c))))).recoverWith(apiError)
  }

  def loadWithCluster(clusterId:Long, hostId:Long) = Action.async {
    clusterHostRepo.findByClusterAndHostId(clusterId,hostId).map { co =>
      co.map { c =>
        success(linkData(c, makeLink(c)))
      }
        .getOrElse(notFound)
    }.recoverWith(apiError)
  }

  def find(clusterId:Long,host:String) = Action.async {
    clusterHostRepo.findByHostAndCluster(clusterId,host).map { co =>
      co.map { c =>
        success(linkData(c, makeLink(c)))
      }
        .getOrElse(notFound)
    }.recoverWith(apiError)
  }


  def delete(clusterId: Long, hostId:Long) = Action.async { req =>
    val future = clusterHostRepo.deleteById(clusterId, hostId)
    future.map(i => success(i)).recoverWith(apiError)
  }


  def add = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterHost]
      .map { cl =>
        val created = clusterHostRepo.insert(cl)
        created
          .map(c => success(linkData(c, makeLink(c))))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def addOrUpdate = Action.async(parse.json) { req =>
    req.body
      .validate[ClusterHost]
      .map { cl =>
        val created = clusterHostRepo.upsert(cl)
        created
          .map(c => success(Json.obj("updated" -> c)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }


}
