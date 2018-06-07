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

package com.hortonworks.dataplane.db
import javax.inject.Singleton

import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.db.Webservice.RatingService
import com.typesafe.config.Config
import play.api.libs.json.{JsObject, Json, __}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class RatingServiceImpl(config: Config)(implicit ws: WSClient)
  extends RatingService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  override def add(rating: Rating): Future[Rating] = {
    ws.url(s"$url/ratings")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .post(Json.toJson(rating))
      .map(mapToRating)
  }

  override def get(queryString: String, userId: Long): Future[Rating] = {
    ws.url(s"$url/ratings?$queryString&userId=$userId")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToRating)
  }

  override def getAverage(queryString: String): Future[JsObject] = {
    ws.url(s"$url/ratings/actions/average?$queryString")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }

  override def update(ratingId: String, ratingUserTuple: (Float, Long)): Future[Rating] = {
    ws.url(s"$url/ratings/$ratingId")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .patch(Json.toJson(ratingUserTuple))
      .map(mapToRating)
  }

  override def deleteByObjectRef(objectId: String, objectType: String): Future[String] = {
    ws.url(s"$url/ratings?objectId=${objectId}&objectType=${objectType}")
      .withHeaders("Accept" -> "application/json")
      .delete()
      .map{ res =>
        res.status match {
          case 200 => (res.json \ "results").as[String]
          case _ => {
            val logMsg = s"Db-Client RatingServiceImpl: In deleteByObjectRef method, result status ${res.status}"
            mapResponseToError(res,Option(logMsg))
          }
        }
      }
  }

  import play.api.libs.functional.syntax._

  implicit val tupledRatingWithUserWrite = (
    (__ \ 'rating).write[Float] and
      (__ \ 'userId).write[Long]
    ) tupled

  private def mapToRating(res: WSResponse) = {
    res.status match {
      case 200 => (res.json \ "results").validate[Rating].get
      case _ => {
        val logMsg = s"Db-Client RatingServiceImpl: In mapToRating method, result status ${res.status}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }

  private def mapResultsGeneric(res: WSResponse): JsObject = {
    res.status match {
      case 200 =>
        (res.json \ "results").as[JsObject]
      case _ => {
        val logMsg = s"Db-Client RatingServiceImpl: In mapResultsGeneric method, result status ${res.status}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }
}
