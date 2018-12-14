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

import domain.{CommentRepo, PaginatedQuery, SortQuery}
import com.hortonworks.dataplane.commons.domain.Entities.Comment
import play.api.Logger
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Comments @Inject()(commentRepo: CommentRepo)(implicit exec: ExecutionContext)
  extends JsonAPI {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._

  //val Logger = Logger(this.getClass)
  def addComment = Action.async(parse.json) { req =>
    Logger.info("Comments Controller: Received add Comment request")
    req.body
      .validate[Comment]
      .map { comment =>
        commentRepo
          .add(comment)
          .map { cmnt =>
            success(cmnt)
          }.recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Adding of Comment $comment failed with message ${e.getMessage}",e)))
      }
      .getOrElse{
        Logger.warn("Comments Controller: Failed to map request to Comment entity")
        Future.successful(BadRequest)
      }
  }

  private def isNumeric(str: String) = scala.util.Try(str.toLong).isSuccess

  private def getPaginatedQuery(req: Request[AnyContent]): Option[PaginatedQuery] = {
    val offset = req.getQueryString("offset")
    val size = req.getQueryString("size")

    if (size.isDefined && offset.isDefined) {
      Some(PaginatedQuery(offset.get.toInt, size.get.toInt, None))
    } else None

  }

  def getCommentByObjectRef(objectId: Long, objectType: String) = Action.async { req =>
    Logger.info("Comments Controller: Received get comment by object-reference request")
    commentRepo.findByObjectRef(objectId,objectType,getPaginatedQuery(req))
      .map{ commentswithuser =>
        success(commentswithuser)
      }.recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Getting Comments with object Id $objectId and object Type $objectType failed with message ${e.getMessage}", e)))
  }

  def getByParentId(parentId: Long) = Action.async { req =>
    Logger.info("Comments Controller: Received get comment by parent Id request")
    commentRepo.findByParentId(parentId)
      .map{ commentswithuser =>
        success(commentswithuser)
      }.recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Getting Comments with parent Id $parentId failed with message ${e.getMessage}", e)))
  }

  def getCommentsCount(objectId: Long, objectType: String) = Action.async { req =>
    Logger.info("Comments Controller: Received get CommentsCount by object Id and object type request")
    commentRepo.getCommentsCount(objectId, objectType)
      .map{ commentsCount =>
        success(Json.obj("totalComments"->commentsCount))
      }.recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Getting Comments count with object Id $objectId and object type $objectType failed with message ${e.getMessage}", e)))
  }

  def delete(objectId: Long, objectType: String) = Action.async { req =>
    Logger.info("Comments Controller: Received delete comment by object-reference request")
    val numOfRowsDel = commentRepo.deleteByObjectRef(objectId,objectType)
    numOfRowsDel.map(i => success(s"Success: ${i} row/rows deleted"))
      .recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Deleting Comments with object Id $objectId and object Type $objectType failed with message ${e.getMessage}",e)))
  }

  def deleteById(id: String, userId: Long) = Action.async { req =>
    Logger.info("Comments Controller: Received delete comment by id request")
    if(!isNumeric(id)) {
      Logger.warn(s"Comments Controller: Not a valid Comment Id $id")
      Future.successful(BadRequest)
    }
    else{
      val commentId = id.toLong
      val comment = commentRepo.getById(commentId, userId)
      val numDel = comment.flatMap { cmnt =>
        if(cmnt.parentCommentId.isDefined){
          commentRepo.deleteReplyCommentById(commentId,userId, cmnt.parentCommentId.get)
        }else {
          commentRepo.deleteCommentById(commentId,userId)
        }
      }
      numDel.map(i => success(s"Success: ${i} row/rows deleted"))
        .recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Deleting Comment with comment Id $commentId failed with message ${e.getMessage}",e)))
    }
  }

  def update(id: String) = Action.async(parse.json) { req =>
    Logger.info("Comments Controller: Received update comment request")
    req.body
      .validate[(String)]
      .map { case (commentText) =>
        commentRepo
          .update(commentText,id.toLong)
          .map { cmnt =>
            success(cmnt)
          }.recoverWith(apiErrorWithLog(e => Logger.error(s"Comments Controller: Updating comment with comment id $id to $commentText failed with message ${e.getMessage}", e)))
      }
      .getOrElse{
        Logger.warn("Comments Controller: Failed to map request to Comment Text")
        Future.successful(BadRequest)
      }
  }

  implicit val tupledCommentTextReads = ((__ \ 'commentText).read[String])
}
