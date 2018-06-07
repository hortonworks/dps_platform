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

import domain.RatingRepo
import com.hortonworks.dataplane.commons.domain.Entities.Rating
import play.api.Logger
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Ratings @Inject()(ratingRepo: RatingRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._


  private def isNumeric(str: String) = scala.util.Try(str.toLong).isSuccess

  def add = Action.async(parse.json) { req =>
    req.body
      .validate[Rating]
      .map { rating =>
        ratingRepo
          .add(rating)
          .map { rt =>
            success(rt)
          }.recoverWith(apiErrorWithLog(e => Logger.error(s"Ratings Controller: Adding of rating $rating failed with message ${e.getMessage}",e)))
      }
      .getOrElse{
        Logger.warn("Ratings Controller: Failed to map request to Rating entity")
        Future.successful(BadRequest("Failed to map request to Rating entity"))
      }
  }

  def getAverage(objectId: Long, objectType: String) = Action.async { req =>
    Logger.info("dp-service Ratings Controller: Received get average rating request")
    ratingRepo.getAverage(objectId,objectType)
      .map{ avgAndTotalVotes =>
        success(Json.obj("average" -> avgAndTotalVotes._1, "votes" -> avgAndTotalVotes._2))
      }.recoverWith(apiErrorWithLog(e => Logger.error(s"Ratings Controller: Get average with object id $objectId & object type $objectType failed with message ${e.getMessage}",e)))
  }

  def update(id: String) = Action.async(parse.json) { req =>
    req.body
      .validate[(Float, Long)]
      .map { ratingTuple =>
        if(!isNumeric(id)) Future.successful(BadRequest)
        else{
          ratingRepo
            .update(id.toLong,ratingTuple._2,ratingTuple._1)
            .map { rt =>
              success(rt)
            }.recoverWith(apiErrorWithLog(e => Logger.error(s"Ratings Controller: Update rating with rating id $id and rating object $ratingTuple failed with message ${e.getMessage}",e)))
        }
      }
      .getOrElse{
        Logger.warn("Ratings Controller: Failed to map request to Rating tuple")
        Future.successful(BadRequest("Failed to map request to Rating tuple"))
      }
  }

  def get(objectId: Long, objectType: String, userId: Long) = Action.async { req =>
    Logger.info("dp-service Ratings Controller: Received get rating request")
    ratingRepo.get(userId,objectId,objectType)
      .map{ rt =>
        success(rt)
      }.recoverWith(apiErrorWithLog(e => Logger.error(s"Ratings Controller: Get rating with user id $userId , object id $objectId and object type $objectType failed with message ${e.getMessage}",e)))
  }

  def delete(objectId: Long, objectType: String) = Action.async { req =>
    Logger.info("db-service Ratings Controller: Received delete ratings by object reference request")
    val numOfRowsDel = ratingRepo.deleteByObjectRef(objectId,objectType)
    numOfRowsDel.map(i => success(s"Success: ${i} row/rows deleted"))
      .recoverWith(apiErrorWithLog(e => Logger.error(s"Ratings Controller: Delete rating with object id $objectId and object type $objectType failed with message ${e.getMessage}",e)))
  }

  implicit val tupledRatingWithUserReads = (
    (__ \ 'rating).read[Float] and
      (__ \ 'userId).read[Long]
    ) tupled


}
