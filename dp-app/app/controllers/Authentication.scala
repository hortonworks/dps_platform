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

import javax.inject.Inject

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.UserService
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import models.JsonFormats._
import models.RequestSyntax.ChangeUserPassword
import models.Formatters._
import models.JsonFormatters._
import models.{Credential, JsonResponses, WrappedErrorsException}
import org.apache.commons.codec.binary.Base64
import org.mindrot.jbcrypt.BCrypt
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._
import play.api.Logger
import play.api.Configuration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class Authentication @Inject()(@Named("userService") val userService: UserService,
                               configuration: Configuration)
    extends Controller {

  val HEADER_FOR_GATEWAY_USER_CTX = "X-DP-User-Info"

  def signIn = Action.async(parse.json) { request =>
    request.body
      .validate[Credential]
      .map { credential =>
        val username = credential.username
        val password = credential.password
        for {
          userOp <- userService.loadUser(username)
          roles <- userService.getUserRoles(username)
        } yield {
          getResponse(password, userOp, roles)
        }
      }
      .getOrElse(Future.successful(
        BadRequest(JsonResponses.statusError("Cannot parse user request"))))
  }

  def userById(userId: String) = AuthenticatedAction.async {
    userService.loadUserById(userId).map{
      case Left(errors) => InternalServerError(Json.toJson(errors))
      case Right(user) => Ok(Json.toJson(user))
    }
  }

  def userDetail = AuthenticatedAction.async { request =>
    request.headers
      .get(HEADER_FOR_GATEWAY_USER_CTX)
      .map { egt =>
        val encodedGatewayToken: String = egt
        val userJsonString: String = new String(Base64.decodeBase64(encodedGatewayToken))
        Json.parse(userJsonString)
          .validate[UserContext] match {
            case JsSuccess(userContext, _) => Future.successful(Ok(Json.toJson(userContext)))
            case JsError(error) =>
              Logger.error(s"Error while parsing Gateway token. $error")
              Future.successful(Unauthorized)
            }
      }
      .getOrElse(Future.successful(Unauthorized))
  }

  def changePassword = AuthenticatedAction.async(parse.json) { request =>
    request.body
      .validate[ChangeUserPassword]
      .map { changeUserPasswordRequest =>
        getUserByUsername(request.user.username)
          .map( user => {
            checkPassword(changeUserPasswordRequest.password, user.password)
            user.copy(password = BCrypt.hashpw(changeUserPasswordRequest.nextPassword, BCrypt.gensalt()))
          })
          .flatMap (user => userService.updateUser(user))
          .map {
            case Left(errors) => {
              Logger.error(s"user fetch issue while changing password for '${request.user.username}': {${errors}")
              InternalServerError(Json.toJson(errors))
            }
            case Right(user) => Ok(Json.toJson(user)).withHeaders(("X-Invalidate-Token", "true"))
          }
          .recoverWith {
            case ex: WrappedErrorsException => Future.successful(InternalServerError(Json.toJson(ex.errors)))
          }
      }
      .getOrElse(Future.successful(BadRequest(JsonResponses.statusError("Cannot parse user request"))))
  }

  private def getRoles(roles: Either[Errors, UserRoles]) = {
    if (roles.isRight) {
      roles.right.get.roles
    } else
      Nil
  }

  private def getResponse(password: String,
                          userOp: Either[Errors, User],
                          roles: Either[Errors, UserRoles]) = {

    userOp match {
      case Left(errors) =>
        Unauthorized(
          JsonResponses.statusError(
            s"Cannot find user for request ${Json.toJson(errors)}"))
      case Right(user) =>
        Try(BCrypt.checkpw(password, user.password))
          .getOrElse(false) match {
            case true =>
              val orElse = getRoles(roles)
              Ok(
                Json.obj(
                  "id" -> user.username,
                  "avatar" -> user.avatar,
                  "display" -> user.displayname
                ))
            case false =>
              Unauthorized(
                JsonResponses.statusError(s"The user cannot be verified"))
          }
    }
  }

  private def getUserByUsername(username: String): Future[User] = {
    userService
      .loadUser(username)
      .map {
        case Left(errors) => throw WrappedErrorsException(errors)
        case Right(user) => user
      }
  }

  private def checkPassword(password: String, hashedPassword: String): Boolean = {
    if(!Try(BCrypt.checkpw(password, hashedPassword)).getOrElse(false)) {
      val errors = Errors(Seq(Error(418, "Current password is incorrect.")))
      throw WrappedErrorsException(errors)
    }
    return true;
  }
}
