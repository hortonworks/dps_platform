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

import com.hortonworks.dataplane.commons.domain.Entities.Workspace
import domain.{API, WorkspaceRepo}
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Workspaces @Inject()(wr: WorkspaceRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  private def makeLink(w: Workspace) = {
    Map(
      "user" -> s"${API.users}/${w.createdBy.get}",
      "cluster" -> s"${API.clusters}/${w.source}"
    )
  }

  def all = Action.async {
    wr.all.map {
      workspaces =>
        success(workspaces.map(
          workspace => (linkData(workspace, makeLink(workspace)))
        ))
    }.recoverWith(apiError)
  }

  def allWithDetails = Action.async {
    wr.allWithDetails().map {
      workspaces =>
        success(workspaces.map(
          workspace => (linkData(workspace, makeLink(workspace.workspace)))
        ))
    }.recoverWith(apiError)
  }

  def insert = Action.async(parse.json) { req =>
    req.body
      .validate[Workspace]
      .map { workspace =>
        wr
          .insert(workspace)
          .map { c =>
            success(linkData(c, makeLink(c)))
          }.recoverWith(apiError)
      }
      .getOrElse(Future.successful(BadRequest))
  }

  def load(workspaceId: Long) = Action.async {
    wr.findById(workspaceId).map { uo =>
      uo.map { c =>
        success(linkData(c, makeLink(c)))
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def loadByName(name: String) = Action.async {
    wr.findByName(name).map { uo =>
      uo.map { c =>
        success(linkData(c, makeLink(c)))
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def loadByNameWithDetails(name: String) = Action.async {
    wr.findByNameWithDetails(name).map { uo =>
      uo.map { c =>
        success(linkData(c, makeLink(c.workspace)))
      }
        .getOrElse(NotFound)
    }.recoverWith(apiError)
  }

  def loadByUser(userId: Long) = Action.async {
    wr.findByUserId(userId).map { uo =>
      success(Json.toJson(uo))
    }.recoverWith(apiError)
  }

  def delete(workspaceId: Long) = Action.async { req =>
    val future = wr.deleteById(workspaceId)
    future.map(i => success(i)).recoverWith(apiError)
  }

  def deleteByName(name: String) = Action.async { req =>
    val future = wr.deleteByName(name)
    future.map(i => success(i)).recoverWith(apiError)
  }

}
