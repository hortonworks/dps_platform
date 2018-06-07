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
import com.hortonworks.dataplane.commons.domain.JsonFormatters._
import com.hortonworks.dataplane.db.Webservice.{ConfigService, DpClusterService}
import com.hortonworks.dataplane.commons.auth.AuthenticatedAction
import com.typesafe.scalalogging.Logger
import models.WrappedErrorsException
import play.api.libs.json.Json
import play.api.mvc._
import play.api.Configuration
import services.LdapService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Config @Inject()(
                        @Named("dpClusterService") val dpClusterService: DpClusterService,
                        ldapService: LdapService,
                        appConfiguration: Configuration,
                        @Named("configService") val configService: ConfigService)
  extends Controller {

  val logger = Logger(classOf[Config])

  def init = AuthenticatedAction.async { request =>
    for {
      user <- Future.successful(request.user)
      lake <- isDpClusterSetUp()
      auth <- isAuthSetup()
      rbac <- isRBACSetup()
    } yield Ok(
      Json.obj(
        "user" -> user,
        "lakeWasInitialized" -> lake,
        "authWasInitialized" -> auth,
        "rbacWasInitialized" -> rbac
      )
    )
  }


  //  code to check if at least one lake has been setup
  private def isDpClusterSetUp(): Future[Boolean] = {
    dpClusterService.list()
      .flatMap {
        case Left(errors) => Future.failed(WrappedErrorsException(errors))
        case Right(dataplaneClusters) => Future.successful(dataplaneClusters.length > 0)
      }
  }

  //  stub to check ldap setup later
  private def isAuthSetup(): Future[Boolean] = Future.successful(true)

  //  stub to check rbac setup later
  private def isRBACSetup(): Future[Boolean] = Future.successful(false)

  def getConfig(key: String) = Action.async {
    configService
      .getConfig(key).map {
        case None => Ok(Json.obj("value" -> ""))
        case Some(config) => Ok(Json.obj("value" -> config.configValue))
      }
  }

  def getGAProperties() = Action.async {
    for {
      trackingStatus <- configService.getConfig("dps.ga.tracking.enabled")
      trackingId <- configService.getConfig("dps.ga.tracking.id")
    } yield {
      trackingStatus match {
        case None => Ok(Json.obj("enabled" -> false))
        case Some(config) => config.configValue match {
          case "true" => trackingId match {
            case None => Ok(Json.obj("enabled" -> true))
            case Some(idConfig) => Ok(Json.obj("enabled" -> true,"trackingId" -> idConfig.configValue))
          }
          case "false" => Ok(Json.obj("enabled" -> false))
          case _ => {
            logger.warn("Config value for GA is not present/not a boolean. Returning false instead.")
            Ok(Json.obj("enabled" -> false))
          }
        }
      }
    }
  }

}
