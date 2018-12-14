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

import com.hortonworks.dataplane.commons.domain.Entities.Bookmark
import domain.BookmarkRepo
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Bookmarks @Inject()(bookmarkRepo: BookmarkRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def add = Action.async(parse.json) { req =>
    Logger.info("Bookmarks Controller: Received add bookmark request")
    req.body
      .validate[Bookmark]
      .map { bookmark =>
        bookmarkRepo
          .add(bookmark)
          .map { bm =>
            success(bm)
          }.recoverWith(apiErrorWithLog(e => Logger.error(s"Bookmarks Controller: Adding of bookmark with user Id ${bookmark.userId} , object type ${bookmark.objectType} and object Id ${bookmark.objectId} failed with message ${e.getMessage}",e)))
      }
      .getOrElse{
        Logger.warn("Bookmarks Controller: Failed to map request to Bookmark entity")
        Future.successful(BadRequest("Bookmarks Controller: Failed to map request to Bookmarks entity"))
      }
  }

  def deleteById(bmId: Long, userId: Long) = Action.async { req =>
    Logger.info("Bookmarks Controller: Received delete bookmark by id request")
    val numOfRowsDel = bookmarkRepo.deleteById(userId,bmId)
    numOfRowsDel.map(i => success(Json.obj("rows Deleted"->i))).recoverWith(apiErrorWithLog(e => Logger.error(s"Bookmarks Controller: Deleting bookmark with bookmark Id $bmId failed with message ${e.getMessage}",e)))
  }

  def deleteByObjectRef(objectId: Long, objectType: String)= Action.async { req =>
    Logger.info("Bookmarks Controller: Received delete bookmark by object reference request")
    val numOfRowsDel = bookmarkRepo.deleteByobjectRef(objectId, objectType)
    numOfRowsDel.map(i => success(Json.obj("rows Deleted"->i))).recoverWith(apiErrorWithLog(e => Logger.error(s"Bookmarks Controller: Deleting bookmarks with ojbect Id $objectId and object type $objectType failed with message ${e.getMessage}",e)))
  }

}
