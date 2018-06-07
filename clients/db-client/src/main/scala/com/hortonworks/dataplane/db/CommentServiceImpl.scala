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

import java.time.{LocalDateTime, ZoneId}
import javax.inject.Singleton

import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.db.Webservice.CommentService
import com.typesafe.config.Config
import play.api.Logger
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class CommentServiceImpl(config: Config)(implicit ws: WSClient)
  extends CommentService {

  private def url =
    Option(System.getProperty("dp.services.db.service.uri"))
      .getOrElse(config.getString("dp.services.db.service.uri"))

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  override def add(comment: Comment): Future[CommentWithUser] = {
    ws.url(s"$url/comments")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .post(Json.toJson(comment))
      .map(mapToCommentWithUser)
  }

  override def deleteById(commentId: String, userId: Long): Future[String] = {
    ws.url(s"$url/comments/$commentId?userId=${userId}")
      .withHeaders("Accept" -> "application/json")
      .delete()
      .map{ res =>
        res.status match {
          case 200 => (res.json \ "results").validate[String].get
          case _ =>{
            val logMsg = s"Db-Client CommentServiceImpl: In deleletById method , result status ${res.status} with comment Id $commentId and userId $userId"
            mapResponseToError(res, Option(logMsg))
          }
        }
      }
  }

  override def deleteByObjectRef(objectId: String, objectType: String): Future[String] = {
    ws.url(s"$url/comments?objectId=${objectId}&objectType=${objectType}")
      .withHeaders("Accept" -> "application/json")
      .delete()
      .map{ res =>
        res.status match {
          case 200 => (res.json \ "results").validate[String].get
          case _ =>
            val logMsg = s"Db-Client CommentServiceImpl: In deleteByObjectRef, result status ${res.status} with object Id $objectId and object type $objectType"
            mapResponseToError(res,Option(logMsg))
        }
      }
  }

  override def update(commentText: String, commentId: String): Future[CommentWithUser] = {
    ws.url(s"$url/comments/$commentId")
      .withHeaders(
        "Content-Type" -> "application/json",
        "Accept" -> "application/json"
      )
      .patch(Json.toJson(commentText))
      .map(mapToCommentWithUser)
  }

  override def getByObjectRef(queryString: String): Future[Seq[CommentWithUser]] = {
    ws.url(s"$url/comments?$queryString")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCommentWithUsers)
  }

  override def getByParentId(parentId: String, queryString: String): Future[Seq[CommentWithUser]] = {
    ws.url(s"$url/comments/$parentId/replies?$queryString")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapToCommentWithUsers)
  }

  override def getCommentsCount(objectId: Long, objectType: String): Future[JsObject] = {
    ws.url(s"$url/comments/actions/count?objectId=$objectId&objectType=$objectType")
      .withHeaders("Accept" -> "application/json")
      .get()
      .map(mapResultsGeneric)
  }


  private def mapToCommentWithUser(res: WSResponse) = {
    res.status match {
      case 200 => (res.json \ "results").validate[CommentWithUser].get
      case _ => {
        val logMsg = s"Db-Client CommentServiceImpl: In mapToCommentWithUser method, result status ${res.status}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }

  private def mapToCommentWithUsers(res: WSResponse) = {
    res.status match {
      case 200 =>
        (res.json \ "results").validate[Seq[CommentWithUser]].getOrElse(Seq())
      case _ => {
        val logMsg = s"Db-Client CommentServiceImpl: In mapToCommentWithUsers method, result status ${res.status}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }

  private def mapResultsGeneric(res: WSResponse): JsObject = {
    res.status match {
      case 200 =>
        (res.json \ "results").as[JsObject]
      case _ => {
        val logMsg = s"Db-Client CommentServiceImpl: In mapResultsGeneric method, result status ${res.status}"
        mapResponseToError(res,Option(logMsg))
      }
    }
  }

}
