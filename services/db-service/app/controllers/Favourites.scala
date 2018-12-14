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

import com.hortonworks.dataplane.commons.domain.Entities.{Favourite, FavouriteWithTotal}
import domain.FavouriteRepo
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Favourites @Inject()(favouriteRepo: FavouriteRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  def add = Action.async(parse.json) { req =>
    Logger.info("Favourites Controller: Received add favourite request")
    req.body
      .validate[Favourite]
      .map{ favourite =>
        favouriteRepo
          .add(favourite)
          .flatMap { fav =>
            favouriteRepo.getTotal(fav.objectId, fav.objectType).map { total =>
              success(FavouriteWithTotal(fav,total))
            }.recoverWith(apiErrorWithLog(e => Logger.error(s"Favourite Controller: Getting total favourites with object Id ${fav.objectId} and object type ${fav.objectType} failed with message ${e.getMessage}",e)))
          }.recoverWith(apiErrorWithLog(e => Logger.error(s"Favourite Controller: Adding of favourite with user Id ${favourite.userId} , object type ${favourite.objectType} and object Id ${favourite.objectId} failed with message ${e.getMessage}",e)))
      }
      .getOrElse{
        Logger.warn("Favourites Controller: Failed to map request to Favourite entity")
        Future.successful(BadRequest("Favourites Controller: Failed to map request to Favourite entity"))
      }
  }

  def deleteById(favId: Long, userId: Long, objectType: String, objectId: Long) = Action.async { req =>
    Logger.info("Favourites Controller: Received delete favourite by id request")
    val numOfRowsDel = favouriteRepo.deleteById(userId,favId)
    numOfRowsDel.flatMap { i =>
      favouriteRepo.getTotal(objectId, objectType).map { total =>
        success(Json.obj("totalFavCount"-> total, "rowsDeleted"-> i))
      }.recoverWith(apiErrorWithLog(e => Logger.error(s"Favourite Controller: Getting total favourites with object Id $objectId and object type $objectType failed with message ${e.getMessage}",e)))
    }.recoverWith(apiErrorWithLog(e => Logger.error(s"Favourites Controller: Deleting favourite with favourite Id $favId failed with message ${e.getMessage}",e)))
  }

  def deleteByObjectRef(objectId: Long, objectType: String) = Action.async { req =>
    Logger.info("Favourites Controller: Received delete favourite by object Id request")
    val numOfRowsDel = favouriteRepo.deleteByobjectRef(objectId, objectType)
    numOfRowsDel.map(i => success(Json.obj("rows Deleted"->i))).recoverWith(apiErrorWithLog(e => Logger.error(s"Favourites Controller: Deleting favourites with object Id $objectId  and object Type $objectType failed with message ${e.getMessage}",e)))
  }

}
