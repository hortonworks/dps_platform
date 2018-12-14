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

import com.google.inject.name.Named
import com.hortonworks.dataplane.commons.domain.Entities.{UserContext, WrappedErrorException}
import javax.inject.Inject
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc._
import services.{ConsulHealthService, PluginManifestService}
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import models.JsonResponses
import org.apache.commons.codec.binary.Base64
import play.api.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class PluginManifests @Inject()(@Named("pluginManifestService") val pluginService: PluginManifestService,
                                @Named("healthService") val healthService: ConsulHealthService)
  extends Controller {

  val HEADER_FOR_GATEWAY_USER_CTX = "X-DP-User-Info"

  def list(allowed: Option[Boolean], installed: Option[Boolean], healthy: Option[Boolean]) = Action.async { request =>
    var plugins = pluginService.list()

    val futures = plugins
      .map { cPlugin =>
        healthService
          .getServiceHealth(cPlugin.name)
          .map(status => Some(cPlugin, status))
          .recover {
            case th: Throwable =>
              Logger.error("Unable to fetch plugin from Consul", th)
              None
          }
      }

    Future
      .sequence(futures)
      .map(_.filterNot(_.isEmpty).map(_.get))
      .map { plugins =>
        plugins
          .filter { case (cPlugin, cStatus) =>
            var isOkay = true

            installed.foreach { installed =>
              isOkay = isOkay && installed == cStatus.installed
            }

            healthy.foreach { healthy =>
              isOkay = isOkay && healthy == cStatus.healthy
            }

            isOkay
          }
          .map(_._1)
      }
      .map { plugins =>
        val oRoles = request
          .headers
          .get(HEADER_FOR_GATEWAY_USER_CTX)
          .flatMap { encodedGatewayToken =>
            val userJsonString: String = new String(Base64.decodeBase64(encodedGatewayToken))
            Json.parse(userJsonString).asOpt[UserContext]
          }
          .map(_.roles)
          .getOrElse(Nil)

        allowed.map(allowed => plugins.filter(_.require_platform_roles.intersect(oRoles).nonEmpty == allowed)).getOrElse(plugins)
      }
      .map(plugins => Ok(Json.toJson(plugins)))
      .recoverWith {
        case e: WrappedErrorException => Future.successful(Status(e.error.status)(Json.toJson(e.error)))
        case e: Throwable => Future.successful(InternalServerError(JsonResponses.statusError(e.getMessage)))
      }
  }
}
