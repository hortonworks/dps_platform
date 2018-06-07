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

import com.hortonworks.dataplane.commons.domain.Entities.{
  DataplaneCluster,
  Location
}
import domain.DpClusterRepo
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DpClusters @Inject()(dpClusterRepo: DpClusterRepo)(
    implicit exec: ExecutionContext)
    extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._
  import domain.API._

  def all = Action.async { request =>
    if (!request.getQueryString("ambariUrl").isEmpty) {
      val ambariUrl = request.getQueryString("ambariUrl").get
      dpClusterRepo
        .findByAmbariUrl(ambariUrl)
        .map { _.map { dl => success(linkData(dl, makeLink(dl)))}.getOrElse(NotFound) }
        .recoverWith(apiError)
    } else {
      dpClusterRepo.all
        .map { dl => success(dl.map(d => linkData(d, makeLink(d)))) }
        .recoverWith(apiError)
    }
  }

  def addLocation = Action.async(parse.json) { req =>
    req.body
      .validate[Location]
      .map { l =>
        val created = dpClusterRepo.addLocation(l)
        created.map(r => success(r)).recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def getLocations(query: Option[String]) = Action.async {
    dpClusterRepo.getLocations(query).map(success(_)).recoverWith(apiError)
  }

  def loadLocation(id: Long) = Action.async {
    dpClusterRepo
      .getLocation(id)
      .map(l => l.map(success(_)).getOrElse(NotFound))
      .recoverWith(apiError)
  }

  def updateStatus = Action.async(parse.json) { req =>
    req.body
      .validate[DataplaneCluster]
      .map { dl =>
        dpClusterRepo
          .updateStatus(dl)
          .map(c => success(Map("updated" -> c)))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def deleteLocation(id: Long) = Action.async {
    dpClusterRepo.deleteLocation(id).map(success(_)).recoverWith(apiError)
  }

  private def makeLink(d: DataplaneCluster) = {
    Map("createdBy" -> s"${users}/${d.createdBy.get}",
        "location" -> s"${locations}/${d.location.getOrElse(0)}")
  }

  def load(dpClusterId: Long) = Action.async {
    dpClusterRepo
      .findById(dpClusterId)
      .map { dlo =>
        dlo
          .map { dl =>
            success(linkData(dl, makeLink(dl)))
          }
          .getOrElse(NotFound)
      }
      .recoverWith(apiError)
  }

  def delete(dpClusterId: Long) = Action.async { req =>
    val future = dpClusterRepo.deleteCluster(dpClusterId)
    future.map(i => success(true)).recoverWith(apiError)
  }

  def update = Action.async(parse.json) { req =>
    req.body
      .validate[DataplaneCluster]
      .map { dl =>
        val created = dpClusterRepo.update(dl)
        created
          .map {
            case d @ (_, false) => success(linkData(d._1, makeLink(d._1)))
            case d @ (_, true)  => entityCreated(linkData(d._1, makeLink(d._1)))
          }
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def add = Action.async(parse.json) { req =>
    req.body
      .validate[DataplaneCluster]
      .map { dl =>
        val created = dpClusterRepo.insert(dl)
        created
          .map(d => success(linkData(d, makeLink(d))))
          .recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

}
