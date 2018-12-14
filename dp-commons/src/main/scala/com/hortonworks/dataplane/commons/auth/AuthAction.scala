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

package com.hortonworks.dataplane.commons.auth

import java.security.cert.X509Certificate

import com.hortonworks.dataplane.commons.domain.Entities._
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.codec.binary.Base64
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import collection.JavaConverters._

object AuthenticatedAction extends ActionBuilder[AuthenticatedRequest] {

  val config = ConfigFactory.load()

  val gatewayUserTokenKey = "X-DP-User-Info"
  val gatewayTokenKey = "X-DP-Token-Info"

  def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[A]) => Future[Result]) = {

    request.headers
      .get(gatewayUserTokenKey)
      .flatMap { egt =>
        val encodedGatewayToken: String = egt
        val userJsonString: String = new String(Base64.decodeBase64(encodedGatewayToken))

        Json.parse(userJsonString).asOpt[UserContext]
      }
      .map(Success(_))
      .getOrElse(Failure(WrappedErrorException(Error(401, "Unauthorized. Unable to parse user context.", "core.unauthorized"))))
      .flatMap(userContext => buildAuthorizedUser(request, userContext))
      .map(user => block(setUpAuthContext(request, user)))
      .recover{
        case ex: WrappedErrorException => Future.successful(Results.Status(ex.error.status)(Json.toJson(ex.error)))
        case ex: Exception => Future.successful(Results.Status(401)(Json.toJson(Error(401, "Unauthorized.", "core.unauthorized"))))
      }
      .getOrElse(Future.successful(Results.Status(401)(Json.toJson(Error(401, "Unauthorized.", "core.unauthorized")))))
  }

  private def buildAuthorizedUser[A](request: Request[A], userContext: UserContext): Try[User] = {
    val isAuthorized = Try(config.getConfigList("dp.authorization"))
      .map {
        _.asScala
          .filter{ config: Config =>
            request.path.startsWith(config.getString("path")) && (config.getStringList("roles").contains("*") || userContext.roles.intersect(config.getStringList("roles").asScala).nonEmpty)
          }.nonEmpty
      }
      .getOrElse(true) // if config path is missing

    isAuthorized match {
      case true => Success(
        User(id = userContext.id,
          username = userContext.username,
          password = "",
          displayname = if (userContext.display.isDefined) userContext.display.get else userContext.username,
          avatar = userContext.avatar
        )
      )
      case false => Failure(WrappedErrorException(Error(401, "Unauthorized. User does not have acces to this route.", "core.unauthorized")))
    }

  }

  private def setUpAuthContext[A](request: Request[A], user: User) = {
    request.headers
      .get(gatewayTokenKey)
      .map { tokenHeader => AuthenticatedRequest[A](user, request, Some(HJwtToken(tokenHeader))) }
      .getOrElse(AuthenticatedRequest[A](user, request))
  }
}

trait AuthenticatedRequest[+A] extends Request[A] {
  val token: Option[HJwtToken]
  val user: User
}

object AuthenticatedRequest {
  def apply[A](u: User, r: Request[A], t: Option[HJwtToken] = None) =
    new AuthenticatedRequest[A] {
      def id = r.id

      def tags = r.tags

      def uri = r.uri

      def path = r.path

      def method = r.method

      def version = r.version

      def queryString = r.queryString

      def headers = r.headers

      lazy val remoteAddress = r.remoteAddress

      def username = None

      val body = r.body
      val user = u
      val token = t

      override def secure: Boolean = r.secure

      override def clientCertificateChain: Option[Seq[X509Certificate]] =
        r.clientCertificateChain
    }
}
