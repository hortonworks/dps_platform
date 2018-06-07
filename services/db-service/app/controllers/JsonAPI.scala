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

import com.hortonworks.dataplane.commons.domain.Entities.{Error, Errors, WrappedErrorException}
import domain.API.{AlreadyExistsError, EntityNotFound, UpdateError}
import org.postgresql.util.PSQLException
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.Json.JsValueWrapper
import play.api.mvc.{Controller, Result}

import scala.concurrent.Future

trait JsonAPI extends Controller {

  import com.hortonworks.dataplane.commons.domain.JsonFormatters._
  val pgErrors = Map("23503" -> CONFLICT,"23514" -> BAD_REQUEST,"23505" -> CONFLICT,"23502" -> BAD_REQUEST,"23000"-> BAD_REQUEST)

  def success(data: JsValueWrapper) = Ok(Json.obj("results" -> data))
  def entityCreated(data: JsValueWrapper) = Created(Json.obj("results" -> data))

  val notFound = NotFound(Json.toJson(wrapErrors(404,"Not found")))

  def linkData(data: JsValueWrapper, links: Map[String, String] = Map()) =
    Json.obj("data" -> data, "links" -> links)

  def apiErrorWithLog(logBlock : (Throwable) => Unit = defaultLogMessage): PartialFunction[Throwable, Future[Result]] = {
    case e: Throwable =>{
      logBlock(e)
      apiError.apply(e)
    }
  }

  private def defaultLogMessage =
    (e : Throwable) => Logger.error(e.getMessage, e)

  val apiError: PartialFunction[Throwable, Future[Result]] = {
    case e: PSQLException =>
      Future.successful {
        val status = pgErrors.get(e.getSQLState).getOrElse(500)
        val errors = wrapErrors(status, e.getMessage)
        Status(status)(Json.toJson(errors))
      }
    case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
    case e: EntityNotFound => Future.successful(notFound)
    case e: UpdateError => Future.successful(NoContent)
    case e: AlreadyExistsError => Future.successful(Conflict)
    case e: Exception =>
      Future.successful(InternalServerError(Json.toJson(wrapErrors(500, e.getMessage))))
  }

  private def wrapErrors(code: Int, message: String): Errors = {
    Errors(Seq(Error(code, message)))
  }


}
